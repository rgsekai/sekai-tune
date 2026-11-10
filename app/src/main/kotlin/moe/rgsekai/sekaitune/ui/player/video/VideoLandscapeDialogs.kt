package moe.rgsekai.sekaitune.ui.player.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.VideoQuality
import moe.rgsekai.sekaitune.constants.VideoQualityKey
import moe.rgsekai.sekaitune.ui.screens.settings.videoSizeName
import moe.rgsekai.sekaitune.ui.utils.ShowMediaInfo
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

val VideoLandscapeCardBackground = Color(0xFF1E1E22)
val VideoLandscapeCardRow = Color(0xFF28282E)

@Composable
fun VideoLandscapeSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    maxWidth: Dp = 480.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = VideoLandscapeCardBackground,
                contentColor = Color.White,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth(0.9f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume */ },
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f)),
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun VideoLandscapeQualitySheet(
    session: YouTubeVideoSession?,
    onDismiss: () -> Unit,
) {
    val (quality, setQuality) = rememberEnumPreference(VideoQualityKey, VideoQuality.AUTO)
    val sizes = session?.sizes.orEmpty()
    val options = remember(sizes) {
        listOf(VideoQuality.AUTO) + VideoQuality.entries.filter {
            it != VideoQuality.AUTO && (sizes.isEmpty() || it.shortSide in sizes)
        }
    }

    VideoLandscapeSheet(
        visible = true,
        onDismiss = onDismiss,
        maxWidth = 440.dp,
    ) {
        Text(
            text = stringResource(R.string.video_quality),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in options) {
                val isSelected = option == quality
                val title = if (option == VideoQuality.AUTO) {
                    qualityLabel(VideoQuality.AUTO, session)
                } else {
                    videoSizeName(option.shortSide)
                }
                val description = when {
                    option == VideoQuality.AUTO -> stringResource(R.string.video_quality_auto_desc)
                    option == quality -> stringResource(R.string.video_quality_fixed_desc)
                    option.shortSide > 1080 -> stringResource(R.string.video_quality_sharp_desc)
                    else -> null
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else VideoLandscapeCardRow)
                        .clickable {
                            setQuality(option)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (description != null) {
                            Text(
                                text = description,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val TempoMin = 0.25f
private const val TempoMax = 2f
private const val PitchMin = 0.25f
private const val PitchMax = 2f

private fun Float.quantize(step: Float): Float =
    if (step <= 0f) this else (round(this / step) * step).coerceAtLeast(0f)

private fun sliderToMultiplier(slider: Float): Float {
    val t = slider.coerceIn(0f, 1f)
    val y = (t - 0.5f) * 2f
    val curve = 2.2f
    val absY = abs(y).pow(curve)
    val shaped = when {
        y > 0f -> absY
        y < 0f -> -absY
        else -> 0f
    }
    val exponent = if (y < 0f) 2f * shaped else shaped
    return 2f.pow(exponent).coerceIn(TempoMin, TempoMax)
}

private fun multiplierToSlider(multiplier: Float): Float {
    val m = multiplier.coerceIn(TempoMin, TempoMax)
    val log = log2(m)
    val curve = 2.2f
    val shaped = if (m < 1f) (log / 2f) else log
    val absShaped = abs(shaped).pow(1f / curve)
    val y = when {
        shaped > 0f -> absShaped
        shaped < 0f -> -absShaped
        else -> 0f
    }
    return (0.5f + y / 2f).coerceIn(0f, 1f)
}

@Composable
fun VideoLandscapeSpeedSheet(
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val initialSpeed = remember { playerConnection.player.playbackParameters.speed }
    val initialPitch = remember { playerConnection.player.playbackParameters.pitch }

    var tempo by remember {
        mutableFloatStateOf(initialSpeed.coerceIn(TempoMin, TempoMax))
    }
    var pitch by remember {
        mutableFloatStateOf(initialPitch.coerceIn(PitchMin, PitchMax))
    }

    val applyPlaybackParameters: (Float, Float) -> Unit = { speed, pitchMultiplier ->
        playerConnection.player.playbackParameters = PlaybackParameters(
            speed.coerceIn(TempoMin, TempoMax),
            pitchMultiplier.coerceIn(PitchMin, PitchMax),
        )
    }

    VideoLandscapeSheet(
        visible = true,
        onDismiss = onDismiss,
        maxWidth = 480.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.tempo_and_pitch),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        tempo = 1f
                        pitch = 1f
                        applyPlaybackParameters(tempo, pitch)
                    },
                ) {
                    Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.primary)
                }
            }

            // Tempo row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.speed),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tempo),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "x${String.format("%.2f", tempo)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    enabled = tempo > TempoMin,
                    onClick = {
                        tempo = (tempo - 0.05f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                        applyPlaybackParameters(tempo, pitch)
                    },
                ) {
                    Icon(painterResource(R.drawable.remove), contentDescription = null, tint = Color.White)
                }

                Slider(
                    value = multiplierToSlider(tempo),
                    onValueChange = { slider ->
                        val updated = sliderToMultiplier(slider).quantize(0.01f)
                        if (abs(updated - tempo) >= 0.005f) {
                            tempo = updated
                            applyPlaybackParameters(tempo, pitch)
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    enabled = tempo < TempoMax,
                    onClick = {
                        tempo = (tempo + 0.05f).coerceIn(TempoMin, TempoMax).quantize(0.01f)
                        applyPlaybackParameters(tempo, pitch)
                    },
                ) {
                    Icon(painterResource(R.drawable.add), contentDescription = null, tint = Color.White)
                }
            }

            // Presets
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                val presets = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                presets.forEach { preset ->
                    val selected = abs(tempo - preset) < 0.005f
                    FilterChip(
                        selected = selected,
                        onClick = {
                            tempo = preset
                            applyPlaybackParameters(tempo, pitch)
                        },
                        label = { Text("x${String.format("%.2f", preset)}") },
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            // Pitch row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.discover_tune),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.pitch),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "x${String.format("%.2f", pitch)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    enabled = pitch > PitchMin,
                    onClick = {
                        pitch = (pitch - 0.05f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                        applyPlaybackParameters(tempo, pitch)
                    },
                ) {
                    Icon(painterResource(R.drawable.remove), contentDescription = null, tint = Color.White)
                }

                Slider(
                    value = multiplierToSlider(pitch),
                    onValueChange = { slider ->
                        val updated = sliderToMultiplier(slider).quantize(0.01f)
                        if (abs(updated - pitch) >= 0.005f) {
                            pitch = updated
                            applyPlaybackParameters(tempo, pitch)
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    enabled = pitch < PitchMax,
                    onClick = {
                        pitch = (pitch + 0.05f).coerceIn(PitchMin, PitchMax).quantize(0.01f)
                        applyPlaybackParameters(tempo, pitch)
                    },
                ) {
                    Icon(painterResource(R.drawable.add), contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun VideoLandscapeSleepTimerSheet(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onEndOfSong: () -> Unit,
) {
    var sleepTimerValue by remember { mutableFloatStateOf(30f) }

    VideoLandscapeSheet(
        visible = true,
        onDismiss = onDismiss,
        maxWidth = 420.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Text(
                text = pluralStringResource(
                    R.plurals.minute,
                    sleepTimerValue.roundToInt(),
                    sleepTimerValue.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
            )

            Spacer(Modifier.height(16.dp))

            Slider(
                value = sleepTimerValue,
                onValueChange = { sleepTimerValue = it },
                valueRange = 5f..120f,
                steps = (120 - 5) / 5 - 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onEndOfSong,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.end_of_song))
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onConfirm(sleepTimerValue.roundToInt()) }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
fun VideoLandscapeSongInfoSheet(
    videoId: String,
    onDismiss: () -> Unit,
) {
    VideoLandscapeSheet(
        visible = true,
        onDismiss = onDismiss,
        maxWidth = 520.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
        ) {
            ShowMediaInfo(
                videoId = videoId,
                onCloseOverride = onDismiss,
            )
        }
    }
}
