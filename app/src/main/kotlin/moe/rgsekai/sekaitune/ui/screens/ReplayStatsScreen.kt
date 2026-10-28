package moe.rgsekai.sekaitune.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.data.model.Song
import moe.rgsekai.sekaitune.data.model.durationMillis
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.playback.queues.YouTubeQueue
import moe.rgsekai.sekaitune.ui.replay.ReplayScreen
import moe.rgsekai.sekaitune.ui.replay.ReplayShareSheet
import moe.rgsekai.sekaitune.ui.replay.ReplayStories
import moe.rgsekai.sekaitune.ui.replay.ReplayStoryPage
import moe.rgsekai.sekaitune.ui.replay.rememberReplayState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayStatsScreen(
    navController: NavController,
) {
    val playerConnection = LocalPlayerConnection.current
    val (replayState, setReplayPeriod) = rememberReplayState(active = true)

    var replayStory by remember { mutableStateOf<ReplayStoryPage?>(null) }
    var showReplayShare by remember { mutableStateOf(false) }
    var replaySharePage by remember { mutableStateOf<ReplayStoryPage?>(null) }

    val systemBarsTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val playerAwareInsets = LocalPlayerAwareWindowInsets.current
    val bottomPadding = playerAwareInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        ReplayScreen(
            state = replayState,
            onBack = navController::navigateUp,
            holder = "Sekai Tune",
            onPeriodChange = setReplayPeriod,
            onOpenStory = { replayStory = it },
            onPlaySong = { song: Song ->
                playerConnection?.playQueue(
                    YouTubeQueue(
                        endpoint = WatchEndpoint(song.videoId),
                        preloadItem = MediaMetadata(
                            id = song.videoId,
                            title = song.title,
                            artists = emptyList(),
                            duration = (song.durationMillis() / 1000).toInt(),
                            thumbnailUrl = song.thumbnailUrl,
                        ),
                    ),
                )
            },
            onOpenArtist = { id, _ ->
                if (!id.isNullOrBlank()) {
                    navController.navigate("artist/$id")
                }
            },
            onOpenAlbum = { id, _, _, _ ->
                if (!id.isNullOrBlank()) {
                    navController.navigate("album/$id")
                }
            },
            onShare = {
                replaySharePage = null
                showReplayShare = true
            },
            contentPadding = PaddingValues(top = systemBarsTop, bottom = bottomPadding),
        )

        // Story Showcase overlay
        replayStory?.let { start ->
            replayState.summary?.takeUnless { it.isEmpty }?.let { summary ->
                ReplayStories(
                    summary = summary,
                    start = start,
                    onClose = { replayStory = null },
                    onShare = { page ->
                        replaySharePage = page
                        showReplayShare = true
                    },
                    paused = showReplayShare,
                )
            }
        }

        // Replay Share Bottom Sheet
        if (showReplayShare) {
            replayState.summary?.takeUnless { it.isEmpty }?.let { summary ->
                ModalBottomSheet(
                    onDismissRequest = { showReplayShare = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    ReplayShareSheet(
                        summary = summary,
                        holder = "Sekai Tune",
                        memberSince = replayState.memberSince,
                        page = replaySharePage,
                        onDismiss = { showReplayShare = false },
                    )
                }
            }
        }
    }
}
