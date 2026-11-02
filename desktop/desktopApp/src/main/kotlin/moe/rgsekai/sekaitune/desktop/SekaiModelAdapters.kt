package moe.rgsekai.sekaitune.desktop

import moe.rgsekai.sekaitune.shared.data.model.ArtistPage as LegacyArtistPage
import moe.rgsekai.sekaitune.shared.data.model.ArtistRef
import moe.rgsekai.sekaitune.shared.data.model.BrowseItem
import moe.rgsekai.sekaitune.shared.data.model.BrowseType
import moe.rgsekai.sekaitune.shared.data.model.HomeFeed
import moe.rgsekai.sekaitune.shared.data.model.HomeShelf
import moe.rgsekai.sekaitune.shared.data.model.SearchResult
import moe.rgsekai.sekaitune.shared.data.model.ShelfItem
import moe.rgsekai.sekaitune.shared.data.model.Song
import moe.rgsekai.sekaitune.desktop.DesktopCollection
import moe.rgsekai.sekaitune.innertube.models.AlbumItem
import moe.rgsekai.sekaitune.innertube.models.ArtistItem
import moe.rgsekai.sekaitune.innertube.models.PlaylistItem
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.YTItem
import moe.rgsekai.sekaitune.innertube.pages.AlbumPage
import moe.rgsekai.sekaitune.innertube.pages.ArtistPage
import moe.rgsekai.sekaitune.innertube.pages.ExplorePage
import moe.rgsekai.sekaitune.innertube.pages.HomePage
import moe.rgsekai.sekaitune.innertube.pages.PlaylistPage

/**
 * Converts Sekai Tune :core data models into Sekai Tune desktop UI models.
 */
object SekaiModelAdapters {

    fun formatDuration(seconds: Int?): String? {
        if (seconds == null || seconds <= 0) return null
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }

    fun SongItem.toLegacyPlayerSong(): Song {
        val leadArtist = artists.firstOrNull()?.name.orEmpty()
        val allArtists = artists.map { ArtistRef(name = it.name, browseId = it.id) }

        return Song(
            videoId = id,
            title = title,
            artist = leadArtist.ifBlank { "Unknown Artist" },
            thumbnailUrl = thumbnail,
            durationText = formatDuration(duration),
            artistId = artists.firstOrNull()?.id,
            artists = allArtists,
            albumId = album?.id,
            albumName = album?.name,
            isVideo = false,
        )
    }

    fun AlbumItem.toBrowseItem(): BrowseItem {
        return BrowseItem(
            browseId = id,
            title = title,
            subtitle = artists?.joinToString(", ") { it.name } ?: year?.toString().orEmpty(),
            thumbnailUrl = thumbnail,
            type = BrowseType.ALBUM,
        )
    }

    fun ArtistItem.toBrowseItem(): BrowseItem {
        return BrowseItem(
            browseId = id,
            title = title,
            subtitle = "Artist",
            thumbnailUrl = thumbnail,
            type = BrowseType.ARTIST,
        )
    }

    fun PlaylistItem.toBrowseItem(): BrowseItem {
        return BrowseItem(
            browseId = id,
            title = title,
            subtitle = author?.name.orEmpty(),
            thumbnailUrl = thumbnail,
            type = BrowseType.PLAYLIST,
        )
    }

    fun YTItem.toSearchResult(): SearchResult? = when (this) {
        is SongItem -> SearchResult.Track(toLegacyPlayerSong())
        is AlbumItem -> SearchResult.Browse(toBrowseItem())
        is ArtistItem -> SearchResult.Browse(toBrowseItem())
        is PlaylistItem -> SearchResult.Browse(toBrowseItem())
        else -> null
    }

    fun AlbumPage.toDesktopCollection(browseId: String): DesktopCollection {
        val songsList = songs.map { it.toLegacyPlayerSong() }
        val durationSec = songs.sumOf { it.duration ?: 0 }
        val subtitleParts = listOfNotNull(
            album.artists?.joinToString(", ") { it.name },
            album.year?.toString(),
            if (songsList.isNotEmpty()) "${songsList.size} songs" else null,
            if (durationSec > 0) formatDuration(durationSec) else null
        )

        return DesktopCollection(
            browseId = browseId,
            title = album.title,
            subtitle = subtitleParts.joinToString(" • "),
            thumbnailUrl = album.thumbnail,
            type = BrowseType.ALBUM,
            songs = songsList,
            continuation = null,
            owned = false,
        )
    }

    fun PlaylistPage.toDesktopCollection(browseId: String): DesktopCollection {
        val songsList = songs.map { it.toLegacyPlayerSong() }
        val subtitleParts = listOfNotNull(
            playlist.author?.name,
            playlist.songCountText,
        )

        return DesktopCollection(
            browseId = browseId,
            title = playlist.title,
            subtitle = subtitleParts.joinToString(" • "),
            thumbnailUrl = playlist.thumbnail,
            type = BrowseType.PLAYLIST,
            songs = songsList,
            continuation = songsContinuation,
            owned = false,
        )
    }

    fun ArtistPage.toLegacyArtistPage(browseId: String): LegacyArtistPage {
        val topSongs = sections.flatMap { section ->
            section.items.filterIsInstance<SongItem>().map { it.toLegacyPlayerSong() }
        }
        val sectionShelves = sections.map { section ->
            HomeShelf(
                title = section.title,
                items = section.items.mapNotNull { it.toShelfItem() }
            )
        }.filter { it.items.isNotEmpty() }

        return LegacyArtistPage(
            songs = topSongs.take(10),
            moreSongsBrowseId = null,
            sections = sectionShelves,
            thumbnailUrl = artist.thumbnail,
            name = artist.title,
            description = description,
        )
    }

    fun YTItem.toShelfItem(): ShelfItem? = when (this) {
        is SongItem -> ShelfItem(
            title = title,
            subtitle = artists.joinToString(", ") { it.name },
            thumbnailUrl = thumbnail,
            videoId = id,
            browseId = null,
        )
        is AlbumItem -> ShelfItem(
            title = title,
            subtitle = artists?.joinToString(", ") { it.name } ?: year?.toString().orEmpty(),
            thumbnailUrl = thumbnail,
            videoId = null,
            browseId = id,
        )
        is PlaylistItem -> ShelfItem(
            title = title,
            subtitle = author?.name.orEmpty(),
            thumbnailUrl = thumbnail,
            videoId = null,
            browseId = id,
        )
        is ArtistItem -> ShelfItem(
            title = title,
            subtitle = "Artist",
            thumbnailUrl = thumbnail,
            videoId = null,
            browseId = id,
        )
        else -> null
    }

    fun HomePage.toHomeFeed(): HomeFeed {
        val shelves = sections.map { section ->
            HomeShelf(
                title = section.title.orEmpty(),
                items = section.items.mapNotNull { it.toShelfItem() }
            )
        }.filter { it.items.isNotEmpty() }

        return HomeFeed(
            shelves = shelves,
            continuation = continuation,
        )
    }

    fun ExplorePage.toHomeShelves(): List<HomeShelf> {
        val shelves = mutableListOf<HomeShelf>()
        if (newReleaseAlbums.isNotEmpty()) {
            shelves.add(
                HomeShelf(
                    title = "New Releases",
                    items = newReleaseAlbums.mapNotNull { it.toShelfItem() }
                )
            )
        }
        return shelves
    }
}
