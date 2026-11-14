package moe.rgsekai.sekaitune.ui.player.flagship

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import moe.rgsekai.sekaitune.R

// how small play and pause get before they swap and spring back
private const val SquashLow = 0.5f

// the shapes of one button row from the design, as paths in its own units, built once from the vector drawables
@Immutable
internal class TransportShapes(
    val play: Path,
    val playSize: Size,
    val skip: Path,
    val skipSize: Size,
    // how far apart a skip button's two triangles sit, and the pair's whole width
    val skipStep: Float,
    val pairWidth: Float,
    // where the play triangle's box sits right of its slot's centre: the design centres it by eye, not by box
    val playShift: Float,
)

// the three button row's shapes (big, nothing round them), or the ten second row's smaller ones
@Composable
internal fun rememberTransportShapes(small: Boolean = false): TransportShapes {
    val play = ImageVector.vectorResource(if (small) R.drawable.transport_play_small else R.drawable.transport_play)
    val skip = ImageVector.vectorResource(if (small) R.drawable.transport_skip_small else R.drawable.transport_skip)
    return remember(play, skip, small) {
        TransportShapes(
            play = play.toFlatPath(),
            playSize = Size(play.viewportWidth, play.viewportHeight),
            skip = skip.toFlatPath(),
            skipSize = Size(skip.viewportWidth, skip.viewportHeight),
            skipStep = if (small) 45f else 61f,
            pairWidth = if (small) 116.22f else 157.32f,
            playShift = if (small) 9.05f else 12.88f,
        )
    }
}

// every path of the vector in one, with its groups' moves applied, so it draws without a painter's bitmap cache
private fun ImageVector.toFlatPath(): Path {
    val out = Path()
    fun walk(group: VectorGroup, dx: Float, dy: Float) {
        for (i in 0 until group.size) {
            when (val node = group[i]) {
                is VectorGroup -> walk(node, dx + node.translationX, dy + node.translationY)
                is VectorPath -> out.addPath(node.pathData.toPath(), Offset(dx, dy))
            }
        }
    }
    walk(root, 0f, 0f)
    return out
}

// play and pause swapping: the glyph squeezes small, swaps while it's tiny, then bounces back past full size
@Stable
internal class PlayGlyphState(playing: Boolean) {
    val squash = Animatable(1f)
    var showsPause by mutableStateOf(playing)
        private set

    suspend fun swapTo(playing: Boolean) {
        if (playing == showsPause && squash.value == 1f) return
        squash.animateTo(SquashLow, tween(durationMillis = 110, easing = FastOutLinearInEasing))
        showsPause = playing
        squash.animateTo(1f, spring(dampingRatio = 0.34f, stiffness = 420f))
    }
}

@Composable
internal fun rememberPlayGlyph(playing: Boolean): PlayGlyphState {
    val state = remember { PlayGlyphState(playing) }
    LaunchedEffect(playing) { state.swapTo(playing) }
    return state
}

// play or pause centred on the slot's centre, one frame unit being unit px, squashed about that centre
internal fun DrawScope.drawPlayGlyph(
    shapes: TransportShapes,
    glyph: PlayGlyphState,
    unit: Float,
    color: Color,
) {
    val s = glyph.squash.value
    val cx = size.width / 2f
    val cy = size.height / 2f
    withTransform({ scale(s, s, Offset(cx, cy)) }) {
        if (glyph.showsPause) {
            drawPause(cx, cy, shapes.playSize.height * unit, color)
        } else {
            val w = shapes.playSize.width * unit
            val h = shapes.playSize.height * unit
            withTransform({
                translate(cx + shapes.playShift * unit - w / 2f, cy - h / 2f)
                scale(unit, unit, Offset.Zero)
            }) {
                drawPath(shapes.play, color)
            }
        }
    }
}

// the old player's pause at its size beside the play triangle: two thick bars with fully round ends, a narrow gap
private fun DrawScope.drawPause(cx: Float, cy: Float, playHeight: Float, color: Color) {
    val barW = playHeight * 0.29f
    val barH = playHeight * 0.86f
    val gap = playHeight * 0.145f
    val radius = CornerRadius(barW / 2f)
    drawRoundRect(color, Offset(cx - gap / 2f - barW, cy - barH / 2f), Size(barW, barH), radius)
    drawRoundRect(color, Offset(cx + gap / 2f, cy - barH / 2f), Size(barW, barH), radius)
}

// a skip button's cycle: each press moves it one step on, a spring carrying it so quick presses run together
@Stable
internal class SkipCycleState {
    val position = Animatable(0f)
    var target by mutableIntStateOf(0)
        private set

    suspend fun step() {
        target += 1
        position.animateTo(target.toFloat(), spring(dampingRatio = 0.6f, stiffness = 320f))
    }

    // how far into the newest step it is; a long way behind it just plays the steps it's still catching up on
    fun phase(): Float {
        val f = position.value - (target - 1)
        return if (f >= 0f) f else position.value - kotlin.math.floor(position.value)
    }
}

// the two triangles at phase f: the front one runs off and shrinks away, the back one takes its place, a new one grows in behind
internal fun DrawScope.drawSkipGlyph(
    shapes: TransportShapes,
    phase: Float,
    unit: Float,
    color: Color,
    backwards: Boolean,
) {
    val w = shapes.skipSize.width * unit
    val h = shapes.skipSize.height * unit
    val step = shapes.skipStep * unit
    val left = size.width / 2f - shapes.pairWidth * unit / 2f
    val top = size.height / 2f - h / 2f
    val f = phase.coerceAtLeast(0f)
    val gone = f.coerceAtMost(1f)
    withTransform({ if (backwards) scale(-1f, 1f, Offset(size.width / 2f, size.height / 2f)) }) {
        // the new one, growing out of nothing where the back one was
        skipTriangle(shapes, left, top, w, h, unit, scale = f, alpha = (f * 2.5f).coerceIn(0f, 1f), color = color)
        // the back one sliding forward into the front's place, a touch past it on the bounce
        skipTriangle(shapes, left + step * f, top, w, h, unit, scale = 1f, alpha = 1f, color = color)
        // the front one running ahead as it shrinks and fades away
        if (gone < 1f) {
            skipTriangle(shapes, left + step + step * 0.8f * gone, top, w, h, unit, scale = 1f - gone, alpha = 1f - gone, color = color)
        }
    }
}

private fun DrawScope.skipTriangle(
    shapes: TransportShapes,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    unit: Float,
    scale: Float,
    alpha: Float,
    color: Color,
) {
    if (scale <= 0.001f || alpha <= 0.001f) return
    withTransform({
        scale(scale, scale, Offset(x + w / 2f, y + h / 2f))
        translate(x, y)
        scale(unit, unit, Offset.Zero)
    }) {
        drawPath(shapes.skip, color, alpha = alpha)
    }
}

// the flagship's play and pause on its own, for any style's button: the triangle is height tall and squashes as it swaps
@Composable
internal fun AnimatedPlayGlyph(playing: Boolean, color: Color, height: Dp, modifier: Modifier = Modifier) {
    val shapes = rememberTransportShapes()
    val glyph = rememberPlayGlyph(playing)
    Spacer(
        modifier = modifier
            .size(height)
            .drawBehind { drawPlayGlyph(shapes, glyph, size.height / shapes.playSize.height, color) },
    )
}

// the flagship's two skip triangles on their own, width across, running a step each time cycle steps
@Composable
internal fun AnimatedSkipGlyph(cycle: SkipCycleState, backwards: Boolean, color: Color, width: Dp, modifier: Modifier = Modifier) {
    val shapes = rememberTransportShapes()
    Spacer(
        modifier = modifier
            .size(width, width * (shapes.skipSize.height / shapes.pairWidth))
            .drawBehind { drawSkipGlyph(shapes, cycle.phase(), size.width / shapes.pairWidth, color, backwards) },
    )
}
