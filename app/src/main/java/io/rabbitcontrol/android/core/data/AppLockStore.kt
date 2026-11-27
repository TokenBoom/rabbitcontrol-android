package io.rabbitcontrol.android.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

internal interface StringPrefBackend {
    fun getString(key: String, def: String?): String?
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getInt(key: String, def: Int): Int
    fun putValues(
        strings: Map<String, String?> = emptyMap(),
        booleans: Map<String, Boolean> = emptyMap(),
        ints: Map<String, Int> = emptyMap(),
    )
}

internal class SharedPrefsBackend(
    private val prefs: SharedPreferences,
) : StringPrefBackend {
    override fun getString(key: String, def: String?): String? = prefs.getString(key, def)
    override fun getBoolean(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    override fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)
    override fun putValues(
        strings: Map<String, String?>,
        booleans: Map<String, Boolean>,
        ints: Map<String, Int>,
    ) {
        val editor = prefs.edit()
        strings.forEach { (k, v) -> if (v == null) editor.remove(k) else editor.putString(k, v) }
        booleans.forEach { (k, v) -> editor.putBoolean(k, v) }
        ints.forEach { (k, v) -> editor.putInt(k, v) }
        editor.apply()
    }
}

@Immutable
data class AppLockUiState(
    val enabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockTimeoutSec: Int = 60,
    val locked: Boolean = false,
    val hasPin: Boolean = false,
    val pinLength: Int = 6,
    val failedAttempts: Int = 0,
)

class AppLockStore internal constructor(
    private val backend: StringPrefBackend,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    constructor(context: Context) : this(
        SharedPrefsBackend(
            context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        ),
    )

    private val random = SecureRandom()
    @Volatile
    private var backgroundedAtMs: Long = 0L

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<AppLockUiState> = _state.asStateFlow()

    private fun loadInitialState(): AppLockUiState {
        val enabled = backend.getBoolean(KEY_ENABLED, false)
        val salt = backend.getString(KEY_PIN_SALT, null).orEmpty()
        val hash = backend.getString(KEY_PIN_HASH, null).orEmpty()
        val hasPin = salt.isNotBlank() && hash.isNotBlank()
        val pinLen = backend.getInt(KEY_PIN_LENGTH, 6).coerceIn(4, 6)
        val bio = backend.getBoolean(KEY_BIOMETRIC, false)
        val timeout = backend.getInt(KEY_TIMEOUT_SEC, 60)
        return AppLockUiState(
            enabled = enabled && hasPin,
            biometricEnabled = bio,
            autoLockTimeoutSec = timeout,
            locked = enabled && hasPin,
            hasPin = hasPin,
            pinLength = pinLen,
            failedAttempts = 0,
        )
    }

    fun isValidPinFormat(pin: String): Boolean =
        pin.length in 4..6 && pin.all { it in '0'..'9' }

    fun setPin(pin: String): Boolean {
        if (!isValidPinFormat(pin)) return false
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hashPin(saltHex, pin)
        backend.putValues(
            strings = mapOf(KEY_PIN_SALT to saltHex, KEY_PIN_HASH to hashHex),
            booleans = mapOf(KEY_ENABLED to true),
            ints = mapOf(KEY_PIN_LENGTH to pin.length),
        )
        _state.value = _state.value.copy(
            enabled = true,
            hasPin = true,
            pinLength = pin.length,
            locked = false,
            failedAttempts = 0,
        )
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val salt = backend.getString(KEY_PIN_SALT, null).orEmpty()
        val expectedHash = backend.getString(KEY_PIN_HASH, null).orEmpty()
        if (salt.isBlank() || expectedHash.isBlank()) return false
        val actualHash = hashPin(salt, pin)
        if (constantTimeEquals(expectedHash, actualHash)) {
            _state.value = _state.value.copy(
                locked = false,
                failedAttempts = 0,
            )
            backgroundedAtMs = 0L
            return true
        }
        _state.value = _state.value.copy(
            failedAttempts = _state.value.failedAttempts + 1,
        )
        return false
    }

    fun unlockViaBiometric() {
        val cur = _state.value
        if (!cur.enabled || !cur.biometricEnabled) return
        _state.value = cur.copy(locked = false, failedAttempts = 0)
        backgroundedAtMs = 0L
    }

    fun setBiometricEnabled(enabled: Boolean) {
        backend.putValues(booleans = mapOf(KEY_BIOMETRIC to enabled))
        _state.value = _state.value.copy(biometricEnabled = enabled)
    }

    fun setAutoLockTimeoutSec(seconds: Int) {
        backend.putValues(ints = mapOf(KEY_TIMEOUT_SEC to seconds))
        _state.value = _state.value.copy(autoLockTimeoutSec = seconds)
    }

    fun disableWithPin(pin: String): Boolean {
        if (!verifyPin(pin)) return false
        disable()
        return true
    }

    fun disable() {
        backend.putValues(
            strings = mapOf(KEY_PIN_SALT to null, KEY_PIN_HASH to null),
            booleans = mapOf(KEY_ENABLED to false, KEY_BIOMETRIC to false),
        )
        backgroundedAtMs = 0L
        _state.value = _state.value.copy(
            enabled = false,
            biometricEnabled = false,
            hasPin = false,
            locked = false,
            failedAttempts = 0,
        )
    }

    fun lockNow() {
        val cur = _state.value
        if (!cur.enabled || !cur.hasPin) return
        _state.value = cur.copy(locked = true, failedAttempts = 0)
    }

    fun onAppBackgrounded() {
        val cur = _state.value
        if (!cur.enabled || !cur.hasPin || cur.locked) return
        backgroundedAtMs = clockMs()
    }

    fun onAppForegrounded() {
        val cur = _state.value
        if (!cur.enabled || !cur.hasPin || cur.locked) return
        val timeoutSec = cur.autoLockTimeoutSec
        val bgAt = backgroundedAtMs
        backgroundedAtMs = 0L
        if (timeoutSec < 0 || bgAt <= 0L) return
        val elapsedMs = (clockMs() - bgAt).coerceAtLeast(0L)
        if (elapsedMs >= timeoutSec * 1000L) {
            _state.value = cur.copy(locked = true, failedAttempts = 0)
        }
    }

    private fun hashPin(saltHex: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$saltHex:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val ba = a.toByteArray(Charsets.UTF_8)
        val bb = b.toByteArray(Charsets.UTF_8)
        if (ba.size != bb.size || ba.isEmpty()) return false
        var diff = 0
        for (i in ba.indices) {
            diff = diff or (ba[i].toInt() xor bb[i].toInt())
        }
        return diff == 0
    }

    private companion object {
        const val PREF_NAME = "rc_app_lock_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_TIMEOUT_SEC = "auto_lock_timeout_sec"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_LENGTH = "pin_length"
    }
}
