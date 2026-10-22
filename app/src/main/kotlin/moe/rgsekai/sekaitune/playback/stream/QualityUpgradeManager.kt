/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.firstOrNull
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.playback.sources.AudioPipelineStatus
import moe.rgsekai.sekaitune.playback.sources.AudioQualityManager
import moe.rgsekai.sekaitune.playback.sources.CandidateQualityRank
import moe.rgsekai.sekaitune.playback.sources.MatchTarget
import moe.rgsekai.sekaitune.playback.sources.SourceKind
import moe.rgsekai.sekaitune.playback.sources.SourceRegistry
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class QualityUpgradeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioQualityManager: AudioQualityManager,
    private val sourceRegistry: SourceRegistry,
    private val resolveAudioStreamUseCase: ResolveAudioStreamUseCase,
    private val database: MusicDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currentUpgradeJob: Job? = null
    private var activeAuditionPlayer: ExoPlayer? = null
    private var watchdogJob: Job? = null
    private val blacklistedAddons = ConcurrentHashMap<String, MutableSet<String>>()
    private val currentGenerationToken = AtomicLong(0)

    @Volatile
    private var activeUpgradedMediaId: String? = null

    @Volatile
    private var activeUpgradedSourceName: String? = null

    @Volatile
    private var currentEvaluatingMediaId: String? = null

    // Callbacks wired to MusicService
    var swapCallback: (suspend (mediaId: String, upgradedItem: MediaItem, customHeaders: Map<String, String>?, targetPositionMs: Long, bitrateBps: Int?, sampleRateHz: Int?) -> Boolean)? = null
    var revertCallback: ((mediaId: String, positionMs: Long) -> Unit)? = null
    var onUpgradeSettled: ((mediaId: String) -> Unit)? = null
    var isEligibleForUpgrade: (() -> Boolean)? = null
    var playerCurrentMediaId: (() -> String?)? = null
    var playerIsPlaying: (() -> Boolean)? = null
    var playerCurrentPosition: (() -> Long)? = null
    var playerDuration: (() -> Long)? = null

    fun isBlacklisted(mediaId: String, sourceName: String): Boolean {
        return blacklistedAddons[mediaId]?.contains(sourceName) == true
    }

    fun blacklistAddonForTrack(mediaId: String, sourceName: String) {
        blacklistedAddons.getOrPut(mediaId) { ConcurrentHashMap.newKeySet() }.add(sourceName)
    }

    fun isActiveUpgraded(mediaId: String): Boolean = activeUpgradedMediaId == mediaId

    fun isUpgradeRunning(mediaId: String): Boolean = currentEvaluatingMediaId == mediaId && currentUpgradeJob?.isActive == true

    fun onDirectAddonStarted(mediaId: String, sourceName: String) {
        cancelUpgrade("Direct addon playback started")
        activeUpgradedMediaId = mediaId
        activeUpgradedSourceName = sourceName
        currentEvaluatingMediaId = null
        AudioQualityManager.registerSourceRecord(
            mediaId = mediaId,
            sourceName = sourceName,
            pipelineStatus = AudioPipelineStatus.PRELOADED_INSTANT,
        )
        startWatchdog(mediaId, sourceName, 0L)
    }

    data class AuditionResult(
        val isSuccess: Boolean,
        val decodedFormat: Format? = null,
        val failureReason: String? = null,
    )

    fun cancelUpgrade(reason: String) {
        val token = currentGenerationToken.incrementAndGet()
        Timber.tag(TAG).d("[QualityUpgrade] Cancelled upgrade (token=%d): %s", token, reason)
        currentEvaluatingMediaId = null
        currentUpgradeJob?.cancel()
        currentUpgradeJob = null
        releaseAuditionPlayer()
    }

    fun onTrackStarted(
        mediaId: String,
        currentPositionMs: Long,
        durationMs: Long,
        targetTitle: String? = null,
        targetArtists: List<String> = emptyList(),
    ) {
        val curPos = (playerCurrentPosition?.invoke() ?: currentPositionMs).coerceAtLeast(0L)
        val totalDur = if (durationMs > 0) durationMs else (playerDuration?.invoke()?.takeIf { it > 0 } ?: 0L)
        val remainingMs = if (totalDur > 0) totalDur - curPos else Long.MAX_VALUE

        if (totalDur > 0 && remainingMs <= MIN_REMAINING_DURATION_FOR_SWAP_MS) {
            Timber.tag(TAG).d("[QualityUpgrade] Less than 15s remaining for %s (%d ms left); skipping upgrade", mediaId, remainingMs)
            onUpgradeSettled?.invoke(mediaId)
            return
        }

        val generation = currentGenerationToken.incrementAndGet()
        currentUpgradeJob?.cancel()
        releaseAuditionPlayer()
        watchdogJob?.cancel()
        activeUpgradedMediaId = null
        activeUpgradedSourceName = null
        currentEvaluatingMediaId = mediaId

        if (isEligibleForUpgrade?.invoke() != true) {
            Timber.tag(TAG).d("[QualityUpgrade] Track %s not eligible for background quality upgrade", mediaId)
            currentEvaluatingMediaId = null
            onUpgradeSettled?.invoke(mediaId)
            return
        }

        currentUpgradeJob = scope.launch(Dispatchers.IO) {
            try {
                val enabledAddons = sourceRegistry.sourcesFlow.value.filter { it.kind == SourceKind.ADDON && it.isEnabled }
                if (enabledAddons.isEmpty()) {
                    Timber.tag(TAG).d("[QualityUpgrade] No addons enabled for quality upgrade")
                    return@launch
                }

                val target = buildMatchTarget(mediaId, targetTitle, targetArtists, totalDur)
                if (target == null) {
                    Timber.tag(TAG).d("[QualityUpgrade] Metadata unavailable for target track %s", mediaId)
                    return@launch
                }

                // Check if already won & cached
                var candidate: AddonStreamResult? = ResolveAudioStreamUseCase.getWinningSource(mediaId)
                    ?: ResolveAudioStreamUseCase.getPendingAddonStream(mediaId)
                val blacklisted = blacklistedAddons[mediaId] ?: emptySet()
                if (candidate != null && blacklisted.contains(candidate.sourceName)) {
                    candidate = null
                }

                val dynamicPos = (playerCurrentPosition?.invoke() ?: curPos).coerceAtLeast(0L)
                val dynamicRemainingMs = if (totalDur > 0) totalDur - dynamicPos else Long.MAX_VALUE
                if (totalDur > 0 && dynamicRemainingMs <= MIN_REMAINING_DURATION_FOR_SWAP_MS) {
                    Timber.tag(TAG).d("[QualityUpgrade] Less than 15s remaining for %s before query; skipping", mediaId)
                    return@launch
                }

                // Dynamic query timeout bounded by song duration before the 15s cutoff
                val availableQueryTimeoutMs = if (totalDur > 0) {
                    (dynamicRemainingMs - MIN_REMAINING_DURATION_FOR_SWAP_MS).coerceIn(15_000L, 60_000L)
                } else {
                    30_000L
                }

                if (candidate == null) {
                    val inFlight = ResolveAudioStreamUseCase.getInFlightAddonPreload(mediaId)
                    if (inFlight != null) {
                        Timber.tag(TAG).d("[QualityUpgrade] Awaiting in-flight preload for %s (timeout=%d ms)", mediaId, availableQueryTimeoutMs)
                        candidate = withTimeoutOrNull(availableQueryTimeoutMs) {
                            inFlight.await()
                        }
                        if (candidate != null && blacklisted.contains(candidate.sourceName)) {
                            candidate = null
                        }
                    }
                }

                if (candidate == null) {
                    val candidateAddons = enabledAddons.filter { !blacklisted.contains(it.displayName) }
                    candidate = withTimeoutOrNull(availableQueryTimeoutMs) {
                        resolveAudioStreamUseCase.resolveAddonStream(
                            targetTrack = target,
                            enabledAddons = candidateAddons,
                            youtubeBitrateKbps = 160,
                        )
                    }

                    if (candidate == null && resolveAudioStreamUseCase.hasAnyAddonBackoff(candidateAddons)) {
                        val remainingBackoff = resolveAudioStreamUseCase.getMaxRemainingAddonBackoffMs(candidateAddons)
                        val dynamicPos = (playerCurrentPosition?.invoke() ?: curPos).coerceAtLeast(0L)
                        val remainingSongMs = if (totalDur > 0) totalDur - dynamicPos else Long.MAX_VALUE
                        if (remainingBackoff in 1..25_000L && remainingSongMs > (remainingBackoff + MIN_REMAINING_DURATION_FOR_SWAP_MS)) {
                            Timber.tag(TAG).i("[QualityUpgrade] Addon is backing off (%d ms left). Waiting to retry query for %s", remainingBackoff, mediaId)
                            delay(remainingBackoff + 300L)
                            if (currentGenerationToken.get() == generation) {
                                candidate = withTimeoutOrNull(availableQueryTimeoutMs) {
                                    resolveAudioStreamUseCase.resolveAddonStream(
                                        targetTrack = target,
                                        enabledAddons = candidateAddons,
                                        youtubeBitrateKbps = 160,
                                    )
                                }
                            }
                        }
                    }
                }

                if (currentGenerationToken.get() != generation) return@launch

                if (candidate == null) {
                    Timber.tag(TAG).d("[QualityUpgrade] No strictly better candidate found for %s within %d ms", mediaId, availableQueryTimeoutMs)
                    return@launch
                }

                if (!CandidateQualityRank.isStrictlyBetterThanYouTube(candidate.rank, 160)) {
                    Timber.tag(TAG).d("[QualityUpgrade] Candidate from %s is not strictly better than YouTube baseline", candidate.sourceName)
                    return@launch
                }

                val curPosBeforeAudition = (playerCurrentPosition?.invoke() ?: curPos).coerceAtLeast(0L)
                val remainingForAuditionMs = if (totalDur > 0) {
                    (totalDur - curPosBeforeAudition - MIN_REMAINING_DURATION_FOR_SWAP_MS).coerceIn(15_000L, 45_000L)
                } else {
                    30_000L
                }

                if (totalDur > 0 && (totalDur - curPosBeforeAudition) <= MIN_REMAINING_DURATION_FOR_SWAP_MS) {
                    Timber.tag(TAG).d("[QualityUpgrade] Less than 15s remaining for %s before audition; skipping", mediaId)
                    return@launch
                }

                Timber.tag(TAG).i(
                    "[QualityUpgrade] Candidate selected from %s (%s). Starting headless audition with timeout %d ms.",
                    candidate.sourceName,
                    candidate.formatDescription,
                    remainingForAuditionMs,
                )
                audioQualityManager.setUpgrading(mediaId)

                val auditionStartMs = SystemClock.elapsedRealtime()
                val auditionResult = auditionCandidate(
                    mediaId = mediaId,
                    candidate = candidate,
                    playingDurationMs = totalDur,
                    generation = generation,
                    timeoutMs = remainingForAuditionMs,
                )

                if (currentGenerationToken.get() != generation) return@launch

                if (!auditionResult.isSuccess) {
                    Timber.tag(TAG).w("[QualityUpgrade] Audition rejected candidate from %s: %s", candidate.sourceName, auditionResult.failureReason)
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    if (currentGenerationToken.get() != generation) return@withContext
                    if (isEligibleForUpgrade?.invoke() != true) return@withContext

                    val currentPlayingPos = (playerCurrentPosition?.invoke() ?: (curPos + (SystemClock.elapsedRealtime() - auditionStartMs))).coerceAtLeast(0L)
                    val remainingBeforeSwap = if (totalDur > 0) totalDur - currentPlayingPos else Long.MAX_VALUE
                    if (totalDur > 0 && remainingBeforeSwap < MIN_REMAINING_DURATION_FOR_SWAP_MS) {
                        Timber.tag(TAG).d("[QualityUpgrade] Aborting swap: less than 15s remaining for %s (%d ms left)", mediaId, remainingBeforeSwap)
                        return@withContext
                    }

                    val customCacheKey = "upgraded:${candidate.sourceName}:$mediaId"
                    val upgradedItem = MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setUri(candidate.resolvedStream.url.toUri())
                        .setCustomCacheKey(customCacheKey)
                        .build()

                    val decoded = auditionResult.decodedFormat
                    val bitDepth = decoded?.pcmEncoding?.let { pcm ->
                        when (pcm) {
                            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
                            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 24
                            else -> candidate.sourceStream.bitDepth ?: 16
                        }
                    } ?: candidate.sourceStream.bitDepth ?: 16

                    val sampleRate = decoded?.sampleRate?.takeIf { it > 0 } ?: candidate.sourceStream.sampleRateHz ?: 44100
                    val mime = decoded?.sampleMimeType ?: candidate.sourceStream.format.name
                    val bitrateBps = (candidate.sourceStream.bitrateKbps ?: 1411) * 1000

                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = mime,
                        bitrateBps = bitrateBps,
                        sampleRateHz = sampleRate,
                        bitDepth = bitDepth,
                        addonFormat = candidate.sourceStream.format,
                    )
                    val isHiRes = isLossless && (bitDepth > 16 || sampleRate > 48000)

                    val bitDepthStr = "${bitDepth}-bit"
                    val sampleRateStr = String.format(java.util.Locale.US, "%.1f kHz", sampleRate / 1000.0)
                    val bitrateStr = candidate.sourceStream.bitrateKbps?.let { "${it} kbps" } ?: "1411 kbps"

                    val swapped = swapCallback?.invoke(
                        mediaId,
                        upgradedItem,
                        candidate.sourceStream.headers,
                        currentPlayingPos,
                        bitrateBps,
                        sampleRate,
                    ) ?: false

                    if (swapped) {
                        activeUpgradedMediaId = mediaId
                        activeUpgradedSourceName = candidate.sourceName

                        audioQualityManager.setFinal(
                            mediaId = mediaId,
                            label = label,
                            isLossless = isLossless,
                            isHiRes = isHiRes,
                            details = candidate.formatDescription,
                            sourceName = candidate.sourceName,
                            format = candidate.sourceStream.format.name,
                            bitDepth = bitDepthStr,
                            sampleRate = sampleRateStr,
                            bitrate = bitrateStr,
                            pipelineStatus = AudioPipelineStatus.UPGRADED_FROM_OPUS,
                        )
                        startWatchdog(mediaId, candidate.sourceName, currentPlayingPos)
                    }
                }
            } finally {
                if (currentEvaluatingMediaId == mediaId) {
                    currentEvaluatingMediaId = null
                }
                if (currentGenerationToken.get() == generation) {
                    withContext(Dispatchers.Main) {
                        onUpgradeSettled?.invoke(mediaId)
                    }
                }
            }
        }
    }

    private suspend fun auditionCandidate(
        mediaId: String,
        candidate: AddonStreamResult,
        playingDurationMs: Long,
        generation: Long,
        timeoutMs: Long,
    ): AuditionResult = withContext(Dispatchers.Main) {
        if (currentGenerationToken.get() != generation) {
            return@withContext AuditionResult(false, null, "Cancelled before audition")
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)
        if (candidate.sourceStream.headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(candidate.sourceStream.headers)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        val headlessPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, false)
            .build().apply {
                volume = 0f
                playWhenReady = true
            }
        activeAuditionPlayer = headlessPlayer

        val auditionKey = "audition:${candidate.sourceName}:$mediaId"
        val auditionItem = MediaItem.Builder()
            .setMediaId("audition:$mediaId")
            .setUri(candidate.resolvedStream.url.toUri())
            .setCustomCacheKey(auditionKey)
            .build()

        headlessPlayer.setMediaItem(auditionItem)
        headlessPlayer.prepare()

        val deferred = CompletableDeferred<AuditionResult>()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val duration = headlessPlayer.duration
                    // Removed rigid 3.5s tolerance gate so legitimate FLAC files with slight intro/outro differences match.
                    // Only reject severe mismatch (>45s or >50% discrepancy).
                    if (playingDurationMs > 0 && duration > 0) {
                        val deltaMs = abs(duration - playingDurationMs)
                        // Never accept candidate tracks that are significantly truncated (>15s shorter)
                        // or have severe discrepancy (>25s difference).
                        if (duration < playingDurationMs - 15_000L || deltaMs > 25_000L) {
                            deferred.complete(
                                AuditionResult(
                                    isSuccess = false,
                                    failureReason = "Duration mismatch ${deltaMs}ms (playing: ${playingDurationMs}ms, candidate: ${duration}ms)",
                                )
                            )
                            return
                        }
                    }
                    val format = headlessPlayer.audioFormat
                    deferred.complete(
                        AuditionResult(
                            isSuccess = true,
                            decodedFormat = format,
                        )
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                deferred.complete(
                    AuditionResult(
                        isSuccess = false,
                        failureReason = "ExoPlayer audition error: ${error.message}",
                    )
                )
            }
        }

        headlessPlayer.addListener(listener)

        try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: AuditionResult(false, null, "Audition timed out after ${timeoutMs}ms")
        } finally {
            headlessPlayer.removeListener(listener)
            headlessPlayer.stop()
            headlessPlayer.release()
            if (activeAuditionPlayer == headlessPlayer) {
                activeAuditionPlayer = null
            }
        }
    }

    private fun startWatchdog(mediaId: String, sourceName: String, originalPositionMs: Long) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            var lastPos = originalPositionMs
            var stallCount = 0
            val checkIntervalMs = 2000L

            while (isActive) {
                delay(checkIntervalMs)
                val currentPlayingId = playerCurrentMediaId?.invoke()
                if (currentPlayingId != null && currentPlayingId != mediaId) {
                    return@launch
                }
                if (activeUpgradedMediaId != mediaId && ResolveAudioStreamUseCase.getWinningSource(mediaId) == null) {
                    return@launch
                }
                val isPlaying = playerIsPlaying?.invoke() == true
                val curPos = playerCurrentPosition?.invoke() ?: 0L

                if (isPlaying && curPos <= lastPos && curPos > 0L) {
                    stallCount++
                    if (stallCount >= 3) {
                        Timber.tag(TAG).w("[QualityUpgrade:Watchdog] Playback stalled for %s (%s) at %d ms. Reverting.", mediaId, sourceName, curPos)
                        onPlaybackFailedOrStalled(mediaId, curPos, "Playback stalled for 6s at pos $curPos")
                        return@launch
                    }
                } else {
                    stallCount = 0
                    lastPos = curPos
                }
            }
        }
    }

    fun onPlaybackFailedOrStalled(mediaId: String, currentPositionMs: Long, reason: String): Boolean {
        watchdogJob?.cancel()
        val isUpgraded = (activeUpgradedMediaId == mediaId) ||
            ResolveAudioStreamUseCase.getWinningSource(mediaId) != null ||
            AudioQualityManager.getSourceRecord(mediaId)?.sourceName?.let { it != "YouTube" && it.isNotBlank() } == true

        if (!isUpgraded || mediaId.startsWith("addon:")) {
            return false
        }

        val failedSource = activeUpgradedSourceName
            ?: ResolveAudioStreamUseCase.getWinningSource(mediaId)?.sourceName
            ?: AudioQualityManager.getSourceRecord(mediaId)?.sourceName.orEmpty()

        Timber.tag(TAG).w("[QualityUpgrade] Upgraded stream failed/stalled for %s (%s, reason=%s). Reverting silently to YouTube baseline.", mediaId, failedSource, reason)
        if (failedSource.isNotBlank()) {
            blacklistAddonForTrack(mediaId, failedSource)
        }
        ResolveAudioStreamUseCase.clearWinningSource(mediaId)
        activeUpgradedMediaId = null
        activeUpgradedSourceName = null
        revertCallback?.invoke(mediaId, currentPositionMs)
        return true
    }

    private fun releaseAuditionPlayer() {
        val playerToRelease = activeAuditionPlayer ?: return
        activeAuditionPlayer = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            playerToRelease.stop()
            playerToRelease.release()
        } else {
            Handler(Looper.getMainLooper()).post {
                playerToRelease.stop()
                playerToRelease.release()
            }
        }
    }

    private suspend fun buildMatchTarget(
        mediaId: String,
        targetTitle: String?,
        targetArtists: List<String>,
        durationMs: Long,
    ): MatchTarget? {
        if (!targetTitle.isNullOrBlank()) {
            return MatchTarget(
                title = targetTitle,
                artists = targetArtists,
                durationSec = (durationMs / 1000L).toInt().takeIf { it > 0 },
            )
        }
        val dbSong = runCatching { database.song(mediaId).firstOrNull() }.getOrNull()
        if (dbSong != null && dbSong.song.title.isNotBlank()) {
            return MatchTarget(
                title = dbSong.song.title,
                artists = dbSong.artists.map { it.name }.filter { it.isNotBlank() },
                durationSec = dbSong.song.duration.takeIf { it > 0 },
            )
        }
        return null
    }

    companion object {
        const val TAG = "QualityUpgradeManager"
        const val BACKGROUND_QUERY_TIMEOUT_MS = 10_000L
        const val WATCHDOG_DURATION_MS = 30_000L
        const val MIN_REMAINING_DURATION_FOR_SWAP_MS = 15_000L
    }
}
