package moe.rgsekai.sekaitune.ui.player.flagship

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.size.Size as CoilSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.LyricsMode
import moe.rgsekai.sekaitune.constants.LyricsModeKey
import moe.rgsekai.sekaitune.constants.SyncLikesToSpotifyKey
import moe.rgsekai.sekaitune.extensions.metadata
import moe.rgsekai.sekaitune.extensions.toggleRepeatMode
import moe.rgsekai.sekaitune.ui.component.MediaMetadataListItem
import moe.rgsekai.sekaitune.ui.player.BackdropBlurTransformation
import moe.rgsekai.sekaitune.ui.player.LyricsContent
import moe.rgsekai.sekaitune.ui.player.StreamFormatBadge
import moe.rgsekai.sekaitune.ui.player.styles.PlayerPreviewData
import moe.rgsekai.sekaitune.utils.makeTimeString
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.roundToInt

// Flagship design constants
private const val ReflectionPx = 360
private const val ReflectionBlurPx = 8
private const val FrostedEdgeFadeFraction = 0.13f
private val PlayerHorizontalPadding = 32.dp
private val ControlsDrawShift = 64.dp
private val SeekBarLift = 6.dp
private val PlayerDeckHeight = 54.dp + 9.dp // 63.dp
private val TransportGapAboveRow = 24.dp + SeekBarLift

// Flagship ControlsShade: clear at top, 24% black by 46% down, then held
private val ControlsShadeBrush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.46f to Color.Black.copy(alpha = 0.24f),
    1f to Color.Black.copy(alpha = 0.24f),
)

/**
 * Flagship Player UI:
 * - Full screen blurred backdrop melting from album art colors.
 * - Foreground sharp cover art (occupying upper 54% of height) with 13% bottom fade mask.
 * - ControlsShade (subtle 24% black gradient instead of thick dark cutoff).
 * - Exact transport row with vector shapes, sizing, spacing, and squash-and-bounce spring animations.
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
    lyricsSyncOffset: Int = 0,
    playerConnection: moe.rgsekai.sekaitune.playback.PlayerConnection? = null,
    navController: androidx.navigation.NavController? = null,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)
    val context = LocalContext.current
    val syncLikesToSpotify by rememberPreference(SyncLikesToSpotifyKey, false)

    val artworkUrl = (data.artworkModel as? String) ?: data.artworkModel?.toString()

    val transition = rememberFlagshipPageTransition()
    val deckState = rememberFlagshipDeckState()

    BackHandler(enabled = transition.page != FlagshipPage.NONE) {
        transition.back()
    }

    val headerProgress = transition.headerProgress
    val activePage = transition.page

    val controlsTop = remember { floatArrayOf(Float.NaN) }
    val seekBarTop = remember { floatArrayOf(Float.NaN) }
    val cardTop = remember { floatArrayOf(Float.NaN) }
    var lyricsBottomInset by remember { mutableStateOf(210.dp) }
    var draggingPosition by remember { mutableStateOf<Long?>(null) }

    var lyricsImmersive by remember { mutableStateOf(false) }
    var queueImmersive by remember { mutableStateOf(false) }
    var userInteractedWithLyrics by remember { mutableStateOf(0L) }

    // Reset immersion when activePage changes
    LaunchedEffect(activePage) {
        lyricsImmersive = false
        queueImmersive = false
    }

    val controlsHidden by animateFloatAsState(
        targetValue = if (
            (lyricsImmersive && activePage == FlagshipPage.LYRICS) ||
            (queueImmersive && activePage == FlagshipPage.QUEUE)
        ) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "controlsHidden",
    )

    // Lyrics 3.5s auto-hide timer
    LaunchedEffect(activePage, lyricsImmersive, userInteractedWithLyrics) {
        if (activePage == FlagshipPage.LYRICS && !lyricsImmersive) {
            delay(3500L)
            lyricsImmersive = true
        }
    }

    val deckMounted by remember {
        derivedStateOf { controlsHidden < 0.99f || transition.page == FlagshipPage.NONE }
    }

    // 1. BackdropBlurTransformation request for the full-screen blurred ambient copy
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
        val screenWidth = maxWidth
        val density = LocalDensity.current
        val screenHeightPx = with(density) { screenHeight.toPx() }

        val lyricsScrollWatcher = remember {
            object : NestedScrollConnection {
                private var travel = 0f

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val dy = consumed.y
                    if (dy == 0f) return Offset.Zero
                    travel = if ((travel < 0f) == (dy < 0f)) travel + dy else dy
                    // dy < 0 means finger dragged up (scrolling down into lyrics) -> hide controls
                    if (travel < -6f) lyricsImmersive = true
                    // dy > 0 means finger dragged down (scrolling up toward top) -> show controls
                    if (travel > 6f) lyricsImmersive = false
                    return Offset.Zero
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        userInteractedWithLyrics = System.currentTimeMillis()
                        if (lyricsImmersive && available.y > 6f) {
                            lyricsImmersive = false
                        }
                    }
                    return Offset.Zero
                }
            }
        }

        val queueScrollWatcher = remember {
            object : NestedScrollConnection {
                private var travel = 0f

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val dy = consumed.y
                    if (dy == 0f) return Offset.Zero
                    travel = if ((travel < 0f) == (dy < 0f)) travel + dy else dy
                    // 24px threshold in queue
                    if (travel < -24f) queueImmersive = true
                    if (travel > 24f) queueImmersive = false
                    return Offset.Zero
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput) {
                        if (queueImmersive && available.y > 6f) {
                            queueImmersive = false
                        }
                    }
                    return Offset.Zero
                }
            }
        }

        val swipeModifier = Modifier.flagshipSwipeHandler(
            transition = transition,
            focusedPageProvider = { deckState.focusedPage },
            controlsTopProvider = { controlsTop[0] },
            seekBarTopProvider = { seekBarTop[0] },
            screenHeightPx = screenHeightPx,
            cardTopProvider = { if (cardTop[0].isNaN()) null else cardTop[0] },
            controlsHiddenProvider = { controlsHidden },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(swipeModifier),
        ) {
            val coverHeight = screenHeight * 0.538f
        val statusBarTop = WindowInsets.statusBars.union(WindowInsets.displayCutout)
            .asPaddingValues().calculateTopPadding().coerceAtLeast(24.dp)

        // Exact morph targets:
        // Mini thumbnail in header: 60.dp size, 36.dp from left, statusBarTop + 28.dp from top, 12.dp corner radius
        val miniArtSize = 60.dp
        val miniArtX = 36.dp
        val miniArtY = statusBarTop + 28.dp
        val targetTitleX = 114.dp
        val headerTitleWidth = (screenWidth - targetTitleX - PlayerHorizontalPadding).coerceAtLeast(0.dp)

        val currentCoverWidth = lerp(screenWidth, miniArtSize, headerProgress)
        val currentCoverHeight = lerp(coverHeight, miniArtSize, headerProgress)
        val currentCoverX = lerp(0.dp, miniArtX, headerProgress)
        val currentCoverY = lerp(0.dp, miniArtY, headerProgress)
        val currentCornerRadius = lerp(0.dp, 12.dp, headerProgress)

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

        // Layer 2: Foreground sharp cover picture fading softly at bottom in resting mode,
        // morphing into the mini thumbnail header when lyrics/queue is active
        Box(
            modifier = Modifier
                .offset(x = currentCoverX, y = currentCoverY)
                .size(width = currentCoverWidth, height = currentCoverHeight)
                .clip(RoundedCornerShape(currentCornerRadius))
                .then(
                    if (headerProgress < 0.95f) {
                        Modifier.flagshipCoverFade(bottomShare = FrostedEdgeFadeFraction * (1f - headerProgress))
                    } else {
                        Modifier
                    }
                )
                .clickable(enabled = activePage != FlagshipPage.NONE) {
                    if (controlsHidden >= 0.5f) {
                        lyricsImmersive = false
                        queueImmersive = false
                    } else {
                        transition.close()
                    }
                },
        ) {
            AsyncImage(
                model = data.artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Morphing Song Info in header when a page is open (X = 114.dp, Y = miniArtY)
        if (headerProgress > 0.05f) {
            Column(
                modifier = Modifier
                    .offset(x = targetTitleX, y = miniArtY + 6.dp)
                    .width(headerTitleWidth)
                    .graphicsLayer { alpha = headerProgress },
            ) {
                Text(
                    text = data.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (data.artist.isNotBlank()) {
                    Text(
                        text = data.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Layer 3: Middle Content (Lyrics or Queue list) when a page is active
        if (activePage != FlagshipPage.NONE && headerProgress > 0.05f) {
            val middleTop = miniArtY + miniArtSize + 16.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = middleTop)
                    .graphicsLayer { alpha = headerProgress }
                    .layout { measurable, constraints ->
                        val inset = (lyricsBottomInset * (1f - controlsHidden)).roundToPx().coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = (constraints.minHeight - inset).coerceAtLeast(0),
                                maxHeight = (constraints.maxHeight - inset).coerceAtLeast(0),
                            )
                        )
                        layout(placeable.width, placeable.height + inset) {
                            placeable.place(0, 0)
                        }
                    }
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val topFade = (18.dp.toPx() / size.height).coerceIn(0f, 0.5f)
                        val bottomFade = 1f - (80.dp.toPx() / size.height).coerceIn(0f, 0.5f)
                        val fade = Brush.verticalGradient(
                            0f to Color.Transparent,
                            topFade to Color.Black,
                            bottomFade to Color.Black,
                            1f to Color.Transparent,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = fade, blendMode = BlendMode.DstIn)
                        }
                    },
            ) {
                if (activePage == FlagshipPage.LYRICS) {
                    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)

                    LyricsContent(
                        lyricsMode = lyricsMode,
                        sliderPositionProvider = { draggingPosition },
                        lyricsSyncOffset = lyricsSyncOffset,
                        textColor = Color.White,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = PlayerHorizontalPadding)
                            .nestedScroll(lyricsScrollWatcher),
                    )
                } else if (activePage == FlagshipPage.QUEUE && playerConnection != null) {
                    val queueWindows by playerConnection.queueWindows.collectAsState()
                    val currentWindowIndex = playerConnection.player.currentMediaItemIndex
                    val queueListState = rememberLazyListState()

                    LazyColumn(
                        state = queueListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .nestedScroll(queueScrollWatcher),
                    ) {
                        val currentWindow = queueWindows.getOrNull(currentWindowIndex)
                        if (currentWindow != null) {
                            item(key = "header_now_playing") {
                                QueueSectionHeader(
                                    title = "Now Playing",
                                )
                            }
                            item(key = "now_playing_${currentWindow.uid}") {
                                val meta = currentWindow.mediaItem.metadata
                                if (meta != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.16f))
                                            .padding(vertical = 4.dp),
                                    ) {
                                        MediaMetadataListItem(
                                            mediaMetadata = meta,
                                            isPlaying = data.isPlaying,
                                            trailingContent = {
                                                Icon(
                                                    painter = painterResource(R.drawable.drag_handle),
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        val upcoming = queueWindows.filterIndexed { index, _ -> index > currentWindowIndex }
                        if (upcoming.isNotEmpty()) {
                            item(key = "header_continue_playing") {
                                QueueSectionHeader(
                                    title = "Continue Playing",
                                    subtitle = "Queue from ${data.title}",
                                )
                            }
                            itemsIndexed(
                                items = upcoming,
                                key = { idx, item -> item.uid.hashCode() + idx },
                            ) { relIndex, window ->
                                val actualIndex = currentWindowIndex + 1 + relIndex
                                val meta = window.mediaItem.metadata
                                if (meta != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                playerConnection.player.seekToDefaultPosition(actualIndex)
                                            }
                                            .padding(vertical = 4.dp),
                                    ) {
                                        MediaMetadataListItem(
                                            mediaMetadata = meta,
                                            isPlaying = false,
                                            trailingContent = {
                                                Icon(
                                                    painter = painterResource(R.drawable.drag_handle),
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Layer 4: Flagship ControlsShade (subtle 24% black gradient for contrast)
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight * (1f - 0.50f))
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = (1f - controlsHidden).coerceIn(0f, 1f) }
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
                .padding(bottom = belowControls + transportGap.coerceAtLeast(0.dp))
                .graphicsLayer {
                    alpha = (1f - controlsHidden).coerceIn(0f, 1f)
                    translationY = controlsHidden * size.height * 0.55f
                },
        ) {
            Column(
                modifier = Modifier
                    .offset(y = -ControlsDrawShift)
                    .onGloballyPositioned { coords ->
                        if (controlsTop[0].isNaN() || activePage == FlagshipPage.NONE) {
                            controlsTop[0] = coords.positionInRoot().y
                        }
                    },
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Track Info Row: Title, Artist, Outlined StreamFormatBadge, Like, Upward Chevron
                // Fades out and shrinks when activePage != NONE because it morphed to header
                if (headerProgress < 0.95f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PlayerHorizontalPadding)
                            .graphicsLayer { alpha = 1f - headerProgress },
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

                                // Flagship Outlined Stream Format Badge
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

                        val bottomLikeIcon =
                            if (syncLikesToSpotify) {
                                if (data.isLiked) R.drawable.check else R.drawable.add
                            } else {
                                if (data.isLiked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill
                            }
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
                                    painter = painterResource(bottomLikeIcon),
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
                }

                Spacer(Modifier.height(16.dp))

                // Side Buttons Column right above seek bar
                if (activePage != FlagshipPage.NONE && headerProgress > 0.05f) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PlayerHorizontalPadding)
                            .bottomAnchoredSlot(
                                height = { (75.dp + (if (activePage == FlagshipPage.QUEUE) 41.dp else 0.dp)) * headerProgress },
                                bottomGap = { 12.dp * headerProgress },
                            ),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        // Chevron Dropdown to collapse back
                        PressScaleButton(
                            onClick = { transition.back() },
                            containerColor = Color.White.copy(alpha = 0.15f),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.expand_less),
                                contentDescription = "Collapse",
                                tint = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = 180f },
                            )
                        }

                        // If Lyrics is open: Like button
                        if (activePage == FlagshipPage.LYRICS) {
                            val lyricsLikeIcon =
                                if (syncLikesToSpotify) {
                                    if (data.isLiked) R.drawable.check else R.drawable.add
                                } else {
                                    if (data.isLiked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill
                                }
                            PressScaleButton(
                                onClick = onLikeClick,
                                containerColor = if (data.isLiked) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.15f),
                            ) {
                                Icon(
                                    painter = painterResource(lyricsLikeIcon),
                                    contentDescription = "Like",
                                    tint = if (data.isLiked) Color.White else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (activePage == FlagshipPage.QUEUE && playerConnection != null) {
                            val shuffleModeEnabled = playerConnection.player.shuffleModeEnabled
                            val repeatMode = playerConnection.player.repeatMode

                            PressScaleButton(
                                onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                                containerColor = if (shuffleModeEnabled) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = "Shuffle",
                                    tint = if (shuffleModeEnabled) Color.White else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            PressScaleButton(
                                onClick = { playerConnection.player.toggleRepeatMode() },
                                containerColor = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.15f),
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat
                                    ),
                                    contentDescription = "Repeat",
                                    tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                // Seek Bar Column: lifted by -SeekBarLift (-6.dp)
                Column(
                    modifier = Modifier
                        .offset(y = -SeekBarLift)
                        .onGloballyPositioned { coords ->
                            if (controlsHidden <= 0.001f) {
                                val rootCoords = coords.findRootCoordinates()
                                val rootHeight = rootCoords.size.height
                                val topY = coords.positionInRoot().y
                                seekBarTop[0] = topY
                                val insetPx = (rootHeight - topY).coerceAtLeast(0f)
                                val inset = with(density) { insetPx.toDp() }
                                if (abs(inset.value - lyricsBottomInset.value) > 0.5f) {
                                    lyricsBottomInset = inset
                                }
                            }
                        },
                ) {
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

                    // Seek Slider with DefaultTrack drawing (7.dp rest, 14.dp held, round caps, 0dp thumb)
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

            // Transport Row: Flagship Vector Shapes & Layout
            FlagshipTransportRow(
                isPlaying = data.isPlaying,
                onPreviousClick = onPreviousClick,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
                modifier = Modifier.offset(y = -buttonsLift),
            )
        }

        // Stacked Bottom Cards (Lyrics & Queue) anchored directly to the bottom with 26.dp horizontal margin
        if (deckMounted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .graphicsLayer {
                        alpha = (1f - controlsHidden).coerceIn(0f, 1f)
                        translationY = controlsHidden * size.height * 0.75f
                    }
                    .onGloballyPositioned { coords ->
                        cardTop[0] = coords.positionInRoot().y
                    },
            ) {
                FlagshipBottomCardStack(
                    onOpenLyrics = { transition.open(FlagshipPage.LYRICS) },
                    onOpenQueue = { transition.open(FlagshipPage.QUEUE) },
                    currentLyric = data.lyricLine,
                    nextSongTitle = nextSongTitle,
                    nextSongArtist = nextSongArtist,
                    nextArtworkUrl = nextArtworkUrl,
                    activePage = activePage,
                )
            }
        }
        }
    }
}

/**
 * 3-button transport row with exact shapes, squash-and-bounce, and scale spring physics.
 */
@Composable
private fun FlagshipTransportRow(
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
        // Design unit: screen width divided by 1172
        val unit = (maxWidth / 1172f).coerceIn(0.27.dp, 0.42.dp)
        val unitPx = unit.value * LocalContext.current.resources.displayMetrics.density

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous Button
            FlagshipSkipButton(
                shapes = shapes,
                unitPx = unitPx,
                backwards = true,
                onClick = onPreviousClick,
            )

            // Play/Pause Button
            FlagshipPlayButton(
                shapes = shapes,
                glyph = playGlyph,
                unitPx = unitPx,
                onClick = onPlayPauseClick,
            )

            // Next Button
            FlagshipSkipButton(
                shapes = shapes,
                unitPx = unitPx,
                backwards = false,
                onClick = onNextClick,
            )
        }
    }
}

@Composable
private fun FlagshipPlayButton(
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
        label = "flagshipPlayPress",
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
private fun FlagshipSkipButton(
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
        label = "flagshipSkipPress",
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
 * Cover fade modifier: uses PorterDuff DST_OUT to cleanly erase the bottom [bottomShare]
 * of the sharp image so it seamlessly transitions into the blurred copy behind it.
 */
private fun Modifier.flagshipCoverFade(bottomShare: Float = FrostedEdgeFadeFraction): Modifier = this
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

@Composable
private fun QueueSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Modifier.bottomAnchoredSlot(
    height: () -> Dp,
    bottomGap: () -> Dp = { 0.dp },
): Modifier = layout { measurable, constraints ->
    val slot = height().roundToPx().coerceAtLeast(0)
    val gap = bottomGap().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
    )
    val total = (slot + gap).coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, total) {
        placeable.place(0, slot - placeable.height)
    }
}
