package moe.rgsekai.sekaitune.ui.player.flagship

import android.os.Build
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import moe.rgsekai.sekaitune.ui.component.asAgslPaintShader
import moe.rgsekai.sekaitune.ui.component.createAgslShader
import moe.rgsekai.sekaitune.ui.component.setAgslColorUniform
import moe.rgsekai.sekaitune.ui.component.setAgslUniform
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// one shader for every ambient backdrop: each draw sets its own numbers first, so nothing compiles on a song change
private val sharedAmbientShader: Any? by lazy {
    if (Build.VERSION.SDK_INT >= 33) createAgslShader(AmbientShaderSource) else null
}

// the clock's loop, in seconds: every drift in the shader turns a whole number of times in it, so the wrap never shows
private const val LoopSeconds = 100f

// the colours are soft pools, so a quarter of the pixels scaled back up look the same at a sixteenth of the work
private const val Downscale = 4

// the drift is slow, so 30 steps a second look like 120 and redraw a quarter as often
private const val StepsPerSecond = 30f

// the cover's colours drifting under the player; time is in seconds and loops every 100
@Composable
fun AmbientBackdrop(
    colors: AmbientColors,
    modifier: Modifier = Modifier,
    time: () -> Float,
) {
    val currentTime by rememberUpdatedState(time)
    // only tells the drawing when the next step is due, however often the clock itself ticks
    val step by remember { derivedStateOf { floor(currentTime() * StepsPerSecond) } }
    val shader = sharedAmbientShader
    Spacer(
        modifier = modifier
            .downscaledLayer()
            .then(
                if (shader != null && Build.VERSION.SDK_INT >= 33) {
                    Modifier.drawWithCache {
                        val brush = ShaderBrush(shader.asAgslPaintShader())
                        onDrawBehind {
                            shader.setAgslUniform("resolution", size.width, size.height)
                            shader.setAgslUniform("time", step / StepsPerSecond)
                            shader.setAgslColorUniform("seamLeft", colors.tone(ToneSeamLeft).toArgb())
                            shader.setAgslColorUniform("seamRight", colors.tone(ToneSeamRight).toArgb())
                            shader.setAgslColorUniform("deep", colors.tone(ToneDeep).toArgb())
                            shader.setAgslColorUniform("accent", colors.tone(ToneAccent).toArgb())
                            drawRect(brush)
                        }
                    }
                } else {
                    Modifier.drawWithCache {
                        val deep = colors.tone(ToneDeep)
                        val radius = size.maxDimension * 0.55f
                        val blobs = listOf(
                            colors.tone(ToneSeamLeft),
                            colors.tone(ToneSeamRight),
                            colors.tone(ToneAccent),
                        ).map { tone ->
                            Brush.radialGradient(
                                colors = listOf(tone, tone.copy(alpha = 0f)),
                                center = Offset.Zero,
                                radius = radius,
                            )
                        }
                        onDrawBehind {
                            drawRect(deep)
                            val t = step / StepsPerSecond * (2f * PI.toFloat() / LoopSeconds)
                            blobs.forEachIndexed { i, brush ->
                                val cx = size.width * (0.25f + 0.5f * (i % 2) + 0.14f * sin(t * (i + 1) + i * 1.7f))
                                val cy = size.height * (0.55f + 0.18f * i / 2f + 0.08f * cos(t * (i + 2) + i))
                                translate(cx, cy) {
                                    drawCircle(brush, radius = radius, center = Offset.Zero)
                                }
                            }
                        }
                    }
                }
            ),
    )
}

private fun Modifier.downscaledLayer(): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val small = measurable.measure(
        Constraints.fixed(
            ((width + Downscale - 1) / Downscale).coerceAtLeast(1),
            ((height + Downscale - 1) / Downscale).coerceAtLeast(1),
        )
    )
    layout(width, height) {
        small.placeWithLayer(0, 0) {
            scaleX = Downscale.toFloat()
            scaleY = Downscale.toFloat()
            transformOrigin = TransformOrigin(0f, 0f)
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }
}

@Composable
fun rememberAmbientClock(running: Boolean): () -> Float {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (isActive) {
            withInfiniteAnimationFrameNanos { nanos ->
                time.floatValue = ((nanos / 1_000_000L) % (LoopSeconds * 1000f).toLong()) / 1000f
            }
        }
    }
    return { time.floatValue }
}

private const val AmbientShaderSource = """
uniform float2 resolution;
uniform float time;
layout(color) uniform half4 seamLeft;
layout(color) uniform half4 seamRight;
layout(color) uniform half4 deep;
layout(color) uniform half4 accent;

const float TAU = 6.2831853;

float pool(float2 p, float2 centre, float spread) {
    float2 d = p - centre;
    return exp(-dot(d, d) * spread);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float aspect = resolution.y / max(resolution.x, 1.0);
    float2 p = float2(uv.x, uv.y * aspect);
    float w = TAU / 50.0;
    float t = time;

    float2 a = float2(0.22 + 0.16 * sin(t * w + 0.4), (0.58 + 0.07 * cos(t * w * 2.0)) * aspect);
    float2 b = float2(0.80 + 0.14 * cos(t * w + 1.3), (0.62 + 0.08 * sin(t * w * 2.0 + 0.7)) * aspect);
    float2 c = float2(0.48 + 0.24 * cos(t * w * 3.0 + 2.1), (0.92 + 0.06 * sin(t * w * 1.9)) * aspect);
    float2 d = float2(0.62 + 0.22 * sin(t * w * 2.0 + 3.4), (0.78 + 0.10 * cos(t * w * 3.0 + 0.4)) * aspect);

    float wa = pool(p, a, 3.2);
    float wb = pool(p, b, 3.2);
    float wc = pool(p, c, 2.4);
    float wd = pool(p, d, 4.0) * 0.8;
    float sum = wa + wb + wc + wd + 0.0001;
    float3 col = (float3(seamLeft.rgb) * wa + float3(seamRight.rgb) * wb + float3(deep.rgb) * wc + float3(accent.rgb) * wd) / sum;

    col *= 1.0 - 0.16 * smoothstep(0.6, 1.0, uv.y);
    float grain = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
    col += (grain - 0.5) / 255.0;
    return half4(half3(col), 1.0);
}
"""

