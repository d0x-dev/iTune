package com.darkxvenom.itune.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Transparent pass-through processor that measures how loud the decoded audio is while it plays.
 *
 * It never modifies samples. AutoMix reads these numbers to:
 *  - notice when a song is already fading into a quiet outro ([shortRms] well below [longRms]),
 *  - notice when an incoming song has finished its silent intro ([shortRms] rises above the floor),
 *  - balance the loudness of the two songs during a transition.
 *
 * Levels are normalised RMS values in 0..1 (0.01 is roughly -40 dBFS).
 * The audio thread writes, the main thread reads, so every published value is @Volatile.
 */
@UnstableApi
class LevelProbeProcessor : BaseAudioProcessor() {

    /** Smoothed loudness over roughly the last half second. */
    @Volatile
    var shortRms: Float = 0f
        private set

    /** Average loudness of every audible 100 ms block since the last [reset]. */
    @Volatile
    var longRms: Float = 0f
        private set

    @Volatile
    private var resetRequested = false

    private var channelCount = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var analyze = false
    private var blockFrames = 4410
    private var framesInBlock = 0
    private var sumSquares = 0.0
    private var longSumSquares = 0.0
    private var longBlocks = 0L
    private var smoothed = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        analyze = channelCount > 0 &&
            (encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT)
        blockFrames = (inputAudioFormat.sampleRate / 10).coerceAtLeast(1)
        framesInBlock = 0
        sumSquares = 0.0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        if (analyze) measure(inputBuffer)
        val output = replaceOutputBuffer(size)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        framesInBlock = 0
        sumSquares = 0.0
    }

    /** Forget the stats of the previous song. Safe to call from any thread. */
    fun resetStats() {
        resetRequested = true
        shortRms = 0f
        longRms = 0f
    }

    override fun onReset() {
        resetStats()
    }

    private fun measure(source: ByteBuffer) {
        // duplicate() so the real buffer position is untouched; order is not inherited, set it again.
        val data = source.duplicate().order(ByteOrder.nativeOrder())
        val isFloat = encoding == C.ENCODING_PCM_FLOAT
        val bytesPerFrame = (if (isFloat) 4 else 2) * channelCount
        var framesLeft = data.remaining() / bytesPerFrame
        while (framesLeft > 0) {
            val take = min(framesLeft, blockFrames - framesInBlock)
            for (frame in 0 until take) {
                for (channel in 0 until channelCount) {
                    val sample = if (isFloat) data.getFloat() else data.getShort() / 32768f
                    sumSquares += (sample * sample).toDouble()
                }
            }
            framesInBlock += take
            framesLeft -= take
            if (framesInBlock >= blockFrames) finishBlock()
        }
    }

    private fun finishBlock() {
        val rms = sqrt(sumSquares / (framesInBlock.toDouble() * channelCount)).toFloat()
        sumSquares = 0.0
        framesInBlock = 0

        if (resetRequested) {
            resetRequested = false
            longSumSquares = 0.0
            longBlocks = 0L
            smoothed = 0f
        }

        smoothed += (rms - smoothed) * 0.2f
        shortRms = smoothed

        if (rms > AUDIBLE_FLOOR) {
            longSumSquares += (rms * rms).toDouble()
            longBlocks++
            longRms = sqrt(longSumSquares / longBlocks).toFloat()
        }
    }

    private companion object {
        const val AUDIBLE_FLOOR = 0.01f
    }
}
