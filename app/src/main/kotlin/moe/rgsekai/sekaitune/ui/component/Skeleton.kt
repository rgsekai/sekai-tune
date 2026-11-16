/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.component

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rgsekai.sekaitune.ui.theme.SekaiMotion
import kotlin.math.min

/**
 * Tracks which keys have already displayed their appear animation.
 * Kept with [rememberSaveable] so scrolling back or navigating back does not replay transitions.
 */
@Stable
class RevealSeenState(initial: Collection<String> = emptyList()) {
    private val seen = HashSet(initial)

    fun hasSeen(key: String): Boolean = key in seen

    fun markSeen(key: String) {
        seen.add(key)
    }

    private var burstStartedAt = 0L
    private var burstCount = 0

    /** Computes burst delay for items appearing together. */
    internal fun nextBurstDelay(): Int {
        val now = SystemClock.uptimeMillis()
        if (now - burstStartedAt > SekaiMotion.BurstWindowMs) {
            burstStartedAt = now
            burstCount = 0
        }
        return min(burstCount++, SekaiMotion.BurstMaxSteps) * SekaiMotion.BurstStepMs
    }

    companion object {
        val Saver: Saver<RevealSeenState, ArrayList<String>> = Saver(
            save = { ArrayList(it.seen) },
            restore = { RevealSeenState(it) },
        )
    }
}

@Composable
fun rememberRevealSeenState(): RevealSeenState =
    rememberSaveable(saver = RevealSeenState.Saver) { RevealSeenState() }

/**
 * Renders [bones] while [data] is null, and transitions to [content] once available.
 * Uses draw-phase alpha and vertical translation with zero continuous recomposition.
 */
@Composable
fun <T : Any> RevealWhenLoaded(
    data: T?,
    modifier: Modifier = Modifier,
    bones: @Composable () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val slidePx = with(LocalDensity.current) { SekaiMotion.SlideDistance.roundToPx() }

    AnimatedContent(
        targetState = data,
        contentKey = { it != null },
        transitionSpec = {
            if (targetState != null) {
                val delay = SekaiMotion.duration(SekaiMotion.DurationShort)
                (fadeIn(SekaiMotion.tweenSpec(SekaiMotion.DurationLong, delayMillis = delay, easing = SekaiMotion.Emphasized)) +
                    slideInVertically(SekaiMotion.tweenSpec(SekaiMotion.DurationLong, delayMillis = delay, easing = SekaiMotion.Emphasized)) { slidePx })
                    .togetherWith(fadeOut(SekaiMotion.tweenSpec(SekaiMotion.DurationShort)))
                    .using(SizeTransform(clip = false))
            } else {
                fadeIn(SekaiMotion.tweenSpec(SekaiMotion.DurationShort))
                    .togetherWith(fadeOut(SekaiMotion.tweenSpec(SekaiMotion.DurationShort)))
                    .using(SizeTransform(clip = false))
            }
        },
        label = "RevealWhenLoaded",
        modifier = modifier,
    ) { state ->
        if (state == null) bones() else content(state)
    }
}

/**
 * Fades and slides an item up the first time it shows for [key].
 * Fully isolated to the graphics layer (draw phase).
 */
@Composable
fun Modifier.revealOnAppear(
    key: String,
    seenState: RevealSeenState,
    enabled: Boolean = true,
    delayMillis: Int = 0,
    cascade: Boolean = false,
): Modifier {
    if (!SekaiMotion.enabled) return this
    val alreadySeen = remember(key) { seenState.hasSeen(key) }
    if (alreadySeen) return this

    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        seenState.markSeen(key)
        val wait = delayMillis + if (cascade) seenState.nextBurstDelay() else 0
        progress.animateTo(
            targetValue = 1f,
            animationSpec = SekaiMotion.tweenSpec(
                baseMillis = SekaiMotion.DurationLong,
                delayMillis = wait,
                easing = SekaiMotion.Emphasized,
            ),
        )
    }

    val slidePx = with(LocalDensity.current) { SekaiMotion.SlideDistance.toPx() }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * slidePx
    }
}

/**
 * Static placeholder bone matching layout dimensions.
 * Uses [SekaiMotion.BoneColor] with zero per-frame redraws.
 */
@Composable
fun Bone(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    color: Color = SekaiMotion.BoneColor,
) {
    Box(
        modifier = modifier
            .size(width, height)
            .background(color, shape),
    )
}

/**
 * Container for structural loading skeletons.
 */
@Composable
fun BonesHost(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        content = content,
    )
}

