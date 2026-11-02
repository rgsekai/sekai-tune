package moe.rgsekai.sekaitune.shared.data.canvas

import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.NetHttp
import moe.rgsekai.sekaitune.shared.platform.systemLanguageTag
import io.ktor.http.encodeURLParameter

/**
 * A plain GET returning the body, or null for anything that isn't a 2xx or
 * that throws. Canvas is decoration: no provider failure is allowed to reach
 * the caller, and none of these hosts are ours to depend on.
 *
 * Goes through [NetHttp], which on Android is the app's shared OkHttp client,
 * so these lookups reuse its connection pool.
 */
internal fun canvasGet(url: String, headers: Map<String, String> = emptyMap()): String? =
    canvasGetWithStatus(url, headers).second

/**
 * Same as [canvasGet], but keeps the status code even on failure — for the
 * few callers where "it wasn't a 2xx" needs to say *which* code, rather than
 * collapsing every kind of failure into the same null.
 */
internal fun canvasGetWithStatus(url: String, headers: Map<String, String> = emptyMap()): Pair<Int, String?> =
    runCatching {
        NetHttp.execute(HttpRequest(url, headers = headers)).let { it.code to if (it.isSuccessful) it.text() else null }
    }.getOrDefault(-1 to null)

/** `base?k=v&...`, percent-encoded (`%20` for a space, as OkHttp did). */
internal fun canvasUrl(base: String, vararg params: Pair<String, String>): String =
    base + "?" + params.joinToString("&") { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" }

/** The device's two-letter region from its language tag ("en-US" -> "US"), or null. */
internal fun deviceCountry(): String? =
    systemLanguageTag().split('-', '_').drop(1).firstOrNull { it.length == 2 && it.all(Char::isLetter) }?.uppercase()

/** The browser UA these catalog endpoints expect; they 403 an unknown one. */
const val CANVAS_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/122.0.0.0 Safari/537.36"
