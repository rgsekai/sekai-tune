/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import moe.rgsekai.sekaitune.ui.theme.pressScale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.EnableHapticFeedbackKey
import moe.rgsekai.sekaitune.constants.SyncLikesToSpotifyKey
import moe.rgsekai.sekaitune.constants.MiniPlayerHeight
import moe.rgsekai.sekaitune.extensions.currentMetadata
import moe.rgsekai.sekaitune.extensions.metadata
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.together.isConnectedToSession
import moe.rgsekai.sekaitune.utils.rememberLowDataModeActive
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// Spotify-calibrated swipe thresholds and physics (defined in dp for density independence)
val SpotifyMinFlickDistance = 13.dp // ~39px on 480dpi (Spotify triggers >= 13.3dp / 40px on flick)
val SpotifyStaticDistanceThreshold = 50.dp // ~150px on 480dpi (Spotify triggers >= 53dp on slow drag, snaps back < 50dp)
val SpotifyFlickVelocityThreshold = 75.dp // 75 dp/s (~225px/s on 480dpi; Spotify triggers at >= 89 dp/s, ignores <= 67 dp/s)

@Immutable
data class MiniPlayerContentColors(
    val title: Color,
    val secondary: Color,
    val progress: Color,
    val progressTrack: Color,
    val artworkContainer: Color,
    val artworkBorder: Color,
    val primaryButtonContainer: Color,
    val buttonBorder: Color,
    val buttonIcon: Color,
    val disabledButtonIcon: Color,
    val togetherContainer: Color,
    val togetherContent: Color,
)

@Composable
fun SwipeableMiniPlayerBox(
    modifier: Modifier = Modifier,
    swipeSensitivity: Float,
    swipeThumbnail: Boolean,
    playerConnection: PlayerConnection,
    layoutDirection: LayoutDirection,
    coroutineScope: CoroutineScope,
    pureBlack: Boolean = false,
    useLegacyBackground: Boolean = false,
    slideDistanceProvider: () -> Float = { 0f },
    content: @Composable (Float) -> Unit,
) {
    val offsetXAnimatable = remember { Animatable(0f) }
    var dragStartTime by remember { mutableStateOf(0L) }
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    var dragCurrentOffset by remember { mutableFloatStateOf(0f) }
    val velocityTracker = remember { VelocityTracker() }

    val view = LocalView.current
    val density = LocalDensity.current
    val currentViewConfig = LocalViewConfiguration.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    val touchSlopPx = with(density) { 8.dp.toPx() }
    val customViewConfig = remember(currentViewConfig, touchSlopPx) {
        object : ViewConfiguration by currentViewConfig {
            override val touchSlop: Float = touchSlopPx
        }
    }

    val minFlickDistancePx = with(density) { SpotifyMinFlickDistance.toPx() }
    val staticDistanceThresholdPx = with(density) { SpotifyStaticDistanceThreshold.toPx() }
    val flickVelocityThresholdPxPerSec = with(density) { SpotifyFlickVelocityThreshold.toPx() }

    val animationSpec =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    CompositionLocalProvider(LocalViewConfiguration provides customViewConfig) {
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                .height(MiniPlayerHeight)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .let { baseModifier ->
                    if (useLegacyBackground) {
                        baseModifier.background(
                            if (pureBlack) {
                                Color.Black
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        )
                    } else {
                        baseModifier.padding(horizontal = 12.dp)
                    }
                }.let { baseModifier ->
                    if (swipeThumbnail) {
                        baseModifier.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragStartTime = System.currentTimeMillis()
                                    totalDragDistance = 0f
                                    dragCurrentOffset = 0f
                                    velocityTracker.resetTracking()
                                    coroutineScope.launch {
                                        offsetXAnimatable.stop()
                                    }
                                },
                                onDragCancel = {
                                    dragCurrentOffset = 0f
                                    coroutineScope.launch {
                                        offsetXAnimatable.animateTo(
                                            targetValue = 0f,
                                            animationSpec = animationSpec,
                                        )
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val adjustedDragAmount =
                                        if (layoutDirection == LayoutDirection.Rtl) -dragAmount else dragAmount
                                    val canSkipPrevious = playerConnection.player.previousMediaItemIndex != -1
                                    val canSkipNext = playerConnection.player.nextMediaItemIndex != -1
                                    val newOffset = dragCurrentOffset + adjustedDragAmount
                                    val allowDrag = when {
                                        newOffset < 0 -> canSkipNext
                                        newOffset > 0 -> canSkipPrevious
                                        else -> true
                                    }
                                    if (allowDrag) {
                                        val maxSlide = slideDistanceProvider().takeIf { it > 0f } ?: 1000f
                                        val clampedOffset = newOffset.coerceIn(-maxSlide, maxSlide)
                                        dragCurrentOffset = clampedOffset
                                        totalDragDistance += kotlin.math.abs(adjustedDragAmount)
                                        coroutineScope.launch {
                                            offsetXAnimatable.snapTo(clampedOffset)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    val dragDuration = maxOf(1L, System.currentTimeMillis() - dragStartTime)
                                    val trackerVelocityX = kotlin.math.abs(velocityTracker.calculateVelocity().x)
                                    val fallbackVelocityPxSec =
                                        ((totalDragDistance + touchSlopPx) / dragDuration) * 1000f
                                    val velocityPxSec = maxOf(trackerVelocityX, fallbackVelocityPxSec)
                                    val currentOffset = dragCurrentOffset
                                    val absOffset = kotlin.math.abs(currentOffset)
                                    val effectiveTravel = absOffset + touchSlopPx

                                    val isFlick =
                                        absOffset > 0f &&
                                            effectiveTravel >= minFlickDistancePx &&
                                            velocityPxSec >= flickVelocityThresholdPxPerSec
                                    val isStaticSwipe = absOffset >= staticDistanceThresholdPx || effectiveTravel >= staticDistanceThresholdPx
                                    val shouldChangeSong = isFlick || isStaticSwipe

                                    val isRightSwipe = currentOffset > 0
                                    val canSkipPrevious = playerConnection.player.previousMediaItemIndex != -1
                                    val canSkipNext = playerConnection.player.nextMediaItemIndex != -1

                                    val willChange = shouldChangeSong && (
                                        (isRightSwipe && canSkipPrevious) || (!isRightSwipe && canSkipNext)
                                    )

                                    if (willChange) {
                                        val targetDistance = slideDistanceProvider().takeIf { it > 0f } ?: 600f
                                        val targetOffset = if (isRightSwipe) targetDistance else -targetDistance
                                        coroutineScope.launch {
                                            offsetXAnimatable.animateTo(
                                                targetValue = targetOffset,
                                                animationSpec = animationSpec,
                                            )
                                            if (enableHapticFeedback) {
                                                view.performHapticFeedback(
                                                    android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                                )
                                            }
                                            if (isRightSwipe) {
                                                playerConnection.player.seekToPreviousMediaItem()
                                            } else {
                                                playerConnection.player.seekToNext()
                                            }
                                            playerConnection.service.currentMediaMetadata.value =
                                                playerConnection.player.currentMetadata
                                            dragCurrentOffset = 0f
                                            offsetXAnimatable.snapTo(0f)
                                        }
                                    } else {
                                        dragCurrentOffset = 0f
                                        coroutineScope.launch {
                                            offsetXAnimatable.animateTo(
                                                targetValue = 0f,
                                                animationSpec = animationSpec,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        baseModifier
                    }
                },
    ) {
        content(offsetXAnimatable.value)
    }
    }
}

@Composable
fun MiniPlayerInfo(
    mediaMetadata: MediaMetadata,
    colors: MiniPlayerContentColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = mediaMetadata.title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "title",
        ) { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }

        AnimatedContent(
            targetState = mediaMetadata.artists,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "artist",
        ) { artists ->
            Text(
                text = artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}

@Composable
private fun MiniPlayerArtworkImage(
    mediaMetadata: MediaMetadata?,
    colors: MiniPlayerContentColors,
    modifier: Modifier = Modifier,
) {
    val baseThumbnailUrl = mediaMetadata?.thumbnailUrl
    if (baseThumbnailUrl != null) {
        val thumbnailSwapState =
            rememberThumbnailSwapState(
                videoId = mediaMetadata?.id,
                ytmUrl = baseThumbnailUrl,
                lowDataMode = rememberLowDataModeActive(),
                isMusicVideo = mediaMetadata?.isMusicVideo ?: false,
            )
        AsyncImage(
            model = thumbnailSwapState.displayUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.about_splash),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun MiniPlayerArtwork(
    mediaMetadata: MediaMetadata?,
    previousMetadata: MediaMetadata?,
    nextMetadata: MediaMetadata?,
    positionProvider: () -> Long,
    duration: Long,
    isPlaying: Boolean,
    colors: MiniPlayerContentColors,
    swipeFraction: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val artCircleSizeDp = 37.dp
    val artCircleSizePx = with(density) { artCircleSizeDp.toPx() }
    val artOffset = swipeFraction * artCircleSizePx

    Box(
        modifier = modifier.size(47.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Layer 1 (fixed): the ring/progress arc. No offset or graphicsLayer translationX.
        CircularWavyProgressIndicator(
            progress = { if (duration > 0) (positionProvider().toFloat() / duration).coerceIn(0f, 1f) else 0f },
            waveSpeed = if (isPlaying) WavyProgressIndicatorDefaults.CircularWavelength else 0.dp,
            modifier = Modifier.fillMaxSize(),
            color = colors.progress,
            trackColor = colors.progressTrack,
        )

        // Layer 2 (moving): only the album art image, with the swipe offset, wrapped in Modifier.clip(CircleShape).
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(artCircleSizeDp)
                    .clip(CircleShape)
                    .background(colors.artworkContainer)
                    .border(
                        width = 1.dp,
                        color = colors.artworkBorder,
                        shape = CircleShape,
                    ),
        ) {
            // Outgoing / current track art
            MiniPlayerArtworkImage(
                mediaMetadata = mediaMetadata,
                colors = colors,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = artOffset },
            )

            // Incoming next track art (slides in from right when swiping left)
            if (swipeFraction < 0f && nextMetadata != null) {
                MiniPlayerArtworkImage(
                    mediaMetadata = nextMetadata,
                    colors = colors,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = artOffset + artCircleSizePx },
                )
            }

            // Incoming previous track art (slides in from left when swiping right)
            if (swipeFraction > 0f && previousMetadata != null) {
                MiniPlayerArtworkImage(
                    mediaMetadata = previousMetadata,
                    colors = colors,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = artOffset - artCircleSizePx },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerTransportButton(
    iconResId: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    iconTintOverride: Color? = null,
    colors: MiniPlayerContentColors,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    LaunchedEffect(enableHapticFeedback) {
        view.isHapticFeedbackEnabled = enableHapticFeedback
    }

    val containerColor =
        if (isPrimary) colors.primaryButtonContainer else Color.Transparent
    val borderColor =
        if (enabled) colors.buttonBorder else colors.buttonBorder.copy(alpha = 0.12f)
    val iconTint =
        iconTintOverride ?: if (enabled) colors.buttonIcon else colors.disabledButtonIcon

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .then(modifier)
                .size(if (isPrimary) 40.dp else 36.dp)
                .pressScale(interactionSource, targetScale = 0.94f)
                .clip(CircleShape)
                .background(containerColor)
                .border(width = 1.dp, color = borderColor, shape = CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    enabled = enabled,
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onClick()
                    },
                ),
    ) {
        Icon(
            painter = painterResource(iconResId),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(if (isPrimary) 22.dp else 18.dp),
        )
    }
}

@Composable
private fun MiniPlayerTransportControls(
    isPlaying: Boolean,
    playbackState: Int,
    isLiked: Boolean,
    playerConnection: PlayerConnection,
    colors: MiniPlayerContentColors,
) {
    val haptic = LocalHapticFeedback.current
    val syncLikesToSpotify by rememberPreference(SyncLikesToSpotifyKey, false)

    val likeIconResId =
        if (syncLikesToSpotify) {
            if (isLiked) R.drawable.check else R.drawable.add
        } else {
            if (isLiked) R.drawable.favorite else R.drawable.favorite_border
        }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPlayerTransportButton(
            iconResId = likeIconResId,
            contentDescription = stringResource(R.string.action_like),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                playerConnection.toggleLike()
            },
            iconTintOverride = if (isLiked) {
                if (syncLikesToSpotify) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary
            } else null,
            colors = colors,
        )

        MiniPlayerTransportButton(
            iconResId =
                when {
                    playbackState == Player.STATE_ENDED -> R.drawable.replay
                    isPlaying -> R.drawable.pause
                    else -> R.drawable.play
                },
            contentDescription =
                stringResource(
                    if (playbackState == Player.STATE_ENDED || !isPlaying) R.string.play else R.string.widget_pause,
                ),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (playbackState == Player.STATE_ENDED) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.player.togglePlayPause()
                }
            },
            isPrimary = true,
            colors = colors,
        )
    }
}

private fun getAdjacentMediaMetadata(player: Player, index: Int): MediaMetadata? {
    if (index < 0 || index >= player.mediaItemCount) return null
    val item = runCatching { player.getMediaItemAt(index) }.getOrNull() ?: return null
    return item.metadata ?: item.mediaMetadata.let { meta ->
        meta.title?.let { title ->
            MediaMetadata(
                id = item.mediaId,
                title = title.toString(),
                artists = listOfNotNull(meta.artist?.toString()?.let { a -> MediaMetadata.Artist(id = null, name = a) }),
                duration = (meta.extras?.getLong("duration") ?: 0L).toInt(),
                thumbnailUrl = meta.artworkUri?.toString(),
            )
        }
    }
}

@Composable
fun NewMiniPlayerContent(
    positionProvider: () -> Long,
    duration: Long,
    playerConnection: PlayerConnection,
    colors: MiniPlayerContentColors,
    offsetX: Float = 0f,
    onSlideDistanceChanged: (Float) -> Unit = {},
) {
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val togetherSessionState by playerConnection.service.togetherSessionState.collectAsStateWithLifecycle()
    val isLiked = currentSong?.song?.liked == true

    var textSlideWidth by remember { mutableFloatStateOf(0f) }

    val previousMetadata = remember(mediaMetadata, queueWindows) {
        val prevIndex = playerConnection.player.previousMediaItemIndex
        if (prevIndex != -1) getAdjacentMediaMetadata(playerConnection.player, prevIndex) else null
    }

    val nextMetadata = remember(mediaMetadata, queueWindows) {
        val nextIndex = playerConnection.player.nextMediaItemIndex
        if (nextIndex != -1) getAdjacentMediaMetadata(playerConnection.player, nextIndex) else null
    }

    val swipeFraction = if (textSlideWidth > 0f) (offsetX / textSlideWidth).coerceIn(-1f, 1f) else 0f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        // Layer 1 (fixed ring) & Layer 2 (sliding circle art) inside fixed container on the left
        MiniPlayerArtwork(
            mediaMetadata = mediaMetadata,
            previousMetadata = previousMetadata,
            nextMetadata = nextMetadata,
            positionProvider = positionProvider,
            duration = duration,
            isPlaying = isPlaying,
            colors = colors,
            swipeFraction = swipeFraction,
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Center: Moving text area clipped strictly between artwork and buttons
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .onSizeChanged {
                        val width = it.width.toFloat()
                        textSlideWidth = width
                        onSlideDistanceChanged(width)
                    },
            contentAlignment = Alignment.CenterStart,
        ) {
            val currentMeta = mediaMetadata
            if (currentMeta != null) {
                MiniPlayerInfo(
                    mediaMetadata = currentMeta,
                    colors = colors,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationX = offsetX },
                )
            }

            if (offsetX < 0f && nextMetadata != null && textSlideWidth > 0f) {
                MiniPlayerInfo(
                    mediaMetadata = nextMetadata,
                    colors = colors,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationX = offsetX + textSlideWidth },
                )
            }

            if (offsetX > 0f && previousMetadata != null && textSlideWidth > 0f) {
                MiniPlayerInfo(
                    mediaMetadata = previousMetadata,
                    colors = colors,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationX = offsetX - textSlideWidth },
                )
            }
        }

        // Fixed Buttons on the right (never translates)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 8.dp),
        ) {
            if (togetherSessionState.isConnectedToSession) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.togetherContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.all_inclusive),
                            contentDescription = stringResource(R.string.music_together),
                            tint = colors.togetherContent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            MiniPlayerTransportControls(
                isPlaying = isPlaying,
                playbackState = playbackState,
                isLiked = isLiked,
                playerConnection = playerConnection,
                colors = colors,
            )
        }
    }
}




