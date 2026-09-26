/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.PreferenceGroup
import moe.rgsekai.sekaitune.ui.utils.backToMain

@Composable
fun AdvancedSettings(navController: NavController) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.content_and_behavior)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.content)) },
                        description = stringResource(R.string.settings_content_subtitle),
                        icon = { Icon(painterResource(R.drawable.language), null) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                            )
                        },
                        onClick = { navController.navigate("settings/content") },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.settings_behavior_title)) },
                        description = stringResource(R.string.settings_behavior_subtitle),
                        icon = { Icon(painterResource(R.drawable.swipe), null) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                            )
                        },
                        onClick = { navController.navigate("settings/privacy") },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.network_and_connectivity)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.internet)) },
                        description = stringResource(R.string.settings_internet_subtitle),
                        icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                            )
                        },
                        onClick = { navController.navigate("settings/internet") },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.playback_engine)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.po_token_generation)) },
                        description = stringResource(R.string.settings_po_token_subtitle),
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                            )
                        },
                        onClick = { navController.navigate("settings/po_token") },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.developer_and_system)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.settings_developer_options_title)) },
                        description = stringResource(R.string.settings_developer_options_subtitle),
                        icon = { Icon(painterResource(R.drawable.experiment), null) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                            )
                        },
                        onClick = { navController.navigate("settings/misc") },
                    )
                }

                if (isAndroid12OrLater) {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.default_links)) },
                            description = stringResource(R.string.open_supported_links),
                            icon = { Icon(painterResource(R.drawable.link), null) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.navigate_next),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                launchDefaultLinksSettings(context)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun launchDefaultLinksSettings(context: Context) {
    try {
        @Suppress("InlinedApi")
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(
                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
            } else {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
            }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        when (e) {
            is ActivityNotFoundException,
            is SecurityException,
            -> {
                Toast
                    .makeText(
                        context,
                        R.string.open_app_settings_error,
                        Toast.LENGTH_LONG,
                    ).show()
            }

            else -> {
                Toast
                    .makeText(
                        context,
                        R.string.open_app_settings_error,
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }
}
