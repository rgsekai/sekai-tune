package moe.rgsekai.sekaitune.eq

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioEffectsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val audioEffectsService: AudioEffectsService,
) {
    private val prefs = context.getSharedPreferences("audio_effects", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _reverb = MutableStateFlow(load("reverb") ?: ReverbSettings())
    val reverb: StateFlow<ReverbSettings> = _reverb.asStateFlow()

    private val _loudness = MutableStateFlow(load("loudness") ?: LoudnessSettings())
    val loudness: StateFlow<LoudnessSettings> = _loudness.asStateFlow()

    private val _spatial = MutableStateFlow(load("spatial") ?: SpatialSettings())
    val spatial: StateFlow<SpatialSettings> = _spatial.asStateFlow()

    private val _playback = MutableStateFlow(load("playback") ?: PlaybackSettings())
    val playback: StateFlow<PlaybackSettings> = _playback.asStateFlow()

    private val _volume = MutableStateFlow(load("volume") ?: VolumeSettings())
    val volume: StateFlow<VolumeSettings> = _volume.asStateFlow()

    private val _normalize = MutableStateFlow(load("normalize") ?: NormalizeSettings())
    val normalize: StateFlow<NormalizeSettings> = _normalize.asStateFlow()

    init {
        pushEffects()
    }

    fun updateReverb(transform: (ReverbSettings) -> ReverbSettings) {
        _reverb.update(transform)
        pushEffects()
        scheduleSave()
    }

    fun updateLoudness(transform: (LoudnessSettings) -> LoudnessSettings) {
        _loudness.update(transform)
        pushEffects()
        scheduleSave()
    }

    fun updateSpatial(transform: (SpatialSettings) -> SpatialSettings) {
        _spatial.update(transform)
        pushEffects()
        scheduleSave()
    }

    fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) {
        _playback.update(transform)
        scheduleSave()
    }

    fun updateVolume(transform: (VolumeSettings) -> VolumeSettings) {
        _volume.update(transform)
        pushEffects()
        scheduleSave()
    }

    fun updateNormalize(transform: (NormalizeSettings) -> NormalizeSettings) {
        _normalize.update(transform)
        pushEffects()
        scheduleSave()
    }

    fun setNormalizeEnabled(enabled: Boolean) {
        audioEffectsService.normalize = enabled
    }

    private fun pushEffects() {
        audioEffectsService.reverb = _reverb.value.toParams()
        audioEffectsService.loudness = _loudness.value.toParams()
        audioEffectsService.spatial = _spatial.value.toParams()
        audioEffectsService.volumeCurve = _volume.value.toCurve()
        audioEffectsService.replayGain = _normalize.value.toParams()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            prefs.edit {
                putString("reverb", json.encodeToString(ReverbSettings.serializer(), _reverb.value))
                putString("loudness", json.encodeToString(LoudnessSettings.serializer(), _loudness.value))
                putString("spatial", json.encodeToString(SpatialSettings.serializer(), _spatial.value))
                putString("playback", json.encodeToString(PlaybackSettings.serializer(), _playback.value))
                putString("volume", json.encodeToString(VolumeSettings.serializer(), _volume.value))
                putString("normalize", json.encodeToString(NormalizeSettings.serializer(), _normalize.value))
            }
        }
    }

    private inline fun <reified T> load(key: String): T? =
        prefs.getString(key, null)?.let {
            runCatching { json.decodeFromString<T>(it) }.getOrNull()
        }
}

