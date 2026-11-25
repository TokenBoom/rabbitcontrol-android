package io.rabbitcontrol.android.ui.session

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.input.ImeInputBridge
import io.rabbitcontrol.android.core.input.PrecisionTouchLayer
import io.rabbitcontrol.android.core.input.ScancodeMapper
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.core.video.NativeVideoStageView
import io.rabbitcontrol.android.session.Core
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DesktopSessionScreen(
    viewModel: SessionViewModel,
    onOpenTool: (DeviceToolKind) -> Unit,
    onExitSession: () -> Unit,
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val stickyMods by viewModel.stickyModifiers.collectAsStateWithLifecycle()
    val keyboardBarVisible by viewModel.keyboardBarVisible.collectAsStateWithLifecycle()
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val isMacTarget = remember(state.deviceOs) { ScancodeMapper.isMacOsTarget(state.deviceOs) }

    var stageRef by remember { mutableStateOf<NativeVideoStageView?>(null) }
    var overlayTouchRef by remember { mutableStateOf<PrecisionTouchLayer?>(null) }
    var bottomTrackpadRef by remember { mutableStateOf<PrecisionTouchLayer?>(null) }
    var imeBridgeRef by remember { mutableStateOf<ImeInputBridge?>(null) }

    // Sync stream geometry & user touch prefs directly to native views without Compose recomposition
    LaunchedEffect(state.negotiated, state.displayConfig) {
        val w = state.displayConfig?.width ?: state.negotiated?.width ?: 1920
        val h = state.displayConfig?.height ?: state.negotiated?.height ?: 1080
        val fps = state.displayConfig?.fps ?: state.negotiated?.fps ?: 120
        stageRef?.updateStreamGeometry(w, h, fps)
    }

    LaunchedEffect(prefs, state.watchOnly) {
        listOfNotNull(overlayTouchRef, bottomTrackpadRef).forEach { layer ->
            layer.inputMode = prefs.touchMode
            layer.sensitivity = prefs.pointerSensitivity
            layer.naturalScroll = prefs.naturalScroll
            layer.directTouchOffset = prefs.directTouchOffset
            layer.watchOnly = state.watchOnly
        }
    }

    // Idle dimming for the edge-snapped micro-pill
    var lastPillTouchMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pillDimmed by remember { mutableStateOf(false) }
    LaunchedEffect(lastPillTouchMs, drawerOpen) {
        pillDimmed = false
        if (!drawerOpen) {
            delay(3200L)
            pillDimmed = true
        }
    }
    val pillAlpha by animateFloatAsState(targetValue = if (pillDimmed) 0.36f else 0.94f, label = "pillAlpha")

    var passwordInput by remember { mutableStateOf("") }
    var dragLocked by remember { mutableStateOf(false) }
    var disconnectConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090D)),
    ) {
        // Hidden 1x1 IME proxy
        AndroidView(
            factory = { ctx ->
                ImeInputBridge(
                    context = ctx,
                    isMacHost = { ScancodeMapper.isMacOsTarget(viewModel.sessionState.value.deviceOs) },
                    stickyModifiersProvider = { viewModel.stickyModifiers.value },
                    onConsumeStickyModifiers = viewModel::consumeStickyModifiers,
                    onSendKey = { sc, pressed, mods -> Core.sendKey(sc, pressed, mods) },
                    onPushSilentIme = { txt -> Core.pushImeSilent(txt) },
                ).also { imeBridgeRef = it }
            },
            modifier = Modifier.size(1.dp),
        )

        if (isPortrait) {
            // ── Portrait One-Handed Ergonomic Mode: Top Desktop Viewport + Bottom Precision Trackpad ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.46f),
                ) {
                    VideoAndCursorAndroidView(
                        onStageCreated = { stageRef = it },
                        onTouchCreated = { overlayTouchRef = it },
                        onSurfaceReady = viewModel::attachSurface,
                        onSurfaceLost = viewModel::detachSurface,
                        onSyncedMove = { nx, ny ->
                            Core.sendMouseMove(nx, ny)
                            bottomTrackpadRef?.syncNormalizedPosition(nx, ny)
                        },
                    )

                    if (state.watchOnly) {
                        WatchModeBadge(modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
                    }
                    if (prefs.showHud && state.negotiated != null) {
                        MiniHudBadge(
                            fps = state.hud.fps,
                            res = state.hud.resolution,
                            rtt = state.hud.rttMs,
                            transport = state.hud.transport,
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        )
                    }
                }

                // Middle ergonomic action strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141820))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactStripButton(
                        label = if (dragLocked) "锁定拖动中" else "左键",
                        active = dragLocked,
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
                        label = if (dragLocked) "松开拖动" else "按住拖动",
                        active = dragLocked,
                        onClick = {
                            dragLocked = !dragLocked
                            Core.sendMouseButton(0, dragLocked)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CompactStripButton(
                        label = "右键",
                        active = false,
                        onClick = {
                            Core.sendMouseButton(1, true)
                            Core.sendMouseButton(1, false)
                        },
                        modifier = Modifier.weight(0.8f),
                    )
                    IconButton(
                        onClick = {
                            viewModel.setKeyboardBarVisible(!keyboardBarVisible)
                            if (!keyboardBarVisible) imeBridgeRef?.showSoftKeyboard() else imeBridgeRef?.hideSoftKeyboard()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Rounded.Keyboard, "键盘", tint = if (keyboardBarVisible) Color(0xFF3370FF) else Color(0xFFD0D8E4))
                    }
                    IconButton(
                        onClick = { viewModel.setDrawerOpen(true) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Rounded.Tune, "控制中心", tint = Color(0xFFD0D8E4))
                    }
                }

                // Dedicated Bottom-Half Precision Glass Trackpad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.54f)
                        .background(Color(0xFF0E1117))
                        .border(1.dp, Color(0xFF1F2633)),
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
                        "单指移动 · 轻点左键 · 双指轻点右键 · 双指惯性滚动 · 三指拖动",
                        color = Color(0xFF5D697D),
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                    )
                }
            }
        } else {
            // ── Landscape Fullscreen Immersive Mode ──
            Box(modifier = Modifier.fillMaxSize()) {
                VideoAndCursorAndroidView(
                    onStageCreated = { stageRef = it },
                    onTouchCreated = { overlayTouchRef = it },
                    onSurfaceReady = viewModel::attachSurface,
                    onSurfaceLost = viewModel::detachSurface,
                    onSyncedMove = { nx, ny -> Core.sendMouseMove(nx, ny) },
                )

                if (state.watchOnly) {
                    WatchModeBadge(modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(14.dp))
                }
                if (prefs.showHud && state.negotiated != null) {
                    MiniHudBadge(
                        fps = state.hud.fps,
                        res = state.hud.resolution,
                        rtt = state.hud.rttMs,
                        transport = state.hud.transport,
                        modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(14.dp),
                    )
                }

                // Edge-snapped draggable micro-pill
                EdgeSnappedMicroPill(
                    alpha = pillAlpha,
                    onTouch = { lastPillTouchMs = System.currentTimeMillis() },
                    onClick = {
                        lastPillTouchMs = System.currentTimeMillis()
                        viewModel.setDrawerOpen(true)
                    },
                    onToggleKeyboard = {
                        lastPillTouchMs = System.currentTimeMillis()
                        val show = !keyboardBarVisible
                        viewModel.setKeyboardBarVisible(show)
                        if (show) imeBridgeRef?.showSoftKeyboard() else imeBridgeRef?.hideSoftKeyboard()
                    },
                )
            }
        }

        // Floating Extended Keyboard Bar (sticky modifiers + macros + special keys)
        AnimatedVisibility(
            visible = keyboardBarVisible && !state.watchOnly,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 6.dp),
        ) {
            ExtendedKeyboardBar(
                isMacHost = isMacTarget,
                stickyMods = stickyMods,
                onToggleMod = viewModel::toggleStickyModifier,
                onTapKey = { sc, modBit, modSc -> viewModel.tapKey(sc, modBit, modSc) },
                onShowIme = { imeBridgeRef?.showSoftKeyboard() },
                onPushClipboard = { viewModel.pushPhoneClipboard() },
                onClose = {
                    viewModel.setKeyboardBarVisible(false)
                    imeBridgeRef?.hideSoftKeyboard()
                },
            )
        }

        // Connection / Auth / Switching Overlay
        when (val st = state.status) {
            is SessionStatus.Connecting, is SessionStatus.Switching -> {
                val msg = if (st is SessionStatus.Connecting) st.label else (st as SessionStatus.Switching).reason
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xC807090D)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1A202A))
                            .padding(horizontal = 28.dp, vertical = 22.dp),
                    ) {
                        CircularProgressIndicator(color = Color(0xFF3370FF))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(state.deviceName.ifBlank { state.targetId }, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(msg, color = Color(0xFFAAB4C4), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        TextButton(onClick = {
                            viewModel.disconnect(false)
                            onExitSession()
                        }) {
                            Text("取消连接", color = Color(0xFFFF8C94))
                        }
                    }
                }
            }

            is SessionStatus.Error, is SessionStatus.Ended -> {
                val reason = if (st is SessionStatus.Error) st.message else (st as SessionStatus.Ended).reason
                val needsPassword = reason.contains("密码") || reason.contains("Auth") || reason.contains("拒绝")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xD807090D)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .width(340.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1A202A))
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("会话未就绪", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(reason, color = Color(0xFFAAB4C4), fontSize = 13.sp)
                        if (needsPassword) {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                placeholder = { Text("输入被控端访问密码") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.retryWithPassword(passwordInput) },
                                enabled = passwordInput.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("验证并重连")
                            }
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.retryWithPassword(passwordInput) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("重新连接")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = onExitSession) { Text("返回", color = Color(0xFFAAB4C4)) }
                    }
                }
            }

            else -> Unit
        }

        // Control Center Drawer Overlay
        if (drawerOpen) {
            ControlCenterOverlay(
                viewModel = viewModel,
                onResetZoom = { stageRef?.resetZoom() },
                onShowKeyboard = {
                    viewModel.setDrawerOpen(false)
                    viewModel.setKeyboardBarVisible(true)
                    imeBridgeRef?.showSoftKeyboard()
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
    }

    if (disconnectConfirm) {
        var lockOnExit by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { disconnectConfirm = false },
            title = { Text("断开远程会话") },
            text = {
                Column {
                    Text("确认结束当前对「${state.deviceName}」的控制？")
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("断开时锁定远端屏幕", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = lockOnExit, onCheckedChange = { lockOnExit = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    disconnectConfirm = false
                    viewModel.disconnect(lockOnExit)
                    onExitSession()
                }) { Text("断开", color = Color(0xFFFF6B73)) }
            },
            dismissButton = {
                TextButton(onClick = { disconnectConfirm = false }) { Text("取消") }
            },
        )
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
    onTouch: () -> Unit,
    onClick: () -> Unit,
    onToggleKeyboard: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        val density = LocalDensity.current
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val pillW = with(density) { 88.dp.toPx() }
        val pillH = with(density) { 40.dp.toPx() }

        var offX by remember { mutableFloatStateOf((maxW - pillW - 24f).coerceAtLeast(16f)) }
        var offY by remember { mutableFloatStateOf((maxH * 0.42f).coerceAtLeast(24f)) }

        Row(
            modifier = Modifier
                .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
                .alpha(alpha)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xD9161B24))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
                .pointerInput(maxW, maxH) {
                    detectDragGestures(
                        onDragStart = { onTouch() },
                        onDragEnd = {
                            // Snap horizontally to nearest screen edge
                            offX = if (offX + pillW * 0.5f < maxW * 0.5f) 16f else (maxW - pillW - 16f).coerceAtLeast(16f)
                            offY = offY.coerceIn(16f, (maxH - pillH - 16f).coerceAtLeast(16f))
                        },
                    ) { change, drag ->
                        change.consume()
                        offX = (offX + drag.x).coerceIn(0f, (maxW - pillW).coerceAtLeast(0f))
                        offY = (offY + drag.y).coerceIn(0f, (maxH - pillH).coerceAtLeast(0f))
                    }
                }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleKeyboard, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Keyboard, "键盘", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Box(modifier = Modifier.width(1.dp).height(18.dp).background(Color(0x2EFFFFFF)))
            IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Tune, "控制中心", tint = Color(0xFF6094FF), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WatchModeBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xBF141920))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Visibility, null, tint = Color(0xFF9FB4D1), modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text("观看模式", color = Color(0xFFE6ECF5), fontSize = 11.sp)
    }
}

@Composable
private fun MiniHudBadge(fps: Double, res: String, rtt: Double?, transport: String, modifier: Modifier = Modifier) {
    val rttStr = rtt?.let { "${it.roundToInt()}ms" } ?: "-"
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xB811151C))
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            "${fps.roundToInt()} fps · $res · $rttStr · $transport",
            color = Color(0xFFD5DFED),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun CompactStripButton(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Color(0xFF3370FF) else Color(0xFF212733))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ExtendedKeyboardBar(
    isMacHost: Boolean,
    stickyMods: Int,
    onToggleMod: (bit: Int, scancode: Int) -> Unit,
    onTapKey: (scancode: Int, extraModBit: Int, extraModScancode: Int?) -> Unit,
    onShowIme: () -> Unit,
    onPushClipboard: () -> Unit,
    onClose: () -> Unit,
) {
    val primaryModBit = if (isMacHost) ScancodeMapper.MOD_LEFT_GUI else ScancodeMapper.MOD_LEFT_CTRL
    val primaryModSc = if (isMacHost) ScancodeMapper.SC_LEFT_GUI else ScancodeMapper.SC_LEFT_CTRL
    val primaryLabel = if (isMacHost) "⌘" else "Ctrl"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xEB151A23))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyChip("软键盘", highlighted = true, onClick = onShowIme)
        KeyChip(if (isMacHost) "⌘" else "Win", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_GUI) != 0) {
            onToggleMod(ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI)
        }
        KeyChip("Ctrl", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_CTRL) != 0) {
            onToggleMod(ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL)
        }
        KeyChip(if (isMacHost) "⌥" else "Alt", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_ALT) != 0) {
            onToggleMod(ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT)
        }
        KeyChip("Shift", highlighted = (stickyMods and ScancodeMapper.MOD_LEFT_SHIFT) != 0) {
            onToggleMod(ScancodeMapper.MOD_LEFT_SHIFT, ScancodeMapper.SC_LEFT_SHIFT)
        }

        // OS-adaptive productivity macros
        KeyChip("${primaryLabel}+C") { onTapKey(0x2E, primaryModBit, primaryModSc) }
        KeyChip("${primaryLabel}+V") { onTapKey(0x2F, primaryModBit, primaryModSc) }
        KeyChip("${primaryLabel}+Z") { onTapKey(0x2C, primaryModBit, primaryModSc) }
        KeyChip("${primaryLabel}+A") { onTapKey(0x1E, primaryModBit, primaryModSc) }
        if (isMacHost) {
            KeyChip("⌘Space") { onTapKey(ScancodeMapper.SC_SPACE, ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI) }
            KeyChip("⌘Tab") { onTapKey(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI) }
        } else {
            KeyChip("Alt+Tab") { onTapKey(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT) }
        }
        KeyChip("Ctrl+C") { onTapKey(0x2E, ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL) }
        KeyChip("Esc") { onTapKey(ScancodeMapper.SC_ESC, 0, null) }
        KeyChip("Tab") { onTapKey(ScancodeMapper.SC_TAB, 0, null) }
        KeyChip("↑") { onTapKey(ScancodeMapper.SC_UP, 0, null) }
        KeyChip("↓") { onTapKey(ScancodeMapper.SC_DOWN, 0, null) }
        KeyChip("←") { onTapKey(ScancodeMapper.SC_LEFT, 0, null) }
        KeyChip("→") { onTapKey(ScancodeMapper.SC_RIGHT, 0, null) }
        KeyChip("Del") { onTapKey(ScancodeMapper.SC_DELETE, 0, null) }
        KeyChip("F5") { onTapKey(ScancodeMapper.SC_F5, 0, null) }
        KeyChip("F11") { onTapKey(ScancodeMapper.SC_F11, 0, null) }
        KeyChip("发剪贴板", onClick = onPushClipboard)
        IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Rounded.Close, "收起", tint = Color(0xFFAAB4C4), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun KeyChip(label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) Color(0xFF3370FF) else Color(0xFF252D3B))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlCenterOverlay(
    viewModel: SessionViewModel,
    onResetZoom: () -> Unit,
    onShowKeyboard: () -> Unit,
    onOpenTool: (DeviceToolKind) -> Unit,
    onRequestDisconnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(340.dp)
                .background(Color(0xFF141821))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.deviceName.ifBlank { "控制中心" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    val neg = state.negotiated
                    val diag = state.media
                    if (neg != null) {
                        val hwTag = if (diag.hardware) "硬解" else "解码"
                        Text("${neg.width}×${neg.height} @ ${neg.fps}fps · ${neg.transport} · $hwTag", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "关闭", tint = Color.White)
                }
            }

            // Quick Actions Grid
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DrawerTile(
                    icon = if (prefs.touchMode == TouchInputMode.Trackpad) Icons.Rounded.Mouse else Icons.Rounded.TouchApp,
                    label = prefs.touchMode.label,
                    active = true,
                    modifier = Modifier.weight(1f),
                ) {
                    val next = if (prefs.touchMode == TouchInputMode.Trackpad) TouchInputMode.DirectTouch else TouchInputMode.Trackpad
                    viewModel.setTouchMode(next)
                }
                DrawerTile(Icons.Rounded.Keyboard, "键盘条", false, Modifier.weight(1f), onShowKeyboard)
                DrawerTile(
                    icon = if (state.audioMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    label = if (state.audioMuted) "已静音" else "声音开",
                    active = !state.audioMuted,
                    modifier = Modifier.weight(1f),
                ) { viewModel.setAudioMuted(!state.audioMuted) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DrawerTile(Icons.Rounded.ContentPaste, if (state.clipboardEnabled) "剪贴板开" else "剪贴板关", state.clipboardEnabled, Modifier.weight(1f)) {
                    viewModel.setClipboardEnabled(!state.clipboardEnabled)
                }
                DrawerTile(Icons.Rounded.Visibility, if (state.watchOnly) "观看模式" else "控制模式", state.watchOnly, Modifier.weight(1f)) {
                    viewModel.setWatchOnly(!state.watchOnly)
                }
                DrawerTile(Icons.Rounded.ScreenRotation, when (prefs.orientationPref) {
                    OrientationPreference.LandscapeFirst -> "横屏"
                    OrientationPreference.AutoSensor -> "自动"
                    OrientationPreference.PortraitTrackpad -> "竖屏单手"
                }, false, Modifier.weight(1f)) {
                    val entries = OrientationPreference.entries
                    val next = entries[(prefs.orientationPref.ordinal + 1) % entries.size]
                    viewModel.setOrientationPref(next)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DrawerTile(Icons.Rounded.Speed, if (prefs.showHud) "HUD 开" else "HUD 关", prefs.showHud, Modifier.weight(1f)) {
                    viewModel.setShowHud(!prefs.showHud)
                }
                DrawerTile(Icons.Rounded.ZoomOutMap, "重置缩放", false, Modifier.weight(1f), onResetZoom)
                DrawerTile(Icons.Rounded.Refresh, "刷新关键帧", false, Modifier.weight(1f)) { viewModel.requestIdr() }
            }

            HorizontalDivider(color = Color(0xFF252D3B))

            // Pointer Tuning
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("指针灵敏度", color = Color.White, fontSize = 13.sp)
                    Text("${String.format("%.2f", prefs.pointerSensitivity)}x", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
                Slider(
                    value = prefs.pointerSensitivity,
                    onValueChange = viewModel::setSensitivity,
                    valueRange = 0.5f..2.5f,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("自然滚动方向", color = Color(0xFFD0D8E4), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = prefs.naturalScroll, onCheckedChange = viewModel::setNaturalScroll)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("直点指尖微偏置", color = Color(0xFFD0D8E4), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = prefs.directTouchOffset, onCheckedChange = viewModel::setDirectTouchOffset)
                }
            }

            HorizontalDivider(color = Color(0xFF252D3B))

            // Stream Resolution, FPS & Bitrate Hot Switching
            val dc = state.displayConfig
            val curW = dc?.width ?: state.negotiated?.width ?: 0
            val curH = dc?.height ?: state.negotiated?.height ?: 0
            val curFps = dc?.fps ?: state.negotiated?.fps ?: 60
            val activeBps = state.targetBitrateBps ?: state.negotiated?.bitrateBps
            val presetSelection = remember(dc?.presets, curW, curH) {
                selectCommonResolutionPresets(dc?.presets.orEmpty(), curW, curH, maxCommon = 5)
            }
            var showMoreResolutions by remember { mutableStateOf(false) }

            Text("画质与帧率", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

            if (presetSelection.common.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("常用分辨率", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    if (curW > 0 && curH > 0) {
                        Text("${curW}×${curH}", color = Color(0xFF8DB2FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presetSelection.common.forEach { preset ->
                        val active = curW == preset.width && curH == preset.height
                        KeyChip(formatCompactPresetLabel(preset), highlighted = active) {
                            viewModel.setStreamParams(preset.width, preset.height, curFps)
                        }
                    }
                    if (presetSelection.extra.isNotEmpty()) {
                        KeyChip(
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
                            KeyChip(formatCompactPresetLabel(preset), highlighted = active) {
                                viewModel.setStreamParams(preset.width, preset.height, curFps)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("帧率上限", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Text("${curFps}fps", color = Color(0xFF8DB2FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            val maxFpsCap = maxOf(dc?.maxFps ?: 120, curFps, 60)
            val fpsOptions = listOf(30, 60, 90, 120).filter { it <= maxFpsCap }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                fpsOptions.forEach { fps ->
                    KeyChip("${fps}fps", highlighted = curFps == fps) {
                        viewModel.setStreamParams(curW, curH, fps)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("目标带宽", color = Color(0xFF94A3B8), fontSize = 12.sp)
                if (activeBps != null && activeBps > 0) {
                    Text("${activeBps / 1_000_000}M", color = Color(0xFF8DB2FF), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
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
                    KeyChip(lbl, highlighted = activeBps == bps) {
                        viewModel.setBitrate(bps)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF252D3B))

            // Tools & Lock
            Text("远控工具", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DrawerTile(Icons.Rounded.FolderOpen, "文件", false, Modifier.weight(1f)) { onOpenTool(DeviceToolKind.FileTransfer) }
                DrawerTile(Icons.Rounded.Terminal, "终端", false, Modifier.weight(1f)) { onOpenTool(DeviceToolKind.Terminal) }
                DrawerTile(Icons.Rounded.Cable, "端口映射", false, Modifier.weight(1f)) { onOpenTool(DeviceToolKind.PortMap) }
            }
            OutlinedButton(
                onClick = viewModel::lockRemoteScreen,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("立即锁定远端电脑", color = Color.White)
            }

            Button(
                onClick = onRequestDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA82932)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("断开连接", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DrawerTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0xFF22365E) else Color(0xFF1F2633))
            .border(1.dp, if (active) Color(0xFF3370FF) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (active) Color(0xFF8DB2FF) else Color(0xFFD0D8E4), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(5.dp))
        Text(label, color = Color.White, fontSize = 11.sp, maxLines = 1)
    }
}
