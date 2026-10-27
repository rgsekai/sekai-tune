/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.smart

import kotlin.math.max
import kotlin.math.min

/**
 * Transition style for the Automix blend.
 */
enum class AutomixStyle {
    STANDARD_FADE,
    BASS_SWAP,
    GAPLESS
}

/**
 * Concrete transition plan for a pair of tracks.
 * Independent of ExoPlayer rendering.
 */
data class AutomixPlan(
    val transitionStartMs: Long,
    val transitionEndMs: Long,
    val fadeDurationMs: Long,
    val cueInMs: Long = 0L,
    val style: AutomixStyle = AutomixStyle.STANDARD_FADE,
    val bassSwap: Boolean = false,
    val bassSwapFraction: Float = 0.5f,
    val reason: String = "default"
)

object AutomixPlanner {
    const val MIN_AUTOMIX_TRACK_DURATION_MS = 15_000L
    const val MAX_AUTOMIX_FADE_DURATION_MS = 12_000L
    const val DEFAULT_CUE_IN_MAX_MS = 2_000L
    const val GUARD_END_MS = 150L

    /**
     * Compute a concrete transition plan for the active outgoing track into the incoming target.
     */
    fun planTransition(
        outgoingDurationMs: Long,
        configuredFadeDurationMs: Long,
        incomingDurationMs: Long = 0L,
        isAutomixEnabled: Boolean = false,
        cueInOffsetMs: Long = 0L,
        outroOffsetMs: Long = 0L
    ): AutomixPlan {
        if (!isAutomixEnabled || outgoingDurationMs <= 0L) {
            val endMs = max(0L, outgoingDurationMs - GUARD_END_MS)
            val fadeMs = min(configuredFadeDurationMs, endMs)
            return AutomixPlan(
                transitionStartMs = max(0L, endMs - fadeMs),
                transitionEndMs = endMs,
                fadeDurationMs = fadeMs,
                cueInMs = 0L,
                style = AutomixStyle.STANDARD_FADE,
                bassSwap = false,
                reason = "legacy-or-disabled"
            )
        }

        // Outro detection: if an outro offset is specified (e.g. silence trim), pull anchor back
        val safeOutro = outroOffsetMs.coerceIn(0L, outgoingDurationMs / 4)
        val mixAnchorMs = max(0L, outgoingDurationMs - GUARD_END_MS - safeOutro)

        // Effective fade duration clamped between min crossfade and max mix window
        val effectiveFadeMs = configuredFadeDurationMs
            .coerceIn(500L, MAX_AUTOMIX_FADE_DURATION_MS)
            .coerceAtMost(mixAnchorMs)

        val transitionStartMs = max(0L, mixAnchorMs - effectiveFadeMs)

        // Cue in: start incoming track past any dead air or silence padding
        val effectiveCueIn = cueInOffsetMs.coerceIn(0L, DEFAULT_CUE_IN_MAX_MS)

        return AutomixPlan(
            transitionStartMs = transitionStartMs,
            transitionEndMs = mixAnchorMs,
            fadeDurationMs = effectiveFadeMs,
            cueInMs = effectiveCueIn,
            style = AutomixStyle.BASS_SWAP,
            bassSwap = true,
            bassSwapFraction = 0.5f,
            reason = "automix-planned"
        )
    }
}
