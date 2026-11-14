package com.darkxvenom.itune.audio

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.darkxvenom.itune.backend.LyricsLine
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Controller for the "Low-Pass Filtering" dynamic audio effect.
 *
 * Requirements:
 * - Operates reliably on ALL songs that have time-synced lyrics (MusicPlayerState.lyrics).
 * - Multi-tier hook detection:
 *     1. Identifies the lyric line repeated the highest number of times in the song (the primary chorus/hook).
 *     2. If lines have slight variations, matches repeating 3+ word hook phrases.
 *     3. Failsafe: picks the chorus sweet-spot lines (~35%-55% into the song) so EVERY synced-lyrics song activates.
 * - Chooses an upcoming occurrence so it triggers during normal listening without requiring 3+ minute waits.
 * - If lyrics arrive late during playback, keeps scanning and schedules an upcoming occurrence.
 * - Instantly applies the Low-Pass Filter Equalizer curve (+0.3 dB @ 60Hz, +15.0 dB @ 230Hz, -15.0 dB @ 910Hz, 3kHz, 14kHz)
 *   with NO smoothness and NO pause/gap in audio.
 * - Holds the effect active for ~10 to 12 seconds (~11.0s).
 * - When 10-12s elapses, restores the user's previous Equalizer settings back SMOOTHLY and SLOWLY over ~4.2 seconds
 *   using a Cosine ease-in-out S-curve and 60ms step intervals so the frequencies open up gradually.
 */
@OptIn(UnstableApi::class)
object LowPassFilterController {
    private const val TAG = "LowPassFilter"
    private const val FALLBACK_TARGET_LABEL = "Instrumental passage"
    private const val FALLBACK_TARGET_FRACTION = 0.42f
    private const val FALLBACK_TARGET_LEAD_MS = 12_000L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scanJob: Job? = null
    private var effectJob: Job? = null

    private var currentTrackId: String? = null
    private var targetTimeMs: Long? = null
    private var triggeredForTrack = false
    private var afterglowTriggeredForTrack = false
    private var afterglowRestartPending = false
    private var untimedFallbackTriggered: Boolean = false
    private var lastPositionMs: Long = 0L
    var targetLyricLineText: String = ""
        private set

    private data class SavedEq(
        val wasEnabled: Boolean,
        val preset: String,
        val bandsMb: List<Int>,
    )
    private var savedEqState: SavedEq? = null
    private var lastScanErrorAtMs = 0L

    // Exact preset from user screenshot:
    // 60 Hz: +0.3 dB (+30 mB), 230 Hz: +15.0 dB (+1500 mB), 910 Hz: -15.0 dB (-1500 mB), 3 kHz: -15.0 dB (-1500 mB), 14 kHz: -15.0 dB (-1500 mB)
    val LOW_PASS_BANDS: List<Int> = listOf(30, 1500, -1500, -1500, -1500)

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        start()
    }

    fun start() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            while (isActive) {
                delay(200)
                try {
                    tick()
                } catch (error: Exception) {
                    val now = System.currentTimeMillis()
                    if (now - lastScanErrorAtMs >= 5_000L) {
                        lastScanErrorAtMs = now
                        Log.e(TAG, "Lyrics scan failed", error)
                    }
                }
            }
        }
    }

    private fun tick() {
        val engineState = AudioEngineManager.state.value
        val locked = engineState.bypassAllEffects || (engineState.bitPerfectEnabled && engineState.bitPerfectActive)
        if (locked) {
            if (engineState.lowPassFilterActive) cancelActiveEffect()
            targetTimeMs = null
            return
        }

        val track = MusicPlayerState.currentTrack
        val trackId = track?.id

        if (trackId == null) {
            if (currentTrackId != null) {
                resetForNewTrack(null)
            }
            return
        }

        if (currentTrackId != trackId) {
            resetForNewTrack(trackId)
            Log.d(TAG, "Monitoring track ${track.title} (${track.id})")
        }

        val lyrics = MusicPlayerState.lyrics
        val positionMs = MusicPlayerState.positionMs
        val lyricsPositionMs = (positionMs + MusicPlayerState.lyricsOffsetMs).coerceAtLeast(0L)
        val isPlaying = MusicPlayerState.isPlaying

        val afterglowMs = SettingsStore.normalizeAfterglowDuration(SettingsStore.state.value.afterglowDuration)
            .removeSuffix(" sec").toLongOrNull()?.times(1000L) ?: 0L
        if (afterglowMs > 0L) {
            // Repeat-one restarts the same MediaItem, so neither the media ID nor every
            // ExoPlayer transition callback is guaranteed to change. Detect the actual
            // end-to-start position rewind here as a second, callback-independent signal.
            // This also catches the case where a discontinuity callback arrives while the
            // observable player position still reports the old end position.
            val repeatedToStart = MusicPlayerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE &&
                positionMs <= 2_000L &&
                lastPositionMs > 5_000L
            if (repeatedToStart) {
                if (afterglowTriggeredForTrack || AudioEngineManager.state.value.lowPassFilterActive) {
                    cancelActiveEffect()
                }
                afterglowTriggeredForTrack = false
                afterglowRestartPending = false
                targetTimeMs = null
                Log.i(TAG, "Repeat-one rewound '${track.title}'; restored EQ and armed the next Afterglow cycle")
            }
            lastPositionMs = positionMs
            targetTimeMs = null
            if (afterglowRestartPending) {
                // Player position can still report the outgoing end position for one progress
                // tick after ExoPlayer loops the same item. Wait for the actual rewind before
                // rearming, otherwise the final-seconds condition immediately fires again.
                if (positionMs > 2_000L) return
                afterglowRestartPending = false
            }
            if (AudioEngineManager.state.value.lowPassFilterActive || afterglowTriggeredForTrack) return
            val duration = MusicPlayerState.durationMs.takeIf { it > 0L }
                ?: (track.durationSec.takeIf { it > 0L }?.times(1000L) ?: 0L)
            val remainingMs = duration - positionMs
            if (isPlaying && duration > 0L && remainingMs in 1L..afterglowMs) {
                targetLyricLineText = "Afterglow"
                afterglowTriggeredForTrack = true
                triggerEffect(holdUntilTrackChange = true)
            }
            return
        }
        afterglowTriggeredForTrack = false

        val enabled = engineState.lowPassFilterEnabled
        if (!enabled) {
            if (AudioEngineManager.state.value.lowPassFilterActive) cancelActiveEffect()
            targetTimeMs = null
            return
        }

        // One hook sweep per track. Keep this latched through later chorus repeats and seeks;
        // the next track (or explicitly toggling the feature off/on) rearms it.
        if (triggeredForTrack) return

        // Track rewind / loop replay detection: reset if song looped to beginning or user scrubbed back
        val isLoopedToStart = (positionMs <= 2000L && lastPositionMs > 4000L)
        val isSeekedBack = (positionMs < lastPositionMs - 3500L)
        if (isLoopedToStart || isSeekedBack) {
            untimedFallbackTriggered = false
            targetTimeMs = null
        }
        lastPositionMs = positionMs

        // Do not stack sweeps. This track's single trigger is latched even after restoration.
        if (AudioEngineManager.state.value.lowPassFilterActive) {
            return
        }

        // Only timestamped lyrics can schedule the repeated hook. Plain/untimed lyric text
        // uses a timed fallback too, so it never fires immediately at timestamp zero.
        val hasSyncedLyrics = lyrics.any { it.timeMs > 0L }
        if (!hasSyncedLyrics) {
            if (MusicPlayerState.lyricsLoading || untimedFallbackTriggered) return
            if (targetLyricLineText != FALLBACK_TARGET_LABEL) {
                targetTimeMs = null
            }
            if (targetTimeMs == null) {
                val durationMs = MusicPlayerState.durationMs.takeIf { it > 0L }
                    ?: ((track.durationSec * 1000L).takeIf { it > 0L } ?: 0L)
                val fallbackTarget = if (durationMs > 0L) {
                    (durationMs * FALLBACK_TARGET_FRACTION).toLong()
                        .coerceAtLeast(positionMs + 1_000L)
                        .coerceAtMost(durationMs)
                } else {
                    positionMs + FALLBACK_TARGET_LEAD_MS
                }
                targetTimeMs = minOf(fallbackTarget, positionMs + FALLBACK_TARGET_LEAD_MS)
                targetLyricLineText = FALLBACK_TARGET_LABEL
            }
        } else if (targetLyricLineText == FALLBACK_TARGET_LABEL) {
            // Lyrics can arrive after the fallback is scheduled; prefer the real hook when available.
            targetTimeMs = null
            targetLyricLineText = ""
        }

        // If target timestamp is not selected yet, scan lyrics and select the hook occurrence.
        if (targetTimeMs == null && hasSyncedLyrics) {
            val candidate = findTargetLyricOccurrence(lyrics, lyricsPositionMs)
            if (candidate != null) {
                targetTimeMs = candidate.timeMs
                targetLyricLineText = candidate.text
                Log.d(TAG, "Selected hook at ${candidate.timeMs}ms: ${candidate.text}")
            }
        }

        val target = targetTimeMs ?: return
        val delta = target - if (hasSyncedLyrics) lyricsPositionMs else positionMs

        // Check if playback reached target timestamp (triggers right as line starts singing)
        if (isPlaying) {
            if (delta in -600L..400L) {
                if (!hasSyncedLyrics) untimedFallbackTriggered = true
                targetTimeMs = null
                triggerEffect()
            } else if (delta < -2500L) {
                // User scrubbed/jumped forward past the target, pick another future occurrence if available
                val nextCandidate = if (hasSyncedLyrics) findTargetLyricOccurrence(lyrics, lyricsPositionMs) else null
                if (nextCandidate != null && nextCandidate.timeMs > lyricsPositionMs) {
                    targetTimeMs = nextCandidate.timeMs
                    targetLyricLineText = nextCandidate.text
                } else {
                    // If playback starts after all hook occurrences, allow one late-song
                    // fallback sweep, then wait for a rewind or the next track.
                    targetTimeMs = null
                    if (!untimedFallbackTriggered) {
                        untimedFallbackTriggered = true
                        triggerEffect()
                    }
                }
            }
        }
    }

    /**
     * Scans time-synced lyrics with multi-tier failsafes to guarantee a target hook is ALWAYS found:
     * Tier 1: Line with highest exact repetition count.
     * Tier 2: Lines containing repeating 3+ word hook phrases.
     * Tier 3: Chorus sweet-spot lines (~35%-55% into the track).
     */
    fun findTargetLyricOccurrence(lyrics: List<LyricsLine>, currentPositionMs: Long): LyricsLine? {
        if (lyrics.isEmpty()) return null

        // 1. Clean valid lines
        val validLines = lyrics.filter { line ->
            line.timeMs >= 0L && isValidLyricLine(line.text)
        }
        val candidateLines = if (validLines.isNotEmpty()) {
            validLines
        } else {
            lyrics.filter { it.timeMs >= 0L && it.text.trim().isNotBlank() }
        }
        if (candidateLines.isEmpty()) return null

        // Group lines by normalized text
        val grouped = candidateLines.groupBy { normalizeLyricLine(it.text) }
        val repeated = grouped.filter { it.value.size >= 2 }

        val chosenOccurrences: List<LyricsLine> = when {
            repeated.isNotEmpty() -> {
                // Tier 1: Highest exact repetition count (hook/chorus)
                val maxCount = repeated.maxOf { it.value.size }
                val topCandidates = repeated.filter { it.value.size == maxCount }
                topCandidates.values.flatten().sortedBy { it.timeMs }
            }
            candidateLines.size >= 4 -> {
                // Tier 2: Repeating 3+ word phrase matches
                val phraseMatches = findLinesWithRepeatedPhrases(candidateLines)
                if (phraseMatches.isNotEmpty()) {
                    phraseMatches
                } else {
                    // Tier 3: Chorus sweet spot lines (~35% to 55% of the song)
                    val midStart = (candidateLines.size * 0.35).toInt().coerceIn(candidateLines.indices)
                    val midEnd = (candidateLines.size * 0.55).toInt().coerceIn(candidateLines.indices)
                    candidateLines.subList(midStart, (midEnd + 1).coerceAtMost(candidateLines.size))
                }
            }
            else -> {
                candidateLines
            }
        }

        // Prioritize upcoming occurrences (at least 400ms ahead)
        val upcoming = chosenOccurrences.filter { it.timeMs > currentPositionMs + 400L }
        return if (upcoming.isNotEmpty()) {
            // Pick earliest upcoming occurrence so user hears the effect without long waits
            upcoming.first()
        } else {
            // If all occurrences of this hook have passed, check if ANY candidate line is upcoming
            val anyUpcoming = candidateLines.filter { it.timeMs > currentPositionMs + 600L }
            if (anyUpcoming.isNotEmpty()) {
                anyUpcoming.first()
            } else {
                candidateLines.lastOrNull()
            }
        }
    }

    private fun findLinesWithRepeatedPhrases(lines: List<LyricsLine>): List<LyricsLine> {
        val lineWords = lines.map { line ->
            normalizeLyricLine(line.text).split(" ").filter { it.length >= 3 }
        }
        val phraseCounts = mutableMapOf<String, Int>()
        for (words in lineWords) {
            for (i in 0..words.size - 3) {
                val phrase = "${words[i]} ${words[i + 1]} ${words[i + 2]}"
                phraseCounts[phrase] = (phraseCounts[phrase] ?: 0) + 1
            }
        }
        val repeatedPhrases = phraseCounts.filter { it.value >= 2 }
        if (repeatedPhrases.isEmpty()) return emptyList()
        val bestPhrase = repeatedPhrases.maxByOrNull { it.value }?.key ?: return emptyList()
        return lines.filter { normalizeLyricLine(it.text).contains(bestPhrase) }
    }

    private fun normalizeLyricLine(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace(Regex("""\[.*?\]"""), "") // Remove bracketed tags like [Chorus], [Verse]
            // The old nested character class is rejected by Android ICU (PatternSyntaxException),
            // which aborted every scan tick before a target lyric could be selected.
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isValidLyricLine(text: String): Boolean {
        val norm = normalizeLyricLine(text)
        if (norm.length < 3) return false
        val noiseMarkers = setOf(
            "instrumental", "music", "intro", "outro", "verse", "chorus",
            "hook", "bridge", "solo", "guitar solo", "applause", "cheering",
            "beat drop", "drop", "interlude", "prechorus", "pre chorus"
        )
        return norm !in noiseMarkers
    }

    private fun triggerEffect(holdUntilTrackChange: Boolean = false) {
        effectJob?.cancel()
        effectJob = scope.launch {
            // 1. Capture current EQ state
            val currentState = AudioEngineManager.state.value
            savedEqState = SavedEq(
                wasEnabled = currentState.eqEnabled,
                preset = currentState.eqPreset,
                bandsMb = currentState.eqBandsMb.toList(),
            )

            // 2. Instantly apply Low-Pass preset: [30, 1500, -1500, -1500, -1500]
            // "starts set instantly this settings take no smoothness no pause at a time"
            if (!AudioEngineManager.applyLowPassInstant(LOW_PASS_BANDS)) {
                savedEqState = null
                targetTimeMs = null
                untimedFallbackTriggered = false
                Log.w(TAG, "Filter application deferred because audio effects are locked")
                return@launch
            }
            Log.i(TAG, "Applied Low-Pass EQ immediately for '$targetLyricLineText'")
            triggeredForTrack = true

            if (holdUntilTrackChange) {
                // Afterglow is held through the tail of this song. The player calls
                // onPlaybackStarted for the next item, which restores the captured EQ.
                while (isActive &&
                    MusicPlayerState.currentTrack?.id == currentTrackId &&
                    SettingsStore.state.value.afterglowDuration != "Off") {
                    delay(100L)
                }
                return@launch
            }

            // 3. Hold for 10 to 12 seconds (~11.0s)
            val holdDurationMs = 11_000L
            delay(holdDurationMs)
            if (!isActive) return@launch
            if (!AudioEngineManager.state.value.lowPassFilterEnabled || MusicPlayerState.currentTrack?.id != currentTrackId) {
                restoreSavedEq(immediate = true)
                return@launch
            }

            // 4. "when reaches 10 12 sec set the old settings back means disbake the effecy but smootly slowly"
            // Update the actual EQ bands and UI state throughout the fade. Do not gate these
            // writes on isPlaying: that made the sweep jump straight to the final restore when
            // the player's observable state briefly lagged or playback was paused.
            val fadeDurationMs = 8_000L
            val steps = 100
            val stepDelay = fadeDurationMs / steps
            val saved = savedEqState ?: SavedEq(false, "Flat", listOf(0, 0, 0, 0, 0))
            val activeBands = AudioEngineManager.state.value.eqBandsMb.toList()
            // When the user's EQ was originally off, its saved sliders are dormant. Fade the
            // active hardware EQ to neutral first, then switch it off; fading to dormant custom
            // values and toggling off afterward creates the audible end-of-sweep snap.
            val audibleRestoreBands = if (saved.wasEnabled) saved.bandsMb else saved.bandsMb.map { 0 }

            var step = 1
            while (step <= steps) {
                if (!isActive) return@launch
                if (!AudioEngineManager.state.value.lowPassFilterEnabled ||
                    MusicPlayerState.currentTrack?.id != currentTrackId) {
                    restoreSavedEq(immediate = true)
                    return@launch
                }

                val progress = step.toFloat() / steps.toFloat()
                // Cosine S-curve gives the EQ a gradual start and finish.
                val factor = (1f - cos(progress * PI.toFloat())) / 2f
                val interpolatedBands = saved.bandsMb.indices.map { idx ->
                    val startLevel = activeBands.getOrElse(idx) { LOW_PASS_BANDS.getOrElse(idx) { 0 } }
                    val targetLevel = audibleRestoreBands.getOrElse(idx) { 0 }
                    (startLevel + (targetLevel - startLevel) * factor).roundToInt()
                }
                AudioEngineManager.applyLowPassInterpolated(interpolatedBands)
                AudioEngineManager.setLowPassProcessorMix(1f - factor)
                step++
                delay(stepDelay)
            }

            // Buffer pause at 100% restored level so hardware filters settle cleanly before restoring EQ enable state
            delay(200)

            // 5. Finalize restore
            restoreSavedEq(immediate = false)
            Log.i(TAG, "Restored previous EQ after the filter sweep")
        }
    }

    private fun restoreSavedEq(immediate: Boolean) {
        val saved = savedEqState ?: return
        savedEqState = null
        AudioEngineManager.restoreEqAfterLowPass(
            originalEnabled = saved.wasEnabled,
            originalPreset = saved.preset,
            originalBands = saved.bandsMb,
            bandsAlreadyRestoredGradually = !immediate,
        )
    }

    fun cancelActiveEffect() {
        effectJob?.cancel()
        effectJob = null
        restoreSavedEq(immediate = true)
    }

    fun onPlaybackStarted(newTrackId: String?, forceRestart: Boolean = false) {
        // ExoPlayer can emit a transition/discontinuity for repeats and output rebuilds of the
        // same item. Ordinary same-item callbacks must not rearm lyric filtering, but a repeat-one
        // seek from the end to the beginning is a new Afterglow cycle even though the media ID is unchanged.
        if (newTrackId == currentTrackId && !forceRestart) return
        resetForNewTrack(newTrackId)
        afterglowRestartPending = forceRestart
    }

    fun onEffectToggledOn() {
        targetTimeMs = null
        triggeredForTrack = false
        afterglowTriggeredForTrack = false
        afterglowRestartPending = false
        untimedFallbackTriggered = false
        Log.i(TAG, "Enabled; scanning synced lyrics or scheduling an untimed fallback")
    }

    fun resetForNewTrack(newTrackId: String?) {
        cancelActiveEffect()
        currentTrackId = newTrackId
        targetTimeMs = null
        triggeredForTrack = false
        afterglowTriggeredForTrack = false
        afterglowRestartPending = false
        untimedFallbackTriggered = false
        lastPositionMs = 0L
        targetLyricLineText = ""
    }
}
