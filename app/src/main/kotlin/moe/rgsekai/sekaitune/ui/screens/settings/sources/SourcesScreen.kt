/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings.sources

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.webdav.WebDavTestResult
import moe.rgsekai.sekaitune.playback.sources.RegisteredSource
import moe.rgsekai.sekaitune.playback.sources.SourceHealth
import moe.rgsekai.sekaitune.playback.sources.SourceKind
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.GlassComponent
import moe.rgsekai.sekaitune.ui.component.LocalGlassEffectConfig
import moe.rgsekai.sekaitune.ui.component.PreferenceGroupTitle
import moe.rgsekai.sekaitune.ui.component.liquidGlass
import moe.rgsekai.sekaitune.ui.screens.settings.SettingsDimensions
import moe.rgsekai.sekaitune.ui.utils.backToMain
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    navController: NavController,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsState()
    val toleranceSeconds by viewModel.toleranceSeconds.collectAsState()
    val testingSourceId by viewModel.testingSourceId.collectAsState()
    val isAdding by viewModel.isAdding.collectAsState()
    val addError by viewModel.addError.collectAsState()

    val isWebDavConfigured by viewModel.isWebDavConfigured.collectAsState()
    val webDavUrl by viewModel.webDavUrl.collectAsState()
    val webDavUsername by viewModel.webDavUsername.collectAsState()
    val webDavTrustSelfSigned by viewModel.webDavTrustSelfSigned.collectAsState()
    val isTestingWebDav by viewModel.isTestingWebDav.collectAsState()
    val webDavTestResult by viewModel.webDavTestResult.collectAsState()
    val webDavWifiOnlyTags by viewModel.webDavWifiOnlyTags.collectAsState()
    val webDavResolveMatching by viewModel.webDavResolveMatching.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var sourceToDelete by remember { mutableStateOf<RegisteredSource?>(null) }
    var showWebDavEditor by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    // Local mutable list for smooth drag-and-drop animation
    val displayList = remember { mutableStateListOf<RegisteredSource>() }
    LaunchedEffect(sources) {
        displayList.clear()
        displayList.addAll(sources)
    }

    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Prevent moving YouTube or moving items past YouTube
        val ytIndex = displayList.indexOfFirst { it.kind == SourceKind.YOUTUBE }
        val maxReorderable = if (ytIndex >= 0) ytIndex - 1 else displayList.size - 1

        if (from.index <= maxReorderable && to.index <= maxReorderable) {
            val item = displayList.removeAt(from.index)
            displayList.add(to.index, item)
            viewModel.reorderSources(from.index, to.index)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sources_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val playerInsets = LocalPlayerAwareWindowInsets.current
        val glassConfig = LocalGlassEffectConfig.current
        val isGlass = glassConfig.isEnabledFor(GlassComponent.QUEUE)
        val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(playerInsets.only(WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = SettingsDimensions.ScreenBottomPadding + playerInsets.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 1. Disclaimer Card
            item(key = "disclaimer") {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGlass) {
                            if (isLight) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.06f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                    border = if (isGlass) {
                        BorderStroke(1.dp, if (isLight) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .then(
                            if (isGlass) {
                                Modifier.liquidGlass(glassConfig, RoundedCornerShape(20.dp), applyEdgeEffects = true)
                            } else Modifier
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.info),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.sources_disclaimer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // 2. Audio Sources Header
            item(key = "sources_header") {
                PreferenceGroupTitle(
                    title = stringResource(R.string.sources_list_title),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            // 3. Reorderable Sources List
            itemsIndexed(
                items = displayList,
                key = { _, item -> item.id },
            ) { index, source ->
                ReorderableItem(
                    state = reorderableState,
                    key = source.id,
                ) {
                    SourceRowItem(
                        source = source,
                        isTesting = testingSourceId == source.id,
                        dragModifier = if (source.isReorderable) Modifier.draggableHandle() else Modifier,
                        onToggle = { viewModel.toggleSource(source.id, it) },
                        onTest = { viewModel.testSource(source.id) },
                        onDelete = { sourceToDelete = source },
                        isGlass = isGlass,
                        isLight = isLight,
                        glassConfig = glassConfig,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            // 4. Add Addon Button
            item(key = "add_addon_button") {
                OutlinedButton(
                    onClick = {
                        viewModel.clearAddError()
                        showAddDialog = true
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.sources_add_addon),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 5. Track Matching Section
            item(key = "matching_header") {
                PreferenceGroupTitle(
                    title = stringResource(R.string.sources_matching_title),
                    modifier = Modifier.padding(top = 20.dp),
                )
            }

            item(key = "tolerance_slider") {
                ToleranceSliderCard(
                    toleranceSeconds = toleranceSeconds,
                    onToleranceChange = { viewModel.setTolerance(it) },
                    isGlass = isGlass,
                    isLight = isLight,
                    glassConfig = glassConfig,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }

            // 6. WebDAV Section
            item(key = "webdav_header") {
                PreferenceGroupTitle(
                    title = stringResource(R.string.webdav_title),
                    modifier = Modifier.padding(top = 20.dp),
                )
            }

            if (isWebDavConfigured) {
                item(key = "webdav_server_card") {
                    WebDavServerCard(
                        url = webDavUrl,
                        username = webDavUsername,
                        trustSelfSigned = webDavTrustSelfSigned,
                        isTesting = isTestingWebDav,
                        testResult = webDavTestResult,
                        onTest = { viewModel.testSavedWebDavConnection() },
                        onEdit = {
                            viewModel.clearWebDavTestResult()
                            showWebDavEditor = true
                        },
                        onDisconnect = { showDisconnectDialog = true },
                        isGlass = isGlass,
                        isLight = isLight,
                        glassConfig = glassConfig,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                item(key = "webdav_wifi_tags_toggle") {
                    Spacer(modifier = Modifier.height(12.dp))
                    WebDavToggleCard(
                        title = stringResource(R.string.webdav_wifi_only_tags),
                        description = stringResource(R.string.webdav_wifi_only_tags_desc),
                        checked = webDavWifiOnlyTags,
                        onCheckedChange = { viewModel.setWebDavWifiOnlyTags(it) },
                        isGlass = isGlass,
                        isLight = isLight,
                        glassConfig = glassConfig,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                item(key = "webdav_matching_toggle") {
                    Spacer(modifier = Modifier.height(12.dp))
                    WebDavToggleCard(
                        title = stringResource(R.string.webdav_use_webdav_matching),
                        description = stringResource(R.string.webdav_use_webdav_matching_desc),
                        checked = webDavResolveMatching,
                        onCheckedChange = { viewModel.setWebDavResolveMatching(it) },
                        isGlass = isGlass,
                        isLight = isLight,
                        glassConfig = glassConfig,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                item(key = "add_webdav_button") {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearWebDavTestResult()
                            showWebDavEditor = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.webdav_add_server),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    // Add Addon Dialog
    if (showAddDialog) {
        var addonUrlInput by remember { mutableStateOf("") }

        DefaultDialog(
            onDismiss = {
                if (!isAdding) showAddDialog = false
            },
            title = {
                Text(
                    text = stringResource(R.string.sources_add_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            buttons = {
                TextButton(
                    onClick = { showAddDialog = false },
                    enabled = !isAdding,
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        viewModel.addAddon(addonUrlInput) {
                            showAddDialog = false
                            addonUrlInput = ""
                        }
                    },
                    enabled = !isAdding && addonUrlInput.isNotBlank(),
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sources_testing))
                    } else {
                        Text(stringResource(R.string.sources_add_addon))
                    }
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = addonUrlInput,
                    onValueChange = { addonUrlInput = it },
                    label = { Text(stringResource(R.string.sources_add_dialog_hint)) },
                    placeholder = { Text("https://example.com/USER_TOKEN") },
                    singleLine = true,
                    enabled = !isAdding,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                if (addError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.sources_add_dialog_error, addError ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (sourceToDelete != null) {
        val target = sourceToDelete!!
        DefaultDialog(
            onDismiss = { sourceToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.sources_remove),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            buttons = {
                TextButton(onClick = { sourceToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        viewModel.removeSource(target.id)
                        sourceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.sources_remove))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.sources_remove_confirm, target.displayName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }

    // WebDAV Editor Dialog
    if (showWebDavEditor) {
        var urlInput by remember { mutableStateOf(webDavUrl) }
        var usernameInput by remember { mutableStateOf(webDavUsername) }
        var passwordInput by remember { mutableStateOf(viewModel.getWebDavPassword().orEmpty()) }
        var trustCertInput by remember { mutableStateOf(webDavTrustSelfSigned) }
        var passwordVisible by remember { mutableStateOf(false) }

        val isHttp = urlInput.trim().startsWith("http://", ignoreCase = true)

        DefaultDialog(
            onDismiss = {
                if (!isTestingWebDav) {
                    viewModel.clearWebDavTestResult()
                    showWebDavEditor = false
                }
            },
            title = {
                Text(
                    text = stringResource(
                        if (isWebDavConfigured) R.string.webdav_edit_server
                        else R.string.webdav_add_server,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        viewModel.clearWebDavTestResult()
                        showWebDavEditor = false
                    },
                    enabled = !isTestingWebDav,
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                OutlinedButton(
                    onClick = {
                        viewModel.testWebDavConnection(
                            url = urlInput,
                            username = usernameInput,
                            password = passwordInput,
                            trustSelfSigned = trustCertInput,
                        )
                    },
                    enabled = !isTestingWebDav && urlInput.isNotBlank() && usernameInput.isNotBlank(),
                ) {
                    if (isTestingWebDav) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.webdav_testing))
                    } else {
                        Text(stringResource(R.string.webdav_test))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.saveWebDav(
                            url = urlInput,
                            username = usernameInput,
                            password = passwordInput,
                            trustSelfSigned = trustCertInput,
                        )
                        viewModel.clearWebDavTestResult()
                        showWebDavEditor = false
                    },
                    enabled = !isTestingWebDav && urlInput.isNotBlank() && usernameInput.isNotBlank(),
                ) {
                    Text(stringResource(R.string.webdav_save))
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { input ->
                        val parsed = moe.rgsekai.sekaitune.webdav.WebDavConfig.extractCredentialsFromUrl(input)
                        val user = parsed.username
                        val pass = parsed.password
                        if (user != null) {
                            urlInput = parsed.cleanUrl
                            usernameInput = user
                            if (pass != null) {
                                passwordInput = pass
                            }
                        } else {
                            urlInput = input
                        }
                    },
                    label = { Text(stringResource(R.string.webdav_server_url)) },
                    placeholder = { Text(stringResource(R.string.webdav_server_url_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isHttp) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.webdav_http_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = { Text(stringResource(R.string.webdav_username)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text(stringResource(R.string.webdav_password)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = painterResource(if (passwordVisible) R.drawable.visibility else R.drawable.visibility_off),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { trustCertInput = !trustCertInput }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.webdav_trust_cert),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.webdav_trust_cert_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = trustCertInput,
                        onCheckedChange = { trustCertInput = it },
                    )
                }

                // Live test result in dialog
                webDavTestResult?.let { result ->
                    val isSuccess = result is WebDavTestResult.Success
                    val resultText = when (result) {
                        is WebDavTestResult.Success -> stringResource(R.string.webdav_connected)
                        is WebDavTestResult.AuthFailed -> stringResource(R.string.webdav_auth_failed)
                        is WebDavTestResult.NotFound -> stringResource(R.string.webdav_not_found)
                        is WebDavTestResult.SslError -> stringResource(R.string.webdav_ssl_error)
                        is WebDavTestResult.CleartextBlocked -> stringResource(R.string.webdav_cleartext_blocked)
                        is WebDavTestResult.NetworkError -> stringResource(R.string.webdav_network_error)
                        is WebDavTestResult.Other -> stringResource(R.string.webdav_error_other, result.message)
                    }
                    val containerColor = if (isSuccess) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    }
                    val textColor = if (isSuccess) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                    val iconRes = if (isSuccess) R.drawable.check else R.drawable.error

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = resultText,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }

    // WebDAV Disconnect Confirmation Dialog
    if (showDisconnectDialog) {
        var deleteDataOnDisconnect by remember { mutableStateOf(true) }
        DefaultDialog(
            onDismiss = { showDisconnectDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.webdav_disconnect_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            buttons = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        viewModel.disconnectWebDav(deleteData = deleteDataOnDisconnect)
                        showDisconnectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text(stringResource(R.string.webdav_disconnect))
                }
            },
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.webdav_disconnect_confirm_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteDataOnDisconnect = !deleteDataOnDisconnect }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = deleteDataOnDisconnect,
                        onCheckedChange = { deleteDataOnDisconnect = it },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.webdav_disconnect_delete_tracks_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceRowItem(
    source: RegisteredSource,
    isTesting: Boolean,
    dragModifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    isGlass: Boolean,
    isLight: Boolean,
    glassConfig: moe.rgsekai.sekaitune.ui.component.GlassEffectConfig,
    modifier: Modifier = Modifier,
) {
    val isYouTube = source.kind == SourceKind.YOUTUBE
    val shape = RoundedCornerShape(18.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) {
                if (isLight) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (isGlass) {
            BorderStroke(1.dp, if (isLight) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
        } else null,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isGlass) {
                    Modifier.liquidGlass(glassConfig, shape, applyEdgeEffects = true)
                } else Modifier
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Drag handle or fixed anchor
            if (source.isReorderable) {
                Icon(
                    painter = painterResource(R.drawable.drag_handle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .then(dragModifier),
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Info (Name, Subtitle, Health)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isYouTube) {
                    Text(
                        text = stringResource(R.string.sources_always_on),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (!source.baseUrl.isNullOrBlank()) {
                    Text(
                        text = source.baseUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Health status pill
                if (source.lastHealth != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    when (val health = source.lastHealth) {
                        is SourceHealth.Ok -> {
                            Text(
                                text = stringResource(R.string.sources_health_ok, health.details ?: "OK"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        is SourceHealth.Unreachable -> {
                            Text(
                                text = stringResource(R.string.sources_health_unreachable, health.message),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        is SourceHealth.Rejected -> {
                            Text(
                                text = stringResource(R.string.sources_health_rejected, health.message),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Actions
            if (isYouTube) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sources_always_on),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Test Button
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(32.dp)
                                .padding(6.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onTest) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = stringResource(R.string.sources_test),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Enable switch
                    Switch(
                        checked = source.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )

                    // Delete button
                    if (source.isDeletable) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                painter = painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.sources_remove),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToleranceSliderCard(
    toleranceSeconds: Int,
    onToleranceChange: (Int) -> Unit,
    isGlass: Boolean,
    isLight: Boolean,
    glassConfig: moe.rgsekai.sekaitune.ui.component.GlassEffectConfig,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    var localSliderValue by remember(toleranceSeconds) { mutableFloatStateOf(toleranceSeconds.toFloat()) }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) {
                if (isLight) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (isGlass) {
            BorderStroke(1.dp, if (isLight) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
        } else null,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isGlass) {
                    Modifier.liquidGlass(glassConfig, shape, applyEdgeEffects = true)
                } else Modifier
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sources_tolerance_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.sources_tolerance_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.sources_tolerance_seconds, localSliderValue.roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Slider(
                value = localSliderValue,
                onValueChange = {
                    localSliderValue = it
                },
                onValueChangeFinished = {
                    onToleranceChange(localSliderValue.roundToInt())
                },
                valueRange = 1f..10f,
                steps = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WebDavServerCard(
    url: String,
    username: String,
    trustSelfSigned: Boolean,
    isTesting: Boolean,
    testResult: WebDavTestResult?,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDisconnect: () -> Unit,
    isGlass: Boolean,
    isLight: Boolean,
    glassConfig: moe.rgsekai.sekaitune.ui.component.GlassEffectConfig,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) {
                if (isLight) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (isGlass) {
            BorderStroke(1.dp, if (isLight) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
        } else null,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isGlass) {
                    Modifier.liquidGlass(glassConfig, shape, applyEdgeEffects = true)
                } else Modifier
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.storage),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(32.dp)
                                .padding(6.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onTest) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = stringResource(R.string.webdav_test),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    IconButton(onClick = onEdit) {
                        Icon(
                            painter = painterResource(R.drawable.edit),
                            contentDescription = stringResource(R.string.webdav_edit_server),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    IconButton(onClick = onDisconnect) {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.webdav_disconnect),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Status chip/pill if tested
            testResult?.let { result ->
                Spacer(modifier = Modifier.height(8.dp))
                val isSuccess = result is WebDavTestResult.Success
                val text = when (result) {
                    is WebDavTestResult.Success -> stringResource(R.string.webdav_connected)
                    is WebDavTestResult.AuthFailed -> stringResource(R.string.webdav_auth_failed)
                    is WebDavTestResult.NotFound -> stringResource(R.string.webdav_not_found)
                    is WebDavTestResult.SslError -> stringResource(R.string.webdav_ssl_error)
                    is WebDavTestResult.CleartextBlocked -> stringResource(R.string.webdav_cleartext_blocked)
                    is WebDavTestResult.NetworkError -> stringResource(R.string.webdav_network_error)
                    is WebDavTestResult.Other -> stringResource(R.string.webdav_error_other, result.message)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WebDavToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isGlass: Boolean,
    isLight: Boolean,
    glassConfig: moe.rgsekai.sekaitune.ui.component.GlassEffectConfig,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) {
                if (isLight) Color.White.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (isGlass) {
            BorderStroke(1.dp, if (isLight) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
        } else null,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isGlass) {
                    Modifier.liquidGlass(glassConfig, shape, applyEdgeEffects = true)
                } else Modifier
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}


