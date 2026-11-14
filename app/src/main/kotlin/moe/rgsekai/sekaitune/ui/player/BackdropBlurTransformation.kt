// BackdropBlurTransformation.kt

package moe.rgsekai.sekaitune.ui.player

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation

class BackdropBlurTransformation(private val radiusPx: Int) : Transformation() {

    override val cacheKey: String = "${this::class.qualifiedName}-$radiusPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (radiusPx <= 0) return input
        val width = input.width
        val height = input.height
        if (width == 0 || height == 0) return input

        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)

        repeat(3) {
            boxBlurHorizontal(pixels, width, height, radiusPx)
            boxBlurVertical(pixels, width, height, radiusPx)
        }

        val output = createBitmap(width, height, input.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun boxBlurHorizontal(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val row = IntArray(width)
        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) row[x] = pixels[rowStart + x]
            for (x in 0 until width) {
                var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                val from = (x - radius).coerceAtLeast(0)
                val to = (x + radius).coerceAtMost(width - 1)
                for (i in from..to) {
                    val p = row[i]
                    a += (p ushr 24) and 0xFF
                    r += (p ushr 16) and 0xFF
                    g += (p ushr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                pixels[rowStart + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }

    private fun boxBlurVertical(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val col = IntArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) col[y] = pixels[y * width + x]
            for (y in 0 until height) {
                var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                val from = (y - radius).coerceAtLeast(0)
                val to = (y + radius).coerceAtMost(height - 1)
                for (i in from..to) {
                    val p = col[i]
                    a += (p ushr 24) and 0xFF
                    r += (p ushr 16) and 0xFF
                    g += (p ushr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                pixels[y * width + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }
}
