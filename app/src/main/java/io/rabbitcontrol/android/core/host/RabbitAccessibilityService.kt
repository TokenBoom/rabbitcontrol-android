package io.rabbitcontrol.android.core.host

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot

/**
 * Accessibility service enabling touch taps, drags, scroll gestures, system navigation keys,
 * text editing, and screen locking when this Android phone acts as a remote-controlled host.
 */
class RabbitAccessibilityService : AccessibilityService() {

    private var cursorX = 540f
    private var cursorY = 1200f
    private var leftButtonDown = false
    private var downX = 0f
    private var downY = 0f
    private var downTimeMs = 0L
    private var dragPath: Path? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        val (w, h) = screenSizePx()
        cursorX = w * 0.5f
        cursorY = h * 0.5f
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No continuous event scraping needed; gesture dispatch is driven on demand.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        _isConnected.value = false
        super.onDestroy()
    }

    fun handleMouseMove(normX: Int, normY: Int) {
        val (px, py) = denormalize(normX, normY)
        cursorX = px
        cursorY = py
        if (leftButtonDown) {
            dragPath?.lineTo(px, py)
        }
    }

    fun handleMouseButton(button: Int, pressed: Boolean) {
        when (button) {
            0 -> { // Left button -> tap or drag stroke
                if (pressed) {
                    leftButtonDown = true
                    downX = cursorX
                    downY = cursorY
                    downTimeMs = System.currentTimeMillis()
                    dragPath = Path().apply { moveTo(downX, downY) }
                } else if (leftButtonDown) {
                    leftButtonDown = false
                    val path = dragPath
                    dragPath = null
                    val dist = hypot(cursorX - downX, cursorY - downY)
                    if (dist < 18f || path == null) {
                        dispatchTap(cursorX, cursorY)
                    } else {
                        path.lineTo(cursorX, cursorY)
                        val dur = (System.currentTimeMillis() - downTimeMs).coerceIn(110L, 1200L)
                        dispatchPath(path, dur)
                    }
                }
            }
            1, 3 -> if (!pressed) { // Right / Back -> Android Back
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            2 -> if (!pressed) { // Middle -> Home
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            4 -> if (!pressed) { // X2 -> Recents
                performGlobalAction(GLOBAL_ACTION_RECENTS)
            }
        }
    }

    fun handleMouseWheel(delta: Int, horizontal: Boolean) {
        if (delta == 0) return
        val (w, h) = screenSizePx()
        val step = (delta.coerceIn(-8, 8) * 110f)
        val sx = cursorX.coerceIn(20f, (w - 20).toFloat().coerceAtLeast(20f))
        val sy = cursorY.coerceIn(20f, (h - 20).toFloat().coerceAtLeast(20f))
        val ex = if (horizontal) (sx + step).coerceIn(10f, (w - 10).toFloat().coerceAtLeast(10f)) else sx
        val ey = if (horizontal) sy else (sy + step).coerceIn(10f, (h - 10).toFloat().coerceAtLeast(10f))
        if (hypot(ex - sx, ey - sy) < 8f) return
        val p = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        dispatchPath(p, 110L)
    }

    fun handleKey(scancode: Int, pressed: Boolean, modifiers: Int) {
        if (!pressed) return
        when (scancode) {
            0x01 -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
            0xE05B, 0xE05C -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
            0x1C -> { // Enter
                val focused = findFocusedEditableNode()
                if (focused != null) {
                    if (Build.VERSION.SDK_INT >= 30) {
                        focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                    } else {
                        appendTextToNode(focused, "\n")
                    }
                    focused.recycle()
                }
                return
            }
            0x0E -> { // Backspace
                val focused = findFocusedEditableNode()
                if (focused != null) {
                    val cur = focused.text?.toString().orEmpty()
                    if (cur.isNotEmpty()) {
                        val bundle = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur.dropLast(1))
                        }
                        focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    }
                    focused.recycle()
                }
                return
            }
        }
        val shifted = (modifiers and 0x22) != 0
        val ch = scancodeToAscii(scancode, shifted) ?: return
        val focused = findFocusedEditableNode() ?: return
        appendTextToNode(focused, ch.toString())
        focused.recycle()
    }

    fun handleLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    private fun appendTextToNode(node: AccessibilityNodeInfo, suffix: String) {
        val existing = node.text?.toString().orEmpty()
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + suffix)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus != null && focus.isEditable) return focus
        focus?.recycle()
        return null
    }

    private fun dispatchTap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        dispatchPath(p, 45L)
    }

    private fun dispatchPath(path: Path, durationMs: Long) {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(20L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun denormalize(normX: Int, normY: Int): Pair<Float, Float> {
        val (w, h) = screenSizePx()
        val x = ((normX.coerceIn(0, 65535) / 65535f) * (w - 1).coerceAtLeast(1)).coerceIn(0f, (w - 1).toFloat())
        val y = ((normY.coerceIn(0, 65535) / 65535f) * (h - 1).coerceAtLeast(1)).coerceIn(0f, (h - 1).toFloat())
        return x to y
    }

    private fun screenSizePx(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            if (b.width() > 0 && b.height() > 0) return b.width() to b.height()
        }
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(dm)
        return (if (dm.widthPixels > 0) dm.widthPixels else 1080) to
            (if (dm.heightPixels > 0) dm.heightPixels else 2400)
    }

    private fun scancodeToAscii(sc: Int, shifted: Boolean): Char? = when (sc) {
        0x1E -> if (shifted) 'A' else 'a'
        0x30 -> if (shifted) 'B' else 'b'
        0x2E -> if (shifted) 'C' else 'c'
        0x20 -> if (shifted) 'D' else 'd'
        0x12 -> if (shifted) 'E' else 'e'
        0x21 -> if (shifted) 'F' else 'f'
        0x22 -> if (shifted) 'G' else 'g'
        0x23 -> if (shifted) 'H' else 'h'
        0x17 -> if (shifted) 'I' else 'i'
        0x24 -> if (shifted) 'J' else 'j'
        0x25 -> if (shifted) 'K' else 'k'
        0x26 -> if (shifted) 'L' else 'l'
        0x32 -> if (shifted) 'M' else 'm'
        0x31 -> if (shifted) 'N' else 'n'
        0x18 -> if (shifted) 'O' else 'o'
        0x19 -> if (shifted) 'P' else 'p'
        0x10 -> if (shifted) 'Q' else 'q'
        0x13 -> if (shifted) 'R' else 'r'
        0x1F -> if (shifted) 'S' else 's'
        0x14 -> if (shifted) 'T' else 't'
        0x16 -> if (shifted) 'U' else 'u'
        0x2F -> if (shifted) 'V' else 'v'
        0x11 -> if (shifted) 'W' else 'w'
        0x2D -> if (shifted) 'X' else 'x'
        0x15 -> if (shifted) 'Y' else 'y'
        0x2C -> if (shifted) 'Z' else 'z'
        0x02 -> if (shifted) '!' else '1'
        0x03 -> if (shifted) '@' else '2'
        0x04 -> if (shifted) '#' else '3'
        0x05 -> if (shifted) '$' else '4'
        0x06 -> if (shifted) '%' else '5'
        0x07 -> if (shifted) '^' else '6'
        0x08 -> if (shifted) '&' else '7'
        0x09 -> if (shifted) '*' else '8'
        0x0A -> if (shifted) '(' else '9'
        0x0B -> if (shifted) ')' else '0'
        0x39 -> ' '
        0x0C -> if (shifted) '_' else '-'
        0x0D -> if (shifted) '+' else '='
        0x33 -> if (shifted) '<' else ','
        0x34 -> if (shifted) '>' else '.'
        0x35 -> if (shifted) '?' else '/'
        0x27 -> if (shifted) ':' else ';'
        0x28 -> if (shifted) '"' else '\''
        else -> null
    }

    companion object {
        @Volatile
        var instance: RabbitAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expected = ComponentName(context, RabbitAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
