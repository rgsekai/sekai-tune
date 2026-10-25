/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.library

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.playback.sources.webdav.toMediaItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.LocalDatabase
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.db.entities.WebDavSongEntity
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavScanState
import moe.rgsekai.sekaitune.playback.sources.webdav.WebDavScanner
import moe.rgsekai.sekaitune.ui.component.ExpressivePullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WebDavSongScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val songs by database.webDavSongs().collectAsState(initial = emptyList())
    val scanState by WebDavScanner.scanState.collectAsState()
    val tagScanState by moe.rgsekai.sekaitune.playback.sources.webdav.WebDavTagScanner.tagScanState.collectAsState()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var artistFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var albumFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var folderFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val artists = remember(songs) {
        songs.groupBy { it.artist }
            .map { (artist, trackList) -> artist to trackList.size }
            .sortedBy { it.first.lowercase() }
    }
    val albums = remember(songs) {
        songs.groupBy { it.album }
            .map { (album, trackList) ->
                Triple(
                    album,
                    trackList.size,
                    trackList.firstOrNull { it.artUrl != null }?.artUrl,
                )
            }
            .sortedBy { it.first.lowercase() }
    }
    val folders = remember(songs) {
        songs.groupBy { it.path.substringBeforeLast('/') }
            .map { (dirPath, trackList) ->
                val name = moe.rgsekai.sekaitune.webdav.WebDavMetadata.extractParentFolder(dirPath + "/").ifBlank {
                    dirPath.substringAfterLast('/').ifBlank { "/" }
                }
                Triple(
                    dirPath,
                    name,
                    trackList.size to trackList.firstOrNull { it.artUrl != null }?.artUrl,
                )
            }
            .sortedBy { it.second.lowercase() }
    }
    val currentArtistSongs = remember(songs, artistFilter) {
        val filter = artistFilter
        if (filter != null) songs.filter { it.artist.equals(filter, ignoreCase = true) } else emptyList()
    }
    val currentAlbumSongs = remember(songs, albumFilter) {
        val filter = albumFilter
        if (filter != null) songs.filter { it.album.equals(filter, ignoreCase = true) } else emptyList()
    }
    val currentFolderSongs = remember(songs, folderFilter) {
        val filter = folderFilter
        if (filter != null) songs.filter { it.path.substringBeforeLast('/') == filter } else emptyList()
    }

    val bottomPadding = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding() + 16.dp

    // Trigger scan on open if last scan was > 24 hours ago
    LaunchedEffect(Unit) {
        WebDavScanner.triggerScanIfOlderThan(context, maxAgeMs = 24 * 60 * 60 * 1000L)
    }

    val isScanning = scanState is WebDavScanState.Scanning
    val playerConnection = LocalPlayerConnection.current

    val playWebDavQueue: (List<WebDavSongEntity>, Int, Boolean) -> Unit = { queueSongs, startIndex, shuffle ->
        if (queueSongs.isNotEmpty()) {
            val items = queueSongs.map { it.toMediaItem() }
            val (finalItems, finalIndex) = if (shuffle) {
                items.shuffled() to 0
            } else {
                items to startIndex.coerceIn(items.indices)
            }
            playerConnection?.playQueue(
                ListQueue(
                    title = context.getString(R.string.webdav_title),
                    items = finalItems,
                    startIndex = finalIndex,
                ),
            )
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = { WebDavScanner.startScan(context, force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.webdav_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = navController::navigateUp) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { WebDavScanner.startScan(context, force = true) },
                            enabled = !isScanning,
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.sync),
                                    contentDescription = stringResource(R.string.webdav_rescan),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { paddingValues ->
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = bottomPadding),
            ) {
                // Header section
                item(key = "header") {
                    WebDavHeader(
                        trackCount = songs.size,
                        onPlayClick = { playWebDavQueue(songs, 0, false) },
                        onShuffleClick = { playWebDavQueue(songs, 0, true) },
                    )
                }

                // Scan / Error / Hard cap status banners
                item(key = "status_banner") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnimatedVisibility(
                            visible = isScanning,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            val scanningCount = (scanState as? WebDavScanState.Scanning)?.foundCount ?: 0
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.webdav_scanning_count, scanningCount),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        val currentTagState = tagScanState
                        if (!isScanning && currentTagState is moe.rgsekai.sekaitune.playback.sources.webdav.WebDavTagScanState.Reading) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(R.string.webdav_reading_tags, currentTagState.completed, currentTagState.total),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (currentTagState.total > 0) {
                                        LinearProgressIndicator(
                                            progress = { (currentTagState.completed.toFloat() / currentTagState.total.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                        }

                        val currentScanState = scanState
                        if (currentScanState is WebDavScanState.Done && currentScanState.hitHardCap) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.info),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.webdav_hard_cap_notice),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                        }

                        if (currentScanState is WebDavScanState.Error) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.error),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (songs.isNotEmpty()) {
                                                stringResource(R.string.webdav_offline_warning)
                                            } else {
                                                currentScanState.message
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { WebDavScanner.startScan(context, force = true) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.webdav_retry),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty state when no songs found
                if (songs.isEmpty() && !isScanning) {
                    item(key = "empty_state") {
                        WebDavEmptyState(
                            onScanClick = { WebDavScanner.startScan(context, force = true) },
                        )
                    }
                } else if (songs.isNotEmpty()) {
                    // Tabs
                    item(key = "tabs") {
                        PrimaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Tab(
                                selected = selectedTabIndex == 0,
                                onClick = {
                                    selectedTabIndex = 0
                                    artistFilter = null
                                    albumFilter = null
                                    folderFilter = null
                                },
                                text = { Text(stringResource(R.string.webdav_tab_songs)) },
                            )
                            Tab(
                                selected = selectedTabIndex == 1,
                                onClick = {
                                    selectedTabIndex = 1
                                    albumFilter = null
                                    folderFilter = null
                                },
                                text = { Text(stringResource(R.string.webdav_tab_artists)) },
                            )
                            Tab(
                                selected = selectedTabIndex == 2,
                                onClick = {
                                    selectedTabIndex = 2
                                    artistFilter = null
                                    folderFilter = null
                                },
                                text = { Text(stringResource(R.string.webdav_tab_albums)) },
                            )
                            Tab(
                                selected = selectedTabIndex == 3,
                                onClick = {
                                    selectedTabIndex = 3
                                    artistFilter = null
                                    albumFilter = null
                                },
                                text = { Text(stringResource(R.string.webdav_tab_folders)) },
                            )
                        }
                    }

                    when (selectedTabIndex) {
                        0 -> {
                            // Songs tab
                            itemsIndexed(songs, key = { _, song -> song.path }) { idx, song ->
                                WebDavSongListItem(
                                    song = song,
                                    onClick = {
                                        playWebDavQueue(songs, idx, false)
                                    },
                                )
                            }
                        }

                        1 -> {
                            // Artists tab
                            val filter = artistFilter
                            if (filter != null) {
                                item(key = "artist_filter_header") {
                                    FilterHeader(
                                        title = filter,
                                        subtitle = "${currentArtistSongs.size} tracks",
                                        onBack = { artistFilter = null },
                                        iconRes = R.drawable.artist,
                                        onPlayClick = { playWebDavQueue(currentArtistSongs, 0, false) },
                                        onShuffleClick = { playWebDavQueue(currentArtistSongs, 0, true) },
                                    )
                                }
                                itemsIndexed(currentArtistSongs, key = { _, song -> song.path }) { idx, song ->
                                    WebDavSongListItem(
                                        song = song,
                                        onClick = {
                                            playWebDavQueue(currentArtistSongs, idx, false)
                                        },
                                    )
                                }
                            } else {
                                items(artists, key = { it.first }) { (artist, count) ->
                                    GroupListItem(
                                        title = artist,
                                        subtitle = "$count ${stringResource(R.string.tracks_label)}",
                                        iconRes = R.drawable.artist,
                                        onClick = { artistFilter = artist },
                                    )
                                }
                            }
                        }

                        2 -> {
                            // Albums tab
                            val filter = albumFilter
                            if (filter != null) {
                                val albumArt = currentAlbumSongs.firstOrNull { it.artUrl != null }?.artUrl
                                item(key = "album_filter_header") {
                                    FilterHeader(
                                        title = filter,
                                        subtitle = "${currentAlbumSongs.size} tracks",
                                        onBack = { albumFilter = null },
                                        artUrl = albumArt,
                                        iconRes = R.drawable.album,
                                        onPlayClick = { playWebDavQueue(currentAlbumSongs, 0, false) },
                                        onShuffleClick = { playWebDavQueue(currentAlbumSongs, 0, true) },
                                    )
                                }
                                itemsIndexed(currentAlbumSongs, key = { _, song -> song.path }) { idx, song ->
                                    WebDavSongListItem(
                                        song = song,
                                        onClick = {
                                            playWebDavQueue(currentAlbumSongs, idx, false)
                                        },
                                    )
                                }
                            } else {
                                items(albums, key = { it.first }) { (album, count, artUrl) ->
                                    AlbumListItem(
                                        title = album,
                                        subtitle = "$count ${stringResource(R.string.tracks_label)}",
                                        artUrl = artUrl,
                                        onClick = { albumFilter = album },
                                    )
                                }
                            }
                        }

                        3 -> {
                            // Folders tab
                            val filter = folderFilter
                            if (filter != null) {
                                val folderDisplayName = folders.firstOrNull { it.first == filter }?.second
                                    ?: moe.rgsekai.sekaitune.webdav.WebDavMetadata.extractParentFolder(filter + "/").ifBlank { "Folder" }
                                val folderArt = currentFolderSongs.firstOrNull { it.artUrl != null }?.artUrl
                                item(key = "folder_filter_header") {
                                    FilterHeader(
                                        title = folderDisplayName,
                                        subtitle = "${currentFolderSongs.size} tracks",
                                        onBack = { folderFilter = null },
                                        artUrl = folderArt,
                                        iconRes = R.drawable.cloud,
                                        onPlayClick = { playWebDavQueue(currentFolderSongs, 0, false) },
                                        onShuffleClick = { playWebDavQueue(currentFolderSongs, 0, true) },
                                    )
                                }
                                itemsIndexed(currentFolderSongs, key = { _, song -> song.path }) { idx, song ->
                                    WebDavSongListItem(
                                        song = song,
                                        onClick = {
                                            playWebDavQueue(currentFolderSongs, idx, false)
                                        },
                                    )
                                }
                            } else {
                                items(folders, key = { it.first }) { (dirPath, displayName, extra) ->
                                    FolderListItem(
                                        title = displayName,
                                        subtitle = "${extra.first} ${stringResource(R.string.tracks_label)}",
                                        artUrl = extra.second,
                                        onClick = { folderFilter = dirPath },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebDavHeader(
    trackCount: Int,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.cloud_upload),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.webdav_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.webdav_music_on_your_server),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "$trackCount ${stringResource(R.string.tracks_label)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onPlayClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.webdav_play))
            }

            OutlinedButton(
                onClick = onShuffleClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.webdav_shuffle))
            }
        }
    }
}

@Composable
private fun WebDavSongListItem(
    song: WebDavSongEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (song.artUrl != null) {
                AsyncImage(
                    model = song.artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GroupListItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun AlbumListItem(
    title: String,
    subtitle: String,
    artUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.album),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FilterHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    artUrl: String? = null,
    iconRes: Int? = null,
    onPlayClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = null,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(iconRes ?: R.drawable.cloud),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPlayClick != null) {
            IconButton(onClick = onPlayClick) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.webdav_play),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (onShuffleClick != null) {
            IconButton(onClick = onShuffleClick) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = stringResource(R.string.webdav_shuffle),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FolderListItem(
    title: String,
    subtitle: String,
    artUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (artUrl != null) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.folder),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            painter = painterResource(R.drawable.navigate_next),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun WebDavEmptyState(
    onScanClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.cloud_upload),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.webdav_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.webdav_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onScanClick,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.webdav_scan_server))
        }
    }
}
