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
import io.ktor.client.statement.bodyAsText
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import moe.rgsekai.sekaitune.canvas.models.CanvasArtworkIdentity
import moe.rgsekai.sekaitune.spotify.SpotifyAuth
import moe.rgsekai.sekaitune.spotify.models.SpotifyInternalToken
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.UUID

object SpotifyCanvasProvider {
    private const val CANVAS_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    private const val WEB_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val APP_USER_AGENT = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"
    private const val DEFAULT_CLIENT_ID = "d8a5dee97f0c409e884d427966f30a64"
    private const val PROBE_TRACK_URI = "spotify:track:4cOdK2wGLETKBW3PvgPWqT"

    private val trackUriPattern = Regex("spotify:track:[A-Za-z0-9]{22}")
    private val configPattern = Regex("""<script[^>]*id="appServerConfig"[^>]*>([^<]+)</script>""")

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
            expectSuccess = false
        }
    }

    data class SpotifySession(
        val accessToken: String,
        val clientId: String,
        val expiresAtMs: Long,
        val spDc: String?,
    )

    private data class ClientToken(val clientId: String, val value: String, val expiresAtNanos: Long)

    private val sessionMutex = Mutex()
    private var cachedSession: SpotifySession? = null
    private val clientTokenMutex = Mutex()
    private var cachedClientToken: ClientToken? = null

    private suspend fun fetchClientToken(clientId: String): String = clientTokenMutex.withLock {
        require(clientId.isNotBlank())
        val now = System.nanoTime()
        cachedClientToken?.takeIf { it.clientId == clientId && now < it.expiresAtNanos }?.let {
            return@withLock it.value
        }

        val page = runCatching {
            client.get("https://open.spotify.com/") { header("User-Agent", WEB_USER_AGENT) }
        }.getOrNull()

        val encoded = page?.takeIf { it.status == HttpStatusCode.OK }?.let {
            configPattern.find(it.bodyAsText())?.groupValues?.get(1)
        }

        val config = encoded?.let {
            runCatching {
                json.parseToJsonElement(String(Base64.getDecoder().decode(it), Charsets.UTF_8)) as? JsonObject
            }.getOrNull()
        }

        val version = (config?.get("clientVersion") as? JsonPrimitive)?.contentOrNull ?: "1.2.58.498.g46b3dc22"

        val deviceId = page?.headers?.getAll("Set-Cookie").orEmpty().firstNotNullOfOrNull {
            it.substringBefore(';').takeIf { cookie -> cookie.startsWith("sp_t=") }?.substringAfter('=')
        } ?: UUID.randomUUID().toString()

        val payload = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", version)
                put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", "android")
                    put("os_version", "14")
                    put("device_id", deviceId)
                    put("device_type", "smartphone")
                }
            }
        }

        val response = client.post("https://clienttoken.spotify.com/v1/clienttoken") {
            header("User-Agent", WEB_USER_AGENT)
            header("Accept", "application/json")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }

        if (response.status != HttpStatusCode.OK) {
            throw IOException("Spotify Client-Token request failed: HTTP ${response.status.value}")
        }

        val root = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        val granted = root?.get("granted_token") as? JsonObject
            ?: (root?.get("granted_token_response") as? JsonObject)
            ?: throw IOException("Spotify client token was not granted")

        val value = (granted["token"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
            ?: throw IOException("Spotify client token was not granted")

        val ttlSeconds = (granted["expires_after_seconds"] as? JsonPrimitive)?.longOrNull?.coerceIn(0, 86_400) ?: 0
        cachedClientToken = ClientToken(clientId, value, now + (ttlSeconds - 30).coerceAtLeast(0) * 1_000_000_000)
        value
    }

    private suspend fun acquireSession(spDc: String?, forceRefresh: Boolean = false): SpotifySession? {
        return sessionMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = cachedSession
            if (!forceRefresh && cached != null && cached.expiresAtMs > now + 60_000L && cached.spDc == spDc) {
                CanvasLogger.d("SpotifyCanvas", "acquireSession: Reusing valid cached session (clientId=${cached.clientId}, expires in ${(cached.expiresAtMs - now) / 1000}s)")
                return@withLock cached
            }

            var accessToken: String? = null
            var clientId: String? = null
            var expiresAtMs: Long = now + 3600_000L

            if (!spDc.isNullOrBlank()) {
                CanvasLogger.d("SpotifyCanvas", "acquireSession: Fetching fresh access token via SpotifyAuth (spDc length=${spDc.length}, forceRefresh=$forceRefresh)...")
                val authResult = SpotifyAuth.fetchAccessToken(spDc)
                if (authResult.isSuccess) {
                    val internalToken = authResult.getOrNull()
                    if (internalToken != null && internalToken.accessToken.isNotBlank()) {
                        accessToken = internalToken.accessToken
                        clientId = internalToken.clientId.ifBlank { DEFAULT_CLIENT_ID }
                        expiresAtMs = if (internalToken.accessTokenExpirationTimestampMs > 0L) {
                            internalToken.accessTokenExpirationTimestampMs
                        } else {
                            now + 3600_000L
                        }
                        CanvasLogger.i("SpotifyCanvas", "acquireSession: Successfully obtained token (clientId=$clientId, expires in ${(expiresAtMs - now) / 1000}s)")
                    } else {
                        CanvasLogger.w("SpotifyCanvas", "acquireSession: SpotifyAuth returned empty/blank access token (isAnonymous=${internalToken?.isAnonymous})")
                    }
                } else {
                    CanvasLogger.e("SpotifyCanvas", "acquireSession: SpotifyAuth.fetchAccessToken failed: ${authResult.exceptionOrNull()?.message}", authResult.exceptionOrNull())
                }
            } else {
                CanvasLogger.w("SpotifyCanvas", "acquireSession: spDc is null or blank, cannot obtain Spotify session")
            }

            if (accessToken == null) return@withLock null
            val finalClientId = clientId ?: DEFAULT_CLIENT_ID

            val session = SpotifySession(
                accessToken = accessToken,
                clientId = finalClientId,
                expiresAtMs = expiresAtMs,
                spDc = spDc,
            )
            cachedSession = session
            session
        }
    }

    private suspend fun invalidateSession() {
        sessionMutex.withLock {
            CanvasLogger.d("SpotifyCanvas", "invalidateSession: Invalidated cached session")
            cachedSession = null
        }
    }

    suspend fun getCanvas(
        song: String,
        artists: List<String> = emptyList(),
        durationMs: Long? = null,
        spDc: String? = null,
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val artistStr = artists.firstOrNull().orEmpty()
        getBySongArtist(song, artistStr, spDc)
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        spDc: String? = null,
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        runCatching {
            var session = acquireSession(spDc) ?: return@withContext null
            var result = fetchBySongArtistInternal(song, artist, session)
            if (result == null && session.spDc != null) {
                invalidateSession()
                session = acquireSession(spDc, forceRefresh = true) ?: return@withContext null
                result = fetchBySongArtistInternal(song, artist, session)
            }
            result
        }.getOrNull()
    }

    private suspend fun fetchBySongArtistInternal(
        song: String,
        artist: String,
        session: SpotifySession,
    ): CanvasArtwork? {
        val token = runCatching { fetchClientToken(session.clientId) }.getOrDefault("")
        val query = if (artist.isNotBlank()) "$song $artist" else song
        CanvasLogger.d("SpotifyCanvas", "fetchBySongArtist: query='$query', clientId=${session.clientId}, hasClientToken=${token.isNotBlank()}")

        val variables = buildJsonObject {
            put("searchTerm", query)
            put("offset", 0)
            put("limit", 10)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includePreReleases", false)
        }
        val extensions = buildJsonObject {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428")
            }
        }

        val search = runCatching {
            client.get("https://api-partner.spotify.com/pathfinder/v1/query") {
                header("Authorization", "Bearer ${session.accessToken}")
                if (token.isNotBlank()) header("Client-Token", token)
                header("App-Platform", "WebPlayer")
                header("User-Agent", WEB_USER_AGENT)
                parameter("operationName", "searchTracks")
                parameter("variables", variables.toString())
                parameter("extensions", extensions.toString())
            }
        }.getOrNull()

        CanvasLogger.d("SpotifyCanvas", "Pathfinder searchTracks status: ${search?.status}")

        val root = if (search?.status == HttpStatusCode.OK) {
            runCatching { json.parseToJsonElement(search.bodyAsText()) as? JsonObject }.getOrNull()
        } else null

        val items = root?.obj("data")?.obj("searchV2")?.obj("tracksV2")?.array("items") ?: JsonArray(emptyList())
        var candidates = items.mapNotNull { item ->
            parseTrack((item as? JsonObject)?.obj("item")?.obj("data"), true)
        }.filter { candidate ->
            CanvasArtworkIdentity.matches(
                title1 = song,
                artists1 = if (artist.isNotBlank()) listOf(artist) else emptyList(),
                durationMs1 = null,
                title2 = candidate.second.name ?: "",
                artists2 = candidate.second.artist?.split(",")?.map { it.trim() } ?: emptyList(),
                durationMs2 = null,
            )
        }
        CanvasLogger.d("SpotifyCanvas", "Pathfinder matched ${candidates.size} candidate tracks: ${candidates.map { "${it.first} (${it.second.name})" }}")

        if (candidates.isEmpty()) {
            val response = runCatching {
                client.get("https://api.spotify.com/v1/search") {
                    header("Authorization", "Bearer ${session.accessToken}")
                    if (token.isNotBlank()) header("Client-Token", token)
                    header("User-Agent", WEB_USER_AGENT)
                    parameter("q", query)
                    parameter("type", "track")
                    parameter("limit", 10)
                }
            }.getOrNull()

            CanvasLogger.d("SpotifyCanvas", "REST search status: ${response?.status}")

            if (response?.status == HttpStatusCode.OK) {
                val rest = runCatching { json.parseToJsonElement(response.bodyAsText()) as? JsonObject }.getOrNull()
                candidates = rest?.obj("tracks")?.array("items").orEmpty().mapNotNull { parseTrack(it as? JsonObject, false) }
                    .filter { candidate ->
                        CanvasArtworkIdentity.matches(
                            title1 = song,
                            artists1 = if (artist.isNotBlank()) listOf(artist) else emptyList(),
                            durationMs1 = null,
                            title2 = candidate.second.name ?: "",
                            artists2 = candidate.second.artist?.split(",")?.map { it.trim() } ?: emptyList(),
                            durationMs2 = null,
                        )
                    }
                CanvasLogger.d("SpotifyCanvas", "REST matched ${candidates.size} candidate tracks: ${candidates.map { "${it.first} (${it.second.name})" }}")
            }
        }

        if (candidates.isEmpty()) return null

        val trackUris = candidates.map { it.first }.distinct().take(10)
        val urls = getCanvases(trackUris, session.accessToken, session.clientId)

        return candidates.firstNotNullOfOrNull { (uri, artwork) ->
            urls[uri]?.let { artwork.copy(animated = it, animatedVertical = it, videoUrl = it, videoUrlVertical = it) }
        }
    }

    private fun parseTrack(track: JsonObject?, graphQl: Boolean): Pair<String, CanvasArtwork>? {
        val uri = track.string("uri") ?: track.string("id")?.let { "spotify:track:$it" } ?: return null
        if (!trackUriPattern.matches(uri)) return null
        val title = track.string("name") ?: return null
        val album = if (graphQl) track.obj("albumOfTrack") else track.obj("album")
        val artists = if (graphQl) track.obj("artists")?.array("items") else track.array("artists")
        val credits = artists.orEmpty().mapNotNull { (it as? JsonObject).string("name") }
        val coverArt = if (graphQl) {
            album?.obj("coverArt")?.array("sources")
        } else {
            album?.array("images")
        }
        val image = coverArt.orEmpty().firstNotNullOfOrNull { (it as? JsonObject)?.string("url") }
            ?: (if (graphQl) album?.obj("coverArt")?.array("extractedColors") else null)?.let { null }
        return uri to CanvasArtwork(
            name = title,
            artist = credits.joinToString(", "),
            albumName = album.string("name"),
            static = image,
        )
    }

    private fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
    private fun JsonObject?.string(key: String): String? = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.array(key: String): JsonArray = this?.get(key) as? JsonArray ?: JsonArray(emptyList())

    suspend fun getCanvases(trackUris: List<String>, accessToken: String, clientId: String): Map<String, String> {
        if (trackUris.isEmpty() || trackUris.size > 10) return emptyMap()
        val clientToken = runCatching { fetchClientToken(clientId) }.getOrDefault("")
        CanvasLogger.d("SpotifyCanvas", "getCanvases: querying ${trackUris.size} tracks -> $trackUris (clientId=$clientId, hasClientToken=${clientToken.isNotBlank()})")
        val response = runCatching {
            client.post(CANVAS_URL) {
                header("Authorization", "Bearer $accessToken")
                if (clientToken.isNotBlank()) header("Client-Token", clientToken)
                header("User-Agent", APP_USER_AGENT)
                header("Accept", "application/protobuf")
                header("Content-Type", "application/protobuf")
                header("Accept-Language", "en")
                setBody(encodeRequest(trackUris))
            }
        }.getOrNull()

        if (response == null) {
            CanvasLogger.w("SpotifyCanvas", "getCanvases: HTTP request failed (network error / exception)")
            return emptyMap()
        }

        CanvasLogger.d("SpotifyCanvas", "getCanvases: response status=${response.status.value}")
        if (response.status != HttpStatusCode.OK) return emptyMap()
        val bytes = response.readRawBytes()
        CanvasLogger.d("SpotifyCanvas", "getCanvases: received ${bytes.size} raw protobuf bytes")
        if (bytes.size > 1_048_576) return emptyMap()
        val decoded = decodeResponse(bytes)
        CanvasLogger.d("SpotifyCanvas", "getCanvases: decoded ${decoded.size} canvas entries: $decoded")
        return decoded.filterKeys { it in trackUris }
    }

    suspend fun isHealthy(spDc: String? = null): Boolean = withContext(Dispatchers.IO) {
        CanvasLogger.i("SpotifyCanvas", "isHealthy: Starting health check probe (spDc.length=${spDc?.length ?: 0})...")
        runCatching {
            val session = acquireSession(spDc)
            if (session == null) {
                CanvasLogger.w("SpotifyCanvas", "isHealthy: acquireSession returned null")
                return@withContext false
            }
            // Probe with tracks verified to have a Canvas on Spotify ("Summertime Sadness" variants)
            val candidateUris = listOf(
                "spotify:track:3BJe4B8zGnqEdQPMvfVjuS",
                "spotify:track:1Ist6PR2BZR3n2z2Y5R6S1",
                "spotify:track:33CeM8NI7tfrNgciVOFMoo",
            )
            val canvases = getCanvases(candidateUris, session.accessToken, session.clientId)
            val ok = canvases.isNotEmpty()
            CanvasLogger.i("SpotifyCanvas", "isHealthy: probe finished -> isHealthy=$ok (resolved ${canvases.size} canvas items)")
            ok
        }.getOrElse { error ->
            CanvasLogger.e("SpotifyCanvas", "isHealthy: probe threw exception: ${error.message}", error)
            false
        }
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while ((v and 0x7FL.inv()) != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write((v and 0x7F).toInt())
    }

    private fun encodeRequest(trackUris: List<String>): ByteArray {
        val buffer = ByteArrayOutputStream()
        for (uri in trackUris) {
            val uriBytes = uri.toByteArray(Charsets.UTF_8)
            val trackBuffer = ByteArrayOutputStream()
            // Field 1 (track_uri) in CanvasRequest
            writeVarint(trackBuffer, (1L shl 3) or 2L)
            writeVarint(trackBuffer, uriBytes.size.toLong())
            trackBuffer.write(uriBytes)
            val trackBytes = trackBuffer.toByteArray()

            // Field 1 (tracks) in CanvasRequestContainer
            writeVarint(buffer, (1L shl 3) or 2L)
            writeVarint(buffer, trackBytes.size.toLong())
            buffer.write(trackBytes)
        }
        return buffer.toByteArray()
    }

    private fun decodeResponse(bytes: ByteArray): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val reader = SimpleProtoReader(bytes)
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
                var trackUri: String? = null
                while (!innerReader.isAtEnd) {
                    val innerTag = innerReader.readTag()
                    if (innerTag == 0) break
                    val innerField = innerTag ushr 3
                    val innerWire = innerTag and 7
                    when {
                        innerField == 2 && innerWire == 2 -> {
                            val strLen = innerReader.readVarint().toInt()
                            canvasUrl = innerReader.readString(strLen)
                        }
                        innerField == 5 && innerWire == 2 -> {
                            val strLen = innerReader.readVarint().toInt()
                            trackUri = innerReader.readString(strLen)
                        }
                        else -> innerReader.skipField(innerTag)
                    }
                }
                if (trackUri != null && canvasUrl != null && isCanvasUrl(canvasUrl)) {
                    result[trackUri] = canvasUrl
                }
            } else {
                reader.skipField(tag)
            }
        }
        return result
    }

    private fun isCanvasUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "canvaz.scdn.co" && uri.path.endsWith(".mp4")
    } catch (_: Exception) {
        false
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
}

