/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.onboarding

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rgsekai.sekaitune.constants.LaunchCountKey
import moe.rgsekai.sekaitune.constants.OnboardingCompletedKey
import moe.rgsekai.sekaitune.utils.dataStore
import javax.inject.Inject

class OnboardingRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun observeShouldShowOnboarding(): Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[OnboardingCompletedKey] != true
            }

        suspend fun markCompleted() {
            context.dataStore.edit { preferences ->
                preferences[OnboardingCompletedKey] = true
            }
        }
    }
