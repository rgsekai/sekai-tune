/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.saket.squiggles.SquigglySlider
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.canvas.ProceduralCanvasStyle
import moe.rgsekai.sekaitune.constants.CanvasAudioReactiveKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralFallbackKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralStyleKey
import moe.rgsekai.sekaitune.constants.EnableHapticFeedbackKey
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.constants.PlayerBackgroundStyle
import moe.rgsekai.sekaitune.constants.PlayerDesignStyle
import moe.rgsekai.sekaitune.constants.SekaiTuneCanvasKey
import moe.rgsekai.sekaitune.constants.PlayerHorizontalPadding
import moe.rgsekai.sekaitune.constants.SliderStyle
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.db.entities.codecLabel
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.extensions.toggleRepeatMode
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.ui.component.BottomSheetPageState
import moe.rgsekai.sekaitune.ui.component.BottomSheetState
import moe.rgsekai.sekaitune.ui.component.MenuState
import moe.rgsekai.sekaitune.ui.component.PlayerSliderTrack
import moe.rgsekai.sekaitune.ui.component.ResizableIconButton
import moe.rgsekai.sekaitune.ui.menu.PlayerMenu
import moe.rgsekai.sekaitune.ui.theme.PlayerBackgroundColorUtils
import moe.rgsekai.sekaitune.ui.theme.PlayerSliderColors
import moe.rgsekai.sekaitune.ui.utils.ShowMediaInfo
import moe.rgsekai.sekaitune.ui.utils.highRes
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberLowDataModeActive
import moe.rgsekai.sekaitune.utils.rememberPreference

private const val PlayerBackgroundMaxBlurRadius = 64f
private const val ExplicitBadgeInlineId = "explicitBadge"

@Composable
internal fun PlayerTitleText(
    title: String,
    explicit: Boolean,
    color: Color,
    style: TextStyle,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
) {
    val annotatedTitle =
        remember(title, explicit) {
            buildAnnotatedString {
                append(title)
                if (explicit) {
                    append(" ")
                    appendInlineContent(ExplicitBadgeInlineId, "\uFFFC")
                }
            }
        }
    val badgePainter = painterResource(R.drawable.explicit)
    val inlineContent =
        remember(badgePainter, color, explicit) {
            if (explicit) {
                mapOf(
                    ExplicitBadgeInlineId to
                        InlineTextContent(
                            placeholder =
                                Placeholder(
                                    width = 0.82.em,
                                    height = 0.82.em,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                                ),
                        ) {
                            Icon(
                                painter = badgePainter,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                )
            } else {
                emptyMap()
            }
        }

    Text(
        text = annotatedTitle,
        inlineContent = inlineContent,
        color = color,
        style = style,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun PlayerTitleSection(
    mediaMetadata: MediaMetadata,
    textBackgroundColor: Color,
    navController: NavController,
    state: BottomSheetState,
) {
    // Tap/long-press behavior is centralized; this style keeps its own visual rendering.
    val actions =
        rememberPlayerTitleActions(
            mediaMetadata = mediaMetadata,
            navController = navController,
            state = state,
        )
    AnimatedContent(
        targetState = mediaMetadata.title,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "",
    ) { title ->
        PlayerTitleText(
            title = title,
            explicit = mediaMetadata.explicit,
            color = textBackgroundColor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .basicMarquee()
                    .combinedClickable(
                        enabled = true,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = actions.onTitleClick,
                        onLongClick = actions.onCopyTitle,
                    ),
        )
    }

    Spacer(Modifier.height(6.dp))

    ClickableArtists(
        artists = mediaMetadata.artists,
        onArtistClick = actions.onArtistClick,
        style = MaterialTheme.typography.titleMedium.copy(color = textBackgroundColor, fontSize = 16.sp),
        onLongClick = actions.onCopyArtists,
        modifier =
            Modifier
                .fillMaxWidth()
                .basicMarquee()
                .padding(end = 12.dp),
    )
}

@Composable
fun PlayerTopActions(
    mediaMetadata: MediaMetadata,
    playerDesignStyle: PlayerDesignStyle,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    playerConnection: PlayerConnection,
    navController: NavController,
    menuState: MenuState,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    context: Context,
    currentSongLiked: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    when (playerDesignStyle) {
        PlayerDesignStyle.V2 -> {
            val shareShape =
                RoundedCornerShape(
                    topStart = 50.dp,
                    bottomStart = 50.dp,
                    topEnd = 10.dp,
                    bottomEnd = 10.dp,
                )

            val favShape =
                RoundedCornerShape(
                    topStart = 10.dp,
                    bottomStart = 10.dp,
                    topEnd = 50.dp,
                    bottomEnd = 50.dp,
                )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(42.dp)
                            .clip(shareShape)
                            .background(textButtonColor)
                            .clickable {
                                val intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "https://music.youtube.com/watch?v=${mediaMetadata.id}",
                                        )
                                    }
                                context.startActivity(Intent.createChooser(intent, null))
                            },
                ) {
                    Image(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconButtonColor),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(24.dp),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(42.dp)
                            .clip(favShape)
                            .background(textButtonColor)
                            .clickable {
                                playerConnection.toggleLike()
                            },
                ) {
                    Image(
                        painter =
                            painterResource(
                                if (currentSongLiked) {
                                    R.drawable.favorite
                                } else {
                                    R.drawable.favorite_border
                                },
                            ),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconButtonColor),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(24.dp),
                    )
                }
            }
        }

        PlayerDesignStyle.V7 -> {
            Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSlider(
    sliderStyle: SliderStyle,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    textButtonColor: Color,
    onValueChange: (Long) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val safeDuration = if (duration <= 0L) 0f else duration.toFloat()
    val safeValue = (sliderPosition ?: position).toFloat().coerceIn(0f, maxOf(0f, safeDuration))

    StyledPlaybackSlider(
        sliderStyle = sliderStyle,
        value = safeValue,
        valueRange = 0f..maxOf(1f, safeDuration),
        onValueChange = { onValueChange(it.toLong()) },
        onValueChangeFinished = onValueChangeFinished,
        activeColor = textButtonColor,
        isPlaying = isPlaying,
        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledPlaybackSlider(
    sliderStyle: SliderStyle,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    when (sliderStyle) {
        SliderStyle.Standard -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.standardSliderColors(activeColor),
                modifier = modifier,
            )
        }

        SliderStyle.Wavy -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.wavySliderColors(activeColor),
                modifier = modifier,
                squigglesSpec =
                    SquigglySlider.SquigglesSpec(
                        amplitude = if (isPlaying) 2.dp else 0.dp,
                        strokeWidth = 6.dp,
                    ),
            )
        }

        SliderStyle.Thick -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.thickSliderColors(activeColor),
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = PlayerSliderColors.thickSliderColors(activeColor),
                        trackHeight = 12.dp,
                    )
                },
                modifier = modifier,
            )
        }

        SliderStyle.Circular -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.circularSliderColors(activeColor),
                modifier = modifier,
                squigglesSpec =
                    SquigglySlider.SquigglesSpec(
                        amplitude = if (isPlaying) 2.dp else 0.dp,
                        strokeWidth = 6.dp,
                    ),
            )
        }

        SliderStyle.Simple -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.simpleSliderColors(activeColor),
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = PlayerSliderColors.simpleSliderColors(activeColor),
                        trackHeight = 3.dp,
                    )
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun PlayerTimeLabel(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    showRemainingTime: Boolean = false,
    centerContent: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding + 4.dp),
    ) {
        Text(
            text = makeTimeString(sliderPosition ?: position),
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        if (centerContent != null) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                centerContent()
            }
        }

        Text(
            text =
                if (duration != C.TIME_UNSET) {
                    if (showRemainingTime) {
                        val remaining = duration - (sliderPosition ?: position)
                        "-${makeTimeString(remaining.coerceAtLeast(0))}"
                    } else {
                        makeTimeString(duration)
                    }
                } else {
                    ""
                },
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
fun PlayerPlaybackControls(
    playerDesignStyle: PlayerDesignStyle,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    repeatMode: Int,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    icBackgroundColor: Color,
    playPauseRoundness: androidx.compose.ui.unit.Dp,
    playerConnection: PlayerConnection,
    currentSongLiked: Boolean,
) {
    val haptic = LocalHapticFeedback.current

    when (playerDesignStyle) {
        PlayerDesignStyle.V2 -> {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val maxW = maxWidth
                val playButtonHeight = maxW / 6f
                val playButtonWidth = playButtonHeight * 1.6f
                val sideButtonHeight = playButtonHeight * 0.8f
                val sideButtonWidth = sideButtonHeight * 1.3f

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            playerConnection.seekToPrevious()
                        },
                        enabled = canSkipPrevious,
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = textButtonColor,
                                contentColor = iconButtonColor,
                            ),
                        modifier =
                            Modifier
                                .size(width = sideButtonWidth, height = sideButtonHeight)
                                .clip(RoundedCornerShape(32.dp)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                        },
                        colors =
                            IconButtonDefaults.filledIconButtonColors(
                                containerColor = textButtonColor,
                                contentColor = iconButtonColor,
                            ),
                        modifier =
                            Modifier
                                .size(width = playButtonWidth, height = playButtonHeight)
                                .clip(RoundedCornerShape(32.dp)),
                    ) {
                        if (isLoading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = iconButtonColor,
                            )
                        } else {
                            Icon(
                                painter =
                                    painterResource(
                                        when {
                                            playbackState == STATE_ENDED -> R.drawable.replay
                                            isPlaying -> R.drawable.pause
                                            else -> R.drawable.play
                                        },
                                    ),
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledTonalIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            playerConnection.seekToNext()
                        },
                        enabled = canSkipNext,
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = textButtonColor,
                                contentColor = iconButtonColor,
                            ),
                        modifier =
                            Modifier
                                .size(width = sideButtonWidth, height = sideButtonHeight)
                                .clip(RoundedCornerShape(32.dp)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }

        PlayerDesignStyle.V7 -> {
            Unit
        }
    }
}

/**
 * Wrapper composable that combines all player control components.
 * This replaces the large inline controlsContent lambda in BottomSheetPlayer
 * to reduce JIT compilation overhead.
 */
@Composable
fun PlayerControlsContent(
    mediaMetadata: MediaMetadata,
    playerDesignStyle: PlayerDesignStyle,
    sliderStyle: SliderStyle,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    repeatMode: Int,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    icBackgroundColor: Color,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    context: Context,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    currentFormat: FormatEntity? = null,
) {
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentSongLiked = currentSong?.song?.liked == true

    val playPauseRoundness by animateDpAsState(
        targetValue = if (isPlaying) 24.dp else 36.dp,
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "playPauseRoundness",
    )

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlayerTitleSection(
                mediaMetadata = mediaMetadata,
                textBackgroundColor = textBackgroundColor,
                navController = navController,
                state = state,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        PlayerTopActions(
            mediaMetadata = mediaMetadata,
            playerDesignStyle = playerDesignStyle,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            textBackgroundColor = textBackgroundColor,
            playerConnection = playerConnection,
            navController = navController,
            menuState = menuState,
            state = state,
            bottomSheetPageState = bottomSheetPageState,
            context = context,
            currentSongLiked = currentSongLiked,
        )
    }

    Spacer(Modifier.height(12.dp))

    PlayerSlider(
        sliderStyle = sliderStyle,
        sliderPosition = sliderPosition,
        position = position,
        duration = duration,
        isPlaying = isPlaying,
        textButtonColor = textButtonColor,
        onValueChange = onSliderValueChange,
        onValueChangeFinished = onSliderValueChangeFinished,
    )

    Spacer(Modifier.height(4.dp))

    PlayerTimeLabel(
        sliderPosition = sliderPosition,
        position = position,
        duration = duration,
        textBackgroundColor = textBackgroundColor,
        showRemainingTime = playerDesignStyle == PlayerDesignStyle.V7,
        centerContent =
            if (playerDesignStyle == PlayerDesignStyle.V7 && currentFormat != null) {
                {
                    val codec = currentFormat.mimeType.substringAfter("/").uppercase()
                    val label =
                        when {
                            codec.contains("FLAC") || codec.contains("ALAC") -> "Lossless"
                            codec.contains("OPUS") -> codec
                            codec.contains("AAC") -> codec
                            codec.contains("MP4A") -> "AAC"
                            codec.contains("VORBIS") -> "Vorbis"
                            else -> codec
                        }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = textBackgroundColor.copy(alpha = 0.12f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.graphic_eq),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = textBackgroundColor.copy(alpha = 0.8f),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = textBackgroundColor.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            } else {
                null
            },
    )

    Spacer(Modifier.height(12.dp))

    PlayerPlaybackControls(
        playerDesignStyle = playerDesignStyle,
        playbackState = playbackState,
        isPlaying = isPlaying,
        isLoading = isLoading,
        repeatMode = repeatMode,
        canSkipPrevious = canSkipPrevious,
        canSkipNext = canSkipNext,
        textButtonColor = textButtonColor,
        iconButtonColor = iconButtonColor,
        textBackgroundColor = textBackgroundColor,
        icBackgroundColor = icBackgroundColor,
        playPauseRoundness = playPauseRoundness,
        playerConnection = playerConnection,
        currentSongLiked = currentSongLiked,
    )
}

@Composable
fun V8PlayerControlsContent(
    mediaMetadata: MediaMetadata,
    queueTitle: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    currentSongLiked: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    volume: Float,
    showVolumeBar: Boolean,
    currentFormat: FormatEntity?,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)
    val onMenuClick =
        remember(mediaMetadata, navController, state, menuState, bottomSheetPageState) {
            {
                menuState.show {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            bottomSheetPageState.show {
                                ShowMediaInfo(mediaMetadata.id)
                            }
                        },
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        }
    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)
    val onTitleClick = titleActions.onTitleClick
    val onArtistClick = titleActions.onArtistClick
    val onPlayPauseClick =
        remember(playbackState, playerConnection) {
            {
                if (playbackState == STATE_ENDED) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.player.togglePlayPause()
                }
            }
        }
    val onToggleLike =
        remember(playerConnection) {
            { playerConnection.toggleLike() }
        }
    val onPreviousClick =
        remember(playerConnection) {
            { playerConnection.seekToPrevious() }
        }
    val onNextClick =
        remember(playerConnection) {
            { playerConnection.seekToNext() }
        }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val horizontalPadding =
            if (landscape) {
                36.dp
            } else if (maxWidth < 380.dp) {
                20.dp
            } else {
                24.dp
            }
        val subtitle = queueTitle ?: mediaMetadata.album?.title.orEmpty()

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = secondaryForeground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                )

                Spacer(Modifier.height(14.dp))
            }

            V8MetadataActions(
                title = mediaMetadata.title,
                explicit = mediaMetadata.explicit,
                artists = mediaMetadata.artists,
                liked = currentSongLiked,
                foreground = foreground,
                onMenuClick = onMenuClick,
                onToggleLike = onToggleLike,
                onTitleClick = onTitleClick,
                onArtistClick = onArtistClick,
            )

            Spacer(Modifier.height(16.dp))

            V8PlaybackProgress(
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                currentFormat = currentFormat,
                foreground = foreground,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )

            Spacer(Modifier.height(16.dp))

            V8TransportControls(
                playbackState = playbackState,
                isPlaying = isPlaying,
                isLoading = isLoading,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                foreground = foreground,
                onPreviousClick = onPreviousClick,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
            )

            if (showVolumeBar) {
                Spacer(Modifier.height(12.dp))

                V8VolumeControls(
                    volume = volume,
                    foreground = foreground,
                    secondaryForeground = secondaryForeground,
                    onVolumeChange = onVolumeChange,
                )
            }
        }
    }
}

@Composable
private fun V8MetadataActions(
    title: String,
    explicit: Boolean,
    artists: List<MediaMetadata.Artist>,
    liked: Boolean,
    foreground: Color,
    onMenuClick: () -> Unit,
    onToggleLike: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlayerTitleText(
                title = title,
                explicit = explicit,
                color = foreground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .basicMarquee()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onTitleClick,
                        ),
            )
            ClickableArtists(
                artists = artists,
                onArtistClick = onArtistClick,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = foreground.copy(alpha = 0.72f),
                modifier = Modifier.basicMarquee(),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(foreground.copy(alpha = 0.20f))
                        .clickable(onClick = onMenuClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = stringResource(R.string.more_options),
                    tint = foreground,
                    modifier = Modifier.size(24.dp),
                )
            }

            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(foreground.copy(alpha = 0.20f))
                        .clickable(onClick = onToggleLike),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (liked) R.drawable.favorite else R.drawable.favorite_border,
                        ),
                    contentDescription = stringResource(R.string.action_like),
                    tint = foreground,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V8PlaybackProgress(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    currentFormat: FormatEntity?,
    foreground: Color,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val safeDuration = if (duration <= 0L || duration == C.TIME_UNSET) 0f else duration.toFloat()
    val safeValue = (sliderPosition ?: position).toFloat().coerceIn(0f, safeDuration.coerceAtLeast(0f))

    val trackInteractionSource = remember { MutableInteractionSource() }
    val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
    val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
    val isTrackActive = isTrackDragged || isTrackPressed

    val trackHeight by animateDpAsState(
        targetValue = if (isTrackActive) 16.dp else 10.dp,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "v8TrackHeight",
    )

    val sliderColors =
        SliderDefaults.colors(
            activeTrackColor = foreground.copy(alpha = 0.88f),
            inactiveTrackColor = foreground.copy(alpha = 0.28f),
            thumbColor = Color.Transparent,
        )

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = safeValue,
            valueRange = 0f..safeDuration.coerceAtLeast(0f),
            onValueChange = { onSliderValueChange(it.toLong()) },
            onValueChangeFinished = onSliderValueChangeFinished,
            enabled = safeDuration > 0f,
            interactionSource = trackInteractionSource,
            colors = sliderColors,
            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
            track = { sliderState ->
                PlayerSliderTrack(
                    sliderState = sliderState,
                    colors = sliderColors,
                    trackHeight = trackHeight,
                )
            },
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )

        Spacer(Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
        ) {
            Text(
                text = makeTimeString(sliderPosition ?: position),
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Text(
                text =
                    if (duration != C.TIME_UNSET && duration > 0L) {
                        val remaining = duration - (sliderPosition ?: position)
                        "-${makeTimeString(remaining.coerceAtLeast(0L))}"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun V8TransportControls(
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    foreground: Color,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ResizableIconButton(
                icon = R.drawable.apple_skip_previous,
                enabled = canSkipPrevious,
                color = foreground,
                modifier = Modifier.size(48.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPreviousClick()
                },
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier =
                Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPauseClick()
                    },
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(54.dp),
                    color = foreground,
                )
            } else {
                Image(
                    painter =
                        painterResource(
                            when {
                                playbackState == STATE_ENDED -> R.drawable.replay
                                isPlaying -> R.drawable.pause_applemusic
                                else -> R.drawable.play_applemusic
                            },
                        ),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(foreground),
                    modifier = Modifier.size(72.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ResizableIconButton(
                icon = R.drawable.apple_skip_next,
                enabled = canSkipNext,
                color = foreground,
                modifier = Modifier.size(48.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNextClick()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V8VolumeControls(
    volume: Float,
    foreground: Color,
    secondaryForeground: Color,
    onVolumeChange: (Float) -> Unit,
) {
    val volumeInteractionSource = remember { MutableInteractionSource() }
    val isVolumeDragged by volumeInteractionSource.collectIsDraggedAsState()
    val isVolumePressed by volumeInteractionSource.collectIsPressedAsState()
    val isVolumeActive = isVolumeDragged || isVolumePressed

    val volumeTrackHeight by animateDpAsState(
        targetValue = if (isVolumeActive) 16.dp else 10.dp,
        animationSpec =
            spring(
                dampingRatio = 0.7f,
                stiffness = 600f,
            ),
        label = "v8VolumeTrackHeight",
    )

    val volumeColors =
        SliderDefaults.colors(
            activeTrackColor = foreground.copy(alpha = 0.88f),
            inactiveTrackColor = foreground.copy(alpha = 0.28f),
            thumbColor = Color.Transparent,
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.volume_off),
            contentDescription = stringResource(R.string.minimum_volume),
            tint = secondaryForeground,
            modifier = Modifier.size(24.dp),
        )

        Slider(
            value = volume.coerceIn(0f, 1f),
            valueRange = 0f..1f,
            onValueChange = { onVolumeChange(it.coerceIn(0f, 1f)) },
            onValueChangeFinished = {},
            interactionSource = volumeInteractionSource,
            colors = volumeColors,
            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
            track = { sliderState ->
                PlayerSliderTrack(
                    sliderState = sliderState,
                    colors = volumeColors,
                    trackHeight = volumeTrackHeight,
                )
            },
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
                    .height(28.dp),
        )

        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = stringResource(R.string.maximum_volume),
            tint = secondaryForeground,
            modifier = Modifier.size(24.dp),
        )
    }
}



@Composable
fun PlayerBackground(
    playerBackground: PlayerBackgroundStyle,
    mediaMetadata: MediaMetadata?,
    gradientColors: List<Color>,
    disableBlur: Boolean,
    blurRadius: Float,
    playerCustomImageUri: String,
    playerCustomBlur: Float,
    playerCustomContrast: Float,
    playerCustomBrightness: Float,
) {
    val effectiveBlurRadius = blurRadius.coerceIn(0f, PlayerBackgroundMaxBlurRadius)
    val shouldApplyBlur = !disableBlur && effectiveBlurRadius > 0f

    val backgroundSwapState =
        rememberThumbnailSwapState(
            videoId = mediaMetadata?.id,
            ytmUrl = mediaMetadata?.thumbnailUrl,
            lowDataMode = rememberLowDataModeActive(),
            isMusicVideo = mediaMetadata?.isMusicVideo ?: false,
        )
    val backgroundThumbnailUrl = backgroundSwapState.displayUrl
    val styleAppliesBlur =
        effectiveBlurRadius > 0f && effectiveBlurRadius >= 0.5f
    Box(modifier = Modifier.fillMaxSize()) {
        when (playerBackground) {
            PlayerBackgroundStyle.BLUR -> {
                AnimatedContent(
                    targetState = backgroundThumbnailUrl,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { thumbnailUrl ->
                    if (thumbnailUrl != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = thumbnailUrl.highRes(),
                                contentDescription = "Blurred background",
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier.fillMaxSize().let {
                                        if (styleAppliesBlur) it.blur(radius = effectiveBlurRadius.dp) else it
                                    },
                            )
                            val overlayStops = PlayerBackgroundColorUtils.buildBlurOverlayStops(gradientColors)
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(colorStops = overlayStops)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.08f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.GRADIENT -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val gradientColorStops =
                                if (colors.size >= 3) {
                                    arrayOf(
                                        0.0f to colors[0].copy(alpha = 0.92f), // Top: primary vibrant color
                                        0.5f to colors[1].copy(alpha = 0.75f), // Middle: darker variant
                                        1.0f to colors[2].copy(alpha = 0.65f), // Bottom: black-ish
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to colors[0].copy(alpha = 0.9f), // Top: primary color
                                        0.6f to colors[0].copy(alpha = 0.55f), // Middle: faded variant
                                        1.0f to Color.Black.copy(alpha = 0.7f), // Bottom: black
                                    )
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops)),
                            )
                            // Keep a gentle dark overlay to ensure text contrast on bright artwork
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.COLORING -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        val baseColor = PlayerBackgroundColorUtils.ensureComfortableColor(colors.first())
                        val gradientStops = PlayerBackgroundColorUtils.buildColoringStops(baseColor)
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize().background(baseColor))
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(colorStops = gradientStops)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.25f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.BLUR_GRADIENT -> {
                AnimatedContent(
                    targetState = backgroundThumbnailUrl,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { thumbnailUrl ->
                    if (thumbnailUrl != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = thumbnailUrl.highRes(),
                                contentDescription = "Blurred background",
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier.fillMaxSize().let {
                                        if (styleAppliesBlur) it.blur(radius = effectiveBlurRadius.dp) else it
                                    },
                            )
                            val gradientColorStops =
                                PlayerBackgroundColorUtils.buildBlurGradientStops(gradientColors)
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.05f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.CUSTOM -> {
                AnimatedContent(
                    targetState = playerCustomImageUri,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { uri ->
                    if (uri.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val blurPx = playerCustomBlur
                            val contrastVal = playerCustomContrast
                            val brightnessVal = playerCustomBrightness

                            val t = (1f - contrastVal) * 128f + (brightnessVal - 1f) * 255f
                            val matrix =
                                floatArrayOf(
                                    contrastVal,
                                    0f,
                                    0f,
                                    0f,
                                    t,
                                    0f,
                                    contrastVal,
                                    0f,
                                    0f,
                                    t,
                                    0f,
                                    0f,
                                    contrastVal,
                                    0f,
                                    t,
                                    0f,
                                    0f,
                                    0f,
                                    1f,
                                    0f,
                                )

                            val cm = ColorMatrix(matrix)

                            AsyncImage(
                                model = Uri.parse(uri),
                                contentDescription = "Custom background",
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier.fillMaxSize().let {
                                        if (disableBlur) it else it.blur(radius = blurPx.dp)
                                    },
                                colorFilter = ColorFilter.colorMatrix(cm),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.GLOW -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                    },
                    label = "",
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .drawWithCache {
                                        val width = size.width
                                        val height = size.height

                                        // Use a dark base, but the gradients will cover most of it
                                        val baseColor = Color(0xFF050505)

                                        // Extract up to 6 colors
                                        val color1 = colors.getOrElse(0) { Color.DarkGray }
                                        val color2 = colors.getOrElse(1) { color1 }
                                        val color3 = colors.getOrElse(2) { color2 }
                                        val color4 = colors.getOrElse(3) { color1 }
                                        val color5 = colors.getOrElse(4) { color2 }
                                        val color6 = colors.getOrElse(5) { color3 }

                                        // Top-Left Large Glow (Primary)
                                        val brush1 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color1.copy(alpha = 0.8f),
                                                        color1.copy(alpha = 0.5f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.2f, height * 0.25f),
                                                radius = width * 1.2f,
                                            )

                                        // Bottom-Right Large Glow (Secondary)
                                        val brush2 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color2.copy(alpha = 0.75f),
                                                        color2.copy(alpha = 0.45f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.85f, height * 0.8f),
                                                radius = width * 1.1f,
                                            )

                                        // Top-Right Glow (Tertiary)
                                        val brush3 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color3.copy(alpha = 0.7f),
                                                        color3.copy(alpha = 0.4f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.9f, height * 0.15f),
                                                radius = width * 1.0f,
                                            )

                                        // Bottom-Left (Quaternary)
                                        val brush4 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color4.copy(alpha = 0.65f),
                                                        color4.copy(alpha = 0.35f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.1f, height * 0.9f),
                                                radius = width * 1.0f,
                                            )

                                        // Top-Center (Quinary)
                                        val brush5 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color5.copy(alpha = 0.6f),
                                                        color5.copy(alpha = 0.3f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.5f, height * 0.1f),
                                                radius = width * 0.9f,
                                            )

                                        // Bottom-Center (Senary)
                                        val brush6 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color6.copy(alpha = 0.6f),
                                                        color6.copy(alpha = 0.3f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.5f, height * 0.95f),
                                                radius = width * 0.9f,
                                            )

                                        onDrawBehind {
                                            drawRect(color = baseColor)
                                            drawRect(brush = brush1)
                                            drawRect(brush = brush2)
                                            drawRect(brush = brush3)
                                            drawRect(brush = brush4)
                                            drawRect(brush = brush5)
                                            drawRect(brush = brush6)
                                        }
                                    },
                        )
                    }
                }
            }

            PlayerBackgroundStyle.GLOW_ANIMATED -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                    },
                    label = "GlowAnimatedContent",
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        val infiniteTransition = rememberInfiniteTransition(label = "GlowAnimation")

                        val progress by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation = tween(20000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart,
                                ),
                            label = "glowProgress",
                        )

                        fun rotatedColorAt(index: Int): Color {
                            val size = colors.size
                            val idx = index.toFloat() + progress * size
                            val a = kotlin.math.floor(idx).toInt() % size
                            val b = (a + 1) % size
                            val frac = idx - kotlin.math.floor(idx)
                            return androidx.compose.ui.graphics.lerp(
                                colors.getOrElse(a) { Color.DarkGray },
                                colors.getOrElse(b) { Color.DarkGray },
                                frac,
                            )
                        }

                        fun oscillate(
                            min: Float,
                            max: Float,
                            phase: Float,
                            speed: Float = 1f,
                        ): Float {
                            // speed MUST be an integer to ensure seamless looping when progress wraps from 1f to 0f.
                            val v = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * (progress * speed + phase)).toFloat()
                            return min + (max - min) * ((v + 1f) * 0.5f)
                        }

                        val color1 = rotatedColorAt(0)
                        val color2 = rotatedColorAt(1)
                        val color3 = rotatedColorAt(2)
                        val color4 = rotatedColorAt(3)
                        val color5 = rotatedColorAt(4)
                        val color6 = rotatedColorAt(5)

                        val o1x = oscillate(0.0f, 1.0f, 0.00f, 1.0f)
                        val o1y = oscillate(0.0f, 0.5f, 0.07f, 1.0f)
                        val r1 = oscillate(0.8f, 1.6f, 0.12f, 1.0f)

                        val o2x = oscillate(1.0f, 0.0f, 0.2f, 1.0f)
                        val o2y = oscillate(0.5f, 1.0f, 0.25f, 1.0f)
                        val r2 = oscillate(0.7f, 1.5f, 0.18f, 1.0f)

                        val o3x = oscillate(0.2f, 0.8f, 0.33f, 1.0f)
                        val o3y = oscillate(0.8f, 0.2f, 0.36f, 1.0f)
                        val r3 = oscillate(0.6f, 1.4f, 0.29f, 1.0f)

                        val o4x = oscillate(0.3f, 0.7f, 0.44f, 1.0f)
                        val o4y = oscillate(0.2f, 0.8f, 0.41f, 1.0f)
                        val r4 = oscillate(0.9f, 1.7f, 0.47f, 1.0f)

                        val o5x = oscillate(0.4f, 0.6f, 0.55f, 1.0f)
                        val o5y = oscillate(0.0f, 1.0f, 0.51f, 1.0f)
                        val r5 = oscillate(0.7f, 1.5f, 0.58f, 1.0f)

                        val o6x = oscillate(0.0f, 1.0f, 0.66f, 1.0f)
                        val o6y = oscillate(0.5f, 0.7f, 0.62f, 1.0f)
                        val r6 = oscillate(0.8f, 1.8f, 0.69f, 1.0f)

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .drawWithCache {
                                        val width = size.width
                                        val height = size.height
                                        val baseColor = Color(0xFF050505)

                                        val brush1 =
                                            Brush.radialGradient(
                                                colors = listOf(color1.copy(alpha = 0.85f), color1.copy(alpha = 0.5f), Color.Transparent),
                                                center = Offset(width * o1x, height * o1y),
                                                radius = width * r1,
                                            )
                                        val brush2 =
                                            Brush.radialGradient(
                                                colors = listOf(color2.copy(alpha = 0.8f), color2.copy(alpha = 0.45f), Color.Transparent),
                                                center = Offset(width * o2x, height * o2y),
                                                radius = width * r2,
                                            )
                                        val brush3 =
                                            Brush.radialGradient(
                                                colors = listOf(color3.copy(alpha = 0.75f), color3.copy(alpha = 0.4f), Color.Transparent),
                                                center = Offset(width * o3x, height * o3y),
                                                radius = width * r3,
                                            )
                                        val brush4 =
                                            Brush.radialGradient(
                                                colors = listOf(color4.copy(alpha = 0.7f), color4.copy(alpha = 0.35f), Color.Transparent),
                                                center = Offset(width * o4x, height * o4y),
                                                radius = width * r4,
                                            )
                                        val brush5 =
                                            Brush.radialGradient(
                                                colors = listOf(color5.copy(alpha = 0.65f), color5.copy(alpha = 0.3f), Color.Transparent),
                                                center = Offset(width * o5x, height * o5y),
                                                radius = width * r5,
                                            )
                                        val brush6 =
                                            Brush.radialGradient(
                                                colors = listOf(color6.copy(alpha = 0.6f), color6.copy(alpha = 0.25f), Color.Transparent),
                                                center = Offset(width * o6x, height * o6y),
                                                radius = width * r6,
                                            )

                                        onDrawBehind {
                                            drawRect(color = baseColor)
                                            drawRect(brush = brush1)
                                            drawRect(brush = brush2)
                                            drawRect(brush = brush3)
                                            drawRect(brush = brush4)
                                            drawRect(brush = brush5)
                                            drawRect(brush = brush6)
                                        }
                                    },
                        )
                    }
                }
            }

            else -> {
                // DEFAULT or other modes - no background
            }
        }
    }
}




