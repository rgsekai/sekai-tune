/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlin.math.roundToInt
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.LiquidGlassBlurRadiusKey
import moe.rgsekai.sekaitune.constants.LiquidGlassChromaticAberrationKey
import moe.rgsekai.sekaitune.constants.LiquidGlassDepthEffectKey
import moe.rgsekai.sekaitune.constants.LiquidGlassGlobalEnabledKey
import moe.rgsekai.sekaitune.constants.LiquidGlassLensAmountKey
import moe.rgsekai.sekaitune.constants.LiquidGlassLensHeightKey
import moe.rgsekai.sekaitune.constants.LiquidGlassMiniPlayerEnabledKey
import moe.rgsekai.sekaitune.constants.LiquidGlassNavBarEnabledKey
import moe.rgsekai.sekaitune.constants.LiquidGlassPopupMenuEnabledKey
import moe.rgsekai.sekaitune.constants.LiquidGlassDialogEnabledKey
import moe.rgsekai.sekaitune.constants.LiquidGlassQueueEnabledKey
import moe.rgsekai.sekaitune.constants.LiquidGlassSurfaceOpacityKey
import moe.rgsekai.sekaitune.constants.LiquidGlassSurfaceTintColorKey
import moe.rgsekai.sekaitune.constants.LiquidGlassTextColorKey
import moe.rgsekai.sekaitune.constants.LiquidGlassVibrancyKey
import moe.rgsekai.sekaitune.ui.component.ColorPickerDialog
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.PreferenceGroup
import moe.rgsekai.sekaitune.ui.component.SwitchPreference
import moe.rgsekai.sekaitune.ui.utils.appBarScrollBehavior
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.utils.rememberPreference

@Composable
fun GlassEffectSettings(navController: NavController) {
    val scrollBehavior = appBarScrollBehavior()

    val (globalEnabled, onGlobalEnabledChange) = rememberPreference(
        LiquidGlassGlobalEnabledKey, defaultValue = false
    )
    val (vibrancy, onVibrancyChange) = rememberPreference(
        LiquidGlassVibrancyKey, defaultValue = 80f
    )
    val (blurRadius, onBlurRadiusChange) = rememberPreference(
        LiquidGlassBlurRadiusKey, defaultValue = 6f
    )
    val (lensHeight, onLensHeightChange) = rememberPreference(
        LiquidGlassLensHeightKey, defaultValue = 70f
    )
    val (lensAmount, onLensAmountChange) = rememberPreference(
        LiquidGlassLensAmountKey, defaultValue = 70f
    )
    val (chromaticAberration, onChromaticAberrationChange) = rememberPreference(
        LiquidGlassChromaticAberrationKey, defaultValue = true
    )
    val (depthEffect, onDepthEffectChange) = rememberPreference(
        LiquidGlassDepthEffectKey, defaultValue = true
    )
    val (surfaceTintColorInt, onSurfaceTintColorChange) = rememberPreference(
        LiquidGlassSurfaceTintColorKey, defaultValue = 0
    )
    val adaptiveTintColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = if (surfaceTintColorInt == 0) {
        adaptiveTintColor
    } else {
        Color(surfaceTintColorInt)
    }
    val (surfaceOpacity, onSurfaceOpacityChange) = rememberPreference(
        LiquidGlassSurfaceOpacityKey, defaultValue = 20f
    )
    val (textColorInt, onTextColorChange) = rememberPreference(
        LiquidGlassTextColorKey, defaultValue = 0
    )
    val adaptiveTextColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.Black
    } else {
        Color.White
    }
    val textColor = if (textColorInt == 0) adaptiveTextColor else Color(textColorInt)
    val (miniPlayerEnabled, onMiniPlayerEnabledChange) = rememberPreference(
        LiquidGlassMiniPlayerEnabledKey, defaultValue = true
    )
    val (navBarEnabled, onNavBarEnabledChange) = rememberPreference(
        LiquidGlassNavBarEnabledKey, defaultValue = true
    )
    val (popupMenuEnabled, onPopupMenuEnabledChange) = rememberPreference(
        LiquidGlassPopupMenuEnabledKey, defaultValue = true
    )
    val (dialogEnabled, onDialogEnabledChange) = rememberPreference(
        LiquidGlassDialogEnabledKey, defaultValue = true
    )
    val (queueEnabled, onQueueEnabledChange) = rememberPreference(
        LiquidGlassQueueEnabledKey, defaultValue = true
    )

    var showVibrancyDialog by rememberSaveable { mutableStateOf(false) }
    var showBlurRadiusDialog by rememberSaveable { mutableStateOf(false) }
    var showLensHeightDialog by rememberSaveable { mutableStateOf(false) }
    var showLensAmountDialog by rememberSaveable { mutableStateOf(false) }
    var showSurfaceOpacityDialog by rememberSaveable { mutableStateOf(false) }
    var showSurfaceTintDialog by rememberSaveable { mutableStateOf(false) }
    var showTextColorDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.liquid_glass_settings),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(0.dp))

            PreferenceGroup(
                title = stringResource(R.string.liquid_glass_beta),
            ) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liquid_glass_global_enabled)) },
                        description = stringResource(R.string.liquid_glass_performance_warning),
                        icon = { Icon(painterResource(R.drawable.check), contentDescription = null) },
                        checked = globalEnabled,
                        onCheckedChange = onGlobalEnabledChange,
                    )
                }
            }

            PreferenceGroup(
                title = stringResource(R.string.liquid_glass_effects),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.liquid_glass_vibrancy)) },
                        description = stringResource(R.string.liquid_glass_vibrancy_desc),
                        icon = { Icon(painterResource(R.drawable.tune), contentDescription = null) },
                        onClick = { showVibrancyDialog = true },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.liquid_glass_blur_radius)) },
                        description = stringResource(R.string.liquid_glass_blur_radius_desc),
                        icon = { Icon(painterResource(R.drawable.tune), contentDescription = null) },
                        onClick = { showBlurRadiusDialog = true },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.liquid_glass_lens_height)) },
                        description = stringResource(R.string.liquid_glass_lens_height_desc),
                        icon = { Icon(painterResource(R.drawable.tune), contentDescription = null) },
                        onClick = { showLensHeightDialog = true },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.liquid_glass_lens_amount)) },
                        description = stringResource(R.string.liquid_glass_lens_amount_desc),
                        icon = { Icon(painterResource(R.drawable.tune), contentDescription = null) },
                        onClick = { showLensAmountDialog = true },
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liquid_glass_chromatic_aberration)) },
                        description = stringResource(R.string.liquid_glass_chromatic_aberration_desc),
                        icon = { Icon(painterResource(R.drawable.tune), contentDescription = null) },
                        checked = chromaticAberration,
                        onCheckedChange = onChromaticAberrationChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liquid_glass_depth_effect)) },
                        description = stringResource(R.string.liquid_glass_depth_effect_desc),
                        icon = { Icon(painterResource(R.drawable.tune), contentDescription = null) },
                        checked = depthEffect,
                        onCheckedChange = onDepthEffectChange,
                    )
                }
            }

            PreferenceGroup(
                title = stringResource(R.string.liquid_glass_appearance),
            ) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.liquid_glass_surface_tint)) },
                        description = stringResource(R.string.liquid_glass_surface_tint_desc),
                        icon = { Icon(painterResource(R.drawable.contrast), contentDescription = null) },
                        onClick = { showSurfaceTintDialog = true },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.liquid_glass_surface_opacity)) },
                        description = stringResource(R.string.liquid_glass_surface_opacity_desc),
                        icon = { Icon(painterResource(R.drawable.tune), contentDescription = null) },
                        onClick = { showSurfaceOpacityDialog = true },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.liquid_glass_text_color)) },
                        description = stringResource(R.string.liquid_glass_text_color_desc),
                        icon = { Icon(painterResource(R.drawable.text_fields), contentDescription = null) },
                        onClick = { showTextColorDialog = true },
                    )
                }
            }

            PreferenceGroup(
                title = stringResource(R.string.liquid_glass_per_component),
            ) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liquid_glass_mini_player)) },
                        description = stringResource(R.string.liquid_glass_mini_player_desc),
                        icon = { Icon(painterResource(R.drawable.drag_handle), contentDescription = null) },
                        checked = miniPlayerEnabled,
                        onCheckedChange = onMiniPlayerEnabledChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liquid_glass_nav_bar)) },
                        description = stringResource(R.string.liquid_glass_nav_bar_desc),
                        icon = { Icon(painterResource(R.drawable.home_outlined), contentDescription = null) },
                        checked = navBarEnabled,
                        onCheckedChange = onNavBarEnabledChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liquid_glass_popup_menu)) },
                        description = stringResource(R.string.liquid_glass_popup_menu_desc),
                        icon = { Icon(painterResource(R.drawable.more_vert), contentDescription = null) },
                        checked = popupMenuEnabled,
                        onCheckedChange = onPopupMenuEnabledChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liquid_glass_dialog)) },
                        description = stringResource(R.string.liquid_glass_dialog_desc),
                        icon = { Icon(painterResource(R.drawable.info), contentDescription = null) },
                        checked = dialogEnabled,
                        onCheckedChange = onDialogEnabledChange,
                    )
                }
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.liquid_glass_queue)) },
                        description = stringResource(R.string.liquid_glass_queue_desc),
                        icon = { Icon(painterResource(R.drawable.queue_music), contentDescription = null) },
                        checked = queueEnabled,
                        onCheckedChange = onQueueEnabledChange,
                    )
                }
            }

            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
                )
            )
        }
    }

    if (showVibrancyDialog) {
        var tempValue by remember { mutableFloatStateOf(vibrancy) }
        DefaultDialog(
            onDismiss = { tempValue = vibrancy; showVibrancyDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 80f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = vibrancy; showVibrancyDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onVibrancyChange(tempValue); showVibrancyDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_vibrancy), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "${tempValue.roundToInt()}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showBlurRadiusDialog) {
        var tempValue by remember { mutableFloatStateOf(blurRadius) }
        DefaultDialog(
            onDismiss = { tempValue = blurRadius; showBlurRadiusDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 6f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = blurRadius; showBlurRadiusDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onBlurRadiusChange(tempValue); showBlurRadiusDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_blur_radius), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "${tempValue.roundToInt()} dp", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..50f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showLensHeightDialog) {
        var tempValue by remember { mutableFloatStateOf(lensHeight) }
        DefaultDialog(
            onDismiss = { tempValue = lensHeight; showLensHeightDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 70f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = lensHeight; showLensHeightDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onLensHeightChange(tempValue); showLensHeightDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_lens_height), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "${tempValue.roundToInt()}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showLensAmountDialog) {
        var tempValue by remember { mutableFloatStateOf(lensAmount) }
        DefaultDialog(
            onDismiss = { tempValue = lensAmount; showLensAmountDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 70f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = lensAmount; showLensAmountDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onLensAmountChange(tempValue); showLensAmountDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_lens_amount), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "${tempValue.roundToInt()}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showSurfaceOpacityDialog) {
        var tempValue by remember { mutableFloatStateOf(surfaceOpacity) }
        DefaultDialog(
            onDismiss = { tempValue = surfaceOpacity; showSurfaceOpacityDialog = false },
            buttons = {
                TextButton(onClick = { tempValue = 20f }) { Text(stringResource(R.string.reset)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { tempValue = surfaceOpacity; showSurfaceOpacityDialog = false }) { Text(stringResource(android.R.string.cancel)) }
                TextButton(onClick = { onSurfaceOpacityChange(tempValue); showSurfaceOpacityDialog = false }) { Text(stringResource(android.R.string.ok)) }
            }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(R.string.liquid_glass_surface_opacity), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                Text(text = "${tempValue.roundToInt()}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                Slider(value = tempValue, onValueChange = { tempValue = it }, valueRange = 0f..100f, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showSurfaceTintDialog) {
        ColorPickerDialog(
            initialColor = surfaceTintColor,
            title = stringResource(R.string.liquid_glass_surface_tint),
            onDismiss = { showSurfaceTintDialog = false },
            onConfirm = { color ->
                onSurfaceTintColorChange(color.toArgb())
                showSurfaceTintDialog = false
            },
            onReset = {
                onSurfaceTintColorChange(0)
                showSurfaceTintDialog = false
            },
        )
    }

    if (showTextColorDialog) {
        ColorPickerDialog(
            initialColor = textColor,
            title = stringResource(R.string.liquid_glass_text_color),
            onDismiss = { showTextColorDialog = false },
            onConfirm = { color ->
                onTextColorChange(color.toArgb())
                showTextColorDialog = false
            },
            onReset = {
                onTextColorChange(0)
                showTextColorDialog = false
            },
        )
    }
}
