package io.rabbitcontrol.android.core.data

import android.content.Context
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class TargetHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_targets", Context.MODE_PRIVATE)

    private val _recent = MutableStateFlow(readList("recent"))
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val _favorites = MutableStateFlow(readList("favorites"))
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _lastConnected = MutableStateFlow(readLastConnected())
    val lastConnected: StateFlow<Map<String, Long>> = _lastConnected.asStateFlow()

    private fun readList(key: String): List<String> {
        val raw = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrNull() ?: return emptyList()
        return List(raw.length()) { raw.optString(it) }.filter { it.isNotBlank() }
    }

    private fun saveList(key: String, list: List<String>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
    }

    private fun readLastConnected(): Map<String, Long> {
        val raw = runCatching { JSONObject(prefs.getString("last_connected_map", "{}")) }.getOrNull()
            ?: return emptyMap()
        val map = mutableMapOf<String, Long>()
        raw.keys().forEach { k ->
            val ts = raw.optLong(k, 0L)
            if (ts > 0L) map[k] = ts
        }
        return map
    }

    fun recordRecent(target: String) {
        val norm = target.trim()
        if (norm.isEmpty()) return
        val next = (listOf(norm) + _recent.value.filter { it != norm }).take(15)
        _recent.value = next
        saveList("recent", next)
    }

    fun toggleFavorite(target: String) {
        val norm = target.trim()
        if (norm.isEmpty()) return
        val current = _favorites.value
        val next = if (norm in current) current.filter { it != norm } else (listOf(norm) + current).take(20)
        _favorites.value = next
        saveList("favorites", next)
    }

    fun isFavorite(target: String): Boolean = target.trim() in _favorites.value

    fun markConnectedNow(deviceId: String) {
        if (deviceId.isBlank()) return
        val next = _lastConnected.value.toMutableMap()
        next[deviceId] = System.currentTimeMillis()
        _lastConnected.value = next
        val obj = JSONObject()
        next.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString("last_connected_map", obj.toString()).apply()
    }

    fun formatLastConnected(deviceId: String): String {
        val ts = _lastConnected.value[deviceId]
            ?: return AppStrings.get(R.string.history_never_connected, "尚未连接过该设备")
        val deltaSec = ((System.currentTimeMillis() - ts) / 1000L).coerceAtLeast(0L)
        return when {
            deltaSec < 60L -> AppStrings.get(R.string.history_last_just_now, "上次连接：刚刚")
            deltaSec < 3600L -> {
                val m = deltaSec / 60L
                AppStrings.get(R.string.history_last_minutes, "上次连接：$m 分钟前", m)
            }
            deltaSec < 86400L -> {
                val h = deltaSec / 3600L
                AppStrings.get(R.string.history_last_hours, "上次连接：$h 小时前", h)
            }
            else -> {
                val d = deltaSec / 86400L
                AppStrings.get(R.string.history_last_days, "上次连接：$d 天前", d)
            }
        }
    }
}
