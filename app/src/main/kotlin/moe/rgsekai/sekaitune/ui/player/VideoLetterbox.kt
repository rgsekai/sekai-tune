/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.ui.player

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What one sampled frame says about the bars on one axis: top and bottom (letterbox) or left
 * and right (pillarbox).
 */
internal sealed interface AxisReading {
    /** The frame can't tell: a fade, a black frame, a dark scene, or bars that don't match. */
    data object Unknown : AxisReading

    /** Bars cover [inset] of the frame on each side of this axis. 0 means there are none. */
    data class Inset(val inset: Float) : AxisReading
}

internal data class FrameReading(
    /** Bars on the top and bottom. */
    val vertical: AxisReading,
    /** Bars on the left and right. */
    val horizontal: AxisReading,
)

/**
 * Finds black bars baked into a music video's frames, so the video view can zoom past them and
 * zoom back out when a scene drops them. Many music videos switch between letterboxed and full
 * frame scenes, so this runs on every sample rather than once per video.
 */
internal object VideoLetterbox {
    /** Largest bar, per side, worth believing. Anything taller is a dark frame, not a bar. */
    const val MAX_INSET = 0.4f

    private const val DARK_LUMA = 26
    private const val BRIGHT_LUMA = 44
    private const val BAR_DARK_FRACTION = 0.96f
    private const val BAR_MAX_MEAN = 16f
    private const val MOSTLY_DARK_FRACTION = 0.6f
    private const val MAX_GAP = 0.06f
    private const val EDGE_PROBE_LINES = 3
    private const val EDGE_BRIGHT_FRACTION = 0.1f
    private const val EDGE_MIN_MEAN = 34f
    private const val MIN_BAR_LINES = 2
    private const val EDGE_MARGIN_LINES = 1
    private const val SYMMETRY_MIN_LINES = 3
    private const val SYMMETRY_FRACTION = 0.2f
    private const val FRAME_MIN_BRIGHT_FRACTION = 0.01f

    private const val EDGE_UNKNOWN = -1
    private const val EDGE_NONE = 0

    private class LineStats(val darkFraction: Float, val brightFraction: Float, val mean: Float) {
        val isBar get() = darkFraction >= BAR_DARK_FRACTION && mean <= BAR_MAX_MEAN
        val isMostlyDark get() = darkFraction >= MOSTLY_DARK_FRACTION
        val isPicture get() = brightFraction >= EDGE_BRIGHT_FRACTION || mean >= EDGE_MIN_MEAN
    }

    fun analyze(pixels: IntArray, width: Int, height: Int): FrameReading {
        val unknown = FrameReading(AxisReading.Unknown, AxisReading.Unknown)
        if (width < 8 || height < 8 || pixels.size < width * height) return unknown

        val luma = IntArray(width * height)
        var brightPixels = 0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            val y = (r * 77 + g * 150 + b * 29) shr 8
            luma[i] = y
            if (y >= BRIGHT_LUMA) brightPixels++
        }

        if (brightPixels.toFloat() / luma.size < FRAME_MIN_BRIGHT_FRACTION) return unknown

        val rows = Array(height) { y -> lineStats(width) { x -> luma[y * width + x] } }
        val cols = Array(width) { x -> lineStats(height) { y -> luma[y * width + x] } }

        return FrameReading(vertical = readAxis(rows), horizontal = readAxis(cols))
    }

    private inline fun lineStats(length: Int, lumaAt: (Int) -> Int): LineStats {
        var dark = 0
        var bright = 0
        var sum = 0L
        for (i in 0 until length) {
            val l = lumaAt(i)
            if (l <= DARK_LUMA) dark++
            if (l >= BRIGHT_LUMA) bright++
            sum += l
        }
        val count = length.toFloat()
        return LineStats(
            darkFraction = dark / count,
            brightFraction = bright / count,
            mean = sum.toFloat() / length,
        )
    }

    private fun readAxis(lines: Array<LineStats>): AxisReading {
        val count = lines.size
        val start = readEdge(count) { lines[it] }
        val end = readEdge(count) { lines[count - 1 - it] }

        if (start == EDGE_NONE || end == EDGE_NONE) return AxisReading.Inset(0f)
        if (start == EDGE_UNKNOWN || end == EDGE_UNKNOWN) return AxisReading.Unknown

        val tolerance = max(SYMMETRY_MIN_LINES.toFloat(), max(start, end) * SYMMETRY_FRACTION)
        if (abs(start - end) > tolerance) return AxisReading.Unknown

        val inset = (min(start, end) + EDGE_MARGIN_LINES).toFloat() / count
        return AxisReading.Inset(inset.coerceAtMost(MAX_INSET))
    }

    private inline fun readEdge(count: Int, lineAt: (Int) -> LineStats): Int {
        val maxGap = max(1, (count * MAX_GAP).toInt())
        var extent = 0
        var i = 0
        while (i < count) {
            val line = lineAt(i)
            if (line.isBar) {
                extent = i + 1
            } else if (!line.isMostlyDark || i - extent >= maxGap) {
                break
            }
            i++
        }

        if (extent > count * MAX_INSET) return EDGE_UNKNOWN
        if (extent < MIN_BAR_LINES) return EDGE_NONE

        val probeEnd = min(count, extent + EDGE_PROBE_LINES)
        for (j in extent until probeEnd) {
            if (lineAt(j).isPicture) return extent
        }
        return EDGE_UNKNOWN
    }
}

internal class LetterboxStabilizer(
    private val samplesToZoomIn: Int = 3,
    private val samplesToZoomOut: Int = 2,
    private val tolerance: Float = 0.015f,
    private val maxSampleAgeMs: Long = 2_500L,
) {
    var inset: Float = 0f
        private set

    private val recentTimes = ArrayDeque<Long>()
    private val recentInsets = ArrayDeque<Float>()

    fun offer(reading: AxisReading, nowMs: Long): Boolean {
        while (recentTimes.isNotEmpty() && nowMs - recentTimes.first() > maxSampleAgeMs) {
            recentTimes.removeFirst()
            recentInsets.removeFirst()
        }
        if (reading !is AxisReading.Inset) return false

        recentTimes.addLast(nowMs)
        recentInsets.addLast(reading.inset)
        while (recentInsets.size > max(samplesToZoomIn, samplesToZoomOut)) {
            recentTimes.removeFirst()
            recentInsets.removeFirst()
        }

        val needed = if (reading.inset > inset) samplesToZoomIn else samplesToZoomOut
        if (recentInsets.size < needed) return false

        val window = recentInsets.takeLast(needed)
        if (window.max() - window.min() > tolerance) return false
        if (window.any { abs(it - inset) <= tolerance }) return false
        if (window.any { it > inset } && window.any { it < inset }) return false

        inset = window.max()
        return true
    }
}

internal data class VideoTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
) {
    companion object {
        val Identity = VideoTransform(1f, 1f, 0f, 0f)
    }
}

internal fun videoCropTransform(
    viewWidth: Float,
    viewHeight: Float,
    videoAspect: Float,
    insetX: Float,
    insetY: Float,
    maxLoss: Float = 1f,
): VideoTransform {
    if (viewWidth <= 0f || viewHeight <= 0f) return VideoTransform.Identity
    val viewAspect = viewWidth / viewHeight
    val aspect = if (videoAspect > 0f && videoAspect.isFinite()) videoAspect else viewAspect

    val keepX = 1f - 2f * insetX.coerceIn(0f, VideoLetterbox.MAX_INSET)
    val keepY = 1f - 2f * insetY.coerceIn(0f, VideoLetterbox.MAX_INSET)
    val pictureAspect = aspect * keepX / keepY

    var pictureWidth: Float
    var pictureHeight: Float
    val keep = (1f - maxLoss).coerceIn(0.01f, 1f)
    if (pictureAspect > viewAspect) {
        pictureHeight = viewHeight
        pictureWidth = viewHeight * pictureAspect
        if (viewWidth / pictureWidth < keep) {
            pictureWidth = viewWidth / keep
            pictureHeight = pictureWidth / pictureAspect
        }
    } else {
        pictureWidth = viewWidth
        pictureHeight = viewWidth / pictureAspect
        if (viewHeight / pictureHeight < keep) {
            pictureHeight = viewHeight / keep
            pictureWidth = pictureHeight * pictureAspect
        }
    }

    val frameWidth = pictureWidth / keepX
    val frameHeight = pictureHeight / keepY
    return VideoTransform(
        scaleX = frameWidth / viewWidth,
        scaleY = frameHeight / viewHeight,
        translationX = (viewWidth - frameWidth) / 2f,
        translationY = (viewHeight - frameHeight) / 2f,
    )
}

