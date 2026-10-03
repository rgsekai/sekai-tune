/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.utils

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rgsekai.sekaitune.constants.AudioQuality
import moe.rgsekai.sekaitune.constants.PlayerStreamClient
import moe.rgsekai.sekaitune.utils.ColdStartTimer
import moe.rgsekai.sekaitune.innertube.NewPipeUtils
import moe.rgsekai.sekaitune.innertube.PlaybackAuthState
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.ANDROID_TESTSUITE
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.ANDROID_UNPLUGGED
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.IOS
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.IOS_MUSIC
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.IPADOS
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.MOBILE
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.TVHTML5
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.VISIONOS
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.WEB
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse
import moe.rgsekai.sekaitune.playback.stream.PersistentVideoClientCache
import moe.rgsekai.sekaitune.simpstream.ITAG
import moe.rgsekai.sekaitune.simpstream.SimpMusicPlayer
import moe.rgsekai.sekaitune.simpstream.SimpStreamLog
import moe.rgsekai.sekaitune.utils.potoken.BotGuardTokenGenerator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    private const val SIMP_MUSIC_FAILURE_BACKOFF_MS = 60_000L
    private const val DEFAULT_STREAM_EXPIRE_SECONDS = 300
    private const val MAX_PLAYBACK_DATA_CACHE_ENTRIES = 128
    private const val PLAYBACK_DATA_RESOLUTION_MUTEX_COUNT = 32
    private const val RESOLUTION_CACHE_TTL_MS = 30_000L
    const val STREAM_URL_EXPIRY_SAFETY_MS = 60_000L
    private val RETRYABLE_STREAM_RESPONSE_CODES = setOf(403, 404, 410, 416)

    init {
        SimpStreamLog.sink = SimpStreamLog.Sink { level, tag, message, error ->
            when (level) {
                SimpStreamLog.DEBUG -> Timber.tag(tag).d(error, message)
                SimpStreamLog.INFO -> Timber.tag(tag).i(error, message)
                SimpStreamLog.WARN -> Timber.tag(tag).w(error, message)
                SimpStreamLog.ERROR -> Timber.tag(tag).e(error, message)
                else -> Timber.tag(tag).d(error, message)
            }
        }
    }

    private fun extractExpireTimestampMsFromUrl(url: String): Long? {
        val expireTimestamp =
            url
                .toHttpUrlOrNull()
                ?.queryParameter("expire")
                ?.toLongOrNull()
                ?: return null
        return expireTimestamp * 1000L
    }

    private fun extractExpireSecondsFromUrl(url: String): Int? {
        val expiresAtMs = extractExpireTimestampMsFromUrl(url) ?: return null
        val remaining = (expiresAtMs - System.currentTimeMillis()) / 1000L
        return remaining.toInt().takeIf { it > 0 }
    }

    private fun resolveExpireSeconds(
        apiExpire: Int?,
        streamUrl: String?,
    ): Int {
        apiExpire?.let { return it }
        streamUrl?.let { url ->
            extractExpireSecondsFromUrl(url)?.let { fromUrl ->
                Timber.tag(logTag).w("Using expire time extracted from stream URL: ${fromUrl}s")
                return fromUrl
            }
        }
        Timber.tag(logTag).w("No expire time available from API or URL, using default: ${DEFAULT_STREAM_EXPIRE_SECONDS}s")
        return DEFAULT_STREAM_EXPIRE_SECONDS
    }

    class LoginRequiredForPlaybackException(
        val videoId: String,
        val targetUrl: String,
        reason: String?,
    ) : IllegalStateException(reason)

    class InvalidPlaybackLoginContextException(
        val videoId: String,
        val targetUrl: String,
        cause: Throwable,
    ) : IllegalStateException("Invalid YouTube Music playback login context", cause)

    class BotDetectionPlaybackException(
        val videoId: String,
        val clients: Set<String>,
    ) : IllegalStateException("YouTube playback bot detection blocked all stream clients")

    class BadStreamPlayerResponseException(
        val videoId: String,
    ) : IllegalStateException("YouTube playback stream clients returned no playable response")

    private data class PlaybackGateFailure(
        val clientName: String,
        val status: String,
        val reason: String?,
    )

    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     *
     * [moe.rgsekai.sekaitune.innertube.models.YouTubeClient.WEB_REMIX] should be preferred here because currently it is the only client which provides:
     * - the correct metadata (like loudnessDb)
     * - premium formats
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> =
        arrayOf(
            WEB_REMIX,
            YouTubeClient.ANDROID_VR_1_43_32,
            YouTubeClient.ANDROID_VR_1_61_48,
            YouTubeClient.ANDROID_VR_NO_AUTH,
            VISIONOS,
            IPADOS,
            IOS,
            IOS_MUSIC,
            ANDROID_MUSIC,
            ANDROID_CREATOR,
            ANDROID_TESTSUITE,
            ANDROID_UNPLUGGED,
            TVHTML5,
            TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            WEB,
            WEB_CREATOR,
            MOBILE,
        )

    private data class StreamResolutionResult(
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamPlayerResponse: PlayerResponse,
        val streamClientUsed: YouTubeClient,
    )

    private data class CachedStreamUrl(
        val url: String,
        val expiresAtMs: Long,
        val authFingerprint: String,
    )

    private data class PlaybackDataCacheKey(
        val videoId: String,
        val audioQuality: AudioQuality,
        val networkMetered: Boolean,
        val authFingerprint: String,
    )

    private data class RecentResolutionKey(
        val videoId: String,
        val audioQuality: AudioQuality,
    )

    private data class CachedPlaybackData(
        val playbackData: PlaybackData,
        val expiresAtMs: Long,
    )

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val playbackDataCache = ConcurrentHashMap<PlaybackDataCacheKey, CachedPlaybackData>()
    private val recentResolutionsCache = ConcurrentHashMap<RecentResolutionKey, Pair<Result<PlaybackData>, Long>>()
    private val playbackDataResolutionMutexes = Array(PLAYBACK_DATA_RESOLUTION_MUTEX_COUNT) { Mutex() }
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()
    private val simpMusicFailedUntil = ConcurrentHashMap<String, Long>()

    @Volatile private var lastSuccessfulClientKey: String? = null

    fun clearPlaybackAuthCaches() {
        streamUrlCache.clear()
        playbackDataCache.clear()
        recentResolutionsCache.clear()
        failedStreamClientsUntil.clear()
        simpMusicFailedUntil.clear()
        lastSuccessfulClientKey = null
    }

    suspend fun preWarm() {
        runCatching {
            val t0 = System.currentTimeMillis()
            Timber.tag(logTag).i("Pre-warming InnerTube player session and connection pools...")
            val authState = YouTube.currentPlaybackAuthState()
            ensureVisitorDataReady("prewarm", authState, reason = "app_prewarm")
            coroutineScope {
                launch { YouTube.player("dQw4w9WgXcQ", client = WEB_REMIX) }
                launch { YouTube.player("dQw4w9WgXcQ", client = VISIONOS) }
                launch { YouTube.player("dQw4w9WgXcQ", client = YouTubeClient.ANDROID_VR_1_43_32) }
            }
            Timber.tag(logTag).i("InnerTube player session pre-warmed in %d ms", System.currentTimeMillis() - t0)
        }.onFailure {
            if (it !is CancellationException) {
                Timber.tag(logTag).w(it, "InnerTube prewarm encountered error")
            }
        }
    }

    suspend fun recoverFromBadStreamPlayerResponse(videoId: String) {
        val authState = YouTube.currentPlaybackAuthState()
        val refreshedAuthState =
            ensureVisitorDataReady(
                videoId = videoId,
                authState = authState,
                forceRefresh = true,
                reason = "all stream clients failed",
            )
        if (refreshedAuthState.fingerprint != authState.fingerprint) {
            YouTube.authState = refreshedAuthState
        }
        clearPlaybackAuthCaches()
    }

    private suspend fun ensureVisitorDataReady(
        videoId: String,
        authState: PlaybackAuthState,
        forceRefresh: Boolean = false,
        reason: String,
    ): PlaybackAuthState {
        if (!forceRefresh) {
            authState.visitorData
                ?.takeIf { it.isNotBlank() }
                ?.let { return authState }
        }

        val action = if (forceRefresh) "Refreshing" else "Fetching"
        Timber.tag(logTag).i("%s visitorData for %s (%s)", action, videoId, reason)

        val refreshedVisitorData =
            YouTube
                .visitorData()
                .onFailure {
                    Timber.tag(logTag).e(it, "Failed to refresh visitorData for $videoId")
                    reportException(it)
                }.getOrNull()
                ?.takeIf { it.isNotBlank() }

        if (refreshedVisitorData != null) {
            YouTube.visitorData = refreshedVisitorData
            return authState.copy(visitorData = refreshedVisitorData).normalized()
        }

        return authState
    }

    private suspend fun repairAuthStateAfterBotDetection(
        videoId: String,
        authState: PlaybackAuthState,
        reason: String,
    ): PlaybackAuthState {
        var repairedAuthState = authState

        if (authState.hasLoginCookie) {
            val activeChannel =
                YouTube
                    .accountChannels()
                    .onFailure {
                        Timber.tag(logTag).w(it, "Failed to refresh playback account channel for $videoId")
                        reportException(it)
                    }.getOrNull()
                    ?.let { channels ->
                        channels.firstOrNull { it.isSelected } ?: channels.firstOrNull()
                    }

            val refreshedDataSyncId = activeChannel?.dataSyncId?.takeIf { it.isNotBlank() }
            if (refreshedDataSyncId != null && refreshedDataSyncId != repairedAuthState.dataSyncId) {
                Timber.tag(logTag).i("Refreshed playback dataSyncId for %s after bot detection", videoId)
                repairedAuthState = repairedAuthState.copy(dataSyncId = refreshedDataSyncId).normalized()
            }
        }

        if (
            repairedAuthState.visitorData.isNullOrBlank() ||
            !hasCompleteWebPlaybackPoToken(repairedAuthState)
        ) {
            repairedAuthState =
                ensureVisitorDataReady(
                    videoId = videoId,
                    authState = repairedAuthState,
                    forceRefresh = true,
                    reason = reason,
                )
        }

        if (repairedAuthState.fingerprint != authState.fingerprint) {
            YouTube.authState = repairedAuthState
            clearPlaybackAuthCaches()
        }

        return repairedAuthState
    }

    private fun hasCompleteWebPlaybackPoToken(authState: PlaybackAuthState): Boolean =
        authState.webClientPoTokenEnabled &&
            !authState.resolvePlayerPoToken(WEB_REMIX).isNullOrBlank() &&
            !authState.resolveGvsPoToken(WEB_REMIX).isNullOrBlank()

    internal fun shouldSkipCipheredWebPlaybackCandidate(
        webClientPoTokenEnabled: Boolean,
        isWebClient: Boolean,
        isCiphered: Boolean,
        hasGvsPoToken: Boolean,
    ): Boolean =
        webClientPoTokenEnabled &&
            isWebClient &&
            isCiphered &&
            !hasGvsPoToken

    internal fun buildStreamCacheKey(
        videoId: String,
        itag: Int,
        client: YouTubeClient,
        authFingerprint: String,
    ): String = "$authFingerprint:$videoId:$itag:${StreamClientUtils.buildClientKey(client)}"

    fun invalidateCachedStreamUrls(videoId: String) {
        val marker = ":$videoId:"
        streamUrlCache.keys.removeIf { it.contains(marker) }
        playbackDataCache.keys.removeIf { it.videoId == videoId }
        simpMusicFailedUntil.remove(videoId)
    }

    fun markStreamUrlSuccessful(url: String) {
        StreamClientUtils
            .resolveRequestProfile(url)
            .clientKey
            .takeIf(String::isNotEmpty)
            ?.let { lastSuccessfulClientKey = it }
    }

    fun isExpiredOrNearExpiredStreamUrl(
        url: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val expiresAtMs = extractExpireTimestampMsFromUrl(url) ?: return false
        return expiresAtMs <= nowMs + STREAM_URL_EXPIRY_SAFETY_MS
    }

    fun markStreamClientFailed(
        videoId: String,
        clientKey: String?,
        httpStatusCode: Int?,
        authFingerprint: String = YouTube.currentPlaybackAuthState().fingerprint,
    ) {
        if (httpStatusCode != null && httpStatusCode !in RETRYABLE_STREAM_RESPONSE_CODES) return
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return
        failedStreamClientsUntil[buildFailedClientKey(videoId, normalizedClientKey, authFingerprint)] =
            System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
        val winningKey = PersistentVideoClientCache.getWinningClientKey(videoId)
        if (winningKey != null && normalizeStreamClientKey(winningKey) == normalizedClientKey) {
            PersistentVideoClientCache.invalidate(videoId)
        }
    }

    fun markPreferredClientFailed(
        videoId: String,
        client: PlayerStreamClient,
        httpStatusCode: Int?,
        authFingerprint: String = YouTube.currentPlaybackAuthState().fingerprint,
    ) {
        markStreamClientFailed(videoId, client.name, httpStatusCode, authFingerprint)
    }

    private fun isStreamClientTemporarilyBlocked(
        videoId: String,
        clientKey: String?,
        authFingerprint: String,
    ): Boolean {
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return false
        val key = buildFailedClientKey(videoId, normalizedClientKey, authFingerprint)
        val until = failedStreamClientsUntil[key] ?: return false
        if (until <= System.currentTimeMillis()) {
            failedStreamClientsUntil.remove(key)
            return false
        }
        return true
    }

    private fun normalizeStreamClientKey(clientKey: String?): String = StreamClientUtils.normalizeClientKey(clientKey)

    internal fun buildFailedClientKey(
        videoId: String,
        clientKey: String,
        authFingerprint: String,
    ): String = "$authFingerprint:$videoId:${normalizeStreamClientKey(clientKey)}"

    internal fun resolvePreferredPlaybackClient(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
    ): YouTubeClient =
        when (preferredStreamClient) {
            PlayerStreamClient.ANDROID_VR -> {
                YouTubeClient.ANDROID_VR_1_43_32
            }
            PlayerStreamClient.WEB_REMIX -> {
                YouTubeClient.WEB_REMIX
            }

            PlayerStreamClient.HI_RES_LOSSLESS -> {
                WEB_REMIX
            }

            PlayerStreamClient.IOS -> {
                IOS
            }

            PlayerStreamClient.TVHTML5 -> {
                TVHTML5
            }

            PlayerStreamClient.ANDROID_MUSIC -> {
                ANDROID_MUSIC
            }

            else -> {
                WEB_REMIX
            }
        }

    internal fun buildStreamClientOrder(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
        videoId: String? = null,
    ): List<YouTubeClient> {
        val preferredYouTubeClient = resolvePreferredPlaybackClient(preferredStreamClient, authState)
        val winningClientKey = videoId?.let { PersistentVideoClientCache.getWinningClientKey(it) }
        val allKnownClients = (listOf(
            preferredYouTubeClient,
            MAIN_CLIENT,
            YouTubeClient.ANDROID_VR_1_43_32,
            YouTubeClient.ANDROID_VR_1_61_48,
            YouTubeClient.ANDROID_VR_NO_AUTH,
        ) + STREAM_FALLBACK_CLIENTS).distinct()

        val winningClient = winningClientKey?.let { key ->
            allKnownClients.find { StreamClientUtils.buildClientKey(it) == key }
        }

        val lastSuccessfulClient =
            lastSuccessfulClientKey?.let { key ->
                STREAM_FALLBACK_CLIENTS.find { StreamClientUtils.buildClientKey(it) == key }
            }

        val orderedFallbackClients =
            if (authState.hasPlaybackLoginContext) {
                STREAM_FALLBACK_CLIENTS.filter { it.supportsCookieAuthentication } +
                    STREAM_FALLBACK_CLIENTS.filterNot { it.supportsCookieAuthentication }
            } else {
                STREAM_FALLBACK_CLIENTS.toList()
            }

        return buildList {
            winningClient?.let { add(it) }
            lastSuccessfulClient?.let { add(it) }
            if (authState.hasPlaybackLoginContext && hasCompleteWebPlaybackPoToken(authState)) {
                add(WEB_REMIX)
            }
            add(preferredYouTubeClient)
            addAll(orderedFallbackClients)
            if (preferredYouTubeClient != MAIN_CLIENT) add(MAIN_CLIENT)
            if (preferredStreamClient == PlayerStreamClient.WEB_REMIX) {
                addAll(STREAM_FALLBACK_CLIENTS)
            }
        }.distinct()
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val authFingerprint: String,
    )

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.ANDROID_VR,
        // if provided, this preference overrides ConnectivityManager.isActiveNetworkMetered
        networkMetered: Boolean? = null,
    ): Result<PlaybackData> {
        val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
        val initialKey =
            buildPlaybackDataCacheKey(
                videoId = videoId,
                audioQuality = audioQuality,
                networkMetered = isMetered,
                authFingerprint = authFingerprint,
            )
        getCachedPlaybackData(initialKey)?.let { return Result.success(it) }

        val now = System.currentTimeMillis()
        val recentKey = RecentResolutionKey(videoId, audioQuality)
        recentResolutionsCache[recentKey]?.let { (result, timestamp) ->
            if (now - timestamp < RESOLUTION_CACHE_TTL_MS) {
                ColdStartTimer.addStage("PlaybackData Resolution Sequential Cache Hit for $videoId")
                return result
            }
        }

        ColdStartTimer.addStage("PlaybackData Resolution Lock for $videoId")
        val resolutionMutex =
            playbackDataResolutionMutexes[(initialKey.hashCode() and Int.MAX_VALUE) % playbackDataResolutionMutexes.size]
        return resolutionMutex.withLock {
            val currentKey =
                buildPlaybackDataCacheKey(
                    videoId = videoId,
                    audioQuality = audioQuality,
                    networkMetered = isMetered,
                    authFingerprint = authFingerprint,
                )
            getCachedPlaybackData(currentKey)?.let {
                ColdStartTimer.addStage("PlaybackData Resolution Shared Result for $videoId")
                return@withLock Result.success(it)
            }

            val recentKeyInner = RecentResolutionKey(videoId, audioQuality)
            recentResolutionsCache[recentKeyInner]?.let { (result, timestamp) ->
                if (System.currentTimeMillis() - timestamp < RESOLUTION_CACHE_TTL_MS) {
                    ColdStartTimer.addStage("PlaybackData Resolution Shared Sequential Cache Hit for $videoId")
                    return@withLock result
                }
            }

            resolvePlaybackData(
                videoId = videoId,
                playlistId = playlistId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                preferredStreamClient = preferredStreamClient,
                networkMetered = isMetered,
            ).onSuccess { playbackData ->
                cachePlaybackData(
                    key = currentKey.copy(authFingerprint = playbackData.authFingerprint),
                    playbackData = playbackData,
                )
                recentResolutionsCache[recentKeyInner] = Result.success(playbackData) to System.currentTimeMillis()
            }.onFailure { error ->
                if (error !is CancellationException) {
                    recentResolutionsCache[recentKeyInner] = Result.failure<PlaybackData>(error) to System.currentTimeMillis()
                }
            }.also { result ->
                val failure = result.exceptionOrNull()
                if (failure is CancellationException) throw failure
            }
        }
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean,
    ): Result<PlaybackData> =
        runCatching {
            val attempts =
                when (audioQuality) {
                    AudioQuality.HIGHEST -> listOf(AudioQuality.HIGHEST, AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.HIGH -> listOf(AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.AUTO -> listOf(AudioQuality.AUTO, AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.LOW -> listOf(AudioQuality.LOW, AudioQuality.HIGH, AudioQuality.AUTO)
                    else -> listOf(audioQuality)
                }.distinct()

            var lastError: Throwable? = null
            var didRefreshIpRotationAfterBotDetection = false
            for (attempt in attempts) {
                val attemptResult =
                    runCatching {
                        playerResponseForPlaybackOnce(
                            videoId = videoId,
                            playlistId = playlistId,
                            audioQuality = attempt,
                            connectivityManager = connectivityManager,
                            preferredStreamClient = preferredStreamClient,
                            networkMetered = networkMetered,
                        )
                    }
                if (attemptResult.isSuccess) return@runCatching attemptResult.getOrThrow()
                lastError = attemptResult.exceptionOrNull()
                if (lastError is CancellationException) throw lastError
                if (
                    !didRefreshIpRotationAfterBotDetection &&
                    lastError is BotDetectionPlaybackException &&
                    refreshIpRotationForBotDetection(videoId, lastError)
                ) {
                    didRefreshIpRotationAfterBotDetection = true
                    val rotatedAttemptResult =
                        runCatching {
                            playerResponseForPlaybackOnce(
                                videoId = videoId,
                                playlistId = playlistId,
                                audioQuality = attempt,
                                connectivityManager = connectivityManager,
                                preferredStreamClient = preferredStreamClient,
                                networkMetered = networkMetered,
                            )
                        }
                    if (rotatedAttemptResult.isSuccess) return@runCatching rotatedAttemptResult.getOrThrow()
                    lastError = rotatedAttemptResult.exceptionOrNull()
                    if (lastError is CancellationException) throw lastError
                }
                if (lastError is BadStreamPlayerResponseException ||
                    lastError is BotDetectionPlaybackException ||
                    lastError is LoginRequiredForPlaybackException ||
                    lastError is java.io.IOException
                ) {
                    break
                }
            }
            throw lastError ?: IllegalStateException("Failed to resolve stream")
        }

    private fun buildPlaybackDataCacheKey(
        videoId: String,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
        authFingerprint: String,
    ): PlaybackDataCacheKey =
        PlaybackDataCacheKey(
            videoId = videoId,
            audioQuality = audioQuality,
            networkMetered = networkMetered,
            authFingerprint = authFingerprint,
        )

    private fun getCachedPlaybackData(key: PlaybackDataCacheKey): PlaybackData? {
        val cached = playbackDataCache[key] ?: return null
        val now = System.currentTimeMillis()
        if (
            cached.expiresAtMs <= now + STREAM_URL_EXPIRY_SAFETY_MS ||
            isExpiredOrNearExpiredStreamUrl(cached.playbackData.streamUrl, now)
        ) {
            playbackDataCache.remove(key, cached)
            return null
        }
        return cached.playbackData
    }

    private fun cachePlaybackData(
        key: PlaybackDataCacheKey,
        playbackData: PlaybackData,
    ) {
        val now = System.currentTimeMillis()
        val expiresAtMs = now + playbackData.streamExpiresInSeconds.coerceAtLeast(1) * 1000L
        playbackDataCache[key] = CachedPlaybackData(playbackData, expiresAtMs)
        if (playbackDataCache.size <= MAX_PLAYBACK_DATA_CACHE_ENTRIES) return
        playbackDataCache.entries.removeIf { (_, cached) ->
            cached.expiresAtMs <= now + STREAM_URL_EXPIRY_SAFETY_MS
        }
        while (playbackDataCache.size > MAX_PLAYBACK_DATA_CACHE_ENTRIES) {
            val oldest = playbackDataCache.entries.minByOrNull { it.value.expiresAtMs } ?: break
            playbackDataCache.remove(oldest.key, oldest.value)
        }
    }

    suspend fun playerResponseForDownload(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        networkMetered: Boolean? = null,
    ): Result<PlaybackData> =
        runCatching {
            Timber.tag(logTag).i("Fetching download response for videoId: $videoId, playlistId: $playlistId")
            var lastError: Throwable? = null

            for (preferredStreamClient in downloadPreferredStreamClientAttempts) {
                val attemptResult =
                    playerResponseForPlayback(
                        videoId = videoId,
                        playlistId = playlistId,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager,
                        preferredStreamClient = preferredStreamClient,
                        networkMetered = networkMetered,
                    )

                if (attemptResult.isSuccess) return@runCatching attemptResult.getOrThrow()

                lastError = attemptResult.exceptionOrNull()
                Timber.tag(logTag).w(
                    lastError,
                    "Download stream resolution failed with preferred client %s for %s",
                    preferredStreamClient.name,
                    videoId,
                )
            }

            throw lastError ?: IllegalStateException("Failed to resolve download stream for $videoId")
        }

    private val downloadPreferredStreamClientAttempts: List<PlayerStreamClient> =
        listOf(
            PlayerStreamClient.WEB_REMIX,
            PlayerStreamClient.HI_RES_LOSSLESS,
            PlayerStreamClient.IOS,
            PlayerStreamClient.TVHTML5,
            PlayerStreamClient.ANDROID_MUSIC,
        )

    private suspend fun refreshIpRotationForBotDetection(
        videoId: String,
        failure: BotDetectionPlaybackException?,
    ): Boolean {
        if (failure == null) return false
        if (YouTube.ipRotationActiveCount.value <= 0) return false

        return runCatching {
            Timber.tag(logTag).w(
                failure,
                "Refreshing IP rotation after YouTube bot detection blocked playback for %s",
                videoId,
            )
            YouTube.refreshIpRotation()
            clearPlaybackAuthCaches()
        }.onFailure {
            Timber.tag(logTag).w(it, "Failed to refresh IP rotation after bot detection for %s", videoId)
            reportException(it)
        }.isSuccess
    }

    private fun selectSimpMusicFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        isMetered: Boolean,
    ): PlayerResponse.StreamingData.Format? {
        val formats = playerResponse.streamingData?.adaptiveFormats ?: emptyList()
        val audioFormats = formats.filter { it.isAudio }

        val targetItags: List<Int> =
            when (audioQuality) {
                AudioQuality.LOW ->
                    listOf(
                        ITAG.AUDIO_OPUS_LOW,
                        ITAG.AUDIO_OPUS_MEDIUM,
                        ITAG.AUDIO_AAC_LOW,
                        ITAG.AUDIO_AAC_MEDIUM,
                    )
                AudioQuality.HIGH, AudioQuality.HIGHEST ->
                    listOf(
                        ITAG.AUDIO_OPUS_HIGH,
                        ITAG.AUDIO_OPUS_MEDIUM,
                        ITAG.AUDIO_AAC_HIGH,
                        ITAG.AUDIO_AAC_MEDIUM,
                        ITAG.AUDIO_OPUS_LOW,
                    )
                AudioQuality.AUTO ->
                    if (isMetered) {
                        listOf(
                            ITAG.AUDIO_OPUS_MEDIUM,
                            ITAG.AUDIO_OPUS_LOW,
                            ITAG.AUDIO_AAC_MEDIUM,
                            ITAG.AUDIO_AAC_LOW,
                        )
                    } else {
                        listOf(
                            ITAG.AUDIO_OPUS_HIGH,
                            ITAG.AUDIO_OPUS_MEDIUM,
                            ITAG.AUDIO_AAC_HIGH,
                            ITAG.AUDIO_AAC_MEDIUM,
                            ITAG.AUDIO_OPUS_LOW,
                        )
                    }
            }

        for (itag in targetItags) {
            val found = audioFormats.firstOrNull { it.itag == itag && (!it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank() || !it.cipher.isNullOrBlank()) }
            if (found != null) return found
        }

        val availableAudio = audioFormats.filter { !it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank() || !it.cipher.isNullOrBlank() }
        val preferOpus =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.bitrate }
        return availableAudio.maxWithOrNull(preferOpus)
            ?: formats.firstOrNull { !it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank() || !it.cipher.isNullOrBlank() }
    }

    private suspend fun trySimpMusicResolution(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        networkMetered: Boolean?,
        authState: PlaybackAuthState,
    ): PlaybackData? {
        val now = System.currentTimeMillis()
        val failedUntil = simpMusicFailedUntil[videoId]
        if (failedUntil != null) {
            if (failedUntil > now) {
                Timber.tag(logTag).d("Tier 0 SimpMusic resolution in backoff for %s (%ds remaining)", videoId, (failedUntil - now) / 1000)
                return null
            } else {
                simpMusicFailedUntil.remove(videoId)
            }
        }

        val startResolveMs = System.currentTimeMillis()
        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered

        return runCatching {
            val result = SimpMusicPlayer.player(videoId, playlistId, authState).getOrThrow()
            val cpn = result.first
            val playerResponse = result.second

            val selectedFormat =
                selectSimpMusicFormat(playerResponse, audioQuality, isMetered)
                    ?: throw IllegalStateException("No valid audio format found in SimpMusic response for $videoId")

            val rawUrl =
                selectedFormat.url
                    ?: NewPipeUtils.getStreamUrl(selectedFormat, videoId, authState = authState).getOrNull()
                    ?: throw IllegalStateException("Selected format itag ${selectedFormat.itag} has null url")

            val streamUrl =
                if (SimpMusicPlayer.isManifestUrl(rawUrl)) {
                    if (rawUrl.contains("cpn=")) rawUrl else "$rawUrl&cpn=$cpn"
                } else {
                    val length = selectedFormat.contentLength ?: 10_000_000L
                    if (rawUrl.contains("cpn=")) {
                        if (rawUrl.contains("range=")) rawUrl else "$rawUrl&range=0-$length"
                    } else {
                        "$rawUrl&cpn=$cpn&range=0-$length"
                    }
                }

            val streamExpiresInSeconds =
                resolveExpireSeconds(
                    apiExpire = playerResponse.streamingData?.expiresInSeconds,
                    streamUrl = streamUrl,
                )

            val tracking = playerResponse.playbackTracking
            val pbBase = tracking?.videostatsPlaybackUrl?.baseUrl
            val wtBase = tracking?.videostatsWatchtimeUrl?.baseUrl
            val atrBase = tracking?.atrUrl?.baseUrl
            val normalizedTracking =
                if (tracking != null) {
                    PlayerResponse.PlaybackTracking(
                        videostatsPlaybackUrl =
                            pbBase?.replace("https://s.youtube.com", "https://music.youtube.com")
                                ?.let { PlayerResponse.PlaybackTracking.VideostatsPlaybackUrl(it) },
                        videostatsWatchtimeUrl =
                            wtBase?.replace("https://s.youtube.com", "https://music.youtube.com")
                                ?.let { PlayerResponse.PlaybackTracking.VideostatsWatchtimeUrl(it) },
                        atrUrl =
                            atrBase?.replace("https://s.youtube.com", "https://music.youtube.com")
                                ?.let { PlayerResponse.PlaybackTracking.AtrUrl(it) },
                    )
                } else {
                    null
                }

            val extractSource = SimpMusicPlayer.getExtractSource(videoId) ?: "Unknown"
            val resolveDurationMs = System.currentTimeMillis() - startResolveMs
            Timber.tag("TrackTelemetry").i(
                "[TrackTelemetry:SimpMusic] videoId=%s, tier=%s, itag=%d, mime=%s, durationMs=%d",
                videoId,
                extractSource,
                selectedFormat.itag,
                selectedFormat.mimeType,
                resolveDurationMs,
            )

            PlaybackData(
                audioConfig = playerResponse.playerConfig?.audioConfig,
                videoDetails = playerResponse.videoDetails,
                playbackTracking = normalizedTracking,
                format = selectedFormat,
                streamUrl = streamUrl,
                streamExpiresInSeconds = streamExpiresInSeconds,
                authFingerprint = authState.fingerprint,
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            Timber.tag(logTag).w(
                error,
                "Tier 0 SimpMusic resolution failed for %s; applying 60s failure backoff and falling back to multi-client chain",
                videoId,
            )
            simpMusicFailedUntil[videoId] = System.currentTimeMillis() + SIMP_MUSIC_FAILURE_BACKOFF_MS
        }.getOrNull()
    }

    private suspend fun playerResponseForPlaybackOnce(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean?,
    ): PlaybackData {
        try {
            return playerResponseForPlaybackOnceInternal(
                videoId = videoId,
                playlistId = playlistId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                preferredStreamClient = preferredStreamClient,
                networkMetered = networkMetered,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.tag(logTag).w(e, "Multi-client playback resolution failed for %s; attempting Tier 0 SimpMusic fallback", videoId)
            val authState = YouTube.currentPlaybackAuthState()
            val fallback =
                trySimpMusicResolution(
                    videoId = videoId,
                    playlistId = playlistId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                    networkMetered = networkMetered,
                    authState = authState,
                )
            if (fallback != null) {
                Timber.tag(logTag).i("Tier 0 SimpMusic fallback succeeded for %s after multi-client failure", videoId)
                return fallback
            }
            throw e
        }
    }

    private suspend fun playerResponseForPlaybackOnceInternal(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean?,
    ): PlaybackData {
        val startResolveMs = System.currentTimeMillis()
        Timber.tag(logTag).i("Fetching player response for videoId: $videoId, playlistId: $playlistId")

        var authState = YouTube.currentPlaybackAuthState()

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)

        Timber.tag(logTag).v("Signature timestamp: $signatureTimestamp")

        val hasLoginCookie = authState.hasLoginCookie
        var canUseLoggedInPlayback = authState.hasPlaybackLoginContext
        if (!canUseLoggedInPlayback) {
            if (hasLoginCookie) {
                Timber.tag(logTag).w(
                    "Ignoring incomplete login context for %s because dataSyncId is missing; falling back to visitorData playback",
                    videoId,
                )
            }
            authState =
                ensureVisitorDataReady(
                    videoId = videoId,
                    authState = authState,
                    reason = if (hasLoginCookie) "cookie-only playback fallback" else "anonymous playback bootstrap",
                )
        }
        val sessionId = authState.visitorData
        val authStatus =
            when {
                canUseLoggedInPlayback -> "Logged in"
                hasLoginCookie -> "Cookie-only"
                else -> "Not logged in"
            }
        Timber.tag(logTag).v("Session authentication status: $authStatus (sessionId=${sessionId.orEmpty()})")

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var streamClientUsed: YouTubeClient? = null
        var didRepairAuthAfterBotDetection = false
        var didRetryWithoutRejectedLoginContext = false

        val streamClients =
            buildStreamClientOrder(preferredStreamClient, authState, videoId).filterNot { client ->
                val blocked =
                    isStreamClientTemporarilyBlocked(
                        videoId = videoId,
                        clientKey = StreamClientUtils.buildClientKey(client),
                        authFingerprint = authState.fingerprint,
                    )
                if (blocked) {
                    Timber.tag(logTag).w("Temporarily blocked stream client for $videoId: ${describeClient(client)}")
                }
                blocked
            }

        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered

        val fastClients = streamClients.take(2)
        val fallbackClients = streamClients.drop(2)

        Timber.tag(logTag).i("Racing ${fastClients.size} primary stream clients for $videoId")
        var resolutionResult: StreamResolutionResult? = coroutineScope {
            val probeTasks: List<suspend () -> StreamResolutionResult?> = fastClients.map { client ->
                {
                    probeStreamClient(
                        client = client,
                        videoId = videoId,
                        playlistId = playlistId,
                        signatureTimestamp = signatureTimestamp,
                        authState = authState,
                        canUseLoggedInPlayback = canUseLoggedInPlayback,
                        audioQuality = audioQuality,
                        networkMetered = isMetered,
                        expectedDurationMs = null,
                    )
                }
            }

            raceFirstNonNull(probeTasks)
        }

        if (resolutionResult == null && fallbackClients.isNotEmpty()) {
            Timber.tag(logTag).w("Primary fast clients failed for $videoId; probing ${fallbackClients.size} fallback clients")
            for (fallbackClient in fallbackClients) {
                val candidate = probeStreamClient(
                    client = fallbackClient,
                    videoId = videoId,
                    playlistId = playlistId,
                    signatureTimestamp = signatureTimestamp,
                    authState = authState,
                    canUseLoggedInPlayback = canUseLoggedInPlayback,
                    audioQuality = audioQuality,
                    networkMetered = isMetered,
                    expectedDurationMs = null,
                )
                if (candidate != null) {
                    resolutionResult = candidate
                    break
                }
            }
        }

        if (resolutionResult == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed for $videoId")
            throw BadStreamPlayerResponseException(videoId)
        }

        val chosenFormat = resolutionResult.format
        val chosenStreamUrl = resolutionResult.streamUrl
        val chosenStreamClientUsed = resolutionResult.streamClientUsed
        val chosenStreamExpiresInSeconds = resolutionResult.streamExpiresInSeconds
        val chosenStreamPlayerResponse = resolutionResult.streamPlayerResponse

        Timber.tag(logTag).i("Successfully obtained playback data with format: ${chosenFormat.mimeType}, bitrate: ${chosenFormat.bitrate} via client: ${chosenStreamClientUsed.clientName}")

        val resolvedStreamClient = chosenStreamClientUsed

        PersistentVideoClientCache.putWinningClient(videoId, resolvedStreamClient)
        lastSuccessfulClientKey = StreamClientUtils.buildClientKey(resolvedStreamClient)

        val resolveDurationMs = System.currentTimeMillis() - startResolveMs
        Timber.tag("TrackTelemetry").i(
            "[TrackTelemetry:YTPlayerUtils] videoId=%s, client=%s, format=%s, durationMs=%d",
            videoId,
            resolvedStreamClient.clientName,
            chosenFormat.mimeType,
            resolveDurationMs,
        )

        return PlaybackData(
            chosenStreamPlayerResponse.playerConfig?.audioConfig,
            chosenStreamPlayerResponse.videoDetails,
            chosenStreamPlayerResponse.playbackTracking,
            chosenFormat,
            chosenStreamUrl,
            chosenStreamExpiresInSeconds,
            authState.fingerprint,
        )
    }

    private suspend fun probeStreamClient(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
        signatureTimestamp: Int?,
        authState: PlaybackAuthState,
        canUseLoggedInPlayback: Boolean,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
        expectedDurationMs: Long?,
        preloadedPlayerResponse: PlayerResponse? = null,
    ): StreamResolutionResult? {
        val requestUsesCookieAuthentication =
            canUseLoggedInPlayback && client.supportsCookieAuthentication
        if (client != MAIN_CLIENT && client.loginRequired && !requestUsesCookieAuthentication) {
            Timber.tag(logTag).d("Skipping client ${describeClient(client)} - requires cookie auth")
            return null
        }

        var clientAuthState = authState
        var poToken: String? = null
        if (client.useWebPoTokens) {
            val sessionId = authState.visitorData ?: YouTube.visitorData
            if (!sessionId.isNullOrBlank()) {
                try {
                    val tokenResult = BotGuardTokenGenerator.mintToken(videoId, sessionId)
                    poToken = tokenResult?.playerToken
                    tokenResult?.let {
                        clientAuthState =
                            clientAuthState.copy(
                                poTokenGvs = it.sessionToken,
                                poTokenPlayer = it.playerToken,
                                webClientPoTokenEnabled = true,
                            )
                    }
                } catch (e: Exception) {
                    Timber.tag(logTag).w(e, "PoToken minting failed for ${client.clientName}")
                }
            }
        }

        val resp = preloadedPlayerResponse ?: runCatching {
            YouTube.player(
                videoId = videoId,
                playlistId = playlistId,
                client = client,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken,
                setLogin = requestUsesCookieAuthentication,
                authState = clientAuthState,
            ).getPlaybackPlayerResponseOrNull(videoId, clientAuthState)
        }.getOrNull()

        if (resp == null) return null

        val playabilityStatus = resp.playabilityStatus
        if (playabilityStatus.status != "OK") {
            Timber.tag(logTag).w(
                "Player response status not OK for ${describeClient(client)}: ${playabilityStatus.status}, reason: ${playabilityStatus.reason.orEmpty()}",
            )
            return null
        }

        val candidates = selectAudioFormatCandidates(resp, audioQuality, networkMetered)
        if (candidates.isEmpty()) {
            Timber.tag(logTag).w("probeStreamClient: client=${describeClient(client)} has no audio candidates")
            return null
        }

        for (candidate in candidates) {
            if (canUseLoggedInPlayback && expectedDurationMs != null && isLikelyPreview(candidate, expectedDurationMs)) continue
            val cacheKey = buildStreamCacheKey(videoId, candidate.itag, client, clientAuthState.fingerprint)
            val cached = streamUrlCache[cacheKey]
            val candidateResult =
                if (cached != null && cached.expiresAtMs > System.currentTimeMillis() + STREAM_URL_EXPIRY_SAFETY_MS) {
                    Result.success(cached.url)
                } else {
                    findUrl(candidate, videoId, client, clientAuthState)
                }
            val candidateUrl = candidateResult.getOrNull()
            if (candidateUrl == null) {
                Timber.tag(logTag).w("probeStreamClient: candidate itag=${candidate.itag} failed for ${describeClient(client)}: ${candidateResult.exceptionOrNull()?.message}")
                continue
            }
            val expireSecs = resolveExpireSeconds(
                apiExpire = resp.streamingData?.expiresInSeconds,
                streamUrl = candidateUrl,
            )
            return StreamResolutionResult(
                format = candidate,
                streamUrl = candidateUrl,
                streamExpiresInSeconds = expireSecs,
                streamPlayerResponse = resp,
                streamClientUsed = client,
            )
        }
        return null
    }

    private suspend fun <T : Any> raceFirstNonNull(
        tasks: List<suspend () -> T?>
    ): T? = kotlinx.coroutines.coroutineScope {
        if (tasks.isEmpty()) return@coroutineScope null
        val channel = kotlinx.coroutines.channels.Channel<T>(kotlinx.coroutines.channels.Channel.CONFLATED)
        val activeCount = java.util.concurrent.atomic.AtomicInteger(tasks.size)
        val jobs = tasks.map { task ->
            launch {
                try {
                    val res = task()
                    if (res != null) {
                        channel.trySend(res)
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                } finally {
                    if (activeCount.decrementAndGet() == 0) {
                        channel.close()
                    }
                }
            }
        }
        val winner = try {
            channel.receiveCatching().getOrNull()
        } finally {
            jobs.forEach { it.cancel() }
        }
        winner
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<PlayerResponse> {
        Timber.tag(logTag).i("Fetching metadata player response for videoId: $videoId")

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        val sessionId = authState.visitorData
        var poToken: String? = null

        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            try {
                val tokenResult = BotGuardTokenGenerator.mintToken(videoId, sessionId)
                poToken = tokenResult?.playerToken
                tokenResult?.let {
                    YouTube.authState =
                        YouTube.authState.copy(
                            poTokenGvs = it.sessionToken,
                            poTokenPlayer = it.playerToken,
                            webClientPoTokenEnabled = true,
                        )
                }
            } catch (e: Exception) {
                Timber.tag(logTag).w(e, "PoToken generation failed for metadata request")
            }
        }

        return YouTube
            .player(
                videoId = videoId,
                playlistId = playlistId,
                client = MAIN_CLIENT,
                signatureTimestamp = signatureTimestamp,
                poToken = poToken,
                setLogin = true,
                authState = authState,
            ).onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        // optional override from user preference; if non-null, use this instead of ConnectivityManager
        networkMetered: Boolean? = null,
    ): PlayerResponse.StreamingData.Format? {
        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
        return selectAudioFormatCandidates(
            playerResponse,
            audioQuality,
            isMetered,
        ).firstOrNull()
    }

    private fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
    ): List<PlayerResponse.StreamingData.Format> {
        Timber.tag(logTag).i("Finding format with audioQuality: $audioQuality, network metered: $networkMetered")

        val audioFormats =
            playerResponse.streamingData
                ?.adaptiveFormats
                ?.asSequence()
                ?.filter { it.isAudio && it.bitrate > 0 }
                ?.filter { it.url != null || it.signatureCipher != null || it.cipher != null }
                ?.toList()
                .orEmpty()

        if (audioFormats.isEmpty()) return emptyList()

        val effectiveQuality =
            when (audioQuality) {
                AudioQuality.AUTO -> if (networkMetered) AudioQuality.HIGH else AudioQuality.HIGHEST
                else -> audioQuality
            }

        val targetBitrateBps =
            when (effectiveQuality) {
                AudioQuality.LOW -> 70_000
                AudioQuality.HIGH -> 160_000
                AudioQuality.HIGHEST -> 320_000
                AudioQuality.AUTO -> null
            }

        val preferHigher =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.bitrate }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val preferLowerAboveTarget =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenBy { it.bitrate }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val candidates =
            when {
                targetBitrateBps == null || effectiveQuality == AudioQuality.HIGHEST -> {
                    audioFormats.sortedWith(preferHigher)
                }

                else -> {
                    val preferred =
                        audioFormats
                            .filter { it.bitrate <= targetBitrateBps }
                            .sortedWith(preferHigher)
                    val fallback =
                        audioFormats
                            .filter { it.bitrate > targetBitrateBps }
                            .sortedWith(preferLowerAboveTarget)

                    preferred + fallback
                }
            }

        Timber
            .tag(logTag)
            .v(
                "Available audio formats: ${
                    candidates.take(12).map {
                        val codec = extractCodec(it.mimeType)
                        val direct = if (it.url != null) "direct" else "cipher"
                        "${it.mimeType} ($direct, codec=${codec ?: "unknown"}) @ ${it.bitrate}bps"
                    }
                }",
            )

        return candidates
    }

    private fun extractCodec(mimeType: String): String? {
        val match = Regex("""codecs="([^"]+)"""").find(mimeType) ?: return null
        return match.groupValues
            .getOrNull(1)
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
    }

    private fun isCipheredFormat(format: PlayerResponse.StreamingData.Format): Boolean =
        format.url == null && (format.signatureCipher != null || format.cipher != null)

    private fun shouldSkipCipheredWebCandidate(
        client: YouTubeClient,
        format: PlayerResponse.StreamingData.Format,
        authState: PlaybackAuthState,
    ): Boolean {
        val isWebClient = StreamClientUtils.isWebClient(client.clientName)
        val isCiphered = isCipheredFormat(format)
        val hasGvsPoToken = !authState.resolveGvsPoToken(client).isNullOrBlank()
        if (
            !shouldSkipCipheredWebPlaybackCandidate(
                webClientPoTokenEnabled = authState.webClientPoTokenEnabled,
                isWebClient = isWebClient,
                isCiphered = isCiphered,
                hasGvsPoToken = hasGvsPoToken,
            )
        ) {
            return false
        }

        Timber.tag(logTag).w(
            "Skipping ciphered %s stream candidate because Web PoToken playback is enabled but no GVS token is available",
            client.clientName,
        )
        return true
    }

    private fun codecRank(codec: String?): Int =
        when {
            codec.isNullOrBlank() -> 0
            codec.contains("opus", ignoreCase = true) -> 3
            codec.contains("mp4a", ignoreCase = true) -> 2
            else -> 1
        }

    private fun isLikelyPreview(
        format: PlayerResponse.StreamingData.Format,
        expectedDurationMs: Long,
    ): Boolean {
        val approx = format.approxDurationMs?.toLongOrNull() ?: return false
        if (expectedDurationMs < 90_000L) return false
        return approx in 1L..(minOf(90_000L, (expectedDurationMs * 9L) / 10L))
    }

    /**
     * Wrapper around the [NewPipeUtils.getSignatureTimestamp] function which reports exceptions
     */
    private suspend fun getSignatureTimestampOrNull(videoId: String): Int? {
        Timber.tag(logTag).i("Getting signature timestamp for videoId: $videoId")
        return NewPipeUtils
            .getSignatureTimestamp(videoId)
            .onSuccess { Timber.tag(logTag).i("Signature timestamp obtained: $it") }
            .onFailure {
                Timber.tag(logTag).e(it, "Failed to get signature timestamp")
                reportException(it)
            }.getOrNull()
    }

    /**
     * Wrapper around the [NewPipeUtils.getStreamUrl] function which reports exceptions.
     * Also patches cver to match the client version.
     */
    private suspend fun findUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState,
    ): Result<String> {
        Timber.tag(logTag).i("Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        return NewPipeUtils
            .getStreamUrl(format, videoId, client, authState)
            .map { url ->
                if (client == null) url else StreamClientUtils.patchClientVersion(url, client.clientVersion)
            }.onSuccess { Timber.tag(logTag).i("Stream URL obtained successfully") }
    }

    private fun Throwable.isJavaScriptPlayerExtractorFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty()
            if (
                message.contains("deobfuscation", ignoreCase = true) ||
                message.contains("JavaScript player", ignoreCase = true) ||
                message.contains("base JavaScript player", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun Result<PlayerResponse>.getPlaybackPlayerResponseOrThrow(
        videoId: String,
        authState: PlaybackAuthState,
    ): PlayerResponse {
        val failure = exceptionOrNull()
        if (failure != null) {
            throwInvalidPlaybackLoginContextIfNeeded(videoId, authState, failure)
            throw failure
        }
        return getOrThrow()
    }

    private fun Result<PlayerResponse>.getPlaybackPlayerResponseOrNull(
        videoId: String,
        authState: PlaybackAuthState,
    ): PlayerResponse? {
        val failure = exceptionOrNull()
        if (failure != null) {
            throwInvalidPlaybackLoginContextIfNeeded(videoId, authState, failure)
            return null
        }
        return getOrNull()
    }

    private fun throwInvalidPlaybackLoginContextIfNeeded(
        videoId: String,
        authState: PlaybackAuthState,
        failure: Throwable,
    ) {
        if (!authState.hasPlaybackLoginContext) return
        if (!failure.isInvalidPlaybackLoginContextFailure()) return

        Timber.tag(logTag).w(
            failure,
            "Detected invalid logged-in playback context for %s; requiring login refresh",
            videoId,
        )
        throw InvalidPlaybackLoginContextException(
            videoId = videoId,
            targetUrl = "https://music.youtube.com/watch?v=$videoId",
            cause = failure,
        )
    }

    private fun Throwable.isInvalidPlaybackLoginContextFailure(): Boolean {
        val clientError = this as? ClientRequestException ?: return false
        if (clientError.response.status != HttpStatusCode.BadRequest) return false

        val message = clientError.message.orEmpty()
        if (!message.contains("/youtubei/v1/player", ignoreCase = true)) return false
        if (message.contains("Origin doesn't match Host", ignoreCase = true)) return false

        return message.contains("INVALID_ARGUMENT", ignoreCase = true) ||
            message.contains("invalid argument", ignoreCase = true)
    }

    private fun isBotDetectionError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "bot" in lower ||
            "unusual traffic" in lower ||
            "automated" in lower ||
            "confirm" in lower && "not a" in lower ||
            "not a robot" in lower ||
            "verify" in lower && "human" in lower
    }

    private fun isLoginRecoveryError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "confirm your age" in lower ||
            "age-restricted" in lower ||
            "age restricted" in lower ||
            "inappropriate for some users" in lower ||
            "mature audiences" in lower ||
            "adult" in lower && "sign in" in lower ||
            "please sign in" in lower ||
            "sign in to confirm" in lower ||
            "allow" in lower && "youtube music" in lower
    }

    private fun isLoginRecoveryResponse(
        status: String,
        reason: String,
    ): Boolean = status.equals("LOGIN_REQUIRED", ignoreCase = true) || isLoginRecoveryError(reason)

    fun isBotDetectionException(error: PlaybackException): Boolean {
        val message = error.message.orEmpty()
        if (isBotDetectionError(message)) return true
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is BotDetectionPlaybackException) return true
            if (isBotDetectionError(cause.message.orEmpty())) return true
            cause = cause.cause
        }
        return false
    }

    fun isBadStreamPlayerResponseException(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is BadStreamPlayerResponseException) return true
            cause = cause.cause
        }
        return false
    }

    private fun describeClient(client: YouTubeClient): String = "${client.clientName}@${client.clientVersion}"
}




