/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.discord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import moe.rgsekai.sekaitune.ui.utils.resize
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class SpinningArtwork(
    private val scope: CoroutineScope,
    private val onReady: () -> Unit,
) {
    private class Made(val url: String, val usableUntilMs: Long)

    private class Host(val name: String, val keepsMs: Long, val upload: (ByteArray) -> String)

    private val made = ConcurrentHashMap<String, Made>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val working = ConcurrentHashMap.newKeySet<String>()

    fun urlFor(sourceUrl: String, style: SpinStyle): String? {
        val key = "$sourceUrl|${style.secondsPerTurn}|${style.fps}"
        val now = System.currentTimeMillis()
        made[key]?.let { if (now < it.usableUntilMs) return it.url }
        if (failedAt[key]?.let { now - it < RetryAfterFailureMs } == true) return null
        if (working.add(key)) {
            scope.launch {
                try {
                    made[key] = upload(render(download(sourceUrl), style))
                    onReady()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(LogTag).w(e, "couldn't make the spinning artwork")
                    failedAt[key] = System.currentTimeMillis()
                } finally {
                    working.remove(key)
                }
            }
        }
        return null
    }

    private suspend fun download(url: String): Bitmap = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url.resize(SourceSize, SourceSize)).build()
        val bytes = DiscordHttp.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "artwork download failed: HTTP ${response.code}" }
            response.body!!.bytes()
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("artwork isn't a picture")
    }

    private suspend fun render(source: Bitmap, style: SpinStyle): ByteArray = withContext(Dispatchers.Default) {
        val (frames, frameCentis) = framesFor(style)
        val square = centerSquare(source)
        val frame = Bitmap.createBitmap(Size, Size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val shader = BitmapShader(square, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val matrix = Matrix()
        val pixels = IntArray(Size * Size)
        val half = Size / 2f
        val scale = Size.toFloat() / square.width

        fun draw(degrees: Float) {
            frame.eraseColor(Color.TRANSPARENT)
            matrix.setScale(scale, scale)
            matrix.postRotate(degrees, half, half)
            shader.setLocalMatrix(matrix)
            canvas.drawCircle(half, half, half, paint)
            frame.getPixels(pixels, 0, Size, 0, 0, Size, Size)
        }
        draw(0f)
        val palette = Palette.of(pixels)
        val encoder = GifEncoder(Size, Size, palette.colors)
        val indexes = ByteArray(Size * Size)
        for (i in 0 until frames) {
            if (i > 0) draw(360f * i / frames)
            for (p in pixels.indices) indexes[p] = palette.indexOf(pixels[p]).toByte()
            encoder.addFrame(indexes, frameCentis)
        }
        frame.recycle()
        if (square !== source) square.recycle()
        encoder.finish()
    }

    private fun framesFor(style: SpinStyle): Pair<Int, Int> {
        val turnCentis = style.secondsPerTurn * 100
        var centis = (100f / style.fps).roundToInt().coerceAtLeast(2)
        if (turnCentis / centis > MaxFrames) centis = ceil(turnCentis / MaxFrames.toFloat()).toInt()
        val frames = (turnCentis / centis.toFloat()).roundToInt().coerceAtLeast(MinFrames)
        return frames to centis
    }

    private fun centerSquare(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        if (source.width == source.height) return source
        return Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    }

    private suspend fun upload(gif: ByteArray): Made = withContext(Dispatchers.IO) {
        var lastProblem: Exception? = null
        for (host in Hosts) {
            try {
                val url = host.upload(gif)
                check(url.startsWith("https://")) { "${host.name} sent back \"${url.take(120)}\"" }
                return@withContext Made(url, System.currentTimeMillis() + host.keepsMs * 9 / 10)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(LogTag).d(e, "${host.name} didn't take the GIF")
                lastProblem = e
            }
        }
        throw lastProblem ?: IllegalStateException("no host to upload to")
    }

    private class Palette(val colors: IntArray) {
        private val nearest = IntArray(1 shl 15) { -1 }

        fun indexOf(argb: Int): Int {
            if (argb ushr 24 < 128) return GifEncoder.Transparent
            val key = (argb shr 9 and 0x7C00) or (argb shr 6 and 0x3E0) or (argb shr 3 and 0x1F)
            val known = nearest[key]
            if (known >= 0) return known
            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF
            var best = 0
            var bestDistance = Int.MAX_VALUE
            for (i in colors.indices) {
                val c = colors[i]
                val dr = (c shr 16 and 0xFF) - r
                val dg = (c shr 8 and 0xFF) - g
                val db = (c and 0xFF) - b
                val distance = 2 * dr * dr + 4 * dg * dg + 3 * db * db
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = i
                }
            }
            nearest[key] = best
            return best
        }

        companion object {
            fun of(pixels: IntArray): Palette {
                val opaque = pixels.filter { it ushr 24 >= 128 }.toIntArray()
                if (opaque.isEmpty()) return Palette(intArrayOf(0))
                val boxes = mutableListOf(opaque)
                while (boxes.size < GifEncoder.Transparent) {
                    val widest = boxes.withIndex().filter { it.value.size > 1 }.maxByOrNull { spread(it.value) } ?: break
                    if (spread(widest.value) == 0) break
                    val (low, high) = split(widest.value)
                    boxes[widest.index] = low
                    boxes.add(high)
                }
                return Palette(boxes.map(::average).toIntArray())
            }

            private fun channel(argb: Int, which: Int) = argb shr (16 - which * 8) and 0xFF

            private fun range(box: IntArray, which: Int): Int {
                var min = 255
                var max = 0
                for (c in box) {
                    val v = channel(c, which)
                    if (v < min) min = v
                    if (v > max) max = v
                }
                return max - min
            }

            private fun spread(box: IntArray) = maxOf(range(box, 0), range(box, 1), range(box, 2))

            private fun split(box: IntArray): Pair<IntArray, IntArray> {
                val which = (0..2).maxBy { range(box, it) }
                val sorted = box.sortedBy { channel(it, which) }
                val middle = sorted.size / 2
                return sorted.subList(0, middle).toIntArray() to sorted.subList(middle, sorted.size).toIntArray()
            }

            private fun average(box: IntArray): Int {
                var r = 0L
                var g = 0L
                var b = 0L
                for (c in box) {
                    r += c shr 16 and 0xFF
                    g += c shr 8 and 0xFF
                    b += c and 0xFF
                }
                val n = box.size
                return ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            }
        }
    }

    companion object {
        private const val LogTag = "SpinningArtwork"

        private const val Size = 96
        private const val SourceSize = 256
        private const val MaxFrames = 400
        private const val MinFrames = 12

        private const val RetryAfterFailureMs = 10 * 60_000L
        private const val HourMs = 60 * 60_000L

        private val json = Json { ignoreUnknownKeys = true }

        private val Hosts = listOf(
            Host("uguu.se", keepsMs = 3 * HourMs) { gif ->
                val reply = post("https://uguu.se/upload", "files[]", gif)
                json.parseToJsonElement(reply).jsonObject["files"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty()
            },
            Host("catbox.moe", keepsMs = 30 * 24 * HourMs) { gif ->
                post("https://catbox.moe/user/api.php", "fileToUpload", gif, "reqtype" to "fileupload")
            },
            Host("litterbox.catbox.moe", keepsMs = 72 * HourMs) { gif ->
                post(
                    "https://litterbox.catbox.moe/resources/internals/api.php", "fileToUpload", gif,
                    "reqtype" to "fileupload", "time" to "72h",
                )
            },
        )

        private fun post(url: String, fileField: String, gif: ByteArray, vararg fields: Pair<String, String>): String {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                fields.forEach { (name, value) -> addFormDataPart(name, value) }
                addFormDataPart(fileField, "sekaitune.gif", gif.toRequestBody("image/gif".toMediaType()))
            }.build()
            val request = Request.Builder().url(url).post(body).build()
            return DiscordHttp.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty().trim()
                check(response.isSuccessful) { "HTTP ${response.code}" }
                text
            }
        }
    }
}

