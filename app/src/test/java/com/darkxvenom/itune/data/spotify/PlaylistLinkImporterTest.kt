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

package com.darkxvenom.itune.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistLinkImporterTest {
    private val playlistId = "37i9dQZF1DXcBWIGoYBM5M"

    @Test
    fun extractsPlaylistIdFromStandardLink() {
        assertEquals(
            playlistId,
            PlaylistLinkImporter.extractPlaylistId("https://open.spotify.com/playlist/$playlistId?si=abc123"),
        )
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    @Test
    fun extractsPlaylistIdFromSpotifyUri() {
        assertEquals(
            playlistId,
            PlaylistLinkImporter.extractPlaylistId("spotify:playlist:$playlistId"),
        )
    }

    @Test
    fun rejectsNonPlaylistAndUntrustedHosts() {
        assertNull(PlaylistLinkImporter.extractPlaylistId("https://example.com/playlist/$playlistId"))
        assertNull(PlaylistLinkImporter.extractPlaylistId("https://open.spotify.com/album/$playlistId"))
        assertNull(PlaylistLinkImporter.extractPlaylistId("not a link"))
    }
}
