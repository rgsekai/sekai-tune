/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry

/**
 * Central motion specifications for Sekai Tune.
 * Keeps all animation parameters, easings, spring curves and durations unified,
 * with global scale and on/off controls.
 */
object SekaiMotion {
    /** Global switch: when false, Compose animations jump directly to completion. */
    var enabled by mutableStateOf(true)

    /** Speed multiplier relative to base specs. 1.0 = standard speed. */
    var speed by mutableFloatStateOf(1f)

    // Standard Curvatures / Easings
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    // Physical Springs
    val PressBounce: SpringSpec<Float> = spring(
        dampingRatio = 0.6f,
        stiffness = 700f,
    )
    val NavRecede: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 380f,
        visibilityThreshold = 0.001f,
    )

    // Standard Durations (Base Milliseconds)
    const val DurationShort = 200
    const val DurationMedium = 350
    const val DurationLong = 520
    const val DurationReturn = 380

    // Distances and Offsets
    val SlideDistance: Dp = 20.dp
    const val StaggerStepMs = 50
    const val BurstWindowMs = 120L
    const val BurstStepMs = 60
    const val BurstMaxSteps = 6

    // Navigation Recede Specs
    const val RecedeScale = 0.92f
    const val RecedeAlpha = 0.45f

    // Visual Palette & Placeholders
    val BoneColor: Color = Color(0xFFD9D9D9).copy(alpha = 0.22f)

    /**
     * Computes the effective duration scaled by [speed].
     * If [enabled] is false, returns 0 ms.
     */
    fun duration(baseMillis: Int): Int {
        if (!enabled) return 0
        return (baseMillis / speed.coerceAtLeast(0.1f)).toInt()
    }

    /**
     * Builds a scaled tween animation spec with the provided [easing].
     */
    fun <T> tweenSpec(baseMillis: Int, delayMillis: Int = 0, easing: Easing = Emphasized) =
        tween<T>(
            durationMillis = duration(baseMillis),
            delayMillis = if (!enabled) 0 else (delayMillis / speed.coerceAtLeast(0.1f)).toInt(),
            easing = easing,
        )
}

/**
 * Returns [transition] if animations are enabled, otherwise [EnterTransition.None].
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.motionEnter(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?,
): EnterTransition? = if (SekaiMotion.enabled) transition() else EnterTransition.None

/**
 * Returns [transition] if animations are enabled, otherwise [ExitTransition.None].
 */
fun AnimatedContentTransitionScope<NavBackStackEntry>.motionExit(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?,
): ExitTransition? = if (SekaiMotion.enabled) transition() else ExitTransition.None

