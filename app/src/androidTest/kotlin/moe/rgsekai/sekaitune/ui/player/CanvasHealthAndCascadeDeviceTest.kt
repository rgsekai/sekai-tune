/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.rgsekai.sekaitune.canvas.CanvasConfiguration
import moe.rgsekai.sekaitune.canvas.CanvasConnectivity
import moe.rgsekai.sekaitune.canvas.CanvasPolicy
import moe.rgsekai.sekaitune.canvas.CanvasRequestPolicy
import moe.rgsekai.sekaitune.canvas.CanvasSettingsRepository
import moe.rgsekai.sekaitune.canvas.CanvasSettingsUseCases
import moe.rgsekai.sekaitune.canvas.CanvasSource
import moe.rgsekai.sekaitune.canvas.SekaiTuneCanvas
import moe.rgsekai.sekaitune.utils.dataStore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class CanvasHealthAndCascadeDeviceTest {

    @Test
    fun testOnDeviceBetterLyricsHealthAndCascade() = runBlocking {
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = CanvasSettingsRepository(context)
        val useCases = CanvasSettingsUseCases(repository)

        println("=================================================================")
        println("=== [CONTINUOUS SESSION PROBE] Starting Real Device Investigation ===")
        println("=================================================================")

        val prefs = context.dataStore.data.first()
        val spDc = prefs[moe.rgsekai.sekaitune.constants.SpotifySpDcKey]
        val spKey = prefs[moe.rgsekai.sekaitune.constants.SpotifySpKeyKey]
        val accessToken = prefs[moe.rgsekai.sekaitune.constants.SpotifyAccessTokenKey]
        val expiresAt = prefs[moe.rgsekai.sekaitune.constants.SpotifyAccessTokenExpiresAtKey]
        val accountName = prefs[moe.rgsekai.sekaitune.constants.SpotifyAccountNameKey]

        println("Initial DataStore Dump:")
        println("  -> [SpotifySpDcKey]: ${if (spDc.isNullOrBlank()) "<EMPTY/NULL>" else "PRESENT (len=${spDc.length}, prefix=${spDc.take(6)}...)"}")
        println("  -> [SpotifyAccessTokenExpiresAtKey]: $expiresAt (now=${System.currentTimeMillis()})")
        println("  -> [SpotifyAccountNameKey]: '$accountName'")
        println("  -> spotifyConnected: ${repository.spotifyConnected.first()}")

        val policy = CanvasPolicy(
            ready = true,
            configuration = CanvasConfiguration(
                enabled = true,
                source = CanvasSource.ALL,
            ),
            connectivity = CanvasConnectivity(
                online = true,
                wifi = true,
                metered = false,
            ),
        )

        val requestPolicy = CanvasRequestPolicy(
            preferredSource = CanvasSource.ALL,
            allowFallback = true,
            spDc = spDc?.takeIf { it.isNotBlank() },
        )

        val iterations = 5
        val delayBetweenIterationsMs = 15_000L

        for (i in 1..iterations) {
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            println("\n=================================================================")
            println("=== [ITERATION $i / $iterations] Timestamp: $timestamp ===")
            println("=================================================================")

            // 1. Provider Health Check
            val isSpotifyConnected = repository.spotifyConnected.first()
            val healthStatus = useCases.checkHealth(policy, spotifyConnected = isSpotifyConnected)
            println("  [HEALTH CHECK] BetterLyrics: ${healthStatus.betterLyrics} | AppleMusic: ${healthStatus.appleMusic} | TIDAL: ${healthStatus.tidal} | Spotify: ${healthStatus.spotify}")

            // 2. Direct Spotify isHealthy Check with spDc
            val spotifyHealthy = moe.rgsekai.sekaitune.canvas.SpotifyCanvasProvider.isHealthy(spDc)
            println("  [SPOTIFY DIRECT HEALTH] SpotifyCanvasProvider.isHealthy: $spotifyHealthy")

            // 3. Live Cascade for "Summertime Sadness"
            val artwork = SekaiTuneCanvas.getCanvas(
                song = "Summertime Sadness",
                artists = listOf("Lana Del Rey"),
                policy = requestPolicy,
                storefront = "us",
            )
            println("  [CASCADE RESULT] Name: '${artwork?.name}', Artist: '${artwork?.artist}'")
            println("  [CASCADE RESULT] Video URL: ${artwork?.preferredAnimationUrl}")
            println("  [CASCADE RESULT] Static URL: ${artwork?.static}")

            // 4. In-App Player Resolver Check (testing cache bypass/hit)
            val playerArtwork = resolveCanvasArtworkForPlayback(
                mediaId = "test_media_id_summertime_sadness_$i",
                songTitleRaw = "Summertime Sadness",
                artistNameRaw = "Lana Del Rey",
                storefront = "us",
                requireVertical = false,
                allowNetwork = true,
                canvasPolicy = requestPolicy,
            )
            println("  [PLAYER RESOLVER] Video URL: ${playerArtwork?.preferredAnimationUrl}")

            // 5. Verification
            assertTrue("Spotify must be reported AVAILABLE in Provider Health", healthStatus.spotify == moe.rgsekai.sekaitune.canvas.CanvasHealth.AVAILABLE)
            assertTrue("Spotify direct isHealthy must return true", spotifyHealthy)
            assertNotNull("Cascade must find animated video from Spotify", artwork?.preferredAnimationUrl)

            if (i < iterations) {
                println("Sleeping ${delayBetweenIterationsMs / 1000}s before next iteration...")
                kotlinx.coroutines.delay(delayBetweenIterationsMs)
            }
        }
        println("\n=================================================================")
        println("=== [CONTINUOUS SESSION PROBE COMPLETED SUCCESSFULLY] ===")
        println("=================================================================")
    }
}
