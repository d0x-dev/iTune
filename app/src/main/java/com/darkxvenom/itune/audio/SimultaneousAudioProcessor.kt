package com.darkxvenom.itune.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Simultaneous Multi-Output Audio Processor (from AirBeats).
 * Taps decoded PCM frames from ExoPlayer in O(1) non-blocking mode and mirrors audio
 * to a secondary hardware sink (e.g. Phone Speaker + Bluetooth buds at the same time)
 * with independent secondary volume control.
 */
@UnstableApi
class SimultaneousAudioProcessor(
    context: Context,
) : BaseAudioProcessor() {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var volume: Float = 1.0f
        private set

    @Volatile
    var targetDevice: AudioDeviceInfo? = null
        private set

    private var sampleRate: Int = 44100
    private var channelCount: Int = 2
    private var encoding: Int = C.ENCODING_PCM_16BIT

    private var secondaryAudioTrack: AudioTrack? = null
    private val trackLock = Any()

    override fun isActive(): Boolean = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding

        synchronized(trackLock) {
            recreateSecondaryTrackLocked()
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (enabled) {
            val duplicate = inputBuffer.duplicate()
            synchronized(trackLock) {
                val track = secondaryAudioTrack
                if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    runCatching {
                        track.write(duplicate, remaining, AudioTrack.WRITE_NON_BLOCKING)
                    }.onFailure {
                        Log.w(TAG, "Secondary write failed: ${it.message}")
                    }
                }
            }
        }

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun onFlush() {
        synchronized(trackLock) {
            runCatching { secondaryAudioTrack?.flush() }
        }
    }

    override fun onReset() {
        synchronized(trackLock) {
            releaseTrackLocked()
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        if (enabled == isEnabled) return
        enabled = isEnabled
        synchronized(trackLock) {
            if (isEnabled) {
                recreateSecondaryTrackLocked()
            } else {
                releaseTrackLocked()
            }
        }
    }

    fun setTargetDevice(device: AudioDeviceInfo?) {
        targetDevice = device
        synchronized(trackLock) {
            if (enabled) {
                recreateSecondaryTrackLocked()
            }
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0.0f, 1.0f)
        synchronized(trackLock) {
            runCatching { secondaryAudioTrack?.setVolume(volume) }
        }
    }

    fun onPlay() {
        synchronized(trackLock) {
            runCatching {
                if (enabled && secondaryAudioTrack == null) {
                    recreateSecondaryTrackLocked()
                }
                secondaryAudioTrack?.play()
            }
        }
    }

    fun onPause() {
        synchronized(trackLock) {
            runCatching { secondaryAudioTrack?.pause() }
        }
    }

    private fun recreateSecondaryTrackLocked() {
        releaseTrackLocked()
        if (!enabled || sampleRate <= 0) return

        runCatching {
            val device = targetDevice ?: getSpeakerDevice()
            // USAGE_ALARM ensures Android AudioPolicy routes to phone speaker even while BT A2DP is active
            val usage = if (device?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                AudioAttributes.USAGE_ALARM
            } else {
                AudioAttributes.USAGE_MEDIA
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val channelConfig = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val audioEncoding = if (encoding == C.ENCODING_PCM_FLOAT) {
                AudioFormat.ENCODING_PCM_FLOAT
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioEncoding).coerceAtLeast(2048)
            val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioEncoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && device != null) {
                track.setPreferredDevice(device)
            }

            track.setVolume(volume)
            track.play()
            secondaryAudioTrack = track
        }.onFailure {
            Log.e(TAG, "Failed secondary AudioTrack init: ${it.message}")
            secondaryAudioTrack = null
        }
    }

    private fun releaseTrackLocked() {
        runCatching {
            secondaryAudioTrack?.stop()
            secondaryAudioTrack?.release()
        }
        secondaryAudioTrack = null
    }

    private fun getSpeakerDevice(): AudioDeviceInfo? {
        val mgr = audioManager ?: return null
        return mgr.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    companion object {
        private const val TAG = "SimultaneousAudioProc"
    }
}
