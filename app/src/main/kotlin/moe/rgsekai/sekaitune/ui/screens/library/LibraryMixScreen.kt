/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.library

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.LocalDatabase
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AccountNameKey
import moe.rgsekai.sekaitune.constants.SpotifySpDcKey
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavCredentialStore
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavScanner
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavScanState
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavTagScanner
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavTagScanState
import moe.rgsekai.sekaitune.ui.component.ExpressivePullToRefreshBox
import moe.rgsekai.sekaitune.ui.replay.PAGE_GUTTER
import moe.rgsekai.sekaitune.ui.replay.ReplayCardRow
import moe.rgsekai.sekaitune.ui.replay.cards
import moe.rgsekai.sekaitune.ui.replay.rememberReplayState
import moe.rgsekai.sekaitune.utils.NetworkConnectivityObserver
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.viewmodels.LibraryMixViewModel

/**
 * String resolver abstraction allowing pure JVM unit tests without android.content.Context.
 */
interface LibraryStringProvider {
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String
}

class ContextStringProvider(private val context: Context) : LibraryStringProvider {
    override fun getString(resId: Int): String = context.getString(resId)
    override fun getString(resId: Int, vararg formatArgs: Any): String = context.getString(resId, *formatArgs)
}

/**
 * Data-driven representation of a Library row item.
 */
data class LibraryRowItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val iconRes: Int? = null,
    val iconUrl: String? = null,
    val iconTint: Color? = null,
    val contentDescription: String? = null,
    val destination: String,
    val onClick: () -> Unit,
)

/**
 * Pure function to construct the list of Library rows using LibraryStringProvider.
 * Separated for deterministic unit testing.
 */
fun buildLibraryRowItems(
    stringProvider: LibraryStringProvider,
    historyEventsCount: Int,
    likedSongsCount: Int,
    uploadedSongsCount: Int,
    webDavSongsCount: Int,
    isWebDavConfigured: Boolean,
    webDavFirstArtUrl: String?,
    webDavScanState: WebDavScanState,
    webDavTagScanState: WebDavTagScanState,
    isOnline: Boolean,
    isSpotifyConnected: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    errorColor: Color,
    onNavigate: (String) -> Unit,
): List<LibraryRowItem> = buildList {
    // 1. History
    add(
        LibraryRowItem(
            id = "history",
            title = stringProvider.getString(R.string.history),
            subtitle = if (historyEventsCount > 0) {
                "$historyEventsCount ${stringProvider.getString(R.string.tracks_label)}"
            } else {
                stringProvider.getString(R.string.history_local_summary)
            },
            iconRes = R.drawable.history,
            iconTint = tertiaryColor,
            contentDescription = "${stringProvider.getString(R.string.history)}, ${stringProvider.getString(R.string.history_local_summary)}",
            destination = "history",
            onClick = { onNavigate("history") },
        ),
    )

    // 2. Liked songs
    add(
        LibraryRowItem(
            id = "liked",
            title = stringProvider.getString(R.string.liked_songs),
            subtitle = "$likedSongsCount ${stringProvider.getString(R.string.tracks_label)}",
            iconRes = R.drawable.favorite,
            iconTint = errorColor,
            contentDescription = "${stringProvider.getString(R.string.liked_songs)}, $likedSongsCount ${stringProvider.getString(R.string.tracks_label)}",
            destination = "auto_playlist/liked",
            onClick = { onNavigate("auto_playlist/liked") },
        ),
    )

    // 2. Offline
    add(
        LibraryRowItem(
            id = "offline",
            title = stringProvider.getString(R.string.offline_shortcut),
            subtitle = stringProvider.getString(R.string.downloaded_desc),
            iconRes = R.drawable.offline,
            iconTint = primaryColor,
            contentDescription = "${stringProvider.getString(R.string.offline_shortcut)}, ${stringProvider.getString(R.string.downloaded_desc)}",
            destination = "auto_playlist/downloaded",
            onClick = { onNavigate("auto_playlist/downloaded") },
        ),
    )

    // 3. Uploaded
    add(
        LibraryRowItem(
            id = "uploaded",
            title = stringProvider.getString(R.string.uploaded),
            subtitle = "$uploadedSongsCount ${stringProvider.getString(R.string.tracks_label)}",
            iconRes = R.drawable.backup,
            iconTint = primaryColor,
            contentDescription = "${stringProvider.getString(R.string.uploaded)}, $uploadedSongsCount ${stringProvider.getString(R.string.tracks_label)}",
            destination = "auto_playlist/uploaded",
            onClick = { onNavigate("auto_playlist/uploaded") },
        ),
    )

    // 4. Local Files
    add(
        LibraryRowItem(
            id = "local",
            title = stringProvider.getString(R.string.local_files),
            subtitle = stringProvider.getString(R.string.on_device),
            iconRes = R.drawable.snippet_folder,
            iconTint = secondaryColor,
            contentDescription = "${stringProvider.getString(R.string.local_files)}, ${stringProvider.getString(R.string.on_device)}",
            destination = "local_songs",
            onClick = { onNavigate("local_songs") },
        ),
    )

    // 5. Cached
    add(
        LibraryRowItem(
            id = "cached",
            title = stringProvider.getString(R.string.cached),
            subtitle = stringProvider.getString(R.string.instant_playback),
            iconRes = R.drawable.cached,
            iconTint = tertiaryColor,
            contentDescription = "${stringProvider.getString(R.string.cached)}, ${stringProvider.getString(R.string.instant_playback)}",
            destination = "cache_playlist/cached",
            onClick = { onNavigate("cache_playlist/cached") },
        ),
    )

    // 6. WebDAV (only if configured)
    if (isWebDavConfigured) {
        val isAuthError = (webDavScanState as? WebDavScanState.Error)?.message?.let {
            it.contains("401") || it.contains("403") || it.contains("auth", ignoreCase = true)
        } == true

        val (webDavSubtitle, webDavDestination) = when {
            isAuthError -> {
                stringProvider.getString(R.string.webdav_sign_in_needed) to "settings/sources"
            }
            webDavScanState is WebDavScanState.Scanning -> {
                val found = (webDavScanState as WebDavScanState.Scanning).foundCount
                if (found > 0) {
                    stringProvider.getString(R.string.webdav_scanning_found, found)
                } else {
                    stringProvider.getString(R.string.webdav_scanning)
                } to "webdav_songs"
            }
            webDavTagScanState is WebDavTagScanState.Reading -> {
                val total = (webDavTagScanState as WebDavTagScanState.Reading).total
                if (total > 0) {
                    stringProvider.getString(R.string.webdav_scanning_found, total)
                } else {
                    stringProvider.getString(R.string.webdav_scanning)
                } to "webdav_songs"
            }
            !isOnline || webDavScanState is WebDavScanState.Error -> {
                stringProvider.getString(R.string.webdav_offline_cached) to "webdav_songs"
            }
            else -> {
                stringProvider.getString(R.string.webdav_track_count, webDavSongsCount) to "webdav_songs"
            }
        }

        add(
            LibraryRowItem(
                id = "webdav",
                title = stringProvider.getString(R.string.webdav_title),
                subtitle = webDavSubtitle,
                iconRes = if (webDavFirstArtUrl.isNullOrBlank()) R.drawable.cloud_upload else null,
                iconUrl = webDavFirstArtUrl,
                iconTint = primaryColor,
                contentDescription = "${stringProvider.getString(R.string.webdav_title)}, $webDavSubtitle",
                destination = webDavDestination,
                onClick = { onNavigate(webDavDestination) },
            ),
        )
    }

    // 7. Spotify (only if connected)
    if (isSpotifyConnected) {
        add(
            LibraryRowItem(
                id = "spotify",
                title = stringProvider.getString(R.string.spotify_filter),
                subtitle = stringProvider.getString(R.string.spotify_playlists),
                iconRes = R.drawable.spotify_icon,
                iconTint = null,
                contentDescription = "${stringProvider.getString(R.string.spotify_filter)}, ${stringProvider.getString(R.string.spotify_playlists)}",
                destination = "spotify_playlists",
                onClick = { onNavigate("spotify_playlists") },
            ),
        )
    }
}

fun buildLibraryRowItems(
    context: Context,
    historyEventsCount: Int,
    likedSongsCount: Int,
    uploadedSongsCount: Int,
    webDavSongsCount: Int,
    isWebDavConfigured: Boolean,
    webDavFirstArtUrl: String?,
    webDavScanState: WebDavScanState,
    webDavTagScanState: WebDavTagScanState,
    isOnline: Boolean,
    isSpotifyConnected: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    errorColor: Color,
    onNavigate: (String) -> Unit,
): List<LibraryRowItem> = buildLibraryRowItems(
    stringProvider = ContextStringProvider(context),
    historyEventsCount = historyEventsCount,
    likedSongsCount = likedSongsCount,
    uploadedSongsCount = uploadedSongsCount,
    webDavSongsCount = webDavSongsCount,
    isWebDavConfigured = isWebDavConfigured,
    webDavFirstArtUrl = webDavFirstArtUrl,
    webDavScanState = webDavScanState,
    webDavTagScanState = webDavTagScanState,
    isOnline = isOnline,
    isSpotifyConnected = isSpotifyConnected,
    primaryColor = primaryColor,
    secondaryColor = secondaryColor,
    tertiaryColor = tertiaryColor,
    errorColor = errorColor,
    onNavigate = onNavigate,
)

@Composable
fun LibraryMixScreen(
    navController: NavController,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current

    val credentialStore = remember(context) { WebDavCredentialStore(context) }
    val isWebDavConfigured by credentialStore.isConfiguredFlow.collectAsState(initial = credentialStore.isConfigured)
    val webDavSongsCount by database.webDavSongsCount().collectAsState(initial = 0)
    val webDavFirstArtUrl by database.webDavFirstArtUrl().collectAsState(initial = null)
    val webDavScanState by WebDavScanner.scanState.collectAsState()
    val webDavTagScanState by WebDavTagScanner.tagScanState.collectAsState()

    val networkObserver = remember(context) { NetworkConnectivityObserver(context) }
    DisposableEffect(networkObserver) {
        onDispose {
            networkObserver.unregister()
        }
    }
    val isOnline by networkObserver.networkStatus.collectAsState(initial = networkObserver.isCurrentlyConnected())

    val (spotifySpDc) = rememberPreference(SpotifySpDcKey, defaultValue = "")
    val isSpotifyConnected = remember(spotifySpDc) { spotifySpDc.isNotBlank() }
    val (accountName) = rememberPreference(AccountNameKey, defaultValue = "")

    val (replayState) = rememberReplayState(active = true)
    val replayCards = remember(replayState.summary, context) {
        replayState.summary?.takeUnless { it.isEmpty }?.cards(context).orEmpty()
    }

    val historyEventsCount by database.eventsCount().collectAsState(initial = 0)
    val likedSongsCount by database.likedSongsCount().collectAsState(initial = 0)
    val uploadedSongsCount by database.uploadedSongsCount().collectAsState(initial = 0)
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    val rowItems = remember(
        historyEventsCount,
        likedSongsCount,
        uploadedSongsCount,
        webDavSongsCount,
        isWebDavConfigured,
        webDavFirstArtUrl,
        webDavScanState,
        webDavTagScanState,
        isOnline,
        isSpotifyConnected,
        primaryColor,
        secondaryColor,
        tertiaryColor,
        errorColor,
    ) {
        buildLibraryRowItems(
            context = context,
            historyEventsCount = historyEventsCount,
            likedSongsCount = likedSongsCount,
            uploadedSongsCount = uploadedSongsCount,
            webDavSongsCount = webDavSongsCount,
            isWebDavConfigured = isWebDavConfigured,
            webDavFirstArtUrl = webDavFirstArtUrl,
            webDavScanState = webDavScanState,
            webDavTagScanState = webDavTagScanState,
            isOnline = isOnline,
            isSpotifyConnected = isSpotifyConnected,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            tertiaryColor = tertiaryColor,
            errorColor = errorColor,
            onNavigate = { route -> navController.navigate(route) },
        )
    }

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.syncAllLibrary() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = playerAwareBottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            // 1. BitChord-style Single-Column List Rows
            item(key = "library_list_rows") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    rowItems.forEachIndexed { index, item ->
                        LibraryRow(item = item)
                        if (index < rowItems.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }

            // 2. Listening Experience Cards at the bottom
            if (replayCards.isNotEmpty()) {
                item(key = "library_replay_cards") {
                    ReplayCardRow(
                        cards = replayCards,
                        holder = accountName,
                        memberSince = replayState.memberSince,
                        onCardClick = { page ->
                            navController.navigate("stats")
                        },
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                    )
                }
            }


        }
    }
}

/**
 * BitChord-style plain list row:
 * Leading icon or album art, title on line 1, status/subtitle on line 2, trailing chevron.
 */
@Composable
fun LibraryRow(
    item: LibraryRowItem,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(
                onClickLabel = item.title,
                onClick = item.onClick,
            )
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                if (item.contentDescription != null) {
                    contentDescription = item.contentDescription
                }
            },
    ) {
        // Leading Icon / Art
        if (!item.iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = item.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        } else if (item.iconRes != null) {
            Icon(
                painter = painterResource(id = item.iconRes),
                contentDescription = null,
                tint = item.iconTint ?: Color.Unspecified,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }

        Spacer(Modifier.width(16.dp))

        // Title and Subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Trailing Chevron
        Icon(
            painter = painterResource(id = R.drawable.navigate_next),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}
