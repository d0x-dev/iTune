package com.darkxvenom.itune.data.updates

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.darkxvenom.itune.MainActivity
import com.darkxvenom.itune.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdateDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_DOWNLOAD) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val tag = intent.getStringExtra(EXTRA_TAG).orEmpty()
        if (!url.startsWith("https://github.com/", ignoreCase = true) || tag.isBlank()) {
            fail("Invalid GitHub release APK link", startId)
            return START_NOT_STICKY
        }

        val initialNotification = progressNotification(tag, -1, true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (error: Exception) {
            AppUpdateManager.publishDownloadState(
                UpdateDownloadState.Failed(error.localizedMessage ?: "Could not start the update download"),
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (downloadJob?.isActive == true) return START_NOT_STICKY
        downloadJob = serviceScope.launch {
            try {
                download(url, tag)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = error.localizedMessage ?: "Update download failed"
                AppUpdateManager.publishDownloadState(UpdateDownloadState.Failed(message))
                notificationManager.notify(COMPLETED_NOTIFICATION_ID, finishedNotification("Update download failed", message))
            } finally {
                ServiceCompat.stopForeground(this@AppUpdateDownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun download(url: String, tag: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.android.package-archive, application/octet-stream")
            .header("User-Agent", "iTune-Android/${AppUpdateManager.installedVersion}")
            .get()
            .build()
        val outputDir = File(cacheDir, "updates").apply { mkdirs() }
        val safeTag = tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val apk = File(outputDir, "iTune-$safeTag.apk")
        val temporary = File(outputDir, "iTune-$safeTag.apk.part")
        apk.delete()
        temporary.delete()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub returned HTTP ${response.code}")
            if (!response.request.url.isHttps) throw IOException("APK download redirected to an insecure URL")
            val body = response.body ?: throw IOException("GitHub returned an empty APK response")
            val totalBytes = body.contentLength()
            // Publish the content length as soon as headers arrive; do not leave the
            // dialog looking frozen at 0 B while the body stream is being opened.
            AppUpdateManager.publishDownloadState(UpdateDownloadState.Downloading(-1, 0L, totalBytes))
            notificationManager.notify(
                NOTIFICATION_ID,
                progressNotification(tag, -1, true, 0L, totalBytes),
            )
            var downloadedBytes = 0L
            var lastPercent = -1
            var lastNotifiedBytes = 0L
            var lastNotificationAt = 0L
            body.byteStream().use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        val percent = if (totalBytes > 0L) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else -1
                        AppUpdateManager.publishDownloadState(
                            UpdateDownloadState.Downloading(percent, downloadedBytes, totalBytes),
                        )
                        val now = android.os.SystemClock.elapsedRealtime()
                        val shouldNotify = if (percent >= 0) {
                            percent != lastPercent
                        } else {
                            downloadedBytes - lastNotifiedBytes >= 512L * 1024L || now - lastNotificationAt >= 1_000L
                        }
                        if (shouldNotify) {
                            lastPercent = percent
                            lastNotifiedBytes = downloadedBytes
                            lastNotificationAt = now
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                progressNotification(tag, percent, totalBytes <= 0L, downloadedBytes, totalBytes),
                            )
                        }
                    }
                    output.flush()
                }
            }
            if (downloadedBytes == 0L || (totalBytes > 0L && downloadedBytes != totalBytes)) {
                throw IOException("The APK download was incomplete")
            }
        }

        if (!temporary.renameTo(apk)) throw IOException("Could not save the downloaded APK")
        if (!AppUpdateManager.isSignedLikeInstalledApp(this, apk)) {
            apk.delete()
            throw SecurityException("The downloaded APK does not match this iTune app")
        }

        AppUpdateManager.publishDownloadState(UpdateDownloadState.Completed(apk))
        notificationManager.notify(COMPLETED_NOTIFICATION_ID, readyNotification(tag, apk))
    }

    private fun progressNotification(
        tag: String,
        percent: Int,
        indeterminate: Boolean,
        downloadedBytes: Long = 0L,
        totalBytes: Long = -1L,
    ): Notification {
        val progressText = if (downloadedBytes == 0L) {
            if (totalBytes > 0L) "Connected · preparing download" else "Connecting to GitHub…"
        } else if (totalBytes > 0L) {
            "$percent% · ${formatBytes(downloadedBytes)} of ${formatBytes(totalBytes)}"
        } else {
            "${formatBytes(downloadedBytes)} downloaded"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading iTune $tag")
            .setContentText(progressText)
            .setProgress(100, percent.coerceAtLeast(0), indeterminate)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppPendingIntent())
            .build()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024f)
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
        else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
    }

    private fun readyNotification(tag: String, apk: File): Notification {
        val installIntent = Intent(this, com.darkxvenom.itune.ui.update.UpdateInstallActivity::class.java).apply {
            putExtra(com.darkxvenom.itune.ui.update.UpdateInstallActivity.EXTRA_APK_PATH, apk.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val installPendingIntent = PendingIntent.getActivity(
            this,
            INSTALL_PENDING_INTENT,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("iTune $tag is ready")
            .setContentText("Tap Install to continue")
            .setProgress(0, 0, false)
            .setContentIntent(installPendingIntent)
            .addAction(android.R.drawable.stat_sys_download_done, "Install", installPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun finishedNotification(title: String, message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            OPEN_APP_PENDING_INTENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Live progress and install status for iTune updates"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    private fun fail(message: String, startId: Int) {
        AppUpdateManager.publishDownloadState(UpdateDownloadState.Failed(message))
        stopSelf(startId)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.darkxvenom.itune.action.DOWNLOAD_UPDATE"
        const val EXTRA_URL = "update_download_url"
        const val EXTRA_TAG = "update_release_tag"
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 6_204
        private const val COMPLETED_NOTIFICATION_ID = 6_205
        private const val INSTALL_PENDING_INTENT = 6_205
        private const val OPEN_APP_PENDING_INTENT = 6_206
    }
}
