package moe.rgsekai.sekaitune.shared.playback.karaoke

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * The MDX-Net separator on iOS: the same STFT/ISTFT as the JVM actual (jvmSharedMain, kept bin for bin
 * with it), with inference handed to ONNX Runtime in Swift through [KaraokeNative].
 * ponytail: the transforms are copied, not shared; hoist them into commonMain if either changes.
 * Construction throws where Swift can't load the model, which KaraokeStems treats as "model failed".
 */
@OptIn(ExperimentalForeignApi::class)
actual class MdxSeparator actual constructor(
    modelPath: String,
    threads: Int,
    actual val model: MdxModel,
) : ChunkSeparator {

    private val N_FFT = model.nFft
    private val DIM_F = model.dimF

    private val runner = requireNotNull(IosKaraoke.native?.openModel(modelPath, threads)) { "ONNX model not loaded" }

    actual class Aborted actual constructor() : Exception()

    // ponytail: ONNX Runtime's Swift API has no terminate; a started chunk always finishes.
    actual fun abort() = Unit

    private val fft = MixedRadixFft(N_FFT)
    private val re = FloatArray(N_FFT)
    private val im = FloatArray(N_FFT)
    private val window = FloatArray(N_FFT) { (0.5 - 0.5 * cos(2.0 * PI * it / N_FFT)).toFloat() }

    private val windowSum = FloatArray(CHUNK + N_FFT).also { sum ->
        for (frame in 0 until DIM_T) {
            for (i in 0 until N_FFT) sum[frame * HOP + i] += window[i] * window[i]
        }
        for (i in sum.indices) sum[i] = max(sum[i], 1e-8f)
    }

    private val padded = Array(2) { FloatArray(CHUNK + N_FFT) }
    private val output = Array(2) { FloatArray(CHUNK + N_FFT) }
    private val input = FloatArray(4 * DIM_F * DIM_T)
    private val spectrum = FloatArray(4 * DIM_F * DIM_T)

    override fun separate(left: FloatArray, right: FloatArray, vocalLeft: FloatArray, vocalRight: FloatArray) {
        analyse(left, right)
        val ok = input.usePinned { i -> spectrum.usePinned { o -> runner.run(i.addressOf(0), o.addressOf(0)) } }
        check(ok) { "ONNX inference failed" }
        synthesise(spectrum)
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
                    for (c in 0 until 4) input[index(c, bin, frame)] = 0f
                    continue
                }
                val mirror = if (bin == 0) 0 else N_FFT - bin
                val zr = re[bin]; val zi = im[bin]
                val mr = re[mirror]; val mi = im[mirror]
                input[index(0, bin, frame)] = 0.5f * (zr + mr)
                input[index(1, bin, frame)] = 0.5f * (zi - mi)
                input[index(2, bin, frame)] = 0.5f * (zi + mi)
                input[index(3, bin, frame)] = -0.5f * (zr - mr)
            }
        }
    }

    /** Inverse STFT of the model's output into [output], window-sum normalised as torch.istft does. */
    private fun synthesise(spectrum: FloatArray) {
        output[0].fill(0f)
        output[1].fill(0f)
        for (frame in 0 until DIM_T) {
            re.fill(0f)
            im.fill(0f)
            // Two real signals' spectra packed as Z = L + i·R, Hermitian halves
            // filled in, then one inverse — done as a forward transform of the
            // conjugate, whose conjugated result is the inverse times N.
            for (bin in 0 until DIM_F) {
                val lr = spectrum[index(0, bin, frame)]
                val li = spectrum[index(1, bin, frame)]
                val rr = spectrum[index(2, bin, frame)]
                val ri = spectrum[index(3, bin, frame)]
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

    actual fun close() = runner.close()

    actual companion object {
        actual const val SAMPLE_RATE = 44_100
        actual const val HOP = 1_024
        actual const val DIM_T = 256
        actual const val CHUNK = HOP * (DIM_T - 1)

        private const val ZEROED_BINS = 3
    }

    private fun index(channel: Int, bin: Int, frame: Int) = (channel * DIM_F + bin) * DIM_T + frame
}
