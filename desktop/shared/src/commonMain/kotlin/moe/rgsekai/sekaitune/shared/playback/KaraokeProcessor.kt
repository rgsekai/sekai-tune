package moe.rgsekai.sekaitune.shared.playback

import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Karaoke: takes the lead vocal out of a stereo mix, over interleaved Float32
 * samples, in place. One instance per sink, like [ReverbProcessor].
 *
 * ## How
 *
 * A lead vocal is mixed dead centre: the same signal, in phase, at the same
 * level in both channels. Most of the band around it is not — guitars, keys,
 * pads and the vocal's own stereo reverb sit off centre or decorrelated. So
 * per frequency bin of a short-time Fourier transform (Hann windows, 75%
 * overlap, about 40 ms long), the stage measures how alike left and right are,
 *
 *     ψ = 2·Re(L·R̄) / (|L|² + |R|²)
 *
 * smoothed over a few frames: 1 only when the bin is identical in both
 * channels, falling off with any difference in level or phase. Bins that
 * score near 1 are the centre, and the centre's spectrum, `(L + R) / 2`
 * masked to those bins, is resynthesised and subtracted from both channels.
 *
 * Only that estimate is ever resynthesised. The output is the input itself,
 * delayed, minus the centre: whatever the mask leaves alone — everything off
 * centre, and everything outside the vocal band — comes out sample for sample
 * as it went in, rather than through an analysis/synthesis round trip.
 *
 * The removal is confined to 120 Hz–14 kHz with soft edges. Below that sit the
 * kick and the bass, centred like the vocal but not what anyone is trying to
 * sing over; above it, the air.
 *
 * It is not a model of what a voice is, and it is not an equaliser: what it
 * removes is "what both speakers play identically", within the band a voice
 * occupies. So it also thins anything else mixed dead centre in that band
 * (snare, a centred lead instrument), cannot touch a vocal panned off centre
 * or double-tracked wide, and leaves its stereo reverb behind as a faint ghost.
 *
 * ## Mono
 *
 * In a mono signal everything is "centre", and this would remove the whole
 * band. So the stage watches the side-to-mid energy of what it is fed and,
 * once a second of audible signal has shown the channels to be (all but)
 * identical, glides its removal out and reports the track unsupported through
 * [onUnavailableChanged] rather than gutting it. One channel is unsupported
 * from the start.
 *
 * ## Staying out of the way
 *
 * Off and settled, [process] returns before reading a sample, as every stage
 * of the Android chain must for bit-exact playback. The transform costs
 * [latencyFrames] of delay, so switching on first passes the input through
 * while the buffers fill, then crossfades from the undelayed to the delayed
 * path over [GLIDE_SECONDS]; switching off crossfades back. Playback carries
 * on throughout — the price is that ~40 ms is heard twice on the way in and
 * skipped on the way out, under a crossfade. A seek does the same warm-up, so
 * it never plays a buffer's worth of silence.
 *
 * ## Channels
 *
 * The front left/right pair is analysed and processed. Further channels (a
 * 5.1 bed) are delayed alongside it so nothing drifts out of step, and
 * otherwise untouched.
 */
class KaraokeProcessor(
    /** Called on the audio thread when the input turns out (not) to be processable. */
    private val onUnavailableChanged: (Boolean) -> Unit = {},
    /** Called on the audio thread when the neural vocal takes over or lets go; see [isUsingStems]. */
    private val onNeuralChanged: (Boolean) -> Unit = {},
    /** How long bound-but-unready stems keep the vocal whole before the spectral estimate steps in; see [waiting]. */
    private val holdSeconds: Int = HOLD_SECONDS,
) {

    @Volatile
    private var targetEnabled = false

    /** Switches it on or off. From any thread; read on the next block. */
    fun setEnabled(enabled: Boolean) {
        targetEnabled = enabled
    }

    /** Whether the listener has it switched on. */
    val isEnabled: Boolean
        get() = targetEnabled

    @Volatile
    private var targetLevel = 1f

    /** The share of the vocal taken out, 0..1. From any thread; glides there, so a drag doesn't zipper. */
    fun setLevel(level: Float) {
        targetLevel = level.coerceIn(0f, 1f)
    }

    /** [targetLevel] as heard. */
    private var level = 1f

    /** Whether samples are being altered right now: on, or off with a crossfade still running. */
    @Volatile
    var isActive: Boolean = false
        private set

    /** Delay the transform adds while it is on, in frames; 0 before [configure]. */
    var latencyFrames = 0
        private set

    private var sampleRate = 0
    private var channelCount = 0

    private var size = 0
    private var hop = 0
    private var window = FloatArray(0)
    private var cosTable = FloatArray(0)
    private var sinTable = FloatArray(0)
    private var bitReverse = IntArray(0)

    /** The last [size] frames of the front pair, oldest first. */
    private var historyLeft = FloatArray(0)
    private var historyRight = FloatArray(0)
    private var pending = 0

    private var real = FloatArray(0)
    private var imag = FloatArray(0)

    /** Per-bin removal weight from the band edges, 0..1. */
    private var band = FloatArray(0)
    private var firstBin = 0
    private var lastBin = 0

    /** Smoothed auto and cross spectra, per bin. */
    private var powerLeft = FloatArray(0)
    private var powerRight = FloatArray(0)
    private var cross = FloatArray(0)
    private var smoothing = 0f

    /** Overlap-add of the centre estimate, and the finished hop being played out. */
    private var overlap = FloatArray(0)
    private var centre = FloatArray(0)

    /** The input, every channel, [latencyFrames] behind. */
    private var delay = FloatArray(0)
    private var delayAt = 0

    /** Frames fed since activation or a seek; the delayed path is valid from [latencyFrames]. */
    private var warm = 0

    private var glide = 0f

    /** Delayed-path share of the output: 0 passes the input through untouched. */
    private var wet = 0f

    /** How much of the centre is taken out, 0..1: glides to 0 on mono input. */
    private var depth = 0f

    private var sideEnergy = 0f
    private var midEnergy = 0f
    private var energySmoothing = 0f
    private var heardFrames = 0
    private var mono = false
    private var reportedUnavailable = false

    @Volatile
    private var boundStems: VocalStems? = null

    @Volatile
    private var bindCount = 0

    /** [bindCount] when the last stream boundary passed: that binding belongs to the track before. */
    private var staleBind = -1
    private var lastStems: VocalStems? = null

    /** The sink's frame count within the track, and how long it has run unbroken. */
    private var nextFrame = -1L
    private var contiguous = 0L

    /** Neural-stem share of the removal: 0 is all spectral, 1 all stem. */
    private var stemShare = 0f

    /**
     * Frames the bound stems have kept us waiting. The vocal is left whole
     * meanwhile — one clean switch when the model's vocal arrives beats a weak
     * removal and then a second step — unless the wait runs past [HOLD_SECONDS].
     */
    private var waiting = 0L
    private val stemBuffer = FloatArray(STEM_BLOCK_FRAMES * 2)

    /** Whether the vocal coming out is the neural model's rather than the spectral estimate. */
    @Volatile
    var isUsingStems: Boolean = false
        private set

    /**
     * The neural vocal for the track this sink is playing, or null where there
     * is none. Called whenever that track changes, including to itself (a
     * repeat): a binding made before a stream boundary is taken to be the old
     * track's until the next one arrives.
     */
    fun bind(stems: VocalStems?) {
        boundStems = stems
        bindCount++
    }

    /** Sizes the transform for [sampleRate]. Allocates; never call it from inside [process]. */
    fun configure(sampleRate: Int, channelCount: Int) {
        this.channelCount = channelCount
        if (sampleRate <= 0 || channelCount < 1) {
            this.sampleRate = 0
            latencyFrames = 0
            return
        }
        if (sampleRate != this.sampleRate) {
            this.sampleRate = sampleRate
            var n = MIN_SIZE
            while (n.toDouble() / sampleRate < WINDOW_SECONDS) n *= 2
            size = n
            hop = n / OVERLAP
            latencyFrames = n
            window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / n)).toFloat() }
            cosTable = FloatArray(n / 2) { cos(-2.0 * PI * it / n).toFloat() }
            sinTable = FloatArray(n / 2) { sin(-2.0 * PI * it / n).toFloat() }
            val bits = n.countTrailingZeroBits()
            bitReverse = IntArray(n) { index ->
                var reversed = 0
                for (bit in 0 until bits) if (index and (1 shl bit) != 0) reversed = reversed or (1 shl (bits - 1 - bit))
                reversed
            }
            historyLeft = FloatArray(n)
            historyRight = FloatArray(n)
            real = FloatArray(n)
            imag = FloatArray(n)
            overlap = FloatArray(n)
            centre = FloatArray(hop)
            val bins = n / 2 + 1
            band = FloatArray(bins) { bandWeight(it.toDouble() * sampleRate / n) }
            firstBin = band.indexOfFirst { it > 0f }.coerceAtLeast(1)
            lastBin = band.indexOfLast { it > 0f }.coerceAtMost(n / 2 - 1)
            powerLeft = FloatArray(bins)
            powerRight = FloatArray(bins)
            cross = FloatArray(bins)
            smoothing = exp(-hop.toDouble() / sampleRate / COHERENCE_SECONDS).toFloat()
            energySmoothing = exp(-hop.toDouble() / sampleRate / MONO_SECONDS).toFloat()
            glide = (1.0 - exp(-1.0 / (GLIDE_SECONDS * sampleRate))).toFloat()
        } else {
            latencyFrames = size
        }
        delay = FloatArray(size * channelCount)
        reset()
    }

    /**
     * Clears the transform — a seek or a new stream is not a continuous signal.
     * While on, the stage warms up again from an undelayed pass-through, so the
     * new position starts immediately rather than after a buffer of silence.
     */
    fun reset() {
        clearTransform()
        nextFrame = -1L
    }

    /**
     * Empties the transform but keeps the frame count, for switching on: the
     * sink has been counting all along, so stems readied while it was off are
     * still lined up.
     */
    private fun clearTransform() {
        historyLeft.fill(0f)
        historyRight.fill(0f)
        overlap.fill(0f)
        centre.fill(0f)
        powerLeft.fill(0f)
        powerRight.fill(0f)
        cross.fill(0f)
        delay.fill(0f)
        delayAt = 0
        pending = 0
        warm = 0
        wet = 0f
        forgetChannels()
    }

    /**
     * A new track has begun gaplessly: keep the audio flowing, but judge its
     * channels afresh — the last one being mono says nothing about this one —
     * and stop using the last track's stems until this one's are bound.
     */
    fun onStreamBoundary() {
        staleBind = bindCount
        nextFrame = -1L
        forgetChannels()
    }

    private fun forgetChannels() {
        sideEnergy = 0f
        midEnergy = 0f
        heardFrames = 0
        mono = false
        report(false)
    }

    /**
     * Processes the first [sampleCount] interleaved samples of [samples] in
     * place. [mediaTimeUs] is the first frame's position in the track, which
     * is what lines the neural stems up; without it only the spectral path runs.
     */
    fun process(samples: FloatArray, sampleCount: Int, mediaTimeUs: Long = TIME_UNKNOWN) {
        val channels = channelCount
        if (sampleRate <= 0 || channels < 1 || sampleCount < channels) return

        val enabled = targetEnabled
        if (channels < 2) {
            report(enabled)
            return
        }
        if (!isActive) {
            if (!enabled) {
                // Off, but stems may be getting ready for the switch: let them
                // follow along. Reads only — the samples go out untouched.
                readStems(samples, sampleCount, mediaTimeUs, watchOnly = true)
                return
            }
            isActive = true
            clearTransform()
            waiting = 0L
            depth = if (boundStems != null) 0f else 1f
        }

        val stemsReady = enabled && readStems(samples, sampleCount, mediaTimeUs)
        if (!stemsReady && stemShare > 0f) stemBuffer.fill(0f)
        val stemTarget = if (stemsReady) 1f else 0f
        val bound = boundStems != null && bindCount != staleBind
        waiting = if (stemsReady || !bound) 0L else waiting + sampleCount / channels
        val holding = bound && waiting < holdSeconds.toLong() * sampleRate
        val levelTarget = targetLevel

        val delaySize = delay.size
        var index = 0
        var frame = 0
        val end = sampleCount - channels + 1
        while (index < end) {
            val left = samples[index]
            val right = samples[index + 1]
            historyLeft[size - hop + pending] = left
            historyRight[size - hop + pending] = right

            val wetTarget = if (enabled && warm >= latencyFrames) 1f else 0f
            wet = if (abs(wetTarget - wet) < SETTLED) wetTarget else wet + glide * (wetTarget - wet)
            depth += glide * ((if (mono || holding) 0f else 1f) - depth)
            stemShare = if (abs(stemTarget - stemShare) < SETTLED) stemTarget else stemShare + glide * (stemTarget - stemShare)
            level += glide * (levelTarget - level)
            val spectral = centre[pending] * depth * (1f - stemShare)
            val removedLeft = (spectral + stemBuffer[2 * frame] * stemShare) * level
            val removedRight = (spectral + stemBuffer[2 * frame + 1] * stemShare) * level
            val dryShare = 1f - wet
            for (channel in 0 until channels) {
                val now = samples[index + channel]
                val delayed = delay[delayAt + channel]
                delay[delayAt + channel] = now
                val processed = when (channel) {
                    0 -> delayed - removedLeft
                    1 -> delayed - removedRight
                    else -> delayed
                }
                samples[index + channel] = now * dryShare + processed * wet
            }
            frame++
            delayAt += channels
            if (delayAt == delaySize) delayAt = 0
            if (warm < latencyFrames) warm++

            if (++pending == hop) {
                pending = 0
                frame()
            }
            index += channels
        }

        val neural = stemShare > 0.5f
        if (!enabled && wet == 0f) {
            isActive = false
            stemShare = 0f
            report(false)
            setNeural(false)
        } else {
            setNeural(neural)
        }
    }

    /**
     * Keeps the sink's frame count, feeds the bound stems what they need to
     * schedule and align, and reads this block's vocal — for the delayed path,
     * so [latencyFrames] behind — into [stemBuffer]. False when there is no
     * usable vocal for every frame of it.
     */
    private fun readStems(samples: FloatArray, sampleCount: Int, mediaTimeUs: Long, watchOnly: Boolean = false): Boolean {
        val stems = boundStems
        // A rate of 0 is stems still opening their decode: capture for them already.
        val stemsRate = stems?.sampleRate ?: 0
        if (stems == null || bindCount == staleBind || mediaTimeUs == TIME_UNKNOWN ||
            stemsRate != 0 && stemsRate != sampleRate
        ) {
            nextFrame = -1L
            return false
        }
        val frames = sampleCount / channelCount
        val anchor = mediaTimeUs * sampleRate / 1_000_000
        // Counted, not taken from each block's timestamp: container timestamps
        // are coarser than a sample. Re-anchored only on a real jump.
        if (stems !== lastStems || nextFrame < 0 || abs(anchor - nextFrame) > sampleRate / DISCONTINUITY_FRACTION) {
            lastStems = stems
            nextFrame = anchor
            contiguous = 0L
            stems.realign()
        }
        val blockFrame = nextFrame
        stems.observe(blockFrame)
        stems.capture(blockFrame, samples, sampleCount, channelCount)
        val ready = !watchOnly && stemsRate == sampleRate && contiguous >= latencyFrames &&
            frames <= STEM_BLOCK_FRAMES && stems.read(blockFrame - latencyFrames, frames, stemBuffer)
        nextFrame += frames
        contiguous += frames
        return ready
    }

    /** One hop: analyse the last [size] frames and add their centre into the overlap. */
    private fun frame() {
        watchForMono()

        val n = size
        for (i in 0 until n) {
            real[i] = historyLeft[i] * window[i]
            imag[i] = historyRight[i] * window[i]
        }
        fft(real, imag)

        // Left and right went in as one complex signal, z = l + i·r; pull their
        // spectra back apart, measure each bin, and leave the masked centre in
        // its place. Bins outside the band are zeroed.
        val keep = smoothing
        val fresh = 1f - keep
        for (k in 0..n / 2) {
            val mirror = (n - k) and (n - 1)
            if (k < firstBin || k > lastBin) {
                real[k] = 0f; imag[k] = 0f
                real[mirror] = 0f; imag[mirror] = 0f
                continue
            }
            val zr = real[k]
            val zi = imag[k]
            val mr = real[mirror]
            val mi = imag[mirror]
            val leftRe = 0.5f * (zr + mr)
            val leftIm = 0.5f * (zi - mi)
            val rightRe = 0.5f * (zi + mi)
            val rightIm = -0.5f * (zr - mr)

            val pl = keep * powerLeft[k] + fresh * (leftRe * leftRe + leftIm * leftIm)
            val pr = keep * powerRight[k] + fresh * (rightRe * rightRe + rightIm * rightIm)
            val lr = keep * cross[k] + fresh * (leftRe * rightRe + leftIm * rightIm)
            powerLeft[k] = pl
            powerRight[k] = pr
            cross[k] = lr

            val similarity = if (pl + pr > POWER_FLOOR) 2f * lr / (pl + pr) else 0f
            val gain = mask(similarity) * band[k] * 0.5f
            val cr = gain * (leftRe + rightRe)
            val ci = gain * (leftIm + rightIm)
            real[k] = cr
            imag[k] = ci
            real[mirror] = cr
            imag[mirror] = -ci
        }

        // Inverse by conjugation: ifft(x) = conj(fft(conj(x))) / n.
        for (i in 0 until n) imag[i] = -imag[i]
        fft(real, imag)

        val scale = SYNTHESIS_SCALE / n
        for (i in 0 until n) overlap[i] += real[i] * window[i] * scale
        overlap.copyInto(centre, 0, 0, hop)
        overlap.copyInto(overlap, 0, hop, n)
        overlap.fill(0f, n - hop, n)
        historyLeft.copyInto(historyLeft, 0, hop, n)
        historyRight.copyInto(historyRight, 0, hop, n)
    }

    /** Tracks side against mid energy over the hop just filled, and decides whether it is mono. */
    private fun watchForMono() {
        var side = 0f
        var mid = 0f
        for (i in size - hop until size) {
            val l = historyLeft[i]
            val r = historyRight[i]
            side += (l - r) * (l - r)
            mid += (l + r) * (l + r)
        }
        sideEnergy = energySmoothing * sideEnergy + (1f - energySmoothing) * side / hop
        midEnergy = energySmoothing * midEnergy + (1f - energySmoothing) * mid / hop
        if (heardFrames < sampleRate * MONO_SECONDS) {
            if (mid / hop > SILENCE) heardFrames += hop
            return
        }
        mono = if (mono) sideEnergy < midEnergy * STEREO_RATIO else sideEnergy < midEnergy * MONO_RATIO
        // With a neural model bound, mono is no obstacle: it separates a voice
        // from a mono mix as well as from a stereo one.
        report(mono && targetEnabled && boundStems == null)
    }

    private fun setNeural(neural: Boolean) {
        if (neural == isUsingStems) return
        isUsingStems = neural
        onNeuralChanged(neural)
    }

    private fun report(unavailable: Boolean) {
        if (unavailable == reportedUnavailable) return
        reportedUnavailable = unavailable
        onUnavailableChanged(unavailable)
    }

    /** In-place radix-2 FFT, forward. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = size
        for (i in 0 until n) {
            val j = bitReverse[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var length = 2
        while (length <= n) {
            val half = length / 2
            val stride = n / length
            var start = 0
            while (start < n) {
                for (k in 0 until half) {
                    val wr = cosTable[k * stride]
                    val wi = sinTable[k * stride]
                    val a = start + k
                    val b = a + half
                    val xr = re[b] * wr - im[b] * wi
                    val xi = re[b] * wi + im[b] * wr
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                }
                start += length
            }
            length *= 2
        }
    }

    companion object {
        /** [process]'s "no position": the spectral path alone. */
        const val TIME_UNKNOWN = Long.MIN_VALUE

        /** The largest block the stems are read for; a larger one runs spectral only. */
        private const val STEM_BLOCK_FRAMES = 16_384

        /** How long to wait for the neural vocal before the spectral removal stands in. */
        private const val HOLD_SECONDS = 8

        /** A jump in the sink's position bigger than 1/20 s is a seek, not timestamp rounding. */
        private const val DISCONTINUITY_FRACTION = 20

        /** Long enough to resolve a voice's harmonics; the window doubles from here to reach it. */
        private const val WINDOW_SECONDS = 0.04
        private const val MIN_SIZE = 256
        private const val OVERLAP = 4

        /** Hann analysis × Hann synthesis at 75% overlap sums to 1.5; this undoes it. */
        private const val SYNTHESIS_SCALE = 2f / 3f

        /** How long the per-bin spectra are averaged over before they are compared. */
        private const val COHERENCE_SECONDS = 0.03

        /** Similarity below which a bin is left alone, and at which it is fully removed. */
        private const val MASK_FLOOR = 0.75f
        private const val MASK_FULL = 0.96f

        private const val LOW_EDGE_HZ = 90.0
        private const val LOW_FULL_HZ = 160.0
        private const val HIGH_FULL_HZ = 8_000.0
        private const val HIGH_EDGE_HZ = 14_000.0

        /** Side/mid energy that counts as mono (-30 dB) and that brings it back (-25 dB). */
        private const val MONO_RATIO = 1e-3f
        private const val STEREO_RATIO = 3e-3f
        private const val MONO_SECONDS = 1.0
        private const val SILENCE = 1e-7f

        private const val POWER_FLOOR = 1e-12f
        private const val GLIDE_SECONDS = 0.02
        private const val SETTLED = 1e-4f

        /** Smoothstep from [MASK_FLOOR] to [MASK_FULL]: no hard edge for the musical noise to ride on. */
        private fun mask(similarity: Float): Float {
            val x = ((similarity - MASK_FLOOR) / (MASK_FULL - MASK_FLOOR)).coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }

        /** Raised-cosine shoulders at either end of the vocal band. */
        private fun bandWeight(hz: Double): Float = when {
            hz <= LOW_EDGE_HZ || hz >= HIGH_EDGE_HZ -> 0f
            hz < LOW_FULL_HZ -> (0.5 - 0.5 * cos(PI * (hz - LOW_EDGE_HZ) / (LOW_FULL_HZ - LOW_EDGE_HZ))).toFloat()
            hz > HIGH_FULL_HZ -> (0.5 + 0.5 * cos(PI * (hz - HIGH_FULL_HZ) / (HIGH_EDGE_HZ - HIGH_FULL_HZ))).toFloat()
            else -> 1f
        }
    }
}
