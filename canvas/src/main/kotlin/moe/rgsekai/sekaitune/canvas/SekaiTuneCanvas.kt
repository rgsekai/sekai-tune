/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object SekaiTuneCanvas {
    private const val BETTERLYRICS_URL = "https://artwork.boidu.dev/"

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 12_000
                requestTimeoutMillis = 18_000
                socketTimeoutMillis = 18_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            install(HttpCache)
            defaultRequest {
                url(BETTERLYRICS_URL)
            }
            expectSuccess = false
        }
    }

    private data class CacheEntry(
        val value: CanvasArtwork?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlMs = 60_000L

    suspend fun getCanvas(
        song: String,
        artists: List<String> = emptyList(),
        durationMs: Long? = null,
        policy: CanvasRequestPolicy = CanvasRequestPolicy(),
        storefront: String = "us",
    ): CanvasArtwork? {
        if (policy.preferredSource == CanvasSource.OFF) return null
        val artistStr = artists.joinToString(", ")

        val key = cacheKey("poly", policy.preferredSource.name, song, artistStr, storefront)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val result =
            when (policy.preferredSource) {
                CanvasSource.OFF -> null
                CanvasSource.TIDAL -> {
                    val tidalResult = TidalCanvasProvider.getCanvas(song, artists, durationMs, storefront)
                    if (tidalResult != null) {
                        tidalResult
                    } else if (policy.allowFallback) {
                        getBySongArtist(song, artistStr, storefront)
                    } else {
                        null
                    }
                }
                CanvasSource.SPOTIFY -> {
                    val spotifyResult = SpotifyCanvasProvider.getCanvas(song, artists, durationMs)
                    if (spotifyResult != null) {
                        spotifyResult
                    } else if (policy.allowFallback) {
                        TidalCanvasProvider.getCanvas(song, artists, durationMs, storefront)
                            ?: getBySongArtist(song, artistStr, storefront)
                    } else {
                        null
                    }
                }
            }

        cache[key] =
            CacheEntry(
                value = result,
                expiresAtMs = System.currentTimeMillis() + ttlMs,
            )

        return result
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        val key = cacheKey("sa", song, artist, storefront)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val response =
            runCatching {
                client.get {
                    parameter("s", song)
                    parameter("a", artist)
                    parameter("storefront", storefront)
                }
            }.getOrNull()

        val primary =
            when (response?.status) {
                HttpStatusCode.OK -> runCatching { response.body<CanvasArtwork>() }.getOrNull()
                else -> null
            }

        val value = primary ?: AppleMusicProvider.getBySongArtist(song, artist, null, storefront)

        cache[key] =
            CacheEntry(
                value = value,
                expiresAtMs = System.currentTimeMillis() + ttlMs,
            )

        return value
    }

    suspend fun getByAlbumId(albumId: String): CanvasArtwork? {
        val key = cacheKey("id", albumId)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val response =
            runCatching {
                client.get {
                    parameter("id", albumId)
                }
            }.getOrNull()

        val primary =
            when (response?.status) {
                HttpStatusCode.OK -> runCatching { response.body<CanvasArtwork>() }.getOrNull()
                else -> null
            }

        val value = primary ?: AppleMusicProvider.getByAlbumId(albumId)

        cache[key] =
            CacheEntry(
                value = value,
                expiresAtMs = System.currentTimeMillis() + ttlMs,
            )

        return value
    }

    suspend fun getByAlbumUrl(url: String): CanvasArtwork? {
        val key = cacheKey("url", url)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val response =
            runCatching {
                client.get {
                    parameter("url", url)
                }
            }.getOrNull()

        val primary =
            when (response?.status) {
                HttpStatusCode.OK -> runCatching { response.body<CanvasArtwork>() }.getOrNull()
                else -> null
            }

        val value =
            primary ?: parseAppleMusicAlbumUrl(url)?.let { (albumId, storefront) ->
                AppleMusicProvider.getByAlbumId(albumId, storefront)
            }

        cache[key] =
            CacheEntry(
                value = value,
                expiresAtMs = System.currentTimeMillis() + ttlMs,
            )

        return value
    }

    private fun parseAppleMusicAlbumUrl(url: String): Pair<String, String>? {
        if (!url.contains("music.apple.com")) return null
        val albumPart = url.substringAfter("/album/", "").substringBefore("?")
        val albumId = albumPart.substringAfterLast("/", "")
        if (albumId.isBlank() || !albumId.all { it.isDigit() }) return null
        val storefront = url.substringAfter("music.apple.com/").substringBefore("/")
        if (storefront.isBlank()) return null
        return albumId to storefront
    }

    private fun cacheKey(
        prefix: String,
        vararg parts: String,
    ): String {
        val normalized =
            parts
                .map { it.trim().lowercase(Locale.ROOT) }
                .joinToString("|")
        return "$prefix|$normalized"
    }
}




