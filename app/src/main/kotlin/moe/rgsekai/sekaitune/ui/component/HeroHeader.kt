/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.screens.playlist.PlaylistHeroImageResult
import moe.rgsekai.sekaitune.ui.theme.SekaiMotion

/**
 * Height of the full-bleed hero cover backdrop (58% of screen height).
 */
@Composable
fun rememberPlaylistCoverHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return (screenHeight * 0.58f).coerceIn(400.dp, 560.dp)
}

/**
 * Top gradient scrim to ensure status bar clock/icons and navigation icons
 * remain sharp, legible, and high-contrast over any hero image.
 */
@Composable
fun HeroTopScrim(modifier: Modifier = Modifier) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(statusBarTop + 72.dp)
            .drawWithCache {
                val scrim = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.55f),
                    0.5f to Color.Black.copy(alpha = 0.25f),
                    1.0f to Color.Transparent,
                )
                onDrawBehind { drawRect(scrim) }
            },
    )
}

/**
 * Multi-stop vertical gradient that blends the bottom of the hero card into the
 * screen background color with an eased curve. Reaches 100% opacity at 0.96f
 * so that the bottom edge of the hero is mathematically identical to the screen
 * background, eliminating any visible edge, line, or seam.
 */
@Composable
fun HeroBottomFade(
    dominantColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val scrim = Brush.verticalGradient(
                    0.00f to Color.Transparent,
                    0.35f to Color.Transparent,
                    0.50f to backgroundColor.copy(alpha = 0.15f),
                    0.62f to backgroundColor.copy(alpha = 0.38f),
                    0.74f to backgroundColor.copy(alpha = 0.65f),
                    0.85f to backgroundColor.copy(alpha = 0.88f),
                    0.93f to backgroundColor.copy(alpha = 0.98f),
                    0.97f to backgroundColor,
                    1.00f to backgroundColor,
                    startY = 0f,
                    endY = size.height,
                )
                onDrawBehind { drawRect(scrim) }
            },
    )
}

/**
 * Pinned full-bleed cover image backdrop drawn behind the list.
 * Includes subtle parallax translation, smooth scroll fade, top status bar scrim,
 * and the zero-seam bottom gradient ending in [backgroundColor].
 */
@Composable
fun PlaylistHeroBackdrop(
    heroImage: PlaylistHeroImageResult,
    dominantColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    coverHeight: Dp = rememberPlaylistCoverHeight(),
    scrollOffset: () -> Int = { 0 },
) {
    val context = LocalContext.current
    val offset = scrollOffset()
    val parallax = -offset * 0.35f
    val fadeAlpha = (1f - (offset / 900f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(coverHeight)
            .clipToBounds()
            .graphicsLayer {
                translationY = parallax
                alpha = fadeAlpha
            },
    ) {
        when (heroImage) {
            is PlaylistHeroImageResult.Image -> {
                val imageRequest = remember(heroImage.urlOrUri, context) {
                    ImageRequest.Builder(context)
                        .data(heroImage.urlOrUri)
                        .crossfade(300)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SekaiMotion.BoneColor),
                )
            }

            is PlaylistHeroImageResult.Placeholder -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    dominantColor.copy(alpha = 0.45f),
                                    backgroundColor.copy(alpha = 0.85f),
                                    backgroundColor,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.music_note),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }
            }
        }

        // Top scrim for status bar legibility
        HeroTopScrim(modifier = Modifier.align(Alignment.TopCenter))

        // Multi-stop bottom fade into page background
        HeroBottomFade(
            dominantColor = dominantColor,
            backgroundColor = backgroundColor,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Title and subtitle overlaid over the bottom faded area of the hero card.
 */
@Composable
fun PlaylistHeroTitleArea(
    title: String,
    subtitle: String,
    coverHeight: Dp = rememberPlaylistCoverHeight(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = (coverHeight - 110.dp).coerceAtLeast(180.dp))
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Floating top overlay with circular back, search, and more buttons.
 * When the list scrolls past the hero, the bar smoothly transitions into
 * [backgroundColor] and displays the compact playlist title.
 */
@Composable
fun PlaylistTopBarOverlay(
    title: String,
    scrolled: Boolean,
    backgroundColor: Color,
    onBackClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val animatedBgColor by animateColorAsState(
        targetValue = if (scrolled) backgroundColor else Color.Transparent,
        animationSpec = tween(280),
        label = "playlistTopBarBg",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(220),
        label = "playlistTopBarTitleAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(animatedBgColor)
            .padding(top = statusBarTop, bottom = 8.dp)
            .padding(horizontal = 24.dp)
            .height(48.dp),
    ) {
        // Back Button on left
        Surface(
            onClick = onBackClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.35f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Compact Title in Center (only visible when scrolled past hero)
        if (titleAlpha > 0.01f) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 72.dp)
                    .graphicsLayer { alpha = titleAlpha },
            )
        }

        // Action buttons on right
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onSearchClick != null) {
                Surface(
                    onClick = onSearchClick,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (onMoreClick != null) {
                Surface(
                    onClick = onMoreClick,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
