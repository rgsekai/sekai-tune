package moe.rgsekai.sekaitune.shared.playback.karaoke

import okio.Path.Companion.toOkioPath
import moe.rgsekai.sekaitune.shared.playback.KaraokeProcessor
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class KaraokeStemsTest {

    private val rate = MdxSeparator.SAMPLE_RATE
    private val cache = kotlin.io.path.createTempDirectory("karaoke").toFile().apply { deleteOnExit() }.toOkioPath()

    /** A deterministic stereo "song": a few tones and a little noise, different per side. */
    private fun sample(frame: Long, channel: Int): Float {
        val t = frame.toDouble() / rate
        val noise = ((frame * 2 + channel) * 2_654_435_761L ushr 16 and 0xFFFF) / 65_536f - 0.5f
        return (0.2 * sin(2 * PI * 220 * t) + 0.1 * sin(2 * PI * (660 + 40 * channel) * t) +
            0.05 * sin(2 * PI * 3_100 * t + channel)).toFloat() + 0.05f * noise
    }

    /**
     * The separator's own decode, starting [priming] frames early as a codec's
     * priming would. With [seekError], it seeks, and says it landed a few frames
     * from where it did — a container timestamp's rounding, different each time.
     */
    private inner class Stream(
        private val priming: Int,
        private val total: Long,
        private val seekError: ((Long) -> Int)? = null,
    ) : PcmStream {
        private var at = 0L
        private var counted = 0L

        override fun seek(frame: Long): Long {
            val error = seekError ?: return -1L
            at = frame
            return frame + error(frame)
        }
        override val sampleRate = rate
        override val durationFrames = total
        override fun next(): Array<FloatArray>? {
            if (at >= total) return null
            val n = minOf(4_000L, total - at).toInt()
            val left = FloatArray(n) { sample(at + it - priming, 0) }
            val right = FloatArray(n) { sample(at + it - priming, 1) }
            at += n
            counted += n
            return arrayOf(left, right)
        }
        override fun close() = Unit
    }

    @Test
    fun `stems line up with the sink and cancel what they separated`() {
        val total = rate * 40L
        val stems = TrackStems("song", "song", { Stream(priming = 312, total = total) }, MdxModel.MDX_9482, cache)
        stems.open()
        val processor = KaraokeProcessor().apply {
            configure(rate, 2)
            setEnabled(true)
            bind(stems)
        }
        // Everything is "vocal": a working path takes the whole signal out.
        val oracle = ChunkSeparator { left, right, vocalLeft, vocalRight ->
            for (i in left.indices) {
                vocalLeft[i] = left[i] / MdxModel.MDX_9482.compensate
                vocalRight[i] = right[i] / MdxModel.MDX_9482.compensate
            }
        }

        val block = 512
        var frame = rate * 9L + 123 // a seek to just past 9 s
        var inputEnergy = 0.0
        var outputEnergy = 0.0
        var separated = false
        repeat(rate * 12 / block) { step ->
            val samples = FloatArray(block * 2) { sample(frame + it / 2, it % 2) }
            // Container timestamps in whole milliseconds, as WebM has them.
            val mediaTimeUs = frame * 1_000_000 / rate / 1_000 * 1_000
            processor.process(samples, samples.size, mediaTimeUs)
            if (stems.captured) stems.align()
            if (!separated && step > 40) {
                for (index in 0..4) stems.separate(index, oracle)
                separated = true
            }
            if (step > rate * 6 / block) {
                for (i in samples.indices) {
                    val original = sample(frame + i / 2 - processor.latencyFrames, i % 2)
                    inputEnergy += original * original
                    outputEnergy += samples[i] * samples[i]
                }
            }
            frame += block
        }
        assertTrue(processor.isUsingStems, "stems never took over")
        val residual = 10 * log10(outputEnergy / inputEnergy)
        assertTrue(residual < -50, "residual only $residual dB below the input")
    }

    @Test
    fun `stems readied while off take over the moment it is switched on`() {
        val total = rate * 40L
        val stems = TrackStems("warm", "warm", { Stream(priming = 312, total = total) }, MdxModel.MDX_9482, cache)
        stems.open()
        val processor = KaraokeProcessor().apply {
            configure(rate, 2)
            bind(stems)
        }
        val oracle = ChunkSeparator { left, right, vocalLeft, vocalRight ->
            for (i in left.indices) {
                vocalLeft[i] = left[i] / MdxModel.MDX_9482.compensate
                vocalRight[i] = right[i] / MdxModel.MDX_9482.compensate
            }
        }
        val block = 512
        var frame = rate * 3L
        fun play(): FloatArray {
            val samples = FloatArray(block * 2) { sample(frame + it / 2, it % 2) }
            processor.process(samples, samples.size, frame * 1_000_000 / rate)
            if (stems.captured) stems.align()
            frame += block
            return samples
        }
        // Off, with the lyrics up: the stems follow along and separate ahead.
        repeat(rate / block) {
            val samples = play()
            val untouched = FloatArray(block * 2) { sample(frame - block + it / 2, it % 2) }
            assertTrue(samples.contentEquals(untouched), "off must not touch the audio")
        }
        for (index in 0..3) stems.separate(index, oracle)
        repeat(rate / 2 / block) { play() }

        processor.setEnabled(true)
        var blocks = 0
        while (!processor.isUsingStems) {
            play()
            blocks++
            assertTrue(blocks * block < rate / 5, "stems took over only after ${blocks * block} frames")
        }
    }

    @Test
    fun `a seek back into separated audio is neural again within half a second`() {
        val total = rate * 40L
        val stems = TrackStems("seek", "seek", { Stream(priming = 312, total = total) }, MdxModel.MDX_9482, cache)
        stems.open()
        val processor = KaraokeProcessor().apply {
            configure(rate, 2)
            setEnabled(true)
            bind(stems)
        }
        val oracle = ChunkSeparator { left, right, vocalLeft, vocalRight ->
            for (i in left.indices) {
                vocalLeft[i] = left[i] / MdxModel.MDX_9482.compensate
                vocalRight[i] = right[i] / MdxModel.MDX_9482.compensate
            }
        }
        val block = 480
        var frame = rate * 4L
        fun play() {
            val samples = FloatArray(block * 2) { sample(frame + it / 2, it % 2) }
            // A seek lands on a whole millisecond, as the sink's block times do.
            processor.process(samples, samples.size, frame * 1_000_000 / rate / 1_000 * 1_000)
            if (stems.captured) stems.align()
            frame += block
        }
        repeat(10) { play() }
        for (index in 0..4) stems.separate(index, oracle)
        repeat(rate * 3 / block) { play() }
        assertTrue(processor.isUsingStems, "stems never took over")

        // Back eight seconds, into audio separated before: a lyric line tapped.
        frame -= rate * 8L + 77
        processor.reset()
        var blocks = 0
        while (!processor.isUsingStems) {
            play()
            blocks++
            assertTrue(blocks * block < rate / 2, "neural again only after ${blocks * block} frames")
        }
    }

    @Test
    fun `segments from decodes that disagree still line up`() {
        val total = rate * 40L
        // Each seek lands a different few frames from where it says.
        val stems = TrackStems(
            "islands", "islands",
            { Stream(priming = 312, total = total, seekError = { frame -> (frame % 97).toInt() - 48 }) },
            MdxModel.MDX_9482, cache,
        )
        stems.open()
        val processor = KaraokeProcessor().apply {
            configure(rate, 2)
            setEnabled(true)
            bind(stems)
        }
        val oracle = ChunkSeparator { left, right, vocalLeft, vocalRight ->
            for (i in left.indices) {
                vocalLeft[i] = left[i] / MdxModel.MDX_9482.compensate
                vocalRight[i] = right[i] / MdxModel.MDX_9482.compensate
            }
        }
        val block = 512
        var frame = rate * 9L
        var inputEnergy = 0.0
        var outputEnergy = 0.0
        fun play(measure: Boolean) {
            val samples = FloatArray(block * 2) { sample(frame + it / 2, it % 2) }
            processor.process(samples, samples.size, frame * 1_000_000 / rate)
            if (stems.captured) stems.align()
            if (measure) {
                for (i in samples.indices) {
                    val original = sample(frame + i / 2 - processor.latencyFrames, i % 2)
                    inputEnergy += original * original
                    outputEnergy += samples[i] * samples[i]
                }
            }
            frame += block
        }
        repeat(4) { play(false) }
        // Far ahead first (its own decode, from a seek), then back to the start
        // (another), and on: three islands that have to become one.
        for (index in listOf(3, 0, 1, 2, 4)) stems.separate(index, oracle)
        // And whatever a conflict sent back, as the worker would.
        while (true) {
            val (index, _) = stems.nextSegment(System.nanoTime(), wholeSong = true) ?: break
            if (index > 4) break
            stems.separate(index, oracle)
        }
        repeat(rate * 2 / block) { play(false) }
        assertTrue(processor.isUsingStems, "stems never took over")
        // Across the joins between segments 1, 2 and 3.
        repeat(rate * 12 / block) { play(true) }
        val residual = 10 * log10(outputEnergy / inputEnergy)
        assertTrue(residual < -45, "residual only $residual dB below the input across decodes")
    }
}
