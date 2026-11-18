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

package com.darkxvenom.itune.data.canvas

import android.util.Log
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.artist.CanvasArtwork
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the looping video that belongs behind a track's or a release's cover
 * art — Spotify's Canvas, Apple's motion artwork, Tidal video covers, and the
 * community index.
 *
 * Four sources, asked in turn until one answers. Results (including misses) are
 * LRU-cached so revisiting a track never pays for repeated lookups.
 */
object CanvasRepository {

    private const val TAG = "CanvasRepository"
    private const val CACHE_SIZE = 64
    private const val MISS_TTL_MS = 15_000L

    private class Entry(
        val artwork: CanvasArtwork?,
        val withAlbum: Boolean,
        val cachedAtMs: Long = System.currentTimeMillis(),
    )

    private val cache = object : LinkedHashMap<String, Entry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > CACHE_SIZE
    }

    private val locks = ConcurrentHashMap<String, Mutex>()

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    suspend fun canvasFor(track: MusicTrack): CanvasArtwork? {
        if (track.id.isBlank()) return null

        val title = track.title.cleaned()
        val artist = track.artists.cleaned()
        if (title.isBlank() || artist.isBlank()) return null

        val album = track.album?.takeIf { it.isNotBlank() }
        val spotifyFirst = SettingsStore.state.value.prioritizeSpotifyCanvas
        val key = cacheKey("song|${track.id}", spotifyFirst)

        return resolve(key, album != null) {
            Log.d(TAG, "looking up '$title' by '$artist' (album=$album, id=${track.id}, spotifyFirst=$spotifyFirst)")
            val found = if (spotifyFirst) {
                firstHit(
                    { SpotifyCanvas.search(title, artist, album) },
                    { AppleMusicCanvas.search(title, artist, album) },
                    { TidalCanvas.search(title, artist, album) },
                    { CommunityCanvas.search(title, artist, album) },
                ) { it.matches(title, artist, album) }
            } else {
                firstHit(
                    { AppleMusicCanvas.search(title, artist, album) },
                    { TidalCanvas.search(title, artist, album) },
                    { CommunityCanvas.search(title, artist, album) },
                    { SpotifyCanvas.search(title, artist, album) },
                ) { it.matches(title, artist, album) }
            }
            if (found != null) {
                Log.d(TAG, "resolved '${found.title ?: title}' via ${found.source} (${found.url.take(64)})")
            } else {
                Log.d(TAG, "no canvas found for '$title' by '$artist'")
            }
            found
        }
    }

    fun cached(track: MusicTrack): CanvasArtwork? {
        val key = cacheKey(
            base = "song|${track.id}",
            spotifyFirst = SettingsStore.state.value.prioritizeSpotifyCanvas,
        )
        return synchronized(cache) { cache[key]?.artwork }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    suspend fun canvasForAlbum(album: String, artist: String): CanvasArtwork? {
        val name = album.cleaned()
        val credit = artist.cleaned()
        if (name.isBlank() || credit.isBlank()) return null

        val spotifyFirst = SettingsStore.state.value.prioritizeSpotifyCanvas
        return resolve(cacheKey("album|$name|$credit", spotifyFirst), withAlbum = true) {
            if (spotifyFirst) {
                firstHit(
                    { SpotifyCanvas.searchAlbum(name, credit) },
                    { AppleMusicCanvas.searchAlbum(name, credit) },
                    { TidalCanvas.searchAlbum(name, credit) },
                    { CommunityCanvas.searchAlbum(name, credit) },
                ) { it.matches(name, credit, name) }
            } else {
                firstHit(
                    { AppleMusicCanvas.searchAlbum(name, credit) },
                    { TidalCanvas.searchAlbum(name, credit) },
                    { CommunityCanvas.searchAlbum(name, credit) },
                    { SpotifyCanvas.searchAlbum(name, credit) },
                ) { it.matches(name, credit, name) }
            }
        }
    }

    private fun cacheKey(base: String, spotifyFirst: Boolean): String =
        "$base|spotifyFirst=$spotifyFirst"

    private suspend fun resolve(
        key: String,
        withAlbum: Boolean,
        lookUp: suspend () -> CanvasArtwork?,
    ): CanvasArtwork? {
        val keyLock = locks.computeIfAbsent(key) { Mutex() }
        return keyLock.withLock {
            synchronized(cache) {
                cache[key]?.let { if (it.reusable(withAlbum)) return@withLock it.artwork }
            }
            val found = withContext(Dispatchers.IO) { lookUp() }
            synchronized(cache) { cache[key] = Entry(found, withAlbum) }
            found
        }
    }

    private fun Entry.reusable(withAlbum: Boolean): Boolean {
        if (artwork != null) return true
        val missFresh = System.currentTimeMillis() - cachedAtMs < MISS_TTL_MS
        if (!missFresh) return false
        return this.withAlbum || !withAlbum
    }

    private suspend fun firstHit(
        vararg sources: suspend () -> CanvasArtwork?,
        accept: (CanvasArtwork) -> Boolean,
    ): CanvasArtwork? {
        for (source in sources) {
            val found = runCatching { source() }
                .onFailure { Log.d(TAG, "source failed: ${it.message}") }
                .getOrNull()
                ?: continue
            if (!accept(found)) {
                Log.d(TAG, "rejected '${found.title}' by '${found.artist}'")
                continue
            }
            return found
        }
        return null
    }

    private fun String.cleaned(): String = replace(NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { this }

    private val NOISE = Regex(
        """\((?:from|official|lyrical|video|audio)[^)]*\)|\[[^]]*]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
}
