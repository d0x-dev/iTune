package com.darkxvenom.itune.data.timetravel

import android.content.Context
import android.util.Log
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

object TimeTravelPlaybackTracker {

    private const val TAG = "TimeTravelTracker"
    private const val MIN_QUALIFYING_SECONDS = 15L // Minimum 15s to count as a verified play

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentSession: ActiveSession? = null
    private var periodicFlushJob: Job? = null

    private data class ActiveSession(
        val sessionId: String,
        val track: MusicTrack,
        val playlistContext: String?,
        val startTimeMs: Long,
        var lastPlayStateChangeNano: Long,
        var isPlaying: Boolean,
        var accumulatedDurationSec: Long = 0L,
        var hasCommittedToDb: Boolean = false,
    )

    fun onTrackStarted(context: Context, track: MusicTrack, playlistContext: String? = null) {
        TimeTravelRepository.ensureInit(context)
        // Flush previous session if any
        flushCurrentSession(completed = false)

        val nowMs = System.currentTimeMillis()
        val nowNano = System.nanoTime()

        currentSession = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            track = track,
            playlistContext = playlistContext,
            startTimeMs = nowMs,
            lastPlayStateChangeNano = nowNano,
            isPlaying = true,
            accumulatedDurationSec = 0L,
            hasCommittedToDb = false,
        )

        startPeriodicFlush()
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        val session = currentSession ?: return
        val nowNano = System.nanoTime()

        synchronized(session) {
            if (session.isPlaying && !isPlaying) {
                // Was playing, now paused: add elapsed time
                val elapsedNano = nowNano - session.lastPlayStateChangeNano
                val elapsedSec = elapsedNano / 1_000_000_000L
                if (elapsedSec > 0) {
                    session.accumulatedDurationSec += elapsedSec
                }
                session.isPlaying = false
                session.lastPlayStateChangeNano = nowNano
                // Flush current state on pause
                commitSessionIfQualified(session, completed = false)
            } else if (!session.isPlaying && isPlaying) {
                // Was paused, now resumed
                session.isPlaying = true
                session.lastPlayStateChangeNano = nowNano
            }
        }
    }

    fun onTrackCompleted() {
        flushCurrentSession(completed = true)
    }

    fun onTrackChanged() {
        flushCurrentSession(completed = false)
    }

    private fun startPeriodicFlush() {
        periodicFlushJob?.cancel()
        periodicFlushJob = scope.launch {
            while (isActive) {
                delay(10_000L) // check every 10 seconds
                val session = currentSession ?: break
                synchronized(session) {
                    if (session.isPlaying) {
                        val nowNano = System.nanoTime()
                        val elapsedNano = nowNano - session.lastPlayStateChangeNano
                        val elapsedSec = elapsedNano / 1_000_000_000L
                        if (elapsedSec > 0) {
                            session.accumulatedDurationSec += elapsedSec
                            session.lastPlayStateChangeNano = nowNano
                        }
                    }
                    commitSessionIfQualified(session, completed = false)
                }
            }
        }
    }

    private fun flushCurrentSession(completed: Boolean) {
        periodicFlushJob?.cancel()
        periodicFlushJob = null

        val session = currentSession ?: return
        currentSession = null

        synchronized(session) {
            if (session.isPlaying) {
                val nowNano = System.nanoTime()
                val elapsedNano = nowNano - session.lastPlayStateChangeNano
                val elapsedSec = elapsedNano / 1_000_000_000L
                if (elapsedSec > 0) {
                    session.accumulatedDurationSec += elapsedSec
                }
                session.isPlaying = false
            }

            val isFullyCompleted = completed || (session.track.durationSec > 0 &&
                session.accumulatedDurationSec >= (session.track.durationSec * 0.80).toLong())

            commitSessionIfQualified(session, completed = isFullyCompleted)
        }
    }

    private fun commitSessionIfQualified(session: ActiveSession, completed: Boolean) {
        // Only record if listened for at least MIN_QUALIFYING_SECONDS or completed a short track
        if (session.accumulatedDurationSec < MIN_QUALIFYING_SECONDS && !completed) {
            return
        }

        val record = PlaybackRecord(
            sessionId = session.sessionId,
            songId = session.track.id,
            songTitle = session.track.title,
            artistName = session.track.artists.ifBlank { "Unknown Artist" },
            artistId = session.track.artistId,
            albumName = session.track.album,
            albumId = session.track.albumId,
            playlistName = session.playlistContext ?: "Library",
            playlistId = null,
            artworkUrl = session.track.thumbnailUrl,
            timestamp = session.startTimeMs,
            durationSec = maxOf(1L, session.accumulatedDurationSec),
            completed = completed,
        )

        TimeTravelRepository.recordPlayback(record)
        session.hasCommittedToDb = true
    }
}
