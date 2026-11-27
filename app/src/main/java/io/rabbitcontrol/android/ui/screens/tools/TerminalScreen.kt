package io.rabbitcontrol.android.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import io.rabbitcontrol.android.core.nativebridge.TerminalSessionSummary
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitEmptyState
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import io.rabbitcontrol.android.ui.theme.SessionPalette
import org.koin.androidx.compose.koinViewModel

@Composable
fun TerminalScreen(
    deviceName: String,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTermId by viewModel.activeTermId.collectAsStateWithLifecycle()
    val renderedText by viewModel.renderedText.collectAsStateWithLifecycle()
    val ctrlSticky by viewModel.ctrlSticky.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<TerminalSessionSummary?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val inInteractiveTerm = activeTermId != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (inInteractiveTerm) SessionPalette.trackpad else palette.canvas)
            .systemBarsPadding()
            .imePadding(),
    ) {
        RabbitTopBar(
            title = stringResource(if (inInteractiveTerm) R.string.term_title_active else R.string.term_title_idle),
            subtitle = deviceName,
            onBack = {
                if (inInteractiveTerm) viewModel.detachBackToList() else onBack()
            },
            contentColor = if (inInteractiveTerm) SessionPalette.text else palette.text,
            mutedColor = if (inInteractiveTerm) SessionPalette.muted else palette.muted,
            actions = {
                if (!inInteractiveTerm) {
                    IconButton(onClick = viewModel::refreshSessions, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.common_refresh), tint = palette.muted)
                    }
                    Button(
                        onClick = viewModel::createTerminal,
                        enabled = sessionState.status is SessionStatus.Streaming,
                        shape = RoundedCornerShape(RabbitRadius.Md),
                        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.primary,
                            contentColor = Color.White,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(40.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.term_new), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    IconButton(onClick = viewModel::createTerminal, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.term_cd_new_tab), tint = SessionPalette.text)
                    }
                }
            },
        )

        when (val st = sessionState.status) {
            is SessionStatus.Connecting -> {
                Text(
                    text = st.label,
                    fontSize = 12.sp,
                    color = palette.primaryText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.primarySoft)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            is SessionStatus.Reconnecting -> {
                Text(
                    text = st.reason.ifBlank { stringResource(R.string.session_reconnecting_generic) },
                    fontSize = 12.sp,
                    color = palette.warning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.warningSoft)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            is SessionStatus.Error -> {
                val errText = st.uiError?.let { it.message.ifBlank { it.title } }
                    ?: NativeErrorLocalizer.localize(st.message)
                Text(
                    text = stringResource(R.string.ft_session_error, errText),
                    fontSize = 12.sp,
                    color = palette.danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.dangerSoft)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            is SessionStatus.Ended -> {
                val reasonText = st.uiError?.let { it.message.ifBlank { it.title } }
                    ?: NativeErrorLocalizer.localize(st.reason)
                Text(
                    text = stringResource(R.string.ft_session_disconnected, reasonText),
                    fontSize = 12.sp,
                    color = palette.danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.dangerSoft)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            else -> Unit
        }

        if (!sessionState.toolPermissions.allowTerminal) {
            Text(
                text = stringResource(R.string.tool_permission_disabled_term),
                fontSize = 12.sp,
                color = palette.warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.warningSoft)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        if (!inInteractiveTerm) {
            // Session List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(RabbitSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (sessions.isEmpty()) {
                    item {
                        RabbitEmptyState(
                            icon = Icons.Rounded.Terminal,
                            title = stringResource(R.string.term_empty_title),
                            description = stringResource(R.string.term_empty_desc),
                        )
                    }
                } else {
                    items(sessions, key = { it.termId }) { sess ->
                        RabbitCard(
                            onClick = { viewModel.attachTerminal(sess) },
                            contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.primarySoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Terminal, null, tint = palette.primaryText, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        sess.name.ifBlank { stringResource(R.string.term_default_name, sess.termId) },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${sess.title.ifBlank { "shell" }} · ${sess.cwd.ifBlank { "~" }}",
                                        fontSize = 12.sp,
                                        color = palette.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        renameTarget = sess
                                        renameDraft = sess.name
                                    },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(Icons.Rounded.Edit, stringResource(R.string.term_cd_rename), tint = palette.muted, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.closeTerminal(sess.termId) },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.common_close), tint = palette.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Interactive Multi-Tab PTY Terminal (SessionPalette)
            HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SessionPalette.chrome)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                openTabs.forEach { tab ->
                    val active = tab.termId == activeTermId
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 34.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .background(if (active) SessionPalette.surfaceActive else SessionPalette.surface)
                            .border(
                                1.dp,
                                if (active) SessionPalette.accent else SessionPalette.border,
                                RoundedCornerShape(RabbitRadius.Sm),
                            )
                            .clickable { viewModel.selectTab(tab.termId) }
                            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tab.name, color = SessionPalette.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.closeTerminal(tab.termId) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                stringResource(R.string.common_close),
                                tint = SessionPalette.muted,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()
            LaunchedEffect(renderedText) {
                vScroll.scrollTo(vScroll.maxValue)
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(SessionPalette.trackpad)
                    .padding(10.dp),
            ) {
                val estCols = (maxWidth.value / 7.4f).toInt().coerceIn(36, 180)
                val estRows = (maxHeight.value / 17.5f).toInt().coerceIn(12, 80)
                LaunchedEffect(estCols, estRows) {
                    viewModel.updateGridSize(estCols, estRows)
                }

                Text(
                    text = renderedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = SessionPalette.text,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll),
                )
            }

            var inputLine by remember { mutableStateOf("") }
            HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SessionPalette.chrome)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TermKeyChip("Esc") { viewModel.sendTextOrCommand("\u001B") }
                    TermKeyChip("Tab") { viewModel.sendTextOrCommand("\t") }
                    TermKeyChip("Ctrl", active = ctrlSticky) { viewModel.toggleCtrlSticky() }
                    TermKeyChip("Ctrl+C") { activeTermId?.let { tid -> viewModel.sendBytes(tid, byteArrayOf(3)) } }
                    TermKeyChip("Ctrl+D") { activeTermId?.let { tid -> viewModel.sendBytes(tid, byteArrayOf(4)) } }
                    TermKeyChip("↑") { viewModel.sendTextOrCommand("\u001B[A") }
                    TermKeyChip("↓") { viewModel.sendTextOrCommand("\u001B[B") }
                    TermKeyChip("←") { viewModel.sendTextOrCommand("\u001B[D") }
                    TermKeyChip("→") { viewModel.sendTextOrCommand("\u001B[C") }
                    TermKeyChip("|") { viewModel.sendTextOrCommand("|") }
                    TermKeyChip("~") { viewModel.sendTextOrCommand("~") }
                    TermKeyChip("/") { viewModel.sendTextOrCommand("/") }
                    TermKeyChip(stringResource(R.string.term_clear_screen)) { viewModel.clearActiveBuffer() }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .clip(RoundedCornerShape(RabbitRadius.Md))
                        .background(SessionPalette.trackpad)
                        .border(1.dp, SessionPalette.border, RoundedCornerShape(RabbitRadius.Md))
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = inputLine,
                        onValueChange = { next ->
                            if (next.endsWith("\n")) {
                                val cmd = next.dropLast(1)
                                viewModel.sendTextOrCommand(cmd + "\r")
                                inputLine = ""
                            } else {
                                inputLine = next
                            }
                        },
                        textStyle = TextStyle(color = SessionPalette.text, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        cursorBrush = SolidColor(SessionPalette.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                viewModel.sendTextOrCommand(inputLine + "\r")
                                inputLine = ""
                            },
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (inputLine.isEmpty()) {
                                Text(stringResource(R.string.term_input_hint), color = SessionPalette.muted, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                    IconButton(
                        onClick = {
                            viewModel.sendTextOrCommand(inputLine + "\r")
                            inputLine = ""
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.common_send), tint = SessionPalette.accentText, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        val target = renameTarget!!
        RabbitDialog(
            title = stringResource(R.string.term_rename_title),
            onDismissRequest = { renameTarget = null },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.common_save),
            confirmEnabled = renameDraft.isNotBlank(),
            onConfirm = {
                viewModel.renameTerminal(target.termId, renameDraft)
                renameTarget = null
            },
        ) {
            RabbitTextField(
                value = renameDraft,
                onValueChange = { renameDraft = it },
                placeholder = stringResource(R.string.term_rename_hint),
            )
        }
    }

    LaunchedEffect(error) {
        val err = error
        if (!err.isNullOrBlank()) {
            RabbitToast.error(err)
            viewModel.clearError()
        }
    }
}

@Composable
private fun TermKeyChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(RabbitRadius.Sm)
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 38.dp, minHeight = 36.dp)
            .clip(shape)
            .background(if (active) SessionPalette.accent else SessionPalette.surface)
            .border(1.dp, if (active) SessionPalette.accent else SessionPalette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Color.White else SessionPalette.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}
