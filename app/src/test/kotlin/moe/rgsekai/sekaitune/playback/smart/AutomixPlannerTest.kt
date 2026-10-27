/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.smart

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomixPlannerTest {

    @Test
    fun planTransition_whenAutomixDisabled_returnsStandardFade() {
        val plan = AutomixPlanner.planTransition(
            outgoingDurationMs = 180_000L,
            configuredFadeDurationMs = 5_000L,
            isAutomixEnabled = false
        )

        assertEquals(0L, plan.cueInMs)
        assertEquals(180_000L - AutomixPlanner.GUARD_END_MS, plan.transitionEndMs)
        assertEquals(5_000L, plan.fadeDurationMs)
        assertEquals(AutomixStyle.STANDARD_FADE, plan.style)
        assertFalse(plan.bassSwap)
    }

    @Test
    fun planTransition_whenAutomixEnabled_calculatesSmartTimings() {
        val plan = AutomixPlanner.planTransition(
            outgoingDurationMs = 200_000L,
            configuredFadeDurationMs = 6_000L,
            isAutomixEnabled = true,
            cueInOffsetMs = 350L
        )

        assertEquals(350L, plan.cueInMs)
        assertEquals(200_000L - AutomixPlanner.GUARD_END_MS, plan.transitionEndMs)
        assertEquals(6_000L, plan.fadeDurationMs)
        assertEquals(plan.transitionEndMs - 6_000L, plan.transitionStartMs)
        assertEquals(AutomixStyle.BASS_SWAP, plan.style)
        assertTrue(plan.bassSwap)
        assertEquals(0.5f, plan.bassSwapFraction, 0.001f)
    }

    @Test
    fun planTransition_withOutroOffset_shiftsAnchorEarly() {
        val plan = AutomixPlanner.planTransition(
            outgoingDurationMs = 240_000L,
            configuredFadeDurationMs = 8_000L,
            isAutomixEnabled = true,
            outroOffsetMs = 3_000L
        )

        val expectedAnchor = 240_000L - AutomixPlanner.GUARD_END_MS - 3_000L
        assertEquals(expectedAnchor, plan.transitionEndMs)
        assertEquals(expectedAnchor - 8_000L, plan.transitionStartMs)
    }

    @Test
    fun automixPolicy_supportedPlayback_returnsTrue() {
        val context = AutomixContext(
            automixEnabled = true,
            crossfadeEnabled = true,
            isTogetherActive = false,
            isCasting = false,
            repeatMode = Player.REPEAT_MODE_ALL,
            currentDurationMs = 180_000L,
            isSameAlbum = false
        )

        assertTrue(AutomixPolicy.isAutomixSupported(context))
    }

    @Test
    fun automixPolicy_gatedConditions_returnFalse() {
        val base = AutomixContext(
            automixEnabled = true,
            crossfadeEnabled = true,
            isTogetherActive = false,
            isCasting = false,
            repeatMode = Player.REPEAT_MODE_ALL,
            currentDurationMs = 180_000L,
            isSameAlbum = false
        )

        assertFalse(AutomixPolicy.isAutomixSupported(base.copy(automixEnabled = false)))
        assertFalse(AutomixPolicy.isAutomixSupported(base.copy(crossfadeEnabled = false)))
        assertFalse(AutomixPolicy.isAutomixSupported(base.copy(isTogetherActive = true)))
        assertFalse(AutomixPolicy.isAutomixSupported(base.copy(isCasting = true)))
        assertFalse(AutomixPolicy.isAutomixSupported(base.copy(repeatMode = Player.REPEAT_MODE_ONE)))
        assertFalse(AutomixPolicy.isAutomixSupported(base.copy(currentDurationMs = 500L)))
        assertFalse(AutomixPolicy.isAutomixSupported(base.copy(isSameAlbum = true)))
    }
}
