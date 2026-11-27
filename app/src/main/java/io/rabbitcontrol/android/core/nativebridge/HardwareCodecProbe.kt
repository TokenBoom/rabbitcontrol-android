package io.rabbitcontrol.android.core.nativebridge

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

internal object HardwareCodecProbe {
    private data class CodecTarget(
        val id: String,
        val mime: String,
    )

    private val targets: List<CodecTarget>
        get() = buildList {
            add(CodecTarget("h264", MediaFormat.MIMETYPE_VIDEO_AVC))
            add(CodecTarget("hevc", MediaFormat.MIMETYPE_VIDEO_HEVC))
            if (Build.VERSION.SDK_INT >= 29) {
                add(CodecTarget("av1", MediaFormat.MIMETYPE_VIDEO_AV1))
            }
        }

    fun probeAvcDecodersJson(): String = probeDecodersJson()

    fun probeDecodersJson(): String {
        val codecs = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList()
        }.getOrDefault(emptyList())

        val arr = JSONArray()
        for (target in targets) {
            val base1080 = MediaFormat.createVideoFormat(target.mime, 1920, 1080)
            val matched = codecs
                .filter { !it.isEncoder && !it.isAlias && target.mime in it.supportedTypes }
                .filter {
                    runCatching {
                        it.getCapabilitiesForType(target.mime).isFormatSupported(base1080)
                    }.getOrDefault(false)
                }
                .sortedWith(
                    compareByDescending<MediaCodecInfo> { it.isHardwareAccelerated }
                        .thenByDescending { supportsLowLatency(it, target.mime) }
                        .thenByDescending { supports4k(it, target.mime) },
                )

            for (info in matched) {
                val (maxW, maxH) = maxSupportedResolution(info, target.mime)
                arr.put(
                    JSONObject()
                        .put("codec", target.id)
                        .put("name", info.name)
                        .put("hardware", info.isHardwareAccelerated)
                        .put("low_latency", supportsLowLatency(info, target.mime))
                        .put("max_width", maxW)
                        .put("max_height", maxH),
                )
            }
        }
        return arr.toString()
    }

    private fun supportsLowLatency(info: MediaCodecInfo, mime: String): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return runCatching {
            info.getCapabilitiesForType(mime)
                .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
        }.getOrDefault(false)
    }

    private fun supports4k(info: MediaCodecInfo, mime: String): Boolean {
        val fmt4k = MediaFormat.createVideoFormat(mime, 3840, 2160)
        return runCatching {
            info.getCapabilitiesForType(mime).isFormatSupported(fmt4k)
        }.getOrDefault(false)
    }

    private fun maxSupportedResolution(info: MediaCodecInfo, mime: String): Pair<Int, Int> {
        return runCatching {
            val vc = info.getCapabilitiesForType(mime).videoCapabilities ?: return 3840 to 2160
            vc.supportedWidths.upper.coerceAtLeast(1920) to vc.supportedHeights.upper.coerceAtLeast(1080)
        }.getOrDefault(3840 to 2160)
    }
}
