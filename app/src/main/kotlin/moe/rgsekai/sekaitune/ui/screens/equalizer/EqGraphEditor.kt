/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.equalizer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.eq.audio.AudioTap
import moe.rgsekai.sekaitune.eq.audio.ResponseCurve
import moe.rgsekai.sekaitune.eq.audio.SpectrumAnalyzer
import moe.rgsekai.sekaitune.eq.data.FilterType
import moe.rgsekai.sekaitune.eq.data.ParametricEQBand
import moe.rgsekai.sekaitune.viewmodels.EqualizerViewModel
import moe.rgsekai.sekaitune.viewmodels.filterTypeLabel
import moe.rgsekai.sekaitune.viewmodels.frequencyLabel
import moe.rgsekai.sekaitune.viewmodels.gainLabel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

internal const val GraphRangeDb = 12.0
private const val MinLog = 1.30103 // log10(20)
private const val MaxLog = 4.30103 // log10(20000)
internal val GraphFrequencies: DoubleArray = ResponseCurve.logSpaced(220)
private val SpectrumBands = ResponseCurve.logSpaced(56, 24.0, 18_000.0)
private val GridFrequencies = listOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10_000.0, 20_000.0)
private val GridLabels = listOf("20", "50", "100", "200", "500", "1k", "2k", "5k", "10k", "20k")

internal class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float, val rangeDb: Double = GraphRangeDb) {
    fun x(f: Double): Float = left + ((log10(f) - MinLog) / (MaxLog - MinLog)).toFloat() * (right - left)
    fun frequencyAt(x: Float): Double = 10.0.pow(MinLog + ((x - left) / (right - left)).coerceIn(0f, 1f) * (MaxLog - MinLog))
    fun y(db: Double): Float = top + ((rangeDb - db) / (2 * rangeDb)).toFloat() * (bottom - top)
    fun gainAt(y: Float): Double = rangeDb - ((y - top) / (bottom - top)).coerceIn(0f, 1f) * 2 * rangeDb
}

private fun ParametricEQBand.nodeGain(): Double = when (filterType) {
    FilterType.LPQ, FilterType.HPQ -> 0.0
    else -> gain
}

@Composable
fun EqGraphSection(
    nodes: List<ParametricEQBand>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (index: Int, frequency: Double, gain: Double) -> Unit,
    onAdd: (frequency: Double, gain: Double) -> Unit,
    onUpdate: (index: Int, transform: (ParametricEQBand) -> ParametricEQBand) -> Unit,
    onRemove: (Int) -> Unit,
    canAdd: Boolean,
) {
    val index = selected.coerceIn(0, (nodes.size - 1).coerceAtLeast(0))
    EqSectionLabel("Graph editor")
    EqCard(padding = 12.dp) {
        val node = nodes.getOrNull(index)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "FREQUENCY RESPONSE",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp,
                color = EqColors.Secondary,
                modifier = Modifier.weight(1f),
            )
            if (node != null) {
                Text(
                    "${frequencyLabel(node.frequency)} ${gainLabel(node.nodeGain())}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = EqColors.Nodes[index % EqColors.Nodes.size],
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        EqGraph(nodes = nodes, selected = index, onSelect = onSelect, onMove = onMove, onAdd = onAdd)
        Spacer(Modifier.height(10.dp))
        Text(
            "Drag a node to shape the sound · double-tap to add one",
            fontSize = 12.sp,
            color = EqColors.Faint,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
    Spacer(Modifier.height(12.dp))

    val order = nodes.indices.sortedBy { nodes[it].frequency }
    val chips = order.map {
        EqChipItem(
            key = it.toString(),
            label = frequencyLabel(nodes[it].frequency),
            dot = EqColors.Nodes[it % EqColors.Nodes.size],
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f)) {
            EqChips(
                chips = chips,
                selectedKey = index.toString(),
                onSelect = { onSelect(it.toInt()) },
            )
        }
        if (canAdd) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(EqColors.Chip)
                    .clickable { onAdd(freeFrequency(nodes), 0.0) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = "Add a band",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    val node = nodes.getOrNull(index) ?: return
    NodeDetail(
        node = node,
        color = EqColors.Nodes[index % EqColors.Nodes.size],
        canRemove = nodes.size > 1,
        onUpdate = { onUpdate(index, it) },
        onRemove = { onRemove(index) },
    )
}

private fun freeFrequency(nodes: List<ParametricEQBand>): Double {
    val logs = (nodes.map { log10(it.frequency) } + MinLog + MaxLog).sorted()
    var best = 3.0
    var widest = -1.0
    for (i in 0 until logs.size - 1) {
        val gap = logs[i + 1] - logs[i]
        if (gap > widest) {
            widest = gap
            best = (logs[i] + logs[i + 1]) / 2
        }
    }
    return 10.0.pow(best).roundToInt().toDouble()
}

@Composable
private fun EqGraph(
    nodes: List<ParametricEQBand>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (index: Int, frequency: Double, gain: Double) -> Unit,
    onAdd: (frequency: Double, gain: Double) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val curve = remember { ResponseCurve(GraphFrequencies) }
    val perNode = remember(nodes) {
        nodes.map { n -> DoubleArray(GraphFrequencies.size).also { if (n.enabled) curve.accumulate(n, it) } }
    }
    val total = remember(perNode) {
        DoubleArray(GraphFrequencies.size).also { sum -> perNode.forEach { r -> for (i in sum.indices) sum[i] += r[i] } }
    }
    val analyzer = remember { SpectrumAnalyzer(SpectrumBands) }
    var animFrame by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                animFrame = (frameNanos / 1_000_000L).toFloat()
            }
        }
    }

    val currentNodes by rememberUpdatedState(nodes)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnAdd by rememberUpdatedState(onAdd)
    var dragging by remember { mutableIntStateOf(-1) }
    val selectedPulse by animateFloatAsState(
        if (dragging >= 0) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "drag",
    )

    val labelStyle = TextStyle(fontSize = 10.sp, color = EqColors.Faint)
    val plotOf: DrawScope.() -> Plot = {
        Plot(left = 30.dp.toPx(), top = 10.dp.toPx(), right = size.width - 10.dp.toPx(), bottom = size.height - 24.dp.toPx())
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(EqColors.Stage)
            .border(1.dp, EqColors.StageLine, RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { pos ->
                        val plot = Plot(30.dp.toPx(), 10.dp.toPx(), size.width - 10.dp.toPx(), size.height - 24.dp.toPx())
                        val hit = nearest(currentNodes, plot, pos, 44.dp.toPx())
                        if (hit >= 0) currentOnSelect(hit)
                    },
                    onDoubleTap = { pos ->
                        val plot = Plot(30.dp.toPx(), 10.dp.toPx(), size.width - 10.dp.toPx(), size.height - 24.dp.toPx())
                        if (nearest(currentNodes, plot, pos, 30.dp.toPx()) < 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnAdd(plot.frequencyAt(pos.x).roundToInt().toDouble(), snap(plot.gainAt(pos.y)))
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val plot = Plot(30.dp.toPx(), 10.dp.toPx(), size.width - 10.dp.toPx(), size.height - 24.dp.toPx())
                    val hit = nearest(currentNodes, plot, down.position, 30.dp.toPx())
                    if (hit < 0) return@awaitEachGesture
                    down.consume()
                    currentOnSelect(hit)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    dragging = hit
                    val grabbed = currentNodes[hit]
                    val dx = down.position.x - plot.x(grabbed.frequency)
                    val dy = down.position.y - plot.y(grabbed.nodeGain())
                    var wasZero = abs(grabbed.nodeGain()) < 0.05
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val f = plot.frequencyAt(change.position.x - dx).roundToInt().toDouble()
                            val g = snap(plot.gainAt(change.position.y - dy))
                            val isZero = g == 0.0
                            if (isZero && !wasZero) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            wasZero = isZero
                            currentOnMove(hit, f, g)
                        }
                    } finally {
                        dragging = -1
                    }
                }
            },
    ) {
        val plot = plotOf()
        drawGrid(plot, measurer, labelStyle)

        @Suppress("UNUSED_VARIABLE")
        val unused = animFrame
        analyzer.update()
        drawSpectrum(plot, analyzer.levels)

        clipRect(plot.left, plot.top, plot.right, plot.bottom) {
            val zero = plot.y(0.0)
            perNode.forEachIndexed { i, response ->
                if (!nodes[i].enabled) return@forEachIndexed
                val color = EqColors.Nodes[i % EqColors.Nodes.size]
                val emphasis = if (i == selected) 1f else 0.55f
                val path = curvePath(plot, response)
                val fill = Path().apply {
                    addPath(path)
                    lineTo(plot.right, zero)
                    lineTo(plot.left, zero)
                    close()
                }
                drawPath(fill, color.copy(alpha = 0.16f * emphasis))
                drawPath(path, color.copy(alpha = 0.75f * emphasis), style = Stroke(width = 1.2.dp.toPx()))
            }
            drawPath(
                curvePath(plot, total),
                Color.White,
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        nodes.forEachIndexed { i, n ->
            val center = Offset(plot.x(n.frequency), plot.y(n.nodeGain().coerceIn(-GraphRangeDb, GraphRangeDb)))
            val color = if (n.enabled) EqColors.Nodes[i % EqColors.Nodes.size] else EqColors.Faint
            val isSelected = i == selected
            val r = (if (isSelected) 8.5.dp else 6.5.dp).toPx() + if (isSelected) 3.dp.toPx() * selectedPulse else 0f
            if (isSelected) {
                drawCircle(color.copy(alpha = 0.18f + 0.1f * selectedPulse), r * 2.1f, center)
            }
            drawCircle(Color.Black, r + 2.dp.toPx(), center)
            drawCircle(color, r, center)
            if (isSelected) drawCircle(Color.White, r + 2.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        }
    }
}

private fun snap(db: Double): Double {
    val g = (db * 10).roundToInt() / 10.0
    return if (abs(g) < 0.35) 0.0 else g.coerceIn(-GraphRangeDb, GraphRangeDb)
}

private fun nearest(nodes: List<ParametricEQBand>, plot: Plot, pos: Offset, radius: Float): Int {
    var best = -1
    var bestD = radius
    nodes.forEachIndexed { i, n ->
        val d = hypot(pos.x - plot.x(n.frequency), pos.y - plot.y(n.nodeGain().coerceIn(-GraphRangeDb, GraphRangeDb)))
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

internal fun curvePath(plot: Plot, response: DoubleArray, frequencies: DoubleArray = GraphFrequencies): Path {
    val path = Path()
    for (i in frequencies.indices) {
        val x = plot.x(frequencies[i])
        val y = plot.y(response[i])
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

internal fun DrawScope.drawGrid(
    plot: Plot,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    yLabels: List<Pair<Double, String>> = listOf(12.0 to "+12", 6.0 to "+6", 0.0 to "0", -6.0 to "-6", -12.0 to "-12"),
) {
    val line = Color.White.copy(alpha = 0.06f)
    yLabels.forEach { (db, label) ->
        val y = plot.y(db)
        drawLine(if (db == 0.0) Color.White.copy(alpha = 0.16f) else line, Offset(plot.left, y), Offset(plot.right, y), 1.dp.toPx())
        val layout = measurer.measure(label, style)
        drawText(layout, topLeft = Offset(plot.left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
    }
    GridFrequencies.forEachIndexed { i, f ->
        val x = plot.x(f)
        drawLine(line, Offset(x, plot.top), Offset(x, plot.bottom), 1.dp.toPx())
        val layout = measurer.measure(GridLabels[i], style)
        val lx = (x - layout.size.width / 2f).coerceIn(plot.left - 4.dp.toPx(), plot.right - layout.size.width.toFloat())
        drawText(layout, topLeft = Offset(lx, plot.bottom + 6.dp.toPx()))
    }
}

private fun DrawScope.drawSpectrum(plot: Plot, levels: FloatArray) {
    val height = (plot.bottom - plot.top) * 0.55f
    val path = Path()
    var any = false
    for (i in levels.indices) {
        val v = ((levels[i] + 78f) / 66f).coerceIn(0f, 1f)
        if (v > 0.01f) any = true
        val x = plot.x(SpectrumBands[i])
        val y = plot.bottom - v * height
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    if (!any) return
    val stroke = Path().apply { addPath(path) }
    path.lineTo(plot.right, plot.bottom)
    path.lineTo(plot.left, plot.bottom)
    path.close()
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.01f)),
            startY = plot.bottom - height,
            endY = plot.bottom,
        ),
    )
    drawPath(stroke, Color.White.copy(alpha = 0.12f), style = Stroke(1.dp.toPx()))
}

@Composable
private fun NodeDetail(
    node: ParametricEQBand,
    color: Color,
    canRemove: Boolean,
    onUpdate: ((ParametricEQBand) -> ParametricEQBand) -> Unit,
    onRemove: () -> Unit,
) {
    EqCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${frequencyLabel(node.frequency)} node",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                )
                Text(
                    "${filterTypeLabel(node.filterType).lowercase()} · Q ${String.format("%.1f", node.q)}" +
                        if (node.filterType == FilterType.LPQ || node.filterType == FilterType.HPQ) "" else " · ${gainLabel(node.gain)}",
                    fontSize = 13.sp,
                    color = EqColors.Secondary,
                )
            }
            Switch(
                checked = node.enabled,
                onCheckedChange = { on -> onUpdate { it.copy(enabled = on) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = EqColors.Secondary,
                    uncheckedTrackColor = EqColors.Chip,
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(FilterType.LSC to "Low shelf", FilterType.PK to "Peak", FilterType.HSC to "High shelf").forEach { (type, label) ->
                val picked = node.filterType == type
                val bg by animateColorAsState(if (picked) Color(0xFFD1D1D6) else Color.Black, tween(160), label = "type")
                val fg by animateColorAsState(if (picked) Color.Black else Color.White, tween(160), label = "typeText")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg)
                        .border(1.dp, if (picked) Color.Transparent else EqColors.StageLine, RoundedCornerShape(14.dp))
                        .clickable {
                            onUpdate {
                                it.copy(
                                    filterType = type,
                                    q = if (type == FilterType.PK) it.q else 0.7,
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = fg)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        EqSlider(
            label = "GAIN",
            valueText = gainLabel(node.gain),
            value = node.gain.toFloat(),
            onValueChange = { v -> onUpdate { it.copy(gain = snap(v.toDouble())) } },
            valueRange = -GraphRangeDb.toFloat()..GraphRangeDb.toFloat(),
        )
        Spacer(Modifier.height(10.dp))
        EqSlider(
            label = "FREQUENCY",
            valueText = if (node.frequency >= 1000) String.format("%.2f kHz", node.frequency / 1000) else "${node.frequency.roundToInt()} Hz",
            value = log10(node.frequency).toFloat(),
            onValueChange = { v -> onUpdate { it.copy(frequency = 10.0.pow(v.toDouble()).roundToInt().toDouble()) } },
            valueRange = MinLog.toFloat()..MaxLog.toFloat(),
        )
        Spacer(Modifier.height(10.dp))
        EqSlider(
            label = "Q",
            valueText = String.format("%.2f", node.q),
            value = log10(node.q).toFloat(),
            onValueChange = { v -> onUpdate { it.copy(q = (10.0.pow(v.toDouble()) * 100).roundToInt() / 100.0) } },
            valueRange = -1f..1f,
        )
        if (canRemove) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(EqColors.Row)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Remove node",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFFFF8A80),
                )
            }
        }
    }
}

