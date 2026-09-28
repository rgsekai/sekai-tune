package moe.rgsekai.sekaitune.playback.preload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.constants.AudioQuality
import moe.rgsekai.sekaitune.constants.PlayerStreamClient
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.playback.stream.AudioStreamRequest
import moe.rgsekai.sekaitune.playback.stream.ResolveAudioStreamUseCase
import moe.rgsekai.sekaitune.playback.stream.StreamPurpose
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextStreamPreloader @Inject constructor(
    private val resolveAudioStreamUseCase: ResolveAudioStreamUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueJob: Job? = null

    fun preloadQueue(
        videoIds: List<String>,
        audioQuality: AudioQuality = AudioQuality.AUTO,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        networkMetered: Boolean = false,
    ) {
        val targets = videoIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_QUEUE_PRELOAD_ITEMS)
        if (targets.isEmpty()) return

        synchronized(this) {
            queueJob?.cancel()
            queueJob = scope.launch {
                for ((index, trimmedId) in targets.withIndex()) {
                    val request = AudioStreamRequest(
                        mediaId = trimmedId,
                        quality = audioQuality,
                        networkMetered = networkMetered,
                        purpose = StreamPurpose.PLAYBACK,
                        preferredStreamClient = preferredStreamClient,
                        authState = YouTube.currentPlaybackAuthState(),
                    )
                    if (resolveAudioStreamUseCase.peek(request) != null) {
                        Timber.tag(TAG).d("[Preload] Track %s already preloaded & cached (index=%d)", trimmedId, index)
                        continue
                    }
                    val startMs = System.currentTimeMillis()
                    Timber.tag(TAG).i("[Preload] Starting background stream preload for %s (queue pos %d) at %d ms", trimmedId, index + 1, startMs)
                    val result = runCatching {
                        resolveAudioStreamUseCase.preload(request)
                    }
                    if (result.isSuccess) {
                        val durationMs = System.currentTimeMillis() - startMs
                        Timber.tag(TAG).i("[Preload] Preload succeeded for %s (queue pos %d) in %d ms", trimmedId, index + 1, durationMs)
                    } else {
                        val error = result.exceptionOrNull()
                        if (error is kotlinx.coroutines.CancellationException || error is java.io.InterruptedIOException) {
                            Timber.tag(TAG).d("[Preload] Preload cancelled for %s (superseded)", trimmedId)
                            break
                        } else {
                            Timber.tag(TAG).w(error, "[Preload] Preload failed for %s", trimmedId)
                        }
                    }
                }
            }
        }
    }

    fun preloadNext(
        videoId: String,
        audioQuality: AudioQuality = AudioQuality.AUTO,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        networkMetered: Boolean = false,
    ) {
        preloadQueue(listOf(videoId), audioQuality, preferredStreamClient, networkMetered)
    }

    fun cancelPreload() {
        synchronized(this) {
            queueJob?.cancel()
            queueJob = null
        }
    }

    fun isPreloaded(videoId: String): Boolean {
        val request = AudioStreamRequest(
            mediaId = videoId.trim(),
            quality = AudioQuality.AUTO,
            networkMetered = false,
            purpose = StreamPurpose.PLAYBACK,
            preferredStreamClient = PlayerStreamClient.ANDROID_VR,
            authState = YouTube.currentPlaybackAuthState(),
        )
        return resolveAudioStreamUseCase.peek(request) != null
    }

    companion object {
        private const val TAG = "NextStreamPreloader"
        private const val MAX_QUEUE_PRELOAD_ITEMS = 2
    }
}

