package com.darkxvenom.itune.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 8D Spatial Audio Processor (from AirBeats):
 * - 360° orbital azimuth rotation across Left <-> Right and Front <-> Back
 * - Fractional Interaural Time Difference (ITD) delay modeling
 * - HRTF dynamic head-shadow low-pass & rear pinna attenuation
 * - 4-stage holographic early reflections diffuser
 * - 1D..16D intensity control with click-free 35ms crossfade
 */
@UnstableApi
class EightDAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var bypass: Boolean = false

    @Volatile
    var level: Int = 8
        set(value) {
            field = value.coerceIn(1, 16)
        }

    private var sampleRate: Int = 44100
    private var orbitPhase: Double = 0.0

    private var delayBufferL = ShortArray(128)
    private var delayBufferR = ShortArray(128)
    private var delayWriteIndex: Int = 0

    private var lowpassL: Float = 0f
    private var lowpassR: Float = 0f

    private var diffBuf1 = ShortArray(0)
    private var diffBuf2 = ShortArray(0)
    private var diffBuf3 = ShortArray(0)
    private var diffBuf4 = ShortArray(0)
    private var diffIdx1 = 0
    private var diffIdx2 = 0
    private var diffIdx3 = 0
    private var diffIdx4 = 0

    private var currentEnabledAlpha: Float = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        val msFactor = sampleRate / 1000f
        diffBuf1 = ShortArray((4.1f * msFactor).toInt().coerceAtLeast(16))
        diffBuf2 = ShortArray((6.7f * msFactor).toInt().coerceAtLeast(16))
        diffBuf3 = ShortArray((9.3f * msFactor).toInt().coerceAtLeast(16))
        diffBuf4 = ShortArray((12.1f * msFactor).toInt().coerceAtLeast(16))
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    fun resetState() {
        delayBufferL.fill(0)
        delayBufferR.fill(0)
        delayWriteIndex = 0
        lowpassL = 0f
        lowpassR = 0f
        diffBuf1.fill(0)
        diffBuf2.fill(0)
        diffBuf3.fill(0)
        diffBuf4.fill(0)
        diffIdx1 = 0
        diffIdx2 = 0
        diffIdx3 = 0
        diffIdx4 = 0
        orbitPhase = 0.0
        currentEnabledAlpha = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (bypass || !enabled) {
            currentEnabledAlpha = 0f
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

        val currentLevel = level
        val orbitHz = 0.035 + (currentLevel - 1) * 0.0163
        val phaseIncrement = (2.0 * PI * orbitHz) / sampleRate
        val depth = 0.45f + (currentLevel - 1) * (0.55f / 15f)
        val maxItdSamples = (0.00065f * sampleRate).coerceIn(12f, 40f)
        val ambienceGain = 0.10f + (currentLevel - 1) * (0.12f / 15f)
        val alphaStep = 1f / (sampleRate * 0.035f)

        val delaySize = delayBufferL.size
        val dSize1 = diffBuf1.size
        val dSize2 = diffBuf2.size
        val dSize3 = diffBuf3.size
        val dSize4 = diffBuf4.size

        repeat(frameCount) {
            val rawLeft = inputBuffer.short.toFloat()
            val rawRight = inputBuffer.short.toFloat()

            if (currentEnabledAlpha < 1f) {
                currentEnabledAlpha = (currentEnabledAlpha + alphaStep).coerceAtMost(1f)
            }

            val sinTheta = sin(orbitPhase).toFloat()
            val cosTheta = cos(orbitPhase).toFloat()
            orbitPhase = (orbitPhase + phaseIncrement) % (2.0 * PI)

            val panAngle = (sinTheta + 1f) * 0.25f * PI.toFloat()
            val panL = cos(panAngle.toDouble()).toFloat()
            val panR = sin(panAngle.toDouble()).toFloat()

            val gainL = (1f - depth) * 0.707f + depth * panL
            val gainR = (1f - depth) * 0.707f + depth * panR

            delayBufferL[delayWriteIndex] = rawLeft.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            delayBufferR[delayWriteIndex] = rawRight.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            val delayL = sinTheta.coerceAtLeast(0f) * maxItdSamples * depth
            val delayR = (-sinTheta).coerceAtLeast(0f) * maxItdSamples * depth

            val delayedLeft = readFractionalDelay(delayBufferL, delayWriteIndex, delayL, delaySize)
            val delayedRight = readFractionalDelay(delayBufferR, delayWriteIndex, delayR, delaySize)
            delayWriteIndex = (delayWriteIndex + 1) % delaySize

            val shadowCoeffL = 0.35f + 0.65f * (1f - (sinTheta.coerceAtLeast(0f) * 0.70f * depth))
            val shadowCoeffR = 0.35f + 0.65f * (1f - ((-sinTheta).coerceAtLeast(0f) * 0.70f * depth))

            lowpassL += shadowCoeffL * (delayedLeft - lowpassL)
            lowpassR += shadowCoeffR * (delayedRight - lowpassR)

            val rearDampening = if (cosTheta < 0f) 1f + (cosTheta * 0.16f * depth) else 1f
            var spatialL = lowpassL * gainL * rearDampening
            var spatialR = lowpassR * gainR * rearDampening

            if (dSize1 > 0 && dSize2 > 0 && dSize3 > 0 && dSize4 > 0) {
                val sumMid = (rawLeft + rawRight) * 0.5f
                val out1 = diffBuf1[diffIdx1].toFloat()
                val out2 = diffBuf2[diffIdx2].toFloat()
                val out3 = diffBuf3[diffIdx3].toFloat()
                val out4 = diffBuf4[diffIdx4].toFloat()

                diffBuf1[diffIdx1] = (sumMid + out1 * 0.35f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                diffBuf2[diffIdx2] = (sumMid + out2 * 0.35f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                diffBuf3[diffIdx3] = (sumMid + out3 * 0.32f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                diffBuf4[diffIdx4] = (sumMid + out4 * 0.32f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                diffIdx1 = (diffIdx1 + 1) % dSize1
                diffIdx2 = (diffIdx2 + 1) % dSize2
                diffIdx3 = (diffIdx3 + 1) % dSize3
                diffIdx4 = (diffIdx4 + 1) % dSize4

                spatialL += (out1 + out3) * 0.5f * ambienceGain
                spatialR += (out2 + out4) * 0.5f * ambienceGain
            }

            val finalSpatialL = spatialL * 0.88f
            val finalSpatialR = spatialR * 0.88f

            val outSampleL = rawLeft * (1f - currentEnabledAlpha) + finalSpatialL * currentEnabledAlpha
            val outSampleR = rawRight * (1f - currentEnabledAlpha) + finalSpatialR * currentEnabledAlpha

            outputBuffer.putShort(clampToShort(outSampleL))
            outputBuffer.putShort(clampToShort(outSampleR))
        }

        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outputBuffer.flip()
    }

    private fun readFractionalDelay(buffer: ShortArray, writeIdx: Int, delay: Float, bufSize: Int): Float {
        val readPos = writeIdx - delay
        val baseIdx = (readPos.toInt() % bufSize + bufSize) % bufSize
        val nextIdx = (baseIdx + 1) % bufSize
        val frac = readPos - readPos.toInt()
        val s0 = buffer[baseIdx].toFloat()
        val s1 = buffer[nextIdx].toFloat()
        return s0 + frac * (s1 - s0)
    }

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private companion object {
        const val BYTES_PER_FRAME = 4
    }
}
