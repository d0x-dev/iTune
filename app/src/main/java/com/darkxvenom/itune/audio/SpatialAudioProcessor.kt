package com.darkxvenom.itune.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Spatial Audio DSP processor (copied and enhanced from AirBeats):
 * Expands the stereo image using Mid/Side matrix widening and 15ms cross-feed
 * head-shadow low-pass filtering with zero per-frame allocations.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var bypass: Boolean = false

    @Volatile
    var widthGain: Float = 2.5f
        set(value) {
            field = value.coerceIn(1.0f, 3.0f)
        }

    @Volatile
    var crossfeedEnabled: Boolean = true

    private val outputGain = 0.82f
    private val crossfeedGain = 0.2f
    private val lowpassCoeff = 0.3f

    private var delayLeft = ShortArray(0)
    private var delayRight = ShortArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val delaySamples = (inputAudioFormat.sampleRate * DELAY_MS / 1000f)
            .roundToInt()
            .coerceAtLeast(1)
        delayLeft = ShortArray(delaySamples)
        delayRight = ShortArray(delaySamples)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    fun resetState() {
        delayLeft.fill(0)
        delayRight.fill(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (bypass || !enabled) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val frameCount = remaining / BYTES_PER_FRAME
        if (frameCount == 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val bytesToProcess = frameCount * BYTES_PER_FRAME
        val outputBuffer = replaceOutputBuffer(bytesToProcess)

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val delaySize = delayLeft.size
        val wGain = widthGain
        val doCrossfeed = crossfeedEnabled && delaySize > 0

        repeat(frameCount) {
            val left = inputBuffer.short.toInt()
            val right = inputBuffer.short.toInt()

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * wGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            if (doCrossfeed) {
                val delayedRight = delayRight[delayIndex].toFloat()
                val delayedLeft = delayLeft[delayIndex].toFloat()
                lowpassLeft += lowpassCoeff * (delayedRight - lowpassLeft)
                lowpassRight += lowpassCoeff * (delayedLeft - lowpassRight)
                widenedLeft += lowpassLeft * crossfeedGain
                widenedRight += lowpassRight * crossfeedGain

                delayLeft[delayIndex] = left.toShort()
                delayRight[delayIndex] = right.toShort()
                delayIndex = (delayIndex + 1) % delaySize
            }

            outputBuffer.putShort(clampToShort(widenedLeft * outputGain))
            outputBuffer.putShort(clampToShort(widenedRight * outputGain))
        }

        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outputBuffer.flip()
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private companion object {
        const val BYTES_PER_FRAME = 4
        const val DELAY_MS = 15
    }
}
