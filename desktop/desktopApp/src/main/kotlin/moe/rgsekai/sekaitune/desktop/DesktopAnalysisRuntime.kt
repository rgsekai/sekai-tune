package moe.rgsekai.sekaitune.desktop

import okio.Path.Companion.toOkioPath
import moe.rgsekai.sekaitune.shared.playback.smart.AnalysisLog
import moe.rgsekai.sekaitune.shared.playback.smart.NativeAnalysisLibrary
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/** Unpacks what the analyser needs out of the jar, once per install. */
internal object DesktopAnalysisRuntime {

    private val started = AtomicBoolean(false)

    /** Where unpacked resources live, alongside the desktop log. */
    private val home: Path by lazy {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: "${System.getProperty("user.home")}/.local/share"
        Path.of(base, "Sekai Tune", "analysis")
    }

    /** Where unpacked resources and cached analyses live. */
    fun analysisHome(): java.io.File = home.toFile().also { Files.createDirectories(home) }

    @Volatile
    private var ortChecked = false
    @Volatile
    private var ortAvailable = false
    @Volatile
    private var ortFailureReason: String? = null

    val isOrtAvailable: Boolean
        get() {
            ensureStarted()
            if (!ortChecked) {
                synchronized(started) {
                    if (!ortChecked) {
                        try {
                            val options = ai.onnxruntime.OrtSession.SessionOptions()
                            options.close()
                            ortAvailable = true
                        } catch (t: Throwable) {
                            ortAvailable = false
                            val hint = hintFor(t)
                            val msg = (t.message ?: t::class.java.simpleName) + hint
                            ortFailureReason = msg
                            DesktopTrackLog.log("automix: beat analysis library unavailable ($msg) — automix disabled")
                        } finally {
                            ortChecked = true
                        }
                    }
                }
            }
            return ortAvailable
        }

    val failureReason: String?
        get() {
            ensureStarted()
            return if (!NativeAnalysisLibrary.available) {
                "Native analysis library unavailable"
            } else if (!isOrtAvailable) {
                ortFailureReason ?: "Beat analysis library unavailable"
            } else null
        }

    /** True once the native analyser and beat model runtime are loaded and usable. */
    val available: Boolean get() = ensureStarted().let { NativeAnalysisLibrary.available && isOrtAvailable }

    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        if (DesktopPlatform.isWindows) {
            val javaHome = System.getProperty("java.home")
            if (!javaHome.isNullOrBlank()) {
                val bin = Path.of(javaHome, "bin")
                listOf("vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll").forEach { dll ->
                    val p = bin.resolve(dll)
                    if (Files.exists(p)) {
                        runCatching { System.load(p.toAbsolutePath().toString()) }
                    }
                }
            }
        }
        AnalysisLog.install { message, error ->
            DesktopTrackLog.log(
                when {
                    error == null -> message
                    else -> "$message: ${error.message}" + hintFor(error)
                },
            )
        }
        // Karaoke's vocal model ships in the jar like Automix's; where builds that
        // downloaded one kept it goes.
        home.parent.resolve("models").toFile().deleteRecursively()
        moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeModel.install {
            modelPath(moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeModel.ASSET)
        }
        moe.rgsekai.sekaitune.shared.playback.karaoke.KaraokeStems.install(home.parent.resolve("karaoke-cache").toOkioPath())
        NativeAnalysisLibrary.install {
            // `mapLibraryName` gives `libsekai_analysis.so` on Linux and `sekai_analysis.dll`
            // on Windows.
            val fileName = System.mapLibraryName(NativeAnalysisLibrary.LIBRARY_NAME)
            System.load(unpack("/native/$fileName", fileName).toString())
        }
    }

    /** A sentence worth adding to a failure whose real cause is off-screen. */
    private fun hintFor(error: Throwable): String {
        if (!DesktopPlatform.isWindows) return ""
        val text = generateSequence(error, Throwable::cause).mapNotNull { it.message }.joinToString(" ")
        val missingRuntime = "dependent libraries" in text ||
            "OrtSession" in text ||
            "onnxruntime" in text && "UnsatisfiedLink" in error::class.java.simpleName
        return if (missingRuntime) {
            " — install the Microsoft Visual C++ Redistributable (x64); ONNX Runtime is built against it"
        } else {
            ""
        }
    }

    /**
     * Unpacks and loads a native library that travels in the jar.
     *
     * The analyser's own load goes through [NativeAnalysisLibrary]; this is the
     * same unpack for anything else that ships beside it, so there is one place
     * that knows where the jar keeps them.
     */
    fun loadNative(name: String) {
        val fileName = System.mapLibraryName(name)
        System.load(unpack("/native/$fileName", fileName).toString())
    }

    /** The absolute path of a model, unpacked on first use. */
    fun modelPath(asset: String): String = unpack("/models/$asset", asset).toString()

    /**
     * Copies a classpath resource to [name] under [home], skipping the copy when the file already
     * there is the same size and no older than the resource.
     *
     * Size alone is not enough: a rebuilt library can come out byte-for-byte the same length as the
     * one it replaces, and the stale copy was then loaded forever — a native fix that never ran.
     */
    private fun unpack(resource: String, name: String): Path {
        Files.createDirectories(home)
        val target = home.resolve(name)
        val stream = javaClass.getResourceAsStream(resource)
            ?: error("$resource is missing from this build")
        stream.use { input ->
            val connection = javaClass.getResource(resource)?.openConnection()
            val expected = connection?.contentLengthLong ?: -1L
            val builtAt = connection?.lastModified ?: 0L
            if (Files.exists(target) && expected > 0 && Files.size(target) == expected &&
                Files.getLastModifiedTime(target).toMillis() >= builtAt
            ) {
                return target
            }
            val partial = home.resolve("$name.partial")
            Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING)
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }
}
