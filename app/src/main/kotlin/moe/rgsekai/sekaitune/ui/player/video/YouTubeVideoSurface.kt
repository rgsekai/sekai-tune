/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player.video

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.constants.VideoQuality
import moe.rgsekai.sekaitune.constants.VideoQualityKey
import moe.rgsekai.sekaitune.ui.player.LetterboxStabilizer
import moe.rgsekai.sekaitune.ui.player.SongVideo
import moe.rgsekai.sekaitune.ui.player.VideoLetterbox
import moe.rgsekai.sekaitune.ui.player.VideoTransform
import moe.rgsekai.sekaitune.ui.player.videoCropTransform
import moe.rgsekai.sekaitune.utils.YTPlayerUtils
import moe.rgsekai.sekaitune.utils.rememberEnumPreference

private const val SampleSize = 144
private const val SampleIntervalMs = 300L
private const val BarZoomMs = 700
private const val SyncIntervalMs = 250L
private const val MaxRefreshes = 3
private const val EdgeBand = 0.12f

@Immutable
data class VideoAmbient(val left: Color, val right: Color)

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
    val isInPip = LocalInPipMode.current
    val running by rememberUpdatedState(isPlaying && (isInPip || lifecycle.isAtLeast(Lifecycle.State.STARTED)))
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

@Composable
fun YouTubeVideoSurface(
    session: YouTubeVideoSession,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    fit: () -> Float = { 0f },
    keepScreenOn: Boolean = false,
    onAmbient: ((VideoAmbient) -> Unit)? = null,
    onFrameColors: ((List<Color>) -> Unit)? = null,
) {
    val context = LocalContext.current
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val playing by rememberUpdatedState(isPlaying)
    val currentOnAmbient by rememberUpdatedState(onAmbient)
    val currentOnFrameColors by rememberUpdatedState(onFrameColors)

    val textureView = remember(session) {
        TextureView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            pivotX = 0f
            pivotY = 0f
            surfaceTextureListener = VideoTexture(session)
        }
    }
    val videoFrame = remember(textureView) {
        FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            clipChildren = true
            isEnabled = false
            isClickable = false
            isFocusable = false
            addView(textureView)
        }
    }

    val barInsetX = remember(session) { Animatable(0f) }
    val barInsetY = remember(session) { Animatable(0f) }

    LaunchedEffect(session) {
        snapshotFlow { session.hasFrame }.first { it }
        val sample = Bitmap.createBitmap(SampleSize, SampleSize, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(SampleSize * SampleSize)
        val vertical = LetterboxStabilizer()
        val horizontal = LetterboxStabilizer()
        val zoomSpec = tween<Float>(BarZoomMs, easing = FastOutSlowInEasing)
        var read = false
        while (isActive) {
            delay(SampleIntervalMs)
            if ((read && !playing) || !textureView.isAvailable) continue
            if (runCatching { textureView.getBitmap(sample) }.isFailure) continue
            sample.getPixels(pixels, 0, SampleSize, 0, 0, SampleSize, SampleSize)
            read = true

            val reading = VideoLetterbox.analyze(pixels, SampleSize, SampleSize)
            val now = SystemClock.uptimeMillis()
            if (vertical.offer(reading.vertical, now)) launch { barInsetY.animateTo(vertical.inset, zoomSpec) }
            if (horizontal.offer(reading.horizontal, now)) launch { barInsetX.animateTo(horizontal.inset, zoomSpec) }
            currentOnAmbient?.invoke(edgeColors(pixels, SampleSize, horizontal.inset, vertical.inset))
        }
    }

    val currentFit by rememberUpdatedState(fit)
    LaunchedEffect(textureView) {
        snapshotFlow {
            val t = currentFit().coerceIn(0f, 1f)
            fun transform(maxLoss: Float) = videoCropTransform(
                viewWidth = viewSize.width.toFloat(),
                viewHeight = viewSize.height.toFloat(),
                videoAspect = session.aspect,
                insetX = barInsetX.value,
                insetY = barInsetY.value,
                maxLoss = maxLoss,
            )
            val filled = transform(1f)
            if (t == 0f) filled else filled.lerpTo(transform(0f), t)
        }.collect { transform ->
            textureView.scaleX = transform.scaleX
            textureView.scaleY = transform.scaleY
            textureView.translationX = transform.translationX
            textureView.translationY = transform.translationY
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (session.hasFrame) 1f else 0f,
        animationSpec = tween(400),
        label = "youtubeVideoAlpha",
    )

    key(videoFrame) {
        AndroidView(
            factory = { videoFrame },
            update = { it.keepScreenOn = keepScreenOn },
            modifier = modifier
                .onSizeChanged { viewSize = it }
                .graphicsLayer { this.alpha = alpha },
        )
    }
}

private fun VideoTransform.lerpTo(
    other: VideoTransform,
    t: Float,
) = VideoTransform(
    scaleX = scaleX + (other.scaleX - scaleX) * t,
    scaleY = scaleY + (other.scaleY - scaleY) * t,
    translationX = translationX + (other.translationX - translationX) * t,
    translationY = translationY + (other.translationY - translationY) * t,
)

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

internal fun edgeColors(pixels: IntArray, size: Int, insetX: Float, insetY: Float): VideoAmbient {
    val top = (insetY * size).toInt().coerceIn(0, size / 2 - 1)
    val bottom = size - top
    val left = (insetX * size).toInt().coerceIn(0, size / 2 - 1)
    val right = size - left
    val band = ((right - left) * EdgeBand).toInt().coerceAtLeast(2)

    fun average(from: Int, to: Int): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0
        for (y in top until bottom) {
            val row = y * size
            for (x in from until to) {
                val c = pixels[row + x]
                r += (c shr 16) and 0xff
                g += (c shr 8) and 0xff
                b += c and 0xff
                count++
            }
        }
        if (count == 0) return Color.Transparent
        return Color((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    return VideoAmbient(
        left = average(left, left + band),
        right = average(right - band, right),
    )
}
