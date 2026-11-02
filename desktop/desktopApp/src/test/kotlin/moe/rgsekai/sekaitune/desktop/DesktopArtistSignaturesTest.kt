package moe.rgsekai.sekaitune.desktop

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The signature API's response parsing and the scan-to-ink transform, which Android pins too. */
class DesktopArtistSignaturesTest {

    @Test
    fun anAmbiguousNameYieldsNothing() {
        assertNull(DesktopArtistSignatures.parse("""{"matches":[{"name":"A"},{"name":"B"}]}"""))
    }

    @Test
    fun aRedistributableSvgBeatsAnUnlicensedPng() {
        val body = """{"signatures":[
            {"license":{"status":"unknown"},"asset":{"url":"https://x/a.png","format":"png","width":900,"height":300}},
            {"license":{"status":"known"},"asset":{"url":"https://x/b.svg","format":"svg","width":600,"height":200}}
        ]}"""
        val best = DesktopArtistSignatures.parse(body)!!
        assertEquals("https://x/b.svg", best.imageUrl)
        assertEquals("svg", best.format)
        assertEquals(3f, best.aspect)
    }

    @Test
    fun aBareStringAssetAndLegacyUrlsAreAccepted() {
        assertEquals("https://x/a.png", DesktopArtistSignatures.parse("""{"signatures":[{"asset":"https://x/a.png"}]}""")!!.imageUrl)
        assertEquals("https://x/o.svg", DesktopArtistSignatures.parse("""{"signatures":[{"original_url":"https://x/o.svg"}]}""")!!.imageUrl)
    }

    @Test
    fun onlyHttpAddressesAreEverRequested() {
        assertFalse(DesktopArtistSignatures.isValidBaseUrl(""))
        assertFalse(DesktopArtistSignatures.isValidBaseUrl("file:///etc/passwd"))
        assertTrue(DesktopArtistSignatures.isValidBaseUrl("https://sig.example.com/"))
        assertNull(DesktopArtistSignatures.effectiveBaseUrl(false, "https://sig.example.com"))
        assertEquals("https://sig.example.com", DesktopArtistSignatures.effectiveBaseUrl(true, " https://sig.example.com/ "))
    }

    @Test
    fun inkOnPaperBecomesWhiteStrokesOnTransparency() {
        val scan = BufferedImage(10, 1, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 10) scan.setRGB(x, 0, 0xFFFFFFFF.toInt())
        scan.setRGB(0, 0, 0xFF000000.toInt())
        val ink = scan.signatureInk()
        assertEquals(0xFFFFFFFF.toInt(), ink.getRGB(0, 0))
        assertEquals(0, ink.getRGB(5, 0) ushr 24)
    }

    @Test
    fun aPhotographIsLeftAlone() {
        val photo = BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 4) photo.setRGB(x, 0, 0xFF303030.toInt())
        assertSame(photo, photo.signatureInk())
    }
}
