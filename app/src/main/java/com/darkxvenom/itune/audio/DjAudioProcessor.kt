package com.darkxvenom.itune.audio

import androidx.annotation.DrawableRes
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.darkxvenom.itune.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class DjPreset(
    val title: String,
    val subtitle: String,
    @param:DrawableRes val iconRes: Int,
) {
    DEFAULT("Flat / Reset", "Pure original mix", R.drawable.ic_headphone),
    CLUB_BOOTH("Club Booth", "Warm echo & bass pump", R.drawable.graphic_eq),
    SLOWED_REVERB("Slowed + Reverb", "0.85x speed & spacious echo", R.drawable.ic_fx_turntable),
    NIGHTCORE("Nightcore", "1.25x speed & high pitch", R.drawable.ic_fx_turntable),
    BASS_BOMB("Bass Bomb", "Subwoofer drive & punch", R.drawable.volume_up),
    LOFI_VINYL("Lo-Fi Vinyl", "Warm muffled telephone filter", R.drawable.ic_fx_flanger),
    SPACE_ECHO("Cosmic Echo", "3D ping-pong & jet flanger", R.drawable.ic_fx_echo),
}

/**
 * Studio DJ Audio Processor (from AirBeats):
 * - Real-time Stereo & 3D Ping-Pong Echo Delay (20..1000ms)
 * - Pioneer-style Bipolar Resonant Club Filter Sweep (-1.0 LPF .. 0.0 Flat .. +1.0 HPF)
 * - Jet Flanger / Phase LFO sweep
 * - Analog Warm Tape Saturation / soft clipper
 */
@UnstableApi
class DjAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var bypass: Boolean = false

    @Volatile
    var echoEnabled: Boolean = false

    @Volatile
    var echoDelayMs: Int = 280
        set(value) {
            field = value.coerceIn(20, 1000)
        }

    @Volatile
    var echoFeedback: Float = 0.40f
        set(value) {
            field = value.coerceIn(0.0f, 0.85f)
        }

    @Volatile
    var echoWetMix: Float = 0.45f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    @Volatile
    var echoPingPong: Boolean = true

    @Volatile
    var filterSweep: Float = 0.0f
        set(value) {
            field = value.coerceIn(-1.0f, 1.0f)
        }

    @Volatile
    var flangerEnabled: Boolean = false

    @Volatile
    var flangerRate: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.1f, 4.0f)
        }

    @Volatile
    var flangerDepth: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    @Volatile
    var saturation: Float = 0.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    private var sampleRate: Int = 44100
    private var delayBufL = FloatArray(48000)
    private var delayBufR = FloatArray(48000)
    private var delayWritePos: Int = 0

    private var svfLowL = 0f
    private var svfBandL = 0f
    private var svfLowR = 0f
    private var svfBandR = 0f

    private var flangerBufL = FloatArray(512)
    private var flangerBufR = FloatArray(512)
    private var flangerWritePos: Int = 0
    private var flangerPhase: Double = 0.0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        val maxDelaySamples = (sampleRate * 1.05f).toInt()
        if (delayBufL.size < maxDelaySamples) {
            delayBufL = FloatArray(maxDelaySamples)
            delayBufR = FloatArray(maxDelaySamples)
        }
        val maxFlangerSamples = (sampleRate * 0.015f).toInt().coerceAtLeast(512)
        if (flangerBufL.size < maxFlangerSamples) {
            flangerBufL = FloatArray(maxFlangerSamples)
            flangerBufR = FloatArray(maxFlangerSamples)
        }
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    fun resetState() {
        delayBufL.fill(0f)
        delayBufR.fill(0f)
        delayWritePos = 0
        svfLowL = 0f
        svfBandL = 0f
        svfLowR = 0f
        svfBandR = 0f
        flangerBufL.fill(0f)
        flangerBufR.fill(0f)
        flangerWritePos = 0
        flangerPhase = 0.0
    }

    fun resetFx() {
        enabled = false
        echoEnabled = false
        echoDelayMs = 280
        echoFeedback = 0.40f
        echoWetMix = 0.0f
        echoPingPong = false
        filterSweep = 0.0f
        flangerEnabled = false
        flangerRate = 0.5f
        flangerDepth = 0.0f
        saturation = 0.0f
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val isEchoOn = echoEnabled && echoWetMix > 0.01f
        val isFilterOn = abs(filterSweep) > 0.02f
        val isFlangerOn = flangerEnabled && flangerDepth > 0.01f
        val isSatOn = saturation > 0.02f

        if (bypass || !enabled || (!isEchoOn && !isFilterOn && !isFlangerOn && !isSatOn)) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val sr = sampleRate.toFloat()
        val delaySamples = (sr * (echoDelayMs / 1000f)).roundToInt().coerceIn(1, delayBufL.size - 1)
        val feedback = echoFeedback
        val wet = echoWetMix
        val dry = 1.0f - (wet * 0.35f)
        val isPingPong = echoPingPong

        var filterActive = false
        var isHighPass = false
        var svfF = 0f
        val svfQ = 1.0f / 1.20f

        if (isFilterOn) {
            filterActive = true
            val sweep = filterSweep
            val cutoffHz = if (sweep < 0f) {
                isHighPass = false
                val t = 1.0f + sweep
                (350f * (18000f / 350f).pow(t)).coerceIn(100f, sr * 0.45f)
            } else {
                isHighPass = true
                (35f * (4500f / 35f).pow(sweep)).coerceIn(20f, sr * 0.45f)
            }
            svfF = (2.0f * sin(PI * cutoffHz / sr)).toFloat().coerceIn(0.01f, 0.95f)
        }

        val flangerLfoInc = (2.0 * PI * flangerRate) / sr
        val flangerBaseDelay = sr * 0.0025f
        val flangerModDepth = sr * 0.0018f * flangerDepth
        val satDrive = 1.0f + saturation * 2.2f

        while (inputBuffer.remaining() >= 4) {
            var sampleL = inputBuffer.short.toFloat()
            var sampleR = inputBuffer.short.toFloat()

            if (isEchoOn) {
                val readPos = (delayWritePos - delaySamples + delayBufL.size) % delayBufL.size
                val delayedL = delayBufL[readPos]
                val delayedR = delayBufR[readPos]

                val echoOutL: Float
                val echoOutR: Float
                if (isPingPong) {
                    echoOutL = delayedR
                    echoOutR = delayedL
                    delayBufL[delayWritePos] = sampleL + delayedR * feedback
                    delayBufR[delayWritePos] = sampleR + delayedL * feedback
                } else {
                    echoOutL = delayedL
                    echoOutR = delayedR
                    delayBufL[delayWritePos] = sampleL + delayedL * feedback
                    delayBufR[delayWritePos] = sampleR + delayedR * feedback
                }
                sampleL = sampleL * dry + echoOutL * wet
                sampleR = sampleR * dry + echoOutR * wet
                delayWritePos = (delayWritePos + 1) % delayBufL.size
            }

            if (filterActive) {
                svfLowL += svfF * svfBandL
                val highL = sampleL - svfLowL - svfQ * svfBandL
                svfBandL += svfF * highL
                sampleL = if (isHighPass) highL else svfLowL

                svfLowR += svfF * svfBandR
                val highR = sampleR - svfLowR - svfQ * svfBandR
                svfBandR += svfF * highR
                sampleR = if (isHighPass) highR else svfLowR
            }

            if (isFlangerOn) {
                flangerPhase = (flangerPhase + flangerLfoInc) % (2.0 * PI)
                val lfoVal = sin(flangerPhase).toFloat()
                val curDelay = (flangerBaseDelay + lfoVal * flangerModDepth).toInt()
                val flReadPos = (flangerWritePos - curDelay + flangerBufL.size) % flangerBufL.size
                val flL = flangerBufL[flReadPos]
                val flR = flangerBufR[flReadPos]
                flangerBufL[flangerWritePos] = sampleL
                flangerBufR[flangerWritePos] = sampleR
                flangerWritePos = (flangerWritePos + 1) % flangerBufL.size
                sampleL = sampleL * 0.72f + flL * 0.48f
                sampleR = sampleR * 0.72f + flR * 0.48f
            }

            if (isSatOn) {
                sampleL = softClip(sampleL, satDrive)
                sampleR = softClip(sampleR, satDrive)
            }

            outputBuffer.putShort(sampleL.coerceIn(-32768f, 32767f).toInt().toShort())
            outputBuffer.putShort(sampleR.coerceIn(-32768f, 32767f).toInt().toShort())
        }

        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        outputBuffer.flip()
    }

    private fun softClip(sample: Float, drive: Float): Float {
        val normalized = (sample / 32768f) * drive
        val clipped = when {
            normalized > 1.0f -> 1.0f - (1.0f / (normalized + 1.0f))
            normalized < -1.0f -> -1.0f - (1.0f / (normalized - 1.0f))
            else -> normalized - (normalized * normalized * normalized) / 3.0f
        }
        return (clipped / drive.pow(0.5f)) * 32768f
    }
}
