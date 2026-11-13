/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.eq.AutoEqCorrection
import moe.rgsekai.sekaitune.eq.EqSettings
import moe.rgsekai.sekaitune.eq.audio.ResponseCurve
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqEntry
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqFitter
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqMeasurement
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqModel
import moe.rgsekai.sekaitune.eq.autoeq.AutoEqTuning
import moe.rgsekai.sekaitune.eq.autoeq.HeadphoneForm
import moe.rgsekai.sekaitune.viewmodels.AutoEqIndex
import moe.rgsekai.sekaitune.viewmodels.EqualizerViewModel
import kotlin.math.ceil
import kotlin.math.floor

private val OriginalColor = Color(0xFF6B9BF2)
private val CorrectedColor = Color(0xFFF28BA8)
private val TargetColor = Color(0xFF9A9AA0)

@Composable
fun AutoEqTab(viewModel: EqualizerViewModel, eq: EqSettings) {
    LaunchedEffect(Unit) { viewModel.openAutoEq() }
    val measurement by viewModel.measurement.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val correction = eq.correction

    EqSectionLabel("Headphone correction")
    EqCard(padding = 12.dp) {
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
            Legend(OriginalColor, "Original")
            Spacer(Modifier.width(10.dp))
            Legend(TargetColor, "Target")
            Spacer(Modifier.width(10.dp))
            Legend(CorrectedColor, "Corrected")
        }
        Spacer(Modifier.height(8.dp))
        ResponseGraph(
            measurement = measurement.takeIf { correction != null && it?.entry?.path == correction.path },
            correction = correction,
        )
    }

    if (correction != null) {
        Spacer(Modifier.height(12.dp))
        SelectedHeadphone(
            correction = correction,
            enabled = eq.correctionEnabled,
            fitting = busy == "fit",
            canTune = measurement?.entry?.path == correction.path,
            viewModel = viewModel,
        )
    }

    AnimatedVisibility(
        error != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Text(
            error.orEmpty(),
            fontSize = 13.sp,
            color = Color(0xFFFF8A80),
            modifier = Modifier.padding(top = 10.dp, start = 6.dp),
        )
    }

    Spacer(Modifier.height(14.dp))
    Database(viewModel = viewModel, selectedPath = correction?.path, loadingPath = busy)
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = EqColors.Secondary)
    }
}

@Composable
private fun ResponseGraph(measurement: AutoEqMeasurement?, correction: AutoEqCorrection?) {
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val style = TextStyle(fontSize = 10.sp, color = EqColors.Faint)
    val appear by animateFloatAsState(if (measurement != null) 1f else 0f, tween(600), label = "appear")

    val curves = remember(measurement, correction) {
        if (measurement == null || correction == null) return@remember null
        val f = measurement.frequencies
        val response = ResponseCurve(f).response(correction.filters)
        val target = AutoEqFitter.tunedTarget(measurement, correction.tuning)
        val corrected = DoubleArray(f.size) { measurement.raw[it] + response[it] }
        fun mid(a: DoubleArray) = f.indices.filter { f[it] in 300.0..3000.0 }.map { a[it] }.average()
        val offRaw = mid(target) - mid(measurement.raw)
        val offCorrected = mid(target) - mid(corrected)
        val raw = DoubleArray(f.size) { measurement.raw[it] + offRaw }
        val cor = DoubleArray(f.size) { corrected[it] + offCorrected }
        val all = raw + target + cor
        val lo = floor((all.minOrNull() ?: -10.0) / 5) * 5 - 5
        val hi = ceil((all.maxOrNull() ?: 10.0) / 5) * 5 + 5
        Triple(Triple(raw, target, cor), lo, hi)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(EqColors.Stage)
            .border(1.dp, EqColors.StageLine, RoundedCornerShape(18.dp)),
    ) {
        val data = curves
        val lo = data?.second ?: -15.0
        val hi = data?.third ?: 15.0
        val range = (hi - lo) / 2
        val centre = (hi + lo) / 2
        val plot = Plot(
            left = 30.dp.toPx(),
            top = 10.dp.toPx(),
            right = size.width - 10.dp.toPx(),
            bottom = size.height - 24.dp.toPx(),
            rangeDb = range,
        )
        val step = if (range > 20) 10.0 else 5.0
        val labels = generateSequence(ceil(lo / step) * step) { it + step }.takeWhile { it <= hi }
            .map { (it - centre) to it.toInt().toString() }.toList()
        drawGrid(plot, measurer, style, labels)
        if (data == null) return@Canvas
        val (raw, target, cor) = data.first
        val f = measurement!!.frequencies
        fun shifted(a: DoubleArray) = DoubleArray(a.size) { a[it] - centre }
        clipRect(plot.left, plot.top, plot.left + (plot.right - plot.left) * appear, plot.bottom) {
            drawPath(
                curvePath(plot, shifted(target), f),
                TargetColor,
                style = Stroke(
                    1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                ),
            )
            drawPath(curvePath(plot, shifted(raw), f), OriginalColor, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            drawPath(curvePath(plot, shifted(cor), f), CorrectedColor, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
    if (measurement == null) {
        Text(
            if (correction == null) "Pick your headphones below" else "Loading ${correction.model}…",
            fontSize = 12.sp,
            color = EqColors.Faint,
            modifier = Modifier.padding(top = 10.dp, start = 6.dp),
        )
    }
}

@Composable
private fun SelectedHeadphone(
    correction: AutoEqCorrection,
    enabled: Boolean,
    fitting: Boolean,
    canTune: Boolean,
    viewModel: EqualizerViewModel,
) {
    EqCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormGlyph(correction.form)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    correction.model,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull("by ${correction.source}", correction.rig, "${correction.filters.size} filters").joinToString(" · "),
                    fontSize = 12.sp,
                    color = EqColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (fitting) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        EqToggleRow(
            title = "Apply correction",
            subtitle = "Flattens these headphones toward the target, under your own curve",
            checked = enabled,
            onCheckedChange = viewModel::setCorrectionEnabled,
        )
        Spacer(Modifier.height(10.dp))

        val tuning = correction.tuning
        Column(Modifier.graphicsLayer { alpha = if (canTune) 1f else 0.4f }) {
            EqSectionLabel("Filter bands")
            EqChips(
                chips = AutoEqTuning.BandChoices.map { EqChipItem(it.toString(), "$it bands") },
                selectedKey = tuning.bands.toString(),
                onSelect = { key -> if (canTune) viewModel.tune { it.copy(bands = key.toInt()) } },
            )
            Spacer(Modifier.height(12.dp))
            EqSectionLabel("Correct up to")
            EqChips(
                chips = AutoEqTuning.MaxHzChoices.map { EqChipItem(it.toString(), "${it / 1000} kHz") },
                selectedKey = tuning.maxHz.toString(),
                onSelect = { key -> if (canTune) viewModel.tune { it.copy(maxHz = key.toInt()) } },
            )
            Spacer(Modifier.height(12.dp))
            EqSlider(
                label = "Bass",
                valueText = (if (tuning.bassDb > 0) "+" else "") + String.format("%.0f dB", tuning.bassDb),
                value = tuning.bassDb.toFloat(),
                onValueChange = { v -> if (canTune) viewModel.tune { it.copy(bassDb = Math.round(v).toDouble()) } },
                valueRange = -6f..6f,
            )
            Spacer(Modifier.height(6.dp))
            EqSlider(
                label = "Tilt",
                valueText = (if (tuning.tiltDbPerOctave > 0) "+" else "") + String.format("%.1f dB/oct", tuning.tiltDbPerOctave),
                value = tuning.tiltDbPerOctave.toFloat(),
                onValueChange = { v -> if (canTune) viewModel.tune { it.copy(tiltDbPerOctave = Math.round(v * 10) / 10.0) } },
                valueRange = -1f..1f,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!tuning.isDefault) {
                EqTextAction("Use AutoEq's fit", R.drawable.refresh) {
                    if (canTune) viewModel.tune { AutoEqTuning() }
                }
            }
            Spacer(Modifier.weight(1f))
            EqTextAction("Remove", R.drawable.close) { viewModel.clearCorrection() }
        }
    }
}

@Composable
private fun FormGlyph(form: HeadphoneForm) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF2C2C2E)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.headset_applemusic), null, tint = Color.White, modifier = Modifier.size(20.dp))
        if (form != HeadphoneForm.OverEar) {
            Text(
                if (form == HeadphoneForm.InEar) "IEM" else "BUD",
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun Database(viewModel: EqualizerViewModel, selectedPath: String?, loadingPath: String?) {
    val index by viewModel.index.collectAsState()
    val results by viewModel.results.collectAsState()
    val query by viewModel.query.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf<String?>(null) }

    EqCard(padding = 14.dp) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Database", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                Text("AutoEq · measured by reviewers", fontSize = 12.sp, color = EqColors.Secondary)
            }
            val ready = index as? AutoEqIndex.Ready
            if (ready != null) {
                Text(
                    "${String.format("%,d", ready.models.size)} models",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = EqColors.Secondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SearchField(
            query = query,
            onQueryChange = {
                viewModel.query.value = it
                scope.launch { listState.scrollToItem(0) }
            },
        )
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(EqColors.Stage)
                .border(1.dp, EqColors.StageLine, RoundedCornerShape(18.dp)),
        ) {
            when (val state = index) {
                AutoEqIndex.Loading -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    repeat(7) { ShimmerRow(it) }
                }
                is AutoEqIndex.Failed -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.message, fontSize = 14.sp, color = EqColors.Secondary)
                    Spacer(Modifier.height(12.dp))
                    EqTextAction("Try again", R.drawable.refresh) { viewModel.loadIndex(refresh = true) }
                }
                is AutoEqIndex.Ready -> {
                    if (results.isEmpty()) {
                        Text(
                            "No headphones match “$query”",
                            fontSize = 14.sp,
                            color = EqColors.Secondary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 22.dp)) {
                        items(results, key = { it.name }) { model ->
                            ModelRow(
                                model = model,
                                expanded = expanded == model.name,
                                selectedPath = selectedPath,
                                loadingPath = loadingPath,
                                onClick = {
                                    if (model.entries.size == 1) viewModel.select(model.entries.first())
                                    else expanded = if (expanded == model.name) null else model.name
                                },
                                onPick = viewModel::select,
                            )
                        }
                    }
                    if (query.isBlank()) {
                        AlphabetRail(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onLetter = { letter ->
                                val target = results.indexOfFirst { m ->
                                    val c = m.name.firstOrNull()?.uppercaseChar() ?: '#'
                                    if (letter == '#') !c.isLetter() else c >= letter
                                }
                                if (target >= 0) scope.launch { listState.scrollToItem(target) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(EqColors.Row)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search model (e.g. HD 600)", fontSize = 15.sp, color = EqColors.Faint)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = Color.White),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = "Clear",
                tint = EqColors.Secondary,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun ModelRow(
    model: AutoEqModel,
    expanded: Boolean,
    selectedPath: String?,
    loadingPath: String?,
    onClick: () -> Unit,
    onPick: (AutoEqEntry) -> Unit,
) {
    val picked = model.entries.any { it.path == selectedPath }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.headset_applemusic), null, tint = EqColors.Secondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    model.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (model.entries.size == 1) model.entries.first().sourceLabel else "${model.entries.size} profiles",
                    fontSize = 12.sp,
                    color = EqColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                model.entries.size == 1 && loadingPath == model.entries.first().path ->
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                picked && model.entries.size == 1 ->
                    Icon(painterResource(R.drawable.check), null, tint = Color.White, modifier = Modifier.size(18.dp))
                model.entries.size > 1 -> {
                    val turn by animateFloatAsState(if (expanded) 90f else 0f, tween(200), label = "chevron")
                    Icon(
                        painterResource(R.drawable.navigate_next),
                        null,
                        tint = EqColors.Faint,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = turn },
                    )
                }
            }
        }
        if (expanded) {
            model.entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 44.dp, end = 10.dp, bottom = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (entry.path == selectedPath) EqColors.RowSelected else EqColors.Row)
                        .clickable { onPick(entry) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.sourceLabel,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        loadingPath == entry.path ->
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        entry.path == selectedPath ->
                            Icon(painterResource(R.drawable.check), null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun AlphabetRail(modifier: Modifier, onLetter: (Char) -> Unit) {
    val letters = ('A'..'Z').toList() + '#'
    var railHeight by remember { mutableStateOf(1) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(22.dp)
            .padding(vertical = 10.dp)
            .onSizeChanged { railHeight = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                fun pick(y: Float) = onLetter(letters[((y / railHeight) * letters.size).toInt().coerceIn(0, letters.size - 1)])
                detectTapGestures { pick(it.y) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    onLetter(letters[((change.position.y / railHeight) * letters.size).toInt().coerceIn(0, letters.size - 1)])
                }
            },
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach {
            Text(it.toString(), fontWeight = FontWeight.SemiBold, fontSize = 9.sp, color = EqColors.Faint)
        }
    }
}

@Composable
private fun ShimmerRow(index: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(EqColors.Row)
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .height(12.dp)
                    .width((120 + (index * 37) % 90).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(EqColors.Row)
            )
            Box(
                Modifier
                    .height(9.dp)
                    .width(70.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(EqColors.Chip)
            )
        }
    }
}

