package moe.rgsekai.sekaitune.shared.data.lyrics

import moe.rgsekai.sekaitune.shared.platform.ioDispatcher
import kotlinx.coroutines.withContext
import io.ktor.http.URLBuilder

/**
 * Word-timed lyrics from BetterLyrics — the backend behind the YouTube Music
 * browser extension of the same name.
 *
 * One key-less call keyed on title, artist and duration, answering with Apple
 * Music's own TTML. That combination is why it leads the chain: no track-id
 * lookup, no token to scrape, no login, and the timing is per-syllable.
 *
 * Note this is the extension's original host. The project's newer Cloudflare
 * API puts the same endpoint behind a Turnstile challenge, which a native
 * client has no way to answer.
 */
object BetterLyrics {

    private const val BASE = "https://lyrics-api.boidu.dev/getLyrics"
    private const val PORTATO = "https://lyrics-api.boidu.dev/qq/getLyrics"

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = fetch(BASE, title, artist, durationMs, album)

    /** QQ Music's karaoke timings through BetterLyrics' Portato endpoint. */
    suspend fun portato(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = fetch(PORTATO, title, artist, durationMs, album)

    private suspend fun fetch(
        endpoint: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<LyricLine>? = withContext(ioDispatcher) {
        val url = URLBuilder(endpoint)
            .apply { param("s", title) }
            .apply { param("a", artist) }
            .apply {
                val seconds = durationMs / 1000
                if (seconds > 0) param("d", seconds.toString())
                if (!album.isNullOrBlank()) param("al", album)
            }
            .build()

        val body = lyricsGet(url.toString()) ?: return@withContext null
        ProviderLyrics.parse(body)
    }
}
