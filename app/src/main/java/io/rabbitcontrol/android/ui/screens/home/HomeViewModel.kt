package io.rabbitcontrol.android.ui.screens.home

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcUiError
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.InteractionPrefs
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.ProxyConfigModel
import io.rabbitcontrol.android.core.data.ProxyConfigStore
import io.rabbitcontrol.android.core.data.TargetHistoryStore
import io.rabbitcontrol.android.core.data.WallpaperCache
import io.rabbitcontrol.android.core.error.RcUiErrorCodec
import io.rabbitcontrol.android.core.host.HostManager
import io.rabbitcontrol.android.core.host.HostUiState
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.nativebridge.ServerConfigModel
import io.rabbitcontrol.android.core.nativebridge.SignalStatusSnapshot
import io.rabbitcontrol.android.core.nativebridge.UserProfile
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.core.session.SessionStatus
import io.rabbitcontrol.android.session.Core
import io.rabbitcontrol.android.ui.components.RcErrorHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeTab(
    @StringRes val labelRes: Int,
    private val defaultLabel: String,
) {
    Devices(R.string.tab_devices, "我的设备"),
    Assist(R.string.tab_assist, "远程协助"),
    Me(R.string.tab_me, "我的");

    val label: String
        get() = AppStrings.get(labelRes, defaultLabel)
}

class HomeViewModel(
    private val accountRepo: AccountRepository,
    private val historyStore: TargetHistoryStore,
    val wallpaperCache: WallpaperCache,
    private val prefsStore: InteractionPrefsStore,
    private val proxyStore: ProxyConfigStore,
    private val sessionManager: SessionManager,
    private val hostManager: HostManager,
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
    val proxyConfig: StateFlow<ProxyConfigModel> = proxyStore.state
    val activeSession: StateFlow<ActiveSessionState> = sessionManager.state
    val hostState: StateFlow<HostUiState> = hostManager.state
    val wallpaperVersion: StateFlow<Long> = wallpaperCache.version
    val kickedNotice: StateFlow<String?> = accountRepo.kickedNotice

    private val _restoring = MutableStateFlow(true)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _refreshingDevices = MutableStateFlow(false)
    val refreshingDevices: StateFlow<Boolean> = _refreshingDevices.asStateFlow()

    private val _deviceError = MutableStateFlow<RcUiError?>(null)
    val deviceError: StateFlow<RcUiError?> = _deviceError.asStateFlow()

    private val _selectedTab = MutableStateFlow(HomeTab.Devices)
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    private val _onlineOnly = MutableStateFlow(false)
    val onlineOnly: StateFlow<Boolean> = _onlineOnly.asStateFlow()

    private val _assistTargetInput = MutableStateFlow("")
    val assistTargetInput: StateFlow<String> = _assistTargetInput.asStateFlow()

    init {
        restoreSession()
        observeRecovery()
    }

    private fun observeRecovery() {
        viewModelScope.launch {
            var prevState: String? = null
            accountRepo.signalStatus.collect { snap ->
                if (snap.state == "connected") {
                    if (prevState != null && prevState != "connected") {
                        _deviceError.value = null
                        if (accountRepo.profile.value?.needsPassword == false && !_refreshingDevices.value) {
                            refreshDevices()
                        }
                    }
                }
                prevState = snap.state
            }
        }
        viewModelScope.launch {
            sessionManager.state.collect { st ->
                if (st.status is SessionStatus.Streaming && _deviceError.value != null) {
                    _deviceError.value = null
                }
            }
        }
        viewModelScope.launch {
            accountRepo.devices.drop(1).collect { list ->
                if (list.isNotEmpty() && accountRepo.signalStatus.value.state == "connected" && _deviceError.value != null) {
                    _deviceError.value = null
                }
            }
        }
    }

    fun dismissDeviceError() {
        _deviceError.value = null
    }

    fun restoreSession() {
        _restoring.value = !accountRepo.restored.value
        viewModelScope.launch {
            if (!accountRepo.restored.value) {
                runCatching { accountRepo.restore() }
            }
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
                .onFailure { err ->
                    _deviceError.value = RcUiErrorCodec.fromThrowable(
                        err,
                        fallbackCode = RcErrorCodes.NETWORK_UNREACHABLE,
                        fallbackMessage = AppStrings.get(R.string.home_err_refresh_failed, "刷新设备列表失败"),
                    )
                }
            _refreshingDevices.value = false
        }
    }

    fun updateAssistInput(raw: String) {
        _assistTargetInput.value = raw.filter { it.isDigit() }.take(9)
    }

    fun parseDeviceCode(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length != 9) return null
        NativeRuntimeBootstrap.ensureReady()
        return Core.deviceId(digits)
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

    fun enableHostWithProjection(resultCode: Int, data: Intent) =
        hostManager.enableHostWithProjection(resultCode, data)

    fun stopHost() = hostManager.stopHost()

    fun setHostAllowRemoteAssist(allow: Boolean) = hostManager.setAllowRemoteAssist(allow)

    fun setHostAllowAssistFileTransfer(allow: Boolean) = hostManager.setAllowAssistFileTransfer(allow)

    fun onMediaProjectionDenied() {
        RcErrorHub.present(
            RcUiErrorCodec.resolvePlatformError(
                code = RcErrorCodes.PLATFORM_ANDROID_MEDIA_PROJECTION_DENIED,
            ),
        )
    }

    fun respondHostApproval(generation: Long, accepted: Boolean) =
        hostManager.respondApproval(generation, accepted)

    fun disconnectHostClient() = hostManager.disconnectClient()

    fun rotateHostTempCode() = hostManager.rotateTempCode()

    fun refreshAccessibilityState() = hostManager.refreshAccessibilityState()
}
