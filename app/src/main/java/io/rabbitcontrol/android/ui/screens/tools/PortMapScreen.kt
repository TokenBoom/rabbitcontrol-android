package io.rabbitcontrol.android.ui.screens.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun PortMapScreen(
    deviceName: String,
    onBack: () -> Unit,
    viewModel: PortMapViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = palette.canvas,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = palette.primary,
                contentColor = Color.White,
            ) {
                Icon(Icons.Rounded.Add, "新增映射")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .systemBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = palette.text)
                }
                Text("端口映射 · $deviceName", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (rules.isEmpty()) {
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
                            Icon(Icons.Rounded.Cable, null, tint = palette.primary, modifier = Modifier.size(34.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("暂无端口映射规则", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = palette.text)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("把远端电脑的服务端口安全转发到手机本地 127.0.0.1，后台保活不断线。", fontSize = 13.sp, color = palette.muted)
                        }
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.line, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    "127.0.0.1:${rule.localPort} → ${rule.targetAddr}:${rule.targetPort}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = palette.primary,
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { on -> viewModel.toggleRule(rule.id, on) },
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "状态: ${rule.status} · 连接 ${rule.activeConns} · ↑${rule.bytesTx}B ↓${rule.bytesRx}B",
                                fontSize = 12.sp,
                                color = if (!rule.error.isNullOrBlank()) palette.danger else palette.muted,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString("127.0.0.1:${rule.localPort}"))
                                    scope.launch { snackbarHostState.showSnackbar("已复制 127.0.0.1:${rule.localPort}") }
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, "复制", tint = palette.muted, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${rule.localPort}")),
                                        )
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "打开", tint = palette.primary, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { viewModel.deleteRule(rule.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, "删除", tint = palette.danger, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var localPort by remember { mutableStateOf("") }
        var targetAddr by remember { mutableStateOf("127.0.0.1") }
        var targetPort by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新建端口映射") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("规则名称") }, singleLine = true)
                    OutlinedTextField(
                        value = localPort,
                        onValueChange = { localPort = it },
                        label = { Text("本机监听端口 (1-65535)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    OutlinedTextField(value = targetAddr, onValueChange = { targetAddr = it }, label = { Text("远端目标地址") }, singleLine = true)
                    OutlinedTextField(
                        value = targetPort,
                        onValueChange = { targetPort = it },
                        label = { Text("远端目标端口 (1-65535)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val lp = localPort.toIntOrNull() ?: 0
                    val tp = targetPort.toIntOrNull() ?: 0
                    if (lp in 1..65535 && tp in 1..65535) {
                        viewModel.addRule(name, lp, targetAddr, tp)
                        showAddDialog = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            },
        )
    }

    if (!error.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("端口映射提示") },
            text = { Text(error.orEmpty()) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("确定") } },
        )
    }
}
