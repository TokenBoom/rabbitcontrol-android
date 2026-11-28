package com.rabbitcontrol.android.ui.screens.tools

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import com.rabbitcontrol.android.core.nativebridge.TerminalSessionSummary
import com.rabbitcontrol.android.core.session.SessionStatus
import com.rabbitcontrol.android.ui.components.RabbitCard
import com.rabbitcontrol.android.ui.components.RabbitDialog
import com.rabbitcontrol.android.ui.components.RabbitEmptyState
import com.rabbitcontrol.android.ui.components.RabbitTextField
import com.rabbitcontrol.android.ui.components.RabbitToast
import com.rabbitcontrol.android.ui.components.RabbitTopBar
import com.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import com.rabbitcontrol.android.ui.theme.RabbitRadius
import com.rabbitcontrol.android.ui.theme.RabbitSpacing
import com.rabbitcontrol.android.ui.theme.RabbitWidthClass
import com.rabbitcontrol.android.ui.theme.SessionPalette
import com.rabbitcontrol.android.ui.theme.rememberRabbitLayoutInfo
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
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val savedDeviceIds by viewModel.savedDeviceIds.collectAsStateWithLifecycle()

    var renameTarget by remember { mutableStateOf<TerminalSessionSummary?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val inInteractiveTerm = activeTermId != null
    val hasSavedPw = sessionState.targetId.isNotBlank() && sessionState.targetId in savedDeviceIds
    val lockState = sessionState.terminalLockState
    val showUnlockModal = lockState.required && !lockState.unlocked
    var sidebarVisible by rememberSaveable { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (inInteractiveTerm) SessionPalette.trackpad else palette.canvas)
            .systemBarsPadding()
            .imePadding(),
    ) {
        val widthClass = rememberRabbitLayoutInfo(maxWidth).widthClass
        val isExpanded = widthClass == RabbitWidthClass.Expanded

        Column(modifier = Modifier.fillMaxSize()) {
            RabbitTopBar(
                title = stringResource(if (inInteractiveTerm) R.string.term_title_active else R.string.term_title_idle),
                subtitle = deviceName,
                onBack = {
                    if (inInteractiveTerm && !isExpanded) viewModel.detachBackToList() else onBack()
                },
                contentColor = if (inInteractiveTerm) SessionPalette.text else palette.text,
                mutedColor = if (inInteractiveTerm) SessionPalette.muted else palette.muted,
                actions = {
                    if (isExpanded && (sessions.isNotEmpty() || inInteractiveTerm)) {
                        IconButton(
                            onClick = { sidebarVisible = !sidebarVisible },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = if (sidebarVisible) Icons.Rounded.ViewSidebar else Icons.Rounded.Menu,
                                contentDescription = null,
                                tint = if (inInteractiveTerm) SessionPalette.text else palette.text,
                            )
                        }
                    }
                    if (hasSavedPw) {
                        val clearedMsg = stringResource(R.string.detail_cleared_term_pw)
                        IconButton(
                            onClick = {
                                viewModel.clearSavedPassword()
                                RabbitToast.info(clearedMsg)
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                Icons.Rounded.KeyOff,
                                stringResource(R.string.detail_clear_term_pw),
                                tint = if (inInteractiveTerm) SessionPalette.muted else palette.muted,
                            )
                        }
                    }
                    if (!inInteractiveTerm || isExpanded) {
                        IconButton(onClick = viewModel::refreshSessions, modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Rounded.Refresh,
                                stringResource(R.string.common_refresh),
                                tint = if (inInteractiveTerm) SessionPalette.muted else palette.muted,
                            )
                        }
                        Button(
                            onClick = viewModel::createTerminal,
                            enabled = sessionState.status is SessionStatus.Streaming && !showUnlockModal,
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

            val canCreateTerminal = sessionState.status is SessionStatus.Streaming && !showUnlockModal
            val sessionListPane: @Composable (Modifier, Boolean) -> Unit = { listModifier, darkChrome ->
                LazyColumn(
                    modifier = listModifier,
                    contentPadding = PaddingValues(if (darkChrome) RabbitSpacing.Md else RabbitSpacing.Lg),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (sessions.isEmpty()) {
                        item {
                            if (isExpanded) {
                                Text(
                                    text = stringResource(R.string.term_empty_title),
                                    fontSize = 13.sp,
                                    color = if (darkChrome) SessionPalette.muted else palette.muted,
                                    modifier = Modifier.padding(horizontal = RabbitSpacing.Sm, vertical = RabbitSpacing.Md),
                                )
                            } else {
                                RabbitEmptyState(
                                    icon = Icons.Rounded.Terminal,
                                    title = stringResource(R.string.term_empty_title),
                                    description = stringResource(R.string.term_empty_desc),
                                )
                            }
                        }
                    } else {
                        items(sessions, key = { it.termId }) { sess ->
                            val isSelected = sess.termId == activeTermId
                            RabbitCard(
                                onClick = { viewModel.attachTerminal(sess) },
                                containerColor = when {
                                    darkChrome && isSelected -> SessionPalette.surfaceActive
                                    darkChrome -> SessionPalette.surface
                                    isSelected -> palette.primarySoft
                                    else -> palette.surface
                                },
                                borderColor = when {
                                    isSelected && darkChrome -> SessionPalette.accent
                                    isSelected -> palette.primary
                                    darkChrome -> SessionPalette.border
                                    else -> palette.line
                                },
                                contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (darkChrome) SessionPalette.chromeElevated else palette.primarySoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Rounded.Terminal,
                                            null,
                                            tint = if (darkChrome) SessionPalette.accentText else palette.primaryText,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            sess.name.ifBlank { stringResource(R.string.term_default_name, sess.termId) },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (darkChrome) SessionPalette.text else palette.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${sess.title.ifBlank { "shell" }} · ${sess.cwd.ifBlank { "~" }}",
                                            fontSize = 12.sp,
                                            color = if (darkChrome) SessionPalette.muted else palette.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            renameTarget = sess
                                            renameDraft = sess.name
                                        },
                                        modifier = Modifier.size(38.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Edit,
                                            stringResource(R.string.term_cd_rename),
                                            tint = if (darkChrome) SessionPalette.muted else palette.muted,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.closeTerminal(sess.termId) },
                                        modifier = Modifier.size(38.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            stringResource(R.string.common_close),
                                            tint = if (darkChrome) SessionPalette.dangerText else palette.danger,
                                            modifier = Modifier.size(17.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val interactivePtyPane: @Composable (Modifier) -> Unit = { ptyModifier ->
                Column(modifier = ptyModifier) {
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
                    val termFontSp = prefs.terminalFontSizeSp.toFloat()
                    val termLineHeightSp = (termFontSp * 1.38f)

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(SessionPalette.trackpad)
                            .padding(10.dp),
                    ) {
                        val estCols = (maxWidth.value / (termFontSp * 0.6f)).toInt().coerceIn(36, 180)
                        val estRows = (maxHeight.value / termLineHeightSp).toInt().coerceIn(12, 80)
                        LaunchedEffect(estCols, estRows) {
                            viewModel.updateGridSize(estCols, estRows)
                        }

                        Text(
                            text = renderedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = termFontSp.sp,
                            lineHeight = termLineHeightSp.sp,
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
                            TermKeyChip("A-") { viewModel.adjustFontSize(-1) }
                            TermKeyChip("${prefs.terminalFontSizeSp}") {}
                            TermKeyChip("A+") { viewModel.adjustFontSize(1) }
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

            if (isExpanded) {
                HorizontalDivider(
                    color = if (inInteractiveTerm) SessionPalette.border else palette.line,
                    thickness = 1.dp,
                )
                if (!inInteractiveTerm && sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(RabbitSpacing.Xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        RabbitEmptyState(
                            icon = Icons.Rounded.Terminal,
                            title = stringResource(R.string.term_empty_title),
                            description = stringResource(R.string.term_empty_desc),
                            actionLabel = if (canCreateTerminal) stringResource(R.string.term_new) else null,
                            onAction = if (canCreateTerminal) viewModel::createTerminal else null,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        if (sidebarVisible) {
                            sessionListPane(
                                Modifier
                                    .width(280.dp)
                                    .fillMaxHeight()
                                    .background(if (inInteractiveTerm) SessionPalette.chrome else palette.surface),
                                inInteractiveTerm,
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(if (inInteractiveTerm) SessionPalette.border else palette.line),
                            )
                        }
                        if (inInteractiveTerm) {
                            interactivePtyPane(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(RabbitSpacing.Xxl),
                                contentAlignment = Alignment.Center,
                            ) {
                                RabbitEmptyState(
                                    icon = Icons.Rounded.Terminal,
                                    title = stringResource(R.string.term_title_idle),
                                    description = stringResource(R.string.cp_tool_term_sub),
                                    actionLabel = if (canCreateTerminal) stringResource(R.string.term_new) else null,
                                    onAction = if (canCreateTerminal) viewModel::createTerminal else null,
                                )
                            }
                        }
                    }
                }
            } else {
                if (!inInteractiveTerm) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        sessionListPane(
                            Modifier
                                .then(
                                    if (widthClass == RabbitWidthClass.Medium) Modifier.widthIn(max = 720.dp)
                                    else Modifier,
                                )
                                .fillMaxSize(),
                            false,
                        )
                    }
                } else {
                    interactivePtyPane(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
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

    if (showUnlockModal) {
        var unlockUser by remember(lockState.username) { mutableStateOf(lockState.username.orEmpty()) }
        var unlockPw by remember { mutableStateOf("") }
        var rememberPw by remember { mutableStateOf(false) }
        val unlockResult = sessionState.lastTerminalUnlockResult
        val retryLeft = maxOf(lockState.retryAfterSecs, unlockResult?.retryAfterSecs ?: 0)
        RabbitDialog(
            title = stringResource(R.string.term_unlock_title),
            message = stringResource(R.string.term_unlock_sub),
            onDismissRequest = onBack,
            dismissLabel = stringResource(R.string.common_back),
            confirmLabel = stringResource(R.string.term_unlock_btn),
            confirmEnabled = unlockPw.isNotEmpty() && retryLeft == 0,
            onConfirm = {
                viewModel.submitUnlock(unlockPw, unlockUser.ifBlank { null }, rememberPw)
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RabbitTextField(
                    value = unlockUser,
                    onValueChange = { unlockUser = it },
                    label = stringResource(R.string.term_unlock_user_label),
                    placeholder = stringResource(R.string.term_unlock_user_hint),
                )
                RabbitTextField(
                    value = unlockPw,
                    onValueChange = { unlockPw = it },
                    label = stringResource(R.string.term_unlock_pw_label),
                    placeholder = stringResource(R.string.term_unlock_pw_hint),
                    isPassword = true,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberPw = !rememberPw },
                ) {
                    Checkbox(
                        checked = rememberPw,
                        onCheckedChange = { rememberPw = it },
                        colors = CheckboxDefaults.colors(checkedColor = palette.primary),
                    )
                    Text(
                        text = stringResource(R.string.term_unlock_remember),
                        fontSize = 13.sp,
                        color = palette.text,
                    )
                }
                if (unlockResult != null && !unlockResult.ok && !unlockResult.error.isNullOrBlank()) {
                    Text(
                        text = NativeErrorLocalizer.localize(unlockResult.error),
                        fontSize = 12.sp,
                        color = palette.danger,
                    )
                }
                if (retryLeft > 0) {
                    Text(
                        text = stringResource(R.string.term_unlock_cooldown, retryLeft),
                        fontSize = 12.sp,
                        color = palette.warning,
                    )
                }
            }
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
