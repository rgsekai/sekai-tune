package moe.rgsekai.sekaitune.ui.player.flagship

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size as CoilSize
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.player.BackdropBlurTransformation
import moe.rgsekai.sekaitune.ui.player.StreamFormatBadge
import moe.rgsekai.sekaitune.ui.player.styles.PlayerPreviewData
import moe.rgsekai.sekaitune.utils.makeTimeString
import kotlin.math.roundToInt

// Mochy design constants
private const val ReflectionPx = 360
private const val ReflectionBlurPx = 8
private const val FrostedEdgeFadeFraction = 0.13f
private val PlayerHorizontalPadding = 32.dp
private val ControlsDrawShift = 64.dp
private val SeekBarLift = 6.dp
private val PlayerDeckHeight = 54.dp + 9.dp // 63.dp
private val TransportGapAboveRow = 24.dp + SeekBarLift

// Mochy ControlsShade: clear at top, 24% black by 46% down, then held
private val ControlsShadeBrush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.46f to Color.Black.copy(alpha = 0.24f),
    1f to Color.Black.copy(alpha = 0.24f),
)

/**
 * Mochy-exact Flagship Player UI:
 * - Full screen blurred backdrop melting from album art colors.
 * - Foreground sharp cover art (occupying upper 54% of height) with Mochy's 13% bottom fade mask.
 * - Mochy ControlsShade (subtle 24% black gradient instead of thick dark cutoff).
 * - Exact transport row using Mochy shapes, sizing, spacing, and squash-and-bounce spring animations.
 * - Track Info Row with title, artist, format badge, and press-scaled Like and Chevron buttons.
 * - Clean stacked bottom cards with zero text overlap.
 */
@Composable
fun FlagshipPlayerContent(
    data: PlayerPreviewData,
    onMenuClick: () -> Unit,
    onLikeClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onSliderValueChange: ((Long) -> Unit)? = null,
    onSliderValueChangeFinished: (() -> Unit)? = null,
    nextSongTitle: String? = null,
    nextSongArtist: String? = null,
    nextArtworkUrl: String? = null,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)
    val context = LocalContext.current

    val artworkUrl = (data.artworkModel as? String) ?: data.artworkModel?.toString()

    // 1. Mochy BackdropBlurTransformation request for the full-screen blurred ambient copy
    val blurredRequest = remember(context, artworkUrl) {
        if (artworkUrl.isNullOrBlank()) null
        else {
            ImageRequest.Builder(context)
                .data(artworkUrl)
                .allowHardware(false)
                .transformations(BackdropBlurTransformation(radiusPx = ReflectionBlurPx))
                .crossfade(false)
                .size(CoilSize(ReflectionPx, ReflectionPx))
                .build()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val screenHeight = maxHeight
        val coverHeight = screenHeight * 0.538f

        // Layer 1: Full-screen blurred ambient artwork reflection
        if (blurredRequest != null) {
            AsyncImage(
                model = blurredRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.95f },
            )
        } else {
            // Fallback subtle gradient if no artwork yet
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF22262C), Color(0xFF14171A)),
                        ),
                    ),
            )
        }

        // Layer 2: Foreground sharp cover picture fading softly into the blurred reflection at bottom 13%
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .align(Alignment.TopCenter)
                .mochyCoverFade(bottomShare = FrostedEdgeFadeFraction),
        ) {
            AsyncImage(
                model = data.artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 3: Mochy ControlsShade (subtle 24% black gradient for contrast without turning the background black)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight * (1f - 0.50f))
                .align(Alignment.BottomCenter)
                .drawWithCache {
                    onDrawBehind {
                        drawRect(ControlsShadeBrush)
                    }
                },
        )

        // Bottom Controls Column
        val belowControls = PlayerDeckHeight
        val transportGap = (screenHeight * 0.19f - 37.dp - belowControls + TransportGapAboveRow + ControlsDrawShift) / 2f
        val transportSpacer = (transportGap - SeekBarLift - ControlsDrawShift).coerceAtLeast(0.dp)
        val gapAboveButtons = transportSpacer + SeekBarLift + ControlsDrawShift
        val gapBelowButtons = transportGap.coerceAtLeast(0.dp)
        val buttonsLift = (gapAboveButtons - gapBelowButtons) / 2f

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = belowControls + transportGap.coerceAtLeast(0.dp)),
        ) {
            Column(modifier = Modifier.offset(y = -ControlsDrawShift)) {
                Spacer(modifier = Modifier.height(20.dp))

                // Track Info Row: Title, Artist, Outlined StreamFormatBadge, Like, Upward Chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = data.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )

                            Spacer(Modifier.width(8.dp))

                            // Mochy-exact Outlined Stream Format Badge
                            StreamFormatBadge(
                                text = data.formatTag.ifBlank { "OPUS" },
                            )
                        }

                        if (data.artist.isNotBlank()) {
                            Text(
                                text = data.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.70f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Action buttons: Like and Chevron Dropdown spaced by 7.dp
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PressScaleButton(
                            onClick = onLikeClick,
                            containerColor = if (data.isLiked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (data.isLiked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                                ),
                                contentDescription = "Like",
                                tint = if (data.isLiked) Color.White else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        PressScaleButton(
                            onClick = onMenuClick,
                            containerColor = Color.White.copy(alpha = 0.15f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.expand_less),
                                contentDescription = "More options",
                                tint = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Seek Bar Column: lifted by -SeekBarLift (-6.dp)
                Column(
                    modifier = Modifier.offset(y = -SeekBarLift),
                ) {
                    var draggingPosition by remember { mutableStateOf<Long?>(null) }
                    val safeDuration = if (data.durationMs > 0L) data.durationMs.toFloat() else 1f
                    val currentPos = draggingPosition ?: data.progressMs
                    val safeProgress = currentPos.toFloat().coerceIn(0f, safeDuration)
                    val progressFraction = (safeProgress / safeDuration).coerceIn(0f, 1f)

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val expand by animateFloatAsState(
                        targetValue = if (isPressed || draggingPosition != null) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                        label = "flagshipSeekExpand",
                    )

                    // Seek Slider with Mochy DefaultTrack drawing (7.dp rest, 14.dp held, round caps, 0dp thumb)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Slider(
                            value = safeProgress,
                            valueRange = 0f..safeDuration,
                            onValueChange = { newPos ->
                                draggingPosition = newPos.toLong()
                                onSliderValueChange?.invoke(newPos.toLong())
                            },
                            onValueChangeFinished = {
                                draggingPosition = null
                                onSliderValueChangeFinished?.invoke()
                            },
                            interactionSource = interactionSource,
                            thumb = { Spacer(Modifier.size(0.dp)) },
                            track = {
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .padding(horizontal = PlayerHorizontalPadding),
                                ) {
                                    val h = (7f + 7f * expand).dp.toPx()
                                    val y = size.height / 2f

                                    // Inactive Track
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.24f),
                                        start = Offset(h / 2f, y),
                                        end = Offset(size.width - h / 2f, y),
                                        strokeWidth = h,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    )

                                    // Active Track
                                    val endX = (h / 2f + (size.width - h) * progressFraction).coerceAtLeast(h / 2f)
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(h / 2f, y),
                                        end = Offset(endX, y),
                                        strokeWidth = h,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                        )
                    }

                    // Elapsed & Total Time Labels
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PlayerHorizontalPadding + 4.dp),
                    ) {
                        Text(
                            text = makeTimeString(currentPos),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.70f + 0.30f * expand),
                        )
                        Text(
                            text = makeTimeString(data.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.70f + 0.30f * expand),
                        )
                    }
                }
            }

            Spacer(Modifier.height(transportSpacer))

            // Transport Row: Mochy Flagship Vector Shapes & Layout
            MochyTransportRow(
                isPlaying = data.isPlaying,
                onPreviousClick = onPreviousClick,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
                modifier = Modifier.offset(y = -buttonsLift),
            )
        }

        // Stacked Bottom Cards (Lyrics & Queue) anchored directly to the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            FlagshipBottomCardStack(
                onOpenLyrics = onOpenLyrics,
                onOpenQueue = onOpenQueue,
                currentLyric = data.lyricLine,
                nextSongTitle = nextSongTitle,
                nextSongArtist = nextSongArtist,
                nextArtworkUrl = nextArtworkUrl,
            )
        }
    }
}

/**
 * Mochy's 3-button transport row with exact shapes, squash-and-bounce, and scale spring physics.
 */
@Composable
private fun MochyTransportRow(
    isPlaying: Boolean,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shapes = rememberTransportShapes(small = false)
    val playGlyph = remember { PlayGlyphState(isPlaying) }

    LaunchedEffect(isPlaying) {
        playGlyph.swapTo(isPlaying)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Design unit from Mochy: screen width divided by 1172
        val unit = (maxWidth / 1172f).coerceIn(0.27.dp, 0.42.dp)
        val unitPx = unit.value * LocalContext.current.resources.displayMetrics.density

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous Button
            MochySkipButton(
                shapes = shapes,
                unitPx = unitPx,
                backwards = true,
                onClick = onPreviousClick,
            )

            // Play/Pause Button
            MochyPlayButton(
                shapes = shapes,
                glyph = playGlyph,
                unitPx = unitPx,
                onClick = onPlayPauseClick,
            )

            // Next Button
            MochySkipButton(
                shapes = shapes,
                unitPx = unitPx,
                backwards = false,
                onClick = onNextClick,
            )
        }
    }
}

@Composable
private fun MochyPlayButton(
    shapes: TransportShapes,
    glyph: PlayGlyphState,
    unitPx: Float,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "mochyPlayPress",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawPlayGlyph(shapes, glyph, unitPx, Color.White)
                },
        )
    }
}

@Composable
private fun MochySkipButton(
    shapes: TransportShapes,
    unitPx: Float,
    backwards: Boolean,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cycle = remember { SkipCycleState() }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "mochySkipPress",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    scope.launch { cycle.step() }
                    onClick()
                },
            ),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawSkipGlyph(shapes, cycle.phase(), unitPx, Color.White, backwards)
                },
        )
    }
}

/**
 * Mochy's cover fade modifier: uses PorterDuff DST_OUT to cleanly erase the bottom [bottomShare]
 * of the sharp image so it seamlessly transitions into the blurred copy behind it.
 */
private fun Modifier.mochyCoverFade(bottomShare: Float = FrostedEdgeFadeFraction): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val w = size.width
            val h = size.height
            // Turned so the gradient runs up from the bottom edge
            CoverFadeMask.erase(native, 1f) {
                translate(0f, h)
                rotate(-90f)
                scale(h * bottomShare, w)
            }
        }
    }

private object CoverFadeMask {
    val paint = Paint().apply {
        val steps = 8
        val colors = IntArray(steps + 1) { i ->
            val t = i / steps.toFloat()
            val eased = t * t * (3f - 2f * t)
            AndroidColor.argb(((1f - eased) * 255f).roundToInt(), 0, 0, 0)
        }
        val positions = FloatArray(steps + 1) { it / steps.toFloat() }
        shader = LinearGradient(
            0f, 0f, 1f, 0f, colors, positions, Shader.TileMode.CLAMP,
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    inline fun erase(canvas: Canvas, strength: Float, place: Canvas.() -> Unit) {
        val alpha = (strength * 255f).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        paint.alpha = alpha
        val saved = canvas.save()
        canvas.place()
        canvas.drawRect(0f, 0f, 1f, 1f, paint)
        canvas.restoreToCount(saved)
    }
}

@Composable
private fun PressScaleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    containerColor: Color = Color.White.copy(alpha = 0.15f),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.54f, stiffness = 720f),
        label = "pressScaleButton",
    )

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
