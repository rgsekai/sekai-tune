package moe.rgsekai.sekaitune.ui.player.poster

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.ui.theme.LyricsFontFamily
import moe.rgsekai.sekaitune.utils.makeTimeString
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A circle in the ring's own pixels: the cover's, which the ring runs round. */
@Immutable
data class RingCircle(val center: Offset, val radius: Float)

/**
 * The ring round a round cover: what the seek bar on it is doing ([expand]: 0 a thin line on the
 * cover's edge, 1 the full bar just outside it with the times at its ends), and the time a held
 * bar has been dragged to. Shared with whatever the bar pushes aside (the Poster's lyrics).
 */
@Stable
class CoverRingState internal constructor(private val scope: CoroutineScope) {
    val expand = Animatable(0f)
    var scrubMs by mutableLongStateOf(-1L)
    internal var held = false
    private var job: Job? = null

    /** grows the bar, and lets it settle back after [forMs] unless it's held by then */
    fun peek(forMs: Long) {
        job?.cancel()
        job = scope.launch {
            expand.animateTo(1f, ExpandSpring)
            delay(forMs)
            if (!held) expand.animateTo(0f, CollapseSpring)
        }
    }

    internal fun grab() {
        job?.cancel()
        held = true
        job = scope.launch { expand.animateTo(1f, ExpandSpring) }
    }

    internal fun letGo(afterMs: Long) {
        held = false
        peek(afterMs)
    }
}

@Composable
fun rememberCoverRingState(): CoverRingState {
    val scope = rememberCoroutineScope()
    return remember(scope) { CoverRingState(scope) }
}

private val ExpandSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 340f)
private val CollapseSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 170f)

/** how long the bar stays out when playback starts */
private const val StartPeekMs = 3_000L
/** and after a touch lets go */
private const val ReleasePeekMs = 1_400L

/** Which part of the cover's rim is on screen: its bottom (upright) or its right side (sideways). */
enum class RingSide { Bottom, Right }

/**
 * The seek bar round [circle], over the lower edge of a cover that runs off the
 * screen's sides (the Poster's). The bar lies thin on the cover's rim; touched, it moves out past
 * the rim and thickens, the elapsed time and the song's length riding its ends - drag along it to scrub,
 * let go to play from there. It also comes out on its own for a few seconds when the music starts.
 */
@Composable
fun CoverRing(
    state: CoverRingState,
    circle: RingCircle,
    durationMs: Long,
    positionMsProvider: () -> Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    trackId: String?,
    modifier: Modifier = Modifier,
    side: RingSide = RingSide.Bottom,
    interactive: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val labelStyle = remember {
        TextStyle(
            fontFamily = LyricsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = Color.White,
        )
    }
    val currentCircle by rememberUpdatedState(circle)
    val currentDuration by rememberUpdatedState(durationMs)
    val currentOnSeek by rememberUpdatedState(onSeek)

    // Peeks for a few seconds whenever playback starts or a new track begins
    LaunchedEffect(trackId, isPlaying) {
        if (isPlaying && !state.held) {
            state.peek(StartPeekMs)
        }
    }

    Spacer(
        modifier = modifier
            .pointerInput(interactive, currentDuration) {
                if (!interactive || currentDuration <= 0L) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                    val c = currentCircle
                    val geometry = RingGeometry(c, Size(size.width.toFloat(), size.height.toFloat()), density, side)
                    if (!geometry.onBand(down.position)) return@awaitEachGesture
                    val dur = currentDuration.takeIf { it > 0 } ?: return@awaitEachGesture
                    state.grab()
                    var travel = Offset.Zero
                    var scrubbing = false
                    var lastTick = -1L
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (!scrubbing) {
                            travel += change.positionChange()
                            if (travel.getDistance() < slop) continue
                            val along = geometry.alongness(change.position, travel)
                            if (along < 0.55f) {
                                state.letGo(250L)
                                return@awaitEachGesture
                            }
                            scrubbing = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        change.consume()
                        val ms = (dur * geometry.fractionAt(change.position)).toLong()
                        state.scrubMs = ms
                        val tick = ms / 10_000L
                        if (tick != lastTick) {
                            if (lastTick >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastTick = tick
                        }
                    }
                    if (scrubbing && state.scrubMs >= 0L) {
                        currentOnSeek(state.scrubMs.coerceIn(0L, dur))
                    }
                    state.letGo(ReleasePeekMs)
                    scope.launch {
                        delay(400)
                        if (!state.held) state.scrubMs = -1L
                    }
                }
            }
            .drawBehind {
                val c = currentCircle
                val geometry = RingGeometry(c, size, density, side)
                val e = state.expand.value
                val dur = currentDuration.takeIf { it > 0 } ?: 0L
                val at = when {
                    state.scrubMs >= 0L -> state.scrubMs
                    else -> positionMsProvider()
                }
                drawSeekArc(
                    geometry = geometry,
                    e = e,
                    fraction = if (dur > 0) (at.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    positionMs = at,
                    durationMs = dur,
                    measurer = measurer,
                    labelStyle = labelStyle,
                )
            },
    )
}

/**
 * Where everything on the ring sits.
 */
internal class RingGeometry(val circle: RingCircle, val size: Size, density: Float, val side: RingSide) {
    private val dp = density
    val center get() = circle.center
    val radius get() = circle.radius

    fun barRadius(e: Float) = radius + lerp(-15f, 22f, e) * dp

    fun angleAtX(x: Float, r: Float): Float {
        val dx = (x - center.x).coerceIn(-r * 0.999f, r * 0.999f)
        return Math.toDegrees(atan2(sqrt((r * r - dx * dx).coerceAtLeast(0f)), dx).toDouble()).toFloat()
    }

    fun angleAtY(y: Float, r: Float): Float {
        val dy = (y - center.y).coerceIn(-r * 0.999f, r * 0.999f)
        return Math.toDegrees(atan2(dy, sqrt((r * r - dy * dy).coerceAtLeast(0f))).toDouble()).toFloat()
    }

    fun span(r: Float, from: Float, to: Float): Pair<Float, Float> = when (side) {
        RingSide.Bottom -> angleAtX(from, r) to angleAtX(size.width - to, r)
        RingSide.Right -> angleAtY(from, r) to angleAtY(size.height - to, r)
    }

    fun barEnds(e: Float): Pair<Float, Float> =
        span(barRadius(e), lerp(26f, 64f, e) * dp, lerp(26f, 52f, e) * dp)

    fun onBand(p: Offset): Boolean {
        val d = hypot(p.x - center.x, p.y - center.y)
        if (d < radius - 46f * dp || d > radius + 50f * dp) return false
        val (start, end) = barEnds(0f)
        val a = angleOf(p)
        return a in (minOf(start, end) - 4f)..(maxOf(start, end) + 4f)
    }

    fun angleOf(p: Offset) = Math.toDegrees(atan2((p.y - center.y).toDouble(), (p.x - center.x).toDouble())).toFloat()

    fun alongness(p: Offset, move: Offset): Float {
        val rx = p.x - center.x
        val ry = p.y - center.y
        val rl = hypot(rx, ry).coerceAtLeast(1f)
        val ml = move.getDistance().coerceAtLeast(1f)
        val across = abs((move.x * rx + move.y * ry) / (rl * ml))
        return sqrt((1f - across * across).coerceAtLeast(0f))
    }

    fun fractionAt(p: Offset): Float {
        val (start, end) = barEnds(1f)
        return ((angleOf(p) - start) / (end - start)).coerceIn(0f, 1f)
    }
}

private fun DrawScope.drawSeekArc(
    geometry: RingGeometry,
    e: Float,
    fraction: Float,
    positionMs: Long,
    durationMs: Long,
    measurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val r = geometry.barRadius(e)
    val (start, end) = geometry.barEnds(e)
    val sweep = end - start
    val stroke = lerp(3f, 7f, e).dp.toPx()
    val topLeft = geometry.center - Offset(r, r)
    val arcSize = Size(r * 2f, r * 2f)
    val ink = lerp(0.3f, 1f, e)

    // Unplayed background track
    drawArc(
        color = Color.White.copy(alpha = 0.38f * ink),
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )

    // Played progress track
    val played = sweep * fraction.coerceIn(0f, 1f)
    drawArc(
        color = Color.White.copy(alpha = 0.96f * ink),
        startAngle = start,
        sweepAngle = if (abs(played) < 0.01f) 0.01f * kotlin.math.sign(sweep) else played,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(stroke, cap = StrokeCap.Round),
    )

    val shown = ((e - 0.25f) / 0.75f).coerceIn(0f, 1f)
    if (shown <= 0.001f || durationMs <= 0L) return
    val toward = kotlin.math.sign(sweep)
    ringLabel(makeTimeString(positionMs), geometry, start, r, outward = -toward, shown, measurer, labelStyle)
    ringLabel(makeTimeString(durationMs), geometry, end, r, outward = toward, shown, measurer, labelStyle)
}

/** A time string positioned just past one end of the bar, turned along it, reading the right way up. */
private fun DrawScope.ringLabel(
    text: String,
    geometry: RingGeometry,
    endDegrees: Float,
    r: Float,
    outward: Float,
    shown: Float,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    val layout = measurer.measure(text, style)
    val gapPx = (layout.size.width / 2f) + (8f + 10f * (1f - shown)).dp.toPx()
    val gapDegrees = Math.toDegrees((gapPx / r).toDouble()).toFloat()
    val degrees = endDegrees + outward * gapDegrees
    val rad = degrees * (PI.toFloat() / 180f)
    val at = geometry.center + Offset(cos(rad), sin(rad)) * r
    val s = 0.86f + 0.14f * shown
    var turn = degrees - 90f
    while (turn > 90f) turn -= 180f
    while (turn < -90f) turn += 180f
    rotate(turn, at) {
        scale(s, s, at) {
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f), alpha = shown)
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

