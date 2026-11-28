package com.rabbitcontrol.android.ui.session

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbitcontrol.android.core.data.ConnectionModePref
import com.rabbitcontrol.android.core.data.InteractionPrefs
import com.rabbitcontrol.android.core.data.InteractionPrefsStore
import com.rabbitcontrol.android.core.data.KeyMapState
import com.rabbitcontrol.android.core.data.OrientationPreference
import com.rabbitcontrol.android.core.data.PortraitLayoutMode
import com.rabbitcontrol.android.core.data.QuickActionKind
import com.rabbitcontrol.android.core.data.SessionLayoutStore
import com.rabbitcontrol.android.core.data.SidebarConfig
import com.rabbitcontrol.android.core.data.TouchInputMode
import com.rabbitcontrol.android.core.data.VirtualKeyItem
import com.rabbitcontrol.android.core.input.ScancodeMapper
import com.rabbitcontrol.android.core.nativebridge.HardwareCodecProbe
import com.rabbitcontrol.android.core.nativebridge.HudSnapshotModel
import com.rabbitcontrol.android.core.nativebridge.LocalCodecSupport
import com.rabbitcontrol.android.core.nativebridge.MediaDiagnosticsModel
import com.rabbitcontrol.android.core.session.ActiveSessionState
import com.rabbitcontrol.android.core.session.ClipboardSyncBridge
import com.rabbitcontrol.android.core.session.SessionManager
import com.rabbitcontrol.android.session.Core
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RemoteKeyboardMode {
    Hidden,
    SystemIme,
    VirtualFull,
}

class SessionViewModel(
    private val sessionManager: SessionManager,
    private val prefsStore: InteractionPrefsStore,
    private val clipboardBridge: ClipboardSyncBridge,
    private val layoutStore: SessionLayoutStore,
) : ViewModel() {

    val sessionState: StateFlow<ActiveSessionState> = sessionManager.state
    val hud: StateFlow<HudSnapshotModel> = sessionManager.hud
    val media: StateFlow<MediaDiagnosticsModel> = sessionManager.media
    val prefs: StateFlow<InteractionPrefs> = prefsStore.state
    val sidebarConfig: StateFlow<SidebarConfig> = layoutStore.sidebarConfig
    val keymapState: StateFlow<KeyMapState> = layoutStore.keymapState
    val localCodecSupport: Map<String, LocalCodecSupport> by lazy {
        HardwareCodecProbe.supportedCodecCapabilities()
    }

    private val _stickyModifiers = MutableStateFlow(0)
    val stickyModifiers: StateFlow<Int> = _stickyModifiers.asStateFlow()

    private val _keyboardMode = MutableStateFlow(RemoteKeyboardMode.Hidden)
    val keyboardMode: StateFlow<RemoteKeyboardMode> = _keyboardMode.asStateFlow()

    private val _keyboardBarVisible = MutableStateFlow(false)
    val keyboardBarVisible: StateFlow<Boolean> = _keyboardBarVisible.asStateFlow()

    private var preferredKeyboardMode: RemoteKeyboardMode = RemoteKeyboardMode.SystemIme

    private val keyActionQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val virtualKeyPressTimestamps = mutableMapOf<Int, Long>()

    init {
        viewModelScope.launch {
            for (action in keyActionQueue) {
                action()
            }
        }
    }

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _keymapEditing = MutableStateFlow(false)
    val keymapEditing: StateFlow<Boolean> = _keymapEditing.asStateFlow()

    fun attachSurface(surface: Surface) = sessionManager.attachSurface(surface)
    fun detachSurface() = sessionManager.detachSurface()

    fun onForeground() = sessionManager.onAppForegrounded()
    fun onBackground(screenLocked: Boolean) {
        releaseAllModifiers()
        sessionManager.onAppBackgrounded(screenLocked)
    }

    fun setDrawerOpen(open: Boolean) {
        _drawerOpen.value = open
        sessionManager.setMediaDiagnosticsVisible(open)
    }

    override fun onCleared() {
        keyActionQueue.close()
        sessionManager.setMediaDiagnosticsVisible(false)
        super.onCleared()
    }

    fun toggleKeyboardBar() {
        val next = if (_keyboardMode.value == RemoteKeyboardMode.Hidden) {
            preferredKeyboardMode
        } else {
            RemoteKeyboardMode.Hidden
        }
        setKeyboardMode(next)
    }

    fun setKeyboardBarVisible(visible: Boolean) {
        setKeyboardMode(if (visible) preferredKeyboardMode else RemoteKeyboardMode.Hidden)
    }

    fun setKeyboardMode(mode: RemoteKeyboardMode) {
        if (mode != RemoteKeyboardMode.Hidden) {
            preferredKeyboardMode = mode
        }
        _keyboardMode.value = mode
        _keyboardBarVisible.value = mode != RemoteKeyboardMode.Hidden
    }

    fun setTouchMode(mode: TouchInputMode) = prefsStore.update { it.copy(touchMode = mode) }
    fun setSensitivity(value: Float) = prefsStore.update { it.copy(pointerSensitivity = value) }
    fun setNaturalScroll(on: Boolean) = prefsStore.update { it.copy(naturalScroll = on) }
    fun setDirectTouchOffset(on: Boolean) = prefsStore.update { it.copy(directTouchOffset = on) }
    fun setShowHud(on: Boolean) = prefsStore.update { it.copy(showHud = on) }
    fun setOrientationPref(pref: OrientationPreference) = prefsStore.update { it.copy(orientationPref = pref) }
    fun setPortraitLayoutMode(mode: PortraitLayoutMode) = prefsStore.update { it.copy(portraitLayoutMode = mode) }

    fun setWatchOnly(enabled: Boolean) = sessionManager.setWatchOnly(enabled)
    fun setAudioMuted(muted: Boolean) = sessionManager.setAudioMuted(muted)
    fun setClipboardEnabled(enabled: Boolean) = sessionManager.setClipboardEnabled(enabled)
    fun setStreamParams(width: Int, height: Int, fps: Int) = sessionManager.setStreamParams(width, height, fps)
    fun setBitrate(bps: Int) = sessionManager.setBitrate(bps)
    fun setCodec(codec: String) = sessionManager.setCodec(codec)
    fun setPreferHardwareDecode(preferHardware: Boolean) = sessionManager.setPreferHardwareDecode(preferHardware)
    fun setConnectionMode(mode: ConnectionModePref) = sessionManager.setConnectionMode(mode)
    fun setPrivacyMode(
        mode: String,
        preset: String = if (mode == "privacy") "preset-1" else "",
        customImageBase64: String? = null,
    ) = sessionManager.setPrivacyMode(mode, preset, customImageBase64)
    fun setMouseMode(mode: String) = sessionManager.setMouseMode(mode)
    fun setAutoLockRemoteOnDisconnect(enabled: Boolean) =
        prefsStore.update { it.copy(autoLockRemoteOnDisconnect = enabled) }
    fun checkRemoteHostUpdate() = sessionManager.checkRemoteHostUpdate()
    fun triggerRemoteHostUpdate(mirrorUrl: String = "") = sessionManager.triggerRemoteHostUpdate(mirrorUrl)
    fun cancelRemoteHostUpdate() = sessionManager.cancelRemoteHostUpdate()
    fun upgradeToDesktop() = sessionManager.upgradeToDesktop()
    fun setHostMute(muted: Boolean) = sessionManager.setHostMute(muted, restoreOnExit = true)
    fun listDisplays() = sessionManager.listDisplays()
    fun switchDisplay(displayId: Int) = sessionManager.switchDisplay(displayId)
    fun createVirtualDisplay(width: Int, height: Int, hidpi: Boolean) =
        sessionManager.createVirtualDisplay(width, height, hidpi)
    fun removeVirtualDisplay(displayId: Int) = sessionManager.removeVirtualDisplay(displayId)
    fun requestIdr() = sessionManager.requestIdr()
    fun lockRemoteScreen() = sessionManager.lockRemoteScreen()

    fun setLeftSlot(index: Int, action: QuickActionKind) = layoutStore.setLeftSlot(index, action)
    fun setRightSlot(index: Int, action: QuickActionKind) = layoutStore.setRightSlot(index, action)
    fun setShowLandscapeSidebars(show: Boolean) = layoutStore.setShowLandscapeSidebars(show)
    fun markGestureGuideSeen() = layoutStore.markGestureGuideSeen()
    fun setKeymapEnabled(enabled: Boolean) {
        layoutStore.setKeymapEnabled(enabled)
        if (!enabled) _keymapEditing.value = false
    }

    fun setKeymapEditing(editing: Boolean) {
        if (editing && !keymapState.value.enabled) {
            layoutStore.setKeymapEnabled(true)
        }
        _keymapEditing.value = editing
    }

    fun setKeymapOpacity(opacity: Float) = layoutStore.setKeymapOpacity(opacity)
    fun selectKeymapProfile(profileId: String) = layoutStore.selectProfile(profileId)
    fun moveKeymapItem(itemId: String, xNorm: Float, yNorm: Float) = layoutStore.moveItem(itemId, xNorm, yNorm)
    fun resizeKeymapItem(itemId: String, sizeDp: Int) = layoutStore.resizeItem(itemId, sizeDp)
    fun addKeymapItem(item: VirtualKeyItem) = layoutStore.addItem(item)
    fun removeKeymapItem(itemId: String) = layoutStore.removeItem(itemId)
    fun resetActiveKeymapProfile() = layoutStore.resetActiveProfile()

    fun retryWithPassword(password: String) {
        val cur = sessionState.value
        if (cur.targetId.isBlank()) return
        viewModelScope.launch {
            sessionManager.connect(
                targetId = cur.targetId,
                deviceName = cur.deviceName,
                deviceOs = cur.deviceOs,
                watchOnly = cur.watchOnly,
                password = password,
            )
        }
    }

    fun disconnect(lockRemote: Boolean) = sessionManager.disconnect(lockRemote)

    fun toggleStickyModifier(bit: Int, scancode: Int) {
        val cur = _stickyModifiers.value
        val active = (cur and bit) != 0
        val next = if (active) (cur and bit.inv()) else (cur or bit)
        _stickyModifiers.value = next
        Core.sendKey(scancode, !active, next)
    }

    fun consumeStickyModifiers() {
        val cur = _stickyModifiers.value
        if (cur != 0) {
            releaseAllModifiers()
        }
    }

    fun releaseAllModifiers() {
        val cur = _stickyModifiers.value
        if (cur == 0) return
        _stickyModifiers.value = 0
        keyActionQueue.trySend {
            releaseModifierBits(cur)
        }
    }

    fun tapKey(scancode: Int, extraModBit: Int = 0, extraModScancode: Int? = null) {
        val sticky = _stickyModifiers.value
        if (sticky != 0) _stickyModifiers.value = 0
        val extraBit = extraModBit or (extraModScancode?.let(ScancodeMapper::modifierBitForScancode) ?: 0)
        keyActionQueue.trySend {
            val needExtra = extraModScancode != null && (extraBit and sticky) == 0
            val mods = sticky or extraBit
            if (needExtra && extraModScancode != null) {
                Core.sendKey(extraModScancode, true, mods)
                delay(18L)
            }
            Core.sendKey(scancode, true, mods)
            delay(28L)
            Core.sendKey(scancode, false, mods)
            if (needExtra && extraModScancode != null) {
                delay(18L)
                Core.sendKey(extraModScancode, false, sticky)
            }
            if (sticky != 0) {
                delay(18L)
                releaseModifierBits(sticky)
            }
        }
    }

    fun sendKeyCombo(scancode: Int, modBits: Int) {
        val sticky = _stickyModifiers.value
        if (sticky != 0) _stickyModifiers.value = 0
        val mods = sticky or modBits
        keyActionQueue.trySend {
            val newlyPressed = mutableListOf<Int>()
            fun pressExtra(bit: Int, sc: Int) {
                if ((modBits and bit) != 0 && (sticky and bit) == 0) {
                    Core.sendKey(sc, true, mods)
                    newlyPressed.add(sc)
                }
            }
            pressExtra(ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL)
            pressExtra(ScancodeMapper.MOD_RIGHT_CTRL, ScancodeMapper.SC_RIGHT_CTRL)
            pressExtra(ScancodeMapper.MOD_LEFT_SHIFT, ScancodeMapper.SC_LEFT_SHIFT)
            pressExtra(ScancodeMapper.MOD_RIGHT_SHIFT, ScancodeMapper.SC_RIGHT_SHIFT)
            pressExtra(ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT)
            pressExtra(ScancodeMapper.MOD_RIGHT_ALT, ScancodeMapper.SC_RIGHT_ALT)
            pressExtra(ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI)
            pressExtra(ScancodeMapper.MOD_RIGHT_GUI, ScancodeMapper.SC_RIGHT_GUI)

            if (newlyPressed.isNotEmpty()) delay(20L)
            Core.sendKey(scancode, true, mods)
            delay(35L)
            Core.sendKey(scancode, false, mods)

            if (newlyPressed.isNotEmpty() || sticky != 0) {
                delay(20L)
                newlyPressed.asReversed().forEach { sc ->
                    Core.sendKey(sc, false, sticky)
                }
                if (sticky != 0) {
                    releaseModifierBits(sticky)
                }
            }
        }
    }

    fun sendAsciiText(text: String) {
        if (text.isEmpty()) return
        val sticky = _stickyModifiers.value
        if (sticky != 0) _stickyModifiers.value = 0
        keyActionQueue.trySend {
            for (ch in text) {
                val mapped = ScancodeMapper.asciiToScancode(ch) ?: continue
                val (sc, needsShift) = mapped
                val shiftAlreadyActive = (sticky and (ScancodeMapper.MOD_LEFT_SHIFT or ScancodeMapper.MOD_RIGHT_SHIFT)) != 0
                val pressShift = needsShift && !shiftAlreadyActive
                val mods = sticky or if (needsShift) ScancodeMapper.MOD_LEFT_SHIFT else 0
                if (pressShift) {
                    Core.sendKey(ScancodeMapper.SC_LEFT_SHIFT, true, mods)
                    delay(14L)
                }
                Core.sendKey(sc, true, mods)
                delay(16L)
                Core.sendKey(sc, false, mods)
                if (pressShift) {
                    delay(14L)
                    Core.sendKey(ScancodeMapper.SC_LEFT_SHIFT, false, sticky)
                }
                delay(12L)
            }
            if (sticky != 0) {
                delay(14L)
                releaseModifierBits(sticky)
            }
        }
    }

    fun pressVirtualKey(scancode: Int) {
        val now = System.currentTimeMillis()
        virtualKeyPressTimestamps[scancode] = now
        val mods = _stickyModifiers.value
        keyActionQueue.trySend {
            Core.sendKey(scancode, true, mods)
        }
    }

    fun repeatVirtualKey(scancode: Int) {
        val mods = _stickyModifiers.value
        keyActionQueue.trySend {
            Core.sendKey(scancode, true, mods)
        }
    }

    fun releaseVirtualKey(scancode: Int) {
        val downAt = virtualKeyPressTimestamps.remove(scancode) ?: 0L
        val elapsed = System.currentTimeMillis() - downAt
        val remainingHoldMs = (24L - elapsed).coerceAtLeast(0L)
        val sticky = _stickyModifiers.value
        val shouldConsume = sticky != 0 && !ScancodeMapper.isModifierScancode(scancode)
        if (shouldConsume) _stickyModifiers.value = 0
        keyActionQueue.trySend {
            if (remainingHoldMs > 0L) delay(remainingHoldMs)
            Core.sendKey(scancode, false, sticky)
            if (shouldConsume) {
                delay(18L)
                releaseModifierBits(sticky)
            }
        }
    }

    fun sendPasteShortcut() {
        val isMac = ScancodeMapper.isMacOsTarget(sessionState.value.deviceOs)
        val modBit = if (isMac) ScancodeMapper.MOD_LEFT_GUI else ScancodeMapper.MOD_LEFT_CTRL
        sendKeyCombo(0x2F, modBit)
    }

    private fun releaseModifierBits(bits: Int) {
        if ((bits and ScancodeMapper.MOD_LEFT_CTRL) != 0) Core.sendKey(ScancodeMapper.SC_LEFT_CTRL, false, 0)
        if ((bits and ScancodeMapper.MOD_RIGHT_CTRL) != 0) Core.sendKey(ScancodeMapper.SC_RIGHT_CTRL, false, 0)
        if ((bits and ScancodeMapper.MOD_LEFT_SHIFT) != 0) Core.sendKey(ScancodeMapper.SC_LEFT_SHIFT, false, 0)
        if ((bits and ScancodeMapper.MOD_RIGHT_SHIFT) != 0) Core.sendKey(ScancodeMapper.SC_RIGHT_SHIFT, false, 0)
        if ((bits and ScancodeMapper.MOD_LEFT_ALT) != 0) Core.sendKey(ScancodeMapper.SC_LEFT_ALT, false, 0)
        if ((bits and ScancodeMapper.MOD_RIGHT_ALT) != 0) Core.sendKey(ScancodeMapper.SC_RIGHT_ALT, false, 0)
        if ((bits and ScancodeMapper.MOD_LEFT_GUI) != 0) Core.sendKey(ScancodeMapper.SC_LEFT_GUI, false, 0)
        if ((bits and ScancodeMapper.MOD_RIGHT_GUI) != 0) Core.sendKey(ScancodeMapper.SC_RIGHT_GUI, false, 0)
    }

    fun executeShortcutKey(action: QuickActionKind) {
        val isMac = ScancodeMapper.isMacOsTarget(sessionState.value.deviceOs)
        val primaryMod = if (isMac) ScancodeMapper.MOD_LEFT_GUI else ScancodeMapper.MOD_LEFT_CTRL
        when (action) {
            QuickActionKind.ShowDesktop -> {
                if (isMac) sendKeyCombo(ScancodeMapper.SC_F11, 0)
                else sendKeyCombo(0x20, ScancodeMapper.MOD_LEFT_GUI) // Win + D
            }
            QuickActionKind.AllWindows -> {
                if (isMac) sendKeyCombo(ScancodeMapper.SC_UP, ScancodeMapper.MOD_LEFT_CTRL)
                else sendKeyCombo(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_GUI) // Win + Tab
            }
            QuickActionKind.SwitchWindow -> {
                if (isMac) sendKeyCombo(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_GUI)
                else sendKeyCombo(ScancodeMapper.SC_TAB, ScancodeMapper.MOD_LEFT_ALT) // Alt + Tab
            }
            QuickActionKind.Screenshot -> {
                if (isMac) sendKeyCombo(0x05, ScancodeMapper.MOD_LEFT_GUI or ScancodeMapper.MOD_LEFT_SHIFT) // Cmd+Shift+4
                else sendKeyCombo(0x1F, ScancodeMapper.MOD_LEFT_GUI or ScancodeMapper.MOD_LEFT_SHIFT) // Win+Shift+S
            }
            QuickActionKind.TaskManager -> {
                if (isMac) sendKeyCombo(ScancodeMapper.SC_ESC, ScancodeMapper.MOD_LEFT_GUI or ScancodeMapper.MOD_LEFT_ALT)
                else sendKeyCombo(ScancodeMapper.SC_ESC, ScancodeMapper.MOD_LEFT_CTRL or ScancodeMapper.MOD_LEFT_SHIFT)
            }
            QuickActionKind.LockScreen -> lockRemoteScreen()
            QuickActionKind.Copy -> sendKeyCombo(0x2E, primaryMod)
            QuickActionKind.Paste -> sendKeyCombo(0x2F, primaryMod)
            QuickActionKind.Undo -> sendKeyCombo(0x2C, primaryMod)
            QuickActionKind.SelectAll -> sendKeyCombo(0x1E, primaryMod)
            QuickActionKind.Enter -> sendKeyCombo(ScancodeMapper.SC_ENTER, 0)
            QuickActionKind.Escape -> sendKeyCombo(ScancodeMapper.SC_ESC, 0)
            QuickActionKind.LeftClick -> {
                Core.sendMouseButton(0, true)
                Core.sendMouseButton(0, false)
            }
            QuickActionKind.RightClick -> {
                Core.sendMouseButton(1, true)
                Core.sendMouseButton(1, false)
            }
            QuickActionKind.MiddleClick -> {
                Core.sendMouseButton(2, true)
                Core.sendMouseButton(2, false)
            }
            QuickActionKind.ScrollUp -> Core.sendMouseWheel(3, false)
            QuickActionKind.ScrollDown -> Core.sendMouseWheel(-3, false)
            QuickActionKind.ToggleTouchMode -> {
                val next = if (prefs.value.touchMode == TouchInputMode.Trackpad) {
                    TouchInputMode.DirectTouch
                } else {
                    TouchInputMode.Trackpad
                }
                setTouchMode(next)
            }
            QuickActionKind.ToggleMute -> setAudioMuted(!sessionState.value.audioMuted)
            QuickActionKind.ToggleHud -> setShowHud(!prefs.value.showHud)
            QuickActionKind.ToggleKeymap -> setKeymapEnabled(!keymapState.value.enabled)
            QuickActionKind.ZoomIn,
            QuickActionKind.ZoomOut,
            QuickActionKind.ResetZoom,
            QuickActionKind.HoldRightClick,
            QuickActionKind.HoldDrag,
            QuickActionKind.DragLock,
            QuickActionKind.ToggleKeyboard,
            QuickActionKind.ToggleOrientation,
            QuickActionKind.Empty,
            -> Unit
        }
    }

    fun pushPhoneClipboard(): Boolean = clipboardBridge.pushCurrentClipboardNow()
}
