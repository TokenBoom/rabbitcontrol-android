package com.rabbitcontrol.android.ui.session

import com.rabbitcontrol.android.core.nativebridge.DisplayPresetModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionPresetFilterTest {

    @Test
    fun returnsAllDedupedPresetsWhenWithinLimit() {
        val input = listOf(
            DisplayPresetModel(1920, 1080, "1920x1080", isNative = true, isHidpi = false),
            DisplayPresetModel(1920, 1080, "1920x1080 HiDPI", isNative = false, isHidpi = true),
            DisplayPresetModel(1280, 720, "1280x720", isNative = false, isHidpi = false),
        )
        val res = selectCommonResolutionPresets(input, currentWidth = 1920, currentHeight = 1080)
        assertEquals(2, res.common.size)
        assertTrue(res.extra.isEmpty())
        assertTrue(res.common.first().isNative)
    }

    @Test
    fun curatesMaxFivePresetsIncludingNativeAndCurrentAndPlacesRestInExtra() {
        val modes = listOf(
            3456 to 2234,
            3024 to 1964,
            2560 to 1600,
            2048 to 1330,
            1920 to 1200,
            1800 to 1169,
            1728 to 1117,
            1680 to 1050,
            1512 to 982,
            1440 to 900,
            1344 to 840,
            1280 to 800,
            1152 to 720,
            1024 to 640,
            840 to 525,
        ).map { (w, h) ->
            DisplayPresetModel(
                width = w,
                height = h,
                label = "${w}x${h}",
                isNative = (w == 3456 && h == 2234),
                isHidpi = (w >= 1280),
            )
        }

        val res = selectCommonResolutionPresets(modes, currentWidth = 1680, currentHeight = 1050, maxCommon = 5)
        assertEquals(5, res.common.size)
        assertEquals(10, res.extra.size)
        assertTrue(res.common.any { it.isNative && it.width == 3456 })
        assertTrue(res.common.any { it.width == 1680 && it.height == 1050 })
        // Verify sorted descending by area
        val areas = res.common.map { it.width.toLong() * it.height.toLong() }
        assertEquals(areas.sortedDescending(), areas)
    }
}
