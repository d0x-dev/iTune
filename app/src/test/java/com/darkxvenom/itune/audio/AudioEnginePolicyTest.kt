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
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

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
