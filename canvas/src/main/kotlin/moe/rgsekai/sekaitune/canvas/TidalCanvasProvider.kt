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
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import moe.rgsekai.sekaitune.canvas.models.CanvasArtworkIdentity

object TidalCanvasProvider {
    private const val TIDAL_SEARCH_URL = "https://api.tidal.com/v1/search/tracks"
    private const val TIDAL_TOKEN = "zU4XHVVkc2XDsqgn"

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
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            expectSuccess = false
        }
    }

    @Serializable
    private data class TidalSearchResponse(
        val items: List<TidalTrackItem>? = null,
    )

    @Serializable
    private data class TidalTrackItem(
        val id: Long? = null,
        val title: String? = null,
        val duration: Long? = null,
        val videoCover: String? = null,
        val artists: List<TidalArtist>? = null,
        val album: TidalAlbum? = null,
    )

    @Serializable
    private data class TidalArtist(
        val id: Long? = null,
        val name: String? = null,
    )

    @Serializable
    private data class TidalAlbum(
        val id: Long? = null,
        val title: String? = null,
        val cover: String? = null,
        val videoCover: String? = null,
    )

    suspend fun getCanvas(
        song: String,
        artists: List<String> = emptyList(),
        durationMs: Long? = null,
        countryCode: String = "US",
    ): CanvasArtwork? {
        val artistQuery = artists.firstOrNull() ?: ""
        val query = if (artistQuery.isNotBlank()) "$song $artistQuery" else song

        val response =
            runCatching {
                client.get(TIDAL_SEARCH_URL) {
                    header("x-tidal-token", TIDAL_TOKEN)
                    parameter("query", query)
                    parameter("limit", 10)
                    parameter("countryCode", countryCode)
                }
            }.getOrNull() ?: return null

        if (response.status != HttpStatusCode.OK) return null

        val body = runCatching { response.body<TidalSearchResponse>() }.getOrNull() ?: return null
        val items = body.items ?: return null

        for (item in items) {
            val itemTitle = item.title ?: continue
            val itemArtists = item.artists?.mapNotNull { it.name } ?: emptyList()
            val itemDurationMs = item.duration?.let { it * 1000L }

            if (CanvasArtworkIdentity.matches(
                    title1 = song,
                    artists1 = artists,
                    durationMs1 = durationMs,
                    title2 = itemTitle,
                    artists2 = itemArtists,
                    durationMs2 = itemDurationMs,
                )
            ) {
                val videoCoverId = item.videoCover ?: item.album?.videoCover
                val staticCoverId = item.album?.cover

                val videoUrl =
                    videoCoverId?.let { id ->
                        val path = id.replace("-", "/")
                        "https://resources.tidal.com/videos/$path/1280x1280.mp4"
                    }

                val staticUrl =
                    staticCoverId?.let { id ->
                        val path = id.replace("-", "/")
                        "https://resources.tidal.com/images/$path/1280x1280.jpg"
                    }

                if (videoUrl != null || staticUrl != null) {
                    return CanvasArtwork(
                        name = itemTitle,
                        artist = itemArtists.joinToString(", "),
                        albumName = item.album?.title,
                        static = staticUrl,
                        animated = videoUrl,
                        animatedVertical = videoUrl,
                        videoUrl = videoUrl,
                        videoUrlVertical = videoUrl,
                    )
                }
            }
        }

        return null
    }
}
