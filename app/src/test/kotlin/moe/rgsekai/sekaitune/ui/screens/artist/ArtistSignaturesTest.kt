/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.screens.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistSignaturesTest {

    @Test
    fun normalizeBaseUrl_trimsTrailingSlashesAndWhitespace() {
        assertEquals(
            "https://artist-signature.vercel.app",
            ArtistSignatureRepository.normalizeBaseUrl("  https://artist-signature.vercel.app///  ")
        )
    }

    @Test
    fun isValidBaseUrl_validatesHttpAndHttpsOnly() {
        assertTrue(ArtistSignatureRepository.isValidBaseUrl("https://artist-signature.vercel.app"))
        assertTrue(ArtistSignatureRepository.isValidBaseUrl("http://localhost:3000"))
        assertFalse(ArtistSignatureRepository.isValidBaseUrl("ftp://example.com"))
        assertFalse(ArtistSignatureRepository.isValidBaseUrl("javascript:alert(1)"))
        assertFalse(ArtistSignatureRepository.isValidBaseUrl(""))
    }

    @Test
    fun effectiveBaseUrl_returnsNullWhenDisabled() {
        assertNull(ArtistSignatureRepository.effectiveBaseUrl(enabled = false, baseUrl = "https://example.com"))
    }

    @Test
    fun effectiveBaseUrl_defaultsToDefaultUrlWhenBlank() {
        assertEquals(
            ArtistSignatureRepository.DEFAULT_BASE_URL,
            ArtistSignatureRepository.effectiveBaseUrl(enabled = true, baseUrl = "")
        )
    }

    @Test
    fun parse_extractsBestSignatureFromApiResponse() {
        val json = """
            {
                "signatures": [
                    {
                        "asset": {
                            "url": "https://cdn.example.com/sig1.png",
                            "format": "png",
                            "width": 300,
                            "height": 100
                        },
                        "license": { "status": "known" },
                        "verification": "unverified"
                    },
                    {
                        "asset": {
                            "url": "https://cdn.example.com/sig2.svg",
                            "format": "svg",
                            "width": 600,
                            "height": 200
                        },
                        "license": { "status": "known" },
                        "verification": "verified"
                    }
                ]
            }
        """.trimIndent()

        val parsed = ArtistSignatureRepository.parse(json)
        assertNotNull(parsed)
        assertEquals("https://cdn.example.com/sig2.svg", parsed?.imageUrl)
        assertEquals(3.0f, parsed!!.aspect, 0.01f)
        assertEquals("svg", parsed.format)
    }

    @Test
    fun parse_returnsNullForAmbiguousMatches() {
        val json = """
            {
                "matches": [
                    { "name": "Artist A" },
                    { "name": "Artist B" }
                ]
            }
        """.trimIndent()

        val parsed = ArtistSignatureRepository.parse(json)
        assertNull(parsed)
    }

    @Test
    fun signatureInk_luminanceCalculation() {
        // Pure black 0xFF000000 -> 0.0f
        val blackLum = SignatureInkTransformation.luminance(0xFF000000.toInt())
        assertEquals(0.0f, blackLum, 0.01f)

        // Pure white 0xFFFFFFFF -> 1.0f
        val whiteLum = SignatureInkTransformation.luminance(0xFFFFFFFF.toInt())
        assertEquals(1.0f, whiteLum, 0.01f)
    }
}
