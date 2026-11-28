package com.rabbitcontrol.android.core.nativebridge

import androidx.compose.runtime.Immutable
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.i18n.AppStrings
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class ServerConfigModel(
    val address: String,
    val fingerprint: String,
    val caPem: String,
    val name: String = "",
    val allowRegistration: Boolean = true,
    val authPubkeyHex: String = "",
    val apiPort: Int = DEFAULT_API_PORT,
    val signalPort: Int = DEFAULT_SIGNAL_PORT,
    val relayPort: Int = DEFAULT_RELAY_PORT,
) {
    val id: String
        get() = "${address.trim().lowercase()}|${fingerprint.trim().lowercase()}"
    val displayName: String
        get() = name.ifBlank { address }
    val hasCustomPorts: Boolean
        get() = apiPort != DEFAULT_API_PORT || signalPort != DEFAULT_SIGNAL_PORT || relayPort != DEFAULT_RELAY_PORT

    fun customPortsSummary(): String? {
        if (!hasCustomPorts) return null
        val parts = buildList {
            if (apiPort != DEFAULT_API_PORT) add("API $apiPort")
            if (signalPort != DEFAULT_SIGNAL_PORT) {
                add(AppStrings.get(R.string.port_summary_signal, "信令 $signalPort", signalPort))
            }
            if (relayPort != DEFAULT_RELAY_PORT) {
                add(AppStrings.get(R.string.port_summary_relay, "中继 $relayPort", relayPort))
            }
        }
        return parts.joinToString(" · ")
    }

    companion object {
        const val DEFAULT_API_PORT = 21118
        const val DEFAULT_SIGNAL_PORT = 21116
        const val DEFAULT_RELAY_PORT = 21117
    }
}

@Immutable
data class UserProfile(
    val id: String,
    val email: String,
    val name: String,
    val needsPassword: Boolean,
)

@Immutable
data class RemoteDevice(
    val id: String,
    val name: String,
    val os: String,
    val online: Boolean,
    val busy: Boolean,
    val disabled: Boolean,
    val allowRemoteAssist: Boolean = false,
    val allowRemoteControl: Boolean = true,
    val wolCapable: Boolean = false,
)

@Immutable
data class ToolPermissionsModel(
    val allowFileTransfer: Boolean = true,
    val allowTerminal: Boolean = true,
    val allowPortMap: Boolean = true,
)

@Immutable
data class SignalStatusSnapshot(
    val enabled: Boolean = false,
    val state: String = "disabled",
    val deviceId: String? = null,
    val deviceIdFormatted: String? = null,
    val lastError: String? = null,
)

@Immutable
data class DisplayPresetModel(
    val width: Int,
    val height: Int,
    val label: String,
    val isNative: Boolean,
    val isHidpi: Boolean,
)

@Immutable
data class HudSnapshotModel(
    val elapsed: String = "00:00:00",
    val transport: String = "UDP LAN",
    val fps: Double = 0.0,
    val bitrateKbps: Double = 0.0,
    val rttMs: Double? = null,
    val frameMs: Double? = null,
    val lossPct: Double = 0.0,
    val resolution: String = "",
)

@Immutable
data class MediaDiagnosticsModel(
    val streamCodec: String = "",
    val codecName: String = "",
    val hardware: Boolean = false,
    val lowLatency: Boolean = false,
    val decodedFrames: Long = 0L,
    val renderedFrames: Long = 0L,
    val droppedFrames: Long = 0L,
    val surfaceAttached: Boolean = false,
)

fun normalizeVideoCodecId(raw: String?): String? {
    val norm = raw?.trim()?.lowercase().orEmpty()
    if (norm.isEmpty()) return null
    return when {
        norm == "h264" || norm == "avc" || norm == "h.264" || norm.contains(".avc.") || norm.contains(".h264.") -> "h264"
        norm == "hevc" || norm == "h265" || norm == "h.265" || norm.contains("hevc") || norm.contains("h265") -> "hevc"
        norm == "av1" || norm == "av01" || norm.contains("av1") || norm.contains("av01") -> "av1"
        else -> null
    }
}

fun formatVideoCodecLabel(raw: String?, fallbackCodecName: String? = null): String? {
    val id = normalizeVideoCodecId(raw) ?: normalizeVideoCodecId(fallbackCodecName) ?: return null
    return when (id) {
        "h264" -> "H.264"
        "hevc" -> "HEVC"
        "av1" -> "AV1"
        else -> null
    }
}

@Immutable
data class NegotiatedStreamInfo(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int,
    val backend: String,
    val watchOnly: Boolean,
    val transport: String,
    val mtlsActive: Boolean,
    val peerCertFingerprint: String? = null,
)

@Immutable
data class HighRefreshAdvisoryModel(
    val level: String = "optimal",
    val reason: String = "optimal",
    val canEnableHevc: Boolean = false,
    val recommendedWidth: Int? = null,
    val recommendedHeight: Int? = null,
)

fun parseHighRefreshAdvisory(obj: JSONObject?): HighRefreshAdvisoryModel? {
    if (obj == null) return null
    val level = obj.optString("level").trim().lowercase().ifBlank { "optimal" }
    val reason = obj.optString("reason").trim().lowercase().ifBlank { "optimal" }
    val canEnableHevc = obj.optBoolean("can_enable_hevc", obj.optBoolean("canEnableHevc", false))
    val recWidth = when {
        obj.has("recommended_width") && !obj.isNull("recommended_width") -> obj.optInt("recommended_width").takeIf { it > 0 }
        obj.has("recommendedWidth") && !obj.isNull("recommendedWidth") -> obj.optInt("recommendedWidth").takeIf { it > 0 }
        else -> null
    }
    val recHeight = when {
        obj.has("recommended_height") && !obj.isNull("recommended_height") -> obj.optInt("recommended_height").takeIf { it > 0 }
        obj.has("recommendedHeight") && !obj.isNull("recommendedHeight") -> obj.optInt("recommendedHeight").takeIf { it > 0 }
        else -> null
    }
    return HighRefreshAdvisoryModel(
        level = level,
        reason = reason,
        canEnableHevc = canEnableHevc,
        recommendedWidth = recWidth,
        recommendedHeight = recHeight,
    )
}

@Immutable
data class RemoteDisplayModel(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val isMain: Boolean = false,
    val isVirtual: Boolean = false,
)

@Immutable
data class PrivacyStateModel(
    val mode: String = "off",
    val preset: String = "",
    val supported: Boolean = true,
)

@Immutable
data class HostMuteStateModel(
    val muted: Boolean = false,
    val supported: Boolean = true,
)

@Immutable
data class TerminalLockStateModel(
    val required: Boolean = false,
    val unlocked: Boolean = true,
    val username: String? = null,
    val retryAfterSecs: Int = 0,
)

@Immutable
data class TerminalUnlockResultModel(
    val ok: Boolean = false,
    val error: String? = null,
    val retryAfterSecs: Int = 0,
    val timestampMs: Long = System.currentTimeMillis(),
)

@Immutable
data class RemoteHostVersionModel(
    val version: String,
    val arch: String,
    val os: String,
    val updateCapable: Boolean = false,
    val isOwnerSession: Boolean = false,
    val serviceInstalled: Boolean = false,
)

@Immutable
data class RemoteUpdateProgressModel(
    val stage: String = "idle",
    val progressPct: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val targetVersion: String = "",
    val message: String = "",
    val needControllerRelay: Boolean = false,
    val error: String? = null,
)

@Immutable
data class RemoteUpdateCheckInfo(
    val checking: Boolean = false,
    val latestVersion: String? = null,
    val hasUpdate: Boolean = false,
    val downloadUrl: String? = null,
    val expectedSha256: String? = null,
    val assetName: String? = null,
    val error: String? = null,
)

@Immutable
data class DeviceCertStatusModel(
    val valid: Boolean = false,
    val serialHex: String? = null,
    val userId: String? = null,
    val deviceId: String? = null,
    val fingerprintSha256: String? = null,
    val issuedAtUnixSec: Long = 0L,
    val expiresAtUnixSec: Long = 0L,
    val needsRenewal: Boolean = true,
)

fun parseTerminalLockState(obj: JSONObject?): TerminalLockStateModel {
    if (obj == null) return TerminalLockStateModel()
    val rawUser = obj.optString("username").takeIf { it.isNotBlank() && it != "null" }
    val retry = obj.optInt("retry_after_secs", obj.optInt("retryAfterSecs", 0))
    return TerminalLockStateModel(
        required = obj.optBoolean("required", false),
        unlocked = obj.optBoolean("unlocked", true),
        username = rawUser,
        retryAfterSecs = retry.coerceAtLeast(0),
    )
}

fun parseTerminalUnlockResult(obj: JSONObject?): TerminalUnlockResultModel {
    if (obj == null) return TerminalUnlockResultModel(ok = false)
    val err = obj.optString("error").takeIf { it.isNotBlank() && it != "null" }
    val retry = obj.optInt("retry_after_secs", obj.optInt("retryAfterSecs", 0))
    return TerminalUnlockResultModel(
        ok = obj.optBoolean("ok", false),
        error = err,
        retryAfterSecs = retry.coerceAtLeast(0),
        timestampMs = System.currentTimeMillis(),
    )
}

fun parseRemoteHostVersion(obj: JSONObject?): RemoteHostVersionModel? {
    if (obj == null) return null
    val ver = obj.optString("version").trim()
    if (ver.isEmpty() || ver == "null") return null
    return RemoteHostVersionModel(
        version = ver,
        arch = obj.optString("arch").trim(),
        os = obj.optString("os").trim(),
        updateCapable = obj.optBoolean("updateCapable", obj.optBoolean("update_capable", false)),
        isOwnerSession = obj.optBoolean("isOwnerSession", obj.optBoolean("is_owner_session", false)),
        serviceInstalled = obj.optBoolean("serviceInstalled", obj.optBoolean("service_installed", false)),
    )
}

fun parseRemoteUpdateProgress(obj: JSONObject?): RemoteUpdateProgressModel? {
    if (obj == null) return null
    val stage = obj.optString("stage").trim().lowercase().ifBlank { "idle" }
    val err = obj.optString("error").takeIf { it.isNotBlank() && it != "null" }
    return RemoteUpdateProgressModel(
        stage = stage,
        progressPct = obj.optInt("progressPct", obj.optInt("progress_pct", 0)).coerceIn(0, 100),
        downloadedBytes = obj.optLong("downloadedBytes", obj.optLong("downloaded_bytes", 0L)),
        totalBytes = obj.optLong("totalBytes", obj.optLong("total_bytes", 0L)),
        targetVersion = obj.optString("targetVersion", obj.optString("target_version", "")),
        message = obj.optString("message", ""),
        needControllerRelay = obj.optBoolean("needControllerRelay", obj.optBoolean("need_controller_relay", false)),
        error = err,
    )
}

fun parseDeviceCertStatus(obj: JSONObject?): DeviceCertStatusModel {
    if (obj == null) return DeviceCertStatusModel()
    fun JSONObject.optStrOrNull(k1: String, k2: String = k1): String? =
        optString(k1).ifBlank { optString(k2) }.takeIf { it.isNotBlank() && it != "null" }
    return DeviceCertStatusModel(
        valid = obj.optBoolean("valid", false),
        serialHex = obj.optStrOrNull("serialHex", "serial_hex"),
        userId = obj.optStrOrNull("userId", "user_id"),
        deviceId = obj.optStrOrNull("deviceId", "device_id"),
        fingerprintSha256 = obj.optStrOrNull("fingerprintSha256", "fingerprint_sha256"),
        issuedAtUnixSec = obj.optLong("issuedAtUnixSec", obj.optLong("issued_at_unix_sec", 0L)),
        expiresAtUnixSec = obj.optLong("expiresAtUnixSec", obj.optLong("expires_at_unix_sec", 0L)),
        needsRenewal = obj.optBoolean("needsRenewal", obj.optBoolean("needs_renewal", true)),
    )
}

@Immutable
data class DisplayConfigModel(
    val width: Int,
    val height: Int,
    val fps: Int,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val maxFps: Int,
    val presets: List<DisplayPresetModel>,
    val codec: String? = null,
    val supportedCodecs: List<String> = emptyList(),
    val clientSupportsHevc: Boolean = false,
    val advisory: HighRefreshAdvisoryModel? = null,
)

@Immutable
data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modifiedUnixSec: Long,
)

@Immutable
data class FileTransferItem(
    val id: Long,
    val direction: String,
    val localPath: String,
    val remotePath: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val status: String,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0L) (transferredBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

@Immutable
data class TerminalSessionSummary(
    val termId: Int,
    val name: String,
    val title: String,
    val cwd: String,
    val attached: Boolean,
    val createdAtUnixSec: Long,
)

@Immutable
data class PortMapRule(
    val id: Long,
    val name: String,
    val localPort: Int,
    val targetAddr: String,
    val targetPort: Int,
    val enabled: Boolean,
    val status: String,
    val activeConns: Int,
    val bytesTx: Long,
    val bytesRx: Long,
    val error: String? = null,
)

internal fun JSONObject.toPortMapRule(): PortMapRule = PortMapRule(
    id = optLong("id"),
    name = optString("name"),
    localPort = optInt("local_port", optInt("localPort")),
    targetAddr = optString("target_addr", optString("targetAddr", "127.0.0.1")),
    targetPort = optInt("target_port", optInt("targetPort")),
    enabled = optBoolean("enabled", true),
    status = optString("status", "stopped"),
    activeConns = optInt("active_conns", optInt("activeConns")),
    bytesTx = optLong("bytes_tx", optLong("bytesTx")),
    bytesRx = optLong("bytes_rx", optLong("bytesRx")),
    error = optString("error").takeIf { it.isNotBlank() && it != "null" },
)

internal fun JSONObject.toFileTransferItem(): FileTransferItem {
    val direction = optString("direction")
    val fromPath = optString("fromPath", optString("from_path"))
    val toPath = optString("toPath", optString("to_path"))
    val explicitLocal = optString("local_path", optString("localPath"))
    val explicitRemote = optString("remote_path", optString("remotePath"))
    val localPath = when {
        explicitLocal.isNotEmpty() -> explicitLocal
        direction == "upload" -> fromPath
        direction == "download" -> toPath
        else -> ""
    }
    val remotePath = when {
        explicitRemote.isNotEmpty() -> explicitRemote
        direction == "upload" -> toPath
        direction == "download" -> fromPath
        else -> ""
    }
    val fileName = optString("name").ifEmpty {
        optString("file_name", optString("fileName"))
    }
    val totalBytes = if (has("totalBytes")) optLong("totalBytes") else optLong("total_bytes")
    val transferredBytes = when {
        has("doneBytes") -> optLong("doneBytes")
        has("done_bytes") -> optLong("done_bytes")
        has("transferredBytes") -> optLong("transferredBytes")
        else -> optLong("transferred_bytes")
    }
    return FileTransferItem(
        id = optLong("id"),
        direction = direction,
        localPath = localPath,
        remotePath = remotePath,
        fileName = fileName,
        totalBytes = totalBytes,
        transferredBytes = transferredBytes,
        status = optString("status"),
        error = optString("error").takeIf { it.isNotBlank() && it != "null" },
    )
}

internal fun JSONArray.toFileEntries(): List<RemoteFileEntry> = List(length()) { idx ->
    val obj = getJSONObject(idx)
    RemoteFileEntry(
        name = obj.optString("name"),
        path = obj.optString("path"),
        isDir = if (obj.has("isDir")) obj.optBoolean("isDir") else obj.optBoolean("is_dir"),
        size = obj.optLong("size"),
        modifiedUnixSec = when {
            obj.has("modifiedSec") -> obj.optLong("modifiedSec")
            obj.has("modifiedUnixSec") -> obj.optLong("modifiedUnixSec")
            else -> obj.optLong("modified_unix_sec")
        },
    )
}

enum class UpnpState {
    Disabled,
    Discovering,
    Mapped,
    Unavailable;

    companion object {
        fun fromWire(raw: String?): UpnpState = when (raw?.trim()?.lowercase()) {
            "discovering" -> Discovering
            "mapped" -> Mapped
            "unavailable" -> Unavailable
            else -> Disabled
        }
    }
}

@Immutable
data class UpnpStatusModel(
    val enabled: Boolean = false,
    val state: UpnpState = UpnpState.Disabled,
    val externalAddr: String = "",
    val protocol: String = "",
    val reason: String = "",
)

fun formatUpnpProtocol(raw: String?): String {
    val norm = raw?.trim()?.lowercase().orEmpty()
    return when (norm) {
        "upnp_igd_v2" -> "UPnP IGD v2"
        "upnp_igd_v1", "upnp_igd" -> "UPnP IGD"
        "nat_pmp", "nat-pmp" -> "NAT-PMP"
        "" -> "UPnP IGD"
        else -> raw?.trim().orEmpty()
    }
}

fun parseUpnpStatusPayload(rawJson: String?): UpnpStatusModel {
    val trimmed = rawJson?.trim().orEmpty()
    if (!trimmed.startsWith("{")) return UpnpStatusModel()
    return runCatching {
        val obj = JSONObject(trimmed)
        val enabled = obj.optBoolean("enabled", false)
        val stObj = obj.optJSONObject("status")
        val stateStr = stObj?.optString("state", "disabled")
        val parsedState = if (!enabled) UpnpState.Disabled else UpnpState.fromWire(stateStr)
        UpnpStatusModel(
            enabled = enabled,
            state = parsedState,
            externalAddr = stObj?.optString("external_addr")
                ?.ifBlank { stObj.optString("externalAddr") }
                ?.takeIf { it != "null" }
                .orEmpty(),
            protocol = stObj?.optString("protocol")?.takeIf { it != "null" }.orEmpty(),
            reason = stObj?.optString("reason")?.takeIf { it != "null" }.orEmpty(),
        )
    }.getOrDefault(UpnpStatusModel())
}
