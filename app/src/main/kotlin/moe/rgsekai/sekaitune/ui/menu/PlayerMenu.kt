/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.menu

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import moe.rgsekai.sekaitune.ui.player.rememberSongVideoLookup
import moe.rgsekai.sekaitune.ui.player.video.VideoLoadingIndicator
import moe.rgsekai.sekaitune.ui.player.video.YouTubeVideoSessions
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import moe.rgsekai.sekaitune.ui.component.frostedGlass
import moe.rgsekai.sekaitune.ui.component.liquidGlass
import androidx.core.net.toUri
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalDatabase
import moe.rgsekai.sekaitune.LocalDownloadUtil
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.ArtistSeparatorsKey
import moe.rgsekai.sekaitune.constants.EqualizerBandLevelsMbKey
import moe.rgsekai.sekaitune.constants.EqualizerBassBoostEnabledKey
import moe.rgsekai.sekaitune.constants.EqualizerBassBoostStrengthKey
import moe.rgsekai.sekaitune.constants.EqualizerCustomProfilesJsonKey
import moe.rgsekai.sekaitune.constants.EqualizerEnabledKey
import moe.rgsekai.sekaitune.constants.EqualizerOutputGainEnabledKey
import moe.rgsekai.sekaitune.constants.EqualizerOutputGainMbKey
import moe.rgsekai.sekaitune.constants.EqualizerSelectedProfileIdKey
import moe.rgsekai.sekaitune.constants.EqualizerVirtualizerEnabledKey
import moe.rgsekai.sekaitune.constants.EqualizerVirtualizerStrengthKey
import moe.rgsekai.sekaitune.constants.ExternalDownloaderEnabledKey
import moe.rgsekai.sekaitune.constants.ExternalDownloaderPackageKey
import moe.rgsekai.sekaitune.constants.ShowSpotifyPlaylistsKey
import moe.rgsekai.sekaitune.constants.SpotifyAccessTokenKey
import moe.rgsekai.sekaitune.constants.SpeedDialSongIdsKey
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.playback.EqProfile
import moe.rgsekai.sekaitune.playback.EqProfilesPayload
import moe.rgsekai.sekaitune.playback.EqualizerJson
import moe.rgsekai.sekaitune.playback.ExoDownloadService
import moe.rgsekai.sekaitune.playback.queues.YouTubeQueue
import moe.rgsekai.sekaitune.ui.component.BottomSheetState
import moe.rgsekai.sekaitune.ui.component.ListDialog
import moe.rgsekai.sekaitune.ui.component.MenuSurfaceSection
import moe.rgsekai.sekaitune.ui.component.NewAction
import moe.rgsekai.sekaitune.ui.component.NewActionGrid
import moe.rgsekai.sekaitune.ui.component.TextFieldDialog
import moe.rgsekai.sekaitune.LocalResolveUploadedCatalogMatchUseCase
import moe.rgsekai.sekaitune.db.entities.ArtistEntity
import moe.rgsekai.sekaitune.playback.CatalogMatchResult
import moe.rgsekai.sekaitune.utils.SpeedDialPin
import moe.rgsekai.sekaitune.utils.SpeedDialPinType
import moe.rgsekai.sekaitune.utils.isLocalMediaId
import moe.rgsekai.sekaitune.utils.parseSpeedDialPins
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.utils.serializeSpeedDialPins
import moe.rgsekai.sekaitune.utils.shareLocalAudio
import moe.rgsekai.sekaitune.utils.toggleSpeedDialPin
import java.util.UUID
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    isQueueTrigger: Boolean? = false,
    hasVideo: Boolean = false,
    useYtVideoCanvas: Boolean = false,
    inlineVideoActive: Boolean = false,
    onToggleInlineVideo: () -> Unit = {},
    onViewFullScreen: () -> Unit = {},
    onRemoveFromQueue: (() -> Unit)? = null,
    onShowDetailsDialog: () -> Unit,
    onShowComments: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val resolveUploadedCatalogMatchUseCase = LocalResolveUploadedCatalogMatchUseCase.current
    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val downloadUtil = LocalDownloadUtil.current
    val download by downloadUtil
        .getDownload(mediaMetadata.id, mediaMetadata.matchedCatalogId ?: librarySong?.song?.matchedCatalogId)
        .collectAsState(initial = null)

    val artists =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.filter { it.id != null }
        }

    // Artist separators for splitting artist names
    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = ",;/&")
    val (externalDownloaderEnabled) = rememberPreference(ExternalDownloaderEnabledKey, defaultValue = false)
    val (externalDownloaderPackage) = rememberPreference(ExternalDownloaderPackageKey, defaultValue = "")
    val (speedDialSongIds, onSpeedDialSongIdsChange) = rememberPreference(SpeedDialSongIdsKey, "")
    val speedDialPins = remember(speedDialSongIds) { parseSpeedDialPins(speedDialSongIds) }
    val songPin = remember(mediaMetadata.id) { SpeedDialPin(type = SpeedDialPinType.SONG, id = mediaMetadata.id) }
    val isInSpeedDial =
        remember(speedDialPins, songPin) {
            speedDialPins.any { it.type == songPin.type && it.id == songPin.id }
        }
    val isLocalMedia =
        remember(librarySong?.song?.isLocal, mediaMetadata.id) {
            librarySong?.song?.isLocal == true || mediaMetadata.id.isLocalMediaId() || mediaMetadata.id.startsWith("webdav:")
        }
    val castPlayerMenuAction = rememberCastPlayerMenuAction()

    // Split artists by configured separators
    data class SplitArtist(
        val name: String,
        val originalArtist: MediaMetadata.Artist?,
    )

    val splitArtists =
        remember(artists, artistSeparators) {
            if (artistSeparators.isEmpty()) {
                artists.map { SplitArtist(it.name, it) }
            } else {
                val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
                artists.flatMap { artist ->
                    val parts =
                        artist.name
                            .split(separatorRegex)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    if (parts.size > 1) {
                        parts.mapIndexed { index, name ->
                            SplitArtist(name, if (index == 0) artist else null)
                        }
                    } else {
                        listOf(SplitArtist(artist.name, artist))
                    }
                }
            }
        }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val (showSpotifyPlaylists) = rememberPreference(ShowSpotifyPlaylistsKey, defaultValue = false)
    val (spotifyAccessToken) = rememberPreference(SpotifyAccessTokenKey, defaultValue = "")
    val (spotifySpDc) = rememberPreference(moe.rgsekai.sekaitune.constants.SpotifySpDcKey, defaultValue = "")
    val canAddToSpotify = remember(showSpotifyPlaylists, spotifyAccessToken, spotifySpDc) {
        showSpotifyPlaylists && (spotifyAccessToken.isNotBlank() || spotifySpDc.isNotBlank())
    }
    var showAddToSpotifyPlaylist by rememberSaveable { mutableStateOf(false) }

    AddToSpotifyPlaylistFlow(
        showDialog = showAddToSpotifyPlaylist,
        youtubeId = mediaMetadata.id,
        title = mediaMetadata.title,
        artist = mediaMetadata.artists.firstOrNull()?.name.orEmpty(),
        durationSec = mediaMetadata.duration,
        spotifyUri = mediaMetadata.spotifyTrackId?.let { "spotify:track:$it" },
        onDismiss = { showAddToSpotifyPlaylist = false },
    )

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            database.withTransaction {
                insert(mediaMetadata)
            }
            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    playlistNames.size == 1 -> context.applicationContext.getString(R.string.added_to_playlist, playlistNames.first())
                    else -> context.applicationContext.getString(R.string.added_to_n_playlists, playlistNames.size)
                }
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        },
    )

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(splitArtists.distinctBy { it.name }) { splitArtist ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = splitArtist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        val thumbUrl = splitArtist.originalArtist?.thumbnailUrl
                        if (thumbUrl.isNullOrBlank()) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            AsyncImage(
                                model = thumbUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape),
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                splitArtist.originalArtist?.let { artist ->
                                    navController.navigate("artist/${artist.id}")
                                    showSelectArtistDialog = false
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                }
                            },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    var showPitchTempoDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showPitchTempoDialog) {
        TempoPitchDialog(
            onDismiss = { showPitchTempoDialog = false },
        )
    }

    /* showEqualizerDialog removed - navigates to equalizer screen instead */


    val nowPlayingTitle =
        remember(mediaMetadata.title) {
            mediaMetadata.title.ifBlank { context.applicationContext.getString(R.string.no_title) }
        }

    val nowPlayingSubtitle =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString(separator = " • ") { it.name }
        }

    val glassConfig = moe.rgsekai.sekaitune.ui.component.LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(moe.rgsekai.sekaitune.ui.component.GlassComponent.POPUP_MENU)

    MenuSurfaceSection(
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            val thumb = mediaMetadata.thumbnailUrl
            if (thumb.isNullOrBlank()) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp)),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = nowPlayingTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                if (nowPlayingSubtitle.isNotBlank()) {
                    Text(
                        text = nowPlayingSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                }
            }
        }
    }

    val videoLookup = rememberSongVideoLookup(
        mediaId = mediaMetadata.id,
        title = mediaMetadata.title,
        artist = mediaMetadata.artists.joinToString { it.name },
        isVideoSong = mediaMetadata.isMusicVideo,
        enabled = useYtVideoCanvas || inlineVideoActive,
    )
    val videoSession = videoLookup.video?.videoId?.let { YouTubeVideoSessions.get(it) }
    val hasVideoFrame = videoSession?.hasFrame == true
    val isVideoLoading = (useYtVideoCanvas || inlineVideoActive) && (videoLookup.searching || !hasVideoFrame || videoSession?.isStalled == true)

    LaunchedEffect(inlineVideoActive, videoLookup.searching, videoLookup.video) {
        if (inlineVideoActive && !videoLookup.searching && videoLookup.video == null) {
            onToggleInlineVideo()
            Toast.makeText(
                context,
                context.getString(R.string.video_mode_no_video),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    if (isQueueTrigger != true && (hasVideo || !useYtVideoCanvas)) {
        Spacer(modifier = Modifier.height(12.dp))

        MenuSurfaceSection(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            ) {
                Text(
                    text = "Video mode",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Full screen icon sitting to the left of Exit (X) when inline video is active and playing
                    AnimatedVisibility(
                        visible = !useYtVideoCanvas && inlineVideoActive && hasVideoFrame && !videoLookup.searching && videoSession?.isStalled != true,
                        enter = fadeIn(tween(200)) + expandHorizontally(),
                        exit = fadeOut(tween(200)) + shrinkHorizontally(),
                    ) {
                        IconButton(
                            onClick = {
                                onDismiss()
                                onViewFullScreen()
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.fullscreen),
                                contentDescription = "Full screen",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Far right action slot
                    AnimatedContent(
                        targetState = when {
                            isVideoLoading -> VideoRowSlot.LOADING
                            useYtVideoCanvas && hasVideoFrame -> VideoRowSlot.FULL_SCREEN
                            inlineVideoActive && hasVideoFrame -> VideoRowSlot.EXIT
                            else -> VideoRowSlot.PLAY
                        },
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "VideoActionSlotAnim",
                    ) { slot ->
                        when (slot) {
                            VideoRowSlot.PLAY -> {
                                IconButton(
                                    onClick = {
                                        onToggleInlineVideo()
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.play),
                                        contentDescription = "Video mode",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            VideoRowSlot.LOADING -> {
                                IconButton(
                                    onClick = { onToggleInlineVideo() },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    VideoLoadingIndicator(
                                        loading = true,
                                        size = 24.dp,
                                    )
                                }
                            }
                            VideoRowSlot.EXIT -> {
                                IconButton(
                                    onClick = { onToggleInlineVideo() },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = "Exit video mode",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            VideoRowSlot.FULL_SCREEN -> {
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onViewFullScreen()
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.fullscreen),
                                        contentDescription = "Full screen",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                NewActionGrid(
                    actions =
                        buildList {
                            castPlayerMenuAction?.let(::add)
                            if (!isLocalMedia) {
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.radio),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.start_radio),
                                        onClick = {
                                            playerConnection.startRadio(mediaMetadata)
                                            onDismiss()
                                        },
                                    ),
                                )
                            }
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.playlist_add),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    text = stringResource(R.string.add_to_playlist),
                                    onClick = { showChoosePlaylistDialog = true },
                                ),
                            )
                            if (canAddToSpotify) {
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.spotify_icon),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.spotify_add_to_playlist),
                                        onClick = { showAddToSpotifyPlaylist = true },
                                    ),
                                )
                            }
                            add(
                                NewAction(
                                    icon = {
                                        Icon(
                                            painter =
                                                painterResource(
                                                    if (isInSpeedDial) R.drawable.bookmark_filled else R.drawable.bookmark,
                                                ),
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    text =
                                        stringResource(
                                            if (isInSpeedDial) {
                                                R.string.remove_from_speed_dial
                                            } else {
                                                R.string.pin_to_speed_dial
                                            },
                                        ),
                                    onClick = {
                                        val updatedPins = toggleSpeedDialPin(speedDialPins, songPin)
                                        onSpeedDialSongIdsChange(serializeSpeedDialPins(updatedPins))
                                        onDismiss()
                                    },
                                ),
                            )
                            add(
                                if (isLocalMedia) {
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.share),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.share),
                                        onClick = {
                                            shareLocalAudio(context, mediaMetadata.id, librarySong?.format?.mimeType)
                                            onDismiss()
                                        },
                                    )
                                } else {
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.link),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.copy_link),
                                        onClick = {
                                            val clipboard =
                                                context.getSystemService(
                                                    android.content.Context.CLIPBOARD_SERVICE,
                                                ) as android.content.ClipboardManager
                                            val clip =
                                                android.content.ClipData.newPlainText(
                                                    context.applicationContext.getString(R.string.copy_link),
                                                    "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                                )
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast
                                                .makeText(
                                                    context,
                                                    R.string.link_copied,
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            onDismiss()
                                        },
                                    )
                                },
                            )
                            if (!isLocalMedia) {
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.fire),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.music_together),
                                        onClick = {
                                            onDismiss()
                                            playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                            navController.navigate("settings/music_together")
                                        },
                                    ),
                                )
                            }
                            if (isQueueTrigger != true) {
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.fullscreen),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.ambient_mode),
                                        onClick = {
                                            onDismiss()
                                            playerBottomSheetState.dismiss()
                                            navController.navigate("ambient_mode")
                                        },
                                    ),
                                )
                                add(
                                    NewAction(
                                        icon = {
                                            Icon(
                                                painter = painterResource(R.drawable.bedtime),
                                                contentDescription = null,
                                                modifier = Modifier.size(28.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                        text = stringResource(R.string.aod_mode),
                                        onClick = {
                                            playerConnection.aodModeEnabled.value = true
                                            onDismiss()
                                        },
                                    ),
                                )
                            }
                        },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (splitArtists.isNotEmpty() || mediaMetadata.album != null) {
            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    Column {
                        if (splitArtists.isNotEmpty()) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.view_artist)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.artist),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        if (splitArtists.size == 1 && splitArtists[0].originalArtist != null) {
                                            onDismiss()
                                            playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                            navController.navigate("artist/${splitArtists[0].originalArtist!!.id}")
                                        } else {
                                            showSelectArtistDialog = true
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        if (splitArtists.isNotEmpty() && mediaMetadata.album != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        if (mediaMetadata.album != null) {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.view_album)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.album),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        onDismiss()
                                        playerBottomSheetState.snapTo(playerBottomSheetState.collapsedBound)
                                        navController.navigate("album/${mediaMetadata.album.id}")
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (!isLocalMedia) {
            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.save_to_device_downloads)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.download),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onDismiss()
                                val songTitle = mediaMetadata.title
                                val safeArtistName = mediaMetadata.artists.joinToString(", ") { it.name }.ifEmpty { "Unknown Artist" }
                                if (mediaMetadata.isUploaded) {
                                    coroutineScope.launch {
                                        val dbSong = withContext(Dispatchers.IO) { database.song(mediaMetadata.id).firstOrNull() }
                                        val songEntity = dbSong?.song ?: mediaMetadata.toSongEntity()
                                        val artistEntities = dbSong?.artists ?: mediaMetadata.artists.map { ArtistEntity(id = it.id ?: "", name = it.name) }
                                        when (val result = resolveUploadedCatalogMatchUseCase(songEntity, artistEntities)) {
                                            is CatalogMatchResult.Success -> {
                                                if (!result.isHighConfidence) {
                                                    android.widget.Toast.makeText(context, context.getString(R.string.saving_matched_version), android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                android.widget.Toast.makeText(context, "Exporting to Music folder...", android.widget.Toast.LENGTH_SHORT).show()
                                                val workRequest = moe.rgsekai.sekaitune.download.createSaveToDeviceWorkRequest(
                                                    songId = result.catalogId,
                                                    title = songTitle,
                                                    artist = safeArtistName,
                                                )
                                                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                                            }
                                            is CatalogMatchResult.NoMatch -> {
                                                android.widget.Toast.makeText(context, context.getString(R.string.export_failed_no_catalog_match), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Exporting to Music folder...", android.widget.Toast.LENGTH_SHORT).show()
                                    val songId = mediaMetadata.id
                                    val workRequest = moe.rgsekai.sekaitune.download.createSaveToDeviceWorkRequest(
                                        songId = songId,
                                        title = songTitle,
                                        artist = safeArtistName,
                                    )
                                    androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                                }
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        if (!isLocalMedia) {
            item {
                MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                    when (download?.state) {
                        Download.STATE_COMPLETED -> {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = stringResource(R.string.remove_download),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.offline),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        val targetRemoveId = mediaMetadata.matchedCatalogId ?: librarySong?.song?.matchedCatalogId ?: mediaMetadata.id
                                        DownloadService.sendRemoveDownload(
                                            context,
                                            ExoDownloadService::class.java,
                                            targetRemoveId,
                                            false,
                                        )
                                        if (targetRemoveId != mediaMetadata.id) {
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                mediaMetadata.id,
                                                false,
                                            )
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }

                        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.downloading)) },
                                leadingContent = {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        val targetRemoveId = mediaMetadata.matchedCatalogId ?: librarySong?.song?.matchedCatalogId ?: mediaMetadata.id
                                        DownloadService.sendRemoveDownload(
                                            context,
                                            ExoDownloadService::class.java,
                                            targetRemoveId,
                                            false,
                                        )
                                        if (targetRemoveId != mediaMetadata.id) {
                                            DownloadService.sendRemoveDownload(
                                                context,
                                                ExoDownloadService::class.java,
                                                mediaMetadata.id,
                                                false,
                                            )
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }


                        else -> {
                            ListItem(
                                headlineContent = { Text(text = stringResource(R.string.action_download)) },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                    )
                                },
                                modifier =
                                    Modifier.clickable {
                                        onDismiss()
                                        if (mediaMetadata.isUploaded) {
                                            downloadUtil.applicationScope.launch {
                                                val dbSong = withContext(Dispatchers.IO) { database.song(mediaMetadata.id).firstOrNull() }
                                                val songEntity = dbSong?.song ?: mediaMetadata.toSongEntity()
                                                val artistEntities = dbSong?.artists ?: mediaMetadata.artists.map { ArtistEntity(id = it.id ?: "", name = it.name) }
                                                when (val result = resolveUploadedCatalogMatchUseCase(songEntity, artistEntities)) {
                                                    is CatalogMatchResult.Success -> {
                                                        withContext(Dispatchers.Main) {
                                                            if (!result.isHighConfidence) {
                                                                android.widget.Toast.makeText(context.applicationContext, context.applicationContext.getString(R.string.downloading_matched_version), android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        database.transaction {
                                                            insert(mediaMetadata)
                                                        }
                                                        val downloadRequest =
                                                            DownloadRequest
                                                                .Builder(result.catalogId, result.catalogId.toUri())
                                                                .setCustomCacheKey(result.catalogId)
                                                                .setData(mediaMetadata.title.toByteArray())
                                                                .build()
                                                        DownloadService.sendAddDownload(
                                                            context.applicationContext,
                                                            ExoDownloadService::class.java,
                                                            downloadRequest,
                                                            false,
                                                        )
                                                    }
                                                    is CatalogMatchResult.NoMatch -> {
                                                        withContext(Dispatchers.Main) {
                                                            android.widget.Toast.makeText(context.applicationContext, context.applicationContext.getString(R.string.no_catalog_match_found), android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            database.transaction {
                                                insert(mediaMetadata)
                                            }
                                            val downloadRequest =
                                                DownloadRequest
                                                    .Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                                                    .setCustomCacheKey(mediaMetadata.id)
                                                    .setData(mediaMetadata.title.toByteArray())
                                                    .build()
                                            DownloadService.sendAddDownload(
                                                context.applicationContext,
                                                ExoDownloadService::class.java,
                                                downloadRequest,
                                                false,
                                            )
                                        }
                                    },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                    if (externalDownloaderEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.open_with_downloader)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onDismiss()
                                    val url = "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                    if (externalDownloaderPackage.isBlank()) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.applicationContext.getString(R.string.external_downloader_not_configured),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        return@clickable
                                    }
                                    val intent =
                                        android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            setPackage(externalDownloaderPackage)
                                            data = android.net.Uri.parse(url)
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: android.content.ActivityNotFoundException) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.applicationContext.getString(R.string.external_downloader_not_installed),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
        item {
            MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
                Column {
                    if (isQueueTrigger == true && onRemoveFromQueue != null) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.remove_from_queue),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.delete),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onRemoveFromQueue()
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }

                    if (onShowComments != null && !isLocalMedia) {
                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.comments)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_comment),
                                    contentDescription = null,
                                )
                            },
                            modifier =
                                Modifier.clickable {
                                    onShowComments()
                                    onDismiss()
                                },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }

                    ListItem(
                        headlineContent = { Text(text = stringResource(R.string.details)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.info),
                                contentDescription = null,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                onShowDetailsDialog()
                                onDismiss()
                            },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    if (isQueueTrigger != true) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.equalizer)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.equalizer),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier.clickable {
                                playerBottomSheetState.collapseSoft()
                                onDismiss()
                                navController.navigate("equalizer")
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        ListItem(
                            headlineContent = { Text(text = stringResource(R.string.tempo_and_pitch)) },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.speed),
                                    contentDescription = null,
                                )
                            },
                            supportingContent = {
                                val playbackParameters by playerConnection.playbackParameters.collectAsState()
                                Text(
                                    text = "x${formatMultiplier(
                                        playbackParameters.speed,
                                    )} • x${formatMultiplier(playbackParameters.pitch)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.clickable { showPitchTempoDialog = true },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TempoPitchDialog(onDismiss: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val initialSpeed = remember { playerConnection.player.playbackParameters.speed }
    val initialPitch = remember { playerConnection.player.playbackParameters.pitch }

    var tempo by remember {
        mutableFloatStateOf(initialSpeed.safeCoerceIn(TempoMin, TempoMax, fallback = 1f))
    }

    var pitch by remember {
        mutableFloatStateOf(initialPitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f))
    }

    var pitchMode by rememberSaveable {
        mutableStateOf(
            if (isPitchSemitoneAligned(pitch)) PitchMode.Semitones else PitchMode.Multiplier,
        )
    }

    val applyPlaybackParameters: (Float, Float) -> Unit = { speed, pitchMultiplier ->
        playerConnection.player.playbackParameters =
            PlaybackParameters(
                speed.coerceIn(TempoMin, TempoMax),
                pitchMultiplier.coerceIn(PitchMin, PitchMax),
            )
    }

    DefaultDialog(
        onDismiss = onDismiss,
        title = {
            Text(stringResource(R.string.tempo_and_pitch))
        },
        buttons = {
            TextButton(
                onClick = {
                    tempo = 1f
                    pitch = 1f
                    applyPlaybackParameters(tempo, pitch)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.reset))
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.speed),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )

                    Text(
                        text = stringResource(R.string.tempo),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = "x${formatMultiplier(tempo)}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        enabled = tempo > TempoMin,
                        onClick = {
                            tempo = (tempo - 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                            applyPlaybackParameters(tempo, pitch)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.remove),
                            contentDescription = null,
                        )
                    }

                    Slider(
                        value = multiplierToSlider(tempo),
                        onValueChange = { slider ->
                            val updated = sliderToMultiplier(slider).quantize(0.01f)
                            if (abs(updated - tempo) >= 0.005f) {
                                tempo = updated
                                applyPlaybackParameters(tempo, pitch)
                            }
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(),
                    )

                    IconButton(
                        enabled = tempo < TempoMax,
                        onClick = {
                            tempo = (tempo + 0.01f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                            applyPlaybackParameters(tempo, pitch)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                ) {
                    val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                    presets.forEach { preset ->
                        val selected = abs(tempo - preset) < 0.005f
                        FilterChip(
                            selected = selected,
                            onClick = {
                                tempo = preset
                                applyPlaybackParameters(tempo, pitch)
                            },
                            label = { Text("x${formatMultiplier(preset)}") },
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.discover_tune),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )

                    Text(
                        text = stringResource(R.string.pitch),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text =
                            when (pitchMode) {
                                PitchMode.Semitones -> {
                                    val semitones = pitchToSemitones(pitch)
                                    "${if (semitones > 0) "+" else ""}$semitones"
                                }

                                PitchMode.Multiplier -> {
                                    "x${formatMultiplier(pitch)}"
                                }
                            },
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = pitchMode == PitchMode.Semitones,
                        onClick = { pitchMode = PitchMode.Semitones },
                        label = { Text(stringResource(R.string.pitch_mode_semitones_short)) },
                    )
                    FilterChip(
                        selected = pitchMode == PitchMode.Multiplier,
                        onClick = { pitchMode = PitchMode.Multiplier },
                        label = { Text(stringResource(R.string.pitch_mode_multiplier_short)) },
                    )
                }

                when (pitchMode) {
                    PitchMode.Semitones -> {
                        val currentSemitones = pitchToSemitones(pitch)
                        Slider(
                            value = currentSemitones.toFloat(),
                            onValueChange = { slider ->
                                val semitones = slider.roundToInt().coerceIn(-12, 12)
                                val updated = semitonesToPitch(semitones)
                                if (abs(updated - pitch) >= 0.0005f) {
                                    pitch = updated
                                    applyPlaybackParameters(tempo, pitch)
                                }
                            },
                            valueRange = -12f..12f,
                            steps = 23,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                        ) {
                            val presets = listOf(-12, -7, -5, 0, 5, 7, 12)
                            presets.forEach { preset ->
                                val selected = currentSemitones == preset
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        pitch = semitonesToPitch(preset)
                                        applyPlaybackParameters(tempo, pitch)
                                    },
                                    label = { Text("${if (preset > 0) "+" else ""}$preset") },
                                )
                            }
                        }
                    }

                    PitchMode.Multiplier -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            IconButton(
                                enabled = pitch > PitchMin,
                                onClick = {
                                    pitch = (pitch - 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                                    applyPlaybackParameters(tempo, pitch)
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.remove),
                                    contentDescription = null,
                                )
                            }

                            Slider(
                                value = multiplierToSlider(pitch),
                                onValueChange = { slider ->
                                    val updated = sliderToMultiplier(slider).quantize(0.01f)
                                    if (abs(updated - pitch) >= 0.005f) {
                                        pitch = updated
                                        applyPlaybackParameters(tempo, pitch)
                                    }
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(),
                            )

                            IconButton(
                                enabled = pitch < PitchMax,
                                onClick = {
                                    pitch = (pitch + 0.01f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                                    applyPlaybackParameters(tempo, pitch)
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.add),
                                    contentDescription = null,
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                        ) {
                            val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                            presets.forEach { preset ->
                                val selected = abs(pitch - preset) < 0.005f
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        pitch = preset
                                        applyPlaybackParameters(tempo, pitch)
                                    },
                                    label = { Text("x${formatMultiplier(preset)}") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

private enum class PitchMode {
    Semitones,
    Multiplier,
}

private const val TempoMin = 0.25f
private const val TempoMax = 2f
private const val PitchMin = 0.25f
private const val PitchMax = 2f

private fun Float.safeCoerceIn(
    min: Float,
    max: Float,
    fallback: Float,
): Float {
    val safe = if (this.isFinite()) this else fallback
    return safe.coerceIn(min, max)
}

private fun Float.quantize(step: Float): Float {
    if (step <= 0f) return this
    return (round(this / step) * step).coerceAtLeast(0f)
}

private fun pitchToSemitones(pitch: Float): Int {
    val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
    return (12f * log2(safePitch)).roundToInt().coerceIn(-12, 12)
}

private fun semitonesToPitch(semitones: Int): Float = 2f.pow(semitones.toFloat() / 12f).coerceIn(PitchMin, PitchMax)

private fun isPitchSemitoneAligned(pitch: Float): Boolean {
    val safePitch = pitch.safeCoerceIn(PitchMin, PitchMax, fallback = 1f).coerceAtLeast(0.0001f)
    val semitones = (12f * log2(safePitch)).roundToInt()
    val reconstructed = 2f.pow(semitones.toFloat() / 12f)
    return abs(reconstructed - pitch) < 0.0015f
}

private fun formatMultiplier(multiplier: Float): String = String.format("%.2f", multiplier)

private fun sliderToMultiplier(slider: Float): Float {
    val t = slider.coerceIn(0f, 1f)
    val y = (t - 0.5f) * 2f
    val curve = 2.2f
    val absY = abs(y).pow(curve)
    val shaped =
        when {
            y > 0f -> absY
            y < 0f -> -absY
            else -> 0f
        }
    val exponent = if (y < 0f) 2f * shaped else shaped
    return 2f.pow(exponent).coerceIn(TempoMin, TempoMax)
}

private fun multiplierToSlider(multiplier: Float): Float {
    val m = multiplier.coerceIn(TempoMin, TempoMax)
    val log = log2(m)
    val curve = 2.2f
    val shaped = if (m < 1f) (log / 2f) else log
    val absShaped = abs(shaped).pow(1f / curve)
    val y =
        when {
            shaped > 0f -> absShaped
            shaped < 0f -> -absShaped
            else -> 0f
        }
    return (0.5f + y / 2f).coerceIn(0f, 1f)
}

private enum class VideoRowSlot {
    PLAY,
    LOADING,
    EXIT,
    FULL_SCREEN,
}

