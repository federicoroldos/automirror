package com.automirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service that injects the gestures coming from the car into the phone.
 * It's the only way to "touch" the screen without root.
 */
class TouchService : AccessibilityService() {

    companion object {
        var instance: TouchService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, TouchService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        private const val TAP_MS = 10L

        /** With no scroll events for this long, the finger is considered lifted. */
        private const val DRAG_END_DELAY_MS = 150L
        private const val DRAG_SEGMENT_MS = 16L
        private const val FLING_SEGMENT_MS = 30L
        private const val PINCH_DELAY_MS = 150L
        private const val PINCH_START_SPAN = 300f
    }

    private val handler = Handler(Looper.getMainLooper())

    // Drag: built up as one continuous stroke (the finger stays down between segments).
    private var dragPos: PointF? = null
    private var dragStroke: GestureDescription.StrokeDescription? = null
    private var dragInFlight = false
    private var dragEnding = false
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var flingVelocity: PointF? = null
    private val endDragRunnable = Runnable { endDrag(null) }

    // Pinch: the factor is accumulated and a single two-finger gesture is injected at the end.
    private var pinchFocus: PointF? = null
    private var pinchScale = 1f
    private val pinchRunnable = Runnable { dispatchPinch() }

    // Invisible window that forces the screen to rotate to landscape while mirroring.
    private var orientationView: View? = null
    private val onMirrorChanged: () -> Unit = { setForceLandscape(Mirror.isMirroring) }

    override fun onServiceConnected() {
        instance = this
        Mirror.addListener(onMirrorChanged)
        setForceLandscape(Mirror.isMirroring)
    }

    override fun onDestroy() {
        Mirror.removeListener(onMirrorChanged)
        setForceLandscape(false)
        instance = null
        super.onDestroy()
    }

    private fun setForceLandscape(on: Boolean) {
        val wm = getSystemService(WindowManager::class.java)
        if (on && orientationView == null) {
            val params = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                // The system uses the orientation of the topmost visible window that requests one,
                // so this wins even over apps locked to portrait.
                screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                gravity = Gravity.TOP or Gravity.START
            }
            orientationView = View(this).also { wm.addView(it, params) }
        } else if (!on) {
            orientationView?.let { wm.removeView(it) }
            orientationView = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    fun tap(p: PointF) {
        cancelDrag()
        val path = Path().apply { moveTo(p.x, p.y) }
        // The click fires when the finger is lifted: the shorter the tap, the less delay.
        dispatch(GestureDescription.StrokeDescription(path, 0, TAP_MS))
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** Moves the virtual finger by (dx, dy) phone pixels. The drag starts at the center. */
    fun dragBy(dx: Float, dy: Float) {
        if (dragEnding) return
        pendingDx += dx
        pendingDy += dy
        handler.removeCallbacks(endDragRunnable)
        handler.postDelayed(endDragRunnable, DRAG_END_DELAY_MS)
        pumpDrag()
    }

    /** Ends the drag by releasing the finger at this velocity (phone px/s). */
    fun fling(vx: Float, vy: Float) {
        handler.removeCallbacks(endDragRunnable)
        endDrag(PointF(vx, vy))
    }

    private fun endDrag(velocity: PointF?) {
        if (dragPos == null && velocity == null && pendingDx == 0f && pendingDy == 0f) return
        dragEnding = true
        flingVelocity = velocity
        pumpDrag()
    }

    private fun cancelDrag() {
        handler.removeCallbacks(endDragRunnable)
        dragPos = null
        dragStroke = null
        dragInFlight = false
        dragEnding = false
        pendingDx = 0f
        pendingDy = 0f
        flingVelocity = null
    }

    private fun pumpDrag() {
        if (dragInFlight) return
        val from = dragPos ?: screenCenter()
        val last = dragEnding
        if (!last && pendingDx == 0f && pendingDy == 0f) return

        var toX = from.x + pendingDx
        var toY = from.y + pendingDy
        var duration = DRAG_SEGMENT_MS
        flingVelocity?.let {
            toX += it.x * FLING_SEGMENT_MS / 1000f
            toY += it.y * FLING_SEGMENT_MS / 1000f
            duration = FLING_SEGMENT_MS
        }
        pendingDx = 0f
        pendingDy = 0f
        val to = clampToScreen(toX, toY)

        val path = Path().apply {
            moveTo(from.x, from.y)
            lineTo(to.x, to.y)
        }
        val previous = dragStroke
        val stroke = previous?.continueStroke(path, 0, duration, !last)
            ?: GestureDescription.StrokeDescription(path, 0, duration, !last)

        dragPos = to
        dragStroke = if (last) null else stroke
        dragInFlight = true
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    dragInFlight = false
                    if (last) cancelDrag() else pumpDrag()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    cancelDrag()
                }
            },
            handler
        )
        if (!ok) cancelDrag()
    }

    fun scale(focus: PointF, factor: Float) {
        cancelDrag()
        if (pinchFocus == null) pinchFocus = focus
        pinchScale *= factor
        handler.removeCallbacks(pinchRunnable)
        handler.postDelayed(pinchRunnable, PINCH_DELAY_MS)
    }

    private fun dispatchPinch() {
        val focus = pinchFocus ?: return
        val endSpan = (PINCH_START_SPAN * pinchScale).coerceIn(40f, 1200f)
        pinchFocus = null
        pinchScale = 1f

        fun finger(sign: Float): GestureDescription.StrokeDescription {
            val start = clampToScreen(focus.x + sign * PINCH_START_SPAN / 2, focus.y)
            val end = clampToScreen(focus.x + sign * endSpan / 2, focus.y)
            val path = Path().apply {
                moveTo(start.x, start.y)
                lineTo(end.x, end.y)
            }
            return GestureDescription.StrokeDescription(path, 0, 250)
        }
        dispatchGesture(
            GestureDescription.Builder().addStroke(finger(-1f)).addStroke(finger(1f)).build(),
            null, null
        )
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription) {
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun screenCenter(): PointF {
        val size = Mirror.phoneSize()
        return PointF(size.x / 2f, size.y / 2f)
    }

    private fun clampToScreen(x: Float, y: Float): PointF {
        val size = Mirror.phoneSize()
        return PointF(x.coerceIn(1f, size.x - 2f), y.coerceIn(1f, size.y - 2f))
    }
}
