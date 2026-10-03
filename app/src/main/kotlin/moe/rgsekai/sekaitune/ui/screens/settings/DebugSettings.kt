/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rgsekai.sekaitune.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.Player
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.LocalPlayerAwareWindowInsets
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.PlayerStreamClient
import moe.rgsekai.sekaitune.constants.PlayerStreamClientKey
import moe.rgsekai.sekaitune.ui.component.IconButton
import moe.rgsekai.sekaitune.ui.component.ListPreference
import moe.rgsekai.sekaitune.ui.component.PreferenceEntry
import moe.rgsekai.sekaitune.ui.component.PreferenceGroup
import moe.rgsekai.sekaitune.ui.component.SwitchPreference
import moe.rgsekai.sekaitune.ui.utils.backToMain
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettings(navController: NavController) {
    val (showNerdStats, onShowNerdStatsChange) =
        rememberPreference(
            key = booleanPreferencesKey("dev_show_nerd_stats"),
            defaultValue = false,
        )

    val (showCodecOnPlayer, onShowCodecOnPlayerChange) =
        rememberPreference(
            key = booleanPreferencesKey("show_codec_on_player"),
            defaultValue = false,
        )

    val (playerStreamClient, onPlayerStreamClientChange) =
        rememberEnumPreference(
            PlayerStreamClientKey,
            defaultValue = PlayerStreamClient.ANDROID_VR,
        )
    val playerStreamClients =
        remember {
            listOf(
                PlayerStreamClient.ANDROID_VR,
                PlayerStreamClient.WEB_REMIX,
            )
        }
    val selectedPlayerStreamClient =
        if (playerStreamClient in playerStreamClients) {
            playerStreamClient
        } else {
            PlayerStreamClient.ANDROID_VR
        }

    val playerConnection = LocalPlayerConnection.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.experiment_settings),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding: PaddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreferenceGroup(title = stringResource(R.string.experimental_features)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_nerd_stats)) },
                        description = stringResource(R.string.description_show_nerd_stats),
                        icon = { Icon(painterResource(R.drawable.stats), null) },
                        checked = showNerdStats,
                        onCheckedChange = onShowNerdStatsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.display_codec_on_player)) },
                        description = stringResource(R.string.description_display_codec_on_player),
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        checked = showCodecOnPlayer,
                        onCheckedChange = onShowCodecOnPlayerChange,
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.player_stream_client)) },
                        description = stringResource(R.string.player_stream_client_desc),
                        icon = { Icon(painterResource(R.drawable.integration), null) },
                        selectedValue = selectedPlayerStreamClient,
                        values = playerStreamClients,
                        onValueSelected = onPlayerStreamClientChange,
                        valueText = {
                            when (it) {
                                PlayerStreamClient.WEB_REMIX -> {
                                    stringResource(R.string.player_stream_client_web_remix)
                                }

                                PlayerStreamClient.ANDROID_VR -> {
                                    "Android VR"
                                }

                                else -> {
                                    stringResource(R.string.player_stream_client_web_remix)
                                }
                            }
                        },
                        valueDescription = {
                            when (it) {
                                PlayerStreamClient.ANDROID_VR -> {
                                    stringResource(R.string.player_stream_client_android_vr_desc)
                                }

                                PlayerStreamClient.WEB_REMIX -> {
                                    stringResource(R.string.player_stream_client_web_remix_desc)
                                }

                                else -> {
                                    stringResource(R.string.player_stream_client_web_remix_desc)
                                }
                            }
                        },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.debug_logs)) },
                        description = stringResource(R.string.view_debug_logs_description),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.manage_search),
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                            )
                        },
                        onClick = { navController.navigate("settings/logcat") },
                    )
                }
            }

            if (moe.rgsekai.sekaitune.BuildConfig.DEBUG) {
                PreferenceGroup(title = "Spotify Follow Test (Debug)") {
                    item {
                        SpotifyArtistFollowTestSection()
                    }
                }
            }

            AnimatedVisibility(
                visible = showNerdStats && playerConnection != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NerdStatsSection(playerConnection = playerConnection)
                }
            }

            Spacer(modifier = Modifier.height(SettingsDimensions.ScreenBottomPadding))
        }
    }
}

@Composable
private fun NerdStatsSection(playerConnection: moe.rgsekai.sekaitune.playback.PlayerConnection?) {
    if (playerConnection == null) return

    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val player = playerConnection.player

    var bufferPercentage by remember { mutableStateOf(0) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var currentPosition by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            bufferPercentage = player.bufferedPercentage
            bufferedPosition = player.bufferedPosition
            currentPosition = player.currentPosition
            playbackSpeed = player.playbackParameters.speed
            delay(500)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.graphic_eq),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.nerd_stats),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.real_time_playback_stats),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (mediaMetadata != null) {
                NerdStatCard(
                    icon = R.drawable.music_note,
                    label = stringResource(R.string.track_label),
                    value = mediaMetadata?.title ?: stringResource(R.string.no_track_playing),
                    accentColor = MaterialTheme.colorScheme.primary,
                )

                if (currentFormat != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NerdStatChip(
                            icon = R.drawable.graphic_eq,
                            label = stringResource(R.string.codec_label),
                            value =
                                currentFormat?.mimeType?.substringAfter("/")?.uppercase()
                                    ?: stringResource(R.string.unknown_codec),
                            modifier = Modifier.weight(1f),
                        )

                        val bitrateKbps = currentFormat?.bitrate?.let { it / 1000 } ?: 0
                        NerdStatChip(
                            icon = R.drawable.speed,
                            label = stringResource(R.string.bitrate_label),
                            value = if (bitrateKbps > 0) "$bitrateKbps kbps" else stringResource(R.string.unknown_bitrate),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val sampleRateKhz = currentFormat?.sampleRate?.let { (it / 1000.0).roundToInt() } ?: 0
                        NerdStatChip(
                            icon = R.drawable.waves,
                            label = stringResource(R.string.sample_rate_label),
                            value = if (sampleRateKhz > 0) "$sampleRateKhz kHz" else stringResource(R.string.unknown_sample_rate),
                            modifier = Modifier.weight(1f),
                        )

                        NerdStatChip(
                            icon = R.drawable.storage,
                            label = stringResource(R.string.content_length_label),
                            value =
                                currentFormat?.contentLength?.let {
                                    if (it > 0) {
                                        "${String.format("%.2f", it / 1024.0 / 1024.0)} MB"
                                    } else {
                                        stringResource(R.string.unknown_content_length)
                                    }
                                } ?: stringResource(R.string.unknown_content_length),
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LinearWavyProgressIndicator(
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = stringResource(R.string.loading_format),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val bufferDuration = ((bufferedPosition - currentPosition) / 1000.0).roundToInt()
                val bufferProgress by animateFloatAsState(
                    targetValue = bufferPercentage / 100f,
                    animationSpec = tween(300),
                    label = "buffer",
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.buffer_health_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "$bufferPercentage% ($bufferDuration sec ahead)",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    LinearWavyProgressIndicator(
                        progress = { bufferProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        color =
                            when {
                                bufferPercentage > 70 -> Color(0xFF43B581)
                                bufferPercentage > 30 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val playbackStateText =
                        when (player.playbackState) {
                            Player.STATE_IDLE -> stringResource(R.string.playback_state_idle)
                            Player.STATE_BUFFERING -> stringResource(R.string.playback_state_buffering)
                            Player.STATE_READY -> stringResource(R.string.playback_state_ready)
                            Player.STATE_ENDED -> stringResource(R.string.playback_state_ended)
                            else -> stringResource(R.string.playback_state_unknown)
                        }

                    val stateColor =
                        when (player.playbackState) {
                            Player.STATE_READY -> Color(0xFF43B581)
                            Player.STATE_BUFFERING -> MaterialTheme.colorScheme.tertiary
                            Player.STATE_IDLE -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.error
                        }

                    NerdStatChip(
                        icon = R.drawable.status,
                        label = stringResource(R.string.state_label),
                        value = playbackStateText,
                        modifier = Modifier.weight(1f),
                        valueColor = stateColor,
                    )

                    NerdStatChip(
                        icon = R.drawable.slow_motion_video,
                        label = stringResource(R.string.playback_speed_label),
                        value = "${playbackSpeed}x",
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.token),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.media_id_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = mediaMetadata?.id?.take(16)?.plus("...") ?: "N/A",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .alpha(0.5f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.no_track_playing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NerdStatCard(
    icon: Int,
    label: String,
    value: String,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.2f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accentColor,
                    )
                }
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NerdStatChip(
    icon: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SpotifyArtistFollowTestSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var artistIdInput by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready. Enter a Spotify Artist ID (e.g. 4Z8W4fKeB5YxbusRsdQVPb) or URI.") }
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Spotify Follow Test",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Debug tool to test follow, unfollow, and verify detection accuracy (Approach A vs Approach B).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            androidx.compose.material3.OutlinedTextField(
                value = artistIdInput,
                onValueChange = { artistIdInput = it },
                label = { Text("Spotify Artist ID or URI") },
                placeholder = { Text("e.g. 4Z8W4fKeB5YxbusRsdQVPb") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.Button(
                    onClick = {
                        val raw = artistIdInput.trim()
                        val uri = if (raw.startsWith("spotify:artist:")) raw else "spotify:artist:$raw"
                        val cleanId = uri.substringAfterLast(":")
                        if (cleanId.isBlank()) {
                            statusText = "Error: Please enter a valid Spotify Artist ID or URI."
                            return@Button
                        }
                        isLoading = true
                        statusText = "Running follow check for $cleanId..."
                        coroutineScope.launch {
                            timber.log.Timber.tag("SpotifyFollowTest").i("=== START CHECK FOLLOWED TEST for ID: %s (URI: %s) ===", cleanId, uri)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val repo = moe.rgsekai.sekaitune.spotify.SpotifyLibraryRepository(context.applicationContext)
                                repo.ensureAuthenticated()

                                val sb = StringBuilder()
                                sb.appendLine("Testing Artist ID: $cleanId ($uri)")

                                // ── APPROACH A: queryArtistOverview inspection ──
                                sb.appendLine("\n--- Approach A: queryArtistOverview ---")
                                timber.log.Timber.tag("SpotifyFollowTest").i("--- Approach A: queryArtistOverview ---")
                                val rawGqlResult = moe.rgsekai.sekaitune.spotify.Spotify.queryArtistOverviewRaw(cleanId)
                                var approachAResult = "UNKNOWN"
                                rawGqlResult.fold(
                                    onSuccess = { json ->
                                        val dataObj = json["data"]?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                                        val artistUnion = dataObj?.get("artistUnion")?.let { if (it is kotlinx.serialization.json.JsonObject) it else null }
                                        val keys = artistUnion?.keys?.joinToString(", ") ?: "null"
                                        val profileObj = artistUnion?.get("profile") as? kotlinx.serialization.json.JsonObject
                                        val profileKeys = profileObj?.keys?.joinToString(", ") ?: "null"
                                        timber.log.Timber.tag("SpotifyFollowTest").i("Approach A: artistUnion keys=[%s]", keys)
                                        timber.log.Timber.tag("SpotifyFollowTest").i("Approach A: profile keys=[%s]", profileKeys)

                                        val savedField = artistUnion?.get("saved")
                                            ?: artistUnion?.get("inLibrary")
                                            ?: artistUnion?.get("isFollowed")
                                            ?: profileObj?.get("isFollowed")
                                            ?: profileObj?.get("saved")
                                        val name = profileObj?.get("name")?.toString() ?: "Unknown"

                                        sb.appendLine("Artist Name: $name")
                                        sb.appendLine("artistUnion keys: $keys")
                                        sb.appendLine("profile keys: $profileKeys")
                                        sb.appendLine("Followed field in schema: ${savedField ?: "NOT FOUND in queryArtistOverview schema"}")
                                        approachAResult = if (savedField != null) "Field found: $savedField" else "Field NOT FOUND in queryArtistOverview (available keys: $keys)"
                                        timber.log.Timber.tag("SpotifyFollowTest").i("Approach A Result: %s", approachAResult)
                                    },
                                    onFailure = { err ->
                                        approachAResult = "FAILED: ${err.message}"
                                        timber.log.Timber.tag("SpotifyFollowTest").e(err, "Approach A queryArtistOverview failed: %s", err.message)
                                        sb.appendLine("Approach A Error: ${err.message}")
                                    }
                                )

                                // ── APPROACH B: libraryV3 pagination ──
                                sb.appendLine("\n--- Approach B: libraryV3 pagination ---")
                                timber.log.Timber.tag("SpotifyFollowTest").i("--- Approach B: libraryV3 pagination ---")
                                var offset = 0
                                val limit = 50
                                var isFollowedB = false
                                var totalFoundInLibrary = 0
                                var pageNumber = 0
                                var foundIndex = -1

                                try {
                                    while (true) {
                                        pageNumber++
                                        timber.log.Timber.tag("SpotifyFollowTest").i("Approach B: Fetching page %d (limit=%d, offset=%d)...", pageNumber, limit, offset)
                                        val pagingResult = moe.rgsekai.sekaitune.spotify.Spotify.myArtists(limit = limit, offset = offset)
                                        val paging = pagingResult.getOrThrow()
                                        totalFoundInLibrary = paging.total
                                        val items = paging.items
                                        timber.log.Timber.tag("SpotifyFollowTest").i("Approach B: Page %d returned %d artists (total=%d)", pageNumber, items.size, totalFoundInLibrary)

                                        val matchIdx = items.indexOfFirst { it.id == cleanId || it.uri == uri }
                                        if (matchIdx != -1) {
                                            isFollowedB = true
                                            foundIndex = offset + matchIdx
                                            timber.log.Timber.tag("SpotifyFollowTest").i("Approach B: MATCH FOUND! Artist '%s' (ID: %s) found at global index %d (page %d)", items[matchIdx].name, items[matchIdx].id, foundIndex, pageNumber)
                                            break
                                        }

                                        if (items.isEmpty() || items.size < limit || offset + items.size >= totalFoundInLibrary) {
                                            timber.log.Timber.tag("SpotifyFollowTest").i("Approach B: All pages scanned (%d pages, scanned %d of %d). Artist not in library.", pageNumber, offset + items.size, totalFoundInLibrary)
                                            break
                                        }
                                        offset += items.size
                                    }

                                    sb.appendLine("libraryV3 Total Followed: $totalFoundInLibrary")
                                    sb.appendLine("Scanned pages: $pageNumber (page size $limit)")
                                    if (isFollowedB) {
                                        sb.appendLine("Status: FOLLOWED (Found at index $foundIndex)")
                                    } else {
                                        sb.appendLine("Status: NOT FOLLOWED (Not found in library)")
                                    }
                                } catch (e: Exception) {
                                    sb.appendLine("Approach B Error: ${e.message}")
                                    timber.log.Timber.tag("SpotifyFollowTest").e(e, "Approach B libraryV3 failed: %s", e.message)
                                }

                                sb.appendLine("\n=== SUMMARY ===")
                                sb.appendLine("Approach A: $approachAResult")
                                sb.appendLine("Approach B: ${if (isFollowedB) "FOLLOWED (Reliable)" else "NOT FOLLOWED (Reliable)"}")

                                val fullReport = sb.toString()
                                timber.log.Timber.tag("SpotifyFollowTest").i(fullReport)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    statusText = fullReport
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                ) {
                    Text("Check", maxLines = 1)
                }

                androidx.compose.material3.FilledTonalButton(
                    onClick = {
                        val raw = artistIdInput.trim()
                        val uri = if (raw.startsWith("spotify:artist:")) raw else "spotify:artist:$raw"
                        val cleanId = uri.substringAfterLast(":")
                        if (cleanId.isBlank()) {
                            statusText = "Error: Please enter a valid Spotify Artist ID or URI."
                            return@FilledTonalButton
                        }
                        isLoading = true
                        statusText = "Calling addToLibrary for $uri..."
                        coroutineScope.launch {
                            timber.log.Timber.tag("SpotifyFollowTest").i("=== START FOLLOW TEST: %s ===", uri)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val repo = moe.rgsekai.sekaitune.spotify.SpotifyLibraryRepository(context.applicationContext)
                                repo.ensureAuthenticated()
                                val result = moe.rgsekai.sekaitune.spotify.Spotify.addToLibrary(listOf(uri))
                                result.fold(
                                    onSuccess = {
                                        val msg = "Follow SUCCESS for $uri via addToLibrary"
                                        timber.log.Timber.tag("SpotifyFollowTest").i(msg)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            statusText = "[SUCCESS] $msg"
                                            isLoading = false
                                        }
                                    },
                                    onFailure = { err ->
                                        val msg = "Follow FAILED for $uri: ${err.message}"
                                        timber.log.Timber.tag("SpotifyFollowTest").e(err, "%s", msg)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            statusText = "[ERROR] $msg\nCause: ${err.cause?.message}"
                                            isLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                ) {
                    Text("Follow", maxLines = 1)
                }

                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        val raw = artistIdInput.trim()
                        val uri = if (raw.startsWith("spotify:artist:")) raw else "spotify:artist:$raw"
                        val cleanId = uri.substringAfterLast(":")
                        if (cleanId.isBlank()) {
                            statusText = "Error: Please enter a valid Spotify Artist ID or URI."
                            return@OutlinedButton
                        }
                        isLoading = true
                        statusText = "Calling removeFromLibrary for $uri..."
                        coroutineScope.launch {
                            timber.log.Timber.tag("SpotifyFollowTest").i("=== START UNFOLLOW TEST: %s ===", uri)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val repo = moe.rgsekai.sekaitune.spotify.SpotifyLibraryRepository(context.applicationContext)
                                repo.ensureAuthenticated()
                                val result = moe.rgsekai.sekaitune.spotify.Spotify.removeFromLibrary(listOf(uri))
                                result.fold(
                                    onSuccess = {
                                        val msg = "Unfollow SUCCESS for $uri via removeFromLibrary"
                                        timber.log.Timber.tag("SpotifyFollowTest").i(msg)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            statusText = "[SUCCESS] $msg"
                                            isLoading = false
                                        }
                                    },
                                    onFailure = { err ->
                                        val msg = "Unfollow FAILED for $uri: ${err.message}"
                                        timber.log.Timber.tag("SpotifyFollowTest").e(err, "%s", msg)
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            statusText = "[ERROR] $msg\nCause: ${err.cause?.message}"
                                            isLoading = false
                                        }
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                ) {
                    Text("Unfollow", maxLines = 1)
                }
            }

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing...", style = MaterialTheme.typography.bodySmall)
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}








