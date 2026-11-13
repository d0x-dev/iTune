package com.darkxvenom.itune.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt

/** Software low-pass fallback so the effect works even when Android's hardware EQ is unavailable. */
@UnstableApi
class LowPassAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var mix: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    @Volatile
    var bypass: Boolean = false

    private var channels = 0
    private var encoding = C.ENCODING_INVALID
    private var coefficient = 0.1f
    private var filtered = FloatArray(0)
    private var reusableOutputBuffer: ByteBuffer? = null
    private var inputScratch = ByteArray(0)
    private var loggedActiveFrame = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding in SUPPORTED_ENCODINGS
        if (!supported) {
            Log.w(TAG, "Unsupported decoded PCM encoding ${inputAudioFormat.encoding}; hardware EQ remains available")
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        encoding = inputAudioFormat.encoding
        filtered = FloatArray(channels)
        loggedActiveFrame = false
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1).toFloat()
        coefficient = (1f - exp((-2.0 * PI * CUTOFF_HZ / sampleRate).toFloat())).coerceIn(0.001f, 1f)
        Log.d(TAG, "Configured ${inputAudioFormat.sampleRate}Hz, $channels-channel PCM (${inputAudioFormat.encoding})")
        return inputAudioFormat
    }

    override fun onFlush() {
        filtered.fill(0f)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val amount = mix
        val inputPosition = inputBuffer.position()
        val inputLimit = inputBuffer.limit()
        val inputSize = inputBuffer.remaining()
        val aliasesPreviousOutput = inputBuffer === reusableOutputBuffer

        if (!bypass && amount > 0f && !loggedActiveFrame) {
            loggedActiveFrame = true
            Log.i(TAG, "Software low-pass is processing audio frames")
        }

        // BaseAudioProcessor reuses its output buffer. The pipeline may feed that same
        // buffer back into this processor on a later frame, so preserve the source before
        // replaceOutputBuffer clears it when we need to transform samples.
        val source = if (aliasesPreviousOutput && !bypass && amount > 0f && channels > 0) {
            if (inputScratch.size < inputSize) inputScratch = ByteArray(inputSize)
            inputBuffer.get(inputScratch, 0, inputSize)
            ByteBuffer.wrap(inputScratch, 0, inputSize).order(ByteOrder.nativeOrder())
        } else {
            inputBuffer
        }

        if (bypass || amount <= 0f || channels <= 0) {
            val output = replaceOutputBuffer(inputSize)
            reusableOutputBuffer = output
            if (output === inputBuffer) {
                // The input is already in the reusable output storage; restore its readable range.
                output.limit(inputLimit)
                output.position(inputPosition)
            } else {
                output.put(inputBuffer)
                output.flip()
            }
            return
        }

        source.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(inputSize).order(ByteOrder.nativeOrder())
        reusableOutputBuffer = output
        val bytesPerSample = bytesPerSample(encoding)
        while (source.remaining() >= bytesPerSample * channels) {
            for (channel in 0 until channels) {
                val original = readSample(source)
                val filteredSample = filtered[channel] + coefficient * (original - filtered[channel])
                filtered[channel] = filteredSample
                val mixed = (original + (filteredSample - original) * amount).coerceIn(-1f, 1f)
                writeSample(output, mixed)
            }
        }
        output.flip()
    }

    private fun readSample(buffer: ByteBuffer): Float = when (encoding) {
        C.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xFF) - 128) / 128f
        C.ENCODING_PCM_16BIT -> buffer.short / 32768f
        C.ENCODING_PCM_24BIT -> {
            val b0 = buffer.get().toInt() and 0xFF
            val b1 = buffer.get().toInt() and 0xFF
            val b2 = buffer.get().toInt()
            var value = b0 or (b1 shl 8) or (b2 shl 16)
            if (value and 0x800000 != 0) value = value or -0x1000000
            value / 8_388_608f
        }
        C.ENCODING_PCM_32BIT -> buffer.int / 2_147_483_648f
        C.ENCODING_PCM_FLOAT -> buffer.float
        else -> 0f
    }

    private fun writeSample(buffer: ByteBuffer, sample: Float) {
        when (encoding) {
            C.ENCODING_PCM_8BIT -> buffer.put((sample * 127f + 128f).roundToInt().coerceIn(0, 255).toByte())
            C.ENCODING_PCM_16BIT -> buffer.putShort((sample * Short.MAX_VALUE).roundToInt().toShort())
            C.ENCODING_PCM_24BIT -> {
                val value = (sample * 8_388_607f).roundToInt()
                buffer.put(value.toByte())
                buffer.put((value shr 8).toByte())
                buffer.put((value shr 16).toByte())
            }
            C.ENCODING_PCM_32BIT -> {
                val value = when {
                    sample >= 1f -> Int.MAX_VALUE
                    sample <= -1f -> Int.MIN_VALUE
                    else -> (sample * Int.MAX_VALUE.toDouble()).toLong().toInt()
                }
                buffer.putInt(value)
            }
            C.ENCODING_PCM_FLOAT -> buffer.putFloat(sample)
        }
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_8BIT -> 1
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 0
    }

    private companion object {
        const val TAG = "LowPassDSP"
        const val CUTOFF_HZ = 900f
        val SUPPORTED_ENCODINGS = setOf(
            C.ENCODING_PCM_8BIT,
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT,
        )
    }
}
