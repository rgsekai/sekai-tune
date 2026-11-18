package moe.rgsekai.sekaitune.ui.player.flagship

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size as CoilSize
import coil3.toBitmap
import moe.rgsekai.sekaitune.ui.player.BackdropBlurTransformation
import moe.rgsekai.sekaitune.ui.utils.resize
import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import kotlin.math.roundToInt

internal const val ReflectionPx = 96
internal const val ReflectionBlurPx = 6
private const val ReflectionStrength = 0.92f
private const val ReflectionFadeEnd = 0.9f
private const val MeshRows = 32
private const val LiveMirrorBlurRadius = 120f
private const val LiveMirrorDownscale = 4

@Composable
fun FlagshipLiveCoverMirror(
    coverLayer: GraphicsLayer,
    alpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val currentAlpha = alpha()
    if (currentAlpha <= 0.004f) return

    val blurRadius = LiveMirrorBlurRadius / LiveMirrorDownscale.toFloat()

    Spacer(
        modifier = modifier
            .graphicsLayer {
                this.alpha = currentAlpha
            }
            .layout { measurable, constraints ->
                val fullW = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
                val fullH = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
                val placeable = measurable.measure(
                    Constraints.fixed(
                        (fullW + LiveMirrorDownscale - 1) / LiveMirrorDownscale,
                        (fullH + LiveMirrorDownscale - 1) / LiveMirrorDownscale,
                    )
                )
                layout(fullW, fullH) {
                    placeable.place(0, 0)
                }
            }
            .graphicsLayer {
                val k = LiveMirrorDownscale.toFloat()
                scaleX = k
                scaleY = k
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Clamp)
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawBehind

                val layerW = coverLayer.size.width.toFloat()
                val layerH = coverLayer.size.height.toFloat()
                if (layerW <= 0f || layerH <= 0f) return@drawBehind

                val scaleToFit = max(w / layerW, h / layerH)
                val pivot = Offset(layerW / 2f, layerH / 2f)

                withTransform({
                    translate(left = (w - layerW) / 2f, top = 0f)
                    scale(
                        scaleX = scaleToFit * 1.04f,
                        scaleY = -scaleToFit * 1.04f,
                        pivot = pivot,
                    )
                }) {
                    drawLayer(coverLayer)
                }
            },
    )
}

@Composable
fun FlagshipCoverReflection(
    url: String?,
    coverHeight: () -> Float,
    magnify: () -> Float = { 1f },
    alpha: () -> Float = { 1f },
    steady: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .transformations(BackdropBlurTransformation(radiusPx = ReflectionBlurPx))
            .crossfade(false)
            .size(CoilSize(ReflectionPx, ReflectionPx))
            .build()
    }
    val picture by produceState<Bitmap?>(null, request) {
        value = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
    }
    val mesh = remember { ReflectionMesh() }
    Spacer(
        modifier = modifier.drawBehind {
            val bitmap = picture ?: return@drawBehind
            val a = alpha().coerceIn(0f, 1f)
            if (a <= 0.004f) return@drawBehind
            mesh.draw(this, bitmap, coverHeight(), magnify(), a, steady)
        },
    )
}

private class ReflectionMesh {
    private val verts = FloatArray((MeshRows + 1) * 2 * 2)
    private val colors = IntArray((MeshRows + 1) * 2)
    private val paint = android.graphics.Paint().apply { isFilterBitmap = true }

    fun draw(scope: DrawScope, bitmap: Bitmap, coverHeight: Float, magnify: Float, alpha: Float, steady: Boolean): Unit = with(scope) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || coverHeight <= 0f) return
        val bw = bitmap.width.toFloat()
        val bh = bitmap.height.toFloat()

        val fit = max(w / bw, coverHeight / bh)
        val srcLeft = (bw - w / fit) / 2f
        val srcBottom = (bh + coverHeight / fit) / 2f
        val scale = fit * magnify

        for (row in 0..MeshRows) {
            val v = bh * row / MeshRows
            val y = (srcBottom - v) * scale
            val fade = if (steady) alpha else fadeAt(y / h) * alpha
            val colour = android.graphics.Color.argb((fade * 255f).roundToInt().coerceIn(0, 255), 255, 255, 255)
            for (col in 0..1) {
                val u = bw * col
                val x = w / 2f + ((u - srcLeft) * fit - w / 2f) * magnify
                val i = row * 2 + col
                verts[i * 2] = x
                verts[i * 2 + 1] = y
                colors[i] = colour
            }
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val saved = native.save()
            native.clipRect(0f, 0f, w, h)
            native.drawBitmapMesh(bitmap, 1, MeshRows, verts, 0, colors, 0, paint)
            native.restoreToCount(saved)
        }
    }

    private fun fadeAt(t: Float): Float {
        val x = (t.coerceIn(0f, 1f) / ReflectionFadeEnd).coerceIn(0f, 1f)
        val eased = x * x * (3f - 2f * x)
        return ReflectionStrength * (1f - eased)
    }
}

