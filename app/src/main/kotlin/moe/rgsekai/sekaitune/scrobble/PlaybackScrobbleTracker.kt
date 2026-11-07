/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.scrobble

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.models.MediaMetadata
import kotlin.math.min

class PlaybackScrobbleTracker(
    private val scope: () -> CoroutineScope,
    private val scrobbleManager: ScrobbleManager,
) {
    private var currentMetadata: MediaMetadata? = null
    private var durationSeconds: Int = 0
    private var startedAtSeconds: Long = 0L

    private var remainingTimeMs: Long = 0L
    private var runningSinceElapsedRealtime: Long = 0L
    private var trackerJob: Job? = null
    private var isScrobbled: Boolean = false

    fun onSongStart(metadata: MediaMetadata?, durationMs: Long) {
        onSongStop()
        if (metadata == null || metadata.title.isBlank() || metadata.artists.isEmpty()) return

        currentMetadata = metadata
        val lengthSec = if (durationMs > 0) (durationMs / 1000).toInt() else metadata.duration
        durationSeconds = lengthSec
        startedAtSeconds = System.currentTimeMillis() / 1000
        isScrobbled = false

        scrobbleManager.onNowPlaying(metadata, lengthSec)

        if (lengthSec < MIN_SONG_DURATION_SECONDS) return

        // Last.fm rule: 50% played or 240 seconds, whichever comes first
        val targetMs = min((lengthSec * 1000L * PLAYED_RATIO_THRESHOLD).toLong(), MAX_WAIT_DURATION_SECONDS * 1000L)
        remainingTimeMs = targetMs
        resumeTimer()
    }

    fun onIsPlayingChanged(isPlaying: Boolean, metadata: MediaMetadata?, durationMs: Long) {
        if (!isPlaying) {
            pauseTimer()
        } else {
            if (currentMetadata == null) {
                onSongStart(metadata, durationMs)
            } else {
                resumeTimer()
            }
        }
    }

    fun onSongStop() {
        trackerJob?.cancel()
        trackerJob = null
        currentMetadata = null
        durationSeconds = 0
        remainingTimeMs = 0L
        runningSinceElapsedRealtime = 0L
        isScrobbled = false
    }

    private fun resumeTimer() {
        val meta = currentMetadata ?: return
        if (isScrobbled || remainingTimeMs <= 0 || runningSinceElapsedRealtime != 0L) return

        runningSinceElapsedRealtime = SystemClock.elapsedRealtime()
        trackerJob = scope().launch {
            delay(remainingTimeMs)
            remainingTimeMs = 0L
            runningSinceElapsedRealtime = 0L
            isScrobbled = true
            scrobbleManager.onScrobble(meta, durationSeconds, startedAtSeconds)
        }
    }

    private fun pauseTimer() {
        if (runningSinceElapsedRealtime == 0L) return
        trackerJob?.cancel()
        trackerJob = null
        val elapsedSinceStart = SystemClock.elapsedRealtime() - runningSinceElapsedRealtime
        remainingTimeMs = (remainingTimeMs - elapsedSinceStart).coerceAtLeast(1L)
        runningSinceElapsedRealtime = 0L
    }

    companion object {
        const val MIN_SONG_DURATION_SECONDS = 30
        const val PLAYED_RATIO_THRESHOLD = 0.5f
        const val MAX_WAIT_DURATION_SECONDS = 240
    }
}
