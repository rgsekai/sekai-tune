package moe.rgsekai.sekaitune.desktopui.player

import moe.rgsekai.sekaitune.shared.data.model.QueueTier
import moe.rgsekai.sekaitune.shared.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueKeyUniquenessTest {
    private fun song(id: String, entry: String?, tier: QueueTier = QueueTier.AUTOPLAY) =
        Song(videoId = id, title = id, artist = "Artist", thumbnailUrl = null, queueTier = tier, queueEntryId = entry)

    @Test
    fun `rows sharing an entry id still get distinct keys`() {
        val queue = listOf(
            song("now", "np", QueueTier.CONTEXT),
            song("a", "dup"),
            song("b", "dup"),
            song("c", "dup"),
        )
        val keys = splitQueue(queue, 0).autoplay.map { it.key }
        assertEquals(3, keys.toSet().size)
        assertEquals("autoplay_dup", keys.first())
    }

    @Test
    fun `unique entry ids keep their plain keys`() {
        val queue = listOf(song("now", "np", QueueTier.CONTEXT), song("a", "x"), song("b", "y"))
        assertEquals(listOf("autoplay_x", "autoplay_y"), splitQueue(queue, 0).autoplay.map { it.key })
    }

    @Test
    fun `keys are unique across the whole split`() {
        val queue = listOf(song("now", "e", QueueTier.CONTEXT), song("a", "e"), song("b", "e"))
        val tracks = splitQueue(queue, 0, showHistory = true)
        val all = listOfNotNull(tracks.nowPlaying) + tracks.history + tracks.user + tracks.context + tracks.autoplay
        assertTrue(all.map { it.key }.let { it.size == it.toSet().size })
    }
}
