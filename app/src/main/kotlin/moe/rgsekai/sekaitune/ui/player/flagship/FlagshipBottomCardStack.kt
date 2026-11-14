package moe.rgsekai.sekaitune.ui.player.flagship

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import moe.rgsekai.sekaitune.R

internal val FlagshipCardHeight = 54.dp
internal val FlagshipCardCorner = 28.dp
internal val FlagshipPeekOffset = 9.dp
internal val FlagshipPeekInset = 10.dp
private const val BackCardScale = 0.94f

/**
 * Flagship bottom card stack showing two transparent stacked padding cards:
 * Card 0: Lyrics ticker / preview
 * Card 1: Queue peek (next song)
 *
 * Clicking either card or swiping up triggers opening the respective view (lyrics or queue).
 */
@Composable
fun FlagshipBottomCardStack(
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    currentLyric: String?,
    nextSongTitle: String?,
    nextSongArtist: String?,
    nextArtworkUrl: String?,
    modifier: Modifier = Modifier,
) {
    // 0 = Lyrics in front, 1 = Queue in front
    var frontIndex by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlagshipCardHeight + FlagshipPeekOffset)
            .pointerInput(Unit) {
                var pulledY = 0f
                detectVerticalDragGestures(
                    onDragStart = { pulledY = 0f },
                    onVerticalDrag = { _, amount ->
                        pulledY += amount
                        if (pulledY < -18f) {
                            if (frontIndex == 0) onOpenLyrics() else onOpenQueue()
                            pulledY = 0f
                        }
                    },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Render back card first, then front card
        val backIndex = 1 - frontIndex

        // Back card
        DeckCardView(
            isFront = false,
            onClick = {
                // Clicking back card flips or opens it
                frontIndex = backIndex
            },
        ) {
            if (backIndex == 0) {
                LyricCardContent(currentLyric)
            } else {
                QueueCardContent(nextSongTitle, nextSongArtist, nextArtworkUrl)
            }
        }

        // Front card
        DeckCardView(
            isFront = true,
            onClick = {
                if (frontIndex == 0) onOpenLyrics() else onOpenQueue()
            },
        ) {
            if (frontIndex == 0) {
                LyricCardContent(currentLyric)
            } else {
                QueueCardContent(nextSongTitle, nextSongArtist, nextArtworkUrl)
            }
        }
    }
}

@Composable
private fun DeckCardView(
    isFront: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val f = if (isFront) 1f else 0f
    val scale = if (isFront) 1f else BackCardScale
    val peek = if (isFront) 0.dp else FlagshipPeekOffset
    val inset = if (isFront) 0.dp else FlagshipPeekInset
    val fillAlpha = if (isFront) 0.04f else 0.02f
    val borderAlpha = if (isFront) 0.12f else 0.06f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlagshipCardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = -peek.toPx()
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawBehind {
                val insetPx = inset.toPx()
                val cornerPx = FlagshipCardCorner.toPx()
                val topLeft = Offset(insetPx, 0f)
                val cardSize = Size(size.width - insetPx * 2f, size.height + 40.dp.toPx())

                clipRect(bottom = size.height) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = fillAlpha),
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = borderAlpha),
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = 1.35.dp.toPx()),
                    )
                }
            }
            .clip(RoundedCornerShape(topStart = FlagshipCardCorner, topEnd = FlagshipCardCorner))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun LyricCardContent(lyric: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            painter = painterResource(R.drawable.flagship_lyrics),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = lyric?.takeIf { it.isNotBlank() } ?: "Lyrics",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.90f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.flagship_card_chevron),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.60f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun QueueCardContent(
    title: String?,
    artist: String?,
    artworkUrl: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.flagship_queue),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Next Song",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = if (!title.isNullOrBlank()) {
                    listOfNotNull(title, artist?.takeIf { it.isNotBlank() }).joinToString(" - ")
                } else {
                    "Queue"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(R.drawable.flagship_card_chevron),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.60f),
            modifier = Modifier.size(12.dp),
        )
    }
}

