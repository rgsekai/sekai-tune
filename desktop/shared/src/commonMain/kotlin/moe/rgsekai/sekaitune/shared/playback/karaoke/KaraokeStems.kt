package moe.rgsekai.sekaitune.shared.playback.karaoke

import moe.rgsekai.sekaitune.shared.platform.ConcurrentMap
import moe.rgsekai.sekaitune.shared.platform.nanoTime
import moe.rgsekai.sekaitune.shared.playback.Karaoke
import moe.rgsekai.sekaitune.shared.playback.VocalStems
import moe.rgsekai.sekaitune.shared.playback.smart.AnalysisLog
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import okio.FileHandle
import okio.FileSystem
import okio.Path
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicIntArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A forward decode of one track: planar stereo at the track's own rate, exactly
 * as many frames as the player's decoder produces. Supplied by each platform from
 * the bytes the player itself is reading.
 */
interface PcmStream : AutoCloseable {
    val sampleRate: Int

    /** Frames in the whole track as the container states it; -1 when it doesn't. */
    val durationFrames: Long

    /** The next chunk as `[left, right]`; null at the end. Throws when the bytes aren't there yet. */
    fun next(): Array<FloatArray>?

    /**
     * Moves to about [frame] — at or before it — and returns the frame the next
     * chunk starts at, or -1 where the stream can't seek. That frame need only
     * be consistent with the chunks after it: [TrackStems] lines each decode up
     * against what it already separated.
     */
    fun seek(frame: Long): Long = -1L
}

/**
 * Neural vocal separation for the song playing, for [KaraokeProcessor].
 *
 * ## The whole song, once
 *
 * MDX-Net looks at ~6 s at a time and takes a few seconds over it on a phone,
 * so it can't sit in the audio callback. One worker thread decodes the song on
 * its own — from the same bytes the player reads — and separates it in 5.75 s
 * segments: the next [AHEAD_SECONDS] first, then, while karaoke is on, the rest
 * of the song ahead, then whatever lies behind. Each segment goes to its own
 * slot in a cache file (16-bit vocal and mix), so once the song is done every
 * seek and lyric tap lands on audio already separated, and the heap holds none
 * of it. Karaoke goes off at every track change, and the file goes with the
 * song; with the lyrics merely open it only works [AHEAD_SECONDS] ahead.
 *
 * ## Lining up
 *
 * Subtracting a vocal only cancels it if both line up to the sample: one sample
 * off is already a 75° phase error at 10 kHz. Two alignments keep that true.
 *
 * - **Decode to decode.** The worker's decoder seeks — never decoding a song
 *   from its start to reach the middle — and a seek lands on a container
 *   timestamp, so each decode can count frames slightly differently. Every
 *   segment's region is read with room either side, and where a neighbour came
 *   from another decode their overlap is cross-correlated and the new decode
 *   slid onto the old one's frames. Segments sharing one frame count form an
 *   *island*; islands that meet are merged the same way.
 * - **Decode to sink.** [TrackStems.capture] hands over a moment of what the
 *   sink played; a thread of its own correlates it against the stored mix, no
 *   decode involved. After a seek, with the last offset to start from, 85 ms of
 *   capture and a narrow search do it.
 *
 * ## Segments
 *
 * Each segment is [MdxSeparator.CHUNK] frames at 44.1 kHz: the 5.75 s kept plus
 * the model's own context either side. The edges of adjacent segments overlap
 * by [CROSSFADE_FRAMES] under complementary sin²/cos² ramps, so a change in the
 * model's estimate from one chunk to the next fades rather than clicks.
 */
object KaraokeStems {

    private val live = ConcurrentMap<String, TrackStems>()

    /** Stems let go of, for the worker to release between jobs: it may be mid-decode on one. */
    private val retired = ArrayDeque<TrackStems>()

    /** Guards [retired], [started] and the check-then-add/remove on [live]. */
    private val guard = SynchronizedObject()
    private val wakeup = WakeSignal()
    private var started = false
    private var separator: MdxSeparator? = null
    private var idleSince = 0L
    private var separatorFailedAt = 0L

    /** Where the songs' separated audio is cached; see [install]. */
    @Volatile
    internal var directory: Path? = null
        private set

    /** What the worker is separating now, for the aligner to call off. */
    @Volatile
    private var running: Pair<TrackStems, Int>? = null

    /**
     * Set where the sink's clock is the decoder's own sample position (iOS's audio tap), so
     * the sink-to-stems offset can't change within a song: a seek then keeps the alignment
     * instead of measuring it again, and already-separated audio is used at once.
     */
    @Volatile
    var exactClock: Boolean = false

    /** How many threads inference may take; a few big cores, not the whole phone. */
    var inferenceThreads: Int = (cpuCount() / 2).coerceIn(2, 4)

    /** Where to cache separated songs. Anything a previous run left there is cleared. */
    fun install(directory: Path) {
        FileSystem.SYSTEM.createDirectories(directory)
        FileSystem.SYSTEM.listOrNull(directory)?.forEach { runCatching { FileSystem.SYSTEM.delete(it) } }
        this.directory = directory
    }

    /**
     * The stems for [key] — one rendition of the song [track] — decoding through
     * [open] when there is work to do.
     */
    fun forTrack(key: String, track: String, open: () -> PcmStream?): TrackStems {
        val stems = synchronized(guard) { live.getOrPut(key) { TrackStems(key, track, open) } }
        stems.lastSeen = nanoTime()
        start()
        return stems
    }

    /** The song playing is now [track]: every other song's stems, and their files, go. From any thread. */
    fun retainOnly(track: String?) {
        synchronized(guard) {
            for (stems in live.values) {
                if (stems.track == track) continue
                live.remove(stems.key)
                stems.retire()
                retired.addLast(stems)
            }
        }
        wake()
    }

    /** Something changed — karaoke on, a model ready, a new track — that may give the worker work. */
    fun wake() {
        wakeup.signal()
    }

    private fun start() {
        synchronized(guard) {
            if (started) return
            started = true
            startKaraokeThread("SekaiKaraoke", lowPriority = true, body = ::loop)
            startKaraokeThread("SekaiKaraokeAlign", lowPriority = false, body = ::alignLoop)
        }
    }

    private fun loop() {
        while (true) {
            while (true) (synchronized(guard) { retired.removeFirstOrNull() } ?: break).close()
            val job = runCatching { nextJob() }
                .onFailure { AnalysisLog.warn("Karaoke scheduling failed", it) }
                .getOrNull()
            if (job == null) {
                idle()
                wakeup.await(IDLE_WAIT_MS)
                continue
            }
            idleSince = 0L
            runCatching { job() }.onFailure { AnalysisLog.warn("Karaoke separation failed", it) }
            running = null
        }
    }

    /**
     * Lines stems up as soon as there is a capture and a segment under it, and
     * calls off an inference a seek has made pointless. Cheap, so it polls.
     */
    private fun alignLoop() {
        while (true) {
            runCatching {
                for (stems in live.values) if (stems.captured) stems.align()
                val (stems, index) = running ?: return@runCatching
                if (!stems.wants(index)) separator?.abort()
            }.onFailure { AnalysisLog.warn("Karaoke alignment failed", it) }
            sleepMillis(ALIGN_POLL_MS)
        }
    }

    /** Lets the model's memory go once karaoke has been off a while. */
    private fun idle() {
        val now = nanoTime()
        if (idleSince == 0L) idleSince = now
        if (now - idleSince > RELEASE_AFTER_NANOS && separator != null) {
            separator?.close()
            separator = null
        }
    }

    private fun nextJob(): (() -> Unit)? {
        val enabled = Karaoke.enabled.value
        if (!enabled && !Karaoke.warm.value) return null
        if (!KaraokeModel.installed) return null
        val now = nanoTime()
        if (separatorFailedAt != 0L && now - separatorFailedAt < RELEASE_AFTER_NANOS) return null
        var best: (() -> Unit)? = null
        var bestRank = Long.MAX_VALUE
        for (stems in live.values) {
            if (now - stems.lastSeen > ACTIVE_NANOS) continue
            if (stems.sampleRate == 0) {
                if (stems.openFailedAt != 0L && now - stems.openFailedAt < RETRY_NANOS) continue
                return { stems.open() }
            }
            val (index, rank) = stems.nextSegment(now, wholeSong = enabled) ?: continue
            if (rank < bestRank) {
                bestRank = rank
                best = {
                    val separator = separator()
                    running = stems to index
                    stems.separate(index, separator)
                }
            }
        }
        return best
    }

    private fun separator(): MdxSeparator {
        separator?.let { return it }
        return runCatching { MdxSeparator(KaraokeModel.path(), inferenceThreads, KaraokeModel.model) }
            .onFailure { separatorFailedAt = nanoTime() }
            .getOrThrow()
            .also { separator = it }
    }

    internal const val AHEAD_SECONDS = 30
    internal const val SEEK_PREROLL_SECONDS = 10
    internal const val CROSSFADE_FRAMES = 1_024
    private const val IDLE_WAIT_MS = 250L
    private const val ALIGN_POLL_MS = 20L
    private const val ACTIVE_NANOS = 15_000_000_000L
    internal const val RETRY_NANOS = 3_000_000_000L
    private const val RELEASE_AFTER_NANOS = 60_000_000_000L
}

/** One song's separated vocal. See [KaraokeStems]. */
class TrackStems internal constructor(
    val key: String,
    /** The song this is a rendition of; see [KaraokeStems.retainOnly]. */
    val track: String,
    open: () -> PcmStream?,
    private val model: MdxModel = KaraokeModel.model,
    private val directory: Path? = KaraokeStems.directory,
) : VocalStems, AutoCloseable {

    private val audio = TrackAudio(open)

    @Volatile
    override var sampleRate = 0
        private set

    @Volatile
    internal var lastSeen = nanoTime()

    @Volatile
    internal var openFailedAt = 0L

    /** Frames each segment keeps, at [sampleRate]; and the decode context either side, there and at 44.1 kHz. */
    @Volatile
    private var segmentFrames = 0
    private var context = 0
    private var context44 = 0
    private var segmentCount = 0

    /** Each segment slot's frames: the kept part and a crossfade either side. */
    private var span = 0

    @Volatile
    private var store: SegmentStore? = null

    /** Per segment, its island + 1; 0 while not separated. */
    @Volatile
    private var done = AtomicIntArray(0)
    private var nextIsland = 0
    private val failedAt = HashMap<Int, Long>()
    private var peak = 0f

    /** The decode the worker is reading, the island it feeds, and how far its frames sit from the island's. */
    private var session = -1
    private var sessionIsland = -1
    private var sessionShift = 0L

    /** The sink's position, in its own frames. */
    @Volatile
    private var playhead = -1L

    /** Island frame = sink frame + [offset], within [alignedIsland], once [aligned]. */
    @Volatile
    private var offset = 0L

    @Volatile
    private var aligned = false

    @Volatile
    private var alignedIsland = -1

    /** The offset before the last realign: where to look first. */
    @Volatile
    private var lastOffset = Long.MIN_VALUE

    // ---- alignment capture: written on the audio thread, read by the aligner once [captured] ----
    private val captureBuffer = FloatArray(CAPTURE_FRAMES)
    private var captureFrame = -1L
    private var captureFill = 0

    /** How much capture this alignment needs: little when [lastOffset] narrows the search. */
    @Volatile
    private var captureWanted = CAPTURE_FRAMES

    @Volatile
    internal var captured = false
        private set

    @Volatile
    private var captureEpoch = 0

    @Volatile
    private var restartCapture = false

    @Volatile
    private var closed = false

    // Audio-thread scratch for reading slots back.
    private var readBuffer = ByteArray(0)

    override fun observe(frame: Long) {
        playhead = frame
        lastSeen = nanoTime()
    }

    override fun realign() {
        if (KaraokeStems.exactClock && aligned) return
        if (aligned) lastOffset = offset
        captureWanted = if (lastOffset != Long.MIN_VALUE) FAST_CAPTURE_FRAMES else CAPTURE_FRAMES
        aligned = false
        restartCapture = true
        captureEpoch++
    }

    override fun capture(frame: Long, samples: FloatArray, sampleCount: Int, channels: Int) {
        if (aligned) return
        if (restartCapture) {
            restartCapture = false
            captured = false
            captureFill = 0
        }
        if (captured) return
        val wanted = captureWanted
        if (captureFill > 0 && frame != captureFrame + captureFill) captureFill = 0
        if (captureFill == 0) captureFrame = frame
        var index = 0
        while (index + channels <= sampleCount && captureFill < wanted) {
            val right = if (channels > 1) samples[index + 1] else samples[index]
            captureBuffer[captureFill++] = 0.5f * (samples[index] + right)
            index += channels
        }
        if (captureFill >= wanted) captured = true
    }

    override fun read(frame: Long, frames: Int, into: FloatArray): Boolean {
        val g = segmentFrames.toLong()
        val store = store
        if (!aligned || g == 0L || store == null || closed || into.size < frames * 2) return false
        val island = alignedIsland + 1
        val start = frame + offset
        val end = start + frames
        val fade = KaraokeStems.CROSSFADE_FRAMES
        val first = (start - fade).floorDiv(g).coerceAtLeast(0L)
        val last = (end - 1 + fade).floorDiv(g).coerceAtMost(segmentCount - 1L)
        if (first > last) return false
        for (k in first..last) if (done.loadAt(k.toInt()) != island) return false
        into.fill(0f, 0, frames * 2)
        for (k in first..last) {
            val slotStart = k * g - fade
            val from = max(start, slotStart)
            val to = min(end, slotStart + span)
            if (to <= from) continue
            val count = (to - from).toInt()
            val bytes = count * BYTES_PER_FRAME
            if (readBuffer.size < bytes) readBuffer = ByteArray(bytes * 2)
            if (!store.read(k.toInt(), (from - slotStart).toInt(), count, readBuffer)) return false
            val target = ((from - start) * 2).toInt()
            for (i in 0 until count) {
                into[target + 2 * i] += shortAt(readBuffer, 6 * i) / SCALE
                into[target + 2 * i + 1] += shortAt(readBuffer, 6 * i + 2) / SCALE
            }
        }
        return true
    }

    // ---- worker side ----

    internal fun open() {
        val directory = directory
        if (directory == null || !audio.open()) {
            openFailedAt = nanoTime()
            return
        }
        val rate = audio.sampleRate
        // Frames each chunk keeps at 44.1 kHz: the chunk less its context.
        val kept44 = MdxSeparator.CHUNK - 2 * model.trim
        // A context whose length is a whole number of samples at both rates, so the
        // 44.1 kHz chunk starts exactly on a native sample and the round trip lines up.
        val common = gcd(rate, MdxSeparator.SAMPLE_RATE)
        val native = rate / common
        val resampled = MdxSeparator.SAMPLE_RATE / common
        val multiple = (model.trim + RESAMPLER_MARGIN + resampled - 1) / resampled
        context = multiple * native
        context44 = multiple * resampled
        val g = (kept44 * rate.toLong() / MdxSeparator.SAMPLE_RATE).toInt()
        val frames = audio.durationFrames.takeIf { it > 0 } ?: (UNKNOWN_DURATION_SECONDS * rate.toLong())
        segmentCount = ((frames + g - 1) / g).toInt()
        span = g + 2 * KaraokeStems.CROSSFADE_FRAMES
        val opened = runCatching {
            SegmentStore(directory / "${key.hashCode().toUInt()}-${nanoTime()}.stems", span)
        }.onFailure { AnalysisLog.warn("Karaoke cache unavailable", it) }.getOrNull()
        if (opened == null || closed) {
            opened?.close()
            audio.close()
            openFailedAt = nanoTime()
            return
        }
        store = opened
        done = AtomicIntArray(segmentCount)
        segmentFrames = g
        sampleRate = rate
        openFailedAt = 0L
    }

    /** Where the playhead is in the island's frames — exactly once aligned, near enough before. */
    private fun position(): Long = max(0L, playhead) + when {
        aligned -> offset
        lastOffset != Long.MIN_VALUE -> lastOffset
        else -> 0L
    }

    /** The segments around the playhead that come first. */
    private fun window(): IntRange {
        val g = segmentFrames.toLong()
        val position = position()
        // From the segment whose fade-out the playhead may still be in.
        val first = (position - KaraokeStems.CROSSFADE_FRAMES).floorDiv(g)
            .coerceIn(0L, segmentCount - 1L).toInt()
        val last = (position + KaraokeStems.AHEAD_SECONDS * sampleRate.toLong()) / g
        return first..max(first.toLong(), min(segmentCount - 1L, last)).toInt()
    }

    /** Whether segment [index] is still worth finishing: near the playhead, or nothing near it is missing. */
    internal fun wants(index: Int): Boolean {
        if (playhead < 0 || segmentFrames == 0) return true
        val window = window()
        return index in window || window.all { done.loadAt(it) != 0 }
    }

    /**
     * The most urgent segment not yet separated, ranked for comparing tracks:
     * the window first by how far ahead it lies, then — for [wholeSong] — the
     * rest ahead, then what lies behind from its earliest gap, so each run reads
     * forward through one decode.
     */
    internal fun nextSegment(now: Long, wholeSong: Boolean): Pair<Int, Long>? {
        // Nothing until the sink says where it is: segment 0 is rarely where it is.
        if (playhead < 0 || closed) return null
        val g = segmentFrames.toLong()
        val position = position()
        fun usable(index: Int): Boolean {
            if (done.loadAt(index) != 0) return false
            val failed = failedAt[index] ?: return true
            return now - failed >= KaraokeStems.RETRY_NANOS
        }
        val window = window()
        for (index in window) if (usable(index)) return index to (index * g - position)
        if (!wholeSong) return null
        for (index in window.last + 1 until segmentCount) if (usable(index)) return index to WHOLE_SONG_RANK + index
        for (index in 0 until window.first) if (usable(index)) return index to 2 * WHOLE_SONG_RANK + index
        return null
    }

    internal fun separate(index: Int, separator: ChunkSeparator) {
        val store = store ?: return
        val started = nanoTime()
        val rate = sampleRate
        val g = segmentFrames
        val lag = ISLAND_LAG
        val regionStart = index.toLong() * g - context
        val regionFrames = g + 2 * context
        // The region, with room either side to slide it onto a neighbour's frames.
        val wideLeft = FloatArray(regionFrames + 2 * lag)
        val wideRight = FloatArray(regionFrames + 2 * lag)
        val preroll = KaraokeStems.SEEK_PREROLL_SECONDS * rate
        val sessionBefore = audio.session
        if (!audio.read(regionStart - lag + sessionShift, wideLeft, wideRight, preroll)) {
            failedAt[index] = nanoTime()
            return
        }
        if (audio.session != sessionBefore || audio.session != session) {
            // A new decode, of unknown standing until it meets a neighbour.
            session = audio.session
            sessionIsland = -1
            sessionShift = 0L
            if (!audio.read(regionStart - lag, wideLeft, wideRight, preroll)) {
                failedAt[index] = nanoTime()
                return
            }
            session = audio.session
        }
        var slide = 0
        if (sessionIsland < 0) {
            val neighbour = listOf(index - 1, index + 1).firstOrNull { it in 0 until segmentCount && done.loadAt(it) != 0 }
            if (neighbour == null) {
                sessionIsland = nextIsland++
            } else {
                slide = matchNeighbour(index, neighbour, wideLeft, wideRight, 0) ?: run {
                    failedAt[index] = nanoTime()
                    return
                }
                sessionShift += slide
                sessionIsland = done.loadAt(neighbour) - 1
            }
        }
        // A neighbour from another island: one look, and either they merge or one
        // of them goes to be separated again — never the one being listened to.
        for (neighbour in listOf(index - 1, index + 1)) {
            if (neighbour !in 0 until segmentCount) continue
            val island = done.loadAt(neighbour) - 1
            if (island < 0 || island == sessionIsland) continue
            val drift = matchNeighbour(index, neighbour, wideLeft, wideRight, slide)
            when {
                drift == 0 -> relabel(island, sessionIsland)
                drift != null && island == alignedIsland && aligned -> {
                    // Theirs is under the playhead: this decode moves onto it instead.
                    drop(sessionIsland)
                    slide += drift
                    sessionShift += drift
                    sessionIsland = island
                }
                else -> drop(island)
            }
        }

        val left = wideLeft.copyOfRange(lag + slide, lag + slide + regionFrames)
        val right = wideRight.copyOfRange(lag + slide, lag + slide + regionFrames)
        val decoded = nanoTime()
        val left44 = resample(left, rate, MdxSeparator.SAMPLE_RATE) ?: return
        val right44 = resample(right, rate, MdxSeparator.SAMPLE_RATE) ?: return

        val chunk = MdxSeparator.CHUNK
        val chunkStart = context44 - model.trim
        val inLeft = FloatArray(chunk)
        val inRight = FloatArray(chunk)
        val available = min(chunk, left44.size - chunkStart)
        left44.copyInto(inLeft, 0, chunkStart, chunkStart + available)
        right44.copyInto(inRight, 0, chunkStart, chunkStart + available)
        var chunkPeak = 0f
        for (i in 0 until chunk) chunkPeak = max(chunkPeak, max(abs(inLeft[i]), abs(inRight[i])))
        // UVR normalises the whole track to its peak before separating. The
        // whole track isn't known yet, so the loudest seen so far stands in.
        peak = max(peak, chunkPeak)
        val scale = max(peak, MIN_PEAK)
        for (i in 0 until chunk) {
            inLeft[i] /= scale
            inRight[i] /= scale
        }
        val vocalLeft = FloatArray(chunk)
        val vocalRight = FloatArray(chunk)
        try {
            separator.separate(inLeft, inRight, vocalLeft, vocalRight)
        } catch (aborted: MdxSeparator.Aborted) {
            // Called off by a seek; not a failure, so free to be asked for again.
            AnalysisLog.warn("karaoke $key segment $index called off after ${(nanoTime() - started) / 1_000_000} ms")
            return
        }
        val inferred = nanoTime()

        val gain = scale * model.compensate
        val placedLeft = FloatArray(left44.size)
        val placedRight = FloatArray(left44.size)
        for (i in 0 until available) {
            placedLeft[chunkStart + i] = vocalLeft[i] * gain
            placedRight[chunkStart + i] = vocalRight[i] * gain
        }
        val backLeft = resample(placedLeft, MdxSeparator.SAMPLE_RATE, rate) ?: return
        val backRight = resample(placedRight, MdxSeparator.SAMPLE_RATE, rate) ?: return

        val fade = KaraokeStems.CROSSFADE_FRAMES
        val slot = ByteArray(span * BYTES_PER_FRAME)
        for (n in 0 until span) {
            val source = context - fade + n
            val weight = when {
                n < 2 * fade && index > 0 -> rampIn(n, fade)
                n >= g -> 1f - rampIn(n - g, fade)
                else -> 1f
            }
            putShort(slot, n * BYTES_PER_FRAME, quantise(backLeft.getOrElse(source) { 0f } * weight))
            putShort(slot, n * BYTES_PER_FRAME + 2, quantise(backRight.getOrElse(source) { 0f } * weight))
            putShort(slot, n * BYTES_PER_FRAME + 4, quantise(0.5f * (left[source] + right[source])))
        }
        if (closed || !store.write(index, slot)) return
        done.storeAt(index, sessionIsland + 1)
        failedAt.remove(index)
        AnalysisLog.warn(
            "karaoke $key segment $index: decode ${(decoded - started) / 1_000_000} ms, " +
                "model ${(inferred - decoded) / 1_000_000} ms, total ${(nanoTime() - started) / 1_000_000} ms " +
                "for ${g * 1000L / rate} ms of audio",
        )
    }

    /**
     * How much further the wide region — read [ISLAND_LAG] early, already moved
     * by [slide] — has to slide to sit on [neighbour]'s frames, from where the
     * two overlap. Null when the overlap won't say.
     */
    private fun matchNeighbour(index: Int, neighbour: Int, left: FloatArray, right: FloatArray, slide: Int): Int? {
        val store = store ?: return null
        val g = segmentFrames.toLong()
        val fade = KaraokeStems.CROSSFADE_FRAMES
        val regionStart = index * g - context
        // The neighbour's kept frames that the region also covers.
        val from = max(neighbour * g, regionStart)
        val to = min((neighbour + 1) * g, regionStart + g + 2 * context)
        val length = (to - from).toInt()
        if (length < MIN_OVERLAP) return null
        val buffer = ByteArray(length * BYTES_PER_FRAME)
        if (!store.read(neighbour, (from - (neighbour * g - fade)).toInt(), length, buffer)) return null
        val reference = FloatArray(length) { shortAt(buffer, 6 * it + 4) / SCALE }
        val mono = FloatArray(left.size) { 0.5f * (left[it] + right[it]) }
        val base = (from - regionStart).toInt() + ISLAND_LAG + slide
        val (shift, score) = slideOnto(reference, mono, base, ISLAND_LAG)
        AnalysisLog.warn("karaoke $key segment $index meets $neighbour at $shift frames, correlation $score")
        return if (score < MIN_CORRELATION) null else shift
    }

    private fun relabel(from: Int, to: Int) {
        for (k in 0 until segmentCount) if (done.loadAt(k) == from + 1) done.storeAt(k, to + 1)
        if (alignedIsland == from) alignedIsland = to
    }

    private fun drop(island: Int) {
        AnalysisLog.warn("karaoke $key: island $island dropped to be separated again")
        for (k in 0 until segmentCount) if (done.loadAt(k) == island + 1) done.storeAt(k, 0)
        if (alignedIsland == island) realign()
    }

    // ---- aligner side ----

    /**
     * Measures [offset] from the capture: where among the separated segments the
     * sink's audio sits. With [lastOffset] in hand, a short capture searched
     * close to it; otherwise the full lag range. Waits, capture in hand, until
     * a segment covers it.
     */
    internal fun align() {
        if (aligned) {
            captured = false
            return
        }
        val store = store ?: return
        val g = segmentFrames.toLong()
        if (g == 0L) return
        val epoch = captureEpoch
        val frame = captureFrame
        val length = captureWanted
        val near = if (lastOffset != Long.MIN_VALUE && length == FAST_CAPTURE_FRAMES) lastOffset else 0L
        val lag = if (length == FAST_CAPTURE_FRAMES) FAST_LAG else MAX_LAG
        val start = frame + near - lag
        val end = frame + near + length + lag
        // Cheap look first: polled every few tens of milliseconds while it waits.
        val firstK = (max(0L, start) / g).toInt()
        val lastK = (end / g).toInt().coerceAtMost(segmentCount - 1)
        if (firstK > lastK) return
        val island = done.loadAt(firstK)
        if (island == 0) return
        for (k in firstK..lastK) if (done.loadAt(k) != island) return

        val reference = FloatArray((end - start).toInt())
        val fade = KaraokeStems.CROSSFADE_FRAMES
        val buffer = ByteArray(span * BYTES_PER_FRAME)
        for (k in firstK..lastK) {
            // The kept part only: the faded edges belong to the neighbours as well.
            val from = max(start, k * g)
            val to = min(end, (k + 1) * g)
            if (to <= from) continue
            val count = (to - from).toInt()
            if (!store.read(k, (from - (k * g - fade)).toInt(), count, buffer)) return
            for (i in 0 until count) reference[(from - start).toInt() + i] = shortAt(buffer, 6 * i + 4) / SCALE
        }
        val snapshot = captureBuffer.copyOf(length)
        // Handed back either way from here: a success needs no more, a failure a fresh one.
        captured = false
        var energy = 0.0
        for (value in snapshot) energy += value * value
        if (energy / snapshot.size < SILENT_ENERGY) {
            restartCapture = true
            return
        }
        val (shift, score) = slideOnto(snapshot, reference, lag, lag)
        if (epoch != captureEpoch) return
        AnalysisLog.warn(
            "karaoke $key aligned at ${near + shift} frames, correlation $score" +
                if (length == FAST_CAPTURE_FRAMES) " (fast)" else "",
        )
        if (score < MIN_CORRELATION) {
            // The quick look missed: the full one next time.
            lastOffset = Long.MIN_VALUE
            captureWanted = CAPTURE_FRAMES
            restartCapture = true
            return
        }
        offset = near + shift
        alignedIsland = island - 1
        aligned = true
    }

    /**
     * The shift in ±[lag] at which [fixed] best matches [moving] read from
     * `base + shift`, and how well: coarse over the opening quarter, confirmed
     * over all of it.
     */
    private fun slideOnto(fixed: FloatArray, moving: FloatArray, base: Int, lag: Int): Pair<Int, Double> {
        fun correlation(shift: Int, length: Int): Double {
            var dot = 0.0
            var power = 0.0
            var own = 0.0
            for (i in 0 until length) {
                val r = moving.getOrElse(base + shift + i) { 0f }
                dot += fixed[i] * r
                power += r * r
                own += fixed[i] * fixed[i]
            }
            return if (power <= 0.0 || own <= 0.0) 0.0 else dot / sqrt(power * own)
        }
        var bestShift = 0
        var bestScore = -1.0
        val coarse = min(fixed.size, max(fixed.size / 4, 2_048))
        for (shift in -lag..lag) {
            val score = correlation(shift, coarse)
            if (score > bestScore) {
                bestScore = score
                bestShift = shift
            }
        }
        return bestShift to correlation(bestShift, fixed.size)
    }

    private fun resample(samples: FloatArray, from: Int, to: Int): FloatArray? =
        if (from == to) samples else resampleAudio(samples, from, to)

    /** Stops answering at once; [close] — on the worker — lets the decoder and the file go. */
    internal fun retire() {
        closed = true
        aligned = false
    }

    override fun close() {
        retire()
        audio.close()
        store?.close()
        store = null
    }

    private companion object {
        /** Room for the resampler's kernel at the region's edges. */
        const val RESAMPLER_MARGIN = 64

        const val CAPTURE_FRAMES = 16_384

        /** ~85 ms at 48 kHz: enough to confirm an offset already known to within [FAST_LAG]. */
        const val FAST_CAPTURE_FRAMES = 4_096

        /** ±100 ms at 48 kHz: a codec's priming, a seek anchor's rounding. */
        const val MAX_LAG = 4_800

        /** What a seek can move the sink's count by when the offset was known before it. */
        const val FAST_LAG = 256

        /** How far two of the worker's decodes can disagree: a codec's priming at most. */
        const val ISLAND_LAG = 2_400
        const val MIN_OVERLAP = 2_048

        const val MIN_CORRELATION = 0.9
        const val SILENT_ENERGY = 1e-6
        const val MIN_PEAK = 1e-3f

        /** Vocal left, vocal right, mono mix: 16-bit each. */
        const val BYTES_PER_FRAME = 6
        const val SCALE = 32_767f

        /** Slots set aside for a stream that doesn't say how long it is. */
        const val UNKNOWN_DURATION_SECONDS = 900L

        /** Ranks the whole-song passes after anything near the playhead. */
        const val WHOLE_SONG_RANK = 1L shl 40

        /** Little-endian 16-bit, as the cache slots have always been laid out. */
        fun shortAt(bytes: ByteArray, at: Int): Short =
            ((bytes[at + 1].toInt() shl 8) or (bytes[at].toInt() and 0xFF)).toShort()

        fun putShort(bytes: ByteArray, at: Int, value: Short) {
            bytes[at] = value.toByte()
            bytes[at + 1] = (value.toInt() shr 8).toByte()
        }

        fun quantise(value: Float): Short = (value * SCALE).roundToInt().coerceIn(-32_768, 32_767).toShort()

        fun rampIn(n: Int, fade: Int): Float {
            val s = sin(PI / 2 * (n + 0.5) / (2 * fade))
            return (s * s).toFloat()
        }

        tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    }
}

/**
 * One song's segment slots on disk, a fixed number of frames each: written once
 * by the worker, read by the sink and the aligner. Positional reads and writes,
 * so the threads never share a file position.
 */
private class SegmentStore(private val file: Path, span: Int) : AutoCloseable {
    private val handle: FileHandle = FileSystem.SYSTEM.openReadWrite(file)
    private val slotBytes = span.toLong() * 6

    fun write(index: Int, data: ByteArray): Boolean = runCatching {
        handle.write(index * slotBytes, data, 0, data.size)
        true
    }.getOrDefault(false)

    /** Frames [from, from+count) of slot [index] into the start of [into]. */
    fun read(index: Int, from: Int, count: Int, into: ByteArray): Boolean = runCatching {
        val length = count * 6
        var at = index * slotBytes + from.toLong() * 6
        var filled = 0
        while (filled < length) {
            val read = handle.read(at, into, filled, length - filled)
            if (read < 0) return@runCatching false
            filled += read
            at += read
        }
        true
    }.getOrDefault(false)

    override fun close() {
        runCatching { handle.close() }
        runCatching { FileSystem.SYSTEM.delete(file) }
    }
}

/**
 * Random access, mostly forward, over a [PcmStream]: keeps what it decoded since
 * the last read's start and decodes on from there. A read far behind or ahead of
 * that seeks the stream instead, which starts a new [session].
 */
internal class TrackAudio(private val factory: () -> PcmStream?) : AutoCloseable {

    private var stream: PcmStream? = null
    var sampleRate = 0
        private set
    var durationFrames = -1L
        private set

    /** Bumped by every restart or seek: frames from different sessions may not line up. */
    var session = 0
        private set

    private var left = FloatArray(0)
    private var right = FloatArray(0)
    private var bufferStart = 0L
    private var bufferFrames = 0
    private var ended = false

    fun open(): Boolean {
        restart()
        return stream != null
    }

    private fun restart() {
        runCatching { stream?.close() }
        stream = runCatching { factory() }.getOrNull()
        sampleRate = stream?.sampleRate ?: 0
        durationFrames = stream?.durationFrames ?: -1L
        bufferStart = 0L
        bufferFrames = 0
        ended = false
        session++
    }

    /**
     * Moves the decode to [preroll] frames before [from]: far enough back that
     * the region's context, and the segment before, come from the same session.
     */
    private fun seekNear(from: Long, preroll: Int) {
        val target = max(0L, from - preroll)
        val source = stream
        val landed = if (target == 0L || source == null) -1L else runCatching { source.seek(target) }.getOrDefault(-1L)
        if (landed < 0 || landed > from) {
            restart()
            return
        }
        bufferStart = landed
        bufferFrames = 0
        ended = false
        session++
    }

    /**
     * Frames from [start] into [outLeft]/[outRight]; zeros outside the track.
     * False when not decodable yet.
     */
    fun read(start: Long, outLeft: FloatArray, outRight: FloatArray, preroll: Int = 0): Boolean {
        val count = outLeft.size
        val end = start + count
        val from = max(start, 0L)
        if (stream == null) restart()
        if (from < bufferStart || from > bufferStart + bufferFrames + max(preroll, MIN_JUMP_FRAMES)) {
            seekNear(from, preroll)
        }
        val source = stream ?: return false
        while (!ended && bufferStart + bufferFrames < end) {
            val chunk = try {
                source.next()
            } catch (error: Exception) {
                // Not downloaded that far yet: start over next time.
                runCatching { source.close() }
                stream = null
                return false
            }
            if (chunk == null) {
                val decoded = bufferStart + bufferFrames
                if (durationFrames > 0 && decoded < durationFrames * 98 / 100) {
                    // Ended early: the bytes behind it aren't there yet.
                    runCatching { source.close() }
                    stream = null
                    return false
                }
                ended = true
                break
            }
            append(chunk[0], chunk[1], keepFrom = from)
        }
        for (i in 0 until count) {
            val at = start + i - bufferStart
            if (at in 0 until bufferFrames) {
                outLeft[i] = left[at.toInt()]
                outRight[i] = right[at.toInt()]
            } else {
                outLeft[i] = 0f
                outRight[i] = 0f
            }
        }
        return true
    }

    /** Appends a chunk, first dropping everything before [keepFrom]. */
    private fun append(chunkLeft: FloatArray, chunkRight: FloatArray, keepFrom: Long) {
        val drop = (keepFrom - bufferStart).coerceIn(0L, bufferFrames.toLong()).toInt()
        if (drop > 0) {
            left.copyInto(left, 0, drop, bufferFrames)
            right.copyInto(right, 0, drop, bufferFrames)
            bufferFrames -= drop
            bufferStart += drop
        }
        val needed = bufferFrames + chunkLeft.size
        if (needed > left.size) {
            val capacity = max(needed, left.size * 3 / 2 + 4_096)
            left = left.copyOf(capacity)
            right = right.copyOf(capacity)
        }
        chunkLeft.copyInto(left, bufferFrames)
        chunkRight.copyInto(right, bufferFrames)
        bufferFrames = needed
    }

    override fun close() {
        runCatching { stream?.close() }
        stream = null
    }

    private companion object {
        /** A gap ahead worth seeking over rather than decoding through. */
        const val MIN_JUMP_FRAMES = 480_000
    }
}
