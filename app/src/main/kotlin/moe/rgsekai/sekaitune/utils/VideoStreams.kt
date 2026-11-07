/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.utils

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse

/**
 * Resolved video streams containing adaptive picture tracks and network headers.
 */
data class VideoStreams(
    val videoId: String,
    val tracks: List<VideoTrack>,
    val headers: Map<String, String>,
    val expiresAtMs: Long,
    val durationMs: Long,
    val sharpTracks: List<VideoTrack> = emptyList(),
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAtMs - 60_000L

    val sizes: List<Int> get() = (tracks + sharpTracks).map { it.shortSide }.distinct().sortedDescending()

    fun tracksFor(shortSide: Int): List<VideoTrack> =
        if (sharpTracks.isNotEmpty() && shortSide > (tracks.lastOrNull()?.shortSide ?: 0)) sharpTracks else tracks

    fun trackFor(shortSide: Int): VideoTrack? {
        val family = tracksFor(shortSide)
        return family.lastOrNull { it.shortSide <= shortSide } ?: family.firstOrNull()
    }
}

val List<VideoTrack>.chunked: Boolean get() = isNotEmpty() && all { it.chunked }

data class VideoTrack(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val codecs: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
    val initRange: LongRange?,
    val indexRange: LongRange?,
    val contentLength: Long?,
) {
    val shortSide: Int get() = minOf(width, height)
    val chunked: Boolean get() = initRange != null && indexRange != null
}

private val CodecsPattern = Regex("codecs=\"([^\"]+)\"")

internal fun PlayerResponse.StreamingData.Format.containerType(): String = mimeType.substringBefore(';').trim()

internal fun PlayerResponse.StreamingData.Format.codecs(): String =
    CodecsPattern.find(mimeType)?.groupValues?.get(1).orEmpty()

internal fun PlayerResponse.StreamingData.Format.ByteRange.toLongRange(): LongRange? {
    val first = start.toLongOrNull() ?: return null
    val last = end.toLongOrNull() ?: return null
    return if (last >= first) first..last else null
}

private val CodecFamilies = listOf("avc1", "vp9", "vp09", "av01")

private val SharpFamilies = listOf(
    listOf("vp9", "vp09") to "video/x-vnd.on2.vp9",
    listOf("av01") to "video/av01",
)

internal fun pickVideoFamily(formats: List<PlayerResponse.StreamingData.Format>): List<PlayerResponse.StreamingData.Format> {
    val pictures = formats.pictures()
    for (family in CodecFamilies) {
        val inFamily = pictures.filter { it.codecs().startsWith(family) }
        if (inFamily.isEmpty()) continue
        return inFamily.onePerSize()
    }
    return emptyList()
}

internal fun pickSharpFamily(
    formats: List<PlayerResponse.StreamingData.Format>,
    aboveShortSide: Int,
): List<PlayerResponse.StreamingData.Format> {
    val pictures = formats.pictures()
    for ((prefixes, decoderType) in SharpFamilies) {
        val inFamily = pictures.filter { format -> prefixes.any { format.codecs().startsWith(it) } }
            .onePerSize()
            .filter { HardwareDecoders.canPlay(decoderType, it.width ?: 0, it.height ?: 0, it.fps ?: 30) }
        if (inFamily.any { minOf(it.width ?: 0, it.height ?: 0) > aboveShortSide }) return inFamily
    }
    return emptyList()
}

private fun List<PlayerResponse.StreamingData.Format>.pictures() =
    filter { !it.isAudio && (it.width ?: 0) > 0 && (it.height ?: 0) > 0 && it.bitrate > 0 }

private fun List<PlayerResponse.StreamingData.Format>.onePerSize() =
    groupBy { minOf(it.width ?: 0, it.height ?: 0) }
        .map { (_, sameSize) -> sameSize.minWith(compareBy({ it.fps ?: 30 }, { it.bitrate })) }
        .sortedBy { minOf(it.width ?: 0, it.height ?: 0) }

internal object HardwareDecoders {
    private val byType = java.util.concurrent.ConcurrentHashMap<String, List<MediaCodecInfo.VideoCapabilities>>()

    fun canPlay(type: String, width: Int, height: Int, fps: Int): Boolean =
        byType.getOrPut(type) { find(type) }.any { caps ->
            runCatching {
                caps.areSizeAndRateSupported(width, height, fps.toDouble()) ||
                    caps.areSizeAndRateSupported(height, width, fps.toDouble())
            }.getOrDefault(false)
        }

    private fun find(type: String): List<MediaCodecInfo.VideoCapabilities> =
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { info -> !info.isEncoder && info.supportedTypes.any { it.equals(type, ignoreCase = true) } && info.isHardware() }
                .mapNotNull { runCatching { it.getCapabilitiesForType(type).videoCapabilities }.getOrNull() }
        }.getOrDefault(emptyList())

    private fun MediaCodecInfo.isHardware(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return isHardwareAccelerated
        val lower = name.lowercase()
        return !lower.startsWith("omx.google.") && !lower.startsWith("c2.android.") && !lower.contains(".sw.")
    }
}

