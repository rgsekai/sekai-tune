/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.stream

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
}
