package moe.rgsekai.sekaitune.shared.playback.smart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransitionTempoTest {

    @Test
    fun `beat grid and ramp steps match what the Android controller computed`() {
        val out = ArrayList<Double>()
        val analyses = listOf(
            TrackAnalysis(bpm = 124.0, beatInterval = 0.4839, firstBeat = 0.21, downbeats = (0 until 40).map { 0.21 + it * 1.9357 + (it % 3) * 0.002 }),
            TrackAnalysis(bpm = 90.0, firstBeat = 0.5),
            TrackAnalysis(bpm = 128.0, beatInterval = 5.0, downbeats = listOf(1.0, 30.0)),
            TrackAnalysis(),
        )
        val beat = BeatPhase()
        for (a in analyses) for (i in 0..300) {
            val ok = beat.at(a, i * 0.37)
            out.add(if (ok) 1.0 else 0.0)
            if (ok) {
                out.add(beat.sinceBeat)
                out.add(beat.beatSeconds)
            }
        }
        for (r in listOf(1.0, 1.0001, 1.004, 1.03, 1.12, 0.9, 0.97, 1.0075, 1.0076)) out.add(rampStepsFor(r).toDouble())
        var h = 0L
        for (v in out) h = (h xor v.toRawBits()) * 0x100000001b3L
        assertEquals(3019, out.size)
        assertEquals(2718551500189920512L, h)
    }

    @Test
    fun `a 2 percent stretch ramps in 3 beats and a 10 percent one in 14`() {
        assertEquals(3, rampStepsFor(1.02))
        assertEquals(14, rampStepsFor(1.10))
        assertEquals(0, rampStepsFor(1.0))
    }

    @Test
    fun `the outgoing ramp climbs one step per beat and lands on the target`() {
        val ramp = TempoRamp()
        // The blend ends at 60s after a 10s fade; 3 beats of 500ms are spent getting up to speed first.
        ramp.begin(outgoingRate = 1.02, beatMs = 500, endMs = 60_000, fadeMs = 10_000)
        assertEquals(3, ramp.steps)
        assertTrue(ramp.due(47_999, 1.02).isNaN(), "nothing before the ramp starts")
        assertEquals(1.0 + 0.02 / 3, ramp.due(48_500, 1.02), 1e-12)
        assertTrue(ramp.due(48_600, 1.02).isNaN(), "one step a beat")
        assertEquals(1.02, ramp.due(50_000, 1.02), 1e-12)
        assertFalse(ramp.pending)
    }

    @Test
    fun `the ease walks back to the listener's speed one step per beat`() {
        val ease = TempoEase()
        val analysis = TrackAnalysis(bpm = 120.0)
        assertTrue(ease.begin(1.02, analysis, positionMs = 0))
        assertEquals(500, ease.stepMs)
        var steps = 0
        var position = 0L
        while (true) {
            position += 500
            val next = ease.step(position)
            if (next == TempoEase.DONE) break
            if (!next.isNaN()) steps++
            assertTrue(steps < 10)
        }
        // Three steps in all, the last of which lands on 1.0 and is reported as done.
        assertEquals(2, steps)
        assertFalse(ease.begin(1.00001, analysis, 0))
    }

    private fun phaseOf(
        lock: PhaseLock, now: Long, progress: Float, error: Double, base: Float = 1f,
    ) = lock.update(now, progress, outPlaying = true, inPlaying = true, base = base, incomingPlaybackRate = 1.0, errorSeconds = error)

    @Test
    fun `phase lock waits out the start, takes the median of five and pulses the speed`() {
        val lock = PhaseLock()
        lock.start(0)
        // Inside the settle window nothing is read.
        assertTrue(phaseOf(lock, 100, 0.1f, 0.05).isNaN())
        var now = 400L
        // Four readings gather; the fifth decides. The incoming beat is 40ms ahead: slow it down.
        repeat(4) { assertTrue(phaseOf(lock, now, 0.2f, 0.04).isNaN()); now += 30 }
        val speed = phaseOf(lock, now, 0.2f, 0.04)
        assertEquals(1f - NUDGE, speed, 1e-6f)
        assertTrue(lock.nudging)
        // The pulse lasts error / (base * nudge) = 0.04 / 0.03 s ≈ 1333ms; then the speed is put back.
        assertTrue(phaseOf(lock, now + 1000, 0.3f, 0.0).isNaN())
        assertEquals(1f, phaseOf(lock, now + 1400, 0.3f, 0.0), 1e-6f)
        assertFalse(lock.nudging)
    }

    @Test
    fun `phase lock leaves two beats within the deadband alone and never chases a grid disagreement`() {
        val lock = PhaseLock()
        lock.start(0)
        var now = 1000L
        repeat(5) { assertTrue(phaseOf(lock, now, 0.2f, 0.003).isNaN()); now += 30 }
        assertFalse(lock.nudging)
        // A NaN error (grids too far apart) is never collected.
        repeat(10) { assertTrue(phaseOf(lock, now + 2000, 0.2f, Double.NaN).isNaN()); now += 30 }
        // Past 90% of the blend the outgoing beat no longer matters.
        repeat(6) { assertTrue(phaseOf(lock, now + 4000, 0.95f, 0.05).isNaN()); now += 30 }
    }

    @Test
    fun `speeding up is capped under the total ceiling`() {
        val lock = PhaseLock()
        lock.start(0)
        var now = 1000L
        var speed = Float.NaN
        repeat(5) {
            speed = lock.update(now, 0.2f, true, true, base = 1.1f, incomingPlaybackRate = 1.1, errorSeconds = -0.05)
            now += 30
        }
        // 1.12 / 1.1 = 1.018: the nudge is cut from 3% to what leaves room under the ceiling.
        assertEquals(1.1f * (1.12 / 1.1).toFloat(), speed, 1e-5f)
    }

    @Test
    fun `phase error reads the shared pulse and ignores grids that disagree`() {
        val lock = PhaseLock()
        val a = TrackAnalysis(bpm = 120.0, beatInterval = 0.5, firstBeat = 0.0)
        // Incoming 40ms after its beat, outgoing on its beat: ahead by 40ms.
        assertEquals(0.04, lock.error(a, 10.0, a, 20.04, 1.0), 1e-9)
        // Behind by 40ms.
        assertEquals(-0.04, lock.error(a, 10.04, a, 20.0, 1.0), 1e-9)
        // Half a beat apart is two grids disagreeing, not a start that landed late.
        assertTrue(lock.error(a, 10.0, a, 20.25, 1.0).isNaN())
        assertTrue(lock.error(TrackAnalysis(), 10.0, a, 20.0, 1.0).isNaN())
    }

    @Test
    fun `position clock runs on between reports at the player's speed`() {
        val clock = PositionClock(halfTickNanos = 15_000_000L)
        val player = Any()
        assertEquals(1000.0, clock.read(player, 1000, true, 1f, 0L), 1e-9)
        assertEquals(1100.0, clock.read(player, 1000, true, 1f, 100_000_000L), 1e-6)
        assertEquals(1200.0, clock.read(player, 1000, true, 2f, 100_000_000L), 1e-6)
        // A new report is taken as half a tick old.
        assertEquals(1515.0, clock.read(player, 1500, true, 1f, 200_000_000L), 1e-6)
        assertEquals(2000.0, clock.read(player, 2000, false, 1f, 900_000_000L), 1e-9)
    }
}
