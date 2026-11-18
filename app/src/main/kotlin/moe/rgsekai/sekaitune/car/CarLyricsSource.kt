package moe.rgsekai.sekaitune.car

import moe.rgsekai.sekaitune.db.MusicDatabase
import moe.rgsekai.sekaitune.db.entities.LyricsEntity
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsHelper
import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import moe.rgsekai.sekaitune.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class CarLyricLines(
    val mediaId: String,
    val lines: List<LyricsEntry>,
    val offsetMs: Int,
    val synced: Boolean,
) {
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        val index = LyricsUtils.findCurrentLineIndex(lines, positionMs + offsetMs)
        return if (index in lines.indices) index else -1
    }

    fun nextSungAfter(index: Int): LyricsEntry? = lines.drop(index + 1).firstOrNull { it.text.isNotBlank() }

    fun isBreak(index: Int, positionMs: Long): Boolean {
        val position = positionMs + offsetMs
        val current = lines.getOrNull(index) ?: return false
        val end = current.time + if (current.durationMs > 0) current.durationMs else 3000L
        val next = nextSungAfter(index)
        return position > end + BreakAfterLineMs &&
            (next == null || next.time - position > BreakBeforeLineMs)
    }

    private companion object {
        const val BreakAfterLineMs = 1_500L
        const val BreakBeforeLineMs = 2_500L
    }
}

class CarLyricsSource(
    private val database: MusicDatabase,
    private val lyricsHelper: LyricsHelper,
) {
    suspend fun load(metadata: MediaMetadata): CarLyricLines? {
        val raw = withContext(Dispatchers.IO) { database.lyrics(metadata.id).first()?.lyrics } ?: fetch(metadata) ?: return null
        if (raw.isBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) return null
        val offset = withContext(Dispatchers.IO) { database.song(metadata.id).first()?.song?.lyricsOffset ?: 0 }
        return withContext(Dispatchers.Default) {
            val timed = LyricsUtils.parseLyrics(raw).filterNot { it.isBackground }
            val synced = LyricsUtils.isLineSyncedLrc(raw) || LyricsUtils.isTtml(raw)
            if (timed.isNotEmpty()) {
                CarLyricLines(metadata.id, timed, offset, synced = synced)
            } else {
                null
            }
        }
    }

    suspend fun fetch(metadata: MediaMetadata): String? {
        val found = runCatching { lyricsHelper.getLyrics(metadata) }.getOrNull() ?: return null
        if (found.isBlank()) return null
        database.query { upsert(LyricsEntity(id = metadata.id, lyrics = found)) }
        return found
    }
}
