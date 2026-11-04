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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import moe.rgsekai.sekaitune.playback.sources.CandidateQualityRank
import moe.rgsekai.sekaitune.playback.sources.AudioPipelineStatus
import moe.rgsekai.sekaitune.playback.sources.AudioQualityManager
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeInfo
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeState
import moe.rgsekai.sekaitune.playback.stream.ResolveAudioStreamUseCase
import moe.rgsekai.sekaitune.utils.isLocalMediaId
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
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
import moe.rgsekai.sekaitune.ui.component.ResizableIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import moe.rgsekai.sekaitune.canvas.models.CanvasArtwork
import moe.rgsekai.sekaitune.canvas.ProceduralCanvasStyle
import moe.rgsekai.sekaitune.constants.CanvasAudioReactiveKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralFallbackKey
import moe.rgsekai.sekaitune.constants.CanvasProceduralStyleKey
import moe.rgsekai.sekaitune.constants.EnableHapticFeedbackKey
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.constants.PlayerHorizontalPadding
import moe.rgsekai.sekaitune.constants.SekaiTuneCanvasKey
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.db.entities.codecLabel
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.extensions.toggleRepeatMode
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.ui.component.BottomSheetPageState
import moe.rgsekai.sekaitune.ui.component.BottomSheetState
import moe.rgsekai.sekaitune.ui.component.MenuState
import moe.rgsekai.sekaitune.ui.component.GlassComponent
import moe.rgsekai.sekaitune.ui.component.LocalGlassEffectConfig
import moe.rgsekai.sekaitune.ui.component.OneLineLyrics
import moe.rgsekai.sekaitune.ui.component.isGlassSupported
import moe.rgsekai.sekaitune.ui.component.liquidGlass
import moe.rgsekai.sekaitune.ui.component.PlayerSliderTrack
import moe.rgsekai.sekaitune.ui.component.rememberDelayedBufferingState
import moe.rgsekai.sekaitune.ui.component.ResizableIconButton
import moe.rgsekai.sekaitune.ui.menu.PlayerMenu
import moe.rgsekai.sekaitune.ui.utils.ShowMediaInfo
import moe.rgsekai.sekaitune.ui.utils.highRes
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberLowDataModeActive
import moe.rgsekai.sekaitune.utils.rememberPreference

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
        )    AnimatedContent(
        targetState = mediaMetadata.title,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "",
    ) { title ->
        PlayerTitleText(
            title = title,
            explicit = mediaMetadata.explicit,
            color = textBackgroundColor,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.5.sp,
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

    Spacer(Modifier.height(2.dp))

    ClickableArtists(
        artists = mediaMetadata.artists,
        onArtistClick = actions.onArtistClick,
        style = MaterialTheme.typography.bodyMedium.copy(color = textBackgroundColor.copy(alpha = 0.72f), fontSize = 14.5.sp),
        onLongClick = actions.onCopyArtists,
        modifier =
            Modifier
                .fillMaxWidth()
                .basicMarquee()
                .padding(end = 12.dp),
    )
}

@Composable
fun PlayerTimeLabel(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    showRemainingTime: Boolean = false,
    modifier: Modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 4.dp),
    centerContent: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier,
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

fun deriveDisplayBadge(
    badgeInfo: PlaybackQualityBadgeInfo,
    currentFormat: FormatEntity?,
    currentMediaId: String?,
): PlaybackQualityBadgeInfo {
    val isFormatFlacOrLossless = currentFormat != null && (
        currentFormat.mimeType.contains("flac", ignoreCase = true) ||
        currentFormat.mimeType.contains("alac", ignoreCase = true) ||
        currentFormat.mimeType.contains("wav", ignoreCase = true)
    )

    if (currentMediaId != null && badgeInfo.mediaId == currentMediaId) {
        when (badgeInfo.state) {
            PlaybackQualityBadgeState.CHECKING -> return badgeInfo
            PlaybackQualityBadgeState.UPGRADING -> return badgeInfo
            PlaybackQualityBadgeState.FINAL -> {
                if (badgeInfo.label.isNotBlank()) {
                    val isUpgradedOrExternal = badgeInfo.pipelineStatus == AudioPipelineStatus.UPGRADED_FROM_OPUS ||
                        (badgeInfo.sourceName.isNotBlank() && !badgeInfo.sourceName.equals("YouTube", ignoreCase = true))
                    if (badgeInfo.isLossless && currentFormat != null && !isUpgradedOrExternal) {
                        val rawMime = currentFormat.mimeType.uppercase()
                        val rawCodecs = currentFormat.codecs.uppercase()
                        val actuallyLossless = rawMime.contains("FLAC") || rawCodecs.contains("FLAC") ||
                            rawMime.contains("ALAC") || rawCodecs.contains("ALAC") ||
                            rawMime.contains("WAV") || rawCodecs.contains("WAV")
                        if (!actuallyLossless) {
                            val (fallbackLabel, _) = CandidateQualityRank.computeFinalQualityLabel(
                                mimeType = currentFormat.mimeType,
                                codecs = currentFormat.codecs,
                                bitrateBps = currentFormat.bitrate,
                                sampleRateHz = currentFormat.sampleRate,
                            )
                            return badgeInfo.copy(label = fallbackLabel, isLossless = false, isHiRes = false)
                        }
                    }

                    // Dynamically resolve actual source if badgeInfo mistakenly fell back to "YouTube" on a lossless/addon stream
                    if (isFormatFlacOrLossless || badgeInfo.isLossless) {
                        if (badgeInfo.sourceName.isBlank() || badgeInfo.sourceName.equals("YouTube", ignoreCase = true)) {
                            val winning = ResolveAudioStreamUseCase.getWinningSource(currentMediaId)
                            val record = AudioQualityManager.getSourceRecord(currentMediaId)
                            val trueSource = record?.sourceName?.takeIf { it.isNotBlank() && !it.equals("YouTube", ignoreCase = true) }
                                ?: winning?.sourceName
                                ?: AudioQualityManager.getDefaultAddonName()
                                ?: "Lossless Source"
                            val trueStatus = if (badgeInfo.pipelineStatus == AudioPipelineStatus.SETTLED_NO_MATCH || badgeInfo.pipelineStatus == null) {
                                record?.pipelineStatus ?: AudioPipelineStatus.UPGRADED_FROM_OPUS
                            } else {
                                badgeInfo.pipelineStatus
                            }
                            return badgeInfo.copy(
                                sourceName = trueSource,
                                pipelineStatus = trueStatus,
                            )
                        }
                    } else if (currentMediaId.startsWith("webdav:")) {
                        if (badgeInfo.sourceName.isBlank() || badgeInfo.sourceName.equals("YouTube", ignoreCase = true)) {
                            return badgeInfo.copy(
                                sourceName = "WebDAV",
                                pipelineStatus = null,
                                statusText = "Playing from WebDAV server",
                            )
                        }
                    } else if (currentMediaId.isLocalMediaId() || currentMediaId.startsWith("content://") || currentMediaId.startsWith("file://")) {
                        if (badgeInfo.sourceName.isBlank() || badgeInfo.sourceName.equals("YouTube", ignoreCase = true)) {
                            return badgeInfo.copy(
                                sourceName = "Local Storage",
                                pipelineStatus = null,
                                statusText = "Playing from local device storage",
                            )
                        }
                    } else {
                        if (!badgeInfo.sourceName.equals("YouTube", ignoreCase = true)) {
                            val correctedStatus = if (badgeInfo.pipelineStatus == AudioPipelineStatus.PRELOADED_INSTANT || badgeInfo.pipelineStatus == AudioPipelineStatus.UPGRADED_FROM_OPUS) {
                                AudioPipelineStatus.SETTLED_NO_MATCH
                            } else {
                                badgeInfo.pipelineStatus ?: AudioPipelineStatus.SETTLED_NO_MATCH
                            }
                            return badgeInfo.copy(
                                sourceName = "YouTube",
                                pipelineStatus = correctedStatus,
                                isLossless = false,
                                isHiRes = false,
                            )
                        }
                    }

                    return badgeInfo
                }
            }
        }
    }

    if (currentFormat != null) {
        val (label, isLossless) = CandidateQualityRank.computeFinalQualityLabel(
            mimeType = currentFormat.mimeType,
            codecs = currentFormat.codecs,
            bitrateBps = currentFormat.bitrate,
            sampleRateHz = currentFormat.sampleRate,
        )
        val isHiRes = isLossless && ((currentFormat.sampleRate ?: 0) > 48000)
        val formatStr = when {
            currentFormat.mimeType.contains("flac", ignoreCase = true) -> "FLAC"
            currentFormat.mimeType.contains("alac", ignoreCase = true) -> "ALAC"
            currentFormat.mimeType.contains("wav", ignoreCase = true) -> "WAV"
            currentFormat.mimeType.contains("opus", ignoreCase = true) -> "Opus"
            currentFormat.mimeType.contains("mp4", ignoreCase = true) || currentFormat.mimeType.contains("aac", ignoreCase = true) -> "AAC"
            else -> currentFormat.mimeType.substringAfter("audio/").uppercase()
        }
        val bitDepthStr = if (isHiRes) "24-bit" else "16-bit"
        val sampleRateStr = currentFormat.sampleRate?.let { String.format(java.util.Locale.US, "%.1f kHz", it / 1000.0) } ?: "48.0 kHz"
        val bitrateStr = currentFormat.bitrate?.let { "${it / 1000} kbps" } ?: "149 kbps"
        val transferSizeStr = currentFormat.contentLength?.let {
            String.format(java.util.Locale.US, "%.1f MB", it / (1024.0 * 1024.0))
        } ?: ""

        val sourceRecord = currentMediaId?.let { AudioQualityManager.getSourceRecord(it) }
        val winningSource = currentMediaId?.let { ResolveAudioStreamUseCase.getWinningSource(it) }

        val (resolvedSource, resolvedStatus, resolvedStatusText) = when {
            currentMediaId?.startsWith("webdav:") == true -> {
                Triple("WebDAV", null, "Playing from WebDAV server")
            }
            currentMediaId != null && (currentMediaId.isLocalMediaId() || currentMediaId.startsWith("content://") || currentMediaId.startsWith("file://")) -> {
                Triple("Local Storage", null, "Playing from local device storage")
            }
            currentMediaId?.startsWith("addon:") == true -> {
                val addonName = AudioQualityManager.resolveAddonDisplayName(currentMediaId) ?: "External Addon"
                Triple(addonName, AudioPipelineStatus.PRELOADED_INSTANT, "Instant playback from $addonName")
            }
            !isLossless && !isFormatFlacOrLossless -> {
                val correctedStatus = if (sourceRecord?.pipelineStatus == AudioPipelineStatus.PRELOADED_INSTANT || sourceRecord?.pipelineStatus == AudioPipelineStatus.UPGRADED_FROM_OPUS) {
                    AudioPipelineStatus.SETTLED_NO_MATCH
                } else {
                    sourceRecord?.pipelineStatus ?: AudioPipelineStatus.SETTLED_NO_MATCH
                }
                Triple("YouTube", correctedStatus, "")
            }
            sourceRecord != null && sourceRecord.sourceName.isNotBlank() && !sourceRecord.sourceName.equals("YouTube", ignoreCase = true) -> {
                Triple(sourceRecord.sourceName, sourceRecord.pipelineStatus, sourceRecord.statusText)
            }
            winningSource != null -> {
                val statusText = if (winningSource.sourceName == "WebDAV") "From WebDAV" else ""
                Triple(winningSource.sourceName, AudioPipelineStatus.UPGRADED_FROM_OPUS, statusText)
            }
            isLossless || formatStr == "FLAC" || formatStr == "ALAC" || formatStr == "WAV" -> {
                val defaultAddon = AudioQualityManager.getDefaultAddonName() ?: "Lossless Source"
                Triple(defaultAddon, AudioPipelineStatus.UPGRADED_FROM_OPUS, "")
            }
            else -> {
                Triple("YouTube", AudioPipelineStatus.SETTLED_NO_MATCH, "")
            }
        }

        return PlaybackQualityBadgeInfo(
            mediaId = currentMediaId,
            state = PlaybackQualityBadgeState.FINAL,
            label = label,
            isLossless = isLossless,
            isHiRes = isHiRes,
            details = sourceRecord?.details ?: winningSource?.formatDescription ?: "",
            sourceName = resolvedSource,
            format = formatStr,
            bitDepth = bitDepthStr,
            sampleRate = sampleRateStr,
            bitrate = bitrateStr,
            transferSize = transferSizeStr,
            statusText = resolvedStatusText,
            pipelineStatus = resolvedStatus,
        )
    }

    return badgeInfo
}

@Composable
fun PlaybackQualityBadge(
    badgeInfo: PlaybackQualityBadgeInfo,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (badgeInfo.label.isBlank()) return

    Surface(
        shape = CircleShape,
        color = textColor.copy(alpha = 0.12f),
        modifier = modifier
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
    ) {
        Crossfade(
            targetState = badgeInfo,
            animationSpec = tween(durationMillis = 250),
            label = "qualityBadgeCrossfade",
        ) { info ->
            when (info.state) {
                PlaybackQualityBadgeState.CHECKING -> {
                    val transition = rememberInfiniteTransition(label = "checkingPulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.45f,
                        targetValue = 0.95f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "checkingAlpha",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quality_checking),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = alpha),
                            maxLines = 1,
                        )
                        if (onClick != null) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(12.dp)
                                    .alpha(0.6f),
                                tint = textColor,
                            )
                        }
                    }
                }
                PlaybackQualityBadgeState.UPGRADING -> {
                    val transition = rememberInfiniteTransition(label = "upgradingPulse")
                    val alpha by transition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "upgradingAlpha",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.graphic_eq),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = textColor.copy(alpha = alpha),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.quality_upgrading),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = alpha),
                            maxLines = 1,
                        )
                        if (onClick != null) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(12.dp)
                                    .alpha(0.6f),
                                tint = textColor,
                            )
                        }
                    }
                }
                PlaybackQualityBadgeState.FINAL -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.graphic_eq),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = textColor.copy(alpha = 0.8f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = info.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                        if (onClick != null) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(12.dp)
                                    .alpha(0.6f),
                                tint = textColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun V8PlayerControlsContent(
    mediaMetadata: MediaMetadata,
    queueTitle: String?,
    playbackState: Int,
    isPlaying: Boolean,
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
    lyricsSyncOffset: Int = 0,
    showOneLineLyrics: Boolean = true,
    onShowLyrics: () -> Unit = {},
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)
    val qualityBadgeInfo by playerConnection.qualityBadgeFlow.collectAsState()
    var showPipelineDetails by rememberSaveable { mutableStateOf(false) }
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

            OneLineLyrics(
                mediaMetadata = mediaMetadata,
                position = position,
                sliderPosition = sliderPosition,
                lyricsSyncOffset = lyricsSyncOffset,
                showOneLineLyrics = showOneLineLyrics,
                onShowLyrics = onShowLyrics,
                textColor = foreground,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
            )

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

            Spacer(Modifier.height(2.dp))

            V8PlaybackProgress(
                mediaId = mediaMetadata.id,
                playbackState = playbackState,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                currentFormat = currentFormat,
                foreground = foreground,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                qualityBadgeInfo = qualityBadgeInfo,
                onBadgeClick = { showPipelineDetails = true },
            )

            Spacer(Modifier.height(16.dp))

            V8TransportControls(
                playbackState = playbackState,
                isPlaying = isPlaying,
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

    if (showPipelineDetails) {
        val displayBadge = remember(qualityBadgeInfo, currentFormat, mediaMetadata.id) {
            deriveDisplayBadge(qualityBadgeInfo, currentFormat, mediaMetadata.id)
        }
        AudioPipelineDetailsBottomSheet(
            onDismiss = { showPipelineDetails = false },
            badgeInfo = displayBadge,
            currentFormat = currentFormat,
            artworkUrl = mediaMetadata.thumbnailUrl,
            isPlaying = isPlaying,
        )
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PlayerTitleText(
                title = title,
                explicit = explicit,
                color = foreground,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 18.5.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .weight(1f)
                        .basicMarquee()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onTitleClick,
                        ),
            )

            Spacer(Modifier.width(16.dp))

            val useGlass = rememberPlayerGlassEnabled()

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(
                                if (useGlass) {
                                    Modifier.playerGlass(CircleShape)
                                } else {
                                    Modifier.background(foreground.copy(alpha = 0.20f))
                                },
                            )
                            .clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.more_options),
                        tint = foreground,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(
                                if (useGlass) {
                                    Modifier.playerGlass(CircleShape)
                                } else {
                                    Modifier.background(foreground.copy(alpha = 0.20f))
                                },
                            )
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
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        ClickableArtists(
            artists = artists,
            onArtistClick = onArtistClick,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
            color = foreground.copy(alpha = 0.72f),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V8PlaybackProgress(
    mediaId: String?,
    playbackState: Int,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    currentFormat: FormatEntity?,
    foreground: Color,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    qualityBadgeInfo: PlaybackQualityBadgeInfo = PlaybackQualityBadgeInfo(),
    onBadgeClick: (() -> Unit)? = null,
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

    val isBuffering =
        rememberDelayedBufferingState(
            playbackState = playbackState,
            mediaId = mediaId,
            delayMillis = 1000L,
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
                    isBuffering = isBuffering && sliderPosition == null,
                )
            },
            modifier = Modifier.fillMaxWidth().height(28.dp),
        )

        Spacer(Modifier.height(4.dp))

        val displayBadgeInfo = remember(qualityBadgeInfo, currentFormat, mediaId) {
            deriveDisplayBadge(qualityBadgeInfo, currentFormat, mediaId)
        }

        PlayerTimeLabel(
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = foreground,
            showRemainingTime = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            centerContent =
                if (displayBadgeInfo.label.isNotBlank()) {
                    {
                        PlaybackQualityBadge(
                            badgeInfo = displayBadgeInfo,
                            textColor = foreground,
                            onClick = onBadgeClick,
                        )
                    }
                } else {
                    null
                },
        )
    }
}

@Composable
private fun V8TransportControls(
    playbackState: Int,
    isPlaying: Boolean,
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
                color = foreground,
                enabled = canSkipPrevious,
                modifier = Modifier.size(52.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPreviousClick()
                },
            )
        }

        Spacer(Modifier.width(8.dp))

        ResizableIconButton(
            icon =
                when {
                    playbackState == STATE_ENDED -> R.drawable.replay
                    isPlaying -> R.drawable.pause_applemusic
                    else -> R.drawable.play_applemusic
                },
            color = foreground,
            modifier = Modifier.size(76.dp),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlayPauseClick()
            },
        )

        Spacer(Modifier.width(8.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ResizableIconButton(
                icon = R.drawable.apple_skip_next,
                color = foreground,
                enabled = canSkipNext,
                modifier = Modifier.size(52.dp),
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



/** True when the "Glass Player" option (and the global glass switch) is on and the device supports it. */
@Composable
internal fun rememberPlayerGlassEnabled(): Boolean {
    val config = LocalGlassEffectConfig.current
    return config.isEnabledFor(GlassComponent.PLAYER) && isGlassSupported()
}

/** Applies the liquid glass effect to a player control. No-op when the glass player is disabled. */
@Composable
internal fun Modifier.playerGlass(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val config = LocalGlassEffectConfig.current
    return if (config.isEnabledFor(GlassComponent.PLAYER) && isGlassSupported()) {
        this.liquidGlass(config = config, shape = shape)
    } else {
        this
    }
}
