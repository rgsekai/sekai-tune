/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player.video

import moe.rgsekai.sekaitune.utils.VideoTrack
import moe.rgsekai.sekaitune.utils.chunked
import java.util.Locale

internal object VideoDash {
    fun manifest(family: List<VideoTrack>, durationMs: Long): String? {
        if (!family.chunked || durationMs <= 0L) return null
        val mimeType = family.first().mimeType
        val tracks = family.filter { it.mimeType == mimeType }
        val duration = seconds(durationMs)
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\"")
            append(" type=\"static\" minBufferTime=\"PT1.500S\" mediaPresentationDuration=\"").append(duration).append("\">")
            append("<Period start=\"PT0S\" duration=\"").append(duration).append("\">")
            append("<AdaptationSet id=\"0\" contentType=\"video\" mimeType=\"").append(escape(mimeType))
            append("\" subsegmentAlignment=\"true\" subsegmentStartsWithSAP=\"1\">")
            for (track in tracks) {
                val init = track.initRange ?: continue
                val index = track.indexRange ?: continue
                append("<Representation id=\"").append(track.itag)
                append("\" codecs=\"").append(escape(track.codecs))
                append("\" bandwidth=\"").append(track.bitrate)
                append("\" width=\"").append(track.width)
                append("\" height=\"").append(track.height)
                append("\" frameRate=\"").append(track.fps).append("\">")
                append("<BaseURL>").append(escape(track.url)).append("</BaseURL>")
                append("<SegmentBase indexRange=\"").append(index.first).append('-').append(index.last).append("\">")
                append("<Initialization range=\"").append(init.first).append('-').append(init.last).append("\"/>")
                append("</SegmentBase></Representation>")
            }
            append("</AdaptationSet></Period></MPD>")
        }
    }

    private fun seconds(ms: Long) = String.format(Locale.ROOT, "PT%.3fS", ms / 1000.0)

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

