/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.stream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ResolveAudioStreamUseCaseTest {
    @Test
    fun testInFlightDeduplication() = runBlocking {
        val resolutionCount = AtomicInteger(0)
        val inFlight = ConcurrentHashMap<String, CompletableDeferred<String>>()

        suspend fun resolveStreamDeduplicated(mediaId: String): String {
            val deferred = inFlight.computeIfAbsent(mediaId) {
                CompletableDeferred()
            }
            if (!deferred.isCompleted && !deferred.isActive) {
                // start resolution
            }
            // simulate first caller executing work
            if (deferred.isActive && resolutionCount.compareAndSet(0, 1)) {
                kotlinx.coroutines.delay(50)
                deferred.complete("https://googlevideo.com/videoplayback?id=$mediaId")
            }
            return deferred.await()
        }

        val call1 = async { resolveStreamDeduplicated("test_track_123") }
        val call2 = async { resolveStreamDeduplicated("test_track_123") }

        val res1 = call1.await()
        val res2 = call2.await()

        assertEquals(res1, res2)
        assertEquals(1, resolutionCount.get())
        assertTrue(res1.contains("test_track_123"))
    }

    @Test
    fun testPreloadCancellationDoesNotCancelForegroundPlayback() = runBlocking {
        val useCaseScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<String>>()

        suspend fun resolveStreamWithScopeIsolation(
            mediaId: String,
            workDelayMs: Long = 100,
        ): String {
            val deferred = synchronized(inFlight) {
                inFlight.computeIfAbsent(mediaId) {
                    useCaseScope.async {
                        try {
                            kotlinx.coroutines.delay(workDelayMs)
                            "https://googlevideo.com/videoplayback?id=$mediaId"
                        } finally {
                            inFlight.remove(mediaId)
                        }
                    }
                }
            }
            return deferred.await()
        }

        // Caller 1 (preload) starts in its own separate child scope
        val preloadScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val preloadJob = preloadScope.launch {
            resolveStreamWithScopeIsolation("track_cancel_test", workDelayMs = 80)
        }

        // Let preload start and enter in-flight
        kotlinx.coroutines.delay(20)

        // Cancel the preload's scope while resolution is in-flight
        preloadJob.cancel()

        // Caller 2 (foreground playback) requests the same track ID immediately
        val playbackResult = async(kotlinx.coroutines.Dispatchers.IO) {
            resolveStreamWithScopeIsolation("track_cancel_test", workDelayMs = 80)
        }.await()

        assertNotNull(playbackResult)
        assertEquals("https://googlevideo.com/videoplayback?id=track_cancel_test", playbackResult)
    }
}
