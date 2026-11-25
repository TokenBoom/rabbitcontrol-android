package io.rabbitcontrol.android.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.nativebridge.TerminalSessionSummary
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (activeTermId != null) Color(0xFF0D1117) else palette.canvas)
            .systemBarsPadding()
            .imePadding(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (activeTermId != null) viewModel.detachBackToList() else onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    "返回",
                    tint = if (activeTermId != null) Color.White else palette.text,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (activeTermId != null) "终端 · $deviceName" else "终端远控 · $deviceName",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (activeTermId != null) Color.White else palette.text,
                )
            }
            if (activeTermId == null) {
                IconButton(onClick = viewModel::refreshSessions) {
                    Icon(Icons.Rounded.Refresh, "刷新", tint = palette.muted)
                }
                Button(
                    onClick = viewModel::createTerminal,
                    enabled = sessionState.status is SessionStatus.Streaming,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新建终端", fontSize = 13.sp)
                }
            } else {
                IconButton(onClick = viewModel::createTerminal) {
                    Icon(Icons.Rounded.Add, "新建标签", tint = Color.White)
                }
            }
        }

        when (val st = sessionState.status) {
            is SessionStatus.Connecting -> {
                Text(
                    text = st.label,
                    fontSize = 12.sp,
                    color = palette.primary,
                    modifier = Modifier.fillMaxWidth().background(palette.primarySoft).padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            is SessionStatus.Error -> {
                Text(
                    text = "会话异常: ${st.message}",
                    fontSize = 12.sp,
                    color = palette.danger,
                    modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            is SessionStatus.Ended -> {
                Text(
                    text = "会话已断开: ${st.reason}",
                    fontSize = 12.sp,
                    color = palette.danger,
                    modifier = Modifier.fillMaxWidth().background(palette.surface).padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            else -> Unit
        }

        if (activeTermId == null) {
            // View 1: Session List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (sessions.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.line, RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(Icons.Rounded.Terminal, null, tint = palette.primary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("暂无运行中的终端会话", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = palette.text)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("点击右上角「新建终端」在远端开启 PTY Shell", fontSize = 13.sp, color = palette.muted)
                        }
                    }
                } else {
                    items(sessions, key = { it.termId }) { sess ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.line, RoundedCornerShape(16.dp))
                                .clickable { viewModel.attachTerminal(sess) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.primarySoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Terminal, null, tint = palette.primary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sess.name.ifBlank { "终端 #${sess.termId}" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                Text("${sess.title.ifBlank { "shell" }} · ${sess.cwd.ifBlank { "~" }}", fontSize = 12.sp, color = palette.muted, maxLines = 1)
                            }
                            IconButton(onClick = {
                                renameTarget = sess
                                renameDraft = sess.name
                            }) {
                                Icon(Icons.Rounded.Edit, "重命名", tint = palette.muted, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.closeTerminal(sess.termId) }) {
                                Icon(Icons.Rounded.DeleteOutline, "关闭", tint = palette.danger, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // View 2: Interactive Multi-Tab PTY Terminal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                openTabs.forEach { tab ->
                    val active = tab.termId == activeTermId
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color(0xFF283448) else Color(0xFF1D232E))
                            .clickable { viewModel.selectTab(tab.termId) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tab.name, color = Color.White, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Close,
                            "关闭",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { viewModel.closeTerminal(tab.termId) },
                        )
                    }
                }
            }

            // Terminal Viewport
            val vScroll = rememberScrollState()
            val hScroll = rememberScrollState()
            LaunchedEffect(renderedText) {
                vScroll.animateScrollTo(vScroll.maxValue)
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0D1117))
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
                    color = Color(0xFFE6EDF7),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll),
                )
            }

            // Direct Command Input + Geek Key Strip
            var inputLine by remember { mutableStateOf("") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    TermKeyChip("清屏") { viewModel.clearActiveBuffer() }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0D1117))
                        .border(1.dp, Color(0xFF2B3444), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                        textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color(0xFF3370FF)),
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
                                Text("输入命令或文本后发送…", color = Color(0xFF64748B), fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                    IconButton(
                        onClick = {
                            viewModel.sendTextOrCommand(inputLine + "\r")
                            inputLine = ""
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, "发送", tint = Color(0xFF3370FF), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        val target = renameTarget!!
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名终端") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameTerminal(target.termId, renameDraft)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    if (!error.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("终端提示") },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } },
        )
    }
}

@Composable
private fun TermKeyChip(label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Color(0xFF3370FF) else Color(0xFF222936))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
