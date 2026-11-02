package moe.rgsekai.sekaitune.desktop

import moe.rgsekai.sekaitune.shared.data.lyrics.LrcLib as LegacyLrcLib
import moe.rgsekai.sekaitune.shared.data.lyrics.LyricLine
import moe.rgsekai.sekaitune.shared.data.lyrics.withBackgroundVocals
import moe.rgsekai.sekaitune.shared.data.model.Song
import moe.rgsekai.sekaitune.desktop.DesktopLyrics
import moe.rgsekai.sekaitune.desktop.DesktopTrackLog
import moe.rgsekai.sekaitune.betterlyrics.BetterLyrics
import moe.rgsekai.sekaitune.kugou.KuGou
import moe.rgsekai.sekaitune.lrclib.LrcLib
import moe.rgsekai.sekaitune.paxsenix.PaxsenixLyrics
import moe.rgsekai.sekaitune.simpmusic.SimpMusicLyrics
import moe.rgsekai.sekaitune.unison.Unison
import moe.rgsekai.sekaitune.youlyplus.YouLyPlus

/**
 * Adapter that invokes Sekai Tune's lyrics providers in Android default priority order:
 * 1. BetterLyrics
 * 2. YouLyPlus
 * 3. LrcLib
 * 4. KuGou
 * 5. SimpMusic
 * 6. Unison
 * 7. Paxsenix (Auto)
 */
internal object SekaiLyricsAdapter {

    private fun log(message: String) {
        DesktopTrackLog.log("[engine] $message")
        println("[engine] $message")
    }

    suspend fun lookup(song: Song, durationMs: Long): Result<DesktopLyrics> {
        val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(0)

        // 1. BetterLyrics
        try {
            val result = BetterLyrics.getLyrics(
                title = song.title,
                artist = song.artist,
                album = song.albumName,
                durationSeconds = durationSec,
            )
            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { raw ->
                val lines = parseToLines(raw)
                if (lines.isNotEmpty()) {
                    log("op=lyrics used=sekai provider=BetterLyrics")
                    return Result.success(DesktopLyrics("BetterLyrics", lines))
                }
            }
        } catch (e: Exception) {
            val reason = "${e::class.java.simpleName}: ${e.message}".take(200)
            log("op=lyrics provider=BetterLyrics failed reason=$reason")
        }

        // 2. YouLyPlus
        try {
            val result = YouLyPlus.getLyrics(
                title = song.title,
                artist = song.artist,
                album = song.albumName,
                durationSeconds = durationSec,
            )
            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { raw ->
                val lines = parseToLines(raw)
                if (lines.isNotEmpty()) {
                    log("op=lyrics used=sekai provider=YouLyPlus")
                    return Result.success(DesktopLyrics("YouLyPlus", lines))
                }
            }
        } catch (e: Exception) {
            val reason = "${e::class.java.simpleName}: ${e.message}".take(200)
            log("op=lyrics provider=YouLyPlus failed reason=$reason")
        }

        // 3. LrcLib
        try {
            val result = LrcLib.getLyrics(
                title = song.title,
                artist = song.artist,
                duration = durationSec,
                album = song.albumName,
            )
            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { raw ->
                val lines = parseToLines(raw)
                if (lines.isNotEmpty()) {
                    log("op=lyrics used=sekai provider=LrcLib")
                    return Result.success(DesktopLyrics("LrcLib", lines))
                }
            }
        } catch (e: Exception) {
            val reason = "${e::class.java.simpleName}: ${e.message}".take(200)
            log("op=lyrics provider=LrcLib failed reason=$reason")
        }

        // 4. KuGou
        try {
            val result = KuGou.getLyrics(
                title = song.title,
                artist = song.artist,
                duration = durationSec,
            )
            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { raw ->
                val lines = parseToLines(raw)
                if (lines.isNotEmpty()) {
                    log("op=lyrics used=sekai provider=KuGou")
                    return Result.success(DesktopLyrics("KuGou", lines))
                }
            }
        } catch (e: Exception) {
            val reason = "${e::class.java.simpleName}: ${e.message}".take(200)
            log("op=lyrics provider=KuGou failed reason=$reason")
        }

        // 5. SimpMusic
        try {
            val result = SimpMusicLyrics.getLyrics(
                videoId = song.videoId,
                duration = durationSec,
            )
            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { raw ->
                val lines = parseToLines(raw)
                if (lines.isNotEmpty()) {
                    log("op=lyrics used=sekai provider=SimpMusic")
                    return Result.success(DesktopLyrics("SimpMusic", lines))
                }
            }
        } catch (e: Exception) {
            val reason = "${e::class.java.simpleName}: ${e.message}".take(200)
            log("op=lyrics provider=SimpMusic failed reason=$reason")
        }

        // 6. Unison
        try {
            val result = Unison.getLyrics(
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                album = song.albumName,
                durationSeconds = durationSec,
            )
            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { raw ->
                val lines = parseToLines(raw)
                if (lines.isNotEmpty()) {
                    log("op=lyrics used=sekai provider=Unison")
                    return Result.success(DesktopLyrics("Unison", lines))
                }
            }
        } catch (e: Exception) {
            val reason = "${e::class.java.simpleName}: ${e.message}".take(200)
            log("op=lyrics provider=Unison failed reason=$reason")
        }

        // 7. Paxsenix (Auto)
        try {
            val result = PaxsenixLyrics.getLyrics(
                title = song.title,
                artist = song.artist,
                durationSeconds = durationSec,
            )
            result.getOrNull()?.takeIf { it.isNotBlank() }?.let { raw ->
                val lines = parseToLines(raw)
                if (lines.isNotEmpty()) {
                    log("op=lyrics used=sekai provider=Paxsenix")
                    return Result.success(DesktopLyrics("Paxsenix", lines))
                }
            }
        } catch (e: Exception) {
            val reason = "${e::class.java.simpleName}: ${e.message}".take(200)
            log("op=lyrics provider=Paxsenix failed reason=$reason")
        }

        log("op=lyrics used=fallback reason=all_sekai_providers_exhausted")
        return Result.failure(IllegalStateException("No lyrics found across Sekai Tune providers"))
    }

    private fun parseToLines(raw: String): List<LyricLine> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("<tt") || trimmed.contains("<body") || trimmed.contains("<div") || trimmed.contains("<p")) {
            val ttmlParsed = moe.rgsekai.sekaitune.shared.data.lyrics.TtmlLyrics.parse(raw)
            if (ttmlParsed.isNotEmpty()) {
                return ttmlParsed
            }
        }
        val parsed = LegacyLrcLib.parseLrc(raw)
        return if (parsed.any { it.text.isNotBlank() }) {
            parsed.withBackgroundVocals()
        } else {
            // Plain text fallback
            raw.lines().filter { it.isNotBlank() }.map { line ->
                LyricLine(timeMs = 0L, text = line)
            }
        }
    }
}
