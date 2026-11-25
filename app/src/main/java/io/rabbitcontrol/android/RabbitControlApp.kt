package io.rabbitcontrol.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import io.rabbitcontrol.android.di.dataModule
import io.rabbitcontrol.android.di.sessionModule
import io.rabbitcontrol.android.di.viewModelModule
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RabbitControlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Kick off hardware decoder probing & Rust runtime initialization off the UI thread
        NativeRuntimeBootstrap.startAsync(this)

        startKoin {
            androidContext(this@RabbitControlApp)
            modules(dataModule, sessionModule, viewModelModule)
        }

        registerForegroundTracker()
    }

    private fun registerForegroundTracker() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                private var startedCount = 0

                override fun onActivityStarted(activity: Activity) {
                    val wasBackground = startedCount == 0
                    startedCount += 1
                    if (wasBackground) {
                        runCatching {
                            getKoin().getOrNull<AccountRepository>()?.onAppForegroundChanged(true)
                        }
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                    startedCount = (startedCount - 1).coerceAtLeast(0)
                    if (startedCount == 0 && !activity.isChangingConfigurations) {
                        runCatching {
                            getKoin().getOrNull<AccountRepository>()?.onAppForegroundChanged(false)
                        }
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
