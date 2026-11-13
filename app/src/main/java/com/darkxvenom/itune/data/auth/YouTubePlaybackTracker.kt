package com.darkxvenom.itune.data.auth

import android.content.Context
import android.util.Log
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Registers audible plays against the signed-in account's YouTube Music history
 * using the three-ping sequence (`videostatsPlaybackUrl`, `atrUrl`, `videostatsWatchtimeUrl`)
 * so `FEmusic_history` and personalized recommendations reflect what was played in iTune.
 */
object YouTubePlaybackTracker {
    private const val TAG = "YtmPlaybackTracker"
    private const val REPORT_INTERVAL_SECONDS = 30L
    private const val OPEN_ATTEMPTS = 3
    private const val OPEN_RETRY_DELAY_MS = 2_000L
    private val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")

    private class Session(
        var videoId: String,
        val cpn: String,
        val tracking: ArtistInnertubeClient.PlaybackTracking,
    ) {
        var reportedSeconds = 0L
        var flushingTo = 0L
        var atrSent = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Mutex()

    @Volatile
    private var session: Session? = null

    @Volatile
    private var opening: String? = null

    fun onPlaying(context: Context, track: MusicTrack) {
        val videoId = track.id
        if (!VIDEO_ID.matches(videoId)) return
        if (!GoogleAuthStore.isSignedIn) return
        if (!SettingsStore.state.value.useListeningHistory) return
        if (SettingsStore.isPauseListeningHistory()) return
        if (session?.videoId == videoId || opening == videoId) return

        opening = videoId
        val appContext = context.applicationContext
        scope.launch {
            try {
                openWithRetries(appContext, track)
            } finally {
                if (opening == videoId) opening = null
            }
        }
    }

    fun onTrackChanged(positionSeconds: Long) {
        val closing = session ?: return
        session = null
        scope.launch {
            runCatching { flush(closing, positionSeconds, final = true) }
                .onFailure { Log.w(TAG, "final watchtime ping failed: ${it.message}") }
        }
    }

    fun onProgress(videoId: String, positionSeconds: Long) {
        val current = session ?: return
        if (current.videoId != videoId) return
        if (!current.atrSent && positionSeconds >= current.tracking.atrAfterSeconds) {
            current.atrSent = true
            current.tracking.atrUrl?.let { atrUrl ->
                scope.launch {
                    runCatching {
                        val code = ArtistInnertubeClient.pingAtr(atrUrl, current.cpn)
                        Log.d(TAG, "atr ping for $videoId -> HTTP $code")
                    }.onFailure {
                        Log.w(TAG, "atr ping failed for $videoId: ${it.message}")
                    }
                }
            }
        }
        if (positionSeconds - maxOf(current.reportedSeconds, current.flushingTo) < REPORT_INTERVAL_SECONDS) return
        scope.launch {
            runCatching { flush(current, positionSeconds) }
                .onFailure { Log.w(TAG, "watchtime ping failed for $videoId: ${it.message}") }
        }
    }

    fun onPlaybackFinished(positionSeconds: Long) {
        val closing = session ?: return
        session = null
        scope.launch {
            withContext(NonCancellable) {
                runCatching { flush(closing, positionSeconds, final = true) }
                    .onFailure { Log.w(TAG, "closing watchtime failed: ${it.message}") }
            }
        }
    }

    private suspend fun openWithRetries(appContext: Context, track: MusicTrack) {
        repeat(OPEN_ATTEMPTS) { attempt ->
            if (opening != track.id) return
            val settled = try {
                open(appContext, track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "playback tracking failed (${attempt + 1}/$OPEN_ATTEMPTS): ${e.message}")
                false
            }
            if (settled) return
            if (attempt < OPEN_ATTEMPTS - 1) delay(OPEN_RETRY_DELAY_MS)
        }
    }

    private suspend fun open(appContext: Context, track: MusicTrack): Boolean = lock.withLock {
        val sts = ArtistInnertubeClient.signatureTimestamp(track.id) ?: return@withLock false
        val tracking = ArtistInnertubeClient.playbackTracking(track.id, sts) ?: return@withLock true
        val cpn = ArtistInnertubeClient.newCpn()
        val status = ArtistInnertubeClient.pingPlayback(tracking.playbackUrl, cpn)
        session = Session(track.id, cpn, tracking)
        Log.d(TAG, "YTM history playback ping for ${track.id} -> HTTP $status")
        if (status in 200..299) {
            LibraryStore.prependYtmHistoryTrack(appContext, track)
        }
        true
    }

    private suspend fun flush(target: Session, positionSeconds: Long, final: Boolean = false) {
        val url = target.tracking.watchtimeUrl ?: return
        if (!final && positionSeconds <= target.reportedSeconds) return
        target.flushingTo = maxOf(target.flushingTo, positionSeconds)
        lock.withLock {
            ArtistInnertubeClient.pingWatchtime(url, target.cpn, positionSeconds, final)
            target.reportedSeconds = maxOf(target.reportedSeconds, positionSeconds)
        }
    }
}
