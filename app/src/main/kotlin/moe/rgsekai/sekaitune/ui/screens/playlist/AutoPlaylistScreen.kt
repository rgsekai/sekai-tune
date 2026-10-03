/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SplitButtonDefaults
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastSumBy
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
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalDownloadUtil
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AutoPlaylistSongSortDescendingKey
import moe.rgsekai.sekaitune.constants.AutoPlaylistSongSortType
import moe.rgsekai.sekaitune.constants.AutoPlaylistSongSortTypeKey
import moe.rgsekai.sekaitune.constants.DisableBlurKey
import moe.rgsekai.sekaitune.constants.InnerTubeCookieKey
import moe.rgsekai.sekaitune.constants.YtmSyncKey
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.innertube.utils.hasYouTubeLoginCookie
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.DraggableScrollbar
import moe.rgsekai.sekaitune.ui.component.EmptyPlaceholder
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.LocalMenuState
import moe.rgsekai.sekaitune.ui.component.SongListItem
import moe.rgsekai.sekaitune.ui.component.SortHeader
import moe.rgsekai.sekaitune.ui.menu.SelectionSongMenu
import moe.rgsekai.sekaitune.ui.menu.SongMenu
import moe.rgsekai.sekaitune.ui.screens.downloads.DownloadLibraryScreen
import moe.rgsekai.sekaitune.ui.theme.PlayerColorExtractor
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadItem
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadProgressIndicator
import moe.rgsekai.sekaitune.ui.utils.HeaderDownloadState
import moe.rgsekai.sekaitune.ui.utils.ItemWrapper
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.ui.utils.formatFileSize
import moe.rgsekai.sekaitune.ui.utils.headerDownloadState
import moe.rgsekai.sekaitune.ui.utils.sendAddMissingDownloads
import moe.rgsekai.sekaitune.ui.utils.sendPauseDownloads
import moe.rgsekai.sekaitune.ui.utils.sendRemoveDownloads
import moe.rgsekai.sekaitune.ui.utils.sendResumeDownloads
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.viewmodels.AutoPlaylistViewModel
import moe.rgsekai.sekaitune.viewmodels.UploadEvent
import moe.rgsekai.sekaitune.viewmodels.UploadProgressItem
import moe.rgsekai.sekaitune.viewmodels.UploadState
import timber.log.Timber

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistViewModel = hiltViewModel(),
) {
    if (viewModel.playlist == "downloaded") {
        DownloadLibraryScreen(navController = navController)
        return
    }

    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playlist =
        when (viewModel.playlist) {
            "liked" -> stringResource(R.string.liked)
            "uploaded" -> stringResource(R.string.uploaded)
            else -> stringResource(R.string.offline)
        }

    val songs by viewModel.likedSongs.collectAsStateWithLifecycle()

    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { hasYouTubeLoginCookie(innerTubeCookie) }
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)

    val uploadProgressItems by viewModel.uploadProgress.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()

    var selectedUploadedTab by remember { mutableStateOf(UploadedTab.UPLOADED) }

    val uploadLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                selectedUploadedTab = UploadedTab.PROGRESS
                viewModel.onFilesSelected(uris)
            }
        }

    LaunchedEffect(Unit) {
        viewModel.uploadEvent.collect { event ->
            when (event) {
                is UploadEvent.LaunchFilePicker -> {
                    uploadLauncher.launch(arrayOf("audio/*"))
                }
                is UploadEvent.ShowToast -> {
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val likeLength = remember(songs) { songs.fastSumBy { it.song.duration } }

    val playlistId = viewModel.playlist
    val playlistType =
        when (playlistId) {
            "liked" -> PlaylistType.LIKE
            "downloaded" -> PlaylistType.DOWNLOAD
            "uploaded" -> PlaylistType.UPLOADED
            else -> PlaylistType.OTHER
        }

    val wrappedSongs =
        remember(songs) {
            songs.map { item -> ItemWrapper(item) }.toMutableStateList()
        }

    var selection by remember { mutableStateOf(false) }
    val selectedCount by remember(wrappedSongs) {
        derivedStateOf { wrappedSongs.count { it.isSelected } }
    }

    LaunchedEffect(selection, selectedCount) {
        if (selection && selectedCount == 0) {
            selection = false
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
            wrappedSongs.forEach { it.isSelected = false }
        }
    }

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            AutoPlaylistSongSortTypeKey,
            AutoPlaylistSongSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(AutoPlaylistSongSortDescendingKey, true)

    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }

    LaunchedEffect(ytmSync, playlistType) {
        if (ytmSync) {
            when (playlistType) {
                PlaylistType.LIKE -> viewModel.syncLikedSongs()
                PlaylistType.UPLOADED -> viewModel.refreshUploadedSongs()
                else -> Unit
            }
        }
    }

    LaunchedEffect(songs) {
        val songIds = songs.map { it.song.id }
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
                    text = stringResource(R.string.remove_download_playlist_confirm, playlist),
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
            val searchQuery = query.text.trim()
            if (searchQuery.isEmpty()) {
                wrappedSongs
            } else {
                wrappedSongs.filter { wrapper ->
                    val song = wrapper.item
                    song.song.title.contains(searchQuery, true) ||
                        song.artists.any { it.name.contains(searchQuery, true) }
                }
            }
        }

    val lazyListState = rememberLazyListState()

    // Gradient colors state for playlist cover
    var gradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    // Capture fallback color in composable context
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    // Extract gradient colors from playlist cover (first song thumbnail)
    LaunchedEffect(songs) {
        val thumbnailUrl = songs.firstOrNull()?.song?.thumbnailUrl
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
            if (songs.isNotEmpty() && !isSearching) 2 else 0
        }
    }

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        // Mesh gradient background layer
        if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val width = size.width
                            val height = size.height * 0.55f

                            if (gradientColors.size >= 3) {
                                val c0 = gradientColors[0]
                                val c1 = gradientColors[1]
                                val c2 = gradientColors[2]
                                val c3 = gradientColors.getOrElse(3) { c0 }
                                val c4 = gradientColors.getOrElse(4) { c1 }
                                // Primary color blob - top center
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c0.copy(alpha = gradientAlpha * 0.75f),
                                                    c0.copy(alpha = gradientAlpha * 0.4f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.15f),
                                            radius = width * 0.8f,
                                        ),
                                )

                                // Secondary color blob - left side
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c1.copy(alpha = gradientAlpha * 0.55f),
                                                    c1.copy(alpha = gradientAlpha * 0.3f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.1f, height * 0.4f),
                                            radius = width * 0.6f,
                                        ),
                                )

                                // Third color blob - right side
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c2.copy(alpha = gradientAlpha * 0.5f),
                                                    c2.copy(alpha = gradientAlpha * 0.25f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.9f, height * 0.35f),
                                            radius = width * 0.55f,
                                        ),
                                )

                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c3.copy(alpha = gradientAlpha * 0.35f),
                                                    c3.copy(alpha = gradientAlpha * 0.18f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.25f, height * 0.65f),
                                            radius = width * 0.75f,
                                        ),
                                )

                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    c4.copy(alpha = gradientAlpha * 0.3f),
                                                    c4.copy(alpha = gradientAlpha * 0.15f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.55f, height * 0.85f),
                                            radius = width * 0.9f,
                                        ),
                                )
                            } else if (gradientColors.isNotEmpty()) {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    gradientColors[0].copy(alpha = gradientAlpha * 0.7f),
                                                    gradientColors[0].copy(alpha = gradientAlpha * 0.35f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(width * 0.5f, height * 0.25f),
                                            radius = width * 0.85f,
                                        ),
                                )
                            }

                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.Transparent,
                                                surfaceColor.copy(alpha = gradientAlpha * 0.22f),
                                                surfaceColor.copy(alpha = gradientAlpha * 0.55f),
                                                surfaceColor,
                                            ),
                                        startY = size.height * 0.35f,
                                        endY = size.height,
                                    ),
                            )
                        },
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (playlistType != PlaylistType.UPLOADED && songs.isEmpty()) {
                item(
                    key = "empty",
                    contentType = CONTENT_TYPE_EMPTY,
                ) {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.playlist_is_empty),
                    )
                }
            } else {
                if (!isSearching && songs.isNotEmpty()) {
                    // Hero Header Item
                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = systemBarsTopPadding + 48.dp)
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 16.dp),
                        ) {
                            // Large centered artwork with shadow
                            Box(
                                modifier =
                                    Modifier
                                        .size(240.dp)
                                        .shadow(
                                            elevation = 24.dp,
                                            shape = RoundedCornerShape(16.dp),
                                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        ),
                            ) {
                                AsyncImage(
                                    model = songs.firstOrNull()?.song?.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp)),
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Playlist title
                            Text(
                                text = playlist,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Metadata chips row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Song count chip
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                ) {
                                    Text(
                                        text =
                                            pluralStringResource(
                                                R.plurals.n_song,
                                                songs.size,
                                                songs.size,
                                            ),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }

                                // Duration chip
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                ) {
                                    Text(
                                        text = makeTimeString(likeLength * 1000L),
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Action buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToggleButton(
                                    checked = downloadState == HeaderDownloadState.Completed,
                                    onCheckedChange = {
                                        val currentDownloadState = downloadState
                                        when (currentDownloadState) {
                                            HeaderDownloadState.Completed -> {
                                                showRemoveDownloadDialog = true
                                            }

                                            is HeaderDownloadState.Partial -> {
                                                val songIds = songs.map { it.song.id }
                                                if (currentDownloadState.paused) {
                                                    sendResumeDownloads(context, songIds)
                                                } else {
                                                    sendPauseDownloads(context, songIds)
                                                }
                                            }

                                            HeaderDownloadState.None -> {
                                                viewModel.downloadPlaylist(
                                                    songs = songs,
                                                    downloads = downloads,
                                                    onToast = { msg ->
                                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                )
                                            }
                                        }
                                    },
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
                                    val state = downloadState
                                    when (state) {
                                        HeaderDownloadState.Completed -> {
                                            Icon(
                                                painter = painterResource(R.drawable.offline),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }

                                        is HeaderDownloadState.Partial -> {
                                            HeaderDownloadProgressIndicator(
                                                progress = state.progress,
                                                paused = state.paused,
                                            )
                                        }

                                        else -> {
                                            Icon(
                                                painter = painterResource(R.drawable.download),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    }
                                }

                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = {
                                        viewModel.playAll(
                                            songs = songs,
                                            shuffle = false,
                                            onToast = { msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onReady = { items ->
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist,
                                                        items = items,
                                                    ),
                                                )
                                            },
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
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = stringResource(R.string.play),
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = {
                                        viewModel.playAll(
                                            songs = songs,
                                            shuffle = true,
                                            onToast = { msg ->
                                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            onReady = { items ->
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = playlist,
                                                        items = items,
                                                    ),
                                                )
                                            },
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
                                            items = songs.map { it.toMediaItem() },
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

                // Sort Header
                item(
                    key = "sortHeader",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    val sortHeaderModifier = if (playlistType == PlaylistType.UPLOADED && songs.isEmpty()) {
                        Modifier
                            .fillMaxWidth()
                            .padding(top = systemBarsTopPadding + 48.dp)
                            .padding(start = 16.dp, end = 16.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = sortHeaderModifier,
                    ) {
                        SortHeader(
                            sortType = sortType,
                            sortDescending = sortDescending,
                            onSortTypeChange = onSortTypeChange,
                            onSortDescendingChange = onSortDescendingChange,
                            sortTypeText = { sortType ->
                                when (sortType) {
                                    AutoPlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                    AutoPlaylistSongSortType.NAME -> R.string.sort_by_name
                                    AutoPlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                    AutoPlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (playlistType == PlaylistType.UPLOADED) {
                            FilledTonalButton(
                                onClick = {
                                    selectedUploadedTab =
                                        if (selectedUploadedTab == UploadedTab.PROGRESS) {
                                            UploadedTab.UPLOADED
                                        } else {
                                            UploadedTab.PROGRESS
                                        }
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors =
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor =
                                            if (selectedUploadedTab == UploadedTab.PROGRESS) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                        contentColor =
                                            if (selectedUploadedTab == UploadedTab.PROGRESS) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    ),
                                modifier =
                                    Modifier
                                        .padding(start = 8.dp)
                                        .heightIn(min = SplitButtonDefaults.MediumContainerHeight),
                            ) {
                                Text(
                                    text = stringResource(R.string.progress),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                                    fontWeight = if (selectedUploadedTab == UploadedTab.PROGRESS) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                if (playlistType == PlaylistType.UPLOADED && selectedUploadedTab == UploadedTab.PROGRESS) {
                    if (uploadProgressItems.isEmpty()) {
                        item(
                            key = "empty_uploads",
                            contentType = CONTENT_TYPE_EMPTY,
                        ) {
                            EmptyPlaceholder(
                                icon = R.drawable.cloud_upload,
                                text = stringResource(R.string.no_uploads_in_progress),
                            )
                        }
                    } else {
                        items(
                            items = uploadProgressItems,
                            key = { "upload_${it.uri}" },
                            contentType = { "upload_entry" },
                        ) { uploadItem ->
                            UploadEntry(
                                item = uploadItem,
                                onPause = { viewModel.pauseUpload(uploadItem) },
                                onResume = { viewModel.resumeUpload(uploadItem) },
                                onCancel = { viewModel.cancelUpload(uploadItem) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else if (playlistType == PlaylistType.UPLOADED && songs.isEmpty()) {
                    item(
                        key = "empty_uploaded",
                        contentType = CONTENT_TYPE_EMPTY,
                    ) {
                        val emptyText =
                            when {
                                !isLoggedIn -> stringResource(R.string.uploaded_empty_not_logged_in)
                                !ytmSync -> stringResource(R.string.uploaded_empty_sync_disabled)
                                else -> stringResource(R.string.uploaded_empty_no_songs)
                            }
                        val emptyIcon = if (!isLoggedIn) R.drawable.login else R.drawable.cloud_upload
                        EmptyPlaceholder(
                            icon = emptyIcon,
                            text = emptyText,
                        )
                    }
                } else {
                    // Song items
                    itemsIndexed(
                        items = filteredSongs,
                        key = { _, song -> song.item.id },
                        contentType = { _, _ -> CONTENT_TYPE_SONG },
                    ) { index, songWrapper ->
                        SongListItem(
                            song = songWrapper.item,
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
                                            if (!selection || selectedCount == 0) {
                                                if (songWrapper.item.song.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    val visibleSongs = filteredSongs.map { it.item }
                                                    viewModel.playTrack(
                                                        songs = visibleSongs,
                                                        startIndex = index,
                                                        onToast = { msg ->
                                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                                        },
                                                        onReady = { items, idx ->
                                                            playerConnection.playQueue(
                                                                ListQueue(
                                                                    title = playlist,
                                                                    items = items,
                                                                    startIndex = idx,
                                                                ),
                                                            )
                                                        },
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
                                            } else {
                                                songWrapper.isSelected = !songWrapper.isSelected
                                            }
                                        },
                                    ).animateItem(),
                        )
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

        TopAppBar(
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = if (transparentAppBar) Color.Transparent else MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            title = {
                when {
                    selection -> {
                        Text(
                            text = pluralStringResource(R.plurals.n_song, selectedCount, selectedCount),
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
                            text = playlist,
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
                                wrappedSongs.forEach { it.isSelected = false }
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
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (selectedCount == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                                selection = false
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (selectedCount == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all,
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
                                    clearAction = {
                                        selection = false
                                        wrappedSongs.forEach { it.isSelected = false }
                                    },
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
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        if (playlistType == PlaylistType.UPLOADED) {
            ExtendedFloatingActionButton(
                onClick = { viewModel.launchUploadPicker() },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.cloud_upload),
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.upload)) },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            LocalPlayerAwareWindowInsets.current
                                .union(WindowInsets.ime)
                                .asPaddingValues(),
                        ).padding(16.dp),
            )
        }
    }
}

@Composable
private fun UploadEntry(
    item: UploadProgressItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = item.filename,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val statusText = when (item.state) {
                        UploadState.Queued -> stringResource(R.string.queued)
                        UploadState.Uploading -> if (item.progress >= 0.999f) "Finalizing upload..." else stringResource(R.string.uploading)
                        UploadState.Paused -> stringResource(R.string.paused)
                        UploadState.Success -> stringResource(R.string.uploaded)
                        UploadState.Failed -> item.errorMessage ?: stringResource(R.string.upload_failed_state)
                    }
                    Text(
                        text = statusText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.state == UploadState.Failed) {
                        Text(
                            text = item.errorMessage ?: stringResource(R.string.upload_failed_state),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    val percent = (item.progress * 100).toInt().coerceIn(0, 100)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.download_progress_percent, percent),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (item.totalBytes > 0L) {
                            Text(
                                text = "${formatFileSize(item.uploadedBytes)} / ${formatFileSize(item.totalBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    LinearWavyProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth(),
                        amplitude = { if (item.state == UploadState.Paused) 0f else 1f },
                    )
                }
            },
            leadingContent = {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.state == UploadState.Failed) {
                        PrimaryFilledIconButton(
                            icon = R.drawable.sync,
                            contentDescription = stringResource(R.string.retry),
                            onClick = onResume,
                        )
                    } else if (item.state != UploadState.Success) {
                        PrimaryFilledIconButton(
                            icon = if (item.state == UploadState.Paused) R.drawable.play else R.drawable.pause,
                            contentDescription = stringResource(
                                if (item.state == UploadState.Paused) R.string.resume_download else R.string.pause_download
                            ),
                            onClick = if (item.state == UploadState.Paused) onResume else onPause,
                        )
                    }
                    PrimaryFilledIconButton(
                        icon = R.drawable.close,
                        contentDescription = stringResource(R.string.cancel_download),
                        onClick = onCancel,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
}

@Composable
private fun PrimaryFilledIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

private const val CONTENT_TYPE_EMPTY = "empty"
private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_SONG = "song"

enum class UploadedTab {
    UPLOADED,
    PROGRESS,
}

enum class PlaylistType {
    LIKE,
    DOWNLOAD,
    UPLOADED,
    OTHER,
}




