package moe.rgsekai.sekaitune.ui.player.styles

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.ShowOneLineLyricsKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropBlurEnabledKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropBlurStrengthKey
import moe.rgsekai.sekaitune.constants.ThemeBackdropMode
import moe.rgsekai.sekaitune.constants.ThemeBackdropModeKey
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeInfo
import moe.rgsekai.sekaitune.playback.sources.PlaybackQualityBadgeState
import moe.rgsekai.sekaitune.ui.component.PlayerSliderTrack
import moe.rgsekai.sekaitune.ui.component.ResizableIconButton
import moe.rgsekai.sekaitune.ui.player.PlayerColorExtractor
import moe.rgsekai.sekaitune.ui.player.PlayerTimeLabel
import moe.rgsekai.sekaitune.ui.player.PlaybackQualityBadge
import moe.rgsekai.sekaitune.ui.theme.PlayerBackgroundColorUtils
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference

/**
 * Preview card representing Sekai Tune's Immersive Now Playing player.
 * Matches the real Immersive player layout & Theme Backdrop settings exactly:
 * - Live Theme Backdrop reflection: Glow Animated or Static Coloring, or Backdrop Blur!
 * - One-line lyric line (with live lyrics text from playback)
 * - Title + Artist row with Comment, More Options, and Like buttons
 * - Sleek pill track slider with live progress
 * - Centered quality badge (e.g. "Opus 144 >")
 * - Apple Music style transport controls with live Play/Pause state
 * - Bottom queue bar (Queue icon, Headset/Device pill, Sleep timer pill, Lyrics icon)
 */
@Composable
fun PlayerStylePreviewCard(
    style: PlayerStyle,
    data: PlayerPreviewData,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .inert(),
    ) {
        ImmersiveStylePreview(data = data)
    }
}

@Composable
private fun ImmersiveStylePreview(
    data: PlayerPreviewData,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)

    // Observe active Theme Backdrop configuration
    val (themeBackdropMode) = rememberEnumPreference(
        ThemeBackdropModeKey,
        defaultValue = ThemeBackdropMode.GLOW_ANIMATED,
    )
    val (themeBackdropBlurEnabled) = rememberPreference(
        ThemeBackdropBlurEnabledKey,
        defaultValue = false,
    )
    val (themeBackdropBlurStrength) = rememberPreference(
        ThemeBackdropBlurStrengthKey,
        defaultValue = 60,
    )

    // Extract palette colors dynamically from the album artwork
    var paletteColors by remember(data.artworkModel) {
        mutableStateOf<List<Color>>(emptyList())
    }

    LaunchedEffect(data.artworkModel) {
        val model = data.artworkModel
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .size(200, 200)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap: Bitmap? = result.image?.toBitmap()
                if (bitmap != null) {
                    val palette = Palette.from(bitmap)
                        .maximumColorCount(16)
                        .generate()
                    val extracted = PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = Color(0xFF1A1A22).toArgb(),
                    )
                    withContext(Dispatchers.Main) {
                        paletteColors = extracted
                    }
                }
            } catch (_: Exception) {
                // Ignore fallback to empty
            }
        }
    }

    val effectiveColors = remember(paletteColors) {
        if (paletteColors.isNotEmpty()) paletteColors else listOf(Color(0xFF242738), Color(0xFF14141A))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070709)),
    ) {
        // 1. Theme Backdrop Layer (Glow animated, Static Coloring, or Blurred Backdrop)
        if (!themeBackdropBlurEnabled) {
            if (themeBackdropMode == ThemeBackdropMode.GLOW_ANIMATED) {
                // Glow animated canvas backdrop
                val infiniteTransition = rememberInfiniteTransition(label = "PreviewBackdropGlow")
                val progress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(22000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "previewGlowProgress",
                )

                fun rotatedColorAt(index: Int): Color {
                    val size = effectiveColors.size
                    val idx = index.toFloat() + progress * size
                    val a = kotlin.math.floor(idx).toInt() % size
                    val b = (a + 1) % size
                    val frac = idx - kotlin.math.floor(idx)
                    return androidx.compose.ui.graphics.lerp(
                        effectiveColors.getOrElse(a) { Color.DarkGray },
                        effectiveColors.getOrElse(b) { Color.DarkGray },
                        frac,
                    )
                }

                fun oscillate(min: Float, max: Float, phase: Float): Float {
                    val v = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * (progress + phase)).toFloat()
                    return min + (max - min) * ((v + 1f) * 0.5f)
                }

                val c1 = rotatedColorAt(0)
                val c2 = rotatedColorAt(1)
                val c3 = rotatedColorAt(2)
                val o1x = oscillate(0.0f, 1.0f, 0.00f)
                val o1y = oscillate(0.2f, 0.7f, 0.07f)
                val o2x = oscillate(1.0f, 0.0f, 0.20f)
                val o2y = oscillate(0.4f, 1.0f, 0.25f)
                val o3x = oscillate(0.2f, 0.8f, 0.33f)
                val o3y = oscillate(0.7f, 0.3f, 0.36f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val w = size.width
                            val h = size.height
                            drawRect(Color(0xFF070707))
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(c1.copy(alpha = 0.85f), c1.copy(alpha = 0.45f), Color.Transparent),
                                    center = Offset(w * o1x, h * o1y),
                                    radius = w * 1.2f,
                                ),
                            )
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(c2.copy(alpha = 0.80f), c2.copy(alpha = 0.40f), Color.Transparent),
                                    center = Offset(w * o2x, h * o2y),
                                    radius = w * 1.1f,
                                ),
                            )
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(c3.copy(alpha = 0.75f), c3.copy(alpha = 0.35f), Color.Transparent),
                                    center = Offset(w * o3x, h * o3y),
                                    radius = w * 1.0f,
                                ),
                            )
                        },
                )
            } else {
                // Static Coloring Backdrop
                val baseColor = PlayerBackgroundColorUtils.ensureComfortableColor(
                    effectiveColors.first(),
                    minBrightness = 0.28f,
                    maxBrightness = 0.65f,
                )
                val gradientStops = PlayerBackgroundColorUtils.buildColoringStops(baseColor)
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(baseColor),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(colorStops = gradientStops)),
                    )
                }
            }
        }

        // 2. Full-bleed Artwork Layer (with blur if Backdrop blur is enabled, or soft fade into Theme Backdrop)
        if (themeBackdropBlurEnabled) {
            val blurDp = (themeBackdropBlurStrength * 0.5f).coerceIn(4f, 50f).dp
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(data.artworkModel)
                    .size(500)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurDp),
            )
            // Overlay gradient for contrast on blurred artwork
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.15f),
                                0.50f to Color.Black.copy(alpha = 0.35f),
                                0.75f to Color.Black.copy(alpha = 0.65f),
                                1.00f to Color.Black.copy(alpha = 0.80f),
                            ),
                        ),
                    ),
            )
        } else {
            // Banner artwork with soft bottom alpha gradient (exactly like Player.kt)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.68f)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Black,
                                    0.25f to Color.Black,
                                    0.40f to Color.Black.copy(alpha = 0.94f),
                                    0.55f to Color.Black.copy(alpha = 0.80f),
                                    0.70f to Color.Black.copy(alpha = 0.55f),
                                    0.82f to Color.Black.copy(alpha = 0.30f),
                                    0.92f to Color.Black.copy(alpha = 0.12f),
                                    0.97f to Color.Black.copy(alpha = 0.04f),
                                    1.00f to Color.Transparent,
                                ),
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(data.artworkModel)
                        .size(600)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 3. Main Player Content Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 12.dp),
        ) {
            Spacer(Modifier.weight(1f))

            // One-line Lyrics
            val (showOneLineLyrics) = rememberPreference(ShowOneLineLyricsKey, defaultValue = true)
            if (showOneLineLyrics && data.lyricLine.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        text = data.lyricLine,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        ),
                        color = foreground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Title, Artist, and Action Buttons (Comments, More, Like)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = data.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(foreground.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_comment),
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(foreground.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(foreground.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(if (data.isLiked) R.drawable.favorite else R.drawable.favorite_border),
                            contentDescription = null,
                            tint = foreground,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Progress Slider
            val safeDuration = if (data.durationMs > 0L) data.durationMs.toFloat() else 1f
            val safeProgress = data.progressMs.toFloat().coerceIn(0f, safeDuration)
            val sliderColors = SliderDefaults.colors(
                activeTrackColor = foreground.copy(alpha = 0.90f),
                inactiveTrackColor = foreground.copy(alpha = 0.28f),
                thumbColor = Color.Transparent,
            )

            Slider(
                value = safeProgress,
                valueRange = 0f..safeDuration,
                onValueChange = {},
                colors = sliderColors,
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = sliderColors,
                        trackHeight = 10.dp,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
            )

            Spacer(Modifier.height(2.dp))

            // Time labels + Quality format chip (centered)
            PlayerTimeLabel(
                sliderPosition = null,
                position = data.progressMs,
                duration = data.durationMs,
                textBackgroundColor = foreground,
                showRemainingTime = true,
                centerContent = {
                    val qualityBadgeInfo = PlaybackQualityBadgeInfo(
                        label = data.formatTag,
                        mediaId = null,
                        state = PlaybackQualityBadgeState.FINAL,
                    )
                    PlaybackQualityBadge(
                        badgeInfo = qualityBadgeInfo,
                        textColor = foreground,
                        onClick = null,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
            )

            Spacer(Modifier.height(14.dp))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ResizableIconButton(
                    icon = R.drawable.apple_skip_previous,
                    color = foreground,
                    modifier = Modifier.size(46.dp),
                    onClick = {},
                )

                ResizableIconButton(
                    icon = if (data.isPlaying) R.drawable.pause_applemusic else R.drawable.play_applemusic,
                    color = foreground,
                    modifier = Modifier.size(68.dp),
                    onClick = {},
                )

                ResizableIconButton(
                    icon = R.drawable.apple_skip_next,
                    color = foreground,
                    modifier = Modifier.size(46.dp),
                    onClick = {},
                )
            }

            Spacer(Modifier.height(14.dp))

            // Bottom Queue Bar
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.apple_queue),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = foreground.copy(alpha = 0.75f),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(foreground.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.speaker_apple),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = foreground,
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.sleep_timer),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = foreground,
                    )
                }

                IconButton(
                    onClick = {},
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.apple_music_me),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = foreground.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}
