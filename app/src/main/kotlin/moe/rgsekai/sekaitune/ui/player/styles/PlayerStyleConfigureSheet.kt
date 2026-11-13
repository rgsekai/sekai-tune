package moe.rgsekai.sekaitune.ui.player.styles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.ShowOneLineLyricsKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropBlurEnabledKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropBlurStrengthKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropMode
import moe.rgsekai.sekaitune.constants.ThemeBackdropModeKey
import moe.rgsekai.sekaitune.ui.component.EnumListPreference
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.SwitchPreference
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.roundToInt

/**
 * Bottom sheet displaying configuration options for Immersive player style:
 * - One-line lyrics toggle
 * - Theme backdrop mode (Glow animated vs Static)
 * - Backdrop blur toggle
 * - Backdrop blur strength slider
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerStyleConfigureSheet(
    style: PlayerStyle,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.player_styles_action_configure) + " • " + stringResource(style.displayNameRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp),
            )

            when (style) {
                PlayerStyle.IMMERSIVE -> {
                    // One-line lyrics option
                    val (showOneLineLyrics, onShowOneLineLyricsChange) = rememberPreference(
                        ShowOneLineLyricsKey,
                        defaultValue = true,
                    )
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_one_line_lyrics)) },
                        description = stringResource(R.string.show_one_line_lyrics_desc),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = showOneLineLyrics,
                        onCheckedChange = onShowOneLineLyricsChange,
                    )

                    // Theme Backdrop Options (moved from Appearance Settings)
                    val (themeBackdropMode, onThemeBackdropModeChange) =
                        rememberEnumPreference(
                            ThemeBackdropModeKey,
                            defaultValue = ThemeBackdropMode.GLOW_ANIMATED,
                        )
                    val (themeBackdropBlurEnabled, onThemeBackdropBlurEnabledChange) =
                        rememberPreference(
                            ThemeBackdropBlurEnabledKey,
                            defaultValue = false,
                        )
                    val (themeBackdropBlurStrength, onThemeBackdropBlurStrengthChange) =
                        rememberPreference(
                            ThemeBackdropBlurStrengthKey,
                            defaultValue = 60,
                        )

                    EnumListPreference(
                        title = { Text(stringResource(R.string.theme_backdrop)) },
                        description = stringResource(R.string.theme_backdrop_desc),
                        icon = { Icon(painterResource(R.drawable.palette), null) },
                        selectedValue = themeBackdropMode,
                        onValueSelected = onThemeBackdropModeChange,
                        isEnabled = !themeBackdropBlurEnabled,
                        valueText = {
                            when (it) {
                                ThemeBackdropMode.GLOW_ANIMATED -> stringResource(R.string.theme_backdrop_glow_animated)
                                ThemeBackdropMode.STATIC -> stringResource(R.string.theme_backdrop_static)
                            }
                        },
                    )

                    SwitchPreference(
                        title = { Text(stringResource(R.string.theme_backdrop_blur)) },
                        description = stringResource(R.string.theme_backdrop_blur_desc),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        checked = themeBackdropBlurEnabled,
                        onCheckedChange = onThemeBackdropBlurEnabledChange,
                    )

                    PreferenceEntry(
                        title = { Text(stringResource(R.string.theme_backdrop_blur_strength)) },
                        description = if (themeBackdropBlurStrength == 0) {
                            stringResource(R.string.theme_backdrop_blur_transparent)
                        } else {
                            stringResource(R.string.backdrop_blur_amount_value, themeBackdropBlurStrength)
                        },
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        isEnabled = themeBackdropBlurEnabled,
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = themeBackdropBlurStrength.toFloat(),
                                onValueChange = { onThemeBackdropBlurStrengthChange(it.roundToInt()) },
                                valueRange = 0f..100f,
                                steps = 19,
                                enabled = themeBackdropBlurEnabled,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
