package com.rabbitcontrol.android.core.nativebridge

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Derives a deterministic 64-char hex `device_key` anchored to Android's app-scoped `ANDROID_ID`
 * (SSAID), which persists across uninstall/reinstall on Android 8.0+ without requiring extra
 * permissions.
 */
internal object DeviceKeyProvider {
    private val INVALID_ANDROID_IDS = setOf(
        "9774d56d682e549c",
        "0000000000000000",
    )

    @SuppressLint("HardwareIds")
    fun resolveStableDeviceKey(context: Context): String? {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return deriveStableDeviceKey(androidId, context.packageName)
    }

    internal fun deriveStableDeviceKey(androidId: String?, packageName: String): String? {
        val normalized = androidId?.trim()?.lowercase().orEmpty()
        if (normalized.length < 8 || normalized in INVALID_ANDROID_IDS) {
            return null
        }
        val pkg = packageName.trim()
        val seed = "rabbitcontrol:android:device-key:v1:$pkg:$normalized"
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
