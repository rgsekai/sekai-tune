/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.WireFormat
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import moe.rgsekai.sekaitune.canvas.models.CanvasArtworkIdentity
import java.io.ByteArrayOutputStream

object SpotifyCanvasProvider {
    private const val SPOTIFY_TOKEN_URL = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
    private const val SPOTIFY_SEARCH_URL = "https://api.spotify.com/v1/search"
    private const val SPOTIFY_CANVAS_URL = "https://gew1-spclient.spotify.com/canvaz-cache/v0/canvases"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

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
    private data class SpotifyTokenResponse(
        val accessToken: String? = null,
        val accessTokenExpirationTimestampMs: Long? = null,
    )

    @Serializable
    private data class SpotifySearchResponse(
        val tracks: SpotifyTracksContainer? = null,
    )

    @Serializable
    private data class SpotifyTracksContainer(
        val items: List<SpotifyTrackItem>? = null,
    )

    @Serializable
    private data class SpotifyTrackItem(
        val id: String? = null,
        val name: String? = null,
        val uri: String? = null,
        val duration_ms: Long? = null,
        val artists: List<SpotifyArtist>? = null,
        val album: SpotifyAlbum? = null,
    )

    @Serializable
    private data class SpotifyArtist(
        val id: String? = null,
        val name: String? = null,
    )

    @Serializable
    private data class SpotifyAlbum(
        val id: String? = null,
        val name: String? = null,
        val images: List<SpotifyImage>? = null,
    )

    @Serializable
    private data class SpotifyImage(
        val url: String? = null,
        val height: Int? = null,
        val width: Int? = null,
    )

    private var cachedAccessToken: String? = null
    private var tokenExpiryMs: Long = 0L
    private val tokenLock = Any()

    private suspend fun getOrFetchAccessToken(): String? {
        synchronized(tokenLock) {
            val token = cachedAccessToken
            if (token != null && System.currentTimeMillis() < tokenExpiryMs - 60_000L) {
                return token
            }
        }

        val response =
            runCatching {
                client.get(SPOTIFY_TOKEN_URL) {
                    header("User-Agent", USER_AGENT)
                }
            }.getOrNull() ?: return null

        if (response.status != HttpStatusCode.OK) return null

        val tokenBody = runCatching { response.body<SpotifyTokenResponse>() }.getOrNull() ?: return null
        val token = tokenBody.accessToken ?: return null
        val expiry = tokenBody.accessTokenExpirationTimestampMs ?: (System.currentTimeMillis() + 3600_000L)

        synchronized(tokenLock) {
            cachedAccessToken = token
            tokenExpiryMs = expiry
        }
        return token
    }

    suspend fun getCanvas(
        song: String,
        artists: List<String> = emptyList(),
        durationMs: Long? = null,
    ): CanvasArtwork? {
        return runCatching {
            val token = getOrFetchAccessToken() ?: return null
            val artistQuery = artists.firstOrNull() ?: ""
            val query = if (artistQuery.isNotBlank()) "track:$song artist:$artistQuery" else song

            val searchResponse =
                client.get(SPOTIFY_SEARCH_URL) {
                    header("Authorization", "Bearer $token")
                    header("User-Agent", USER_AGENT)
                    parameter("type", "track")
                    parameter("q", query)
                    parameter("limit", 5)
                }

            if (searchResponse.status != HttpStatusCode.OK) return null

            val searchBody = searchResponse.body<SpotifySearchResponse>()
            val tracks = searchBody.tracks?.items ?: return null

            for (track in tracks) {
                val trackTitle = track.name ?: continue
                val trackUri = track.uri ?: continue
                val trackArtists = track.artists?.mapNotNull { it.name } ?: emptyList()
                val trackDuration = track.duration_ms

                if (CanvasArtworkIdentity.matches(
                        title1 = song,
                        artists1 = artists,
                        durationMs1 = durationMs,
                        title2 = trackTitle,
                        artists2 = trackArtists,
                        durationMs2 = trackDuration,
                    )
                ) {
                    val canvasUrl = fetchCanvasUrlForTrackUri(trackUri, token)
                    val staticCover = track.album?.images?.firstOrNull()?.url

                    if (canvasUrl != null || staticCover != null) {
                        return CanvasArtwork(
                            name = trackTitle,
                            artist = trackArtists.joinToString(", "),
                            albumName = track.album?.name,
                            static = staticCover,
                            animated = canvasUrl,
                            animatedVertical = canvasUrl,
                            videoUrl = canvasUrl,
                            videoUrlVertical = canvasUrl,
                        )
                    }
                }
            }
            null
        }.getOrNull()
    }

    private suspend fun fetchCanvasUrlForTrackUri(trackUri: String, token: String): String? {
        return runCatching {
            val requestBytes = buildCanvasProtobufRequest(trackUri)

            val response =
                client.post(SPOTIFY_CANVAS_URL) {
                    header("Authorization", "Bearer $token")
                    header("User-Agent", USER_AGENT)
                    contentType(ContentType("application", "x-protobuf"))
                    setBody(requestBytes)
                }

            if (response.status != HttpStatusCode.OK) return null

            val responseBytes = response.readRawBytes()
            parseCanvasUrlFromProtobuf(responseBytes)
        }.getOrNull()
    }

    private fun buildCanvasProtobufRequest(trackUri: String): ByteArray {
        val itemStream = ByteArrayOutputStream()
        val itemCos = CodedOutputStream.newInstance(itemStream)
        itemCos.writeString(1, trackUri)
        itemCos.flush()

        val mainStream = ByteArrayOutputStream()
        val mainCos = CodedOutputStream.newInstance(mainStream)
        mainCos.writeByteArray(1, itemStream.toByteArray())
        mainCos.flush()

        return mainStream.toByteArray()
    }

    private fun parseCanvasUrlFromProtobuf(data: ByteArray): String? {
        val cis = CodedInputStream.newInstance(data)
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            val fieldNumber = WireFormat.getTagFieldNumber(tag)
            val wireType = WireFormat.getTagWireType(tag)
            if (fieldNumber == 1 && wireType == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                val canvasBytes = cis.readByteArray()
                val canvasCis = CodedInputStream.newInstance(canvasBytes)
                var canvasUrl: String? = null
                while (!canvasCis.isAtEnd) {
                    val subTag = canvasCis.readTag()
                    val subField = WireFormat.getTagFieldNumber(subTag)
                    val subWire = WireFormat.getTagWireType(subTag)
                    if (subField == 2 && subWire == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                        canvasUrl = canvasCis.readString()
                    } else {
                        canvasCis.skipField(subTag)
                    }
                }
                if (!canvasUrl.isNullOrBlank()) {
                    return canvasUrl
                }
            } else {
                cis.skipField(tag)
            }
        }
        return null
    }
}
