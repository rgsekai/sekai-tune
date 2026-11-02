package moe.rgsekai.sekaitune.shared.playback.smart

/** The Beat This! heads over a whole clip: one logit per mel frame, at [frameRate] frames a second. */
internal class BeatLogits(val beat: FloatArray, val downbeat: FloatArray, val frameRate: Double)

/**
 * Mel front end plus ONNX inference for [BeatTracker]; null when either is unavailable.
 * JVM runs ONNX Runtime; iOS has no runtime yet.
 */
internal expect class BeatModel(modelPath: () -> String, inferenceThreads: () -> Int) {
    fun infer(pcm: FloatArray): BeatLogits?
    fun release()
}
