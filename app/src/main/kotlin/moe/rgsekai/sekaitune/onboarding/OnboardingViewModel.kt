/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        observeOnboardingDataUseCase: ObserveOnboardingDataUseCase,
        private val buildOnboardingUiStateUseCase: BuildOnboardingUiStateUseCase,
        private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    ) : ViewModel() {
        private val mutableEvents = MutableSharedFlow<OnboardingEvent>(extraBufferCapacity = 1)
        private var completionJob: Job? = null

        val events = mutableEvents.asSharedFlow()

        val screenState: StateFlow<OnboardingScreenState> =
            observeOnboardingDataUseCase()
                .map<OnboardingData, OnboardingScreenState> { data ->
                    val uiState = buildOnboardingUiStateUseCase(data)
                    OnboardingScreenState.Success(uiState)
                }.catch { emit(OnboardingScreenState.Error(R.string.onboarding_error_generic)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = OnboardingScreenState.Loading,
                )

        fun onOpenUri(url: String) {
            viewModelScope.launch {
                mutableEvents.emit(OnboardingEvent.OpenUri(url))
            }
        }

        fun onSupportClick() {
            viewModelScope.launch {
                completeOnboardingUseCase()
                mutableEvents.emit(OnboardingEvent.NavigateToSupport)
            }
        }

        fun onLoginClick() {
            viewModelScope.launch {
                completeOnboardingUseCase()
                mutableEvents.emit(OnboardingEvent.NavigateToLogin)
            }
        }

        fun complete() {
            if (completionJob?.isActive == true) return

            completionJob =
                viewModelScope.launch {
                    completeOnboardingUseCase()
                    mutableEvents.emit(OnboardingEvent.NavigateToHome)
                }
        }
    }
