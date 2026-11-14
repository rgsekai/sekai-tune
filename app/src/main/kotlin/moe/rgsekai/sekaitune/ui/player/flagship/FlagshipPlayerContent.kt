package moe.rgsekai.sekaitune.ui.player.flagship

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.player.styles.PlayerPreviewData
import moe.rgsekai.sekaitune.utils.makeTimeString

/**
 * Flagship Player UI layout:
 * - Cover art occupying the top portion with subtle bottom fade into ambient background.
 * - Title + Artist with format badge (e.g., OPUS), like heart button, and Sekai Tune's real 3-dot menu button.
 * - Clean slider bar with 0:00 elapsed and total duration.
 * - Transport buttons: Skip Previous, Play/Pause, Skip Next.
 * - Stacked bottom cards: Lyrics and Queue (two transparent pads with peek card behind).
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
    nextSongTitle: String? = null,
    nextSongArtist: String? = null,
    nextArtworkUrl: String? = null,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Top Artwork with ambient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.68f),
        ) {
            AsyncImage(
                model = data.artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Vertical gradient fade at the bottom of the artwork
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.82f to Color.Black.copy(alpha = 0.65f),
                            1f to Color.Black,
                        ),
                    ),
            )
        }

        // Bottom Controls Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp),
        ) {
            // Track Info Row: Title, Artist, Badge, Heart, 3-dot Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        Spacer(Modifier.width(6.dp))

                        // Format Badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(foreground.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = data.formatTag.ifBlank { "OPUS" },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = foreground.copy(alpha = 0.90f),
                            )
                        }
                    }

                    Text(
                        text = data.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Action buttons: Like and Real 3-dot Menu
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Like button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(foreground.copy(alpha = 0.15f))
                            .clickable(onClick = onLikeClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (data.isLiked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill,
                            ),
                            contentDescription = "Like",
                            tint = if (data.isLiked) Color.White else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(19.dp),
                        )
                    }

                    // 3-dot menu button (Sekai Tune real more_vert menu)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(foreground.copy(alpha = 0.15f))
                            .clickable(onClick = onMenuClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = "Menu",
                            tint = foreground,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Seek Bar
            val safeDuration = if (data.durationMs > 0L) data.durationMs.toFloat() else 1f
            val safeProgress = data.progressMs.toFloat().coerceIn(0f, safeDuration)
            Slider(
                value = safeProgress,
                valueRange = 0f..safeDuration,
                onValueChange = {},
                colors = SliderDefaults.colors(
                    activeTrackColor = foreground,
                    inactiveTrackColor = foreground.copy(alpha = 0.28f),
                    thumbColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
            )

            // Elapsed & Total Time Labels
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            ) {
                Text(
                    text = makeTimeString(data.progressMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = secondaryForeground,
                )
                Text(
                    text = makeTimeString(data.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = secondaryForeground,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Transport Row: Previous, Play/Pause, Next
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(0.92f),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPreviousClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.flagship_skip_previous),
                        contentDescription = "Previous",
                        tint = foreground,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (data.isPlaying) R.drawable.flagship_pause else R.drawable.flagship_play,
                        ),
                        contentDescription = if (data.isPlaying) "Pause" else "Play",
                        tint = foreground,
                        modifier = Modifier.size(34.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onNextClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.flagship_skip_next),
                        contentDescription = "Next",
                        tint = foreground,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Stacked Bottom Cards (Lyrics & Queue with transparent paddings)
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

