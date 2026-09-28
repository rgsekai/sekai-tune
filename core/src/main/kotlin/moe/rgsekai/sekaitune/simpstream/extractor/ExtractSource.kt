package moe.rgsekai.sekaitune.simpstream.extractor

/**
 * Tracks which extractor and decoder tier produced the stream URLs for a video.
 */
object ExtractSource {
    private const val MAX_ENTRIES = 32

    @Volatile
    private var sources: Map<String, String> = emptyMap()

    fun record(
        videoId: String,
        source: String,
    ) {
        if (videoId.isEmpty()) return
        val updated = LinkedHashMap<String, String>(sources)
        updated.remove(videoId)
        updated[videoId] = source
        while (updated.size > MAX_ENTRIES) {
            updated.remove(updated.keys.firstOrNull() ?: break)
        }
        sources = updated
    }

    fun of(videoId: String): String? = sources[videoId]
}
