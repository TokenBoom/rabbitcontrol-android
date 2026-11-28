package com.rabbitcontrol.android.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.rabbitcontrol.android.R
import com.rabbitcontrol.android.core.i18n.AppStrings
import java.io.Closeable

/** Single media-thread owner. Only compressed access units enter the codec. */
internal class AvcDecoder private constructor(
    private val codec: MediaCodec,
    val description: String,
) : Closeable {
    private val info = MediaCodec.BufferInfo()

    /** A busy codec retains no caller data. The caller chooses retry or IDR recovery. */
    fun offer(accessUnit: ByteArray, ptsUs: Long): Boolean {
        val index = codec.dequeueInputBuffer(0)
        if (index < 0) return false
        val buffer = checkNotNull(codec.getInputBuffer(index))
        require(accessUnit.size <= buffer.remaining()) {
            AppStrings.get(R.string.preview_err_capacity, "压缩帧超出解码器输入容量")
        }
        buffer.put(accessUnit)
        codec.queueInputBuffer(index, 0, accessUnit.size, ptsUs, 0)
        return true
    }

    /** When output backs up, present only the newest available decoded frame. */
    fun drain(): Boolean {
        var newest = -1
        while (true) {
            val index = codec.dequeueOutputBuffer(info, 0)
            when {
                index >= 0 && info.size > 0 -> {
                    if (newest >= 0) codec.releaseOutputBuffer(newest, false)
                    newest = index
                }
                index >= 0 -> codec.releaseOutputBuffer(index, false)
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> continue
                else -> break
            }
        }
        if (newest >= 0) codec.releaseOutputBuffer(newest, true)
        return newest >= 0
    }

    override fun close() {
        try { codec.stop() } finally { codec.release() }
    }

    companion object {
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

        fun open(surface: Surface, width: Int, height: Int, sps: ByteArray, pps: ByteArray): AvcDecoder {
            val base = MediaFormat.createVideoFormat(MIME, width, height)
            val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && !it.isAlias && MIME in it.supportedTypes }
                .filter { it.getCapabilitiesForType(MIME).isFormatSupported(base) }
                .sortedByDescending { it.isHardwareAccelerated }
            var failure: Exception? = null
            for (entry in candidates) {
                val lowLatency = Build.VERSION.SDK_INT >= 30 && entry.getCapabilitiesForType(MIME)
                    .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
                for (low in if (lowLatency) listOf(true, false) else listOf(false)) {
                    var codec: MediaCodec? = null
                    try {
                        val format = MediaFormat.createVideoFormat(MIME, width, height)
                        format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(sps))
                        format.setByteBuffer("csd-1", java.nio.ByteBuffer.wrap(pps))
                        if (Build.VERSION.SDK_INT >= 30 && low) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                        codec = MediaCodec.createByCodecName(entry.name)
                        codec.configure(format, surface, null, 0)
                        codec.start()
                        val path = if (entry.isHardwareAccelerated) {
                            AppStrings.get(R.string.preview_codec_hw, "硬解")
                        } else {
                            AppStrings.get(R.string.preview_codec_sw, "软件解码")
                        }
                        val lowSuffix = if (low) {
                            " · ${AppStrings.get(R.string.preview_codec_low_latency, "低延迟")}"
                        } else {
                            ""
                        }
                        return AvcDecoder(codec, "${entry.name} · $path$lowSuffix")
                    } catch (e: Exception) {
                        failure = e
                        codec?.release()
                    }
                }
            }
            throw IllegalStateException(
                AppStrings.get(R.string.preview_err_unsupported, "设备无法解码此规格的 H.264 画面"),
                failure,
            )
        }
    }
}
