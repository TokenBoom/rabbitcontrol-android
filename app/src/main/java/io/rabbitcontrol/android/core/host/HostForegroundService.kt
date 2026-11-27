package io.rabbitcontrol.android.core.host

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.rabbitcontrol.android.MainActivity
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.core.i18n.AppLocaleManager
import org.koin.core.context.GlobalContext

class HostForegroundService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            runCatching { GlobalContext.get().get<HostManager>().stopHost() }
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && data != null) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mgr.getMediaProjection(resultCode, data)
            if (proj != null) {
                clearProjection()
                val cb = object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i("RC_DIAG", "MediaProjection stopped by system")
                        runCatching { GlobalContext.get().get<HostManager>().onSystemProjectionStopped() }
                        stopSelf()
                    }
                }
                proj.registerCallback(cb, Handler(Looper.getMainLooper()))
                mediaProjection = proj
                projectionCallback = cb
                runCatching { GlobalContext.get().get<HostManager>().onMediaProjectionReady(proj) }
            } else {
                Log.w("RC_DIAG", "getMediaProjection returned null")
                runCatching {
                    GlobalContext.get().get<HostManager>().onProjectionFailed(
                        getString(R.string.host_err_projection_denied),
                    )
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        clearProjection()
        super.onDestroy()
    }

    private fun clearProjection() {
        val proj = mediaProjection
        val cb = projectionCallback
        mediaProjection = null
        projectionCallback = null
        if (proj != null && cb != null) {
            runCatching { proj.unregisterCallback(cb) }
        }
        if (proj != null) {
            runCatching { proj.stop() }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.host_notif_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            102,
            Intent(this, HostForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.rabbitcontrol)
            .setContentTitle(getString(R.string.host_notif_title))
            .setContentText(getString(R.string.host_notif_text))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.host_notif_stop), stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "rabbitcontrol_host"
        private const val NOTIF_ID = 2002
        const val ACTION_START = "io.rabbitcontrol.android.host.START"
        const val ACTION_STOP = "io.rabbitcontrol.android.host.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, HostForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HostForegroundService::class.java))
        }
    }
}
