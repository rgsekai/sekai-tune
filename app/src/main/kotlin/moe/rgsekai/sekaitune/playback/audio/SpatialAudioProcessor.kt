/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Real-time Spatial Audio and Stereo Widener DSP.
 *
 * Implements a high-efficiency psychoacoustic virtualizer:
 * 1. Mid/Side separation with adjustable lateral widening ([widthGain] = 2.5x).
 * 2. Inter-aural time delay circular buffer ([delayMs] = 15ms) simulating head acoustic shadow.
 * 3. Single-pole low-pass filter ([lowpassCoeff] = 0.3) for natural head absorption.
 * 4. Crossfeed blending ([crossfeedGain] = 0.2) to simulate natural room acoustic bleed in headphones.
 * 5. Headroom preservation with makeup gain ([outputGain] = 0.82x).
 *
 * Runs in O(1) per sample with zero allocations during playback.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    var widthGain: Float = 2.5f
    var outputGain: Float = 0.82f
    var crossfeedGain: Float = 0.2f
    var lowpassCoeff: Float = 0.3f
    var delayMs: Int = 15

    private var sampleRate: Int = 0
    private var channelCount: Int = 0

    private var delayLeft = FloatArray(0)
    private var delayRight = FloatArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    fun setupDspBuffers(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        if (channelCount != 2 || sampleRate <= 0) {
            delayLeft = FloatArray(0)
            delayRight = FloatArray(0)
            delayIndex = 0
            lowpassLeft = 0f
            lowpassRight = 0f
            return
        }
        val delaySamples = (sampleRate * delayMs / 1000f)
            .roundToInt()
            .coerceAtLeast(1)
        if (delayLeft.size != delaySamples) {
            delayLeft = FloatArray(delaySamples)
            delayRight = FloatArray(delaySamples)
        }
        onFlush()
    }

    public override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        setupDspBuffers(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        return inputAudioFormat
    }

    public override fun onFlush() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    override fun onReset() {
        delayLeft = FloatArray(0)
        delayRight = FloatArray(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        channelCount = 0
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameCount = inputBuffer.remaining() / BYTES_PER_FRAME
        if (frameCount == 0) return
        val outputBuffer = replaceOutputBuffer(frameCount * BYTES_PER_FRAME)

        if (!enabled) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val delaySize = delayLeft.size
        if (delaySize == 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight
        val invScale = 1.0f / 32768.0f

        repeat(frameCount) {
            val left = inputBuffer.short.toFloat() * invScale
            val right = inputBuffer.short.toFloat() * invScale

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[dIdx]
            val delayedLeft = delayLeft[dIdx]
            lpL += lowpassCoeff * (delayedRight - lpL)
            lpR += lowpassCoeff * (delayedLeft - lpR)
            widenedLeft += lpL * crossfeedGain
            widenedRight += lpR * crossfeedGain

            delayLeft[dIdx] = left
            delayRight[dIdx] = right
            dIdx = (dIdx + 1) % delaySize

            outputBuffer.putShort(clamp16(widenedLeft * outputGain))
            outputBuffer.putShort(clamp16(widenedRight * outputGain))
        }
        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR

        outputBuffer.flip()
    }

    companion object {
        const val BYTES_PER_FRAME = 4 // Stereo 16-bit PCM (2 channels * 2 bytes)

        fun clamp16(sample: Float): Short {
            if (sample.isNaN()) return 0
            val scaled = sample * 32768.0f
            if (scaled <= -32768.0f) return Short.MIN_VALUE
            if (scaled >= 32767.0f) return Short.MAX_VALUE
            return Math.round(scaled).toShort()
        }
    }
}
