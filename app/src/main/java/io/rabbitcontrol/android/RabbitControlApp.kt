package io.rabbitcontrol.android

import android.app.Application
import android.os.Build
import io.rabbitcontrol.android.core.nativebridge.HardwareCodecProbe
import io.rabbitcontrol.android.di.dataModule
import io.rabbitcontrol.android.di.sessionModule
import io.rabbitcontrol.android.di.viewModelModule
import io.rabbitcontrol.android.session.Core
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RabbitControlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val deviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
        val codecsJson = HardwareCodecProbe.probeAvcDecodersJson()
        runCatching {
            Core.initRuntime(filesDir.absolutePath, deviceName, codecsJson)
        }.onSuccess {
            android.util.Log.i("RC_DIAG", "initRuntime ok: $it")
        }.onFailure {
            android.util.Log.e("RC_DIAG", "initRuntime FAILED", it)
        }
        startKoin {
            androidContext(this@RabbitControlApp)
            modules(dataModule, sessionModule, viewModelModule)
        }
    }
}
