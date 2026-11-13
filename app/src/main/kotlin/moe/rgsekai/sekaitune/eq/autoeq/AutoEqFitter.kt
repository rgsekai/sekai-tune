package moe.rgsekai.sekaitune.eq.autoeq

import moe.rgsekai.sekaitune.eq.audio.Biquad
import moe.rgsekai.sekaitune.eq.data.FilterType
import moe.rgsekai.sekaitune.eq.data.ParametricEQ
import moe.rgsekai.sekaitune.eq.data.ParametricEQBand
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

@Serializable
data class AutoEqTuning(
    val bands: Int = 10,
    val maxHz: Int = 16_000,
    val bassDb: Double = 0.0,
    val tiltDbPerOctave: Double = 0.0,
) {
    val isDefault: Boolean get() = bands == 10 && maxHz == 16_000 && bassDb == 0.0 && tiltDbPerOctave == 0.0

    companion object {
        val BandChoices = listOf(5, 8, 10, 15)
        val MaxHzChoices = listOf(10_000, 16_000, 20_000)
    }
}

object AutoEqFitter {

    private const val SampleRate = 48_000
    private const val MaxBoostDb = 8.0
    private const val MaxCutDb = 12.0

    fun correction(measurement: AutoEqMeasurement, tuning: AutoEqTuning): ParametricEQ {
        if (tuning.isDefault && measurement.preset.bands.isNotEmpty()) return measurement.preset
        return fit(
            frequencies = measurement.frequencies,
            raw = measurement.raw,
            target = tunedTarget(measurement, tuning),
            bands = tuning.bands,
            maxHz = tuning.maxHz.toDouble(),
        )
    }

    fun tunedTarget(measurement: AutoEqMeasurement, tuning: AutoEqTuning): DoubleArray {
        val f = measurement.frequencies
        val out = measurement.target.copyOf()
        if (tuning.bassDb != 0.0) {
            val c = DoubleArray(Biquad.Stride)
            Biquad.coefficients(FilterType.LSC, 105.0, tuning.bassDb, 0.7, SampleRate, c)
            for (i in f.indices) out[i] += Biquad.magnitudeDb(c, 0, f[i], SampleRate)
        }
        if (tuning.tiltDbPerOctave != 0.0) {
            for (i in f.indices) out[i] += tuning.tiltDbPerOctave * log2(f[i] / 1000.0)
        }
        return out
    }

    fun fit(
        frequencies: DoubleArray,
        raw: DoubleArray,
        target: DoubleArray,
        bands: Int,
        maxHz: Double,
    ): ParametricEQ {
        val n = frequencies.size
        if (n < 8) return ParametricEQ(0.0, emptyList())
        val octave = DoubleArray(n) { log2(frequencies[it]) }
        val cos1 = DoubleArray(n) { cos(2.0 * PI * frequencies[it] / SampleRate) }
        val cos2 = DoubleArray(n) { cos(4.0 * PI * frequencies[it] / SampleRate) }

        val wanted = DoubleArray(n) { target[it] - raw[it] }
        val midMean = frequencies.indices.filter { frequencies[it] in 250.0..2500.0 }
            .map { wanted[it] }.average().takeIf { !it.isNaN() } ?: 0.0
        for (i in 0 until n) wanted[i] -= midMean
        val e = smooth(wanted, octave) { f -> if (f < 6000.0) 1.0 / 24 else 1.0 / 6 }
        for (i in 0 until n) e[i] = e[i].coerceIn(-MaxCutDb, MaxBoostDb)

        val weight = DoubleArray(n) {
            val over = log2(frequencies[it] / maxHz)
            when {
                over <= 0.0 -> 1.0
                over >= 0.25 -> 0.0
                else -> 0.5 + 0.5 * cos(PI * over / 0.25)
            }
        }

        val filters = ArrayList<Filter>()
        filters += Filter(FilterType.LSC, 105.0, meanOver(e, frequencies, 20.0, 90.0), 0.7)
        if (maxHz >= 12_000) filters += Filter(FilterType.HSC, 10_000.0, meanOver(e, frequencies, 10_000.0, maxHz), 0.7)
        filters.forEach { it.gain = it.gain.coerceIn(-MaxCutDb, MaxBoostDb) }

        val total = DoubleArray(n)
        val scratch = DoubleArray(Biquad.Stride)
        fun responseOf(type: FilterType, f: Double, g: Double, q: Double, into: DoubleArray) {
            Biquad.coefficients(type, f, g, q, SampleRate, scratch)
            for (i in 0 until n) into[i] = Biquad.magnitudeDb(scratch, 0, cos1[i], cos2[i])
        }
        filters.forEach { responseOf(it.type, it.frequency, it.gain, it.q, it.response(n)) }
        fun recomputeTotal() {
            total.fill(0.0)
            filters.forEach { flt -> for (i in 0 until n) total[i] += flt.response(n)[i] }
        }
        recomputeTotal()

        val residual = DoubleArray(n)
        val peaks = (bands - filters.size).coerceAtLeast(0)
        repeat(peaks) {
            for (i in 0 until n) residual[i] = e[i] - total[i]
            val r = smooth(residual, octave) { 1.0 / 6 }
            var best = -1
            var bestValue = 0.0
            for (i in 0 until n) {
                val f = frequencies[i]
                if (f < 20.0 || f > maxHz) continue
                val v = abs(r[i]) * weight[i]
                if (v > bestValue) {
                    bestValue = v
                    best = i
                }
            }
            if (best < 0 || bestValue < 0.2) return@repeat
            val gain = r[best].coerceIn(-MaxCutDb, MaxBoostDb)
            var lo = best
            var hi = best
            while (lo > 0 && r[lo - 1] * gain > 0 && abs(r[lo - 1]) > abs(gain) / 2) lo--
            while (hi < n - 1 && r[hi + 1] * gain > 0 && abs(r[hi + 1]) > abs(gain) / 2) hi++
            val bw = (octave[hi] - octave[lo]).coerceIn(0.15, 4.0)
            val q = (sqrt(2.0.pow(bw)) / (2.0.pow(bw) - 1.0)).coerceIn(0.3, 6.0)
            val flt = Filter(FilterType.PK, frequencies[best], gain, q)
            responseOf(flt.type, flt.frequency, flt.gain, flt.q, flt.response(n))
            filters += flt
            for (i in 0 until n) total[i] += flt.response(n)[i]
        }

        optimize(filters, total, e, weight, n, frequencies, ::responseOf)

        val maxBoost = (0 until n).maxOf { total[it] }
        val bandsOut = filters.map {
            ParametricEQBand(
                frequency = round(it.frequency),
                gain = round(it.gain * 10) / 10,
                q = round(it.q * 100) / 100,
                filterType = it.type,
            )
        }
        return ParametricEQ(preamp = round(-max(0.0, maxBoost) * 10) / 10, bands = bandsOut)
    }

    private fun optimize(
        filters: List<Filter>,
        total: DoubleArray,
        e: DoubleArray,
        w: DoubleArray,
        n: Int,
        frequencies: DoubleArray,
        responseOf: (FilterType, Double, Double, Double, DoubleArray) -> Unit,
    ) {
        val weightSum = w.sum().coerceAtLeast(1e-9)
        val probe = DoubleArray(n)
        fun lossWith(k: Filter, replacement: DoubleArray): Double {
            var sum = 0.0
            val old = k.response(n)
            for (i in 0 until n) {
                if (w[i] == 0.0) continue
                val d = total[i] - old[i] + replacement[i] - e[i]
                sum += w[i] * d * d
            }
            return sum / weightSum
        }

        val params = filters.size * 3
        val m = DoubleArray(params)
        val v = DoubleArray(params)
        val grad = DoubleArray(params)
        val lr = doubleArrayOf(0.03, 0.18, 0.04)
        val step = doubleArrayOf(0.005, 0.02, 0.005)
        val maxF = frequencies.last()
        val iterations = 160
        for (t in 1..iterations) {
            filters.forEachIndexed { k, flt ->
                val tunable = if (flt.type == FilterType.PK) 0..2 else 1..1
                for (j in 0..2) grad[k * 3 + j] = 0.0
                for (j in tunable) {
                    val h = step[j]
                    val plus = flt.nudged(j, h)
                    responseOf(flt.type, plus.frequency, plus.gain, plus.q, probe)
                    val lp = lossWith(flt, probe)
                    val minus = flt.nudged(j, -h)
                    responseOf(flt.type, minus.frequency, minus.gain, minus.q, probe)
                    val lm = lossWith(flt, probe)
                    grad[k * 3 + j] = (lp - lm) / (2 * h) + if (j == 1) 2e-4 * flt.gain else 0.0
                }
            }
            val decay = 1.0 - 0.6 * t / iterations
            filters.forEachIndexed { k, flt ->
                for (j in 0..2) {
                    val i = k * 3 + j
                    if (grad[i] == 0.0) continue
                    m[i] = 0.9 * m[i] + 0.1 * grad[i]
                    v[i] = 0.999 * v[i] + 0.001 * grad[i] * grad[i]
                    val mh = m[i] / (1 - 0.9.pow(t))
                    val vh = v[i] / (1 - 0.999.pow(t))
                    val delta = -lr[j] * decay * mh / (sqrt(vh) + 1e-8)
                    flt.shift(j, delta, maxF)
                }
                responseOf(flt.type, flt.frequency, flt.gain, flt.q, flt.response(n))
            }
            total.fill(0.0)
            filters.forEach { flt -> val r = flt.response(n); for (i in 0 until n) total[i] += r[i] }
        }
    }

    private class Filter(val type: FilterType, var frequency: Double, var gain: Double, var q: Double) {
        private var curve: DoubleArray? = null
        fun response(n: Int): DoubleArray = curve ?: DoubleArray(n).also { curve = it }

        fun nudged(j: Int, h: Double): Filter = when (j) {
            0 -> Filter(type, frequency * 2.0.pow(h), gain, q)
            1 -> Filter(type, frequency, gain + h, q)
            else -> Filter(type, frequency, gain, q * exp(h))
        }

        fun shift(j: Int, delta: Double, maxF: Double) {
            when (j) {
                0 -> frequency = (frequency * 2.0.pow(delta)).coerceIn(20.0, maxF)
                1 -> gain = (gain + delta).coerceIn(-MaxCutDb, MaxBoostDb)
                else -> q = (q * exp(delta)).coerceIn(0.18, 6.0)
            }
        }
    }

    private fun meanOver(values: DoubleArray, f: DoubleArray, from: Double, to: Double): Double {
        var sum = 0.0
        var count = 0
        for (i in f.indices) if (f[i] in from..to) { sum += values[i]; count++ }
        return if (count == 0) 0.0 else sum / count
    }

    private inline fun smooth(values: DoubleArray, octave: DoubleArray, halfWidth: (Double) -> Double): DoubleArray {
        val n = values.size
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val hw = halfWidth(2.0.pow(octave[i]))
            var sum = values[i]
            var count = 1
            var j = i - 1
            while (j >= 0 && octave[i] - octave[j] <= hw) { sum += values[j]; count++; j-- }
            j = i + 1
            while (j < n && octave[j] - octave[i] <= hw) { sum += values[j]; count++; j++ }
            out[i] = sum / count
        }
        return out
    }
}

