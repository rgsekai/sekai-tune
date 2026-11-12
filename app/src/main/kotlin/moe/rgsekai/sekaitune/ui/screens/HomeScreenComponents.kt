/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import android.os.Build
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.constants.DisableBlurKey
import moe.rgsekai.sekaitune.utils.rememberPreference
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.GridThumbnailHeight
import moe.rgsekai.sekaitune.constants.ListItemHeight
import moe.rgsekai.sekaitune.constants.ListThumbnailSize
import moe.rgsekai.sekaitune.constants.ThumbnailCornerRadius
import moe.rgsekai.sekaitune.db.entities.Album
import moe.rgsekai.sekaitune.db.entities.Artist
import moe.rgsekai.sekaitune.db.entities.LocalItem
import moe.rgsekai.sekaitune.db.entities.Playlist
import moe.rgsekai.sekaitune.db.entities.Song
import moe.rgsekai.sekaitune.extensions.toMediaItem
import moe.rgsekai.sekaitune.extensions.togglePlayPause
import moe.rgsekai.sekaitune.innertube.models.AlbumItem
import moe.rgsekai.sekaitune.innertube.models.ArtistItem
import moe.rgsekai.sekaitune.innertube.models.PlaylistItem
import moe.rgsekai.sekaitune.innertube.models.SongItem
import moe.rgsekai.sekaitune.innertube.models.WatchEndpoint
import moe.rgsekai.sekaitune.innertube.models.YTItem
import moe.rgsekai.sekaitune.innertube.pages.HomePage
import moe.rgsekai.sekaitune.models.MediaMetadata
import moe.rgsekai.sekaitune.models.SimilarRecommendation
import moe.rgsekai.sekaitune.models.toMediaMetadata
import moe.rgsekai.sekaitune.playback.PlayerConnection
import moe.rgsekai.sekaitune.playback.queues.ListQueue
import moe.rgsekai.sekaitune.playback.queues.YouTubeQueue
import moe.rgsekai.sekaitune.ui.component.AlbumGridItem
import moe.rgsekai.sekaitune.ui.component.ArtistGridItem
import moe.rgsekai.sekaitune.ui.component.MenuState
import moe.rgsekai.sekaitune.ui.component.SongGridItem
import moe.rgsekai.sekaitune.ui.component.SongListItem
import moe.rgsekai.sekaitune.ui.component.SpeedDialGridItem
import moe.rgsekai.sekaitune.ui.component.YouTubeGridItem
import moe.rgsekai.sekaitune.ui.menu.AlbumMenu
import moe.rgsekai.sekaitune.ui.menu.ArtistMenu
import moe.rgsekai.sekaitune.ui.menu.PlaylistMenu
import moe.rgsekai.sekaitune.ui.menu.SongMenu
import moe.rgsekai.sekaitune.ui.menu.YouTubeAlbumMenu
import moe.rgsekai.sekaitune.ui.menu.YouTubeArtistMenu
import moe.rgsekai.sekaitune.ui.menu.YouTubePlaylistMenu
import moe.rgsekai.sekaitune.ui.menu.YouTubeSongMenu
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        thumbnail?.invoke()
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val HeroSlideEasing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f)
private const val HeroSlideMillis = 1150
private const val HeroAdvanceMillis = 5500L
private val HeroPillFill = Color.White.copy(alpha = 0.64f)
private val HeroPillContent = Color(0xFF2F2F2F)
private val HeroLabelColor = Color.White.copy(alpha = 0.69f)

private const val PageMediaTravel = 0.18f
private const val PageTextTravel = 0.42f
private const val PageWipeFeather = 0.55f
private const val PageWipeTrail = 0.25f
private val PageSwitchBlur = 18.dp
private const val PageWipeCurveReach = 0.6f
private const val PageWipeFlat = 0.04f
private const val PageWipeSettled = 0.001f
private const val PageWipeHidden = 0.999f

private fun PagerState.offsetOf(page: Int): Float =
    (page - currentPage) - currentPageOffsetFraction

private fun Modifier.pageMediaDrift(pageOffset: () -> Float): Modifier = graphicsLayer {
    val d = pageOffset().coerceIn(-1f, 1f)
    val scale = 1f + 2f * PageMediaTravel * kotlin.math.abs(d)
    scaleX = scale
    scaleY = scale
    translationX = d * size.width * PageMediaTravel
}

private fun Modifier.pageTextDrift(pageOffset: () -> Float): Modifier = graphicsLayer {
    val d = pageOffset()
    translationX = d * size.width * (PageTextTravel + PageMediaTravel)
    alpha = (1f - kotlin.math.abs(d) * 1.9f).coerceIn(0f, 1f)
}

private class PageWipeEdge {
    var incoming = false
    var centerX = 0f
    var centerY = 0f
    var inner = 0f
    var outer = 0f
    var blur = 0f

    fun update(d: Float, size: GeometrySize, curve: Float, blurPx: Float): Boolean {
        val a = kotlin.math.abs(d)
        if (a < PageWipeSettled || a >= PageWipeHidden) return false
        val w = size.width
        val feather = w * PageWipeFeather
        val progress = if (d > 0f) 1f - d else -d
        val off = w * PageWipeCurveReach / curve.coerceIn(PageWipeFlat, 1f)
        centerX = w + off
        centerY = size.height / 2f
        val from = off - feather
        val to = kotlin.math.hypot(centerX, centerY)
        val r = from + (to - from) * progress
        incoming = d > 0f
        if (incoming) {
            inner = r
            outer = r + feather
        } else {
            inner = r - feather * PageWipeTrail
            outer = inner + feather
        }
        blur = a * blurPx
        val erases = if (incoming) inner < to else outer > off
        return erases || blur > 0.5f
    }
}

private val FeatherEase = floatArrayOf(0f, 0.104f, 0.352f, 0.648f, 0.896f, 1f)

private fun featherStops(start: Float, inside: Color, outside: Color): Array<Pair<Float, Color>> {
    val s = start.coerceIn(0f, 0.999f)
    return Array(FeatherEase.size) { i ->
        val t = i / (FeatherEase.size - 1f)
        (s + (1f - s) * t) to lerp(inside, outside, FeatherEase[i])
    }
}

@Composable
private fun PageWipeCopy(
    pageLayer: GraphicsLayer,
    pageOffset: () -> Float,
    curve: () -> Float,
    blurPx: Float,
) {
    val edge = remember { PageWipeEdge() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                val a = kotlin.math.abs(pageOffset())
                if (blurPx > 0.5f && a >= PageWipeSettled && a < PageWipeHidden && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val r = (a * blurPx).coerceIn(0f, 48f)
                    if (r > 0.5f) {
                        renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Clamp)
                    }
                }
            }
            .clipToBounds()
            .drawWithContent {
                if (!edge.update(pageOffset(), size, curve(), blurPx)) return@drawWithContent
                val w = size.width
                val h = size.height
                val halfH = h / 2f
                fun fullyInside(radius: Float): Float =
                    if (radius > halfH) kotlin.math.min(w, edge.centerX - kotlin.math.sqrt(radius * radius - halfH * halfH)) else w
                val left: Float
                val right: Float
                val maskLeft: Float
                val maskRight: Float
                if (edge.incoming) {
                    left = (edge.centerX - edge.outer).coerceAtLeast(0f)
                    right = w
                    maskLeft = left
                    maskRight = fullyInside(edge.inner)
                } else {
                    left = 0f
                    right = fullyInside(edge.inner)
                    maskLeft = (edge.centerX - edge.outer).coerceAtLeast(0f)
                    maskRight = right
                }
                if (right <= left) return@drawWithContent
                clipRect(left, 0f, right, h) { drawLayer(pageLayer) }
                if (maskRight > maskLeft) {
                    val center = Offset(edge.centerX, edge.centerY)
                    val stops = if (edge.incoming) {
                        featherStops(edge.inner / edge.outer, inside = Color.Transparent, outside = Color.Black)
                    } else {
                        featherStops(edge.inner / edge.outer, inside = Color.Black, outside = Color.Transparent)
                    }
                    drawRect(
                        brush = Brush.radialGradient(*stops, center = center, radius = edge.outer),
                        topLeft = Offset(maskLeft, 0f),
                        size = GeometrySize(maskRight - maskLeft, h),
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
    )
}

@Composable
private fun PageWipe(
    pageOffset: () -> Float,
    curve: () -> Float,
    blur: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val blurPx = if (blur) with(LocalDensity.current) { PageSwitchBlur.toPx() } else 0f
    val pageLayer = rememberGraphicsLayer()
    val sourceEdge = remember { PageWipeEdge() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val d = pageOffset()
                translationX = -d * size.width
                alpha = if (kotlin.math.abs(d) >= PageWipeHidden) 0f else 1f
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .drawWithContent {
                    if (sourceEdge.update(pageOffset(), size, curve(), blurPx)) {
                        pageLayer.record { this@drawWithContent.drawContent() }
                    } else {
                        drawContent()
                    }
                },
            content = content,
        )
        PageWipeCopy(pageLayer = pageLayer, pageOffset = pageOffset, curve = curve, blurPx = blurPx)
    }
}

@Composable
private fun Modifier.heroPressBounce(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.88f,
): Modifier {
    val scale = remember { Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(interactionSource, pressedScale) {
        var pressed = 0
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    pressed++
                    launch { scale.animateTo(pressedScale, spring(dampingRatio = 1f, stiffness = 1600f)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    pressed = (pressed - 1).coerceAtLeast(0)
                    if (pressed == 0) {
                        launch {
                            if (scale.value > pressedScale + 0.03f) scale.animateTo(pressedScale, tween(70))
                            scale.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 520f))
                        }
                    }
                }
            }
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

private const val HeroDim = 0.36f
private fun heroScrimBlack(gradient: Float): Color =
    Color.Black.copy(alpha = 1f - (1f - HeroDim) * (1f - gradient))

@Composable
private fun HeroScrims(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .drawWithCache {
                    val bottomStart = 0.533f
                    val bottomEnd = 0.989f
                    val scrim =
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            bottomStart to Color.Transparent,
                            bottomStart + 0.457f * (bottomEnd - bottomStart) to heroScrimBlack(0.59f),
                            bottomEnd to heroScrimBlack(1f),
                            1f to heroScrimBlack(1f),
                            startY = 0f,
                            endY = size.height,
                        )
                    onDrawBehind { drawRect(scrim) }
                },
    )
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun QuickPicksSection(
    quickPicks: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val distinctQuickPicks = remember(quickPicks) { quickPicks.distinctBy { it.id } }
    if (distinctQuickPicks.isEmpty()) return

    val isSingleItem = distinctQuickPicks.size == 1
    val initialPage =
        remember(distinctQuickPicks.size) {
            if (isSingleItem) 0
            else (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % distinctQuickPicks.size)
        }
    val pagerState =
        rememberPagerState(
            initialPage = initialPage,
            pageCount = { if (isSingleItem) 1 else Int.MAX_VALUE },
        )

    val (disableBlur) = rememberPreference(DisableBlurKey, defaultValue = false)
    val edgeBlur = !disableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    if (!isSingleItem) {
        val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
        androidx.compose.runtime.LaunchedEffect(pagerState, distinctQuickPicks.size, isDragged) {
            if (isDragged) return@LaunchedEffect
            while (true) {
                delay(HeroAdvanceMillis)
                pagerState.animateScrollToPage(
                    page = pagerState.currentPage + 1,
                    animationSpec = tween(HeroSlideMillis, easing = HeroSlideEasing),
                )
            }
        }
    }

    var userSwipe by remember { androidx.compose.runtime.mutableStateOf(false) }
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    androidx.compose.runtime.LaunchedEffect(dragging) { if (dragging) userSwipe = true }
    androidx.compose.runtime.LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress }.collect { if (!it) userSwipe = false }
    }
    val wipeCurve by animateFloatAsState(if (userSwipe) 1f else 0f, tween(260), label = "heroWipeCurve")

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = screenHeight * 0.60f

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clipToBounds(),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSingleItem,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pageOffset = remember(pagerState, page) { { pagerState.offsetOf(page) } }
            val song = distinctQuickPicks[page.mod(distinctQuickPicks.size)]
            val isSongActive = song.id == mediaMetadata?.id
            val isSongPlaying = isSongActive && isPlaying
            val context = LocalContext.current

            val imageRequest =
                remember(song.song.thumbnailUrl, context) {
                    ImageRequest
                        .Builder(context)
                        .data(song.song.thumbnailUrl)
                        .crossfade(300)
                        .build()
                }

            PageWipe(
                pageOffset = pageOffset,
                curve = { wipeCurve },
                blur = edgeBlur,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = {
                                    if (isSongActive) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            if (song.song.isLocal) {
                                                ListQueue(items = listOf(song.toMediaItem()))
                                            } else {
                                                YouTubeQueue.radio(song.toMediaMetadata())
                                            },
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                ) {
                    // Background cover image with page drift & subtle zoom
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pageMediaDrift(pageOffset),
                    )

                    // Progressive blur band at bottom if blur is supported and enabled
                    if (edgeBlur) {
                        // Bottom blur band
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.50f)
                                    .blur(30.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.5f to Color.Black.copy(alpha = 0.40f),
                                            1f to Color.Black.copy(alpha = 0.85f),
                                        ),
                                    ),
                        )
                    }

                    // Multi-stop scrim gradient
                    HeroScrims()

                    // Content at bottom-left with page text drift
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .pageTextDrift(pageOffset)
                                .padding(start = 36.dp, end = 36.dp, bottom = 64.dp),
                    ) {
                        // Title & Artist with smooth transition
                        val songTitle = song.song.title
                        val artistName = remember(song.artists) {
                            song.artists.joinToString { it.name }
                        }

                        AnimatedContent(
                            targetState = songTitle to artistName,
                            transitionSpec = {
                                (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 3 } + fadeIn(tween(220)))
                                    .togetherWith(slideOutVertically(tween(160)) { -it / 3 } + fadeOut(tween(140)))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "heroSongInfo",
                        ) { (title, artist) ->
                            Column {
                                // 1st line: Song Title (single line with ellipsis)
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.04).sp,
                                        lineHeight = 30.sp,
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (artist.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    // 2nd line: Artist Name (low contrast / secondary tone)
                                    Text(
                                        text = artist,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = (-0.02).sp,
                                        ),
                                        color = Color.White.copy(alpha = 0.70f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(11.dp))

                        // Capsule Play / Pause Pill button
                        val pillInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier =
                                Modifier
                                    .heroPressBounce(pillInteraction)
                                    .height(33.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(HeroPillFill)
                                    .clickable(
                                        interactionSource = pillInteraction,
                                        indication = null,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isSongActive) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    if (song.song.isLocal) {
                                                        ListQueue(items = listOf(song.toMediaItem()))
                                                    } else {
                                                        YouTubeQueue.radio(song.toMediaMetadata())
                                                    },
                                                )
                                            }
                                        },
                                    ),
                        ) {
                            AnimatedContent(
                                targetState = isSongPlaying,
                                transitionSpec = {
                                    (scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f)) + fadeIn(tween(130)))
                                        .togetherWith(scaleOut(targetScale = 0.6f, animationSpec = tween(110)) + fadeOut(tween(90)))
                                        .using(SizeTransform(clip = false) { _, _ -> spring(dampingRatio = 0.75f, stiffness = 520f) })
                                },
                                label = "heroPillPlayback",
                            ) { playing ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier =
                                        Modifier
                                            .height(33.dp)
                                            .padding(start = 21.dp, end = 17.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (playing) R.drawable.pause else R.drawable.play
                                        ),
                                        contentDescription = null,
                                        tint = HeroPillContent,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        text = if (playing) stringResource(R.string.pause) else stringResource(R.string.play),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.02).sp,
                                        ),
                                        color = HeroPillContent,
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

private const val SpeedDialGridRows = 3
private const val SpeedDialGridColumns = 3
private const val SpeedDialItemsPerPage = SpeedDialGridRows * SpeedDialGridColumns

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedDialSection(
    speedDialItems: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    data class SpeedDialTile(
        val key: String,
        val localItem: LocalItem?,
        val ytItem: YTItem?,
    )

    val distinctSpeedDial =
        remember(speedDialItems) {
            speedDialItems
                .distinctBy {
                    when (it) {
                        is Song -> "song_${it.id}"
                        is Album -> "album_${it.id}"
                        is Artist -> "artist_${it.id}"
                        is Playlist -> "playlist_${it.id}"
                    }
                }.take(24)
        }
    val speedDialSongs = remember(distinctSpeedDial) { distinctSpeedDial.filterIsInstance<Song>() }
    val speedDialSongIndexById =
        remember(speedDialSongs) {
            speedDialSongs.mapIndexed { index, song -> song.id to index }.toMap()
        }
    val spacing = 10.dp

    val tiles =
        remember(distinctSpeedDial) {
            buildList {
                distinctSpeedDial.forEach { localItem ->
                    val key =
                        when (localItem) {
                            is Song -> "song_${localItem.id}"
                            is Album -> "album_${localItem.id}"
                            is Artist -> "artist_${localItem.id}"
                            is Playlist -> "playlist_${localItem.id}"
                        }
                    val ytItem =
                        when (localItem) {
                            is Song -> {
                                SongItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    artists =
                                        localItem.artists.map {
                                            moe.rgsekai.sekaitune.innertube.models
                                                .Artist(name = it.name, id = it.id)
                                        },
                                    thumbnail = localItem.song.thumbnailUrl.orEmpty(),
                                    explicit = localItem.song.explicit,
                                )
                            }

                            is Album -> {
                                AlbumItem(
                                    browseId = localItem.id,
                                    playlistId = localItem.album.playlistId.orEmpty(),
                                    title = localItem.title,
                                    artists =
                                        localItem.artists.map {
                                            moe.rgsekai.sekaitune.innertube.models
                                                .Artist(name = it.name, id = it.id)
                                        },
                                    year = localItem.album.year,
                                    thumbnail = localItem.album.thumbnailUrl.orEmpty(),
                                )
                            }

                            is Artist -> {
                                ArtistItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    thumbnail = localItem.artist.thumbnailUrl,
                                    channelId = localItem.artist.channelId,
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                )
                            }

                            is Playlist -> {
                                PlaylistItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    author = null,
                                    songCountText = localItem.songCount.toString(),
                                    thumbnail = localItem.thumbnails.firstOrNull(),
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                    isEditable = localItem.playlist.isEditable,
                                )
                            }
                        }
                    add(SpeedDialTile(key = key, localItem = localItem, ytItem = ytItem))
                }
                add(SpeedDialTile(key = "random", localItem = null, ytItem = null))
            }
        }
    val tilePages =
        remember(tiles) {
            tiles.chunked(SpeedDialItemsPerPage)
        }
    val visibleGridRows =
        remember(tilePages) {
            if (tilePages.size == 1) {
                ((tilePages.first().size + SpeedDialGridColumns - 1) / SpeedDialGridColumns)
                    .coerceIn(1, SpeedDialGridRows)
            } else {
                SpeedDialGridRows
            }
        }
    val pagerState =
        rememberPagerState(
            pageCount = { tilePages.size },
        )

    fun playSpeedDialQueue(startIndex: Int) {
        if (speedDialSongs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = context.getString(R.string.speed_dial),
                items = speedDialSongs.map { it.toMediaItem() },
                startIndex = startIndex,
            ),
        )
    }

    val selectedDotIndex by
        remember(pagerState, tilePages) {
            derivedStateOf {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .roundToInt()
                    .coerceIn(0, (tilePages.size - 1).coerceAtLeast(0))
            }
        }
    val motionScheme = MaterialTheme.motionScheme

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier =
            modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
            ) {
                val tileSize = (maxWidth - spacing * (SpeedDialGridColumns - 1)) / SpeedDialGridColumns
                val gridHeight = (tileSize * visibleGridRows) + (spacing * (visibleGridRows - 1))

                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fill,
                    pageSpacing = spacing,
                    key = { page -> tilePages[page].firstOrNull()?.key ?: "speed_dial_page_$page" },
                    verticalAlignment = Alignment.Top,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                ) { page ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        tilePages[page]
                            .chunked(SpeedDialGridColumns)
                            .forEach { rowTiles ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    rowTiles.forEach { tile ->
                                        val localItem = tile.localItem
                                        val ytItem = tile.ytItem
                                        if (localItem == null || ytItem == null) {
                                            SpeedDialRandomTile(
                                                onClick = {
                                                    if (speedDialSongs.isNotEmpty()) {
                                                        playSpeedDialQueue(Random.nextInt(speedDialSongs.size))
                                                    }
                                                },
                                                modifier = Modifier.size(tileSize),
                                            )
                                        } else {
                                            val isActive =
                                                when (localItem) {
                                                    is Song -> localItem.id == mediaMetadata?.id
                                                    is Album -> localItem.id == mediaMetadata?.album?.id
                                                    is Artist -> false
                                                    is Playlist -> false
                                                }
                                            val songIndex =
                                                if (localItem is Song) speedDialSongIndexById[localItem.id] ?: 0 else 0

                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(tileSize)
                                                        .clip(MaterialTheme.shapes.large)
                                                        .focusable()
                                                        .combinedClickable(
                                                            onClick = {
                                                                when (localItem) {
                                                                    is Song -> {
                                                                        if (isActive) {
                                                                            playerConnection.player.togglePlayPause()
                                                                        } else {
                                                                            playSpeedDialQueue(songIndex)
                                                                        }
                                                                    }

                                                                    is Album -> {
                                                                        navController.navigate("album/${localItem.id}")
                                                                    }

                                                                    is Artist -> {
                                                                        navController.navigate("artist/${localItem.id}")
                                                                    }

                                                                    is Playlist -> {
                                                                        navController.navigate("local_playlist/${localItem.id}")
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    when (localItem) {
                                                                        is Song -> {
                                                                            SongMenu(
                                                                                originalSong = localItem,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Album -> {
                                                                            AlbumMenu(
                                                                                originalAlbum = localItem,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Artist -> {
                                                                            ArtistMenu(
                                                                                originalArtist = localItem,
                                                                                coroutineScope = scope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Playlist -> {
                                                                            PlaylistMenu(
                                                                                playlist = localItem,
                                                                                coroutineScope = scope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                        ),
                                            ) {
                                                SpeedDialGridItem(
                                                    item = ytItem,
                                                    isPinned = true,
                                                    isActive = isActive,
                                                    isPlaying = isPlaying,
                                                )
                                            }
                                        }
                                    }
                                    repeat(SpeedDialGridColumns - rowTiles.size) {
                                        Spacer(modifier = Modifier.size(tileSize))
                                    }
                                }
                            }
                    }
                }
            }

            if (tilePages.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    repeat(tilePages.size) { index ->
                        val isSelected = index == selectedDotIndex
                        val dotColor by animateColorAsState(
                            targetValue =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            animationSpec = motionScheme.defaultEffectsSpec(),
                            label = "speedDialDotColor",
                        )
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 8.dp,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            label = "speedDialDotWidth",
                        )
                        Surface(
                            color = dotColor,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier =
                                Modifier
                                    .width(dotWidth)
                                    .height(8.dp),
                        ) {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialRandomTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier =
            modifier
                .aspectRatio(1f)
                .combinedClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp),
                    ) {}
                }
            }
        }
    }
}

/**
 * Keep Listening section - horizontal grid of local items
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepListeningSection(
    keepListening: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val rows = if (keepListening.size > 6) 2 else 1
    val gridHeight =
        (
            GridThumbnailHeight +
                with(LocalDensity.current) {
                    MaterialTheme.typography.bodyLarge.lineHeight
                        .toDp() * 2 +
                        MaterialTheme.typography.bodyMedium.lineHeight
                            .toDp() * 2
                }
        ) * rows

    LazyHorizontalGrid(
        state = rememberLazyGridState(),
        rows = GridCells.Fixed(rows),
        modifier =
            modifier
                .fillMaxWidth()
                .height(gridHeight),
    ) {
        items(
            items = keepListening,
            key = { item ->
                when (item) {
                    is Song -> "song_${item.id}"
                    is Album -> "album_${item.id}"
                    is Artist -> "artist_${item.id}"
                    is Playlist -> "playlist_${item.id}"
                }
            },
            contentType = { item -> item::class },
        ) { item ->
            LocalGridItem(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

/**
 * Forgotten Favorites section - horizontal grid of songs
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForgottenFavoritesSection(
    forgottenFavorites: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    horizontalLazyGridItemWidth: Dp,
    lazyGridState: LazyGridState,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val rows = min(4, forgottenFavorites.size)
    val distinctForgottenFavorites = remember(forgottenFavorites) { forgottenFavorites.distinctBy { it.id } }

    LazyHorizontalGrid(
        state = lazyGridState,
        rows = GridCells.Fixed(rows),
        flingBehavior = rememberSnapFlingBehavior(lazyGridState),
        contentPadding =
            WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        modifier =
            modifier
                .fillMaxWidth()
                .height(ListItemHeight * rows),
    ) {
        items(
            items = distinctForgottenFavorites,
            key = { it.id },
            contentType = { "forgotten_favorite_song" },
        ) { song ->
            SongListItem(
                song = song,
                showInLibraryIcon = true,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                isSwipeable = false,
                trailingContent = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                },
                modifier =
                    Modifier
                        .width(horizontalLazyGridItemWidth)
                        .focusable()
                        .combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        if (song.song.isLocal) {
                                            ListQueue(items = listOf(song.toMediaItem()))
                                        } else {
                                            YouTubeQueue.radio(song.toMediaMetadata())
                                        },
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
            )
        }
    }
}

/**
 * Account Playlists section - horizontal row of YouTube playlists
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPlaylistsSection(
    accountPlaylists: List<PlaylistItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val distinctPlaylists = remember(accountPlaylists) { accountPlaylists.distinctBy { it.id } }

    LazyRow(
        contentPadding =
            WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        modifier = modifier,
    ) {
        items(
            items = distinctPlaylists,
            key = { it.id },
            contentType = { "account_playlist" },
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

/**
 * Similar Recommendations section
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimilarRecommendationsSection(
    recommendation: SimilarRecommendation,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding =
            WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        modifier = modifier,
    ) {
        items(
            items = recommendation.items,
            key = { it.id },
            contentType = { item -> item::class },
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

/**
 * HomePage Section - a single section from YouTube home page
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageSectionContent(
    section: HomePage.Section,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding =
            WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        modifier = modifier,
    ) {
        items(
            items = section.items,
            key = { it.id },
            contentType = { item -> item::class },
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

// ============== Helper Composables ==============

/**
 * Wrapper for YouTube grid items with click handling
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeGridItemWrapper(
    item: YTItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    YouTubeGridItem(
        item = item,
        isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
        isPlaying = isPlaying,
        coroutineScope = scope,
        thumbnailRatio = 1f,
        modifier =
            modifier
                .focusable()
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> {
                                playerConnection.playQueue(
                                    YouTubeQueue(
                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                        item.toMediaMetadata(),
                                    ),
                                )
                            }

                            is AlbumItem -> {
                                navController.navigate("album/${item.id}")
                            }

                            is ArtistItem -> {
                                navController.navigate("artist/${item.id}")
                            }

                            is PlaylistItem -> {
                                navController.navigate("online_playlist/${item.id}")
                            }
                        }
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            when (item) {
                                is SongItem -> {
                                    YouTubeSongMenu(
                                        song = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }

                                is AlbumItem -> {
                                    YouTubeAlbumMenu(
                                        albumItem = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }

                                is ArtistItem -> {
                                    YouTubeArtistMenu(
                                        artist = item,
                                        onDismiss = menuState::dismiss,
                                    )
                                }

                                is PlaylistItem -> {
                                    YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = scope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        }
                    },
                ),
    )
}

/**
 * Local item grid item for songs, albums, artists
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalGridItem(
    item: LocalItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is Song -> {
            SongGridItem(
                song = item,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .focusable()
                        .combinedClickable(
                            onClick = {
                                if (item.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
                isActive = item.id == mediaMetadata?.id,
                isPlaying = isPlaying,
            )
        }

        is Album -> {
            AlbumGridItem(
                album = item,
                isActive = item.id == mediaMetadata?.album?.id,
                isPlaying = isPlaying,
                coroutineScope = scope,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .focusable()
                        .combinedClickable(
                            onClick = { navController.navigate("album/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = item,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
            )
        }

        is Artist -> {
            ArtistGridItem(
                artist = item,
                modifier =
                    modifier
                        .fillMaxWidth()
                        .focusable()
                        .combinedClickable(
                            onClick = { navController.navigate("artist/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    ArtistMenu(
                                        originalArtist = item,
                                        coroutineScope = scope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ),
            )
        }

        is Playlist -> { /* Not displayed */ }
    }
}

/**
 * Account playlist navigation title with image
 */
@Composable
fun AccountPlaylistsTitle(
    accountName: String,
    accountImageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        label = stringResource(R.string.your_youtube_playlists),
        title = accountName.ifBlank { stringResource(R.string.account) },
        thumbnail = {
            if (accountImageUrl != null) {
                val context = LocalContext.current
                val avatarSizePx =
                    with(LocalDensity.current) {
                        ListThumbnailSize.roundToPx().coerceAtLeast(1)
                    }
                val imageRequest =
                    remember(accountImageUrl, avatarSizePx) {
                        ImageRequest
                            .Builder(context)
                            .data(accountImageUrl)
                            .size(Size(avatarSizePx, avatarSizePx))
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(accountImageUrl)
                            .crossfade(true)
                            .build()
                    }
                AsyncImage(
                    model = imageRequest,
                    placeholder = painterResource(id = R.drawable.person),
                    error = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier.size(ListThumbnailSize),
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Similar recommendations navigation title
 */
@Composable
fun SimilarRecommendationsTitle(
    recommendation: SimilarRecommendation,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        label = stringResource(R.string.similar_to),
        title = recommendation.title.title,
        thumbnail =
            recommendation.title.thumbnailUrl?.let { thumbnailUrl ->
                {
                    val shape =
                        if (recommendation.title is Artist) {
                            CircleShape
                        } else {
                            RoundedCornerShape(ThumbnailCornerRadius)
                        }
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(shape),
                    )
                }
            },
        onClick = {
            when (recommendation.title) {
                is Song -> {
                    navController.navigate("album/${recommendation.title.album!!.id}")
                }

                is Album -> {
                    navController.navigate("album/${recommendation.title.id}")
                }

                is Artist -> {
                    navController.navigate("artist/${recommendation.title.id}")
                }

                is Playlist -> {}
            }
        },
        modifier = modifier,
    )
}

/**
 * HomePage section navigation title
 */
@Composable
fun HomePageSectionTitle(
    section: HomePage.Section,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        title = section.title,
        label = section.label,
        thumbnail =
            section.thumbnail?.let { thumbnailUrl ->
                {
                    val shape =
                        if (section.endpoint?.isArtistEndpoint == true) {
                            CircleShape
                        } else {
                            RoundedCornerShape(ThumbnailCornerRadius)
                        }
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(shape),
                    )
                }
            },
        onClick =
            section.endpoint?.browseId?.let { browseId ->
                {
                    if (browseId == "FEmusic_moods_and_genres") {
                        navController.navigate(Screens.MoodAndGenres.route)
                    } else {
                        navController.navigate("browse/$browseId")
                    }
                }
            },
        modifier = modifier,
    )
}


