package io.rabbitcontrol.android.core.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.rabbitcontrol.android.R
import io.rabbitcontrol.android.ui.session.SessionActivity
import org.koin.android.ext.android.inject

class SessionForegroundService : Service() {
    private val sessionManager: SessionManager by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                sessionManager.disconnect(lockRemote = false)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "正在远控桌面"
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: "保持高刷连接与工具通道活跃"
        ensureChannel()
        val notification = buildNotification(title, subtitle)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "远程控制会话",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "保持远程桌面、文件传输、终端和端口映射在后台持续运行"
                setShowBadge(false)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, subtitle: String): Notification {
        val openIntent = Intent(this, SessionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val openPi = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = Intent(this, SessionForegroundService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPi = PendingIntent.getService(
            this,
            2,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_remote)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(Notification.Action.Builder(null, "断开连接", disconnectPi).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "rabbitcontrol_session"
        private const val NOTIFICATION_ID = 21118
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val ACTION_DISCONNECT = "io.rabbitcontrol.android.ACTION_DISCONNECT"

        fun startOrUpdate(context: Context, title: String, subtitle: String) {
            val intent = Intent(context, SessionForegroundService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SessionForegroundService::class.java)) }
        }
    }
}
