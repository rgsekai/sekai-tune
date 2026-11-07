/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import moe.rgsekai.sekaitune.ui.player.video.DriveYouTubeVideo
import moe.rgsekai.sekaitune.ui.player.video.VideoLoadingIndicator
import moe.rgsekai.sekaitune.ui.player.video.YouTubeVideoSurface
import moe.rgsekai.sekaitune.ui.player.video.rememberYouTubeVideoSession
import moe.rgsekai.sekaitune.ui.utils.rememberSettled

private const val VideoSettleMs = 700L
private const val BackgroundAutoCap = 720

@Composable
fun YouTubeVideoBackground(
    video: SongVideo?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onShowingChange: ((Boolean) -> Unit)? = null,
) {
    if (video == null) {
        LaunchedEffect(Unit) { onShowingChange?.invoke(false) }
        return
    }
    val settledId = rememberSettled(video.videoId, VideoSettleMs) ?: return
    if (settledId != video.videoId) return
    val session = rememberYouTubeVideoSession(settledId)
    val currentOnShowing by rememberUpdatedState(onShowingChange)
    LaunchedEffect(session) {
        snapshotFlow { session.hasFrame }.collect { currentOnShowing?.invoke(it) }
    }
    DisposableEffect(session) { onDispose { currentOnShowing?.invoke(false) } }
    DriveYouTubeVideo(session, video, isPlaying, autoCap = BackgroundAutoCap)
    Box(modifier = modifier) {
        YouTubeVideoSurface(
            session = session,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize(),
        )
        VideoLoadingIndicator(
            loading = session.hasFrame && session.isStalled,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

