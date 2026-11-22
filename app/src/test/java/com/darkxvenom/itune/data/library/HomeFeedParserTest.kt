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

import com.darkxvenom.itune.data.model.HomeFeedItem
import com.darkxvenom.itune.data.model.HomeFeedShelf
import com.darkxvenom.itune.data.model.HomeItemKind
import com.darkxvenom.itune.data.model.HomeShelfGroup
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedParserTest {

    private val sample = """
    {"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{
      "contents":[
       {"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Quick picks"}]}}},
        "contents":[
         {"musicResponsiveListItemRenderer":{"thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://x/a=w60"},{"url":"https://x/a=w226"}]}}},
           "playlistItemData":{"videoId":"vid1"},
           "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Blinding Lights"}]}}},
                          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"The Weeknd","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist1","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}},{"text":" • "},{"text":"After Hours","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_abc","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}}]}}}]}}
        ]}},
       {"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Albums for you"}]}}},
        "contents":[
         {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Dawn FM"}]},
           "subtitle":{"runs":[{"text":"Album"},{"text":" • "},{"text":"The Weeknd","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist1"}}},{"text":" • "},{"text":"2022"}]},
           "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_dawn","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}}},
         {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"My Supermix"}]},"subtitle":{"runs":[{"text":"Playlist"}]},
           "navigationEndpoint":{"watchPlaylistEndpoint":{"playlistId":"RDTMAK5uy_x"}}}},
         {"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"Some Podcast"}]},"subtitle":{"runs":[{"text":"Podcast"},{"text":" • "},{"text":"Host"}]},
           "navigationEndpoint":{"browseEndpoint":{"browseId":"MPSPPLabc"}}}}
        ]}}
      ],
      "continuations":[{"nextContinuationData":{"continuation":"TOKEN123"}}]}}}}]}}}
    """
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    @Test
    fun parse_readsShelvesSongsAndContinuation() {
        val page = HomeFeedParser.parse(JSONObject(sample), HomeShelfGroup.FEED, "t")
        assertEquals(2, page.shelves.size)
        assertEquals("TOKEN123", page.continuation)

        val quick = page.shelves[0]
        assertTrue(quick.isSongShelf)
        val song = quick.items.single()
        assertEquals("vid1", song.videoId)
        assertEquals("The Weeknd", song.artistName)
        assertEquals("UCartist1", song.artistId)
        assertEquals("After Hours", song.albumName)
        assertEquals("MPREb_abc", song.albumId)
        assertEquals("vid1", song.toMusicTrack()?.id)
    }

    @Test
    fun parse_classifiesAlbumsMixesAndPodcasts() {
        val shelf = HomeFeedParser.parse(JSONObject(sample), HomeShelfGroup.FEED, "t").shelves[1]
        val kinds = shelf.items.associate { it.title to it.kind }
        assertEquals(HomeItemKind.ALBUM, kinds["Dawn FM"])
        assertEquals(HomeItemKind.PLAYLIST, kinds["My Supermix"])
        assertEquals(HomeItemKind.PODCAST, kinds["Some Podcast"])

        val album = shelf.items.first { it.title == "Dawn FM" }
        assertEquals("MPREb_dawn", album.browseId)
        assertEquals("The Weeknd", album.artistName)
        assertFalse(album.subtitle.startsWith("Album"))

        val mix = shelf.items.first { it.title == "My Supermix" }
        assertNull(mix.browseId)
        assertEquals("RDTMAK5uy_x", mix.watchPlaylistId)
    }

    @Test
    fun parse_emptyOrUnknownResponse_returnsNoShelves() {
        val page = HomeFeedParser.parse(JSONObject("{}"), HomeShelfGroup.FEED, "t")
        assertTrue(page.shelves.isEmpty())
        assertNull(page.continuation)
    }

    @Test
    fun feedItem_nonTrackHasNoMusicTrack() {
        val artist = HomeFeedItem(kind = HomeItemKind.ARTIST, title = "A", browseId = "UC1")
        assertNull(artist.toMusicTrack())
        assertFalse(artist.isPlayableTrack)
        assertTrue(HomeFeedShelf("k", "t", items = listOf(artist)).isArtistShelf)
    }
    @Test
    fun parse_keepsViewCountOutOfTheArtistName() {
        val runs = """[{"text":"Naresh NK"},{"text":" • "},{"text":"5.6m views"}]"""
        val json = JSONObject("""{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"Trending"}]}}},"contents":[{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"a"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Arz Kiya Hai"}]}}},{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":$runs}}}]}}]}}]}}}}]}}}""")
        val item = HomeFeedParser.parse(json, HomeShelfGroup.FEED, "t").shelves.single().items.single()
        assertEquals("Naresh NK", item.artistName)
        assertEquals("5.6m views", item.views)
        assertEquals("Naresh NK", item.toMusicTrack()?.artists)
    }
}
