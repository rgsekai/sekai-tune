package moe.rgsekai.sekaitune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import moe.rgsekai.sekaitune.eq.AutoEqCorrection
import moe.rgsekai.sekaitune.eq.EqPresets
import moe.rgsekai.sekaitune.eq.EqSettings
import moe.rgsekai.sekaitune.eq.EqualizerStore
import moe.rgsekai.sekaitune.eq.audio.Biquad
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqEntry
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqFitter
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqMeasurement
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqModel
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqRepository
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqTuning
import moe.rgsekai.sekaitune.eq.data.FilterType
import moe.rgsekai.sekaitune.eq.data.ParametricEQBand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

sealed interface AutoEqIndex {
    data object Loading : AutoEqIndex
    data class Failed(val message: String) : AutoEqIndex
    data class Ready(val models: List<AutoEqModel>) : AutoEqIndex
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    val store: EqualizerStore,
    private val autoEq: AutoEqRepository,
) : ViewModel() {

    val eq = store.eq
    val presets = store.presets

    fun setEnabled(enabled: Boolean) = store.updateEq { it.copy(enabled = enabled) }

    fun updateNode(index: Int, transform: (ParametricEQBand) -> ParametricEQBand) = store.updateEq { s ->
        if (index !in s.nodes.indices) s
        else s.copy(nodes = s.nodes.toMutableList().also { it[index] = transform(it[index]) }, presetId = null)
    }

    fun addNode(frequency: Double, gain: Double = 0.0): Int {
        var index = -1
        store.updateEq { s ->
            if (s.nodes.size >= MaxNodes) return@updateEq s
            val node = ParametricEQBand(
                frequency = frequency.coerceIn(20.0, 20_000.0),
                gain = gain,
                q = 1.0,
                filterType = FilterType.PK,
            )
            index = s.nodes.size
            s.copy(nodes = s.nodes + node, presetId = null)
        }
        return index
    }

    fun removeNode(index: Int) = store.updateEq { s ->
        if (s.nodes.size <= 1 || index !in s.nodes.indices) s
        else s.copy(nodes = s.nodes.filterIndexed { i, _ -> i != index }, presetId = null)
    }

    fun applyPreset(id: String) {
        val builtIn = EqPresets.BuiltIn.firstOrNull { it.id == id }
        val user = presets.value.firstOrNull { it.id == id }
        store.updateEq { s ->
            when {
                builtIn != null -> s.copy(nodes = builtIn.nodes, presetId = id)
                user != null -> s.copy(nodes = user.nodes, preamp = user.preamp, presetId = id)
                else -> s
            }
        }
    }

    fun reset() = store.updateEq { it.copy(nodes = EqPresets.flatNodes(), preamp = 0.0, presetId = EqPresets.FlatId) }

    fun savePreset(name: String) {
        if (name.isNotBlank()) store.savePreset(name)
    }

    fun deletePreset(id: String) = store.deletePreset(id)

    fun setPreamp(db: Double) = store.updateEq { it.copy(preamp = db) }

    fun setAutoPreamp(on: Boolean) = store.updateEq { it.copy(autoPreamp = on) }

    fun autoHeadroom(settings: EqSettings): Double = store.autoHeadroom(settings)

    // ---- AutoEQ ----

    private val _index = MutableStateFlow<AutoEqIndex>(AutoEqIndex.Loading)
    val index: StateFlow<AutoEqIndex> = _index.asStateFlow()

    val query = MutableStateFlow("")

    @OptIn(FlowPreview::class)
    val results: StateFlow<List<AutoEqModel>> = combine(_index, query.debounce(120)) { idx, q ->
        if (idx is AutoEqIndex.Ready) AutoEqRepository.search(idx.models, q) else emptyList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _measurement = MutableStateFlow<AutoEqMeasurement?>(null)
    val measurement: StateFlow<AutoEqMeasurement?> = _measurement.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var indexJob: Job? = null
    private var fitJob: Job? = null

    fun openAutoEq() {
        if (_index.value !is AutoEqIndex.Ready && indexJob?.isActive != true) loadIndex(refresh = false)
        val current = eq.value.correction
        if (current != null && _measurement.value?.entry?.path != current.path) {
            viewModelScope.launch {
                runCatching { autoEq.measurement(current.toEntry()) }.onSuccess { _measurement.value = it }
            }
        }
    }

    fun loadIndex(refresh: Boolean) {
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            if (_index.value !is AutoEqIndex.Ready) _index.value = AutoEqIndex.Loading
            _index.value = runCatching { AutoEqIndex.Ready(autoEq.models(refresh)) }
                .getOrElse { AutoEqIndex.Failed(it.message ?: "Couldn't reach the AutoEq database") }
        }
    }

    fun select(entry: AutoEqEntry) {
        fitJob?.cancel()
        fitJob = viewModelScope.launch {
            _busy.value = entry.path
            _error.value = null
            runCatching {
                val m = autoEq.measurement(entry)
                val tuning = AutoEqTuning()
                val fit = withContext(Dispatchers.Default) { AutoEqFitter.correction(m, tuning) }
                _measurement.value = m
                store.updateEq {
                    it.copy(
                        enabled = true,
                        correctionEnabled = true,
                        correction = AutoEqCorrection(
                            model = entry.name,
                            source = entry.source,
                            rig = entry.rig,
                            path = entry.path,
                            form = entry.form,
                            tuning = tuning,
                            filters = fit.bands,
                            preamp = fit.preamp,
                        ),
                    )
                }
            }.onFailure { _error.value = "Couldn't load ${entry.name}. Check your connection." }
            _busy.value = null
        }
    }

    fun tune(transform: (AutoEqTuning) -> AutoEqTuning) {
        val current = eq.value.correction ?: return
        val m = _measurement.value ?: return
        val tuning = transform(current.tuning)
        store.updateEq { it.copy(correction = current.copy(tuning = tuning)) }
        fitJob?.cancel()
        fitJob = viewModelScope.launch {
            delay(140)
            _busy.value = "fit"
            val fit = withContext(Dispatchers.Default) { AutoEqFitter.correction(m, tuning) }
            store.updateEq { s ->
                val c = s.correction ?: return@updateEq s
                if (c.path != current.path) s else s.copy(correction = c.copy(tuning = tuning, filters = fit.bands, preamp = fit.preamp))
            }
            _busy.value = null
        }
    }

    fun setCorrectionEnabled(on: Boolean) = store.updateEq { it.copy(correctionEnabled = on, enabled = it.enabled || on) }

    fun clearCorrection() {
        _measurement.value = null
        store.updateEq { it.copy(correction = null) }
    }

    companion object {
        const val MaxNodes = 16
    }
}

private fun AutoEqCorrection.toEntry() = AutoEqEntry(model, source, rig, path, form)

fun frequencyLabel(hz: Double): String = when {
    hz >= 10_000 -> "${(hz / 1000).toInt()}k"
    hz >= 1000 -> String.format(Locale.US, "%.1fk", hz / 1000)
    else -> "${hz.toInt()}Hz"
}

fun gainLabel(db: Double): String = (if (db > 0.04) "+" else if (db < -0.04) "−" else "") + String.format(Locale.US, "%.1f dB", abs(db))

fun filterTypeLabel(type: FilterType): String = when (type) {
    FilterType.PK -> "Peak"
    FilterType.LSC -> "Low shelf"
    FilterType.HSC -> "High shelf"
    FilterType.LPQ -> "Low pass"
    FilterType.HPQ -> "High pass"
}

fun EqSettings.isFlat(): Boolean = nodes.none(Biquad::isAudible) && preamp == 0.0 &&
    (!correctionEnabled || correction == null)

