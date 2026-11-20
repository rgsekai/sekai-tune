package moe.rgsekai.sekaitune.floating

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private val EdgeGap = 8.dp
private val BarGap = 6.dp
private val EdgeGive = 5.dp
private const val TuckWaitMs = 350L
private const val FlingCarrySec = 0.14f
private const val MaxFlingDpPerSec = 1_800f

private const val VanishMs = 220L

internal interface PillWindowListener {
    fun onDocked(end: Boolean)
    fun onDragStarted()
    fun onSettling()
    fun onMoved(x: Int, y: Int)
    fun onDropped(drop: PillDrop)
    fun onTouched()
    fun onTouchedOutside()
    fun onCoverTapped()
    fun onCoverLongPressed()
}

internal class PillWindow(
    private val context: Context,
    savedX: Int?,
    savedY: Int?,
    private val listener: PillWindowListener,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val owner = OverlayOwner()
    private val root = DragFrame()
    private val compose = ComposeView(context)
    private var snapAnimator: ValueAnimator? = null

    val metrics = PillMetrics.forScreen((screenSize().x / density).dp)
    private val margin = px(PillMetrics.Margin)
    private val cover = px(PillMetrics.Cover)

    private val targets = PillDropTargets(context, cover)

    private var coverX = 0f
    private var coverY = 0f
    private var dockEnd = true
    private var frameWidth = px(metrics.width(PillShape.PILL))
    private var frameHeight = px(metrics.height(PillShape.PILL, BaseSlots))
    private var slots = BaseSlots
    private var open = false

    private var pendingSettle: PointF? = null
    private val settleAnyway = Runnable {
        val waiting = pendingSettle ?: return@Runnable
        pendingSettle = null
        resize(px(metrics.width(PillShape.COVER)), px(metrics.height(PillShape.COVER, slots)))
        settle(waiting.x, waiting.y)
    }

    private var landing = false

    private val params = WindowManager.LayoutParams(
        frameWidth,
        frameHeight,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
        title = "Floating lyrics"
    }

    init {
        val screen = screenSize()
        dockEnd = savedX?.let { it + frameWidth / 2 > screen.x / 2 } ?: true
        coverX = restX(dockEnd)
        coverY = clampY((savedY ?: (screen.y / 6)) + margin.toFloat(), frameHeight)
        place(apply = false)
    }

    fun show(content: @Composable () -> Unit): Boolean {
        listener.onDocked(dockEnd)
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        root.setViewTreeViewModelStoreOwner(owner)
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        compose.setContent(content)
        root.addView(compose, FrameLayout.LayoutParams(frameWidth, frameHeight, Gravity.TOP or Gravity.START))
        owner.start()
        return runCatching { windowManager.addView(root, params) }
            .onFailure { owner.destroy() }
            .isSuccess
    }

    fun remove() {
        landing = false
        root.removeCallbacks(settleAnyway)
        cancelSnap()
        targets.hideNow()
        root.release()
        compose.animate().cancel()
        owner.destroy()
        runCatching { windowManager.removeView(root) }
    }

    fun reserveFrame(shape: PillShape, slots: Int) {
        this.slots = slots
        open = shape == PillShape.OPEN
        if (root.dragging) return
        resize(maxOf(frameWidth, px(metrics.width(shape))), maxOf(frameHeight, px(metrics.height(shape, slots))))
        if (snapAnimator?.isRunning != true) makeRoom()
    }

    fun fitFrame(shape: PillShape, slots: Int) {
        this.slots = slots
        open = shape == PillShape.OPEN
        if (root.dragging) return
        resize(px(metrics.width(shape)), px(metrics.height(shape, slots)))
        if (shape == PillShape.COVER) pendingSettle?.let {
            pendingSettle = null
            root.removeCallbacks(settleAnyway)
            settle(it.x, it.y)
        }
    }

    private fun resize(width: Int, height: Int) {
        if (width == frameWidth && height == frameHeight) return
        frameWidth = width
        frameHeight = height
        place()
    }

    private var currentWriter = "initial"

    private fun moveCover(x: Float, y: Float, writer: String = "unknown") {
        currentWriter = writer
        coverX = x
        coverY = y
        place(writer = writer)
    }

    private fun place(apply: Boolean = true, writer: String = currentWriter) {
        val left = if (dockEnd) coverX + cover + margin - frameWidth else coverX - margin
        val top = coverY - margin
        val lp = compose.layoutParams
        if (lp != null && (lp.width != frameWidth || lp.height != frameHeight)) {
            lp.width = frameWidth
            lp.height = frameHeight
            compose.layoutParams = lp
        }
        params.width = frameWidth
        params.height = frameHeight
        params.x = left.roundToInt()
        params.y = top.roundToInt()
        if (apply && root.isAttachedToWindow) runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun cancelSnap() {
        snapAnimator?.cancel()
        snapAnimator = null
    }

    private fun snapTo(goalX: Float, goalY: Float, onEnd: (() -> Unit)? = null) {
        cancelSnap()
        val startX = coverX
        val startY = coverY
        val distance = hypot(goalX - startX, goalY - startY)
        val duration = (distance / density * 0.8f).toLong().coerceIn(200L, 350L)

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val curX = startX + (goalX - startX) * fraction
                val curY = startY + (goalY - startY) * fraction
                moveCover(curX, curY, "snap")
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    snapAnimator = null
                    moveCover(goalX, goalY, "snapEnd")
                    onEnd?.invoke()
                }
            })
        }
        snapAnimator = anim
        anim.start()
    }

    private fun makeRoom() {
        if (root.dragging) return
        val toY = clampY(coverY, frameHeight)
        if (toY == coverY) return
        snapTo(coverX, toY) {
            save()
        }
    }

    private fun onSeekBar(y: Float): Boolean =
        open && abs(y - px(PillMetrics.barY(slots.toFloat()))) <= px(PillMetrics.SeekReach)

    private fun onPill(x: Float, y: Float): Boolean =
        x >= 0 && x <= frameWidth && y >= 0 && y <= frameHeight

    private fun isJustCover(): Boolean =
        frameWidth == px(metrics.width(PillShape.COVER)) && frameHeight == px(metrics.height(PillShape.COVER, slots))

    private fun settle(velocityX: Float, velocityY: Float) {
        val targetDockEnd = coverX + cover / 2f + velocityX * FlingCarrySec > screenSize().x / 2f
        val goalY = clampY(coverY + velocityY * FlingCarrySec, frameHeight)
        val goalX = restX(targetDockEnd)
        landing = true
        snapTo(goalX, goalY) {
            if (dockEnd != targetDockEnd) {
                dockEnd = targetDockEnd
                listener.onDocked(dockEnd)
                place()
            }
            popOut()
            save()
        }
    }

    private fun popOut() {
        if (!landing) return
        landing = false
        listener.onSettling()
    }

    private fun vanish(drop: PillDrop) {
        targets.swallow(drop)
        targets.centerOf(drop)?.let { center ->
            snapTo(center.x - cover / 2f, center.y - cover / 2f)
        }
        compose.pivotX = if (dockEnd) frameWidth - margin - cover / 2f else margin + cover / 2f
        compose.pivotY = margin + cover / 2f
        compose.animate()
            .scaleX(0.2f)
            .scaleY(0.2f)
            .alpha(0f)
            .setDuration(VanishMs)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                targets.hide()
                listener.onDropped(drop)
            }
            .start()
    }

    private fun save() {
        val x = if (dockEnd) coverX + cover + margin - px(metrics.pillWidth) else coverX - margin
        listener.onMoved(x.roundToInt(), (coverY - margin).roundToInt())
    }

    private fun restX(end: Boolean): Float =
        if (end) (screenSize().x - px(EdgeGap) - cover).toFloat() else px(EdgeGap).toFloat()

    private fun clampY(y: Float, height: Int): Float {
        val bars = systemBars()
        val top = (bars.top + px(BarGap)).toFloat()
        val bottom = (screenSize().y - bars.bottom - px(BarGap) - (height - margin * 2)).toFloat()
        return if (bottom < top) top else y.coerceIn(top, bottom)
    }

    private fun screenSize(): Point = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.currentWindowMetrics.bounds
        Point(bounds.width(), bounds.height())
    } else {
        Point().also {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(it)
        }
    }

    private class Bars(val top: Int, val bottom: Int)

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun systemBars(): Bars {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Bars(insets.top, insets.bottom)
        }
        val resources = context.resources
        val status = resources.getIdentifier("status_bar_height", "dimen", "android")
        val navigation = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return Bars(
            top = if (status > 0) resources.getDimensionPixelSize(status) else px(24.dp),
            bottom = if (navigation > 0) resources.getDimensionPixelSize(navigation) else 0,
        )
    }

    private fun px(value: Dp): Int = (value.value * density).roundToInt()

    private inner class DragFrame : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val maxFling = MaxFlingDpPerSec * density
        private var tracker: VelocityTracker? = null
        private var downRawX = 0f
        private var downRawY = 0f
        private var startParamsX = 0
        private var startParamsY = 0
        private var startCoverX = 0f
        private var startCoverY = 0f
        private var magnet: PillDrop? = null
        private var seeking = false
        private var hasLongPressed = false

        private val longPressRunnable = Runnable {
            if (!dragging && !seeking) {
                hasLongPressed = true
                listener.onCoverLongPressed()
            }
        }

        var dragging = false
            private set

        init {
            clipChildren = false
        }

        private fun isTouchOnCover(rawX: Float, rawY: Float): Boolean {
            val pad = margin.toFloat()
            val minX = coverX - pad
            val maxX = coverX + cover + pad
            val minY = coverY - pad
            val maxY = coverY + cover + pad
            return rawX in minX..maxX && rawY in minY..maxY
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> {
                    removeCallbacks(longPressRunnable)
                    listener.onTouchedOutside()
                    return false
                }
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startParamsX = params.x
                    startParamsY = params.y
                    startCoverX = coverX
                    startCoverY = coverY
                    cancelSnap()
                    seeking = onSeekBar(event.y)
                    dragging = false
                    hasLongPressed = false
                    magnet = null
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    listener.onTouched()
                    if (!seeking && isTouchOnCover(event.rawX, event.rawY)) {
                        postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dist = hypot(event.rawX - downRawX, event.rawY - downRawY)
                    if (dist > slop) {
                        removeCallbacks(longPressRunnable)
                        if (!dragging && !seeking) {
                            startDrag(event)
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPressRunnable)
                    if (!dragging && !seeking && !hasLongPressed) {
                        if (isTouchOnCover(event.rawX, event.rawY)) {
                            listener.onCoverTapped()
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPressRunnable)
                }
            }
            track(event)
            if (!dragging) return super.dispatchTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val targetX = startParamsX + (event.rawX - downRawX)
                    val targetY = startParamsY + (event.rawY - downRawY)
                    drag(targetX, targetY)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drop(event.actionMasked == MotionEvent.ACTION_UP)
            }
            return true
        }

        private fun track(event: MotionEvent) {
            val onScreen = MotionEvent.obtain(event)
            onScreen.setLocation(event.rawX, event.rawY)
            tracker?.addMovement(onScreen)
            onScreen.recycle()
        }

        private fun startDrag(event: MotionEvent) {
            dragging = true
            cancelSnap()

            val currentDiscScreenX = params.x + (if (dockEnd) frameWidth - margin - cover else margin)
            val currentDiscScreenY = params.y + margin

            frameWidth = px(metrics.width(PillShape.COVER))
            frameHeight = px(metrics.height(PillShape.COVER, slots))

            val newParamsX = if (dockEnd) currentDiscScreenX + cover + margin - frameWidth else currentDiscScreenX - margin
            val newParamsY = currentDiscScreenY - margin

            params.x = newParamsX
            params.y = newParamsY
            startParamsX = newParamsX
            startParamsY = newParamsY
            startCoverX = currentDiscScreenX.toFloat()
            startCoverY = currentDiscScreenY.toFloat()
            coverX = currentDiscScreenX.toFloat()
            coverY = currentDiscScreenY.toFloat()

            params.width = frameWidth
            params.height = frameHeight
            val lp = compose.layoutParams
            if (lp != null) {
                lp.width = frameWidth
                lp.height = frameHeight
                compose.layoutParams = lp
            }
            if (root.isAttachedToWindow) runCatching { windowManager.updateViewLayout(root, params) }

            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            pendingSettle = null
            landing = false
            root.removeCallbacks(settleAnyway)
            listener.onDragStarted()
            targets.show(systemBars().bottom)
        }

        private fun drag(freeX: Float, freeY: Float) {
            params.x = freeX.roundToInt()
            params.y = freeY.roundToInt()
            coverX = if (dockEnd) (params.x + frameWidth - margin - cover).toFloat() else (params.x + margin).toFloat()
            coverY = (params.y + margin).toFloat()
            if (root.isAttachedToWindow) runCatching { windowManager.updateViewLayout(root, params) }
            val over = targets.hover(coverX + cover / 2f, coverY + cover / 2f)
            magnet = over
        }

        private fun drop(released: Boolean) {
            dragging = false
            val speed = tracker?.let {
                it.computeCurrentVelocity(1_000, maxFling)
                PointF(it.xVelocity, it.yVelocity)
            } ?: PointF()
            tracker?.recycle()
            tracker = null
            val drop = magnet.takeIf { released }
            magnet = null
            cancelSnap()
            if (drop != null) {
                vanish(drop)
                return
            }
            targets.hide()
            settle(speed.x, speed.y)
        }

        fun release() {
            removeCallbacks(longPressRunnable)
            tracker?.recycle()
            tracker = null
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            if (dragging) return
            cancelSnap()
            popOut()
            coverX = restX(dockEnd)
            coverY = clampY(coverY, frameHeight)
            place()
        }
    }
}

internal fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
} else {
    @Suppress("DEPRECATION")
    WindowManager.LayoutParams.TYPE_PHONE
}

private class OverlayOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val vmStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = vmStore

    fun start() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) return
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.INITIALIZED || registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
        vmStore.clear()
    }
}
