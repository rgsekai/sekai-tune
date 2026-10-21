/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherPositionsTest {

    @Test
    fun testCleanTitleForSearch_stripsPackagingAndArtistPrefix() {
        val clean1 = TrackMatcher.cleanTitleForSearch("positions (Official Video)", "Ariana Grande")
        assertEquals("positions", clean1)

        val clean2 = TrackMatcher.cleanTitleForSearch("positions [Explicit]", "Ariana Grande")
        assertEquals("positions", clean2)

        val clean3 = TrackMatcher.cleanTitleForSearch("Ariana Grande - positions (Official Music Video)", "Ariana Grande")
        assertEquals("positions", clean3)

        val clean4 = TrackMatcher.cleanTitleForSearch("positions (Lyric Video) [4K]", "Ariana Grande")
        assertEquals("positions", clean4)
    }

    @Test
    fun testBuildAddonSearchQuery_avoidsDuplicateArtistAndPackaging() {
        val query1 = TrackMatcher.buildAddonSearchQuery("positions (Official Video)", listOf("Ariana Grande"))
        assertEquals("positions Ariana Grande", query1)

        val query2 = TrackMatcher.buildAddonSearchQuery("Ariana Grande - positions [Explicit]", listOf("Ariana Grande"))
        assertEquals("positions Ariana Grande", query2)

        // If artist is already in title, do not duplicate
        val query3 = TrackMatcher.buildAddonSearchQuery("positions Ariana Grande", listOf("Ariana Grande"))
        assertEquals("positions Ariana Grande", query3)
    }

    @Test
    fun testMatchPositions_targetOfficialVideoMatchesStudioCandidate() {
        val target = MatchTarget(
            title = "positions (Official Video)",
            artists = listOf("Ariana Grande"),
            durationSec = 172,
        )
        val candidate = MatchCandidate(
            title = "positions",
            artists = listOf("Ariana Grande"),
            durationSec = 172,
        )

        val result = TrackMatcher.match(target, candidate, toleranceSec = 3)
        assertTrue("Official Video target should match studio candidate", result.isMatch)
        assertTrue("Match score should be high (>= 80), was ${result.score}", result.score >= 80)
    }

    @Test
    fun testMatch_caseInsensitive() {
        val target = MatchTarget(
            title = "POSITIONS",
            artists = listOf("ARIANA GRANDE"),
            durationSec = 172,
        )
        val candidate = MatchCandidate(
            title = "positions",
            artists = listOf("ariana grande"),
            durationSec = 172,
        )

        val result = TrackMatcher.match(target, candidate, toleranceSec = 3)
        assertTrue("Matching should be case-insensitive", result.isMatch)
    }

    @Test
    fun testMatch_diacriticInsensitive() {
        val target = MatchTarget(
            title = "Beyoncé - CUFF IT",
            artists = listOf("Beyoncé"),
            durationSec = 225,
        )
        val candidate = MatchCandidate(
            title = "CUFF IT",
            artists = listOf("Beyonce"),
            durationSec = 225,
        )

        val result = TrackMatcher.match(target, candidate, toleranceSec = 3)
        assertTrue("Matching should be diacritic-insensitive", result.isMatch)
    }

    @Test
    fun testMatch_durationTolerance() {
        val target = MatchTarget(
            title = "positions",
            artists = listOf("Ariana Grande"),
            durationSec = 172,
        )

        val candidateWithin = MatchCandidate(
            title = "positions",
            artists = listOf("Ariana Grande"),
            durationSec = 174, // delta = 2s <= 3s
        )
        val resultWithin = TrackMatcher.match(target, candidateWithin, toleranceSec = 3)
        assertTrue("Should match within 3s tolerance", resultWithin.isMatch)

        val candidateOutside = MatchCandidate(
            title = "positions",
            artists = listOf("Ariana Grande"),
            durationSec = 177, // delta = 5s > 3s
        )
        val resultOutside = TrackMatcher.match(target, candidateOutside, toleranceSec = 3)
        assertFalse("Should reject when delta exceeds tolerance", resultOutside.isMatch)
        assertTrue("Reason should mention duration delta", resultOutside.rejectReason?.contains("Duration delta") == true)
    }
}
