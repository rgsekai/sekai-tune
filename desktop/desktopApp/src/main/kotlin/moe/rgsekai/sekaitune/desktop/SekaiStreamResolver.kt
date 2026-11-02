package moe.rgsekai.sekaitune.desktop

import moe.rgsekai.sekaitune.shared.data.NerdStats
import moe.rgsekai.sekaitune.shared.data.innertube.StreamResolver
import moe.rgsekai.sekaitune.shared.data.model.Song
import moe.rgsekai.sekaitune.desktop.DesktopStream
import moe.rgsekai.sekaitune.desktop.DesktopStreamFormat
import moe.rgsekai.sekaitune.desktop.DesktopTrackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse
import moe.rgsekai.sekaitune.simpstream.SimpMusicPlayer

internal object SekaiStreamResolver {

    private fun log(message: String) {
        DesktopTrackLog.log("[engine] $message")
        println("[engine] $message")
    }

    /**
     * Resolves a streamable URL using Sekai Tune :core + :morideobfuscator.
     * Selects best audio format (Opus/AAC, highest bitrate <= maxKbps).
     */
    suspend fun resolve(song: Song, maxKbps: Int = Int.MAX_VALUE): Result<DesktopStream> = withContext(Dispatchers.IO) {
        runCatching {
            val (cpn, playerResponse) = SimpMusicPlayer.player(song.videoId).getOrThrow()
            val formats = playerResponse.streamingData?.adaptiveFormats.orEmpty()
                .filter { it.mimeType.startsWith("audio/") && !it.url.isNullOrBlank() }

            if (formats.isEmpty()) {
                throw IllegalStateException("No usable audio formats returned by SimpMusicPlayer for ${song.videoId}")
            }

            // Pick highest bitrate <= maxKbps, preferring opus (itag 251) over aac (itag 140) if similar
            val maxBitrateBps = if (maxKbps == Int.MAX_VALUE) Int.MAX_VALUE else maxKbps * 1000
            val eligible = formats.filter { it.bitrate <= maxBitrateBps }.ifEmpty { formats }

            val chosen = eligible.maxWithOrNull(
                compareBy<PlayerResponse.StreamingData.Format> { it.bitrate }
                    .thenBy { if (it.mimeType.contains("opus")) 1 else 0 }
            ) ?: formats.first()

            val chosenUrl = chosen.url!!
            val chosenKbps = chosen.bitrate / 1000
            val codec = when {
                chosen.mimeType.contains("opus") -> "opus"
                chosen.mimeType.contains("mp4a") || chosen.mimeType.contains("aac") -> "aac"
                else -> "webm"
            }

            log("op=stream used=sekai song='${song.title}' format=$codec @ ${chosenKbps}kbps (itag=${chosen.itag})")
            NerdStats.onStreamPicked(song.videoId, chosenKbps)

            DesktopStream(
                url = chosenUrl,
                format = DesktopStreamFormat(
                    codec = codec,
                    kbps = chosenKbps,
                ),
                headers = StreamResolver.mediaHeadersFor(chosenUrl),
                sourceId = "youtube",
                windowedReads = true,
            )
        }.onFailure { err ->
            val reason = "${err::class.java.simpleName}: ${err.message}".take(200)
            log("op=stream used=fallback reason=$reason")
            err.printStackTrace()
        }
    }
}
