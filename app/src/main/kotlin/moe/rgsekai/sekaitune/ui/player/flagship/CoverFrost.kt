package moe.rgsekai.sekaitune.ui.player.flagship

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// the frost starts above the title row and runs past the screen's bottom so it never ends in view
private const val FrostTop = 0.50f
private const val FrostTitle = 0.565f
private const val FrostBottom = 1.03f

private const val FrostFull = FrostTop + (FrostTitle - FrostTop) / 0.83666f
private const val FullAt = (FrostFull - FrostTop) / (FrostBottom - FrostTop)

private val FrostRadius = 40.dp

private val ProgressiveMask = Brush.verticalGradient(
    0f to Color.Transparent,
    0.04f to Color.Black,
    1f to Color.Black,
)

private const val FlatTop = 0.44f
private const val FlatFull = 0.545f
private const val FlatFullAt = (FlatFull - FlatTop) / (FrostBottom - FlatTop)

private val FlatMask = Brush.verticalGradient(
    *Array(9) { i ->
        val t = i / 8f
        t * FlatFullAt to Color.Black.copy(alpha = t * t * (3f - 2f * t))
    },
    1f to Color.Black,
)

// the frost over the cover fading into its blurred copy
@Composable
fun CoverFrost(
    progressProvider: () -> Float = { 1f },
    maxHeight: Dp,
    fadeProvider: () -> Float = { 1f },
    offsetProvider: () -> Float = { 0f },
    disableBlur: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (disableBlur) return
    val drawn by remember { derivedStateOf { progressProvider() > 0.5f && fadeProvider() > 0f } }
    if (!drawn) return
    val smooth = Build.VERSION.SDK_INT >= 33
    val top = if (smooth) FrostTop else FlatTop
    val mask = if (smooth) ProgressiveMask else FlatMask
    val currentProgress by rememberUpdatedState(progressProvider)
    val currentFade by rememberUpdatedState(fadeProvider)

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(maxHeight * (FrostBottom - top))
            .offset { IntOffset(0, (maxHeight.toPx() * top + offsetProvider()).roundToInt()) }
            .graphicsLayer {
                alpha = (((currentProgress() - 0.5f) / 0.45f).coerceIn(0f, 1f) * currentFade().coerceIn(0f, 1f) * 0.12f)
            }
            .drawWithCache {
                onDrawBehind {
                    drawRect(mask)
                }
            },
    )
}

