package io.rabbitcontrol.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.InteractionPrefs
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.TargetHistoryStore
import io.rabbitcontrol.android.core.data.WallpaperCache
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.nativebridge.ServerConfigModel
import io.rabbitcontrol.android.core.nativebridge.SignalStatusSnapshot
import io.rabbitcontrol.android.core.nativebridge.UserProfile
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.session.Core
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeTab(val label: String) {
    Devices("我的设备"),
    Assist("远程协助"),
    Me("我的"),
}

class HomeViewModel(
    private val accountRepo: AccountRepository,
    private val historyStore: TargetHistoryStore,
    val wallpaperCache: WallpaperCache,
    private val prefsStore: InteractionPrefsStore,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val servers: StateFlow<List<ServerConfigModel>> = accountRepo.servers
    val server: StateFlow<ServerConfigModel?> = accountRepo.server
    val savedAccountEmails: StateFlow<Map<String, String>> = accountRepo.savedAccountEmails
    val profile: StateFlow<UserProfile?> = accountRepo.profile
    val devices: StateFlow<List<RemoteDevice>> = accountRepo.devices
    val signalStatus: StateFlow<SignalStatusSnapshot> = accountRepo.signalStatus
    val recentTargets: StateFlow<List<String>> = historyStore.recent
    val favoriteTargets: StateFlow<List<String>> = historyStore.favorites
    val prefs: StateFlow<InteractionPrefs> = prefsStore.state
    val activeSession: StateFlow<ActiveSessionState> = sessionManager.state
    val wallpaperVersion: StateFlow<Long> = wallpaperCache.version
    val kickedNotice: StateFlow<String?> = accountRepo.kickedNotice

    private val _restoring = MutableStateFlow(true)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _refreshingDevices = MutableStateFlow(false)
    val refreshingDevices: StateFlow<Boolean> = _refreshingDevices.asStateFlow()

    private val _deviceError = MutableStateFlow<String?>(null)
    val deviceError: StateFlow<String?> = _deviceError.asStateFlow()

    private val _selectedTab = MutableStateFlow(HomeTab.Devices)
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    private val _onlineOnly = MutableStateFlow(false)
    val onlineOnly: StateFlow<Boolean> = _onlineOnly.asStateFlow()

    private val _assistTargetInput = MutableStateFlow("")
    val assistTargetInput: StateFlow<String> = _assistTargetInput.asStateFlow()

    init {
        restoreSession()
    }

    fun restoreSession() {
        _restoring.value = true
        viewModelScope.launch {
            runCatching { accountRepo.restore() }
            _restoring.value = false
            if (accountRepo.profile.value?.needsPassword == false) {
                refreshDevices()
            }
        }
    }

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    fun toggleOnlineOnly() {
        _onlineOnly.value = !_onlineOnly.value
    }

    fun refreshDevices() {
        if (accountRepo.profile.value?.needsPassword != false) return
        _refreshingDevices.value = true
        _deviceError.value = null
        viewModelScope.launch {
            runCatching { accountRepo.refreshDevices() }
                .onFailure { err -> _deviceError.value = err.message ?: "刷新设备列表失败" }
            _refreshingDevices.value = false
        }
    }

    fun updateAssistInput(raw: String) {
        _assistTargetInput.value = raw
    }

    fun parseTargetOrIpPort(raw: String): String? {
        val trimmed = raw.trim()
        Core.deviceId(trimmed)?.let { return it }
        // Support direct LAN IP:port debugging target
        val colon = trimmed.lastIndexOf(':')
        if (colon > 0 && colon < trimmed.length - 1) {
            val port = trimmed.substring(colon + 1).toIntOrNull()
            if (port != null && port in 1..65535) {
                return trimmed
            }
        }
        return null
    }

    fun recordTarget(target: String) {
        historyStore.recordRecent(target)
    }

    fun toggleFavorite(target: String) {
        historyStore.toggleFavorite(target)
    }

    fun updatePrefs(transform: (InteractionPrefs) -> InteractionPrefs) {
        prefsStore.update(transform)
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            sessionManager.disconnect(false)
            accountRepo.logout()
            onDone()
        }
    }

    fun switchServer(target: ServerConfigModel, onNeedsLogin: () -> Unit) {
        viewModelScope.launch {
            sessionManager.disconnect(false)
            val restored = accountRepo.switchServer(target)
            if (restored && accountRepo.profile.value?.needsPassword == false) {
                refreshDevices()
            } else {
                onNeedsLogin()
            }
        }
    }

    fun dismissKicked() = accountRepo.dismissKickedNotice()
}
