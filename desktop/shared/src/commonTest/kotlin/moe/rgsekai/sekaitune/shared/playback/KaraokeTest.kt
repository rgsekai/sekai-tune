package moe.rgsekai.sekaitune.shared.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KaraokeTest {

    private val rate = 48_000

    private fun tone(hz: Double, frame: Int) = sin(2.0 * PI * hz * frame / rate).toFloat()

    /**
     * A stand-in mix: a "vocal" at 440 Hz and its harmonic at 1320 Hz dead
     * centre, a centred bass at 60 Hz, a guitar at 1000 Hz hard left and keys
     * at 1500 Hz panned 80/20 right.
     */
    private fun mix(frames: Int, vocal: Boolean = true): FloatArray {
        val samples = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            val voice = if (vocal) 0.2f * tone(440.0, frame) + 0.1f * tone(1320.0, frame) else 0f
            val bass = 0.2f * tone(60.0, frame)
            val guitar = 0.15f * tone(1000.0, frame)
            val keys = 0.15f * tone(1500.0, frame)
            samples[frame * 2] = voice + bass + guitar + keys * 0.2f
            samples[frame * 2 + 1] = voice + bass + keys * 0.8f
        }
        return samples
    }

    private fun KaraokeProcessor.run(samples: FloatArray, channels: Int = 2, blockFrames: Int = 512) {
        val block = FloatArray(blockFrames * channels)
        var at = 0
        while (at < samples.size) {
            val count = minOf(block.size, samples.size - at)
            samples.copyInto(block, 0, at, at + count)
            process(block, count)
            block.copyInto(samples, at, 0, count)
            at += count
        }
    }

    /** Level of [hz] in one channel of the frames from [from], by Goertzel, in dB. */
    private fun level(samples: FloatArray, channel: Int, hz: Double, from: Int, channels: Int = 2): Double {
        val coefficient = 2 * cos(2 * PI * hz / rate)
        var previous = 0.0
        var before = 0.0
        val frames = samples.size / channels
        for (frame in from until frames) {
            val current = samples[frame * channels + channel] + coefficient * previous - before
            before = previous
            previous = current
        }
        val power = previous * previous + before * before - coefficient * previous * before
        return 10 * log10(power / (frames - from) + 1e-20)
    }

    @Test
    fun `off it leaves every sample untouched`() {
        val processor = KaraokeProcessor().apply { configure(rate, 2) }
        val input = mix(rate / 2)
        val output = input.copyOf()
        processor.run(output)
        assertContentEquals(input, output)
        assertFalse(processor.isActive)
    }

    @Test
    fun `on it takes the centred vocal out and leaves the band around it`() {
        val processor = KaraokeProcessor().apply {
            configure(rate, 2)
            setEnabled(true)
        }
        val frames = rate * 2
        val input = mix(frames)
        val output = input.copyOf()
        processor.run(output)
        val reference = mix(frames, vocal = false)

        val settled = rate / 2
        for (channel in 0..1) {
            val vocalDrop = level(input, channel, 440.0, settled) - level(output, channel, 440.0, settled)
            val harmonicDrop = level(input, channel, 1320.0, settled) - level(output, channel, 1320.0, settled)
            assertTrue(vocalDrop > 20, "vocal down only $vocalDrop dB on channel $channel")
            assertTrue(harmonicDrop > 20, "harmonic down only $harmonicDrop dB on channel $channel")
            for (hz in doubleArrayOf(60.0, 1000.0, 1500.0)) {
                val change = level(output, channel, hz, settled) - level(reference, channel, hz, settled)
                if (level(reference, channel, hz, settled) < -60) continue
                assertTrue(abs(change) < 1.0, "$hz Hz moved $change dB on channel $channel")
            }
        }
    }

    @Test
    fun `the level sets how much of the vocal goes`() {
        val frames = rate * 2
        val settled = rate / 2
        val input = mix(frames)
        val drops = listOf(0f, 0.5f).map { amount ->
            val processor = KaraokeProcessor().apply {
                configure(rate, 2)
                setEnabled(true)
                setLevel(amount)
            }
            val output = input.copyOf()
            processor.run(output)
            level(input, 0, 440.0, settled) - level(output, 0, 440.0, settled)
        }
        assertTrue(abs(drops[0]) < 0.5, "level 0 still took ${drops[0]} dB off the vocal")
        // Half the vocal subtracted leaves half of it: 6 dB down.
        assertTrue(abs(drops[1] - 6.02) < 0.5, "level 0.5 took ${drops[1]} dB off the vocal")
    }

    @Test
    fun `the delay it adds is what it reports`() {
        val processor = KaraokeProcessor().apply {
            configure(rate, 2)
            setEnabled(true)
        }
        // Silence in one channel only: nothing is centre, so the output is
        // the input delayed once the crossfade has finished.
        val frames = rate
        val input = FloatArray(frames * 2) { if (it % 2 == 0) tone(997.0, it / 2) * 0.3f else 0f }
        val output = input.copyOf()
        processor.run(output)
        val latency = processor.latencyFrames
        assertTrue(latency in 1_920..4_096)
        for (frame in rate / 2 until frames) {
            assertEquals(input[(frame - latency) * 2], output[frame * 2], 1e-4f)
        }
    }

    @Test
    fun `a mono track is reported and left whole`() {
        var unavailable = false
        val processor = KaraokeProcessor(onUnavailableChanged = { unavailable = it }).apply {
            configure(rate, 2)
            setEnabled(true)
        }
        val frames = rate * 3
        val input = FloatArray(frames * 2) { 0.3f * tone(440.0, it / 2) }
        val output = input.copyOf()
        processor.run(output)
        assertTrue(unavailable)
        val tail = rate * 2
        val change = level(output, 0, 440.0, tail) - level(input, 0, 440.0, tail)
        assertTrue(abs(change) < 0.5, "mono track changed by $change dB")
    }

    @Test
    fun `a single channel is reported unavailable`() {
        var unavailable = false
        val processor = KaraokeProcessor(onUnavailableChanged = { unavailable = it }).apply {
            configure(rate, 1)
            setEnabled(true)
        }
        val input = FloatArray(1_024) { tone(440.0, it) }
        val output = input.copyOf()
        processor.process(output, output.size)
        assertTrue(unavailable)
        assertContentEquals(input, output)
    }

    @Test
    fun `switching on and off mid-song does not click and ends bit-exact`() {
        val processor = KaraokeProcessor().apply { configure(rate, 2) }
        val frames = rate * 3
        val input = mix(frames)
        val output = input.copyOf()
        val block = 2 * 512
        var at = 0
        while (at < output.size) {
            if (at == rate / 2 * 2 / block * block) processor.setEnabled(true)
            if (at == rate * 3 / 2 * 2 / block * block) processor.setEnabled(false)
            val chunk = output.copyOfRange(at, minOf(at + block, output.size))
            processor.process(chunk, chunk.size)
            chunk.copyInto(output, at)
            at += block
        }
        var largestStep = 0f
        for (index in 2 until output.size) largestStep = maxOf(largestStep, abs(output[index] - output[index - 2]))
        var inputStep = 0f
        for (index in 2 until input.size) inputStep = maxOf(inputStep, abs(input[index] - input[index - 2]))
        assertTrue(largestStep < inputStep * 1.5f, "step $largestStep against the input's $inputStep")
        assertFalse(processor.isActive)
        val tail = frames * 2 - rate
        assertContentEquals(input.copyOfRange(tail, input.size), output.copyOfRange(tail, output.size))
    }

    @Test
    fun `extra channels are delayed in step and otherwise untouched`() {
        val processor = KaraokeProcessor().apply {
            configure(rate, 6)
            setEnabled(true)
        }
        val frames = rate
        val input = FloatArray(frames * 6) { 0.25f * tone(330.0 + it % 6 * 50, it / 6) }
        val output = input.copyOf()
        processor.run(output, channels = 6)
        val latency = processor.latencyFrames
        for (frame in rate / 2 until frames) {
            for (channel in 2 until 6) {
                assertEquals(input[(frame - latency) * 6 + channel], output[frame * 6 + channel], 1e-5f)
            }
        }
        assertTrue(sqrt(output.sumOf { it.toDouble() * it }) > 0)
    }
}
