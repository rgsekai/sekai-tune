package moe.rgsekai.sekaitune.eq.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlinx.serialization.Serializable
import kotlin.math.min
import kotlin.math.pow

@Serializable
enum class ReplayGainMode { Track, Album }

@Serializable
data class ReplayGainParams(
    val mode: ReplayGainMode = ReplayGainMode.Track,
    val preampDb: Float = 3f,
)

class ReplayGainTags {
    @Volatile var trackGain: Float? = null
        private set
    @Volatile var trackPeak: Float? = null
        private set
    @Volatile var albumGain: Float? = null
        private set
    @Volatile var albumPeak: Float? = null
        private set

    val present: Boolean get() = trackGain != null || albumGain != null

    @OptIn(UnstableApi::class)
    fun update(tracks: Tracks) {
        val comments = ArrayList<Pair<String, String>>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSelected(i)) continue
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (e in 0 until metadata.length()) {
                    val entry = metadata.get(e)
                    if (entry is VorbisComment) {
                        comments += entry.key to entry.value
                    } else if (entry is TextInformationFrame) {
                        comments += entry.id to (entry.values.firstOrNull() ?: entry.value)
                    }
                }
            }
        }
        update(comments)
    }

    fun update(comments: List<Pair<String, String>>) {
        var tg: Float? = null
        var tp: Float? = null
        var ag: Float? = null
        var ap: Float? = null
        for ((key, text) in comments) {
            val value = number(text) ?: continue
            when (key.uppercase()) {
                "REPLAYGAIN_TRACK_GAIN" -> tg = value
                "REPLAYGAIN_TRACK_PEAK" -> tp = value
                "REPLAYGAIN_ALBUM_GAIN" -> ag = value
                "REPLAYGAIN_ALBUM_PEAK" -> ap = value
            }
        }
        trackGain = tg
        trackPeak = tp
        albumGain = ag
        albumPeak = ap
    }

    fun linear(params: ReplayGainParams, normalize: Boolean): Float {
        if (!normalize) return 1f
        val album = params.mode == ReplayGainMode.Album
        val gain = (if (album) albumGain ?: trackGain else trackGain ?: albumGain) ?: return 1f
        val peak = if (album) albumPeak ?: trackPeak else trackPeak ?: albumPeak
        var factor = 10f.pow((gain + params.preampDb) / 20f)
        if (peak != null && peak > 0f) factor = min(factor, 1f / peak)
        return factor.coerceIn(0.03f, 1f)
    }

    companion object {
        internal fun number(text: String): Float? =
            Regex("""[-+]?\d+(?:\.\d+)?""").find(text)?.value?.toFloatOrNull()
    }
}

