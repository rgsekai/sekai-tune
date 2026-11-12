package moe.rgsekai.sekaitune.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.constants.HideExplicitKey
import moe.rgsekai.sekaitune.constants.HideVideoKey
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.extensions.filterBlockedArtists
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.AlbumItem
import moe.rgsekai.sekaitune.innertube.models.AlbumReleaseType
import moe.rgsekai.sekaitune.innertube.models.filterExplicit
import moe.rgsekai.sekaitune.innertube.models.filterVideo
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.get
import moe.rgsekai.sekaitune.viewmodels.NewReleaseContent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewReleaseRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        private val mutex = Mutex()
        private var cachedContent: NewReleaseContent? = null
        private var lastFetchedTimestamp: Long = 0L

        companion object {
            private const val CACHE_TTL_MILLIS = 60 * 60 * 1000L // 1 hour TTL
        }

        suspend fun getNewReleases(forceRefresh: Boolean = false): Result<NewReleaseContent> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val now = System.currentTimeMillis()
                    val existing = cachedContent
                    if (!forceRefresh && existing != null && (now - lastFetchedTimestamp < CACHE_TTL_MILLIS)) {
                        return@withContext Result.success(existing)
                    }

                    try {
                        val albums = YouTube.newReleaseAlbums().getOrThrow()
                        val blockedArtistIds = database.getBlockedArtistIds().toSet()
                        val artistRanks: MutableMap<String, Int> = mutableMapOf()
                        val favouriteArtistRanks: MutableMap<String, Int> = mutableMapOf()

                        database.allArtistsByPlayTime().first().let { list ->
                            var favIndex = 0
                            for ((artistsIndex, artist) in list.withIndex()) {
                                artistRanks[artist.id] = artistsIndex
                                if (artist.artist.bookmarkedAt != null) {
                                    favouriteArtistRanks[artist.id] = favIndex
                                    favIndex++
                                }
                            }
                        }

                        val filtered =
                            albums
                                .sortedBy { album ->
                                    val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                    val firstArtistKey =
                                        artistIds.firstNotNullOfOrNull { artistId ->
                                            favouriteArtistRanks[artistId] ?: artistRanks[artistId]
                                        } ?: Int.MAX_VALUE
                                    firstArtistKey
                                }.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                .filterVideo(context.dataStore.get(HideVideoKey, false))
                                .filterBlockedArtists(blockedArtistIds)
                                .distinctBy { it.id }

                        val content = filtered.toNewReleaseContent()
                        cachedContent = content
                        lastFetchedTimestamp = now
                        Result.success(content)
                    } catch (t: Throwable) {
                        if (existing != null) {
                            Result.success(existing)
                        } else {
                            Result.failure(t)
                        }
                    }
                }
            }

        private fun List<AlbumItem>.toNewReleaseContent(): NewReleaseContent {
            val albumsList = filter { it.releaseType == AlbumReleaseType.ALBUM }
            val singlesList = filter { it.releaseType == AlbumReleaseType.SINGLE }
            val epsList = filter { it.releaseType == AlbumReleaseType.EP }

            return NewReleaseContent(
                albums = if (albumsList.isEmpty() && singlesList.isEmpty() && epsList.isEmpty()) this else albumsList,
                singles = singlesList,
                eps = epsList,
            )
        }
    }
