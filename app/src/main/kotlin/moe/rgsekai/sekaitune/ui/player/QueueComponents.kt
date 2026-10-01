/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import moe.rgsekai.sekaitune.models.QueueFilter
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import moe.rgsekai.sekaitune.ui.component.GlassComponent
import moe.rgsekai.sekaitune.ui.component.LocalGlassEffectConfig
import moe.rgsekai.sekaitune.ui.component.isGlassSupported
import moe.rgsekai.sekaitune.ui.component.liquidGlass

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.EnableHapticFeedbackKey
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.db.entities.containerLabel
import moe.rgsekai.sekaitune.db.entities.formattedBitrate
import moe.rgsekai.sekaitune.db.entities.formattedFileSize
import moe.rgsekai.sekaitune.db.entities.formattedSampleRate
import moe.rgsekai.sekaitune.models.ActiveOutputDevice
import moe.rgsekai.sekaitune.models.PlayerOutputDevice
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.ui.component.ActionPromptDialog
import moe.rgsekai.sekaitune.ui.component.BottomSheetState
import moe.rgsekai.sekaitune.ui.component.bottomSheetDraggable
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.roundToInt

/**
 * Current Song Header shown at the top of the queue
 * Displays album art, song info, and control buttons
 */
@Composable
fun CurrentSongHeader(
    sheetState: BottomSheetState,
    mediaMetadata: MediaMetadata?,
    liked: Boolean,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    locked: Boolean,
    songCount: Int,
    queueDuration: Int,
    infiniteQueueEnabled: Boolean,
    infiniteQueueLoading: Boolean,
    selectedFilter: moe.rgsekai.sekaitune.models.QueueFilter,
    activeQueueType: moe.rgsekai.sekaitune.playback.queues.ActiveQueueType = moe.rgsekai.sekaitune.playback.queues.ActiveQueueType.ONLINE,
    backgroundColor: Color,
    onBackgroundColor: Color,
    onToggleLike: () -> Unit,
    onMenuClick: () -> Unit,
    onClearQueueClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onLockClick: () -> Unit,
    onInfiniteQueueClick: () -> Unit,
    onToggleActiveQueue: () -> Unit = {},
    onFilterSelected: (moe.rgsekai.sekaitune.models.QueueFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val glassConfig = LocalGlassEffectConfig.current
    val isGlassQueue = glassConfig.isEnabledFor(GlassComponent.QUEUE)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(if (isGlassQueue) Color.Transparent else backgroundColor)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .bottomSheetDraggable(sheetState)
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(if (isGlassQueue) onBackgroundColor.copy(alpha = 0.5f) else onBackgroundColor.copy(alpha = 0.4f)),
            )
        }

        val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        val rimColor = remember(isLight) { if (isLight) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f) }
        val defaultRimBorder = remember(rimColor) { BorderStroke(1.dp, rimColor) }
        val activeRimBorder = remember { BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)) }

        val nowPlayingShape = remember { RoundedCornerShape(22.dp) }
        val thumbShape = remember { RoundedCornerShape(12.dp) }
        Surface(
            shape = nowPlayingShape,
            color = Color.Transparent,
            border = if (isGlassQueue) defaultRimBorder else null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isGlassQueue) {
                            Modifier.liquidGlass(
                                config = glassConfig,
                                shape = nowPlayingShape,
                                applyEdgeEffects = true,
                            )
                        } else {
                            Modifier
                        }
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(if (isGlassQueue) 56.dp else 64.dp)
                            .clip(thumbShape)
                            .then(
                                if (isGlassQueue) {
                                    Modifier.border(1.dp, onBackgroundColor.copy(alpha = 0.18f), thumbShape)
                                } else {
                                    Modifier.background(onBackgroundColor.copy(alpha = 0.06f))
                                }
                            ),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = mediaMetadata?.title ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onBackgroundColor,
                    )
                    Text(
                        text = mediaMetadata?.artists?.joinToString(", ") { it.name } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onBackgroundColor.copy(alpha = 0.6f),
                    )
                }

                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(44.dp),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            contentColor =
                                if (liked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    onBackgroundColor
                                },
                        ),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (liked) {
                                    R.drawable.favorite
                                } else {
                                    R.drawable.favorite_border
                                },
                            ),
                        contentDescription = stringResource(R.string.action_like),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val infoBarShape = remember { RoundedCornerShape(22.dp) }
        Surface(
            shape = infoBarShape,
            color = if (isGlassQueue) Color.Transparent else onBackgroundColor.copy(alpha = 0.06f),
            border = if (isGlassQueue) defaultRimBorder else null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isGlassQueue) {
                            Modifier.liquidGlass(
                                config = glassConfig,
                                shape = infoBarShape,
                                applyEdgeEffects = true,
                            )
                        } else {
                            Modifier
                        }
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    IconButton(
                        onClick = onLockClick,
                        modifier = Modifier.size(40.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = onBackgroundColor.copy(alpha = 0.7f),
                            ),
                    ) {
                        Icon(
                            painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(40.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = onBackgroundColor.copy(alpha = 0.7f),
                            ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onClearQueueClick,
                        modifier = Modifier.size(40.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.delete),
                            contentDescription = stringResource(R.string.clear),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Text(
                    text =
                        pluralStringResource(R.plurals.n_song, songCount, songCount) +
                            "  •  " + makeTimeString(queueDuration * 1000L),
                    style = MaterialTheme.typography.labelMedium,
                    color = onBackgroundColor.copy(alpha = 0.55f),
                    modifier = Modifier.padding(end = 14.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val leadingShape = ButtonGroupDefaults.connectedLeadingButtonShapes().shape
            val trailingShape = ButtonGroupDefaults.connectedTrailingButtonShapes().shape

            val shuffleBorder = if (isGlassQueue) (if (shuffleModeEnabled) activeRimBorder else defaultRimBorder) else null
            val repeatBorder = if (isGlassQueue) (if (repeatMode != Player.REPEAT_MODE_OFF) activeRimBorder else defaultRimBorder) else null

            Surface(
                onClick = {
                    if (enableHapticFeedback) {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                        )
                    }
                    onShuffleClick()
                },
                shape = leadingShape,
                color = if (isGlassQueue) (if (shuffleModeEnabled) Color.White.copy(alpha = 0.22f) else Color.Transparent) else (if (shuffleModeEnabled) onBackgroundColor.copy(alpha = 0.22f) else onBackgroundColor.copy(alpha = 0.12f)),
                border = shuffleBorder,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .then(
                            if (isGlassQueue) {
                                Modifier.liquidGlass(
                                    config = glassConfig,
                                    shape = leadingShape,
                                    applyEdgeEffects = true,
                                )
                            } else {
                                Modifier
                            }
                        ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(R.string.action_shuffle_on),
                        tint = if (isGlassQueue && shuffleModeEnabled) Color.White else onBackgroundColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Surface(
                onClick = {
                    if (enableHapticFeedback) {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                        )
                    }
                    onRepeatClick()
                },
                shape = trailingShape,
                color = if (isGlassQueue) (if (repeatMode != Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.22f) else Color.Transparent) else (if (repeatMode != Player.REPEAT_MODE_OFF) onBackgroundColor.copy(alpha = 0.22f) else onBackgroundColor.copy(alpha = 0.12f)),
                border = repeatBorder,
                modifier =
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .then(
                            if (isGlassQueue) {
                                Modifier.liquidGlass(
                                    config = glassConfig,
                                    shape = trailingShape,
                                    applyEdgeEffects = true,
                                )
                            } else {
                                Modifier
                            }
                        ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter =
                            painterResource(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                                    Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                                    else -> R.drawable.repeat
                                },
                            ),
                        contentDescription = null,
                        tint = if (isGlassQueue && repeatMode != Player.REPEAT_MODE_OFF) Color.White else onBackgroundColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // YouTube Music style Auto-play Header with Switch in its own liquid glass container
        val autoplayShape = remember { RoundedCornerShape(20.dp) }
        Surface(
            onClick = {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
                onInfiniteQueueClick()
            },
            shape = autoplayShape,
            color = Color.Transparent,
            border = if (isGlassQueue) defaultRimBorder else null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isGlassQueue) {
                            Modifier.liquidGlass(
                                config = glassConfig,
                                shape = autoplayShape,
                                applyEdgeEffects = true,
                            )
                        } else {
                            Modifier
                        }
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.autoplay),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onBackgroundColor,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.autoplay_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = onBackgroundColor.copy(alpha = 0.55f),
                    )
                }

                androidx.compose.material3.Switch(
                    checked = infiniteQueueEnabled,
                    onCheckedChange = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onInfiniteQueueClick()
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = if (isGlassQueue) Color.White else backgroundColor,
                        checkedTrackColor = if (isGlassQueue) Color.White.copy(alpha = 0.35f) else onBackgroundColor,
                        uncheckedThumbColor = onBackgroundColor.copy(alpha = 0.6f),
                        uncheckedTrackColor = if (isGlassQueue) Color.White.copy(alpha = 0.10f) else onBackgroundColor.copy(alpha = 0.10f),
                        uncheckedBorderColor = if (isGlassQueue) Color.White.copy(alpha = 0.20f) else onBackgroundColor.copy(alpha = 0.18f),
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dual Queue Selector (Online vs Local) in its own liquid glass container
        val dualQueueShape = remember { RoundedCornerShape(20.dp) }
        Surface(
            onClick = {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
                onToggleActiveQueue()
            },
            shape = dualQueueShape,
            color = if (isGlassQueue) Color.Transparent else onBackgroundColor.copy(alpha = 0.05f),
            border = if (isGlassQueue) defaultRimBorder else null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isGlassQueue) {
                            Modifier.liquidGlass(
                                config = glassConfig,
                                shape = dualQueueShape,
                                applyEdgeEffects = true,
                            )
                        } else {
                            Modifier
                        }
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (activeQueueType == moe.rgsekai.sekaitune.playback.queues.ActiveQueueType.LOCAL) {
                                    R.drawable.storage
                                } else {
                                    R.drawable.language
                                },
                            ),
                        contentDescription = null,
                        tint = onBackgroundColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text =
                                stringResource(
                                    if (activeQueueType == moe.rgsekai.sekaitune.playback.queues.ActiveQueueType.LOCAL) {
                                        R.string.queue_local
                                    } else {
                                        R.string.queue_online
                                    },
                                ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onBackgroundColor,
                        )
                        Text(
                            text =
                                stringResource(
                                    if (activeQueueType == moe.rgsekai.sekaitune.playback.queues.ActiveQueueType.LOCAL) {
                                        R.string.switch_to_online_queue
                                    } else {
                                        R.string.switch_to_local_queue
                                    },
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = onBackgroundColor.copy(alpha = 0.55f),
                        )
                    }
                }

                Icon(
                    painter = painterResource(R.drawable.swipe),
                    contentDescription = null,
                    tint = onBackgroundColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Queue Filter Chips Row (All, Discover, Familiar, Popular, Deep cuts)
        val chipShape = remember { RoundedCornerShape(100.dp) }
        val chipBorderUnselected = if (isGlassQueue) defaultRimBorder else remember(onBackgroundColor) { BorderStroke(1.dp, onBackgroundColor.copy(alpha = 0.14f)) }
        val chipBorderSelected = if (isGlassQueue) activeRimBorder else null

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(
                items = QueueFilter.entries,
                key = { it.name },
            ) { filter ->
                val isSelected = filter == selectedFilter
                val chipContainerColor = if (isGlassQueue) {
                    if (isSelected) Color.White.copy(alpha = 0.22f) else Color.Transparent
                } else {
                    if (isSelected) onBackgroundColor else onBackgroundColor.copy(alpha = 0.08f)
                }

                Surface(
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onFilterSelected(filter)
                    },
                    shape = chipShape,
                    color = chipContainerColor,
                    border = if (isSelected) chipBorderSelected else chipBorderUnselected,
                    modifier =
                        Modifier.then(
                            if (isGlassQueue) {
                                Modifier.liquidGlass(
                                    config = glassConfig,
                                    shape = chipShape,
                                    applyEdgeEffects = false,
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (isSelected && infiniteQueueLoading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = if (isGlassQueue) Color.White else backgroundColor,
                            )
                        }
                        Text(
                            text = stringResource(filter.titleRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color =
                                if (isSelected) {
                                    if (isGlassQueue) Color.White else backgroundColor
                                } else {
                                    onBackgroundColor.copy(alpha = 0.85f)
                                },
                        )
                    }
                }
            }
        }

        if (!isGlassQueue) {
            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = onBackgroundColor.copy(alpha = 0.08f),
                thickness = 1.dp,
            )
        }
    }
}

/**
 * Shared Sleep Timer Dialog component used in both Queue and Player.
 */
@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onEndOfSong: () -> Unit,
    initialValue: Float = 30f,
) {
    var sleepTimerValue by remember { mutableFloatStateOf(initialValue) }

    ActionPromptDialog(
        titleBar = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.sleep_timer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(sleepTimerValue.roundToInt())
        },
        onCancel = onDismiss,
        onReset = {
            sleepTimerValue = 30f
        },
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt(),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(Modifier.height(16.dp))

                Slider(
                    value = sleepTimerValue,
                    onValueChange = { sleepTimerValue = it },
                    valueRange = 5f..120f,
                    steps = (120 - 5) / 5 - 1,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(onClick = onEndOfSong, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.end_of_song))
                }
            }
        },
    )
}

/**
 * Codec information row displayed when showCodecOnPlayer is enabled.
 */
@Composable
fun CodecInfoRow(
    codec: String,
    bitrate: String,
    fileSize: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp, top = 6.dp, bottom = 2.dp),
    ) {
        Text(
            text =
                buildString {
                    append(codec)
                    if (bitrate != "Unknown") {
                        append(" • ")
                        append(bitrate)
                    }
                    if (fileSize.isNotEmpty()) {
                        append(" • ")
                        append(fileSize)
                    }
                },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * V2 Design Style collapsed queue content.
 */
@Composable
fun QueueCollapsedContentV2(
    showCodecOnPlayer: Boolean,
    currentFormat: FormatEntity?,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    repeatMode: Int,
    mediaMetadata: MediaMetadata?,
    onExpandQueue: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onShowLyrics: () -> Unit,
    onRepeatModeClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    LaunchedEffect(enableHapticFeedback) {
        view.isHapticFeedbackEnabled = enableHapticFeedback
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showCodecOnPlayer && currentFormat != null) {
            val codec =
                currentFormat.codecs
                    .takeIf { it.isNotBlank() }
                    ?: currentFormat.containerLabel()

            val container = currentFormat.containerLabel()

            val codecLabel =
                if (container.isNotBlank() && !codec.equals(container, ignoreCase = true)) {
                    "$codec ($container)"
                } else {
                    codec
                }

            val bitrate = currentFormat.formattedBitrate()

            val extraText =
                listOfNotNull(
                    currentFormat.formattedSampleRate(),
                    currentFormat.formattedFileSize().takeIf { it.isNotBlank() },
                ).joinToString(separator = " • ")

            CodecInfoRow(
                codec = codecLabel,
                bitrate = bitrate,
                fileSize = extraText,
                textColor = textBackgroundColor.copy(alpha = 0.7f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 30.dp, vertical = 10.dp)
                    .windowInsetsPadding(
                        WindowInsets.systemBars.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    ),
        ) {
            val buttonSize = 42.dp
            val iconSize = 24.dp
            val borderColor = textBackgroundColor.copy(alpha = 0.35f)

            // Queue button
            Box(
                modifier =
                    Modifier
                        .size(buttonSize)
                        .clip(
                            RoundedCornerShape(
                                topStart = 50.dp,
                                bottomStart = 50.dp,
                                topEnd = 10.dp,
                                bottomEnd = 10.dp,
                            ),
                        ).border(
                            1.dp,
                            borderColor,
                            RoundedCornerShape(
                                topStart = 50.dp,
                                bottomStart = 50.dp,
                                topEnd = 10.dp,
                                bottomEnd = 10.dp,
                            ),
                        ).clickable { onExpandQueue() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.queue_music),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = textBackgroundColor,
                )
            }

            // Sleep timer button
            Box(
                modifier =
                    Modifier
                        .size(buttonSize)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable { onSleepTimerClick() },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    label = "sleepTimer",
                    targetState = sleepTimerEnabled,
                ) { enabled ->
                    if (enabled) {
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft),
                            color = textBackgroundColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.bedtime),
                            contentDescription = null,
                            modifier = Modifier.size(iconSize),
                            tint = textBackgroundColor,
                        )
                    }
                }
            }

            // Lyrics button
            Box(
                modifier =
                    Modifier
                        .size(buttonSize)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable { onShowLyrics() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.lyrics),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = textBackgroundColor,
                )
            }

            // Repeat mode button
            Box(
                modifier =
                    Modifier
                        .size(buttonSize)
                        .clip(
                            RoundedCornerShape(
                                topStart = 10.dp,
                                bottomStart = 10.dp,
                                topEnd = 50.dp,
                                bottomEnd = 50.dp,
                            ),
                        ).border(
                            1.dp,
                            borderColor,
                            RoundedCornerShape(
                                topStart = 10.dp,
                                bottomStart = 10.dp,
                                topEnd = 50.dp,
                                bottomEnd = 50.dp,
                            ),
                        ).clickable {
                            if (enableHapticFeedback) {
                                view.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                    android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                )
                            }
                            onRepeatModeClick()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            id =
                                when (repeatMode) {
                                    Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                    else -> R.drawable.repeat
                                },
                        ),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(iconSize)
                            .alpha(if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f),
                    tint = textBackgroundColor,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Menu button
            Box(
                modifier =
                    Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .background(textButtonColor)
                        .clickable { onMenuClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.more_vert),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = iconButtonColor,
                )
            }
        }
    }
}




@Composable
fun QueueCollapsedContentV7(
    showCodecOnPlayer: Boolean,
    currentFormat: FormatEntity?,
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onExpandQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onDeviceClick: () -> Unit,
    device: ActiveOutputDevice,
    modifier: Modifier = Modifier,
) {
    val isBluetoothConnected = device.type == PlayerOutputDevice.Bluetooth || device.type == PlayerOutputDevice.Headset

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp, vertical = 12.dp)
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                ),
    ) {
        IconButton(
            onClick = onExpandQueue,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.apple_queue),
                contentDescription = stringResource(id = R.string.queue),
                modifier = Modifier.size(24.dp),
                tint = textBackgroundColor,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier.width(116.dp),
        ) {
            ToggleButton(
                checked = false,
                onCheckedChange = { onDeviceClick() },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                modifier =
                    Modifier
                        .height(46.dp)
                        .weight(1f),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = textBackgroundColor.copy(alpha = 0.2f),
                        contentColor = textBackgroundColor,
                        checkedContainerColor = textBackgroundColor.copy(alpha = 0.4f),
                        checkedContentColor = textBackgroundColor,
                    ),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (isBluetoothConnected) R.drawable.headset_applemusic else R.drawable.speaker_apple,
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }

            ToggleButton(
                checked = sleepTimerEnabled,
                onCheckedChange = { onSleepTimerClick() },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier =
                    Modifier
                        .height(46.dp)
                        .weight(1f),
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = textBackgroundColor.copy(alpha = 0.2f),
                        contentColor = textBackgroundColor,
                        checkedContainerColor = textBackgroundColor.copy(alpha = 0.4f),
                        checkedContentColor = textBackgroundColor,
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.sleep_timer),
                        contentDescription = stringResource(id = R.string.sleep_timer),
                        modifier = Modifier.size(22.dp),
                    )
                    if (sleepTimerEnabled) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = makeTimeString(sleepTimerTimeLeft.coerceAtLeast(0L)),
                            style = MaterialTheme.typography.labelSmall,
                            color = textBackgroundColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onShowLyrics,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.apple_music_me),
                contentDescription = stringResource(id = R.string.lyrics),
                modifier = Modifier.size(24.dp),
                tint = textBackgroundColor,
            )
        }
    }
}





