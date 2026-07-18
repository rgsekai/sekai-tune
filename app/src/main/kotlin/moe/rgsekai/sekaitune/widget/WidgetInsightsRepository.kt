/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.widget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.Artist
import moe.rgsekai.sekaitune.db.entities.LibraryTopMixEntity
import moe.rgsekai.sekaitune.db.entities.ListeningTotals
import moe.rgsekai.sekaitune.db.entities.Song
import moe.rgsekai.sekaitune.db.entities.SongWithStats
import java.time.Duration
import javax.inject.Inject

internal class WidgetInsightsRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        suspend fun load(nowMs: Long): WidgetInsightsData =
            withContext(Dispatchers.IO) {
                val fromMs = nowMs - Duration.ofDays(30).toMillis()
                WidgetInsightsData(
                    recentSongs = database.recentSongs(limit = 4).first(),
                    totals = database.listeningTotals(fromTimestamp = fromMs, toTimestamp = nowMs).first(),
                    topSongs = database.mostPlayedSongsStats(fromTimeStamp = fromMs, limit = 4, toTimeStamp = nowMs).first(),
                    recommendations = database.quickPicks(now = nowMs).first().take(6),
                    topArtists = database.mostPlayedArtists(fromTimeStamp = fromMs, limit = 6, toTimeStamp = nowMs).first(),
                    topMixes = database.libraryTopMixes(limit = 4).first(),
                )
            }
    }

internal data class WidgetInsightsData(
    val recentSongs: List<Song>,
    val totals: ListeningTotals,
    val topSongs: List<SongWithStats>,
    val recommendations: List<Song>,
    val topArtists: List<Artist>,
    val topMixes: List<LibraryTopMixEntity>,
)




