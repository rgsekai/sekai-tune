package moe.rgsekai.sekaitune.ui.player.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.LocalPlayerConnection
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.extensions.metadata
import moe.rgsekai.sekaitune.ui.component.ItemThumbnail
import moe.rgsekai.sekaitune.ui.component.LyricsEnhanced

import moe.rgsekai.sekaitune.utils.makeTimeString
import kotlin.math.roundToInt

private const val PaneBlurRamp = 0.34f
private const val PaneTintAlpha = 0.85f

private const val HandleRestAlpha = 0.1f
private const val HandleOpenAlpha = 0.5f
private val HandleWidth = 140.dp
private val HandleHeight = 4.dp
private val HandleTop = 16.dp
private val HandleBottom = 10.dp

private val HandleExclusionWidth = 180.dp
private val HandleExclusionHeight = 48.dp

internal fun smooth(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

@Composable
internal fun BoxScope.VideoPaneBackdrop(
    frame: VideoFrame,
    state: VideoModeState,
    tint: State<Color>,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .width(frame.paneWidth)
            .fillMaxHeight()
            .graphicsLayer { alpha = smooth(state.pane.value * 1.4f) }
            .drawBehind {
                val color = tint.value
                drawRect(
                    Brush.horizontalGradient(
                        0f to color.copy(alpha = 0f),
                        PaneBlurRamp to color.copy(alpha = PaneTintAlpha),
                        1f to color.copy(alpha = 0.95f),
                    ),
                )
            },
    )
}

@Composable
internal fun BoxScope.VideoPaneContent(
    frame: VideoFrame,
    state: VideoModeState,
    lyricsSyncOffset: Int,
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .width(frame.paneWidth)
            .fillMaxHeight()
            .padding(top = 36.dp, bottom = 20.dp, end = 16.dp, start = 8.dp)
            .graphicsLayer {
                val p = state.pane.value
                alpha = smooth(p)
                translationY = (1f - p) * 40.dp.toPx()
            },
    ) {
        when (state.paneContent) {
            VideoPane.LYRICS -> {
                LyricsEnhanced(
                    sliderPositionProvider = { null },
                    lyricsSyncOffset = lyricsSyncOffset,
                    textColorOverride = Color.White,
                    lyricsLineBlurOverride = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            VideoPane.QUEUE -> {
                VideoPaneQueueList(onClose = state::closePane)
            }
        }
    }
}

@Composable
private fun VideoPaneQueueList(onClose: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val queueWindows by connection.queueWindows.collectAsState()
    val currentIndex by connection.currentWindowIndex.collectAsState()
    val isPlaying by connection.isPlaying.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in queueWindows.indices) {
            listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = queueWindows,
            key = { index, window -> window.uid },
        ) { index, window ->
            val metadata = window.mediaItem.metadata ?: return@itemsIndexed
            val isActive = index == currentIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { connection.player.seekToDefaultPosition(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                ItemThumbnail(
                    thumbnailUrl = metadata.thumbnailUrl,
                    albumIndex = null,
                    isSelected = false,
                    isActive = isActive,
                    isPlaying = isPlaying && isActive,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata.title,
                        color = if (isActive) Color.White else Color(0xFFECECEC),
                        
                        fontSize = 14.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metadata.artists.joinToString { it.name },
                        color = Color(0xFFAAAAAA),
                        
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = makeTimeString(metadata.duration * 1000L),
                    color = Color(0xFF888888),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.VideoPaneHandle(
    frame: VideoFrame,
    state: VideoModeState,
) {
    val density = LocalDensity.current
    val travel = with(density) { (frame.height - HandleTop - HandleBottom - HandleHeight).toPx() }
    val travelHeight = with(density) { frame.height.toPx() * 0.72f }

    // Direct touch interceptor for the handle area when pane is open, so taps & drags on top handle zone always close/drag pane
    if (state.paneMounted) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(frame.paneWidth)
                .height(56.dp)
                .pointerInput(state, frame) {
                    detectTapGestures {
                        if (state.paneOpen) state.closePane()
                    }
                }
                .pointerInput(state, frame) {
                    var mode = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            state.startPaneDrag()
                            mode = true
                        },
                        onVerticalDrag = { change, dy ->
                            if (mode) {
                                change.consume()
                                state.dragPaneBy(-dy / travelHeight)
                            }
                        },
                        onDragEnd = {
                            if (mode) state.settlePane(0f)
                            mode = false
                        },
                        onDragCancel = {
                            if (mode) state.settlePane(0f)
                            mode = false
                        },
                    )
                },
        )
    }

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = (frame.paneWidth - HandleWidth) / 2, top = HandleTop)
            .offset {
                IntOffset(0, (travel * (1f - state.pane.value)).roundToInt())
            }
            .graphicsLayer {
                alpha = HandleRestAlpha + (HandleOpenAlpha - HandleRestAlpha) * state.pane.value
            }
            .size(HandleWidth, HandleHeight)
            .clip(CircleShape)
            .background(Color.White),
    )
    val closed by remember { derivedStateOf { state.pane.value < 0.001f && !state.paneDragging } }
    if (closed) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (frame.paneWidth - HandleExclusionWidth) / 2)
                .size(HandleExclusionWidth, HandleExclusionHeight)
                .systemGestureExclusion(),
        )
    }
}

internal fun paneHandleZone(frame: VideoFrame, open: Boolean, x: Float, y: Float, density: androidx.compose.ui.unit.Density): Boolean =
    with(density) {
        val left = (frame.width - frame.paneWidth).toPx()
        if (open) return x >= left && y < 56.dp.toPx()
        val center = (frame.width - frame.paneWidth / 2).toPx()
        kotlin.math.abs(x - center) < (HandleExclusionWidth / 2).toPx() && y > (frame.height - HandleExclusionHeight).toPx()
    }

internal fun paneSide(frame: VideoFrame, x: Float, density: androidx.compose.ui.unit.Density): Boolean =
    with(density) { x >= (frame.width - frame.paneWidth).toPx() }

