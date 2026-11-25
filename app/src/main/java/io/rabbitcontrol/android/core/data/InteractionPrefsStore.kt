package io.rabbitcontrol.android.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TouchInputMode(val label: String) {
    Trackpad("精密触控板"),
    DirectTouch("直点触控"),
}

enum class OrientationPreference(val label: String) {
    LandscapeFirst("横屏沉浸优先"),
    AutoSensor("跟随系统旋转"),
    PortraitTrackpad("竖屏单手触控"),
}

data class InteractionPrefs(
    val touchMode: TouchInputMode = TouchInputMode.Trackpad,
    val pointerSensitivity: Float = 1.15f,
    val naturalScroll: Boolean = true,
    val directTouchOffset: Boolean = true,
    val showHud: Boolean = true,
    val orientationPref: OrientationPreference = OrientationPreference.LandscapeFirst,
    val lockScreenTimeoutSec: Int = 60,
    val audioEnabledDefault: Boolean = true,
    val clipboardEnabledDefault: Boolean = true,
)

class InteractionPrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("rabbitcontrol_interaction_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<InteractionPrefs> = _state.asStateFlow()

    private fun load(): InteractionPrefs {
        val touchMode = TouchInputMode.entries.getOrNull(prefs.getInt("touch_mode", 0))
            ?: TouchInputMode.Trackpad
        val orientationPref = OrientationPreference.entries.getOrNull(prefs.getInt("orientation_pref", 0))
            ?: OrientationPreference.LandscapeFirst
        return InteractionPrefs(
            touchMode = touchMode,
            pointerSensitivity = prefs.getFloat("sensitivity", 1.15f).coerceIn(0.4f, 3.0f),
            naturalScroll = prefs.getBoolean("natural_scroll", true),
            directTouchOffset = prefs.getBoolean("direct_offset", true),
            showHud = prefs.getBoolean("show_hud", true),
            orientationPref = orientationPref,
            lockScreenTimeoutSec = prefs.getInt("lock_timeout_sec", 60),
            audioEnabledDefault = prefs.getBoolean("audio_default", true),
            clipboardEnabledDefault = prefs.getBoolean("clipboard_default", true),
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
            .putInt("orientation_pref", next.orientationPref.ordinal)
            .putInt("lock_timeout_sec", next.lockScreenTimeoutSec)
            .putBoolean("audio_default", next.audioEnabledDefault)
            .putBoolean("clipboard_default", next.clipboardEnabledDefault)
            .apply()
    }
}
