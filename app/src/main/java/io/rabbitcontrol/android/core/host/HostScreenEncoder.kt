package io.rabbitcontrol.android.core.host

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import io.rabbitcontrol.android.session.Core
import kotlin.concurrent.thread

/**
 * Hardware Surface-to-H.264 Annex-B encoder driven by Android's [MediaProjection].
 * Keeps a single reusable [VirtualDisplay] across client reconnections/resizes so Android 14+
 * single-use MediaProjection tokens remain valid for the lifetime of the host session.
 */
class HostScreenEncoder(
    private val projection: MediaProjection,
    private val densityDpi: Int,
) {
    private val lock = Any()
    private var virtualDisplay: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var drainThread: Thread? = null
    private var csdBuffer: ByteArray? = null

    @Volatile
    private var active = false

    private var currentWidth = 0
    private var currentHeight = 0
    private var currentFps = 60
    private var currentBitrate = 8_000_000

    fun startOrReconfigure(width: Int, height: Int, fps: Int, bitrateBps: Int) {
        val w = (width.coerceAtLeast(2)) and 1.inv()
        val h = (height.coerceAtLeast(2)) and 1.inv()
        val targetFps = fps.coerceIn(10, 120)
        val targetBitrate = bitrateBps.coerceIn(500_000, 60_000_000)

        synchronized(lock) {
            if (active && codec != null && w == currentWidth && h == currentHeight && targetFps == currentFps) {
                if (targetBitrate != currentBitrate) {
                    applyBitrateLocked(targetBitrate)
                }
                requestIdrLocked()
                return
            }

            stopCodecLocked()
            currentWidth = w
            currentHeight = h
            currentFps = targetFps
            currentBitrate = targetBitrate
            csdBuffer = null

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                if (Build.VERSION.SDK_INT >= 30) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
                setInteger(MediaFormat.KEY_PRIORITY, 0)
            }

            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            try {
                enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            } catch (err: Throwable) {
                Log.w("RC_DIAG", "HostScreenEncoder low-latency configure fallback: ${err.message}")
                val basicFmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                }
                enc.configure(basicFmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            val surface = enc.createInputSurface()
            enc.start()

            val vd = virtualDisplay
            if (vd == null) {
                virtualDisplay = projection.createVirtualDisplay(
                    "RabbitControlHost",
                    w,
                    h,
                    densityDpi.coerceAtLeast(160),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null,
                )
            } else {
                vd.resize(w, h, densityDpi.coerceAtLeast(160))
                vd.surface = surface
            }

            codec = enc
            inputSurface = surface
            active = true
            drainThread = thread(name = "rc-host-enc-drain") {
                drainLoop(enc)
            }
            Log.i("RC_DIAG", "HostScreenEncoder started ${w}x${h}@${targetFps}fps bitrate=$targetBitrate")
        }
    }

    fun setBitrate(bps: Int) {
        val clamped = bps.coerceIn(500_000, 60_000_000)
        synchronized(lock) {
            applyBitrateLocked(clamped)
        }
    }

    fun requestIdr() {
        synchronized(lock) {
            requestIdrLocked()
        }
    }

    fun pauseEncoder() {
        synchronized(lock) {
            stopCodecLocked()
            runCatching { virtualDisplay?.surface = null }
        }
    }

    fun releaseAll() {
        synchronized(lock) {
            stopCodecLocked()
            runCatching { virtualDisplay?.release() }
            virtualDisplay = null
        }
    }

    private fun applyBitrateLocked(bps: Int) {
        currentBitrate = bps
        val enc = codec ?: return
        runCatching {
            val bundle = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bps)
            }
            enc.setParameters(bundle)
        }
    }

    private fun requestIdrLocked() {
        val enc = codec ?: return
        runCatching {
            val bundle = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            enc.setParameters(bundle)
        }
    }

    private fun stopCodecLocked() {
        active = false
        val t = drainThread
        drainThread = null
        val enc = codec
        codec = null
        val surf = inputSurface
        inputSurface = null
        if (enc != null) {
            runCatching { enc.signalEndOfInputStream() }
        }
        if (t != null && Thread.currentThread() !== t) {
            runCatching { t.join(350L) }
        }
        if (enc != null) {
            runCatching { enc.stop() }
            runCatching { enc.release() }
        }
        runCatching { surf?.release() }
    }

    private fun drainLoop(enc: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (active) {
            val idx = try {
                enc.dequeueOutputBuffer(info, 12_000L)
            } catch (_: Throwable) {
                break
            }
            if (idx < 0) continue

            try {
                if (info.size > 0) {
                    val buf = enc.getOutputBuffer(idx)
                    if (buf != null) {
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        buf.get(bytes)

                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (isConfig) {
                            csdBuffer = bytes
                        } else {
                            val isKeyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                            val payload = if (isKeyframe) prependCsdIfMissing(bytes) else bytes
                            Core.pushHostEncodedFrame(payload, isKeyframe)
                        }
                    }
                }
            } finally {
                runCatching { enc.releaseOutputBuffer(idx, false) }
            }
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                break
            }
        }
    }

    private fun prependCsdIfMissing(frame: ByteArray): ByteArray {
        val csd = csdBuffer ?: return frame
        if (startsWithSps(frame)) return frame
        val out = ByteArray(csd.size + frame.size)
        System.arraycopy(csd, 0, out, 0, csd.size)
        System.arraycopy(frame, 0, out, csd.size, frame.size)
        return out
    }

    private fun startsWithSps(data: ByteArray): Boolean {
        if (data.size >= 5 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte()) {
            return (data[4].toInt() and 0x1F) == 7
        }
        if (data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte()) {
            return (data[3].toInt() and 0x1F) == 7
        }
        return false
    }
}
