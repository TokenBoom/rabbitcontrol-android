package io.rabbitcontrol.android.core.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.video.NativeVideoStageView
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Ultra-low-latency 120Hz native touch & vector cursor overlay.
 *
 * Supports:
 * - Trackpad mode: nonlinear acceleration, 1-finger move/tap/double-tap/tap-drag,
 *   3-finger drag, 2-finger tap right-click, 2-finger inertial scroll, 2-finger pinch zoom.
 * - Direct-touch mode: inverse zoom/pan coordinate mapping + optional fingertip offset.
 * - Dedicated portrait bottom-half trackpad mode (`trackpadOnly = true`).
 * - Hardware Bluetooth/USB mouse & trackpad passthrough (`SOURCE_MOUSE`).
 */
class PrecisionTouchLayer(
    context: Context,
    private val stageProvider: () -> NativeVideoStageView?,
    private val trackpadOnly: Boolean = false,
    private val onMove: (normX: Int, normY: Int) -> Unit,
    private val onButton: (button: Int, pressed: Boolean) -> Unit,
    private val onWheel: (delta: Int, horizontal: Boolean) -> Unit,
) : View(context) {

    private enum class Gesture {
        Idle,
        TrackpadMove,
        LongPressPending,
        TrackpadDrag,
        DirectTap,
        DirectDrag,
        TwoFingerPending,
        TwoFingerScroll,
        TwoFingerPinch,
        ThreeFingerDrag,
        Consumed,
    }

    var inputMode: TouchInputMode = TouchInputMode.Trackpad
    var sensitivity: Float = 1.15f
    var naturalScroll: Boolean = true
    var directTouchOffset: Boolean = true
    var rightClickModifierHeld: Boolean = false
    var dragModifierHeld: Boolean = false
        set(value) {
            val prev = field
            field = value
            if (prev && !value && leftButtonHeld) {
                leftButtonHeld = false
                onButton(0, false)
                if (gesture == Gesture.TrackpadDrag) gesture = Gesture.TrackpadMove
                if (gesture == Gesture.DirectDrag) gesture = Gesture.Consumed
            }
        }
    var watchOnly: Boolean = false
        set(value) {
            field = value
            if (value) cancelGesture()
            invalidate()
        }

    private val accelerator = PointerAccelerator()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val density = resources.displayMetrics.density

    private var gesture = Gesture.Idle
    private var downTimeMs = 0L
    private var downX = 0f
    private var downY = 0f
    private var prevX = 0f
    private var prevY = 0f
    private var prevEventMs = 0L
    private var movedBeyondSlop = false

    private var lastTapUpTimeMs = 0L
    private var lastTapUpX = 0f
    private var lastTapUpY = 0f
    private var pendingTapDrag = false
    private var leftButtonHeld = false

    // Two-finger scroll & pinch tracking
    private var twoDownTimeMs = 0L
    private var twoDownCenterX = 0f
    private var twoDownCenterY = 0f
    private var twoDownSpan = 0f
    private var twoCenterX = 0f
    private var twoCenterY = 0f
    private var twoSpan = 0f
    private var scrollAccumX = 0f
    private var scrollAccumY = 0f
    private var twoMoved = false

    // Inertial scroll via Choreographer
    private var velocityTracker: VelocityTracker? = null
    private var inertiaVx = 0f
    private var inertiaVy = 0f
    private var inertiaRunning = false
    private var lastFlingFrameNs = 0L

    private val handlerMain = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (watchOnly) return@Runnable
        if (!movedBeyondSlop) {
            when (gesture) {
                Gesture.TrackpadMove -> {
                    gesture = Gesture.LongPressPending
                    triggerRipple(0xFF3370FF.toInt())
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                Gesture.DirectTap -> {
                    performClickRight()
                    gesture = Gesture.Consumed
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
                else -> Unit
            }
        }
    }

    // Crisp vector cursor & tap feedback
    private var rippleX = -1f
    private var rippleY = -1f
    private var rippleAlpha = 0f
    private var rippleColor = Color.WHITE

    private val arrowPath = Path()
    private val cursorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val cursorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF10141A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val choreographerCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!inertiaRunning) return
            val dtSec = if (lastFlingFrameNs == 0L) 0.008f
            else ((frameTimeNanos - lastFlingFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
            lastFlingFrameNs = frameTimeNanos

            val decay = Math.exp((-6.5 * dtSec)).toFloat()
            inertiaVx *= decay
            inertiaVy *= decay

            val stepPx = 18f * density
            scrollAccumX += inertiaVx * dtSec
            scrollAccumY += inertiaVy * dtSec

            val hLines = (scrollAccumX / stepPx).toInt()
            val vLines = (scrollAccumY / stepPx).toInt()
            if (hLines != 0) {
                emitWheel(hLines, horizontal = true)
                scrollAccumX -= hLines * stepPx
            }
            if (vLines != 0) {
                emitWheel(vLines, horizontal = false)
                scrollAccumY -= vLines * stepPx
            }

            if (hypot(inertiaVx, inertiaVy) < 90f * density) {
                stopInertia()
            } else {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun syncNormalizedPosition(normX: Int, normY: Int) {
        accelerator.setNormalizedPosition(normX, normY)
        postInvalidateOnAnimation()
    }

    fun currentNormalized(): Pair<Int, Int> = accelerator.currentNormX to accelerator.currentNormY

    fun pressLeft(pressed: Boolean) {
        if (watchOnly && pressed) return
        if (leftButtonHeld != pressed) {
            leftButtonHeld = pressed
            onButton(0, pressed)
            if (pressed) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun clickRight() {
        if (watchOnly) return
        performClickRight()
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun cancelGesture() {
        handlerMain.removeCallbacks(longPressRunnable)
        stopInertia()
        releaseVelocityTracker()
        if (leftButtonHeld) {
            leftButtonHeld = false
            onButton(0, false)
        }
        pendingTapDrag = false
        gesture = Gesture.Idle
    }

    private fun stopInertia() {
        inertiaRunning = false
        lastFlingFrameNs = 0L
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun emitMove(nx: Int, ny: Int) {
        onMove(nx, ny)
        if (!trackpadOnly) {
            stageProvider()?.ensureCursorVisible(nx, ny)
        }
        postInvalidateOnAnimation()
    }

    private fun performClickLeft() {
        onButton(0, true)
        onButton(0, false)
        triggerRipple(Color.WHITE)
    }

    private fun performClickRight() {
        onButton(1, true)
        onButton(1, false)
        triggerRipple(0xFF6094FF.toInt())
    }

    private fun emitWheel(rawLines: Int, horizontal: Boolean) {
        val dir = if (naturalScroll) rawLines else -rawLines
        val clamped = dir.coerceIn(-32768, 32767)
        if (clamped != 0) onWheel(clamped, horizontal)
    }

    private fun triggerRipple(color: Int) {
        if (trackpadOnly) return
        val stage = stageProvider() ?: return
        val (sx, sy) = stage.mapNormalizedToViewPoint(accelerator.currentNormX, accelerator.currentNormY)
        rippleX = sx
        rippleY = sy
        rippleColor = color
        rippleAlpha = 1f
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (watchOnly) {
            return handleWatchOnlyTouch(event)
        }

        // Physical mouse routed via MotionEvent on older APIs
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return handleMouseMotionEvent(event)
        }

        val nowMs = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stopInertia()
                handlerMain.removeCallbacks(longPressRunnable)
                releaseVelocityTracker()
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }

                downTimeMs = nowMs
                downX = event.x
                downY = event.y
                prevX = event.x
                prevY = event.y
                prevEventMs = nowMs
                movedBeyondSlop = false

                val useTrackpad = trackpadOnly || inputMode == TouchInputMode.Trackpad
                if (useTrackpad) {
                    val isSecondTap = (nowMs - lastTapUpTimeMs) <= doubleTapTimeout &&
                        hypot(event.x - lastTapUpX, event.y - lastTapUpY) < 42f * density
                    pendingTapDrag = isSecondTap
                    if (dragModifierHeld && !rightClickModifierHeld) {
                        leftButtonHeld = true
                        onButton(0, true)
                        gesture = Gesture.TrackpadDrag
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } else {
                        gesture = Gesture.TrackpadMove
                        if (!isSecondTap && !rightClickModifierHeld) {
                            handlerMain.postDelayed(longPressRunnable, longPressTimeout)
                        }
                    }
                } else {
                    mapDirectAndMove(event.x, event.y)
                    if (dragModifierHeld && !rightClickModifierHeld) {
                        leftButtonHeld = true
                        onButton(0, true)
                        gesture = Gesture.DirectDrag
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } else {
                        gesture = Gesture.DirectTap
                        if (!rightClickModifierHeld) {
                            handlerMain.postDelayed(longPressRunnable, longPressTimeout)
                        }
                    }
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                handlerMain.removeCallbacks(longPressRunnable)
                if (leftButtonHeld) {
                    leftButtonHeld = false
                    onButton(0, false)
                }
                pendingTapDrag = false
                if (event.pointerCount == 2) {
                    twoDownTimeMs = nowMs
                    twoCenterX = (event.getX(0) + event.getX(1)) * 0.5f
                    twoCenterY = (event.getY(0) + event.getY(1)) * 0.5f
                    twoSpan = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                    twoDownCenterX = twoCenterX
                    twoDownCenterY = twoCenterY
                    twoDownSpan = twoSpan
                    scrollAccumX = 0f
                    scrollAccumY = 0f
                    twoMoved = false
                    gesture = Gesture.TwoFingerPending
                    velocityTracker?.addMovement(event)
                } else if (event.pointerCount == 3 && (trackpadOnly || inputMode == TouchInputMode.Trackpad)) {
                    // 3-finger drag (Mac accessibility style)
                    prevX = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f
                    prevY = (event.getY(0) + event.getY(1) + event.getY(2)) / 3f
                    prevEventMs = nowMs
                    leftButtonHeld = true
                    onButton(0, true)
                    gesture = Gesture.ThreeFingerDrag
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } else {
                    gesture = Gesture.Consumed
                }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                when (gesture) {
                    Gesture.LongPressPending -> {
                        val dist = hypot(event.x - downX, event.y - downY)
                        if (dist > touchSlop * 0.6f) {
                            movedBeyondSlop = true
                            leftButtonHeld = true
                            onButton(0, true)
                            gesture = Gesture.TrackpadDrag
                            prevX = event.x
                            prevY = event.y
                            prevEventMs = nowMs
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }

                    Gesture.TrackpadMove, Gesture.TrackpadDrag -> {
                        val dist = hypot(event.x - downX, event.y - downY)
                        if (!movedBeyondSlop && dist > touchSlop) {
                            movedBeyondSlop = true
                            handlerMain.removeCallbacks(longPressRunnable)
                            if ((pendingTapDrag || dragModifierHeld) && !leftButtonHeld && !rightClickModifierHeld) {
                                leftButtonHeld = true
                                onButton(0, true)
                                gesture = Gesture.TrackpadDrag
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                        val dx = event.x - prevX
                        val dy = event.y - prevY
                        val dt = (nowMs - prevEventMs).toFloat().coerceAtLeast(1f)
                        prevX = event.x
                        prevY = event.y
                        prevEventMs = nowMs
                        if (movedBeyondSlop || abs(dx) > 0.25f || abs(dy) > 0.25f) {
                            val span = minOf(width, height).coerceAtLeast(360).toFloat()
                            val (nx, ny) = accelerator.applyDelta(dx, dy, dt, span, density, sensitivity)
                            emitMove(nx, ny)
                        }
                    }

                    Gesture.DirectTap, Gesture.DirectDrag -> {
                        if (gesture == Gesture.DirectTap && hypot(event.x - downX, event.y - downY) > touchSlop) {
                            handlerMain.removeCallbacks(longPressRunnable)
                            movedBeyondSlop = true
                            leftButtonHeld = true
                            onButton(0, true)
                            gesture = Gesture.DirectDrag
                        }
                        if (gesture == Gesture.DirectDrag) {
                            mapDirectAndMove(event.x, event.y)
                        }
                    }

                    Gesture.TwoFingerPending, Gesture.TwoFingerScroll, Gesture.TwoFingerPinch -> {
                        if (event.pointerCount == 2) {
                            val cx = (event.getX(0) + event.getX(1)) * 0.5f
                            val cy = (event.getY(0) + event.getY(1)) * 0.5f
                            val span = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))

                            if (gesture == Gesture.TwoFingerPending) {
                                val centerMove = hypot(cx - twoDownCenterX, cy - twoDownCenterY)
                                val spanDiff = abs(span - twoDownSpan)
                                if (!trackpadOnly && spanDiff > touchSlop * 1.2f && spanDiff > centerMove * 0.7f) {
                                    gesture = Gesture.TwoFingerPinch
                                    twoMoved = true
                                    twoCenterX = cx
                                    twoCenterY = cy
                                    twoSpan = span
                                } else if (centerMove > touchSlop * 0.8f) {
                                    gesture = Gesture.TwoFingerScroll
                                    twoMoved = true
                                    twoCenterX = cx
                                    twoCenterY = cy
                                    twoSpan = span
                                }
                            } else {
                                if (gesture == Gesture.TwoFingerPinch && !trackpadOnly) {
                                    val stage = stageProvider()
                                    if (stage != null && twoSpan > 20f) {
                                        val factor = span / twoSpan
                                        val newScale = (stage.zoomScale * factor).coerceIn(1f, 4f)
                                        val dx = cx - twoCenterX
                                        val dy = cy - twoCenterY
                                        stage.setZoomAndPan(newScale, stage.panX + dx, stage.panY + dy)
                                        invalidate()
                                    }
                                } else if (gesture == Gesture.TwoFingerScroll) {
                                    val stepPx = 18f * density
                                    scrollAccumX += (cx - twoCenterX)
                                    scrollAccumY += (cy - twoCenterY)
                                    val hLines = (scrollAccumX / stepPx).toInt()
                                    val vLines = (scrollAccumY / stepPx).toInt()
                                    if (hLines != 0) {
                                        emitWheel(hLines, horizontal = true)
                                        scrollAccumX -= hLines * stepPx
                                    }
                                    if (vLines != 0) {
                                        emitWheel(vLines, horizontal = false)
                                        scrollAccumY -= vLines * stepPx
                                    }
                                }
                                twoCenterX = cx
                                twoCenterY = cy
                                twoSpan = span
                            }
                        }
                    }

                    Gesture.ThreeFingerDrag -> {
                        if (event.pointerCount >= 3) {
                            val cx = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f
                            val cy = (event.getY(0) + event.getY(1) + event.getY(2)) / 3f
                            val dt = (nowMs - prevEventMs).toFloat().coerceAtLeast(1f)
                            val span = minOf(width, height).coerceAtLeast(360).toFloat()
                            val (nx, ny) = accelerator.applyDelta(cx - prevX, cy - prevY, dt, span, density, sensitivity)
                            prevX = cx
                            prevY = cy
                            prevEventMs = nowMs
                            emitMove(nx, ny)
                        }
                    }

                    else -> Unit
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                when (gesture) {
                    Gesture.TwoFingerPending -> {
                        if (!twoMoved && (nowMs - twoDownTimeMs) < 280L) {
                            performClickRight()
                            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        gesture = Gesture.Consumed
                    }
                    Gesture.TwoFingerScroll -> {
                        velocityTracker?.computeCurrentVelocity(1000, 6000f * density)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val vy = velocityTracker?.yVelocity ?: 0f
                        if (hypot(vx, vy) > 220f * density) {
                            inertiaVx = vx
                            inertiaVy = vy
                            inertiaRunning = true
                            lastFlingFrameNs = 0L
                            Choreographer.getInstance().postFrameCallback(choreographerCallback)
                        }
                        gesture = Gesture.Consumed
                    }
                    Gesture.ThreeFingerDrag -> {
                        if (leftButtonHeld) {
                            leftButtonHeld = false
                            onButton(0, false)
                        }
                        gesture = Gesture.Consumed
                    }
                    else -> gesture = Gesture.Consumed
                }
            }

            MotionEvent.ACTION_UP -> {
                handlerMain.removeCallbacks(longPressRunnable)
                when (gesture) {
                    Gesture.LongPressPending -> {
                        performClickRight()
                        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                    Gesture.TrackpadMove -> {
                        if (!movedBeyondSlop) {
                            if (rightClickModifierHeld) {
                                performClickRight()
                                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            } else {
                                performClickLeft()
                                lastTapUpTimeMs = nowMs
                                lastTapUpX = event.x
                                lastTapUpY = event.y
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                    }
                    Gesture.TrackpadDrag, Gesture.DirectDrag, Gesture.ThreeFingerDrag -> {
                        if (leftButtonHeld) {
                            leftButtonHeld = false
                            onButton(0, false)
                        }
                    }
                    Gesture.DirectTap -> {
                        if (!movedBeyondSlop) {
                            mapDirectAndMove(event.x, event.y)
                            if (rightClickModifierHeld) {
                                performClickRight()
                                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            } else {
                                performClickLeft()
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        }
                    }
                    else -> Unit
                }
                releaseVelocityTracker()
                pendingTapDrag = false
                gesture = Gesture.Idle
                performClick()
            }

            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun handleWatchOnlyTouch(event: MotionEvent): Boolean {
        if (trackpadOnly) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) {
                twoCenterX = (event.getX(0) + event.getX(1)) * 0.5f
                twoCenterY = (event.getY(0) + event.getY(1)) * 0.5f
                twoSpan = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                gesture = Gesture.TwoFingerPinch
            }
            MotionEvent.ACTION_MOVE -> if (gesture == Gesture.TwoFingerPinch && event.pointerCount == 2) {
                val stage = stageProvider() ?: return true
                val cx = (event.getX(0) + event.getX(1)) * 0.5f
                val cy = (event.getY(0) + event.getY(1)) * 0.5f
                val span = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                if (twoSpan > 20f) {
                    val factor = span / twoSpan
                    stage.setZoomAndPan((stage.zoomScale * factor).coerceIn(1f, 4f), stage.panX + (cx - twoCenterX), stage.panY + (cy - twoCenterY))
                }
                twoCenterX = cx
                twoCenterY = cy
                twoSpan = span
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> gesture = Gesture.Idle
        }
        return true
    }

    private fun mapDirectAndMove(viewX: Float, viewY: Float) {
        val stage = stageProvider() ?: return
        val offsetPx = if (directTouchOffset) 18f * density else 0f
        val (nx, ny) = stage.mapViewPointToNormalized(viewX, viewY - offsetPx) ?: return
        accelerator.setNormalizedPosition(nx, ny)
        emitMove(nx, ny)
    }

    private fun handleMouseMotionEvent(event: MotionEvent): Boolean {
        if (watchOnly) return true
        val stage = stageProvider()
        val (nx, ny) = if (stage != null && !trackpadOnly) {
            stage.mapViewPointToNormalized(event.x, event.y) ?: currentNormalized()
        } else {
            currentNormalized()
        }
        accelerator.setNormalizedPosition(nx, ny)
        emitMove(nx, ny)
        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                mapActionButton(event.actionButton)?.let { onButton(it, true) }
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                mapActionButton(event.actionButton)?.let { onButton(it, false) }
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (watchOnly) return super.onGenericMotionEvent(event)
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) {
            if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                val stage = stageProvider()
                val (nx, ny) = if (stage != null && !trackpadOnly) {
                    stage.mapViewPointToNormalized(event.x, event.y) ?: return true
                } else return true
                accelerator.setNormalizedPosition(nx, ny)
                emitMove(nx, ny)
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
                val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL).roundToInt()
                val h = event.getAxisValue(MotionEvent.AXIS_HSCROLL).roundToInt()
                if (v != 0) onWheel(v, false)
                if (h != 0) onWheel(h, true)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        if (watchOnly) return true
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val dx = event.x
            val dy = event.y
            val span = minOf(width, height).coerceAtLeast(480).toFloat()
            val (nx, ny) = accelerator.applyDelta(dx, dy, 8f, span, density, sensitivity)
            emitMove(nx, ny)
        } else if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
            mapActionButton(event.actionButton)?.let { onButton(it, true) }
        } else if (event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) {
            mapActionButton(event.actionButton)?.let { onButton(it, false) }
        } else if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL).roundToInt()
            val h = event.getAxisValue(MotionEvent.AXIS_HSCROLL).roundToInt()
            if (v != 0) onWheel(v, false)
            if (h != 0) onWheel(h, true)
        }
        return true
    }

    private fun mapActionButton(btn: Int): Int? = when (btn) {
        MotionEvent.BUTTON_PRIMARY -> 0
        MotionEvent.BUTTON_SECONDARY -> 1
        MotionEvent.BUTTON_TERTIARY -> 2
        MotionEvent.BUTTON_BACK -> 3
        MotionEvent.BUTTON_FORWARD -> 4
        else -> null
    }

    override fun onDraw(canvas: Canvas) {
        if (trackpadOnly) return
        val stage = stageProvider() ?: return
        if (!watchOnly) {
            val (cx, cy) = stage.mapNormalizedToViewPoint(accelerator.currentNormX, accelerator.currentNormY)
            drawCursorArrow(canvas, cx, cy)
        }
        if (rippleAlpha > 0.02f) {
            ripplePaint.color = rippleColor
            ripplePaint.alpha = (rippleAlpha * 210).toInt().coerceIn(0, 255)
            val radius = (8f + (1f - rippleAlpha) * 20f) * density
            canvas.drawCircle(rippleX, rippleY, radius, ripplePaint)
            rippleAlpha *= 0.84f
            postInvalidateOnAnimation()
        }
    }

    private fun drawCursorArrow(canvas: Canvas, x: Float, y: Float) {
        val s = density
        arrowPath.reset()
        arrowPath.moveTo(x, y)
        arrowPath.lineTo(x, y + 17f * s)
        arrowPath.lineTo(x + 4.3f * s, y + 13.2f * s)
        arrowPath.lineTo(x + 7.5f * s, y + 19.6f * s)
        arrowPath.lineTo(x + 10.1f * s, y + 18.3f * s)
        arrowPath.lineTo(x + 6.9f * s, y + 12.0f * s)
        arrowPath.lineTo(x + 12.2f * s, y + 11.8f * s)
        arrowPath.close()
        canvas.drawPath(arrowPath, cursorFillPaint)
        canvas.drawPath(arrowPath, cursorStrokePaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelGesture()
    }
}
