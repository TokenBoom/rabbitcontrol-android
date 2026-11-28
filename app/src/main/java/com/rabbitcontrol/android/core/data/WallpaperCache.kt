package com.rabbitcontrol.android.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class WallpaperCache(context: Context) {
    private val dir = File(context.cacheDir, "wallpapers").apply { mkdirs() }
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    // Cap in-memory wallpaper cache at 12 MB using byte-sized accounting
    private val memoryCache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 2).coerceAtLeast(1024)
    }

    fun saveDataUrl(deviceId: String, dataUrl: String) {
        if (deviceId.isBlank() || !dataUrl.startsWith("data:image/")) return
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) return
        val bytes = runCatching { Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT) }.getOrNull() ?: return
        if (bytes.isEmpty()) return
        val safe = safeId(deviceId)
        val target = File(dir, "$safe.img")
        val tmp = File(dir, "$safe.tmp")
        runCatching {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            evictDevice(safe)
            _version.value = _version.value + 1L
        }
    }

    fun getCachedImageBitmap(deviceId: String, maxDimensionPx: Int): ImageBitmap? {
        if (deviceId.isBlank()) return null
        val key = cacheKey(safeId(deviceId), maxDimensionPx)
        synchronized(memoryCache) {
            return memoryCache.get(key)
        }
    }

    suspend fun loadImageBitmapAsync(deviceId: String, maxDimensionPx: Int): ImageBitmap? {
        if (deviceId.isBlank()) return null
        val safe = safeId(deviceId)
        val key = cacheKey(safe, maxDimensionPx)
        synchronized(memoryCache) {
            memoryCache.get(key)?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            synchronized(memoryCache) {
                memoryCache.get(key)?.let { return@withContext it }
            }
            val f = File(dir, "$safe.img")
            if (!f.exists()) return@withContext null
            val decoded = decodeDownsampledBitmap(f.absolutePath, maxDimensionPx.coerceAtLeast(128))
                ?.asImageBitmap()
                ?: return@withContext null
            synchronized(memoryCache) {
                memoryCache.put(key, decoded)
            }
            decoded
        }
    }

    fun loadBitmap(deviceId: String): Bitmap? {
        if (deviceId.isBlank()) return null
        val f = fileFor(deviceId)
        if (!f.exists()) return null
        return decodeDownsampledBitmap(f.absolutePath, 960)
    }

    private fun decodeDownsampledBitmap(path: String, reqMaxPx: Int): Bitmap? = runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        var sampleSize = 1
        val maxSide = maxOf(srcW, srcH)
        while ((maxSide / (sampleSize * 2)) >= reqMaxPx) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeFile(path, decodeOpts)
    }.getOrNull()

    private fun evictDevice(safeId: String) {
        val prefix = "$safeId@"
        synchronized(memoryCache) {
            val keys = memoryCache.snapshot().keys.filter { it.startsWith(prefix) }
            keys.forEach { memoryCache.remove(it) }
        }
    }

    private fun cacheKey(safeId: String, maxDim: Int) = "$safeId@$maxDim"
    private fun safeId(deviceId: String) = deviceId.replace(SAFE_ID_REGEX, "_")
    private fun fileFor(deviceId: String) = File(dir, "${safeId(deviceId)}.img")

    private companion object {
        val SAFE_ID_REGEX = Regex("[^a-zA-Z0-9._-]")
    }
}

@Composable
fun rememberWallpaperImage(
    wallpaperCache: WallpaperCache,
    deviceId: String,
    version: Long,
    maxDimensionPx: Int,
): ImageBitmap? {
    var image by remember(deviceId, version, maxDimensionPx) {
        mutableStateOf(wallpaperCache.getCachedImageBitmap(deviceId, maxDimensionPx))
    }
    LaunchedEffect(deviceId, version, maxDimensionPx) {
        if (image == null && deviceId.isNotBlank()) {
            image = wallpaperCache.loadImageBitmapAsync(deviceId, maxDimensionPx)
        }
    }
    return image
}
