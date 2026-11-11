package com.darkxvenom.itune.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRadioRepositoryTest {

    @Test
    fun cleanCountryDisplayName_mapsVerboseCodesToCleanNames() {
        assertEquals("United States", cleanCountryDisplayName("US", "The United States Of America"))
        assertEquals("United Kingdom", cleanCountryDisplayName("GB", "The United Kingdom Of Great Britain And Northern Ireland"))
        assertEquals("Russia", cleanCountryDisplayName("RU", "Russian Federation"))
        assertEquals("United Arab Emirates", cleanCountryDisplayName("AE", "The United Arab Emirates"))
        assertEquals("India", cleanCountryDisplayName("IN", "India"))
        assertEquals("JP", cleanCountryDisplayName("JP", ""))
    }

    @Test
    fun parseCountriesJson_filtersInvalidDeduplicatesAndSortsDescending() {
        val raw = """
            [
              {"iso_3166_1": "IN", "name": "India", "stationcount": 420},
              {"iso_3166_1": "us", "name": "The United States Of America", "stationcount": 3200},
              {"iso_3166_1": "us", "name": "USA", "stationcount": 120},
              {"iso_3166_1": "", "name": "Invalid", "stationcount": 900},
              {"iso_3166_1": "XX", "name": "Zero Stations", "stationcount": 0},
              {"iso_3166_1": "GB", "name": "", "stationcount": 500}
            ]
        """.trimIndent()

        val parsed = parseCountriesJson(raw)
        assertEquals(2, parsed.size)
        assertEquals("US", parsed[0].code)
        assertEquals("United States", parsed[0].name)
        assertEquals("The United States Of America", parsed[0].apiName)
        assertEquals(3200, parsed[0].stationCount)

        assertEquals("IN", parsed[1].code)
        assertEquals("India", parsed[1].name)
        assertEquals(420, parsed[1].stationCount)
    }

    @Test
    fun parseStationsJsonArray_filtersNonHttpDeduplicatesAndParsesMetadata() {
        val fallbackCountry = RadioCountryOption(code = "IN", name = "India", apiName = "India", stationCount = 420)
        val raw = """
            [
              {
                "stationuuid": "st-1",
                "name": "Radio Mirchi Mumbai",
                "url_resolved": "https://stream.example.com/mirchi.mp3",
                "url": "http://playlist.example.com/mirchi.pls",
                "homepage": "https://radiomirchi.com",
                "favicon": "https://radiomirchi.com/logo.png",
                "countrycode": "IN",
                "country": "India",
                "state": "Maharashtra",
                "language": "hindi, english",
                "tags": "bollywood, hit, fm",
                "codec": "mp3",
                "bitrate": 128,
                "votes": 950,
                "hls": 0
              },
              {
                "stationuuid": "st-1",
                "name": "Duplicate Station",
                "url_resolved": "https://dup.example.com/live.aac"
              },
              {
                "stationuuid": "st-2",
                "name": "BBC Live HLS",
                "url_resolved": "",
                "url": "https://cdn.example.com/live/master.m3u8",
                "favicon": "ftp://ignored.invalid/icon.png",
                "state": "London",
                "codec": "",
                "bitrate": -10,
                "votes": 50,
                "hls": 0
              },
              {
                "stationuuid": "st-4",
                "name": "Watermark Banner Test",
                "url_resolved": "https://stream.example.com/fm.mp3",
                "favicon": "https://static.mytuner.mobi/media/radios/banner.png"
              },
              {
                "stationuuid": "st-3",
                "name": "Broken Rtmp",
                "url_resolved": "rtmp://invalid.example.com/stream",
                "url": ""
              }
            ]
        """.trimIndent()

        val stations = parseStationsJsonArray(raw, fallbackCountry = fallbackCountry)
        assertEquals(3, stations.size)

        val first = stations[0]
        assertEquals("st-1", first.uuid)
        assertEquals("Radio Mirchi Mumbai", first.name)
        assertEquals("https://stream.example.com/mirchi.mp3", first.streamUrl)
        assertEquals("http://playlist.example.com/mirchi.pls", first.fallbackUrl)
        assertEquals("https://radiomirchi.com/logo.png", first.faviconUrl)
        assertEquals("Maharashtra", first.cityState)
        assertEquals("Maharashtra, India", first.locationLabel)
        assertEquals("MP3 • 128k", first.qualityBadge)
        assertEquals("bollywood • hit • fm", first.primaryTagSummary)
        assertFalse(first.isHls)

        val second = stations[1]
        assertEquals("st-2", second.uuid)
        assertEquals("https://cdn.example.com/live/master.m3u8", second.streamUrl)
        assertNull(second.faviconUrl)
        assertEquals("STREAM", second.codec)
        assertEquals(0, second.bitrateKbps)
        assertTrue(second.isHls)
        assertEquals("IN", second.countryCode)
        assertEquals("India", second.countryName)

        assertNull(stations[2].faviconUrl)
    }

    @Test
    fun parseStationPageFromJson_reportsPaginationHasMoreUsingRawCount() {
        val rawFullPage = """
            [
              {"stationuuid": "p1", "name": "Station 1", "url_resolved": "https://a.example.com/1.mp3"},
              {"stationuuid": "p1", "name": "Duplicate Station 1", "url_resolved": "https://a.example.com/1.mp3"},
              {"stationuuid": "p2", "name": "Station 2", "url_resolved": "https://a.example.com/2.mp3"}
            ]
        """.trimIndent()
        val fullPage = parseStationPageFromJson(rawFullPage, requestedLimit = 3)
        assertEquals(3, fullPage.rawCount)
        assertEquals(2, fullPage.stations.size)
        assertTrue(fullPage.hasMore)

        val partialPage = parseStationPageFromJson(rawFullPage, requestedLimit = 5)
        assertEquals(3, partialPage.rawCount)
        assertEquals(2, partialPage.stations.size)
        assertFalse(partialPage.hasMore)
    }

    @Test
    fun buildHomeLiveRadioStations_prioritizesFavoritesFirstAndCapsAtTen() {
        fun st(id: String) = OnlineRadioStation(
            uuid = id,
            name = "Station $id",
            streamUrl = "https://example.com/$id.mp3",
            countryCode = "IN",
            countryName = "India",
            cityState = "Mumbai",
            language = "hindi, english",
            tags = listOf("lata mangeshkar", "bollywood classic", "hits"),
        )

        val favorites = listOf(st("fav1"), st("fav2"), st("fav3"))
        val catalog = listOf(st("fav2"), st("c1"), st("c2"), st("c3"), st("c4"), st("c5"), st("c6"), st("c7"), st("c8"), st("c9"))

        val result = buildHomeLiveRadioStations(favorites = favorites, catalog = catalog, limit = 10)
        assertEquals(10, result.size)
        assertEquals(listOf("fav1", "fav2", "fav3", "c1", "c2", "c3", "c4", "c5", "c6", "c7"), result.map { it.uuid })

        assertEquals("Lata Mangeshkar • Bollywood Classic • Hits", result[0].liveShowOrTagsLabel())
        assertEquals("Live Artist — Live Track", result[0].liveShowOrTagsLabel("Live Artist — Live Track"))
        assertEquals("Mumbai, India • Hindi", result[0].realHostedByLabel())
    }
}
