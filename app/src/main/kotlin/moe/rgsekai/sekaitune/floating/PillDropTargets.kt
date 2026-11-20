package moe.rgsekai.sekaitune.floating

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import moe.rgsekai.sekaitune.R
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class PillDrop { CLOSE, NEVER }

private const val RestFill = 0xF0242528.toInt()
private const val CloseFill = 0xFFFFFFFF.toInt()
private const val CloseInk = 0xFF1C1C1E.toInt()
private const val NeverFill = 0xFFE5484D.toInt()
private const val ScrimDark = 0x8C000000.toInt()

private const val ScrimHeightDp = 220f
private const val FromBottomDp = 28f
private const val PillGapDp = 12f

private const val ReachDp = 28f
private const val LeaveDp = 18f
private const val RestAboveDp = 10f

internal class PillDropTargets(private val context: Context, private val coverPx: Int) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var added = false

    private val wmParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
    }

    private val density = context.resources.displayMetrics.density
    private val font = runCatching { ResourcesCompat.getFont(context, R.font.sf_pro_display_semibold) }.getOrNull()
    private val targets = listOf(
        Target(PillDrop.CLOSE, R.string.floating_lyrics_close, CloseFill, CloseInk),
        Target(PillDrop.NEVER, R.string.floating_lyrics_never, NeverFill, Color.WHITE),
    )

    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
        targets.forEachIndexed { i, target ->
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (i > 0) lp.marginStart = px(PillGapDp)
            addView(target.view, lp)
        }
    }

    val view = FrameLayout(context).apply {
        background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(ScrimDark, Color.TRANSPARENT))
        alpha = 0f
        visibility = View.GONE
        clipChildren = false
        clipToPadding = false
        addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ),
        )
    }

    private val bounds = RectF()

    var hovered: PillDrop? = null
        private set

    fun layoutParams(bottomInset: Int): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ScrimHeightDp) + bottomInset, Gravity.BOTTOM)

    fun show(bottomInset: Int) {
        view.animate().cancel()
        val h = px(ScrimHeightDp) + bottomInset
        wmParams.height = h
        view.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM)
        (row.layoutParams as FrameLayout.LayoutParams).bottomMargin = px(FromBottomDp) + bottomInset
        if (!added) {
            runCatching {
                windowManager.addView(view, wmParams)
                added = true
            }
        } else {
            runCatching { windowManager.updateViewLayout(view, wmParams) }
        }
        hovered = null
        targets.forEach { it.reset() }
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(180L).start()
        targets.forEachIndexed { i, target ->
            target.view.translationY = px(72f).toFloat()
            target.view.alpha = 0f
            target.view.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(i * 45L)
                .setDuration(360L)
                .setInterpolator(OvershootInterpolator(1.4f))
                .start()
        }
    }

    fun hide() {
        if (view.visibility != View.VISIBLE) return
        targets.forEach { target ->
            target.stop()
            target.view.animate()
                .translationY(px(56f).toFloat())
                .alpha(0f)
                .setStartDelay(0L)
                .setDuration(170L)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
        view.animate().alpha(0f).setDuration(200L).withEndAction {
            view.visibility = View.GONE
            if (added) {
                runCatching { windowManager.removeView(view) }
                added = false
            }
        }.start()
    }

    fun hideNow() {
        targets.forEach { it.stop() }
        view.animate().cancel()
        view.alpha = 0f
        view.visibility = View.GONE
        hovered = null
        if (added) {
            runCatching { windowManager.removeView(view) }
            added = false
        }
    }

    fun hover(centerX: Float, centerY: Float): PillDrop? {
        var best: PillDrop? = null
        var bestDistance = Float.MAX_VALUE
        for (target in targets) {
            if (!boundsOf(target)) continue
            val reach = px(ReachDp) + if (target.drop == hovered) px(LeaveDp) else 0
            val inside = centerX >= bounds.left - reach && centerX <= bounds.right + reach &&
                centerY >= bounds.top - coverPx - reach && centerY <= bounds.bottom + reach
            val distance = abs(centerX - bounds.centerX())
            if (inside && distance < bestDistance) {
                best = target.drop
                bestDistance = distance
            }
        }
        if (best != hovered) {
            hovered = best
            targets.forEach { it.light(it.drop == best) }
            if (best != null) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        return best
    }

    fun restSpot(drop: PillDrop): PointF? {
        if (!boundsOf(targets.first { it.drop == drop })) return null
        return PointF(bounds.centerX() - coverPx / 2f, bounds.top - px(RestAboveDp) - coverPx)
    }

    fun centerOf(drop: PillDrop): PointF? {
        if (!boundsOf(targets.first { it.drop == drop })) return null
        return PointF(bounds.centerX(), bounds.centerY())
    }

    fun swallow(drop: PillDrop) {
        val pill = targets.first { it.drop == drop }.view
        pill.animate().scaleX(0.92f).scaleY(0.92f).setDuration(110L).setInterpolator(DecelerateInterpolator())
            .withEndAction {
                pill.animate().scaleX(1f).scaleY(1f).setDuration(220L).setInterpolator(OvershootInterpolator(2f)).start()
            }
            .start()
    }

    private fun boundsOf(target: Target): Boolean {
        val pill = target.view
        if (view.visibility != View.VISIBLE || !view.isLaidOut || !pill.isLaidOut) return false
        val loc = IntArray(2)
        pill.getLocationOnScreen(loc)
        bounds.set(loc[0].toFloat(), loc[1].toFloat(), (loc[0] + pill.width).toFloat(), (loc[1] + pill.height).toFloat())
        return true
    }

    private fun px(dp: Float): Int = (dp * density).roundToInt()

    private inner class Target(val drop: PillDrop, label: Int, private val litFill: Int, private val litInk: Int) {
        private val fill = GradientDrawable().apply {
            cornerRadius = px(100f).toFloat()
            setColor(RestFill)
        }
        private val icon = ContextCompat.getDrawable(context, R.drawable.close)?.mutate()?.apply {
            val size = px(16f)
            setBounds(0, 0, size, size)
            setTint(Color.WHITE)
        }
        private val colors = ArgbEvaluator()
        private var amount = 0f
        private var animator: ValueAnimator? = null

        val view = TextView(context).apply {
            text = context.getString(label)
            textSize = 14f
            font?.let { typeface = it }
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = px(6f)
            setPaddingRelative(px(16f), px(12f), px(18f), px(12f))
            background = fill
        }

        fun light(on: Boolean) {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(amount, if (on) 1f else 0f).apply {
                duration = if (on) 240L else 160L
                interpolator = if (on) OvershootInterpolator(2.2f) else DecelerateInterpolator()
                addUpdateListener { paint(it.animatedValue as Float) }
                start()
            }
        }

        fun stop() {
            animator?.cancel()
            animator = null
        }

        fun reset() {
            stop()
            view.animate().cancel()
            paint(0f)
        }

        private fun paint(k: Float) {
            amount = k
            val shade = k.coerceIn(0f, 1f)
            fill.setColor(colors.evaluate(shade, RestFill, litFill) as Int)
            val ink = colors.evaluate(shade, Color.WHITE, litInk) as Int
            view.setTextColor(ink)
            icon?.setTint(ink)
            val scale = 1f + 0.1f * k
            view.scaleX = scale
            view.scaleY = scale
        }
    }
}

