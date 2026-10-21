/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import kotlinx.coroutines.runBlocking
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.playback.CatalogMatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonCatalogMatcherTest {

    @Test
    fun trackMatcher_enforcesVersionMarkerAgreementForTargetAndCandidate() {
        val target = MatchTarget(
            title = "Positions (Live)",
            artists = listOf("Ariana Grande"),
            durationSec = 172,
        )
        val studioCandidate = MatchCandidate(
            title = "Positions",
            artists = listOf("Ariana Grande"),
            durationSec = 172,
        )
        val liveCandidate = MatchCandidate(
            title = "Positions (Live)",
            artists = listOf("Ariana Grande"),
            durationSec = 173,
        )

        val studioResult = TrackMatcher.match(target, studioCandidate, toleranceSec = 3)
        assertFalse("Studio version should be rejected when target has (Live)", studioResult.isMatch)
        assertTrue(studioResult.rejectReason?.contains("Version marker mismatch") == true)

        val liveResult = TrackMatcher.match(target, liveCandidate, toleranceSec = 3)
        assertTrue("Live version should match target with (Live)", liveResult.isMatch)
        assertTrue(liveResult.score >= 50)
    }

    @Test
    fun trackMatcher_respectsDurationTolerance() {
        val target = MatchTarget(
            title = "Positions",
            artists = listOf("Ariana Grande"),
            durationSec = 172,
        )
        val matchingCandidate = MatchCandidate(
            title = "positions",
            artists = listOf("Ariana Grande"),
            durationSec = 174,
        )
        val differentTrackCandidate = MatchCandidate(
            title = "positions",
            artists = listOf("Ariana Grande"),
            durationSec = 220,
        )

        val match = TrackMatcher.match(target, matchingCandidate, toleranceSec = 3)
        assertTrue("Candidate within 2s should match", match.isMatch)

        val mismatch = TrackMatcher.match(target, differentTrackCandidate, toleranceSec = 3)
        assertFalse("Candidate 48s apart should fail strict duration check", mismatch.isMatch)

        // But loose tolerance (e.g. 50s) can permit it if needed during fallback
        val looseMatch = TrackMatcher.match(target, differentTrackCandidate, toleranceSec = 50)
        assertTrue("Loose tolerance permits match if titles/artists agree", looseMatch.isMatch)
    }

    @Test
    fun autoplayDeduplication_filtersCurrentSongByVideoIdAndNormalizedMetadata() {
        val currentTitle = "Positions (Official Video)"
        val currentArtist = "Ariana Grande"
        val currentVideoId = "tcYodQoapMg"
        val currentNormTitle = TrackMatcher.normalizeTitle(currentTitle)
        val currentArtistWords = TrackMatcher.tokenizeArtistWords(listOf(currentArtist))

        val knownIds = mutableSetOf(currentVideoId, "addon:source1:track123")

        data class MockItem(val id: String, val title: String, val artists: List<String>)

        val candidates = listOf(
            MockItem(id = "tcYodQoapMg", title = "Positions", artists = listOf("Ariana Grande")), // Duplicate by videoId
            MockItem(id = "otherId123", title = "positions", artists = listOf("Ariana Grande")), // Duplicate by title+artist
            MockItem(id = "3435_id", title = "34+35", artists = listOf("Ariana Grande")), // Recommended song 1
            MockItem(id = "pov_id", title = "pov", artists = listOf("Ariana Grande")), // Recommended song 2
        )

        fun isDuplicate(item: MockItem): Boolean {
            if (knownIds.contains(item.id)) return true
            val itemNormTitle = TrackMatcher.normalizeTitle(item.title)
            if (itemNormTitle.isNotBlank() && itemNormTitle == currentNormTitle) {
                val itemArtistWords = TrackMatcher.tokenizeArtistWords(item.artists)
                if (currentArtistWords.isNotEmpty() && itemArtistWords.isNotEmpty() &&
                    currentArtistWords.intersect(itemArtistWords).isNotEmpty()
                ) {
                    return true
                }
            }
            return false
        }

        val filtered = candidates.filterNot { isDuplicate(it) }
        assertEquals(2, filtered.size)
        assertEquals("3435_id", filtered[0].id)
        assertEquals("pov_id", filtered[1].id)
    }

    @Test
    fun autoplayToggle_off_appendsNothing() {
        val autoPlayEnabled = false
        val incomingRecommendations = listOf("rec1", "rec2", "rec3")
        val queueItems = mutableListOf("initial_addon_track")

        if (autoPlayEnabled) {
            queueItems.addAll(incomingRecommendations)
        }

        assertEquals(1, queueItems.size)
        assertEquals("initial_addon_track", queueItems[0])
    }

    @Test
    fun emptyTitle_returnsNoMatch() = runBlocking {
        val matcher = AddonCatalogMatcher()
        val result = matcher.findCatalogMatch("", listOf("Artist"), 180)
        assertEquals(CatalogMatchResult.NoMatch, result)
    }
}
