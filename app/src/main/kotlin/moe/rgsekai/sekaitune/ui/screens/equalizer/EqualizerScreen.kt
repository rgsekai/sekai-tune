/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rgsekai.sekaitune.ui.screens.equalizer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.eq.EqPreset
import moe.rgsekai.sekaitune.eq.EqPresets
import moe.rgsekai.sekaitune.eq.EqSettings
import moe.rgsekai.sekaitune.viewmodels.EqualizerViewModel
import moe.rgsekai.sekaitune.viewmodels.isFlat
import kotlin.math.roundToInt

private val Tabs = listOf("Graph", "Bands", "AutoEQ")

@Composable
fun EqualizerScreen(
    navController: NavController,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val eq by viewModel.eq.collectAsState()
    val presets by viewModel.presets.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedNode by rememberSaveable { mutableIntStateOf(0) }
    var savingPreset by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<EqPreset?>(null) }

    ListenToAudio()

    val insets = WindowInsets.systemBars.asPaddingValues()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = insets.calculateTopPadding())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C1E)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Equalizer",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            EqCard(padding = 10.dp) {
                EqToggleRow(
                    title = "Equalizer",
                    subtitle = statusLine(eq, presets),
                    checked = eq.enabled,
                    onCheckedChange = viewModel::setEnabled,
                    leadingDot = true,
                )
            }
            Spacer(Modifier.height(14.dp))
            EqSegmented(options = Tabs, selectedIndex = tab, onSelect = { tab = it })
            Spacer(Modifier.height(18.dp))

            val dim = animatedDim(!eq.enabled)
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = dim }
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(220, 60)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 8 * dir }) togetherWith fadeOut(tween(120))
                    },
                    label = "eqTab",
                ) { page ->
                    Column(Modifier.fillMaxWidth()) {
                        when (page) {
                            0, 1 -> {
                                Presets(
                                    eq = eq,
                                    presets = presets,
                                    onPick = viewModel::applyPreset,
                                    onReset = viewModel::reset,
                                    onSave = {
                                        presetNameInput = ""
                                        savingPreset = true
                                    },
                                    onLongPress = { id -> deleting = presets.firstOrNull { it.id == id } },
                                )
                                Spacer(Modifier.height(18.dp))
                                if (page == 0) {
                                    EqGraphSection(
                                        nodes = eq.nodes,
                                        selected = selectedNode,
                                        onSelect = { selectedNode = it },
                                        onMove = { i, f, g -> viewModel.updateNode(i) { it.copy(frequency = f, gain = g) } },
                                        onAdd = { f, g -> viewModel.addNode(f, g).takeIf { it >= 0 }?.let { selectedNode = it } },
                                        onUpdate = viewModel::updateNode,
                                        onRemove = { i ->
                                            viewModel.removeNode(i)
                                            selectedNode = (i - 1).coerceAtLeast(0)
                                        },
                                        canAdd = eq.nodes.size < EqualizerViewModel.MaxNodes,
                                    )
                                } else {
                                    EqBandsSection(
                                        nodes = eq.nodes,
                                        onGain = { i, g -> viewModel.updateNode(i) { it.copy(gain = g) } },
                                    )
                                }
                            }
                            else -> AutoEqTab(viewModel = viewModel, eq = eq)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Output(eq = eq, headroom = viewModel.autoHeadroom(eq), viewModel = viewModel)
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    if (savingPreset) {
        AlertDialog(
            onDismissRequest = { savingPreset = false },
            title = { Text("Save preset", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    placeholder = { Text("My earbuds") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = presetNameInput.trim()
                        if (name.isNotEmpty()) {
                            viewModel.savePreset(name)
                        }
                        savingPreset = false
                    }
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { savingPreset = false }) {
                    Text("Cancel", color = EqColors.Secondary)
                }
            },
            containerColor = EqColors.Card,
        )
    }

    deleting?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${preset.name}”?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("This will permanently remove your custom preset.", color = EqColors.Secondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePreset(preset.id)
                        deleting = null
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF8A80))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Cancel", color = EqColors.Secondary)
                }
            },
            containerColor = EqColors.Card,
        )
    }
}

private fun statusLine(eq: EqSettings, presets: List<EqPreset>): String {
    if (!eq.enabled) return "Off"
    val curve = eq.presetId?.let { id ->
        EqPresets.BuiltIn.firstOrNull { it.id == id }?.name ?: presets.firstOrNull { it.id == id }?.name
    } ?: "Custom"
    val correction = eq.correction?.takeIf { eq.correctionEnabled }?.model
    return when {
        eq.isFlat() -> "On · Flat"
        correction != null && eq.presetId == EqPresets.FlatId -> "On · $correction"
        correction != null -> "On · $curve + $correction"
        else -> "On · $curve"
    }
}

@Composable
private fun Presets(
    eq: EqSettings,
    presets: List<EqPreset>,
    onPick: (String) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onLongPress: (String) -> Unit,
) {
    EqSectionLabel("Presets") {
        EqTextAction("Reset", R.drawable.refresh, onClick = onReset)
        EqTextAction("Save", R.drawable.add, onClick = onSave)
    }
    val chips = buildList {
        if (eq.presetId == null) add(EqChipItem("custom", "Custom"))
        presets.forEach { add(EqChipItem(it.id, it.name, dot = Color.White)) }
        EqPresets.BuiltIn.forEach { add(EqChipItem(it.id, it.name)) }
    }
    EqChips(
        chips = chips,
        selectedKey = eq.presetId ?: "custom",
        onSelect = { if (it != "custom") onPick(it) },
        onLongPress = { id -> if (presets.any { it.id == id }) onLongPress(id) },
    )
}

@Composable
private fun Output(eq: EqSettings, headroom: Double, viewModel: EqualizerViewModel) {
    EqSectionLabel("Output")
    EqCard(padding = 16.dp) {
        EqSlider(
            label = "Preamp",
            valueText = (if (eq.preamp > 0) "+" else "") + String.format("%.1f dB", eq.preamp),
            value = eq.preamp.toFloat(),
            onValueChange = { viewModel.setPreamp((it * 2).roundToInt() / 2.0) },
            valueRange = -12f..6f,
        )
        Spacer(Modifier.height(6.dp))
        EqToggleRow(
            title = "Auto preamp",
            subtitle = if (eq.autoPreamp) {
                if (headroom < -0.05) "Lowering by ${String.format("%.1f", -headroom)} dB, the limiter catches the rest"
                else "Nothing to lower, the limiter catches any peaks"
            } else "Off: louder, but big boosts get squashed on loud songs",
            checked = eq.autoPreamp,
            onCheckedChange = viewModel::setAutoPreamp,
        )
    }
}

