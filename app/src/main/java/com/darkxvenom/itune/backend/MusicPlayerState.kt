package com.darkxvenom.itune.backend

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dedicated music playback controller and state machine for iTune.
 * Manages ExoPlayer lifecycle, stream extraction, background progress, and UI state.
 */
object MusicPlayerState : Player.Listener {
    private const val TAG = "MusicPlayerState"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null
    private var progressJob: Job? = null
    private var loadJob: Job? = null
    private var resolvingStream = false

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

    var playbackSpeed by mutableFloatStateOf(1f)
        private set

    var holdingFast by mutableStateOf(false)
        private set

    private var preHoldSpeed: Float = 1f

    // Sheet / Fullscreen Dialog State
    var expanded by mutableStateOf(false)

    fun init(context: Context) {
        if (exoPlayer != null) return
        val appContext = context.applicationContext
        this.appContext = appContext
        MusicBackend.init(appContext)

        val httpDataSourceFactory = OkHttpDataSource.Factory(Net.client)
            .setUserAgent(Net.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(appContext)
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
            .build()
            .apply {
                addListener(this@MusicPlayerState)
                volume = this@MusicPlayerState.volume
            }

        startProgressLoop()
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    if (dur > 0) {
                        durationMs = dur
                    }
                    isPlaying = player.isPlaying
                    isLoading = !player.isPlaying && (resolvingStream || player.playbackState == Player.STATE_BUFFERING)
                }
                delay(200)
            }
        }
    }

    fun play(track: MusicTrack, newQueue: List<MusicTrack>? = null) {
        expanded = true
        loadJob?.cancel()

        exoPlayer?.let { player ->
            player.playWhenReady = false
            player.pause()
        }
        resolvingStream = true
        isPlaying = false
        isLoading = true
        positionMs = 0L
        durationMs = 0L
        currentTrack = track

        // Pre-warm thumbnail in Coil cache
        appContext?.let { ctx ->
            val thumb = track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl
            if (!thumb.isNullOrBlank()) {
                val preReq = ImageRequest.Builder(ctx)
                    .data(thumb)
                    .allowHardware(false)
                    .size(160)
                    .build()
                ctx.imageLoader.enqueue(preReq)
            }
        }

        loadJob = scope.launch {
            try {
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

                val streamUrl = MusicBackend.resolveAudioStreamUrl(track.id)
                if (streamUrl.isNullOrBlank()) {
                    Log.e(TAG, "Failed to resolve stream URL for track: ${track.id}")
                    resolvingStream = false
                    isLoading = false
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
                                .setArtworkUri(track.thumbnailUrl?.let(Uri::parse))
                                .build()
                        )
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.setPlaybackSpeed(if (holdingFast) 2f else playbackSpeed)
                    player.playWhenReady = true
                    player.play()
                    resolvingStream = false
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

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun skipNext() {
        if (queue.isEmpty()) return
        val nextIdx = (queueIndex + 1).coerceAtMost(queue.lastIndex)
        if (nextIdx != queueIndex && nextIdx in queue.indices) {
            queueIndex = nextIdx
            play(queue[nextIdx])
        }
    }

    fun skipPrevious() {
        if (queue.isEmpty()) return
        if (positionMs > 3000L) {
            seekTo(0L)
            return
        }
        val prevIdx = (queueIndex - 1).coerceAtLeast(0)
        if (prevIdx in queue.indices) {
            queueIndex = prevIdx
            play(queue[prevIdx])
        }
    }

    fun seekTo(positionMs: Long) {
        this.positionMs = positionMs
        exoPlayer?.seekTo(positionMs)
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

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        if (isPlaying) {
            isLoading = false
        }
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
                skipNext()
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

    fun release() {
        progressJob?.cancel()
        loadJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}
