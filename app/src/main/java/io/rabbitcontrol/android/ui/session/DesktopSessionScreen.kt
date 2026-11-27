package io.rabbitcontrol.android.ui.session

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rabbitcontrol.android.error.RcErrorCodes
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.PortraitLayoutMode
import io.rabbitcontrol.android.core.data.QuickActionKind
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.input.ImeInputBridge
import io.rabbitcontrol.android.core.input.PrecisionTouchLayer
import io.rabbitcontrol.android.core.input.ScancodeMapper
import io.rabbitcontrol.android.core.nativebridge.HudSnapshotModel
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.core.video.NativeVideoStageView
import io.rabbitcontrol.android.session.Core
import io.rabbitcontrol.android.ui.components.RabbitDialog
import io.rabbitcontrol.android.ui.components.RcErrorHub
import io.rabbitcontrol.android.ui.theme.RabbitElevation
import io.rabbitcontrol.android.ui.theme.RabbitRadius
import io.rabbitcontrol.android.ui.theme.SessionPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private data class EditingSlotTarget(
    val side: SidebarSide,
    val index: Int,
)

@Composable
fun DesktopSessionScreen(
    viewModel: SessionViewModel,
    onOpenTool: (DeviceToolKind) -> Unit,
    onExitSession: () -> Unit,
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val stickyMods by viewModel.stickyModifiers.collectAsStateWithLifecycle()
    val keyboardMode by viewModel.keyboardMode.collectAsStateWithLifecycle()
    val keyboardBarVisible = keyboardMode != RemoteKeyboardMode.Hidden
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val sidebarConfig by viewModel.sidebarConfig.collectAsStateWithLifecycle()
    val keymapState by viewModel.keymapState.collectAsStateWithLifecycle()
    val keymapEditing by viewModel.keymapEditing.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SessionPalette.canvas),
    ) {
        val isPortrait = maxHeight >= maxWidth
        val screenWidthPx = constraints.maxWidth.toFloat()
        val pxPerDp = if (maxWidth.value > 0f) screenWidthPx / maxWidth.value else 1f
        val screenHeightPx = constraints.maxHeight.toFloat()
        val isMacTarget = remember(state.deviceOs) { ScancodeMapper.isMacOsTarget(state.deviceOs) }
        var virtualKbdDockedTop by remember { mutableStateOf(false) }
        var virtualKbdOffsetX by remember { mutableFloatStateOf(0f) }
        var virtualKbdOffsetY by remember { mutableFloatStateOf(0f) }

    val streamW = state.displayConfig?.width ?: state.negotiated?.width ?: 1920
    val streamH = state.displayConfig?.height ?: state.negotiated?.height ?: 1080
    val isRemoteVertical = streamH > streamW
    val usePortraitTrackpad = when (prefs.portraitLayoutMode) {
        PortraitLayoutMode.Auto -> !isRemoteVertical
        PortraitLayoutMode.Fullscreen -> false
        PortraitLayoutMode.SplitTrackpad -> true
    }

    var stageRef by remember { mutableStateOf<NativeVideoStageView?>(null) }
    var overlayTouchRef by remember { mutableStateOf<PrecisionTouchLayer?>(null) }
    var bottomTrackpadRef by remember { mutableStateOf<PrecisionTouchLayer?>(null) }
    var imeBridgeRef by remember { mutableStateOf<ImeInputBridge?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    var rightClickModHeld by remember { mutableStateOf(false) }
    var dragModHeld by remember { mutableStateOf(false) }

    // Sync stream geometry & user touch prefs directly to native views without Compose recomposition
    LaunchedEffect(stageRef, state.negotiated, state.displayConfig) {
        val w = state.displayConfig?.width ?: state.negotiated?.width ?: 1920
        val h = state.displayConfig?.height ?: state.negotiated?.height ?: 1080
        val fps = state.displayConfig?.fps ?: state.negotiated?.fps ?: 120
        stageRef?.updateStreamGeometry(w, h, fps)
    }

    LaunchedEffect(stageRef, overlayTouchRef, bottomTrackpadRef, prefs, state.watchOnly, rightClickModHeld, dragModHeld) {
        stageRef?.setCursorVisibility(!state.watchOnly && prefs.touchMode == TouchInputMode.Trackpad)
        listOfNotNull(overlayTouchRef, bottomTrackpadRef).forEach { layer ->
            layer.inputMode = prefs.touchMode
            layer.sensitivity = prefs.pointerSensitivity
            layer.naturalScroll = prefs.naturalScroll
            layer.directTouchOffset = prefs.directTouchOffset
            layer.watchOnly = state.watchOnly
            layer.rightClickModifierHeld = rightClickModHeld
            layer.dragModifierHeld = dragModHeld
        }
    }

    LaunchedEffect(isPortrait) {
        zoomScale = 1f
        rightClickModHeld = false
        dragModHeld = false
    }
    LaunchedEffect(isPortrait, virtualKbdDockedTop, keyboardMode) {
        virtualKbdOffsetX = 0f
        virtualKbdOffsetY = 0f
    }

    // Idle dimming for the edge-snapped micro-pill (when sidebars are hidden)
    var lastPillTouchMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pillDimmed by remember { mutableStateOf(false) }
    LaunchedEffect(lastPillTouchMs, drawerOpen) {
        pillDimmed = false
        if (!drawerOpen) {
            delay(3200L)
            pillDimmed = true
        }
    }
    val pillAlpha by animateFloatAsState(targetValue = if (pillDimmed) 0.38f else 0.95f, label = "pillAlpha")

    var passwordInput by remember { mutableStateOf("") }
    var dragLocked by remember { mutableStateOf(false) }
    var disconnectConfirm by remember { mutableStateOf(false) }
    var initialControlTab by remember { mutableStateOf(ControlPanelTab.Controls) }
    var editingSlotTarget by remember { mutableStateOf<EditingSlotTarget?>(null) }
    var showGestureGuideOverride by remember { mutableStateOf(false) }

    val showGestureGuide = showGestureGuideOverride ||
        (!sidebarConfig.gestureGuideSeen && state.status is SessionStatus.Streaming && (isPortrait || sidebarConfig.showLandscapeSidebars))

    fun openControlPanel(tab: ControlPanelTab = ControlPanelTab.Controls) {
        initialControlTab = tab
        viewModel.setDrawerOpen(true)
    }

    LaunchedEffect(keyboardMode) {
        when (keyboardMode) {
            RemoteKeyboardMode.SystemIme -> imeBridgeRef?.showSoftKeyboard()
            RemoteKeyboardMode.Hidden, RemoteKeyboardMode.VirtualFull -> imeBridgeRef?.hideSoftKeyboard()
        }
    }

    fun toggleKeyboardAndIme() {
        viewModel.toggleKeyboardBar()
    }

    fun handleQuickAction(kind: QuickActionKind) {
        when (kind) {
            QuickActionKind.ZoomIn -> zoomScale = stageRef?.stepZoomIn() ?: zoomScale
            QuickActionKind.ZoomOut -> zoomScale = stageRef?.stepZoomOut() ?: zoomScale
            QuickActionKind.ResetZoom -> {
                stageRef?.resetZoom()
                zoomScale = 1f
            }
            QuickActionKind.DragLock -> {
                dragLocked = !dragLocked
                Core.sendMouseButton(0, dragLocked)
            }
            QuickActionKind.ToggleKeyboard -> toggleKeyboardAndIme()
            QuickActionKind.HoldRightClick -> viewModel.executeShortcutKey(QuickActionKind.RightClick)
            QuickActionKind.HoldDrag -> {
                dragLocked = !dragLocked
                Core.sendMouseButton(0, dragLocked)
            }
            QuickActionKind.ToggleOrientation -> {
                val next = if (isPortrait) {
                    OrientationPreference.LandscapeFirst
                } else {
                    OrientationPreference.PortraitTrackpad
                }
                viewModel.setOrientationPref(next)
            }
            QuickActionKind.Empty -> Unit
            else -> viewModel.executeShortcutKey(kind)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Hidden 1x1 IME proxy
        AndroidView(
            factory = { ctx ->
                ImeInputBridge(
                    context = ctx,
                    stickyModifiersProvider = { viewModel.stickyModifiers.value },
                    onConsumeStickyModifiers = viewModel::consumeStickyModifiers,
                    onSendKey = { sc, pressed, mods -> Core.sendKey(sc, pressed, mods) },
                    onTapScancode = { sc -> viewModel.tapKey(sc) },
                    onSendAsciiText = { txt -> viewModel.sendAsciiText(txt) },
                    onPushSilentIme = { txt -> Core.pushImeSilent(txt) },
                    onSendPasteShortcut = { viewModel.sendPasteShortcut() },
                ).also { imeBridgeRef = it }
            },
            modifier = Modifier.size(1.dp),
        )

        if (isPortrait) {
            // Portrait Adaptive Mode: Fullscreen (vertical target / full view) or Split Trackpad (one-handed desktop)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SessionPalette.canvas)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (usePortraitTrackpad) 0.45f else 1f),
                ) {
                    VideoAndCursorAndroidView(
                        onStageCreated = { stg ->
                            stg.onZoomChanged = { zoomScale = it }
                            stageRef = stg
                        },
                        onTouchCreated = { overlayTouchRef = it },
                        onSurfaceReady = viewModel::attachSurface,
                        onSurfaceLost = viewModel::detachSurface,
                        onSyncedMove = { nx, ny ->
                            Core.sendMouseMove(nx, ny)
                            bottomTrackpadRef?.syncNormalizedPosition(nx, ny)
                        },
                    )

                    SessionKeyMappingOverlay(
                        keymapState = keymapState,
                        editing = keymapEditing,
                        onExecuteQuickAction = ::handleQuickAction,
                        onSelectProfile = viewModel::selectKeymapProfile,
                        onOpacityChange = viewModel::setKeymapOpacity,
                        onMoveItem = viewModel::moveKeymapItem,
                        onResizeItem = viewModel::resizeKeymapItem,
                        onAddItem = viewModel::addKeymapItem,
                        onRemoveItem = viewModel::removeKeymapItem,
                        onResetProfile = viewModel::resetActiveKeymapProfile,
                        onDoneEditing = { viewModel.setKeymapEditing(false) },
                    )

                    if (state.watchOnly) {
                        WatchModeBadge(modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                    }
                    if (zoomScale > 1.02f) {
                        ZoomBadge(
                            zoomScale = zoomScale,
                            onReset = { handleQuickAction(QuickActionKind.ResetZoom) },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 10.dp, top = if (state.watchOnly) 42.dp else 10.dp),
                        )
                    }
                    if (rightClickModHeld || dragModHeld) {
                        ModifierModeBadge(
                            text = stringResource(
                                if (rightClickModHeld) R.string.session_banner_right_hold else R.string.session_banner_drag_hold,
                            ),
                            accentColor = if (rightClickModHeld) Color(0xFF4C8DFF) else Color(0xFF34C759),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        )
                    }
                    if (prefs.showHud && state.negotiated != null) {
                        LiveMiniHudBadge(
                            hudFlow = viewModel.hud,
                            onClick = { openControlPanel(ControlPanelTab.Display) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        )
                    }
                }

                if (usePortraitTrackpad) {
                    // Middle Ergonomic Action Dock
                    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(SessionPalette.chrome)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactStripButton(
                            label = stringResource(R.string.preview_btn_left),
                            active = false,
                            onClick = {
                                if (dragLocked) {
                                    dragLocked = false
                                    Core.sendMouseButton(0, false)
                                } else {
                                    Core.sendMouseButton(0, true)
                                    Core.sendMouseButton(0, false)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        CompactStripButton(
                            label = stringResource(if (dragLocked) R.string.session_drag_active else R.string.session_drag),
                            active = dragLocked,
                            onClick = { handleQuickAction(QuickActionKind.DragLock) },
                            modifier = Modifier.weight(1f),
                        )
                        CompactStripButton(
                            label = stringResource(R.string.preview_btn_right),
                            active = false,
                            onClick = {
                                Core.sendMouseButton(1, true)
                                Core.sendMouseButton(1, false)
                            },
                            modifier = Modifier.weight(0.9f),
                        )
                        Box(modifier = Modifier.width(1.dp).height(22.dp).background(SessionPalette.border))
                        IconButton(
                            onClick = { viewModel.setPortraitLayoutMode(PortraitLayoutMode.Fullscreen) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = stringResource(R.string.session_cd_fullscreen),
                                tint = SessionPalette.text,
                            )
                        }
                        IconButton(onClick = ::toggleKeyboardAndIme, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Keyboard,
                                contentDescription = stringResource(R.string.session_cd_keyboard),
                                tint = if (keyboardBarVisible) SessionPalette.accentText else SessionPalette.text,
                            )
                        }
                        IconButton(
                            onClick = { openControlPanel(ControlPanelTab.Controls) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = stringResource(R.string.session_cd_control_center),
                                tint = SessionPalette.accentText,
                            )
                        }
                    }
                    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)

                    // Dedicated Bottom Precision Trackpad
                    DisposableEffect(Unit) {
                        onDispose { bottomTrackpadRef = null }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.55f)
                            .background(SessionPalette.trackpad),
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PrecisionTouchLayer(
                                    context = ctx,
                                    stageProvider = { stageRef },
                                    trackpadOnly = true,
                                    onMove = { nx, ny ->
                                        Core.sendMouseMove(nx, ny)
                                        overlayTouchRef?.syncNormalizedPosition(nx, ny)
                                    },
                                    onButton = { btn, pressed -> Core.sendMouseButton(btn, pressed) },
                                    onWheel = { delta, horiz -> Core.sendMouseWheel(delta, horiz) },
                                ).also { bottomTrackpadRef = it }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        Text(
                            text = stringResource(R.string.session_trackpad_hint),
                            color = SessionPalette.muted.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                        )
                    }
                } else {
                    // Compact Portrait Fullscreen Bottom Bar
                    HorizontalDivider(color = SessionPalette.border, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(SessionPalette.chrome),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PortraitHoldModifierButton(
                            icon = Icons.Rounded.Mouse,
                            label = stringResource(if (rightClickModHeld) R.string.session_right_active else R.string.session_right_hold),
                            held = rightClickModHeld,
                            activeTint = Color(0xFF4C8DFF),
                            onHoldChanged = { rightClickModHeld = it },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        PortraitBarButton(
                            icon = Icons.Rounded.TouchApp,
                            label = stringResource(R.string.session_trackpad),
                            active = false,
                            onClick = { viewModel.setPortraitLayoutMode(PortraitLayoutMode.SplitTrackpad) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        PortraitBarButton(
                            icon = Icons.Rounded.Keyboard,
                            label = stringResource(R.string.session_cd_keyboard),
                            active = keyboardBarVisible,
                            onClick = ::toggleKeyboardAndIme,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        PortraitBarButton(
                            icon = Icons.Rounded.ScreenRotation,
                            label = stringResource(R.string.session_landscape),
                            active = false,
                            onClick = { handleQuickAction(QuickActionKind.ToggleOrientation) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        PortraitBarButton(
                            icon = Icons.Rounded.Tune,
                            label = stringResource(R.string.session_actions),
                            active = false,
                            tintAccent = true,
                            onClick = { openControlPanel(ControlPanelTab.Controls) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        } else if (sidebarConfig.showLandscapeSidebars) {
            // Landscape UU-Style Dual Sidebars (Left 3 Hold/Shortcut Zones + Center Viewport + Right Fixed 操作 + 3 Customizable Buttons)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SessionPalette.canvas),
            ) {
                LandscapeLeftSidebar(
                    slots = sidebarConfig.leftSlots,
                    zoomScale = zoomScale,
                    rightClickHeld = rightClickModHeld,
                    dragModHeld = dragModHeld,
                    dragLocked = dragLocked,
                    audioMuted = state.audioMuted,
                    showHud = prefs.showHud,
                    keymapEnabled = keymapState.enabled,
                    onHoldRightClick = { rightClickModHeld = it },
                    onHoldDrag = { dragModHeld = it },
                    onSlotClick = { idx, action ->
                        if (action == QuickActionKind.Empty) {
                            editingSlotTarget = EditingSlotTarget(SidebarSide.Left, idx)
                        } else {
                            handleQuickAction(action)
                        }
                    },
                    onSlotLongClick = { idx ->
                        editingSlotTarget = EditingSlotTarget(SidebarSide.Left, idx)
                    },
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    VideoAndCursorAndroidView(
                        onStageCreated = { stg ->
                            stg.onZoomChanged = { zoomScale = it }
                            stageRef = stg
                        },
                        onTouchCreated = { overlayTouchRef = it },
                        onSurfaceReady = viewModel::attachSurface,
                        onSurfaceLost = viewModel::detachSurface,
                        onSyncedMove = { nx, ny -> Core.sendMouseMove(nx, ny) },
                    )

                    SessionKeyMappingOverlay(
                        keymapState = keymapState,
                        editing = keymapEditing,
                        onExecuteQuickAction = ::handleQuickAction,
                        onSelectProfile = viewModel::selectKeymapProfile,
                        onOpacityChange = viewModel::setKeymapOpacity,
                        onMoveItem = viewModel::moveKeymapItem,
                        onResizeItem = viewModel::resizeKeymapItem,
                        onAddItem = viewModel::addKeymapItem,
                        onRemoveItem = viewModel::removeKeymapItem,
                        onResetProfile = viewModel::resetActiveKeymapProfile,
                        onDoneEditing = { viewModel.setKeymapEditing(false) },
                    )

                    if (state.watchOnly) {
                        WatchModeBadge(modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                    }
                    if (zoomScale > 1.02f) {
                        ZoomBadge(
                            zoomScale = zoomScale,
                            onReset = { handleQuickAction(QuickActionKind.ResetZoom) },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 10.dp, top = if (state.watchOnly) 42.dp else 10.dp),
                        )
                    }
                    if (rightClickModHeld || dragModHeld) {
                        ModifierModeBadge(
                            text = stringResource(
                                if (rightClickModHeld) R.string.session_banner_right_hold else R.string.session_banner_drag_hold,
                            ),
                            accentColor = if (rightClickModHeld) Color(0xFF4C8DFF) else Color(0xFF34C759),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        )
                    }
                    if (prefs.showHud && state.negotiated != null) {
                        LiveMiniHudBadge(
                            hudFlow = viewModel.hud,
                            onClick = { openControlPanel(ControlPanelTab.Display) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        )
                    }
                }

                LandscapeRightSidebar(
                    slots = sidebarConfig.rightSlots,
                    zoomScale = zoomScale,
                    dragLocked = dragLocked,
                    audioMuted = state.audioMuted,
                    showHud = prefs.showHud,
                    keymapEnabled = keymapState.enabled,
                    keyboardVisible = keyboardBarVisible,
                    onOpenControls = { openControlPanel(ControlPanelTab.Controls) },
                    onSlotClick = { idx, action ->
                        if (action == QuickActionKind.Empty) {
                            editingSlotTarget = EditingSlotTarget(SidebarSide.Right, idx)
                        } else {
                            handleQuickAction(action)
                        }
                    },
                    onSlotLongClick = { idx ->
                        editingSlotTarget = EditingSlotTarget(SidebarSide.Right, idx)
                    },
                )
            }
        } else {
            // Landscape Pure Fullscreen Mode (Micro-Pill fallback)
            Box(modifier = Modifier.fillMaxSize()) {
                VideoAndCursorAndroidView(
                    onStageCreated = { stg ->
                        stg.onZoomChanged = { zoomScale = it }
                        stageRef = stg
                    },
                    onTouchCreated = { overlayTouchRef = it },
                    onSurfaceReady = viewModel::attachSurface,
                    onSurfaceLost = viewModel::detachSurface,
                    onSyncedMove = { nx, ny -> Core.sendMouseMove(nx, ny) },
                )

                SessionKeyMappingOverlay(
                    keymapState = keymapState,
                    editing = keymapEditing,
                    onExecuteQuickAction = ::handleQuickAction,
                    onSelectProfile = viewModel::selectKeymapProfile,
                    onOpacityChange = viewModel::setKeymapOpacity,
                    onMoveItem = viewModel::moveKeymapItem,
                    onResizeItem = viewModel::resizeKeymapItem,
                    onAddItem = viewModel::addKeymapItem,
                    onRemoveItem = viewModel::removeKeymapItem,
                    onResetProfile = viewModel::resetActiveKeymapProfile,
                    onDoneEditing = { viewModel.setKeymapEditing(false) },
                )

                if (state.watchOnly) {
                    WatchModeBadge(modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(14.dp))
                }
                if (zoomScale > 1.02f) {
                    ZoomBadge(
                        zoomScale = zoomScale,
                        onReset = { handleQuickAction(QuickActionKind.ResetZoom) },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .systemBarsPadding()
                            .padding(start = 14.dp, top = if (state.watchOnly) 48.dp else 14.dp),
                    )
                }
                if (prefs.showHud && state.negotiated != null) {
                    LiveMiniHudBadge(
                        hudFlow = viewModel.hud,
                        onClick = { openControlPanel(ControlPanelTab.Display) },
                        modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(14.dp),
                    )
                }

                EdgeSnappedMicroPill(
                    alpha = pillAlpha,
                    keyboardActive = keyboardBarVisible,
                    touchMode = prefs.touchMode,
                    onTouch = { lastPillTouchMs = System.currentTimeMillis() },
                    onToggleKeyboard = {
                        lastPillTouchMs = System.currentTimeMillis()
                        toggleKeyboardAndIme()
                    },
                    onToggleTouchMode = {
                        lastPillTouchMs = System.currentTimeMillis()
                        handleQuickAction(QuickActionKind.ToggleTouchMode)
                    },
                    onOpenControlPanel = {
                        lastPillTouchMs = System.currentTimeMillis()
                        openControlPanel(ControlPanelTab.Controls)
                    },
                )
            }
        }

        // Pinned-Anchor Floating System IME Extended Bar (lifts cleanly above IME)
        AnimatedVisibility(
            visible = keyboardMode == RemoteKeyboardMode.SystemIme && !state.watchOnly,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .then(if (isPortrait) Modifier.navigationBarsPadding() else Modifier)
                .padding(bottom = 4.dp),
        ) {
            SessionKeyboardBar(
                isPortrait = isPortrait,
                isMacHost = isMacTarget,
                stickyMods = stickyMods,
                onToggleMod = viewModel::toggleStickyModifier,
                onTapKey = { sc, modBit, modSc -> viewModel.tapKey(sc, modBit, modSc) },
                onShowIme = { imeBridgeRef?.showSoftKeyboard() },
                onSwitchToVirtual = { viewModel.setKeyboardMode(RemoteKeyboardMode.VirtualFull) },
                onPushClipboard = { viewModel.pushPhoneClipboard() },
                onClose = { viewModel.setKeyboardMode(RemoteKeyboardMode.Hidden) },
            )
        }

        // UU-Remote Style Built-In Full Virtual Keyboard (PC/Mac 5-row QWERTY)
        AnimatedVisibility(
            visible = keyboardMode == RemoteKeyboardMode.VirtualFull && !state.watchOnly,
            enter = fadeIn() + slideInVertically { if (virtualKbdDockedTop) -it / 2 else it / 2 },
            exit = fadeOut() + slideOutVertically { if (virtualKbdDockedTop) -it / 2 else it / 2 },
            modifier = Modifier
                .align(if (virtualKbdDockedTop) Alignment.TopCenter else Alignment.BottomCenter)
                .offset { IntOffset(virtualKbdOffsetX.roundToInt(), virtualKbdOffsetY.roundToInt()) },
        ) {
            val maxDragY = screenHeightPx * 0.55f
            val maxDragX = if (isPortrait) {
                0f
            } else {
                ((screenWidthPx - (480f * pxPerDp).coerceAtMost(screenWidthPx)) * 0.5f).coerceAtLeast(0f)
            }
            SessionVirtualKeyboard(
                isPortrait = isPortrait,
                isMacHost = isMacTarget,
                stickyMods = stickyMods,
                dockedTop = virtualKbdDockedTop,
                onToggleMod = viewModel::toggleStickyModifier,
                onPressKey = viewModel::pressVirtualKey,
                onRepeatKey = viewModel::repeatVirtualKey,
                onReleaseKey = viewModel::releaseVirtualKey,
                onTapComboKey = { sc, modBit, modSc -> viewModel.tapKey(sc, modBit, modSc) },
                onPushClipboard = { viewModel.pushPhoneClipboard() },
                onSwitchToSystemIme = { viewModel.setKeyboardMode(RemoteKeyboardMode.SystemIme) },
                onToggleDock = { virtualKbdDockedTop = !virtualKbdDockedTop },
                onDrag = { dx, dy ->
                    virtualKbdOffsetX = (virtualKbdOffsetX + dx).coerceIn(-maxDragX, maxDragX)
                    virtualKbdOffsetY = if (virtualKbdDockedTop) {
                        (virtualKbdOffsetY + dy).coerceIn(0f, maxDragY)
                    } else {
                        (virtualKbdOffsetY + dy).coerceIn(-maxDragY, 0f)
                    }
                },
                onDragEnd = {
                    val threshold = screenHeightPx * 0.40f
                    if (!virtualKbdDockedTop && virtualKbdOffsetY < -threshold) {
                        virtualKbdDockedTop = true
                        virtualKbdOffsetY = 0f
                    } else if (virtualKbdDockedTop && virtualKbdOffsetY > threshold) {
                        virtualKbdDockedTop = false
                        virtualKbdOffsetY = 0f
                    }
                },
                onClose = { viewModel.setKeyboardMode(RemoteKeyboardMode.Hidden) },
            )
        }

        // 3-Step Interactive Gesture Onboarding Guide
        if (showGestureGuide) {
            SessionGestureGuide(
                isPortrait = isPortrait,
                onFinish = {
                    showGestureGuideOverride = false
                    viewModel.markGestureGuideSeen()
                },
            )
        }

        // Connection / Auth / Switching / Reconnecting Overlay
        when (val st = state.status) {
            is SessionStatus.Connecting, is SessionStatus.Switching, is SessionStatus.Reconnecting -> {
                val msg = when (st) {
                    is SessionStatus.Connecting -> st.label
                    is SessionStatus.Switching -> st.reason
                    is SessionStatus.Reconnecting -> st.reason
                    else -> ""
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SessionPalette.scrim),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(RabbitRadius.Xl),
                        color = SessionPalette.chromeElevated,
                        tonalElevation = 0.dp,
                        shadowElevation = RabbitElevation.Floating,
                        border = BorderStroke(1.dp, SessionPalette.border),
                        modifier = Modifier.width(300.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                        ) {
                            CircularProgressIndicator(color = SessionPalette.accent, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                state.deviceName.ifBlank { state.targetId },
                                color = SessionPalette.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(msg, color = SessionPalette.muted, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(14.dp))
                            TextButton(onClick = {
                                viewModel.disconnect(false)
                                onExitSession()
                            }) {
                                Text(stringResource(R.string.session_cancel_connect), color = SessionPalette.dangerText)
                            }
                        }
                    }
                }
            }

            is SessionStatus.Error, is SessionStatus.Ended -> {
                val uiErr = when (st) {
                    is SessionStatus.Error -> st.uiError
                    is SessionStatus.Ended -> st.uiError
                    else -> null
                }
                val title = uiErr?.title?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.session_not_ready)
                val reason = uiErr?.message?.takeIf { it.isNotBlank() }
                    ?: if (st is SessionStatus.Error) st.message else (st as SessionStatus.Ended).reason
                val suggestion = uiErr?.suggestion?.takeIf { it.isNotBlank() && it != reason }
                val lowerReason = reason.lowercase()
                val needsPassword = uiErr?.code == RcErrorCodes.SESSION_AUTH_REJECTED ||
                    lowerReason.contains("password") ||
                    lowerReason.contains("auth") ||
                    lowerReason.contains("denied") ||
                    lowerReason.contains("unauthorized") ||
                    reason.contains("密码") ||
                    reason.contains("密碼") ||
                    reason.contains("拒绝") ||
                    reason.contains("拒絕")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SessionPalette.scrim),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(RabbitRadius.Xl),
                        color = SessionPalette.chromeElevated,
                        tonalElevation = 0.dp,
                        shadowElevation = RabbitElevation.Floating,
                        border = BorderStroke(1.dp, SessionPalette.border),
                        modifier = Modifier
                            .width(340.dp)
                            .padding(horizontal = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(title, color = SessionPalette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(reason, color = SessionPalette.muted, fontSize = 13.sp)
                            if (suggestion != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(suggestion, color = SessionPalette.muted.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                            if (needsPassword) {
                                var passwordVisible by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it },
                                    placeholder = { Text(stringResource(R.string.session_host_pw_hint), color = SessionPalette.muted) },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                                contentDescription = stringResource(
                                                    if (passwordVisible) R.string.common_hide_password else R.string.common_show_password,
                                                ),
                                                tint = if (passwordVisible) SessionPalette.accentText else SessionPalette.muted,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    shape = RoundedCornerShape(RabbitRadius.Md),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = SessionPalette.text,
                                        unfocusedTextColor = SessionPalette.text,
                                        focusedContainerColor = SessionPalette.trackpad,
                                        unfocusedContainerColor = SessionPalette.trackpad,
                                        focusedBorderColor = SessionPalette.accent,
                                        unfocusedBorderColor = SessionPalette.border,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.retryWithPassword(passwordInput) },
                                    enabled = passwordInput.isNotEmpty(),
                                    shape = RoundedCornerShape(RabbitRadius.Md),
                                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SessionPalette.accent, contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) {
                                    Text(stringResource(R.string.session_verify_reconnect), fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { viewModel.retryWithPassword(passwordInput) },
                                    shape = RoundedCornerShape(RabbitRadius.Md),
                                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SessionPalette.accent, contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) {
                                    Text(stringResource(R.string.session_reconnect), fontWeight = FontWeight.Medium)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(onClick = onExitSession) { Text(stringResource(R.string.common_back), color = SessionPalette.muted) }
                        }
                    }
                }
            }

            else -> Unit
        }

        // Right-Docked Side Drawer (Landscape) / Compact Half-Sheet (Portrait) Control Center
        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { viewModel.setDrawerOpen(false) },
                    ),
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = if (isPortrait) {
                slideInVertically(animationSpec = tween(220)) { it }
            } else {
                slideInHorizontally(animationSpec = tween(220)) { it }
            },
            exit = if (isPortrait) {
                slideOutVertically(animationSpec = tween(180)) { it }
            } else {
                slideOutHorizontally(animationSpec = tween(180)) { it }
            },
            modifier = Modifier.align(if (isPortrait) Alignment.BottomCenter else Alignment.CenterEnd),
        ) {
            SessionControlPanel(
                viewModel = viewModel,
                isPortrait = isPortrait,
                initialTab = initialControlTab,
                onQuickAction = ::handleQuickAction,
                onEditSidebarSlot = { side, idx ->
                    viewModel.setDrawerOpen(false)
                    editingSlotTarget = EditingSlotTarget(side, idx)
                },
                onEditKeymap = {
                    viewModel.setDrawerOpen(false)
                    viewModel.setKeymapEditing(true)
                },
                onShowGestureGuide = {
                    viewModel.setDrawerOpen(false)
                    showGestureGuideOverride = true
                },
                onShowKeyboard = {
                    viewModel.setDrawerOpen(false)
                    viewModel.setKeyboardBarVisible(true)
                },
                onPushClipboard = {
                    viewModel.pushPhoneClipboard()
                    viewModel.setDrawerOpen(false)
                },
                onOpenTool = { kind ->
                    viewModel.setDrawerOpen(false)
                    onOpenTool(kind)
                },
                onRequestDisconnect = {
                    viewModel.setDrawerOpen(false)
                    disconnectConfirm = true
                },
                onDismiss = { viewModel.setDrawerOpen(false) },
            )
        }

        // Left/Right Sidebar Customizer Modal
        val target = editingSlotTarget
        if (target != null) {
            val currentSlots = if (target.side == SidebarSide.Left) {
                sidebarConfig.leftSlots
            } else {
                sidebarConfig.rightSlots
            }
            SidebarSlotPickerOverlay(
                side = target.side,
                initialSlotIndex = target.index,
                currentSlots = currentSlots,
                onSelectSlotAction = { slotIdx, action ->
                    if (target.side == SidebarSide.Left) {
                        viewModel.setLeftSlot(slotIdx, action)
                    } else {
                        viewModel.setRightSlot(slotIdx, action)
                    }
                },
                onDismiss = { editingSlotTarget = null },
            )
        }
    }

    if (disconnectConfirm) {
        var lockOnExit by remember { mutableStateOf(false) }
        RabbitDialog(
            title = stringResource(R.string.session_disconnect_title),
            message = stringResource(R.string.session_disconnect_msg, state.deviceName),
            onDismissRequest = { disconnectConfirm = false },
            dismissLabel = stringResource(R.string.common_cancel),
            confirmLabel = stringResource(R.string.session_disconnect_confirm),
            destructive = true,
            darkSessionStyle = true,
            onConfirm = {
                disconnectConfirm = false
                viewModel.disconnect(lockOnExit)
                onExitSession()
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RabbitRadius.Md))
                    .background(SessionPalette.surface)
                    .border(1.dp, SessionPalette.border, RoundedCornerShape(RabbitRadius.Md))
                    .clickable { lockOnExit = !lockOnExit }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.session_disconnect_lock_screen), color = SessionPalette.text, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Switch(
                    checked = lockOnExit,
                    onCheckedChange = { lockOnExit = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SessionPalette.accent,
                    ),
                )
            }
        }
    }
    }
}

@Composable
private fun VideoAndCursorAndroidView(
    onStageCreated: (NativeVideoStageView) -> Unit,
    onTouchCreated: (PrecisionTouchLayer) -> Unit,
    onSurfaceReady: (android.view.Surface) -> Unit,
    onSurfaceLost: () -> Unit,
    onSyncedMove: (Int, Int) -> Unit,
) {
    AndroidView(
        factory = { ctx ->
            var stage: NativeVideoStageView? = null
            val container = NativeVideoStageView(ctx, onSurfaceReady, onSurfaceLost)
            stage = container
            onStageCreated(container)
            val touch = PrecisionTouchLayer(
                context = ctx,
                stageProvider = { stage },
                trackpadOnly = false,
                onMove = onSyncedMove,
                onButton = { btn, pressed -> Core.sendMouseButton(btn, pressed) },
                onWheel = { delta, horiz -> Core.sendMouseWheel(delta, horiz) },
            )
            onTouchCreated(touch)
            container.addView(touch, android.widget.FrameLayout.LayoutParams(-1, -1))
            container
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun EdgeSnappedMicroPill(
    alpha: Float,
    keyboardActive: Boolean,
    touchMode: TouchInputMode,
    onTouch: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onToggleTouchMode: () -> Unit,
    onOpenControlPanel: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val pillW = with(density) { 134.dp.toPx() }
        val pillH = with(density) { 44.dp.toPx() }

        var dragging by remember { mutableStateOf(false) }
        var offX by remember { mutableFloatStateOf((maxW - pillW - 24f).coerceAtLeast(16f)) }
        var offY by remember { mutableFloatStateOf((maxH * 0.42f).coerceAtLeast(24f)) }
        val animatedX by animateFloatAsState(
            targetValue = offX,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "pillSnapX",
        )
        val displayX = if (dragging) offX else animatedX

        Surface(
            modifier = Modifier
                .graphicsLayer {
                    translationX = displayX
                    translationY = offY
                    this.alpha = alpha
                }
                .pointerInput(maxW, maxH) {
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            onTouch()
                        },
                        onDragEnd = {
                            dragging = false
                            offX = if (offX + pillW * 0.5f < maxW * 0.5f) {
                                16f
                            } else {
                                (maxW - pillW - 16f).coerceAtLeast(16f)
                            }
                            offY = offY.coerceIn(16f, (maxH - pillH - 16f).coerceAtLeast(16f))
                        },
                        onDragCancel = { dragging = false },
                    ) { change, drag ->
                        change.consume()
                        offX = (offX + drag.x).coerceIn(0f, (maxW - pillW).coerceAtLeast(0f))
                        offY = (offY + drag.y).coerceIn(0f, (maxH - pillH).coerceAtLeast(0f))
                    }
                },
            shape = RoundedCornerShape(22.dp),
            color = SessionPalette.chromeElevated.copy(alpha = 0.90f),
            tonalElevation = 0.dp,
            shadowElevation = RabbitElevation.Floating,
            border = BorderStroke(1.dp, SessionPalette.borderSubtle),
        ) {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleKeyboard, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = stringResource(R.string.session_cd_keyboard),
                        tint = if (keyboardActive) SessionPalette.accentText else SessionPalette.text,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(18.dp).background(SessionPalette.borderSubtle))
                IconButton(onClick = onToggleTouchMode, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (touchMode == TouchInputMode.Trackpad) Icons.Rounded.Mouse else Icons.Rounded.TouchApp,
                        contentDescription = touchMode.label,
                        tint = SessionPalette.text,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(18.dp).background(SessionPalette.borderSubtle))
                IconButton(onClick = onOpenControlPanel, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.session_cd_control_center),
                        tint = SessionPalette.accentText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchModeBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SessionPalette.chromeElevated.copy(alpha = 0.85f))
            .border(1.dp, SessionPalette.borderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Visibility, contentDescription = null, tint = SessionPalette.muted, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(stringResource(R.string.tool_watch_mode), color = SessionPalette.text, fontSize = 11.sp)
    }
}

@Composable
private fun ModifierModeBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accentColor.copy(alpha = 0.25f))
            .border(1.dp, accentColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ZoomBadge(
    zoomScale: Float,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SessionPalette.chromeElevated.copy(alpha = 0.85f))
            .border(1.dp, SessionPalette.accent.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(onClick = onReset)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ZoomIn, contentDescription = null, tint = SessionPalette.accentText, modifier = Modifier.size(13.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.session_zoom_reset_chip, String.format("%.1f", zoomScale)),
            color = SessionPalette.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun LiveMiniHudBadge(
    hudFlow: StateFlow<HudSnapshotModel>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hud by hudFlow.collectAsStateWithLifecycle()
    MiniHudBadge(
        fps = hud.fps,
        res = hud.resolution,
        rtt = hud.rttMs,
        transport = hud.transport,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun MiniHudBadge(
    fps: Double,
    res: String,
    rtt: Double?,
    transport: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rttStr = rtt?.let { "${it.roundToInt()}ms" } ?: "-"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SessionPalette.chromeElevated.copy(alpha = 0.82f))
            .border(1.dp, SessionPalette.borderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = "${fps.roundToInt()} fps · $res · $rttStr · $transport",
            color = SessionPalette.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CompactStripButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(if (active) SessionPalette.accent else SessionPalette.surface)
            .border(
                width = 1.dp,
                color = if (active) SessionPalette.accent else SessionPalette.border,
                shape = shape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color.White else SessionPalette.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun PortraitBarButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    tintAccent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(if (active) SessionPalette.surfaceActive.copy(alpha = 0.50f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                active || tintAccent -> SessionPalette.accentText
                else -> SessionPalette.text.copy(alpha = 0.90f)
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (active || tintAccent) SessionPalette.accentText else SessionPalette.muted,
            fontSize = 10.sp,
            fontWeight = if (active || tintAccent) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PortraitHoldModifierButton(
    icon: ImageVector,
    label: String,
    held: Boolean,
    activeTint: Color,
    onHoldChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val currentHold by rememberUpdatedState(onHoldChanged)

    DisposableEffect(Unit) {
        onDispose { currentHold(false) }
    }

    Column(
        modifier = modifier
            .background(if (held) activeTint.copy(alpha = 0.28f) else Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentHold(true)
                    waitForUpOrCancellation()
                    currentHold(false)
                }
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (held) activeTint else SessionPalette.text.copy(alpha = 0.90f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (held) Color.White else SessionPalette.muted,
            fontSize = 10.sp,
            fontWeight = if (held) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
