package io.rabbitcontrol.android.ui.screens.home

import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenDevice: (RemoteDevice) -> Unit,
    onReturnToDesktop: () -> Unit,
    onOpenPreviewCheck: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val kickedNotice by viewModel.kickedNotice.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(profile?.id, profile?.needsPassword) {
        if (profile != null && profile?.needsPassword == false) {
            viewModel.refreshDevices()
        }
    }

    if (!restoring && profile == null) {
        // Guide unauthenticated users smoothly to login while keeping Profile accessible if needed.
    }

    if (!kickedNotice.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissKicked,
            title = { Text("服务器连接已断开") },
            text = { Text(kickedNotice.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissKicked) { Text("知道了") }
            },
        )
    }

    Scaffold(
        containerColor = palette.canvas,
        bottomBar = {
            Column {
                HorizontalDivider(color = palette.line, thickness = 0.8.dp)
                NavigationBar(containerColor = palette.surface) {
                    HomeTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val icon = when (tab) {
                            HomeTab.Devices -> Icons.Rounded.Devices
                            HomeTab.Assist -> Icons.Rounded.SettingsRemote
                            HomeTab.Me -> Icons.Rounded.Person
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.primary,
                                selectedTextColor = palette.primary,
                                indicatorColor = palette.primarySoft,
                                unselectedIconColor = palette.muted,
                                unselectedTextColor = palette.muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (activeSession.status is SessionStatus.Streaming || activeSession.status is SessionStatus.Connecting) {
                ActiveSessionBanner(
                    deviceName = activeSession.deviceName.ifBlank { activeSession.targetId },
                    subtitle = if (activeSession.watchOnly) "观看模式运行中" else "远控会话运行中 · 点击回到桌面",
                    onClick = onReturnToDesktop,
                )
            }

            when (selectedTab) {
                HomeTab.Devices -> DevicesTab(viewModel = viewModel, onOpenAuth = onOpenAuth, onOpenDevice = onOpenDevice)
                HomeTab.Assist -> AssistTab(viewModel = viewModel, onOpenDevice = onOpenDevice)
                HomeTab.Me -> ProfileTab(
                    viewModel = viewModel,
                    onOpenAuth = onOpenAuth,
                    onOpenPreviewCheck = onOpenPreviewCheck,
                )
            }
        }
    }
}

@Composable
private fun ActiveSessionBanner(deviceName: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LaptopMac, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(deviceName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        Text("回到桌面 ›", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesTab(
    viewModel: HomeViewModel,
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenDevice: (RemoteDevice) -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val savedEmails by viewModel.savedAccountEmails.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val onlineOnly by viewModel.onlineOnly.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshingDevices.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val error by viewModel.deviceError.collectAsStateWithLifecycle()
    val wallpaperVersion by viewModel.wallpaperVersion.collectAsStateWithLifecycle()

    var showServerSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("可控设备", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = palette.text, modifier = Modifier.weight(1f))
                if (profile != null) {
                    IconButton(onClick = viewModel::refreshDevices, enabled = !refreshing) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = palette.muted)
                    }
                    Text(
                        text = if (onlineOnly) "全部设备" else "只看在线",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.primarySoft)
                            .clickable(onClick = viewModel::toggleOnlineOnly)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            if (server != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(12.dp))
                        .clickable { showServerSheet = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Dns, null, tint = palette.primary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${server!!.displayName}${if (servers.size > 1) " (${servers.size})" else ""}",
                        fontSize = 12.sp,
                        color = palette.text,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, "切换服务器", tint = palette.muted, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (profile == null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                ) {
                    Text(
                        if (restoring) "正在恢复账号…" else "登录后查看设备",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("登录 RabbitControl 账号后，会自动同步你的电脑设备并开启免密快连。", color = palette.muted, fontSize = 14.sp)
                    if (!restoring) {
                        Spacer(modifier = Modifier.height(14.dp))
                        if (server == null) {
                            Button(
                                onClick = { onOpenAuth(AuthStep.Server, false) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Text("连接服务器并登录", color = Color.White)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { onOpenAuth(AuthStep.Login, false) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                    modifier = Modifier.weight(1f).height(46.dp),
                                ) {
                                    Text("登录", color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = { onOpenAuth(AuthStep.Register, false) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp),
                                ) {
                                    Text("注册新账号")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { onOpenAuth(AuthStep.Server, true) },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text("切换或添加服务器", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        } else if (profile?.needsPassword == true) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                        .padding(20.dp),
                ) {
                    Text("请先修改初始密码", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("首次登录需要设置新密码，完成后即可查看和远控你的设备。", color = palette.muted, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { onOpenAuth(AuthStep.Password, false) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("设置新密码")
                    }
                }
            }
        } else {
            if (!error.isNullOrBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.dangerSoft)
                            .padding(16.dp),
                    ) {
                        Text(error.orEmpty(), color = palette.danger, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::refreshDevices) { Text("重试") }
                    }
                }
            }

            val online = devices.filter { (it.online || it.busy) && !it.disabled }
            val offline = devices.filterNot { (it.online || it.busy) && !it.disabled }

            if (devices.isEmpty() && !refreshing && error == null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                            .padding(20.dp),
                    ) {
                        Text("暂无已绑定设备", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("在电脑端登录同一 RabbitControl 账号后，设备会自动出现在这里。", color = palette.muted, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(onClick = viewModel::refreshDevices, modifier = Modifier.fillMaxWidth()) {
                            Text("刷新设备")
                        }
                    }
                }
            }

            if (online.isNotEmpty()) {
                item {
                    Text("在线设备 ${online.size}", fontSize = 13.sp, color = palette.muted, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                }
                items(online, key = { it.id }) { dev ->
                    val thumb = remember(dev.id, wallpaperVersion) { viewModel.wallpaperCache.loadBitmap(dev.id) }
                    DeviceCard(device = dev, thumbnail = thumb, onClick = { onOpenDevice(dev) })
                }
            }

            if (!onlineOnly && offline.isNotEmpty()) {
                item {
                    Text("离线设备 ${offline.size}", fontSize = 13.sp, color = palette.muted, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                }
                items(offline, key = { it.id }) { dev ->
                    val thumb = remember(dev.id, wallpaperVersion) { viewModel.wallpaperCache.loadBitmap(dev.id) }
                    DeviceCard(device = dev, thumbnail = thumb, onClick = { onOpenDevice(dev) })
                }
            }
        }
        item { Spacer(modifier = Modifier.height(14.dp)) }
    }

    if (showServerSheet) {
        ModalBottomSheet(onDismissRequest = { showServerSheet = false }, containerColor = palette.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("切换服务器", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                servers.forEach { srv ->
                    val isActive = srv.id == server?.id
                    val email = savedEmails[srv.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) palette.primarySoft.copy(alpha = 0.45f) else palette.field)
                            .border(1.dp, if (isActive) palette.primary else palette.line, RoundedCornerShape(12.dp))
                            .clickable {
                                showServerSheet = false
                                if (!isActive) {
                                    viewModel.switchServer(srv) {
                                        onOpenAuth(AuthStep.Login, false)
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Dns, null, tint = if (isActive) palette.primary else palette.muted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(srv.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                            Text(email ?: "未登录", fontSize = 12.sp, color = palette.muted)
                        }
                        if (isActive) {
                            Icon(Icons.Rounded.Check, null, tint = palette.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        showServerSheet = false
                        onOpenAuth(AuthStep.Server, true)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("管理 / 添加新服务器")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DeviceCard(device: RemoteDevice, thumbnail: Bitmap?, onClick: () -> Unit) {
    val palette = LocalRabbitPalette.current
    val online = (device.online || device.busy) && !device.disabled
    val statusText = when {
        device.disabled -> "已停用"
        device.busy -> "会话中"
        device.online -> "在线"
        else -> "离线"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(1.dp, palette.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.heroDarkBg)
                .alpha(if (online) 1f else 0.52f),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.LaptopMac, null, tint = Color(0xFFB7C2D1), modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            Spacer(modifier = Modifier.height(3.dp))
            Text(device.os.ifBlank { "桌面设备" }, fontSize = 13.sp, color = palette.muted)
            Spacer(modifier = Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (online) palette.success else palette.muted),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(statusText, fontSize = 12.sp, color = if (online) palette.success else palette.muted)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistTab(
    viewModel: HomeViewModel,
    onOpenDevice: (RemoteDevice) -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val input by viewModel.assistTargetInput.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val recent by viewModel.recentTargets.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteTargets.collectAsStateWithLifecycle()

    var sheetKind by remember { mutableStateOf<String?>(null) }
    var invalidError by remember { mutableStateOf<String?>(null) }
    val validTarget = remember(input) { viewModel.parseTargetOrIpPort(input) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text("远程协助", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.SettingsRemote, null, tint = palette.primary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("远控伙伴设备", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    Text("输入 9 位设备码或局域网 IP:端口", fontSize = 13.sp, color = palette.muted)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text("伙伴的设备 ID / IP:端口", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.text)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = input,
                onValueChange = {
                    viewModel.updateAssistInput(it)
                    invalidError = null
                },
                placeholder = { Text("例如 604 928 235 或 192.168.1.20:9901", color = palette.muted, fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.field,
                    unfocusedContainerColor = palette.field,
                    focusedBorderColor = palette.primary,
                    unfocusedBorderColor = palette.line,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!invalidError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(invalidError.orEmpty(), color = palette.danger, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val parsed = viewModel.parseTargetOrIpPort(input)
                    if (parsed == null) {
                        invalidError = "请输入有效的 9 位设备码或 IP:端口"
                        return@Button
                    }
                    viewModel.recordTarget(parsed)
                    val matched = devices.firstOrNull { it.id == parsed } ?: RemoteDevice(
                        id = parsed,
                        name = if (parsed.contains(':')) parsed else parsed.chunked(3).joinToString(" "),
                        os = if (parsed.contains(':')) "局域网直连主机" else "伙伴设备",
                        online = true,
                        busy = false,
                        disabled = false,
                    )
                    onOpenDevice(matched)
                },
                enabled = validTarget != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text("开始远控", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { sheetKind = "recent" }) {
                    Icon(Icons.Rounded.History, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("最近连接 (${recent.size})")
                }
                TextButton(onClick = { sheetKind = "favorites" }) {
                    Icon(Icons.Rounded.Bookmark, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("我的收藏 (${favorites.size})")
                }
            }
        }
    }

    if (sheetKind != null) {
        val list = if (sheetKind == "recent") recent else favorites
        val title = if (sheetKind == "recent") "最近连接" else "我的收藏"
        ModalBottomSheet(onDismissRequest = { sheetKind = null }, containerColor = palette.surface) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Spacer(modifier = Modifier.height(12.dp))
                if (list.isEmpty()) {
                    Text("暂无记录", color = palette.muted, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    list.forEach { item ->
                        val fav = item in favorites
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.updateAssistInput(item)
                                    sheetKind = null
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (item.contains(':')) item else item.chunked(3).joinToString(" "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                color = palette.text,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.toggleFavorite(item) }) {
                                Icon(
                                    imageVector = if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                    contentDescription = "收藏",
                                    tint = if (fav) palette.primary else palette.muted,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProfileTab(
    viewModel: HomeViewModel,
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenPreviewCheck: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val signal by viewModel.signalStatus.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("我的", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = palette.text)

        // Account & Server Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(palette.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, null, tint = palette.primary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile?.name?.ifBlank { profile?.email } ?: "未登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    Text(profile?.email ?: "登录后同步设备与免密自控证书", fontSize = 13.sp, color = palette.muted)
                }
                if (profile == null) {
                    TextButton(onClick = { onOpenAuth(AuthStep.Login, false) }) { Text("登录") }
                    TextButton(onClick = { onOpenAuth(AuthStep.Register, false) }) { Text("注册") }
                }
            }
            HorizontalDivider(color = palette.line, thickness = 0.6.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAuth(AuthStep.Server, true) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Dns, null, tint = palette.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("服务器管理", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.text)
                    val srvSub = if (server != null) {
                        "${server?.displayName} · 已保存 ${servers.size} 台"
                    } else {
                        "尚未配置服务器"
                    }
                    Text(srvSub, fontSize = 13.sp, color = palette.muted)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
            }
            if (profile != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Sensors, null, tint = if (signal.state == "connected") palette.success else palette.muted, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    val sigText = when (signal.state) {
                        "connected" -> "信令在线 · 本机码 ${signal.deviceIdFormatted ?: "-"}"
                        "connecting", "reconnecting" -> "信令连接中…"
                        "error" -> "信令异常：${signal.lastError ?: "重连中"}"
                        else -> "信令未启动"
                    }
                    Text(sigText, fontSize = 13.sp, color = palette.muted)
                }
            }
        }

        // Interaction Preferences Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("操控与默认偏好", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("默认交互模式", fontSize = 14.sp, color = palette.text)
                    Text(prefs.touchMode.label, fontSize = 12.sp, color = palette.muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TouchInputMode.entries.forEach { mode ->
                        val active = prefs.touchMode == mode
                        Text(
                            mode.label,
                            fontSize = 12.sp,
                            color = if (active) Color.White else palette.muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) palette.primary else palette.field)
                                .clickable { viewModel.updatePrefs { it.copy(touchMode = mode) } }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("指针加速灵敏度", fontSize = 14.sp, color = palette.text)
                    Text("${String.format("%.2f", prefs.pointerSensitivity)}x", fontSize = 13.sp, color = palette.muted)
                }
                Slider(
                    value = prefs.pointerSensitivity,
                    onValueChange = { v -> viewModel.updatePrefs { it.copy(pointerSensitivity = v) } },
                    valueRange = 0.5f..2.5f,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("默认屏幕方向", fontSize = 14.sp, color = palette.text)
                    Text(prefs.orientationPref.label, fontSize = 12.sp, color = palette.muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrientationPreference.entries.forEach { opt ->
                        val active = prefs.orientationPref == opt
                        val shortLabel = when (opt) {
                            OrientationPreference.LandscapeFirst -> "横屏"
                            OrientationPreference.AutoSensor -> "自动"
                            OrientationPreference.PortraitTrackpad -> "竖屏"
                        }
                        Text(
                            shortLabel,
                            fontSize = 12.sp,
                            color = if (active) Color.White else palette.muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) palette.primary else palette.field)
                                .clickable { viewModel.updatePrefs { it.copy(orientationPref = opt) } }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            PreferenceToggleRow("默认开启性能 HUD", prefs.showHud) { on ->
                viewModel.updatePrefs { it.copy(showHud = on) }
            }
            PreferenceToggleRow("默认同步远端声音", prefs.audioEnabledDefault) { on ->
                viewModel.updatePrefs { it.copy(audioEnabledDefault = on) }
            }
            PreferenceToggleRow("默认同步剪贴板", prefs.clipboardEnabledDefault) { on ->
                viewModel.updatePrefs { it.copy(clipboardEnabledDefault = on) }
            }
            PreferenceToggleRow("直点模式指尖防遮挡微偏置", prefs.directTouchOffset) { on ->
                viewModel.updatePrefs { it.copy(directTouchOffset = on) }
            }
        }

        // Offline Verification Entry
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surface)
                .border(1.dp, palette.line, RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenPreviewCheck)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, tint = palette.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("画面与手势校验", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = palette.text)
                Text("离线播放样片，检查硬解能力与触控编码", fontSize = 12.sp, color = palette.muted)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
        }

        if (profile != null) {
            OutlinedButton(
                onClick = { viewModel.logout {} },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("退出登录", color = palette.danger)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PreferenceToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, fontSize = 14.sp, color = LocalRabbitPalette.current.text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
