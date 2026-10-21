/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.playback.sources.addon

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AddonClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var httpClient: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        httpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testFetchManifest_success() = runBlocking {
        val manifestJson = """
            {
                "id": "moe.sekai.test",
                "name": "HiFi Audio Addon",
                "version": "1.0.4",
                "description": "High resolution lossless audio stream provider",
                "resources": ["search", "stream"]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(manifestJson)
        )

        val baseUrl = mockWebServer.url("/USER_TOKEN_XYZ").toString()
        val client = AddonClient(baseUrl, httpClient)

        val response = client.fetchManifest()
        assertTrue("Expected Success response", response is AddonResponse.Success)

        val manifest = (response as AddonResponse.Success).value
        assertEquals("moe.sekai.test", manifest.id)
        assertEquals("HiFi Audio Addon", manifest.name)
        assertEquals("1.0.4", manifest.version)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/USER_TOKEN_XYZ/manifest.json", recordedRequest.path)
    }

    @Test
    fun testHttp429_retriesWithBackoffAndSucceeds() = runBlocking {
        val manifestJson = """{"id":"test","name":"Backoff Addon","version":"2.0"}"""

        // 1st request returns 429 with Retry-After: 1
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "1")
                .setBody("""{"error":"Rate limited"}""")
        )

        // 2nd request succeeds
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(manifestJson)
        )

        val client = AddonClient(mockWebServer.url("/").toString(), httpClient)
        val response = client.fetchManifest()

        assertTrue("Expected Success response after 429 retry", response is AddonResponse.Success)
        val manifest = (response as AddonResponse.Success).value
        assertEquals("Backoff Addon", manifest.name)

        assertEquals("Expected 2 requests made to mock server", 2, mockWebServer.requestCount)
    }

    @Test
    fun testMalformedJson_returnsSerializationError_neverCrashes() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{ invalid_json_syntax: true, [")
        )

        val client = AddonClient(mockWebServer.url("/").toString(), httpClient)
        val response = client.fetchManifest()

        assertTrue("Expected SerializationError for bad JSON", response is AddonResponse.SerializationError)
        val error = response as AddonResponse.SerializationError
        assertNotNull(error.cause)
    }

    @Test
    fun testSearchAndStream_parsesCorrectly() = runBlocking {
        val searchJson = """
            {
                "tracks": [
                    {
                        "id": "track_123",
                        "title": "Racing into the Night",
                        "artists": ["YOASOBI"],
                        "duration": 261,
                        "format": "FLAC",
                        "audioQuality": "LOSSLESS"
                    }
                ]
            }
        """.trimIndent()

        val streamJson = """
            {
                "url": "https://cdn.example.com/audio/track_123.flac",
                "format": "FLAC",
                "bitrate": 1411,
                "sampleRate": 44100,
                "bitDepth": 16
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(searchJson)
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(streamJson)
        )

        val client = AddonClient(mockWebServer.url("/AUTH_TOKEN").toString(), httpClient)

        val searchResponse = client.search("YOASOBI")
        assertTrue(searchResponse is AddonResponse.Success)
        val tracks = (searchResponse as AddonResponse.Success).value
        assertEquals(1, tracks.size)
        assertEquals("track_123", tracks[0].id)
        assertEquals("Racing into the Night", tracks[0].title)

        val streamResponse = client.stream("track_123")
        assertTrue(streamResponse is AddonResponse.Success)
        val stream = (streamResponse as AddonResponse.Success).value
        assertEquals("https://cdn.example.com/audio/track_123.flac", stream.resolvedUrl())
        assertEquals(1411, stream.resolvedBitrate())
    }

    @Test
    fun testFetchManifest_withNumericBooleansAndSettings_parsesCleanly() = runBlocking {
        // Manifest format sent by real-world addons (e.g. Qobuz) where allowDownloads is integer 0
        val manifestJson = """
            {
                "id": "com.example.addon",
                "name": "Qobuz",
                "version": "1.0.0",
                "resources": ["search", "stream"],
                "allowDownloads": 0,
                "settings": [
                    {
                        "key": "quality",
                        "type": "select",
                        "default": "lossless",
                        "options": [
                            {"label": "Lossless", "value": "lossless"},
                            {"label": "High", "value": "high"}
                        ]
                    }
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(manifestJson)
        )

        val client = AddonClient(mockWebServer.url("/").toString(), httpClient)
        val response = client.fetchManifest()

        assertTrue("Expected Success response for numeric boolean", response is AddonResponse.Success)
        val manifest = (response as AddonResponse.Success).value
        assertEquals("com.example.addon", manifest.id)
        assertEquals("Qobuz", manifest.name)
        assertEquals(false, manifest.downloadsAllowed)
        assertEquals(1, manifest.settings.size)
        assertEquals("lossless", manifest.settings[0].defaultValue)
    }

    @Test
    fun testNetworkFailure_returnsNetworkErrorGracefully() = runBlocking {
        val serverUrl = mockWebServer.url("/").toString()
        mockWebServer.shutdown() // Force network connection failure

        val client = AddonClient(serverUrl, httpClient)
        val response = client.fetchManifest()

        assertTrue("Expected NetworkError on connection failure", response is AddonResponse.NetworkError)
    }

    @Test
    fun testTokenPathPreservation_andHeaders() = runBlocking {
        // Test base URL with token path: /MY_SECRET_TOKEN
        val baseUrl = mockWebServer.url("/MY_SECRET_TOKEN").toString()
        val client = AddonClient(baseUrl, httpClient)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"tracks":[{"id":"t1","title":"Song"}]}""")
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"url":"https://cdn.example.com/stream/t1.flac","format":"FLAC"}""")
        )

        val searchRes = client.search("Song")
        assertTrue(searchRes is AddonResponse.Success)

        val recordedSearch = mockWebServer.takeRequest()
        // Must preserve the token path!
        assertTrue("Expected path to start with /MY_SECRET_TOKEN/search, got: ${recordedSearch.path}",
            recordedSearch.path?.startsWith("/MY_SECRET_TOKEN/search") == true)
        assertEquals("SekaiTune/3.0", recordedSearch.getHeader("User-Agent"))
        assertEquals("application/json", recordedSearch.getHeader("Accept"))
        // Check query params: q=Song and quality is NOT "best"
        assertTrue("Must contain query param q=Song", recordedSearch.requestUrl?.queryParameter("q") == "Song")
        assertTrue("Must NOT send quality=best", recordedSearch.requestUrl?.queryParameter("quality") != "best")

        val streamRes = client.stream("t1")
        assertTrue(streamRes is AddonResponse.Success)

        val recordedStream = mockWebServer.takeRequest()
        assertTrue("Expected path to start with /MY_SECRET_TOKEN/stream/t1, got: ${recordedStream.path}",
            recordedStream.path?.startsWith("/MY_SECRET_TOKEN/stream/t1") == true)
    }

    @Test
    fun testHttpError_extractsServerBodySnippet() = runBlocking {
        val errorJson = """{"error":"quality must be LOSSLESS, HIGH, or LOW"}"""

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorJson)
        )

        val client = AddonClient(mockWebServer.url("/").toString(), httpClient)
        val response = client.search("test")

        assertTrue("Expected HttpError", response is AddonResponse.HttpError)
        val httpError = response as AddonResponse.HttpError
        assertEquals(400, httpError.statusCode)
        assertTrue("Error message must contain server error body snippet, got: ${httpError.message}",
            httpError.message.contains("quality must be LOSSLESS"))
    }

    @Test
    fun testNormalizeBaseUrl_keepsTokenPathAndStripsJson() {
        assertEquals(
            "https://addon.example.com/USER_TOKEN",
            AddonClient.normalizeBaseUrl("https://addon.example.com/USER_TOKEN/manifest.json")
        )
        assertEquals(
            "https://addon.example.com/USER_TOKEN",
            AddonClient.normalizeBaseUrl("https://addon.example.com/USER_TOKEN/addon.json")
        )
        assertEquals(
            "https://addon.example.com/USER_TOKEN",
            AddonClient.normalizeBaseUrl("https://addon.example.com/USER_TOKEN/")
        )
        assertEquals(
            "https://addon.example.com/USER_TOKEN",
            AddonClient.normalizeBaseUrl("https://addon.example.com/USER_TOKEN/manifest.json?api_key=123")
        )
    }
}
