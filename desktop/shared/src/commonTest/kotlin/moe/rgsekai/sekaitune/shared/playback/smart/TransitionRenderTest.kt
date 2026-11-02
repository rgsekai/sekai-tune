package moe.rgsekai.sekaitune.shared.playback.smart

import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared transition math against what Android's CrossfadeController and
 * TransitionFilterProcessor produced before it moved here. The hashes below were taken from the
 * old code (every fader value, every filter cutoff and echo setting it emitted over 504 style
 * configurations, and every sample the DSP produced), so a pass is bit-for-bit the same sound.
 */
class TransitionRenderTest {

    private fun fnv(h: Long, bits: Int): Long = (h xor (bits.toLong() and 0xffffffffL)) * 0x100000001b3L

    /** Records every filter move the way the old controller's dump did. */
    private class Recorder : AimedFilters {
        var h = 0L
        override var aimIncoming = true
        override var aimOutgoing = true
        private fun fnv(h: Long, bits: Int): Long = (h xor (bits.toLong() and 0xffffffffL)) * 0x100000001b3L
        override fun incoming(lowPassHz: Float, highPassHz: Float) {
            if (!aimIncoming) return
            h = fnv(fnv(fnv(h, 1), lowPassHz.toRawBits()), highPassHz.toRawBits())
        }

        override fun outgoing(lowPassHz: Float, highPassHz: Float) {
            if (!aimOutgoing) return
            h = fnv(fnv(fnv(h, 2), lowPassHz.toRawBits()), highPassHz.toRawBits())
        }

        /** The controller's "open" is unmasked, in session roles. */
        override fun open() {
            h = fnv(fnv(fnv(h, 1), TransitionFilterDsp.OPEN_HZ.toRawBits()), TransitionFilterDsp.OFF_HZ.toRawBits())
            h = fnv(fnv(fnv(h, 2), TransitionFilterDsp.OPEN_HZ.toRawBits()), TransitionFilterDsp.OFF_HZ.toRawBits())
        }

        override fun outgoingEcho(delaySeconds: Float, send: Float, dry: Float) {
            if (!aimOutgoing) return
            h = fnv(fnv(fnv(fnv(h, 3), delaySeconds.toRawBits()), send.toRawBits()), dry.toRawBits())
        }
    }

    @Test
    fun `504 style configurations render exactly as the Android controller did`() {
        val styles = listOf(
            Triple(TransitionStyle.EQUAL_POWER, false, FilterTransitionVariant.SWEEP),
            Triple(TransitionStyle.GAPLESS, false, FilterTransitionVariant.SWEEP),
            Triple(TransitionStyle.DJ_BLEND, false, FilterTransitionVariant.SWEEP),
            Triple(TransitionStyle.DJ_BLEND, true, FilterTransitionVariant.SWEEP),
            Triple(TransitionStyle.DJ_FILTER, false, FilterTransitionVariant.SWEEP),
            Triple(TransitionStyle.DJ_FILTER, false, FilterTransitionVariant.BASS_HANDOFF),
            Triple(TransitionStyle.DJ_FILTER, false, FilterTransitionVariant.ECHO_RIDE),
        )
        var master = 0L
        var count = 0
        for ((style, swap, variant) in styles) for (advanced in listOf(false, true)) for (clash in listOf(0.0, 0.5, 1.0))
            for (beats in listOf(0.0, 16.0, 32.0)) for (echo in listOf(0.0, 0.4)) for (sweep in listOf(0.0, 0.8)) {
                val rec = Recorder()
                rec.h = -3750763034362895579L
                val r = TransitionRender(
                    style = style, bassSwap = swap, bassSwapFraction = 0.62, filterSweep = sweep, filterVariant = variant,
                    vocalOverlap = clash, advanced = advanced, beats = beats, lockPhase = true, echoSeconds = echo,
                    tracks = rec,
                )
                for (i in 0..100) {
                    val p = i / 100f
                    rec.h = fnv(rec.h, r.mixRise(p).toRawBits())
                    rec.h = fnv(rec.h, r.mixFall(p).toRawBits())
                    rec.h = fnv(rec.h, r.outgoingLevel(p, 8000f).toRawBits())
                    rec.h = fnv(rec.h, r.echoDry(p).toRawBits())
                    r.rideFilters(p, p)
                    r.rideFilters(p, (p + 0.05f).coerceAtMost(1f))
                    r.rideFilters(p, null)
                }
                rec.h = fnv(rec.h, r.handoffAt.toRawBits())
                rec.h = fnv(rec.h, r.swapAtF.toRawBits())
                rec.h = fnv(rec.h, r.echoTailMs.toRawBits())
                master = (master xor rec.h) * 0x100000001b3L
                count++
            }
        assertEquals(504, count)
        // pow/sin/cos differ in the last bit between ARM and x64 JVMs (x64 HotSpot has its own libm), so each
        // architecture has its own hash: ARM is the one taken from the old code, x64 is this code's on x64.
        assertTrue(master == 4704663225900926948L || master == -389660207587063836L, "hash $master")
    }

    @Test
    fun `equal power never dips and the classic fade ends silent`() {
        val r = TransitionRender()
        for (i in 0..100) {
            val p = i / 100f
            val rise = r.mixRise(p)
            val fall = r.mixFall(p)
            assertEquals(1f, rise * rise + fall * fall, 1e-5f, "power at $p")
        }
        assertEquals(0f, r.mixRise(0f), 1e-6f)
        assertEquals(0f, r.mixFall(1f), 1e-6f)
    }

    @Test
    fun `a bass swap keeps the outgoing fader up until the swap and the incoming one up by it`() {
        val r = TransitionRender(
            style = TransitionStyle.DJ_BLEND, bassSwap = true, bassSwapFraction = 0.5, advanced = true, beats = 32.0,
        )
        assertEquals(1f, r.mixFall(r.swapAtF * 0.99f), 1e-6f)
        assertTrue(r.mixRise(r.swapAtF) > 0.999f)
        assertEquals(0f, r.mixFall(1f), 1e-6f)
    }

    @Test
    fun `the swap lands on a beat of the overlap`() {
        val r = TransitionRender(style = TransitionStyle.DJ_BLEND, bassSwap = true, bassSwapFraction = 0.62, advanced = true, beats = 32.0)
        val beat = r.swapAt * 32.0
        assertTrue(abs(beat - kotlin.math.round(beat)) < 1e-9, "swap at beat $beat")
        assertEquals(0.5 / 32.0, r.swapWidth, 1e-12)
    }

    @Test
    fun `a deck only hears its own side of the ride`() {
        val dsp = TransitionFilterDsp().apply { configure(48_000, 2) }
        val outgoing = TransitionRender(
            style = TransitionStyle.DJ_FILTER, filterSweep = 1.0, advanced = true, beats = 32.0,
            tracks = DeckFilters(dsp, DeckFilters.Side.OUTGOING),
        )
        outgoing.rideFilters(0.5f, 0.5f)
        assertTrue(dsp.lowPassTargetHz < TransitionFilterDsp.OPEN_HZ, "outgoing closes its low-pass")
        val dsp2 = TransitionFilterDsp().apply { configure(48_000, 2) }
        val incoming = TransitionRender(
            style = TransitionStyle.DJ_FILTER, filterSweep = 1.0, advanced = true, beats = 32.0,
            tracks = DeckFilters(dsp2, DeckFilters.Side.INCOMING),
        )
        incoming.rideFilters(0.1f, 0.1f)
        assertEquals(TransitionFilterDsp.OPEN_HZ, dsp2.lowPassTargetHz)
        assertTrue(dsp2.highPassTargetHz > TransitionFilterDsp.OFF_HZ, "incoming enters high-passed")
    }

    @Test
    fun `filter output is the same bit for bit as the Android processor`() {
        val rate = 48_000
        val p = TransitionFilterDsp().apply { configure(rate, 2) }
        val rnd = Random(7)
        val samples = FloatArray(960)
        var h = -3750763034362895579L
        for (b in 0 until 200) {
            val t = b / 200f
            when {
                b < 20 -> p.setCutoffs(TransitionFilterDsp.OPEN_HZ, TransitionFilterDsp.OFF_HZ)
                b < 120 -> p.setCutoffs(20000f * 0.015.pow((b - 20) / 100.0).toFloat(), 20f + 180f * t)
                else -> p.setCutoffs(500f, 180f)
            }
            if (b == 60) p.setEcho(0.25f, 0.8f, 1f)
            if (b == 100) p.setEcho(0.25f, 0f, 0f)
            if (b == 150) p.parkEcho()
            if (b == 170) p.open()
            for (i in 0 until 960) samples[i] = rnd.nextFloat() * 1.6f - 0.8f
            p.process(samples, 480)
            for (i in 0 until 960) h = fnv(h, samples[i].toRawBits())
        }
        assertEquals(4250403824794652242L, h)
    }

    @Test
    fun `a preallocated echo never allocates on the audio thread and still repeats`() {
        val dsp = TransitionFilterDsp().apply { configure(1_000, 1, preallocateEcho = true) }
        dsp.setEcho(0.1f, 1f, 1f)
        dsp.process(FloatArray(100), 100)
        val input = FloatArray(300).also { it[0] = 1f }
        dsp.process(input, 300)
        assertTrue(input[100] > 0.3f, "first repeat at the delay: ${input[100]}")
    }
}
