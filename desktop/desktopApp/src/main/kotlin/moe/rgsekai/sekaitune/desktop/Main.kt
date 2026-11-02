package moe.rgsekai.sekaitune.desktop

import androidx.compose.runtime.CompositionLocalProvider
import moe.rgsekai.sekaitune.desktopui.player.PlayerPlatform
import moe.rgsekai.sekaitune.shared.data.DebugLog
import moe.rgsekai.sekaitune.shared.data.TrackLog
import moe.rgsekai.sekaitune.shared.data.innertube.InnerTubeXResolver
import moe.rgsekai.sekaitune.shared.data.innertube.StreamResolver
import moe.rgsekai.sekaitune.shared.data.innertube.potoken.PoTokenGenerator
import moe.rgsekai.sekaitune.shared.data.lyrics.LyricsTranslation
import moe.rgsekai.sekaitune.shared.data.spotify.SpotifyLibrary
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import moe.rgsekai.sekaitune.desktop.generated.resources.Res
import moe.rgsekai.sekaitune.desktop.generated.resources.logo
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.Toolkit
import okio.Path.Companion.toOkioPath
import kotlin.math.roundToInt
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.painterResource

fun main() {
    // Before any WebView exists, so the sign-in window can start over with an empty jar.
    DesktopWebCookies.install()
    DesktopWindowVisibility.install()
    // The player is the phone's, from the shared UI module; this is what it reads underneath.
    PlayerPlatform.install(DesktopPlayerHost)
    moe.rgsekai.sekaitune.desktopui.AppUi.install(DesktopAppUiHost)
    // The data layer both apps share logs through here, and keeps translated
    // lyrics beside the rest of the desktop's cache.
    DebugLog.sink = DebugLog.Sink { level, tag, message, error ->
        DesktopTrackLog.log("$tag/$level: $message" + (error?.let { " (${it.message})" } ?: ""))
    }
    LyricsTranslation.cacheDir = DesktopMediaCache.directory.toOkioPath()
    // The Spotify library both apps share, on the tokens the web player mints in JavaFX.
    SpotifyLibrary.auth = SpotifyLibrary.Auth {
        DesktopSpotifyToken.accessToken()?.let { SpotifyLibrary.Tokens(it, DesktopSpotifyToken.clientToken()) }
    }
    // Desktop follows its own language picker (zh-Hant / zh-Hans preserved).
    moe.rgsekai.sekaitune.shared.data.innertube.Innertube.appLanguage = { DesktopStrings.resolvedTag() }
    // YouTube playback is the phone's StreamResolver over InnerTubeX, with BotGuard PoTokens
    // minted in JavaFX's WebView where the phone uses Android's.
    TrackLog.echo = { level, tag, message, error ->
        DesktopTrackLog.log("$tag/$level: $message" + (error?.let { " (${it.message})" } ?: ""))
    }
    StreamResolver.maxKbps = {
        DesktopStreamClient.ceiling.get() ?: DesktopSourceRegistry.ceiling(null).maxKbps
    }
    InnerTubeXResolver.init(
        filesDir = DesktopMediaCache.directory.toOkioPath(),
        store = DesktopInnerTubeXStore,
        poTokenProvider = PoTokenGenerator(
            createMinter = { DesktopPoTokenWebView.getNewPoTokenGenerator() },
            available = { DesktopPoTokenWebView.available },
        ).asTokenProvider(),
    )
    if (DesktopPlatform.isWindows) {
        DesktopWindowsFrame.initialize()
    }
    // The public "apps open right now" count; see Presence.
    DesktopPresence.install()
    desktopMain()
}

private fun desktopMain() = application {
    // Closing puts the window away rather than ending the process, while there is a tray icon to
    // bring it back from — see [DesktopWindowVisibility].
    val visible by DesktopWindowVisibility.visible.collectAsState()
    val state = rememberWindowState(width = 1_220.dp, height = 780.dp)
    LaunchedEffect(visible) {
        if (visible && state.isMinimized) {
            state.isMinimized = false
        }
    }
    // Hoisted so the player can fill the screen and the caption buttons can maximize — see
    // [DesktopWindowMode]. Fullscreen on Windows is a native borderless window that covers the
    // taskbar, so it needs no placement change here; only ordinary maximize does.
    val placement by DesktopWindowMode.placement.collectAsState()
    LaunchedEffect(placement) { state.placement = placement }
    // ...and back, for the times the window is moved between placements by something that is not
    // us; see [DesktopWindowMode.adopt].
    LaunchedEffect(state) {
        snapshotFlow { state.placement }.collect(DesktopWindowMode::adopt)
    }
    val maximized by DesktopWindowMode.maximized.collectAsState()
    LaunchedEffect(maximized) {
        if (DesktopPlatform.isMac) {
            DesktopMacFrame.updateCornerRadius(maximized)
        }
    }
    Window(
        onCloseRequest = { if (DesktopWindowVisibility.onCloseRequest()) exitApplication() },
        visible = visible,
        title = "Sekai Tune",
        icon = painterResource(Res.drawable.logo),
        state = state,
        // No system title bar on Windows, where the application draws its own instead — see
        // [DesktopWindowChrome].
        undecorated = DesktopPlatform.drawsOwnWindowFrame,
        // For Mica and Acrylic behind the sidebar and top bar: only a transparent window lets
        // DWM's material show through the chrome. Fixed at creation, so it is on wherever the
        // material is possible and the chosen one can be switched live — see [DesktopWindowBackdrop].
        transparent = DesktopWindowBackdrop.available,
        resizable = true,
    ) {
        val composeWindow = window
        val openingSize = remember { state.size }
        LaunchedEffect(composeWindow) {
            DesktopAppIcon.applyToWindow(composeWindow)
            DesktopWindowVisibility.attachWindow(composeWindow)
            // AWT's default is white, and it is what shows for the frame or two a moved or resized
            // window takes to repaint: a white band along its edges.
            // Not over a transparent window, whose clear background is what the material shows
            // through; DWM moves that window, so there is no band to hide.
            if (!DesktopWindowBackdrop.available) {
                composeWindow.background = java.awt.Color.BLACK
                composeWindow.contentPane.background = java.awt.Color.BLACK
            } else {
                composeWindow.background = java.awt.Color(0, 0, 0, 0)
                composeWindow.contentPane.background = java.awt.Color(0, 0, 0, 0)
                (composeWindow.contentPane as? javax.swing.JComponent)?.isOpaque = false
                composeWindow.rootPane.isOpaque = false
            }
            // AWT window sizes are logical points, the same unit as Compose's dp — the display
            // scale is already applied. Multiplying by it doubled both sizes on a Retina screen,
            // which pushed the window past a 13-inch MacBook's display.
            composeWindow.minimumSize = Dimension(
                MIN_WINDOW_WIDTH_DP.roundToInt(),
                MIN_WINDOW_HEIGHT_DP.roundToInt(),
            )
            // Setting AWT's minimum during the first composition can resize the native peer to
            // that minimum without updating Compose's state. Compose would then keep laying out a
            // 1220dp surface inside a 900dp window, cropping the right edge and putting its resize
            // boundary off-screen until a maximize/restore cycle synchronised the two. Re-assert
            // the requested opening size on the native peer so both layers start in agreement.
            composeWindow.size = Dimension(
                openingSize.width.value.roundToInt(),
                openingSize.height.value.roundToInt(),
            )
            if (DesktopPlatform.isWindows && DesktopWindowsFrame.install("Sekai Tune")) {
                DesktopWindowBackdrop.apply()
            } else if (DesktopPlatform.isMac && DesktopMacFrame.install(composeWindow)) {
                DesktopWindowBackdrop.apply()
            }
        }
        val actions = remember {
            DesktopWindowActions(
                minimize = {
                    if (!DesktopWindowsFrame.minimize()) state.isMinimized = true
                },
                toggleMaximize = {
                    if (!DesktopWindowsFrame.toggleMaximize()) {
                        // An undecorated AWT window otherwise maximizes to the monitor bounds on
                        // Windows when the native frame bridge is unavailable. Give AWT the
                        // monitor's usable work area before Compose switches the placement.
                        if (DesktopPlatform.isWindows && !DesktopWindowMode.maximized.value) {
                            val screen = composeWindow.graphicsConfiguration.bounds
                            val insets = Toolkit.getDefaultToolkit().getScreenInsets(
                                composeWindow.graphicsConfiguration,
                            )
                            composeWindow.maximizedBounds = Rectangle(
                                screen.x + insets.left,
                                screen.y + insets.top,
                                screen.width - insets.left - insets.right,
                                screen.height - insets.top - insets.bottom,
                            )
                        }
                        DesktopWindowMode.toggleMaximized()
                    }
                },
                // The same door the system's close button went through, so the tray keeps the
                // process alive exactly as it did before.
                close = { if (DesktopWindowVisibility.onCloseRequest()) exitApplication() },
            )
        }
        CompositionLocalProvider(
            LocalDesktopWindowActions provides actions,
            LocalDesktopWindowScope provides this,
        ) {
            SekaiDesktopApp()
        }
    }
}

// The now-playing screen's two-column layout starts at 560dp wide (LANDSCAPE_PLAYER_MIN_WIDTH in
// NowPlayingScreen). Narrower and short, it falls back to the portrait player, which needs height.
private const val MIN_WINDOW_WIDTH_DP = 560.0
private const val MIN_WINDOW_HEIGHT_DP = 400.0
