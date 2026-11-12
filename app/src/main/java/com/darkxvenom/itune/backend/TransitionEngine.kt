package com.darkxvenom.itune.backend

import android.content.Context
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.darkxvenom.itune.audio.LevelProbeProcessor
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.library.SettingsUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Crossfade and AutoMix for iTune.
 *
 * ## How it works
 * iTune plays one song at a time on a single ExoPlayer that carries all the effects
 * (EQ, spatial, 8D, visualiser, output routing). A real overlap needs two songs sounding at once,
 * so the *outgoing* song's last seconds are handed to a second, effect-free "tail" player:
 *
 *  1. Shortly before the end, the tail player buffers the same stream a fraction of a second ahead.
 *  2. When the main player reaches that point, the tail starts and the main player is silenced
 *     inside a ~150 ms micro-overlap, so the hand-off is seamless.
 *  3. The main player is now free: it loads the next song (URL already resolved) and plays it
 *     with all effects, exactly as if the user had skipped.
 *  4. The two players are cross-faded (equal-power) and the tail is stopped.
 *
 * Everything after the transition (seek, pause, queue, now-playing, lyrics) is the normal flow,
 * because the incoming song simply lives on the main player.
 *
 * ## Modes
 *  - **Crossfade**: fixed length from Settings, equal-power curve.
 *  - **AutoMix**: length chosen per song, smoother S-curve, starts early when the outgoing song
 *    is already in a quiet outro, skips the incoming song's silent intro, and balances loudness
 *    of the two songs (measured live by [LevelProbeProcessor]).
 *
 * Any user action that changes playback (skip, tap a song, pause, close) cancels the transition
 * and falls back to the normal behaviour.
 */
@OptIn(UnstableApi::class)
object TransitionEngine {
    private const val TAG = "TransitionEngine"

    private const val RELAY_LEAD_MS = 350L
    private const val RELAY_START_LATENCY_MS = 100L
    private const val RELAY_OVERLAP_MS = 150L
    private const val CROSSFADE_GUARD_MS = 2_200L
    private const val AUTOMIX_GUARD_MS = 4_200L
    private const val PREFETCH_LEAD_MS = 30_000L
    private const val MIN_TRACK_MS = 9_000L
    private const val PREROLL_MAX_MS = 2_000L
    private const val SILENCE_FLOOR = 0.012f
    private const val ABORT_COOLDOWN_MS = 10_000L

    /** Placed first in the main player's audio chain by [MusicPlayerState.init]. */
    val probe = LevelProbeProcessor()

    private enum class Mode { OFF, CROSSFADE, AUTOMIX }

    private data class Plan(
        val fadeMs: Long,
        val guardMs: Long,
        val smoothCurve: Boolean,
        val skipSilentIntro: Boolean,
        val levelMatch: Boolean,
    )

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var appContext: Context? = null
    private var tail: ExoPlayer? = null
    private var monitorJob: Job? = null
    private var runJob: Job? = null
    private var prefetchJob: Job? = null
    private var preferredDevice: AudioDeviceInfo? = null

    @Volatile
    var active: Boolean = false
        private set

    private var serial = 0L
    private var lastTrackId: String? = null
    private var quietTicks = 0

    private var prefetched: Pair<String, String>? = null
    private var prefetchingId: String? = null
    private var prefetchFailedId: String? = null

    private var abortedOutId: String? = null
    private var abortedAtMs = 0L

    // ── Public API ──

    fun init(context: Context) {
        if (monitorJob != null) return
        appContext = context.applicationContext
        monitorJob = scope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "tick failed: ${e.message}")
                }
                delay(200)
            }
        }
    }

    /** Called whenever the main player starts a different song. */
    fun onTrackStarted() {
        probe.resetStats()
        quietTicks = 0
    }

    fun onPreferredDevice(device: AudioDeviceInfo?) {
        preferredDevice = device
    }

    /** Stops any running transition immediately and hands full volume back to the main player. */
    fun cancel() {
        serial++
        runJob?.cancel()
        runJob = null
        if (active) {
            stopTail()
            MusicPlayerState.engineRestoreVolume()
            active = false
        }
    }

    fun release() {
        monitorJob?.cancel()
        monitorJob = null
        prefetchJob?.cancel()
        cancel()
        tail?.release()
        tail = null
    }

    // ── Monitoring ──

    private fun crossfadeMs(state: SettingsUiState): Long =
        (state.crossfadeDuration.substringBefore(' ').toLongOrNull() ?: 0L) * 1_000L

    private fun currentMode(): Mode {
        val state = SettingsStore.state.value
        return when {
            state.autoMix -> Mode.AUTOMIX
            crossfadeMs(state) > 0L -> Mode.CROSSFADE
            else -> Mode.OFF
        }
    }

    private fun buildPlan(mode: Mode, durationMs: Long): Plan = when (mode) {
        Mode.CROSSFADE -> Plan(
            fadeMs = min(crossfadeMs(SettingsStore.state.value), durationMs / 3),
            guardMs = CROSSFADE_GUARD_MS,
            smoothCurve = false,
            skipSilentIntro = false,
            levelMatch = false,
        )
        else -> {
            val base = when {
                durationMs < 120_000L -> 3_000L
                durationMs < 210_000L -> 6_000L
                else -> 8_000L
            }
            Plan(
                fadeMs = min(base, durationMs / 3),
                guardMs = AUTOMIX_GUARD_MS,
                smoothCurve = true,
                skipSilentIntro = true,
                levelMatch = true,
            )
        }
    }

    private fun tick() {
        if (active) return
        val mode = currentMode()
        if (mode == Mode.OFF) return
        val main = MusicPlayerState.enginePlayer() ?: return
        val current = MusicPlayerState.currentTrack ?: return
        if (current.id != lastTrackId) {
            lastTrackId = current.id
            quietTicks = 0
        }
        if (!main.isPlaying) return
        if (MusicPlayerState.engineBusy() || MusicPlayerState.holdingFast) return
        if (MusicPlayerState.repeatMode == Player.REPEAT_MODE_ONE || MusicPlayerState.sleepTimerEndOfTrack) return

        val durationMs = main.duration
        if (durationMs == C.TIME_UNSET || durationMs < MIN_TRACK_MS) return
        val speed = main.playbackParameters.speed.coerceAtLeast(0.25f)
        val remainingMs = ((durationMs - main.currentPosition) / speed).toLong()
        if (remainingMs <= 0L) return

        if (abortedOutId == current.id &&
            SystemClock.elapsedRealtime() - abortedAtMs < ABORT_COOLDOWN_MS
        ) return

        val next = MusicPlayerState.peekNext() ?: return
        if (next.second.id == current.id) return

        val plan = buildPlan(mode, durationMs)
        var fadeMs = plan.fadeMs
        if (fadeMs < 1_000L) return

        // AutoMix: if the song is already sinking into a quiet outro, start now with a shorter blend.
        var early = false
        if (mode == Mode.AUTOMIX && remainingMs in 4_500L..12_000L) {
            val quiet = probe.longRms > 0.02f && probe.shortRms < 0.45f * probe.longRms
            quietTicks = if (quiet) quietTicks + 1 else 0
            if (quietTicks >= 4) {
                early = true
                fadeMs = (remainingMs - 2_200L).coerceIn(1_500L, plan.fadeMs)
            }
        } else {
            quietTicks = 0
        }

        val startThresholdMs = fadeMs + plan.guardMs
        if (!early && remainingMs > startThresholdMs) {
            if (remainingMs <= startThresholdMs + PREFETCH_LEAD_MS) prefetch(next.second)
            return
        }

        prefetch(next.second)
        val url = prefetched?.takeIf { it.first == next.second.id }?.second ?: return
        // Too late to blend properly: let the song end normally.
        if (remainingMs < fadeMs / 2 + 1_200L) return

        active = true
        val mySerial = ++serial
        val finalPlan = plan.copy(fadeMs = fadeMs)
        runJob = scope.launch {
            try {
                runTransition(current, next, url, finalPlan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "transition failed: ${e.message}", e)
                markAborted(current.id)
            } finally {
                if (mySerial == serial) {
                    stopTail()
                    MusicPlayerState.engineRestoreVolume()
                    active = false
                }
            }
        }
    }

    private fun prefetch(track: MusicTrack) {
        if (prefetched?.first == track.id ||
            prefetchingId == track.id ||
            prefetchFailedId == track.id
        ) return
        prefetchingId = track.id
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val url = try {
                MusicBackend.resolveAudioStreamUrl(track.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (prefetchingId == track.id) prefetchingId = null
            if (url.isNullOrBlank()) {
                prefetchFailedId = track.id
            } else {
                prefetched = track.id to url
            }
        }
    }

    // ── The transition itself ──

    private suspend fun runTransition(
        outgoing: MusicTrack,
        next: Pair<Int, MusicTrack>,
        url: String,
        plan: Plan,
    ) {
        val main = MusicPlayerState.enginePlayer() ?: return
        val incoming = next.second

        val outUri = main.currentMediaItem?.localConfiguration?.uri
        val tp = if (outUri != null) ensureTail() else null
        if (outUri == null || tp == null) {
            markAborted(outgoing.id)
            return
        }

        // 1) Prepare the tail player a little ahead of the main player.
        val relayStartMs = main.currentPosition + RELAY_LEAD_MS
        tp.volume = 0f
        tp.playWhenReady = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tp.setPreferredAudioDevice(preferredDevice)
        }
        tp.setMediaItem(
            MediaItem.Builder().setMediaId("tail-${outgoing.id}").setUri(outUri).build(),
            relayStartMs,
        )
        tp.prepare()
        if (!waitFor(main, 2_500L) { tp.playbackState == Player.STATE_READY }) {
            markAborted(outgoing.id)
            return
        }

        // 2) Wait until the main player reaches the same spot, then start the tail.
        if (!waitFor(main, 4_000L) { main.currentPosition >= relayStartMs - RELAY_START_LATENCY_MS }) {
            markAborted(outgoing.id)
            return
        }
        if (!stillOutgoing(main, outgoing.id)) {
            markAborted(outgoing.id)
            return
        }
        val outLevel = probe.longRms
        tp.playbackParameters = main.playbackParameters
        tp.volume = 0f
        tp.play()

        // 3) Micro-overlap so the hand-off from main to tail is inaudible.
        val relayT0 = SystemClock.elapsedRealtime()
        while (true) {
            val k = ((SystemClock.elapsedRealtime() - relayT0).toFloat() / RELAY_OVERLAP_MS).coerceIn(0f, 1f)
            val userVolume = MusicPlayerState.volume
            tp.volume = userVolume * k
            main.volume = userVolume * (1f - k)
            if (k >= 1f) break
            delay(12)
        }
        main.volume = 0f

        // 4) The main player is free: load the next song on it (silent for now).
        MusicPlayerState.startTransitionPlayback(next.first, incoming, url)
        if (!waitFor(main, 8_000L) { main.isPlaying && main.currentMediaItem?.mediaId == incoming.id }) {
            markAborted(outgoing.id)
            return
        }

        // 5) AutoMix: let a silent intro pass before the blend starts.
        if (plan.skipSilentIntro) {
            waitFor(main, PREROLL_MAX_MS) { probe.shortRms > SILENCE_FLOOR }
            if (!stillIncoming(main, incoming.id)) return
        }

        // 6) Equal-power blend.
        var inGain = 1f
        var outGain = 1f
        var targetIn = 1f
        var targetOut = 1f
        var matched = false
        var lastGainIn = 0f
        var clockMs = 0L
        var lastTick = SystemClock.elapsedRealtime()
        var tailEndedEarly = false

        while (clockMs < plan.fadeMs) {
            delay(30)
            if (!stillIncoming(main, incoming.id)) return
            val now = SystemClock.elapsedRealtime()
            val dt = now - lastTick
            lastTick = now
            // Hold the blend while the incoming song is buffering.
            if (main.isPlaying) clockMs += dt
            if (tp.playbackState == Player.STATE_ENDED) {
                tailEndedEarly = true
                break
            }

            val t = (clockMs.toFloat() / plan.fadeMs).coerceIn(0f, 1f)
            val shaped = if (plan.smoothCurve) t * t * (3f - 2f * t) else t
            val angle = shaped * (PI.toFloat() / 2f)
            val gainOut = cos(angle)
            val gainIn = sin(angle)

            if (plan.levelMatch && !matched && clockMs >= 800L) {
                matched = true
                val inLevel = probe.longRms
                if (outLevel > 0.02f && inLevel > 0.02f) {
                    val ratio = outLevel / inLevel
                    if (ratio < 1f) targetIn = max(0.6f, ratio) else targetOut = max(0.6f, 1f / ratio)
                }
            }
            inGain += (targetIn - inGain) * 0.15f
            outGain += (targetOut - outGain) * 0.15f

            val userVolume = MusicPlayerState.volume
            lastGainIn = gainIn * inGain
            main.volume = (userVolume * lastGainIn).coerceIn(0f, 1f)
            tp.volume = (userVolume * gainOut * outGain).coerceIn(0f, 1f)
        }

        // 7) Finish: stop the tail, bring the incoming song to full level smoothly.
        stopTail()
        if (tailEndedEarly) {
            val startGain = lastGainIn
            val t0 = SystemClock.elapsedRealtime()
            while (true) {
                val k = ((SystemClock.elapsedRealtime() - t0) / 300f).coerceIn(0f, 1f)
                val g = startGain + (1f - startGain) * k
                main.volume = (MusicPlayerState.volume * g).coerceIn(0f, 1f)
                if (k >= 1f) break
                delay(20)
                if (!stillIncoming(main, incoming.id)) return
            }
            inGain = 1f
        }
        var easing = inGain
        while (easing < 0.995f) {
            delay(30)
            if (!stillIncoming(main, incoming.id)) return
            easing += (1f - easing) * 0.06f
            main.volume = (MusicPlayerState.volume * easing).coerceIn(0f, 1f)
        }
    }

    // ── Helpers ──

    private fun ensureTail(): ExoPlayer? {
        tail?.let { return it }
        val ctx = appContext ?: return null
        val httpFactory = OkHttpDataSource.Factory(Net.client).setUserAgent(Net.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(ctx, httpFactory)
        val player = ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLooper(Looper.getMainLooper())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false,
            )
            .setHandleAudioBecomingNoisy(false)
            .build()
        tail = player
        return player
    }

    private fun stopTail() {
        tail?.let {
            runCatching {
                it.volume = 0f
                it.playWhenReady = false
                it.stop()
                it.clearMediaItems()
            }
        }
    }

    private fun markAborted(outgoingId: String) {
        abortedOutId = outgoingId
        abortedAtMs = SystemClock.elapsedRealtime()
    }

    /** The main player is still on the outgoing song and the user still wants playback. */
    private fun stillOutgoing(main: ExoPlayer, outgoingId: String): Boolean =
        main.currentMediaItem?.mediaId == outgoingId &&
            MusicPlayerState.currentTrack?.id == outgoingId &&
            main.playWhenReady

    /**
     * The main player is on (or still loading) the incoming song and playback was not paused.
     * While the song is loading, play() leaves playWhenReady false for a moment, which is why
     * [MusicPlayerState.engineBusy] counts as "still wanted".
     */
    private fun stillIncoming(main: ExoPlayer, incomingId: String): Boolean {
        if (MusicPlayerState.currentTrack?.id != incomingId) return false
        val item = main.currentMediaItem?.mediaId
        val loaded = item == incomingId
        val loading = MusicPlayerState.engineBusy()
        return (loaded || loading) && (main.playWhenReady || loading)
    }

    /** Polls [condition] every 15 ms. Returns false on timeout or if the user stopped playback. */
    private suspend fun waitFor(main: ExoPlayer, timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            if (!(main.playWhenReady || MusicPlayerState.engineBusy())) return false
            delay(15)
        }
        return condition()
    }
}
