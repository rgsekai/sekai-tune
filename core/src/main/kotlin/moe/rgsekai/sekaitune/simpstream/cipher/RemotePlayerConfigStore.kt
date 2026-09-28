package moe.rgsekai.sekaitune.simpstream.cipher

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rgsekai.sekaitune.simpstream.SimpStreamLog
import kotlin.time.Clock

internal class RemotePlayerConfigStore(
    private val httpClient: HttpClient,
    private val repository: PlayerConfigRepository,
) {
    companion object {
        private const val TAG = "RemotePlayerConfigStore"
        private const val MAX_CONFIG_RESPONSE_BYTES = 5 * 1024 * 1024
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val REFRESH_TTL_MS = 24 * 60 * 60 * 1000L
        private const val FAILURE_RETRY_MS = 5 * 60 * 1000L
        private const val FAILURE_REFRESH_COOLDOWN_MS = 10 * 1000L
        private const val OKHTTP_USER_AGENT = "okhttp/5.4.0"
        private const val LEGACY_FARADAY_CONFIG_URL = "https://raw.githubusercontent.com/MetrolistGroup/faraday/main/player_configs.json"
        private const val MAX_LEGACY_TIMESTAMP_CACHE_ENTRIES = 16
    }

    private val mutex = Mutex()
    private val legacyTimestampCache = mutableMapOf<String, CachedLegacyTimestamp>()

    @Volatile
    private var configs: Map<String, RemotePlayerConfigParser.HardcodedPlayerConfig> = emptyMap()

    @Volatile
    private var configSourceUrl: String? = null

    @Volatile
    var configEpoch: Long = 0L
        private set

    @Volatile
    private var lastRefreshAttemptAtMs: Long = 0L

    @Volatile
    private var lastRefreshAttemptSourceUrl: String? = null

    @Volatile
    private var lastFailureRefreshAtMs: Long = 0L

    @Volatile
    private var lastFailureRefreshSourceUrl: String? = null

    private data class CachedLegacyTimestamp(
        val value: Int?,
        val fetchedAtMs: Long,
    )

    suspend fun refreshIfStale(): Boolean =
        mutex.withLock {
            if (!repository.enabled) return@withLock false
            val sourceUrl = configuredUrl() ?: return@withLock false
            val now = Clock.System.now().toEpochMilliseconds()
            ensureLoadedFromCache(sourceUrl)
            if (hasFreshCache(sourceUrl, now)) return@withLock false
            if (hasRecentRefreshAttempt(sourceUrl, now)) return@withLock false
            lastRefreshAttemptAtMs = now
            lastRefreshAttemptSourceUrl = sourceUrl
            fetchAndApply(sourceUrl)
        }

    suspend fun forceRefresh(missingHash: String? = null): Boolean =
        mutex.withLock {
            if (!repository.enabled) return@withLock false
            val sourceUrl = configuredUrl() ?: return@withLock false
            val now = Clock.System.now().toEpochMilliseconds()
            if (missingHash != null && isKnownHash(missingHash, sourceUrl)) return@withLock false
            if (withinCooldown(lastFailureRefreshSourceUrl, lastFailureRefreshAtMs, sourceUrl, now)) {
                return@withLock false
            }
            lastFailureRefreshAtMs = now
            lastFailureRefreshSourceUrl = sourceUrl
            lastRefreshAttemptAtMs = now
            lastRefreshAttemptSourceUrl = sourceUrl
            fetchAndApply(sourceUrl)
        }

    suspend fun refreshAfterStreamRejection(): Boolean =
        mutex.withLock {
            if (!repository.enabled) return@withLock false
            val sourceUrl = configuredUrl() ?: return@withLock false
            val now = Clock.System.now().toEpochMilliseconds()
            if (withinCooldown(lastFailureRefreshSourceUrl, lastFailureRefreshAtMs, sourceUrl, now)) {
                return@withLock false
            }
            lastFailureRefreshAtMs = now
            lastFailureRefreshSourceUrl = sourceUrl
            lastRefreshAttemptAtMs = now
            lastRefreshAttemptSourceUrl = sourceUrl
            fetchAndApply(sourceUrl)
        }

    private suspend fun fetchAndApply(sourceUrl: String): Boolean {
        val parsedUrl = validatedSourceUrlOrNull(sourceUrl) ?: return false
        val etag = repository.cachedEtag.takeIf { it.isNotBlank() && repository.cachedSourceUrl == sourceUrl }
        val response =
            try {
                httpClient.getTextWithoutRedirects(parsedUrl, MAX_CONFIG_RESPONSE_BYTES) {
                    header(HttpHeaders.UserAgent, OKHTTP_USER_AGENT)
                    header(HttpHeaders.Accept, "application/json")
                    etag?.let { header(HttpHeaders.IfNoneMatch, it) }
                    timeout {
                        requestTimeoutMillis = REQUEST_TIMEOUT_MS
                        connectTimeoutMillis = REQUEST_TIMEOUT_MS
                        socketTimeoutMillis = REQUEST_TIMEOUT_MS
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SimpStreamLog.w(TAG, "Remote player config fetch failed: ${e.message}", e)
                ensureLoadedFromCache(sourceUrl)
                return false
            }

        val responseEtag = response.headers[HttpHeaders.ETag]?.trim()?.takeIf { it.isNotBlank() }
        if (response.status == HttpStatusCode.NotModified) {
            repository.cachedAtMs = Clock.System.now().toEpochMilliseconds()
            responseEtag?.let { repository.cachedEtag = it }
            ensureLoadedFromCache(sourceUrl)
            return false
        }

        val body = response.body ?: return false
        when (val result = RemotePlayerConfigParser.parse(body)) {
            is RemotePlayerConfigParser.ParseResult.Success -> {
                val changed = applyConfigs(sourceUrl, result.configs)
                repository.cachedJson = body
                repository.cachedAtMs = Clock.System.now().toEpochMilliseconds()
                repository.cachedSourceUrl = sourceUrl
                responseEtag?.let { repository.cachedEtag = it }
                SimpStreamLog.d(
                    TAG,
                    "Remote player configs applied (${result.configs.size} entries) changed=$changed epoch=$configEpoch",
                )
                return changed
            }

            is RemotePlayerConfigParser.ParseResult.Failure -> {
                SimpStreamLog.w(TAG, "Remote player configs rejected: ${result.reason}")
                ensureLoadedFromCache(sourceUrl)
                return false
            }
        }
    }

    suspend fun getSignatureTimestamp(playerUrl: String): Int? =
        getConfig(playerUrl)?.signatureTimestamp ?: getLegacySignatureTimestamp(playerUrl)

    internal suspend fun getConfig(playerUrl: String): RemotePlayerConfigParser.HardcodedPlayerConfig? {
        if (!repository.enabled) return null
        val hash = RemotePlayerConfigParser.extractPlayerHash(playerUrl) ?: return null
        val sourceUrl = configuredUrl() ?: return null
        refreshIfStale()
        return configs.takeIf { configSourceUrl == sourceUrl }?.get(hash)
    }

    private suspend fun isKnownHash(
        hash: String,
        sourceUrl: String,
    ): Boolean {
        ensureLoadedFromCache(sourceUrl)
        return configs.takeIf { configSourceUrl == sourceUrl }?.containsKey(hash) == true
    }

    private fun applyConfigs(
        sourceUrl: String,
        newConfigs: Map<String, RemotePlayerConfigParser.HardcodedPlayerConfig>,
    ): Boolean {
        val changed = configSourceUrl != sourceUrl || configs != newConfigs
        configs = newConfigs
        configSourceUrl = sourceUrl
        if (changed) configEpoch += 1L
        return changed
    }

    private fun hasFreshCache(
        sourceUrl: String,
        now: Long,
    ): Boolean =
        repository.cachedJson.isNotBlank() &&
            repository.cachedSourceUrl == sourceUrl &&
            now - repository.cachedAtMs < REFRESH_TTL_MS

    private fun hasRecentRefreshAttempt(
        sourceUrl: String,
        now: Long,
    ): Boolean =
        lastRefreshAttemptSourceUrl == sourceUrl &&
            now - lastRefreshAttemptAtMs in 0 until FAILURE_RETRY_MS

    private fun withinCooldown(
        previousSourceUrl: String?,
        previousAtMs: Long,
        sourceUrl: String,
        now: Long,
    ): Boolean =
        previousSourceUrl == sourceUrl &&
            (now <= previousAtMs || now - previousAtMs < FAILURE_REFRESH_COOLDOWN_MS)

    private fun ensureLoadedFromCache(sourceUrl: String) {
        if (configSourceUrl == sourceUrl && configs.isNotEmpty()) return
        if (repository.cachedSourceUrl != sourceUrl) return
        val cached = repository.cachedJson
        if (cached.isBlank()) return
        when (val result = RemotePlayerConfigParser.parse(cached)) {
            is RemotePlayerConfigParser.ParseResult.Success -> {
                applyConfigs(sourceUrl, result.configs)
            }

            is RemotePlayerConfigParser.ParseResult.Failure -> {
                SimpStreamLog.w(TAG, "Cached remote player configs rejected: ${result.reason}")
            }
        }
    }

    private suspend fun getLegacySignatureTimestamp(playerUrl: String): Int? {
        if (!repository.enabled) return null
        val playerHash = RemotePlayerConfigParser.extractPlayerHash(playerUrl) ?: return null
        val url = legacyConfigUrl(playerHash) ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        legacyTimestampCache
            .remove(url)
            ?.takeIf { now - it.fetchedAtMs < REFRESH_TTL_MS }
            ?.let { cached ->
                legacyTimestampCache[url] = cached
                return cached.value
            }

        val value =
            try {
                val response =
                    httpClient.getTextWithoutRedirects(Url(url), MAX_CONFIG_RESPONSE_BYTES) {
                        header(HttpHeaders.UserAgent, OKHTTP_USER_AGENT)
                        header(HttpHeaders.Accept, "application/json")
                        timeout {
                            requestTimeoutMillis = REQUEST_TIMEOUT_MS
                            connectTimeoutMillis = REQUEST_TIMEOUT_MS
                            socketTimeoutMillis = REQUEST_TIMEOUT_MS
                        }
                    }
                response.body?.let { body -> RemotePlayerConfigParser.parseLegacySignatureTimestamp(body, playerHash) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SimpStreamLog.d(TAG, "Legacy player config timestamp unavailable hash=$playerHash error=${e.message}")
                null
            }

        legacyTimestampCache[url] = CachedLegacyTimestamp(value, now)
        while (legacyTimestampCache.size > MAX_LEGACY_TIMESTAMP_CACHE_ENTRIES) {
            legacyTimestampCache.remove(legacyTimestampCache.keys.firstOrNull() ?: break)
        }
        return value
    }

    private fun legacyConfigUrl(playerHash: String): String? {
        val configured = repository.sourceUrl.trim()
        if (
            configured.isBlank() ||
            configured == LEGACY_FARADAY_CONFIG_URL ||
            configured.substringBefore('?').endsWith("/player_configs.json")
        ) {
            return null
        }
        return configured
            .replace("{playerHash}", playerHash)
            .replace("{playerTag}", "player-$playerHash")
            .takeIf { validatedSourceUrlOrNull(it) != null }
    }

    private fun configuredUrl(): String? =
        repository.sourceUrl
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { configured ->
                when {
                    configured == LEGACY_FARADAY_CONFIG_URL -> repository.defaultSourceUrl
                    configured.substringBefore('?').endsWith("/player_configs.json") -> configured
                    else -> null
                }
            }?.takeIf { validatedSourceUrlOrNull(it) != null }

    internal fun validatedSourceUrlOrNull(value: String): Url? =
        runCatching { Url(value) }
            .getOrNull()
            ?.takeIf { url ->
                url.protocol.name == "https" &&
                    when (url.host) {
                        "raw.githubusercontent.com" ->
                            url.encodedPath.startsWith("/maxrave-dev/simpmusic-files/") ||
                                url.encodedPath.startsWith("/MetrolistGroup/faraday/")
                        "cdn.jsdelivr.net" -> url.encodedPath.startsWith("/gh/MetrolistGroup/faraday@")
                        "github.com" -> url.encodedPath.startsWith("/MetrolistGroup/faraday/releases/download/")
                        else -> false
                    }
            }
}
