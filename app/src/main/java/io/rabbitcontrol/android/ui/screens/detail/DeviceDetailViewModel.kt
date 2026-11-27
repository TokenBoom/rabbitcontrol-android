package io.rabbitcontrol.android.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcUiError
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.TargetHistoryStore
import io.rabbitcontrol.android.core.data.ToolOrderStore
import io.rabbitcontrol.android.core.data.WallpaperCache
import io.rabbitcontrol.android.core.error.RcUiErrorCodec
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.ui.components.RcErrorHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceDetailViewModel(
    private val historyStore: TargetHistoryStore,
    private val toolOrderStore: ToolOrderStore,
    val wallpaperCache: WallpaperCache,
    private val sessionManager: SessionManager,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val toolOrder: StateFlow<List<DeviceToolKind>> = toolOrderStore.order
    val wallpaperVersion: StateFlow<Long> = wallpaperCache.version
    val activeSession: StateFlow<ActiveSessionState> = sessionManager.state
    val favoriteTargets: StateFlow<List<String>> = historyStore.favorites
    val devices: StateFlow<List<RemoteDevice>> = accountRepository.devices

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _removing = MutableStateFlow(false)
    val removing: StateFlow<Boolean> = _removing.asStateFlow()

    private val _wolSending = MutableStateFlow(false)
    val wolSending: StateFlow<Boolean> = _wolSending.asStateFlow()

    private val _connectError = MutableStateFlow<RcUiError?>(null)
    val connectError: StateFlow<RcUiError?> = _connectError.asStateFlow()

    fun lastConnectedLabel(deviceId: String): String = historyStore.formatLastConnected(deviceId)

    fun isFavorite(deviceId: String): Boolean = historyStore.isFavorite(deviceId)

    fun toggleFavorite(deviceId: String) = historyStore.toggleFavorite(deviceId)

    fun saveToolOrder(next: List<DeviceToolKind>) = toolOrderStore.saveOrder(next)

    fun clearError() {
        _connectError.value = null
    }

    fun startSessionAndNavigate(
        deviceId: String,
        deviceName: String,
        deviceOs: String,
        watchOnly: Boolean,
        password: String? = null,
        onReady: () -> Unit,
    ) {
        if (sessionManager.isConnectedTo(deviceId)) {
            if (sessionManager.state.value.watchOnly != watchOnly) {
                sessionManager.setWatchOnly(watchOnly)
            }
            onReady()
            return
        }
        _connecting.value = true
        _connectError.value = null
        viewModelScope.launch {
            val res = sessionManager.connect(
                targetId = deviceId,
                deviceName = deviceName,
                deviceOs = deviceOs,
                watchOnly = watchOnly,
                password = password,
            )
            _connecting.value = false
            res.onSuccess { onReady() }
                .onFailure { err ->
                    _connectError.value = RcUiErrorCodec.fromThrowable(
                        err,
                        fallbackCode = RcErrorCodes.SESSION_DISCONNECTED,
                        fallbackMessage = AppStrings.get(R.string.detail_err_connect_failed, "连接失败"),
                    )
                }
        }
    }

    fun sendWakeOnLan(deviceId: String, onSuccess: (String) -> Unit) {
        if (_wolSending.value) return
        _wolSending.value = true
        viewModelScope.launch {
            accountRepository.requestWol(deviceId)
                .onSuccess { msg ->
                    _wolSending.value = false
                    onSuccess(msg)
                }
                .onFailure { err ->
                    _wolSending.value = false
                    RcErrorHub.present(
                        RcUiErrorCodec.fromThrowable(err, fallbackCode = RcErrorCodes.WOL_SEND_FAILED),
                    )
                }
        }
    }

    fun removeDevice(deviceId: String, onRemoved: () -> Unit) {
        if (_removing.value) return
        _removing.value = true
        _connectError.value = null
        viewModelScope.launch {
            runCatching { accountRepository.removeDevice(deviceId) }
                .onSuccess {
                    _removing.value = false
                    onRemoved()
                }
                .onFailure { err ->
                    _removing.value = false
                    _connectError.value = RcUiErrorCodec.fromThrowable(
                        err,
                        fallbackCode = RcErrorCodes.COMMON_UNKNOWN,
                        fallbackMessage = AppStrings.get(R.string.detail_err_unbind_failed, "解绑失败"),
                    )
                }
        }
    }
}
