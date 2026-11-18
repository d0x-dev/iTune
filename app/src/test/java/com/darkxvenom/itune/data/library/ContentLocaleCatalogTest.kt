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
import org.junit.Test
import java.util.Locale

class ContentLocaleCatalogTest {

    @Test
    fun systemDefault_resolvesFromDeviceLocale() {
        val device = Locale.Builder().setLanguage("hi").setRegion("IN").build()
        assertEquals("hi", ContentLocaleCatalog.resolveEffectiveHl(ContentLocaleCatalog.SYSTEM_DEFAULT, device))
        assertEquals("IN", ContentLocaleCatalog.resolveEffectiveGl(ContentLocaleCatalog.SYSTEM_DEFAULT, device))
    }

    @Test
    fun chineseScripts_resolveToExpectedInnerTubeLanguageCodes() {
        assertEquals("zh-CN", ContentLocaleCatalog.resolveEffectiveHl("zh-Hans", Locale.US))
        assertEquals("zh-TW", ContentLocaleCatalog.resolveEffectiveHl("zh-Hant", Locale.US))
        assertEquals("zh-TW", ContentLocaleCatalog.resolveEffectiveHl("zh-TW", Locale.US))
    }

    @Test
    fun explicitChoice_normalizesAndRoundTripsLabels() {
        assertEquals("hi", ContentLocaleCatalog.normalizeLanguageChoice("Hindi"))
        assertEquals("Hindi", ContentLocaleCatalog.languageLabel("hi"))
        assertEquals("IN", ContentLocaleCatalog.normalizeCountryChoice("India"))
        assertEquals("India", ContentLocaleCatalog.countryLabel("in"))
        assertEquals(ContentLocaleCatalog.SYSTEM_DEFAULT, ContentLocaleCatalog.normalizeCountryChoice("System Default"))
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    @Test
    fun filterVisible_removesExplicitItemsOnlyWhenEnabled() {
        data class Item(val name: String, val explicit: Boolean)
        val items = listOf(Item("Clean Track", false), Item("Explicit Track", true))

        assertEquals(2, ContentLocaleCatalog.filterVisible(items, hideExplicit = false) { it.explicit }.size)
        val cleanOnly = ContentLocaleCatalog.filterVisible(items, hideExplicit = true) { it.explicit }
        assertEquals(listOf(Item("Clean Track", false)), cleanOnly)
    }

    @Test
    fun activeRegionSummary_formatsRegionLanguageAndCleanFlag() {
        assertEquals(
            "India • Hindi • Clean Only",
            ContentLocaleCatalog.activeRegionSummary(
                storedCountry = "IN",
                storedLanguage = "hi",
                hideExplicit = true,
                deviceLocale = Locale.US,
            ),
        )
        assertEquals(
            "United States • English",
            ContentLocaleCatalog.activeRegionSummary(
                storedCountry = "US",
                storedLanguage = "en",
                hideExplicit = false,
                deviceLocale = Locale.US,
            ),
        )
    }
}
