package com.rabbitcontrol.android.ui.session

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.data.KeyMapState
import com.rabbitcontrol.android.core.data.QuickActionKind
import com.rabbitcontrol.android.core.data.VirtualKeyItem
import com.rabbitcontrol.android.core.data.VirtualKeyKind
import com.rabbitcontrol.android.core.i18n.AppStrings
import com.rabbitcontrol.android.core.input.ScancodeMapper
import com.rabbitcontrol.android.session.Core
import com.rabbitcontrol.android.ui.theme.RabbitElevation
import com.rabbitcontrol.android.ui.theme.RabbitRadius
import com.rabbitcontrol.android.ui.theme.SessionPalette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun SessionKeyMappingOverlay(
    keymapState: KeyMapState,
    editing: Boolean,
    onExecuteQuickAction: (QuickActionKind) -> Unit,
    onSelectProfile: (String) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMoveItem: (String, Float, Float) -> Unit,
    onResizeItem: (String, Int) -> Unit,
    onAddItem: (VirtualKeyItem) -> Unit,
    onRemoveItem: (String) -> Unit,
    onResetProfile: () -> Unit,
    onDoneEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!keymapState.enabled && !editing) return

    var selectedItemId by remember(editing, keymapState.activeProfileId) { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val activeProfile = keymapState.activeProfile
    val selectedItem = activeProfile.items.firstOrNull { it.id == selectedItemId }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val maxH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

        // Slight dim veil while dragging layout in Edit Mode
        if (editing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x55000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { selectedItemId = null },
                    ),
            )
        }

        // Render all virtual keys & D-pads
        activeProfile.items.forEach { item ->
            val sizePx = with(density) { item.sizeDp.dp.toPx() }
            val leftPx = (item.xNorm * maxW - sizePx * 0.5f).coerceIn(0f, (maxW - sizePx).coerceAtLeast(0f))
            val topPx = (item.yNorm * maxH - sizePx * 0.5f).coerceIn(0f, (maxH - sizePx).coerceAtLeast(0f))

            if (editing) {
                EditableVirtualKeyItem(
                    item = item,
                    selected = item.id == selectedItemId,
                    leftPx = leftPx,
                    topPx = topPx,
                    maxW = maxW,
                    maxH = maxH,
                    onSelect = { selectedItemId = item.id },
                    onMoved = { newXNorm, newYNorm -> onMoveItem(item.id, newXNorm, newYNorm) },
                )
            } else {
                when (item.kind) {
                    VirtualKeyKind.DpadWasd, VirtualKeyKind.DpadArrows -> {
                        LiveVirtualDpad(
                            item = item,
                            opacity = keymapState.opacity,
                            leftPx = leftPx,
                            topPx = topPx,
                        )
                    }
                    else -> {
                        LiveVirtualKeyButton(
                            item = item,
                            opacity = keymapState.opacity,
                            leftPx = leftPx,
                            topPx = topPx,
                            onExecuteQuickAction = onExecuteQuickAction,
                        )
                    }
                }
            }
        }

        // Edit Mode Top Toolbar & Bottom Inspector
        if (editing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                shape = RoundedCornerShape(14.dp),
                color = SessionPalette.chromeElevated,
                tonalElevation = 0.dp,
                shadowElevation = RabbitElevation.Floating,
                border = BorderStroke(1.dp, SessionPalette.border),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            keymapState.profiles.forEach { profile ->
                                val active = profile.id == keymapState.activeProfileId
                                SessionKeyChip(profile.name, highlighted = active) {
                                    onSelectProfile(profile.id)
                                }
                            }
                        }
                        SessionKeyChip(stringResource(R.string.km_add_btn)) { showAddDialog = true }
                        IconButton(onClick = onResetProfile, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Refresh, stringResource(R.string.km_cd_reset), tint = SessionPalette.muted, modifier = Modifier.size(18.dp))
                        }
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SessionPalette.accent)
                                .clickable(onClick = onDoneEditing)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.common_done), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.cp_opacity), color = SessionPalette.muted, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = keymapState.opacity,
                            onValueChange = onOpacityChange,
                            valueRange = 0.25f..0.85f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = SessionPalette.accent,
                                inactiveTrackColor = SessionPalette.border,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${(keymapState.opacity * 100).roundToInt()}%", color = SessionPalette.text, fontSize = 11.sp)
                    }
                }
            }

            if (selectedItem != null) {
                val isDpad = selectedItem.kind == VirtualKeyKind.DpadWasd || selectedItem.kind == VirtualKeyKind.DpadArrows
                val smallSize = if (isDpad) 104 else 44
                val medSize = if (isDpad) 128 else 54
                val largeSize = if (isDpad) 152 else 68
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = SessionPalette.chromeElevated,
                    tonalElevation = 0.dp,
                    shadowElevation = RabbitElevation.Floating,
                    border = BorderStroke(1.dp, SessionPalette.border),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(selectedItem.label, color = SessionPalette.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        SessionKeyChip(stringResource(R.string.common_small), highlighted = selectedItem.sizeDp <= smallSize + 2) {
                            onResizeItem(selectedItem.id, smallSize)
                        }
                        SessionKeyChip(stringResource(R.string.common_medium), highlighted = selectedItem.sizeDp in (smallSize + 3)..(largeSize - 3)) {
                            onResizeItem(selectedItem.id, medSize)
                        }
                        SessionKeyChip(stringResource(R.string.common_large), highlighted = selectedItem.sizeDp >= largeSize - 2) {
                            onResizeItem(selectedItem.id, largeSize)
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SessionPalette.dangerSoft)
                                .clickable {
                                    onRemoveItem(selectedItem.id)
                                    selectedItemId = null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.km_cd_delete), tint = SessionPalette.dangerText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.km_editor_tip),
                    color = SessionPalette.text.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SessionPalette.chromeElevated.copy(alpha = 0.88f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (showAddDialog) {
        AddVirtualKeyDialog(
            onAdd = { item ->
                onAddItem(item)
                selectedItemId = item.id
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun LiveVirtualKeyButton(
    item: VirtualKeyItem,
    opacity: Float,
    leftPx: Float,
    topPx: Float,
    onExecuteQuickAction: (QuickActionKind) -> Unit,
) {
    val view = LocalView.current
    var pressed by remember(item.id) { mutableStateOf(false) }
    val currentAction by rememberUpdatedState(onExecuteQuickAction)

    DisposableEffect(item.id) {
        onDispose {
            if (pressed) {
                releaseVirtualItem(item)
                pressed = false
            }
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .size(item.sizeDp.dp)
            .alpha(if (pressed) 0.96f else opacity)
            .clip(CircleShape)
            .background(if (pressed) SessionPalette.accent else SessionPalette.chromeElevated.copy(alpha = 0.85f))
            .border(
                width = 1.2.dp,
                color = if (pressed) Color.White else Color.White.copy(alpha = 0.35f),
                shape = CircleShape,
            )
            .pointerInput(item.id, item.kind, item.scancode, item.modifiers, item.mouseButton, item.actionKind) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    when (item.kind) {
                        VirtualKeyKind.Key -> {
                            pressModifiersForVirtualItem(item)
                            Core.sendKey(item.scancode, true, item.modifiers)
                        }
                        VirtualKeyKind.MouseButton -> Core.sendMouseButton(item.mouseButton, true)
                        VirtualKeyKind.MouseWheel -> Core.sendMouseWheel(item.wheelDelta, false)
                        VirtualKeyKind.QuickAction -> item.actionKind?.let { currentAction(it) }
                        VirtualKeyKind.DpadWasd, VirtualKeyKind.DpadArrows -> Unit
                    }
                    waitForUpOrCancellation()
                    pressed = false
                    releaseVirtualItem(item)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.label,
            color = Color.White,
            fontSize = if (item.label.length >= 4) 11.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun virtualItemModifierScancodes(item: VirtualKeyItem): List<Int> {
    if (item.modifiers == 0) return emptyList()
    val sc = mutableListOf<Int>()
    fun addIf(bit: Int, code: Int) {
        if ((item.modifiers and bit) != 0 && item.scancode != code) sc.add(code)
    }
    addIf(ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL)
    addIf(ScancodeMapper.MOD_RIGHT_CTRL, ScancodeMapper.SC_RIGHT_CTRL)
    addIf(ScancodeMapper.MOD_LEFT_SHIFT, ScancodeMapper.SC_LEFT_SHIFT)
    addIf(ScancodeMapper.MOD_RIGHT_SHIFT, ScancodeMapper.SC_RIGHT_SHIFT)
    addIf(ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT)
    addIf(ScancodeMapper.MOD_RIGHT_ALT, ScancodeMapper.SC_RIGHT_ALT)
    addIf(ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI)
    addIf(ScancodeMapper.MOD_RIGHT_GUI, ScancodeMapper.SC_RIGHT_GUI)
    return sc
}

private fun pressModifiersForVirtualItem(item: VirtualKeyItem) {
    virtualItemModifierScancodes(item).forEach { modSc ->
        Core.sendKey(modSc, true, item.modifiers)
    }
}

private fun releaseVirtualItem(item: VirtualKeyItem) {
    when (item.kind) {
        VirtualKeyKind.Key -> {
            Core.sendKey(item.scancode, false, item.modifiers)
            virtualItemModifierScancodes(item).asReversed().forEach { modSc ->
                Core.sendKey(modSc, false, 0)
            }
        }
        VirtualKeyKind.MouseButton -> Core.sendMouseButton(item.mouseButton, false)
        else -> Unit
    }
}

@Composable
private fun LiveVirtualDpad(
    item: VirtualKeyItem,
    opacity: Float,
    leftPx: Float,
    topPx: Float,
) {
    val isWasd = item.kind == VirtualKeyKind.DpadWasd
    val upSc = if (isWasd) 0x11 else ScancodeMapper.SC_UP // W or Up
    val downSc = if (isWasd) 0x1F else ScancodeMapper.SC_DOWN // S or Down
    val leftSc = if (isWasd) 0x1E else ScancodeMapper.SC_LEFT // A or Left
    val rightSc = if (isDpadWasd(item)) 0x20 else ScancodeMapper.SC_RIGHT // D or Right
    val labels = if (isWasd) listOf("W", "S", "A", "D") else listOf("↑", "↓", "←", "→")

    var activeDirs by remember(item.id) { mutableStateOf(emptySet<Int>()) }
    var puckX by remember(item.id) { mutableFloatStateOf(0f) }
    var puckY by remember(item.id) { mutableFloatStateOf(0f) }

    fun updateDirections(next: Set<Int>) {
        if (next == activeDirs) return
        (activeDirs - next).forEach { sc -> Core.sendKey(sc, false, 0) }
        (next - activeDirs).forEach { sc -> Core.sendKey(sc, true, 0) }
        activeDirs = next
    }

    DisposableEffect(item.id) {
        onDispose {
            activeDirs.forEach { sc -> Core.sendKey(sc, false, 0) }
            activeDirs = emptySet()
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
            .size(item.sizeDp.dp)
            .alpha(opacity)
            .clip(CircleShape)
            .background(SessionPalette.chromeElevated.copy(alpha = 0.72f))
            .border(1.2.dp, Color.White.copy(alpha = 0.30f), CircleShape)
            .pointerInput(item.id, item.kind) {
                val cx = size.width * 0.5f
                val cy = size.height * 0.5f
                val maxR = minOf(cx, cy).coerceAtLeast(10f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun applyPos(px: Float, py: Float) {
                        val dx = px - cx
                        val dy = py - cy
                        val dist = hypot(dx, dy).coerceAtLeast(0.001f)
                        val clamped = dist.coerceAtMost(maxR * 0.65f)
                        puckX = (dx / dist) * clamped
                        puckY = (dy / dist) * clamped

                        val deadzone = maxR * 0.20f
                        val next = mutableSetOf<Int>()
                        if (dist >= deadzone) {
                            val nx = dx / dist
                            val ny = dy / dist
                            if (ny < -0.38f) next.add(upSc)
                            if (ny > 0.38f) next.add(downSc)
                            if (nx < -0.38f) next.add(leftSc)
                            if (nx > 0.38f) next.add(rightSc)
                        }
                        updateDirections(next)
                    }

                    applyPos(down.position.x, down.position.y)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ptr = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull()
                        if (ptr == null || !ptr.pressed) {
                            break
                        }
                        ptr.consume()
                        applyPos(ptr.position.x, ptr.position.y)
                    }
                    puckX = 0f
                    puckY = 0f
                    updateDirections(emptySet())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(labels[0], color = Color.White.copy(alpha = if (upSc in activeDirs) 1f else 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        Text(labels[1], color = Color.White.copy(alpha = if (downSc in activeDirs) 1f else 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        Text(labels[2], color = Color.White.copy(alpha = if (leftSc in activeDirs) 1f else 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp))
        Text(labels[3], color = Color.White.copy(alpha = if (rightSc in activeDirs) 1f else 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp))

        Box(
            modifier = Modifier
                .offset { IntOffset(puckX.roundToInt(), puckY.roundToInt()) }
                .size((item.sizeDp * 0.34f).dp)
                .clip(CircleShape)
                .background(if (activeDirs.isNotEmpty()) SessionPalette.accent else Color.White.copy(alpha = 0.25f))
                .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
        )
    }
}

private fun isDpadWasd(item: VirtualKeyItem): Boolean = item.kind == VirtualKeyKind.DpadWasd

@Composable
private fun EditableVirtualKeyItem(
    item: VirtualKeyItem,
    selected: Boolean,
    leftPx: Float,
    topPx: Float,
    maxW: Float,
    maxH: Float,
    onSelect: () -> Unit,
    onMoved: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { item.sizeDp.dp.toPx() }
    var dragDx by remember(item.id, item.xNorm) { mutableFloatStateOf(0f) }
    var dragDy by remember(item.id, item.yNorm) { mutableFloatStateOf(0f) }

    val curLeft = (leftPx + dragDx).coerceIn(0f, (maxW - sizePx).coerceAtLeast(0f))
    val curTop = (topPx + dragDy).coerceIn(0f, (maxH - sizePx).coerceAtLeast(0f))

    Box(
        modifier = Modifier
            .offset { IntOffset(curLeft.roundToInt(), curTop.roundToInt()) }
            .size(item.sizeDp.dp)
            .clip(CircleShape)
            .background(if (selected) SessionPalette.accent.copy(alpha = 0.65f) else SessionPalette.chromeElevated.copy(alpha = 0.88f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else SessionPalette.accentText,
                shape = CircleShape,
            )
            .clickable(onClick = onSelect)
            .pointerInput(item.id, maxW, maxH) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDragEnd = {
                        val finalCenterX = (curLeft + sizePx * 0.5f) / maxW
                        val finalCenterY = (curTop + sizePx * 0.5f) / maxH
                        dragDx = 0f
                        dragDy = 0f
                        onMoved(finalCenterX, finalCenterY)
                    },
                    onDragCancel = {
                        dragDx = 0f
                        dragDy = 0f
                    },
                ) { change, drag ->
                    change.consume()
                    dragDx += drag.x
                    dragDy += drag.y
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.label,
            color = Color.White,
            fontSize = if (item.label.length >= 4) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private data class KeyPresetCatalogItem(
    val label: String,
    val builder: (id: String) -> VirtualKeyItem,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddVirtualKeyDialog(
    onAdd: (VirtualKeyItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val idPrefix = remember { System.currentTimeMillis() }
    val groups = remember {
        val leftLbl = AppStrings.get(R.string.qa_left_click_short, "左键")
        val rightLbl = AppStrings.get(R.string.qa_right_click_short, "右键")
        val midLbl = AppStrings.get(R.string.qa_middle_click_short, "中键")
        val scrollUpLbl = AppStrings.get(R.string.qa_scroll_up_short, "上滚")
        val scrollDownLbl = AppStrings.get(R.string.qa_scroll_down_short, "下滚")
        val dpadShortLbl = AppStrings.get(R.string.preset_dpad_short, "方向")
        val desktopShortLbl = AppStrings.get(R.string.qa_show_desktop_short, "桌面")
        val copyLbl = AppStrings.get(R.string.qa_copy, "复制")
        val pasteLbl = AppStrings.get(R.string.qa_paste, "粘贴")
        val undoLbl = AppStrings.get(R.string.qa_undo, "撤销")
        val shotShortLbl = AppStrings.get(R.string.qa_screenshot_short, "截屏")
        val zoomInShortLbl = AppStrings.get(R.string.qa_zoom_in_short, "放大")
        val zoomOutShortLbl = AppStrings.get(R.string.qa_zoom_out_short, "缩小")
        listOf(
            AppStrings.get(R.string.km_cat_stick, "摇杆与方向") to listOf(
                KeyPresetCatalogItem(AppStrings.get(R.string.km_item_wasd, "WASD 摇杆")) { id ->
                    VirtualKeyItem(id, "WASD", VirtualKeyKind.DpadWasd, xNorm = 0.20f, yNorm = 0.65f, sizeDp = 128)
                },
                KeyPresetCatalogItem(AppStrings.get(R.string.km_item_dpad, "方向键摇杆")) { id ->
                    VirtualKeyItem(id, dpadShortLbl, VirtualKeyKind.DpadArrows, xNorm = 0.20f, yNorm = 0.65f, sizeDp = 124)
                },
            ),
            AppStrings.get(R.string.km_cat_mouse, "鼠标与滚轮") to listOf(
                KeyPresetCatalogItem(leftLbl) { id ->
                    VirtualKeyItem(id, leftLbl, VirtualKeyKind.MouseButton, mouseButton = 0, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 56)
                },
                KeyPresetCatalogItem(rightLbl) { id ->
                    VirtualKeyItem(id, rightLbl, VirtualKeyKind.MouseButton, mouseButton = 1, xNorm = 0.58f, yNorm = 0.50f, sizeDp = 54)
                },
                KeyPresetCatalogItem(midLbl) { id ->
                    VirtualKeyItem(id, midLbl, VirtualKeyKind.MouseButton, mouseButton = 2, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52)
                },
                KeyPresetCatalogItem(AppStrings.get(R.string.qa_scroll_up, "上滚")) { id ->
                    VirtualKeyItem(id, scrollUpLbl, VirtualKeyKind.MouseWheel, wheelDelta = 3, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 50)
                },
                KeyPresetCatalogItem(AppStrings.get(R.string.qa_scroll_down, "下滚")) { id ->
                    VirtualKeyItem(id, scrollDownLbl, VirtualKeyKind.MouseWheel, wheelDelta = -3, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 50)
                },
            ),
            AppStrings.get(R.string.km_cat_common, "常用按键") to listOf(
                KeyPresetCatalogItem("Space") { id -> VirtualKeyItem(id, "Space", VirtualKeyKind.Key, scancode = ScancodeMapper.SC_SPACE, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 58) },
                KeyPresetCatalogItem("Shift") { id -> VirtualKeyItem(id, "Shift", VirtualKeyKind.Key, scancode = ScancodeMapper.SC_LEFT_SHIFT, modifiers = ScancodeMapper.MOD_LEFT_SHIFT, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 54) },
                KeyPresetCatalogItem("Ctrl") { id -> VirtualKeyItem(id, "Ctrl", VirtualKeyKind.Key, scancode = ScancodeMapper.SC_LEFT_CTRL, modifiers = ScancodeMapper.MOD_LEFT_CTRL, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem("Alt") { id -> VirtualKeyItem(id, "Alt", VirtualKeyKind.Key, scancode = ScancodeMapper.SC_LEFT_ALT, modifiers = ScancodeMapper.MOD_LEFT_ALT, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 50) },
                KeyPresetCatalogItem("Esc") { id -> VirtualKeyItem(id, "Esc", VirtualKeyKind.Key, scancode = ScancodeMapper.SC_ESC, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 50) },
                KeyPresetCatalogItem("Tab") { id -> VirtualKeyItem(id, "Tab", VirtualKeyKind.Key, scancode = ScancodeMapper.SC_TAB, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 50) },
                KeyPresetCatalogItem("Enter") { id -> VirtualKeyItem(id, "Enter", VirtualKeyKind.Key, scancode = ScancodeMapper.SC_ENTER, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 56) },
                KeyPresetCatalogItem("E") { id -> VirtualKeyItem(id, "E", VirtualKeyKind.Key, scancode = 0x12, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem("F") { id -> VirtualKeyItem(id, "F", VirtualKeyKind.Key, scancode = 0x21, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem("Q") { id -> VirtualKeyItem(id, "Q", VirtualKeyKind.Key, scancode = 0x10, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem("R") { id -> VirtualKeyItem(id, "R", VirtualKeyKind.Key, scancode = 0x13, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem("Z") { id -> VirtualKeyItem(id, "Z", VirtualKeyKind.Key, scancode = 0x2C, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem("X") { id -> VirtualKeyItem(id, "X", VirtualKeyKind.Key, scancode = 0x2D, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem("C") { id -> VirtualKeyItem(id, "C", VirtualKeyKind.Key, scancode = 0x2E, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem("1") { id -> VirtualKeyItem(id, "1", VirtualKeyKind.Key, scancode = 0x02, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 48) },
                KeyPresetCatalogItem("2") { id -> VirtualKeyItem(id, "2", VirtualKeyKind.Key, scancode = 0x03, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 48) },
                KeyPresetCatalogItem("3") { id -> VirtualKeyItem(id, "3", VirtualKeyKind.Key, scancode = 0x04, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 48) },
            ),
            AppStrings.get(R.string.km_cat_shortcuts, "快捷组合操作") to listOf(
                KeyPresetCatalogItem(AppStrings.get(R.string.qa_show_desktop, "显示桌面")) { id -> VirtualKeyItem(id, desktopShortLbl, VirtualKeyKind.QuickAction, actionKind = QuickActionKind.ShowDesktop, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 54) },
                KeyPresetCatalogItem(copyLbl) { id -> VirtualKeyItem(id, copyLbl, VirtualKeyKind.QuickAction, actionKind = QuickActionKind.Copy, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 54) },
                KeyPresetCatalogItem(pasteLbl) { id -> VirtualKeyItem(id, pasteLbl, VirtualKeyKind.QuickAction, actionKind = QuickActionKind.Paste, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 54) },
                KeyPresetCatalogItem(undoLbl) { id -> VirtualKeyItem(id, undoLbl, VirtualKeyKind.QuickAction, actionKind = QuickActionKind.Undo, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 54) },
                KeyPresetCatalogItem(AppStrings.get(R.string.qa_screenshot, "截屏")) { id -> VirtualKeyItem(id, shotShortLbl, VirtualKeyKind.QuickAction, actionKind = QuickActionKind.Screenshot, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 54) },
                KeyPresetCatalogItem(AppStrings.get(R.string.qa_zoom_in, "放大")) { id -> VirtualKeyItem(id, zoomInShortLbl, VirtualKeyKind.QuickAction, actionKind = QuickActionKind.ZoomIn, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
                KeyPresetCatalogItem(AppStrings.get(R.string.qa_zoom_out, "缩小")) { id -> VirtualKeyItem(id, zoomOutShortLbl, VirtualKeyKind.QuickAction, actionKind = QuickActionKind.ZoomOut, xNorm = 0.50f, yNorm = 0.50f, sizeDp = 52) },
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SessionPalette.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .fillMaxHeight(0.82f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(RabbitRadius.Xl),
            color = SessionPalette.chromeElevated,
            tonalElevation = 0.dp,
            shadowElevation = RabbitElevation.Sheet,
            border = BorderStroke(1.dp, SessionPalette.border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.km_add_dialog_title),
                        color = SessionPalette.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = SessionPalette.text)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groups.forEachIndexed { gIdx, (groupName, entries) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(groupName, color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                entries.forEachIndexed { eIdx, entry ->
                                    SessionKeyChip(entry.label) {
                                        onAdd(entry.builder("vk_${idPrefix}_${gIdx}_$eIdx"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
