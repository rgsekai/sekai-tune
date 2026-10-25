/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.webdav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.ArtistItem
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.playback.sources.TrackMatcher
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Resolves YouTube Music seeds for WebDAV tracks to power Auto-play endless recommendations
 * when the WebDAV playback queue nears its end.
 */
object WebDavAutoPlaySeedFinder {
    private const val TAG = "WebDavAutoPlaySeedFinder"
    private const val SEARCH_TIMEOUT_MS = 10000L
    const val NO_MATCH_SENTINEL = "__NO_MATCH__"

    // In-memory cache: webdavPath -> videoId (or NO_MATCH_SENTINEL)
    private val memoryCache = ConcurrentHashMap<String, String>()

    private val PACKAGING_REGEX = Regex(
        """[\(\[\{][^\)\]\}]*(?:official\s*(?:video|music\s*video|audio|mv|pv|visualizer)?|video|audio|mv|pv|visualizer|remaster(?:ed)?|hd|hq|4k|explicit|clean|lyric(?:s)?|audio\s*video)[^\)\]\}]*[\)\]\}]""",
        RegexOption.IGNORE_CASE,
    )

    private val FEAT_REGEX = Regex(
        """(?:\s*[\(\[\{]\s*(?:feat\.?|ft\.?|with)\b[^\)\]\}]*[\)\]\}]|\s+(?:feat\.?|ft\.?|with)\b.*$)""",
        RegexOption.IGNORE_CASE,
    )

    private val ARTIST_SUFFIX_REGEX = Regex(
        """(?i)(?:\s*-\s*topic|\s*vevo|\s*official)$""",
    )

    private val LEADING_TRACK_NUMBER_REGEX = Regex("""^(?:\d{1,3}\s*[-._]\s*|\d{1,3}\s+)""")

    /**
     * Cleans title and artist for searching YouTube Music seed tracks:
     * - Strips leading track numbers
     * - Strips packaging phrases: "(Official Video)", "(Official Music Video)", etc.
     * - Strips "ft./feat." parts
     * - Strips "- Topic", "VEVO", "Official" suffixes
     */
    fun cleanForSearch(title: String, artist: String): Pair<String, String> {
        var cleanTitle = title.trim()
        cleanTitle = cleanTitle.replace(LEADING_TRACK_NUMBER_REGEX, "")

        // Strip artist prefix if formatted like "Artist - Title"
        if (artist.isNotBlank()) {
            val prefixRegex = Regex("""^${Regex.escape(artist.trim())}\s*[-–—:]\s*""", RegexOption.IGNORE_CASE)
            cleanTitle = cleanTitle.replace(prefixRegex, "")
        }

        // Strip packaging phrases
        cleanTitle = cleanTitle.replace(PACKAGING_REGEX, " ")
        // Strip feat parts
        cleanTitle = cleanTitle.replace(FEAT_REGEX, " ")
        // Strip standalone packaging suffixes
        cleanTitle = cleanTitle.replace(ARTIST_SUFFIX_REGEX, "")
        // Clean up punctuation and whitespace
        cleanTitle = cleanTitle.replace(Regex("""[\[\]\(\)\{\}\-_.,:;!?'"~`+*/\\#@$%^&=|<>]"""), " ")
        cleanTitle = cleanTitle.replace(Regex("""\s+"""), " ").trim()

        var cleanArtist = artist.trim()
        cleanArtist = cleanArtist.replace(ARTIST_SUFFIX_REGEX, "")
        cleanArtist = cleanArtist.replace(FEAT_REGEX, " ")
        cleanArtist = cleanArtist.replace(Regex("""\s+"""), " ").trim()

        return (if (cleanTitle.isNotBlank()) cleanTitle else title.trim()) to
            (if (cleanArtist.isNotBlank()) cleanArtist else artist.trim())
    }

    /**
     * Evaluates a candidate YouTube track against target WebDAV metadata:
     * - Similar normalized title
     * - Consistent version markers (reject live/remix/cover unless target specified it)
     * - Whole-word artist overlap
     * - Duration tolerance (when both durations are known)
     */
    fun isCandidateAcceptable(
        targetTitle: String,
        targetArtist: String,
        targetDurationSec: Int?,
        candidateTitle: String,
        candidateArtists: List<String>,
        candidateDurationSec: Int?,
        toleranceSec: Int = 3,
    ): Boolean {
        // 1. Version markers check
        val targetVersions = TrackMatcher.detectVersionMarkers(targetTitle)
        val candidateVersions = TrackMatcher.detectVersionMarkers(candidateTitle)
        if (targetVersions != candidateVersions) {
            return false
        }

        // 2. Artist match check (whole-word overlap)
        val targetArtistWords = TrackMatcher.tokenizeArtistWords(listOf(targetArtist))
        val candidateArtistWords = TrackMatcher.tokenizeArtistWords(candidateArtists)
        if (targetArtistWords.isNotEmpty() && candidateArtistWords.isNotEmpty()) {
            if (targetArtistWords.intersect(candidateArtistWords).isEmpty()) {
                return false
            }
        }

        // 3. Duration tolerance check (when both known)
        if (targetDurationSec != null && targetDurationSec > 0 &&
            candidateDurationSec != null && candidateDurationSec > 0
        ) {
            val delta = abs(targetDurationSec - candidateDurationSec)
            if (delta > toleranceSec) {
                return false
            }
        }

        // 4. Title similarity check
        val normTarget = TrackMatcher.normalizeTitle(targetTitle)
        val normCandidate = TrackMatcher.normalizeTitle(candidateTitle)
        if (normTarget == normCandidate) return true

        val candidateWords = normCandidate.split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()
        val targetWords = normTarget.split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()
        if (candidateWords.isEmpty() || targetWords.isEmpty()) return false

        val common = candidateWords.intersect(targetWords).size
        val similarity = common.toFloat() / minOf(candidateWords.size, targetWords.size).toFloat()
        return similarity >= 0.6f
    }

    /**
     * Finds a YouTube seed videoId for the given WebDAV song:
     * 1. Checks memory cache and database
     * 2. Searches YouTube with cleaned `<title> <artist>`
     * 3. Selects top matching song candidate
     * 4. If no song matches, falls back to artist radio
     * 5. Caches the result (videoId or sentinel)
     */
    suspend fun findSeedVideoId(
        webdavPath: String,
        title: String,
        artist: String,
        durationSec: Int?,
        toleranceSec: Int = 3,
        database: moe.rgsekai.sekaitune.db.MusicDatabase? = null,
    ): String? = withContext(Dispatchers.IO) {
        Timber.tag(TAG).i("findSeedVideoId: path=%s, title='%s', artist='%s', duration=%s", webdavPath, title, artist, durationSec)

        // 1. Check memory cache
        val cached = memoryCache[webdavPath]
        if (cached != null && cached != NO_MATCH_SENTINEL) {
            Timber.tag(TAG).i("Returning memory-cached seed: %s", cached)
            return@withContext cached
        }

        // 2. Check Room database
        val dbMatch = database?.webDavSongDao?.getByPath(webdavPath)?.matchedCatalogId
        if (!dbMatch.isNullOrBlank() && dbMatch != NO_MATCH_SENTINEL) {
            memoryCache[webdavPath] = dbMatch
            Timber.tag(TAG).i("Returning DB-cached seed: %s", dbMatch)
            return@withContext dbMatch
        }

        // 3. Perform network search within timeout
        val seed = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            searchYouTubeForSeed(title, artist, durationSec, toleranceSec)
        }

        if (seed != null) {
            memoryCache[webdavPath] = seed
            database?.webDavSongDao?.updateMatchedCatalogId(webdavPath, seed)
            Timber.tag(TAG).i("Found and saved seed: %s", seed)
        } else {
            Timber.tag(TAG).w("No seed resolved for '%s' by '%s'", title, artist)
        }

        seed
    }

    private suspend fun searchYouTubeForSeed(
        title: String,
        artist: String,
        durationSec: Int?,
        toleranceSec: Int,
    ): String? {
        val (cleanTitle, cleanArtist) = cleanForSearch(title, artist)
        val query = "$cleanTitle $cleanArtist".trim()
        Timber.tag(TAG).i("searchYouTubeForSeed: query='%s', cleanTitle='%s', cleanArtist='%s', duration=%s", query, cleanTitle, cleanArtist, durationSec)
        if (query.isBlank()) return null

        // 1. Search for songs
        val songSearch = runCatching {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.onFailure {
            Timber.tag(TAG).e(it, "YouTube song search failed for query: %s", query)
        }.getOrNull()

        // 1. Search for songs with strict tolerance
        val candidates = songSearch?.items?.filterIsInstance<SongItem>().orEmpty()
        Timber.tag(TAG).i("YouTube song search returned %d candidates", candidates.size)
        for (candidate in candidates) {
            val candidateArtists = candidate.artists.map { it.name }
            val acceptable = isCandidateAcceptable(
                targetTitle = cleanTitle,
                targetArtist = cleanArtist,
                targetDurationSec = durationSec,
                candidateTitle = candidate.title,
                candidateArtists = candidateArtists,
                candidateDurationSec = candidate.duration,
                toleranceSec = toleranceSec,
            )
            Timber.tag(TAG).i("Candidate '%s' by %s (dur=%s): acceptable=%b", candidate.title, candidateArtists, candidate.duration, acceptable)
            if (acceptable) {
                Timber.tag(TAG).i("Accepted YouTube seed '%s' (%s) for WebDAV track '%s'", candidate.title, candidate.id, title)
                return candidate.id
            }
        }

        // 1b. Relax duration for exact title + artist match (handles music video vs studio audio discrepancy)
        if (durationSec != null) {
            for (candidate in candidates) {
                val candidateArtists = candidate.artists.map { it.name }
                val acceptable = isCandidateAcceptable(
                    targetTitle = cleanTitle,
                    targetArtist = cleanArtist,
                    targetDurationSec = null,
                    candidateTitle = candidate.title,
                    candidateArtists = candidateArtists,
                    candidateDurationSec = null,
                    toleranceSec = toleranceSec,
                )
                if (acceptable) {
                    Timber.tag(TAG).i("Accepted YouTube seed with relaxed duration '%s' (%s) for WebDAV track '%s'", candidate.title, candidate.id, title)
                    return candidate.id
                }
            }
        }

        // 2. Fall back to artist search & radio
        if (cleanArtist.isNotBlank() && cleanArtist != "Unknown Artist") {
            val artistSearch = runCatching {
                YouTube.search(cleanArtist, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
            }.onFailure {
                Timber.tag(TAG).e(it, "YouTube artist search failed for artist: %s", cleanArtist)
            }.getOrNull()

            val artistItem = artistSearch?.items?.filterIsInstance<ArtistItem>()?.firstOrNull()
            val artistRadioSeed = artistItem?.playEndpoint?.videoId
                ?: artistItem?.radioEndpoint?.videoId
                ?: artistItem?.shuffleEndpoint?.videoId
            Timber.tag(TAG).i("Artist radio fallback: artistItem=%s, seed=%s", artistItem?.title, artistRadioSeed)
            if (!artistRadioSeed.isNullOrBlank()) {
                Timber.tag(TAG).i("Falling back to artist radio seed '%s' for artist '%s'", artistRadioSeed, cleanArtist)
                return artistRadioSeed
            }

            // Fallback: Top song by artist
            val artistSongsSearch = runCatching {
                YouTube.search(cleanArtist, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            }.getOrNull()
            val topArtistSong = artistSongsSearch?.items?.filterIsInstance<SongItem>()?.firstOrNull { song ->
                val words = TrackMatcher.tokenizeArtistWords(song.artists.map { it.name })
                val targetWords = TrackMatcher.tokenizeArtistWords(listOf(cleanArtist))
                targetWords.isEmpty() || words.intersect(targetWords).isNotEmpty()
            }
            if (topArtistSong != null) {
                Timber.tag(TAG).i("Falling back to top artist song seed '%s' (%s) for artist '%s'", topArtistSong.title, topArtistSong.id, cleanArtist)
                return topArtistSong.id
            }
        }

        Timber.tag(TAG).d("No YouTube seed found for WebDAV track '%s' by '%s'", title, artist)
        return null
    }

    fun clearMemoryCache() {
        memoryCache.clear()
    }
}
