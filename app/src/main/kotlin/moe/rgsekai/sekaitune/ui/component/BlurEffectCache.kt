package moe.rgsekai.sekaitune.ui.component

import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * High-performance RenderEffect cache for Compose.
 */
object BlurEffectCache {
    private val composeCache = ConcurrentHashMap<Int, RenderEffect>()
    private val nativeCache = ConcurrentHashMap<Int, android.graphics.RenderEffect>()

    fun get(
        radius: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP,
        allowLargeRadius: Boolean = false,
    ): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) {
            return null
        }

        val effectiveRadius = if (Build.VERSION.SDK_INT <= 34 && !allowLargeRadius) {
            radius.coerceAtMost(54f)
        } else {
            radius
        }

        val quant = when {
            effectiveRadius <= 16f -> 0.5f
            effectiveRadius <= 32f -> 1.0f
            effectiveRadius <= 64f -> 2.0f
            else -> 4.0f
        }
        val step = (effectiveRadius / quant).roundToInt().coerceAtLeast(1)
        val key = (step * 100) + (quant * 10).toInt() + tileMode.ordinal

        return composeCache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect
                .createBlurEffect(r, r, tileMode)
                .asComposeRenderEffect()
        }
    }

    fun effectiveRadius(radius: Float, allowLargeRadius: Boolean = false): Float =
        if (Build.VERSION.SDK_INT <= 34 && !allowLargeRadius) radius.coerceAtMost(54f) else radius

    fun quantize(radius: Float): Float {
        val quant = when {
            radius <= 16f -> 0.5f
            radius <= 32f -> 1.0f
            radius <= 64f -> 2.0f
            else -> 4.0f
        }
        return (radius / quant).roundToInt().coerceAtLeast(1) * quant
    }

    fun getNative(
        radius: Float,
        tileMode: Shader.TileMode = Shader.TileMode.CLAMP,
    ): android.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.5f) {
            return null
        }

        val effectiveRadius = if (Build.VERSION.SDK_INT <= 34) {
            radius.coerceAtMost(54f)
        } else {
            radius
        }

        val quant = when {
            effectiveRadius <= 16f -> 0.5f
            effectiveRadius <= 32f -> 1.0f
            effectiveRadius <= 64f -> 2.0f
            else -> 4.0f
        }
        val step = (effectiveRadius / quant).roundToInt().coerceAtLeast(1)
        val key = (step * 100) + (quant * 10).toInt() + tileMode.ordinal

        return nativeCache.getOrPut(key) {
            val r = step * quant
            android.graphics.RenderEffect.createBlurEffect(r, r, tileMode)
        }
    }
}

