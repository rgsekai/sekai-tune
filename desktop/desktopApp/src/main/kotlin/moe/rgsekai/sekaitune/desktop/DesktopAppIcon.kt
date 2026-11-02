package moe.rgsekai.sekaitune.desktop

import androidx.compose.ui.awt.ComposeWindow
import moe.rgsekai.sekaitune.desktop.generated.resources.Res
import java.awt.Image
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.image.BaseMultiResolutionImage
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

internal object DesktopAppIcon {

    private val SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    @Volatile
    private var cachedBaseImage: BufferedImage? = null

    @Volatile
    private var cachedIcons: List<BufferedImage>? = null

    internal fun extractPngBytes(svgBytes: ByteArray): ByteArray? {
        val text = String(svgBytes, Charsets.UTF_8)
        val marker = "base64,"
        val start = text.indexOf(marker)
        if (start < 0) return null
        val base64Start = start + marker.length
        val end = text.indexOf('"', base64Start)
        if (end < 0) return null
        val b64 = text.substring(base64Start, end).trim()
        return runCatching { Base64.getDecoder().decode(b64) }.getOrNull()
    }

    private fun loadBaseImage(): BufferedImage? {
        cachedBaseImage?.let { return it }
        val svgBytes = runCatching {
            kotlinx.coroutines.runBlocking { Res.readBytes("drawable/logo.svg") }
        }.getOrNull() ?: return null

        val pngBytes = extractPngBytes(svgBytes)
        val decoded = if (pngBytes != null) {
            runCatching { ImageIO.read(ByteArrayInputStream(pngBytes)) }.getOrNull()
        } else null

        if (decoded != null) {
            cachedBaseImage = decoded
            return decoded
        }
        return null
    }

    fun scaleImage(src: BufferedImage, width: Int, height: Int): BufferedImage {
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(src, 0, 0, width, height, null)
        g.dispose()
        return scaled
    }

    suspend fun loadIconImages(): List<BufferedImage> {
        cachedIcons?.let { return it }
        val base = loadBaseImage() ?: return emptyList()
        val images = SIZES.map { size -> scaleImage(base, size, size) }
        cachedIcons = images
        return images
    }

    fun getTrayImage(): Image? {
        val base = loadBaseImage() ?: return null
        val traySize = runCatching {
            if (SystemTray.isSupported()) SystemTray.getSystemTray().trayIconSize else null
        }.getOrNull()
        val targetW = traySize?.width?.takeIf { it > 0 } ?: 16
        val targetH = traySize?.height?.takeIf { it > 0 } ?: 16

        val ladder = listOf(16, 20, 24, 32, 40, 48).map { scaleImage(base, it, it) }
        val target = scaleImage(base, targetW, targetH)
        val allVariants = (listOf(target) + ladder).distinctBy { it.width to it.height }
        return BaseMultiResolutionImage(*allVariants.toTypedArray())
    }

    suspend fun applyToWindow(window: ComposeWindow) {
        val images = loadIconImages()
        if (images.isNotEmpty()) {
            window.iconImages = images
            getTrayImage()?.let { DesktopAwtTray.setIcon(it) }
        }
        if (DesktopPlatform.isWindows) {
            val smallImg = images.find { it.width == 16 } ?: images.firstOrNull()
            val bigImg = images.find { it.width == 32 } ?: images.find { it.width == 48 } ?: images.firstOrNull()
            if (smallImg != null && bigImg != null) {
                val smallPixels = IntArray(smallImg.width * smallImg.height)
                smallImg.getRGB(0, 0, smallImg.width, smallImg.height, smallPixels, 0, smallImg.width)
                val bigPixels = IntArray(bigImg.width * bigImg.height)
                bigImg.getRGB(0, 0, bigImg.width, bigImg.height, bigPixels, 0, bigImg.width)
                DesktopWindowsFrame.setWindowIcons(bigPixels, bigImg.width, bigImg.height, smallPixels, smallImg.width, smallImg.height)
            }
        }
    }
}
