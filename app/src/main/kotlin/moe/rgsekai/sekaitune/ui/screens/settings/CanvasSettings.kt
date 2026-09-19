/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.canvas.CanvasHealth
import moe.rgsekai.sekaitune.canvas.CanvasSource
import moe.rgsekai.sekaitune.viewmodels.CanvasCacheOption
import moe.rgsekai.sekaitune.viewmodels.CanvasSettingsAction
import moe.rgsekai.sekaitune.viewmodels.CanvasSettingsDialog
import moe.rgsekai.sekaitune.viewmodels.CanvasSettingsState
import moe.rgsekai.sekaitune.viewmodels.CanvasSettingsUiModel
import moe.rgsekai.sekaitune.viewmodels.CanvasSettingsViewModel

@Composable
fun CanvasSettings(
    navController: NavController,
    viewModel: CanvasSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack: () -> Unit = remember(navController) {
        {
            navController.navigateUp()
            Unit
        }
    }
    CanvasSettingsContent(state = state, onAction = onAction, onBack = onBack)
}

@Composable
private fun CanvasSettingsContent(
    state: CanvasSettingsState,
    onAction: (CanvasSettingsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.SekaiTune_canvas)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                    }
                },
            )
        },
    ) { padding ->
        val topPadding = padding.calculateTopPadding()
        val insets = LocalPlayerAwareWindowInsets.current
        val contentModifier = remember(topPadding, insets) {
            Modifier.padding(top = topPadding)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        }
        when (state) {
            CanvasSettingsState.Loading -> Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is CanvasSettingsState.Success -> CanvasSettingsBody(state.model, onAction, contentModifier)
            CanvasSettingsState.Empty -> CanvasSettingsFailure(R.string.canvas_settings_load_failed, onAction, contentModifier)
            is CanvasSettingsState.Error -> CanvasSettingsFailure(state.messageRes, onAction, contentModifier)
        }
    }
}

@Composable
private fun CanvasSettingsBody(
    model: CanvasSettingsUiModel,
    onAction: (CanvasSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onEnabled: (Boolean) -> Unit = remember(onAction) { { onAction(CanvasSettingsAction.SetEnabled(it)) } }
    val onWifiOnly: (Boolean) -> Unit = remember(onAction) { { onAction(CanvasSettingsAction.SetWifiOnly(it)) } }
    val onProceduralFallback: (Boolean) -> Unit = remember(onAction) { { onAction(CanvasSettingsAction.SetProceduralFallback(it)) } }
    val onRefresh = remember(onAction) { { onAction(CanvasSettingsAction.RefreshHealth) } }
    val onCacheLimit = remember(onAction) { { onAction(CanvasSettingsAction.ShowCacheLimit) } }
    val onClear = remember(onAction) { { onAction(CanvasSettingsAction.ShowClearCache) } }
    val sources = remember { CanvasSource.entries.toList() }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
            .padding(top = SettingsDimensions.SectionSpacing, bottom = SettingsDimensions.ScreenBottomPadding),
        verticalArrangement = remember { Arrangement.spacedBy(SettingsDimensions.SectionSpacing) },
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = model.configuration.enabled,
                        enabled = !model.busy,
                        role = Role.Switch,
                        onValueChange = onEnabled,
                    )
                    .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
            ) {
                Column(modifier = remember { Modifier.weight(1f) }) {
                    Text(
                        text = stringResource(R.string.canvas_enable),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.SekaiTune_canvas_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                Switch(
                    checked = model.configuration.enabled,
                    onCheckedChange = null,
                    enabled = !model.busy,
                )
            }
        }

        CanvasSettingsGroup(title = stringResource(R.string.canvas_source)) {
            Column(
                modifier = remember {
                    Modifier.fillMaxWidth().selectableGroup()
                },
            ) {
                sources.forEachIndexed { index, source ->
                    val onSelect = remember(onAction, source) { { onAction(CanvasSettingsAction.SelectSource(source)) } }
                    CanvasChoiceRow(
                        title = stringResource(source.labelResource()),
                        subtitle = when (source) {
                            CanvasSource.SPOTIFY -> stringResource(R.string.canvas_spotify_desc)
                            CanvasSource.ALL -> stringResource(R.string.canvas_source_all_desc)
                            else -> null
                        },
                        selected = model.configuration.source == source,
                        enabled = !model.busy,
                        shape = itemShape(index, sources.size),
                        onClick = onSelect,
                    )
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = model.configuration.wifiOnly,
                            enabled = !model.busy,
                            role = Role.Switch,
                            onValueChange = onWifiOnly,
                        )
                        .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
                ) {
                    Column(modifier = remember { Modifier.weight(1f) }) {
                        Text(
                            text = stringResource(R.string.canvas_wifi_only),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.canvas_wifi_only_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = model.configuration.wifiOnly,
                        onCheckedChange = null,
                        enabled = !model.busy,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = model.configuration.proceduralFallback,
                            enabled = !model.busy,
                            role = Role.Switch,
                            onValueChange = onProceduralFallback,
                        )
                        .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
                ) {
                    Column(modifier = remember { Modifier.weight(1f) }) {
                        Text(
                            text = stringResource(R.string.canvas_procedural_fallback),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.canvas_procedural_fallback_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = model.configuration.proceduralFallback,
                        onCheckedChange = null,
                        enabled = !model.busy,
                    )
                }
            }
        }

        CanvasSettingsGroup(
            title = stringResource(R.string.canvas_provider_health),
            action = {
                TextButton(
                    onClick = onRefresh,
                    enabled = model.canRefreshHealth && !model.busy,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.refresh),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
        ) {
            val providers = remember {
                listOf(
                    CanvasSource.BETTER_LYRICS to R.drawable.sync,
                    CanvasSource.APPLE_MUSIC to R.drawable.music_note,
                    CanvasSource.TIDAL to R.drawable.music_note,
                    CanvasSource.SPOTIFY to R.drawable.music_note,
                )
            }
            Column(modifier = remember { Modifier.fillMaxWidth() }) {
                providers.forEachIndexed { index, (provider, iconRes) ->
                    val health = when (provider) {
                        CanvasSource.BETTER_LYRICS -> model.health.betterLyrics
                        CanvasSource.APPLE_MUSIC -> model.health.appleMusic
                        CanvasSource.TIDAL -> model.health.tidal
                        CanvasSource.SPOTIFY -> model.health.spotify
                        CanvasSource.ALL -> CanvasHealth.NOT_CHECKED
                    }
                    CanvasHealthRow(
                        title = stringResource(provider.labelResource()),
                        status = stringResource(health.labelResource()),
                        iconRes = iconRes,
                        checking = health == CanvasHealth.CHECKING,
                        shape = itemShape(index, providers.size),
                    )
                }
            }
        }

        CanvasSettingsGroup(title = stringResource(R.string.canvas_cache)) {
            CanvasCacheCard(
                model = model,
                onCacheLimit = onCacheLimit,
                onClear = onClear,
            )
        }
    }
    CanvasSettingsDialogs(model = model, onAction = onAction)
}

@Composable
private fun CanvasSettingsGroup(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = remember { Modifier.fillMaxWidth() },
        verticalArrangement = remember { Arrangement.spacedBy(8.dp) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun CanvasChoiceRow(
    title: String,
    subtitle: String? = null,
    selected: Boolean,
    enabled: Boolean,
    shape: Shape = MaterialTheme.shapes.small,
    onClick: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
            Column(modifier = remember { Modifier.weight(1f) }) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasHealthRow(
    title: String,
    status: String,
    @DrawableRes iconRes: Int,
    checking: Boolean,
    shape: Shape,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
        ) {
            CanvasSectionIcon(iconRes = iconRes, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = remember { Modifier.weight(1f) }) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (checking) {
                CircularProgressIndicator(modifier = remember { Modifier.size(20.dp) }, strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun CanvasCacheCard(
    model: CanvasSettingsUiModel,
    onCacheLimit: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = remember { Modifier.fillMaxWidth() }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
            ) {
                CanvasSectionIcon(iconRes = R.drawable.storage, tint = MaterialTheme.colorScheme.primary)
                Column(
                    modifier = remember { Modifier.weight(1f) },
                    verticalArrangement = remember { Arrangement.spacedBy(6.dp) },
                ) {
                    Text(
                        text = "${model.cacheSize} used",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LinearProgressIndicator(
                        progress = { model.cacheProgress },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 6.dp),
                    )
                    Text(
                        text = stringResource(R.string.canvas_cache_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                onClick = onCacheLimit,
                enabled = !model.busy,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
                ) {
                    Column(modifier = remember { Modifier.weight(1f) }) {
                        Text(stringResource(R.string.max_cache_size), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = when (model.configuration.cacheLimitMb) {
                                0 -> stringResource(R.string.disable)
                                -1 -> stringResource(R.string.unlimited)
                                else -> model.cacheLimit
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.edit), null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                onClick = onClear,
                enabled = !model.busy,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.error,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = remember { Arrangement.spacedBy(16.dp) },
                ) {
                    Icon(painterResource(R.drawable.delete), null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(R.string.clear_canvas_cache),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasSectionIcon(@DrawableRes iconRes: Int, tint: Color) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(iconRes), null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun itemShape(index: Int, total: Int): Shape = when {
    total <= 1 -> MaterialTheme.shapes.large
    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    index == total - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    else -> RoundedCornerShape(4.dp)
}

@Composable
private fun CanvasSettingsDialogs(model: CanvasSettingsUiModel, onAction: (CanvasSettingsAction) -> Unit) {
    val onDismiss = remember(onAction) { { onAction(CanvasSettingsAction.DismissDialog) } }
    val onConfirmClear = remember(onAction) { { onAction(CanvasSettingsAction.ClearCache) } }
    when (model.dialog) {
        CanvasSettingsDialog.CACHE_LIMIT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.max_cache_size)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp).selectableGroup()) {
                    items(model.cacheOptions.values, key = CanvasCacheOption::limitMb, contentType = { "cache_limit" }) { option ->
                        val onSelect = remember(onAction, option.limitMb) {
                            { onAction(CanvasSettingsAction.SetCacheLimit(option.limitMb)) }
                        }
                        CanvasChoiceRow(
                            title = when (option.limitMb) {
                                0 -> stringResource(R.string.disable)
                                -1 -> stringResource(R.string.unlimited)
                                else -> option.formattedSize
                            },
                            selected = model.configuration.cacheLimitMb == option.limitMb,
                            enabled = !model.busy,
                            onClick = onSelect,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        CanvasSettingsDialog.CLEAR_CACHE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.clear_canvas_cache)) },
            text = { Text(stringResource(R.string.clear_canvas_cache_dialog)) },
            confirmButton = { TextButton(onClick = onConfirmClear, enabled = !model.busy) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun CanvasSettingsFailure(@StringRes messageRes: Int, onAction: (CanvasSettingsAction) -> Unit, modifier: Modifier) {
    val onRetry = remember(onAction) { { onAction(CanvasSettingsAction.Retry) } }
    Column(
        modifier = modifier.fillMaxSize().padding(SettingsDimensions.ScreenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(messageRes))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@StringRes
private fun CanvasSource.labelResource(): Int = when (this) {
    CanvasSource.BETTER_LYRICS -> R.string.canvas_better_lyrics
    CanvasSource.APPLE_MUSIC -> R.string.canvas_apple_music
    CanvasSource.TIDAL -> R.string.canvas_tidal
    CanvasSource.SPOTIFY -> R.string.canvas_spotify
    CanvasSource.ALL -> R.string.canvas_source_all
}

@StringRes
private fun CanvasHealth.labelResource(): Int = when (this) {
    CanvasHealth.NOT_CHECKED -> R.string.canvas_health_not_checked
    CanvasHealth.CHECKING -> R.string.canvas_health_checking
    CanvasHealth.AVAILABLE -> R.string.canvas_health_available
    CanvasHealth.UNAVAILABLE -> R.string.canvas_health_unavailable
    CanvasHealth.NOT_CONNECTED -> R.string.spotify_not_connected
    CanvasHealth.NOT_SELECTED -> R.string.canvas_health_not_selected
    CanvasHealth.DISABLED -> R.string.canvas_health_disabled
    CanvasHealth.OFFLINE -> R.string.canvas_health_offline
    CanvasHealth.WIFI_REQUIRED -> R.string.canvas_health_wifi_required
    CanvasHealth.LOW_DATA_MODE -> R.string.canvas_health_low_data
}
