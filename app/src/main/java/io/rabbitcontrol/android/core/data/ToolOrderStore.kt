package io.rabbitcontrol.android.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

enum class DeviceToolKind(val key: String, val label: String) {
    FileTransfer("file_transfer", "文件传输"),
    WatchMode("watch", "观看模式"),
    Terminal("terminal", "终端"),
    PortMap("portmap", "端口映射"),
}

class ToolOrderStore(context: Context) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_tool_order", Context.MODE_PRIVATE)
    private val _order = MutableStateFlow(loadOrder())
    val order: StateFlow<List<DeviceToolKind>> = _order.asStateFlow()

    private fun loadOrder(): List<DeviceToolKind> {
        val raw = runCatching { JSONArray(prefs.getString(KEY_ORDER, null)) }.getOrNull()
        val parsed = if (raw != null) {
            List(raw.length()) { idx ->
                val k = raw.optString(idx)
                DeviceToolKind.entries.firstOrNull { it.key == k }
            }.filterNotNull().distinct()
        } else {
            emptyList()
        }
        val missing = DeviceToolKind.entries.filter { it !in parsed }
        return (parsed + missing).ifEmpty { DEFAULT_ORDER }
    }

    fun saveOrder(next: List<DeviceToolKind>) {
        val sanitized = (next.distinct() + DeviceToolKind.entries.filter { it !in next })
        _order.value = sanitized
        prefs.edit().putString(KEY_ORDER, JSONArray(sanitized.map { it.key }).toString()).apply()
    }

    companion object {
        private const val KEY_ORDER = "order_v1"
        val DEFAULT_ORDER = listOf(
            DeviceToolKind.FileTransfer,
            DeviceToolKind.WatchMode,
            DeviceToolKind.Terminal,
            DeviceToolKind.PortMap,
        )
    }
}
