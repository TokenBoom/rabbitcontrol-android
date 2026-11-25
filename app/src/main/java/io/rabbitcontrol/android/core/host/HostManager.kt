package io.rabbitcontrol.android.core.host

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import io.rabbitcontrol.android.session.Core
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
    val allowRemoteAssist: Boolean = false,
    val ownerSelfControlReady: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val lastError: String? = null,
)

class HostManager(
    private val context: Context,
    private val accountRepo: AccountRepository,
    private val prefsStore: InteractionPrefsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var screenEncoder: HostScreenEncoder? = null

    private val _state = MutableStateFlow(
        HostUiState(
            allowRemoteAssist = prefsStore.state.value.hostAllowRemoteAssist,
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
        val allowAssist = prefsStore.state.value.hostAllowRemoteAssist
        _state.value = _state.value.copy(
            starting = true,
            allowRemoteAssist = allowAssist,
            lastError = null,
            accessibilityEnabled = RabbitAccessibilityService.isEnabled(context),
        )
        scope.launch {
            NativeRuntimeBootstrap.ensureReady(context)
            val (screenW, screenH, _) = queryDisplayMetrics()
            Core.setHostScreenMetrics(screenW, screenH)

            val req = JSONObject()
                .put("allowRemoteAssist", allowAssist)
                .put("requireApproval", true)

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
                    ownerSelfControlReady = ownerReady,
                    lastError = null,
                )
                Log.i("RC_DIAG", "startHost ok addr=$listenAddr ownerReady=$ownerReady allowAssist=$allowAssist")
                withContext(Dispatchers.Main) {
                    HostForegroundService.start(context, resultCode, data)
                }
                startPolling()
            }.onFailure { err ->
                Log.e("RC_DIAG", "startHost failed", err)
                _state.value = _state.value.copy(
                    enabled = false,
                    starting = false,
                    listening = false,
                    lastError = err.message ?: "启动被控服务失败",
                )
            }
        }
    }

    fun onMediaProjectionReady(projection: MediaProjection) {
        val (_, _, density) = queryDisplayMetrics()
        screenEncoder?.releaseAll()
        screenEncoder = HostScreenEncoder(projection, density)
        Log.i("RC_DIAG", "HostManager MediaProjection ready density=$density")
    }

    fun onProjectionFailed(message: String) {
        _state.value = _state.value.copy(lastError = message)
        stopHost()
    }

    fun onSystemProjectionStopped() {
        stopHost()
    }

    fun stopHost() {
        accountRepo.setBackgroundKeepAlive(false)
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
                _state.value = _state.value.copy(lastError = err.message)
            }
        }
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
                _state.value = _state.value.copy(lastError = u.optString("message"))
            }
        }
    }

    private fun handleEncoderCommand(cmd: JSONObject) {
        val enc = screenEncoder ?: return
        when (cmd.optString("kind")) {
            "start" -> enc.startOrReconfigure(
                width = cmd.optInt("width", 1080),
                height = cmd.optInt("height", 2400),
                fps = cmd.optInt("fps", 60),
                bitrateBps = cmd.optInt("bitrate_bps", 8_000_000),
            )
            "stop" -> enc.pauseEncoder()
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
