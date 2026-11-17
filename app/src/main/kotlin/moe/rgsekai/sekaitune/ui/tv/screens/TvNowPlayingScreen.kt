/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.tv.screens

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalDatabase
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.canvas.CanvasRequestPolicy
import moe.rgsekai.sekaitune.canvas.CanvasSource
import moe.rgsekai.sekaitune.canvas.ProceduralCanvasStyle
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import moe.rgsekai.sekaitune.constants.AmbientArtScaleKey
import moe.rgsekai.sekaitune.constants.AmbientCanvasEnabledKey
import moe.rgsekai.sekaitune.constants.AmbientShowArtistKey
import moe.rgsekai.sekaitune.constants.AmbientShowLyricsKey
import moe.rgsekai.sekaitune.constants.AmbientShowProgressBarKey
import moe.rgsekai.sekaitune.constants.AmbientShowTitleKey
import moe.rgsekai.sekaitune.constants.CanvasAudioReactiveKey
import moe.rgsekai.sekaitune.constants.CanvasFallbackKey
import moe.rgsekai.sekaitune.constants.CanvasMeteredKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralFallbackKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralStyleKey
import moe.rgsekai.sekaitune.constants.CanvasSourceKey
import moe.rgsekai.sekaitune.constants.LyricsMode
import moe.rgsekai.sekaitune.constants.LyricsModeKey
import moe.rgsekai.sekaitune.constants.SpotifySpDcKey
import moe.rgsekai.sekaitune.constants.SyncLikesToSpotifyKey
import moe.rgsekai.sekaitune.di.LyricsHelperEntryPoint
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.lyrics.LyricsFetchManager
import moe.rgsekai.sekaitune.constants.LyricsMotionStyle
import moe.rgsekai.sekaitune.ui.component.LyricsEnhanced
import moe.rgsekai.sekaitune.ui.component.LyricsStyleView
import moe.rgsekai.sekaitune.ui.component.LyricsV2
import moe.rgsekai.sekaitune.ui.component.PlayerSliderTrack
import moe.rgsekai.sekaitune.ui.player.CanvasArtworkPlayer
import moe.rgsekai.sekaitune.ui.player.CanvasRenderMode
import moe.rgsekai.sekaitune.ui.player.rememberOfflineArtworkImageRequest
import moe.rgsekai.sekaitune.ui.player.resolveCanvasArtworkForPlayback
import moe.rgsekai.sekaitune.ui.player.resolveProceduralRenderMode
import moe.rgsekai.sekaitune.ui.screens.ambient.AmbientGlowBackground
import moe.rgsekai.sekaitune.ui.utils.highRes
import moe.rgsekai.sekaitune.ui.utils.safeRequestFocus
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import timber.log.Timber

/**
 * TV Now Playing screen view modes:
 * - SPLIT: Default view (Album Art + Controls on left, Synchronized Lyrics on right)
 * - FULL_ART: Centered enlarged Album Art (1:1 aspect ratio), lyrics smoothly hidden, controls below
 * - FULL_LYRICS: Full-screen Synchronized Lyrics, album art hidden, controls below remain visible
 */
enum class TvNowPlayingViewMode {
    SPLIT,
    FULL_ART,
    FULL_LYRICS,
}

/**
 * Reusable TV Action Button with focus outline, scale spring animation, and D-pad key navigation.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvActionButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    isLikedState: Boolean? = null,
    focusPropertiesBuilder: FocusProperties.() -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.18f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tvBtnScale",
    )

    val activePrimary = MaterialTheme.colorScheme.primary
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> activePrimary
            isLikedState == true -> Color(0xFFE91E63).copy(alpha = 0.25f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        animationSpec = tween(150),
        label = "tvBtnBg",
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            isLikedState == true -> Color(0xFFFF4081)
            else -> Color.White.copy(alpha = 0.88f)
        },
        animationSpec = tween(150),
        label = "tvBtnTint",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.White.copy(alpha = 0.95f)
            isLikedState == true -> Color(0xFFFF4081).copy(alpha = 0.6f)
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "tvBtnBorder",
    )

    Box(
        modifier = modifier
            .size(46.dp)
            .scale(scale)
            .focusRequester(focusRequester)
            .focusProperties(focusPropertiesBuilder)
            .focusable(interactionSource = interactionSource)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(2.dp, borderColor, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown && keyEvent.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                val isEnter = keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter ||
                    keyEvent.key == Key.Spacebar ||
                    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                    keyCode == KeyEvent.KEYCODE_SPACE

                if (isEnter) {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        onClick()
                    }
                    return@onPreviewKeyEvent true
                }
                false
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * TV-optimized Now Playing screen with D-pad navigation, like button, and 3-mode view changer:
 * 1. Default (Split): Album Art left, Lyrics right
 * 2. Full Art: Enlarged 1:1 Album Art centered, lyrics hidden
 * 3. Full Lyrics: Synchronized Lyrics taking main area, album art hidden, controls below remain visible
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TvNowPlayingScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current

    if (playerConnection == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.together_playback_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val isLiked = currentSong?.song?.liked == true

    if (mediaMetadata == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(R.string.together_playback_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var viewMode by rememberSaveable { mutableStateOf(TvNowPlayingViewMode.SPLIT) }

    val artScale by rememberPreference(AmbientArtScaleKey, 0.95f)
    val ambientCanvasEnabled by rememberPreference(AmbientCanvasEnabledKey, true)
    val showProgressBar by rememberPreference(AmbientShowProgressBarKey, true)
    val showTitle by rememberPreference(AmbientShowTitleKey, true)
    val showArtist by rememberPreference(AmbientShowArtistKey, true)
    val showLyrics by rememberPreference(AmbientShowLyricsKey, true)
    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)

    val currentLyrics by database.lyrics(mediaMetadata?.id.orEmpty()).collectAsState(initial = null)
    val lyricsHelper =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    LyricsHelperEntryPoint::class.java,
                ).lyricsHelper()
        }

    LaunchedEffect(mediaMetadata?.id, showLyrics, currentLyrics?.lyrics) {
        val currentMeta = mediaMetadata ?: return@LaunchedEffect
        if (!showLyrics || currentLyrics?.lyrics != null) return@LaunchedEffect
        LyricsFetchManager.fetchLyricsForSong(
            context = context,
            database = database,
            lyricsHelper = lyricsHelper,
            mediaMetadata = currentMeta,
            force = true,
        )
    }

    // Canvas settings
    val spotifySpDc by rememberPreference(SpotifySpDcKey, "")
    val syncLikesToSpotify by rememberPreference(SyncLikesToSpotifyKey, false)
    val canvasSource by rememberEnumPreference(CanvasSourceKey, CanvasSource.TIDAL)
    val canvasMetered by rememberPreference(CanvasMeteredKey, true)
    val canvasFallback by rememberPreference(CanvasFallbackKey, true)
    val canvasProceduralFallback by rememberPreference(CanvasProceduralFallbackKey, false)
    val canvasProceduralStyle by rememberEnumPreference(CanvasProceduralStyleKey, ProceduralCanvasStyle.KAWARP)
    val canvasAudioReactive by rememberPreference(CanvasAudioReactiveKey, false)

    val player = playerConnection.player
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(player, mediaMetadata?.id) {
        while (isActive) {
            if (sliderPosition == null) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.coerceAtLeast(0L)
            }
            delay(200)
        }
    }

    val canvasPolicy =
        remember(canvasSource, canvasMetered, canvasFallback, spotifySpDc) {
            CanvasRequestPolicy(
                preferredSource = canvasSource,
                allowMetered = canvasMetered,
                allowFallback = canvasFallback,
                spDc = spotifySpDc.takeIf { it.isNotBlank() },
            )
        }

    var canvasArtwork by remember(mediaMetadata?.id) { mutableStateOf<CanvasArtwork?>(null) }
    var canvasFetchInFlight by remember(mediaMetadata?.id) { mutableStateOf(false) }

    LaunchedEffect(ambientCanvasEnabled, mediaMetadata?.id) {
        val metadata = mediaMetadata
        if (!ambientCanvasEnabled || metadata == null) {
            canvasArtwork = null
            canvasFetchInFlight = false
            return@LaunchedEffect
        }
        if (canvasFetchInFlight) return@LaunchedEffect
        canvasFetchInFlight = true
        try {
            canvasArtwork =
                resolveCanvasArtworkForPlayback(
                    mediaId = metadata.id,
                    songTitleRaw = metadata.title,
                    artistNameRaw = metadata.artists.firstOrNull()?.name.orEmpty(),
                    albumId = metadata.album?.id,
                    albumTitleRaw = metadata.album?.title,
                    storefront = "US",
                    requireVertical = false,
                    allowNetwork = canvasMetered,
                    canvasPolicy = canvasPolicy,
                )
        } finally {
            canvasFetchInFlight = false
        }
    }

    val primaryCanvasUrl =
        canvasArtwork?.animated ?: canvasArtwork?.animatedVertical ?: canvasArtwork?.videoUrl ?: canvasArtwork?.videoUrlVertical
    val fallbackCanvasUrl = canvasArtwork?.videoUrl ?: canvasArtwork?.videoUrlVertical
    val hasAnimatedCanvas = !primaryCanvasUrl.isNullOrBlank() || !fallbackCanvasUrl.isNullOrBlank()

    val displayUrl =
        mediaMetadata?.thumbnailUrl?.highRes()
            ?: canvasArtwork?.static
            ?: canvasArtwork?.preferredAnimationUrl
            ?: canvasArtwork?.preferredVerticalAnimationUrl

    val proceduralBitmap by produceState<Bitmap?>(null, displayUrl, ambientCanvasEnabled, hasAnimatedCanvas, canvasProceduralFallback) {
        if (!ambientCanvasEnabled || !canvasProceduralFallback || hasAnimatedCanvas || displayUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        withContext(Dispatchers.IO) {
            try {
                val request =
                    ImageRequest.Builder(context)
                        .data(displayUrl)
                        .allowHardware(false)
                        .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    value = result.image.toBitmap()
                }
            } catch (_: Exception) {
                value = null
            }
        }
    }

    val audioSessionId = playerConnection.localPlayer.audioSessionId
    val canvasRenderMode =
        remember(
            ambientCanvasEnabled,
            hasAnimatedCanvas,
            canvasProceduralFallback,
            canvasProceduralStyle,
            canvasAudioReactive,
            audioSessionId,
            primaryCanvasUrl,
            fallbackCanvasUrl,
            proceduralBitmap,
            context,
        ) {
            when {
                !ambientCanvasEnabled -> CanvasRenderMode.None
                hasAnimatedCanvas ->
                    CanvasRenderMode.Video(
                        primaryUrl = primaryCanvasUrl ?: fallbackCanvasUrl!!,
                        fallbackUrl = fallbackCanvasUrl,
                    )
                canvasProceduralFallback && proceduralBitmap != null ->
                    resolveProceduralRenderMode(
                        context = context,
                        bitmap = proceduralBitmap,
                        style = canvasProceduralStyle,
                        audioSessionId = audioSessionId,
                        audioReactive = canvasAudioReactive,
                    )
                else -> CanvasRenderMode.None
            }
        }

    // Focus requesters for Modes 1 & 2 (Split / Full Art)
    val defaultAlbumArtFocusRequester = remember { FocusRequester() }
    val defaultFullLyricsLikeFocusRequester = remember { FocusRequester() }

    val albumArtFocusRequester = remember(firstFocusRequester, viewMode) {
        if (viewMode != TvNowPlayingViewMode.FULL_LYRICS && firstFocusRequester != null) {
            firstFocusRequester
        } else {
            defaultAlbumArtFocusRequester
        }
    }

    val fullLyricsLikeFocusRequester = remember(firstFocusRequester, viewMode) {
        if (viewMode == TvNowPlayingViewMode.FULL_LYRICS && firstFocusRequester != null) {
            firstFocusRequester
        } else {
            defaultFullLyricsLikeFocusRequester
        }
    }

    val likeFocusRequester = remember { FocusRequester() }
    val viewModeFocusRequester = remember { FocusRequester() }

    // Focus requesters for Mode 3 (Full Lyrics)
    val fullLyricsViewModeFocusRequester = remember { FocusRequester() }

    val albumArtInteractionSource = remember { MutableInteractionSource() }
    val isAlbumArtFocused by albumArtInteractionSource.collectIsFocusedAsState()

    val albumArtScale by animateFloatAsState(
        targetValue = if (isAlbumArtFocused) 1.05f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tvAlbumArtScale",
    )

    val activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isAlbumArtFocused) activeBorderColor else Color.Transparent,
        animationSpec = tween(150),
        label = "tvAlbumArtBorder",
    )

    // Smoothly maintain focus on viewMode change
    LaunchedEffect(viewMode) {
        if (viewMode == TvNowPlayingViewMode.FULL_LYRICS) {
            fullLyricsViewModeFocusRequester.safeRequestFocus()
        } else {
            viewModeFocusRequester.safeRequestFocus()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        AmbientGlowBackground(mediaMetadata = mediaMetadata, modifier = Modifier.fillMaxSize())

        AnimatedContent(
            targetState = viewMode == TvNowPlayingViewMode.FULL_LYRICS,
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(250))
            },
            label = "tvNowPlayingMainLayout",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 68.dp),
        ) { isFullLyrics ->
            if (isFullLyrics) {
                // Mode 3: Full Synchronized Lyrics taking top/main screen, controls below remain visible
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Synchronized Lyrics taking main area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusProperties {
                                canFocus = false
                                enter = { FocusRequester.Cancel }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (lyricsMode) {
                            LyricsMode.STYLE_1 -> {
                                LyricsStyleView(
                                    motionStyle = LyricsMotionStyle.MOTION,
                                    sliderPositionProvider = { sliderPosition },
                                    lyricsSyncOffset = 0,
                                    textColorOverride = Color.White,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            LyricsMode.STYLE_2 -> {
                                LyricsStyleView(
                                    motionStyle = LyricsMotionStyle.LEGACY,
                                    sliderPositionProvider = { sliderPosition },
                                    lyricsSyncOffset = 0,
                                    textColorOverride = Color.White,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            LyricsMode.V2 -> {
                                LyricsV2(
                                    sliderPositionProvider = { sliderPosition },
                                    lyricsSyncOffset = 0,
                                    textColorOverride = Color.White,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            LyricsMode.ENHANCED -> {
                                LyricsEnhanced(
                                    sliderPositionProvider = { sliderPosition },
                                    lyricsSyncOffset = 0,
                                    textColorOverride = Color.White,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Song Info (Title & Artist)
                    if (showTitle) {
                        Text(
                            text = mediaMetadata?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .basicMarquee(),
                        )
                    }
                    if (showArtist) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .basicMarquee(),
                        )
                    }

                    // Progress Bar shifted below
                    if (showProgressBar) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val currentPos = (sliderPosition ?: position).coerceIn(0L, maxOf(1L, duration))
                        val safeDuration = maxOf(1L, duration)

                        Slider(
                            value = currentPos.toFloat(),
                            valueRange = 0f..safeDuration.toFloat(),
                            onValueChange = { sliderPosition = it.toLong() },
                            onValueChangeFinished = {
                                sliderPosition?.let { targetPos ->
                                    playerConnection.player.seekTo(targetPos)
                                }
                                sliderPosition = null
                            },
                            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                            track = { sliderState ->
                                PlayerSliderTrack(
                                    sliderState = sliderState,
                                    colors =
                                        SliderDefaults.colors(
                                            activeTrackColor = Color.White,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                                        ),
                                    trackHeight = 3.5.dp,
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(22.dp)
                                .focusProperties { canFocus = false },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Buttons Row: Like Button + View Changer Button
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tvLikeIcon =
                            if (syncLikesToSpotify) {
                                if (isLiked) R.drawable.check else R.drawable.add
                            } else {
                                if (isLiked) R.drawable.favorite else R.drawable.favorite_border
                            }
                        TvActionButton(
                            iconRes = tvLikeIcon,
                            contentDescription = if (isLiked) "Unlike song" else "Like song",
                            onClick = { playerConnection.toggleLike() },
                            focusRequester = fullLyricsLikeFocusRequester,
                            isLikedState = isLiked,
                            focusPropertiesBuilder = {
                                left = FocusRequester.Cancel
                                right = fullLyricsViewModeFocusRequester
                                down = FocusRequester.Cancel
                                if (upFocusRequester != null) {
                                    up = upFocusRequester
                                }
                            },
                        )

                        TvActionButton(
                            iconRes = R.drawable.lyrics,
                            contentDescription = "View mode: Lyrics (click for Split View)",
                            onClick = {
                                viewMode = TvNowPlayingViewMode.SPLIT
                            },
                            focusRequester = fullLyricsViewModeFocusRequester,
                            focusPropertiesBuilder = {
                                left = fullLyricsLikeFocusRequester
                                right = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                                if (upFocusRequester != null) {
                                    up = upFocusRequester
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                // Mode 1 (SPLIT) & Mode 2 (FULL_ART)
                val lyricsWeight by animateFloatAsState(
                    targetValue = if (viewMode == TvNowPlayingViewMode.SPLIT && showLyrics) 1.2f else 0f,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "tvLyricsWeight",
                )

                val animatedArtHeightFraction by animateFloatAsState(
                    targetValue = if (viewMode == TvNowPlayingViewMode.FULL_ART) 0.98f else 0.92f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "tvArtHeightFraction",
                )

                val controlsMaxWidthFraction by animateFloatAsState(
                    targetValue = if (viewMode == TvNowPlayingViewMode.FULL_ART) 0.52f else 0.88f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "tvControlsWidthFraction",
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left Column (Split) or Center Column (Full Art)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(bottom = 6.dp),
                        ) {
                            // Upper area taking weight(1f) to maximize and center Album Art
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(animatedArtHeightFraction)
                                        .aspectRatio(1f)
                                        .scale(albumArtScale)
                                        .focusRequester(albumArtFocusRequester)
                                        .focusProperties {
                                            left = FocusRequester.Cancel
                                            right = FocusRequester.Cancel
                                            down = likeFocusRequester
                                            if (upFocusRequester != null) {
                                                up = upFocusRequester
                                            }
                                        }
                                        .focusable(interactionSource = albumArtInteractionSource)
                                        .clip(RoundedCornerShape(22.dp))
                                        .border(
                                            width = 3.dp,
                                            color = animatedBorderColor,
                                            shape = RoundedCornerShape(22.dp),
                                        )
                                        .pointerInput(Unit) {
                                            detectTapGestures {
                                                playerConnection.player.togglePlayPause()
                                            }
                                        }
                                        .onPreviewKeyEvent { keyEvent ->
                                            val keyCode = keyEvent.nativeKeyEvent.keyCode
                                            val isEnterKey = keyEvent.key == Key.DirectionCenter ||
                                                keyEvent.key == Key.Enter ||
                                                keyEvent.key == Key.NumPadEnter ||
                                                keyEvent.key == Key.Spacebar ||
                                                keyEvent.key == Key.MediaPlayPause ||
                                                keyEvent.key == Key.MediaPlay ||
                                                keyEvent.key == Key.MediaPause ||
                                                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                                keyCode == KeyEvent.KEYCODE_ENTER ||
                                                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                                keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                                                keyCode == KeyEvent.KEYCODE_SPACE ||
                                                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                                                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE

                                            val isLeftKey = keyEvent.key == Key.DirectionLeft ||
                                                keyEvent.key == Key.MediaPrevious ||
                                                keyEvent.key == Key.MediaRewind ||
                                                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                                                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                                                keyCode == KeyEvent.KEYCODE_MEDIA_REWIND

                                            val isRightKey = keyEvent.key == Key.DirectionRight ||
                                                keyEvent.key == Key.MediaNext ||
                                                keyEvent.key == Key.MediaFastForward ||
                                                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                                                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                                                keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD

                                            val isUpKey = keyEvent.key == Key.DirectionUp || keyCode == KeyEvent.KEYCODE_DPAD_UP
                                            val isDownKey = keyEvent.key == Key.DirectionDown || keyCode == KeyEvent.KEYCODE_DPAD_DOWN

                                            if (isEnterKey) {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    Timber.d("TvNowPlaying: Play/Pause toggled via Enter/OK")
                                                    playerConnection.player.togglePlayPause()
                                                }
                                                return@onPreviewKeyEvent true
                                            }

                                            if (isLeftKey) {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    Timber.d("TvNowPlaying: seekToPrevious via Left")
                                                    playerConnection.seekToPrevious()
                                                }
                                                return@onPreviewKeyEvent true
                                            }

                                            if (isRightKey) {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    Timber.d("TvNowPlaying: seekToNext via Right")
                                                    playerConnection.seekToNext()
                                                }
                                                return@onPreviewKeyEvent true
                                            }

                                            if (isUpKey) {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    Timber.d("TvNowPlaying: Move focus up to TopBar")
                                                    upFocusRequester?.safeRequestFocus()
                                                }
                                                return@onPreviewKeyEvent true
                                            }

                                            if (isDownKey) {
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    likeFocusRequester.safeRequestFocus()
                                                }
                                                return@onPreviewKeyEvent true
                                            }

                                            false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AsyncImage(
                                        model = rememberOfflineArtworkImageRequest(displayUrl),
                                        contentDescription = "Album Art",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )

                                    if (canvasRenderMode !is CanvasRenderMode.None) {
                                        CanvasArtworkPlayer(
                                            renderMode = canvasRenderMode,
                                            isPlaying = isPlaying,
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }

                            // Song Info (Title & Artist)
                            if (showTitle || showArtist) {
                                Spacer(modifier = Modifier.height(6.dp))
                                if (showTitle) {
                                    Text(
                                        text = mediaMetadata?.title.orEmpty(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth(controlsMaxWidthFraction)
                                            .basicMarquee(),
                                    )
                                }
                                if (showArtist) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth(controlsMaxWidthFraction)
                                            .basicMarquee(),
                                    )
                                }
                            }

                            // Progress Bar shifted below
                            if (showProgressBar) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val currentPos = (sliderPosition ?: position).coerceIn(0L, maxOf(1L, duration))
                                val safeDuration = maxOf(1L, duration)

                                Slider(
                                    value = currentPos.toFloat(),
                                    valueRange = 0f..safeDuration.toFloat(),
                                    onValueChange = { sliderPosition = it.toLong() },
                                    onValueChangeFinished = {
                                        sliderPosition?.let { targetPos ->
                                            playerConnection.player.seekTo(targetPos)
                                        }
                                        sliderPosition = null
                                    },
                                    thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                                    track = { sliderState ->
                                        PlayerSliderTrack(
                                            sliderState = sliderState,
                                            colors =
                                                SliderDefaults.colors(
                                                    activeTrackColor = Color.White,
                                                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                                                ),
                                            trackHeight = 3.5.dp,
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth(controlsMaxWidthFraction)
                                        .height(22.dp)
                                        .focusProperties { canFocus = false },
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Buttons Row: Like Button + View Changer Button
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val tvLikeIcon2 =
                                    if (syncLikesToSpotify) {
                                        if (isLiked) R.drawable.check else R.drawable.add
                                    } else {
                                        if (isLiked) R.drawable.favorite else R.drawable.favorite_border
                                    }
                                TvActionButton(
                                    iconRes = tvLikeIcon2,
                                    contentDescription = if (isLiked) "Unlike song" else "Like song",
                                    onClick = { playerConnection.toggleLike() },
                                    focusRequester = likeFocusRequester,
                                    isLikedState = isLiked,
                                    focusPropertiesBuilder = {
                                        left = FocusRequester.Cancel
                                        right = viewModeFocusRequester
                                        down = FocusRequester.Cancel
                                        up = albumArtFocusRequester
                                    },
                                )

                                TvActionButton(
                                    iconRes = when (viewMode) {
                                        TvNowPlayingViewMode.SPLIT -> R.drawable.grid_view
                                        TvNowPlayingViewMode.FULL_ART -> R.drawable.album
                                        TvNowPlayingViewMode.FULL_LYRICS -> R.drawable.lyrics
                                    },
                                    contentDescription = when (viewMode) {
                                        TvNowPlayingViewMode.SPLIT -> "View mode: Split (click for Album Art)"
                                        TvNowPlayingViewMode.FULL_ART -> "View mode: Album Art (click for Lyrics)"
                                        TvNowPlayingViewMode.FULL_LYRICS -> "View mode: Lyrics (click for Split)"
                                    },
                                    onClick = {
                                        viewMode = when (viewMode) {
                                            TvNowPlayingViewMode.SPLIT -> TvNowPlayingViewMode.FULL_ART
                                            TvNowPlayingViewMode.FULL_ART -> TvNowPlayingViewMode.FULL_LYRICS
                                            TvNowPlayingViewMode.FULL_LYRICS -> TvNowPlayingViewMode.SPLIT
                                        }
                                    },
                                    focusRequester = viewModeFocusRequester,
                                    focusPropertiesBuilder = {
                                        left = likeFocusRequester
                                        right = FocusRequester.Cancel
                                        down = FocusRequester.Cancel
                                        up = albumArtFocusRequester
                                    },
                                )
                            }
                        }
                    }

                    // Right Column: Synchronized Lyrics in SPLIT mode
                    if (lyricsWeight > 0.01f) {
                        Box(
                            modifier = Modifier
                                .weight(lyricsWeight)
                                .fillMaxHeight()
                                .padding(start = 16.dp, end = 24.dp, top = 4.dp, bottom = 10.dp)
                                .graphicsLayer { alpha = (lyricsWeight / 1.2f).coerceIn(0f, 1f) }
                                .focusProperties {
                                    canFocus = false
                                    enter = { FocusRequester.Cancel }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (lyricsMode) {
                                LyricsMode.STYLE_1 -> {
                                    LyricsStyleView(
                                        motionStyle = LyricsMotionStyle.MOTION,
                                        sliderPositionProvider = { sliderPosition },
                                        lyricsSyncOffset = 0,
                                        textColorOverride = Color.White,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                LyricsMode.STYLE_2 -> {
                                    LyricsStyleView(
                                        motionStyle = LyricsMotionStyle.LEGACY,
                                        sliderPositionProvider = { sliderPosition },
                                        lyricsSyncOffset = 0,
                                        textColorOverride = Color.White,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                LyricsMode.V2 -> {
                                    LyricsV2(
                                        sliderPositionProvider = { sliderPosition },
                                        lyricsSyncOffset = 0,
                                        textColorOverride = Color.White,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                LyricsMode.ENHANCED -> {
                                    LyricsEnhanced(
                                        sliderPositionProvider = { sliderPosition },
                                        lyricsSyncOffset = 0,
                                        textColorOverride = Color.White,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
