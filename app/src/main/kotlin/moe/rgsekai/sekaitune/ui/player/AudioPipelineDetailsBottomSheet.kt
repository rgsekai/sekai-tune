/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.playback.sources.AudioPipelineStatus
import moe.rgsekai.sekaitune.playback.sources.AudioQualityManager
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeInfo
import moe.rgsekai.sekaitune.playback.stream.ResolveAudioStreamUseCase
import moe.rgsekai.sekaitune.ui.component.GlassComponent
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import moe.rgsekai.sekaitune.ui.component.LocalGlassEffectConfig
import moe.rgsekai.sekaitune.utils.isLocalMediaId

data class AudioPipelineDetails(
    val source: String,
    val format: String,
    val bitDepth: String,
    val sampleRate: String,
    val bitrate: String,
    val audioSpec: String,
    val pcmDataRate: String,
    val channels: String,
    val transferSize: String,
    val decoderName: String,
    val decoderFormat: String,
    val ioRate: String,
    val resamplerType: String,
    val cutoff: String,
    val resamplerQuality: String,
    val pipelineStatus: AudioPipelineStatus?,
    val statusFallbackText: String,
)

fun resolveAudioPipelineDetails(
    badgeInfo: PlaybackQualityBadgeInfo,
    currentFormat: FormatEntity? = null,
): AudioPipelineDetails {
    val isLossless = badgeInfo.isLossless
    val isHiRes = badgeInfo.isHiRes

    val isFormatFlacOrLossless = isLossless || (currentFormat != null && (
        currentFormat.mimeType.contains("flac", ignoreCase = true) ||
        currentFormat.mimeType.contains("alac", ignoreCase = true) ||
        currentFormat.mimeType.contains("wav", ignoreCase = true)
    ))

    val sourceRecord = badgeInfo.mediaId?.let { AudioQualityManager.getSourceRecord(it) }
    val winning = badgeInfo.mediaId?.let { ResolveAudioStreamUseCase.getWinningSource(it) }

    val source = when {
        badgeInfo.mediaId != null && (badgeInfo.mediaId.isLocalMediaId() || badgeInfo.mediaId.startsWith("content://") || badgeInfo.mediaId.startsWith("file://")) -> "Local Storage"
        badgeInfo.mediaId?.startsWith("addon:") == true -> AudioQualityManager.resolveAddonDisplayName(badgeInfo.mediaId) ?: "External Addon"
        !isFormatFlacOrLossless -> "YouTube"
        badgeInfo.sourceName.isNotBlank() && !badgeInfo.sourceName.equals("YouTube", ignoreCase = true) -> badgeInfo.sourceName
        sourceRecord != null && sourceRecord.sourceName.isNotBlank() && !sourceRecord.sourceName.equals("YouTube", ignoreCase = true) -> sourceRecord.sourceName
        winning != null -> winning.sourceName
        isFormatFlacOrLossless -> AudioQualityManager.getDefaultAddonName() ?: "Lossless Source"
        else -> "YouTube"
    }

    val format = when {
        badgeInfo.format.isNotBlank() -> badgeInfo.format.uppercase()
        currentFormat != null -> {
            val mime = currentFormat.mimeType.uppercase()
            when {
                mime.contains("FLAC") -> "FLAC"
                mime.contains("ALAC") -> "ALAC"
                mime.contains("WAV") -> "WAV"
                mime.contains("OPUS") -> "Opus"
                mime.contains("MP4") || mime.contains("AAC") -> "AAC"
                mime.contains("MP3") -> "MP3"
                else -> currentFormat.mimeType.substringAfter("audio/").uppercase()
            }
        }
        isLossless -> "FLAC"
        else -> "Opus"
    }

    val bitDepth = when {
        badgeInfo.bitDepth.isNotBlank() -> badgeInfo.bitDepth
        isHiRes -> "24-bit"
        else -> "16-bit"
    }

    val sampleRate = when {
        badgeInfo.sampleRate.isNotBlank() -> badgeInfo.sampleRate
        currentFormat?.sampleRate != null -> String.format(java.util.Locale.US, "%.1f kHz", currentFormat.sampleRate / 1000.0)
        isHiRes -> "96.0 kHz"
        isLossless -> "44.1 kHz"
        else -> "48.0 kHz"
    }

    val bitrate = when {
        badgeInfo.bitrate.isNotBlank() -> badgeInfo.bitrate
        currentFormat?.bitrate != null -> "${currentFormat.bitrate / 1000} kbps"
        isHiRes -> "2840 kbps"
        isLossless -> "751 kbps"
        else -> "149 kbps"
    }

    val audioSpec = "$bitDepth / $sampleRate / $bitrate"

    val pcmDataRate = when {
        badgeInfo.pcmDataRate.isNotBlank() -> badgeInfo.pcmDataRate
        isHiRes -> "4608 kbps"
        sampleRate.contains("48") -> "1536 kbps"
        else -> "1411 kbps"
    }

    val channels = if (badgeInfo.channels.isNotBlank()) badgeInfo.channels else "Stereo"

    val transferSize = when {
        badgeInfo.transferSize.isNotBlank() -> badgeInfo.transferSize
        currentFormat?.contentLength != null && currentFormat.contentLength > 0 -> {
            String.format(java.util.Locale.US, "%.1f MB", currentFormat.contentLength / (1024.0 * 1024.0))
        }
        else -> "—"
    }

    val decoderName = when {
        badgeInfo.decoderName.isNotBlank() -> badgeInfo.decoderName
        format.contains("FLAC") -> "c2.android.flac.decoder"
        format.contains("ALAC") -> "c2.android.alac.decoder"
        format.contains("OPUS") || format.contains("WEBM") -> "c2.android.opus.decoder"
        format.contains("AAC") || format.contains("MP4") -> "c2.android.aac.decoder"
        format.contains("MP3") -> "c2.android.mp3.decoder"
        else -> "c2.android.audio.decoder"
    }

    val decoderFormat = when {
        badgeInfo.outputFormat.isNotBlank() -> badgeInfo.outputFormat
        isHiRes -> "24-bit PCM"
        else -> "16-bit PCM"
    }

    val ioRate = "$sampleRate \u2192 $sampleRate"
    val resamplerType = "None"
    val cutoff = "—"
    val resamplerQuality = "Passthrough"

    val effectivePipelineStatus = when {
        !isFormatFlacOrLossless -> {
            if (badgeInfo.pipelineStatus == AudioPipelineStatus.PRELOADED_INSTANT || badgeInfo.pipelineStatus == AudioPipelineStatus.UPGRADED_FROM_OPUS) {
                AudioPipelineStatus.SETTLED_NO_MATCH
            } else {
                badgeInfo.pipelineStatus ?: AudioPipelineStatus.SETTLED_NO_MATCH
            }
        }
        isFormatFlacOrLossless && badgeInfo.pipelineStatus == AudioPipelineStatus.SETTLED_NO_MATCH -> {
            sourceRecord?.pipelineStatus ?: AudioPipelineStatus.UPGRADED_FROM_OPUS
        }
        badgeInfo.mediaId != null && (badgeInfo.mediaId.isLocalMediaId() || badgeInfo.mediaId.startsWith("content://") || badgeInfo.mediaId.startsWith("file://")) -> null
        else -> badgeInfo.pipelineStatus ?: sourceRecord?.pipelineStatus
    }

    val effectiveStatusText = when {
        badgeInfo.mediaId != null && (badgeInfo.mediaId.isLocalMediaId() || badgeInfo.mediaId.startsWith("content://") || badgeInfo.mediaId.startsWith("file://")) -> "Playing from local device storage"
        isFormatFlacOrLossless && badgeInfo.statusText.contains("YouTube", ignoreCase = true) -> ""
        badgeInfo.statusText.isNotBlank() -> badgeInfo.statusText
        else -> sourceRecord?.statusText.orEmpty()
    }

    return AudioPipelineDetails(
        source = source,
        format = format,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        bitrate = bitrate,
        audioSpec = audioSpec,
        pcmDataRate = pcmDataRate,
        channels = channels,
        transferSize = transferSize,
        decoderName = decoderName,
        decoderFormat = decoderFormat,
        ioRate = ioRate,
        resamplerType = resamplerType,
        cutoff = cutoff,
        resamplerQuality = resamplerQuality,
        pipelineStatus = effectivePipelineStatus,
        statusFallbackText = effectiveStatusText,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPipelineDetailsBottomSheet(
    onDismiss: () -> Unit,
    badgeInfo: PlaybackQualityBadgeInfo,
    currentFormat: FormatEntity? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val details = remember(badgeInfo, currentFormat) {
        resolveAudioPipelineDetails(badgeInfo, currentFormat)
    }

    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU) || glassConfig.isEnabledFor(GlassComponent.DIALOG)
    val isLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    val statusString = when (details.pipelineStatus) {
        AudioPipelineStatus.PRELOADED_INSTANT -> stringResource(R.string.status_preloaded_instant)
        AudioPipelineStatus.UPGRADED_FROM_OPUS -> stringResource(R.string.status_upgraded_from_opus)
        AudioPipelineStatus.SETTLED_NO_MATCH -> stringResource(R.string.status_settled_no_match)
        AudioPipelineStatus.SETTLED_LOWER_QUALITY -> stringResource(R.string.status_settled_lower_quality)
        AudioPipelineStatus.SETTLED_METERED -> stringResource(R.string.status_settled_metered)
        AudioPipelineStatus.SETTLED_RATE_LIMITED -> stringResource(R.string.status_settled_rate_limited)
        null -> details.statusFallbackText.ifBlank {
            if (badgeInfo.isLossless || details.format == "FLAC" || details.format == "ALAC" || details.format == "WAV") {
                stringResource(R.string.status_upgraded_from_opus)
            } else if (details.source.equals("YouTube", ignoreCase = true)) {
                stringResource(R.string.status_settled_no_match)
            } else {
                "Active stream from ${details.source}"
            }
        }
    }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.audio_pipeline_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.audio_pipeline_subtitle),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            Spacer(Modifier.height(16.dp))

            // Step 1: TRACK INFO
            PipelineStepNode(
                iconRes = R.drawable.library_music,
                sectionTitle = stringResource(R.string.audio_pipeline_section_track),
                isLast = false,
            ) {
                PipelineField(label = stringResource(R.string.audio_pipeline_source), value = details.source)
                PipelineField(label = stringResource(R.string.audio_pipeline_format), value = details.format)
                PipelineField(label = stringResource(R.string.audio_pipeline_bit_depth), value = details.bitDepth)
                PipelineField(label = stringResource(R.string.audio_pipeline_sample_rate), value = details.sampleRate)
                PipelineField(label = stringResource(R.string.audio_pipeline_bitrate), value = details.bitrate)
                PipelineField(label = stringResource(R.string.audio_pipeline_audio_spec), value = details.audioSpec)
                PipelineField(label = "PCM Data Rate", value = details.pcmDataRate)
                if (details.transferSize != "—") {
                    PipelineField(label = stringResource(R.string.audio_pipeline_size), value = details.transferSize)
                }
                PipelineField(label = stringResource(R.string.audio_pipeline_channels), value = details.channels)
            }

            // Step 2: DECODER
            PipelineStepNode(
                iconRes = R.drawable.settings,
                sectionTitle = stringResource(R.string.audio_pipeline_section_decoder),
                isLast = false,
            ) {
                PipelineField(label = stringResource(R.string.audio_pipeline_decoder_name), value = details.decoderName)
                PipelineField(label = stringResource(R.string.audio_pipeline_decoder_format), value = details.decoderFormat)
            }

            // Step 3: RESAMPLER
            PipelineStepNode(
                iconRes = R.drawable.tune,
                sectionTitle = stringResource(R.string.audio_pipeline_section_resampler),
                isLast = true,
            ) {
                PipelineField(label = stringResource(R.string.audio_pipeline_io_rate), value = details.ioRate)
                PipelineField(label = stringResource(R.string.audio_pipeline_resampler_type), value = details.resamplerType)
                PipelineField(label = stringResource(R.string.audio_pipeline_cutoff), value = details.cutoff)
                PipelineField(label = stringResource(R.string.audio_pipeline_quality), value = details.resamplerQuality)
                PipelineField(label = stringResource(R.string.audio_pipeline_status), value = statusString)
            }

            Spacer(Modifier.height(24.dp))

            // Done Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGlass) {
                        if (isLight) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isGlass) Color.White else MaterialTheme.colorScheme.onPrimary,
                ),
                border = if (isGlass) BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)) else null,
            ) {
                Text(
                    text = stringResource(R.string.audio_pipeline_done),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PipelineStepNode(
    iconRes: Int,
    sectionTitle: String,
    isLast: Boolean,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
                        CircleShape,
                    )
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 4.dp else 18.dp),
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 12.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
            )

            Spacer(Modifier.height(6.dp))

            content()

            if (!isLast) {
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
private fun PipelineField(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}
