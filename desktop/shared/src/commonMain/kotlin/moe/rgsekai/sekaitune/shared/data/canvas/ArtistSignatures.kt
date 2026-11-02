package moe.rgsekai.sekaitune.shared.data.canvas

import moe.rgsekai.sekaitune.shared.data.DebugLog as Log
import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.NetHttp
import moe.rgsekai.sekaitune.shared.platform.ioDispatcher
import moe.rgsekai.sekaitune.shared.platform.normalizeNfd
import io.ktor.http.encodeURLParameter
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

data class ArtistSignature(
    val imageUrl: String,
    val aspect: Float,
    /** Lowercased asset format (`svg`, `png`, ...); "" when unknown. */
    val format: String = "",
)

/**
 * Name-keyed lookup against a custom base URL, mirroring
 * [AppleArtistArtRepository]'s contract: in-memory LRU, never throws,
 * `null` for "nothing known" (never looked up and known-to-have-nothing
 * read the same to a caller deciding what to draw first).
 */
object ArtistSignatureRepository {

    private const val TAG = "ArtistSignature"
    private const val CACHE_SIZE = 64
    const val MAX_BASE_URL_LENGTH = 500

    private class Entry(val signature: ArtistSignature?)

    // Insertion-ordered; reads re-insert so it stays least-recently-used.
    private val cache = LinkedHashMap<String, Entry>()
    private val guard = SynchronizedObject()

    private fun lookupCached(key: String): Entry? =
        synchronized(guard) { cache.remove(key)?.also { cache[key] = it } }

    private val _updates = MutableStateFlow(0)

    /** Ticks whenever a lookup settles, so headers re-read [cached]. */
    val updates: StateFlow<Int> = _updates

    /** Trimmed base without trailing slash, or "" when blank. */
    fun normalizeBaseUrl(raw: String): String =
        raw.trim().trimEnd('/').take(MAX_BASE_URL_LENGTH)

    /** Only http(s) URLs are ever requested. Blank never is. */
    fun isValidBaseUrl(baseUrl: String): Boolean {
        val base = normalizeBaseUrl(baseUrl)
        if (base.isEmpty()) return false
        return base.startsWith("http://") || base.startsWith("https://")
    }

    /** Effective base, or null when the provider is off/unconfigured. */
    fun effectiveBaseUrl(enabled: Boolean, baseUrl: String): String? {
        if (!enabled) return null
        val base = normalizeBaseUrl(baseUrl)
        return base.takeIf { isValidBaseUrl(it) }
    }

    fun cached(artistName: String, baseUrl: String?): ArtistSignature? {
        if (baseUrl.isNullOrEmpty()) return null
        val key = cacheKey(artistName, baseUrl) ?: return null
        return lookupCached(key)?.signature
    }

    /** The best signature for [artistName], or null. Never throws. */
    suspend fun signatureFor(artistName: String, baseUrl: String?): ArtistSignature? {
        if (baseUrl.isNullOrEmpty()) return null
        val key = cacheKey(artistName, baseUrl) ?: return null
        lookupCached(key)?.let {
            Log.d(TAG, "cache hit for $artistName: ${it.signature?.imageUrl}")
            return it.signature
        }

        val entry = withContext(ioDispatcher) {
            runCatching { lookup(artistName, baseUrl) }
                .onFailure { Log.d(TAG, "lookup failed for $artistName: $it") }
                .getOrNull()
        } ?: return null
        Log.d(TAG, "settled for $artistName: ${entry.signature?.imageUrl}")
        synchronized(guard) {
            cache.remove(key)
            cache[key] = entry
            if (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
        }
        _updates.value++
        return entry.signature
    }

    /** Forgets everything, e.g. after the base URL changes. */
    fun clear() {
        synchronized(guard) { cache.clear() }
        _updates.value++
    }

    /** `true` when `GET base/v1/health` answers 2xx. Never throws. */
    suspend fun checkHealth(baseUrl: String): Boolean = withContext(ioDispatcher) {
        runCatching {
            val base = normalizeBaseUrl(baseUrl)
            if (!isValidBaseUrl(base)) return@runCatching false
            NetHttp.execute(HttpRequest("$base/v1/health")).isSuccessful
        }.getOrDefault(false)
    }

    private fun cacheKey(artistName: String, baseUrl: String): String? {
        val name = artistName.normalized()
        if (name.isEmpty()) return null
        return "${normalizeBaseUrl(baseUrl)}\n$name"
    }

    private fun lookup(artistName: String, baseUrl: String): Entry? {
        val base = normalizeBaseUrl(baseUrl)
        if (!isValidBaseUrl(base)) return Entry(null)
        val url = "$base/v1/signatures?artist=${artistName.encodeURLParameter()}"
        val body = get(url) ?: return null
        return Entry(parse(body))
    }

    /**
     * Tolerant of both shapes the API has documented: `asset` as an object
     * with `url` or as a plain string, and legacy top-level `url` /
     * `original_url`. A `matches[]` response (ambiguous name) parses as null.
     *
     * When several signatures exist the best pick wins rather than whatever
     * was listed first — same order as the server's `best()`: redistributable
     * license, then SVG format, then verification state, then a panoramic
     * bonus (wide images are usually complete signature lines; square canvases
     * are often fragments).
     */
    internal fun parse(body: String): ArtistSignature? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        if ("signatures" !in root && "matches" in root) return null
        val signatures = root["signatures"] as? JsonArray ?: return null
        var best: ArtistSignature? = null
        var bestScore = Int.MIN_VALUE
        var bestArea = -1L
        for (element in signatures) {
            val sig = element as? JsonObject ?: continue
            val url = extractUrl(sig) ?: continue
            val score = rank(sig, url)
            // Resolution only breaks ties: a larger raster of the same rank
            // upscales less on the header.
            val area = extractArea(sig)
            if (best == null || score > bestScore || (score == bestScore && area > bestArea)) {
                bestScore = score
                bestArea = area
                best = ArtistSignature(
                    imageUrl = url,
                    aspect = extractAspect(sig),
                    format = extractFormat(sig, url),
                )
            }
        }
        return best
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
            ?.toInt()?.takeIf { it > 0 }

    private fun extractFormat(sig: JsonObject, url: String): String {
        sig.obj("asset")?.str("format")?.lowercase()?.takeIf { it.isNotBlank() }?.let { return it }
        sig.str("format").lowercase().takeIf { it.isNotBlank() }?.let { return it }
        return url.substringBefore('?').substringAfterLast('.', "").lowercase()
    }

    private fun rank(sig: JsonObject, url: String): Int {
        val status = sig.obj("license")?.str("status").orEmpty().lowercase()
        val license = when (status) {
            "known" -> 2
            "requires_review" -> 1
            else -> 0
        }
        val formatScore = when (extractFormat(sig, url)) {
            "svg" -> 2
            "png" -> 1
            else -> 0
        }
        val verificationScore = when (sig.str("verification").lowercase()) {
            "artist_verified" -> 3
            "verified" -> 2
            "unverified" -> 1
            else -> 0
        }
        val panoramic = if (extractAspect(sig) >= 1.4f) 1 else 0
        return license * 1000 + formatScore * 100 + verificationScore * 10 + panoramic
    }

    private fun extractUrl(sig: JsonObject): String? {
        sig.obj("asset")?.str("url")?.takeIf { it.startsWith("http") }?.let { return it }
        // Some builds serve the mirror as a bare string asset.
        for (key in listOf("asset", "url", "original_url", "originalUrl")) {
            sig.str(key).takeIf { it.startsWith("http") }?.let { return it }
        }
        return null
    }

    private fun dims(sig: JsonObject): Pair<Int, Int>? {
        val asset = sig.obj("asset")
        val w = sig.int("width") ?: asset?.int("width") ?: return null
        val h = sig.int("height") ?: asset?.int("height") ?: return null
        return w to h
    }

    private fun extractAspect(sig: JsonObject): Float =
        dims(sig)?.let { (w, h) -> w.toFloat() / h } ?: 2.5f

    private fun extractArea(sig: JsonObject): Long =
        dims(sig)?.let { (w, h) -> w.toLong() * h } ?: 0

    private fun get(url: String): String? {
        val response = NetHttp.execute(HttpRequest(url))
        return if (response.isSuccessful) response.text() else null
    }

    /** Same folding as Apple's lookup: case, accents and punctuation don't identify. */
    private fun String.normalized(): String =
        normalizeNfd(lowercase()).filter { it.isLetterOrDigit() }
}
