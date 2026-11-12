package com.darkxvenom.itune.recognition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import coil.Coil
import coil.request.ImageRequest
import com.darkxvenom.itune.MainActivity
import com.darkxvenom.itune.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicRecognitionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recognitionJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_RECOGNITION

        when (action) {
            ACTION_STOP -> {
                stopRecognition()
                stopSelf()
            }
            ACTION_START_RECOGNITION -> {
                startForegroundWithListeningNotification()
                performRecognition()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Recognition",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows progress and results when identifying music"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithListeningNotification() {
        isRecognizing = true
        requestTileUpdate(this)

        val cancelIntent = Intent(this, MusicRecognitionService::class.java).apply {
            action = ACTION_STOP
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            101,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = MusicRecognitionTileService.ACTION_START_RECOGNITION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            102,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recognition_mic)
            .setContentTitle("Listening for music…")
            .setContentText("Hold phone near sound source")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(R.drawable.close, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_LISTENING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID_LISTENING, notification)
        }
    }

    private fun updateProgress(title: String, message: String) {
        val cancelIntent = Intent(this, MusicRecognitionService::class.java).apply {
            action = ACTION_STOP
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            101,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recognition_mic)
            .setContentTitle(title)
            .setContentText(message)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .addAction(R.drawable.close, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_LISTENING, notification)
    }

    private fun performRecognition() {
        recognitionJob?.cancel()
        recognitionJob = serviceScope.launch {
            try {
                // 1. Capture microphone audio and generate Shazam signature
                val signature = IRecognitionRepository.captureAndFingerprint(applicationContext)

                // 2. Query Shazam backend
                updateProgress("Finding your song…", "Comparing audio fingerprint…")
                val result = IRecognitionRepository.recognize(signature)

                // 3. Match found!
                MusicRecognitionController.lastResult.value = result
                showSuccessNotification(result)
            } catch (_: CancellationException) {
                // User cancelled
            } catch (_: RecognitionNoMatchException) {
                showNoMatchNotification()
            } catch (_: RecognitionPermissionException) {
                showPermissionRequiredNotification()
            } catch (e: Exception) {
                showErrorNotification(e.message ?: "Unable to recognize audio")
            } finally {
                isRecognizing = false
                requestTileUpdate(applicationContext)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun showSuccessNotification(result: IRecognitionResult) {
        var artworkBitmap: Bitmap? = null
        result.artworkUrl?.let { url ->
            try {
                val imageLoader = Coil.imageLoader(applicationContext)
                val request = ImageRequest.Builder(applicationContext)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val drawable = imageLoader.execute(request).drawable
                artworkBitmap = (drawable as? BitmapDrawable)?.bitmap
            } catch (_: Exception) {}
        }

        val playIntent = Intent(this, MainActivity::class.java).apply {
            action = MusicRecognitionTileService.ACTION_PLAY_RECOGNIZED
            putExtra(MusicRecognitionTileService.EXTRA_SONG_TITLE, result.title)
            putExtra(MusicRecognitionTileService.EXTRA_SONG_ARTIST, result.artist)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val playPendingIntent = PendingIntent.getActivity(
            this,
            201,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = MusicRecognitionTileService.ACTION_START_RECOGNITION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            202,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtitle = buildString {
            append(result.artist)
            result.album?.takeIf { it.isNotBlank() }?.let {
                append("  •  ").append(it)
            }
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recognition_mic)
            .setContentTitle(result.title)
            .setContentText(subtitle)
            .setSubText("Song Identified")
            .apply { artworkBitmap?.let { setLargeIcon(it) } }
            .setContentIntent(playPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_ALL)
            .addAction(R.drawable.play, "Play in iTune", playPendingIntent)
            .addAction(R.drawable.more_horiz, "View Details", openPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_RESULT, notification)
    }

    private fun showNoMatchNotification() {
        val retryIntent = Intent(this, MusicRecognitionService::class.java).apply {
            action = ACTION_START_RECOGNITION
        }
        val retryPendingIntent = PendingIntent.getService(
            this,
            301,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recognition_mic)
            .setContentTitle("No match found")
            .setContentText("Could not identify the song. Tap to try again.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(retryPendingIntent)
            .addAction(R.drawable.replay, "Try Again", retryPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_RESULT, notification)
    }

    private fun showPermissionRequiredNotification() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = MusicRecognitionTileService.ACTION_START_RECOGNITION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            401,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recognition_mic)
            .setContentTitle("Microphone permission needed")
            .setContentText("Tap to allow microphone access so iTune can identify music.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_RESULT, notification)
    }

    private fun showErrorNotification(message: String) {
        val retryIntent = Intent(this, MusicRecognitionService::class.java).apply {
            action = ACTION_START_RECOGNITION
        }
        val retryPendingIntent = PendingIntent.getService(
            this,
            501,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recognition_mic)
            .setContentTitle("Recognition failed")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(retryPendingIntent)
            .addAction(R.drawable.replay, "Retry", retryPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_RESULT, notification)
    }

    private fun stopRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        isRecognizing = false
        requestTileUpdate(this)
    }

    override fun onDestroy() {
        stopRecognition()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "itune_music_recognition"
        const val NOTIFICATION_ID_LISTENING = 4040
        const val NOTIFICATION_ID_RESULT = 4041

        const val ACTION_START_RECOGNITION = "com.darkxvenom.itune.action.SERVICE_START_RECOGNITION"
        const val ACTION_STOP = "com.darkxvenom.itune.action.SERVICE_STOP_RECOGNITION"

        @Volatile
        var isRecognizing: Boolean = false
            private set

        fun requestTileUpdate(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    TileService.requestListeningState(
                        context,
                        ComponentName(context, MusicRecognitionTileService::class.java)
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
