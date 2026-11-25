package io.rabbitcontrol.android.core.input

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * Zero-draw, unbuffered 120Hz/240Hz touch & hardware mouse input layer.
 *
 * - Uses `requestUnbufferedDispatch` to shave 4~8ms VSYNC touch batching latency.
 * - Integrates historical `MotionEvent` samples for accurate velocity curve acceleration.
 * - Updates [NativeVideoStageView]'s hardware-composited cursor via RenderNode translation
 *   (zero full-screen Canvas invalidation), showing steady arrow in Trackpad mode and hiding
 *   it in Direct-Touch mode while preserving lightweight tap ripple feedback.
 * - Micro-coalesces high-rate unbuffered `MouseMove` wire packets while flushing immediately
 *   before any button press/release or scroll event.
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
        set(value) {
            if (field != value) {
                field = value
                syncStageCursorVisibility()
            }
        }
    var sensitivity: Float = 1.15f
    var naturalScroll: Boolean = true
    var directTouchOffset: Boolean = true
    var rightClickModifierHeld: Boolean = false
    var dragModifierHeld: Boolean = false
        set(value) {
            val prev = field
            field = value
            if (prev && !value && leftButtonHeld) {
                flushPendingWireMove()
                leftButtonHeld = false
                onButton(0, false)
                if (gesture == Gesture.TrackpadDrag) gesture = Gesture.TrackpadMove
                if (gesture == Gesture.DirectDrag) gesture = Gesture.Consumed
            }
        }
    var watchOnly: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) cancelGesture()
                syncStageCursorVisibility()
            }
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

    // Wire MouseMove micro-coalescing (~200Hz max wire rate, 0ms first-move latency)
    private var lastWireNx = -1
    private var lastWireNy = -1
    private var lastWireSendNs = 0L
    private var hasPendingWireMove = false
    private var pendingWireNx = 0
    private var pendingWireNy = 0
    private var flushScheduled = false
    private val flushWireRunnable = Runnable {
        flushScheduled = false
        flushPendingWireMove()
    }

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

    private val choreographerCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!inertiaRunning) return
            val dtSec = if (lastFlingFrameNs == 0L) 0.008f
            else ((frameTimeNanos - lastFlingFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.032f)
            lastFlingFrameNs = frameTimeNanos

            val decay = kotlin.math.exp(-6.5f * dtSec)
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

    init {
        setWillNotDraw(true)
    }

    private fun shouldShowSteadyCursor(): Boolean =
        !watchOnly && inputMode == TouchInputMode.Trackpad

    private fun syncStageCursorVisibility() {
        if (!trackpadOnly) {
            stageProvider()?.setCursorVisibility(shouldShowSteadyCursor())
        }
    }

    fun syncNormalizedPosition(normX: Int, normY: Int) {
        accelerator.setNormalizedPosition(normX, normY)
        if (!trackpadOnly) {
            stageProvider()?.updateCursorOverlay(normX, normY, shouldShowSteadyCursor())
        }
    }

    fun currentNormalized(): Pair<Int, Int> = accelerator.currentNormX to accelerator.currentNormY

    fun pressLeft(pressed: Boolean) {
        if (watchOnly && pressed) return
        if (leftButtonHeld != pressed) {
            flushPendingWireMove()
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
        removeCallbacks(flushWireRunnable)
        flushScheduled = false
        flushPendingWireMove()
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

    private fun flushPendingWireMove() {
        if (!hasPendingWireMove) return
        hasPendingWireMove = false
        val nx = pendingWireNx
        val ny = pendingWireNy
        if (nx != lastWireNx || ny != lastWireNy) {
            lastWireNx = nx
            lastWireNy = ny
            lastWireSendNs = SystemClock.elapsedRealtimeNanos()
            onMove(nx, ny)
        }
    }

    private fun emitMove(nx: Int, ny: Int, forceImmediate: Boolean = false) {
        if (!trackpadOnly) {
            val stg = stageProvider()
            stg?.ensureCursorVisible(nx, ny)
            stg?.updateCursorOverlay(nx, ny, shouldShowSteadyCursor())
        }

        if (nx == lastWireNx && ny == lastWireNy && !hasPendingWireMove) {
            return
        }

        val nowNs = SystemClock.elapsedRealtimeNanos()
        if (forceImmediate || (nowNs - lastWireSendNs) >= MIN_WIRE_MOVE_INTERVAL_NS) {
            hasPendingWireMove = false
            lastWireNx = nx
            lastWireNy = ny
            lastWireSendNs = nowNs
            onMove(nx, ny)
        } else {
            pendingWireNx = nx
            pendingWireNy = ny
            hasPendingWireMove = true
            if (!flushScheduled) {
                flushScheduled = true
                postOnAnimation(flushWireRunnable)
            }
        }
    }

    private fun sendButtonImmediate(button: Int, pressed: Boolean) {
        flushPendingWireMove()
        onButton(button, pressed)
    }

    private fun performClickLeft() {
        flushPendingWireMove()
        onButton(0, true)
        onButton(0, false)
        triggerRipple(Color.WHITE)
    }

    private fun performClickRight() {
        flushPendingWireMove()
        onButton(1, true)
        onButton(1, false)
        triggerRipple(0xFF6094FF.toInt())
    }

    private fun emitWheel(rawLines: Int, horizontal: Boolean) {
        val dir = if (naturalScroll) rawLines else -rawLines
        val clamped = dir.coerceIn(-32768, 32767)
        if (clamped != 0) {
            flushPendingWireMove()
            onWheel(clamped, horizontal)
        }
    }

    private fun triggerRipple(color: Int) {
        if (trackpadOnly) return
        stageProvider()?.triggerTapRipple(accelerator.currentNormX, accelerator.currentNormY, color)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (watchOnly) {
            return handleWatchOnlyTouch(event)
        }

        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            return handleMouseMotionEvent(event)
        }

        val nowMs = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(event)
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
                        sendButtonImmediate(0, true)
                        gesture = Gesture.TrackpadDrag
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } else {
                        gesture = Gesture.TrackpadMove
                        if (!isSecondTap && !rightClickModifierHeld) {
                            handlerMain.postDelayed(longPressRunnable, longPressTimeout)
                        }
                    }
                } else {
                    mapDirectAndMove(event.x, event.y, forceImmediate = true)
                    if (dragModifierHeld && !rightClickModifierHeld) {
                        leftButtonHeld = true
                        sendButtonImmediate(0, true)
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
                flushPendingWireMove()
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
                    prevX = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f
                    prevY = (event.getY(0) + event.getY(1) + event.getY(2)) / 3f
                    prevEventMs = nowMs
                    leftButtonHeld = true
                    sendButtonImmediate(0, true)
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
                            sendButtonImmediate(0, true)
                            gesture = Gesture.TrackpadDrag
                            prevX = event.x
                            prevY = event.y
                            prevEventMs = nowMs
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    }

                    Gesture.TrackpadMove, Gesture.TrackpadDrag -> {
                        val span = minOf(width, height).coerceAtLeast(360).toFloat()
                        val histCount = event.historySize
                        var latestNx = accelerator.currentNormX
                        var latestNy = accelerator.currentNormY
                        var didStep = false

                        for (h in 0 until histCount) {
                            val hx = event.getHistoricalX(h)
                            val hy = event.getHistoricalY(h)
                            val ht = event.getHistoricalEventTime(h)
                            val step = stepSinglePointerTrackpad(hx, hy, ht, span)
                            if (step != null) {
                                latestNx = step.first
                                latestNy = step.second
                                didStep = true
                            }
                        }
                        val finalStep = stepSinglePointerTrackpad(event.x, event.y, nowMs, span)
                        if (finalStep != null) {
                            latestNx = finalStep.first
                            latestNy = finalStep.second
                            didStep = true
                        }
                        if (didStep) {
                            emitMove(latestNx, latestNy)
                        }
                    }

                    Gesture.DirectTap, Gesture.DirectDrag -> {
                        if (gesture == Gesture.DirectTap && hypot(event.x - downX, event.y - downY) > touchSlop) {
                            handlerMain.removeCallbacks(longPressRunnable)
                            movedBeyondSlop = true
                            leftButtonHeld = true
                            sendButtonImmediate(0, true)
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
                            val span = minOf(width, height).coerceAtLeast(360).toFloat()
                            for (h in 0 until event.historySize) {
                                val hcx = (event.getHistoricalX(0, h) + event.getHistoricalX(1, h) + event.getHistoricalX(2, h)) / 3f
                                val hcy = (event.getHistoricalY(0, h) + event.getHistoricalY(1, h) + event.getHistoricalY(2, h)) / 3f
                                val ht = event.getHistoricalEventTime(h)
                                val dt = (ht - prevEventMs).toFloat().coerceAtLeast(1f)
                                accelerator.applyDelta(hcx - prevX, hcy - prevY, dt, span, density, sensitivity)
                                prevX = hcx
                                prevY = hcy
                                prevEventMs = ht
                            }
                            val cx = (event.getX(0) + event.getX(1) + event.getX(2)) / 3f
                            val cy = (event.getY(0) + event.getY(1) + event.getY(2)) / 3f
                            val dt = (nowMs - prevEventMs).toFloat().coerceAtLeast(1f)
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
                flushPendingWireMove()
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
                flushPendingWireMove()
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
                            mapDirectAndMove(event.x, event.y, forceImmediate = true)
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

    private fun stepSinglePointerTrackpad(
        x: Float,
        y: Float,
        sampleMs: Long,
        span: Float,
    ): Pair<Int, Int>? {
        val dist = hypot(x - downX, y - downY)
        if (!movedBeyondSlop && dist > touchSlop) {
            movedBeyondSlop = true
            handlerMain.removeCallbacks(longPressRunnable)
            if ((pendingTapDrag || dragModifierHeld) && !leftButtonHeld && !rightClickModifierHeld) {
                leftButtonHeld = true
                sendButtonImmediate(0, true)
                gesture = Gesture.TrackpadDrag
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
        val dx = x - prevX
        val dy = y - prevY
        val dt = (sampleMs - prevEventMs).toFloat().coerceAtLeast(1f)
        prevX = x
        prevY = y
        prevEventMs = sampleMs
        if (movedBeyondSlop || abs(dx) > 0.25f || abs(dy) > 0.25f) {
            return accelerator.applyDelta(dx, dy, dt, span, density, sensitivity)
        }
        return null
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

    private fun mapDirectAndMove(viewX: Float, viewY: Float, forceImmediate: Boolean = false) {
        val stage = stageProvider() ?: return
        val offsetPx = if (directTouchOffset) 18f * density else 0f
        val (nx, ny) = stage.mapViewPointToNormalized(viewX, viewY - offsetPx) ?: return
        accelerator.setNormalizedPosition(nx, ny)
        emitMove(nx, ny, forceImmediate = forceImmediate)
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
        emitMove(nx, ny, forceImmediate = event.actionMasked != MotionEvent.ACTION_MOVE)
        when (event.actionMasked) {
            MotionEvent.ACTION_BUTTON_PRESS -> {
                mapActionButton(event.actionButton)?.let { sendButtonImmediate(it, true) }
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                mapActionButton(event.actionButton)?.let { sendButtonImmediate(it, false) }
            }
        }
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (watchOnly) return super.onGenericMotionEvent(event)
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) {
            if (event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
                requestUnbufferedDispatch(event)
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
                if (v != 0) emitWheel(v, false)
                if (h != 0) emitWheel(h, true)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        if (watchOnly) return true
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            val span = minOf(width, height).coerceAtLeast(480).toFloat()
            for (h in 0 until event.historySize) {
                accelerator.applyDelta(event.getHistoricalX(h), event.getHistoricalY(h), 4f, span, density, sensitivity)
            }
            val (nx, ny) = accelerator.applyDelta(event.x, event.y, 4f, span, density, sensitivity)
            emitMove(nx, ny)
        } else if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS) {
            mapActionButton(event.actionButton)?.let { sendButtonImmediate(it, true) }
        } else if (event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) {
            mapActionButton(event.actionButton)?.let { sendButtonImmediate(it, false) }
        } else if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL).roundToInt()
            val h = event.getAxisValue(MotionEvent.AXIS_HSCROLL).roundToInt()
            if (v != 0) emitWheel(v, false)
            if (h != 0) emitWheel(h, true)
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncStageCursorVisibility()
    }

    override fun onDetachedFromWindow() {
        cancelGesture()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelGesture()
    }

    private companion object {
        const val MIN_WIRE_MOVE_INTERVAL_NS = 5_000_000L // ~200Hz max wire MouseMove packet rate
    }
}
