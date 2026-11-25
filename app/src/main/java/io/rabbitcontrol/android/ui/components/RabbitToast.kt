package io.rabbitcontrol.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RabbitToastTone {
    Error,
    Success,
    Warning,
    Info,
}

data class RabbitToastMessage(
    val id: Long,
    val text: String,
    val tone: RabbitToastTone,
    val durationMs: Long,
)

/**
 * 全局轻提示调度器，统一替代内联报错与默认 Snackbar。
 */
object RabbitToast {
    private val nextId = AtomicLong(1L)
    private val _current = MutableStateFlow<RabbitToastMessage?>(null)
    val current: StateFlow<RabbitToastMessage?> = _current.asStateFlow()

    private val _modalOverlayCount = MutableStateFlow(0)
    val modalOverlayCount: StateFlow<Int> = _modalOverlayCount.asStateFlow()

    private fun defaultDuration(tone: RabbitToastTone): Long = when (tone) {
        RabbitToastTone.Error -> 3200L
        RabbitToastTone.Warning -> 3000L
        RabbitToastTone.Success, RabbitToastTone.Info -> 2400L
    }

    fun show(
        text: String,
        tone: RabbitToastTone = RabbitToastTone.Info,
        durationMs: Long = defaultDuration(tone),
    ) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        _current.value = RabbitToastMessage(
            id = nextId.getAndIncrement(),
            text = cleaned,
            tone = tone,
            durationMs = durationMs,
        )
    }

    fun error(text: String, durationMs: Long = 3200L) =
        show(text = text, tone = RabbitToastTone.Error, durationMs = durationMs)

    fun success(text: String, durationMs: Long = 2400L) =
        show(text = text, tone = RabbitToastTone.Success, durationMs = durationMs)

    fun warning(text: String, durationMs: Long = 3000L) =
        show(text = text, tone = RabbitToastTone.Warning, durationMs = durationMs)

    fun info(text: String, durationMs: Long = 2400L) =
        show(text = text, tone = RabbitToastTone.Info, durationMs = durationMs)

    fun dismiss(id: Long? = null) {
        if (id == null) {
            _current.value = null
        } else {
            _current.update { cur -> if (cur?.id == id) null else cur }
        }
    }

    internal fun acquireModalOverlay() {
        _modalOverlayCount.update { it + 1 }
    }

    internal fun releaseModalOverlay() {
        _modalOverlayCount.update { (it - 1).coerceAtLeast(0) }
    }
}

/**
 * 根容器轻提示宿主，负责统一自动消失计时并在无弹窗遮挡时渲染顶部悬浮提示。
 */
@Composable
fun RabbitToastHost(
    modifier: Modifier = Modifier,
) {
    val current by RabbitToast.current.collectAsStateWithLifecycle()
    val modalCount by RabbitToast.modalOverlayCount.collectAsStateWithLifecycle()

    LaunchedEffect(current?.id) {
        val msg = current ?: return@LaunchedEffect
        delay(msg.durationMs)
        RabbitToast.dismiss(msg.id)
    }

    RabbitToastBanner(
        message = if (modalCount == 0) current else null,
        modifier = modifier,
    )
}

private val TopCenterWindowPositionProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0)
        return IntOffset(x = x, y = 0)
    }
}

/**
 * 在 ModalBottomSheet 等独立 Window 内挂载，使轻提示可悬浮显示在弹窗之上。
 */
@Composable
fun RabbitToastModalOverlay() {
    val current by RabbitToast.current.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val statusBarTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    var showPopup by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        RabbitToast.acquireModalOverlay()
        onDispose { RabbitToast.releaseModalOverlay() }
    }

    LaunchedEffect(current?.id) {
        if (current != null) {
            showPopup = true
        } else if (showPopup) {
            delay(220L)
            showPopup = false
        }
    }

    if (showPopup) {
        Popup(
            popupPositionProvider = TopCenterWindowPositionProvider,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false,
            ),
        ) {
            RabbitToastBanner(
                message = current,
                topPadding = statusBarTop + 12.dp,
            )
        }
    }
}

private data class ToastStyle(
    val bgColor: Color,
    val borderColor: Color,
    val textColor: Color,
    val badgeBg: Color,
    val iconTint: Color,
    val icon: ImageVector,
)

@Composable
private fun RabbitToastBanner(
    message: RabbitToastMessage?,
    modifier: Modifier = Modifier,
    topPadding: Dp? = null,
) {
    val palette = LocalRabbitPalette.current
    var lastMessage by remember { mutableStateOf<RabbitToastMessage?>(null) }
    if (message != null) {
        lastMessage = message
    }
    val display = message ?: lastMessage

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { -it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { -it / 2 },
        modifier = modifier,
    ) {
        val msg = display ?: return@AnimatedVisibility
        val style = resolveToastStyle(tone = msg.tone, isDark = palette.isDark)
        val topModifier = if (topPadding != null) {
            Modifier.padding(top = topPadding, start = 16.dp, end = 16.dp)
        } else {
            Modifier
                .statusBarsPadding()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(topModifier),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                onClick = { RabbitToast.dismiss(msg.id) },
                modifier = Modifier.widthIn(min = 148.dp, max = 420.dp),
                shape = RoundedCornerShape(14.dp),
                color = style.bgColor,
                tonalElevation = 0.dp,
                shadowElevation = if (palette.isDark) 8.dp else 6.dp,
                border = BorderStroke(1.dp, style.borderColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(style.badgeBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.iconTint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = msg.text,
                        color = style.textColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun resolveToastStyle(
    tone: RabbitToastTone,
    isDark: Boolean,
): ToastStyle {
    return if (!isDark) {
        when (tone) {
            RabbitToastTone.Error -> ToastStyle(
                bgColor = Color(0xFFFFFFFF),
                borderColor = Color(0xFFF1C9CD),
                textColor = Color(0xFF171717),
                badgeBg = Color(0xFFFCECEE),
                iconTint = Color(0xFFB92530),
                icon = Icons.Rounded.ErrorOutline,
            )
            RabbitToastTone.Success -> ToastStyle(
                bgColor = Color(0xFFFFFFFF),
                borderColor = Color(0xFFC5E8D5),
                textColor = Color(0xFF171717),
                badgeBg = Color(0xFFE7F6EE),
                iconTint = Color(0xFF156B41),
                icon = Icons.Rounded.CheckCircleOutline,
            )
            RabbitToastTone.Warning -> ToastStyle(
                bgColor = Color(0xFFFFFFFF),
                borderColor = Color(0xFFF3D9A4),
                textColor = Color(0xFF171717),
                badgeBg = Color(0xFFFEF3C7),
                iconTint = Color(0xFFB45309),
                icon = Icons.Rounded.WarningAmber,
            )
            RabbitToastTone.Info -> ToastStyle(
                bgColor = Color(0xFFFFFFFF),
                borderColor = Color(0xFFE5E5E5),
                textColor = Color(0xFF171717),
                badgeBg = Color(0xFFEEF3FF),
                iconTint = Color(0xFF285EDB),
                icon = Icons.Rounded.Info,
            )
        }
    } else {
        when (tone) {
            RabbitToastTone.Error -> ToastStyle(
                bgColor = Color(0xFF1F1F1F),
                borderColor = Color(0xFF5A2A32),
                textColor = Color(0xFFF5F5F5),
                badgeBg = Color(0xFF3A1A20),
                iconTint = Color(0xFFFF8E96),
                icon = Icons.Rounded.ErrorOutline,
            )
            RabbitToastTone.Success -> ToastStyle(
                bgColor = Color(0xFF1F1F1F),
                borderColor = Color(0xFF214E3B),
                textColor = Color(0xFFF5F5F5),
                badgeBg = Color(0xFF133126),
                iconTint = Color(0xFF6EE7B7),
                icon = Icons.Rounded.CheckCircleOutline,
            )
            RabbitToastTone.Warning -> ToastStyle(
                bgColor = Color(0xFF1F1F1F),
                borderColor = Color(0xFF523C19),
                textColor = Color(0xFFF5F5F5),
                badgeBg = Color(0xFF332510),
                iconTint = Color(0xFFFBBF24),
                icon = Icons.Rounded.WarningAmber,
            )
            RabbitToastTone.Info -> ToastStyle(
                bgColor = Color(0xFF1F1F1F),
                borderColor = Color(0xFF343434),
                textColor = Color(0xFFF5F5F5),
                badgeBg = Color(0xFF2A2A2A),
                iconTint = Color(0xFF6B9BFF),
                icon = Icons.Rounded.Info,
            )
        }
    }
}
