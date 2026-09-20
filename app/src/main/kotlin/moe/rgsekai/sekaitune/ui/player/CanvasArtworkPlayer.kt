/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package moe.rgsekai.sekaitune.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rgsekai.sekaitune.canvas.KenBurnsCanvas
import moe.rgsekai.sekaitune.canvas.ProceduralCanvas
import moe.rgsekai.sekaitune.canvas.ProceduralCanvasStyle
import moe.rgsekai.sekaitune.canvas.SquareTunnelCanvas
import moe.rgsekai.sekaitune.canvas.isProceduralShaderSupported
import moe.rgsekai.sekaitune.innertube.YouTube
import moe.rgsekai.sekaitune.utils.StreamClientUtils
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale

private const val CanvasPlaybackStallCheckIntervalMs = 1_000L
private const val CanvasPlaybackStallTimeoutMs = 5_000L

/**
 * Represents the target rendering mode for Canvas artwork playback.
 */
sealed interface CanvasRenderMode {
    data class Video(
        val primaryUrl: String,
        val fallbackUrl: String? = null,
    ) : CanvasRenderMode

    data class ProceduralShader(
        val bitmap: Bitmap,
        val style: ProceduralCanvasStyle = ProceduralCanvasStyle.KAWARP,
        val audioSessionId: Int = 0,
        val audioReactive: Boolean = false,
    ) : CanvasRenderMode

    data class KenBurns(
        val bitmap: Bitmap,
    ) : CanvasRenderMode

    data class Static(
        val bitmap: Bitmap? = null,
        val url: String? = null,
    ) : CanvasRenderMode

    data object None : CanvasRenderMode
}

/**
 * Resolves whether a static bitmap should be rendered using the AGSL Procedural Shader (API 33+)
 * or the Ken Burns pan/zoom fallback.
 */
internal fun resolveProceduralRenderMode(
    context: Context,
    bitmap: Bitmap?,
    style: ProceduralCanvasStyle = ProceduralCanvasStyle.KAWARP,
    audioSessionId: Int = 0,
    audioReactive: Boolean = false,
): CanvasRenderMode {
    if (bitmap == null) return CanvasRenderMode.None
    return if (isProceduralShaderSupported(context)) {
        CanvasRenderMode.ProceduralShader(
            bitmap = bitmap,
            style = style,
            audioSessionId = audioSessionId,
            audioReactive = audioReactive,
        )
    } else {
        CanvasRenderMode.KenBurns(bitmap)
    }
}

@Composable
internal fun CanvasArtworkPlayer(
    renderMode: CanvasRenderMode,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    when (renderMode) {
        is CanvasRenderMode.Video -> {
            CanvasVideoPlayer(
                primaryUrl = renderMode.primaryUrl,
                fallbackUrl = renderMode.fallbackUrl,
                isPlaying = isPlaying,
                modifier = modifier,
                resizeMode = resizeMode,
            )
        }
        is CanvasRenderMode.ProceduralShader -> {
            when (renderMode.style) {
                ProceduralCanvasStyle.KAWARP -> {
                    ProceduralCanvas(
                        bitmap = renderMode.bitmap,
                        modifier = modifier,
                        isPlaying = isPlaying,
                        audioSessionId = renderMode.audioSessionId,
                        audioReactive = renderMode.audioReactive,
                    )
                }
                ProceduralCanvasStyle.SQUARE_TUNNEL -> {
                    SquareTunnelCanvas(
                        bitmap = renderMode.bitmap,
                        modifier = modifier,
                        isPlaying = isPlaying,
                        audioSessionId = renderMode.audioSessionId,
                        audioReactive = renderMode.audioReactive,
                    )
                }
            }
        }
        is CanvasRenderMode.KenBurns -> {
            KenBurnsCanvas(
                bitmap = renderMode.bitmap,
                modifier = modifier,
                isPlaying = isPlaying,
            )
        }
        is CanvasRenderMode.Static -> {
            if (renderMode.bitmap != null) {
                Image(
                    bitmap = renderMode.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = resizeMode.toContentScale(),
                    modifier = modifier,
                )
            } else if (!renderMode.url.isNullOrBlank()) {
                AsyncImage(
                    model = rememberOfflineArtworkImageRequest(renderMode.url),
                    contentDescription = null,
                    contentScale = resizeMode.toContentScale(),
                    modifier = modifier,
                )
            }
        }
        is CanvasRenderMode.None -> {
            // No-op
        }
    }
}

@Composable
internal fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback
    if (initial == null) return
    CanvasArtworkPlayer(
        renderMode = CanvasRenderMode.Video(primaryUrl = initial, fallbackUrl = fallback),
        isPlaying = isPlaying,
        modifier = modifier,
        resizeMode = resizeMode,
    )
}

@Composable
private fun CanvasVideoPlayer(
    primaryUrl: String,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initial = primaryUrl
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    val shouldPlay by rememberUpdatedState(isPlaying)

    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) {
                        return@addInterceptor chain.proceed(
                            request
                                .newBuilder()
                                .header("User-Agent", CanvasPlaybackUserAgent)
                                .build(),
                        )
                    }

                    val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                    chain.proceed(
                        StreamClientUtils
                            .applyRequestProfile(
                                request.newBuilder(),
                                requestProfile,
                            ).build(),
                    )
                }.build()
        }
    val mediaSourceFactory =
        remember(okHttpClient) {
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                ),
            )
        }
    val renderersFactory =
        remember(context) {
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        }
    val exoPlayer =
        remember(initial, mediaSourceFactory, renderersFactory) {
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .build()
                .apply {
                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .build()
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = isPlaying
                }
        }

    val watchdog =
        remember(currentUrl, fallback) {
            CanvasPlaybackWatchdog(
                stallTimeoutMs = CanvasPlaybackStallTimeoutMs,
                checkIntervalMs = CanvasPlaybackStallCheckIntervalMs,
                onStallDetected = { stalledMs, posMs ->
                    if (!fallback.isNullOrBlank() && fallback != currentUrl) {
                        Timber.tag(CanvasPlaybackLogTag).w(
                            "Canvas stream stalled for %d ms at pos=%d. Switching to fallback URL %s",
                            stalledMs, posMs, fallback,
                        )
                        currentUrl = fallback
                        isVideoReady = false
                    } else {
                        Timber.tag(CanvasPlaybackLogTag).w(
                            "Canvas stream stalled for %d ms at pos=%d. In-place recovering stream %s",
                            stalledMs, posMs, currentUrl,
                        )
                        exoPlayer.seekTo(0)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                },
            )
        }

    Timber.tag(CanvasPlaybackLogTag).d(
        "CanvasArtworkPlayer composed: currentUrl=%s, primary=%s, fallback=%s, isPlaying=%s",
        currentUrl, primaryUrl, fallback, isPlaying,
    )

    LaunchedEffect(isPlaying) {
        Timber.tag(CanvasPlaybackLogTag).d("Canvas isPlaying changed: %s (playbackState=%s)", isPlaying, exoPlayer.playbackState)
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    // Active playback & initial load stall watchdog:
    // Distinguishes loop wraps (position changes / discontinuities) from genuine hangs (position unchanged for >= timeout)
    LaunchedEffect(currentUrl, isPlaying, watchdog, exoPlayer) {
        if (!isPlaying) {
            watchdog.reset()
            return@LaunchedEffect
        }

        while (isActive && isPlaying) {
            delay(CanvasPlaybackStallCheckIntervalMs)
            val currentPos = exoPlayer.currentPosition
            val state = exoPlayer.playbackState
            val isBufferingOrReady = state == Player.STATE_BUFFERING || state == Player.STATE_READY
            watchdog.tick(
                currentPositionMs = currentPos,
                isBufferingOrReady = isBufferingOrReady,
                playWhenReady = exoPlayer.playWhenReady,
            )
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                Timber.tag(CanvasPlaybackLogTag).d("Lifecycle event: %s (shouldPlay=%s)", event, shouldPlay)
                if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                    exoPlayer.setCanvasPlayback(shouldPlay)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer, primaryUrl, fallback, watchdog) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(CanvasPlaybackLogTag).w(error, "Canvas playback error on %s", currentUrl)
                    val next =
                        when (currentUrl) {
                            primaryUrl -> fallback?.takeIf { it != currentUrl }
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        Timber.tag(CanvasPlaybackLogTag).i("Switching to fallback URL after player error: %s", next)
                        currentUrl = next
                        isVideoReady = false
                    }
                }

                override fun onRenderedFirstFrame() {
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas rendered first frame for %s", currentUrl)
                    isVideoReady = true
                    watchdog.onFirstFrameRendered()
                    if (shouldPlay) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateName = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN($playbackState)"
                    }
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas playbackState: %s (playWhenReady=%s)", stateName, exoPlayer.playWhenReady)
                    if (!shouldPlay) return
                    exoPlayer.setCanvasPlayback(isPlaying = true)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    Timber.tag(CanvasPlaybackLogTag).v("Canvas position discontinuity: %d -> %d (reason=%d)", oldPosition.positionMs, newPosition.positionMs, reason)
                    watchdog.onPositionDiscontinuity(newPosition.positionMs)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas playWhenReady: %s (reason=%d)", playWhenReady, reason)
                    if (shouldPlay && !playWhenReady) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    Timber.tag(CanvasPlaybackLogTag).d("Canvas onIsPlayingChanged: %s", isPlaying)
                    if (shouldPlay && !isPlaying) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        isVideoReady = false
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") || lowercaseUrl.contains(".hls") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                else -> null
            }

        Timber.tag(CanvasPlaybackLogTag).i("Preparing ExoPlayer for Canvas URL: %s (mimeType=%s)", normalized, mimeType)

        val mediaItemBuilder = MediaItem.Builder().setUri(normalized)
        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType)
        }
        val mediaItem = mediaItemBuilder.build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    ContentFrame(
        player = exoPlayer,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        contentScale = resizeMode.toContentScale(),
        keepContentOnReset = false,
        shutter = {},
        modifier = modifier.alpha(alpha),
    )
}

private fun Int.toContentScale(): ContentScale =
    when (this) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop

        AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds

        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        -> ContentScale.Fit

        else -> ContentScale.Fit
    }

private fun ExoPlayer.setCanvasPlayback(isPlaying: Boolean) {
    if (isPlaying) {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        play()
    } else {
        pause()
    }
}

private const val CanvasPlaybackLogTag = "CanvasPlayback"
private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"

internal class CanvasPlaybackWatchdog(
    val stallTimeoutMs: Long = CanvasPlaybackStallTimeoutMs,
    val checkIntervalMs: Long = CanvasPlaybackStallCheckIntervalMs,
    private val onStallDetected: (stalledMs: Long, positionMs: Long) -> Unit,
) {
    var lastPositionMs: Long = -1L
        private set
    var stalledForMs: Long = 0L
        private set

    fun reset() {
        lastPositionMs = -1L
        stalledForMs = 0L
    }

    fun onPositionDiscontinuity(newPositionMs: Long) {
        lastPositionMs = newPositionMs
        stalledForMs = 0L
    }

    fun onFirstFrameRendered() {
        stalledForMs = 0L
    }

    fun tick(currentPositionMs: Long, isBufferingOrReady: Boolean, playWhenReady: Boolean) {
        val progressMade = (lastPositionMs < 0L) || (currentPositionMs != lastPositionMs)
        lastPositionMs = currentPositionMs

        if (progressMade) {
            stalledForMs = 0L
        } else if (isBufferingOrReady && playWhenReady) {
            stalledForMs += checkIntervalMs
        }

        if (stalledForMs >= stallTimeoutMs) {
            onStallDetected(stalledForMs, currentPositionMs)
            stalledForMs = 0L
        }
    }
}
