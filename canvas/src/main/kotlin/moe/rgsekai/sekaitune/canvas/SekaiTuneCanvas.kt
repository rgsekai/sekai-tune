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
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal object CanvasLogger {
    fun d(tag: String, msg: String) = println("$tag: D: $msg")
    fun i(tag: String, msg: String) = println("$tag: I: $msg")
    fun w(tag: String, msg: String, t: Throwable? = null) {
        println("$tag: W: $msg")
        t?.printStackTrace()
    }
    fun e(tag: String, msg: String, t: Throwable? = null) {
        System.err.println("$tag: E: $msg")
        t?.printStackTrace()
    }
}

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
                connectTimeoutMillis = 2_500
                requestTimeoutMillis = 5_000
                socketTimeoutMillis = 5_000
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

    @Volatile
    private var betterLyricsUnreachableUntilMs: Long = 0L

    suspend fun getCanvas(
        song: String,
        artists: List<String> = emptyList(),
        durationMs: Long? = null,
        policy: CanvasRequestPolicy = CanvasRequestPolicy(),
        storefront: String = "us",
    ): CanvasArtwork? {
        val artistStr = artists.joinToString(", ")

        val key = cacheKey("poly", policy.preferredSource.name, song, artistStr, storefront)
        cache[key]?.let { entry ->
            if (entry.expiresAtMs > System.currentTimeMillis()) return entry.value
            cache.remove(key)
        }

        val result =
            when (policy.preferredSource) {
                CanvasSource.BETTER_LYRICS -> {
                    val bl = getBySongArtist(song, artistStr, storefront)
                    if (bl?.preferredAnimationUrl != null) {
                        bl
                    } else if (policy.allowFallback) {
                        val spotify = if (!policy.spDc.isNullOrBlank()) SpotifyCanvasProvider.getCanvas(song, artists, durationMs, policy.spDc) else null
                        if (spotify?.preferredAnimationUrl != null) {
                            spotify
                        } else {
                            val tidal = TidalCanvasProvider.getCanvas(song, artists, durationMs, storefront)
                            if (tidal?.preferredAnimationUrl != null) {
                                tidal
                            } else {
                                val am = AppleMusicProvider.getBySongArtist(song, artistStr, null, storefront)
                                if (am?.preferredAnimationUrl != null) am else (bl ?: spotify ?: tidal ?: am)
                            }
                        }
                    } else {
                        bl
                    }
                }
                CanvasSource.APPLE_MUSIC -> {
                    val am = AppleMusicProvider.getBySongArtist(song, artistStr, null, storefront)
                    if (am?.preferredAnimationUrl != null) {
                        am
                    } else if (policy.allowFallback) {
                        val spotify = if (!policy.spDc.isNullOrBlank()) SpotifyCanvasProvider.getCanvas(song, artists, durationMs, policy.spDc) else null
                        if (spotify?.preferredAnimationUrl != null) {
                            spotify
                        } else {
                            val tidal = TidalCanvasProvider.getCanvas(song, artists, durationMs, storefront)
                            if (tidal?.preferredAnimationUrl != null) {
                                tidal
                            } else {
                                val bl = getBetterLyricsOnly(song, artistStr, storefront)
                                if (bl?.preferredAnimationUrl != null) bl else (am ?: spotify ?: tidal ?: bl)
                            }
                        }
                    } else {
                        am
                    }
                }
                CanvasSource.TIDAL -> {
                    val tidalResult = TidalCanvasProvider.getCanvas(song, artists, durationMs, storefront)
                    if (tidalResult?.preferredAnimationUrl != null) {
                        tidalResult
                    } else if (policy.allowFallback) {
                        val spotify = if (!policy.spDc.isNullOrBlank()) SpotifyCanvasProvider.getCanvas(song, artists, durationMs, policy.spDc) else null
                        if (spotify?.preferredAnimationUrl != null) {
                            spotify
                        } else {
                            val am = AppleMusicProvider.getBySongArtist(song, artistStr, null, storefront)
                            if (am?.preferredAnimationUrl != null) {
                                am
                            } else {
                                val bl = getBetterLyricsOnly(song, artistStr, storefront)
                                if (bl?.preferredAnimationUrl != null) bl else (tidalResult ?: spotify ?: am ?: bl)
                            }
                        }
                    } else {
                        tidalResult
                    }
                }
                CanvasSource.SPOTIFY -> {
                    val spotifyResult = SpotifyCanvasProvider.getCanvas(song, artists, durationMs, policy.spDc)
                    if (spotifyResult?.preferredAnimationUrl != null) {
                        spotifyResult
                    } else if (policy.allowFallback) {
                        val tidal = TidalCanvasProvider.getCanvas(song, artists, durationMs, storefront)
                        if (tidal?.preferredAnimationUrl != null) {
                            tidal
                        } else {
                            val am = AppleMusicProvider.getBySongArtist(song, artistStr, null, storefront)
                            if (am?.preferredAnimationUrl != null) {
                                am
                            } else {
                                val bl = getBetterLyricsOnly(song, artistStr, storefront)
                                if (bl?.preferredAnimationUrl != null) bl else (spotifyResult ?: tidal ?: am ?: bl)
                            }
                        }
                    } else {
                        spotifyResult
                    }
                }
                CanvasSource.ALL -> {
                    CanvasLogger.i("CanvasCascade", "=== Starting Canvas cascade for '$song' by '$artistStr' (storefront=$storefront) ===")
                    var fallbackStatic: CanvasArtwork? = null

                    // 1. BetterLyrics
                    val bl = getBetterLyricsOnly(song, artistStr, storefront)
                    if (bl?.preferredAnimationUrl != null) {
                        CanvasLogger.i("CanvasCascade", "Cascade WON by BetterLyrics with animated video: ${bl.preferredAnimationUrl}")
                        bl
                    } else {
                        if (bl?.static != null && fallbackStatic == null) {
                            fallbackStatic = bl
                            CanvasLogger.d("CanvasCascade", "Retained BetterLyrics static art as candidate fallback: ${bl.static}")
                        }

                        // 2. Apple Music
                        val am = try {
                            val res = AppleMusicProvider.getBySongArtist(song, artistStr, null, storefront)
                            val status = when {
                                res?.preferredAnimationUrl != null -> "found-with-video (url=${res.preferredAnimationUrl})"
                                res?.static != null -> "found-static-only (static=${res.static})"
                                else -> "not-found"
                            }
                            CanvasLogger.i("CanvasCascade", "Apple Music [$song - $artistStr]: $status")
                            res
                        } catch (error: Exception) {
                            CanvasLogger.e("CanvasCascade", "Apple Music [$song - $artistStr]: errored (${error.message})", error)
                            null
                        }

                        if (am?.preferredAnimationUrl != null) {
                            CanvasLogger.i("CanvasCascade", "Cascade WON by Apple Music with animated video: ${am.preferredAnimationUrl}")
                            am
                        } else {
                            if (am?.static != null && fallbackStatic == null) {
                                fallbackStatic = am
                                CanvasLogger.d("CanvasCascade", "Retained Apple Music static art as candidate fallback: ${am.static}")
                            }

                            // 3. TIDAL
                            val tidal = try {
                                val res = TidalCanvasProvider.getCanvas(song, artists, durationMs, storefront)
                                val status = when {
                                    res?.preferredAnimationUrl != null -> "found-with-video (url=${res.preferredAnimationUrl})"
                                    res?.static != null -> "found-static-only (static=${res.static})"
                                    else -> "not-found"
                                }
                                CanvasLogger.i("CanvasCascade", "TIDAL [$song - $artistStr]: $status")
                                res
                            } catch (error: Exception) {
                                CanvasLogger.e("CanvasCascade", "TIDAL [$song - $artistStr]: errored (${error.message})", error)
                                null
                            }

                            if (tidal?.preferredAnimationUrl != null) {
                                CanvasLogger.i("CanvasCascade", "Cascade WON by TIDAL with animated video: ${tidal.preferredAnimationUrl}")
                                tidal
                            } else {
                                if (tidal?.static != null && fallbackStatic == null) {
                                    fallbackStatic = tidal
                                    CanvasLogger.d("CanvasCascade", "Retained TIDAL static art as candidate fallback: ${tidal.static}")
                                }

                                // 4. Spotify
                                val spotify = if (policy.spDc.isNullOrBlank()) {
                                    CanvasLogger.i("CanvasCascade", "Spotify [$song - $artistStr]: skipped (not connected / no sp_dc)")
                                    null
                                } else {
                                    try {
                                        val res = SpotifyCanvasProvider.getCanvas(song, artists, durationMs, policy.spDc)
                                        val status = when {
                                            res?.preferredAnimationUrl != null -> "found-with-video (url=${res.preferredAnimationUrl})"
                                            res?.static != null -> "found-static-only (static=${res.static})"
                                            else -> "not-found"
                                        }
                                        CanvasLogger.i("CanvasCascade", "Spotify [$song - $artistStr]: $status")
                                        res
                                    } catch (error: Exception) {
                                        CanvasLogger.e("CanvasCascade", "Spotify [$song - $artistStr]: errored (${error.message})", error)
                                        null
                                    }
                                }

                                if (spotify?.preferredAnimationUrl != null) {
                                    CanvasLogger.i("CanvasCascade", "Cascade WON by Spotify with animated video: ${spotify.preferredAnimationUrl}")
                                    spotify
                                } else {
                                    if (spotify?.static != null && fallbackStatic == null) fallbackStatic = spotify
                                    if (fallbackStatic != null) {
                                        CanvasLogger.i("CanvasCascade", "Cascade ended: no provider had animated video, falling back to static artwork (static=${fallbackStatic.static}) -> Tier 5 Procedural Canvas active")
                                    } else {
                                        CanvasLogger.w("CanvasCascade", "Cascade ended: no artwork found on any provider -> base album art will be used for Tier 5 Procedural Canvas")
                                    }
                                    fallbackStatic
                                }
                            }
                        }
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

    suspend fun getBetterLyricsOnly(
        song: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? {
        val now = System.currentTimeMillis()
        if (betterLyricsUnreachableUntilMs > now) {
            CanvasLogger.d("CanvasCascade", "BetterLyrics [$song - $artist]: skipped (backend unreachable, cooloff remaining ${(betterLyricsUnreachableUntilMs - now) / 1000}s)")
            return null
        }
        val startTime = System.currentTimeMillis()
        return try {
            val response =
                client.get {
                    parameter("s", song)
                    parameter("a", artist)
                    parameter("storefront", storefront)
                }
            val elapsed = System.currentTimeMillis() - startTime
            if (response.status == HttpStatusCode.OK) {
                val artwork = response.body<CanvasArtwork>()
                val status = when {
                    artwork.preferredAnimationUrl != null -> "found-with-video (url=${artwork.preferredAnimationUrl})"
                    artwork.static != null -> "found-static-only (static=${artwork.static})"
                    else -> "not-found (empty payload)"
                }
                CanvasLogger.i("CanvasCascade", "BetterLyrics [$song - $artist] in ${elapsed}ms: $status")
                artwork
            } else {
                CanvasLogger.w("CanvasCascade", "BetterLyrics [$song - $artist] in ${elapsed}ms: not-found (HTTP ${response.status})")
                null
            }
        } catch (error: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            betterLyricsUnreachableUntilMs = System.currentTimeMillis() + 60_000L
            CanvasLogger.e("CanvasCascade", "BetterLyrics [$song - $artist] in ${elapsed}ms: errored (${error.message})", error)
            null
        }
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

        val bl = getBetterLyricsOnly(song, artist, storefront)
        val value = if (bl?.preferredAnimationUrl != null) {
            bl
        } else {
            val am = AppleMusicProvider.getBySongArtist(song, artist, null, storefront)
            if (am?.preferredAnimationUrl != null) {
                am
            } else {
                bl ?: am
            }
        }

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

    suspend fun isHealthy(): Boolean {
        val startTime = System.currentTimeMillis()
        val endpoint = "${BETTERLYRICS_URL}health"
        return try {
            CanvasLogger.d("CanvasHealth", "BetterLyrics health check probe starting -> GET $endpoint")
            val response =
                client.get(endpoint) {
                    header("Cache-Control", "no-cache")
                }
            val elapsed = System.currentTimeMillis() - startTime
            val isOk = response.status == HttpStatusCode.OK
            CanvasLogger.i(
                "CanvasHealth",
                "BetterLyrics health check probe finished in ${elapsed}ms: status=${response.status}, isHealthy=$isOk, requestUrl=${response.call.request.url}",
            )
            isOk
        } catch (error: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            CanvasLogger.e(
                "CanvasHealth",
                "BetterLyrics health check probe failed in ${elapsed}ms: exception=${error.javaClass.simpleName}, message=${error.message}, endpoint=$endpoint",
                error,
            )
            false
        }
    }
}





