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


import com.darkxvenom.itune.diagnostics.LiveLogs
import com.darkxvenom.itune.R
import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.Coil
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.darkxvenom.itune.audio.AudioEngineManager
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.audio.SimultaneousAudioProcessor
import com.darkxvenom.itune.backend.lyrics.LyricsHelper
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.auth.YouTubePlaybackTracker
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.ishare.IShareSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CACHE_FROM_BEGINNING_TOLERANCE_MS = 1_000L

/**
 * Dedicated music playback controller and state machine for iTune.
 * Manages ExoPlayer lifecycle, stream extraction, background progress, queue, and lyrics.
 */
object MusicPlayerState : Player.Listener {
    private const val TAG = "MusicPlayerState"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null
    private var simultaneousProcessor: SimultaneousAudioProcessor? = null
    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepTimerJob: Job? = null
    var resolvingStream by mutableStateOf(false)
        private set
    private var restoredPendingStream = false
    private var lastPersistedAtMs = 0L
    private var isApplyingRemoteSync = false
    private var lastPlaybackFailureTrackId: String? = null
    private var lastPlaybackFailureAtMs: Long = 0L
    /** Track must begin near zero and reach natural completion before auto-cache can start. */
    private var cacheOnNaturalCompletionTrackId: String? = null
    private val autoSkipFailedTrackIds = mutableSetOf<String>()
    private var streamRetryAttemptCount = 0
    private var lastFailedStreamUrl: String? = null

    // ── Observable UI States ──
    var currentTrack by mutableStateOf<MusicTrack?>(null)
        private set

    val queue = mutableStateListOf<MusicTrack>()

    var queueIndex by mutableIntStateOf(0)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var playbackState by mutableIntStateOf(Player.STATE_IDLE)
        private set

    var positionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var volume by mutableFloatStateOf(1f)
        private set

    var shuffleEnabled by mutableStateOf(false)
        private set

    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    var endlessQueueEnabled by mutableStateOf(false)
        private set

    private var endlessQueueJob: Job? = null
    private var endlessQueueRequestedForTrackId: String? = null

    var playbackSpeed by mutableFloatStateOf(1f)
        private set

    var holdingFast by mutableStateOf(false)
        private set

    private var preHoldSpeed: Float = 1f

    // Sheet / Fullscreen Dialog State
    var expanded by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var lyricsOpen by mutableStateOf(false)

    // Sleep Timer & Navigation Intents
    var sleepTimerRemainingMs by mutableLongStateOf(0L)
        private set
    var sleepTimerEndOfTrack by mutableStateOf(false)
        private set
    var pendingArtistSearch by mutableStateOf<String?>(null)
    var pendingArtistNav by mutableStateOf<PendingArtistNav?>(null)

    var lyrics by mutableStateOf<List<LyricsLine>>(emptyList())
        private set

    var rawLyricsText by mutableStateOf("")
        private set

    var lyricsOffsetMs by mutableLongStateOf(0L)
        private set

    var hasCustomLyricsOverride by mutableStateOf(false)
        private set

    var lyricsLoading by mutableStateOf(false)
        private set

    private val LYRICS_OVERRIDE_PREFS = "itune_lyrics_overrides"

    val favoriteIds = mutableStateListOf<String>()

    fun isFavorite(trackId: String): Boolean = favoriteIds.contains(trackId)

    fun toggleFavorite(track: MusicTrack) {
        val ctx = appContext
        val nowLiked: Boolean
        if (favoriteIds.contains(track.id)) {
            favoriteIds.remove(track.id)
            ctx?.let { LibraryStore.removeLikedSong(it, track.id) }
            nowLiked = false
        } else {
            favoriteIds.add(track.id)
            ctx?.let {
                LibraryStore.saveLikedSong(it, track, savedLocally = SettingsStore.state.value.addFavoriteSongs, isYtmSynced = true)
                if (SettingsStore.state.value.automaticDownloads) enqueueAutomaticDownload(track)
            }
            nowLiked = true
        }
        YouTubeLibrarySyncManager.pushSongLikeAsync(track.id, nowLiked)
        ctx?.getSharedPreferences("itune_favorites", Context.MODE_PRIVATE)
            ?.edit()
            ?.putStringSet("favorite_track_ids", favoriteIds.toSet())
            ?.apply()
    }

    private fun enqueueAutomaticDownload(track: MusicTrack) {
        scope.launch(Dispatchers.IO) {
            val context = appContext ?: return@launch
            while (SettingsStore.state.value.automaticDownloads && !MusicCacheManager.isTrackCached(track.id)) {
                if (!SettingsStore.state.value.downloadOverWifiOnly || isWifiConnected(context)) {
                    MusicCacheManager.cacheTrackOnDemand(track, force = true)
                    return@launch
                }
                delay(15_000)
            }
        }
    }

    fun enqueueAutoDownloadForSettings(track: MusicTrack) = enqueueAutomaticDownload(track)

    private fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    fun setPcmOutputFormat(format: String) {
        val context = appContext ?: return
        val normalized = if (format == "32-bit float PCM") format else "16-bit PCM"
        if (SettingsStore.state.value.pcmOutputFormat == normalized) return
        SettingsStore.setString(context, SettingsStore.KEY_PCM_OUTPUT_FORMAT, normalized)

        val oldPlayer = exoPlayer ?: return
        val position = oldPlayer.currentPosition.coerceAtLeast(0L)
        val wasPlaying = oldPlayer.isPlaying || oldPlayer.playWhenReady
        val wasExpanded = expanded
        val queueSnapshot = queue.toList()
        val track = currentTrack
        oldPlayer.removeListener(this)
        oldPlayer.release()
        exoPlayer = buildExoPlayer(context, simultaneousProcessor ?: SimultaneousAudioProcessor(context))
        exoPlayer?.shuffleModeEnabled = shuffleEnabled
        exoPlayer?.repeatMode = repeatMode
        if (track != null) {
            play(track, newQueue = queueSnapshot, startPositionMs = position, expandPlayer = wasExpanded, autoPlay = wasPlaying)
        }
    }

    fun toggleFavorite(trackId: String) {
        val known = currentTrack?.takeIf { it.id == trackId }
            ?: queue.firstOrNull { it.id == trackId }
        if (known != null) {
            toggleFavorite(known)
            return
        }
        val ctx = appContext
        val nowLiked: Boolean
        if (favoriteIds.contains(trackId)) {
            favoriteIds.remove(trackId)
            ctx?.let { LibraryStore.removeLikedSong(it, trackId) }
            nowLiked = false
        } else {
            favoriteIds.add(trackId)
            ctx?.let {
                LibraryStore.syncLegacyIds(
                    context = it,
                    favoriteTrackIds = listOf(trackId),
                    subscribedArtistIds = emptyList(),
                    savedReleaseIds = emptyList(),
                )
            }
            nowLiked = true
        }
        YouTubeLibrarySyncManager.pushSongLikeAsync(trackId, nowLiked)
        ctx?.getSharedPreferences("itune_favorites", Context.MODE_PRIVATE)
            ?.edit()
            ?.putStringSet("favorite_track_ids", favoriteIds.toSet())
            ?.apply()
    }

    fun replaceFavoriteIdsFromSync(context: Context, ids: Set<String>) {
        mainHandler.post {
            favoriteIds.clear()
            favoriteIds.addAll(ids)
            context.applicationContext
                .getSharedPreferences("itune_favorites", Context.MODE_PRIVATE)
                .edit()
                .putStringSet("favorite_track_ids", ids)
                .apply()
        }
    }

    @OptIn(UnstableApi::class)
    fun init(context: Context) {
        if (exoPlayer != null) return
        val appContext = context.applicationContext
        this.appContext = appContext
        com.darkxvenom.itune.data.discord.DiscordPresenceManager.initialize(appContext)
        SettingsStore.ensureInit(appContext)
        com.darkxvenom.itune.data.remote.RemoteLibraryStore.ensureInit(appContext)
        MusicBackend.init(appContext)
        LibraryStore.ensureInit(appContext)
        com.darkxvenom.itune.data.library.ListeningProfileStore.ensureInit(appContext)
        com.darkxvenom.itune.data.timetravel.TimeTravelRepository.ensureInit(appContext)
        ArtistStateStore.ensureInit(appContext)

        val prefs = appContext.getSharedPreferences("itune_favorites", Context.MODE_PRIVATE)
        val saved = prefs.getStringSet("favorite_track_ids", emptySet()) ?: emptySet()
        favoriteIds.clear()
        favoriteIds.addAll(saved)
        LibraryStore.syncLegacyIds(
            context = appContext,
            favoriteTrackIds = saved,
            subscribedArtistIds = ArtistStateStore.subscribedArtists.value,
            savedReleaseIds = ArtistStateStore.savedReleases.value,
        )

        // Configure app-wide Coil ImageLoader using Net.client with smart 404 retries
        try {
            val customLoader = ImageLoader.Builder(appContext)
                .okHttpClient { Net.client }
                .crossfade(true)
                .build()
            Coil.setImageLoader(customLoader)
        } catch (_: Exception) {}

        val dualProc = SimultaneousAudioProcessor(appContext)
        simultaneousProcessor = dualProc
        exoPlayer = buildExoPlayer(appContext, dualProc)

        TransitionEngine.init(appContext)
        AudioOutputManager.init(appContext, dualProc) { targetDevice ->
            TransitionEngine.onPreferredDevice(targetDevice)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                exoPlayer?.setPreferredAudioDevice(targetDevice)
            }
        }
        AudioEngineManager.init(
            context = appContext,
            getSessionId = { exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET },
            applyPlaybackParams = { params ->
                if (!holdingFast) {
                    exoPlayer?.playbackParameters = params
                }
            },
        )

        restorePlaybackSession(appContext)
        com.darkxvenom.itune.widget.ITuneWidgetUpdater.ensureObserving(appContext)
        startProgressLoop()
        IShareSyncManager.start(appContext)
        MusicCacheManager.init(appContext)
        if (SettingsStore.state.value.automaticDownloads) {
            LibraryStore.likedSongs.value.forEach { enqueueAutomaticDownload(it.toMusicTrack()) }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildExoPlayer(context: Context, dualProc: SimultaneousAudioProcessor): ExoPlayer {
        val httpDataSourceFactory = OkHttpDataSource.Factory(Net.client).setUserAgent(Net.USER_AGENT)
        val dataSourceFactory = SmbDataSource.RoutingFactory(DefaultDataSource.Factory(context, httpDataSourceFactory))
        val floatOutput = SettingsStore.state.value.pcmOutputFormat == "32-bit float PCM"
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(floatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(
                    arrayOf(
                        TransitionEngine.probe,
                        AudioEngineManager.djProcessor,
                        AudioEngineManager.spatialProcessor,
                        AudioEngineManager.eightDProcessor,
                        AudioEngineManager.lowPassProcessor,
                        dualProc,
                    ),
                )
                .build()
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLooper(Looper.getMainLooper())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(this@MusicPlayerState)
                volume = this@MusicPlayerState.volume
                val preferredId = AudioOutputManager.state.value.preferredDeviceId
                val preferred = AudioOutputManager.state.value.devices.firstOrNull { it.id == preferredId }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && preferred != null) {
                    setPreferredAudioDevice(preferred.info)
                }
            }
    }

    private fun prewarmTrackThumbnail(ctx: Context, track: MusicTrack) {
        val thumb = track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl
        if (!thumb.isNullOrBlank()) {
            val preReq = ImageRequest.Builder(ctx)
                .data(thumb)
                .size(Size.ORIGINAL)
                .crossfade(200)
                .build()
            ctx.imageLoader.enqueue(preReq)
        }
    }

    private fun restorePlaybackSession(ctx: Context) {
        val snapshot = PlaybackSessionStore.load(ctx) ?: return
        loadJob?.cancel()
        resolvingStream = false
        restoredPendingStream = true
        currentTrack = snapshot.track
        queue.clear()
        queue.addAll(snapshot.queue)
        queueIndex = snapshot.queueIndex
        shuffleEnabled = snapshot.shuffleEnabled
        repeatMode = snapshot.repeatMode
        positionMs = snapshot.positionMs
        durationMs = if (snapshot.durationMs > 0L) {
            snapshot.durationMs
        } else {
            (snapshot.track.durationSec * 1000L).coerceAtLeast(0L)
        }
        isPlaying = false
        isLoading = false
        expanded = false
        queueOpen = false
        lyricsOpen = false
        exoPlayer?.let { player ->
            player.shuffleModeEnabled = shuffleEnabled
            player.repeatMode = repeatMode
        }
        prewarmTrackThumbnail(ctx, snapshot.track)
        fetchLyricsForCurrentTrack(snapshot.track)
    }

    /** Persists the current track, queue, and timestamp so cold relaunch restores the Mini Player. */
    fun savePlaybackSession(sync: Boolean = false) {
        val ctx = appContext ?: return
        val track = currentTrack ?: return
        val currentPos = if (!restoredPendingStream && !resolvingStream) {
            exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: positionMs
        } else {
            positionMs
        }
        val currentDur = if (!restoredPendingStream && !resolvingStream) {
            exoPlayer?.duration?.takeIf { it > 0L } ?: durationMs
        } else {
            durationMs
        }
        positionMs = currentPos
        durationMs = currentDur
        PlaybackSessionStore.save(
            context = ctx,
            snapshot = PlaybackSessionSnapshot(
                track = track,
                positionMs = currentPos,
                durationMs = currentDur,
                queue = queue.toList(),
                queueIndex = queueIndex,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode
            ),
            sync = sync
        )
    }

    fun ensureProgressLoopRunning() {
        if (progressJob == null || progressJob?.isActive != true) {
            startProgressLoop()
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (!restoredPendingStream && !resolvingStream) {
                        positionMs = player.currentPosition.coerceAtLeast(0L)
                        val dur = player.duration
                        if (dur > 0) {
                            durationMs = dur
                        }
                        isPlaying = player.isPlaying
                        playbackState = player.playbackState
                        isLoading = !player.isPlaying && player.playbackState == Player.STATE_BUFFERING
                        val active = currentTrack
                        if (player.isPlaying && active != null) {
                            com.darkxvenom.itune.data.discord.DiscordPresenceManager.onTrackProgress(
                                active, positionMs, durationMs, playbackSpeed
                            )
                            YouTubePlaybackTracker.onProgress(active.id, positionMs / 1000L)
                            com.darkxvenom.itune.data.lastfm.LastFmIntegration.onPlayback(
                                trackId = active.id,
                                title = active.title,
                                artists = active.artists,
                                album = active.album,
                                durationMs = durationMs,
                                positionMs = positionMs,
                            )
                            maybeExtendEndlessQueue()
                        }

                        val now = System.currentTimeMillis()
                        if (player.isPlaying && now - lastPersistedAtMs >= 2000L) {
                            lastPersistedAtMs = now
                            savePlaybackSession(sync = false)
                        }
                    }
                }
                delay(200)
            }
        }
    }

    fun play(
        track: MusicTrack,
        newQueue: List<MusicTrack>? = null,
        startPositionMs: Long = 0L,
        expandPlayer: Boolean = true,
        autoPlay: Boolean = true,
        fromEngine: Boolean = false,
        preResolvedUrl: String? = null,
    ) {
        autoSkipFailedTrackIds.remove(track.id)
        ensureProgressLoopRunning()
        if (AutoSleepTimerManager.isSleepWindowActive()) {
            appContext?.let {
                android.widget.Toast.makeText(it, it.getString(R.string.toast_auto_sleep_timer_active_playback_paused), android.widget.Toast.LENGTH_SHORT).show()
            }
            pause()
            return
        }

        cacheOnNaturalCompletionTrackId = track.id.takeIf {
            startPositionMs <= CACHE_FROM_BEGINNING_TOLERANCE_MS &&
                track.localPlaybackUri == null &&
                MusicCacheManager.cachingEnabled.value &&
                !MusicCacheManager.isTrackCached(track.id)
        }

        if (com.darkxvenom.itune.data.library.SettingsStore.isPermanentShuffleEnabled()) {
            shuffleEnabled = true
            exoPlayer?.shuffleModeEnabled = true
        }

        // Any manual track change cancels a running crossfade; the engine's own hand-off must not.
        if (!fromEngine) TransitionEngine.cancel()
        plannedNext = null
        TransitionEngine.onTrackStarted()
        com.darkxvenom.itune.radio.OnlineRadioController.pauseIfPlaying()
        if (currentTrack != null && currentTrack?.id != track.id) {
            YouTubePlaybackTracker.onTrackChanged(positionMs / 1000L)
            com.darkxvenom.itune.data.timetravel.TimeTravelPlaybackTracker.onTrackChanged()
        }
        if (currentTrack?.id != track.id) {
            streamRetryAttemptCount = 0
            lastFailedStreamUrl = null
        }
        if (expandPlayer) expanded = true
        loadJob?.cancel()

        exoPlayer?.let { player ->
            player.playWhenReady = false
            player.pause()
        }
        val safeStartMs = startPositionMs.coerceAtLeast(0L)
        val sameTrack = currentTrack?.id == track.id
        restoredPendingStream = false
        resolvingStream = true
        isPlaying = false
        isLoading = true
        positionMs = safeStartMs
        durationMs = when {
            sameTrack && durationMs > 0L -> durationMs
            track.durationSec > 0L -> track.durationSec * 1000L
            else -> 0L
        }
        val cleanArtist: String = if (track.artists.equals("iShare", ignoreCase = true) || track.artists.isBlank()) {
            val cached = MusicCacheManager.getCachedTrack(track.id)?.artists
            if (!cached.isNullOrBlank() && !cached.equals("iShare", ignoreCase = true)) {
                cached
            } else if (track.artists.equals("iShare", ignoreCase = true)) {
                ""
            } else {
                track.artists
            }
        } else {
            track.artists
        }
        val safeTrack = if (cleanArtist != track.artists) track.copy(artists = cleanArtist) else track
        currentTrack = safeTrack
        // Make sure the media notification exists as soon as a song is requested (not only once
        // the stream has resolved), so it shows up for every song.
        if (autoPlay) appContext?.let { PlaybackService.start(it) }
        com.darkxvenom.itune.audio.LowPassFilterController.onPlaybackStarted(safeTrack.id)
        appContext?.let { LibraryStore.recordRecentSong(it, safeTrack) }
        if (autoPlay) {
            appContext?.let {
                com.darkxvenom.itune.data.library.ListeningProfileStore.recordPlay(it, safeTrack)
                com.darkxvenom.itune.data.timetravel.TimeTravelPlaybackTracker.onTrackStarted(it, safeTrack)
            }
        }

        // Fetch synchronized lyrics unless already loaded for this restored track
        if (!sameTrack || (lyrics.isEmpty() && !lyricsLoading)) {
            fetchLyricsForCurrentTrack(safeTrack)
        }

        // Pre-warm thumbnail in Coil cache with ultra-high quality
        appContext?.let { ctx -> prewarmTrackThumbnail(ctx, safeTrack) }

        if (newQueue != null && newQueue !== queue) {
            val snapshot = newQueue.toList()
            queue.clear()
            queue.addAll(snapshot)
            val idx = queue.indexOfFirst { it.id == safeTrack.id }
            queueIndex = if (idx >= 0) idx else 0
        } else {
            val idx = queue.indexOfFirst { it.id == safeTrack.id }
            if (idx >= 0) {
                queueIndex = idx
            } else {
                queue.add(safeTrack)
                queueIndex = queue.lastIndex
            }
        }
        savePlaybackSession(sync = false)
        if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()

        loadJob = scope.launch {
            try {
                // Pre-warm the next 3 queued tracks in original master quality
                if (appContext != null) {
                    for (step in 1..3) {
                        val nextInQueue = queue.getOrNull(queueIndex + step) ?: continue
                        prewarmTrackThumbnail(appContext!!, nextInQueue)
                    }
                }

                val cachedFile = MusicCacheManager.getCachedAudioFile(track.id)
                // Pill state for cached songs, plus the optional background "upgrade cached songs" pass.
                if (cachedFile != null) {
                    val rawSource = MusicCacheManager.cachedSource(track.id) ?: "YouTube Music"
                    val cachedFrom = when (rawSource) {
                        "yt" -> "YouTube Music"
                        "saavn" -> "JioSaavn"
                        else -> rawSource
                    }
                    com.darkxvenom.itune.audio.StreamTraceStore.put(
                        track.id,
                        com.darkxvenom.itune.audio.StreamTrace(
                            source = "Local cache ($cachedFrom)",
                            quality = "",
                            steps = listOf(
                                "Found a saved copy on this device (${MusicCacheManager.formatStorageSize(cachedFile.length())})",
                                "That copy was originally downloaded from $cachedFrom",
                                "No network request needed — played straight from storage",
                            ),
                        ),
                    )
                    if (cachedFrom.contains("saavn", ignoreCase = true)) {
                        com.darkxvenom.itune.data.saavn.SaavnQuality.set(track.id, com.darkxvenom.itune.data.saavn.SaavnStatus.HIGH_QUALITY)
                    } else {
                        com.darkxvenom.itune.data.saavn.SaavnQuality.clear(track.id)
                        val st = SettingsStore.state.value
                        if (st.upgradeCachedSongs && !st.offlineMode) upgradeCachedInBackground(track)
                    }
                }
                LiveLogs.event("Playback", when {
                    preResolvedUrl != null -> "Using pre-resolved stream"
                    track.localPlaybackUri != null -> "Opening local audio"
                    cachedFile != null -> "Opening cached audio"
                    else -> "Requesting stream extraction"
                })
                val streamUrl = preResolvedUrl ?: track.localPlaybackUri ?: if (cachedFile != null) {
                    Uri.fromFile(cachedFile).toString()
                } else if (SettingsStore.state.value.offlineMode) {
                    null
                } else {
                    MusicBackend.resolveAudioStreamUrl(track.id, title = track.title, artist = track.artists)
                }
                if (streamUrl.isNullOrBlank()) {
                    Log.e(TAG, "Failed to resolve stream URL for track: ${track.id}")
                    if (SettingsStore.state.value.offlineMode && cachedFile == null) {
                        appContext?.let { Toast.makeText(it, it.getString(R.string.toast_offline_mode_this_song_is_not_downloaded), Toast.LENGTH_SHORT).show() }
                    }
                    showPlaybackFailure(track.id, null)
                    resolvingStream = false
                    isLoading = false
                    autoSkipAfterFailure(track.id)
                    return@launch
                }

                exoPlayer?.let { player ->
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(track.id)
                        .setUri(Uri.parse(streamUrl))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artists)
                                .setAlbumTitle(track.album)
                                .setArtworkUri((track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl)?.let(Uri::parse))
                                .build()
                        )
                        .build()
                    player.setMediaItem(mediaItem, safeStartMs)
                    player.prepare()
                    player.setPlaybackSpeed(if (holdingFast) 2f else playbackSpeed)
                    player.playWhenReady = autoPlay
                    if (autoPlay) player.play() else player.pause()
                    resolvingStream = false
                    if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Error playing music track", e)
                showPlaybackFailure(track.id, e)
                if (currentTrack?.id == track.id) {
                    resolvingStream = false
                    isLoading = false
                }
                autoSkipAfterFailure(track.id)
            }
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    private fun fetchLyricsForCurrentTrack(track: MusicTrack) {
        lyricsJob?.cancel()
        lyrics = emptyList()
        rawLyricsText = ""
        hasCustomLyricsOverride = false
        lyricsLoading = true

        val prefs = appContext?.getSharedPreferences(LYRICS_OVERRIDE_PREFS, Context.MODE_PRIVATE)
        lyricsOffsetMs = (prefs?.getLong("offset_${track.id}", 0L) ?: 0L).coerceIn(-5000L, 5000L)
        val rawPref = prefs?.getString("custom_${track.id}", null)
        val savedCustomRaw = rawPref?.takeIf { it.isNotBlank() && !it.trim().equals("null", ignoreCase = true) }
        if (rawPref != null && savedCustomRaw == null) {
            prefs.edit().remove("custom_${track.id}").apply()
        }

        if (savedCustomRaw != null) {
            val parsedCustom = LyricsHelper.parseLyricsText(savedCustomRaw)
            lyrics = parsedCustom
            rawLyricsText = savedCustomRaw
            hasCustomLyricsOverride = true
            lyricsLoading = false
            LyricsHelper.updateMemoryCache(track.id, savedCustomRaw, parsedCustom)
            return
        }

        lyricsJob = scope.launch {
            try {
                val loaded = LyricsHelper.loadLyricsWithRaw(
                    songId = track.id,
                    title = track.title,
                    artist = track.artists,
                    durationSec = track.durationSec.toInt()
                )
                if (currentTrack?.id == track.id) {
                    lyrics = loaded.lines
                    rawLyricsText = loaded.rawText.ifBlank { LyricsHelper.formatToEditableText(loaded.lines) }
                }
            } catch (_: Exception) {
                if (currentTrack?.id == track.id) {
                    lyrics = emptyList()
                    rawLyricsText = ""
                }
            } finally {
                if (currentTrack?.id == track.id) {
                    lyricsLoading = false
                }
            }
        }
    }

    /** Updates live lyrics sync offset in milliseconds (`-5000..5000`) without altering audio playback. */
    fun setLyricsOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-5000L, 5000L)
        lyricsOffsetMs = clamped
        val trackId = currentTrack?.id ?: return
        appContext?.getSharedPreferences(LYRICS_OVERRIDE_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putLong("offset_$trackId", clamped)
            ?.apply()
    }

    /** Saves user-selected or user-edited lyrics locally for [track] and updates the Lyrics screen immediately. */
    fun applyCustomLyrics(track: MusicTrack, rawText: String) {
        lyricsJob?.cancel()
        val rawClean = rawText.trim()
        val trimmed = if (rawClean.equals("null", ignoreCase = true)) "" else rawClean
        val parsed = if (trimmed.isNotEmpty()) LyricsHelper.parseLyricsText(trimmed) else emptyList()
        lyrics = parsed
        rawLyricsText = trimmed
        hasCustomLyricsOverride = trimmed.isNotEmpty()
        lyricsLoading = false
        LyricsHelper.updateMemoryCache(track.id, trimmed, parsed)
        appContext?.getSharedPreferences(LYRICS_OVERRIDE_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.apply {
                if (trimmed.isNotEmpty()) putString("custom_${track.id}", trimmed)
                else remove("custom_${track.id}")
            }
            ?.apply()
    }

    /** Removes any manual lyrics override for [track] and reloads provider lyrics automatically. */
    fun resetCustomLyrics(track: MusicTrack) {
        hasCustomLyricsOverride = false
        LyricsHelper.invalidateMemoryCache(track.id)
        appContext?.getSharedPreferences(LYRICS_OVERRIDE_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove("custom_${track.id}")
            ?.apply()
        fetchLyricsForCurrentTrack(track)
    }

    fun refreshLyrics() {
        currentTrack?.let(::fetchLyricsForCurrentTrack)
    }

    fun pause() {
        exoPlayer?.pause()
        isPlaying = false
    }

    fun stop() {
        cacheOnNaturalCompletionTrackId = null
        progressJob?.cancel()
        loadJob?.cancel()
        runCatching {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
        isPlaying = false
        isLoading = false
        playbackState = Player.STATE_IDLE
    }

    fun resetForAccountSwitch() {
        stop()
        cacheOnNaturalCompletionTrackId = null
        currentTrack = null
        queue.clear()
        queueIndex = 0
        positionMs = 0L
        durationMs = 0L
        lyrics = emptyList()
        rawLyricsText = ""
        lyricsOffsetMs = 0L
        hasCustomLyricsOverride = false
        lyricsLoading = false
        ensureProgressLoopRunning()
    }

    /** Live format the decoder is reading right now (mime, bitrate, sample rate, channels), or null. */
    fun liveAudioFormat(): androidx.media3.common.Format? = exoPlayer?.audioFormat

    // ── Upgrade Quality (JioSaavn) ──────────────────────────────────────────

    /** True while the 3-dot menu's "Upgrade Quality" is searching / swapping. */
    var upgradingQuality by mutableStateOf(false)
        private set

    private val backgroundUpgrades = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Settings: "Upgrade Quality of cached songs" — replaces cached songs with higher-quality streams from enabled sources in priority order. */
    private fun upgradeCachedInBackground(track: MusicTrack) {
        if (!backgroundUpgrades.add(track.id)) return
        scope.launch(Dispatchers.IO) {
            try {
                val activeSources = com.darkxvenom.itune.data.sources.SourceRegistry.activeForPlayback()
                    .filterNot { it.kind == com.darkxvenom.itune.data.sources.SourceKind.YOUTUBE }
                if (activeSources.isEmpty()) return@launch

                val target = com.darkxvenom.itune.data.sources.TrackMatcher.Target(
                    title = track.title,
                    artist = track.artists,
                    durationSec = track.durationSec.toInt().takeIf { it > 0 },
                    album = track.album
                )
                val request = com.darkxvenom.itune.data.sources.SourceResolver.requestForNow()

                for (source in activeSources) {
                    val stream = try {
                        source.search(
                            target.title,
                            limit = 15,
                            waitForAll = true,
                            request = request
                        )?.let { candidates ->
                            val matches = com.darkxvenom.itune.data.sources.TrackMatcher.ranked(candidates, target)
                            if (matches.isNotEmpty()) {
                                source.stream(matches.first().id, request)
                            } else null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    if (stream != null && stream.url.isNotBlank()) {
                        MusicCacheManager.cacheStream(track, stream.url, source = source.displayName, replaceExisting = true) { ok ->
                            if (ok && currentTrack?.id == track.id) {
                                if (source.kind == com.darkxvenom.itune.data.sources.SourceKind.JIOSAAVN) {
                                    com.darkxvenom.itune.data.saavn.SaavnQuality.set(track.id, com.darkxvenom.itune.data.saavn.SaavnStatus.HIGH_QUALITY)
                                }
                                com.darkxvenom.itune.audio.StreamTraceStore.put(
                                    track.id,
                                    com.darkxvenom.itune.audio.StreamTrace(
                                        source = "Local cache (${source.displayName})",
                                        quality = stream.format.summary,
                                        steps = listOf(
                                            "Background upgrade evaluated enabled sources in priority order",
                                            "Found higher quality copy on ${source.displayName} (${stream.format.summary})",
                                            "Cached file was upgraded to ${source.displayName}"
                                        )
                                    )
                                )
                            }
                        }
                        break // Stop extracting as soon as a source delivers!
                    }
                }
            } finally {
                val curSource = MusicCacheManager.cachedSource(track.id)
                if (curSource == "YouTube Music" || curSource == "yt") {
                    backgroundUpgrades.remove(track.id)
                }
            }
        }
    }

    /**
     * 3-dot menu → "Upgrade Quality". Runs through every enabled source in priority order one by one.
     * As soon as an upgraded stream is found, it stops extracting, seamlessly switches playback at the
     * current position, updates the Signal Pipeline with the source name and full details, and caches the stream.
     */
    fun upgradeCurrentQuality() {
        val ctx = appContext ?: return
        val track = currentTrack ?: return
        if (upgradingQuality) return
        if (SettingsStore.state.value.offlineMode) {
            Toast.makeText(ctx, ctx.getString(R.string.toast_offline_mode_cannot_upgrade_quality), Toast.LENGTH_SHORT).show()
            return
        }

        // Show immediate default toast on phone as requested:
        Toast.makeText(ctx, ctx.getString(R.string.toast_upgrading_quality), Toast.LENGTH_SHORT).show()
        upgradingQuality = true

        val activeSources = com.darkxvenom.itune.data.sources.SourceRegistry.activeForPlayback()
            .filterNot { it.kind == com.darkxvenom.itune.data.sources.SourceKind.YOUTUBE }

        if (activeSources.isEmpty()) {
            upgradingQuality = false
            Toast.makeText(ctx, ctx.getString(R.string.toast_no_lossless_streaming_sources_enabled_in_sources), Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val evalSteps = mutableListOf<String>()
            evalSteps.add("Upgrade Quality initiated for '${track.title}'")
            evalSteps.add("Evaluating ${activeSources.size} enabled source(s) in priority order")
            var winningSource: com.darkxvenom.itune.data.sources.MusicSource? = null
            var winningStream: com.darkxvenom.itune.data.sources.SourceStream? = null

            try {
                com.darkxvenom.itune.data.saavn.SaavnQuality.set(track.id, com.darkxvenom.itune.data.saavn.SaavnStatus.SEARCHING)
                val target = com.darkxvenom.itune.data.sources.TrackMatcher.Target(
                    title = track.title,
                    artist = track.artists,
                    durationSec = track.durationSec.toInt().takeIf { it > 0 },
                    album = track.album
                )
                val request = com.darkxvenom.itune.data.sources.SourceResolver.requestForNow()

                for ((idx, source) in activeSources.withIndex()) {
                    val rank = idx + 1
                    evalSteps.add("#$rank Priority: Querying ${source.displayName}...")
                    com.darkxvenom.itune.audio.StreamTraceStore.put(
                        track.id,
                        com.darkxvenom.itune.audio.StreamTrace(
                            source = source.displayName,
                            quality = "Upgrading...",
                            steps = evalSteps.toList()
                        )
                    )

                    val queriesToTry = mutableListOf<String>()
                    val cleanTitle = com.darkxvenom.itune.data.sources.TrackMatcher.searchableTitle(target.title, target.artist)
                    val primaryArtist = com.darkxvenom.itune.data.sources.TrackMatcher.primaryArtist(target.artist)
                    if (cleanTitle.isNotBlank() && primaryArtist.isNotBlank()) {
                        queriesToTry.add("$cleanTitle $primaryArtist".trim())
                    }
                    if (cleanTitle.isNotBlank()) {
                        queriesToTry.add(cleanTitle)
                    }
                    if (target.title.isNotBlank() && target.artist.isNotBlank()) {
                        queriesToTry.add("${target.title} ${target.artist}".trim())
                    }
                    if (target.title.isNotBlank()) {
                        queriesToTry.add(target.title)
                    }
                    val distinctQueries = queriesToTry.distinct()

                    var stream: com.darkxvenom.itune.data.sources.SourceStream? = null
                    for (q in distinctQueries) {
                        try {
                            val candidates = source.search(
                                q,
                                limit = 20,
                                waitForAll = true,
                                request = request
                            )
                            val matches = if (!candidates.isNullOrEmpty()) {
                                com.darkxvenom.itune.data.sources.TrackMatcher.ranked(candidates, target)
                            } else emptyList()
                            if (matches.isNotEmpty()) {
                                stream = source.stream(matches.first().id, request)
                                if (stream != null && stream.url.isNotBlank()) {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Source ${source.displayName} (#$rank) upgrade search query '$q' failed: ${e.message}")
                        }
                    }

                    if (stream != null && stream.url.isNotBlank()) {
                        val srcName = stream.sourceConfigId?.let { com.darkxvenom.itune.data.sources.SourceRegistry.config(it)?.displayName }
                            ?: source.displayName
                        val kbps = stream.format.kbps ?: if (stream.format.isLossless == true) 1411 else 320
                        val codec = stream.format.codec?.uppercase(java.util.Locale.ROOT) ?: (if (stream.format.isLossless == true) "FLAC" else "AAC")
                        evalSteps.add("Matched valid stream on $srcName (#$rank priority): $kbps kbps • $codec")
                        winningSource = source
                        winningStream = stream
                        // STOP EXTRACTING immediately on first valid stream!
                        break
                    } else {
                        evalSteps.add("No matching stream found on ${source.displayName} (#$rank priority)")
                    }
                }

                if (winningSource == null || winningStream == null) {
                    evalSteps.add("Finished evaluation: No higher quality stream available across enabled sources")
                    com.darkxvenom.itune.audio.StreamTraceStore.put(
                        track.id,
                        com.darkxvenom.itune.audio.StreamTrace(
                            source = "YouTube Music",
                            quality = "128 kbps • AAC",
                            steps = evalSteps.toList()
                        )
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, ctx.getString(R.string.toast_no_higher_quality_stream_found_across_enabled_sour), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (currentTrack?.id != track.id) return@launch // user moved on meanwhile

                val srcName = winningSource.displayName
                val kbps = winningStream.format.kbps ?: if (winningStream.format.isLossless == true) 1411 else 320
                val codec = winningStream.format.codec?.uppercase(java.util.Locale.ROOT) ?: (if (winningStream.format.isLossless == true) "FLAC" else "AAC")
                val fmtSummary = "$kbps kbps • $codec"
                evalSteps.add("Swapping playback seamlessly to $srcName ($fmtSummary)")

                withContext(Dispatchers.Main) {
                    exoPlayer?.let { player ->
                        val resumeAt = player.currentPosition.coerceAtLeast(0L)
                        val wasPlaying = player.playWhenReady
                        val item = MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri(Uri.parse(winningStream.url))
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artists)
                                    .setAlbumTitle(track.album)
                                    .setArtworkUri((track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl)?.let(Uri::parse))
                                    .build()
                            )
                            .build()
                        player.setMediaItem(item, resumeAt)
                        player.prepare()
                        player.setPlaybackSpeed(if (holdingFast) 2f else playbackSpeed)
                        player.playWhenReady = wasPlaying
                    }
                    Toast.makeText(ctx, ctx.getString(R.string.toast_upgraded_to_lossless_kbps, kbps), Toast.LENGTH_SHORT).show()
                }

                // Update signal pipeline trace store with the winning streamer and full evaluation steps
                com.darkxvenom.itune.audio.StreamTraceStore.put(
                    track.id,
                    com.darkxvenom.itune.audio.StreamTrace(
                        source = srcName,
                        quality = fmtSummary,
                        steps = evalSteps.toList()
                    )
                )

                if (winningSource.kind == com.darkxvenom.itune.data.sources.SourceKind.JIOSAAVN) {
                    com.darkxvenom.itune.data.saavn.SaavnQuality.set(track.id, com.darkxvenom.itune.data.saavn.SaavnStatus.HIGH_QUALITY)
                }

                // Replace the cached copy with the upgraded stream
                MusicCacheManager.cacheStream(track, winningStream.url, source = srcName, replaceExisting = true)
            } finally {
                upgradingQuality = false
            }
        }
    }

    fun togglePlayPause() {
        ensureProgressLoopRunning()
        val track = currentTrack ?: return
        if (restoredPendingStream) {
            if (!resolvingStream) {
                play(track = track, startPositionMs = positionMs, expandPlayer = expanded)
            }
            return
        }
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                com.darkxvenom.itune.radio.OnlineRadioController.pauseIfPlaying()
                player.play()
            }
        }
        if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()
    }

    fun hasNextTrack(): Boolean {
        if (queue.isEmpty()) return false
        return (shuffleEnabled && queue.size > 1) ||
            (queueIndex + 1 < queue.size) ||
            (repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty())
    }

    fun hasPreviousTrack(): Boolean {
        if (queue.isEmpty()) return false
        return positionMs > 3000L ||
            queueIndex > 0 ||
            ((shuffleEnabled || repeatMode == Player.REPEAT_MODE_ALL) && queue.size > 1)
    }

    // The next song is decided once so that the crossfade engine, which prepares it ahead of time,
    // and skipNext() always agree (matters for shuffle, which picks at random).
    private var plannedNext: Pair<Int, String>? = null

    private fun pickNextIndex(): Int? {
        if (queue.isEmpty()) return null
        return if (shuffleEnabled && queue.size > 1) {
            (queue.indices.filter { it != queueIndex }).randomOrNull()
        } else {
            val sequential = queueIndex + 1
            when {
                sequential < queue.size -> sequential
                repeatMode == Player.REPEAT_MODE_ALL -> 0
                else -> null
            }
        }
    }

    internal fun peekNext(): Pair<Int, MusicTrack>? {
        val planned = plannedNext
        if (planned != null) {
            val track = queue.getOrNull(planned.first)
            if (track != null && track.id == planned.second && planned.first != queueIndex) {
                return planned.first to track
            }
        }
        val index = pickNextIndex() ?: return null
        val track = queue.getOrNull(index) ?: return null
        plannedNext = index to track.id
        return index to track
    }

    fun skipNext() {
        if (queue.isEmpty()) return
        val (nextIndex, nextTrack) = peekNext() ?: return
        queueIndex = nextIndex
        play(nextTrack, expandPlayer = expanded)
    }

    // ── Bridge used by TransitionEngine ──

    internal fun enginePlayer(): ExoPlayer? = exoPlayer

    internal fun engineBusy(): Boolean = resolvingStream || restoredPendingStream

    internal fun engineRestoreVolume() {
        exoPlayer?.volume = volume
    }

    internal fun startTransitionPlayback(index: Int, track: MusicTrack, url: String) {
        queueIndex = index
        play(track, expandPlayer = expanded, fromEngine = true, preResolvedUrl = url)
    }

    fun skipPrevious(forcePrevious: Boolean = false) {
        if (queue.isEmpty()) return
        val hasPreviousInQueue = (shuffleEnabled && queue.size > 1) ||
            queueIndex > 0 ||
            (repeatMode == Player.REPEAT_MODE_ALL && queue.size > 1)
        if ((!forcePrevious || !hasPreviousInQueue) && positionMs > 3000L) {
            seekTo(0L)
            return
        }
        if (queue.size <= 1) return
        val prevIndex = when {
            shuffleEnabled -> (queue.indices.filter { it != queueIndex }).randomOrNull() ?: 0
            queueIndex > 0 -> queueIndex - 1
            repeatMode == Player.REPEAT_MODE_ALL -> queue.size - 1
            else -> return
        }
        val prevTrack = queue.getOrNull(prevIndex) ?: return
        queueIndex = prevIndex
        play(prevTrack, expandPlayer = expanded)
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        updateCacheEligibilityAfterSeek(safePos)
        this.positionMs = safePos
        if (!restoredPendingStream) {
            exoPlayer?.seekTo(safePos)
        }
        savePlaybackSession(sync = false)
        if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()
    }

    private fun updateCacheEligibilityAfterSeek(positionMs: Long) {
        val track = currentTrack
        cacheOnNaturalCompletionTrackId = track?.id?.takeIf {
            positionMs <= CACHE_FROM_BEGINNING_TOLERANCE_MS &&
                MusicCacheManager.cachingEnabled.value &&
                !MusicCacheManager.isTrackCached(it)
        }
    }

    private fun cacheCompletedTrackIfEligible() {
        val track = currentTrack ?: run {
            cacheOnNaturalCompletionTrackId = null
            return
        }
        val player = exoPlayer
        val finalPositionMs = maxOf(player?.currentPosition?.coerceAtLeast(0L) ?: 0L, positionMs)
        val finalDurationMs = player?.duration?.takeIf { it > 0L } ?: durationMs
        val shouldCache = shouldCacheAfterFullPlayback(
            activeTrackId = track.id,
            eligibleTrackId = cacheOnNaturalCompletionTrackId,
            cachingEnabled = MusicCacheManager.cachingEnabled.value,
            alreadyCached = MusicCacheManager.isTrackCached(track.id),
            positionMs = finalPositionMs,
            durationMs = finalDurationMs,
        )
        cacheOnNaturalCompletionTrackId = null
        if (shouldCache) {
            Log.i(TAG, "Track completed from its beginning. Adding '${track.title}' to offline cache")
            MusicCacheManager.cacheTrackOnDemand(track)
        }
    }

    fun adjustVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        exoPlayer?.volume = volume
    }

    fun toggleShuffle() {
        if (com.darkxvenom.itune.data.library.SettingsStore.isPermanentShuffleEnabled()) {
            shuffleEnabled = true
            exoPlayer?.shuffleModeEnabled = true
            appContext?.let {
                android.widget.Toast.makeText(it, it.getString(R.string.toast_shuffle_is_locked_by_permanent_shuffle_in_settings), android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        shuffleEnabled = !shuffleEnabled
        exoPlayer?.shuffleModeEnabled = shuffleEnabled
        savePlaybackSession(sync = false)
    }

    /**
     * Plays [track] immediately, and seeds the playback queue with algorithmic recommendations
     * fetched from YouTube Music's Next/Radio endpoint ("Because you listened to...").
     */
    fun playWithRadioQueue(
        track: MusicTrack,
        expandPlayer: Boolean = false,
    ) {
        play(
            track = track,
            newQueue = listOf(track),
            expandPlayer = expandPlayer,
        )

        scope.launch(Dispatchers.IO) {
            val radioTracks = runCatching {
                MusicBackend.fetchRadioQueue(track.id)
            }.getOrDefault(emptyList()).filter { it.id != track.id }

            if (radioTracks.isNotEmpty() && currentTrack?.id == track.id) {
                withContext(Dispatchers.Main) {
                    if (currentTrack?.id == track.id) {
                        val existingIds = queue.map { it.id }.toSet()
                        val newItems = radioTracks.filter { it.id !in existingIds }
                        queue.addAll(newItems)
                        savePlaybackSession(sync = false)
                    }
                }
            }
        }
    }

    /**
     * Extends the active queue with YouTube Music's own Next/Radio recommendations.
     * Fetches while two or fewer upcoming tracks remain so the next batch is ready
     * before the listener reaches the end of the current queue.
     */
    fun toggleEndlessQueue() {
        endlessQueueEnabled = !endlessQueueEnabled
        if (endlessQueueEnabled) {
            endlessQueueRequestedForTrackId = null
            maybeExtendEndlessQueue()
        } else {
            endlessQueueJob?.cancel()
            endlessQueueJob = null
            endlessQueueRequestedForTrackId = null
        }
        savePlaybackSession(sync = false)
    }

    private fun maybeExtendEndlessQueue() {
        if (!endlessQueueEnabled || endlessQueueJob?.isActive == true) return
        val track = currentTrack ?: return
        val upcomingCount = (queue.size - queueIndex - 1).coerceAtLeast(0)
        if (upcomingCount > 2 || endlessQueueRequestedForTrackId == track.id) return

        endlessQueueRequestedForTrackId = track.id
        endlessQueueJob = scope.launch {
            try {
                val recommendations = withContext(Dispatchers.IO) {
                    MusicBackend.fetchRadioQueue(track.id)
                }
                if (!endlessQueueEnabled || currentTrack?.id != track.id) return@launch

                val existingIds = queue.mapTo(mutableSetOf()) { it.id }
                val newTracks = recommendations.filter { existingIds.add(it.id) }
                if (newTracks.isNotEmpty()) {
                    queue.addAll(newTracks)
                    appContext?.let { prewarmTrackThumbnail(it, newTracks.first()) }
                    savePlaybackSession(sync = false)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Endless queue extension failed: ${error.message}")
                endlessQueueRequestedForTrackId = null
            } finally {
                endlessQueueJob = null
            }
        }
    }
    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer?.repeatMode = repeatMode
        savePlaybackSession(sync = false)
    }

    fun removeFromQueue(index: Int) {
        if (index in queue.indices) {
            val wasPlayingCurrent = (index == queueIndex)
            queue.removeAt(index)
            if (index < queueIndex) {
                queueIndex = (queueIndex - 1).coerceAtLeast(0)
            } else if (queueIndex >= queue.size) {
                queueIndex = (queue.size - 1).coerceAtLeast(0)
            }
            if (wasPlayingCurrent) {
                if (queue.isNotEmpty() && queueIndex in queue.indices) {
                    play(queue[queueIndex], expandPlayer = expanded)
                } else {
                    close()
                }
            } else {
                savePlaybackSession(sync = false)
                if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()
            }
        }
    }

    /** Removes an upcoming item relative to [queueIndex] or matching [songId]. */
    fun removeUpcomingFromIShare(upcomingIndex: Int, songId: String?) {
        val matchIdx = if (!songId.isNullOrBlank()) {
            val upcomingMatch = ((queueIndex + 1).coerceAtLeast(0) until queue.size)
                .firstOrNull { queue[it].id == songId }
            upcomingMatch ?: queue.indexOfFirst { it.id == songId }
        } else if (upcomingIndex >= 0) {
            val candidate = queueIndex + 1 + upcomingIndex
            if (candidate in queue.indices) candidate else upcomingIndex.takeIf { it in queue.indices } ?: -1
        } else -1
        if (matchIdx in queue.indices) {
            removeFromQueue(matchIdx)
        }
    }

    /** Moves an upcoming queue track cleanly (0-based relative to current track + 1). */
    fun moveUpcomingTrack(fromUpcoming: Int, toUpcoming: Int) {
        val base = (queueIndex + 1).coerceAtLeast(0)
        val fromIdx = base + fromUpcoming
        val toIdx = base + toUpcoming
        if (fromIdx in base until queue.size && toIdx in base until queue.size && fromIdx != toIdx) {
            val item = queue.removeAt(fromIdx)
            queue.add(toIdx, item)
            savePlaybackSession(sync = false)
            if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()
        }
    }

    /** Inserts [track] immediately after the current track (or starts playing it if queue is empty). */
    fun playNext(track: MusicTrack) {
        if (queue.isEmpty() || currentTrack == null) {
            play(track, listOf(track))
            return
        }
        val insertIndex = (queueIndex + 1).coerceIn(0, queue.size)
        queue.add(insertIndex, track)
        savePlaybackSession(sync = false)
        if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()
    }

    /** Appends [tracks] to the end of the active playback queue (or starts playback if empty). */
    fun addToQueue(tracks: List<MusicTrack>) {
        if (tracks.isEmpty()) return
        if (queue.isEmpty() || currentTrack == null) {
            play(tracks.first(), tracks)
            return
        }
        queue.addAll(tracks)
        savePlaybackSession(sync = false)
        if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()
    }

    fun close() {
        com.darkxvenom.itune.data.discord.DiscordPresenceManager.clearActivity()
        TransitionEngine.cancel()
        YouTubePlaybackTracker.onPlaybackFinished(positionMs / 1000L)
        loadJob?.cancel()
        lyricsJob?.cancel()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        restoredPendingStream = false
        resolvingStream = false
        currentTrack = null
        queue.clear()
        queueIndex = 0
        isPlaying = false
        holdingFast = false
        isLoading = false
        positionMs = 0L
        durationMs = 0L
        expanded = false
        queueOpen = false
        lyricsOpen = false
        lyrics = emptyList()
        rawLyricsText = ""
        lyricsOffsetMs = 0L
        hasCustomLyricsOverride = false
        lyricsLoading = false
        appContext?.let { PlaybackSessionStore.clear(it) }
    }

    fun startFastHold(): Boolean {
        if (holdingFast) return false
        holdingFast = true
        preHoldSpeed = playbackSpeed
        exoPlayer?.setPlaybackSpeed(2f)
        return true
    }

    fun endFastHold() {
        if (!holdingFast) return
        holdingFast = false
        exoPlayer?.setPlaybackSpeed(preHoldSpeed)
    }

    @OptIn(UnstableApi::class)
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        AudioEngineManager.attachAudioSession(audioSessionId, isPlaying)
    }

    @OptIn(UnstableApi::class)
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        LiveLogs.event("Player", if (isPlaying) "Playing" else "Playback paused or interrupted")
        this.isPlaying = isPlaying
        AudioEngineManager.updatePlayingState(isPlaying)
        com.darkxvenom.itune.data.timetravel.TimeTravelPlaybackTracker.onIsPlayingChanged(isPlaying)
        if (isPlaying) {
            isLoading = false
            exoPlayer?.audioSessionId?.let { sid ->
                AudioEngineManager.attachAudioSession(sid, true)
            }
            simultaneousProcessor?.onPlay()
            val active = currentTrack
            val ctx = appContext
            if (active != null && ctx != null) {
                YouTubePlaybackTracker.onPlaying(ctx, active)
            }
        } else {
            com.darkxvenom.itune.data.discord.DiscordPresenceManager.clearActivity()
            simultaneousProcessor?.onPause()
            if (!restoredPendingStream && !resolvingStream) {
                savePlaybackSession(sync = false)
            }
        }
        if (!isApplyingRemoteSync && !resolvingStream) {
            IShareSyncManager.onLocalPlaybackEvent()
        }
        appContext?.let { PlaybackService.sync(it) }
    }

    // ── iShare Remote Playback Synchronization Helpers ──

    fun playSyncedFromIShare(
        track: MusicTrack,
        upcomingQueue: List<MusicTrack>,
        positionMs: Long,
        shouldPlay: Boolean,
    ) {
        isApplyingRemoteSync = true
        try {
            val fullQueue = listOf(track) + upcomingQueue.filterNot { it.id == track.id }
            play(
                track = track,
                newQueue = fullQueue,
                startPositionMs = positionMs.coerceAtLeast(0L),
                expandPlayer = false,
                autoPlay = shouldPlay,
            )
        } finally {
            isApplyingRemoteSync = false
        }
    }

    fun syncUpcomingQueueFromIShare(upcomingQueue: List<MusicTrack>) {
        if (upcomingQueue.isEmpty()) return
        val cur = currentTrack ?: return
        val desiredIds = upcomingQueue.map { it.id }
        val existingUpcomingIds = if (queueIndex in queue.indices) {
            queue.drop(queueIndex + 1).map { it.id }
        } else emptyList()
        if (existingUpcomingIds == desiredIds) return

        val keepPrefix = if (queueIndex in queue.indices) queue.take(queueIndex + 1) else listOf(cur)
        queue.clear()
        queue.addAll(keepPrefix)
        queue.addAll(upcomingQueue)
    }

    fun setPlayPauseFromSync(shouldPlay: Boolean) {
        val track = currentTrack ?: return
        isApplyingRemoteSync = true
        try {
            if (restoredPendingStream) {
                if (!resolvingStream) {
                    play(
                        track = track,
                        startPositionMs = positionMs,
                        expandPlayer = expanded,
                        autoPlay = shouldPlay,
                    )
                }
                return
            }
            exoPlayer?.let { player ->
                if (shouldPlay && !player.playWhenReady) {
                    player.playWhenReady = true
                    player.play()
                } else if (!shouldPlay && player.playWhenReady) {
                    player.playWhenReady = false
                    player.pause()
                }
            }
        } finally {
            isApplyingRemoteSync = false
        }
    }

    fun seekFromSync(positionMs: Long) {
        isApplyingRemoteSync = true
        try {
            val safePos = positionMs.coerceAtLeast(0L)
            this.positionMs = safePos
            if (!restoredPendingStream) exoPlayer?.seekTo(safePos)
        } finally {
            isApplyingRemoteSync = false
        }
    }

    fun setRemotePlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.85f, 1.25f)
        if (playbackSpeed != clamped && !holdingFast) {
            playbackSpeed = clamped
            exoPlayer?.setPlaybackSpeed(clamped)
        }
    }

    fun playFromIShareIndexOrId(index: Int, songId: String?) {
        if (queue.isEmpty()) return
        val targetIdx = if (!songId.isNullOrBlank()) {
            queue.indexOfFirst { it.id == songId }
        } else if (index >= 0) {
            val upcoming = queueIndex + 1 + index
            if (upcoming in queue.indices) upcoming else index.takeIf { it in queue.indices } ?: -1
        } else -1
        if (targetIdx in queue.indices) {
            queueIndex = targetIdx
            play(queue[targetIdx], expandPlayer = expanded)
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerEndOfTrack = false
        if (minutes <= 0) {
            sleepTimerRemainingMs = 0L
            return
        }
        sleepTimerRemainingMs = minutes * 60_000L
        sleepTimerJob = scope.launch {
            while (isActive && sleepTimerRemainingMs > 0L) {
                delay(1000L)
                sleepTimerRemainingMs = (sleepTimerRemainingMs - 1000L).coerceAtLeast(0L)
            }
            if (isActive) {
                exoPlayer?.pause()
                sleepTimerRemainingMs = 0L
            }
        }
    }

    fun setSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        sleepTimerRemainingMs = 0L
        sleepTimerEndOfTrack = true
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerRemainingMs = 0L
        sleepTimerEndOfTrack = false
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        LiveLogs.event("Player", when (playbackState) {
            Player.STATE_BUFFERING -> "Buffering"
            Player.STATE_READY -> "Ready"
            Player.STATE_ENDED -> "Track ended"
            else -> "Idle"
        })
        this.playbackState = playbackState
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                isLoading = true
            }
            Player.STATE_READY -> {
                isLoading = false
                if (LiveLogs.enabled) exoPlayer?.audioFormat?.let { format ->
                    LiveLogs.event("Decoder", "Sample rate ${format.sampleRate} Hz, channels ${format.channelCount}, bitrate ${format.bitrate} bps")
                }
                durationMs = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                autoSkipFailedTrackIds.clear()
                streamRetryAttemptCount = 0
                lastFailedStreamUrl = null
            }
            Player.STATE_ENDED -> {
                com.darkxvenom.itune.data.timetravel.TimeTravelPlaybackTracker.onTrackCompleted()
                cacheCompletedTrackIfEligible()
                if (sleepTimerEndOfTrack) {
                    sleepTimerEndOfTrack = false
                    exoPlayer?.pause()
                    exoPlayer?.seekTo(0L)
                } else {
                    skipNext()
                }
            }
            Player.STATE_IDLE -> {
                isLoading = false
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        com.darkxvenom.itune.audio.LowPassFilterController.onPlaybackStarted(currentTrack?.id)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            updateCacheEligibilityAfterSeek(newPosition.positionMs)
        }
        val loopedToBeginning = newPosition.positionMs < 2_000L && oldPosition.positionMs > 5_000L
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || loopedToBeginning) {
            com.darkxvenom.itune.audio.LowPassFilterController.onPlaybackStarted(
                newTrackId = currentTrack?.id,
                forceRestart = loopedToBeginning && repeatMode == Player.REPEAT_MODE_ONE,
            )
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer error: ${error.errorCodeName} - ${error.message}", error)
        val track = currentTrack
        val trackId = track?.id
        cacheOnNaturalCompletionTrackId = null
        val currentPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: positionMs
        val wasActivelyPlaying = currentPos > 3000L

        val httpEx = findCause<HttpDataSource.InvalidResponseCodeException>(error)
        LiveLogs.event("Player", "Error ${error.errorCode}, HTTP ${httpEx?.responseCode ?: "n/a"}")
        if (LiveLogs.enabled && httpEx != null && httpEx.responseBody.isNotEmpty()) {
            val body = httpEx.responseBody
            val excerpt = String(body, 0, minOf(body.size, 8192), Charsets.UTF_8)
            val type = httpEx.headerFields.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.firstOrNull()
            val reason = com.darkxvenom.itune.diagnostics.DiagnosticText.responseReason(excerpt, type)
            if (reason != null) LiveLogs.event("Playback HTTP ${httpEx.responseCode}", reason)
        }
        val isHttp403 = httpEx?.responseCode == 403 ||
            error.message?.contains("403") == true ||
            error.cause?.message?.contains("403") == true ||
            (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && httpEx?.responseCode != null && httpEx.responseCode >= 400)

        if (track != null && trackId != null && (isHttp403 || wasActivelyPlaying) && streamRetryAttemptCount < 2) {
            streamRetryAttemptCount++
            Log.w(TAG, "Attempting stream recovery for $trackId (attempt $streamRetryAttemptCount, 403=$isHttp403, pos=${currentPos}ms)...")

            // Invalidate cached stream in MusicBackend so we don't replay the same 403 URL
            MusicBackend.invalidateStreamCache(trackId)

            val currentMediaUri = exoPlayer?.currentMediaItem?.localConfiguration?.uri?.toString()
            val excluded = buildSet {
                currentMediaUri?.let { add(it) }
                lastFailedStreamUrl?.let { add(it) }
            }
            if (currentMediaUri != null) lastFailedStreamUrl = currentMediaUri

            scope.launch {
                delay(300L)
                val freshUrl = MusicBackend.resolveAudioStreamUrl(
                    videoId = trackId,
                    forceRefresh = true,
                    excludedUrls = excluded
                )
                if (!freshUrl.isNullOrBlank() && currentTrack?.id == trackId) {
                    Log.i(TAG, "Successfully recovered fresh stream for $trackId! Resuming playback...")
                    withContext(Dispatchers.Main) {
                        exoPlayer?.let { player ->
                            val mediaItem = MediaItem.Builder()
                                .setMediaId(trackId)
                                .setUri(Uri.parse(freshUrl))
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(track.title)
                                        .setArtist(track.artists)
                                        .setAlbumTitle(track.album)
                                        .setArtworkUri((track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl)?.let(Uri::parse))
                                        .build()
                                )
                                .build()
                            player.setMediaItem(mediaItem, currentPos)
                            player.prepare()
                            player.playWhenReady = true
                            player.play()
                        }
                    }
                    return@launch
                }

                // If recovery failed, proceed to standard failure display and auto-skip
                withContext(Dispatchers.Main) {
                    showPlaybackFailure(trackId, error)
                    resolvingStream = false
                    isLoading = false
                    autoSkipAfterFailure(trackId)
                }
            }
            return
        }

        showPlaybackFailure(currentTrack?.id, error)
        resolvingStream = false
        isLoading = false
        currentTrack?.id?.let(::autoSkipAfterFailure)
    }

    private inline fun <reified T : Throwable> findCause(throwable: Throwable?): T? {
        var current = throwable
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun autoSkipAfterFailure(failedTrackId: String) {
        if (!SettingsStore.state.value.autoSkipOnError || !autoSkipFailedTrackIds.add(failedTrackId)) return
        val currentIndex = queueIndex
        val currentQueue = queue.toList()
        val candidateIndices = if (shuffleEnabled && currentQueue.size > 1) {
            currentQueue.indices.filter { it != currentIndex && currentQueue[it].id !in autoSkipFailedTrackIds }.shuffled()
        } else {
            buildList {
                for (index in (currentIndex + 1) until currentQueue.size) add(index)
                if (repeatMode == Player.REPEAT_MODE_ALL) for (index in 0 until currentIndex) add(index)
            }.filter { currentQueue[it].id !in autoSkipFailedTrackIds }
        }
        val nextIndex = candidateIndices.firstOrNull() ?: return
        val nextTrack = currentQueue.getOrNull(nextIndex) ?: return
        Log.i(TAG, "Auto-skipping failed track $failedTrackId to ${nextTrack.id}")
        scope.launch {
            delay(250L)
            if (currentTrack?.id == failedTrackId && queueIndex == currentIndex) {
                plannedNext = null
                queueIndex = nextIndex
                play(nextTrack, expandPlayer = expanded)
            }
        }
    }

    private fun showPlaybackFailure(trackId: String?, error: Throwable?) {
        val context = appContext ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (trackId != null && trackId == lastPlaybackFailureTrackId && now - lastPlaybackFailureAtMs < 2_500L) return
        lastPlaybackFailureTrackId = trackId
        lastPlaybackFailureAtMs = now

        val errorText = buildString {
            var cause = error
            var depth = 0
            while (cause != null && depth++ < 8) {
                append(' ').append(cause.message.orEmpty())
                cause = cause.cause
            }
        }.lowercase()
        val isBotChallenge = listOf(
            "sign in to confirm you're not a bot",
            "sign in to confirm you’re not a bot",
            "confirm you're not a bot",
            "confirm you’re not a bot",
            "prove you're not a robot",
            "prove you’re not a robot",
            "login_required",
            "captcha",
            "unusual traffic",
        ).any(errorText::contains) ||
            (("bot" in errorText || "robot" in errorText) &&
                ("sign in" in errorText || "login" in errorText || "confirm" in errorText || "prove" in errorText))
        val showChallengeGuidance = com.darkxvenom.itune.data.sources.PlaybackSources.captchaGuidance.value
        val message = if (isBotChallenge && showChallengeGuidance) {
            "Please log in with your Google account to avoid this playback issue."
        } else {
            "Couldn't play this song. Please try again."
        }
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(UnstableApi::class)
    fun release() {
        TransitionEngine.release()
        progressJob?.cancel()
        loadJob?.cancel()
        lyricsJob?.cancel()
        sleepTimerJob?.cancel()
        AudioEngineManager.release()
        AudioOutputManager.release()
        simultaneousProcessor?.setEnabled(false)
        simultaneousProcessor = null
        exoPlayer?.release()
        exoPlayer = null
        isPlaying = false
        isLoading = false
        playbackState = Player.STATE_IDLE
    }
}
