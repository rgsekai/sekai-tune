// LyricsGlowLine.kt

package moe.rgsekai.sekaitune.ui.player

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import moe.rgsekai.sekaitune.lyrics.LyricsEntry
import moe.rgsekai.sekaitune.ui.component.agslRenderEffect
import moe.rgsekai.sekaitune.ui.component.createAgslShader
import moe.rgsekai.sekaitune.ui.component.damageBoundary
import moe.rgsekai.sekaitune.ui.component.setAgslUniform

enum class LyricsLineState { ACTIVE, UPCOMING, PAST, DEFAULT }

/**
 * Which side of the screen a line sits on.
 *
 * Word-synced providers mark duet and call-and-response lines with `{agent:v1}` / `{agent:v2}`,
 * which LyricsUtils has always parsed into [moe.rgsekai.sekaitune.lyrics.LyricsEntry.agent]. Until
 * now only the renderers (such as MetroLyrics or LyricsV2) did anything with it,
 * so in the live player every voice rendered flush left and duets were impossible to follow.
 */
enum class LyricsAlignment { START, CENTER, END }

/**
 * Side room for a lyric line.
 *
 * Sized for the *zoomed* line, not the resting one: the active line scales to 1.06 in a
 * graphicsLayer, which happens after layout, so a line that already fills the width grows past
 * the screen edges. Long wrapped lines - CJK especially - hit this on every active line.
 */
private val LyricsLineHorizontalPadding = 44.dp

/**
 * Inset of the tap highlight around the text. The line's outer padding gives this back, so the
 * text itself still starts exactly at [20.dp] - level with the artwork's left edge.
 */
private val LyricsTapPadH = 8.dp
private val LyricsTapPadV = 4.dp
private val LyricsTapShape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

/**
 * How far a line-synced (no word timing) line sits below its place while it waits; it rises into
 * place as it becomes the active line. Word-synced lines do the same letter by letter, see
 * [rememberWordMotionModifier].
 */
private val LyricsWaitingDrop = 3.dp

internal val LyricsFontSize = 36.sp
internal val LyricsLineHeight = 46.sp

private val BlurStageRadii = floatArrayOf(0f, 5f, 12f)

private const val BlurQuantStepPx = 0.5f
private val BlurEffectCache: Array<androidx.compose.ui.graphics.RenderEffect?> =
    arrayOfNulls((BlurStageRadii.max() / BlurQuantStepPx).toInt() + 2)

// a lyric line's or a note's own blur, so it goes with element blur switched off
internal fun blurEffectForRadius(radius: Float): androidx.compose.ui.graphics.RenderEffect? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radius <= 0.05f) return null
    if (!true) return null
    val step = (radius / BlurQuantStepPx).roundToInt().coerceIn(1, BlurEffectCache.lastIndex)
    BlurEffectCache[step]?.let { return it }
    val quantised = step * BlurQuantStepPx
    // DECAL, not CLAMP: CLAMP repeats the layer's edge pixels outward, and wherever a glyph sat
    // on the edge (the bar of a "T") that drew a hard streak off the side of the line. DECAL fades
    // to clear past the edge instead. Same API level as the blur itself, so this stays inside the
    // version check above.
    return android.graphics.RenderEffect
        .createBlurEffect(quantised, quantised, android.graphics.Shader.TileMode.DECAL)
        .asComposeRenderEffect()
        .also { BlurEffectCache[step] = it }
}

private const val UpcomingAlpha = 0.5f
private const val PastAlpha = 0.24f
private const val DefaultAlpha = 0.38f

/** How bright a line rests in [state]. The lyrics share starts its flying copy from here too. */
internal fun restingAlphaOf(state: LyricsLineState): Float = when (state) {
    LyricsLineState.ACTIVE -> 1f
    LyricsLineState.UPCOMING -> UpcomingAlpha
    LyricsLineState.PAST -> PastAlpha
    LyricsLineState.DEFAULT -> DefaultAlpha
}

/** How big a line rests in [state]: the line being sung is zoomed in a little. */
internal fun restingScaleOf(state: LyricsLineState): Float = when (state) {
    LyricsLineState.ACTIVE -> 1.06f
    LyricsLineState.UPCOMING -> 0.98f
    LyricsLineState.PAST -> 0.93f
    LyricsLineState.DEFAULT -> 0.95f
}

/** The text style of a line without its karaoke sweep, from the theme's [base]. */
internal fun restingLineStyle(base: androidx.compose.ui.text.TextStyle, active: Boolean, letterMotion: Boolean) =
    base.copy(
        fontSize = LyricsFontSize,
        lineHeight = LyricsLineHeight,
        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
    ).withLetterMotionFeatures(letterMotion)

/**
 * Where a line's words sit in its row: the room on each side of the tap highlight, and the
 * highlight's own padding round the text.
 */
internal class LyricsLineInsets(
    val start: androidx.compose.ui.unit.Dp,
    val end: androidx.compose.ui.unit.Dp,
    val top: androidx.compose.ui.unit.Dp,
    val padH: androidx.compose.ui.unit.Dp,
    val padV: androidx.compose.ui.unit.Dp,
)

/**
 * The anchored side lines up with the artwork; the far side keeps room for the 1.06 zoom (padding
 * is applied before the scale, so a line that fills the width would otherwise grow past the edge).
 * Both give back the tap highlight's own inset.
 */
internal fun lyricsLineInsets(alignment: LyricsAlignment): LyricsLineInsets {
    val anchored = 20.dp - LyricsTapPadH
    val zoom = LyricsLineHorizontalPadding - LyricsTapPadH
    val (start, end) = when (alignment) {
        LyricsAlignment.START -> anchored to zoom
        LyricsAlignment.CENTER -> zoom to zoom
        LyricsAlignment.END -> zoom to anchored
    }
    return LyricsLineInsets(start, end, top = 16.dp - LyricsTapPadV, padH = LyricsTapPadH, padV = LyricsTapPadV)
}

private const val ActiveUnsungAlpha = 0.58f

@Composable
fun LyricsGlowLine(
    entry: LyricsEntry,
    state: LyricsLineState,
    blurStage: Int,
    positionProvider: () -> Long,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subLine: String? = null,

    waveEnabled: Boolean = true,

    highBloom: Boolean = true,

    suppressEffects: Boolean = false,

    /** Which voice this line belongs to. See [LyricsAlignment]. */
    alignment: LyricsAlignment = LyricsAlignment.START,

    /** Per-word readings drawn against the line, furigana style. */
    ruby: List<moe.rgsekai.sekaitune.lyrics.RubyToken>? = null,

    /**
     * Which side the readings sit on. Below by default; the caller moves them above when a
     * translation is showing, so the reading and the translation don't stack under the line and
     * push it out of the way.
     */
    rubyPlacement: RubyPlacement = RubyPlacement.BELOW,

    /** Translated text, shown under the line when the translate toggle is on. */
    translation: String? = null,

    /**
     * A translation has been asked for but hasn't arrived. Shows three pulsing dots in the slot the
     * translation will occupy, so the wait is visible instead of the screen simply not changing.
     */
    translationLoading: Boolean = false,

    // the translation goes over the line instead of under it, with the readings left below (the full video mode)
    translationAbove: Boolean = false,

    /** style 1 or boring (words rise, held notes swell), or style 2 or plain (the original wave) */
    motionStyle: moe.rgsekai.sekaitune.constants.LyricsMotionStyle =
        moe.rgsekai.sekaitune.constants.LyricsMotionStyle.MOTION,

    fontSize: androidx.compose.ui.unit.TextUnit = LyricsFontSize,
    lineHeight: androidx.compose.ui.unit.TextUnit = LyricsLineHeight,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    activeWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Bold,

    /** Holding the line: the lyrics page opens sharing with it. */
    onLongClick: (() -> Unit)? = null,

    /** told where the words start across the row (px from its left edge), for the line the card grows into */
    onTextStart: ((Float) -> Unit)? = null,
) {
    val wordMotion = motionStyle.risesWords
    val targetAlpha = restingAlphaOf(state)
    val targetScale = restingScaleOf(state)

    val animSpec = tween<Float>(durationMillis = 600)
    val alpha by animateFloatAsState(targetAlpha, animSpec, label = "lyricsLineAlpha")
    val scale by animateFloatAsState(targetScale, animSpec, label = "lyricsLineScale")

    // Only lines without word timing move as a whole; word-synced ones rise word by word.
    val wholeLineRise = wordMotion && entry.words.isNullOrEmpty()
    val drop by animateFloatAsState(
        targetValue = if (state == LyricsLineState.ACTIVE || !wholeLineRise) 0f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessVeryLow,
        ),
        label = "lyricsLineDrop",
    )

    val blurRadius by animateFloatAsState(
        targetValue = BlurStageRadii[blurStage.coerceIn(0, BlurStageRadii.lastIndex)],
        animationSpec = tween(durationMillis = 350),
        label = "lyricsLineBlur",
    )

    val baseColor = LocalContentColor.current
    // eased with the alpha and scale, and read only while drawing so the fade doesn't recompose the line every frame
    val lineColor = androidx.compose.animation.animateColorAsState(
        targetValue = if (state == LyricsLineState.ACTIVE) Color.White else baseColor,
        animationSpec = tween(durationMillis = 600),
        label = "lyricsLineColor",
    )
    val textColor = remember(lineColor) { ColorProducer { lineColor.value } }
    val unsungColor = remember(lineColor) { ColorProducer { lineColor.value.copy(alpha = ActiveUnsungAlpha) } }
    val readingColor = remember(lineColor) { ColorProducer { lineColor.value.copy(alpha = 0.4f) } }
    val subLineColor = remember(lineColor) { ColorProducer { lineColor.value.copy(alpha = 0.6f) } }
    val translationColor = remember(lineColor) { ColorProducer { lineColor.value.copy(alpha = 0.5f) } }

    // the side on screen, not the text's own start: Start on an arabic line is its right edge
    val onLeft = (alignment == LyricsAlignment.START) !=
        (androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl)
    val textAlign = when (alignment) {
        LyricsAlignment.CENTER -> TextAlign.Center
        else -> if (onLeft) TextAlign.Left else TextAlign.Right
    }
    val columnAlign = when (alignment) {
        LyricsAlignment.START -> androidx.compose.ui.Alignment.Start
        LyricsAlignment.CENTER -> androidx.compose.ui.Alignment.CenterHorizontally
        LyricsAlignment.END -> androidx.compose.ui.Alignment.End
    }

    val insets = lyricsLineInsets(alignment)

    // the pieces as the source timed them: this style sweeps along the line's own text,
    // syllable by syllable, so it never shows the gaps other styles put between them
    val parts = remember(entry.text, entry.words, entry.syllables) {
        splitBackingVocal(entry.text, entry.syllables ?: entry.words)
    }
    val leadText = parts.lead
    val words = parts.leadWords
    val hasRuby = !ruby.isNullOrEmpty()
    val inactiveWeight = when (activeWeight) {
        FontWeight.Black -> FontWeight.ExtraBold
        FontWeight.ExtraBold -> FontWeight.Bold
        FontWeight.Bold -> FontWeight.SemiBold
        FontWeight.SemiBold -> FontWeight.Medium
        FontWeight.Medium -> FontWeight.Normal
        FontWeight.Normal -> FontWeight.Light
        else -> FontWeight.Normal
    }
    val leadStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = fontSize,
        lineHeight = if (hasRuby) (lineHeight.value + RubyHeadroomSp).sp else lineHeight,
        lineHeightStyle = if (hasRuby) rubyLineHeightStyle(rubyPlacement) else null,
        textAlign = textAlign,
        fontFamily = fontFamily,
        fontWeight = activeWeight,
    ).withLetterMotionFeatures(wordMotion)
    // kept above the sweep/plain switch, so the line already has its bold breaks the moment it goes plain again
    val steadyWrap = rememberSteadyWrap(leadText, leadStyle.copy(fontWeight = activeWeight))

    // the row's own frame, so a centred or right-aligned line can say where its words start inside it
    val rowCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val tapPadPx = with(androidx.compose.ui.platform.LocalDensity.current) { LyricsTapPadH.toPx() }

    Column(
        horizontalAlignment = columnAlign,
        modifier = modifier
            .then(if (onTextStart != null) Modifier.onPlaced { rowCoords[0] = it } else Modifier)
            // a row repaints alone, so one moving line doesn't redraw every other row of the list
            .layerBleed(DamageBleed)
            .damageBoundary()
            .padding(DamageBleed)
            .fillMaxWidth()
            .padding(
                start = insets.start,
                end = insets.end,
                top = insets.top,
                bottom = insets.top,
            )
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationY = drop * LyricsWaitingDrop.toPx()
                // The active line grows from its own anchored edge. Pinning a right-aligned line
                // to origin 0 would make it swing left as it scaled up, away from the side it is
                // aligned to.
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    pivotFractionX = when {
                        alignment == LyricsAlignment.CENTER -> 0.5f
                        onLeft -> 0f
                        else -> 1f
                    },
                    pivotFractionY = 0.5f,
                )

                renderEffect = if (suppressEffects) null else blurEffectForRadius(blurRadius)
            }
    ) {
    // The tap target and its highlight hug the text (rounded, as wide as the words) instead of
    // flashing a full-width bar from screen edge to screen edge.
    //
    // It sits BEHIND the text rather than around it. Clipping the line to the rounded highlight
    // also clipped the words: a letter that rose or swelled into a corner was sliced off.
    Box(
        modifier = if (onTextStart != null) {
            Modifier.onPlaced { box ->
                val row = rowCoords[0]
                // measured inside the row, so the flight's own layer around it can't move the answer
                if (row != null && row.isAttached) onTextStart(row.localPositionOf(box, Offset.Zero).x + tapPadPx)
            }
        } else {
            Modifier
        },
    ) {
    Spacer(
        modifier = Modifier
            .matchParentSize()
            .clip(LyricsTapShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
    Column(
        horizontalAlignment = columnAlign,
        modifier = Modifier
            .padding(horizontal = LyricsTapPadH, vertical = LyricsTapPadV)
            .then(steadyWrap.modifier),
    ) {

        val waveAmplitude by animateFloatAsState(
            targetValue = if (state == LyricsLineState.ACTIVE) 1f else 0f,
            animationSpec = animSpec,
            label = "lyricsWaveAmplitude",
        )

        if (translationAbove) {
            TranslationSubText(translation, translationLoading, translationColor, textAlign, above = true)
        }

        // a derived flag, so the fade doesn't recompose the line on every frame
        val waveLingers by remember { derivedStateOf { waveAmplitude > 0.01f } }
        val sweepActive = !words.isNullOrEmpty() &&
            (state == LyricsLineState.ACTIVE || waveLingers)
        val karaokeActive = sweepActive && leadText.isNotEmpty()
        if (karaokeActive) {
            KaraokeSweepText(
                text = leadText,
                words = words,
                positionProvider = positionProvider,
                waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                // The rise is drawn, not shaded, so it runs with the shader setting off too.
                riseAmplitudeProvider = { waveAmplitude },
                motionStyle = motionStyle,
                baseColor = unsungColor,
                highlightColor = Color.White,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontFamily = fontFamily,
                fontWeight = activeWeight,
                // The reading is drawn below, in one place for both paths. It used to move into
                // the karaoke block when the line went active (above any backing vocal, and at a
                // dimmer colour) and back out when it ended, so it jumped every line change.
                subLine = null,
                highBloom = highBloom,
                textAlign = textAlign,
                ruby = ruby,
                rubyPlacement = rubyPlacement,
                rubyColor = readingColor,
            )
        } else if (leadText.isNotEmpty()) {
            // Inactive lines carry their readings too, so the ruby doesn't pop in and out as the
            // active line moves. Needs its own layout capture â€” the karaoke path's holder only
            // exists while the sweep is running.
            val plainLayout = remember(leadText) { arrayOfNulls<TextLayoutResult>(1) }
            BasicText(
                // same characters as the lead text, with a line break wherever the bold line wraps
                text = steadyWrap.text,
                style = leadStyle.copy(
                    fontWeight = if (state == LyricsLineState.ACTIVE) activeWeight else inactiveWeight,
                ),
                color = textColor,
                // No maxLines cap. A capped line ellipsised into "..." and simply lost the rest of
                // the words â€” a long line is meant to wrap, not to be truncated, and there is
                // nowhere else the reader can go to see what was cut.
                overflow = TextOverflow.Clip,
                onTextLayout = { plainLayout[0] = it },
                // No karaoke head on an inactive line, so nothing is lit â€” the readings stay
                // uniformly dim, matching the words above them.
                modifier = rememberRubyOverlay(
                    layoutHolder = plainLayout,
                    ruby = ruby,
                    textLength = leadText.length,
                    placement = rubyPlacement,
                    dimColor = readingColor,
                    litColor = readingColor,
                    headProvider = { 0f },
                ),
            )
        }

        FadingSubText(
            text = subLine,
            color = subLineColor,
            textAlign = textAlign,
        )

        parts.backing?.let { backing ->
            BackingVocalLine(
                text = backing,
                color = textColor,

                slide = leadText.isNotEmpty(),
                words = parts.backingWords,
                positionProvider = positionProvider,
                waveAmplitudeProvider = { if (waveEnabled) waveAmplitude else 0f },
                sweep = sweepActive,

                expandProvider = { waveAmplitude },
                highBloom = highBloom,
                textAlign = textAlign,
                motionStyle = motionStyle,
                riseAmplitudeProvider = { waveAmplitude },
            )
        }

        // The translation sits below both the line and its romanisation. It is written by
        // LyricsTranslationHelper, which until now had no reader in the live player at all â€” the
        // menu toggle wrote into flows nothing was collecting.
        if (!translationAbove) {
            TranslationSubText(translation, translationLoading, translationColor, textAlign, above = false)
        }
    }
    }
    }
}

// the line's translation, or three pulsing dots while it's on its way
@Composable
private fun TranslationSubText(
    translation: String?,
    loading: Boolean,
    color: ColorProducer,
    textAlign: TextAlign,
    above: Boolean,
) {
    if (loading && translation.isNullOrBlank()) {
        TranslationLoadingDots(
            color = color,
            modifier = Modifier.padding(top = if (above) 0.dp else 7.dp, bottom = if (above) 5.dp else 0.dp),
        )
    } else {
        FadingSubText(
            text = translation,
            color = color,
            textAlign = textAlign,
            above = above,
        )
    }
}

// a small line under the lyric (reading or translation) that fades and unfolds in, instead of popping in and shoving the rows below
@Composable
private fun FadingSubText(text: String?, color: ColorProducer, textAlign: TextAlign, above: Boolean = false) {
    // Keep the last text while folding away, or the exit would animate an empty line.
    val shown = remember { arrayOfNulls<String>(1) }
    if (!text.isNullOrBlank()) shown[0] = text
    androidx.compose.animation.AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = androidx.compose.animation.fadeIn(tween(SubTextFadeMs)) +
            androidx.compose.animation.expandVertically(tween(SubTextFadeMs)),
        exit = androidx.compose.animation.fadeOut(tween(SubTextFadeMs)) +
            androidx.compose.animation.shrinkVertically(tween(SubTextFadeMs)),
    ) {
        BasicText(
            text = shown[0].orEmpty(),
            style = MaterialTheme.typography.bodyLarge.copy(textAlign = textAlign),
            color = color,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(top = if (above) 0.dp else 3.dp, bottom = if (above) 2.dp else 0.dp),
        )
    }
}

private const val SubTextFadeMs = 350

/** Three dots pulsing in sequence, sized to sit in the translation's place. */
@Composable
private fun TranslationLoadingDots(color: ColorProducer, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "translating")
    Row(modifier = modifier) {
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 620,
                        delayMillis = index * 140,
                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "translatingDot$index",
            )
            Box(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(5.dp)
                    .graphicsLayer { alpha = 0.25f + 0.75f * phase }
                    .drawBehind { drawCircle(color()) }
            )
        }
    }
}

private val RubyFontSize = 11.sp
private val RubyLineHeight = 13.sp

/**
 * Extra height added to every text line to make room for the reading.
 *
 * Applied through lineHeight rather than container padding so the space lands against EVERY visual
 * line, including ones created by wrapping â€” padding would only feed the first.
 */
private const val RubyHeadroomSp = 14f

/** Gap between the lyric's baseline and its reading. Small on purpose â€” they read as one unit. */
private const val RubyBaselineGapPx = 2f

/**
 * Line-height style that opens the gap on the correct side: pinning glyphs to the bottom of their
 * line box leaves the slack above them, and vice versa.
 */
private fun rubyLineHeightStyle(placement: RubyPlacement) =
    androidx.compose.ui.text.style.LineHeightStyle(
        alignment = when (placement) {
            RubyPlacement.ABOVE -> androidx.compose.ui.text.style.LineHeightStyle.Alignment.Bottom
            RubyPlacement.BELOW -> androidx.compose.ui.text.style.LineHeightStyle.Alignment.Top
        },
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
    )

/** Which side of the lyric its reading sits on. */
enum class RubyPlacement { ABOVE, BELOW }

/**
 * Draws [ruby] readings against the characters they belong to, using the layout the text already
 * produced. Nothing here changes the text's own layout, so the karaoke mask â€” which is built from
 * the same [layoutHolder] â€” stays exactly in step.
 *
 * @param headProvider character offset of the karaoke head, read every frame. Readings light up as
 *   the head passes them so the pronunciation flows with the line instead of sitting there static
 *   while the words underneath it sweep.
 */
@Composable
private fun rememberRubyOverlay(
    layoutHolder: Array<TextLayoutResult?>,
    ruby: List<moe.rgsekai.sekaitune.lyrics.RubyToken>?,
    textLength: Int,
    placement: RubyPlacement,
    dimColor: ColorProducer,
    litColor: ColorProducer,
    headProvider: () -> Float,
): Modifier {
    if (ruby.isNullOrEmpty()) return Modifier
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    // Colour is deliberately NOT part of this style: baking the per-frame colour into the measure
    // call would miss the TextMeasurer cache on every frame and re-lay-out every reading. The
    // colour is applied at draw time instead, where it is free.
    val style = MaterialTheme.typography.bodySmall.copy(
        fontSize = RubyFontSize,
        lineHeight = RubyLineHeight,
    )

    // the measurer only keeps 8 layouts, so a line with more readings re-measured them every frame
    val measuredTokens = remember(ruby, style, measurer) { arrayOfNulls<TextLayoutResult>(ruby.size) }

    return Modifier.drawWithContent {
        drawContent()
        val layout = layoutHolder[0] ?: return@drawWithContent
        val head = headProvider()

        for (tokenIndex in ruby.indices) {
            val token = ruby[tokenIndex]
            val start = token.start.coerceIn(0, textLength)
            val end = token.end.coerceIn(start, textLength)
            if (end <= start) continue

            val line = layout.getLineForOffset(start)
            val left = layout.getHorizontalPosition(start, true)
            // a word that reaches the wrap ends at its line's edge, since the offset after it already sits at the start of the next line and the reading got skipped
            val right = if (end >= layout.getLineEnd(line)) {
                layout.getLineRight(line)
            } else {
                layout.getHorizontalPosition(end, true)
            }
            if (right <= left) continue

            val measured = measuredTokens[tokenIndex] ?: measurer.measure(
                text = androidx.compose.ui.text.AnnotatedString(token.text),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            ).also { measuredTokens[tokenIndex] = it }

            // Centre the reading on its word, then keep it inside the line box so a long reading
            // over a short word doesn't run off the edge.
            val centred = (left + right) / 2f - measured.size.width / 2f
            val x = centred.coerceIn(
                0f,
                (size.width - measured.size.width).coerceAtLeast(0f),
            )
            // Hang the reading off the BASELINE, not off the line box.
            //
            // The line box is deliberately taller than the text â€” that is where the headroom comes
            // from â€” so pinning to its bottom edge parked the reading at the far end of that empty
            // space, visibly detached from the word it belongs to. Sitting it just under the
            // baseline keeps it attached, and the clamp stops a tall glyph pushing it into the
            // next line.
            val y = when (placement) {
                RubyPlacement.ABOVE ->
                    layout.getLineTop(line).coerceAtMost(
                        layout.getLineBaseline(line) - measured.size.height - RubyBaselineGapPx
                    )
                RubyPlacement.BELOW ->
                    (layout.getLineBaseline(line) + RubyBaselineGapPx)
                        .coerceAtMost(layout.getLineBottom(line) - measured.size.height)
            }

            // Same fill the sweep uses, one word at a time: fully lit once the head is past the
            // token, partially lit while inside it.
            val lit = when {
                head <= start -> 0f
                head >= end -> 1f
                else -> (head - start) / (end - start).toFloat()
            }

            drawText(
                textLayoutResult = measured,
                color = lerpColor(dimColor(), litColor(), lit),
                topLeft = Offset(x, y),
            )
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    if (f <= 0f) return from
    if (f >= 1f) return to
    return Color(
        red = from.red + (to.red - from.red) * f,
        green = from.green + (to.green - from.green) * f,
        blue = from.blue + (to.blue - from.blue) * f,
        alpha = from.alpha + (to.alpha - from.alpha) * f,
    )
}

private const val EdgeSoftChars = 1

private const val EdgeSoftPx = 26f

// about how wide a latin letter is, in ems; the sweep's soft edge and the glow's spans are no wider than this a character
private const val LatinLetterEm = 0.55f

// the text's own size in px, so a backing vocal's smaller letters are measured as themselves
private fun androidx.compose.ui.unit.Density.fontPx(layout: TextLayoutResult): Float =
    layout.layoutInput.style.fontSize.takeIf { it.isSp }?.toPx() ?: LyricsFontSize.toPx()

private const val EdgeLeadPx = 6f

private const val WaveCharsBehind = 4f
private const val WaveCharsAhead = 3f

// Style 2 (legacy) wave: a lift-and-zoom bell that rides the head across the letters.
private const val LegacyWaveLiftPx = 4.5f
private const val LegacyWaveZoom = 0.09f

// Style 1 motion. The line is drawn a letter at a time, each letter clipped to its own slot and
// then moved - so a letter can rise without dragging a sliver of the line above or below with it
// (which the old pixel-shifting shader did: the faint line under a rising word).

/** How far an unsung letter waits below its place. It rises into place as it's sung. */
internal val WordWaitDrop = 3.dp

/**
 * A held word (a long note) lifts this much more and swells by [HeldZoom], in step with its glow.
 * It swells letter by letter as the sweep reaches each one, and the word spreads to make room.
 */
private val HeldLift = 2.5.dp
private const val HeldZoom = 0.075f

/**
 * Critically damped follow times. A letter's rise tracks the sweep but never snaps (a one-letter
 * word would otherwise jump up in a frame); the swell breathes in and out slower.
 */
private const val RiseFollowSec = 0.08f
private const val SwellFollowSec = 0.2f

/** A word is fully risen by this fraction of the way through it, so it's up before it's over. */
private const val RiseCompleteAt = 0.85f

/**
 * How much of a letter's rise is its own rather than its word's.
 *
 * Words alone hopped up as blocks - a long word was all the way up while the sweep had only just
 * entered it, and a short one popped. Letters alone stepped up like a staircase. Mixed, the word
 * starts lifting as a whole the moment it's sung and the letters the sweep has reached lift ahead
 * of it, so the line rises as one wave running through each word.
 */
private const val LetterRiseShare = 0.55f

/**
 * A letter starts rising this many characters before the sweep reaches it, and is fully up once
 * the sweep is [LetterRiseSpanChars] further on. A wider span is a softer slope between letters.
 */
private const val LetterRiseLeadChars = 0.5f
private const val LetterRiseSpanChars = 2.5f

/** Same for the swell on a held word: each letter grows as the sweep arrives on it. */
private const val LetterSwellLeadChars = 0.35f
private const val LetterSwellSpanChars = 1.4f

/** The held-note swell comes in quickly and holds until the very end of the note. */
private const val SwellAttackFraction = 0.2f
private const val SwellReleaseFraction = 0.12f

/**
 * Room around the moving text, on every side, inside the layers that draw it.
 *
 * A RenderEffect (the bloom) and an offscreen layer (the sweep's highlight copy) only keep what
 * lies inside their own bounds, and those bounds were exactly the text's box. A letter that rose,
 * dropped or swelled past that box - the first word of a line growing left, descenders on the last
 * line dropping - was sliced off at the edge, and only while the bloom was on, so the cut flickered
 * in and out with held notes.
 */
private val MotionBleed = 10.dp

/** Room around a row for its zoom, lift and glow, so the row's damage boundary never cuts them. */
private val DamageBleed = 24.dp

/** Transforms closer than this (px) are drawn as one piece. */
private const val MergeEpsilonPx = 0.1f

/**
 * Ligatures off for Style 1. A ligature is one glyph covering two letters, so moving those letters
 * separately would tear it in half. Both karaoke copies and the resting line use the same setting,
 * so the line keeps its width when it becomes active.
 */
private const val NoLigatures = "liga 0, clig 0"

internal fun androidx.compose.ui.text.TextStyle.withLetterMotionFeatures(letterMotion: Boolean) =
    if (letterMotion) copy(fontFeatureSettings = NoLigatures) else this

/**
 * Lays out exactly like the content, but gives the layer that follows [bleed] of extra room on
 * every side. Use as `layerBleed(b).graphicsLayer { ... }.padding(b)`: the layer grows, the content
 * inside it lands where it would have been anyway, and nothing around it moves.
 */
internal fun Modifier.layerBleed(bleed: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val px = bleed.roundToPx()
        val placeable = measurable.measure(constraints.offset(px * 2, px * 2))
        layout(
            (placeable.width - px * 2).coerceIn(constraints.minWidth, constraints.maxWidth),
            (placeable.height - px * 2).coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            placeable.place(-px, -px)
        }
    }

/**
 * Time weight of one space, relative to one letter, when a word runs straight into the next with
 * no gap. The sweep then crosses the space at the tail of the word instead of jumping it - the
 * jump was the abrupt cut at every word boundary.
 */
private const val SpaceTimeWeight = 0.4f

/** Below this the bloom is invisible, so the shader is skipped entirely (no offscreen pass). */
private const val BloomCutoff = 0.01f

/** Style 1 without AGSL (API < 33): a soft light behind a held word stands in for the bloom. */
private const val FakeGlowAlpha = 0.16f

private const val BloomRadiusPx = 2.6f

private const val BloomStrength = 1.6f

private const val BloomLagChars = 1.6f
private const val BloomSpanChars = 3.5f

/**
 * Style 1 shader: bloom only. Words are moved by drawing (see [rememberWordMotionModifier]), so
 * nothing here displaces pixels - it reads each pixel where it is and adds a soft glow trailing the
 * head, strongest on a held word. Runs only while there's glow to show.
 */
private const val BloomAgsl = """
uniform shader content;
uniform float2 layerSize;
uniform float headX;
uniform float lineTop;
uniform float lineBottom;
uniform float lineStart;
uniform float prevTop;
uniform float prevBottom;
uniform float prevEnd;
uniform float hasPrev;
uniform float flowDir;
uniform float bloom;
uniform float bloomR;
uniform float bloomLag;
uniform float bloomSpan;
uniform float highQuality;

// Outside the content, eval() returns its edge pixels stretched out - the hard line that ran off
// the side of the active lyric. Samples outside are transparent instead.
half4 sampleIn(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x > layerSize.x || p.y > layerSize.y) return half4(0.0);
    return content.eval(p);
}

half4 main(float2 coord) {
    half4 c = sampleIn(coord);

    float onCur  = (coord.y < lineTop || coord.y > lineBottom) ? 0.0 : 1.0;
    float onPrev = (hasPrev < 0.5 || coord.y < prevTop || coord.y > prevBottom) ? 0.0 : 1.0;
    float onLine = max(onCur, onPrev);
    if (onLine <= 0.0) return c;

    // Distance from the head along the reading flow, wrapping onto the previous visual line.
    // flowDir is -1 on a right-to-left line, where the flow runs the other way across the screen.
    float dxCur  = (coord.x - headX) * flowDir;
    float dxPrev = -((prevEnd - coord.x) + (headX - lineStart)) * flowDir;
    float dx = onPrev > 0.5 ? dxPrev : dxCur;

    float bdx = dx + bloomLag;
    float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
    float bw = 1.0 - ba;
    float bbell = bw * bw * (3.0 - 2.0 * bw);
    if (bbell <= 0.0) return c;

    half4 b = sampleIn(coord + float2(bloomR, bloomR))
            + sampleIn(coord + float2(bloomR, -bloomR))
            + sampleIn(coord + float2(-bloomR, bloomR))
            + sampleIn(coord + float2(-bloomR, -bloomR));
    half weight = 0.25;
    if (highQuality > 0.5) {
        b += sampleIn(coord + float2(bloomR, 0.0))
           + sampleIn(coord - float2(bloomR, 0.0))
           + sampleIn(coord + float2(0.0, bloomR))
           + sampleIn(coord - float2(0.0, bloomR));
        weight = 0.125;
    }
    return c + b * (bloom * bbell * weight);
}
"""

private const val LegacyWaveAgsl = """
uniform shader content;
uniform float headX;
uniform float spanBehind;
uniform float spanAhead;
uniform float liftPx;
uniform float zoomAmt;
uniform float baseY;
uniform float lineTop;
uniform float lineBottom;
uniform float bloom;
uniform float bloomR;
uniform float bloomLag;
uniform float bloomSpan;
uniform float highQuality;

// The visual line the sweep just left, and where it ended. Without these the effect was clamped
// to the head's own line, so the moment a long lyric wrapped, the trailing part of the glow -- the
// few characters behind the head that should still be lit on the line above -- was cut off
// mid-stride instead of easing out. See flowDx below.
uniform float prevTop;
uniform float prevBottom;
uniform float prevEnd;
uniform float prevBaseY;
uniform float hasPrev;
uniform float lineStart;
// -1 on a right-to-left line: the flow runs leftward, lines start at the right and end at the left
uniform float flowDir;

// Bounds-safe sampling (the only change from the original): outside the content, eval() returned
// the edge pixels stretched out, which drew a hard line off the side of the active lyric.
uniform float2 layerSize;

half4 sampleIn(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x > layerSize.x || p.y > layerSize.y) return half4(0.0);
    return content.eval(p);
}

half4 main(float2 coord) {
    float onCur  = (coord.y < lineTop || coord.y > lineBottom) ? 0.0 : 1.0;
    float onPrev = (hasPrev < 0.5 || coord.y < prevTop || coord.y > prevBottom) ? 0.0 : 1.0;
    float onLine = max(onCur, onPrev);

    // Distance from the head measured along the READING FLOW, not along screen x. On the head's
    // own line that is just the horizontal gap. On the previous line it continues around the
    // wrap: how far the pixel sits from where that line ends, plus how far the head has already
    // travelled from where this line starts. That makes the glow spill across the line break the
    // way it would if the text were one continuous strip.
    float dxCur  = (coord.x - headX) * flowDir;
    float dxPrev = -((prevEnd - coord.x) + (headX - lineStart)) * flowDir;
    float dx = onPrev > 0.5 ? dxPrev : dxCur;

    float bY = onPrev > 0.5 ? prevBaseY : baseY;

    float span = dx < 0.0 ? spanBehind : spanAhead;
    float a = min(1.0, abs(dx) / max(span, 1.0));
    float w = 1.0 - a;
    float bell = w * w * (3.0 - 2.0 * w) * onLine;

    // sampling from below the output pixel is what makes the letter appear to
    float2 p = coord;
    p.y += liftPx * bell;
    // stretch about the baseline so the glyph grows upward instead of drifting
    p.y = bY + (p.y - bY) / (1.0 + zoomAmt * bell);

    half4 c = sampleIn(p);

    if (bloom > 0.0 && onLine > 0.0) {
        // the bloom gets its own bell wider than the lift s and centred behind the
        // than on it sharing the lift s bell put the glow exactly where the sweep s
        // boundary is the one place the lit copy is half transparent and the dim
        // dim so it landed on the faintest pixels on screen and was invisible
        // Expressed as a lag from the head in flow space so it wraps with everything else.
        float bdx = dx + bloomLag;
        float ba = min(1.0, abs(bdx) / max(bloomSpan, 1.0));
        float bw = 1.0 - ba;
        float bbell = bw * bw * (3.0 - 2.0 * bw) * onLine;

        // four diagonal taps always the axis aligned four only on the high setting
        // alone still read as a halo they just make it very slightly less round
        half4 b = sampleIn(p + float2(bloomR, bloomR))
                + sampleIn(p + float2(bloomR, -bloomR))
                + sampleIn(p + float2(-bloomR, bloomR))
                + sampleIn(p + float2(-bloomR, -bloomR));
        half weight = 0.25;
        if (highQuality > 0.5) {
            b += sampleIn(p + float2(bloomR, 0.0))
               + sampleIn(p - float2(bloomR, 0.0))
               + sampleIn(p + float2(0.0, bloomR))
               + sampleIn(p - float2(0.0, bloomR));
            weight = 0.125;
        }
        c += b * (bloom * bbell * weight);
    }
    return c;
}
"""


/**
 * Where the sweep is, for a caller that has to follow it - the bottom card slides a long line
 * sideways to keep the sung part in view. Written from draw, so reading it costs nothing.
 */
internal class SweepProbe {
    /** The sweep's x in the text's own coordinates, 0 before it starts. */
    var headX = 0f
    var textWidth = 0f
}

@Composable
internal fun KaraokeSweepText(
    text: String,
    words: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>,
    positionProvider: () -> Long,
    waveAmplitudeProvider: () -> Float,
    // read while drawing, so a fading line doesn't recompose every frame
    baseColor: ColorProducer,
    highlightColor: Color,
    subLine: String? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = LyricsFontSize,
    lineHeight: androidx.compose.ui.unit.TextUnit = LyricsLineHeight,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    fontWeight: androidx.compose.ui.text.font.FontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    highBloom: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
    ruby: List<moe.rgsekai.sekaitune.lyrics.RubyToken>? = null,
    rubyPlacement: RubyPlacement = RubyPlacement.BELOW,
    rubyColor: ColorProducer = DefaultRubyColor,
    motionStyle: moe.rgsekai.sekaitune.constants.LyricsMotionStyle =
        moe.rgsekai.sekaitune.constants.LyricsMotionStyle.MOTION,
    /**
     * Style 1 word rise, 0..1 with the line becoming active. Separate from [waveAmplitudeProvider]
     * on purpose: that one is zero when the lyrics shader is switched off, and the rise is drawn
     * without the shader, so it keeps working.
     */
    riseAmplitudeProvider: () -> Float = waveAmplitudeProvider,
    /** One line that never wraps: the bottom card scrolls it sideways instead. */
    singleLine: Boolean = false,
    /** Letter rise and swell distances, relative to the full-size line. */
    motionScale: Float = 1f,
    probe: SweepProbe? = null,
) {
    val wordMotion = motionStyle.risesWords
    val head = rememberKaraokeHead(text, words, positionProvider, wordMotion)

    val hasRuby = !ruby.isNullOrEmpty()
    val style = MaterialTheme.typography.headlineSmall.copy(
        fontSize = fontSize,
        lineHeight = if (hasRuby) (lineHeight.value + RubyHeadroomSp).sp else lineHeight,
        lineHeightStyle = if (hasRuby) rubyLineHeightStyle(rubyPlacement) else null,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
    ).withLetterMotionFeatures(wordMotion)

    val layoutHolder = remember(text) { arrayOfNulls<TextLayoutResult>(1) }

    val sung = remember(text) { Path() }

    val effect: Modifier
    val motion: Modifier
    if (wordMotion) {
        effect = rememberBloomModifier(layoutHolder, head, text.length, waveAmplitudeProvider, highBloom)
        // Letters move by drawing, inside the bloom layer so the glow sees them where they are.
        // Without AGSL there's no bloom pass, so a soft light behind a held word stands in for it
        // (only while the lyrics shader setting is on - off means no glow at all).
        val fakeGlow = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        motion = rememberWordMotionModifier(
            layoutHolder = layoutHolder,
            head = head,
            riseAmplitudeProvider = riseAmplitudeProvider,
            fakeGlowProvider = { if (fakeGlow) waveAmplitudeProvider() else 0f },
            motionScale = motionScale,
        )
    } else {
        effect = rememberLegacyWaveModifier(layoutHolder, head, text.length, waveAmplitudeProvider, highBloom)
        motion = Modifier
    }
    // Applied outside the wave shader so the readings stay still while the sung word lifts â€” a
    // reading that rides the wave with its word reads as jitter at this size.
    val rubyOverlay = rememberRubyOverlay(
        layoutHolder = layoutHolder,
        ruby = ruby,
        textLength = text.length,
        placement = rubyPlacement,
        dimColor = rubyColor,
        litColor = LitRubyColor,
        headProvider = { head.offset.floatValue },
    )

    Column(modifier = rubyOverlay) {
        // The effect layer is [MotionBleed] bigger than the text on every side (see there), so a
        // letter that moves past the text's box is still inside the layer. The letters are drawn
        // after the padding, in the text's own coordinates.
        Box(
            modifier = Modifier
                .layerBleed(MotionBleed)
                .then(effect)
                .padding(MotionBleed)
                .then(motion)
        ) {

            // Both copies must lay out identically or the sweep mask, which is built from the
            // first copy's TextLayoutResult and clipped over the second, lands in the wrong place.
            // That includes the line cap: capping at 3 lines also ellipsised long lines into
            // "..." and dropped the rest of the words outright.
            BasicText(
                text = text,
                style = style.copy(fontWeight = fontWeight, textAlign = textAlign),
                color = baseColor,
                overflow = TextOverflow.Clip,
                softWrap = !singleLine,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                onTextLayout = {
                    layoutHolder[0] = it
                    probe?.textWidth = it.size.width.toFloat()
                },
            )

            Text(
                text = text,
                style = style,
                color = highlightColor,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                textAlign = textAlign,
                overflow = TextOverflow.Clip,
                softWrap = !singleLine,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                modifier = Modifier
                    // Room around the text, as for the effect layer: an offscreen layer keeps only
                    // what's inside it, and glyph ink that reaches past the line's ends (a "y"
                    // tail, an "f" hook) was lit on the dim copy but cut on the lit one.
                    .layerBleed(MotionBleed)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .padding(MotionBleed)
                    .drawWithContent {
                        val layout = layoutHolder[0] ?: return@drawWithContent
                        val h = head.offset.floatValue
                        if (h <= 0f) return@drawWithContent
                        val len = text.length
                        if (len == 0) return@drawWithContent
                        val reach = MotionBleed.toPx()
                        val lastLine = layout.lineCount - 1
                        // The sung area reaches into the room around the text on the outside edges
                        // only - between lines it stops exactly at the boundary, so it never lights
                        // the top of the line below.
                        fun topOf(line: Int) = layout.getLineTop(line) - if (line == 0) reach else 0f
                        fun bottomOf(line: Int) =
                            layout.getLineBottom(line) + if (line == lastLine) reach else 0f

                        val headOffset = h.toInt().coerceIn(0, len)
                        val headLine = layout.shownLineFor(headOffset.coerceAtMost(len - 1))
                        val rtl = layout.isRtlLine(headLine)
                        // the way the sweep travels on screen: right to left for arabic, hebrew...
                        val flow = if (rtl) -1f else 1f

                        val headX = layout.sweepHeadX(h, headOffset, headLine, len, rtl)
                        probe?.headX = headX
                        val lineTop = topOf(headLine)
                        val lineBottom = bottomOf(headLine)

                        val softFrom = layout.getHorizontalPosition(
                            (headOffset - EdgeSoftChars).coerceAtLeast(layout.getLineStart(headLine)),
                            true,
                        )
                        // one character back, but no more than a latin letter: across a whole kanji the sung one and the one before it both sat half lit
                        val softMax = fontPx(layout) * LatinLetterEm
                        val fromX = if ((headX - softFrom) * flow > 0f) {
                            headX - flow * minOf((headX - softFrom) * flow, softMax)
                        } else {
                            headX - EdgeSoftPx * flow
                        }
                        val toX = headX + EdgeLeadPx * flow

                        sung.rewind()
                        for (line in 0..headLine) {
                            var left = layout.getLineLeft(line) - reach
                            var right = layout.getLineRight(line) + reach
                            // the head's own line is lit from where it starts up to the head
                            if (line == headLine && headOffset < len) {
                                if (rtl) left = maxOf(toX, left) else right = minOf(toX, right)
                            }
                            if (right > left) {
                                sung.addRect(Rect(left, topOf(line), right, bottomOf(line)))
                            }
                        }

                        clipPath(sung) { this@drawWithContent.drawContent() }

                        if (headOffset < len) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Black,
                                    1f to Color.Transparent,
                                    startX = fromX,
                                    endX = toX,
                                ),
                                topLeft = Offset(minOf(fromX, toX), lineTop),
                                size = Size(kotlin.math.abs(toX - fromX), lineBottom - lineTop),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            )
        }

        if (!subLine.isNullOrBlank()) {
            BasicText(
                text = subLine,
                style = MaterialTheme.typography.bodyLarge.copy(textAlign = textAlign),
                color = { baseColor().copy(alpha = 0.6f) },
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

// the line an offset is on, kept to the lines the layout shows: when text is too wide for a maxLines = 1
// layout (the bottom card's) it still wraps onto a hidden line 1, and asking that line anything crashed
internal fun TextLayoutResult.shownLineFor(offset: Int): Int = getLineForOffset(offset).coerceAtMost(lineCount - 1)

internal fun TextLayoutResult.isRtlLine(line: Int): Boolean =
    getParagraphDirection(getLineStart(line)) == androidx.compose.ui.text.style.ResolvedTextDirection.Rtl

// the head's x on screen. an rtl line starts at its right edge, so a finished one ends at its left
internal fun TextLayoutResult.sweepHeadX(h: Float, headOffset: Int, headLine: Int, len: Int, rtl: Boolean): Float {
    if (headOffset >= len) return if (rtl) getLineLeft(headLine) else getLineRight(headLine)
    val x0 = getHorizontalPosition(headOffset, true)
    val x1 = getHorizontalPosition((headOffset + 1).coerceAtMost(len), true)
    val forward = if (rtl) x1 <= x0 else x1 >= x0
    return if (forward) x0 + (x1 - x0) * (h - headOffset) else x0
}

// where reading starts and stops on a line, on screen
internal fun TextLayoutResult.lineStartX(line: Int, rtl: Boolean): Float = if (rtl) getLineRight(line) else getLineLeft(line)
internal fun TextLayoutResult.lineEndX(line: Int, rtl: Boolean): Float = if (rtl) getLineLeft(line) else getLineRight(line)

private val DefaultRubyColor = ColorProducer { Color.White.copy(alpha = 0.4f) }
private val LitRubyColor = ColorProducer { Color.White }

internal class LyricLineParts(
    val lead: String,
    val backing: String?,
    val leadWords: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>?,
    val backingWords: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>?,
)

private fun partitionWords(
    text: String,
    words: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>?,
    inBracket: BooleanArray,
): Pair<List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>, List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>> {
    if (words.isNullOrEmpty()) return emptyList<moe.rgsekai.sekaitune.lyrics.WordTimestamp>() to emptyList()
    val lead = ArrayList<moe.rgsekai.sekaitune.lyrics.WordTimestamp>(words.size)
    val backing = ArrayList<moe.rgsekai.sekaitune.lyrics.WordTimestamp>()
    var searchFrom = 0
    for (word in words) {
        val trimmed = word.text.trim()
        if (trimmed.isEmpty()) continue
        val start = text.indexOf(trimmed, searchFrom)
        if (start < 0) continue
        searchFrom = start + trimmed.length

        if (inBracket.getOrElse(start) { false }) backing.add(word) else lead.add(word)
    }
    return lead to backing
}

internal fun splitBackingVocal(
    text: String,
    words: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>?,
): LyricLineParts {
    val untouched = LyricLineParts(text, null, words, null)
    if (text.indexOf('(') < 0 && text.indexOf('[') < 0) return untouched

    val lead = StringBuilder(text.length)
    val backing = StringBuilder()
    val inBracket = BooleanArray(text.length)
    var depth = 0
    text.forEachIndexed { i, ch ->
        when {
            ch == '(' || ch == '[' -> {
                depth++
                inBracket[i] = true
            }
            (ch == ')' || ch == ']') && depth > 0 -> {
                depth--
                inBracket[i] = true
                if (depth == 0) backing.append(' ')
            }
            depth > 0 -> {
                inBracket[i] = true
                backing.append(ch)
            }
            else -> lead.append(ch)
        }
    }

    if (depth != 0) return untouched

    val leadText = lead.toString().replace(WhitespaceRun, " ").trim()
    val backingText = backing.toString().replace(WhitespaceRun, " ").trim()
    if (backingText.isEmpty()) return untouched

    val (leadWords, backingWords) = partitionWords(text, words, inBracket)
    return LyricLineParts(
        lead = leadText,
        backing = backingText,
        leadWords = leadWords.withoutBrackets().ifEmpty { null },
        backingWords = backingWords.withoutBrackets().ifEmpty { null },
    )
}

/**
 * The timed words with their brackets removed, to match the split texts, which have none.
 *
 * Timed words keep the brackets of the original line ("(ooh", "yeah)"). The sweep finds each word
 * by searching the text it draws, and "(ooh" is not in "ooh yeah", so the first and last backing
 * words were never found: the sweep sat still until the middle word and stopped before the end.
 */
private fun List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>.withoutBrackets() =
    mapNotNull { word ->
        val bare = word.text.filterNot { it == '(' || it == ')' || it == '[' || it == ']' }
        when {
            bare == word.text -> word
            bare.isBlank() -> null
            else -> word.copy(text = bare)
        }
    }

private val WhitespaceRun = Regex("\\s{2,}")

@Composable
private fun BackingVocalLine(
    text: String,
    color: ColorProducer,
    slide: Boolean,
    words: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>?,
    positionProvider: () -> Long,
    waveAmplitudeProvider: () -> Float,
    sweep: Boolean,
    expandProvider: () -> Float,
    highBloom: Boolean,
    textAlign: TextAlign = TextAlign.Start,
    motionStyle: moe.rgsekai.sekaitune.constants.LyricsMotionStyle =
        moe.rgsekai.sekaitune.constants.LyricsMotionStyle.MOTION,
    riseAmplitudeProvider: () -> Float = waveAmplitudeProvider,
) {

    val expand = rememberBackingExpand(words, positionProvider, expandProvider)
    val wordMotion = motionStyle.risesWords

    // Keep the sweep for as long as the backing line is on screen. It used to drop back to the
    // plain copy the moment the main line finished, while a backing vocal that trails the line
    // was still being sung, so the sweep stopped halfway and the text flashed to full brightness.
    val visible by remember(expand) { derivedStateOf { expand.floatValue > 0.01f } }
    val useSweep = !words.isNullOrEmpty() && (sweep || visible)

    Box(
        modifier = Modifier
            // Clipped only while it opens, and only on the edge it opens from. A permanent clip to
            // its bounds cut the backing words' descenders off as they dropped, and their tops as
            // they swelled, with no room at all around the text.
            .drawWithContent {
                val e = expand.floatValue
                if (e >= 1f) {
                    drawContent()
                } else {
                    val room = size.width + size.height
                    clipRect(
                        left = -room,
                        top = if (slide) 0f else -room,
                        right = size.width + room,
                        bottom = if (slide) size.height + room else size.height,
                    ) { this@drawWithContent.drawContent() }
                }
            }
            // Hidden until it is sung and folded away after: it opens to its full height while
            // its words are being sung and closes again once they are done.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val e = expand.floatValue.coerceIn(0f, 1f)
                val height = (placeable.height * e).roundToInt()
                layout(placeable.width, height) {

                    placeable.place(0, if (slide) height - placeable.height else 0)
                }
            }
            .graphicsLayer { alpha = expand.floatValue.coerceIn(0f, 1f) },
    ) {

        if (useSweep) {
            KaraokeSweepText(
                text = text,
                words = words!!,
                positionProvider = positionProvider,
                waveAmplitudeProvider = waveAmplitudeProvider,
                baseColor = { color().copy(alpha = ActiveUnsungAlpha) },
                highlightColor = Color.White,
                fontSize = BackingVocalFontSize,
                lineHeight = BackingVocalLineHeight,
                highBloom = highBloom,
                textAlign = textAlign,
                motionStyle = motionStyle,
                riseAmplitudeProvider = riseAmplitudeProvider,
            )
        } else {
            BasicText(
                text = text,
                // Same font features as the karaoke copy, so the line keeps its width (and its
                // wrapping) when it switches between the two.
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = BackingVocalFontSize,
                    lineHeight = BackingVocalLineHeight,
                    fontWeight = FontWeight.Bold,
                    textAlign = textAlign,
                ).withLetterMotionFeatures(wordMotion),
                color = color,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

private const val BackingLeadInSec = 0.35
private const val BackingTailSec = 0.6

@Composable
private fun rememberBackingExpand(
    words: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>?,
    positionProvider: () -> Long,
    lineAmplitudeProvider: () -> Float,
): androidx.compose.runtime.MutableFloatState {
    val state = remember(words) { mutableFloatStateOf(0f) }
    val firstStart = remember(words) { words?.firstOrNull()?.startTime }
    val lastEnd = remember(words) { words?.lastOrNull()?.endTime }

    LaunchedEffect(words, positionProvider) {
        while (true) {
            withFrameMillis {
                val sec = positionProvider() / 1000.0
                val own = if (firstStart == null || lastEnd == null) {
                    0f
                } else {
                    when {
                        sec >= firstStart && sec <= lastEnd -> 1f
                        sec < firstStart -> smoothstep(
                            ((sec - (firstStart - BackingLeadInSec)) / BackingLeadInSec).toFloat()
                        )
                        else -> 1f - smoothstep(((sec - lastEnd) / BackingTailSec).toFloat())
                    }
                }
                // Timed backing vocals follow their own words: they open just before they are sung
                // and fold away once done. Taking the larger of this and the main line's state kept
                // them open for the whole line and they never hid. Untimed ones still follow the line.
                val next = if (firstStart == null || lastEnd == null) {
                    lineAmplitudeProvider().coerceIn(0f, 1f)
                } else {
                    own
                }
                if (next != state.floatValue) state.floatValue = next
            }
        }
    }
    return state
}

private val BackingVocalFontSize = 21.sp
private val BackingVocalLineHeight = 26.sp

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

// compiled once for every line: each line sets all its uniforms right before it builds its effect
private var sharedBloomShader: Any? = null
private var sharedLegacyShader: Any? = null

/** Style 1: the bloom pass. Skipped (no render effect at all) whenever nothing is glowing. */
@Suppress("NewApi")
@Composable
private fun rememberBloomModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    textLength: Int,
    amplitudeProvider: () -> Float,
    highBloom: Boolean,
): Modifier {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sharedBloomShader ?: createAgslShader(BloomAgsl).also { sharedBloomShader = it }
        } else {
            null
        }
    }
    if (shader == null) return Modifier

    val cache = remember { WaveEffectCache() }

    return Modifier.graphicsLayer {
        val layout = layoutHolder[0]
        val amp = amplitudeProvider().coerceIn(0f, 1f)
        val glow = head.glow.floatValue
        val bloom = BloomStrength * glow * amp
        if (layout == null || textLength == 0 || bloom <= BloomCutoff) {
            renderEffect = null
            return@graphicsLayer
        }

        val h = head.offset.floatValue
        val headOffset = h.toInt().coerceIn(0, textLength)
        val headLine = layout.shownLineFor(headOffset.coerceAtMost(textLength - 1))
        val rtl = layout.isRtlLine(headLine)
        val headX = layout.sweepHeadX(h, headOffset, headLine, textLength, rtl)
        val lineStart = layout.getLineStart(headLine)
        val lineEnd = layout.getLineEnd(headLine, visibleEnd = true)
        // capped at a latin letter: a line of kanji averages a whole em a character, and its glow trailed twice as far as on latin
        val charWidth = if (lineEnd > lineStart) {
            ((layout.getLineRight(headLine) - layout.getLineLeft(headLine)) / (lineEnd - lineStart))
                .coerceAtMost(fontPx(layout) * LatinLetterEm)
        } else {
            LyricsFontSize.toPx() * 0.5f
        }

        // The layer is [MotionBleed] bigger than the text on every side; the text starts at o.
        val o = MotionBleed.roundToPx().toFloat()
        shader.setAgslUniform("layerSize", size.width, size.height)
        shader.setAgslUniform("headX", headX + o)
        shader.setAgslUniform("lineTop", layout.getLineTop(headLine) + o)
        shader.setAgslUniform("lineBottom", layout.getLineBottom(headLine) + o)
        shader.setAgslUniform("lineStart", layout.lineStartX(headLine, rtl) + o)
        shader.setAgslUniform("flowDir", if (rtl) -1f else 1f)
        shader.setAgslUniform("bloom", bloom)
        shader.setAgslUniform("bloomR", BloomRadiusPx)
        shader.setAgslUniform("bloomLag", charWidth * BloomLagChars)
        shader.setAgslUniform("bloomSpan", charWidth * BloomSpanChars)
        shader.setAgslUniform("highQuality", if (highBloom) 1f else 0f)
        if (headLine > 0) {
            shader.setAgslUniform("hasPrev", 1f)
            shader.setAgslUniform("prevTop", layout.getLineTop(headLine - 1) + o)
            shader.setAgslUniform("prevBottom", layout.getLineBottom(headLine - 1) + o)
            shader.setAgslUniform("prevEnd", layout.lineEndX(headLine - 1, rtl) + o)
        } else {
            shader.setAgslUniform("hasPrev", 0f)
            shader.setAgslUniform("prevTop", 0f)
            shader.setAgslUniform("prevBottom", 0f)
            shader.setAgslUniform("prevEnd", 0f)
        }

        renderEffect = cache.effectFor(shader, headX, amp, glow, headLine, size.width, size.height)
    }
}

// where a sweep's head is, in the layer's own pixels, for the bloom around it; glow is how strongly it shines, 0 to 1
internal class SweepHeadGeometry(
    val headX: Float,
    val lineTop: Float,
    val lineBottom: Float,
    val lineStart: Float,
    val charWidth: Float,
    val glow: Float,
)

// the same bloom centred on a point instead of trailing a head, fading out spanPx either side, for things that aren't letters like the gap dots
internal fun glowAround(centreX: Float, spanPx: Float, top: Float, bottom: Float, glow: Float) = SweepHeadGeometry(
    headX = centreX + spanPx * BloomLagChars / BloomSpanChars,
    lineTop = top,
    lineBottom = bottom,
    lineStart = 0f,
    charWidth = spanPx / BloomSpanChars,
    glow = glow,
)

// the page's bloom around a sweep head on any text that can say where its head is (the share's cinematic words); android 13+, nothing at all before that
@Suppress("NewApi")
@Composable
internal fun Modifier.sweepBloom(highBloom: Boolean, head: androidx.compose.ui.unit.Density.() -> SweepHeadGeometry?): Modifier {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sharedBloomShader ?: createAgslShader(BloomAgsl).also { sharedBloomShader = it }
        } else {
            null
        }
    } ?: return this
    val cache = remember { WaveEffectCache() }
    return graphicsLayer {
        val at = head()
        val bloom = if (at == null) 0f else BloomStrength * at.glow.coerceIn(0f, 1f)
        if (at == null || bloom <= BloomCutoff) {
            renderEffect = null
            return@graphicsLayer
        }
        shader.setAgslUniform("layerSize", size.width, size.height)
        shader.setAgslUniform("headX", at.headX)
        shader.setAgslUniform("lineTop", at.lineTop)
        shader.setAgslUniform("lineBottom", at.lineBottom)
        shader.setAgslUniform("lineStart", at.lineStart)
        shader.setAgslUniform("flowDir", 1f)
        shader.setAgslUniform("bloom", bloom)
        shader.setAgslUniform("bloomR", BloomRadiusPx)
        shader.setAgslUniform("bloomLag", at.charWidth * BloomLagChars)
        shader.setAgslUniform("bloomSpan", at.charWidth * BloomSpanChars)
        shader.setAgslUniform("highQuality", if (highBloom) 1f else 0f)
        shader.setAgslUniform("hasPrev", 0f)
        shader.setAgslUniform("prevTop", 0f)
        shader.setAgslUniform("prevBottom", 0f)
        shader.setAgslUniform("prevEnd", 0f)
        renderEffect = cache.effectFor(shader, at.headX, 1f, at.glow, 0, size.width, size.height)
    }
}

/** Style 2: the original wave - a lift-and-zoom bell that follows the head across the letters. */
@Suppress("NewApi")
@Composable
private fun rememberLegacyWaveModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    textLength: Int,

    amplitudeProvider: () -> Float,

    highBloom: Boolean,
): Modifier {

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sharedLegacyShader ?: createAgslShader(LegacyWaveAgsl).also { sharedLegacyShader = it }
        } else {
            null
        }
    }
    if (shader == null) return Modifier

    val cache = remember { WaveEffectCache() }

    return Modifier.graphicsLayer {
        val layout = layoutHolder[0]
        val h = head.offset.floatValue
        val amp = amplitudeProvider().coerceIn(0f, 1f)

        if (layout == null || textLength == 0 || h <= 0f || amp <= WaveCutoff) {

            renderEffect = null
            return@graphicsLayer
        }

        val headOffset = h.toInt().coerceIn(0, textLength)
        val headLine = layout.shownLineFor(headOffset.coerceAtMost(textLength - 1))
        val rtl = layout.isRtlLine(headLine)
        val headX = layout.sweepHeadX(h, headOffset, headLine, textLength, rtl)

        val lineStart = layout.getLineStart(headLine)
        val lineEnd = layout.getLineEnd(headLine, visibleEnd = true)
        // capped at a latin letter: a line of kanji averages a whole em a character, and its glow trailed twice as far as on latin
        val charWidth = if (lineEnd > lineStart) {
            ((layout.getLineRight(headLine) - layout.getLineLeft(headLine)) / (lineEnd - lineStart))
                .coerceAtMost(fontPx(layout) * LatinLetterEm)
        } else {
            LyricsFontSize.toPx() * 0.5f
        }

        // The layer is [MotionBleed] bigger than the text on every side; the text starts at o.
        // Only the coordinates move - the shader and its look are untouched.
        val o = MotionBleed.roundToPx().toFloat()
        shader.setAgslUniform("layerSize", size.width, size.height)
        shader.setAgslUniform("headX", headX + o)
        shader.setAgslUniform("spanBehind", charWidth * WaveCharsBehind)
        shader.setAgslUniform("spanAhead", charWidth * WaveCharsAhead)
        shader.setAgslUniform("liftPx", LegacyWaveLiftPx * amp)
        shader.setAgslUniform("zoomAmt", LegacyWaveZoom * amp)
        shader.setAgslUniform("baseY", layout.getLineBaseline(headLine) + o)
        shader.setAgslUniform("lineTop", layout.getLineTop(headLine) + o)
        shader.setAgslUniform("lineBottom", layout.getLineBottom(headLine) + o)
        shader.setAgslUniform("bloom", BloomStrength * head.glow.floatValue * amp)
        shader.setAgslUniform("bloomR", BloomRadiusPx)
        shader.setAgslUniform("bloomLag", charWidth * BloomLagChars)
        shader.setAgslUniform("bloomSpan", charWidth * BloomSpanChars)
        shader.setAgslUniform("highQuality", if (highBloom) 1f else 0f)
        shader.setAgslUniform("lineStart", layout.lineStartX(headLine, rtl) + o)
        shader.setAgslUniform("flowDir", if (rtl) -1f else 1f)

        // Let the trailing glow continue onto the line the sweep just wrapped off, instead of
        // being clipped at the line break.
        if (headLine > 0) {
            shader.setAgslUniform("hasPrev", 1f)
            shader.setAgslUniform("prevTop", layout.getLineTop(headLine - 1) + o)
            shader.setAgslUniform("prevBottom", layout.getLineBottom(headLine - 1) + o)
            shader.setAgslUniform("prevEnd", layout.lineEndX(headLine - 1, rtl) + o)
            shader.setAgslUniform("prevBaseY", layout.getLineBaseline(headLine - 1) + o)
        } else {
            shader.setAgslUniform("hasPrev", 0f)
            shader.setAgslUniform("prevTop", 0f)
            shader.setAgslUniform("prevBottom", 0f)
            shader.setAgslUniform("prevEnd", 0f)
            shader.setAgslUniform("prevBaseY", 0f)
        }

        renderEffect = cache.effectFor(shader, headX, amp, head.glow.floatValue, headLine, size.width, size.height)
    }
}

private const val WaveCutoff = 0.02f

private const val WaveHeadStepPx = 0.5f
private const val WaveAmpStep = 0.02f

private class WaveEffectCache {
    private var lastHead = Int.MIN_VALUE
    private var lastAmp = Int.MIN_VALUE
    private var lastGlow = Int.MIN_VALUE
    private var lastLine = Int.MIN_VALUE
    private var lastWidth = Int.MIN_VALUE
    private var lastHeight = Int.MIN_VALUE
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    @Suppress("NewApi")
    fun effectFor(
        shader: Any,
        headX: Float,
        amplitude: Float,
        glow: Float,
        // RenderEffects are immutable snapshots of the uniforms, so the line index has to be part
        // of the key: on a wrap headX can land on the same quantised bucket it held on the line
        // above, and the stale effect would keep painting the old line's band.
        headLine: Int,
        width: Float,
        height: Float,
    ): androidx.compose.ui.graphics.RenderEffect? {
        val h = (headX / WaveHeadStepPx).roundToInt()
        val a = (amplitude / WaveAmpStep).roundToInt()
        val g = (glow / WaveAmpStep).roundToInt()
        val w = width.roundToInt()
        val ht = height.roundToInt()
        val cached = effect
        if (cached != null && h == lastHead && a == lastAmp && g == lastGlow && headLine == lastLine &&
            w == lastWidth && ht == lastHeight
        ) return cached
        lastWidth = w
        lastHeight = ht
        lastHead = h
        lastAmp = a
        lastGlow = g
        lastLine = headLine
        return agslRenderEffect(shader, "content")
            .asComposeRenderEffect()
            .also { effect = it }
    }
}

@androidx.compose.runtime.Stable
internal class KaraokeHead(
    /** Visual words of the line - [wordStarts] inclusive, [wordEnds] exclusive, as text offsets. */
    val wordStarts: IntArray = IntArray(0),
    val wordEnds: IntArray = IntArray(0),
    /** The letters each word is moved as - see [motionCells]. */
    val cells: MotionCells = MotionCells.Empty,
) {

    val offset = mutableFloatStateOf(0f)

    val glow = mutableFloatStateOf(0f)

    val wordCount: Int get() = wordStarts.size

    val cellCount: Int get() = cells.starts.size

    /** Per letter cell, Style 1: 0 = waiting below its place, 1 = risen into place. */
    val rise = FloatArray(cells.starts.size)
    val riseVelocity = FloatArray(cells.starts.size)

    /** Per letter cell, Style 1: how swollen it is with a held note (0..1). */
    val swell = FloatArray(cells.starts.size)
    val swellVelocity = FloatArray(cells.starts.size)

    /** Bumped whenever [rise]/[swell] change, so the draw that reads them is invalidated. */
    val motionTick = androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * The pieces a line is moved in: letters (grapheme clusters, so an accented letter or an emoji stays
 * whole), or whole words where letters can't be pulled apart cleanly.
 *
 * @property word the visual word each cell belongs to.
 * @property tiled the cells can tile each line edge to edge - plain left-to-right text. Right-to-left
 *   text keeps the old per-word glyph boxes instead.
 */
internal class MotionCells(
    val starts: IntArray,
    val ends: IntArray,
    val word: IntArray,
    val tiled: Boolean,
) {
    companion object {
        val Empty = MotionCells(IntArray(0), IntArray(0), IntArray(0), tiled = false)
    }
}

internal fun motionCells(text: String, wordStarts: IntArray, wordEnds: IntArray): MotionCells {
    val ltr = text.none {
        val dir = Character.getDirectionality(it)
        dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }
    val graphemes = if (ltr) {
        java.text.BreakIterator.getCharacterInstance().also { it.setText(text) }
    } else {
        null
    }
    val starts = ArrayList<Int>(text.length)
    val ends = ArrayList<Int>(text.length)
    val owner = ArrayList<Int>(text.length)
    for (w in wordStarts.indices) {
        val ws = wordStarts[w]
        val we = wordEnds[w]
        if (graphemes == null || !splitsIntoLetters(text, ws, we)) {
            starts += ws
            ends += we
            owner += w
            continue
        }
        var s = ws
        while (s < we) {
            var e = graphemes.following(s)
            if (e == java.text.BreakIterator.DONE || e > we) e = we
            starts += s
            ends += e
            owner += w
            s = e
        }
    }
    return MotionCells(starts.toIntArray(), ends.toIntArray(), owner.toIntArray(), tiled = ltr)
}

/**
 * Scripts whose letters are separate shapes. Joined and shaped scripts (Arabic, Devanagari, Thai...)
 * would be torn apart mid-stroke by moving their letters separately, so their words move whole.
 */
internal fun splitsIntoLetters(text: String, start: Int, end: Int): Boolean {
    var i = start
    while (i < end) {
        val cp = text.codePointAt(i)
        when (Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.ARMENIAN,
            Character.UnicodeScript.GEORGIAN,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.COMMON,
            Character.UnicodeScript.INHERITED -> Unit
            else -> return false
        }
        i += Character.charCount(cp)
    }
    return true
}

/**
 * The words a line is drawn as, for per-word motion: runs of non-space characters, except that
 * Chinese and Japanese characters each count as their own word (there are no spaces to split on,
 * and they are sung one at a time).
 */
internal fun visualWords(text: String): Pair<IntArray, IntArray> {
    val starts = ArrayList<Int>()
    val ends = ArrayList<Int>()
    var i = 0
    while (i < text.length) {
        val cp = text.codePointAt(i)
        val width = Character.charCount(cp)
        when {
            Character.isWhitespace(cp) -> i += width
            isSingleGlyphWord(cp) -> {
                starts += i
                ends += i + width
                i += width
            }
            else -> {
                val start = i
                while (i < text.length) {
                    val c = text.codePointAt(i)
                    if (Character.isWhitespace(c) || isSingleGlyphWord(c)) break
                    i += Character.charCount(c)
                }
                starts += start
                ends += i
            }
        }
    }
    return starts.toIntArray() to ends.toIntArray()
}

private fun isSingleGlyphWord(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA -> true
    else -> false
}

/** One critically damped spring step toward [target] - exact, so any frame time is stable. */
private fun dampedStep(
    values: FloatArray,
    velocities: FloatArray,
    index: Int,
    target: Float,
    followSec: Float,
    dt: Float,
): Boolean {
    val omega = 1f / followSec
    val y = values[index] - target
    val v = velocities[index]
    if (kotlin.math.abs(y) < 0.0005f && kotlin.math.abs(v) < 0.005f) {
        if (values[index] == target && v == 0f) return false
        values[index] = target
        velocities[index] = 0f
        return true
    }
    val e = kotlin.math.exp(-omega * dt)
    val tmp = (v + omega * y) * dt
    values[index] = target + (y + tmp) * e
    velocities[index] = (v - omega * tmp) * e
    return true
}

/** Gaps up to this count as "the next word starts as this one ends". */
private const val NoGapSec = 0.03

private const val FastSecPerChar = 0.1f
private const val SlowSecPerChar = 0.34f

// a kanji, kana or hangul block is a whole syllable, about three latin letters' worth, so it's weighed as that much when telling a held note
private const val SyllableGlyphWeight = 3

private const val GlowAttackFraction = 0.35f
private const val GlowReleaseFraction = 0.3f

internal class TimedRange(
    val start: Int,
    val end: Int,
    val startTime: Double,
    val endTime: Double,
    /** letters' worth of singing: a syllable glyph counts as [SyllableGlyphWeight], so one kanji sung at a normal pace isn't a held note */
    val sungLength: Int,
)

internal fun sungLength(text: String, start: Int = 0, end: Int = text.length): Int {
    var n = 0
    var i = start
    while (i < end) {
        val cp = text.codePointAt(i)
        n += if (isSyllableGlyph(cp)) SyllableGlyphWeight else 1
        i += Character.charCount(cp)
    }
    return n.coerceAtLeast(1)
}

private fun isSyllableGlyph(codePoint: Int): Boolean = when (Character.UnicodeScript.of(codePoint)) {
    Character.UnicodeScript.HAN,
    Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA,
    Character.UnicodeScript.HANGUL -> true
    else -> false
}

internal fun karaokeFilledChars(
    text: String,
    words: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>,
    positionMs: Long,
): Int {
    var searchFrom = 0
    val ranges = words.map { word ->
        val trimmed = word.text.trim()
        val start = if (trimmed.isEmpty()) -1 else text.indexOf(trimmed, searchFrom)
        if (start >= 0) {
            searchFrom = start + trimmed.length
            start to (start + trimmed.length)
        } else null
    }
    val positionSec = positionMs / 1000.0
    var filled = 0
    words.forEachIndexed { index, word ->
        val range = ranges.getOrNull(index) ?: return@forEachIndexed
        when {
            positionSec >= word.endTime -> filled = range.second
            positionSec >= word.startTime -> {
                val span = (word.endTime - word.startTime).takeIf { it > 0.0 } ?: return@forEachIndexed
                val within = ((positionSec - word.startTime) / span).coerceIn(0.0, 1.0)
                filled = range.first + ((range.second - range.first) * within).roundToInt()
            }
        }
    }
    return filled.coerceIn(0, text.length)
}

internal fun karaokeAnnotated(
    text: String,
    filledChars: Int,
    highlightColor: Color,
    baseColor: Color,
) = buildAnnotatedString {
    val cut = filledChars.coerceIn(0, text.length)
    if (cut > 0) {
        withStyle(SpanStyle(color = highlightColor)) { append(text.substring(0, cut)) }
    }
    if (cut < text.length) {
        withStyle(SpanStyle(color = baseColor)) { append(text.substring(cut)) }
    }
}

@Composable
private fun rememberKaraokeHead(
    text: String,
    words: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>,
    positionProvider: () -> Long,
    /** Style 1: sweep through spaces smoothly and drive the per-word rise/swell springs. */
    wordMotion: Boolean = false,
): KaraokeHead {
    val timed = remember(text, words) { karaokeTimedRanges(text, words) }
    val head = remember(text, words) { karaokeHeadFor(text) }

    LaunchedEffect(text, timed, positionProvider, wordMotion) {
        var lastFrameNanos = -1L
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastFrameNanos < 0L) 0.016f else ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.064f)
                // the provider is a playback clock that already moves on the frame's own time, so no guessing between polls
                head.advance(timed, positionProvider() / 1000.0, dt, wordMotion)
                lastFrameNanos = frameNanos
            }
        }
    }

    return head
}

// each timed word found in the line's own text, so the sweep knows which letters it covers
internal fun karaokeTimedRanges(
    text: String,
    words: List<moe.rgsekai.sekaitune.lyrics.WordTimestamp>,
): List<TimedRange> {
    var searchFrom = 0
    return words.mapNotNull { word ->
        val trimmed = word.text.trim()
        val start = if (trimmed.isEmpty()) -1 else text.indexOf(trimmed, searchFrom)
        if (start < 0) return@mapNotNull null
        searchFrom = start + trimmed.length
        TimedRange(start, searchFrom, word.startTime, word.endTime, sungLength(text, start, searchFrom))
    }
}

// a fresh sweep for one line, with the words and letters it moves as
internal fun karaokeHeadFor(text: String): KaraokeHead {
    val (starts, ends) = visualWords(text)
    return KaraokeHead(starts, ends, motionCells(text, starts, ends))
}

// moves the sweep and every letter's rise and swell on to positionSec, true while the sweep or a letter still moved
internal fun KaraokeHead.advance(
    timed: List<TimedRange>,
    positionSec: Double,
    dt: Float,
    wordMotion: Boolean,
): Boolean {
    var next = 0f
    var nextGlow = 0f
    var nextSwell = 0f
    for (index in timed.indices) {
        val word = timed[index]
        if (positionSec < word.startTime) break

        if (positionSec >= word.endTime) {
            next = word.end.toFloat()

            val following = timed.getOrNull(index + 1) ?: continue
            val gap = following.startTime - word.endTime
            if (gap > 0.0 && positionSec < following.startTime) {
                val through = ((positionSec - word.endTime) / gap).coerceIn(0.0, 1.0)
                next = word.end +
                    (following.start - word.end) * smoothstep(through.toFloat())
            }
            continue
        }

        val span = word.endTime - word.startTime
        if (span <= 0.0) break
        val within = ((positionSec - word.startTime) / span).coerceIn(0.0, 1.0)
        val length = (word.end - word.start).coerceAtLeast(1)

        // When the next word starts the instant this one ends, the space between them
        // has no time of its own, and the head used to jump it in one frame - the
        // abrupt cut at every word boundary. Style 1 gives the space a small share of
        // this word's time instead, so the sweep glides across it.
        val following = timed.getOrNull(index + 1)
        val tail = if (
            wordMotion && following != null &&
            following.startTime - word.endTime <= NoGapSec &&
            following.start > word.end
        ) {
            following.start - word.end
        } else {
            0
        }
        next = if (tail > 0) {
            val letters = length.toFloat()
            val letterShare = letters / (letters + tail * SpaceTimeWeight)
            val w = within.toFloat()
            if (w <= letterShare) {
                word.start + letters * (w / letterShare)
            } else {
                word.end + tail * ((w - letterShare) / (1f - letterShare))
            }
        } else {
            word.start + (length * within).toFloat()
        }

        val secPerChar = (span / word.sungLength).toFloat()
        val hold = smoothstep(
            (secPerChar - FastSecPerChar) / (SlowSecPerChar - FastSecPerChar)
        )
        val w = within.toFloat()
        nextGlow = hold *
            smoothstep(w / GlowAttackFraction) *
            smoothstep((1f - w) / GlowReleaseFraction)
        // The swell has its own, squarer envelope: it fills the letters one by one as
        // they're sung, so it has to still be there when the sweep reaches the last
        // letter. The glow's longer release had it gone by then.
        nextSwell = hold *
            smoothstep(w / SwellAttackFraction) *
            smoothstep((1f - w) / SwellReleaseFraction)
        break
    }

    var moved = false
    if (next != offset.floatValue) {
        offset.floatValue = next
        moved = true
    }

    val glowNow = glow.floatValue
    val glowNext =
        if (nextGlow >= glowNow) nextGlow else glowNow + (nextGlow - glowNow) * 0.12f
    if (glowNext != glowNow) glow.floatValue = glowNext

    if (wordMotion && cellCount > 0) {
        var changed = false
        for (c in 0 until cellCount) {
            val w = cells.word[c]
            val wordStart = wordStarts[w]
            val wordEnd = wordEnds[w]
            val letterStart = cells.starts[c]

            // The word lifts as a whole as the sweep goes through it...
            val through = (next - wordStart) / (wordEnd - wordStart).coerceAtLeast(1)
            val wordRise = smoothstep(through / RiseCompleteAt)
            // ...and each letter lifts further as the sweep reaches it. Once the word
            // is over every letter is up, so the last word of a line (where the sweep
            // stops) doesn't leave its final letters hanging part-way.
            val letterRise = if (next >= wordEnd) {
                1f
            } else {
                smoothstep((next - letterStart + LetterRiseLeadChars) / LetterRiseSpanChars)
            }
            val riseTarget = wordRise + (letterRise - wordRise) * LetterRiseShare

            // A held note swells the word the sweep is in, letter by letter as each
            // is reached, then the whole word settles together once the sweep leaves.
            val swellTarget = if (next >= wordStart && next <= wordEnd) {
                nextSwell *
                    smoothstep((next - letterStart + LetterSwellLeadChars) / LetterSwellSpanChars)
            } else {
                0f
            }
            changed = dampedStep(rise, riseVelocity, c, riseTarget, RiseFollowSec, dt) or changed
            changed = dampedStep(swell, swellVelocity, c, swellTarget, SwellFollowSec, dt) or changed
        }
        if (changed) {
            motionTick.intValue++
            moved = true
        }
    }
    return moved
}

/**
 * Where each letter cell sits in one text layout, plus per-frame scratch space for its transform.
 * Rebuilt only when the layout object changes. Parallel arrays, one entry per (cell, line) piece -
 * a cell only has more than one piece when it's a whole word that wraps.
 *
 * Pieces are in reading order, so the pieces of one word on one line are consecutive.
 */
private class MotionLayout(
    val count: Int,
    val tiled: Boolean,
    val cell: IntArray,
    val word: IntArray,
    val line: IntArray,
    /** The letter's own advance - what it's centred on and spread by when its word swells. */
    val coreLeft: FloatArray,
    val coreRight: FloatArray,
    /** The slot it's clipped to before it moves. */
    val clipLeft: FloatArray,
    val clipTop: FloatArray,
    val clipRight: FloatArray,
    val clipBottom: FloatArray,
    val baseline: FloatArray,
) {
    val dx = FloatArray(count)
    val dy = FloatArray(count)
    val scale = FloatArray(count)
}

private class MotionLayoutCache {
    private var forLayout: TextLayoutResult? = null
    private var cached: MotionLayout? = null

    fun layoutFor(layout: TextLayoutResult, head: KaraokeHead, edgePadPx: Float): MotionLayout {
        val current = cached
        if (current != null && layout === forLayout) return current
        forLayout = layout
        return buildMotionLayout(layout, head, edgePadPx).also { cached = it }
    }
}

/** Side room for glyph overhang in untiled (per-word) slots, only where the neighbour is a space. */
private const val WordBoxPadPx = 3f

private fun buildMotionLayout(
    layout: TextLayoutResult,
    head: KaraokeHead,
    edgePadPx: Float,
): MotionLayout {
    val text = layout.layoutInput.text.text
    val tiled = head.cells.tiled
    // only a whole word that wraps becomes more than one piece, at most one extra per line break
    val capacity = head.cellCount + layout.lineCount
    val cells = IntArray(capacity)
    val lines = IntArray(capacity)
    val segStarts = IntArray(capacity)
    val segEnds = IntArray(capacity)
    val lefts = FloatArray(capacity)
    val rights = FloatArray(capacity)
    var n = 0
    if (text.isNotEmpty() && layout.lineCount > 0) {
        // every letter's box in one go: asking one letter at a time re-measured the line from its start for each, which hitched long lines
        val boxes = layout.multiParagraph.fillBoundingBoxes(TextRange(0, text.length), FloatArray(text.length * 4), 0)
        for (c in 0 until head.cellCount) {
            val start = head.cells.starts[c].coerceIn(0, text.length)
            val end = head.cells.ends[c].coerceIn(start, text.length)
            if (end <= start) continue
            for (line in layout.shownLineFor(start)..layout.shownLineFor(end - 1)) {
                val segStart = maxOf(start, layout.getLineStart(line))
                val segEnd = minOf(end, layout.getLineEnd(line))
                if (segEnd <= segStart) continue
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                for (i in segStart until segEnd) {
                    if (text[i].isWhitespace()) continue
                    left = minOf(left, boxes[i * 4])
                    right = maxOf(right, boxes[i * 4 + 2])
                }
                if (left > right || n >= capacity) continue
                cells[n] = c
                lines[n] = line
                segStarts[n] = segStart
                segEnds[n] = segEnd
                lefts[n] = left
                rights[n] = right
                n++
            }
        }
    }

    val lastLine = layout.lineCount - 1
    val clipLeft = FloatArray(n)
    val clipRight = FloatArray(n)
    val clipTop = FloatArray(n)
    val clipBottom = FloatArray(n)
    val baseline = FloatArray(n)
    for (k in 0 until n) {
        val line = lines[k]
        // Exact line boundaries between lines, so a moving letter never carries a sliver of the
        // line above or below it. The outside edges reach into the layer's spare room.
        clipTop[k] = layout.getLineTop(line) - if (line == 0) edgePadPx else 0f
        clipBottom[k] = layout.getLineBottom(line) + if (line == lastLine) edgePadPx else 0f
        baseline[k] = layout.getLineBaseline(line)
    }

    if (tiled) {
        // The slots cover each line edge to edge with no gaps: a boundary between two letters is
        // where they meet, a boundary across a space is the middle of the space, and the ends of
        // the line reach into the spare room. Whatever ink a glyph has - an overhang, a tail, a
        // mark - it lands in exactly one slot.
        var a = 0
        while (a < n) {
            var b = a
            while (b + 1 < n && lines[b + 1] == lines[a]) b++
            val line = lines[a]
            clipLeft[a] = minOf(lefts[a], layout.getLineLeft(line)) - edgePadPx
            clipRight[b] = maxOf(rights[b], layout.getLineRight(line)) + edgePadPx
            for (k in a until b) {
                val meet = (rights[k] + lefts[k + 1]) / 2f
                clipRight[k] = meet
                clipLeft[k + 1] = meet
            }
            a = b + 1
        }
    } else {
        for (k in 0 until n) {
            val segStart = segStarts[k]
            val segEnd = segEnds[k]
            val padLeft = if (segStart == 0 || text[segStart - 1].isWhitespace()) WordBoxPadPx else 0f
            val padRight = if (segEnd >= text.length || text[segEnd].isWhitespace()) WordBoxPadPx else 0f
            clipLeft[k] = lefts[k] - padLeft
            clipRight[k] = rights[k] + padRight
        }
    }

    return MotionLayout(
        count = n,
        tiled = tiled,
        cell = cells.copyOf(n),
        word = IntArray(n) { head.cells.word[cells[it]] },
        line = lines.copyOf(n),
        coreLeft = lefts.copyOf(n),
        coreRight = rights.copyOf(n),
        clipLeft = clipLeft,
        clipTop = clipTop,
        clipRight = clipRight,
        clipBottom = clipBottom,
        baseline = baseline,
    )
}

/** A swell this small is drawn as none, so a settled word goes back to a single plain draw. */
private fun settledSwell(swell: Float): Float = if (swell < 0.002f) 0f else swell

/**
 * Style 1: draws the line one letter at a time, each clipped to its own slot and then moved.
 *
 * Unsung letters sit [WordWaitDrop] low and rise as they're sung - partly with their word, partly on
 * their own (see [LetterRiseShare]), so the rise runs through each word as a wave. On a held note
 * the letters swell and lift one by one as the sweep reaches them; the word spreads about its
 * centre by its average swell to make room, so neighbours don't crowd each other or drift sideways.
 *
 * Clip first, then move: the clip is in the letter's own (unmoved) space, so only that letter's
 * pixels ever travel. Letters with the same transform are drawn together - the risen part of a
 * line and the waiting part are one draw each - and when nothing is displaced it's a single plain
 * draw, so the cost is a handful of draws around the sweep, not one per letter.
 */
@Composable
internal fun rememberWordMotionModifier(
    layoutHolder: Array<TextLayoutResult?>,
    head: KaraokeHead,
    riseAmplitudeProvider: () -> Float,
    fakeGlowProvider: () -> Float,
    motionScale: Float = 1f,
): Modifier {
    val cache = remember(head) { MotionLayoutCache() }
    return Modifier.drawWithContent {
        // Subscribes this draw to the per-letter springs.
        @Suppress("UNUSED_VARIABLE") val tick = head.motionTick.intValue
        val layout = layoutHolder[0]
        if (layout == null || head.cellCount == 0) {
            drawContent()
            return@drawWithContent
        }
        val m = cache.layoutFor(layout, head, MotionBleed.toPx())
        val n = m.count
        if (n == 0) {
            drawContent()
            return@drawWithContent
        }

        val drop = WordWaitDrop.toPx() * motionScale * riseAmplitudeProvider().coerceIn(0f, 1f)
        val lift = HeldLift.toPx() * motionScale
        val glow = fakeGlowProvider().coerceIn(0f, 1f)

        // Work out every letter's transform, one word-on-a-line at a time.
        var uniform = true
        var i = 0
        while (i < n) {
            var j = i
            while (j + 1 < n && m.word[j + 1] == m.word[i] && m.line[j + 1] == m.line[i]) j++

            var swellSum = 0f
            for (k in i..j) swellSum += settledSwell(head.swell[m.cell[k]])
            val meanSwell = swellSum / (j - i + 1)
            val pivot = (m.coreLeft[i] + m.coreRight[j]) / 2f
            for (k in i..j) {
                val swell = settledSwell(head.swell[m.cell[k]])
                val dy = drop * (1f - head.rise[m.cell[k]]) - lift * swell
                m.dy[k] = if (kotlin.math.abs(dy) < 0.05f) 0f else dy
                m.dx[k] = ((m.coreLeft[k] + m.coreRight[k]) / 2f - pivot) * HeldZoom * meanSwell
                m.scale[k] = 1f + HeldZoom * swell
                if (m.scale[k] != 1f || kotlin.math.abs(m.dy[k] - m.dy[0]) > MergeEpsilonPx) {
                    uniform = false
                }
            }

            // Without AGSL there's no bloom: a soft light behind the held word stands in for it.
            if (glow > 0f && meanSwell > 0.01f) {
                val width = m.coreRight[j] - m.coreLeft[i]
                val height = m.clipBottom[i] - m.clipTop[i]
                val centreY = (m.clipTop[i] + m.clipBottom[i]) / 2f + (m.dy[i] + m.dy[j]) / 2f
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = FakeGlowAlpha * meanSwell * glow), Color.Transparent),
                        center = Offset(pivot, centreY),
                        radius = maxOf(width, height) * 0.75f,
                    ),
                    topLeft = Offset(pivot - width * 0.75f, centreY - height * 0.6f),
                    size = Size(width * 1.5f, height * 1.2f),
                )
            }
            i = j + 1
        }

        // Nothing out of step (the line hasn't started, or everything's up): one draw.
        if (uniform) {
            val dy = m.dy[0]
            if (dy == 0f) {
                drawContent()
            } else {
                withTransform({ translate(0f, dy) }) { this@drawWithContent.drawContent() }
            }
            return@drawWithContent
        }

        var k = 0
        while (k < n) {
            // Neighbouring slots that move together are one slot (tiled slots meet edge to edge).
            if (m.tiled && m.scale[k] == 1f && m.dx[k] == 0f) {
                var e = k
                while (e + 1 < n && m.line[e + 1] == m.line[k] && m.scale[e + 1] == 1f &&
                    m.dx[e + 1] == 0f && kotlin.math.abs(m.dy[e + 1] - m.dy[k]) <= MergeEpsilonPx
                ) e++
                withTransform({ translate(0f, m.dy[k]) }) {
                    clipRect(m.clipLeft[k], m.clipTop[k], m.clipRight[e], m.clipBottom[k]) {
                        this@drawWithContent.drawContent()
                    }
                }
                k = e + 1
                continue
            }

            val s = m.scale[k]
            withTransform({
                translate(m.dx[k], m.dy[k])
                if (s != 1f) {
                    scale(s, s, Offset((m.coreLeft[k] + m.coreRight[k]) / 2f, m.baseline[k]))
                }
            }) {
                clipRect(m.clipLeft[k], m.clipTop[k], m.clipRight[k], m.clipBottom[k]) {
                    this@drawWithContent.drawContent()
                }
            }
            k++
        }
    }
}

