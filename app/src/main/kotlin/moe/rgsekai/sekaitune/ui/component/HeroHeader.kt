/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.component

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.screens.playlist.PlaylistHeroImageResult
import moe.rgsekai.sekaitune.ui.theme.SekaiMotion

private val ReceiptSmallStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 9.sp,
    letterSpacing = 0.04.em,
)

private val ReceiptCenterStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    letterSpacing = 0.42.em,
)

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
 * Wide pill Play/Pause button used in the playlist hero controls row.
 */
@Composable
fun PlayPillButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.globalEnabled

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (isGlass) Color.Transparent else Color.White.copy(alpha = 0.85f),
        contentColor = if (isGlass) Color.White else Color(0xFF1E1F23),
        shadowElevation = 0.dp,
        modifier = modifier
            .height(36.dp)
            .then(
                if (isGlass) {
                    Modifier.liquidGlass(
                        config = glassConfig,
                        shape = RoundedCornerShape(50),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(if (isPlaying) R.string.pause else R.string.play),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                ),
            )
        }
    }
}

/**
 * Circular 36dp action button (for Shuffle, Search) matching the hero controls row.
 */
@Composable
fun CircleActionButton(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassConfig = LocalGlassEffectConfig.current
    val isGlass = glassConfig.globalEnabled

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isGlass) Color.Transparent else Color.White.copy(alpha = 0.85f),
        contentColor = if (isGlass) Color.White else Color(0xFF1E1F23),
        shadowElevation = 0.dp,
        modifier = modifier
            .size(36.dp)
            .then(
                if (isGlass) {
                    Modifier.liquidGlass(
                        config = glassConfig,
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Text dropdown sort control ("custom order ▾") matching the reference playlist design.
 */
@Composable
fun <T> PlaylistSortTextControl(
    current: T,
    descending: Boolean,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onToggleDirection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = label(current).lowercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = if (!descending) 180f else 0f },
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF1E1F23),
            shape = RoundedCornerShape(16.dp),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label(option),
                            color = if (option == current) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = if (option == current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = if (option == current) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.expand_more),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = if (!descending) 180f else 0f },
                            )
                        }
                    } else null,
                    onClick = {
                        if (option == current) onToggleDirection() else onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Header item placed at index 0 of the LazyColumn.
 * Sits over the faded bottom of the hero backdrop with left-aligned title,
 * stats line, controls row (Play, Shuffle, Search), and sort controls.
 */
@Composable
fun PlaylistHeroHeader(
    title: String,
    stats: String,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onSearchClick: () -> Unit,
    coverHeight: Dp = rememberPlaylistCoverHeight(),
    modifier: Modifier = Modifier,
    sortControl: (@Composable () -> Unit)? = null,
    lockControl: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = coverHeight * 0.90f),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 24.dp, bottom = 12.dp),
        ) {
            // Large Bold Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp,
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle Stats ("1 song · 4m")
            Text(
                text = stats,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Controls Row: Play Pill + Shuffle Circle + Search Circle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PlayPillButton(
                    isPlaying = isPlaying,
                    onClick = onPlayClick,
                )

                CircleActionButton(
                    icon = R.drawable.shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    onClick = onShuffleClick,
                )

                CircleActionButton(
                    icon = R.drawable.search,
                    contentDescription = stringResource(R.string.search),
                    onClick = onSearchClick,
                )
            }

            if (sortControl != null || lockControl != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (sortControl != null) {
                        sortControl()
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (lockControl != null) {
                        lockControl()
                    }
                }
            }
        }
    }
}

/**
 * "+ Add Music" row matching the reference playlist screen, shown for editable playlists.
 */
@Composable
fun PlaylistAddMusicRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier.size(39.dp),
            shape = RoundedCornerShape(9.dp),
            color = Color.White.copy(alpha = 0.15f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.add_to_playlist),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
            color = Color.White,
        )
    }
}

/**
 * Receipt-style footer strip: N SONGS · E N D   O F   L I S T · N MINUTES between two rules.
 */
@Composable
fun PlaylistEndOfLine(
    songCount: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val minutes = (totalSeconds + 30) / 60
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .padding(top = 24.dp, bottom = 12.dp),
    ) {
        // Top Rule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.22f)),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        ) {
            Text(
                text = "${songCount} ${if (songCount == 1) "SONG" else "SONGS"}",
                style = ReceiptSmallStyle,
                color = Color.White.copy(alpha = 0.60f),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "E N D   O F   L I S T",
                style = ReceiptCenterStyle,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${minutes} ${if (minutes == 1) "MINUTE" else "MINUTES"}",
                style = ReceiptSmallStyle,
                color = Color.White.copy(alpha = 0.60f),
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }

        // Bottom Rule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.22f)),
        )
    }
}

/**
 * Featured Artist Ui model and row matching the reference playlist design.
 */
data class FeaturedArtistItem(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
)

@Composable
fun FeaturedArtistsRow(
    artists: List<FeaturedArtistItem>,
    onArtistClick: (FeaturedArtistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 32.dp, end = 24.dp)
                .padding(vertical = 4.dp),
        ) {
            Text(
                text = "Featured Artist",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items = artists, key = { it.id }) { artist ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onArtistClick(artist) },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        if (artist.thumbnailUrl != null) {
                            AsyncImage(
                                model = artist.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.person),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        ),
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Floating top overlay with circular back and more (three dots) buttons.
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
                    .padding(horizontal = 48.dp)
                    .graphicsLayer { alpha = titleAlpha },
            )
        }

        // More Button on right
        if (onMoreClick != null) {
            Surface(
                onClick = onMoreClick,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.35f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(34.dp),
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
