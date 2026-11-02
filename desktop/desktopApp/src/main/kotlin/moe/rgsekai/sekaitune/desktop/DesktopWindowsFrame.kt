package moe.rgsekai.sekaitune.desktop

import kotlinx.coroutines.delay

/**
 * Windows' native frame kept underneath Sekai Tune's Compose-drawn traffic-light controls.
 *
 * The Win32 window remains an overlapped window, so DWM owns its shadow, transitions, taskbar
 * maximization and resize border. Only the caption drawing is replaced by the app. This object is
 * never loaded on Linux, where the window manager continues to provide the ordinary decoration.
 */
internal object DesktopWindowsFrame {

    const val APP_USER_MODEL_ID = "moe.rgsekai.sekaitune.desktop"

    fun initialize() {
        if (!DesktopPlatform.isWindows || !available) return
        val setAppId = runCatching { nativeSetProcessAppUserModelId(APP_USER_MODEL_ID) }.getOrDefault(false)
        DesktopTrackLog.log("process AppUserModelID set: $setAppId ($APP_USER_MODEL_ID)")
        updateStartMenuShortcut()
    }

    fun updateStartMenuShortcut(force: Boolean = false) {
        if (!DesktopPlatform.isWindows || !available) return
        val cmd = ProcessHandle.current().info().command().orElse("")
        val isPackagedExe = cmd.endsWith("SekaiTune.exe", ignoreCase = true)
        val classPath = System.getProperty("java.class.path", "")
        val isGradleOrIde = classPath.contains("gradle", ignoreCase = true) ||
            classPath.contains("idea", ignoreCase = true) ||
            classPath.contains("build\\classes", ignoreCase = true)
        if (force || isPackagedExe || (!isGradleOrIde && System.getProperty("sekai.version") != null)) {
            val targetPath = cmd
            val iconPath = if (isPackagedExe) cmd else {
                val dir = java.io.File(cmd).parentFile
                val ico = java.io.File(dir, "AppIcon.ico")
                if (ico.isFile) ico.absolutePath else cmd
            }
            val updated = runCatching {
                nativeCreateOrUpdateShortcut(targetPath, iconPath, APP_USER_MODEL_ID)
            }.getOrDefault(false)
            DesktopTrackLog.log("Start menu shortcut: $updated ($targetPath)")
        }
    }

    fun removeStartMenuShortcut(): Boolean {
        if (!DesktopPlatform.isWindows) return false
        val appData = System.getenv("APPDATA") ?: return false
        val programs = java.io.File(appData, "Microsoft\\Windows\\Start Menu\\Programs")
        var removed = false
        val loose = java.io.File(programs, "Sekai Tune.lnk")
        if (loose.exists()) {
            removed = loose.delete() || removed
        }
        val group = java.io.File(programs, "Sekai Tune")
        if (group.isDirectory) {
            val groupLnk = java.io.File(group, "SekaiTune.lnk")
            if (groupLnk.exists()) {
                removed = groupLnk.delete() || removed
            }
        }
        DesktopTrackLog.log("Start menu shortcut removed: $removed")
        return removed
    }

    private val available: Boolean by lazy {
        DesktopPlatform.isWindows &&
            runCatching { DesktopAnalysisRuntime.loadNative(LIBRARY) }
                .onFailure { DesktopTrackLog.log("native Windows frame unavailable: ${it.message}") }
                .isSuccess
    }

    @Volatile
    private var installed = false

    suspend fun install(title: String): Boolean {
        if (!available) return false
        repeat(INSTALL_ATTEMPTS) {
            val ready = runCatching { nativeInstall(title) }.getOrElse {
                DesktopTrackLog.log("native Windows frame install failed: ${it.message}")
                false
            }
            if (ready) {
                installed = true
                return true
            }
            delay(INSTALL_RETRY_MILLIS)
        }
        DesktopTrackLog.log("native Windows frame could not find the visible app window")
        return false
    }

    fun setWindowIcons(
        bigPixels: IntArray,
        bigW: Int,
        bigH: Int,
        smallPixels: IntArray,
        smallW: Int,
        smallH: Int,
    ): Boolean = installed && runCatching {
        nativeSetWindowIcons(bigPixels, bigW, bigH, smallPixels, smallW, smallH)
    }.getOrDefault(false)

    fun minimize(): Boolean = installed && runCatching { nativeMinimize() }.getOrDefault(false)

    fun toggleMaximize(): Boolean =
        installed && runCatching { nativeToggleMaximize() }.getOrDefault(false)

    /**
     * Hands the press in progress to Windows as a caption drag. False when there is no native
     * frame, and the caller moves the window itself.
     */
    fun startDrag(): Boolean = installed && runCatching { nativeStartDrag() }.getOrDefault(false)

    @JvmStatic
    private external fun nativeStartDrag(): Boolean

    /**
     * Fills the whole monitor — taskbar included — with a frameless window above it, or puts the
     * caption window back. False without the native frame, and the caller falls back to maximize.
     */
    fun setFullscreen(on: Boolean): Boolean =
        installed && runCatching { nativeSetFullscreen(on) }.getOrDefault(false)

    @JvmStatic
    private external fun nativeSetFullscreen(on: Boolean): Boolean

    /**
     * Puts DWM's material behind the window — [DesktopBackdrop.nativeKind] — or takes it away.
     * False without the native frame, or on a Windows too old to have one.
     */
    fun setBackdrop(kind: Int): Boolean =
        installed && runCatching { nativeSetBackdrop(kind) }.getOrDefault(false)

    @JvmStatic
    private external fun nativeSetProcessAppUserModelId(appId: String): Boolean

    @JvmStatic
    private external fun nativeCreateOrUpdateShortcut(
        targetPath: String,
        iconPath: String,
        appId: String,
    ): Boolean

    @JvmStatic
    private external fun nativeSetBackdrop(kind: Int): Boolean

    @JvmStatic
    private external fun nativeSetWindowIcons(
        bigPixels: IntArray,
        bigW: Int,
        bigH: Int,
        smallPixels: IntArray,
        smallW: Int,
        smallH: Int,
    ): Boolean

    @JvmStatic
    private external fun nativeInstall(title: String): Boolean

    @JvmStatic
    private external fun nativeMinimize(): Boolean

    @JvmStatic
    private external fun nativeToggleMaximize(): Boolean

    private const val LIBRARY = "sekai_window"
    private const val INSTALL_ATTEMPTS = 20
    private const val INSTALL_RETRY_MILLIS = 100L
}
