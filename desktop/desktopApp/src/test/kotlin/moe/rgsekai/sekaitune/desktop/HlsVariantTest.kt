package moe.rgsekai.sekaitune.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HlsVariantTest {
    private val master = """
        #EXTM3U
        #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=683208,CODECS="avc1.64001f",RESOLUTION=486x486,URI="iframe.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=2743775,CODECS="avc1.64001f",RESOLUTION=768x768
        https://cdn.example/video_768.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1900475,CODECS="hvc1.2.20000000.L123.B0",RESOLUTION=600x600
        https://cdn.example/hevc_600.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=306105,CODECS="avc1.64001f",RESOLUTION=360x360
        video_360.m3u8
    """.trimIndent()

    @Test
    fun takesSmallestH264AtLeastTheEdge() =
        assertEquals("https://cdn.example/video_768.m3u8", pickVariant(master, "https://apple.example/a/master.m3u8"))

    @Test
    fun fallsBackToLargestAndResolvesRelative() =
        assertEquals("https://apple.example/a/video_360.m3u8",
            pickVariant(master.replace("RESOLUTION=768x768", "RESOLUTION=300x300"), "https://apple.example/a/master.m3u8"))

    @Test
    fun mediaPlaylistHasNoVariant() =
        assertNull(pickVariant("#EXTM3U\n#EXTINF:4.0,\nseg0.ts", "https://x/a.m3u8"))
}
