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
import androidx.compose.foundation.layout.statusBars
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
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroBackdrop
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroTitleArea
import moe.rgsekai.sekaitune.ui.component.PlaylistTopBarOverlay
import moe.rgsekai.sekaitune.ui.component.rememberPlaylistCoverHeight
import moe.rgsekai.sekaitune.ui.screens.playlist.PlaylistHeroImageResult
import moe.rgsekai.sekaitune.ui.screens.playlist.ResolvePlaylistHeroImage
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
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
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
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.LocalMenuState
import moe.rgsekai.sekaitune.ui.component.SpotifyTrackListItem
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

    val coverHeight = rememberPlaylistCoverHeight()

    val heroImageResult = remember(playlist, tracks) {
        ResolvePlaylistHeroImage.resolve(
            platformCoverUrl = playlist?.let { SpotifyMapper.getPlaylistThumbnail(it) },
            firstSongThumbnailUrl = tracks.firstOrNull()?.album?.images?.firstOrNull()?.url,
        )
    }

    val thumbnailUrl =
        remember(heroImageResult) {
            when (heroImageResult) {
                is PlaylistHeroImageResult.Image -> heroImageResult.urlOrUri
                is PlaylistHeroImageResult.Placeholder -> null
            }
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

    val extractedThemeColor =
        remember(gradientColors) {
            if (gradientColors.isEmpty()) {
                null
            } else {
                val seed = gradientColors.first()
                val scheme =
                    dynamicColorScheme(
                        seedColor = seed,
                        isDark = true,
                        style = PaletteStyle.Vibrant,
                    )
                Color(
                    red = (seed.red * 0.35f + scheme.surfaceVariant.red * 0.65f).coerceIn(0f, 1f),
                    green = (seed.green * 0.35f + scheme.surfaceVariant.green * 0.65f).coerceIn(0f, 1f),
                    blue = (seed.blue * 0.35f + scheme.surfaceVariant.blue * 0.65f).coerceIn(0f, 1f),
                    alpha = 1f,
                )
            }
        }

    val effectivePageBackgroundColor = extractedThemeColor ?: surfaceColor

    val animatedBackgroundColor by animateColorAsState(
        targetValue = effectivePageBackgroundColor,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "spotifyPlaylistBg",
    )

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 300
        }
    }

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
        if (!isSearching) {
            PlaylistHeroBackdrop(
                heroImage = heroImageResult,
                dominantColor = gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.primary,
                backgroundColor = animatedBackgroundColor,
                coverHeight = coverHeight,
                scrollOffset = {
                    if (lazyListState.firstVisibleItemIndex == 0) {
                        lazyListState.firstVisibleItemScrollOffset
                    } else {
                        1000
                    }
                },
            )
        }
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!isSearching) {
                playlist?.let { currentPlaylist ->
                    item(key = "header") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            val trackCount = currentPlaylist.tracks?.total ?: tracks.size
                            val songCountStr = pluralStringResource(R.plurals.n_song, trackCount, trackCount)
                            val ownerStr = currentPlaylist.owner?.displayName?.takeIf(String::isNotBlank)
                            val durationStr = if (loadedDurationMs > 0L) makeTimeString(loadedDurationMs) else ""
                            val subtitle = listOfNotNull(ownerStr, songCountStr, durationStr.takeIf { it.isNotBlank() }).joinToString(" · ")

                            PlaylistHeroTitleArea(
                                title = currentPlaylist.name,
                                subtitle = subtitle,
                                coverHeight = coverHeight,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToggleButton(
                                    checked = downloadState == HeaderDownloadState.Completed,
                                    onCheckedChange = {
                                        if (state.downloadItems.isEmpty()) {
                                            viewModel.resolveDownloads()
                                        } else {
                                            when (downloadState) {
                                                HeaderDownloadState.Completed -> {
                                                    showRemoveDownloadDialog = true
                                                }

                                                else -> {
                                                    handleDownloadAction(state.downloadItems)
                                                }
                                            }
                                        }
                                    },
                                    enabled = tracks.isNotEmpty() && !state.isLoading && !state.isResolvingDownloads,
                                    modifier = Modifier.size(48.dp),
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                    colors =
                                        ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            checkedContentColor = MaterialTheme.colorScheme.primary,
                                        ),
                                ) {
                                    if (state.isResolvingDownloads) {
                                        CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        when (val currentDownloadState = downloadState) {
                                            HeaderDownloadState.Completed -> {
                                                Icon(
                                                    painter = painterResource(R.drawable.offline),
                                                    contentDescription = stringResource(R.string.remove_download),
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }

                                            is HeaderDownloadState.Partial -> {
                                                HeaderDownloadProgressIndicator(
                                                    progress = currentDownloadState.progress,
                                                    paused = currentDownloadState.paused,
                                                )
                                            }

                                            HeaderDownloadState.None -> {
                                                Icon(
                                                    painter = painterResource(R.drawable.download),
                                                    contentDescription = stringResource(R.string.download),
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = { playPlaylist() },
                                    enabled = tracks.isNotEmpty(),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                    colors =
                                        ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = stringResource(R.string.play),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = { playPlaylist(shuffled = true) },
                                    enabled = tracks.isNotEmpty(),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                    colors =
                                        ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.shuffle),
                                        contentDescription = stringResource(R.string.shuffle),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = { viewModel.reload() },
                                    modifier = Modifier.size(48.dp),
                                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                    colors =
                                        ToggleButtonDefaults.toggleButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.sync),
                                        contentDescription = stringResource(R.string.spotify_reload_playlist),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { playPlaylist(shuffled = true) },
                                    enabled = tracks.isNotEmpty(),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.mix),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
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

        if (selection || isSearching) {
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

    if (!selection && !isSearching) {
        PlaylistTopBarOverlay(
            title = playlist?.name ?: stringResource(R.string.spotify_playlists),
            scrolled = showTopBarTitle,
            backgroundColor = animatedBackgroundColor,
            onBackClick = { navController.navigateUp() },
            onSearchClick = { isSearching = true },
            onMoreClick = null,
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




