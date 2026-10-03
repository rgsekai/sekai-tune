/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AmbientArtScaleKey
import moe.rgsekai.sekaitune.constants.AmbientCanvasEnabledKey
import moe.rgsekai.sekaitune.constants.AmbientShowArtistKey
import moe.rgsekai.sekaitune.constants.AmbientShowLyricsKey
import moe.rgsekai.sekaitune.constants.AmbientShowProgressBarKey
import moe.rgsekai.sekaitune.constants.AmbientShowTitleKey
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.PreferenceGroup
import moe.rgsekai.sekaitune.ui.component.SwitchPreference
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientModeSettingsScreen(navController: NavController) {
    val (artScale, onArtScaleChange) = rememberPreference(AmbientArtScaleKey, defaultValue = 0.85f)
    val (canvasEnabled, onCanvasEnabledChange) = rememberPreference(AmbientCanvasEnabledKey, defaultValue = true)
    val (showProgressBar, onShowProgressBarChange) = rememberPreference(AmbientShowProgressBarKey, defaultValue = true)
    val (showTitle, onShowTitleChange) = rememberPreference(AmbientShowTitleKey, defaultValue = true)
    val (showArtist, onShowArtistChange) = rememberPreference(AmbientShowArtistKey, defaultValue = true)
    val (showLyrics, onShowLyricsChange) = rememberPreference(AmbientShowLyricsKey, defaultValue = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ambient_mode_settings)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            modifier =
                Modifier
                    .padding(top = topPadding)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.aod_customize_layout)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ambient_art_size)) },
                        description = "${(artScale * 100).roundToInt()}%",
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = artScale,
                                onValueChange = onArtScaleChange,
                                valueRange = 0.3f..0.95f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ambient_live_artwork)) },
                        description = stringResource(R.string.ambient_live_artwork_desc),
                        icon = { Icon(painterResource(R.drawable.sparkles), null) },
                        checked = canvasEnabled,
                        onCheckedChange = onCanvasEnabledChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ambient_show_progress_bar)) },
                        description = stringResource(R.string.ambient_show_progress_bar_desc),
                        icon = { Icon(painterResource(R.drawable.sliders), null) },
                        checked = showProgressBar,
                        onCheckedChange = onShowProgressBarChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ambient_show_song_name)) },
                        description = stringResource(R.string.ambient_show_song_name_desc),
                        icon = { Icon(painterResource(R.drawable.music_note), null) },
                        checked = showTitle,
                        onCheckedChange = onShowTitleChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ambient_show_artist_name)) },
                        description = stringResource(R.string.ambient_show_artist_name_desc),
                        icon = { Icon(painterResource(R.drawable.artist), null) },
                        checked = showArtist,
                        onCheckedChange = onShowArtistChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.ambient_show_lyrics)) },
                        description = stringResource(R.string.ambient_show_lyrics_desc),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = showLyrics,
                        onCheckedChange = onShowLyricsChange,
                    )
                }
            }
        }
    }
}
