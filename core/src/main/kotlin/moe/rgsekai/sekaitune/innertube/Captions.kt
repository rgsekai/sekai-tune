/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import moe.rgsekai.sekaitune.innertube.models.YouTubeClient
import moe.rgsekai.sekaitune.innertube.models.response.PlayerResponse.Captions.CaptionTrack

data class CaptionCue(val startMs: Long, val endMs: Long, val text: String)

class VideoCaptions internal constructor(
    val videoId: String,
    val tracks: List<CaptionTrack>,
    internal val client: YouTubeClient,
)

internal fun timedTextToCues(json: String): List<CaptionCue> {
    val events = Json.parseToJsonElement(json).jsonObject["events"]?.jsonArray ?: return emptyList()
    val cues = ArrayList<CaptionCue>()
    for (event in events) {
        val cue = event.jsonObject
        val start = cue["tStartMs"]?.jsonPrimitive?.longOrNull ?: continue
        val end = start + (cue["dDurationMs"]?.jsonPrimitive?.longOrNull ?: 0L)
        val segs = cue["segs"]?.jsonArray ?: continue
        val pieces = ArrayList<Pair<Long, StringBuilder>>()
        for (seg in segs) {
            val segObject = seg.jsonObject
            val text = segObject["utf8"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val offset = segObject["tOffsetMs"]?.jsonPrimitive?.longOrNull
            if (offset != null || pieces.isEmpty()) pieces += (start + (offset ?: 0L)) to StringBuilder(text)
            else pieces.last().second.append(text)
        }
        pieces.forEachIndexed { index, (pieceStart, raw) ->
            val text = raw.toString()
                .replace(SoundLabel, " ")
                .replace('♪', ' ')
                .replace(Whitespace, " ")
                .trim()
            if (text.isEmpty()) return@forEachIndexed
            val pieceEnd = pieces.getOrNull(index + 1)?.first ?: end
            cues += CaptionCue(pieceStart, maxOf(pieceEnd, pieceStart), text)
        }
    }
    return cues.sortedBy { it.startMs }
}

private val SoundLabel = Regex("""\[[^\]]*\]""")
private val Whitespace = Regex("""\s+""")

