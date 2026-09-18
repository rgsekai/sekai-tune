/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.stream

import android.content.Context
import android.net.ConnectivityManager
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
import moe.rgsekai.sekaitune.di.StreamResolutionScope
import moe.rgsekai.sekaitune.utils.YTPlayerUtils
import moe.rgsekai.sekaitune.utils.retryWithoutPlaybackLoginContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ResolveAudioStreamUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @StreamResolutionScope private val scope: CoroutineScope,
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

        suspend operator fun invoke(request: AudioStreamRequest): ResolvedAudioStream =
            resolve(request, ResolutionConsumer.PLAYBACK)

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
        }

        private suspend fun resolveUncached(request: AudioStreamRequest): ResolvedAudioStream {
            val playbackData =
                context.retryWithoutPlaybackLoginContext {
                    when (request.purpose) {
                        StreamPurpose.DOWNLOAD -> {
                            YTPlayerUtils.playerResponseForDownload(
                                videoId = request.mediaId,
                                audioQuality = request.quality,
                                connectivityManager = connectivityManager,
                                networkMetered = request.networkMetered,
                            )
                        }
                        StreamPurpose.PLAYBACK -> {
                            YTPlayerUtils.playerResponseForPlayback(
                                videoId = request.mediaId,
                                audioQuality = request.quality,
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

        private fun isFresh(stream: ResolvedAudioStream): Boolean =
            stream.expiresAtMs > System.currentTimeMillis() + STREAM_EXPIRY_SAFETY_MS

        private companion object {
            const val TAG = "ResolveAudioStream"
            const val STREAM_EXPIRY_SAFETY_MS = 60_000L
            const val MAX_CACHE_ENTRIES = 128
        }
    }
