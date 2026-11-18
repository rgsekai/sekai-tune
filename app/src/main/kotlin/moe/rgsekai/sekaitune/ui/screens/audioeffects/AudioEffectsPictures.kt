package moe.rgsekai.sekaitune.ui.screens.audioeffects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import moe.rgsekai.sekaitune.eq.AudioEffectsService
import moe.rgsekai.sekaitune.eq.LoudnessSettings
import moe.rgsekai.sekaitune.eq.PlaybackSettings
import moe.rgsekai.sekaitune.eq.ReverbSettings
import moe.rgsekai.sekaitune.eq.SpatialSettings
import moe.rgsekai.sekaitune.eq.VolumeSettings
import moe.rgsekai.sekaitune.eq.audio.AudioTap
import moe.rgsekai.sekaitune.eq.audio.SpatialMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun ListenToAudio() {
    DisposableEffect(Unit) {
        AudioTap.listen()
        onDispose { AudioTap.stopListening() }
    }
}

class LevelFollower {
    private val scratch = FloatArray(1024)
    var level = 0f
        private set
    var punch = 0f
        private set
    private var slow = 0f

    fun update(dt: Float) {
        val now = AudioTap.level(scratch)
        level += (now - level) * (if (now > level) 0.5f else 0.12f)
        slow += (now - slow) * 0.03f
        val p = ((now - slow * 1.25f) * 6f).coerceIn(0f, 1f)
        punch = if (p > punch) p else (punch - dt * 3f).coerceAtLeast(0f)
    }
}

class PictureClock {
    val level = LevelFollower()
    private var lastTime = -1f

    var dt = 0f
        private set

    fun tick(t: Float) {
        if (t == lastTime) {
            dt = 0f
            return
        }
        dt = if (lastTime < 0f) 0f else (t - lastTime).coerceIn(0f, 0.05f)
        level.update(dt)
        lastTime = t
    }
}

@Composable
fun rememberEffectsTime(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val delta = ((now - last) / 1e9f).coerceAtMost(0.05f)
                    time.floatValue += delta
                }
                last = now
            }
        }
    }
    return time
}

@Composable
fun PictureCanvas(draw: DrawScope.(t: Float, clock: PictureClock) -> Unit) {
    val time = rememberEffectsTime()
    val clock = remember { PictureClock() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        clock.tick(t)
        draw(t, clock)
    }
}

// ---- Reverb Picture: Waves bouncing between walls ----------------------------------------------

private class RoomWaves {
    val born = FloatArray(14) { -100f }
    val strength = FloatArray(14)
    var next = 0
    var nextEmit = 0f
    var lastEmit = -10f

    fun emit(t: Float, s: Float) {
        born[next] = t
        strength[next] = s
        next = (next + 1) % born.size
        lastEmit = t
    }
}

@Composable
fun ReverbPicture(settings: ReverbSettings) {
    val room by animateFloatAsState(settings.roomSize, spring(dampingRatio = 0.8f, stiffness = 120f), label = "room")
    val on by animateFloatAsState(if (settings.enabled) 1f else 0f, tween(400), label = "on")
    val waves = remember { RoomWaves() }

    PictureCanvas { t, clock ->
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val speakerX = w * 0.15f
        val backX = w * 0.05f
        val wallX = w * (0.52f + 0.4f * room)
        val speed = w * 0.42f

        val live = AudioTap.isLive
        val punch = clock.level.punch
        if (t >= waves.nextEmit || (live && punch > 0.55f && t - waves.lastEmit > 0.28f)) {
            waves.emit(t, if (live) 0.55f + 0.45f * min(1f, clock.level.level * 3f + punch) else 0.75f)
            waves.nextEmit = t + if (live) 0.9f else 1.3f
        }

        val reflectivity = (0.35f + 0.55f * settings.roomSize) * (1f - 0.55f * settings.damping)
        val echoAlpha = on * (0.35f + 0.65f * settings.wet) * (0.4f + 0.6f * settings.mix)
        var flash = 0f
        val span = wallX - backX

        for (i in waves.born.indices) {
            val age = t - waves.born[i]
            if (age < 0f || age > 8f) continue
            val d = age * speed
            val first = wallX - speakerX
            if (d <= first || on < 0.01f) {
                val x = speakerX + d
                if (x > w + 20f) continue
                val a = waves.strength[i] * exp(-d / (w * 1.6f))
                drawWave(x, cy, h, d, a * (if (d > first) 1f - on else 1f), rightward = true, softness = 0f)
                continue
            }
            val past = d - first
            val bounces = 1 + (past / span).toInt()
            val inLeg = past % span
            val rightward = bounces % 2 == 0
            val x = if (rightward) backX + inLeg else wallX - inLeg
            val a = waves.strength[i] * reflectivity.pow(bounces) * exp(-d / (w * 2.2f)) * echoAlpha
            if (a < 0.02f) continue
            if (inLeg < span * 0.08f && !rightward) flash = max(flash, a * (1f - inLeg / (span * 0.08f)))
            drawWave(x, cy, h, d, a, rightward, softness = settings.damping)
        }

        val wallH = h * 0.62f
        drawRoundRect(
            Color.White.copy(alpha = (0.18f + 0.55f * on + 0.6f * flash).coerceAtMost(1f)),
            Offset(wallX, cy - wallH / 2),
            Size(5.dp.toPx(), wallH),
            CornerRadius(3.dp.toPx()),
        )
        drawSpeaker(Offset(speakerX, cy), 1f + clock.level.punch * 0.12f)
    }
}

private fun DrawScope.drawWave(x: Float, cy: Float, h: Float, travelled: Float, alpha: Float, rightward: Boolean, softness: Float) {
    if (alpha <= 0.005f) return
    val reach = min(h * 0.42f, 14.dp.toPx() + travelled * 0.18f)
    val bulge = reach * 0.42f * if (rightward) 1f else -1f
    val path = Path().apply {
        moveTo(x - bulge * 0.2f, cy - reach)
        quadraticTo(x + bulge, cy, x - bulge * 0.2f, cy + reach)
    }
    if (softness > 0.15f) {
        drawPath(path, Color.White.copy(alpha = alpha * 0.25f * softness), style = Stroke(7.dp.toPx() * softness, cap = StrokeCap.Round))
    }
    drawPath(path, Color.White.copy(alpha = alpha.coerceAtMost(1f)), style = Stroke((2.2f - softness).dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawSpeaker(center: Offset, scale: Float) {
    val s = 9.dp.toPx() * scale
    val body = Path().apply {
        moveTo(center.x - s * 1.1f, center.y - s * 0.45f)
        lineTo(center.x - s * 0.4f, center.y - s * 0.45f)
        lineTo(center.x + s * 0.5f, center.y - s * 1.1f)
        lineTo(center.x + s * 0.5f, center.y + s * 1.1f)
        lineTo(center.x - s * 0.4f, center.y + s * 0.45f)
        lineTo(center.x - s * 1.1f, center.y + s * 0.45f)
        close()
    }
    drawPath(body, Color.White.copy(alpha = 0.92f), style = Stroke(1.6.dp.toPx()))
}

// ---- Loudness Picture: Scrolling Waveform & Threshold ------------------------------------------

private class LevelHistory {
    val values = FloatArray(56)
    var head = 0
    var accumulated = 0f
}

@Composable
fun LoudnessPicture(settings: LoudnessSettings, fx: AudioEffectsService) {
    val on by animateFloatAsState(if (settings.enabled) 1f else 0f, tween(400), label = "on")
    val history = remember { LevelHistory() }

    PictureCanvas { t, clock ->
        history.accumulated += clock.dt
        if (history.accumulated > 1f / 24f) {
            history.accumulated = 0f
            val v = if (AudioTap.isLive) clock.level.level * 2.6f
            else 0.18f + 0.12f * sin(t * 2.1f) + 0.1f * sin(t * 5.3f + 1f) + 0.06f * sin(t * 11f)
            history.values[history.head] = v.coerceIn(0.005f, 1.4f)
            history.head = (history.head + 1) % history.values.size
        }
        val w = size.width
        val h = size.height
        val top = 18.dp.toPx()
        val bottom = h - 14.dp.toPx()
        fun yOf(db: Float) = bottom - ((db + 42f) / 42f).coerceIn(0f, 1f) * (bottom - top)
        val ceiling = yOf(-0.5f)

        val compression = settings.compression
        val threshold = -10f - 24f * compression
        val slope = 1f - 1f / (1f + 3f * compression)
        val makeup = if (compression > 0f) -threshold * slope * 0.5f else 0f

        val n = history.values.size
        val barW = (w - 24.dp.toPx()) / n
        for (k in 0 until n) {
            val v = history.values[(history.head + k) % n]
            val inDb = 20f * log10(max(v, 1e-4f))
            val reduced = if (inDb > threshold) (threshold - inDb) * slope else 0f
            val outDb = (inDb + (settings.boostDb + makeup + reduced) * on).coerceAtMost(-0.5f)
            val x = 12.dp.toPx() + k * barW
            val yIn = yOf(min(inDb, 0f))
            val yOut = yOf(outDb)
            val mid = (top + bottom) / 2f

            val inHalf = (bottom - yIn) / 2f
            val outHalf = (bottom - yOut) / 2f
            drawRoundRect(
                Color.White.copy(alpha = 0.12f),
                Offset(x, mid - inHalf),
                Size(barW * 0.62f, inHalf * 2),
                CornerRadius(barW * 0.3f),
            )
            val limited = on > 0.5f && outDb >= -0.6f
            drawRoundRect(
                if (limited) Color.White else Color.White.copy(alpha = 0.72f),
                Offset(x, mid - outHalf),
                Size(barW * 0.62f, outHalf * 2),
                CornerRadius(barW * 0.3f),
            )
        }

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        val mid = (top + bottom) / 2f
        val half = (bottom - ceiling) / 2f
        drawLine(
            Color.White.copy(alpha = 0.3f * on + 0.08f),
            Offset(10.dp.toPx(), mid - half),
            Offset(w - 10.dp.toPx(), mid - half),
            1.dp.toPx(),
            pathEffect = dash,
        )
        drawLine(
            Color.White.copy(alpha = 0.3f * on + 0.08f),
            Offset(10.dp.toPx(), mid + half),
            Offset(w - 10.dp.toPx(), mid + half),
            1.dp.toPx(),
            pathEffect = dash,
        )

        val reduction = (-fx.loudnessReductionDb).coerceIn(0f, 12f) / 12f * on
        if (reduction > 0.01f) {
            val meterH = (bottom - top) * reduction
            drawRoundRect(
                Color.White.copy(alpha = 0.85f),
                Offset(w - 7.dp.toPx(), top),
                Size(3.dp.toPx(), meterH),
                CornerRadius(2.dp.toPx()),
            )
        }
    }
}

// ---- Spatial Picture: Top-down Listener Head & Acoustic Arcs ----------------------------------

@Composable
fun SpatialPicture(settings: SpatialSettings) {
    val on by animateFloatAsState(if (settings.enabled) settings.amount else 0f, tween(400), label = "on")
    val speakers by animateFloatAsState(if (settings.mode == SpatialMode.Speakers) 1f else 0f, tween(500), label = "mode")
    val angle by animateFloatAsState(settings.speakerAngle, spring(dampingRatio = 0.8f, stiffness = 140f), label = "angle")
    val feed by animateFloatAsState(settings.feedDb, tween(300), label = "feed")

    PictureCanvas { t, clock ->
        val w = size.width
        val h = size.height
        val head = Offset(w / 2f, h * 0.66f)
        val r = 17.dp.toPx()
        val earL = Offset(head.x - r, head.y)
        val earR = Offset(head.x + r, head.y)
        val pulse = clock.level.punch

        drawCircle(Color.White.copy(alpha = 0.9f), r, head, style = Stroke(1.6.dp.toPx()))
        drawPath(
            Path().apply {
                moveTo(head.x - 4.dp.toPx(), head.y - r + 1.dp.toPx())
                lineTo(head.x, head.y - r - 6.dp.toPx())
                lineTo(head.x + 4.dp.toPx(), head.y - r + 1.dp.toPx())
            },
            Color.White.copy(alpha = 0.9f),
            style = Stroke(1.6.dp.toPx()),
        )
        drawCircle(Color.White, 3.dp.toPx(), earL)
        drawCircle(Color.White, 3.dp.toPx(), earR)

        val inside = 1f - on
        if (inside > 0.01f) {
            drawCircle(Color(0xFF6BBAF2).copy(alpha = 0.7f * inside), 5.dp.toPx() + 2.dp.toPx() * pulse, Offset(head.x - r * 0.45f, head.y))
            drawCircle(Color(0xFFF28BA8).copy(alpha = 0.7f * inside), 5.dp.toPx() + 2.dp.toPx() * pulse, Offset(head.x + r * 0.45f, head.y))
        }

        val ring = min(w * 0.36f, h * 0.55f)
        val crossfeedAmount = on * (1f - speakers)
        val speakerAmount = on * speakers
        val rad = Math.toRadians(angle.toDouble())
        val spL = Offset(head.x - (sin(rad) * ring).toFloat(), head.y - (cos(rad) * ring).toFloat())
        val spR = Offset(head.x + (sin(rad) * ring).toFloat(), head.y - (cos(rad) * ring).toFloat())

        if (speakerAmount > 0.01f) {
            listOf(spL to Color(0xFF6BBAF2), spR to Color(0xFFF28BA8)).forEachIndexed { side, (sp, color) ->
                val near = if (side == 0) earL else earR
                val far = if (side == 0) earR else earL
                flowLine(sp, near, color.copy(alpha = 0.8f * speakerAmount), t, 1f)
                flowLine(sp, far, color.copy(alpha = 0.32f * speakerAmount), t - 0.08f, 0.6f)
                drawRoundRect(
                    color.copy(alpha = speakerAmount),
                    Offset(sp.x - 6.dp.toPx(), sp.y - 8.dp.toPx()),
                    Size(12.dp.toPx(), 16.dp.toPx()),
                    CornerRadius(3.dp.toPx()),
                )
            }
        }

        if (crossfeedAmount > 0.01f) {
            val strength = (1f - (feed - 1f) / 14f).coerceIn(0.2f, 1f)
            val lift = r * 2.4f
            listOf(earL to earR to Color(0xFF6BBAF2), earR to earL to Color(0xFFF28BA8)).forEach { (pair, color) ->
                val (from, to) = pair
                val path = Path().apply {
                    moveTo(from.x, from.y - 4.dp.toPx())
                    cubicTo(from.x, from.y - lift, to.x, to.y - lift, to.x, to.y - 4.dp.toPx())
                }
                drawPath(
                    path,
                    color.copy(alpha = 0.55f * crossfeedAmount * strength),
                    style = Stroke(
                        1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), -t * 40.dp.toPx()),
                    ),
                )
            }
            drawCircle(Color(0xFF6BBAF2).copy(alpha = 0.8f * crossfeedAmount), 5.dp.toPx() + 2.dp.toPx() * pulse, Offset(earL.x - 10.dp.toPx(), earL.y))
            drawCircle(Color(0xFFF28BA8).copy(alpha = 0.8f * crossfeedAmount), 5.dp.toPx() + 2.dp.toPx() * pulse, Offset(earR.x + 10.dp.toPx(), earR.y))
        }
    }
}

private fun DrawScope.flowLine(from: Offset, to: Offset, color: Color, t: Float, weight: Float) {
    drawLine(color.copy(alpha = color.alpha * 0.35f), from, to, 1.2.dp.toPx() * weight)
    for (k in 0 until 3) {
        val p = ((t * 0.9f + k / 3f) % 1f)
        drawCircle(color, 2.2.dp.toPx() * weight, Offset(from.x + (to.x - from.x) * p, from.y + (to.y - from.y) * p))
    }
}

// ---- Atmos Picture: 3D Dome & Orbiting Sound Nodes --------------------------------------------

@Composable
fun AtmosPicture(enabled: Boolean, renderOnHeadphones: Boolean) {
    val on by animateFloatAsState(if (enabled) 1f else 0f, tween(500), label = "on")
    val render by animateFloatAsState(if (enabled && renderOnHeadphones) 1f else 0f, tween(500), label = "render")

    PictureCanvas { t, clock ->
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h * 0.74f)
        val rx = w * 0.36f
        val ry = h * 0.2f
        val dome = h * 0.56f
        val line = Color.White.copy(alpha = 0.12f + 0.2f * on)

        drawOval(line, Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(1.dp.toPx()))
        val domePath = Path().apply {
            moveTo(c.x - rx, c.y)
            cubicTo(c.x - rx, c.y - dome * 1.3f, c.x + rx, c.y - dome * 1.3f, c.x + rx, c.y)
        }
        drawPath(
            domePath,
            line.copy(alpha = line.alpha * on),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))),
        )

        val bed = listOf(-30f, 30f, 0f, -110f, 110f, -150f, 150f)
        bed.forEach { az ->
            val a = Math.toRadians(az.toDouble() - 90)
            val p = Offset(c.x + (cos(a) * rx).toFloat(), c.y + (sin(a) * ry).toFloat())
            drawCircle(Color.White.copy(alpha = 0.25f + 0.45f * on), 2.6.dp.toPx(), p)
        }
        if (on > 0.01f) {
            listOf(-45f, 45f, -135f, 135f).forEach { az ->
                val a = Math.toRadians(az.toDouble() - 90)
                val p = Offset(c.x + (cos(a) * rx * 0.62f).toFloat(), c.y + (sin(a) * ry * 0.62f).toFloat() - dome * 0.72f)
                drawCircle(Color.White.copy(alpha = 0.55f * on), 2.6.dp.toPx(), p)
            }
        }

        drawCircle(Color.White.copy(alpha = 0.9f), 9.dp.toPx(), Offset(c.x, c.y - 4.dp.toPx()), style = Stroke(1.5.dp.toPx()))

        val colors = listOf(Color(0xFF6BBAF2), Color(0xFFF28BA8), Color(0xFF8FD98B))
        colors.forEachIndexed { k, color ->
            val speed = 0.35f + k * 0.17f
            val phase = t * speed + k * 2.1f
            val height = (0.25f + 0.5f * (0.5f + 0.5f * sin(phase * 1.3f + k))) * dome * on
            val x = c.x + cos(phase) * rx * 0.82f * (0.35f + 0.65f * on)
            val depth = sin(phase)
            val y = c.y + depth * ry * 0.82f * on - height - 4.dp.toPx() * (1f - on)
            val scale = 1f + 0.35f * depth * on
            val glow = 0.25f + 0.2f * clock.level.punch
            drawCircle(color.copy(alpha = glow), 9.dp.toPx() * scale, Offset(x, y))
            drawCircle(color, 4.dp.toPx() * scale, Offset(x, y))
            if (render > 0.01f) {
                drawLine(color.copy(alpha = 0.35f * render), Offset(x, y), Offset(c.x, c.y - 4.dp.toPx()), 1.dp.toPx())
            }
        }
    }
}

// ---- Speed Picture: Spinning Vinyl Record & Pitch Wave -----------------------------------------

private class Spin { var angle = 0f; var phase = 0f }

@Composable
fun SpeedPicture(settings: PlaybackSettings) {
    val speed by animateFloatAsState(settings.speed, spring(dampingRatio = 0.9f, stiffness = 80f), label = "speed")
    val pitch by animateFloatAsState(settings.pitch, spring(dampingRatio = 0.9f, stiffness = 80f), label = "pitch")
    val spin = remember { Spin() }

    PictureCanvas { _, clock ->
        val dt = clock.dt
        spin.angle = (spin.angle + dt * 200f * speed) % 360f
        spin.phase += dt * 2.4f * speed
        val h = size.height
        val w = size.width
        val disc = h * 0.36f
        val c = Offset(disc + 18.dp.toPx(), h / 2f)

        drawCircle(Color(0xFF16161A), disc, c)
        for (g in 1..5) drawCircle(Color.White.copy(alpha = 0.06f), disc * (0.45f + g * 0.1f), c, style = Stroke(1.dp.toPx()))
        rotate(spin.angle, c) {
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), c),
                startAngle = 0f,
                sweepAngle = 70f,
                useCenter = true,
                topLeft = Offset(c.x - disc, c.y - disc),
                size = Size(disc * 2, disc * 2),
            )
            drawCircle(Color.White.copy(alpha = 0.9f), disc * 0.3f, c)
            drawCircle(Color.Black, disc * 0.06f, Offset(c.x, c.y - disc * 0.16f))
        }
        drawCircle(Color.Black, disc * 0.05f, c)

        val left = c.x + disc + 22.dp.toPx()
        val right = w - 16.dp.toPx()
        val amp = h * (0.16f + 0.12f * clock.level.level.coerceAtMost(1f))
        val cycles = 2.5f * pitch
        val path = Path()
        var x = left
        while (x <= right) {
            val p = (x - left) / (right - left)
            val env = sin(p * PI.toFloat())
            val y = h / 2f + sin((p * cycles * 2f * PI.toFloat()) - spin.phase) * amp * env
            if (x == left) path.moveTo(x, y) else path.lineTo(x, y)
            x += 2.dp.toPx()
        }
        drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---- Volume Curve Picture: Power Graph & Level Cursor -----------------------------------------

@Composable
fun VolumeCurvePicture(settings: VolumeSettings, volume: Float) {
    val exponent by animateFloatAsState(if (settings.enabled) settings.exponent else 1f, spring(dampingRatio = 0.85f, stiffness = 160f), label = "exp")
    val limit by animateFloatAsState(if (settings.enabled) settings.limit else 1f, tween(300), label = "limit")
    val position by animateFloatAsState(volume, tween(200), label = "vol")

    PictureCanvas { _, clock ->
        val w = size.width
        val h = size.height
        val plot = Rect(16.dp.toPx(), 14.dp.toPx(), w - 16.dp.toPx(), h - 14.dp.toPx())
        fun at(p: Float) = Offset(plot.left + p * plot.width, plot.bottom - limit * p.pow(exponent) * plot.height)

        drawLine(
            Color.White.copy(alpha = 0.16f),
            Offset(plot.left, plot.bottom),
            Offset(plot.right, plot.top),
            1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
        if (limit < 0.999f) {
            val y = plot.bottom - limit * plot.height
            drawLine(
                Color.White.copy(alpha = 0.3f),
                Offset(plot.left, y),
                Offset(plot.right, y),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
            )
        }
        val curve = Path()
        val fill = Path().apply { moveTo(plot.left, plot.bottom) }
        for (i in 0..60) {
            val p = at(i / 60f)
            if (i == 0) curve.moveTo(p.x, p.y) else curve.lineTo(p.x, p.y)
            fill.lineTo(p.x, p.y)
        }
        fill.lineTo(plot.right, plot.bottom)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), plot.top, plot.bottom))
        drawPath(curve, Color.White, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))

        for (i in 1..9) {
            val p = at(i / 10f)
            drawLine(Color.White.copy(alpha = 0.25f), Offset(p.x, plot.bottom), Offset(p.x, p.y), 1.dp.toPx())
        }
        val dot = at(position.coerceIn(0f, 1f))
        drawCircle(Color.White.copy(alpha = 0.18f + 0.2f * clock.level.punch), 11.dp.toPx(), dot)
        drawCircle(Color.White, 5.dp.toPx(), dot)
    }
}

