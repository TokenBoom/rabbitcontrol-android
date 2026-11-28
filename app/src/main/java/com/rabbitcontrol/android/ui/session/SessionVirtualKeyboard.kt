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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.input.ScancodeMapper
import com.rabbitcontrol.android.ui.theme.RabbitElevation
import com.rabbitcontrol.android.ui.theme.SessionPalette
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class VirtualKeyRole {
    Standard,
    Modifier,
    CapsLock,
    FnToggle,
}

internal data class VirtualKeySpec(
    val primaryLabel: String,
    val shiftedLabel: String? = null,
    val scancode: Int = 0,
    val weight: Float = 1f,
    val role: VirtualKeyRole = VirtualKeyRole.Standard,
    val modifierBit: Int = 0,
    val repeatable: Boolean = false,
)

internal fun virtualKeyboardKeyRows(
    fnActive: Boolean,
    isMacHost: Boolean,
    capsLabel: String = "Caps",
    spaceLabel: String = "Space",
    fnLabel: String = "Fn",
): List<List<VirtualKeySpec>> {
    val row1 = if (fnActive) {
        listOf(
            VirtualKeySpec("Esc", scancode = ScancodeMapper.SC_ESC, weight = 1.05f),
            VirtualKeySpec("F1", scancode = ScancodeMapper.SC_F1),
            VirtualKeySpec("F2", scancode = ScancodeMapper.SC_F2),
            VirtualKeySpec("F3", scancode = ScancodeMapper.SC_F3),
            VirtualKeySpec("F4", scancode = ScancodeMapper.SC_F4),
            VirtualKeySpec("F5", scancode = ScancodeMapper.SC_F5),
            VirtualKeySpec("F6", scancode = ScancodeMapper.SC_F6),
            VirtualKeySpec("F7", scancode = ScancodeMapper.SC_F7),
            VirtualKeySpec("F8", scancode = ScancodeMapper.SC_F8),
            VirtualKeySpec("F9", scancode = ScancodeMapper.SC_F9),
            VirtualKeySpec("F10", scancode = ScancodeMapper.SC_F10),
            VirtualKeySpec("F11", scancode = ScancodeMapper.SC_F11),
            VirtualKeySpec("F12", scancode = ScancodeMapper.SC_F12),
            VirtualKeySpec("Del", scancode = ScancodeMapper.SC_DELETE, weight = 1.35f, repeatable = true),
        )
    } else {
        listOf(
            VirtualKeySpec("`", "~", scancode = 0x29),
            VirtualKeySpec("1", "!", scancode = 0x02),
            VirtualKeySpec("2", "@", scancode = 0x03),
            VirtualKeySpec("3", "#", scancode = 0x04),
            VirtualKeySpec("4", "$", scancode = 0x05),
            VirtualKeySpec("5", "%", scancode = 0x06),
            VirtualKeySpec("6", "^", scancode = 0x07),
            VirtualKeySpec("7", "&", scancode = 0x08),
            VirtualKeySpec("8", "*", scancode = 0x09),
            VirtualKeySpec("9", "(", scancode = 0x0A),
            VirtualKeySpec("0", ")", scancode = 0x0B),
            VirtualKeySpec("-", "_", scancode = 0x0C),
            VirtualKeySpec("=", "+", scancode = 0x0D),
            VirtualKeySpec("⌫", scancode = ScancodeMapper.SC_BACKSPACE, weight = 1.45f, repeatable = true),
        )
    }

    val row2 = listOf(
        VirtualKeySpec("Tab", scancode = ScancodeMapper.SC_TAB, weight = 1.35f),
        VirtualKeySpec("q", "Q", scancode = 0x10),
        VirtualKeySpec("w", "W", scancode = 0x11),
        VirtualKeySpec("e", "E", scancode = 0x12),
        VirtualKeySpec("r", "R", scancode = 0x13),
        VirtualKeySpec("t", "T", scancode = 0x14),
        VirtualKeySpec("y", "Y", scancode = 0x15),
        VirtualKeySpec("u", "U", scancode = 0x16),
        VirtualKeySpec("i", "I", scancode = 0x17),
        VirtualKeySpec("o", "O", scancode = 0x18),
        VirtualKeySpec("p", "P", scancode = 0x19),
        VirtualKeySpec("[", "{", scancode = 0x1A),
        VirtualKeySpec("]", "}", scancode = 0x1B),
        VirtualKeySpec("\\", "|", scancode = 0x2B, weight = 1.15f),
    )

    val row3 = listOf(
        VirtualKeySpec(capsLabel, scancode = ScancodeMapper.SC_CAPS_LOCK, weight = 1.55f, role = VirtualKeyRole.CapsLock),
        VirtualKeySpec("a", "A", scancode = 0x1E),
        VirtualKeySpec("s", "S", scancode = 0x1F),
        VirtualKeySpec("d", "D", scancode = 0x20),
        VirtualKeySpec("f", "F", scancode = 0x21),
        VirtualKeySpec("g", "G", scancode = 0x22),
        VirtualKeySpec("h", "H", scancode = 0x23),
        VirtualKeySpec("j", "J", scancode = 0x24),
        VirtualKeySpec("k", "K", scancode = 0x25),
        VirtualKeySpec("l", "L", scancode = 0x26),
        VirtualKeySpec(";", ":", scancode = 0x27),
        VirtualKeySpec("'", "\"", scancode = 0x28),
        VirtualKeySpec("↵", scancode = ScancodeMapper.SC_ENTER, weight = 1.75f),
    )

    val row4 = listOf(
        VirtualKeySpec(
            "Shift",
            scancode = ScancodeMapper.SC_LEFT_SHIFT,
            weight = 1.75f,
            role = VirtualKeyRole.Modifier,
            modifierBit = ScancodeMapper.MOD_LEFT_SHIFT,
        ),
        VirtualKeySpec("z", "Z", scancode = 0x2C),
        VirtualKeySpec("x", "X", scancode = 0x2D),
        VirtualKeySpec("c", "C", scancode = 0x2E),
        VirtualKeySpec("v", "V", scancode = 0x2F),
        VirtualKeySpec("b", "B", scancode = 0x30),
        VirtualKeySpec("n", "N", scancode = 0x31),
        VirtualKeySpec("m", "M", scancode = 0x32),
        VirtualKeySpec(",", "<", scancode = 0x33),
        VirtualKeySpec(".", ">", scancode = 0x34),
        VirtualKeySpec("/", "?", scancode = 0x35),
        VirtualKeySpec("↑", scancode = ScancodeMapper.SC_UP, weight = 1.05f, repeatable = true),
        VirtualKeySpec(
            "Shift",
            scancode = ScancodeMapper.SC_RIGHT_SHIFT,
            weight = 1.25f,
            role = VirtualKeyRole.Modifier,
            modifierBit = ScancodeMapper.MOD_RIGHT_SHIFT,
        ),
    )

    val guiLabel = if (isMacHost) "⌘" else "Win"
    val altLabel = if (isMacHost) "⌥" else "Alt"
    val row5 = listOf(
        VirtualKeySpec(fnLabel, weight = 1.05f, role = VirtualKeyRole.FnToggle),
        VirtualKeySpec(
            "Ctrl",
            scancode = ScancodeMapper.SC_LEFT_CTRL,
            weight = 1.15f,
            role = VirtualKeyRole.Modifier,
            modifierBit = ScancodeMapper.MOD_LEFT_CTRL,
        ),
        VirtualKeySpec(
            guiLabel,
            scancode = ScancodeMapper.SC_LEFT_GUI,
            weight = 1.10f,
            role = VirtualKeyRole.Modifier,
            modifierBit = ScancodeMapper.MOD_LEFT_GUI,
        ),
        VirtualKeySpec(
            altLabel,
            scancode = ScancodeMapper.SC_LEFT_ALT,
            weight = 1.10f,
            role = VirtualKeyRole.Modifier,
            modifierBit = ScancodeMapper.MOD_LEFT_ALT,
        ),
        VirtualKeySpec(spaceLabel, scancode = ScancodeMapper.SC_SPACE, weight = 4.6f, repeatable = true),
        VirtualKeySpec("←", scancode = ScancodeMapper.SC_LEFT, weight = 1.0f, repeatable = true),
        VirtualKeySpec("↓", scancode = ScancodeMapper.SC_DOWN, weight = 1.0f, repeatable = true),
        VirtualKeySpec("→", scancode = ScancodeMapper.SC_RIGHT, weight = 1.0f, repeatable = true),
    )

    return listOf(row1, row2, row3, row4, row5)
}

@Composable
fun SessionVirtualKeyboard(
    isPortrait: Boolean,
    isTablet: Boolean = false,
    isMacHost: Boolean,
    stickyMods: Int,
    dockedTop: Boolean = false,
    onToggleMod: (bit: Int, scancode: Int) -> Unit,
    onPressKey: (scancode: Int) -> Unit,
    onRepeatKey: (scancode: Int) -> Unit,
    onReleaseKey: (scancode: Int) -> Unit,
    onTapComboKey: (scancode: Int, extraModBit: Int, extraModScancode: Int?) -> Unit,
    onPushClipboard: () -> Unit,
    onSwitchToSystemIme: () -> Unit,
    onToggleDock: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fnActive by remember { mutableStateOf(false) }
    var capsActive by remember { mutableStateOf(false) }
    var panelAlpha by remember(isPortrait) {
        mutableFloatStateOf(if (isPortrait) 0.88f else 0.72f)
    }
    val keyAlpha = (panelAlpha * 0.85f).coerceIn(0.28f, 0.95f)
    val chipAlpha = (panelAlpha * 0.88f).coerceIn(0.32f, 0.95f)
    val borderAlpha = (panelAlpha * 0.75f + 0.22f).coerceIn(0.35f, 0.95f)

    val shiftHeld = (stickyMods and (ScancodeMapper.MOD_LEFT_SHIFT or ScancodeMapper.MOD_RIGHT_SHIFT)) != 0
    val primaryModBit = if (isMacHost) ScancodeMapper.MOD_LEFT_GUI else ScancodeMapper.MOD_LEFT_CTRL
    val primaryModSc = if (isMacHost) ScancodeMapper.SC_LEFT_GUI else ScancodeMapper.SC_LEFT_CTRL
    val primaryLabel = if (isMacHost) "⌘" else "Ctrl"

    val capsLabel = stringResource(R.string.kbd_caps)
    val spaceLabel = stringResource(R.string.kbd_space)
    val fnLabel = stringResource(R.string.kbd_fn)
    val rows = remember(fnActive, isMacHost, capsLabel, spaceLabel, fnLabel) {
        virtualKeyboardKeyRows(
            fnActive = fnActive,
            isMacHost = isMacHost,
            capsLabel = capsLabel,
            spaceLabel = spaceLabel,
            fnLabel = fnLabel,
        )
    }

    val compact = !isPortrait && !isTablet
    val keyHeight = when {
        isTablet -> 40.dp
        isPortrait -> 34.dp
        else -> 25.dp
    }
    val rowGap = when {
        isTablet -> 5.dp
        isPortrait -> 3.5.dp
        else -> 2.5.dp
    }
    val keyGap = if (isTablet) 4.dp else 2.5.dp
    val curOnDrag by rememberUpdatedState(onDrag)
    val curOnDragEnd by rememberUpdatedState(onDragEnd)

    Surface(
        modifier = modifier
            .then(
                when {
                    dockedTop -> Modifier.statusBarsPadding().padding(top = 4.dp)
                    isPortrait -> Modifier.navigationBarsPadding().padding(bottom = 4.dp)
                    else -> Modifier.padding(bottom = 6.dp)
                },
            )
            .padding(horizontal = if (isPortrait) 4.dp else 8.dp)
            .widthIn(max = if (isTablet) 780.dp else 480.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SessionPalette.chromeElevated.copy(alpha = panelAlpha),
        tonalElevation = 0.dp,
        shadowElevation = RabbitElevation.Sheet,
        border = BorderStroke(1.dp, SessionPalette.border.copy(alpha = borderAlpha)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isPortrait) 4.dp else 5.dp, vertical = if (isPortrait) 4.dp else 3.dp),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            // Drag grip + Top bar: Mode Switcher + Quick Shortcuts + Opacity + Dock Toggle + Close
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { curOnDragEnd() },
                            onDragCancel = { curOnDragEnd() },
                        ) { change, dragAmount ->
                            change.consume()
                            curOnDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp, bottom = 2.dp)
                        .width(32.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(SessionPalette.muted.copy(alpha = 0.55f)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isPortrait) 30.dp else 26.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { curOnDragEnd() },
                            onDragCancel = { curOnDragEnd() },
                        ) { change, dragAmount ->
                            change.consume()
                            curOnDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VirtualModeChip(
                    label = stringResource(R.string.kbd_mode_system),
                    selected = false,
                    chipAlpha = chipAlpha,
                    borderAlpha = borderAlpha,
                    compact = compact,
                    onClick = onSwitchToSystemIme,
                )
                Spacer(modifier = Modifier.width(3.dp))
                VirtualModeChip(
                    label = stringResource(R.string.kbd_mode_virtual),
                    selected = true,
                    chipAlpha = chipAlpha,
                    borderAlpha = borderAlpha,
                    compact = compact,
                    onClick = {},
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .width(1.dp)
                        .height(14.dp)
                        .background(SessionPalette.border.copy(alpha = borderAlpha)),
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactQuickChip("${primaryLabel}+C", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(0x2E, primaryModBit, primaryModSc)
                    }
                    CompactQuickChip("${primaryLabel}+V", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(0x2F, primaryModBit, primaryModSc)
                    }
                    CompactQuickChip("${primaryLabel}+Z", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(0x2C, primaryModBit, primaryModSc)
                    }
                    CompactQuickChip("${primaryLabel}+A", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(0x1E, primaryModBit, primaryModSc)
                    }
                    if (isMacHost) {
                        CompactQuickChip("⌘Tab", chipAlpha, borderAlpha, compact) {
                            onTapComboKey(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI)
                        }
                        CompactQuickChip("⌘Space", chipAlpha, borderAlpha, compact) {
                            onTapComboKey(ScancodeMapper.SC_SPACE, ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI)
                        }
                    } else {
                        CompactQuickChip("Alt+Tab", chipAlpha, borderAlpha, compact) {
                            onTapComboKey(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT)
                        }
                    }
                    CompactQuickChip("Home", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(ScancodeMapper.SC_HOME, 0, null)
                    }
                    CompactQuickChip("End", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(ScancodeMapper.SC_END, 0, null)
                    }
                    CompactQuickChip("PgUp", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(ScancodeMapper.SC_PAGE_UP, 0, null)
                    }
                    CompactQuickChip("PgDn", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(ScancodeMapper.SC_PAGE_DOWN, 0, null)
                    }
                    CompactQuickChip("Ins", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(ScancodeMapper.SC_INSERT, 0, null)
                    }
                    CompactQuickChip("Del", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(ScancodeMapper.SC_DELETE, 0, null)
                    }
                    CompactQuickChip("PrtSc", chipAlpha, borderAlpha, compact) {
                        onTapComboKey(ScancodeMapper.SC_PRINT_SCREEN, 0, null)
                    }
                    CompactQuickChip(stringResource(R.string.kbd_send_clip), chipAlpha, borderAlpha, compact, onClick = onPushClipboard)
                }

                Spacer(modifier = Modifier.width(4.dp))

                OpacityCycleChip(
                    alpha = panelAlpha,
                    chipAlpha = chipAlpha,
                    borderAlpha = borderAlpha,
                    compact = compact,
                    onClick = {
                        panelAlpha = when {
                            panelAlpha > 0.80f -> 0.72f
                            panelAlpha > 0.55f -> 0.42f
                            else -> 0.94f
                        }
                    },
                )

                IconButton(
                    onClick = onToggleDock,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = if (dockedTop) Icons.Rounded.VerticalAlignBottom else Icons.Rounded.VerticalAlignTop,
                        contentDescription = stringResource(
                            if (dockedTop) R.string.kbd_cd_dock_bottom else R.string.kbd_cd_dock_top,
                        ),
                        tint = SessionPalette.muted,
                        modifier = Modifier.size(15.dp),
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.kbd_cd_collapse),
                        tint = SessionPalette.muted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            HorizontalDivider(color = SessionPalette.border.copy(alpha = (borderAlpha * 0.7f)), thickness = 1.dp)

            // 5-row full QWERTY grid
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keyGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { spec ->
                        val isLetter = spec.primaryLabel.length == 1 && spec.primaryLabel[0] in 'a'..'z'
                        val displayLabel = when {
                            isLetter -> if (capsActive xor shiftHeld) spec.primaryLabel.uppercase() else spec.primaryLabel
                            shiftHeld && spec.shiftedLabel != null -> spec.shiftedLabel
                            else -> spec.primaryLabel
                        }
                        val subHint = if (!isLetter && !shiftHeld && spec.shiftedLabel != null) spec.shiftedLabel else null

                        val isHighlighted = when (spec.role) {
                            VirtualKeyRole.Modifier -> (stickyMods and spec.modifierBit) != 0
                            VirtualKeyRole.CapsLock -> capsActive
                            VirtualKeyRole.FnToggle -> fnActive
                            VirtualKeyRole.Standard -> false
                        }
                        val isSpecialBg = spec.role != VirtualKeyRole.Standard ||
                            spec.scancode in setOf(
                                ScancodeMapper.SC_BACKSPACE,
                                ScancodeMapper.SC_TAB,
                                ScancodeMapper.SC_ENTER,
                                ScancodeMapper.SC_DELETE,
                            )

                        VirtualKeyCap(
                            label = displayLabel,
                            subHint = subHint,
                            keyHeight = keyHeight,
                            keyAlpha = keyAlpha,
                            borderAlpha = borderAlpha,
                            compact = compact,
                            highlighted = isHighlighted,
                            specialKey = isSpecialBg,
                            showIndicatorDot = spec.role == VirtualKeyRole.CapsLock,
                            dotOn = capsActive,
                            repeatable = spec.repeatable && spec.role == VirtualKeyRole.Standard,
                            onPress = {
                                when (spec.role) {
                                    VirtualKeyRole.Modifier -> onToggleMod(spec.modifierBit, spec.scancode)
                                    VirtualKeyRole.FnToggle -> fnActive = !fnActive
                                    VirtualKeyRole.CapsLock -> {
                                        capsActive = !capsActive
                                        onTapComboKey(spec.scancode, 0, null)
                                    }
                                    VirtualKeyRole.Standard -> onPressKey(spec.scancode)
                                }
                            },
                            onRepeat = {
                                if (spec.role == VirtualKeyRole.Standard) {
                                    onRepeatKey(spec.scancode)
                                }
                            },
                            onRelease = {
                                if (spec.role == VirtualKeyRole.Standard) {
                                    onReleaseKey(spec.scancode)
                                }
                            },
                            modifier = Modifier.weight(spec.weight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualKeyCap(
    label: String,
    subHint: String?,
    keyHeight: Dp,
    keyAlpha: Float,
    borderAlpha: Float,
    compact: Boolean,
    highlighted: Boolean,
    specialKey: Boolean,
    showIndicatorDot: Boolean,
    dotOn: Boolean,
    repeatable: Boolean,
    onPress: () -> Unit,
    onRepeat: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var fingerDown by remember { mutableStateOf(false) }

    val curOnPress by rememberUpdatedState(onPress)
    val curOnRepeat by rememberUpdatedState(onRepeat)
    val curOnRelease by rememberUpdatedState(onRelease)

    val bgColor = when {
        fingerDown -> SessionPalette.accent.copy(alpha = 0.88f)
        highlighted -> SessionPalette.accent.copy(alpha = (keyAlpha + 0.22f).coerceAtMost(0.96f))
        specialKey -> SessionPalette.chrome.copy(alpha = keyAlpha)
        else -> SessionPalette.surface.copy(alpha = keyAlpha)
    }
    val borderColor = when {
        fingerDown || highlighted -> SessionPalette.accent
        else -> SessionPalette.border.copy(alpha = borderAlpha)
    }
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .height(keyHeight)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .pointerInput(repeatable) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    fingerDown = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    curOnPress()
                    val repeatJob = if (repeatable) {
                        scope.launch {
                            delay(380L)
                            while (true) {
                                curOnRepeat()
                                delay(65L)
                            }
                        }
                    } else {
                        null
                    }
                    waitForUpOrCancellation()
                    repeatJob?.cancel()
                    fingerDown = false
                    curOnRelease()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (subHint != null) {
            Text(
                text = subHint,
                color = if (fingerDown || highlighted) Color.White.copy(alpha = 0.85f) else SessionPalette.muted.copy(alpha = 0.92f),
                fontSize = if (compact) 7.sp else 8.sp,
                lineHeight = 8.sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 2.dp, top = 1.dp),
            )
        }
        if (showIndicatorDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (dotOn) Color(0xFF34C759) else SessionPalette.muted.copy(alpha = 0.35f)),
            )
        }
        Text(
            text = label,
            color = if (fingerDown || highlighted) Color.White else SessionPalette.text,
            fontSize = when {
                label.length >= 4 -> 9.sp
                label.length >= 2 -> 10.sp
                else -> if (compact) 12.sp else 13.sp
            },
            fontWeight = if (label.length == 1) FontWeight.Medium else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun VirtualModeChip(
    label: String,
    selected: Boolean,
    chipAlpha: Float,
    borderAlpha: Float,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .height(if (compact) 24.dp else 28.dp)
            .clip(shape)
            .background(if (selected) SessionPalette.accent.copy(alpha = 0.92f) else SessionPalette.surface.copy(alpha = chipAlpha))
            .border(1.dp, if (selected) SessionPalette.accent else SessionPalette.border.copy(alpha = borderAlpha), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(
            text = label,
            color = if (selected) Color.White else SessionPalette.text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactQuickChip(
    label: String,
    chipAlpha: Float,
    borderAlpha: Float,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .height(if (compact) 22.dp else 26.dp)
            .clip(shape)
            .background(SessionPalette.surface.copy(alpha = chipAlpha))
            .border(1.dp, SessionPalette.border.copy(alpha = borderAlpha), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = SessionPalette.text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun OpacityCycleChip(
    alpha: Float,
    chipAlpha: Float,
    borderAlpha: Float,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .height(if (compact) 22.dp else 26.dp)
            .clip(shape)
            .background(SessionPalette.surface.copy(alpha = chipAlpha))
            .border(1.dp, SessionPalette.border.copy(alpha = borderAlpha), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Opacity,
            contentDescription = stringResource(R.string.kbd_cd_opacity),
            tint = SessionPalette.muted,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "${(alpha * 100).roundToInt()}%",
            color = SessionPalette.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
