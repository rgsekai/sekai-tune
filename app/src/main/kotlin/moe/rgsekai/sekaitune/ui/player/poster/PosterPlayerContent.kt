package moe.rgsekai.sekaitune.ui.player.poster

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import moe.rgsekai.sekaitune.constants.SyncLikesToSpotifyKey
import moe.rgsekai.sekaitune.utils.rememberPreference
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.ui.player.styles.PlayerPreviewData

/**
 * Poster Player UI layout:
 * - Huge circle crop artwork swung from the top-left corner.
 * - Circular seek bar arc / ring tracking progress.
 * - Bold expressive title / lyrics.
 * - Vertical 4-pill action stack on the bottom right (Like, Prev, Play/Pause, Next).
 */
@Composable
fun PosterPlayerContent(
    data: PlayerPreviewData,
    onLikeClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)
    val syncLikesToSpotify by rememberPreference(SyncLikesToSpotifyKey, false)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val w = maxWidth
        val h = maxHeight
        val density = LocalDensity.current
        val wPx = with(density) { w.toPx() }

        // Center and radius of circle cover swung in from top-left
        val centerOffset = Offset(0.13f * wPx, 0.06f * wPx)
        val radiusPx = 1.14f * wPx

        val shape = remember(centerOffset, radiusPx) {
            GenericShape { _, _ ->
                addOval(
                    Rect(
                        centerOffset.x - radiusPx,
                        centerOffset.y - radiusPx,
                        centerOffset.x + radiusPx,
                        centerOffset.y + radiusPx,
                    ),
                )
            }
        }

        // 1. Circle Cover Artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(w * 1.22f)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.9f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Transparent,
                            center = centerOffset + Offset(0f, 10.dp.toPx()),
                            radius = radiusPx * 1.06f,
                        ),
                        radius = radiusPx * 1.06f,
                        center = centerOffset + Offset(0f, 10.dp.toPx()),
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .drawWithContent {
                        drawContent()
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.82f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.32f),
                                center = centerOffset,
                                radius = radiusPx,
                            ),
                            radius = radiusPx,
                            center = centerOffset,
                        )
                    },
            ) {
                AsyncImage(
                    model = data.artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 2. Cover Rim Progress Arc
        val progressFraction = if (data.durationMs > 0L) {
            (data.progressMs.toFloat() / data.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0.35f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Draw seek arc around the rim of the circle
                    val startAngle = 18f
                    val sweepAngle = 76f * progressFraction
                    val arcRadius = radiusPx + 8.dp.toPx()

                    drawArc(
                        color = Color.White.copy(alpha = 0.25f),
                        startAngle = startAngle,
                        sweepAngle = 76f,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius),
                        size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )

                    drawArc(
                        color = Color.White,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius),
                        size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                },
        )

        // 3. Top Menu Option Button (Sekai Tune Real 3-dot Menu)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 20.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onMenuClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = "Options",
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
        }

        // 4. Large Bold Text (Title / Lyrics)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.70f)
                .padding(start = 24.dp, bottom = 48.dp),
        ) {
            Text(
                text = data.title,
                style = MaterialTheme.typography.displaySmall,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 46.sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = data.artist,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = secondaryForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (data.lyricLine.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = data.lyricLine,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = foreground.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 5. Vertical 4-Pill Action Stack on the Bottom Right
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val posterLikeIcon =
                if (syncLikesToSpotify) {
                    if (data.isLiked) R.drawable.check else R.drawable.add
                } else {
                    if (data.isLiked) R.drawable.ic_untitled_heart else R.drawable.ic_untitled_heart_unfill
                }

            // Heart / Like Pill
            PosterPillButton(
                icon = posterLikeIcon,
                onClick = onLikeClick,
                iconSize = 20.dp,
            )

            // Skip Previous Pill
            PosterPillButton(
                icon = R.drawable.flagship_skip_previous,
                onClick = onPreviousClick,
                iconSize = 22.dp,
            )

            // Play / Pause Pill
            PosterPillButton(
                icon = if (data.isPlaying) R.drawable.flagship_pause else R.drawable.flagship_play,
                onClick = onPlayPauseClick,
                iconSize = 22.dp,
            )

            // Skip Next Pill
            PosterPillButton(
                icon = R.drawable.flagship_skip_next,
                onClick = onNextClick,
                iconSize = 22.dp,
            )
        }
    }
}

@Composable
private fun PosterPillButton(
    icon: Int,
    onClick: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

