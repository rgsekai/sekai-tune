package moe.rgsekai.sekaitune.shared.data.spotify

import moe.rgsekai.sekaitune.shared.data.YtMusicRepository
import moe.rgsekai.sekaitune.shared.data.model.SearchFilter
import moe.rgsekai.sekaitune.shared.data.model.SearchResult
import moe.rgsekai.sekaitune.shared.data.model.Song
import moe.rgsekai.sekaitune.shared.data.lyrics.param
import moe.rgsekai.sekaitune.shared.data.sources.TrackMatcher
import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.NetHttp
import moe.rgsekai.sekaitune.shared.platform.ioDispatcher
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.atomics.AtomicInt

/** Single track metadata extracted from a Spotify playlist. */
data class SpotifyImportTrack(
    val title: String,
    val artist: String,
)

/** The result of fetching and resolving a Spotify playlist's tracks. */
data class SpotifyImportResult(
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val totalTracks: Int,
    val resolvedVideoIds: List<String>,
    val unmatchedTracks: List<SpotifyImportTrack>,
)

object SpotifyImporter {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** A Spotify playlist id is 22 base-62 characters. */
    private val PLAYLIST_ID = Regex("""[A-Za-z0-9]{22}""")

    private fun isSpotifyHost(host: String) =
        host == "spotify.com" || host.endsWith(".spotify.com")

    /** Parses a raw user string or link into a Spotify playlist ID if valid. */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("spotify:playlist:")) {
            val id = trimmed.removePrefix("spotify:playlist:").substringBefore("?").substringBefore("/")
            return id.takeIf { PLAYLIST_ID.matches(it) }
        }

        val uri = runCatching { Url(trimmed) }.getOrNull() ?: return null
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) return null
        if (!isSpotifyHost(uri.host.lowercase())) return null
        val segments = uri.segments.filter { it.isNotEmpty() }
        val playlistIdx = segments.indexOf("playlist")
        if (playlistIdx == -1 || playlistIdx + 1 >= segments.size) return null
        return segments[playlistIdx + 1].takeIf { PLAYLIST_ID.matches(it) }
    }

    /**
     * Fetches public Spotify playlist details from Spotify's embed endpoint.
     */
    suspend fun fetchPlaylistTracks(playlistId: String): Pair<String, List<SpotifyImportTrack>> =
        withContext(ioDispatcher) {
            val url = "https://open.spotify.com/embed/playlist/$playlistId"
            val html = NetHttp.execute(HttpRequest(url, headers = mapOf("User-Agent" to USER_AGENT))).let { response ->
                val body = response.text()
                if (!response.isSuccessful || body.isBlank()) {
                    error("Failed to load Spotify playlist (HTTP ${response.code})")
                }
                body
            }

            // Extract <script id="__NEXT_DATA__" type="application/json">...</script>
            val scriptRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val match = scriptRegex.find(html)
                ?: error("Could not parse Spotify playlist metadata. Make sure the playlist is public.")

            val jsonString = match.groupValues[1]
            val root = json.parseToJsonElement(jsonString).jsonObject

            val props = root["props"]?.jsonObject
                ?.get("pageProps")?.jsonObject
            val stateData = props?.get("state")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("entity")?.jsonObject
                ?: props?.get("entity")?.jsonObject
                ?: error("Spotify playlist entity missing or private")

            val playlistTitle = stateData["name"]?.jsonPrimitive?.content
                ?: stateData["title"]?.jsonPrimitive?.content
                ?: "Imported Spotify Playlist"

            val trackListJson = stateData["trackList"]?.jsonArray
                ?: stateData["tracks"]?.jsonArray
                ?: JsonArray(emptyList())

            val tracks = mutableListOf<SpotifyImportTrack>()
            for (element in trackListJson) {
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content
                    ?: continue
                val subtitle = obj["subtitle"]?.jsonPrimitive?.content
                    ?: obj["artists"]?.jsonArray?.joinToString(", ") {
                        it.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
                    }
                    ?: ""
                if (title.isNotBlank()) {
                    tracks.add(SpotifyImportTrack(title = title.trim(), artist = subtitle.trim()))
                }
            }

            if (tracks.isEmpty()) {
                error("No tracks found in public Spotify playlist.")
            }

            // The embed page stops at its first 100 tracks. The web player's
            // own query, with the anonymous token the embed hands out, pages
            // through the rest; if it refuses, the 100 already read stand.
            if (tracks.size >= EMBED_TRACK_LIMIT) {
                val token = props?.get("state")?.jsonObject
                    ?.get("settings")?.jsonObject
                    ?.get("session")?.jsonObject
                    ?.get("accessToken")?.jsonPrimitive?.content
                if (token != null) {
                    runCatching { fetchRemainingTracks(playlistId, token, tracks.size) }
                        .getOrNull()
                        ?.let { tracks.addAll(it) }
                }
            }

            Pair(playlistTitle, tracks)
        }

    /**
     * The YouTube Music song that is [track], or null if there is none: the
     * best candidate by title, artist, length and album, and failing that the
     * search's first hit.
     */
    suspend fun matchTrack(track: SpotifyTrack): Song? {
        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        val candidates = YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
            ?.filterIsInstance<SearchResult.Track>()
            ?.map { it.song }
            .orEmpty()
        return TrackMatcher.best(
            candidates,
            TrackMatcher.Target(
                title = track.title,
                artist = track.artist,
                durationSec = track.durationMs.takeIf { it > 0 }?.div(1000),
                album = track.album,
            ),
        ) ?: candidates.firstOrNull()
    }

    /** Tracks the embed page lists before it cuts off. */
    private const val EMBED_TRACK_LIMIT = 100
    private const val PAGE_SIZE = 100
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val FETCH_PLAYLIST_HASH = "19ff1327c29e99c208c86d7a9d8f1929cfdf3d3202a0ff4253c821f1901aa94d"

    /** Tracks from [start] to the end of the playlist, a page at a time. */
    private fun fetchRemainingTracks(
        playlistId: String,
        token: String,
        start: Int,
    ): List<SpotifyImportTrack> {
        val out = mutableListOf<SpotifyImportTrack>()
        var offset = start
        while (true) {
            val variables = """{"uri":"spotify:playlist:$playlistId","offset":$offset,"limit":$PAGE_SIZE,"enableWatchFeedEntrypoint":false}"""
            val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$FETCH_PLAYLIST_HASH"}}"""
            val url = URLBuilder(PATHFINDER_URL).apply {
                param("operationName", "fetchPlaylist")
                param("variables", variables)
                param("extensions", extensions)
            }.buildString()
            val request = HttpRequest(
                url,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Authorization" to "Bearer $token",
                    "App-platform" to "WebPlayer",
                    "Accept" to "application/json",
                ),
            )
            val body = NetHttp.execute(request).let { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.text()
            }
            val content = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
                ?.get("playlistV2")?.jsonObject?.get("content")?.jsonObject
                ?: break
            val items = content["items"]?.jsonArray ?: break
            for (item in items) {
                val data = item.jsonObject["itemV2"]?.jsonObject?.get("data")?.jsonObject ?: continue
                val title = data["name"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (title.isEmpty()) continue
                val artist = data["artists"]?.jsonObject?.get("items")?.jsonArray
                    ?.mapNotNull { it.jsonObject["profile"]?.jsonObject?.get("name")?.jsonPrimitive?.content }
                    ?.joinToString(", ")
                    .orEmpty()
                out.add(SpotifyImportTrack(title = title, artist = artist))
            }
            val total = content["totalCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: break
            offset += items.size
            if (items.isEmpty() || offset >= total) break
        }
        return out
    }

    /**
     * Resolves a list of Spotify tracks to full Song objects using Innertube search.
     * Optionally filters or prioritizes matches by specified [language].
     * Reports real-time progress via [onProgress].
     */
    suspend fun resolveToSongs(
        tracks: List<SpotifyImportTrack>,
        language: String? = null,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): Pair<List<Song>, List<SpotifyImportTrack>> = coroutineScope {
        val total = tracks.size
        val completedCount = AtomicInt(0)
        val semaphore = Semaphore(4) // Bounded concurrency for search queries

        val cleanLang = language?.trim()?.takeIf {
            !it.equals("No Preference", ignoreCase = true) &&
            !it.equals("Multilingual", ignoreCase = true) &&
            it.isNotBlank()
        }

        val knownLanguages = listOf(
            "Tamil", "Telugu", "Kannada", "Malayalam", "Hindi",
            "English", "Punjabi", "Marathi", "Bengali", "Bhojpuri", "Gujarati"
        )

        val deferredResults = tracks.map { track ->
            async(ioDispatcher) {
                semaphore.withPermit {
                    val query = if (cleanLang != null) {
                        "${track.title} ${track.artist} $cleanLang".trim()
                    } else {
                        "${track.title} ${track.artist}".trim()
                    }

                    val searchResult = YtMusicRepository.search(query, SearchFilter.SONGS).getOrNull()
                    val candidates = searchResult?.filterIsInstance<SearchResult.Track>()?.map { it.song }.orEmpty()

                    val target = TrackMatcher.Target(
                        title = track.title,
                        artist = track.artist,
                    )
                    val bestMatch = TrackMatcher.best(candidates, target) ?: candidates.firstOrNull()

                    val matchedSong = if (cleanLang != null && candidates.isNotEmpty()) {
                        val conflictingLangs = knownLanguages.filter { !it.equals(cleanLang, ignoreCase = true) }
                        val nonConflictingCandidates = candidates.filter { candidate ->
                            val meta = "${candidate.title} ${candidate.artist} ${candidate.albumName.orEmpty()}".lowercase()
                            !conflictingLangs.any {
                                meta.contains("(${it.lowercase()})") ||
                                meta.contains("${it.lowercase()} version") ||
                                meta.contains("${it.lowercase()} song")
                            }
                        }
                        TrackMatcher.best(nonConflictingCandidates, target)
                            ?: nonConflictingCandidates.firstOrNull()
                            ?: bestMatch
                    } else {
                        bestMatch
                    }

                    val done = completedCount.addAndFetch(1)
                    onProgress(done, total)

                    if (matchedSong != null) {
                        Pair(matchedSong, null)
                    } else {
                        Pair(null, track)
                    }
                }
            }
        }

        val results = deferredResults.awaitAll()
        val songs = results.mapNotNull { it.first }
        val unmatched = results.mapNotNull { it.second }

        Pair(songs, unmatched)
    }
}
