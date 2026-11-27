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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.rabbitcontrol.android.error.RcErrorAction
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcErrorPresentation
import com.rabbitcontrol.android.error.RcUiError
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.PortraitLayoutMode
import io.rabbitcontrol.android.core.data.ProxyConfigModel
import io.rabbitcontrol.android.core.data.ProxyConfigStore
import io.rabbitcontrol.android.core.data.ProxyModeOption
import io.rabbitcontrol.android.core.data.ProxyProtocolOption
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.error.RcUiErrorCodec
import io.rabbitcontrol.android.core.host.HostManager
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
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
import io.rabbitcontrol.android.ui.components.RabbitPrimaryButton
import io.rabbitcontrol.android.ui.components.RabbitSegmentedControl
import io.rabbitcontrol.android.ui.components.RabbitStatusPill
import io.rabbitcontrol.android.ui.components.RabbitSwitchRow
import io.rabbitcontrol.android.ui.components.RabbitTextField
import io.rabbitcontrol.android.ui.components.RabbitToast
import io.rabbitcontrol.android.ui.components.RabbitTopBar
import io.rabbitcontrol.android.ui.components.RcErrorHub
import io.rabbitcontrol.android.ui.components.RcInlineError
import io.rabbitcontrol.android.ui.components.StatusTone
import io.rabbitcontrol.android.ui.screens.auth.AuthStep
import io.rabbitcontrol.android.ui.theme.LocalRabbitPalette
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.RabbitSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    LaunchedEffect(hostState.lastUiError, hostState.lastError) {
        val uiErr = hostState.lastUiError
        val err = hostState.lastError
        if (uiErr != null) {
            RcErrorHub.present(uiErr)
            hostManager.clearLastError()
        } else if (!err.isNullOrBlank()) {
            RabbitToast.error(err)
            hostManager.clearLastError()
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
        } else {
            RcErrorHub.present(
                RcUiErrorCodec.resolvePlatformError(RcErrorCodes.PLATFORM_ANDROID_MEDIA_PROJECTION_DENIED),
            )
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
        RabbitTopBar(title = stringResource(R.string.home_menu_host_mode), onBack = onBack)

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
                    Text(stringResource(R.string.host_service_status), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text, modifier = Modifier.weight(1f))
                    val (pillText, pillTone) = when {
                        hostState.connectedPeer != null -> stringResource(R.string.home_host_controlling) to StatusTone.Primary
                        hostState.enabled -> stringResource(R.string.home_host_waiting) to StatusTone.Success
                        hostState.starting -> stringResource(R.string.home_host_starting) to StatusTone.Warning
                        else -> stringResource(R.string.home_host_off) to StatusTone.Muted
                    }
                    RabbitStatusPill(label = pillText, tone = pillTone)
                }

                Spacer(modifier = Modifier.height(6.dp))
                RabbitSwitchRow(
                    title = stringResource(R.string.host_allow_control),
                    subtitle = if (profile == null) {
                        stringResource(R.string.host_sub_need_login)
                    } else if (hostState.enabled) {
                        val readySuffix = if (hostState.ownerSelfControlReady) {
                            stringResource(R.string.host_sub_owner_ready)
                        } else {
                            ""
                        }
                        stringResource(R.string.host_sub_enabled, readySuffix)
                    } else {
                        stringResource(R.string.host_sub_disabled)
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
                                    stringResource(R.string.host_peer_owner, hostState.connectedPeer.orEmpty())
                                } else {
                                    stringResource(R.string.host_peer_helper, hostState.connectedPeer.orEmpty())
                                },
                                fontSize = 12.sp,
                                color = palette.primaryText,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = hostManager::disconnectClient,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(stringResource(R.string.host_disconnect), color = palette.danger, fontSize = 12.sp)
                            }
                        }
                    } else if (!signal.deviceIdFormatted.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.host_self_id, signal.deviceIdFormatted.orEmpty()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = palette.muted,
                        )
                    }
                }

                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow(
                    title = stringResource(R.string.host_allow_assist),
                    subtitle = stringResource(R.string.host_allow_assist_sub),
                    checked = hostState.allowRemoteAssist,
                    onCheckedChange = hostManager::setAllowRemoteAssist,
                )

                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow(
                    title = stringResource(R.string.host_allow_assist_file_transfer),
                    subtitle = stringResource(R.string.host_allow_assist_file_transfer_sub),
                    checked = hostState.allowAssistFileTransfer,
                    onCheckedChange = hostManager::setAllowAssistFileTransfer,
                )

                if (hostState.enabled && hostState.allowRemoteAssist && !hostState.tempCode.isNullOrBlank()) {
                    val code = hostState.tempCode!!
                    val copiedCodeMsg = stringResource(R.string.host_temp_code_copied)
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
                            Text(stringResource(R.string.host_temp_code), fontSize = 11.sp, color = palette.muted)
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
                            RabbitToast.success(copiedCodeMsg)
                        }) {
                            Icon(Icons.Rounded.ContentCopy, stringResource(R.string.host_cd_copy_code), tint = palette.muted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = {
                            hostManager.rotateTempCode()
                        }) {
                            Icon(Icons.Rounded.Refresh, stringResource(R.string.host_cd_rotate_code), tint = palette.muted, modifier = Modifier.size(18.dp))
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
                        Text(stringResource(R.string.host_accessibility_title), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
                        Text(
                            if (hostState.accessibilityEnabled) {
                                stringResource(R.string.host_accessibility_on)
                            } else {
                                stringResource(R.string.host_accessibility_off)
                            },
                            fontSize = 12.sp,
                            color = if (hostState.accessibilityEnabled) palette.success else palette.warning,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.muted)
                }

                if (hostState.enabled && !hostState.accessibilityEnabled) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val accessibilityError = remember {
                        RcUiErrorCodec.resolvePlatformError(
                            code = RcErrorCodes.PLATFORM_ANDROID_ACCESSIBILITY_DENIED,
                            presentationOverride = RcErrorPresentation.INLINE,
                        )
                    }
                    RcInlineError(
                        error = accessibilityError,
                        onAction = { action ->
                            if (action == RcErrorAction.OPEN_PERMISSIONS || action == RcErrorAction.OPEN_SETTINGS) {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                    )
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
        RabbitTopBar(title = stringResource(R.string.home_menu_interaction), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
        ) {
            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_controls_screen), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Spacer(modifier = Modifier.height(14.dp))

                Text(stringResource(R.string.settings_default_touch), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.text)
                Spacer(modifier = Modifier.height(6.dp))
                RabbitSegmentedControl(
                    options = TouchInputMode.entries,
                    selected = prefs.touchMode,
                    labelProvider = { it.label },
                    onSelect = { mode -> prefsStore.update { it.copy(touchMode = mode) } },
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.settings_default_orientation), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.text)
                Spacer(modifier = Modifier.height(6.dp))
                val autoOrientLabel = stringResource(R.string.orientation_auto)
                val landOrientLabel = stringResource(R.string.orientation_landscape)
                val portOrientLabel = stringResource(R.string.orientation_portrait)
                RabbitSegmentedControl(
                    options = OrientationPreference.entries,
                    selected = prefs.orientationPref,
                    labelProvider = {
                        when (it) {
                            OrientationPreference.AutoSensor -> autoOrientLabel
                            OrientationPreference.LandscapeFirst -> landOrientLabel
                            OrientationPreference.PortraitTrackpad -> portOrientLabel
                        }
                    },
                    onSelect = { opt -> prefsStore.update { it.copy(orientationPref = opt) } },
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.settings_default_portrait_layout), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.text)
                Spacer(modifier = Modifier.height(6.dp))
                RabbitSegmentedControl(
                    options = PortraitLayoutMode.entries,
                    selected = prefs.portraitLayoutMode,
                    labelProvider = { it.label },
                    onSelect = { mode -> prefsStore.update { it.copy(portraitLayoutMode = mode) } },
                )

                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.settings_pointer_sensitivity), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text)
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
                Text(stringResource(R.string.settings_default_prefs), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                Spacer(modifier = Modifier.height(6.dp))

                RabbitSwitchRow(stringResource(R.string.settings_hud_default), prefs.showHud) { on ->
                    prefsStore.update { it.copy(showHud = on) }
                }
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow(stringResource(R.string.settings_audio_default), prefs.audioEnabledDefault) { on ->
                    prefsStore.update { it.copy(audioEnabledDefault = on) }
                }
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow(stringResource(R.string.settings_clipboard_default), prefs.clipboardEnabledDefault) { on ->
                    prefsStore.update { it.copy(clipboardEnabledDefault = on) }
                }
                HorizontalDivider(color = palette.line, thickness = 1.dp)
                RabbitSwitchRow(stringResource(R.string.settings_direct_offset), prefs.directTouchOffset) { on ->
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
            .onFailure { RabbitToast.error(AppStrings.get(R.string.about_no_browser, "未找到可用的浏览器")) }
    }

    fun copyUrl(label: String, url: String) {
        clipboard.setText(AnnotatedString(url))
        RabbitToast.success(AppStrings.get(R.string.about_copied_link, "已复制$label 链接", label))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            RabbitTopBar(title = stringResource(R.string.home_menu_about), onBack = onBack)

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
                            text = stringResource(R.string.about_tagline),
                            fontSize = 13.sp,
                            color = palette.muted,
                        )
                    }
                }

                RabbitCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = RabbitSpacing.Lg, vertical = 4.dp),
                ) {
                    AboutInfoRow(label = stringResource(R.string.about_current_version), value = "v$versionName")
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    val loadedCore = coreState.loadedCore
                    val coreStatusTag = stringResource(if (loadedCore.isDynamic) R.string.home_core_dynamic else R.string.home_core_builtin)
                    val pendingTag = stringResource(R.string.about_pending_short)
                    val coreSummary = buildString {
                        append("v${loadedCore.version} · ")
                        append(coreStatusTag)
                        append(" · ABI v${loadedCore.abiVersion}")
                        val shortCommit = loadedCore.coreGitSha.trim()
                        if (shortCommit.isNotEmpty() && !shortCommit.equals("unknown", ignoreCase = true)) {
                            append(" (${shortCommit.take(7)})")
                        }
                        if (loadedCore.pendingRestartVersion != null) {
                            append(" · $pendingTag")
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
                        Text(stringResource(R.string.about_core_version), fontSize = 14.sp, color = palette.text, modifier = Modifier.weight(1f))
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
                    val homepageLabel = stringResource(R.string.about_homepage)
                    val feedbackLabel = stringResource(R.string.about_feedback)
                    val releasesLabel = stringResource(R.string.about_releases)
                    AboutLinkRow(
                        label = homepageLabel,
                        value = "TokenBoom/rabbitcontrol-android",
                        onClick = { openUrl(AppUpdateManager.GITHUB_REPO_URL) },
                        onLongClick = { copyUrl(homepageLabel, AppUpdateManager.GITHUB_REPO_URL) },
                    )
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    AboutLinkRow(
                        label = feedbackLabel,
                        value = "GitHub Issues",
                        onClick = { openUrl(AppUpdateManager.GITHUB_ISSUES_URL) },
                        onLongClick = { copyUrl("Issues", AppUpdateManager.GITHUB_ISSUES_URL) },
                    )
                    HorizontalDivider(color = palette.line, thickness = 1.dp)
                    AboutLinkRow(
                        label = releasesLabel,
                        value = "GitHub Releases",
                        onClick = { openUrl(AppUpdateManager.GITHUB_RELEASES_URL) },
                        onLongClick = { copyUrl("Releases", AppUpdateManager.GITHUB_RELEASES_URL) },
                    )
                }

                Text(
                    text = stringResource(R.string.about_copy_tip),
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 370.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(RabbitRadius.Xl),
            color = palette.surface,
            tonalElevation = 0.dp,
            shadowElevation = RabbitElevation.Floating,
            border = BorderStroke(1.dp, palette.line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                when (val dl = state.downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Text(stringResource(R.string.update_downloading_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
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
                            val pctStr = progress?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.update_downloading_pct)
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
                            label = stringResource(R.string.update_cancel_download),
                            filled = false,
                            onClick = onCancelDownload,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Text(stringResource(R.string.update_ready_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (dl.needsInstallPermission) {
                                stringResource(R.string.update_ready_perm_desc)
                            } else {
                                stringResource(R.string.update_ready_desc)
                            },
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = palette.muted,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogActionButton(
                                label = stringResource(R.string.common_later),
                                filled = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            DialogActionButton(
                                label = stringResource(R.string.update_install_now),
                                filled = true,
                                onClick = { onInstall(dl.apkPath) },
                                modifier = Modifier.weight(1.3f),
                            )
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = palette.danger, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.update_download_failed), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(NativeErrorLocalizer.localize(dl.message), fontSize = 14.sp, lineHeight = 20.sp, color = palette.muted)
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogActionButton(
                                label = stringResource(R.string.update_open_browser),
                                filled = false,
                                onClick = {
                                    onOpenReleasePage(release?.htmlUrl ?: AppUpdateManager.GITHUB_RELEASES_URL)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            DialogActionButton(
                                label = stringResource(R.string.update_retry_download),
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
                                Text(stringResource(R.string.update_check_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
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
                                    Text(stringResource(R.string.update_checking_msg), fontSize = 14.sp, color = palette.muted)
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                DialogActionButton(
                                    label = stringResource(R.string.common_close),
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
                                        Text(stringResource(R.string.update_found_title, rel.versionName), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                        val meta = buildString {
                                            append(stringResource(R.string.update_current_prefix, state.currentVersion))
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
                                    Text(stringResource(R.string.update_view_github), fontSize = 12.sp, color = palette.primaryText)
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = palette.primaryText, modifier = Modifier.size(13.dp))
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = stringResource(R.string.common_later),
                                        filled = false,
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                    )
                                    DialogActionButton(
                                        label = stringResource(if (rel.apkUrl != null) R.string.update_now else R.string.update_go_download),
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
                                    Text(stringResource(R.string.update_check_failed), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(NativeErrorLocalizer.localize(check.message), fontSize = 14.sp, lineHeight = 20.sp, color = palette.muted)
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = stringResource(R.string.update_view_browser),
                                        filled = false,
                                        onClick = {
                                            onOpenReleasePage(AppUpdateManager.GITHUB_RELEASES_URL)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    DialogActionButton(
                                        label = stringResource(R.string.common_retry),
                                        filled = true,
                                        onClick = onRetryCheck,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            UpdateCheckStatus.Idle, is UpdateCheckStatus.UpToDate -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.CheckCircleOutline, null, tint = palette.success, modifier = Modifier.size(20.dp))
                                    Text(stringResource(R.string.update_up_to_date_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.update_up_to_date_desc, state.currentVersion),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = palette.muted,
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = stringResource(R.string.update_view_history),
                                        filled = false,
                                        onClick = {
                                            onOpenReleasePage(AppUpdateManager.GITHUB_RELEASES_URL)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1.1f),
                                    )
                                    DialogActionButton(
                                        label = stringResource(R.string.common_got_it),
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(RabbitRadius.Xl),
            color = palette.surface,
            tonalElevation = 0.dp,
            shadowElevation = RabbitElevation.Floating,
            border = BorderStroke(1.dp, palette.line),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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
                        Text(stringResource(R.string.core_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        Text(
                            text = stringResource(R.string.core_current_meta, loaded.version, loaded.coreGitSha, loaded.abiVersion),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RabbitStatusPill(
                        label = stringResource(if (loaded.isDynamic) R.string.home_core_dynamic else R.string.home_core_builtin),
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
                            text = NativeErrorLocalizer.localize(loaded.lastFallbackReason),
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
                            text = candidate?.let { stringResource(R.string.core_downloading_ver, it.manifest.coreVersion) }
                                ?: stringResource(R.string.core_downloading),
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
                            val pctStr = progress?.let { "${(it * 100).toInt()}%" } ?: stringResource(R.string.update_downloading_pct)
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
                            label = stringResource(R.string.update_cancel_download),
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
                            Text(stringResource(R.string.core_verifying), fontSize = 14.sp, color = palette.muted)
                        }
                    }

                    is CoreDownloadState.ReadyToRestart -> {
                        Text(
                            text = stringResource(if (dl.isRevert) R.string.core_reverted_title else R.string.core_ready_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                if (dl.isRevert) R.string.core_reverted_desc else R.string.core_ready_desc,
                                dl.targetVersion,
                            ),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = palette.muted,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogActionButton(
                                label = stringResource(R.string.core_apply_next_launch),
                                filled = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            DialogActionButton(
                                label = stringResource(R.string.core_restart_now),
                                filled = true,
                                onClick = onRestartApp,
                                modifier = Modifier.weight(1.2f),
                            )
                        }
                    }

                    is CoreDownloadState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = palette.danger, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.core_update_failed), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(NativeErrorLocalizer.localize(dl.message), fontSize = 13.sp, lineHeight = 19.sp, color = palette.muted)
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DialogActionButton(
                                label = stringResource(R.string.common_close),
                                filled = false,
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            )
                            DialogActionButton(
                                label = stringResource(R.string.common_retry),
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
                                    Text(stringResource(R.string.core_checking_msg), fontSize = 14.sp, color = palette.muted)
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                DialogActionButton(
                                    label = stringResource(R.string.common_close),
                                    filled = false,
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            is CoreCheckStatus.UpdateAvailable -> {
                                val rel = check.candidate
                                Text(
                                    text = stringResource(R.string.core_found_title, rel.manifest.coreVersion),
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
                                        label = stringResource(R.string.common_later),
                                        filled = false,
                                        onClick = onDismiss,
                                        modifier = Modifier.weight(1f),
                                    )
                                    DialogActionButton(
                                        label = stringResource(R.string.core_update_now),
                                        filled = true,
                                        onClick = { onStartDownload(rel) },
                                        modifier = Modifier.weight(1.3f),
                                    )
                                }
                            }

                            is CoreCheckStatus.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.ErrorOutline, null, tint = palette.danger, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.core_check_failed), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(NativeErrorLocalizer.localize(check.message), fontSize = 13.sp, lineHeight = 19.sp, color = palette.muted)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DialogActionButton(
                                        label = stringResource(R.string.update_view_browser),
                                        filled = false,
                                        onClick = {
                                            onOpenReleasePage(AppUpdateManager.GITHUB_RELEASES_URL)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    DialogActionButton(
                                        label = stringResource(R.string.common_retry),
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
                                            stringResource(R.string.core_pending_ready_title, loaded.pendingRestartVersion)
                                        } else {
                                            stringResource(R.string.core_up_to_date_title)
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.text,
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.core_baseline_desc, loaded.builtInVersion),
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
                                            label = stringResource(R.string.common_close),
                                            filled = false,
                                            onClick = onDismiss,
                                            modifier = Modifier.weight(1f),
                                        )
                                        DialogActionButton(
                                            label = stringResource(R.string.core_restart_now),
                                            filled = true,
                                            onClick = onRestartApp,
                                            modifier = Modifier.weight(1.2f),
                                        )
                                    } else {
                                        DialogActionButton(
                                            label = stringResource(R.string.core_recheck),
                                            filled = false,
                                            onClick = onRetryCheck,
                                            modifier = Modifier.weight(1f),
                                        )
                                        DialogActionButton(
                                            label = stringResource(R.string.common_got_it),
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
                                label = stringResource(R.string.core_revert_btn, loaded.builtInVersion),
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
                                text = stringResource(
                                    if (state.mirrorPrefix.isBlank()) R.string.core_mirror_title else R.string.core_mirror_configured,
                                ),
                                fontSize = 12.sp,
                                color = palette.primaryText,
                            )
                            Text(
                                text = stringResource(if (showMirrorEditor) R.string.common_collapse else R.string.common_settings),
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
                                placeholder = { Text(stringResource(R.string.core_mirror_hint), fontSize = 12.sp) },
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
                                    label = stringResource(R.string.core_mirror_clear),
                                    filled = false,
                                    onClick = {
                                        mirrorDraft = ""
                                        onSaveMirrorPrefix("")
                                        showMirrorEditor = false
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                DialogActionButton(
                                    label = stringResource(R.string.core_mirror_save),
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
            text = stringResource(
                R.string.core_incompat_banner,
                notice.coreVersion,
                notice.requiredAbiVersion,
                notice.minAppVersion,
            ),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = palette.primaryText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ProxySettingsScreen(
    onBack: () -> Unit,
) {
    val palette = LocalRabbitPalette.current
    val proxyStore: ProxyConfigStore = koinInject()
    val saved by proxyStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var mode by remember(saved) { mutableStateOf(saved.mode) }
    var protocol by remember(saved) { mutableStateOf(saved.protocol) }
    var host by remember(saved) { mutableStateOf(saved.host) }
    var portText by remember(saved) { mutableStateOf(saved.port.toString()) }
    var username by remember(saved) { mutableStateOf(saved.username) }
    var password by remember(saved) { mutableStateOf(saved.password) }
    var showPassword by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<RcUiError?>(null) }
    var saving by remember { mutableStateOf(false) }

    val savedToast = stringResource(R.string.proxy_saved)
    val invalidHostMsg = stringResource(R.string.proxy_err_host_required)
    val invalidPortMsg = stringResource(R.string.proxy_err_port_range)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvas)
            .systemBarsPadding(),
    ) {
        RabbitTopBar(
            title = stringResource(R.string.proxy_settings_title),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RabbitSpacing.Lg, vertical = RabbitSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(RabbitSpacing.Md),
        ) {
            RabbitCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.proxy_mode_label),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.proxy_mode_desc),
                    fontSize = 12.sp,
                    color = palette.muted,
                )
                Spacer(modifier = Modifier.height(10.dp))
                val modes = ProxyModeOption.entries
                val modeLabels = listOf(
                    stringResource(R.string.proxy_mode_none_short),
                    stringResource(R.string.proxy_mode_system_short),
                    stringResource(R.string.proxy_mode_manual_short),
                )
                RabbitSegmentedControl(
                    options = modes,
                    selected = mode,
                    labelProvider = { option -> modeLabels[modes.indexOf(option)] },
                    onSelect = {
                        mode = it
                        inlineError = null
                    },
                )
            }

            if (mode == ProxyModeOption.Manual) {
                RabbitCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.proxy_protocol_label),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val protocols = ProxyProtocolOption.entries
                    RabbitSegmentedControl(
                        options = protocols,
                        selected = protocol,
                        labelProvider = { it.wireValue.uppercase() },
                        onSelect = {
                            protocol = it
                            inlineError = null
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    RabbitTextField(
                        value = host,
                        onValueChange = {
                            host = it
                            inlineError = null
                        },
                        label = stringResource(R.string.proxy_host_label),
                        placeholder = stringResource(R.string.proxy_host_hint),
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    RabbitTextField(
                        value = portText,
                        onValueChange = { next ->
                            if (next.length <= 5 && next.all { it.isDigit() }) {
                                portText = next
                                inlineError = null
                            }
                        },
                        label = stringResource(R.string.proxy_port_label),
                        placeholder = "7890",
                        keyboardType = KeyboardType.Number,
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    RabbitTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = stringResource(R.string.proxy_username_label),
                        placeholder = stringResource(R.string.proxy_optional_hint),
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    RabbitTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.proxy_password_label),
                        placeholder = stringResource(R.string.proxy_optional_hint),
                        isPassword = !showPassword,
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = palette.muted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
            }

            inlineError?.let { uiErr ->
                RcInlineError(
                    error = uiErr,
                    onDismiss = { inlineError = null },
                )
            }

            RabbitPrimaryButton(
                label = stringResource(R.string.common_save),
                busy = saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (mode == ProxyModeOption.Manual) {
                        val trimmedHost = host.trim()
                        if (trimmedHost.isEmpty()) {
                            inlineError = RcUiErrorCodec.inlineValidation(invalidHostMsg)
                            return@RabbitPrimaryButton
                        }
                        val parsedPort = portText.trim().toIntOrNull()
                        if (parsedPort == null || parsedPort !in 1..65535) {
                            inlineError = RcUiErrorCodec.inlineValidation(invalidPortMsg)
                            return@RabbitPrimaryButton
                        }
                    }
                    val targetPort = portText.trim().toIntOrNull()?.coerceIn(1, 65535) ?: saved.port
                    val model = ProxyConfigModel(
                        mode = mode,
                        protocol = protocol,
                        host = host.trim(),
                        port = targetPort,
                        username = username.trim(),
                        password = password,
                    )
                    saving = true
                    inlineError = null
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            proxyStore.saveAndApply(model)
                        }
                        saving = false
                        res.onSuccess {
                            RabbitToast.success(savedToast)
                            onBack()
                        }.onFailure { err ->
                            inlineError = RcUiErrorCodec.fromThrowable(
                                err,
                                fallbackCode = RcErrorCodes.PROXY_CONFIG_CONFLICT,
                            )
                        }
                    }
                },
            )
        }
    }
}

