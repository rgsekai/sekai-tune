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
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroBackdrop
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroTitleArea
import moe.rgsekai.sekaitune.ui.component.PlaylistTopBarOverlay
import moe.rgsekai.sekaitune.ui.component.rememberPlaylistCoverHeight
import moe.rgsekai.sekaitune.ui.menu.YouTubePlaylistMenu
import moe.rgsekai.sekaitune.ui.screens.playlist.PlaylistHeroImageResult
import moe.rgsekai.sekaitune.ui.screens.playlist.ResolvePlaylistHeroImage
import androidx.compose.material3.Icon
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
import moe.rgsekai.sekaitune.ui.component.YouTubeListItem
import moe.rgsekai.sekaitune.ui.component.shimmer.ButtonPlaceholder
import moe.rgsekai.sekaitune.ui.component.shimmer.ListItemPlaceHolder
import moe.rgsekai.sekaitune.ui.component.shimmer.ShimmerHost
import moe.rgsekai.sekaitune.ui.component.shimmer.TextPlaceholder
import moe.rgsekai.sekaitune.ui.menu.SelectionMediaMetadataMenu
import moe.rgsekai.sekaitune.ui.menu.YouTubePlaylistMenu
import moe.rgsekai.sekaitune.ui.menu.YouTubeSongMenu
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

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 300
        }
    }

    val coverHeight = rememberPlaylistCoverHeight()

    val heroImageResult = remember(playlist, songs) {
        ResolvePlaylistHeroImage.resolve(
            platformCoverUrl = playlist?.thumbnail,
            firstSongThumbnailUrl = songs.firstOrNull()?.thumbnail,
        )
    }

    // Gradient colors state for playlist cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    val effectiveThumbnailUrl = remember(heroImageResult) {
        when (heroImageResult) {
            is PlaylistHeroImageResult.Image -> heroImageResult.urlOrUri
            is PlaylistHeroImageResult.Placeholder -> null
        }
    }

    // Extract gradient colors from top/first song or playlist cover
    LaunchedEffect(effectiveThumbnailUrl) {
        val thumbnailUrl = effectiveThumbnailUrl
        if (thumbnailUrl != null) {
            val cachedColors = onlinePlaylistGradientColorsCache[thumbnailUrl]
            if (cachedColors != null) {
                gradientColors = cachedColors
                return@LaunchedEffect
            }

            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
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
                onlinePlaylistGradientColorsCache[thumbnailUrl] = extractedColors
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
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "onlinePlaylistBg",
    )

    val transparentAppBar by remember {
        derivedStateOf { !disableBlur && !selection && !showTopBarTitle }
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

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
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
            contentPadding =
                LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            playlist.let { playlist ->
                if (isLoading) {
                    // Shimmer Loading State
                    item(key = "shimmer") {
                        ShimmerHost {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                // Playlist art placeholder
                                Box(
                                    modifier =
                                        Modifier
                                            .padding(top = 8.dp, bottom = 20.dp)
                                            .size(240.dp)
                                            .shimmer()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.onSurface),
                                )

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
                        // Hero Header
                        item(key = "header") {
                            Column(
                                modifier = Modifier.fillMaxWidth().animateItem(),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                val authorName = playlist.author?.name
                                val countText = playlist.songCountText
                                val subtitle = listOfNotNull(authorName, countText).joinToString(" · ")

                                PlaylistHeroTitleArea(
                                    title = playlist.title,
                                    subtitle = subtitle,
                                    coverHeight = coverHeight,
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Action Buttons Row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val hasLike = playlist.id != "LM"
                                    val hasPlay = playlist.playEndpoint != null
                                    val hasShuffle = playlist.shuffleEndpoint != null
                                    val hasRadio = playlist.radioEndpoint != null
                                    val activeIndices =
                                        remember(hasLike, hasPlay, hasShuffle, hasRadio) {
                                            listOf(hasLike, hasPlay, hasShuffle, hasRadio, true, true)
                                                .withIndex()
                                                .filter { it.value }
                                                .map { it.index }
                                        }

                                    @Composable
                                    fun shapeFor(slotIndex: Int) =
                                        when {
                                            activeIndices.first() == slotIndex && activeIndices.last() == slotIndex -> {
                                                ButtonGroupDefaults
                                                    .connectedLeadingButtonShapes()
                                            }

                                            activeIndices.first() == slotIndex -> {
                                                ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            }

                                            activeIndices.last() == slotIndex -> {
                                                ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            }

                                            else -> {
                                                ButtonGroupDefaults.connectedMiddleButtonShapes()
                                            }
                                        }

                                    if (hasLike) {
                                        val isBookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
                                        ToggleButton(
                                            checked = isBookmarked,
                                            onCheckedChange = {
                                                if (dbPlaylist?.playlist == null) {
                                                    database.transaction {
                                                        val existingPlaylist = playlistEntityByBrowseId(playlist.id)
                                                        val targetPlaylistId =
                                                            if (existingPlaylist == null) {                                                                 val playlistEntity =
                                                                    PlaylistEntity(
                                                                        name = playlist.title,
                                                                        browseId = playlist.id,
                                                                        thumbnailUrl =
                                                                            playlist.thumbnail,
                                                                        isEditable =
                                                                            playlist.isEditable,
                                                                        remoteSongCount = parseSongCount(playlist.songCountText),
                                                                        playEndpointParams =
                                                                            playlist.playEndpoint
                                                                                ?.params,
                                                                        shuffleEndpointParams =
                                                                            playlist.shuffleEndpoint
                                                                                ?.params,
                                                                        radioEndpointParams =
                                                                            playlist.radioEndpoint
                                                                                ?.params,
                                                                    ).toggleLike()
                                                                insert(playlistEntity)
                                                                playlistEntityByBrowseId(playlist.id)?.id ?: playlistEntity.id
                                                            } else {
                                                                val refreshedPlaylist =
                                                                    existingPlaylist.copy(
                                                                        name = playlist.title,
                                                                        browseId = playlist.id,
                                                                        thumbnailUrl = playlist.thumbnail,
                                                                        isEditable = playlist.isEditable,
                                                                        remoteSongCount = parseSongCount(playlist.songCountText),
                                                                        playEndpointParams =
                                                                            playlist.playEndpoint
                                                                                ?.params,
                                                                        shuffleEndpointParams =
                                                                            playlist.shuffleEndpoint
                                                                                ?.params,
                                                                        radioEndpointParams =
                                                                            playlist.radioEndpoint
                                                                                ?.params,
                                                                    )
                                                                update(
                                                                    if (existingPlaylist.bookmarkedAt == null) {
                                                                        refreshedPlaylist.toggleLike()
                                                                    } else {
                                                                        refreshedPlaylist
                                                                    },
                                                                )
                                                                existingPlaylist.id
                                                            }
                                                        if (songs.isNotEmpty()) {
                                                            clearPlaylist(targetPlaylistId)
                                                            songs
                                                                .onEach { song -> insert(song.toMediaMetadata()) }
                                                                .mapIndexed { index, song ->
                                                                    PlaylistSongMap(
                                                                        songId = song.id,
                                                                        playlistId = targetPlaylistId,
                                                                        position = index,
                                                                        setVideoId = song.setVideoId,
                                                                    )
                                                                }.forEach(::insert)
                                                        }
                                                    }
                                                } else {
                                                    database.transaction {
                                                        val currentPlaylist = dbPlaylist!!.playlist
                                                        update(currentPlaylist, playlist)
                                                        update(currentPlaylist.toggleLike())
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(56.dp),
                                            shapes = shapeFor(0),
                                            colors =
                                                ToggleButtonDefaults.toggleButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    checkedContentColor = MaterialTheme.colorScheme.error,
                                                ),
                                        ) {
                                            Icon(
                                                painter =
                                                    painterResource(
                                                        if (isBookmarked) {
                                                            R.drawable.favorite
                                                        } else {
                                                            R.drawable.favorite_border
                                                        },
                                                    ),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }

                                    playlist.playEndpoint?.let { playEndpoint ->
                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.playlist(playEndpoint),
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            shapes = shapeFor(1),
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
                                                contentDescription =
                                                    stringResource(R.string.play),
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }

                                    playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.playlist(shuffleEndpoint),
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            shapes = shapeFor(2),
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
                                                contentDescription =
                                                    stringResource(R.string.shuffle),
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }

                                    playlist.radioEndpoint?.let { radioEndpoint ->
                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(radioEndpoint),
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(56.dp),
                                            shapes = shapeFor(3),
                                            colors =
                                                ToggleButtonDefaults.toggleButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                                                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                                ),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.radio),
                                                contentDescription = stringResource(R.string.radio),
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    }

                                    ToggleButton(
                                        checked = downloadState == HeaderDownloadState.Completed,
                                        onCheckedChange = {
                                            val currentDownloadState = downloadState
                                            when (currentDownloadState) {
                                                HeaderDownloadState.Completed -> {
                                                    sendRemoveDownloads(
                                                        context = context,
                                                        songIds = songs.map { it.id },
                                                    )
                                                }

                                                is HeaderDownloadState.Partial -> {
                                                    val songIds = songs.map { it.id }
                                                    if (currentDownloadState.paused) {
                                                        sendResumeDownloads(context, songIds)
                                                    } else {
                                                        sendPauseDownloads(context, songIds)
                                                    }
                                                }

                                                HeaderDownloadState.None -> {
                                                    downloadProgressToolbarDismissed = false
                                                    sendAddMissingDownloads(
                                                        context = context,
                                                        songs =
                                                            songs.map { song ->
                                                                HeaderDownloadItem(
                                                                    id = song.id,
                                                                    title = song.title,
                                                                )
                                                            },
                                                        downloads = downloads,
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(56.dp),
                                        shapes = shapeFor(4),
                                        colors =
                                            ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                checkedContentColor = MaterialTheme.colorScheme.primary,
                                            ),
                                    ) {
                                        val state = downloadState
                                        when (state) {
                                            HeaderDownloadState.Completed -> {
                                                Icon(
                                                    painter = painterResource(R.drawable.offline),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }

                                            is HeaderDownloadState.Partial -> {
                                                HeaderDownloadProgressIndicator(
                                                    progress = state.progress,
                                                    paused = state.paused,
                                                )
                                            }

                                            HeaderDownloadState.None -> {
                                                Icon(
                                                    painter = painterResource(R.drawable.download),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }
                                        }
                                    }

                                    ToggleButton(
                                        checked = false,
                                        onCheckedChange = {
                                            menuState.show {
                                                YouTubePlaylistMenu(
                                                    playlist = playlist,
                                                    songs = songs,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                    selectAction = { selection = true },
                                                    canSelect = true,
                                                    snackbarHostState = snackbarHostState,
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(56.dp),
                                        shapes = shapeFor(5),
                                        colors =
                                            ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                checkedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                checkedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
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
                                    val mixEndpoint = playlist.shuffleEndpoint ?: playlist.radioEndpoint
                                    if (mixEndpoint != null) {
                                        Button(
                                            onClick = {
                                                playerConnection.playQueue(
                                                    if (mixEndpoint == playlist.shuffleEndpoint) {
                                                        YouTubeQueue.playlist(mixEndpoint)
                                                    } else {
                                                        YouTubeQueue(mixEndpoint)
                                                    },
                                                )
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.mix),
                                                contentDescription = stringResource(R.string.start_radio),
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))
                            }
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

        // Top App Bar
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
                    containerColor = effectivePageBackgroundColor,
                    scrolledContainerColor = effectivePageBackgroundColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                )
            }

        if (selection || isSearching) {
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
                    }
                },
            )
        }

        if (!selection && !isSearching) {
            PlaylistTopBarOverlay(
                title = playlist?.title.orEmpty(),
                scrolled = showTopBarTitle,
                backgroundColor = animatedBackgroundColor,
                onBackClick = { navController.navigateUp() },
                onSearchClick = { isSearching = true },
                onMoreClick = {
                    val currentPlaylist = playlist
                    if (currentPlaylist != null) {
                        menuState.show {
                            YouTubePlaylistMenu(
                                playlist = currentPlaylist,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
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




