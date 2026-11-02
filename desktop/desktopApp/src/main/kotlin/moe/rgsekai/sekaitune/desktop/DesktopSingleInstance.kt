package moe.rgsekai.sekaitune.desktop

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * Ensures only a single instance of Sekai Tune runs at a time.
 *
 * If an instance is already running when a new process starts (e.g. user clicks the shortcut
 * in the Start Menu), the new process notifies the active instance to bring its window to
 * the front, and then the new process immediately terminates.
 */
internal object DesktopSingleInstance {

    private var lockChannel: FileChannel? = null
    private var fileLock: FileLock? = null
    private var serverSocket: ServerSocket? = null

    private val baseDir: Path by lazy {
        val state = System.getenv("XDG_CACHE_HOME")?.takeIf(String::isNotBlank)
            ?: "${System.getProperty("user.home")}/.cache"
        val dir = File(state, "sekaitune")
        dir.mkdirs()
        dir.toPath()
    }

    private val lockFile: File by lazy { baseDir.resolve("instance.lock").toFile() }
    private val portFile: File by lazy { baseDir.resolve("instance.port").toFile() }

    /**
     * Attempts to acquire the single-instance lock.
     *
     * @return `true` if this is the primary instance and execution should proceed.
     *         `false` if another instance is already running (it has been signaled to focus)
     *         and this process should exit immediately.
     */
    fun acquireOrActivate(): Boolean {
        return try {
            val channel = FileChannel.open(
                lockFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = channel.tryLock()

            if (lock != null) {
                lockChannel = channel
                fileLock = lock

                startServer()

                Runtime.getRuntime().addShutdownHook(Thread {
                    cleanup()
                })

                DesktopTrackLog.log("DesktopSingleInstance: primary instance lock acquired")
                true
            } else {
                channel.close()
                notifyExistingInstance()
                false
            }
        } catch (e: Exception) {
            DesktopTrackLog.log("DesktopSingleInstance: error checking lock: ${e.message}")
            notifyExistingInstance()
            false
        }
    }

    private fun startServer() {
        try {
            val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
            serverSocket = server
            val port = server.localPort

            portFile.writeText(port.toString())

            thread(isDaemon = true, name = "SekaiTune-SingleInstance") {
                while (!server.isClosed) {
                    try {
                        val client = server.accept()
                        client.use { s ->
                            s.soTimeout = 2000
                            val line = s.getInputStream().bufferedReader().readLine()
                            if (line == "ACTIVATE") {
                                DesktopTrackLog.log("DesktopSingleInstance: activation signal received")
                                DesktopWindowVisibility.show()
                                s.getOutputStream().write("OK\n".toByteArray())
                                s.getOutputStream().flush()
                            }
                        }
                    } catch (e: Exception) {
                        // Connection closed or timed out
                    }
                }
            }
        } catch (e: Exception) {
            DesktopTrackLog.log("DesktopSingleInstance: failed to start listener: ${e.message}")
        }
    }

    private fun notifyExistingInstance() {
        DesktopTrackLog.log("DesktopSingleInstance: existing instance found, sending activation signal")
        try {
            if (portFile.exists()) {
                val portStr = portFile.readText().trim()
                val port = portStr.toIntOrNull()
                if (port != null && port in 1..65535) {
                    Socket(InetAddress.getByName("127.0.0.1"), port).use { s ->
                        s.soTimeout = 2000
                        val writer = s.getOutputStream().bufferedWriter()
                        writer.write("ACTIVATE\n")
                        writer.flush()
                        s.getInputStream().bufferedReader().readLine()
                    }
                    DesktopTrackLog.log("DesktopSingleInstance: activation signal sent successfully")
                    return
                }
            }
        } catch (e: Exception) {
            DesktopTrackLog.log("DesktopSingleInstance: failed to notify existing instance: ${e.message}")
        }
    }

    internal fun cleanup() {
        runCatching { serverSocket?.close() }
        runCatching { fileLock?.release() }
        runCatching { lockChannel?.close() }
        runCatching { portFile.delete() }
        runCatching { lockFile.delete() }
    }
}

