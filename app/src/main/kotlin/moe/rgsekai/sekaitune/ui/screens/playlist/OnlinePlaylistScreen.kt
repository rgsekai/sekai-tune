/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalDatabase
import moe.rgsekai.sekaitune.LocalDownloadUtil
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AppBarHeight
import moe.rgsekai.sekaitune.constants.DisableBlurKey
import moe.rgsekai.sekaitune.constants.HideExplicitKey
import moe.rgsekai.sekaitune.db.entities.PlaylistEntity
import moe.rgsekai.sekaitune.db.entities.PlaylistSongMap
import moe.rgsekai.sekaitune.extensions.metadata
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint
import moe.rgsekai.sekaitune.innertube.utils.parseSongCount
import moe.rgsekai.sekaitune.models.toMediaMetadata
import moe.rgsekai.sekaitune.playback.queues.YouTubeQueue
import moe.rgsekai.sekaitune.ui.component.DraggableScrollbar
import moe.rgsekai.sekaitune.ui.component.ExpressivePullToRefreshBox
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.LocalMenuState
import androidx.compose.foundation.layout.statusBars
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.ui.component.FeaturedArtistItem
import moe.rgsekai.sekaitune.ui.component.FeaturedArtistsRow
import moe.rgsekai.sekaitune.ui.component.PlaylistEndOfLine
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroBackdrop
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroHeader
import moe.rgsekai.sekaitune.ui.component.PlaylistTopBarOverlay
import moe.rgsekai.sekaitune.ui.component.rememberPlaylistCoverHeight
import moe.rgsekai.sekaitune.ui.component.YouTubeListItem
import moe.rgsekai.sekaitune.ui.component.shimmer.ButtonPlaceholder
import moe.rgsekai.sekaitune.ui.component.shimmer.ListItemPlaceHolder
import moe.rgsekai.sekaitune.ui.component.shimmer.ShimmerHost
import moe.rgsekai.sekaitune.ui.component.shimmer.TextPlaceholder
import moe.rgsekai.sekaitune.ui.menu.SelectionMediaMetadataMenu
import moe.rgsekai.sekaitune.ui.menu.YouTubePlaylistMenu
import moe.rgsekai.sekaitune.ui.menu.YouTubeSongMenu
import moe.rgsekai.sekaitune.ui.screens.playlist.PlaylistHeroImageResult
import moe.rgsekai.sekaitune.ui.screens.playlist.ResolvePlaylistHeroImage
import moe.rgsekai.sekaitune.ui.theme.PlayerColorExtractor
import moe.rgsekai.sekaitune.ui.utils.DownloadProgressFloatingToolbar
import moe.rgsekai.sekaitune.ui.utils.DownloadProgressToolbarState
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadItem
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadProgressIndicator
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadState
import moe.rgsekai.sekaitune.ui.utils.ItemWrapper
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.ui.utils.formatCompactCount
import moe.rgsekai.sekaitune.ui.utils.hasActiveDownloads
import moe.rgsekai.sekaitune.ui.utils.headerDownloadState
import moe.rgsekai.sekaitune.ui.utils.sendAddMissingDownloads
import moe.rgsekai.sekaitune.ui.utils.sendPauseDownloads
import moe.rgsekai.sekaitune.ui.utils.sendRemoveDownloads
import moe.rgsekai.sekaitune.ui.utils.sendResumeDownloads
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.viewmodels.OnlinePlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
private const val OnlinePlaylistPaletteCacheSize = 24
private val onlinePlaylistGradientColorsCache =
    object : java.util.LinkedHashMap<String, List<Color>>(OnlinePlaylistPaletteCacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Color>>?): Boolean {
            return size > OnlinePlaylistPaletteCacheSize
        }
    }

@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val viewCounts by viewModel.viewCounts.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }
    var downloadProgressToolbarDismissed by remember { mutableStateOf(true) }

    var selection by remember { mutableStateOf(false) }
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by
        rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs.mapIndexed { index, song -> index to song }
            } else {
                songs
                    .mapIndexed { index, song -> index to song }
                    .filter { (_, song) ->
                        song.title.contains(query.text, ignoreCase = true) ||
                            song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                    }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler { selection = false }
    }

    val wrappedSongs =
        remember(filteredSongs) { filteredSongs.map { item -> ItemWrapper(item) } }
            .toMutableStateList()

    LaunchedEffect(songs) {
        val songIds = songs.map { it.id }
        if (songIds.isEmpty()) {
            downloads = emptyMap()
            downloadState = HeaderDownloadState.None
            return@LaunchedEffect
        }
        downloadUtil.downloads.collect { currentDownloads ->
            downloads = currentDownloads
            downloadState = headerDownloadState(songIds, currentDownloads)
        }
    }

    val heroImageResult = remember(playlist, songs) {
        ResolvePlaylistHeroImage.resolve(
            customImageUri = null,
            platformCoverUrl = playlist?.thumbnail,
            firstSongThumbnailUrl = songs.firstOrNull()?.thumbnail,
        )
    }

    val heroImageSource = remember(heroImageResult) {
        when (heroImageResult) {
            is PlaylistHeroImageResult.Image -> heroImageResult.urlOrUri
            is PlaylistHeroImageResult.Placeholder -> null
        }
    }

    val featuredArtists = remember(songs) {
        songs.flatMap { it.artists }
            .groupBy { it.id }
            .values
            .sortedByDescending { it.size }
            .take(10)
            .mapNotNull { artistList ->
                artistList.firstOrNull()?.let {
                    FeaturedArtistItem(
                        id = it.id.orEmpty(),
                        name = it.name,
                        thumbnailUrl = null,
                    )
                }
            }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 ||
                (lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset > 450)
        }
    }

    // Gradient colors state for playlist cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Extract gradient colors from resolved hero image
    LaunchedEffect(heroImageSource) {
        val imageSource = heroImageSource
        if (imageSource != null) {
            val cachedColors = onlinePlaylistGradientColorsCache[imageSource]
            if (cachedColors != null) {
                gradientColors = cachedColors
                return@LaunchedEffect
            }

            val request =
                ImageRequest
                    .Builder(context)
                    .data(imageSource)
                    .size(
                        PlayerColorExtractor.Config.IMAGE_SIZE,
                        PlayerColorExtractor.Config.IMAGE_SIZE,
                    ).allowHardware(false)
                    .build()

            val extractedColors =
                runCatching {
                    val result =
                        withContext(Dispatchers.IO) {
                            context.imageLoader.execute(request)
                        }
                    val bitmap = result.image?.toBitmap() ?: return@runCatching emptyList()
                    val palette =
                        withContext(Dispatchers.Default) {
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        }
                    PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColor,
                    )
                }.getOrDefault(emptyList())

            if (extractedColors.isNotEmpty()) {
                onlinePlaylistGradientColorsCache[imageSource] = extractedColors
            }
            gradientColors = extractedColors
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
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "onlinePlaylistBgColor",
    )

    val animatedDominantColor by animateColorAsState(
        targetValue = gradientColors.firstOrNull() ?: effectivePageBackgroundColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "onlinePlaylistDominantColor",
    )

    val pageGradientColors =
        remember(gradientColors) {
            if (gradientColors.size < 2) {
                emptyList()
            } else {
                listOf(
                    gradientColors[0].copy(alpha = 0.50f),
                    gradientColors[1].copy(alpha = 0.20f),
                    Color.Transparent,
                )
            }
        }

    // Calculate gradient opacity based on scroll position
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
        derivedStateOf { !disableBlur && !selection && !showTopBarTitle && !isSearching }
    }

    val headerItems by remember {
        derivedStateOf {
            val current = playlist
            if (!isLoading && current != null && !isSearching) 1 else 0
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            if (
                songs.size >= 5 &&
                lastVisibleIndex != null &&
                lastVisibleIndex >= songs.size - 5
            ) {
                viewModel.loadMoreSongs()
            }
        }
    }

    val coverHeight = rememberPlaylistCoverHeight()

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
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

        LazyColumn(
            state = lazyListState,
            contentPadding = listContentPadding,
        ) {
            playlist.let { playlist ->
                if (isLoading) {
                    // Shimmer Loading State
                    item(key = "shimmer") {
                        ShimmerHost {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Playlist hero placeholder
                                val heroHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.44f).coerceIn(300.dp, 400.dp)
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(heroHeight)
                                            .shimmer()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Title placeholder
                                TextPlaceholder(
                                    height = 28.dp,
                                    modifier =
                                        Modifier.fillMaxWidth(0.6f).padding(horizontal = 32.dp),
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Author placeholder
                                TextPlaceholder(
                                    height = 20.dp,
                                    modifier = Modifier.fillMaxWidth(0.4f),
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Metadata placeholder
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                ) {
                                    repeat(2) {
                                        TextPlaceholder(
                                            height = 32.dp,
                                            modifier = Modifier.width(80.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Buttons placeholder
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface),
                                    )
                                    ButtonPlaceholder(modifier = Modifier.weight(1f).height(48.dp))
                                    ButtonPlaceholder(modifier = Modifier.weight(1f).height(48.dp))
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .shimmer()
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface),
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }

                            repeat(6) { ListItemPlaceHolder() }
                        }
                    }
                } else if (playlist != null) {
                    if (!isSearching) {
                        item(key = "header") {
                            val durationText = remember(songs) {
                                val totalSeconds = songs.sumOf { it.duration ?: 0 }
                                val minutes = (totalSeconds + 30) / 60
                                if (minutes >= 60) {
                                    val hours = minutes / 60
                                    val remMin = minutes % 60
                                    if (remMin > 0) "${hours}h ${remMin}m" else "${hours}h"
                                } else {
                                    "${minutes}m"
                                }
                            }
                            val songCountText = playlist.songCountText ?: "${songs.size} ${if (songs.size == 1) "song" else "songs"}"
                            val statsText = "$songCountText · $durationText"

                            PlaylistHeroHeader(
                                title = playlist.title,
                                stats = statsText,
                                isPlaying = isPlaying,
                                onPlayClick = {
                                    val playEp = playlist.playEndpoint
                                    if (isPlaying) {
                                        playerConnection.player.pause()
                                    } else if (playEp != null) {
                                        playerConnection.playQueue(
                                            YouTubeQueue.playlist(playEp),
                                        )
                                    } else if (songs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = playlist.title,
                                                items = songs.map { it.toMediaItem() },
                                            ),
                                        )
                                    }
                                },
                                onShuffleClick = {
                                    val shuffleEp = playlist.shuffleEndpoint
                                    if (shuffleEp != null) {
                                        playerConnection.playQueue(
                                            YouTubeQueue.playlist(shuffleEp),
                                        )
                                    } else if (songs.isNotEmpty()) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = playlist.title,
                                                items = songs.shuffled().map { it.toMediaItem() },
                                            ),
                                        )
                                    }
                                },
                                onSearchClick = { isSearching = true },
                                coverHeight = coverHeight,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (songs.isEmpty() && !isLoading && error == null) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.empty_playlist),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_playlist_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Songs List
                    items(
                        items = wrappedSongs,
                        key = { it.item.second.setVideoId ?: "${it.item.second.id}-${it.item.first}" },
                        contentType = { "online_playlist_song" },
                    ) { song ->
                        YouTubeListItem(
                            item = song.item.second,
                            viewCountText =
                                viewCounts[song.item.second.id]?.let { count ->
                                    formatCompactCount(count.toLong())
                                },
                            isActive = mediaMetadata?.id == song.item.second.id,
                            isPlaying = isPlaying,
                            isSelected = song.isSelected && selection,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song.item.second,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
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
                            },
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        enabled = !hideExplicit || !song.item.second.explicit,
                                        onClick = {
                                            if (!selection) {
                                                if (song.item.second.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue.playlist(
                                                            endpoint =
                                                                song.item.second
                                                                    .toPlaylistPlaybackEndpoint(
                                                                        playlistId = playlist.id,
                                                                        playlistPlayParams =
                                                                            playlist.playEndpoint
                                                                                ?.params,
                                                                    ),
                                                            preloadItem = song.item.second.toMediaMetadata(),
                                                        ),
                                                    )
                                                }
                                            } else {
                                                song.isSelected = !song.isSelected
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                            if (!selection) {
                                                selection = true
                                            }
                                            wrappedSongs.forEach { it.isSelected = false }
                                            song.isSelected = true
                                        },
                                    ).animateItem(),
                        )
                    }

                    if (viewModel.continuation != null && songs.isNotEmpty() && isLoadingMore) {
                        item(key = "loading_more") {
                            ShimmerHost { repeat(2) { ListItemPlaceHolder() } }
                        }
                    }

                    // End of List Footer
                    if (!isSearching && songs.isNotEmpty()) {
                        item(key = "end_of_line_footer") {
                            val totalSeconds = songs.sumOf { it.duration ?: 0 }
                            PlaylistEndOfLine(
                                songCount = songs.size,
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
                                    navController.navigate("artist/${artist.id}")
                                },
                            )
                        }
                    }
                } else {
                    val isPrivatePlaylist = error?.contains("PLAYLIST_PRIVATE") == true
                    item(key = "error") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (isPrivatePlaylist) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_empty_state),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.playlist_private_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.playlist_private_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                Text(
                                    text =
                                        if (error != null) {
                                            stringResource(R.string.error_unknown)
                                        } else {
                                            stringResource(R.string.playlist_not_found)
                                        },
                                    style = MaterialTheme.typography.titleLarge,
                                    color =
                                        if (error != null) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (error != null) {
                                            error!!
                                        } else {
                                            stringResource(R.string.playlist_not_found_desc)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (error != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.retry() }, shapes = ButtonDefaults.shapes()) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = headerItems,
        )

        if (!selection && !isSearching) {
            PlaylistTopBarOverlay(
                title = playlist?.title.orEmpty(),
                scrolled = showTopBarTitle,
                backgroundColor = animatedBackgroundColor,
                onBackClick = { navController.navigateUp() },
                onMoreClick = {
                    val currentPlaylist = playlist
                    if (currentPlaylist != null) {
                        menuState.show {
                            YouTubePlaylistMenu(
                                playlist = currentPlaylist,
                                songs = songs,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                                selectAction = { selection = true },
                                canSelect = true,
                                snackbarHostState = snackbarHostState,
                            )
                        }
                    }
                },
            )
        } else {
        // Top App Bar
        val topAppBarColors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = animatedBackgroundColor,
                    scrolledContainerColor = animatedBackgroundColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            }

        val navButtonColors =
            if (transparentAppBar) {
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White,
                )
            } else {
                IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                )
            }

        TopAppBar(
            colors = topAppBarColors,
            title = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
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
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.title.orEmpty())
                }
            },
            navigationIcon = {
                IconButton(
                    colors = navButtonColors,
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else if (selection) {
                            selection = false
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching && !selection) {
                            navController.backToMain()
                        }
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (selection) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    IconButton(
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == wrappedSongs.size) {
                                        R.drawable.deselect
                                    } else {
                                        R.drawable.select_all
                                    },
                                ),
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionMediaMetadataMenu(
                                    songSelection =
                                        wrappedSongs
                                            .filter { it.isSelected }
                                            .map {
                                                it.item.second
                                                    .toMediaItem()
                                                    .metadata!!
                                            },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                    currentItems = emptyList(),
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
                        colors = navButtonColors,
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
        )
        }

        val currentDownloadState = downloadState
        val showDownloadProgressToolbar =
            currentDownloadState is HeaderDownloadState.Partial &&
                songs.isNotEmpty() &&
                !downloadProgressToolbarDismissed
        AnimatedVisibility(
            visible = showDownloadProgressToolbar,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
                    .padding(bottom = 16.dp),
        ) {
            if (currentDownloadState is HeaderDownloadState.Partial && songs.isNotEmpty()) {
                val songIds = remember(songs) { songs.map { it.id } }
                DownloadProgressFloatingToolbar(
                    state =
                        DownloadProgressToolbarState(
                            progress = currentDownloadState.progress,
                            paused = currentDownloadState.paused,
                            canPause = hasActiveDownloads(songIds, downloads),
                        ),
                    onPauseResume = {
                        if (currentDownloadState.paused) {
                            sendResumeDownloads(context, songIds)
                        } else {
                            sendPauseDownloads(context, songIds)
                        }
                    },
                    onDismiss = {
                        downloadProgressToolbarDismissed = true
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime),
                    ).align(Alignment.BottomCenter),
        )
    }
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

private fun SongItem.toPlaylistPlaybackEndpoint(
    playlistId: String,
    playlistPlayParams: String?,
): WatchEndpoint {
    val baseEndpoint = endpoint ?: WatchEndpoint(videoId = id)
    return baseEndpoint.copy(
        videoId = baseEndpoint.videoId ?: id,
        playlistId = baseEndpoint.playlistId ?: playlistId,
        playlistSetVideoId = baseEndpoint.playlistSetVideoId ?: setVideoId,
        params = baseEndpoint.params ?: playlistPlayParams,
    )
}




