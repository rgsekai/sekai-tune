/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.lyrics

import kotlin.math.abs
import kotlin.math.roundToLong

class SongVideoMap(val runs: List<Run>) {

    class Run(
        val fromSongMs: Long,
        val toSongMs: Long,
        val deltaMs: Long,
        val points: List<Point> = emptyList(),
    ) {
        fun deltaAt(songMs: Long): Long {
            if (points.size < 2) return points.firstOrNull()?.deltaMs ?: deltaMs
            val first = points.first()
            val last = points.last()
            if (songMs <= first.songMs) return first.deltaMs - (driftRate * minOf(first.songMs - songMs, MaxReachMs)).roundToLong()
            if (songMs >= last.songMs) return last.deltaMs + (driftRate * minOf(songMs - last.songMs, MaxReachMs)).roundToLong()
            val i = points.indexOfFirst { it.songMs >= songMs }
            return between(points[i - 1].songMs, points[i - 1].deltaMs, points[i].songMs, points[i].deltaMs, songMs)
        }

        val firstDeltaMs: Long get() = points.firstOrNull()?.deltaMs ?: deltaMs
        val lastDeltaMs: Long get() = points.lastOrNull()?.deltaMs ?: deltaMs

        val driftRate: Double
            get() {
                if (points.size < 2) return 0.0
                val span = (points.last().songMs - points.first().songMs).coerceAtLeast(1L)
                return (points.last().deltaMs - points.first().deltaMs).toDouble() / span
            }
    }

    class Point(val songMs: Long, val deltaMs: Long)

    val isShifted: Boolean
        get() = runs.any { run -> abs(run.deltaMs) >= MinShiftMs || run.points.any { abs(it.deltaMs) >= MinShiftMs } }

    fun deltaAt(songMs: Long): Long {
        val after = runs.indexOfFirst { it.toSongMs >= songMs }
        if (after == -1) return runs.last().deltaAt(songMs)
        val run = runs[after]
        if (songMs >= run.fromSongMs || after == 0) return run.deltaAt(songMs)
        val before = runs[after - 1]
        val gap = run.fromSongMs - before.toSongMs
        val jump = run.firstDeltaMs - before.lastDeltaMs

        val drifting = before.points.size > 1 && run.points.size > 1
        val expected = before.lastDeltaMs + before.driftRate * gap
        if (drifting && abs(run.firstDeltaMs - expected) <= DriftJoinMs && abs(jump) * MaxDriftSlope <= gap) {
            return between(before.toSongMs, before.lastDeltaMs, run.fromSongMs, run.firstDeltaMs, songMs)
        }
        return if (songMs - before.toSongMs < run.fromSongMs - songMs) before.deltaAt(songMs) else run.deltaAt(songMs)
    }

    fun videoMsAt(songMs: Long): Long = songMs + deltaAt(songMs)

    override fun toString(): String =
        runs.joinToString { "${it.fromSongMs}..${it.toSongMs}:${it.deltaMs}" + if (it.points.size > 1) "(${it.firstDeltaMs}->${it.lastDeltaMs})" else "" }

    private companion object {
        const val MinShiftMs = 250L
        const val DriftJoinMs = 450L
        const val MaxDriftSlope = 4L
        const val MaxReachMs = 30_000L
    }
}

private fun between(fromMs: Long, fromDelta: Long, toMs: Long, toDelta: Long, atMs: Long): Long {
    if (toMs <= fromMs) return toDelta
    val t = (atMs - fromMs).toDouble() / (toMs - fromMs)
    return fromDelta + ((toDelta - fromDelta) * t.coerceIn(0.0, 1.0)).roundToLong()
}

