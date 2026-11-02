package moe.rgsekai.sekaitune.shared.playback.karaoke

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * One chunk of vocal separation with one of UVR's MDX-Net models (Kim /
 * Anjok07, MIT) — see [MdxModel] — the model [KaraokeModel] ships.
 *
 * MDX-Net is a TFC-TDF U-Net over a complex spectrogram: it takes the mix's
 * STFT (left and right, real and imaginary, as four channels) and returns the
 * vocal's. The STFT and its inverse are done here, exactly as the model was
 * trained on them — `torch.stft` with a periodic Hann window, `center=True`
 * reflect padding, [MdxModel.nFft] points, hop 1024, the lowest [MdxModel.dimF]
 * bins kept and the three lowest zeroed — so that the numbers match the Python
 * reference (python-audio-separator) bin for bin.
 *
 * Input is 44.1 kHz, [CHUNK] frames: about 5.9 s, of which the outer
 * [MdxModel.trim] on either side is context the caller throws away. Inference is the expensive
 * part (a couple of seconds per chunk on a phone's big cores); the transforms
 * are ~5% of it.
 *
 * Not thread-safe; one instance per worker.
 */
actual class MdxSeparator actual constructor(
    modelPath: String,
    threads: Int,
    actual val model: MdxModel,
) : ChunkSeparator, Closeable {

    private val N_FFT = model.nFft
    private val DIM_F = model.dimF

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(
        modelPath,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        },
    )
    private val inputName = session.inputNames.first()

    /** The run in flight's options, for [abort] to reach. */
    @Volatile
    private var runOptions: OrtSession.RunOptions? = null

    /** Thrown out of [separate] when [abort] cut it short. */
    actual class Aborted actual constructor() : Exception()

    /** Cuts the inference under way short, from any thread; [separate] then throws [Aborted]. */
    actual fun abort() {
        runCatching { runOptions?.setTerminate(true) }
    }

    private val fft = MixedRadixFft(N_FFT)
    private val re = FloatArray(N_FFT)
    private val im = FloatArray(N_FFT)
    private val window = FloatArray(N_FFT) { (0.5 - 0.5 * cos(2.0 * PI * it / N_FFT)).toFloat() }

    /** Σ window² over every frame touching each padded sample, floored as torch does. */
    private val windowSum = FloatArray(CHUNK + N_FFT).also { sum ->
        for (frame in 0 until DIM_T) {
            for (i in 0 until N_FFT) sum[frame * HOP + i] += window[i] * window[i]
        }
        for (i in sum.indices) sum[i] = max(sum[i], 1e-8f)
    }

    private val padded = Array(2) { FloatArray(CHUNK + N_FFT) }
    private val output = Array(2) { FloatArray(CHUNK + N_FFT) }

    /** The model's [1, 4, DIM_F, DIM_T] input, direct so ORT reads it in place. */
    private val input: ByteBuffer = ByteBuffer
        .allocateDirect(4 * DIM_F * DIM_T * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())

    /**
     * The vocal in [left]/[right] — [CHUNK] frames each at 44.1 kHz — written
     * into [vocalLeft]/[vocalRight], also [CHUNK] long.
     */
    override fun separate(
        left: FloatArray,
        right: FloatArray,
        vocalLeft: FloatArray,
        vocalRight: FloatArray,
    ) {
        analyse(left, right)
        val shape = longArrayOf(1, 4, DIM_F.toLong(), DIM_T.toLong())
        val options = OrtSession.RunOptions()
        runOptions = options
        try {
            OnnxTensor.createTensor(environment, input.asFloatBuffer(), shape).use { tensor ->
                val outputs = try {
                    session.run(mapOf(inputName to tensor), options)
                } catch (error: ai.onnxruntime.OrtException) {
                    throw Aborted()
                }
                outputs.use { synthesise((it.get(0) as OnnxTensor).floatBuffer) }
            }
        } finally {
            runOptions = null
            options.close()
        }
        output[0].copyInto(vocalLeft, 0, N_FFT / 2, N_FFT / 2 + CHUNK)
        output[1].copyInto(vocalRight, 0, N_FFT / 2, N_FFT / 2 + CHUNK)
    }

    /** Reflect-pads both channels and writes their STFT into [input]. */
    private fun analyse(left: FloatArray, right: FloatArray) {
        val half = N_FFT / 2
        for ((channel, source) in arrayOf(left, right).withIndex()) {
            val target = padded[channel]
            source.copyInto(target, half, 0, CHUNK)
            // torch's "reflect": the edge sample is not repeated.
            for (i in 1..half) {
                target[half - i] = source[i]
                target[half + CHUNK - 1 + i] = source[CHUNK - 1 - i]
            }
        }
        val values = input.asFloatBuffer()
        for (frame in 0 until DIM_T) {
            val start = frame * HOP
            // Both channels in one transform: z = l + i·r.
            for (i in 0 until N_FFT) {
                re[i] = padded[0][start + i] * window[i]
                im[i] = padded[1][start + i] * window[i]
            }
            fft.forward(re, im)
            for (bin in 0 until DIM_F) {
                if (bin < ZEROED_BINS) {
                    for (c in 0 until 4) values.put(index(c, bin, frame), 0f)
                    continue
                }
                val mirror = if (bin == 0) 0 else N_FFT - bin
                val zr = re[bin]; val zi = im[bin]
                val mr = re[mirror]; val mi = im[mirror]
                values.put(index(0, bin, frame), 0.5f * (zr + mr))
                values.put(index(1, bin, frame), 0.5f * (zi - mi))
                values.put(index(2, bin, frame), 0.5f * (zi + mi))
                values.put(index(3, bin, frame), -0.5f * (zr - mr))
            }
        }
    }

    /** Inverse STFT of the model's output into [output], window-sum normalised as torch.istft does. */
    private fun synthesise(spectrum: FloatBuffer) {
        output[0].fill(0f)
        output[1].fill(0f)
        for (frame in 0 until DIM_T) {
            re.fill(0f)
            im.fill(0f)
            // Two real signals' spectra packed as Z = L + i·R, Hermitian halves
            // filled in, then one inverse — done as a forward transform of the
            // conjugate, whose conjugated result is the inverse times N.
            for (bin in 0 until DIM_F) {
                val lr = spectrum.get(index(0, bin, frame))
                val li = spectrum.get(index(1, bin, frame))
                val rr = spectrum.get(index(2, bin, frame))
                val ri = spectrum.get(index(3, bin, frame))
                // Z[k] = L + iR, conjugated for the forward-as-inverse trick.
                re[bin] = lr - ri
                im[bin] = -(li + rr)
                if (bin == 0) continue
                // Z[N-k] = conj(L) + i·conj(R), conjugated.
                val mirror = N_FFT - bin
                re[mirror] = lr + ri
                im[mirror] = -(-li + rr)
            }
            fft.forward(re, im)
            val start = frame * HOP
            val left = output[0]
            val right = output[1]
            for (i in 0 until N_FFT) {
                val w = window[i] / N_FFT
                left[start + i] += re[i] * w
                right[start + i] += -im[i] * w
            }
        }
        for (i in 0 until CHUNK + N_FFT) {
            val norm = windowSum[i]
            output[0][i] /= norm
            output[1][i] /= norm
        }
    }

    actual override fun close() {
        session.close()
    }

    actual companion object {
        actual const val SAMPLE_RATE = 44_100
        actual const val HOP = 1_024
        actual const val DIM_T = 256
        actual const val CHUNK = HOP * (DIM_T - 1)

        private const val ZEROED_BINS = 3
    }

    private fun index(channel: Int, bin: Int, frame: Int) = (channel * DIM_F + bin) * DIM_T + frame
}
