package moe.rgsekai.sekaitune.desktop

import moe.rgsekai.sekaitune.desktop.DesktopPartySync.Companion.queueForPartyPublish
import moe.rgsekai.sekaitune.shared.data.listentogether.PartyMember
import moe.rgsekai.sekaitune.shared.data.listentogether.PartyTrack
import moe.rgsekai.sekaitune.shared.data.model.QueueTier
import moe.rgsekai.sekaitune.shared.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the desktop shares with a party. When the playhead moves is `PartyDriftCorrectorTest`'s, in :shared. */
class DesktopPartySyncTest {

    @Test
    fun `party queue publish preserves current autoplay and drops only context tail`() {
        val past = Song("past", "Past", "Artist", null, queueTier = QueueTier.CONTEXT)
        val current = Song("current", "Current", "Artist", null, queueTier = QueueTier.AUTOPLAY)
        val contextTail = Song("context", "Context", "Artist", null, queueTier = QueueTier.CONTEXT)
        val autoplayTail = Song("auto", "Auto", "Artist", null, queueTier = QueueTier.AUTOPLAY)

        val (published, index) = queueForPartyPublish(
            songs = listOf(past, current, contextTail, autoplayTail),
            currentIndex = 1,
            currentVideoId = current.videoId,
            currentDurationMs = 123_000L,
        )

        assertEquals(listOf("past", "current", "auto"), published.map(PartyTrack::videoId))
        assertEquals(1, index)
        assertTrue(published[1].fromAutoplay, "the current AutoPlay item must remain marked")
        assertTrue(published[2].fromAutoplay, "the AutoPlay tail must be sent to the server")
        assertEquals(123_000L, published[1].durationMs)
    }

    @Test
    fun `party queue publish caps upcoming songs like the phone`() {
        val songs = listOf(Song("current", "Current", "Artist", null)) +
            (1..40).map {
                Song("next-$it", "Next $it", "Artist", null, queueTier = QueueTier.USER_QUEUE)
            }

        val (published, index) = queueForPartyPublish(
            songs = songs,
            currentIndex = 0,
            currentVideoId = "current",
            currentDurationMs = 1L,
        )

        assertEquals(26, published.size, "current plus 25 upcoming songs")
        assertEquals(0, index)
        assertEquals("next-25", published.last().videoId)
    }

    @Test
    fun `autoplay supplier election matches the phone`() {
        val host = PartyMember("host", isHost = true, connected = true)
        val member = PartyMember("a-member", connected = true)
        val you = PartyMember("you", connected = true)

        assertEquals(
            "host",
            DesktopAutoplay.supplierId(
                DesktopListenTogether.State(code = "party", you = you, members = listOf(member, host, you)),
            ),
        )
        assertEquals(
            "a-member",
            DesktopAutoplay.supplierId(
                DesktopListenTogether.State(code = "party", you = you, members = listOf(member, you)),
            ),
            "an unlocked party falls back deterministically after the host disconnects",
        )
        assertEquals(
            null,
            DesktopAutoplay.supplierId(
                DesktopListenTogether.State(
                    code = "party",
                    you = you,
                    members = listOf(member, you),
                    hostOnlyControl = true,
                ),
            ),
            "a host-only party waits for its host rather than electing a guest",
        )
    }

    @Test
    fun `autoplay rearms only when the current song is the empty tail`() {
        assertTrue(
            DesktopAutoplay.queueNeedsRefresh(
                enabled = true,
                repeatAll = false,
                currentIndex = 2,
                itemCount = 3,
                loadInProgress = false,
            ),
        )
        assertFalse(
            DesktopAutoplay.queueNeedsRefresh(
                enabled = true,
                repeatAll = false,
                currentIndex = 1,
                itemCount = 3,
                loadInProgress = false,
            ),
        )
        assertFalse(
            DesktopAutoplay.queueNeedsRefresh(
                enabled = true,
                repeatAll = false,
                currentIndex = 2,
                itemCount = 3,
                loadInProgress = true,
            ),
        )
    }

    @Test
    fun `party queue conversion preserves autoplay boundary`() {
        val autoplay = PartyTrack("next", "Next", "Artist", fromAutoplay = true).toDesktopSong()
        assertEquals(QueueTier.AUTOPLAY, autoplay.queueTier)

        val shared = Song("id", "Title", "Artist", null, queueTier = QueueTier.AUTOPLAY)
            .toPartyTrack(durationMs = 123_000L)
        assertTrue(shared.fromAutoplay)
        assertEquals(123_000L, shared.durationMs)
    }
}
