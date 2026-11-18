package moe.rgsekai.sekaitune.car

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.HideVideoSongsKey
import moe.rgsekai.sekaitune.constants.SongSortType
import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.Album
import moe.rgsekai.sekaitune.db.entities.Artist
import moe.rgsekai.sekaitune.db.entities.Playlist
import moe.rgsekai.sekaitune.db.entities.Song
import moe.rgsekai.sekaitune.extensions.filterVideoSongs
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.AlbumItem
import moe.rgsekai.sekaitune.innertube.models.PlaylistItem
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint
import moe.rgsekai.sekaitune.innertube.models.YTItem
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.models.toMediaMetadata
import moe.rgsekai.sekaitune.playback.DownloadUtil
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.playback.queues.Queue
import moe.rgsekai.sekaitune.playback.queues.YouTubeQueue
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// a song as the car shows it and plays it
class CarSong(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?,
    val item: MediaItem,
    val metadata: MediaMetadata,
    val endpoint: WatchEndpoint? = null,
)

// a list the car can open: a playlist, an album, an artist's songs
class CarCollection(
    val key: String,
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val round: Boolean = false,
    val songs: suspend () -> List<CarSong>,
)

// one home category: its songs to play and the lists to open
class CarShelf(
    val title: String,
    val thumbnail: String?,
    val songs: List<CarSong>,
    val collections: List<CarCollection>,
)

// where every car screen gets its songs and lists, all read off the main thread
class CarCatalog(
    private val context: Context,
    private val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
) {
    private val hideVideoSongs: Boolean get() = context.dataStore.get(HideVideoSongsKey, false)

    suspend fun quickPicks(): List<CarSong> = io {
        val monthAgo = System.currentTimeMillis() - MonthMs
        val top = database.mostPlayedSongs(fromTimeStamp = monthAgo, limit = QuickPicks).first().filterVideoSongs(hideVideoSongs)
        val picks = top.ifEmpty { database.events().first().map { it.song }.filterVideoSongs(hideVideoSongs).take(QuickPicks) }
        picks.distinctBy { it.id }.map { it.toCarSong() }
    }

    suspend fun shelves(): List<CarShelf> = io {
        val home = YouTube.home().getOrNull()
        val sections = home?.sections.orEmpty().map { Triple(it.title.orEmpty(), it.thumbnail, it.items) }
        sections.mapNotNull { (title, thumbnail, items) -> shelf(title, thumbnail, items) }
    }

    suspend fun recentSongs(): List<CarSong> = io {
        database.events().first().map { it.song }.filterVideoSongs(hideVideoSongs).distinctBy { it.id }.take(RecentSongs).map { it.toCarSong() }
    }

    suspend fun recentCollections(): List<CarCollection> = io {
        val albums = database.albumsByCreateDateAsc().first().asReversed().take(RecentLists).map { it.toCollection() }
        val playlists = database.playlistsByCreateDateAsc().first().asReversed().take(RecentLists).map { it.toCollection() }
        albums + playlists
    }

    suspend fun likedCount(): Int = io { database.likedSongsCount().first() }

    suspend fun downloadedCount(): Int = io {
        downloadUtil.downloads.value.values.count { it.state == Download.STATE_COMPLETED }
    }

    suspend fun liked(): List<CarSong> = io { database.likedSongs(SongSortType.CREATE_DATE, true).first().filterVideoSongs(hideVideoSongs).map { it.toCarSong() } }

    suspend fun downloaded(): List<CarSong> = io {
        val completed = downloadUtil.downloads.value.filterValues { it.state == Download.STATE_COMPLETED }
        if (completed.isEmpty()) return@io emptyList()
        val songs = database.getSongsByIds(completed.keys.toList())
        songs
            .map { it to completed[it.id] }
            .sortedBy { it.second?.updateTimeMs ?: 0L }
            .map { it.first }
            .filterVideoSongs(hideVideoSongs)
            .map { it.toCarSong() }
    }

    suspend fun playlists(): List<CarCollection> = io { database.playlistsByCreateDateAsc().first().asReversed().map { it.toCollection() } }

    suspend fun albums(): List<CarCollection> = io { database.albumsByCreateDateAsc().first().asReversed().map { it.toCollection() } }

    suspend fun artists(): List<CarCollection> = io { database.artistsByCreateDateAsc().first().asReversed().map { it.toCollection() } }

    fun radio(song: CarSong): Queue = YouTubeQueue(song.endpoint ?: WatchEndpoint(videoId = song.id), song.metadata)

    fun listFrom(title: String, songs: List<CarSong>, index: Int): Queue =
        ListQueue(title = title, items = songs.map { it.item }, startIndex = index.coerceIn(0, (songs.size - 1).coerceAtLeast(0)))

    private fun shelf(title: String, thumbnail: String?, items: List<YTItem>): CarShelf? {
        val songs = items.filterIsInstance<SongItem>().map { it.toCarSong() }
        val collections = items.mapNotNull { item ->
            when (item) {
                is AlbumItem -> item.toCollection()
                is PlaylistItem -> item.toCollection()
                else -> null
            }
        }
        if (songs.isEmpty() && collections.isEmpty()) return null
        return CarShelf(title, thumbnail ?: items.firstNotNullOfOrNull { it.thumbnail }, songs, collections)
    }

    private fun Song.toCarSong() = CarSong(
        id = id,
        title = song.title,
        subtitle = artists.joinToString { it.name },
        thumbnail = song.thumbnailUrl,
        item = toMediaItem(),
        metadata = toMediaMetadata(),
    )

    private fun SongItem.toCarSong() = CarSong(
        id = id,
        title = title,
        subtitle = artists.joinToString { it.name },
        thumbnail = thumbnail,
        item = toMediaItem(),
        metadata = toMediaMetadata(),
        endpoint = endpoint,
    )

    private fun Playlist.toCollection() = CarCollection(
        key = "playlist/$id",
        title = playlist.name,
        subtitle = context.resources.getQuantityString(R.plurals.n_song, songCount, songCount),
        thumbnail = playlist.thumbnailUrl ?: songThumbnails.firstOrNull { !it.isNullOrBlank() },
    ) { io { database.playlistSongs(id).first().map { it.song.toCarSong() } } }

    private fun Album.toCollection() = CarCollection(
        key = "album/$id",
        title = album.title,
        subtitle = artists.joinToString { it.name },
        thumbnail = album.thumbnailUrl,
    ) { io { database.albumSongs(id).first().map { it.toCarSong() } } }

    private fun Artist.toCollection() = CarCollection(
        key = "artist/$id",
        title = artist.name,
        subtitle = context.resources.getQuantityString(R.plurals.n_song, songCount, songCount),
        thumbnail = artist.thumbnailUrl,
        round = true,
    ) { io { database.artistSongsByCreateDateAsc(id).first().asReversed().map { it.toCarSong() } } }

    private fun AlbumItem.toCollection() = CarCollection(
        key = "yt-album/$browseId",
        title = title,
        subtitle = artists?.joinToString { it.name },
        thumbnail = thumbnail,
    ) { io { YouTube.album(browseId).getOrNull()?.songs.orEmpty().map { it.toCarSong() } } }

    private fun PlaylistItem.toCollection() = CarCollection(
        key = "yt-playlist/$id",
        title = title,
        subtitle = author?.name,
        thumbnail = thumbnail,
    ) { io { YouTube.playlist(id).getOrNull()?.songs.orEmpty().map { it.toCarSong() } } }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val MonthMs = 30L * 24 * 60 * 60 * 1000
        const val QuickPicks = 20
        const val RecentSongs = 30
        const val RecentLists = 6
    }
}

