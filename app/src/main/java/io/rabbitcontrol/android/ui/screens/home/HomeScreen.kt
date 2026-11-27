package io.rabbitcontrol.android.ui.screens.home

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Memory
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
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AppThemeMode
import io.rabbitcontrol.android.core.data.rememberWallpaperImage
import io.rabbitcontrol.android.core.i18n.AppLanguageMode
import io.rabbitcontrol.android.core.i18n.AppLocaleManager
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.core.update.AppUpdateManager
import io.rabbitcontrol.android.core.update.AppUpdateUiState
import io.rabbitcontrol.android.core.update.CoreDownloadState
import io.rabbitcontrol.android.core.update.CoreUpdateManager
import io.rabbitcontrol.android.core.update.CoreUpdateUiState
import io.rabbitcontrol.android.core.update.UpdateDownloadState
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RabbitEmptyState
import io.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import io.rabbitcontrol.android.ui.components.RabbitSecondaryButton
import io.rabbitcontrol.android.ui.components.RabbitSectionHeader
import io.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.screens.settings.AppUpdateDialog
import io.rabbitcontrol.android.ui.screens.settings.CoreUpdateDialog
import io.rabbitcontrol.android.ui.screens.settings.getAppVersionName
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
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
                HomeTab.Devices -> DevicesTab(viewModel = viewModel, onOpenAuth = onOpenAuth, onOpenDevice = onOpenDevice)
                HomeTab.Assist -> AssistTab(viewModel = viewModel, onOpenDevice = onOpenDevice)
                HomeTab.Me -> ProfileTab(
                    viewModel = viewModel,
                    updateManager = updateManager,
                    updateState = updateState,
                    coreUpdateManager = coreUpdateManager,
                    coreUpdateState = coreUpdateState,
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
                Text(stringResource(R.string.home_return_desktop), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
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
                            stringResource(R.string.tab_devices),
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
                        }
                    }

                    if (profile != null && profile?.needsPassword == false && devices.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DeviceFilterChip(
                                label = stringResource(R.string.home_filter_all, devices.size),
                                selected = !onlineOnly,
                                onClick = { if (onlineOnly) viewModel.toggleOnlineOnly() },
                            )
                            DeviceFilterChip(
                                label = stringResource(R.string.home_filter_online, online.size),
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
                    var lastErrorText by remember { mutableStateOf("") }
                    if (!error.isNullOrBlank()) {
                        lastErrorText = error.orEmpty()
                    }
                    AnimatedVisibility(
                        visible = !error.isNullOrBlank(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        val currentConfig = LocalConfiguration.current
                        val (errTitle, errSub) = remember(lastErrorText, currentConfig) {
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
                                        text = stringResource(R.string.common_retry),
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
                            title = stringResource(R.string.home_empty_devices_title),
                            description = stringResource(R.string.home_empty_devices_desc),
                            actionLabel = stringResource(R.string.home_refresh_devices),
                            onAction = viewModel::refreshDevices,
                        )
                    }
                }

                val showGroupHeaders = online.isNotEmpty() && !onlineOnly && offline.isNotEmpty()

                if (online.isNotEmpty()) {
                    if (showGroupHeaders) {
                        item {
                            RabbitSectionHeader(title = stringResource(R.string.home_section_online, online.size))
                        }
                    }
                    items(online, key = { it.id }) { dev ->
                        val thumb = rememberWallpaperImage(viewModel.wallpaperCache, dev.id, wallpaperVersion, 320)
                        DeviceCard(device = dev, thumbnail = thumb, onClick = { onOpenDevice(dev) })
                    }
                }

                if (!onlineOnly && offline.isNotEmpty()) {
                    if (showGroupHeaders) {
                        item {
                            RabbitSectionHeader(title = stringResource(R.string.home_section_offline, offline.size))
                        }
                    }
                    items(offline, key = { it.id }) { dev ->
                        val thumb = rememberWallpaperImage(viewModel.wallpaperCache, dev.id, wallpaperVersion, 320)
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
        AppStrings.get(R.string.home_err_cannot_connect, "无法连接服务器") to
            AppStrings.get(R.string.home_err_network_hint, "网络波动或代理异常，连接恢复后将自动消失")
    } else {
        AppStrings.get(R.string.home_err_refresh_failed, "刷新设备失败") to raw.trim()
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
private fun DeviceCard(device: RemoteDevice, thumbnail: ImageBitmap?, onClick: () -> Unit) {
    val palette = LocalRabbitPalette.current
    val online = (device.online || device.busy) && !device.disabled
    val (statusLabel, dotColor) = when {
        device.disabled -> stringResource(R.string.home_status_disabled) to Color(0xFF9E9E9E)
        device.busy -> stringResource(R.string.home_status_busy) to Color(0xFF60A5FA)
        device.online -> stringResource(R.string.home_status_online) to Color(0xFF34D399)
        else -> stringResource(R.string.home_status_offline) to Color(0xFF9E9E9E)
    }
    RabbitCard(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth(),
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
                    if (online) {
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
                        Text(
                            text = if (device.disabled) stringResource(R.string.home_status_disabled) else stringResource(R.string.home_btn_offline),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = palette.muted.copy(alpha = 0.8f),
                            maxLines = 1,
                            style = CompactCenteredTextStyle,
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
            Text(stringResource(R.string.tab_assist), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = palette.text)

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

            // Inline Quick Connect (Recent & Favorites)
            val list = if (quickTab == AssistQuickTab.Recent) validRecent else validFavorites
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab(
    viewModel: HomeViewModel,
    updateManager: AppUpdateManager,
    updateState: AppUpdateUiState,
    coreUpdateManager: CoreUpdateManager,
    coreUpdateState: CoreUpdateUiState,
    onOpenAuth: (AuthStep, Boolean) -> Unit,
    onOpenPreviewCheck: () -> Unit,
    onOpenProfileRoute: (String) -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE")
    val currentConfig = LocalConfiguration.current
    val versionName = remember { getAppVersionName(context) }

    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val server by viewModel.server.collectAsStateWithLifecycle()
    val signal by viewModel.signalStatus.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val hostState by viewModel.hostState.collectAsStateWithLifecycle()
    val languageMode by AppLocaleManager.mode.collectAsStateWithLifecycle()

    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var showCoreDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val currentLangLabel = when (languageMode) {
        AppLanguageMode.System -> stringResource(R.string.lang_follow_system)
        AppLanguageMode.SimplifiedChinese -> stringResource(R.string.lang_simplified_chinese)
        AppLanguageMode.TraditionalChinese -> stringResource(R.string.lang_traditional_chinese)
        AppLanguageMode.English -> stringResource(R.string.lang_english)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        // Card 2: Grouped Settings & Features
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
                trailingContent = { RabbitStatusPill(label = hostLabel, tone = hostTone) },
                onClick = { onOpenProfileRoute("settings/host") },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.Tune,
                title = stringResource(R.string.home_menu_interaction),
                trailingText = prefs.touchMode.label,
                onClick = { onOpenProfileRoute("settings/interaction") },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.DarkMode,
                title = stringResource(R.string.home_menu_theme),
                trailingText = prefs.themeMode.label,
                onClick = { showThemeSheet = true },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.home_menu_language),
                trailingText = currentLangLabel,
                onClick = { showLanguageSheet = true },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)

            ProfileMenuRow(
                icon = Icons.Rounded.PlayArrow,
                title = stringResource(R.string.home_menu_preview_check),
                trailingText = stringResource(R.string.home_menu_preview_trailing),
                onClick = onOpenPreviewCheck,
            )
        }

        // Card 3: Updates & About
        RabbitCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = RabbitSpacing.Lg, vertical = 4.dp),
        ) {
            val newVer = updateState.availableRelease?.versionName
            ProfileMenuRow(
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.home_menu_app_update),
                trailingText = if (newVer == null) "v$versionName" else null,
                trailingContent = if (newVer != null) {
                    {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(RabbitRadius.Pill))
                                .background(palette.dangerSoft)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935)),
                            )
                            Text(
                                text = stringResource(R.string.home_badge_new_app, newVer),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.danger,
                                maxLines = 1,
                            )
                        }
                    }
                } else {
                    null
                },
                onClick = {
                    showVersionDialog = true
                    if (!updateState.hasUpdate &&
                        updateState.downloadState !is UpdateDownloadState.Downloading &&
                        updateState.downloadState !is UpdateDownloadState.ReadyToInstall
                    ) {
                        updateManager.checkForUpdate(force = true)
                    }
                },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)
            val newCoreVer = coreUpdateState.availableUpdate?.manifest?.coreVersion
            val loadedCore = coreUpdateState.loadedCore
            ProfileMenuRow(
                icon = Icons.Rounded.Memory,
                title = stringResource(R.string.home_menu_core_update),
                trailingText = if (newCoreVer == null && loadedCore.pendingRestartVersion == null) {
                    "v${loadedCore.version} · ${
                        if (loadedCore.isDynamic) {
                            stringResource(R.string.home_core_dynamic)
                        } else {
                            stringResource(R.string.home_core_builtin)
                        }
                    }"
                } else {
                    null
                },
                trailingContent = if (newCoreVer != null || loadedCore.pendingRestartVersion != null) {
                    {
                        val isPending = loadedCore.pendingRestartVersion != null
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(RabbitRadius.Pill))
                                .background(if (isPending) palette.primarySoft else palette.dangerSoft)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isPending) palette.primary else Color(0xFFE53935)),
                            )
                            Text(
                                text = if (isPending) {
                                    stringResource(R.string.home_badge_pending_restart)
                                } else {
                                    stringResource(R.string.home_badge_new_core, newCoreVer.orEmpty())
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPending) palette.primaryText else palette.danger,
                                maxLines = 1,
                            )
                        }
                    }
                } else {
                    null
                },
                onClick = {
                    showCoreDialog = true
                    if (!coreUpdateState.hasUpdate &&
                        coreUpdateState.downloadState !is CoreDownloadState.Downloading &&
                        coreUpdateState.downloadState !is CoreDownloadState.Verifying &&
                        coreUpdateState.downloadState !is CoreDownloadState.ReadyToRestart
                    ) {
                        coreUpdateManager.checkForUpdate(force = true)
                    }
                },
            )
            HorizontalDivider(color = palette.line, thickness = 1.dp)
            ProfileMenuRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.home_menu_about),
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
                Text(stringResource(R.string.home_menu_theme), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(stringResource(R.string.home_theme_sheet_sub), fontSize = 12.sp, color = palette.muted)
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

    if (showLanguageSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLanguageSheet = false },
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
                Text(stringResource(R.string.home_menu_language), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Text(stringResource(R.string.home_language_sheet_sub), fontSize = 12.sp, color = palette.muted)
                Spacer(modifier = Modifier.height(2.dp))
                AppLanguageMode.entries.forEach { lang ->
                    val selected = languageMode == lang
                    val label = when (lang) {
                        AppLanguageMode.System -> stringResource(R.string.lang_follow_system)
                        AppLanguageMode.SimplifiedChinese -> stringResource(R.string.lang_simplified_chinese)
                        AppLanguageMode.TraditionalChinese -> stringResource(R.string.lang_traditional_chinese)
                        AppLanguageMode.English -> stringResource(R.string.lang_english)
                    }
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
                                showLanguageSheet = false
                                AppLocaleManager.setLanguageMode(context, lang)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
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
        AppUpdateDialog(
            state = updateState,
            onRetryCheck = { updateManager.checkForUpdate(force = true) },
            onStartDownload = { rel -> updateManager.startDownload(rel) },
            onCancelDownload = { updateManager.cancelDownload() },
            onInstall = { path -> updateManager.triggerInstall(path) },
            onOpenReleasePage = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
            onDismiss = { showVersionDialog = false },
        )
    }

    if (showCoreDialog) {
        CoreUpdateDialog(
            state = coreUpdateState,
            onRetryCheck = { coreUpdateManager.checkForUpdate(force = true) },
            onStartDownload = { candidate -> coreUpdateManager.startDownload(candidate) },
            onCancelDownload = { coreUpdateManager.cancelDownload() },
            onRevertToBuiltIn = { coreUpdateManager.revertToBuiltIn() },
            onRestartApp = { coreUpdateManager.restartApp() },
            onSaveMirrorPrefix = { prefix -> coreUpdateManager.setMirrorPrefix(prefix) },
            onOpenReleasePage = { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
            onDismiss = { showCoreDialog = false },
        )
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
