/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.screens.artist

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.time.Duration
import java.util.Locale

/**
 * What Apple Music's artist page shows at the top: a hero photograph, usually
 * the artist's title logo drawn over it as a separate image, and for some
 * artists a looping video that stands in for the photograph.
 *
 * [logoUrl] is a transparent PNG, null for the many artists without one;
 * [logoAspect] is its width over its height, so the caller can size it without
 * waiting for the image to decode. [videoUrl] is an HLS playlist / mp4.
 *
 * [background] and [accent] are Apple's own colours for the photograph (as
 * ARGB), so the page can be tinted without decoding the image to find them.
 */
data class AppleArtistArt(
    val heroUrl: String,
    val logoUrl: String?,
    val logoAspect: Float,
    val videoUrl: String?,
    val background: Int,
    val accent: Int,
    /** The colour Apple fills the artist's Play button with; absent for some artists. */
    val keyColor: Int?,
) {
    val backgroundColor: Color get() = Color(background)
    val accentColor: Color get() = Color(accent)
    val playColor: Color? get() = keyColor?.let { Color(it) }

    fun washColor(dark: Boolean = true): Color = backgroundColor.toArtistWash(dark)
    fun elevatedColor(dark: Boolean = true): Color = backgroundColor.toArtistElevated(dark)
}

private const val CHROMATIC_SATURATION_THRESHOLD = 0.12f

/**
 * Keeps neutral artwork neutral instead of inventing a hue for it (which would
 * manufacture red/brown once lightness is lowered).
 */
internal fun adaptedArtworkSaturation(source: Float, minimum: Float, maximum: Float): Float {
    val saturation = source.coerceIn(0f, 1f)
    return if (saturation < CHROMATIC_SATURATION_THRESHOLD) {
        saturation
    } else {
        saturation.coerceIn(minimum, maximum)
    }
}

/**
 * Converts RGB components (0..1) to HSL components:
 * outHsl[0] in 0..360 (hue)
 * outHsl[1] in 0..1 (saturation)
 * outHsl[2] in 0..1 (lightness)
 */
internal fun rgbToHsl(r: Float, g: Float, b: Float, outHsl: FloatArray) {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f

    val saturation = if (delta == 0f) {
        0f
    } else {
        val denom = 1f - kotlin.math.abs(2f * lightness - 1f)
        if (denom == 0f) 0f else delta / denom
    }

    val hue = when {
        delta == 0f -> 0f
        max == r -> (((g - b) / delta) % 6f) * 60f
        max == g -> (((b - r) / delta) + 2f) * 60f
        else -> (((r - g) / delta) + 4f) * 60f
    }.let { if (it < 0f) it + 360f else it }

    outHsl[0] = hue
    outHsl[1] = saturation.coerceIn(0f, 1f)
    outHsl[2] = lightness.coerceIn(0f, 1f)
}

/**
 * Converts HSL components to Compose Color.
 */
internal fun hslToColor(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1f): Color {
    val h = (hue % 360f + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)

    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f

    val (r1, g1, b1) = when ((h / 60f).toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = alpha,
    )
}

internal fun Color.withHsl(
    saturation: (Float) -> Float = { it },
    lightness: (Float) -> Float = { it },
): Color {
    val hsl = FloatArray(3)
    rgbToHsl(red, green, blue, hsl)
    val newSat = saturation(hsl[1]).coerceIn(0f, 1f)
    val newLight = lightness(hsl[2]).coerceIn(0f, 1f)
    return hslToColor(hsl[0], newSat, newLight, alpha)
}

/**
 * Tone-maps a raw artwork/header background color into a cohesive wash that
 * guarantees WCAG text contrast while preserving the artist's unique hue/tint.
 *
 * In dark mode, lightness is clamped to 0.14f..0.24f (preventing the pure white
 * / washed-out background seen when artists like One Direction have light photos),
 * so white text is always sharp and clear.
 *
 * In light mode, lightness is clamped to 0.78f..0.90f for dark text.
 */
fun Color.toArtistWash(dark: Boolean = true): Color = if (dark) {
    withHsl(
        saturation = { adaptedArtworkSaturation(it, minimum = 0.18f, maximum = 0.58f) },
        lightness = { it.coerceIn(0.14f, 0.24f) },
    )
} else {
    withHsl(
        saturation = { adaptedArtworkSaturation(it, minimum = 0.12f, maximum = 0.46f) },
        lightness = { it.coerceIn(0.78f, 0.90f) },
    )
}

fun Color.toArtistElevated(dark: Boolean = true): Color = if (dark) {
    withHsl(
        saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
        lightness = { 0.22f },
    )
} else {
    withHsl(
        saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
        lightness = { 0.83f },
    )
}

/**
 * Finds [AppleArtistArt] from an artist's name using public unauthenticated Apple endpoints.
 *
 * Two public, unauthenticated requests: the iTunes search API turns the name
 * into an Apple Music artist id, and the artist's own music.apple.com page
 * carries its header art in the JSON it embeds for hydration.
 */
object AppleArtistArtRepository {

    private const val TAG = "AppleArtistArt"
    private const val CACHE_SIZE = 64
    private const val HERO_WIDTH = 1200
    private const val LOGO_WIDTH = 1000
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    internal class Entry(val art: AppleArtistArt?)

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > CACHE_SIZE
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(10))
            .build()
    }

    private val _updates = MutableStateFlow(0)
    val updates: StateFlow<Int> = _updates

    fun cached(artistName: String): AppleArtistArt? {
        val key = artistName.normalized()
        return synchronized(cache) { cache[key] }?.art
    }

    suspend fun artFor(artistName: String): AppleArtistArt? {
        val key = artistName.normalized()
        if (key.isEmpty()) return null
        synchronized(cache) { cache[key] }?.let { return it.art }

        val entry = withContext(Dispatchers.IO) {
            runCatching { lookup(artistName, key) }
                .onFailure { Timber.tag(TAG).d(it, "lookup failed for %s", artistName) }
                .getOrNull()
        } ?: return null
        synchronized(cache) { cache[key] = entry }
        _updates.value++
        return entry.art
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
        _updates.value++
    }

    internal fun lookup(artistName: String, key: String = artistName.normalized()): Entry? {
        val search = get(
            "https://itunes.apple.com/search?term=${URLEncoder.encode(artistName, "UTF-8")}" +
                "&entity=musicArtist&limit=5",
        ) ?: return null
        val results = JSONObject(search).optJSONArray("results") ?: JSONArray()
        var page: String? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optString("artistName").normalized() == key) {
                page = r.optString("artistLinkUrl").substringBefore('?').takeIf { it.isNotBlank() }
                    ?: r.optLong("artistId").takeIf { it > 0 }?.let { "https://music.apple.com/us/artist/$it" }
                break
            }
        }
        if (page == null) return Entry(null)

        val html = get(page) ?: return null
        return Entry(parse(html))
    }

    internal fun parse(html: String): AppleArtistArt? {
        val marker = html.indexOf("id=\"serialized-server-data\"")
        if (marker < 0) return null
        val start = html.indexOf('>', marker) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        val header = findHeader(JSONObject(html.substring(start, end).let { "{\"d\":$it}" }).get("d"))
            ?: return null

        val logo = header.optJSONObject("artistLogo")?.optJSONObject("dictionary")
        val hero = (header.optJSONObject("artwork") ?: header.optJSONObject("colorBackdropArtwork"))
            ?.optJSONObject("dictionary") ?: return null

        val logoW = logo?.optInt("width") ?: 0
        val logoH = logo?.optInt("height") ?: 0
        val heroW = hero.optInt("width").takeIf { it > 0 } ?: return null
        val heroH = hero.optInt("height").takeIf { it > 0 } ?: return null

        val heroUrl = hero.optString("url").fill(
            HERO_WIDTH, (HERO_WIDTH.toLong() * heroH / heroW).toInt(), "jpg",
        ) ?: return null
        val logoUrl = if (logo != null && logoW > 0 && logoH > 0) {
            logo.optString("url").fill(LOGO_WIDTH, (LOGO_WIDTH.toLong() * logoH / logoW).toInt(), "png")
        } else {
            null
        }
        val clip = header.optJSONObject("videoArtwork")?.optJSONObject("dictionary")
            ?.optJSONObject("motionArtistSquare1x1")
        val video = clip?.optString("video")?.takeIf { it.startsWith("http") }
        val colours = clip?.optJSONObject("previewFrame")?.takeIf { video != null } ?: hero
        val background = colours.optString("bgColor").toArgb()
            ?: hero.optString("bgColor").toArgb() ?: return null
        val accent = colours.optString("textColor1").toArgb() ?: background
        return AppleArtistArt(
            heroUrl = heroUrl,
            logoUrl = logoUrl,
            logoAspect = if (logoUrl != null) logoW.toFloat() / logoH else 1f,
            videoUrl = video,
            background = background,
            accent = accent,
            keyColor = header.optString("keyColor").toArgb(),
        )
    }

    private fun findHeader(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.has("artistLogo") || (node.has("artwork") && node.optJSONObject("artwork") != null)) return node
                val keys = node.keys()
                while (keys.hasNext()) findHeader(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findHeader(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun String.toArgb(): Int? =
        takeIf { it.length == 6 }?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }

    private fun String.fill(w: Int, h: Int, format: String): String? {
        if (isBlank() || !contains("{w}")) return null
        return replace("{w}", w.toString())
            .replace("{h}", h.toString())
            .replace("{c}", "bb")
            .replace("{f}", format)
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun String.normalized(): String =
        java.text.Normalizer.normalize(lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }
}
