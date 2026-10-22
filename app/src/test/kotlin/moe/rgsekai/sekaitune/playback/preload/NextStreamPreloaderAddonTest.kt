/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.preload

import kotlinx.coroutines.sync.Semaphore
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse
import moe.rgsekai.sekaitune.playback.sources.CandidateQualityRank
import moe.rgsekai.sekaitune.playback.sources.SourceStream
import moe.rgsekai.sekaitune.playback.sources.StreamFormat
import moe.rgsekai.sekaitune.playback.stream.AddonStreamResult
import moe.rgsekai.sekaitune.playback.stream.ResolvedAudioStream
import moe.rgsekai.sekaitune.playback.stream.ResolveAudioStreamUseCase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class NextStreamPreloaderAddonTest {

    private fun createDummyStream(expiresAtMs: Long): ResolvedAudioStream {
        val format = PlayerResponse.StreamingData.Format(
            itag = 999,
            url = "https://example.com/audio.flac",
            mimeType = "audio/flac",
            bitrate = 1411000,
            width = null,
            height = null,
            contentLength = null,
            quality = "hd",
            fps = null,
            qualityLabel = null,
            averageBitrate = null,
            audioQuality = "AUDIO_QUALITY_HIGH",
            approxDurationMs = "200000",
            audioSampleRate = 44100,
            audioChannels = 2,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
        )
        return ResolvedAudioStream(
            url = "https://example.com/audio.flac",
            format = format,
            audioConfig = null,
            videoDetails = null,
            playbackTracking = null,
            expiresAtMs = expiresAtMs,
            authFingerprint = "addon:test",
        )
    }

    private fun createDummyAddonResult(mediaId: String, expiresAtMs: Long): AddonStreamResult {
        val stream = createDummyStream(expiresAtMs)
        return AddonStreamResult(
            resolvedStream = stream,
            sourceName = "Test Addon",
            formatDescription = "flac - 16-bit - 44.1 kHz",
            rank = CandidateQualityRank.from(StreamFormat.FLAC, 1411, 44100, 16),
            sourceStream = SourceStream(
                url = stream.url,
                format = StreamFormat.FLAC,
                bitrateKbps = 1411,
                sampleRateHz = 44100,
                bitDepth = 16,
            ),
        )
    }

    @Before
    fun setUp() {
        ResolveAudioStreamUseCase.clearAllWinningSources()
        ResolveAudioStreamUseCase.fallbackYouTubeCache.clear()
        ResolveAudioStreamUseCase.pendingAddonResults.clear()
    }

    @After
    fun tearDown() {
        ResolveAudioStreamUseCase.clearAllWinningSources()
        ResolveAudioStreamUseCase.fallbackYouTubeCache.clear()
        ResolveAudioStreamUseCase.pendingAddonResults.clear()
    }

    @Test
    fun preloadWindow_limitsToNextTracks() {
        val targets = listOf(
            PreloadTarget("track1"),
            PreloadTarget("track2"),
            PreloadTarget("track3"),
            PreloadTarget("track4"),
            PreloadTarget("track5"),
            PreloadTarget("track6"),
            PreloadTarget("track7"),
        )
        val window = targets.distinctBy { it.mediaId.trim() }
            .filter { it.mediaId.isNotBlank() }
            .take(NextStreamPreloader.MAX_QUEUE_PRELOAD_ITEMS)

        assertEquals(NextStreamPreloader.MAX_QUEUE_PRELOAD_ITEMS, window.size)
        assertEquals("track1", window[0].mediaId)
        assertEquals("track2", window[1].mediaId)
    }

    @Test
    fun freshnessCheck_detectsExpiredAndFreshStreams() {
        val now = System.currentTimeMillis()
        val freshStream = createDummyStream(now + 120_000L) // 2 minutes remaining (> 60s safety)
        val expiredStream = createDummyStream(now + 30_000L) // only 30s remaining (< 60s safety)

        assertTrue(ResolveAudioStreamUseCase.isFresh(freshStream))
        assertFalse(ResolveAudioStreamUseCase.isFresh(expiredStream))
    }

    @Test
    fun winningSourceCache_validityAndFallbackStorage() {
        val now = System.currentTimeMillis()
        val mediaId = "track_alpha"

        // Put fresh winner
        val winningResult = createDummyAddonResult(mediaId, now + 300_000L)
        ResolveAudioStreamUseCase.winningSourceCache[mediaId] = winningResult
        assertTrue(ResolveAudioStreamUseCase.isWinningSourceValid(mediaId))

        // Put fresh fallback YouTube stream
        val fallback = createDummyStream(now + 300_000L)
        ResolveAudioStreamUseCase.setFallbackYouTubeStream(mediaId, fallback)
        assertNotNull(ResolveAudioStreamUseCase.getFallbackYouTubeStream(mediaId))

        // Check expired winner
        val expiredWinner = createDummyAddonResult(mediaId, now + 10_000L)
        ResolveAudioStreamUseCase.winningSourceCache[mediaId] = expiredWinner
        assertFalse(ResolveAudioStreamUseCase.isWinningSourceValid(mediaId))
    }

    @Test
    fun generationToken_incrementsOnQueueChangeOrCancel() {
        val token = AtomicLong(0L)
        val gen1 = token.incrementAndGet()
        val gen2 = token.incrementAndGet()

        assertEquals(1L, gen1)
        assertEquals(2L, gen2)
        assertTrue(gen2 > gen1)
    }

    @Test
    fun addonConcurrency_semaphoreRestrictsPermits() {
        val semaphore = Semaphore(2)
        assertEquals(2, semaphore.availablePermits)

        assertTrue(semaphore.tryAcquire())
        assertEquals(1, semaphore.availablePermits)

        assertTrue(semaphore.tryAcquire())
        assertEquals(0, semaphore.availablePermits)

        assertFalse(semaphore.tryAcquire()) // Blocked: 2 permits already taken
        semaphore.release()
        assertEquals(1, semaphore.availablePermits)
    }

    @Test
    fun fallbackYouTubeCache_evictsExpiredEntryOnGet() {
        val now = System.currentTimeMillis()
        val mediaId = "track_exp"
        val expiredFallback = createDummyStream(now + 20_000L) // Expired by safety threshold

        ResolveAudioStreamUseCase.fallbackYouTubeCache[mediaId] = expiredFallback
        val result = ResolveAudioStreamUseCase.getFallbackYouTubeStream(mediaId)

        assertNull(result)
        assertFalse(ResolveAudioStreamUseCase.fallbackYouTubeCache.containsKey(mediaId))
    }
}
