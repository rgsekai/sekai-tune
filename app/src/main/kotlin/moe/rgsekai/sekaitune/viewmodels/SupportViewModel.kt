/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.about.GitHubStatsRepository
import javax.inject.Inject

@HiltViewModel
class SupportViewModel
    @Inject
    constructor(
        private val gitHubStatsRepository: GitHubStatsRepository,
    ) : ViewModel() {
        private val _starsCount = MutableStateFlow<Int?>(null)
        val starsCount: StateFlow<Int?> = _starsCount.asStateFlow()

        init {
            loadStars()
        }

        fun loadStars() {
            viewModelScope.launch {
                gitHubStatsRepository.getCachedStars()?.let { cached ->
                    _starsCount.value = cached
                }
                val fresh = gitHubStatsRepository.fetchStars()
                if (fresh != null) {
                    _starsCount.value = fresh
                }
            }
        }
    }

