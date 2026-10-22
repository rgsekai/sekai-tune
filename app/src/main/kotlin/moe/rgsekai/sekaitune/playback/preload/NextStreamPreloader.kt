/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.preload

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.rgsekai.sekaitune.constants.AudioQuality
import moe.rgsekai.sekaitune.constants.PlayerStreamClient
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.playback.sources.AudioQualityManager
import moe.rgsekai.sekaitune.playback.stream.AudioStreamRequest
import moe.rgsekai.sekaitune.playback.stream.ResolveAudioStreamUseCase
import moe.rgsekai.sekaitune.playback.stream.StreamPurpose
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class PreloadTarget(
    val mediaId: String,
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val durationSec: Int? = null,
)

@Singleton
class NextStreamPreloader @Inject constructor(
    private val resolveAudioStreamUseCase: ResolveAudioStreamUseCase,
    private val audioQualityManager: AudioQualityManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueJob: Job? = null
    private val generationToken = AtomicLong(0L)
    private val trackPreloadSemaphore = Semaphore(MAX_QUEUE_PRELOAD_ITEMS)

    fun preloadTargets(
        targets: List<PreloadTarget>,
        audioQuality: AudioQuality = AudioQuality.MEDIUM,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        networkMetered: Boolean = false,
    ) {
        val distinctTargets = targets.distinctBy { it.mediaId.trim() }
            .filter { it.mediaId.isNotBlank() }
            .take(MAX_QUEUE_PRELOAD_ITEMS)
        if (distinctTargets.isEmpty()) return

        val currentGen = generationToken.incrementAndGet()

        synchronized(this) {
            queueJob?.cancel()
            queueJob = scope.launch {
                // Yield priority to currently playing track so it doesn't collide on addon bridge rate limits
                kotlinx.coroutines.delay(1200)
                for ((index, target) in distinctTargets.withIndex()) {
                    if (currentGen != generationToken.get()) return@launch
                    if (index > 0) {
                        kotlinx.coroutines.delay(2000)
                    }
                    val trimmedId = target.mediaId.trim()
                    val request = AudioStreamRequest(
                        mediaId = trimmedId,
                        quality = audioQuality,
                        networkMetered = networkMetered,
                        purpose = StreamPurpose.PLAYBACK,
                        preferredStreamClient = preferredStreamClient,
                        authState = YouTube.currentPlaybackAuthState(),
                        targetTitle = target.title,
                        targetArtists = target.artists,
                        targetDurationSec = target.durationSec,
                    )

                    trackPreloadSemaphore.withPermit {
                        if (currentGen != generationToken.get()) return@withPermit

                        val hasFreshWinning = ResolveAudioStreamUseCase.isWinningSourceValid(trimmedId)
                        val hasFreshFallback = ResolveAudioStreamUseCase.getFallbackYouTubeStream(trimmedId) != null
                        val hasCachedStream = resolveAudioStreamUseCase.peek(request) != null

                        if (audioQuality == AudioQuality.MAX && audioQualityManager.isMaxAllowed() && !networkMetered) {
                            if (hasFreshWinning && hasFreshFallback) {
                                Timber.tag(TAG).d("[Preload] Track %s already preloaded & fresh (winning addon + fallback ready)", trimmedId)
                                return@withPermit
                            }
                        } else if (hasCachedStream) {
                            Timber.tag(TAG).d("[Preload] Track %s already preloaded & cached (index=%d)", trimmedId, index)
                            return@withPermit
                        }

                        val startMs = System.currentTimeMillis()
                        Timber.tag(TAG).i("[Preload] Starting background stream preload for %s (queue pos %d, quality=%s) at %d ms", trimmedId, index + 1, audioQuality.name, startMs)
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
                                return@launch
                            } else {
                                Timber.tag(TAG).w(error, "[Preload] Preload failed for %s", trimmedId)
                            }
                        }
                    }
                }
            }
        }
    }

    fun preloadQueue(
        videoIds: List<String>,
        audioQuality: AudioQuality = AudioQuality.MEDIUM,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        networkMetered: Boolean = false,
    ) {
        preloadTargets(
            targets = videoIds.map { PreloadTarget(it) },
            audioQuality = audioQuality,
            preferredStreamClient = preferredStreamClient,
            networkMetered = networkMetered,
        )
    }

    fun preloadNext(
        videoId: String,
        audioQuality: AudioQuality = AudioQuality.MEDIUM,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        networkMetered: Boolean = false,
    ) {
        preloadQueue(listOf(videoId), audioQuality, preferredStreamClient, networkMetered)
    }

    fun cancelPreload() {
        generationToken.incrementAndGet()
        synchronized(this) {
            queueJob?.cancel()
            queueJob = null
        }
    }

    fun isPreloaded(videoId: String): Boolean {
        val trimmedId = videoId.trim()
        if (trimmedId.isEmpty()) return false
        val winning = ResolveAudioStreamUseCase.getWinningSource(trimmedId)
        if (winning != null && ResolveAudioStreamUseCase.isFresh(winning.resolvedStream)) {
            return true
        }
        val fallback = ResolveAudioStreamUseCase.getFallbackYouTubeStream(trimmedId)
        if (fallback != null && ResolveAudioStreamUseCase.isFresh(fallback)) {
            return true
        }
        val request = AudioStreamRequest(
            mediaId = trimmedId,
            quality = AudioQuality.MEDIUM,
            networkMetered = false,
            purpose = StreamPurpose.PLAYBACK,
            preferredStreamClient = PlayerStreamClient.ANDROID_VR,
            authState = YouTube.currentPlaybackAuthState(),
        )
        return resolveAudioStreamUseCase.peek(request) != null
    }

    fun hasWinningAddonPreloaded(videoId: String): Boolean {
        val winning = ResolveAudioStreamUseCase.getWinningSource(videoId.trim()) ?: return false
        return ResolveAudioStreamUseCase.isFresh(winning.resolvedStream)
    }

    companion object {
        private const val TAG = "NextStreamPreloader"
        const val MAX_QUEUE_PRELOAD_ITEMS = 2
    }
}
