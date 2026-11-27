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
)

class InteractionPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_interaction_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<InteractionPrefs> = _state.asStateFlow()

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
        )
    }

    fun update(transform: (InteractionPrefs) -> InteractionPrefs) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
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
            .apply()
    }
}
