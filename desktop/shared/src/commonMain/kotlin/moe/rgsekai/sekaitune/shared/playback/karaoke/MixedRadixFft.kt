package moe.rgsekai.sekaitune.shared.playback.karaoke

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Forward complex FFT for a length made of 2s, 3s and 5s — the separator's
 * 7680-point frame is `4⁴·2·3·5`, which a radix-2 FFT cannot do and Bluestein
 * would do at three times the cost.
 *
 * Stockham autosort, decimation in frequency: each stage reads one buffer and
 * writes the other, so there is no bit-reversal pass and every stage is a plain
 * strided loop. Radix 4 and 2 have hand-written butterflies; 3 and 5 occur once
 * each and go through the generic one.
 *
 * Not thread-safe: the scratch buffers are the instance's own.
 */
internal class MixedRadixFft(val size: Int) {

    private val radices: IntArray = factor(size)
    private val cosTable = FloatArray(size) { cos(-2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size) { sin(-2.0 * PI * it / size).toFloat() }
    private val scratchRe = FloatArray(size)
    private val scratchIm = FloatArray(size)

    /** Transforms [re] + i·[im] in place. */
    fun forward(re: FloatArray, im: FloatArray) {
        var inRe = re
        var inIm = im
        var outRe = scratchRe
        var outIm = scratchIm
        var length = size
        var stride = 1
        for (radix in radices) {
            val m = length / radix
            when (radix) {
                4 -> radix4(inRe, inIm, outRe, outIm, m, stride)
                2 -> radix2(inRe, inIm, outRe, outIm, m, stride)
                else -> generic(radix, inRe, inIm, outRe, outIm, m, stride)
            }
            val swapRe = inRe; inRe = outRe; outRe = swapRe
            val swapIm = inIm; inIm = outIm; outIm = swapIm
            length = m
            stride *= radix
        }
        if (inRe !== re) {
            inRe.copyInto(re)
            inIm.copyInto(im)
        }
    }

    // Stage: for p < m, q < s — y[q + s(r·p + u)] = W_n^(u·p) · Σ_v x[q + s(p + v·m)] · W_r^(u·v),
    // with n = r·m the current length; W_n^k is the table at k·s.

    private fun radix2(xr: FloatArray, xi: FloatArray, yr: FloatArray, yi: FloatArray, m: Int, s: Int) {
        for (p in 0 until m) {
            val w = (p * s) % size
            val wr = cosTable[w]
            val wi = sinTable[w]
            for (q in 0 until s) {
                val a = q + s * p
                val b = a + s * m
                val ar = xr[a]; val ai = xi[a]
                val br = xr[b]; val bi = xi[b]
                val out = q + s * 2 * p
                yr[out] = ar + br
                yi[out] = ai + bi
                val dr = ar - br
                val di = ai - bi
                yr[out + s] = dr * wr - di * wi
                yi[out + s] = dr * wi + di * wr
            }
        }
    }

    private fun radix4(xr: FloatArray, xi: FloatArray, yr: FloatArray, yi: FloatArray, m: Int, s: Int) {
        val sm = s * m
        for (p in 0 until m) {
            val w1 = (p * s) % size
            val w2 = (2 * p * s) % size
            val w3 = (3 * p * s) % size
            val c1 = cosTable[w1]; val s1 = sinTable[w1]
            val c2 = cosTable[w2]; val s2 = sinTable[w2]
            val c3 = cosTable[w3]; val s3 = sinTable[w3]
            for (q in 0 until s) {
                val a = q + s * p
                val ar = xr[a]; val ai = xi[a]
                val br = xr[a + sm]; val bi = xi[a + sm]
                val cr = xr[a + 2 * sm]; val ci = xi[a + 2 * sm]
                val dr = xr[a + 3 * sm]; val di = xi[a + 3 * sm]
                // W4 = -i.
                val t0r = ar + cr; val t0i = ai + ci
                val t1r = ar - cr; val t1i = ai - ci
                val t2r = br + dr; val t2i = bi + di
                val t3r = bi - di; val t3i = dr - br // -i·(b - d)
                val out = q + s * 4 * p
                yr[out] = t0r + t2r
                yi[out] = t0i + t2i
                val u1r = t1r + t3r; val u1i = t1i + t3i
                yr[out + s] = u1r * c1 - u1i * s1
                yi[out + s] = u1r * s1 + u1i * c1
                val u2r = t0r - t2r; val u2i = t0i - t2i
                yr[out + 2 * s] = u2r * c2 - u2i * s2
                yi[out + 2 * s] = u2r * s2 + u2i * c2
                val u3r = t1r - t3r; val u3i = t1i - t3i
                yr[out + 3 * s] = u3r * c3 - u3i * s3
                yi[out + 3 * s] = u3r * s3 + u3i * c3
            }
        }
    }

    private fun generic(r: Int, xr: FloatArray, xi: FloatArray, yr: FloatArray, yi: FloatArray, m: Int, s: Int) {
        val step = size / r
        for (p in 0 until m) {
            for (q in 0 until s) {
                val base = q + s * p
                val out = q + s * r * p
                for (u in 0 until r) {
                    var sr = 0f
                    var si = 0f
                    for (v in 0 until r) {
                        val w = (u * v % r) * step
                        val vr = xr[base + v * s * m]
                        val vi = xi[base + v * s * m]
                        sr += vr * cosTable[w] - vi * sinTable[w]
                        si += vr * sinTable[w] + vi * cosTable[w]
                    }
                    val w = (u * p * s) % size
                    yr[out + u * s] = sr * cosTable[w] - si * sinTable[w]
                    yi[out + u * s] = sr * sinTable[w] + si * cosTable[w]
                }
            }
        }
    }

    private companion object {
        fun factor(n: Int): IntArray {
            require(n > 0)
            val radices = ArrayList<Int>()
            var rest = n
            while (rest % 4 == 0) { radices += 4; rest /= 4 }
            for (radix in intArrayOf(2, 3, 5)) {
                while (rest % radix == 0) { radices += radix; rest /= radix }
            }
            require(rest == 1) { "$n has a factor other than 2, 3 and 5" }
            return radices.toIntArray()
        }
    }
}
