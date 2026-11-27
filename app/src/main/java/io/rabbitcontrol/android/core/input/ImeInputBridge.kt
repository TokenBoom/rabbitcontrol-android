package io.rabbitcontrol.android.core.input

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * Transparent 1x1 IME proxy view for remote desktop sessions.
 * - Pure ASCII / hardware KeyEvents -> ordered PS/2 Set 1 scancode key taps.
 * - Non-ASCII / CJK / Emoji / long IME commits -> buffered briefly (35ms), sent silently via core
 *   clipboard channel (without touching Android system clipboard), followed by remote Cmd+V / Ctrl+V.
 */
class ImeInputBridge(
    context: Context,
    private val stickyModifiersProvider: () -> Int,
    private val onConsumeStickyModifiers: () -> Unit,
    private val onSendKey: (scancode: Int, pressed: Boolean, modifiers: Int) -> Unit,
    private val onTapScancode: (scancode: Int) -> Unit,
    private val onSendAsciiText: (String) -> Unit,
    private val onPushSilentIme: (String) -> Unit,
    private val onSendPasteShortcut: () -> Unit,
) : View(context) {

    private val handlerMain = Handler(Looper.getMainLooper())
    private val imeBuffer = StringBuilder()
    private val flushImeRunnable = Runnable { flushSilentImeBuffer() }

    // Sentinel buffer ensures OEM IMEs (Gboard, Xiaomi, Sogou) always see text before cursor
    // and therefore reliably dispatch deleteSurroundingText / KEYCODE_DEL for Backspace.
    private val sentinelEditable: Editable = Editable.Factory.getInstance().newEditable(" ")

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        ensureSentinel()
    }

    fun showSoftKeyboard() {
        requestFocus()
        ensureSentinel()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        ensureSentinel()
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, true) {
            override fun getEditable(): Editable = sentinelEditable

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    handleCommittedText(text.toString())
                }
                ensureSentinel()
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val res = super.setComposingText(text, newCursorPosition)
                if (sentinelEditable.isEmpty()) ensureSentinel()
                return res
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength.coerceAtMost(8)) {
                        onTapScancode(ScancodeMapper.SC_BACKSPACE)
                    }
                }
                ensureSentinel()
                return true
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) {
                    repeat(beforeLength.coerceAtMost(8)) {
                        onTapScancode(ScancodeMapper.SC_BACKSPACE)
                    }
                }
                ensureSentinel()
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                onTapScancode(ScancodeMapper.SC_ENTER)
                ensureSentinel()
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                return dispatchRemoteKeyEvent(event) || super.sendKeyEvent(event)
            }
        }
    }

    fun dispatchRemoteKeyEvent(event: KeyEvent): Boolean {
        val (sc, needsShift) = ScancodeMapper.keyCodeToScancodeAndShift(event.keyCode) ?: return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return false
        }
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val sticky = stickyModifiersProvider()
        val metaMods = ScancodeMapper.metaStateToModifierBits(event.metaState)
        val combinedMods = metaMods or sticky
        val shiftAlreadyHeld = (combinedMods and (ScancodeMapper.MOD_LEFT_SHIFT or ScancodeMapper.MOD_RIGHT_SHIFT)) != 0
        val effectiveMods = if (needsShift) combinedMods or ScancodeMapper.MOD_LEFT_SHIFT else combinedMods

        if (pressed) {
            if (needsShift && !shiftAlreadyHeld) {
                onSendKey(ScancodeMapper.SC_LEFT_SHIFT, true, effectiveMods)
            }
            onSendKey(sc, true, effectiveMods)
        } else {
            onSendKey(sc, false, effectiveMods)
            if (needsShift && !shiftAlreadyHeld) {
                onSendKey(ScancodeMapper.SC_LEFT_SHIFT, false, combinedMods)
            }
            if (!ScancodeMapper.isModifierScancode(sc)) {
                onConsumeStickyModifiers()
            }
        }
        return true
    }

    private fun handleCommittedText(text: String) {
        val isPureAscii = text.all { it == '\n' || it == '\r' || it == '\t' || it.code in 0x20..0x7E }
        if (isPureAscii && text.length <= 64) {
            onSendAsciiText(text)
            return
        }

        // Non-ASCII (CJK / Emoji / long paste): coalesce over 35ms and paste silently.
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
            onSendPasteShortcut()
        }, 45L)
    }

    private fun ensureSentinel() {
        if (sentinelEditable.length != 1 || sentinelEditable[0] != ' ') {
            sentinelEditable.replace(0, sentinelEditable.length, " ")
        }
        Selection.setSelection(sentinelEditable, 1)
    }
}
