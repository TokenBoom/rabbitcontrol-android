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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.InteractionPrefs
import io.rabbitcontrol.android.core.data.KeyMapState
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.PortraitLayoutMode
import io.rabbitcontrol.android.core.data.QuickActionKind
import io.rabbitcontrol.android.core.data.SidebarConfig
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.SessionPalette
import kotlin.math.roundToInt

enum class ControlPanelTab(val label: String, val icon: ImageVector) {
    Controls("操控", Icons.Rounded.Tune),
    Display("画质", Icons.Rounded.Speed),
    Keymap("键位", Icons.Rounded.SportsEsports),
    Tools("工具", Icons.Rounded.FolderOpen),
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
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val sidebarConfig by viewModel.sidebarConfig.collectAsStateWithLifecycle()
    val keymapState by viewModel.keymapState.collectAsStateWithLifecycle()
    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

    val neg = state.negotiated
    val diag = state.media
    val devTitle = state.deviceName.ifBlank { "控制中心" }
    val telemetry = if (neg != null) {
        val hwTag = if (diag.hardware) "硬解" else "软解"
        "${neg.width}×${neg.height} · ${neg.fps}fps · ${neg.transport} · $hwTag"
    } else {
        "已就绪"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SessionPalette.scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = if (isPortrait) Alignment.BottomCenter else Alignment.Center,
    ) {
        if (!isPortrait) {
            // Landscape: Floating Split-Rail Control Center (Left Rail + Wide Right Canvas)
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .widthIn(min = 480.dp, max = 610.dp)
                    .fillMaxHeight(0.88f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(RabbitRadius.Xl),
                color = SessionPalette.chrome,
                tonalElevation = 0.dp,
                shadowElevation = RabbitElevation.Sheet,
                border = BorderStroke(1.dp, SessionPalette.border),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Navigation Rail
                    Column(
                        modifier = Modifier
                            .width(114.dp)
                            .fillMaxHeight()
                            .background(SessionPalette.trackpad)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ControlPanelTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            val itemShape = RoundedCornerShape(10.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(itemShape)
                                    .background(if (selected) SessionPalette.surfaceActive else Color.Transparent)
                                    .then(
                                        if (selected) Modifier.border(1.dp, SessionPalette.accent, itemShape)
                                        else Modifier,
                                    )
                                    .clickable { activeTab = tab }
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (selected) SessionPalette.accentText else SessionPalette.muted,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tab.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (selected) SessionPalette.text else SessionPalette.muted,
                                    maxLines = 1,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(SessionPalette.surface)
                                .border(1.dp, SessionPalette.border, RoundedCornerShape(9.dp))
                                .clickable(onClick = onShowGestureGuide)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.HelpOutline, null, tint = SessionPalette.accentText, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("使用教程", color = SessionPalette.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(SessionPalette.surface)
                                .border(1.dp, SessionPalette.border, RoundedCornerShape(9.dp))
                                .clickable(onClick = viewModel::lockRemoteScreen)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = SessionPalette.text, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("锁屏", color = SessionPalette.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(SessionPalette.danger)
                                .clickable(onClick = onRequestDisconnect)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("断开连接", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(SessionPalette.border),
                    )

                    // Right Wide Content Canvas
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(start = 16.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = devTitle,
                                color = SessionPalette.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = telemetry,
                                color = SessionPalette.muted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, "关闭", tint = SessionPalette.text, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ControlCenterTabBody(
                                activeTab = activeTab,
                                isPortrait = false,
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
        } else {
            // Portrait: Compact Bottom Sheet with Horizontal Pill Tabs
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(topStart = RabbitRadius.Xl, topEnd = RabbitRadius.Xl),
                color = SessionPalette.chrome,
                tonalElevation = 0.dp,
                shadowElevation = RabbitElevation.Sheet,
                border = BorderStroke(1.dp, SessionPalette.border),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .align(Alignment.CenterHorizontally)
                            .width(32.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SessionPalette.border),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = devTitle,
                                color = SessionPalette.text,
                                fontSize = 15.sp,
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
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SessionPalette.surface)
                                .border(1.dp, SessionPalette.border, RoundedCornerShape(8.dp))
                                .clickable(onClick = onShowGestureGuide),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.HelpOutline, "使用教程", tint = SessionPalette.accentText, modifier = Modifier.size(16.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SessionPalette.surface)
                                .border(1.dp, SessionPalette.border, RoundedCornerShape(8.dp))
                                .clickable(onClick = viewModel::lockRemoteScreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Lock, "锁屏", tint = SessionPalette.text, modifier = Modifier.size(16.dp))
                        }
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SessionPalette.danger)
                                .clickable(onClick = onRequestDisconnect)
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("断开", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Close, "关闭", tint = SessionPalette.text, modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ControlPanelTab.entries.forEach { tab ->
                            SessionKeyChip(tab.label, highlighted = tab == activeTab) {
                                activeTab = tab
                            }
                        }
                    }

                    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ControlCenterTabBody(
                            activeTab = activeTab,
                            isPortrait = true,
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
    val cols = if (isPortrait) 3 else 4
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
            label = if (state.audioMuted) "已静音" else "声音开",
            active = !state.audioMuted,
            onClick = { viewModel.setAudioMuted(!state.audioMuted) },
        ),
        TileSpec(
            icon = Icons.Rounded.ContentPaste,
            label = if (state.clipboardEnabled) "剪贴板" else "剪贴板关",
            active = state.clipboardEnabled,
            onClick = { viewModel.setClipboardEnabled(!state.clipboardEnabled) },
        ),
        TileSpec(
            icon = Icons.Rounded.Visibility,
            label = if (state.watchOnly) "观看模式" else "控制模式",
            active = state.watchOnly,
            onClick = { viewModel.setWatchOnly(!state.watchOnly) },
        ),
        TileSpec(
            icon = Icons.Rounded.ScreenRotation,
            label = when (prefs.orientationPref) {
                OrientationPreference.AutoSensor -> "自动旋转"
                OrientationPreference.LandscapeFirst -> "锁定横屏"
                OrientationPreference.PortraitTrackpad -> "锁定竖屏"
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
                label = "竖屏·${prefs.portraitLayoutMode.label}",
                active = prefs.portraitLayoutMode != PortraitLayoutMode.Auto,
                onClick = {
                    val modes = PortraitLayoutMode.entries
                    viewModel.setPortraitLayoutMode(modes[(prefs.portraitLayoutMode.ordinal + 1) % modes.size])
                },
            )
        } else {
            TileSpec(
                icon = Icons.Rounded.ViewSidebar,
                label = if (sidebarConfig.showLandscapeSidebars) "侧边栏" else "纯全屏",
                active = sidebarConfig.showLandscapeSidebars,
                onClick = { viewModel.setShowLandscapeSidebars(!sidebarConfig.showLandscapeSidebars) },
            )
        },
        TileSpec(
            icon = Icons.Rounded.SportsEsports,
            label = if (keymapState.enabled) "按键开" else "按键映射",
            active = keymapState.enabled,
            onClick = { viewModel.setKeymapEnabled(!keymapState.enabled) },
        ),
        TileSpec(
            icon = Icons.Rounded.Speed,
            label = if (prefs.showHud) "HUD 开" else "HUD 关",
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
    Text("快捷操作", color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
            Text("指针灵敏度", color = SessionPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
        SessionSwitchRow("自然滚动方向", prefs.naturalScroll, viewModel::setNaturalScroll)
        SessionSwitchRow("直点指尖微偏置", prefs.directTouchOffset, viewModel::setDirectTouchOffset)
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

    SessionSwitchRow("悬浮性能 HUD", prefs.showHud, viewModel::setShowHud)
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
        Text("刷新画面关键帧 (IDR)", color = SessionPalette.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("立即请求", color = SessionPalette.accentText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }

    if (presetSelection.common.isNotEmpty()) {
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("分辨率", color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                    label = if (showMoreResolutions) "收起" else "更多 (${presetSelection.extra.size})",
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
        Text("帧率上限", color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
        Text("目标带宽", color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    Text("右侧 3 个自定义按钮（点击更换）", color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    Text("左侧 3 个握持快捷区（点击更换）", color = SessionPalette.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    SessionSwitchRow("开启屏幕虚拟按键映射", keymapState.enabled, viewModel::setKeymapEnabled)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        keymapState.profiles.forEach { profile ->
            val active = profile.id == keymapState.activeProfileId
            SessionKeyChip(profile.name, highlighted = active) {
                viewModel.selectKeymapProfile(profile.id)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("透明度", color = SessionPalette.muted, fontSize = 12.sp)
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
        Text("进入拖拽布局编辑器（移动/大小/增删）", fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
        SessionToolRow(Icons.Rounded.Keyboard, "键盘扩展条", "打开软键盘与常用快捷组合键", onShowKeyboard)
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.ContentPaste, "发送手机剪贴板", "将当前剪贴板文本同步到远端", onPushClipboard)
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.FolderOpen, "文件传输", "浏览与收发远端计算机文件") {
            onOpenTool(DeviceToolKind.FileTransfer)
        }
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.Terminal, "远程终端", "在远端主机开启命令行会话") {
            onOpenTool(DeviceToolKind.Terminal)
        }
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.Cable, "端口映射", "将远端端口安全转发到本地") {
            onOpenTool(DeviceToolKind.PortMap)
        }
        HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
        SessionToolRow(Icons.Rounded.HelpOutline, "使用教程", "再次查看手势与侧边栏操作指南", onShowGestureGuide)
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
