/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.queues

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.BrowseEndpoint
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.models.QueueFilter
import timber.log.Timber

object QueueFilterProvider {

    suspend fun fetchFilteredQueue(
        seedSong: MediaMetadata,
        filter: QueueFilter,
        database: MusicDatabase,
        hideExplicit: Boolean = false,
        hideVideo: Boolean = false,
    ): Pair<List<MediaItem>, Queue> = withContext(Dispatchers.IO) {
        val seedId = seedSong.id.trim()
        val artistId = seedSong.artists.firstOrNull()?.id?.trim()
        val artistName = seedSong.artists.firstOrNull()?.name?.trim().orEmpty()

        val baseRadioQueue = YouTubeQueue(
            endpoint = WatchEndpoint(videoId = seedId),
            followAutomixPreview = true,
        )

        when (filter) {
            QueueFilter.ALL -> {
                val initialStatus = baseRadioQueue.getInitialStatus()
                    .filterExplicit(hideExplicit)
                    .filterVideo(hideVideo)
                val items = initialStatus.items.filter { it.mediaId != seedId }
                Pair(items, baseRadioQueue)
            }

            QueueFilter.DISCOVER -> {
                val radioItems = try {
                    baseRadioQueue.getInitialStatus()
                        .filterExplicit(hideExplicit)
                        .filterVideo(hideVideo)
                        .items
                } catch (e: Exception) {
                    Timber.w(e, "QueueFilterProvider: Failed to load initial radio for DISCOVER")
                    emptyList()
                }

                val relatedItems = fetchRelatedSongItems(seedId, hideExplicit)
                val allCandidates = (radioItems + relatedItems.map { it.toMediaItem() })
                    .filter { it.mediaId != seedId }
                    .distinctBy { it.mediaId }

                // Query local database for songs that are already in library or heavily played
                val localKnownSongIds = try {
                    val inLibrary = database.songsByRowIdAsc().first().map { it.song.id }.toSet()
                    val history = database.songsByPlayTimeAsc().first().map { it.song.id }.toSet()
                    inLibrary + history
                } catch (e: Exception) {
                    emptySet()
                }

                // Partition: prioritize undiscovered tracks (not in local library/history)
                val undiscovered = allCandidates.filter { it.mediaId !in localKnownSongIds }
                val remaining = allCandidates.filter { it.mediaId in localKnownSongIds }
                val finalItems = (undiscovered + remaining).take(50)

                Pair(finalItems, baseRadioQueue)
            }

            QueueFilter.FAMILIAR -> {
                val familiarMediaItems = mutableListOf<MediaItem>()

                // 1. Fetch user's local songs by the same artist(s) from database
                if (artistId != null || artistName.isNotEmpty()) {
                    try {
                        val artistSongs = database.songsByRowIdAsc().first()
                            .filter { song ->
                                song.artists.any { a ->
                                    a.name.equals(artistName, ignoreCase = true) || (artistId != null && a.id == artistId)
                                }
                            }
                            .map { it.toMediaItem() }
                        familiarMediaItems.addAll(artistSongs.filter { it.mediaId != seedId })
                    } catch (e: Exception) {
                        Timber.w(e, "QueueFilterProvider: Failed to load familiar artist songs from DB")
                    }
                }

                // 2. Add standard radio items that match familiar artists or user library
                try {
                    val radioStatus = baseRadioQueue.getInitialStatus()
                        .filterExplicit(hideExplicit)
                        .filterVideo(hideVideo)
                    val radioItems = radioStatus.items.filter { it.mediaId != seedId }
                    familiarMediaItems.addAll(radioItems)
                } catch (e: Exception) {
                    Timber.w(e, "QueueFilterProvider: Failed to load radio for FAMILIAR")
                }

                val finalItems = familiarMediaItems.distinctBy { it.mediaId }.take(50)
                Pair(finalItems, baseRadioQueue)
            }

            QueueFilter.POPULAR -> {
                val popularItems = mutableListOf<MediaItem>()

                // 1. Fetch artist top songs if artist browseId is available
                if (artistId != null && artistId.startsWith("UC")) {
                    try {
                        val artistResult = YouTube.artist(artistId).getOrNull()
                        val topSongs = artistResult?.sections
                            ?.flatMap { it.items }
                            ?.filterIsInstance<SongItem>()
                            ?.map { it.toMediaItem() }
                            ?.filter { it.mediaId != seedId }
                            .orEmpty()
                        popularItems.addAll(topSongs)
                    } catch (e: Exception) {
                        Timber.w(e, "QueueFilterProvider: Failed to load artist top songs for POPULAR")
                    }
                }

                // 2. Blend with related top hits and radio
                try {
                    val related = fetchRelatedSongItems(seedId, hideExplicit).map { it.toMediaItem() }
                    popularItems.addAll(related.filter { it.mediaId != seedId })

                    val radioStatus = baseRadioQueue.getInitialStatus()
                        .filterExplicit(hideExplicit)
                        .filterVideo(hideVideo)
                    popularItems.addAll(radioStatus.items.filter { it.mediaId != seedId })
                } catch (e: Exception) {
                    Timber.w(e, "QueueFilterProvider: Failed to blend radio for POPULAR")
                }

                val finalItems = popularItems.distinctBy { it.mediaId }.take(50)
                Pair(finalItems, baseRadioQueue)
            }

            QueueFilter.DEEP_CUTS -> {
                val deepCutItems = mutableListOf<MediaItem>()

                // 1. Fetch artist albums / deep songs from artist page
                if (artistId != null && artistId.startsWith("UC")) {
                    try {
                        val artistResult = YouTube.artist(artistId).getOrNull()
                        val topSongs = artistResult?.sections
                            ?.flatMap { it.items }
                            ?.filterIsInstance<SongItem>()
                            ?.take(5)
                            ?.map { it.id }
                            ?.toSet()
                            .orEmpty()

                        val otherSongs = artistResult?.sections
                            ?.flatMap { it.items }
                            ?.filterIsInstance<SongItem>()
                            ?.filter { it.id !in topSongs && it.id != seedId }
                            ?.map { it.toMediaItem() }
                            .orEmpty()
                        deepCutItems.addAll(otherSongs)
                    } catch (e: Exception) {
                        Timber.w(e, "QueueFilterProvider: Failed to load deep cuts from artist")
                    }
                }

                // 2. Fetch radio items and skip top hits, prioritize second half of automix
                try {
                    val radioStatus = baseRadioQueue.getInitialStatus()
                        .filterExplicit(hideExplicit)
                        .filterVideo(hideVideo)
                    val radioItems = radioStatus.items.filter { it.mediaId != seedId }
                    // Reverse/shuffle or take deeper items
                    val deeperRadio = if (radioItems.size > 8) radioItems.drop(4) else radioItems
                    deepCutItems.addAll(deeperRadio)
                } catch (e: Exception) {
                    Timber.w(e, "QueueFilterProvider: Failed to load radio for DEEP_CUTS")
                }

                val finalItems = deepCutItems.distinctBy { it.mediaId }.take(50)
                Pair(finalItems, baseRadioQueue)
            }
        }
    }

    private suspend fun fetchRelatedSongItems(
        videoId: String,
        hideExplicit: Boolean,
    ): List<SongItem> {
        return try {
            val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
            val relatedEndpoint = nextResult?.relatedEndpoint ?: BrowseEndpoint(browseId = "FErelated")
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull()
            val songs = relatedPage?.songs.orEmpty()
            if (hideExplicit) {
                songs.filter { !it.explicit }
            } else {
                songs
            }
        } catch (e: Exception) {
            Timber.w(e, "QueueFilterProvider: Failed to fetch related song items")
            emptyList()
        }
    }
}
