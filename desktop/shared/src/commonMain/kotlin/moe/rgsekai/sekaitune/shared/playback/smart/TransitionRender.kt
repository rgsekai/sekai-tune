package moe.rgsekai.sekaitune.shared.playback.smart

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/**
 * The two filters a transition rides: one over the track arriving, one over the track leaving.
 *
 * An interface rather than the processors themselves so the renderer stays testable without an
 * audio sink, and so it never has to know that "incoming" and "outgoing" are two different
 * players whose roles swap at the lap.
 */
interface TransitionFilters {
    /** The track fading up. */
    fun incoming(lowPassHz: Float, highPassHz: Float)

    /** The track fading out. */
    fun outgoing(lowPassHz: Float, highPassHz: Float)

    /**
     * How far ahead of the speaker each side's filter is running, in that track's media
     * milliseconds. Zero, the default, means "applied as it is heard".
     */
    fun incomingLeadMs(): Long = 0L
    fun outgoingLeadMs(): Long = 0L

    /** Echo out on each side's sink. */
    fun incomingEcho(delaySeconds: Float, send: Float, dry: Float) = Unit
    fun outgoingEcho(delaySeconds: Float, send: Float, dry: Float) = Unit

    /** Drops any echo on either sink. Called whenever a transition ends, however it ended. */
    fun parkEchoes() = Unit

    /** Parks both. Called whenever a transition ends, however it ended. */
    fun open() {
        incoming(TransitionFilterDsp.OPEN_HZ, TransitionFilterDsp.OFF_HZ)
        outgoing(TransitionFilterDsp.OPEN_HZ, TransitionFilterDsp.OFF_HZ)
    }

    /** For callers with no audio sink to filter — tests, and the default wiring. */
    object None : TransitionFilters {
        override fun incoming(lowPassHz: Float, highPassHz: Float) = Unit
        override fun outgoing(lowPassHz: Float, highPassHz: Float) = Unit
    }
}

/**
 * [TransitionFilters] with a mask on which side a ride pass may aim: both, unless
 * [TransitionRender.rideFilters] is evaluating the style at two different points in the blend —
 * one per side, because each side's filter runs its own distance ahead of the speaker.
 */
interface AimedFilters : TransitionFilters {
    var aimIncoming: Boolean
    var aimOutgoing: Boolean

    object None : AimedFilters, TransitionFilters by TransitionFilters.None {
        override var aimIncoming = true
        override var aimOutgoing = true
    }
}

/**
 * One deck's end of a transition, for a player that filters each track in its own audio path
 * (iOS): only the calls addressed to [side] reach [dsp], the other side's are somebody else's.
 */
class DeckFilters(var dsp: TransitionFilterDsp?, private val side: Side) : AimedFilters {
    enum class Side { INCOMING, OUTGOING }

    override var aimIncoming = true
    override var aimOutgoing = true

    override fun incoming(lowPassHz: Float, highPassHz: Float) {
        if (aimIncoming && side == Side.INCOMING) dsp?.setCutoffs(lowPassHz, highPassHz)
    }

    override fun outgoing(lowPassHz: Float, highPassHz: Float) {
        if (aimOutgoing && side == Side.OUTGOING) dsp?.setCutoffs(lowPassHz, highPassHz)
    }

    override fun outgoingEcho(delaySeconds: Float, send: Float, dry: Float) {
        if (aimOutgoing && side == Side.OUTGOING) dsp?.setEcho(delaySeconds, send, dry)
    }

    override fun open() {
        dsp?.open()
    }

    override fun parkEchoes() {
        dsp?.parkEcho()
    }
}

/** Equal-power pair: [riseGain]² + [fallGain]² = 1, so a blend of two tracks never dips. */
fun riseGain(progress: Float): Float =
    sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

fun fallGain(progress: Float): Float =
    cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

/** See Android's `onBlendHeadroom`: `1 / sqrt(incomingGain + outgoingGain)` once the pair sums past unity. */
fun headroomFor(incomingGain: Float, outgoingGain: Float): Float {
    val sum = incomingGain + outgoingGain
    return if (sum <= 1f) 1f else 1f / kotlin.math.sqrt(sum)
}

/**
 * The style-specific half of a transition plan — everything the ride needs and nothing else —
 * and the curves it renders: the fader pair, the filter cutoffs of each side, the echo out.
 * Fixed when the transition begins, because a plan is recomputed every tick and a bass swap that
 * moved to a different beat halfway through the blend would be heard as the low end flapping.
 *
 * Every fraction the ride reads is resolved here, once per transition. With [advanced] off they
 * come out as the classic constants; with it on and a trusted grid, each is moved onto the
 * nearest beat or bar of the overlap, so the bass swap, the filters opening and the echo all land
 * where a DJ would put them.
 *
 * Shared by Android's CrossfadeController and iOS's per-deck tap, so a blend sounds the same on
 * both. Nothing here allocates once constructed.
 */
class TransitionRender(
    val style: TransitionStyle = TransitionStyle.EQUAL_POWER,
    val bassSwap: Boolean = false,
    bassSwapFraction: Double = 0.7,
    val filterSweep: Double = 0.0,
    val filterVariant: FilterTransitionVariant = FilterTransitionVariant.SWEEP,
    val vocalOverlap: Double = 0.0,
    /** Advanced Automix: DJ fader curves, moves on the beat, phase lock, tempo ease-back. */
    val advanced: Boolean = false,
    /** The overlap's length in outgoing beats; 0 when the grid is not trusted. */
    beats: Double = 0.0,
    lockPhase: Boolean = false,
    /** The plan's echo time; 0 for no echo out. */
    echoSeconds: Double = 0.0,
    /** Where the filter moves go. */
    private val tracks: AimedFilters = AimedFilters.None,
) {
    private val grid = if (advanced && beats >= 2.0) beats else 0.0
    val clash = vocalOverlap.coerceIn(0.0, 1.0)

    /** Echo the outgoing track out rather than fading or filtering it away. Needs the grid it repeats on. */
    val echo = grid > 0 && echoSeconds > 0.0
    val echoSecondsF = echoSeconds.toFloat()

    /** How long the repeats ring after the dry signal goes, in ms. */
    val echoTailMs = (echoTailSeconds(echoSeconds) * 1000).toFloat()

    /** Hold the two beats in phase by nudging the incoming track's speed. */
    val phaseLock = advanced && lockPhase

    val swapAt = onBeat(bassSwapFraction.coerceIn(0.05, 0.95))

    /** The low end changes hands over half a beat that ends on [swapAt]; classically, centred on it. */
    val swapWidth = if (grid > 0) SWAP_BEATS / grid else 2 * BASS_SWAP_WIDTH
    val swapFrom = if (grid > 0) swapAt - swapWidth else swapAt - BASS_SWAP_WIDTH
    val entryOpenBy = onBar(
        when (filterVariant) {
            FilterTransitionVariant.SWEEP -> ENTRY_OPEN_BY
            FilterTransitionVariant.BASS_HANDOFF -> 0.38
            FilterTransitionVariant.ECHO_RIDE -> 0.52
        },
    )
    val vocalEntryOpenBy = onBar(entryOpenBy + (VOCAL_SAFE_ENTRY_OPEN_BY - entryOpenBy) * clash)
    val blendEntryOpenBy = onBar(BLEND_ENTRY_OPEN_BY + (BLEND_ENTRY_CLASH_OPEN_BY - BLEND_ENTRY_OPEN_BY) * clash)
    val blendExitFrom = onBar(BLEND_EXIT_FROM + (BLEND_EXIT_CLASH_FROM - BLEND_EXIT_FROM) * clash)
    val filterSwapAt = onBar(
        when (filterVariant) {
            FilterTransitionVariant.SWEEP -> FILTER_BASS_SWAP_AT
            FilterTransitionVariant.BASS_HANDOFF -> 0.43
            FilterTransitionVariant.ECHO_RIDE -> 0.58
        },
    )
    val filterSwapAtF = filterSwapAt.toFloat()


    /**
     * Where the dry outgoing signal is killed and only its echo goes on:
     * the filter ride's bass handover. The send opens
     * across the beat before it, so what repeats is that one beat — the
     * DJ move of hitting echo on the last beat and pulling the fader.
     */
    val echoAt = onBar(FILTER_ECHO_AT)
    val echoFrom = if (grid > 0) echoAt - 1.0 / grid else echoAt
    val echoKill = if (grid > 0) ECHO_KILL_BEATS / grid else 0.0

    /**
     * How much of the DJ fader curve a blend keeps: all of it for two tracks
     * that don't sing over each other, none — plain equal power — for a
     * head-on vocal collision, where both faders up would only be louder.
     */
    val holdFaders = (1.0 - clash).toFloat()
    val swapAtF = swapAt.toFloat()

    /**
     * Where the session moves to the incoming track. Advanced: when it
     * actually takes over — the bass swap in a blend.
     */
    val handoffAt = when {
        !advanced -> HANDOFF_AT
        style == TransitionStyle.DJ_BLEND && bassSwap -> swapAtF
        style == TransitionStyle.DJ_FILTER && filterVariant == FilterTransitionVariant.BASS_HANDOFF -> filterSwapAtF
        else -> HANDOFF_AT
    }

    private fun onBeat(fraction: Double): Double =
        if (grid > 0) (round(fraction * grid) / grid).coerceIn(1.0 / grid, 1.0 - 1.0 / grid) else fraction

    private fun onBar(fraction: Double): Double =
        if (grid >= 8.0) {
            (round(fraction * grid / 4.0) * 4.0 / grid).coerceIn(4.0 / grid, 1.0 - 4.0 / grid)
        } else {
            onBeat(fraction)
        }

    /**
     * Aims the incoming filter at [incomingAt] and the outgoing one at
     * [outgoingAt]: each the point in the blend its sink is processing *now*,
     * which is seconds past the point being heard — see
     * [TransitionFilterDsp.leadUs]. Driving both off the heard progress,
     * as this used to, left every filter move seconds late: the incoming track
     * played its first seconds unfiltered, and the entry filter was still
     * lifting after the faders had finished.
     *
     * One style pass when the two agree, two masked passes when they don't —
     * a handful of `pow`s either way. [outgoingAt] is null for the arming
     * case, where the outgoing sink has not reached the blend yet and must be
     * left open.
     */
    fun rideFilters(incomingAt: Float, outgoingAt: Float?) {
        if (outgoingAt == incomingAt) {
            rideStyle(incomingAt)
            return
        }
        // Incoming first: the few progress-independent branches that open both
        // filters agree with themselves, and the outgoing pass after this one
        // then has the last word on its own side.
        tracks.aimOutgoing = false
        rideStyle(incomingAt)
        tracks.aimOutgoing = true
        if (outgoingAt == null) return
        tracks.aimIncoming = false
        rideStyle(outgoingAt)
        tracks.aimIncoming = true
    }

    /**
     * Renders the plan's [TransitionStyle] as filtering across the blend.
     *
     * The gain curve is the same equal-power pair for every style — this is
     * what makes them sound different from each other, and it is the whole of
     * Phase 4. Driven off the same `progress` as the gains so the two stay
     * locked: a pause parks the filter exactly where it parks the fade.
     */
    private fun rideStyle(progress: Float) {
        when (style) {
            TransitionStyle.DJ_FILTER -> when (filterVariant) {
                FilterTransitionVariant.BASS_HANDOFF -> rideFallbackBassHandoff(progress)
                FilterTransitionVariant.SWEEP,
                FilterTransitionVariant.ECHO_RIDE,
                -> rideFilterSweep(progress)
            }
            TransitionStyle.DJ_BLEND ->
                if (bassSwap) rideBassSwap(progress) else rideVocalSeparation(progress)
            // GAPLESS is an album being played through, where any filtering would
            // be an edit the record didn't ask for — so it stays open whatever
            // the material does.
            TransitionStyle.GAPLESS -> tracks.open()
            // EQUAL_POWER used to be defined the same way: the bottom tier,
            // reached because the evidence was too weak to justify anything more
            // opinionated, therefore don't touch the spectrum.
            //
            // That conflated two different kinds of evidence. The tier is decided
            // by tempo and beat confidence; whether both tracks are singing is
            // measured by a separate model that doesn't depend on either. A pair
            // can have useless tempo evidence — dropping it to this tier — and a
            // perfectly good vocal mask on both sides saying they collide. Every
            // one of those transitions was rendered as a plain crossfade with two
            // full vocals over each other, because the weak half of the evidence
            // was silencing the strong half.
            TransitionStyle.EQUAL_POWER -> rideVocalSeparation(progress)
        }
        if (echo) rideEchoOut(progress)
    }

    /**
     * Echo out, over whatever the style did to the outgoing side: that track
     * stays open — an echo of a muffled signal is only mud — with its low end
     * handed off on the echo beat, the send opening across the beat before it,
     * and the dry signal killed on it. After that only the repeats are left,
     * ringing over the incoming track.
     *
     * Left alone before the send opens, so the echo stage is not even engaged
     * — not a sample touched — until the beat that feeds it.
     */
    private fun rideEchoOut(progress: Float) {
        val p = progress.toDouble()
        val handover = ((p - (echoAt - swapWidth)) / swapWidth).coerceIn(0.0, 1.0)
        tracks.outgoing(TransitionFilterDsp.OPEN_HZ, bassCutoff(handover))
        if (p < echoFrom) return
        val send = if (p >= echoAt) 0.0 else (p - echoFrom) / (echoAt - echoFrom)
        tracks.outgoingEcho(echoSecondsF, send.toFloat(), echoDry(progress))
    }

    /** The outgoing dry level in an echo out: whole until the echo beat, gone a fraction of a beat after. */
    fun echoDry(progress: Float): Float =
        (1.0 - ((progress - echoAt) / echoKill).coerceIn(0.0, 1.0)).toFloat()

    /**
     * How loud the outgoing track actually is at [progress] — uncapped, since
     * an echo tail runs past the end of the blend — for the headroom trim: its
     * fader, or with an echo out, where the fader stays up for the repeats and
     * the chain does the fading, the louder of its dry signal and its repeats.
     * The repeats sum with a full-level incoming track in the platform mixer
     * just as a dry signal would.
     */
    fun outgoingLevel(progress: Float, wallSpan: Float): Float {
        if (!echo) return mixFall(progress)
        val sinceEcho = (progress - echoAt.toFloat()) * wallSpan
        val repeats = if (sinceEcho > 0f) sinceEcho / (echoSecondsF * 1000f) else 0f
        val wet = if (sinceEcho > 0f) ECHO_LEVEL * ECHO_DECAY.pow(repeats) else 0f
        return maxOf(echoDry(progress), wet)
    }

    /**
     * The minimum intervention: pull two colliding vocals apart, and otherwise
     * leave the spectrum alone.
     *
     * Not a filter ride. [rideFilterSweep] is a *style* — a gesture chosen for a
     * pair that cannot be blended flat, driving to [FILTER_FLOOR_HZ] and taking
     * the outgoing track somewhere distant. This is damage control on a pair that
     * was going to be crossfaded plainly, and it has to stay subtle enough that a
     * listener notices the absence of the clash rather than the presence of a
     * filter. So it works the same way — complementary bands, outgoing losing its
     * top while the incoming enters with its body lifted — over a much shorter
     * distance, and only as far as the measured collision justifies.
     *
     * Zero overlap leaves both sides open, which is exactly what these styles did
     * before, so nothing changes for a pair that doesn't collide or for either
     * track lacking a vocal mask.
     */
    private fun rideVocalSeparation(progress: Float) {
        val amount = vocalOverlap.coerceIn(0.0, 1.0)
        if (amount <= 0.0) {
            tracks.open()
            return
        }
        val open = TransitionFilterDsp.OPEN_HZ.toDouble()
        // Both endpoints scaled by the collision, so a marginal clash is nudged
        // and a full one is properly separated, rather than everything getting
        // the same treatment at different speeds.
        val floor = glide(open, VOCAL_SEPARATION_FLOOR_HZ, amount)
        tracks.outgoing(
            glide(open, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE)).toFloat(),
            TransitionFilterDsp.OFF_HZ,
        )
        tracks.incoming(
            TransitionFilterDsp.OPEN_HZ,
            entryHighPass(progress, amount, VOCAL_SEPARATION_HIGH_PASS_HZ, ENTRY_OPEN_BY),
        )
    }

    /**
     * Pulls the outgoing track behind a closing low-pass while the incoming one
     * arrives with its body lifted out, for a pair too far apart in tempo to
     * blend flat.
     *
     * ## Why both sides are filtered
     *
     * The first version filtered only the outgoing track, and squared the
     * progress so that the sweep was spent almost entirely in the second half.
     * Both halves of that were wrong for the same reason: at the midpoint the
     * outgoing cutoff was still at 6.9kHz — wide open across the whole vocal
     * range — and the incoming track was explicitly set to no filtering at all.
     * So for the entire first half of every transition, two complete vocals
     * played over each other at comparable level, and the only thing
     * distinguishing them was gain. That is what a plain crossfade sounds like,
     * which is the one thing this is meant not to be.
     *
     * What a DJ does instead is hand the midrange over rather than double it:
     * the outgoing track starts losing its top the moment the blend begins, and
     * the incoming one enters high-passed — hats and presence only, no vocal
     * body — opening out as the outgoing track darkens. The two occupy
     * complementary bands through the middle of the blend and never compete for
     * the range a voice lives in.
     *
     * [FILTER_SWEEP_SHAPE] is what replaces the squaring: front-loaded now, so
     * the outgoing track's top is gone within the first tenth of the blend
     * rather than somewhere past the midpoint. What keeps that from gutting the
     * track being left is [FILTER_FLOOR_HZ] — the ride settles onto a 300Hz bed
     * and stays there — not restraint in the early travel, which is the part the
     * listener reads as the transition happening at all.
     */
    private fun rideFilterSweep(progress: Float) {
        val sweep = filterSweep.coerceIn(0.0, 1.0)
        if (sweep <= 0.0) {
            tracks.open()
            return
        }
        // Both ends scaled by [filterSweep], so a partial sweep engages less
        // sharply *and* stops short of the floor rather than crawling the same
        // distance more slowly.
        val open = TransitionFilterDsp.OPEN_HZ.toDouble()
        val entry = glide(open, FILTER_ENTRY_HZ, sweep)
        val floor = glide(open, FILTER_FLOOR_HZ, sweep)
        val cutoff = glide(entry, floor, progress.toDouble().pow(FILTER_SWEEP_SHAPE))
        val entryCorner = entryHighPass(
            progress,
            sweep,
            glide(ENTRY_HIGH_PASS_HZ, VOCAL_SAFE_ENTRY_HIGH_PASS_HZ, clash),
            vocalEntryOpenBy,
        )
        if (!advanced) {
            tracks.outgoing(cutoff.toFloat(), TransitionFilterDsp.OFF_HZ)
            tracks.incoming(TransitionFilterDsp.OPEN_HZ, entryCorner)
            return
        }
        // Two tempi that don't match are two kicks that flam, and the 300Hz bed
        // keeps the outgoing kick whole. So the low end still changes hands
        // once, on a beat — only one kick is ever playing.
        val handover = ((progress - (filterSwapAt - swapWidth)) / swapWidth).coerceIn(0.0, 1.0)
        tracks.outgoing(cutoff.toFloat(), bassCutoff(handover))
        tracks.incoming(TransitionFilterDsp.OPEN_HZ, maxOf(entryCorner, bassCutoff(1.0 - handover)))
    }

    /**
     * A compact fallback move for unmatched grids: keep the mids recognisable,
     * remove the two kicks from each other's way, then darken the outgoing deck
     * as the incoming low end takes over. This is deliberately a different
     * gesture from the long filter sweep and uses the same two filter stages.
     */
    private fun rideFallbackBassHandoff(progress: Float) {
        val p = progress.toDouble()
        val handover = ((p - (filterSwapAt - swapWidth)) / swapWidth).coerceIn(0.0, 1.0)
        val exitAmount = ((p - filterSwapAt) / (1.0 - filterSwapAt)).coerceIn(0.0, 1.0)
        val handoffTone = glide(
            TransitionFilterDsp.OPEN_HZ.toDouble(),
            FALLBACK_HANDOFF_FLOOR_HZ,
            exitAmount.pow(FALLBACK_HANDOFF_SHAPE),
        )
        // If timed masks found two singers, start clearing the outgoing vocal
        // range immediately instead of waiting for the bass exchange.
        val vocalTone = glide(
            TransitionFilterDsp.OPEN_HZ.toDouble(),
            VOCAL_SEPARATION_FLOOR_HZ,
            clash * p.pow(VOCAL_SAFE_EXIT_SHAPE),
        )
        val outgoingTone = minOf(handoffTone, vocalTone).toFloat()
        val incomingEntry = entryHighPass(
            progress,
            1.0,
            glide(FALLBACK_HANDOFF_ENTRY_HZ, VOCAL_SAFE_ENTRY_HIGH_PASS_HZ, clash),
            vocalEntryOpenBy,
        )
        tracks.outgoing(outgoingTone, bassCutoff(handover))
        tracks.incoming(TransitionFilterDsp.OPEN_HZ, maxOf(incomingEntry, bassCutoff(1.0 - handover)))
    }

    /**
     * Where the incoming track's high-pass sits at [progress].
     *
     * Rides from [topHz] down to nothing by [openBy] of the fade, so the track
     * is whole well before it is alone — the filter is there to keep it out of
     * the outgoing vocal's way during the overlap, not to colour the track the
     * listener is left with. [amount] scales the whole gesture, so a partial
     * sweep lifts proportionally less out.
     *
     * [ENTRY_SHAPE] is why the descent isn't linear. A geometric glide runs from
     * [TransitionFilterDsp.OFF_HZ] to [topHz], and the bottom half of that
     * range is sub-bass nobody hears a filter in: measured, a plain ride was
     * down to 123Hz by a third of the way through, which is to say doing nothing
     * at all for two thirds of the overlap. The exponent spends the travel where
     * a voice actually is — 772Hz at a sixth of the way in, 436Hz at a third —
     * and still arrives at fully open on time.
     */
    private fun entryHighPass(progress: Float, amount: Double, topHz: Double, openBy: Double): Float {
        val remaining = (1.0 - progress / openBy).coerceIn(0.0, 1.0)
        return glide(TransitionFilterDsp.OFF_HZ.toDouble(), topHz, amount * remaining.pow(ENTRY_SHAPE))
            .toFloat()
    }

    /**
     * Geometric interpolation between two cutoffs: [amount] 0 gives [from], 1
     * gives [to].
     *
     * Geometric rather than linear because pitch is logarithmic — a cutoff
     * moving in equal Hz steps sounds like it lurches through the bottom of its
     * range and crawls through the top.
     */
    private fun glide(from: Double, to: Double, amount: Double): Double =
        from * (to / from).pow(amount.coerceIn(0.0, 1.0))

    /**
     * Hands the low end from one track to the other, once, at the beat the
     * planner chose.
     *
     * Below [BASS_SWAP_HZ] exactly one track is present at any instant: the
     * incoming track arrives with its low end lifted out, and takes it over as
     * the outgoing track's is lifted in turn. Ramped over [BASS_SWAP_WIDTH] of
     * the fade rather than switched, because a 24 dB/octave filter appearing in
     * one buffer is a transient of its own.
     *
     * The midrange is handled far more lightly than in [rideFilterSweep] but is
     * no longer left alone, which it was. This style is chosen for pairs that
     * are beat-matched and close in tempo, so the two tracks are *meant* to
     * sound simultaneous — but "simultaneous" and "two lead vocals at once" are
     * not the same thing, and only the bass was ever being separated. So the
     * incoming track still enters with its body lifted, over a shorter window
     * and from a lower corner, and the outgoing track loses its top in the last
     * half, where it is already quiet enough that the change reads as it
     * receding rather than as an effect.
     */
    private fun rideBassSwap(progress: Float) {
        // 0 before the swap window, 1 after it: how much of the low end has
        // changed hands. Classically a window centred on the swap; advanced,
        // half a beat that ends on it, so the new kick arrives on the one.
        val handover = ((progress - swapFrom) / swapWidth).coerceIn(0.0, 1.0)
        // The incoming track's own low end is already being held out by the
        // swap, so whichever corner sits higher is the one doing the work.
        // Scaled up by however much the two are actually singing over each other.
        // A blend is chosen for pairs on a shared grid, which is the case where
        // nothing about the arrangement separates two lead vocals — they sit in
        // the same bar and the same range for the whole overlap — so the fixed
        // corner that was here handled a marginal collision and a head-on one
        // identically. At full collision the entry corner reaches
        // [BLEND_ENTRY_CLASH_HIGH_PASS_HZ] and holds longer.
        val clash = vocalOverlap.coerceIn(0.0, 1.0)
        val entry = maxOf(
            bassCutoff(1.0 - handover),
            entryHighPass(
                progress,
                1.0,
                glide(BLEND_ENTRY_HIGH_PASS_HZ, BLEND_ENTRY_CLASH_HIGH_PASS_HZ, clash),
                blendEntryOpenBy,
            ),
        )
        tracks.incoming(TransitionFilterDsp.OPEN_HZ, entry)
        tracks.outgoing(blendExitLowPass(progress, clash), bassCutoff(handover))
    }

    /**
     * The outgoing track's low-pass through a beat-matched blend: open until
     * [BLEND_EXIT_FROM], then closing to [BLEND_EXIT_LOW_PASS_HZ] by the end.
     *
     * Deliberately shallow. Enough to take the air and the sibilance off a voice
     * that is on its way out, so it stops competing with the one arriving;
     * nowhere near the [FILTER_FLOOR_HZ] that [rideFilterSweep] drives to, which
     * would contradict the reason this style was chosen.
     *
     * [clash] both starts it earlier and takes it further, because "shallow" is
     * the right default and the wrong answer for two choruses landing together.
     */
    private fun blendExitLowPass(progress: Float, clash: Double): Float {
        val from = blendExitFrom
        val amount = ((progress - from) / (1.0 - from)).coerceIn(0.0, 1.0)
        val floor = glide(BLEND_EXIT_LOW_PASS_HZ, BLEND_EXIT_CLASH_LOW_PASS_HZ, clash)
        return glide(TransitionFilterDsp.OPEN_HZ.toDouble(), floor, amount).toFloat()
    }

    /** [amount] 0 leaves the low end alone; 1 lifts it out entirely. */
    private fun bassCutoff(amount: Double): Float =
        glide(TransitionFilterDsp.OFF_HZ.toDouble(), BASS_SWAP_HZ, amount).toFloat()


    /**
     * The incoming fader for the blend in flight. Classic Automix — and every
     * non-DJ style — is the equal-power [riseGain]. Advanced, each style gets
     * the curve a DJ would ride it on, with the filters doing the separating:
     *  - a bass-swap blend has the incoming fader fully up by the swap
     *    ("both faders up"), backed off toward equal power as far as the two
     *    vocals collide;
     *  - a filter ride brings it up a little early, behind its high-pass.
     *
     * [headroomFor] keeps two faders at full from summing past full scale.
     */
    fun mixRise(progress: Float): Float {
        if (!advanced) return riseGain(progress)
        val styled = when (style) {
            TransitionStyle.DJ_BLEND ->
                if (bassSwap) held(riseGain(progress), riseGain(progress / swapAtF)) else riseGain(progress)
            TransitionStyle.DJ_FILTER -> when (filterVariant) {
                FilterTransitionVariant.BASS_HANDOFF -> riseGain(progress / filterSwapAtF)
                FilterTransitionVariant.ECHO_RIDE -> riseGain(progress.pow(ECHO_RIDE_RISE_SHAPE))
                FilterTransitionVariant.SWEEP -> riseGain(progress.pow(FILTER_RISE_SHAPE))
            }
            else -> riseGain(progress)
        }
        val vocalSafe = riseGain(progress.pow(VOCAL_SAFE_RISE_SHAPE))
        return styled + (vocalSafe - styled) * clash.toFloat()
    }

    /**
     * The outgoing fader, [mixRise]'s partner: held at full until the bass
     * swap in a blend and pulled after it, held longer in a filter ride where
     * the closing low-pass is already taking it away.
     */
    fun mixFall(progress: Float): Float {
        if (!advanced) return fallGain(progress)
        // The fader carries the repeats too, so an echo out leaves it up and
        // fades the dry signal in the chain instead — see [rideEchoOut].
        if (echo) return 1f
        val styled = when (style) {
            TransitionStyle.DJ_BLEND -> {
                if (!bassSwap) {
                    fallGain(progress)
                } else {
                    val swap = swapAtF
                    held(fallGain(progress), if (progress <= swap) 1f else fallGain((progress - swap) / (1f - swap)))
                }
            }
            TransitionStyle.DJ_FILTER -> when (filterVariant) {
                FilterTransitionVariant.BASS_HANDOFF -> {
                    val swap = filterSwapAtF
                    if (progress <= swap) 1f else fallGain((progress - swap) / (1f - swap))
                }
                FilterTransitionVariant.ECHO_RIDE -> fallGain(progress.pow(ECHO_RIDE_FALL_SHAPE))
                FilterTransitionVariant.SWEEP -> fallGain(progress.pow(FILTER_FALL_SHAPE))
            }
            else -> fallGain(progress)
        }
        val vocalSafe = fallGain(progress.pow(VOCAL_SAFE_FALL_SHAPE))
        return styled + (vocalSafe - styled) * clash.toFloat()
    }

    /** [dj] as far as [Render.holdFaders] allows, the rest of the way back toward [plain]. */
    private fun held(plain: Float, dj: Float): Float = plain + (dj - plain) * holdFaders

    companion object {
        /** The render of [plan], as the player arms it. */
        fun of(plan: TransitionPlan, advanced: Boolean = true, tracks: AimedFilters = AimedFilters.None) = TransitionRender(
            style = plan.transitionStyle,
            bassSwap = plan.bassSwap,
            bassSwapFraction = plan.bassSwapFraction,
            filterSweep = plan.filterSweep,
            filterVariant = plan.filterVariant,
            vocalOverlap = plan.vocalOverlap,
            advanced = advanced,
            beats = if (plan.beatSeconds > 0) plan.fadeSeconds / plan.beatSeconds else 0.0,
            lockPhase = plan.phaseLock,
            echoSeconds = plan.echoSeconds,
            tracks = tracks,
        )

        /**
         * Where the outgoing low-pass sits the instant a filter ride begins.
         *
         * The ride used to start from [TransitionFilterProcessor.OPEN_HZ] and
         * travel down, which meant the first stretch of every transition was
         * spent crossing a range nobody can hear a filter in: a tenth of the way
         * through the fade the cutoff was still at 17.5kHz, indistinguishable
         * from no filter at all, and the ride only became audible around the
         * midpoint. Engaging here instead — above the fundamentals of everything
         * but cymbals, so what goes first is air and shimmer — is what makes the
         * gesture read as a hand landing on the filter the moment the blend
         * starts, rather than something remembered late.
         *
         * 9kHz was the first attempt at that and still read as late by ear: it
         * is above everything but cymbals, so engaging there takes the air off
         * and nothing else, and the outgoing vocal — the thing actually clashing
         * — was untouched until the sweep had travelled most of the way down.
         * 7kHz is inside the presence range, so the gesture is audible on the
         * voice itself from the first instant.
         */
        const val FILTER_ENTRY_HZ = 7_000.0

        /**
         * The bottom of a filter ride. Below a few hundred hertz a track stops
         * reading as "further away" and starts reading as "broken", which is not
         * the impression a transition should leave of the song being left.
         */
        const val FILTER_FLOOR_HZ = 300.0

        /**
         * Where the low end is considered to end. Around the fundamental of a
         * bass guitar's upper register, and the usual corner on a mixer's bass
         * kill — high enough to clear the kick and the sub, low enough to leave
         * the body of the vocal alone.
         */
        const val BASS_SWAP_HZ = 200.0

        /** How much of the fade the low end takes to change hands. */
        const val BASS_SWAP_WIDTH = 0.10

        // ---- Advanced Automix ----------------------------------------------

        /**
         * Advanced: the low end changes hands over this many beats, ending on
         * the swap beat. Half a beat is a hand on the EQ, not a kill switch —
         * long enough that the 24 dB/octave corner moving is no click, short
         * enough that the two kicks never play together.
         */
        const val SWAP_BEATS = 0.5

        /** Where a filter ride hands its low end over, before bar-snapping. */
        const val FILTER_BASS_SWAP_AT = 0.5

        /**
         * Filter-ride fader shapes, as exponents on progress. Above 1 holds the
         * outgoing fader up longer, since the closing low-pass is already
         * taking it away; below 1 brings the incoming one up a little early
         * behind its high-pass.
         */
        const val FILTER_FALL_SHAPE = 1.6f
        const val FILTER_RISE_SHAPE = 0.8f

        /** Alternate fallback: a quick bass exchange followed by a shallow exit filter. */
        const val FALLBACK_HANDOFF_ENTRY_HZ = 900.0
        const val FALLBACK_HANDOFF_FLOOR_HZ = 1_600.0
        const val FALLBACK_HANDOFF_SHAPE = 0.8

        /** Vocal-aware fallback: keep the arriving lead out until the outgoing lead has cleared. */
        const val VOCAL_SAFE_ENTRY_HIGH_PASS_HZ = 2_400.0
        const val VOCAL_SAFE_ENTRY_OPEN_BY = 0.78
        const val VOCAL_SAFE_EXIT_SHAPE = 0.55
        const val VOCAL_SAFE_RISE_SHAPE = 1.65f
        const val VOCAL_SAFE_FALL_SHAPE = 0.65f

        /** Echo personality keeps both decks recognisable until its decisive fader pull. */
        const val ECHO_RIDE_RISE_SHAPE = 0.7f
        const val ECHO_RIDE_FALL_SHAPE = 1.35f

        /**
         * An echo out's dry signal is killed over this much of a beat: short
         * enough to read as the fader being pulled, long enough not to click.
         */
        const val ECHO_KILL_BEATS = 0.25

        /**
         * The echo's first repeat against the dry level, and what each repeat
         * keeps of the one before — [TransitionFilterProcessor]'s wet and
         * feedback, mirrored here for the headroom trim.
         */
        const val ECHO_LEVEL = 0.5f
        const val ECHO_DECAY = 0.4f

        /**
         * Shape of the outgoing low-pass against fade progress, between
         * [FILTER_ENTRY_HZ] and [FILTER_FLOOR_HZ].
         *
         * Was 2.0 — squared — which left the cutoff at 6.9kHz at the midpoint,
         * so the outgoing vocal went untouched through the whole first half of
         * every transition. Then 1.3, which was still back-loaded: the exponent
         * held the cutoff near its entry point through the opening of the fade,
         * which is precisely where the two vocals overlap at comparable level.
         *
         * Below 1 now, so the ride is front-loaded — steepest at the start,
         * flattening as it approaches the floor. That is the shape of the gesture
         * being imitated: a hand moves a filter knob fast and then eases it in,
         * not the reverse. The old worry that a fast cutoff takes the outgoing
         * track out prematurely is answered by [FILTER_FLOOR_HZ] rather than by
         * the exponent — the ride bottoms out at 300Hz, which is still a present
         * bed under the incoming track, not silence.
         *
         * Crosses 5kHz — about where a low-pass becomes plainly audible on a
         * full-range mix — a twentieth of the way into the fade, against a
         * quarter of the way at 1.3. Lands at 3.8kHz a tenth of the way in,
         * 2.6kHz at a fifth, 1.0kHz at the midpoint.
         */
        const val FILTER_SWEEP_SHAPE = 0.75

        /**
         * Where the incoming track's high-pass starts on a filter ride.
         *
         * Above the fundamental range of most voices and the body of a snare, so
         * what arrives first is presence and percussion — enough to hear a track
         * coming and lock onto its groove, not enough for a second lead vocal.
         *
         * 700Hz was that corner while the outgoing sweep was gentler. It no longer
         * is: the sweep engages at [FILTER_ENTRY_HZ] and is down to 4kHz a tenth
         * of the way in, so a 700Hz entry left the two tracks sharing very nearly
         * three octaves — and sharing them from 529Hz up, which is exactly where a
         * lead vocal's fundamentals sit. 1.2kHz takes about an octave off the
         * bottom of that shared band, and it is the octave the collision actually
         * happens in. What is left of the outgoing track then sits *under* the
         * arriving one rather than inside it, which is what makes the incoming
         * track read as a layer landing on top of a darkening one instead of a
         * second voice in the same space.
         */
        const val ENTRY_HIGH_PASS_HZ = 1_200.0

        /**
         * How far into the fade the incoming track is fully open again.
         *
         * Comfortably before the end: past this point the outgoing track is deep
         * into its own sweep and quiet with it, so there is nothing left to keep
         * out of the way of, and anything still filtered here would just be the
         * new track arriving wrong.
         */
        const val ENTRY_OPEN_BY = 0.6

        /**
         * Shape of the incoming high-pass's descent; see [entryHighPass].
         *
         * Below 1 so the corner lingers in the range a voice occupies instead of
         * dropping straight through it into sub-bass, where a high-pass is
         * inaudible and the clash this exists to prevent is already back.
         *
         * 0.35 rather than 0.45 for more of the same, and the effect compounds
         * across the overlap rather than being a flat offset: on a filter ride the
         * corner sits a fourteenth higher a tenth of the way in, a quarter higher
         * at three tenths, a third higher at four. So the hold is back-loaded into
         * the middle of the blend — where both tracks are near equal gain and the
         * collision is at its worst — and what gets given up in exchange is the
         * bottom of the descent, which is a few hundred hertz of sub-bass nobody
         * hears a high-pass leave. The release into the last of [ENTRY_OPEN_BY] is
         * correspondingly more of an event, which is the point: the arriving track
         * opening out is the moment the listener is meant to notice.
         */
        const val ENTRY_SHAPE = 0.35

        /**
         * How far [rideVocalSeparation] closes the outgoing track's top at a
         * full collision.
         *
         * Well above [FILTER_FLOOR_HZ]'s 300Hz, because this fires on pairs that
         * were going to be crossfaded plainly and the intent is to stop two
         * voices competing, not to send one of them into another room. 1.6kHz is
         * below the presence and sibilance a lead vocal is picked out by, and
         * above enough of its body that the track still reads as itself.
         */
        const val VOCAL_SEPARATION_FLOOR_HZ = 1_600.0

        /**
         * Where the incoming track's high-pass starts in [rideVocalSeparation].
         *
         * Lower than [ENTRY_HIGH_PASS_HZ]'s 1.2kHz, for the same reason the floor
         * is higher: on a plain crossfade the arriving track has no filter
         * gesture to explain itself with, so it has to sound like it fades in
         * normally. 700Hz clears the body of a voice while leaving its lower
         * harmonics, which is enough to stop it fighting the outgoing lead.
         *
         * Was 450Hz, which fit that description on paper and was mostly inaudible
         * in practice: a fifth of the way in it was already down to 268Hz, doing
         * nothing about a collision the vocal model had reported at full strength.
         * 700Hz is the corner a filter ride itself used to open at, so it is a
         * known-restrained one rather than a new guess — and keeping this style a
         * clear step below that one leaves the two ranked the way their tiers are.
         */
        const val VOCAL_SEPARATION_HIGH_PASS_HZ = 700.0

        /**
         * [ENTRY_HIGH_PASS_HZ]'s counterpart for a beat-matched blend: lower, and
         * briefer.
         *
         * Was 320Hz, which the bass swap almost entirely swallowed. The incoming
         * track is already high-passed at [BASS_SWAP_HZ] until the low end changes
         * hands and [rideBassSwap] takes whichever corner is higher, so a 320Hz
         * entry was only above that floor for the first sixth of the blend, and
         * only ever by a little. 520Hz gives the arriving track an entry gesture
         * that outlives the bass kill — clear of it until nearly three tenths in —
         * rather than one hiding inside it.
         */
        const val BLEND_ENTRY_HIGH_PASS_HZ = 520.0

        /** [ENTRY_OPEN_BY]'s counterpart for a beat-matched blend. */
        const val BLEND_ENTRY_OPEN_BY = 0.45

        /**
         * Where [BLEND_ENTRY_HIGH_PASS_HZ] and [BLEND_ENTRY_OPEN_BY] go at a full
         * vocal collision: a corner high enough to hold the arriving voice's body
         * out, held for most of the blend rather than a third of it.
         *
         * Still short of [ENTRY_HIGH_PASS_HZ]'s filter-ride treatment. The two
         * tracks are on a shared grid and meant to sound simultaneous; the aim is
         * to stop the two leads occupying one band, not to hide either of them.
         *
         * Tracks [BLEND_ENTRY_HIGH_PASS_HZ] upward — 620Hz to 950Hz — so how hard
         * the two are singing over each other stays the thing that separates a
         * marginal collision from a head-on one, rather than both converging on
         * whatever the bass kill was already doing.
         */
        const val BLEND_ENTRY_CLASH_HIGH_PASS_HZ = 950.0
        const val BLEND_ENTRY_CLASH_OPEN_BY = 0.7

        /** Where [BLEND_EXIT_FROM] and [BLEND_EXIT_LOW_PASS_HZ] go at a full collision. */
        const val BLEND_EXIT_CLASH_FROM = 0.12
        const val BLEND_EXIT_CLASH_LOW_PASS_HZ = 1_100.0

        /**
         * Where the outgoing track starts losing its top on a beat-matched
         * blend.
         *
         * Was 0.5, which left the outgoing track completely unfiltered for the
         * whole first half — the same "remembered late" complaint that
         * [FILTER_ENTRY_HZ] answers on a filter ride, in the one style where
         * both tracks are at their most similar and so most likely to clash.
         * Brought forward rather than to zero: a beat-matched blend is chosen
         * because the two tracks are meant to sound simultaneous, and opening
         * with the outgoing one already darkened would defeat that.
         */
        const val BLEND_EXIT_FROM = 0.3

        /**
         * Where that low-pass lands by the end of the blend. High enough that the
         * track is still plainly itself — this style is chosen for pairs meant to
         * sound simultaneous — and low enough to take the sibilance off a voice
         * that is leaving.
         */
        const val BLEND_EXIT_LOW_PASS_HZ = 2_200.0

        /**
         * Where in the blend the session moves to the incoming track: halfway,
         * which with an equal-power curve is the instant the two are equally
         * loud and the incoming one takes over.
         */
        const val HANDOFF_AT = 0.5f
    }
}
