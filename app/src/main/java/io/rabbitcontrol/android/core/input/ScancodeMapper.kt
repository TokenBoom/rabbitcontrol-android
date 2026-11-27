package io.rabbitcontrol.android.core.input

import android.view.KeyEvent

/**
 * Maps Android KeyEvents, ASCII characters, and virtual extended keys to PS/2 Set 1 make codes
 * matching `rc-input`'s wire format (`0xE0xx` for E0-prefixed keys) and modifier bitmask.
 */
object ScancodeMapper {
    const val MOD_LEFT_CTRL = 0x01
    const val MOD_LEFT_SHIFT = 0x02
    const val MOD_LEFT_ALT = 0x04
    const val MOD_LEFT_GUI = 0x08
    const val MOD_RIGHT_CTRL = 0x10
    const val MOD_RIGHT_SHIFT = 0x20
    const val MOD_RIGHT_ALT = 0x40
    const val MOD_RIGHT_GUI = 0x80

    const val SC_ESC = 0x01
    const val SC_BACKSPACE = 0x0E
    const val SC_TAB = 0x0F
    const val SC_ENTER = 0x1C
    const val SC_LEFT_CTRL = 0x1D
    const val SC_LEFT_SHIFT = 0x2A
    const val SC_RIGHT_SHIFT = 0x36
    const val SC_LEFT_ALT = 0x38
    const val SC_SPACE = 0x39
    const val SC_CAPS_LOCK = 0x3A
    const val SC_F1 = 0x3B
    const val SC_F2 = 0x3C
    const val SC_F3 = 0x3D
    const val SC_F4 = 0x3E
    const val SC_F5 = 0x3F
    const val SC_F6 = 0x40
    const val SC_F7 = 0x41
    const val SC_F8 = 0x42
    const val SC_F9 = 0x43
    const val SC_F10 = 0x44
    const val SC_F11 = 0x57
    const val SC_F12 = 0x58
    const val SC_RIGHT_CTRL = 0xE01D
    const val SC_RIGHT_ALT = 0xE038
    const val SC_HOME = 0xE047
    const val SC_UP = 0xE048
    const val SC_PAGE_UP = 0xE049
    const val SC_LEFT = 0xE04B
    const val SC_RIGHT = 0xE04D
    const val SC_END = 0xE04F
    const val SC_DOWN = 0xE050
    const val SC_PAGE_DOWN = 0xE051
    const val SC_INSERT = 0xE052
    const val SC_DELETE = 0xE053
    const val SC_LEFT_GUI = 0xE05B
    const val SC_RIGHT_GUI = 0xE05C
    const val SC_PRINT_SCREEN = 0xE037
    const val SC_SCROLL_LOCK = 0x46
    const val SC_NUM_LOCK = 0x45
    const val SC_CONTEXT_MENU = 0xE05D
    const val SC_NUMPAD_0 = 0x52
    const val SC_NUMPAD_1 = 0x4F
    const val SC_NUMPAD_2 = 0x50
    const val SC_NUMPAD_3 = 0x51
    const val SC_NUMPAD_4 = 0x4B
    const val SC_NUMPAD_5 = 0x4C
    const val SC_NUMPAD_6 = 0x4D
    const val SC_NUMPAD_7 = 0x47
    const val SC_NUMPAD_8 = 0x48
    const val SC_NUMPAD_9 = 0x49
    const val SC_NUMPAD_DECIMAL = 0x53
    const val SC_NUMPAD_ADD = 0x4E
    const val SC_NUMPAD_SUBTRACT = 0x4A
    const val SC_NUMPAD_MULTIPLY = 0x37
    const val SC_NUMPAD_DIVIDE = 0xE035
    const val SC_NUMPAD_ENTER = 0xE01C

    fun keyCodeToScancode(keyCode: Int): Int? = keyCodeToScancodeAndShift(keyCode)?.first

    /**
     * Maps an Android `KeyEvent.keyCode` to `(ps2Set1Scancode, requiresShift)`.
     * `requiresShift` is true for Android virtual symbol keycodes such as `@`, `#`, `*`, `+`, `(`, `)`.
     */
    fun keyCodeToScancodeAndShift(keyCode: Int): Pair<Int, Boolean>? = when (keyCode) {
        KeyEvent.KEYCODE_ESCAPE -> SC_ESC to false
        KeyEvent.KEYCODE_1 -> 0x02 to false
        KeyEvent.KEYCODE_2 -> 0x03 to false
        KeyEvent.KEYCODE_AT -> 0x03 to true
        KeyEvent.KEYCODE_3 -> 0x04 to false
        KeyEvent.KEYCODE_POUND -> 0x04 to true
        KeyEvent.KEYCODE_4 -> 0x05 to false
        KeyEvent.KEYCODE_5 -> 0x06 to false
        KeyEvent.KEYCODE_6 -> 0x07 to false
        KeyEvent.KEYCODE_7 -> 0x08 to false
        KeyEvent.KEYCODE_8 -> 0x09 to false
        KeyEvent.KEYCODE_STAR -> 0x09 to true
        KeyEvent.KEYCODE_9 -> 0x0A to false
        KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> 0x0A to true
        KeyEvent.KEYCODE_0 -> 0x0B to false
        KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> 0x0B to true
        KeyEvent.KEYCODE_MINUS -> 0x0C to false
        KeyEvent.KEYCODE_EQUALS -> 0x0D to false
        KeyEvent.KEYCODE_PLUS -> 0x0D to true
        KeyEvent.KEYCODE_DEL -> SC_BACKSPACE to false
        KeyEvent.KEYCODE_TAB -> SC_TAB to false
        KeyEvent.KEYCODE_Q -> 0x10 to false
        KeyEvent.KEYCODE_W -> 0x11 to false
        KeyEvent.KEYCODE_E -> 0x12 to false
        KeyEvent.KEYCODE_R -> 0x13 to false
        KeyEvent.KEYCODE_T -> 0x14 to false
        KeyEvent.KEYCODE_Y -> 0x15 to false
        KeyEvent.KEYCODE_U -> 0x16 to false
        KeyEvent.KEYCODE_I -> 0x17 to false
        KeyEvent.KEYCODE_O -> 0x18 to false
        KeyEvent.KEYCODE_P -> 0x19 to false
        KeyEvent.KEYCODE_LEFT_BRACKET -> 0x1A to false
        KeyEvent.KEYCODE_RIGHT_BRACKET -> 0x1B to false
        KeyEvent.KEYCODE_ENTER -> SC_ENTER to false
        KeyEvent.KEYCODE_CTRL_LEFT -> SC_LEFT_CTRL to false
        KeyEvent.KEYCODE_A -> 0x1E to false
        KeyEvent.KEYCODE_S -> 0x1F to false
        KeyEvent.KEYCODE_D -> 0x20 to false
        KeyEvent.KEYCODE_F -> 0x21 to false
        KeyEvent.KEYCODE_G -> 0x22 to false
        KeyEvent.KEYCODE_H -> 0x23 to false
        KeyEvent.KEYCODE_J -> 0x24 to false
        KeyEvent.KEYCODE_K -> 0x25 to false
        KeyEvent.KEYCODE_L -> 0x26 to false
        KeyEvent.KEYCODE_SEMICOLON -> 0x27 to false
        KeyEvent.KEYCODE_APOSTROPHE -> 0x28 to false
        KeyEvent.KEYCODE_GRAVE -> 0x29 to false
        KeyEvent.KEYCODE_SHIFT_LEFT -> SC_LEFT_SHIFT to false
        KeyEvent.KEYCODE_BACKSLASH -> 0x2B to false
        KeyEvent.KEYCODE_Z -> 0x2C to false
        KeyEvent.KEYCODE_X -> 0x2D to false
        KeyEvent.KEYCODE_C -> 0x2E to false
        KeyEvent.KEYCODE_V -> 0x2F to false
        KeyEvent.KEYCODE_B -> 0x30 to false
        KeyEvent.KEYCODE_N -> 0x31 to false
        KeyEvent.KEYCODE_M -> 0x32 to false
        KeyEvent.KEYCODE_COMMA -> 0x33 to false
        KeyEvent.KEYCODE_PERIOD -> 0x34 to false
        KeyEvent.KEYCODE_SLASH -> 0x35 to false
        KeyEvent.KEYCODE_SHIFT_RIGHT -> SC_RIGHT_SHIFT to false
        KeyEvent.KEYCODE_ALT_LEFT -> SC_LEFT_ALT to false
        KeyEvent.KEYCODE_SPACE -> SC_SPACE to false
        KeyEvent.KEYCODE_CAPS_LOCK -> SC_CAPS_LOCK to false
        KeyEvent.KEYCODE_F1 -> SC_F1 to false
        KeyEvent.KEYCODE_F2 -> SC_F2 to false
        KeyEvent.KEYCODE_F3 -> SC_F3 to false
        KeyEvent.KEYCODE_F4 -> SC_F4 to false
        KeyEvent.KEYCODE_F5 -> SC_F5 to false
        KeyEvent.KEYCODE_F6 -> SC_F6 to false
        KeyEvent.KEYCODE_F7 -> SC_F7 to false
        KeyEvent.KEYCODE_F8 -> SC_F8 to false
        KeyEvent.KEYCODE_F9 -> SC_F9 to false
        KeyEvent.KEYCODE_F10 -> SC_F10 to false
        KeyEvent.KEYCODE_F11 -> SC_F11 to false
        KeyEvent.KEYCODE_F12 -> SC_F12 to false
        KeyEvent.KEYCODE_CTRL_RIGHT -> SC_RIGHT_CTRL to false
        KeyEvent.KEYCODE_ALT_RIGHT -> SC_RIGHT_ALT to false
        KeyEvent.KEYCODE_SYSRQ -> SC_PRINT_SCREEN to false
        KeyEvent.KEYCODE_SCROLL_LOCK -> SC_SCROLL_LOCK to false
        KeyEvent.KEYCODE_NUM_LOCK -> SC_NUM_LOCK to false
        KeyEvent.KEYCODE_MENU -> SC_CONTEXT_MENU to false
        KeyEvent.KEYCODE_MOVE_HOME -> SC_HOME to false
        KeyEvent.KEYCODE_DPAD_UP -> SC_UP to false
        KeyEvent.KEYCODE_PAGE_UP -> SC_PAGE_UP to false
        KeyEvent.KEYCODE_DPAD_LEFT -> SC_LEFT to false
        KeyEvent.KEYCODE_DPAD_RIGHT -> SC_RIGHT to false
        KeyEvent.KEYCODE_MOVE_END -> SC_END to false
        KeyEvent.KEYCODE_DPAD_DOWN -> SC_DOWN to false
        KeyEvent.KEYCODE_PAGE_DOWN -> SC_PAGE_DOWN to false
        KeyEvent.KEYCODE_INSERT -> SC_INSERT to false
        KeyEvent.KEYCODE_FORWARD_DEL -> SC_DELETE to false
        KeyEvent.KEYCODE_META_LEFT -> SC_LEFT_GUI to false
        KeyEvent.KEYCODE_META_RIGHT -> SC_RIGHT_GUI to false
        KeyEvent.KEYCODE_NUMPAD_0 -> SC_NUMPAD_0 to false
        KeyEvent.KEYCODE_NUMPAD_1 -> SC_NUMPAD_1 to false
        KeyEvent.KEYCODE_NUMPAD_2 -> SC_NUMPAD_2 to false
        KeyEvent.KEYCODE_NUMPAD_3 -> SC_NUMPAD_3 to false
        KeyEvent.KEYCODE_NUMPAD_4 -> SC_NUMPAD_4 to false
        KeyEvent.KEYCODE_NUMPAD_5 -> SC_NUMPAD_5 to false
        KeyEvent.KEYCODE_NUMPAD_6 -> SC_NUMPAD_6 to false
        KeyEvent.KEYCODE_NUMPAD_7 -> SC_NUMPAD_7 to false
        KeyEvent.KEYCODE_NUMPAD_8 -> SC_NUMPAD_8 to false
        KeyEvent.KEYCODE_NUMPAD_9 -> SC_NUMPAD_9 to false
        KeyEvent.KEYCODE_NUMPAD_DOT -> SC_NUMPAD_DECIMAL to false
        KeyEvent.KEYCODE_NUMPAD_ADD -> SC_NUMPAD_ADD to false
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> SC_NUMPAD_SUBTRACT to false
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> SC_NUMPAD_MULTIPLY to false
        KeyEvent.KEYCODE_NUMPAD_DIVIDE -> SC_NUMPAD_DIVIDE to false
        KeyEvent.KEYCODE_NUMPAD_ENTER -> SC_NUMPAD_ENTER to false
        else -> null
    }

    fun isModifierScancode(scancode: Int): Boolean = modifierBitForScancode(scancode) != 0

    fun modifierBitForScancode(scancode: Int): Int = when (scancode) {
        SC_LEFT_CTRL -> MOD_LEFT_CTRL
        SC_LEFT_SHIFT -> MOD_LEFT_SHIFT
        SC_LEFT_ALT -> MOD_LEFT_ALT
        SC_LEFT_GUI -> MOD_LEFT_GUI
        SC_RIGHT_CTRL -> MOD_RIGHT_CTRL
        SC_RIGHT_SHIFT -> MOD_RIGHT_SHIFT
        SC_RIGHT_ALT -> MOD_RIGHT_ALT
        SC_RIGHT_GUI -> MOD_RIGHT_GUI
        else -> 0
    }

    /** Maps a printable ASCII character to `(scancode, needsShift)`. */
    fun asciiToScancode(ch: Char): Pair<Int, Boolean>? = when (ch) {
        in 'a'..'z' -> keyCodeToScancode(KeyEvent.KEYCODE_A + (ch - 'a'))?.let { it to false }
        in 'A'..'Z' -> keyCodeToScancode(KeyEvent.KEYCODE_A + (ch - 'A'))?.let { it to true }
        '0' -> 0x0B to false
        ')' -> 0x0B to true
        '1' -> 0x02 to false
        '!' -> 0x02 to true
        '2' -> 0x03 to false
        '@' -> 0x03 to true
        '3' -> 0x04 to false
        '#' -> 0x04 to true
        '4' -> 0x05 to false
        '$' -> 0x05 to true
        '5' -> 0x06 to false
        '%' -> 0x06 to true
        '6' -> 0x07 to false
        '^' -> 0x07 to true
        '7' -> 0x08 to false
        '&' -> 0x08 to true
        '8' -> 0x09 to false
        '*' -> 0x09 to true
        '9' -> 0x0A to false
        '(' -> 0x0A to true
        ' ' -> SC_SPACE to false
        '\n', '\r' -> SC_ENTER to false
        '\t' -> SC_TAB to false
        '-' -> 0x0C to false
        '_' -> 0x0C to true
        '=' -> 0x0D to false
        '+' -> 0x0D to true
        '[' -> 0x1A to false
        '{' -> 0x1A to true
        ']' -> 0x1B to false
        '}' -> 0x1B to true
        ';' -> 0x27 to false
        ':' -> 0x27 to true
        '\'' -> 0x28 to false
        '"' -> 0x28 to true
        '`' -> 0x29 to false
        '~' -> 0x29 to true
        '\\' -> 0x2B to false
        '|' -> 0x2B to true
        ',' -> 0x33 to false
        '<' -> 0x33 to true
        '.' -> 0x34 to false
        '>' -> 0x34 to true
        '/' -> 0x35 to false
        '?' -> 0x35 to true
        else -> null
    }

    fun metaStateToModifierBits(metaState: Int): Int {
        var bits = 0
        if ((metaState and KeyEvent.META_CTRL_LEFT_ON) != 0) bits = bits or MOD_LEFT_CTRL
        if ((metaState and KeyEvent.META_CTRL_RIGHT_ON) != 0) bits = bits or MOD_RIGHT_CTRL
        if ((metaState and KeyEvent.META_CTRL_ON) != 0 && (bits and (MOD_LEFT_CTRL or MOD_RIGHT_CTRL)) == 0) {
            bits = bits or MOD_LEFT_CTRL
        }
        if ((metaState and KeyEvent.META_SHIFT_LEFT_ON) != 0) bits = bits or MOD_LEFT_SHIFT
        if ((metaState and KeyEvent.META_SHIFT_RIGHT_ON) != 0) bits = bits or MOD_RIGHT_SHIFT
        if ((metaState and KeyEvent.META_SHIFT_ON) != 0 && (bits and (MOD_LEFT_SHIFT or MOD_RIGHT_SHIFT)) == 0) {
            bits = bits or MOD_LEFT_SHIFT
        }
        if ((metaState and KeyEvent.META_ALT_LEFT_ON) != 0) bits = bits or MOD_LEFT_ALT
        if ((metaState and KeyEvent.META_ALT_RIGHT_ON) != 0) bits = bits or MOD_RIGHT_ALT
        if ((metaState and KeyEvent.META_ALT_ON) != 0 && (bits and (MOD_LEFT_ALT or MOD_RIGHT_ALT)) == 0) {
            bits = bits or MOD_LEFT_ALT
        }
        if ((metaState and KeyEvent.META_META_LEFT_ON) != 0) bits = bits or MOD_LEFT_GUI
        if ((metaState and KeyEvent.META_META_RIGHT_ON) != 0) bits = bits or MOD_RIGHT_GUI
        if ((metaState and KeyEvent.META_META_ON) != 0 && (bits and (MOD_LEFT_GUI or MOD_RIGHT_GUI)) == 0) {
            bits = bits or MOD_LEFT_GUI
        }
        return bits
    }

    fun isMacOsTarget(osName: String): Boolean {
        val lower = osName.lowercase()
        return lower.contains("mac") || lower.contains("darwin") || lower.contains("os x")
    }
}
