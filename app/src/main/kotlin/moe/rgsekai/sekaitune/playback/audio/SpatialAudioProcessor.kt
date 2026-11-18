/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.eq.AudioEffectsService
import moe.rgsekai.sekaitune.eq.audio.SpatialMode
import moe.rgsekai.sekaitune.eq.audio.SpatialParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh

@UnstableApi
@Suppress("DEPRECATION")
class SpatialAudioProcessor(
    private val service: AudioEffectsService? = null,
) : AudioProcessor {

    @Volatile
    private var localEnabled: Boolean = false

    var enabled: Boolean
        get() = service?.spatial?.enabled ?: localEnabled
        set(value) {
            localEnabled = value
            service?.let { s ->
                s.spatial = s.spatial.copy(enabled = value)
            }
        }

    @Volatile
    var outputAllowed: Boolean = true

    var widthGain: Float = 2.5f
    var outputGain: Float = 0.82f
    var crossfeedGain: Float = 0.2f
    var lowpassCoeff: Float = 0.3f
    var delayMs: Int = 15

    private var sampleRate: Int = 0
    private var inChannels: Int = 0
    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var pendingSurround = false
    private var surround = false

    private var delayLeft = FloatArray(0)
    private var delayRight = FloatArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var head: HeadRenderer? = null
    private var headAngle = Float.NaN

    fun setupDspBuffers(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.inChannels = channelCount
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
        flush()
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val params = currentParams()
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return notSet()
        sampleRate = inputAudioFormat.sampleRate
        inChannels = inputAudioFormat.channelCount
        setupDspBuffers(sampleRate, inChannels)
        return when (inChannels) {
            2 -> {
                pendingSurround = false
                pendingFormat = inputAudioFormat
                inputAudioFormat
            }
            in 3..8 -> if (params.surround) {
                pendingSurround = true
                pendingFormat = inputAudioFormat
                AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
            } else notSet()
            else -> notSet()
        }
    }

    private fun notSet(): AudioProcessor.AudioFormat {
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun isActive(): Boolean = pendingFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        if (inChannels == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val frameCount = bytes / (if (surround) 2 * inChannels else BYTES_PER_FRAME)
        if (frameCount == 0) return

        val params = currentParams()
        val isEffectivelyEnabled = params.enabled && outputAllowed

        val out = replaceOutputBuffer(frameCount * BYTES_PER_FRAME)

        // Bypass check: if not enabled, not mono, and not surround, pass through directly
        if (!isEffectivelyEnabled && !params.mono && !surround) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        out.order(ByteOrder.nativeOrder())

        val invScale = 1.0f / 32768.0f

        if (surround) {
            val renderer = head ?: HeadRenderer(sampleRate, surroundAzimuths(inChannels)).also { head = it }
            val frame = FloatArray(inChannels)
            for (f in 0 until frameCount) {
                for (c in 0 until inChannels) frame[c] = inputBuffer.short * invScale
                renderer.render(frame)
                out.putShort(clamp16(renderer.left * SurroundGain))
                out.putShort(clamp16(renderer.right * SurroundGain))
            }
            out.flip()
            return
        }

        if (params.mode == SpatialMode.Speakers && isEffectivelyEnabled) {
            val angle = params.speakerAngle.coerceIn(5f, 90f)
            val renderer = head?.takeIf { headAngle == angle }
                ?: HeadRenderer(sampleRate, floatArrayOf(-angle, angle)).also { head = it; headAngle = angle }
            val frame = FloatArray(2)
            for (f in 0 until frameCount) {
                var l = inputBuffer.short * invScale
                var r = inputBuffer.short * invScale
                if (params.mono) {
                    val m = (l + r) * 0.5f
                    l = m
                    r = m
                }
                frame[0] = l
                frame[1] = r
                renderer.render(frame)
                out.putShort(clamp16(renderer.left * SpeakerGain))
                out.putShort(clamp16(renderer.right * SpeakerGain))
            }
            out.flip()
            return
        }

        // Stereo Widening + Crossfeed DSP (Mode = Crossfeed or Default)
        val delaySize = delayLeft.size
        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight

        for (f in 0 until frameCount) {
            var left = inputBuffer.short * invScale
            var right = inputBuffer.short * invScale

            if (params.mono) {
                val m = (left + right) * 0.5f
                left = m
                right = m
            }

            if (isEffectivelyEnabled) {
                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f * widthGain
                var widenedLeft = mid + side
                var widenedRight = mid - side

                if (delaySize > 0) {
                    val delayedRight = delayRight[dIdx]
                    val delayedLeft = delayLeft[dIdx]
                    lpL += lowpassCoeff * (delayedRight - lpL)
                    lpR += lowpassCoeff * (delayedLeft - lpR)
                    widenedLeft += lpL * crossfeedGain
                    widenedRight += lpR * crossfeedGain

                    delayLeft[dIdx] = left
                    delayRight[dIdx] = right
                    dIdx = (dIdx + 1) % delaySize
                }

                out.putShort(clamp16(widenedLeft * outputGain))
                out.putShort(clamp16(widenedRight * outputGain))
            } else {
                out.putShort(clamp16(left))
                out.putShort(clamp16(right))
            }
        }

        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR

        out.flip()
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in Java")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        val format = pendingFormat
        if (format != AudioProcessor.AudioFormat.NOT_SET) {
            sampleRate = format.sampleRate
            inChannels = format.channelCount
            surround = pendingSurround
        }
        if (delayLeft.isNotEmpty()) delayLeft.fill(0f)
        if (delayRight.isNotEmpty()) delayRight.fill(0f)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        head?.clear()
    }

    override fun reset() {
        flush()
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        sampleRate = 0
        inChannels = 0
        surround = false
        delayLeft = FloatArray(0)
        delayRight = FloatArray(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        head = null
        headAngle = Float.NaN
    }

    private fun currentParams(): SpatialParams =
        service?.spatial ?: SpatialParams(enabled = localEnabled)

    private class HeadRenderer(private val sampleRate: Int, azimuthsDeg: FloatArray) {
        private val count = azimuthsDeg.size
        private val delayL = IntArray(count)
        private val delayR = IntArray(count)
        private val coeffL = FloatArray(count)
        private val coeffR = FloatArray(count)
        private val gainL = FloatArray(count)
        private val gainR = FloatArray(count)
        private val buffers = Array(count) { FloatArray(192) }
        private var writePos = 0
        private val lpL = FloatArray(count)
        private val lpR = FloatArray(count)
        var left = 0f
            private set
        var right = 0f
            private set

        init {
            val headRadiusM = 0.0875f
            val soundSpeed = 343f
            for (i in 0 until count) {
                val deg = azimuthsDeg[i]
                if (deg.isNaN()) continue
                val rad = (deg * PI / 180.0).toFloat()
                val sinTheta = sin(rad)
                val itdSec = (headRadiusM / soundSpeed) * (sinTheta + (if (sinTheta >= 0) (rad.coerceIn(0f, PI.toFloat())) else (rad.coerceIn(-PI.toFloat(), 0f))))
                val dSamples = (itdSec * sampleRate).roundToInt()
                if (dSamples >= 0) {
                    delayL[i] = 0
                    delayR[i] = dSamples.coerceIn(0, 180)
                } else {
                    delayL[i] = (-dSamples).coerceIn(0, 180)
                    delayR[i] = 0
                }
                val shadow = (1f - sinTheta) * 0.5f
                val cutoffL = 900f + 3500f * (1f - shadow)
                val cutoffR = 900f + 3500f * shadow
                coeffL[i] = 1f - exp(-2f * PI.toFloat() * cutoffL / sampleRate)
                coeffR[i] = 1f - exp(-2f * PI.toFloat() * cutoffR / sampleRate)
                gainL[i] = 0.6f + 0.4f * (1f - shadow)
                gainR[i] = 0.6f + 0.4f * shadow
            }
        }

        fun clear() {
            for (b in buffers) b.fill(0f)
            lpL.fill(0f)
            lpR.fill(0f)
            writePos = 0
            left = 0f
            right = 0f
        }

        fun render(frame: FloatArray) {
            val len = 192
            val mask = len - 1
            var accL = 0f
            var accR = 0f
            for (i in 0 until count) {
                val x = if (i < frame.size) frame[i] else 0f
                buffers[i][writePos] = x
                val posL = (writePos - delayL[i] + len) and mask
                val posR = (writePos - delayR[i] + len) and mask
                val sL = buffers[i][posL]
                val sR = buffers[i][posR]
                lpL[i] += coeffL[i] * (sL - lpL[i])
                lpR[i] += coeffR[i] * (sR - lpR[i])
                accL += lpL[i] * gainL[i]
                accR += lpR[i] * gainR[i]
            }
            writePos = (writePos + 1) and mask
            left = accL
            right = accR
        }
    }

    companion object {
        const val BYTES_PER_FRAME = 4

        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
        private const val SpeakerGain = 0.6f
        private const val SurroundGain = 0.45f

        fun clamp16(sample: Float): Short {
            if (sample.isNaN()) return 0
            val scaled = sample * 32768.0f
            if (scaled <= -32768.0f) return Short.MIN_VALUE
            if (scaled >= 32767.0f) return Short.MAX_VALUE
            return Math.round(scaled).toShort()
        }

        private fun surroundAzimuths(channels: Int): FloatArray = when (channels) {
            3 -> floatArrayOf(-30f, 30f, 0f)
            4 -> floatArrayOf(-30f, 30f, -110f, 110f)
            5 -> floatArrayOf(-30f, 30f, 0f, -110f, 110f)
            6 -> floatArrayOf(-30f, 30f, 0f, Float.NaN, -110f, 110f)
            7 -> floatArrayOf(-30f, 30f, 0f, Float.NaN, 180f, -90f, 90f)
            else -> floatArrayOf(-30f, 30f, 0f, Float.NaN, -150f, 150f, -90f, 90f)
        }
    }
}
