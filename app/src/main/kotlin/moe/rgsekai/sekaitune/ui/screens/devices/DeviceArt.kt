package moe.rgsekai.sekaitune.ui.screens.devices

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import moe.rgsekai.sekaitune.link.DeviceKind

private val Body = Color(0xFF3A3A3D)
private val Edge = Color.White.copy(alpha = 0.55f)

// a phone handing over to a tv with a dashed arrow between them
@Composable
internal fun LinkPicture(modifier: Modifier, scale: Float = 1f, centered: Boolean = true) {
    Canvas(modifier) {
        val phone = Size(20.dp.toPx() * scale, 34.dp.toPx() * scale)
        val tv = Size(38.dp.toPx() * scale, 26.dp.toPx() * scale)
        val gap = 34.dp.toPx() * scale
        val total = phone.width + gap + tv.width
        val left = if (centered) (size.width - total) / 2 else 0f
        val middle = size.height / 2
        drawPhone(Offset(left, middle - phone.height / 2), phone)
        val tvLeft = left + phone.width + gap
        drawTv(Offset(tvLeft, middle - tv.height / 2 - 2.dp.toPx() * scale), tv)
        drawArrow(
            from = Offset(left + phone.width + 7.dp.toPx() * scale, middle),
            to = Offset(tvLeft - 7.dp.toPx() * scale, middle),
            width = 1.5.dp.toPx() * scale.coerceAtLeast(1f),
            dash = 4.dp.toPx() * scale,
        )
    }
}

// one device drawn large: a tv on its stand, or a phone, tablet or car screen
@Composable
internal fun DevicePicture(kind: DeviceKind, modifier: Modifier) {
    Canvas(modifier) {
        when (kind) {
            DeviceKind.Tv -> {
                val screen = Size(size.width * 0.8f, size.height * 0.78f)
                drawTv(Offset((size.width - screen.width) / 2, 0f), screen)
            }
            DeviceKind.Phone -> {
                val body = Size(size.height * 0.52f, size.height)
                drawPhone(Offset((size.width - body.width) / 2, 0f), body)
            }
            DeviceKind.Tablet, DeviceKind.Car -> {
                val body = Size(size.width * 0.7f, size.height * 0.86f)
                drawPhone(Offset((size.width - body.width) / 2, (size.height - body.height) / 2), body)
            }
        }
    }
}

private fun DrawScope.drawPhone(topLeft: Offset, body: Size) {
    val corner = CornerRadius(body.width * 0.2f)
    drawRoundRect(Body, topLeft, body, corner)
    drawRoundRect(Edge, topLeft, body, corner, style = Stroke(1.2.dp.toPx()))
}

private fun DrawScope.drawTv(topLeft: Offset, screen: Size) {
    val stand = Size(screen.width * 0.36f, screen.height * 0.12f)
    val standTop = Offset(topLeft.x + (screen.width - stand.width) / 2, topLeft.y + screen.height - stand.height * 0.2f)
    drawRoundRect(Body, standTop, stand, CornerRadius(stand.height / 2))
    drawRoundRect(Edge, standTop, stand, CornerRadius(stand.height / 2), style = Stroke(1.dp.toPx()))
    val corner = CornerRadius(screen.height * 0.16f)
    drawRoundRect(Body, topLeft, screen, corner)
    drawRoundRect(Edge, topLeft, screen, corner, style = Stroke(1.2.dp.toPx()))
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, width: Float, dash: Float) {
    val color = Color.White.copy(alpha = 0.8f)
    val head = width * 4f
    drawLine(color, from, Offset(to.x - width, to.y), width, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f)))
    drawLine(color, Offset(to.x - head, to.y - head), to, width, StrokeCap.Round)
    drawLine(color, Offset(to.x - head, to.y + head), to, width, StrokeCap.Round)
}

