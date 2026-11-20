/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.backend

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat

class AudioDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iTune:AudioDownloadWakeLock")?.apply {
                setReferenceCounted(false)
            }
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "iTune:AudioDownloadWifiLock")?.apply {
                setReferenceCounted(false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AudioDownloader.ensureChannel(applicationContext)
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                releaseLocks()
                val removeNotification = intent.getBooleanExtra(EXTRA_REMOVE_NOTIFICATION, false)
                ServiceCompat.stopForeground(
                    this,
                    if (removeNotification) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH
                )
                if (removeNotification) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(AudioDownloader.DEFAULT_FOREGROUND_ID)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                acquireLocks()
                val notification = AudioDownloader.getMasterNotification(applicationContext)
                val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
                ServiceCompat.startForeground(this, AudioDownloader.DEFAULT_FOREGROUND_ID, notification, fgType)
                if (!AudioDownloader.hasActiveOrPendingDownloads()) {
                    releaseLocks()
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.cancel(AudioDownloader.DEFAULT_FOREGROUND_ID)
                    stopSelf()
                    return START_NOT_STICKY
                }
                return START_STICKY
            }
        }
    }

    private fun acquireLocks() {
        runCatching {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours safety
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
    }

    override fun onDestroy() {
        releaseLocks()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(AudioDownloader.DEFAULT_FOREGROUND_ID)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_OR_SYNC = "com.darkxvenom.itune.action.START_DOWNLOAD_SERVICE"
        const val ACTION_STOP_SERVICE = "com.darkxvenom.itune.action.STOP_DOWNLOAD_SERVICE"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_REMOVE_NOTIFICATION = "extra_remove_notification"
    }
}
