/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.PreferenceGroup
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.viewmodels.SupportViewModel

private const val GITHUB_REPO_URL = "https://github.com/rgsekai/sekai-tune"
private const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/rgsekai"
private const val UPI_ID = "rgsekai@upi"
private const val UPI_NAME = "SekaiTune"
private const val UPI_NOTE = "SekaiTune"
private const val UPI_CURRENCY = "INR"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    navController: NavController,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val starsCount by viewModel.starsCount.collectAsStateWithLifecycle()

    fun copyUpiId() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("UPI ID", UPI_ID))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "UPI ID copied", Toast.LENGTH_SHORT).show()
        }
    }

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
            PreferenceGroup(title = stringResource(R.string.support_development_title)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.support_development_star)) },
                        description = stringResource(R.string.support_development_star_desc),
                        icon = { Icon(painterResource(R.drawable.star), null) },
                        trailingContent =
                            starsCount?.let { count ->
                                {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.star),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                            Text(
                                                text = "$count",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            },
                        onClick = { uriHandler.openUri(GITHUB_REPO_URL) },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.buy_me_a_coffee)) },
                        description = stringResource(R.string.buy_me_a_coffee_desc),
                        icon = { Icon(painterResource(R.drawable.coffee), null) },
                        onClick = { uriHandler.openUri(BUY_ME_A_COFFEE_URL) },
                    )
                }
                item {
                    PreferenceEntry(
                        title = { Text("UPI") },
                        description = "$UPI_ID - tap to pay, hold to copy",
                        icon = { Icon(painterResource(R.drawable.payments), null) },
                        onClick = {
                            try {
                                val uriString = "upi://pay?pa=$UPI_ID&pn=$UPI_NAME&tn=$UPI_NOTE&cu=$UPI_CURRENCY"
                                Log.d("UpiLink", "UPI URI: $uriString")
                                val uri = Uri.parse(uriString)
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                val chooser = Intent.createChooser(intent, "Pay with")
                                context.startActivity(chooser)
                            } catch (e: ActivityNotFoundException) {
                                copyUpiId()
                            }
                        },
                        onLongClick = {
                            copyUpiId()
                        },
                    )
                }
            }
        }
    }
}
