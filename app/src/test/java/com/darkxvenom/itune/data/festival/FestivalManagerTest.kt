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

package com.darkxvenom.itune.data.festival

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalManagerTest {

    @Test
    fun parseFestivalConfig_enabledValidPayload_returnsEnabledConfig() {
        val json = """
            {
              "festival": true,
              "text": "Happy Diwali",
              "color": "#FFD700"
            }
        """.trimIndent()

        val config = FestivalManager.parseFestivalConfig(json)
        assertTrue(config.enabled)
        assertEquals("Happy Diwali", config.text)
        assertEquals("#FFD700", config.colorHex)
        assertEquals(Color(0xFF, 0xD7, 0x00), config.color)
    }

    @Test
    fun parseFestivalConfig_disabledPayload_returnsDisabledConfigWithoutValidatingFields() {
        val json = """
            {
              "festival": false
            }
        """.trimIndent()

        val config = FestivalManager.parseFestivalConfig(json)
        assertFalse(config.enabled)
        assertEquals("", config.text)
        assertEquals("", config.colorHex)
    }

    @Test
    fun parseFestivalConfig_missingRequiredFieldsWhenEnabled_returnsDisabled() {
        val missingText = """{"festival": true, "color": "#FFD700"}"""
        assertFalse(FestivalManager.parseFestivalConfig(missingText).enabled)

        val missingColor = """{"festival": true, "text": "Happy Holi"}"""
        assertFalse(FestivalManager.parseFestivalConfig(missingColor).enabled)

        val blankText = """{"festival": true, "text": "   ", "color": "#FFD700"}"""
        assertFalse(FestivalManager.parseFestivalConfig(blankText).enabled)

        val invalidColor = """{"festival": true, "text": "Happy Holi", "color": "invalid_hex"}"""
        assertFalse(FestivalManager.parseFestivalConfig(invalidColor).enabled)
    }

    @Test
    fun parseFestivalConfig_emptyOrCorruptJson_returnsDisabledWithoutCrash() {
        assertFalse(FestivalManager.parseFestivalConfig("").enabled)
        assertFalse(FestivalManager.parseFestivalConfig("   ").enabled)
        assertFalse(FestivalManager.parseFestivalConfig("{ broken json").enabled)
        assertFalse(FestivalManager.parseFestivalConfig("null").enabled)
    }

    @Test
    fun parseHexColor_handlesRgbRrgbbAarrgbbFormats() {
        // #RGB -> 0xFFD -> (0xFF, 0xFF, 0xDD)
        val shortHex = FestivalManager.parseHexColor("#F0A")
        assertNotNull(shortHex)
        assertEquals(Color(0xFF, 0x00, 0xAA), shortHex)

        // #RRGGBB -> #FFD700
        val stdHex = FestivalManager.parseHexColor("#FFD700")
        assertNotNull(stdHex)
        assertEquals(Color(0xFF, 0xD7, 0x00), stdHex)

        // Without hash prefix
        val noHashHex = FestivalManager.parseHexColor("FFD700")
        assertNotNull(noHashHex)
        assertEquals(Color(0xFF, 0xD7, 0x00), noHashHex)

        // #AARRGGBB
        val alphaHex = FestivalManager.parseHexColor("#80FFD700")
        assertNotNull(alphaHex)
        assertEquals(Color(0xFF, 0xD7, 0x00, 0x80), alphaHex)

        // Invalid formats
        assertNull(FestivalManager.parseHexColor("not_a_hex"))
        assertNull(FestivalManager.parseHexColor("#12345"))
        assertNull(FestivalManager.parseHexColor(""))
    }
}
