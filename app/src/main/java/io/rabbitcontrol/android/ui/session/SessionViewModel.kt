package io.rabbitcontrol.android.ui.session

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rabbitcontrol.android.core.data.InteractionPrefs
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.KeyMapState
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.PortraitLayoutMode
import io.rabbitcontrol.android.core.data.QuickActionKind
import io.rabbitcontrol.android.core.data.SessionLayoutStore
import io.rabbitcontrol.android.core.data.SidebarConfig
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.data.VirtualKeyItem
import io.rabbitcontrol.android.core.input.ScancodeMapper
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.ClipboardSyncBridge
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.session.Core
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(
    private val sessionManager: SessionManager,
    private val prefsStore: InteractionPrefsStore,
    private val clipboardBridge: ClipboardSyncBridge,
    private val layoutStore: SessionLayoutStore,
) : ViewModel() {

    val sessionState: StateFlow<ActiveSessionState> = sessionManager.state
    val prefs: StateFlow<InteractionPrefs> = prefsStore.state
    val sidebarConfig: StateFlow<SidebarConfig> = layoutStore.sidebarConfig
    val keymapState: StateFlow<KeyMapState> = layoutStore.keymapState

    private val _stickyModifiers = MutableStateFlow(0)
    val stickyModifiers: StateFlow<Int> = _stickyModifiers.asStateFlow()

    private val _keyboardBarVisible = MutableStateFlow(false)
    val keyboardBarVisible: StateFlow<Boolean> = _keyboardBarVisible.asStateFlow()

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
    }

    fun toggleKeyboardBar() {
        _keyboardBarVisible.value = !_keyboardBarVisible.value
    }

    fun setKeyboardBarVisible(visible: Boolean) {
        _keyboardBarVisible.value = visible
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
        if ((cur and ScancodeMapper.MOD_LEFT_CTRL) != 0) Core.sendKey(ScancodeMapper.SC_LEFT_CTRL, false, 0)
        if ((cur and ScancodeMapper.MOD_LEFT_SHIFT) != 0) Core.sendKey(ScancodeMapper.SC_LEFT_SHIFT, false, 0)
        if ((cur and ScancodeMapper.MOD_LEFT_ALT) != 0) Core.sendKey(ScancodeMapper.SC_LEFT_ALT, false, 0)
        if ((cur and ScancodeMapper.MOD_LEFT_GUI) != 0) Core.sendKey(ScancodeMapper.SC_LEFT_GUI, false, 0)
    }

    fun tapKey(scancode: Int, extraModBit: Int = 0, extraModScancode: Int? = null) {
        val mods = _stickyModifiers.value or extraModBit
        if (extraModScancode != null && (extraModBit and _stickyModifiers.value) == 0) {
            Core.sendKey(extraModScancode, true, mods)
        }
        Core.sendKey(scancode, true, mods)
        Core.sendKey(scancode, false, mods)
        if (extraModScancode != null && (extraModBit and _stickyModifiers.value) == 0) {
            Core.sendKey(extraModScancode, false, _stickyModifiers.value)
        }
        consumeStickyModifiers()
    }

    fun sendKeyCombo(scancode: Int, modBits: Int) {
        val mods = _stickyModifiers.value or modBits
        consumeStickyModifiers()
        viewModelScope.launch {
            val pressedMods = mutableListOf<Int>()
            fun pressIf(bit: Int, sc: Int) {
                if ((mods and bit) != 0) {
                    Core.sendKey(sc, true, mods)
                    pressedMods.add(sc)
                }
            }
            pressIf(ScancodeMapper.MOD_LEFT_CTRL, ScancodeMapper.SC_LEFT_CTRL)
            pressIf(ScancodeMapper.MOD_LEFT_SHIFT, ScancodeMapper.SC_LEFT_SHIFT)
            pressIf(ScancodeMapper.MOD_LEFT_ALT, ScancodeMapper.SC_LEFT_ALT)
            pressIf(ScancodeMapper.MOD_LEFT_GUI, ScancodeMapper.SC_LEFT_GUI)

            if (pressedMods.isNotEmpty()) delay(20L)
            Core.sendKey(scancode, true, mods)
            delay(35L)
            Core.sendKey(scancode, false, mods)

            if (pressedMods.isNotEmpty()) {
                delay(20L)
                pressedMods.asReversed().forEach { sc ->
                    Core.sendKey(sc, false, 0)
                }
            }
        }
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
