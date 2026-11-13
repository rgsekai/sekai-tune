package moe.rgsekai.sekaitune.eq

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import moe.rgsekai.sekaitune.eq.audio.CustomEqualizerAudioProcessor
import moe.rgsekai.sekaitune.eq.data.ParametricEQ
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class EqualizerService @Inject constructor() {

    private val audioProcessors = CopyOnWriteArrayList<CustomEqualizerAudioProcessor>()

    @Volatile private var current: ParametricEQ? = null

    fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.add(processor)
        current?.let(processor::applyProfile) ?: processor.disable()
    }

    fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
        audioProcessors.remove(processor)
    }

    fun apply(eq: ParametricEQ?) {
        current = eq
        audioProcessors.forEach { processor ->
            if (eq != null) processor.applyProfile(eq) else processor.disable()
        }
    }

    fun disable() = apply(null)

    fun isEnabled(): Boolean = current != null

    fun release() {
        audioProcessors.clear()
    }
}

