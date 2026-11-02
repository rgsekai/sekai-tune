package moe.rgsekai.sekaitune.desktop

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import moe.rgsekai.sekaitune.shared.playback.smart.BeatTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Whether the Beat This! */
class DesktopBeatModelTest {

    @Test
    fun `the beat model loads in this build`() {
        DesktopAnalysisRuntime.ensureStarted()
        if (!DesktopAnalysisRuntime.available) {
            println("analysis library unavailable — skipping")
            return
        }
        val model = File(DesktopAnalysisRuntime.modelPath(BeatTracker.MODEL_ASSET))
        assertTrue(model.isFile && model.length() > 0, "the model was not unpacked")

        val outcome = runCatching {
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setCPUArenaAllocator(false)
                setMemoryPatternOptimization(false)
            }
            OrtEnvironment.getEnvironment().createSession(model.absolutePath, options).use { session ->
                session.inputNames.first()
            }
        }
        if (outcome.isFailure) {
            val err = outcome.exceptionOrNull()
            val text = generateSequence(err, Throwable::cause).mapNotNull { it?.message }.joinToString(" ")
            if ("initialization routine failed" in text || "UnsatisfiedLink" in (err?.javaClass?.simpleName ?: "")) {
                println("onnxruntime native unavailable on this test JVM: $text — skipping")
                return
            }
        }
        outcome.exceptionOrNull()?.let { println("beat model load failed: ${it.message}") }
        assertTrue(
            outcome.isSuccess,
            "the beat model would not load, so Automix has no meter: ${outcome.exceptionOrNull()?.message}",
        )
    }
}
