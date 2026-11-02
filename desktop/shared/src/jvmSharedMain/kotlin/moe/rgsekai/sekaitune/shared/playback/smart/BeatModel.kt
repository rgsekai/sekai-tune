package moe.rgsekai.sekaitune.shared.playback.smart

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.min

internal actual class BeatModel actual constructor(
    private val modelPath: () -> String,
    private val inferenceThreads: () -> Int,
) {

    @Volatile private var session: OrtSession? = null
    @Volatile private var sessionThreads = 0
    @Volatile private var sessionFailed = false
    private val lock = Any()

    /** Parsing the graph is far too expensive to repeat per track, so one session is kept. */
    private fun session(): OrtSession? {
        if (sessionFailed) return null
        val threads = inferenceThreads()
        session?.takeIf { sessionThreads == threads }?.let { return it }
        synchronized(lock) {
            if (sessionFailed) return null
            session?.takeIf { sessionThreads == threads }?.let { return it }
            runCatching { session?.close() }
            session = null
            return runCatching {
                val file = File(modelPath())
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(threads)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // ORT's arena allocator keeps every block it has ever needed, which for this
                    // graph is tens of megabytes of native heap retained for the life of the
                    // session, far past the model's own size, on a process that also has to
                    // survive in the background. Analysis runs a handful of times per track, so
                    // allocating per run is the right trade.
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                OrtEnvironment.getEnvironment().createSession(file.absolutePath, options)
                    .also {
                        session = it
                        sessionThreads = threads
                    }
            }.onFailure {
                sessionFailed = true
                AnalysisLog.warn("Beat model unavailable; falling back to no grid", it)
            }
                .getOrNull()
        }
    }

    actual fun infer(pcm: FloatArray): BeatLogits? {
        val melStarted = System.currentTimeMillis()
        val spectrogram = MelSpectrogram.compute(pcm) ?: return null
        val melMs = System.currentTimeMillis() - melStarted
        val active = session() ?: return null

        val beatLogits = FloatArray(spectrogram.frames)
        val downbeatLogits = FloatArray(spectrogram.frames)
        val inferStarted = System.currentTimeMillis()
        if (!infer(active, spectrogram, beatLogits, downbeatLogits)) return null
        AnalysisLog.warn("mel ${melMs}ms (${spectrogram.frames} frames) " +
                "infer ${System.currentTimeMillis() - inferStarted}ms",
        )
        return BeatLogits(beatLogits, downbeatLogits, MelSpectrogram.frameRate)
    }

    /**
     * Runs the model over the spectrogram in chunks, writing logits into the output arrays.
     *
     * The model reads 1500-frame chunks and has no context at their edges, so BORDER_FRAMES are
     * discarded from each side and chunks advance by the difference. The first and last chunk keep
     * their outer border, since there is no neighbouring chunk to supply it.
     */
    @Suppress("UNCHECKED_CAST")
    private fun infer(
        session: OrtSession,
        spectrogram: MelSpectrogram.Spectrogram,
        beatLogits: FloatArray,
        downbeatLogits: FloatArray,
    ): Boolean = runCatching {
        val environment = OrtEnvironment.getEnvironment()
        val mels = spectrogram.mels
        val name = session.inputNames.first()
        val stride = BeatTracker.CHUNK_FRAMES - 2 * BeatTracker.BORDER_FRAMES

        var start = 0
        while (start < spectrogram.frames) {
            val length = min(BeatTracker.CHUNK_FRAMES, spectrogram.frames - start)
            // A chunk shorter than the border padding carries no usable centre.
            if (length <= 2 * BeatTracker.BORDER_FRAMES && start > 0) break

            val chunk = spectrogram.values.copyOfRange(start * mels, (start + length) * mels)
            val shape = longArrayOf(1, length.toLong(), mels.toLong())

            OnnxTensor.createTensor(environment, FloatBuffer.wrap(chunk), shape).use { tensor ->
                session.run(mapOf(name to tensor)).use { outputs ->
                    val beat = (outputs.get(0).value as Array<FloatArray>)[0]
                    val downbeat = (outputs.get(1).value as Array<FloatArray>)[0]

                    val keepFrom = if (start == 0) 0 else BeatTracker.BORDER_FRAMES
                    val keepTo = if (start + length >= spectrogram.frames) length else length - BeatTracker.BORDER_FRAMES
                    for (index in keepFrom until keepTo) {
                        val target = start + index
                        if (target >= beatLogits.size) break
                        beatLogits[target] = beat[index]
                        downbeatLogits[target] = downbeat[index]
                    }
                }
            }

            if (start + length >= spectrogram.frames) break
            start += stride
        }
        true
    }.onFailure { AnalysisLog.warn("Beat inference failed", it) }.getOrDefault(false)

    actual fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
            sessionThreads = 0
        }
    }
}
