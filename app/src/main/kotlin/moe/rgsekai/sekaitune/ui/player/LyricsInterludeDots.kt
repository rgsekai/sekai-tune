package moe.rgsekai.sekaitune.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp
import moe.rgsekai.sekaitune.constants.LyricsMotionStyle
import moe.rgsekai.sekaitune.ui.component.damageBoundary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DotCount = 3

const val MinInterludeGapMs = 4_000L

private val RowHeight = 56.dp

private val DotSize = 12.dp
private val DotGap = 7.dp

private const val RestAlpha = 0.3f

private val GlowRoom = 8.dp

private const val GlowFadeMs = 900f

private const val BobPeriodMs = 2_600f
private val BobHeight = 1.8.dp
private const val BobPhaseStep = 0.9f

private const val PopStaggerMs = 70L

private const val DotFadeFraction = 0.55f

private const val FinaleFraction = 0.88f

internal const val LyricsRecenterDurationMs = 950

internal val LyricsRecenterEasing = CubicBezierEasing(0.5f, 0.45f, 0f, 1f)
internal val LyricsSmoothRecenterEasing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)

internal fun recenterEasingFor(style: LyricsMotionStyle): Easing =
    if (style == LyricsMotionStyle.MOTION) LyricsSmoothRecenterEasing else LyricsRecenterEasing

val LyricsHeaderArtX = 24.dp

@Composable
fun LyricsInterludeDots(
    startMs: Long,
    endMs: Long,
    positionProvider: () -> Long,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    alignment: LyricsAlignment = LyricsAlignment.CENTER,
    collapseEasing: Easing = LyricsRecenterEasing,
    bloom: Boolean = true,
    highBloom: Boolean = true,
    onDotsStart: ((Offset) -> Unit)? = null,
) {
    val rowCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val span = (endMs - startMs).coerceAtLeast(1L)

    val fills = remember { Array(DotCount) { mutableFloatStateOf(0f) } }
    val glows = remember { Array(DotCount) { mutableFloatStateOf(0f) } }
    val finale = remember { mutableFloatStateOf(0f) }
    val clockMs = remember { mutableLongStateOf(0L) }

    val height = remember { Animatable(if (visible) 1f else 0f) }
    val pops = remember { Array(DotCount) { Animatable(if (visible) 1f else 0f) } }

    LaunchedEffect(visible) {
        if (visible) {
            launch {
                height.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
                )
            }
            pops.forEachIndexed { index, pop ->
                launch {
                    delay(index * PopStaggerMs)
                    pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
                }
            }
        } else {
            launch {
                height.animateTo(0f, tween(LyricsRecenterDurationMs, easing = collapseEasing))
            }
            pops.forEach { pop ->
                launch { pop.animateTo(0f, tween(LyricsRecenterDurationMs / 2, easing = collapseEasing)) }
            }
        }
    }

    LaunchedEffect(startMs, endMs, positionProvider, visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            withFrameMillis { frameMs ->
                clockMs.longValue = frameMs
                val progress = ((positionProvider() - startMs).toFloat() / span).coerceIn(0f, 1f)
                finale.floatValue = smoothstepDots((progress - FinaleFraction) / (1f - FinaleFraction))
                for (index in 0 until DotCount) {
                    val sliceStart = index.toFloat() / DotCount
                    val fill = smoothstepDots((progress - sliceStart) * DotCount / DotFadeFraction)
                    fills[index].floatValue = fill
                    val litAt = sliceStart + DotFadeFraction / DotCount
                    glows[index].floatValue = if (progress < litAt) fill else exp(-((progress - litAt) * span) / GlowFadeMs)
                }
            }
        }
    }

    Row(
        modifier = modifier
            .then(if (onDotsStart != null) Modifier.onPlaced { rowCoords[0] = it } else Modifier)
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val full = RowHeight.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = full, maxHeight = full))
                val shown = (full * height.value.coerceIn(0f, 1f)).roundToInt()
                layout(placeable.width, shown) {
                    placeable.place(0, (shown - full) / 2)
                }
            }
            .damageBoundary()
            .height(RowHeight)
            .padding(horizontal = LyricsHeaderArtX)
            .graphicsLayer { alpha = height.value.coerceIn(0f, 1f) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = when (alignment) {
            LyricsAlignment.START -> Arrangement.Start
            LyricsAlignment.CENTER -> Arrangement.Center
            LyricsAlignment.END -> Arrangement.End
        },
    ) {
        Row(
            modifier = Modifier
                .layerBleed(GlowRoom)
                .sweepBloom(highBloom) {
                    if (!bloom) return@sweepBloom null
                    dotsGlow(glows, finale.floatValue, height.value.coerceIn(0f, 1f))
                }
                .padding(GlowRoom),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(DotCount) { index ->
                Spacer(
                    modifier = Modifier
                        .size(DotSize)
                        .then(
                            if (index == 0 && onDotsStart != null) {
                                Modifier.onPlaced { dot ->
                                    val row = rowCoords[0]
                                    if (row != null && row.isAttached) {
                                        val at = row.localPositionOf(dot, Offset.Zero)
                                        onDotsStart(Offset(at.x, at.y + dot.size.height / 2f))
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .graphicsLayer {
                            val pop = pops[index].value
                            scaleX = pop
                            scaleY = pop
                            alpha = (RestAlpha + (1f - RestAlpha) * fills[index].floatValue) * pop.coerceIn(0f, 1f)
                            val phase = clockMs.longValue / BobPeriodMs * 2f * PI.toFloat() - index * BobPhaseStep
                            translationY = sin(phase) * BobHeight.toPx()
                        }
                        .drawBehind {
                            val fill = fills[index].floatValue
                            drawCircle(Color.White.copy(alpha = 0.75f + 0.25f * fill), size.minDimension / 2f)
                        }
                )
                if (index < DotCount - 1) Spacer(modifier = Modifier.size(DotGap))
            }
        }
    }
}

private fun androidx.compose.ui.unit.Density.dotsGlow(
    glows: Array<androidx.compose.runtime.MutableFloatState>,
    finale: Float,
    shown: Float,
): SweepHeadGeometry? {
    val pulse = sin(PI.toFloat() * finale) * 0.7f
    var strongest = pulse
    var weight = 0f
    var weighted = 0f
    val dot = DotSize.toPx()
    val pitch = dot + DotGap.toPx()
    val room = GlowRoom.toPx()
    for (index in 0 until DotCount) {
        val glow = glows[index].floatValue
        strongest = maxOf(strongest, glow)
        weight += glow
        weighted += glow * (room + index * pitch + dot / 2f)
    }
    val strength = strongest * shown
    if (strength < 0.01f) return null
    val groupCentre = room + (DotCount - 1) * pitch / 2f + dot / 2f
    val lit = if (weight > 0f) weighted / weight else groupCentre
    val groupWidth = DotCount * dot + (DotCount - 1) * DotGap.toPx()
    return glowAround(
        centreX = lit + (groupCentre - lit) * finale,
        spanPx = pitch + (groupWidth - pitch) * finale,
        top = 0f,
        bottom = dot + room * 2f,
        glow = strength,
    )
}

private fun smoothstepDots(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

