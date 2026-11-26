package io.rabbitcontrol.android.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.PortraitLayoutMode
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.host.HostManager
import io.rabbitcontrol.android.core.update.AppUpdateManager
import io.rabbitcontrol.android.core.update.AppUpdateUiState
import io.rabbitcontrol.android.core.update.CoreCheckStatus
import io.rabbitcontrol.android.core.update.CoreDownloadState
import io.rabbitcontrol.android.core.update.CoreIncompatibleNotice
import io.rabbitcontrol.android.core.update.CoreReleaseCandidate
import io.rabbitcontrol.android.core.update.CoreUpdateManager
import io.rabbitcontrol.android.core.update.CoreUpdateUiState
import io.rabbitcontrol.android.core.update.ReleaseInfo
import io.rabbitcontrol.android.core.update.UpdateCheckStatus
import io.rabbitcontrol.android.core.update.UpdateDownloadState
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitSwitchRow
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import org.koin.compose.koinInject

fun getAppVersionName(context: Context): String {
    return runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.2.0"
}

@Composable
fun HostSettingsScreen(
    onBack: () -> Unit,
    onOpenAuth: (AuthStep, Boolean) -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val accountRepo: AccountRepository = koinInject()
    val hostManager: HostManager = koinInject()

    val profile by accountRepo.profile.collectAsStateWithLifecycle()
    val signal by accountRepo.signalStatus.collectAsStateWithLifecycle()
    val hostState by hostManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(hostState.lastError) {
        val err = hostState.lastError
        if (!err.isNullOrBlank()) {
            RabbitToast.error(err)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hostManager.refreshAccessibilityState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK && res.data != null) {
            hostManager.enableHostWithProjection(res.resultCode, res.data!!)
        }
    }

    val launchScreenCaptureConsent: () -> Unit = {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        launchScreenCaptureConsent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding(),
    ) {
        RabbitTopBar(title = "手机被控端", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
        ) {
            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ScreenShare, null, tint = palette.primaryText, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("被控服务状态", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text, modifier = Modifier.weight(1f))
                    val (pillText, pillTone) = when {
                        hostState.connectedPeer != null -> "远控中" to StatusTone.Primary
                        hostState.enabled -> "等待连接" to StatusTone.Success
                        hostState.starting -> "启动中" to StatusTone.Warning
                        else -> "已关闭" to StatusTone.Muted
                    }
                    RabbitStatusPill(label = pillText, tone = pillTone)
                }

                Spacer(modifier = Modifier.height(6.dp))
                RabbitSwitchRow(
                    title = "允许本机被远控",
                    subtitle = if (profile == null) {
                        "需先登录账号以绑定设备码与安全证书"
                    } else if (hostState.enabled) {
                        "同账号电脑可直接在设备列表发起控制${if (hostState.ownerSelfControlReady) "（免密直通已就绪）" else ""}"
                    } else {
                        "开启后将请求屏幕共享权限并允许电脑控制此手机"
                    },
                    checked = hostState.enabled || hostState.starting,
                    onCheckedChange = { wantEnable ->
                        if (wantEnable) {
                            if (profile == null || profile?.needsPassword == true) {
                                onOpenAuth(if (profile == null) AuthStep.Login else AuthStep.Password, false)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                launchScreenCaptureConsent()
                            }
                        } else {
                            hostManager.stopHost()
                        }
                    },
                )

                if (hostState.enabled) {
                    if (hostState.connectedPeer != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .background(palette.primarySoft)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (hostState.connectedIsOwner) {
                                    "本人设备 ${hostState.connectedPeer} 正在控制"
                                } else {
                                    "协助端 ${hostState.connectedPeer} 正在控制"
                                },
                                fontSize = 12.sp,
                                color = palette.primaryText,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = hostManager::disconnectClient,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text("断开", color = palette.danger, fontSize = 12.sp)
                            }
                        }
                    } else if (!signal.deviceIdFormatted.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "本机识别码：${signal.deviceIdFormatted}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = palette.muted,
                        )
                    }
                }

                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow(
                    title = "允许他人远程协助",
                    subtitle = "关闭时仅允许同账号本人设备控制；开启后生成临时验证码并需机主确认",
                    checked = hostState.allowRemoteAssist,
                    onCheckedChange = hostManager::setAllowRemoteAssist,
                )

                if (hostState.enabled && hostState.allowRemoteAssist && !hostState.tempCode.isNullOrBlank()) {
                    val code = hostState.tempCode!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .background(palette.field)
                            .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Sm))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("临时验证码", fontSize = 11.sp, color = palette.muted)
                            Text(
                                text = code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryText,
                            )
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(code))
                            RabbitToast.success("已复制临时验证码")
                        }) {
                            Icon(Icons.Rounded.ContentCopy, "复制验证码", tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            hostManager.rotateTempCode()
                        }) {
                            Icon(Icons.Rounded.Refresh, "刷新验证码", tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                HorizontalDivider(color = palette.line, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(RabbitRadius.Sm))
                        .clickable {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.AccessibilityNew,
                        null,
                        tint = if (hostState.accessibilityEnabled) palette.success else palette.warning,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("无障碍触控权限", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
                        Text(
                            if (hostState.accessibilityEnabled) {
                                "已授权 · 支持远程点击、滑动与返回手势"
                            } else {
                                "未开启 · 仅可观看屏幕，点此去系统设置授权"
                            },
                            fontSize = 12.sp,
                            color = if (hostState.accessibilityEnabled) palette.success else palette.warning,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
                }
            }
        }
    }
}

@Composable
fun InteractionSettingsScreen(
    onBack: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val prefsStore: InteractionPrefsStore = koinInject()
    val prefs by prefsStore.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding(),
    ) {
        RabbitTopBar(title = "远控默认设置", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
        ) {
            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Text("操控与屏幕", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Spacer(modifier = Modifier.height(14.dp))

                Text("默认交互模式", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.text)
                Spacer(modifier = Modifier.height(6.dp))
                RabbitSegmentedControl(
                    options = TouchInputMode.entries,
                    selected = prefs.touchMode,
                    labelProvider = { it.label },
                    onSelect = { mode -> prefsStore.update { it.copy(touchMode = mode) } },
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("默认屏幕方向", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.text)
                Spacer(modifier = Modifier.height(6.dp))
                RabbitSegmentedControl(
                    options = OrientationPreference.entries,
                    selected = prefs.orientationPref,
                    labelProvider = {
                        when (it) {
                            OrientationPreference.AutoSensor -> "自动旋转"
                            OrientationPreference.LandscapeFirst -> "锁定横屏"
                            OrientationPreference.PortraitTrackpad -> "锁定竖屏"
                        }
                    },
                    onSelect = { opt -> prefsStore.update { it.copy(orientationPref = opt) } },
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("默认竖屏布局", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.text)
                Spacer(modifier = Modifier.height(6.dp))
                RabbitSegmentedControl(
                    options = PortraitLayoutMode.entries,
                    selected = prefs.portraitLayoutMode,
                    labelProvider = { it.label },
                    onSelect = { mode -> prefsStore.update { it.copy(portraitLayoutMode = mode) } },
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("指针灵敏度", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
                    Text(
                        "${String.format("%.2f", prefs.pointerSensitivity)}x",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = palette.primaryText,
                    )
                }
                Slider(
                    value = prefs.pointerSensitivity,
                    onValueChange = { v -> prefsStore.update { it.copy(pointerSensitivity = v) } },
                    valueRange = 0.5f..2.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.primary,
                        activeTrackColor = palette.primary,
                        inactiveTrackColor = palette.line,
                    ),
                )
            }

            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Text("默认偏好", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Spacer(modifier = Modifier.height(6.dp))

                RabbitSwitchRow("默认开启性能 HUD", prefs.showHud) { on ->
                    prefsStore.update { it.copy(showHud = on) }
                }
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow("默认同步远端声音", prefs.audioEnabledDefault) { on ->
                    prefsStore.update { it.copy(audioEnabledDefault = on) }
                }
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow("默认同步剪贴板", prefs.clipboardEnabledDefault) { on ->
                    prefsStore.update { it.copy(clipboardEnabledDefault = on) }
                }
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow("直点模式指尖防遮挡偏置", prefs.directTouchOffset) { on ->
                    prefsStore.update { it.copy(directTouchOffset = on) }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val versionName = remember { getAppVersionName(context) }
    val coreUpdateManager: CoreUpdateManager = koinInject()
    val coreState by coreUpdateManager.state.collectAsStateWithLifecycle()
    var showCoreDialog by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { RabbitToast.error("未找到可用的浏览器") }
    }

    fun copyUrl(label: String, url: String) {
        clipboard.setText(AnnotatedString(url))
        RabbitToast.success("已复制$label 链接")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RabbitTopBar(title = "关于我们", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
            ) {
                RabbitCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = RabbitSpacing.Sm),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.rabbitcontrol),
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("RabbitControl", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(RabbitRadius.Pill))
                                .background(palette.field)
                                .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Pill))
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "v$versionName",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.muted,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "低延迟跨平台远程桌面与移动协助客户端",
                            fontSize = 13.sp,
                            color = palette.muted,
                        )
                    }
                }

                RabbitCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = RabbitSpacing.Lg, vertical = 4.dp),
                ) {
                    AboutInfoRow(label = "当前版本", value = "v$versionName")
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    val loadedCore = coreState.loadedCore
                    val coreSummary = buildString {
                        append("v${loadedCore.version} · ")
                        append(if (loadedCore.isDynamic) "已更新" else "内置")
                        append(" · ABI v${loadedCore.abiVersion}")
                        if (loadedCore.pendingRestartVersion != null) {
                            append(" · 待重启")
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 50.dp)
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .clickable {
                                showCoreDialog = true
                                if (!coreState.hasUpdate &&
                                    coreState.downloadState !is CoreDownloadState.Downloading &&
                                    coreState.downloadState !is CoreDownloadState.Verifying &&
                                    coreState.downloadState !is CoreDownloadState.ReadyToRestart
                                ) {
                                    coreUpdateManager.checkForUpdate(force = true)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("内核版本", fontSize = 14.sp, color = palette.text, modifier = Modifier.weight(1f))
                        if (coreState.hasUpdate) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE53935)),
                            )
                        }
                        Text(coreSummary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = palette.muted)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = palette.muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    AboutLinkRow(
                        label = "项目主页",
                        value = "TokenBoom/rabbitcontrol-android",
                        onClick = { openUrl(AppUpdateManager.GITHUB_REPO_URL) },
                        onLongClick = { copyUrl("项目主页", AppUpdateManager.GITHUB_REPO_URL) },
                    )
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    AboutLinkRow(
                        label = "反馈与建议",
                        value = "GitHub Issues",
                        onClick = { openUrl(AppUpdateManager.GITHUB_ISSUES_URL) },
                        onLongClick = { copyUrl("Issues", AppUpdateManager.GITHUB_ISSUES_URL) },
                    )
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    AboutLinkRow(
                        label = "发行版本",
                        value = "GitHub Releases",
                        onClick = { openUrl(AppUpdateManager.GITHUB_RELEASES_URL) },
                        onLongClick = { copyUrl("Releases", AppUpdateManager.GITHUB_RELEASES_URL) },
                    )
                }

                Text(
                    text = "提示：长按任意开源地址条目可快速复制链接",
                    fontSize = 12.sp,
                    color = palette.muted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (showCoreDialog) {
        CoreUpdateDialog(
            state = coreState,
            onRetryCheck = { coreUpdateManager.checkForUpdate(force = true) },
            onStartDownload = { candidate -> coreUpdateManager.startDownload(candidate) },
            onCancelDownload = { coreUpdateManager.cancelDownload() },
            onRevertToBuiltIn = { coreUpdateManager.revertToBuiltIn() },
            onRestartApp = { coreUpdateManager.restartApp() },
            onSaveMirrorPrefix = { prefix -> coreUpdateManager.setMirrorPrefix(prefix) },
            onOpenReleasePage = { url -> openUrl(url) },
            onDismiss = { showCoreDialog = false },
        )
    }
}

@Composable
private fun AboutInfoRow(
    label: String,
    value: String,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = palette.text, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = palette.muted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutLinkRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .clip(RoundedCornerShape(RabbitRadius.Sm))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = palette.text, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 12.sp,
            color = palette.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
fun AppUpdateDialog(
    state: AppUpdateUiState,
    onRetryCheck: () -> Unit,
    onStartDownload: (ReleaseInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String) -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val release = state.availableRelease ?: (state.checkStatus as? UpdateCheckStatus.UpdateAvailable)?.release

    Dialog(
        onDismissRequest = {
            if (state.downloadState !is UpdateDownloadState.Downloading) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 370.dp),
            shape = RoundedCornerShape(RabbitRadius.Xl),
            color = palette.surface,
            tonalElevation = 0.dp,
            shadowElevation = RabbitElevation.Floating,
            border = BorderStroke(1.dp, palette.line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                when (val dl = state.downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Text("正在下载更新", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        if (release != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "v${release.versionName}${release.apkName?.let { " · $it" } ?: ""}",
                                fontSize = 12.sp,
                                color = palette.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        val progress = if (dl.totalBytes > 0L) {
                            (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                color = palette.primary,
                                trackColor = palette.line,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
                                color = palette.primary,
                                trackColor = palette.line,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val pctStr = progress?.let { "${(it * 100).toInt()}%" } ?: "下载中…"
                            val sizeStr = if (dl.totalBytes > 0L) {
                                "${formatUpdateBytes(dl.downloadedBytes)} / ${formatUpdateBytes(dl.totalBytes)}"
                            } else {
                                formatUpdateBytes(dl.downloadedBytes)
                            }
                            Text("$pctStr · $sizeStr", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = palette.text)
                            Text(
                                if (dl.speedBytesPerSec > 0L) "${formatUpdateBytes(dl.speedBytesPerSec)}/s" else "",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.muted,
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        DialogActionButton(
                            label = "取消下载",
                            filled = false,
                            onClick = onCancelDownload,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Text("安装包已就绪", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (dl.needsInstallPermission) {
                                "请在系统设置中开启「允许来自此来源的应用」安装权限，授权后点击下方「立即安装」。"
                            } else {
                                "新版本安装包已下载完成，点击「立即安装」即可升级。"
                            },
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = palette.muted,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogActionButton(
                                label = "稍后",
                                filled = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            DialogActionButton(
                                label = "立即安装",
                                filled = true,
                                onClick = { onInstall(dl.apkPath) },
                                modifier = Modifier.weight(1.3f),
                            )
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = palette.danger, modifier = Modifier.size(20.dp))
                            Text("下载失败", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(dl.message, fontSize = 14.sp, lineHeight = 20.sp, color = palette.muted)
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogActionButton(
                                label = "浏览器打开",
                                filled = false,
                                onClick = {
                                    onOpenReleasePage(release?.htmlUrl ?: AppUpdateManager.GITHUB_RELEASES_URL)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            DialogActionButton(
                                label = "重试下载",
                                filled = true,
                                onClick = {
                                    if (release != null) onStartDownload(release) else onRetryCheck()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    UpdateDownloadState.Idle -> {
                        when (val check = state.checkStatus) {
                            UpdateCheckStatus.Checking -> {
                                Text("检查更新", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        color = palette.primary,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text("正在查询 GitHub 最新正式版本…", fontSize = 14.sp, color = palette.muted)
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                DialogActionButton(
                                    label = "关闭",
                                    filled = false,
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            is UpdateCheckStatus.UpdateAvailable -> {
                                val rel = check.release
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(palette.primarySoft),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Rounded.SystemUpdate, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("发现新版本 v${rel.versionName}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                        val meta = buildString {
                                            append("当前 v${state.currentVersion}")
                                            if (rel.apkSizeBytes > 0L) append(" · ${formatUpdateBytes(rel.apkSizeBytes)}")
                                            if (rel.publishedAt.isNotBlank()) append(" · ${rel.publishedAt}")
                                        }
                                        Text(meta, fontSize = 12.sp, color = palette.muted)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 72.dp, max = 210.dp)
                                        .clip(RoundedCornerShape(RabbitRadius.Md))
                                        .background(palette.field)
                                        .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = rel.releaseNotes,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = palette.text,
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(RabbitRadius.Sm))
                                        .clickable { onOpenReleasePage(rel.htmlUrl) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text("前往 GitHub Releases 查看详情", fontSize = 12.sp, color = palette.primaryText)
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = palette.primaryText, modifier = Modifier.size(13.dp))
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = "稍后再说",
                                        filled = false,
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                    )
                                    DialogActionButton(
                                        label = if (rel.apkUrl != null) "立即更新" else "前往下载",
                                        filled = true,
                                        onClick = {
                                            if (rel.apkUrl != null) {
                                                onStartDownload(rel)
                                            } else {
                                                onOpenReleasePage(rel.htmlUrl)
                                                onDismiss()
                                            }
                                        },
                                        modifier = Modifier.weight(1.3f),
                                    )
                                }
                            }

                            is UpdateCheckStatus.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.ErrorOutline, null, tint = palette.danger, modifier = Modifier.size(20.dp))
                                    Text("检查更新失败", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(check.message, fontSize = 14.sp, lineHeight = 20.sp, color = palette.muted)
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = "浏览器查看",
                                        filled = false,
                                        onClick = {
                                            onOpenReleasePage(AppUpdateManager.GITHUB_RELEASES_URL)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    DialogActionButton(
                                        label = "重试",
                                        filled = true,
                                        onClick = onRetryCheck,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            UpdateCheckStatus.Idle, is UpdateCheckStatus.UpToDate -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.CheckCircleOutline, null, tint = palette.success, modifier = Modifier.size(20.dp))
                                    Text("已是最新版本", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "当前版本 v${state.currentVersion} 已是 GitHub 最新正式版。",
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = palette.muted,
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = "查看历史版本",
                                        filled = false,
                                        onClick = {
                                            onOpenReleasePage(AppUpdateManager.GITHUB_RELEASES_URL)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1.1f),
                                    )
                                    DialogActionButton(
                                        label = "知道了",
                                        filled = true,
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalRabbitPalette.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(RabbitRadius.Md))
            .background(if (filled) palette.primary else palette.field)
            .then(
                if (!filled) Modifier.border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
            color = if (filled) Color.White else palette.text,
            maxLines = 1,
        )
    }
}

private fun formatUpdateBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}

@Composable
fun CoreUpdateDialog(
    state: CoreUpdateUiState,
    onRetryCheck: () -> Unit,
    onStartDownload: (CoreReleaseCandidate) -> Unit,
    onCancelDownload: () -> Unit,
    onRevertToBuiltIn: () -> Unit,
    onRestartApp: () -> Unit,
    onSaveMirrorPrefix: (String) -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val loaded = state.loadedCore
    val candidate = state.availableUpdate ?: (state.checkStatus as? CoreCheckStatus.UpdateAvailable)?.candidate
    val incompat = state.incompatibleNotice
        ?: (state.checkStatus as? CoreCheckStatus.UpdateAvailable)?.incompatibleNotice
        ?: (state.checkStatus as? CoreCheckStatus.UpToDate)?.incompatibleNotice
    var showMirrorEditor by remember { mutableStateOf(false) }
    var mirrorDraft by remember(state.mirrorPrefix) { mutableStateOf(state.mirrorPrefix) }

    Dialog(
        onDismissRequest = {
            if (state.downloadState !is CoreDownloadState.Downloading &&
                state.downloadState !is CoreDownloadState.Verifying
            ) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 380.dp),
            shape = RoundedCornerShape(RabbitRadius.Xl),
            color = palette.surface,
            tonalElevation = 0.dp,
            shadowElevation = RabbitElevation.Floating,
            border = BorderStroke(1.dp, palette.line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header: current core status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Memory, null, tint = palette.primaryText, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("rabbitcontrol-core 内核", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Text(
                            text = "当前 v${loaded.version} (${loaded.coreGitSha}) · ABI v${loaded.abiVersion}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RabbitStatusPill(
                        label = if (loaded.isDynamic) "已热更新" else "内置",
                        tone = if (loaded.isDynamic) StatusTone.Primary else StatusTone.Muted,
                    )
                }

                if (!loaded.lastFallbackReason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(RabbitRadius.Sm))
                            .background(palette.dangerSoft)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = palette.danger, modifier = Modifier.size(16.dp))
                        Text(
                            text = loaded.lastFallbackReason,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = palette.danger,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (val dl = state.downloadState) {
                    is CoreDownloadState.Downloading -> {
                        Text(
                            text = candidate?.let { "正在下载内核 v${it.manifest.coreVersion}" } ?: "正在下载内核",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.text,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val progress = if (dl.totalBytes > 0L) {
                            (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                color = palette.primary,
                                trackColor = palette.line,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
                                color = palette.primary,
                                trackColor = palette.line,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val pctStr = progress?.let { "${(it * 100).toInt()}%" } ?: "下载中…"
                            val sizeStr = if (dl.totalBytes > 0L) {
                                "${formatUpdateBytes(dl.downloadedBytes)} / ${formatUpdateBytes(dl.totalBytes)}"
                            } else {
                                formatUpdateBytes(dl.downloadedBytes)
                            }
                            Text("$pctStr · $sizeStr", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = palette.text)
                            Text(
                                if (dl.speedBytesPerSec > 0L) "${formatUpdateBytes(dl.speedBytesPerSec)}/s" else "",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.muted,
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        DialogActionButton(
                            label = "取消下载",
                            filled = false,
                            onClick = onCancelDownload,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    CoreDownloadState.Verifying -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                color = palette.primary,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(22.dp),
                            )
                            Text("正在校验 SHA-256 与文件完整性…", fontSize = 14.sp, color = palette.muted)
                        }
                    }

                    is CoreDownloadState.ReadyToRestart -> {
                        Text(
                            text = if (dl.isRevert) "已切换回内置内核" else "新内核已就绪",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (dl.isRevert) {
                                "已恢复为安装包内置内核 v${dl.targetVersion}，重启应用后生效。"
                            } else {
                                "内核 v${dl.targetVersion} 已通过 ECDSA P-256 签名与 SHA-256 校验，重启应用后立即生效。"
                            },
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = palette.muted,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogActionButton(
                                label = "下次启动生效",
                                filled = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            DialogActionButton(
                                label = "立即重启生效",
                                filled = true,
                                onClick = onRestartApp,
                                modifier = Modifier.weight(1.2f),
                            )
                        }
                    }

                    is CoreDownloadState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = palette.danger, modifier = Modifier.size(18.dp))
                            Text("内核更新失败", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(dl.message, fontSize = 13.sp, lineHeight = 19.sp, color = palette.muted)
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogActionButton(
                                label = "关闭",
                                filled = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            DialogActionButton(
                                label = "重试",
                                filled = true,
                                onClick = {
                                    if (candidate != null) onStartDownload(candidate) else onRetryCheck()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    CoreDownloadState.Idle -> {
                        when (val check = state.checkStatus) {
                            CoreCheckStatus.Checking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        color = palette.primary,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text("正在查询 GitHub 最新内核版本…", fontSize = 14.sp, color = palette.muted)
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                DialogActionButton(
                                    label = "关闭",
                                    filled = false,
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            is CoreCheckStatus.UpdateAvailable -> {
                                val rel = check.candidate
                                Text(
                                    text = "发现新内核 v${rel.manifest.coreVersion}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.text,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val meta = buildString {
                                    append(rel.artifact.abi)
                                    append(" · ${formatUpdateBytes(rel.artifact.sizeBytes)}")
                                    append(" · ${rel.manifest.coreGitSha}")
                                    if (rel.manifest.publishedAt.isNotBlank()) {
                                        append(" · ${rel.manifest.publishedAt.take(10)}")
                                    }
                                }
                                Text(meta, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = palette.muted)
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 64.dp, max = 180.dp)
                                        .clip(RoundedCornerShape(RabbitRadius.Md))
                                        .background(palette.field)
                                        .border(1.dp, palette.line, RoundedCornerShape(RabbitRadius.Md))
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp),
                                ) {
                                    Text(
                                        text = rel.manifest.releaseNotes,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = palette.text,
                                    )
                                }
                                if (incompat != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    IncompatibleCoreBanner(notice = incompat, onOpenReleasePage = onOpenReleasePage)
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = "稍后再说",
                                        filled = false,
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                    )
                                    DialogActionButton(
                                        label = "立即更新内核",
                                        filled = true,
                                        onClick = { onStartDownload(rel) },
                                        modifier = Modifier.weight(1.3f),
                                    )
                                }
                            }

                            is CoreCheckStatus.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.ErrorOutline, null, tint = palette.danger, modifier = Modifier.size(18.dp))
                                    Text("检查内核更新失败", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(check.message, fontSize = 13.sp, lineHeight = 19.sp, color = palette.muted)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = "浏览器查看",
                                        filled = false,
                                        onClick = {
                                            onOpenReleasePage(AppUpdateManager.GITHUB_RELEASES_URL)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    DialogActionButton(
                                        label = "重试",
                                        filled = true,
                                        onClick = onRetryCheck,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            CoreCheckStatus.Idle, is CoreCheckStatus.UpToDate -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.CheckCircleOutline, null, tint = palette.success, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = if (loaded.pendingRestartVersion != null) {
                                            "新内核 v${loaded.pendingRestartVersion} 已就绪（待重启）"
                                        } else {
                                            "当前内核已是最新兼容版"
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.text,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "内置基线 v${loaded.builtInVersion} · 当前已启用 ECDSA P-256 离线签名校验。",
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = palette.muted,
                                )
                                if (incompat != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    IncompatibleCoreBanner(notice = incompat, onOpenReleasePage = onOpenReleasePage)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (loaded.pendingRestartVersion != null) {
                                        DialogActionButton(
                                            label = "关闭",
                                            filled = false,
                                            onClick = onDismiss,
                                            modifier = Modifier.weight(1f),
                                        )
                                        DialogActionButton(
                                            label = "立即重启生效",
                                            filled = true,
                                            onClick = onRestartApp,
                                            modifier = Modifier.weight(1.2f),
                                        )
                                    } else {
                                        DialogActionButton(
                                            label = "重新检查",
                                            filled = false,
                                            onClick = onRetryCheck,
                                            modifier = Modifier.weight(1f),
                                        )
                                        DialogActionButton(
                                            label = "知道了",
                                            filled = true,
                                            onClick = onDismiss,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }

                        if (loaded.isDynamic || loaded.pendingRestartVersion != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            DialogActionButton(
                                label = "恢复内置内核 (v${loaded.builtInVersion})",
                                filled = false,
                                onClick = onRevertToBuiltIn,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(RabbitRadius.Sm))
                                .clickable { showMirrorEditor = !showMirrorEditor }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (state.mirrorPrefix.isBlank()) "下载镜像加速（可选）" else "镜像已配置",
                                fontSize = 12.sp,
                                color = palette.primaryText,
                            )
                            Text(
                                text = if (showMirrorEditor) "收起" else "设置",
                                fontSize = 12.sp,
                                color = palette.muted,
                            )
                        }

                        if (showMirrorEditor) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = mirrorDraft,
                                onValueChange = { mirrorDraft = it },
                                singleLine = true,
                                placeholder = { Text("例如 https://ghfast.top/", fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = palette.primary,
                                    unfocusedBorderColor = palette.line,
                                    focusedTextColor = palette.text,
                                    unfocusedTextColor = palette.text,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DialogActionButton(
                                    label = "清除直连",
                                    filled = false,
                                    onClick = {
                                        mirrorDraft = ""
                                        onSaveMirrorPrefix("")
                                        showMirrorEditor = false
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                DialogActionButton(
                                    label = "保存前缀",
                                    filled = true,
                                    onClick = {
                                        onSaveMirrorPrefix(mirrorDraft)
                                        showMirrorEditor = false
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncompatibleCoreBanner(
    notice: CoreIncompatibleNotice,
    onOpenReleasePage: (String) -> Unit,
) {
    val palette = LocalRabbitPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RabbitRadius.Sm))
            .background(palette.primarySoft)
            .clickable { onOpenReleasePage(notice.htmlUrl) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = palette.primaryText, modifier = Modifier.size(16.dp))
        Text(
            text = "另有更新内核 v${notice.coreVersion}（ABI v${notice.requiredAbiVersion}），需升级 App 至 v${notice.minAppVersion}+ 后可用",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = palette.primaryText,
            modifier = Modifier.weight(1f),
        )
    }
}

