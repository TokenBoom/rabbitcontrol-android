package io.rabbitcontrol.android.video

import android.content.res.Resources
import android.os.SystemClock
import android.view.Surface
import io.rabbitcontrol.android.R
import java.io.Closeable
import java.util.concurrent.locks.LockSupport

/** Created after surfaceCreated; close completes before SurfaceHolder releases the surface. */
internal class PreviewPlayer(
    resources: Resources,
    surface: Surface,
    update: (String) -> Unit,
) : Closeable {
    @Volatile private var running = true
    private val worker = Thread({
        try {
            val clip = resources.openRawResource(R.raw.motion).use { MotionClip(it.readBytes()) }
            AvcDecoder.open(surface, MotionClip.WIDTH, MotionClip.HEIGHT, clip.sps, clip.pps).use { decoder ->
                var frame = 0L
                val interval = 1_000_000_000L / MotionClip.FPS
                var due = SystemClock.elapsedRealtimeNanos()
                var window = due
                var submitted = 0
                update(decoder.description)
                while (running) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (decoder.drain()) submitted++
                    if (now >= due && decoder.offer(clip.frames[(frame % clip.frames.size).toInt()], frame * interval / 1000)) {
                        frame++
                        // Slow devices play the sample at their own rate; never build a catch-up queue.
                        due = maxOf(due + interval, now + interval / 2)
                    }
                    if (now - window >= 1_000_000_000L) {
                        val fps = submitted * 1_000_000_000L / (now - window)
                        update("${decoder.description}\n1280 × 720 · 送显 $fps fps")
                        submitted = 0
                        window = now
                    }
                    LockSupport.parkNanos(minOf((due - now).coerceAtLeast(1_000_000), 4_000_000))
                }
            }
        } catch (e: Exception) {
            if (running) update("画面校验失败 · ${e.message ?: e.javaClass.simpleName}")
        }
    }, "rc-media").apply { start() }

    override fun close() {
        running = false
        LockSupport.unpark(worker)
        worker.join()
    }
}
