package moe.rgsekai.sekaitune.playback.audio

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import moe.rgsekai.sekaitune.eq.AudioEffectsService
import moe.rgsekai.sekaitune.eq.audio.ReplayGainTags
import moe.rgsekai.sekaitune.eq.audio.VolumeCurve
import java.nio.ByteBuffer

@UnstableApi
class AudioEffectsClockSink(
    delegate: AudioSink,
    private val audioEffectsService: AudioEffectsService? = null,
    val replayGain: ReplayGainTags = ReplayGainTags(),
) : ForwardingAudioSink(delegate) {

    private var requestedVolume = 1f
    private var appliedCurve: VolumeCurve? = null
    private var appliedReplayGain = 1f

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (audioEffectsService != null && (audioEffectsService.volumeCurve !== appliedCurve || replayGainFactor() != appliedReplayGain)) {
            applyVolume()
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun setVolume(volume: Float) {
        requestedVolume = volume
        applyVolume()
    }

    fun applyVolume() {
        val curve = audioEffectsService?.volumeCurve
        appliedCurve = curve
        appliedReplayGain = replayGainFactor()
        val shaped = (curve?.gain(requestedVolume) ?: requestedVolume) * appliedReplayGain
        super.setVolume(shaped)
    }

    private fun replayGainFactor(): Float {
        val service = audioEffectsService ?: return 1f
        return replayGain.linear(service.replayGain, service.normalize)
    }
}

