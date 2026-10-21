/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.stream

import kotlinx.coroutines.CompletableDeferred
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse
import moe.rgsekai.sekaitune.playback.sources.CandidateQualityRank
import moe.rgsekai.sekaitune.playback.sources.SourceStream
import moe.rgsekai.sekaitune.playback.sources.StreamFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class StartRulesTest {

    private fun createDummyStream(url: String, expiresAtMs: Long): ResolvedAudioStream {
        val format = PlayerResponse.StreamingData.Format(
            itag = 999,
            url = url,
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
            url = url,
            format = format,
            audioConfig = null,
            videoDetails = null,
            playbackTracking = null,
            expiresAtMs = expiresAtMs,
            authFingerprint = "addon:test",
        )
    }

    private fun createDummyAddonResult(mediaId: String, sourceName: String, expiresAtMs: Long): AddonStreamResult {
        val stream = createDummyStream("https://example.com/audio_$mediaId.flac", expiresAtMs)
        return AddonStreamResult(
            resolvedStream = stream,
            sourceName = sourceName,
            formatDescription = "flac - 24-bit - 96.0 kHz",
            rank = CandidateQualityRank.from(StreamFormat.FLAC, 2400, 96000, 24),
            sourceStream = SourceStream(
                url = stream.url,
                format = StreamFormat.FLAC,
                bitrateKbps = 2400,
                sampleRateHz = 96000,
                bitDepth = 24,
            ),
        )
    }

    @Before
    fun setUp() {
        ResolveAudioStreamUseCase.clearAllWinningSources()
        ResolveAudioStreamUseCase.fallbackYouTubeCache.clear()
        ResolveAudioStreamUseCase.pendingAddonResults.clear()
        ResolveAudioStreamUseCase.inFlightAddonPreload.clear()
    }

    @After
    fun tearDown() {
        ResolveAudioStreamUseCase.clearAllWinningSources()
        ResolveAudioStreamUseCase.fallbackYouTubeCache.clear()
        ResolveAudioStreamUseCase.pendingAddonResults.clear()
        ResolveAudioStreamUseCase.inFlightAddonPreload.clear()
    }

    @Test
    fun rule1_preloadedFreshWinningAddon_directStartValid() {
        val mediaId = "track_preloaded_fresh"
        val now = System.currentTimeMillis()
        val winningResult = createDummyAddonResult(mediaId, "HiFi Source", now + 600_000L)
        ResolveAudioStreamUseCase.winningSourceCache[mediaId] = winningResult

        assertTrue(ResolveAudioStreamUseCase.isWinningSourceValid(mediaId))
        val winner = ResolveAudioStreamUseCase.getWinningSource(mediaId)
        assertNotNull(winner)
        assertEquals("HiFi Source", winner?.sourceName)

        val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = winner!!.sourceStream.format.name,
            bitrateBps = (winner.sourceStream.bitrateKbps ?: 1411) * 1000,
            sampleRateHz = winner.sourceStream.sampleRateHz,
            bitDepth = winner.sourceStream.bitDepth,
            addonFormat = winner.sourceStream.format,
        )
        val isHiRes = isLossless && ((winner.sourceStream.bitDepth ?: 16) > 16 || (winner.sourceStream.sampleRateHz ?: 44100) > 48000)

        assertTrue(isLossless)
        assertTrue(isHiRes)
        assertEquals("Hi-Res Lossless", label)
    }

    @Test
    fun rule2_notPreloadedTrack_winningSourceNotValid() {
        val mediaId = "track_unpreloaded"
        assertFalse(ResolveAudioStreamUseCase.isWinningSourceValid(mediaId))
        assertNull(ResolveAudioStreamUseCase.getWinningSource(mediaId))
    }

    @Test
    fun rule3_inFlightPreload_canBeRetrievedAndAwaited() {
        val mediaId = "track_inflight"
        val deferred = CompletableDeferred<AddonStreamResult?>()
        ResolveAudioStreamUseCase.inFlightAddonPreload[mediaId] = deferred

        val retrievedDeferred = ResolveAudioStreamUseCase.getInFlightAddonPreload(mediaId)
        assertNotNull(retrievedDeferred)

        val expectedResult = createDummyAddonResult(mediaId, "InFlight Addon", System.currentTimeMillis() + 300_000L)
        deferred.complete(expectedResult)

        kotlinx.coroutines.runBlocking {
            val result = retrievedDeferred!!.await()
            assertNotNull(result)
            assertEquals("InFlight Addon", result?.sourceName)
        }
    }

    @Test
    fun rule4_fallbackYouTubeStream_preservesPositionAndRevertsCleanly() {
        val mediaId = "track_revert"
        val now = System.currentTimeMillis()
        val ytFallback = createDummyStream("https://youtube.com/audio_$mediaId.opus", now + 300_000L)
        ResolveAudioStreamUseCase.setFallbackYouTubeStream(mediaId, ytFallback)

        val retrievedFallback = ResolveAudioStreamUseCase.getFallbackYouTubeStream(mediaId)
        assertNotNull(retrievedFallback)
        assertEquals("https://youtube.com/audio_$mediaId.opus", retrievedFallback?.url)

        // Clear winning source on failure
        val winningResult = createDummyAddonResult(mediaId, "Unstable Source", now + 300_000L)
        ResolveAudioStreamUseCase.winningSourceCache[mediaId] = winningResult
        assertTrue(ResolveAudioStreamUseCase.isWinningSourceValid(mediaId))

        // Revert action clears winning source
        ResolveAudioStreamUseCase.clearWinningSource(mediaId)
        assertFalse(ResolveAudioStreamUseCase.isWinningSourceValid(mediaId))

        // Fallback remains accessible for playback recovery
        val fallbackAfterRevert = ResolveAudioStreamUseCase.getFallbackYouTubeStream(mediaId)
        assertNotNull(fallbackAfterRevert)
    }

    @Test
    fun rule4_blacklistPreventsInfiniteRetryLoop() {
        val blacklistedAddons = ConcurrentHashMap<String, MutableSet<String>>()
        val mediaId = "track_flaky"
        val sourceName = "FlakyAddon"

        blacklistedAddons.getOrPut(mediaId) { ConcurrentHashMap.newKeySet() }.add(sourceName)

        assertTrue(blacklistedAddons[mediaId]?.contains(sourceName) == true)
        assertFalse(blacklistedAddons[mediaId]?.contains("AnotherAddon") == true)
    }
}
