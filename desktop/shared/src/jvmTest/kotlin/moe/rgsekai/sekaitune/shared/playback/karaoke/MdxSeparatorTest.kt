package moe.rgsekai.sekaitune.shared.playback.karaoke

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class MdxSeparatorTest {

    @Test
    fun `mixed radix fft matches a direct dft`() {
        for (n in intArrayOf(2, 3, 4, 5, 8, 12, 60, 240, 6_144, 7_680)) {
            val random = Random(n)
            val re = FloatArray(n) { random.nextFloat() - 0.5f }
            val im = FloatArray(n) { random.nextFloat() - 0.5f }
            val expectedRe = DoubleArray(n)
            val expectedIm = DoubleArray(n)
            // Spot-check a spread of bins on the big one; all of them otherwise.
            val bins = if (n > 1_000) (0 until n step 97).toList() + (n - 1) else (0 until n).toList()
            for (k in bins) {
                for (t in 0 until n) {
                    val angle = -2 * PI * k.toLong() * t / n
                    expectedRe[k] += re[t] * cos(angle) - im[t] * sin(angle)
                    expectedIm[k] += re[t] * sin(angle) + im[t] * cos(angle)
                }
            }
            MixedRadixFft(n).forward(re, im)
            for (k in bins) {
                val error = abs(re[k] - expectedRe[k]) + abs(im[k] - expectedIm[k])
                assertTrue(error < 1e-3 * maxOf(1.0, n / 100.0), "n=$n bin $k off by $error")
            }
        }
    }

    /**
     * Bin-for-bin parity with python-audio-separator's pipeline on one real
     * chunk. Needs the model and the reference pair the
     * prototype writes, so it only runs where `SEKAI_KARAOKE_REF` points at them.
     */
    @Test
    fun `separation matches the python reference`() {
        val dir = System.getenv("SEKAI_KARAOKE_REF")?.let(::File) ?: return
        val input = read(File(dir, "ref_in.f32"))
        val chunk = MdxSeparator.CHUNK
        val left = input.copyOfRange(0, chunk)
        val right = input.copyOfRange(chunk, 2 * chunk)
        for ((model, referenceName) in listOf(MdxModel.MDX_9482 to "ref_vocals_9482.f32")) {
            val reference = read(File(dir, referenceName))
            val vocalLeft = FloatArray(chunk)
            val vocalRight = FloatArray(chunk)
            MdxSeparator(File(dir, KaraokeModel.ASSET).path, threads = 4, model).use { separator ->
                val started = System.nanoTime()
                separator.separate(left, right, vocalLeft, vocalRight)
                println("$model chunk separated in ${(System.nanoTime() - started) / 1_000_000} ms")
            }
            var signal = 0.0
            var error = 0.0
            for (i in 0 until chunk) {
                signal += reference[i] * reference[i] + reference[chunk + i] * reference[chunk + i]
                val dl = vocalLeft[i] - reference[i]
                val dr = vocalRight[i] - reference[chunk + i]
                error += dl * dl + dr * dr
            }
            val snr = 10 * log10(signal / error)
            println("$model parity SNR $snr dB")
            assertTrue(snr > 50, "$model only $snr dB from the reference")
        }
    }

    private fun read(file: File): FloatArray {
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(buffer.remaining()).also(buffer::get)
    }
}
