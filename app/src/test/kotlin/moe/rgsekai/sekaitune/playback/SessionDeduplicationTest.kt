/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback

import moe.rgsekai.sekaitune.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDeduplicationTest {

    private fun createMetadata(
        id: String,
        title: String,
        artistName: String,
        matchedCatalogId: String? = null
    ): MediaMetadata = MediaMetadata(
        id = id,
        title = title,
        artists = listOf(MediaMetadata.Artist(id = null, name = artistName)),
        duration = 180,
        matchedCatalogId = matchedCatalogId
    )

    @Test
    fun record_addsItemToHistory() {
        val deduplicator = SessionDeduplicator(maxCapacity = 10)
        val meta = createMetadata("song_1", "Blinding Lights", "The Weeknd")

        deduplicator.record(meta)

        assertEquals(1, deduplicator.size)
        assertEquals("song_1", deduplicator.getAll().first().id)
    }

    @Test
    fun record_ignoresDuplicateIdsAndMatchedCatalogIds() {
        val deduplicator = SessionDeduplicator(maxCapacity = 10)
        val meta1 = createMetadata("song_1", "Blinding Lights", "The Weeknd", matchedCatalogId = "cat_1")
        val meta2 = createMetadata("song_1", "Blinding Lights (Remix)", "The Weeknd")
        val meta3 = createMetadata("song_other", "Blinding Lights", "The Weeknd", matchedCatalogId = "cat_1")

        deduplicator.record(meta1)
        deduplicator.record(meta2)
        deduplicator.record(meta3)

        assertEquals(1, deduplicator.size)
    }

    @Test
    fun record_respectsCapacityLimit() {
        val deduplicator = SessionDeduplicator(maxCapacity = 3)

        for (i in 1..5) {
            deduplicator.record(createMetadata("song_$i", "Title $i", "Artist $i"))
        }

        assertEquals(3, deduplicator.size)
        val ids = deduplicator.getAll().map { it.id }
        assertEquals(listOf("song_3", "song_4", "song_5"), ids)
    }

    @Test
    fun isDuplicate_detectsExactIdInKnownQueue() {
        val deduplicator = SessionDeduplicator()
        val knownIds = setOf("song_known", "song_other")

        val result = deduplicator.isDuplicate(
            candidateId = "song_known",
            candidateTitle = "Any Song",
            candidateArtists = listOf("Any Artist"),
            knownIds = knownIds,
            checkSessionHistory = false
        )

        assertTrue(result)
    }

    @Test
    fun isDuplicate_detectsExactIdInSessionHistory() {
        val deduplicator = SessionDeduplicator()
        deduplicator.record(createMetadata("song_session_1", "Starboy", "The Weeknd"))

        val result = deduplicator.isDuplicate(
            candidateId = "song_session_1",
            candidateTitle = "Different Title",
            candidateArtists = listOf("Different Artist"),
            knownIds = emptySet(),
            checkSessionHistory = true
        )

        assertTrue(result)
    }

    @Test
    fun isDuplicate_detectsMatchedCatalogIdInSessionHistory() {
        val deduplicator = SessionDeduplicator()
        deduplicator.record(createMetadata("local_song_1", "Starboy", "The Weeknd", matchedCatalogId = "yt_starboy"))

        val result = deduplicator.isDuplicate(
            candidateId = "addon_song_2",
            candidateTitle = "Starboy",
            candidateArtists = listOf("The Weeknd"),
            candidateMatchedCatalogId = "yt_starboy",
            knownIds = emptySet(),
            checkSessionHistory = true
        )

        assertTrue(result)
    }

    @Test
    fun isDuplicate_detectsFuzzyTitleAndArtistTokens() {
        val deduplicator = SessionDeduplicator()
        deduplicator.record(createMetadata("yt_1", "Levitating (Official Music Video)", "Dua Lipa feat. DaBaby"))

        val isDup = deduplicator.isDuplicate(
            candidateId = "yt_different_id",
            candidateTitle = "levitating",
            candidateArtists = listOf("Dua Lipa"),
            knownIds = emptySet(),
            checkSessionHistory = true
        )

        assertTrue(isDup)
    }

    @Test
    fun isDuplicate_distinctSongsAreNotMarkedAsDuplicate() {
        val deduplicator = SessionDeduplicator()
        deduplicator.record(createMetadata("yt_1", "Levitating", "Dua Lipa"))

        val isDup = deduplicator.isDuplicate(
            candidateId = "yt_2",
            candidateTitle = "Don't Start Now",
            candidateArtists = listOf("Dua Lipa"),
            knownIds = emptySet(),
            checkSessionHistory = true
        )

        assertFalse(isDup)
    }

    @Test
    fun isDuplicate_ignoresSessionHistoryWhenCheckSessionHistoryFalse() {
        val deduplicator = SessionDeduplicator()
        deduplicator.record(createMetadata("yt_1", "Levitating", "Dua Lipa"))

        val isDup = deduplicator.isDuplicate(
            candidateId = "yt_1",
            candidateTitle = "Levitating",
            candidateArtists = listOf("Dua Lipa"),
            knownIds = emptySet(),
            checkSessionHistory = false
        )

        assertFalse(isDup)
    }

    @Test
    fun clear_resetsHistory() {
        val deduplicator = SessionDeduplicator()
        deduplicator.record(createMetadata("song_1", "Title", "Artist"))
        assertEquals(1, deduplicator.size)

        deduplicator.clear()

        assertEquals(0, deduplicator.size)
        assertTrue(deduplicator.getAll().isEmpty())
    }
}
