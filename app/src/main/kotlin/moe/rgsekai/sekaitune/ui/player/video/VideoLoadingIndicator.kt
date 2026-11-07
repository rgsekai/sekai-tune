/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player.video

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin

private const val ShowAfterMs = 350L
private const val TurnMs = 1_100f
private const val ArcMin = 0.12f
private const val ArcMax = 0.62f

@Composable
fun VideoLoadingIndicator(
    loading: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val alpha = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(ShowAfterMs)
            spinning = true
            alpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
        } else {
            alpha.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            spinning = false
        }
    }
    if (!spinning) return
    var timeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { timeMs = it - start }
    }
    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                this.alpha = alpha.value
                val grow = 0.85f + 0.15f * alpha.value
                scaleX = grow
                scaleY = grow
            },
    ) {
        val stroke = 2.6.dp.toPx()
        val t = timeMs / TurnMs
        val sweep = 360f * (ArcMin + (ArcMax - ArcMin) * (0.5f + 0.5f * sin(t * 2.4f)))
        drawCircle(Color.Black.copy(alpha = 0.28f), radius = this.size.minDimension / 2f + stroke * 2.2f)
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawCircle(Color.White.copy(alpha = 0.18f), radius = arcSize.minDimension / 2f, style = Stroke(stroke))
        rotate(degrees = (t * 360f) % 360f) {
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

