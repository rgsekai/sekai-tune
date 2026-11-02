package moe.rgsekai.sekaitune.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.awt.image.BufferedImage
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A handwritten signature image for an artist, served by a user-configured artist-signature API
 * (https://github.com/itsmeadarsh2008/artist-signature). Android's `ArtistSignature`, ported.
 *
 * [aspect] is width over height, for sizing without decoding; [format] is the lowercased asset
 * format (`svg`, `png`, …), or "" when unknown.
 */
internal data class DesktopArtistSignature(
    val imageUrl: String,
    val aspect: Float,
    val format: String = "",
)

/**
 * Whether the signature API is on and where it lives.
 *
 * Keys and defaults are Android's, from `AppSettings`.
 */
internal object DesktopSignatureSettings {

    private val persistence = DesktopPersistence()

    private val _enabled = MutableStateFlow(persistence.boolean(KEY_ENABLED, false))
    val defaultUrl: String = DesktopArtistSignatures.normalizeBaseUrl(
        System.getProperty("Sekai Tune.signature.url").orEmpty(),
    )

    private val _url = MutableStateFlow(
        DesktopArtistSignatures.normalizeBaseUrl(persistence.string(KEY_URL, "")).ifBlank { defaultUrl },
    )

    val enabled: StateFlow<Boolean> = _enabled
    val url: StateFlow<String> = _url

    fun setEnabled(value: Boolean) {
        persistence.saveBoolean(KEY_ENABLED, value)
        _enabled.value = value
    }

    fun setUrl(raw: String) {
        val normalized = DesktopArtistSignatures.normalizeBaseUrl(raw)
        persistence.saveString(KEY_URL, normalized)
        _url.value = normalized.ifBlank { defaultUrl }
    }

    private const val KEY_ENABLED = "signature_api_enabled"
    private const val KEY_URL = "signature_api_url"
}

/**
 * Name-keyed lookup against the configured base URL. Never throws; null means "nothing known",
 * which a caller deciding what to draw reads the same whether it never looked or looked and found
 * nothing.
 */
internal object DesktopArtistSignatures {

    private const val CACHE_SIZE = 64
    const val MAX_BASE_URL_LENGTH = 500

    private class Entry(val signature: DesktopArtistSignature?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > CACHE_SIZE
    }

    private val _updates = MutableStateFlow(0)

    /** Ticks whenever a lookup settles or the cache is cleared, so a header re-reads [cached]. */
    val updates: StateFlow<Int> = _updates

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Trimmed base without trailing slash, or "" when blank. */
    fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/').take(MAX_BASE_URL_LENGTH)

    /** Only http(s) URLs are ever requested. Blank never is. */
    fun isValidBaseUrl(baseUrl: String): Boolean {
        val base = normalizeBaseUrl(baseUrl)
        return base.startsWith("http://") || base.startsWith("https://")
    }

    /** Effective base, or null when the provider is off or unconfigured. */
    fun effectiveBaseUrl(enabled: Boolean, baseUrl: String): String? {
        if (!enabled) return null
        return normalizeBaseUrl(baseUrl).takeIf(::isValidBaseUrl)
    }

    fun cached(artistName: String, baseUrl: String?): DesktopArtistSignature? {
        if (baseUrl.isNullOrEmpty()) return null
        val key = cacheKey(artistName, baseUrl) ?: return null
        return synchronized(cache) { cache[key] }?.signature
    }

    /** The best signature for [artistName], or null. Never throws; runs its request on IO. */
    suspend fun signatureFor(artistName: String, baseUrl: String?): DesktopArtistSignature? {
        if (baseUrl.isNullOrEmpty()) return null
        val key = cacheKey(artistName, baseUrl) ?: return null
        synchronized(cache) { cache[key] }?.let { return it.signature }
        val entry = withContext(Dispatchers.IO) { runCatching { lookup(artistName, baseUrl) }.getOrNull() }
            ?: return null // A failed lookup is not cached: the next visit may well succeed.
        synchronized(cache) { cache[key] = entry }
        _updates.value++
        return entry.signature
    }

    /** Forgets everything, e.g. after the base URL changes. */
    fun clear() {
        synchronized(cache) { cache.clear() }
        _updates.value++
    }

    private fun lookup(artistName: String, baseUrl: String): Entry? {
        val base = normalizeBaseUrl(baseUrl)
        if (!isValidBaseUrl(base)) return Entry(null)
        val body = canvasGet("$base/v1/signatures?artist=${URLEncoder.encode(artistName, "UTF-8")}") ?: return null
        return Entry(parse(body))
    }

    /** `true` when `GET base/v1/health` answers 2xx. */
    suspend fun checkHealth(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(baseUrl)
        isValidBaseUrl(base) && canvasGet("$base/v1/health") != null
    }

    private fun cacheKey(artistName: String, baseUrl: String): String? {
        val name = artistName.foldedForMatch()
        if (name.isEmpty()) return null
        return "${normalizeBaseUrl(baseUrl)}\n$name"
    }

    /** Case, accents and punctuation don't identify an artist. */
    private fun String.foldedForMatch(): String =
        Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD).filter(Char::isLetterOrDigit)

    /**
     * Tolerant of both shapes the API has documented: `asset` as an object with `url` or as a plain
     * string, and legacy top-level `url` / `original_url`. A `matches[]` response (an ambiguous
     * name) parses as null.
     *
     * When several signatures exist the best pick wins rather than whatever was listed first, in
     * the order of the server's `best()`: redistributable licence, then SVG, then verification
     * state, then a panoramic bonus (wide images are usually a complete signature line; square
     * canvases are often fragments). Resolution only breaks ties.
     */
    internal fun parse(body: String): DesktopArtistSignature? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if ("signatures" !in root && "matches" in root) return null
        val signatures = root["signatures"] as? JsonArray ?: return null
        var best: DesktopArtistSignature? = null
        var bestScore = Int.MIN_VALUE
        var bestArea = -1L
        for (element in signatures) {
            val sig = element as? JsonObject ?: continue
            val url = urlOf(sig) ?: continue
            val score = rank(sig, url)
            val area = areaOf(sig)
            if (best == null || score > bestScore || (score == bestScore && area > bestArea)) {
                bestScore = score
                bestArea = area
                best = DesktopArtistSignature(url, aspectOf(sig), formatOf(sig, url))
            }
        }
        return best
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.number(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }

    private fun assetOf(sig: JsonObject): JsonObject? = sig["asset"] as? JsonObject

    private fun urlOf(sig: JsonObject): String? =
        listOf(assetOf(sig)?.text("url"), sig.text("asset"), sig.text("url"), sig.text("original_url"), sig.text("originalUrl"))
            .firstOrNull { it != null && it.startsWith("http") }

    private fun formatOf(sig: JsonObject, url: String): String =
        (assetOf(sig)?.text("format")?.takeIf(String::isNotBlank) ?: sig.text("format")?.takeIf(String::isNotBlank))
            ?.lowercase(Locale.ROOT)
            ?: url.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun widthOf(sig: JsonObject) = sig.number("width") ?: assetOf(sig)?.number("width")

    private fun heightOf(sig: JsonObject) = sig.number("height") ?: assetOf(sig)?.number("height")

    private fun aspectOf(sig: JsonObject): Float {
        val w = widthOf(sig)
        val h = heightOf(sig)
        return if (w != null && h != null) w.toFloat() / h else DEFAULT_ASPECT
    }

    private fun areaOf(sig: JsonObject): Long {
        val w = widthOf(sig) ?: return 0
        val h = heightOf(sig) ?: return 0
        return w.toLong() * h
    }

    private fun rank(sig: JsonObject, url: String): Int {
        val license = when ((sig["license"] as? JsonObject)?.text("status")?.lowercase(Locale.ROOT)) {
            "known" -> 2
            "requires_review" -> 1
            else -> 0
        }
        val format = when (formatOf(sig, url)) {
            "svg" -> 2
            "png" -> 1
            else -> 0
        }
        val verification = when (sig.text("verification")?.lowercase(Locale.ROOT)) {
            "artist_verified" -> 3
            "verified" -> 2
            "unverified" -> 1
            else -> 0
        }
        val panoramic = if (aspectOf(sig) >= 1.4f) 1 else 0
        return license * 1000 + format * 100 + verification * 10 + panoramic
    }

    private const val DEFAULT_ASPECT = 2.5f
}

/**
 * Turns a scanned signature (dark ink on white paper) into white strokes on transparency, so it
 * reads on the dark artist photo like the tinted SVGs. Android's `SignatureInkTransformation`.
 *
 * Maps each pixel's alpha to its darkness (white-to-alpha) and paints the survivors white. Refuses
 * images that don't read as scans: when fewer than [SCAN_WHITE_FRACTION] of pixels are near-white
 * the input comes back untouched, so a photograph of an autograph never becomes white blobs.
 */
internal fun BufferedImage.signatureInk(): BufferedImage {
    if (width <= 0 || height <= 0) return this
    val pixels = getRGB(0, 0, width, height, null, 0, width)
    val paper = pixels.count { inkLuminance(it) >= PAPER_LUMINANCE }
    if (paper.toFloat() / pixels.size < SCAN_WHITE_FRACTION) return this
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val inAlpha = (pixel ushr 24) / 255f
        if (inAlpha <= 0f) continue
        val darkness = 1f - inkLuminance(pixel)
        pixels[i] = if (darkness <= 0f) {
            0
        } else {
            ((darkness * inAlpha * 255f).roundToInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
        }
    }
    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
        it.setRGB(0, 0, width, height, pixels, 0, width)
    }
}

/** Luminance above this counts as paper rather than ink (0..1). */
private const val PAPER_LUMINANCE = 0.92f

/** Share of paper pixels required before treating the image as a scan. */
private const val SCAN_WHITE_FRACTION = 0.55f

private fun inkLuminance(pixel: Int): Float {
    val r = ((pixel ushr 16) and 0xFF) / 255f
    val g = ((pixel ushr 8) and 0xFF) / 255f
    val b = (pixel and 0xFF) / 255f
    return 0.299f * r + 0.587f * g + 0.114f * b
}
