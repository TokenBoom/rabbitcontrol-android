package com.rabbitcontrol.android.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerAcceleratorTest {
    @Test
    fun slowMovementProducesSubtleDeltaWhileFastFlickCoversMoreScreen() {
        val slow = PointerAccelerator().apply { setNormalizedPosition(32768, 32768) }
        val (sx, _) = slow.applyDelta(
            dxPx = 20f,
            dyPx = 0f,
            dtMs = 32f,
            surfaceSpanPx = 1000f,
            density = 3f,
            sensitivity = 1f,
        )

        val fast = PointerAccelerator().apply { setNormalizedPosition(32768, 32768) }
        val (fx, _) = fast.applyDelta(
            dxPx = 20f,
            dyPx = 0f,
            dtMs = 4f,
            surfaceSpanPx = 1000f,
            density = 3f,
            sensitivity = 1f,
        )

        assertTrue("Slow delta ($sx) should be positive", sx > 32768)
        assertTrue("Fast flick ($fx) should travel further than slow ($sx)", fx > sx)
    }

    @Test
    fun clampsCoordinatesWithinNormalizedBounds() {
        val acc = PointerAccelerator().apply { setNormalizedPosition(65500, 10) }
        val (nx, ny) = acc.applyDelta(5000f, -5000f, 4f, 1000f, 2f, 2f)
        assertEquals(65535, nx)
        assertEquals(0, ny)
    }
}
