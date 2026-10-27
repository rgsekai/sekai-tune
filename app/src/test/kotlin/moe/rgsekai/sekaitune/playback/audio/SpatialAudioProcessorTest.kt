/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpatialAudioProcessorTest {

    private lateinit var processor: SpatialAudioProcessor

    @Before
    fun setUp() {
        processor = SpatialAudioProcessor()
    }

    @Test
    fun clamp16_handlesBoundariesAndNan() {
        assertEquals(0.toShort(), SpatialAudioProcessor.clamp16(Float.NaN))
        assertEquals(Short.MAX_VALUE, SpatialAudioProcessor.clamp16(2.0f))
        assertEquals(Short.MAX_VALUE, SpatialAudioProcessor.clamp16(1.0f))
        assertEquals(Short.MIN_VALUE, SpatialAudioProcessor.clamp16(-2.0f))
        assertEquals(Short.MIN_VALUE, SpatialAudioProcessor.clamp16(-1.0f))
        assertEquals(0.toShort(), SpatialAudioProcessor.clamp16(0.0f))
        assertEquals(16384.toShort(), SpatialAudioProcessor.clamp16(0.5f))
        assertEquals((-16384).toShort(), SpatialAudioProcessor.clamp16(-0.5f))
    }

    @Test
    fun configure_acceptsStereo16BitPcm() {
        val stereoFormat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val result = processor.configure(stereoFormat)

        assertEquals(44100, result.sampleRate)
        assertEquals(2, result.channelCount)
        assertEquals(C.ENCODING_PCM_16BIT, result.encoding)
    }

    @Test
    fun configure_rejectsMonoOrNon16Bit() {
        val monoFormat = AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(monoFormat))

        val floatFormat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(floatFormat))
    }

    @Test
    fun queueInput_whenDisabled_passesAudioThroughDirectly() {
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.enabled = false

        val frameCount = 10
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * SpatialAudioProcessor.BYTES_PER_FRAME)
            .order(ByteOrder.nativeOrder())

        repeat(frameCount) { i ->
            inputBuffer.putShort((i * 1000).toShort()) // Left
            inputBuffer.putShort((-i * 1000).toShort()) // Right
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertEquals(frameCount * SpatialAudioProcessor.BYTES_PER_FRAME, outputBuffer.remaining())
        repeat(frameCount) { i ->
            assertEquals((i * 1000).toShort(), outputBuffer.short)
            assertEquals((-i * 1000).toShort(), outputBuffer.short)
        }
    }

    @Test
    fun queueInput_whenEnabled_appliesStereoWidening() {
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.enabled = true

        val inputBuffer = ByteBuffer.allocateDirect(SpatialAudioProcessor.BYTES_PER_FRAME)
            .order(ByteOrder.nativeOrder())

        // Feed hard-left panned sample
        inputBuffer.putShort(10000.toShort())
        inputBuffer.putShort(0.toShort())
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertTrue(outputBuffer.hasRemaining())
        val outLeft = outputBuffer.short
        val outRight = outputBuffer.short

        // Out left should be expanded beyond input (10000 * 0.82 = 8200 if mono, but widened side boosts it)
        assertTrue(outLeft > 10000.toShort())
        // Out right should have opposite-phase side component
        assertTrue(outRight < 0.toShort())
    }

    @Test
    fun flush_clearsInternalMemoryWithoutError() {
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.enabled = true

        processor.flush()
        // Ensure subsequent processing runs cleanly
        val inputBuffer = ByteBuffer.allocateDirect(SpatialAudioProcessor.BYTES_PER_FRAME)
            .order(ByteOrder.nativeOrder())
        inputBuffer.putShort(5000.toShort())
        inputBuffer.putShort(5000.toShort())
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val output = processor.output
        assertTrue(output.hasRemaining())
    }
}
