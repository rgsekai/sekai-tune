/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.addon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Manifest payload returned by GET /manifest.json
 */
@Serializable
data class AddonManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String? = null,
    val resources: List<String> = emptyList(),
    val allowDownloads: JsonElement? = null,
    val checkValidLossless: JsonElement? = null,
    val settings: List<AddonSettingDto> = emptyList(),
) {
    val downloadsAllowed: Boolean
        get() = allowDownloads.asBoolean(default = true) ?: true

    val requiresLosslessOutput: Boolean
        get() = checkValidLossless.asBoolean(default = false) ?: false

    val isPlayable: Boolean
        get() = resources.isEmpty() || resources.any { it.equals("search", ignoreCase = true) }
}

@Serializable
data class AddonSettingDto(
    val key: String = "",
    val default: JsonElement? = null,
    val options: List<AddonSettingOptionDto> = emptyList(),
) {
    val defaultValue: String?
        get() = (default as? JsonPrimitive)?.content?.ifBlank { null }
}

@Serializable
data class AddonSettingOptionDto(
    val value: JsonElement? = null,
    val label: String? = null,
) {
    val valueString: String?
        get() = (value as? JsonPrimitive)?.content?.ifBlank { null }
}

/**
 * Track payload within search results.
 */
@Serializable
data class AddonTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String? = null,
    val artists: List<String>? = null,
    val album: JsonElement? = null,
    val duration: Double? = null,
    val durationSec: Int? = null,
    val format: String? = null,
    val audioQuality: String? = null,
    val audioMode: String? = null,
    val streamUrl: String? = null,
    @SerialName("streamURL")
    val streamURLAlt: String? = null,
    val artworkURL: JsonElement? = null,
    val albumArtworkURL: JsonElement? = null,
    @SerialName("artwork_url")
    val artworkUrlSnake: JsonElement? = null,
    @SerialName("album_artwork_url")
    val albumArtworkUrlSnake: JsonElement? = null,
    val artwork: JsonElement? = null,
    val thumbnail: JsonElement? = null,
    val thumbnailUrl: JsonElement? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrlSnake: JsonElement? = null,
    val cover: JsonElement? = null,
    val coverUrl: JsonElement? = null,
    @SerialName("cover_url")
    val coverUrlSnake: JsonElement? = null,
    val image: JsonElement? = null,
    val picture: JsonElement? = null,
) {
    /**
     * Resolves artist list from either [artists] array or [artist] string.
     */
    fun resolvedArtists(): List<String> {
        if (!artists.isNullOrEmpty()) return artists
        if (!artist.isNullOrBlank()) {
            return artist.split(",", ";", "&", "/", " feat. ", " ft. ", " with ")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        return emptyList()
    }

    /**
     * Resolves album title from string or nested object.
     */
    val resolvedAlbum: String?
        get() {
            if (album == null || album is JsonNull) return null
            if (album is JsonPrimitive) return album.content.takeIf { it.isNotBlank() }
            if (album is JsonObject) {
                val titleElem = album["title"] ?: album["name"]
                if (titleElem is JsonPrimitive && titleElem.content.isNotBlank()) {
                    return titleElem.content
                }
            }
            return null
        }

    /**
     * Resolves track duration in seconds.
     */
    fun resolvedDurationSec(): Int? {
        durationSec?.let { return it }
        duration?.let {
            return if (it > 10000) (it / 1000).toInt() else it.toInt()
        }
        return null
    }

    /**
     * Resolves stream URL if provided in search.
     */
    fun resolvedStreamUrl(): String? = streamUrl ?: streamURLAlt

    /**
     * Resolves artwork URL across diverse addon response schemas.
     */
    fun resolvedArtworkUrl(): String? {
        val candidates = listOf(
            artworkURL,
            albumArtworkURL,
            artworkUrlSnake,
            albumArtworkUrlSnake,
            artwork,
            thumbnailUrl,
            thumbnailUrlSnake,
            thumbnail,
            cover,
            coverUrl,
            coverUrlSnake,
            image,
            picture,
        )
        for (candidate in candidates) {
            val url = extractImageUrl(candidate)
            if (!url.isNullOrBlank()) return url
        }
        if (album is JsonObject) {
            val nested = extractImageUrl(album["image"])
                ?: extractImageUrl(album["cover"])
                ?: extractImageUrl(album["artwork"])
                ?: extractImageUrl(album["thumbnail"])
            if (!nested.isNullOrBlank()) return nested
        }
        return null
    }

    private fun extractImageUrl(element: JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        if (element is JsonPrimitive) {
            return element.content.takeIf { it.isNotBlank() }
        }
        if (element is JsonObject) {
            val preferredKeys = listOf("large", "extralarge", "url", "medium", "small", "default", "uri", "src")
            for (key in preferredKeys) {
                val child = element[key]
                if (child is JsonPrimitive && child.content.isNotBlank()) {
                    return child.content
                }
            }
        }
        return null
    }
}

/**
 * Container for search results if wrapped in an object.
 */
@Serializable
data class AddonSearchContainer(
    val tracks: List<AddonTrackDto>? = null,
    val results: List<AddonTrackDto>? = null,
    val data: List<AddonTrackDto>? = null,
    val items: List<AddonTrackDto>? = null,
) {
    fun allTracks(): List<AddonTrackDto> =
        tracks ?: results ?: data ?: items ?: emptyList()
}

/**
 * Stream payload returned by GET /stream/{id}
 */
@Serializable
data class AddonStreamDto(
    val url: String? = null,
    @SerialName("streamURL")
    val streamURLAlt: String? = null,
    val format: String? = null,
    val codec: String? = null,
    val quality: String? = null,
    val audioQuality: String? = null,
    val audioMode: String? = null,
    val bitrate: JsonElement? = null,
    val sampleRate: JsonElement? = null,
    val bitDepth: JsonElement? = null,
    val headers: Map<String, String>? = null,
    val error: String? = null,
) {
    fun resolvedUrl(): String? = url ?: streamURLAlt

    fun resolvedBitrate(): Int? {
        val num = bitrate?.asInt()
        if (num != null && num > 0) {
            return if (num > 3000) num / 1000 else num
        }
        quality?.let { text ->
            val match = Regex("""(\d{2,4})\s*kbps""", RegexOption.IGNORE_CASE).find(text)
            if (match != null) return match.groupValues[1].toIntOrNull()
        }
        return null
    }

    fun resolvedSampleRateHz(): Int? {
        val num = sampleRate?.asInt()
        if (num != null && num > 0) {
            return if (num < 1000) (num * 1000).toInt() else num.toInt()
        }
        quality?.let { text ->
            val match = Regex("""([\d.]+)\s*kHz""", RegexOption.IGNORE_CASE).find(text)
            if (match != null) {
                val khz = match.groupValues[1].toDoubleOrNull()
                if (khz != null) return (khz * 1000).toInt()
            }
        }
        return null
    }

    fun resolvedBitDepth(): Int? {
        val num = bitDepth?.asInt()
        if (num != null && num in 8..32) return num
        quality?.let { text ->
            val match = Regex("""(\d{1,2})\s*-?\s*bit""", RegexOption.IGNORE_CASE).find(text)
            if (match != null) return match.groupValues[1].toIntOrNull()
        }
        return null
    }
}

/**
 * Typed response from the addon client avoiding any unhandled exceptions.
 */
sealed interface AddonResponse<out T> {
    data class Success<T>(val value: T) : AddonResponse<T>
    data class HttpError(val statusCode: Int, val message: String) : AddonResponse<Nothing>
    data class RateLimited(val retryAfterSeconds: Long?) : AddonResponse<Nothing>
    data class SerializationError(val rawBody: String?, val cause: Throwable) : AddonResponse<Nothing>
    data class NetworkError(val cause: Throwable) : AddonResponse<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is HttpError -> throw IllegalStateException("HTTP $statusCode: $message")
        is RateLimited -> throw IllegalStateException("Rate limited (retry after ${retryAfterSeconds}s)")
        is SerializationError -> throw IllegalStateException("Failed to parse JSON: ${cause.message}")
        is NetworkError -> throw cause
    }
}

/**
 * High-level classification of an addon query outcome.
 */
enum class AddonOutcomeKind {
    MATCH,
    NO_MATCH,
    REAL_FAILURE,
}

/**
 * Thrown when an addon explicitly has no matching track or stream.
 * This should never be logged as an error or warning in telemetry/logs.
 */
class AddonNoMatchException(
    val statusCode: Int? = null,
    override val message: String = "No match found at source",
) : RuntimeException(message)

/**
 * Classifies responses from addon HTTP endpoints to distinguish between benign NO_MATCH
 * (404, empty list, or 400/403 with unavailable/not found body) and REAL_FAILURE
 * (timeouts, network drops, 5xx, 429, 401, 402 payment required).
 */
object AddonResponseClassifier {
    /**
     * Determines whether an HTTP status code and response body represent a NO_MATCH outcome
     * (track not found, unavailable, empty results, region restriction) rather than an infrastructure or app failure.
     * Specifically, HTTP 404 is always NO_MATCH, and HTTP 400/403 with bodies indicating
     * "unavailable / not found / no results" (such as Tido 403 `{"error": "Unavailable...}`) are NO_MATCH.
     */
    fun isNoMatchHttp(statusCode: Int, bodyOrMessage: String?): Boolean {
        if (statusCode == 404) return true
        if (statusCode == 400 || statusCode == 403) {
            if (bodyOrMessage.isNullOrBlank()) return true
            val lower = bodyOrMessage.lowercase().trim()
            return lower.contains("unavailab") ||
                lower.contains("not found") ||
                lower.contains("no result") ||
                lower.contains("no match") ||
                lower.contains("not available") ||
                lower.contains("does not exist") ||
                lower.contains("not_found") ||
                lower.contains("empty") ||
                lower.contains("none found") ||
                lower.contains("track not available") ||
                lower.contains("item not found") ||
                lower.contains("song not found")
        }
        return false
    }

    /**
     * Classifies a typed [AddonResponse] as MATCH, NO_MATCH, or REAL_FAILURE.
     */
    fun <T> classify(response: AddonResponse<T>, isEmpty: Boolean = false): AddonOutcomeKind {
        return when (response) {
            is AddonResponse.Success -> {
                if (isEmpty) AddonOutcomeKind.NO_MATCH else AddonOutcomeKind.MATCH
            }
            is AddonResponse.HttpError -> {
                if (isNoMatchHttp(response.statusCode, response.message)) {
                    AddonOutcomeKind.NO_MATCH
                } else {
                    AddonOutcomeKind.REAL_FAILURE
                }
            }
            is AddonResponse.RateLimited,
            is AddonResponse.SerializationError,
            is AddonResponse.NetworkError -> AddonOutcomeKind.REAL_FAILURE
        }
    }

    /**
     * Formats a short, human-readable reason for real failures (with tokens redacted).
     * Real failures include timeout, no network, 5xx, 429, 401, 402 "Payment required" (from server), etc.
     */
    fun formatFailureReason(response: AddonResponse<*>): String {
        return when (response) {
            is AddonResponse.HttpError -> {
                when (response.statusCode) {
                    401 -> "Authentication required (HTTP 401)"
                    402 -> "Payment required (HTTP 402)"
                    429 -> "Rate limit exceeded (HTTP 429)"
                    in 500..599 -> "Server error (HTTP ${response.statusCode})"
                    else -> {
                        val cleaned = AddonClient.redactTokens(response.message).take(80)
                        if (cleaned.isNotBlank() && !cleaned.startsWith("HTTP")) {
                            "HTTP ${response.statusCode}: $cleaned"
                        } else {
                            "HTTP ${response.statusCode}"
                        }
                    }
                }
            }
            is AddonResponse.RateLimited -> {
                val sec = response.retryAfterSeconds
                if (sec != null && sec > 0) "Rate limited (retry in ${sec}s)" else "Rate limited"
            }
            is AddonResponse.NetworkError -> {
                val cause = response.cause
                when {
                    cause is java.net.SocketTimeoutException || cause is java.io.InterruptedIOException -> "Request timed out"
                    cause is java.net.UnknownHostException -> "No network connection"
                    cause is java.net.ConnectException -> "Connection failed"
                    else -> "Network error"
                }
            }
            is AddonResponse.SerializationError -> "Invalid response format"
            is AddonResponse.Success -> ""
        }
    }

    fun formatFailureReason(throwable: Throwable): String {
        val msg = throwable.message.orEmpty()
        return when {
            throwable is java.net.SocketTimeoutException || throwable is java.io.InterruptedIOException -> "Request timed out"
            throwable is java.net.UnknownHostException -> "No network connection"
            throwable is java.net.ConnectException -> "Connection failed"
            msg.contains("401") -> "Authentication required (HTTP 401)"
            msg.contains("402") -> "Payment required (HTTP 402)"
            msg.contains("429") -> "Rate limit exceeded (HTTP 429)"
            msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") -> "Server error"
            else -> AddonClient.redactTokens(msg.take(80)).ifBlank { "Connection failed" }
        }
    }
}

/**
 * Safely parses a [JsonElement] as a boolean flag.
 * Supports boolean literals, 0/1 integers, "true"/"false", "yes"/"no", "on"/"off".
 */
fun JsonElement?.asBoolean(default: Boolean? = null): Boolean? {
    if (this == null || this is JsonNull) return default
    val primitive = this as? JsonPrimitive ?: return default
    primitive.booleanOrNull?.let { return it }
    return when (primitive.content.trim().lowercase()) {
        "1", "true", "yes", "on" -> true
        "0", "false", "no", "off" -> false
        else -> primitive.content.toDoubleOrNull()?.let { it != 0.0 } ?: default
    }
}

/**
 * Safely parses a [JsonElement] as an integer number.
 */
fun JsonElement?.asInt(): Int? {
    if (this == null || this is JsonNull) return null
    val primitive = this as? JsonPrimitive ?: return null
    primitive.intOrNull?.let { return it }
    return primitive.content.toDoubleOrNull()?.toInt()
}
