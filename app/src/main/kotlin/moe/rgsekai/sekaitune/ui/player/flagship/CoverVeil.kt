package moe.rgsekai.sekaitune.ui.player.flagship

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush

// the colour stays clear over the top of the blurred mirror and rises toward the bottom of the screen
private const val VeilFrom = 0.3f
private const val VeilStrength = 0.7f

@Composable
fun CoverVeil(
    colors: AmbientColors,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .drawWithCache {
                val seam = colors.seam()
                val veil = Brush.verticalGradient(
                    0f to seam.copy(alpha = 0f),
                    VeilFrom to seam.copy(alpha = 0f),
                    1f to seam.copy(alpha = VeilStrength),
                )
                onDrawBehind {
                    val a = alpha().coerceIn(0f, 1f)
                    if (a > 0.004f) drawRect(veil, alpha = a)
                }
            },
    )
}

