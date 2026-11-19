/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalDownloadUtil
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AppBarHeight
import moe.rgsekai.sekaitune.constants.DisableBlurKey
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.spotify.SpotifyDownloadItem
import moe.rgsekai.sekaitune.spotify.SpotifyMapper
import moe.rgsekai.sekaitune.spotify.SpotifyPlaybackResolver
import moe.rgsekai.sekaitune.spotify.SpotifyPlaylistEvent
import moe.rgsekai.sekaitune.spotify.SpotifyPlaylistQueue
import moe.rgsekai.sekaitune.spotify.SpotifyPlaylistViewModel
import moe.rgsekai.sekaitune.spotify.models.SpotifyTrack
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.DraggableScrollbar
import moe.rgsekai.sekaitune.ui.component.EmptyPlaceholder
import moe.rgsekai.sekaitune.ui.component.ExpressivePullToRefreshBox
import moe.rgsekai.sekaitune.ui.component.FeaturedArtistItem
import moe.rgsekai.sekaitune.ui.component.FeaturedArtistsRow
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.LocalMenuState
import moe.rgsekai.sekaitune.ui.component.PlaylistEndOfLine
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroBackdrop
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroHeader
import moe.rgsekai.sekaitune.ui.component.PlaylistTopBarOverlay
import moe.rgsekai.sekaitune.ui.component.SpotifyTrackListItem
import moe.rgsekai.sekaitune.ui.component.rememberPlaylistCoverHeight
import moe.rgsekai.sekaitune.ui.menu.SelectionSpotifyTracksMenu
import moe.rgsekai.sekaitune.ui.theme.PlayerColorExtractor
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadItem
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadProgressIndicator
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadState
import moe.rgsekai.sekaitune.ui.utils.ItemWrapper
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.ui.utils.headerDownloadState
import moe.rgsekai.sekaitune.ui.utils.resize
import moe.rgsekai.sekaitune.ui.utils.sendAddMissingDownloads
import moe.rgsekai.sekaitune.ui.utils.sendPauseDownloads
import moe.rgsekai.sekaitune.ui.utils.sendRemoveDownloads
import moe.rgsekai.sekaitune.ui.utils.sendResumeDownloads
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SpotifyPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadUtil = LocalDownloadUtil.current
    val downloads by downloadUtil.downloads.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val playlist = state.playlist
    val tracks = state.tracks
    val lazyListState = rememberLazyListState()
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val snackbarHostState = remember { SnackbarHostState() }
    val downloadActionFailedMessage = stringResource(R.string.download_action_failed)
    val latestDownloads by rememberUpdatedState(downloads)
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    val downloadState =
        remember(state.downloadItems, downloads) {
            headerDownloadState(
                songIds = state.downloadItems.map(SpotifyDownloadItem::id),
                downloads = downloads,
            )
        }

    fun handleDownloadAction(
        items: List<SpotifyDownloadItem>,
        removeCompleted: Boolean = true,
    ) {
        val songIds = items.map(SpotifyDownloadItem::id)
        when (val currentDownloadState = headerDownloadState(songIds, latestDownloads)) {
            HeaderDownloadState.Completed -> {
                if (removeCompleted) {
                    sendRemoveDownloads(
                        context = context,
                        songIds = songIds,
                    )
                }
            }

            is HeaderDownloadState.Partial -> {
                if (currentDownloadState.paused) {
                    sendResumeDownloads(context, songIds)
                } else {
                    sendPauseDownloads(context, songIds)
                }
            }

            HeaderDownloadState.None -> {
                sendAddMissingDownloads(
                    context = context,
                    songs =
                        items.map { item ->
                            HeaderDownloadItem(
                                id = item.id,
                                title = item.title,
                            )
                        },
                    downloads = latestDownloads,
                )
                navController.navigate("auto_playlist/downloaded?tab=progress")
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SpotifyPlaylistEvent.DownloadResolutionFailed -> {
                    snackbarHostState.showSnackbar(downloadActionFailedMessage)
                }

                is SpotifyPlaylistEvent.DownloadsResolved -> {
                    handleDownloadAction(
                        items = event.items,
                        removeCompleted = false,
                    )
                }
            }
        }
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist?.name.orEmpty(),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        sendRemoveDownloads(
                            context = context,
                            songIds = state.downloadItems.map(SpotifyDownloadItem::id),
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    val filteredTracks =
        remember(tracks, query.text) {
            if (query.text.isBlank()) {
                tracks
            } else {
                tracks.filter { track ->
                    track.name.contains(query.text, ignoreCase = true) ||
                        track.artists.any { artist -> artist.name.contains(query.text, ignoreCase = true) } ||
                        track.album?.name?.contains(query.text, ignoreCase = true) == true
                }
            }
        }

    val wrappedTracks =
        remember(filteredTracks) {
            filteredTracks.map { ItemWrapper(it) }.toMutableStateList()
        }

    var selection by remember { mutableStateOf(false) }
    val selectedCount by remember(wrappedTracks) {
        derivedStateOf { wrappedTracks.count { it.isSelected } }
    }
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current

    LaunchedEffect(selection, selectedCount) {
        if (selection && selectedCount == 0) {
            selection = false
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItemIndex = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItemsCount = lazyListState.layoutInfo.totalItemsCount
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore, state.hasMore, state.isLoadingMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore && query.text.isBlank()) {
            viewModel.loadMore()
        }
    }

    val loadedDurationMs =
        remember(tracks) {
            tracks.sumOf { track -> track.durationMs.toLong() }
        }

    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    val thumbnailUrl =
        remember(playlist) {
            playlist?.let { SpotifyMapper.getPlaylistThumbnail(it)?.resize(544, 544) }
        }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                    .allowHardware(false)
                    .build()

            val result =
                runCatching {
                    context.imageLoader.execute(request)
                }.getOrNull()

            if (result != null) {
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette =
                        withContext(Dispatchers.Default) {
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        }

                    gradientColors =
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                }
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val heroImageResult = remember(playlist, tracks) {
        ResolvePlaylistHeroImage.resolve(
            customImageUri = null,
            platformCoverUrl = playlist?.let { SpotifyMapper.getPlaylistThumbnail(it) },
            firstSongThumbnailUrl = tracks.firstOrNull()?.let { SpotifyMapper.getTrackThumbnail(it) },
        )
    }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = gradientColors.getOrNull(1) ?: MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "spotifyPlaylistBgColor",
    )
    val animatedDominantColor by animateColorAsState(
        targetValue = gradientColors.firstOrNull() ?: surfaceColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "spotifyPlaylistDominantColor",
    )

    val featuredArtists = remember(tracks) {
        tracks.flatMap { it.artists }
            .filter { !it.id.isNullOrBlank() }
            .groupBy { it.id ?: "" }
            .values
            .sortedByDescending { it.size }
            .take(10)
            .mapNotNull { artistList ->
                artistList.firstOrNull()?.let { artist ->
                    FeaturedArtistItem(
                        id = artist.id.orEmpty(),
                        name = artist.name,
                        thumbnailUrl = null,
                    )
                }
            }
    }

    val insetsPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues()
    val listContentPadding = remember(insetsPadding, isSearching) {
        if (!isSearching) {
            PaddingValues(
                start = insetsPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = 0.dp,
                end = insetsPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = insetsPadding.calculateBottomPadding(),
            )
        } else {
            insetsPadding
        }
    }

    val coverHeight = rememberPlaylistCoverHeight()

    val gradientAlpha by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                val offset = lazyListState.firstVisibleItemScrollOffset
                (1f - (offset / 600f)).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    val transparentAppBar by remember {
        derivedStateOf { !disableBlur && !showTopBarTitle && !isSearching && !selection }
    }

    val topAppBarColors =
        if (transparentAppBar) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                actionIconContentColor = MaterialTheme.colorScheme.onBackground,
            )
        } else {
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            )
        }

    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
            wrappedTracks.forEach { it.isSelected = false }
        }
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
        if (resolvingTrackId != null) return

        coroutineScope.launch {
            resolvingTrackId = preloadTrack.id
            try {
                val preloadItem = SpotifyPlaybackResolver.resolveToMetadata(preloadTrack)
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
            } finally {
                resolvingTrackId = null
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = state.isLoading && tracks.isNotEmpty(),
        onRefresh = viewModel::reload,
        modifier =
            Modifier
                .fillMaxSize()
                .background(animatedBackgroundColor),
    ) {
        if (!selection && !isSearching) {
            PlaylistHeroBackdrop(
                heroImage = heroImageResult,
                dominantColor = animatedDominantColor,
                backgroundColor = animatedBackgroundColor,
                coverHeight = coverHeight,
                scrollOffset = {
                    if (lazyListState.firstVisibleItemIndex == 0) {
                        lazyListState.firstVisibleItemScrollOffset
                    } else {
                        10000
                    }
                },
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = listContentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isSearching) {
                playlist?.let { currentPlaylist ->
                    item(key = "header") {
                        val durationText = remember(tracks) {
                            val totalSeconds = tracks.sumOf { it.durationMs.toLong() / 1000L }.toInt()
                            val minutes = (totalSeconds + 30) / 60
                            if (minutes >= 60) {
                                val hours = minutes / 60
                                val remMin = minutes % 60
                                if (remMin > 0) "${hours}h ${remMin}m" else "${hours}h"
                            } else {
                                "${minutes}m"
                            }
                        }
                        val trackCount = currentPlaylist.tracks?.total ?: tracks.size
                        val statsText = "$trackCount ${if (trackCount == 1) "song" else "songs"} · $durationText"

                        PlaylistHeroHeader(
                            title = currentPlaylist.name,
                            stats = statsText,
                            isPlaying = isPlaying,
                            onPlayClick = {
                                if (isPlaying) {
                                    playerConnection?.player?.pause()
                                } else {
                                    playPlaylist(shuffled = false)
                                }
                            },
                            onShuffleClick = {
                                playPlaylist(shuffled = true)
                            },
                            onSearchClick = { isSearching = true },
                            coverHeight = coverHeight,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (state.isLoading && tracks.isEmpty()) {
                item(key = "loading") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }

            state.errorMessage?.let { error ->
                item(key = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            if (!state.isLoading && state.errorMessage == null && filteredTracks.isEmpty()) {
                item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text =
                            stringResource(
                                if (query.text.isBlank()) {
                                    R.string.spotify_no_tracks
                                } else {
                                    R.string.ai_model_no_results
                                },
                            ),
                    )
                }
            }

            itemsIndexed(
                items = wrappedTracks,
                key = { index, trackWrapper -> "spotify_track_${trackWrapper.item.id}_$index" },
                contentType = { _, _ -> "spotify_track" },
            ) { index, trackWrapper ->
                val track = trackWrapper.item
                val trackIsActive =
                    remember(track, mediaMetadata) {
                        track.isResolvedAs(mediaMetadata)
                    }
                val trackIsResolving = resolvingTrackId == track.id
                val trackDownloadState =
                    remember(track.id, state.downloadItems, downloads) {
                        val resolvedId =
                            state.downloadItems.firstOrNull { it.spotifyTrackId == track.id }?.id
                                ?: SpotifyPlaybackResolver.getCached(track.id)?.id
                        resolvedId?.let { downloads[it]?.state }
                    }

                SpotifyTrackListItem(
                    track = track,
                    downloadState = trackDownloadState,
                    isSelected = trackWrapper.isSelected && selection,
                    isActive = trackIsActive || trackIsResolving,
                    isPlaying = isPlaying && !trackIsResolving,
                    trailingContent = {
                        if (trackIsResolving) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = resolvingTrackId == null || trackIsActive || selection,
                                onClick = {
                                    if (!selection || selectedCount == 0) {
                                        if (trackIsActive) {
                                            playerConnection?.player?.togglePlayPause()
                                        } else {
                                            val startIndex =
                                                tracks
                                                    .indexOfFirst { item -> item.id == track.id }
                                                    .takeIf { itemIndex -> itemIndex >= 0 }
                                                    ?: index
                                            playPlaylist(startIndex = startIndex)
                                        }
                                    } else {
                                        trackWrapper.isSelected = !trackWrapper.isSelected
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (!selection) {
                                        selection = true
                                        wrappedTracks.forEach { it.isSelected = false }
                                        trackWrapper.isSelected = true
                                    } else {
                                        trackWrapper.isSelected = !trackWrapper.isSelected
                                    }
                                },
                            ),
                )
            }

            if (state.isLoadingMore) {
                item(key = "loading_more", contentType = "loading_more") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }

            // End of List Footer
            if (!isSearching && tracks.isNotEmpty()) {
                item(key = "end_of_line_footer") {
                    val totalSeconds = remember(tracks) {
                        tracks.sumOf { it.durationMs.toLong() / 1000L }.toInt()
                    }
                    PlaylistEndOfLine(
                        songCount = tracks.size,
                        totalSeconds = totalSeconds,
                    )
                }
            }

            // Featured Artists
            if (!isSearching && featuredArtists.isNotEmpty()) {
                item(key = "featured_artists_row") {
                    FeaturedArtistsRow(
                        artists = featuredArtists,
                        onArtistClick = { artist ->
                        },
                    )
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = if (!isSearching && playlist != null) 1 else 0,
        )

        if (!selection && !isSearching) {
            PlaylistTopBarOverlay(
                title = playlist?.name ?: stringResource(R.string.spotify_playlists),
                scrolled = showTopBarTitle,
                backgroundColor = animatedBackgroundColor,
                onBackClick = { navController.navigateUp() },
                onMoreClick = null,
            )
        } else {
        TopAppBar(
            colors = topAppBarColors,
            title = {
                if (selection) {
                    Text(
                        text = pluralStringResource(R.plurals.n_song, selectedCount, selectedCount),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(
                        text = playlist?.name ?: stringResource(R.string.spotify_playlists),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else if (selection) {
                            selection = false
                            wrappedTracks.forEach { it.isSelected = false }
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching && !selection) navController.backToMain()
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isSearching || selection) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (selection) {
                    IconButton(
                        onClick = {
                            if (selectedCount == wrappedTracks.size) {
                                wrappedTracks.forEach { it.isSelected = false }
                                selection = false
                            } else {
                                wrappedTracks.forEach { it.isSelected = true }
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (selectedCount == wrappedTracks.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = {
                            val selectedTracks = wrappedTracks.filter { it.isSelected }.map { it.item }
                            menuState.show {
                                SelectionSpotifyTracksMenu(
                                    trackSelection = selectedTracks,
                                    playlistName = playlist?.name.orEmpty(),
                                    onDismiss = menuState::dismiss,
                                    clearAction = {
                                        selection = false
                                        wrappedTracks.forEach { it.isSelected = false }
                                    },
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    IconButton(
                        onClick = { isSearching = true },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.BottomCenter),
        )
    }
}

private fun SpotifyTrack.isResolvedAs(mediaMetadata: MediaMetadata?): Boolean {
    if (mediaMetadata == null) return false

    mediaMetadata.spotifyTrackId?.let { spotifyTrackId ->
        return id.isNotBlank() && spotifyTrackId == id
    }

    val titleMatches = name.equals(mediaMetadata.title, ignoreCase = true)
    val durationMatches =
        durationMs <= 0 ||
            mediaMetadata.duration <= 0 ||
            abs(durationMs.toLong() - mediaMetadata.duration * 1000L) <= 1_000L
    val albumMatches =
        album?.let { spotifyAlbum ->
            val currentAlbum = mediaMetadata.album ?: return false
            spotifyAlbum.id.isNotBlank() && spotifyAlbum.id == currentAlbum.id ||
                spotifyAlbum.name.equals(currentAlbum.title, ignoreCase = true)
        } ?: true
    val artistMatches =
        artists.isEmpty() ||
            mediaMetadata.artists.isEmpty() ||
            artists.any { spotifyArtist ->
                mediaMetadata.artists.any { artist ->
                    spotifyArtist.name.equals(artist.name, ignoreCase = true)
                }
            }
    val thumbnailMatches =
        SpotifyMapper.getTrackThumbnail(this)?.let { thumbnail ->
            thumbnail == mediaMetadata.thumbnailUrl
        } ?: true

    return titleMatches && durationMatches && albumMatches && artistMatches && thumbnailMatches
}

@Composable
private fun MetadataChip(
    icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}




