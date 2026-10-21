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

class TrackMatcherTest {

    @Test
    fun testVersionMarkers_rejectsMismatch() {
        // Original vs Remix
        val targetOriginal = MatchTarget(title = "Blinding Lights", artists = listOf("The Weeknd"), durationSec = 200)
        val candidateRemix = MatchCandidate(title = "Blinding Lights (Remix)", artists = listOf("The Weeknd"), durationSec = 200)

        val result1 = TrackMatcher.match(targetOriginal, candidateRemix)
        assertFalse("Expected mismatch when candidate is a remix and target is original", result1.isMatch)
        assertTrue(result1.rejectReason?.contains("Version marker mismatch") == true)

        // Live vs Original
        val targetLive = MatchTarget(title = "Hotel California - Live", artists = listOf("Eagles"), durationSec = 390)
        val candidateStudio = MatchCandidate(title = "Hotel California", artists = listOf("Eagles"), durationSec = 390)

        val result2 = TrackMatcher.match(targetLive, candidateStudio)
        assertFalse("Expected mismatch when target is live and candidate is studio", result2.isMatch)
        assertTrue(result2.rejectReason?.contains("Version marker mismatch") == true)

        // Matching version markers (Acoustic vs Acoustic)
        val targetAcoustic = MatchTarget(title = "Everlong (Acoustic)", artists = listOf("Foo Fighters"), durationSec = 250)
        val candidateAcoustic = MatchCandidate(title = "Everlong - Acoustic Version", artists = listOf("Foo Fighters"), durationSec = 252)

        val result3 = TrackMatcher.match(targetAcoustic, candidateAcoustic, toleranceSec = 3)
        assertTrue("Expected match when both tracks have acoustic version marker", result3.isMatch)
    }

    @Test
    fun testArtistOverlap_requiresWholeWordOverlap() {
        // Multi-artist collaboration overlap
        val targetCollab = MatchTarget(title = "Ninelie", artists = listOf("Aimer", "chelly"), durationSec = 260)
        val candidateSolo = MatchCandidate(title = "Ninelie", artists = listOf("Aimer"), durationSec = 260)

        val resultCollab = TrackMatcher.match(targetCollab, candidateSolo)
        assertTrue("Expected match when one of the collaborating artists matches", resultCollab.isMatch)

        // Whole-word vs substring: "Aimer" must NOT match "Claimer"
        val targetAimer = MatchTarget(title = "Brave Shine", artists = listOf("Aimer"), durationSec = 230)
        val candidateClaimer = MatchCandidate(title = "Brave Shine", artists = listOf("Claimer"), durationSec = 230)

        val resultClaimer = TrackMatcher.match(targetAimer, candidateClaimer)
        assertFalse("Aimer must not match Claimer (substring violation)", resultClaimer.isMatch)
        assertTrue(resultClaimer.rejectReason?.contains("Artist mismatch") == true)
    }

    @Test
    fun testDurationTolerance_enforcesBoundary() {
        val target = MatchTarget(title = "Shape of You", artists = listOf("Ed Sheeran"), durationSec = 233)

        // Within default 3s tolerance (delta = 2s)
        val candidateClose = MatchCandidate(title = "Shape of You", artists = listOf("Ed Sheeran"), durationSec = 235)
        val resultClose = TrackMatcher.match(target, candidateClose, toleranceSec = 3)
        assertTrue("Should match within 3s tolerance", resultClose.isMatch)

        // Exceeds 3s tolerance (delta = 5s)
        val candidateFar = MatchCandidate(title = "Shape of You", artists = listOf("Ed Sheeran"), durationSec = 238)
        val resultFar = TrackMatcher.match(target, candidateFar, toleranceSec = 3)
        assertFalse("Should be rejected when delta 5s exceeds tolerance 3s", resultFar.isMatch)
        assertTrue(resultFar.rejectReason?.contains("Duration delta 5s exceeds tolerance of 3s") == true)

        // With user tolerance adjusted to 6s, 5s delta passes!
        val resultWithHigherTolerance = TrackMatcher.match(target, candidateFar, toleranceSec = 6)
        assertTrue("Should match when tolerance is expanded to 6s", resultWithHigherTolerance.isMatch)
    }

    @Test
    fun testJapaneseTitleMatching_bridgesKanaAndRomaji() {
        // Katakana vs Romaji
        val targetKatakana = MatchTarget(title = "コイコガレ", artists = listOf("milet", "MAN WITH A MISSION"), durationSec = 216)
        val candidateRomaji = MatchCandidate(title = "koi kogare", artists = listOf("milet", "MAN WITH A MISSION"), durationSec = 216)

        val resultKatakana = TrackMatcher.match(targetKatakana, candidateRomaji)
        assertTrue("Expected Japanese Katakana to match Romaji representation", resultKatakana.isMatch)

        // Hiragana vs Romaji
        val targetHiragana = MatchTarget(title = "よるにかける", artists = listOf("YOASOBI"), durationSec = 261)
        val candidateRomaji2 = MatchCandidate(title = "yoru ni kakeru", artists = listOf("YOASOBI"), durationSec = 260)

        val resultHiragana = TrackMatcher.match(targetHiragana, candidateRomaji2, toleranceSec = 3)
        assertTrue("Expected Japanese Hiragana to match Romaji representation", resultHiragana.isMatch)
    }

    @Test
    fun testPackagingWordsStripped() {
        val target = MatchTarget(title = "Idol", artists = listOf("YOASOBI"), durationSec = 213)
        val candidate = MatchCandidate(title = "Idol (Official Music Video) [HD]", artists = listOf("YOASOBI"), durationSec = 213)

        val result = TrackMatcher.match(target, candidate)
        assertTrue("Expected match after stripping packaging words (Official Music Video, HD)", result.isMatch)
    }
}
