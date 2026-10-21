/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.player

import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.playback.sources.AudioPipelineStatus
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeInfo
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPipelineDetailsTest {

    @Test
    fun `resolveAudioPipelineDetails for preloaded lossless addon stream`() {
        val badge = PlaybackQualityBadgeInfo(
            mediaId = "track_1",
            state = PlaybackQualityBadgeState.FINAL,
            label = "Hi-Res Lossless",
            isLossless = true,
            isHiRes = true,
            details = "FLAC 24-bit / 96.0 kHz",
            sourceName = "Lossless Bridge",
            format = "FLAC",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            bitrate = "2840 kbps",
            pipelineStatus = AudioPipelineStatus.PRELOADED_INSTANT,
        )

        val details = resolveAudioPipelineDetails(badge)

        assertEquals("Lossless Bridge", details.source)
        assertEquals("FLAC", details.format)
        assertEquals("24-bit", details.bitDepth)
        assertEquals("96.0 kHz", details.sampleRate)
        assertEquals("2840 kbps", details.bitrate)
        assertEquals("24-bit / 96.0 kHz / 2840 kbps", details.audioSpec)
        assertEquals("c2.android.flac.decoder", details.decoderName)
        assertEquals("24-bit PCM", details.decoderFormat)
        assertEquals("96.0 kHz \u2192 96.0 kHz", details.ioRate)
        assertEquals("Passthrough", details.resamplerQuality)
        assertEquals(AudioPipelineStatus.PRELOADED_INSTANT, details.pipelineStatus)
    }

    @Test
    fun `resolveAudioPipelineDetails for swapped upgrade from Opus`() {
        val badge = PlaybackQualityBadgeInfo(
            mediaId = "track_2",
            state = PlaybackQualityBadgeState.FINAL,
            label = "Lossless",
            isLossless = true,
            isHiRes = false,
            details = "FLAC 16-bit / 44.1 kHz",
            sourceName = "External Stream",
            format = "FLAC",
            bitDepth = "16-bit",
            sampleRate = "44.1 kHz",
            bitrate = "751 kbps",
            pipelineStatus = AudioPipelineStatus.UPGRADED_FROM_OPUS,
        )

        val details = resolveAudioPipelineDetails(badge)

        assertEquals("External Stream", details.source)
        assertEquals("FLAC", details.format)
        assertEquals("16-bit", details.bitDepth)
        assertEquals("44.1 kHz", details.sampleRate)
        assertEquals("751 kbps", details.bitrate)
        assertEquals("16-bit / 44.1 kHz / 751 kbps", details.audioSpec)
        assertEquals("c2.android.flac.decoder", details.decoderName)
        assertEquals("16-bit PCM", details.decoderFormat)
        assertEquals(AudioPipelineStatus.UPGRADED_FROM_OPUS, details.pipelineStatus)
    }

    @Test
    fun `resolveAudioPipelineDetails for settled YouTube fallback due to no match`() {
        val badge = PlaybackQualityBadgeInfo(
            mediaId = "track_3",
            state = PlaybackQualityBadgeState.FINAL,
            label = "High",
            isLossless = false,
            isHiRes = false,
            details = "",
            sourceName = "YouTube",
            format = "Opus",
            bitDepth = "16-bit",
            sampleRate = "48.0 kHz",
            bitrate = "149 kbps",
            pipelineStatus = AudioPipelineStatus.SETTLED_NO_MATCH,
        )

        val details = resolveAudioPipelineDetails(badge)

        assertEquals("YouTube", details.source)
        assertEquals("OPUS", details.format)
        assertEquals("16-bit", details.bitDepth)
        assertEquals("48.0 kHz", details.sampleRate)
        assertEquals("149 kbps", details.bitrate)
        assertEquals("c2.android.opus.decoder", details.decoderName)
        assertEquals("16-bit PCM", details.decoderFormat)
        assertEquals("48.0 kHz \u2192 48.0 kHz", details.ioRate)
        assertEquals(AudioPipelineStatus.SETTLED_NO_MATCH, details.pipelineStatus)
    }

    @Test
    fun `resolveAudioPipelineDetails for settled YouTube fallback due to metered network`() {
        val badge = PlaybackQualityBadgeInfo(
            mediaId = "track_4",
            state = PlaybackQualityBadgeState.FINAL,
            label = "High",
            isLossless = false,
            isHiRes = false,
            sourceName = "YouTube",
            format = "Opus",
            pipelineStatus = AudioPipelineStatus.SETTLED_METERED,
        )

        val details = resolveAudioPipelineDetails(badge)
        assertEquals("YouTube", details.source)
        assertEquals(AudioPipelineStatus.SETTLED_METERED, details.pipelineStatus)
    }

    @Test
    fun `resolveAudioPipelineDetails for settled YouTube fallback due to rate limit HTTP 429`() {
        val badge = PlaybackQualityBadgeInfo(
            mediaId = "track_5",
            state = PlaybackQualityBadgeState.FINAL,
            label = "High",
            isLossless = false,
            isHiRes = false,
            sourceName = "YouTube",
            format = "Opus",
            pipelineStatus = AudioPipelineStatus.SETTLED_RATE_LIMITED,
        )

        val details = resolveAudioPipelineDetails(badge)
        assertEquals(AudioPipelineStatus.SETTLED_RATE_LIMITED, details.pipelineStatus)
    }

    @Test
    fun `resolveAudioPipelineDetails computes transfer size and format from FormatEntity`() {
        val badge = PlaybackQualityBadgeInfo(
            mediaId = "track_6",
            state = PlaybackQualityBadgeState.FINAL,
            label = "High",
            isLossless = false,
            isHiRes = false,
        )
        val format = FormatEntity(
            id = "format_1",
            itag = 140,
            mimeType = "audio/mp4",
            codecs = "mp4a.40.2",
            bitrate = 128000,
            sampleRate = 44100,
            contentLength = 32 * 1024 * 1024L, // 32 MB
            loudnessDb = null,
            playbackUrl = null,
        )

        val details = resolveAudioPipelineDetails(badge, format)
        assertEquals("AAC", details.format)
        assertEquals("c2.android.aac.decoder", details.decoderName)
        assertEquals("32.0 MB", details.transferSize)
        assertEquals("128 kbps", details.bitrate)
        assertEquals("44.1 kHz", details.sampleRate)
    }
}
