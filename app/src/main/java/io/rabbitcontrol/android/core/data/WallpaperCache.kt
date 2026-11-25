package io.rabbitcontrol.android.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class WallpaperCache(context: Context) {
    private val dir = File(context.cacheDir, "wallpapers").apply { mkdirs() }
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun saveDataUrl(deviceId: String, dataUrl: String) {
        if (deviceId.isBlank() || !dataUrl.startsWith("data:image/")) return
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) return
        val bytes = runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT) }.getOrNull() ?: return
        if (bytes.isEmpty()) return
        val target = fileFor(deviceId)
        val tmp = File(dir, "${safeId(deviceId)}.tmp")
        runCatching {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            _version.value = _version.value + 1L
        }
    }

    fun loadBitmap(deviceId: String): Bitmap? {
        if (deviceId.isBlank()) return null
        val f = fileFor(deviceId)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
    }

    private fun safeId(deviceId: String) = deviceId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    private fun fileFor(deviceId: String) = File(dir, "${safeId(deviceId)}.img")
}
