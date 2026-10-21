/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.stream

import android.content.Context
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private var activeUpgradedSourceName: String? = null

    // Callbacks wired to MusicService
    var swapCallback: ((mediaId: String, upgradedItem: MediaItem, customHeaders: Map<String, String>?, targetPositionMs: Long) -> Boolean)? = null
    var revertCallback: ((mediaId: String, positionMs: Long) -> Unit)? = null
    var isEligibleForUpgrade: (() -> Boolean)? = null
    var playerCurrentMediaId: (() -> String?)? = null
    var playerIsPlaying: (() -> Boolean)? = null
    var playerCurrentPosition: (() -> Long)? = null

    fun isBlacklisted(mediaId: String, sourceName: String): Boolean {
        return blacklistedAddons[mediaId]?.contains(sourceName) == true
    }

    fun blacklistAddonForTrack(mediaId: String, sourceName: String) {
        blacklistedAddons.getOrPut(mediaId) { ConcurrentHashMap.newKeySet() }.add(sourceName)
    }

    fun onDirectAddonStarted(mediaId: String, sourceName: String) {
        cancelUpgrade("Direct addon playback started")
        activeUpgradedSourceName = sourceName
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
        val generation = currentGenerationToken.incrementAndGet()
        currentUpgradeJob?.cancel()
        releaseAuditionPlayer()
        watchdogJob?.cancel()
        activeUpgradedSourceName = null

        if (isEligibleForUpgrade?.invoke() != true) {
            Timber.tag(TAG).d("[QualityUpgrade] Track %s not eligible for background quality upgrade", mediaId)
            return
        }

        currentUpgradeJob = scope.launch(Dispatchers.IO) {
            val enabledAddons = sourceRegistry.sourcesFlow.value.filter { it.kind == SourceKind.ADDON && it.isEnabled }
            if (enabledAddons.isEmpty()) {
                Timber.tag(TAG).d("[QualityUpgrade] No addons enabled for quality upgrade")
                return@launch
            }

            val target = buildMatchTarget(mediaId, targetTitle, targetArtists, durationMs)
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

            if (candidate == null) {
                // Rule 3: Check if in-flight addon preload exists; await it so we don't double query
                val inFlight = ResolveAudioStreamUseCase.getInFlightAddonPreload(mediaId)
                if (inFlight != null) {
                    Timber.tag(TAG).d("[QualityUpgrade] Awaiting in-flight preload for %s", mediaId)
                    candidate = withTimeoutOrNull(ResolveAudioStreamUseCase.ADDON_RESOLUTION_BUDGET_MS) {
                        inFlight.await()
                    }
                    if (candidate != null && blacklisted.contains(candidate.sourceName)) {
                        candidate = null
                    }
                }
            }

            if (candidate == null) {
                // Query addons in background with 10s timeout
                candidate = withTimeoutOrNull(BACKGROUND_QUERY_TIMEOUT_MS) {
                    resolveAudioStreamUseCase.resolveAddonStream(
                        targetTrack = target,
                        enabledAddons = enabledAddons.filter { !blacklisted.contains(it.displayName) },
                        youtubeBitrateKbps = 160,
                    )
                }
            }

            if (currentGenerationToken.get() != generation) return@launch

            if (candidate == null) {
                Timber.tag(TAG).d("[QualityUpgrade] No strictly better candidate found for %s within %d ms", mediaId, BACKGROUND_QUERY_TIMEOUT_MS)
                return@launch
            }

            if (!CandidateQualityRank.isStrictlyBetterThanYouTube(candidate.rank, 160)) {
                Timber.tag(TAG).d("[QualityUpgrade] Candidate from %s is not strictly better than YouTube baseline", candidate.sourceName)
                return@launch
            }

            Timber.tag(TAG).i("[QualityUpgrade] Candidate selected from %s (%s). Starting headless audition.", candidate.sourceName, candidate.formatDescription)
            audioQualityManager.setUpgrading(mediaId)

            val auditionStartMs = SystemClock.elapsedRealtime()
            val auditionResult = auditionCandidate(
                mediaId = mediaId,
                candidate = candidate,
                playingDurationMs = durationMs,
                generation = generation,
            )

            if (currentGenerationToken.get() != generation) return@launch

            if (!auditionResult.isSuccess) {
                Timber.tag(TAG).w("[QualityUpgrade] Audition rejected candidate from %s: %s", candidate.sourceName, auditionResult.failureReason)
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (currentGenerationToken.get() != generation) return@withContext
                if (isEligibleForUpgrade?.invoke() != true) return@withContext

                val auditionElapsed = SystemClock.elapsedRealtime() - auditionStartMs
                val currentPlayingPos = currentPositionMs + auditionElapsed
                val remainingMs = durationMs - currentPlayingPos
                if (durationMs > 0 && remainingMs < MIN_REMAINING_DURATION_FOR_SWAP_MS) {
                    Timber.tag(TAG).d("[QualityUpgrade] Aborting swap: less than 15s remaining for %s", mediaId)
                    return@withContext
                }

                val customCacheKey = "upgraded:${candidate.sourceName}:$mediaId"
                val upgradedItem = MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(candidate.resolvedStream.url.toUri())
                    .setCustomCacheKey(customCacheKey)
                    .build()

                val swapped = swapCallback?.invoke(
                    mediaId,
                    upgradedItem,
                    candidate.sourceStream.headers,
                    currentPlayingPos,
                ) ?: false

                if (swapped) {
                    activeUpgradedSourceName = candidate.sourceName
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

                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = mime,
                        bitrateBps = (candidate.sourceStream.bitrateKbps ?: 1411) * 1000,
                        sampleRateHz = sampleRate,
                        bitDepth = bitDepth,
                        addonFormat = candidate.sourceStream.format,
                    )
                    val isHiRes = isLossless && (bitDepth > 16 || sampleRate > 48000)

                    val bitDepthStr = "${bitDepth}-bit"
                    val sampleRateStr = String.format(java.util.Locale.US, "%.1f kHz", sampleRate / 1000.0)
                    val bitrateStr = candidate.sourceStream.bitrateKbps?.let { "${it} kbps" } ?: "1411 kbps"

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
        }
    }

    private suspend fun auditionCandidate(
        mediaId: String,
        candidate: AddonStreamResult,
        playingDurationMs: Long,
        generation: Long,
    ): AuditionResult = withContext(Dispatchers.Main) {
        if (currentGenerationToken.get() != generation) {
            return@withContext AuditionResult(false, null, "Cancelled before audition")
        }

        val headlessPlayer = ExoPlayer.Builder(context)
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
                    if (playingDurationMs > 0 && duration > 0) {
                        val deltaMs = abs(duration - playingDurationMs)
                        if (deltaMs > 2000L) { // 2s tolerance check
                            deferred.complete(
                                AuditionResult(
                                    isSuccess = false,
                                    failureReason = "Duration delta ${deltaMs}ms exceeds 2s tolerance",
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
            withTimeoutOrNull(5000L) {
                deferred.await()
            } ?: AuditionResult(false, null, "Audition timed out after 5s")
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
            val totalChecks = (WATCHDOG_DURATION_MS / checkIntervalMs).toInt()

            for (i in 1..totalChecks) {
                delay(checkIntervalMs)
                val currentPlayingId = playerCurrentMediaId?.invoke()
                if (currentPlayingId != null && currentPlayingId != mediaId) {
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
            Timber.tag(TAG).d("[QualityUpgrade:Watchdog] 30s monitoring passed cleanly for %s from %s", mediaId, sourceName)
            if (activeUpgradedSourceName == sourceName) {
                activeUpgradedSourceName = null
            }
        }
    }

    fun onPlaybackFailedOrStalled(mediaId: String, currentPositionMs: Long, reason: String) {
        if (watchdogJob?.isActive == true) {
            watchdogJob?.cancel()
            val failedSource = activeUpgradedSourceName.orEmpty()
            Timber.tag(TAG).w("[QualityUpgrade:Watchdog] Upgraded stream failed/stalled within 30s for %s (%s). Reverting silently to YouTube.", mediaId, reason)
            if (failedSource.isNotBlank()) {
                blacklistedAddons.getOrPut(mediaId) { ConcurrentHashMap.newKeySet() }.add(failedSource)
            }
            ResolveAudioStreamUseCase.clearWinningSource(mediaId)
            activeUpgradedSourceName = null
            revertCallback?.invoke(mediaId, currentPositionMs)
        }
    }

    private fun releaseAuditionPlayer() {
        activeAuditionPlayer?.let { player ->
            player.stop()
            player.release()
            activeAuditionPlayer = null
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
