/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.spotify

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.ArtistItem
import moe.rgsekai.sekaitune.innertube.models.SongItem
import timber.log.Timber
import java.text.Normalizer

sealed interface SpotifyArtistResolution {
    data class Matched(
        val spotifyArtistId: String,
        val saved: Boolean,
    ) : SpotifyArtistResolution

    data object NoMatch : SpotifyArtistResolution
}

sealed interface YtmArtistResolution {
    data class Matched(
        val channelId: String,
    ) : YtmArtistResolution

    data object NoMatch : YtmArtistResolution
}

object SpotifyArtistResolver {
    private const val CACHE_MAX_SIZE = 256

    private val cache =
        object : LinkedHashMap<String, SpotifyArtistResolution>(CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, SpotifyArtistResolution>?,
            ): Boolean = size > CACHE_MAX_SIZE
        }

    private val spotifyToYtmCache =
        object : LinkedHashMap<String, YtmArtistResolution>(CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, YtmArtistResolution>?,
            ): Boolean = size > CACHE_MAX_SIZE
        }

    fun getCached(channelId: String): SpotifyArtistResolution? =
        synchronized(cache) {
            cache[channelId]
        }

    fun getCachedYtm(spotifyArtistId: String): YtmArtistResolution? =
        synchronized(spotifyToYtmCache) {
            spotifyToYtmCache[spotifyArtistId]
        }

    fun seedSpotifyToYtm(
        spotifyArtistId: String,
        channelId: String,
    ) {
        synchronized(spotifyToYtmCache) {
            spotifyToYtmCache[spotifyArtistId] = YtmArtistResolution.Matched(channelId)
        }
    }

    fun updateCachedFollowState(
        spotifyArtistId: String,
        saved: Boolean,
    ) {
        synchronized(cache) {
            for ((key, value) in cache.entries) {
                if (value is SpotifyArtistResolution.Matched && value.spotifyArtistId == spotifyArtistId) {
                    cache[key] = value.copy(saved = saved)
                }
            }
        }
    }

    suspend fun resolveToSpotifyArtist(
        channelId: String,
        ytmName: String,
        ytmTopSongTitles: List<String>,
    ): SpotifyArtistResolution =
        withContext(Dispatchers.IO) {
            val cached = synchronized(cache) { cache[channelId] }
            if (cached != null) return@withContext cached

            if (ytmName.isBlank() || ytmTopSongTitles.isEmpty()) {
                val noMatch = SpotifyArtistResolution.NoMatch
                synchronized(cache) { cache[channelId] = noMatch }
                return@withContext noMatch
            }

            val normYtmName = normalizeArtistName(ytmName)
            if (normYtmName.isBlank()) {
                val noMatch = SpotifyArtistResolution.NoMatch
                synchronized(cache) { cache[channelId] = noMatch }
                return@withContext noMatch
            }

            val searchResult =
                Spotify
                    .search(query = ytmName, types = listOf("artist"), limit = 3)
                    .getOrNull()
            val candidates = searchResult?.artists?.items.orEmpty().take(3)
            if (candidates.isEmpty()) {
                val noMatch = SpotifyArtistResolution.NoMatch
                synchronized(cache) { cache[channelId] = noMatch }
                return@withContext noMatch
            }

            val normalizedYtmSongs =
                ytmTopSongTitles
                    .map { normalizeSongTitle(it) }
                    .filter { it.isNotBlank() }

            for (candidate in candidates) {
                val normCandidateName = normalizeArtistName(candidate.name)
                if (normCandidateName != normYtmName) {
                    continue
                }

                val rawOverview = Spotify.queryArtistOverviewRaw(candidate.id).getOrNull() ?: continue
                val dataObj = rawOverview["data"] as? JsonObject ?: continue
                val artistUnion = dataObj["artistUnion"] as? JsonObject ?: continue

                val topTracksItems =
                    (artistUnion["discography"] as? JsonObject)
                        ?.get("topTracks")
                        ?.let { if (it is JsonObject) it else null }
                        ?.get("items") as? JsonArray ?: JsonArray(emptyList())

                val spotifyTopTrackTitles =
                    topTracksItems.mapNotNull { item ->
                        (item as? JsonObject)
                            ?.get("track")
                            ?.let { if (it is JsonObject) it else null }
                            ?.get("name")
                            ?.let { (it as? JsonPrimitive)?.contentOrNull }
                    }

                val normalizedSpotifySongs =
                    spotifyTopTrackTitles
                        .map { normalizeSongTitle(it) }
                        .filter { it.isNotBlank() }

                val hasSongMatch =
                    normalizedYtmSongs.any { ytmSong ->
                        normalizedSpotifySongs.contains(ytmSong)
                    }

                if (!hasSongMatch) {
                    continue
                }

                val savedFromGql = (artistUnion["saved"] as? JsonPrimitive)?.booleanOrNull
                val isSaved =
                    savedFromGql ?: run {
                        checkFollowedInLibraryV3(candidate.id)
                    }

                val matched =
                    SpotifyArtistResolution.Matched(
                        spotifyArtistId = candidate.id,
                        saved = isSaved,
                    )
                synchronized(cache) { cache[channelId] = matched }
                seedSpotifyToYtm(candidate.id, channelId)
                return@withContext matched
            }

            val noMatch = SpotifyArtistResolution.NoMatch
            synchronized(cache) { cache[channelId] = noMatch }
            noMatch
        }

    suspend fun resolveSpotifyToYtmArtist(
        spotifyArtistId: String,
        spotifyArtistName: String,
    ): YtmArtistResolution =
        withContext(Dispatchers.IO) {
            val cached = synchronized(spotifyToYtmCache) { spotifyToYtmCache[spotifyArtistId] }
            if (cached != null) return@withContext cached

            if (spotifyArtistId.isBlank() || spotifyArtistName.isBlank()) {
                val noMatch = YtmArtistResolution.NoMatch
                synchronized(spotifyToYtmCache) { spotifyToYtmCache[spotifyArtistId] = noMatch }
                return@withContext noMatch
            }

            val normSpotifyName = normalizeArtistName(spotifyArtistName)
            if (normSpotifyName.isBlank()) {
                val noMatch = YtmArtistResolution.NoMatch
                synchronized(spotifyToYtmCache) { spotifyToYtmCache[spotifyArtistId] = noMatch }
                return@withContext noMatch
            }

            val searchResult =
                YouTube
                    .search(
                        query = spotifyArtistName,
                        filter = YouTube.SearchFilter.FILTER_ARTIST,
                    ).getOrNull()

            val ytmCandidates =
                searchResult?.items
                    ?.filterIsInstance<ArtistItem>()
                    .orEmpty()
                    .take(3)

            if (ytmCandidates.isEmpty()) {
                val noMatch = YtmArtistResolution.NoMatch
                synchronized(spotifyToYtmCache) { spotifyToYtmCache[spotifyArtistId] = noMatch }
                return@withContext noMatch
            }

            val spotifyTopTracks =
                Spotify.artistTopTracks(spotifyArtistId).getOrNull()?.tracks.orEmpty()
            val normalizedSpotifySongs =
                spotifyTopTracks
                    .map { normalizeSongTitle(it.name) }
                    .filter { it.isNotBlank() }

            for (candidate in ytmCandidates) {
                val normCandidateName = normalizeArtistName(candidate.title)
                if (normCandidateName != normSpotifyName) {
                    continue
                }

                val candidatePage = YouTube.artist(candidate.id).getOrNull()
                val ytmSongs =
                    candidatePage?.sections
                        ?.flatMap { it.items }
                        ?.filterIsInstance<SongItem>()
                        ?.map { normalizeSongTitle(it.title) }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()

                val hasOverlap =
                    if (normalizedSpotifySongs.isEmpty() || ytmSongs.isEmpty()) {
                        false
                    } else {
                        normalizedSpotifySongs.any { spotifySong -> ytmSongs.contains(spotifySong) }
                    }

                if (hasOverlap) {
                    val matched = YtmArtistResolution.Matched(candidate.id)
                    synchronized(spotifyToYtmCache) {
                        spotifyToYtmCache[spotifyArtistId] = matched
                    }
                    return@withContext matched
                }
            }

            val noMatch = YtmArtistResolution.NoMatch
            synchronized(spotifyToYtmCache) { spotifyToYtmCache[spotifyArtistId] = noMatch }
            noMatch
        }

    private suspend fun checkFollowedInLibraryV3(artistId: String): Boolean {
        var offset = 0
        val limit = 50
        while (true) {
            val page = Spotify.myArtists(limit = limit, offset = offset).getOrNull() ?: break
            if (page.items.isEmpty()) break
            if (page.items.any { it.id == artistId || it.uri == "spotify:artist:$artistId" }) {
                return true
            }
            offset += page.items.size
            if (offset >= page.total || page.items.size < limit) break
        }
        return false
    }

    fun normalizeArtistName(name: String): String =
        Normalizer
            .normalize(name, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .lowercase()
            .replace(NON_ALNUM_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    fun normalizeSongTitle(title: String): String =
        Normalizer
            .normalize(title, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .lowercase()
            .replace(FEAT_PATTERN, "")
            .replace(FT_PATTERN, "")
            .replace(BRACKET_PATTERN, "")
            .replace(REMASTER_PATTERN, "")
            .replace(REMIX_PATTERN, "")
            .replace(NON_ALNUM_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val FEAT_PATTERN = Regex("\\(feat\\..*?\\)", RegexOption.IGNORE_CASE)
    private val FT_PATTERN = Regex("\\(ft\\..*?\\)", RegexOption.IGNORE_CASE)
    private val BRACKET_PATTERN = Regex("\\[.*?]", RegexOption.IGNORE_CASE)
    private val REMASTER_PATTERN = Regex("\\(.*?remaster.*?\\)", RegexOption.IGNORE_CASE)
    private val REMIX_PATTERN = Regex("\\(.*?remix.*?\\)", RegexOption.IGNORE_CASE)
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9\\s]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
}
