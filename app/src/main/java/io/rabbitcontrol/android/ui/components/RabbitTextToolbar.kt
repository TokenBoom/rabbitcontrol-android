package io.rabbitcontrol.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

internal data class TextToolbarMenuState(
    val version: Long,
    val rect: Rect,
    val onCut: (() -> Unit)?,
    val onCopy: (() -> Unit)?,
    val onPaste: (() -> Unit)?,
    val onSelectAll: (() -> Unit)?,
    val onAutofill: (() -> Unit)?,
)

private data class ToolbarActionItem(
    val label: String,
    val hideOnInvoke: Boolean,
    val onClick: () -> Unit,
)

/**
 * 自定义文本操作悬浮菜单（替代系统原生淡紫 Material 气泡），匹配应用亮/暗色细边框卡片风格。
 */
@Stable
class RabbitTextToolbar : TextToolbar {
    private val nextVersion = AtomicLong(1L)

    // status 必须是非快照字段：同屏未获焦的 TextField 会在组合期间读取 status，若读取快照状态会导致 showMenu 触发兄弟节点重组并立即调用 hide()
    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    internal var menuState by mutableStateOf<TextToolbarMenuState?>(null)
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        updateMenu(
            rect = rect,
            onCopy = onCopyRequested,
            onPaste = onPasteRequested,
            onCut = onCutRequested,
            onSelectAll = onSelectAllRequested,
            onAutofill = null,
        )
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) {
        updateMenu(
            rect = rect,
            onCopy = onCopyRequested,
            onPaste = onPasteRequested,
            onCut = onCutRequested,
            onSelectAll = onSelectAllRequested,
            onAutofill = onAutofillRequested,
        )
    }

    private fun updateMenu(
        rect: Rect,
        onCopy: (() -> Unit)?,
        onPaste: (() -> Unit)?,
        onCut: (() -> Unit)?,
        onSelectAll: (() -> Unit)?,
        onAutofill: (() -> Unit)?,
    ) {
        if (onCopy == null && onPaste == null && onCut == null && onSelectAll == null && onAutofill == null) {
            status = TextToolbarStatus.Hidden
            menuState = null
            return
        }
        status = TextToolbarStatus.Shown
        menuState = TextToolbarMenuState(
            version = nextVersion.getAndIncrement(),
            rect = rect,
            onCut = onCut,
            onCopy = onCopy,
            onPaste = onPaste,
            onSelectAll = onSelectAll,
            onAutofill = onAutofill,
        )
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        menuState = null
    }
}

@Composable
fun ProvideRabbitTextToolbar(
    content: @Composable () -> Unit,
) {
    val toolbar = remember { RabbitTextToolbar() }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
        RabbitTextToolbarPopup(toolbar = toolbar)
    }
}

@Composable
private fun RabbitTextToolbarPopup(
    toolbar: RabbitTextToolbar,
) {
    val state = toolbar.menuState ?: return
    val palette = LocalRabbitPalette.current
    val density = LocalDensity.current
    val view = LocalView.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val effectiveStatusBarTopPx = remember(view, statusBarTopPx) {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        (statusBarTopPx - loc[1]).coerceAtLeast(0)
    }
    val gapPx = with(density) { 10.dp.roundToPx() }
    val horizontalMarginPx = with(density) { 12.dp.roundToPx() }
    val topSafeMarginPx = effectiveStatusBarTopPx + with(density) { 8.dp.roundToPx() }

    val cutLabel = stringResource(R.string.toolbar_cut)
    val copyLabel = stringResource(R.string.toolbar_copy)
    val pasteLabel = stringResource(R.string.toolbar_paste)
    val selectAllLabel = stringResource(R.string.toolbar_select_all)
    val autofillLabel = stringResource(R.string.toolbar_autofill)

    val actions = remember(state, cutLabel, copyLabel, pasteLabel, selectAllLabel, autofillLabel) {
        buildList {
            state.onCut?.let { add(ToolbarActionItem(cutLabel, hideOnInvoke = true, onClick = it)) }
            state.onCopy?.let { add(ToolbarActionItem(copyLabel, hideOnInvoke = true, onClick = it)) }
            state.onPaste?.let { add(ToolbarActionItem(pasteLabel, hideOnInvoke = true, onClick = it)) }
            state.onSelectAll?.let { add(ToolbarActionItem(selectAllLabel, hideOnInvoke = false, onClick = it)) }
            state.onAutofill?.let { add(ToolbarActionItem(autofillLabel, hideOnInvoke = true, onClick = it)) }
        }
    }
    if (actions.isEmpty()) return

    val positionProvider = remember(state.rect, gapPx, horizontalMarginPx, topSafeMarginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val targetCenterX = ((state.rect.left + state.rect.right) / 2f).roundToInt()
                val maxX = (windowSize.width - popupContentSize.width - horizontalMarginPx)
                    .coerceAtLeast(horizontalMarginPx)
                val x = (targetCenterX - popupContentSize.width / 2)
                    .coerceIn(horizontalMarginPx, maxX)

                val aboveY = state.rect.top.roundToInt() - popupContentSize.height - gapPx
                val belowY = state.rect.bottom.roundToInt() + gapPx
                val maxY = (windowSize.height - popupContentSize.height - horizontalMarginPx)
                    .coerceAtLeast(topSafeMarginPx)
                val y = if (aboveY >= topSafeMarginPx) {
                    aboveY.coerceAtMost(maxY)
                } else {
                    belowY.coerceIn(topSafeMarginPx, maxY)
                }
                return IntOffset(x = x, y = y)
            }
        }
    }

    val bgColor = if (palette.isDark) Color(0xFF1F1F1F) else Color(0xFFFFFFFF)
    val borderColor = if (palette.isDark) Color(0xFF343434) else Color(0xFFE5E5E5)
    val textColor = if (palette.isDark) Color(0xFFF5F5F5) else Color(0xFF171717)
    val dividerColor = if (palette.isDark) Color(0xFF333333) else Color(0xFFEAEAEA)

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = bgColor,
            tonalElevation = 0.dp,
            shadowElevation = if (palette.isDark) 8.dp else 6.dp,
            border = BorderStroke(1.dp, borderColor),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                actions.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(dividerColor),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val version = state.version
                                item.onClick()
                                if (item.hideOnInvoke && toolbar.menuState?.version == version) {
                                    toolbar.hide()
                                }
                            }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.label,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
