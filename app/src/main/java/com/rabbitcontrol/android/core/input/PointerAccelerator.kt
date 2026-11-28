package com.rabbitcontrol.android.core.input

import kotlin.math.hypot
import kotlin.math.pow

/**
 * Mac-style non-linear pointer acceleration curve with sub-pixel fractional accumulation.
 * Keeps slow micro-adjustments pixel-accurate on 4K displays while allowing fast flicks
 * to traverse the full screen in one thumb swipe.
 */
class PointerAccelerator {
    private var normX = 32767.5f
    private var normY = 32767.5f

    val currentNormX: Int get() = normX.toInt().coerceIn(0, 65535)
    val currentNormY: Int get() = normY.toInt().coerceIn(0, 65535)

    fun setNormalizedPosition(x: Int, y: Int) {
        normX = x.coerceIn(0, 65535).toFloat()
        normY = y.coerceIn(0, 65535).toFloat()
    }

    /**
     * Applies non-linear acceleration to pixel deltas `(dxPx, dyPx)` over a trackpad surface
     * whose reference span is `surfaceWidthPx` and returns the updated normalized `(x, y)` in `0..65535`.
     */
    fun applyDelta(
        dxPx: Float,
        dyPx: Float,
        dtMs: Float,
        surfaceSpanPx: Float,
        density: Float,
        sensitivity: Float,
    ): Pair<Int, Int> {
        val safeSpan = surfaceSpanPx.coerceAtLeast(320f)
        val safeDensity = density.coerceAtLeast(1f)
        val safeDt = dtMs.coerceIn(1f, 64f)

        val distDp = hypot(dxPx, dyPx) / safeDensity
        val speedDpPerMs = distDp / safeDt

        // Slow precision floor (~0.55x) smoothly ramping up to ~3.8x on brisk flicks.
        val curve = 0.55f + 3.25f * (speedDpPerMs / 2.2f).coerceIn(0f, 1.4f).pow(1.35f)
        val gain = curve * sensitivity.coerceIn(0.4f, 3.0f)

        val scale = 65535f / safeSpan
        normX = (normX + dxPx * gain * scale).coerceIn(0f, 65535f)
        normY = (normY + dyPx * gain * scale).coerceIn(0f, 65535f)
        return currentNormX to currentNormY
    }
}
