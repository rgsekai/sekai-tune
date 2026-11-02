package moe.rgsekai.sekaitune.shared.data.canvas

import moe.rgsekai.sekaitune.shared.data.DebugLog as Log
import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.NetHttp
import moe.rgsekai.sekaitune.shared.platform.ioDispatcher
import moe.rgsekai.sekaitune.shared.platform.normalizeNfd
import io.ktor.http.encodeURLParameter
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Finds [AppleArtistArt] from nothing but an artist's name.
 *
 * Two public, unauthenticated requests: the iTunes search API turns the name
 * into an Apple Music artist id, and the artist's own music.apple.com page
 * carries its header art in the JSON it embeds for hydration. That JSON is not
 * an API Apple publishes, so every step here treats a surprise as "no art" —
 * the YouTube header is always underneath.
 *
 * A search will happily return somebody else, so an answer is only taken when
 * the name Apple gives back is the name that was asked for. Most artists have
 * no logo; those, and every miss, are cached so a revisit costs nothing.
 */
object AppleArtistArtRepository {

    private const val TAG = "AppleArtistArt"
    private const val CACHE_SIZE = 64
    private const val HERO_WIDTH = 1200
    private const val LOGO_WIDTH = 1000
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private class Entry(val art: AppleArtistArt?)

    // Insertion-ordered; reads re-insert so it stays least-recently-used.
    private val cache = LinkedHashMap<String, Entry>()
    private val guard = SynchronizedObject()

    private fun lookupCached(key: String): Entry? =
        synchronized(guard) { cache.remove(key)?.also { cache[key] = it } }

    private val _updates = MutableStateFlow(0)

    /**
     * Ticks whenever a lookup settles. The page that asked for the art is not the
     * only thing coloured by it — the bars above and below the page are drawn
     * from outside it — so anything that reads [cached] re-reads on this.
     */
    val updates: StateFlow<Int> = _updates

    /**
     * What is already known for [artistName], without a request — null for a
     * name never looked up *and* for one known to have nothing, which is the
     * same thing to a caller that is only deciding what to draw first.
     */
    fun cached(artistName: String): AppleArtistArt? = lookupCached(artistName.normalized())?.art

    /** The header art for [artistName], or null when Apple has none. Never throws. */
    suspend fun artFor(artistName: String): AppleArtistArt? {
        val key = artistName.normalized()
        if (key.isEmpty()) return null
        lookupCached(key)?.let { return it.art }

        // Null here is a network hiccup, not an answer — only a settled result
        // (including a genuine "no logo") is worth remembering.
        val entry = withContext(ioDispatcher) {
            runCatching { lookup(artistName, key) }
                .onFailure { Log.d(TAG, "lookup failed for $artistName: $it") }
                .getOrNull()
        } ?: return null
        synchronized(guard) {
            cache[key] = entry
            if (cache.size > CACHE_SIZE) cache.remove(cache.keys.first())
        }
        _updates.value++
        return entry.art
    }

    private fun lookup(artistName: String, key: String): Entry? {
        val search = get(
            "https://itunes.apple.com/search?term=${artistName.encodeURLParameter()}" +
                "&entity=musicArtist&limit=5",
        ) ?: return null
        val results = (Json.parseToJsonElement(search) as? JsonObject)?.get("results") as? JsonArray
        var page: String? = null
        for (r in results.orEmpty().filterIsInstance<JsonObject>()) {
            if (r.string("artistName").normalized() == key) {
                // The link carries the artist's slug; the bare id answers with
                // a redirect to it, one more round trip for the same page.
                page = r.string("artistLinkUrl").substringBefore('?').takeIf { it.isNotBlank() }
                    ?: (r["artistId"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
                        ?.let { "https://music.apple.com/us/artist/$it" }
                break
            }
        }
        if (page == null) return Entry(null)

        val html = get(page) ?: return null
        return Entry(parse(html))
    }

    private fun parse(html: String): AppleArtistArt? {
        val marker = html.indexOf("id=\"serialized-server-data\"")
        if (marker < 0) return null
        val start = html.indexOf('>', marker) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        val header = findHeader(Json.parseToJsonElement(html.substring(start, end))) ?: return null

        // Absent for most artists: the key is there and null.
        val logo = header.obj("artistLogo")?.obj("dictionary")
        val hero = (header.obj("artwork") ?: header.obj("colorBackdropArtwork"))
            ?.obj("dictionary") ?: return null

        val logoW = logo?.int("width") ?: 0
        val logoH = logo?.int("height") ?: 0
        val heroW = hero.int("width")?.takeIf { it > 0 } ?: return null
        val heroH = hero.int("height")?.takeIf { it > 0 } ?: return null

        val heroUrl = hero.string("url").fill(
            HERO_WIDTH, (HERO_WIDTH.toLong() * heroH / heroW).toInt(), "jpg",
        ) ?: return null
        val logoUrl = if (logo != null && logoW > 0 && logoH > 0) {
            logo.string("url").fill(LOGO_WIDTH, (LOGO_WIDTH.toLong() * logoH / logoW).toInt(), "png")
        } else {
            null
        }
        val clip = header.obj("videoArtwork")?.obj("dictionary")?.obj("motionArtistSquare1x1")
        val video = clip?.string("video")?.takeIf { it.startsWith("http") }
        // The clip is a different picture from the still, with its own colours
        // — the page has to match whichever one is on screen.
        val colours = clip?.obj("previewFrame")?.takeIf { video != null } ?: hero
        val background = colours.string("bgColor").toArgb()
            ?: hero.string("bgColor").toArgb() ?: return null
        val accent = colours.string("textColor1").toArgb() ?: background
        return AppleArtistArt(
            heroUrl = heroUrl,
            logoUrl = logoUrl,
            logoAspect = if (logoUrl != null) logoW.toFloat() / logoH else 1f,
            videoUrl = video,
            background = background,
            accent = accent,
            keyColor = header.string("keyColor").toArgb(),
        )
    }

    /** The first object anywhere in [node] that has an `artistLogo` key. */
    private fun findHeader(node: JsonElement?): JsonObject? {
        when (node) {
            is JsonObject -> {
                if ("artistLogo" in node) return node
                node.values.forEach { child -> findHeader(child)?.let { return it } }
            }
            is JsonArray -> node.forEach { child -> findHeader(child)?.let { return it } }
            else -> {}
        }
        return null
    }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** Apple's colours are six hex digits with no prefix. */
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
        val response = NetHttp.execute(HttpRequest(url, headers = mapOf("User-Agent" to USER_AGENT)))
        return if (response.isSuccessful) response.text() else null
    }

    /** Case, accents and punctuation are not what makes two names different. */
    private fun String.normalized(): String =
        normalizeNfd(lowercase()).filter { it.isLetterOrDigit() }
}
