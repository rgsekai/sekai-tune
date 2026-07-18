package moe.rgsekai.sekaitune.innertube

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import moe.rgsekai.sekaitune.innertube.models.AccountChannel
import moe.rgsekai.sekaitune.innertube.models.AccountInfo
import moe.rgsekai.sekaitune.innertube.models.AlbumItem
import moe.rgsekai.sekaitune.innertube.models.BrowseEndpoint
import moe.rgsekai.sekaitune.innertube.models.MediaInfo
import moe.rgsekai.sekaitune.innertube.models.SearchSuggestions
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient
import moe.rgsekai.sekaitune.innertube.models.YouTubeLocale
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse
import moe.rgsekai.sekaitune.innertube.pages.AlbumPage
import moe.rgsekai.sekaitune.innertube.pages.ArtistItemsContinuationPage
import moe.rgsekai.sekaitune.innertube.pages.ArtistItemsPage
import moe.rgsekai.sekaitune.innertube.pages.ArtistPage
import moe.rgsekai.sekaitune.innertube.pages.BrowseResult
import moe.rgsekai.sekaitune.innertube.pages.ChartsPage
import moe.rgsekai.sekaitune.innertube.pages.ExplorePage
import moe.rgsekai.sekaitune.innertube.pages.HistoryPage
import moe.rgsekai.sekaitune.innertube.pages.HomePage
import moe.rgsekai.sekaitune.innertube.pages.LibraryContinuationPage
import moe.rgsekai.sekaitune.innertube.pages.LibraryPage
import moe.rgsekai.sekaitune.innertube.pages.MoodAndGenres
import moe.rgsekai.sekaitune.innertube.pages.NextResult
import moe.rgsekai.sekaitune.innertube.pages.PlaylistContinuationPage
import moe.rgsekai.sekaitune.innertube.pages.PlaylistPage
import moe.rgsekai.sekaitune.innertube.pages.RelatedPage
import moe.rgsekai.sekaitune.innertube.pages.SearchResult
import moe.rgsekai.sekaitune.innertube.pages.SearchSummaryPage
import moe.rgsekai.sekaitune.innertube.proxy.RotatingProxyClient
import okhttp3.Dns
import java.net.Proxy

interface MusicBackend {
    val authStateFlow: StateFlow<PlaybackAuthState>
    val historySyncEvent: SharedFlow<Unit>
    val ipRotationActiveCount: StateFlow<Int>

    var authState: PlaybackAuthState
    var locale: YouTubeLocale
    var visitorData: String?
    var dataSyncId: String?
    var cookie: String?
    var poToken: String?
    var webClientPoTokenEnabled: Boolean
    var poTokenGvs: String?
    var poTokenPlayer: String?
    var proxy: Proxy?
    var proxyUsername: String?
    var proxyPassword: String?
    var dns: Dns
    var streamBypassProxy: Boolean
    var useLoginForBrowse: Boolean

    val streamProxy: Proxy?
    val streamOkHttpProxy: Proxy
    val rotatingProxyClient: RotatingProxyClient

    fun notifyHistorySynced()

    fun currentPlaybackAuthState(): PlaybackAuthState

    fun hasLoginCookie(): Boolean

    fun hasPlaybackLoginContext(): Boolean

    suspend fun enableIpRotation()

    suspend fun refreshIpRotation()

    fun disableIpRotation()

    fun createDnsOverHttps(url: String): Dns

    // Search
    suspend fun searchSuggestions(query: String): Result<SearchSuggestions>

    suspend fun searchSummary(query: String): Result<SearchSummaryPage>

    suspend fun search(
        query: String,
        filter: SearchFilter,
        useAccountContext: Boolean = true,
    ): Result<SearchResult>

    suspend fun searchContinuation(
        continuation: String,
        useAccountContext: Boolean = true,
    ): Result<SearchResult>

    // Browse / Content
    suspend fun album(
        browseId: String,
        withSongs: Boolean = true,
    ): Result<AlbumPage>

    suspend fun albumSongs(
        playlistId: String,
        album: AlbumItem? = null,
    ): Result<List<SongItem>>

    suspend fun artist(browseId: String): Result<ArtistPage>

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage>

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage>

    suspend fun playlist(playlistId: String): Result<PlaylistPage>

    suspend fun playlistContinuation(
        continuation: String,
        playlistId: String? = null,
    ): Result<PlaylistContinuationPage>

    suspend fun home(
        continuation: String? = null,
        params: String? = null,
    ): Result<HomePage>

    suspend fun explore(): Result<ExplorePage>

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>>

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>>

    suspend fun browse(
        browseId: String,
        params: String?,
    ): Result<BrowseResult>

    suspend fun getChartsPage(continuation: String? = null): Result<ChartsPage>

    // Library
    suspend fun library(
        browseId: String,
        tabIndex: Int = 0,
    ): Result<LibraryPage>

    suspend fun libraryContinuation(continuation: String): Result<LibraryContinuationPage>

    suspend fun libraryRecentActivity(): Result<LibraryPage>

    suspend fun musicHistory(): Result<HistoryPage>

    // Player / Playback
    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
        setLogin: Boolean = true,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): Result<PlayerResponse>

    suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String? = null,
        followAutomixPreview: Boolean = true,
    ): Result<NextResult>

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?>

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage>

    suspend fun queue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ): Result<List<SongItem>>

    suspend fun registerPlayback(
        playlistId: String? = null,
        playbackTracking: String,
        authState: PlaybackAuthState = currentPlaybackAuthState(),
    ): Result<Unit>

    suspend fun transcript(videoId: String): Result<String>

    // Auth helpers
    suspend fun visitorData(): Result<String>

    suspend fun accountInfo(): Result<AccountInfo>

    suspend fun accountChannels(): Result<List<AccountChannel>>

    suspend fun getChannelId(browseId: String): String

    // Interactions
    suspend fun likeVideo(
        videoId: String,
        like: Boolean,
    ): Result<Unit>

    suspend fun likePlaylist(
        playlistId: String,
        like: Boolean,
    ): Result<Unit>

    suspend fun subscribeChannel(
        channelId: String,
        subscribe: Boolean,
    ): Result<Unit>

    suspend fun addToPlaylist(
        playlistId: String,
        videoId: String,
    ): Result<String>

    suspend fun addSongsToPlaylist(
        playlistId: String,
        videoIds: List<String>,
        batchSize: Int = 50,
        onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
    ): Result<List<String?>>

    suspend fun addPlaylistToPlaylist(
        playlistId: String,
        addPlaylistId: String,
    ): Result<Unit>

    suspend fun playlistEntrySetVideoIds(
        playlistId: String,
        videoId: String,
    ): Result<List<String>>

    suspend fun removeFromPlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ): Result<Unit>

    suspend fun moveSongPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ): Result<Unit>

    suspend fun createPlaylist(
        title: String,
        videoIds: List<String> = emptyList(),
    ): Result<String>

    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ): Result<Unit>

    suspend fun deletePlaylist(playlistId: String): Result<Unit>

    // Misc
    suspend fun getMediaInfo(videoId: String): Result<MediaInfo>
}

