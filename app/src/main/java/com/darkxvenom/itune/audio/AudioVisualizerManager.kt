package com.darkxvenom.itune.audio

import android.media.audiofx.Visualizer
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

@Immutable
data class AudioVisualizerStats(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val bassDb: Int = 0,
    val midDb: Int = 0,
    val trebleDb: Int = 0,
    val isBassPeak: Boolean = false,
)

/**
 * Live FFT Bass / Mid / Treble energy meter (from AirBeats) with automatic fallback curve
 * when Visualizer hardware capture is unavailable on restricted/emulated sessions.
 */
class AudioVisualizerManager(
    private val scope: CoroutineScope,
) {
    private val _stats = MutableStateFlow(AudioVisualizerStats())
    val stats: StateFlow<AudioVisualizerStats> = _stats.asStateFlow()

    private var visualizer: Visualizer? = null
    private var fallbackJob: Job? = null
    private var currentSessionId: Int = 0

    @Volatile
    var isPlaying: Boolean = false
        set(value) {
            field = value
            if (!value) decay()
        }

    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f

    fun start(audioSessionId: Int) {
        if (visualizer != null && currentSessionId == audioSessionId && audioSessionId > 0) return
        if (visualizer == null && audioSessionId <= 0 && fallbackJob?.isActive == true) return

        stop()
        currentSessionId = audioSessionId

        if (audioSessionId > 0) {
            runCatching {
                val vis = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, sr: Int) = Unit
                            override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, sr: Int) {
                                if (fft != null && isPlaying) processFft(fft) else decay()
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true,
                    )
                    enabled = true
                }
                visualizer = vis
            }.onFailure {
                startFallbackLoop()
            }
        } else {
            startFallbackLoop()
        }
    }

    private fun processFft(fft: ByteArray) {
        val n = fft.size / 2
        if (n <= 0) return
        var rawBass = 0f
        var rawMid = 0f
        var rawTreble = 0f
        val bassEnd = min(n, max(2, n / 12))
        val midEnd = min(n, max(bassEnd + 1, n / 2))

        for (i in 1 until bassEnd) rawBass += hypot(fft[2 * i].toFloat(), fft[2 * i + 1].toFloat())
        if (bassEnd > 1) rawBass /= (bassEnd - 1)
        for (i in bassEnd until midEnd) rawMid += hypot(fft[2 * i].toFloat(), fft[2 * i + 1].toFloat())
        if (midEnd > bassEnd) rawMid /= (midEnd - bassEnd)
        for (i in midEnd until n) rawTreble += hypot(fft[2 * i].toFloat(), fft[2 * i + 1].toFloat())
        if (n > midEnd) rawTreble /= (n - midEnd)

        val normBass = (rawBass / 65f).coerceIn(0f, 1f)
        val normMid = (rawMid / 45f).coerceIn(0f, 1f)
        val normTreble = (rawTreble / 30f).coerceIn(0f, 1f)

        smoothBass = if (normBass > smoothBass) normBass else smoothBass * 0.75f + normBass * 0.25f
        smoothMid = if (normMid > smoothMid) normMid else smoothMid * 0.78f + normMid * 0.22f
        smoothTreble = if (normTreble > smoothTreble) normTreble else smoothTreble * 0.80f + normTreble * 0.20f

        emit(smoothBass > 0.72f)
    }

    private fun decay() {
        smoothBass = if (smoothBass * 0.85f < 0.02f) 0f else smoothBass * 0.85f
        smoothMid = if (smoothMid * 0.85f < 0.02f) 0f else smoothMid * 0.85f
        smoothTreble = if (smoothTreble * 0.85f < 0.02f) 0f else smoothTreble * 0.85f
        emit(false)
    }

    private fun emit(peak: Boolean) {
        _stats.value = AudioVisualizerStats(
            bass = smoothBass,
            mid = smoothMid,
            treble = smoothTreble,
            bassDb = (smoothBass * 18).toInt(),
            midDb = (smoothMid * 12).toInt(),
            trebleDb = (smoothTreble * 10).toInt(),
            isBassPeak = peak,
        )
    }

    private fun startFallbackLoop() {
        if (fallbackJob?.isActive == true) return
        fallbackJob?.cancel()
        fallbackJob = scope.launch(Dispatchers.Default) {
            var step = 0.0
            while (isActive) {
                if (isPlaying) {
                    step += 0.25
                    val rhythm = (sin(step * 1.8) * 0.5 + 0.5).toFloat()
                    val b = ((sin(step * 0.9) * 0.4 + 0.5).toFloat() + rhythm * 0.35f + Random.nextFloat() * 0.1f).coerceIn(0f, 1f)
                    val m = ((sin(step * 1.4 + 1.0) * 0.3 + 0.4).toFloat() + Random.nextFloat() * 0.15f).coerceIn(0f, 1f)
                    val t = ((sin(step * 2.2 + 2.0) * 0.25 + 0.35).toFloat() + Random.nextFloat() * 0.2f).coerceIn(0f, 1f)
                    smoothBass = smoothBass * 0.6f + b * 0.4f
                    smoothMid = smoothMid * 0.6f + m * 0.4f
                    smoothTreble = smoothTreble * 0.6f + t * 0.4f
                    emit(smoothBass > 0.72f)
                } else {
                    decay()
                }
                delay(40L)
            }
        }
    }

    fun stop() {
        fallbackJob?.cancel()
        fallbackJob = null
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        currentSessionId = 0
    }
}
