package io.rabbitcontrol.android.core.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScancodeMapperTest {
    @Test
    fun mapsCommonKeysAndExtendedArrows() {
        assertEquals(0x01, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(0x1E, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_A))
        assertEquals(0xE048, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(0xE05B, ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_META_LEFT))
        assertNull(ScancodeMapper.keyCodeToScancode(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun mapsAsciiWithShiftFlagsAndDetectsMacOs() {
        assertEquals(0x1E to false, ScancodeMapper.asciiToScancode('a'))
        assertEquals(0x1E to true, ScancodeMapper.asciiToScancode('A'))
        assertEquals(0x03 to true, ScancodeMapper.asciiToScancode('@'))
        assertTrue(ScancodeMapper.isMacOsTarget("macOS 15.2"))
        assertFalse(ScancodeMapper.isMacOsTarget("Windows 11"))
    }
}
