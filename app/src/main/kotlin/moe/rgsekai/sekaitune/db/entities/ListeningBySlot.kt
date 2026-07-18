/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class ListeningBySlot(
    val slot: Int,
    val timeListened: Long,
)

@Immutable
data class ListeningTotals(
    val totalPlayCount: Int,
    val totalTimeListened: Long,
)

@Immutable
data class ListeningSummary(
    val totalPlayCount: Int,
    val totalTimeListened: Long,
    val uniqueSongsCount: Int,
    val uniqueArtistsCount: Int,
    val uniqueAlbumsCount: Int,
)




