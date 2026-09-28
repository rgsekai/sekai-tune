package moe.rgsekai.sekaitune.simpstream.extractor

import dev.maxrave.pipepipe.extractor.NewPipe as PipePipeNewPipe
import dev.maxrave.pipepipe.extractor.ServiceList as PipePipeServiceList
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeApiDecoder
import dev.maxrave.pipepipe.extractor.stream.StreamInfo as PipePipeStreamInfo
import moe.rgsekai.sekaitune.simpstream.SimpStreamLog
import org.schabi.newpipe.extractor.NewPipe as BraveNewPipe
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo as BraveStreamInfo

private const val TAG = "SimpStreamExtractor"
private const val LOCAL_TIER = "local"
private const val REMOTE_TIER = "pipepipe.dev"

class SimpStreamExtractor {
    private val newPipeDownloader = NewPipeDownloaderImpl(proxy = null)
    private val braveNewPipeDownloader = BraveNewPipeDownloaderImpl(proxy = null)
    private val faradayDecoder = FaradayJsDecoder()

    init {
        PipePipeNewPipe.init(newPipeDownloader)
        BraveNewPipe.init(braveNewPipeDownloader)
        YoutubeApiDecoder.setLocalDecoder(faradayDecoder)
    }

    fun extractStreams(videoId: String): List<Pair<Int, String>> {
        // Tier 1 - ciphers solved locally with QuickJS
        YoutubeApiDecoder.setLocalDecoder(faradayDecoder)
        pipePipeStreams(videoId, LOCAL_TIER)?.let { return it }

        // Tier 2 - same extractor with no local decoder at all (routes to api.pipepipe.dev)
        YoutubeApiDecoder.setLocalDecoder(null)
        pipePipeStreams(videoId, REMOTE_TIER)?.let { return it }

        // Tier 3 - BravePipe extractor
        return braveStreams(videoId)
    }

    private fun pipePipeStreams(
        videoId: String,
        tier: String,
    ): List<Pair<Int, String>>? {
        try {
            val streamInfo =
                PipePipeStreamInfo.getInfo(PipePipeServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            val temp =
                streamsList
                    .mapNotNull {
                        (it.itagItem?.id ?: return@mapNotNull null) to it.content
                    }.toMutableList()
            val manifest = streamInfo.dashMpdUrl.takeIf { !it.isNullOrEmpty() } ?: streamInfo.hlsUrl
            if (!manifest.isNullOrEmpty()) temp.add(96 to manifest)
            val pipeResult = temp.toList()
            if (!pipeResult.hasRequiredItags()) {
                SimpStreamLog.d(TAG, "PipePipe[$tier] missing required itags for $videoId (got=${pipeResult.map { it.first }})")
                return null
            }
            val label = if (tier == LOCAL_TIER) faradayDecoder.lastOutcomeLabel else REMOTE_TIER
            ExtractSource.record(videoId, "PipePipe ~ $label")
            SimpStreamLog.d(TAG, "extract source=PipePipe[$tier] itags=${pipeResult.map { it.first }} for $videoId")
            return pipeResult
        } catch (e: Throwable) {
            SimpStreamLog.w(TAG, "PipePipe[$tier] extractor failed for $videoId: ${e.message}", e)
            return null
        }
    }

    private fun braveStreams(videoId: String): List<Pair<Int, String>> =
        runCatching {
            val streamInfo =
                BraveStreamInfo.getInfo(BraveServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            val temp =
                streamsList
                    .mapNotNull {
                        (it.itagItem?.id ?: return@mapNotNull null) to it.content
                    }.toMutableList()
            val manifest = streamInfo.dashMpdUrl.takeIf { !it.isNullOrEmpty() } ?: streamInfo.hlsUrl
            if (!manifest.isNullOrEmpty()) temp.add(96 to manifest)
            ExtractSource.record(videoId, "BravePipe")
            SimpStreamLog.d(TAG, "extract source=BravePipe itags=${temp.map { it.first }} for $videoId")
            temp.toList()
        }.onFailure {
            SimpStreamLog.w(TAG, "BravePipe extractor failed for $videoId: ${it.message}", it)
        }.getOrElse { emptyList() }
}
