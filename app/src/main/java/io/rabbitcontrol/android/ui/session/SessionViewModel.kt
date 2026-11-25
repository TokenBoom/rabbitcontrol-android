package io.rabbitcontrol.android.ui.session

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rabbitcontrol.android.core.data.InteractionPrefs
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.OrientationPreference
import io.rabbitcontrol.android.core.data.TouchInputMode
import io.rabbitcontrol.android.core.input.ScancodeMapper
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.ClipboardSyncBridge
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.session.Core
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(
    private val sessionManager: SessionManager,
    private val prefsStore: InteractionPrefsStore,
    private val clipboardBridge: ClipboardSyncBridge,
) : ViewModel() {

    val sessionState: StateFlow<ActiveSessionState> = sessionManager.state
    val prefs: StateFlow<InteractionPrefs> = prefsStore.state

    private val _stickyModifiers = MutableStateFlow(0)
    val stickyModifiers: StateFlow<Int> = _stickyModifiers.asStateFlow()

    private val _keyboardBarVisible = MutableStateFlow(false)
    val keyboardBarVisible: StateFlow<Boolean> = _keyboardBarVisible.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

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

    fun setWatchOnly(enabled: Boolean) = sessionManager.setWatchOnly(enabled)
    fun setAudioMuted(muted: Boolean) = sessionManager.setAudioMuted(muted)
    fun setClipboardEnabled(enabled: Boolean) = sessionManager.setClipboardEnabled(enabled)
    fun setStreamParams(width: Int, height: Int, fps: Int) = sessionManager.setStreamParams(width, height, fps)
    fun setBitrate(bps: Int) = sessionManager.setBitrate(bps)
    fun requestIdr() = sessionManager.requestIdr()
    fun lockRemoteScreen() = sessionManager.lockRemoteScreen()

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

    fun pushPhoneClipboard(): Boolean = clipboardBridge.pushCurrentClipboardNow()
}
