/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.utils.VideoStreams
import moe.rgsekai.sekaitune.utils.YTPlayerUtils

data class OfficialMusicVideo(
    val videoId: String,
    val streams: VideoStreams,
)

@Immutable
class SongVideo(
    val videoId: String,
    val streams: VideoStreams,
    val positionAt: ((songPositionMs: Long) -> Long)? = null,
)

object YouTubeVideoUrlCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, OfficialMusicVideo?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(mediaId: String): Boolean =
        map.containsKey(mediaId) && map[mediaId]?.streams?.isExpired != true

    @Synchronized
    fun get(mediaId: String): OfficialMusicVideo? = map[mediaId]

    @Synchronized
    fun put(mediaId: String, video: OfficialMusicVideo?) {
        if (mediaId.isBlank()) return
        map[mediaId] = video
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }
}

suspend fun findOfficialMusicVideo(title: String, artist: String): OfficialMusicVideo? {
    val videoId = findOfficialMusicVideoId(title, artist)
    android.util.Log.d("VideoDebug", "Step 1b: findOfficialMusicVideoId returned videoId=$videoId for title='$title', artist='$artist'")
    if (videoId == null) return null
    val result = YTPlayerUtils.resolveVideoStreams(videoId)
    val streams = result.getOrNull()
    android.util.Log.d("VideoDebug", "Step 1b: resolveVideoStreams in findOfficialMusicVideo isSuccess=${result.isSuccess}, error=${result.exceptionOrNull()?.message}, tracks=${streams?.tracks?.size}")
    if (streams == null) return null
    return OfficialMusicVideo(videoId = videoId, streams = streams)
}

suspend fun ownMusicVideo(mediaId: String): OfficialMusicVideo? =
    YTPlayerUtils.resolveVideoStreams(mediaId).getOrNull()?.let { OfficialMusicVideo(mediaId, it) }

suspend fun findOfficialMusicVideoId(title: String, artist: String): String? {
    android.util.Log.d("VideoDebug", "Step 1b: lookup start for title='$title', artist='$artist'")
    if (title.isBlank() || artist.isBlank()) {
        android.util.Log.d("VideoDebug", "Step 1b: title or artist is blank, returning null")
        return null
    }
    val key = "$title|$artist"
    if (OfficialVideoIdCache.contains(key)) {
        val cached = OfficialVideoIdCache.get(key)
        android.util.Log.d("VideoDebug", "Step 1b: cache hit for key='$key' -> videoId=$cached")
        return cached
    }
    android.util.Log.d("VideoDebug", "Step 1b: cache miss for key='$key', searching YouTube...")

    val results = YouTube.search(query = "$title $artist", filter = YouTube.SearchFilter.FILTER_VIDEO)
        .getOrElse {
            android.util.Log.d("VideoDebug", "Step 1b: search failed with exception: ${it.message}")
            return null
        }.items

    android.util.Log.d("VideoDebug", "Step 1b: search returned ${results.size} items")

    val normalizedTitle = normalizeCanvasSongTitle(title)
    val normalizedArtist = normalizeCanvasArtistName(artist)
    android.util.Log.d("VideoDebug", "Step 1b: normalized target: title='$normalizedTitle', artist='$normalizedArtist'")

    for (item in results) {
        if (item is SongItem) {
            val config = item.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig
            val candidateTitle = normalizeCanvasSongTitle(item.title)
            val candidateArtist = normalizeCanvasArtistName(item.artists.joinToString { it.name })
            android.util.Log.d("VideoDebug", "Step 1b: candidate id=${item.id}, musicVideoType=${config?.musicVideoType}, rawTitle='${item.title}', normTitle='$candidateTitle', rawArtist='${item.artists.joinToString { it.name }}', normArtist='$candidateArtist'")
        }
    }

    val match = results
        .filterIsInstance<SongItem>()
        .filter {
            val config = it.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig
            config?.musicVideoType == MUSIC_VIDEO_TYPE_OMV
        }
        .firstOrNull { candidate ->
            val candidateTitle = normalizeCanvasSongTitle(candidate.title)
            val candidateArtist = normalizeCanvasArtistName(candidate.artists.joinToString { it.name })
            (candidateTitle.contains(normalizedTitle, ignoreCase = true) || normalizedTitle.contains(candidateTitle, ignoreCase = true)) &&
                (candidateArtist.contains(normalizedArtist, ignoreCase = true) || normalizedArtist.contains(candidateArtist, ignoreCase = true))
        }
    android.util.Log.d("VideoDebug", "Step 1b: lookup result -> match=${match?.id} (title=${match?.title})")
    OfficialVideoIdCache.put(key, match?.id)
    return match?.id
}

private object OfficialVideoIdCache {
    private const val maxSize = 64
    private val map = LinkedHashMap<String, String?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun get(key: String): String? = map[key]

    @Synchronized
    fun put(key: String, videoId: String?) {
        map[key] = videoId
        if (map.size > maxSize) map.remove(map.keys.first())
    }
}

@Immutable
class SongVideoLookup(val video: SongVideo?, val searching: Boolean)

@Composable
fun rememberSongVideo(
    mediaId: String,
    title: String,
    artist: String,
    isVideoSong: Boolean,
    enabled: Boolean,
): SongVideo? = rememberSongVideoLookup(mediaId, title, artist, isVideoSong, enabled).video

@Composable
fun rememberSongVideoLookup(
    mediaId: String,
    title: String,
    artist: String,
    isVideoSong: Boolean,
    enabled: Boolean,
): SongVideoLookup {
    val firstId = remember { mediaId }
    var found by remember(mediaId) {
        mutableStateOf(if (enabled && mediaId == firstId) cachedVideo(mediaId, isVideoSong) else null)
    }
    var searching by remember(mediaId) { mutableStateOf(enabled && found == null) }
    LaunchedEffect(mediaId, title, artist, enabled, isVideoSong) {
        if (!enabled || mediaId.isBlank()) {
            found = null
            searching = false
            return@LaunchedEffect
        }
        if (found != null) {
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(LookupSettleMs)
        found = lookUpVideo(mediaId, title, artist, isVideoSong)
        searching = false
    }
    val video = found
    val positionAt = if (video != null) rememberVideoTimeline(mediaId, video.videoId) else null
    val songVideo = remember(video, positionAt) { video?.let { SongVideo(it.videoId, it.streams, positionAt) } }
    return remember(songVideo, searching) { SongVideoLookup(songVideo, searching) }
}

@Composable
private fun rememberVideoTimeline(mediaId: String, videoId: String): ((Long) -> Long)? {
    val playerConnection = moe.rgsekai.sekaitune.LocalPlayerConnection.current ?: return null
    if (videoId == mediaId) return null
    val (lyricsSyncEnabled) = moe.rgsekai.sekaitune.utils.rememberPreference(moe.rgsekai.sekaitune.constants.YtVideoBackgroundLyricsSyncKey, defaultValue = true)
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)

    var lyricTimeline by remember(mediaId) { mutableStateOf<LyricVideoTimeline?>(null) }
    LaunchedEffect(videoId, currentLyrics?.lyrics, lyricsSyncEnabled) {
        val lyricsRaw = currentLyrics?.lyrics?.takeIf { it.isNotBlank() && it != moe.rgsekai.sekaitune.db.entities.LyricsEntity.LYRICS_NOT_FOUND }
        if (!lyricsSyncEnabled || lyricsRaw == null) {
            lyricTimeline = null
            return@LaunchedEffect
        }
        val key = "$mediaId|$videoId|${lyricsRaw.hashCode()}"
        if (LyricVideoTimelineCache.contains(key)) {
            lyricTimeline = LyricVideoTimelineCache.get(key)
            return@LaunchedEffect
        }
        val timeline = try {
            buildLyricVideoTimeline(videoId, lyricsRaw)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        LyricVideoTimelineCache.put(key, timeline)
        lyricTimeline = timeline
    }

    return remember(lyricTimeline) {
        lyricTimeline?.let { timeline ->
            { songMs: Long -> timeline.videoMsAt(songMs) }
        }
    }
}

private const val LookupSettleMs = 500L

private fun videoCacheKey(mediaId: String, isVideoSong: Boolean) = if (isVideoSong) "self:$mediaId" else mediaId

private fun cachedVideo(mediaId: String, isVideoSong: Boolean): OfficialMusicVideo? {
    val key = videoCacheKey(mediaId, isVideoSong)
    return if (YouTubeVideoUrlCache.contains(key)) YouTubeVideoUrlCache.get(key) else null
}

private val lookupLock = Mutex()

internal suspend fun lookUpVideo(mediaId: String, title: String, artist: String, isVideoSong: Boolean): OfficialMusicVideo? =
    lookupLock.withLock {
        val key = videoCacheKey(mediaId, isVideoSong)
        android.util.Log.d("VideoDebug", "Step 1b: lookUpVideo mediaId=$mediaId, key=$key, isVideoSong=$isVideoSong")
        if (YouTubeVideoUrlCache.contains(key)) {
            val cached = YouTubeVideoUrlCache.get(key)
            android.util.Log.d("VideoDebug", "Step 1b: YouTubeVideoUrlCache hit for key=$key -> videoId=${cached?.videoId}")
            return@withLock cached
        }
        android.util.Log.d("VideoDebug", "Step 1b: YouTubeVideoUrlCache miss for key=$key")
        if (!isVideoSong && (title.isBlank() || artist.isBlank())) {
            android.util.Log.d("VideoDebug", "Step 1b: not video song and title/artist blank")
            return@withLock null
        }
        val resolved = if (isVideoSong) ownMusicVideo(mediaId) else findOfficialMusicVideo(title, artist)
        android.util.Log.d("VideoDebug", "Step 1b: lookUpVideo resolved=${resolved?.videoId}")
        YouTubeVideoUrlCache.put(key, resolved)
        resolved
    }

