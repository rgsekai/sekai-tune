package moe.rgsekai.sekaitune.shared.platform

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformSeamsTest {
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

    @Test fun crypto() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1("abc".encodeToByteArray()).hex())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256("abc".encodeToByteArray()).hex())
        assertEquals(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            hmacSha256("key".encodeToByteArray(), "The quick brown fox jumps over the lazy dog".encodeToByteArray()).hex(),
        )
    }

    @Test fun nfd() = assertEquals(2, normalizeNfd("é").length)

    @Test fun netHttp() {
        MockWebServer().use { s ->
            s.enqueue(MockResponse().setBody("hi"))
            s.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
            val ok = NetHttp.execute(HttpRequest(s.url("/a").toString()))
            assertTrue(ok.isSuccessful); assertEquals("hi", ok.text())
            val nf = NetHttp.execute(HttpRequest(s.url("/b").toString(), callTimeoutMs = 5000))
            assertFalse(nf.isSuccessful); assertEquals(404, nf.code)
        }
    }
}
