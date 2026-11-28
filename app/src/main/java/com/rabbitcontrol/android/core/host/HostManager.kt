package com.rabbitcontrol.android.core.host

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcUiError
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.data.AccountRepository
import com.rabbitcontrol.android.core.data.HostAuthMode
import com.rabbitcontrol.android.core.data.InteractionPrefsStore
import com.rabbitcontrol.android.core.error.RcUiErrorCodec
import com.rabbitcontrol.android.core.i18n.AppStrings
import com.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import com.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import com.rabbitcontrol.android.session.Core
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class HostApprovalPromptModel(
    val generation: Long,
    val sessionId: Long,
    val remoteAddr: String,
    val requestedRes: String,
    val requestedFps: Int,
    val passwordVerified: Boolean,
)

data class HostUiState(
    val enabled: Boolean = false,
    val starting: Boolean = false,
    val listening: Boolean = false,
    val listenAddr: String = "",
    val displayAddr: String = "",
    val connectedPeer: String? = null,
    val connectedIsOwner: Boolean = false,
    val pendingApproval: HostApprovalPromptModel? = null,
    val tempCode: String? = null,
    val authMode: HostAuthMode = HostAuthMode.TempCode,
    val allowRemoteAssist: Boolean = false,
    val allowAssistFileTransfer: Boolean = true,
    val ownerSelfControlReady: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val lastError: String? = null,
    val lastUiError: RcUiError? = null,
)

class HostManager(
    private val context: Context,
    private val accountRepo: AccountRepository,
    private val prefsStore: InteractionPrefsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var screenEncoder: HostScreenEncoder? = null
    @Volatile
    private var pendingStartEncoderCmd: JSONObject? = null

    private val _state = MutableStateFlow(
        HostUiState(
            authMode = prefsStore.state.value.hostAuthMode,
            allowRemoteAssist = prefsStore.state.value.hostAllowRemoteAssist,
            allowAssistFileTransfer = prefsStore.state.value.hostAllowAssistFileTransfer,
            accessibilityEnabled = RabbitAccessibilityService.isEnabled(context),
        ),
    )
    val state: StateFlow<HostUiState> = _state.asStateFlow()

    init {
        scope.launch {
            RabbitAccessibilityService.isConnected.collect { connected ->
                _state.value = _state.value.copy(
                    accessibilityEnabled = connected || RabbitAccessibilityService.isEnabled(context),
                )
            }
        }
        scope.launch {
            accountRepo.profile.collect { prof ->
                if (prof == null && (_state.value.enabled || _state.value.starting)) {
                    stopHost()
                }
            }
        }
    }

    fun refreshAccessibilityState() {
        _state.value = _state.value.copy(
            accessibilityEnabled = RabbitAccessibilityService.isEnabled(context),
        )
    }

    fun enableHostWithProjection(resultCode: Int, data: Intent) {
        val prefs = prefsStore.state.value
        val allowAssist = prefs.hostAllowRemoteAssist
        val allowAssistFiles = prefs.hostAllowAssistFileTransfer
        val authMode = prefs.hostAuthMode
        val fixedPw = prefs.hostFixedPassword.trim()
        _state.value = _state.value.copy(
            starting = true,
            authMode = authMode,
            allowRemoteAssist = allowAssist,
            allowAssistFileTransfer = allowAssistFiles,
            lastError = null,
            lastUiError = null,
            accessibilityEnabled = RabbitAccessibilityService.isEnabled(context),
        )
        scope.launch {
            NativeRuntimeBootstrap.ensureReady(context)
            val (screenW, screenH, _) = queryDisplayMetrics()
            Core.setHostScreenMetrics(screenW, screenH)

            val effectiveMode = if (authMode == HostAuthMode.FixedPassword && fixedPw.isEmpty()) {
                HostAuthMode.TempCode
            } else {
                authMode
            }
            val req = JSONObject()
                .put("allowRemoteAssist", allowAssist)
                .put("allowAssistFileTransfer", allowAssistFiles)
                .put("requireApproval", true)
                .put("authMode", effectiveMode.wireValue)
            if (effectiveMode == HostAuthMode.FixedPassword && fixedPw.isNotEmpty()) {
                req.put("password", fixedPw)
            }

            val res = runCatching { JSONObject(Core.startHost(req.toString())) }
            res.onSuccess { obj ->
                accountRepo.setBackgroundKeepAlive(true)
                val pw = obj.optString("password").takeIf { it.isNotBlank() && it != "null" }
                val ownerReady = obj.optBoolean("ownerSelfControlReady", false)
                val listenAddr = obj.optString("listenAddr")
                _state.value = _state.value.copy(
                    enabled = true,
                    starting = false,
                    listening = true,
                    listenAddr = listenAddr,
                    tempCode = pw,
                    allowRemoteAssist = obj.optBoolean("allowRemoteAssist", allowAssist),
                    allowAssistFileTransfer = obj.optBoolean("allowAssistFileTransfer", allowAssistFiles),
                    ownerSelfControlReady = ownerReady,
                    lastError = null,
                    lastUiError = null,
                )
                Log.i("RC_DIAG", "startHost ok addr=$listenAddr ownerReady=$ownerReady allowAssist=$allowAssist allowAssistFiles=$allowAssistFiles")
                withContext(Dispatchers.Main) {
                    HostForegroundService.start(context, resultCode, data)
                }
                startPolling()
            }.onFailure { err ->
                Log.e("RC_DIAG", "startHost failed", err)
                val uiErr = RcUiErrorCodec.fromThrowable(
                    err,
                    fallbackCode = RcErrorCodes.SESSION_HOST_NOT_READY,
                    fallbackMessage = AppStrings.get(R.string.host_err_start_failed, "启动被控服务失败"),
                )
                _state.value = _state.value.copy(
                    enabled = false,
                    starting = false,
                    listening = false,
                    lastError = uiErr.message.ifBlank { uiErr.title },
                    lastUiError = uiErr,
                )
            }
        }
    }

    fun onMediaProjectionReady(projection: MediaProjection) {
        val (_, _, density) = queryDisplayMetrics()
        screenEncoder?.releaseAll()
        val enc = HostScreenEncoder(projection, density)
        screenEncoder = enc
        Log.i("RC_DIAG", "HostManager MediaProjection ready density=$density")
        val pending = pendingStartEncoderCmd
        if (pending != null) {
            pendingStartEncoderCmd = null
            Log.i("RC_DIAG", "HostManager replaying early encoder start cmd")
            enc.startOrReconfigure(
                width = pending.optInt("width", 1080),
                height = pending.optInt("height", 2400),
                fps = pending.optInt("fps", 60),
                bitrateBps = pending.optInt("bitrate_bps", 8_000_000),
            )
        }
    }

    fun onProjectionFailed(message: String) {
        val uiErr = RcUiErrorCodec.resolvePlatformError(
            code = RcErrorCodes.PLATFORM_ANDROID_MEDIA_PROJECTION_DENIED,
            detail = message,
            fallbackMessage = message,
        )
        _state.value = _state.value.copy(
            lastError = uiErr.message.ifBlank { message },
            lastUiError = uiErr,
        )
        stopHost()
    }

    fun onSystemProjectionStopped() {
        stopHost()
    }

    fun stopHost() {
        accountRepo.setBackgroundKeepAlive(false)
        pendingStartEncoderCmd = null
        pollJob?.cancel()
        pollJob = null
        screenEncoder?.releaseAll()
        screenEncoder = null
        if (NativeRuntimeBootstrap.isReady) {
            scope.launch {
                runCatching { Core.stopHost() }
            }
        }
        HostForegroundService.stop(context)
        _state.value = _state.value.copy(
            enabled = false,
            starting = false,
            listening = false,
            listenAddr = "",
            displayAddr = "",
            connectedPeer = null,
            connectedIsOwner = false,
            pendingApproval = null,
            tempCode = null,
        )
        Log.i("RC_DIAG", "HostManager stopped")
    }

    fun setAllowRemoteAssist(allow: Boolean) {
        prefsStore.update { it.copy(hostAllowRemoteAssist = allow) }
        _state.value = _state.value.copy(allowRemoteAssist = allow)
        if (!_state.value.enabled) return
        scope.launch {
            runCatching {
                JSONObject(
                    Core.hostCommand(
                        JSONObject().put("type", "set_allow_remote_assist").put("enabled", allow).toString(),
                    ),
                )
            }.onSuccess { resp ->
                val pw = resp.optString("password").takeIf { it.isNotBlank() && it != "null" }
                _state.value = _state.value.copy(
                    allowRemoteAssist = allow,
                    tempCode = if (allow) pw else null,
                )
            }.onFailure { err ->
                val uiErr = RcUiErrorCodec.fromThrowable(err, fallbackCode = RcErrorCodes.SESSION_HOST_NOT_READY)
                _state.value = _state.value.copy(
                    lastError = uiErr.message,
                    lastUiError = uiErr,
                )
            }
        }
    }

    fun setAllowAssistFileTransfer(allowed: Boolean) {
        prefsStore.update { it.copy(hostAllowAssistFileTransfer = allowed) }
        _state.value = _state.value.copy(allowAssistFileTransfer = allowed)
        if (!_state.value.enabled) return
        scope.launch {
            runCatching {
                JSONObject(
                    Core.hostCommand(
                        JSONObject().put("type", "set_allow_assist_file_transfer").put("allowed", allowed).toString(),
                    ),
                )
            }.onFailure { err ->
                val uiErr = RcUiErrorCodec.fromThrowable(err, fallbackCode = RcErrorCodes.SESSION_HOST_NOT_READY)
                _state.value = _state.value.copy(
                    lastError = uiErr.message,
                    lastUiError = uiErr,
                )
            }
        }
    }

    fun clearLastError() {
        _state.value = _state.value.copy(lastError = null, lastUiError = null)
    }

    fun respondApproval(generation: Long, accepted: Boolean) {
        _state.value = _state.value.copy(pendingApproval = null)
        scope.launch {
            runCatching {
                Core.hostCommand(
                    JSONObject()
                        .put("type", "respond_approval")
                        .put("generation", generation)
                        .put("accepted", accepted)
                        .toString(),
                )
            }
        }
    }

    fun disconnectClient() {
        scope.launch {
            runCatching {
                Core.hostCommand(JSONObject().put("type", "disconnect_client").toString())
            }
        }
    }

    fun rotateTempCode() {
        if (!_state.value.enabled || !_state.value.allowRemoteAssist) return
        if (_state.value.authMode != HostAuthMode.TempCode) return
        scope.launch {
            runCatching {
                JSONObject(Core.hostCommand(JSONObject().put("type", "rotate_password").toString()))
            }.onSuccess { resp ->
                val pw = resp.optString("password").takeIf { it.isNotBlank() && it != "null" }
                if (pw != null) {
                    _state.value = _state.value.copy(tempCode = pw)
                }
            }
        }
    }

    fun setHostAuthMode(mode: HostAuthMode, fixedPassword: String? = null) {
        val cleanFixed = fixedPassword?.trim() ?: prefsStore.state.value.hostFixedPassword
        if (mode == HostAuthMode.FixedPassword && cleanFixed.isBlank()) {
            prefsStore.update { it.copy(hostAuthMode = mode) }
            _state.value = _state.value.copy(authMode = mode)
            return
        }
        prefsStore.update {
            it.copy(
                hostAuthMode = mode,
                hostFixedPassword = if (fixedPassword != null) cleanFixed else it.hostFixedPassword,
            )
        }
        _state.value = _state.value.copy(authMode = mode)
        if (!_state.value.enabled) return
        scope.launch {
            val cmd = JSONObject()
                .put("type", "set_password_mode")
                .put("mode", mode.wireValue)
            if (mode == HostAuthMode.FixedPassword) {
                cmd.put("password", cleanFixed)
            }
            runCatching {
                JSONObject(Core.hostCommand(cmd.toString()))
            }.onSuccess { resp ->
                val pw = resp.optString("password").takeIf { it.isNotBlank() && it != "null" }
                _state.value = _state.value.copy(
                    authMode = mode,
                    tempCode = if (mode == HostAuthMode.ApprovalOnly) null else pw,
                )
            }.onFailure { err ->
                val uiErr = RcUiErrorCodec.fromThrowable(err, fallbackCode = RcErrorCodes.SESSION_HOST_NOT_READY)
                _state.value = _state.value.copy(
                    lastError = uiErr.message,
                    lastUiError = uiErr,
                )
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                runCatching {
                    val raw = Core.pollHostEvents()
                    if (raw != null) {
                        val payload = JSONObject(raw)
                        payload.optJSONArray("updates")?.let { updates ->
                            for (i in 0 until updates.length()) {
                                handleHostUpdate(updates.getJSONObject(i))
                            }
                        }
                        payload.optJSONArray("encoderCommands")?.let { encCmds ->
                            for (i in 0 until encCmds.length()) {
                                handleEncoderCommand(encCmds.getJSONObject(i))
                            }
                        }
                        payload.optJSONArray("inputEvents")?.let { inputEvs ->
                            for (i in 0 until inputEvs.length()) {
                                handleInputEvent(inputEvs.getJSONObject(i))
                            }
                        }
                    }
                }.onFailure { err ->
                    Log.w("RC_DIAG", "pollHostEvents error: ${err.message}")
                }
                delay(16L)
            }
        }
    }

    private fun handleHostUpdate(u: JSONObject) {
        when (u.optString("kind")) {
            "listening" -> {
                _state.value = _state.value.copy(
                    listening = true,
                    listenAddr = u.optString("listen_addr", _state.value.listenAddr),
                    displayAddr = u.optString("display_addr"),
                )
            }
            "approval_requested" -> {
                _state.value = _state.value.copy(
                    pendingApproval = HostApprovalPromptModel(
                        generation = u.optLong("generation"),
                        sessionId = u.optLong("session_id"),
                        remoteAddr = u.optString("remote_addr"),
                        requestedRes = u.optString("requested_res"),
                        requestedFps = u.optInt("requested_fps", 60),
                        passwordVerified = u.optBoolean("password_verified"),
                    ),
                )
            }
            "approval_cancelled" -> {
                val gen = u.optLong("generation")
                val cur = _state.value.pendingApproval
                if (cur != null && cur.generation == gen) {
                    _state.value = _state.value.copy(pendingApproval = null)
                }
            }
            "client_connected" -> {
                _state.value = _state.value.copy(
                    pendingApproval = null,
                    connectedPeer = u.optString("remote_addr"),
                    connectedIsOwner = false,
                )
            }
            "owner_connected" -> {
                _state.value = _state.value.copy(
                    pendingApproval = null,
                    connectedPeer = u.optString("caller_device_id").ifBlank { u.optString("remote_addr") },
                    connectedIsOwner = true,
                )
            }
            "client_disconnected" -> {
                _state.value = _state.value.copy(
                    connectedPeer = null,
                    connectedIsOwner = false,
                    pendingApproval = null,
                )
            }
            "password_rotated" -> {
                val pw = u.optString("password").takeIf { it.isNotBlank() }
                if (pw != null) {
                    _state.value = _state.value.copy(tempCode = pw)
                }
            }
            "stopped" -> {
                _state.value = _state.value.copy(
                    listening = false,
                    connectedPeer = null,
                    pendingApproval = null,
                )
            }
            "error" -> {
                val uiErr = RcUiErrorCodec.fromJsonObject(u.optJSONObject("ui_error"))
                    ?: RcUiErrorCodec.resolvePlatformError(
                        code = RcErrorCodes.SESSION_HOST_NOT_READY,
                        detail = u.optString("message"),
                    )
                _state.value = _state.value.copy(
                    lastError = uiErr.message.ifBlank { u.optString("message") },
                    lastUiError = uiErr,
                )
            }
        }
    }

    private fun handleEncoderCommand(cmd: JSONObject) {
        val kind = cmd.optString("kind")
        val enc = screenEncoder
        if (enc == null) {
            when (kind) {
                "start" -> pendingStartEncoderCmd = cmd
                "stop" -> pendingStartEncoderCmd = null
            }
            return
        }
        when (kind) {
            "start" -> {
                pendingStartEncoderCmd = null
                enc.startOrReconfigure(
                    width = cmd.optInt("width", 1080),
                    height = cmd.optInt("height", 2400),
                    fps = cmd.optInt("fps", 60),
                    bitrateBps = cmd.optInt("bitrate_bps", 8_000_000),
                )
            }
            "stop" -> {
                pendingStartEncoderCmd = null
                enc.pauseEncoder()
            }
            "set_bitrate" -> enc.setBitrate(cmd.optInt("bps", 8_000_000))
            "request_idr" -> enc.requestIdr()
        }
    }

    private fun handleInputEvent(ev: JSONObject) {
        val acc = RabbitAccessibilityService.instance ?: return
        when (ev.optString("kind")) {
            "mouse_move" -> acc.handleMouseMove(ev.optInt("x"), ev.optInt("y"))
            "mouse_button" -> acc.handleMouseButton(ev.optInt("button"), ev.optBoolean("pressed"))
            "mouse_wheel" -> acc.handleMouseWheel(ev.optInt("delta"), ev.optBoolean("horizontal"))
            "key" -> acc.handleKey(ev.optInt("scancode"), ev.optBoolean("pressed"), ev.optInt("modifiers"))
            "lock_screen" -> acc.handleLockScreen()
        }
    }

    private fun queryDisplayMetrics(): Triple<Int, Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(dm)
        val dpi = if (dm.densityDpi > 0) dm.densityDpi else 420
        if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            if (b.width() > 0 && b.height() > 0) return Triple(b.width(), b.height(), dpi)
        }
        val w = if (dm.widthPixels > 0) dm.widthPixels else 1080
        val h = if (dm.heightPixels > 0) dm.heightPixels else 2400
        return Triple(w, h, dpi)
    }
}
