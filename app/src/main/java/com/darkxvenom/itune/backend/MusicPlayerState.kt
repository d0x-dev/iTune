package com.darkxvenom.itune.backend

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.ishare.IShareSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dedicated music playback controller and state machine for iTune.
 * Manages ExoPlayer lifecycle, stream extraction, background progress, queue, and lyrics.
 */
object MusicPlayerState : Player.Listener {
    private const val TAG = "MusicPlayerState"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var activeExoPlayer: ExoPlayer? = null
    private var standbyExoPlayer: ExoPlayer? = null

    val exoPlayer: ExoPlayer? get() = activeExoPlayer
    val standbyPlayer: ExoPlayer? get() = standbyExoPlayer

    private var appContext: Context? = null
    private var simultaneousProcessor: SimultaneousAudioProcessor? = null
    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var lyricsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var resolvingStream = false
    private var restoredPendingStream = false
    private var lastPersistedAtMs = 0L
    private var isApplyingRemoteSync = false

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
            // Always persist with savedLocally = true so liked tracks stay viewable even after logout
            ctx?.let { LibraryStore.saveLikedSong(it, track, savedLocally = true, isYtmSynced = true) }
            nowLiked = true
        }
        YouTubeLibrarySyncManager.pushSongLikeAsync(track.id, nowLiked)
        ctx?.getSharedPreferences("itune_favorites", Context.MODE_PRIVATE)
            ?.edit()
            ?.putStringSet("favorite_track_ids", favoriteIds.toSet())
            ?.apply()
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
        MusicBackend.init(appContext)
        LibraryStore.ensureInit(appContext)
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

        val httpDataSourceFactory = OkHttpDataSource.Factory(Net.client)
            .setUserAgent(Net.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        val dualProc = SimultaneousAudioProcessor(appContext)
        simultaneousProcessor = dualProc

        fun createRenderers(): DefaultRenderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(
                    arrayOf(
                        AudioEngineManager.djProcessor,
                        AudioEngineManager.spatialProcessor,
                        AudioEngineManager.eightDProcessor,
                        dualProc,
                    ),
                )
                .build()
        }

        val playerAudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Two peer ExoPlayers for seamless equal-power crossfading & automix role-swapping
        playerA = ExoPlayer.Builder(appContext, createRenderers())
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLooper(Looper.getMainLooper())
            .setAudioAttributes(playerAudioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(this@MusicPlayerState)
                volume = this@MusicPlayerState.volume
            }

        playerB = ExoPlayer.Builder(appContext, createRenderers())
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLooper(Looper.getMainLooper())
            .setAudioAttributes(playerAudioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(false)
            .build()
            .apply {
                volume = 0f
            }

        activeExoPlayer = playerA
        standbyExoPlayer = playerB

        AudioOutputManager.init(appContext, dualProc) { targetDevice ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                playerA?.setPreferredAudioDevice(targetDevice)
                playerB?.setPreferredAudioDevice(targetDevice)
            }
        }
        AudioEngineManager.init(
            context = appContext,
            getSessionId = { activeExoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET },
            applyPlaybackParams = { params ->
                if (!holdingFast) {
                    playerA?.playbackParameters = params
                    playerB?.playbackParameters = params
                }
            },
        )

        restorePlaybackSession(appContext)
        startProgressLoop()
        IShareSyncManager.start(appContext)
        MusicCacheManager.init(appContext)
        AutomixManager.init(appContext)
        CrossfadeAudioEngine.init(appContext)
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
                        isLoading = !player.isPlaying && player.playbackState == Player.STATE_BUFFERING
                        val active = currentTrack
                        if (player.isPlaying && active != null) {
                            YouTubePlaybackTracker.onProgress(active.id, positionMs / 1000L)
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
    ) {
        com.darkxvenom.itune.radio.OnlineRadioController.pauseIfPlaying()
        if (currentTrack != null && currentTrack?.id != track.id) {
            YouTubePlaybackTracker.onTrackChanged(positionMs / 1000L)
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
        currentTrack = track
        appContext?.let { LibraryStore.recordRecentSong(it, track) }
        CrossfadeAudioEngine.cancelCrossfade(resetVolume = true)
        AutomixManager.fetchAutomixForTrack(track)

        // Fetch synchronized lyrics unless already loaded for this restored track
        if (!sameTrack || (lyrics.isEmpty() && !lyricsLoading)) {
            fetchLyricsForCurrentTrack(track)
        }

        // Pre-warm thumbnail in Coil cache with ultra-high quality
        appContext?.let { ctx -> prewarmTrackThumbnail(ctx, track) }

        if (newQueue != null && newQueue !== queue) {
            val snapshot = newQueue.toList()
            queue.clear()
            queue.addAll(snapshot)
            val idx = queue.indexOfFirst { it.id == track.id }
            queueIndex = if (idx >= 0) idx else 0
        } else {
            val idx = queue.indexOfFirst { it.id == track.id }
            if (idx >= 0) {
                queueIndex = idx
            } else {
                queue.add(track)
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

                // 1. Play directly from offline audio cache if available
                val cachedFile = MusicCacheManager.getCachedAudioFile(track.id)
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    MusicCacheManager.markTrackPlayed(track.id)
                    exoPlayer?.let { player ->
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri(Uri.fromFile(cachedFile))
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
                        preCacheNextTrack()
                    }
                    return@launch
                }

                val streamUrl = MusicBackend.resolveAudioStreamUrl(track.id)
                if (streamUrl.isNullOrBlank()) {
                    Log.e(TAG, "Failed to resolve stream URL for track: ${track.id}")
                    resolvingStream = false
                    isLoading = false
                    return@launch
                }

                // 2. Start live caching audio in background while streaming
                MusicCacheManager.startLiveCaching(track, streamUrl)
                preCacheNextTrack()

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
                if (currentTrack?.id == track.id) {
                    resolvingStream = false
                    isLoading = false
                }
            }
        }
    }

    /**
     * Seamlessly swaps peer players at crossfade/automix handoff (progress >= 0.5).
     * Transference of audio focus, session listener, queue index, lyrics, and metadata
     * occurs without stopping, seeking, or re-buffering the incoming player.
     */
    fun handOffToIncoming(incomingTrack: MusicTrack) {
        val outgoing = activeExoPlayer ?: return
        val incoming = standbyExoPlayer ?: return

        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // 1. Order matters: release focus on outgoing BEFORE incoming claims it
        outgoing.setAudioAttributes(attrs, /* handleAudioFocus = */ false)
        outgoing.setHandleAudioBecomingNoisy(false)

        incoming.setAudioAttributes(attrs, /* handleAudioFocus = */ true)
        incoming.setHandleAudioBecomingNoisy(true)

        // 2. Transfer session listener to incoming player
        outgoing.removeListener(this@MusicPlayerState)
        incoming.addListener(this@MusicPlayerState)

        // 3. Swap peer player roles
        activeExoPlayer = incoming
        standbyExoPlayer = outgoing

        // 4. Advance queue index to incoming track
        val nextIdx = queue.indexOfFirst { it.id == incomingTrack.id }
        if (nextIdx >= 0) {
            queueIndex = nextIdx
        } else {
            queue.add(incomingTrack)
            queueIndex = queue.lastIndex
        }

        // 5. Update UI states and session metadata
        currentTrack = incomingTrack
        positionMs = incoming.currentPosition.coerceAtLeast(0L)
        durationMs = if (incoming.duration > 0L) incoming.duration else (incomingTrack.durationSec * 1000L)
        isLoading = false
        isPlaying = incoming.isPlaying

        appContext?.let { ctx ->
            LibraryStore.recordRecentSong(ctx, incomingTrack)
            prewarmTrackThumbnail(ctx, incomingTrack)
        }

        fetchLyricsForCurrentTrack(incomingTrack)
        savePlaybackSession(sync = false)

        preCacheNextTrack()
        AutomixManager.fetchAutomixForTrack(incomingTrack)
    }

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
        CrossfadeAudioEngine.onPause()
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        val track = currentTrack ?: return
        if (restoredPendingStream) {
            if (!resolvingStream) {
                play(track = track, startPositionMs = positionMs, expandPlayer = expanded)
            }
            return
        }
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                CrossfadeAudioEngine.onPause()
                player.pause()
            } else {
                com.darkxvenom.itune.radio.OnlineRadioController.pauseIfPlaying()
                player.play()
                CrossfadeAudioEngine.onResume()
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

    fun nextUpTrack(): MusicTrack? {
        if (queue.isEmpty()) return null
        val nextIndex = if (shuffleEnabled && queue.size > 1) {
            val valid = queue.indices.filter { it != queueIndex }
            if (valid.isNotEmpty()) valid.firstOrNull() else (queueIndex + 1) % queue.size
        } else {
            (queueIndex + 1).takeIf { it in queue.indices }
        } ?: return null
        return queue.getOrNull(nextIndex)
    }

    fun preCacheNextTrack() {
        val next = nextUpTrack() ?: return
        if (MusicCacheManager.cachingEnabled.value && !MusicCacheManager.isTrackCached(next.id)) {
            scope.launch(Dispatchers.IO) {
                MusicCacheManager.cacheTrackOnDemand(next)
            }
        }
    }

    fun skipNext() {
        if (queue.isEmpty()) return
        CrossfadeAudioEngine.cancelCrossfade(resetVolume = true)
        val nextIndex = if (shuffleEnabled && queue.size > 1) {
            (queue.indices.filter { it != queueIndex }).randomOrNull() ?: queueIndex
        } else {
            val sequential = queueIndex + 1
            when {
                sequential < queue.size -> sequential
                repeatMode == Player.REPEAT_MODE_ALL -> 0
                else -> return
            }
        }
        val nextTrack = queue.getOrNull(nextIndex) ?: return
        queueIndex = nextIndex
        play(nextTrack, expandPlayer = expanded)
    }

    fun skipPrevious(forcePrevious: Boolean = false) {
        if (queue.isEmpty()) return
        CrossfadeAudioEngine.cancelCrossfade(resetVolume = true)
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
        CrossfadeAudioEngine.cancelCrossfade(resetVolume = true)
        val safePos = positionMs.coerceAtLeast(0L)
        this.positionMs = safePos
        if (!restoredPendingStream) {
            exoPlayer?.seekTo(safePos)
        }
        savePlaybackSession(sync = false)
        if (!isApplyingRemoteSync) IShareSyncManager.onLocalPlaybackEvent()
    }

    fun adjustVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        CrossfadeAudioEngine.onUserVolumeChanged(volume)
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        exoPlayer?.shuffleModeEnabled = shuffleEnabled
        savePlaybackSession(sync = false)
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
        YouTubePlaybackTracker.onPlaybackFinished(positionMs / 1000L)
        CrossfadeAudioEngine.cancelCrossfade(resetVolume = true)
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
        this.isPlaying = isPlaying
        AudioEngineManager.updatePlayingState(isPlaying)
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
            simultaneousProcessor?.onPause()
            if (!restoredPendingStream && !resolvingStream) {
                savePlaybackSession(sync = false)
            }
        }
        if (!isApplyingRemoteSync && !resolvingStream) {
            IShareSyncManager.onLocalPlaybackEvent()
        }
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
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                isLoading = !isPlaying
            }
            Player.STATE_READY -> {
                isLoading = false
                durationMs = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
            }
            Player.STATE_ENDED -> {
                if (CrossfadeAudioEngine.isTransitionActive()) {
                    // Suppress premature skipping when crossfade or automix handoff is handling transition
                    return
                }
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

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer error: ${error.errorCodeName} - ${error.message}", error)
        resolvingStream = false
        isLoading = false
    }

    @OptIn(UnstableApi::class)
    fun release() {
        progressJob?.cancel()
        loadJob?.cancel()
        lyricsJob?.cancel()
        sleepTimerJob?.cancel()
        CrossfadeAudioEngine.release()
        AutomixManager.clearAutomix()
        AudioEngineManager.release()
        AudioOutputManager.release()
        simultaneousProcessor?.setEnabled(false)
        simultaneousProcessor = null
        playerA?.release()
        playerA = null
        playerB?.release()
        playerB = null
        activeExoPlayer = null
        standbyExoPlayer = null
    }
}
