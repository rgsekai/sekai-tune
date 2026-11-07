/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.scrobble.ScrobbleProvider
import moe.rgsekai.sekaitune.scrobble.ScrobbleViewModel
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.utils.LocalIsTv
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.ui.utils.tvFocusOutline

@Composable
fun ScrobbleSettingsScreen(
    navController: NavController,
    provider: ScrobbleProvider,
    viewModel: ScrobbleViewModel = hiltViewModel(),
) {
    val isTv = LocalIsTv.current
    val playerInsets = LocalPlayerAwareWindowInsets.current
    val state by viewModel.uiState(provider).collectAsStateWithLifecycle()
    var showSignInSheet by rememberSaveable { mutableStateOf(false) }

    val iconRes = if (provider == ScrobbleProvider.LASTFM) R.drawable.ic_lastfm else R.drawable.ic_librefm
    val accentColor = if (provider == ScrobbleProvider.LASTFM) Color(0xFFD51007) else Color(0xFFB52323)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(provider.title) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                        modifier = if (isTv) Modifier.tvFocusOutline() else Modifier,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(playerInsets.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = SettingsDimensions.ScreenBottomPadding + playerInsets.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.isConfigured) {
                item {
                    ExpressiveSectionCard(title = stringResource(R.string.scrobble_not_configured_title)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.scrobble_not_configured_desc, provider.title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                ExpressiveSectionCard(title = stringResource(R.string.account)) {
                    if (!state.isAuthenticated) {
                        ExpressiveActionRow(
                            icon = painterResource(iconRes),
                            title = stringResource(R.string.scrobble_sign_in_title, provider.title),
                            subtitle = if (!state.isConfigured) stringResource(R.string.scrobble_not_configured_title) else stringResource(R.string.scrobble_not_connected),
                            accent = accentColor,
                            tintIcon = false,
                            onClick = {
                                if (!state.isLoading && state.isConfigured) {
                                    showSignInSheet = true
                                } else if (!state.isConfigured) {
                                    viewModel.login(provider, "", "")
                                }
                            },
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = state.username,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(R.string.scrobble_connected_as, state.username),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        ExpressiveDivider()

                        ExpressiveActionRow(
                            icon = painterResource(R.drawable.logout),
                            title = stringResource(R.string.action_logout),
                            accent = MaterialTheme.colorScheme.error,
                            onClick = { viewModel.logout(provider) },
                        )
                    }
                }
            }

            if (state.isAuthenticated) {
                item {
                    ExpressiveSectionCard(title = stringResource(R.string.settings)) {
                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.sync),
                            title = stringResource(R.string.scrobble_enable_switch),
                            subtitle = stringResource(R.string.scrobble_enable_switch_desc),
                            checked = state.isEnabled,
                            onCheckedChange = { viewModel.setEnabled(provider, it) },
                        )

                        ExpressiveDivider()

                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.music_note),
                            title = stringResource(R.string.scrobble_now_playing),
                            subtitle = stringResource(R.string.scrobble_now_playing_desc),
                            checked = state.nowPlayingEnabled,
                            onCheckedChange = { viewModel.setNowPlayingEnabled(provider, it) },
                        )

                        ExpressiveDivider()

                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.history),
                            title = stringResource(R.string.scrobble_submit_plays),
                            subtitle = stringResource(R.string.scrobble_submit_plays_desc),
                            checked = state.scrobbleEnabled,
                            onCheckedChange = { viewModel.setScrobbleEnabled(provider, it) },
                        )

                        ExpressiveDivider()

                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.favorite),
                            title = stringResource(R.string.scrobble_send_likes),
                            subtitle = stringResource(R.string.scrobble_send_likes_desc, provider.title),
                            checked = state.sendLikesEnabled,
                            onCheckedChange = { viewModel.setSendLikesEnabled(it) },
                        )

                        ExpressiveDivider()

                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.person),
                            title = stringResource(R.string.scrobble_primary_artist),
                            subtitle = stringResource(R.string.scrobble_primary_artist_desc),
                            checked = state.primaryArtistOnly,
                            onCheckedChange = { viewModel.setPrimaryArtistOnly(provider, it) },
                        )

                        ExpressiveDivider()

                        ExpressiveActionRow(
                            icon = painterResource(R.drawable.cached),
                            title = stringResource(R.string.scrobble_pending_scrobbles),
                            subtitle = stringResource(R.string.scrobble_pending_desc, state.pendingCount),
                            onClick = {},
                        )
                    }
                }
            }
        }
    }

    if (showSignInSheet) {
        ScrobbleSignInSheet(
            provider = provider,
            isLoading = state.isLoading,
            onDismiss = { showSignInSheet = false },
            onSignIn = { user, pass ->
                showSignInSheet = false
                viewModel.login(provider, user, pass)
            },
        )
    }

    val errorMessage = state.errorMessage
    if (errorMessage != null) {
        DefaultDialog(
            onDismiss = viewModel::dismissError,
            title = { Text(stringResource(R.string.error_unknown)) },
            content = { Text(errorMessage) },
            buttons = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@Composable
private fun ScrobbleSignInSheet(
    provider: ScrobbleProvider,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSignIn: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.scrobble_sign_in_title, provider.title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.scrobble_username_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    keyboardType = KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                ),
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.scrobble_password_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Password,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (username.isNotBlank() && password.isNotBlank()) {
                            onSignIn(username, password)
                        }
                    },
                ),
            )

            Button(
                onClick = { onSignIn(username, password) },
                enabled = username.isNotBlank() && password.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.scrobble_sign_in_button))
                }
            }

            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.registerUrl))
                    context.startActivity(intent)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.scrobble_register_link))
            }
        }
    }
}
