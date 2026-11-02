package moe.rgsekai.sekaitune.shared.playback.smart

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * The tempo half of a transition, shared by Android's CrossfadeController and iOS's AudioEngine:
 * the outgoing speed-up ramp, the beat-phase lock that nudges the incoming speed, the ease back
 * to the listener's own speed, and the beat grid they all read. Each class is plain state and
 * arithmetic over times it is handed, so the two platforms differ only in how they read a
 * player's clock and apply a speed.
 */

/**
 * The most the tempo moves in one beat, ramping up or easing back. Gradual drift below about 1% a
 * beat reads as the groove, not as the track changing speed.
 */
const val TEMPO_STEP_PER_BEAT = 0.0075

/** Close enough to the listener's own speed to call the ease done. */
const val EASE_DONE = 1e-4

/** A step every this long when the track has no beat to step on. */
const val EASE_FALLBACK_STEP_MS = 500L

/** Grace after the incoming track starts before its position is trusted for phase. */
const val LOCK_START_MS = 300L

/** Phase readings per decision; the median of these is what gets corrected. */
const val LOCK_SAMPLES = 5

/** Under this the two beats are together as far as anyone can hear. */
const val LOCK_DEADBAND_SECONDS = 0.008

/** After a nudge: long enough for the audio already buffered to play out at the new speed. */
const val LOCK_SETTLE_MS = 400L

/** After a reading in phase, how long before looking again. */
const val LOCK_IN_PHASE_MS = 500L

/**
 * How far a nudge moves the incoming speed. Inside the stretch the policy already calls
 * transparent, and over a pulse this short nobody hears the tempo move — just the beats pulling
 * together.
 */
const val NUDGE = 0.03f

/** Longest single nudge; anything left over is corrected on the next reading. */
const val MAX_NUDGE_MS = 1_500L

/** Past this much of the blend the outgoing track is too far gone for its beat to matter. */
const val LOCK_UNTIL = 0.9f

/**
 * More than this far apart — in beats of the shared pulse — is two grids disagreeing about where
 * the beat is, not a start that landed late, and nudging toward it would only make it worse.
 */
const val LOCK_MAX_BEATS = 0.3

/** The ceiling on a phase-lock nudge stacked on a speed-up. */
const val MAX_TOTAL_SPEED_UP = 1.12

/** Beats a tempo change of [rate] takes at [TEMPO_STEP_PER_BEAT]; 0 for none. */
fun rampStepsFor(rate: Double): Int {
    val stretch = abs(rate - 1.0)
    return if (stretch < EASE_DONE) 0 else ceil(stretch / TEMPO_STEP_PER_BEAT).toInt()
}

/**
 * Where a position falls in a track's beat: seconds past the last beat in [sinceBeat], that beat's
 * length in [beatSeconds]. Holds its answer between calls so reading it allocates nothing.
 */
class BeatPhase {
    var sinceBeat = 0.0
        private set
    var beatSeconds = 0.0
        private set

    /**
     * Where [positionSec] falls in [analysis]'s beat. False when the track has no grid at all.
     *
     * Beats are counted within the bar the analyzer's downbeats bound, not off one constant
     * interval from the first beat, so a track that drifts still reads its own local beat rather
     * than one that slid minutes ago.
     */
    fun at(analysis: TrackAnalysis, positionSec: Double): Boolean {
        // The tempo is the authority. A stored beat interval that disagrees with it by more than a
        // quarter is not a beat — a head-only analysis was seen carrying one of 24.7s on a 127 BPM
        // track, which stopped the scrubber's pulse dead for the rest of the blend.
        val fromTempo = if (analysis.bpm > 0.0) 60.0 / analysis.bpm else 0.0
        val interval = analysis.beatInterval
        val nominal = when {
            interval > 0.0 && (fromTempo <= 0.0 || abs(interval / fromTempo - 1.0) < 0.25) -> interval
            fromTempo > 0.0 -> fromTempo
            else -> return false
        }
        val downbeats = analysis.downbeats
        val next = downbeats.binarySearch(positionSec).let { if (it >= 0) it + 1 else -it - 1 }
        val origin: Double
        var beat = nominal
        if (next in 1 until downbeats.size) {
            origin = downbeats[next - 1]
            val bar = downbeats[next] - origin
            // A bar of one to eight beats is a bar, counted from the analyzer's own downbeats.
            // Anything longer is a stretch the analyzer found no downbeat in, and dividing it up
            // would invent a tempo.
            val beatsInBar = (bar / nominal).roundToInt()
            if (beatsInBar in 1..8) beat = bar / beatsInBar
        } else {
            origin = if (next >= 1) downbeats[next - 1] else analysis.firstBeat
        }
        if (beat <= 0.0 || !beat.isFinite()) return false
        sinceBeat = ((positionSec - origin) % beat + beat) % beat
        beatSeconds = beat
        return true
    }
}

/**
 * A player's position between the moments it actually reports one.
 *
 * Media3 1.11 schedules its playback loop dynamically: while the output buffer is full it sleeps,
 * and `getCurrentPosition` only moves when it wakes — about every 280ms, a step at a time.
 * Everything a blend times off a position was reading those steps, so each new report is taken as
 * having landed halfway through the tick it was seen in, and moved on from there at the player's
 * own speed until the next one. (iOS, whose deck clocks are continuous, reads them through this
 * too: the extrapolation is then a few milliseconds and keeps both platforms' phase readings on
 * one definition.)
 */
class PositionClock(private val halfTickNanos: Long = 15_000_000L) {
    private var player: Any? = null
    private var reported = 0L
    private var seenAt = 0L

    fun reset() {
        player = null
    }

    /** [p]'s position now, in ms, given its last report [position]. */
    fun read(p: Any, position: Long, isPlaying: Boolean, speed: Float, nowNanos: Long): Double {
        if (p !== player || position != reported) {
            seenAt = if (p === player) nowNanos - halfTickNanos else nowNanos
            player = p
            reported = position
        }
        if (!isPlaying) return position.toDouble()
        val since = (nowNanos - seenAt).coerceIn(0L, MAX_EXTRAPOLATION_NANOS)
        return position + since / 1e6 * speed
    }

    private companion object {
        /** Never run a stalled player's position on further than its loop could plausibly sleep. */
        const val MAX_EXTRAPOLATION_NANOS = 600_000_000L
    }
}

/**
 * Keeps the incoming track's beats on the outgoing track's, the way a DJ rides the jog wheel:
 * measure how far apart the two beats are, then run the incoming track a few percent fast or slow
 * for just long enough to close the gap.
 *
 * The incoming track starts on a tick plus however long its player takes to start, so it lands
 * anything up to tens of milliseconds off the beat — the "galloping" of a bad mix. Nothing
 * measured or corrected that before.
 *
 * Pulsed rather than continuous on purpose. A speed change is only heard once the audio already
 * buffered ahead has played out, so a controller that reacted to every reading would keep
 * correcting an error it had already fixed and oscillate. A pulse of known size and length moves
 * the incoming track by exactly the measured error however late it lands, and the lock then waits
 * [LOCK_SETTLE_MS] before measuring again. Readings are taken [LOCK_SAMPLES] at a time and the
 * median used, because a player's reported position jitters by about as much as the error being
 * fixed.
 */
class PhaseLock {
    private val beat = BeatPhase()

    /** When the speed nudge in flight ends, in the caller's ms clock; 0 when none is. */
    private var nudgeUntil = 0L

    /** No phase is measured before this: the players are settling after a start or a nudge. */
    private var lockQuietUntil = 0L

    /** Phase errors, in incoming seconds, gathered until there are enough to take a median of. */
    private val lockErrors = DoubleArray(LOCK_SAMPLES)
    private var lockCount = 0

    /** The last median the lock acted on or accepted, in incoming seconds, for logging. */
    var lastMedian = 0.0
        private set

    /** Whether a nudge is in flight. */
    val nudging: Boolean get() = nudgeUntil != 0L

    /** The blend has started at [nowMs]; the incoming player's clock is still settling. */
    fun start(nowMs: Long) {
        nudgeUntil = 0L
        lockCount = 0
        lockQuietUntil = nowMs + LOCK_START_MS
    }

    /** Forgets a nudge in flight, for a blend that ended; the caller restores the speed. */
    fun stop() {
        nudgeUntil = 0L
    }

    /**
     * How far the incoming beat sits ahead (positive) or behind the outgoing one, in
     * incoming-timeline seconds, or NaN when either has no grid or the two are too far apart for
     * this to be a phase error rather than a grid disagreement.
     *
     * Read in outgoing seconds so the two grids compare directly: the incoming one is divided by
     * the stretch ([mediaRatio], incoming speed over outgoing). Wrapped to the shorter of the two
     * beats, so a track counted at half or double time against the other still locks on the
     * shared pulse.
     */
    fun error(
        outgoing: TrackAnalysis,
        outgoingSec: Double,
        incoming: TrackAnalysis,
        incomingSec: Double,
        mediaRatio: Double,
    ): Double {
        if (!beat.at(outgoing, outgoingSec)) return Double.NaN
        val outSince = beat.sinceBeat
        val outBeat = beat.beatSeconds
        if (!beat.at(incoming, incomingSec)) return Double.NaN
        val rate = mediaRatio
        val period = minOf(outBeat, beat.beatSeconds / rate)
        var diff = (beat.sinceBeat / rate - outSince) % period
        if (diff > period / 2) diff -= period else if (diff < -period / 2) diff += period
        if (abs(diff) > period * LOCK_MAX_BEATS) return Double.NaN
        return diff * rate
    }

    /**
     * One look at the blend at [nowMs], with [errorSeconds] from [error]. Returns the speed to put
     * the incoming player on, or NaN to leave it where it is.
     *
     * [base] is the speed the incoming track is meant to be at (the listener's speed times its
     * beatmatch stretch), [incomingPlaybackRate] the stretch alone.
     */
    fun update(
        nowMs: Long,
        progress: Float,
        outPlaying: Boolean,
        inPlaying: Boolean,
        base: Float,
        incomingPlaybackRate: Double,
        errorSeconds: Double,
    ): Float {
        if (nudgeUntil != 0L) {
            // A pause mid-pulse ends it early; the next reading picks up the rest.
            if (nowMs < nudgeUntil && inPlaying) return Float.NaN
            nudgeUntil = 0L
            lockQuietUntil = nowMs + LOCK_SETTLE_MS
            lockCount = 0
            return base
        }
        // Once the outgoing track is on its way out its beat stops mattering.
        if (progress >= LOCK_UNTIL || nowMs < lockQuietUntil) return Float.NaN
        if (!outPlaying || !inPlaying) {
            lockCount = 0
            return Float.NaN
        }
        if (errorSeconds.isNaN()) return Float.NaN
        lockErrors[lockCount++] = errorSeconds
        if (lockCount < LOCK_SAMPLES) return Float.NaN
        lockCount = 0
        lockErrors.sort()
        val median = lockErrors[LOCK_SAMPLES / 2]
        lastMedian = median
        if (abs(median) < LOCK_DEADBAND_SECONDS) {
            lockQuietUntil = nowMs + LOCK_IN_PHASE_MS
            return Float.NaN
        }
        // Ahead slows down, behind speeds up; the pulse lasts as long as it takes that offset in
        // speed to cover the error. Speeding up is capped so a nudge stacked on a large stretch
        // never takes the incoming track past [MAX_TOTAL_SPEED_UP].
        val factor = if (median > 0) {
            1f - NUDGE
        } else {
            minOf(1f + NUDGE, (MAX_TOTAL_SPEED_UP / incomingPlaybackRate).toFloat())
        }
        if (factor <= 1f && median < 0) return Float.NaN
        nudgeUntil = nowMs + (abs(median) / (base * abs(factor - 1f)) * 1000).roundToLong().coerceIn(1L, MAX_NUDGE_MS)
        return base * factor
    }
}

/**
 * Brings the outgoing track up to its target rate over its last beats before the fade, one
 * [TEMPO_STEP_PER_BEAT] step per beat, so it arrives at the incoming track's tempo instead of the
 * incoming track being slowed to its own. Stepped off the track's position, so a tick landing
 * late only delays a step, never skips the tempo it reaches.
 */
class TempoRamp {
    var steps = 0
        private set
    var stepMs = 0L
        private set
    private var startMs = 0L
    var step = 0
        private set

    /** The ramp for an outgoing track that ends its blend at [endMs] after [fadeMs], one step per [beatMs]. */
    fun begin(outgoingRate: Double, beatMs: Long, endMs: Long, fadeMs: Long) {
        steps = if (beatMs > 0L) rampStepsFor(outgoingRate) else 0
        stepMs = beatMs
        startMs = endMs - fadeMs - steps * beatMs
        step = 0
    }

    fun clear() {
        steps = 0
    }

    /** Steps still to take. */
    val pending: Boolean get() = steps > 0 && step < steps

    /**
     * The stretch the outgoing track should be at once its playhead is at [positionMs], or NaN
     * when no new step has come due. Multiply by the listener's speed.
     */
    fun due(positionMs: Long, outgoingRate: Double): Double {
        val due = ((positionMs - startMs) / stepMs + 1).toInt().coerceIn(0, steps)
        if (due <= step) return Double.NaN
        step = due
        return 1.0 + (outgoingRate - 1.0) * due / steps
    }

    /** However the ramp went — a late arm, a slow buffer — the blend starts on the incoming tempo. */
    fun finish() {
        step = steps
    }
}

/**
 * Walks a player off a beatmatch stretch, one small step per beat of its own track, instead of
 * snapping it back to the listener's speed the moment the blend ends. Each step is at most
 * [TEMPO_STEP_PER_BEAT] — under what anyone hears as the tempo moving — so the length follows the
 * stretch: a 10% speed-up comes off over 14 beats, a 3% one over 4.
 */
class TempoEase {
    var rate = 1.0
        private set
    private var step = 0.0
    var stepMs = 0L
        private set
    private var nextAtMs = 0L

    /**
     * Starts easing off a stretch of [stretchRate] on a track playing [analysis], from
     * [positionMs]. False when the stretch is already nothing.
     */
    fun begin(stretchRate: Double, analysis: TrackAnalysis, positionMs: Long): Boolean {
        val stretch = stretchRate - 1.0
        if (abs(stretch) < EASE_DONE) return false
        val beat = when {
            analysis.beatInterval > 0.0 -> analysis.beatInterval
            analysis.bpm > 0.0 -> 60.0 / analysis.bpm
            else -> 0.0
        }
        val steps = rampStepsFor(stretchRate)
        step = stretch / steps
        rate = stretchRate
        stepMs = if (beat > 0.0) (beat * 1000).roundToLong() else EASE_FALLBACK_STEP_MS
        nextAtMs = positionMs + stepMs
        return true
    }

    /** The playhead is so far from where the ease expects it that something else moved the listener. */
    fun lost(positionMs: Long): Boolean = positionMs < nextAtMs - 2 * stepMs

    /**
     * One step, when the playhead has crossed the next beat: the new stretch, [DONE] when the
     * ease has arrived, or NaN when no step is due yet.
     */
    fun step(positionMs: Long): Double {
        if (positionMs < nextAtMs) return Double.NaN
        rate -= step
        if (abs(rate - 1.0) < EASE_DONE) return DONE
        nextAtMs += stepMs
        return rate
    }

    companion object {
        const val DONE = -1.0
    }
}
