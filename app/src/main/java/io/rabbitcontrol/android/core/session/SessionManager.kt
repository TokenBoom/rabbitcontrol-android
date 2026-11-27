package io.rabbitcontrol.android.core.session

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.Immutable
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.error.RcUiError
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.data.AppLockStore
import io.rabbitcontrol.android.core.data.ConnectionModePref
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.PortMapRuleStore
import io.rabbitcontrol.android.core.data.SavedPortMapRule
import io.rabbitcontrol.android.core.data.TargetHistoryStore
import io.rabbitcontrol.android.core.data.WallpaperCache
import io.rabbitcontrol.android.core.error.RcUiErrorCodec
import io.rabbitcontrol.android.core.error.RcUiErrorException
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.core.i18n.NativeErrorLocalizer
import io.rabbitcontrol.android.core.nativebridge.DisplayConfigModel
import io.rabbitcontrol.android.core.nativebridge.DisplayPresetModel
import io.rabbitcontrol.android.core.nativebridge.FileTransferItem
import io.rabbitcontrol.android.core.nativebridge.HostMuteStateModel
import io.rabbitcontrol.android.core.nativebridge.HudSnapshotModel
import io.rabbitcontrol.android.core.nativebridge.MediaDiagnosticsModel
import io.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import io.rabbitcontrol.android.core.nativebridge.NegotiatedStreamInfo
import io.rabbitcontrol.android.core.nativebridge.PortMapRule
import io.rabbitcontrol.android.core.nativebridge.PrivacyStateModel
import io.rabbitcontrol.android.core.nativebridge.RemoteDisplayModel
import io.rabbitcontrol.android.core.nativebridge.RemoteFileEntry
import io.rabbitcontrol.android.core.nativebridge.RemoteHostVersionModel
import io.rabbitcontrol.android.core.nativebridge.RemoteUpdateCheckInfo
import io.rabbitcontrol.android.core.nativebridge.RemoteUpdateProgressModel
import io.rabbitcontrol.android.core.nativebridge.TerminalLockStateModel
import io.rabbitcontrol.android.core.nativebridge.TerminalSessionSummary
import io.rabbitcontrol.android.core.nativebridge.TerminalUnlockResultModel
import io.rabbitcontrol.android.core.nativebridge.ToolPermissionsModel
import io.rabbitcontrol.android.core.nativebridge.parseHighRefreshAdvisory
import io.rabbitcontrol.android.core.nativebridge.parseRemoteHostVersion
import io.rabbitcontrol.android.core.nativebridge.parseRemoteUpdateProgress
import io.rabbitcontrol.android.core.nativebridge.parseTerminalLockState
import io.rabbitcontrol.android.core.nativebridge.parseTerminalUnlockResult
import io.rabbitcontrol.android.core.nativebridge.toFileEntries
import io.rabbitcontrol.android.core.nativebridge.toFileTransferItem
import io.rabbitcontrol.android.core.nativebridge.toPortMapRule
import io.rabbitcontrol.android.session.Core
import io.rabbitcontrol.android.ui.components.RabbitToast
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
    val toolsOnly: Boolean = false,
    val displays: List<RemoteDisplayModel> = emptyList(),
    val activeDisplayId: Int = 0,
    val privacy: PrivacyStateModel = PrivacyStateModel(),
    val hostMute: HostMuteStateModel = HostMuteStateModel(),
    val mouseMode: String = "smart",
    val terminalLockState: TerminalLockStateModel = TerminalLockStateModel(),
    val lastTerminalUnlockResult: TerminalUnlockResultModel? = null,
    val remoteHostVersion: RemoteHostVersionModel? = null,
    val remoteUpdateProgress: RemoteUpdateProgressModel? = null,
    val remoteUpdateCheckInfo: RemoteUpdateCheckInfo? = null,
)

sealed interface FileToolEvent {
    data class RemoteDir(val path: String, val entries: List<RemoteFileEntry>, val error: String?) : FileToolEvent
    data class MkdirResult(val path: String, val ok: Boolean, val error: String?) : FileToolEvent
    data class DeleteResult(val path: String, val ok: Boolean, val error: String?) : FileToolEvent
    data class RenameResult(val path: String, val newPath: String?, val ok: Boolean, val error: String?) : FileToolEvent
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
    private val appLockStore: AppLockStore,
    private val portMapRuleStore: PortMapRuleStore,
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
    private var lastToolsOnly: Boolean = false
    @Volatile
    private var portMapRestoredForSession: Boolean = false

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
        toolsOnly: Boolean = false,
        encryptConnection: Boolean? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (appLockStore.state.value.locked) {
            return@withContext Result.failure(
                IllegalStateException(
                    AppStrings.get(R.string.app_lock_blocked_connect, "应用已锁定，请先解锁后再发起连接"),
                ),
            )
        }
        val prefs = prefsStore.state.value
        val effectiveMode = if (forceRelay) ConnectionModePref.TcpRelay else prefs.connectionMode
        val effectiveForceRelay = forceRelay || effectiveMode == ConnectionModePref.TcpRelay
        val effEncrypt = encryptConnection ?: prefs.encryptByDefault
        lastRequestTarget = targetId
        lastRequestName = deviceName
        lastRequestOs = deviceOs
        lastPassword = password
        lastForceRelay = effectiveForceRelay
        lastToolsOnly = toolsOnly
        portMapRestoredForSession = false

        NativeRuntimeBootstrap.ensureReady(context)
        Core.setPreferHardwareDecode(prefs.preferHardwareDecode)
        acquireMulticastLock()
        _hud.value = HudSnapshotModel()
        _media.value = MediaDiagnosticsModel()
        val isDesktopHost = !deviceOs.equals("android", ignoreCase = true)
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
            mediaPaused = toolsOnly,
            toolsOnly = toolsOnly,
            privacy = PrivacyStateModel(supported = isDesktopHost),
            hostMute = HostMuteStateModel(supported = isDesktopHost),
            mouseMode = "smart",
            terminalLockState = TerminalLockStateModel(),
        )

        val req = JSONObject()
            .put("target", targetId)
            .put("watchOnly", watchOnly)
            .put("forceRelay", effectiveForceRelay)
            .put("connectionMode", effectiveMode.wireValue)
            .put("encryptConnection", effEncrypt)
            .put("toolsOnly", toolsOnly)
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

    fun setCodec(codec: String) {
        val normalized = codec.trim().lowercase()
        if (normalized.isEmpty()) return
        Log.i("RC_DIAG", "setCodec codec=$normalized")
        Core.sendSessionCommand(
            JSONObject().put("type", "set_codec").put("codec", normalized).toString(),
        )
    }

    fun setPreferHardwareDecode(preferHardware: Boolean) {
        prefsStore.update { it.copy(preferHardwareDecode = preferHardware) }
        Core.setPreferHardwareDecode(preferHardware)
        val status = _state.value.status
        if (status is SessionStatus.Streaming || status is SessionStatus.Connecting) {
            Log.i("RC_DIAG", "setDecodeMode preferHardware=$preferHardware")
            Core.sendSessionCommand(
                JSONObject()
                    .put("type", "set_decode_mode")
                    .put("preferHardware", preferHardware)
                    .toString(),
            )
        }
    }

    fun setConnectionMode(mode: ConnectionModePref) {
        prefsStore.update { it.copy(connectionMode = mode) }
        val cur = _state.value
        val active = cur.status is SessionStatus.Streaming ||
            cur.status is SessionStatus.Connecting ||
            cur.status is SessionStatus.Reconnecting ||
            cur.status is SessionStatus.Switching ||
            cur.status is SessionStatus.Error ||
            cur.status is SessionStatus.Ended
        if (active && lastRequestTarget.isNotBlank()) {
            Log.i("RC_DIAG", "setConnectionMode=${mode.wireValue}; reconnecting immediately")
            scope.launch {
                connect(
                    targetId = lastRequestTarget,
                    deviceName = lastRequestName,
                    deviceOs = lastRequestOs,
                    watchOnly = cur.watchOnly,
                    password = lastPassword,
                    forceRelay = mode == ConnectionModePref.TcpRelay,
                    toolsOnly = cur.toolsOnly,
                )
            }
        }
    }

    fun upgradeToDesktop() {
        Core.sendSessionCommand(JSONObject().put("type", "upgrade_to_desktop").toString())
        lastToolsOnly = false
        _state.value = _state.value.copy(toolsOnly = false, mediaPaused = false)
    }

    fun unlockTerminal(password: String, username: String? = null) {
        scope.launch {
            runCatching {
                val cmd = JSONObject()
                    .put("type", "terminal_unlock")
                    .put("password", password)
                val cleanUser = username?.trim().orEmpty()
                if (cleanUser.isNotEmpty()) cmd.put("username", cleanUser)
                Core.sendSessionCommand(cmd.toString())
            }.onFailure { err ->
                val uiErr = RcUiErrorCodec.fromThrowable(err, fallbackCode = RcErrorCodes.TOOL_TERMINAL_FAILED)
                _state.value = _state.value.copy(
                    lastTerminalUnlockResult = TerminalUnlockResultModel(
                        ok = false,
                        error = uiErr.message.ifBlank { err.message },
                    ),
                )
            }
        }
    }

    fun setMouseMode(mode: String) {
        val norm = when (mode.trim().lowercase()) {
            "remote" -> "remote"
            "local" -> "local"
            else -> "smart"
        }
        Core.sendSessionCommand(JSONObject().put("type", "set_mouse_mode").put("mode", norm).toString())
        _state.value = _state.value.copy(mouseMode = norm)
    }

    fun setPrivacyMode(
        mode: String,
        preset: String = if (mode == "privacy") "preset-1" else "",
        customImageBase64: String? = null,
    ) {
        val cmd = JSONObject()
            .put("type", "set_privacy_mode")
            .put("mode", mode)
            .put("preset", preset)
        if (!customImageBase64.isNullOrBlank()) {
            cmd.put("customImageBase64", customImageBase64)
        }
        Core.sendSessionCommand(cmd.toString())
        val cur = _state.value
        if (cur.privacy.supported) {
            _state.value = cur.copy(privacy = cur.privacy.copy(mode = mode, preset = preset))
        }
    }

    fun checkRemoteHostUpdate() {
        val hostVer = _state.value.remoteHostVersion ?: return
        if (!hostVer.isOwnerSession || !hostVer.updateCapable) return
        if (_state.value.remoteUpdateCheckInfo?.checking == true) return
        _state.value = _state.value.copy(
            remoteUpdateCheckInfo = RemoteUpdateCheckInfo(checking = true),
        )
        scope.launch {
            runCatching {
                val apiUrl = "https://api.github.com/repos/TokenBoom/rabbitcontrol-macos/releases/latest"
                val conn = (java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "RabbitControl-Android")
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    error("HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
                val assets = json.optJSONArray("assets") ?: org.json.JSONArray()
                val hostArch = when (hostVer.arch.trim().lowercase()) {
                    "aarch64", "arm64" -> "arm64"
                    "x86_64", "amd64", "x64" -> "x86_64"
                    else -> hostVer.arch.trim().lowercase()
                }
                var selectedName: String? = null
                var selectedUrl: String? = null
                var sumsUrl: String? = null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val name = a.optString("name")
                    val url = a.optString("browser_download_url")
                    val lower = name.lowercase()
                    if (lower == "sha256sums.txt" || lower.endsWith("sha256sums.txt")) {
                        sumsUrl = url
                    } else if (lower.endsWith(".dmg")) {
                        if (lower.contains(hostArch) || (selectedName == null && lower.contains("universal"))) {
                            selectedName = name
                            selectedUrl = url
                        }
                    }
                }
                var sha256: String? = null
                if (!sumsUrl.isNullOrBlank() && !selectedName.isNullOrBlank()) {
                    runCatching {
                        val sConn = (java.net.URL(sumsUrl).openConnection() as java.net.HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 8_000
                            readTimeout = 10_000
                            setRequestProperty("User-Agent", "RabbitControl-Android")
                        }
                        if (sConn.responseCode in 200..299) {
                            val txt = sConn.inputStream.bufferedReader().use { it.readText() }
                            sha256 = txt.lineSequence()
                                .map { it.trim() }
                                .firstOrNull { it.endsWith(selectedName!!) }
                                ?.split(Regex("\\s+"))
                                ?.firstOrNull()
                                ?.lowercase()
                                ?.takeIf { it.length == 64 }
                        }
                    }
                }
                val hasUpdate = compareSemVer(tag, hostVer.version) > 0 &&
                    !selectedUrl.isNullOrBlank() &&
                    !sha256.isNullOrBlank()
                RemoteUpdateCheckInfo(
                    checking = false,
                    latestVersion = tag.ifBlank { null },
                    hasUpdate = hasUpdate,
                    downloadUrl = selectedUrl,
                    expectedSha256 = sha256,
                    assetName = selectedName,
                    error = null,
                )
            }.onSuccess { info ->
                _state.value = _state.value.copy(remoteUpdateCheckInfo = info)
            }.onFailure { err ->
                _state.value = _state.value.copy(
                    remoteUpdateCheckInfo = RemoteUpdateCheckInfo(
                        checking = false,
                        error = err.message ?: "Check failed",
                    ),
                )
            }
        }
    }

    fun triggerRemoteHostUpdate(mirrorUrl: String = "") {
        val check = _state.value.remoteUpdateCheckInfo ?: return
        val ver = check.latestVersion ?: return
        val url = check.downloadUrl ?: return
        val sha = check.expectedSha256 ?: return
        val asset = check.assetName ?: return
        val req = JSONObject()
            .put("targetVersion", ver)
            .put("downloadUrl", url)
            .put("expectedSha256", sha)
            .put("assetName", asset)
            .put("mirrorUrl", mirrorUrl.trim())
            .put("relayFallbackAllowed", true)
        Core.sendSessionCommand(
            JSONObject()
                .put("type", "trigger_remote_update")
                .put("request", req)
                .toString(),
        )
    }

    fun cancelRemoteHostUpdate() {
        Core.sendSessionCommand(JSONObject().put("type", "cancel_remote_update").toString())
    }

    private fun compareSemVer(a: String, b: String): Int {
        fun parse(raw: String): List<Int> =
            raw.trim().removePrefix("v").removePrefix("V")
                .substringBefore('-').substringBefore('+')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }
        val pa = parse(a)
        val pb = parse(b)
        val len = maxOf(pa.size, pb.size, 3)
        for (i in 0 until len) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun setHostMute(muted: Boolean, restoreOnExit: Boolean = true) {
        Core.sendSessionCommand(
            JSONObject()
                .put("type", "set_host_mute")
                .put("muted", muted)
                .put("restoreOnExit", restoreOnExit)
                .toString(),
        )
        val cur = _state.value
        if (cur.hostMute.supported) {
            _state.value = cur.copy(hostMute = cur.hostMute.copy(muted = muted))
        }
    }

    fun listDisplays() {
        Core.sendSessionCommand(JSONObject().put("type", "list_displays").toString())
    }

    fun switchDisplay(displayId: Int) {
        Core.sendSessionCommand(
            JSONObject()
                .put("type", "switch_display")
                .put("displayId", displayId)
                .toString(),
        )
    }

    fun createVirtualDisplay(width: Int, height: Int, hidpi: Boolean) {
        Core.sendSessionCommand(
            JSONObject()
                .put("type", "create_virtual_display")
                .put("width", width)
                .put("height", height)
                .put("hidpi", hidpi)
                .toString(),
        )
    }

    fun removeVirtualDisplay(displayId: Int) {
        Core.sendSessionCommand(
            JSONObject()
                .put("type", "remove_virtual_display")
                .put("displayId", displayId)
                .toString(),
        )
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
            toolsOnly = false,
            displays = emptyList(),
            activeDisplayId = 0,
            privacy = PrivacyStateModel(supported = false),
            hostMute = HostMuteStateModel(supported = false),
            mouseMode = "smart",
            terminalLockState = TerminalLockStateModel(),
            lastTerminalUnlockResult = null,
            remoteHostVersion = null,
            remoteUpdateProgress = null,
            remoteUpdateCheckInfo = null,
        )
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                runCatching {
                    val includeMedia = mediaDiagnosticsVisible || prefsStore.state.value.showHud
                    val raw = Core.pollSessionUpdates(includeMedia)
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
                            val streamCodec = m.optString("streamCodec")
                            val codecName = m.optString("codecName")
                            val hardware = m.optBoolean("hardware")
                            val lowLatency = m.optBoolean("lowLatency")
                            if (!cur.firstFrameSeen && firstFrame) {
                                _state.value = cur.copy(firstFrameSeen = true)
                            }
                            _media.value = MediaDiagnosticsModel(
                                streamCodec = streamCodec,
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
                if (!_state.value.deviceOs.equals("android", ignoreCase = true)) {
                    listDisplays()
                }
                if (!portMapRestoredForSession && _state.value.targetId.isNotBlank()) {
                    portMapRestoredForSession = true
                    restoreSavedPortMapRules(_state.value.targetId)
                }
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
                val supportedCodecsArr = data.optJSONArray("supported_codecs")
                val supportedCodecs = if (supportedCodecsArr != null) {
                    buildList {
                        for (idx in 0 until supportedCodecsArr.length()) {
                            val item = supportedCodecsArr.optString(idx).trim().lowercase()
                            if (item.isNotEmpty() && item != "null") add(item)
                        }
                    }
                } else {
                    emptyList()
                }
                val codec = data.optString("codec").trim().lowercase().takeIf { it.isNotEmpty() && it != "null" }
                val advisory = parseHighRefreshAdvisory(data.optJSONObject("advisory"))
                _state.value = _state.value.copy(
                    displayConfig = DisplayConfigModel(
                        width = data.optInt("width"),
                        height = data.optInt("height"),
                        fps = data.optInt("fps"),
                        nativeWidth = data.optInt("native_width"),
                        nativeHeight = data.optInt("native_height"),
                        maxFps = data.optInt("max_fps"),
                        presets = presets,
                        codec = codec,
                        supportedCodecs = supportedCodecs,
                        clientSupportsHevc = data.optBoolean("client_supports_hevc", false),
                        advisory = advisory,
                    ),
                )
            }
            "privacy_state" -> if (data != null) {
                val supported = data.optBoolean("supported", false)
                _state.value = _state.value.copy(
                    privacy = PrivacyStateModel(
                        mode = data.optString("mode", "off").ifBlank { "off" },
                        preset = data.optString("preset"),
                        supported = supported,
                    ),
                )
                if (!supported) {
                    RabbitToast.info(
                        AppStrings.get(R.string.cp_privacy_unsupported, "当前被控端不支持防窥隐私幕布"),
                    )
                }
            }
            "host_mute_state" -> if (data != null) {
                val supported = data.optBoolean("supported", false)
                _state.value = _state.value.copy(
                    hostMute = HostMuteStateModel(
                        muted = data.optBoolean("muted", false),
                        supported = supported,
                    ),
                )
                if (!supported) {
                    RabbitToast.info(
                        AppStrings.get(R.string.cp_host_mute_unsupported, "当前被控端不支持远端系统静音"),
                    )
                }
            }
            "displays_state" -> if (data != null) {
                val arr = data.optJSONArray("displays")
                val list = if (arr != null) {
                    List(arr.length()) { idx ->
                        val d = arr.getJSONObject(idx)
                        RemoteDisplayModel(
                            id = d.optInt("id"),
                            name = d.optString("name"),
                            width = d.optInt("width"),
                            height = d.optInt("height"),
                            isMain = d.optBoolean("is_main", false),
                            isVirtual = d.optBoolean("is_virtual", false),
                        )
                    }
                } else {
                    emptyList()
                }
                _state.value = _state.value.copy(
                    displays = list,
                    activeDisplayId = data.optInt("active_display_id", 0),
                )
            }
            "virtual_display_error" -> if (data != null) {
                val rawMsg = data.optString("message")
                if (rawMsg.isNotBlank()) {
                    RabbitToast.error(NativeErrorLocalizer.localize(rawMsg))
                }
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
                    toolsOnly = false,
                    negotiated = nextNeg,
                )
            }
            "mouse_mode_state" -> if (data != null) {
                val m = data.optString("mode", "smart").trim().lowercase().ifBlank { "smart" }
                _state.value = _state.value.copy(mouseMode = m)
            }
            "terminal_lock_state" -> if (data != null) {
                val lockState = parseTerminalLockState(data)
                _state.value = _state.value.copy(
                    terminalLockState = lockState,
                    lastTerminalUnlockResult = if (!lockState.required || lockState.unlocked) null else _state.value.lastTerminalUnlockResult,
                )
            }
            "terminal_unlock_result" -> if (data != null) {
                val res = parseTerminalUnlockResult(data)
                val cur = _state.value
                _state.value = cur.copy(
                    lastTerminalUnlockResult = res,
                    terminalLockState = if (res.ok) {
                        cur.terminalLockState.copy(unlocked = true, retryAfterSecs = 0)
                    } else if (res.retryAfterSecs > 0) {
                        cur.terminalLockState.copy(retryAfterSecs = res.retryAfterSecs)
                    } else {
                        cur.terminalLockState
                    },
                )
            }
            "host_version" -> if (data != null) {
                val hv = parseRemoteHostVersion(data)
                if (hv != null) {
                    _state.value = _state.value.copy(remoteHostVersion = hv)
                }
            }
            "remote_update_status" -> if (data != null) {
                val prog = parseRemoteUpdateProgress(data)
                if (prog != null) {
                    _state.value = _state.value.copy(remoteUpdateProgress = prog)
                }
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

    private fun restoreSavedPortMapRules(targetId: String) {
        val saved = portMapRuleStore.loadRules(targetId)
        if (saved.isEmpty()) return
        scope.launch {
            val existing = callTool(JSONObject().put("action", "portmap_list")).getOrNull()
                ?.optJSONArray("rules")
                ?.let { arr -> List(arr.length()) { arr.getJSONObject(it).toPortMapRule() } }
                ?: emptyList()
            for (rule in saved) {
                val alreadyExists = existing.any {
                    it.localPort == rule.localPort &&
                        it.targetAddr == rule.targetAddr &&
                        it.targetPort == rule.targetPort
                }
                if (alreadyExists) continue
                val addResp = callTool(
                    JSONObject()
                        .put("action", "portmap_add")
                        .put("name", rule.name)
                        .put("localPort", rule.localPort)
                        .put("targetAddr", rule.targetAddr)
                        .put("targetPort", rule.targetPort),
                ).getOrNull()
                if (addResp != null) {
                    val createdId = addResp.optJSONObject("rule")?.optLong("id", 0L) ?: 0L
                    if (!rule.enabled && createdId > 0L) {
                        val toggled = callTool(
                            JSONObject()
                                .put("action", "portmap_toggle")
                                .put("id", createdId)
                                .put("enabled", false),
                        ).getOrNull()
                        toggled?.optJSONArray("rules")?.let { arr ->
                            _portMapRules.value = List(arr.length()) { arr.getJSONObject(it).toPortMapRule() }
                        }
                    } else {
                        addResp.optJSONArray("rules")?.let { arr ->
                            _portMapRules.value = List(arr.length()) { arr.getJSONObject(it).toPortMapRule() }
                        }
                    }
                }
            }
        }
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
    fun JSONObject.optNonEmpty(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }
    return when {
        type == "remoteDir" -> FileToolEvent.RemoteDir(
            path = ev.optString("path"),
            entries = ev.optJSONArray("entries")?.toFileEntries() ?: emptyList(),
            error = ev.optNonEmpty("error"),
        )
        type == "mkdirResult" -> FileToolEvent.MkdirResult(
            path = ev.optString("path"),
            ok = ev.optBoolean("ok"),
            error = ev.optNonEmpty("error"),
        )
        type == "deleteResult" -> FileToolEvent.DeleteResult(
            path = ev.optString("path"),
            ok = ev.optBoolean("ok"),
            error = ev.optNonEmpty("error"),
        )
        type == "renameResult" -> FileToolEvent.RenameResult(
            path = ev.optString("path"),
            newPath = ev.optNonEmpty("newPath") ?: ev.optNonEmpty("new_path"),
            ok = ev.optBoolean("ok"),
            error = ev.optNonEmpty("error"),
        )
        type == "transferUpdated" -> FileToolEvent.TransferUpdated(ev.toFileTransferItem())
        ev.has("RemoteDir") -> {
            val d = ev.getJSONObject("RemoteDir")
            FileToolEvent.RemoteDir(
                path = d.optString("path"),
                entries = d.optJSONArray("entries")?.toFileEntries() ?: emptyList(),
                error = d.optNonEmpty("error"),
            )
        }
        ev.has("MkdirResult") -> {
            val d = ev.getJSONObject("MkdirResult")
            FileToolEvent.MkdirResult(
                path = d.optString("path"),
                ok = d.optBoolean("ok"),
                error = d.optNonEmpty("error"),
            )
        }
        ev.has("DeleteResult") -> {
            val d = ev.getJSONObject("DeleteResult")
            FileToolEvent.DeleteResult(
                path = d.optString("path"),
                ok = d.optBoolean("ok"),
                error = d.optNonEmpty("error"),
            )
        }
        ev.has("RenameResult") -> {
            val d = ev.getJSONObject("RenameResult")
            FileToolEvent.RenameResult(
                path = d.optString("path"),
                newPath = d.optNonEmpty("newPath") ?: d.optNonEmpty("new_path"),
                ok = d.optBoolean("ok"),
                error = d.optNonEmpty("error"),
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
