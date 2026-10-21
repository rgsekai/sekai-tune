/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.stream

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.WorkerThread
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import moe.rgsekai.sekaitune.di.StreamResolutionScope
import moe.rgsekai.sekaitune.utils.YTPlayerUtils
import moe.rgsekai.sekaitune.utils.retryWithoutPlaybackLoginContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.rgsekai.sekaitune.constants.AudioQuality
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse
import moe.rgsekai.sekaitune.playback.sources.AudioQualityManager
import moe.rgsekai.sekaitune.playback.sources.CandidateQualityRank
import moe.rgsekai.sekaitune.playback.sources.MatchCandidate
import moe.rgsekai.sekaitune.playback.sources.MatchTarget
import moe.rgsekai.sekaitune.playback.sources.RegisteredSource
import moe.rgsekai.sekaitune.playback.sources.SourceKind
import moe.rgsekai.sekaitune.playback.sources.SourceRegistry
import moe.rgsekai.sekaitune.playback.sources.SourceStream
import moe.rgsekai.sekaitune.playback.sources.SourceTrack
import moe.rgsekai.sekaitune.playback.sources.StreamFormat
import moe.rgsekai.sekaitune.playback.sources.StreamRequest
import moe.rgsekai.sekaitune.playback.sources.TrackMatcher
import moe.rgsekai.sekaitune.playback.sources.addon.AddonClient
import moe.rgsekai.sekaitune.playback.sources.addon.AddonMusicSource
import moe.rgsekai.sekaitune.playback.sources.addon.AddonNoMatchException
import moe.rgsekai.sekaitune.playback.sources.addon.AddonResponseClassifier
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToInt

data class AddonStreamResult(
    val resolvedStream: ResolvedAudioStream,
    val sourceName: String,
    val formatDescription: String,
    val rank: CandidateQualityRank,
    val sourceStream: SourceStream,
    val fetchedAtMs: Long = System.currentTimeMillis(),
)

data class CandidateEvaluation(
    val title: String,
    val artists: List<String>,
    val durationSec: Int?,
    val isMatch: Boolean,
    val score: Int,
    val rejectReason: String?,
)

data class AddonQueryResult(
    val sourceName: String,
    val elapsedMs: Long,
    val candidates: List<CandidateEvaluation>,
    val streamResult: AddonStreamResult?,
    val error: String?,
)

@Singleton
class ResolveAudioStreamUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @StreamResolutionScope private val scope: CoroutineScope,
        private val audioQualityManager: AudioQualityManager,
        private val sourceRegistry: SourceRegistry,
        private val database: MusicDatabase,
    ) {
        private val connectivityManager = context.getSystemService<ConnectivityManager>()!!

        private data class CacheKey(
            val mediaId: String,
            val quality: String,
            val networkMetered: Boolean,
            val purpose: StreamPurpose,
            val authFingerprint: String,
        )

        private data class InFlightKey(
            val cacheKey: CacheKey,
            val priority: StreamResolutionPriority,
        )

        private enum class ResolutionConsumer {
            PLAYBACK,
            PRELOAD,
        }

        private class InFlightResolution(
            val deferred: Deferred<ResolvedAudioStream>,
            var playbackOwners: Int = 0,
            var preloadOwners: Int = 0,
        )

        private sealed interface ResolutionLease {
            data class Cached(val stream: ResolvedAudioStream) : ResolutionLease

            data class Active(
                val key: InFlightKey,
                val resolution: InFlightResolution,
            ) : ResolutionLease
        }

        private val cache = ConcurrentHashMap<CacheKey, ResolvedAudioStream>()
        private val inFlightLock = Any()
        private val inFlight = mutableMapOf<InFlightKey, InFlightResolution>()

        private val addonSemaphores = ConcurrentHashMap<String, Semaphore>()
        private val addonBackoffs = ConcurrentHashMap<String, Long>()

        private fun getAddonSemaphore(configId: String): Semaphore =
            addonSemaphores.computeIfAbsent(configId) { Semaphore(2) }

        fun isAddonBackingOff(configId: String): Boolean {
            val backoffUntil = addonBackoffs[configId] ?: return false
            if (System.currentTimeMillis() < backoffUntil) return true
            addonBackoffs.remove(configId)
            return false
        }

        fun recordAddon429(configId: String, retryAfterSeconds: Int = 30) {
            val backoffMs = (retryAfterSeconds.coerceIn(5, 300)) * 1000L
            addonBackoffs[configId] = System.currentTimeMillis() + backoffMs
            Timber.tag(TAG).w("Addon %s rate limited (HTTP 429); backing off for %d s", configId, backoffMs / 1000)
        }

        suspend operator fun invoke(request: AudioStreamRequest): ResolvedAudioStream =
            resolve(request, ResolutionConsumer.PLAYBACK)

        @WorkerThread
        fun resolveBlocking(request: AudioStreamRequest): ResolvedAudioStream =
            runBlocking(Dispatchers.IO) {
                invoke(request)
            }

        suspend fun preload(request: AudioStreamRequest) {
            resolve(request, ResolutionConsumer.PRELOAD)
        }

        private suspend fun resolve(
            request: AudioStreamRequest,
            consumer: ResolutionConsumer,
        ): ResolvedAudioStream {
            val resolveStartMs = System.currentTimeMillis()
            val key = request.cacheKey()
            val priority = request.resolutionPriority(consumer)
            val lease = acquireResolution(key, request, consumer, priority)
            if (lease is ResolutionLease.Cached) {
                Timber.tag("TrackTelemetry").i(
                    "[TrackTelemetry:Resolve] videoId=%s, consumer=%s, status=PRELOAD_CACHE_HIT, resolveDurationMs=%d",
                    key.mediaId,
                    consumer,
                    System.currentTimeMillis() - resolveStartMs,
                )
                return lease.stream
            }

            val activeLease = lease as ResolutionLease.Active
            val resolution = activeLease.resolution
            val isJoin = activeLease.key.priority != priority || activeLease.resolution.playbackOwners > 1 || activeLease.resolution.preloadOwners > 1
            return try {
                val stream = resolution.deferred.await()
                Timber.tag("TrackTelemetry").i(
                    "[TrackTelemetry:Resolve] videoId=%s, consumer=%s, status=%s, resolveDurationMs=%d",
                    key.mediaId,
                    consumer,
                    if (isJoin) "PRELOAD_IN_FLIGHT_JOIN" else "COLD_RESOLVE",
                    System.currentTimeMillis() - resolveStartMs,
                )
                stream
            } catch (cancellation: CancellationException) {
                coroutineContext.ensureActive()
                throw cancellation
            } finally {
                releaseResolution(activeLease.key, consumer)
            }
        }


        private fun acquireResolution(
            cacheKey: CacheKey,
            request: AudioStreamRequest,
            consumer: ResolutionConsumer,
            priority: StreamResolutionPriority,
        ): ResolutionLease {
            synchronized(inFlightLock) {
                val cached = cache[cacheKey]
                if (cached != null) {
                    if (isFresh(cached)) {
                        Timber.tag(TAG).i("[StreamResolution] CACHE HIT (already resolved/preloaded) for %s (consumer=%s)", cacheKey.mediaId, consumer)
                        return ResolutionLease.Cached(cached)
                    }
                    cache.remove(cacheKey, cached)
                }

                val foregroundKey = InFlightKey(cacheKey, StreamResolutionPriority.FOREGROUND)
                val backgroundKey = InFlightKey(cacheKey, StreamResolutionPriority.BACKGROUND)

                val existing =
                    when (priority) {
                        StreamResolutionPriority.FOREGROUND -> inFlight[foregroundKey] ?: inFlight[backgroundKey]
                        StreamResolutionPriority.BACKGROUND -> inFlight[backgroundKey] ?: inFlight[foregroundKey]
                    }

                if (existing != null && !existing.deferred.isCancelled) {
                    when (consumer) {
                        ResolutionConsumer.PLAYBACK -> existing.playbackOwners += 1
                        ResolutionConsumer.PRELOAD -> existing.preloadOwners += 1
                    }
                    val activeKey = if (inFlight.containsKey(foregroundKey)) foregroundKey else backgroundKey
                    Timber.tag(TAG).i("[StreamResolution] IN-FLIGHT JOIN (deduplicating with active resolution) for %s (consumer=%s)", cacheKey.mediaId, consumer)
                    return ResolutionLease.Active(activeKey, existing)
                }

                if (existing != null) {
                    inFlight.remove(foregroundKey)
                    inFlight.remove(backgroundKey)
                }

                val targetKey = InFlightKey(cacheKey, priority)
                Timber.tag(TAG).i("[StreamResolution] COLD RESOLUTION START for %s (consumer=%s, priority=%s)", cacheKey.mediaId, consumer, priority)
                val deferred =
                    scope.async {
                        val startMs = System.currentTimeMillis()
                        try {
                            val resolved = resolveUncached(request)
                            storeResolvedStream(cacheKey, resolved)
                            Timber.tag(TAG).i("[StreamResolution] COLD RESOLUTION COMPLETED for %s in %d ms", cacheKey.mediaId, System.currentTimeMillis() - startMs)
                            resolved
                        } finally {
                            synchronized(inFlightLock) {
                                inFlight.remove(targetKey)
                            }
                        }
                    }

                val resolution =
                    InFlightResolution(deferred = deferred).apply {
                        when (consumer) {
                            ResolutionConsumer.PLAYBACK -> playbackOwners = 1
                            ResolutionConsumer.PRELOAD -> preloadOwners = 1
                        }
                    }

                inFlight[targetKey] = resolution
                return ResolutionLease.Active(targetKey, resolution)
            }
        }

        private fun releaseResolution(
            key: InFlightKey,
            consumer: ResolutionConsumer,
        ) {
            synchronized(inFlightLock) {
                val resolution = inFlight[key] ?: return
                when (consumer) {
                    ResolutionConsumer.PLAYBACK -> resolution.playbackOwners = (resolution.playbackOwners - 1).coerceAtLeast(0)
                    ResolutionConsumer.PRELOAD -> resolution.preloadOwners = (resolution.preloadOwners - 1).coerceAtLeast(0)
                }
                if (resolution.deferred.isCompleted) {
                    inFlight.remove(key)
                }
            }
        }

        fun invalidate(mediaId: String, purpose: StreamPurpose? = null) {
            val deferredsToCancel =
                synchronized(inFlightLock) {
                    cache.keys.removeIf { it.mediaId == mediaId && (purpose == null || it.purpose == purpose) }
                    inFlight.keys
                        .filter {
                            it.cacheKey.mediaId == mediaId &&
                                (purpose == null || it.cacheKey.purpose == purpose)
                        }
                        .mapNotNull { inFlight.remove(it)?.deferred }
                }
            deferredsToCancel.forEach { it.cancel() }
            winningSourceCache.remove(mediaId)
            fallbackYouTubeCache.remove(mediaId)
            pendingAddonResults.remove(mediaId)
        }

        fun invalidateUrl(url: String) {
            cache.entries.removeIf { it.value.url == url }
        }

        fun peek(request: AudioStreamRequest): ResolvedAudioStream? {
            val key = request.cacheKey()
            val resolved = cache[key] ?: return null
            if (isFresh(resolved)) return resolved
            cache.remove(key, resolved)
            return null
        }

        fun clear() {
            val deferredsToCancel =
                synchronized(inFlightLock) {
                    cache.clear()
                    inFlight.values.map(InFlightResolution::deferred).also { inFlight.clear() }
                }
            deferredsToCancel.forEach { it.cancel() }
            winningSourceCache.clear()
            fallbackYouTubeCache.clear()
            pendingAddonResults.clear()
        }

        private suspend fun resolveUncached(request: AudioStreamRequest): ResolvedAudioStream {
            // Low Quality or Low Data Mode on metered network skips addons entirely
            val isLowQuality = request.quality == AudioQuality.LOW
            val isMetered = request.networkMetered || audioQualityManager.isMeteredNetwork()
            if (isLowQuality || (request.networkMetered && isMetered)) {
                Timber.tag(TAG).i(
                    "[StreamResolution:Decision] mediaId=%s, tier=%s, networkAllowed=true, addonsQueried=[], chosenSource=YouTube, reason=Low quality or metered network selected",
                    request.mediaId,
                    request.quality.name,
                )
                val effectiveQuality = if (isLowQuality) AudioQuality.LOW else request.quality
                val ytStream = resolveYouTube(request, effectiveQuality)
                if (request.purpose == StreamPurpose.PLAYBACK) {
                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = ytStream.format.mimeType,
                        bitrateBps = ytStream.format.bitrate,
                        sampleRateHz = ytStream.format.audioSampleRate,
                    )
                    audioQualityManager.setFinal(
                        mediaId = request.mediaId,
                        label = label,
                        isLossless = isLossless,
                        isHiRes = false,
                        details = "",
                    )
                }
                return ytStream
            }

            // Only MAX quality queries external addons!
            // LOW, MEDIUM, HIGH use YouTube streams directly.
            if (request.quality != AudioQuality.MAX) {
                Timber.tag(TAG).i(
                    "[StreamResolution:Decision] mediaId=%s, tier=%s, networkAllowed=true, addonsQueried=[], chosenSource=YouTube, reason=Addon queries only run on MAX tier",
                    request.mediaId,
                    request.quality.name,
                )
                val ytStream = resolveYouTube(request, request.quality)
                if (request.purpose == StreamPurpose.PLAYBACK) {
                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = ytStream.format.mimeType,
                        bitrateBps = ytStream.format.bitrate,
                        sampleRateHz = ytStream.format.audioSampleRate,
                    )
                    audioQualityManager.setFinal(
                        mediaId = request.mediaId,
                        label = label,
                        isLossless = isLossless,
                        isHiRes = false,
                        details = "",
                    )
                }
                return ytStream
            }

            // When Max is selected but the current network is not allowed, behave exactly like High
            // (no addon queries, YouTube only) and do not show Upgrading.
            if (!audioQualityManager.isMaxAllowed()) {
                Timber.tag(TAG).i(
                    "[StreamResolution:Decision] mediaId=%s, tier=MAX, networkAllowed=false, addonsQueried=[], chosenSource=YouTube, reason=Max quality gated by network preference (Wi-Fi/Mobile)",
                    request.mediaId,
                )
                val ytStream = resolveYouTube(request, AudioQuality.HIGH)
                if (request.purpose == StreamPurpose.PLAYBACK) {
                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = ytStream.format.mimeType,
                        bitrateBps = ytStream.format.bitrate,
                        sampleRateHz = ytStream.format.audioSampleRate,
                    )
                    audioQualityManager.setFinal(
                        mediaId = request.mediaId,
                        label = label,
                        isLossless = isLossless,
                        isHiRes = false,
                        details = "",
                    )
                }
                return ytStream
            }

            // Check winning source cache per track so subsequent plays/preloads don't re-resolve or re-fail
            val cachedWinning = winningSourceCache[request.mediaId]
            if (cachedWinning != null && isFresh(cachedWinning.resolvedStream)) {
                Timber.tag(TAG).i(
                    "[StreamResolution:Decision] mediaId=%s, tier=MAX, networkAllowed=true, addonsQueried=[], chosenSource=%s (WINNING_CACHE_HIT), format=%s",
                    request.mediaId,
                    cachedWinning.sourceName,
                    cachedWinning.formatDescription,
                )
                if (request.purpose == StreamPurpose.PLAYBACK) {
                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = cachedWinning.sourceStream.format.name,
                        bitrateBps = (cachedWinning.sourceStream.bitrateKbps ?: 1411) * 1000,
                        sampleRateHz = cachedWinning.sourceStream.sampleRateHz,
                        bitDepth = cachedWinning.sourceStream.bitDepth,
                        addonFormat = cachedWinning.sourceStream.format,
                    )
                    val isHiRes = isLossless && ((cachedWinning.sourceStream.bitDepth ?: 16) > 16 || (cachedWinning.sourceStream.sampleRateHz ?: 44100) > 48000)
                    audioQualityManager.setFinal(
                        mediaId = request.mediaId,
                        label = label,
                        isLossless = isLossless,
                        isHiRes = isHiRes,
                        details = cachedWinning.formatDescription,
                    )
                }
                return cachedWinning.resolvedStream
            }

            // Check if any external addons are enabled
            val enabledAddons = sourceRegistry.sourcesFlow.value.filter { it.kind == SourceKind.ADDON && it.isEnabled }
            if (enabledAddons.isEmpty()) {
                Timber.tag(TAG).i(
                    "[StreamResolution:Decision] mediaId=%s, tier=MAX, networkAllowed=true, addonsQueried=[], chosenSource=YouTube, reason=No external addons enabled",
                    request.mediaId,
                )
                val ytStream = resolveYouTube(request)
                if (request.purpose == StreamPurpose.PLAYBACK) {
                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = ytStream.format.mimeType,
                        bitrateBps = ytStream.format.bitrate,
                        sampleRateHz = ytStream.format.audioSampleRate,
                    )
                    audioQualityManager.setFinal(
                        mediaId = request.mediaId,
                        label = label,
                        isLossless = isLossless,
                        isHiRes = false,
                        details = "",
                    )
                }
                return ytStream
            }

            val targetTrack = getMatchTarget(request.mediaId, request)
            if (targetTrack == null) {
                Timber.tag(TAG).i(
                    "[StreamResolution:Decision] mediaId=%s, tier=MAX, networkAllowed=true, addonsQueried=[], chosenSource=YouTube, reason=Target track metadata unavailable from request, DB, and YouTube",
                    request.mediaId,
                )
                val ytStream = resolveYouTube(request)
                if (request.purpose == StreamPurpose.PLAYBACK) {
                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = ytStream.format.mimeType,
                        bitrateBps = ytStream.format.bitrate,
                        sampleRateHz = ytStream.format.audioSampleRate,
                    )
                    audioQualityManager.setFinal(
                        mediaId = request.mediaId,
                        label = label,
                        isLossless = isLossless,
                        isHiRes = false,
                        details = "",
                    )
                }
                return ytStream
            }

            if (request.purpose == StreamPurpose.PLAYBACK) {
                audioQualityManager.setChecking(request.mediaId)
            }

            // Start YouTube stream resolution immediately in parallel
            val ytDeferred = scope.async(Dispatchers.IO) {
                resolveYouTube(request)
            }

            // Query all enabled addons in parallel within 1500 ms budget
            val toleranceSec = 30
            val query = TrackMatcher.buildAddonSearchQuery(targetTrack.title, targetTrack.artists)

            val queryResults = CopyOnWriteArrayList<AddonQueryResult>()
            val addonJobs = enabledAddons.map { registeredSource ->
                scope.launch(Dispatchers.IO) {
                    val source = registeredSource.instance as? AddonMusicSource ?: return@launch
                    val result = querySingleAddonDetailed(source, registeredSource.displayName, targetTrack, query, toleranceSec)
                    queryResults.add(result)
                }
            }

            // Wait until all addon jobs complete or 1500 ms budget expires
            withTimeoutOrNull(ADDON_RESOLUTION_BUDGET_MS) {
                addonJobs.forEach { it.join() }
            }

            val ytBaselineBitrate = if (ytDeferred.isCompleted) {
                runCatching { ytDeferred.getCompleted().format.bitrate / 1000 }.getOrDefault(160)
            } else {
                160
            }

            val completedReports = queryResults.toList()
            val bestAddon = completedReports
                .mapNotNull { it.streamResult }
                .filter { CandidateQualityRank.isStrictlyBetterThanYouTube(it.rank, ytBaselineBitrate) }
                .maxByOrNull { it.rank }

            if (bestAddon != null) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        val ytStream = ytDeferred.await()
                        fallbackYouTubeCache[request.mediaId] = ytStream
                    }
                }
                winningSourceCache[request.mediaId] = bestAddon
                val reportsLog = completedReports.joinToString("; ") { report ->
                    val candidateSummary = report.candidates.joinToString(", ") { c ->
                        val status = if (c.isMatch) "MATCH(${c.score})" else "REJECT(${c.rejectReason})"
                        "\"${c.title}\" [${c.durationSec}s] -> $status"
                    }
                    "${report.sourceName} (${report.elapsedMs}ms): ${report.error ?: candidateSummary.ifEmpty { "stream resolved" }}"
                }
                Timber.tag(TAG).i(
                    "[StreamResolution:Decision] mediaId=%s, tier=MAX, networkAllowed=true, addonsQueried=%s, chosenSource=%s, format=%s, query=\"%s\", reports=[%s]",
                    request.mediaId,
                    enabledAddons.map { it.displayName },
                    bestAddon.sourceName,
                    bestAddon.formatDescription,
                    query,
                    reportsLog,
                )
                if (request.purpose == StreamPurpose.PLAYBACK) {
                    val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                        mimeType = bestAddon.sourceStream.format.name,
                        bitrateBps = (bestAddon.sourceStream.bitrateKbps ?: 1411) * 1000,
                        sampleRateHz = bestAddon.sourceStream.sampleRateHz,
                        bitDepth = bestAddon.sourceStream.bitDepth,
                        addonFormat = bestAddon.sourceStream.format,
                    )
                    val isHiRes = isLossless && ((bestAddon.sourceStream.bitDepth ?: 16) > 16 || (bestAddon.sourceStream.sampleRateHz ?: 44100) > 48000)
                    audioQualityManager.setFinal(
                        mediaId = request.mediaId,
                        label = label,
                        isLossless = isLossless,
                        isHiRes = isHiRes,
                        details = bestAddon.formatDescription,
                    )
                }
                return bestAddon.resolvedStream
            }

            // Addon timed out, failed, or was not strictly better. Fall back to YouTube immediately!
            addonJobs.forEach { job ->
                job.invokeOnCompletion {
                    val lateReports = queryResults.toList()
                    val lateBest = lateReports
                        .mapNotNull { it.streamResult }
                        .filter { CandidateQualityRank.isStrictlyBetterThanYouTube(it.rank, ytBaselineBitrate) }
                        .maxByOrNull { it.rank }
                    if (lateBest != null) {
                        Timber.tag("ExternalSources").d(
                            "Caching pending addon stream for %s from %s for background upgrade",
                            request.mediaId,
                            lateBest.sourceName,
                        )
                        pendingAddonResults[request.mediaId] = lateBest
                        winningSourceCache[request.mediaId] = lateBest
                    }
                }
            }

            val ytStream = ytDeferred.await()
            fallbackYouTubeCache[request.mediaId] = ytStream
            val whyYouTubeWon = when {
                completedReports.isEmpty() -> "All addons timed out past ${ADDON_RESOLUTION_BUDGET_MS}ms budget"
                completedReports.none { it.streamResult != null } -> "No addon returned a playable matching stream within budget"
                else -> "Addon stream quality not strictly better than YouTube baseline (${ytBaselineBitrate} kbps)"
            }
            val reportsLog = completedReports.joinToString("; ") { report ->
                val candidateSummary = report.candidates.joinToString(", ") { c ->
                    val status = if (c.isMatch) "MATCH(${c.score})" else "REJECT(${c.rejectReason})"
                    "\"${c.title}\" [${c.durationSec}s] -> $status"
                }
                "${report.sourceName} (${report.elapsedMs}ms): ${report.error ?: candidateSummary.ifEmpty { "no valid candidate" }}"
            }
            Timber.tag(TAG).i(
                "[StreamResolution:Decision] mediaId=%s, tier=MAX, networkAllowed=true, addonsQueried=%s, chosenSource=YouTube, reason=%s, query=\"%s\", reports=[%s]",
                request.mediaId,
                enabledAddons.map { it.displayName },
                whyYouTubeWon,
                query,
                reportsLog,
            )

            if (request.purpose == StreamPurpose.PLAYBACK) {
                val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
                    mimeType = ytStream.format.mimeType,
                    bitrateBps = ytStream.format.bitrate,
                    sampleRateHz = ytStream.format.audioSampleRate,
                )
                audioQualityManager.setFinal(
                    mediaId = request.mediaId,
                    label = label,
                    isLossless = isLossless,
                    isHiRes = false,
                    details = "",
                )
            }
            return ytStream
        }

        private suspend fun getMatchTarget(mediaId: String, request: AudioStreamRequest? = null): MatchTarget? {
            if (request != null && !request.targetTitle.isNullOrBlank()) {
                return MatchTarget(
                    title = request.targetTitle,
                    artists = request.targetArtists,
                    durationSec = request.targetDurationSec,
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

            val meta = runCatching { YTPlayerUtils.playerResponseForMetadata(mediaId).getOrNull() }.getOrNull()
            val videoDetails = meta?.videoDetails
            if (videoDetails != null && !videoDetails.title.isNullOrBlank()) {
                val author = videoDetails.author.takeIf { it.isNotBlank() }
                val artists = if (author != null) listOf(author) else emptyList()
                val durationSec = videoDetails.lengthSeconds.toIntOrNull()?.takeIf { it > 0 }
                return MatchTarget(
                    title = videoDetails.title,
                    artists = artists,
                    durationSec = durationSec,
                )
            }

            return null
        }

        internal suspend fun resolveAddonStream(
            targetTrack: MatchTarget,
            enabledAddons: List<RegisteredSource>,
            youtubeBitrateKbps: Int = 160,
        ): AddonStreamResult? = coroutineScope {
            val toleranceSec = sourceRegistry.toleranceSecondsFlow.value
            val query = TrackMatcher.buildAddonSearchQuery(targetTrack.title, targetTrack.artists)

            val collectedResults = CopyOnWriteArrayList<AddonStreamResult>()
            val jobs = enabledAddons.map { registeredSource ->
                launch(Dispatchers.IO) {
                    val source = registeredSource.instance as? AddonMusicSource ?: return@launch
                    val result = querySingleAddon(source, registeredSource.displayName, targetTrack, query, toleranceSec)
                    if (result != null) {
                        collectedResults.add(result)
                    }
                }
            }
            jobs.forEach { runCatching { it.join() } }

            collectedResults
                .filter { CandidateQualityRank.isStrictlyBetterThanYouTube(it.rank, youtubeBitrateKbps) }
                .maxByOrNull { it.rank }
        }

        internal suspend fun querySingleAddon(
            source: AddonMusicSource,
            sourceName: String,
            targetTrack: MatchTarget,
            query: String,
            toleranceSec: Int,
        ): AddonStreamResult? = querySingleAddonDetailed(source, sourceName, targetTrack, query, toleranceSec).streamResult

        internal suspend fun querySingleAddonDetailed(
            source: AddonMusicSource,
            sourceName: String,
            targetTrack: MatchTarget,
            query: String,
            toleranceSec: Int = 30,
        ): AddonQueryResult {
            val startMs = System.currentTimeMillis()
            if (isAddonBackingOff(source.configId)) {
                return AddonQueryResult(
                    sourceName = sourceName,
                    elapsedMs = 0L,
                    candidates = emptyList(),
                    streamResult = null,
                    error = null,
                )
            }
            val semaphore = getAddonSemaphore(source.configId)
            return semaphore.withPermit {
                try {
                    val tracksResult = source.search(query, limit = 10)
                    val tracks = tracksResult.getOrElse { err ->
                        val msg = err.message.orEmpty()
                        if (msg.contains("429")) {
                            recordAddon429(source.configId)
                        }
                        return@withPermit AddonQueryResult(
                            sourceName = sourceName,
                            elapsedMs = System.currentTimeMillis() - startMs,
                            candidates = emptyList(),
                            streamResult = null,
                            error = AddonResponseClassifier.formatFailureReason(err),
                        )
                    }
                    if (tracks.isEmpty()) {
                        return@withPermit AddonQueryResult(
                            sourceName = sourceName,
                            elapsedMs = System.currentTimeMillis() - startMs,
                            candidates = emptyList(),
                            streamResult = null,
                            error = null,
                        )
                    }

                    val evaluations = mutableListOf<CandidateEvaluation>()
                    var bestCandidate: SourceTrack? = null
                    var bestScore = -1

                    for (track in tracks) {
                        val candidate = MatchCandidate(
                            title = track.title,
                            artists = track.artists,
                            durationSec = track.durationSec,
                        )
                        val match = TrackMatcher.match(targetTrack, candidate, toleranceSec)
                        evaluations.add(
                            CandidateEvaluation(
                                title = track.title,
                                artists = track.artists,
                                durationSec = track.durationSec,
                                isMatch = match.isMatch,
                                score = match.score,
                                rejectReason = match.rejectReason,
                            )
                        )
                        if (match.isMatch && match.score > bestScore) {
                            bestScore = match.score
                            bestCandidate = track
                        }
                    }

                    if (bestCandidate == null) {
                        return@withPermit AddonQueryResult(
                            sourceName = sourceName,
                            elapsedMs = System.currentTimeMillis() - startMs,
                            candidates = evaluations,
                            streamResult = null,
                            error = null,
                        )
                    }

                    val streamResult = source.stream(bestCandidate.id, StreamRequest.Best)
                    val sourceStream = streamResult.getOrElse { err ->
                        val msg = err.message.orEmpty()
                        if (msg.contains("429")) {
                            recordAddon429(source.configId)
                        }
                        return@withPermit AddonQueryResult(
                            sourceName = sourceName,
                            elapsedMs = System.currentTimeMillis() - startMs,
                            candidates = evaluations,
                            streamResult = null,
                            error = AddonResponseClassifier.formatFailureReason(err),
                        )
                    }

                    val rank = CandidateQualityRank.from(sourceStream)

                    val mimeType = when (sourceStream.format) {
                        StreamFormat.FLAC -> "audio/flac; codecs=\"flac\""
                        StreamFormat.ALAC -> "audio/alac"
                        StreamFormat.WAV -> "audio/wav"
                        StreamFormat.AAC -> "audio/mp4; codecs=\"mp4a.40.2\""
                        StreamFormat.OPUS -> "audio/opus; codecs=\"opus\""
                        StreamFormat.MP3 -> "audio/mpeg"
                        else -> "audio/flac"
                    }

                    val formatDesc = buildString {
                        append(sourceStream.format.name.lowercase())
                        sourceStream.bitDepth?.let { append(" - ${it}-bit") }
                        sourceStream.sampleRateHz?.let { append(" - ${(it / 100.0).roundToInt() / 10.0} kHz") }
                        val kbps = sourceStream.bitrateKbps ?: if (sourceStream.format in listOf(StreamFormat.FLAC, StreamFormat.ALAC, StreamFormat.WAV)) 1411 else null
                        kbps?.let { append(" - ${it} kbps") }
                    }.trim()

                    val format = PlayerResponse.StreamingData.Format(
                        itag = 999,
                        url = sourceStream.url,
                        mimeType = mimeType,
                        bitrate = (sourceStream.bitrateKbps ?: if (sourceStream.format in listOf(StreamFormat.FLAC, StreamFormat.ALAC, StreamFormat.WAV)) 1411 else 320) * 1000,
                        width = null,
                        height = null,
                        contentLength = null,
                        quality = "hd",
                        fps = null,
                        qualityLabel = null,
                        averageBitrate = null,
                        audioQuality = "AUDIO_QUALITY_HIGH",
                        approxDurationMs = bestCandidate.durationSec?.let { (it * 1000L).toString() },
                        audioSampleRate = sourceStream.sampleRateHz ?: 44100,
                        audioChannels = 2,
                        loudnessDb = null,
                        lastModified = null,
                        signatureCipher = null,
                        cipher = null,
                    )

                    val resolved = ResolvedAudioStream(
                        url = sourceStream.url,
                        format = format,
                        audioConfig = null,
                        videoDetails = null,
                        playbackTracking = null,
                        expiresAtMs = System.currentTimeMillis() + 6L * 3600 * 1000L,
                        authFingerprint = "addon:${source.configId}",
                    )

                    AddonQueryResult(
                        sourceName = sourceName,
                        elapsedMs = System.currentTimeMillis() - startMs,
                        candidates = evaluations,
                        streamResult = AddonStreamResult(
                            resolvedStream = resolved,
                            sourceName = sourceName,
                            formatDescription = formatDesc,
                            rank = rank,
                            sourceStream = sourceStream,
                        ),
                        error = null,
                    )
                } catch (e: Exception) {
                    val rawMsg = e.message ?: e.javaClass.simpleName
                    if (rawMsg.contains("429")) {
                        recordAddon429(source.configId)
                    }
                    val safeMsg = AddonClient.redactTokens(rawMsg)
                    AddonQueryResult(
                        sourceName = sourceName,
                        elapsedMs = System.currentTimeMillis() - startMs,
                        candidates = emptyList(),
                        streamResult = null,
                        error = safeMsg,
                    )
                }
            }
        }

        private suspend fun resolveYouTube(
            request: AudioStreamRequest,
            audioQualityOverride: AudioQuality? = null,
        ): ResolvedAudioStream {
            val quality = audioQualityOverride ?: request.quality
            val playbackData =
                context.retryWithoutPlaybackLoginContext {
                    when (request.purpose) {
                        StreamPurpose.DOWNLOAD -> {
                            YTPlayerUtils.playerResponseForDownload(
                                videoId = request.mediaId,
                                audioQuality = quality,
                                connectivityManager = connectivityManager,
                                networkMetered = request.networkMetered,
                            )
                        }
                        StreamPurpose.PLAYBACK -> {
                            YTPlayerUtils.playerResponseForPlayback(
                                videoId = request.mediaId,
                                audioQuality = quality,
                                connectivityManager = connectivityManager,
                                preferredStreamClient = request.preferredStreamClient,
                                networkMetered = request.networkMetered,
                            )
                        }
                    }
                }.getOrThrow()

            return ResolvedAudioStream(
                url = playbackData.streamUrl,
                format = playbackData.format,
                audioConfig = playbackData.audioConfig,
                videoDetails = playbackData.videoDetails,
                playbackTracking = playbackData.playbackTracking,
                expiresAtMs = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L),
                authFingerprint = playbackData.authFingerprint,
            )
        }

        private fun AudioStreamRequest.resolutionPriority(
            consumer: ResolutionConsumer,
        ): StreamResolutionPriority =
            if (consumer == ResolutionConsumer.PLAYBACK && purpose == StreamPurpose.PLAYBACK) {
                StreamResolutionPriority.FOREGROUND
            } else {
                StreamResolutionPriority.BACKGROUND
            }

        private fun AudioStreamRequest.cacheKey(): CacheKey =
            CacheKey(
                mediaId = mediaId,
                quality = quality.name,
                networkMetered = networkMetered,
                purpose = purpose,
                authFingerprint = authState.fingerprint,
            )

        private fun storeResolvedStream(
            key: CacheKey,
            resolved: ResolvedAudioStream,
        ) {
            putResolvedStream(key, resolved)
            // Share resolution between PLAYBACK and DOWNLOAD
            val alternatePurpose =
                when (key.purpose) {
                    StreamPurpose.PLAYBACK -> StreamPurpose.DOWNLOAD
                    StreamPurpose.DOWNLOAD -> StreamPurpose.PLAYBACK
                }
            putResolvedStream(key.copy(purpose = alternatePurpose), resolved)

            Timber.tag(TAG).d("Resolved stream cached for %s (expires in %ds)", key.mediaId, (resolved.expiresAtMs - System.currentTimeMillis()) / 1000)
            trimCacheIfNeeded()
        }

        private fun putResolvedStream(
            key: CacheKey,
            resolved: ResolvedAudioStream,
        ) {
            cache[key] = resolved
            cache[key.copy(authFingerprint = resolved.authFingerprint)] = resolved
        }

        private fun trimCacheIfNeeded() {
            if (cache.size <= MAX_CACHE_ENTRIES) return
            cache.entries.removeIf { !isFresh(it.value) }
            val excess = cache.size - MAX_CACHE_ENTRIES
            if (excess <= 0) return
            cache.entries
                .sortedBy { it.value.expiresAtMs }
                .take(excess)
                .forEach { entry -> cache.remove(entry.key, entry.value) }
        }

        companion object {
            const val TAG = "ResolveAudioStream"
            const val STREAM_EXPIRY_SAFETY_MS = 60_000L
            const val MAX_CACHE_ENTRIES = 128
            const val ADDON_RESOLUTION_BUDGET_MS = 1500L

            val pendingAddonResults = ConcurrentHashMap<String, AddonStreamResult>()
            val winningSourceCache = ConcurrentHashMap<String, AddonStreamResult>()
            val fallbackYouTubeCache = ConcurrentHashMap<String, ResolvedAudioStream>()

            fun getPendingAddonStream(mediaId: String): AddonStreamResult? = pendingAddonResults[mediaId]

            fun clearPendingAddonStream(mediaId: String) {
                pendingAddonResults.remove(mediaId)
            }

            fun getWinningSource(mediaId: String): AddonStreamResult? = winningSourceCache[mediaId]

            fun clearWinningSource(mediaId: String) {
                winningSourceCache.remove(mediaId)
            }

            fun clearAllWinningSources() {
                winningSourceCache.clear()
            }

            fun getFallbackYouTubeStream(mediaId: String): ResolvedAudioStream? {
                val cached = fallbackYouTubeCache[mediaId] ?: return null
                if (isFresh(cached)) return cached
                fallbackYouTubeCache.remove(mediaId)
                return null
            }

            fun setFallbackYouTubeStream(mediaId: String, stream: ResolvedAudioStream) {
                fallbackYouTubeCache[mediaId] = stream
            }

            fun clearFallbackYouTubeStream(mediaId: String) {
                fallbackYouTubeCache.remove(mediaId)
            }

            fun isFresh(stream: ResolvedAudioStream): Boolean =
                stream.expiresAtMs > System.currentTimeMillis() + STREAM_EXPIRY_SAFETY_MS

            fun isWinningSourceValid(mediaId: String): Boolean {
                val winning = winningSourceCache[mediaId] ?: return false
                return isFresh(winning.resolvedStream)
            }
        }
    }
