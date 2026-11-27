package io.rabbitcontrol.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbitcontrol.android.error.RcErrorAction
import com.rabbitcontrol.android.error.RcErrorPresentation
import com.rabbitcontrol.android.error.RcErrorSeverity
import com.rabbitcontrol.android.error.RcUiError
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RcPresentedError(
    val id: Long,
    val error: RcUiError,
    val onRetry: (() -> Unit)? = null,
)

object RcErrorHub {
    private val nextId = AtomicLong(1L)
    private val _banner = MutableStateFlow<RcPresentedError?>(null)
    val banner: StateFlow<RcPresentedError?> = _banner.asStateFlow()

    private val _modal = MutableStateFlow<RcPresentedError?>(null)
    val modal: StateFlow<RcPresentedError?> = _modal.asStateFlow()

    fun present(error: RcUiError, onRetry: (() -> Unit)? = null) {
        when (error.presentation) {
            RcErrorPresentation.TOAST, RcErrorPresentation.INLINE -> {
                RabbitToast.show(
                    text = formatToastText(error),
                    tone = error.severity.toToastTone(),
                )
            }
            RcErrorPresentation.BANNER -> {
                _banner.value = RcPresentedError(nextId.getAndIncrement(), error, onRetry)
            }
            RcErrorPresentation.MODAL -> {
                _modal.value = RcPresentedError(nextId.getAndIncrement(), error, onRetry)
            }
        }
    }

    fun showBanner(error: RcUiError, onRetry: (() -> Unit)? = null) {
        _banner.value = RcPresentedError(nextId.getAndIncrement(), error, onRetry)
    }

    fun showModal(error: RcUiError, onRetry: (() -> Unit)? = null) {
        _modal.value = RcPresentedError(nextId.getAndIncrement(), error, onRetry)
    }

    fun dismissBanner(id: Long? = null) {
        if (id == null) {
            _banner.value = null
        } else {
            _banner.update { cur -> if (cur?.id == id) null else cur }
        }
    }

    fun dismissModal(id: Long? = null) {
        if (id == null) {
            _modal.value = null
        } else {
            _modal.update { cur -> if (cur?.id == id) null else cur }
        }
    }

    private fun formatToastText(error: RcUiError): String {
        val msg = error.message.ifBlank { error.title }.trim()
        val sug = error.suggestion.trim()
        return if (sug.isNotEmpty() && msg.length + sug.length <= 72 && msg != sug) {
            "$msg · $sug"
        } else {
            msg
        }
    }
}

fun RcErrorSeverity.toToastTone(): RabbitToastTone = when (this) {
    RcErrorSeverity.INFO -> RabbitToastTone.Info
    RcErrorSeverity.WARNING -> RabbitToastTone.Warning
    RcErrorSeverity.ERROR, RcErrorSeverity.CRITICAL -> RabbitToastTone.Error
}

@Composable
fun rcErrorActionLabel(action: RcErrorAction): String? = when (action) {
    RcErrorAction.NONE -> null
    RcErrorAction.RETRY -> stringResource(R.string.common_retry)
    RcErrorAction.RELOGIN -> stringResource(R.string.err_action_relogin)
    RcErrorAction.CHANGE_PASSWORD -> stringResource(R.string.err_action_change_password)
    RcErrorAction.OPEN_SETTINGS -> stringResource(R.string.err_action_open_settings)
    RcErrorAction.OPEN_PERMISSIONS -> stringResource(R.string.err_action_open_permissions)
    RcErrorAction.CONTACT_ADMIN -> stringResource(R.string.err_action_contact_admin)
}

private data class RcSeverityColors(
    val bg: Color,
    val border: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val icon: ImageVector,
)

@Composable
private fun resolveSeverityColors(severity: RcErrorSeverity): RcSeverityColors {
    val palette = LocalRabbitPalette.current
    return when (severity) {
        RcErrorSeverity.INFO -> RcSeverityColors(
            bg = palette.primarySoft,
            border = palette.primary.copy(alpha = 0.25f),
            accent = palette.primaryText,
            text = palette.text,
            muted = palette.muted,
            icon = Icons.Rounded.Info,
        )
        RcErrorSeverity.WARNING -> RcSeverityColors(
            bg = palette.warningSoft,
            border = palette.warning.copy(alpha = 0.30f),
            accent = palette.warning,
            text = palette.text,
            muted = palette.muted,
            icon = Icons.Rounded.WarningAmber,
        )
        RcErrorSeverity.ERROR, RcErrorSeverity.CRITICAL -> RcSeverityColors(
            bg = palette.dangerSoft,
            border = palette.danger.copy(alpha = 0.30f),
            accent = palette.danger,
            text = palette.text,
            muted = palette.muted,
            icon = Icons.Rounded.ErrorOutline,
        )
    }
}

@Composable
fun RcInlineError(
    error: RcUiError,
    modifier: Modifier = Modifier,
    onAction: ((RcErrorAction) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = resolveSeverityColors(error.severity)
    var showDetail by remember(error.code, error.detail) { mutableStateOf(false) }
    val actionLabel = rcErrorActionLabel(error.action)
    val hasDetail = !error.detail.isNullOrBlank() || error.status != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RabbitRadius.Md))
            .background(colors.bg)
            .border(1.dp, colors.border, RoundedCornerShape(RabbitRadius.Md))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = colors.icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (error.title.isNotBlank() && error.title != error.message) {
                    Text(
                        text = error.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent,
                    )
                }
                Text(
                    text = error.message.ifBlank { error.title },
                    fontSize = 13.sp,
                    color = colors.text,
                    lineHeight = 18.sp,
                )
                if (error.suggestion.isNotBlank()) {
                    Text(
                        text = error.suggestion,
                        fontSize = 12.sp,
                        color = colors.muted,
                        lineHeight = 16.sp,
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if ((actionLabel != null && onAction != null) || hasDetail) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (hasDetail) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .clickable { showDetail = !showDetail }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (showDetail) R.string.err_hide_detail else R.string.err_show_detail,
                            ),
                            fontSize = 11.sp,
                            color = colors.muted,
                        )
                        Icon(
                            imageVector = if (showDetail) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = colors.muted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = { onAction(error.action) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = actionLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showDetail,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val detailSummary = buildString {
                append(error.code)
                error.status?.let { append(" · HTTP $it") }
                error.detail?.takeIf { it.isNotBlank() }?.let {
                    append("\n")
                    append(it)
                }
            }
            Text(
                text = detailSummary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = colors.muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RabbitRadius.Sm))
                    .background(LocalRabbitPalette.current.surface.copy(alpha = 0.6f))
                    .padding(8.dp),
            )
        }
    }
}

@Composable
fun RcErrorBanner(
    error: RcUiError,
    onAction: (RcErrorAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = resolveSeverityColors(error.severity)
    val palette = LocalRabbitPalette.current
    val actionLabel = rcErrorActionLabel(error.action)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(RabbitRadius.Md),
        color = palette.surface,
        tonalElevation = 0.dp,
        shadowElevation = RabbitElevation.Floating,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.bg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = colors.icon,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = error.title.ifBlank { error.message },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val body = error.suggestion.ifBlank { error.message }
                if (body.isNotBlank() && body != error.title) {
                    Text(
                        text = body,
                        fontSize = 11.sp,
                        color = palette.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (actionLabel != null) {
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.surface)
                        .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                        .clickable { onAction(error.action) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.accent,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = palette.muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun RcErrorModal(
    error: RcUiError,
    onAction: (RcErrorAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val colors = resolveSeverityColors(error.severity)
    val actionLabel = rcErrorActionLabel(error.action)
    var showDetail by remember(error.code, error.detail) { mutableStateOf(false) }
    val hasDetail = !error.detail.isNullOrBlank() || error.status != null || error.code.isNotBlank()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 370.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(RabbitRadius.Xl),
            color = palette.surface,
            tonalElevation = 0.dp,
            shadowElevation = RabbitElevation.Floating,
            border = BorderStroke(1.dp, palette.line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = colors.icon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = error.title.ifBlank { error.message },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error.message.ifBlank { error.title },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = palette.text,
                )

                if (error.suggestion.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = error.suggestion,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = palette.muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RabbitRadius.Md))
                            .background(palette.field)
                            .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                            .padding(12.dp),
                    )
                }

                if (hasDetail) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .clickable { showDetail = !showDetail }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (showDetail) R.string.err_hide_detail else R.string.err_show_detail,
                            ),
                            fontSize = 12.sp,
                            color = palette.muted,
                        )
                        Icon(
                            imageVector = if (showDetail) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = palette.muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (showDetail) {
                        val detailText = buildString {
                            append("code: ${error.code}")
                            error.status?.let { append("\nstatus: $it") }
                            error.detail?.takeIf { it.isNotBlank() }?.let {
                                append("\ndetail: $it")
                            }
                        }
                        Text(
                            text = detailText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = palette.muted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Sm))
                                .padding(10.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (actionLabel != null) {
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.common_close),
                                color = palette.muted,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onAction(error.action) },
                            shape = RoundedCornerShape(RabbitRadius.Pill),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (error.severity == RcErrorSeverity.CRITICAL || error.severity == RcErrorSeverity.ERROR) {
                                    palette.danger
                                } else {
                                    palette.primary
                                },
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text(actionLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(RabbitRadius.Pill),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.primary,
                                contentColor = Color.White,
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(R.string.common_got_it), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RcErrorHost(
    onAction: (RcErrorAction, RcUiError, (() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val banner by RcErrorHub.banner.collectAsStateWithLifecycle()
    val modal by RcErrorHub.modal.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        val current = banner
        if (current != null) {
            RcErrorBanner(
                error = current.error,
                onAction = { act ->
                    RcErrorHub.dismissBanner(current.id)
                    onAction(act, current.error, current.onRetry)
                },
                onDismiss = { RcErrorHub.dismissBanner(current.id) },
            )
        }
    }

    val currentModal = modal
    if (currentModal != null) {
        RcErrorModal(
            error = currentModal.error,
            onAction = { act ->
                RcErrorHub.dismissModal(currentModal.id)
                onAction(act, currentModal.error, currentModal.onRetry)
            },
            onDismiss = { RcErrorHub.dismissModal(currentModal.id) },
        )
    }
}
