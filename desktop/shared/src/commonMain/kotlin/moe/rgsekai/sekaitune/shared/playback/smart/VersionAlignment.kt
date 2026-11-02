package moe.rgsekai.sekaitune.shared.playback.smart

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The platform-free half of version alignment: finds the time offset between two cuts of a track from their
 * energy envelopes. Android's [VersionAudioAligner] (and iOS's lyrics alignment) feed it decoded PCM.
 */
object VersionAlignment {
    const val HOP_MS = 10.0 // 100 frames per second (10ms resolution)
    const val WIN_MS = 40.0 // 40ms analysis window
    const val MAX_LAG_SECONDS = 40.0 // Search up to ±40s drift/intro
    const val MIN_CONFIDENCE = 0.40 // Minimum normalized correlation

    /** [offsetMs] is null when nothing trustworthy was measured; [why] then says so. Target time = source time + offsetMs. */
    data class Alignment(val offsetMs: Long?, val correlation: Correlation, val why: String? = null)

    /**
     * Offset of the target cut against the source, from mono PCM windows that begin at [sourceStartSec] / [targetStartSec]
     * in their files. A weak match, or a peak pinned to the searchable edge (a truncated head), is refused rather than guessed.
     */
    fun align(
        source: FloatArray, sourceRate: Double, sourceStartSec: Double,
        target: FloatArray, targetRate: Double, targetStartSec: Double,
    ): Alignment {
        val c = crossCorrelate(computeEnergyEnvelope(source, sourceRate), computeEnergyEnvelope(target, targetRate))
        if (c.confidence < MIN_CONFIDENCE) return Alignment(null, c, "confidence ${c.confidence} below $MIN_CONFIDENCE")
        // A peak on the edge of what the window could search means the true lag is further out than these bytes reach.
        if (c.lagFrames == c.floor || c.lagFrames == c.ceiling) {
            return Alignment(null, c, "peak pinned to the searchable edge (${c.lagFrames} of ${c.floor}..${c.ceiling})")
        }
        return Alignment((c.lagFrames * HOP_MS).toLong() + ((targetStartSec - sourceStartSec) * 1000.0).toLong(), c)
    }

    /**
     * Computes RMS energy envelope over sliding windows.
     */
    fun computeEnergyEnvelope(
        samples: FloatArray,
        sampleRate: Double,
        hopMs: Double = HOP_MS,
        winMs: Double = WIN_MS,
    ): FloatArray {
        val hopSamples = max(1, (sampleRate * (hopMs / 1000.0)).toInt())
        val winSamples = max(hopSamples, (sampleRate * (winMs / 1000.0)).toInt())
        val numFrames = max(0, (samples.size - winSamples) / hopSamples)
        if (numFrames <= 0) return FloatArray(0)

        val env = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            val start = i * hopSamples
            val end = min(samples.size, start + winSamples)
            var sumSq = 0.0
            for (j in start until end) {
                val s = samples[j]
                sumSq += s * s
            }
            env[i] = sqrt(sumSq / (end - start)).toFloat()
        }
        return env
    }

    /**
     * A peak found by [crossCorrelate], together with the range it was found in.
     *
     * [floor] and [ceiling] are carried out of the search rather than left
     * there because they are the only way to tell a measured lag from a lag the
     * window was too small to move past — see [findOffsetMs].
     */
    data class Correlation(val lagFrames: Int, val confidence: Double, val floor: Int, val ceiling: Int)

    /**
     * Normalized cross-correlation between source envelope and target envelope.
     * Returns the best lag in frames, its confidence, and the lag range searched.
     * Positive lag means target event occurs later than source event (target = source + lag).
     *
     * Same numbers as the brute-force double loop this replaced — per-lag
     * means and variances from prefix sums, cross terms from one FFT pass —
     * but the cost drops from ~70M multiply-adds per pair of 45 s windows
     * (which on a cold device ran long enough for the measurement to blow its
     * whole deadline *after* finding the answer) to two O(n log n) transforms
     * and a linear scan over the lags.
     */
    fun crossCorrelate(
        sourceEnv: FloatArray,
        targetEnv: FloatArray,
        hopMs: Double = HOP_MS,
        maxLagSec: Double = MAX_LAG_SECONDS,
    ): Correlation {
        if (sourceEnv.isEmpty() || targetEnv.isEmpty()) return Correlation(0, 0.0, 0, 0)

        val maxLagFrames = (maxLagSec * 1000.0 / hopMs).toInt()
        val minOverlapFrames = (3.0 * 1000.0 / hopMs).toInt() // At least 3 seconds overlap

        var bestLag = 0
        var bestCorrelation = -1.0

        // lag d means targetEnv[i + d] corresponds to sourceEnv[i]
        val minLag = max(-maxLagFrames, -(sourceEnv.size - minOverlapFrames))
        val maxLag = min(maxLagFrames, targetEnv.size - minOverlapFrames)

        val crossTerms = crossTermsByLag(sourceEnv, targetEnv)

        val srcPrefix = DoubleArray(sourceEnv.size + 1)
        val srcSqPrefix = DoubleArray(sourceEnv.size + 1)
        for (i in sourceEnv.indices) {
            val v = sourceEnv[i].toDouble()
            srcPrefix[i + 1] = srcPrefix[i] + v
            srcSqPrefix[i + 1] = srcSqPrefix[i] + v * v
        }
        val tgtPrefix = DoubleArray(targetEnv.size + 1)
        val tgtSqPrefix = DoubleArray(targetEnv.size + 1)
        for (i in targetEnv.indices) {
            val v = targetEnv[i].toDouble()
            tgtPrefix[i + 1] = tgtPrefix[i] + v
            tgtSqPrefix[i + 1] = tgtSqPrefix[i] + v * v
        }

        for (lag in minLag..maxLag) {
            val srcStart = max(0, -lag)
            val tgtStart = max(0, lag)
            val overlapLen = min(sourceEnv.size - srcStart, targetEnv.size - tgtStart)
            if (overlapLen < minOverlapFrames) continue

            val sumSrc = srcPrefix[srcStart + overlapLen] - srcPrefix[srcStart]
            val sumTgt = tgtPrefix[tgtStart + overlapLen] - tgtPrefix[tgtStart]
            val sumSrcSq = srcSqPrefix[srcStart + overlapLen] - srcSqPrefix[srcStart]
            val sumTgtSq = tgtSqPrefix[tgtStart + overlapLen] - tgtSqPrefix[tgtStart]

            // The FFT hands back the raw Σx·y; the numerator the denominator's
            // variances expect is the centered one:
            // Σ(x−x̄)(y−ȳ) = Σxy − (Σx)(Σy)/n. Without this the confidence is
            // uncentered and can exceed 1 — a meaningless threshold.
            val num = crossTerms[lag + sourceEnv.size - 1] - sumSrc * sumTgt / overlapLen
            val denSrc = sumSrcSq - sumSrc * sumSrc / overlapLen
            val denTgt = sumTgtSq - sumTgt * sumTgt / overlapLen

            val denom = sqrt(denSrc * denTgt)
            if (denom > 1e-6) {
                val r = num / denom
                if (r > bestCorrelation) {
                    bestCorrelation = r
                    bestLag = lag
                }
            }
        }

        return Correlation(bestLag, bestCorrelation, minLag, maxLag)
    }

    /**
     * Σ source[i]·target[i+lag] for every lag the search may ask about,
     * indexed as `result[lag + source.size - 1]`.
     *
     * One zero-padded FFT pair does what the inner loop used to: the
     * cross-correlation theorem turns the per-lag sums into
     * `IFFT(conj(FFT(source)) · FFT(target))`, and padding to at least
     * source + target samples keeps the circular wrap out of the linear range
     * −(n−1)…(m−1), which covers any windowed lag with overlap.
     */
    private fun crossTermsByLag(source: FloatArray, target: FloatArray): DoubleArray {
        val n = source.size
        val m = target.size
        var fftLen = 1
        while (fftLen < n + m) fftLen = fftLen shl 1

        val srcRe = DoubleArray(fftLen)
        val srcIm = DoubleArray(fftLen)
        for (i in 0 until n) srcRe[i] = source[i].toDouble()
        val tgtRe = DoubleArray(fftLen)
        val tgtIm = DoubleArray(fftLen)
        for (i in 0 until m) tgtRe[i] = target[i].toDouble()

        fft(srcRe, srcIm)
        fft(tgtRe, tgtIm)

        // conj(X) · Y — the transform of the cross-correlation rather than the
        // convolution: the conjugate is what keeps the lag sign as written.
        for (i in 0 until fftLen) {
            val a = srcRe[i]
            val b = -srcIm[i]
            val c = tgtRe[i]
            val d = tgtIm[i]
            srcRe[i] = a * c - b * d
            srcIm[i] = a * d + b * c
        }
        fft(srcRe, srcIm, inverse = true)

        val result = DoubleArray(n + m - 1)
        for (lag in -(n - 1) until m) {
            val bin = ((lag % fftLen) + fftLen) % fftLen
            result[lag + n - 1] = srcRe[bin]
        }
        return result
    }

    /**
     * In-place iterative radix-2 FFT; the arrays' length must be a power of
     * two. [inverse] divides by the length on the way out.
     */
    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        var len = 2
        while (len <= n) {
            val angle = 2.0 * PI / len * (if (inverse) 1.0 else -1.0)
            val stepRe = cos(angle)
            val stepIm = sin(angle)
            for (start in 0 until n step len) {
                var twiddleRe = 1.0
                var twiddleIm = 0.0
                for (k in 0 until len / 2) {
                    val evenRe = re[start + k]
                    val evenIm = im[start + k]
                    val oddRe =
                        re[start + k + len / 2] * twiddleRe - im[start + k + len / 2] * twiddleIm
                    val oddIm =
                        re[start + k + len / 2] * twiddleIm + im[start + k + len / 2] * twiddleRe
                    re[start + k] = evenRe + oddRe
                    im[start + k] = evenIm + oddIm
                    re[start + k + len / 2] = evenRe - oddRe
                    im[start + k + len / 2] = evenIm - oddIm
                    val nextRe = twiddleRe * stepRe - twiddleIm * stepIm
                    twiddleIm = twiddleRe * stepIm + twiddleIm * stepRe
                    twiddleRe = nextRe
                }
            }
            len = len shl 1
        }

        if (inverse) {
            for (i in 0 until n) {
                re[i] /= n
                im[i] /= n
            }
        }
    }
}
