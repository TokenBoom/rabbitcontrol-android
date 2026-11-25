package io.rabbitcontrol.android.core.nativebridge

import android.content.Context
import android.os.Build
import android.util.Log
import io.rabbitcontrol.android.session.Core
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Ensures `librabbitcontrol_android.so` loading, MediaCodec capability probing,
 * and `Core.initRuntime` happen off the UI thread during cold start, while allowing
 * any early caller to await readiness safely without duplicate initialization.
 */
internal object NativeRuntimeBootstrap {
    private val started = AtomicBoolean(false)
    @Volatile
    private var ready = false
    private val latch = CountDownLatch(1)

    fun startAsync(context: Context) {
        val appCtx = context.applicationContext
        if (started.compareAndSet(false, true)) {
            thread(name = "rc-native-init", priority = Thread.NORM_PRIORITY + 1) {
                doInit(appCtx)
            }
        }
    }

    fun ensureReady(context: Context? = null) {
        if (ready) return
        if (context != null && started.compareAndSet(false, true)) {
            doInit(context.applicationContext)
            return
        }
        runCatching { latch.await() }
    }

    val isReady: Boolean
        get() = ready

    private fun doInit(appContext: Context) {
        try {
            val deviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
            val codecsJson = HardwareCodecProbe.probeAvcDecodersJson()
            Core.initRuntime(appContext.filesDir.absolutePath, deviceName, codecsJson)
        } catch (err: Throwable) {
            Log.e("RC_DIAG", "initRuntime FAILED", err)
        } finally {
            ready = true
            latch.countDown()
        }
    }
}
