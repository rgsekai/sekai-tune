/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.utils.appBarScrollBehavior
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.utils.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
    onClearUpdateBadge: () -> Unit = {},
) {
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val listState = rememberLazyListState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val isSearching = searchQuery.isNotBlank()

    val searchIndex = remember(context) { SettingsSearchRepository.buildSearchIndex(context) }
    val searchResults = remember(searchQuery, searchIndex) { SettingsSearchRepository.search(searchQuery, searchIndex) }

    // SharedPreferences setup for saving the audio format choice (MP3 by default)
    val sharedPrefs = remember {
        context.getSharedPreferences("sekai_tune_prefs", Context.MODE_PRIVATE)
    }
    var selectedFormat by remember {
        mutableStateOf(sharedPrefs.getString("audio_format", "mp3") ?: "mp3")
    }

    val storagePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    val notificationPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    var isStorageGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                storagePermission
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var isNotificationGranted by remember {
        mutableStateOf(
            notificationPermission == null ||
                    ContextCompat.checkSelfPermission(
                        context,
                        notificationPermission
                    ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            isStorageGranted = result[storagePermission] == true || isStorageGranted
            if (notificationPermission != null) {
                isNotificationGranted =
                    result[notificationPermission] == true || isNotificationGranted
            }
        }

    val scrollBehavior = appBarScrollBehavior()
    val shouldShowPermissionHint = !isStorageGranted || !isNotificationGranted
    val hasUpdate =
        BuildConfig.UPDATER_AVAILABLE &&
                Updater.isUpdateAvailable(latestVersionName, BuildConfig.VERSION_NAME)
    var isUpdateDismissed by remember { mutableStateOf(false) }
    val settingsGroups = buildSettingsGroups(navController, isAndroid12OrLater, hasUpdate, context)
    val settingsItems =
        remember(settingsGroups) {
            settingsGroups.flatMap { it.items }
        }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { },
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
        ) {
            item(key = "search_bar", contentType = "settings_search_bar") {
                SettingsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier =
                        Modifier
                            .padding(horizontal = 26.dp)
                            .padding(bottom = 10.dp),
                )
            }

            if (isSearching) {
                if (searchResults.isEmpty()) {
                    item(key = "empty_search", contentType = "settings_empty_search") {
                        SettingsSearchEmptyState(query = searchQuery)
                    }
                } else {
                    itemsIndexed(
                        items = searchResults,
                        key = { _, item -> item.id },
                        contentType = { _, _ -> "settings_search_result" },
                    ) { index, entry ->
                        SettingsSearchResultItem(
                            entry = entry,
                            index = index,
                            count = searchResults.size,
                            onClick = { navController.navigate(entry.route) },
                            modifier = Modifier.padding(horizontal = 26.dp),
                        )
                    }
                }
            } else {
                if (hasUpdate && !isUpdateDismissed) {
                    item(key = "update", contentType = "settings_banner") {
                        SettingsUpdateBanner(
                            latestVersion = latestVersionName,
                            onClick = { navController.navigate("settings/update") },
                            onDismiss = { isUpdateDismissed = true },
                            modifier =
                                Modifier
                                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                    .padding(bottom = SettingsDimensions.SectionSpacing),
                        )
                    }
                }

                if (shouldShowPermissionHint) {
                    item(key = "permission", contentType = "settings_banner") {
                        SettingsPermissionBanner(
                            onRequestPermission = {
                                val toRequest =
                                    buildList {
                                        if (!isStorageGranted) add(storagePermission)
                                        if (!isNotificationGranted && notificationPermission != null) {
                                            add(notificationPermission)
                                        }
                                    }
                                if (toRequest.isNotEmpty()) {
                                    permissionLauncher.launch(toRequest.toTypedArray())
                                }
                            },
                            modifier =
                                Modifier
                                    .padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                                    .padding(bottom = SettingsDimensions.SectionSpacing),
                        )
                    }
                }

                // 2. THE NATIVE SETTINGS LIST (Make sure there is only ONE of these!)
                itemsIndexed(
                    items = settingsItems,
                    key = { _, item -> item.key },
                    contentType = { _, _ -> "settings_segment" },
                ) { index, settingsItem ->
                    SettingsSegmentedItem(
                        item = settingsItem,
                        index = index,
                        count = settingsItems.size,
                        modifier = Modifier.padding(horizontal = 26.dp),
                    )
                }
            }
        }
    }
}
