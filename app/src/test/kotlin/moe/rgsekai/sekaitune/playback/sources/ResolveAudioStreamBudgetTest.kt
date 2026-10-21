/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unit tests verifying parallel addon resolution within the 1500 ms budget:
 * - Query all enabled addons in parallel.
 * - Highest-ranked match wins within budget if strictly better than YouTube baseline.
 * - Slow addons exceeding budget lose and fall back to YouTube immediately.
 * - Slower results are kept in pending cache for Phase 3.
 * - Equal or worse addons lose and fall back to YouTube baseline.
 * - Failing addons fall back to YouTube silently.
 */
class ResolveAudioStreamBudgetTest {

    private data class TestResolvedStream(
        val source: String,
        val url: String,
        val format: StreamFormat,
        val bitrateKbps: Int?,
        val rank: CandidateQualityRank,
    )

    private val pendingAddonResults = ConcurrentHashMap<String, TestResolvedStream>()

    /**
     * Simulation of parallel resolution across enabled addons and YouTube baseline.
     */
    private suspend fun resolveParallelWithBudget(
        mediaId: String,
        budgetMs: Long = 1500L,
        addonQueries: List<suspend () -> SourceStream?>,
        youtubeQuery: suspend () -> TestResolvedStream,
    ): TestResolvedStream {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // YouTube starts immediately in parallel
        val ytDeferred = scope.async {
            youtubeQuery()
        }

        val collectedResults = CopyOnWriteArrayList<TestResolvedStream>()
        val addonJobs = addonQueries.map { query ->
            scope.launch {
                val stream = runCatching { query() }.getOrNull() ?: return@launch
                val rank = CandidateQualityRank.from(stream)
                collectedResults.add(
                    TestResolvedStream(
                        source = "addon",
                        url = stream.url,
                        format = stream.format,
                        bitrateKbps = stream.bitrateKbps,
                        rank = rank,
                    )
                )
            }
        }

        // Wait within budget
        withTimeoutOrNull(budgetMs) {
            addonJobs.forEach { it.join() }
        }

        val ytBaselineBitrate = if (ytDeferred.isCompleted) {
            runCatching { ytDeferred.getCompleted().bitrateKbps ?: 160 }.getOrDefault(160)
        } else {
            160
        }

        val bestAddon = collectedResults
            .filter { CandidateQualityRank.isStrictlyBetterThanYouTube(it.rank, ytBaselineBitrate) }
            .maxByOrNull { it.rank }

        if (bestAddon != null) {
            ytDeferred.cancel()
            return bestAddon
        }

        // Addon timed out, failed, or was not strictly better. Fall back to YouTube immediately.
        // Slower results are cached in pendingAddonResults for Phase 3.
        addonJobs.forEach { job ->
            job.invokeOnCompletion {
                val lateBest = collectedResults
                    .filter { CandidateQualityRank.isStrictlyBetterThanYouTube(it.rank, ytBaselineBitrate) }
                    .maxByOrNull { it.rank }
                if (lateBest != null) {
                    pendingAddonResults[mediaId] = lateBest
                }
            }
        }

        return ytDeferred.await()
    }

    @Test
    fun testFastAddonWins_withinBudget() = runBlocking {
        val result = resolveParallelWithBudget(
            mediaId = "track_fast",
            budgetMs = 1500L,
            addonQueries = listOf(
                {
                    delay(100)
                    SourceStream(
                        url = "https://addon.example.com/stream/flac",
                        format = StreamFormat.FLAC,
                        bitrateKbps = 1411,
                        sampleRateHz = 44100,
                        bitDepth = 16,
                    )
                }
            ),
            youtubeQuery = {
                delay(300)
                TestResolvedStream(
                    source = "youtube",
                    url = "https://googlevideo.com/opus",
                    format = StreamFormat.OPUS,
                    bitrateKbps = 160,
                    rank = CandidateQualityRank.from(StreamFormat.OPUS, 160),
                )
            },
        )

        assertEquals("Fast addon must win", "addon", result.source)
        assertEquals(StreamFormat.FLAC, result.format)
    }

    @Test
    fun testMultipleAddonsInParallel_bestRankWins() = runBlocking {
        val result = resolveParallelWithBudget(
            mediaId = "track_multi",
            budgetMs = 1500L,
            addonQueries = listOf(
                // Addon 1 returns 320 kbps MP3 in 80 ms
                {
                    delay(80)
                    SourceStream(
                        url = "https://addon1.example.com/mp3",
                        format = StreamFormat.MP3,
                        bitrateKbps = 320,
                    )
                },
                // Addon 2 returns 24-bit 96kHz FLAC in 150 ms
                {
                    delay(150)
                    SourceStream(
                        url = "https://addon2.example.com/flac_hires",
                        format = StreamFormat.FLAC,
                        bitrateKbps = 4608,
                        sampleRateHz = 96000,
                        bitDepth = 24,
                    )
                },
                // Addon 3 returns 16-bit 44.1kHz FLAC in 120 ms
                {
                    delay(120)
                    SourceStream(
                        url = "https://addon3.example.com/flac_standard",
                        format = StreamFormat.FLAC,
                        bitrateKbps = 1411,
                        sampleRateHz = 44100,
                        bitDepth = 16,
                    )
                },
            ),
            youtubeQuery = {
                delay(300)
                TestResolvedStream(
                    source = "youtube",
                    url = "https://googlevideo.com/opus",
                    format = StreamFormat.OPUS,
                    bitrateKbps = 160,
                    rank = CandidateQualityRank.from(StreamFormat.OPUS, 160),
                )
            },
        )

        assertEquals("Highest ranked candidate (Hi-Res FLAC) must win", "addon", result.source)
        assertEquals("https://addon2.example.com/flac_hires", result.url)
        assertEquals(StreamFormat.FLAC, result.format)
        assertEquals(4608, result.bitrateKbps)
    }

    @Test
    fun testSlowAddonLoses_andFallsBackToYouTubeWithoutDelay() = runBlocking {
        pendingAddonResults.clear()
        val startMs = System.currentTimeMillis()

        val result = resolveParallelWithBudget(
            mediaId = "track_slow",
            budgetMs = 250L, // Simulated short budget
            addonQueries = listOf(
                // Addon takes 600 ms (exceeds budget)
                {
                    delay(600)
                    SourceStream(
                        url = "https://addon.example.com/stream/flac",
                        format = StreamFormat.FLAC,
                        bitrateKbps = 1411,
                    )
                }
            ),
            youtubeQuery = {
                delay(100)
                TestResolvedStream(
                    source = "youtube",
                    url = "https://googlevideo.com/opus",
                    format = StreamFormat.OPUS,
                    bitrateKbps = 160,
                    rank = CandidateQualityRank.from(StreamFormat.OPUS, 160),
                )
            },
        )
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals("Slow addon must lose, YouTube stream must be returned", "youtube", result.source)
        assertTrue("Playback must not be delayed beyond budget", elapsedMs < 500)

        // Verify that slower addon result is cached for Phase 3
        delay(600)
        val cached = pendingAddonResults["track_slow"]
        assertNotNull("Late addon result must be kept in pending cache for Phase 3", cached)
        assertEquals("addon", cached?.source)
        assertEquals(StreamFormat.FLAC, cached?.format)
    }

    @Test
    fun testEqualOrWorseAddonLoses_andFallsBackToYouTube() = runBlocking {
        val result = resolveParallelWithBudget(
            mediaId = "track_worse",
            budgetMs = 1500L,
            addonQueries = listOf(
                // Addon returns 160 kbps MP3 (equal to 160 kbps YouTube, and < 320 kbps requirement)
                {
                    delay(50)
                    SourceStream(
                        url = "https://addon.example.com/stream/mp3",
                        format = StreamFormat.MP3,
                        bitrateKbps = 160,
                    )
                }
            ),
            youtubeQuery = {
                delay(100)
                TestResolvedStream(
                    source = "youtube",
                    url = "https://googlevideo.com/opus",
                    format = StreamFormat.OPUS,
                    bitrateKbps = 160,
                    rank = CandidateQualityRank.from(StreamFormat.OPUS, 160),
                )
            },
        )

        assertEquals("Equal or worse addon must lose and stay on YouTube", "youtube", result.source)
    }

    @Test
    fun testFailingAddonFallsBack_silently() = runBlocking {
        val result = resolveParallelWithBudget(
            mediaId = "track_fail",
            budgetMs = 1500L,
            addonQueries = listOf(
                {
                    delay(50)
                    throw RuntimeException("HTTP 500 Internal Server Error")
                }
            ),
            youtubeQuery = {
                delay(100)
                TestResolvedStream(
                    source = "youtube",
                    url = "https://googlevideo.com/opus",
                    format = StreamFormat.OPUS,
                    bitrateKbps = 160,
                    rank = CandidateQualityRank.from(StreamFormat.OPUS, 160),
                )
            },
        )

        assertEquals("Failing addon must silently fall back to YouTube", "youtube", result.source)
    }
}
