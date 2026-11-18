package moe.rgsekai.sekaitune.ui.player.flagship

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class FlagshipPage {
    NONE,
    LYRICS,
    QUEUE,
}

private val OpenSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = 230f,
    visibilityThreshold = 0.0005f,
)

private val CloseSpring = spring<Float>(
    dampingRatio = 1f,
    stiffness = 280f,
    visibilityThreshold = 0.0005f,
)

private val SwitchTween = tween<Float>(
    durationMillis = 420,
    easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
)

private const val OpenThreshold = 0.38f
private const val FlingDecides = 1.4f
private const val ReleaseProjectionSec = 0.14f
private const val MaxCarriedVelocity = 4.5f

@Stable
class FlagshipPageTransition(private val scope: CoroutineScope) {
    var page by mutableStateOf(FlagshipPage.NONE)
        private set

    var linked by mutableStateOf(FlagshipPage.NONE)
        private set

    var dragging by mutableStateOf(false)
        private set

    var preparing by mutableStateOf(FlagshipPage.NONE)
        private set

    var preparingClose by mutableStateOf(false)
        private set

    var closeNeedsPreparing: () -> Boolean = { false }

    var previous by mutableStateOf(FlagshipPage.NONE)
        private set

    private val header = Animatable(0f).apply { updateBounds(lowerBound = 0f) }
    private val lyrics = Animatable(0f)
    private val queue = Animatable(0f)
    private var job: Job? = null
    private var dragValue = 0f

    val headerProgress: Float get() = header.value

    val lyricsProgress: Float get() = if (linked == FlagshipPage.LYRICS) header.value else lyrics.value
    val queueProgress: Float get() = if (linked == FlagshipPage.QUEUE) header.value else queue.value

    fun progressOf(target: FlagshipPage): Float = when (target) {
        FlagshipPage.LYRICS -> lyricsProgress
        FlagshipPage.QUEUE -> queueProgress
        FlagshipPage.NONE -> 0f
    }

    fun isCardMorph(target: FlagshipPage): Boolean = linked == target && target != FlagshipPage.NONE

    fun cardMorphProgress(target: FlagshipPage): Float =
        if (isCardMorph(target)) header.value else 1f

    private fun animatableOf(target: FlagshipPage) = if (target == FlagshipPage.QUEUE) queue else lyrics
    private fun other(target: FlagshipPage) = if (target == FlagshipPage.QUEUE) FlagshipPage.LYRICS else FlagshipPage.QUEUE

    fun open(target: FlagshipPage) {
        if (target == FlagshipPage.NONE) {
            close()
            return
        }
        if (page == FlagshipPage.NONE) previous = FlagshipPage.NONE
        else if (page != target) previous = page
        page = target
        job?.cancel()
        job = scope.launch {
            if (linked == target || header.value < 0.001f) {
                if (linked != target) {
                    lyrics.snapTo(0f)
                    queue.snapTo(0f)
                    linked = target
                }
                if (header.value < 0.001f) {
                    preparing = target
                    try {
                        withFrameNanos { }
                        withFrameNanos { }
                        header.animateTo(1f, OpenSpring)
                    } finally {
                        preparing = FlagshipPage.NONE
                    }
                } else {
                    header.animateTo(1f, OpenSpring)
                }
            } else {
                if (linked != FlagshipPage.NONE) {
                    animatableOf(linked).snapTo(header.value.coerceIn(0f, 1f))
                    linked = FlagshipPage.NONE
                }
                coroutineScope {
                    launch { header.animateTo(1f, OpenSpring) }
                    launch { animatableOf(target).animateTo(1f, SwitchTween) }
                    launch { animatableOf(other(target)).animateTo(0f, SwitchTween) }
                }
                animatableOf(target).snapTo(1f)
                linked = target
            }
        }
    }

    fun back() {
        val to = previous
        if (to == FlagshipPage.NONE) {
            close()
        } else {
            open(to)
            previous = FlagshipPage.NONE
        }
    }

    fun close() {
        val prepare = page != FlagshipPage.NONE && closeNeedsPreparing()
        if (!prepare) {
            page = FlagshipPage.NONE
            previous = FlagshipPage.NONE
        }
        job?.cancel()
        job = scope.launch {
            if (prepare) {
                preparingClose = true
                try {
                    withFrameNanos { }
                    withFrameNanos { }
                    page = FlagshipPage.NONE
                    previous = FlagshipPage.NONE
                } finally {
                    preparingClose = false
                }
            }
            if (linked == FlagshipPage.NONE) {
                linked = if (lyrics.value >= queue.value) FlagshipPage.LYRICS else FlagshipPage.QUEUE
            }
            val fading = animatableOf(other(linked))
            coroutineScope {
                launch { header.animateTo(0f, CloseSpring) }
                launch { fading.animateTo(0f, SwitchTween) }
            }
            lyrics.snapTo(0f)
            queue.snapTo(0f)
            linked = FlagshipPage.NONE
            page = FlagshipPage.NONE
        }
    }

    fun reset() {
        page = FlagshipPage.NONE
        previous = FlagshipPage.NONE
        dragging = false
        job?.cancel()
        job = scope.launch {
            header.snapTo(0f)
            lyrics.snapTo(0f)
            queue.snapTo(0f)
            linked = FlagshipPage.NONE
        }
    }

    fun beginDrag(target: FlagshipPage): Boolean {
        if (target == FlagshipPage.NONE || page != FlagshipPage.NONE || header.value > 0.02f) return false
        job?.cancel()
        dragValue = header.value
        dragging = true
        linked = target
        job = scope.launch {
            lyrics.snapTo(0f)
            queue.snapTo(0f)
        }
        return true
    }

    fun beginCloseDrag(): Boolean {
        if (page == FlagshipPage.NONE || previous != FlagshipPage.NONE || linked != page || dragging) return false
        if (closeNeedsPreparing()) return false
        job?.cancel()
        dragValue = header.value.coerceIn(0f, 1f)
        dragging = true
        return true
    }

    fun dragBy(delta: Float) {
        if (!dragging) return
        dragValue = (dragValue + delta).coerceIn(0f, 1f)
        val value = dragValue
        scope.launch { if (dragging) header.snapTo(value) }
    }

    fun release(velocity: Float) {
        if (!dragging) return
        dragging = false
        val projected = dragValue + velocity * ReleaseProjectionSec
        val open = if (abs(velocity) > FlingDecides) velocity > 0f else projected > OpenThreshold
        val target = linked
        page = if (open) target else FlagshipPage.NONE
        job?.cancel()
        val from = dragValue
        job = scope.launch {
            header.snapTo(from)
            header.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = if (open) OpenSpring else CloseSpring,
                initialVelocity = velocity.coerceIn(-MaxCarriedVelocity, MaxCarriedVelocity),
            )
        }
    }
}

@Stable
class FlagshipDeckState {
    val flip = Animatable(0f)
    var frontIndex by mutableIntStateOf(0)

    val focusedPage: FlagshipPage get() = if (frontIndex == 1) FlagshipPage.QUEUE else FlagshipPage.LYRICS
}

@Composable
fun rememberFlagshipPageTransition(): FlagshipPageTransition {
    val scope = rememberCoroutineScope()
    return remember(scope) { FlagshipPageTransition(scope) }
}

@Composable
fun rememberFlagshipDeckState(): FlagshipDeckState {
    return remember { FlagshipDeckState() }
}
