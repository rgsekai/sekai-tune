/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.utils.UpdateDownloadState
import kotlin.math.roundToInt

@Composable
fun UpdatePromptDialog(
    latestVersion: String,
    downloadState: UpdateDownloadState,
    targetBadgeOffset: Offset? = null,
    onDismiss: () -> Unit,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
    onViewChangelog: () -> Unit,
) {
    val isBusy = downloadState is UpdateDownloadState.Downloading || downloadState is UpdateDownloadState.Installing
    val coroutineScope = rememberCoroutineScope()
    val collapseProgress = remember { Animatable(0f) }
    var isCollapsing by remember { mutableStateOf(false) }
    var dialogCenter by remember { mutableStateOf<Offset?>(null) }
    var dialogSize by remember { mutableStateOf<IntSize?>(null) }

    val handleLater: () -> Unit = {
        if (!isBusy && !isCollapsing) {
            isCollapsing = true
            coroutineScope.launch {
                collapseProgress.animateTo(
                    targetValue = 1f,
                    animationSpec =
                        tween(
                            durationMillis = 400,
                            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
                        ),
                )
                onLater()
            }
        }
    }

    val progress = collapseProgress.value
    val scrimAlpha = (1f - progress) * 0.55f
    val cardAlpha = if (progress > 0.92f) ((1f - progress) / 0.08f).coerceIn(0f, 1f) else 1f
    val contentAlpha = (1f - progress * 3f).coerceIn(0f, 1f)
    val cornerRadius = androidx.compose.ui.unit.lerp(28.dp, 100.dp, progress)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val density = LocalDensity.current
    val fallbackTargetDeltaX = with(density) { ((screenWidth / 2) - 128.dp).toPx() }
    val fallbackTargetDeltaY = with(density) { (-(screenHeight / 2 - 64.dp)).toPx() }

    val targetTranslateX =
        if (targetBadgeOffset != null && dialogCenter != null) {
            targetBadgeOffset.x - dialogCenter!!.x
        } else {
            fallbackTargetDeltaX
        }

    val targetTranslateY =
        if (targetBadgeOffset != null && dialogCenter != null) {
            targetBadgeOffset.y - dialogCenter!!.y
        } else {
            fallbackTargetDeltaY
        }

    val targetScaleX = dialogSize?.let { (with(density) { 10.dp.toPx() } / it.width.toFloat()) } ?: 0.03f
    val targetScaleY = dialogSize?.let { (with(density) { 10.dp.toPx() } / it.height.toFloat()) } ?: 0.03f
    val scaleX = 1f - progress * (1f - targetScaleX)
    val scaleY = 1f - progress * (1f - targetScaleY)

    Dialog(
        onDismissRequest = {
            if (!isBusy && !isCollapsing) {
                onDismiss()
            }
        },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !isBusy && !isCollapsing,
                dismissOnClickOutside = !isBusy && !isCollapsing,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha.coerceIn(0f, 0.55f)))
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            if (!isCollapsing && coordinates.isAttached) {
                                val pos = coordinates.positionInWindow()
                                val size = coordinates.size
                                dialogCenter = Offset(pos.x + size.width / 2f, pos.y + size.height / 2f)
                                dialogSize = size
                            }
                        }.graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            this.scaleX = scaleX
                            this.scaleY = scaleY
                            translationX = progress * targetTranslateX
                            translationY = progress * targetTranslateY
                            alpha = cardAlpha
                        },
                shape = RoundedCornerShape(cornerRadius),
                color =
                    if (progress > 0.1f) {
                        androidx.compose.ui.graphics.lerp(
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                            Color(0xFFE53935),
                            ((progress - 0.1f) / 0.6f).coerceIn(0f, 1f),
                        )
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f)
                    },
                tonalElevation = 8.dp,
                shadowElevation = ((1f - progress) * 16f).dp,
                border =
                    if (progress > 0.1f) {
                        null
                    } else {
                        BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f * (1f - progress * 10f)),
                        )
                    },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .graphicsLayer {
                                alpha = contentAlpha
                            },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Header Red Circle Icon Container (matching top-right red circle badge)
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935)),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(id = R.string.new_update_available),
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Version Tag Chip
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                    ) {
                        Text(
                            text = "v$latestVersion",
                            style =
                                MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // "View changelog" button
                    TextButton(
                        onClick = onViewChangelog,
                        shape = CircleShape,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.github),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "View changelog",
                                style =
                                    MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // In-place transforming Progress / Action Button
                    UpdateActionButton(
                        downloadState = downloadState,
                        onClick = onUpdateNow,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // "Later" Button
                    AnimatedVisibility(
                        visible = !isBusy && !isCollapsing,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(150)),
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = handleLater,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = stringResource(id = R.string.later),
                                    style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Medium,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateActionButton(
    downloadState: UpdateDownloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    when (downloadState) {
        is UpdateDownloadState.Idle -> {
            Box(
                modifier =
                    modifier
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.download),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update Now",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        is UpdateDownloadState.Downloading -> {
            val progressFraction = downloadState.fraction
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction ?: 0f,
                animationSpec =
                    tween(
                        durationMillis = 200,
                        easing = LinearOutSlowInEasing,
                    ),
                label = "DownloadProgressFraction",
            )

            Box(
                modifier =
                    modifier
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Progress Track Fill from left to right
                if (progressFraction != null) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        ),
                                    ),
                                ),
                    )
                } else {
                    // Indeterminate background pulse
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    )
                }

                // Centered text label
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (progressFraction == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Downloading...",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        val percent = (animatedProgress * 100).roundToInt().coerceIn(0, 100)
                        Text(
                            text = "Downloading $percent%",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color =
                                if (animatedProgress > 0.5f) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
            }
        }

        is UpdateDownloadState.Installing -> {
            Box(
                modifier =
                    modifier
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Installing...",
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        is UpdateDownloadState.Error -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = onClick,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.cached),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Retry Update",
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = MaterialTheme.colorScheme.onError,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = downloadState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
