package moe.rgsekai.sekaitune.ui.player.flagship

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val PageSwipeCommit = 40.dp
private val PageSwipeFling = 600.dp

enum class FlagshipSwipeMode {
    NONE,
    PULL,
    PUSH,
    OPEN_OTHER,
    BACK,
}

fun Modifier.flagshipSwipeHandler(
    transition: FlagshipPageTransition,
    focusedPageProvider: () -> FlagshipPage,
    controlsTopProvider: () -> Float,
    seekBarTopProvider: () -> Float,
    screenHeightPx: Float,
    cardTopProvider: () -> Float? = { null },
    controlsHiddenProvider: () -> Float = { 0f },
): Modifier = pointerInput(transition, screenHeightPx) {
    val commitPx = PageSwipeCommit.toPx()
    val flingPx = PageSwipeFling.toPx()

    fun distance(): Float {
        val cardTop = cardTopProvider()
        return (cardTop ?: (screenHeightPx * 0.85f)).coerceAtLeast(1f)
    }

    fun inControlStrip(start: Offset): Boolean {
        val top = seekBarTopProvider()
        return !top.isNaN() && controlsHiddenProvider() < 0.5f && start.y >= top
    }

    fun aroundControls(start: Offset): Boolean {
        if (controlsHiddenProvider() >= 0.5f) return false
        val top = controlsTopProvider()
        return !top.isNaN() && start.y >= top
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)

        var sum = Offset.Zero
        var mode = FlagshipSwipeMode.NONE
        var travelled = 0f
        var claimed = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                val velocity = velocityTracker.calculateVelocity().y
                val finished = mode
                mode = FlagshipSwipeMode.NONE

                when (finished) {
                    FlagshipSwipeMode.PULL, FlagshipSwipeMode.PUSH -> {
                        transition.release(-velocity / distance())
                    }
                    FlagshipSwipeMode.OPEN_OTHER -> {
                        if (travelled < -commitPx || velocity < -flingPx) {
                            transition.open(
                                if (transition.page == FlagshipPage.LYRICS) FlagshipPage.QUEUE else FlagshipPage.LYRICS
                            )
                        }
                    }
                    FlagshipSwipeMode.BACK -> {
                        if (travelled > commitPx || velocity > flingPx) {
                            transition.back()
                        }
                    }
                    FlagshipSwipeMode.NONE -> Unit
                }
                break
            }

            velocityTracker.addPosition(change.uptimeMillis, change.position)
            val posChange = change.positionChange()
            val dy = posChange.y
            val dx = posChange.x

            if (!claimed) {
                sum += Offset(dx, dy)
                if (abs(sum.y) > abs(sum.x) && abs(sum.y) > viewConfiguration.touchSlop) {
                    val up = sum.y < 0f
                    mode = when {
                        transition.page == FlagshipPage.NONE -> {
                            if (up && aroundControls(start) && transition.beginDrag(focusedPageProvider())) {
                                FlagshipSwipeMode.PULL
                            } else {
                                FlagshipSwipeMode.NONE
                            }
                        }
                        inControlStrip(start) -> {
                            when {
                                up -> FlagshipSwipeMode.OPEN_OTHER
                                transition.beginCloseDrag() -> FlagshipSwipeMode.PUSH
                                else -> FlagshipSwipeMode.BACK
                            }
                        }
                        else -> FlagshipSwipeMode.NONE
                    }

                    if (mode != FlagshipSwipeMode.NONE) {
                        claimed = true
                        change.consume()
                        travelled = sum.y - dy
                        when (mode) {
                            FlagshipSwipeMode.PULL, FlagshipSwipeMode.PUSH -> {
                                transition.dragBy(-dy / distance())
                            }
                            else -> travelled += dy
                        }
                    } else {
                        break
                    }
                } else if (abs(sum.x) > viewConfiguration.touchSlop && abs(sum.x) >= abs(sum.y)) {
                    break
                }
            } else {
                change.consume()
                when (mode) {
                    FlagshipSwipeMode.PULL, FlagshipSwipeMode.PUSH -> {
                        transition.dragBy(-dy / distance())
                    }
                    else -> travelled += dy
                }
            }
        }
    }
}

