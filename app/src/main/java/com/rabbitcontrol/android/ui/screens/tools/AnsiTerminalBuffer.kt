package com.rabbitcontrol.android.ui.screens.tools

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.min

/**
 * Lightweight, allocation-conscious VT100/ANSI terminal screen buffer supporting SGR 16/256/RGB
 * colors, cursor movement, erase sequences, and scrollback lines.
 */
class AnsiTerminalBuffer(
    initialCols: Int = 80,
    initialRows: Int = 24,
    private val maxScrollback: Int = 600,
) {
    data class Cell(
        var ch: Char = ' ',
        var fg: Int = DEFAULT_FG,
        var bg: Int = DEFAULT_BG,
        var bold: Boolean = false,
    )

    var cols: Int = initialCols.coerceIn(20, 240)
        private set
    var rows: Int = initialRows.coerceIn(8, 120)
        private set

    private var grid = Array(rows) { Array(cols) { Cell() } }
    private val scrollback = ArrayDeque<String>()
    private var cursorX = 0
    private var cursorY = 0
    private var curFg = DEFAULT_FG
    private var curBg = DEFAULT_BG
    private var curBold = false

    private enum class ParseState { Normal, Esc, EscCharset, Csi, Osc }
    private var state = ParseState.Normal
    private val csiBuf = StringBuilder()

    fun resize(newCols: Int, newRows: Int) {
        val nc = newCols.coerceIn(20, 240)
        val nr = newRows.coerceIn(8, 120)
        if (nc == cols && nr == rows) return
        val next = Array(nr) { r ->
            Array(nc) { c ->
                if (r < rows && c < cols) grid[r][c].copy() else Cell()
            }
        }
        cols = nc
        rows = nr
        grid = next
        cursorX = cursorX.coerceIn(0, cols - 1)
        cursorY = cursorY.coerceIn(0, rows - 1)
    }

    fun clear() {
        scrollback.clear()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                grid[r][c] = Cell()
            }
        }
        cursorX = 0
        cursorY = 0
    }

    fun feed(bytes: ByteArray) {
        val text = bytes.toString(Charsets.UTF_8)
        for (ch in text) {
            when (state) {
                ParseState.Normal -> handleNormalChar(ch)
                ParseState.Esc -> when (ch) {
                    '[' -> {
                        csiBuf.clear()
                        state = ParseState.Csi
                    }
                    ']' -> state = ParseState.Osc
                    '(', ')', '*', '+' -> state = ParseState.EscCharset
                    else -> state = ParseState.Normal
                }
                ParseState.EscCharset -> {
                    state = ParseState.Normal
                }
                ParseState.Osc -> {
                    if (ch == '\u0007') state = ParseState.Normal
                    else if (ch == '\u001B') state = ParseState.Esc
                }
                ParseState.Csi -> {
                    if (ch.code in 0x40..0x7E) {
                        handleCsi(ch, csiBuf.toString())
                        state = ParseState.Normal
                    } else if (csiBuf.length < 64) {
                        csiBuf.append(ch)
                    } else {
                        state = ParseState.Normal
                    }
                }
            }
        }
    }

    private fun handleNormalChar(ch: Char) {
        when (ch) {
            '\u001B' -> state = ParseState.Esc
            '\r' -> cursorX = 0
            '\n' -> newline()
            '\b' -> if (cursorX > 0) cursorX--
            '\t' -> {
                val nextTab = ((cursorX / 8) + 1) * 8
                cursorX = min(cols - 1, nextTab)
            }
            else -> {
                if (ch.code >= 0x20) {
                    if (cursorX >= cols) {
                        cursorX = 0
                        newline()
                    }
                    val cell = grid[cursorY][cursorX]
                    cell.ch = ch
                    cell.fg = curFg
                    cell.bg = curBg
                    cell.bold = curBold
                    cursorX++
                }
            }
        }
    }

    private fun newline() {
        if (cursorY < rows - 1) {
            cursorY++
        } else {
            val top = grid[0].joinToString("") { it.ch.toString() }.trimEnd()
            if (scrollback.size >= maxScrollback) scrollback.removeFirst()
            scrollback.addLast(top)
            for (r in 0 until rows - 1) {
                grid[r] = grid[r + 1]
            }
            grid[rows - 1] = Array(cols) { Cell() }
        }
    }

    private fun handleCsi(cmd: Char, rawParams: String) {
        val cleaned = rawParams.removePrefix("?")
        val params = if (cleaned.isEmpty()) emptyList() else cleaned.split(';').map { it.toIntOrNull() ?: 0 }
        val p0 = params.getOrElse(0) { 0 }
        val p1 = params.getOrElse(1) { 0 }
        when (cmd) {
            'A' -> cursorY = (cursorY - maxOf(1, p0)).coerceAtLeast(0)
            'B' -> cursorY = (cursorY + maxOf(1, p0)).coerceAtMost(rows - 1)
            'C' -> cursorX = (cursorX + maxOf(1, p0)).coerceAtMost(cols - 1)
            'D' -> cursorX = (cursorX - maxOf(1, p0)).coerceAtLeast(0)
            'G' -> cursorX = (maxOf(1, p0) - 1).coerceIn(0, cols - 1)
            'H', 'f' -> {
                cursorY = (maxOf(1, p0) - 1).coerceIn(0, rows - 1)
                cursorX = (maxOf(1, p1) - 1).coerceIn(0, cols - 1)
            }
            'J' -> when (p0) {
                2, 3 -> {
                    for (r in 0 until rows) for (c in 0 until cols) grid[r][c] = Cell()
                    if (p0 == 3) scrollback.clear()
                }
                0 -> {
                    for (c in cursorX until cols) grid[cursorY][c] = Cell()
                    for (r in (cursorY + 1) until rows) for (c in 0 until cols) grid[r][c] = Cell()
                }
                1 -> {
                    for (r in 0 until cursorY) for (c in 0 until cols) grid[r][c] = Cell()
                    for (c in 0..cursorX.coerceAtMost(cols - 1)) grid[cursorY][c] = Cell()
                }
            }
            'K' -> when (p0) {
                0 -> for (c in cursorX until cols) grid[cursorY][c] = Cell()
                1 -> for (c in 0..cursorX.coerceAtMost(cols - 1)) grid[cursorY][c] = Cell()
                2 -> for (c in 0 until cols) grid[cursorY][c] = Cell()
            }
            'm' -> applySgr(if (params.isEmpty()) listOf(0) else params)
        }
    }

    private fun applySgr(params: List<Int>) {
        var i = 0
        while (i < params.size) {
            when (val code = params[i]) {
                0 -> {
                    curFg = DEFAULT_FG
                    curBg = DEFAULT_BG
                    curBold = false
                }
                1 -> curBold = true
                22 -> curBold = false
                in 30..37 -> curFg = ANSI_16[code - 30]
                39 -> curFg = DEFAULT_FG
                in 40..47 -> curBg = ANSI_16[code - 40]
                49 -> curBg = DEFAULT_BG
                in 90..97 -> curFg = ANSI_16[code - 90 + 8]
                in 100..107 -> curBg = ANSI_16[code - 100 + 8]
                38, 48 -> {
                    val isFg = code == 38
                    val mode = params.getOrNull(i + 1)
                    if (mode == 5 && i + 2 < params.size) {
                        val c = ansi256(params[i + 2])
                        if (isFg) curFg = c else curBg = c
                        i += 2
                    } else if (mode == 2 && i + 4 < params.size) {
                        val r = params[i + 2].coerceIn(0, 255)
                        val g = params[i + 3].coerceIn(0, 255)
                        val b = params[i + 4].coerceIn(0, 255)
                        val c = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        if (isFg) curFg = c else curBg = c
                        i += 4
                    }
                }
            }
            i++
        }
    }

    fun renderSnapshot(): AnnotatedString = buildAnnotatedString {
        if (scrollback.isNotEmpty()) {
            pushStyle(SpanStyle(color = Color(0xFF94A3B8)))
            scrollback.forEach { line ->
                append(line)
                append('\n')
            }
            pop()
        }
        var lastNonEmptyRow = 0
        for (r in rows - 1 downTo 0) {
            val rowCells = grid[r]
            if (rowCells.any { it.ch != ' ' || it.bg != DEFAULT_BG }) {
                lastNonEmptyRow = r
                break
            }
        }
        val renderRows = maxOf(cursorY, lastNonEmptyRow)
        for (r in 0..renderRows) {
            val rowCells = grid[r]
            var lastNonSpace = -1
            for (c in cols - 1 downTo 0) {
                if (rowCells[c].ch != ' ' || rowCells[c].bg != DEFAULT_BG || (r == cursorY && c == cursorX)) {
                    lastNonSpace = c
                    break
                }
            }
            val limit = lastNonSpace.coerceAtLeast(-1)
            for (c in 0..limit) {
                val cell = rowCells[c]
                val isCursor = r == cursorY && c == cursorX
                val fg = if (isCursor) Color(0xFF0B0E14) else Color(cell.fg)
                val bg = if (isCursor) Color(0xFF6094FF) else if (cell.bg != DEFAULT_BG) Color(cell.bg) else Color.Unspecified
                pushStyle(
                    SpanStyle(
                        color = fg,
                        background = bg,
                        fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
                append(cell.ch)
                pop()
            }
            if (r < renderRows) append('\n')
        }
    }

    companion object {
        const val DEFAULT_FG = 0xFFE6EDF7.toInt()
        const val DEFAULT_BG = 0x00000000

        private val ANSI_16 = intArrayOf(
            0xFF1E2430.toInt(), 0xFFFF6B73.toInt(), 0xFF4ADE80.toInt(), 0xFFFACC15.toInt(),
            0xFF60A5FA.toInt(), 0xFFC084FC.toInt(), 0xFF22D3EE.toInt(), 0xFFE2E8F0.toInt(),
            0xFF64748B.toInt(), 0xFFFCA5A5.toInt(), 0xFF86EFAC.toInt(), 0xFFFDE047.toInt(),
            0xFF93C5FD.toInt(), 0xFFD8B4FE.toInt(), 0xFF67E8F9.toInt(), 0xFFFFFFFF.toInt(),
        )

        private fun ansi256(idx: Int): Int {
            val n = idx.coerceIn(0, 255)
            if (n < 16) return ANSI_16[n]
            if (n >= 232) {
                val v = 8 + (n - 232) * 10
                return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            val c = n - 16
            val r = (c / 36) * 51
            val g = ((c / 6) % 6) * 51
            val b = (c % 6) * 51
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
