/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.library

import android.widget.Toast
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.ShowSpotifyFollowArtistKey
import moe.rgsekai.sekaitune.constants.ShowSpotifyPlaylistsKey
import moe.rgsekai.sekaitune.spotify.SpotifyArtistResolver
import moe.rgsekai.sekaitune.spotify.SpotifyLibraryViewModel
import moe.rgsekai.sekaitune.spotify.YtmArtistResolution
import moe.rgsekai.sekaitune.ui.component.ExpressivePullToRefreshBox
import moe.rgsekai.sekaitune.ui.component.SpotifyLibraryArtistListItem
import moe.rgsekai.sekaitune.ui.component.SpotifyLibraryPlaylistListItem
import moe.rgsekai.sekaitune.utils.rememberPreference

private enum class SpotifySection {
    PLAYLISTS,
    ARTISTS,
}

@Composable
fun LibrarySpotifyPlaylistsScreen(
    navController: NavController,
    viewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val (showSpotifyPlaylists) = rememberPreference(ShowSpotifyPlaylistsKey, defaultValue = false)
    val (showSpotifyFollowArtist) = rememberPreference(ShowSpotifyFollowArtistKey, defaultValue = true)

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val followedArtists by viewModel.followedArtists.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var selectedSection by rememberSaveable {
        mutableStateOf(
            if (showSpotifyPlaylists) SpotifySection.PLAYLISTS else SpotifySection.ARTISTS,
        )
    }

    var resolvingArtistId by remember { mutableStateOf<String?>(null) }

    val activeSection =
        when {
            showSpotifyPlaylists && showSpotifyFollowArtist -> selectedSection
            showSpotifyPlaylists -> SpotifySection.PLAYLISTS
            showSpotifyFollowArtist -> SpotifySection.ARTISTS
            else -> SpotifySection.PLAYLISTS
        }

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (activeSection == SpotifySection.PLAYLISTS) {
                viewModel.refreshPlaylists()
            } else {
                viewModel.refreshFollowedArtists()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showSpotifyPlaylists && showSpotifyFollowArtist) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpotifyPillTab(
                        text = stringResource(R.string.spotify_playlists_tab),
                        isSelected = activeSection == SpotifySection.PLAYLISTS,
                        onClick = { selectedSection = SpotifySection.PLAYLISTS },
                    )
                    SpotifyPillTab(
                        text = stringResource(R.string.spotify_artists_tab),
                        isSelected = activeSection == SpotifySection.ARTISTS,
                        onClick = { selectedSection = SpotifySection.ARTISTS },
                    )
                }
            }

            LazyColumn(
                state = rememberLazyListState(),
                contentPadding =
                    PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = if (showSpotifyPlaylists && showSpotifyFollowArtist) 4.dp else 0.dp,
                        bottom = playerAwareBottomPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (activeSection == SpotifySection.PLAYLISTS) {
                    if (playlists.isEmpty()) {
                        item(key = "spotify_empty_playlists", contentType = "spotify_empty") {
                            Text(
                                text = stringResource(R.string.spotify_no_sources),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }

                    items(
                        items = playlists,
                        key = { playlist -> playlist.id },
                        contentType = { "spotify_playlist" },
                    ) { playlist ->
                        SpotifyLibraryPlaylistListItem(
                            playlist = playlist,
                            navController = navController,
                        )
                    }
                } else {
                    if (followedArtists.isEmpty()) {
                        item(key = "spotify_empty_artists", contentType = "spotify_empty") {
                            Text(
                                text = stringResource(R.string.spotify_no_artists),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }

                    items(
                        items = followedArtists,
                        key = { artist -> artist.id },
                        contentType = { "spotify_artist" },
                    ) { artist ->
                        SpotifyLibraryArtistListItem(
                            artist = artist,
                            isResolving = resolvingArtistId == artist.id,
                            onClick = {
                                if (resolvingArtistId != null) return@SpotifyLibraryArtistListItem
                                resolvingArtistId = artist.id
                                coroutineScope.launch {
                                    val resolution =
                                        SpotifyArtistResolver.resolveSpotifyToYtmArtist(
                                            spotifyArtistId = artist.id,
                                            spotifyArtistName = artist.name,
                                        )
                                    resolvingArtistId = null
                                    when (resolution) {
                                        is YtmArtistResolution.Matched -> {
                                            navController.navigate("artist/${resolution.channelId}")
                                        }
                                        YtmArtistResolution.NoMatch -> {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.spotify_artist_not_found_on_ytm),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onUnfollow = {
                                viewModel.unfollowArtist(artist.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyPillTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = contentColor,
        )
    }
}
