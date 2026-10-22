/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.ui.player.deriveDisplayBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateQualityRankTest {

    @Test
    fun testLosslessBeats320KbpsLossy() {
        val flac = CandidateQualityRank.from(StreamFormat.FLAC, bitrateKbps = 1411, sampleRateHz = 44100, bitDepth = 16)
        val alac = CandidateQualityRank.from(StreamFormat.ALAC, bitrateKbps = 1411, sampleRateHz = 44100, bitDepth = 16)
        val wav = CandidateQualityRank.from(StreamFormat.WAV, bitrateKbps = 1411, sampleRateHz = 44100, bitDepth = 16)
        val mp3_320 = CandidateQualityRank.from(StreamFormat.MP3, bitrateKbps = 320)
        val aac_320 = CandidateQualityRank.from(StreamFormat.AAC, bitrateKbps = 320)

        assertTrue("FLAC must rank above 320 kbps MP3", flac > mp3_320)
        assertTrue("ALAC must rank above 320 kbps MP3", alac > mp3_320)
        assertTrue("WAV must rank above 320 kbps AAC", wav > aac_320)
    }

    @Test
    fun test320KbpsLossyBeatsYouTubeOpus() {
        val mp3_320 = CandidateQualityRank.from(StreamFormat.MP3, bitrateKbps = 320)
        val aac_320 = CandidateQualityRank.from(StreamFormat.AAC, bitrateKbps = 320)
        val ytOpus_160 = CandidateQualityRank.from(StreamFormat.OPUS, bitrateKbps = 160)

        assertTrue("320 kbps MP3 must rank above 160 kbps YouTube Opus", mp3_320 > ytOpus_160)
        assertTrue("320 kbps AAC must rank above 160 kbps YouTube Opus", aac_320 > ytOpus_160)
    }

    @Test
    fun testEqualOrWorseAddonLoses() {
        val ytBaselineKbps = 160
        val addon_160 = CandidateQualityRank.from(StreamFormat.MP3, bitrateKbps = 160)
        val addon_128 = CandidateQualityRank.from(StreamFormat.AAC, bitrateKbps = 128)
        val addon_256 = CandidateQualityRank.from(StreamFormat.AAC, bitrateKbps = 256)

        assertFalse("160 kbps lossy is not strictly better than 160 kbps YouTube baseline",
            CandidateQualityRank.isStrictlyBetterThanYouTube(addon_160, ytBaselineKbps))
        assertFalse("128 kbps lossy is not strictly better than 160 kbps YouTube baseline",
            CandidateQualityRank.isStrictlyBetterThanYouTube(addon_128, ytBaselineKbps))
        assertFalse("256 kbps lossy (< 320 kbps requirement) is not strictly better than YouTube",
            CandidateQualityRank.isStrictlyBetterThanYouTube(addon_256, ytBaselineKbps))
    }

    @Test
    fun testStrictlyBetterThanYouTube_losslessAnd320Plus() {
        val ytBaselineKbps = 160
        val flac = CandidateQualityRank.from(StreamFormat.FLAC, bitrateKbps = 1411)
        val mp3_320 = CandidateQualityRank.from(StreamFormat.MP3, bitrateKbps = 320)

        assertTrue("Lossless stream is strictly better than YouTube",
            CandidateQualityRank.isStrictlyBetterThanYouTube(flac, ytBaselineKbps))
        assertTrue("320 kbps stream is strictly better than 160 kbps YouTube",
            CandidateQualityRank.isStrictlyBetterThanYouTube(mp3_320, ytBaselineKbps))
    }

    @Test
    fun testLosslessRanking_higherDepthAndRateRankHigher() {
        val flac16_44 = CandidateQualityRank.from(StreamFormat.FLAC, bitrateKbps = 1411, sampleRateHz = 44100, bitDepth = 16)
        val flac24_48 = CandidateQualityRank.from(StreamFormat.FLAC, bitrateKbps = 2304, sampleRateHz = 48000, bitDepth = 24)
        val flac24_96 = CandidateQualityRank.from(StreamFormat.FLAC, bitrateKbps = 4608, sampleRateHz = 96000, bitDepth = 24)

        assertTrue("24-bit must rank higher than 16-bit", flac24_48 > flac16_44)
        assertTrue("96 kHz must rank higher than 48 kHz at same bit depth", flac24_96 > flac24_48)
    }

    @Test
    fun testFinalQualityLabelComputation() {
        // FLAC standard
        val (flacLabel, flacLossless) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/flac",
            sampleRateHz = 44100,
            bitDepth = 16,
        )
        assertEquals("Lossless", flacLabel)
        assertTrue(flacLossless)

        // Hi-Res Lossless (sample rate > 48000 or depth > 16)
        val (hiResLabel, hiResLossless) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/flac",
            sampleRateHz = 96000,
            bitDepth = 24,
        )
        assertEquals("Hi-Res Lossless", hiResLabel)
        assertTrue(hiResLossless)

        // Opus YouTube stream
        val (opusLabel, opusLossless) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/webm; codecs=\"opus\"",
            bitrateBps = 150000,
        )
        assertEquals("Opus 150", opusLabel)
        assertFalse(opusLossless)

        // AAC 256
        val (aacLabel, aacLossless) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/mp4",
            codecs = "mp4a.40.2",
            bitrateBps = 256000,
        )
        assertEquals("AAC 256", aacLabel)
        assertFalse(aacLossless)

        // MP3 320
        val (mp3Label, mp3Lossless) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = "audio/mpeg",
            codecs = "mp3",
            bitrateBps = 320000,
        )
        assertEquals("MP3 320", mp3Label)
        assertFalse(mp3Lossless)
    }

    @Test
    fun testDeriveDisplayBadge_neverClaimsLosslessUnlessDecodedIsLossless() {
        val fakeFormat = FormatEntity(
            id = "track_1",
            itag = 140,
            mimeType = "audio/mp4",
            codecs = "mp4a.40.2",
            bitrate = 128000,
            sampleRate = 44100,
            contentLength = 5000000,
            loudnessDb = null,
            playbackUrl = null,
        )

        // Addon claimed lossless, but player decoded format is AAC
        val badClaimBadge = PlaybackQualityBadgeInfo(
            mediaId = "track_1",
            state = PlaybackQualityBadgeState.FINAL,
            label = "Lossless",
            isLossless = true,
            isHiRes = false,
            details = "flac - 16-bit - 44.1 kHz",
        )

        val derived = deriveDisplayBadge(badClaimBadge, fakeFormat, "track_1")
        assertFalse("Must never claim Lossless when decoded format is lossy", derived.isLossless)
        assertEquals("AAC 128", derived.label)
    }

    @Test
    fun testDeriveDisplayBadge_upgradedStreamPreservesLosslessBadge() {
        val opusFormat = FormatEntity(
            id = "track_1",
            itag = 251,
            mimeType = "audio/webm",
            codecs = "opus",
            bitrate = 149000,
            sampleRate = 48000,
            contentLength = 3000000,
            loudnessDb = null,
            playbackUrl = null,
        )

        val upgradedBadge = PlaybackQualityBadgeInfo(
            mediaId = "track_1",
            state = PlaybackQualityBadgeState.FINAL,
            label = "Lossless",
            isLossless = true,
            isHiRes = false,
            details = "flac - 16-bit - 44.1 kHz - 902 kbps",
            sourceName = "Lossless Bridge",
            format = "FLAC",
            bitDepth = "16-bit",
            sampleRate = "44.1 kHz",
            bitrate = "902 kbps",
            pipelineStatus = moe.rgsekai.sekaitune.playback.sources.AudioPipelineStatus.UPGRADED_FROM_OPUS,
        )

        val derived = deriveDisplayBadge(upgradedBadge, opusFormat, "track_1")
        assertTrue("Upgraded stream from Lossless Bridge must remain Lossless", derived.isLossless)
        assertEquals("Lossless", derived.label)
    }

    @Test
    fun testBadgeStates_checkingAndUpgradingPreserved() {
        val checking = PlaybackQualityBadgeInfo(
            mediaId = "track_2",
            state = PlaybackQualityBadgeState.CHECKING,
            label = "Checking quality...",
        )
        val derivedChecking = deriveDisplayBadge(checking, null, "track_2")
        assertEquals(PlaybackQualityBadgeState.CHECKING, derivedChecking.state)
        assertEquals("Checking quality...", derivedChecking.label)

        val upgrading = PlaybackQualityBadgeInfo(
            mediaId = "track_2",
            state = PlaybackQualityBadgeState.UPGRADING,
            label = "Upgrading quality",
        )
        val derivedUpgrading = deriveDisplayBadge(upgrading, null, "track_2")
        assertEquals(PlaybackQualityBadgeState.UPGRADING, derivedUpgrading.state)
        assertEquals("Upgrading quality", derivedUpgrading.label)
    }
}
