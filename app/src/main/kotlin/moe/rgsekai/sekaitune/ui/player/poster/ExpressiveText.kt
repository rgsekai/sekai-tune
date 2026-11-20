package moe.rgsekai.sekaitune.ui.player.poster

import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.lyrics.RubyToken
import moe.rgsekai.sekaitune.ui.component.BlurEffectCache
import moe.rgsekai.sekaitune.ui.player.LyricsAlignment
import moe.rgsekai.sekaitune.ui.player.LyricsHeaderArtX
import moe.rgsekai.sekaitune.ui.player.LyricsInterludeDots
import moe.rgsekai.sekaitune.ui.player.MinInterludeGapMs
import moe.rgsekai.sekaitune.ui.player.splitsIntoLetters
import moe.rgsekai.sekaitune.ui.player.sungLength
import moe.rgsekai.sekaitune.ui.theme.LyricsFontFamily
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Playback clock for the poster lyrics animations.
 * Reading positionMs() inside draw/layout blocks re-evaluates every frame without triggering recomposition.
 */
@Stable
class LivePosition internal constructor(private val read: () -> Long, private val tick: () -> Long) {
    private var readAt = Long.MIN_VALUE
    private var value = 0L

    fun positionMs(): Long {
        val now = tick()
        if (now != readAt) {
            readAt = now
            value = read()
        }
        return value
    }

    fun shifted(shiftMs: () -> Long) = LivePosition(read = { read() + shiftMs() }, tick = tick)
}

/**
 * High-performance frame-tick position clock for Compose animations.
 */
@Composable
fun rememberLivePosition(
    positionMsProvider: () -> Long,
    isPlaying: Boolean = true,
): LivePosition {
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            tick.longValue++
            return@LaunchedEffect
        }
        while (isPlaying) {
            withFrameMillis { tick.longValue = it }
        }
    }
    return remember(positionMsProvider) {
        LivePosition(read = positionMsProvider, tick = { tick.longValue })
    }
}

/** Big type with no padding above or below glyphs, so stacked lines sit tight like a poster. */
internal fun posterType(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(
    fontFamily = LyricsFontFamily,
    fontSize = size,
    lineHeight = size * 0.96f,
    fontWeight = weight,
    letterSpacing = (-0.035).em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    textMotion = TextMotion.Animated,
)

/** Whether [text] reads right to left (Arabic, Hebrew...), judged by its first strong directional char. */
internal fun isRtlText(text: CharSequence): Boolean =
    androidx.core.text.TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR.isRtl(text, 0, text.length)

/**
 * How big each word is set: one prominent word (three letters or more) is shouted larger,
 * and very long words step down so they fit comfortably.
 */
internal fun wordScales(words: List<String>): FloatArray {
    val emphasised = words.withIndex()
        .filter { it.value.length >= 3 }
        .minByOrNull { it.value.length }?.index ?: -1
    return FloatArray(words.size) { i ->
        val length = words[i].length
        when {
            i == emphasised && words.size > 1 -> 1.50f
            length >= 11 -> 0.74f
            length >= 9 -> 0.86f
            else -> 1f
        }
    }
}

/**
 * Expressive song title displayed like an art poster with words rising into place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpressiveTitle(
    text: String,
    baseSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val words = remember(text) { text.split(' ').filter { it.isNotBlank() } }
    val scales = remember(words) { wordScales(words) }
    val rtl = remember(text) { isRtlText(text) }
    val enter = remember(text) { Animatable(0f) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val fontFamilyResolver = LocalFontFamilyResolver.current
        val layoutDirection = LocalLayoutDirection.current
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        val fitKey = TitleFitKey(text, baseSize, maxWidth, maxHeight, density.density, density.fontScale)
        val fit = remember(text) { mutableStateOf<TextUnit?>(TitleFits.get(fitKey)) }
        LaunchedEffect(fitKey) {
            fit.value = TitleFits.get(fitKey) ?: withContext(Dispatchers.Default) {
                fitTitle(TextMeasurer(fontFamilyResolver, density, layoutDirection, 16), words, scales, text.length, baseSize, maxWidth, maxHeight, density)
            }.also { TitleFits.put(fitKey, it) }
        }
        val fitted = fit.value ?: return@BoxWithConstraints
        LaunchedEffect(text) { enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 180f)) }
        CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(fitted.value.dp * TitleSpacing),
                itemVerticalAlignment = Alignment.Bottom,
            ) {
                words.forEachIndexed { index, word ->
                    Text(
                        text = word,
                        color = color,
                        style = titleWordStyle(fitted, scales[index]),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            val delay = index * 0.09f
                            val p = ((enter.value - delay) / (1f - delay).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
                            translationY = (1f - p) * 30.dp.toPx()
                            alpha = p
                        },
                    )
                }
            }
        }
    }
}

private const val TitleSpacing = 0.24f

private data class TitleFitKey(
    val text: String,
    val baseSize: TextUnit,
    val maxWidth: Int,
    val maxHeight: Int,
    val density: Float,
    val fontScale: Float,
)

private val TitleFits = android.util.LruCache<TitleFitKey, TextUnit>(48)

private fun titleWordStyle(size: TextUnit, scale: Float) =
    posterType(size * scale, if (scale > 1f) FontWeight.Black else FontWeight.Bold)

private fun fitTitle(
    measurer: TextMeasurer,
    words: List<String>,
    scales: FloatArray,
    length: Int,
    baseSize: TextUnit,
    maxWidth: Int,
    maxHeight: Int,
    density: androidx.compose.ui.unit.Density,
): TextUnit {
    var size = baseSize * kotlin.math.sqrt((14f / length.coerceAtLeast(1)).coerceIn(0.52f, 1f))
    repeat(8) {
        if (titleFits(measurer, words, scales, size, maxWidth, maxHeight, density)) return size
        size *= 0.9f
    }
    return size
}

private fun titleFits(
    measurer: TextMeasurer,
    words: List<String>,
    scales: FloatArray,
    size: TextUnit,
    maxWidth: Int,
    maxHeight: Int,
    density: androidx.compose.ui.unit.Density,
): Boolean {
    val spacing = with(density) { (size.value * TitleSpacing).dp.toPx() }
    var rowWidth = 0f
    var rowHeight = 0f
    var total = 0f
    words.forEachIndexed { i, word ->
        val layout = measurer.measure(word, titleWordStyle(size, scales[i]), maxLines = 1, softWrap = false, constraints = Constraints())
        val w = layout.size.width.toFloat()
        if (w > maxWidth) return false
        if (rowWidth > 0f && rowWidth + spacing + w > maxWidth) {
            total += rowHeight
            rowWidth = 0f
            rowHeight = 0f
        }
        rowWidth += (if (rowWidth > 0f) spacing else 0f) + w
        rowHeight = max(rowHeight, layout.size.height.toFloat())
    }
    return total + rowHeight <= maxHeight
}

// ---- Lyrics Data Structures -----------------------------------------------------------------

/** One word of a line, with when it's sung. */
private class TimedWord(val text: String, val startMs: Long, val endMs: Long) {
    val sungLength = sungLength(text)
}

/**
 * Reconstructs clean whole words from entry.text so syllables in TTML (e.g. "gon na", "mi ni mart",
 * "eve ry bo dy") are properly assembled into whole words without awkward splitting.
 */
private fun timedWords(entry: LyricsEntry, nextStartMs: Long?): List<TimedWord> {
    val cleanWords = entry.text.split(' ').filter { it.isNotBlank() }
    if (cleanWords.isEmpty()) return emptyList()

    val rawTokens = entry.words?.filter { it.text.isNotBlank() }

    if (rawTokens.isNullOrEmpty()) {
        val lineEnd = (nextStartMs ?: (entry.time + 4_000L)).coerceAtLeast(entry.time + 600L)
        val singing = ((lineEnd - entry.time) * 0.86f).coerceAtMost(cleanWords.size * 900f)
        val weights = cleanWords.map { it.length + 1.5f }
        val total = weights.sum()
        var cursor = entry.time.toFloat()
        return cleanWords.mapIndexed { i, word ->
            val length = singing * weights[i] / total
            val s = cursor.toLong()
            val e = (cursor + length).toLong()
            cursor += length
            TimedWord(word, s, e)
        }
    }

    if (rawTokens.size == cleanWords.size) {
        return cleanWords.mapIndexed { i, word ->
            val rw = rawTokens[i]
            TimedWord(word, (rw.startTime * 1_000).toLong(), (rw.endTime * 1_000).toLong())
        }
    }

    // Merge syllable tokens into whole clean words
    val result = ArrayList<TimedWord>(cleanWords.size)
    var rawIdx = 0
    cleanWords.forEach { word ->
        val cleanWordNorm = word.lowercase().filter { it.isLetterOrDigit() }
        var wordStart = Long.MAX_VALUE
        var wordEnd = Long.MIN_VALUE
        var accumulated = ""

        while (rawIdx < rawTokens.size) {
            val rw = rawTokens[rawIdx]
            val s = (rw.startTime * 1_000).toLong()
            val e = (rw.endTime * 1_000).toLong()
            wordStart = minOf(wordStart, s)
            wordEnd = maxOf(wordEnd, e)
            accumulated += rw.text.lowercase().filter { it.isLetterOrDigit() }
            rawIdx++
            if (cleanWordNorm.isNotEmpty() && accumulated.length >= cleanWordNorm.length) {
                break
            }
            if (rawIdx >= rawTokens.size) {
                break
            }
        }
        if (wordStart == Long.MAX_VALUE) {
            wordStart = entry.time
            wordEnd = entry.time + 500L
        }
        result += TimedWord(word, wordStart, maxOf(wordStart + 120L, wordEnd))
    }
    return result
}

// Animation timing constants
private const val EnterLeadMs = 420L
private const val EnterMs = 560f
private const val EnterStaggerMs = 65f
private const val ExitMs = 460f
private const val ExitStaggerMs = 40f
private const val RiseShare = 0.55f
private const val RiseLeadChars = 0.8f
private const val RiseSpanChars = 3.2f
private const val GapExitDelayMs = 450L
private const val ActiveFadeMs = 450f
private const val GlowAlpha = 0.65f
private const val GlowSoftness = 0.14f
private const val GlowTrailChars = 2.5f
private const val GlowRest = 0.3f
private const val GlowRes = 0.5f
private const val GlowCutoff = 0.04f

private enum class LineSide { LEFT, CENTER, RIGHT }

private fun sideOf(entry: LyricsEntry, duet: Boolean, rtl: Boolean): LineSide {
    if (duet && entry.agent == "v1000") return LineSide.CENTER
    val answering = duet && entry.agent == "v2"
    return if (answering != rtl) LineSide.RIGHT else LineSide.LEFT
}

private fun readsRightToLeft(lines: List<LyricsEntry>): Boolean =
    lines.count { isRtlText(it.text) } * 2 > lines.size

private class WordCell(
    val word: TimedWord,
    val letters: Array<TextLayoutResult>,
    val letterX: FloatArray,
    val width: Float,
    val height: Float,
    val baseline: Float,
    val rest: Float,
    val sizePx: Float,
    val row: Int,
    val charStart: Int,
    val bleed: Float,
    val rtl: Boolean,
) {
    val lift = FloatArray(letters.size)
    val zoom = FloatArray(letters.size)
    val glow = FloatArray(letters.size)
}

private enum class WordStage { Waiting, Live, Done }

private fun letterBounds(word: String, graphemes: java.text.BreakIterator): IntArray {
    if (word.isEmpty() || !splitsIntoLetters(word, 0, word.length)) {
        return intArrayOf(0, word.length)
    }
    graphemes.setText(word)
    val bounds = ArrayList<Int>(word.length + 1)
    var at = graphemes.first()
    while (at != java.text.BreakIterator.DONE) {
        bounds += at
        at = graphemes.next()
    }
    return bounds.toIntArray()
}

private fun visualOrder(words: List<TimedWord>, rows: IntArray): IntArray {
    val order = IntArray(words.size) { it }
    val joined = words.joinToString(" ") { it.text }
    if (!java.text.Bidi.requiresBidi(joined.toCharArray(), 0, joined.length)) return order
    val bidi = java.text.Bidi(joined, java.text.Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT)
    val levels = ByteArray(words.size)
    var offset = 0
    words.forEachIndexed { i, w ->
        levels[i] = bidi.getLevelAt(offset).toByte()
        offset += w.text.length + 1
    }
    val boxed = Array<Any>(words.size) { it }
    var start = 0
    while (start < words.size) {
        var end = start
        while (end < words.size && rows[end] == rows[start]) end++
        java.text.Bidi.reorderVisually(levels, start, boxed, start, end - start)
        start = end
    }
    return IntArray(words.size) { boxed[it] as Int }
}

private class SubCell(val layout: TextLayoutResult, val alpha: Float, val bleed: Float)

private class LineCells(
    val words: List<WordCell>,
    val spacePx: Float,
    val maxWidth: Float,
    val side: LineSide,
    val subs: List<SubCell>,
    val subGapPx: Float,
    val visual: IntArray = IntArray(words.size) { it },
) {
    val chars = words.sumOf { it.letters.size }
    val x = FloatArray(words.size)
    val y = FloatArray(words.size)
    val scale = FloatArray(words.size)
    val subY = FloatArray(subs.size)
    var totalHeight = 0f
        private set
    private var frameT = Long.MIN_VALUE

    private val liveFrom = LongArray(words.size)
    private val liveUntil = LongArray(words.size)

    init {
        var settled = Long.MIN_VALUE
        words.forEachIndexed { i, cell ->
            val w = cell.word
            settled = max(settled, max(w.startMs, w.endMs))
            liveFrom[i] = w.startMs
            liveUntil[i] = maxOf(settled, w.endMs + ActiveFadeMs.toLong(), w.startMs + growSpanOf(w).toLong()) + 1L
        }
    }

    fun stageAt(index: Int, t: Long): WordStage = when {
        t < liveFrom[index] -> WordStage.Waiting
        t >= liveUntil[index] -> WordStage.Done
        else -> WordStage.Live
    }

    fun drawTimeOf(index: Int, stage: WordStage, t: Long): Long = when (stage) {
        WordStage.Waiting -> liveFrom[index] - 1L
        WordStage.Done -> liveUntil[index]
        WordStage.Live -> t
    }

    fun frameAt(t: Long) {
        if (t == frameT) return
        frameT = t
        var i = 0
        var rowTop = 0f
        var bottom = 0f
        while (i < words.size) {
            val row = words[i].row
            var j = i
            var baseline = 0f
            var height = 0f
            var rowWidth = 0f
            while (j < words.size && words[j].row == row) {
                val w = words[j]
                val s = if (w.rest >= 1f) 1f else w.rest + (1f - w.rest) * grow(w.word, t)
                scale[j] = s
                baseline = max(baseline, w.baseline * s)
                height = max(height, w.height * s)
                rowWidth += w.width * s + (if (j > i) spacePx else 0f)
                j++
            }
            var cx = when (side) {
                LineSide.LEFT -> 0f
                LineSide.CENTER -> (maxWidth - rowWidth) / 2f
                LineSide.RIGHT -> maxWidth - rowWidth
            }
            for (p in i until j) {
                val k = visual[p]
                val w = words[k]
                x[k] = cx
                y[k] = rowTop + baseline - w.baseline * scale[k]
                cx += w.width * scale[k] + spacePx
            }
            bottom = rowTop + height
            rowTop += height * 0.98f
            i = j
        }
        var subTop = bottom
        subs.forEachIndexed { k, sub ->
            subTop += subGapPx
            subY[k] = subTop
            subTop += sub.layout.size.height
        }
        totalHeight = subTop
    }

    fun headAt(t: Long): Float {
        for (w in words) {
            val n = w.letters.size
            if (t < w.word.startMs) return w.charStart.toFloat()
            if (t < w.word.endMs) {
                val span = (w.word.endMs - w.word.startMs).coerceAtLeast(1L)
                return w.charStart + n * ((t - w.word.startMs).toFloat() / span)
            }
        }
        return chars.toFloat()
    }
}

private fun grow(word: TimedWord, t: Long): Float = smootherstep((t - word.startMs) / growSpanOf(word))

private fun growSpanOf(word: TimedWord): Float = max(520f, (word.endMs - word.startMs) * 0.75f)

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun smootherstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}

private fun easeOutCubic(x: Float): Float {
    val u = 1f - x.coerceIn(0f, 1f)
    return 1f - u * u * u
}

private fun measureLine(
    measurer: TextMeasurer,
    entry: LyricsEntry,
    nextStartMs: Long?,
    maxWidth: Float,
    maxHeight: Float,
    basePx: Float,
    density: androidx.compose.ui.unit.Density,
    color: Color,
    side: LineSide,
    reading: String?,
    translation: String?,
): LineCells {
    val words = timedWords(entry, nextStartMs)
    val subGap = with(density) { 6.dp.toPx() }
    if (words.isEmpty()) return LineCells(emptyList(), 0f, maxWidth, side, emptyList(), subGap)

    val subPx = (basePx * 0.3f).coerceIn(with(density) { 13.sp.toPx() }, with(density) { 19.sp.toPx() })
    val align = when (side) {
        LineSide.LEFT -> TextAlign.Left
        LineSide.CENTER -> TextAlign.Center
        LineSide.RIGHT -> TextAlign.Right
    }
    val subWidth = maxWidth.roundToInt().coerceAtLeast(1)
    val subs = listOfNotNull(reading?.let { it to 0.74f }, translation?.let { it to 0.56f }).map { (text, alpha) ->
        val style = TextStyle(
            fontFamily = LyricsFontFamily,
            fontSize = with(density) { subPx.toSp() },
            lineHeight = with(density) { (subPx * 1.22f).toSp() },
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02).em,
            color = color,
            textAlign = align,
            textMotion = TextMotion.Animated,
        )
        val layout = measurer.measure(
            text = text,
            style = style,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = subWidth),
        )
        SubCell(layout, alpha, with(density) { 14.dp.toPx() })
    }
    val subsHeight = subs.sumOf { it.layout.size.height } + subs.size * subGap
    val lineHeightBudget = (maxHeight - subsHeight).coerceAtLeast(maxHeight * 0.45f)

    val scales = wordScales(words.map { it.text })
    fun style(i: Int, base: Float) = posterType(with(density) { (base * scales[i]).toSp() }, FontWeight.Bold).copy(color = color)
    var base = basePx
    var attempt = 0
    while (true) {
        val wholes = words.mapIndexed { i, w ->
            measurer.measure(w.text, style(i, base), maxLines = 1, softWrap = false, constraints = Constraints())
        }
        val space = base * 0.26f
        val rows = IntArray(words.size)
        var x = 0f
        var row = 0
        var rowHeight = 0f
        var total = 0f
        var widest = 0f
        wholes.forEachIndexed { i, layout ->
            val ww = layout.size.width.toFloat()
            if (x > 0f && x + ww > maxWidth) {
                total += rowHeight * 0.98f
                rowHeight = 0f
                x = 0f
                row++
            }
            rows[i] = row
            x += ww + space
            rowHeight = max(rowHeight, layout.size.height.toFloat())
            widest = max(widest, ww)
        }
        total += rowHeight
        if ((widest <= maxWidth && total <= lineHeightBudget) || attempt >= 10) {
            var charStart = 0
            val graphemes = java.text.BreakIterator.getCharacterInstance()
            val cells = words.mapIndexed { i, w ->
                val whole = wholes[i]
                val st = style(i, base)
                val bounds = letterBounds(w.text, graphemes)
                val pieces = bounds.size - 1
                val letters = if (pieces == 1) arrayOf(whole) else Array(pieces) { k ->
                    measurer.measure(w.text.substring(bounds[k], bounds[k + 1]), st, maxLines = 1, softWrap = false, constraints = Constraints())
                }
                val letterX = if (pieces == 1) FloatArray(1) else FloatArray(pieces) { k ->
                    whole.getHorizontalPosition(bounds[k], usePrimaryDirection = true)
                }
                val sizePx = base * scales[i]
                WordCell(
                    word = w,
                    letters = letters,
                    letterX = letterX,
                    width = whole.size.width.toFloat(),
                    height = whole.size.height.toFloat(),
                    baseline = whole.firstBaseline,
                    rest = if (scales[i] > 1f) 1f / scales[i] else 1f,
                    sizePx = sizePx,
                    row = rows[i],
                    charStart = charStart,
                    bleed = sizePx * 0.45f + with(density) { 14.dp.toPx() },
                    rtl = pieces == 1 && isRtlText(w.text),
                ).also { charStart += pieces }
            }
            return LineCells(cells, space, maxWidth, side, subs, subGap, visualOrder(words, rows))
        }
        base *= 0.88f
        attempt++
    }
}

private fun enterTimes(lines: List<LyricsEntry>): LongArray = LongArray(lines.size) { i ->
    val start = lines[i].time
    if (i == 0) (if (start < MinInterludeGapMs) 0L else start - EnterLeadMs)
    else (start - EnterLeadMs).coerceAtLeast(lines[i - 1].time)
}

private class Interlude(val startMs: Long, val endMs: Long, val afterIndex: Int, val side: LineSide)

private fun interludesOf(lines: List<LyricsEntry>, ends: LongArray, enterAts: LongArray, duet: Boolean, rtl: Boolean): List<Interlude> {
    if (lines.isEmpty()) return emptyList()
    val gaps = ArrayList<Interlude>()
    if (lines[0].time >= MinInterludeGapMs) {
        gaps += Interlude(300L, enterAts[0], -1, sideOf(lines[0], duet, rtl))
    }
    for (i in 0 until lines.lastIndex) {
        val next = i + 1
        if (lines[next].time - ends[i] >= MinInterludeGapMs) {
            val start = ends[i] + GapExitDelayMs + 250L
            if (enterAts[next] - start > 1_500L) gaps += Interlude(start, enterAts[next], i, sideOf(lines[next], duet, rtl))
        }
    }
    return gaps
}

private fun lastAtOrBefore(times: LongArray, t: Long): Int {
    var lo = 0
    var hi = times.lastIndex
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (times[mid] <= t) {
            found = mid
            lo = mid + 1
        } else hi = mid - 1
    }
    return found
}

private data class LineKey(val index: Int, val reading: String?, val translation: String?)

private class LineCache {
    private val measured = LinkedHashMap<LineKey, LineCells>()
    private val landed = mutableIntStateOf(0)

    fun has(key: LineKey): Boolean = key in measured

    fun cellsFor(key: LineKey): LineCells? {
        landed.intValue
        return measured[key] ?: measured.entries.lastOrNull { it.key.index == key.index }?.value
    }

    fun put(key: LineKey, cells: LineCells, shows: Boolean) {
        measured[key] = cells
        if (shows) landed.intValue++
    }

    fun keepOnly(indices: IntRange) {
        measured.keys.retainAll { it.index in indices }
    }
}

private fun readingOf(roman: String?, ruby: List<RubyToken>?, text: String): String? =
    roman?.takeIf { it.isNotBlank() && it != text }
        ?: ruby?.joinToString(" ") { it.text.trim() }?.takeIf { it.isNotBlank() && it != text }

/**
 * Expressive Canvas Lyrics:
 * - Fluid letters rising in a soft wave as the singing reaches them
 * - Continuous gradient light flowing across letters
 * - Swell and zoom on held notes
 * - GPU-blurred bloom/glow halo on active letters
 * - Words staggered enter with rise and un-blur, and staggered exit flying up and blurring away
 * - Stacked lines strictly separated so upcoming lines NEVER collide or cross over active lines
 */
@Composable
fun ExpressiveLyrics(
    lines: List<LyricsEntry>,
    position: LivePosition,
    color: Color,
    baseSize: TextUnit,
    modifier: Modifier = Modifier,
    dimAlpha: Float = 0.32f,
) {
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val layoutDirection = LocalLayoutDirection.current
    val duet = remember(lines) { lines.map { it.agent ?: "v1" }.distinct().size > 1 }
    val rtl = remember(lines) { readsRightToLeft(lines) }
    val enterAts = remember(lines) { enterTimes(lines) }
    val ends = remember(lines) {
        LongArray(lines.size) { i ->
            timedWords(lines[i], lines.getOrNull(i + 1)?.time).lastOrNull()?.endMs ?: lines[i].time
        }
    }
    val interludes = remember(lines, ends, enterAts, duet, rtl) { interludesOf(lines, ends, enterAts, duet, rtl) }
    val overlaps = remember(lines, ends, enterAts) {
        BooleanArray(lines.size) { i -> i < lines.lastIndex && ends[i] - 100L > enterAts[i + 1] }
    }
    val exitAts = remember(lines, ends, enterAts, interludes, overlaps) {
        Array(lines.size) { index ->
            val next = index + 1
            when {
                next !in lines.indices -> null
                interludes.any { it.afterIndex == index } -> (ends[index] + GapExitDelayMs).coerceAtMost(enterAts[next])
                overlaps[index] -> ends[index] + 150L
                else -> (ends[index] - 80L).coerceIn(enterAts[next], max(enterAts[next], lines[next].time))
            }
        }
    }
    val shown by remember(lines, position) {
        derivedStateOf { lastAtOrBefore(enterAts, position.positionMs()) }
    }
    val first by remember(lines, position, exitAts) {
        derivedStateOf {
            val t = position.positionMs()
            val top = lastAtOrBefore(enterAts, t)
            var firstOn = top
            var j = top - 1
            while (j >= 0 && top - j <= 2) {
                val exit = exitAts[j] ?: break
                val gone = t >= exit + ExitMs + 12 * ExitStaggerMs + 120L
                if (!gone) firstOn = j else break
                j--
            }
            firstOn
        }
    }
    val gap by remember(interludes, position) {
        derivedStateOf {
            val t = position.positionMs()
            interludes.firstOrNull { t >= it.startMs - 150L && t < it.endMs + 700L }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else widthPx
        val basePx = with(density) { baseSize.toPx() }
        val stackGapPx = with(density) { 16.dp.toPx() }
        val cache = remember(lines, widthPx, heightPx, basePx, color) { LineCache() }

        suspend fun measure(key: LineKey): LineCells = withContext(Dispatchers.Default) {
            val index = key.index
            val budget = heightPx * 0.72f
            measureLine(
                TextMeasurer(fontFamilyResolver, density, layoutDirection, 128), lines[index], lines.getOrNull(index + 1)?.time,
                widthPx, budget, basePx, density, color, sideOf(lines[index], duet, rtl), key.reading, key.translation,
            )
        }

        LaunchedEffect(shown, cache) {
            withFrameMillis { }
            cache.keepOnly((first - 1)..(shown + 2))
            val next = shown + 1
            if (next in lines.indices) {
                val e = lines[next]
                val reading = readingOf(e.romanizedTextFlow.value, e.rubyFlow.value, e.text)
                val translation = e.translatedTextFlow.value?.takeIf { it.isNotBlank() && it != e.text }
                val key = LineKey(next, reading, translation)
                if (!cache.has(key)) cache.put(key, measure(key), shows = false)
            }
        }

        gap?.let { interlude ->
            androidx.compose.runtime.key(interlude.startMs) {
                InterludeRow(interlude, position, baseSize)
            }
        }

        if (shown in lines.indices) {
            // Lines strictly stack vertically: every line below an earlier visible line is pushed down
            // by that line's total measured height + gap. As an earlier line exits, it flies up and
            // the lines below glide smoothly up into place. ZERO collisions or cross-overs!
            val above = ArrayList<LineCells>()
            val aboveExits = ArrayList<Long>()
            for (index in first.coerceAtLeast(0)..shown) {
                androidx.compose.runtime.key(index) {
                    val entry = lines[index]
                    val roman by entry.romanizedTextFlow.collectAsState()
                    val ruby by entry.rubyFlow.collectAsState()
                    val translated by entry.translatedTextFlow.collectAsState()
                    val key = LineKey(
                        index,
                        readingOf(roman, ruby, entry.text),
                        translated?.takeIf { it.isNotBlank() && it != entry.text },
                    )
                    if (!cache.has(key)) {
                        LaunchedEffect(key, cache) { cache.put(key, measure(key), shows = true) }
                    }
                    val cells = cache.cellsFor(key)
                    if (cells != null) {
                        val pushers = above.toList()
                        val pusherExits = aboveExits.toList()
                        LyricLine(
                            cells = cells,
                            enterAt = enterAts[index],
                            exitAt = exitAts[index],
                            position = position,
                            color = color,
                            dimAlpha = dimAlpha,
                            offsetY = { t ->
                                var y = 0f
                                for (k in pushers.indices) {
                                    val holder = pushers[k]
                                    holder.frameAt(t)
                                    val exitT = pusherExits[k]
                                    val hold = if (t <= exitT) 1f else (1f - smootherstep((t - exitT) / ExitMs)).coerceIn(0f, 1f)
                                    y += (holder.totalHeight + stackGapPx) * hold
                                }
                                y
                            },
                        )
                        val exit = exitAts[index] ?: (ends[index] + GapExitDelayMs)
                        above += cells
                        aboveExits += exit
                    }
                }
            }
        }
    }
}

@Composable
private fun InterludeRow(gap: Interlude, position: LivePosition, baseSize: TextUnit) {
    val visible by remember(gap, position) {
        derivedStateOf {
            val t = position.positionMs()
            t >= gap.startMs && t < gap.endMs
        }
    }
    val provider = remember(position) { { position.positionMs() } }
    val scale = (baseSize.value / 30f).coerceIn(1f, 1.8f)
    val origin = when (gap.side) {
        LineSide.LEFT -> 0f
        LineSide.CENTER -> 0.5f
        LineSide.RIGHT -> 1f
    }
    LyricsInterludeDots(
        startMs = gap.startMs,
        endMs = gap.endMs,
        positionProvider = provider,
        visible = visible,
        alignment = when (gap.side) {
            LineSide.LEFT -> LyricsAlignment.START
            LineSide.CENTER -> LyricsAlignment.CENTER
            LineSide.RIGHT -> LyricsAlignment.END
        },
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(origin, 0f)
            }
            .layout { measurable, constraints ->
                val pad = LyricsHeaderArtX.roundToPx()
                val wide = constraints.maxWidth + pad * 2
                val p = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
                layout(constraints.maxWidth, p.height) { p.place(-pad, 0) }
            },
    )
}

@Composable
private fun LyricLine(
    cells: LineCells,
    enterAt: Long,
    exitAt: Long?,
    position: LivePosition,
    color: Color,
    dimAlpha: Float,
    offsetY: (Long) -> Float = { 0f },
) {
    val density = LocalDensity.current
    val blurIn = with(density) { 10.dp.toPx() }
    val blurOut = with(density) { 12.dp.toPx() }
    val dim = color.copy(alpha = color.alpha * dimAlpha)
    val gpuGlow = android.os.Build.VERSION.SDK_INT >= 31

    fun enterOf(order: Int, t: Long) = easeOutCubic((t - enterAt - order * EnterStaggerMs) / EnterMs)
    fun exitOf(order: Int, t: Long) = if (exitAt == null) 0f else smoothstep((t - exitAt - order * ExitStaggerMs) / ExitMs)

    fun blurOf(order: Int, t: Long): Float =
        if (!gpuGlow) 0f
        else (1f - enterOf(order, t)) * blurIn + exitOf(order, t) * blurOut

    fun GraphicsLayerScope.motion(order: Int, t: Long, liftPx: Float, x: Float, y: Float, bleed: Float, mayGlow: Boolean) {
        val e = enterOf(order, t)
        val out = exitOf(order, t)
        translationX = x - bleed
        translationY = y + offsetY(t) - bleed + (1f - e) * liftPx * 0.55f - out * out * (liftPx + 24.dp.toPx())
        alpha = (e * (1f - out)).coerceIn(0f, 1f)
        val zoom = (0.94f + 0.06f * e) * (1f + 0.08f * out)
        scaleX = zoom
        scaleY = zoom
        val pivotX = when (cells.side) {
            LineSide.LEFT -> 0f
            LineSide.CENTER -> cells.maxWidth / 2f
            LineSide.RIGHT -> cells.maxWidth
        }
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin((pivotX - (x - bleed)) / size.width, (cells.totalHeight / 2f - (y - bleed)) / size.height)
        val blurred = blurOf(order, t) > BlurShownPx
        compositingStrategy = if (blurred || (!mayGlow && alpha > 0.95f)) CompositingStrategy.ModulateAlpha else CompositingStrategy.Auto
    }

    cells.words.forEachIndexed { w, cell ->
        androidx.compose.runtime.key(w) {
            val stage = remember(cells, w, position) { derivedStateOf { cells.stageAt(w, position.positionMs()) } }
            val blurring = remember(cells, w, position) { derivedStateOf { blurOf(w, position.positionMs()) > BlurShownPx } }
            val glowLayer = if (gpuGlow) rememberGraphicsLayer() else null
            val blurLayer = if (gpuGlow) rememberGraphicsLayer() else null
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = measurable.measure(
                            Constraints.fixed(
                                (cell.width + cell.bleed * 2f).roundToInt().coerceAtLeast(1),
                                (cell.height + cell.bleed * 2f).roundToInt().coerceAtLeast(1),
                            )
                        )
                        layout(0, 0) {
                            p.placeWithLayer(0, 0) {
                                val t = position.positionMs()
                                cells.frameAt(t)
                                motion(w, t, cell.sizePx, cells.x[w], cells.y[w], cell.bleed, mayGlow = stage.value == WordStage.Live)
                            }
                        }
                    }
                    .drawBehind {
                        val now = stage.value
                        val moving = blurring.value
                        val clock = if (now == WordStage.Live || moving) position.positionMs() else 0L
                        val t = cells.drawTimeOf(w, now, clock)
                        cells.frameAt(t)
                        if (moving && blurLayer != null) {
                            drawBlurred(blurLayer, blurOf(w, clock)) { drawWord(cell, cells.scale[w], cells.headAt(t), t, color, dim, glowLayer) }
                        } else {
                            drawWord(cell, cells.scale[w], cells.headAt(t), t, color, dim, glowLayer)
                        }
                    },
            )
        }
    }
    cells.subs.forEachIndexed { k, sub ->
        val order = cells.words.size + k
        val blurring = remember(cells, k, position) { derivedStateOf { blurOf(order, position.positionMs()) > BlurShownPx } }
        val blurLayer = if (gpuGlow) rememberGraphicsLayer() else null
        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier
                .layout { measurable, _ ->
                    val p = measurable.measure(
                        Constraints.fixed(
                            (sub.layout.size.width + sub.bleed * 2f).roundToInt().coerceAtLeast(1),
                            (sub.layout.size.height + sub.bleed * 2f).roundToInt().coerceAtLeast(1),
                        )
                    )
                    layout(0, 0) {
                        p.placeWithLayer(0, 0) {
                            val t = position.positionMs()
                            cells.frameAt(t)
                            motion(order, t, sub.layout.size.height * 1.6f, 0f, cells.subY[k], sub.bleed, mayGlow = false)
                        }
                    }
                }
                .drawBehind {
                    val text: DrawScope.() -> Unit = {
                        drawText(sub.layout, color = color.copy(alpha = color.alpha * sub.alpha), topLeft = Offset(sub.bleed, sub.bleed), shadow = Shadow.None)
                    }
                    if (blurring.value && blurLayer != null) drawBlurred(blurLayer, blurOf(order, position.positionMs()), text) else text()
                },
        )
    }
}

private const val BlurShownPx = 0.5f

private fun DrawScope.drawBlurred(layer: GraphicsLayer, radius: Float, content: DrawScope.() -> Unit) {
    val res = if (radius >= 12f) 0.5f else 1f
    layer.renderEffect = BlurEffectCache.get(radius * res, Shader.TileMode.DECAL)
    val box = androidx.compose.ui.unit.IntSize((size.width * res).roundToInt().coerceAtLeast(1), (size.height * res).roundToInt().coerceAtLeast(1))
    layer.record(box) { scale(res, res, pivot = Offset.Zero) { content() } }
    scale(1f / res, 1f / res, pivot = Offset.Zero) { drawLayer(layer) }
}

private fun DrawScope.drawWord(cell: WordCell, scale: Float, head: Float, t: Long, color: Color, dim: Color, glowLayer: GraphicsLayer?) {
    val word = cell.word
    val n = cell.letters.size
    if (n == 0) return
    val k = (head - cell.charStart).coerceIn(0f, n.toFloat())
    val through = k / n
    val wordRise = smootherstep(through / 0.85f)
    val soft = cell.width / n * 1.15f
    val frontAtHead = cell.width * through
    val front = frontAtHead + soft * through
    val litFrom = front - soft
    val span = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
    val hold = smoothstep((span / word.sungLength - 140f) / 200f)
    val within = ((t - word.startMs) / span).coerceIn(0f, 1f)
    val swellEnvelope = hold * smootherstep(within / 0.2f) * smootherstep((1f - within) / 0.12f)
    val active = smootherstep(through / 0.15f) * (1f - smootherstep((t - word.endMs) / ActiveFadeMs))

    var glowing = false
    for (i in 0 until n) {
        val lx = cell.letterX[i]
        val lw = cell.letters[i].size.width.toFloat()
        val c = i.toFloat()
        val letterRise = if (k >= n) 1f else smootherstep((k - c + RiseLeadChars) / RiseSpanChars)
        val rise = wordRise + (letterRise - wordRise) * RiseShare
        val d = k - (c + 0.5f)
        val wave = smootherstep((d + 1.6f) / 1.6f) * (1f - smootherstep(d / 3.2f)) * active
        val swell = swellEnvelope * smootherstep((k - c + 0.35f) / 1.4f)
        cell.lift[i] = (rise * 0.07f + wave * 0.025f + swell * 0.02f) * cell.sizePx
        cell.zoom[i] = 1f + wave * 0.035f + swell * 0.07f
        val lit = ((front - (lx + lw / 2f)) / soft).coerceIn(0f, 1f)
        val since = k - (c + 0.5f)
        val trail = if (since <= 0f) 1f else 1f - smootherstep(since / GlowTrailChars)
        val glow = if (lx >= front) 0f else (active * lit * (GlowRest + (1f - GlowRest) * trail) + swell * 0.6f).coerceIn(0f, 1f)
        cell.glow[i] = if (glow > GlowCutoff) glow else 0f
        if (glow > GlowCutoff) glowing = true
    }
    if (glowing && glowLayer != null) {
        glowLayer.renderEffect = BlurEffectCache.get(cell.sizePx * GlowSoftness * GlowRes, Shader.TileMode.DECAL)
        val half = androidx.compose.ui.unit.IntSize((size.width * GlowRes).roundToInt().coerceAtLeast(1), (size.height * GlowRes).roundToInt().coerceAtLeast(1))
        glowLayer.record(half) {
            scale(GlowRes, GlowRes, pivot = Offset.Zero) { drawLetters(cell, scale, litFrom, front, color, dim, LetterPass.Glow) }
        }
        scale(1f / GlowRes, 1f / GlowRes, pivot = Offset.Zero) { drawLayer(glowLayer) }
    }
    drawLetters(cell, scale, litFrom, front, color, dim, if (glowLayer == null) LetterPass.Shadowed else LetterPass.Plain)
}

private enum class LetterPass {
    Plain,
    Shadowed,
    Glow,
}

private fun DrawScope.drawLetters(cell: WordCell, scale: Float, litFrom: Float, front: Float, color: Color, dim: Color, pass: LetterPass) {
    withTransform({
        translate(cell.bleed, cell.bleed)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        for (i in cell.letters.indices) {
            val glow = cell.glow[i]
            if (pass == LetterPass.Glow && glow == 0f) continue
            val layout = cell.letters[i]
            val lx = cell.letterX[i]
            val lw = layout.size.width.toFloat()
            val lh = layout.size.height.toFloat()
            val zoom = cell.zoom[i]
            val shadow = if (pass == LetterPass.Shadowed && glow > 0f) {
                Shadow(color.copy(alpha = GlowAlpha * glow), Offset.Zero, cell.sizePx * GlowSoftness)
            } else Shadow.None
            withTransform({
                translate(lx, -cell.lift[i])
                scale(zoom, zoom, pivot = Offset(lw / 2f, lh * 0.85f))
            }) {
                when {
                    lx + lw <= litFrom -> drawText(
                        layout,
                        color = if (pass == LetterPass.Glow) color.copy(alpha = GlowAlpha * glow) else color,
                        shadow = shadow,
                    )
                    lx >= front -> drawText(layout, color = dim, shadow = Shadow.None)
                    else -> {
                        val litX = if (cell.rtl) cell.width - litFrom - lx else litFrom - lx
                        val frontX = if (cell.rtl) cell.width - front - lx else front - lx
                        drawText(
                            layout,
                            brush = Brush.horizontalGradient(0f to color, 1f to dim, startX = litX, endX = frontX),
                            alpha = if (pass == LetterPass.Glow) GlowAlpha * glow else Float.NaN,
                            shadow = shadow,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean artist & track title displayed at the bottom of the poster with marquee.
 */
@Composable
fun CreditLine(
    title: String,
    artist: String,
    color: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = posterType(20.sp, FontWeight.Bold),
            color = color,
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
        Text(
            text = artist,
            style = posterType(14.sp, FontWeight.Medium),
            color = color.copy(alpha = 0.65f),
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
    }
}

/**
 * PosterText container: combines ExpressiveLyrics with fallback to ExpressiveTitle and bottom CreditLine.
 */
@Composable
fun ColumnScope.PosterText(
    lines: List<LyricsEntry>,
    position: LivePosition,
    title: String,
    artist: String,
    color: Color = Color.White,
    baseSize: TextUnit = 42.sp,
    gap: Dp = 10.dp,
    push: () -> Offset = { Offset.Zero },
) {
    val hasLyrics = lines.isNotEmpty()
    val found = remember { Animatable(if (hasLyrics) 1f else 0f) }
    LaunchedEffect(hasLyrics) {
        found.animateTo(
            if (hasLyrics) 1f else 0f,
            tween(600, easing = FastOutSlowInEasing),
        )
    }
    val titleShown by remember { derivedStateOf { found.value < 0.999f } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .graphicsLayer {
                val o = push()
                translationX = o.x
                translationY = o.y
            },
    ) {
        if (hasLyrics) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = found.value
                        if (f < 0.999f) alpha = ((f - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    },
            ) {
                ExpressiveLyrics(
                    lines = lines,
                    position = position,
                    color = color,
                    baseSize = baseSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (titleShown) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val f = found.value
                        alpha = 1f - ((f - 0.5f) / 0.45f).coerceIn(0f, 1f)
                    },
            ) {
                ExpressiveTitle(
                    text = title,
                    baseSize = baseSize,
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Spacer(Modifier.height(gap))

    CreditLine(
        title = title,
        artist = artist,
        color = color,
        modifier = Modifier.fillMaxWidth(),
    )
}
