package moe.rgsekai.sekaitune.floating

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.theme.LyricsFontFamily
import moe.rgsekai.sekaitune.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val ButtonCorner = 16.dp
private val ButtonShadowBlur = 12.dp
private val ButtonShadowDrop = 4.dp
private const val ButtonShadowAlpha = 0.18f
private val IconSize = 26.dp

private val RingStroke = 2.5.dp
private val RingInset = 5.dp
private val RingTrack = Color.Black.copy(alpha = 0.2f)
private val BarTrack = Color.White.copy(alpha = 0.42f)
private val TimesTop = 10.dp

private val BarShade = Color.Black.copy(alpha = 0.22f)
private val BarShadeWidth = 3.dp
private val TimesShade = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), blurRadius = 5f)

private val GrabWidth = 3.dp
private val KnobRadius = 7.dp

private const val MorphSteps = 64
private const val MorphStagger = 0.45f

private val PlaySpring = spring<Float>(dampingRatio = 0.55f, stiffness = 520f)
private val GrabSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 600f)

internal const val NoScrub = -1f

@Composable
internal fun SongProgressTicker(
    state: PillState,
    progress: MutableFloatState,
    elapsed: MutableLongState,
    timesShown: Boolean,
) {
    val ringLength = with(LocalDensity.current) { (PillMetrics.Cover / 2 - RingInset).toPx() * 2f * PI.toFloat() }
    LaunchedEffect(state.playing, state.seekTick, state.song?.id, timesShown) {
        while (true) {
            val duration = state.durationMs()
            val position = state.songPositionMs()
            progress.floatValue = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            elapsed.longValue = position
            if (!state.playing) break
            val ringStep = if (duration > 0L) (duration / ringLength).toLong().coerceIn(120L, 500L) else 250L
            val wait = if (timesShown) minOf(ringStep, 1_000L - position % 1_000L + 8L) else ringStep
            delay((wait / state.speed()).toLong().coerceAtLeast(100L))
        }
    }
}

@Composable
internal fun PlayButton(state: PillState, host: PillHost, end: Boolean, opened: () -> Float) {
    val haptics = LocalHapticFeedback.current
    val tint = LocalPillTint.current
    val playing = animateFloatAsState(if (state.playing) 1f else 0f, PlaySpring, label = "pillPlaying")
    var pressed by remember { mutableStateOf(false) }
    val press = animateFloatAsState(
        if (pressed) 0.82f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "pillPlayPress",
    )
    Box(
        Modifier
            .fromCoverSide(end, { (PillMetrics.Margin + PillMetrics.Cover / 2).toPx() }, { PillMetrics.Margin.toPx() })
            .layout { measurable, _ ->
                val width = (PillMetrics.Cover / 2).roundToPx() + (PillMetrics.ButtonExtra.toPx() * opened()).roundToInt()
                val height = PillMetrics.Cover.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer { alpha = (opened() * 4f).coerceIn(0f, 1f) }
            .softShadow(ButtonCorner, ButtonShadowBlur, { ButtonShadowDrop }, { ButtonShadowAlpha * opened() })
            .drawBehind {
                val corner = ButtonCorner.toPx()
                drawRoundRect(tint.bubble, cornerRadius = CornerRadius(corner, corner))
            },
    )
    val tapToggle = if (state.expanded) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = {
                    state.touchTick++
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    host.togglePlay()
                },
            )
        }
    } else {
        Modifier
    }
    val toCover = if (end) 1f else -1f
    val description = stringResource(if (state.playing) R.string.pause else R.string.play)
    Box(
        modifier = Modifier
            .fromCoverSide(end, { (PillMetrics.Margin + PillMetrics.Cover).toPx() }, { PillMetrics.Margin.toPx() })
            .size(PillMetrics.ButtonExtra, PillMetrics.Cover)
            .then(tapToggle)
            .semantics { contentDescription = description }
            .graphicsLayer {
                val t = opened()
                val shown = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                alpha = shown
                val scale = (0.6f + 0.4f * shown) * press.value
                scaleX = scale
                scaleY = scale
                translationX = toCover * PillMetrics.ButtonExtra.toPx() / 2f * (1f - t)
            },
        contentAlignment = Alignment.Center,
    ) {
        PlayPauseGlyph(R.drawable.pause_applemusic, tint) { playing.value }
        PlayPauseGlyph(R.drawable.play_applemusic, tint) { 1f - playing.value }
    }
}

@Composable
private fun PlayPauseGlyph(icon: Int, tint: PillTint, shown: () -> Float) {
    Image(
        painter = painterResource(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint.ink),
        modifier = Modifier
            .size(IconSize)
            .graphicsLayer {
                val k = shown()
                alpha = k.coerceIn(0f, 1f)
                val scale = 0.5f + 0.5f * k
                scaleX = scale
                scaleY = scale
            },
    )
}

@Composable
internal fun ProgressRing(
    progress: MutableFloatState,
    scrub: MutableFloatState,
    end: Boolean,
    opened: () -> Float,
    body: () -> Float,
    coverScale: () -> Float,
    coverAlpha: () -> Float,
) {
    val morph = remember { RingMorph() }
    val grabbing by remember { derivedStateOf { scrub.floatValue >= 0f } }
    val grab = animateFloatAsState(if (grabbing) 1f else 0f, GrabSpring, label = "pillBarGrab")
    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val t = opened()
                val shown = coverAlpha()
                if (shown <= 0f) return@drawBehind
                val margin = PillMetrics.Margin.toPx()
                val cover = PillMetrics.Cover.toPx()
                val barHeight = PillMetrics.BarHeight.toPx()
                val center = Offset(
                    if (end) size.width - margin - cover / 2f else margin + cover / 2f,
                    margin + cover / 2f,
                )
                val radius = (cover / 2f - RingInset.toPx()) * coverScale()
                val barLeft = margin + barHeight / 2f
                val barRight = size.width - margin - barHeight / 2f
                val barY = PillMetrics.barY(body()).toPx()
                val stroke = RingStroke.toPx() + (barHeight - RingStroke.toPx()) * t
                val track = lerp(RingTrack, BarTrack, t).let { it.copy(alpha = it.alpha * shown) }
                val head = Color.White.copy(alpha = shown)
                val played = scrub.floatValue.takeIf { it >= 0f } ?: progress.floatValue
                val shade = ((t - 0.85f) / 0.15f).coerceIn(0f, 1f)
                if (shade > 0f) {
                    val wide = stroke + GrabWidth.toPx() * grab.value + BarShadeWidth.toPx()
                    drawLine(
                        BarShade.copy(alpha = BarShade.alpha * shade),
                        Offset(barLeft, barY),
                        Offset(barRight, barY),
                        wide,
                        StrokeCap.Round,
                    )
                }
                when {
                    t <= 0.001f -> {
                        drawCircle(track, radius, center, style = Stroke(stroke))
                        drawArc(
                            color = head,
                            startAngle = -90f,
                            sweepAngle = 360f * played,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    t >= 0.999f -> {
                        val thick = stroke + GrabWidth.toPx() * grab.value
                        val headX = barLeft + (barRight - barLeft) * played
                        drawLine(track, Offset(barLeft, barY), Offset(barRight, barY), thick, StrokeCap.Round)
                        drawLine(head, Offset(barLeft, barY), Offset(headX, barY), thick, StrokeCap.Round)
                        if (grab.value > 0.01f) drawCircle(head, KnobRadius.toPx() * grab.value, Offset(headX, barY))
                    }
                    else -> with(morph) {
                        set(center, radius, barLeft, barRight, barY, t)
                        drawMorph(track, head, played, stroke)
                    }
                }
            },
    )
}

private class RingMorph {
    private val trackPath = Path()
    private val playedPath = Path()
    private val point = FloatArray(2)
    private var center = Offset.Zero
    private var radius = 0f
    private var barLeft = 0f
    private var barRight = 0f
    private var barY = 0f
    private var t = 0f

    fun set(center: Offset, radius: Float, barLeft: Float, barRight: Float, barY: Float, t: Float) {
        this.center = center
        this.radius = radius
        this.barLeft = barLeft
        this.barRight = barRight
        this.barY = barY
        this.t = t
    }

    fun DrawScope.drawMorph(track: Color, head: Color, played: Float, stroke: Float) {
        trace(trackPath, 1f)
        trace(playedPath, played)
        val style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(trackPath, track, style = style)
        if (played > 0f) drawPath(playedPath, head, style = style)
    }

    private fun trace(path: Path, upTo: Float) {
        path.reset()
        pointAt(0f)
        path.moveTo(point[0], point[1])
        val last = (upTo * MorphSteps).toInt()
        for (i in 1..last) {
            pointAt(i / MorphSteps.toFloat())
            path.lineTo(point[0], point[1])
        }
        if (upTo * MorphSteps > last) {
            pointAt(upTo)
            path.lineTo(point[0], point[1])
        }
    }

    private fun pointAt(s: Float) {
        val raw = (t * (1f + MorphStagger) - s * MorphStagger).coerceIn(0f, 1f)
        val m = raw * raw * (3f - 2f * raw)
        val angle = (-90f + 360f * s) * (PI.toFloat() / 180f)
        val ringX = center.x + radius * cos(angle)
        val ringY = center.y + radius * sin(angle)
        val lineX = barLeft + (barRight - barLeft) * s
        point[0] = ringX + (lineX - ringX) * m
        point[1] = ringY + (barY - ringY) * m
    }
}

@Composable
internal fun SeekBarTouch(
    state: PillState,
    host: PillHost,
    scrub: MutableFloatState,
    progress: MutableFloatState,
    body: () -> Float,
) {
    if (!state.expanded) return
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .layout { measurable, constraints ->
                val height = (PillMetrics.SeekReach * 2).roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                layout(placeable.width, placeable.height) {
                    placeable.place(0, (PillMetrics.barY(body()) - PillMetrics.SeekReach).roundToPx())
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val left = (PillMetrics.Margin + PillMetrics.BarHeight / 2).toPx()
                    val span = (size.width - left * 2f).coerceAtLeast(1f)
                    val at = { x: Float -> ((x - left) / span).coerceIn(0f, 1f) }
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    state.touchTick++
                    scrub.floatValue = at(down.position.x)
                    down.consume()
                    var letGo = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                letGo = true
                                break
                            }
                            scrub.floatValue = at(change.position.x)
                            change.consume()
                        }
                    } finally {
                        val to = scrub.floatValue
                        val duration = state.durationMs()
                        if (letGo && to >= 0f && duration > 0L) {
                            progress.floatValue = to
                            host.seekTo((to * duration).toLong())
                        }
                        state.touchTick++
                        scrub.floatValue = NoScrub
                    }
                }
            },
    )
}

@Composable
internal fun SongTimes(
    state: PillState,
    elapsed: MutableLongState,
    scrub: MutableFloatState,
    opened: () -> Float,
    body: () -> Float,
) {
    val style = remember {
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.86f),
            shadow = TimesShade,
        )
    }
    val timesY: Density.() -> Int = {
        (PillMetrics.barY(body()) + PillMetrics.BarHeight / 2 + TimesTop).roundToPx()
    }
    val shown: GraphicsLayerScope.() -> Unit = {
        val k = ((opened() - 0.5f) / 0.5f).coerceIn(0f, 1f)
        alpha = k
        translationY = (1f - k) * 6.dp.toPx()
    }
    val duration = state.durationMs()
    val gone = scrub.floatValue.takeIf { it >= 0f }?.let { (it * duration).toLong() } ?: elapsed.longValue
    BasicText(
        text = makeTimeString(gone),
        style = style,
        modifier = Modifier
            .offset { IntOffset(PillMetrics.Margin.roundToPx(), timesY()) }
            .graphicsLayer(shown),
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        BasicText(
            text = makeTimeString(duration),
            style = style,
            modifier = Modifier
                .offset { IntOffset(-PillMetrics.Margin.roundToPx(), timesY()) }
                .graphicsLayer(shown),
        )
    }
}

