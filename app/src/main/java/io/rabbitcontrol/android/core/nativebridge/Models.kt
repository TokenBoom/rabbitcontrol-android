package io.rabbitcontrol.android.core.nativebridge

import androidx.compose.runtime.Immutable
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
            if (signalPort != DEFAULT_SIGNAL_PORT) add("信令 $signalPort")
            if (relayPort != DEFAULT_RELAY_PORT) add("中继 $relayPort")
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
    val codecName: String = "",
    val hardware: Boolean = false,
    val lowLatency: Boolean = false,
    val decodedFrames: Long = 0L,
    val renderedFrames: Long = 0L,
    val droppedFrames: Long = 0L,
    val surfaceAttached: Boolean = false,
)

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
data class DisplayConfigModel(
    val width: Int,
    val height: Int,
    val fps: Int,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val maxFps: Int,
    val presets: List<DisplayPresetModel>,
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
