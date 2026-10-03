/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.lyrics

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.LyricsEntity
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.utils.NetworkConnectivityObserver
import moe.rgsekai.sekaitune.utils.reportException
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates fetching of lyrics for songs between LyricsScreen and OneLineLyrics.
 * Ensures:
 * - ONE in-flight fetch per mediaId (shared/joined)
 * - Session-level LRU cache (128 entries) for negative/failed results to prevent repeat attempts
 * - Early check against Room database to avoid redundant network calls
 */
object LyricsFetchManager {
    private const val MAX_NEGATIVE_CACHE_SIZE = 128

    private val inFlightFetches = ConcurrentHashMap<String, Deferred<Unit>>()
    private val inFlightMutex = Mutex()

    private val failedMediaIds: MutableMap<String, Boolean> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Boolean>(MAX_NEGATIVE_CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>): Boolean =
                    size > MAX_NEGATIVE_CACHE_SIZE
            },
        )

    /**
     * Clears negative cache for a song (e.g. when the user manually refreshes lyrics).
     */
    fun clearNegativeCache(mediaId: String) {
        failedMediaIds.remove(mediaId)
    }

    /**
     * Fetches lyrics for the given [mediaMetadata] if not already present in the database.
     * Joins any existing in-flight fetch for the same [mediaMetadata.id].
     *
     * @param context Application or UI context
     * @param database Room MusicDatabase
     * @param lyricsHelper Shared LyricsHelper instance
     * @param mediaMetadata Song metadata to fetch lyrics for
     * @param force If true, bypasses session negative cache
     */
    suspend fun fetchLyricsForSong(
        context: Context,
        database: MusicDatabase,
        lyricsHelper: LyricsHelper,
        mediaMetadata: MediaMetadata,
        force: Boolean = false,
    ) {
        val mediaId = mediaMetadata.id
        if (mediaId.isBlank()) return

        if (!force && failedMediaIds.containsKey(mediaId)) {
            return
        }

        // 1. Check if database already has valid lyrics
        try {
            val existing = withContext(Dispatchers.IO) {
                database.lyrics(mediaId).first()
            }
            if (existing != null) {
                return
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Proceed to fetch if DB query fails
        }

        val isConnected = NetworkConnectivityObserver(context).isCurrentlyConnected()
        if (!isConnected) {
            return
        }

        // 2. Join in-flight fetch if available, or create one
        var deferredToAwait: Deferred<Unit>? = null

        coroutineScope {
            inFlightMutex.withLock {
                val existingDeferred = inFlightFetches[mediaId]
                if (existingDeferred != null) {
                    deferredToAwait = existingDeferred
                } else {
                    val deferred = async<Unit>(Dispatchers.IO) {
                        try {
                            val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                            if (lyrics.isNotBlank() && lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                                database.query {
                                    insertLyricsIfAbsent(
                                        id = mediaId,
                                        lyrics = lyrics,
                                    )
                                }
                                failedMediaIds.remove(mediaId)
                            } else {
                                failedMediaIds[mediaId] = true
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            failedMediaIds[mediaId] = true
                            reportException(e)
                        } finally {
                            inFlightFetches.remove(mediaId)
                        }
                    }
                    inFlightFetches[mediaId] = deferred
                    deferredToAwait = deferred
                }
            }

            deferredToAwait?.await()
        }
    }
}
