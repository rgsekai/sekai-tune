package moe.rgsekai.sekaitune.shared.data.sources

/**
 * The quality labels a source stamps on its search rows ([moe.rgsekai.sekaitune.shared.data.model.Song.sourceQuality])
 * and the small checks on a stream URL / format that every source applies. Shared by the addon and
 * module sources; `ModuleSource` re-exports the constants under its old names.
 */
object SourceTiers {
    const val LOSSLESS = "LOSSLESS"
    const val HIGH = "HIGH"
    const val LOW = "LOW"

    /** Dolby Atmos rows get their own label: Tidal files them under its lowest quality, which reads as the worst. */
    const val DOLBY = "DOLBY"

    /** Lowest first, the order the quality badge ranks them. */
    val TIERS = listOf(LOW, HIGH, LOSSLESS)

    /** A capped request at or below this asks an addon for its LOW tier. */
    const val LOW_CEILING_KBPS = 128

    /** The tier a free-text quality label (`HI_RES_LOSSLESS`, `320kbps`, `flac`) names, or null. */
    fun qualityTier(label: String): String? {
        val text = label.uppercase()
        return when {
            text.isBlank() -> null
            LOSSLESS_HINTS.any { it in text } -> LOSSLESS
            LOW_HINTS.any { it in text } -> LOW
            HIGH_HINTS.any { it in text } -> HIGH
            else -> null
        }
    }

    private val LOSSLESS_HINTS =
        listOf("LOSSLESS", "FLAC", "ALAC", "HI-RES", "HI_RES", "HIRES", "24-BIT", "16-BIT", "WAV")
    private val LOW_HINTS = listOf("LOW", "128", "96KBPS", "64")
    private val HIGH_HINTS = listOf("HIGH", "320", "MP3", "AAC", "M4A", "OPUS", "OGG")

    /**
     * Whether [url] can't be fetched: not a URL at all, or one with its own origin pasted in again
     * after the path (`https://a/https://a/x`), which some catalogues emit.
     */
    fun malformed(url: String): Boolean {
        if (parseHttpUrl(url) == null) return true
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return false
        val originEnd = url.indexOf('/', schemeEnd + 3)
        if (originEnd < 0) return false
        return url.indexOf(url.substring(0, originEnd), originEnd) >= 0
    }

    /** Whether [format] is Dolby Atmos that this run must not play (switched off, or no decoder). */
    fun unplayable(format: StreamFormat, atmosAllowed: Boolean): Boolean =
        format.isDolbyAtmos && !atmosAllowed
}
