package moe.rgsekai.sekaitune.shared.playback.karaoke

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * The vocal-separation model, shipped inside the app on every platform:
 * UVR-MDX-NET 9482 from Ultimate Vocal Remover's public release (MIT), 30 MB.
 * Karaoke works the first time and offline. Each platform says how to get a
 * readable copy of its bundled file with [install].
 */
object KaraokeModel {

    /** The file's name in the app's assets — and the desktop jar's `models/`. */
    const val ASSET = "UVR_MDXNET_9482.onnx"

    val model = MdxModel.MDX_9482

    private val lock = SynchronizedObject()

    @Volatile
    private var locate: (() -> String)? = null

    @Volatile
    private var path: String? = null

    /** How this platform gets the model's file. Called at most once, off the main thread. */
    fun install(locate: () -> String) {
        this.locate = locate
    }

    val installed: Boolean get() = locate != null

    /** The model's file, unpacked on the first call. Worker thread only. */
    fun path(): String = path ?: synchronized(lock) {
        path ?: requireNotNull(locate) { "karaoke model not installed" }().also { path = it }
    }
}
