/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import moe.rgsekai.sekaitune.LocalPlayerConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.EqualizerEnabledKey
import moe.rgsekai.sekaitune.constants.EqualizerSelectedProfileIdKey
import moe.rgsekai.sekaitune.constants.EqualizerVirtualizerEnabledKey
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.models.PlayerOutputDevice
import moe.rgsekai.sekaitune.playback.sources.AudioPipelineStatus
import moe.rgsekai.sekaitune.playback.sources.AudioQualityManager
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeInfo
import moe.rgsekai.sekaitune.playback.stream.ResolveAudioStreamUseCase
import moe.rgsekai.sekaitune.ui.component.GlassComponent
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import moe.rgsekai.sekaitune.ui.component.LocalGlassEffectConfig
import moe.rgsekai.sekaitune.ui.theme.PlayerColorExtractor
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.get
import moe.rgsekai.sekaitune.utils.isLocalMediaId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

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
    // DSP Stage
    val dspPcmFormat: String,
    val dspSampleRate: String,
    val dspGain: String,
    val dspMeasured: String,
    val dspEqPreset: String,
    val dspStereoExpand: String,
    val dspBuffers: String,
    val dspOutputApi: String,
    val dspBitExact: String,
    // OUTPUT DEVICE Stage
    val deviceName: String,
    val routeKind: String,
    val transport: String,
    val directStatus: String,
    val audioTrackSpec: String,
    val systemMixerSpec: String,
    val outputDeviceIcon: ImageVector,
)

fun resolveAudioPipelineDetails(
    badgeInfo: PlaybackQualityBadgeInfo,
    currentFormat: FormatEntity? = null,
    context: Context? = null,
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
        badgeInfo.mediaId?.startsWith("webdav:") == true -> "WebDAV"
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
        badgeInfo.mediaId?.startsWith("webdav:") == true -> null
        badgeInfo.mediaId != null && (badgeInfo.mediaId.isLocalMediaId() || badgeInfo.mediaId.startsWith("content://") || badgeInfo.mediaId.startsWith("file://")) -> null
        else -> badgeInfo.pipelineStatus ?: sourceRecord?.pipelineStatus
    }

    val effectiveStatusText = when {
        badgeInfo.mediaId?.startsWith("webdav:") == true -> "Playing from WebDAV server"
        badgeInfo.mediaId != null && (badgeInfo.mediaId.isLocalMediaId() || badgeInfo.mediaId.startsWith("content://") || badgeInfo.mediaId.startsWith("file://")) -> "Playing from local device storage"
        isFormatFlacOrLossless && badgeInfo.statusText.contains("YouTube", ignoreCase = true) -> ""
        badgeInfo.statusText.isNotBlank() -> badgeInfo.statusText
        else -> sourceRecord?.statusText.orEmpty()
    }

    // --- DSP Stage Telemetry ---
    val eqEnabled = runCatching { context?.dataStore?.get(EqualizerEnabledKey) ?: false }.getOrDefault(false)
    val selectedProfileId = runCatching { context?.dataStore?.get(EqualizerSelectedProfileIdKey) ?: "flat" }.getOrDefault("flat")
    val virtualizerEnabled = runCatching { context?.dataStore?.get(EqualizerVirtualizerEnabledKey) ?: false }.getOrDefault(false)

    val dspEqPreset = if (eqEnabled) {
        selectedProfileId.substringAfter("system:").replace("_", " ").replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            .ifBlank { "Flat" }
    } else {
        "Flat"
    }

    val dspStereoExpand = if (virtualizerEnabled) "250%" else "100%"

    val dspBuffers = runCatching {
        val sampleRateVal = currentFormat?.sampleRate ?: (if (isHiRes) 96000 else if (isLossless) 44100 else 48000)
        val frames = (sampleRateVal * 0.5f).toInt()
        "2x (500ms, $frames frames)"
    }.getOrDefault("2x (500ms, 22050 frames)")

    val dspBitExact = when {
        virtualizerEnabled -> "No — spatial audio"
        eqEnabled -> "No — equalizer"
        else -> "Yes"
    }

    // --- Output Device Telemetry ---
    val audioManager = runCatching { context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()

    val sinks = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.filter { it.isSink }.orEmpty()
    val activeDevice: AudioDeviceInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && audioManager != null) {
        val mediaAttrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioManager.getAudioDevicesForAttributes(mediaAttrs).firstOrNull { it.isSink }
    } else {
        null
    } ?: sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        ?: sinks.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
            it.type == AudioDeviceInfo.TYPE_HEARING_AID
        }
        ?: sinks.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        ?: sinks.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        ?: sinks.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_HDMI ||
            it.type == AudioDeviceInfo.TYPE_HDMI_ARC
        }
        ?: sinks.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE
        }

    val deviceType = PlayerOutputDevice.from(activeDevice)
    val routeKind = when (deviceType) {
        PlayerOutputDevice.BuiltinSpeaker -> "PHONE"
        PlayerOutputDevice.Bluetooth -> "BLUETOOTH"
        PlayerOutputDevice.Headset -> "WIRED"
        PlayerOutputDevice.Usb -> "USB"
        PlayerOutputDevice.Hdmi -> "HDMI"
        else -> "PHONE"
    }

    val modelName = runCatching { Build.MODEL }.getOrNull()?.takeUnless { it.isNullOrBlank() }
    val deviceName = if (routeKind == "PHONE") {
        modelName ?: "Phone Speaker"
    } else {
        activeDevice?.productName?.toString()?.takeUnless { it.isNullOrBlank() } ?: deviceType.defaultName
    }

    val directStatus = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && audioManager != null) {
            val mediaAttrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val fmt = android.media.AudioFormat.Builder()
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val support = AudioManager.getDirectPlaybackSupport(fmt, mediaAttrs)
            if (support != AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED) {
                "Supported (Framework Mixed)"
            } else {
                "Not Supported (Mixed Path)"
            }
        } else {
            "Supported (Framework Mixed)"
        }
    }.getOrDefault("Supported (Framework Mixed)")

    val audioTrackSpec = "PCM16 / $sampleRate"

    val mixerRateHz = runCatching {
        audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    }.getOrNull() ?: 48000
    val systemMixerSpec = "AudioFlinger Mixer ${mixerRateHz / 1000} kHz, HAL PCM24 packed"

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
        dspPcmFormat = "Float32",
        dspSampleRate = sampleRate,
        dspGain = "—",
        dspMeasured = "—",
        dspEqPreset = dspEqPreset,
        dspStereoExpand = dspStereoExpand,
        dspBuffers = dspBuffers,
        dspOutputApi = "AudioTrack",
        dspBitExact = dspBitExact,
        deviceName = deviceName,
        routeKind = routeKind,
        transport = "AudioTrack",
        directStatus = directStatus,
        audioTrackSpec = audioTrackSpec,
        systemMixerSpec = systemMixerSpec,
        outputDeviceIcon = deviceType.imageVector,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPipelineDetailsBottomSheet(
    onDismiss: () -> Unit,
    badgeInfo: PlaybackQualityBadgeInfo,
    currentFormat: FormatEntity? = null,
    artworkUrl: String? = null,
    isPlaying: Boolean = true,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val details = remember(badgeInfo, currentFormat, context) {
        resolveAudioPipelineDetails(badgeInfo, currentFormat, context)
    }

    // Extract dominant/vibrant color from album art with smooth fallback to primary theme color
    val fallbackColor = MaterialTheme.colorScheme.primary.toArgb()
    var albumArtColor by remember(artworkUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            albumArtColor = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .memoryCacheKey(artworkUrl)
                    .diskCacheKey(artworkUrl)
                    .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette = withContext(Dispatchers.Default) {
                        Palette.from(bitmap)
                            .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                            .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                            .generate()
                    }
                    val extracted = PlayerColorExtractor.extractGradientColors(palette, fallbackColor)
                    val primaryColor = extracted.firstOrNull()
                    if (primaryColor != null) {
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(primaryColor.toArgb(), hsv)
                        hsv[1] = (hsv[1] * 1.30f).coerceIn(0.55f, 1f)
                        hsv[2] = (hsv[2] * 1.15f).coerceIn(0.75f, 1f)
                        val glowingColor = Color(android.graphics.Color.HSVToColor(hsv))
                        withContext(Dispatchers.Main) {
                            albumArtColor = glowingColor
                        }
                    }
                }
            }
        }
    }

    val activeAccentColor = albumArtColor ?: MaterialTheme.colorScheme.primary
    val animatedAccentColor by animateColorAsState(
        targetValue = activeAccentColor,
        animationSpec = tween(durationMillis = 600),
        label = "pipelineAccentColor",
    )

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

    // Animation drivers for the travelling snake wave and signal flow
    val flowAlpha = 1f
    var pulseProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        val anim = Animatable(0f)
        val cycleDuration = if (isPlaying) 2200 else 3000
        while (isActive) {
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = cycleDuration,
                    easing = LinearEasing,
                ),
            ) {
                pulseProgress = value
            }
        }
    }

    var columnCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val stageCenters = remember { mutableStateMapOf<Int, Offset>() }
    val interactionRadiusPx = with(density) { 26.dp.toPx() }

    val stageActivation: (Int) -> Float = { idx ->
        calculateStageActivation(idx, pulseProgress, flowAlpha, stageCenters, interactionRadiusPx)
    }
    val onStageIconPositioned: (Int, Offset) -> Unit = { idx, center ->
        if (stageCenters[idx] != center) stageCenters[idx] = center
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

            // Main 5-stage pipeline container with illuminated animated signal bus
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Background signal flow drawing layer (Hardware accelerated, isolated from list recomposition)
                Spacer(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { }
                        .drawBehind {
                            drawSignalFlow(
                                stageCenters = stageCenters,
                                flowAlpha = flowAlpha,
                                pulseProgress = pulseProgress,
                                interactionRadiusPx = interactionRadiusPx,
                                accentColor = animatedAccentColor,
                            )
                        },
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { columnCoordinates = it },
                ) {
                    // Step 1: TRACK INFO (Stage 0)
                    PipelineStepNode(
                        stageIndex = 0,
                        iconRes = R.drawable.library_music,
                        sectionTitle = stringResource(R.string.audio_pipeline_section_track),
                        isLast = false,
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                        accentColor = animatedAccentColor,
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

                        if (details.source == "WebDAV" && badgeInfo.mediaId != null && !badgeInfo.mediaId.startsWith("webdav:")) {
                            val playerConnection = LocalPlayerConnection.current
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.cloud_upload),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.webdav_from_webdav),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            playerConnection?.switchToYouTube(badgeInfo.mediaId)
                                            onDismiss()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.webdav_switch_to_youtube),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Step 2: DECODER (Stage 1)
                    PipelineStepNode(
                        stageIndex = 1,
                        iconRes = R.drawable.settings,
                        sectionTitle = stringResource(R.string.audio_pipeline_section_decoder),
                        isLast = false,
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                        accentColor = animatedAccentColor,
                    ) {
                        PipelineField(label = stringResource(R.string.audio_pipeline_decoder_name), value = details.decoderName)
                        PipelineField(label = stringResource(R.string.audio_pipeline_decoder_format), value = details.decoderFormat)
                    }

                    // Step 3: RESAMPLER (Stage 2)
                    PipelineStepNode(
                        stageIndex = 2,
                        iconRes = R.drawable.tune,
                        sectionTitle = stringResource(R.string.audio_pipeline_section_resampler),
                        isLast = false,
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                        accentColor = animatedAccentColor,
                    ) {
                        PipelineField(label = stringResource(R.string.audio_pipeline_io_rate), value = details.ioRate)
                        PipelineField(label = stringResource(R.string.audio_pipeline_resampler_type), value = details.resamplerType)
                        PipelineField(label = stringResource(R.string.audio_pipeline_cutoff), value = details.cutoff)
                        PipelineField(label = stringResource(R.string.audio_pipeline_quality), value = details.resamplerQuality)
                        if (statusString.isNotBlank()) {
                            PipelineField(label = stringResource(R.string.audio_pipeline_status), value = statusString)
                        }
                    }

                    // Step 4: DSP (Stage 3)
                    PipelineStepNode(
                        stageIndex = 3,
                        iconRes = R.drawable.graphic_eq,
                        sectionTitle = stringResource(R.string.audio_pipeline_section_dsp),
                        isLast = false,
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                        accentColor = animatedAccentColor,
                    ) {
                        PipelineField(label = stringResource(R.string.audio_pipeline_pcm_format), value = details.dspPcmFormat)
                        PipelineField(label = stringResource(R.string.audio_pipeline_sample_rate), value = details.dspSampleRate)
                        PipelineField(label = stringResource(R.string.audio_pipeline_gain), value = details.dspGain)
                        PipelineField(label = stringResource(R.string.audio_pipeline_measured), value = details.dspMeasured)
                        PipelineField(label = stringResource(R.string.audio_pipeline_eq_preset), value = details.dspEqPreset)
                        PipelineField(label = stringResource(R.string.audio_pipeline_stereo_expand), value = details.dspStereoExpand)
                        PipelineField(label = stringResource(R.string.audio_pipeline_buffers), value = details.dspBuffers)
                        PipelineField(label = stringResource(R.string.audio_pipeline_output_api), value = details.dspOutputApi)
                        PipelineField(label = stringResource(R.string.audio_pipeline_bit_exact), value = details.dspBitExact)
                    }

                    // Step 5: OUTPUT DEVICE (Stage 4, isLast = true)
                    PipelineStepNode(
                        stageIndex = 4,
                        iconVector = details.outputDeviceIcon,
                        sectionTitle = stringResource(R.string.audio_pipeline_section_output),
                        isLast = true,
                        columnCoordinates = columnCoordinates,
                        stageActivationProvider = stageActivation,
                        onStageIconPositioned = onStageIconPositioned,
                        accentColor = animatedAccentColor,
                    ) {
                        PipelineField(label = stringResource(R.string.audio_pipeline_device_name), value = details.deviceName)
                        PipelineField(label = stringResource(R.string.audio_pipeline_route), value = details.routeKind)
                        PipelineField(label = stringResource(R.string.audio_pipeline_transport), value = details.transport)
                        PipelineField(label = stringResource(R.string.audio_pipeline_direct), value = details.directStatus)
                        PipelineField(label = stringResource(R.string.audio_pipeline_audiotrack), value = details.audioTrackSpec)
                        PipelineField(label = stringResource(R.string.audio_pipeline_system), value = details.systemMixerSpec)
                    }
                }
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

/**
 * Draws the signal bus line, the reactive node anchor halos, and the bioluminescent glowing snake wave
 * with a high-intensity white photon head extracted from the album art.
 */
private fun DrawScope.drawSignalFlow(
    stageCenters: Map<Int, Offset>,
    flowAlpha: Float,
    pulseProgress: Float,
    interactionRadiusPx: Float,
    accentColor: Color,
) {
    if (stageCenters.size < 5) return

    val p0 = stageCenters[0] ?: return
    val p1 = stageCenters[1] ?: return
    val p2 = stageCenters[2] ?: return
    val p3 = stageCenters[3] ?: return
    val p4 = stageCenters[4] ?: return

    val xCenter = p0.x
    val y0 = p0.y
    val y4 = p4.y
    val totalHeight = y4 - y0
    if (totalHeight <= 0f) return

    val stagePoints = listOf(p0, p1, p2, p3, p4)
    val nodeRadius = 14.dp.toPx()

    // 1. Static Node-to-Node Signal Bus Line
    val baseBusAlpha = if (flowAlpha > 0f) 0.22f + 0.08f * flowAlpha else 0.18f
    val busStroke = 2.dp.toPx()

    for (i in 0 until stagePoints.size - 1) {
        val yStart = stagePoints[i].y + nodeRadius
        val yEnd = stagePoints[i + 1].y - nodeRadius
        if (yEnd > yStart) {
            drawLine(
                color = Color.White.copy(alpha = baseBusAlpha),
                start = Offset(xCenter, yStart),
                end = Offset(xCenter, yEnd),
                strokeWidth = busStroke,
                cap = StrokeCap.Round,
            )

            // Subtle ambient neon under-glow along the bus when powered
            if (flowAlpha > 0f) {
                drawLine(
                    color = accentColor.copy(alpha = 0.12f * flowAlpha),
                    start = Offset(xCenter, yStart),
                    end = Offset(xCenter, yEnd),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    // 2. Dynamic Travelling Snake Wave with Luminous Photon Head
    if (flowAlpha > 0f && pulseProgress <= 0.95f) {
        val pulseVisibility = if (pulseProgress <= 0.85f) {
            1f
        } else {
            (1f - (pulseProgress - 0.85f) / 0.10f).coerceIn(0f, 1f)
        } * flowAlpha

        if (pulseVisibility > 0.01f) {
            val s = (pulseProgress / 0.85f).coerceIn(0f, 1f)
            val yPulse = y0 + s * totalHeight

            val tailLength = 65.dp.toPx()
            val waveAmplitude = 4.2.dp.toPx()
            val waveFrequency = 0.075f

            for (i in 0 until stagePoints.size - 1) {
                val yStart = stagePoints[i].y + nodeRadius
                val yEnd = stagePoints[i + 1].y - nodeRadius
                if (yEnd <= yStart) continue

                val pStart = max(yStart, yPulse - tailLength)
                val pEnd = min(yEnd, yPulse)

                if (pEnd > pStart) {
                    val path = Path()
                    val step = 3f
                    var currY = pStart
                    var isFirst = true

                    while (currY <= pEnd) {
                        val distFromHead = yPulse - currY
                        val distNormalized = (distFromHead / tailLength).coerceIn(0f, 1f)
                        val tailEnvelope = sin((1f - distNormalized) * (PI.toFloat() / 2f))

                        val boundaryClamping = ((currY - yStart) / 12.dp.toPx()).coerceIn(0f, 1f) *
                            ((yEnd - currY) / 12.dp.toPx()).coerceIn(0f, 1f)

                        val envelope = tailEnvelope * boundaryClamping
                        val phase = (pulseProgress * 2f * PI.toFloat() * 2.2f)
                        val xOffset = waveAmplitude * envelope * sin(distFromHead * waveFrequency + phase)

                        if (isFirst) {
                            path.moveTo(xCenter + xOffset, currY)
                            isFirst = false
                        } else {
                            path.lineTo(xCenter + xOffset, currY)
                        }
                        currY += step
                    }

                    // A. Broad Neon Ambient Glow of the Snake in Album Art Accent Color
                    drawPath(
                        path = path,
                        color = accentColor.copy(alpha = 0.40f * pulseVisibility),
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // B. Secondary Vivid Glow
                    drawPath(
                        path = path,
                        color = accentColor.copy(alpha = 0.70f * pulseVisibility),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // C. Brilliant High-Intensity White Core
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.95f * pulseVisibility),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                    )

                    // D. Leading White Glowing Circle / Photon Head
                    if (yPulse in yStart..yEnd) {
                        val headCenter = Offset(xCenter, yPulse)
                        // Radiant Corona (Album Art Color + White)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.85f * pulseVisibility),
                                    accentColor.copy(alpha = 0.55f * pulseVisibility),
                                    Color.Transparent,
                                ),
                                center = headCenter,
                                radius = 11.dp.toPx(),
                            ),
                            radius = 11.dp.toPx(),
                            center = headCenter,
                        )
                        // White Photon Core
                        drawCircle(
                            color = Color.White.copy(alpha = 0.98f * pulseVisibility),
                            radius = 3.2.dp.toPx(),
                            center = headCenter,
                        )
                    }
                }
            }
        }
    }

    // 3. Stage Node Anchors with Album Art Reactive Glow Flare
    for (i in stagePoints.indices) {
        val stageCenter = Offset(xCenter, stagePoints[i].y)
        val act = calculateStageActivation(i, pulseProgress, flowAlpha, stageCenters, interactionRadiusPx)

        // A. Subtle Ambient Ring
        val haloAlpha = 0.08f + 0.35f * act * flowAlpha
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to accentColor.copy(alpha = haloAlpha * 0.35f),
                    0.50f to accentColor.copy(alpha = haloAlpha),
                    1.0f to Color.Transparent,
                ),
                center = stageCenter,
                radius = nodeRadius + 6.dp.toPx(),
            ),
            radius = nodeRadius + 6.dp.toPx(),
            center = stageCenter,
        )

        // B. Active Luminous Pulse Ring when Head is present
        if (flowAlpha > 0f && act > 0.02f) {
            drawCircle(
                color = accentColor.copy(alpha = 0.45f * act * flowAlpha),
                radius = nodeRadius + 1.5.dp.toPx(),
                center = stageCenter,
                style = Stroke(width = 2.2.dp.toPx()),
            )
        }

        // C. Precision Micro-Border Ring
        val ringAlpha = (0.28f + 0.65f * act * flowAlpha).coerceIn(0f, 1f)
        drawCircle(
            color = Color.White.copy(alpha = ringAlpha),
            radius = nodeRadius,
            center = stageCenter,
            style = Stroke(width = 1.2.dp.toPx()),
        )

        // D. Core Bright Edge Flare
        if (flowAlpha > 0f && act > 0.05f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.70f * act * flowAlpha),
                radius = nodeRadius,
                center = stageCenter,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

private fun calculateStageActivation(
    stageIndex: Int,
    progress: Float,
    flowAlpha: Float,
    stageCenters: Map<Int, Offset>,
    interactionRadiusPx: Float,
): Float {
    if (flowAlpha <= 0.001f || stageCenters.size < 5) return 0f
    val p0 = stageCenters[0] ?: return 0f
    val p4 = stageCenters[4] ?: return 0f
    val totalHeight = p4.y - p0.y
    if (totalHeight <= 0f) return 0f

    val targetY = stageCenters[stageIndex]?.y ?: return 0f

    val yPulse = if (progress <= 0.85f) {
        val s = (progress / 0.85f).coerceIn(0f, 1f)
        p0.y + s * totalHeight
    } else {
        p4.y
    }

    val dy = abs(yPulse - targetY)
    if (dy > interactionRadiusPx) return 0f

    val p = 1f - (dy / interactionRadiusPx)
    val raw = (0.5f - 0.5f * kotlin.math.cos(p * PI.toFloat())).coerceIn(0f, 1f)

    val absorption = if (stageIndex == 4 && progress > 0.85f) {
        if (progress >= 0.95f) 0f else 1f - ((progress - 0.85f) / 0.10f)
    } else 1f

    return (raw * absorption * flowAlpha).coerceIn(0f, 1f)
}

@Composable
private fun PipelineStepNode(
    stageIndex: Int,
    iconRes: Int? = null,
    iconVector: ImageVector? = null,
    sectionTitle: String,
    isLast: Boolean,
    columnCoordinates: LayoutCoordinates?,
    stageActivationProvider: (Int) -> Float,
    onStageIconPositioned: (Int, Offset) -> Unit,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    var iconCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun reportCenter(child: LayoutCoordinates?, parent: LayoutCoordinates?) {
        if (child == null || parent == null || !child.isAttached || !parent.isAttached) return
        val center = parent.localPositionOf(
            child,
            Offset(child.size.width / 2f, child.size.height / 2f),
        )
        onStageIconPositioned(stageIndex, center)
    }

    LaunchedEffect(iconCoordinates, columnCoordinates) {
        reportCenter(iconCoordinates, columnCoordinates)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        val act = stageActivationProvider(stageIndex)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(36.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .onGloballyPositioned { coords ->
                        iconCoordinates = coords
                        reportCenter(coords, columnCoordinates)
                    }
                    .background(
                        MaterialTheme.colorScheme.surface,
                        CircleShape,
                    )
                    .border(
                        width = if (act > 0.05f) 1.5.dp else 1.dp,
                        color = if (act > 0.05f) {
                            accentColor.copy(alpha = (0.35f + 0.65f * act))
                        } else {
                            Color.White.copy(alpha = 0.15f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer {
                                alpha = (0.75f + 0.25f * act).coerceIn(0f, 1f)
                            },
                        tint = if (act > 0.05f) {
                            lerp(MaterialTheme.colorScheme.onSurface, Color.White, act)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                    )
                } else if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        modifier = Modifier
                            .size(15.dp)
                            .graphicsLayer {
                                alpha = (0.75f + 0.25f * act).coerceIn(0f, 1f)
                            },
                        tint = if (act > 0.05f) {
                            lerp(MaterialTheme.colorScheme.onSurface, Color.White, act)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 6.dp else 18.dp),
        ) {
            val act = stageActivationProvider(stageIndex)
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 12.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    alpha = (0.60f + 0.38f * act).coerceIn(0f, 1f)
                },
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
            .padding(vertical = 1.5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )
    }
}
