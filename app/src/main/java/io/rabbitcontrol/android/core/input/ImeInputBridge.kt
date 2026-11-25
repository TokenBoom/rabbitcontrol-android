package io.rabbitcontrol.android.core.input

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Transparent 1x1 IME proxy view for remote desktop sessions.
 * - Pure ASCII / hardware KeyEvents -> PS/2 Set 1 scancodes immediately.
 * - Non-ASCII / CJK / Emoji IME commits -> buffered briefly (35ms), sent silently via core
 *   clipboard channel (without touching Android system clipboard), followed by remote Cmd+V / Ctrl+V.
 */
class ImeInputBridge(
    context: Context,
    private val isMacHost: () -> Boolean,
    private val stickyModifiersProvider: () -> Int,
    private val onConsumeStickyModifiers: () -> Unit,
    private val onSendKey: (scancode: Int, pressed: Boolean, modifiers: Int) -> Unit,
    private val onPushSilentIme: (String) -> Unit,
) : View(context) {

    private val handlerMain = Handler(Looper.getMainLooper())
    private val imeBuffer = StringBuilder()
    private val flushImeRunnable = Runnable { flushSilentImeBuffer() }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun showSoftKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    handleCommittedText(text.toString())
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength.coerceAtMost(8)) {
                        tapScancode(ScancodeMapper.SC_BACKSPACE)
                    }
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                return dispatchRemoteKeyEvent(event) || super.sendKeyEvent(event)
            }
        }
    }

    fun dispatchRemoteKeyEvent(event: KeyEvent): Boolean {
        val sc = ScancodeMapper.keyCodeToScancode(event.keyCode) ?: return false
        val pressed = event.action == KeyEvent.ACTION_DOWN
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return false
        }
        val mods = ScancodeMapper.metaStateToModifierBits(event.metaState) or stickyModifiersProvider()
        onSendKey(sc, pressed, mods)
        if (!pressed && !event.isModifierKey()) {
            onConsumeStickyModifiers()
        }
        return true
    }

    private fun handleCommittedText(text: String) {
        val sticky = stickyModifiersProvider()
        val isPureAscii = text.all { it.code in 0x09..0x7E }
        if (isPureAscii && text.length <= 2) {
            for (ch in text) {
                val mapped = ScancodeMapper.asciiToScancode(ch)
                if (mapped != null) {
                    val (sc, needsShift) = mapped
                    val mods = sticky or if (needsShift) ScancodeMapper.MOD_LEFT_SHIFT else 0
                    if (needsShift && (sticky and (ScancodeMapper.MOD_LEFT_SHIFT or ScancodeMapper.MOD_RIGHT_SHIFT)) == 0) {
                        onSendKey(ScancodeMapper.SC_LEFT_SHIFT, true, mods)
                    }
                    onSendKey(sc, true, mods)
                    onSendKey(sc, false, mods)
                    if (needsShift && (sticky and (ScancodeMapper.MOD_LEFT_SHIFT or ScancodeMapper.MOD_RIGHT_SHIFT)) == 0) {
                        onSendKey(ScancodeMapper.SC_LEFT_SHIFT, false, sticky)
                    }
                    onConsumeStickyModifiers()
                }
            }
            return
        }

        // Non-ASCII (CJK / Emoji / longer pasted phrase): coalesce over 35ms and paste silently.
        imeBuffer.append(text)
        handlerMain.removeCallbacks(flushImeRunnable)
        handlerMain.postDelayed(flushImeRunnable, 35L)
    }

    private fun flushSilentImeBuffer() {
        if (imeBuffer.isEmpty()) return
        val phrase = imeBuffer.toString()
        imeBuffer.clear()
        onPushSilentIme(phrase)

        // Allow one short tick for the reliable clipboard frame to land before Cmd+V / Ctrl+V.
        handlerMain.postDelayed({
            sendPasteShortcut()
        }, 45L)
    }

    fun sendPasteShortcut() {
        val modSc = if (isMacHost()) ScancodeMapper.SC_LEFT_GUI else ScancodeMapper.SC_LEFT_CTRL
        val modBit = if (isMacHost()) ScancodeMapper.MOD_LEFT_GUI else ScancodeMapper.MOD_LEFT_CTRL
        val scV = 0x2F // 'V' in Set 1
        onSendKey(modSc, true, modBit)
        onSendKey(scV, true, modBit)
        onSendKey(scV, false, modBit)
        onSendKey(modSc, false, 0)
    }

    private fun tapScancode(sc: Int) {
        val sticky = stickyModifiersProvider()
        onSendKey(sc, true, sticky)
        onSendKey(sc, false, sticky)
        onConsumeStickyModifiers()
    }

    private fun KeyEvent.isModifierKey(): Boolean = keyCode in setOf(
        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_META_LEFT,
        KeyEvent.KEYCODE_META_RIGHT,
    )
}
