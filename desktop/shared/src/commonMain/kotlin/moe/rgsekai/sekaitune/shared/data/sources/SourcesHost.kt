package moe.rgsekai.sekaitune.shared.data.sources

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * What the shared source machinery needs from the platform: where the source list is kept, which
 * kinds it can build, and what the device can decode. Android and iOS each install one through
 * [SourceRegistry.init]; the defaults are the conservative answers.
 */
interface SourcesHost {
    /** Sent as `SekaiTune/v<this>` in the User-Agent of every addon call that doesn't set its own. */
    val appVersion: String get() = ""

    /** Whether this device can decode E-AC-3 JOC (Dolby Atmos). */
    val playsDolbyAtmos: Boolean get() = false

    /** Whether this platform can play a source of [kind] at all. Addons and YouTube always can. */
    fun supports(kind: SourceKind): Boolean = kind == SourceKind.ADDON || kind == SourceKind.YOUTUBE

    /** Builds the kinds the shared code can't (modules, custom source); null when [supports] says no. */
    fun build(config: SourceConfig): MusicSource? = null

    /**
     * Tells the player an addon stream is a manifest ([transport] `hls` or `dash`) before it opens
     * [url]; false when this platform can't play that, and the stream is passed over like any other
     * unplayable one. Android's ExoPlayer is told which and plays both.
     */
    fun acceptTransport(url: String, transport: String): Boolean = true

    /**
     * Whether the player can decode a stream of [codec] (lowercase, as [StreamFormat.codec]; null
     * when the source didn't say). False sends the resolver on to the next source.
     */
    fun canDecode(codec: String?): Boolean = true

    /** The persisted source list (JSON) and the one-time custom source opt-in flag. */
    var storedSources: String?
    
}

/** [raw] as an http(s) URL with a host, or null — the check OkHttp's `toHttpUrlOrNull` made. */
internal fun parseHttpUrl(raw: String): Url? {
    if ("://" !in raw) return null
    val url = runCatching { Url(raw) }.getOrNull() ?: return null
    if (url.protocol != URLProtocol.HTTP && url.protocol != URLProtocol.HTTPS) return null
    return url.takeIf { it.host.isNotBlank() }
}
