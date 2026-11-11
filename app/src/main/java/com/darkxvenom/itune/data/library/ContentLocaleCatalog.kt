package com.darkxvenom.itune.data.library

import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class ContentLocaleOption(
    val code: String,
    val displayName: String,
    val detail: String,
)

/**
 * AirBeats / YouTube Music InnerTube content language (`hl`), content country (`gl`),
 * and explicit-content filtering catalog & resolution helpers.
 */
object ContentLocaleCatalog {
    const val SYSTEM_DEFAULT = "SYSTEM"

    val languages: List<ContentLocaleOption> = listOf(
        ContentLocaleOption(SYSTEM_DEFAULT, "System Default", "Follow device language setting"),
        ContentLocaleOption("en", "English", "Global & English catalog"),
        ContentLocaleOption("hi", "Hindi", "हिन्दी music, moods & Bollywood mixes"),
        ContentLocaleOption("pa", "Punjabi", "ਪੰਜਾਬੀ beats & regional hits"),
        ContentLocaleOption("es", "Spanish", "Español, Latin & Urbana releases"),
        ContentLocaleOption("fr", "French", "Français & francophone charts"),
        ContentLocaleOption("de", "German", "Deutsch hits & moods"),
        ContentLocaleOption("it", "Italian", "Italiano pop & curated playlists"),
        ContentLocaleOption("pt", "Portuguese", "Português & Brazilian hits"),
        ContentLocaleOption("ja", "Japanese", "日本語 J-Pop, Anime & City Pop"),
        ContentLocaleOption("ko", "Korean", "한국어 K-Pop & R&B charts"),
        ContentLocaleOption("zh-Hans", "Chinese (Simplified)", "简体中文 Mandopop & Asian hits"),
        ContentLocaleOption("zh-Hant", "Chinese (Traditional)", "繁體中文 Mandopop & Cantopop"),
        ContentLocaleOption("ar", "Arabic", "العربية Arabic pop & chill mixes"),
        ContentLocaleOption("tr", "Turkish", "Türkçe hits & Anatolian rock"),
        ContentLocaleOption("id", "Indonesian", "Bahasa Indonesia viral charts"),
        ContentLocaleOption("ru", "Russian", "Русский charts & electronic"),
        ContentLocaleOption("bn", "Bengali", "বাংলা regional releases & playlists"),
        ContentLocaleOption("ta", "Tamil", "தமிழ் Kollywood & indie playlists"),
        ContentLocaleOption("te", "Telugu", "తెలుగు Tollywood & trending hits"),
        ContentLocaleOption("mr", "Marathi", "मराठी regional playlists & hits"),
        ContentLocaleOption("ur", "Urdu", "اردو ghazals, sufi & pop"),
        ContentLocaleOption("nl", "Dutch", "Nederlands hits & dance mixes"),
        ContentLocaleOption("pl", "Polish", "Polski hip-hop & pop charts"),
        ContentLocaleOption("vi", "Vietnamese", "Tiếng Việt V-Pop playlists"),
        ContentLocaleOption("th", "Thai", "ไทย T-Pop & chill moods"),
    )

    val countries: List<ContentLocaleOption> = listOf(
        ContentLocaleOption(SYSTEM_DEFAULT, "System Default", "Follow device country / region"),
        ContentLocaleOption("US", "United States", "US Billboard, Hip-Hop & Global Mixes"),
        ContentLocaleOption("IN", "India", "India Top Charts, Hindi, Punjabi & Regional"),
        ContentLocaleOption("GB", "United Kingdom", "UK Singles, Grime, Indie & Electronic"),
        ContentLocaleOption("CA", "Canada", "Canada Top 100 & R&B Releases"),
        ContentLocaleOption("AU", "Australia", "Australia Trending, Rock & Electronic"),
        ContentLocaleOption("JP", "Japan", "Japan Oricon, J-Pop & Anime Releases"),
        ContentLocaleOption("KR", "South Korea", "South Korea Melon, K-Pop & Osts"),
        ContentLocaleOption("DE", "Germany", "Germany Offizielle Charts & Club"),
        ContentLocaleOption("FR", "France", "France SNEP Top, Chanson & Electro"),
        ContentLocaleOption("ES", "Spain", "Spain Promusicae & Latin Hits"),
        ContentLocaleOption("IT", "Italy", "Italy FIMI Top & Mediterranean Pop"),
        ContentLocaleOption("BR", "Brazil", "Brazil Sertanejo, Funk & Phonk"),
        ContentLocaleOption("MX", "Mexico", "Mexico Regional & Latin Pop"),
        ContentLocaleOption("ID", "Indonesia", "Indonesia Top Viral & Nusantara Pop"),
        ContentLocaleOption("TR", "Turkey", "Turkey Top 100 & Türkçe Rap"),
        ContentLocaleOption("AE", "United Arab Emirates", "UAE Global & Arabic Mixes"),
        ContentLocaleOption("SA", "Saudi Arabia", "Saudi Khaliji & Arabic Trending"),
        ContentLocaleOption("RU", "Russia", "Russia Pop, Rap & Dance Charts"),
        ContentLocaleOption("NL", "Netherlands", "Netherlands Dutch & EDM Releases"),
        ContentLocaleOption("SE", "Sweden", "Sweden Nordic Pop & Dance"),
        ContentLocaleOption("PL", "Poland", "Poland OLiS Trending Releases"),
        ContentLocaleOption("TW", "Taiwan", "Taiwan Mandopop & Indie Charts"),
        ContentLocaleOption("HK", "Hong Kong", "Hong Kong Cantopop & Asian Hits"),
        ContentLocaleOption("SG", "Singapore", "Singapore SEA Top Playlists"),
        ContentLocaleOption("PK", "Pakistan", "Pakistan Coke Studio & Sufi Hits"),
        ContentLocaleOption("NG", "Nigeria", "Nigeria Afrobeats & Amapiano"),
        ContentLocaleOption("ZA", "South Africa", "South Africa Amapiano & House"),
    )

    fun resolveEffectiveHl(
        storedLanguage: String?,
        deviceLocale: Locale = Locale.getDefault(),
    ): String {
        val raw = normalizeLanguageChoice(storedLanguage)
        val effective = if (raw == SYSTEM_DEFAULT) {
            deviceLocale.toLanguageTag().ifBlank { "en" }
        } else {
            raw
        }
        return when (val lower = effective.lowercase(Locale.ROOT).replace('_', '-')) {
            "zh-hant", "zh-tw", "zh-hk", "zh-mo" -> "zh-TW"
            "zh-hans", "zh-cn", "zh-sg" -> "zh-CN"
            else -> lower.substringBefore('-').ifBlank { "en" }
        }
    }

    fun resolveEffectiveGl(
        storedCountry: String?,
        deviceLocale: Locale = Locale.getDefault(),
    ): String {
        val raw = normalizeCountryChoice(storedCountry)
        if (raw != SYSTEM_DEFAULT) return raw
        val deviceCountry = deviceLocale.country.uppercase(Locale.ROOT)
        return if (deviceCountry.length == 2 && deviceCountry.all { it.isLetter() }) {
            deviceCountry
        } else {
            "US"
        }
    }

    fun normalizeLanguageChoice(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("System Default", ignoreCase = true) || trimmed.equals(SYSTEM_DEFAULT, ignoreCase = true)) {
            return SYSTEM_DEFAULT
        }
        return languages.firstOrNull {
            it.code.equals(trimmed, ignoreCase = true) || it.displayName.equals(trimmed, ignoreCase = true)
        }?.code ?: trimmed
    }

    fun normalizeCountryChoice(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals("System Default", ignoreCase = true) || trimmed.equals(SYSTEM_DEFAULT, ignoreCase = true)) {
            return SYSTEM_DEFAULT
        }
        return countries.firstOrNull {
            it.code.equals(trimmed, ignoreCase = true) || it.displayName.equals(trimmed, ignoreCase = true)
        }?.code ?: trimmed.uppercase(Locale.ROOT).take(2).ifBlank { SYSTEM_DEFAULT }
    }

    fun languageLabel(storedCode: String?): String {
        val norm = normalizeLanguageChoice(storedCode)
        return languages.firstOrNull { it.code.equals(norm, ignoreCase = true) }?.displayName ?: norm
    }

    fun countryLabel(storedCode: String?): String {
        val norm = normalizeCountryChoice(storedCode)
        return countries.firstOrNull { it.code.equals(norm, ignoreCase = true) }?.displayName ?: norm
    }

    inline fun <T> filterVisible(
        items: List<T>,
        hideExplicit: Boolean,
        crossinline isExplicit: (T) -> Boolean,
    ): List<T> = if (hideExplicit) items.filterNot { isExplicit(it) } else items

    fun activeRegionSummary(
        storedCountry: String?,
        storedLanguage: String?,
        hideExplicit: Boolean,
        deviceLocale: Locale = Locale.getDefault(),
    ): String {
        val gl = resolveEffectiveGl(storedCountry, deviceLocale)
        val hl = resolveEffectiveHl(storedLanguage, deviceLocale)
        val countryName = countries.firstOrNull { it.code.equals(gl, ignoreCase = true) }?.displayName ?: gl
        val languageName = languages.firstOrNull { it.code.equals(hl, ignoreCase = true) }?.displayName ?: hl.uppercase(Locale.ROOT)
        return if (hideExplicit) {
            "$countryName • $languageName • Clean Only"
        } else {
            "$countryName • $languageName"
        }
    }
}
