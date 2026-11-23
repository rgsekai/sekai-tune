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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AccountChannelHandleKey
import moe.rgsekai.sekaitune.constants.AccountEmailKey
import moe.rgsekai.sekaitune.constants.AccountNameKey
import moe.rgsekai.sekaitune.constants.DataSyncIdKey
import moe.rgsekai.sekaitune.constants.InnerTubeCookieKey
import moe.rgsekai.sekaitune.constants.VisitorDataKey
import moe.rgsekai.sekaitune.innertube.utils.hasYouTubeLoginCookie
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.PreferenceGroup
import moe.rgsekai.sekaitune.ui.utils.LocalIsTv
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.utils.PreferenceStore
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.putLegacyPoToken
import moe.rgsekai.sekaitune.utils.rememberPreference

@Composable
fun AdvancedSettings(navController: NavController) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isTv = LocalIsTv.current

    val (accountNamePref, onAccountNameChange) = rememberPreference(AccountNameKey, "")
    val (accountEmail, onAccountEmailChange) = rememberPreference(AccountEmailKey, "")
    val (accountChannelHandle, onAccountChannelHandleChange) = rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (visitorData, onVisitorDataChange) = rememberPreference(VisitorDataKey, "")
    val (dataSyncId, onDataSyncIdChange) = rememberPreference(DataSyncIdKey, "")

    val onLegacyPoTokenChange: (String) -> Unit = { value ->
        PreferenceStore.launchEdit(context.dataStore) {
            putLegacyPoToken(value)
        }
    }

    val isLoggedIn =
        remember(innerTubeCookie) {
            hasYouTubeLoginCookie(innerTubeCookie)
        }

    var showToken by remember { mutableStateOf(false) }
    var showTokenEditor by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            showToken = false
        }
    }

    val tokenActionTitle =
        when {
            !isLoggedIn -> stringResource(R.string.advanced_login)
            showToken -> stringResource(R.string.token_shown)
            else -> stringResource(R.string.token_hidden)
        }

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

                if (!isTv) {
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
            }

            if (!isTv) {
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

                item {
                    PreferenceEntry(
                        title = {
                            Text(
                                text = tokenActionTitle,
                                color = if (isLoggedIn && showToken) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                            )
                        },
                        description = stringResource(R.string.token_adv_login_description),
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            if (!isLoggedIn) {
                                showTokenEditor = true
                            } else if (!showToken) {
                                showToken = true
                            } else {
                                showTokenEditor = true
                            }
                        },
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

        if (showTokenEditor) {
            TokenEditorDialog(
                innerTubeCookie = innerTubeCookie,
                visitorData = visitorData,
                dataSyncId = dataSyncId,
                accountNamePref = accountNamePref,
                accountEmail = accountEmail,
                accountChannelHandle = accountChannelHandle,
                onInnerTubeCookieChange = onInnerTubeCookieChange,
                onPoTokenChange = onLegacyPoTokenChange,
                onVisitorDataChange = onVisitorDataChange,
                onDataSyncIdChange = onDataSyncIdChange,
                onAccountNameChange = onAccountNameChange,
                onAccountEmailChange = onAccountEmailChange,
                onAccountChannelHandleChange = onAccountChannelHandleChange,
                onDismiss = { showTokenEditor = false },
            )
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
