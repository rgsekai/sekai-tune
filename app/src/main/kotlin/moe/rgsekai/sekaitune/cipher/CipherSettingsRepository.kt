/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.cipher

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import moe.rgsekai.sekaitune.constants.MoriCipherManualRefreshHistoryKey
import moe.rgsekai.sekaitune.morideobfuscator.CipherRefreshResult
import moe.rgsekai.sekaitune.morideobfuscator.CipherSnapshot
import moe.rgsekai.sekaitune.morideobfuscator.MoriCipherRuntime
import moe.rgsekai.sekaitune.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CipherSettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val runtimeSnapshot: Flow<CipherSnapshot> = MoriCipherRuntime.snapshot

        val manualRefreshHistory: Flow<List<Long>> =
            context.dataStore.data.map { preferences ->
                preferences[MoriCipherManualRefreshHistoryKey].toManualRefreshHistory()
            }

        suspend fun getManualRefreshHistory(): List<Long> =
            context.dataStore.data
                .first()[MoriCipherManualRefreshHistoryKey]
                .toManualRefreshHistory()

        suspend fun refresh(): Result<CipherRefreshResult> =
            MoriCipherRuntime.refresh(force = true)

        suspend fun recordSuccessfulManualRefresh(
            timestampMillis: Long,
            windowStartsAtMillis: Long,
            windowEndsAtMillis: Long,
            maximumEntries: Int,
        ) {
            context.dataStore.edit { preferences ->
                val retained =
                    preferences[MoriCipherManualRefreshHistoryKey]
                        .toManualRefreshHistory()
                        .filter { it >= windowStartsAtMillis && it < windowEndsAtMillis }
                        .plus(timestampMillis)
                        .distinct()
                        .sorted()
                        .take(maximumEntries.coerceAtLeast(1))
                        .map(Long::toString)
                        .toSet()
                preferences[MoriCipherManualRefreshHistoryKey] = retained
            }
        }
    }

private fun Set<String>?.toManualRefreshHistory(): List<Long> =
    orEmpty()
        .mapNotNull(String::toLongOrNull)
        .distinct()
        .sorted()




