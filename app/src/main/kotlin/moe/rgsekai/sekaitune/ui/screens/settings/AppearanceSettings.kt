/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AppFontPreference
import moe.rgsekai.sekaitune.constants.BackdropBlurAmountKey
import moe.rgsekai.sekaitune.constants.BackdropEnabledKey
import moe.rgsekai.sekaitune.constants.BlurRadiusKey
import moe.rgsekai.sekaitune.constants.ChipSortTypeKey
import moe.rgsekai.sekaitune.constants.CropThumbnailToSquareKey
import moe.rgsekai.sekaitune.constants.CustomFontNameKey
import moe.rgsekai.sekaitune.constants.CustomFontUriKey
import moe.rgsekai.sekaitune.constants.DefaultOpenTabKey
import moe.rgsekai.sekaitune.constants.DisableBlurKey
import moe.rgsekai.sekaitune.constants.FontPreferenceKey
import moe.rgsekai.sekaitune.constants.GridItemSize
import moe.rgsekai.sekaitune.constants.GridItemsSizeKey
import moe.rgsekai.sekaitune.constants.LibraryFilter
import moe.rgsekai.sekaitune.constants.MiniPlayerBackgroundStyle
import moe.rgsekai.sekaitune.constants.MiniPlayerBackgroundStyleKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropMode
import moe.rgsekai.sekaitune.constants.ThemeBackdropModeKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropBlurEnabledKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropBlurStrengthKey
import moe.rgsekai.sekaitune.constants.LiquidGlassGlobalEnabledKey
import moe.rgsekai.sekaitune.constants.LiquidGlassNavBarEnabledKey
import moe.rgsekai.sekaitune.constants.ShowOneLineLyricsKey
import moe.rgsekai.sekaitune.constants.ShowTagsInLibraryKey
import moe.rgsekai.sekaitune.constants.SwipeThumbnailKey
import moe.rgsekai.sekaitune.constants.SwipeToSongKey
import moe.rgsekai.sekaitune.constants.ThumbnailCornerRadiusKey
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.EnumListPreference
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.ListPreference
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.PreferenceGroup
import moe.rgsekai.sekaitune.ui.component.SwitchPreference
import moe.rgsekai.sekaitune.ui.component.ThumbnailCornerRadiusSelectorButton
import moe.rgsekai.sekaitune.ui.theme.CustomFontLoader
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(navController: NavController) {
    val context = LocalContext.current
    val (showOneLineLyrics, onShowOneLineLyricsChange) =
        rememberPreference(
            ShowOneLineLyricsKey,
            defaultValue = true,
        )
    val (thumbnailCornerRadius, onThumbnailCornerRadiusChange) =
        rememberPreference(
            key = ThumbnailCornerRadiusKey,
            defaultValue = 16f, // default dp
        )
    val (cropThumbnailToSquare, onCropThumbnailToSquareChange) =
        rememberPreference(
            CropThumbnailToSquareKey,
            defaultValue = false,
        )
    val (miniPlayerBackground, onMiniPlayerBackgroundChange) =
        rememberEnumPreference(
            MiniPlayerBackgroundStyleKey,
            defaultValue = MiniPlayerBackgroundStyle.GLOW,
        )
    val (disableBlur, onDisableBlurChange) = rememberPreference(DisableBlurKey, defaultValue = false)
    val (blurRadius, onBlurRadiusChange) = rememberPreference(BlurRadiusKey, defaultValue = 48f)
    val (backdropEnabled, onBackdropEnabledChange) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount, onBackdropBlurAmountChange) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
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
    val (fontPreference, onFontPreferenceChange) =
        rememberEnumPreference(
            FontPreferenceKey,
            defaultValue = AppFontPreference.DEFAULT,
        )
    val (customFontUri, onCustomFontUriChange) = rememberPreference(CustomFontUriKey, defaultValue = "")
    val (customFontName, onCustomFontNameChange) = rememberPreference(CustomFontNameKey, defaultValue = "")
    val (defaultOpenTab, onDefaultOpenTabChange) =
        rememberEnumPreference(
            DefaultOpenTabKey,
            defaultValue = NavigationTab.HOME,
        )
    val (swipeThumbnail, onSwipeThumbnailChange) =
        rememberPreference(
            SwipeThumbnailKey,
            defaultValue = true,
        )
    val (gridItemSize, onGridItemSizeChange) =
        rememberEnumPreference(
            GridItemsSizeKey,
            defaultValue = GridItemSize.SMALL,
        )

    val (swipeToSong, onSwipeToSongChange) =
        rememberPreference(
            SwipeToSongKey,
            defaultValue = false,
        )

    val (showTagsInLibrary, onShowTagsInLibraryChange) =
        rememberPreference(
            ShowTagsInLibraryKey,
            defaultValue = false,
        )

    val customFontPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (!CustomFontLoader.isSupportedTtf(context, uri)) {
                Toast.makeText(context, context.getString(R.string.custom_font_invalid), Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (customFontUri.isNotBlank() && customFontUri != uri.toString()) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(
                        Uri.parse(customFontUri),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            onCustomFontUriChange(uri.toString())
            onCustomFontNameChange(CustomFontLoader.displayName(context, uri))
            onFontPreferenceChange(AppFontPreference.CUSTOM)
        }
    val pickCustomFont =
        remember(customFontPickerLauncher) {
            {
                customFontPickerLauncher.launch(CustomFontLoader.supportedMimeTypes)
            }
        }
    val onFontPreferenceSelected =
        remember(customFontUri, onFontPreferenceChange, pickCustomFont) {
            { value: AppFontPreference ->
                onFontPreferenceChange(value)
                if (value == AppFontPreference.CUSTOM && customFontUri.isBlank()) {
                    pickCustomFont()
                }
            }
        }

    val (defaultChip, onDefaultChipChange) =
        rememberEnumPreference(
            key = ChipSortTypeKey,
            defaultValue = LibraryFilter.LIBRARY,
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance)) },
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
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState()),
        ) {
            PreferenceGroup(title = stringResource(R.string.theme)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.liquid_glass)) },
                        description = stringResource(R.string.liquid_glass_performance_warning),
                        icon = { Icon(painterResource(R.drawable.tune), null) },
                        onClick = { navController.navigate("settings/appearance/liquid_glass") },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.app_icon)) },
                        description = stringResource(R.string.app_icon_description),
                        icon = {
                            Image(
                                painter = painterResource(id = R.drawable.sekai_tune),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).clip(CircleShape),
                            )
                        },
                        onClick = { navController.navigate(route = "settings/appearance/icon") },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.appearance_section_typography)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.font_preference)) },
                        description = stringResource(R.string.font_preference_desc),
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        selectedValue = fontPreference,
                        onValueSelected = onFontPreferenceSelected,
                        valueText = {
                            when (it) {
                                AppFontPreference.DEFAULT -> stringResource(R.string.font_preference_default)
                                AppFontPreference.SYSTEM -> stringResource(R.string.font_preference_system)
                                AppFontPreference.CUSTOM -> stringResource(R.string.font_preference_custom)
                            }
                        },
                    )
                }

                item(visible = fontPreference == AppFontPreference.CUSTOM) {
                    val customFontDescription =
                        if (customFontName.isNotBlank()) {
                            customFontName
                        } else if (customFontUri.isBlank()) {
                            stringResource(R.string.custom_font_desc)
                        } else {
                            customFontUri
                        }
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.custom_font)) },
                        description = customFontDescription,
                        icon = { Icon(painterResource(R.drawable.text_fields), null) },
                        onClick = pickCustomFont,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.display)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.disable_blur)) },
                        description = stringResource(R.string.disable_blur_desc),
                        icon = { Icon(painterResource(R.drawable.blur_off), null) },
                        checked = disableBlur,
                        onCheckedChange = onDisableBlurChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.blur_intensity)) },
                        description = stringResource(R.string.blur_intensity_value, blurRadius.roundToInt()),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        isEnabled = !disableBlur,
                        content = {
                            Spacer(modifier = Modifier.height(10.dp))
                            Slider(
                                value = blurRadius,
                                onValueChange = onBlurRadiusChange,
                                valueRange = 0f..64f,
                                steps = 63,
                                enabled = !disableBlur,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                    )
                }

            }

            PreferenceGroup(title = stringResource(R.string.theme_backdrop)) {
                item {
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
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.theme_backdrop_blur)) },
                        description = stringResource(R.string.theme_backdrop_blur_desc),
                        icon = { Icon(painterResource(R.drawable.blur_on), null) },
                        checked = themeBackdropBlurEnabled,
                        onCheckedChange = onThemeBackdropBlurEnabledChange,
                    )
                }

                item {
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

            PreferenceGroup(title = stringResource(R.string.player)) {

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_one_line_lyrics)) },
                        description = stringResource(R.string.show_one_line_lyrics_desc),
                        icon = { Icon(painterResource(R.drawable.lyrics), null) },
                        checked = showOneLineLyrics,
                        onCheckedChange = onShowOneLineLyricsChange,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.appearance_section_player_artwork)) {
                item {
                    ThumbnailCornerRadiusSelectorButton(
                        onRadiusSelected = {},
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.crop_thumbnail_to_square)) },
                        description = stringResource(R.string.crop_thumbnail_to_square_desc),
                        icon = { Icon(painterResource(R.drawable.image), null) },
                        checked = cropThumbnailToSquare,
                        onCheckedChange = onCropThumbnailToSquareChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.enable_swipe_thumbnail)) },
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        checked = swipeThumbnail,
                        onCheckedChange = onSwipeThumbnailChange,
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.appearance_section_mini_player)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.mini_player_background_style)) },
                        icon = { Icon(painterResource(R.drawable.gradient), null) },
                        selectedValue = miniPlayerBackground,
                        onValueSelected = onMiniPlayerBackgroundChange,
                        valueText = {
                            when (it) {
                                MiniPlayerBackgroundStyle.THEME -> stringResource(R.string.follow_theme)
                                MiniPlayerBackgroundStyle.GRADIENT -> stringResource(R.string.gradient)
                                MiniPlayerBackgroundStyle.GLOW -> stringResource(R.string.glow)
                                MiniPlayerBackgroundStyle.LIVE_MESH -> stringResource(R.string.live_mesh)
                                MiniPlayerBackgroundStyle.LIQUID_GLASS -> stringResource(R.string.player_background_liquid_glass)
                            }
                        },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.appearance_section_ambient)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.aod_customize_title)) },
                        description = stringResource(R.string.aod_customize_entry_desc),
                        icon = { Icon(painterResource(R.drawable.bedtime), null) },
                        onClick = { navController.navigate("settings/appearance/aod_customized") },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.ambient_mode_settings)) },
                        description = stringResource(R.string.ambient_mode_settings_desc),
                        icon = { Icon(painterResource(R.drawable.fullscreen), null) },
                        onClick = { navController.navigate("settings/appearance/ambient_mode") },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.appearance_section_navigation_layout)) {
                item {
                    EnumListPreference(
                        title = { Text(stringResource(R.string.default_open_tab)) },
                        icon = { Icon(painterResource(R.drawable.nav_bar), null) },
                        selectedValue = defaultOpenTab,
                        onValueSelected = onDefaultOpenTabChange,
                        valueText = {
                            when (it) {
                                NavigationTab.HOME -> stringResource(R.string.home)
                                NavigationTab.SEARCH -> stringResource(R.string.search)
                                NavigationTab.MOODANDGENRES -> stringResource(R.string.mood_and_genres)
                                NavigationTab.LIBRARY -> stringResource(R.string.filter_library)
                            }
                        },
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.default_lib_chips)) },
                        icon = { Icon(painterResource(R.drawable.tab), null) },
                        selectedValue = defaultChip,
                        values =
                            listOf(
                                LibraryFilter.LIBRARY,
                                LibraryFilter.PLAYLISTS,
                                LibraryFilter.SONGS,
                                LibraryFilter.ALBUMS,
                                LibraryFilter.ARTISTS,
                                LibraryFilter.AI_MIXES,
                            ),
                        valueText = {
                            when (it) {
                                LibraryFilter.SONGS -> stringResource(R.string.songs)
                                LibraryFilter.ARTISTS -> stringResource(R.string.artists)
                                LibraryFilter.ALBUMS -> stringResource(R.string.albums)
                                LibraryFilter.PLAYLISTS -> stringResource(R.string.playlists)
                                LibraryFilter.SPOTIFY -> stringResource(R.string.spotify_playlists)
                                LibraryFilter.LIBRARY -> stringResource(R.string.filter_library)
                                LibraryFilter.AI_MIXES -> stringResource(R.string.top_mixes)
                            }
                        },
                        onValueSelected = onDefaultChipChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_tags_in_library)) },
                        description = stringResource(R.string.show_tags_in_library_desc),
                        icon = { Icon(painterResource(R.drawable.filter_alt), null) },
                        checked = showTagsInLibrary,
                        onCheckedChange = onShowTagsInLibraryChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.swipe_song_to_add)) },
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        checked = swipeToSong,
                        onCheckedChange = onSwipeToSongChange,
                    )
                }
            }

            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
                ).padding(bottom = SettingsDimensions.ScreenBottomPadding)
            )
        }
    }
}


enum class DarkMode {
    ON,
    OFF,
    AUTO,
}

enum class NavigationTab {
    HOME,
    SEARCH,
    MOODANDGENRES,
    LIBRARY,
}

enum class PlayerTextAlignment {
    SIDED,
    CENTER,
}

enum class LyricsPosition {
    LEFT,
    CENTER,
    RIGHT,
}




