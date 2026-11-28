package com.rabbitcontrol.android.core.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

@Immutable
data class SavedTerminalCredential(
    val password: String,
    val username: String? = null,
)

class TerminalPasswordStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _savedDeviceIds = MutableStateFlow(loadSavedIds())
    val savedDeviceIds: StateFlow<Set<String>> = _savedDeviceIds.asStateFlow()

    private fun loadSavedIds(): Set<String> =
        prefs.all.keys
            .filter { it.startsWith(KEY_PREFIX) }
            .map { it.removePrefix(KEY_PREFIX) }
            .toSet()

    fun hasSaved(deviceId: String): Boolean =
        deviceId.isNotBlank() && prefs.contains(entryKey(deviceId))

    fun save(deviceId: String, password: String, username: String? = null) {
        val id = deviceId.trim()
        if (id.isEmpty() || password.isEmpty()) return
        val payloadJson = JSONObject()
            .put("password", password)
            .apply {
                val u = username?.trim().orEmpty()
                if (u.isNotEmpty()) put("username", u)
            }
            .toString()
        val encrypted = runCatching {
            KeystoreCrypto.encrypt(KEY_ALIAS, payloadJson, id.toByteArray(Charsets.UTF_8))
        }.getOrNull() ?: return
        prefs.edit().putString(entryKey(id), encrypted).apply()
        _savedDeviceIds.value = loadSavedIds()
    }

    fun get(deviceId: String): SavedTerminalCredential? {
        val id = deviceId.trim()
        if (id.isEmpty()) return null
        val raw = prefs.getString(entryKey(id), null) ?: return null
        val jsonStr = KeystoreCrypto.decrypt(KEY_ALIAS, raw, id.toByteArray(Charsets.UTF_8))
            ?: return null
        return runCatching {
            val obj = JSONObject(jsonStr)
            val pw = obj.optString("password")
            if (pw.isEmpty()) return null
            val user = obj.optString("username").takeIf { it.isNotBlank() && it != "null" }
            SavedTerminalCredential(password = pw, username = user)
        }.getOrNull()
    }

    fun clear(deviceId: String) {
        val id = deviceId.trim()
        if (id.isEmpty()) return
        prefs.edit().remove(entryKey(id)).apply()
        _savedDeviceIds.value = loadSavedIds()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _savedDeviceIds.value = emptySet()
    }

    private fun entryKey(deviceId: String) = "$KEY_PREFIX${deviceId.trim()}"

    private companion object {
        const val PREF_NAME = "rc_terminal_passwords"
        const val KEY_ALIAS = "rabbitcontrol.terminal.pw.v1"
        const val KEY_PREFIX = "pw_"
    }
}
