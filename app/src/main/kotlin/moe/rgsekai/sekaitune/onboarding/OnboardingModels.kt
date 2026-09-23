/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList

sealed interface OnboardingScreenState {
    data object Loading : OnboardingScreenState

    data class Success(
        val uiState: OnboardingUiState,
    ) : OnboardingScreenState

    data object Empty : OnboardingScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : OnboardingScreenState
}

@Immutable
data class OnboardingUiState(
    val shouldShowOnboarding: Boolean,
    @StringRes val variantLabelResId: Int,
    val versionName: String,
    val socialLinks: ImmutableList<OnboardingSocialLinkUiModel>,
)

@Immutable
data class OnboardingSocialLinkUiModel(
    val id: String,
    @DrawableRes val iconResId: Int,
    val url: String,
    val contentDescription: String,
)

data class OnboardingData(
    val shouldShowOnboarding: Boolean,
)

sealed interface OnboardingEvent {
    data class OpenUri(
        val url: String,
    ) : OnboardingEvent

    data object NavigateToSupport : OnboardingEvent

    data object NavigateToLogin : OnboardingEvent

    data object NavigateToHome : OnboardingEvent
}
