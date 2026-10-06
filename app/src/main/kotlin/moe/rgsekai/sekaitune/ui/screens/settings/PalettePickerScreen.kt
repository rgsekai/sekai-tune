/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.CustomThemeColorKey
import moe.rgsekai.sekaitune.constants.DynamicThemeKey
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.svg.DynamicSVGImage
import moe.rgsekai.sekaitune.ui.svg.PALETTE
import moe.rgsekai.sekaitune.ui.svg.SVGString
import moe.rgsekai.sekaitune.ui.theme.ColorSaver
import moe.rgsekai.sekaitune.ui.theme.ThemeSeedPalette
import moe.rgsekai.sekaitune.ui.theme.ThemeSeedPaletteCodec
import moe.rgsekai.sekaitune.ui.theme.palette.TonalPalettes
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.utils.rememberPreference

private enum class SeedRole {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    NEUTRAL,
}

data class ThemePalette(
    val id: String,
    val nameResId: Int,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
    val onPrimary: Color = if (primary.luminance() > 0.5f) Color.Black else Color.White,
)

object ThemePalettes {
    val Default =
        ThemePalette(
            id = "default",
            nameResId = R.string.palette_default,
            primary = Color(0xFF000000),
            secondary = Color(0xFF000000),
            tertiary = Color(0xFF000000),
            neutral = Color(0xFF000000),
        )

    val allPalettes = listOf(Default)

    fun findByPrimaryColor(colorHex: String): ThemePalette? =
        if (colorHex.equals(Default.primary.toHexString(), ignoreCase = true)) Default else null

    fun findById(id: String): ThemePalette? =
        if (id == "default" || id.isEmpty()) Default else null
}

private fun Color.toHexString(): String {
    val red = (this.red * 255).toInt()
    val green = (this.green * 255).toInt()
    val blue = (this.blue * 255).toInt()
    return String.format("#%02X%02X%02X", red, green, blue)
}

private fun ThemePalette.toSeedPalette(): ThemeSeedPalette =
    ThemeSeedPalette(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        neutral = neutral,
    )

private fun ThemeSeedPalette.toThemePalette(): ThemePalette =
    ThemePalette(
        id = "custom_seed",
        nameResId = R.string.palette_custom,
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        neutral = neutral,
    )

@Composable
fun PalettePickerScreen(navController: NavController) {
    ThemeCreatorScreen(navController = navController)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCreatorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val (customThemeValue, setCustomThemeValue) =
        rememberPreference(
            key = CustomThemeColorKey,
            defaultValue = ThemePalettes.Default.id,
        )
    val (_, setDynamicThemeEnabled) =
        rememberPreference(
            key = DynamicThemeKey,
            defaultValue = false,
        )

    val seedFromPrefs =
        remember(customThemeValue) {
            ThemeSeedPaletteCodec.decodeFromPreference(customThemeValue)
                ?: ThemePalettes.findById(customThemeValue)?.toSeedPalette()
                ?: ThemePalettes.Default.toSeedPalette()
        }

    var primary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.primary) }
    var secondary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.secondary) }
    var tertiary by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.tertiary) }
    var neutral by rememberSaveable(customThemeValue, stateSaver = ColorSaver) { mutableStateOf(seedFromPrefs.neutral) }

    val currentPalette =
        ThemeSeedPalette(
            primary = primary,
            secondary = secondary,
            tertiary = tertiary,
            neutral = neutral,
        )

    var activeRole by rememberSaveable { mutableStateOf(SeedRole.PRIMARY) }
    var showImportErrorDialog by rememberSaveable { mutableStateOf(false) }
    var importErrorText by rememberSaveable { mutableStateOf("") }

    fun applyThemeToPrefs() {
        setDynamicThemeEnabled(false)
        setCustomThemeValue(ThemeSeedPaletteCodec.encodeForPreference(currentPalette, null))
        Toast.makeText(context, context.getString(R.string.theme_applied), Toast.LENGTH_SHORT).show()
    }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val payload = ThemeSeedPaletteCodec.encodeAsJson(currentPalette, null)
                val ok =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(payload.toByteArray(Charsets.UTF_8))
                                out.flush()
                            } ?: error("No output stream")
                        }.isSuccess
                    }
                if (ok) {
                    Toast.makeText(context, context.getString(R.string.theme_export_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.theme_export_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver
                                .openInputStream(uri)
                                ?.bufferedReader()
                                ?.use { it.readText() }
                                .orEmpty()
                        }.getOrNull().orEmpty()
                    }
                val importedPalette = ThemeSeedPaletteCodec.decodeFromJson(text)
                if (importedPalette != null) {
                    setDynamicThemeEnabled(false)
                    setCustomThemeValue(ThemeSeedPaletteCodec.encodeForPreference(importedPalette, null))
                    Toast.makeText(context, context.getString(R.string.theme_import_success), Toast.LENGTH_SHORT).show()
                } else {
                    importErrorText = text.take(1200)
                    showImportErrorDialog = true
                }
            }
        }

    if (showImportErrorDialog) {
        moe.rgsekai.sekaitune.ui.component.DefaultDialog(
            onDismiss = { showImportErrorDialog = false },
            buttons = {
                TextButton(onClick = { showImportErrorDialog = false }, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
            title = { Text(text = stringResource(R.string.theme_import_failed_title)) },
        ) {
            Text(
                text = if (importErrorText.isBlank()) stringResource(R.string.theme_import_failed) else importErrorText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.theme_creator_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            primary = ThemePalettes.Default.primary
                            secondary = ThemePalettes.Default.secondary
                            tertiary = ThemePalettes.Default.tertiary
                            neutral = ThemePalettes.Default.neutral
                        },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(text = stringResource(R.string.reset))
                    }
                    TextButton(onClick = { applyThemeToPrefs() }, shapes = ButtonDefaults.shapes()) {
                        Text(text = stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .verticalScroll(rememberScrollState()),
        ) {
            SimpleThemePreview(
                palette = currentPalette,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            SeedRolePicker(
                activeRole = activeRole,
                onRoleChange = { activeRole = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val activeColor =
                when (activeRole) {
                    SeedRole.PRIMARY -> primary
                    SeedRole.SECONDARY -> secondary
                    SeedRole.TERTIARY -> tertiary
                    SeedRole.NEUTRAL -> neutral
                }

            SeedColorEditor(
                role = activeRole,
                color = activeColor,
                onColorChange = { nextColor ->
                    when (activeRole) {
                        SeedRole.PRIMARY -> primary = nextColor
                        SeedRole.SECONDARY -> secondary = nextColor
                        SeedRole.TERTIARY -> tertiary = nextColor
                        SeedRole.NEUTRAL -> neutral = nextColor
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { applyThemeToPrefs() },
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(text = stringResource(R.string.theme_apply_button), style = MaterialTheme.typography.titleSmall)
            }

            Row(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.restore),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.import_theme), style = MaterialTheme.typography.labelMedium)
                }

                FilledTonalButton(
                    onClick = {
                        exportLauncher.launch("SekaiTune_Theme.json")
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.export_theme), style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
fun SimpleThemePreview(
    palette: ThemeSeedPalette,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val tonalPalettes =
        remember(palette) {
            TonalPalettes.fromSeedColors(
                primarySeed = palette.primary,
                secondarySeed = palette.secondary,
                tertiarySeed = palette.tertiary,
                neutralSeed = palette.neutral,
            )
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        DynamicSVGImage(
            svgImageString = SVGString.PALETTE,
            tonalPalettes = tonalPalettes,
            isDarkTheme = isDark,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp)),
        )
    }
}

@Composable
private fun SeedRolePicker(
    activeRole: SeedRole,
    onRoleChange: (SeedRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.theme_seed_colors), style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SeedChip(label = stringResource(R.string.theme_seed_primary), selected = activeRole == SeedRole.PRIMARY, onClick = {
                    onRoleChange(SeedRole.PRIMARY)
                }, modifier = Modifier.weight(1f))
                SeedChip(label = stringResource(R.string.theme_seed_secondary), selected = activeRole == SeedRole.SECONDARY, onClick = {
                    onRoleChange(SeedRole.SECONDARY)
                }, modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SeedChip(label = stringResource(R.string.theme_seed_tertiary), selected = activeRole == SeedRole.TERTIARY, onClick = {
                    onRoleChange(SeedRole.TERTIARY)
                }, modifier = Modifier.weight(1f))
                SeedChip(label = stringResource(R.string.theme_seed_neutral), selected = activeRole == SeedRole.NEUTRAL, onClick = {
                    onRoleChange(SeedRole.NEUTRAL)
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SeedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier =
            modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onClick),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = if (selected) 6.dp else 0.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeedColorEditor(
    role: SeedRole,
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val roleLabel =
        when (role) {
            SeedRole.PRIMARY -> stringResource(R.string.theme_seed_primary)
            SeedRole.SECONDARY -> stringResource(R.string.theme_seed_secondary)
            SeedRole.TERTIARY -> stringResource(R.string.theme_seed_tertiary)
            SeedRole.NEUTRAL -> stringResource(R.string.theme_seed_neutral)
        }

    val r0 = ((color.toArgb() shr 16) and 0xFF)
    val g0 = ((color.toArgb() shr 8) and 0xFF)
    val b0 = (color.toArgb() and 0xFF)

    var r by rememberSaveable(role.name) { mutableStateOf(r0) }
    var g by rememberSaveable(role.name) { mutableStateOf(g0) }
    var b by rememberSaveable(role.name) { mutableStateOf(b0) }

    LaunchedEffect(role, color.toArgb()) {
        val argb = color.toArgb()
        r = (argb shr 16) and 0xFF
        g = (argb shr 8) and 0xFF
        b = argb and 0xFF
    }

    val hex = remember(color.toArgb()) { String.format("#%08X", color.toArgb()) }
    var hexInput by rememberSaveable(role.name) { mutableStateOf(hex) }
    var hexError by rememberSaveable(role.name) { mutableStateOf(false) }

    LaunchedEffect(hex) {
        if (!hexError) hexInput = hex
    }

    fun commitRgb() {
        hexError = false
        onColorChange(Color((0xFF shl 24) or (r shl 16) or (g shl 8) or b))
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text = stringResource(R.string.theme_editor_title, roleLabel), style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(54.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.theme_editor_hex),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = hex, style = MaterialTheme.typography.titleSmall)
                }
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(
                        modifier =
                            Modifier
                                .clickable {
                                    clipboard.setText(AnnotatedString(hex))
                                    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                                }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(painter = painterResource(R.drawable.link), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(R.string.copy), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            OutlinedTextField(
                value = hexInput,
                onValueChange = { next ->
                    hexInput = next.take(12)
                    val parsed =
                        runCatching {
                            val normalized = hexInput.trim().let { if (it.startsWith("#")) it else "#$it" }
                            Color(android.graphics.Color.parseColor(normalized))
                        }.getOrNull()
                    if (parsed != null) {
                        hexError = false
                        onColorChange(parsed)
                    } else {
                        hexError = true
                    }
                },
                label = { Text(stringResource(R.string.theme_editor_hex_input)) },
                isError = hexError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            RgbSlider(label = "R", value = r, color = Color(0xFFE53935), onValueChange = {
                r = it
                commitRgb()
            })
            RgbSlider(label = "G", value = g, color = Color(0xFF43A047), onValueChange = {
                g = it
                commitRgb()
            })
            RgbSlider(label = "B", value = b, color = Color(0xFF1E88E5), onValueChange = {
                b = it
                commitRgb()
            })
        }
    }
}

@Composable
private fun RgbSlider(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.18f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors =
                SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier = Modifier.weight(1f),
        )
        Text(text = value.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp))
    }
}
