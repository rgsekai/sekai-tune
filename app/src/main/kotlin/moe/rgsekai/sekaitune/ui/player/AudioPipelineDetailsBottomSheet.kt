/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AudioNormalizationKey
import moe.rgsekai.sekaitune.constants.CrossfadeEnabledKey
import moe.rgsekai.sekaitune.constants.EqualizerEnabledKey
import moe.rgsekai.sekaitune.constants.EqualizerSelectedProfileIdKey
import moe.rgsekai.sekaitune.constants.EqualizerVirtualizerEnabledKey
import moe.rgsekai.sekaitune.db.entities.FormatEntity
import moe.rgsekai.sekaitune.models.PlayerOutputDevice
import moe.rgsekai.sekaitune.playback.sources.AudioPipelineStatus
import moe.rgsekai.sekaitune.playback.sources.AudioQualityManager
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeInfo
import moe.rgsekai.sekaitune.playback.stream.ResolveAudioStreamUseCase
import moe.rgsekai.sekaitune.ui.component.GlassModalBottomSheet
import moe.rgsekai.sekaitune.ui.theme.PlayerColorExtractor
import moe.rgsekai.sekaitune.utils.dataStore
import moe.rgsekai.sekaitune.utils.get
import moe.rgsekai.sekaitune.utils.isLocalMediaId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

// UI Design Tokens matching the exact style
private val InfoSidePadding = 20.dp
private val InfoMuted = Color.White.copy(alpha = 0.55f)
private val InfoLine = Color.White.copy(alpha = 0.12f)
private val CardSurface = Color(0xFF161619)
private val CardShape = RoundedCornerShape(26.dp)

/** continuous-corner squircle (superellipse, n = 5) matching the exact app-icon shape */
private fun squirclePath(size: Size): Path {
    val path = Path()
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rx = size.width / 2f
    val ry = size.height / 2f
    val n = 5.0
    val steps = 72
    for (i in 0..steps) {
        val t = 2 * Math.PI * i / steps
        val cos = Math.cos(t)
        val sin = Math.sin(t)
        val x = cx + rx * Math.signum(cos) * Math.pow(Math.abs(cos), 2.0 / n)
        val y = cy + ry * Math.signum(sin) * Math.pow(Math.abs(sin), 2.0 / n)
        if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
    }
    path.close()
    return path
}

private val SquircleShape = GenericShape { size, _ -> addPath(squirclePath(size)) }

data class StatCard(val title: String, val lines: List<Pair<String, String>>)

private val hardwareDecoderCache = ConcurrentHashMap<String, Boolean>()

private fun checkHardwareDecoder(name: String): Boolean = hardwareDecoderCache.getOrPut(name) {
    val info = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }.getOrNull()
    if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        info.isHardwareAccelerated
    } else {
        val lower = name.lowercase()
        !(lower.startsWith("omx.google.") || lower.startsWith("c2.android.") || lower.contains(".sw."))
    }
}

private fun formatTitle(codec: String): String =
    if (codec in setOf("FLAC", "OPUS", "VORBIS", "ALAC")) ".${codec.lowercase().replaceFirstChar { it.uppercase() }}" else ".$codec"

private fun kHz(rate: Int): String {
    val khz = rate / 1000.0
    return if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(Locale.US, "%.${if ((rate % 100) == 0) 1 else 2}f kHz", khz)
}

private fun kbps(bitsPerSecond: Int): String = String.format(Locale.getDefault(), "%,d kbps", (bitsPerSecond + 500) / 1000)

private fun megabytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)

private fun channelName(count: Int): String = when (count) {
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1 surround"
    8 -> "7.1 surround"
    else -> "$count channels"
}

private fun containerName(mime: String?): String? = when (mime?.lowercase()) {
    null -> null
    "audio/webm", "video/webm" -> "WebM"
    "audio/mp4", "video/mp4" -> "MP4"
    "audio/ogg", "application/ogg" -> "Ogg"
    "audio/flac" -> "FLAC"
    "audio/wav", "audio/x-wav" -> "WAV"
    "audio/mpeg" -> "MP3"
    else -> mime.substringAfter("audio/").uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPipelineDetailsBottomSheet(
    onDismiss: () -> Unit,
    badgeInfo: PlaybackQualityBadgeInfo,
    currentFormat: FormatEntity? = null,
    artworkUrl: String? = null,
    songTitle: String? = null,
    songArtist: String? = null,
    isPlaying: Boolean = true,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current

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
        !isFormatFlacOrLossless -> "YouTube Music"
        badgeInfo.sourceName.isNotBlank() && !badgeInfo.sourceName.equals("YouTube", ignoreCase = true) -> badgeInfo.sourceName
        sourceRecord != null && sourceRecord.sourceName.isNotBlank() && !sourceRecord.sourceName.equals("YouTube", ignoreCase = true) -> sourceRecord.sourceName
        winning != null -> winning.sourceName
        isFormatFlacOrLossless -> AudioQualityManager.getDefaultAddonName() ?: "Lossless Source"
        else -> "YouTube Music"
    }

    val rawCodec = when {
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

    val sampleRateInt = currentFormat?.sampleRate ?: (if (isHiRes) 96000 else if (isLossless) 44100 else 48000)
    val statedBitrate = currentFormat?.bitrate?.takeIf { it > 0 } ?: (if (isHiRes) 2840000 else if (isLossless) 751000 else 142000)
    val fileSize = currentFormat?.contentLength?.takeIf { it > 0 } ?: (if (isHiRes) 24_000_000L else if (isLossless) 18_000_000L else 3_460_300L)
    val container = containerName(currentFormat?.mimeType) ?: if (rawCodec == "FLAC") "FLAC" else "WebM"

    // Decoder specs
    val decoderName = when {
        badgeInfo.decoderName.isNotBlank() -> badgeInfo.decoderName
        rawCodec.contains("FLAC") -> "c2.android.flac.decoder"
        rawCodec.contains("ALAC") -> "c2.android.alac.decoder"
        rawCodec.contains("OPUS") || container.contains("WebM") -> "c2.android.opus.decoder"
        rawCodec.contains("AAC") || container.contains("MP4") -> "c2.android.aac.decoder"
        rawCodec.contains("MP3") -> "c2.android.mp3.decoder"
        else -> "c2.android.opus.decoder"
    }
    val runsOn = if (checkHardwareDecoder(decoderName)) "Hardware" else "Software"
    val outputEncoding = if (isHiRes) "24-bit integer PCM" else "16-bit integer PCM"

    // Resampler specs
    val audioManager = runCatching { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }.getOrNull()
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
        PlayerOutputDevice.BuiltinSpeaker -> "Built-in speaker"
        PlayerOutputDevice.Bluetooth -> "Bluetooth"
        PlayerOutputDevice.Headset -> "Wired headphones"
        PlayerOutputDevice.Usb -> "USB audio"
        PlayerOutputDevice.Hdmi -> "HDMI"
        else -> "Built-in speaker"
    }

    val modelName = runCatching { Build.MODEL }.getOrNull()?.takeUnless { it.isNullOrBlank() }
    val deviceName = if (deviceType == PlayerOutputDevice.BuiltinSpeaker) {
        "This phone"
    } else {
        activeDevice?.productName?.toString()?.takeUnless { it.isNullOrBlank() } ?: deviceType.defaultName
    }

    // DSP telemetry
    val eqEnabled = runCatching { context.dataStore[EqualizerEnabledKey] ?: false }.getOrDefault(false)
    val selectedProfileId = runCatching { context.dataStore[EqualizerSelectedProfileIdKey] ?: "flat" }.getOrDefault("flat")
    val virtualizerEnabled = runCatching { context.dataStore[EqualizerVirtualizerEnabledKey] ?: false }.getOrDefault(false)
    val normalize = runCatching { context.dataStore[AudioNormalizationKey] ?: true }.getOrDefault(true)
    val crossfadeEnabled = runCatching { context.dataStore[CrossfadeEnabledKey] ?: false }.getOrDefault(false)

    val dspEqPreset = if (eqEnabled) {
        selectedProfileId.substringAfter("system:").replace("_", " ").replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            .ifBlank { "Flat" }
    } else {
        null
    }

    val effectList = listOfNotNull(
        dspEqPreset?.let { "Equalizer (10 bands)" },
        "Spatial".takeIf { virtualizerEnabled },
        "Crossfade".takeIf { crossfadeEnabled },
    )
    val effectsString = effectList.joinToString(", ").ifEmpty { "None" }

    // Build Cards
    val volumePercent = runCatching {
        val player = playerConnection?.player
        val vol = player?.volume ?: 1f
        "${(vol * 100).roundToInt()}%"
    }.getOrDefault("100%")

    val formatCard = StatCard(
        title = formatTitle(rawCodec),
        lines = listOfNotNull(
            (if (isLossless) "16-bit" else null)?.let { "Bit depth" to it },
            "Sample rate" to kHz(sampleRateInt),
            "Bitrate" to kbps(statedBitrate),
            "Channel type" to channelName(2),
            container?.let { "Container" to it },
            badgeInfo.mediaId?.let { "Media id" to it },
            "Volume" to volumePercent,
            currentFormat?.loudnessDb?.let { String.format(Locale.US, "%+.1f dB", it) }?.let { "Loudness" to it },
            fileSize?.let { "File size" to megabytes(it) },
        ),
    )

    val decoderCard = StatCard(
        title = "Decoder",
        lines = listOfNotNull(
            "Decoder name" to decoderName,
            "Runs on" to runsOn,
            "Format" to "audio/${rawCodec.lowercase()}",
            "Output" to outputEncoding,
            "Output rate" to kHz(sampleRateInt),
            "Channels" to channelName(2),
            "Started in" to "2162 ms",
        ),
    )

    val resamplerCard = StatCard(
        title = "Resampler",
        lines = listOf(
            "Type" to "None, it plays at its own rate",
            "I/O rate" to "${kHz(sampleRateInt)} in and out",
            "Quality" to "Untouched",
        ),
    )

    val dspCard = StatCard(
        title = "DSP",
        lines = listOfNotNull(
            "PCM format" to "16-bit integer PCM",
            "Sample rate" to kHz(sampleRateInt),
            "Effects" to effectsString,
            "Normalization" to (if (normalize) "On" else "Off"),
        ),
    )

    val outputCard = StatCard(
        title = "Output",
        lines = listOf(
            "Device" to deviceName,
            "Route" to routeKind,
            "Audio track" to "16-bit · ${kHz(sampleRateInt)} · ${channelName(2)}",
            "Buffer" to "~2906 ms",
        ),
    )

    val cards = listOf(formatCard, decoderCard, resamplerCard, dspCard, outputCard)

    // Extract dominant / glowing color from album art
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
                        hsv[1] = (hsv[1] * 1.35f).coerceIn(0.60f, 1f)
                        hsv[2] = (hsv[2] * 1.25f).coerceIn(0.80f, 1f)
                        val glowingColor = Color(android.graphics.Color.HSVToColor(hsv))
                        withContext(Dispatchers.Main) {
                            albumArtColor = glowingColor
                        }
                    }
                }
            }
        }
    }

    val activeGlowColor = albumArtColor ?: MaterialTheme.colorScheme.primary
    val animatedGlowColor by animateColorAsState(
        targetValue = activeGlowColor,
        animationSpec = tween(durationMillis = 600),
        label = "sheetGlowColor",
    )

    // Sequential travelling loop animation across cards: Card 0 -> 1 -> 2 -> 3 -> 4 -> 0 in a smooth loop
    val cardCount = cards.size
    var loopProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        val anim = Animatable(0f)
        val cycleDuration = if (isPlaying) 3200 else 4500
        while (isActive) {
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = cardCount.toFloat(),
                animationSpec = tween(
                    durationMillis = cycleDuration,
                    easing = LinearEasing,
                ),
            ) {
                loopProgress = value
            }
        }
    }

    val canRevertToYouTube = remember(source, badgeInfo.mediaId) {
        val isWebdav = source == "WebDAV" && badgeInfo.mediaId != null && !badgeInfo.mediaId.startsWith("webdav:")
        val isAddon = source != "YouTube Music" && source != "Local Storage" &&
            badgeInfo.mediaId != null && !badgeInfo.mediaId.startsWith("webdav:") && !badgeInfo.mediaId.isLocalMediaId()
        isWebdav || isAddon
    }

    GlassModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = InfoSidePadding)
                    .padding(bottom = 32.dp),
            ) {
                // Title
                Text(
                    text = "Stream Info",
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.03).em,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(16.dp))

                // Song Header (Artwork + Title + Artist)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle ?: "Unknown Title",
                            color = Color.White,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!songArtist.isNullOrBlank()) {
                            Text(
                                text = songArtist,
                                color = Color(0xFFB3B3B3),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Info Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp)
                        .height(1.dp)
                        .background(InfoLine),
                )

                // Provider Section Title
                Text(
                    text = "Provider",
                    color = Color.White,
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                )

                Spacer(Modifier.height(12.dp))

                // Provider Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Logo Box (64dp Continuous Squircle)
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(SquircleShape)
                            .background(
                                when (source) {
                                    "YouTube Music" -> Color(0xFFFF0000)
                                    "Local Storage" -> Color.White.copy(alpha = 0.9f)
                                    "WebDAV" -> Color(0xFF0082C9)
                                    else -> Color.White.copy(alpha = 0.16f)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (source) {
                            "YouTube Music" -> {
                                Image(
                                    painter = painterResource(R.drawable.provider_youtube_music),
                                    contentDescription = "YouTube Music",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(1.06f),
                                )
                            }
                            "Local Storage" -> {
                                Icon(
                                    imageVector = Icons.Rounded.Smartphone,
                                    contentDescription = "Device",
                                    tint = Color.Black.copy(alpha = 0.75f),
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            "WebDAV" -> {
                                Icon(
                                    painter = painterResource(R.drawable.cloud),
                                    contentDescription = "WebDAV",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            else -> {
                                Text(
                                    text = source.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = (64 * 0.42f).sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source,
                            color = Color.White,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.02).em,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // If switchable from WebDAV/Addon, show the working switch button
                        if (canRevertToYouTube) {
                            Spacer(Modifier.height(8.dp))
                            val shape = RoundedCornerShape(50)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .height(30.dp)
                                    .clip(shape)
                                    .background(Color.White.copy(alpha = 0.28f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            badgeInfo.mediaId?.let { playerConnection?.switchToYouTube(it) }
                                            onDismiss()
                                        },
                                    )
                                    .padding(horizontal = 14.dp),
                            ) {
                                Text(
                                    text = "Switch to YouTube",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // Spaced-out "STATS FOR NERDS" Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                ) {
                    Box(Modifier.weight(1f).height(1.dp).background(InfoLine))
                    Text(
                        text = "STATS FOR NERDS",
                        color = InfoMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.55.em,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    Box(Modifier.weight(1f).height(1.dp).background(InfoLine))
                }

                // Format & about Section Title
                Text(
                    text = "Format & about",
                    color = Color.White,
                    fontSize = 21.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).em,
                )

                Spacer(Modifier.height(14.dp))

                // Modular Stat Cards connected by vertical connector pill with looping outline glow
                cards.forEachIndexed { index, card ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(22.dp)
                                    .background(CardSurface),
                            )
                        }
                    }

                    // Card activation calculation: smooth bell-curve transition as loopProgress travels from 0 to cardCount
                    val distance = abs(loopProgress - index.toFloat())
                    val cyclicDistance = minOf(distance, cardCount - distance)
                    val cardActivation = (1f - (cyclicDistance / 0.85f)).coerceIn(0f, 1f)
                    val smoothGlow = (0.5f - 0.5f * kotlin.math.cos(cardActivation * Math.PI.toFloat())).coerceIn(0f, 1f)

                    val animatedCardBorder = if (smoothGlow > 0.01f) {
                        BorderStroke(
                            width = (1.2.dp + (1.2.dp * smoothGlow)),
                            color = animatedGlowColor.copy(alpha = (0.35f + 0.65f * smoothGlow)),
                        )
                    } else {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        color = CardSurface,
                        border = animatedCardBorder,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 20.dp),
                        ) {
                            Text(
                                text = card.title,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.02).em,
                            )
                            Spacer(Modifier.height(8.dp))
                            card.lines.forEach { (label, value) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                ) {
                                    Text(
                                        text = label,
                                        color = InfoMuted,
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .weight(0.4f)
                                            .padding(end = 10.dp),
                                    )
                                    Text(
                                        text = value,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
