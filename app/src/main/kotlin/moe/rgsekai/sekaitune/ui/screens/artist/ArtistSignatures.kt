/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.screens.artist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.time.Duration
import java.util.Locale

/**
 * A handwritten signature image for an artist, served by an artist-signature API
 * (see https://github.com/itsmeadarsh2008/artist-signature).
 *
 * [imageUrl] is the served mirror URL or upstream original URL.
 * [aspect] is width / height ratio for layout sizing without pre-decode.
 * [format] is lowercased asset format (e.g., "svg", "png").
 */
data class ArtistSignature(
    val imageUrl: String,
    val aspect: Float,
    val format: String = "",
)

object ArtistSignatureRepository {

    private const val TAG = "ArtistSignature"
    private const val CACHE_SIZE = 64
    const val MAX_BASE_URL_LENGTH = 500
    const val DEFAULT_BASE_URL = "https://artist-signature.vercel.app"

    private class Entry(val signature: ArtistSignature?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > CACHE_SIZE
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(10))
            .build()
    }

    private val _updates = MutableStateFlow(0)
    val updates: StateFlow<Int> = _updates

    fun normalizeBaseUrl(raw: String): String =
        raw.trim().trimEnd('/').take(MAX_BASE_URL_LENGTH)

    fun isValidBaseUrl(baseUrl: String): Boolean {
        val base = normalizeBaseUrl(baseUrl)
        if (base.isEmpty()) return false
        return base.startsWith("http://") || base.startsWith("https://")
    }

    fun effectiveBaseUrl(enabled: Boolean, baseUrl: String): String? {
        if (!enabled) return null
        val effective = if (baseUrl.isBlank()) DEFAULT_BASE_URL else baseUrl
        val base = normalizeBaseUrl(effective)
        return base.takeIf { isValidBaseUrl(it) }
    }

    fun cached(artistName: String, baseUrl: String?): ArtistSignature? {
        if (baseUrl.isNullOrEmpty()) return null
        val key = cacheKey(artistName, baseUrl) ?: return null
        return synchronized(cache) { cache[key] }?.signature
    }

    suspend fun signatureFor(artistName: String, baseUrl: String?): ArtistSignature? {
        if (baseUrl.isNullOrEmpty()) return null
        val key = cacheKey(artistName, baseUrl) ?: return null
        synchronized(cache) { cache[key] }?.let {
            return it.signature
        }

        val entry = withContext(Dispatchers.IO) {
            runCatching { lookup(artistName, baseUrl) }
                .onFailure { Timber.tag(TAG).d(it, "lookup failed for %s", artistName) }
                .getOrNull()
        } ?: return null

        synchronized(cache) { cache[key] = entry }
        _updates.value++
        return entry.signature
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
        _updates.value++
    }

    suspend fun checkHealth(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeBaseUrl(baseUrl)
            if (!isValidBaseUrl(base)) return@runCatching false
            val request = Request.Builder().url("$base/v1/health").get().build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
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
        val url = "$base/v1/signatures?artist=${URLEncoder.encode(artistName, "UTF-8")}"
        val body = get(url) ?: return null
        return Entry(parse(body))
    }

    internal fun parse(body: String): ArtistSignature? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (!root.has("signatures") && root.has("matches")) return null
        val signatures = root.optJSONArray("signatures") ?: return null
        var best: ArtistSignature? = null
        var bestScore = Int.MIN_VALUE
        var bestArea = -1L
        for (i in 0 until signatures.length()) {
            val sig = signatures.optJSONObject(i) ?: continue
            val url = extractUrl(sig) ?: continue
            val score = rank(sig, url)
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

    private fun extractFormat(sig: JSONObject, url: String): String {
        sig.optJSONObject("asset")?.optString("format")?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }?.let { return it }
        sig.optString("format").lowercase(Locale.ROOT)
            .takeIf { it.isNotBlank() }?.let { return it }
        return url.substringBefore('?').substringAfterLast('.', "").lowercase(Locale.ROOT)
    }

    private fun rank(sig: JSONObject, url: String): Int {
        val status = sig.optJSONObject("license")?.optString("status").orEmpty().lowercase(Locale.ROOT)
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
        val verification = sig.optString("verification").lowercase(Locale.ROOT)
        val verificationScore = when (verification) {
            "artist_verified" -> 3
            "verified" -> 2
            "unverified" -> 1
            else -> 0
        }
        val panoramic = if (extractAspect(sig) >= 1.4f) 1 else 0
        return license * 1000 + formatScore * 100 + verificationScore * 10 + panoramic
    }

    private fun extractUrl(sig: JSONObject): String? {
        sig.optJSONObject("asset")?.optString("url")?.takeIf { it.startsWith("http") }?.let { return it }
        sig.optString("asset").takeIf { it.startsWith("http") }?.let { return it }
        sig.optString("url").takeIf { it.startsWith("http") }?.let { return it }
        sig.optString("original_url").takeIf { it.startsWith("http") }?.let { return it }
        sig.optString("originalUrl").takeIf { it.startsWith("http") }?.let { return it }
        return null
    }

    private fun extractAspect(sig: JSONObject): Float {
        val asset = sig.optJSONObject("asset")
        val w = sig.optInt("width").takeIf { it > 0 }
            ?: asset?.optInt("width")?.takeIf { it > 0 }
        val h = sig.optInt("height").takeIf { it > 0 }
            ?: asset?.optInt("height")?.takeIf { it > 0 }
        return if (w != null && h != null && h > 0) w.toFloat() / h else 2.5f
    }

    private fun extractArea(sig: JSONObject): Long {
        val asset = sig.optJSONObject("asset")
        val w = sig.optInt("width").takeIf { it > 0 }
            ?: asset?.optInt("width")?.takeIf { it > 0 } ?: return 0
        val h = sig.optInt("height").takeIf { it > 0 }
            ?: asset?.optInt("height")?.takeIf { it > 0 } ?: return 0
        return w.toLong() * h
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun String.normalized(): String =
        java.text.Normalizer.normalize(lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }
}
