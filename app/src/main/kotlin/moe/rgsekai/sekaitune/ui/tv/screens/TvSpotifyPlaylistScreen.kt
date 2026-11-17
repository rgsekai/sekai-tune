/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.tv.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.spotify.SpotifyMapper
import moe.rgsekai.sekaitune.spotify.SpotifyPlaybackResolver
import moe.rgsekai.sekaitune.spotify.SpotifyPlaylistQueue
import moe.rgsekai.sekaitune.spotify.SpotifyPlaylistViewModel
import moe.rgsekai.sekaitune.spotify.models.SpotifyTrack
import moe.rgsekai.sekaitune.ui.tv.TV_HOME_ROUTE
import moe.rgsekai.sekaitune.ui.tv.components.TvDetailLayout
import moe.rgsekai.sekaitune.ui.tv.components.TvSongItem
import moe.rgsekai.sekaitune.ui.tv.components.TvSongMenu
import moe.rgsekai.sekaitune.ui.tv.components.TvSongMenuTarget
import moe.rgsekai.sekaitune.utils.makeTimeString

/**
 * TV Detail Screen for Spotify Playlists (`spotify_playlist/{playlistId}`).
 */
@Composable
fun TvSpotifyPlaylistScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    viewModel: SpotifyPlaylistViewModel = hiltViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    val playlist = uiState.playlist
    val tracks = uiState.tracks

    var selectedTrackWithMediaItem by remember { mutableStateOf<Pair<SpotifyTrack, MediaItem?>?>(null) }

    val playlistTitle = playlist?.name.orEmpty()
    val coverThumbnail = remember(playlist) {
        playlist?.let { SpotifyMapper.getPlaylistThumbnail(it) }
    }

    fun playPlaylist(
        startIndex: Int = 0,
        shuffled: Boolean = false,
    ) {
        val currentPlaylist = playlist ?: return
        val queueTracks = if (shuffled) tracks.shuffled() else tracks
        if (queueTracks.isEmpty()) return
        val boundedStartIndex = startIndex.coerceIn(queueTracks.indices)
        val preloadTrack = queueTracks[boundedStartIndex]

        coroutineScope.launch {
            val preloadItem = withContext(Dispatchers.IO) {
                SpotifyPlaybackResolver.resolveToMetadata(preloadTrack)
            }
            playerConnection?.playQueue(
                SpotifyPlaylistQueue(
                    playlistId = currentPlaylist.id,
                    title = currentPlaylist.name,
                    initialTracks = queueTracks,
                    startIndex = boundedStartIndex,
                    preloadItem = preloadItem,
                    totalTracksCount = currentPlaylist.tracks?.total,
                ),
            )
        }
    }

    val songItems = remember(tracks, mediaMetadata, isPlaying, playlistTitle) {
        tracks.mapIndexed { index, track ->
            val isActive = mediaMetadata?.id == track.id
            val isCurrentPlaying = isActive && isPlaying

            TvSongItem(
                id = track.id,
                title = track.name,
                subtitle = track.artists.joinToString { it.name }.ifEmpty { track.album?.name },
                thumbnailUrl = track.album?.images?.firstOrNull()?.url,
                durationText = makeTimeString(track.durationMs.toLong()),
                isActive = isActive,
                isPlaying = isCurrentPlaying,
                onClick = {
                    if (playerConnection != null) {
                        if (isActive) {
                            if (isPlaying) playerConnection.player.pause() else playerConnection.player.play()
                        } else {
                            playPlaylist(startIndex = index, shuffled = false)
                        }
                    }
                },
                onLongClick = {
                    coroutineScope.launch {
                        val mediaItem = withContext(Dispatchers.IO) {
                            SpotifyPlaybackResolver.resolveToMediaItem(track)
                        }
                        selectedTrackWithMediaItem = Pair(track, mediaItem)
                    }
                },
            )
        }
    }

    TvDetailLayout(
        title = playlistTitle,
        subtitle = playlist?.owner?.displayName ?: stringResource(R.string.playlists),
        thumbnailUrl = coverThumbnail,
        songs = songItems,
        isLoading = uiState.isLoading && playlist == null,
        emptyMessage = uiState.errorMessage ?: stringResource(R.string.playlist_is_empty),
        coverPlaceholderIcon = R.drawable.spotify_icon,
        onPlayClick = {
            playPlaylist(startIndex = 0, shuffled = false)
        },
        onShuffleClick = {
            playPlaylist(startIndex = 0, shuffled = true)
        },
        playEnabled = tracks.isNotEmpty(),
        shuffleEnabled = tracks.isNotEmpty(),
        upFocusRequester = upFocusRequester,
        onBack = {
            if (!navController.popBackStack()) {
                navController.navigate(TV_HOME_ROUTE) {
                    popUpTo(TV_HOME_ROUTE) { inclusive = true }
                    launchSingleTop = true
                }
            }
        },
        modifier = modifier,
    )

    // Context / Action menu for selected song row
    selectedTrackWithMediaItem?.let { (track, mediaItem) ->
        TvSongMenu(
            target = TvSongMenuTarget(
                id = track.id,
                title = track.name,
                artistsText = track.artists.joinToString { it.name },
                artistId = null,
                albumId = null,
                albumTitle = track.album?.name,
                thumbnailUrl = track.album?.images?.firstOrNull()?.url,
                shareUrl = "https://open.spotify.com/track/${track.id}",
                mediaItem = mediaItem,
            ),
            navController = navController,
            onDismiss = { selectedTrackWithMediaItem = null },
        )
    }
}
