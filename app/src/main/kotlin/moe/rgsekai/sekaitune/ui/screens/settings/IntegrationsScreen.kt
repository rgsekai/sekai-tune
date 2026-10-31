/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.http.SslError
import android.net.Uri
import android.os.Message
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.ShowSpotifyFollowArtistKey
import moe.rgsekai.sekaitune.constants.ShowSpotifyPlaylistsKey
import moe.rgsekai.sekaitune.spotify.SpotifyAccountUiState
import moe.rgsekai.sekaitune.spotify.SpotifyAccountViewModel
import moe.rgsekai.sekaitune.spotify.SpotifyAuth
import moe.rgsekai.sekaitune.ui.component.DefaultDialog
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.utils.LocalIsTv
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.ui.utils.tvFocusOutline
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.utils.resetAuthWebViewSession

private const val SpotifyMobileUserAgent =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

private fun cleanSpotifyUserAgent(defaultUa: String, isTv: Boolean): String =
    if (isTv) {
        defaultUa
            .replace("; wv", "")
            .replace(Regex("Version/[0-9.]+ "), "")
    } else {
        SpotifyMobileUserAgent
    }

@Composable
fun IntegrationsScreen(
    navController: NavController,
    spotifyAccountViewModel: SpotifyAccountViewModel = hiltViewModel(),
) {
    val isTv = LocalIsTv.current
    val playerInsets = LocalPlayerAwareWindowInsets.current
    val (showSpotifyPlaylists, onShowSpotifyPlaylistsChange) = rememberPreference(
        key = ShowSpotifyPlaylistsKey,
        defaultValue = false,
    )
    val (showSpotifyFollowArtist, onShowSpotifyFollowArtistChange) = rememberPreference(
        key = ShowSpotifyFollowArtistKey,
        defaultValue = false,
    )
    val spotifyState by spotifyAccountViewModel.uiState.collectAsStateWithLifecycle()
    var showSpotifyLogin by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(spotifyState.isAuthenticated) {
        if (spotifyState.isAuthenticated) {
            showSpotifyLogin = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.integrations)) },
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        playerInsets.only(
                            WindowInsetsSides.Horizontal,
                        ),
                    ),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = SettingsDimensions.ScreenBottomPadding + playerInsets.asPaddingValues().calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ExpressiveSectionCard(title = "Spotify") {
                    if (!spotifyState.isAuthenticated) {
                        ExpressiveActionRow(
                            icon = painterResource(R.drawable.spotify_icon),
                            title = stringResource(R.string.spotify_connect),
                            subtitle = stringResource(R.string.spotify_not_connected),
                            accent = Color(0xFF1DB954),
                            onClick = {
                                if (!spotifyState.isLoading) {
                                    showSpotifyLogin = true
                                }
                            },
                        )
                    } else {
                        ExpressiveSpotifyAccountRow(
                            state = spotifyState,
                        )

                        ExpressiveDivider()

                        ExpressiveSwitchRow(
                            icon = painterResource(R.drawable.spotify_icon),
                            title = stringResource(R.string.spotify_show_playlist),
                            subtitle = stringResource(R.string.spotify_show_playlist_desc),
                            checked = showSpotifyPlaylists,
                            onCheckedChange = onShowSpotifyPlaylistsChange,
                        )

                        if (!isTv) {
                            ExpressiveDivider()

                            ExpressiveSwitchRow(
                                icon = painterResource(R.drawable.spotify_icon),
                                title = stringResource(R.string.spotify_follow_artist),
                                subtitle = stringResource(R.string.spotify_follow_artist_desc),
                                checked = showSpotifyFollowArtist,
                                onCheckedChange = onShowSpotifyFollowArtistChange,
                            )
                        }

                        ExpressiveDivider()

                        ExpressiveActionRow(
                            icon = painterResource(R.drawable.sync),
                            title = stringResource(R.string.spotify_reload_playlist),
                            subtitle = stringResource(R.string.spotify_reload_playlist_desc),
                            onClick = {
                                if (!spotifyState.isLoading) {
                                    spotifyAccountViewModel.reloadPlaylists()
                                }
                            },
                        )

                        ExpressiveDivider()

                        ExpressiveActionRow(
                            icon = painterResource(R.drawable.logout),
                            title = stringResource(R.string.action_logout),
                            accent = MaterialTheme.colorScheme.error,
                            onClick = {
                                if (!spotifyState.isLoading) {
                                    spotifyAccountViewModel.logout()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showSpotifyLogin) {
        SpotifyLoginSheet(
            onDismiss = { showSpotifyLogin = false },
            onCookiesCaptured = { spDc, spKey ->
                showSpotifyLogin = false
                spotifyAccountViewModel.connectWithCookies(spDc = spDc, spKey = spKey)
            },
        )
    }

    spotifyState.errorMessage?.let { error ->
        SpotifyErrorDialog(
            message = error,
            onDismiss = spotifyAccountViewModel::dismissError,
        )
    }
}

@Composable
private fun ExpressiveSpotifyAccountRow(
    state: SpotifyAccountUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!state.accountAvatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = state.accountAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.spotify_icon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF1DB954),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text =
                    if (state.accountName.isNotBlank()) {
                        stringResource(R.string.spotify_connected_as, state.accountName)
                    } else {
                        stringResource(R.string.spotify_account)
                    },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text =
                    when {
                        state.isLoading -> stringResource(R.string.spotify_loading_library)
                        state.playlistCount > 0 -> stringResource(R.string.spotify_available_count, state.playlistCount)
                        else -> stringResource(R.string.spotify_no_sources)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AnimatedVisibility(visible = state.isLoading) {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color(0xFF1DB954),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifyLoginSheet(
    onDismiss: () -> Unit,
    onCookiesCaptured: (spDc: String, spKey: String) -> Unit,
) {
    val isTv = LocalIsTv.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val webViewFocusRequester = remember { FocusRequester() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var captured by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroySpotifyLoginWebView()
            mainWebView?.takeIf { it !== webView }?.destroySpotifyLoginWebView()
            webView = null
            mainWebView = null
        }
    }

    BackHandler(enabled = webView != null) {
        val activeWebView = webView
        val rootWebView = mainWebView
        when {
            activeWebView?.canGoBack() == true -> {
                activeWebView.goBack()
            }

            activeWebView != null && rootWebView != null && activeWebView !== rootWebView -> {
                activeWebView.destroySpotifyLoginWebView()
                webView = rootWebView
            }

            else -> {
                onDismiss()
            }
        }
    }

    LaunchedEffect(isTv) {
        if (isTv) {
            webViewFocusRequester.requestFocus()
        }
    }

    GlassModalBottomSheet(
        modifier =
            if (isTv) {
                Modifier
                    .fillMaxHeight(0.92f)
                    .widthIn(min = 560.dp, max = 720.dp)
            } else {
                Modifier.fillMaxHeight()
            },
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = if (isTv) 24.dp else 20.dp)
                    .padding(bottom = if (isTv) 24.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.spotify_login_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.spotify_waiting_for_login),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isTv) {
                    IconButton(
                        onClick = onDismiss,
                        onLongClick = onDismiss,
                        modifier = Modifier.tvFocusOutline(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }
            }

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.large)
                        .then(
                            if (isTv) {
                                Modifier
                                    .focusRequester(webViewFocusRequester)
                                    .focusable()
                            } else {
                                Modifier
                            },
                        ),
                factory = { context ->
                    val container = FrameLayout(context)
                    val spotifyWebView =
                        SpotifyLoginWebView(context, isTv = isTv).apply {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)
                            configureSpotifyLoginWebView(isTv = isTv)

                            fun captureCookies(url: String?): Boolean {
                                if (captured) return true
                                val cookies = readSpotifyCookies(cookieManager, url)
                                val spDc = cookies["sp_dc"].orEmpty()
                                if (spDc.isBlank()) return false
                                captured = true
                                cookieManager.flush()
                                onCookiesCaptured(spDc, cookies["sp_key"].orEmpty())
                                return true
                            }

                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean =
                                        shouldOverrideSpotifyLoginUrl(
                                            view = view,
                                            url = request.url?.toString(),
                                            captureCookies = { url -> captureCookies(url) },
                                        )

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView,
                                        url: String?,
                                    ): Boolean =
                                        shouldOverrideSpotifyLoginUrl(
                                            view = view,
                                            url = url,
                                            captureCookies = { targetUrl -> captureCookies(targetUrl) },
                                        )

                                    override fun onPageStarted(
                                        view: WebView,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?,
                                    ) {
                                        captureCookies(url)
                                    }

                                    @SuppressLint("WebViewClientOnReceivedSslError")
                                    override fun onReceivedSslError(
                                        view: WebView,
                                        handler: SslErrorHandler,
                                        error: SslError,
                                    ) {
                                        handler.proceed()
                                    }

                                    override fun onPageFinished(
                                        view: WebView,
                                        url: String?,
                                    ) {
                                        captureCookies(url)
                                        if (isTv) {
                                            view.requestFocus()
                                            injectSpotifyTvDpadNavigation(view)
                                            view.postDelayed({ injectSpotifyTvDpadNavigation(view) }, 600)
                                            view.postDelayed({ injectSpotifyTvDpadNavigation(view) }, 1500)
                                        }
                                    }
                                }
                            webChromeClient =
                                SpotifyLoginWebChromeClient(
                                    container = container,
                                    parentWebView = this,
                                    isTv = isTv,
                                    captureCookies = { url -> captureCookies(url) },
                                    onActiveWebViewChanged = { activeWebView -> webView = activeWebView },
                                )
                            webView = this
                            mainWebView = this
                            resetAuthWebViewSession(context, this, clearCookies = false) {
                                loadUrl(SpotifyAuth.LOGIN_URL)
                            }
                        }
                    container.addView(
                        spotifyWebView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    container
                },
                update = {
                    webView = webView ?: mainWebView
                },
            )
        }
    }
}

private class SpotifyLoginWebView(
    context: Context,
    private val isTv: Boolean,
) : WebView(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isTv) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            evaluateJavascript("window.__tvDpadNext && window.__tvDpadNext()", null)
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            evaluateJavascript("window.__tvDpadPrev && window.__tvDpadPrev()", null)
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            evaluateJavascript(
                                """
                                (function() {
                                    var f = (window.__tvDpadGetFocusable && window.__tvDpadGetFocusable()) || [];
                                    if (f.length === 0) return 'none';
                                    var idx = window.__tvFocusIdx;
                                    if (typeof idx !== 'number' || idx < 0 || idx >= f.length) {
                                        idx = 0;
                                        if (typeof window.__tvSetFocus === 'function') {
                                            window.__tvSetFocus(0);
                                        }
                                    }
                                    var target = f[idx];
                                    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') {
                                        target.focus();
                                        target.click();
                                        return 'input';
                                    } else {
                                        target.click();
                                        return 'clicked';
                                    }
                                })()
                                """.trimIndent(),
                            ) { result ->
                                if (result?.contains("input") == true) {
                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                                }
                            }
                        }
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

private fun WebView.destroySpotifyLoginWebView() {
    stopLoading()
    loadUrl("about:blank")
    (parent as? ViewGroup)?.removeView(this)
    destroy()
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSpotifyLoginWebView(isTv: Boolean = false) {
    WebView.setWebContentsDebuggingEnabled(true)
    setBackgroundColor(android.graphics.Color.parseColor("#121212"))
    isFocusable = true
    isFocusableInTouchMode = true
    if (isTv) {
        requestFocus()
    }
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        if (isTv) {
            textZoom = 100
        }
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        userAgentString = cleanSpotifyUserAgent(userAgentString, isTv = isTv)
    }
}

private fun injectSpotifyTvDpadNavigation(view: WebView) {
    val js =
        "javascript:void((function(){" +
            "try{" +
            "var id='tv-spotify-focus-style';" +
            "var existing = document.getElementById(id);" +
            "if(existing){existing.remove();}" +
            "var s=document.createElement('style');" +
            "s.id=id;" +
            "s.textContent='body { background-color: #121212 !important; } .tv-focused, input:focus, button:focus, a:focus { outline: 3px solid #1DB954 !important; outline-offset: 3px !important; box-shadow: 0 0 14px rgba(29, 185, 84, 0.9) !important; border-radius: 6px !important; } img, svg { max-width: 100% !important; max-height: 80px !important; }';" +
            "(document.head||document.documentElement).appendChild(s);" +
            "window.__tvDpadGetFocusable=function(){" +
            "return Array.from(document.querySelectorAll('input:not([type=\"hidden\"]):not([disabled]), button:not([disabled]), a[href]')).filter(function(el){" +
            "if(el.getAttribute('tabindex')==='-1')return false;" +
            "var r=el.getBoundingClientRect();" +
            "return r.width>0 && r.height>0 && window.getComputedStyle(el).visibility!=='hidden' && window.getComputedStyle(el).display!=='none';" +
            "});" +
            "};" +
            "if(typeof window.__tvFocusIdx !== 'number'){window.__tvFocusIdx = -1;}" +
            "window.__tvSetFocus=function(newIdx){" +
            "var f=window.__tvDpadGetFocusable();" +
            "if(!f||f.length===0)return;" +
            "var old=document.querySelector('.tv-focused');" +
            "if(old){" +
            "old.classList.remove('tv-focused');" +
            "if(old.tagName==='INPUT'||old.tagName==='TEXTAREA'){old.blur();}" +
            "}" +
            "window.__tvFocusIdx=(newIdx+f.length)%f.length;" +
            "var target=f[window.__tvFocusIdx];" +
            "target.classList.add('tv-focused');" +
            "if(typeof target.scrollIntoView==='function'){target.scrollIntoView({behavior:'smooth',block:'center'});}" +
            "if(target.tagName!=='INPUT'&&target.tagName!=='TEXTAREA'){target.focus();}" +
            "};" +
            "window.__tvDpadNext=function(){window.__tvSetFocus(window.__tvFocusIdx+1);};" +
            "window.__tvDpadPrev=function(){window.__tvSetFocus(window.__tvFocusIdx-1);};" +
            "if(document.activeElement&&(document.activeElement.tagName==='INPUT'||document.activeElement.tagName==='TEXTAREA')){" +
            "document.activeElement.blur();" +
            "}" +
            "}catch(e){}" +
            "})())"
    view.loadUrl(js)
}

private class SpotifyLoginWebChromeClient(
    private val container: FrameLayout,
    private val parentWebView: WebView,
    private val isTv: Boolean,
    private val captureCookies: (String?) -> Boolean,
    private val onActiveWebViewChanged: (WebView) -> Unit,
) : WebChromeClient() {
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        closePopupWebViews()

        val popupWebView =
            SpotifyLoginWebView(view.context, isTv = isTv).apply {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                configureSpotifyLoginWebView(isTv = isTv)
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean =
                            shouldOverrideSpotifyLoginUrl(
                                view = view,
                                url = request.url?.toString(),
                                captureCookies = captureCookies,
                            )

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            url: String?,
                        ): Boolean =
                            shouldOverrideSpotifyLoginUrl(
                                view = view,
                                url = url,
                                captureCookies = captureCookies,
                            )

                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            captureCookies(url)
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(
                            view: WebView,
                            handler: SslErrorHandler,
                            error: SslError,
                        ) {
                            handler.proceed()
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            captureCookies(url)
                            if (isTv) {
                                view.requestFocus()
                                injectSpotifyTvDpadNavigation(view)
                                view.postDelayed({ injectSpotifyTvDpadNavigation(view) }, 600)
                            }
                        }
                    }
            }

        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        container.addView(
            popupWebView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        popupWebView.bringToFront()
        popupWebView.requestFocus()
        onActiveWebViewChanged(popupWebView)
        transport.webView = popupWebView
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView) {
        window.destroySpotifyLoginWebView()
        onActiveWebViewChanged(parentWebView)
    }

    private fun closePopupWebViews() {
        for (index in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(index) as? WebView ?: continue
            if (child !== parentWebView) {
                child.destroySpotifyLoginWebView()
            }
        }
        onActiveWebViewChanged(parentWebView)
    }
}

private fun shouldOverrideSpotifyLoginUrl(
    view: WebView,
    url: String?,
    captureCookies: (String?) -> Boolean,
): Boolean {
    if (captureCookies(url)) return true

    val targetUrl = url?.takeIf(String::isNotBlank) ?: return false
    if (targetUrl.isWebViewLoadableUrl()) return false

    targetUrl.intentBrowserFallbackUrl()?.let { fallbackUrl -> view.loadUrl(fallbackUrl) }
    return true
}

private fun String.isWebViewLoadableUrl(): Boolean {
    val scheme = runCatching { Uri.parse(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" ||
        scheme == "https" ||
        scheme == "javascript" ||
        scheme == "data" ||
        scheme == "blob"
}

private fun String.intentBrowserFallbackUrl(): String? =
    runCatching { Intent.parseUri(this, Intent.URI_INTENT_SCHEME) }
        .getOrNull()
        ?.getStringExtra("browser_fallback_url")
        ?.takeIf { it.isWebViewLoadableUrl() }

private fun readSpotifyCookies(
    cookieManager: CookieManager,
    currentUrl: String?,
): Map<String, String> {
    val urls =
        linkedSetOf(
            "https://open.spotify.com",
            "https://accounts.spotify.com",
            "https://spotify.com",
        )
    currentUrl?.toSpotifyCookieOrigin()?.let(urls::add)
    val cookies = linkedMapOf<String, String>()
    cookieManager.flush()
    urls.forEach { url ->
        cookieManager
            .getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@forEach
                val key = part.substring(0, separator).trim()
                val value = part.substring(separator + 1).trim()
                if (key.isNotBlank()) {
                    cookies[key] = value
                }
            }
    }
    return cookies
}

private fun String.toSpotifyCookieOrigin(): String? {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host != "spotify.com" && !host.endsWith(".spotify.com")) return null
    val scheme =
        uri.scheme
            ?.takeIf { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) }
            ?: "https"
    return "$scheme://$host"
}

@Composable
private fun SpotifyErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(stringResource(R.string.import_failed)) },
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.ok))
            }
        },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
