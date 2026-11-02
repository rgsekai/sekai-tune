package moe.rgsekai.sekaitune.shared.data.lyrics

import moe.rgsekai.sekaitune.shared.platform.HttpRequest
import moe.rgsekai.sekaitune.shared.platform.NetHttp
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json

/**
 * Shared plumbing for the lyric providers.
 *
 * They are all raced against each other by [LyricsRepository], so a provider
 * that hangs holds up the whole lookup. The 6s call timeout is deliberately
 * far shorter than the shared client's stream-oriented timeouts: a lyric that
 * arrives after the second chorus is of no use to anyone, and the fallbacks
 * behind it are the better answer.
 */
internal const val LYRICS_AGENT = "Sekai Tune (https://github.com/rgsekai/sekai-tune)"

val lyricsJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Adds a query parameter percent-encoded like OkHttp did (`%20` for a space; ktor's default is `+`). */
internal fun URLBuilder.param(name: String, value: String) =
    encodedParameters.append(name.encodeURLParameter(), value.encodeURLParameter())

private fun fetch(url: String, headers: Map<String, String>, callMs: Long, connectMs: Long): String? {
    val response = NetHttp.execute(
        HttpRequest(url, headers = headers, callTimeoutMs = callMs, connectTimeoutMs = connectMs),
    )
    return if (response.isSuccessful) response.text() else null
}

/** Body of a successful GET, or null for any failure at all. */
internal fun lyricsGet(url: String): String? = runCatching {
    fetch(url, mapOf("User-Agent" to LYRICS_AGENT, "Accept" to "application/json"), 6_000, 3_000)
}.getOrNull()

// PaxSenix's authenticated routes often have to query an upstream catalogue
// before answering. Keep the fast deadline for the ordinary providers, but
// match PaxSenix's own client timeout here (15s/10s) so valid requests are not
// discarded while its backend is still resolving a track.
/** Body of an authenticated provider GET without service-specific browser headers. */
internal fun lyricsGetBearer(url: String, bearer: String): String? = runCatching {
    if (bearer.isBlank()) return null
    fetch(
        url,
        mapOf(
            "User-Agent" to LYRICS_AGENT,
            "Accept" to "application/json, text/plain, */*",
            "Authorization" to "Bearer $bearer",
        ),
        15_000, 10_000,
    )
}.getOrNull()

/**
 * [lyricsGet], with a bearer token and the headers Apple's own web player
 * sends alongside one — `amp-api.music.apple.com` answers a token with no
 * `Origin` at all the same way it answers a wrong one, with a 403.
 */
internal fun lyricsGetAuthorized(url: String, bearer: String): String? = runCatching {
    fetch(
        url,
        mapOf(
            "User-Agent" to LYRICS_AGENT,
            "Accept" to "application/json",
            "Authorization" to "Bearer $bearer",
            "Origin" to "https://music.apple.com",
            "Referer" to "https://music.apple.com/",
        ),
        6_000, 3_000,
    )
}.getOrNull()
