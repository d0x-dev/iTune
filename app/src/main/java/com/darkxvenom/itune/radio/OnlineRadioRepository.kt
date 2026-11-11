package com.darkxvenom.itune.radio

import android.content.Context
import android.net.Uri
import com.darkxvenom.itune.backend.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Fetches global countries, cities/states, paginated station feeds (supporting 10,000+ stations without
 * memory spikes or hiding stations), and server-side regional search from Radio Browser API mirrors.
 */
object OnlineRadioRepository {
    private const val PREFS_NAME = "itune_online_radio_store"
    private const val KEY_COUNTRY_CODE = "selected_country_code"
    private const val KEY_COUNTRY_NAME = "selected_country_name"
    private const val KEY_COUNTRY_API_NAME = "selected_country_api_name"
    private const val KEY_CITY = "selected_city"
    private const val KEY_FAVORITES_JSON = "favorites_json"
    private const val MAX_REGION_PAGE0_CACHE = 8

    private val API_MIRRORS = listOf(
        "https://de1.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
    )

    private var activeMirror: String = API_MIRRORS.first()
    private val countriesCache = mutableListOf<RadioCountryOption>()
    private val countryCitiesCache = object : LinkedHashMap<String, List<RadioCityOption>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<RadioCityOption>>?): Boolean =
            size > MAX_REGION_PAGE0_CACHE
    }
    private val pageZeroCache = object : LinkedHashMap<String, RadioStationPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RadioStationPage>?): Boolean =
            size > MAX_REGION_PAGE0_CACHE
    }

    fun loadSavedCountry(context: Context): RadioCountryOption? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_COUNTRY_CODE, null)?.trim()?.uppercase(Locale.ROOT)
            ?.takeIf { it.length == 2 } ?: return null
        val name = prefs.getString(KEY_COUNTRY_NAME, null)?.ifBlank { null } ?: code
        val apiName = prefs.getString(KEY_COUNTRY_API_NAME, null)?.ifBlank { null } ?: name
        return RadioCountryOption(code = code, name = name, apiName = apiName, stationCount = 0)
    }

    fun saveSelectedCountry(context: Context, country: RadioCountryOption) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COUNTRY_CODE, country.code)
            .putString(KEY_COUNTRY_NAME, country.name)
            .putString(KEY_COUNTRY_API_NAME, country.apiName)
            .apply()
    }

    fun loadSavedCity(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CITY, RADIO_ALL_CITIES)
            ?.ifBlank { RADIO_ALL_CITIES } ?: RADIO_ALL_CITIES
    }

    fun saveSelectedCity(context: Context, city: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CITY, city.ifBlank { RADIO_ALL_CITIES })
            .apply()
    }

    fun loadFavorites(context: Context): List<OnlineRadioStation> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FAVORITES_JSON, null) ?: return emptyList()
        return parseStationsJsonArray(raw)
    }

    fun saveFavorites(context: Context, stations: List<OnlineRadioStation>) {
        val arr = JSONArray()
        stations.forEach { st ->
            arr.put(
                JSONObject().apply {
                    put("stationuuid", st.uuid)
                    put("name", st.name)
                    put("url_resolved", st.streamUrl)
                    put("url", st.fallbackUrl)
                    put("homepage", st.homepage)
                    put("favicon", st.faviconUrl.orEmpty())
                    put("countrycode", st.countryCode)
                    put("country", st.countryName)
                    put("state", st.cityState)
                    put("language", st.language)
                    put("tags", st.tags.joinToString(","))
                    put("codec", st.codec)
                    put("bitrate", st.bitrateKbps)
                    put("votes", st.votes)
                    put("hls", if (st.isHls) 1 else 0)
                },
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAVORITES_JSON, arr.toString())
            .apply()
    }

    suspend fun fetchCountries(forceRefresh: Boolean = false): List<RadioCountryOption> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && countriesCache.isNotEmpty()) {
                return@withContext countriesCache.toList()
            }
            val raw = getJsonString("/json/countries?order=stationcount&reverse=true&hidebroken=true")
                ?: return@withContext countriesCache.toList()
            val parsed = parseCountriesJson(raw)
            if (parsed.isNotEmpty()) {
                synchronized(countriesCache) {
                    countriesCache.clear()
                    countriesCache.addAll(parsed)
                }
            }
            parsed.ifEmpty { countriesCache.toList() }
        }

    suspend fun fetchCitiesForCountry(
        country: RadioCountryOption,
        forceRefresh: Boolean = false,
    ): List<RadioCityOption> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            synchronized(countryCitiesCache) {
                countryCitiesCache[country.code]?.let { return@withContext it }
            }
        }
        val encodedCountry = Uri.encode(country.apiName)
        val statesRaw = getJsonString("/json/states/$encodedCountry/?order=stationcount&reverse=true&hidebroken=true")
        val stateCounts = linkedMapOf<String, Int>()
        if (!statesRaw.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(statesRaw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    val cnt = obj.optInt("stationcount", 0)
                    if (name.isNotBlank() && cnt > 0) {
                        stateCounts[name] = maxOf(stateCounts[name] ?: 0, cnt)
                    }
                }
            }
        }

        // Only if /states/ returned nothing, seed city list from page 0 without fetching huge dumps
        if (stateCounts.isEmpty()) {
            val sample = fetchStationsPage(country, RADIO_ALL_CITIES, offset = 0, limit = 80, forceRefresh = forceRefresh)
            sample.stations.forEach { st ->
                if (st.cityState.isNotBlank()) {
                    stateCounts[st.cityState] = (stateCounts[st.cityState] ?: 0) + 1
                }
            }
        }

        val sortedCities = stateCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { (name, count) -> RadioCityOption(name = name, stationCount = count) }

        val fullList = listOf(
            RadioCityOption(
                name = RADIO_ALL_CITIES,
                stationCount = country.stationCount,
            ),
        ) + sortedCities

        synchronized(countryCitiesCache) {
            countryCitiesCache[country.code] = fullList
        }
        fullList
    }

    suspend fun fetchStationsPage(
        country: RadioCountryOption,
        city: String,
        offset: Int,
        limit: Int = RADIO_PAGE_SIZE,
        forceRefresh: Boolean = false,
    ): RadioStationPage = withContext(Dispatchers.IO) {
        val normalizedCity = city.trim().ifBlank { RADIO_ALL_CITIES }
        val cacheKey = "${country.code}|$normalizedCity|$limit"
        if (offset == 0 && !forceRefresh) {
            synchronized(pageZeroCache) {
                pageZeroCache[cacheKey]?.let { return@withContext it }
            }
        }

        val path = buildString {
            append("/json/stations/search?countrycode=")
            append(Uri.encode(country.code))
            if (normalizedCity != RADIO_ALL_CITIES) {
                append("&state=")
                append(Uri.encode(normalizedCity))
                append("&stateExact=true")
            }
            append("&limit=")
            append(limit)
            append("&offset=")
            append(offset.coerceAtLeast(0))
            append("&hidebroken=true&order=clickcount&reverse=true")
        }

        val raw = getJsonString(path)
        if (raw.isNullOrBlank()) {
            if (offset == 0) {
                synchronized(pageZeroCache) {
                    pageZeroCache[cacheKey]?.let { return@withContext it }
                }
            }
            return@withContext RadioStationPage(stations = emptyList(), rawCount = 0, hasMore = false)
        }

        val page = parseStationPageFromJson(raw, fallbackCountry = country, requestedLimit = limit)
        if (offset == 0 && page.stations.isNotEmpty()) {
            synchronized(pageZeroCache) {
                pageZeroCache[cacheKey] = page
            }
        }
        page
    }

    suspend fun searchStationsInRegion(
        country: RadioCountryOption,
        city: String,
        query: String,
        limit: Int = RADIO_PAGE_SIZE,
    ): List<OnlineRadioStation> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext emptyList()
        val normalizedCity = city.trim().ifBlank { RADIO_ALL_CITIES }
        val path = buildString {
            append("/json/stations/search?countrycode=")
            append(Uri.encode(country.code))
            if (normalizedCity != RADIO_ALL_CITIES) {
                append("&state=")
                append(Uri.encode(normalizedCity))
                append("&stateExact=true")
            }
            append("&name=")
            append(Uri.encode(clean))
            append("&limit=")
            append(limit)
            append("&hidebroken=true&order=clickcount&reverse=true")
        }
        val raw = getJsonString(path) ?: return@withContext emptyList()
        parseStationsJsonArray(raw, fallbackCountry = country)
    }

    suspend fun recordStationClick(stationUuid: String) = withContext(Dispatchers.IO) {
        if (stationUuid.isBlank()) return@withContext
        getJsonString("/json/url/${Uri.encode(stationUuid)}")
    }

    private fun getJsonString(path: String): String? {
        val orderedMirrors = (listOf(activeMirror) + API_MIRRORS).distinct()
        for (base in orderedMirrors) {
            val req = Request.Builder()
                .url(base + path)
                .header("User-Agent", "iTuneAndroid/1.0")
                .header("Accept", "application/json")
                .build()
            val body = runCatching {
                Net.client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }.getOrNull()
            if (!body.isNullOrBlank()) {
                activeMirror = base
                return body
            }
        }
        return null
    }
}

internal fun cleanCountryDisplayName(code: String, rawName: String): String = when (code.uppercase(Locale.ROOT)) {
    "US" -> "United States"
    "GB" -> "United Kingdom"
    "RU" -> "Russia"
    "KR" -> "South Korea"
    "IR" -> "Iran"
    "AE" -> "United Arab Emirates"
    "CZ" -> "Czechia"
    else -> rawName.trim().ifBlank { code }
}

internal fun parseCountriesJson(raw: String): List<RadioCountryOption> = runCatching {
    val arr = JSONArray(raw)
    val byCode = linkedMapOf<String, RadioCountryOption>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val code = obj.optString("iso_3166_1").trim().uppercase(Locale.ROOT)
        val rawName = obj.optString("name").trim()
        val count = obj.optInt("stationcount", 0)
        if (code.length != 2 || rawName.isBlank() || count <= 0) continue
        val option = RadioCountryOption(
            code = code,
            name = cleanCountryDisplayName(code, rawName),
            apiName = rawName,
            stationCount = count,
        )
        val existing = byCode[code]
        if (existing == null || count > existing.stationCount) {
            byCode[code] = option
        }
    }
    byCode.values.sortedByDescending { it.stationCount }
}.getOrDefault(emptyList())

internal fun parseStationPageFromJson(
    raw: String,
    fallbackCountry: RadioCountryOption? = null,
    requestedLimit: Int = RADIO_PAGE_SIZE,
): RadioStationPage = runCatching {
    val arr = JSONArray(raw)
    val rawLen = arr.length()
    val stations = parseJsonArrayToStations(arr, fallbackCountry = fallbackCountry)
    RadioStationPage(
        stations = stations,
        rawCount = rawLen,
        hasMore = rawLen >= requestedLimit,
    )
}.getOrDefault(RadioStationPage(stations = emptyList(), rawCount = 0, hasMore = false))

internal fun parseStationsJsonArray(
    raw: String,
    fallbackCountry: RadioCountryOption? = null,
): List<OnlineRadioStation> = runCatching {
    parseJsonArrayToStations(JSONArray(raw), fallbackCountry = fallbackCountry)
}.getOrDefault(emptyList())

private fun parseJsonArrayToStations(
    arr: JSONArray,
    fallbackCountry: RadioCountryOption?,
): List<OnlineRadioStation> = buildList(arr.length()) {
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val uuid = obj.optString("stationuuid").trim()
        val name = obj.optString("name").trim()
        val resolved = obj.optString("url_resolved").trim()
        val rawUrl = obj.optString("url").trim()
        val streamUrl = resolved.ifBlank { rawUrl }
        if (uuid.isBlank() || name.isBlank() || streamUrl.isBlank()) continue
        if (!streamUrl.startsWith("http://", ignoreCase = true) &&
            !streamUrl.startsWith("https://", ignoreCase = true)
        ) {
            continue
        }

        val code = obj.optString("countrycode").trim().uppercase(Locale.ROOT)
            .ifBlank { fallbackCountry?.code.orEmpty() }
        val rawCountry = obj.optString("country").trim()
        val countryName = if (rawCountry.isNotBlank()) {
            cleanCountryDisplayName(code, rawCountry)
        } else {
            fallbackCountry?.name.orEmpty()
        }
        val favicon = obj.optString("favicon").trim().takeIf {
            (it.startsWith("http://", ignoreCase = true) ||
                it.startsWith("https://", ignoreCase = true)) &&
                !it.contains("mytuner", ignoreCase = true)
        }
        val tags = obj.optString("tags").split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        add(
            OnlineRadioStation(
                uuid = uuid,
                name = name,
                streamUrl = streamUrl,
                fallbackUrl = rawUrl.takeIf { it != streamUrl && it.startsWith("http", ignoreCase = true) }.orEmpty(),
                homepage = obj.optString("homepage").trim(),
                faviconUrl = favicon,
                countryCode = code,
                countryName = countryName,
                cityState = obj.optString("state").trim(),
                language = obj.optString("language").trim(),
                tags = tags,
                codec = obj.optString("codec").trim().uppercase(Locale.ROOT).ifBlank { "STREAM" },
                bitrateKbps = obj.optInt("bitrate", 0).coerceAtLeast(0),
                votes = obj.optInt("votes", 0).coerceAtLeast(0),
                isHls = obj.optInt("hls", 0) == 1 || streamUrl.contains(".m3u8", ignoreCase = true),
            ),
        )
    }
}.distinctBy { it.uuid }
