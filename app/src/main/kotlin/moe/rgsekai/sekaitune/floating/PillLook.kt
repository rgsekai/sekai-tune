package moe.rgsekai.sekaitune.floating

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import moe.rgsekai.sekaitune.ui.theme.extractThemeColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val DarkInk = Color(0xFF0D0D0E)
private val LightInk = Color.White
private const val MinContrast = 4.5
private const val WaitingAlpha = 0.42f
private const val SoftAlpha = 0.58f

@Immutable
internal class PillColors(val bubble: Color, val ink: Color) {
    companion object {
        val Default = PillColors(Color(0xFFE8EEF1), DarkInk)

        fun from(accent: Color?): PillColors {
            if (accent == null) return Default
            var fill = accent.copy(alpha = 1f).toArgb()
            val darkInk = ColorUtils.calculateContrast(DarkInk.toArgb(), fill) >=
                ColorUtils.calculateContrast(LightInk.toArgb(), fill)
            val ink = if (darkInk) DarkInk else LightInk
            val away = if (darkInk) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            var steps = 0
            while (ColorUtils.calculateContrast(ink.toArgb(), fill) < MinContrast && steps < 12) {
                fill = ColorUtils.blendARGB(fill, away, 0.1f)
                steps++
            }
            return PillColors(Color(fill), ink)
        }
    }
}

@Stable
internal class PillTint(private val bubbleState: State<Color>, private val inkState: State<Color>) {
    val bubble: Color get() = bubbleState.value
    val ink: Color get() = inkState.value
    val waiting: Color get() = inkState.value.copy(alpha = WaitingAlpha)
    val soft: Color get() = inkState.value.copy(alpha = SoftAlpha)
}

internal val LocalPillTint = staticCompositionLocalOf<PillTint> {
    error("LocalPillTint must be provided by FloatingPill")
}

internal suspend fun coverAccent(context: Context, url: String?): Color? {
    if (url.isNullOrBlank()) return null
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(96, 96)
            .allowHardware(false)
            .build()
        val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return null
        withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

internal fun Modifier.softShadow(corner: Dp, blur: Dp, offsetY: () -> Dp, alpha: () -> Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return shadow(3.dp, RoundedCornerShape(corner), clip = false)
    }
    return drawWithCache {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        val radius = corner.toPx().coerceAtMost(size.minDimension / 2f)
        val inset = blur.toPx() * 0.2f
        onDrawBehind {
            val strength = alpha()
            if (strength <= 0f) return@onDrawBehind
            paint.color = android.graphics.Color.argb((strength.coerceIn(0f, 1f) * 255).roundToInt(), 0, 0, 0)
            val dy = offsetY().toPx()
            drawIntoCanvas {
                it.nativeCanvas.drawRoundRect(inset, dy, size.width - inset, size.height + dy, radius, radius, paint)
            }
        }
    }
}

