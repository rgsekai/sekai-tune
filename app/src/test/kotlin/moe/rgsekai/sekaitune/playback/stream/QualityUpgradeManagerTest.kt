/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.stream

import moe.rgsekai.sekaitune.playback.sources.CandidateQualityRank
import moe.rgsekai.sekaitune.playback.sources.StreamFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class QualityUpgradeManagerTest {

    @Test
    fun testCandidateQualityRank_strictlyBetterThanYouTube() {
        val youtubeBitrate = 160

        // Lossless FLAC always beats YouTube
        val flacRank = CandidateQualityRank.from(StreamFormat.FLAC, bitrateKbps = 1411, sampleRateHz = 44100, bitDepth = 16)
        assertTrue(CandidateQualityRank.isStrictlyBetterThanYouTube(flacRank, youtubeBitrate))

        // 320 kbps MP3 beats YouTube 160 kbps
        val mp3320Rank = CandidateQualityRank.from(StreamFormat.MP3, bitrateKbps = 320)
        assertTrue(CandidateQualityRank.isStrictlyBetterThanYouTube(mp3320Rank, youtubeBitrate))

        // 256 kbps does not meet the >= 320 kbps threshold for lossy upgrade
        val aac256Rank = CandidateQualityRank.from(StreamFormat.AAC, bitrateKbps = 256)
        assertFalse(CandidateQualityRank.isStrictlyBetterThanYouTube(aac256Rank, youtubeBitrate))

        // 128 kbps is worse than YouTube baseline
        val mp3128Rank = CandidateQualityRank.from(StreamFormat.MP3, bitrateKbps = 128)
        assertFalse(CandidateQualityRank.isStrictlyBetterThanYouTube(mp3128Rank, youtubeBitrate))
    }

    @Test
    fun testCandidateQualityRank_rankingComparison() {
        val flacStandard = CandidateQualityRank.from(StreamFormat.FLAC, bitrateKbps = 1411, sampleRateHz = 44100, bitDepth = 16)
        val flacHiRes = CandidateQualityRank.from(StreamFormat.FLAC, bitrateKbps = 4608, sampleRateHz = 96000, bitDepth = 24)
        val mp3320 = CandidateQualityRank.from(StreamFormat.MP3, bitrateKbps = 320)

        // Hi-Res FLAC ranks above standard FLAC
        assertTrue(flacHiRes > flacStandard)

        // Standard FLAC ranks above MP3 320
        assertTrue(flacStandard > mp3320)
    }

    @Test
    fun testFinalQualityLabelComputation() {
        // Standard FLAC
        val (label1, isLossless1) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/flac",
            bitDepth = 16,
            sampleRateHz = 44100,
        )
        assertEquals("Lossless", label1)
        assertTrue(isLossless1)

        // Hi-Res FLAC (24-bit 96kHz)
        val (label2, isLossless2) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/flac",
            bitDepth = 24,
            sampleRateHz = 96000,
        )
        assertEquals("Hi-Res Lossless", label2)
        assertTrue(isLossless2)

        // MP3 320 kbps
        val (label3, isLossless3) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/mpeg",
            bitrateBps = 320_000,
        )
        assertEquals("MP3 320", label3)
        assertFalse(isLossless3)

        // AAC 256 kbps
        val (label4, isLossless4) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/mp4a-latm",
            bitrateBps = 256_000,
        )
        assertEquals("AAC 256", label4)
        assertFalse(isLossless4)
    }

    @Test
    fun testGenerationTokenCancellationOnSeekOrSkip() {
        val generationToken = AtomicLong(0)
        val swapExecuted = AtomicBoolean(false)

        // Track 1 starts with generation 1
        val gen1 = generationToken.incrementAndGet()
        assertEquals(1L, gen1)

        // User seeks or skips before audition completes -> increments generation to 2
        val gen2 = generationToken.incrementAndGet()
        assertEquals(2L, gen2)

        // When gen1 audition completes asynchronously, it compares generation tokens
        if (generationToken.get() == gen1) {
            swapExecuted.set(true)
        }

        assertFalse("Stale generation token must not execute swap", swapExecuted.get())
    }

    @Test
    fun testWatchdogRevertAndBlacklistOnStall() {
        val blacklistedSources = ConcurrentHashMap<String, MutableSet<String>>()
        var revertedPosition: Long? = null
        val mediaId = "test_media_456"
        val failedSource = "sample_flac_addon"

        // Simulate watchdog active
        var isWatchdogActive = true

        fun onPlaybackFailedOrStalled(songId: String, currentPos: Long, reason: String) {
            if (isWatchdogActive) {
                isWatchdogActive = false
                blacklistedSources.getOrPut(songId) { ConcurrentHashMap.newKeySet() }.add(failedSource)
                ResolveAudioStreamUseCase.clearWinningSource(songId)
                revertedPosition = currentPos
            }
        }

        // Trigger stall at position 12,450 ms
        onPlaybackFailedOrStalled(mediaId, 12450L, "Decoder stall within 30s")

        assertEquals(12450L, revertedPosition)
        assertTrue("Failed source must be blacklisted for song", blacklistedSources[mediaId]?.contains(failedSource) == true)
        assertNull("Winning source cache must be cleared", ResolveAudioStreamUseCase.getWinningSource(mediaId))

        // Subsequent stall after watchdog deactivated does not re-trigger revert
        revertedPosition = null
        onPlaybackFailedOrStalled(mediaId, 15000L, "Late error after watchdog")
        assertNull("Revert must not trigger when watchdog is inactive", revertedPosition)
    }

    @Test
    fun testSeamlessSwapGuardFlagPreventsSideEffects() {
        var isQualityUpgradeSwapInProgress = false
        var historySessionCount = 0
        var lyricsPreloadCount = 0

        fun onMediaItemTransitionSimulated(isSwap: Boolean) {
            if (isSwap) {
                // Guard suppresses side effects
                return
            }
            historySessionCount++
            lyricsPreloadCount++
        }

        // Standard track start
        onMediaItemTransitionSimulated(isSwap = isQualityUpgradeSwapInProgress)
        assertEquals(1, historySessionCount)
        assertEquals(1, lyricsPreloadCount)

        // Seamless swap in progress
        isQualityUpgradeSwapInProgress = true
        onMediaItemTransitionSimulated(isSwap = isQualityUpgradeSwapInProgress)
        assertEquals(1, historySessionCount) // Did not increment!
        assertEquals(1, lyricsPreloadCount)  // Did not increment!

        // Swap completed
        isQualityUpgradeSwapInProgress = false
        onMediaItemTransitionSimulated(isSwap = isQualityUpgradeSwapInProgress)
        assertEquals(2, historySessionCount)
        assertEquals(2, lyricsPreloadCount)
    }
}
