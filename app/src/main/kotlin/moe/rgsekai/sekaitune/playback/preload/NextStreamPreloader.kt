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

    fun preloadNext(
        videoId: String,
        audioQuality: AudioQuality = AudioQuality.AUTO,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        networkMetered: Boolean = false,
    ) {
        val trimmedId = videoId.trim()
        if (trimmedId.isEmpty()) return

        synchronized(this) {
            if (currentPreloadVideoId == trimmedId && currentJob?.isActive == true) {
                Timber.tag(TAG).d("Preload already active for %s", trimmedId)
                return
            }
            currentJob?.cancel()
            currentPreloadVideoId = trimmedId
            currentJob = scope.launch {
                Timber.tag(TAG).d("Starting background stream preload for %s", trimmedId)
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
                    Timber.tag(TAG).d("Preload succeeded for %s", trimmedId)
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Preload failed for %s", trimmedId)
                }
            }
        }
    }

    fun cancelPreload() {
        synchronized(this) {
            currentJob?.cancel()
            currentJob = null
            currentPreloadVideoId = null
        }
    }

    fun getPreloadedVideoId(): String? = currentPreloadVideoId

    companion object {
        private const val TAG = "NextStreamPreloader"
    }
}
