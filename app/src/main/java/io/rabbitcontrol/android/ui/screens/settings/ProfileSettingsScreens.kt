package io.rabbitcontrol.android.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.PortraitLayoutMode
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.host.HostManager
import io.rabbitcontrol.android.ui.components.RabbitCard
import io.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitSwitchRow
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
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

    var copiedCodeHint by remember { mutableStateOf(false) }

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
                            if (copiedCodeHint) {
                                Text("已复制到剪贴板", fontSize = 11.sp, color = palette.success)
                            }
                        }
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(code))
                            copiedCodeHint = true
                        }) {
                            Icon(Icons.Rounded.ContentCopy, "复制验证码", tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            copiedCodeHint = false
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

                if (!hostState.lastError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(hostState.lastError.orEmpty(), fontSize = 12.sp, color = palette.danger)
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
    val versionName = remember { getAppVersionName(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding(),
    ) {
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
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Devices, null, tint = palette.primaryText, modifier = Modifier.size(28.dp))
                    }
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

            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                AboutInfoRow(label = "当前版本", value = "v$versionName")
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                AboutInfoRow(label = "GitHub 仓库", value = "待配置")
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                AboutInfoRow(label = "反馈与建议", value = "待配置 GitHub Issues")
            }
        }
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
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = palette.text, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = palette.muted)
    }
}
