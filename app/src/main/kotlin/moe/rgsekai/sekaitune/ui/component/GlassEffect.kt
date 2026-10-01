/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.component

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import moe.rgsekai.sekaitune.ui.component.backdrop.Backdrop
import moe.rgsekai.sekaitune.ui.component.backdrop.drawBackdrop
import moe.rgsekai.sekaitune.ui.component.backdrop.effects.blur
import moe.rgsekai.sekaitune.ui.component.backdrop.effects.colorControls
import moe.rgsekai.sekaitune.ui.component.backdrop.effects.lens
import moe.rgsekai.sekaitune.ui.component.backdrop.highlight.Highlight
import moe.rgsekai.sekaitune.ui.component.backdrop.highlight.HighlightElement
import moe.rgsekai.sekaitune.ui.component.backdrop.internal.ShapeProvider
import moe.rgsekai.sekaitune.ui.component.backdrop.shadow.Shadow
import moe.rgsekai.sekaitune.ui.component.backdrop.shadow.ShadowElement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.PopupProperties

@Stable
data class GlassEffectConfig(
    val globalEnabled: Boolean = false,
    val vibrancy: Float = 70f,
    val blurRadius: Float = 30f,
    val lensHeight: Float = 50f,
    val lensAmount: Float = 50f,
    val chromaticAberration: Boolean = true,
    val depthEffect: Boolean = true,
    val surfaceTintColor: Color = Color.Unspecified,
    val surfaceOpacity: Float = 60f,
    val textColor: Color = Color.Unspecified,
    val miniPlayerEnabled: Boolean = true,
    val navBarEnabled: Boolean = true,
    val popupMenuEnabled: Boolean = true,
    val dialogEnabled: Boolean = true,
    val queueEnabled: Boolean = true,
) {
    fun isEnabledFor(component: GlassComponent): Boolean =
        globalEnabled && when (component) {
            GlassComponent.MINI_PLAYER -> miniPlayerEnabled
            GlassComponent.NAV_BAR -> navBarEnabled
            GlassComponent.POPUP_MENU -> popupMenuEnabled
            GlassComponent.DIALOG -> dialogEnabled
            GlassComponent.QUEUE -> queueEnabled
        }
}

enum class GlassComponent {
    MINI_PLAYER,
    NAV_BAR,
    POPUP_MENU,
    DIALOG,
    QUEUE,
}

internal const val LENS_MAX_DP = 48f
internal const val PLAYER_BLUR_MULTIPLIER = 4f
internal const val MIN_GLASS_RESOLUTION_SCALE = 0.33f
internal const val FULL_QUALITY_BLUR_DP = 8f

fun glassResolutionScale(context: Context? = null, blurRadiusDp: Float): Float {
    val t = (blurRadiusDp / FULL_QUALITY_BLUR_DP).coerceIn(0f, 1f)
    var scale = 1f - t * (1f - MIN_GLASS_RESOLUTION_SCALE)
    if (context != null) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isPowerSaveMode == true) {
            scale = (scale * 0.75f).coerceAtLeast(0.25f)
        }
    }
    return scale
}

fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

fun glassSaturation(vibrancy: Float): Float {
    val norm = if (vibrancy > 2f) (vibrancy / 100f).coerceIn(0f, 1f) else vibrancy.coerceIn(0f, 2f)
    return 1f + 0.5f * norm
}

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }

val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

@Composable
fun Modifier.liquidGlass(
    config: GlassEffectConfig,
    shape: Shape = RoundedCornerShape(0.dp),
    applyEdgeEffects: Boolean = true,
    blurRadiusDp: Float = config.blurRadius,
): Modifier {
    if (!isGlassSupported()) return this
    val backdrop = LocalAppBackdrop.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val resolutionScale = remember(blurRadiusDp, context) {
        glassResolutionScale(context, blurRadiusDp)
    }
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
    val saturation = glassSaturation(config.vibrancy)
    val normLensHeight = if (config.lensHeight > 1f) (config.lensHeight / 100f).coerceIn(0f, 1f) else config.lensHeight.coerceIn(0f, 1f)
    val normLensAmount = if (config.lensAmount > 1f) (config.lensAmount / 100f).coerceIn(0f, 1f) else config.lensAmount.coerceIn(0f, 1f)
    val normSurfaceOpacity = if (config.surfaceOpacity > 1f) (config.surfaceOpacity / 100f).coerceIn(0f, 1f) else config.surfaceOpacity.coerceIn(0f, 1f)
    val lensHeightPx = with(density) { (normLensHeight * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (normLensAmount * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val surfaceTintColor = if (config.surfaceTintColor.isSpecified) {
        config.surfaceTintColor
    } else if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (saturation != 1f) {
                colorControls(saturation = saturation)
            }
            if (blurPx > 0f) {
                blur(blurPx)
            }
            if (applyEdgeEffects &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (lensHeightPx > 0f || lensAmountPx > 0f)
            ) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = config.depthEffect,
                    chromaticAberration = config.chromaticAberration,
                )
            }
        },
        highlight = if (applyEdgeEffects) ({ Highlight.Default }) else null,
        shadow = if (applyEdgeEffects) ({ Shadow.Default }) else null,
        onDrawSurface = {
            if (normSurfaceOpacity > 0f) {
                drawRect(
                    color = surfaceTintColor.copy(alpha = normSurfaceOpacity),
                    size = size,
                )
            }
        },
        backdropScale = resolutionScale,
    )
}

private val vibrancyHsvThreadLocal = ThreadLocal.withInitial { FloatArray(3) }

fun adjustColorVibrancy(color: Color, vibrancy: Float): Color {
    val sat = glassSaturation(vibrancy)
    if (sat == 1f) return color
    val hsv = vibrancyHsvThreadLocal.get() ?: FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] = (hsv[1] * sat).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor((color.alpha * 255).toInt(), hsv))
}

fun findDialogWindow(view: View): Window? {
    var ctx: Context? = view.context
    while (ctx != null) {
        if (ctx is DialogWindowProvider) return ctx.window
        if (ctx is Activity) return ctx.window
        if (ctx is ContextWrapper) {
            ctx = ctx.baseContext
        } else {
            break
        }
    }

    var parent: Any? = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        if (parent is View) {
            val c = parent.context
            if (c is DialogWindowProvider) return c.window
            if (c is Activity) return c.window
            parent = parent.parent
        } else {
            break
        }
    }
    return null
}

@Composable
fun ApplyWindowBlur(
    config: GlassEffectConfig,
    enabled: Boolean = true,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val view = LocalView.current
        val density = LocalDensity.current
        val blurPx = with(density) { config.blurRadius.dp.toPx().toInt() }

        LaunchedEffect(enabled, blurPx, view) {
            val window = findDialogWindow(view)
            if (window != null) {
                if (enabled && blurPx > 0) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = blurPx
                    }
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }
    }
}

@Composable
fun Modifier.frostedGlass(
    config: GlassEffectConfig,
    shape: Shape = RoundedCornerShape(0.dp),
    border: BorderStroke? = null,
    applyEdgeEffects: Boolean = true,
): Modifier {
    if (!config.globalEnabled) return this
    val normSurfaceOpacity =
        if (config.surfaceOpacity > 1f) (config.surfaceOpacity / 100f).coerceIn(0f, 1f)
        else config.surfaceOpacity.coerceIn(0f, 1f)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val rawTint =
        if (config.surfaceTintColor.isSpecified) {
            config.surfaceTintColor
        } else if (isLight) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    val baseTint = adjustColorVibrancy(rawTint, config.vibrancy)

    val topAlpha = (normSurfaceOpacity * 1.05f).coerceIn(0.08f, 0.95f)
    val bottomAlpha = (normSurfaceOpacity * 0.85f).coerceIn(0.05f, 0.90f)
    val topTint = baseTint.copy(alpha = topAlpha)
    val bottomTint = baseTint.copy(alpha = bottomAlpha)

    val shapeProvider = remember(shape) { ShapeProvider { shape } }

    var modifier: Modifier = this
    if (applyEdgeEffects) {
        modifier = modifier
            .then(ShadowElement(shapeProvider = shapeProvider, shadow = { Shadow.Default }))
            .then(HighlightElement(shapeProvider = shapeProvider, highlight = { Highlight.Default }))
    }

    val rimColor =
        if (isLight) {
            Color.White.copy(alpha = (0.25f + 0.25f * (1f - normSurfaceOpacity)).coerceIn(0.15f, 0.60f))
        } else {
            Color.White.copy(alpha = (0.08f + 0.12f * (1f - normSurfaceOpacity)).coerceIn(0.05f, 0.25f))
        }
    val effectiveBorder = border ?: BorderStroke(1.dp, rimColor)

    return modifier
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(topTint, bottomTint)
            ),
            shape = shape,
        )
        .border(effectiveBorder, shape)
}

@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation: Dp = 3.dp,
    shadowElevation: Dp = 3.dp,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val effectiveBorder = if (isGlass) border ?: BorderStroke(1.dp, if (isLight) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f)) else border

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.then(
            if (isGlass) {
                Modifier.liquidGlass(
                    config = glassConfig,
                    shape = shape,
                    applyEdgeEffects = true,
                )
            } else {
                Modifier
            }
        ),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape,
        containerColor = if (isGlass) Color.Transparent else containerColor,
        tonalElevation = if (isGlass) 0.dp else tonalElevation,
        shadowElevation = if (isGlass) 0.dp else shadowElevation,
        border = effectiveBorder,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU) || glassConfig.isEnabledFor(GlassComponent.DIALOG)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val effectiveBorder = if (isGlass) BorderStroke(1.dp, if (isLight) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f)) else null

    if (isGlass) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.25f),
            dragHandle = null,
            contentWindowInsets = contentWindowInsets,
            properties = properties,
        ) {
            ApplyWindowBlur(glassConfig, enabled = true)
            Surface(
                shape = shape,
                color = Color.Transparent,
                border = effectiveBorder,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            config = glassConfig,
                            shape = shape,
                            applyEdgeEffects = true,
                        ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (dragHandle != null) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            dragHandle()
                        }
                    }
                    content()
                }
            }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            tonalElevation = tonalElevation,
            scrimColor = scrimColor,
            dragHandle = dragHandle,
            contentWindowInsets = contentWindowInsets,
            properties = properties,
            content = content,
        )
    }
}

