package io.rabbitcontrol.android.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcUiError
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.error.RcUiErrorCodec
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.nativebridge.ServerConfigModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthStep { Server, Trust, Login, Register, Password }

sealed interface ServerTestState {
    data object Testing : ServerTestState
    data class Reachable(val latencyMs: Long, val healthy: Boolean) : ServerTestState
    data class Failed(val error: RcUiError) : ServerTestState
}

data class AuthUiState(
    val step: AuthStep = AuthStep.Server,
    val serverName: String = "",
    val serverAddress: String = "",
    val apiPort: String = "",
    val signalPort: String = "",
    val relayPort: String = "",
    val caPin: String = "",
    val editingServer: ServerConfigModel? = null,
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
    val uiError: RcUiError? = null,
    val savedServerAddress: String? = null,
    val serverTests: Map<String, ServerTestState> = emptyMap(),
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
            apiPort = "",
            signalPort = "",
            relayPort = "",
            caPin = "",
            editingServer = null,
            savedServerAddress = srv?.displayName,
            allowRegistration = srv?.allowRegistration ?: true,
            uiError = null,
        )
        if ((step == AuthStep.Login || step == AuthStep.Register) && srv != null) {
            refreshAuthConfig(srv)
        }
    }

    fun updateServerName(v: String) { _ui.value = _ui.value.copy(serverName = v, uiError = null) }
    fun updateServerAddress(v: String) { _ui.value = _ui.value.copy(serverAddress = v, uiError = null) }
    fun updateApiPort(v: String) {
        _ui.value = _ui.value.copy(apiPort = v.filter(Char::isDigit).take(5), uiError = null)
    }
    fun updateSignalPort(v: String) {
        _ui.value = _ui.value.copy(signalPort = v.filter(Char::isDigit).take(5), uiError = null)
    }
    fun updateRelayPort(v: String) {
        _ui.value = _ui.value.copy(relayPort = v.filter(Char::isDigit).take(5), uiError = null)
    }
    fun updateCaPin(v: String) { _ui.value = _ui.value.copy(caPin = v, uiError = null) }

    fun startAddServer() {
        _ui.value = _ui.value.copy(
            serverName = "",
            serverAddress = "",
            apiPort = "",
            signalPort = "",
            relayPort = "",
            caPin = "",
            editingServer = null,
            uiError = null,
        )
    }

    fun startEditServer(target: ServerConfigModel) {
        _ui.value = _ui.value.copy(
            serverName = target.name,
            serverAddress = target.address,
            apiPort = if (target.apiPort == ServerConfigModel.DEFAULT_API_PORT) "" else target.apiPort.toString(),
            signalPort = if (target.signalPort == ServerConfigModel.DEFAULT_SIGNAL_PORT) "" else target.signalPort.toString(),
            relayPort = if (target.relayPort == ServerConfigModel.DEFAULT_RELAY_PORT) "" else target.relayPort.toString(),
            caPin = "",
            editingServer = target,
            uiError = null,
        )
    }
    fun updateEmail(v: String) { _ui.value = _ui.value.copy(email = v, uiError = null) }
    fun updateDisplayName(v: String) { _ui.value = _ui.value.copy(displayName = v, uiError = null) }
    fun updatePassword(v: String) { _ui.value = _ui.value.copy(password = v, uiError = null) }
    fun updateRegisterConfirmPassword(v: String) { _ui.value = _ui.value.copy(registerConfirmPassword = v, uiError = null) }
    fun updateCurrentPassword(v: String) { _ui.value = _ui.value.copy(currentPassword = v, uiError = null) }
    fun updateNewPassword(v: String) { _ui.value = _ui.value.copy(newPassword = v, uiError = null) }
    fun updateConfirmPassword(v: String) { _ui.value = _ui.value.copy(confirmPassword = v, uiError = null) }

    fun clearError() { _ui.value = _ui.value.copy(uiError = null) }

    fun goToServerEdit() {
        _ui.value = _ui.value.copy(step = AuthStep.Server, uiError = null)
    }

    fun backFromTrust() {
        _ui.value = _ui.value.copy(step = AuthStep.Server, uiError = null)
    }

    fun switchAuthMode(register: Boolean) {
        val nextStep = if (register) AuthStep.Register else AuthStep.Login
        val srv = accountRepo.server.value
        _ui.value = _ui.value.copy(
            step = nextStep,
            allowRegistration = srv?.allowRegistration ?: true,
            uiError = null,
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

    private fun parseOptionalPort(raw: String, defaultPort: Int, label: String): Result<Int> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.success(defaultPort)
        val parsed = trimmed.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: return Result.failure(
                IllegalArgumentException(
                    AppStrings.get(R.string.auth_err_port_range, "$label 需为 1–65535 之间的数字", label),
                ),
            )
        return Result.success(parsed)
    }

    fun needsReprobeForEdit(editing: ServerConfigModel): Boolean {
        val hostChanged = _ui.value.serverAddress.trim() != editing.address.trim()
        val rawApiPort = _ui.value.apiPort.trim()
        val effectiveApiPort = if (rawApiPort.isEmpty()) {
            ServerConfigModel.DEFAULT_API_PORT
        } else {
            rawApiPort.toIntOrNull() ?: -1
        }
        val apiPortChanged = effectiveApiPort != editing.apiPort
        val pinProvided = _ui.value.caPin.isNotBlank()
        return hostChanged || apiPortChanged || pinProvided
    }

    fun submitServerForm(onMetadataSaved: () -> Unit) {
        val editing = _ui.value.editingServer
        val host = _ui.value.serverAddress.trim()
        val name = _ui.value.serverName.trim()
        if (host.isEmpty()) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.auth_err_enter_address, "请输入服务器地址"),
                ),
            )
            return
        }
        val apiPort = parseOptionalPort(
            _ui.value.apiPort,
            ServerConfigModel.DEFAULT_API_PORT,
            AppStrings.get(R.string.auth_api_port_label, "API 端口"),
        )
            .getOrElse {
                _ui.value = _ui.value.copy(
                    uiError = RcUiErrorCodec.inlineValidation(it.message.orEmpty()),
                )
                return
            }
        val signalPort = parseOptionalPort(
            _ui.value.signalPort,
            ServerConfigModel.DEFAULT_SIGNAL_PORT,
            AppStrings.get(R.string.auth_signal_port_label, "信令端口"),
        )
            .getOrElse {
                _ui.value = _ui.value.copy(
                    uiError = RcUiErrorCodec.inlineValidation(it.message.orEmpty()),
                )
                return
            }
        val relayPort = parseOptionalPort(
            _ui.value.relayPort,
            ServerConfigModel.DEFAULT_RELAY_PORT,
            AppStrings.get(R.string.auth_relay_port_label, "中继端口"),
        )
            .getOrElse {
                _ui.value = _ui.value.copy(
                    uiError = RcUiErrorCodec.inlineValidation(it.message.orEmpty()),
                )
                return
            }

        if (editing != null && !needsReprobeForEdit(editing)) {
            _ui.value = _ui.value.copy(busy = true, uiError = null)
            viewModelScope.launch {
                runCatching { accountRepo.updateServerMetadata(editing, name, signalPort, relayPort) }
                    .onSuccess {
                        _ui.value = _ui.value.copy(
                            busy = false,
                            editingServer = null,
                            savedServerAddress = accountRepo.server.value?.displayName,
                            serverTests = _ui.value.serverTests - editing.id,
                        )
                        onMetadataSaved()
                    }
                    .onFailure { err ->
                        _ui.value = _ui.value.copy(
                            busy = false,
                            uiError = RcUiErrorCodec.fromThrowable(
                                err,
                                fallbackCode = RcErrorCodes.SERVER_UNAVAILABLE,
                                fallbackMessage = AppStrings.get(R.string.err_auth_switch_server_failed, "切换服务器失败，请重试"),
                            ),
                        )
                    }
            }
            return
        }

        val apiPortOverride = if (_ui.value.apiPort.isNotBlank()) apiPort else null
        probeServerInternal(host = host, name = name, apiPortOverride = apiPortOverride, signalPort = signalPort, relayPort = relayPort)
    }

    fun probeServer() {
        val host = _ui.value.serverAddress.trim()
        val name = _ui.value.serverName.trim()
        if (host.isEmpty()) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.auth_err_enter_address, "请输入服务器地址"),
                ),
            )
            return
        }
        val apiPort = parseOptionalPort(
            _ui.value.apiPort,
            ServerConfigModel.DEFAULT_API_PORT,
            AppStrings.get(R.string.auth_api_port_label, "API 端口"),
        )
            .getOrElse {
                _ui.value = _ui.value.copy(uiError = RcUiErrorCodec.inlineValidation(it.message.orEmpty()))
                return
            }
        val signalPort = parseOptionalPort(
            _ui.value.signalPort,
            ServerConfigModel.DEFAULT_SIGNAL_PORT,
            AppStrings.get(R.string.auth_signal_port_label, "信令端口"),
        )
            .getOrElse {
                _ui.value = _ui.value.copy(uiError = RcUiErrorCodec.inlineValidation(it.message.orEmpty()))
                return
            }
        val relayPort = parseOptionalPort(
            _ui.value.relayPort,
            ServerConfigModel.DEFAULT_RELAY_PORT,
            AppStrings.get(R.string.auth_relay_port_label, "中继端口"),
        )
            .getOrElse {
                _ui.value = _ui.value.copy(uiError = RcUiErrorCodec.inlineValidation(it.message.orEmpty()))
                return
            }
        val apiPortOverride = if (_ui.value.apiPort.isNotBlank()) apiPort else null
        probeServerInternal(host = host, name = name, apiPortOverride = apiPortOverride, signalPort = signalPort, relayPort = relayPort)
    }

    private fun probeServerInternal(
        host: String,
        name: String,
        apiPortOverride: Int?,
        signalPort: Int,
        relayPort: Int,
    ) {
        val pin = _ui.value.caPin.replace("\\s".toRegex(), "")
        _ui.value = _ui.value.copy(busy = true, uiError = null)
        viewModelScope.launch {
            runCatching {
                accountRepo.probeServer(
                    address = host,
                    expectedPin = pin,
                    name = name,
                    apiPort = apiPortOverride,
                    signalPort = signalPort,
                    relayPort = relayPort,
                )
            }
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
                    _ui.value = _ui.value.copy(
                        busy = false,
                        uiError = RcUiErrorCodec.fromThrowable(
                            err,
                            fallbackCode = RcErrorCodes.NETWORK_UNREACHABLE,
                            fallbackMessage = AppStrings.get(R.string.err_auth_probe_failed, "无法连接到服务器，请检查地址和端口"),
                        ),
                    )
                }
        }
    }

    fun trustAndContinue(onlyServer: Boolean, onDone: () -> Unit) {
        val candidate = _ui.value.candidateServer ?: return
        val replaceTargetId = _ui.value.editingServer?.id
        _ui.value = _ui.value.copy(busy = true, uiError = null)
        viewModelScope.launch {
            runCatching { accountRepo.saveServer(candidate, replaceTargetId = replaceTargetId) }
                .onSuccess { restored ->
                    val needsPwd = accountRepo.profile.value?.needsPassword == true
                    _ui.value = _ui.value.copy(
                        busy = false,
                        savedServerAddress = candidate.displayName,
                        allowRegistration = candidate.allowRegistration,
                        serverName = "",
                        serverAddress = "",
                        apiPort = "",
                        signalPort = "",
                        relayPort = "",
                        caPin = "",
                        editingServer = null,
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
                    _ui.value = _ui.value.copy(
                        busy = false,
                        uiError = RcUiErrorCodec.fromThrowable(
                            err,
                            fallbackCode = RcErrorCodes.SERVER_UNAVAILABLE,
                            fallbackMessage = AppStrings.get(R.string.err_auth_switch_server_failed, "切换服务器失败，请重试"),
                        ),
                    )
                }
        }
    }

    fun selectSavedServer(target: ServerConfigModel, onDone: () -> Unit) {
        _ui.value = _ui.value.copy(busy = true, uiError = null)
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
                    _ui.value = _ui.value.copy(
                        busy = false,
                        uiError = RcUiErrorCodec.fromThrowable(
                            err,
                            fallbackCode = RcErrorCodes.SERVER_UNAVAILABLE,
                            fallbackMessage = AppStrings.get(R.string.err_auth_switch_server_failed, "切换服务器失败，请重试"),
                        ),
                    )
                }
        }
    }

    fun testServer(
        target: ServerConfigModel,
        onResult: ((ServerTestState) -> Unit)? = null,
    ) {
        if (_ui.value.serverTests[target.id] is ServerTestState.Testing) return
        _ui.value = _ui.value.copy(
            serverTests = _ui.value.serverTests + (target.id to ServerTestState.Testing),
        )
        viewModelScope.launch {
            val resultState = runCatching { accountRepo.testServerConnection(target) }
                .fold(
                    onSuccess = { probe ->
                        ServerTestState.Reachable(
                            latencyMs = probe.latencyMs,
                            healthy = probe.healthy,
                        )
                    },
                    onFailure = { err ->
                        ServerTestState.Failed(
                            RcUiErrorCodec.fromThrowable(
                                err,
                                fallbackCode = RcErrorCodes.NETWORK_UNREACHABLE,
                                fallbackMessage = AppStrings.get(R.string.err_auth_probe_failed, "无法连接到服务器，请检查地址和端口"),
                            ),
                        )
                    },
                )
            _ui.value = _ui.value.copy(
                serverTests = _ui.value.serverTests + (target.id to resultState),
            )
            onResult?.invoke(resultState)
        }
    }

    fun testAllServers(
        onFinished: ((reachable: Int, total: Int) -> Unit)? = null,
    ) {
        val list = servers.value
        if (list.isEmpty()) return
        val marked = list.associate { it.id to ServerTestState.Testing }
        _ui.value = _ui.value.copy(serverTests = _ui.value.serverTests + marked)
        viewModelScope.launch {
            val results = list.map { srv ->
                async {
                    val state = runCatching { accountRepo.testServerConnection(srv) }
                        .fold(
                            onSuccess = { probe ->
                                ServerTestState.Reachable(
                                    latencyMs = probe.latencyMs,
                                    healthy = probe.healthy,
                                )
                            },
                            onFailure = { err ->
                                ServerTestState.Failed(
                                    RcUiErrorCodec.fromThrowable(
                                        err,
                                        fallbackCode = RcErrorCodes.NETWORK_UNREACHABLE,
                                        fallbackMessage = AppStrings.get(R.string.err_auth_probe_failed, "无法连接到服务器，请检查地址和端口"),
                                    ),
                                )
                            },
                        )
                    _ui.value = _ui.value.copy(
                        serverTests = _ui.value.serverTests + (srv.id to state),
                    )
                    state
                }
            }.awaitAll()
            val okCount = results.count { it is ServerTestState.Reachable && it.healthy }
            onFinished?.invoke(okCount, list.size)
        }
    }

    fun removeSavedServer(target: ServerConfigModel) {
        viewModelScope.launch {
            runCatching { accountRepo.removeServer(target) }
                .onSuccess {
                    _ui.value = _ui.value.copy(
                        savedServerAddress = accountRepo.server.value?.displayName,
                        serverTests = _ui.value.serverTests - target.id,
                        uiError = null,
                    )
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(
                        uiError = RcUiErrorCodec.fromThrowable(
                            err,
                            fallbackCode = RcErrorCodes.COMMON_UNKNOWN,
                            fallbackMessage = AppStrings.get(R.string.err_auth_remove_server_failed, "删除服务器失败，请重试"),
                        ),
                    )
                }
        }
    }

    fun login(onCompleted: () -> Unit) {
        val email = _ui.value.email.trim()
        val pwd = _ui.value.password
        if (email.isEmpty()) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.auth_err_enter_email, "请输入邮箱"),
                ),
            )
            return
        }
        if (pwd.isEmpty()) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.auth_err_enter_password, "请输入密码"),
                ),
            )
            return
        }
        _ui.value = _ui.value.copy(busy = true, uiError = null)
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
                    _ui.value = _ui.value.copy(
                        busy = false,
                        uiError = RcUiErrorCodec.fromThrowable(
                            err,
                            fallbackCode = RcErrorCodes.AUTH_INVALID_CREDENTIALS,
                            fallbackMessage = AppStrings.get(R.string.err_auth_login_failed, "登录失败，请检查邮箱和密码"),
                        ),
                    )
                }
        }
    }

    fun register(onCompleted: () -> Unit) {
        if (!_ui.value.allowRegistration) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.resolvePlatformError(
                    code = RcErrorCodes.AUTH_REGISTRATION_DISABLED,
                    fallbackMessage = AppStrings.get(R.string.err_auth_reg_disabled, "管理员已关闭自助注册，请联系管理员开通"),
                ),
            )
            return
        }
        val email = _ui.value.email.trim()
        val name = _ui.value.displayName.trim()
        val pwd = _ui.value.password
        val confirm = _ui.value.registerConfirmPassword
        if (email.isEmpty() || !email.contains('@')) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.auth_err_invalid_email, "请输入有效的邮箱地址"),
                ),
            )
            return
        }
        if (pwd.length < 8) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.err_auth_password_min_len, "密码长度至少 8 位"),
                ),
            )
            return
        }
        if (pwd != confirm) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.auth_err_pw_mismatch, "两次输入的密码不一致"),
                ),
            )
            return
        }
        _ui.value = _ui.value.copy(busy = true, uiError = null)
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
                    _ui.value = _ui.value.copy(
                        busy = false,
                        uiError = RcUiErrorCodec.fromThrowable(
                            err,
                            fallbackCode = RcErrorCodes.VALIDATION_INVALID_INPUT,
                            fallbackMessage = AppStrings.get(R.string.err_auth_register_failed, "注册失败，请检查填写信息后重试"),
                        ),
                    )
                }
        }
    }

    fun changePassword(onCompleted: () -> Unit) {
        val cur = _ui.value.currentPassword
        val next = _ui.value.newPassword
        val confirm = _ui.value.confirmPassword
        if (cur.isEmpty() || next.isEmpty()) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.auth_err_enter_both_pws, "请输入当前密码和新密码"),
                ),
            )
            return
        }
        if (next != confirm) {
            _ui.value = _ui.value.copy(
                uiError = RcUiErrorCodec.inlineValidation(
                    AppStrings.get(R.string.auth_err_new_pw_mismatch, "两次输入的新密码不一致"),
                ),
            )
            return
        }
        _ui.value = _ui.value.copy(busy = true, uiError = null)
        viewModelScope.launch {
            runCatching { accountRepo.changePassword(cur, next) }
                .onSuccess {
                    _ui.value = _ui.value.copy(busy = false)
                    onCompleted()
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(
                        busy = false,
                        uiError = RcUiErrorCodec.fromThrowable(
                            err,
                            fallbackCode = RcErrorCodes.AUTH_INVALID_CURRENT_PASSWORD,
                            fallbackMessage = AppStrings.get(R.string.err_auth_change_pw_failed, "修改密码失败，请重试"),
                        ),
                    )
                }
        }
    }
}
