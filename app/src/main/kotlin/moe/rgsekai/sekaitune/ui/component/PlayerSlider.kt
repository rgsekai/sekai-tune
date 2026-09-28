/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

/**
 * Remembers whether playback has remained in [Player.STATE_BUFFERING] continuously for longer than [delayMillis].
 * Cancels and resets immediately on track changes (different [mediaId]), playback reaching [Player.STATE_READY],
 * or leaving buffering.
 */
@Composable
fun rememberDelayedBufferingState(
    playbackState: Int,
    mediaId: String?,
    delayMillis: Long = 1000L,
): Boolean {
    var isBufferingDelayed by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState, mediaId) {
        if (playbackState == Player.STATE_BUFFERING) {
            isBufferingDelayed = false
            delay(delayMillis)
            if (playbackState == Player.STATE_BUFFERING) {
                isBufferingDelayed = true
            }
        } else {
            isBufferingDelayed = false
        }
    }

    return isBufferingDelayed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSliderTrack(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    trackHeight: Dp = 10.dp,
    isBuffering: Boolean = false,
) {
    val inactiveTrackColor = colors.inactiveTrackColor
    val activeTrackColor = colors.activeTrackColor
    val inactiveTickColor = colors.inactiveTickColor
    val activeTickColor = colors.activeTickColor
    val valueRange = sliderState.valueRange

    val infiniteTransition = rememberInfiniteTransition(label = "sliderBufferingTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "sliderPulseAlpha",
    )
    val shimmerOffsetFraction by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "sliderShimmerOffset",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(trackHeight),
    ) {
        drawTrack(
            tickFractions = stepsToTickFractions(sliderState.steps),
            activeRangeStart = 0f,
            activeRangeEnd =
                calcFraction(
                    valueRange.start,
                    valueRange.endInclusive,
                    sliderState.value.coerceIn(valueRange.start, valueRange.endInclusive),
                ),
            inactiveTrackColor = inactiveTrackColor,
            activeTrackColor = activeTrackColor,
            inactiveTickColor = inactiveTickColor,
            activeTickColor = activeTickColor,
            trackHeight = trackHeight,
            isBuffering = isBuffering,
            pulseAlpha = if (isBuffering) pulseAlpha else 1f,
            shimmerOffsetFraction = if (isBuffering) shimmerOffsetFraction else 0f,
        )
    }
}

private fun DrawScope.drawTrack(
    tickFractions: FloatArray,
    activeRangeStart: Float,
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    inactiveTickColor: Color,
    activeTickColor: Color,
    trackHeight: Dp = 2.dp,
    isBuffering: Boolean = false,
    pulseAlpha: Float = 1f,
    shimmerOffsetFraction: Float = 0f,
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val sliderLeft = Offset(0f, center.y)
    val sliderRight = Offset(size.width, center.y)
    val sliderStart = if (isRtl) sliderRight else sliderLeft
    val sliderEnd = if (isRtl) sliderLeft else sliderRight
    val tickSize = 2.0.dp.toPx()
    val trackStrokeWidth = trackHeight.toPx()

    // 1. Draw Inactive Track
    drawLine(
        inactiveTrackColor,
        sliderStart,
        sliderEnd,
        trackStrokeWidth,
        StrokeCap.Round,
    )

    // 2. If Buffering, draw subtle flowing shimmer pulse across the track
    if (isBuffering) {
        val shimmerCenter = sliderStart.x + (sliderEnd.x - sliderStart.x) * shimmerOffsetFraction
        val shimmerWidth = size.width * 0.4f
        val shimmerBrush =
            Brush.horizontalGradient(
                colors =
                    listOf(
                        Color.Transparent,
                        activeTrackColor.copy(alpha = 0.38f * pulseAlpha),
                        Color.Transparent,
                    ),
                startX = shimmerCenter - shimmerWidth / 2f,
                endX = shimmerCenter + shimmerWidth / 2f,
            )
        drawLine(
            brush = shimmerBrush,
            start = sliderStart,
            end = sliderEnd,
            strokeWidth = trackStrokeWidth,
            cap = StrokeCap.Round,
        )
    }

    // 3. Draw Active Track (with gentle pulse when buffering)
    val sliderValueEnd =
        Offset(
            sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeEnd,
            center.y,
        )
    val sliderValueStart =
        Offset(
            sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeStart,
            center.y,
        )

    if (activeRangeEnd > 0f) {
        val effectiveActiveColor =
            if (isBuffering) {
                activeTrackColor.copy(alpha = (activeTrackColor.alpha * pulseAlpha).coerceIn(0f, 1f))
            } else {
                activeTrackColor
            }
        drawLine(
            effectiveActiveColor,
            sliderValueStart,
            sliderValueEnd,
            trackStrokeWidth,
            StrokeCap.Round,
        )
    }

    // 4. Draw Ticks
    for (tick in tickFractions) {
        val outsideFraction = tick > activeRangeEnd || tick < activeRangeStart
        drawCircle(
            color = if (outsideFraction) inactiveTickColor else activeTickColor,
            center = Offset(lerp(sliderStart, sliderEnd, tick).x, center.y),
            radius = tickSize / 2f,
        )
    }
}

private fun stepsToTickFractions(steps: Int): FloatArray =
    if (steps == 0) {
        floatArrayOf()
    } else {
        FloatArray(steps + 2) {
            it.toFloat() / (steps + 1)
        }
    }

private fun calcFraction(
    a: Float,
    b: Float,
    pos: Float,
) = (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)




