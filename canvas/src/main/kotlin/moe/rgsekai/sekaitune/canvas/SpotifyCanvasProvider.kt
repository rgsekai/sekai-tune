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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import moe.rgsekai.sekaitune.canvas.models.CanvasArtworkIdentity
import moe.rgsekai.sekaitune.spotify.SpotifyAuth
import java.io.ByteArrayOutputStream

object SpotifyCanvasProvider {
    private const val SPOTIFY_TOKEN_URL = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
    private const val SPOTIFY_CLIENT_TOKEN_URL = "https://clienttoken.spotify.com/v1/clienttoken"
    private const val SPOTIFY_SEARCH_URL = "https://api.spotify.com/v1/search"
    private const val SPOTIFY_CANVAS_URL = "https://gew1-spclient.spotify.com/canvaz-cache/v0/canvases"
    private const val DEFAULT_CLIENT_ID = "d8a5dee97f0c409e884d427966f30a64"
    private const val PROBE_TRACK_URI = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"
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

    data class SpotifySession(
        val accessToken: String,
        val clientId: String,
        val clientToken: String,
        val expiresAtMs: Long,
        val spDc: String?,
    )

    @Serializable
    private data class SpotifyTokenResponse(
        val accessToken: String? = null,
        val accessTokenExpirationTimestampMs: Long? = null,
        val clientId: String? = null,
    )

    @Serializable
    private data class ClientTokenRequest(
        val client_data: ClientData,
    )

    @Serializable
    private data class ClientData(
        val client_id: String,
        val js_sdk_data: JsSdkData = JsSdkData(),
    )

    @Serializable
    private data class JsSdkData(
        val device_brand: String = "unknown",
        val device_model: String = "unknown",
        val os: String = "windows",
        val os_version: String = "NT 10.0",
    )

    @Serializable
    private data class ClientTokenResponse(
        val response_type: String? = null,
        val granted_token_response: GrantedTokenResponse? = null,
    )

    @Serializable
    private data class GrantedTokenResponse(
        val token: String? = null,
        val expires_after_seconds: Long? = null,
        val refresh_after_seconds: Long? = null,
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

    private val sessionMutex = Mutex()
    private var cachedSession: SpotifySession? = null

    private suspend fun fetchClientToken(clientId: String): String? {
        val payload = ClientTokenRequest(
            client_data = ClientData(client_id = clientId)
        )

        val response = runCatching {
            client.post(SPOTIFY_CLIENT_TOKEN_URL) {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/json")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        }.getOrNull() ?: return null

        if (response.status != HttpStatusCode.OK) return null
        val body = runCatching { response.body<ClientTokenResponse>() }.getOrNull()
        return body?.granted_token_response?.token
    }

    private suspend fun acquireSession(spDc: String?, forceRefresh: Boolean = false): SpotifySession? {
        return sessionMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = cachedSession
            if (!forceRefresh && cached != null && cached.expiresAtMs > now + 60_000L && cached.spDc == spDc) {
                return@withLock cached
            }

            var accessToken: String? = null
            var clientId: String? = null
            var expiresAtMs: Long = now + 3600_000L

            // 1. Try authenticated session via sp_dc if available
            if (!spDc.isNullOrBlank()) {
                val authResult = runCatching { SpotifyAuth.fetchAccessToken(spDc) }.getOrNull()
                val internalToken = authResult?.getOrNull()
                if (internalToken != null && internalToken.accessToken.isNotBlank()) {
                    accessToken = internalToken.accessToken
                    clientId = internalToken.clientId.ifBlank { DEFAULT_CLIENT_ID }
                    expiresAtMs = if (internalToken.accessTokenExpirationTimestampMs > 0L) {
                        internalToken.accessTokenExpirationTimestampMs
                    } else {
                        now + 3600_000L
                    }
                }
            }

            // 2. Fallback to web player token endpoint
            if (accessToken == null) {
                val tokenResponse = runCatching {
                    client.get(SPOTIFY_TOKEN_URL) {
                        header("User-Agent", USER_AGENT)
                    }
                }.getOrNull()

                if (tokenResponse?.status == HttpStatusCode.OK) {
                    val tokenBody = runCatching { tokenResponse.body<SpotifyTokenResponse>() }.getOrNull()
                    if (tokenBody?.accessToken != null) {
                        accessToken = tokenBody.accessToken
                        clientId = tokenBody.clientId?.ifBlank { DEFAULT_CLIENT_ID } ?: DEFAULT_CLIENT_ID
                        expiresAtMs = tokenBody.accessTokenExpirationTimestampMs ?: (now + 3600_000L)
                    }
                }
            }

            if (accessToken == null) return@withLock null
            val finalClientId = clientId ?: DEFAULT_CLIENT_ID

            // 3. Obtain matching Client-Token
            val clientToken = fetchClientToken(finalClientId) ?: ""

            val session = SpotifySession(
                accessToken = accessToken,
                clientId = finalClientId,
                clientToken = clientToken,
                expiresAtMs = expiresAtMs,
                spDc = spDc,
            )
            cachedSession = session
            session
        }
    }

    private suspend fun invalidateSession() {
        sessionMutex.withLock {
            cachedSession = null
        }
    }

    suspend fun getCanvas(
        song: String,
        artists: List<String> = emptyList(),
        durationMs: Long? = null,
        spDc: String? = null,
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        runCatching {
            var session = acquireSession(spDc) ?: return@withContext null
            val artistQuery = artists.firstOrNull() ?: ""
            val query = if (artistQuery.isNotBlank()) "track:$song artist:$artistQuery" else song

            var searchResponse = client.get(SPOTIFY_SEARCH_URL) {
                header("Authorization", "Bearer ${session.accessToken}")
                if (session.clientToken.isNotBlank()) {
                    header("client-token", session.clientToken)
                }
                header("User-Agent", USER_AGENT)
                parameter("type", "track")
                parameter("q", query)
                parameter("limit", 5)
            }

            // Auth error recovery
            if (searchResponse.status == HttpStatusCode.Unauthorized || searchResponse.status == HttpStatusCode.Forbidden) {
                invalidateSession()
                session = acquireSession(spDc, forceRefresh = true) ?: return@withContext null
                searchResponse = client.get(SPOTIFY_SEARCH_URL) {
                    header("Authorization", "Bearer ${session.accessToken}")
                    if (session.clientToken.isNotBlank()) {
                        header("client-token", session.clientToken)
                    }
                    header("User-Agent", USER_AGENT)
                    parameter("type", "track")
                    parameter("q", query)
                    parameter("limit", 5)
                }
            }

            if (searchResponse.status != HttpStatusCode.OK) return@withContext null

            val searchBody = searchResponse.body<SpotifySearchResponse>()
            val tracks = searchBody.tracks?.items ?: return@withContext null

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
                    var canvasUrl = fetchCanvasUrlForTrackUri(trackUri, session)
                    if (canvasUrl == null) {
                        // In case of token rejection during canvas fetch
                        invalidateSession()
                        val refreshedSession = acquireSession(spDc, forceRefresh = true)
                        if (refreshedSession != null) {
                            canvasUrl = fetchCanvasUrlForTrackUri(trackUri, refreshedSession)
                        }
                    }

                    val staticCover = track.album?.images?.firstOrNull()?.url

                    if (canvasUrl != null || staticCover != null) {
                        return@withContext CanvasArtwork(
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

    private suspend fun fetchCanvasUrlForTrackUri(trackUri: String, session: SpotifySession): String? {
        return runCatching {
            val requestBytes = buildCanvasProtobufRequest(trackUri)

            val response = client.post(SPOTIFY_CANVAS_URL) {
                header("Authorization", "Bearer ${session.accessToken}")
                if (session.clientToken.isNotBlank()) {
                    header("client-token", session.clientToken)
                }
                header("User-Agent", USER_AGENT)
                contentType(ContentType("application", "x-protobuf"))
                setBody(requestBytes)
            }

            if (response.status != HttpStatusCode.OK) return null

            val responseBytes = response.readRawBytes()
            parseCanvasUrlFromProtobuf(responseBytes)
        }.getOrNull()
    }

    suspend fun isHealthy(spDc: String? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            var session = acquireSession(spDc) ?: return@withContext false
            val requestBytes = buildCanvasProtobufRequest(PROBE_TRACK_URI)

            var response = client.post(SPOTIFY_CANVAS_URL) {
                header("Authorization", "Bearer ${session.accessToken}")
                if (session.clientToken.isNotBlank()) {
                    header("client-token", session.clientToken)
                }
                header("User-Agent", USER_AGENT)
                contentType(ContentType("application", "x-protobuf"))
                setBody(requestBytes)
            }

            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                invalidateSession()
                session = acquireSession(spDc, forceRefresh = true) ?: return@withContext false
                response = client.post(SPOTIFY_CANVAS_URL) {
                    header("Authorization", "Bearer ${session.accessToken}")
                    if (session.clientToken.isNotBlank()) {
                        header("client-token", session.clientToken)
                    }
                    header("User-Agent", USER_AGENT)
                    contentType(ContentType("application", "x-protobuf"))
                    setBody(requestBytes)
                }
            }

            response.status == HttpStatusCode.OK
        }.getOrDefault(false)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while ((v and 0x7FL.inv()) != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private fun buildCanvasProtobufRequest(trackUri: String): ByteArray {
        val uriBytes = trackUri.toByteArray(Charsets.UTF_8)
        val innerStream = ByteArrayOutputStream()
        // Field 1 (track_uri), wire type 2 (length-delimited): tag = (1 shl 3) or 2 = 0x0A
        writeVarint(innerStream, (1L shl 3) or 2L)
        writeVarint(innerStream, uriBytes.size.toLong())
        innerStream.write(uriBytes)
        val innerBytes = innerStream.toByteArray()

        val outerStream = ByteArrayOutputStream()
        // Field 1 (tracks), wire type 2 (length-delimited): tag = (1 shl 3) or 2 = 0x0A
        writeVarint(outerStream, (1L shl 3) or 2L)
        writeVarint(outerStream, innerBytes.size.toLong())
        outerStream.write(innerBytes)
        return outerStream.toByteArray()
    }

    private class SimpleProtoReader(
        private val bytes: ByteArray,
        offset: Int = 0,
        private val limit: Int = bytes.size,
    ) {
        private var pos = offset

        val isAtEnd: Boolean get() = pos >= limit

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (pos < limit && shift < 64) {
                val b = bytes[pos++].toLong()
                result = result or ((b and 0x7F) shl shift)
                if ((b and 0x80L) == 0L) return result
                shift += 7
            }
            return result
        }

        fun readTag(): Int = if (isAtEnd) 0 else readVarint().toInt()

        fun readBytes(length: Int): ByteArray {
            val safeLen = length.coerceAtMost(limit - pos).coerceAtLeast(0)
            val copy = bytes.copyOfRange(pos, pos + safeLen)
            pos += safeLen
            return copy
        }

        fun readString(length: Int): String {
            val safeLen = length.coerceAtMost(limit - pos).coerceAtLeast(0)
            val str = String(bytes, pos, safeLen, Charsets.UTF_8)
            pos += safeLen
            return str
        }

        fun skipField(tag: Int) {
            val wireType = tag and 7
            when (wireType) {
                0 -> readVarint()
                1 -> pos = (pos + 8).coerceAtMost(limit)
                2 -> {
                    val len = readVarint().toInt()
                    pos = (pos + len).coerceAtMost(limit)
                }
                5 -> pos = (pos + 4).coerceAtMost(limit)
                else -> pos = limit
            }
        }
    }

    private fun parseCanvasUrlFromProtobuf(data: ByteArray): String? {
        val reader = SimpleProtoReader(data)
        while (!reader.isAtEnd) {
            val tag = reader.readTag()
            if (tag == 0) break
            val fieldNumber = tag ushr 3
            val wireType = tag and 7
            if (fieldNumber == 1 && wireType == 2) {
                val len = reader.readVarint().toInt()
                val canvasBytes = reader.readBytes(len)
                val innerReader = SimpleProtoReader(canvasBytes)
                var canvasUrl: String? = null
                while (!innerReader.isAtEnd) {
                    val innerTag = innerReader.readTag()
                    if (innerTag == 0) break
                    val innerField = innerTag ushr 3
                    val innerWire = innerTag and 7
                    if (innerField == 2 && innerWire == 2) {
                        val strLen = innerReader.readVarint().toInt()
                        canvasUrl = innerReader.readString(strLen)
                    } else {
                        innerReader.skipField(innerTag)
                    }
                }
                if (!canvasUrl.isNullOrBlank()) {
                    return canvasUrl
                }
            } else {
                reader.skipField(tag)
            }
        }
        return null
    }
}
