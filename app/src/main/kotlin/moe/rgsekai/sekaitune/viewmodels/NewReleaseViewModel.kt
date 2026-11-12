/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.innertube.models.AlbumItem
import moe.rgsekai.sekaitune.repository.NewReleaseRepository
import moe.rgsekai.sekaitune.utils.reportException
import javax.inject.Inject

@Immutable
data class NewReleaseContent(
    val albums: List<AlbumItem>,
    val singles: List<AlbumItem>,
    val eps: List<AlbumItem>,
) {
    val totalReleases: Int
        get() = albums.size + singles.size + eps.size

    val isEmpty: Boolean
        get() = totalReleases == 0
}

sealed interface NewReleaseUiState {
    data object Loading : NewReleaseUiState

    data class Success(
        val content: NewReleaseContent,
    ) : NewReleaseUiState

    data object Empty : NewReleaseUiState

    data object Error : NewReleaseUiState
}

@HiltViewModel
class NewReleaseViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val newReleaseRepository: NewReleaseRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<NewReleaseUiState>(NewReleaseUiState.Loading)
        val uiState = _uiState.asStateFlow()

        init {
            load(forceRefresh = false)
        }

        fun retry() {
            load(forceRefresh = true)
        }

        private fun load(forceRefresh: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                if (_uiState.value !is NewReleaseUiState.Success) {
                    _uiState.value = NewReleaseUiState.Loading
                }
                newReleaseRepository
                    .getNewReleases(forceRefresh = forceRefresh)
                    .onSuccess { content ->
                        _uiState.value =
                            if (content.isEmpty) {
                                NewReleaseUiState.Empty
                            } else {
                                NewReleaseUiState.Success(content)
                            }
                    }
                    .onFailure { t ->
                        reportException(t)
                        if (_uiState.value !is NewReleaseUiState.Success) {
                            _uiState.value = NewReleaseUiState.Error
                        }
                    }
            }
        }
    }
