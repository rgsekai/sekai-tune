package moe.rgsekai.sekaitune.floating

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.ui.player.KaraokeHead
import moe.rgsekai.sekaitune.ui.player.LyricsAlignment
import moe.rgsekai.sekaitune.ui.player.WordWaitDrop
import moe.rgsekai.sekaitune.ui.player.advance
import moe.rgsekai.sekaitune.ui.player.isRtlLine
import moe.rgsekai.sekaitune.ui.player.karaokeHeadFor
import moe.rgsekai.sekaitune.ui.player.karaokeTimedRanges
import moe.rgsekai.sekaitune.ui.player.rememberWordMotionModifier
import moe.rgsekai.sekaitune.ui.player.sweepHeadX
import moe.rgsekai.sekaitune.ui.player.withLetterMotionFeatures
import moe.rgsekai.sekaitune.ui.theme.LyricsFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

private val SlotInset = 14.dp
private val DotRadius = 3.dp
private val DotGap = 5.dp
private const val PillMotionScale = 0.62f
private const val SweepKeep = 0.62f
private const val SlideFollowSec = 0.28f
private const val SweepSoftEm = 0.55f
private val SweepLead = 2.dp
private const val FrameLeadMs = 48L
private const val MinStepMs = 16L

private val LeadSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 260f)
private val TrailSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 150f)
private const val EdgeFadeSlots = 0.7f
private const val NextScale = 0.84f
private val NextSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 280f)

private enum class RowLook { SUNG, SOFT, WAITING }

@Immutable
internal class PreparedScript(val script: PillScript, val layouts: List<TextLayoutResult>)

private fun rowStyle(font: FontFamily, small: Boolean) = TextStyle(
    fontFamily = font,
    fontWeight = if (small) FontWeight.SemiBold else FontWeight.Bold,
    fontSize = if (small) 13.sp else 15.sp,
    lineHeight = if (small) 17.sp else 19.sp,
    letterSpacing = (-0.03).em,
).withLetterMotionFeatures(true)

private suspend fun prepare(script: PillScript, measurer: TextMeasurer, font: FontFamily): PreparedScript =
    withContext(Dispatchers.Default) {
        val big = rowStyle(font, small = false)
        val small = rowStyle(font, small = true)
        val layouts = script.rows.map { row ->
            measurer.measure(
                text = row.text,
                style = if (row.small) small else big,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1,
            )
        }
        PreparedScript(script, layouts)
    }

@Composable
internal fun PillLyrics(state: PillState, width: Dp, modifier: Modifier = Modifier) {
    val song = state.song
    val script = state.script
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val font = LyricsFontFamily
    val prepared by produceState<PreparedScript?>(null, script, song?.id, font) {
        val shown = script ?: PillScript.info(song?.title.orEmpty(), song?.artists?.joinToString { it.name }.orEmpty())
        value = prepare(shown, TextMeasurer(resolver, density, direction, 0), font)
    }
    Crossfade(targetState = prepared, animationSpec = tween(320), modifier = modifier, label = "pillLyrics") { ready ->
        if (ready != null) PillRows(ready, state, width, appRtl = direction == LayoutDirection.Rtl)
    }
}

@Immutable
private class RowGeometry(
    val slotLeft: Float,
    val slotWidth: Float,
    val topPad: Float,
    val pitch: Float,
    val slots: Int,
    val appRtl: Boolean,
) {
    fun centerY(pos: Float): Float = topPad + (pos + 0.5f) * pitch

    fun alignedX(side: LyricsAlignment, width: Float): Float {
        val room = slotWidth - width
        return when (side) {
            LyricsAlignment.START -> if (appRtl) room else 0f
            LyricsAlignment.END -> if (appRtl) 0f else room
            LyricsAlignment.CENTER -> room / 2f
        }
    }

    fun alpha(pos: Float): Float {
        val last = (slots - 1).toFloat()
        return when {
            pos < 0f -> (1f + pos / EdgeFadeSlots).coerceIn(0f, 1f)
            pos > last -> (1f - (pos - last) / EdgeFadeSlots).coerceIn(0f, 1f)
            else -> 1f
        }
    }
}

@Composable
private fun PillRows(prepared: PreparedScript, state: PillState, width: Dp, appRtl: Boolean) {
    val script = prepared.script
    var top by remember(script) { mutableIntStateOf(script.stops[script.stopAt(state.lyricsClock())].top) }
    var dotsLit by remember(script) { mutableIntStateOf(-1) }

    LaunchedEffect(script, state.playing, state.seekTick) {
        while (true) {
            val position = state.lyricsClock()
            val stop = script.stopAt(position)
            top = script.stops[stop].top
            dotsLit = script.dotsLit(stop, position)
            if (!state.playing) break
            val next = script.nextChangeMs(stop, position)
            if (next == Long.MAX_VALUE) break
            delay(((next - position) / state.speed()).toLong().coerceAtLeast(MinStepMs))
        }
    }

    val lead = remember(script) { Animatable(top.toFloat()) }
    val trail = remember(script) { Animatable(top.toFloat()) }
    LaunchedEffect(script) {
        snapshotFlow { top }.collectLatest { target ->
            val goal = target.toFloat()
            if (abs(goal - lead.value) > 2.5f) {
                val near = goal - (goal - lead.value).sign * 1.5f
                lead.snapTo(near)
                trail.snapTo(near)
            }
            coroutineScope {
                launch { lead.animateTo(goal, LeadSpring) }
                launch { trail.animateTo(goal, TrailSpring) }
            }
        }
    }

    val density = LocalDensity.current
    val tint = LocalPillTint.current
    val geometry = remember(density, width, script.slots, appRtl) {
        with(density) {
            val inset = SlotInset.toPx()
            val pitch = PillMetrics.RowPitch.toPx()
            RowGeometry(
                slotLeft = inset,
                slotWidth = (width.toPx() - inset * 2f).coerceAtLeast(0f),
                topPad = (PillMetrics.BubbleHeight.toPx() - pitch * BaseSlots) / 2f,
                pitch = pitch,
                slots = script.slots,
                appRtl = appRtl,
            )
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .drawWithCache {
                val fog = geometry.slotLeft
                val bubble = tint.bubble
                val left = Brush.horizontalGradient(0f to bubble, 1f to bubble.copy(alpha = 0f), startX = 0f, endX = fog)
                val right = Brush.horizontalGradient(
                    0f to bubble.copy(alpha = 0f),
                    1f to bubble,
                    startX = size.width - fog,
                    endX = size.width,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(left, size = Size(fog, size.height))
                    drawRect(right, topLeft = Offset(size.width - fog, 0f), size = Size(fog, size.height))
                }
            },
    ) {
        val first = (top - 1).coerceAtLeast(0)
        val last = (top + script.slots).coerceAtMost(script.rows.lastIndex)
        val currentLine = script.rows[top].line
        for (index in first..last) {
            key(index) {
                val row = script.rows[index]
                val showsDots = row.kind == PillRowKind.INTRO ||
                    (row.hostsBreak && (index < top || (index == top && dotsLit >= 0)))
                val inLine = index >= top && index - top < script.slots && row.line == currentLine
                val look = when {
                    index > top && !inLine -> RowLook.WAITING
                    row.sweeps || row.kind == PillRowKind.LEAD || row.kind == PillRowKind.TITLE -> RowLook.SUNG
                    else -> RowLook.SOFT
                }
                val weight = ((index - top + 1) / 2f).coerceIn(0f, 1f)
                PillLyricRow(
                    row = row,
                    layout = prepared.layouts[index],
                    position = { index - (lead.value + (trail.value - lead.value) * weight) },
                    live = row.sweeps && !showsDots && inLine,
                    look = look,
                    showsDots = showsDots,
                    dotsLit = if (index < top) PillDots else dotsLit,
                    geometry = geometry,
                    tint = tint,
                    state = state,
                )
            }
        }
    }
}

@Composable
private fun PillLyricRow(
    row: PillRow,
    layout: TextLayoutResult,
    position: () -> Float,
    live: Boolean,
    look: RowLook,
    showsDots: Boolean,
    dotsLit: Int,
    geometry: RowGeometry,
    tint: PillTint,
    state: PillState,
) {
    val slide = remember(row) { mutableFloatStateOf(0f) }
    val dots = animateFloatAsState(if (showsDots) 1f else 0f, tween(320), label = "dots")
    val lit = animateFloatAsState(dotsLit.coerceAtLeast(0).toFloat(), tween(420), label = "dotsLit")
    val waiting = animateFloatAsState(if (look == RowLook.WAITING) 1f else 0f, tween(300), label = "rowWaiting")
    val rtlText = remember(layout) { row.text.isNotEmpty() && layout.isRtlLine(0) }
    val width = layout.size.width
    val height = layout.size.height
    val next = animateFloatAsState(
        if (look == RowLook.WAITING && (row.kind == PillRowKind.LEAD || row.kind == PillRowKind.TITLE)) 1f else 0f,
        NextSpring,
        label = "rowNext",
    )
    val pivot = remember(layout, geometry) {
        when {
            geometry.slotWidth < width -> if (rtlText) 1f else 0f
            row.side == LyricsAlignment.CENTER -> 0.5f
            (row.side == LyricsAlignment.END) != geometry.appRtl -> 1f
            else -> 0f
        }
    }
    val textX = {
        val room = geometry.slotWidth - width
        when {
            room >= 0f -> geometry.alignedX(row.side, width.toFloat())
            rtlText -> room + slide.floatValue
            else -> -slide.floatValue
        }
    }
    Box(
        Modifier
            .graphicsLayer {
                val pos = position()
                translationX = geometry.slotLeft + textX()
                translationY = geometry.centerY(pos) - height / 2f
                alpha = geometry.alpha(pos)
                val scale = 1f - (1f - NextScale) * next.value
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(pivot, 0.5f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(minOf(width, constraints.maxWidth), minOf(height, constraints.maxHeight)) { placeable.place(0, 0) }
            },
    ) {
        if (live) {
            SweepText(row, layout, state, tint, slide, geometry.slotWidth, rtlText)
        } else {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val d = dots.value
                        if (d < 1f) {
                            drawRestingText(
                                layout,
                                restingColor(tint, look, position(), waiting.value),
                                drop = row.sweeps,
                                waiting = waiting.value,
                                alpha = 1f - d,
                            )
                        }
                        if (d > 0f) {
                            val dotsWidth = (DotRadius.toPx() * 2f) * PillDots + DotGap.toPx() * (PillDots - 1)
                            val x = geometry.alignedX(row.side, dotsWidth) - textX()
                            drawDots(tint, x, lit.value, alpha = d)
                        }
                    },
            )
        }
    }
}

private fun restingColor(tint: PillTint, look: RowLook, pos: Float, waiting: Float): Color {
    val settled = if (look == RowLook.SOFT) tint.soft else lerp(tint.ink, tint.waiting, pos.coerceIn(0f, 1f))
    return lerp(settled, tint.waiting, waiting)
}

private fun DrawScope.drawRestingText(
    layout: TextLayoutResult,
    color: Color,
    drop: Boolean,
    waiting: Float,
    alpha: Float,
) {
    if (drop && waiting > 0f) {
        translate(top = WordWaitDrop.toPx() * PillMotionScale * waiting) {
            drawText(layout, color = color, alpha = alpha)
        }
        return
    }
    drawText(layout, color = color, alpha = alpha)
}

private fun DrawScope.drawDots(tint: PillTint, x: Float, lit: Float, alpha: Float) {
    val radius = DotRadius.toPx()
    val step = radius * 2f + DotGap.toPx()
    for (i in 0 until PillDots) {
        val on = (lit - i).coerceIn(0f, 1f)
        drawCircle(
            color = lerp(tint.waiting, tint.ink, on),
            radius = radius * (0.8f + 0.2f * on),
            center = Offset(x + radius + i * step, size.height / 2f),
            alpha = alpha,
        )
    }
}

@Composable
private fun SweepText(
    row: PillRow,
    layout: TextLayoutResult,
    state: PillState,
    tint: PillTint,
    slide: MutableFloatState,
    slotWidth: Float,
    rtlText: Boolean,
) {
    val head = remember(row) { karaokeHeadFor(row.text) }
    val timed = remember(row) { karaokeTimedRanges(row.text, row.words.orEmpty()) }
    val holder = remember(layout) { arrayOf<TextLayoutResult?>(layout) }
    val overflow = (layout.size.width - slotWidth).coerceAtLeast(0f)
    val playing = state.playing

    LaunchedEffect(head, playing, state.seekTick) {
        val firstWordMs = timed.firstOrNull()?.let { (it.startTime * 1000).toLong() } ?: row.startMs
        var lastFrame = -1L
        while (true) {
            if (playing) {
                val wait = firstWordMs - state.lyricsClock() - FrameLeadMs
                if (wait > 0L) {
                    delay((wait / state.speed()).toLong().coerceAtLeast(1L))
                    lastFrame = -1L
                    continue
                }
            }
            val moving = withFrameNanos { now ->
                val dt = if (lastFrame < 0L) 0.016f else ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.064f)
                lastFrame = now
                val position = state.lyricsClock()
                val moved = head.advance(timed, position / 1000.0, dt, wordMotion = true)
                val slid = overflow > 0f && slide.follow(slideTarget(head, layout, row.text.length, slotWidth, overflow, rtlText), dt)
                moved || slid || (playing && position < row.endMs)
            }
            if (!moving) break
        }
    }

    val motion = rememberWordMotionModifier(
        layoutHolder = holder,
        head = head,
        riseAmplitudeProvider = { 1f },
        fakeGlowProvider = { 0f },
        motionScale = PillMotionScale,
    )
    val brush = remember(layout) { SweepBrush() }
    Spacer(
        Modifier
            .fillMaxSize()
            .then(motion)
            .drawBehind {
                val h = head.offset.floatValue
                val length = row.text.length
                when {
                    h <= 0f -> drawText(layout, color = tint.waiting)
                    h >= length -> drawText(layout, color = tint.ink)
                    else -> {
                        val x = layout.sweepHeadX(h, h.toInt().coerceIn(0, length), 0, length, rtlText)
                        val soft = layout.layoutInput.style.fontSize.toPx() * SweepSoftEm
                        drawText(
                            layout,
                            brush = brush.at(x, soft, SweepLead.toPx(), rtlText, tint.ink, tint.waiting),
                            alpha = 1f,
                        )
                    }
                }
            },
    )
}

private fun slideTarget(
    head: KaraokeHead,
    layout: TextLayoutResult,
    length: Int,
    slotWidth: Float,
    overflow: Float,
    rtl: Boolean,
): Float {
    val h = head.offset.floatValue
    if (h <= 0f || length == 0) return 0f
    val x = layout.sweepHeadX(h, h.toInt().coerceIn(0, length), 0, length, rtl)
    val read = if (rtl) layout.size.width - x else x
    return (read - slotWidth * SweepKeep).coerceIn(0f, overflow)
}

private fun MutableFloatState.follow(target: Float, dt: Float): Boolean {
    val now = floatValue
    if (abs(target - now) < 0.5f) {
        if (now != target) floatValue = target
        return false
    }
    floatValue = now + (target - now) * (1f - exp(-dt / SlideFollowSec))
    return true
}

private class SweepBrush {
    private var headX = Float.NaN
    private var rtl = false
    private var sung = Color.Unspecified
    private var waiting = Color.Unspecified
    private var brush: Brush = Brush.horizontalGradient(listOf(Color.Black, Color.Gray))

    fun at(x: Float, soft: Float, lead: Float, rtl: Boolean, sung: Color, waiting: Color): Brush {
        if (x == headX && rtl == this.rtl && sung == this.sung && waiting == this.waiting) return brush
        headX = x
        this.rtl = rtl
        this.sung = sung
        this.waiting = waiting
        val flow = if (rtl) -1f else 1f
        brush = Brush.horizontalGradient(
            0f to sung,
            1f to waiting,
            startX = x - soft * flow,
            endX = x + lead * flow,
        )
        return brush
    }
}

