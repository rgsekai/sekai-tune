package moe.rgsekai.sekaitune.viewmodels

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import moe.rgsekai.sekaitune.eq.AudioEffectsService
import moe.rgsekai.sekaitune.eq.AudioEffectsStore
import moe.rgsekai.sekaitune.eq.LoudnessSettings
import moe.rgsekai.sekaitune.eq.NormalizeSettings
import moe.rgsekai.sekaitune.eq.PlaybackSettings
import moe.rgsekai.sekaitune.eq.ReverbSettings
import moe.rgsekai.sekaitune.eq.SpatialSettings
import moe.rgsekai.sekaitune.eq.VolumeSettings
import javax.inject.Inject

@HiltViewModel
class AudioEffectsViewModel @Inject constructor(
    private val store: AudioEffectsStore,
    val effectsService: AudioEffectsService,
) : ViewModel() {

    val reverb: StateFlow<ReverbSettings> = store.reverb
    val loudness: StateFlow<LoudnessSettings> = store.loudness
    val spatial: StateFlow<SpatialSettings> = store.spatial
    val playback: StateFlow<PlaybackSettings> = store.playback
    val volume: StateFlow<VolumeSettings> = store.volume
    val normalize: StateFlow<NormalizeSettings> = store.normalize

    fun updateReverb(transform: (ReverbSettings) -> ReverbSettings) {
        store.updateReverb(transform)
    }

    fun updateLoudness(transform: (LoudnessSettings) -> LoudnessSettings) {
        store.updateLoudness(transform)
    }

    fun updateSpatial(transform: (SpatialSettings) -> SpatialSettings) {
        store.updateSpatial(transform)
    }

    fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) {
        store.updatePlayback(transform)
    }

    fun updateVolume(transform: (VolumeSettings) -> VolumeSettings) {
        store.updateVolume(transform)
    }

    fun updateNormalize(transform: (NormalizeSettings) -> NormalizeSettings) {
        store.updateNormalize(transform)
    }

    fun setNormalizeEnabled(enabled: Boolean) {
        store.setNormalizeEnabled(enabled)
    }
}

