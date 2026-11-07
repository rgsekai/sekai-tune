/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.VideoQuality
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.player.video.YouTubeVideoSession

@Composable
internal fun videoSizeName(shortSide: Int): String =
    if (shortSide >= 2160) stringResource(R.string.video_quality_4k)
    else stringResource(R.string.video_quality_size, shortSide)

@Composable
internal fun videoQualityName(quality: VideoQuality): String =
    if (quality == VideoQuality.AUTO) stringResource(R.string.video_quality_auto)
    else videoSizeName(quality.shortSide)

@Composable
internal fun qualityLabel(quality: VideoQuality, session: YouTubeVideoSession?): String {
    if (quality != VideoQuality.AUTO) return videoSizeName(quality.shortSide)
    val playing = session?.playingShortSide ?: 0
    return if (playing > 0) {
        stringResource(R.string.video_quality_auto_now, videoSizeName(playing))
    } else {
        stringResource(R.string.video_quality_auto)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQualityPreference(
    selectedQuality: VideoQuality,
    session: YouTubeVideoSession? = null,
    onQualitySelected: (VideoQuality) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
) {
    var showSheet = remember { androidx.compose.runtime.mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    if (showSheet.value) {
        VideoQualityBottomSheet(
            selectedQuality = selectedQuality,
            session = session,
            sheetState = sheetState,
            onDismiss = { showSheet.value = false },
            onQualitySelected = { quality ->
                onQualitySelected(quality)
                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                    showSheet.value = false
                }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = { Text(stringResource(R.string.video_quality)) },
        description = stringResource(R.string.video_quality_settings_desc),
        icon = { Icon(painterResource(R.drawable.slow_motion_video), null) },
        content = {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            ) {
                Text(
                    text = qualityLabel(selectedQuality, session),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        },
        onClick = { showSheet.value = true },
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQualityBottomSheet(
    selectedQuality: VideoQuality,
    session: YouTubeVideoSession? = null,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onQualitySelected: (VideoQuality) -> Unit,
) {
    val sizes = session?.sizes.orEmpty()
    val options = remember(sizes) {
        listOf(VideoQuality.AUTO) + VideoQuality.entries.filter {
            it != VideoQuality.AUTO && (sizes.isEmpty() || it.shortSide in sizes)
        }
    }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.video_quality),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            for (option in options) {
                val isSelected = option == selectedQuality
                val title = if (option == VideoQuality.AUTO) {
                    qualityLabel(VideoQuality.AUTO, session)
                } else {
                    videoSizeName(option.shortSide)
                }
                val description = when {
                    option == VideoQuality.AUTO -> stringResource(R.string.video_quality_auto_desc)
                    option == selectedQuality -> stringResource(R.string.video_quality_fixed_desc)
                    option.shortSide > 1080 -> stringResource(R.string.video_quality_sharp_desc)
                    else -> null
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onQualitySelected(option) },
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            )
                            if (!description.isNullOrBlank()) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (sizes.isEmpty()) {
                Text(
                    text = stringResource(R.string.video_quality_not_loaded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }
}
