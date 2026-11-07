/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings.discord

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.discord.DiscordLogin
import moe.rgsekai.sekaitune.discord.NowPlaying
import moe.rgsekai.sekaitune.discord.PresenceConnection
import moe.rgsekai.sekaitune.discord.PresenceImage
import moe.rgsekai.sekaitune.discord.PresenceKind
import moe.rgsekai.sekaitune.discord.PresenceSettings
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.viewmodels.DiscordViewModel

const val DiscordRoute = "settings/discord"
private val HeaderHeight = 118.dp

@Composable
fun DiscordScreen(
    navController: NavController,
    viewModel: DiscordViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val signedIn by viewModel.signedIn.collectAsState()
    val login by viewModel.login.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val playing by viewModel.nowPlaying.collectAsState()

    val sample = remember { NowPlaying.sample(System.currentTimeMillis()) }
    val track = playing ?: sample

    LaunchedEffect(signedIn) {
        if (signedIn) viewModel.refreshProfile()
    }
    LaunchedEffect(login) {
        (login as? DiscordLogin.Failed)?.let {
            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
            viewModel.dismissProblem()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            delay(1_500)
            viewModel.cancelWaiting()
        }
    }

    fun openLogin() {
        scope.launch {
            val uri = viewModel.loginUri()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: Exception) {
                Toast.makeText(context, "No browser found to sign in", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerBottomPx = with(density) { (statusBarTop + HeaderHeight).toPx() }
    var previewBottomPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val previewHidden by remember(headerBottomPx) { derivedStateOf { previewBottomPx < headerBottomPx } }
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                IconButton(
                    onClick = { navController.navigateUp() },
                    onLongClick = {},
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.discord_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.discord),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Rich Presence",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = statusLine(viewModel.isConfigured, signedIn, settings.enabled, connection, login),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = settings.enabled,
                            onCheckedChange = { on -> viewModel.edit { copy(enabled = on) } },
                            enabled = signedIn,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(14.dp))
                    DiscordProfileCard(
                        profile = profile.takeIf { signedIn },
                        track = track,
                        settings = settings,
                        modifier = Modifier.onGloballyPositioned { previewBottomPx = it.boundsInWindow().bottom },
                    )
                }

                item {
                    Spacer(Modifier.height(14.dp))
                    if (!viewModel.isConfigured) {
                        Text(
                            text = "Discord Client ID is not configured. Add DISCORD_CLIENT_ID to local.properties.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else if (!signedIn) {
                        Button(
                            onClick = ::openLogin,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                            enabled = login != DiscordLogin.Finishing,
                        ) {
                            if (login == DiscordLogin.Finishing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text("Connect Discord", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = viewModel::logOut,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Disconnect Discord", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (signedIn) {
                    item {
                        DiscordSectionTitle("OPTIONS")
                        DiscordCard {
                            DiscordCardTitle("Refresh interval")
                            Spacer(Modifier.height(4.dp))
                            DiscordStepSlider(
                                value = settings.refreshSeconds,
                                values = (PresenceSettings.MinRefreshSeconds..PresenceSettings.MaxRefreshSeconds).toList(),
                                label = { "${it}s" },
                                onValueChange = { seconds -> viewModel.edit { copy(refreshSeconds = seconds) } },
                            )
                        }
                    }

                    item {
                        DiscordSectionTitle(stringResource(R.string.discord_behaviour))
                        DiscordSwitchCard(
                            title = stringResource(R.string.discord_clear_on_pause),
                            description = stringResource(R.string.discord_clear_on_pause_desc),
                            checked = settings.clearOnPause,
                            onCheckedChange = { on -> viewModel.edit { copy(clearOnPause = on) } },
                        )
                    }

                    item {
                        DiscordSectionTitle("ACTIVITY")
                        DiscordCard {
                            DiscordCardTitle("Activity type")
                            Spacer(Modifier.height(8.dp))
                            DiscordSegmented(
                                options = PresenceKind.entries,
                                selected = settings.kind,
                                label = { it.label },
                                onSelect = { kind -> viewModel.edit { copy(kind = kind) } },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        DiscordTextCard("Activity name", settings.name, { text -> viewModel.edit { copy(name = text) } }, hint = "Sekai Tune")
                    }

                    item {
                        DiscordSectionTitle("STRINGS CUSTOMIZATION")
                        DiscordNoticeCard("Empty a box to hide that line")
                        Spacer(Modifier.height(10.dp))
                        DiscordTextCard("Details string", settings.details, { text -> viewModel.edit { copy(details = text) } })
                        Spacer(Modifier.height(10.dp))
                        DiscordTextCard("State string", settings.state, { text -> viewModel.edit { copy(state = text) } })
                        Spacer(Modifier.height(10.dp))
                        DiscordTextCard("Large image hover text", settings.largeText, { text -> viewModel.edit { copy(largeText = text) } })
                        Spacer(Modifier.height(10.dp))
                        DiscordTextCard("Small image hover text", settings.smallText, { text -> viewModel.edit { copy(smallText = text) } })
                        Spacer(Modifier.height(10.dp))
                        DiscordPlaceholderCard()
                    }

                    item {
                        DiscordSectionTitle("IMAGES")
                        DiscordImageSourceCard(
                            title = "Small image source",
                            selected = settings.smallImage,
                            options = PresenceImage.ForSmall,
                            onSelect = { image -> viewModel.edit { copy(smallImage = image) } },
                            spin = settings.spin,
                            onSpinChange = { spin -> viewModel.edit { copy(spinSeconds = spin.secondsPerTurn, spinFps = spin.fps) } },
                        )
                        Spacer(Modifier.height(10.dp))
                        DiscordImageSourceCard(
                            title = "Big image source",
                            selected = settings.largeImage,
                            options = PresenceImage.ForLarge,
                            onSelect = { image -> viewModel.edit { copy(largeImage = image) } },
                        )
                    }

                    item {
                        DiscordSectionTitle("BUTTONS")
                        DiscordButtonCard("1st button", settings.button1) { button -> viewModel.edit { copy(button1 = button) } }
                        Spacer(Modifier.height(10.dp))
                        DiscordButtonCard("2nd button", settings.button2) { button -> viewModel.edit { copy(button2 = button) } }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Discord only shows buttons to other users, never on your own profile.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(240.dp))
                }
            }

            DiscordFloatingPreview(
                visible = previewHidden,
                track = track,
                settings = settings,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomInset + 12.dp),
            )
        }
    }
}

private fun statusLine(
    configured: Boolean,
    signedIn: Boolean,
    enabled: Boolean,
    connection: PresenceConnection,
    login: DiscordLogin,
): String = when {
    !configured -> "Not configured in this build"
    login == DiscordLogin.Finishing -> "Signing in…"
    !signedIn -> "Log in below to connect"
    !enabled -> "Presence is disabled"
    connection == PresenceConnection.Live -> "Live on Discord profile"
    connection == PresenceConnection.Connecting -> "Connecting to Discord…"
    connection == PresenceConnection.Retrying -> "Can't reach Discord. Retrying…"
    else -> "Ready (idle)"
}

