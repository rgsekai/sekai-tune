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
import moe.rgsekai.sekaitune.constants.SongSortType
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.Playlist
import moe.rgsekai.sekaitune.db.entities.PlaylistEntity
import moe.rgsekai.sekaitune.db.entities.Song
import java.time.Duration
import javax.inject.Inject

internal class WidgetShortcutsRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        suspend fun loadShortcuts(nowMs: Long = System.currentTimeMillis()): List<WidgetShortcutItem> =
            withContext(Dispatchers.IO) {
                val fromMs = nowMs - Duration.ofDays(60).toMillis()

                // 1. Liked Songs (Slot 1)
                val likedSongs = database.likedSongs(
                    sortType = SongSortType.CREATE_DATE,
                    descending = true
                ).first()
                val likedArt = likedSongs.firstOrNull()?.thumbnailUrl
                val slot1 = WidgetShortcutItem(
                    title = "Liked Songs",
                    subtitle = "${likedSongs.size} songs",
                    artPathOrUrl = likedArt,
                    type = WidgetShortcutType.LIKED_SONGS,
                    targetId = PlaylistEntity.LIKED_PLAYLIST_ID,
                )

                // 2. Playlists ranked by play counts and recency
                val allPlaylists = database.playlistsByUpdatedDateAsc().first().asReversed()
                    .filter { it.id != PlaylistEntity.LIKED_PLAYLIST_ID && it.id != PlaylistEntity.DOWNLOADED_PLAYLIST_ID }

                val playCounts = try {
                    database.playlistPlayCounts().first().associateBy({ it.playlistId }, { it.playCount })
                } catch (_: Exception) {
                    emptyMap()
                }

                // Sort top played playlists descending
                val sortedByPlayCount = allPlaylists.sortedByDescending { playCounts[it.id] ?: 0L }
                val topPlaylists = mutableListOf<Playlist>()
                sortedByPlayCount.forEach { pl ->
                    if (topPlaylists.none { it.id == pl.id }) {
                        topPlaylists.add(pl)
                    }
                }

                // Slot candidates: most recently updated distinct playlists
                val mostRecentCandidates = allPlaylists.filter { pl -> topPlaylists.take(4).none { it.id == pl.id } }

                // 3. Top Played Songs for Fallback
                val topSongs = try {
                    database.mostPlayedSongs(fromTimeStamp = fromMs, limit = 10, toTimeStamp = nowMs).first()
                } catch (_: Exception) {
                    emptyList<Song>()
                }.ifEmpty {
                    database.songsByNameAsc().first().take(10)
                }

                val result = mutableListOf<WidgetShortcutItem>()
                result.add(slot1)

                val selectedPlaylists = mutableListOf<Playlist>()
                topPlaylists.forEach { pl ->
                    if (selectedPlaylists.size < 7 && selectedPlaylists.none { it.id == pl.id }) {
                        selectedPlaylists.add(pl)
                    }
                }
                mostRecentCandidates.forEach { pl ->
                    if (selectedPlaylists.size < 7 && selectedPlaylists.none { it.id == pl.id }) {
                        selectedPlaylists.add(pl)
                    }
                }

                var playlistIndex = 0
                var songIndex = 0

                // Fill slots 2 through 8
                for (slot in 2..8) {
                    if (playlistIndex < selectedPlaylists.size) {
                        val pl = selectedPlaylists[playlistIndex++]
                        result.add(
                            WidgetShortcutItem(
                                title = pl.title,
                                subtitle = "${pl.songCount} songs",
                                artPathOrUrl = pl.thumbnails.firstOrNull(),
                                type = WidgetShortcutType.PLAYLIST,
                                targetId = pl.id,
                            )
                        )
                    } else if (songIndex < topSongs.size) {
                        val song = topSongs[songIndex++]
                        val artistName = song.artists.firstOrNull()?.name.orEmpty()
                        result.add(
                            WidgetShortcutItem(
                                title = song.title,
                                subtitle = if (artistName.isNotBlank()) artistName else "Song",
                                artPathOrUrl = song.thumbnailUrl,
                                type = WidgetShortcutType.SONG,
                                targetId = song.id,
                            )
                        )
                    } else {
                        // Empty / zero-history state
                        result.add(WidgetShortcutItem.Empty)
                    }
                }

                result
            }
    }
