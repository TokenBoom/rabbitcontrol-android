package io.rabbitcontrol.android.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.rabbitcontrol.android.core.nativebridge.RemoteDevice
import io.rabbitcontrol.android.core.nativebridge.ServerConfigModel
import io.rabbitcontrol.android.core.nativebridge.SignalStatusSnapshot
import io.rabbitcontrol.android.core.nativebridge.UserProfile
import io.rabbitcontrol.android.session.Core
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AccountRepository(context: Context) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_account", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var tokens: JSONObject? = null
    private val _servers = MutableStateFlow<List<ServerConfigModel>>(emptyList())
    val servers: StateFlow<List<ServerConfigModel>> = _servers.asStateFlow()

    private val _server = MutableStateFlow<ServerConfigModel?>(null)
    val server: StateFlow<ServerConfigModel?> = _server.asStateFlow()

    private val _savedAccountEmails = MutableStateFlow<Map<String, String>>(emptyMap())
    val savedAccountEmails: StateFlow<Map<String, String>> = _savedAccountEmails.asStateFlow()

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _devices = MutableStateFlow<List<RemoteDevice>>(emptyList())
    val devices: StateFlow<List<RemoteDevice>> = _devices.asStateFlow()

    private val _signalStatus = MutableStateFlow(SignalStatusSnapshot())
    val signalStatus: StateFlow<SignalStatusSnapshot> = _signalStatus.asStateFlow()

    private val _kickedNotice = MutableStateFlow<String?>(null)
    val kickedNotice: StateFlow<String?> = _kickedNotice.asStateFlow()

    init {
        migrateLegacyIfNeeded()
        val loaded = loadServers()
        _servers.value = loaded
        _server.value = loadActiveServer(loaded)
        _savedAccountEmails.value = loadSavedEmails(loaded)
        startSignalPoller()
    }

    private fun startSignalPoller() {
        scope.launch {
            while (isActive) {
                runCatching {
                    val raw = JSONObject(Core.pollSignalEvents())
                    val snapObj = raw.optJSONObject("snapshot")
                    if (snapObj != null) {
                        val snap = SignalStatusSnapshot(
                            enabled = snapObj.optBoolean("enabled", false),
                            state = snapObj.optString("state", "disabled"),
                            deviceId = snapObj.optString("deviceId", snapObj.optString("device_id"))
                                .takeIf { it.isNotBlank() && it != "null" },
                            deviceIdFormatted = snapObj.optString("deviceIdFormatted", snapObj.optString("device_id_formatted"))
                                .takeIf { it.isNotBlank() && it != "null" },
                            lastError = snapObj.optString("errorMessage", snapObj.optString("last_error"))
                                .takeIf { it.isNotBlank() && it != "null" },
                        )
                        val prev = _signalStatus.value
                        _signalStatus.value = snap
                        val active = _server.value
                        if (!snap.deviceId.isNullOrBlank() && active != null) {
                            prefs.edit().putString(deviceIdKey(active), snap.deviceId).apply()
                        }
                        if (snap != prev) {
                            android.util.Log.i("RC_DIAG", "signal snap: state=${snap.state} did=${snap.deviceId} err=${snap.lastError}")
                        }
                    }
                    val rotatedTokens = raw.optJSONObject("sessionTokens")
                    val active = _server.value
                    if (rotatedTokens != null && active != null) {
                        val curRefresh = tokens?.optString("refresh_token")
                        val newRefresh = rotatedTokens.optString("refresh_token")
                        if (newRefresh.isNotBlank() && newRefresh != curRefresh) {
                            runCatching { saveTokens(rotatedTokens, active) }
                        }
                    }
                    val events = raw.optJSONArray("events")
                    if (events != null && events.length() > 0) {
                        for (i in 0 until events.length()) {
                            val ev = events.getJSONObject(i)
                            when (ev.optString("kind")) {
                                "device_status" -> {
                                    val did = ev.optString("device_id")
                                    val online = ev.optBoolean("online")
                                    val allowAssist = ev.optBoolean("allow_remote_assist")
                                    val alias = ev.optString("alias")
                                    val hostname = ev.optString("hostname")
                                    val os = ev.optString("os")
                                    _devices.value = _devices.value.map { d ->
                                        if (d.id == did) {
                                            d.copy(
                                                online = online,
                                                allowRemoteAssist = allowAssist,
                                                name = alias.ifBlank { hostname.ifBlank { d.name } },
                                                os = os.ifBlank { d.os },
                                            )
                                        } else {
                                            d
                                        }
                                    }
                                }
                                "kicked" -> {
                                    val reason = ev.optString("reason")
                                    if (reason.isNotBlank()) {
                                        _kickedNotice.value = reason
                                    }
                                }
                            }
                        }
                    }
                }
                delay(500L)
            }
        }
    }

    fun dismissKickedNotice() {
        _kickedNotice.value = null
    }

    private fun storageSuffix(serverId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(serverId.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun tokenKey(s: ServerConfigModel) = "tokens_${storageSuffix(s.id)}"
    private fun deviceIdKey(s: ServerConfigModel) = "self_device_id_${storageSuffix(s.id)}"
    private fun emailKey(s: ServerConfigModel) = "email_${storageSuffix(s.id)}"

    private fun ServerConfigModel.toJson(): JSONObject = JSONObject()
        .put("address", address)
        .put("pin", fingerprint)
        .put("ca", caPem)
        .put("name", name)
        .put("allowRegistration", allowRegistration)

    private fun parseServerJson(obj: JSONObject): ServerConfigModel? = runCatching {
        ServerConfigModel(
            address = obj.getString("address"),
            fingerprint = obj.getString("pin"),
            caPem = obj.getString("ca"),
            name = obj.optString("name", ""),
            allowRegistration = obj.optBoolean("allowRegistration", true),
        )
    }.getOrNull()

    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_SERVERS_V2)) return
        val legacyRaw = prefs.getString("server", null) ?: return
        val legacy = runCatching { parseServerJson(JSONObject(legacyRaw)) }.getOrNull() ?: return
        val editor = prefs.edit()
        editor.putString(KEY_SERVERS_V2, JSONArray().put(legacy.toJson()).toString())
        editor.putString(KEY_ACTIVE_SERVER_ID, legacy.id)
        prefs.getString("tokens", null)?.let { editor.putString(tokenKey(legacy), it) }
        prefs.getString("self_device_id", null)?.let { editor.putString(deviceIdKey(legacy), it) }
        editor.commit()
    }

    private fun loadServers(): List<ServerConfigModel> {
        val raw = prefs.getString(KEY_SERVERS_V2, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    parseServerJson(arr.getJSONObject(i))?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistServers(list: List<ServerConfigModel>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SERVERS_V2, arr.toString()).commit()
        _servers.value = list
        _savedAccountEmails.value = loadSavedEmails(list)
    }

    private fun loadActiveServer(list: List<ServerConfigModel>): ServerConfigModel? {
        if (list.isEmpty()) return null
        val activeId = prefs.getString(KEY_ACTIVE_SERVER_ID, null)
        return list.firstOrNull { it.id == activeId } ?: list.first()
    }

    private fun loadSavedEmails(list: List<ServerConfigModel>): Map<String, String> =
        buildMap {
            for (srv in list) {
                val email = prefs.getString(emailKey(srv), null)
                if (!email.isNullOrBlank()) put(srv.id, email)
            }
        }

    private fun parseProfile(tok: JSONObject?): UserProfile? = tok?.optJSONObject("user")?.let {
        UserProfile(
            id = it.getString("id"),
            email = it.getString("email"),
            name = it.optString("display_name"),
            needsPassword = it.optBoolean("must_change_password"),
        )
    }

    suspend fun restore(): Unit = withContext(Dispatchers.IO) {
        val list = loadServers()
        _servers.value = list
        val selected = loadActiveServer(list)
        _server.value = selected
        _savedAccountEmails.value = loadSavedEmails(list)
        if (selected == null) {
            tokens = null
            _profile.value = null
            return@withContext
        }
        restoreForServer(selected)
    }

    private fun restoreForServer(selected: ServerConfigModel): Boolean {
        val encrypted = prefs.getString(tokenKey(selected), null)
        if (encrypted == null) {
            tokens = null
            _profile.value = null
            return false
        }
        return try {
            val parts = encrypted.split('.', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            cipher.updateAAD(serverIdentity(selected))
            val json = String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            val parsed = JSONObject(json)
            tokens = parsed
            val prof = parseProfile(parsed)
            _profile.value = prof
            if (prof != null && prefs.getString(emailKey(selected), null).isNullOrBlank()) {
                prefs.edit().putString(emailKey(selected), prof.email).apply()
                _savedAccountEmails.value = loadSavedEmails(_servers.value)
            }
            android.util.Log.i(
                "RC_DIAG",
                "restore decrypted ok: server=${selected.address} profile=${prof?.email} needsPwd=${prof?.needsPassword}",
            )

            val req = JSONObject()
                .put("action", "restore")
                .put("address", selected.address)
                .put("pin", selected.fingerprint)
                .put("ca", selected.caPem)
                .put("tokens", parsed)
            prefs.getString(deviceIdKey(selected), null)?.let { req.put("deviceId", it) }
            runCatching { Core.account(req.toString()) }
                .onSuccess { android.util.Log.i("RC_DIAG", "Core.account(restore) ok: $it") }
                .onFailure { android.util.Log.e("RC_DIAG", "Core.account(restore) failed", it) }
            prof != null
        } catch (e: Exception) {
            android.util.Log.e("RC_DIAG", "restoreForServer(${selected.address}) failed", e)
            clearLoginFor(selected)
            false
        }
    }

    suspend fun probeServer(
        address: String,
        expectedPin: String,
        name: String = "",
    ): Pair<ServerConfigModel, String> = withContext(Dispatchers.IO) {
        val known = _servers.value.firstOrNull { it.address.equals(address, ignoreCase = true) }
        val response = request(
            "probe",
            null,
            "address" to address,
            "pin" to expectedPin.ifBlank { known?.fingerprint.orEmpty() },
        )
        val model = ServerConfigModel(
            address = address,
            fingerprint = response.getString("caSha256"),
            caPem = response.getString("caPem"),
            name = name.trim(),
            allowRegistration = response.optBoolean("allowRegistration", true),
        )
        val health = if (response.getString("status") == "ok") "连接正常" else "服务状态异常"
        val report = "$health · ${response.getLong("elapsedMs")} ms"
        model to report
    }

    /** Upserts [selected] into the saved servers list, switches to it, and returns true if login was restored. */
    suspend fun saveServer(selected: ServerConfigModel): Boolean = withContext(Dispatchers.IO) {
        val existingIdx = _servers.value.indexOfFirst { it.address.equals(selected.address, ignoreCase = true) }
        val updated = _servers.value.toMutableList()
        if (existingIdx >= 0) {
            val prev = updated[existingIdx]
            if (prev.id != selected.id) {
                prefs.edit()
                    .remove(tokenKey(prev))
                    .remove(deviceIdKey(prev))
                    .remove(emailKey(prev))
                    .apply()
            }
            updated[existingIdx] = selected
        } else {
            updated.add(selected)
        }
        persistServers(updated)
        switchServer(selected)
    }

    /** Switches active server, stops previous signal/session, and attempts to restore saved tokens for [target]. */
    suspend fun switchServer(target: ServerConfigModel): Boolean = withContext(Dispatchers.IO) {
        runCatching { Core.account(JSONObject().put("action", "disconnect").toString()) }
        prefs.edit().putString(KEY_ACTIVE_SERVER_ID, target.id).commit()
        tokens = null
        _profile.value = null
        _devices.value = emptyList()
        _server.value = target
        restoreForServer(target)
    }

    suspend fun removeServer(target: ServerConfigModel) = withContext(Dispatchers.IO) {
        val wasActive = _server.value?.id == target.id
        prefs.edit()
            .remove(tokenKey(target))
            .remove(deviceIdKey(target))
            .remove(emailKey(target))
            .commit()
        val remaining = _servers.value.filterNot { it.id == target.id }
        persistServers(remaining)
        if (wasActive) {
            val next = remaining.firstOrNull()
            if (next != null) {
                switchServer(next)
            } else {
                runCatching { Core.account(JSONObject().put("action", "disconnect").toString()) }
                prefs.edit().remove(KEY_ACTIVE_SERVER_ID).commit()
                tokens = null
                _profile.value = null
                _devices.value = emptyList()
                _server.value = null
            }
        }
    }

    suspend fun fetchAuthConfig(target: ServerConfigModel): Boolean = withContext(Dispatchers.IO) {
        val resp = request("auth_config", target)
        val allowReg = resp.optBoolean("allowRegistration", true)
        if (target.allowRegistration != allowReg) {
            val updatedTarget = target.copy(allowRegistration = allowReg)
            val nextList = _servers.value.map { if (it.id == target.id) updatedTarget else it }
            persistServers(nextList)
            if (_server.value?.id == target.id) {
                _server.value = updatedTarget
            }
        }
        allowReg
    }

    suspend fun login(email: String, password: String): UserProfile = withContext(Dispatchers.IO) {
        val selected = checkNotNull(_server.value) { "请先配置服务器" }
        val response = request("login", selected, "email" to email, "password" to password)
        check(response.optJSONObject("user") != null) { "服务器没有返回账号信息" }
        response.optJSONObject("device")?.optString("device_id")?.takeIf { it.isNotBlank() }?.let { did ->
            prefs.edit().putString(deviceIdKey(selected), did).apply()
        }
        saveTokens(response, selected)
        checkNotNull(_profile.value)
    }

    suspend fun register(email: String, displayName: String, password: String): UserProfile =
        withContext(Dispatchers.IO) {
            val selected = checkNotNull(_server.value) { "请先配置服务器" }
            val args = mutableListOf("email" to email, "password" to password)
            if (displayName.isNotBlank()) {
                args.add("displayName" to displayName.trim())
            }
            val response = request("register", selected, *args.toTypedArray())
            check(response.optJSONObject("user") != null) { "服务器没有返回账号信息" }
            response.optJSONObject("device")?.optString("device_id")?.takeIf { it.isNotBlank() }?.let { did ->
                prefs.edit().putString(deviceIdKey(selected), did).apply()
            }
            saveTokens(response, selected)
            checkNotNull(_profile.value)
        }

    suspend fun refreshDevices(): List<RemoteDevice> = withContext(Dispatchers.IO) {
        android.util.Log.i("RC_DIAG", "refreshDevices start")
        val selected = checkNotNull(_server.value) { "请先配置服务器" }
        val raw = authenticated {
            request("devices", selected, "access" to it.getString("access_token"))
        }.getJSONArray("devices")
        android.util.Log.i("RC_DIAG", "refreshDevices got ${raw.length()} items")
        val selfId = prefs.getString(deviceIdKey(selected), null)
        val list = List(raw.length()) { index ->
            val item = raw.getJSONObject(index)
            val id = item.getString("device_id")
            RemoteDevice(
                id = id,
                name = item.optString("alias").ifBlank { item.optString("hostname").ifBlank { id } },
                os = item.optString("os"),
                online = item.optBoolean("online"),
                busy = item.optBoolean("in_session"),
                disabled = item.optBoolean("disabled"),
                allowRemoteAssist = item.optBoolean("allow_remote_assist"),
            )
        }.filter { it.id != selfId }
        _devices.value = list
        list
    }

    suspend fun changePassword(current: String, replacement: String): UserProfile = withContext(Dispatchers.IO) {
        val selected = checkNotNull(_server.value) { "请先配置服务器" }
        val changed = authenticated {
            request(
                "password",
                selected,
                "access" to it.getString("access_token"),
                "password" to current,
                "replacement" to replacement,
            )
        }
        saveTokens(changed, selected)
        checkNotNull(_profile.value)
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val selected = _server.value
        val current = tokens
        if (current != null && selected != null) {
            runCatching {
                request(
                    "logout",
                    selected,
                    "access" to current.getString("access_token"),
                    "refresh" to current.getString("refresh_token"),
                )
            }
        }
        selected?.let { clearLoginFor(it) }
    }

    private fun clearLoginFor(target: ServerConfigModel) {
        if (_server.value?.id == target.id) {
            tokens = null
            _profile.value = null
            _devices.value = emptyList()
        }
        prefs.edit()
            .remove(tokenKey(target))
            .remove(deviceIdKey(target))
            .remove(emailKey(target))
            .commit()
        _savedAccountEmails.value = loadSavedEmails(_servers.value)
    }

    private fun <T> authenticated(action: (JSONObject) -> T): T {
        val selected = checkNotNull(_server.value) { "请先配置服务器" }
        val current = checkNotNull(tokens) { "请先登录" }
        try {
            return action(current)
        } catch (_: SecurityException) {
            val refreshed = try {
                request("refresh", selected, "refresh" to current.getString("refresh_token"))
            } catch (expired: SecurityException) {
                clearLoginFor(selected)
                throw expired
            }
            if (refreshed.optJSONObject("user") == null && current.has("user")) {
                refreshed.put("user", current.getJSONObject("user"))
            }
            saveTokens(refreshed, selected)
            val syncReq = JSONObject()
                .put("action", "restore")
                .put("address", selected.address)
                .put("pin", selected.fingerprint)
                .put("ca", selected.caPem)
                .put("tokens", refreshed)
            prefs.getString(deviceIdKey(selected), null)?.let { syncReq.put("deviceId", it) }
            runCatching { Core.account(syncReq.toString()) }
            return action(refreshed)
        }
    }

    private fun request(action: String, selected: ServerConfigModel?, vararg args: Pair<String, String>): JSONObject {
        val req = JSONObject().put("action", action)
        selected?.let {
            req.put("address", it.address).put("ca", it.caPem).put("pin", it.fingerprint)
            prefs.getString(deviceIdKey(it), null)?.let { did -> req.put("deviceId", did) }
        }
        args.forEach { (key, value) -> req.put(key, value) }
        return JSONObject(Core.account(req.toString()))
    }

    private fun saveTokens(response: JSONObject, selected: ServerConfigModel) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(serverIdentity(selected))
        val encrypted = cipher.doFinal(response.toString().toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val prof = parseProfile(response)
        val editor = prefs.edit().putString(tokenKey(selected), encoded)
        if (prof != null) {
            editor.putString(emailKey(selected), prof.email)
        }
        check(editor.commit()) { "无法保存本机账号配置" }
        if (_server.value?.id == selected.id) {
            tokens = response
            _profile.value = prof
        }
        _savedAccountEmails.value = loadSavedEmails(_servers.value)
    }

    private fun serverIdentity(selected: ServerConfigModel) =
        "${selected.address}\n${selected.fingerprint}".toByteArray(Charsets.UTF_8)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "rabbitcontrol.account.v1"
        const val KEY_SERVERS_V2 = "servers_v2"
        const val KEY_ACTIVE_SERVER_ID = "active_server_id"
    }
}
