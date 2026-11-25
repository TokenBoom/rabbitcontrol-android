package io.rabbitcontrol.android.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rabbitcontrol.android.core.input.ScancodeMapper
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.SessionPalette

@Composable
fun SessionKeyboardBar(
    isMacHost: Boolean,
    stickyMods: Int,
    onToggleMod: (bit: Int, scancode: Int) -> Unit,
    onTapKey: (scancode: Int, extraModBit: Int, extraModScancode: Int?) -> Unit,
    onShowIme: () -> Unit,
    onPushClipboard: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryModBit = if (isMacHost) ScancodeMapper.MOD_LEFT_GUI else ScancodeMapper.MOD_LEFT_CTRL
    val primaryModSc = if (isMacHost) ScancodeMapper.SC_LEFT_GUI else ScancodeMapper.SC_LEFT_CTRL
    val primaryLabel = if (isMacHost) "⌘" else "Ctrl"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = SessionPalette.chromeElevated,
        tonalElevation = 0.dp,
        shadowElevation = RabbitElevation.Floating,
        border = BorderStroke(1.dp, SessionPalette.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pinned left: system soft keyboard trigger
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SessionPalette.accent)
                    .clickable(onClick = onShowIme)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text("软键盘", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }

            KeyboardGroupDivider()

            // Scrollable middle: modifiers, macros, navigation
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionKeyChip(if (isMacHost) "⌘" else "Win", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_GUI) != 0) {
                    onToggleMod(ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI)
                }
                SessionKeyChip("Ctrl", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_CTRL) != 0) {
                    onToggleMod(ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL)
                }
                SessionKeyChip(if (isMacHost) "⌥" else "Alt", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_ALT) != 0) {
                    onToggleMod(ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT)
                }
                SessionKeyChip("Shift", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_SHIFT) != 0) {
                    onToggleMod(ScancodeMapper.MOD_LEFT_SHIFT, ScancodeMapper.SC_LEFT_SHIFT)
                }

                KeyboardGroupDivider(horizontalPadding = 2.dp)

                SessionKeyChip("${primaryLabel}+C") { onTapKey(0x2E, primaryModBit, primaryModSc) }
                SessionKeyChip("${primaryLabel}+V") { onTapKey(0x2F, primaryModBit, primaryModSc) }
                SessionKeyChip("${primaryLabel}+Z") { onTapKey(0x2C, primaryModBit, primaryModSc) }
                SessionKeyChip("${primaryLabel}+A") { onTapKey(0x1E, primaryModBit, primaryModSc) }
                if (isMacHost) {
                    SessionKeyChip("⌘Space") { onTapKey(ScancodeMapper.SC_SPACE, ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI) }
                    SessionKeyChip("⌘Tab") { onTapKey(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI) }
                } else {
                    SessionKeyChip("Alt+Tab") { onTapKey(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT) }
                }
                SessionKeyChip("Ctrl+C") { onTapKey(0x2E, ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL) }

                KeyboardGroupDivider(horizontalPadding = 2.dp)

                SessionKeyChip("Esc") { onTapKey(ScancodeMapper.SC_ESC, 0, null) }
                SessionKeyChip("Tab") { onTapKey(ScancodeMapper.SC_TAB, 0, null) }
                SessionKeyChip("←") { onTapKey(ScancodeMapper.SC_LEFT, 0, null) }
                SessionKeyChip("↑") { onTapKey(ScancodeMapper.SC_UP, 0, null) }
                SessionKeyChip("↓") { onTapKey(ScancodeMapper.SC_DOWN, 0, null) }
                SessionKeyChip("→") { onTapKey(ScancodeMapper.SC_RIGHT, 0, null) }
                SessionKeyChip("Del") { onTapKey(ScancodeMapper.SC_DELETE, 0, null) }
                SessionKeyChip("F5") { onTapKey(ScancodeMapper.SC_F5, 0, null) }
                SessionKeyChip("F11") { onTapKey(ScancodeMapper.SC_F11, 0, null) }
                SessionKeyChip("发剪贴板", onClick = onPushClipboard)
            }

            KeyboardGroupDivider()

            // Pinned right: always-visible close action
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "收起键盘条",
                    tint = SessionPalette.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun KeyboardGroupDivider(horizontalPadding: androidx.compose.ui.unit.Dp = 6.dp) {
    Box(
        modifier = Modifier
            .padding(horizontal = horizontalPadding)
            .width(1.dp)
            .height(18.dp)
            .background(SessionPalette.border),
    )
}

@Composable
fun SessionKeyChip(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(RabbitRadius.Sm)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp, minHeight = 38.dp)
            .clip(shape)
            .background(if (highlighted) SessionPalette.accent else SessionPalette.surface)
            .border(
                width = 1.dp,
                color = if (highlighted) SessionPalette.accent else SessionPalette.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (highlighted) Color.White else SessionPalette.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
