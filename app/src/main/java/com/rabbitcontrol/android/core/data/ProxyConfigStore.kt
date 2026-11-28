package com.rabbitcontrol.android.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Immutable
import com.rabbitcontrol.android.error.RcErrorCodes
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.error.RcUiErrorCodec
import com.rabbitcontrol.android.core.error.RcUiErrorException
import com.rabbitcontrol.android.core.i18n.AppStrings
import com.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import com.rabbitcontrol.android.session.Core
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ProxyModeOption(val wireValue: String) {
    None("none"),
    System("system"),
    Manual("manual");

    companion object {
        fun fromWire(raw: String?): ProxyModeOption =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: None
    }
}

enum class ProxyProtocolOption(val wireValue: String) {
    Http("http"),
    Socks5("socks5");

    companion object {
        fun fromWire(raw: String?): ProxyProtocolOption =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: Http
    }
}

@Immutable
data class ProxyConfigModel(
    val mode: ProxyModeOption = ProxyModeOption.None,
    val protocol: ProxyProtocolOption = ProxyProtocolOption.Http,
    val host: String = "",
    val port: Int = 7890,
    val username: String = "",
    val password: String = "",
) {
    override fun toString(): String {
        val pwdMarker = if (password.isNotEmpty()) "<redacted>" else ""
        return "ProxyConfigModel(mode=$mode, protocol=$protocol, host=$host, port=$port, username=$username, password=$pwdMarker)"
    }

    fun toNativeJson(): JSONObject {
        val obj = JSONObject()
            .put("mode", mode.wireValue)
            .put("host", host.trim())
            .put("port", port.coerceIn(0, 65535))
        if (mode == ProxyModeOption.Manual) {
            obj.put("protocol", protocol.wireValue)
            if (username.isNotBlank()) obj.put("username", username.trim())
            if (password.isNotEmpty()) obj.put("password", password)
        }
        return obj
    }

    fun summaryLabel(): String = when (mode) {
        ProxyModeOption.None -> AppStrings.get(R.string.proxy_mode_none, "直连（无代理）")
        ProxyModeOption.System -> AppStrings.get(R.string.proxy_mode_system, "跟随系统代理")
        ProxyModeOption.Manual -> "${protocol.wireValue.uppercase()} ${host.trim()}:$port"
    }
}

class ProxyConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("rabbitcontrol_proxy_config", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ProxyConfigModel> = _state.asStateFlow()

    private fun load(): ProxyConfigModel {
        val mode = ProxyModeOption.fromWire(prefs.getString(KEY_MODE, "none"))
        val protocol = ProxyProtocolOption.fromWire(prefs.getString(KEY_PROTOCOL, "http"))
        val host = prefs.getString(KEY_HOST, "").orEmpty()
        val port = prefs.getInt(KEY_PORT, 7890).coerceIn(1, 65535)
        val username = prefs.getString(KEY_USERNAME, "").orEmpty()
        val encryptedPwd = prefs.getString(KEY_PASSWORD_ENC, null)
        val password = if (!encryptedPwd.isNullOrBlank()) {
            decryptPassword(encryptedPwd).orEmpty()
        } else {
            ""
        }
        return ProxyConfigModel(
            mode = mode,
            protocol = protocol,
            host = host,
            port = port,
            username = username,
            password = password,
        )
    }

    fun saveAndApply(config: ProxyConfigModel): Result<Unit> {
        NativeRuntimeBootstrap.ensureReady(appContext)
        val res = runCatching {
            Core.setProxyConfig(config.toNativeJson().toString())
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { err ->
                Result.failure(
                    RcUiErrorException(
                        RcUiErrorCodec.fromThrowable(err, fallbackCode = RcErrorCodes.PROXY_CONFIG_CONFLICT),
                        err,
                    ),
                )
            },
        )
        if (res.isFailure) return res

        val editor = prefs.edit()
            .putString(KEY_MODE, config.mode.wireValue)
            .putString(KEY_PROTOCOL, config.protocol.wireValue)
            .putString(KEY_HOST, config.host.trim())
            .putInt(KEY_PORT, config.port.coerceIn(1, 65535))
            .putString(KEY_USERNAME, config.username.trim())

        if (config.password.isEmpty()) {
            editor.remove(KEY_PASSWORD_ENC)
        } else {
            val encrypted = encryptPassword(config.password)
                ?: return Result.failure(
                    RcUiErrorException(
                        RcUiErrorCodec.resolvePlatformError(
                            code = RcErrorCodes.SERVICE_RUNTIME_NOT_INITIALIZED,
                            detail = "Keystore encrypt failed",
                        ),
                    ),
                )
            editor.putString(KEY_PASSWORD_ENC, encrypted)
        }
        editor.apply()
        _state.value = config
        return Result.success(Unit)
    }

    fun applySavedOnStartup() {
        val current = _state.value
        if (current.mode != ProxyModeOption.None) {
            runCatching { Core.setProxyConfig(current.toNativeJson().toString()) }
        }
    }

    private fun encryptPassword(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.getOrNull()

    private fun decryptPassword(encoded: String): String? = runCatching {
        val parts = encoded.split('.', limit = 2)
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)),
        )
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

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
        const val KEY_ALIAS = "rabbitcontrol.proxy.v1"
        const val KEY_MODE = "mode"
        const val KEY_PROTOCOL = "protocol"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD_ENC = "password_enc"
    }
}
