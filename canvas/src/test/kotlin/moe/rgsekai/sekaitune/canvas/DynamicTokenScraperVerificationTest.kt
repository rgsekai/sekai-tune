/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

    @Test
    fun testBetterLyricsHealthAndStaticFiltering() = runBlocking {
        println("=== [TEST] BetterLyrics Health & Fallback Verification ===")
        val isHealthy = SekaiTuneCanvas.isHealthy()
        println("SekaiTuneCanvas.isHealthy(): $isHealthy")

        println("Fetching Lana Del Rey - Summertime Sadness with Mode=ALL...")
        val canvasAll = SekaiTuneCanvas.getCanvas(
            song = "Summertime Sadness",
            artists = listOf("Lana Del Rey"),
            policy = CanvasRequestPolicy(
                preferredSource = CanvasSource.ALL,
            ),
        )
        println("Result with Mode=ALL: $canvasAll")
        println("preferredAnimationUrl: ${canvasAll?.preferredAnimationUrl}")

        val tidal = TidalCanvasProvider.getCanvas("Summertime Sadness", listOf("Lana Del Rey"), durationMs = null, countryCode = "US")
        println("Direct TIDAL: $tidal (animationUrl=${tidal?.preferredAnimationUrl})")

        val apple = AppleMusicProvider.getBySongArtist("Summertime Sadness", "Lana Del Rey", "Born to Die – The Paradise Edition", "us")
        println("Direct Apple Music (with album): $apple (animationUrl=${apple?.preferredAnimationUrl})")

        val appleAlbum = AppleMusicProvider.getByAlbumArtist("Born to Die - The Paradise Edition", "Lana Del Rey", "us")
        println("Direct Apple Music (album search): $appleAlbum (animationUrl=${appleAlbum?.preferredAnimationUrl})")

        val appleAlbum2 = AppleMusicProvider.getByAlbumArtist("Born to Die – The Paradise Edition", "Lana Del Rey", "us")
        println("Direct Apple Music (album search 2): $appleAlbum2 (animationUrl=${appleAlbum2?.preferredAnimationUrl})")

        val appleAlbum3 = AppleMusicProvider.getByAlbumArtist("Born to Die", "Lana Del Rey", "us")
        println("Direct Apple Music (album search 3): $appleAlbum3 (animationUrl=${appleAlbum3?.preferredAnimationUrl})")
    }

    @Test
    fun testDeepCompareProvidersForSummertimeSadness() = runBlocking {
        println("=================================================================")
        println("=== [DEEP PROBE] 1. TIDAL Raw API Inspection ===")
        println("=================================================================")
        val tidalToken = TidalCanvasProvider.tokenProvider.getToken()
        val httpClient = io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(io.ktor.client.plugins.HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        
        // Test Endpoint A: /v1/search?query=Lana Del Rey Summertime Sadness&types=TRACKS
        val tidalRespA = httpClient.get("https://api.tidal.com/v1/search") {
            header("Authorization", "Bearer $tidalToken")
            parameter("query", "Lana Del Rey Summertime Sadness")
            parameter("types", "TRACKS")
            parameter("countryCode", "US")
            parameter("limit", 10)
        }
        println("TIDAL /v1/search Status: ${tidalRespA.status}")
        val tidalBodyA = tidalRespA.bodyAsText()
        println("TIDAL /v1/search Raw Body (first 1500 chars): ${tidalBodyA.take(1500)}")

        // Search albums on TIDAL
        val tidalAlbumResp = httpClient.get("https://api.tidal.com/v1/search") {
            header("Authorization", "Bearer $tidalToken")
            parameter("query", "Born to Die The Paradise Edition Lana Del Rey")
            parameter("types", "ALBUMS")
            parameter("countryCode", "US")
            parameter("limit", 10)
        }
        println("TIDAL Albums Search Status: ${tidalAlbumResp.status}")
        val tidalAlbumBody = tidalAlbumResp.bodyAsText()
        println("TIDAL Albums Raw Body (first 1500 chars): ${tidalAlbumBody.take(1500)}")

        println("=================================================================")
        println("=== [DEEP PROBE] 2. Apple Music Motion Artwork Inspection ===")
        println("=================================================================")
        val amToken = AppleMusicProvider.tokenProvider.getToken()
        val amResp = httpClient.get("https://api.music.apple.com/v1/catalog/us/search") {
            header("Authorization", "Bearer $amToken")
            header("Origin", "https://music.apple.com")
            parameter("term", "Summertime Sadness Lana Del Rey")
            parameter("types", "songs,albums")
            parameter("limit", 10)
        }
        println("Apple Music Search Status: ${amResp.status}")
        val amSearchObj = Json.parseToJsonElement(amResp.bodyAsText()) as? JsonObject
        val songItems = amSearchObj?.get("results")?.let { (it as JsonObject)["songs"] }?.let { (it as JsonObject)["data"] as? kotlinx.serialization.json.JsonArray } ?: emptyList()
        val albumItems = amSearchObj?.get("results")?.let { (it as JsonObject)["albums"] }?.let { (it as JsonObject)["data"] as? kotlinx.serialization.json.JsonArray } ?: emptyList()

        println("Found ${songItems.size} songs and ${albumItems.size} albums in Apple Music search.")
        for (item in songItems) {
            val songObj = item as JsonObject
            val attrs = songObj["attributes"] as? JsonObject
            val songName = attrs?.get("name")
            val albumName = attrs?.get("albumName")
            val songId = songObj["id"]
            val relAlbumId = songObj["relationships"]?.let { (it as JsonObject)["albums"] }?.let { (it as JsonObject)["data"] as? kotlinx.serialization.json.JsonArray }?.firstOrNull()?.let { (it as JsonObject)["id"] }
            println("Song: id=$songId, name=$songName, albumName=$albumName, relAlbumId=$relAlbumId")
        }

        // Test fetching album details for top albums
        val albumIdsToTest = listOf("1442452465", "1440854064", "1440818783", "1445306981", "1594844052", "1440858075")
        for (albumId in albumIdsToTest) {
            val albumDetailResp = httpClient.get("https://api.music.apple.com/v1/catalog/us/albums/$albumId") {
                header("Authorization", "Bearer $amToken")
                header("Origin", "https://music.apple.com")
            }
            if (albumDetailResp.status == io.ktor.http.HttpStatusCode.OK) {
                val albumDetailObj = Json.parseToJsonElement(albumDetailResp.bodyAsText()) as? JsonObject
                val data = (albumDetailObj?.get("data") as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject
                val attrs = data?.get("attributes") as? JsonObject
                val name = (attrs?.get("name") as? JsonPrimitive)?.content
                val editorialVideo = attrs?.get("editorialVideo")
                println("Album ID: $albumId | Name: '$name' | editorialVideo present: ${editorialVideo != null}")
                if (editorialVideo != null) {
                    println("--> EDITORIAL VIDEO FOUND for $albumId ($name): $editorialVideo")
                }
            } else {
                println("Album ID: $albumId -> Status ${albumDetailResp.status}")
            }
        }

        println("=================================================================")
        println("=== [DEEP PROBE] 3. Spotify Client Token & Canvas Extraction ===")
        println("=================================================================")
        val spPageResp = httpClient.get("https://open.spotify.com/") {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        }
        val spPageHtml = spPageResp.bodyAsText()
        val configPattern = Regex("""<script[^>]*id="appServerConfig"[^>]*>([^<]+)</script>""")
        val match = configPattern.find(spPageHtml)
        println("Spotify appServerConfig found: ${match != null}")
        if (match != null) {
            val decodedJson = String(java.util.Base64.getDecoder().decode(match.groupValues[1]), Charsets.UTF_8)
            val jsonRoot = Json.parseToJsonElement(decodedJson) as? kotlinx.serialization.json.JsonObject
            val clientVersion = (jsonRoot?.get("clientVersion") as? JsonPrimitive)?.content ?: "1.2.58.498.g46b3dc22"
            println("Extracted Spotify clientVersion: $clientVersion")
            
            val clientIdsToTest = listOf(
                "27964a2583204968800164c4c9fa30eb",
                "65b708073fc0480ea92a077233ca87bd",
                "d8a5dee97f0c409e884d427966f30a64",
            )
            for (cid in clientIdsToTest) {
                val payload = buildJsonObject {
                    putJsonObject("client_data") {
                        put("client_version", clientVersion)
                        put("client_id", cid)
                        putJsonObject("js_sdk_data") {
                            put("device_brand", "unknown")
                            put("device_model", "unknown")
                            put("os", "android")
                            put("os_version", "14")
                            put("device_id", java.util.UUID.randomUUID().toString())
                            put("device_type", "smartphone")
                        }
                    }
                }
                val res = httpClient.post("https://clienttoken.spotify.com/v1/clienttoken") {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    header("Accept", "application/json")
                    header("Content-Type", "application/json")
                    setBody(payload.toString())
                }
                println("ClientId $cid -> Status ${res.status}, Body: ${res.bodyAsText().take(300)}")
            }
        }
    }
}
