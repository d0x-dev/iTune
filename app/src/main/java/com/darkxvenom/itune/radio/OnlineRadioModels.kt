package com.darkxvenom.itune.radio

import androidx.compose.runtime.Immutable

const val RADIO_ALL_CITIES = "All Cities / Regions"
const val RADIO_PAGE_SIZE = 80

@Immutable
data class RadioCountryOption(
    val code: String,
    val name: String,
    val apiName: String,
    val stationCount: Int,
)

@Immutable
data class RadioCityOption(
    val name: String,
    val stationCount: Int,
)

@Immutable
data class OnlineRadioStation(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val fallbackUrl: String = "",
    val homepage: String = "",
    val faviconUrl: String? = null,
    val countryCode: String = "",
    val countryName: String = "",
    val cityState: String = "",
    val language: String = "",
    val tags: List<String> = emptyList(),
    val codec: String = "MP3",
    val bitrateKbps: Int = 128,
    val votes: Int = 0,
    val isHls: Boolean = false,
) {
    val locationLabel: String
        get() = listOf(cityState, countryName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { countryCode.ifBlank { "Online FM" } }

    val qualityBadge: String
        get() = listOfNotNull(
            codec.takeIf { it.isNotBlank() },
            if (bitrateKbps > 0) "${bitrateKbps}k" else null,
        ).joinToString(" • ").ifBlank { "Live Stream" }

    val subtitleLine: String
        get() = listOf(locationLabel, qualityBadge)
            .filter { it.isNotBlank() }
            .joinToString(" • ")

    val primaryTagSummary: String
        get() = tags.take(3).joinToString(" • ").ifBlank { subtitleLine }

    fun liveShowOrTagsLabel(liveMetadataTitle: String? = null): String {
        val live = liveMetadataTitle?.trim().orEmpty()
        if (live.isNotBlank()) return live
        val formattedTags = tags
            .asSequence()
            .map { it.trim() }
            .filter { it.length >= 2 }
            .take(3)
            .map { word ->
                word.split(' ')
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { token ->
                        token.replaceFirstChar { ch -> ch.uppercaseChar() }
                    }
            }
            .distinctBy { it.lowercase() }
            .joinToString(" • ")
        return formattedTags.ifBlank { qualityBadge }
    }

    fun realHostedByLabel(): String {
        val region = listOf(cityState, countryName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(", ")
        val primaryLanguage = language
            .split(',', '/')
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.replaceFirstChar { it.uppercaseChar() }
        val details = listOfNotNull(
            region.takeIf { it.isNotBlank() },
            primaryLanguage?.takeIf { lang -> !region.contains(lang, ignoreCase = true) },
        ).joinToString(" • ")
        return details.ifBlank { countryCode.ifBlank { qualityBadge } }
    }
}

/**
 * Selects up to [limit] live radio stations for the Home screen carousel,
 * giving strict priority to user-favorited stations first and filling remaining slots
 * cleanly with unliked catalog stations.
 */
fun buildHomeLiveRadioStations(
    favorites: List<OnlineRadioStation>,
    catalog: List<OnlineRadioStation>,
    limit: Int = 10,
): List<OnlineRadioStation> {
    if (limit <= 0) return emptyList()
    val result = ArrayList<OnlineRadioStation>(limit)
    val seen = HashSet<String>(limit * 2)
    for (fav in favorites) {
        if (result.size >= limit) break
        if (seen.add(fav.uuid)) {
            result.add(fav)
        }
    }
    for (station in catalog) {
        if (result.size >= limit) break
        if (seen.add(station.uuid)) {
            result.add(station)
        }
    }
    return result
}

data class RadioStationPage(
    val stations: List<OnlineRadioStation>,
    val rawCount: Int,
    val hasMore: Boolean,
)

@Immutable
data class OnlineRadioUiState(
    val countries: List<RadioCountryOption> = emptyList(),
    val selectedCountry: RadioCountryOption? = null,
    val cities: List<RadioCityOption> = listOf(RadioCityOption(RADIO_ALL_CITIES, 0)),
    val selectedCity: String = RADIO_ALL_CITIES,
    val stations: List<OnlineRadioStation> = emptyList(),
    val favoriteStations: List<OnlineRadioStation> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val loadingCountries: Boolean = false,
    val loadingCities: Boolean = false,
    val loadingStations: Boolean = false,
    val loadingMoreStations: Boolean = false,
    val hasMoreStations: Boolean = false,
    val nextStationOffset: Int = 0,
    val totalRegionStations: Int = 0,
    val searchQuery: String = "",
    val searchResults: List<OnlineRadioStation> = emptyList(),
    val searchingStations: Boolean = false,
    val errorMessage: String? = null,
    val currentStation: OnlineRadioStation? = null,
    val stationQueue: List<OnlineRadioStation> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasActiveSession: Boolean = false,
    val playerExpanded: Boolean = false,
    val liveMetadataTitle: String? = null,
    val sleepTimerRemainingSec: Int? = null,
    val streamError: String? = null,
)
