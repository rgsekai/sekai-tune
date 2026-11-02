package moe.rgsekai.sekaitune.desktopui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

/**
 * How far the pointer has settled over a player control, 0 to 1, animated.
 *
 * The player's glyphs have their ripple removed, so on a desktop they answered nothing until they
 * were pressed — which reads as a page in a browser rather than an app. Apple Music lights a control
 * the moment the cursor enters it. Callers install [hoverable] themselves and read this to brighten
 * a capsule or an icon. Touch has no hover, so on a phone this stays at 0 and nothing changes.
 */
@Composable
internal fun rememberPlayerHover(interaction: MutableInteractionSource): State<Float> {
    val hovered by interaction.collectIsHoveredAsState()
    return animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(PLAYER_HOVER_MS),
        label = "playerHover",
    )
}

/**
 * A round glyph's acknowledgement of the pointer: the faintest wash over its disc.
 *
 * Installs its own [hoverable], so it suits a control that wants a circular highlight and has no
 * other use for the hover state. Kept lighter than an active disc, so hovering never reads as
 * chosen. Placed after the disc's own background, it brightens that disc rather than replacing it.
 */
@Composable
internal fun Modifier.playerHoverDisc(alpha: Float = PLAYER_HOVER_DISC_ALPHA): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val hover by rememberPlayerHover(interaction)
    this
        .hoverable(interaction)
        .drawBehind {
            if (hover <= 0.01f) return@drawBehind
            drawCircle(
                color = Color.White.copy(alpha = alpha * hover),
                radius = size.minDimension / 2f,
            )
        }
}

private const val PLAYER_HOVER_MS = 120
private const val PLAYER_HOVER_DISC_ALPHA = 0.14f
