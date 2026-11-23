/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.aod

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.utils.makeTimeString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AodLockscreenScreen(
    playerConnection: PlayerConnection?,
    onDismiss: () -> Unit,
    onSleep: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }
    val currentSong by playerConnection?.currentSong?.collectAsStateWithLifecycle(null) ?: remember { mutableStateOf(null) }
    val isLiked = currentSong?.song?.liked == true

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(mediaMetadata, isPlaying) {
        val player = playerConnection?.player ?: return@LaunchedEffect
        while (isActive) {
            val dur = player.duration
            duration = if (dur > 0L) dur else (mediaMetadata?.duration?.toLong()?.times(1000L) ?: 0L)
            if (!isUserSeeking) {
                currentPosition = player.currentPosition.coerceAtLeast(0L)
            }
            delay(250)
        }
    }

    var currentTimeText by remember { mutableStateOf("") }
    var currentDateText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val is24Hour = DateFormat.is24HourFormat(context)
        val timePattern = if (is24Hour) "HH:mm" else "h:mm"
        val timeFormat = SimpleDateFormat(timePattern, Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        while (isActive) {
            val now = Date()
            currentTimeText = timeFormat.format(now)
            currentDateText = dateFormat.format(now)
            delay(1000)
        }
    }

    // OLED Burn-in Protection: subtle pixel shift every 60 seconds
    var burnInShiftX by remember { mutableFloatStateOf(0f) }
    var burnInShiftY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val shifts = floatArrayOf(0f, 2f, -2f, 3f, -3f, 1f, -1f)
        var step = 0
        while (isActive) {
            delay(60_000L)
            step++
            burnInShiftX = shifts[step % shifts.size]
            burnInShiftY = shifts[(step / shifts.size) % shifts.size]
        }
    }

    // Swipe up to dismiss animation
    val dragOffsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 120.dp.toPx() }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .offset { IntOffset(0, dragOffsetY.value.roundToInt().coerceAtMost(0)) }
                .alpha((1f - (-dragOffsetY.value / (dismissThresholdPx * 2.5f))).coerceIn(0f, 1f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onSleep() },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragOffsetY.value < -dismissThresholdPx) {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(-1500f, tween(180))
                                    onDismiss()
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(0f, tween(180))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f, tween(150))
                            }
                        },
                        onVerticalDrag = { _, dragAmount ->
                            coroutineScope.launch {
                                dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtMost(0f))
                            }
                        },
                    )
                },
    ) {
        // Fullscreen Dim-Toned Album Artwork
        if (mediaMetadata?.thumbnailUrl != null) {
            val desaturateMatrix =
                remember {
                    ColorMatrix().apply {
                        setToSaturation(0.35f)
                    }
                }
            AsyncImage(
                model = mediaMetadata?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(desaturateMatrix),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            // Dark vignette overlay: heavily dimmed for AOD power efficiency & aesthetics
                            drawRect(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Black.copy(alpha = 0.90f),
                                            Color.Black.copy(alpha = 0.75f),
                                            Color.Black.copy(alpha = 0.92f),
                                            Color.Black.copy(alpha = 0.98f),
                                        ),
                                ),
                            )
                        },
            )
        }

        // Content Column with Burn-in Shift
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 20.dp)
                    .offset(burnInShiftX.dp, burnInShiftY.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top: Minimal Clock & Date
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(
                    text = currentTimeText,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.95f),
                    letterSpacing = (-1).sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentDateText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }

            // Center/Bottom: Song Details, Progress Bar & Media Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Song Title & Artist
                Text(
                    text = mediaMetadata?.title ?: stringResource(R.string.no_track_playing),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = mediaMetadata?.artists?.joinToString { it.name }.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Bar
                val safeDuration = if (duration > 0L) duration else 1L
                val progressFraction =
                    if (isUserSeeking) {
                        seekProgress
                    } else {
                        (currentPosition.toFloat() / safeDuration).coerceIn(0f, 1f)
                    }

                Slider(
                    value = progressFraction,
                    onValueChange = {
                        isUserSeeking = true
                        seekProgress = it
                    },
                    onValueChangeFinished = {
                        val seekTargetMs = (seekProgress * safeDuration).toLong()
                        playerConnection?.player?.seekTo(seekTargetMs)
                        currentPosition = seekTargetMs
                        isUserSeeking = false
                    },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.9f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val displayedPosition =
                        if (isUserSeeking) {
                            (seekProgress * safeDuration).toLong()
                        } else {
                            currentPosition
                        }
                    Text(
                        text = makeTimeString(displayedPosition),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                    Text(
                        text = makeTimeString(duration),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Playback Controls: Like, Previous, Play/Pause, Next
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Like button
                    IconButton(
                        onClick = { playerConnection?.toggleLike() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                                ),
                            contentDescription = null,
                            tint = if (isLiked) Color(0xFFFF4081) else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    // Skip Previous
                    IconButton(
                        onClick = { playerConnection?.seekToPrevious() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(32.dp),
                        )
                    }

                    // Play / Pause Circle
                    Surface(
                        onClick = { playerConnection?.player?.togglePlayPause() },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (isPlaying) R.drawable.pause else R.drawable.play,
                                    ),
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }

                    // Skip Next
                    IconButton(
                        onClick = { playerConnection?.seekToNext() },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            // Bottom: Swipe up to unlock hint
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.35f)),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.swipe_up_to_unlock),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}
