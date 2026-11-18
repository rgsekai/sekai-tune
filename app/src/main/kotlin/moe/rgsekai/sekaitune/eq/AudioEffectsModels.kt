package moe.rgsekai.sekaitune.eq

import moe.rgsekai.sekaitune.eq.audio.LoudnessParams
import moe.rgsekai.sekaitune.eq.audio.ReplayGainMode
import moe.rgsekai.sekaitune.eq.audio.ReplayGainParams
import moe.rgsekai.sekaitune.eq.audio.ReverbParams
import moe.rgsekai.sekaitune.eq.audio.SpatialMode
import moe.rgsekai.sekaitune.eq.audio.SpatialParams
import moe.rgsekai.sekaitune.eq.audio.VolumeCurve
import kotlinx.serialization.Serializable
import kotlin.math.pow

const val MinPlaybackSpeed = 0.1f
const val MaxPlaybackSpeed = 8.0f

@Serializable
data class ReverbPreset(
    val id: String,
    val name: String,
    val mix: Float,
    val roomSize: Float,
    val damping: Float,
    val wet: Float,
    val width: Float,
    val preDelayMs: Float,
    val lowCutHz: Float,
)

object ReverbPresets {
    val Default = ReverbPreset("room", "Room", 0.5f, 0.55f, 0.45f, 0.6f, 1f, 12f, 180f)

    val All = listOf(
        Default,
        ReverbPreset("studio", "Studio", 0.4f, 0.3f, 0.65f, 0.45f, 0.8f, 5f, 250f),
        ReverbPreset("bathroom", "Bathroom", 0.5f, 0.42f, 0.05f, 0.7f, 0.6f, 2f, 120f),
        ReverbPreset("plate", "Plate", 0.45f, 0.65f, 0.1f, 0.55f, 1f, 0f, 300f),
        ReverbPreset("hall", "Hall", 0.5f, 0.78f, 0.35f, 0.6f, 1f, 25f, 150f),
        ReverbPreset("cathedral", "Cathedral", 0.55f, 0.95f, 0.2f, 0.7f, 1f, 40f, 120f),
        ReverbPreset("stadium", "Stadium", 0.6f, 0.9f, 0.5f, 0.65f, 1f, 60f, 200f),
        ReverbPreset("dream", "Dreamy", 0.7f, 0.93f, 0.6f, 0.85f, 1f, 30f, 300f),
    )
}

@Serializable
data class ReverbSettings(
    val enabled: Boolean = false,
    val mix: Float = 0.5f,
    val roomSize: Float = 0.55f,
    val damping: Float = 0.45f,
    val wet: Float = 0.6f,
    val dry: Float = 1f,
    val width: Float = 1f,
    val preDelayMs: Float = 12f,
    val lowCutHz: Float = 180f,
    val presetId: String? = ReverbPresets.Default.id,
) {
    fun toParams() = ReverbParams(enabled, mix, roomSize, damping, wet, dry, width, preDelayMs, lowCutHz)

    fun withPreset(preset: ReverbPreset) = copy(
        mix = preset.mix,
        roomSize = preset.roomSize,
        damping = preset.damping,
        wet = preset.wet,
        dry = 1f,
        width = preset.width,
        preDelayMs = preset.preDelayMs,
        lowCutHz = preset.lowCutHz,
        presetId = preset.id,
    )
}

@Serializable
data class LoudnessSettings(
    val enabled: Boolean = false,
    val boostDb: Float = 4f,
    val compression: Float = 0.3f,
) {
    fun toParams() = LoudnessParams(enabled, boostDb, compression)
}

@Serializable
data class SpatialSettings(
    val enabled: Boolean = false,
    val mode: SpatialMode = SpatialMode.Crossfeed,
    val cutoffHz: Float = 700f,
    val feedDb: Float = 4.5f,
    val speakerAngle: Float = 30f,
    val amount: Float = 1f,
    val surround: Boolean = false,
    val mono: Boolean = false,
    val disableOnSpeaker: Boolean = true,
) {
    fun toParams() = SpatialParams(enabled, mode, cutoffHz, feedDb, speakerAngle, amount, surround, mono, disableOnSpeaker)
}

@Serializable
data class NormalizeSettings(
    val replayGainMode: ReplayGainMode = ReplayGainMode.Track,
    val preampDb: Float = 3f,
) {
    fun toParams() = ReplayGainParams(replayGainMode, preampDb)
}

@Serializable
data class PlaybackSettings(
    val speed: Float = 1f,
    val preservePitch: Boolean = true,
    val pitchSemitones: Float = 0f,
) {
    val pitch: Float
        get() = if (preservePitch) 2f.pow(pitchSemitones / 12f) else speed

    val isNormal: Boolean get() = speed == 1f && pitch == 1f
}

@Serializable
data class VolumeSettings(
    val enabled: Boolean = false,
    val exponent: Float = 2f,
    val limit: Float = 1f,
) {
    fun toCurve() = VolumeCurve(enabled, exponent, limit)
}

