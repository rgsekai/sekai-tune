/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.lyrics.CaptionAligner
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import moe.rgsekai.sekaitune.lyrics.VideoCaptionSource

class LyricVideoTimeline internal constructor(private val alignment: CaptionAligner.Alignment) {
    fun videoMsAt(songLyricMs: Long): Long = alignment.map.videoMsAt(songLyricMs)
}

private fun isUnsynced(lines: List<LyricsEntry>): Boolean {
    if (lines.size < 2) return false
    if (lines.any { !it.words.isNullOrEmpty() }) return false
    val first = lines.first().time
    return lines.all { it.time == first }
}

suspend fun buildLyricVideoTimeline(videoId: String, songLyricsRaw: String): LyricVideoTimeline? {
    val lines = withContext(Dispatchers.Default) { LyricsUtils.parseLyrics(songLyricsRaw) }
    if (lines.size < 4 || isUnsynced(lines)) return null
    val captions = VideoCaptionSource.captions(videoId) ?: return null
    val aligned = VideoCaptionSource.align(captions, lines) ?: return null
    return LyricVideoTimeline(aligned.alignment)
}

object LyricVideoTimelineCache {
    private const val maxSize = 32
    private val map = LinkedHashMap<String, LyricVideoTimeline?>(maxSize, 0.75f, true)

    @Synchronized
    fun contains(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun get(key: String): LyricVideoTimeline? = map[key]

    @Synchronized
    fun put(key: String, timeline: LyricVideoTimeline?) {
        map[key] = timeline
        if (map.size > maxSize) map.remove(map.keys.first())
    }
}

