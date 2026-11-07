/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobble

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.ScrobbleSendLikesKey
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.utils.dataStore
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScrobbleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushLocks = mapOf(
        ScrobbleProvider.LASTFM to Mutex(),
        ScrobbleProvider.LIBREFM to Mutex(),
    )

    private val queues = mapOf(
        ScrobbleProvider.LASTFM to ScrobbleQueue(File(context.filesDir, ScrobbleProvider.LASTFM.queueFileName)),
        ScrobbleProvider.LIBREFM to ScrobbleQueue(File(context.filesDir, ScrobbleProvider.LIBREFM.queueFileName)),
    )

    init {
        scope.launch {
            queues.values.forEach { it.load() }
            ScrobbleProvider.entries.forEach { provider ->
                if ((queues[provider]?.count?.value ?: 0) > 0) {
                    flushQueue(provider)
                }
            }
        }
    }

    fun getPendingCountFlow(provider: ScrobbleProvider): StateFlow<Int> {
        return queues[provider]?.count ?: error("Unknown provider $provider")
    }

    fun onNowPlaying(metadata: MediaMetadata, durationSeconds: Int) {
        scope.launch {
            val prefs = context.dataStore.data.first()
            val primaryArtistName = metadata.artists.firstOrNull()?.name.orEmpty()
            val allArtistsJoined = metadata.artists.joinToString(", ") { it.name }

            ScrobbleProvider.entries.forEach { provider ->
                val enabled = prefs[provider.enabledPref] ?: false
                val nowPlayingEnabled = prefs[provider.nowPlayingPref] ?: true
                val sessionKey = prefs[provider.sessionKeyPref].orEmpty()

                if (enabled && nowPlayingEnabled && sessionKey.isNotBlank()) {
                    val usePrimary = prefs[provider.primaryArtistOnlyPref] ?: false
                    val artist = if (usePrimary) primaryArtistName else allArtistsJoined
                    val item = ScrobbleItem(
                        artist = artist,
                        title = metadata.title,
                        album = metadata.album?.title?.takeIf { it.isNotBlank() },
                        durationSeconds = durationSeconds.takeIf { it > 0 },
                    )
                    try {
                        provider.client.updateNowPlaying(sessionKey, item)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("ScrobbleManager").w(e, "Failed to submit now playing to ${provider.title}")
                        if (e is ScrobblerApiException && e.isSessionInvalid) {
                            handleSessionInvalid(provider)
                        }
                    }
                }
            }
        }
    }

    fun onScrobble(metadata: MediaMetadata, durationSeconds: Int, timestampSeconds: Long) {
        scope.launch {
            val prefs = context.dataStore.data.first()
            val primaryArtistName = metadata.artists.firstOrNull()?.name.orEmpty()
            val allArtistsJoined = metadata.artists.joinToString(", ") { it.name }

            ScrobbleProvider.entries.forEach { provider ->
                val enabled = prefs[provider.enabledPref] ?: false
                val scrobbleEnabled = prefs[provider.scrobblePref] ?: true
                val sessionKey = prefs[provider.sessionKeyPref].orEmpty()

                if (enabled && scrobbleEnabled && sessionKey.isNotBlank()) {
                    val usePrimary = prefs[provider.primaryArtistOnlyPref] ?: false
                    val artist = if (usePrimary) primaryArtistName else allArtistsJoined
                    val item = ScrobbleItem(
                        artist = artist,
                        title = metadata.title,
                        album = metadata.album?.title?.takeIf { it.isNotBlank() },
                        durationSeconds = durationSeconds.takeIf { it > 0 },
                        timestamp = timestampSeconds,
                    )
                    val queue = queues[provider] ?: return@forEach
                    queue.enqueue(item)
                    flushQueue(provider)
                }
            }
        }
    }

    /**
     * Sends love / unlove when a user likes or unlikes a track.
     * Fires once in a coroutine without queueing or retries.
     */
    fun love(artist: String, title: String, love: Boolean) {
        if (artist.isBlank() || title.isBlank()) return
        scope.launch {
            val prefs = context.dataStore.data.first()
            val sendLikesEnabled = prefs[ScrobbleSendLikesKey] ?: false
            if (!sendLikesEnabled) return@launch

            ScrobbleProvider.entries.forEach { provider ->
                val enabled = prefs[provider.enabledPref] ?: false
                val sessionKey = prefs[provider.sessionKeyPref].orEmpty()
                if (enabled && sessionKey.isNotBlank()) {
                    val usePrimary = prefs[provider.primaryArtistOnlyPref] ?: false
                    val targetArtist = if (usePrimary) artist.split(",", "/", "&").firstOrNull()?.trim().orEmpty().ifBlank { artist } else artist
                    try {
                        provider.client.love(sessionKey, targetArtist, title, love)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("ScrobbleManager").w(e, "Failed to send love ($love) to ${provider.title}")
                        if (e is ScrobblerApiException && e.isSessionInvalid) {
                            handleSessionInvalid(provider)
                        }
                    }
                }
            }
        }
    }

    suspend fun flushQueue(provider: ScrobbleProvider) {
        val mutex = flushLocks[provider] ?: return
        val queue = queues[provider] ?: return
        mutex.withLock {
            val prefs = context.dataStore.data.first()
            val sessionKey = prefs[provider.sessionKeyPref].orEmpty()
            if (sessionKey.isBlank()) return@withLock

            val pending = queue.peekAll()
            if (pending.isEmpty()) return@withLock

            var hasNetworkIssue = false
            for (item in pending) {
                try {
                    provider.client.scrobble(sessionKey, item)
                    queue.remove(item)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("ScrobbleManager").w(e, "Error flushing scrobble to ${provider.title}")
                    if (e is ScrobblerApiException) {
                        if (e.isSessionInvalid) {
                            handleSessionInvalid(provider)
                            break
                        } else if (e.isTemporary) {
                            hasNetworkIssue = true
                            break
                        } else {
                            // Non-retryable error, drop item to avoid blocking queue
                            queue.remove(item)
                        }
                    } else {
                        hasNetworkIssue = true
                        break
                    }
                }
            }

            if (hasNetworkIssue && queue.count.value > 0) {
                scheduleRetryWorker(provider)
            }
        }
    }

    private fun scheduleRetryWorker(provider: ScrobbleProvider) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ScrobbleSyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ScrobbleSyncWorker.KEY_PROVIDER_ID to provider.id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "scrobble_flush_${provider.id}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private suspend fun handleSessionInvalid(provider: ScrobbleProvider) {
        Timber.tag("ScrobbleManager").w("Session expired for %s, logging out", provider.title)
        context.dataStore.edit {
            it.remove(provider.sessionKeyPref)
            it.remove(provider.usernamePref)
            it[provider.enabledPref] = false
        }
        queues[provider]?.clear()
        val message = context.getString(R.string.scrobble_signed_out, provider.title)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
