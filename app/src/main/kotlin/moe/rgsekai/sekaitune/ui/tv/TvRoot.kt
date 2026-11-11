/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.tv

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import moe.rgsekai.sekaitune.BuildConfig
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.ui.screens.LOGIN_ROUTE
import moe.rgsekai.sekaitune.ui.screens.Screens
import moe.rgsekai.sekaitune.ui.screens.navigationBuilder
import moe.rgsekai.sekaitune.ui.screens.search.OnlineSearchResultArgument
import moe.rgsekai.sekaitune.ui.screens.search.OnlineSearchResultRoute
import moe.rgsekai.sekaitune.ui.screens.search.OnlineSearchResultRoutePrefix
import moe.rgsekai.sekaitune.ui.tv.components.TvTab
import moe.rgsekai.sekaitune.ui.tv.components.TvTopBar
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import moe.rgsekai.sekaitune.constants.LyricsTextSizeKey
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.ui.tv.screens.TvAlbumScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvArtistScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvAutoPlaylistScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvExploreScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvHomeScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvLibraryScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvLocalPlaylistScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvNowPlayingScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvOnlinePlaylistScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvOnlineSearchResultScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvSearchScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvSettingsCategory
import moe.rgsekai.sekaitune.ui.tv.screens.TvSettingsScreen
import moe.rgsekai.sekaitune.ui.tv.screens.TvSpotifyPlaylistScreen
import moe.rgsekai.sekaitune.ui.utils.safeRequestFocus
import moe.rgsekai.sekaitune.viewmodels.HomeViewModel

const val TV_HOME_ROUTE = "tv_home"

/**
 * Root container for the TV UI.
 * Reuses the application's existing [NavHostController] and [PlayerConnection].
 * Displays the top pill bar and routes TV/placeholder screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvRoot(
    navController: NavHostController,
    playerConnection: PlayerConnection?,
    modifier: Modifier = Modifier,
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val topBarFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    val topAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: TV_HOME_ROUTE

    // Request initial focus on the top bar
    LaunchedEffect(Unit) {
        topBarFocusRequester.safeRequestFocus()
    }

    var lastTopTab by rememberSaveable { mutableStateOf(TvTab.HOME) }

    val topTabForCurrentRoute = when {
        currentRoute == TV_HOME_ROUTE || currentRoute == Screens.Home.route -> TvTab.HOME
        currentRoute == Screens.NewRelease.route || currentRoute == Screens.MoodAndGenres.route -> TvTab.EXPLORE
        currentRoute == Screens.Library.route -> TvTab.LIBRARY
        currentRoute == "ambient_mode" -> TvTab.NOW_PLAYING
        currentRoute == Screens.Search.route || currentRoute.startsWith(OnlineSearchResultRoutePrefix) || currentRoute.startsWith("online_search") -> TvTab.SEARCH
        currentRoute == "settings" || currentRoute.startsWith("settings/") || currentRoute == LOGIN_ROUTE || currentRoute == "account" -> TvTab.SETTINGS
        else -> null
    }

    LaunchedEffect(topTabForCurrentRoute) {
        if (topTabForCurrentRoute != null) {
            lastTopTab = topTabForCurrentRoute
        }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        if (prefs[LyricsTextSizeKey] == null) {
            context.dataStore.edit { it[LyricsTextSizeKey] = 32f }
        }
    }

    val selectedTab = topTabForCurrentRoute ?: lastTopTab
    val isLoginRoute = currentRoute == LOGIN_ROUTE || currentRoute.startsWith("$LOGIN_ROUTE?") || currentRoute.contains("login")
    val isNowPlayingRoute = currentRoute == "ambient_mode"

    var isTopBarFocused by remember { mutableStateOf(false) }
    var lastRemoteActivityTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isTopBarAutoHidden by remember { mutableStateOf(false) }

    // Inactivity timer: When focus is on the Now Playing screen or its elements,
    // after 10 seconds of no remote activity, hide the top TV bar upside.
    // If any remote activity happens, the top bar appears immediately.
    LaunchedEffect(isNowPlayingRoute, isTopBarFocused, lastRemoteActivityTime) {
        if (isNowPlayingRoute && !isTopBarFocused) {
            isTopBarAutoHidden = false
            delay(10_000L)
            isTopBarAutoHidden = true
        } else {
            isTopBarAutoHidden = false
        }
    }

    val isTopBarVisible = !isTopBarAutoHidden

    val topBarOffsetFraction by animateFloatAsState(
        targetValue = if (isTopBarVisible) 0f else -1f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing,
        ),
        label = "tvTopBarSlide",
    )
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isTopBarVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 350,
            easing = FastOutSlowInEasing,
        ),
        label = "tvTopBarAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    lastRemoteActivityTime = System.currentTimeMillis()
                    if (isTopBarAutoHidden) {
                        isTopBarAutoHidden = false
                    }
                }
                false
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                        lastRemoteActivityTime = System.currentTimeMillis()
                        if (isTopBarAutoHidden) {
                            isTopBarAutoHidden = false
                        }
                    }
                }
            },
    ) {

        // TV Content Area: hosts NavHost wrapped with TvDpadLayer for generic D-pad navigation
        TvDpadLayer(
            navController = navController,
            currentRoute = currentRoute,
            upFocusRequester = topBarFocusRequester,
            contentFocusRequester = contentFocusRequester,
            modifier = Modifier.fillMaxSize(),
        ) {
            NavHost(
                navController = navController,
                startDestination = TV_HOME_ROUTE,
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(150)) },
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(TV_HOME_ROUTE) {
                    TvHomeScreen(
                        homeViewModel = homeViewModel,
                        playerConnection = playerConnection,
                        navController = navController,
                        firstShelfFocusRequester = contentFocusRequester,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // Reuse existing application navigation graph for placeholder screens and sub-screens
                navigationBuilder(
                    navController = navController,
                    scrollBehavior = topAppBarScrollBehavior,
                    latestVersionName = { BuildConfig.VERSION_NAME },
                )

                // TV-optimized Explore / New Releases screen
                composable(Screens.NewRelease.route) {
                    TvExploreScreen(
                        navController = navController,
                        firstShelfFocusRequester = contentFocusRequester,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Library screen (Playlists & Spotify Playlists)
                composable(Screens.Library.route) {
                    TvLibraryScreen(
                        navController = navController,
                        firstShelfFocusRequester = contentFocusRequester,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Now Playing screen
                composable("ambient_mode") {
                    TvNowPlayingScreen(
                        navController = navController,
                        firstFocusRequester = contentFocusRequester,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Search screen (search field only)
                composable(Screens.Search.route) {
                    TvSearchScreen(
                        navController = navController,
                        firstFocusRequester = contentFocusRequester,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Search Results screen (songs only, no chips, no section header, no dead space)
                composable(
                    route = OnlineSearchResultRoute,
                    arguments = listOf(
                        navArgument(OnlineSearchResultArgument) {
                            type = NavType.StringType
                        },
                    ),
                ) {
                    TvOnlineSearchResultScreen(
                        navController = navController,
                        firstFocusRequester = contentFocusRequester,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Artist screen
                composable(
                    route = "artist/{artistId}",
                    arguments = listOf(
                        navArgument("artistId") {
                            type = NavType.StringType
                        },
                    ),
                ) {
                    TvArtistScreen(
                        navController = navController,
                        playerConnection = playerConnection,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Album screen
                composable(
                    route = "album/{albumId}",
                    arguments = listOf(
                        navArgument("albumId") {
                            type = NavType.StringType
                        },
                    ),
                ) {
                    TvAlbumScreen(
                        navController = navController,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Online Playlist screen
                composable(
                    route = "online_playlist/{playlistId}",
                    arguments = listOf(
                        navArgument("playlistId") {
                            type = NavType.StringType
                        },
                    ),
                ) {
                    TvOnlinePlaylistScreen(
                        navController = navController,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Local Playlist screen
                composable(
                    route = "local_playlist/{playlistId}",
                    arguments = listOf(
                        navArgument("playlistId") {
                            type = NavType.StringType
                        },
                    ),
                ) {
                    TvLocalPlaylistScreen(
                        navController = navController,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Auto Playlist screen (liked, downloaded, uploaded)
                composable(
                    route = "auto_playlist/{playlist}?tab={tab}",
                    arguments = listOf(
                        navArgument("playlist") {
                            type = NavType.StringType
                        },
                        navArgument("tab") {
                            type = NavType.StringType
                            defaultValue = "downloaded"
                        },
                    ),
                ) {
                    TvAutoPlaylistScreen(
                        navController = navController,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Spotify Playlist screen
                composable(
                    route = "spotify_playlist/{playlistId}",
                    arguments = listOf(
                        navArgument("playlistId") {
                            type = NavType.StringType
                        },
                    ),
                ) {
                    TvSpotifyPlaylistScreen(
                        navController = navController,
                        upFocusRequester = topBarFocusRequester,
                    )
                }

                // TV-optimized Settings screen and sub-routes
                composable("settings") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("stats") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/account") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/appearance") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/canvas") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.CANVAS,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/player") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/lyrics") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.LYRICS,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/content") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.CONTENT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/privacy") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/storage") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.STORAGE,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/backup_restore") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/integrations") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.INTEGRATIONS,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/internet") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/advanced") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/about") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ABOUT,
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/appearance/liquid_glass") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        activeSubRoute = "settings/appearance/liquid_glass",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/liquid_glass") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        activeSubRoute = "settings/appearance/liquid_glass",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/appearance/ambient_mode") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        activeSubRoute = "settings/appearance/ambient_mode",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/appearance/icon") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        activeSubRoute = "settings/appearance/icon",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/appearance/aod_customized") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        activeSubRoute = "settings/appearance/aod_customized",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/appearance/lyrics_animations") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        activeSubRoute = "settings/appearance/lyrics_animations",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/player/chiper") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ACCOUNT,
                        activeSubRoute = "settings/player/chiper",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/music_together") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.INTEGRATIONS,
                        activeSubRoute = "settings/music_together",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/misc") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ABOUT,
                        activeSubRoute = "settings/misc",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/logcat") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ABOUT,
                        activeSubRoute = "settings/logcat",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/po_token") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.CONTENT,
                        activeSubRoute = "settings/po_token",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                composable("settings/changelog") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.UPDATE,
                        activeSubRoute = "settings/changelog",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }

                if (BuildConfig.UPDATER_AVAILABLE) {
                    composable("settings/update") {
                        TvSettingsScreen(
                            navController = navController,
                            initialCategory = TvSettingsCategory.UPDATE,
                            upFocusRequester = topBarFocusRequester,
                            contentFocusRequester = contentFocusRequester,
                        )
                    }
                }

                composable("settings/support") {
                    TvSettingsScreen(
                        navController = navController,
                        initialCategory = TvSettingsCategory.ABOUT,
                        activeSubRoute = "settings/support",
                        upFocusRequester = topBarFocusRequester,
                        contentFocusRequester = contentFocusRequester,
                    )
                }
            }
        }

        if (!isLoginRoute) {
            // TV Top Navigation Bar (floats on top of content)
            TvTopBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    when (tab) {
                        TvTab.HOME -> {
                            if (currentRoute != TV_HOME_ROUTE) {
                                navController.navigate(TV_HOME_ROUTE) {
                                    popUpTo(TV_HOME_ROUTE) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                        TvTab.EXPLORE -> {
                            navController.navigate(Screens.NewRelease.route) {
                                launchSingleTop = true
                            }
                        }
                        TvTab.LIBRARY -> {
                            navController.navigate(Screens.Library.route) {
                                launchSingleTop = true
                            }
                        }
                        TvTab.NOW_PLAYING -> {
                            navController.navigate("ambient_mode") {
                                launchSingleTop = true
                            }
                        }
                        TvTab.SEARCH -> {
                            navController.navigate(Screens.Search.route) {
                                launchSingleTop = true
                            }
                        }
                        TvTab.SETTINGS -> {
                            navController.navigate("settings") {
                                launchSingleTop = true
                            }
                        }
                    }
                },
                selectedTabFocusRequester = topBarFocusRequester,
                downFocusRequester = contentFocusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .onFocusChanged { focusState ->
                        isTopBarFocused = focusState.hasFocus
                    }
                    .graphicsLayer {
                        translationY = topBarOffsetFraction * (size.height + 40.dp.toPx())
                        alpha = topBarAlpha
                    },
            )
        }
    }
}

/**
 * Focus handoff helper connecting the TV top bar and content area:
 * - Directs focus to [contentFocusRequester] or allows Down focus movement into the container.
 * - Sets focusProperties.up to [upFocusRequester] (the selected top bar tab).
 * - On unconsumed Up key event at the top of content, returns focus safely to [upFocusRequester].
 * - Never crashes if the content screen has nothing focusable.
 */
fun Modifier.tvContentFocusHandoff(
    upFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester? = null,
): Modifier {
    return this
        .then(
            if (contentFocusRequester != null) {
                Modifier.focusRequester(contentFocusRequester)
            } else {
                Modifier
            },
        )
        .focusProperties {
            up = upFocusRequester
        }
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                upFocusRequester.safeRequestFocus()
            } else {
                false
            }
        }
}
