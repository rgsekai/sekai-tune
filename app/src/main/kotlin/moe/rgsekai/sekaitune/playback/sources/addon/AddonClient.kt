package moe.rgsekai.sekaitune.playback.sources.addon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for interacting with external HTTP REST addon servers.
 * Preserves path-based authentication tokens, negotiates parameters from
 * manifest settings, enforces backoff for HTTP 429, and captures error snippets.
 */
class AddonClient(
    val baseUrl: String,
    private val httpClient: OkHttpClient = defaultHttpClient,
    manifest: AddonManifest? = null,
) {
    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 20L
        private const val CALL_TIMEOUT_SECONDS = 25L
        private const val MAX_RETRIES = 3
        private const val MAX_RETRY_DELAY_MS = 10_000L

        val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            encodeDefaults = true
        }

        /**
         * Normalizes a user-entered URL into an addon base URL.
         * Prepends https:// if scheme is missing, strips any trailing `.json` filename
         * (e.g. `/manifest.json`, `/addon.json`) or trailing slashes so path-based auth
         * (e.g. `https://host/USER_TOKEN`) is preserved cleanly.
         */
        fun normalizeBaseUrl(url: String): String {
            var trimmed = url.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                trimmed = "https://$trimmed"
            }
            val queryIndex = trimmed.indexOfAny(charArrayOf('?', '#'))
            val withoutQuery = if (queryIndex >= 0) trimmed.substring(0, queryIndex) else trimmed
            val trimmedPath = withoutQuery.trimEnd('/')

            val lastSegment = trimmedPath.substringAfterLast('/', "")
            return if (lastSegment.endsWith(".json", ignoreCase = true) && trimmedPath.contains("://")) {
                trimmedPath.dropLast(lastSegment.length).trimEnd('/')
            } else {
                trimmedPath
            }
        }

        /**
         * Redacts path tokens from an addon URL for safe logging.
         */
        fun redact(url: String): String {
            val parsed = url.toHttpUrlOrNull() ?: return "***"
            return "${parsed.scheme}://${parsed.host}/***"
        }

        /**
         * Redacts potential sensitive token patterns from text snippets.
         */
        fun redactTokens(text: String): String {
            return text.replace(Regex("(?i)(token|key|secret)[=:]\\s*[\"']?([a-zA-Z0-9_-]{8,})[\"']?")) {
                "${it.groupValues[1]}=***"
            }
        }
    }

    val normalizedBaseUrl: String = normalizeBaseUrl(baseUrl)

    @Volatile
    var cachedManifest: AddonManifest? = manifest

    /**
     * Builds an endpoint URL keeping the full base path intact without dropped segments.
     */
    private fun buildEndpointUrl(
        segments: List<String>,
        queryParams: Map<String, String> = emptyMap(),
    ): HttpUrl? {
        val base = normalizedBaseUrl.toHttpUrlOrNull() ?: return null
        val builder = base.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        queryParams.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }

    /**
     * Fetches the addon manifest from GET /manifest.json
     */
    suspend fun fetchManifest(): AddonResponse<AddonManifest> = withContext(Dispatchers.IO) {
        val httpUrl = buildEndpointUrl(listOf("manifest.json"))
            ?: return@withContext AddonResponse.HttpError(400, "Invalid addon manifest URL")

        val response = executeWithBackoff(httpUrl.toString()) { bodyString ->
            json.decodeFromString<AddonManifest>(bodyString)
        }
        if (response is AddonResponse.Success) {
            cachedManifest = response.value
        }
        response
    }

    /**
     * Resolves query parameters negotiating settings from the manifest.
     */
    private fun resolveParameters(wantedQuality: String? = "LOSSLESS"): Map<String, String> {
        val manifest = cachedManifest

        val params = LinkedHashMap<String, String>()
        val declaredSettings = manifest?.settings.orEmpty()

        // Pass declared defaults for settings (e.g. format, region, etc.)
        declaredSettings.forEach { setting ->
            val key = setting.key.takeIf { it.isNotBlank() } ?: return@forEach
            setting.defaultValue?.let { params[key] = it }
        }

        // Negotiate quality parameter
        val qualitySetting = declaredSettings.firstOrNull { it.key.equals("quality", ignoreCase = true) }
        if (qualitySetting != null) {
            val options = qualitySetting.options.mapNotNull { it.valueString }
            val matched = matchQualityOption(options) ?: qualitySetting.defaultValue ?: "lossless"
            params["quality"] = matched
        } else if (!wantedQuality.isNullOrBlank()) {
            params["quality"] = wantedQuality
        }

        return params
    }

    private fun matchQualityOption(options: List<String>): String? {
        if (options.isEmpty()) return null
        val preferredKeywords = listOf("lossless", "flac", "hi-res", "hires", "hifi", "max", "high", "320")
        for (keyword in preferredKeywords) {
            val found = options.firstOrNull { it.contains(keyword, ignoreCase = true) }
            if (found != null) return found
        }
        return options.first()
    }

    /**
     * Executes a track search via GET /search?q={query}&quality={quality}
     */
    suspend fun search(
        query: String,
        quality: String? = null,
    ): AddonResponse<List<AddonTrackDto>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext AddonResponse.Success(emptyList())

        val params = resolveParameters(wantedQuality = quality ?: "LOSSLESS")
        val queryParams = params + ("q" to trimmed)

        val httpUrl = buildEndpointUrl(listOf("search"), queryParams)
            ?: return@withContext AddonResponse.HttpError(400, "Invalid search URL")

        executeWithBackoff(httpUrl.toString()) { bodyString ->
            try {
                val container = json.decodeFromString<AddonSearchContainer>(bodyString)
                container.allTracks()
            } catch (_: Exception) {
                json.decodeFromString<List<AddonTrackDto>>(bodyString)
            }
        }
    }

    /**
     * Resolves stream URL for a track via GET /stream/{id}?quality={quality}
     */
    suspend fun stream(
        trackId: String,
        quality: String? = null,
    ): AddonResponse<AddonStreamDto> = withContext(Dispatchers.IO) {
        val params = resolveParameters(wantedQuality = quality ?: "LOSSLESS")
        val httpUrl = buildEndpointUrl(listOf("stream", trackId), params)
            ?: return@withContext AddonResponse.HttpError(400, "Invalid stream URL")

        executeWithBackoff(httpUrl.toString()) { bodyString ->
            json.decodeFromString<AddonStreamDto>(bodyString)
        }
    }

    /**
     * Executes a request with backoff for HTTP 429 and parses the body with [parser].
     */
    private suspend fun <T> executeWithBackoff(
        url: String,
        parser: (String) -> T,
    ): AddonResponse<T> {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            attempt++
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SekaiTune/3.0")
                .header("Accept", "application/json")
                .build()

            val response: Response
            try {
                response = httpClient.newCall(request).execute()
            } catch (e: IOException) {
                Timber.w(e, "AddonClient request failed on attempt $attempt: %s", redact(url))
                if (attempt >= MAX_RETRIES) {
                    return AddonResponse.NetworkError(e)
                }
                delay(calculateExponentialDelay(attempt))
                continue
            }

            response.use { resp ->
                val code = resp.code
                if (code == 429) {
                    val retryAfterHeader = resp.header("Retry-After")
                    val retrySeconds = retryAfterHeader?.toLongOrNull()
                    val delayMs = if (retrySeconds != null && retrySeconds > 0) {
                        (retrySeconds * 1000L).coerceAtMost(MAX_RETRY_DELAY_MS)
                    } else {
                        calculateExponentialDelay(attempt)
                    }

                    if (attempt < MAX_RETRIES) {
                        Timber.d("Received 429 from %s, backing off for %dms (attempt %d)", redact(url), delayMs, attempt)
                        delay(delayMs)
                        return@use
                    } else {
                        return AddonResponse.RateLimited(retrySeconds)
                    }
                }

                if (!resp.isSuccessful) {
                    val rawBody = try {
                        resp.body?.string()?.take(200)?.let { redactTokens(it) }?.trim() ?: ""
                    } catch (_: Exception) { "" }

                    val errorDetail = if (rawBody.isNotBlank()) rawBody else resp.message.ifBlank { "HTTP $code" }
                    if (AddonResponseClassifier.isNoMatchHttp(code, errorDetail)) {
                        Timber.d("Addon HTTP %d (no match) from %s: %s", code, redact(url), errorDetail)
                    } else {
                        Timber.w("Addon HTTP %d from %s: %s", code, redact(url), errorDetail)
                    }
                    return AddonResponse.HttpError(code, errorDetail)
                }

                val bodyString = try {
                    resp.body?.string() ?: ""
                } catch (e: Exception) {
                    return AddonResponse.NetworkError(e)
                }

                return try {
                    AddonResponse.Success(parser(bodyString))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse JSON response from %s", redact(url))
                    AddonResponse.SerializationError(bodyString, e)
                }
            }
        }

        return AddonResponse.HttpError(500, "Max retries reached")
    }

    private fun calculateExponentialDelay(attempt: Int): Long {
        val baseDelay = 1000L * (1 shl (attempt - 1))
        return baseDelay.coerceAtMost(MAX_RETRY_DELAY_MS)
    }
}
