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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.input.ScancodeMapper
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.SessionPalette

@Composable
fun SessionKeyboardBar(
    isPortrait: Boolean = true,
    isTablet: Boolean = false,
    isMacHost: Boolean,
    stickyMods: Int,
    onToggleMod: (bit: Int, scancode: Int) -> Unit,
    onTapKey: (scancode: Int, extraModBit: Int, extraModScancode: Int?) -> Unit,
    onShowIme: () -> Unit,
    onSwitchToVirtual: () -> Unit,
    onPushClipboard: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = !isPortrait && !isTablet
    val itemHeight = when {
        isTablet -> 36.dp
        compact -> 30.dp
        else -> 34.dp
    }
    val primaryModBit = if (isMacHost) ScancodeMapper.MOD_LEFT_GUI else ScancodeMapper.MOD_LEFT_CTRL
    val primaryModSc = if (isMacHost) ScancodeMapper.SC_LEFT_GUI else ScancodeMapper.SC_LEFT_CTRL
    val primaryLabel = if (isMacHost) "⌘" else "Ctrl"

    Surface(
        modifier = modifier
            .padding(horizontal = if (isPortrait) 8.dp else 12.dp)
            .widthIn(
                max = when {
                    isTablet -> 680.dp
                    isPortrait -> 380.dp
                    else -> 460.dp
                },
            )
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SessionPalette.chromeElevated.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
        shadowElevation = RabbitElevation.Floating,
        border = BorderStroke(1.dp, SessionPalette.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pinned left: System IME trigger (active) + Switch to UU-style Virtual Full Keyboard
            Row(
                modifier = Modifier
                    .height(itemHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SessionPalette.accent)
                    .clickable(onClick = onShowIme)
                    .padding(horizontal = if (compact) 7.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    stringResource(R.string.kbd_mode_system),
                    color = Color.White,
                    fontSize = if (compact) 11.sp else 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Row(
                modifier = Modifier
                    .height(itemHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SessionPalette.surface)
                    .border(1.dp, SessionPalette.border, RoundedCornerShape(8.dp))
                    .clickable(onClick = onSwitchToVirtual)
                    .padding(horizontal = if (compact) 7.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.kbd_mode_virtual),
                    color = SessionPalette.text,
                    fontSize = if (compact) 11.sp else 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }

            KeyboardGroupDivider(horizontalPadding = if (compact) 4.dp else 6.dp)

            // Scrollable middle: modifiers, Backspace/Enter, macros, navigation
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SessionKeyChip(if (isMacHost) "⌘" else "Win", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_GUI) != 0, compact = compact) {
                    onToggleMod(ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI)
                }
                SessionKeyChip("Ctrl", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_CTRL) != 0, compact = compact) {
                    onToggleMod(ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL)
                }
                SessionKeyChip(if (isMacHost) "⌥" else "Alt", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_ALT) != 0, compact = compact) {
                    onToggleMod(ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT)
                }
                SessionKeyChip("Shift", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_SHIFT) != 0, compact = compact) {
                    onToggleMod(ScancodeMapper.MOD_LEFT_SHIFT, ScancodeMapper.SC_LEFT_SHIFT)
                }

                KeyboardGroupDivider(horizontalPadding = 2.dp)

                SessionKeyChip("${primaryLabel}+C", compact = compact) { onTapKey(0x2E, primaryModBit, primaryModSc) }
                SessionKeyChip("${primaryLabel}+V", compact = compact) { onTapKey(0x2F, primaryModBit, primaryModSc) }
                SessionKeyChip("${primaryLabel}+Z", compact = compact) { onTapKey(0x2C, primaryModBit, primaryModSc) }
                SessionKeyChip("${primaryLabel}+A", compact = compact) { onTapKey(0x1E, primaryModBit, primaryModSc) }
                if (isMacHost) {
                    SessionKeyChip("⌘Space", compact = compact) { onTapKey(ScancodeMapper.SC_SPACE, ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI) }
                    SessionKeyChip("⌘Tab", compact = compact) { onTapKey(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI) }
                    SessionKeyChip("Ctrl+C", compact = compact) { onTapKey(0x2E, ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL) }
                } else {
                    SessionKeyChip("Alt+Tab", compact = compact) { onTapKey(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT) }
                }

                KeyboardGroupDivider(horizontalPadding = 2.dp)

                SessionKeyChip("⌫", compact = compact) { onTapKey(ScancodeMapper.SC_BACKSPACE, 0, null) }
                SessionKeyChip("↵", compact = compact) { onTapKey(ScancodeMapper.SC_ENTER, 0, null) }
                SessionKeyChip("Esc", compact = compact) { onTapKey(ScancodeMapper.SC_ESC, 0, null) }
                SessionKeyChip("Tab", compact = compact) { onTapKey(ScancodeMapper.SC_TAB, 0, null) }
                SessionKeyChip("←", compact = compact) { onTapKey(ScancodeMapper.SC_LEFT, 0, null) }
                SessionKeyChip("↑", compact = compact) { onTapKey(ScancodeMapper.SC_UP, 0, null) }
                SessionKeyChip("↓", compact = compact) { onTapKey(ScancodeMapper.SC_DOWN, 0, null) }
                SessionKeyChip("→", compact = compact) { onTapKey(ScancodeMapper.SC_RIGHT, 0, null) }
                SessionKeyChip("Del", compact = compact) { onTapKey(ScancodeMapper.SC_DELETE, 0, null) }
                SessionKeyChip("F5", compact = compact) { onTapKey(ScancodeMapper.SC_F5, 0, null) }
                SessionKeyChip("F11", compact = compact) { onTapKey(ScancodeMapper.SC_F11, 0, null) }
                SessionKeyChip(stringResource(R.string.kbd_send_clip), compact = compact, onClick = onPushClipboard)
            }

            KeyboardGroupDivider(horizontalPadding = if (compact) 4.dp else 6.dp)

            // Pinned right: always-visible close action
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(itemHeight),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.kbd_cd_collapse),
                    tint = SessionPalette.muted,
                    modifier = Modifier.size(16.dp),
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
    compact: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(RabbitRadius.Sm)
    val bgColor = when {
        !enabled -> SessionPalette.surface.copy(alpha = 0.45f)
        highlighted -> SessionPalette.accent
        else -> SessionPalette.surface
    }
    val borderColor = when {
        !enabled -> SessionPalette.border.copy(alpha = 0.45f)
        highlighted -> SessionPalette.accent
        else -> SessionPalette.border
    }
    val textColor = when {
        !enabled -> SessionPalette.muted.copy(alpha = 0.55f)
        highlighted -> Color.White
        else -> SessionPalette.text
    }
    Box(
        modifier = Modifier
            .defaultMinSize(
                minWidth = if (compact) 32.dp else 36.dp,
                minHeight = if (compact) 30.dp else 34.dp,
            )
            .clip(shape)
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = shape,
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                horizontal = if (compact) 8.dp else 9.dp,
                vertical = if (compact) 5.dp else 6.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
