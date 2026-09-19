/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import kotlinx.coroutines.runBlocking
import moe.rgsekai.sekaitune.canvas.tokens.TokenStoreRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DynamicTokenScraperVerificationTest {

    private fun formatEpoch(epochSeconds: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(epochSeconds * 1000L))
    }

    @Test
    fun testTidalDynamicTokenAndIsolatedVideoFetch() = runBlocking {
        println("=== [TEST] TIDAL Dynamic Token & Isolated Video Fetch ===")
        
        // 1. Check health
        val healthy = TidalCanvasProvider.isHealthy()
        println("TidalCanvasProvider.isHealthy(): $healthy")
        assertTrue("TIDAL health check must pass", healthy)

        // 2. Fetch token from WebTokenProvider
        val token = TidalCanvasProvider.tokenProvider.getToken()
        println("Scraped TIDAL Access Token: ${token.take(20)}...")
        assertNotNull(token)
        assertTrue(token.isNotBlank())

        val cachedTokenObj = TokenStoreRegistry.activeStore.get("TIDAL")
        assertNotNull(cachedTokenObj)
        println("TIDAL Token Expiry: ${formatEpoch(cachedTokenObj!!.expiresAtEpochSeconds)} (Epoch: ${cachedTokenObj.expiresAtEpochSeconds})")

        // 3. Perform real isolated canvas fetch through SekaiTuneCanvas
        val canvas = SekaiTuneCanvas.getCanvas(
            song = "After Hours",
            artists = listOf("The Weeknd"),
            durationMs = 361000L,
            policy = CanvasRequestPolicy(
                preferredSource = CanvasSource.TIDAL,
                allowFallback = false,
            ),
        )

        println("TIDAL Canvas Result: $canvas")
        assertNotNull("TIDAL Canvas must resolve for 'After Hours' by 'The Weeknd'", canvas)
        assertNotNull("TIDAL Canvas must contain video URL", canvas?.videoUrl)
        assertTrue("Video URL must point to TIDAL resources", canvas!!.videoUrl!!.contains("resources.tidal.com/videos/"))
        println("Verified TIDAL Video Stream URL: ${canvas.videoUrl}")

        // 4. Forced-expiry test: invalidate and verify automatic re-scrape & recovery
        println("--- Forced Expiry Test for TIDAL ---")
        TidalCanvasProvider.tokenProvider.invalidate()
        val recoveredCanvas = SekaiTuneCanvas.getCanvas(
            song = "Blinding Lights",
            artists = listOf("The Weeknd"),
            durationMs = 200000L,
            policy = CanvasRequestPolicy(
                preferredSource = CanvasSource.TIDAL,
                allowFallback = false,
            ),
        )
        assertNotNull("TIDAL must recover and resolve canvas after forced token invalidation", recoveredCanvas)
        println("Recovered TIDAL Canvas Result: $recoveredCanvas")
    }

    @Test
    fun testAppleMusicDynamicTokenAndIsolatedMotionArtwork() = runBlocking {
        println("=== [TEST] Apple Music Dynamic Token & Isolated Motion Artwork ===")
        
        // 1. Check health
        val healthy = AppleMusicProvider.isHealthy()
        println("AppleMusicProvider.isHealthy(): $healthy")
        assertTrue("Apple Music health check must pass", healthy)

        // 2. Fetch token from WebTokenProvider
        val token = AppleMusicProvider.tokenProvider.getToken()
        println("Scraped Apple Music JWT: ${token.take(20)}...")
        assertNotNull(token)
        assertTrue(token.isNotBlank())

        val cachedTokenObj = TokenStoreRegistry.activeStore.get("AppleMusic")
        assertNotNull(cachedTokenObj)
        println("Apple Music Token Expiry: ${formatEpoch(cachedTokenObj!!.expiresAtEpochSeconds)} (Epoch: ${cachedTokenObj.expiresAtEpochSeconds})")

        // 3. Perform real isolated search / canvas fetch through SekaiTuneCanvas
        val canvas = SekaiTuneCanvas.getCanvas(
            song = "After Hours",
            artists = listOf("The Weeknd"),
            durationMs = 361000L,
            policy = CanvasRequestPolicy(
                preferredSource = CanvasSource.APPLE_MUSIC,
                allowFallback = false,
            ),
        )

        println("Apple Music Canvas Result: $canvas")
        assertNotNull("Apple Music Canvas must resolve for 'After Hours' by 'The Weeknd'", canvas)
        println("Verified Apple Music Artwork: name='${canvas?.name}', artist='${canvas?.artist}', animated='${canvas?.animated}'")

        // 4. Forced-expiry test: invalidate and verify automatic re-scrape & recovery
        println("--- Forced Expiry Test for Apple Music ---")
        AppleMusicProvider.tokenProvider.invalidate()
        val recoveredCanvas = SekaiTuneCanvas.getCanvas(
            song = "Starboy",
            artists = listOf("The Weeknd"),
            durationMs = 230000L,
            policy = CanvasRequestPolicy(
                preferredSource = CanvasSource.APPLE_MUSIC,
                allowFallback = false,
            ),
        )
        assertNotNull("Apple Music must recover and resolve canvas after forced token invalidation", recoveredCanvas)
        println("Recovered Apple Music Canvas Result: $recoveredCanvas")
    }
}
