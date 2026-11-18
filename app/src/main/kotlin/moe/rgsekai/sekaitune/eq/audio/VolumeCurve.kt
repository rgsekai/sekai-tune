package moe.rgsekai.sekaitune.eq.audio

import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
data class VolumeCurve(
    val enabled: Boolean = false,
    val exponent: Float = 2f,
    val limit: Float = 1f,
) {
    fun gain(volume: Float): Float {
        val v = volume.coerceIn(0f, 1f)
        if (!enabled) return v
        return limit.coerceIn(0.05f, 1f) * v.pow(exponent.coerceIn(1f, 5f))
    }
}

