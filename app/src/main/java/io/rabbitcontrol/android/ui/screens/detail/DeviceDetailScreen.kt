package io.rabbitcontrol.android.ui.screens.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun DeviceDetailScreen(
    device: RemoteDevice,
    onBack: () -> Unit,
    onEnterDesktop: (watchOnly: Boolean) -> Unit,
    onOpenTool: (DeviceToolKind) -> Unit,
    viewModel: DeviceDetailViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val toolOrder by viewModel.toolOrder.collectAsStateWithLifecycle()
    val wallpaperVersion by viewModel.wallpaperVersion.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val connectError by viewModel.connectError.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTargets.collectAsStateWithLifecycle()

    val wallpaperBitmap = remember(device.id, wallpaperVersion) {
        viewModel.wallpaperCache.loadBitmap(device.id)
    }
    val isCurrentConnected = activeSession.targetId == device.id && activeSession.negotiated != null
    val isFav = device.id in favorites

    var showMoreTools by remember { mutableStateOf(false) }
    var powerConfirmKind by remember { mutableStateOf<String?>(null) }

    fun triggerTool(kind: DeviceToolKind) {
        when (kind) {
            DeviceToolKind.WatchMode -> {
                viewModel.startSessionAndNavigate(device.id, device.name, device.os, watchOnly = true) {
                    onEnterDesktop(true)
                }
            }
            DeviceToolKind.FileTransfer, DeviceToolKind.Terminal, DeviceToolKind.PortMap -> {
                viewModel.startSessionAndNavigate(device.id, device.name, device.os, watchOnly = false) {
                    onOpenTool(kind)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = palette.text)
                }
                Text(
                    device.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.toggleFavorite(device.id) }) {
                    Icon(
                        imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        contentDescription = "收藏",
                        tint = if (isFav) palette.primary else palette.muted,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Hero Card matching screenshot 1
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(20.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF282E3A), Color(0xFF171A21)),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (wallpaperBitmap != null) {
                            Image(
                                bitmap = wallpaperBitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x9912151C)),
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x3DFFFFFF))
                                    .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.LaptopMac, null, tint = Color(0xFFE5EAF2), modifier = Modifier.size(30.dp))
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                device.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isCurrentConnected) "当前会话已连接" else viewModel.lastConnectedLabel(device.id),
                                fontSize = 13.sp,
                                color = Color(0xFFAAB4C4),
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    viewModel.startSessionAndNavigate(device.id, device.name, device.os, watchOnly = false) {
                                        onEnterDesktop(false)
                                    }
                                },
                                enabled = !connecting,
                                shape = RoundedCornerShape(26.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.primary,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.height(46.dp),
                            ) {
                                if (connecting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("正在连接…", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                } else {
                                    Text(if (isCurrentConnected) "回到桌面" else "进入桌面", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Bottom White Action Strip matching screenshot 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(palette.surface)
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        toolOrder.forEachIndexed { index, tool ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(22.dp)
                                        .background(palette.line.copy(alpha = 0.7f)),
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !connecting) { triggerTool(tool) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(toolIcon(tool), null, tint = palette.text, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tool.label, fontSize = 12.sp, color = palette.text, maxLines = 1)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(22.dp)
                                .background(palette.line.copy(alpha = 0.7f)),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showMoreTools = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = "更多工具", tint = palette.text, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Device Metadata Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetaRow(
                        icon = Icons.Rounded.Computer,
                        label = "设备状态",
                        value = when {
                            device.disabled -> "已停用"
                            device.busy -> "会话中"
                            device.online -> "在线"
                            else -> "离线"
                        },
                        valueColor = if (device.online || device.busy) palette.success else palette.muted,
                    )
                    HorizontalDivider(color = palette.line, thickness = 0.6.dp)
                    MetaRow(
                        icon = Icons.Rounded.LaptopMac,
                        label = "系统信息",
                        value = device.os.ifBlank { "未知系统" },
                    )
                    if (device.id.isNotBlank()) {
                        HorizontalDivider(color = palette.line, thickness = 0.6.dp)
                        val formatted = if (device.id.contains(':')) device.id else device.id.chunked(3).joinToString(" ")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboard.setText(AnnotatedString(device.id))
                                    scope.launch { snackbarHostState.showSnackbar("已复制设备 ID") }
                                },
                        ) {
                            Icon(Icons.Rounded.Key, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("设备 ID", fontSize = 14.sp, color = palette.muted, modifier = Modifier.weight(1f))
                            Text(formatted, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = palette.text)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.ContentCopy, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }

    if (!connectError.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("无法建立连接") },
            text = { Text(connectError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("确定") }
            },
        )
    }

    if (showMoreTools) {
        MoreToolsDialog(
            tools = toolOrder,
            onSelectTool = { tool ->
                showMoreTools = false
                triggerTool(tool)
            },
            onSelectPower = { kind ->
                showMoreTools = false
                powerConfirmKind = kind
            },
            onSaveOrder = viewModel::saveToolOrder,
            onDismiss = { showMoreTools = false },
        )
    }

    if (powerConfirmKind != null) {
        val isReboot = powerConfirmKind == "reboot"
        val verb = if (isReboot) "重启" else "关机"
        AlertDialog(
            onDismissRequest = { powerConfirmKind = null },
            title = { Text("${verb}设备") },
            text = { Text("确认要${verb}「${device.name}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        powerConfirmKind = null
                        scope.launch {
                            snackbarHostState.showSnackbar("等待被控端支持远程电源协议（可使用「终端」执行 $verb）")
                        }
                    },
                ) {
                    Text("确认", color = palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { powerConfirmKind = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MetaRow(icon: ImageVector, label: String, value: String, valueColor: Color? = null) {
    val palette = LocalRabbitPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = palette.muted, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 14.sp, color = palette.muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = valueColor ?: palette.text)
    }
}

@Composable
private fun MoreToolsDialog(
    tools: List<DeviceToolKind>,
    onSelectTool: (DeviceToolKind) -> Unit,
    onSelectPower: (String) -> Unit,
    onSaveOrder: (List<DeviceToolKind>) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    var reordering by remember { mutableStateOf(false) }
    var draftOrder by remember(tools) { mutableStateOf(tools) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(palette.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("更多工具", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            Spacer(modifier = Modifier.height(2.dp))

            draftOrder.forEachIndexed { idx, tool ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.primarySoft)
                        .border(1.dp, palette.primary.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                        .clickable(enabled = !reordering) { onSelectTool(tool) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(toolIcon(tool), null, tint = palette.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(tool.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.text, modifier = Modifier.weight(1f))
                    if (reordering) {
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val m = draftOrder.toMutableList()
                                    m[idx] = m[idx - 1].also { m[idx - 1] = m[idx] }
                                    draftOrder = m
                                }
                            },
                            enabled = idx > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, "上移", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                if (idx < draftOrder.lastIndex) {
                                    val m = draftOrder.toMutableList()
                                    m[idx] = m[idx + 1].also { m[idx + 1] = m[idx] }
                                    draftOrder = m
                                }
                            },
                            enabled = idx < draftOrder.lastIndex,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, "下移", modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (!reordering) {
                Spacer(modifier = Modifier.height(2.dp))
                PowerRow(icon = Icons.Rounded.RestartAlt, label = "重启") { onSelectPower("reboot") }
                PowerRow(icon = Icons.Rounded.PowerSettingsNew, label = "关机") { onSelectPower("shutdown") }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(
                    onClick = {
                        if (reordering) {
                            onSaveOrder(draftOrder)
                            reordering = false
                        } else {
                            reordering = true
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(if (reordering) "保存顺序" else "调整顺序")
                }
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun PowerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.muted, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, fontSize = 15.sp, color = palette.text, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted, modifier = Modifier.size(18.dp))
    }
}

private fun toolIcon(kind: DeviceToolKind): ImageVector = when (kind) {
    DeviceToolKind.FileTransfer -> Icons.Rounded.FolderOpen
    DeviceToolKind.WatchMode -> Icons.Rounded.Visibility
    DeviceToolKind.Terminal -> Icons.Rounded.Terminal
    DeviceToolKind.PortMap -> Icons.Rounded.Cable
}
