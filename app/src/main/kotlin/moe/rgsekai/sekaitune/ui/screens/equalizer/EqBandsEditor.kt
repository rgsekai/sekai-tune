/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.eq.data.FilterType
import moe.rgsekai.sekaitune.eq.data.ParametricEQBand
import moe.rgsekai.sekaitune.viewmodels.frequencyLabel
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EqBandsSection(
    nodes: List<ParametricEQBand>,
    onGain: (index: Int, gain: Double) -> Unit,
) {
    EqSectionLabel("Bands")
    EqCard(padding = 12.dp) {
        BandSliders(nodes, onGain)
        Spacer(Modifier.height(10.dp))
        Text(
            "Sweep across the sliders to draw a curve",
            fontSize = 12.sp,
            color = EqColors.Faint,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun BandSliders(nodes: List<ParametricEQBand>, onGain: (Int, Double) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val columns = remember(nodes) {
        nodes.indices.filter { nodes[it].filterType != FilterType.LPQ && nodes[it].filterType != FilterType.HPQ }
            .sortedBy { nodes[it].frequency }
    }
    val currentNodes by rememberUpdatedState(nodes)
    val currentColumns by rememberUpdatedState(columns)
    val currentOnGain by rememberUpdatedState(onGain)
    var active by remember { mutableIntStateOf(-1) }

    val valueStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = EqColors.Secondary)
    val freqStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = EqColors.Faint)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(EqColors.Stage)
            .border(1.dp, EqColors.StageLine, RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val top = 30.dp.toPx()
                    val bottom = size.height - 30.dp.toPx()
                    val cols = currentColumns
                    if (cols.isEmpty()) return@awaitEachGesture
                    val colWidth = size.width / cols.size
                    fun columnAt(x: Float) = (x / colWidth).toInt().coerceIn(0, cols.size - 1)
                    fun gainAt(y: Float): Double {
                        val g = GraphRangeDb - ((y - top) / (bottom - top)).coerceIn(0f, 1f) * 2 * GraphRangeDb
                        val r = (g * 2).roundToInt() / 2.0
                        return if (abs(r) < 0.5) 0.0 else r
                    }
                    var lastCol = columnAt(down.position.x)
                    var lastGain = gainAt(down.position.y)
                    active = lastCol
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnGain(cols[lastCol], lastGain)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val col = columnAt(change.position.x)
                            val gain = gainAt(change.position.y)
                            if (col != lastCol) {
                                val dir = if (col > lastCol) 1 else -1
                                var c = lastCol + dir
                                while (c != col) {
                                    val t = (c - lastCol).toDouble() / (col - lastCol)
                                    currentOnGain(cols[c], ((lastGain + (gain - lastGain) * t) * 2).roundToInt() / 2.0)
                                    c += dir
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            if (currentNodes[cols[col]].gain != gain) currentOnGain(cols[col], gain)
                            lastCol = col
                            lastGain = gain
                            active = col
                        }
                    } finally {
                        active = -1
                    }
                }
            },
    ) {
        if (columns.isEmpty()) return@Canvas
        val top = 30.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        val colWidth = size.width / columns.size
        fun y(db: Double) = top + ((GraphRangeDb - db) / (2 * GraphRangeDb)).toFloat() * (bottom - top)
        val zero = y(0.0)

        drawLine(
            Color.White.copy(alpha = 0.14f), Offset(10.dp.toPx(), zero), Offset(size.width - 10.dp.toPx(), zero),
            1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
        )

        val points = columns.mapIndexed { c, i -> Offset(colWidth * (c + 0.5f), y(nodes[i].gain)) }
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (k in 0 until points.size - 1) {
                    val p0 = points[(k - 1).coerceAtLeast(0)]
                    val p1 = points[k]
                    val p2 = points[k + 1]
                    val p3 = points[(k + 2).coerceAtMost(points.size - 1)]
                    cubicTo(
                        p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                        p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                        p2.x, p2.y,
                    )
                }
            }
            drawPath(path, Color.White.copy(alpha = 0.28f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }

        val trackWidth = 4.dp.toPx()
        columns.forEachIndexed { c, i ->
            val node = nodes[i]
            val cx = colWidth * (c + 0.5f)
            val isActive = c == active
            val color = if (node.enabled) EqColors.Nodes[i % EqColors.Nodes.size] else EqColors.Faint
            drawRoundRect(
                Color.White.copy(alpha = 0.08f),
                Offset(cx - trackWidth / 2, top), Size(trackWidth, bottom - top), CornerRadius(trackWidth / 2),
            )
            val gy = y(node.gain)
            drawRoundRect(
                color.copy(alpha = if (node.enabled) 0.9f else 0.4f),
                Offset(cx - trackWidth / 2, minOf(gy, zero)), Size(trackWidth, abs(gy - zero)), CornerRadius(trackWidth / 2),
            )
            val thumbW = (if (isActive) 28.dp else 24.dp).toPx()
            val thumbH = (if (isActive) 14.dp else 12.dp).toPx()
            drawRoundRect(
                Color.White,
                Offset(cx - thumbW / 2, gy - thumbH / 2), Size(thumbW, thumbH), CornerRadius(thumbH / 2),
            )
            val value = measurer.measure(
                (if (node.gain > 0) "+" else "") + (if (node.gain == node.gain.roundToInt().toDouble()) node.gain.roundToInt().toString()
                else String.format("%.1f", node.gain)),
                if (isActive) valueStyle.copy(color = Color.White) else valueStyle,
            )
            drawText(value, topLeft = Offset(cx - value.size.width / 2f, top - value.size.height - 8.dp.toPx()))
            val freq = measurer.measure(frequencyLabel(node.frequency).removeSuffix("Hz"), freqStyle)
            drawText(freq, topLeft = Offset(cx - freq.size.width / 2f, bottom + 9.dp.toPx()))
        }
    }
}

