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

package com.darkxvenom.itune.audio

import com.darkxvenom.itune.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioOutputIconPolicyTest {

    @Test
    fun capsuleIcon_usesBudsForBluetoothAndWiredHeadphoneForUsbOrJack() {
        assertEquals(
            R.drawable.ic_airbuds,
            resolveOutputCapsuleIcon(AudioDeviceCategory.BLUETOOTH_BUDS, explicitSelection = false),
        )
        assertEquals(
            R.drawable.ic_headphone,
            resolveOutputCapsuleIcon(AudioDeviceCategory.WIRED_OR_USB_HEADPHONE, explicitSelection = false),
        )
        assertEquals(
            R.drawable.ic_headphone,
            resolveOutputCapsuleIcon(AudioDeviceCategory.USB_DAC, explicitSelection = false),
        )
        assertEquals(
            R.drawable.ic_output_speaker,
            resolveOutputCapsuleIcon(AudioDeviceCategory.BUILTIN_SPEAKER, explicitSelection = true),
        )
        assertEquals(
            R.drawable.ic_output_speaker,
            resolveOutputCapsuleIcon(AudioDeviceCategory.BUILTIN_SPEAKER, explicitSelection = false),
        )
        assertEquals(
            R.drawable.ic_output_external_speaker,
            resolveOutputCapsuleIcon(AudioDeviceCategory.EXTERNAL_SPEAKER, explicitSelection = false),
        )
        assertEquals(
            R.drawable.ic_output_hearing_aid,
            resolveOutputCapsuleIcon(AudioDeviceCategory.HEARING_AID, explicitSelection = false),
        )
        assertEquals(
            R.drawable.ic_cast,
            resolveOutputCapsuleIcon(AudioDeviceCategory.CAST_DEVICE, explicitSelection = false),
        )
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    @Test
    fun deviceListIcon_usesDistinctXmlPerCategory() {
        assertEquals(R.drawable.ic_airbuds, resolveOutputListIcon(AudioDeviceCategory.BLUETOOTH_BUDS))
        assertEquals(R.drawable.ic_headphone, resolveOutputListIcon(AudioDeviceCategory.WIRED_OR_USB_HEADPHONE))
        assertEquals(R.drawable.ic_output_usb_dac, resolveOutputListIcon(AudioDeviceCategory.USB_DAC))
        assertEquals(R.drawable.ic_output_speaker, resolveOutputListIcon(AudioDeviceCategory.BUILTIN_SPEAKER))
        assertEquals(R.drawable.ic_output_external_speaker, resolveOutputListIcon(AudioDeviceCategory.EXTERNAL_SPEAKER))
        assertEquals(R.drawable.ic_output_hearing_aid, resolveOutputListIcon(AudioDeviceCategory.HEARING_AID))
        assertEquals(R.drawable.ic_cast, resolveOutputListIcon(AudioDeviceCategory.CAST_DEVICE))
    }
}
