package moe.rgsekai.sekaitune.ui.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp

// how much more each row below the active line lags, per row of distance
private const val StaggerLagPerRowMs = 55f
private const val StaggerMaxRows = 9

// style 1's follow-through on an auto scroll: rows below the active line trail the scroll, the further down the later, then catch up on their own
@Stable
internal class LyricsStagger(rowCount: Int) {
    private val lags = FloatArray(rowCount)
    private val velocities = FloatArray(rowCount)

    // the numbers aren't snapshot state, so one tick tells the layers reading them to redraw
    private val version = mutableIntStateOf(0)
    private val moving = mutableStateOf(false)

    var anchorRow = -1

    // how far this row trails its place, in px; read it in a graphics layer only
    fun lagOf(row: Int): Float {
        version.intValue
        return if (row in lags.indices) lags[row] else 0f
    }

    // the list just scrolled by px and this row holds back by that much
    fun holdBack(row: Int, px: Float) {
        if (row !in lags.indices) return
        lags[row] += px
        version.intValue++
        moving.value = true
    }

    // runs for as long as the page is open, asleep whenever every row is in place
    suspend fun run() {
        while (true) {
            snapshotFlow { moving.value }.first { it }
            var lastNanos = 0L
            var still: Boolean
            do {
                still = withFrameNanos { now ->
                    val dt = if (lastNanos == 0L) 0.016f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.064f)
                    lastNanos = now
                    step(dt).also { version.intValue++ }
                }
            } while (still || anyLag())
            moving.value = false
        }
    }

    // each row chases its place on a critically damped spring, solved exactly so any frame time is stable
    private fun step(dt: Float): Boolean {
        var any = false
        for (row in lags.indices) {
            val lag = lags[row]
            val v = velocities[row]
            if (lag == 0f && v == 0f) continue
            val k = (row - anchorRow).coerceIn(1, StaggerMaxRows)
            val omega = 1000f / (StaggerLagPerRowMs * k)
            val e = exp(-omega * dt)
            val tmp = (v + omega * lag) * dt
            val nextLag = (lag + tmp) * e
            val nextV = (v - omega * tmp) * e
            if (abs(nextLag) < 0.3f && abs(nextV) < 4f) {
                lags[row] = 0f
                velocities[row] = 0f
            } else {
                lags[row] = nextLag
                velocities[row] = nextV
                any = true
            }
        }
        return any
    }

    private fun anyLag(): Boolean = lags.any { it != 0f }
}

