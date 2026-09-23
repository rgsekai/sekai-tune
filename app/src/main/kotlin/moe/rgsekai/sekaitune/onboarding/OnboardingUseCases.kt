/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.onboarding

import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.R
import javax.inject.Inject

class ObserveOnboardingDataUseCase
    @Inject
    constructor(
        private val repository: OnboardingRepository,
    ) {
        operator fun invoke(): Flow<OnboardingData> =
            repository
                .observeShouldShowOnboarding()
                .map { shouldShowOnboarding ->
                    OnboardingData(shouldShowOnboarding = shouldShowOnboarding)
                }.flowOn(Dispatchers.IO)
    }

class BuildOnboardingUiStateUseCase
    @Inject
    constructor() {
        operator fun invoke(data: OnboardingData): OnboardingUiState =
            OnboardingUiState(
                shouldShowOnboarding = data.shouldShowOnboarding,
                variantLabelResId = variantLabelResId(),
                versionName = BuildConfig.VERSION_NAME,
                socialLinks = socialLinks,
            )

        private fun variantLabelResId(): Int =
            if (BuildConfig.DISTRIBUTION == DISTRIBUTION_GMS) {
                R.string.onboarding_gms_variant
            } else {
                R.string.onboarding_non_gms_variant
            }

        private companion object {
            const val DISTRIBUTION_GMS = "gms"

            val socialLinks =
                ImmutableList.of(
                    OnboardingSocialLinkUiModel(
                        id = "github",
                        iconResId = R.drawable.github,
                        url = "https://github.com/rgsekai/sekai-tune",
                        contentDescription = "GitHub",
                    ),
                    OnboardingSocialLinkUiModel(
                        id = "website",
                        iconResId = R.drawable.ic_website_color,
                        url = "https://rgsekai.github.io/sekai-tune/",
                        contentDescription = "Website",
                    ),
                    OnboardingSocialLinkUiModel(
                        id = "instagram",
                        iconResId = R.drawable.ic_instagram_color,
                        url = "https://www.instagram.com/sekaitune?stkn=MWJ6dWFhemZveWRkOA==",
                        contentDescription = "Instagram",
                    ),
                    OnboardingSocialLinkUiModel(
                        id = "telegram",
                        iconResId = R.drawable.ic_telegram_color,
                        url = "https://t.me/+-mT3ps-V32g3ZWFl",
                        contentDescription = "Telegram",
                    ),
                )
        }
    }

class CompleteOnboardingUseCase
    @Inject
    constructor(
        private val repository: OnboardingRepository,
    ) {
        suspend operator fun invoke() {
            repository.markCompleted()
        }
    }
