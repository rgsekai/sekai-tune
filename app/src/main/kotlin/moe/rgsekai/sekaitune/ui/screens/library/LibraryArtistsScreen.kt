/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalDatabase
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.ArtistFilter
import moe.rgsekai.sekaitune.constants.ArtistFilterKey
import moe.rgsekai.sekaitune.constants.ArtistSongSortType
import moe.rgsekai.sekaitune.constants.ArtistSortDescendingKey
import moe.rgsekai.sekaitune.constants.ArtistSortType
import moe.rgsekai.sekaitune.constants.ArtistSortTypeKey
import moe.rgsekai.sekaitune.constants.ShowSpotifyFollowArtistKey
import moe.rgsekai.sekaitune.constants.YtmSyncKey
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.spotify.SpotifyArtistResolver
import moe.rgsekai.sekaitune.spotify.SpotifyLibraryViewModel
import moe.rgsekai.sekaitune.spotify.YtmArtistResolution
import moe.rgsekai.sekaitune.ui.component.ExpressivePullToRefreshBox
import moe.rgsekai.sekaitune.ui.component.GlassDropdownMenu
import moe.rgsekai.sekaitune.ui.component.LocalMenuState
import moe.rgsekai.sekaitune.ui.component.SpotifyLibraryArtistListItem
import moe.rgsekai.sekaitune.ui.menu.ArtistMenu
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.viewmodels.LibraryArtistsViewModel

private enum class ArtistSourceSection {
    YT_MUSIC,
    SPOTIFY,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
    spotifyLibraryViewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            ArtistSortTypeKey,
            ArtistSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val (showSpotifyFollowArtist) = rememberPreference(ShowSpotifyFollowArtistKey, defaultValue = true)

    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIKED)
    var selectedSection by rememberSaveable { mutableStateOf(ArtistSourceSection.YT_MUSIC) }
    var resolvingSpotifyArtistId by remember { mutableStateOf<String?>(null) }

    val followedSpotifyArtists by spotifyLibraryViewModel.followedArtists.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    val artists by viewModel.allArtists.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isSpotifyRefreshing by spotifyLibraryViewModel.isRefreshing.collectAsStateWithLifecycle()

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    val activeSection = if (showSpotifyFollowArtist) selectedSection else ArtistSourceSection.YT_MUSIC

    ExpressivePullToRefreshBox(
        isRefreshing = if (activeSection == ArtistSourceSection.SPOTIFY) isSpotifyRefreshing else isRefreshing,
        onRefresh = {
            if (activeSection == ArtistSourceSection.SPOTIFY) {
                spotifyLibraryViewModel.refreshFollowedArtists()
            } else {
                viewModel.sync()
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Segmented connected pill toggle for YT Music and Spotify
            if (showSpotifyFollowArtist) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isYtSelected = activeSection == ArtistSourceSection.YT_MUSIC
                    val isSpotifySelected = activeSection == ArtistSourceSection.SPOTIFY

                    // Left segment: YT Music (rounded on left side, subtle gap on right)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 6.dp, bottomEnd = 6.dp))
                                .background(
                                    if (isYtSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                                .clickable { selectedSection = ArtistSourceSection.YT_MUSIC }
                                .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "YT Music",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isYtSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color =
                                if (isYtSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }

                    // Right segment: Spotify (subtle gap on left, rounded on right side)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 50.dp, bottomEnd = 50.dp))
                                .background(
                                    if (isSpotifySelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                )
                                .clickable { selectedSection = ArtistSourceSection.SPOTIFY }
                                .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.spotify_filter),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSpotifySelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                            color =
                                if (isSpotifySelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = playerAwareBottomPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (activeSection == ArtistSourceSection.SPOTIFY) {
                    if (followedSpotifyArtists.isEmpty()) {
                        item(span = { GridItemSpan(2) }, key = "spotify_artists_empty") {
                            Text(
                                text = stringResource(R.string.spotify_no_artists),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }

                    items(
                        items = followedSpotifyArtists,
                        key = { it.id },
                        span = { GridItemSpan(2) },
                    ) { artist ->
                        SpotifyLibraryArtistListItem(
                            artist = artist,
                            isResolving = resolvingSpotifyArtistId == artist.id,
                            onClick = {
                                if (resolvingSpotifyArtistId != null) return@SpotifyLibraryArtistListItem
                                resolvingSpotifyArtistId = artist.id
                                coroutineScope.launch {
                                    val resolution =
                                        SpotifyArtistResolver.resolveSpotifyToYtmArtist(
                                            spotifyArtistId = artist.id,
                                            spotifyArtistName = artist.name,
                                        )
                                    resolvingSpotifyArtistId = null
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
                                spotifyLibraryViewModel.unfollowArtist(artist.id)
                            },
                        )
                    }
                } else {
                    // Sub-Header Controls (Sort dropdown, sort direction, Subscribed / All filter)
                    item(span = { GridItemSpan(2) }, key = "sub_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            var showSortMenu by remember { mutableStateOf(false) }
                            val currentSortLabel =
                                when (sortType) {
                                    ArtistSortType.CREATE_DATE -> {
                                        if (sortDescending) {
                                            stringResource(
                                                R.string.newest_first,
                                            )
                                        } else {
                                            stringResource(R.string.oldest_first)
                                        }
                                    }

                                    ArtistSortType.NAME -> {
                                        if (sortDescending) {
                                            stringResource(
                                                R.string.sort_z_to_a,
                                            )
                                        } else {
                                            stringResource(R.string.sort_a_to_z)
                                        }
                                    }

                                    ArtistSortType.SONG_COUNT -> {
                                        if (sortDescending) {
                                            stringResource(
                                                R.string.most_tracks,
                                            )
                                        } else {
                                            stringResource(R.string.least_tracks)
                                        }
                                    }

                                    ArtistSortType.PLAY_TIME -> {
                                        stringResource(R.string.play_time)
                                    }
                                }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box {
                                    Row(
                                        modifier =
                                            Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .clickable { showSortMenu = true }
                                                .padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = currentSortLabel,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            painter = painterResource(id = R.drawable.expand_more),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }

                                    GlassDropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                    ) {
                                        ArtistSortType.entries.forEach { type ->
                                            val label =
                                                when (type) {
                                                    ArtistSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                                    ArtistSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                                    ArtistSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                                    ArtistSortType.PLAY_TIME -> stringResource(R.string.play_time)
                                                }
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    onSortTypeChange(type)
                                                    if (type == ArtistSortType.NAME) onSortDescendingChange(false)
                                                    showSortMenu = false
                                                },
                                            )
                                        }
                                    }
                                }

                                // Sort direction toggle button
                                Box(
                                    modifier =
                                        Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { onSortDescendingChange(!sortDescending) }
                                            .padding(horizontal = 8.dp, vertical = 7.dp),
                                ) {
                                    Icon(
                                        painter =
                                            painterResource(
                                                id = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                                            ),
                                        contentDescription =
                                            if (sortDescending) {
                                                stringResource(
                                                    R.string.sort_descending,
                                                )
                                            } else {
                                                stringResource(R.string.sort_ascending)
                                            },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            // Filter Chips Row (Subscribed / All)
                            Row(
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable {
                                            filter = if (filter == ArtistFilter.LIKED) ArtistFilter.LIBRARY else ArtistFilter.LIKED
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text =
                                        if (filter == ArtistFilter.LIKED) {
                                            stringResource(R.string.subscribed_only)
                                        } else {
                                            stringResource(R.string.all_artists_filter)
                                        },
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }

                    // Artists: matching Spotify artist card design (72dp avatar, rounded container, play button + 3-dot menu)
                    items(artists, key = { it.id }, span = { GridItemSpan(2) }) { artistWrapper ->
                        val artist = artistWrapper.artist
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(26.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusable()
                                    .combinedClickable(
                                        onClick = { navController.navigate("artist/${artist.id}") },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                ArtistMenu(
                                                    originalArtist = artistWrapper,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                            ) {
                                AsyncImage(
                                    model = artist.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(R.string.artist_subtitle),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.padding(start = 8.dp),
                                ) {
                                    // Play button
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val songs =
                                                    database
                                                        .artistSongs(
                                                            artistWrapper.id,
                                                            ArtistSongSortType.CREATE_DATE,
                                                            true,
                                                        ).first()
                                                        .map { it.toMediaItem() }
                                                if (songs.isNotEmpty()) {
                                                    playerConnection?.playQueue(
                                                        ListQueue(
                                                            title = artistWrapper.artist.name,
                                                            items = songs,
                                                        ),
                                                    )
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.play),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    // 3-dot More menu
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                ArtistMenu(
                                                    originalArtist = artistWrapper,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.more_vert),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Recently Played Artists Section
                    if (artists.size > 2) {
                        item(span = { GridItemSpan(2) }, key = "recent_artists_header") {
                            Text(
                                text = stringResource(R.string.recently_played_artists),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }

                        item(span = { GridItemSpan(2) }, key = "recent_artists_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(artists.take(5)) { artistWrapper ->
                                    val artist = artistWrapper.artist
                                    Column(
                                        modifier =
                                            Modifier
                                                .width(72.dp)
                                                .clickable {
                                                    navController.navigate("artist/${artist.id}")
                                                },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        AsyncImage(
                                            model = artist.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier =
                                                Modifier
                                                    .size(60.dp)
                                                    .clip(CircleShape),
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = artist.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onBackground,
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
}
