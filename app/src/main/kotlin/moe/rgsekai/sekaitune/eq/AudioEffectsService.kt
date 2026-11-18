package moe.rgsekai.sekaitune.eq

import moe.rgsekai.sekaitune.eq.audio.LoudnessParams
import moe.rgsekai.sekaitune.eq.audio.ReplayGainParams
import moe.rgsekai.sekaitune.eq.audio.ReverbParams
import moe.rgsekai.sekaitune.eq.audio.SpatialParams
import moe.rgsekai.sekaitune.eq.audio.VolumeCurve
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEffectsService @Inject constructor() {
    @Volatile
    var reverb: ReverbParams = ReverbParams()

    @Volatile
    var loudness: LoudnessParams = LoudnessParams()

    @Volatile
    var spatial: SpatialParams = SpatialParams()

    @Volatile
    var volumeCurve: VolumeCurve = VolumeCurve()

    @Volatile
    var normalize: Boolean = true

    @Volatile
    var replayGain: ReplayGainParams = ReplayGainParams()

    @Volatile
    var loudnessReductionDb: Float = 0f
}

