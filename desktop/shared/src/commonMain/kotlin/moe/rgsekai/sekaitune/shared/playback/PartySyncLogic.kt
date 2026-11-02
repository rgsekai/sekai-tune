package moe.rgsekai.sekaitune.shared.playback

import moe.rgsekai.sekaitune.shared.data.listentogether.PartyPlayback
import moe.rgsekai.sekaitune.shared.data.listentogether.PartyTrack
import moe.rgsekai.sekaitune.shared.data.model.QueueTier
import moe.rgsekai.sekaitune.shared.data.model.Song
import kotlin.math.abs

/**
 * Keeps one player on the party's playhead, by seeking: Android's `PartySync`, the desktop's and
 * iOS's all run the same one.
 *
 * A seek is not instant. Between asking for a position and audio coming out of it the player
 * rebuffers, and the party runs on meanwhile, so a device seeked to exactly where the party is
 * lands behind it by however long that took — 100ms on a warm cache, half a second on mobile data.
 * Checked once and then left to a loose drift limit, that lag stayed for the whole song. So every
 * seek here is checked after it settles: how far off it landed is learned as [seekLagMs] and added
 * to the next one, and a landing still outside [PartySyncLogic.ALIGN_TOLERANCE_MS] is tried again,
 * a bounded number of times, so a device that cannot land never seeks on a loop.
 */
class PartyDriftCorrector {
    private var control: List<Any?>? = null
    private var strikes = 0
    private var attempts = 0
    private var verifying = false
    private var holdUntilMs = 0L

    /** How far behind its target a seek on this device lands. Learned, and kept across tracks. */
    var seekLagMs = 0L
        private set

    /** A new party, or none: everything but the learned lag, which is this device's own. */
    fun reset() {
        control = null
        strikes = 0
        attempts = 0
        verifying = false
        holdUntilMs = 0L
    }

    /** The player is being started at the party's [wantMs]; where to put it. Checked once it settles. */
    fun startAt(wantMs: Long, target: PartyPlayback, nowMs: Long): Long {
        control = keyOf(target)
        attempts = 0
        strikes = 0
        return seek(wantMs, nowMs)
    }

    /** Where to seek the playing player, at [positionMs], to meet the party at [wantMs]; null to leave it. */
    fun correct(positionMs: Long, wantMs: Long, target: PartyPlayback, nowMs: Long): Long? {
        val drift = positionMs - wantMs
        // A control is a discontinuity everybody is meant to land on exactly. Only one that moved the
        // playhead counts: a playback seq bumped for anything else is not worth an audible seek.
        val key = keyOf(target)
        if (key != control) {
            control = key
            strikes = 0
            attempts = 0
            verifying = false
            return if (abs(drift) > PartySyncLogic.ALIGN_TOLERANCE_MS) seek(wantMs, nowMs) else null
        }
        if (nowMs < holdUntilMs) return null
        if (verifying) {
            verifying = false
            seekLagMs = (seekLagMs - drift).coerceIn(0L, PartySyncLogic.MAX_SEEK_LAG_MS)
            if (abs(drift) <= PartySyncLogic.ALIGN_TOLERANCE_MS) return null
            if (attempts < PartySyncLogic.MAX_ALIGN_ATTEMPTS) return seek(wantMs, nowMs)
            // Cannot land here right now. Leave it alone long enough to settle rather than seek forever.
            holdUntilMs = nowMs + PartySyncLogic.DRIFT_COOLDOWN_MS
            return null
        }
        if (abs(drift) <= PartySyncLogic.DRIFT_LIMIT_MS) {
            strikes = 0
            return null
        }
        // Twice in a row, so one reading taken while the pipeline caught up cannot cause a seek.
        if (++strikes < PartySyncLogic.DRIFT_STRIKES) return null
        strikes = 0
        attempts = 0
        return seek(wantMs, nowMs)
    }

    private fun seek(wantMs: Long, nowMs: Long): Long {
        attempts++
        verifying = true
        holdUntilMs = nowMs + PartySyncLogic.SEEK_SETTLE_MS
        return wantMs + seekLagMs
    }

    private fun keyOf(target: PartyPlayback): List<Any?> =
        listOf(target.track?.videoId, target.isPlaying, target.positionMs, target.anchorMs)
}

/** The rules a device follows to stay in time with a party, free of any player. */
object PartySyncLogic {
    /**
     * How far apart is a desync worth a seek between controls. Low, because a seek here lands where
     * it is aimed ([PartyDriftCorrector]); before it did, this had to sit at 1.2s to stop seeks
     * chasing their own rebuffer, and two devices a second apart are an echo, not a party.
     */
    const val DRIFT_LIMIT_MS = 250L

    /** Readings in a row before a correction fires, so one stall does not cause a seek. */
    const val DRIFT_STRIKES = 2
    const val DRIFT_COOLDOWN_MS = 6_000L

    /** How long a seek is given to rebuffer and start moving before where it landed is measured. */
    const val SEEK_SETTLE_MS = 1_500L

    /** Seeks to land one control or correction before giving up on it for [DRIFT_COOLDOWN_MS]. */
    const val MAX_ALIGN_ATTEMPTS = 3

    /** The most lead a seek is ever given: past this the device is stalling, not seeking. */
    const val MAX_SEEK_LAG_MS = 1_500L
    const val PAUSED_TOLERANCE_MS = 400L
    const val ALIGN_TOLERANCE_MS = 80L
    const val SEEK_REPORT_FLOOR_MS = 1_000L
    const val INTENT_QUIET_MS = 4_500L
    const val PUBLISH_DEBOUNCE_MS = 120L
    const val PUBLISH_WAIT_STEP_MS = 100L
    const val PUBLISH_WAIT_ATTEMPTS = 100
    const val RECONCILE_INTERVAL_MS = 700L
    const val MAX_PARTY_UPCOMING_QUEUE = 25

    /**
     * The playhead now, from a reading taken at [sampledAtNanos]. iOS and the desktop report it a few
     * times a second, so a raw reading is up to 250ms old — read as drift, that is a quarter second
     * of seek aimed at nothing.
     */
    fun livePositionMs(positionMs: Long, sampledAtNanos: Long, nowNanos: Long, playing: Boolean): Long =
        if (playing && sampledAtNanos > 0) positionMs + ((nowNanos - sampledAtNanos) / 1_000_000L).coerceIn(0L, 1_000L)
        else positionMs

    /** The queue a device shares with a party: the playing track onwards, the listener's own and AutoPlay's. */
    fun queueForPartyPublish(
        songs: List<Song>,
        currentIndex: Int,
        currentVideoId: String,
        currentDurationMs: Long,
    ): Pair<List<PartyTrack>, Int> {
        val raw = songs.withIndex().filter { (_, song) -> song.localPath == null && song.localUri == null }
        val rawCurrent = raw.indexOfFirst { (originalIndex, song) ->
            originalIndex == currentIndex && song.videoId == currentVideoId
        }.takeIf { it >= 0 } ?: raw.indexOfFirst { it.value.videoId == currentVideoId }

        val withoutContextTail = if (rawCurrent >= 0) {
            raw.take(rawCurrent + 1) + raw.drop(rawCurrent + 1).filter { it.value.queueTier != QueueTier.CONTEXT }
        } else {
            raw.filter { it.value.queueTier != QueueTier.CONTEXT }
        }
        val projectedCurrent = withoutContextTail.indexOfFirst { (originalIndex, song) ->
            originalIndex == currentIndex && song.videoId == currentVideoId
        }.takeIf { it >= 0 } ?: withoutContextTail.indexOfFirst { it.value.videoId == currentVideoId }
        val endExclusive = if (projectedCurrent >= 0) {
            (projectedCurrent + 1 + MAX_PARTY_UPCOMING_QUEUE).coerceAtMost(withoutContextTail.size)
        } else {
            (1 + MAX_PARTY_UPCOMING_QUEUE).coerceAtMost(withoutContextTail.size)
        }
        val tracks = withoutContextTail.take(endExclusive).map { (_, queued) ->
            queued.toPartyTrack(if (queued.videoId == currentVideoId) currentDurationMs else 0L)
        }
        return tracks to tracks.indexOfFirst { it.videoId == currentVideoId }
    }
}

/** This device's row as the party knows it: identity and artwork, not how the audio was got. */
fun Song.toPartyTrack(durationMs: Long): PartyTrack = PartyTrack(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs.takeIf { it > 0 },
    fromAutoplay = queueTier == QueueTier.AUTOPLAY,
)

/** A shared queue item as a catalogue song, in the section the phone puts it in. */
fun PartyTrack.toQueueSong(): Song = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    // Not cosmetic: a cross-source match is made on it.
    durationText = durationMs?.let { ms ->
        val total = ms / 1000
        "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    },
    queueTier = if (fromAutoplay) QueueTier.AUTOPLAY else QueueTier.USER_QUEUE,
)
