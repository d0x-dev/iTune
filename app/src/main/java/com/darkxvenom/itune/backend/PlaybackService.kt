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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.darkxvenom.itune.island.DynamicIslandOverlay
import com.darkxvenom.itune.island.DynamicIslandStore
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
        val liked: Boolean = false,
    ) {
        val radioMode: Boolean get() = radioPlaying || (track == null && station != null)
        val hasContent: Boolean get() = track != null || station != null
        val isPlaying: Boolean get() = if (radioMode) radioPlaying else playing
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var lastSnap: Snap? = null
    private var island: DynamicIslandOverlay? = null
    private var foreground = false
    private var commandReceived = false

    private val islandMonitor = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
        DynamicIslandStore.refreshOverlayPermission(this@PlaybackService)
        DynamicIslandStore.overlayAllowed.collect { allowed ->
            island?.close()
            island = if (allowed) DynamicIslandOverlay(this@PlaybackService).also { it.attach() } else null
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopping = false

    // Artwork is keyed by track identity so state changes never restart / cancel a running load.
    private var artworkIdentity: String? = null
    private var artworkBitmap: Bitmap? = null
    private var artworkJob: Job? = null
    private var metadataKey: String? = null

    private val idleDetach = Runnable {
        // Paused for a long time: let the notification be swiped away while keeping it visible.
        if (foreground && lastSnap?.isPlaying == false) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            foreground = false
        }
    }
    private val stopIfEmpty = Runnable {
        val snap = currentSnap()
        if (!snap.hasContent) shutDown()
    }

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
                    liked = MusicPlayerState.currentTrack?.let { MusicPlayerState.isFavorite(it.id) } == true,
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
            // Notification buttons can arrive at a freshly (re)created service: re-arm it first,
            // otherwise state changes would be ignored and the notification would go stale.
            commandReceived = true
            stopping = false
            handleAction(action)
            refresh(currentSnap(), forceForeground = false)
            if (!stopping) islandMonitor.start()
            return START_NOT_STICKY
        }
        stopping = false
        // Started by PlaybackService.sync(): must call startForeground promptly
        commandReceived = true
        refresh(currentSnap(), forceForeground = true)
        if (!stopping) islandMonitor.start()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val stopOnTaskClear = SettingsStore.isStopMusicOnTaskClearEnabled(applicationContext)
        if (stopOnTaskClear) {
            MusicPlayerState.savePlaybackSession(sync = true)
            MusicPlayerState.pause()
            OnlineRadioController.pauseIfPlaying()
            shutDown()
        } else if (!MusicPlayerState.isPlaying) {
            island?.dismissUntilPlayback()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        island?.configurationChanged()
    }

    override fun onDestroy() {
        island?.close()
        island = null
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        runCatching { session?.isActive = false }
        runCatching { session?.release() }
        session = null
        // Only remove the notification on an intentional stop. If the system merely reclaims an
        // idle service the paused notification must stay so the user can resume from it.
        if (stopping) getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun shutDown() {
        stopping = true
        mainHandler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        stopSelf()
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
            ACTION_STAR -> if (!radio) MusicPlayerState.currentTrack?.let { MusicPlayerState.toggleFavorite(it) }
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
            liked = MusicPlayerState.currentTrack?.let { MusicPlayerState.isFavorite(it.id) } == true,
        )
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    private fun refresh(snap: Snap, forceForeground: Boolean) {
        lastSnap = snap
        if (!snap.hasContent) {
            if (forceForeground && !foreground) postForeground(buildNotification(snap))
            // Debounced: the track briefly becomes null while the next song is being resolved,
            // which previously tore the notification down mid-playback.
            mainHandler.removeCallbacks(stopIfEmpty)
            mainHandler.postDelayed(stopIfEmpty, EMPTY_STOP_DELAY_MS)
            return
        }
        mainHandler.removeCallbacks(stopIfEmpty)
        updateMetadata(snap)
        updatePlaybackState(snap)
        val notif = buildNotification(snap)
        val nm = getSystemService(NotificationManager::class.java)
        if (snap.isPlaying) {
            mainHandler.removeCallbacks(idleDetach)
            postForeground(notif)
        } else if (foreground) {
            // Stay a foreground service while paused so the system can't reap it (and drop the
            // controls); release it to a swipeable notification after a long idle period.
            nm?.notify(NOTIFICATION_ID, notif)
            mainHandler.removeCallbacks(idleDetach)
            mainHandler.postDelayed(idleDetach, IDLE_DETACH_MS)
        } else if (forceForeground) {
            postForeground(notif)
            mainHandler.removeCallbacks(idleDetach)
            mainHandler.postDelayed(idleDetach, IDLE_DETACH_MS)
        } else {
            nm?.notify(NOTIFICATION_ID, notif)
        }
    }

    private fun postForeground(notif: Notification) {
        runCatching {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, type)
            foreground = true
        }.onFailure {
            // Still show the notification even if promotion to foreground was refused.
            android.util.Log.w("PlaybackService", "startForeground failed", it)
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notif)
        }
    }

    private fun updateMetadata(snap: Snap) {
        val s = session ?: return
        val title: String
        val artist: String
        val album: String?
        val duration: Long
        val identity: String
        val candidates: List<String>
        val key: String
        if (snap.radioMode) {
            val st = snap.station ?: return
            title = st.name; artist = "Online Radio"; album = null; duration = -1L
            identity = "radio:${st.uuid}"
            candidates = listOfNotNull(st.faviconUrl?.takeIf { it.isNotBlank() })
            key = identity
        } else {
            val t = snap.track ?: return
            title = t.title; artist = t.artists; album = t.album; duration = snap.durationMs
            identity = "track:${t.id}"
            candidates = artworkCandidates(t)
            key = "$identity:$duration"
        }
        if (artworkIdentity != identity) {
            artworkIdentity = identity
            artworkBitmap = null
            artworkJob?.cancel()
            if (candidates.isNotEmpty()) loadArtwork(identity, candidates)
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
                .apply {
                    artworkBitmap?.let {
                        putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                        putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
                    }
                }
                .build(),
        )
    }

    /**
     * Ordered artwork URLs to try. The in-app "max quality" rewrite points at `maxresdefault.jpg`,
     * which does not exist for most videos (404) — that is why many songs had no thumbnail here.
     */
    private fun artworkCandidates(t: MusicTrack): List<String> {
        val out = LinkedHashSet<String>()
        val raw = t.thumbnailUrl?.takeIf { it.isNotBlank() }
        if (raw != null) {
            val hq = runCatching { raw.highQualityThumbnail() }.getOrNull()
            // Prefer the URL the app itself already displays, then the upscaled variant.
            out += raw
            if (hq != null && !hq.contains("maxresdefault")) out += hq
        }
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(t.id)) {
            out += "https://i.ytimg.com/vi/${t.id}/hq720.jpg"
            out += "https://i.ytimg.com/vi/${t.id}/hqdefault.jpg"
            out += "https://i.ytimg.com/vi/${t.id}/mqdefault.jpg"
        }
        return out.toList()
    }

    private fun loadArtwork(identity: String, urls: List<String>) {
        artworkJob = scope.launch {
            val loader = Coil.imageLoader(this@PlaybackService)
            for (url in urls) {
                val res = runCatching {
                    loader.execute(
                        ImageRequest.Builder(this@PlaybackService)
                            .data(url)
                            .allowHardware(false)
                            .size(512)
                            .build(),
                    )
                }.getOrNull()
                if (res is SuccessResult) {
                    val bmp = runCatching {
                        (res.drawable as? BitmapDrawable)?.bitmap ?: res.drawable.toBitmap()
                    }.getOrNull() ?: continue
                    // Ignore YouTube's 120x90 grey "not found" placeholder.
                    if (bmp.width <= 120 && bmp.height <= 90 && url.contains("ytimg.com")) continue
                    if (artworkIdentity == identity) {
                        artworkBitmap = bmp
                        lastSnap?.let { refresh(it, forceForeground = false) }
                    }
                    return@launch
                }
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
                PlaybackState.CustomAction.Builder(ACTION_STAR, if (snap.liked) "Unstar" else "Star", starIcon(snap.liked)).build(),
            )
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

    private fun starIcon(on: Boolean) = if (on) R.drawable.ic_star_filled else R.drawable.ic_star_outline

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
        builder.setWhen(0L)
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
            builder.addAction(action(starIcon(snap.liked), if (snap.liked) "Unstar" else "Star", ACTION_STAR, 4))
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
        private const val ACTION_STAR = "com.darkxvenom.itune.action.STAR"
        private const val EMPTY_STOP_DELAY_MS = 3_000L
        private const val IDLE_DETACH_MS = 10 * 60_000L

        /**
         * Starts the service while music or radio plays. The service keeps itself alive (paused
         * notification stays so playback can be resumed) and stops itself once nothing is loaded.
         */
        /** Starts the service for a requested song; it shows controls immediately, even while buffering. */
        fun start(context: Context) {
            val ctx = context.applicationContext
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, PlaybackService::class.java))
            }
        }

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
