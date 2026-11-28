package com.rabbitcontrol.android.input

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.rabbitcontrol.android.session.Core
import kotlin.math.abs
import kotlin.math.hypot

/** Transparent input layer with exactly the same bounds as the video SurfaceView. */
internal class TouchPad(context: Context, private val onEvent: (Int, Int, Int) -> Unit) : View(context) {
    private enum class Gesture { Idle, Tap, Drag, Scroll, Consumed }
    private var gesture = Gesture.Idle
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val lineHeight = 32 * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var scrollY = 0f
    private var cursorX = -1f
    private var cursorY = -1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2 * resources.displayMetrics.density
    }
    private val longPress = Runnable {
        if (gesture == Gesture.Tap) {
            button(1, true)
            button(1, false)
            gesture = Gesture.Consumed
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }

    private fun move(x: Float, y: Float) {
        val pos = Core.pointer(x, y, width, height)
        onEvent(0, pos ushr 16, pos and 65535)
        cursorX = (pos ushr 16) / 65535f * width
        cursorY = (pos and 65535) / 65535f * height
        postInvalidateOnAnimation()
    }

    private fun button(id: Int, pressed: Boolean) = onEvent(1, id, if (pressed) 1 else 0)

    fun cancelGesture() {
        removeCallbacks(longPress)
        if (gesture == Gesture.Drag) button(0, false)
        gesture = Gesture.Idle
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelGesture()
                downX = event.x
                downY = event.y
                move(downX, downY)
                gesture = Gesture.Tap
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelGesture()
                gesture = if (event.pointerCount == 2) Gesture.Scroll else Gesture.Consumed
                scrollY = (event.getY(0) + event.getY(1)) / 2
            }
            MotionEvent.ACTION_MOVE -> when (gesture) {
                Gesture.Tap, Gesture.Drag -> {
                    if (gesture == Gesture.Tap && hypot(event.x - downX, event.y - downY) > slop) {
                        removeCallbacks(longPress)
                        button(0, true)
                        gesture = Gesture.Drag
                    }
                    if (gesture == Gesture.Drag) move(event.x, event.y)
                }
                Gesture.Scroll -> if (event.pointerCount == 2) {
                    val y = (event.getY(0) + event.getY(1)) / 2
                    val lines = ((y - scrollY) / lineHeight).toInt()
                    if (abs(lines) > 0) {
                        onEvent(2, lines.coerceIn(-32768, 32767), 0)
                        scrollY += lines * lineHeight
                    }
                }
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> {
                cancelGesture()
                gesture = Gesture.Consumed
            }
            MotionEvent.ACTION_UP -> {
                if (gesture == Gesture.Tap) {
                    button(0, true)
                    button(0, false)
                    performClick()
                } else if (gesture == Gesture.Drag) {
                    move(event.x, event.y)
                }
                cancelGesture()
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDraw(canvas: Canvas) {
        if (cursorX >= 0f) canvas.drawCircle(cursorX, cursorY, 9 * resources.displayMetrics.density, paint)
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
