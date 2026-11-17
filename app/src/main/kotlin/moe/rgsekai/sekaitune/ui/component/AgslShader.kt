package moe.rgsekai.sekaitune.ui.component

import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas

/*
 * AGSL shader plumbing, kept behind `Any`.
 *
 * `android.graphics.RuntimeShader` is API 33. Naming it in a parameter, return type or field
 * means ART has to resolve the class when that method is reached - and on Android 8 that is a
 * `NoClassDefFoundError: Failed resolution of: Landroid/graphics/RuntimeShader;` at launch, even
 * though every *call* is already behind an SDK check. Guarding the call is not enough; the type
 * must not appear in any signature reachable on an older device.
 *
 * So shaders travel as `Any` and only these helpers - each annotated, each invoked solely from
 * inside a version check - ever name the real type.
 */

@RequiresApi(33)
internal fun createAgslShader(source: String): Any = RuntimeShader(source)

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, value: Float) {
    (this as RuntimeShader).setFloatUniform(name, value)
}

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, first: Float, second: Float) {
    (this as RuntimeShader).setFloatUniform(name, first, second)
}

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, first: Float, second: Float, third: Float) {
    (this as RuntimeShader).setFloatUniform(name, first, second, third)
}

@RequiresApi(33)
internal fun Any.setAgslUniform(name: String, first: Float, second: Float, third: Float, fourth: Float) {
    (this as RuntimeShader).setFloatUniform(name, first, second, third, fourth)
}

/** For a `layout(color) uniform half4`: [color] is an sRGB ARGB int, converted for the shader. */
@RequiresApi(33)
internal fun Any.setAgslColorUniform(name: String, color: Int) {
    (this as RuntimeShader).setColorUniform(name, color)
}

@RequiresApi(33)
internal fun agslRenderEffect(shader: Any, inputName: String): RenderEffect =
    RenderEffect.createRuntimeShaderEffect(shader as RuntimeShader, inputName)

@RequiresApi(33)
internal fun Any.setAgslInputShader(name: String, input: android.graphics.Shader) {
    (this as RuntimeShader).setInputShader(name, input)
}

/** The shader itself, to draw with (a brush/paint shader rather than a layer effect). */
@RequiresApi(33)
internal fun Any.asAgslPaintShader(): android.graphics.Shader = this as RuntimeShader

@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNode(width: Int, height: Int): Any =
    RenderNode("DamageBoundary").apply {
        setPosition(0, 0, width, height)
        setClipToBounds(true)
    }

@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNodeBeginRecording(node: Any): android.graphics.Canvas = (node as RenderNode).beginRecording()

@RequiresApi(Build.VERSION_CODES.Q)
private fun damageNodeEndRecording(node: Any) {
    (node as RenderNode).endRecording()
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun android.graphics.Canvas.drawDamageNode(node: Any) {
    drawRenderNode(node as RenderNode)
}

fun Modifier.damageBoundary(): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        this
    } else {
        this.drawWithCache {
            val width = size.width.toInt()
            val height = size.height.toInt()
            val node = if (width > 0 && height > 0) damageNode(width, height) else null
            onDrawWithContent {
                val target = drawContext.canvas
                if (node == null || !target.nativeCanvas.isHardwareAccelerated) {
                    drawContent()
                    return@onDrawWithContent
                }
                drawContext.canvas = Canvas(damageNodeBeginRecording(node))
                try {
                    drawContent()
                } finally {
                    drawContext.canvas = target
                    damageNodeEndRecording(node)
                }
                target.nativeCanvas.drawDamageNode(node)
            }
        }.graphicsLayer()
    }
