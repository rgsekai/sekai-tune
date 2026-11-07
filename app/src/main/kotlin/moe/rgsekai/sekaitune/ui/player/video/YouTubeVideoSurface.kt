/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player.video

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.constants.VideoQuality
import moe.rgsekai.sekaitune.constants.VideoQualityKey
import moe.rgsekai.sekaitune.ui.player.SongVideo
import moe.rgsekai.sekaitune.utils.YTPlayerUtils
import moe.rgsekai.sekaitune.utils.rememberEnumPreference

private const val SyncIntervalMs = 250L
private const val MaxRefreshes = 3

@Composable
fun rememberYouTubeVideoSession(videoId: String): YouTubeVideoSession {
    val context = LocalContext.current
    val session = remember(videoId) { YouTubeVideoSessions.acquire(context, videoId) }
    DisposableEffect(session) { onDispose { YouTubeVideoSessions.release(session) } }
    return session
}

@Composable
fun DriveYouTubeVideo(
    session: YouTubeVideoSession,
    video: SongVideo,
    isPlaying: Boolean,
    autoCap: Int,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val (quality) = rememberEnumPreference(VideoQualityKey, VideoQuality.AUTO)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running by rememberUpdatedState(isPlaying && lifecycle.isAtLeast(Lifecycle.State.STARTED))
    val positionAt by rememberUpdatedState(video.positionAt)
    var streams by remember(video.videoId) { mutableStateOf(video.streams) }
    LaunchedEffect(video.streams) { if (!video.streams.isExpired) streams = video.streams }

    fun target(songMs: Long): Long? {
        val duration = streams.durationMs
        return positionAt?.invoke(songMs) ?: if (duration > 0L) songMs % duration else null
    }

    LaunchedEffect(session, streams) {
        session.load(streams, quality, autoCap, target(playerConnection.player.currentPosition) ?: 0L)
    }
    LaunchedEffect(session, quality, autoCap) { session.setQuality(quality, autoCap) }

    LaunchedEffect(session) {
        snapshotFlow { session.failures }.filter { it in 1..MaxRefreshes }.collectLatest { count ->
            delay(1_000L * count * count)
            YTPlayerUtils.resolveVideoStreams(session.videoId).getOrNull()?.let { streams = it }
        }
    }

    LaunchedEffect(session) {
        while (isActive) {
            val player = playerConnection.player
            session.sync(target(player.currentPosition), player.playbackParameters.speed, running)
            delay(SyncIntervalMs)
        }
    }
}

private class VideoTexture(private val session: YouTubeVideoSession) : TextureView.SurfaceTextureListener {
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        session.attach(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        session.detach(surface) { surface.release() }
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}

@Composable
fun DebugVideoPlayerView(
    session: YouTubeVideoSession,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            FrameLayout(context).apply {
                val tv = TextureView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    surfaceTextureListener = VideoTexture(session)
                }
                addView(tv)
            }
        },
        modifier = modifier,
    )
}

