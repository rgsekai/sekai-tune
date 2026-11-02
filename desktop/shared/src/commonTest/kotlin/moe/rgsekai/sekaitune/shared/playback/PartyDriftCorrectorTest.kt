package moe.rgsekai.sekaitune.shared.playback

import moe.rgsekai.sekaitune.shared.data.listentogether.PartyPlayback
import moe.rgsekai.sekaitune.shared.data.listentogether.PartyTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * When the playhead gets moved to follow the party, and where to.
 *
 * A control aligns at once and tightly; a seek is checked once it settles and the lag it showed is
 * added to the next; drift between controls has to be persistent; and a device that cannot land
 * is left alone rather than seeked on a loop.
 */
class PartyDriftCorrectorTest {

    private val song = PartyTrack(videoId = "a", title = "A", artist = "Artist")
    private fun control(positionMs: Long, anchorMs: Long = 0, seq: Long = 1) =
        PartyPlayback(seq = seq, track = song, isPlaying = true, positionMs = positionMs, anchorMs = anchorMs)

    private val settle = PartySyncLogic.SEEK_SETTLE_MS

    @Test
    fun `a new control aligns at once when it is out, and not when it lines up`() {
        assertEquals(10_000L, PartyDriftCorrector().correct(10_300, 10_000, control(10_000), 0))
        assertNull(PartyDriftCorrector().correct(10_040, 10_000, control(10_000), 0))
    }

    @Test
    fun `a seq bumped without the playhead moving is not a control`() {
        val c = PartyDriftCorrector()
        assertNull(c.correct(10_040, 10_000, control(10_000, seq = 1), 0))
        assertNull(c.correct(10_200, 10_000, control(10_000, seq = 2), 100), "same playhead: inside the drift limit")
    }

    @Test
    fun `the lag a seek showed is learned and led into the next`() {
        val c = PartyDriftCorrector()
        val target = control(10_000)
        assertEquals(10_000L, c.correct(9_000, 10_000, target, 0))
        assertNull(c.correct(11_000, 11_300, target, settle - 1), "nothing is measured before the seek settles")
        // Landed 300ms behind: seeks on this device cost 300ms, and the retry is aimed that far ahead.
        assertEquals(11_800L, c.correct(11_200, 11_500, target, settle))
        assertEquals(300L, c.seekLagMs)
        assertNull(c.correct(13_010, 13_000, target, settle * 2), "landed: nothing more to do")
        assertEquals(290L, c.seekLagMs)
    }

    @Test
    fun `small drift is left alone, and one large reading is not enough`() {
        val c = PartyDriftCorrector()
        val target = control(10_000)
        assertNull(c.correct(10_000, 10_000, target, 0))
        assertNull(c.correct(10_000 + PartySyncLogic.DRIFT_LIMIT_MS, 10_000, target, 700))
        assertNull(c.correct(15_000, 10_000, target, 1_400), "a single stall should not cause an audible seek")
        assertNotNull(c.correct(5_000, 10_000, target, 2_100), "behind is as wrong as ahead, and twice is real")
    }

    @Test
    fun `a device that cannot land is left alone after a few tries`() {
        val c = PartyDriftCorrector()
        val target = control(10_000)
        var now = 0L
        assertNotNull(c.correct(0, 10_000, target, now))
        repeat(PartySyncLogic.MAX_ALIGN_ATTEMPTS - 1) {
            now += settle
            assertNotNull(c.correct(0, 10_000, target, now), "retry ${it + 1}")
        }
        now += settle
        assertNull(c.correct(0, 10_000, target, now), "out of attempts")
        assertNull(c.correct(0, 10_000, target, now + 700), "and held off for the cooldown")
        assertNull(c.correct(0, 10_000, target, now + PartySyncLogic.DRIFT_COOLDOWN_MS), "first strike after it")
        assertNotNull(c.correct(0, 10_000, target, now + PartySyncLogic.DRIFT_COOLDOWN_MS + 700))
    }

    @Test
    fun `a start is aimed with the learned lag and checked like any seek`() {
        val c = PartyDriftCorrector()
        val target = control(10_000)
        c.correct(9_000, 10_000, target, 0)
        c.correct(11_200, 11_500, target, settle)
        assertEquals(20_300L, c.startAt(20_000, control(20_000, seq = 2), 10_000))
        assertNull(c.correct(21_000, 21_000, control(20_000, seq = 2), 10_000 + settle))
    }

    @Test
    fun `a stale reading is carried forward to now while playing`() {
        assertEquals(10_250L, PartySyncLogic.livePositionMs(10_000, 1_000_000_000, 1_250_000_000, playing = true))
        assertEquals(10_000L, PartySyncLogic.livePositionMs(10_000, 1_000_000_000, 1_250_000_000, playing = false))
    }
}
