/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.ambient

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import moe.rgsekai.sekaitune.constants.AmbientVolumeGestureEnabledKey
import moe.rgsekai.sekaitune.constants.CanvasAudioReactiveKey
import moe.rgsekai.sekaitune.constants.CanvasFallbackKey
import moe.rgsekai.sekaitune.constants.CanvasMeteredKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralFallbackKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralStyleKey
import moe.rgsekai.sekaitune.constants.CanvasSourceKey
import moe.rgsekai.sekaitune.constants.LyricsMode
import moe.rgsekai.sekaitune.constants.LyricsModeKey
import moe.rgsekai.sekaitune.constants.SpotifySpDcKey
import moe.rgsekai.sekaitune.di.LyricsHelperEntryPoint
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.lyrics.LyricsFetchManager
import moe.rgsekai.sekaitune.ui.component.LyricsEnhanced
import moe.rgsekai.sekaitune.ui.component.LyricsV2
import moe.rgsekai.sekaitune.ui.component.PlayerSliderTrack
import moe.rgsekai.sekaitune.ui.player.CanvasArtworkPlayer
import moe.rgsekai.sekaitune.ui.player.CanvasRenderMode
import moe.rgsekai.sekaitune.ui.player.rememberOfflineArtworkImageRequest
import moe.rgsekai.sekaitune.ui.player.resolveCanvasArtworkForPlayback
import moe.rgsekai.sekaitune.ui.player.resolveProceduralRenderMode
import moe.rgsekai.sekaitune.ui.utils.highRes
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientModeScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val artScale by rememberPreference(AmbientArtScaleKey, 0.85f)
    val ambientCanvasEnabled by rememberPreference(AmbientCanvasEnabledKey, true)
    val ambientVolumeGestureEnabled by rememberPreference(AmbientVolumeGestureEnabledKey, true)
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
    val canvasSource by rememberEnumPreference(CanvasSourceKey, CanvasSource.TIDAL)
    val canvasMetered by rememberPreference(CanvasMeteredKey, true)
    val canvasFallback by rememberPreference(CanvasFallbackKey, true)
    val canvasProceduralFallback by rememberPreference(CanvasProceduralFallbackKey, true)
    val canvasProceduralStyle by rememberEnumPreference(CanvasProceduralStyleKey, ProceduralCanvasStyle.KAWARP)
    val canvasAudioReactive by rememberPreference(CanvasAudioReactiveKey, false)

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation =
            activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        var windowInsetsController: WindowInsetsControllerCompat? = null
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { navController.popBackStack() }

    var swipeThresholdX by remember { mutableFloatStateOf(0f) }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // Playback position tracking
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

    // Live Canvas Artwork resolution
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(swipeThresholdX) > 100.dp.toPx()) {
                                if (swipeThresholdX > 0) {
                                    playerConnection.player.seekToPreviousMediaItem()
                                } else {
                                    playerConnection.player.seekToNext()
                                }
                            }
                            swipeThresholdX = 0f
                        },
                        onDragCancel = {
                            swipeThresholdX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeThresholdX += dragAmount
                        },
                    )
                },
    ) {
        AmbientGlowBackground(mediaMetadata = mediaMetadata, modifier = Modifier.fillMaxSize())

        Row(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column: Album Art, Info & Progress Bar
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    // Album Art Container (with live canvas or static artwork)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f, fill = false)
                                .fillMaxHeight(artScale.coerceIn(0.4f, 0.95f))
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .pointerInput(ambientVolumeGestureEnabled) {
                                    var lastTapTime = 0L
                                    val doubleTapTimeout = 320L
                                    val stepPx = 28.dp.toPx()

                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var accumulatedDeltaY = 0f
                                        var isDragging = false
                                        val pointerId = down.id

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                            if (change.changedToUpIgnoreConsumed()) {
                                                if (!isDragging) {
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastTapTime < doubleTapTimeout) {
                                                        playerConnection.player.togglePlayPause()
                                                        lastTapTime = 0L
                                                    } else {
                                                        lastTapTime = now
                                                    }
                                                }
                                                break
                                            }

                                            if (change.isConsumed) {
                                                break
                                            }

                                            val dragY = change.position.y - change.previousPosition.y
                                            if (ambientVolumeGestureEnabled) {
                                                if (!isDragging && abs(change.position.y - down.position.y) > 10.dp.toPx()) {
                                                    isDragging = true
                                                }

                                                if (isDragging) {
                                                    change.consume()
                                                    accumulatedDeltaY += dragY
                                                    while (accumulatedDeltaY <= -stepPx) {
                                                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                        if (currentVolume < maxVolume) {
                                                            audioManager.adjustStreamVolume(
                                                                AudioManager.STREAM_MUSIC,
                                                                AudioManager.ADJUST_RAISE,
                                                                AudioManager.FLAG_SHOW_UI,
                                                            )
                                                        }
                                                        accumulatedDeltaY += stepPx
                                                    }
                                                    while (accumulatedDeltaY >= stepPx) {
                                                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                        if (currentVolume > 0) {
                                                            audioManager.adjustStreamVolume(
                                                                AudioManager.STREAM_MUSIC,
                                                                AudioManager.ADJUST_LOWER,
                                                                AudioManager.FLAG_SHOW_UI,
                                                            )
                                                        }
                                                        accumulatedDeltaY -= stepPx
                                                    }
                                                }
                                            }
                                        }
                                    }
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

                    // Song Info (Title & Artist) - decreased size
                    if (showTitle || showArtist) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (showTitle) {
                            Text(
                                text = mediaMetadata?.title.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.85f).basicMarquee(),
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
                                modifier = Modifier.fillMaxWidth(0.85f).basicMarquee(),
                            )
                        }
                    }

                    // Progress Bar only below the Album Art
                    if (showProgressBar) {
                        Spacer(modifier = Modifier.height(8.dp))
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
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(22.dp),
                        )
                    }
                }
            }

            // Right Column: Synchronized Lyrics
            if (showLyrics) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 12.dp, end = 28.dp, top = 20.dp, bottom = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (lyricsMode) {
                        LyricsMode.V2 -> {
                            LyricsV2(
                                sliderPositionProvider = { sliderPosition ?: position },
                                lyricsSyncOffset = 0,
                                textColorOverride = Color.White,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        LyricsMode.ENHANCED -> {
                            LyricsEnhanced(
                                sliderPositionProvider = { sliderPosition ?: position },
                                lyricsSyncOffset = 0,
                                textColorOverride = Color.White,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        // Top-Left Back Button Overlay (close to edge)
        IconButton(
            onClick = { navController.popBackStack() },
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
    }
}
