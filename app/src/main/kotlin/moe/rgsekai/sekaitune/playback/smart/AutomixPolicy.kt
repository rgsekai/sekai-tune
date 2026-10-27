/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.smart

import androidx.media3.common.Player

/**
 * Snapshot of playback state needed to determine Automix eligibility.
 */
data class AutomixContext(
    val automixEnabled: Boolean,
    val crossfadeEnabled: Boolean,
    val isTogetherActive: Boolean,
    val isCasting: Boolean,
    val repeatMode: Int,
    val currentDurationMs: Long,
    val isSameAlbum: Boolean
)

/**
 * Evaluates whether an Automix transition can safely proceed.
 * If any condition fails, falls back gracefully to standard crossfade or gapless.
 */
object AutomixPolicy {

    const val MIN_PLAYABLE_DURATION_MS = 650L

    fun isAutomixSupported(context: AutomixContext): Boolean {
        if (!context.automixEnabled) return false
        if (!context.crossfadeEnabled) return false
        if (context.isTogetherActive) return false
        if (context.isCasting) return false
        if (context.repeatMode == Player.REPEAT_MODE_ONE) return false
        if (context.currentDurationMs < MIN_PLAYABLE_DURATION_MS) return false
        if (context.isSameAlbum) return false
        return true
    }
}
