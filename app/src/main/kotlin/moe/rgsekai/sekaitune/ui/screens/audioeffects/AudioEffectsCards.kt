package moe.rgsekai.sekaitune.ui.screens.audioeffects

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.AudioNormalizationKey
import moe.rgsekai.sekaitune.eq.MaxPlaybackSpeed
import moe.rgsekai.sekaitune.eq.MinPlaybackSpeed
import moe.rgsekai.sekaitune.eq.PlaybackSettings
import moe.rgsekai.sekaitune.eq.ReverbPresets
import moe.rgsekai.sekaitune.eq.audio.ReplayGainMode
import moe.rgsekai.sekaitune.eq.audio.SpatialMode
import moe.rgsekai.sekaitune.ui.component.GlassComponent
import moe.rgsekai.sekaitune.ui.component.LocalGlassEffectConfig
import moe.rgsekai.sekaitune.ui.component.liquidGlass
import moe.rgsekai.sekaitune.utils.rememberPreference
import moe.rgsekai.sekaitune.viewmodels.AudioEffectsViewModel
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

// ---- 1. Reverb Card -------------------------------------------------------------------------

@Composable
fun ReverbCard(viewModel: AudioEffectsViewModel) {
    val reverb by viewModel.reverb.collectAsState()

    EffectCard(
        picture = { ReverbPicture(reverb) },
        header = {
            EffectToggleRow(
                title = stringResource(R.string.fx_reverb_title),
                checked = reverb.enabled,
                onCheckedChange = { on -> viewModel.updateReverb { it.copy(enabled = on) } },
            )
        },
        expanded = reverb.enabled,
        options = {
            val chips = ReverbPresets.All.map { EffectChipItem(it.id, it.name) } +
                (if (reverb.presetId == null) listOf(EffectChipItem("custom", "Custom")) else emptyList())

            EffectChips(
                chips = chips,
                selectedKey = reverb.presetId ?: "custom",
                onSelect = { id ->
                    ReverbPresets.All.firstOrNull { it.id == id }?.let { p ->
                        viewModel.updateReverb { it.withPreset(p) }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            EffectPanel {
                EffectSlider(
                    label = "Mix",
                    valueLabel = pct(reverb.mix),
                    value = reverb.mix,
                    onValueChange = { v -> viewModel.updateReverb { it.copy(mix = v, presetId = null) } },
                    valueRange = 0f..1f,
                )
                EffectSlider(
                    label = "Room size",
                    valueLabel = pct(reverb.roomSize),
                    value = reverb.roomSize,
                    onValueChange = { v -> viewModel.updateReverb { it.copy(roomSize = v, presetId = null) } },
                    valueRange = 0f..1f,
                )
                EffectSlider(
                    label = "Damping",
                    valueLabel = pct(reverb.damping),
                    value = reverb.damping,
                    onValueChange = { v -> viewModel.updateReverb { it.copy(damping = v, presetId = null) } },
                    valueRange = 0f..1f,
                )
                EffectSlider(
                    label = "Wet level",
                    valueLabel = pct(reverb.wet),
                    value = reverb.wet,
                    onValueChange = { v -> viewModel.updateReverb { it.copy(wet = v, presetId = null) } },
                    valueRange = 0f..1f,
                )
                Text(
                    text = "FINE-TUNING",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                EffectSlider(
                    label = "Dry level",
                    valueLabel = pct(reverb.dry),
                    value = reverb.dry,
                    onValueChange = { v -> viewModel.updateReverb { it.copy(dry = v, presetId = null) } },
                    valueRange = 0f..1f,
                )
                EffectSlider(
                    label = "Stereo width",
                    valueLabel = pct(reverb.width),
                    value = reverb.width,
                    onValueChange = { v -> viewModel.updateReverb { it.copy(width = v, presetId = null) } },
                    valueRange = 0f..1f,
                )
                EffectSlider(
                    label = "Pre-delay",
                    valueLabel = "${reverb.preDelayMs.roundToInt()} ms",
                    value = reverb.preDelayMs,
                    onValueChange = { v -> viewModel.updateReverb { it.copy(preDelayMs = v.roundToInt().toFloat(), presetId = null) } },
                    valueRange = 0f..120f,
                )
                EffectSlider(
                    label = "Low-cut filter",
                    valueLabel = "${reverb.lowCutHz.roundToInt()} Hz",
                    value = log10(reverb.lowCutHz),
                    onValueChange = { v -> viewModel.updateReverb { it.copy(lowCutHz = 10f.pow(v).roundToInt().toFloat(), presetId = null) } },
                    valueRange = log10(20f)..log10(1000f),
                )
            }
        },
    )
}

// ---- 2 & 3. Loudness & Even Out Songs Card ----------------------------------------------------

@Composable
fun LoudnessCard(viewModel: AudioEffectsViewModel) {
    val loudness by viewModel.loudness.collectAsState()
    val (normalize, onNormalize) = rememberPreference(AudioNormalizationKey, defaultValue = true)
    val rg by viewModel.normalize.collectAsState()

    EffectCard(
        picture = { LoudnessPicture(loudness, viewModel.effectsService) },
        header = {
            EffectToggleRow(
                title = stringResource(R.string.fx_loudness_title),
                checked = loudness.enabled,
                onCheckedChange = { on -> viewModel.updateLoudness { it.copy(enabled = on) } },
            )
        },
        expanded = loudness.enabled,
        options = {
            val chips = listOf(
                EffectChipItem("gentle", "Gentle"),
                EffectChipItem("punchy", "Punchy"),
                EffectChipItem("night", "Night"),
                EffectChipItem("max", "Max"),
            )
            val selectedKey = when {
                loudness.boostDb == 2f && loudness.compression == 0.15f -> "gentle"
                loudness.boostDb == 4f && loudness.compression == 0.3f -> "punchy"
                loudness.boostDb == 3f && loudness.compression == 0.85f -> "night"
                loudness.boostDb == 9f && loudness.compression == 0.5f -> "max"
                else -> null
            }

            EffectChips(
                chips = chips,
                selectedKey = selectedKey,
                onSelect = { key ->
                    val (boost, comp) = when (key) {
                        "gentle" -> 2f to 0.15f
                        "night" -> 3f to 0.85f
                        "max" -> 9f to 0.5f
                        else -> 4f to 0.3f
                    }
                    viewModel.updateLoudness { it.copy(boostDb = boost, compression = comp) }
                },
            )
            Spacer(Modifier.height(10.dp))
            EffectPanel {
                EffectSlider(
                    label = "Boost",
                    valueLabel = "+${String.format("%.1f", loudness.boostDb)} dB",
                    value = loudness.boostDb,
                    onValueChange = { v -> viewModel.updateLoudness { it.copy(boostDb = (v * 2).roundToInt() / 2f) } },
                    valueRange = 0f..12f,
                )
                EffectSlider(
                    label = "Compression",
                    valueLabel = pct(loudness.compression),
                    value = loudness.compression,
                    onValueChange = { v -> viewModel.updateLoudness { it.copy(compression = (v * 100).roundToInt() / 100f) } },
                    valueRange = 0f..1f,
                )
                Text(
                    text = "Compression lifts quiet passages while keeping peaks cleanly below clipping.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        footer = {
            Spacer(Modifier.height(10.dp))
            EffectToggleRow(
                title = stringResource(R.string.fx_even_out_title),
                checked = normalize,
                onCheckedChange = { on ->
                    onNormalize(on)
                    viewModel.setNormalizeEnabled(on)
                },
            )
            EffectCollapsible(normalize) {
                Spacer(Modifier.height(8.dp))
                EffectPanel {
                    Text(
                        text = "REPLAYGAIN",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    EffectChips(
                        chips = listOf(
                            EffectChipItem("track", "Track"),
                            EffectChipItem("album", "Album"),
                        ),
                        selectedKey = if (rg.replayGainMode == ReplayGainMode.Album) "album" else "track",
                        onSelect = { key ->
                            viewModel.updateNormalize {
                                it.copy(replayGainMode = if (key == "album") ReplayGainMode.Album else ReplayGainMode.Track)
                            }
                        },
                    )
                    EffectSlider(
                        label = "Preamp",
                        valueLabel = (if (rg.preampDb > 0) "+" else "") + String.format("%.1f dB", rg.preampDb),
                        value = rg.preampDb,
                        onValueChange = { v -> viewModel.updateNormalize { it.copy(preampDb = (v * 2).roundToInt() / 2f) } },
                        valueRange = -6f..6f,
                    )
                    Text(
                        text = "Track keeps every song level; Album preserves intended loudness dynamics across an album. Streamed songs use track loudness metadata, and local audio uses ReplayGain tags.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
    )
}

// ---- 4. Spatial Audio Card ------------------------------------------------------------------

private class CrossfeedPreset(val key: String, val label: String, val cutoff: Float, val feed: Float)

private val CrossfeedPresets = listOf(
    CrossfeedPreset("light", "Light", 700f, 4.5f),
    CrossfeedPreset("natural", "Natural", 700f, 6f),
    CrossfeedPreset("strong", "Strong", 650f, 9.5f),
)

@Composable
fun SpatialCard(viewModel: AudioEffectsViewModel) {
    val spatial by viewModel.spatial.collectAsState()

    EffectCard(
        picture = { SpatialPicture(spatial) },
        header = {
            EffectToggleRow(
                title = stringResource(R.string.fx_spatial_title),
                checked = spatial.enabled,
                onCheckedChange = { on -> viewModel.updateSpatial { it.copy(enabled = on) } },
            )
        },
        expanded = spatial.enabled,
        options = {
            EffectSegmented(
                options = listOf("Crossfeed", "Virtual Speakers"),
                selectedIndex = if (spatial.mode == SpatialMode.Crossfeed) 0 else 1,
                onSelect = { i ->
                    viewModel.updateSpatial { it.copy(mode = if (i == 0) SpatialMode.Crossfeed else SpatialMode.Speakers) }
                },
                height = 42.dp,
            )
            Spacer(Modifier.height(10.dp))
            if (spatial.mode == SpatialMode.Crossfeed) {
                EffectChips(
                    chips = CrossfeedPresets.map { EffectChipItem(it.key, it.label) },
                    selectedKey = CrossfeedPresets.firstOrNull { it.cutoff == spatial.cutoffHz && it.feed == spatial.feedDb }?.key,
                    onSelect = { key ->
                        CrossfeedPresets.firstOrNull { it.key == key }?.let { l ->
                            viewModel.updateSpatial { it.copy(cutoffHz = l.cutoff, feedDb = l.feed) }
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                EffectPanel {
                    EffectSlider(
                        label = "Head shadow cutoff",
                        valueLabel = "${spatial.cutoffHz.roundToInt()} Hz",
                        value = spatial.cutoffHz,
                        onValueChange = { v -> viewModel.updateSpatial { it.copy(cutoffHz = (v / 10).roundToInt() * 10f) } },
                        valueRange = 300f..1500f,
                    )
                    EffectSlider(
                        label = "Crossfeed attenuation",
                        valueLabel = "−${String.format("%.1f", spatial.feedDb)} dB",
                        value = 16f - spatial.feedDb,
                        onValueChange = { v -> viewModel.updateSpatial { it.copy(feedDb = ((16f - v) * 2).roundToInt() / 2f) } },
                        valueRange = 1f..15f,
                    )
                    EffectSlider(
                        label = "Amount",
                        valueLabel = pct(spatial.amount),
                        value = spatial.amount,
                        onValueChange = { v -> viewModel.updateSpatial { it.copy(amount = v) } },
                        valueRange = 0f..1f,
                    )
                }
            } else {
                EffectPanel {
                    EffectSlider(
                        label = "Speaker angle",
                        valueLabel = "±${spatial.speakerAngle.roundToInt()}°",
                        value = spatial.speakerAngle,
                        onValueChange = { v -> viewModel.updateSpatial { it.copy(speakerAngle = v.roundToInt().toFloat()) } },
                        valueRange = 10f..90f,
                    )
                    EffectSlider(
                        label = "Amount",
                        valueLabel = pct(spatial.amount),
                        value = spatial.amount,
                        onValueChange = { v -> viewModel.updateSpatial { it.copy(amount = v) } },
                        valueRange = 0f..1f,
                    )
                    Text(
                        text = "Brown & Duda spherical head acoustic modeling simulates sound arrival delays and head-shadow filtering from virtual speakers.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
    )
}

// ---- 5. Mono Audio Card ---------------------------------------------------------------------

@Composable
fun MonoCard(viewModel: AudioEffectsViewModel) {
    val spatial by viewModel.spatial.collectAsState()
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.isEnabledFor(GlassComponent.POPUP_MENU)
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isGlass) {
                    Modifier.liquidGlass(glassConfig, shape = cardShape)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer, cardShape)
                        .padding(1.dp)
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        EffectToggleRow(
            title = stringResource(R.string.fx_mono_title),
            checked = spatial.mono,
            onCheckedChange = { on -> viewModel.updateSpatial { it.copy(mono = on) } },
            leadingDot = true,
        )
    }
}

// ---- 6. Dolby Atmos & Surround Card ---------------------------------------------------------

@Composable
fun AtmosCard(viewModel: AudioEffectsViewModel) {
    val spatial by viewModel.spatial.collectAsState()
    val context = LocalContext.current
    var deviceSupport by remember { mutableStateOf<AtmosSupportInfo?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        deviceSupport = checkAtmosSupport(context)
    }

    EffectCard(
        picture = { AtmosPicture(spatial.surround, renderOnHeadphones = true) },
        header = {
            EffectToggleRow(
                title = stringResource(R.string.fx_atmos_title),
                checked = spatial.surround,
                onCheckedChange = { on -> viewModel.updateSpatial { it.copy(surround = on) } },
            )
        },
        expanded = false,
        options = {},
        footer = {
            Text(
                text = "Surround and multi-channel audio tracks (5.1 and 7.1 local files, FLAC, AAC, AC-3) are downmixed binaurally for headphones. Stereo tracks use Spatial Audio.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            )
            deviceSupport?.let { support ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SupportPill("Atmos Decoder", support.decoder)
                    SupportPill("Android Spatializer", support.spatializer)
                }
            }
        },
    )
}

private class AtmosSupportInfo(val decoder: String, val spatializer: String)

private fun checkAtmosSupport(context: Context): AtmosSupportInfo {
    val decoderFound = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals("audio/eac3-joc", ignoreCase = true) }
        }
    }.getOrDefault(false)

    val spatializerStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
        runCatching {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val spatializer = manager.spatializer
            when {
                !spatializer.isAvailable -> "Not available"
                spatializer.isEnabled -> "On"
                else -> "Off"
            }
        }.getOrDefault("Unknown")
    } else {
        "Needs Android 12L+"
    }

    return AtmosSupportInfo(
        decoder = if (decoderFound) "Available" else "Not on this phone",
        spatializer = spatializerStatus,
    )
}

// ---- 7. Playback Speed Card -----------------------------------------------------------------

private class SpeedPresetItem(val key: String, val label: String, val speed: Float, val preservePitch: Boolean, val withReverb: Boolean = false)

private val SpeedPresetItems = listOf(
    SpeedPresetItem("normal", "Normal", 1f, true),
    SpeedPresetItem("slowed", "Slowed", 0.85f, false),
    SpeedPresetItem("slowed_reverb", "Slowed + reverb", 0.85f, false, withReverb = true),
    SpeedPresetItem("nightcore", "Nightcore", 1.25f, false),
    SpeedPresetItem("0.75", "0.75×", 0.75f, true),
    SpeedPresetItem("1.25", "1.25×", 1.25f, true),
    SpeedPresetItem("1.5", "1.5×", 1.5f, true),
    SpeedPresetItem("2", "2×", 2f, true),
)

@Composable
fun SpeedCard(viewModel: AudioEffectsViewModel) {
    val playback by viewModel.playback.collectAsState()
    val reverb by viewModel.reverb.collectAsState()

    EffectCard(
        picture = { SpeedPicture(playback) },
        header = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.fx_speed_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (playback.preservePitch) "Pitch stays where it is" else "Pitch follows speed",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StepButton("−") {
                    viewModel.updatePlayback {
                        it.copy(speed = ((it.speed - 0.05f) * 100).roundToInt() / 100f)
                    }
                }
                Text(
                    text = String.format("%.2f×", playback.speed),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                StepButton("+") {
                    viewModel.updatePlayback {
                        it.copy(speed = ((it.speed + 0.05f) * 100).roundToInt() / 100f)
                    }
                }
            }
        },
        expanded = true,
        options = {
            val selectedKey = if (playback.speed == 0.85f && !playback.preservePitch) {
                if (reverb.enabled) "slowed_reverb" else "slowed"
            } else {
                SpeedPresetItems.firstOrNull { p ->
                    !p.withReverb && p.speed == playback.speed && p.preservePitch == playback.preservePitch &&
                        (!p.preservePitch || playback.pitchSemitones == 0f)
                }?.key
            }

            EffectChips(
                chips = SpeedPresetItems.map { EffectChipItem(it.key, it.label) },
                selectedKey = selectedKey,
                onSelect = { key ->
                    val p = SpeedPresetItems.first { it.key == key }
                    viewModel.updatePlayback { it.copy(speed = p.speed, preservePitch = p.preservePitch, pitchSemitones = 0f) }
                    if (p.withReverb) {
                        ReverbPresets.All.firstOrNull { it.id == "hall" }?.let { hall ->
                            viewModel.updateReverb { it.withPreset(hall).copy(enabled = true) }
                        }
                    } else if (key == "slowed" && reverb.enabled) {
                        viewModel.updateReverb { it.copy(enabled = false) }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            EffectPanel {
                EffectSlider(
                    label = "Speed",
                    valueLabel = String.format("%.2f×", playback.speed),
                    value = log10(playback.speed),
                    onValueChange = { v ->
                        viewModel.updatePlayback { it.copy(speed = (10f.pow(v) * 100).roundToInt() / 100f) }
                    },
                    valueRange = log10(MinPlaybackSpeed)..log10(MaxPlaybackSpeed),
                )
                EffectToggleRow(
                    title = "Preserve pitch",
                    checked = playback.preservePitch,
                    onCheckedChange = { on -> viewModel.updatePlayback { it.copy(preservePitch = on) } },
                )
                EffectCollapsible(playback.preservePitch) {
                    EffectSlider(
                        label = "Pitch",
                        valueLabel = (if (playback.pitchSemitones > 0) "+" else "") + "${playback.pitchSemitones.roundToInt()} semitones",
                        value = playback.pitchSemitones,
                        onValueChange = { v -> viewModel.updatePlayback { it.copy(pitchSemitones = v.roundToInt().toFloat()) } },
                        valueRange = -12f..12f,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                EffectTextAction(
                    text = "Reset",
                    iconRes = R.drawable.refresh,
                    enabled = !playback.isNormal,
                ) {
                    viewModel.updatePlayback { PlaybackSettings() }
                }
            }
        },
    )
}

// ---- 8. Exponential Volume Card -------------------------------------------------------------

private class VolumeCurvePreset(val key: String, val label: String, val exponent: Float)

private val VolumeCurvePresets = listOf(
    VolumeCurvePreset("gentle", "Gentle", 1.6f),
    VolumeCurvePreset("exponential", "Exponential", 2.2f),
    VolumeCurvePreset("steep", "Steep", 3.2f),
)

@Composable
fun VolumeCard(viewModel: AudioEffectsViewModel) {
    val volume by viewModel.volume.collectAsState()
    val playerConnection = LocalPlayerConnection.current
    val currentVolume = playerConnection?.player?.volume ?: 0.6f

    EffectCard(
        picture = { VolumeCurvePicture(volume, currentVolume) },
        header = {
            EffectToggleRow(
                title = stringResource(R.string.fx_volume_title),
                checked = volume.enabled,
                onCheckedChange = { on -> viewModel.updateVolume { it.copy(enabled = on) } },
            )
        },
        expanded = volume.enabled,
        options = {
            EffectChips(
                chips = VolumeCurvePresets.map { EffectChipItem(it.key, it.label) },
                selectedKey = VolumeCurvePresets.firstOrNull { it.exponent == volume.exponent }?.key,
                onSelect = { key ->
                    VolumeCurvePresets.firstOrNull { it.key == key }?.let { s ->
                        viewModel.updateVolume { it.copy(exponent = s.exponent) }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            EffectPanel {
                EffectSlider(
                    label = "Curve exponent",
                    valueLabel = String.format("x^%.1f", volume.exponent),
                    value = volume.exponent,
                    onValueChange = { v -> viewModel.updateVolume { it.copy(exponent = (v * 10).roundToInt() / 10f) } },
                    valueRange = 1f..4f,
                )
                EffectSlider(
                    label = "Volume ceiling limit",
                    valueLabel = pct(volume.limit),
                    value = volume.limit,
                    onValueChange = { v -> viewModel.updateVolume { it.copy(limit = (v * 100).roundToInt() / 100f) } },
                    valueRange = 0.2f..1f,
                )
                Text(
                    text = "A power curve expands the lower range of the volume slider, giving delicate control at quiet listening levels.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
    )
}

