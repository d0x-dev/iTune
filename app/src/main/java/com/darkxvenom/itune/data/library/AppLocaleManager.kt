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

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Applies the user-selected app language while keeping catalog language independent. */
object AppLocaleManager {
    const val SYSTEM_DEFAULT = "system"
    const val ENGLISH = "en"
    const val HINDI = "hi"
    const val SPANISH = "es"
    const val FRENCH = "fr"
    const val DUTCH = "nl"
    const val RUSSIAN = "ru"
    const val URDU = "ur"
    const val CHINESE = "zh"
    const val GERMAN = "de"
    const val PORTUGUESE = "pt"
    const val ITALIAN = "it"
    const val JAPANESE = "ja"
    const val KOREAN = "ko"
    const val SWEDISH = "sv"
    const val ARABIC = "ar"
    const val AZERBAIJANI = "az"
    const val BENGALI = "bn"
    const val BULGARIAN = "bg"
    const val CATALAN = "ca"
    const val CZECH = "cs"
    const val DANISH = "da"
    const val FINNISH = "fi"
    const val GREEK = "el"
    const val HEBREW = "he"
    const val HUNGARIAN = "hu"
    const val INDONESIAN = "id"
    const val MALAY = "ms"
    const val NORWEGIAN = "nb"
    const val PERSIAN = "fa"
    const val POLISH = "pl"
    const val ROMANIAN = "ro"
    const val SLOVAK = "sk"
    const val THAI = "th"
    const val TURKISH = "tr"
    const val UKRAINIAN = "uk"
    const val VIETNAMESE = "vi"
    const val SWAHILI = "sw"
    const val TAGALOG = "tl"

    private val supportedLanguages = setOf(
        SYSTEM_DEFAULT, ENGLISH, HINDI, SPANISH, FRENCH, DUTCH, RUSSIAN, URDU, CHINESE,
        GERMAN, PORTUGUESE, ITALIAN, JAPANESE, KOREAN, SWEDISH, ARABIC,
        AZERBAIJANI,
        BENGALI,
        BULGARIAN,
        CATALAN,
        CZECH,
        DANISH,
        FINNISH,
        GREEK,
        HEBREW,
        HUNGARIAN,
        INDONESIAN,
        MALAY,
        NORWEGIAN,
        PERSIAN,
        POLISH,
        ROMANIAN,
        SLOVAK,
        THAI,
        TURKISH,
        UKRAINIAN,
        VIETNAMESE,
        SWAHILI,
        TAGALOG,
    )

    private const val PREFS_NAME = "itune_app_locale"
    private const val KEY_LANGUAGE = "language"

    fun normalizeLanguageChoice(language: String): String {
        if (language.equals(SYSTEM_DEFAULT, ignoreCase = true)) return SYSTEM_DEFAULT

        val languageCode = Locale.forLanguageTag(language.replace('_', '-'))
            .language
            .lowercase(Locale.ROOT)
        return languageCode.takeIf(supportedLanguages::contains) ?: SYSTEM_DEFAULT
    }

    fun currentLanguage(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val platformLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!platformLocales.isEmpty) return normalizeLanguageChoice(platformLocales.toLanguageTags().substringBefore(','))
        }
        return normalizeLanguageChoice(preferences(context).getString(KEY_LANGUAGE, SYSTEM_DEFAULT) ?: SYSTEM_DEFAULT)
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val language = currentLanguage(context)
        if (language == SYSTEM_DEFAULT) return context

        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return context.createConfigurationContext(configuration)
    }

    /** Returns true when the caller should recreate its Activity after applying the locale. */
    fun setLanguage(context: Context, language: String): Boolean {
        val normalized = normalizeLanguageChoice(language)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (normalized == SYSTEM_DEFAULT) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(normalized)
            context.getSystemService(LocaleManager::class.java).applicationLocales = locales
            return false
        }
        preferences(context).edit().putString(KEY_LANGUAGE, normalized).apply()
        val locales = if (normalized == SYSTEM_DEFAULT) systemLocales() else listOf(Locale.forLanguageTag(normalized))
        val locale = locales.firstOrNull() ?: Locale.getDefault()
        val configuration = Configuration(context.resources.configuration).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(*locales.toTypedArray()))
            } else {
                @Suppress("DEPRECATION")
                this.locale = locale
            }
            setLayoutDirection(locale)
        }
        Locale.setDefault(locale)
        @Suppress("DEPRECATION")
        context.applicationContext.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        return true
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun systemLocales(): List<Locale> {
        val systemConfiguration = Resources.getSystem().configuration
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val locales = systemConfiguration.locales
            if (locales.isEmpty) listOf(LocaleList.getDefault()[0])
            else (0 until locales.size()).map(locales::get)
        } else {
            @Suppress("DEPRECATION")
            listOf(systemConfiguration.locale ?: Locale.getDefault())
        }
    }
}
