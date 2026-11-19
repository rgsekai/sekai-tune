/*
 * Sekai Tune (2026)
 * Â© Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalDownloadUtil
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.DisableBlurKey
import moe.rgsekai.sekaitune.constants.MyTopFilter
import moe.rgsekai.sekaitune.db.entities.Song
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.DraggableScrollbar
import moe.rgsekai.sekaitune.ui.component.EmptyPlaceholder
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.LocalMenuState
import androidx.compose.foundation.layout.statusBars
import moe.rgsekai.sekaitune.ui.component.FeaturedArtistItem
import moe.rgsekai.sekaitune.ui.component.FeaturedArtistsRow
import moe.rgsekai.sekaitune.ui.component.PlaylistEndOfLine
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroBackdrop
import moe.rgsekai.sekaitune.ui.component.PlaylistHeroHeader
import moe.rgsekai.sekaitune.ui.component.PlaylistSortTextControl
import moe.rgsekai.sekaitune.ui.component.PlaylistTopBarOverlay
import moe.rgsekai.sekaitune.ui.component.rememberPlaylistCoverHeight
import moe.rgsekai.sekaitune.ui.utils.navigateToArtist
import moe.rgsekai.sekaitune.ui.component.SongListItem
import moe.rgsekai.sekaitune.ui.component.SortHeader
import moe.rgsekai.sekaitune.ui.menu.SelectionSongMenu
import moe.rgsekai.sekaitune.ui.menu.SongMenu
import moe.rgsekai.sekaitune.ui.screens.playlist.PlaylistHeroImageResult
import moe.rgsekai.sekaitune.ui.screens.playlist.ResolvePlaylistHeroImage
import moe.rgsekai.sekaitune.ui.theme.PlayerColorExtractor
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadItem
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadProgressIndicator
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadState
import moe.rgsekai.sekaitune.ui.utils.ItemWrapper
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.ui.utils.headerDownloadState
import moe.rgsekai.sekaitune.ui.utils.sendAddMissingDownloads
import moe.rgsekai.sekaitune.ui.utils.sendPauseDownloads
import moe.rgsekai.sekaitune.ui.utils.sendRemoveDownloads
import moe.rgsekai.sekaitune.ui.utils.sendResumeDownloads
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.viewmodels.TopPlaylistViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TopPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: TopPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val maxSize = viewModel.top

    val songs by viewModel.topSongs.collectAsState(null)
    val mutableSongs = remember { mutableStateListOf<Song>() }

    val likeLength =
        remember(songs) {
            songs?.fastSumBy { it.song.duration } ?: 0
        }

    val wrappedSongs =
        remember(songs) {
            songs?.map { item -> ItemWrapper(item) }?.toMutableStateList() ?: mutableStateListOf()
        }

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var selection by remember { mutableStateOf(false) }
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
        }
    }

    val sortType by viewModel.topPeriod.collectAsState()
    val name = stringResource(R.string.my_top) + " $maxSize"

    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            songs?.let { addAll(it) }
        }
        val songIds = songs?.map { it.song.id }.orEmpty()
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

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.remove_download_playlist_confirm, name),
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
                            songIds = songs.orEmpty().map { it.song.id },
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val filteredSongs =
        remember(wrappedSongs, query) {
            if (query.text.isEmpty()) {
                wrappedSongs
            } else {
                wrappedSongs.filter { wrapper ->
                    val song = wrapper.item
                    song.song.title.contains(query.text, true) ||
                        song.artists.any { it.name.contains(query.text, true) }
                }
            }
        }

    val lazyListState = rememberLazyListState()

    // Gradient colors state for playlist cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    // Capture fallback color in composable context
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    val heroImageResult = remember(songs) {
        ResolvePlaylistHeroImage.resolve(
            customImageUri = null,
            platformCoverUrl = null,
            firstSongThumbnailUrl = songs?.firstOrNull()?.song?.thumbnailUrl,
        )
    }

    val heroImageSource = remember(heroImageResult) {
        when (heroImageResult) {
            is PlaylistHeroImageResult.Image -> heroImageResult.urlOrUri
            is PlaylistHeroImageResult.Placeholder -> null
        }
    }

    val featuredArtists = remember(songs) {
        songs.orEmpty()
            .flatMap { it.artists }
            .groupBy { it.id }
            .values
            .sortedByDescending { it.size }
            .take(10)
            .mapNotNull { artistList ->
                artistList.firstOrNull()?.let {
                    FeaturedArtistItem(
                        id = it.id,
                        name = it.name,
                        thumbnailUrl = null,
                    )
                }
            }
    }

    val effectiveThumbnailUrl = remember(heroImageSource) {
        heroImageSource
    }

    // Extract gradient colors from playlist cover (first song thumbnail)
    LaunchedEffect(effectiveThumbnailUrl) {
        val thumbnailUrl = effectiveThumbnailUrl
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

                    val extractedColors =
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    gradientColors = extractedColors
                }
            }
        } else {
            gradientColors = emptyList()
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

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            !disableBlur && !selection && !showTopBarTitle
        }
    }

    val headerItems by remember {
        derivedStateOf {
            val currentSongs = songs
            if (currentSongs != null && currentSongs.isNotEmpty() && !isSearching) 2 else 0
        }
    }

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val animatedBackgroundColor by animateColorAsState(
        targetValue = surfaceColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "topPlaylistBgColor",
    )

    val animatedDominantColor by animateColorAsState(
        targetValue = gradientColors.firstOrNull() ?: surfaceColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "topPlaylistDominantColor",
    )

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

    Box(
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
        ) {
            if (songs != null) {
                if (songs!!.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                        )
                    }
                } else {
                if (!isSearching && songs != null && songs!!.isNotEmpty()) {
                    item(key = "header") {
                        val durationText = remember(likeLength) {
                            val totalSeconds = likeLength
                            val minutes = (totalSeconds + 30) / 60
                            if (minutes >= 60) {
                                val hours = minutes / 60
                                val remMin = minutes % 60
                                if (remMin > 0) "${hours}h ${remMin}m" else "${hours}h"
                            } else {
                                "${minutes}m"
                            }
                        }
                        val statsText = "${songs!!.size} ${if (songs!!.size == 1) "song" else "songs"} · $durationText"

                        PlaylistHeroHeader(
                            title = name,
                            stats = statsText,
                            isPlaying = isPlaying,
                            onPlayClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = name,
                                        items = songs!!.map { it.toMediaItem() },
                                    ),
                                )
                            },
                            onShuffleClick = {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = name,
                                        items = songs!!.shuffled().map { it.toMediaItem() },
                                    ),
                                )
                            },
                            onSearchClick = { isSearching = true },
                            coverHeight = coverHeight,
                            sortControl = {
                                PlaylistSortTextControl(
                                    current = sortType,
                                    descending = false,
                                    options = listOf(
                                        MyTopFilter.ALL_TIME,
                                        MyTopFilter.DAY,
                                        MyTopFilter.WEEK,
                                        MyTopFilter.MONTH,
                                        MyTopFilter.YEAR,
                                    ),
                                    label = { type: MyTopFilter ->
                                        when (type) {
                                            MyTopFilter.ALL_TIME -> context.getString(R.string.all_time)
                                            MyTopFilter.DAY -> context.getString(R.string.past_24_hours)
                                            MyTopFilter.WEEK -> context.getString(R.string.past_week)
                                            MyTopFilter.MONTH -> context.getString(R.string.past_month)
                                            MyTopFilter.YEAR -> context.getString(R.string.past_year)
                                        }
                                    },
                                    onSelect = { viewModel.topPeriod.value = it },
                                    onToggleDirection = {},
                                )
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                    // Song items
                    itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.item.id },
                    ) { index, songWrapper ->
                        SongListItem(
                            song = songWrapper.item,
                            albumIndex = index + 1,
                            isActive = songWrapper.item.song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = {
                                androidx.compose.material3.IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = songWrapper.item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                    )
                                }
                            },
                            isSelected = songWrapper.isSelected && selection,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (!selection) {
                                                if (songWrapper.item.song.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = name,
                                                            items = songs!!.map { it.toMediaItem() },
                                                            startIndex = songs!!.indexOfFirst { it.id == songWrapper.item.id },
                                                        ),
                                                    )
                                                }
                                            } else {
                                                songWrapper.isSelected = !songWrapper.isSelected
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!selection) {
                                                selection = true
                                                wrappedSongs.forEach { it.isSelected = false }
                                                songWrapper.isSelected = true
                                            }
                                        },
                                    ).animateItem(),
                        )
                    }
                }
            }
            // End of List Footer
            if (!isSearching && songs != null && songs!!.isNotEmpty()) {
                item(key = "end_of_line_footer") {
                    PlaylistEndOfLine(
                        songCount = songs!!.size,
                        totalSeconds = likeLength.toInt(),
                    )
                }
            }

            // Featured Artists
            if (!isSearching && featuredArtists.isNotEmpty()) {
                item(key = "featured_artists_row") {
                    FeaturedArtistsRow(
                        artists = featuredArtists,
                        onArtistClick = { artist ->
                            navController.navigateToArtist(artist.id)
                        },
                    )
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
                title = name,
                scrolled = showTopBarTitle,
                backgroundColor = animatedBackgroundColor,
                onBackClick = { navController.navigateUp() },
                onMoreClick = null,
            )
        } else {
        TopAppBar(
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = if (transparentAppBar) Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            title = {
                when {
                    selection -> {
                        val count = wrappedSongs.count { it.isSelected }
                        Text(
                            text = pluralStringResource(R.plurals.n_song, count, count),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }

                    isSearching -> {
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
                    }

                    showTopBarTitle -> {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        when {
                            isSearching -> {
                                isSearching = false
                                query = TextFieldValue()
                                focusManager.clearFocus()
                            }

                            selection -> {
                                selection = false
                            }

                            else -> {
                                navController.navigateUp()
                            }
                        }
                    },
                    onLongClick = {
                        if (!isSearching && !selection) {
                            navController.backToMain()
                        }
                    },
                    colors =
                        if (transparentAppBar) {
                            IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.35f),
                                contentColor = Color.White,
                            )
                        } else {
                            IconButtonDefaults.iconButtonColors()
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
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }

                    androidx.compose.material3.IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        wrappedSongs
                                            .filter { it.isSelected }
                                            .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    androidx.compose.material3.IconButton(
                        onClick = { isSearching = true },
                        colors =
                            if (transparentAppBar) {
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.35f),
                                    contentColor = Color.White,
                                )
                            } else {
                                IconButtonDefaults.iconButtonColors()
                            },
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
}
}




