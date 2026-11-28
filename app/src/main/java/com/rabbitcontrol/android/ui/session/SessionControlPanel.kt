package com.rabbitcontrol.android.ui.session

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Monitor
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.data.ConnectionModePref
import com.rabbitcontrol.android.core.data.DeviceToolKind
import com.rabbitcontrol.android.core.data.InteractionPrefs
import com.rabbitcontrol.android.core.data.KeyMapState
import com.rabbitcontrol.android.core.data.OrientationPreference
import com.rabbitcontrol.android.core.data.PortraitLayoutMode
import com.rabbitcontrol.android.core.data.QuickActionKind
import com.rabbitcontrol.android.core.data.SidebarConfig
import com.rabbitcontrol.android.core.data.TouchInputMode
import com.rabbitcontrol.android.core.i18n.AppStrings
import com.rabbitcontrol.android.core.nativebridge.LocalCodecSupport
import com.rabbitcontrol.android.core.nativebridge.MediaDiagnosticsModel
import com.rabbitcontrol.android.core.nativebridge.formatVideoCodecLabel
import com.rabbitcontrol.android.core.nativebridge.normalizeVideoCodecId
import com.rabbitcontrol.android.core.session.ActiveSessionState
import com.rabbitcontrol.android.ui.components.RabbitToast
import com.rabbitcontrol.android.ui.theme.RabbitElevation
import com.rabbitcontrol.android.ui.theme.RabbitRadius
import com.rabbitcontrol.android.ui.theme.SessionPalette
import kotlin.math.roundToInt

enum class ControlPanelTab(
    @androidx.annotation.StringRes val labelRes: Int,
    private val fallbackLabel: String,
    val icon: ImageVector,
) {
    Controls(R.string.cp_tab_controls, "操控", Icons.Rounded.Tune),
    Display(R.string.cp_tab_display, "画质", Icons.Rounded.Speed),
    Keymap(R.string.cp_tab_keymap, "键位", Icons.Rounded.SportsEsports),
    Tools(R.string.cp_tab_tools, "工具", Icons.Rounded.FolderOpen);

    val label: String
        get() = AppStrings.get(labelRes, fallbackLabel)
}

@Composable
fun SessionControlPanel(
    viewModel: SessionViewModel,
    isPortrait: Boolean,
    initialTab: ControlPanelTab,
    onQuickAction: (QuickActionKind) -> Unit,
    onEditSidebarSlot: (SidebarSide, Int) -> Unit,
    onEditKeymap: () -> Unit,
    onShowGestureGuide: () -> Unit,
    onShowKeyboard: () -> Unit,
    onPushClipboard: () -> Unit,
    onOpenTool: (DeviceToolKind) -> Unit,
    onRequestDisconnect: () -> Unit,
    onDismiss: () -> Unit,
    isTablet: Boolean = false,
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val diag by viewModel.media.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val sidebarConfig by viewModel.sidebarConfig.collectAsStateWithLifecycle()
    val keymapState by viewModel.keymapState.collectAsStateWithLifecycle()
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var showEncryptionDialog by remember { mutableStateOf(false) }

    val neg = state.negotiated
    val devTitle = state.deviceName.ifBlank { stringResource(R.string.cp_title_default) }
    val telemetry = if (neg != null) {
        val decoderActive = diag.codecName.isNotBlank() || diag.decodedFrames > 0L || diag.renderedFrames > 0L
        val useHw = if (decoderActive) diag.hardware else prefs.preferHardwareDecode
        val hwTag = stringResource(if (useHw) R.string.cp_hw_decode else R.string.cp_sw_decode)
        val codecTag = formatVideoCodecLabel(
            diag.streamCodec.ifBlank { state.displayConfig?.codec },
            diag.codecName,
        )
        if (codecTag != null) {
            "${neg.width}×${neg.height} · ${neg.fps}fps · $codecTag $hwTag · ${neg.transport}"
        } else {
            "${neg.width}×${neg.height} · ${neg.fps}fps · ${neg.transport} · $hwTag"
        }
    } else {
        stringResource(R.string.cp_ready)
    }

    Surface(
        modifier = Modifier
            .then(
                if (isPortrait) {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.52f)
                        .heightIn(max = 440.dp)
                } else {
                    Modifier
                        .widthIn(
                            min = if (isTablet) 360.dp else 320.dp,
                            max = if (isTablet) 420.dp else 360.dp,
                        )
                        .fillMaxWidth()
                        .fillMaxHeight()
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = if (isPortrait) {
            RoundedCornerShape(topStart = RabbitRadius.Xl, topEnd = RabbitRadius.Xl)
        } else {
            RoundedCornerShape(topStart = RabbitRadius.Xl, bottomStart = RabbitRadius.Xl)
        },
        color = if (isPortrait) SessionPalette.chrome.copy(alpha = 0.98f) else SessionPalette.chrome.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = RabbitElevation.Sheet,
        border = BorderStroke(1.dp, SessionPalette.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isPortrait) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                    },
                ),
        ) {
            if (isPortrait) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally)
                        .width(32.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SessionPalette.border),
                )
            }

            // Unified Header Row: Device Title + Telemetry + mTLS Badge + Guide + Lock + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = devTitle,
                        color = SessionPalette.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = telemetry,
                        color = SessionPalette.muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (neg?.mtlsActive == true) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SessionPalette.success.copy(alpha = 0.14f))
                            .border(1.dp, SessionPalette.success.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .clickable { showEncryptionDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.VerifiedUser,
                            stringResource(R.string.cp_mtls_badge_cd),
                            tint = SessionPalette.success,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SessionPalette.surface)
                        .border(1.dp, SessionPalette.border, RoundedCornerShape(8.dp))
                        .clickable(onClick = onShowGestureGuide),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.HelpOutline, stringResource(R.string.cp_guide), tint = SessionPalette.accentText, modifier = Modifier.size(16.dp))
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SessionPalette.surface)
                        .border(1.dp, SessionPalette.border, RoundedCornerShape(8.dp))
                        .clickable(onClick = viewModel::lockRemoteScreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Lock, stringResource(R.string.cp_lock), tint = SessionPalette.text, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.common_close), tint = SessionPalette.text, modifier = Modifier.size(18.dp))
                }
            }

            // Horizontal Tab Row: Quick Disconnect first + Divider + Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(RabbitRadius.Sm))
                        .background(SessionPalette.danger)
                        .clickable(onClick = onRequestDisconnect)
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.host_disconnect),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(16.dp)
                        .background(SessionPalette.border),
                )

                ControlPanelTab.entries.forEach { tab ->
                    SessionKeyChip(tab.label, highlighted = tab == activeTab, compact = true) {
                        activeTab = tab
                    }
                }
            }

            HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ControlCenterTabBody(
                    activeTab = activeTab,
                    isPortrait = isPortrait,
                    state = state,
                    diag = diag,
                    prefs = prefs,
                    localCodecSupport = viewModel.localCodecSupport,
                    sidebarConfig = sidebarConfig,
                    keymapState = keymapState,
                    viewModel = viewModel,
                    onQuickAction = onQuickAction,
                    onEditSidebarSlot = onEditSidebarSlot,
                    onEditKeymap = onEditKeymap,
                    onShowGestureGuide = onShowGestureGuide,
                    onShowKeyboard = onShowKeyboard,
                    onPushClipboard = onPushClipboard,
                    onOpenTool = onOpenTool,
                )
            }
        }
    }

    if (showEncryptionDialog && neg != null) {
        MtlsFingerprintDialog(
            transport = neg.transport,
            fingerprint = neg.peerCertFingerprint,
            privacySupported = state.privacy.supported,
            privacyMode = state.privacy.mode,
            hostMuteSupported = state.hostMute.supported,
            hostMuted = state.hostMute.muted,
            onDismiss = { showEncryptionDialog = false },
        )
    }
}

@Composable
private fun ControlCenterTabBody(
    activeTab: ControlPanelTab,
    isPortrait: Boolean,
    state: ActiveSessionState,
    diag: MediaDiagnosticsModel,
    prefs: InteractionPrefs,
    localCodecSupport: Map<String, LocalCodecSupport>,
    sidebarConfig: SidebarConfig,
    keymapState: KeyMapState,
    viewModel: SessionViewModel,
    onQuickAction: (QuickActionKind) -> Unit,
    onEditSidebarSlot: (SidebarSide, Int) -> Unit,
    onEditKeymap: () -> Unit,
    onShowGestureGuide: () -> Unit,
    onShowKeyboard: () -> Unit,
    onPushClipboard: () -> Unit,
    onOpenTool: (DeviceToolKind) -> Unit,
) {
    when (activeTab) {
        ControlPanelTab.Controls -> ControlsTabBody(
            state = state,
            prefs = prefs,
            sidebarConfig = sidebarConfig,
            keymapState = keymapState,
            isPortrait = isPortrait,
            viewModel = viewModel,
            onQuickAction = onQuickAction,
        )
        ControlPanelTab.Display -> DisplayTabBody(
            state = state,
            diag = diag,
            prefs = prefs,
            localCodecSupport = localCodecSupport,
            viewModel = viewModel,
        )
        ControlPanelTab.Keymap -> KeymapTabBody(
            sidebarConfig = sidebarConfig,
            keymapState = keymapState,
            viewModel = viewModel,
            onEditSidebarSlot = onEditSidebarSlot,
            onEditKeymap = onEditKeymap,
        )
        ControlPanelTab.Tools -> ToolsTabBody(
            onShowKeyboard = onShowKeyboard,
            onPushClipboard = onPushClipboard,
            onOpenTool = onOpenTool,
            onShowGestureGuide = onShowGestureGuide,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsTabBody(
    state: ActiveSessionState,
    prefs: InteractionPrefs,
    sidebarConfig: SidebarConfig,
    keymapState: KeyMapState,
    isPortrait: Boolean,
    viewModel: SessionViewModel,
    onQuickAction: (QuickActionKind) -> Unit,
) {
    val cols = 3
    val tiles = buildList {
        add(
            TileSpec(
                icon = if (prefs.touchMode == TouchInputMode.Trackpad) Icons.Rounded.Mouse else Icons.Rounded.TouchApp,
                label = prefs.touchMode.label,
                active = true,
                onClick = {
                    val next = if (prefs.touchMode == TouchInputMode.Trackpad) TouchInputMode.DirectTouch else TouchInputMode.Trackpad
                    viewModel.setTouchMode(next)
                },
            ),
        )
        add(
            TileSpec(
                icon = if (state.audioMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                label = stringResource(if (state.audioMuted) R.string.cp_sound_muted else R.string.cp_sound_on),
                active = !state.audioMuted,
                onClick = { viewModel.setAudioMuted(!state.audioMuted) },
            ),
        )
        add(
            TileSpec(
                icon = Icons.Rounded.ContentPaste,
                label = stringResource(if (state.clipboardEnabled) R.string.cp_clipboard_on else R.string.cp_clipboard_off),
                active = state.clipboardEnabled,
                onClick = { viewModel.setClipboardEnabled(!state.clipboardEnabled) },
            ),
        )
        add(
            TileSpec(
                icon = Icons.Rounded.Visibility,
                label = stringResource(if (state.watchOnly) R.string.tool_watch_mode else R.string.cp_mode_control),
                active = state.watchOnly,
                onClick = { viewModel.setWatchOnly(!state.watchOnly) },
            ),
        )
        add(
            TileSpec(
                icon = Icons.Rounded.ScreenRotation,
                label = when (prefs.orientationPref) {
                    OrientationPreference.AutoSensor -> stringResource(R.string.orientation_auto)
                    OrientationPreference.LandscapeFirst -> stringResource(R.string.orientation_landscape)
                    OrientationPreference.PortraitTrackpad -> stringResource(R.string.orientation_portrait)
                },
                active = prefs.orientationPref != OrientationPreference.AutoSensor,
                onClick = {
                    val entries = OrientationPreference.entries
                    viewModel.setOrientationPref(entries[(prefs.orientationPref.ordinal + 1) % entries.size])
                },
            ),
        )
        if (isPortrait) {
            add(
                TileSpec(
                    icon = Icons.Rounded.ViewAgenda,
                    label = stringResource(R.string.cp_portrait_prefix, prefs.portraitLayoutMode.label),
                    active = prefs.portraitLayoutMode != PortraitLayoutMode.Auto,
                    onClick = {
                        val modes = PortraitLayoutMode.entries
                        viewModel.setPortraitLayoutMode(modes[(prefs.portraitLayoutMode.ordinal + 1) % modes.size])
                    },
                ),
            )
        } else {
            add(
                TileSpec(
                    icon = Icons.Rounded.ViewSidebar,
                    label = stringResource(if (sidebarConfig.showLandscapeSidebars) R.string.cp_sidebars_on else R.string.cp_sidebars_off),
                    active = sidebarConfig.showLandscapeSidebars,
                    onClick = { viewModel.setShowLandscapeSidebars(!sidebarConfig.showLandscapeSidebars) },
                ),
            )
        }
        add(
            TileSpec(
                icon = Icons.Rounded.SportsEsports,
                label = stringResource(if (keymapState.enabled) R.string.cp_keymap_on else R.string.cp_keymap_off),
                active = keymapState.enabled,
                onClick = { viewModel.setKeymapEnabled(!keymapState.enabled) },
            ),
        )
        add(
            TileSpec(
                icon = Icons.Rounded.Speed,
                label = stringResource(if (prefs.showHud) R.string.cp_hud_on else R.string.cp_hud_off),
                active = prefs.showHud,
                onClick = { viewModel.setShowHud(!prefs.showHud) },
            ),
        )
        if (state.hostMute.supported) {
            add(
                TileSpec(
                    icon = if (state.hostMute.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    label = stringResource(if (state.hostMute.muted) R.string.cp_host_mute_on else R.string.cp_host_mute_off),
                    active = state.hostMute.muted,
                    onClick = { viewModel.setHostMute(!state.hostMute.muted) },
                ),
            )
        }
    }

    tiles.chunked(cols).forEach { rowTiles ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            rowTiles.forEach { t ->
                ControlTile(t.icon, t.label, t.active, Modifier.weight(1f), t.onClick)
            }
            repeat(cols - rowTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }

    if (state.privacy.supported) {
        val ctx = LocalContext.current
        val customImagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri: Uri? ->
            if (uri != null) {
                val b64 = encodeImageUriToBase64(ctx, uri)
                if (!b64.isNullOrBlank()) {
                    viewModel.setPrivacyMode("privacy", "custom", b64)
                    RabbitToast.success(AppStrings.get(R.string.cp_privacy_custom_applied, "已应用自定义防窥壁纸"))
                } else {
                    RabbitToast.error(AppStrings.get(R.string.cp_privacy_custom_failed, "无法读取所选图片"))
                }
            }
        }
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        val privacyModeLabel = when (state.privacy.mode) {
            "privacy" -> if (state.privacy.preset == "custom") {
                stringResource(R.string.cp_privacy_custom)
            } else {
                stringResource(R.string.cp_privacy_mode)
            }
            "black" -> stringResource(R.string.cp_privacy_black)
            else -> stringResource(R.string.cp_privacy_off)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.cp_privacy_curtain), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(privacyModeLabel, color = SessionPalette.accentText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "off" to stringResource(R.string.cp_privacy_off),
                "privacy" to stringResource(R.string.cp_privacy_mode),
                "black" to stringResource(R.string.cp_privacy_black),
            ).forEach { (modeId, label) ->
                SessionKeyChip(
                    label = label,
                    highlighted = state.privacy.mode == modeId && (modeId != "privacy" || state.privacy.preset != "custom"),
                ) {
                    viewModel.setPrivacyMode(modeId)
                }
            }
            SessionKeyChip(
                label = stringResource(R.string.cp_privacy_custom),
                highlighted = state.privacy.mode == "privacy" && state.privacy.preset == "custom",
            ) {
                customImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.cp_mouse_mode_label), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        val mmLabel = when (state.mouseMode) {
            "remote" -> stringResource(R.string.cp_mouse_mode_remote)
            "local" -> stringResource(R.string.cp_mouse_mode_local)
            else -> stringResource(R.string.cp_mouse_mode_smart)
        }
        Text(mmLabel, color = SessionPalette.accentText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "smart" to stringResource(R.string.cp_mouse_mode_smart),
            "remote" to stringResource(R.string.cp_mouse_mode_remote),
            "local" to stringResource(R.string.cp_mouse_mode_local),
        ).forEach { (modeKey, label) ->
            SessionKeyChip(
                label = label,
                highlighted = state.mouseMode == modeKey,
            ) {
                viewModel.setMouseMode(modeKey)
            }
        }
    }
    SessionSwitchRow(
        label = stringResource(R.string.cp_lock_on_disconnect),
        checked = prefs.autoLockRemoteOnDisconnect,
        onCheckedChange = viewModel::setAutoLockRemoteOnDisconnect,
    )

    val hostVer = state.remoteHostVersion
    if (hostVer != null && hostVer.isOwnerSession && hostVer.updateCapable) {
        var confirmHostUpdate by remember { mutableStateOf(false) }
        val checkInfo = state.remoteUpdateCheckInfo
        val prog = state.remoteUpdateProgress
        val updating = prog != null && prog.stage !in setOf("idle", "succeeded", "failed")
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = SessionPalette.accentText, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.cp_host_update_title), color = SessionPalette.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Text("v${hostVer.version} (${hostVer.arch})", color = SessionPalette.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        if (updating && prog != null) {
            LinearProgressIndicator(
                progress = { (prog.progressPct / 100f).coerceIn(0f, 1f) },
                color = SessionPalette.accent,
                trackColor = SessionPalette.border,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${prog.message.ifBlank { prog.stage }} (${prog.progressPct}%)",
                    color = SessionPalette.accentText,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                SessionKeyChip(stringResource(R.string.common_cancel)) {
                    viewModel.cancelRemoteHostUpdate()
                }
            }
        } else if (checkInfo?.hasUpdate == true && !checkInfo.latestVersion.isNullOrBlank()) {
            SessionKeyChip(stringResource(R.string.cp_host_update_apply, checkInfo.latestVersion)) {
                confirmHostUpdate = true
            }
        } else {
            val statusTxt = when {
                checkInfo?.checking == true -> stringResource(R.string.cp_host_update_checking)
                checkInfo?.error != null -> stringResource(R.string.cp_host_update_check_err, checkInfo.error)
                checkInfo?.latestVersion != null -> stringResource(R.string.cp_host_update_latest, hostVer.version)
                else -> stringResource(R.string.cp_host_update_check_btn)
            }
            SessionKeyChip(statusTxt, enabled = checkInfo?.checking != true) {
                viewModel.checkRemoteHostUpdate()
            }
        }
        if (prog != null && prog.stage == "failed" && !prog.error.isNullOrBlank()) {
            Text(prog.error, color = SessionPalette.dangerText, fontSize = 11.sp)
        } else if (prog != null && prog.stage == "succeeded") {
            Text(stringResource(R.string.cp_host_update_done), color = SessionPalette.success, fontSize = 11.sp)
        }
        if (confirmHostUpdate && checkInfo?.latestVersion != null) {
            com.rabbitcontrol.android.ui.components.RabbitDialog(
                title = stringResource(R.string.cp_host_update_confirm_title),
                message = stringResource(R.string.cp_host_update_confirm_msg, checkInfo.latestVersion),
                onDismissRequest = { confirmHostUpdate = false },
                dismissLabel = stringResource(R.string.common_cancel),
                confirmLabel = stringResource(R.string.cp_host_update_apply_btn),
                darkSessionStyle = true,
                onConfirm = {
                    confirmHostUpdate = false
                    viewModel.triggerRemoteHostUpdate()
                },
            )
        }
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

    // Flat Shortcut Chips Section
    Text(stringResource(R.string.cp_quick_actions), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            QuickActionKind.ZoomIn,
            QuickActionKind.ZoomOut,
            QuickActionKind.ResetZoom,
            QuickActionKind.ShowDesktop,
            QuickActionKind.AllWindows,
            QuickActionKind.SwitchWindow,
            QuickActionKind.Screenshot,
            QuickActionKind.TaskManager,
        ).forEach { action ->
            SessionKeyChip(action.shortLabel) { onQuickAction(action) }
        }
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

    // Flat Pointer Tuning Section
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_pointer_sensitivity), color = SessionPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "${String.format("%.2f", prefs.pointerSensitivity)}x",
                color = SessionPalette.accentText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Slider(
            value = prefs.pointerSensitivity,
            onValueChange = viewModel::setSensitivity,
            valueRange = 0.5f..2.5f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = SessionPalette.accent,
                inactiveTrackColor = SessionPalette.border,
            ),
        )
        SessionSwitchRow(stringResource(R.string.cp_natural_scroll), prefs.naturalScroll, viewModel::setNaturalScroll)
        SessionSwitchRow(stringResource(R.string.cp_direct_offset), prefs.directTouchOffset, viewModel::setDirectTouchOffset)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayTabBody(
    state: ActiveSessionState,
    diag: MediaDiagnosticsModel,
    prefs: InteractionPrefs,
    localCodecSupport: Map<String, LocalCodecSupport>,
    viewModel: SessionViewModel,
) {
    val dc = state.displayConfig
    val curW = dc?.width ?: state.negotiated?.width ?: 0
    val curH = dc?.height ?: state.negotiated?.height ?: 0
    val curFps = dc?.fps ?: state.negotiated?.fps ?: 60
    val activeBps = state.targetBitrateBps ?: state.negotiated?.bitrateBps
    val activeCodecId = normalizeVideoCodecId(diag.streamCodec)
        ?: normalizeVideoCodecId(dc?.codec)
        ?: normalizeVideoCodecId(diag.codecName)
    val activeCodecLabel = formatVideoCodecLabel(activeCodecId) ?: "--"
    val remoteSupported = remember(dc?.supportedCodecs) {
        dc?.supportedCodecs?.mapNotNull { normalizeVideoCodecId(it) }?.toSet().orEmpty()
    }
    val decoderActive = diag.codecName.isNotBlank() || diag.decodedFrames > 0L || diag.renderedFrames > 0L
    val presetSelection = remember(dc?.presets, curW, curH) {
        selectCommonResolutionPresets(dc?.presets.orEmpty(), curW, curH, maxCommon = 5)
    }
    var showMoreResolutions by remember { mutableStateOf(false) }
    var showCreateVirtualDisplay by remember { mutableStateOf(false) }

    SessionSwitchRow(stringResource(R.string.cp_floating_hud), prefs.showHud, viewModel::setShowHud)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SessionPalette.surface)
            .border(1.dp, SessionPalette.border, RoundedCornerShape(10.dp))
            .clickable(onClick = viewModel::requestIdr)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = SessionPalette.accentText, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.cp_request_idr), color = SessionPalette.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.cp_request_now), color = SessionPalette.accentText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
    Text(
        text = stringResource(R.string.cp_connection_mode),
        color = SessionPalette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SessionPalette.surface)
            .border(1.dp, SessionPalette.border, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ConnectionModePref.entries.forEach { modeOption ->
            val selected = prefs.connectionMode == modeOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) SessionPalette.accent else Color.Transparent)
                    .clickable {
                        if (!selected) {
                            viewModel.setConnectionMode(modeOption)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = modeOption.label,
                    color = if (selected) Color.White else SessionPalette.muted,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.cp_connection_mode_hint),
        color = SessionPalette.muted,
        fontSize = 11.sp,
    )

    val adv = dc?.advisory
    if (adv != null && adv.level != "optimal") {
        val recW = adv.recommendedWidth
        val recH = adv.recommendedHeight
        val advisoryText = when {
            adv.reason == "h264_suggest_hevc" || adv.canEnableHevc ->
                stringResource(R.string.cp_advisory_h264_hevc)
            adv.reason == "h264_no_hevc" && recW != null && recH != null ->
                stringResource(R.string.cp_advisory_h264_res, recW, recH)
            recW != null && recH != null ->
                stringResource(R.string.cp_advisory_hevc_res, recW, recH)
            else -> null
        }
        if (advisoryText != null) {
            HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = advisoryText,
                    color = SessionPalette.warning,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                if (adv.canEnableHevc) {
                    SessionKeyChip(stringResource(R.string.cp_advisory_switch_hevc)) {
                        viewModel.setCodec("hevc")
                    }
                } else if (recW != null && recH != null) {
                    SessionKeyChip(stringResource(R.string.cp_advisory_switch_res, recW, recH)) {
                        viewModel.setStreamParams(recW, recH, curFps)
                    }
                }
            }
        }
    }

    if (state.displays.isNotEmpty() || !state.deviceOs.equals("android", ignoreCase = true)) {
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.cp_displays), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.common_refresh),
                tint = SessionPalette.muted,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = viewModel::listDisplays)
                    .padding(2.dp),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.displays.forEach { disp ->
                val isActive = disp.id == state.activeDisplayId
                val title = disp.name.ifBlank { "#${disp.id}" }
                val shape = RoundedCornerShape(8.dp)
                Row(
                    modifier = Modifier
                        .heightIn(min = 32.dp)
                        .clip(shape)
                        .background(if (isActive) SessionPalette.accent else SessionPalette.surface)
                        .border(
                            1.dp,
                            if (isActive) SessionPalette.accent else SessionPalette.border,
                            shape,
                        )
                        .clickable { viewModel.switchDisplay(disp.id) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Monitor,
                        contentDescription = null,
                        tint = if (isActive) Color.White else SessionPalette.accentText,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "$title · ${disp.width}×${disp.height}",
                        color = if (isActive) Color.White else SessionPalette.text,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (disp.isVirtual) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isActive) Color.White.copy(alpha = 0.22f)
                                    else SessionPalette.surfaceActive,
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.cp_display_virtual_tag),
                                color = if (isActive) Color.White else SessionPalette.muted,
                                fontSize = 9.sp,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cp_vdisp_remove_cd),
                            tint = if (isActive) Color.White else SessionPalette.muted,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { viewModel.removeVirtualDisplay(disp.id) }
                                .padding(1.dp),
                        )
                    }
                }
            }
            SessionKeyChip(stringResource(R.string.cp_add_virtual_display)) {
                showCreateVirtualDisplay = true
            }
        }
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.cp_video_codec), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(activeCodecLabel, color = SessionPalette.accentText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
    val codecChoices = listOf(
        "h264" to "H.264",
        "hevc" to "HEVC",
        "av1" to "AV1",
    )
    var hasDisabledCodec = false
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        codecChoices.forEach { (codecId, label) ->
            val hostSupported = if (remoteSupported.isNotEmpty()) {
                codecId in remoteSupported
            } else {
                codecId == "h264"
            }
            val localSupported = when (codecId) {
                "h264" -> true
                "hevc" -> dc?.clientSupportsHevc == true || localCodecSupport["hevc"]?.hasHardware == true
                "av1" -> localCodecSupport["av1"]?.hasHardware == true
                else -> false
            }
            val enabled = (hostSupported && localSupported) || (activeCodecId == codecId)
            if (!enabled) hasDisabledCodec = true
            SessionKeyChip(
                label = label,
                highlighted = activeCodecId == codecId,
                enabled = enabled,
            ) {
                viewModel.setCodec(codecId)
            }
        }
    }
    if (hasDisabledCodec) {
        Text(
            text = stringResource(R.string.cp_codec_unsupported_hint),
            color = SessionPalette.muted,
            fontSize = 11.sp,
        )
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.cp_decode_mode), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        val statusLabel = when {
            prefs.preferHardwareDecode && decoderActive && !diag.hardware -> stringResource(R.string.cp_decode_fallback_sw)
            decoderActive && diag.hardware -> stringResource(R.string.cp_hw_decode)
            decoderActive && !diag.hardware -> stringResource(R.string.cp_sw_decode)
            prefs.preferHardwareDecode -> stringResource(R.string.cp_hw_decode)
            else -> stringResource(R.string.cp_sw_decode)
        }
        Text(statusLabel, color = SessionPalette.accentText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SessionKeyChip(
            label = stringResource(R.string.cp_decode_hw),
            highlighted = prefs.preferHardwareDecode,
        ) {
            viewModel.setPreferHardwareDecode(true)
        }
        SessionKeyChip(
            label = stringResource(R.string.cp_decode_sw),
            highlighted = !prefs.preferHardwareDecode,
        ) {
            viewModel.setPreferHardwareDecode(false)
        }
    }

    if (presetSelection.common.isNotEmpty()) {
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.cp_resolution), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (curW > 0 && curH > 0) {
                Text("${curW}×${curH}", color = SessionPalette.accentText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presetSelection.common.forEach { preset ->
                val active = curW == preset.width && curH == preset.height
                SessionKeyChip(formatCompactPresetLabel(preset), highlighted = active) {
                    viewModel.setStreamParams(preset.width, preset.height, curFps)
                }
            }
            if (presetSelection.extra.isNotEmpty()) {
                SessionKeyChip(
                    label = if (showMoreResolutions) {
                        stringResource(R.string.common_collapse)
                    } else {
                        stringResource(R.string.cp_more_res, presetSelection.extra.size)
                    },
                    highlighted = showMoreResolutions || presetSelection.extra.any { it.width == curW && it.height == curH },
                ) {
                    showMoreResolutions = !showMoreResolutions
                }
            }
        }
        if (showMoreResolutions && presetSelection.extra.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presetSelection.extra.forEach { preset ->
                    val active = curW == preset.width && curH == preset.height
                    SessionKeyChip(formatCompactPresetLabel(preset), highlighted = active) {
                        viewModel.setStreamParams(preset.width, preset.height, curFps)
                    }
                }
            }
        }
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.cp_fps_limit), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text("${curFps}fps", color = SessionPalette.accentText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
    val maxFpsCap = maxOf(dc?.maxFps ?: 120, curFps, 60)
    val fpsOptions = listOf(30, 60, 90, 120, 144, 240).filter { it <= maxFpsCap }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        fpsOptions.forEach { fps ->
            SessionKeyChip("${fps}fps", highlighted = curFps == fps) {
                viewModel.setStreamParams(curW, curH, fps)
            }
        }
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.cp_bitrate), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            text = if (activeBps == null || activeBps <= 0) stringResource(R.string.cp_bitrate_auto) else "${activeBps / 1_000_000}M",
            color = SessionPalette.accentText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            0 to stringResource(R.string.cp_bitrate_auto),
            8_000_000 to "8M",
            14_000_000 to "14M",
            30_000_000 to "30M",
            60_000_000 to "60M",
            100_000_000 to "100M",
            150_000_000 to "150M",
        ).forEach { (bps, lbl) ->
            val selected = if (bps == 0) (activeBps == null || activeBps == 0) else activeBps == bps
            SessionKeyChip(lbl, highlighted = selected) {
                viewModel.setBitrate(bps)
            }
        }
    }

    if (showCreateVirtualDisplay) {
        CreateVirtualDisplayDialog(
            onDismiss = { showCreateVirtualDisplay = false },
            onCreate = { w, h, hidpi ->
                viewModel.createVirtualDisplay(w, h, hidpi)
                showCreateVirtualDisplay = false
            },
        )
    }
}

@Composable
private fun KeymapTabBody(
    sidebarConfig: SidebarConfig,
    keymapState: KeyMapState,
    viewModel: SessionViewModel,
    onEditSidebarSlot: (SidebarSide, Int) -> Unit,
    onEditKeymap: () -> Unit,
) {
    // Right 3 Customizable Buttons
    Text(stringResource(R.string.cp_right_slots), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        (0 until 3).forEach { idx ->
            val act = sidebarConfig.rightSlots.getOrElse(idx) { QuickActionKind.Empty }
            ControlTile(
                icon = quickActionIcon(act),
                label = "${idx + 1}. ${act.shortLabel}",
                active = act != QuickActionKind.Empty,
                modifier = Modifier.weight(1f),
            ) { onEditSidebarSlot(SidebarSide.Right, idx) }
        }
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

    // Left 3 Hold/Shortcut Zones
    Text(stringResource(R.string.cp_left_slots), color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        (0 until 3).forEach { idx ->
            val act = sidebarConfig.leftSlots.getOrElse(idx) { QuickActionKind.Empty }
            ControlTile(
                icon = quickActionIcon(act),
                label = "${idx + 1}. ${act.shortLabel}",
                active = act != QuickActionKind.Empty,
                modifier = Modifier.weight(1f),
            ) { onEditSidebarSlot(SidebarSide.Left, idx) }
        }
    }

    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

    // Virtual Keymap Overlay Settings
    SessionSwitchRow(stringResource(R.string.cp_enable_keymap), keymapState.enabled, viewModel::setKeymapEnabled)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        keymapState.profiles.forEach { profile ->
            val active = profile.id == keymapState.activeProfileId
            SessionKeyChip(profile.name, highlighted = active) {
                viewModel.selectKeymapProfile(profile.id)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.cp_opacity), color = SessionPalette.muted, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Slider(
            value = keymapState.opacity,
            onValueChange = viewModel::setKeymapOpacity,
            valueRange = 0.25f..0.85f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = SessionPalette.accent,
                inactiveTrackColor = SessionPalette.border,
            ),
            modifier = Modifier.weight(1f),
        )
        Text("${(keymapState.opacity * 100).roundToInt()}%", color = SessionPalette.accentText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
    OutlinedButton(
        onClick = onEditKeymap,
        shape = RoundedCornerShape(RabbitRadius.Md),
        border = BorderStroke(1.dp, SessionPalette.accent.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SessionPalette.surfaceActive,
            contentColor = SessionPalette.text,
        ),
        modifier = Modifier.fillMaxWidth().height(42.dp),
    ) {
        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.cp_edit_keymap), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToolsTabBody(
    onShowKeyboard: () -> Unit,
    onPushClipboard: () -> Unit,
    onOpenTool: (DeviceToolKind) -> Unit,
    onShowGestureGuide: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SessionPalette.surface)
            .border(1.dp, SessionPalette.border, RoundedCornerShape(14.dp)),
    ) {
        SessionToolRow(Icons.Rounded.Keyboard, stringResource(R.string.cp_tool_kbd_title), stringResource(R.string.cp_tool_kbd_sub), onShowKeyboard)
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.ContentPaste, stringResource(R.string.cp_tool_clip_title), stringResource(R.string.cp_tool_clip_sub), onPushClipboard)
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.FolderOpen, stringResource(R.string.tool_file_transfer), stringResource(R.string.cp_tool_ft_sub)) {
            onOpenTool(DeviceToolKind.FileTransfer)
        }
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.Terminal, stringResource(R.string.cp_tool_term_title), stringResource(R.string.cp_tool_term_sub)) {
            onOpenTool(DeviceToolKind.Terminal)
        }
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.Cable, stringResource(R.string.tool_port_map), stringResource(R.string.cp_tool_pm_sub)) {
            onOpenTool(DeviceToolKind.PortMap)
        }
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.HelpOutline, stringResource(R.string.cp_guide), stringResource(R.string.cp_tool_guide_sub), onShowGestureGuide)
    }
}

private data class TileSpec(
    val icon: ImageVector,
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ControlTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(RabbitRadius.Md)
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(if (active) SessionPalette.surfaceActive else SessionPalette.surface)
            .border(
                width = 1.dp,
                color = if (active) SessionPalette.accent else SessionPalette.border,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) SessionPalette.accentText else SessionPalette.muted,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = SessionPalette.text,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SessionSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 1.dp),
    ) {
        Text(label, color = SessionPalette.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SessionPalette.accent,
                uncheckedThumbColor = SessionPalette.muted,
                uncheckedTrackColor = SessionPalette.trackpad,
                uncheckedBorderColor = SessionPalette.border,
            ),
        )
    }
}

@Composable
private fun SessionToolRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SessionPalette.surfaceActive),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = SessionPalette.accentText, modifier = Modifier.size(17.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SessionPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SessionPalette.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = SessionPalette.muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MtlsFingerprintDialog(
    transport: String,
    fingerprint: String?,
    privacySupported: Boolean,
    privacyMode: String,
    hostMuteSupported: Boolean,
    hostMuted: Boolean,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.cp_mtls_fingerprint_copied)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(RabbitRadius.Lg),
            color = SessionPalette.chromeElevated,
            border = BorderStroke(1.dp, SessionPalette.border),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = SessionPalette.success,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.cp_mtls_title),
                        color = SessionPalette.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = stringResource(R.string.cp_mtls_status_active),
                    color = SessionPalette.muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.cp_mtls_transport_label),
                        color = SessionPalette.muted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = transport,
                        color = SessionPalette.accentText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (privacySupported) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.cp_privacy_curtain),
                            color = SessionPalette.muted,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = when (privacyMode) {
                                "privacy" -> stringResource(R.string.cp_privacy_mode)
                                "black" -> stringResource(R.string.cp_privacy_black)
                                else -> stringResource(R.string.cp_privacy_off)
                            },
                            color = SessionPalette.text,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (hostMuteSupported) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.cp_host_mute_title),
                            color = SessionPalette.muted,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = stringResource(if (hostMuted) R.string.cp_host_mute_on else R.string.cp_host_mute_off),
                            color = SessionPalette.text,
                            fontSize = 12.sp,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.cp_mtls_fingerprint_label),
                    color = SessionPalette.muted,
                    fontSize = 12.sp,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SessionPalette.surface)
                        .border(1.dp, SessionPalette.border, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                ) {
                    Text(
                        text = fingerprint?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.cp_mtls_fingerprint_missing),
                        color = SessionPalette.text,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!fingerprint.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(fingerprint))
                                RabbitToast.success(copiedMsg)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = null,
                                tint = SessionPalette.accentText,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.cp_mtls_copy_fingerprint),
                                color = SessionPalette.accentText,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.common_close),
                            color = SessionPalette.text,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateVirtualDisplayDialog(
    onDismiss: () -> Unit,
    onCreate: (width: Int, height: Int, hidpi: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val dm = context.resources.displayMetrics
    val phoneRatioSize = remember(dm.widthPixels, dm.heightPixels) {
        val longSide = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
        val shortSide = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1)
        val targetW = 1920
        val rawH = ((1920.0 * shortSide.toDouble()) / longSide.toDouble()).roundToInt().coerceIn(720, 1440)
        val evenH = (rawH / 2) * 2
        targetW to evenH
    }
    var widthText by remember { mutableStateOf("1920") }
    var heightText by remember { mutableStateOf("1080") }
    var hidpi by remember { mutableStateOf(false) }

    val parsedW = widthText.trim().toIntOrNull()
    val parsedH = heightText.trim().toIntOrNull()
    val validSize = parsedW != null && parsedW in 640..3840 && parsedH != null && parsedH in 480..2160

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(RabbitRadius.Lg),
            color = SessionPalette.chromeElevated,
            border = BorderStroke(1.dp, SessionPalette.border),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.cp_vdisp_title),
                    color = SessionPalette.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.cp_vdisp_presets),
                    color = SessionPalette.muted,
                    fontSize = 12.sp,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SessionKeyChip(
                        label = "1920×1080",
                        highlighted = widthText == "1920" && heightText == "1080",
                    ) {
                        widthText = "1920"
                        heightText = "1080"
                    }
                    SessionKeyChip(
                        label = "2560×1440",
                        highlighted = widthText == "2560" && heightText == "1440",
                    ) {
                        widthText = "2560"
                        heightText = "1440"
                    }
                    SessionKeyChip(
                        label = stringResource(R.string.cp_vdisp_match_phone) +
                            " (${phoneRatioSize.first}×${phoneRatioSize.second})",
                        highlighted = widthText == phoneRatioSize.first.toString() &&
                            heightText == phoneRatioSize.second.toString(),
                    ) {
                        widthText = phoneRatioSize.first.toString()
                        heightText = phoneRatioSize.second.toString()
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SessionPalette.text,
                        unfocusedTextColor = SessionPalette.text,
                        focusedBorderColor = SessionPalette.accent,
                        unfocusedBorderColor = SessionPalette.border,
                        focusedLabelColor = SessionPalette.accentText,
                        unfocusedLabelColor = SessionPalette.muted,
                    )
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { next -> if (next.length <= 4 && next.all { it.isDigit() }) widthText = next },
                        label = { Text(stringResource(R.string.cp_vdisp_width), fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { next -> if (next.length <= 4 && next.all { it.isDigit() }) heightText = next },
                        label = { Text(stringResource(R.string.cp_vdisp_height), fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!validSize) {
                    Text(
                        text = stringResource(R.string.cp_vdisp_invalid_size),
                        color = SessionPalette.dangerText,
                        fontSize = 11.sp,
                    )
                }
                SessionSwitchRow(
                    label = stringResource(R.string.cp_vdisp_hidpi),
                    checked = hidpi,
                    onCheckedChange = { hidpi = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.common_cancel),
                            color = SessionPalette.muted,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        enabled = validSize,
                        onClick = {
                            val w = (((parsedW ?: 1920).coerceIn(640, 3840)) / 2) * 2
                            val h = (((parsedH ?: 1080).coerceIn(480, 2160)) / 2) * 2
                            onCreate(w, h, hidpi)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = SessionPalette.accentText,
                            disabledContentColor = SessionPalette.muted,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.cp_vdisp_create),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun encodeImageUriToBase64(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
        val maxSide = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
        var sample = 1
        while (maxSide / sample > 1920) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeByteArray(
            rawBytes,
            0,
            rawBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val scale = minOf(1f, 1280f / maxOf(decoded.width, decoded.height).coerceAtLeast(1))
        val targetBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        val baos = java.io.ByteArrayOutputStream()
        targetBitmap.compress(Bitmap.CompressFormat.JPEG, 78, baos)
        if (targetBitmap !== decoded) targetBitmap.recycle()
        decoded.recycle()
        val jpeg = baos.toByteArray()
        if (jpeg.size > 170 * 1024) return null
        Base64.encodeToString(jpeg, Base64.NO_WRAP)
    }.getOrNull()
}
