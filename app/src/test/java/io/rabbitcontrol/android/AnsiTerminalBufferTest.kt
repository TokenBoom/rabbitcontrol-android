package io.rabbitcontrol.android.ui.screens.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiTerminalBufferTest {
    @Test
    fun parsesAnsiColorsCursorMoveAndErase() {
        val buf = AnsiTerminalBuffer(initialCols = 40, initialRows = 10)
        buf.feed("\u001B[32mhello\u001B[0m world\r\nline2".toByteArray())
        val text = buf.renderSnapshot().text
        assertTrue(text.contains("hello world"))
        assertTrue(text.contains("line2"))

        buf.feed("\u001B[2J\u001B[1;1Hfresh".toByteArray())
        val cleared = buf.renderSnapshot().text
        assertTrue(cleared.startsWith("fresh"))
    }

    @Test
    fun handlesCharsetDesignatorsAndCursorHorizontalAbsolute() {
        val buf = AnsiTerminalBuffer(initialCols = 40, initialRows = 10)
        buf.feed("\u001B(Bprompt$ \u001B[3Gxx".toByteArray())
        val text = buf.renderSnapshot().text
        assertEquals("prxxpt$", text.trimEnd('\n'))
    }

    @Test
    fun resizesGridCleanly() {
        val buf = AnsiTerminalBuffer(80, 24)
        buf.resize(120, 40)
        assertEquals(120, buf.cols)
        assertEquals(40, buf.rows)
    }
}
