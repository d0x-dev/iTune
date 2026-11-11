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
            R.drawable.ic_airbuds,
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
    }

    @Test
    fun deviceListIcon_usesDistinctXmlPerCategory() {
        assertEquals(R.drawable.ic_airbuds, resolveOutputListIcon(AudioDeviceCategory.BLUETOOTH_BUDS))
        assertEquals(R.drawable.ic_headphone, resolveOutputListIcon(AudioDeviceCategory.WIRED_OR_USB_HEADPHONE))
        assertEquals(R.drawable.ic_output_usb_dac, resolveOutputListIcon(AudioDeviceCategory.USB_DAC))
        assertEquals(R.drawable.ic_output_speaker, resolveOutputListIcon(AudioDeviceCategory.BUILTIN_SPEAKER))
        assertEquals(R.drawable.ic_output_external_speaker, resolveOutputListIcon(AudioDeviceCategory.EXTERNAL_SPEAKER))
        assertEquals(R.drawable.ic_output_hearing_aid, resolveOutputListIcon(AudioDeviceCategory.HEARING_AID))
    }
}
