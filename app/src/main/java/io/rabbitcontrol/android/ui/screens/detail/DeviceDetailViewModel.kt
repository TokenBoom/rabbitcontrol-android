package io.rabbitcontrol.android.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.DeviceToolKind
import io.rabbitcontrol.android.core.data.TargetHistoryStore
import io.rabbitcontrol.android.core.data.ToolOrderStore
import io.rabbitcontrol.android.core.data.WallpaperCache
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import io.rabbitcontrol.android.core.session.ActiveSessionState
import io.rabbitcontrol.android.core.session.SessionManager
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

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private val _removing = MutableStateFlow(false)
    val removing: StateFlow<Boolean> = _removing.asStateFlow()

    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()

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
                    _connectError.value = err.message?.let(NativeErrorLocalizer::localize)
                        ?: AppStrings.get(R.string.detail_err_connect_failed, "连接失败")
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
                    _connectError.value = err.message?.let(NativeErrorLocalizer::localize)
                        ?: AppStrings.get(R.string.detail_err_unbind_failed, "解绑失败")
                }
        }
    }
}
