/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.playback.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Whether the phone has an output connected right now that can carry lossless audio.
 */
object LosslessOutput {

    enum class Kind { WIRED, USB, HDMI, DOCK, BLUETOOTH }

    data class State(
        val capable: Boolean = false,
        val via: Kind? = null,
        val bluetoothCodec: String? = null,
        val bluetoothCodecUnknown: Boolean = false,
    )

    private val _state = MutableStateFlow(State(capable = true))
    val state: StateFlow<State> = _state.asStateFlow()

    val capable: Boolean get() = _state.value.capable

    fun init(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AudioManager::class.java) ?: return
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .map { it.type }
            .toSet()
        _state.value = decide(outputs, null)
    }

    internal fun decide(outputTypes: Set<Int>, bluetoothCodec: String?): State {
        val cabled = when {
            outputTypes.any { it in WIRED } -> Kind.WIRED
            outputTypes.any { it in USB } -> Kind.USB
            outputTypes.any { it in HDMI } -> Kind.HDMI
            outputTypes.any { it in DOCK } -> Kind.DOCK
            else -> null
        }
        if (cabled != null) return State(capable = true, via = cabled, bluetoothCodec = bluetoothCodec)
        if (AudioDeviceInfo.TYPE_BLUETOOTH_A2DP !in outputTypes) return State(capable = true)
        if (bluetoothCodec == null) return State(capable = true, bluetoothCodecUnknown = true)
        return State(
            capable = isLosslessBluetoothCodec(bluetoothCodec),
            via = Kind.BLUETOOTH.takeIf { isLosslessBluetoothCodec(bluetoothCodec) },
            bluetoothCodec = bluetoothCodec,
        )
    }

    internal fun isLosslessBluetoothCodec(name: String): Boolean {
        val codec = name.uppercase(Locale.ROOT)
        return "LDAC" in codec || "LHDC" in codec || ("APTX" in codec && "LOSSLESS" in codec)
    }

    private val WIRED = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE,
    )
    private val USB = setOf(
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
    )
    private val HDMI = setOf(
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        29, // TYPE_HDMI_EARC, API 31
    )
    private val DOCK = setOf(
        AudioDeviceInfo.TYPE_DOCK,
        31, // TYPE_DOCK_ANALOG, API 34
    )
}
