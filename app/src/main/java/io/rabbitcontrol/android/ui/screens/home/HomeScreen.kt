package io.rabbitcontrol.android.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.data.AppThemeMode
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitEmptyState
import io.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import io.rabbitcontrol.android.ui.components.RabbitSecondaryButton
import io.rabbitcontrol.android.ui.components.RabbitSectionHeader
import io.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.screens.settings.getAppVersionName
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenDevice: (RemoteDevice) -> Unit,
    onReturnToDesktop: () -> Unit,
    onOpenPreviewCheck: () -> Unit,
    onOpenProfileRoute: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val kickedNotice by viewModel.kickedNotice.collectAsStateWithLifecycle()
    val hostState by viewModel.hostState.collectAsStateWithLifecycle()

    LaunchedEffect(profile?.id, profile?.needsPassword) {
        if (profile != null && profile?.needsPassword == false) {
            viewModel.refreshDevices()
        }
    }

    hostState.pendingApproval?.let { req ->
        RabbitDialog(
            title = "收到远程协助请求",
            onDismissRequest = { viewModel.respondHostApproval(req.generation, false) },
            dismissLabel = "拒绝",
            onDismissAction = { viewModel.respondHostApproval(req.generation, false) },
            confirmLabel = "允许控制",
            onConfirm = { viewModel.respondHostApproval(req.generation, true) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("来源地址：${req.remoteAddr}", fontSize = 14.sp, color = palette.muted)
                Text("请求画面：${req.requestedRes} @ ${req.requestedFps}fps", fontSize = 14.sp, color = palette.muted)
                Text(
                    text = if (req.passwordVerified) "临时验证码已通过校验" else "未经过密码校验，请确认是否本人或受信任协助者",
                    fontSize = 13.sp,
                    color = if (req.passwordVerified) palette.success else palette.warning,
                )
            }
        }
    }

    if (!kickedNotice.isNullOrBlank()) {
        RabbitDialog(
            title = "服务器连接已断开",
            message = kickedNotice.orEmpty(),
            onDismissRequest = viewModel::dismissKicked,
            confirmLabel = "知道了",
            onConfirm = viewModel::dismissKicked,
        )
    }

    Scaffold(
        containerColor = palette.canvas,
        bottomBar = {
            Column {
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                NavigationBar(
                    containerColor = palette.surface,
                    tonalElevation = 0.dp,
                ) {
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
                            label = { Text(tab.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = palette.primaryText,
                                selectedTextColor = palette.primaryText,
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
                    subtitle = if (activeSession.watchOnly) "观看模式运行中" else "远控会话运行中",
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
                    onOpenProfileRoute = onOpenProfileRoute,
                )
            }
        }
    }
}

@Composable
private fun ActiveSessionBanner(deviceName: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalRabbitPalette.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Sm)
            .clip(RoundedCornerShape(RabbitRadius.Md))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(RabbitRadius.Md),
        color = palette.primary,
        tonalElevation = 0.dp,
        shadowElevation = RabbitElevation.Floating,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF6EE7B7)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(deviceName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(RabbitRadius.Pill))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("回到桌面", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
        }
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
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (profile != null && profile?.needsPassword == false) {
                viewModel.refreshDevices()
            }
        },
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                containerColor = palette.surface,
                color = palette.primary,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = RabbitSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
            contentPadding = PaddingValues(top = RabbitSpacing.Md, bottom = RabbitSpacing.Xl),
        ) {
            val online = devices.filter { (it.online || it.busy) && !it.disabled }
            val offline = devices.filterNot { (it.online || it.busy) && !it.disabled }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "我的设备",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (server != null) {
                            Row(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(RabbitRadius.Pill))
                                    .background(palette.surface)
                                    .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Pill))
                                    .clickable { showServerSheet = true }
                                    .padding(horizontal = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Dns, null, tint = palette.primaryText, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${server!!.displayName}${if (servers.size > 1) " · ${servers.size}台" else ""}",
                                    fontSize = 12.sp,
                                    color = palette.text,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(Icons.Rounded.KeyboardArrowDown, "切换服务器", tint = palette.muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    if (profile != null && profile?.needsPassword == false && devices.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeviceFilterChip(
                                label = "全部 ${devices.size}",
                                selected = !onlineOnly,
                                onClick = { if (onlineOnly) viewModel.toggleOnlineOnly() },
                            )
                            DeviceFilterChip(
                                label = "在线 ${online.size}",
                                selected = onlineOnly,
                                onClick = { if (!onlineOnly) viewModel.toggleOnlineOnly() },
                            )
                        }
                    }
                }
            }

            if (profile == null) {
                item {
                    RabbitCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (restoring) "正在恢复账号…" else "登录后查看设备",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("登录 RabbitControl 账号后，将自动同步你的电脑设备并开启免密快连。", color = palette.muted, fontSize = 14.sp)
                        if (!restoring) {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (server == null) {
                                RabbitPrimaryButton(
                                    label = "连接服务器并登录",
                                    onClick = { onOpenAuth(AuthStep.Server, false) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    RabbitPrimaryButton(
                                        label = "登录",
                                        onClick = { onOpenAuth(AuthStep.Login, false) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    RabbitSecondaryButton(
                                        label = "注册新账号",
                                        onClick = { onOpenAuth(AuthStep.Register, false) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = { onOpenAuth(AuthStep.Server, true) },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Text("切换或添加服务器", fontSize = 13.sp, color = palette.primaryText)
                                }
                            }
                        }
                    }
                }
            } else if (profile?.needsPassword == true) {
                item {
                    RabbitCard(modifier = Modifier.fillMaxWidth()) {
                        Text("请先修改初始密码", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("首次登录需要设置新密码，完成后即可查看和远控你的设备。", color = palette.muted, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        RabbitPrimaryButton(
                            label = "设置新密码",
                            onClick = { onOpenAuth(AuthStep.Password, false) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                item {
                    var lastErrorText by remember { mutableStateOf("") }
                    if (!error.isNullOrBlank()) {
                        lastErrorText = error.orEmpty()
                    }
                    AnimatedVisibility(
                        visible = !error.isNullOrBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        val (errTitle, errSub) = remember(lastErrorText) {
                            formatConnectionError(lastErrorText)
                        }
                        RabbitCard(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(palette.dangerSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WifiOff,
                                        contentDescription = null,
                                        tint = palette.danger,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = errTitle,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = errSub,
                                        fontSize = 11.sp,
                                        color = palette.muted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(palette.field)
                                        .border(1.dp, palette.line, RoundedCornerShape(8.dp))
                                        .clickable(enabled = !refreshing, onClick = viewModel::refreshDevices)
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "重试",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.text,
                                    )
                                }
                            }
                        }
                    }
                }

                if (devices.isEmpty() && !refreshing && error == null) {
                    item {
                        RabbitEmptyState(
                            icon = Icons.Rounded.Devices,
                            title = "暂无已绑定设备",
                            description = "在电脑端登录同一 RabbitControl 账号后，设备会自动出现在这里。",
                            actionLabel = "刷新设备",
                            onAction = viewModel::refreshDevices,
                        )
                    }
                }

                val showGroupHeaders = online.isNotEmpty() && !onlineOnly && offline.isNotEmpty()

                if (online.isNotEmpty()) {
                    if (showGroupHeaders) {
                        item {
                            RabbitSectionHeader(title = "在线设备 · ${online.size}")
                        }
                    }
                    items(online, key = { it.id }) { dev ->
                        val thumb = remember(dev.id, wallpaperVersion) { viewModel.wallpaperCache.loadBitmap(dev.id) }
                        DeviceCard(device = dev, thumbnail = thumb, onClick = { onOpenDevice(dev) })
                    }
                }

                if (!onlineOnly && offline.isNotEmpty()) {
                    if (showGroupHeaders) {
                        item {
                            RabbitSectionHeader(title = "离线设备 · ${offline.size}")
                        }
                    }
                    items(offline, key = { it.id }) { dev ->
                        val thumb = remember(dev.id, wallpaperVersion) { viewModel.wallpaperCache.loadBitmap(dev.id) }
                        DeviceCard(device = dev, thumbnail = thumb, onClick = { onOpenDevice(dev) })
                    }
                }
            }
        }
    }

    if (showServerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showServerSheet = false },
            containerColor = palette.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RabbitSpacing.Xl, vertical = RabbitSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("切换服务器", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                servers.forEach { srv ->
                    val isActive = srv.id == server?.id
                    val email = savedEmails[srv.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RabbitRadius.Md))
                            .background(if (isActive) palette.primarySoft else palette.field)
                            .border(1.dp, if (isActive) palette.primary else palette.line, RoundedCornerShape(RabbitRadius.Md))
                            .clickable {
                                showServerSheet = false
                                if (!isActive) {
                                    viewModel.switchServer(srv) {
                                        onOpenAuth(AuthStep.Login, false)
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Dns, null, tint = if (isActive) palette.primaryText else palette.muted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(srv.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                            Text(email ?: "未登录", fontSize = 12.sp, color = palette.muted)
                        }
                        if (isActive) {
                            Icon(Icons.Rounded.Check, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                RabbitSecondaryButton(
                    label = "管理 / 添加新服务器",
                    onClick = {
                        showServerSheet = false
                        onOpenAuth(AuthStep.Server, true)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DeviceFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(RabbitRadius.Pill))
            .background(if (selected) palette.surface else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, palette.lineStrong, RoundedCornerShape(RabbitRadius.Pill))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) palette.text else palette.muted,
        )
    }
}

private fun formatConnectionError(raw: String): Pair<String, String> {
    val lower = raw.lowercase()
    return if (
        lower.contains("tls") ||
        lower.contains("https") ||
        lower.contains("unexpected end of file") ||
        lower.contains("reset") ||
        lower.contains("timed out") ||
        lower.contains("timeout") ||
        lower.contains("connect")
    ) {
        "无法连接服务器" to "网络波动或代理异常，连接恢复后将自动消失"
    } else {
        "刷新设备失败" to raw.trim()
    }
}

private fun formatDeviceOs(raw: String): String {
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

private fun deviceOsIcon(raw: String): ImageVector {
    val normalized = raw.trim().lowercase()
    return when {
        normalized.contains("android") || normalized.contains("ios") -> Icons.Rounded.PhoneAndroid
        normalized.contains("win") || normalized.contains("linux") -> Icons.Rounded.DesktopWindows
        else -> Icons.Rounded.LaptopMac
    }
}

@Composable
private fun DeviceCard(device: RemoteDevice, thumbnail: Bitmap?, onClick: () -> Unit) {
    val palette = LocalRabbitPalette.current
    val online = (device.online || device.busy) && !device.disabled
    val (statusLabel, dotColor) = when {
        device.disabled -> "已停用" to Color(0xFF9E9E9E)
        device.busy -> "会话中" to Color(0xFF60A5FA)
        device.online -> "在线" to Color(0xFF34D399)
        else -> "离线" to Color(0xFF9E9E9E)
    }
    RabbitCard(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = RabbitRadius.Lg, bottomStart = RabbitRadius.Lg))
                    .background(palette.heroDarkBg),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (online) 1f else 0.45f),
                    )
                } else {
                    Icon(
                        imageVector = deviceOsIcon(device.os),
                        contentDescription = null,
                        tint = Color(0xFFA3A3A3),
                        modifier = Modifier
                            .size(26.dp)
                            .alpha(if (online) 1f else 0.55f),
                    )
                }

                if (palette.isDark) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(16.dp)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, palette.surface))),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(0.5.dp)
                            .fillMaxHeight()
                            .background(palette.line.copy(alpha = 0.7f)),
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(RabbitRadius.Pill))
                        .background(Color(0x9E000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    device.name,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = deviceOsIcon(device.os),
                        contentDescription = null,
                        tint = palette.muted,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = formatDeviceOs(device.os),
                        fontSize = 12.sp,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (online) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = palette.primaryText,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (device.busy) "返回控制" else "连接桌面",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.text,
                            )
                        }
                    } else {
                        Text(
                            text = if (device.disabled) "已停用" else "当前离线",
                            fontSize = 11.sp,
                            color = palette.muted.copy(alpha = 0.8f),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(palette.field),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            null,
                            tint = palette.muted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

private enum class AssistQuickTab(val label: String) {
    Recent("最近连接"),
    Favorites("我的收藏"),
}

private class DeviceCodeVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(9)
        val out = buildString {
            digits.forEachIndexed { idx, ch ->
                if (idx == 3 || idx == 6) append(' ')
                append(ch)
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val transformed = when {
                    offset <= 3 -> offset
                    offset <= 6 -> offset + 1
                    else -> offset + 2
                }
                return transformed.coerceIn(0, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val original = when {
                    offset <= 3 -> offset
                    offset <= 7 -> offset - 1
                    else -> offset - 2
                }
                return original.coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}

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

    val validRecent = remember(recent) {
        recent.filter { !it.contains(':') && it.filter(Char::isDigit).length == 9 }
    }
    val validFavorites = remember(favorites) {
        favorites.filter { !it.contains(':') && it.filter(Char::isDigit).length == 9 }
    }

    var quickTab by remember { mutableStateOf(AssistQuickTab.Recent) }
    var invalidError by remember { mutableStateOf<String?>(null) }
    val validTarget = remember(input) { viewModel.parseDeviceCode(input) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Lg),
        ) {
            Text("远程协助", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.text)

            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Md))
                            .background(palette.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.SettingsRemote, null, tint = palette.primaryText, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("远控伙伴设备", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Text("输入对方 9 位数字设备码发起远控", fontSize = 12.sp, color = palette.muted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                RabbitTextField(
                    value = input,
                    onValueChange = {
                        viewModel.updateAssistInput(it)
                        invalidError = null
                    },
                    label = "9 位设备码",
                    placeholder = "例如 604 928 235",
                    keyboardType = KeyboardType.Number,
                    visualTransformation = remember { DeviceCodeVisualTransformation() },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    ),
                    trailingIcon = if (input.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    viewModel.updateAssistInput("")
                                    invalidError = null
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "清空",
                                    tint = palette.muted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                if (!invalidError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(invalidError.orEmpty(), color = palette.danger, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))
                RabbitPrimaryButton(
                    label = "开始远控",
                    enabled = validTarget != null,
                    onClick = {
                        val parsed = viewModel.parseDeviceCode(input)
                        if (parsed == null) {
                            invalidError = "请输入有效的 9 位数字设备码"
                            return@RabbitPrimaryButton
                        }
                        viewModel.recordTarget(parsed)
                        val matched = devices.firstOrNull { it.id == parsed } ?: RemoteDevice(
                            id = parsed,
                            name = parsed.chunked(3).joinToString(" "),
                            os = "伙伴设备",
                            online = true,
                            busy = false,
                            disabled = false,
                        )
                        onOpenDevice(matched)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Inline Quick Connect (Recent & Favorites)
            val list = if (quickTab == AssistQuickTab.Recent) validRecent else validFavorites
            RabbitSectionHeader(title = "快速填充")
            RabbitSegmentedControl(
                options = AssistQuickTab.entries,
                selected = quickTab,
                labelProvider = {
                    val count = if (it == AssistQuickTab.Recent) validRecent.size else validFavorites.size
                    "${it.label} ($count)"
                },
                onSelect = { quickTab = it },
            )
            RabbitCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = RabbitSpacing.Md, vertical = RabbitSpacing.Sm),
            ) {
                if (list.isEmpty()) {
                    Text(
                        text = if (quickTab == AssistQuickTab.Recent) "暂无最近连接记录" else "暂无收藏设备",
                        color = palette.muted,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = RabbitSpacing.Xl),
                    )
                } else {
                    list.forEachIndexed { idx, item ->
                        if (idx > 0) {
                            HorizontalDivider(color = palette.line, thickness = 1.dp)
                        }
                        val fav = item in validFavorites
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .clickable {
                                    viewModel.updateAssistInput(item)
                                    invalidError = null
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.chunked(3).joinToString(" "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                color = palette.text,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.toggleFavorite(item) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                    contentDescription = "收藏",
                                    tint = if (fav) palette.primaryText else palette.muted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab(
    viewModel: HomeViewModel,
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenPreviewCheck: () -> Unit,
    onOpenProfileRoute: (String) -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val context = LocalContext.current
    val versionName = remember { getAppVersionName(context) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val signal by viewModel.signalStatus.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val hostState by viewModel.hostState.collectAsStateWithLifecycle()

    var showThemeSheet by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
        verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
    ) {
        Text("我的", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.text)

        // Card 1: Account & Signal Overview
        RabbitCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, null, tint = palette.primaryText)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile?.name?.ifBlank { profile?.email } ?: "未登录",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                    Text(
                        profile?.email ?: "登录后同步设备与免密自控证书",
                        fontSize = 12.sp,
                        color = palette.muted,
                    )
                }
                if (profile == null) {
                    TextButton(onClick = { onOpenAuth(AuthStep.Login, false) }) {
                        Text("登录", color = palette.primaryText)
                    }
                    TextButton(onClick = { onOpenAuth(AuthStep.Register, false) }) {
                        Text("注册", color = palette.primaryText)
                    }
                }
            }

            if (profile != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Sensors,
                        null,
                        tint = if (signal.state == "connected") palette.success else palette.muted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val sigText = when (signal.state) {
                        "connected" -> "信令在线 · 本机码 ${signal.deviceIdFormatted ?: "-"}"
                        "connecting", "reconnecting" -> "信令连接中…"
                        "error" -> "信令异常：${signal.lastError ?: "重连中"}"
                        else -> "信令未启动"
                    }
                    Text(sigText, fontSize = 12.sp, color = palette.muted)
                }
            }
        }

        // Card 2: Grouped Settings & Features
        RabbitCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = RabbitSpacing.Lg, vertical = 4.dp),
        ) {
            val srvTrailing = if (server != null) {
                if (servers.size > 1) "${server?.displayName} (${servers.size})" else server?.displayName.orEmpty()
            } else {
                "未配置"
            }
            ProfileMenuRow(
                icon = Icons.Rounded.Dns,
                title = "服务器管理",
                trailingText = srvTrailing,
                onClick = { onOpenAuth(AuthStep.Server, true) },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            val (hostLabel, hostTone) = when {
                hostState.connectedPeer != null -> "远控中" to StatusTone.Primary
                hostState.enabled -> "等待连接" to StatusTone.Success
                hostState.starting -> "启动中" to StatusTone.Warning
                else -> "已关闭" to StatusTone.Muted
            }
            ProfileMenuRow(
                icon = Icons.Rounded.ScreenShare,
                title = "手机被控端",
                trailingContent = { RabbitStatusPill(label = hostLabel, tone = hostTone) },
                onClick = { onOpenProfileRoute("settings/host") },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.Tune,
                title = "远控默认设置",
                trailingText = prefs.touchMode.label,
                onClick = { onOpenProfileRoute("settings/interaction") },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.DarkMode,
                title = "外观主题",
                trailingText = prefs.themeMode.label,
                onClick = { showThemeSheet = true },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.PlayArrow,
                title = "画面与手势校验",
                trailingText = "离线诊断",
                onClick = onOpenPreviewCheck,
            )
        }

        // Card 3: Updates & About
        RabbitCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = RabbitSpacing.Lg, vertical = 4.dp),
        ) {
            ProfileMenuRow(
                icon = Icons.Rounded.SystemUpdate,
                title = "版本更新",
                trailingText = "v$versionName",
                onClick = { showVersionDialog = true },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)
            ProfileMenuRow(
                icon = Icons.Rounded.Info,
                title = "关于我们",
                onClick = { onOpenProfileRoute("settings/about") },
            )
        }

        if (profile != null) {
            RabbitCard(
                onClick = { showLogoutConfirm = true },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "退出登录",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.danger,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showThemeSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = sheetState,
            containerColor = palette.surface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = RabbitSpacing.Xl)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("外观主题", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text("远控桌面会话始终保持深色沉浸画布", fontSize = 12.sp, color = palette.muted)
                Spacer(modifier = Modifier.height(2.dp))
                AppThemeMode.entries.forEach { mode ->
                    val selected = prefs.themeMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Md))
                            .background(if (selected) palette.primarySoft else palette.field)
                            .border(
                                1.dp,
                                if (selected) palette.primary else palette.line,
                                RoundedCornerShape(RabbitRadius.Md),
                            )
                            .clickable {
                                viewModel.updatePrefs { it.copy(themeMode = mode) }
                                showThemeSheet = false
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Rounded.Check, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showVersionDialog) {
        RabbitDialog(
            title = "版本更新",
            message = "当前版本：v$versionName\n\n配置 GitHub 仓库地址后即可在线检查与更新版本。",
            onDismissRequest = { showVersionDialog = false },
            confirmLabel = "知道了",
            onConfirm = { showVersionDialog = false },
        )
    }

    if (showLogoutConfirm) {
        RabbitDialog(
            title = "退出登录",
            message = "退出后需重新登录以同步你的设备与远控凭证。",
            onDismissRequest = { showLogoutConfirm = false },
            dismissLabel = "取消",
            confirmLabel = "退出登录",
            destructive = true,
            onConfirm = {
                showLogoutConfirm = false
                viewModel.logout {}
            },
        )
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    trailingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(RabbitRadius.Sm))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.primaryText,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = palette.text,
            modifier = Modifier.weight(1f),
        )
        if (trailingContent != null) {
            trailingContent()
            Spacer(modifier = Modifier.width(6.dp))
        } else if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                fontSize = 12.sp,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}
