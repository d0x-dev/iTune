package com.darkxvenom.itune.audio

import com.darkxvenom.itune.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEnginePolicyTest {

    @Test
    fun pillBadge_defaultsToIMusic128k() {
        val badge = resolveAudioEnginePillBadge(AudioEngineUiState())
        assertTrue(badge.isBrandedITune)
        assertEquals(R.drawable.graphic_eq, badge.iconRes)
        assertEquals("iMusic • 128k", badge.text)
    }

    @Test
    fun pillBadge_switchesToDolbyLogoAndTitleWhenDolbyEnabled() {
        val badge = resolveAudioEnginePillBadge(AudioEngineUiState(dolbyAtmosEnabled = true))
        assertFalse(badge.isBrandedITune)
        assertEquals(R.drawable.ic_dolby_atmos, badge.iconRes)
        assertEquals("Dolby Atmos", badge.text)
    }

    @Test
    fun pillBadge_switchesTo8DAudioAndBitPerfectRespectively() {
        val eightD = resolveAudioEnginePillBadge(AudioEngineUiState(eightDAudioEnabled = true, eightDAudioLevel = 8))
        assertEquals(R.drawable.ic_fx_8d, eightD.iconRes)
        assertEquals("8D Audio", eightD.text)

        val bitPerfect = resolveAudioEnginePillBadge(
            AudioEngineUiState(dolbyAtmosEnabled = true, bitPerfectEnabled = true, bitPerfectActive = true),
        )
        assertEquals(R.drawable.ic_output_bit_perfect, bitPerfect.iconRes)
        assertEquals("Bit-Perfect", bitPerfect.text)
    }
}
