package moe.rgsekai.sekaitune.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import java.awt.MouseInfo

// The window's own frame, drawn by the application rather than by the system.

/** The strip where the system's title bar would have been. */
@Composable
internal fun DesktopTitleBar() {
    val enabled by DesktopTitleBarSetting.enabled.collectAsState()
    if (!DesktopPlatform.drawsOwnWindowFrame || !enabled) return
    DesktopTitleBarDragArea(
        Modifier
            .fillMaxWidth()
            .height(CAPTION_HEIGHT)
            .desktopWindowGlass(),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (DesktopPlatform.isMac) {
                DesktopWindowButtons(Modifier.align(Alignment.CenterStart))
            } else {
                DesktopWindowButtons(Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

/** Whether there is a title bar at all. */
internal object DesktopTitleBarSetting {

    internal const val KEY = "window_title_bar"

    private val _enabled = MutableStateFlow(DesktopPersistence().boolean(KEY, false))

    /** What Settings shows and writes. */
    val enabled: StateFlow<Boolean> = _enabled

    fun set(value: Boolean) {
        DesktopPersistence().saveBoolean(KEY, value)
        _enabled.value = value
    }
}

/** What the caption buttons act on. */
internal class DesktopWindowActions(
    val minimize: () -> Unit,
    val toggleMaximize: () -> Unit,
    val close: () -> Unit,
)

internal val LocalDesktopWindowActions = staticCompositionLocalOf<DesktopWindowActions?> { null }

/** The window, for the drag area. */
internal val LocalDesktopWindowScope = staticCompositionLocalOf<WindowScope?> { null }

/** A region that moves the window, and maximizes it on a double click. */
@Composable
internal fun DesktopTitleBarDragArea(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val scope = LocalDesktopWindowScope.current
    val actions = LocalDesktopWindowActions.current
    if (!DesktopPlatform.drawsOwnWindowFrame || scope == null) {
        Box(modifier) { content() }
        return
    }
    Box(modifier) {
        Box(Modifier.matchParentSize().captionPress(scope, actions))
        content()
    }
}

/** The caption buttons. In the title bar when there is one, otherwise on the toolbar or sidebar. */
@Composable
internal fun DesktopWindowButtons(modifier: Modifier = Modifier) {
    if (!DesktopPlatform.drawsOwnWindowFrame) return
    val actions = LocalDesktopWindowActions.current ?: return
    val maximized by DesktopWindowMode.maximized.collectAsState()
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isFocused = windowInfo.isWindowFocused

    if (DesktopPlatform.isMac) {
        val inactiveFill = if (!isFocused) Color.White.copy(alpha = 0.22f) else null
        Row(
            modifier = modifier.height(CAPTION_HEIGHT).padding(start = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MacCaptionButton("Close", MAC_CLOSE, onClick = actions.close, inactiveFill = inactiveFill) { glyph ->
                val reach = GLYPH_HALF.toPx()
                val line = HAIRLINE.toPx()
                drawLine(
                    color = glyph,
                    start = Offset(center.x - reach, center.y - reach),
                    end = Offset(center.x + reach, center.y + reach),
                    strokeWidth = line,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = glyph,
                    start = Offset(center.x + reach, center.y - reach),
                    end = Offset(center.x - reach, center.y + reach),
                    strokeWidth = line,
                    cap = StrokeCap.Round,
                )
            }
            MacCaptionButton("Minimize", MAC_MINIMIZE, onClick = actions.minimize, inactiveFill = inactiveFill) { glyph ->
                drawLine(
                    color = glyph,
                    start = Offset(center.x - GLYPH_HALF.toPx(), center.y),
                    end = Offset(center.x + GLYPH_HALF.toPx(), center.y),
                    strokeWidth = HAIRLINE.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            MacCaptionButton(
                if (maximized) "Restore" else "Maximize",
                MAC_MAXIMIZE,
                onClick = actions.toggleMaximize,
                inactiveFill = inactiveFill,
            ) { glyph ->
                val reach = GLYPH_HALF.toPx()
                val line = HAIRLINE.toPx()
                drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x + reach, center.y - reach), line, StrokeCap.Round)
                drawLine(glyph, Offset(center.x + reach, center.y - reach), Offset(center.x + 1.dp.toPx(), center.y - reach), line, StrokeCap.Round)
                drawLine(glyph, Offset(center.x + reach, center.y - reach), Offset(center.x + reach, center.y - 1.dp.toPx()), line, StrokeCap.Round)
                drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x - 1.dp.toPx(), center.y + reach), line, StrokeCap.Round)
                drawLine(glyph, Offset(center.x - reach, center.y + reach), Offset(center.x - reach, center.y + 1.dp.toPx()), line, StrokeCap.Round)
            }
        }
    } else {
        // Windows 11 style caption buttons: 46dp wide rectangles, glyphs drawn via Canvas
        Row(
            modifier = modifier.fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WindowsCaptionButton(
                label = "Minimize",
                onClick = actions.minimize,
                isClose = false,
            ) { glyphColor ->
                val half = 5.dp.toPx()
                val stroke = 1.dp.toPx()
                drawLine(
                    color = glyphColor,
                    start = Offset(center.x - half, center.y),
                    end = Offset(center.x + half, center.y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
            }
            WindowsCaptionButton(
                label = if (maximized) "Restore" else "Maximize",
                onClick = actions.toggleMaximize,
                isClose = false,
            ) { glyphColor ->
                val stroke = 1.dp.toPx()
                if (maximized) {
                    // Two overlapping squares for Restore
                    val size = 8.dp.toPx()
                    val offset = 2.dp.toPx()
                    // Back square (top-right)
                    drawRect(
                        color = glyphColor,
                        topLeft = Offset(center.x - size / 2 + offset, center.y - size / 2 - offset),
                        size = Size(size, size),
                        style = Stroke(width = stroke),
                    )
                    // Front square (bottom-left)
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(center.x - size / 2 - offset, center.y - size / 2 + offset),
                        size = Size(size, size),
                    )
                    drawRect(
                        color = glyphColor,
                        topLeft = Offset(center.x - size / 2 - offset, center.y - size / 2 + offset),
                        size = Size(size, size),
                        style = Stroke(width = stroke),
                    )
                } else {
                    // Single square for Maximize
                    val size = 10.dp.toPx()
                    drawRect(
                        color = glyphColor,
                        topLeft = Offset(center.x - size / 2, center.y - size / 2),
                        size = Size(size, size),
                        style = Stroke(width = stroke),
                    )
                }
            }
            WindowsCaptionButton(
                label = "Close",
                onClick = actions.close,
                isClose = true,
            ) { glyphColor ->
                val half = 5.dp.toPx()
                val stroke = 1.dp.toPx()
                drawLine(
                    color = glyphColor,
                    start = Offset(center.x - half, center.y - half),
                    end = Offset(center.x + half, center.y + half),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
                drawLine(
                    color = glyphColor,
                    start = Offset(center.x + half, center.y - half),
                    end = Offset(center.x - half, center.y + half),
                    strokeWidth = stroke,
                    cap = StrokeCap.Square,
                )
            }
        }
    }
}

@Composable
private fun WindowsCaptionButton(
    label: String,
    onClick: () -> Unit,
    isClose: Boolean,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val bg = when {
        isClose && pressed -> Color(0xFF992318)
        isClose && hovered -> Color(0xFFC42B1C)
        pressed -> Color.White.copy(alpha = 0.15f)
        hovered -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val glyphColor = if (isClose && hovered) Color.White else Color.White.copy(alpha = 0.90f)

    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Default)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .background(bg)
            .semantics { contentDescription = label }
            .drawBehind {
                glyph(glyphColor)
            },
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
private fun MacCaptionButton(
    label: String,
    fill: Color,
    onClick: () -> Unit,
    inactiveFill: Color? = null,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val buttonColor = if (inactiveFill != null && !hovered) inactiveFill else fill
    Box(
        Modifier
            .size(CAPTION_TOUCH_SIZE)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Default)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .drawBehind {
                drawCircle(buttonColor, radius = MAC_DOT_RADIUS.toPx())
                if (hovered) glyph(MAC_GLYPH)
            },
        contentAlignment = Alignment.Center,
    ) {}
}

private val CAPTION_TOUCH_SIZE = 22.dp
private val CAPTION_HEIGHT = 24.dp
private val MAC_DOT_RADIUS = 6.dp

private val GLYPH_HALF = 2.5.dp
private val HAIRLINE = 0.75.dp

private val MAC_CLOSE = Color(0xFFFF5F57)
private val MAC_MINIMIZE = Color(0xFFFFBD2E)
private val MAC_MAXIMIZE = Color(0xFF28C840)
private val MAC_GLYPH = Color(0xB3000000)

private fun Modifier.captionPress(scope: WindowScope, actions: DesktopWindowActions?): Modifier =
    pointerInput(scope, actions) {
        var lastPressAt = 0L
        var lastPressPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown()
            val double = down.uptimeMillis - lastPressAt <= viewConfiguration.doubleTapTimeoutMillis &&
                (down.position - lastPressPosition).getDistance() <= viewConfiguration.touchSlop
            if (double) {
                lastPressAt = 0L
                down.consume()
                actions?.toggleMaximize()
                return@awaitEachGesture
            }
            lastPressAt = down.uptimeMillis
            lastPressPosition = down.position
            if (DesktopWindowsFrame.startDrag()) return@awaitEachGesture
            if (DesktopPlatform.isMac && DesktopMacFrame.startDrag()) return@awaitEachGesture
            val window = scope.window
            val windowAtStart = window.location
            val pointerAtStart = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
            drag(down.id) {
                val pointer = MouseInfo.getPointerInfo()?.location ?: return@drag
                window.setLocation(
                    windowAtStart.x + pointer.x - pointerAtStart.x,
                    windowAtStart.y + pointer.y - pointerAtStart.y,
                )
            }
        }
    }