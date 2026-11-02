package moe.rgsekai.sekaitune.shared.data.innertube

/** One NewPipe audio stream, reduced to what StreamResolver chooses between. */
internal class NewPipeAudio(val isM4a: Boolean, val isWebmOpus: Boolean)

/** What only the JVM has: NewPipe (the failsafe extractor) and the streaming probe over its OkHttp client. */
internal expect object StreamPlatform {
    fun init()
    val newPipeAvailable: Boolean

    /** Whether the player can only decode AAC-in-MP4 (AVFoundation has no WebM/Opus). */
    val playbackRequiresM4a: Boolean
    fun isLinkageError(e: Throwable): Boolean
    suspend fun signatureTimestamp(videoId: String): Int?

    /** NewPipe's verdict-about-the-content exceptions as a message, or null. */
    fun permanentReason(e: Throwable): String?
    suspend fun extractStream(
        videoId: String,
        select: (List<Pair<Int, NewPipeAudio>>) -> NewPipeAudio?,
    ): StreamResolver.Stream

    /**
     * Sends a GET with [headers] (a Range among them) and hands the status, Content-Type and a
     * "did [readBytes] of body arrive" check to [onReply] while the response is open.
     */
    fun <T> probeFetch(url: String, headers: Map<String, String>, readBytes: Long, onReply: (Int, String?, () -> Boolean) -> T): T
}
