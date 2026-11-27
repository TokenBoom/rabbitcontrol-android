package io.rabbitcontrol.android.ui.session

import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.nativebridge.DisplayPresetModel

data class ResolutionPresetSelection(
    val common: List<DisplayPresetModel>,
    val extra: List<DisplayPresetModel>,
)

private val PREFERRED_COMMON_WIDTHS = intArrayOf(3840, 2560, 1920, 1512, 1280, 1728, 1440)

fun formatCompactPresetLabel(preset: DisplayPresetModel): String {
    return if (preset.isNative) {
        AppStrings.get(
            R.string.session_res_native,
            "${preset.width}×${preset.height} 原生",
            preset.width,
            preset.height,
        )
    } else {
        "${preset.width}×${preset.height}"
    }
}

/**
 * Curates a compact set of 4-5 practical resolution presets for quick mobile switching while
 * keeping the remaining host modes accessible behind an optional "more" expansion.
 */
fun selectCommonResolutionPresets(
    presets: List<DisplayPresetModel>,
    currentWidth: Int,
    currentHeight: Int,
    maxCommon: Int = 5,
): ResolutionPresetSelection {
    if (presets.isEmpty() || maxCommon <= 0) {
        return ResolutionPresetSelection(emptyList(), emptyList())
    }

    val dedupedBySize = LinkedHashMap<Pair<Int, Int>, DisplayPresetModel>()
    for (p in presets) {
        if (p.width <= 0 || p.height <= 0) continue
        val key = p.width to p.height
        val existing = dedupedBySize[key]
        if (existing == null ||
            (!existing.isNative && p.isNative) ||
            (existing.isNative == p.isNative && !existing.isHidpi && p.isHidpi)
        ) {
            dedupedBySize[key] = p
        }
    }

    val sortedDesc = dedupedBySize.values
        .sortedByDescending { it.width.toLong() * it.height.toLong() }

    if (sortedDesc.size <= maxCommon) {
        return ResolutionPresetSelection(common = sortedDesc, extra = emptyList())
    }

    val selected = LinkedHashMap<Pair<Int, Int>, DisplayPresetModel>()
    fun addIfAbsent(preset: DisplayPresetModel?) {
        if (preset == null || selected.size >= maxCommon) return
        val key = preset.width to preset.height
        if (!selected.containsKey(key)) {
            selected[key] = preset
        }
    }

    // 1. Always preserve native and currently active resolutions.
    addIfAbsent(sortedDesc.firstOrNull { it.isNative })
    if (currentWidth > 0 && currentHeight > 0) {
        addIfAbsent(sortedDesc.firstOrNull { it.width == currentWidth && it.height == currentHeight })
    }

    // 2. Pick preferred common width tiers (preferring HiDPI entries).
    val pool = sortedDesc.filter { !selected.containsKey(it.width to it.height) }
    val hidpiFirst = pool.sortedByDescending { if (it.isHidpi) 1 else 0 }
    for (targetW in PREFERRED_COMMON_WIDTHS) {
        if (selected.size >= maxCommon) break
        val match = hidpiFirst.firstOrNull { it.width == targetW && !selected.containsKey(it.width to it.height) }
        addIfAbsent(match)
    }

    // 3. Fill remaining slots evenly from high to low resolution.
    val remaining = sortedDesc.filter { !selected.containsKey(it.width to it.height) }
    if (selected.size < maxCommon && remaining.isNotEmpty()) {
        val slotsLeft = maxCommon - selected.size
        if (remaining.size <= slotsLeft) {
            remaining.forEach { addIfAbsent(it) }
        } else {
            val step = (remaining.size - 1).toDouble() / slotsLeft.coerceAtLeast(1).toDouble()
            for (i in 0 until slotsLeft) {
                val idx = (i * step).toInt().coerceIn(0, remaining.lastIndex)
                addIfAbsent(remaining[idx])
            }
            for (cand in remaining) {
                if (selected.size >= maxCommon) break
                addIfAbsent(cand)
            }
        }
    }

    val common = selected.values.sortedByDescending { it.width.toLong() * it.height.toLong() }
    val commonKeys = common.mapTo(HashSet()) { it.width to it.height }
    val extra = sortedDesc.filter { (it.width to it.height) !in commonKeys }
    return ResolutionPresetSelection(common = common, extra = extra)
}
