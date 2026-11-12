package com.darkxvenom.itune.data.auth

import com.darkxvenom.itune.data.model.BrowseType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAccountAuthAndParsersTest {

    @Test
    fun hasApiSid_matchesExactCookieNamesOnly() {
        assertTrue(hasApiSid("VISITOR_INFO1=abc; SAPISID=secret123; SID=xyz"))
        assertTrue(hasApiSid("__Secure-3PAPISID=partitionedSecret; SID=xyz"))
        assertTrue(hasApiSid("__Secure-1PAPISID=firstPartySecret"))
        assertFalse(hasApiSid("SAPISID=; SID=xyz"))
        assertFalse(hasApiSid("FAKE_SAPISID=notReal; SID=xyz"))
        assertFalse(hasApiSid(""))
        assertFalse(hasApiSid(null))
    }

    @Test
    fun sapisidFrom_prioritizesPlainOverSecureCookies() {
        val cookie = "__Secure-1PAPISID=s1; __Secure-3PAPISID=s3; SAPISID=plain"
        assertEquals("plain", sapisidFrom(cookie))

        val secureOnly = "__Secure-1PAPISID=s1; __Secure-3PAPISID=s3"
        assertEquals("s3", sapisidFrom(secureOnly))
    }

    @Test
    fun buildSapisidHash_computesValidDigest() {
        val header = buildSapisidHash(
            sapisid = "test_secret",
            origin = "https://music.youtube.com",
            timestampSeconds = 1700000000L,
        )
        assertTrue(header.startsWith("SAPISIDHASH 1700000000_"))
        val digestHex = header.substringAfter("_")
        assertEquals(40, digestHex.length)
        assertTrue(digestHex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun normalizeDataSyncId_extractsDelegatedOrPlain() {
        assertEquals("delegated123", normalizeDataSyncId("owner||delegated123"))
        assertEquals("ownerOnly", normalizeDataSyncId("ownerOnly||"))
        assertEquals("plainId", normalizeDataSyncId("plainId"))
        assertEquals("user123", normalizeDataSyncId("delegated123||user123"))
        assertEquals("delegated123", delegatedSessionId("delegated123||user123"))
        assertNull(delegatedSessionId("ownerOnly||"))
        assertNull(delegatedSessionId("plainId"))
        assertNull(normalizeDataSyncId("   "))
        assertNull(normalizeDataSyncId(null))
    }

    @Test
    fun parseAccountInfo_andChannels_extractCorrectMetadata() {
        val response = JSONObject(
            """
            {
              "actions": [{
                "openPopupAction": {
                  "popup": {
                    "multiPageMenuRenderer": {
                      "header": {
                        "activeAccountHeaderRenderer": {
                          "accountName": { "runs": [{ "text": "Kushagra" }] },
                          "email": { "simpleText": "kushagra@example.com" },
                          "channelHandle": { "runs": [{ "text": "@kushagra" }] },
                          "accountPhoto": {
                            "thumbnails": [
                              { "url": "https://example.com/a64.jpg", "width": 64 },
                              { "url": "https://example.com/a256.jpg", "width": 256 }
                            ]
                          }
                        }
                      }
                    }
                  }
                }
              }]
            }
            """.trimIndent(),
        )
        val info = YouTubeAccountParsers.parseAccountInfo(response)
        assertNotNull(info)
        assertEquals("Kushagra", info?.name)
        assertEquals("kushagra@example.com", info?.email)
        assertEquals("@kushagra", info?.handle)
        assertEquals("https://example.com/a256.jpg", info?.avatarUrl)
    }

    @Test
    fun parseHistoryTracks_extractsExplicitFlagAndMpedFallback() {
        val root = JSONObject(
            """
            {
              "contents": {
                "singleColumnBrowseResultsRenderer": {
                  "tabs": [{
                    "tabRenderer": {
                      "content": {
                        "sectionListRenderer": {
                          "contents": [{
                            "musicShelfRenderer": {
                              "contents": [
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "playlistItemData": { "videoId": "vidExplicit01" },
                                    "badges": [{
                                      "musicInlineBadgeRenderer": {
                                        "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" }
                                      }
                                    }],
                                    "flexColumns": [
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": { "runs": [{ "text": "Explicit Track" }] }
                                        }
                                      },
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": { "runs": [{ "text": "Artist Name" }, { "text": " • " }, { "text": "3:45" }] }
                                        }
                                      }
                                    ]
                                  }
                                },
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [{ "text": "Listen Again Song" }] },
                                    "subtitle": { "runs": [{ "text": "Song • Indie Artist" }] },
                                    "navigationEndpoint": {
                                      "browseEndpoint": { "browseId": "MPEDaudioOnly99" }
                                    }
                                  }
                                }
                              ]
                            }
                          }]
                        }
                      }
                    }
                  }]
                }
              }
            }
            """.trimIndent(),
        )
        val (songs, _) = YouTubeAccountParsers.parseHistoryTracks(root)
        assertEquals(2, songs.size)
        assertEquals("vidExplicit01", songs[0].videoId)
        assertEquals("Explicit Track", songs[0].title)
        assertEquals("Artist Name", songs[0].artist)
        assertEquals(true, songs[0].isExplicit)

        assertEquals("audioOnly99", songs[1].videoId)
        assertEquals("Indie Artist", songs[1].artist)
        assertEquals(false, songs[1].isExplicit)
    }

    @Test
    fun parseLibraryItems_skipsSyntheticCreateTileAndReadsExplicit() {
        val root = JSONObject(
            """
            {
              "items": [
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "New playlist" }] },
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "FEmusic_create_playlist" }
                    }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Late Night Mix" }] },
                    "subtitle": { "runs": [{ "text": "Album • The Weeknd" }] },
                    "subtitleBadges": [{
                      "musicInlineBadgeRenderer": {
                        "icon": { "iconType": "MUSIC_EXPLICIT_BADGE" }
                      }
                    }],
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "MPREb_12345" }
                    },
                    "thumbnailOverlay": {
                      "musicItemThumbnailOverlayRenderer": {
                        "content": {
                          "musicPlayButtonRenderer": {
                            "playNavigationEndpoint": {
                              "watchPlaylistEndpoint": { "playlistId": "OLAK5uy_abc" }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        val page = YouTubeAccountParsers.parseLibraryItems(root, BrowseType.ALBUM)
        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals("MPREb_12345", item.browseId)
        assertEquals("Late Night Mix", item.title)
        assertTrue(item.isExplicit)
        assertEquals("OLAK5uy_abc", item.playlistId)
    }

    @Test
    fun looksSignedInCookies_requiresSessionCookieAlongsideApiSid() {
        assertTrue(looksSignedInCookies("SAPISID=abc; LOGIN_INFO=def"))
        assertTrue(looksSignedInCookies("__Secure-3PAPISID=abc; __Secure-1PSID=xyz"))
        assertFalse(looksSignedInCookies("SAPISID=abc; PREF=f1"))
        assertFalse(looksSignedInCookies("LOGIN_INFO=def; SID=xyz"))
        assertFalse(looksSignedInCookies(null))
    }

    @Test
    fun buildFullSapisidAuthorization_includesAllPresentHashSchemes() {
        val header = buildFullSapisidAuthorization(
            cookieHeader = "SAPISID=s0; __Secure-1PAPISID=s1; __Secure-3PAPISID=s3",
            origin = "https://music.youtube.com",
            timestampSeconds = 1700000000L,
        )
        assertNotNull(header)
        val pairs = Regex("(SAPISID(?:1P|3P)?HASH) (1700000000_[0-9a-f]{40})")
            .findAll(header!!)
            .toList()
        assertEquals(3, pairs.size)
        assertEquals("SAPISIDHASH", pairs[0].groupValues[1])
        assertEquals("SAPISID1PHASH", pairs[1].groupValues[1])
        assertEquals("SAPISID3PHASH", pairs[2].groupValues[1])
        assertFalse(pairs[0].groupValues[2] == pairs[1].groupValues[2])
    }

    @Test
    fun buildFullSapisidAuthorization_bindsUserSessionIdIntoHash() {
        val cookies = "SAPISID=s0; __Secure-3PAPISID=s3"
        val withoutUser = buildFullSapisidAuthorization(
            cookieHeader = cookies,
            origin = "https://music.youtube.com",
            timestampSeconds = 1700000000L,
        )
        val withUser = buildFullSapisidAuthorization(
            cookieHeader = cookies,
            origin = "https://music.youtube.com",
            timestampSeconds = 1700000000L,
            userSessionId = "user-session-123",
        )

        assertNotNull(withoutUser)
        assertNotNull(withUser)
        assertFalse(withoutUser == withUser)
        assertTrue(withUser!!.contains("_u"))
    }

    @Test
    fun mergeSetCookies_updatesAndDeletesRotatedCookies() {
        val initial = "SAPISID=old; __Secure-1PSIDTS=t1; TMP=remove_me"
        val updated = mergeSetCookies(
            initial,
            listOf(
                "__Secure-1PSIDTS=t2; Path=/; Secure; HttpOnly",
                "SIDCC=fresh_cc; Path=/",
                "TMP=; Max-Age=0; Path=/",
            ),
        )
        assertNotNull(updated)
        val parsed = parseCookieHeader(updated!!)
        assertEquals("old", parsed["SAPISID"])
        assertEquals("t2", parsed["__Secure-1PSIDTS"])
        assertEquals("fresh_cc", parsed["SIDCC"])
        assertFalse(parsed.containsKey("TMP"))
        assertNull(mergeSetCookies(initial, listOf("SAPISID=old; Path=/")))
    }

    @Test
    fun parseLibraryItems_extractsLockupPlaylistAndNormalizesArtistMplaPrefix() {
        val playlistRoot = JSONObject(
            """
            {
              "contents": [{
                "lockupViewModel": {
                  "contentId": "PLuserDark123",
                  "contentType": "LOCKUP_CONTENT_TYPE_PLAYLIST",
                  "metadata": {
                    "lockupMetadataViewModel": {
                      "title": { "content": "Dark" },
                      "metadata": {
                        "contentMetadataViewModel": {
                          "metadataRows": [{
                            "metadataParts": [{ "text": { "content": "Dar" } }]
                          }]
                        }
                      }
                    }
                  },
                  "contentImage": {
                    "thumbnailViewModel": {
                      "image": {
                        "sources": [{ "url": "https://example.com/dark.jpg" }]
                      }
                    }
                  }
                }
              }]
            }
            """.trimIndent(),
        )
        val playlists = YouTubeAccountParsers.parseLibraryItems(playlistRoot, BrowseType.PLAYLIST)
        assertEquals(1, playlists.items.size)
        assertEquals("VLPLuserDark123", playlists.items.first().browseId)
        assertEquals("Dark", playlists.items.first().title)
        assertEquals("Dar", playlists.items.first().subtitle)
        assertEquals("PLuserDark123", playlists.items.first().playlistId)

        val artistRoot = JSONObject(
            """
            {
              "items": [{
                "musicResponsiveListItemRenderer": {
                  "navigationEndpoint": {
                    "browseEndpoint": { "browseId": "MPLAUCEminemChannel123" }
                  },
                  "flexColumns": [
                    {
                      "musicResponsiveListItemFlexColumnRenderer": {
                        "text": { "runs": [{ "text": "Eminem" }] }
                      }
                    },
                    {
                      "musicResponsiveListItemFlexColumnRenderer": {
                        "text": { "runs": [{ "text": "Artist" }] }
                      }
                    }
                  ]
                }
              }]
            }
            """.trimIndent(),
        )
        val artists = YouTubeAccountParsers.parseLibraryItems(artistRoot, BrowseType.ARTIST)
        assertEquals(1, artists.items.size)
        assertEquals("UCEminemChannel123", artists.items.first().browseId)
        assertEquals("Eminem", artists.items.first().title)
    }

    @Test
    fun parseLibraryItems_forPlaylistsFiltersSystemAutoPlaylistsAndNonPlaylistTypes() {
        val ytmLibraryPlaylistsRoot = JSONObject(
            """
            {
              "items": [
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "New playlist" }] },
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "FEmusic_create_playlist" }
                    }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Liked music" }] },
                    "subtitle": { "runs": [{ "text": "Auto playlist" }] },
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "VLLM" }
                    }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Episodes for later" }] },
                    "subtitle": { "runs": [{ "text": "Auto playlist" }] },
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "VLSE" }
                    }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "New Episodes" }] },
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "VLNE" }
                    }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Tech Podcast" }] },
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "MPSPpodcast99" }
                    }
                  }
                },
                {
                  "musicTwoRowItemRenderer": {
                    "title": { "runs": [{ "text": "Dark" }] },
                    "subtitle": { "runs": [{ "text": "Playlist • Dar • 2 songs" }] },
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "VLPLdarkYtm01" }
                    }
                  }
                },
                {
                  "musicResponsiveListItemRenderer": {
                    "navigationEndpoint": {
                      "browseEndpoint": { "browseId": "VLRDCLAK5uy_mix01" }
                    },
                    "flexColumns": [
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": { "runs": [{ "text": "Late Night Drives" }] }
                        }
                      },
                      {
                        "musicResponsiveListItemFlexColumnRenderer": {
                          "text": { "runs": [{ "text": "Playlist • YouTube Music" }] }
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val result = YouTubeAccountParsers.parseLibraryItems(ytmLibraryPlaylistsRoot, BrowseType.PLAYLIST)
        assertEquals(2, result.items.size)
        assertEquals("VLPLdarkYtm01", result.items[0].browseId)
        assertEquals("Dark", result.items[0].title)
        assertEquals("VLRDCLAK5uy_mix01", result.items[1].browseId)
        assertEquals("Late Night Drives", result.items[1].title)
    }

    @Test
    fun sessionsSerialization_preservesMultipleAccountsChannelsAndVisitorData() {
        val accA = GoogleAccountSession(
            accountId = "acc_a",
            cookie = "SAPISID=aaa; SID=sid_a",
            name = "Personal User",
            email = "personal@example.com",
            profiles = listOf(
                YouTubeProfile(
                    profileId = "ds_personal",
                    name = "Personal User",
                    handle = "@personal",
                    dataSyncId = "ds_personal",
                    isBrandAccount = false,
                ),
                YouTubeProfile(
                    profileId = "page_brand",
                    name = "Music Brand Channel",
                    handle = "@brand",
                    pageId = "page_brand",
                    dataSyncId = "ds_brand",
                    isBrandAccount = true,
                ),
            ),
            activeProfileId = "page_brand",
            visitorData = "CgvisitorA",
        )
        val accB = GoogleAccountSession(
            accountId = "acc_b",
            cookie = "SAPISID=bbb; SID=sid_b",
            name = "Second Account",
            email = "second@example.com",
            profiles = listOf(
                YouTubeProfile(
                    profileId = "ds_second",
                    name = "Second Account",
                    handle = "@second",
                    dataSyncId = "ds_second",
                    isBrandAccount = false,
                ),
            ),
            activeProfileId = "ds_second",
            visitorData = "CgvisitorB",
        )

        val restored = sessionsFromJson(listOf(accA, accB).sessionsToJson())
        assertEquals(2, restored.size)
        assertEquals("acc_a", restored[0].accountId)
        assertEquals("CgvisitorA", restored[0].visitorData)
        assertEquals(2, restored[0].profiles.size)
        assertEquals("page_brand", restored[0].activeProfileId)
        assertTrue(restored[0].profiles[1].isBrandAccount)
        assertEquals("acc_b", restored[1].accountId)
        assertEquals("second@example.com", restored[1].email)
        assertEquals("CgvisitorB", restored[1].visitorData)

        val statePersonal = YouTubeAccountUiState(
            isSignedIn = true,
            activeAccountId = "acc_a",
            activeProfileId = "ds_personal",
        )
        val stateBrand = statePersonal.copy(activeProfileId = "page_brand")
        val stateOther = statePersonal.copy(activeAccountId = "acc_b", activeProfileId = "ds_second")
        assertFalse(statePersonal.activeSessionKey == stateBrand.activeSessionKey)
        assertFalse(stateBrand.activeSessionKey == stateOther.activeSessionKey)
    }
}
