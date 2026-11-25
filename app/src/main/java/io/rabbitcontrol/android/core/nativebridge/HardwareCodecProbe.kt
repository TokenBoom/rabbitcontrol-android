package io.rabbitcontrol.android.core.nativebridge

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

internal object HardwareCodecProbe {
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    fun probeAvcDecodersJson(): String {
        val list = runCatching {
            val base1080 = MediaFormat.createVideoFormat(MIME, 1920, 1080)
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && !it.isAlias && MIME in it.supportedTypes }
                .filter { runCatching { it.getCapabilitiesForType(MIME).isFormatSupported(base1080) }.getOrDefault(false) }
                .sortedWith(
                    compareByDescending<MediaCodecInfo> { it.isHardwareAccelerated }
                        .thenByDescending { supportsLowLatency(it) }
                        .thenByDescending { supports4k(it) }
                )
        }.getOrDefault(emptyList())

        val arr = JSONArray()
        for (info in list) {
            arr.put(
                JSONObject()
                    .put("name", info.name)
                    .put("hardware", info.isHardwareAccelerated)
                    .put("low_latency", supportsLowLatency(info))
            )
        }
        return arr.toString()
    }

    private fun supportsLowLatency(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return runCatching {
            info.getCapabilitiesForType(MIME)
                .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
        }.getOrDefault(false)
    }

    private fun supports4k(info: MediaCodecInfo): Boolean {
        val fmt4k = MediaFormat.createVideoFormat(MIME, 3840, 2160)
        return runCatching {
            info.getCapabilitiesForType(MIME).isFormatSupported(fmt4k)
        }.getOrDefault(false)
    }
}
