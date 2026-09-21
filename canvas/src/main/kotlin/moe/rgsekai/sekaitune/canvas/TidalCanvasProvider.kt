/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import moe.rgsekai.sekaitune.canvas.models.CanvasArtworkIdentity
import moe.rgsekai.sekaitune.canvas.tokens.TokenRejectedException
import moe.rgsekai.sekaitune.canvas.tokens.WebToken
import moe.rgsekai.sekaitune.canvas.tokens.WebTokenProvider
import java.io.IOException
import java.util.Base64
import java.util.Locale

object TidalCanvasProvider {
    private const val TIDAL_SEARCH_URL = "https://api.tidal.com/v1/search/tracks"
    private const val TIDAL_AUTH_URL = "https://auth.tidal.com/v1/oauth2/token"

    /*
     * TIDAL Client Credentials for anonymous catalog querying (OAuth 2.0 client_credentials flow).
     *
     * In 2026, TIDAL deprecated static `x-tidal-token` API keys on `api.tidal.com/v1` (which returned
     * 401 Invalid Token / subStatus 6004). Instead, public catalog reads require a dynamically minted
     * Bearer token via `https://auth.tidal.com/v1/oauth2/token` using standard client credentials.
     * Note: Direct web-scraping of `listen.tidal.com` is protected by DataDome antibot challenges
     * (ct.captcha-delivery.com), so this first-party OAuth endpoint is the official public client path.
     *
     * These credentials are valid public client credentials utilized by open-source TIDAL integration
     * clients (e.g. Android Automotive / tidal-dl community configurations). If TIDAL revokes these client
     * credentials in the future, check active open-source TIDAL client repositories (such as
     * yaronzz/Tidal-Media-Downloader or community Mopidy-Tidal forks) for updated OAuth client credentials.
     */
    private const val CLIENT_ID = "4N3n6Q1x95LL5K7p"
    private const val CLIENT_SECRET = "oKOXfJW371cX6xaZ0PyhgGNBdNLlBZd4AKKYougMjik="

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
    private data class TidalTokenResponse(
        val access_token: String,
        val token_type: String? = null,
        val expires_in: Long = 14400L,
    )

    @Serializable
    data class TidalSearchResponse(
        val items: List<TidalTrackItem>? = null,
    )

    @Serializable
    data class TidalTrackItem(
        val id: Long? = null,
        val title: String? = null,
        val duration: Long? = null,
        val videoCover: String? = null,
        val artists: List<TidalArtist>? = null,
        val album: TidalAlbum? = null,
    )

    @Serializable
    data class TidalArtist(
        val id: Long? = null,
        val name: String? = null,
    )

    @Serializable
    data class TidalAlbum(
        val id: Long? = null,
        val title: String? = null,
        val cover: String? = null,
        val videoCover: String? = null,
    )

    val tokenProvider by lazy {
        WebTokenProvider(
            providerName = "TIDAL",
            client = client,
            fetcher = { httpClient, _ ->
                val basicAuth = Base64.getEncoder().encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())
                val response = httpClient.submitForm(
                    url = TIDAL_AUTH_URL,
                    formParameters = Parameters.build {
                        append("grant_type", "client_credentials")
                    },
                ) {
                    header("Authorization", "Basic $basicAuth")
                }

                if (response.status != HttpStatusCode.OK) {
                    throw IOException("TIDAL token endpoint returned status ${response.status.value}: ${response.bodyAsText()}")
                }

                val rawBody = response.bodyAsText()
                val tokenBody = json.decodeFromString<TidalTokenResponse>(rawBody)
                val expiresAtSeconds = (System.currentTimeMillis() / 1000L) + tokenBody.expires_in
                WebToken(
                    token = tokenBody.access_token,
                    expiresAtEpochSeconds = expiresAtSeconds,
                )
            },
        )
    }

    suspend fun getCanvas(
        song: String,
        artists: List<String> = emptyList(),
        durationMs: Long? = null,
        countryCode: String = "US",
    ): CanvasArtwork? = runCatching {
        tokenProvider.executeWithTokenRetry { token ->
            val artistQuery = artists.firstOrNull() ?: ""
            val query = if (artistQuery.isNotBlank()) "$song $artistQuery" else song

            val response = client.get(TIDAL_SEARCH_URL) {
                header("Authorization", "Bearer $token")
                parameter("query", query)
                parameter("limit", 10)
                parameter("countryCode", countryCode.uppercase(Locale.ROOT))
            }

            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                throw TokenRejectedException(
                    provider = "TIDAL",
                    statusCode = response.status.value,
                    message = response.bodyAsText(),
                )
            }

            if (response.status != HttpStatusCode.OK) return@executeWithTokenRetry null

            val rawBody = response.bodyAsText()
            val body = runCatching { json.decodeFromString<TidalSearchResponse>(rawBody) }.getOrNull()
                ?: return@executeWithTokenRetry null
            val items = body.items ?: return@executeWithTokenRetry null

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
                        return@executeWithTokenRetry CanvasArtwork(
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

            null
        }
    }.getOrNull()

    suspend fun isHealthy(): Boolean =
        runCatching {
            tokenProvider.executeWithTokenRetry { token ->
                val response = client.get(TIDAL_SEARCH_URL) {
                    header("Authorization", "Bearer $token")
                    parameter("query", "music")
                    parameter("limit", 1)
                    parameter("countryCode", "US")
                }
                if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                    throw TokenRejectedException("TIDAL", response.status.value, response.bodyAsText())
                }
                response.status == HttpStatusCode.OK
            }
        }.getOrDefault(false)
}
