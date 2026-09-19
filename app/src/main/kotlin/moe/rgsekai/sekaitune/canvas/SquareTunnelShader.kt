/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Attribution / Technique Provenance:
 * The square-tunnel algorithm implemented in AGSL below adapts the geometric square-tunnel math
 * originally created by Inigo Quilez (MIT License, 2013, ShaderToy: "Square Tunnel" / "Ms2SWW")
 * and adapted for animated album art visualization in Screenbox's "Music Tunnel" by Dani John /
 * rocksdanister (MIT License, 2023).
 * Reimplemented from scratch in Kotlin / AGSL for Sekai Tune.
 */

package moe.rgsekai.sekaitune.canvas

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import kotlinx.coroutines.isActive

/**
 * AGSL (Android Graphics Shading Language) source implementing the mirrored square-tunnel effect.
 *
 * Mathematics:
 * - Isotropic coordinate normalization centered at (0, 0) divided by min(resolution.x, resolution.y)
 * - Polar angle: theta = atan(y, x)
 * - Radial distance via 8th-order Minkowski p-norm: r = pow(pow(abs(x), 8) + pow(abs(y), 8), 1/8)
 * - Depth UV: u = (0.3 / r) + (0.2 * time * speed)
 * - Angle UV: v = 0.5 + (theta / PI)
 * - Mirrored-repeat wrapping: mirroredCoord = 1.0 - abs(fract(uv * 0.5) * 2.0 - 1.0)
 * - Radial center vignette: smoothstep blend towards extracted dominant/muted album art color
 */
const val SQUARE_TUNNEL_AGSL_SHADER: String = """
    uniform shader image;
    uniform float2 resolution;
    uniform float time;
    uniform float3 centerColor;

    half4 main(float2 fragCoord) {
        // Isotropic coordinates centered at (0, 0), normalized by min(width, height)
        float minDim = min(resolution.x, resolution.y);
        float2 p = (fragCoord - resolution * 0.5) / minDim;

        // Polar angle in [-PI, PI]
        float theta = atan(p.y, p.x);

        // 8th-order Minkowski p-norm produces flat square tunnel walls with soft corners
        float r = pow(pow(abs(p.x), 8.0) + pow(abs(p.y), 8.0), 0.125);

        // Depth UV motion (continuous forward flight loop)
        float speed = 0.4;
        float u = (0.3 / max(r, 0.0001)) + (0.2 * time * speed);

        // Angle UV mapped across full circular revolution
        float v = 0.5 + (theta / 3.14159265359);

        // GL_MIRRORED_REPEAT emulation
        float2 uv = float2(u, v);
        float2 mirroredCoord = 1.0 - abs(fract(uv * 0.5) * 2.0 - 1.0);

        // Sample album art bitmap in layer pixel coordinates
        half4 texColor = image.eval(mirroredCoord * resolution);

        // Radial center vignette fade towards album art dominant/muted color
        float vignette = smoothstep(0.04, 0.40, r);
        half3 finalRgb = mix(half3(centerColor), texColor.rgb, half(vignette));

        return half4(finalRgb, 1.0);
    }
"""

/**
 * Extracts a dark dominant or muted swatch from [bitmap] to use as the center vignette vanishing point color.
 * Uses a lightweight cached palette extraction.
 */
fun extractTunnelCenterColor(bitmap: Bitmap): FloatArray {
    return try {
        val palette = Palette.from(bitmap)
            .maximumColorCount(16)
            .resizeBitmapArea(4000)
            .generate()

        val swatch = palette.darkMutedSwatch
            ?: palette.mutedSwatch
            ?: palette.dominantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.vibrantSwatch

        if (swatch != null) {
            val red = android.graphics.Color.red(swatch.rgb) / 255f
            val green = android.graphics.Color.green(swatch.rgb) / 255f
            val blue = android.graphics.Color.blue(swatch.rgb) / 255f
            floatArrayOf(red * 0.6f, green * 0.6f, blue * 0.6f)
        } else {
            floatArrayOf(0.08f, 0.08f, 0.08f)
        }
    } catch (_: Exception) {
        floatArrayOf(0.08f, 0.08f, 0.08f)
    }
}

/**
 * Composable that applies the Square Tunnel AGSL shader to a static album art [Bitmap].
 * Only active on API 33+ devices.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun SquareTunnelShaderCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    val time by produceState(initialValue = 0f, key1 = isPlaying) {
        if (!isPlaying) return@produceState
        var lastFrameTimeNanos = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTimeNanos != 0L) {
                    val deltaSeconds = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
                    value += deltaSeconds
                }
                lastFrameTimeNanos = frameTimeNanos
            }
        }
    }

    val centerColor = remember(bitmap) {
        extractTunnelCenterColor(bitmap)
    }

    val runtimeShader = remember { RuntimeShader(SQUARE_TUNNEL_AGSL_SHADER) }

    Box(
        modifier = modifier
            .graphicsLayer {
                val currentWidth = this.size.width
                val currentHeight = this.size.height
                if (currentWidth > 0f && currentHeight > 0f) {
                    runtimeShader.setFloatUniform("resolution", currentWidth, currentHeight)
                    runtimeShader.setFloatUniform("time", time)
                    runtimeShader.setFloatUniform("centerColor", centerColor[0], centerColor[1], centerColor[2])
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(runtimeShader, "image")
                        .asComposeRenderEffect()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Unified Square Tunnel procedural canvas entry point that selects the AGSL shader path on API 33+ or
 * falls back to [KenBurnsCanvas] on pre-API 33 or low-RAM devices.
 */
@Composable
fun SquareTunnelCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    val context = LocalContext.current
    val supported = remember(context) { isProceduralShaderSupported(context) }

    if (supported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        SquareTunnelShaderCanvas(
            bitmap = bitmap,
            modifier = modifier,
            isPlaying = isPlaying,
        )
    } else {
        KenBurnsCanvas(
            bitmap = bitmap,
            modifier = modifier,
            isPlaying = isPlaying,
        )
    }
}
