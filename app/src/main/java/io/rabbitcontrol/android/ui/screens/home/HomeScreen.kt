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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbitcontrol.android.error.RcErrorAction
import com.rabbitcontrol.android.error.RcUiError
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.ProxyModeOption
import io.rabbitcontrol.android.core.data.rememberWallpaperImage
import io.rabbitcontrol.android.core.i18n.AppLanguageMode
import io.rabbitcontrol.android.core.i18n.AppLocaleManager
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.nativebridge.UpnpState
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.core.update.AppUpdateManager
import io.rabbitcontrol.android.core.update.AppUpdateUiState
import io.rabbitcontrol.android.core.update.CoreUpdateManager
import io.rabbitcontrol.android.core.update.CoreUpdateUiState
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitEmptyState
import io.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import io.rabbitcontrol.android.ui.components.RabbitSecondaryButton
import io.rabbitcontrol.android.ui.components.RcInlineError
import io.rabbitcontrol.android.ui.components.RabbitSectionHeader
import io.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.screens.detail.DeviceDetailScreen
import io.rabbitcontrol.android.ui.screens.settings.AboutScreen
import io.rabbitcontrol.android.ui.screens.settings.GeneralSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.HostSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.InteractionSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.NetworkSettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.SecuritySettingsScreen
import io.rabbitcontrol.android.ui.screens.settings.UpdateCenterScreen
import io.rabbitcontrol.android.ui.screens.settings.getAppVersionName
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import io.rabbitcontrol.android.ui.theme.RabbitVerticalDivider
import io.rabbitcontrol.android.ui.theme.RabbitWidthClass
import io.rabbitcontrol.android.ui.theme.rememberRabbitLayoutInfo
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private val CompactCenteredTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
fun HomeScreen(
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenDevice: (RemoteDevice) -> Unit,
    onReturnToDesktop: () -> Unit,
    onOpenProfileRoute: (String) -> Unit,
    onOpenToolFromDetail: (RemoteDevice, DeviceToolKind) -> Unit = { _, _ -> },
    onOpenPreviewCheck: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val palette = LocalRabbitPalette.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val kickedNotice by viewModel.kickedNotice.collectAsStateWithLifecycle()
    val hostState by viewModel.hostState.collectAsStateWithLifecycle()

    val updateManager: AppUpdateManager = koinInject()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val coreUpdateManager: CoreUpdateManager = koinInject()
    val coreUpdateState by coreUpdateManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        updateManager.checkForUpdate(force = false)
        coreUpdateManager.checkForUpdate(force = false)
    }

    LaunchedEffect(profile?.id, profile?.needsPassword) {
        if (profile != null && profile?.needsPassword == false) {
            viewModel.refreshDevices()
        }
    }

    hostState.pendingApproval?.let { req ->
        RabbitDialog(
            title = stringResource(R.string.home_host_req_title),
            onDismissRequest = { viewModel.respondHostApproval(req.generation, false) },
            dismissLabel = stringResource(R.string.home_host_req_reject),
            onDismissAction = { viewModel.respondHostApproval(req.generation, false) },
            confirmLabel = stringResource(R.string.home_host_req_allow),
            onConfirm = { viewModel.respondHostApproval(req.generation, true) },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_host_req_addr, req.remoteAddr), fontSize = 14.sp, color = palette.muted)
                Text(stringResource(R.string.home_host_req_stream, req.requestedRes, req.requestedFps), fontSize = 14.sp, color = palette.muted)
                Text(
                    text = if (req.passwordVerified) {
                        stringResource(R.string.home_host_req_verified)
                    } else {
                        stringResource(R.string.home_host_req_unverified)
                    },
                    fontSize = 13.sp,
                    color = if (req.passwordVerified) palette.success else palette.warning,
                )
            }
        }
    }

    if (!kickedNotice.isNullOrBlank()) {
        RabbitDialog(
            title = stringResource(R.string.home_kicked_title),
            message = kickedNotice.orEmpty(),
            onDismissRequest = viewModel::dismissKicked,
            confirmLabel = stringResource(R.string.common_got_it),
            onConfirm = viewModel::dismissKicked,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutInfo = rememberRabbitLayoutInfo(maxWidth)
        val widthClass = layoutInfo.widthClass

        if (widthClass == RabbitWidthClass.Compact) {
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
                                val tabLabel = stringResource(tab.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        Box {
                                            Icon(icon, contentDescription = tabLabel)
                                            if (tab == HomeTab.Me && (updateState.hasUpdate || coreUpdateState.hasUpdate)) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFE53935))
                                                        .border(1.dp, palette.surface, CircleShape),
                                                )
                                            }
                                        }
                                    },
                                    label = { Text(tabLabel, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
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
                            subtitle = if (activeSession.watchOnly) {
                                stringResource(R.string.home_banner_watch_running)
                            } else {
                                stringResource(R.string.home_banner_control_running)
                            },
                            onClick = onReturnToDesktop,
                        )
                    }

                    when (selectedTab) {
                        HomeTab.Devices -> DevicesTab(
                            viewModel = viewModel,
                            widthClass = widthClass,
                            onOpenAuth = onOpenAuth,
                            onOpenDevice = onOpenDevice,
                            onReturnToDesktop = onReturnToDesktop,
                            onOpenToolFromDetail = onOpenToolFromDetail,
                        )
                        HomeTab.Assist -> AssistTab(
                            viewModel = viewModel,
                            widthClass = widthClass,
                            onOpenDevice = onOpenDevice,
                        )
                        HomeTab.Me -> ProfileTab(
                            viewModel = viewModel,
                            widthClass = widthClass,
                            updateState = updateState,
                            coreUpdateState = coreUpdateState,
                            onOpenAuth = onOpenAuth,
                            onOpenProfileRoute = onOpenProfileRoute,
                            onOpenPreviewCheck = onOpenPreviewCheck,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.canvas)
                    .systemBarsPadding(),
            ) {
                NavigationRail(
                    containerColor = palette.surface,
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight(),
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HomeTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val icon = when (tab) {
                            HomeTab.Devices -> Icons.Rounded.Devices
                            HomeTab.Assist -> Icons.Rounded.SettingsRemote
                            HomeTab.Me -> Icons.Rounded.Person
                        }
                        val tabLabel = stringResource(tab.labelRes)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Box {
                                    Icon(icon, contentDescription = tabLabel)
                                    if (tab == HomeTab.Me && (updateState.hasUpdate || coreUpdateState.hasUpdate)) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE53935))
                                                .border(1.dp, palette.surface, CircleShape),
                                        )
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = tabLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = palette.primaryText,
                                selectedTextColor = palette.primaryText,
                                indicatorColor = palette.primarySoft,
                                unselectedIconColor = palette.muted,
                                unselectedTextColor = palette.muted,
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                RabbitVerticalDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    if (activeSession.status is SessionStatus.Streaming || activeSession.status is SessionStatus.Connecting) {
                        ActiveSessionBanner(
                            deviceName = activeSession.deviceName.ifBlank { activeSession.targetId },
                            subtitle = if (activeSession.watchOnly) {
                                stringResource(R.string.home_banner_watch_running)
                            } else {
                                stringResource(R.string.home_banner_control_running)
                            },
                            onClick = onReturnToDesktop,
                        )
                    }

                    when (selectedTab) {
                        HomeTab.Devices -> DevicesTab(
                            viewModel = viewModel,
                            widthClass = widthClass,
                            onOpenAuth = onOpenAuth,
                            onOpenDevice = onOpenDevice,
                            onReturnToDesktop = onReturnToDesktop,
                            onOpenToolFromDetail = onOpenToolFromDetail,
                        )
                        HomeTab.Assist -> AssistTab(
                            viewModel = viewModel,
                            widthClass = widthClass,
                            onOpenDevice = onOpenDevice,
                        )
                        HomeTab.Me -> ProfileTab(
                            viewModel = viewModel,
                            widthClass = widthClass,
                            updateState = updateState,
                            coreUpdateState = coreUpdateState,
                            onOpenAuth = onOpenAuth,
                            onOpenProfileRoute = onOpenProfileRoute,
                            onOpenPreviewCheck = onOpenPreviewCheck,
                        )
                    }
                }
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
                Text(stringResource(R.string.home_return_desktop), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicesTab(
    viewModel: HomeViewModel,
    widthClass: RabbitWidthClass,
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenDevice: (RemoteDevice) -> Unit,
    onReturnToDesktop: () -> Unit,
    onOpenToolFromDetail: (RemoteDevice, DeviceToolKind) -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val savedEmails by viewModel.savedAccountEmails.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val deviceFilter by viewModel.deviceFilter.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshingDevices.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val error by viewModel.deviceError.collectAsStateWithLifecycle()
    val wallpaperVersion by viewModel.wallpaperVersion.collectAsStateWithLifecycle()

    var showServerSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteDevice?>(null) }
    var removeTarget by remember { mutableStateOf<RemoteDevice?>(null) }
    var selectedDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val pullState = rememberPullToRefreshState()

    val controllableDevices = remember(devices) { devices.filter { it.allowRemoteControl } }
    val nonControllableDevices = remember(devices) { devices.filterNot { it.allowRemoteControl } }
    val online = remember(controllableDevices) {
        controllableDevices.filter { (it.online || it.busy) && !it.disabled }
    }
    val offline = remember(controllableDevices) {
        controllableDevices.filterNot { (it.online || it.busy) && !it.disabled }
    }
    val activeDetailDevice = remember(widthClass, profile, controllableDevices, online, selectedDeviceId) {
        if (widthClass != RabbitWidthClass.Expanded || profile == null || profile?.needsPassword == true) {
            null
        } else {
            controllableDevices.firstOrNull { it.id == selectedDeviceId }
                ?: online.firstOrNull()
                ?: controllableDevices.firstOrNull()
        }
    }

    val listContent: @Composable (Modifier) -> Unit = { listModifier ->
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
            modifier = listModifier,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = RabbitSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
                contentPadding = PaddingValues(top = RabbitSpacing.Md, bottom = RabbitSpacing.Xl),
            ) {

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.tab_devices),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp,
                            color = palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (server != null) {
                            Box {
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
                                    val countSuffix = if (servers.size > 1) {
                                        stringResource(R.string.home_server_count_suffix, servers.size)
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = "${server!!.displayName}$countSuffix",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = palette.text,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = CompactCenteredTextStyle,
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.auth_switch_server), tint = palette.muted, modifier = Modifier.size(16.dp))
                                }

                                if (widthClass != RabbitWidthClass.Compact) {
                                    DropdownMenu(
                                        expanded = showServerSheet,
                                        onDismissRequest = { showServerSheet = false },
                                        containerColor = palette.surface,
                                        tonalElevation = 0.dp,
                                        shadowElevation = RabbitElevation.Sheet,
                                        shape = RoundedCornerShape(RabbitRadius.Lg),
                                        border = BorderStroke(1.dp, palette.line),
                                        modifier = Modifier.widthIn(min = 280.dp, max = 320.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.home_switch_server_sheet),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.muted,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                        servers.forEach { srv ->
                                            val isActive = srv.id == server?.id
                                            val email = savedEmails[srv.id]
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            srv.displayName,
                                                            fontSize = 14.sp,
                                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                                                            color = if (isActive) palette.primaryText else palette.text,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            email ?: stringResource(R.string.home_not_signed_in),
                                                            fontSize = 11.sp,
                                                            color = palette.muted,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Rounded.Dns,
                                                        null,
                                                        tint = if (isActive) palette.primaryText else palette.muted,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                },
                                                trailingIcon = if (isActive) {
                                                    {
                                                        Icon(
                                                            Icons.Rounded.Check,
                                                            null,
                                                            tint = palette.primaryText,
                                                            modifier = Modifier.size(18.dp),
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                                onClick = {
                                                    showServerSheet = false
                                                    if (!isActive) {
                                                        viewModel.switchServer(srv) {
                                                            onOpenAuth(AuthStep.Login, false)
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                        HorizontalDivider(color = palette.line, thickness = 1.dp)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.home_manage_add_server),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = palette.primaryText,
                                                )
                                            },
                                            onClick = {
                                                showServerSheet = false
                                                onOpenAuth(AuthStep.Server, true)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (profile != null && profile?.needsPassword == false && devices.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeviceFilterChip(
                                label = stringResource(R.string.home_filter_controllable, controllableDevices.size),
                                selected = deviceFilter == DeviceListFilter.Controllable,
                                onClick = { viewModel.setDeviceFilter(DeviceListFilter.Controllable) },
                            )
                            DeviceFilterChip(
                                label = stringResource(R.string.home_filter_online, online.size),
                                selected = deviceFilter == DeviceListFilter.Online,
                                onClick = { viewModel.setDeviceFilter(DeviceListFilter.Online) },
                            )
                            DeviceFilterChip(
                                label = stringResource(R.string.home_filter_all, devices.size),
                                selected = deviceFilter == DeviceListFilter.AllDevices,
                                onClick = { viewModel.setDeviceFilter(DeviceListFilter.AllDevices) },
                            )
                        }
                    }
                }
            }

            if (profile == null) {
                item {
                    RabbitCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (restoring) stringResource(R.string.home_restoring_account) else stringResource(R.string.home_login_prompt_title),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.home_login_prompt_desc), color = palette.muted, fontSize = 14.sp)
                        if (!restoring) {
                            Spacer(modifier = Modifier.height(16.dp))
                            if (server == null) {
                                RabbitPrimaryButton(
                                    label = stringResource(R.string.home_connect_and_login),
                                    onClick = { onOpenAuth(AuthStep.Server, false) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    RabbitPrimaryButton(
                                        label = stringResource(R.string.auth_sign_in),
                                        onClick = { onOpenAuth(AuthStep.Login, false) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    RabbitSecondaryButton(
                                        label = stringResource(R.string.home_register_new),
                                        onClick = { onOpenAuth(AuthStep.Register, false) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                TextButton(
                                    onClick = { onOpenAuth(AuthStep.Server, true) },
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    Text(stringResource(R.string.home_switch_or_add_server), fontSize = 13.sp, color = palette.primaryText)
                                }
                            }
                        }
                    }
                }
            } else if (profile?.needsPassword == true) {
                item {
                    RabbitCard(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.home_change_pw_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(stringResource(R.string.home_change_pw_desc), color = palette.muted, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        RabbitPrimaryButton(
                            label = stringResource(R.string.auth_password_title),
                            onClick = { onOpenAuth(AuthStep.Password, false) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                item {
                    var lastUiError by remember { mutableStateOf<RcUiError?>(null) }
                    if (error != null) {
                        lastUiError = error
                    }
                    AnimatedVisibility(
                        visible = error != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        lastUiError?.let { uiErr ->
                            RcInlineError(
                                error = uiErr,
                                modifier = Modifier.fillMaxWidth(),
                                onAction = { action ->
                                    when (action) {
                                        RcErrorAction.RELOGIN -> onOpenAuth(AuthStep.Login, false)
                                        RcErrorAction.CHANGE_PASSWORD -> onOpenAuth(AuthStep.Password, false)
                                        RcErrorAction.OPEN_SETTINGS -> onOpenAuth(AuthStep.Server, true)
                                        else -> if (!refreshing) viewModel.refreshDevices()
                                    }
                                },
                            )
                        }
                    }
                }

                if (devices.isEmpty() && !refreshing && error == null) {
                    item {
                        RabbitEmptyState(
                            icon = Icons.Rounded.Devices,
                            title = stringResource(R.string.home_empty_devices_title),
                            description = stringResource(R.string.home_empty_devices_desc),
                            actionLabel = stringResource(R.string.home_refresh_devices),
                            onAction = viewModel::refreshDevices,
                        )
                    }
                }

                val showOffline = deviceFilter != DeviceListFilter.Online && offline.isNotEmpty()
                val showNonControllable = deviceFilter == DeviceListFilter.AllDevices && nonControllableDevices.isNotEmpty()
                val showGroupHeaders = (online.isNotEmpty() && showOffline) || showNonControllable

                if (online.isNotEmpty()) {
                    if (showGroupHeaders) {
                        item {
                            RabbitSectionHeader(title = stringResource(R.string.home_section_online, online.size))
                        }
                    }
                    items(online, key = { it.id }) { dev ->
                        val thumb = rememberWallpaperImage(viewModel.wallpaperCache, dev.id, wallpaperVersion, 320)
                        DeviceCard(
                            device = dev,
                            thumbnail = thumb,
                            selected = widthClass == RabbitWidthClass.Expanded && activeDetailDevice?.id == dev.id,
                            onClick = {
                                if (widthClass == RabbitWidthClass.Expanded) {
                                    selectedDeviceId = dev.id
                                } else {
                                    onOpenDevice(dev)
                                }
                            },
                            onRename = { renameTarget = dev },
                            onRemove = { removeTarget = dev },
                        )
                    }
                }

                if (showOffline) {
                    if (showGroupHeaders) {
                        item {
                            RabbitSectionHeader(title = stringResource(R.string.home_section_offline, offline.size))
                        }
                    }
                    items(offline, key = { it.id }) { dev ->
                        val thumb = rememberWallpaperImage(viewModel.wallpaperCache, dev.id, wallpaperVersion, 320)
                        DeviceCard(
                            device = dev,
                            thumbnail = thumb,
                            selected = widthClass == RabbitWidthClass.Expanded && activeDetailDevice?.id == dev.id,
                            onClick = {
                                if (widthClass == RabbitWidthClass.Expanded) {
                                    selectedDeviceId = dev.id
                                } else {
                                    onOpenDevice(dev)
                                }
                            },
                            onRename = { renameTarget = dev },
                            onRemove = { removeTarget = dev },
                        )
                    }
                }

                if (showNonControllable) {
                    item {
                        RabbitSectionHeader(title = stringResource(R.string.home_section_non_controllable, nonControllableDevices.size))
                    }
                    items(nonControllableDevices, key = { "nc_${it.id}" }) { dev ->
                        val thumb = rememberWallpaperImage(viewModel.wallpaperCache, dev.id, wallpaperVersion, 320)
                        DeviceCard(
                            device = dev,
                            thumbnail = thumb,
                            selected = false,
                            onClick = {
                                RabbitToast.info(AppStrings.get(R.string.home_remote_control_off_hint, "该设备未开启被控端，不支持远控连接"))
                            },
                            onRename = { renameTarget = dev },
                            onRemove = { removeTarget = dev },
                        )
                    }
                }
            }
        }
    }
    }

    when (widthClass) {
        RabbitWidthClass.Expanded -> {
            if (activeDetailDevice == null && (profile == null || profile?.needsPassword == true || devices.isEmpty())) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    listContent(
                        Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize(),
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    listContent(
                        Modifier
                            .width(380.dp)
                            .fillMaxHeight(),
                    )
                    RabbitVerticalDivider()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val detailDev = activeDetailDevice
                        if (detailDev != null) {
                            DeviceDetailScreen(
                                device = detailDev,
                                onBack = {},
                                onEnterDesktop = { _ -> onReturnToDesktop() },
                                onOpenTool = { kind -> onOpenToolFromDetail(detailDev, kind) },
                                embedded = true,
                            )
                        } else {
                            RabbitEmptyState(
                                icon = Icons.Rounded.Devices,
                                title = stringResource(R.string.home_empty_devices_title),
                                description = stringResource(R.string.home_empty_devices_desc),
                                modifier = Modifier.padding(RabbitSpacing.Xxl),
                            )
                        }
                    }
                }
            }
        }
        RabbitWidthClass.Medium -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                listContent(
                    Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize(),
                )
            }
        }
        RabbitWidthClass.Compact -> {
            listContent(Modifier.fillMaxSize())
        }
    }

    if (showServerSheet && widthClass == RabbitWidthClass.Compact) {
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
                Text(stringResource(R.string.home_switch_server_sheet), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
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
                            Text(email ?: stringResource(R.string.home_not_signed_in), fontSize = 12.sp, color = palette.muted)
                        }
                        if (isActive) {
                            Icon(Icons.Rounded.Check, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                RabbitSecondaryButton(
                    label = stringResource(R.string.home_manage_add_server),
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

    val currentRename = renameTarget
    if (currentRename != null) {
        var aliasDraft by remember(currentRename.id) { mutableStateOf(currentRename.name) }
        val renamedMsg = stringResource(R.string.device_rename_success)
        RabbitDialog(
            title = stringResource(R.string.device_rename_title),
            onDismissRequest = { renameTarget = null },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.common_save),
            confirmEnabled = aliasDraft.trim().isNotEmpty(),
            onConfirm = {
                val target = currentRename
                renameTarget = null
                viewModel.renameDevice(target.id, aliasDraft.trim()) { ok, err ->
                    if (ok) RabbitToast.success(renamedMsg)
                    else if (!err.isNullOrBlank()) RabbitToast.error(err)
                }
            },
        ) {
            RabbitTextField(
                value = aliasDraft,
                onValueChange = { aliasDraft = it },
                placeholder = stringResource(R.string.device_rename_hint),
            )
        }
    }

    val currentRemove = removeTarget
    if (currentRemove != null) {
        val removedMsg = stringResource(R.string.detail_unbind_success)
        RabbitDialog(
            title = stringResource(R.string.detail_unbind_title),
            message = stringResource(R.string.detail_unbind_msg, currentRemove.name),
            onDismissRequest = { removeTarget = null },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.detail_unbind_confirm),
            destructive = true,
            onConfirm = {
                val target = currentRemove
                removeTarget = null
                viewModel.removeDevice(target.id) { ok, err ->
                    if (ok) RabbitToast.success(removedMsg)
                    else if (!err.isNullOrBlank()) RabbitToast.error(err)
                }
            },
        )
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

private fun formatDeviceOs(raw: String): String {
    val normalized = raw.trim().lowercase()
    return when {
        normalized.isEmpty() -> AppStrings.get(R.string.home_device_default_name, "桌面设备")
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
private fun DeviceCard(
    device: RemoteDevice,
    thumbnail: ImageBitmap?,
    selected: Boolean = false,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    var menuExpanded by remember { mutableStateOf(false) }
    val controllable = device.allowRemoteControl
    val online = controllable && (device.online || device.busy) && !device.disabled
    val (statusLabel, dotColor) = when {
        !controllable -> stringResource(R.string.home_remote_control_off) to Color(0xFF9E9E9E)
        device.disabled -> stringResource(R.string.home_status_disabled) to Color(0xFF9E9E9E)
        device.busy -> stringResource(R.string.home_status_busy) to Color(0xFF60A5FA)
        device.online -> stringResource(R.string.home_status_online) to Color(0xFF34D399)
        else -> stringResource(R.string.home_status_offline) to Color(0xFF9E9E9E)
    }
    RabbitCard(
        onClick = onClick,
        borderColor = if (selected) palette.primary else palette.line,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (controllable) 1f else 0.78f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(98.dp),
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
                        bitmap = thumbnail,
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
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        style = CompactCenteredTextStyle,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    device.name,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
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
                        lineHeight = 16.sp,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = CompactCenteredTextStyle,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!controllable) {
                        Text(
                            text = stringResource(R.string.home_remote_control_off),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = palette.muted.copy(alpha = 0.85f),
                            maxLines = 1,
                            style = CompactCenteredTextStyle,
                        )
                    } else if (online) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = palette.primaryText,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (device.busy) stringResource(R.string.home_btn_resume_control) else stringResource(R.string.home_btn_connect_desktop),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.text,
                                maxLines = 1,
                                style = CompactCenteredTextStyle,
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (device.disabled) stringResource(R.string.home_status_disabled) else stringResource(R.string.home_btn_offline),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = palette.muted.copy(alpha = 0.8f),
                                maxLines = 1,
                                style = CompactCenteredTextStyle,
                            )
                            if (!device.disabled && device.wolCapable) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.primarySoft)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = palette.primaryText,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.home_wol_ready),
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.primaryText,
                                        maxLines = 1,
                                        style = CompactCenteredTextStyle,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { menuExpanded = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.home_device_more_cd),
                                tint = palette.muted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.device_rename_title)) },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_unbind), color = palette.danger) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = palette.danger, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    onRemove()
                                },
                            )
                        }
                    }
                    if (controllable) {
                        Spacer(modifier = Modifier.width(4.dp))
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
}

private enum class AssistQuickTab(
    val labelRes: Int,
    private val defaultLabel: String,
) {
    Recent(R.string.home_assist_recent, "最近连接"),
    Favorites(R.string.home_assist_favorites, "我的收藏");

    val label: String
        get() = AppStrings.get(labelRes, defaultLabel)
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
    widthClass: RabbitWidthClass,
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

    val inputCard: @Composable () -> Unit = {
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
                    Text(stringResource(R.string.home_assist_card_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    Text(stringResource(R.string.home_assist_card_sub), fontSize = 12.sp, color = palette.muted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            RabbitTextField(
                value = input,
                onValueChange = { viewModel.updateAssistInput(it) },
                label = stringResource(R.string.home_assist_code_label),
                placeholder = stringResource(R.string.home_assist_code_hint),
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
                            onClick = { viewModel.updateAssistInput("") },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.common_clear),
                                tint = palette.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    null
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            val invalidCodeMsg = stringResource(R.string.home_assist_invalid_code)
            val partnerOs = stringResource(R.string.home_assist_partner_os)
            RabbitPrimaryButton(
                label = stringResource(R.string.home_assist_start),
                enabled = input.isNotBlank(),
                onClick = {
                    val parsed = viewModel.parseDeviceCode(input)
                    if (parsed == null) {
                        RabbitToast.error(invalidCodeMsg)
                        return@RabbitPrimaryButton
                    }
                    viewModel.recordTarget(parsed)
                    val matched = devices.firstOrNull { it.id == parsed } ?: RemoteDevice(
                        id = parsed,
                        name = parsed.chunked(3).joinToString(" "),
                        os = partnerOs,
                        online = true,
                        busy = false,
                        disabled = false,
                    )
                    onOpenDevice(matched)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val quickFillSection: @Composable () -> Unit = {
        val list = if (quickTab == AssistQuickTab.Recent) validRecent else validFavorites
        Column(verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md)) {
            RabbitSectionHeader(title = stringResource(R.string.home_assist_quick_fill))
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
                        text = if (quickTab == AssistQuickTab.Recent) {
                            stringResource(R.string.home_assist_empty_recent)
                        } else {
                            stringResource(R.string.home_assist_empty_fav)
                        },
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
                                .clickable { viewModel.updateAssistInput(item) }
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
                                    contentDescription = stringResource(R.string.home_cd_favorite),
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = if (widthClass == RabbitWidthClass.Expanded) 940.dp else 540.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Lg),
        ) {
            Text(stringResource(R.string.tab_assist), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            if (widthClass == RabbitWidthClass.Expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(RabbitSpacing.Xl),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.weight(1f)) { inputCard() }
                    Box(modifier = Modifier.weight(1f)) { quickFillSection() }
                }
            } else {
                inputCard()
                quickFillSection()
            }
        }
    }
}

@Composable
private fun ProfileTab(
    viewModel: HomeViewModel,
    widthClass: RabbitWidthClass,
    updateState: AppUpdateUiState,
    coreUpdateState: CoreUpdateUiState,
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenProfileRoute: (String) -> Unit,
    onOpenPreviewCheck: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val context = LocalContext.current
    val versionName = remember { getAppVersionName(context) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val signal by viewModel.signalStatus.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val proxyConfig by viewModel.proxyConfig.collectAsStateWithLifecycle()
    val hostState by viewModel.hostState.collectAsStateWithLifecycle()
    val upnpStatus by viewModel.upnpStatus.collectAsStateWithLifecycle()
    val appLockState by viewModel.appLockState.collectAsStateWithLifecycle()
    val languageMode by AppLocaleManager.mode.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshUpnpStatus()
    }

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var selectedSettingsRoute by rememberSaveable { mutableStateOf("settings/interaction") }

    val isExpanded = widthClass == RabbitWidthClass.Expanded
    fun openSettingRoute(route: String) {
        if (isExpanded) {
            selectedSettingsRoute = route
        } else {
            onOpenProfileRoute(route)
        }
    }

    val currentLangLabel = when (languageMode) {
        AppLanguageMode.System -> stringResource(R.string.lang_follow_system)
        AppLanguageMode.SimplifiedChinese -> stringResource(R.string.lang_simplified_chinese)
        AppLanguageMode.TraditionalChinese -> stringResource(R.string.lang_traditional_chinese)
        AppLanguageMode.English -> stringResource(R.string.lang_english)
    }

    val menuColumn: @Composable (Modifier) -> Unit = { colModifier ->
        Column(
            modifier = colModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
        ) {
        Text(stringResource(R.string.tab_me), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.text)

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
                        profile?.name?.ifBlank { profile?.email } ?: stringResource(R.string.home_not_signed_in),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                    Text(
                        profile?.email ?: stringResource(R.string.home_profile_signin_sub),
                        fontSize = 12.sp,
                        color = palette.muted,
                    )
                }
                if (profile == null) {
                    TextButton(onClick = { onOpenAuth(AuthStep.Login, false) }) {
                        Text(stringResource(R.string.auth_sign_in), color = palette.primaryText)
                    }
                    TextButton(onClick = { onOpenAuth(AuthStep.Register, false) }) {
                        Text(stringResource(R.string.auth_sign_up), color = palette.primaryText)
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
                        "connected" -> stringResource(R.string.home_signal_online, signal.deviceIdFormatted ?: "-")
                        "connecting", "reconnecting" -> stringResource(R.string.home_signal_connecting)
                        "error" -> stringResource(
                            R.string.home_signal_error,
                            signal.lastError ?: stringResource(R.string.home_signal_reconnecting),
                        )
                        else -> stringResource(R.string.home_signal_idle)
                    }
                    Text(sigText, fontSize = 12.sp, color = palette.muted)
                }
            }
        }

        // Card 2: Connections & Services
        RabbitCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = RabbitSpacing.Lg, vertical = 4.dp),
        ) {
            val srvTrailing = if (server != null) {
                if (servers.size > 1) "${server?.displayName} (${servers.size})" else server?.displayName.orEmpty()
            } else {
                stringResource(R.string.home_menu_not_configured)
            }
            ProfileMenuRow(
                icon = Icons.Rounded.Dns,
                title = stringResource(R.string.home_menu_servers),
                trailingText = srvTrailing,
                onClick = { onOpenAuth(AuthStep.Server, true) },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            val (hostLabel, hostTone) = when {
                hostState.connectedPeer != null -> stringResource(R.string.home_host_controlling) to StatusTone.Primary
                hostState.enabled -> stringResource(R.string.home_host_waiting) to StatusTone.Success
                hostState.starting -> stringResource(R.string.home_host_starting) to StatusTone.Warning
                else -> stringResource(R.string.home_host_off) to StatusTone.Muted
            }
            ProfileMenuRow(
                icon = Icons.Rounded.ScreenShare,
                title = stringResource(R.string.home_menu_host_mode),
                selected = isExpanded && selectedSettingsRoute == "settings/host",
                trailingContent = { RabbitStatusPill(label = hostLabel, tone = hostTone) },
                onClick = { openSettingRoute("settings/host") },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            val upnpSummaryShort = when {
                !upnpStatus.enabled || upnpStatus.state == UpnpState.Disabled -> null
                upnpStatus.state == UpnpState.Mapped -> "UPnP ${stringResource(R.string.upnp_badge_mapped_short)}"
                upnpStatus.state == UpnpState.Discovering -> "UPnP ${stringResource(R.string.upnp_badge_discovering_short)}"
                else -> "UPnP ${stringResource(R.string.upnp_badge_unavailable_short)}"
            }
            val proxyTag = stringResource(R.string.network_summary_proxy_tag)
            val networkSummary = buildList {
                add(prefs.connectionMode.label)
                if (upnpSummaryShort != null) add(upnpSummaryShort)
                if (proxyConfig.mode != ProxyModeOption.None) add(proxyTag)
            }.joinToString(" · ")
            ProfileMenuRow(
                icon = Icons.Rounded.Public,
                title = stringResource(R.string.home_menu_network),
                selected = isExpanded && selectedSettingsRoute == "settings/network",
                trailingText = networkSummary,
                onClick = { openSettingRoute("settings/network") },
            )
        }

        // Card 3: Security & Preferences
        RabbitCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = RabbitSpacing.Lg, vertical = 4.dp),
        ) {
            ProfileMenuRow(
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.home_menu_security),
                selected = isExpanded && selectedSettingsRoute == "settings/security",
                trailingContent = {
                    RabbitStatusPill(
                        label = stringResource(if (appLockState.enabled) R.string.sec_lock_on else R.string.sec_lock_off),
                        tone = if (appLockState.enabled) StatusTone.Success else StatusTone.Muted,
                    )
                },
                onClick = { openSettingRoute("settings/security") },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.home_menu_interaction),
                selected = isExpanded && selectedSettingsRoute == "settings/interaction",
                trailingText = prefs.touchMode.label,
                onClick = { openSettingRoute("settings/interaction") },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.Settings,
                title = stringResource(R.string.home_menu_general),
                selected = isExpanded && selectedSettingsRoute == "settings/general",
                trailingText = "${prefs.themeMode.label} · $currentLangLabel",
                onClick = { openSettingRoute("settings/general") },
            )
        }

        // Card 4: Versions & About
        RabbitCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = RabbitSpacing.Lg, vertical = 4.dp),
        ) {
            val newVer = updateState.availableRelease?.versionName
            val loadedCore = coreUpdateState.loadedCore
            val newCoreVer = coreUpdateState.availableUpdate?.manifest?.coreVersion
            val pendingRestart = loadedCore.pendingRestartVersion != null
            val hasAnyUpdateBadge = pendingRestart || newVer != null || newCoreVer != null

            ProfileMenuRow(
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.home_menu_updates),
                selected = isExpanded && selectedSettingsRoute == "settings/updates",
                trailingText = if (!hasAnyUpdateBadge) "v$versionName" else null,
                trailingContent = if (hasAnyUpdateBadge) {
                    {
                        val badgeLabel = when {
                            pendingRestart -> stringResource(R.string.home_badge_pending_restart)
                            newVer != null && newCoreVer != null -> stringResource(R.string.home_badge_updates_available)
                            newVer != null -> stringResource(R.string.home_badge_new_app, newVer)
                            else -> stringResource(R.string.home_badge_new_core, newCoreVer.orEmpty())
                        }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(RabbitRadius.Pill))
                                .background(if (pendingRestart) palette.primarySoft else palette.dangerSoft)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (pendingRestart) palette.primary else Color(0xFFE53935)),
                            )
                            Text(
                                text = badgeLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pendingRestart) palette.primaryText else palette.danger,
                                maxLines = 1,
                            )
                        }
                    }
                } else {
                    null
                },
                onClick = { openSettingRoute("settings/updates") },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.home_menu_about),
                selected = isExpanded && selectedSettingsRoute == "settings/about",
                onClick = { openSettingRoute("settings/about") },
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
                        text = stringResource(R.string.home_logout),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.danger,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        }
    }

    when (widthClass) {
        RabbitWidthClass.Expanded -> {
            Row(modifier = Modifier.fillMaxSize()) {
                menuColumn(
                    Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                )
                RabbitVerticalDivider()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    when (selectedSettingsRoute) {
                        "settings/host" -> HostSettingsScreen(onBack = {}, onOpenAuth = onOpenAuth, embedded = true)
                        "settings/network" -> NetworkSettingsScreen(onBack = {}, embedded = true)
                        "settings/security" -> SecuritySettingsScreen(onBack = {}, embedded = true)
                        "settings/general" -> GeneralSettingsScreen(onBack = {}, embedded = true)
                        "settings/updates" -> UpdateCenterScreen(onBack = {}, embedded = true)
                        "settings/about" -> AboutScreen(onBack = {}, embedded = true)
                        else -> InteractionSettingsScreen(onBack = {}, onOpenPreviewCheck = onOpenPreviewCheck, embedded = true)
                    }
                }
            }
        }
        RabbitWidthClass.Medium -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                menuColumn(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize(),
                )
            }
        }
        RabbitWidthClass.Compact -> {
            menuColumn(Modifier.fillMaxSize())
        }
    }

    if (showLogoutConfirm) {
        RabbitDialog(
            title = stringResource(R.string.home_logout),
            message = stringResource(R.string.home_logout_confirm_msg),
            onDismissRequest = { showLogoutConfirm = false },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.home_logout),
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
    selected: Boolean = false,
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
            .background(if (selected) palette.primarySoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 8.dp else 0.dp, vertical = 8.dp),
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
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) palette.primaryText else palette.text,
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
