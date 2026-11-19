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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroupDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.DisableBlurKey
import moe.rgsekai.sekaitune.constants.HideExplicitKey
import moe.rgsekai.sekaitune.constants.SongSortDescendingKey
import moe.rgsekai.sekaitune.constants.SongSortType
import moe.rgsekai.sekaitune.constants.SongSortTypeKey
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.ui.component.DraggableScrollbar
import moe.rgsekai.sekaitune.ui.component.EmptyPlaceholder
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.LocalMenuState
import moe.rgsekai.sekaitune.ui.component.SongListItem
import moe.rgsekai.sekaitune.ui.component.SortHeader
import moe.rgsekai.sekaitune.ui.menu.SelectionSongMenu
import moe.rgsekai.sekaitune.ui.menu.SongMenu
import moe.rgsekai.sekaitune.ui.theme.PlayerColorExtractor
import moe.rgsekai.sekaitune.ui.utils.ItemWrapper
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.viewmodels.CachePlaylistViewModel
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CachePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: CachePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val cachedSongs by viewModel.cachedSongs.collectAsState()

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            SongSortTypeKey,
            SongSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val wrappedSongs =
        remember(cachedSongs, sortType, sortDescending) {
            val sortedSongs =
                when (sortType) {
                    SongSortType.CREATE_DATE -> {
                        cachedSongs.sortedBy { it.song.dateDownload ?: LocalDateTime.MIN }
                    }

                    SongSortType.NAME -> {
                        cachedSongs.sortedBy { it.song.title }
                    }

                    SongSortType.ARTIST -> {
                        cachedSongs.sortedBy { song ->
                            song.artists.joinToString(separator = "") { artist -> artist.name }
                        }
                    }

                    SongSortType.PLAY_TIME -> {
                        cachedSongs.sortedBy { it.song.totalPlayTime }
                    }
                }.let { if (sortDescending) it.reversed() else it }

            sortedSongs.map { song -> ItemWrapper(song) }
        }.toMutableStateList()

    var selection by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()

    val selectedCount by remember(wrappedSongs) {
        derivedStateOf { wrappedSongs.count { it.isSelected } }
    }

    LaunchedEffect(selectedCount) {
        if (selection && selectedCount == 0) {
            selection = false
        }
    }

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
        BackHandler {
            selection = false
        }
    }

    val filteredSongs =
        remember(wrappedSongs, query) {
            if (query.text.isEmpty()) {
                wrappedSongs
            } else {
                wrappedSongs.filter { wrapper ->
                    val song = wrapper.item
                    song.title.contains(query.text, true) ||
                        song.artists.any { it.name.contains(query.text, true) }
                }
            }
        }

    // Gradient colors state for playlist cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    // Capture fallback color in composable context
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    val coverHeight = rememberPlaylistCoverHeight()

    val heroImageResult = remember(cachedSongs) {
        ResolvePlaylistHeroImage.resolve(
            firstSongThumbnailUrl = cachedSongs.firstOrNull()?.thumbnailUrl,
        )
    }

    // Extract gradient colors from playlist cover (first song thumbnail)
    LaunchedEffect(cachedSongs) {
        val thumbnailUrl = cachedSongs.firstOrNull()?.thumbnailUrl
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
        label = "cachePlaylistBg",
    )

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 300
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            !disableBlur && !selection && !showTopBarTitle
        }
    }

    val headerItems by remember {
        derivedStateOf {
            if (filteredSongs.isNotEmpty() && !isSearching) 2 else 0
        }
    }

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Box(
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
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (filteredSongs.isEmpty() && !isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.playlist_is_empty),
                    )
                }
            }

            if (filteredSongs.isEmpty() && isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found),
                    )
                }
            } else {
                if (filteredSongs.isNotEmpty() && !isSearching) {
                    // Hero Header Item
                    item(key = "header") {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val songCountStr = pluralStringResource(R.plurals.n_song, filteredSongs.size, filteredSongs.size)

                            PlaylistHeroTitleArea(
                                title = stringResource(R.string.cached_playlist),
                                subtitle = songCountStr,
                                coverHeight = coverHeight,
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = "Cache Songs",
                                                items = filteredSongs.map { it.item.toMediaItem() },
                                            ),
                                        )
                                    },
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
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
                                    onCheckedChange = {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = "Cache Songs",
                                                items = filteredSongs.shuffled().map { it.item.toMediaItem() },
                                            ),
                                        )
                                    },
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
                                    onCheckedChange = {
                                        playerConnection.addToQueue(
                                            items = filteredSongs.map { it.item.toMediaItem() },
                                        )
                                    },
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
                                        painter = painterResource(R.drawable.queue_music),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                if (filteredSongs.isNotEmpty()) {
                    // Sort Header
                    item(key = "sortHeader") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        SongSortType.NAME -> R.string.sort_by_name
                                        SongSortType.ARTIST -> R.string.sort_by_artist
                                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // Song items
                itemsIndexed(filteredSongs, key = { _, song -> song.item.id }) { index, songWrapper ->
                    SongListItem(
                        song = songWrapper.item,
                        isActive = songWrapper.item.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        isSelected = songWrapper.isSelected && selection,
                        showInLibraryIcon = true,
                        trailingContent = {
                            androidx.compose.material3.IconButton(onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = songWrapper.item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                        isFromCache = true,
                                    )
                                }
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!selection) {
                                            if (songWrapper.item.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = "Cache Songs",
                                                        items = cachedSongs.map { it.toMediaItem() },
                                                        startIndex = cachedSongs.indexOfFirst { it.id == songWrapper.item.id },
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

        if (selection || isSearching) {
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
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
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
                    }, onLongClick = {
                        if (!isSearching && !selection) {
                            navController.backToMain()
                        }
                    }) {
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
                        androidx.compose.material3.IconButton(onClick = {
                            wrappedSongs.filter { it.isSelected }.forEach {
                                viewModel.removeSongFromCache(it.item.id)
                            }
                            selection = false
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.remove_from_cache),
                            )
                        }

                        androidx.compose.material3.IconButton(onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                                selection = false
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        }) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all,
                                    ),
                                contentDescription = null,
                            )
                        }

                        androidx.compose.material3.IconButton(onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = wrappedSongs.filter { it.isSelected }.map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                    isFromCache = true,
                                    onRemoveFromCache = { songs ->
                                        songs.forEach { viewModel.removeSongFromCache(it.id) }
                                    },
                                )
                            }
                        }) {
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
                title = stringResource(R.string.cached_playlist),
                scrolled = showTopBarTitle,
                backgroundColor = animatedBackgroundColor,
                onBackClick = { navController.navigateUp() },
                onSearchClick = { isSearching = true },
                onMoreClick = null,
            )
        }
    }
}




