package moe.rgsekai.sekaitune.floating

import androidx.compose.runtime.Immutable
import moe.rgsekai.sekaitune.db.entities.LyricsEntity
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import moe.rgsekai.sekaitune.lyrics.WordTimestamp
import moe.rgsekai.sekaitune.ui.component.lyricsReadRtl
import moe.rgsekai.sekaitune.ui.component.usesAgentAlignment
import moe.rgsekai.sekaitune.ui.player.LyricsAlignment
import moe.rgsekai.sekaitune.ui.player.MinInterludeGapMs
import moe.rgsekai.sekaitune.ui.player.splitBackingVocal

private const val OutroHoldMs = 1_500L
internal const val PillDots = 3
internal const val BaseSlots = 2
private const val ReadingSlots = 3
internal const val LineLeadInMs = 300L

internal enum class PillRowKind { LEAD, BACKING, READING, TRANSLATION, INTRO, TITLE, ARTIST }

@Immutable
internal class PillRow(
    val kind: PillRowKind,
    val text: String,
    val words: List<WordTimestamp>?,
    val startMs: Long,
    val endMs: Long,
    val side: LyricsAlignment,
    val line: Int = -1,
    val hostsBreak: Boolean = false,
) {
    val sweeps: Boolean get() = !words.isNullOrEmpty()
    val small: Boolean get() = kind != PillRowKind.LEAD && kind != PillRowKind.TITLE && kind != PillRowKind.INTRO
}

@Immutable
internal class LineReading(val text: String?, val words: List<WordTimestamp>? = null)

@Immutable
internal class PillReadings(
    val romanized: List<LineReading?>,
    val translations: List<String?>,
    val romanizedAsMain: Boolean,
)

@Immutable
internal class PillStop(
    val timeMs: Long,
    val top: Int,
    val breakFromMs: Long = -1L,
    val breakToMs: Long = -1L,
) {
    val isBreak: Boolean get() = breakFromMs >= 0L
}

@Immutable
internal class PillScript(
    val rows: List<PillRow>,
    val stops: List<PillStop>,
    val slots: Int,
) {
    val sung: Boolean get() = rows.any { it.kind == PillRowKind.LEAD }

    fun stopAt(positionMs: Long): Int {
        var low = 0
        var high = stops.lastIndex
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (stops[mid].timeMs <= positionMs) low = mid else high = mid - 1
        }
        return low
    }

    fun dotsLit(stop: Int, positionMs: Long): Int {
        val s = stops[stop]
        if (!s.isBreak) return -1
        val span = (s.breakToMs - s.breakFromMs).coerceAtLeast(1L)
        val through = ((positionMs - s.breakFromMs) * PillDots / span).toInt()
        return (through + 1).coerceIn(1, PillDots)
    }

    fun nextChangeMs(stop: Int, positionMs: Long): Long {
        val next = stops.getOrNull(stop + 1)?.timeMs ?: Long.MAX_VALUE
        val s = stops[stop]
        if (!s.isBreak) return next
        val span = (s.breakToMs - s.breakFromMs).coerceAtLeast(1L)
        for (dot in 1 until PillDots) {
            val at = s.breakFromMs + span * dot / PillDots
            if (at > positionMs) return minOf(at, next)
        }
        return next
    }

    companion object {
        fun info(title: String, artist: String): PillScript = PillScript(
            rows = infoRows(title, artist, line = 0),
            stops = listOf(PillStop(0L, 0)),
            slots = BaseSlots,
        )

        fun lines(raw: String?, durationSec: Int? = null): List<LyricsEntry> = raw
            ?.takeIf { it.isNotBlank() && it != LyricsEntity.LYRICS_NOT_FOUND }
            ?.let { text ->
                runCatching {
                    when {
                        LyricsUtils.isTtml(text) -> LyricsUtils.parseTtml(text, durationSec)
                        else -> LyricsUtils.parseLyrics(text)
                    }
                }.getOrNull()
            }
            .orEmpty()

        fun build(
            lines: List<LyricsEntry>,
            title: String,
            artist: String,
            appRtl: Boolean,
            readings: PillReadings? = null,
        ): PillScript {
            val syncedLines = lines.filter { it.text.isNotBlank() }
            if (syncedLines.isEmpty() || !syncedLines.any { it.time > 0L || it.durationMs > 0L }) return info(title, artist)
            val mirrored = lyricsReadRtl(lines) != appRtl
            val groups = groupLines(lines, usesAgentAlignment(lines), mirrored, readings)
            if (groups.isEmpty()) return info(title, artist)
            return layOut(groups, title, artist)
        }
    }
}

private class LineGroup(val lead: PillRow) {
    var reading: PillRow? = null
    val backing = ArrayList<PillRow>(1)
    var translation: PillRow? = null
    val startMs: Long get() = lead.startMs
    val endMs: Long get() = backing.fold(lead.endMs) { end, row -> maxOf(end, row.endMs) }

    fun rows(): List<PillRow> = buildList {
        add(lead)
        reading?.let(::add)
        addAll(backing)
        translation?.let(::add)
    }
}

private fun infoRows(title: String, artist: String, line: Int) = listOf(
    PillRow(PillRowKind.TITLE, title, null, 0L, 0L, LyricsAlignment.START, line),
    PillRow(PillRowKind.ARTIST, artist, null, 0L, 0L, LyricsAlignment.START, line),
)

private fun lineEndMs(lines: List<LyricsEntry>, index: Int): Long {
    val entry = lines.getOrNull(index) ?: return 0L
    if (entry.durationMs > 0L) return entry.time + entry.durationMs
    val words = entry.syllables ?: entry.words
    val lastWord = words?.lastOrNull()
    if (lastWord != null && lastWord.endTime > 0.0) {
        return (lastWord.endTime * 1000.0).toLong()
    }
    val next = lines.getOrNull(index + 1)
    return if (next != null && next.time > entry.time) next.time else entry.time + 3_000L
}

private fun alignmentFor(entry: LyricsEntry, useAgentAlignment: Boolean, mirrored: Boolean): LyricsAlignment = when {
    !useAgentAlignment -> LyricsAlignment.START
    entry.isBackground -> LyricsAlignment.CENTER
    entry.agent == "v2" -> if (mirrored) LyricsAlignment.START else LyricsAlignment.END
    entry.agent == "v1000" -> LyricsAlignment.CENTER
    else -> if (mirrored) LyricsAlignment.END else LyricsAlignment.START
}

private fun groupLines(
    lines: List<LyricsEntry>,
    duet: Boolean,
    mirrored: Boolean,
    readings: PillReadings?,
): List<LineGroup> {
    val groups = ArrayList<LineGroup>(lines.size)
    for (index in lines.indices) {
        val entry = lines[index]
        if (entry.text.isBlank()) continue
        val endMs = lineEndMs(lines, index)
        val owner = groups.lastOrNull()
        val entryWords = entry.syllables ?: entry.words
        if (entry.isBackground && owner != null) {
            val parts = splitBackingVocal(entry.text, entryWords)
            val text = parts.backing?.takeIf { parts.lead.isBlank() } ?: entry.text
            val words = if (parts.lead.isBlank()) parts.backingWords else entryWords
            owner.backing += PillRow(PillRowKind.BACKING, text, words, entry.time, endMs, owner.lead.side)
            continue
        }
        val side = alignmentFor(entry, useAgentAlignment = duet, mirrored = mirrored)
        val reading = readings?.romanized?.getOrNull(index)?.takeIf { !it.text.isNullOrBlank() }
        val asMain = reading != null && readings.romanizedAsMain
        val text = if (asMain) reading?.text.orEmpty() else entry.text
        val words = if (asMain) reading?.words else entryWords
        val parts = splitBackingVocal(text, words)
        val group = when {
            parts.backing == null -> LineGroup(PillRow(PillRowKind.LEAD, text, words, entry.time, endMs, side))
            parts.lead.isBlank() -> LineGroup(PillRow(PillRowKind.LEAD, parts.backing, parts.backingWords, entry.time, endMs, side))
            else -> LineGroup(PillRow(PillRowKind.LEAD, parts.lead, parts.leadWords, entry.time, endMs, side)).also {
                val backingWords = parts.backingWords
                val backingStart = backingWords?.firstOrNull()?.let { word -> (word.startTime * 1000).toLong() } ?: entry.time
                val backingEnd = backingWords?.lastOrNull()?.let { word -> (word.endTime * 1000).toLong() } ?: endMs
                it.backing += PillRow(PillRowKind.BACKING, parts.backing, backingWords, backingStart, backingEnd, side)
            }
        }
        if (reading != null) {
            group.reading = if (asMain) {
                PillRow(PillRowKind.READING, entry.text, entryWords, entry.time, endMs, side)
            } else {
                PillRow(PillRowKind.READING, reading.text.orEmpty(), reading.words, entry.time, endMs, side)
            }
        }
        group.translation = readings?.translations?.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?.let { PillRow(PillRowKind.TRANSLATION, it, null, entry.time, endMs, side) }
        groups += group
    }
    return groups
}

private fun layOut(groups: List<LineGroup>, title: String, artist: String): PillScript {
    val rows = ArrayList<PillRow>(groups.size * 3 + 3)
    val leadRow = IntArray(groups.size)
    val intro = groups.first().startMs >= MinInterludeGapMs
    if (intro) rows += PillRow(PillRowKind.INTRO, "", null, 0L, groups.first().startMs, LyricsAlignment.START)
    for ((k, group) in groups.withIndex()) {
        val breakAfter = groups.getOrNull(k + 1)?.let { it.startMs - group.endMs >= MinInterludeGapMs } ?: false
        leadRow[k] = rows.size
        val own = group.rows()
        own.forEachIndexed { i, row -> rows += row.placed(line = k, hostsBreak = breakAfter && i == own.lastIndex) }
    }
    val titleRow = rows.size
    rows += infoRows(title, artist, line = groups.size)

    val stops = ArrayList<PillStop>(groups.size * 2 + 2)
    stops += if (intro) {
        PillStop(0L, 0, breakFromMs = 0L, breakToMs = groups.first().startMs)
    } else {
        PillStop(0L, leadRow[0])
    }
    for ((k, group) in groups.withIndex()) {
        stops.addInOrder(PillStop((group.startMs - LineLeadInMs).coerceAtLeast(0L), leadRow[k]))
        val next = groups.getOrNull(k + 1) ?: continue
        if (next.startMs - group.endMs >= MinInterludeGapMs) {
            stops.addInOrder(PillStop(group.endMs, leadRow[k + 1] - 1, breakFromMs = group.endMs, breakToMs = next.startMs))
        }
    }
    stops.addInOrder(PillStop(groups.maxOf { it.endMs } + OutroHoldMs, titleRow))
    val slots = if (groups.any { it.reading != null || it.translation != null }) ReadingSlots else BaseSlots
    return PillScript(rows, stops, slots)
}

private fun PillRow.placed(line: Int, hostsBreak: Boolean) =
    PillRow(kind, text, words, startMs, endMs, side, line, hostsBreak)

private fun MutableList<PillStop>.addInOrder(stop: PillStop) {
    while (isNotEmpty() && last().timeMs >= stop.timeMs && size > 1) removeAt(lastIndex)
    if (isNotEmpty() && last().timeMs >= stop.timeMs) {
        set(lastIndex, PillStop(last().timeMs, stop.top, stop.breakFromMs, stop.breakToMs))
        return
    }
    add(stop)
}

