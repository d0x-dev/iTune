package com.darkxvenom.itune.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build

/**
 * Requests Android 14+ (API 34+) platform USB bit-perfect PCM output for connected USB DACs,
 * bypassing OS software resampling and mixer attenuation when supported by hardware.
 */
class UsbBitPerfectOutput(private val manager: AudioManager?) {
    private var device: AudioDeviceInfo? = null
    private var format: AudioFormat? = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(48000)
        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        .build()
    private var enabled = false
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var requestedDevice: AudioDeviceInfo? = null

    @Synchronized
    fun setDevice(value: AudioDeviceInfo?) {
        if (device?.id == value?.id) return
        clear()
        device = value
        apply()
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        apply()
    }

    @Synchronized
    fun setFormat(value: AudioFormat?) {
        val next = value ?: return
        if (sameFormat(format, next)) return
        clear()
        format = next
        apply()
    }

    @Synchronized
    fun isConfigured(): Boolean {
        if (Build.VERSION.SDK_INT < 34 || !enabled || requestedDevice == null) return false
        return runCatching {
            val preferred = manager?.getPreferredMixerAttributes(attributes, requestedDevice!!)
            preferred?.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                sameFormat(preferred.format, format)
        }.getOrDefault(false)
    }

    private fun apply() {
        if (Build.VERSION.SDK_INT < 34) return
        val target = device
        val pcm = format
        if (!enabled || target == null || pcm == null) {
            clear()
            return
        }
        if (target.type != AudioDeviceInfo.TYPE_USB_DEVICE && target.type != AudioDeviceInfo.TYPE_USB_HEADSET) return
        runCatching {
            val supported = manager?.getSupportedMixerAttributes(target)?.firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT && sameFormat(it.format, pcm)
            } ?: manager?.getSupportedMixerAttributes(target)?.firstOrNull {
                it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
            } ?: return
            if (manager?.setPreferredMixerAttributes(attributes, target, supported) == true) {
                requestedDevice = target
                format = supported.format
            }
        }
    }

    fun clear() {
        val previous = requestedDevice
        requestedDevice = null
        if (Build.VERSION.SDK_INT >= 34 && previous != null) {
            runCatching { manager?.clearPreferredMixerAttributes(attributes, previous) }
        }
    }

    private fun sameFormat(a: AudioFormat?, b: AudioFormat?): Boolean {
        if (a == null || b == null) return a == null && b == null
        if (a === b) return true
        return runCatching {
            a.encoding == b.encoding &&
                a.sampleRate == b.sampleRate &&
                a.channelMask == b.channelMask
        }.getOrDefault(false)
    }
}
