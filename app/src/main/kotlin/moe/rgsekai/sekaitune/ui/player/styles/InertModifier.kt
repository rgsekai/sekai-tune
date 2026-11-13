package moe.rgsekai.sekaitune.ui.player.styles

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Modifier that intercepts and consumes all pointer/touch input unconditionally.
 * Used for live player previews so that child buttons, sliders, and gestures cannot
 * trigger playback actions, seeks, or volume changes.
 */
fun Modifier.inert(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        }
    }
}

