package moe.rgsekai.sekaitune.ui.player.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.LocalDatabase
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.VideoModeWholeVideoKey
import moe.rgsekai.sekaitune.constants.VideoQuality
import moe.rgsekai.sekaitune.constants.VideoQualityKey
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.ui.menu.AddToPlaylistDialog
import moe.rgsekai.sekaitune.ui.menu.TempoPitchDialog
import moe.rgsekai.sekaitune.ui.player.SleepTimerDialog
import moe.rgsekai.sekaitune.ui.screens.settings.VideoQualityBottomSheet
import moe.rgsekai.sekaitune.ui.screens.settings.videoSizeName
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference

@Composable
internal fun qualityLabel(quality: VideoQuality, session: YouTubeVideoSession?): String {
    if (quality != VideoQuality.AUTO) return videoSizeName(quality.shortSide)
    val playing = session?.playingShortSide ?: 0
    return if (playing > 0) "Auto · ${videoSizeName(playing)}" else "Auto"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoModeMenu(
    metadata: MediaMetadata,
    artworkUrl: String?,
    session: YouTubeVideoSession?,
    onDismiss: () -> Unit,
    onExitVideoMode: () -> Unit,
    onOpenPane: (VideoPane) -> Unit,
    onSongInfo: () -> Unit,
) {
    val connection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val (quality, setQuality) = rememberEnumPreference(VideoQualityKey, VideoQuality.AUTO)
    val (wholeVideo, setWholeVideo) = rememberPreference(VideoModeWholeVideoKey, defaultValue = false)
    val currentSong by connection.currentSong.collectAsState(initial = null)
    val isLiked = currentSong?.song?.liked == true

    var showQuality by remember { mutableStateOf(false) }
    val qualitySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showTempoPitch by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun closeThen(action: () -> Unit) {
        coroutineScope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 28.dp),
        ) {
            // Header: Cover + Title + Artist + Like
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata.title,
                        color = Color.White,
                        
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata.artists.joinToString { it.name },
                        color = Color(0xFFB3B3B3),
                        
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                PressScaleActionButton(
                    icon = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                    tint = if (isLiked) Color(0xFFFF4081) else Color.White,
                    containerColor = Color.White.copy(alpha = if (isLiked) 0.25f else 0.12f),
                    onClick = {
                        coroutineScope.launch {
                            val song = currentSong?.song ?: return@launch
                            database.query {
                                update(song.toggleLike())
                            }
                        }
                    },
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Cards: Exit Video & Add to Playlist
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MenuActionCard(
                    icon = R.drawable.fullscreen_exit,
                    title = stringResource(R.string.video_mode_exit),
                    onClick = { closeThen(onExitVideoMode) },
                    modifier = Modifier.weight(1f),
                )
                MenuActionCard(
                    icon = R.drawable.playlist_add,
                    title = stringResource(R.string.add_to_playlist),
                    onClick = { showAddToPlaylist = true },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Details / Info
            MenuOptionRow(
                icon = R.drawable.info,
                title = stringResource(R.string.video_mode_song_info),
                onClick = { closeThen(onSongInfo) },
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quality & Whole Video
            MenuOptionRow(
                icon = R.drawable.tune,
                title = stringResource(R.string.video_quality),
                onClick = { showQuality = true },
                trailing = {
                    Text(
                        text = qualityLabel(quality, session),
                        color = Color(0xFF9A9A9A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            MenuOptionRow(
                icon = R.drawable.fullscreen,
                title = stringResource(R.string.video_mode_whole_video),
                onClick = { setWholeVideo(!wholeVideo) },
                trailing = {
                    Switch(
                        checked = wholeVideo,
                        onCheckedChange = setWholeVideo,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.White.copy(alpha = 0.5f),
                            uncheckedThumbColor = Color(0xFFB3B3B3),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                    )
                },
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Panes: Lyrics & Queue
            MenuOptionRow(
                icon = R.drawable.lyrics,
                title = stringResource(R.string.video_mode_lyrics),
                onClick = { closeThen { onOpenPane(VideoPane.LYRICS) } },
            )
            Spacer(modifier = Modifier.height(8.dp))
            MenuOptionRow(
                icon = R.drawable.queue_music,
                title = stringResource(R.string.video_mode_queue),
                onClick = { closeThen { onOpenPane(VideoPane.QUEUE) } },
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Playback: Speed & Sleep Timer
            MenuOptionRow(
                icon = R.drawable.speed,
                title = stringResource(R.string.video_mode_speed),
                onClick = { showTempoPitch = true },
            )
            Spacer(modifier = Modifier.height(8.dp))
            MenuOptionRow(
                icon = R.drawable.sleep_timer,
                title = stringResource(R.string.sleep_timer),
                onClick = { showSleepTimer = true },
            )

            // PiP placeholder spot - Phase 6
            // MenuOptionRow(
            //     icon = R.drawable.pip,
            //     title = stringResource(R.string.pip),
            //     onClick = { closeThen { /* enter PiP in Phase 6 */ } },
            // )
        }
    }

    if (showQuality) {
        VideoQualityBottomSheet(
            selectedQuality = quality,
            session = session,
            sheetState = qualitySheetState,
            onDismiss = { showQuality = false },
            onQualitySelected = { newQuality ->
                setQuality(newQuality)
                coroutineScope.launch { qualitySheetState.hide() }.invokeOnCompletion {
                    showQuality = false
                }
            },
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            isVisible = showAddToPlaylist,
            onGetSong = { listOf(metadata.id) },
            onDismiss = { showAddToPlaylist = false },
        )
    }

    if (showTempoPitch) {
        TempoPitchDialog(onDismiss = { showTempoPitch = false })
    }

    if (showSleepTimer) {
        SleepTimerDialog(
            onDismiss = { showSleepTimer = false },
            onConfirm = { minutes ->
                connection.service.sleepTimer.start(minutes)
                showSleepTimer = false
            },
            onEndOfSong = {
                connection.service.sleepTimer.start(-1)
                showSleepTimer = false
            },
        )
    }
}

@Composable
private fun MenuActionCard(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = Color.White,
                
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuOptionRow(
    icon: Int,
    title: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            color = Color.White,
            
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            trailing()
        }
    }
}

