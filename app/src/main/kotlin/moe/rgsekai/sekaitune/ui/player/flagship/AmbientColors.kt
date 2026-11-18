package moe.rgsekai.sekaitune.ui.player.flagship

import android.content.Context
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import moe.rgsekai.sekaitune.ui.utils.resize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// how long the colours take to drift to a new set
private const val FadeMs = 1_400

// a new set that's barely different is let go, so a video's small changes never make it flicker
private const val MinChange = 0.035f

// how often a fade hands out new colours: soft colours stepping 30 times a second look as smooth as 120
private const val PublishMs = 33L

// the picture is read this small: plenty for four soft colours
private const val PicturePx = 32

// where the four colours come from on the picture, and where the backdrop puts them
internal const val ToneSeamLeft = 0
internal const val ToneSeamRight = 1
internal const val ToneDeep = 2
internal const val ToneAccent = 3

// not a colour you see: a grey whose level says how much of the picture under the text is near white
internal const val ToneGlare = 4
private const val ToneCount = 5

// a pixel this bright (rec. 709 luma out of 255) is one white text gets lost on
private const val GlareLuma = 194

// under this share of bright pixels the text reads fine, from the top one on it's fully glaring
private const val GlareFrom = 0.35f
private const val GlareTo = 0.75f

private val Start = Color(0xFF2B2A2D)

// the four colours the flagship's backdrop drifts between (the cover's bottom edge left and right, a deep one and an accent), plus how glaring the picture is under the text
@Stable
class AmbientColors internal constructor(private val scope: CoroutineScope) {
    // what everything draws with, handed out at most 30 times a second while it fades, so a fade never redraws the player every frame
    private var shownTones by mutableStateOf(List(ToneCount) { if (it == ToneGlare) Color.Black else Start })
    private var target: List<Color>? = null
    private var picture: List<Color>? = null

    // a video or animated cover is feeding the colours right now, not the still picture
    var isLive by mutableStateOf(false)
        private set
    private var fade: Job? = null

    fun tone(index: Int): Color = shownTones[index]

    // 0 on dark and mid covers, up to 1 when the picture under the title is mostly white; read it while drawing
    fun glare(): Float = shownTones[ToneGlare].red

    // where the cover meets what's under it, both halves mixed, for the colour fade at the bottom
    fun seam(): Color {
        val a = shownTones[ToneSeamLeft]
        val b = shownTones[ToneSeamRight]
        return Color((a.red + b.red) / 2f, (a.green + b.green) / 2f, (a.blue + b.blue) / 2f)
    }

    // the still picture's colours; they show unless a video or an animated cover is feeding its own
    fun picture(colors: List<Color>) {
        picture = colors
        if (!isLive) ease(colors)
    }

    // a frame of the playing video or animated cover
    fun live(colors: List<Color>) {
        isLive = true
        ease(colors)
    }

    // a new song: whatever was feeding live colours belonged to the last one
    fun forgetLive() {
        isLive = false
    }

    // the video or animated cover stopped, so the picture's colours come back
    fun endLive() {
        if (!isLive) return
        isLive = false
        picture?.let(::ease)
    }

    private fun ease(next: List<Color>) {
        if (next.size < ToneCount) return
        val last = target
        if (last != null && last.indices.all { distance(last[it], next[it]) < MinChange }) return
        target = next
        val from = shownTones
        fade?.cancel()
        fade = scope.launch {
            val start = withFrameMillis { it }
            var handedOut = start
            while (true) {
                val now = withFrameMillis { it }
                val t = ((now - start).toFloat() / FadeMs).coerceIn(0f, 1f)
                if (t >= 1f) break
                if (now - handedOut >= PublishMs) {
                    handedOut = now
                    val k = FastOutSlowInEasing.transform(t)
                    shownTones = List(ToneCount) { lerp(from[it], next[it], k) }
                }
            }
            shownTones = next
        }
    }

    private fun distance(a: Color, b: Color): Float =
        maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
}

@Composable
fun rememberAmbientColors(): AmbientColors {
    val scope = rememberCoroutineScope()
    return remember(scope) { AmbientColors(scope) }
}

// feeds [colors] the picture's colours whenever the song's picture changes, read off the main thread
@Composable
fun FollowPicture(colors: AmbientColors, url: String?) {
    val context = LocalContext.current
    LaunchedEffect(colors, url) {
        colors.forgetLive()
        val tones = pictureTones(context, url ?: return@LaunchedEffect) ?: return@LaunchedEffect
        colors.picture(tones)
    }
}

private val pictureCache = LruCache<String, List<Color>>(48)

// the four tones of a cover picture, worked out once per picture
suspend fun pictureTones(context: Context, url: String): List<Color>? {
    pictureCache.get(url)?.let { return it }
    val bitmap = withContext(Dispatchers.IO) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url.resize(PicturePx * 2, PicturePx * 2))
                .size(PicturePx, PicturePx)
                .allowHardware(false)
                .build()
            context.imageLoader.execute(request).image?.toBitmap()
        }.getOrNull()
    } ?: return null
    return withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return@withContext null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        ambientTones(pixels, w, h)
    }?.also { pictureCache.put(url, it) }
}

// four tones and the glare from a frame's pixels, inside any black bars ([insetX] and [insetY] are the share cut off each side)
internal fun ambientTones(pixels: IntArray, width: Int, height: Int, insetX: Float = 0f, insetY: Float = 0f): List<Color> {
    val left = (insetX * width).toInt().coerceIn(0, width / 2 - 1)
    val right = width - left
    val top = (insetY * height).toInt().coerceIn(0, height / 2 - 1)
    val bottom = height - top
    val span = bottom - top
    val midX = (left + right) / 2
    val seamTop = top + (span * 0.6f).toInt()
    val accentBottom = top + (span * 0.4f).toInt()
    // sparse on big frames, every pixel on small ones
    val step = if (width * height > 4_000) 3 else 1

    fun average(x0: Int, x1: Int, y0: Int, y1: Int): Color {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        var y = y0
        while (y < y1) {
            val row = y * width
            var x = x0
            while (x < x1) {
                val c = pixels[row + x]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
                x += step
            }
            y += step
        }
        if (n == 0) return Color.Black
        return Color(red = (r / n).toInt(), green = (g / n).toInt(), blue = (b / n).toInt())
    }

    // the share of near-white pixels, not the average, so white snow beside a black coat still counts as glaring
    fun glare(x0: Int, x1: Int, y0: Int, y1: Int): Color {
        var bright = 0
        var n = 0
        var y = y0
        while (y < y1) {
            val row = y * width
            var x = x0
            while (x < x1) {
                val c = pixels[row + x]
                val luma = (54 * ((c shr 16) and 0xFF) + 183 * ((c shr 8) and 0xFF) + 19 * (c and 0xFF)) shr 8
                if (luma >= GlareLuma) bright++
                n++
                x += step
            }
            y += step
        }
        if (n == 0) return Color.Black
        val level = ((bright.toFloat() / n - GlareFrom) / (GlareTo - GlareFrom)).coerceIn(0f, 1f)
        return Color(level, level, level)
    }

    return listOf(
        tone(average(left, midX, seamTop, bottom), minLight = 0.14f, maxLight = 0.44f, saturation = 1.05f),
        tone(average(midX, right, seamTop, bottom), minLight = 0.14f, maxLight = 0.44f, saturation = 1.05f),
        tone(average(left, right, top, bottom), minLight = 0.08f, maxLight = 0.26f, saturation = 0.9f),
        tone(average(left, right, top, accentBottom), minLight = 0.16f, maxLight = 0.42f, saturation = 1.25f),
        glare(left, right, seamTop, bottom),
    )
}

// calmer and darker than the picture, so it stays rich without ever fighting the white text over it
private fun tone(color: Color, minLight: Float, maxLight: Float, saturation: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturation).coerceAtMost(0.62f)
    hsl[2] = hsl[2].coerceIn(minLight, maxLight)
    return Color(ColorUtils.HSLToColor(hsl))
}

