package moe.rgsekai.sekaitune.ui.player.video

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.models.MediaMetadata

import moe.rgsekai.sekaitune.utils.makeTimeString
import kotlin.math.roundToInt

// Controls line up with artwork on left and seek bar end on right
private val EdgeStart = 48.dp
private val EdgeEnd = 54.dp
private val TopRow = 32.dp

// Artwork: cover beside name with controls up, small faint watermark without them
private val ArtSize = 48.dp
private const val WatermarkScale = 28f / 48f
private const val WatermarkAlpha = 0.6f

// Play buttons
private val PlaySize = 64.dp
private val SkipSize = 44.dp
private val TransportGap = 52.dp

// Seek bar, touch strip, and chevron
private val SeekBottom = 14.dp
private val SeekTouch = 28.dp
private val SeekThickness = 5.dp
private val ChevronSize = 30.dp

// Volume line at left edge
private val VolumeLength = 100.dp
private val VolumeTouch = 40.dp

@Composable
fun PressScaleActionButton(
    icon: Int,
    tint: Color,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "pressScaleActionButton",
    )

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(containerColor),
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(tint),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberControlsVisible(controls: () -> Float) =
    remember { derivedStateOf { controls() > 0.01f } }

@Composable
internal fun BoxScope.VideoTopBar(
    metadata: MediaMetadata,
    artworkUrl: String?,
    frame: VideoFrame,
    state: VideoModeState,
    controls: () -> Float,
    wholeVideo: Boolean,
    onToggleWholeVideo: () -> Unit,
    onClose: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSongInfo: () -> Unit,
) {
    val visible by rememberControlsVisible(controls)
    val context = LocalContext.current
    val pipSupported = remember(context) { isPipSupported(context) }
    AsyncImage(
        model = artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = EdgeStart, top = TopRow)
            .size(ArtSize)
            .graphicsLayer {
                val c = controls()
                val scale = WatermarkScale + (1f - WatermarkScale) * c
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = WatermarkAlpha + (1f - WatermarkAlpha) * c
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.12f)),
    )
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = EdgeStart + ArtSize + 14.dp, top = TopRow + 3.dp)
            .widthIn(max = (frame.width - EdgeStart - ArtSize - EdgeEnd - 320.dp).coerceAtLeast(80.dp))
            .graphicsLayer {
                val c = controls()
                alpha = c
                translationX = -(1f - c) * 12.dp.toPx()
            }
            .then(
                if (visible) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            state.touched()
                            if (state.controlsShown) onSongInfo() else state.toggleControls()
                        },
                    )
                } else Modifier,
            ),
    ) {
        Text(
            text = metadata.title,
            color = Color.White,
            
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metadata.artists.joinToString { it.name },
            color = Color.White.copy(alpha = 0.72f),
            
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (visible) {
        val closeLabel = stringResource(R.string.video_mode_close_player)
        val buttonSize = 34.dp
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = EdgeEnd, top = TopRow + 7.dp)
                .graphicsLayer { alpha = controls() },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Options Pill container
            Row(
                modifier = Modifier
                    .height(buttonSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Playback speed
                PressScaleActionButton(
                    icon = R.drawable.speed,
                    tint = Color.White,
                    containerColor = Color.Transparent,
                    size = buttonSize,
                    onClick = {
                        state.touched()
                        if (state.controlsShown) onOpenSpeed() else state.toggleControls()
                    },
                    modifier = Modifier.semantics { contentDescription = "Playback speed" },
                )

                // PiP mode
                if (pipSupported) {
                    val pipLabel = stringResource(R.string.pip)
                    PressScaleActionButton(
                        icon = R.drawable.picture_in_picture,
                        tint = Color.White,
                        containerColor = Color.Transparent,
                        size = buttonSize,
                        onClick = {
                            state.touched()
                            if (state.controlsShown) onEnterPip() else state.toggleControls()
                        },
                        modifier = Modifier.semantics { contentDescription = pipLabel },
                    )
                }

                // Lyrics
                val lyricsLabel = stringResource(R.string.lyrics)
                PressScaleActionButton(
                    icon = R.drawable.lyrics,
                    tint = if (state.paneOpen && state.paneContent == VideoPane.LYRICS) Color(0xFF64B5F6) else Color.White,
                    containerColor = Color.Transparent,
                    size = buttonSize,
                    onClick = {
                        state.touched()
                        if (state.controlsShown) onOpenLyrics() else state.toggleControls()
                    },
                    modifier = Modifier.semantics { contentDescription = lyricsLabel },
                )

                // Queue
                val queueLabel = stringResource(R.string.queue)
                PressScaleActionButton(
                    icon = R.drawable.queue_music,
                    tint = if (state.paneOpen && state.paneContent == VideoPane.QUEUE) Color(0xFF64B5F6) else Color.White,
                    containerColor = Color.Transparent,
                    size = buttonSize,
                    onClick = {
                        state.touched()
                        if (state.controlsShown) onOpenQueue() else state.toggleControls()
                    },
                    modifier = Modifier.semantics { contentDescription = queueLabel },
                )

                // Show the whole video (Fit / Crop toggle)
                val wholeVideoLabel = stringResource(R.string.video_mode_whole_video)
                PressScaleActionButton(
                    icon = if (wholeVideo) R.drawable.fullscreen_exit else R.drawable.fullscreen,
                    tint = if (wholeVideo) Color(0xFF81C784) else Color.White,
                    containerColor = Color.Transparent,
                    size = buttonSize,
                    onClick = {
                        state.touched()
                        if (state.controlsShown) onToggleWholeVideo() else state.toggleControls()
                    },
                    modifier = Modifier.semantics { contentDescription = wholeVideoLabel },
                )

                // Video Quality
                val qualityLabel = stringResource(R.string.video_quality)
                PressScaleActionButton(
                    icon = R.drawable.tune,
                    tint = Color.White,
                    containerColor = Color.Transparent,
                    size = buttonSize,
                    onClick = {
                        state.touched()
                        if (state.controlsShown) onOpenQuality() else state.toggleControls()
                    },
                    modifier = Modifier.semantics { contentDescription = qualityLabel },
                )

                // Sleep Timer
                val sleepLabel = stringResource(R.string.sleep_timer)
                PressScaleActionButton(
                    icon = R.drawable.sleep_timer,
                    tint = Color.White,
                    containerColor = Color.Transparent,
                    size = buttonSize,
                    onClick = {
                        state.touched()
                        if (state.controlsShown) onOpenSleepTimer() else state.toggleControls()
                    },
                    modifier = Modifier.semantics { contentDescription = sleepLabel },
                )

                // Song Info
                val infoLabel = stringResource(R.string.video_mode_song_info)
                PressScaleActionButton(
                    icon = R.drawable.info,
                    tint = Color.White,
                    containerColor = Color.Transparent,
                    size = buttonSize,
                    onClick = {
                        state.touched()
                        if (state.controlsShown) onSongInfo() else state.toggleControls()
                    },
                    modifier = Modifier.semantics { contentDescription = infoLabel },
                )
            }

            // Close button (circular action button)
            PressScaleActionButton(
                icon = R.drawable.close,
                tint = Color.White,
                containerColor = Color.White.copy(alpha = 0.22f),
                size = buttonSize,
                onClick = {
                    state.touched()
                    if (state.controlsShown) onClose() else state.toggleControls()
                },
                modifier = Modifier.semantics { contentDescription = closeLabel },
            )
        }
    }
}

// Transport buttons
@Composable
internal fun BoxScope.VideoTransport(
    state: VideoModeState,
    controls: () -> Float,
    isPlaying: Boolean,
) {
    val visible by rememberControlsVisible(controls)
    if (!visible) return
    val connection = LocalPlayerConnection.current ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(TransportGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                val c = controls()
                alpha = c
                val scale = 0.92f + 0.08f * c
                scaleX = scale
                scaleY = scale
            },
    ) {
        TransportButton(
            label = stringResource(R.string.previous),
            size = SkipSize,
            background = null,
            glyph = {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(GlyphSize),
                )
            },
        ) {
            state.touched()
            connection.player.seekToPrevious()
        }
        TransportButton(
            label = stringResource(if (isPlaying) R.string.pause else R.string.play),
            size = PlaySize,
            background = Color.Black.copy(alpha = 0.3f),
            glyph = {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(GlyphSize),
                )
            },
        ) {
            state.touched()
            if (isPlaying) connection.player.pause() else connection.player.play()
        }
        TransportButton(
            label = stringResource(R.string.next),
            size = SkipSize,
            background = null,
            glyph = {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(GlyphSize),
                )
            },
        ) {
            state.touched()
            connection.player.seekToNext()
        }
    }
}

private val GlyphSize = 29.dp

@Composable
private fun TransportButton(
    label: String,
    size: Dp,
    background: Color?,
    glyph: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val scale = if (pressed) 0.88f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .then(if (background != null) Modifier.clip(CircleShape).background(background) else Modifier),
        ) {
            glyph()
        }
    }
}

// Left volume drag bar
internal class MusicVolume(private val audio: android.media.AudioManager) {
    private val steps = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var level by mutableFloatStateOf(read())
    private var dragging by mutableStateOf<Float?>(null)
    private var sentStep = -1

    val shown: Float get() = dragging ?: level
    val isDragging: Boolean get() = dragging != null

    fun read(): Float = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / steps

    fun refresh() {
        level = read()
    }

    fun drag(fraction: Float) {
        dragging = fraction
        val step = (fraction * steps).roundToInt()
        if (step == sentStep) return
        sentStep = step
        runCatching { audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, step, 0) }
    }

    fun release() {
        dragging = null
        sentStep = -1
        refresh()
    }
}

@Composable
internal fun rememberMusicVolume(listening: Boolean): MusicVolume {
    val context = LocalContext.current
    val volume = remember(context) {
        MusicVolume(context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager)
    }
    DisposableEffect(volume, listening) {
        if (!listening) return@DisposableEffect onDispose { }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val stream = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (stream == android.media.AudioManager.STREAM_MUSIC) volume.refresh()
            }
        }
        volume.refresh()
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return volume
}

@Composable
internal fun BoxScope.VideoVolumeBar(state: VideoModeState, controls: () -> Float) {
    val visible by rememberControlsVisible(controls)
    if (!visible) return
    val volume = rememberMusicVolume(listening = true)
    var held by remember { mutableStateOf(false) }
    val label = stringResource(R.string.video_mode_volume)
    val density = LocalDensity.current
    val cutout = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding() }
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = (cutout - 6.dp).coerceAtLeast(2.dp))
            .size(VolumeTouch, VolumeLength + 24.dp)
            .semantics { contentDescription = label }
            .graphicsLayer { alpha = controls() }
            .pointerInput(volume) {
                val inset = 12.dp.toPx()
                fun level(y: Float) = (1f - (y - inset) / (size.height - inset * 2f)).coerceIn(0f, 1f)
                detectVerticalDragGestures(
                    onDragStart = {
                        held = true
                        state.touched()
                        volume.drag(level(it.y))
                    },
                    onDragEnd = {
                        held = false
                        volume.release()
                    },
                    onDragCancel = {
                        held = false
                        volume.release()
                    },
                ) { change, _ ->
                    change.consume()
                    state.touched()
                    volume.drag(level(change.position.y))
                }
            }
            .pointerInput(volume) {
                val inset = 12.dp.toPx()
                detectTapGestures { offset ->
                    state.touched()
                    volume.drag((1f - (offset.y - inset) / (size.height - inset * 2f)).coerceIn(0f, 1f))
                    volume.release()
                }
            }
            .drawBehind {
                val thickness = (if (held) 7.dp else 4.dp).toPx()
                val length = VolumeLength.toPx()
                val left = (size.width - thickness) / 2f
                val top = (size.height - length) / 2f
                val radius = CornerRadius(thickness / 2f)
                drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(left, top), Size(thickness, length), radius)
                val filled = length * volume.shown.coerceIn(0f, 1f)
                drawRoundRect(Color.White, Offset(left, top + length - filled), Size(thickness, filled), radius)
            },
    )
}

// Seek bar with times
@Composable
internal fun BoxScope.VideoSeekRow(
    state: VideoModeState,
    controls: () -> Float,
    positionProvider: () -> Long,
) {
    val visible by rememberControlsVisible(controls)
    if (!visible) return
    val connection = LocalPlayerConnection.current ?: return
    val player = connection.player
    val durationMs = player.duration.takeIf { it > 0L } ?: 1L
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    // Moves with audio position
    val fill = remember { mutableFloatStateOf(0f) }
    var currentPos by remember { mutableStateOf(0L) }
    LaunchedEffect(connection, durationMs) {
        while (true) {
            withFrameNanos { }
            val pos = positionProvider()
            currentPos = pos
            fill.floatValue = (pos.toFloat() / durationMs).coerceIn(0f, 1f)
            if (!player.isPlaying) delay(200)
        }
    }
    val shownMs = dragFraction?.let { (it * durationMs).toLong() } ?: currentPos

    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(start = EdgeStart, end = EdgeEnd, bottom = SeekBottom)
            .graphicsLayer { alpha = controls() },
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TimeText(makeTimeString(shownMs))
            Spacer(modifier = Modifier.weight(1f))
            TimeText(makeTimeString(durationMs))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SeekTouch)
                .pointerInput(durationMs) {
                    fun fraction(x: Float): Float = (x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    detectHorizontalDragGestures(
                        onDragStart = {
                            state.touched()
                            dragFraction = fraction(it.x)
                        },
                        onDragEnd = {
                            dragFraction?.let { connection.player.seekTo((it * durationMs).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        change.consume()
                        state.touched()
                        dragFraction = fraction(change.position.x)
                    }
                }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        state.touched()
                        connection.player.seekTo(((offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                }
                .drawBehind {
                    val thickness = (if (dragFraction != null) 7.dp else SeekThickness).toPx()
                    val top = (size.height - thickness) / 2f
                    val radius = CornerRadius(thickness / 2f)
                    drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(0f, top), Size(size.width, thickness), radius)
                    val played = dragFraction ?: fill.floatValue
                    drawRoundRect(Color.White, Offset(0f, top), Size(size.width * played, thickness), radius)
                },
        )
    }
}

@Composable
private fun TimeText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

// Double-tap seek flash
private const val SeekFlashHoldMs = 420L

@Composable
internal fun BoxScope.VideoSeekFlash(frame: VideoFrame, state: VideoModeState) {
    val flash = state.seekFlash
    val shown = remember { arrayOfNulls<SeekFlash>(1) }
    if (flash != null) shown[0] = flash
    val last = shown[0] ?: return
    val glow = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (flash == null) return@LaunchedEffect
        glow.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        delay(SeekFlashHoldMs)
        glow.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        if (state.seekFlash === flash) state.seekFlash = null
    }
    val paneWidth = with(LocalDensity.current) { frame.paneWidth.toPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(if (last.forward) Alignment.CenterEnd else Alignment.CenterStart)
            .fillMaxHeight()
            .width(frame.width * 0.32f)
            .graphicsLayer {
                alpha = glow.value
                if (last.forward) translationX = -paneWidth * state.pane.value
            }
            .drawBehind {
                val radius = size.height * 0.95f
                val x = if (last.forward) size.width + radius * 0.35f else -radius * 0.35f
                drawCircle(Color.White.copy(alpha = 0.14f), radius = radius, center = Offset(x, size.height / 2f))
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.fast_forward),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer { if (!last.forward) scaleX = -1f },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.video_mode_seek_seconds, last.seconds, last.seconds),
                color = Color.White,
                
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

