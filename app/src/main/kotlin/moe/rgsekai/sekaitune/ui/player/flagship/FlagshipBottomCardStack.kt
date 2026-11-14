package moe.rgsekai.sekaitune.ui.player.flagship

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import moe.rgsekai.sekaitune.R
import kotlin.math.abs

internal val FlagshipCardHeight = 54.dp
internal val FlagshipCardCorner = 28.dp
internal val FlagshipPeekOffset = 9.dp
internal val FlagshipPeekInset = 10.dp
private const val BackCardScale = 0.94f
private val BottomBleed = 100.dp
private val FlipDistance = 70.dp

enum class FlagshipPage {
    NONE,
    LYRICS,
    QUEUE,
}

private fun mixF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * Mochy-exact Flagship bottom card deck:
 * - Shows Lyrics card and Queue card in a stacked deck.
 * - Upward drag flips between lyrics and queue with spring physics (`Spring.DampingRatioLowBouncy`).
 * - Swiping up past threshold or tapping the front card opens the respective view.
 * - Tapping the back peeking card brings it to the front.
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
    activePage: FlagshipPage = FlagshipPage.NONE,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val flipDistancePx = with(density) { FlipDistance.toPx() }

    var frontIndex by remember { mutableIntStateOf(0) } // 0 = lyrics, 1 = queue
    val flipAnim = remember { Animatable(0f) }

    // When a page is open, flip the deck so the card on top is for the other view:
    // If lyrics page is open -> Queue card is in front
    // If queue page is open -> Lyrics card is in front
    val queueFrontness = when (activePage) {
        FlagshipPage.LYRICS -> 1f
        FlagshipPage.QUEUE -> 0f
        FlagshipPage.NONE -> flipAnim.value.coerceIn(0f, 1f)
    }
    val queueOnTop = queueFrontness >= 0.5f

    val order = if (queueOnTop) listOf(0, 1) else listOf(1, 0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlagshipCardHeight + FlagshipPeekOffset)
            .pointerInput(activePage) {
                if (activePage != FlagshipPage.NONE) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var claimed = false
                    var travelled = 0f
                    var dragAccum = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        val dy = change.positionChange().y
                        travelled += dy

                        if (!claimed) {
                            if (travelled < -slop) {
                                claimed = true
                                dragAccum = 0f
                            } else if (travelled > slop) {
                                break
                            }
                        }

                        if (claimed) {
                            change.consume()
                            dragAccum += -dy
                            val frac = (abs(dragAccum) / flipDistancePx).coerceIn(0f, 1f)
                            val target = if (frontIndex == 0) frac else 1f - frac
                            scope.launch { flipAnim.snapTo(target) }
                        }
                    }

                    if (claimed) {
                        if (abs(dragAccum) >= flipDistancePx * 0.4f) {
                            frontIndex = 1 - frontIndex
                        }
                        scope.launch {
                            flipAnim.animateTo(
                                targetValue = frontIndex.toFloat(),
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        for (pageIdx in order) {
            key(pageIdx) {
                DeckCard(
                    isQueue = (pageIdx == 1),
                    frontnessProvider = {
                        if (pageIdx == 1) queueFrontness else 1f - queueFrontness
                    },
                    onOpen = {
                        if (it == 1) onOpenQueue() else onOpenLyrics()
                    },
                    onSelect = {
                        if (activePage == FlagshipPage.NONE) {
                            frontIndex = pageIdx
                            scope.launch {
                                flipAnim.animateTo(
                                    targetValue = pageIdx.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                        } else {
                            if (pageIdx == 1) onOpenQueue() else onOpenLyrics()
                        }
                    },
                    isTop = (pageIdx == (if (queueOnTop) 1 else 0)),
                ) {
                    if (pageIdx == 0) {
                        LyricCardContent(currentLyric)
                    } else {
                        QueueCardContent(nextSongTitle, nextSongArtist, nextArtworkUrl)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DeckCard(
    isQueue: Boolean,
    frontnessProvider: () -> Float,
    onOpen: (Int) -> Unit,
    onSelect: () -> Unit,
    isTop: Boolean,
    content: @Composable () -> Unit,
) {
    val f = frontnessProvider().coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlagshipCardHeight)
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                val scale = mixF(BackCardScale, 1f, f)
                scaleX = scale
                scaleY = scale
                translationY = -mixF(FlagshipPeekOffset.toPx(), 0f, f)
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .drawBehind {
                val inset = mixF(FlagshipPeekInset.toPx(), 0f, f)
                val corner = FlagshipCardCorner.toPx()
                val bottom = size.height + BottomBleed.toPx()

                clipRect(bottom = size.height) {
                    val topLeft = Offset(inset, 0f)
                    val cardSize = Size(size.width - inset * 2f, bottom)
                    drawRoundRect(
                        color = Color.White.copy(alpha = mixF(0.02f, 0.04f, f)),
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = CornerRadius(corner, corner),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = mixF(0.06f, 0.12f, f)),
                        topLeft = topLeft,
                        size = cardSize,
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = 1.35.dp.toPx()),
                    )
                }
            }
            .clip(RoundedCornerShape(topStart = FlagshipCardCorner, topEnd = FlagshipCardCorner))
            .clickable {
                if (isTop) {
                    onOpen(if (isQueue) 1 else 0)
                } else {
                    onSelect()
                }
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (f * f * (3f - 2f * f)).coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            content()
        }
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
            text = lyric?.takeIf { it.isNotBlank() } ?: "Lyrics appear here",
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
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.90f),
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
