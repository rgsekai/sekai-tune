package moe.rgsekai.sekaitune.ui.component

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import moe.rgsekai.sekaitune.LocalPlayerConnection
import androidx.compose.ui.unit.sp
import moe.rgsekai.sekaitune.R
import moe.rgsekai.sekaitune.constants.LyricsBoldness
import moe.rgsekai.sekaitune.constants.LyricsBoldnessKey
import moe.rgsekai.sekaitune.constants.LyricsClickKey
import moe.rgsekai.sekaitune.constants.LyricsLineBlurKey
import moe.rgsekai.sekaitune.constants.LyricsLineSpacingKey
import moe.rgsekai.sekaitune.constants.LyricsMotionStyle
import moe.rgsekai.sekaitune.constants.LyricsScrollKey
import moe.rgsekai.sekaitune.constants.LyricsTextSizeKey
import moe.rgsekai.sekaitune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.LyricsUtils
import moe.rgsekai.sekaitune.ui.component.shimmer.ShimmerHost
import moe.rgsekai.sekaitune.ui.component.shimmer.TextPlaceholder
import moe.rgsekai.sekaitune.ui.player.LyricsAlignment
import moe.rgsekai.sekaitune.ui.player.LyricsGlowLine
import moe.rgsekai.sekaitune.ui.player.LyricsHeaderArtX
import moe.rgsekai.sekaitune.ui.player.LyricsInterludeDots
import moe.rgsekai.sekaitune.ui.player.LyricsLineState
import moe.rgsekai.sekaitune.ui.player.LyricsRecenterDurationMs
import moe.rgsekai.sekaitune.ui.player.LyricsStagger
import moe.rgsekai.sekaitune.ui.player.MinInterludeGapMs
import moe.rgsekai.sekaitune.ui.player.PlaybackClock
import moe.rgsekai.sekaitune.ui.player.RubyPlacement
import moe.rgsekai.sekaitune.ui.player.recenterEasingFor
import moe.rgsekai.sekaitune.ui.theme.rememberLyricsFontFamily
import moe.rgsekai.sekaitune.utils.rememberEnumPreference
import moe.rgsekai.sekaitune.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ActiveLineViewportFraction = 0.055f
private val MinPastLineVisibleHeight = 72.dp
private const val IdleRecenterDelayMs = 3_500L
private const val TopFadeFraction = 0.10f
private const val BottomFadeStart = 0.62f
private const val NoInterlude = -2

private class LyricsListPadding : PaddingValues {
    var heightPx = 0
    var density = 1f

    private fun listHeight() = (heightPx / density).dp

    override fun calculateTopPadding() = maxOf(listHeight() * ActiveLineViewportFraction, MinPastLineVisibleHeight)
    override fun calculateBottomPadding() = listHeight() * 0.55f
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp
}

internal data class LineLook(val state: LyricsLineState, val blurStage: Int)

internal fun lineLook(index: Int, current: Int, anchor: Int, unsynced: Boolean, interlude: Boolean): LineLook {
    if (unsynced) return LineLook(LyricsLineState.ACTIVE, 0)
    val distance = when {
        current < 0 -> index - current
        index < anchor -> index - anchor
        index > current -> index - current
        else -> 0
    }
    val state = when {
        current >= 0 && index in anchor..current ->
            if (interlude) LyricsLineState.PAST else LyricsLineState.ACTIVE
        index == current + 1 -> LyricsLineState.UPCOMING
        index < current -> LyricsLineState.PAST
        else -> LyricsLineState.DEFAULT
    }
    val blurStage = when (abs(distance)) {
        0 -> 0
        1 -> 1
        else -> 2
    }
    return LineLook(state, blurStage)
}

internal sealed interface LyricRow {
    val key: Long

    data class Line(val index: Int, val entry: LyricsEntry) : LyricRow {
        override val key get() = index.toLong()
    }

    data class Interlude(val afterIndex: Int, val startMs: Long, val endMs: Long) : LyricRow {
        override val key get() = -1_000_000L - afterIndex
    }
}

private fun lineEndMs(lines: List<LyricsEntry>, index: Int): Long {
    val line = lines.getOrNull(index) ?: return 0L
    val lastWord = line.words?.lastOrNull()
    if (lastWord != null) return (lastWord.endTime * 1000).toLong()
    val next = lines.getOrNull(index + 1)
    if (next != null) return next.time
    return line.time + line.durationMs.coerceAtLeast(3000L)
}

internal fun buildLyricRows(lines: List<LyricsEntry>): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = ArrayList<LyricRow>(lines.size + 4)
    if (lines.first().time >= MinInterludeGapMs) {
        rows.add(LyricRow.Interlude(afterIndex = -1, startMs = 0L, endMs = lines.first().time))
    }
    for (index in lines.indices) {
        rows.add(LyricRow.Line(index, lines[index]))
        val next = lines.getOrNull(index + 1) ?: continue
        val gapStart = lineEndMs(lines, index)
        if (next.time - gapStart >= MinInterludeGapMs) {
            rows.add(LyricRow.Interlude(afterIndex = index, startMs = gapStart, endMs = next.time))
        }
    }
    return rows
}

internal fun lyricAnchorIndex(lines: List<LyricsEntry>, positionMs: Long): Int {
    var i = LyricsUtils.findCurrentLineIndex(lines, positionMs)
    if (i <= 0 || i >= lines.size) return i
    while (i > 0) {
        val previous = i - 1
        val endMs = lines[previous].words
            ?.lastOrNull()
            ?.let { (it.endTime * 1000).toLong() }
            ?: return i
        if (endMs <= positionMs) return i
        i = previous
    }
    return i
}

internal fun activeInterludeOf(rows: List<LyricRow>, positionMs: Long): Int =
    rows.firstOrNull {
        it is LyricRow.Interlude && positionMs >= it.startMs &&
            positionMs + 300L < it.endMs
    }?.let { (it as LyricRow.Interlude).afterIndex } ?: NoInterlude

internal fun alignmentFor(entry: LyricsEntry, useAgentAlignment: Boolean, mirrored: Boolean = false): LyricsAlignment {
    val side = when {
        !useAgentAlignment -> LyricsAlignment.START
        entry.isBackground -> LyricsAlignment.CENTER
        entry.agent == "v2" -> LyricsAlignment.END
        entry.agent == "v1000" -> LyricsAlignment.CENTER
        else -> LyricsAlignment.START
    }
    if (!mirrored) return side
    return when (side) {
        LyricsAlignment.START -> LyricsAlignment.END
        LyricsAlignment.END -> LyricsAlignment.START
        LyricsAlignment.CENTER -> LyricsAlignment.CENTER
    }
}

internal fun usesAgentAlignment(lines: List<LyricsEntry>): Boolean =
    lines.filter { !it.isBackground }
        .map { it.agent ?: "v1" }
        .distinct()
        .size > 1

internal fun lyricsReadRtl(lines: List<LyricsEntry>): Boolean {
    var rtl = 0
    var ltr = 0
    for (line in lines) {
        for (ch in line.text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> { rtl++; break }
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> { ltr++; break }
            }
        }
    }
    return rtl > ltr
}

@Composable
internal fun rememberLyricsMirrored(lines: List<LyricsEntry>): Boolean {
    val appRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lyricsRtl = remember(lines) { lyricsReadRtl(lines) }
    return lyricsRtl != appRtl
}

@Composable
fun LyricsStyleView(
    motionStyle: LyricsMotionStyle,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val density = LocalDensity.current

    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val rawLyrics = currentLyrics?.lyrics

    val (lyricsClick) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsScroll) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (lyricsLineBlurPreference) = rememberPreference(LyricsLineBlurKey, defaultValue = true)
    val (lyricsTextSize) = rememberPreference(LyricsTextSizeKey, defaultValue = 36f)
    val (lyricsLineSpacing) = rememberPreference(LyricsLineSpacingKey, defaultValue = 1.3f)
    val (lyricsBoldness) = rememberEnumPreference(LyricsBoldnessKey, defaultValue = LyricsBoldness.BOLD)
    val lyricsFontFamily = rememberLyricsFontFamily()

    val lyricsFontSize = lyricsTextSize.sp
    val lyricsLineHeight = (lyricsTextSize * lyricsLineSpacing).sp
    val lyricsFontWeight = lyricsBoldness.toFontWeight()

    val parsedLyrics = remember(rawLyrics) {
        if (rawLyrics.isNullOrBlank() || rawLyrics == LYRICS_NOT_FOUND) {
            emptyList<LyricsEntry>() to false
        } else {
            val timed = when {
                LyricsUtils.isTtml(rawLyrics) -> LyricsUtils.parseTtml(rawLyrics)
                LyricsUtils.isLineSyncedLrc(rawLyrics) -> LyricsUtils.parseLyrics(rawLyrics)
                else -> emptyList()
            }
            when {
                timed.isEmpty() -> {
                    val plain = rawLyrics.lines()
                        .filter { it.isNotBlank() }
                        .map { LyricsEntry(time = -1L, text = it.trim()) }
                    plain to true
                }
                timed.all { it.time < 0 } -> timed to true
                else -> timed to false
            }
        }
    }
    val lines = parsedLyrics.first
    val unsynced = parsedLyrics.second

    var playbackPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player, unsynced, lyricsSyncOffset) {
        while (isActive) {
            val sliderPos = sliderPositionProvider()
            val pos = sliderPos ?: (player.currentPosition + lyricsSyncOffset).coerceAtLeast(0L)
            playbackPositionMs = pos
            delay(16L)
        }
    }

    val positionProvider: () -> Long = {
        sliderPositionProvider() ?: (player.currentPosition + lyricsSyncOffset).coerceAtLeast(0L)
    }

    val currentIndex by remember(lines, unsynced) {
        derivedStateOf {
            if (unsynced) -1 else LyricsUtils.findCurrentLineIndex(lines, playbackPositionMs)
        }
    }

    val anchorIndex by remember(lines, unsynced) {
        derivedStateOf {
            if (unsynced) -1 else lyricAnchorIndex(lines, playbackPositionMs)
        }
    }

    val rows = remember(lines) { buildLyricRows(lines) }

    val rowOfLine = remember(rows, lines) {
        IntArray(lines.size).also { map ->
            rows.forEachIndexed { rowIndex, row -> if (row is LyricRow.Line) map[row.index] = rowIndex }
        }
    }

    val cascade = motionStyle == LyricsMotionStyle.MOTION

    val initialRow = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
        val currentAnchor = if (unsynced) -1 else lyricAnchorIndex(lines, (player.currentPosition + lyricsSyncOffset).coerceAtLeast(0L))
        rowOfLine.getOrNull(currentAnchor.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) ?: 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialRow)
    var followPlayback by remember { mutableStateOf(true) }

    val stagger = remember(rows) { LyricsStagger(rows.size + 1) }
    LaunchedEffect(stagger) { stagger.run() }

    var isAutoScrolling by remember { mutableStateOf(false) }

    val userScrolling by remember {
        derivedStateOf { listState.isScrollInProgress && !isAutoScrolling }
    }

    val suppressEffects = userScrolling

    LaunchedEffect(userScrolling) {
        if (userScrolling) followPlayback = false
    }

    LaunchedEffect(userScrolling, followPlayback) {
        if (!followPlayback && !userScrolling) {
            delay(IdleRecenterDelayMs)
            followPlayback = true
        }
    }

    // Auto-scroll logic with cascade spring stagger
    LaunchedEffect(followPlayback, lines, motionStyle, lyricsScroll) {
        if (!followPlayback || !lyricsScroll) return@LaunchedEffect
        snapshotFlow { anchorIndex }.collectLatest { target ->
            if (target !in lines.indices) return@collectLatest
            val targetRow = rowOfLine[target]

            val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
                .first { it > 0 }

            val minOffsetPx = with(density) { MinPastLineVisibleHeight.toPx() }
            val restingOffset = maxOf(viewportHeight * ActiveLineViewportFraction, minOffsetPx).roundToInt()

            try {
                isAutoScrolling = true

                val layoutInfo = listState.layoutInfo
                val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetRow }
                if (visible != null) {
                    val current = visible.offset - layoutInfo.viewportStartOffset
                    val collapsing = layoutInfo.visibleItemsInfo
                        .filter { it.index < targetRow && rows.getOrNull(it.index) is LyricRow.Interlude }
                        .sumOf { it.size }
                    val distance = (current - restingOffset - collapsing).toFloat()

                    stagger.anchorRow = targetRow
                    listState.scroll {
                        var previous = 0f
                        animate(
                            initialValue = 0f,
                            targetValue = distance,
                            animationSpec = tween(durationMillis = LyricsRecenterDurationMs, easing = recenterEasingFor(motionStyle)),
                        ) { value, _ ->
                            val consumed = scrollBy(value - previous)
                            previous += consumed
                            if (cascade && consumed != 0f) {
                                for (item in listState.layoutInfo.visibleItemsInfo) {
                                    if (item.index > targetRow) stagger.holdBack(item.index, consumed)
                                }
                            }
                        }
                    }
                } else {
                    listState.animateScrollToItem(targetRow, 0)
                }
            } finally {
                isAutoScrolling = false
            }
        }
    }

    val activeInterlude by remember(lines, rows) {
        derivedStateOf { activeInterludeOf(rows, playbackPositionMs) }
    }

    val useAgentAlignment = remember(lines) { usesAgentAlignment(lines) }
    val mirrored = rememberLyricsMirrored(lines)

    val accent = textColorOverride ?: MaterialTheme.colorScheme.primary
    val listPadding = remember { LyricsListPadding() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                listPadding.heightPx = constraints.maxHeight
                listPadding.density = this.density
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
    ) {
        if (lines.isEmpty()) {
            if (currentLyrics == null) {
                ShimmerHost(modifier = Modifier.align(Alignment.Center)) {
                    TextPlaceholder()
                }
            } else {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = LyricsHeaderArtX)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = listPadding,
                userScrollEnabled = true,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        val fade = Brush.verticalGradient(
                            0f to Color.Transparent,
                            TopFadeFraction to Color.Black,
                            BottomFadeStart to Color.Black,
                            1f to Color.Transparent,
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = fade, blendMode = BlendMode.DstIn)
                        }
                    },
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> row.key },
                    contentType = { _, row -> if (row is LyricRow.Interlude) "interlude" else "lyric" },
                ) { listIndex, row ->
                    when (row) {
                        is LyricRow.Interlude -> {
                            LyricsInterludeDots(
                                modifier = Modifier.graphicsLayer {
                                    translationY = stagger.lagOf(listIndex)
                                },
                                startMs = row.startMs,
                                endMs = row.endMs,
                                positionProvider = positionProvider,
                                bloom = true,
                                highBloom = true,
                                visible = activeInterlude == row.afterIndex,
                                collapseEasing = recenterEasingFor(motionStyle),
                                alignment = lines.getOrNull(row.afterIndex + 1)
                                    ?.let { alignmentFor(it, useAgentAlignment, mirrored) }
                                    ?: LyricsAlignment.CENTER,
                            )
                        }

                        is LyricRow.Line -> {
                            val index = row.index
                            val entry = row.entry

                            val look by remember(index, lines, unsynced) {
                                derivedStateOf {
                                    lineLook(index, currentIndex, anchorIndex, unsynced, activeInterlude != NoInterlude)
                                }
                            }
                            val state = look.state
                            val blurStage = if (suppressEffects || !lyricsLineBlurPreference) 0 else look.blurStage
                            val lineAlignment = alignmentFor(entry, useAgentAlignment, mirrored)
                            val translation = remember(entry.providerTranslationText, entry.text) {
                                LyricsUtils.providedTranslationTextForEntry(entry)
                            }

                            LyricsGlowLine(
                                modifier = Modifier.graphicsLayer {
                                    translationY = stagger.lagOf(listIndex)
                                },
                                entry = entry,
                                state = state,
                                blurStage = blurStage,
                                suppressEffects = suppressEffects,
                                positionProvider = positionProvider,
                                accentColor = accent,
                                subLine = entry.providerRomanizedText,
                                ruby = entry.ruby,
                                rubyPlacement = if (!translation.isNullOrBlank()) RubyPlacement.ABOVE else RubyPlacement.BELOW,
                                translation = translation,
                                alignment = lineAlignment,
                                waveEnabled = !suppressEffects,
                                motionStyle = motionStyle,
                                fontSize = lyricsFontSize,
                                lineHeight = lyricsLineHeight,
                                fontFamily = lyricsFontFamily,
                                activeWeight = lyricsFontWeight,
                                highBloom = true,
                                onClick = {
                                    if (lyricsClick && !unsynced && entry.time >= 0) {
                                        player.seekTo(entry.time)
                                        followPlayback = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}


