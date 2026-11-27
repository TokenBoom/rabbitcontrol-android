package io.rabbitcontrol.android.core.session

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.Immutable
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcUiError
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.TargetHistoryStore
import io.rabbitcontrol.android.core.data.WallpaperCache
import io.rabbitcontrol.android.core.error.RcUiErrorCodec
import io.rabbitcontrol.android.core.error.RcUiErrorException
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import io.rabbitcontrol.android.core.nativebridge.DisplayConfigModel
import io.rabbitcontrol.android.core.nativebridge.DisplayPresetModel
import io.rabbitcontrol.android.core.nativebridge.FileTransferItem
import io.rabbitcontrol.android.core.nativebridge.HudSnapshotModel
import io.rabbitcontrol.android.core.nativebridge.MediaDiagnosticsModel
import io.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import io.rabbitcontrol.android.core.nativebridge.NegotiatedStreamInfo
import io.rabbitcontrol.android.core.nativebridge.PortMapRule
import io.rabbitcontrol.android.core.nativebridge.RemoteFileEntry
import io.rabbitcontrol.android.core.nativebridge.TerminalSessionSummary
import io.rabbitcontrol.android.core.nativebridge.ToolPermissionsModel
import io.rabbitcontrol.android.core.nativebridge.toFileEntries
import io.rabbitcontrol.android.core.nativebridge.toFileTransferItem
import io.rabbitcontrol.android.core.nativebridge.toPortMapRule
import io.rabbitcontrol.android.session.Core
import io.rabbitcontrol.android.ui.components.RcErrorHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface SessionStatus {
    data object Idle : SessionStatus
    data class Connecting(val phase: String, val label: String) : SessionStatus
    data object Streaming : SessionStatus
    data class Switching(val reason: String) : SessionStatus
    data class Reconnecting(val attempt: Int, val maxAttempts: Int, val reason: String) : SessionStatus
    data class Error(val message: String, val uiError: RcUiError? = null) : SessionStatus
    data class Ended(
        val reason: String,
        val recoverable: Boolean = false,
        val uiError: RcUiError? = null,
    ) : SessionStatus
}

@Immutable
data class ActiveSessionState(
    val targetId: String = "",
    val deviceName: String = "",
    val deviceOs: String = "",
    val status: SessionStatus = SessionStatus.Idle,
    val watchOnly: Boolean = false,
    val audioMuted: Boolean = false,
    val clipboardEnabled: Boolean = true,
    val negotiated: NegotiatedStreamInfo? = null,
    val targetBitrateBps: Int? = null,
    val displayConfig: DisplayConfigModel? = null,
    val firstFrameSeen: Boolean = false,
    val toolPermissions: ToolPermissionsModel = ToolPermissionsModel(),
    val mediaPaused: Boolean = false,
)

sealed interface FileToolEvent {
    data class RemoteDir(val path: String, val entries: List<RemoteFileEntry>, val error: String?) : FileToolEvent
    data class MkdirResult(val path: String, val ok: Boolean, val error: String?) : FileToolEvent
    data class TransferUpdated(val item: FileTransferItem) : FileToolEvent
}

sealed interface TerminalToolEvent {
    data class SessionList(val sessions: List<TerminalSessionSummary>) : TerminalToolEvent
    data class Opened(
        val termId: Int,
        val name: String,
        val title: String,
        val cwd: String,
        val attached: Boolean,
        val ok: Boolean,
        val error: String?,
        val createdAtUnixSec: Long,
    ) : TerminalToolEvent
    data class Attached(
        val termId: Int,
        val ok: Boolean,
        val error: String?,
        val session: TerminalSessionSummary? = null,
    ) : TerminalToolEvent
    data class Output(val termId: Int, val bytes: ByteArray) : TerminalToolEvent {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Output && termId == other.termId && bytes.contentEquals(other.bytes))
        override fun hashCode(): Int = 31 * termId + bytes.contentHashCode()
    }
    data class Exited(val termId: Int, val exitCode: Int) : TerminalToolEvent
    data class Renamed(val termId: Int, val name: String, val ok: Boolean, val error: String?) : TerminalToolEvent
    data class Error(val termId: Int?, val message: String) : TerminalToolEvent
}

class SessionManager(
    private val context: Context,
    private val historyStore: TargetHistoryStore,
    private val wallpaperCache: WallpaperCache,
    private val prefsStore: InteractionPrefsStore,
    private val clipboardBridge: ClipboardSyncBridge,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var backgroundLockJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var lastRequestTarget: String = ""
    private var lastRequestName: String = ""
    private var lastRequestOs: String = ""
    private var lastPassword: String? = null
    private var lastForceRelay: Boolean = false

    private val _state = MutableStateFlow(ActiveSessionState())
    val state: StateFlow<ActiveSessionState> = _state.asStateFlow()

    private val _hud = MutableStateFlow(HudSnapshotModel())
    val hud: StateFlow<HudSnapshotModel> = _hud.asStateFlow()

    private val _media = MutableStateFlow(MediaDiagnosticsModel())
    val media: StateFlow<MediaDiagnosticsModel> = _media.asStateFlow()

    @Volatile
    private var mediaDiagnosticsVisible: Boolean = false

    private val _fileEvents = MutableSharedFlow<FileToolEvent>(extraBufferCapacity = 128)
    val fileEvents: SharedFlow<FileToolEvent> = _fileEvents.asSharedFlow()

    private val _terminalEvents = MutableSharedFlow<TerminalToolEvent>(extraBufferCapacity = 256)
    val terminalEvents: SharedFlow<TerminalToolEvent> = _terminalEvents.asSharedFlow()

    private val _portMapRules = MutableStateFlow<List<PortMapRule>>(emptyList())
    val portMapRules: StateFlow<List<PortMapRule>> = _portMapRules.asStateFlow()

    fun setMediaDiagnosticsVisible(visible: Boolean) {
        mediaDiagnosticsVisible = visible
    }

    fun isConnectedTo(targetId: String): Boolean {
        val cur = _state.value
        return cur.targetId == targetId && (cur.status is SessionStatus.Streaming || cur.status is SessionStatus.Connecting)
    }

    suspend fun connect(
        targetId: String,
        deviceName: String,
        deviceOs: String,
        watchOnly: Boolean,
        password: String? = null,
        forceRelay: Boolean = false,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val prefs = prefsStore.state.value
        lastRequestTarget = targetId
        lastRequestName = deviceName
        lastRequestOs = deviceOs
        lastPassword = password
        lastForceRelay = forceRelay

        NativeRuntimeBootstrap.ensureReady(context)
        acquireMulticastLock()
        _hud.value = HudSnapshotModel()
        _media.value = MediaDiagnosticsModel()
        _state.value = ActiveSessionState(
            targetId = targetId,
            deviceName = deviceName,
            deviceOs = deviceOs,
            status = SessionStatus.Connecting(
                "endpoint",
                AppStrings.get(R.string.session_stage_init, "正在建立加密通道…"),
            ),
            watchOnly = watchOnly,
            audioMuted = !prefs.audioEnabledDefault,
            clipboardEnabled = prefs.clipboardEnabledDefault,
        )

        val req = JSONObject()
            .put("target", targetId)
            .put("watchOnly", watchOnly)
            .put("forceRelay", forceRelay)
            .put("encryptConnection", true)
            .put("audioEnabled", prefs.audioEnabledDefault)
            .put("clipboardEnabled", prefs.clipboardEnabledDefault)
        if (!password.isNullOrEmpty()) req.put("password", password)

        val result = runCatching {
            val resp = JSONObject(Core.openSession(req.toString()))
            Log.i("RC_DIAG", "openSession resp=$resp")
            if (resp.optBoolean("cancelled", false)) {
                throw IllegalStateException(
                    AppStrings.get(R.string.session_err_cancelled, "连接已取消"),
                )
            }
            resp
        }
        result.onSuccess {
            historyStore.recordRecent(targetId)
            historyStore.markConnectedNow(targetId)
            if (prefs.clipboardEnabledDefault) clipboardBridge.start()
            SessionForegroundService.startOrUpdate(
                context,
                title = AppStrings.get(
                    R.string.session_notif_connecting_title,
                    "正在连接 $deviceName",
                    deviceName,
                ),
                subtitle = if (watchOnly) {
                    AppStrings.get(R.string.session_mode_watch, "观看模式")
                } else {
                    AppStrings.get(R.string.session_mode_control, "远程桌面控制")
                },
            )
            startPolling()
        }.onFailure { err ->
            Log.w("RC_DIAG", "openSession failed: ${err.message}", err)
            releaseMulticastLock()
            val uiErr = RcUiErrorCodec.fromThrowable(
                err,
                fallbackCode = RcErrorCodes.SESSION_DISCONNECTED,
                fallbackMessage = AppStrings.get(R.string.session_err_connect_failed, "连接建立失败"),
            )
            _state.value = _state.value.copy(
                status = SessionStatus.Error(uiErr.message.ifBlank { uiErr.title }, uiErr),
            )
            SessionForegroundService.stop(context)
        }
        result.map { }
    }

    fun attachSurface(surface: Surface) {
        NativeRuntimeBootstrap.ensureReady(context)
        Core.attachSurface(surface)
    }

    fun detachSurface() {
        if (!NativeRuntimeBootstrap.isReady) return
        Core.detachSurface()
    }

    fun onAppBackgrounded(screenLocked: Boolean) {
        val cur = _state.value
        if (cur.status !is SessionStatus.Streaming && cur.status !is SessionStatus.Connecting) return
        val timeoutSec = prefsStore.state.value.lockScreenTimeoutSec
        if (screenLocked && timeoutSec > 0) {
            backgroundLockJob?.cancel()
            backgroundLockJob = scope.launch {
                delay(timeoutSec * 1000L)
                disconnect(lockRemote = false)
            }
        }
    }

    fun onAppForegrounded() {
        backgroundLockJob?.cancel()
        backgroundLockJob = null
    }

    fun setWatchOnly(enabled: Boolean) {
        Core.sendSessionCommand(JSONObject().put("type", "set_watch_only").put("enabled", enabled).toString())
        _state.value = _state.value.copy(watchOnly = enabled)
    }

    fun setAudioMuted(muted: Boolean) {
        Core.sendSessionCommand(JSONObject().put("type", "set_audio_muted").put("muted", muted).toString())
        _state.value = _state.value.copy(audioMuted = muted)
    }

    fun setClipboardEnabled(enabled: Boolean) {
        Core.sendSessionCommand(JSONObject().put("type", "set_clipboard_enabled").put("enabled", enabled).toString())
        clipboardBridge.setEnabled(enabled)
        _state.value = _state.value.copy(clipboardEnabled = enabled)
    }

    fun setBitrate(bps: Int) {
        runCatching {
            Core.sendSessionCommand(JSONObject().put("type", "set_bitrate").put("bps", bps).toString())
        }.onSuccess {
            Log.i("RC_DIAG", "setBitrate bps=$bps")
        }.onFailure { err ->
            Log.w("RC_DIAG", "setBitrate failed bps=$bps: ${err.message}")
        }
        val cur = _state.value
        _state.value = cur.copy(
            targetBitrateBps = bps,
            negotiated = cur.negotiated?.copy(bitrateBps = bps),
        )
    }

    fun setStreamParams(width: Int, height: Int, fps: Int) {
        Log.i("RC_DIAG", "setStreamParams ${width}x${height}@${fps}")
        Core.sendSessionCommand(
            JSONObject()
                .put("type", "set_stream_params")
                .put("width", width)
                .put("height", height)
                .put("fps", fps)
                .toString(),
        )
        val cur = _state.value
        val updatedDc = cur.displayConfig?.let { dc ->
            dc.copy(
                width = if (width > 0) width else dc.width,
                height = if (height > 0) height else dc.height,
                fps = if (fps > 0) fps else dc.fps,
            )
        }
        _state.value = cur.copy(displayConfig = updatedDc)
    }

    fun lockRemoteScreen() {
        Core.sendSessionCommand(JSONObject().put("type", "lock_remote_screen").toString())
    }

    fun requestIdr() {
        Core.sendSessionCommand(JSONObject().put("type", "request_idr").toString())
    }

    fun setMediaPaused(paused: Boolean) {
        Core.sendSessionCommand(JSONObject().put("type", "set_media_paused").put("paused", paused).toString())
        _state.value = _state.value.copy(mediaPaused = paused)
    }

    fun probeNow() {
        Core.sendSessionCommand(JSONObject().put("type", "probe_now").toString())
    }

    fun setStillRefinementEnabled(enabled: Boolean) {
        Core.sendSessionCommand(
            JSONObject().put("type", "set_still_refinement_enabled").put("enabled", enabled).toString(),
        )
    }

    fun disconnect(lockRemote: Boolean = false) {
        pollJob?.cancel()
        pollJob = null
        backgroundLockJob?.cancel()
        backgroundLockJob = null
        clipboardBridge.stop()
        scope.launch {
            Core.disconnectSession(lockRemote)
        }
        releaseMulticastLock()
        SessionForegroundService.stop(context)
        _portMapRules.value = emptyList()
        _hud.value = HudSnapshotModel()
        _media.value = MediaDiagnosticsModel()
        _state.value = _state.value.copy(
            status = SessionStatus.Idle,
            negotiated = null,
            targetBitrateBps = null,
            displayConfig = null,
            firstFrameSeen = false,
            toolPermissions = ToolPermissionsModel(),
            mediaPaused = false,
        )
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                runCatching {
                    val raw = Core.pollSessionUpdates(mediaDiagnosticsVisible)
                    if (raw != null) {
                        val payload = JSONObject(raw)
                        payload.optJSONArray("updates")?.let { updates ->
                            for (i in 0 until updates.length()) {
                                handleSessionUpdate(updates.getJSONObject(i))
                            }
                        }
                        payload.optJSONArray("clipboardTexts")?.let { clips ->
                            for (i in 0 until clips.length()) {
                                val txt = clips.optString(i)
                                if (txt.isNotEmpty() && _state.value.clipboardEnabled) {
                                    clipboardBridge.applyPeerText(txt)
                                }
                            }
                        }
                        payload.optJSONObject("media")?.let { m ->
                            val rendered = m.optLong("renderedFrames", 0L)
                            val cur = _state.value
                            val firstFrame = cur.firstFrameSeen || rendered > 0L
                            val codecName = m.optString("codecName")
                            val hardware = m.optBoolean("hardware")
                            val lowLatency = m.optBoolean("lowLatency")
                            if (!cur.firstFrameSeen && firstFrame) {
                                _state.value = cur.copy(firstFrameSeen = true)
                            }
                            _media.value = MediaDiagnosticsModel(
                                codecName = codecName,
                                hardware = hardware,
                                lowLatency = lowLatency,
                                decodedFrames = m.optLong("decodedFrames"),
                                renderedFrames = rendered,
                                droppedFrames = m.optLong("droppedFrames"),
                                surfaceAttached = m.optBoolean("surfaceAttached"),
                            )
                        }
                        payload.optJSONArray("fileEvents")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                parseFileToolEvent(arr.getJSONObject(i))?.let { _fileEvents.emit(it) }
                            }
                        }
                        payload.optJSONArray("terminalEvents")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                parseTerminalToolEvent(arr.getJSONObject(i))?.let { _terminalEvents.emit(it) }
                            }
                        }
                        payload.optJSONArray("portmapRules")?.let { arr ->
                            _portMapRules.value = List(arr.length()) { idx -> arr.getJSONObject(idx).toPortMapRule() }
                        }
                    }
                }.onFailure { err ->
                    Log.w("RC_DIAG", "pollSessionUpdates error: ${err.message}", err)
                }
                delay(20L)
            }
        }
    }

    private fun handleSessionUpdate(u: JSONObject) {
        val kind = u.optString("kind")
        val data = u.optJSONObject("data")
        when (kind) {
            "connecting" -> if (data != null) {
                val phase = data.optString("phase")
                Log.i("RC_DIAG", "sessionUpdate connecting phase=$phase watchOnly=${data.optBoolean("watch_only", _state.value.watchOnly)}")
                val label = when (phase) {
                    "endpoint_creating" -> AppStrings.get(R.string.session_stage_endpoint, "准备传输端点…")
                    "connecting" -> AppStrings.get(R.string.session_stage_connecting, "正在建立安全握手…")
                    "awaiting_welcome" -> AppStrings.get(R.string.session_stage_welcome, "正在协商画质与权限…")
                    "first_frame_pending" -> AppStrings.get(R.string.session_stage_first_frame, "等待首帧画面…")
                    else -> AppStrings.get(R.string.session_stage_default, "正在连接…")
                }
                _state.value = _state.value.copy(
                    status = SessionStatus.Connecting(phase, label),
                    watchOnly = data.optBoolean("watch_only", _state.value.watchOnly),
                )
            }
            "negotiated" -> if (data != null) {
                val neg = NegotiatedStreamInfo(
                    width = data.optInt("width"),
                    height = data.optInt("height"),
                    fps = data.optInt("fps"),
                    bitrateBps = data.optInt("bitrate_bps"),
                    backend = data.optString("backend"),
                    watchOnly = data.optBoolean("watch_only"),
                    transport = data.optString("transport"),
                    mtlsActive = data.optBoolean("mtls_active"),
                    peerCertFingerprint = data.optString("peer_cert_fingerprint").takeIf { it.isNotBlank() && it != "null" },
                )
                Log.i(
                    "RC_DIAG",
                    "sessionUpdate negotiated ${neg.width}x${neg.height}@${neg.fps} transport=${neg.transport} mtls=${neg.mtlsActive} watchOnly=${neg.watchOnly}",
                )
                _state.value = _state.value.copy(
                    status = SessionStatus.Streaming,
                    watchOnly = neg.watchOnly,
                    negotiated = neg,
                    targetBitrateBps = _state.value.targetBitrateBps ?: neg.bitrateBps,
                )
                SessionForegroundService.startOrUpdate(
                    context,
                    title = AppStrings.get(
                        R.string.session_notif_controlling_title,
                        "正在远控 ${_state.value.deviceName}",
                        _state.value.deviceName,
                    ),
                    subtitle = "${neg.width}×${neg.height} @ ${neg.fps}fps · ${neg.transport}",
                )
            }
            "hud" -> if (data != null) {
                val nextHud = HudSnapshotModel(
                    elapsed = data.optString("elapsed"),
                    transport = data.optString("transport"),
                    fps = data.optDouble("fps", 0.0),
                    bitrateKbps = data.optDouble("bitrate_kbps", 0.0),
                    rttMs = if (data.isNull("rtt_ms")) null else data.optDouble("rtt_ms"),
                    frameMs = if (data.isNull("frame_ms")) null else data.optDouble("frame_ms"),
                    lossPct = data.optDouble("loss_pct", 0.0),
                    resolution = data.optString("resolution"),
                )
                if (_hud.value != nextHud) {
                    _hud.value = nextHud
                }
            }
            "display_config" -> if (data != null) {
                val presetsArr = data.optJSONArray("presets")
                val presets = if (presetsArr != null) {
                    List(presetsArr.length()) { idx ->
                        val p = presetsArr.getJSONObject(idx)
                        DisplayPresetModel(
                            width = p.optInt("width"),
                            height = p.optInt("height"),
                            label = p.optString("label"),
                            isNative = p.optBoolean("is_native"),
                            isHidpi = p.optBoolean("is_hidpi"),
                        )
                    }
                } else {
                    emptyList()
                }
                _state.value = _state.value.copy(
                    displayConfig = DisplayConfigModel(
                        width = data.optInt("width"),
                        height = data.optInt("height"),
                        fps = data.optInt("fps"),
                        nativeWidth = data.optInt("native_width"),
                        nativeHeight = data.optInt("native_height"),
                        maxFps = data.optInt("max_fps"),
                        presets = presets,
                    ),
                )
            }
            "wallpaper" -> if (data != null) {
                val dataUrl = data.optString("data_url")
                if (dataUrl.isNotBlank() && _state.value.targetId.isNotBlank()) {
                    wallpaperCache.saveDataUrl(_state.value.targetId, dataUrl)
                }
            }
            "session_switching" -> if (data != null) {
                val rawReason = data.optString("reason")
                val reason = if (rawReason.isBlank()) {
                    AppStrings.get(R.string.session_reconnecting_desktop_switch, "远端正在切换桌面，自动重连中…")
                } else {
                    NativeErrorLocalizer.localize(rawReason)
                }
                Log.i("RC_DIAG", "sessionUpdate switching reason=$reason")
                _state.value = _state.value.copy(status = SessionStatus.Switching(reason))
                scope.launch {
                    delay(1200L)
                    if (_state.value.status is SessionStatus.Switching && lastRequestTarget.isNotBlank()) {
                        connect(
                            targetId = lastRequestTarget,
                            deviceName = lastRequestName,
                            deviceOs = lastRequestOs,
                            watchOnly = _state.value.watchOnly,
                            password = lastPassword,
                            forceRelay = lastForceRelay,
                        )
                    }
                }
            }
            "reconnecting" -> if (data != null) {
                val attempt = data.optInt("attempt", 1)
                val maxAttempts = data.optInt("max_attempts", 3)
                val rawReason = data.optString("reason")
                val reason = if (rawReason.isBlank()) {
                    AppStrings.get(R.string.session_reconnecting_generic, "网络波动，正在尝试第 $attempt/$maxAttempts 次重连…", attempt, maxAttempts)
                } else {
                    NativeErrorLocalizer.localize(rawReason)
                }
                _state.value = _state.value.copy(
                    status = SessionStatus.Reconnecting(attempt, maxAttempts, reason),
                )
            }
            "media_state" -> if (data != null) {
                _state.value = _state.value.copy(mediaPaused = data.optBoolean("paused", false))
            }
            "media_resumed" -> if (data != null) {
                val cur = _state.value
                val nextNeg = cur.negotiated?.copy(
                    backend = data.optString("backend").ifBlank { cur.negotiated.backend },
                    width = data.optInt("width").takeIf { it > 0 } ?: cur.negotiated.width,
                    height = data.optInt("height").takeIf { it > 0 } ?: cur.negotiated.height,
                    fps = data.optInt("fps").takeIf { it > 0 } ?: cur.negotiated.fps,
                )
                _state.value = cur.copy(
                    mediaPaused = false,
                    negotiated = nextNeg,
                )
            }
            "tool_permissions_updated" -> if (data != null) {
                _state.value = _state.value.copy(
                    toolPermissions = ToolPermissionsModel(
                        allowFileTransfer = data.optBoolean("allow_file_transfer", true),
                        allowTerminal = data.optBoolean("allow_terminal", true),
                        allowPortMap = data.optBoolean("allow_port_map", true),
                    ),
                )
            }
            "tool_permission_denied" -> if (data != null) {
                val uiErr = RcUiErrorCodec.fromJsonObject(data.optJSONObject("ui_error"))
                    ?: RcUiErrorCodec.resolvePlatformError(RcErrorCodes.TOOL_FORBIDDEN)
                RcErrorHub.present(uiErr)
            }
            "ended" -> if (data != null) {
                if (_state.value.status is SessionStatus.Switching) return
                val uiErr = RcUiErrorCodec.fromJsonObject(data.optJSONObject("ui_error"))
                val recoverable = data.optBoolean("recoverable", false)
                val rawReason = data.optString("reason")
                val reason = uiErr?.message?.takeIf { it.isNotBlank() }
                    ?: if (rawReason.isBlank()) {
                        AppStrings.get(R.string.session_ended_default, "会话已结束")
                    } else {
                        NativeErrorLocalizer.localize(rawReason)
                    }
                Log.i("RC_DIAG", "sessionUpdate ended reason=$reason recoverable=$recoverable code=${uiErr?.code}")
                clipboardBridge.stop()
                releaseMulticastLock()
                SessionForegroundService.stop(context)
                _state.value = _state.value.copy(status = SessionStatus.Ended(reason, recoverable, uiErr))
                pollJob?.cancel()
            }
        }
    }

    suspend fun callTool(request: JSONObject): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching { JSONObject(Core.toolCall(request.toString())) }
            .recoverCatching { err ->
                throw RcUiErrorException(RcUiErrorCodec.fromThrowable(err), err)
            }
    }

    fun updatePortMapRules(rules: List<PortMapRule>) {
        _portMapRules.value = rules
    }

    private fun acquireMulticastLock() {
        runCatching {
            if (multicastLock == null) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wm?.createMulticastLock("rabbitcontrol_lan")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
    }
}

internal fun parseFileToolEvent(ev: JSONObject): FileToolEvent? {
    val type = ev.optString("type")
    return when {
        type == "remoteDir" -> FileToolEvent.RemoteDir(
            path = ev.optString("path"),
            entries = ev.optJSONArray("entries")?.toFileEntries() ?: emptyList(),
            error = ev.optString("error").takeIf { it.isNotBlank() && it != "null" },
        )
        type == "mkdirResult" -> FileToolEvent.MkdirResult(
            path = ev.optString("path"),
            ok = ev.optBoolean("ok"),
            error = ev.optString("error").takeIf { it.isNotBlank() && it != "null" },
        )
        type == "transferUpdated" -> FileToolEvent.TransferUpdated(ev.toFileTransferItem())
        ev.has("RemoteDir") -> {
            val d = ev.getJSONObject("RemoteDir")
            FileToolEvent.RemoteDir(
                path = d.optString("path"),
                entries = d.optJSONArray("entries")?.toFileEntries() ?: emptyList(),
                error = d.optString("error").takeIf { it.isNotBlank() && it != "null" },
            )
        }
        ev.has("MkdirResult") -> {
            val d = ev.getJSONObject("MkdirResult")
            FileToolEvent.MkdirResult(
                path = d.optString("path"),
                ok = d.optBoolean("ok"),
                error = d.optString("error").takeIf { it.isNotBlank() && it != "null" },
            )
        }
        ev.has("TransferUpdated") -> FileToolEvent.TransferUpdated(
            ev.getJSONObject("TransferUpdated").toFileTransferItem(),
        )
        else -> null
    }
}

internal fun parseTerminalToolEvent(ev: JSONObject): TerminalToolEvent? {
    val type = ev.optString("type")
    return when {
        type == "sessionList" -> {
            val arr = ev.optJSONArray("sessions")
            val list = if (arr != null) {
                List(arr.length()) { idx -> arr.getJSONObject(idx).toTerminalSessionSummary() }
            } else {
                emptyList()
            }
            TerminalToolEvent.SessionList(list)
        }
        type == "opened" -> TerminalToolEvent.Opened(
            termId = if (ev.has("termId")) ev.optInt("termId") else ev.optInt("term_id"),
            name = ev.optString("name"),
            title = ev.optString("title"),
            cwd = ev.optString("cwd"),
            attached = ev.optBoolean("attached"),
            ok = ev.optBoolean("ok"),
            error = ev.optString("error").takeIf { it.isNotBlank() && it != "null" },
            createdAtUnixSec = if (ev.has("createdAtUnixSec")) ev.optLong("createdAtUnixSec") else ev.optLong("created_at_unix_sec"),
        )
        type == "attached" -> TerminalToolEvent.Attached(
            termId = if (ev.has("termId")) ev.optInt("termId") else ev.optInt("term_id"),
            ok = ev.optBoolean("ok"),
            error = ev.optString("error").takeIf { it.isNotBlank() && it != "null" },
            session = ev.optJSONObject("session")?.toTerminalSessionSummary(),
        )
        type == "output" -> TerminalToolEvent.Output(
            termId = if (ev.has("termId")) ev.optInt("termId") else ev.optInt("term_id"),
            bytes = parseByteArrayFromJson(ev),
        )
        type == "exited" -> TerminalToolEvent.Exited(
            termId = if (ev.has("termId")) ev.optInt("termId") else ev.optInt("term_id"),
            exitCode = if (ev.has("code")) ev.optInt("code") else ev.optInt("exitCode", ev.optInt("exit_code")),
        )
        type == "renamed" -> TerminalToolEvent.Renamed(
            termId = if (ev.has("termId")) ev.optInt("termId") else ev.optInt("term_id"),
            name = ev.optString("name"),
            ok = ev.optBoolean("ok"),
            error = ev.optString("error").takeIf { it.isNotBlank() && it != "null" },
        )
        type == "error" -> {
            val tid = if (ev.has("termId") && !ev.isNull("termId")) ev.optInt("termId") else null
            TerminalToolEvent.Error(tid, ev.optString("message"))
        }
        ev.has("SessionList") -> {
            val arr = ev.getJSONObject("SessionList").optJSONArray("sessions")
            val list = if (arr != null) {
                List(arr.length()) { idx -> arr.getJSONObject(idx).toTerminalSessionSummary() }
            } else {
                emptyList()
            }
            TerminalToolEvent.SessionList(list)
        }
        ev.has("Opened") -> {
            val d = ev.getJSONObject("Opened")
            TerminalToolEvent.Opened(
                termId = d.optInt("termId", d.optInt("term_id")),
                name = d.optString("name"),
                title = d.optString("title"),
                cwd = d.optString("cwd"),
                attached = d.optBoolean("attached"),
                ok = d.optBoolean("ok"),
                error = d.optString("error").takeIf { it.isNotBlank() && it != "null" },
                createdAtUnixSec = d.optLong("createdAtUnixSec", d.optLong("created_at_unix_sec")),
            )
        }
        ev.has("Attached") -> {
            val d = ev.getJSONObject("Attached")
            TerminalToolEvent.Attached(
                termId = d.optInt("termId", d.optInt("term_id")),
                ok = d.optBoolean("ok"),
                error = d.optString("error").takeIf { it.isNotBlank() && it != "null" },
                session = d.optJSONObject("session")?.toTerminalSessionSummary(),
            )
        }
        ev.has("Output") -> {
            val d = ev.getJSONObject("Output")
            TerminalToolEvent.Output(
                termId = d.optInt("termId", d.optInt("term_id")),
                bytes = parseByteArrayFromJson(d),
            )
        }
        ev.has("Exited") -> {
            val d = ev.getJSONObject("Exited")
            TerminalToolEvent.Exited(
                termId = d.optInt("termId", d.optInt("term_id")),
                exitCode = d.optInt("code", d.optInt("exitCode", d.optInt("exit_code"))),
            )
        }
        ev.has("Renamed") -> {
            val d = ev.getJSONObject("Renamed")
            TerminalToolEvent.Renamed(
                termId = d.optInt("termId", d.optInt("term_id")),
                name = d.optString("name"),
                ok = d.optBoolean("ok"),
                error = d.optString("error").takeIf { it.isNotBlank() && it != "null" },
            )
        }
        ev.has("Error") -> {
            val d = ev.getJSONObject("Error")
            val tid = if (d.has("termId") && !d.isNull("termId")) d.optInt("termId") else null
            TerminalToolEvent.Error(tid, d.optString("message"))
        }
        else -> null
    }
}

private fun parseByteArrayFromJson(obj: JSONObject): ByteArray {
    val arr = obj.optJSONArray("bytes")
    if (arr != null && arr.length() > 0) {
        return ByteArray(arr.length()) { idx -> arr.optInt(idx).toByte() }
    }
    val text = obj.optString("textUtf8Lossy")
    return if (text.isNotEmpty()) text.toByteArray(Charsets.UTF_8) else ByteArray(0)
}

private fun JSONObject.toTerminalSessionSummary(): TerminalSessionSummary = TerminalSessionSummary(
    termId = if (has("termId")) optInt("termId") else optInt("term_id"),
    name = optString("name"),
    title = optString("title"),
    cwd = optString("cwd"),
    attached = optBoolean("attached"),
    createdAtUnixSec = if (has("createdAtUnixSec")) optLong("createdAtUnixSec") else optLong("created_at_unix_sec"),
)
