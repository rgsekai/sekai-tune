/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.screens.artist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleArtistArtTest {

    @Test
    fun parse_withLogoAndArtwork_returnsArtWithCalculatedAspect() {
        val html = """
            <html>
            <script id="serialized-server-data">
            {
                "data": [
                    {
                        "header": {
                            "artistLogo": {
                                "dictionary": {
                                    "url": "https://is1-ssl.mzstatic.com/image/thumb/logo/{w}x{h}{c}.{f}",
                                    "width": 1000,
                                    "height": 400
                                }
                            },
                            "artwork": {
                                "dictionary": {
                                    "url": "https://is1-ssl.mzstatic.com/image/thumb/hero/{w}x{h}{c}.{f}",
                                    "width": 1200,
                                    "height": 800,
                                    "bgColor": "112233",
                                    "textColor1": "ffffff"
                                }
                            },
                            "videoArtwork": {
                                "dictionary": {
                                    "motionArtistSquare1x1": {
                                        "video": "https://video-ssl.itunes.apple.com/motion.m3u8"
                                    }
                                }
                            },
                            "keyColor": "ff0055"
                        }
                    }
                ]
            }
            </script>
            </html>
        """.trimIndent()

        val parsed = AppleArtistArtRepository.parse(html)
        assertNotNull(parsed)
        parsed!!
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/hero/1200x800bb.jpg", parsed.heroUrl)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/logo/1000x400bb.png", parsed.logoUrl)
        assertEquals(2.5f, parsed.logoAspect, 0.01f)
        assertEquals("https://video-ssl.itunes.apple.com/motion.m3u8", parsed.videoUrl)
        assertEquals(0xFF112233.toInt(), parsed.background)
        assertEquals(0xFFffffff.toInt(), parsed.accent)
        assertEquals(0xFFff0055.toInt(), parsed.keyColor)
    }

    @Test
    fun parse_withoutLogo_returnsArtWithNullLogo() {
        val html = """
            <html>
            <script id="serialized-server-data">
            {
                "data": [
                    {
                        "header": {
                            "artwork": {
                                "dictionary": {
                                    "url": "https://is1-ssl.mzstatic.com/image/thumb/hero/{w}x{h}{c}.{f}",
                                    "width": 1200,
                                    "height": 800,
                                    "bgColor": "000000",
                                    "textColor1": "ffffff"
                                }
                            }
                        }
                    }
                ]
            }
            </script>
            </html>
        """.trimIndent()

        val parsed = AppleArtistArtRepository.parse(html)
        assertNotNull(parsed)
        parsed!!
        assertNull(parsed.logoUrl)
        assertEquals(1.0f, parsed.logoAspect, 0.01f)
        assertNull(parsed.videoUrl)
    }

    @Test
    fun parse_missingMarker_returnsNull() {
        assertNull(AppleArtistArtRepository.parse("<html><body>No marker here</body></html>"))
    }
}
