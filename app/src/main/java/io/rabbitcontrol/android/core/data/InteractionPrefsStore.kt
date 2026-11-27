package io.rabbitcontrol.android.core.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TouchInputMode(
    @StringRes val labelRes: Int,
    private val defaultLabel: String,
) {
    Trackpad(R.string.touch_mode_trackpad, "精密触控板"),
    DirectTouch(R.string.touch_mode_direct, "直点触控");

    val label: String
        get() = AppStrings.get(labelRes, defaultLabel)
}

enum class OrientationPreference(
    @StringRes val labelRes: Int,
    private val defaultLabel: String,
) {
    AutoSensor(R.string.orientation_auto, "自动旋转"),
    LandscapeFirst(R.string.orientation_landscape, "锁定横屏"),
    PortraitTrackpad(R.string.orientation_portrait, "锁定竖屏");

    val label: String
        get() = AppStrings.get(labelRes, defaultLabel)
}

enum class PortraitLayoutMode(
    @StringRes val labelRes: Int,
    private val defaultLabel: String,
) {
    Auto(R.string.portrait_layout_auto, "智能"),
    Fullscreen(R.string.portrait_layout_fullscreen, "竖屏全屏"),
    SplitTrackpad(R.string.portrait_layout_split, "触控板分屏");

    val label: String
        get() = AppStrings.get(labelRes, defaultLabel)
}

enum class AppThemeMode(
    @StringRes val labelRes: Int,
    private val defaultLabel: String,
) {
    System(R.string.theme_system, "跟随系统"),
    Light(R.string.theme_light, "浅色"),
    Dark(R.string.theme_dark, "深色");

    val label: String
        get() = AppStrings.get(labelRes, defaultLabel)
}

enum class ConnectionModePref(
    val wireValue: String,
    @StringRes val labelRes: Int,
    private val defaultLabel: String,
) {
    Auto("auto", R.string.conn_mode_auto, "自动"),
    P2pOnly("p2p_only", R.string.conn_mode_p2p, "仅 P2P"),
    TcpRelay("tcp_relay", R.string.conn_mode_relay, "TCP 中继");

    val label: String
        get() = AppStrings.get(labelRes, defaultLabel)

    companion object {
        fun fromWire(raw: String?): ConnectionModePref = when (raw?.trim()?.lowercase()) {
            "p2p_only", "p2p" -> P2pOnly
            "tcp_relay", "relay" -> TcpRelay
            else -> Auto
        }
    }
}

enum class HostAuthMode(
    val wireValue: String,
    @StringRes val labelRes: Int,
    private val defaultLabel: String,
) {
    TempCode("temp_code", R.string.host_auth_mode_temp, "动态临时验证码"),
    FixedPassword("fixed_password", R.string.host_auth_mode_fixed, "固定访问密码"),
    ApprovalOnly("approval_only", R.string.host_auth_mode_approval, "仅手动审批");

    val label: String
        get() = AppStrings.get(labelRes, defaultLabel)

    companion object {
        fun fromWire(raw: String?): HostAuthMode = when (raw?.trim()?.lowercase()) {
            "fixed_password", "fixed" -> FixedPassword
            "approval_only", "approval" -> ApprovalOnly
            else -> TempCode
        }
    }
}

@Immutable
data class InteractionPrefs(
    val touchMode: TouchInputMode = TouchInputMode.Trackpad,
    val pointerSensitivity: Float = 1.15f,
    val naturalScroll: Boolean = true,
    val directTouchOffset: Boolean = true,
    val showHud: Boolean = true,
    val orientationPref: OrientationPreference = OrientationPreference.AutoSensor,
    val portraitLayoutMode: PortraitLayoutMode = PortraitLayoutMode.Auto,
    val lockScreenTimeoutSec: Int = 60,
    val audioEnabledDefault: Boolean = true,
    val clipboardEnabledDefault: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.System,
    val hostAllowRemoteAssist: Boolean = false,
    val hostAllowAssistFileTransfer: Boolean = true,
    val preferHardwareDecode: Boolean = true,
    val upnpEnabled: Boolean = false,
    val connectionMode: ConnectionModePref = ConnectionModePref.Auto,
    val encryptByDefault: Boolean = true,
    val autoLockRemoteOnDisconnect: Boolean = false,
    val terminalFontSizeSp: Int = 12,
    val hostAuthMode: HostAuthMode = HostAuthMode.TempCode,
    val hostFixedPassword: String = "",
)

class InteractionPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_interaction_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<InteractionPrefs> = _state.asStateFlow()

    private fun decryptFixedPassword(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return KeystoreCrypto.decrypt(KEY_ALIAS_HOST_PW, raw) ?: ""
    }

    private fun encryptFixedPassword(pw: String): String {
        if (pw.isEmpty()) return ""
        return runCatching { KeystoreCrypto.encrypt(KEY_ALIAS_HOST_PW, pw) }.getOrDefault("")
    }

    private fun load(): InteractionPrefs {
        val touchMode = TouchInputMode.entries.getOrNull(prefs.getInt("touch_mode", 0))
            ?: TouchInputMode.Trackpad
        val orientationPrefName = prefs.getString("orientation_pref_v2", null)
        val orientationPref = OrientationPreference.entries.firstOrNull { it.name == orientationPrefName }
            ?: OrientationPreference.AutoSensor
        val portraitModeName = prefs.getString("portrait_layout_mode", null)
        val portraitLayoutMode = PortraitLayoutMode.entries.firstOrNull { it.name == portraitModeName }
            ?: PortraitLayoutMode.Auto
        val themeMode = AppThemeMode.entries.getOrNull(prefs.getInt("theme_mode", 0))
            ?: AppThemeMode.System
        val connectionMode = ConnectionModePref.fromWire(prefs.getString("connection_mode", null))
        val hostAuthMode = HostAuthMode.fromWire(prefs.getString("host_auth_mode", null))
        return InteractionPrefs(
            touchMode = touchMode,
            pointerSensitivity = prefs.getFloat("sensitivity", 1.15f).coerceIn(0.4f, 3.0f),
            naturalScroll = prefs.getBoolean("natural_scroll", true),
            directTouchOffset = prefs.getBoolean("direct_offset", true),
            showHud = prefs.getBoolean("show_hud", true),
            orientationPref = orientationPref,
            portraitLayoutMode = portraitLayoutMode,
            lockScreenTimeoutSec = prefs.getInt("lock_timeout_sec", 60),
            audioEnabledDefault = prefs.getBoolean("audio_default", true),
            clipboardEnabledDefault = prefs.getBoolean("clipboard_default", true),
            themeMode = themeMode,
            hostAllowRemoteAssist = prefs.getBoolean("host_allow_remote_assist", false),
            hostAllowAssistFileTransfer = prefs.getBoolean("host_allow_assist_file_transfer", true),
            preferHardwareDecode = prefs.getBoolean("prefer_hardware_decode", true),
            upnpEnabled = prefs.getBoolean("upnp_enabled", false),
            connectionMode = connectionMode,
            encryptByDefault = prefs.getBoolean("encrypt_by_default", true),
            autoLockRemoteOnDisconnect = prefs.getBoolean("auto_lock_remote_on_disconnect", false),
            terminalFontSizeSp = prefs.getInt("terminal_font_size_sp", 12).coerceIn(10, 20),
            hostAuthMode = hostAuthMode,
            hostFixedPassword = decryptFixedPassword(prefs.getString("host_fixed_password_enc", null)),
        )
    }

    fun update(transform: (InteractionPrefs) -> InteractionPrefs) {
        val rawNext = transform(_state.value)
        val next = rawNext.copy(terminalFontSizeSp = rawNext.terminalFontSizeSp.coerceIn(10, 20))
        val prevPw = _state.value.hostFixedPassword
        _state.value = next
        val editor = prefs.edit()
            .putInt("touch_mode", next.touchMode.ordinal)
            .putFloat("sensitivity", next.pointerSensitivity)
            .putBoolean("natural_scroll", next.naturalScroll)
            .putBoolean("direct_offset", next.directTouchOffset)
            .putBoolean("show_hud", next.showHud)
            .putString("orientation_pref_v2", next.orientationPref.name)
            .putString("portrait_layout_mode", next.portraitLayoutMode.name)
            .putInt("lock_timeout_sec", next.lockScreenTimeoutSec)
            .putBoolean("audio_default", next.audioEnabledDefault)
            .putBoolean("clipboard_default", next.clipboardEnabledDefault)
            .putInt("theme_mode", next.themeMode.ordinal)
            .putBoolean("host_allow_remote_assist", next.hostAllowRemoteAssist)
            .putBoolean("host_allow_assist_file_transfer", next.hostAllowAssistFileTransfer)
            .putBoolean("prefer_hardware_decode", next.preferHardwareDecode)
            .putBoolean("upnp_enabled", next.upnpEnabled)
            .putString("connection_mode", next.connectionMode.wireValue)
            .putBoolean("encrypt_by_default", next.encryptByDefault)
            .putBoolean("auto_lock_remote_on_disconnect", next.autoLockRemoteOnDisconnect)
            .putInt("terminal_font_size_sp", next.terminalFontSizeSp)
            .putString("host_auth_mode", next.hostAuthMode.wireValue)
        if (next.hostFixedPassword != prevPw) {
            val enc = encryptFixedPassword(next.hostFixedPassword)
            if (enc.isEmpty()) editor.remove("host_fixed_password_enc") else editor.putString("host_fixed_password_enc", enc)
        }
        editor.apply()
    }

    private companion object {
        const val KEY_ALIAS_HOST_PW = "rabbitcontrol.host.pw.v1"
    }
}
