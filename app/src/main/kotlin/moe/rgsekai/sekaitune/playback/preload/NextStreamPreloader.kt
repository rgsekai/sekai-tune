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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextStreamPreloader @Inject constructor(
    private val resolveAudioStreamUseCase: ResolveAudioStreamUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    @Volatile
    private var currentPreloadVideoId: String? = null
    @Volatile
    private var lastSuccessfulPreloadVideoId: String? = null

    fun preloadNext(
        videoId: String,
        audioQuality: AudioQuality = AudioQuality.AUTO,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        networkMetered: Boolean = false,
    ) {
        val trimmedId = videoId.trim()
        if (trimmedId.isEmpty()) return

        synchronized(this) {
            if (currentPreloadVideoId == trimmedId) {
                if (currentJob?.isActive == true) {
                    Timber.tag(TAG).d("Preload already active for %s", trimmedId)
                    return
                }
                if (lastSuccessfulPreloadVideoId == trimmedId) {
                    Timber.tag(TAG).d("Preload already succeeded and cached for %s", trimmedId)
                    return
                }
            }
            currentJob?.cancel()
            currentPreloadVideoId = trimmedId
            currentJob = scope.launch {
                val startMs = System.currentTimeMillis()
                Timber.tag(TAG).i("[Preload] Starting background stream preload for %s at %d ms", trimmedId, startMs)
                runCatching {
                    resolveAudioStreamUseCase.preload(
                        AudioStreamRequest(
                            mediaId = trimmedId,
                            quality = audioQuality,
                            networkMetered = networkMetered,
                            purpose = StreamPurpose.PLAYBACK,
                            preferredStreamClient = preferredStreamClient,
                            authState = YouTube.currentPlaybackAuthState(),
                        ),
                    )
                }.onSuccess {
                    val durationMs = System.currentTimeMillis() - startMs
                    lastSuccessfulPreloadVideoId = trimmedId
                    Timber.tag(TAG).i("[Preload] Preload succeeded for %s in %d ms", trimmedId, durationMs)
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException || error is java.io.InterruptedIOException) {
                        Timber.tag(TAG).d("[Preload] Preload cancelled for %s (superseded)", trimmedId)
                    } else {
                        Timber.tag(TAG).w(error, "[Preload] Preload failed for %s", trimmedId)
                    }
                }
            }
        }
    }

    fun cancelPreload() {
        synchronized(this) {
            currentJob?.cancel()
            currentJob = null
            currentPreloadVideoId = null
            lastSuccessfulPreloadVideoId = null
        }
    }

    fun getPreloadedVideoId(): String? = currentPreloadVideoId

    companion object {
        private const val TAG = "NextStreamPreloader"
    }
}

