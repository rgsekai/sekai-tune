package moe.rgsekai.sekaitune.shared.data.lyrics

import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.NetHttp
import moe.rgsekai.sekaitune.shared.platform.currentTimeMillis
import moe.rgsekai.sekaitune.shared.platform.hmacSha256
import moe.rgsekai.sekaitune.shared.platform.ioDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.takeFrom
import kotlin.concurrent.atomics.AtomicReference
import kotlin.io.encoding.Base64
import kotlin.math.abs
import kotlin.uuid.Uuid

/** Rich word timing, with line-synced fallback, from Musixmatch's web API. */
object Musixmatch {

    private const val BASE = "https://apic.musixmatch.com/ws/1.1"
    private const val APP_ID = "mobile-app-v1.0"
    private const val SEARCH_PAGE = "https://www.musixmatch.com/search"
    private const val BROWSER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    // Used only if the public page serving the rotating key is briefly unavailable.
    private const val FALLBACK_SIGNING_SECRET = "f09016176ba43a1cfd1031fbd6b3d26c"

    private val tokenMutex = Mutex()
    private val cachedSecret = AtomicReference<String?>(null)
    private val cachedToken = AtomicReference<String?>(null)
    private val processGuid = Uuid.random().toString()

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
    ): List<LyricLine>? = withContext(ioDispatcher) {
        val seconds = (durationMs / 1000).toInt()
        val track = bestTrack(title, artist, seconds) ?: return@withContext null

        // The subtitle endpoint is line-timed by design. Prefer the separate rich-sync
        // tier so tracks that have syllable data do not get flattened on ingestion.
        if (track.hasRichSync != 0) {
            val rich = fetchRichSync(track.trackId)
                ?.let(::parseRichSyncBody)
                ?.takeIf { lines -> lines.any { it.isWordSynced } }
            if (rich != null) return@withContext rich
        }

        val subtitle = if (track.hasSubtitles != 0) fetchSubtitle(track.trackId) else null
        val lrc = subtitle?.let(::subtitleToLrc)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        LrcLib.parseLrc(lrc).takeIf { it.isNotEmpty() }
    }

    private suspend fun bestTrack(title: String, artist: String, seconds: Int): Track? {
        val tracks = searchTrack(title, artist) ?: return null
        return tracks.maxByOrNull { score(it, title, artist, seconds) }
    }

    private fun score(track: Track, title: String, artist: String, seconds: Int): Double {
        var score = 0.0
        val name = track.trackName.trim().lowercase()
        val targetTitle = title.trim().lowercase()
        score += when {
            name == targetTitle -> 80.0
            name.contains(targetTitle) || targetTitle.contains(name) -> 40.0
            else -> 0.0
        }
        if (track.artistName.trim().lowercase().contains(artist.trim().lowercase())) {
            score += 40.0
        }
        track.trackLength?.let { length ->
            val diff = abs(length - seconds)
            score += when {
                diff <= 2 -> 30.0
                diff <= 5 -> 15.0
                diff <= 10 -> 5.0
                else -> -20.0
            }
        }
        return score
    }

    private suspend fun searchTrack(title: String, artist: String): List<Track>? {
        val response = signedGet { token ->
            URLBuilder("$BASE/track.search")
                .apply { param("app_id", APP_ID) }
                .apply { param("format", "json") }
                .apply { param("q_track", title) }
                .apply { param("q_artist", artist) }
                .apply { param("f_has_lyrics", "1") }
                .apply { param("s_track_rating", "desc") }
                .apply { param("quorum_factor", "1") }
                .apply { param("page_size", "10") }
                .apply { param("page", "1") }
                .apply { param("usertoken", token) }
                .build()
        } ?: return null
        val body = runCatching {
            lyricsJson.decodeFromString<Envelope<TrackSearchBody>>(response)
        }.getOrNull() ?: return null
        return body.message.body?.trackList?.map { it.track }
    }

    private suspend fun fetchSubtitle(trackId: Long): String? {
        val response = signedGet { token ->
            URLBuilder("$BASE/track.subtitle.get")
                .apply { param("app_id", APP_ID) }
                .apply { param("format", "json") }
                .apply { param("track_id", trackId.toString()) }
                .apply { param("subtitle_format", "mxm") }
                .apply { param("usertoken", token) }
                .build()
        } ?: return null
        return runCatching {
            lyricsJson.decodeFromString<Envelope<SubtitleBody>>(response)
        }.getOrNull()?.message?.body?.subtitle?.subtitleBody
    }

    private suspend fun fetchRichSync(trackId: Long): String? {
        val response = signedGet { token ->
            URLBuilder("$BASE/track.richsync.get")
                .apply { param("app_id", APP_ID) }
                .apply { param("format", "json") }
                .apply { param("track_id", trackId.toString()) }
                .apply { param("usertoken", token) }
                .build()
        } ?: return null
        return runCatching {
            lyricsJson.decodeFromString<Envelope<RichSyncBody>>(response)
        }.getOrNull()?.message?.body?.richsync?.richsyncBody
    }

    /**
     * Decodes rich-sync offsets without flattening them through line LRC.
     * Source order is retained because punctuation and syllable fragments may share a stamp.
     */
    fun parseRichSyncBody(body: String): List<LyricLine> {
        val entries = runCatching {
            lyricsJson.decodeFromString<List<RichSyncEntry>>(body)
        }.getOrNull() ?: return emptyList()

        return entries.mapNotNull { entry ->
            val lineStart = secondsToMs(entry.startSeconds)
            val lineEnd = maxOf(lineStart, secondsToMs(entry.endSeconds))
            var previousStart = lineStart
            // Fragments carry their own spacing, so a fragment with no space
            // after it is a syllable of a word that carries on — see
            // [wordsFromRuns].
            val runs = entry.fragments.mapIndexedNotNull { index, fragment ->
                if (fragment.text.isEmpty()) return@mapIndexedNotNull null
                val start = maxOf(
                    lineStart,
                    previousStart,
                    secondsToMs(entry.startSeconds + fragment.offsetSeconds),
                )
                val next = entry.fragments.getOrNull(index + 1)?.let {
                    secondsToMs(entry.startSeconds + it.offsetSeconds)
                } ?: lineEnd
                previousStart = start
                TimedRun(start, maxOf(start, minOf(lineEnd, next)), fragment.text)
            }
            val words = wordsFromRuns(runs, spacingIsExplicit = true)

            val text = entry.text.trim().ifEmpty { words.joinToString(" ") { it.text } }
            if (text.isEmpty()) return@mapNotNull null
            LyricLine(
                timeMs = minOf(lineStart, words.firstOrNull()?.startMs ?: lineStart),
                text = text,
                words = words,
                sungUntilMs = lineEnd.takeIf { it > lineStart },
            )
        }.sortedBy { it.timeMs }.withInstrumentalGaps()
    }

    /** Musixmatch's `mxm` subtitle JSON, converted only for the line-sync fallback. */
    private fun subtitleToLrc(subtitleBody: String): String {
        val lines = runCatching { lyricsJson.decodeFromString<List<SubtitleLine>>(subtitleBody) }
            .getOrNull() ?: return ""
        return buildString {
            for (line in lines) {
                if (line.text.isBlank()) continue
                val totalMs = secondsToMs(line.time.total)
                val minutes = totalMs / 1000 / 60
                val seconds = (totalMs / 1000) % 60
                val millis = totalMs % 1000
                appendLine(
                    "[" + "${minutes.pad(2)}:${seconds.pad(2)}.${millis.pad(3)}" + "]" + line.text,
                )
            }
        }.trim()
    }

    private fun Long.pad(width: Int) = toString().padStart(width, '0')

    private fun secondsToMs(seconds: Double): Long = (seconds * 1_000.0).toLong()

    /** Signs and issues [buildUrl]; an auth failure refreshes both moving credentials once. */
    private suspend fun signedGet(buildUrl: (token: String) -> Url): String? {
        val secret = getSecret()
        val token = getToken(secret) ?: return null
        val first = apiGet(sign(buildUrl(token).toString(), secret))
        if (first != null && !looksUnauthorized(first)) return first

        cachedToken.store(null)
        cachedSecret.store(null)
        val freshSecret = getSecret()
        val freshToken = getToken(freshSecret) ?: return null
        return apiGet(sign(buildUrl(freshToken).toString(), freshSecret))
    }

    /** Musixmatch reports an expired token inside a successful HTTP response. */
    private fun looksUnauthorized(body: String): Boolean =
        runCatching { lyricsJson.decodeFromString<Envelope<kotlinx.serialization.json.JsonElement>>(body) }
            .getOrNull()?.message?.header?.statusCode?.let { it == 401 || it == 402 } ?: false

    private suspend fun getToken(secret: String): String? = cachedToken.load() ?: tokenMutex.withLock {
        cachedToken.load() ?: fetchToken(secret)?.also(cachedToken::store)
    }

    private fun fetchToken(secret: String): String? {
        val url = URLBuilder("$BASE/token.get")
            .apply { param("app_id", APP_ID) }
            .apply { param("guid", processGuid) }
            .apply { param("format", "json") }
            .build()
        val body = apiGet(sign(url.toString(), secret)) ?: return null
        return runCatching {
            lyricsJson.decodeFromString<Envelope<TokenBody>>(body)
        }.getOrNull()?.message?.body?.userToken
    }

    /** Reads the rotating signing key from the JavaScript linked by the current web client. */
    private fun getSecret(): String = cachedSecret.load() ?: runCatching {
        val page = browserGet(SEARCH_PAGE, "text/html,application/xhtml+xml")
            ?: error("search page unavailable")
        val script = APP_SCRIPT.find(page)?.groupValues?.get(1)
            ?: error("application script not found")
        val scriptUrl = URLBuilder(SEARCH_PAGE).takeFrom(script).buildString()
        val javascript = browserGet(scriptUrl, "*/*") ?: error("application script unavailable")
        val encoded = ENCODED_SECRET.find(javascript)?.groupValues?.get(1)
            ?: error("signing key not found")
        Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(encoded.reversed()).decodeToString()
            .takeIf { it.isNotBlank() }
            ?: error("empty signing key")
    }.getOrElse { FALLBACK_SIGNING_SECRET }.also(cachedSecret::store)

    /** Signs `<url><UTC yyyyMMdd>` with HMAC-SHA256, matching the current web client. */
    private fun sign(url: String, secret: String): String {
        val normalized = url.replace("%20", "+").replace(" ", "+")
        val raw = hmacSha256(secret.encodeToByteArray(), "$normalized${utcDate()}".encodeToByteArray())
        val signature = Base64.Default.encode(raw)
        return "$normalized&signature=${signature.encodeURLParameter()}" +
            "&signature_protocol=sha256"
    }

    /** Today's UTC date as `yyyyMMdd` (days-to-civil, Howard Hinnant). */
    private fun utcDate(): String {
        val z = currentTimeMillis().floorDiv(86_400_000L) + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        return "$year${month.pad(2)}${day.pad(2)}"
    }

    private fun apiGet(url: String): String? = request(url, "application/json, text/plain, */*")

    private fun browserGet(url: String, accept: String): String? =
        request(url, accept, cookie = "mxm_bab=AB")

    private fun request(url: String, accept: String, cookie: String? = null): String? = runCatching {
        val headers = mutableMapOf(
            "User-Agent" to BROWSER_AGENT,
            "Accept" to accept,
            "Accept-Language" to "en-US,en;q=0.9",
        )
        if (cookie != null) headers["Cookie"] = cookie
        val response = NetHttp.execute(
            HttpRequest(url, headers = headers, callTimeoutMs = 8_000, connectTimeoutMs = 4_000),
        )
        if (response.isSuccessful) response.text() else null
    }.getOrNull()

    @Serializable
    private data class Envelope<T>(val message: Message<T>)

    @Serializable
    private data class Message<T>(val header: Header, val body: T? = null)

    @Serializable
    private data class Header(@SerialName("status_code") val statusCode: Int = 0)

    @Serializable
    private data class TokenBody(@SerialName("user_token") val userToken: String)

    @Serializable
    private data class TrackSearchBody(
        @SerialName("track_list") val trackList: List<TrackWrapper> = emptyList(),
    )

    @Serializable
    private data class TrackWrapper(val track: Track)

    @Serializable
    private data class Track(
        @SerialName("track_id") val trackId: Long,
        @SerialName("track_name") val trackName: String,
        @SerialName("artist_name") val artistName: String = "",
        @SerialName("track_length") val trackLength: Int? = null,
        @SerialName("has_subtitles") val hasSubtitles: Int? = null,
        @SerialName("has_richsync") val hasRichSync: Int? = null,
    )

    @Serializable
    private data class SubtitleBody(val subtitle: Subtitle? = null)

    @Serializable
    private data class Subtitle(@SerialName("subtitle_body") val subtitleBody: String)

    @Serializable
    private data class SubtitleLine(val text: String, val time: SubtitleTime)

    @Serializable
    private data class SubtitleTime(val total: Double)

    @Serializable
    private data class RichSyncBody(val richsync: RichSync? = null)

    @Serializable
    private data class RichSync(@SerialName("richsync_body") val richsyncBody: String? = null)

    @Serializable
    private data class RichSyncEntry(
        @SerialName("ts") val startSeconds: Double,
        @SerialName("te") val endSeconds: Double,
        @SerialName("l") val fragments: List<RichSyncFragment> = emptyList(),
        @SerialName("x") val text: String = "",
    )

    @Serializable
    private data class RichSyncFragment(
        @SerialName("c") val text: String,
        @SerialName("o") val offsetSeconds: Double,
    )

    private val APP_SCRIPT = Regex(
        """src=["']([^"']*/_next/static/chunks/pages/_app-[^"']+\.js)["']""",
        RegexOption.IGNORE_CASE,
    )
    private val ENCODED_SECRET = Regex("""from\(\s*["']([^"']+)["']\s*\.split""")
}
