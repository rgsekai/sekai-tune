package moe.rgsekai.sekaitune.eq.audio

import kotlinx.serialization.Serializable

@Serializable
enum class SpatialMode {
    Crossfeed,
    Speakers,
}

@Serializable
data class SpatialParams(
    val enabled: Boolean = false,
    val mode: SpatialMode = SpatialMode.Crossfeed,
    val cutoffHz: Float = 700f,
    val feedDb: Float = 4.5f,
    val speakerAngle: Float = 30f,
    val amount: Float = 1f,
    val surround: Boolean = false,
    val mono: Boolean = false,
    val disableOnSpeaker: Boolean = true,
)
