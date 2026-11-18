package moe.rgsekai.sekaitune.ui.player.flagship

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

// the design's shade starts here on the screen and runs to the bottom
private const val ShadeTop = 0.538f

// on a white picture the extra dark starts a little above the frost and is full before the title's top, so the title gets it too
private const val GlareTop = 0.48f
private const val GlareFull = 0.545f

// how much more black a fully white picture gets under the text, on top of the usual shade
private const val GlareShade = 0.3f

// clear at the top, 24% black by 46% of the way down, then held: just enough for white text over the blurred cover
private val ShadeStops = arrayOf(
    0f to Color.Transparent,
    0.46f to Color.Black.copy(alpha = 0.24f),
    1f to Color.Black.copy(alpha = 0.24f),
)

// the new flagship's dark shade under its controls, over the cover fading into its blurred copy; one gradient, no blur of its own
@Composable
fun ControlsShade(
    progressProvider: () -> Float = { 1f },
    maxHeight: Dp,
    // gone while a page is up
    fadeProvider: () -> Float = { 1f },
    // how far the controls (and with them the cover's bottom) were moved, px down; read while placing only
    offsetProvider: () -> Float = { 0f },
    // 0 to 1, how white the picture is under the text; read while drawing only
    glareProvider: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val drawn by remember { derivedStateOf { progressProvider() > 0.5f && fadeProvider() > 0f } }
    if (!drawn) return
    val currentProgress by rememberUpdatedState(progressProvider)
    val currentFade by rememberUpdatedState(fadeProvider)
    val currentGlare by rememberUpdatedState(glareProvider)
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(maxHeight * (1f - GlareTop))
            .offset { IntOffset(0, (maxHeight.toPx() * GlareTop + offsetProvider()).roundToInt()) }
            .drawWithCache {
                val screen = size.height / (1f - GlareTop)
                // the usual shade keeps its old place, the box just starts higher now for the glare's dark
                val shade = Brush.verticalGradient(*ShadeStops, startY = screen * (ShadeTop - GlareTop), endY = size.height)
                val glareDark = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = GlareShade),
                    startY = 0f,
                    endY = screen * (GlareFull - GlareTop),
                )
                onDrawBehind {
                    val shown = ((currentProgress() - 0.5f) / 0.45f).coerceIn(0f, 1f) * currentFade().coerceIn(0f, 1f)
                    drawRect(shade, alpha = shown)
                    val glare = currentGlare().coerceIn(0f, 1f)
                    if (glare > 0.004f) drawRect(glareDark, alpha = shown * glare)
                }
            },
    )
}

