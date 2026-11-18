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

package com.darkxvenom.itune.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAudioQualityTest {

    @Test
    fun defaultSettingsState_hasLosslessAudioTrueAndExtremeQuality() {
        val state = SettingsUiState()
        assertTrue(state.losslessAudio)
        assertEquals("Extreme", state.audioQuality)
    }

    @Test
    fun qualityConstants_matchExpectedValues() {
        assertEquals("Extreme", SettingsStore.QUALITY_EXTREME)
        assertEquals("High", SettingsStore.QUALITY_HIGH)
        assertEquals("Medium", SettingsStore.QUALITY_MEDIUM)
        assertEquals("Low", SettingsStore.QUALITY_LOW)
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    @Test
    fun normalizeAudioQuality_mapsKnownAndLegacyValues() {
        assertEquals("Extreme", SettingsStore.normalizeAudioQuality("Extreme"))
        assertEquals("High", SettingsStore.normalizeAudioQuality("High"))
        assertEquals("Medium", SettingsStore.normalizeAudioQuality("Medium"))
        assertEquals("Low", SettingsStore.normalizeAudioQuality("Low"))

        // Legacy values
        assertEquals("Extreme", SettingsStore.normalizeAudioQuality("Hi-Res Lossless"))
        assertEquals("Extreme", SettingsStore.normalizeAudioQuality("Lossless (24-bit)"))
        assertEquals("Extreme", SettingsStore.normalizeAudioQuality("Lossless"))
        assertEquals("High", SettingsStore.normalizeAudioQuality("High Quality"))
        assertEquals("Low", SettingsStore.normalizeAudioQuality("High Efficiency"))

        // Null or blank fallback
        assertEquals("Extreme", SettingsStore.normalizeAudioQuality(null))
        assertEquals("Extreme", SettingsStore.normalizeAudioQuality(""))
        assertEquals("Extreme", SettingsStore.normalizeAudioQuality("unknown_bitrate"))
    }
}
