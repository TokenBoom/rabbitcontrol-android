package io.rabbitcontrol.android.core.input

import android.view.KeyEvent
import io.rabbitcontrol.android.ui.session.VirtualKeyRole
import io.rabbitcontrol.android.ui.session.virtualKeyboardKeyRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScancodeMapperTest {
    @Test
    fun mapsCommonKeysExtendedNavigationSystemAndNumpad() {
        assertEquals(0x01, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(0x1E, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_A))
        assertEquals(0xE048, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(0xE05B, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_META_LEFT))
        assertEquals(0xE037, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_SYSRQ))
        assertEquals(0x46, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_SCROLL_LOCK))
        assertEquals(0x45, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_NUM_LOCK))
        assertEquals(0xE05D, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_MENU))
        assertEquals(0xE01C, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(0xE035, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_NUMPAD_DIVIDE))
        assertEquals(0x4E, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_NUMPAD_ADD))
        assertNull(ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun mapsShiftedAndroidSymbolKeycodesWithShiftFlag() {
        assertEquals(0x03 to true, ScancodeMapper.keyCodeToScancodeAndShift(KeyEvent.KEYCODE_AT))
        assertEquals(0x04 to true, ScancodeMapper.keyCodeToScancodeAndShift(KeyEvent.KEYCODE_POUND))
        assertEquals(0x09 to true, ScancodeMapper.keyCodeToScancodeAndShift(KeyEvent.KEYCODE_STAR))
        assertEquals(0x0D to true, ScancodeMapper.keyCodeToScancodeAndShift(KeyEvent.KEYCODE_PLUS))
        assertEquals(0x0A to true, ScancodeMapper.keyCodeToScancodeAndShift(KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN))
        assertEquals(0x0B to true, ScancodeMapper.keyCodeToScancodeAndShift(KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN))
        assertEquals(0x1E to false, ScancodeMapper.keyCodeToScancodeAndShift(KeyEvent.KEYCODE_A))
    }

    @Test
    fun mapsAllPrintableAsciiCharactersAndDetectsMacOs() {
        for (code in 0x20..0x7E) {
            val ch = code.toChar()
            assertNotNull("Expected ASCII mapping for '$ch' ($code)", ScancodeMapper.asciiToScancode(ch))
        }
        assertEquals(0x1C to false, ScancodeMapper.asciiToScancode('\n'))
        assertEquals(0x1C to false, ScancodeMapper.asciiToScancode('\r'))
        assertEquals(0x0F to false, ScancodeMapper.asciiToScancode('\t'))
        assertEquals(0x1E to false, ScancodeMapper.asciiToScancode('a'))
        assertEquals(0x1E to true, ScancodeMapper.asciiToScancode('A'))
        assertEquals(0x03 to true, ScancodeMapper.asciiToScancode('@'))
        assertTrue(ScancodeMapper.isMacOsTarget("macOS 15.2"))
        assertFalse(ScancodeMapper.isMacOsTarget("Windows 11"))
    }

    @Test
    fun modifierHelpersCoverAllEightModifiers() {
        val pairs = listOf(
            ScancodeMapper.SC_LEFT_CTRL to ScancodeMapper.MOD_LEFT_CTRL,
            ScancodeMapper.SC_RIGHT_CTRL to ScancodeMapper.MOD_RIGHT_CTRL,
            ScancodeMapper.SC_LEFT_SHIFT to ScancodeMapper.MOD_LEFT_SHIFT,
            ScancodeMapper.SC_RIGHT_SHIFT to ScancodeMapper.MOD_RIGHT_SHIFT,
            ScancodeMapper.SC_LEFT_ALT to ScancodeMapper.MOD_LEFT_ALT,
            ScancodeMapper.SC_RIGHT_ALT to ScancodeMapper.MOD_RIGHT_ALT,
            ScancodeMapper.SC_LEFT_GUI to ScancodeMapper.MOD_LEFT_GUI,
            ScancodeMapper.SC_RIGHT_GUI to ScancodeMapper.MOD_RIGHT_GUI,
        )
        for ((sc, bit) in pairs) {
            assertTrue(ScancodeMapper.isModifierScancode(sc))
            assertEquals(bit, ScancodeMapper.modifierBitForScancode(sc))
        }
        assertFalse(ScancodeMapper.isModifierScancode(ScancodeMapper.SC_ENTER))
        assertEquals(0, ScancodeMapper.modifierBitForScancode(ScancodeMapper.SC_SPACE))
    }

    @Test
    fun allVirtualKeyboardKeysMapToValidSet1Scancodes() {
        val layouts = listOf(
            virtualKeyboardKeyRows(fnActive = false, isMacHost = false),
            virtualKeyboardKeyRows(fnActive = true, isMacHost = true),
        )
        for (rows in layouts) {
            assertEquals(5, rows.size)
            for (row in rows) {
                for (key in row) {
                    if (key.role == VirtualKeyRole.FnToggle) {
                        assertEquals(0, key.scancode)
                    } else {
                        assertTrue(
                            "Expected valid Set 1 scancode for key '${key.primaryLabel}'",
                            key.scancode in 1..0xFFFF,
                        )
                        if (key.role == VirtualKeyRole.Modifier) {
                            assertTrue(
                                "Expected modifier bit for '${key.primaryLabel}'",
                                key.modifierBit != 0 && ScancodeMapper.isModifierScancode(key.scancode),
                            )
                        }
                    }
                }
            }
        }
    }
}
