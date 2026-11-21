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

import com.darkxvenom.itune.data.artist.CanvasArtwork
import com.darkxvenom.itune.data.canvas.SpotifyCanvas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/** One looping Canvas video, together with the song it belongs to. */
data class HeroSlide(val canvas: CanvasArtwork, val title: String, val artist: String)

/** Everything a Spotify "Made for you" style big card needs besides the cover art. */
data class HeroCardData(
    val tagline: String? = null,
    val slides: List<HeroSlide> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Loads a playlist's tagline and the Canvas videos of its first songs for the big home cards.
 * Slides arrive one by one: the card shows the cover immediately, plays song #1's Canvas as soon
 * as it is found, and "next" steps through the Canvases of songs #2 and #3.
 */
object SpotifyHeroLoader {
    const val MAX_SLIDES = 3
    private const val SCAN_TRACKS = 12

    private val cache = ConcurrentHashMap<String, HeroCardData>()
    private val gate = Semaphore(2)

    fun cached(playlistId: String): HeroCardData? = cache[playlistId]

    suspend fun load(playlistId: String, onUpdate: (HeroCardData) -> Unit) {
        cache[playlistId]?.takeIf { it.loaded }?.let { onUpdate(it); return }

        val head = try {
            SpotifyLibrary.playlistHead(playlistId, SCAN_TRACKS)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        val tracks = head?.tracks.orEmpty()
        var data = HeroCardData(tagline = head?.description ?: featuring(tracks))
        cache[playlistId] = data
        onUpdate(data)

        for (track in tracks) {
            if (data.slides.size >= MAX_SLIDES) break
            val canvas = try {
                gate.withPermit {
                    SpotifyCanvas.forTrackId(track.id, track.title, track.artist, track.album)
                        ?: SpotifyCanvas.search(track.title, track.artist, track.album)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } ?: continue
            if (data.slides.any { it.canvas.url == canvas.url }) continue
            data = data.copy(slides = data.slides + HeroSlide(canvas, track.title, track.artist))
            cache[playlistId] = data
            onUpdate(data)
        }
        // Only remember a finished scan if it found something, so an empty/failed scan can retry.
        data = data.copy(loaded = data.slides.isNotEmpty())
        if (data.loaded) cache[playlistId] = data else cache.remove(playlistId)
        onUpdate(data)
    }

    /** Fallback tagline when the playlist has no description of its own. */
    private fun featuring(tracks: List<SpotifyTrack>): String? {
        val names = tracks.flatMap { it.artist.split(",") }.map { it.trim() }.filter { it.isNotBlank() }
            .distinct().take(3)
        return when (names.size) {
            0 -> null
            1 -> "Featuring ${names[0]}"
            else -> "Featuring ${names.dropLast(1).joinToString(", ")} and ${names.last()}"
        }
    }
}
