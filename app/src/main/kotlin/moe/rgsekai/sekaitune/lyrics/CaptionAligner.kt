/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.lyrics

import moe.rgsekai.sekaitune.innertube.CaptionCue
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.min

internal object CaptionAligner {

    class Anchor(val lyricMs: Long, val videoMs: Long, val score: Float) {
        val deltaMs: Long get() = videoMs - lyricMs
    }

    class Alignment(val anchors: List<Anchor>, val map: SongVideoMap, val matchedLines: Int, val usableLines: Int)

    fun align(lines: List<LyricsEntry>, cues: List<CaptionCue>): Alignment? {
        val stream = CaptionStream.of(cues) ?: return null
        val gram = if (stream.isMostlyCjk) 2 else 3
        val index = stream.gramIndex(gram)

        val mainLines = lines.filter { it.text.isNotBlank() }
        var usable = 0
        val nodes = ArrayList<Node>()
        mainLines.forEachIndexed { lineIndex, line ->
            val text = normalize(line.text)
            if (text.length < gram + 2) return@forEachIndexed
            usable++
            candidates(text, gram, index).forEach { (position, score) ->
                nodes += Node(lineIndex, line.time, stream.timeAt(position), position, score)
            }
        }
        val path = bestPath(nodes)
        val anchors = dropOutliers(path.map { Anchor(it.lyricMs, it.videoMs, it.score) })
        if (anchors.size < MinAnchors || anchors.size < usable * MinMatchedShare) return null
        val runs = runsOf(anchors) ?: return null
        return Alignment(anchors, SongVideoMap(runs), anchors.size, usable)
    }

    fun normalize(text: String): String {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        val out = StringBuilder(folded.length)
        for (ch in folded) {
            if (!ch.isLetterOrDigit()) continue
            out.append(if (ch in 'ァ'..'ヶ') ch - 0x60 else ch)
        }
        return out.toString()
    }

    private fun isCjk(ch: Char): Boolean =
        ch in '぀'..'ヿ' || ch in '一'..'鿿' || ch in '가'..'힯' || ch in '㐀'..'䶿'

    private class CaptionStream(val text: String, val times: LongArray) {
        val isMostlyCjk: Boolean get() = text.count(::isCjk) * 10 >= text.length * 4

        fun timeAt(position: Int): Long = times[position.coerceIn(0, times.lastIndex)]

        fun gramIndex(size: Int): Map<String, IntArray> {
            val positions = HashMap<String, MutableList<Int>>()
            for (start in 0..text.length - size) {
                positions.getOrPut(text.substring(start, start + size)) { ArrayList(2) } += start
            }
            return positions.filterValues { it.size <= MaxGramRepeats }.mapValues { it.value.toIntArray() }
        }

        companion object {
            fun of(cues: List<CaptionCue>): CaptionStream? {
                val text = StringBuilder()
                val times = ArrayList<Long>()
                for (cue in cues) {
                    val letters = normalize(cue.text)
                    if (letters.isEmpty()) continue
                    val perLetter = min((cue.endMs - cue.startMs).coerceAtLeast(0) / letters.length, MaxMsPerLetter)
                    letters.forEachIndexed { k, letter ->
                        text.append(letter)
                        times += cue.startMs + perLetter * k
                    }
                }
                if (text.length < 20) return null
                return CaptionStream(text.toString(), times.toLongArray())
            }
        }
    }

    private fun candidates(line: String, gram: Int, index: Map<String, IntArray>): List<Pair<Int, Float>> {
        val grams = line.length - gram + 1
        val bucket = maxOf(4, line.length / 4)
        val votes = HashMap<Int, Int>()
        val starts = HashMap<Int, MutableList<Int>>()
        val seen = HashSet<Int>()
        for (k in 0 until grams) {
            val positions = index[line.substring(k, k + gram)] ?: continue
            seen.clear()
            for (position in positions) {
                val start = position - k
                val center = Math.floorDiv(start, bucket)
                for (b in center - 1..center + 1) {
                    if (seen.add(b)) votes[b] = (votes[b] ?: 0) + 1
                }
                starts.getOrPut(center) { ArrayList() } += start
            }
        }
        val picked = ArrayList<Pair<Int, Float>>()
        val taken = ArrayList<Int>()
        for ((b, count) in votes.entries.sortedByDescending { it.value }) {
            val score = count.toFloat() / grams
            if (score < MinLineScore || picked.size >= MaxCandidates) break
            if (taken.any { abs(it - b) <= 2 }) continue
            val near = (b - 1..b + 1).flatMap { starts[it].orEmpty() }.sorted()
            if (near.isEmpty()) continue
            taken += b
            picked += near[near.size / 2] to score.coerceAtMost(1f)
        }
        return picked
    }

    private class Node(val line: Int, val lyricMs: Long, val videoMs: Long, val position: Int, val score: Float)

    private fun bestPath(nodes: List<Node>): List<Node> {
        if (nodes.isEmpty()) return emptyList()
        val sorted = nodes.sortedWith(compareBy({ it.line }, { it.position }))
        val total = FloatArray(sorted.size)
        val previous = IntArray(sorted.size) { -1 }
        for (i in sorted.indices) {
            val node = sorted[i]
            total[i] = node.score
            for (j in 0 until i) {
                val before = sorted[j]
                if (before.line >= node.line || before.position >= node.position || before.videoMs > node.videoMs) continue
                val jump = abs((node.videoMs - node.lyricMs) - (before.videoMs - before.lyricMs))
                val penalty = min(jump / 1000f * JumpCostPerSecond, MaxJumpCost)
                val candidate = total[j] - penalty + node.score
                if (candidate > total[i]) {
                    total[i] = candidate
                    previous[i] = j
                }
            }
        }
        var at = total.indices.maxBy { total[it] }
        val path = ArrayList<Node>()
        while (at != -1) {
            path += sorted[at]
            at = previous[at]
        }
        return path.asReversed()
    }

    private fun dropOutliers(anchors: List<Anchor>): List<Anchor> = anchors.filterIndexed { i, anchor ->
        if (anchor.score >= 0.75f) return@filterIndexed true
        val neighbours = (maxOf(0, i - 2)..minOf(anchors.lastIndex, i + 2)).filter { it != i }.map { anchors[it].deltaMs }
        if (neighbours.size < 2) return@filterIndexed true
        abs(anchor.deltaMs - median(neighbours)) <= OutlierMs
    }

    private fun runsOf(anchors: List<Anchor>): List<SongVideoMap.Run>? {
        val groups = ArrayList<MutableList<Anchor>>()
        for (anchor in anchors) {
            val current = groups.lastOrNull()
            val recent = current?.takeLast(RecentAnchors)?.map { it.deltaMs }
            if (current != null && abs(anchor.deltaMs - median(recent!!)) <= RunToleranceMs) {
                current += anchor
            } else {
                groups += mutableListOf(anchor)
            }
        }
        val steady = groups.filter { it.size >= MinRunAnchors }
        if (steady.isEmpty()) return null
        return steady.map { group ->
            val points = group.indices.map { i -> SongVideoMap.Point(group[i].lyricMs, smoothedDelta(group, i)) }
            SongVideoMap.Run(group.first().lyricMs, group.last().lyricMs, median(group.map { it.deltaMs }), points)
        }
    }

    private fun smoothedDelta(group: List<Anchor>, i: Int): Long {
        val from = maxOf(0, i - SmoothReach)
        val to = minOf(group.size, i + SmoothReach + 1)
        var window = group.subList(from, to)
        if (window.size < 3) return median(window.map { it.deltaMs })
        val first = fitLine(window)
        val kept = window.filter { abs(it.deltaMs - first.at(it.lyricMs)) <= FitOutlierMs }
        if (kept.size >= 3) window = kept
        return fitLine(window).at(group[i].lyricMs)
    }

    private class Fit(val intercept: Double, val slope: Double) {
        fun at(ms: Long): Long = (intercept + slope * ms).toLong()
    }

    private fun fitLine(anchors: List<Anchor>): Fit {
        val meanX = anchors.sumOf { it.lyricMs.toDouble() } / anchors.size
        val meanY = anchors.sumOf { it.deltaMs.toDouble() } / anchors.size
        var top = 0.0
        var bottom = 0.0
        for (anchor in anchors) {
            val dx = anchor.lyricMs - meanX
            top += dx * (anchor.deltaMs - meanY)
            bottom += dx * dx
        }
        val slope = if (bottom > 0.0) (top / bottom).coerceIn(-MaxDriftRate, MaxDriftRate) else 0.0
        return Fit(meanY - slope * meanX, slope)
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private const val MaxGramRepeats = 60
    private const val MaxMsPerLetter = 250L
    private const val MinLineScore = 0.34f
    private const val MaxCandidates = 4
    private const val JumpCostPerSecond = 0.25f
    private const val MaxJumpCost = 0.8f
    private const val OutlierMs = 2_000L
    private const val RunToleranceMs = 700L
    private const val RecentAnchors = 3
    private const val SmoothReach = 3
    private const val FitOutlierMs = 300L
    private const val MaxDriftRate = 0.1
    private const val MinRunAnchors = 2
    private const val MinAnchors = 3
    private const val MinMatchedShare = 0.3f
}

