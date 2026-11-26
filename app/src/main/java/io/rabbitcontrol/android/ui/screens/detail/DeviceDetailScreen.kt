package io.rabbitcontrol.android.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.rememberWallpaperImage
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import io.rabbitcontrol.android.ui.components.RabbitSecondaryButton
import io.rabbitcontrol.android.ui.components.RabbitSectionHeader
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import org.koin.androidx.compose.koinViewModel

private enum class DetailSheetMode {
    ReorderTools,
    MoreTools,
}

@OptIn(ExperimentalMaterial3Api::class)
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

    val toolOrder by viewModel.toolOrder.collectAsStateWithLifecycle()
    val wallpaperVersion by viewModel.wallpaperVersion.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()
    val removing by viewModel.removing.collectAsStateWithLifecycle()
    val connectError by viewModel.connectError.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTargets.collectAsStateWithLifecycle()

    val wallpaperBitmap = rememberWallpaperImage(viewModel.wallpaperCache, device.id, wallpaperVersion, 960)
    val isCurrentConnected = activeSession.targetId == device.id && activeSession.negotiated != null
    val isFav = device.id in favorites

    var sheetMode by remember { mutableStateOf<DetailSheetMode?>(null) }
    var powerConfirmKind by remember { mutableStateOf<String?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val osLabel = remember(device.os) { formatDetailOs(device.os) }

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
            RabbitTopBar(
                title = device.name,
                subtitle = osLabel,
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleFavorite(device.id) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                            contentDescription = "收藏",
                            tint = if (isFav) palette.primaryText else palette.muted,
                        )
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Lg),
            ) {
                // Compact Hero Card (0.dp shadow, hairline border)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RabbitRadius.Xl),
                    color = palette.heroDarkBg,
                    tonalElevation = 0.dp,
                    shadowElevation = RabbitElevation.Card,
                    border = BorderStroke(1.dp, palette.line),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(196.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(palette.heroCardBg, palette.heroDarkBg),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (wallpaperBitmap != null) {
                            Image(
                                bitmap = wallpaperBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xA60A0A0A)),
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x2EFFFFFF))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.LaptopMac, null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isCurrentConnected) "当前会话已连接" else viewModel.lastConnectedLabel(device.id),
                                fontSize = 13.sp,
                                color = Color(0xFFD4D4D4),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.startSessionAndNavigate(device.id, device.name, device.os, watchOnly = false) {
                                        onEnterDesktop(false)
                                    }
                                },
                                enabled = !connecting,
                                shape = RoundedCornerShape(RabbitRadius.Pill),
                                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.primary,
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = 22.dp),
                                modifier = Modifier.height(48.dp),
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
                }

                // 2x2 Quick Tools Grid
                RabbitSectionHeader(
                    title = "快捷工具",
                    trailing = {
                        Text(
                            text = "自定义排序",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.primaryText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .clickable { sheetMode = DetailSheetMode.ReorderTools }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    },
                )

                val rows = toolOrder.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            pair.forEach { tool ->
                                RabbitCard(
                                    onClick = if (!connecting) {
                                        { triggerTool(tool) }
                                    } else {
                                        null
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(palette.primarySoft),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(toolIcon(tool), null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = tool.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = palette.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Device Metadata & Power Entry Card
                RabbitCard(modifier = Modifier.fillMaxWidth()) {
                    val (statusLabel, statusTone) = when {
                        device.disabled -> "已停用" to StatusTone.Muted
                        device.busy -> "会话中" to StatusTone.Primary
                        device.online -> "在线" to StatusTone.Success
                        else -> "离线" to StatusTone.Muted
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Computer, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("设备状态", fontSize = 14.sp, color = palette.muted, modifier = Modifier.weight(1f))
                        RabbitStatusPill(label = statusLabel, tone = statusTone)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.LaptopMac, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("系统信息", fontSize = 14.sp, color = palette.muted, modifier = Modifier.weight(1f))
                        Text(osLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
                    }

                    if (device.id.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        val formatted = if (device.id.contains(':')) device.id else device.id.chunked(3).joinToString(" ")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .clickable {
                                    clipboard.setText(AnnotatedString(device.id))
                                    RabbitToast.success("已复制设备 ID")
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(Icons.Rounded.Key, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("设备 ID", fontSize = 14.sp, color = palette.muted, modifier = Modifier.weight(1f))
                            Text(formatted, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = palette.text)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.ContentCopy, null, tint = palette.primaryText, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .clickable { sheetMode = DetailSheetMode.MoreTools }
                            .padding(vertical = 4.dp),
                    ) {
                        Icon(Icons.Rounded.Tune, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("更多工具与电源管理", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
                    }

                    if (device.id.isNotBlank() && !device.id.contains(':')) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .alpha(if (removing) 0.5f else 1f)
                                .clickable(enabled = !removing) { showRemoveConfirm = true }
                                .padding(vertical = 4.dp),
                        ) {
                            Icon(Icons.Rounded.LinkOff, null, tint = palette.danger, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (removing) "正在解绑…" else "从账号解绑设备",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.danger,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    LaunchedEffect(connectError) {
        val err = connectError
        if (!err.isNullOrBlank()) {
            RabbitToast.error(err)
            viewModel.clearError()
        }
    }

    val activeSheet = sheetMode
    if (activeSheet != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var draftOrder by remember(activeSheet, toolOrder) { mutableStateOf(toolOrder) }

        ModalBottomSheet(
            onDismissRequest = { sheetMode = null },
            sheetState = sheetState,
            containerColor = palette.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = RabbitSpacing.Xl)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (activeSheet == DetailSheetMode.ReorderTools) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "自定义工具排序",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.text,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "决定详情页快捷工具的展示顺序",
                                fontSize = 12.sp,
                                color = palette.muted,
                            )
                        }
                        IconButton(
                            onClick = { sheetMode = null },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Close, "关闭", tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                    }

                    draftOrder.forEachIndexed { idx, tool ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Md))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.surface)
                                    .border(1.dp, palette.line, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.muted,
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.primarySoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(toolIcon(tool), null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                tool.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.text,
                                modifier = Modifier.weight(1f),
                            )
                            ReorderArrowButton(
                                icon = Icons.Rounded.ArrowUpward,
                                contentDescription = "上移",
                                enabled = idx > 0,
                                onClick = {
                                    if (idx > 0) {
                                        val m = draftOrder.toMutableList()
                                        m[idx] = m[idx - 1].also { m[idx - 1] = m[idx] }
                                        draftOrder = m
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            ReorderArrowButton(
                                icon = Icons.Rounded.ArrowDownward,
                                contentDescription = "下移",
                                enabled = idx < draftOrder.lastIndex,
                                onClick = {
                                    if (idx < draftOrder.lastIndex) {
                                        val m = draftOrder.toMutableList()
                                        m[idx] = m[idx + 1].also { m[idx + 1] = m[idx] }
                                        draftOrder = m
                                    }
                                },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RabbitSecondaryButton(
                            label = "取消",
                            onClick = { sheetMode = null },
                            modifier = Modifier.weight(1f),
                        )
                        RabbitPrimaryButton(
                            label = "保存排序",
                            onClick = {
                                viewModel.saveToolOrder(draftOrder)
                                sheetMode = null
                            },
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "更多工具与电源管理",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { sheetMode = DetailSheetMode.ReorderTools }) {
                            Text("调整顺序", color = palette.primaryText)
                        }
                        IconButton(
                            onClick = { sheetMode = null },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Rounded.Close, "关闭", tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                    }

                    toolOrder.forEach { tool ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Md))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                                .clickable {
                                    sheetMode = null
                                    triggerTool(tool)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.primarySoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(toolIcon(tool), null, tint = palette.primaryText, modifier = Modifier.size(17.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(tool.label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    Text("远程电源管理", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        PowerActionChip(
                            icon = Icons.Rounded.RestartAlt,
                            label = "重启设备",
                            modifier = Modifier.weight(1f),
                        ) {
                            sheetMode = null
                            powerConfirmKind = "reboot"
                        }
                        PowerActionChip(
                            icon = Icons.Rounded.PowerSettingsNew,
                            label = "关闭电源",
                            modifier = Modifier.weight(1f),
                        ) {
                            sheetMode = null
                            powerConfirmKind = "shutdown"
                        }
                    }
                }
            }
        }
    }

    if (powerConfirmKind != null) {
        val isReboot = powerConfirmKind == "reboot"
        val verb = if (isReboot) "重启" else "关机"
        RabbitDialog(
            title = "${verb}设备",
            message = "确认要${verb}「${device.name}」吗？",
            onDismissRequest = { powerConfirmKind = null },
            dismissLabel = "取消",
            confirmLabel = "确认$verb",
            destructive = true,
            onConfirm = {
                powerConfirmKind = null
                RabbitToast.info("等待被控端支持远程电源协议（可使用「终端」执行 $verb）")
            },
        )
    }

    if (showRemoveConfirm) {
        RabbitDialog(
            title = "解绑设备",
            message = "确定要从当前账号解绑「${device.name}」吗？解绑后将从我的设备列表中移除。",
            onDismissRequest = { showRemoveConfirm = false },
            dismissLabel = "取消",
            confirmLabel = "确认解绑",
            destructive = true,
            onConfirm = {
                showRemoveConfirm = false
                viewModel.removeDevice(device.id) {
                    RabbitToast.success("已解绑设备")
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun PowerActionChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(RabbitRadius.Md))
            .background(palette.dangerSoft)
            .border(1.dp, palette.danger.copy(alpha = 0.25f), RoundedCornerShape(RabbitRadius.Md))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.danger, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.danger)
    }
}

@Composable
private fun ReorderArrowButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.surface)
            .border(1.dp, palette.line, RoundedCornerShape(8.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.text,
            modifier = Modifier.size(17.dp),
        )
    }
}

private fun formatDetailOs(raw: String): String {
    val normalized = raw.trim().lowercase()
    return when {
        normalized.isEmpty() -> "桌面设备"
        normalized.contains("mac") || normalized.contains("darwin") -> "macOS"
        normalized.contains("win") -> "Windows"
        normalized.contains("linux") -> "Linux"
        normalized.contains("android") -> "Android"
        normalized.contains("ios") -> "iOS"
        else -> raw.trim()
    }
}

private fun toolIcon(kind: DeviceToolKind): ImageVector = when (kind) {
    DeviceToolKind.FileTransfer -> Icons.Rounded.FolderOpen
    DeviceToolKind.WatchMode -> Icons.Rounded.Visibility
    DeviceToolKind.Terminal -> Icons.Rounded.Terminal
    DeviceToolKind.PortMap -> Icons.Rounded.Cable
}
