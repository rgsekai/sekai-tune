/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.spotify

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.SyncLikesToSpotifyKey
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.SongEntity
import moe.rgsekai.sekaitune.utils.AppLifecycleTracker
import moe.rgsekai.sekaitune.utils.dataStore
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyLikeSyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val repository: SpotifyLibraryRepository,
    ) {
        private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val pendingJobs = ConcurrentHashMap<String, Job>()

        fun syncLike(song: SongEntity) {
            syncLike(
                songId = song.id,
                title = song.title,
                artist = null,
                durationSec = song.duration,
                targetLiked = song.liked,
            )
        }

        fun syncLike(
            songId: String,
            title: String,
            artist: String?,
            durationSec: Int,
            targetLiked: Boolean,
        ) {
            pendingJobs[songId]?.cancel()
            val job =
                syncScope.launch {
                    delay(DEBOUNCE_DELAY_MS)
                    performSync(songId, title, artist, durationSec, targetLiked)
                }
            pendingJobs[songId] = job
        }

        private suspend fun performSync(
            songId: String,
            title: String,
            artist: String?,
            durationSec: Int,
            targetLiked: Boolean,
        ) {
            try {
                val syncEnabled = context.dataStore.data.first()[SyncLikesToSpotifyKey] ?: false
                if (!syncEnabled) return

                val session = repository.restoreSession()
                if (!session.isAuthenticated) {
                    Timber.w("Spotify like sync skipped: not authenticated")
                    return
                }

                val resolvedArtist =
                    if (!artist.isNullOrBlank()) {
                        artist
                    } else {
                        database.song(songId).firstOrNull()?.artists?.firstOrNull()?.name.orEmpty()
                    }

                val matchResult =
                    retryWithBackoff {
                        SpotifyPlaybackResolver.resolveConfidentSpotifyUri(
                            youtubeId = songId,
                            title = title,
                            artist = resolvedArtist,
                            durationSec = durationSec,
                        )
                    }

                val spotifyUri =
                    matchResult.getOrElse { error ->
                        Timber.w(error, "Spotify track resolution failed for $songId: $title")
                        dispatchFeedback(context.getString(R.string.spotify_sync_failed))
                        return
                    }

                if (spotifyUri.isNullOrBlank()) {
                    Timber.d("Spotify like sync: no confident match for $title - $resolvedArtist")
                    dispatchFeedback(context.getString(R.string.spotify_no_confident_match))
                    return
                }

                val mutationResult =
                    retryWithBackoff {
                        if (targetLiked) {
                            repository.addTrackToLibrary(spotifyUri)
                        } else {
                            repository.removeTrackFromLibrary(spotifyUri)
                        }
                    }

                mutationResult.fold(
                    onSuccess = {
                        val msg =
                            if (targetLiked) {
                                context.getString(R.string.spotify_liked_added)
                            } else {
                                context.getString(R.string.spotify_liked_removed)
                            }
                        dispatchFeedback(msg)
                    },
                    onFailure = { error ->
                        Timber.e(error, "Spotify library mutation failed for $spotifyUri (targetLiked=$targetLiked)")
                        dispatchFeedback(context.getString(R.string.spotify_sync_failed))
                    },
                )
            } catch (t: Throwable) {
                Timber.e(t, "Unexpected error in SpotifyLikeSyncManager for $songId")
            } finally {
                pendingJobs.remove(songId)
            }
        }

        private fun dispatchFeedback(message: String) {
            if (AppLifecycleTracker.isForeground) {
                _feedbackEvents.tryEmit(message)
            } else {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        private suspend fun <T> retryWithBackoff(
            maxAttempts: Int = 3,
            initialDelayMs: Long = 1000L,
            maxDelayMs: Long = 3000L,
            factor: Double = 2.0,
            block: suspend () -> Result<T>,
        ): Result<T> {
            var currentDelay = initialDelayMs
            var lastResult: Result<T> = Result.failure(IllegalStateException("No attempt made"))

            for (attempt in 1..maxAttempts) {
                lastResult = block()
                if (lastResult.isSuccess) {
                    return lastResult
                }
                val throwable = lastResult.exceptionOrNull()
                val isRetryable =
                    when (throwable) {
                        is IOException -> true
                        is Spotify.SpotifyException -> throwable.statusCode == 429 || throwable.statusCode in 500..599
                        else -> false
                    }
                if (!isRetryable || attempt == maxAttempts) {
                    return lastResult
                }
                Timber.d(
                    "Spotify sync retryable error on attempt $attempt/$maxAttempts: ${throwable?.message}. Retrying in ${currentDelay}ms...",
                )
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
            return lastResult
        }

        companion object {
            private const val DEBOUNCE_DELAY_MS = 350L

            private val _feedbackEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
            val feedbackEvents: SharedFlow<String> = _feedbackEvents.asSharedFlow()
        }
    }

