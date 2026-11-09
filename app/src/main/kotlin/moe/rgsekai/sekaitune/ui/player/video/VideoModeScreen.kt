package moe.rgsekai.sekaitune.ui.player.video

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.VideoModeWholeVideoKey
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import moe.rgsekai.sekaitune.models.MediaMetadata

import moe.rgsekai.sekaitune.ui.utils.ShowMediaInfo
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val ModeAutoCap = 1080
private const val FitMs = 600
private const val SeekStepMs = 10_000L
private const val PaneDim = 0.18f
private const val AmbientFadeMs = 1_400
private const val AmbientMinChange = 0.04f
private const val ControlsFadeMs = 280
private const val ControlsHideMs = 3_200L
private const val PaneDragShare = 0.7f
private val CloseDrag = 48.dp

internal fun sin01(t: Float): Float = sin(Math.PI.toFloat() * t.coerceIn(0f, 1f))

internal fun turnFit(w: Float, h: Float, restAngle: Float, angle: Float): Float {
    if (w <= 0f || h <= 0f) return 1f
    val screenW = if (restAngle == 0f) w else h
    val screenH = if (restAngle == 0f) h else w
    val a = Math.toRadians((restAngle + angle).toDouble())
    val c = abs(cos(a)).toFloat()
    val s = abs(sin(a)).toFloat()
    val extentW = w * c + h * s
    val extentH = w * s + h * c
    return minOf(screenW / extentW, screenH / extentH, 1f)
}

fun Modifier.quarterTurn(enabled: Boolean = true): Modifier = layout { measurable, constraints ->
    if (!enabled || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val placeable = measurable.measure(Constraints.fixed(h, w))
        layout(w, h) {
            placeable.placeWithLayer((w - h) / 2, (h - w) / 2) { rotationZ = 90f }
        }
    }
}

@Composable
fun VideoModeScreen(
    metadata: MediaMetadata,
    artworkUrl: String?,
    video: moe.rgsekai.sekaitune.ui.player.SongVideo?,
    searching: Boolean = false,
    lyricsSyncOffset: Int,
    onExitVideoMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current ?: return
    val player = connection.player
    val isPlaying by connection.isPlaying.collectAsState()

    // Video session reuse
    val session = if (video != null) rememberYouTubeVideoSession(video.videoId) else null
    if (session != null && video != null) {
        DriveYouTubeVideo(session, video, isPlaying, ModeAutoCap)
    }

    val isInPip = LocalInPipMode.current
    val currentLyrics by connection.currentLyrics.collectAsState(initial = null)
    val parsedLines: List<LyricsEntry> = remember(currentLyrics?.lyrics) {
        val raw = currentLyrics?.lyrics
        if (raw.isNullOrBlank() || raw == moe.rgsekai.sekaitune.db.entities.LyricsEntity.LYRICS_NOT_FOUND) {
            emptyList()
        } else if (LyricsUtils.isTtml(raw)) {
            LyricsUtils.parseTtml(raw)
        } else {
            LyricsUtils.parseLyrics(raw)
        }
    }

    // Update PiP actions when playback state changes while in PiP mode
    LaunchedEffect(isInPip, isPlaying, session?.aspect) {
        if (isInPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && session != null) {
            val activity = context as? Activity
            if (activity != null && isPipSupported(activity)) {
                try {
                    val params = buildPipParams(activity, session.aspect, isPlaying)
                    activity.setPictureInPictureParams(params)
                } catch (_: Exception) {}
            }
        }
    }

    // System bars and keep screen on management
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        var controller: WindowInsetsControllerCompat? = null
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val scope = rememberCoroutineScope()
    val state = remember(scope) { VideoModeState(scope) }
    val ambient = rememberAmbient()

    val controlsFade = animateFloatAsState(
        targetValue = if (state.controlsShown) 1f else 0f,
        animationSpec = tween(ControlsFadeMs, easing = FastOutSlowInEasing),
        label = "videoControls",
    )
    val controls = remember(controlsFade) { { controlsFade.value } }

    LaunchedEffect(state.controlsShown, state.lastTouchMs, isPlaying, state.menuOpen, state.paneDragging) {
        if (state.controlsShown && isPlaying && !state.menuOpen && !state.paneDragging) {
            delay(ControlsHideMs)
            state.controlsShown = false
        }
    }

    // Back handling: close pane first, then exit video mode
    BackHandler {
        if (state.paneOpen) {
            state.closePane()
        } else {
            onExitVideoMode()
        }
    }

    val (wholeVideo) = rememberPreference(VideoModeWholeVideoKey, defaultValue = false)
    val fit = animateFloatAsState(
        targetValue = if (wholeVideo) 1f else 0f,
        animationSpec = tween(FitMs, easing = FastOutSlowInEasing),
        label = "videoFit",
    )

    val activity = context as? Activity
    val onEnterPip: () -> Unit = {
        if (activity != null && session != null) {
            enterPictureInPictureMode(activity, session.aspect, isPlaying)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (isInPip) {
            // In PiP: reset quarter turn, hide all overlay/pane UI, show only the video surface
            if (session != null && session.hasFrame) {
                YouTubeVideoSurface(
                    session = session,
                    isPlaying = isPlaying,
                    fit = { fit.value },
                    keepScreenOn = isPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                VideoArtwork(thumbnailUrl = artworkUrl)
            }
        } else {
            VideoTurnFrame(modifier = Modifier.fillMaxSize()) {
                VideoModeLayout(
                    state = state,
                    metadata = metadata,
                    artworkUrl = artworkUrl,
                    video = video,
                    searching = searching,
                    session = session,
                    isPlaying = isPlaying,
                    ambient = ambient,
                    lyricsSyncOffset = lyricsSyncOffset,
                    parsedLines = parsedLines,
                    controls = controls,
                    fit = { fit.value },
                    onExit = onExitVideoMode,
                    onEnterPip = onEnterPip,
                )

                if (state.menuOpen) {
                    VideoModeMenu(
                        metadata = metadata,
                        artworkUrl = artworkUrl,
                        session = session,
                        onDismiss = { state.menuOpen = false },
                        onExitVideoMode = onExitVideoMode,
                        onEnterPip = onEnterPip,
                        onOpenPane = { state.openPane(it) },
                        onSongInfo = { state.songInfoOpen = true },
                    )
                }

                if (state.songInfoOpen) {
                    ShowMediaInfo(videoId = metadata.id)
                    state.songInfoOpen = false
                }
            }
        }
    }
}

@Composable
private fun VideoModeLayout(
    state: VideoModeState,
    metadata: MediaMetadata,
    artworkUrl: String?,
    video: moe.rgsekai.sekaitune.ui.player.SongVideo?,
    searching: Boolean,
    session: YouTubeVideoSession?,
    isPlaying: Boolean,
    ambient: VideoAmbientState,
    lyricsSyncOffset: Int,
    parsedLines: List<LyricsEntry>,
    controls: () -> Float,
    fit: () -> Float,
    onExit: () -> Unit,
    onEnterPip: () -> Unit,
) {
    val connection = LocalPlayerConnection.current ?: return
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(ambient.backLeft.value, ambient.backRight.value)))
            },
    ) {
        val frame = remember(maxWidth, maxHeight) { VideoFrame(maxWidth, maxHeight) }
        val density = LocalDensity.current
        val slidePx = with(density) { frame.videoSlide.toPx() }
        val paneShift = with(density) { frame.paneWidth.toPx() } / 2f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .videoGestures(
                    state = state,
                    frame = frame,
                    onCollapse = onExit,
                    onSeek = { forward ->
                        val p = connection.player
                        val dur = p.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                        val target = (p.currentPosition + if (forward) SeekStepMs else -SeekStepMs).coerceIn(0L, dur)
                        p.seekTo(target)
                        state.flashSeek(forward)
                    },
                ),
        ) {
            // Video surface layer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -slidePx * state.pane.value },
            ) {
                if (session == null || !session.hasFrame) {
                    VideoArtwork(thumbnailUrl = artworkUrl)
                }
                if (session != null) {
                    YouTubeVideoSurface(
                        session = session,
                        isPlaying = isPlaying,
                        fit = fit,
                        keepScreenOn = isPlaying,
                        onAmbient = ambient::follow,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            val loading = searching || (session != null && (!session.hasFrame || session.isStalled))
            VideoLoadingIndicator(
                loading = loading && isPlaying,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { translationX = -paneShift * state.pane.value },
            )

            val status = when {
                video != null -> null
                searching -> R.string.video_mode_finding
                else -> R.string.video_mode_no_video
            }
            if (status != null) {
                Text(
                    text = stringResource(status),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 40.dp)
                        .graphicsLayer {
                            alpha = 1f - controls()
                            translationX = -paneShift * state.pane.value
                        },
                )
            }

            // Pane layer
            if (state.paneMounted) {
                VideoPaneBackdrop(frame = frame, state = state, tint = ambient.pane)
                VideoPaneContent(frame = frame, state = state, lyricsSyncOffset = lyricsSyncOffset)
            }

            // Controls dim overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val c = controls()
                        if (c < 0.002f) return@drawBehind
                        drawRect(Color.Black.copy(alpha = 0.2f * c))
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.4f * c),
                                0.3f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.55f * c),
                            ),
                        )
                    },
            )

            // Bottom-left single-line lyrics when pane is closed
            if (parsedLines.isNotEmpty() && state.pane.value < 0.999f) {
                val playbackPosition = remember { mutableLongStateOf(connection.player.currentPosition) }
                LaunchedEffect(isPlaying, connection) {
                    while (true) {
                        withFrameNanos { }
                        playbackPosition.longValue = connection.player.currentPosition + lyricsSyncOffset
                        if (!isPlaying) delay(250)
                    }
                }

                val currentLineIndex by remember(parsedLines) {
                    derivedStateOf {
                        val pos = playbackPosition.longValue
                        val idx = parsedLines.indexOfLast { entry -> entry.time <= pos }
                        if (idx >= 0) idx else 0
                    }
                }

                val currentLineText = parsedLines.getOrNull(currentLineIndex)?.text.orEmpty()
                if (currentLineText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = if (controls() > 0.01f) 80.dp else 26.dp)
                            .widthIn(max = (frame.width * 0.55f).coerceAtLeast(200.dp))
                            .graphicsLayer {
                                val p = state.pane.value
                                alpha = (1f - p) * (if (controls() > 0.01f) 0.85f else 1f)
                                translationY = -controls() * 8.dp.toPx()
                            }
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable { state.openPane(VideoPane.LYRICS) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = currentLineText,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            VideoSeekFlash(frame = frame, state = state)
            VideoTopBar(
                metadata = metadata,
                artworkUrl = artworkUrl,
                frame = frame,
                state = state,
                controls = controls,
                onClose = onExit,
                onEnterPip = onEnterPip,
                onSongInfo = { state.songInfoOpen = true },
            )
            VideoTransport(state = state, controls = controls, isPlaying = isPlaying)
            VideoVolumeBar(state = state, controls = controls)
            VideoSeekRow(
                state = state,
                controls = controls,
                positionProvider = { connection.player.currentPosition },
                onChevron = { state.menuOpen = true },
            )
            VideoPaneHandle(frame = frame, state = state)
        }
    }
}

private enum class DragMode { NONE, UNDECIDED, PANE, COLLAPSE }

private fun Modifier.videoGestures(
    state: VideoModeState,
    frame: VideoFrame,
    onCollapse: () -> Unit,
    onSeek: (forward: Boolean) -> Unit,
): Modifier = this
    .pointerInput(state, frame) {
        detectTapGestures(
            onDoubleTap = { offset ->
                val videoWidth = size.width - (frame.paneWidth.toPx() * state.pane.value)
                if (offset.x < videoWidth) {
                    state.touched()
                    onSeek(offset.x > videoWidth / 2f)
                }
            },
            onTap = { offset ->
                if (paneHandleZone(frame, state.paneOpen, offset.x, offset.y, this)) {
                    if (state.paneOpen) state.closePane() else state.openPane(VideoPane.LYRICS)
                } else if (state.paneOpen && !paneSide(frame, offset.x, this)) {
                    state.closePane()
                } else {
                    state.toggleControls()
                }
            },
        )
    }
    .pointerInput(state, frame) {
        val travel = size.height * PaneDragShare
        val closePx = CloseDrag.toPx()
        val tracker = VelocityTracker()
        var mode = DragMode.NONE
        var pulled = 0f
        detectVerticalDragGestures(
            onDragStart = { offset ->
                tracker.resetTracking()
                pulled = 0f
                mode = when {
                    state.paneOpen && paneHandleZone(frame, true, offset.x, offset.y, this) -> {
                        state.startPaneDrag()
                        DragMode.PANE
                    }
                    state.paneOpen -> DragMode.NONE
                    paneSide(frame, offset.x, this) -> DragMode.UNDECIDED
                    else -> DragMode.COLLAPSE
                }
            },
            onVerticalDrag = { change, dy ->
                tracker.addPosition(change.uptimeMillis, change.position)
                if (mode == DragMode.UNDECIDED) {
                    mode = if (dy < 0f) {
                        state.startPaneDrag()
                        DragMode.PANE
                    } else {
                        DragMode.COLLAPSE
                    }
                }
                when (mode) {
                    DragMode.PANE -> {
                        change.consume()
                        state.dragPaneBy(-dy / travel)
                    }
                    DragMode.COLLAPSE -> {
                        change.consume()
                        pulled += dy
                        if (pulled > closePx) {
                            mode = DragMode.NONE
                            onCollapse()
                        }
                    }
                    else -> Unit
                }
            },
            onDragEnd = {
                if (mode == DragMode.PANE) state.settlePane(-tracker.calculateVelocity().y / travel)
                mode = DragMode.NONE
            },
            onDragCancel = {
                if (mode == DragMode.PANE) state.settlePane(0f)
                mode = DragMode.NONE
            },
        )
    }

@Composable
private fun VideoTurnFrame(modifier: Modifier, content: @Composable () -> Unit) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(2) { withFrameNanos { } }
        turn.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 150f))
    }
    Box(
        modifier = modifier
            .quarterTurn(true)
            .graphicsLayer {
                val t = turn.value
                if (t == 1f) return@graphicsLayer
                val angle = -90f * (1f - t)
                rotationZ = angle
                val scale = turnFit(size.width, size.height, 90f, angle) * (1f - 0.04f * sin01(t))
                scaleX = scale
                scaleY = scale
                cameraDistance = 14f * density
                rotationX = 9f * sin01(t)
                alpha = smooth(t / 0.45f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blur = 14.dp.toPx() * sin01(t)
                    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Clamp) else null
                }
            },
    ) {
        content()
    }
}

@Composable
private fun VideoArtwork(thumbnailUrl: String?) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(48.dp.toPx(), 48.dp.toPx(), TileMode.Clamp)
                    }
                },
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
    }
}

internal class VideoAmbientState(private val scope: kotlinx.coroutines.CoroutineScope) {
    val backLeft = androidx.compose.animation.Animatable(Color.Black)
    val backRight = androidx.compose.animation.Animatable(Color.Black)
    private val paneTone = androidx.compose.animation.Animatable(Color(0xFF1E1E1E))
    val pane: androidx.compose.runtime.State<Color> = paneTone.asState()

    private var seen: VideoAmbient? = null

    fun follow(next: VideoAmbient) {
        val last = seen
        if (last != null && distance(last.left, next.left) < AmbientMinChange && distance(last.right, next.right) < AmbientMinChange) return
        seen = next
        val spec = tween<Color>(AmbientFadeMs, easing = FastOutSlowInEasing)
        scope.launch { backLeft.animateTo(tone(next.left, 0.16f), spec) }
        scope.launch { backRight.animateTo(tone(next.right, 0.16f), spec) }
        scope.launch { paneTone.animateTo(tone(next.right, 0.24f), spec) }
    }

    private fun distance(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))

    private fun tone(color: Color, maxLightness: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        hsl[1] = hsl[1] * 0.8f
        hsl[2] = hsl[2].coerceIn(0.06f, maxLightness)
        return Color(ColorUtils.HSLToColor(hsl))
    }
}

@Composable
private fun rememberAmbient(): VideoAmbientState {
    val scope = rememberCoroutineScope()
    return remember(scope) { VideoAmbientState(scope) }
}

