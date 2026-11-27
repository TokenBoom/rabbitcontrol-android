package io.rabbitcontrol.android.ui.session

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewSidebar
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.InteractionPrefs
import io.rabbitcontrol.android.core.data.KeyMapState
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.PortraitLayoutMode
import io.rabbitcontrol.android.core.data.QuickActionKind
import io.rabbitcontrol.android.core.data.SidebarConfig
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.SessionPalette
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
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val diag by viewModel.media.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val sidebarConfig by viewModel.sidebarConfig.collectAsStateWithLifecycle()
    val keymapState by viewModel.keymapState.collectAsStateWithLifecycle()
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

    val neg = state.negotiated
    val devTitle = state.deviceName.ifBlank { stringResource(R.string.cp_title_default) }
    val telemetry = if (neg != null) {
        val hwTag = stringResource(if (diag.hardware) R.string.cp_hw_decode else R.string.cp_sw_decode)
        "${neg.width}×${neg.height} · ${neg.fps}fps · ${neg.transport} · $hwTag"
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
                        .fillMaxWidth(0.45f)
                        .widthIn(min = 320.dp, max = 360.dp)
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

            // Unified Header Row: Device Title + Telemetry + Guide + Lock + Close
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
                    prefs = prefs,
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
}

@Composable
private fun ControlCenterTabBody(
    activeTab: ControlPanelTab,
    isPortrait: Boolean,
    state: ActiveSessionState,
    prefs: InteractionPrefs,
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
            prefs = prefs,
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
    val tiles = listOf(
        TileSpec(
            icon = if (prefs.touchMode == TouchInputMode.Trackpad) Icons.Rounded.Mouse else Icons.Rounded.TouchApp,
            label = prefs.touchMode.label,
            active = true,
            onClick = {
                val next = if (prefs.touchMode == TouchInputMode.Trackpad) TouchInputMode.DirectTouch else TouchInputMode.Trackpad
                viewModel.setTouchMode(next)
            },
        ),
        TileSpec(
            icon = if (state.audioMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            label = stringResource(if (state.audioMuted) R.string.cp_sound_muted else R.string.cp_sound_on),
            active = !state.audioMuted,
            onClick = { viewModel.setAudioMuted(!state.audioMuted) },
        ),
        TileSpec(
            icon = Icons.Rounded.ContentPaste,
            label = stringResource(if (state.clipboardEnabled) R.string.cp_clipboard_on else R.string.cp_clipboard_off),
            active = state.clipboardEnabled,
            onClick = { viewModel.setClipboardEnabled(!state.clipboardEnabled) },
        ),
        TileSpec(
            icon = Icons.Rounded.Visibility,
            label = stringResource(if (state.watchOnly) R.string.tool_watch_mode else R.string.cp_mode_control),
            active = state.watchOnly,
            onClick = { viewModel.setWatchOnly(!state.watchOnly) },
        ),
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
        if (isPortrait) {
            TileSpec(
                icon = Icons.Rounded.ViewAgenda,
                label = stringResource(R.string.cp_portrait_prefix, prefs.portraitLayoutMode.label),
                active = prefs.portraitLayoutMode != PortraitLayoutMode.Auto,
                onClick = {
                    val modes = PortraitLayoutMode.entries
                    viewModel.setPortraitLayoutMode(modes[(prefs.portraitLayoutMode.ordinal + 1) % modes.size])
                },
            )
        } else {
            TileSpec(
                icon = Icons.Rounded.ViewSidebar,
                label = stringResource(if (sidebarConfig.showLandscapeSidebars) R.string.cp_sidebars_on else R.string.cp_sidebars_off),
                active = sidebarConfig.showLandscapeSidebars,
                onClick = { viewModel.setShowLandscapeSidebars(!sidebarConfig.showLandscapeSidebars) },
            )
        },
        TileSpec(
            icon = Icons.Rounded.SportsEsports,
            label = stringResource(if (keymapState.enabled) R.string.cp_keymap_on else R.string.cp_keymap_off),
            active = keymapState.enabled,
            onClick = { viewModel.setKeymapEnabled(!keymapState.enabled) },
        ),
        TileSpec(
            icon = Icons.Rounded.Speed,
            label = stringResource(if (prefs.showHud) R.string.cp_hud_on else R.string.cp_hud_off),
            active = prefs.showHud,
            onClick = { viewModel.setShowHud(!prefs.showHud) },
        ),
    )

    tiles.chunked(cols).forEach { rowTiles ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            rowTiles.forEach { t ->
                ControlTile(t.icon, t.label, t.active, Modifier.weight(1f), t.onClick)
            }
            repeat(cols - rowTiles.size) { Spacer(modifier = Modifier.weight(1f)) }
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
    prefs: InteractionPrefs,
    viewModel: SessionViewModel,
) {
    val dc = state.displayConfig
    val curW = dc?.width ?: state.negotiated?.width ?: 0
    val curH = dc?.height ?: state.negotiated?.height ?: 0
    val curFps = dc?.fps ?: state.negotiated?.fps ?: 60
    val activeBps = state.targetBitrateBps ?: state.negotiated?.bitrateBps
    val presetSelection = remember(dc?.presets, curW, curH) {
        selectCommonResolutionPresets(dc?.presets.orEmpty(), curW, curH, maxCommon = 5)
    }
    var showMoreResolutions by remember { mutableStateOf(false) }

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
    val fpsOptions = listOf(30, 60, 90, 120).filter { it <= maxFpsCap }
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
        if (activeBps != null && activeBps > 0) {
            Text("${activeBps / 1_000_000}M", color = SessionPalette.accentText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            4_000_000 to "4M",
            8_000_000 to "8M",
            14_000_000 to "14M",
            24_000_000 to "24M",
            40_000_000 to "40M",
        ).forEach { (bps, lbl) ->
            SessionKeyChip(lbl, highlighted = activeBps == bps) {
                viewModel.setBitrate(bps)
            }
        }
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
