/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.PreferenceGroup
import moe.rgsekai.sekaitune.ui.utils.backToMain

private const val PROJECT_URL = "https://github.com/rgsekai/sekai-tune"
private const val ISSUES_URL = "$PROJECT_URL/issues/new/choose"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(navController: NavController) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.support)) },
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
        Column(
            modifier =
                Modifier
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).verticalScroll(rememberScrollState())
                    .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            PreferenceGroup(title = stringResource(R.string.support_get_help)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.support_report_issue)) },
                        description = stringResource(R.string.support_report_issue_desc),
                        icon = { Icon(painterResource(R.drawable.info), null) },
                        onClick = { uriHandler.openUri(ISSUES_URL) },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.support_project_page)) },
                        description = stringResource(R.string.support_project_page_desc),
                        icon = { Icon(painterResource(R.drawable.github), null) },
                        onClick = { uriHandler.openUri(PROJECT_URL) },
                    )
                }
            }

            PreferenceGroup(title = stringResource(R.string.support_app_information)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.privacy)) },
                        description = stringResource(R.string.support_privacy_desc),
                        icon = { Icon(painterResource(R.drawable.security), null) },
                        onClick = { navController.navigate("settings/privacy") },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.about)) },
                        description = stringResource(R.string.settings_about_subtitle),
                        icon = { Icon(painterResource(R.drawable.info), null) },
                        onClick = { navController.navigate("settings/about") },
                    )
                }
            }
        }
    }
}
