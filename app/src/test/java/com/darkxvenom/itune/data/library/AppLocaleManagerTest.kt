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

class AppLocaleManagerTest {
    @Test
    fun normalizeLanguageChoice_acceptsRegionalTagsForSupportedLanguages() {
        assertEquals(AppLocaleManager.SPANISH, AppLocaleManager.normalizeLanguageChoice("es-ES"))
        assertEquals(AppLocaleManager.FRENCH, AppLocaleManager.normalizeLanguageChoice("fr-CA"))
        assertEquals(AppLocaleManager.DUTCH, AppLocaleManager.normalizeLanguageChoice("nl-NL"))
        assertEquals(AppLocaleManager.RUSSIAN, AppLocaleManager.normalizeLanguageChoice("ru-RU"))
        assertEquals(AppLocaleManager.URDU, AppLocaleManager.normalizeLanguageChoice("ur-PK"))
        assertEquals(AppLocaleManager.CHINESE, AppLocaleManager.normalizeLanguageChoice("zh-Hans-CN"))
        assertEquals(AppLocaleManager.GERMAN, AppLocaleManager.normalizeLanguageChoice("de-DE"))
        assertEquals(AppLocaleManager.PORTUGUESE, AppLocaleManager.normalizeLanguageChoice("pt-BR"))
        assertEquals(AppLocaleManager.ITALIAN, AppLocaleManager.normalizeLanguageChoice("it-IT"))
        assertEquals(AppLocaleManager.JAPANESE, AppLocaleManager.normalizeLanguageChoice("ja-JP"))
        assertEquals(AppLocaleManager.KOREAN, AppLocaleManager.normalizeLanguageChoice("ko-KR"))
        assertEquals(AppLocaleManager.SWEDISH, AppLocaleManager.normalizeLanguageChoice("sv-SE"))
        assertEquals(AppLocaleManager.ARABIC, AppLocaleManager.normalizeLanguageChoice("ar-SA"))
        assertEquals(AppLocaleManager.AZERBAIJANI, AppLocaleManager.normalizeLanguageChoice("az-AZ"))
        assertEquals(AppLocaleManager.BENGALI, AppLocaleManager.normalizeLanguageChoice("bn-BD"))
        assertEquals(AppLocaleManager.BULGARIAN, AppLocaleManager.normalizeLanguageChoice("bg-BG"))
        assertEquals(AppLocaleManager.CATALAN, AppLocaleManager.normalizeLanguageChoice("ca-ES"))
        assertEquals(AppLocaleManager.CZECH, AppLocaleManager.normalizeLanguageChoice("cs-CZ"))
        assertEquals(AppLocaleManager.DANISH, AppLocaleManager.normalizeLanguageChoice("da-DK"))
        assertEquals(AppLocaleManager.FINNISH, AppLocaleManager.normalizeLanguageChoice("fi-FI"))
        assertEquals(AppLocaleManager.GREEK, AppLocaleManager.normalizeLanguageChoice("el-GR"))
        assertEquals(AppLocaleManager.HEBREW, AppLocaleManager.normalizeLanguageChoice("he-IL"))
        assertEquals(AppLocaleManager.HUNGARIAN, AppLocaleManager.normalizeLanguageChoice("hu-HU"))
        assertEquals(AppLocaleManager.INDONESIAN, AppLocaleManager.normalizeLanguageChoice("id-ID"))
        assertEquals(AppLocaleManager.MALAY, AppLocaleManager.normalizeLanguageChoice("ms-MY"))
        assertEquals(AppLocaleManager.NORWEGIAN, AppLocaleManager.normalizeLanguageChoice("nb-NO"))
        assertEquals(AppLocaleManager.PERSIAN, AppLocaleManager.normalizeLanguageChoice("fa-IR"))
        assertEquals(AppLocaleManager.POLISH, AppLocaleManager.normalizeLanguageChoice("pl-PL"))
        assertEquals(AppLocaleManager.ROMANIAN, AppLocaleManager.normalizeLanguageChoice("ro-RO"))
        assertEquals(AppLocaleManager.SLOVAK, AppLocaleManager.normalizeLanguageChoice("sk-SK"))
        assertEquals(AppLocaleManager.THAI, AppLocaleManager.normalizeLanguageChoice("th-TH"))
        assertEquals(AppLocaleManager.TURKISH, AppLocaleManager.normalizeLanguageChoice("tr-TR"))
        assertEquals(AppLocaleManager.UKRAINIAN, AppLocaleManager.normalizeLanguageChoice("uk-UA"))
        assertEquals(AppLocaleManager.VIETNAMESE, AppLocaleManager.normalizeLanguageChoice("vi-VN"))
        assertEquals(AppLocaleManager.SWAHILI, AppLocaleManager.normalizeLanguageChoice("sw-KE"))
        assertEquals(AppLocaleManager.TAGALOG, AppLocaleManager.normalizeLanguageChoice("tl-PH"))
    }
}
