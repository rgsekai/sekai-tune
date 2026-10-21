/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.playback.sources.RegisteredSource
import moe.rgsekai.sekaitune.playback.sources.SourceRegistry
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val sourceRegistry: SourceRegistry,
) : ViewModel() {

    val sources: StateFlow<List<RegisteredSource>> = sourceRegistry.sourcesFlow
    val toleranceSeconds: StateFlow<Int> = sourceRegistry.toleranceSecondsFlow

    private val _testingSourceId = MutableStateFlow<String?>(null)
    val testingSourceId: StateFlow<String?> = _testingSourceId.asStateFlow()

    private val _isAdding = MutableStateFlow(false)
    val isAdding: StateFlow<Boolean> = _isAdding.asStateFlow()

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

    fun toggleSource(id: String, enabled: Boolean) {
        sourceRegistry.setSourceEnabled(id, enabled)
    }

    fun reorderSources(fromIndex: Int, toIndex: Int) {
        sourceRegistry.reorderSources(fromIndex, toIndex)
    }

    fun removeSource(id: String) {
        sourceRegistry.removeSource(id)
    }

    fun setTolerance(seconds: Int) {
        sourceRegistry.setToleranceSeconds(seconds)
    }

    fun testSource(id: String) {
        viewModelScope.launch {
            _testingSourceId.value = id
            try {
                sourceRegistry.testSource(id)
            } finally {
                _testingSourceId.value = null
            }
        }
    }

    fun addAddon(url: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isAdding.value = true
            _addError.value = null
            val result = sourceRegistry.addAddon(url)
            _isAdding.value = false
            if (result.isSuccess) {
                onSuccess()
            } else {
                _addError.value = result.exceptionOrNull()?.message ?: "Failed to add addon source"
            }
        }
    }

    fun clearAddError() {
        _addError.value = null
    }
}
