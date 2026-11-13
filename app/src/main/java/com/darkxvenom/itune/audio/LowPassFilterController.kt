package com.darkxvenom.itune.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.darkxvenom.itune.backend.LyricsLine
import com.darkxvenom.itune.backend.MusicPlayerState
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
import kotlin.random.Random

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
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scanJob: Job? = null
    private var effectJob: Job? = null

    private var currentTrackId: String? = null
    private var targetTimeMs: Long? = null
    private var hasTriggeredForTrack: Boolean = false
    private var lastPositionMs: Long = 0L
    var targetLyricLineText: String = ""
        private set

    private data class SavedEq(
        val wasEnabled: Boolean,
        val preset: String,
        val bandsMb: List<Int>,
    )
    private var savedEqState: SavedEq? = null

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
                } catch (_: Exception) {
                    // Protect scan loop
                }
            }
        }
    }

    private fun tick() {
        val enabled = AudioEngineManager.state.value.lowPassFilterEnabled
        if (!enabled) {
            if (AudioEngineManager.state.value.lowPassFilterActive) {
                cancelActiveEffect()
            }
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
        }

        val lyrics = MusicPlayerState.lyrics
        val positionMs = MusicPlayerState.positionMs
        val isPlaying = MusicPlayerState.isPlaying

        // Track rewind / loop replay detection: reset if song looped to beginning or user scrubbed back
        val isLoopedToStart = (positionMs <= 2000L && lastPositionMs > 4000L)
        val isSeekedBack = (positionMs < lastPositionMs - 3500L)
        if (isLoopedToStart || isSeekedBack) {
            hasTriggeredForTrack = false
            targetTimeMs = null
        }
        lastPositionMs = positionMs

        if (hasTriggeredForTrack) {
            return
        }

        // If lyrics not loaded yet, keep scanning until last
        if (lyrics.isEmpty()) {
            return
        }

        // If target timestamp is not selected yet, scan lyrics and select the hook line occurrence
        if (targetTimeMs == null) {
            val candidate = findTargetLyricOccurrence(lyrics, positionMs)
            if (candidate != null) {
                targetTimeMs = candidate.timeMs
                targetLyricLineText = candidate.text
            }
        }

        val target = targetTimeMs ?: return
        val delta = target - positionMs

        // Check if playback reached target timestamp (triggers right as line starts singing)
        if (isPlaying && !hasTriggeredForTrack) {
            if (delta in -2500L..400L) {
                hasTriggeredForTrack = true
                triggerEffect()
            } else if (delta < -2500L) {
                // User scrubbed/jumped forward past the target, pick another future occurrence if available
                val nextCandidate = findTargetLyricOccurrence(lyrics, positionMs)
                if (nextCandidate != null && nextCandidate.timeMs > positionMs) {
                    targetTimeMs = nextCandidate.timeMs
                    targetLyricLineText = nextCandidate.text
                } else {
                    // Near end of song or scrubbed past all occurrences: trigger now on current line
                    hasTriggeredForTrack = true
                    triggerEffect()
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
                topCandidates.values.random()
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
            .replace(Regex("""[()[\]{}<>"'.,!?:;~`*#^&_+=/\\|-]"""), " ")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), "")
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

    private fun triggerEffect() {
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
            AudioEngineManager.applyLowPassInstant(LOW_PASS_BANDS)

            // 3. Hold for 10 to 12 seconds (~11.0s)
            val holdDurationMs = Random.nextLong(10_500L, 11_500L)
            val holdStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - holdStart < holdDurationMs) {
                if (!isActive) return@launch
                if (!AudioEngineManager.state.value.lowPassFilterEnabled) {
                    restoreSavedEq(immediate = true)
                    return@launch
                }
                if (MusicPlayerState.currentTrack?.id != currentTrackId) {
                    restoreSavedEq(immediate = true)
                    return@launch
                }
                delay(80)
            }

            // 4. "when reaches 10 12 sec set the old settings back means disbake the effecy but smootly slowly"
            // Smoothly release over 4.2 seconds using Cosine ease-in-out S-curve and 60ms steps
            val fadeDurationMs = 4200L
            val steps = 70
            val stepDelay = fadeDurationMs / steps // 60ms per step
            val saved = savedEqState ?: SavedEq(false, "Flat", listOf(0, 0, 0, 0, 0))

            for (step in 1..steps) {
                if (!isActive) return@launch
                if (!AudioEngineManager.state.value.lowPassFilterEnabled ||
                    MusicPlayerState.currentTrack?.id != currentTrackId) {
                    restoreSavedEq(immediate = true)
                    return@launch
                }

                val progress = step.toFloat() / steps.toFloat()
                // True Cosine S-curve: starts slowly with 0 slope, accelerates gently, settles with 0 slope
                val factor = (1f - cos(progress * PI.toFloat())) / 2f

                val interpolatedBands = (0 until 5).map { idx ->
                    val startLevel = LOW_PASS_BANDS.getOrElse(idx) { 0 }
                    val targetLevel = saved.bandsMb.getOrElse(idx) { 0 }
                    (startLevel + (targetLevel - startLevel) * factor).roundToInt()
                }

                AudioEngineManager.applyLowPassInterpolated(interpolatedBands)
                delay(stepDelay)
            }

            // Buffer pause at 100% restored level so hardware filters settle cleanly before restoring EQ enable state
            delay(200)

            // 5. Finalize restore
            restoreSavedEq(immediate = false)
        }
    }

    private fun restoreSavedEq(@Suppress("UNUSED_PARAMETER") immediate: Boolean) {
        val saved = savedEqState ?: return
        savedEqState = null
        AudioEngineManager.restoreEqAfterLowPass(
            originalEnabled = saved.wasEnabled,
            originalPreset = saved.preset,
            originalBands = saved.bandsMb,
        )
    }

    fun cancelActiveEffect() {
        effectJob?.cancel()
        effectJob = null
        restoreSavedEq(immediate = true)
    }

    fun onPlaybackStarted(newTrackId: String?) {
        resetForNewTrack(newTrackId)
    }

    fun onEffectToggledOn() {
        targetTimeMs = null
        hasTriggeredForTrack = false
    }

    fun resetForNewTrack(newTrackId: String?) {
        cancelActiveEffect()
        currentTrackId = newTrackId
        targetTimeMs = null
        hasTriggeredForTrack = false
        lastPositionMs = 0L
        targetLyricLineText = ""
    }
}
