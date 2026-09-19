/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasPlaybackWatchdogTest {

    @Test
    fun testNormalPlaybackNeverTriggersStall() {
        var stallCount = 0
        val watchdog = CanvasPlaybackWatchdog(
            stallTimeoutMs = 5000L,
            checkIntervalMs = 1000L,
            onStallDetected = { _, _ -> stallCount++ },
        )

        // Simulate 10 seconds of active playback with advancing positions
        for (i in 0..10) {
            watchdog.tick(
                currentPositionMs = i * 1000L,
                isBufferingOrReady = true,
                playWhenReady = true,
            )
            assertEquals("Stall must not trigger during normal progression", 0, stallCount)
            assertEquals("Stall timer must be 0", 0L, watchdog.stalledForMs)
        }
    }

    @Test
    fun testLoopBoundariesDoNotTriggerFalsePositiveStall() {
        var stallCount = 0
        val watchdog = CanvasPlaybackWatchdog(
            stallTimeoutMs = 5000L,
            checkIntervalMs = 1000L,
            onStallDetected = { _, _ -> stallCount++ },
        )

        // Simulate 4 complete loops of a 4-second canvas video clip (0 -> 1000 -> 2000 -> 3000 -> 4000 -> loop -> 100 -> ...)
        val timelinePositions = listOf(
            0L, 1000L, 2000L, 3000L, 4000L, // Loop 1
            100L, 1100L, 2100L, 3100L, 4000L, // Loop 2 (wrap around)
            80L, 1050L, 2050L, 3050L, 4000L, // Loop 3 (wrap around)
            120L, 1120L, 2120L, 3120L, 4000L, // Loop 4 (wrap around)
        )

        for (pos in timelinePositions) {
            watchdog.tick(
                currentPositionMs = pos,
                isBufferingOrReady = true,
                playWhenReady = true,
            )
            assertEquals("Stall must not trigger during loop boundaries at pos=$pos", 0, stallCount)
            assertEquals("Stall accumulator must remain 0 on loop progress", 0L, watchdog.stalledForMs)
        }
    }

    @Test
    fun testLoopDiscontinuityEventResetsWatchdog() {
        var stallCount = 0
        val watchdog = CanvasPlaybackWatchdog(
            stallTimeoutMs = 5000L,
            checkIntervalMs = 1000L,
            onStallDetected = { _, _ -> stallCount++ },
        )

        watchdog.tick(4000L, isBufferingOrReady = true, playWhenReady = true)
        // ExoPlayer loop discontinuity fires:
        watchdog.onPositionDiscontinuity(0L)
        assertEquals(0L, watchdog.lastPositionMs)
        assertEquals(0L, watchdog.stalledForMs)

        watchdog.tick(1000L, isBufferingOrReady = true, playWhenReady = true)
        assertEquals(0, stallCount)
    }

    @Test
    fun testGenuinePlaybackFreezeTriggersStall() {
        var stallCount = 0
        var detectedStallMs = 0L
        var detectedPositionMs = -1L

        val watchdog = CanvasPlaybackWatchdog(
            stallTimeoutMs = 5000L,
            checkIntervalMs = 1000L,
            onStallDetected = { stalledMs, posMs ->
                stallCount++
                detectedStallMs = stalledMs
                detectedPositionMs = posMs
            },
        )

        // Progress up to 2500ms
        watchdog.tick(1000L, isBufferingOrReady = true, playWhenReady = true)
        watchdog.tick(2500L, isBufferingOrReady = true, playWhenReady = true)
        assertEquals(0, stallCount)

        // Stream freezes at 2500ms for 5 ticks (5000ms)
        for (i in 1..4) {
            watchdog.tick(2500L, isBufferingOrReady = true, playWhenReady = true)
            assertEquals("Stall must not trigger before timeout (tick $i)", 0, stallCount)
            assertEquals(i * 1000L, watchdog.stalledForMs)
        }

        // 5th tick reaches 5000ms timeout
        watchdog.tick(2500L, isBufferingOrReady = true, playWhenReady = true)
        assertEquals("Stall must trigger when frozen for >= 5000ms", 1, stallCount)
        assertEquals(5000L, detectedStallMs)
        assertEquals(2500L, detectedPositionMs)
        assertEquals("Watchdog must reset accumulator after triggering stall recovery", 0L, watchdog.stalledForMs)
    }

    @Test
    fun testPausedStateDoesNotAccumulateStall() {
        var stallCount = 0
        val watchdog = CanvasPlaybackWatchdog(
            stallTimeoutMs = 5000L,
            checkIntervalMs = 1000L,
            onStallDetected = { _, _ -> stallCount++ },
        )

        watchdog.tick(1000L, isBufferingOrReady = true, playWhenReady = true)

        // User paused playback: playWhenReady = false
        for (i in 1..10) {
            watchdog.tick(1000L, isBufferingOrReady = true, playWhenReady = false)
            assertEquals("Paused playback must never trigger stall", 0, stallCount)
            assertEquals(0L, watchdog.stalledForMs)
        }
    }
}
