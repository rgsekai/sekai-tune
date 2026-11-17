package moe.rgsekai.sekaitune.ui.player

import android.view.animation.AnimationUtils
import androidx.compose.runtime.Stable
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.exp

// further apart than this (ms) and the clock jumps straight to the player, because that was a seek
private const val SnapMs = 250.0

// how fast a small gap to the player's own position is eased away
private const val PullTimeMs = 60.0

// a longer break between reads than this (ms) means nothing was watching, so start again from the player
private const val StaleMs = 200L

// a report is carried on at most this far (ms); the playback loop wakes at least every second, so past this the song really has stalled
private const val CarryMaxMs = 2000L

// the playback position for the frame on screen, so lyrics and the seek bar glide every frame instead of stepping with the 66ms poller
@Stable
class PlaybackClock(
    private val player: () -> Player?,
    private val offsetMs: () -> Long = { 0L },
) : () -> Long {
    private var frameMs = Long.MIN_VALUE
    private var smoothed = 0.0

    // the last position the player reported, and about when that was true
    private var reportedMs = Long.MIN_VALUE
    private var reportedAtMs = 0L

    override fun invoke(): Long {
        // choreographer pins this to the vsync while a frame runs, so every reader in a frame sees the same time
        val now = AnimationUtils.currentAnimationTimeMillis()
        if (now != frameMs) advance(now)
        return smoothed.toLong() + offsetMs()
    }

    private fun advance(now: Long) {
        val p = player() ?: return
        val playing = p.isPlaying
        val speed = p.playbackParameters.speed
        val fresh = frameMs == Long.MIN_VALUE || now - frameMs !in 1..StaleMs
        val elapsed = (now - frameMs).toDouble()
        frameMs = now
        val heard = carried(p.currentPosition.coerceAtLeast(0L), now, elapsed, fresh, playing, speed)
        if (fresh) {
            smoothed = heard
            return
        }
        // move on by the frame's own time, then ease out the little jitter in what the player reports
        val predicted = if (playing) smoothed + elapsed * speed else smoothed
        val gap = heard - predicted
        smoothed = if (abs(gap) > SnapMs) heard else predicted + gap * (1.0 - exp(-elapsed / PullTimeMs))
    }

    // the player's position only moves when its playback loop wakes, so it's carried on by the time since; held as is, the lyrics stalled and jumped with every wake
    private fun carried(reported: Long, now: Long, elapsed: Double, fresh: Boolean, playing: Boolean, speed: Float): Double {
        if (fresh || reported != reportedMs) {
            reportedMs = reported
            // it changed somewhere between the last frame and this one, so call it halfway
            reportedAtMs = if (fresh) now else now - (elapsed / 2.0).toLong()
        }
        if (!playing) {
            // paused, it holds still, and the carry starts over the moment it plays again
            reportedAtMs = now
            return reported.toDouble()
        }
        return reported + (now - reportedAtMs).coerceAtMost(CarryMaxMs) * speed.toDouble()
    }
}

