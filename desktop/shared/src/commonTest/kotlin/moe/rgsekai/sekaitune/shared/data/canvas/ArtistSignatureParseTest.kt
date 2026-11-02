package moe.rgsekai.sekaitune.shared.data.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtistSignatureParseTest {
    @Test
    fun picksLicensedSvgOverPlainPng() {
        val body = """{"signatures":[
            {"asset":"https://x/a.png","width":100,"height":100},
            {"asset":{"url":"https://x/b.svg","width":300,"height":100},"license":{"status":"known"}}]}"""
        val sig = ArtistSignatureRepository.parse(body)!!
        assertEquals("https://x/b.svg", sig.imageUrl)
        assertEquals("svg", sig.format)
        assertEquals(3f, sig.aspect)
    }

    @Test
    fun ambiguousOrGarbageIsNull() {
        assertNull(ArtistSignatureRepository.parse("""{"matches":[]}"""))
        assertNull(ArtistSignatureRepository.parse("nope"))
    }
}
