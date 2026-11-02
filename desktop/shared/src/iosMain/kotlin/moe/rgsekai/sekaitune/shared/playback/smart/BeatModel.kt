package moe.rgsekai.sekaitune.shared.playback.smart

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

/** Mel front end and ONNX Runtime run in Swift ([IosAutomix.native]); this only moves the logits across. */
@OptIn(ExperimentalForeignApi::class)
internal actual class BeatModel actual constructor(private val modelPath: () -> String, private val inferenceThreads: () -> Int) {
    actual fun infer(pcm: FloatArray): BeatLogits? {
        val native = IosAutomix.native ?: return null
        val out = pcm.usePinned { native.beatLogits(modelPath(), inferenceThreads(), it.addressOf(0), pcm.size) } ?: return null
        val beat = FloatArray(out.frames)
        val downbeat = FloatArray(out.frames)
        beat.usePinned { b -> downbeat.usePinned { d -> out.copy(b.addressOf(0), d.addressOf(0)) } }
        return BeatLogits(beat, downbeat, FRAME_RATE)
    }

    actual fun release() = Unit

    private companion object {
        /** 22,050 Hz over a hop of 441: the model's 50 frames a second. */
        const val FRAME_RATE = 50.0
    }
}
