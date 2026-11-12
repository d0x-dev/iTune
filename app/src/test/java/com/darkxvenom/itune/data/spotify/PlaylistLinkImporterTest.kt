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
