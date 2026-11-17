/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.tv.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.spotify.SPOTIFY_LIKED_SONGS_ID
import moe.rgsekai.sekaitune.spotify.SpotifyLibraryViewModel
import moe.rgsekai.sekaitune.ui.tv.components.TvShelfItem
import moe.rgsekai.sekaitune.ui.tv.components.TvShelfRow
import moe.rgsekai.sekaitune.ui.utils.safeRequestFocus
import moe.rgsekai.sekaitune.viewmodels.LibraryPlaylistsViewModel

private data class TvLibrarySection(
    val key: String,
    val title: String,
    val items: List<TvShelfItem>,
)

/**
 * TV Library Screen displaying Playlists and Spotify Playlists
 * directly as TV shelf rows without mobile chip tabs.
 */
@Composable
fun TvLibraryScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    firstShelfFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    playlistsViewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    spotifyViewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val allPlaylists by playlistsViewModel.allPlaylists.collectAsStateWithLifecycle()
    val spotifyPlaylists by spotifyViewModel.playlists.collectAsStateWithLifecycle()
    val likedSongsCount by spotifyViewModel.likedSongsCount.collectAsStateWithLifecycle()
    val isPlaylistsRefreshing by playlistsViewModel.isRefreshing.collectAsStateWithLifecycle()
    val isSpotifyRefreshing by spotifyViewModel.isRefreshing.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        playlistsViewModel.sync()
        delay(60)
        firstShelfFocusRequester?.safeRequestFocus()
    }

    LaunchedEffect(allPlaylists, spotifyPlaylists) {
        if (allPlaylists.isNotEmpty() || spotifyPlaylists.isNotEmpty()) {
            delay(50)
            firstShelfFocusRequester?.safeRequestFocus()
        }
    }

    val playlistsTitle = stringResource(R.string.playlists)
    val spotifyPlaylistsTitle = stringResource(R.string.spotify_playlists)

    val playlistShelfItems = remember(allPlaylists) {
        allPlaylists.map { playlist ->
            val count = playlist.songCount
            TvShelfItem(
                id = playlist.id,
                title = playlist.playlist.name,
                subtitle = if (count > 0) {
                    "$count ${if (count == 1) "song" else "songs"}"
                } else null,
                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                onClick = {
                    if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.browseId != null) {
                        navController.navigate("online_playlist/${playlist.playlist.browseId}")
                    } else {
                        navController.navigate("local_playlist/${playlist.id}")
                    }
                },
            )
        }
    }

    val spotifyShelfItems = remember(spotifyPlaylists, likedSongsCount, context) {
        val likedItem =
            TvShelfItem(
                id = SPOTIFY_LIKED_SONGS_ID,
                title = context.getString(R.string.spotify_liked_songs),
                subtitle = likedSongsCount?.let { count -> if (count > 0) "$count ${if (count == 1) "song" else "songs"}" else "0 songs" },
                thumbnailUrl = "android.resource://${context.packageName}/${R.drawable.spotify_liked_songs}",
                onClick = {
                    navController.navigate("spotify_playlist/$SPOTIFY_LIKED_SONGS_ID")
                },
            )
        listOf(likedItem) + spotifyPlaylists.map { playlist ->
            val count = playlist.tracks?.total ?: 0
            TvShelfItem(
                id = playlist.id,
                title = playlist.name,
                subtitle = playlist.owner?.displayName
                    ?: if (count > 0) "$count ${if (count == 1) "song" else "songs"}" else null,
                thumbnailUrl = playlist.images.firstOrNull()?.url,
                onClick = {
                    navController.navigate("spotify_playlist/${playlist.id}")
                },
            )
        }
    }

    val sections = remember(playlistShelfItems, spotifyShelfItems, playlistsTitle, spotifyPlaylistsTitle) {
        buildList {
            if (playlistShelfItems.isNotEmpty()) {
                add(
                    TvLibrarySection(
                        key = "shelf_playlists",
                        title = playlistsTitle,
                        items = playlistShelfItems,
                    ),
                )
            }
            if (spotifyShelfItems.isNotEmpty()) {
                add(
                    TvLibrarySection(
                        key = "shelf_spotify_playlists",
                        title = spotifyPlaylistsTitle,
                        items = spotifyShelfItems,
                    ),
                )
            }
        }
    }

    if (sections.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize(),
        ) {
            if (isPlaylistsRefreshing || isSpotifyRefreshing) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Text(
                    text = stringResource(R.string.no_results_found),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 76.dp, bottom = 48.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = sections,
                key = { _, section -> section.key },
            ) { index, section ->
                val isFirst = index == 0
                val isLast = index == sections.size - 1

                TvShelfRow(
                    title = section.title,
                    items = section.items,
                    isFirstShelf = isFirst,
                    isLastShelf = isLast,
                    upFocusRequester = upFocusRequester,
                    firstItemFocusRequester = if (isFirst) firstShelfFocusRequester else null,
                )
            }
        }
    }
}
