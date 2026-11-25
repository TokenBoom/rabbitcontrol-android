package io.rabbitcontrol.android.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.nativebridge.ServerConfigModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthStep { Server, Trust, Login, Register, Password }

data class AuthUiState(
    val step: AuthStep = AuthStep.Server,
    val serverName: String = "",
    val serverAddress: String = "",
    val caPin: String = "",
    val candidateServer: ServerConfigModel? = null,
    val probeReport: String = "",
    val email: String = "",
    val displayName: String = "",
    val password: String = "",
    val registerConfirmPassword: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val allowRegistration: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val savedServerAddress: String? = null,
)

class AuthViewModel(
    private val accountRepo: AccountRepository,
) : ViewModel() {
    val servers: StateFlow<List<ServerConfigModel>> = accountRepo.servers
    val activeServer: StateFlow<ServerConfigModel?> = accountRepo.server
    val savedAccountEmails: StateFlow<Map<String, String>> = accountRepo.savedAccountEmails

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun init(initialStep: AuthStep) {
        val srv = accountRepo.server.value
        val step = when {
            initialStep == AuthStep.Password && accountRepo.profile.value != null -> AuthStep.Password
            initialStep == AuthStep.Register && srv != null -> AuthStep.Register
            initialStep == AuthStep.Login && srv != null -> AuthStep.Login
            srv != null && initialStep != AuthStep.Server -> AuthStep.Login
            else -> AuthStep.Server
        }
        _ui.value = _ui.value.copy(
            step = step,
            serverName = "",
            serverAddress = "",
            caPin = "",
            savedServerAddress = srv?.displayName,
            allowRegistration = srv?.allowRegistration ?: true,
            error = null,
        )
        if ((step == AuthStep.Login || step == AuthStep.Register) && srv != null) {
            refreshAuthConfig(srv)
        }
    }

    fun updateServerName(v: String) { _ui.value = _ui.value.copy(serverName = v, error = null) }
    fun updateServerAddress(v: String) { _ui.value = _ui.value.copy(serverAddress = v, error = null) }
    fun updateCaPin(v: String) { _ui.value = _ui.value.copy(caPin = v, error = null) }
    fun updateEmail(v: String) { _ui.value = _ui.value.copy(email = v, error = null) }
    fun updateDisplayName(v: String) { _ui.value = _ui.value.copy(displayName = v, error = null) }
    fun updatePassword(v: String) { _ui.value = _ui.value.copy(password = v, error = null) }
    fun updateRegisterConfirmPassword(v: String) { _ui.value = _ui.value.copy(registerConfirmPassword = v, error = null) }
    fun updateCurrentPassword(v: String) { _ui.value = _ui.value.copy(currentPassword = v, error = null) }
    fun updateNewPassword(v: String) { _ui.value = _ui.value.copy(newPassword = v, error = null) }
    fun updateConfirmPassword(v: String) { _ui.value = _ui.value.copy(confirmPassword = v, error = null) }

    fun goToServerEdit() {
        _ui.value = _ui.value.copy(step = AuthStep.Server, error = null)
    }

    fun backFromTrust() {
        _ui.value = _ui.value.copy(step = AuthStep.Server, error = null)
    }

    fun switchAuthMode(register: Boolean) {
        val nextStep = if (register) AuthStep.Register else AuthStep.Login
        val srv = accountRepo.server.value
        _ui.value = _ui.value.copy(
            step = nextStep,
            allowRegistration = srv?.allowRegistration ?: true,
            error = null,
        )
        if (srv != null) {
            refreshAuthConfig(srv)
        }
    }

    private fun refreshAuthConfig(target: ServerConfigModel) {
        viewModelScope.launch {
            runCatching { accountRepo.fetchAuthConfig(target) }
                .onSuccess { allowReg ->
                    if (accountRepo.server.value?.id == target.id) {
                        _ui.value = _ui.value.copy(allowRegistration = allowReg)
                    }
                }
        }
    }

    fun probeServer() {
        val host = _ui.value.serverAddress.trim()
        val name = _ui.value.serverName.trim()
        val pin = _ui.value.caPin.replace("\\s".toRegex(), "")
        if (host.isEmpty()) {
            _ui.value = _ui.value.copy(error = "请输入服务器地址")
            return
        }
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { accountRepo.probeServer(host, pin, name) }
                .onSuccess { (model, report) ->
                    _ui.value = _ui.value.copy(
                        busy = false,
                        candidateServer = model,
                        probeReport = report,
                        allowRegistration = model.allowRegistration,
                        step = AuthStep.Trust,
                    )
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(busy = false, error = err.message ?: "服务器探测失败")
                }
        }
    }

    fun trustAndContinue(onlyServer: Boolean, onDone: () -> Unit) {
        val candidate = _ui.value.candidateServer ?: return
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { accountRepo.saveServer(candidate) }
                .onSuccess { restored ->
                    val needsPwd = accountRepo.profile.value?.needsPassword == true
                    _ui.value = _ui.value.copy(
                        busy = false,
                        savedServerAddress = candidate.displayName,
                        allowRegistration = candidate.allowRegistration,
                        serverName = "",
                        serverAddress = "",
                        caPin = "",
                        step = when {
                            restored && needsPwd -> AuthStep.Password
                            restored && onlyServer -> _ui.value.step
                            else -> AuthStep.Login
                        },
                    )
                    if (restored && onlyServer && !needsPwd) {
                        onDone()
                    }
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(busy = false, error = err.message ?: "保存服务器失败")
                }
        }
    }

    fun selectSavedServer(target: ServerConfigModel, onDone: () -> Unit) {
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { accountRepo.switchServer(target) }
                .onSuccess { restored ->
                    val needsPwd = accountRepo.profile.value?.needsPassword == true
                    if (restored && !needsPwd) {
                        _ui.value = _ui.value.copy(
                            busy = false,
                            savedServerAddress = target.displayName,
                            allowRegistration = target.allowRegistration,
                        )
                        onDone()
                    } else if (restored && needsPwd) {
                        _ui.value = _ui.value.copy(
                            busy = false,
                            savedServerAddress = target.displayName,
                            step = AuthStep.Password,
                        )
                    } else {
                        _ui.value = _ui.value.copy(
                            busy = false,
                            savedServerAddress = target.displayName,
                            allowRegistration = target.allowRegistration,
                            step = AuthStep.Login,
                        )
                        refreshAuthConfig(target)
                    }
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(busy = false, error = err.message ?: "切换服务器失败")
                }
        }
    }

    fun removeSavedServer(target: ServerConfigModel) {
        viewModelScope.launch {
            runCatching { accountRepo.removeServer(target) }
                .onSuccess {
                    _ui.value = _ui.value.copy(
                        savedServerAddress = accountRepo.server.value?.displayName,
                        error = null,
                    )
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(error = err.message ?: "删除服务器失败")
                }
        }
    }

    fun login(onCompleted: () -> Unit) {
        val email = _ui.value.email.trim()
        val pwd = _ui.value.password
        if (email.isEmpty()) {
            _ui.value = _ui.value.copy(error = "请输入邮箱")
            return
        }
        if (pwd.isEmpty()) {
            _ui.value = _ui.value.copy(error = "请输入密码")
            return
        }
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { accountRepo.login(email, pwd) }
                .onSuccess { profile ->
                    _ui.value = _ui.value.copy(busy = false)
                    if (profile.needsPassword) {
                        _ui.value = _ui.value.copy(step = AuthStep.Password, currentPassword = pwd)
                    } else {
                        onCompleted()
                    }
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(busy = false, error = err.message ?: "登录失败，请检查账号或密码")
                }
        }
    }

    fun register(onCompleted: () -> Unit) {
        if (!_ui.value.allowRegistration) {
            _ui.value = _ui.value.copy(error = "管理员已关闭自助注册，请联系管理员开通")
            return
        }
        val email = _ui.value.email.trim()
        val name = _ui.value.displayName.trim()
        val pwd = _ui.value.password
        val confirm = _ui.value.registerConfirmPassword
        if (email.isEmpty() || !email.contains('@')) {
            _ui.value = _ui.value.copy(error = "请输入有效的邮箱地址")
            return
        }
        if (pwd.length < 8) {
            _ui.value = _ui.value.copy(error = "密码长度至少 8 位")
            return
        }
        if (pwd != confirm) {
            _ui.value = _ui.value.copy(error = "两次输入的密码不一致")
            return
        }
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { accountRepo.register(email, name, pwd) }
                .onSuccess { profile ->
                    _ui.value = _ui.value.copy(busy = false)
                    if (profile.needsPassword) {
                        _ui.value = _ui.value.copy(step = AuthStep.Password, currentPassword = pwd)
                    } else {
                        onCompleted()
                    }
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(busy = false, error = err.message ?: "注册失败")
                }
        }
    }

    fun changePassword(onCompleted: () -> Unit) {
        val cur = _ui.value.currentPassword
        val next = _ui.value.newPassword
        val confirm = _ui.value.confirmPassword
        if (cur.isEmpty() || next.isEmpty()) {
            _ui.value = _ui.value.copy(error = "请输入当前密码和新密码")
            return
        }
        if (next != confirm) {
            _ui.value = _ui.value.copy(error = "两次输入的新密码不一致")
            return
        }
        _ui.value = _ui.value.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { accountRepo.changePassword(cur, next) }
                .onSuccess {
                    _ui.value = _ui.value.copy(busy = false)
                    onCompleted()
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(busy = false, error = err.message ?: "修改密码失败")
                }
        }
    }
}
