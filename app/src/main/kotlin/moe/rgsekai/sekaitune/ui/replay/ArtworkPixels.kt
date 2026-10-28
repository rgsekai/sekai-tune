package moe.rgsekai.sekaitune.ui.replay

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

fun ImageRequest.Builder.forPixelAccess(): ImageRequest.Builder = allowHardware(false)

fun Image.toImageBitmap(): ImageBitmap = toBitmap().asImageBitmap()

fun imageBitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

fun paletteSwatches(image: ImageBitmap, maxColors: Int, clearFilters: Boolean): List<Swatch> {
    val builder = Palette.from(image.asAndroidBitmap()).maximumColorCount(maxColors)
    if (clearFilters) builder.clearFilters()
    return builder.generate().swatches.map { Swatch(it.rgb, it.population) }
}


class Swatch(val rgb: Int, val population: Int)
