package io.rabbitcontrol.android.ui.session

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.ScreenshotMonitor
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rabbitcontrol.android.core.data.QuickActionCategory
import io.rabbitcontrol.android.core.data.QuickActionKind
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.SessionPalette

enum class SidebarSide(val title: String) {
    Left("左侧快捷握持区"),
    Right("右侧自定义按钮"),
}

@Composable
fun LandscapeLeftSidebar(
    slots: List<QuickActionKind>,
    zoomScale: Float,
    rightClickHeld: Boolean,
    dragModHeld: Boolean,
    dragLocked: Boolean,
    audioMuted: Boolean,
    showHud: Boolean,
    keymapEnabled: Boolean,
    onHoldRightClick: (Boolean) -> Unit,
    onHoldDrag: (Boolean) -> Unit,
    onSlotClick: (index: Int, action: QuickActionKind) -> Unit,
    onSlotLongClick: (index: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(SessionPalette.canvas)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
    ) {
        Column(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight(),
        ) {
            (0 until 3).forEach { idx ->
                val action = slots.getOrElse(idx) { QuickActionKind.Empty }
                if (idx > 0) {
                    HorizontalDivider(color = SessionPalette.border.copy(alpha = 0.65f), thickness = 1.dp)
                }

                when (action) {
                    QuickActionKind.HoldRightClick -> {
                        HoldModifierSlotButton(
                            icon = quickActionIcon(action),
                            label = if (rightClickHeld) "右键中" else action.shortLabel,
                            held = rightClickHeld,
                            activeTint = Color(0xFF4C8DFF),
                            onHoldChanged = onHoldRightClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    QuickActionKind.HoldDrag -> {
                        HoldModifierSlotButton(
                            icon = quickActionIcon(action),
                            label = if (dragModHeld) "拖动中" else action.shortLabel,
                            held = dragModHeld,
                            activeTint = Color(0xFF34C759),
                            onHoldChanged = onHoldDrag,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    else -> {
                        val isActive = isQuickActionActive(
                            action = action,
                            zoomScale = zoomScale,
                            dragLocked = dragLocked,
                            audioMuted = audioMuted,
                            showHud = showHud,
                            keymapEnabled = keymapEnabled,
                            keyboardVisible = false,
                        )
                        val label = quickActionDynamicLabel(action, zoomScale, dragLocked)
                        SidebarSlotButton(
                            icon = if (action == QuickActionKind.Empty) null else quickActionIcon(action),
                            label = if (action == QuickActionKind.Empty) "" else label,
                            active = isActive,
                            dimmed = action == QuickActionKind.Empty,
                            onClick = { onSlotClick(idx, action) },
                            onLongClick = { onSlotLongClick(idx) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(SessionPalette.border.copy(alpha = 0.7f)),
        )
    }
}

@Composable
fun LandscapeRightSidebar(
    slots: List<QuickActionKind>,
    zoomScale: Float,
    dragLocked: Boolean,
    audioMuted: Boolean,
    showHud: Boolean,
    keymapEnabled: Boolean,
    keyboardVisible: Boolean,
    onOpenControls: () -> Unit,
    onSlotClick: (index: Int, action: QuickActionKind) -> Unit,
    onSlotLongClick: (index: Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(SessionPalette.canvas)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End)),
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(SessionPalette.border.copy(alpha = 0.7f)),
        )
        Column(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight(),
        ) {
            // Fixed top core Control Center button
            SidebarSlotButton(
                icon = Icons.Rounded.Tune,
                label = "操作",
                active = false,
                onClick = onOpenControls,
                modifier = Modifier.weight(1f),
            )

            // 3 user-customizable shortcut buttons
            (0 until 3).forEach { idx ->
                val action = slots.getOrElse(idx) { QuickActionKind.Empty }
                HorizontalDivider(color = SessionPalette.border.copy(alpha = 0.65f), thickness = 1.dp)
                val isActive = isQuickActionActive(
                    action = action,
                    zoomScale = zoomScale,
                    dragLocked = dragLocked,
                    audioMuted = audioMuted,
                    showHud = showHud,
                    keymapEnabled = keymapEnabled,
                    keyboardVisible = keyboardVisible,
                )
                val label = quickActionDynamicLabel(action, zoomScale, dragLocked)
                SidebarSlotButton(
                    icon = quickActionIcon(action),
                    label = label,
                    active = isActive,
                    dimmed = action == QuickActionKind.Empty,
                    onClick = { onSlotClick(idx, action) },
                    onLongClick = { onSlotLongClick(idx) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun isQuickActionActive(
    action: QuickActionKind,
    zoomScale: Float,
    dragLocked: Boolean,
    audioMuted: Boolean,
    showHud: Boolean,
    keymapEnabled: Boolean,
    keyboardVisible: Boolean,
): Boolean = when (action) {
    QuickActionKind.ZoomIn, QuickActionKind.ZoomOut -> zoomScale > 1.02f
    QuickActionKind.DragLock -> dragLocked
    QuickActionKind.ToggleMute -> audioMuted
    QuickActionKind.ToggleHud -> showHud
    QuickActionKind.ToggleKeymap -> keymapEnabled
    QuickActionKind.ToggleKeyboard -> keyboardVisible
    else -> false
}

private fun quickActionDynamicLabel(
    action: QuickActionKind,
    zoomScale: Float,
    dragLocked: Boolean,
): String = when {
    action == QuickActionKind.ZoomIn && zoomScale > 1.02f -> String.format("%.1fx", zoomScale)
    action == QuickActionKind.DragLock && dragLocked -> "拖动中"
    else -> action.shortLabel
}

@Composable
private fun HoldModifierSlotButton(
    icon: ImageVector,
    label: String,
    held: Boolean,
    activeTint: Color,
    onHoldChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val currentHold by rememberUpdatedState(onHoldChanged)

    DisposableEffect(Unit) {
        onDispose { currentHold(false) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (held) activeTint.copy(alpha = 0.28f) else Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentHold(true)
                    waitForUpOrCancellation()
                    currentHold(false)
                }
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (held) activeTint else SessionPalette.muted.copy(alpha = 0.80f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            color = if (held) Color.White else SessionPalette.muted.copy(alpha = 0.85f),
            fontSize = 10.sp,
            fontWeight = if (held) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SidebarSlotButton(
    icon: ImageVector?,
    label: String,
    active: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (active) SessionPalette.surfaceActive.copy(alpha = 0.48f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    active -> SessionPalette.accentText
                    dimmed -> SessionPalette.muted.copy(alpha = 0.55f)
                    else -> SessionPalette.text.copy(alpha = 0.90f)
                },
                modifier = Modifier.size(21.dp),
            )
        }
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                color = when {
                    active -> SessionPalette.accentText
                    dimmed -> SessionPalette.muted.copy(alpha = 0.65f)
                    else -> SessionPalette.muted
                },
                fontSize = 11.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SidebarSlotPickerOverlay(
    side: SidebarSide,
    initialSlotIndex: Int,
    currentSlots: List<QuickActionKind>,
    onSelectSlotAction: (slotIndex: Int, action: QuickActionKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var activeSlot by remember(side, initialSlotIndex) { mutableIntStateOf(initialSlotIndex.coerceIn(0, 2)) }
    val selectedAction = currentSlots.getOrElse(activeSlot) { QuickActionKind.Empty }

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
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.90f)
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
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        side.title,
                        color = SessionPalette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "长按侧边按钮可快速更换",
                        color = SessionPalette.muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Close, "关闭", tint = SessionPalette.text, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val slotTrackShape = RoundedCornerShape(10.dp)
                val slotItemShape = RoundedCornerShape(8.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(slotTrackShape)
                        .background(SessionPalette.trackpad)
                        .border(1.dp, SessionPalette.border, slotTrackShape)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    listOf(0, 1, 2).forEach { idx ->
                        val selected = idx == activeSlot
                        val act = currentSlots.getOrElse(idx) { QuickActionKind.Empty }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(slotItemShape)
                                .background(if (selected) SessionPalette.surfaceActive else Color.Transparent)
                                .then(
                                    if (selected) Modifier.border(1.dp, SessionPalette.accent, slotItemShape) else Modifier,
                                )
                                .clickable { activeSlot = idx },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "槽位${idx + 1} · ${act.shortLabel}",
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) SessionPalette.text else SessionPalette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickActionCategory.entries.forEach { category ->
                        val items = QuickActionKind.entries.filter { it.category == category }
                        if (items.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    category.label,
                                    color = SessionPalette.muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items.forEach { action ->
                                        val selected = action == selectedAction
                                        val shape = RoundedCornerShape(RabbitRadius.Md)
                                        Row(
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 38.dp)
                                                .clip(shape)
                                                .background(if (selected) SessionPalette.surfaceActive else SessionPalette.surface)
                                                .border(
                                                    1.dp,
                                                    if (selected) SessionPalette.accent else SessionPalette.border,
                                                    shape,
                                                )
                                                .clickable { onSelectSlotAction(activeSlot, action) }
                                                .padding(horizontal = 11.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                quickActionIcon(action),
                                                contentDescription = null,
                                                tint = if (selected) SessionPalette.accentText else SessionPalette.muted,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                action.label,
                                                color = SessionPalette.text,
                                                fontSize = 12.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            )
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
}

fun quickActionIcon(kind: QuickActionKind): ImageVector = when (kind) {
    QuickActionKind.ZoomIn -> Icons.Rounded.ZoomIn
    QuickActionKind.ZoomOut -> Icons.Rounded.ZoomOut
    QuickActionKind.ResetZoom -> Icons.Rounded.CenterFocusStrong
    QuickActionKind.ShowDesktop -> Icons.Rounded.DesktopWindows
    QuickActionKind.AllWindows -> Icons.Rounded.GridView
    QuickActionKind.SwitchWindow -> Icons.Rounded.SwapHoriz
    QuickActionKind.Screenshot -> Icons.Rounded.ScreenshotMonitor
    QuickActionKind.TaskManager -> Icons.AutoMirrored.Rounded.ViewList
    QuickActionKind.LockScreen -> Icons.Rounded.Lock
    QuickActionKind.Copy -> Icons.Rounded.ContentCopy
    QuickActionKind.Paste -> Icons.Rounded.ContentPaste
    QuickActionKind.Undo -> Icons.AutoMirrored.Rounded.Undo
    QuickActionKind.SelectAll -> Icons.Rounded.SelectAll
    QuickActionKind.Enter -> Icons.AutoMirrored.Rounded.KeyboardReturn
    QuickActionKind.Escape -> Icons.Rounded.Close
    QuickActionKind.LeftClick,
    QuickActionKind.RightClick,
    QuickActionKind.MiddleClick,
    QuickActionKind.HoldRightClick,
    -> Icons.Rounded.Mouse
    QuickActionKind.HoldDrag -> Icons.Rounded.DragIndicator
    QuickActionKind.DragLock -> Icons.Rounded.OpenWith
    QuickActionKind.ScrollUp -> Icons.Rounded.ArrowUpward
    QuickActionKind.ScrollDown -> Icons.Rounded.ArrowDownward
    QuickActionKind.ToggleKeyboard -> Icons.Rounded.Keyboard
    QuickActionKind.ToggleTouchMode -> Icons.Rounded.TouchApp
    QuickActionKind.ToggleMute -> Icons.AutoMirrored.Rounded.VolumeOff
    QuickActionKind.ToggleHud -> Icons.Rounded.Speed
    QuickActionKind.ToggleKeymap -> Icons.Rounded.SportsEsports
    QuickActionKind.ToggleOrientation -> Icons.Rounded.ScreenRotation
    QuickActionKind.Empty -> Icons.Rounded.Add
}
