package com.rabbitcontrol.android.video

import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.i18n.AppStrings

/** The bundled Annex-B clip has one access unit delimiter per encoded frame.
 * Live sessions receive complete access units from core and need no AUD. */
internal class MotionClip(bytes: ByteArray) {
    val frames: List<ByteArray>
    val sps: ByteArray
    val pps: ByteArray

    init {
        val nals = mutableListOf<Pair<Int, Int>>()
        var pos = 0
        while (pos + 3 < bytes.size) {
            val length = when {
                bytes[pos] != 0.toByte() || bytes[pos + 1] != 0.toByte() -> 0
                bytes[pos + 2] == 1.toByte() -> 3
                pos + 4 < bytes.size && bytes[pos + 2] == 0.toByte() && bytes[pos + 3] == 1.toByte() -> 4
                else -> 0
            }
            if (length == 0) {
                pos++
            } else {
                nals += pos to (bytes[pos + length].toInt() and 31)
                pos += length + 1
            }
        }
        val delimiters = nals.filter { it.second == 9 }.map { it.first } + bytes.size
        frames = delimiters.zipWithNext { start, end -> bytes.copyOfRange(start, end) }
        require(frames.isNotEmpty())
        fun config(type: Int): ByteArray {
            val i = nals.indexOfFirst { it.second == type }
            require(i >= 0) { AppStrings.get(R.string.preview_err_missing_sps, "样片缺少 H.264 参数集") }
            return bytes.copyOfRange(nals[i].first, nals.getOrNull(i + 1)?.first ?: bytes.size)
        }
        sps = config(7)
        pps = config(8)
    }

    companion object {
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val FPS = 60
    }
}
