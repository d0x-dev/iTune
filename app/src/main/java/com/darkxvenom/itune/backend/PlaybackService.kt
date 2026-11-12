package com.darkxvenom.itune.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.snapshotFlow
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.darkxvenom.itune.MainActivity
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.radio.OnlineRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foreground media-playback service.
 *
 * - Keeps the process (and the singleton players) alive so playback continues after the app is
 *   swiped away from recents. If "Stop Music on Task Clear" is enabled, [onTaskRemoved] pauses
 *   playback and stops the service.
 * - Publishes the standard Android media notification (MediaStyle + MediaSession) with
 *   shuffle, previous, play/pause, next and repeat, using the same icons as the in-app player.
 *   The same actions are exposed to the lock screen / Android 13+ media controls and Bluetooth.
 */
class PlaybackService : Service() {

    private data class Snap(
        val track: MusicTrack?,
        val playing: Boolean,
        val shuffle: Boolean,
        val repeat: Int,
        val durationMs: Long,
        val station: OnlineRadioStation?,
        val radioPlaying: Boolean,
    ) {
        val radioMode: Boolean get() = radioPlaying || (track == null && station != null)
        val hasContent: Boolean get() = track != null || station != null
        val isPlaying: Boolean get() = if (radioMode) radioPlaying else playing
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var lastSnap: Snap? = null
    private var foreground = false
    private var commandReceived = false

    private var artworkUrl: String? = null
    private var artworkBitmap: Bitmap? = null
    private var artworkJob: Job? = null
    private var metadataKey: String? = null

    private var stateAnchorPos = 0L
    private var stateAnchorRealtime = 0L
    private var stateAnchorPlaying = false

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        session = MediaSession(this, "iTunePlayback").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(sessionCallback, Handler(Looper.getMainLooper()))
            isActive = true
        }

        scope.launch {
            val musicFlow = snapshotFlow {
                Snap(
                    track = MusicPlayerState.currentTrack,
                    playing = MusicPlayerState.isPlaying,
                    shuffle = MusicPlayerState.shuffleEnabled,
                    repeat = MusicPlayerState.repeatMode,
                    durationMs = MusicPlayerState.durationMs,
                    station = null,
                    radioPlaying = false,
                )
            }
            val radioFlow = OnlineRadioController.state
                .map { it.currentStation to it.isPlaying }
                .distinctUntilChanged()
            combine(musicFlow, radioFlow) { m, r -> m.copy(station = r.first, radioPlaying = r.second) }
                .distinctUntilChanged()
                .collect { if (commandReceived) refresh(it, forceForeground = false) }
        }

        // Keep lock-screen / shade seek bar in sync when the user seeks inside the app
        scope.launch {
            snapshotFlow { MusicPlayerState.positionMs }.collect { pos ->
                val snap = lastSnap ?: return@collect
                if (snap.radioMode) return@collect
                val expected = if (stateAnchorPlaying) {
                    stateAnchorPos + (SystemClock.elapsedRealtime() - stateAnchorRealtime)
                } else stateAnchorPos
                if (abs(pos - expected) > 2_000L) updatePlaybackState(snap)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            handleAction(action)
            return START_NOT_STICKY
        }
        // Started by PlaybackService.sync(): must call startForeground promptly
        commandReceived = true
        refresh(currentSnap(), forceForeground = true)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (SettingsStore.isStopMusicOnTaskClearEnabled(applicationContext)) {
            scope.cancel()
            MusicPlayerState.savePlaybackSession(sync = true)
            MusicPlayerState.pause()
            OnlineRadioController.pauseIfPlaying()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { session?.isActive = false }
        runCatching { session?.release() }
        session = null
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    // ── Controls ──────────────────────────────────────────────────────────────────────────────

    private val sessionCallback = object : MediaSession.Callback() {
        override fun onPlay() = handleAction(ACTION_PLAY)
        override fun onPause() = handleAction(ACTION_PAUSE)
        override fun onSkipToNext() = handleAction(ACTION_NEXT)
        override fun onSkipToPrevious() = handleAction(ACTION_PREVIOUS)
        override fun onStop() = handleAction(ACTION_PAUSE)
        override fun onSeekTo(pos: Long) {
            if (lastSnap?.radioMode == false) MusicPlayerState.seekTo(pos)
        }
        override fun onCustomAction(action: String, extras: android.os.Bundle?) = handleAction(action)
    }

    private fun handleAction(action: String) {
        val radio = lastSnap?.radioMode ?: currentSnap().radioMode
        when (action) {
            ACTION_TOGGLE -> if (radio) OnlineRadioController.togglePlayPause() else MusicPlayerState.togglePlayPause()
            ACTION_PLAY -> {
                val playing = if (radio) OnlineRadioController.state.value.isPlaying else MusicPlayerState.isPlaying
                if (!playing) handleAction(ACTION_TOGGLE)
            }
            ACTION_PAUSE -> {
                val playing = if (radio) OnlineRadioController.state.value.isPlaying else MusicPlayerState.isPlaying
                if (playing) handleAction(ACTION_TOGGLE)
            }
            ACTION_NEXT -> if (radio) OnlineRadioController.skipNextStation() else MusicPlayerState.skipNext()
            ACTION_PREVIOUS -> if (radio) OnlineRadioController.skipPreviousStation() else MusicPlayerState.skipPrevious()
            ACTION_SHUFFLE -> if (!radio) MusicPlayerState.toggleShuffle()
            ACTION_REPEAT -> if (!radio) MusicPlayerState.toggleRepeat()
        }
    }

    // ── State → session + notification ────────────────────────────────────────────────────────

    private fun currentSnap(): Snap {
        val radio = OnlineRadioController.state.value
        return Snap(
            track = MusicPlayerState.currentTrack,
            playing = MusicPlayerState.isPlaying,
            shuffle = MusicPlayerState.shuffleEnabled,
            repeat = MusicPlayerState.repeatMode,
            durationMs = MusicPlayerState.durationMs,
            station = radio.currentStation,
            radioPlaying = radio.isPlaying,
        )
    }

    private fun refresh(snap: Snap, forceForeground: Boolean) {
        lastSnap = snap
        if (!snap.hasContent) {
            if (forceForeground) postForeground(buildNotification(snap))
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
            return
        }
        updateMetadata(snap)
        updatePlaybackState(snap)
        val notif = buildNotification(snap)
        if (snap.isPlaying || (forceForeground && !foreground)) {
            postForeground(notif)
        } else {
            if (foreground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                foreground = false
            }
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notif)
        }
    }

    private fun postForeground(notif: Notification) {
        runCatching {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, type)
            foreground = true
        }
    }

    private fun updateMetadata(snap: Snap) {
        val s = session ?: return
        val title: String
        val artist: String
        val album: String?
        val duration: Long
        val art: String?
        val key: String
        if (snap.radioMode) {
            val st = snap.station ?: return
            title = st.name; artist = "Online Radio"; album = null; duration = -1L
            art = st.faviconUrl
            key = "radio:${st.uuid}"
        } else {
            val t = snap.track ?: return
            title = t.title; artist = t.artists; album = t.album; duration = snap.durationMs
            art = t.thumbnailUrl?.highQualityThumbnail() ?: t.thumbnailUrl
            key = "track:${t.id}:$duration"
        }
        if (artworkUrl != art) {
            artworkBitmap = null
            artworkUrl = null
            if (!art.isNullOrBlank()) loadArtwork(art)
        }
        val fullKey = "$key:${artworkBitmap != null}"
        if (fullKey == metadataKey) return
        metadataKey = fullKey
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
                .apply { artworkBitmap?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
    }

    private fun loadArtwork(url: String) {
        artworkJob?.cancel()
        artworkJob = scope.launch {
            val req = ImageRequest.Builder(this@PlaybackService)
                .data(url)
                .allowHardware(false)
                .size(512)
                .build()
            val res = Coil.imageLoader(this@PlaybackService).execute(req)
            if (res is SuccessResult) {
                val bmp = (res.drawable as? BitmapDrawable)?.bitmap ?: res.drawable.toBitmap()
                artworkBitmap = bmp
                artworkUrl = url
                lastSnap?.let { refresh(it, forceForeground = false) }
            }
        }
    }

    private fun updatePlaybackState(snap: Snap) {
        val s = session ?: return
        val pos = if (snap.radioMode) PlaybackState.PLAYBACK_POSITION_UNKNOWN else MusicPlayerState.positionMs
        val playing = snap.isPlaying
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
        if (!snap.radioMode) actions = actions or PlaybackState.ACTION_SEEK_TO
        val b = PlaybackState.Builder()
            .setActions(actions)
            .setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                pos,
                if (playing) 1f else 0f,
                SystemClock.elapsedRealtime(),
            )
        if (!snap.radioMode) {
            b.addCustomAction(
                PlaybackState.CustomAction.Builder(ACTION_SHUFFLE, "Shuffle", shuffleIcon(snap.shuffle)).build(),
            )
            b.addCustomAction(
                PlaybackState.CustomAction.Builder(ACTION_REPEAT, "Repeat", repeatIcon(snap.repeat)).build(),
            )
        }
        s.setPlaybackState(b.build())
        stateAnchorPos = if (pos >= 0) pos else 0L
        stateAnchorRealtime = SystemClock.elapsedRealtime()
        stateAnchorPlaying = playing
    }

    private fun shuffleIcon(on: Boolean) = if (on) R.drawable.shuffle_on else R.drawable.shuffle

    private fun repeatIcon(mode: Int) = when (mode) {
        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
        Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
        else -> R.drawable.repeat
    }

    private fun actionIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(iconRes: Int, title: String, act: String, code: Int): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, iconRes), title, actionIntent(act, code)).build()

    @Suppress("DEPRECATION")
    private fun buildNotification(snap: Snap): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val title: String
        val text: String
        if (snap.radioMode) {
            title = snap.station?.name ?: "iTune Radio"
            text = "Online Radio"
        } else {
            title = snap.track?.title ?: "iTune"
            text = snap.track?.artists ?: ""
        }
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 9201, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder.setSmallIcon(R.drawable.ic_itune_waves)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPi)
            .setOngoing(snap.isPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        artworkBitmap?.let { builder.setLargeIcon(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        val style = Notification.MediaStyle()
        session?.let { style.setMediaSession(it.sessionToken) }
        val playPauseIcon = if (snap.isPlaying) R.drawable.pause else R.drawable.play
        val playPauseTitle = if (snap.isPlaying) "Pause" else "Play"
        if (snap.radioMode) {
            builder.addAction(action(R.drawable.skip_previous, "Previous", ACTION_PREVIOUS, 1))
            builder.addAction(action(playPauseIcon, playPauseTitle, ACTION_TOGGLE, 2))
            builder.addAction(action(R.drawable.skip_next, "Next", ACTION_NEXT, 3))
            style.setShowActionsInCompactView(0, 1, 2)
        } else {
            builder.addAction(action(shuffleIcon(snap.shuffle), "Shuffle", ACTION_SHUFFLE, 0))
            builder.addAction(action(R.drawable.skip_previous, "Previous", ACTION_PREVIOUS, 1))
            builder.addAction(action(playPauseIcon, playPauseTitle, ACTION_TOGGLE, 2))
            builder.addAction(action(R.drawable.skip_next, "Next", ACTION_NEXT, 3))
            builder.addAction(action(repeatIcon(snap.repeat), "Repeat", ACTION_REPEAT, 4))
            style.setShowActionsInCompactView(1, 2, 3)
        }
        builder.setStyle(style)
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Now Playing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Playback controls for the song that is playing"
                    setShowBadge(false)
                },
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "itune_playback"
        private const val NOTIFICATION_ID = 9201

        private const val ACTION_TOGGLE = "com.darkxvenom.itune.action.TOGGLE"
        private const val ACTION_PLAY = "com.darkxvenom.itune.action.PLAY"
        private const val ACTION_PAUSE = "com.darkxvenom.itune.action.PAUSE"
        private const val ACTION_NEXT = "com.darkxvenom.itune.action.NEXT"
        private const val ACTION_PREVIOUS = "com.darkxvenom.itune.action.PREVIOUS"
        private const val ACTION_SHUFFLE = "com.darkxvenom.itune.action.SHUFFLE"
        private const val ACTION_REPEAT = "com.darkxvenom.itune.action.REPEAT"

        /**
         * Starts the service while music or radio plays. The service keeps itself alive (paused
         * notification stays so playback can be resumed) and stops itself once nothing is loaded.
         */
        fun sync(context: Context) {
            val ctx = context.applicationContext
            val active = MusicPlayerState.isPlaying || OnlineRadioController.state.value.isPlaying
            if (!active) return
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
            }
        }
    }
}
