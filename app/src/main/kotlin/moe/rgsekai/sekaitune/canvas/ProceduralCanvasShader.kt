/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import android.app.ActivityManager
import android.content.Context
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
import kotlinx.coroutines.isActive

/**
 * Tuned audio-reactive amplitude scaling factor for Kawarp displacement.
 */
const val KAWARP_AUDIO_REACTIVE_INTENSITY: Float = 1.80f

/**
 * AGSL (Android Graphics Shading Language) source implementing the "Kawarp"
 * fluid UV warp distortion effect.
 *
 * Uses isotropic normalization by min(width, height) to ensure that harmonic wave
 * frequencies and physical pixel displacements remain uniform and distortion-free across
 * all aspect ratios (e.g. 1:1 square mini-player vs portrait Now Playing screen).
 * Applies center-anchored breathing zoom to prevent edge clamping artifacts.
 */
const val KAWARP_AGSL_SHADER: String = """
    uniform shader image;
    uniform float2 resolution;
    uniform float time;
    uniform float bassEnergy;

    half4 main(float2 fragCoord) {
        // Isotropic coordinates normalized by min(width, height).
        // This ensures identical wave frequency and physical pixel displacement
        // across any aspect ratio (1:1, 9:16 portrait, 16:9 landscape) without distortion.
        float minDim = min(resolution.x, resolution.y);
        float2 normCoord = fragCoord / minDim;
        
        float t = time * 0.4;
        float audioBoost = 1.0 + bassEnergy * 1.80;
        
        // Multi-frequency harmonic wave displacement in isotropic space modulated by audioBoost
        float warpX = (sin(normCoord.y * 6.2831853 + t * 1.2) * 0.015
                    + sin(normCoord.y * 12.5663706 - t * 0.9) * 0.007
                    + cos(normCoord.x * 4.7123889 + t * 0.7) * 0.005) * audioBoost;
                    
        float warpY = (cos(normCoord.x * 6.2831853 + t * 1.1) * 0.015
                    + cos(normCoord.x * 12.5663706 - t * 0.8) * 0.007
                    + sin(normCoord.y * 4.7123889 - t * 0.6) * 0.005) * audioBoost;

        // Convert displacement to physical layer pixel space
        float2 dispPx = float2(warpX, warpY) * minDim;

        // Center-anchored breathing zoom around the actual visual center (resolution * 0.5)
        float2 center = resolution * 0.5;
        float zoom = 1.03 + sin(t * 0.5) * 0.015;
        float2 sampleCoord = center + ((fragCoord - center) / zoom) + dispPx;
        
        // Clamp sample coordinate safely to [0.0, resolution] bounds
        sampleCoord = clamp(sampleCoord, float2(0.0), resolution);
        
        return image.eval(sampleCoord);
    }
"""

/**
 * Checks whether the current device environment supports hardware-accelerated AGSL runtime shaders.
 * Requires Android 13+ (API 33, Tiramisu) and a non-low-RAM device profile.
 */
fun isProceduralShaderSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return false
    }
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return activityManager?.isLowRamDevice != true
}

/**
 * Pure evaluation overload for testing device capability branching.
 */
fun isProceduralShaderSupported(sdkInt: Int, isLowRamDevice: Boolean): Boolean {
    return sdkInt >= Build.VERSION_CODES.TIRAMISU && !isLowRamDevice
}

/**
 * Composable that applies the Kawarp AGSL fluid distortion shader to a static album art [Bitmap].
 * Only active on API 33+ devices.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun ProceduralShaderCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    audioSessionId: Int = 0,
    audioReactive: Boolean = false,
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

    val bassEnergy by rememberBassEnergy(
        audioSessionId = audioSessionId,
        isPlaying = isPlaying,
        enabled = audioReactive,
    )

    val runtimeShader = remember { RuntimeShader(KAWARP_AGSL_SHADER) }

    Box(
        modifier = modifier
            .graphicsLayer {
                val currentWidth = this.size.width
                val currentHeight = this.size.height
                if (currentWidth > 0f && currentHeight > 0f) {
                    runtimeShader.setFloatUniform("resolution", currentWidth, currentHeight)
                    runtimeShader.setFloatUniform("time", time)
                    runtimeShader.setFloatUniform("bassEnergy", bassEnergy)
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
 * Unified procedural canvas entry point that selects the AGSL shader path on API 33+ or
 * falls back to [KenBurnsCanvas] on pre-API 33 or low-RAM devices.
 */
@Composable
fun ProceduralCanvas(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
    audioSessionId: Int = 0,
    audioReactive: Boolean = false,
) {
    val context = LocalContext.current
    val supported = remember(context) { isProceduralShaderSupported(context) }

    if (supported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ProceduralShaderCanvas(
            bitmap = bitmap,
            modifier = modifier,
            isPlaying = isPlaying,
            audioSessionId = audioSessionId,
            audioReactive = audioReactive,
        )
    } else {
        KenBurnsCanvas(
            bitmap = bitmap,
            modifier = modifier,
            isPlaying = isPlaying,
        )
    }
}
