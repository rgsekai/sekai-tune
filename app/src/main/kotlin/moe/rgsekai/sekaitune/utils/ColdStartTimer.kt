/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.utils

import timber.log.Timber

object ColdStartTimer {
    private var startTime = 0L
    private val stages = mutableListOf<Pair<String, Long>>()

    @Synchronized
    fun start() {
        if (startTime != 0L) return
        startTime = System.currentTimeMillis()
        stages.clear()
        addStage("Process Start")
    }

    @Synchronized
    fun addStage(name: String) {
        if (startTime == 0L) return
        val now = System.currentTimeMillis()
        stages.add(name to now)
        val totalElapsed = now - startTime
        val stageElapsed = if (stages.size > 1) now - stages[stages.size - 2].second else 0L
        Timber.tag("ColdStart").i("Stage: %-40s | Step: %4d ms | Total: %5d ms", name, stageElapsed, totalElapsed)
    }

    @Synchronized
    fun reset() {
        startTime = 0L
        stages.clear()
    }
}
