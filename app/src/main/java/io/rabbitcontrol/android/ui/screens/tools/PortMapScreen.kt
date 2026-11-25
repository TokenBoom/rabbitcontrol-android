package io.rabbitcontrol.android.ui.screens.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitEmptyState
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
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

    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        val err = error
        if (!err.isNullOrBlank()) {
            RabbitToast.error(err)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = palette.canvas,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = palette.primary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = RabbitElevation.Floating,
                    pressedElevation = 3.dp,
                    focusedElevation = RabbitElevation.Floating,
                    hoveredElevation = RabbitElevation.Floating,
                ),
            ) {
                Icon(Icons.Rounded.Add, "新增映射")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RabbitTopBar(
                title = "端口映射",
                subtitle = deviceName,
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = RabbitSpacing.Lg,
                    end = RabbitSpacing.Lg,
                    top = RabbitSpacing.Sm,
                    bottom = 84.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (rules.isEmpty()) {
                    item {
                        RabbitEmptyState(
                            icon = Icons.Rounded.Cable,
                            title = "暂无端口映射规则",
                            description = "把远端电脑的服务端口安全转发到手机本地 127.0.0.1，后台保活不断线。",
                        )
                    }
                }
                items(rules, key = { it.id }) { rule ->
                    RabbitCard(
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(rule.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                    val tone = when {
                                        !rule.error.isNullOrBlank() -> StatusTone.Danger
                                        rule.enabled -> StatusTone.Success
                                        else -> StatusTone.Muted
                                    }
                                    RabbitStatusPill(label = rule.status, tone = tone)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "127.0.0.1:${rule.localPort} → ${rule.targetAddr}:${rule.targetPort}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = palette.primaryText,
                                )
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { on -> viewModel.toggleRule(rule.id, on) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = palette.primary,
                                    uncheckedThumbColor = palette.muted,
                                    uncheckedTrackColor = palette.field,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "连接 ${rule.activeConns} · ↑${rule.bytesTx}B ↓${rule.bytesRx}B",
                                fontSize = 12.sp,
                                color = if (!rule.error.isNullOrBlank()) palette.danger else palette.muted,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString("127.0.0.1:${rule.localPort}"))
                                    RabbitToast.success("已复制 127.0.0.1:${rule.localPort}")
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Rounded.ContentCopy, "复制", tint = palette.muted, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:${rule.localPort}")),
                                        )
                                    }
                                },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, "打开", tint = palette.primaryText, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { viewModel.deleteRule(rule.id) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, "删除", tint = palette.danger, modifier = Modifier.size(18.dp))
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
        val lp = localPort.toIntOrNull() ?: 0
        val tp = targetPort.toIntOrNull() ?: 0
        RabbitDialog(
            title = "新建端口映射",
            onDismissRequest = { showAddDialog = false },
            dismissLabel = "取消",
            confirmLabel = "添加",
            confirmEnabled = lp in 1..65535 && tp in 1..65535,
            onConfirm = {
                viewModel.addRule(name, lp, targetAddr, tp)
                showAddDialog = false
            },
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RabbitTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "规则名称",
                    placeholder = "例如 本地 Web 服务",
                )
                RabbitTextField(
                    value = localPort,
                    onValueChange = { localPort = it },
                    label = "本机监听端口 (1-65535)",
                    placeholder = "例如 8080",
                    keyboardType = KeyboardType.Number,
                )
                RabbitTextField(
                    value = targetAddr,
                    onValueChange = { targetAddr = it },
                    label = "远端目标地址",
                    placeholder = "127.0.0.1",
                )
                RabbitTextField(
                    value = targetPort,
                    onValueChange = { targetPort = it },
                    label = "远端目标端口 (1-65535)",
                    placeholder = "例如 80",
                    keyboardType = KeyboardType.Number,
                )
            }
        }
    }
}
