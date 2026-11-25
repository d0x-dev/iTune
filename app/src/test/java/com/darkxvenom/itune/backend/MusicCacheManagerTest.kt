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

package com.darkxvenom.itune.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class MusicCacheManagerTest {

    @Test
    fun completedPlaybackCachesOnlyWhenTrackPlayedFromBeginningToEnd() {
        assertEquals(
            true,
            shouldCacheAfterFullPlayback(
                activeTrackId = "finished",
                eligibleTrackId = "finished",
                cachingEnabled = true,
                alreadyCached = false,
                positionMs = 239_900L,
                durationMs = 240_000L,
            ),
        )
        assertEquals(
            false,
            shouldCacheAfterFullPlayback(
                activeTrackId = "skipped",
                eligibleTrackId = "skipped",
                cachingEnabled = true,
                alreadyCached = false,
                positionMs = 90_000L,
                durationMs = 240_000L,
            ),
        )
        assertEquals(
            false,
            shouldCacheAfterFullPlayback(
                activeTrackId = "resumed",
                eligibleTrackId = null,
                cachingEnabled = true,
                alreadyCached = false,
                positionMs = 239_900L,
                durationMs = 240_000L,
            ),
        )
        assertEquals(
            false,
            shouldCacheAfterFullPlayback(
                activeTrackId = "finished",
                eligibleTrackId = "finished",
                cachingEnabled = false,
                alreadyCached = false,
                positionMs = 240_000L,
                durationMs = 240_000L,
            ),
        )
    }

    @Test
    fun targetBitrateKbps_mapsQualityToBitrate() {
        assertEquals(64, MusicCacheManager.targetBitrateKbps(MusicCacheManager.QUALITY_LOW))
        assertEquals(128, MusicCacheManager.targetBitrateKbps(MusicCacheManager.QUALITY_MEDIUM))
        assertNull(MusicCacheManager.targetBitrateKbps(MusicCacheManager.QUALITY_HIGH))
    }

    @Test
    fun formatStorageSize_formatsCorrectUnits() {
        assertEquals("0 B", MusicCacheManager.formatStorageSize(0L))
        assertEquals("500 B", MusicCacheManager.formatStorageSize(500L))
        assertEquals("1.0 KB", MusicCacheManager.formatStorageSize(1024L))
        assertEquals("1.5 MB", MusicCacheManager.formatStorageSize((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", MusicCacheManager.formatStorageSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun cachedMusicTrack_toMusicTrack_preservesAllMetadataAndHighQualityArtwork() {
        val cached = CachedMusicTrack(
            id = "test_song_123",
            title = "Midnight City",
            artists = "M83",
            artistId = "UC123",
            album = "Hurry Up, We're Dreaming",
            albumId = "MPREb_album",
            thumbnailUrl = "https://lh3.googleusercontent.com/test=w120-h120",
            localThumbnailPath = null,
            durationSec = 244L,
            audioFilePath = "/dummy/cache/test_song_123.m4a",
            fileSizeBytes = 7_500_000L,
            liked = true
        )

        val track = cached.toMusicTrack()
        assertEquals("test_song_123", track.id)
        assertEquals("Midnight City", track.title)
        assertEquals("M83", track.artists)
        assertEquals("UC123", track.artistId)
        assertEquals("Hurry Up, We're Dreaming", track.album)
        assertEquals("MPREb_album", track.albumId)
        assertEquals(244L, track.durationSec)
        assertEquals(true, track.liked)
        assertNotNull(track.thumbnailUrl)
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    @Test
    fun cachedMusicTrack_resolvedArtworkUri_prefersExistingLocalFile() {
        val tempImg = File.createTempFile("thumb_test", ".jpg").apply {
            writeText("dummy-image-bytes")
            deleteOnExit()
        }

        val cached = CachedMusicTrack(
            id = "local_thumb_track",
            title = "Offline Song",
            artists = "Artist",
            thumbnailUrl = "https://lh3.googleusercontent.com/remote",
            localThumbnailPath = tempImg.absolutePath,
            audioFilePath = "/dummy/path.m4a"
        )

        val resolved = cached.resolvedArtworkUri()
        assertNotNull(resolved)
        assertEquals(true, resolved!!.startsWith("file:"))

        tempImg.delete()
        val fallback = cached.resolvedArtworkUri()
        assertEquals(true, fallback!!.startsWith("https:"))
    }
}
