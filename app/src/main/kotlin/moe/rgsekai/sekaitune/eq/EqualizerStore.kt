package moe.rgsekai.sekaitune.eq

import android.content.Context
import androidx.core.content.edit
import moe.rgsekai.sekaitune.constants.EqualizerBandLevelsMbKey
import moe.rgsekai.sekaitune.constants.EqualizerCustomProfilesJsonKey
import moe.rgsekai.sekaitune.constants.EqualizerEnabledKey
import moe.rgsekai.sekaitune.eq.audio.Biquad
import moe.rgsekai.sekaitune.eq.audio.PeakLimiter
import moe.rgsekai.sekaitune.eq.audio.ResponseCurve
import moe.rgsekai.sekaitune.eq.data.FilterType
import moe.rgsekai.sekaitune.eq.data.ParametricEQ
import moe.rgsekai.sekaitune.eq.data.ParametricEQBand
import moe.rgsekai.sekaitune.playback.EqProfilesPayload
import moe.rgsekai.sekaitune.playback.EqualizerJson
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
import kotlin.math.max

@Singleton
class EqualizerStore @Inject constructor(
    @ApplicationContext context: Context,
    private val equalizerService: EqualizerService,
) {
    private val prefs = context.getSharedPreferences("equalizer_store", Context.MODE_PRIVATE)
    private val appPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null
    private val headroomCurve = ResponseCurve(ResponseCurve.logSpaced(160))
    private val boostRoom = headroomCurve.frequencies.map(PeakLimiter::boostRoomDb).toDoubleArray()

    private val _eq = MutableStateFlow(load("eq") ?: EqSettings())
    val eq: StateFlow<EqSettings> = _eq.asStateFlow()

    private val _presets = MutableStateFlow(load<List<EqPreset>>("presets") ?: emptyList())
    val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()

    init {
        migrateLegacyEqualizer()
        pushEq()
    }

    fun updateEq(transform: (EqSettings) -> EqSettings) {
        _eq.update(transform)
        pushEq()
        scheduleSave()
    }

    fun savePreset(name: String) {
        val current = _eq.value
        val preset = EqPreset("user_${System.currentTimeMillis()}", name.trim(), current.nodes, current.preamp)
        _presets.update { it + preset }
        updateEq { it.copy(presetId = preset.id) }
    }

    fun deletePreset(id: String) {
        _presets.update { list -> list.filterNot { it.id == id } }
        if (_eq.value.presetId == id) updateEq { it.copy(presetId = null) } else scheduleSave()
    }

    fun autoHeadroom(settings: EqSettings = _eq.value): Double = synchronized(headroomCurve) {
        val response = headroomCurve.response(audibleBands(settings))
        var over = 0.0
        for (i in response.indices) over = max(over, response[i] - boostRoom[i])
        -over
    }

    fun design(settings: EqSettings): ParametricEQ? {
        if (!settings.enabled) return null
        val bands = audibleBands(settings)
        val correctionOn = settings.correctionEnabled && settings.correction != null
        val headroom = when {
            settings.autoPreamp -> autoHeadroom(settings)
            correctionOn -> settings.correction!!.preamp
            else -> 0.0
        }
        val preamp = settings.preamp + headroom
        if (bands.isEmpty() && preamp == 0.0) return null
        return ParametricEQ(preamp = preamp, bands = bands)
    }

    private fun audibleBands(settings: EqSettings): List<ParametricEQBand> {
        val own = settings.nodes.filter(Biquad::isAudible)
        val correction = if (settings.correctionEnabled) settings.correction?.filters.orEmpty() else emptyList()
        return own + correction.filter(Biquad::isAudible)
    }

    private fun pushEq() = equalizerService.apply(design(_eq.value))

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            prefs.edit {
                putString("eq", json.encodeToString(EqSettings.serializer(), _eq.value))
                putString(
                    "presets",
                    json.encodeToString(kotlinx.serialization.builtins.ListSerializer(EqPreset.serializer()), _presets.value),
                )
            }
        }
    }

    private inline fun <reified T> load(key: String): T? = prefs.getString(key, null)?.let {
        runCatching { json.decodeFromString<T>(it) }.getOrNull()
    }

    private fun migrateLegacyEqualizer() {
        if (prefs.getBoolean("migrated_legacy_eq", false)) return
        runCatching {
            val legacyJson = appPrefs.getString(EqualizerCustomProfilesJsonKey.name, null)
            if (!legacyJson.isNullOrBlank()) {
                val payload = runCatching {
                    EqualizerJson.json.decodeFromString<EqProfilesPayload>(legacyJson)
                }.getOrNull()
                val converted = payload?.profiles?.mapNotNull { profile ->
                    if (profile.bandCenterFreqHz.isEmpty() || profile.bandLevelsMb.isEmpty()) return@mapNotNull null
                    val bands = profile.bandCenterFreqHz.mapIndexed { i, freq ->
                        val levelMb = profile.bandLevelsMb.getOrElse(i) { 0 }
                        val gainDb = levelMb / 100.0
                        ParametricEQBand(
                            frequency = freq.toDouble(),
                            gain = gainDb,
                            q = 1.41,
                            filterType = FilterType.PK,
                        )
                    }
                    val preampDb = profile.outputGainMb / 100.0
                    EqPreset(
                        id = "legacy_${profile.id}",
                        name = profile.name,
                        nodes = bands,
                        preamp = preampDb,
                    )
                }.orEmpty()
                if (converted.isNotEmpty()) {
                    _presets.update { it + converted }
                }
            }

            val legacyEnabled = appPrefs.getBoolean(EqualizerEnabledKey.name, false)
            val legacyLevelsRaw = appPrefs.getString(EqualizerBandLevelsMbKey.name, null)
            if (prefs.getString("eq", null) == null && !legacyLevelsRaw.isNullOrBlank()) {
                val levels = runCatching {
                    EqualizerJson.json.decodeFromString<List<Int>>(legacyLevelsRaw)
                }.getOrNull()
                if (!levels.isNullOrEmpty()) {
                    val nodes = EqPresets.Frequencies.mapIndexed { i, freq ->
                        val levelMb = levels.getOrElse(i) { 0 }
                        ParametricEQBand(
                            frequency = freq,
                            gain = levelMb / 100.0,
                            q = 1.41,
                            filterType = FilterType.PK,
                        )
                    }
                    _eq.value = EqSettings(
                        enabled = legacyEnabled,
                        nodes = nodes,
                        presetId = null,
                    )
                }
            }
        }
        prefs.edit { putBoolean("migrated_legacy_eq", true) }
        scheduleSave()
    }
}

