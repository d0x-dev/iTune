package com.darkxvenom.itune.backend

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

class AudioDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AudioDownloader.ensureChannel(applicationContext)
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                val removeNotification = intent.getBooleanExtra(EXTRA_REMOVE_NOTIFICATION, false)
                ServiceCompat.stopForeground(
                    this,
                    if (removeNotification) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH
                )
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val id = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Downloading audio"
                val notificationId = if (id > 0) id else AudioDownloader.DEFAULT_FOREGROUND_ID
                val notification = AudioDownloader.getOrBuildNotification(applicationContext, notificationId, title)
                val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
                ServiceCompat.startForeground(this, notificationId, notification, fgType)
                if (!AudioDownloader.hasActiveDownloads()) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }
    }

    companion object {
        const val ACTION_START_OR_SYNC = "com.darkxvenom.itune.action.START_DOWNLOAD_SERVICE"
        const val ACTION_STOP_SERVICE = "com.darkxvenom.itune.action.STOP_DOWNLOAD_SERVICE"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REMOVE_NOTIFICATION = "extra_remove_notification"
    }
}
