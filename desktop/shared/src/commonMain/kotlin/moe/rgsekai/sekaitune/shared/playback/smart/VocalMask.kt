package moe.rgsekai.sekaitune.shared.playback.smart

import kotlin.math.ceil
import kotlin.math.floor

/** Reads a model tensor without saying where it lives: a direct buffer on Android, an array on iOS. */
fun interface FloatSource {
    operator fun get(index: Int): Float
}

/**
 * The open-unmix vocal path's constants and its post-processing, shared by Android's [VocalTracker]
 * (ONNX Runtime in Kotlin) and iOS (ONNX Runtime in Swift). The front end and the model are
 * platform code; what turns their output into a vocal-activity mask for the planner is this.
 *
 * Every constant is dictated by the trained network (see native/analyzer/vocal_spectrogram.h).
 */
object VocalModel {
    const val SAMPLE_RATE = 44_100.0
    const val FFT_SIZE = 4096
    const val HOP = 1024
    const val BINS = FFT_SIZE / 2 + 1
    const val CHANNELS = 2

    /** The model's fixed input width, ~22.8 s, chosen upstream to cover a transition overlap. */
    const val FIXED_FRAMES = 960

    /** The band a vocal actually occupies; below and above it the mask says little. */
    const val LOW_HZ = 200.0
    const val HIGH_HZ = 4000.0

    const val FRAME_RATE = SAMPLE_RATE / HOP

    /**
     * What an unmeasured instant reads as. Below the policy's VOCAL_ACTIVE_THRESHOLD by design, so
     * absence of measurement is never mistaken for absence of a vocal, or for the presence of one.
     */
    const val NEUTRAL_VOCAL = 0.5

    /** First and last STFT bin of the vocal band. */
    val lowBin: Int get() = floor(LOW_HZ * FFT_SIZE / SAMPLE_RATE).toInt().coerceAtLeast(0)
    val highBin: Int get() = ceil(HIGH_HZ * FFT_SIZE / SAMPLE_RATE).toInt().coerceAtMost(BINS - 1)

    /**
     * The longest stretch of audio the model takes in one go. The beat model's window is longer, so
     * a region is trimmed to this rather than handed over whole; two frames of margin absorb the ±1
     * sample a rate conversion can land on.
     */
    val maxRegionSeconds: Double get() = (FIXED_FRAMES - 2) * HOP / SAMPLE_RATE

    /**
     * Averages `mask = target / (mix + eps)` across the vocal band, then across channels, for each
     * of the first [usableFrames] frames.
     *
     * Band-averaging rather than a full per-bin mask: the only consumer is a single number per
     * instant (how vocal this moment is), so per-bin resolution would be work with no reader. Only
     * the frames carrying real audio are reduced; the padded tail's mask is meaningless and folding
     * it in would drag every short window toward silence.
     *
     * Both tensors carry the model's `[1, 2, bins, FIXED_FRAMES]` layout, but may hold only some of
     * the bins: [firstBin] is the first one stored and [storedBins] how many.
     */
    fun reduceToBandCurve(
        mix: FloatSource,
        target: FloatSource,
        usableFrames: Int,
        firstBin: Int = 0,
        storedBins: Int = BINS,
    ): FloatArray {
        val lowBin = lowBin
        val highBin = highBin
        if (highBin <= lowBin || usableFrames <= 0) return FloatArray(0)

        val curve = FloatArray(usableFrames)
        for (frame in 0 until usableFrames) {
            var sum = 0.0
            var count = 0
            for (channel in 0 until CHANNELS) {
                for (bin in lowBin..highBin) {
                    // One index reads the same cell of each tensor.
                    val index = (channel * storedBins + (bin - firstBin)) * FIXED_FRAMES + frame
                    val mixValue = mix[index]
                    if (mixValue <= 1e-6f) continue
                    val ratio = target[index] / mixValue
                    sum += ratio.coerceIn(0f, 1f)
                    count += 1
                }
            }
            curve[frame] = if (count > 0) (sum / count).toFloat() else 0f
        }
        return curve
    }

    /**
     * A vocal-presence value for every point on the energy curve, filled only where the model
     * actually ran.
     *
     * The policy indexes the mask against energy-curve sample times and requires the two to be the
     * same length, but the model's window is fixed at about 22 seconds, far less than a track. So
     * the mask is built at full length and filled only over this region ([values], one per STFT
     * frame, starting at [regionStart] seconds); everywhere else stays at [NEUTRAL_VOCAL].
     */
    fun maskOver(curve: List<EnergySample>, values: FloatArray, regionStart: Double): DoubleArray {
        val mask = DoubleArray(curve.size) { NEUTRAL_VOCAL }
        for (index in curve.indices) {
            val frame = ((curve[index].time - regionStart) * FRAME_RATE).toInt()
            if (frame in values.indices) mask[index] = values[frame].toDouble()
        }
        return mask
    }

    /**
     * Overlays the head and tail masks onto one full-length curve, or null when neither ran —
     * which the caller answers by keeping the DSP heuristic rather than reporting a mask of nothing
     * but [NEUTRAL_VOCAL].
     */
    fun mergeMasks(size: Int, head: DoubleArray?, tail: DoubleArray?): List<Double>? {
        if (size <= 0 || (head == null && tail == null)) return null
        val merged = DoubleArray(size) { NEUTRAL_VOCAL }
        for (source in listOfNotNull(head, tail)) {
            for (index in merged.indices) {
                if (index < source.size && source[index] != NEUTRAL_VOCAL) merged[index] = source[index]
            }
        }
        return merged.toList()
    }
}
