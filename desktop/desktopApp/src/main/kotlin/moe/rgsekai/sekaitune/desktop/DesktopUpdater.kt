package moe.rgsekai.sekaitune.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import kotlin.system.exitProcess

/**
 * Downloads a newer release in the background and swaps it in on "Restart to update".
 *
 * Only for installs it can replace without asking for anything: the Windows per-user install
 * (its MSI, run quietly), the macOS app bundle, and a Linux AppImage. A `.deb`/`.rpm` needs root
 * and a `gradle run` has nothing to replace, so those keep [DesktopUpdateChecker]'s
 * open-the-installer prompt.
 *
 * Nothing is installed unless its SHA-256 matches the release's `SHA256SUMS` (written by
 * .github/workflows/release.yml): a release without one, or a download that differs from it, is
 * never run. The swap itself is a small script started just before Sekai Tune quits; it waits for this
 * process to end, installs, and opens the new version.
 */
internal object DesktopUpdater {

    private val persistence = DesktopPersistence()

    private val _automatic = MutableStateFlow(persistence.boolean(KEY_AUTOMATIC, true))

    /** Whether updates are downloaded in the background and offered as a restart. */
    val automatic: StateFlow<Boolean> = _automatic.asStateFlow()

    fun setAutomatic(value: Boolean) {
        persistence.saveBoolean(KEY_AUTOMATIC, value)
        _automatic.value = value
    }

    /** A verified download, ready to install. */
    class Ready(val version: String, val notes: String?, internal val file: File, internal val target: Target)

    /** What this copy of Sekai Tune is, when it is something the updater can replace in place. */
    sealed class Target(val assetSuffix: String) {
        class Windows(val launcher: File) : Target("-windows-x64.msi")
        class Mac(val bundle: File) : Target(if (isArm64) "-macos-arm64.zip" else "-macos-x64.zip")
        class AppImage(val file: File) : Target("-linux-x86_64.AppImage")
    }

    private val isArm64 = System.getProperty("os.arch").orEmpty().lowercase().let { it == "aarch64" || it == "arm64" }

    /**
     * The running install, from the packaged launcher's own path (`jpackage.app-path`) or, failing
     * that, the bundled runtime's place inside it. Null for anything else.
     */
    internal fun target(): Target? = runCatching {
        val launcher = System.getProperty("jpackage.app-path")?.let(::File)
        val javaHome = File(System.getProperty("java.home").orEmpty())
        when {
            DesktopPlatform.isWindows -> {
                // <install>\runtime is the bundled JRE; the launcher sits beside it.
                val exe = launcher ?: javaHome.parentFile?.parentFile?.resolve("Sekai Tune.exe")
                exe?.takeIf { it.isFile }?.let(Target::Windows)
            }
            DesktopPlatform.isMac -> {
                val bundle = generateSequence(launcher ?: javaHome) { it.parentFile }
                    .firstOrNull { it.name.endsWith(".app") }
                // Replacing it means renaming it, which needs its folder to be writable.
                bundle?.takeIf { it.isDirectory && it.parentFile?.canWrite() == true }?.let(Target::Mac)
            }
            DesktopPlatform.isLinux -> System.getenv("APPIMAGE")?.let(::File)
                ?.takeIf { it.isFile && it.parentFile?.canWrite() == true }
                ?.let(Target::AppImage)
            else -> null
        }
    }.getOrNull()

    /**
     * Fetches and verifies the installer for [update], or returns null when this install can't be
     * updated in place, the release has no checksum for it, or anything fails. A copy already
     * downloaded by an earlier launch is reused when it still matches.
     */
    suspend fun download(update: DesktopUpdateChecker.UpdateInfo): Ready? = withContext(Dispatchers.IO) {
        runCatching {
            val target = target() ?: return@runCatching null
            val (name, url) = update.assets.entries
                .firstOrNull { it.key.endsWith(target.assetSuffix, ignoreCase = true) }
                ?.toPair() ?: return@runCatching null
            val sumsUrl = update.assets[SUMS_ASSET] ?: return@runCatching null
            val expected = http.send(request(sumsUrl), HttpResponse.BodyHandlers.ofString())
                .takeIf { it.statusCode() == 200 }?.body()
                ?.let { expectedHash(it, name) }
                ?: return@runCatching null

            val dir = File(System.getProperty("java.io.tmpdir"), "Sekai Tune-update").apply { mkdirs() }
            // Older downloads are of no further use.
            dir.listFiles()?.filter { it.name != name }?.forEach { it.deleteRecursively() }
            val file = File(dir, name)
            if (!file.isFile || sha256(file) != expected) {
                val part = File(dir, "$name.part")
                val response = http.send(request(url), HttpResponse.BodyHandlers.ofFile(part.toPath()))
                if (response.statusCode() != 200) {
                    part.delete()
                    return@runCatching null
                }
                java.nio.file.Files.move(part.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (sha256(file) != expected) {
                DesktopTrackLog.log("update: $name does not match SHA256SUMS; not installing it")
                file.delete()
                return@runCatching null
            }
            Ready(update.version, update.notes, file, target)
        }.onFailure { DesktopTrackLog.log("update: download failed: ${it.message}") }.getOrNull()
    }

    /**
     * Starts the install script, runs [beforeExit] (the same native teardown the tray's Quit
     * does) and quits. The script waits for this process to be gone before it touches anything.
     */
    fun installAndRestart(ready: Ready, beforeExit: () -> Unit) {
        val pid = ProcessHandle.current().pid().toString()
        val dir = ready.file.parentFile
        val log = File(dir, "install.log")
        val command = when (val target = ready.target) {
            is Target.Windows -> {
                val script = File(dir, "install.cmd").apply { writeText(WINDOWS_SCRIPT) }
                // `start` detaches it and keeps its console minimised. Its first quoted argument is
                // the window title — Java quotes this one because of the space.
                listOf("cmd", "/c", "start", "Sekai Tune update", "/min", "cmd", "/c", script.absolutePath,
                    pid, ready.file.absolutePath, target.launcher.absolutePath)
            }
            is Target.Mac -> {
                val script = File(dir, "install.sh").apply { writeText(MAC_SCRIPT) }
                listOf("/bin/sh", script.absolutePath, pid, ready.file.absolutePath, target.bundle.absolutePath)
            }
            is Target.AppImage -> {
                val script = File(dir, "install.sh").apply { writeText(APPIMAGE_SCRIPT) }
                listOf("/bin/sh", script.absolutePath, pid, ready.file.absolutePath, target.file.absolutePath)
            }
        }
        val started = runCatching {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                .start()
        }
        if (started.isFailure) {
            DesktopTrackLog.log("update: could not start the installer: ${started.exceptionOrNull()?.message}")
            return
        }
        beforeExit()
        exitProcess(0)
    }

    /**
     * [name]'s hash in a `sha256sum` listing (`<hex>  <name>`, or `<hex> *<name>` in binary mode),
     * or null when it isn't listed or the hash isn't a SHA-256 — which means "don't install".
     */
    internal fun expectedHash(sums: String, name: String): String? =
        sums.lineSequence()
            .map { it.trim().split(Regex("\\s+"), limit = 2) }
            .firstOrNull { it.size == 2 && it[1].removePrefix("*") == name }
            ?.get(0)?.lowercase()
            ?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }

    private fun request(url: String) = HttpRequest.newBuilder(URI(url))
        .timeout(Duration.ofMinutes(10))
        .header("User-Agent", "SekaiTune/${DesktopUpdateChecker.currentVersion}")
        .build()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // GitHub's asset links redirect to its storage host.
    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private const val KEY_AUTOMATIC = "auto_update"
    private const val SUMS_ASSET = "SHA256SUMS"

    /** Args: pid, msi, launcher. Per-user MSI, so no elevation; same upgrade code, so it upgrades. */
    private val WINDOWS_SCRIPT = """
        @echo off
        :wait
        tasklist /FI "PID eq %1" 2>NUL | find "%1" >NUL && (timeout /t 1 /nobreak >NUL & goto wait)
        msiexec /i "%~2" /qn /norestart
        start "" "%~3"
    """.trimIndent().replace("\n", "\r\n")

    /** Args: pid, zip, bundle. The old bundle is put back if the new one can't be moved in. */
    private val MAC_SCRIPT = """
        #!/bin/sh
        pid="${'$'}1"; zip="${'$'}2"; app="${'$'}3"
        while kill -0 "${'$'}pid" 2>/dev/null; do sleep 0.5; done
        stage="${'$'}(mktemp -d)"
        ditto -x -k "${'$'}zip" "${'$'}stage" || { open "${'$'}app"; exit 1; }
        new="${'$'}(find "${'$'}stage" -maxdepth 1 -name '*.app' | head -n 1)"
        [ -d "${'$'}new" ] || { open "${'$'}app"; exit 1; }
        old="${'$'}app.previous"
        rm -rf "${'$'}old"
        if mv "${'$'}app" "${'$'}old"; then
            if mv "${'$'}new" "${'$'}app"; then rm -rf "${'$'}old"; else mv "${'$'}old" "${'$'}app"; fi
        fi
        xattr -dr com.apple.quarantine "${'$'}app" 2>/dev/null
        rm -rf "${'$'}stage" "${'$'}zip"
        open "${'$'}app"
    """.trimIndent()

    /** Args: pid, new AppImage, current AppImage. */
    private val APPIMAGE_SCRIPT = """
        #!/bin/sh
        pid="${'$'}1"; new="${'$'}2"; target="${'$'}3"
        while kill -0 "${'$'}pid" 2>/dev/null; do sleep 0.5; done
        chmod +x "${'$'}new"
        cp -f "${'$'}new" "${'$'}target.new" && mv -f "${'$'}target.new" "${'$'}target" && rm -f "${'$'}new"
        nohup "${'$'}target" >/dev/null 2>&1 &
    """.trimIndent()
}
