package moe.rgsekai.sekaitune.desktop

import moe.rgsekai.sekaitune.shared.data.model.ArtistPage as LegacyArtistPage
import moe.rgsekai.sekaitune.shared.data.model.BrowseItem
import moe.rgsekai.sekaitune.shared.data.model.HomeFeed
import moe.rgsekai.sekaitune.shared.data.model.HomeShelf
import moe.rgsekai.sekaitune.shared.data.model.LibraryPage
import moe.rgsekai.sekaitune.shared.data.model.SearchFilter
import moe.rgsekai.sekaitune.shared.data.model.SearchResult
import moe.rgsekai.sekaitune.shared.data.model.Song
import moe.rgsekai.sekaitune.desktop.DesktopCollection
import moe.rgsekai.sekaitune.desktop.DesktopPersistence
import moe.rgsekai.sekaitune.desktop.DesktopSearchClient
import moe.rgsekai.sekaitune.desktop.DesktopTrackLog
import moe.rgsekai.sekaitune.desktop.SekaiModelAdapters.toDesktopCollection
import moe.rgsekai.sekaitune.desktop.SekaiModelAdapters.toHomeFeed
import moe.rgsekai.sekaitune.desktop.SekaiModelAdapters.toHomeShelves
import moe.rgsekai.sekaitune.desktop.SekaiModelAdapters.toLegacyArtistPage
import moe.rgsekai.sekaitune.desktop.SekaiModelAdapters.toLegacyPlayerSong
import moe.rgsekai.sekaitune.desktop.SekaiModelAdapters.toSearchResult
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint
import moe.rgsekai.sekaitune.simpstream.SimpMusicPlayer

/**
 * Routes search, browse, streams, and library through Sekai Tune's :core
 * when "Use Sekai Tune engine" is enabled, falling back to Sekai Tune's client otherwise.
 */
object SekaiEngineGateway {

    const val KEY_USE_SEKAI_ENGINE = "use_sekai_engine"

    private val persistence = DesktopPersistence()

    @Volatile
    var useSekaiEngine: Boolean = persistence.boolean(KEY_USE_SEKAI_ENGINE, true)
        set(value) {
            field = value
            persistence.saveBoolean(KEY_USE_SEKAI_ENGINE, value)
            log("setting updated: useSekaiEngine=$value (saved in key '$KEY_USE_SEKAI_ENGINE')")
        }

    init {
        log("initialized at startup: useSekaiEngine=$useSekaiEngine (read from Preferences key '$KEY_USE_SEKAI_ENGINE')")
    }

    private fun log(message: String) {
        DesktopTrackLog.log("[engine] $message")
        println("[engine] $message")
    }

    suspend fun search(
        query: String,
        filter: SearchFilter = SearchFilter.SONGS,
    ): Result<List<SearchResult>> {
        if (!useSekaiEngine) {
            log("op=search used=fallback reason=useSekaiEngine_off")
            return DesktopSearchClient.search(query, filter)
        }

        return runCatching {
            val coreFilter = when (filter) {
                SearchFilter.SONGS -> YouTube.SearchFilter.FILTER_SONG
                SearchFilter.VIDEOS -> YouTube.SearchFilter.FILTER_VIDEO
                SearchFilter.ALBUMS -> YouTube.SearchFilter.FILTER_ALBUM
                SearchFilter.ARTISTS -> YouTube.SearchFilter.FILTER_ARTIST
                SearchFilter.PLAYLISTS -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                SearchFilter.ALL -> null
            }

            if (coreFilter == null) {
                val summary = YouTube.searchSummary(query).getOrThrow()
                val items = summary.summaries.flatMap { it.items }
                val mapped = items.mapNotNull { it.toSearchResult() }
                log("op=search used=sekai count=${mapped.size}")
                mapped
            } else {
                val searchRes = YouTube.search(query, coreFilter, useAccountContext = true).getOrThrow()
                val mapped = searchRes.items.mapNotNull { it.toSearchResult() }
                log("op=search used=sekai filter=$filter count=${mapped.size}")
                mapped
            }
        }.recoverCatching { err ->
            val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
            log("op=search used=fallback reason=$reason")
            err.printStackTrace()
            DesktopSearchClient.search(query, filter).getOrThrow()
        }
    }

    suspend fun searchSuggestions(query: String): Result<List<String>> {
        if (!useSekaiEngine) {
            log("op=searchSuggestions used=fallback reason=useSekaiEngine_off")
            return DesktopSearchClient.searchSuggestions(query)
        }

        return YouTube.searchSuggestions(query).map {
            log("op=searchSuggestions used=sekai count=${it.queries.size}")
            it.queries
        }.recoverCatching { err ->
            val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
            log("op=searchSuggestions used=fallback reason=$reason")
            DesktopSearchClient.searchSuggestions(query).getOrThrow()
        }
    }

    suspend fun searchTypeahead(query: String): Result<List<SearchResult>> {
        return DesktopSearchClient.searchTypeahead(query)
    }

    suspend fun home(): Result<HomeFeed> {
        if (!useSekaiEngine) {
            log("op=home used=fallback reason=useSekaiEngine_off")
            return DesktopSearchClient.home()
        }

        return runCatching {
            val homePage = YouTube.home().getOrThrow()
            val feed = homePage.toHomeFeed()
            log("op=home used=sekai shelves=${feed.shelves.size}")
            feed
        }.recoverCatching { err ->
            val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
            log("op=home used=fallback reason=$reason")
            err.printStackTrace()
            DesktopSearchClient.home().getOrThrow()
        }
    }

    suspend fun moreHome(token: String): Result<HomeFeed> {
        if (!useSekaiEngine) {
            log("op=moreHome used=fallback reason=useSekaiEngine_off")
            return DesktopSearchClient.moreHome(token)
        }

        return runCatching {
            val homePage = YouTube.home(continuation = token).getOrThrow()
            val feed = homePage.toHomeFeed()
            log("op=moreHome used=sekai shelves=${feed.shelves.size}")
            feed
        }.recoverCatching { err ->
            val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
            log("op=moreHome used=fallback reason=$reason")
            err.printStackTrace()
            DesktopSearchClient.moreHome(token).getOrThrow()
        }
    }

    suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>> {
        if (!useSekaiEngine) {
            log("op=homeSupplement used=fallback reason=useSekaiEngine_off")
            return moe.rgsekai.sekaitune.shared.data.YtMusicRepository.homeSupplement(browseId)
        }

        if (browseId == "FEmusic_explore") {
            return runCatching {
                val explore = YouTube.explore().getOrThrow()
                val shelves = explore.toHomeShelves()
                log("op=homeSupplement(explore) used=sekai shelves=${shelves.size}")
                shelves
            }.recoverCatching { err ->
                val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
                log("op=homeSupplement(explore) used=fallback reason=$reason")
                err.printStackTrace()
                moe.rgsekai.sekaitune.shared.data.YtMusicRepository.homeSupplement(browseId).getOrThrow()
            }
        }

        return moe.rgsekai.sekaitune.shared.data.YtMusicRepository.homeSupplement(browseId)
    }

    suspend fun browse(browseId: String, fallback: BrowseItem? = null): Result<DesktopCollection> {
        if (!useSekaiEngine) {
            log("op=album/browse used=fallback reason=useSekaiEngine_off")
            return DesktopSearchClient.browse(browseId, fallback)
        }

        return runCatching {
            when {
                browseId.startsWith("MPREb_") || browseId.startsWith("FEmusic_library_album_") -> {
                    val albumPage = YouTube.album(browseId, withSongs = true).getOrThrow()
                    val collection = albumPage.toDesktopCollection(browseId)
                    log("op=album used=sekai songs=${collection.songs.size}")
                    collection
                }
                browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RDCLAK5uy_") -> {
                    val playlistId = browseId.removePrefix("VL")
                    val playlistPage = YouTube.playlist(playlistId).getOrThrow()
                    val collection = playlistPage.toDesktopCollection(browseId)
                    log("op=playlist used=sekai songs=${collection.songs.size}")
                    collection
                }
                else -> {
                    val album = YouTube.album(browseId, withSongs = true).getOrNull()
                    if (album != null) {
                        val collection = album.toDesktopCollection(browseId)
                        log("op=album(fallback_detect) used=sekai songs=${collection.songs.size}")
                        collection
                    } else {
                        val playlist = YouTube.playlist(browseId).getOrNull()
                        if (playlist != null) {
                            val collection = playlist.toDesktopCollection(browseId)
                            log("op=playlist(fallback_detect) used=sekai songs=${collection.songs.size}")
                            collection
                        } else {
                            log("op=browse used=fallback reason=unrecognized_browseId")
                            DesktopSearchClient.browse(browseId, fallback).getOrThrow()
                        }
                    }
                }
            }
        }.recoverCatching { err ->
            val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
            log("op=album/browse used=fallback reason=$reason")
            err.printStackTrace()
            DesktopSearchClient.browse(browseId, fallback).getOrThrow()
        }
    }

    suspend fun moreCollectionSongs(
        token: String,
        artistFallback: String? = null,
    ): Result<Pair<List<Song>, String?>> {
        if (!useSekaiEngine) {
            return DesktopSearchClient.moreCollectionSongs(token, artistFallback)
        }

        return runCatching {
            val cont = YouTube.playlistContinuation(token).getOrThrow()
            val songs = cont.songs.map { it.toLegacyPlayerSong() }
            songs to cont.continuation
        }.recoverCatching {
            DesktopSearchClient.moreCollectionSongs(token, artistFallback).getOrThrow()
        }
    }

    suspend fun artistPage(browseId: String): Result<LegacyArtistPage> {
        if (!useSekaiEngine) {
            log("op=artist used=fallback reason=useSekaiEngine_off")
            return DesktopSearchClient.artistPage(browseId)
        }

        return runCatching<LegacyArtistPage> {
            val artist = YouTube.artist(browseId).getOrThrow()
            val page = artist.toLegacyArtistPage(browseId)
            log("op=artist used=sekai")
            page
        }.recoverCatching { err ->
            val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
            log("op=artist used=fallback reason=$reason")
            err.printStackTrace()
            DesktopSearchClient.artistPage(browseId).getOrThrow()
        }
    }

    suspend fun radio(videoId: String): Result<List<Song>> {
        if (!useSekaiEngine) {
            return DesktopSearchClient.radio(videoId)
        }

        return runCatching {
            val nextResult = YouTube.next(WatchEndpoint(videoId = videoId)).getOrThrow()
            val songs = nextResult.items?.filterIsInstance<SongItem>()?.map { it.toLegacyPlayerSong() }
                ?: emptyList()
            if (songs.isEmpty()) {
                DesktopSearchClient.radio(videoId).getOrThrow()
            } else {
                log("op=radio used=sekai count=${songs.size}")
                songs
            }
        }.recoverCatching { err ->
            val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
            log("op=radio used=fallback reason=$reason")
            DesktopSearchClient.radio(videoId).getOrThrow()
        }
    }

    suspend fun library(): Result<LibraryPage> {
        return DesktopSearchClient.library()
    }
}
