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
import com.darkxvenom.itune.data.artist.CanvasArtwork
import com.darkxvenom.itune.data.artist.artistsCompatible
import com.darkxvenom.itune.data.artist.normalizeForMatch
import com.darkxvenom.itune.data.artist.titlesCompatible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Community-curated `song + artist -> looping video` index, published as a JSON
 * manifest and cached in memory for [TTL_MS].
 */
object CommunityCanvas {

    private const val TAG = "CommunityCanvas"
    private const val MANIFEST = "https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json"
    private const val TTL_MS = 30L * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private data class Entry(
        val song: String,
        val artist: String,
        val album: String,
        val url: String,
    )

    @Volatile private var entries: List<Entry> = emptyList()
    @Volatile private var fetchedAtMs = 0L

    fun search(title: String, artist: String, album: String?): CanvasArtwork? {
        val index = manifest().ifEmpty { return null }

        val wantAlbum = album?.normalizeForMatch()

        val matches = index.filter { entry ->
            titlesCompatible(entry.song, title) &&
                artistsCompatible(entry.artist, entry.song, artist, title)
        }
        val hit = matches.firstOrNull { entry ->
            val listed = entry.album.normalizeForMatch()
            !wantAlbum.isNullOrBlank() && listed == wantAlbum
        } ?: matches.firstOrNull() ?: return null

        Log.d(TAG, "manifest hit for '${hit.song}' by '${hit.artist}'")
        return CanvasArtwork(
            url = hit.url,
            title = hit.song,
            artist = hit.artist,
            album = hit.album.takeIf { it.isNotBlank() },
        )
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    fun searchAlbum(album: String, artist: String): CanvasArtwork? {
        val index = manifest().ifEmpty { return null }

        val wantAlbum = album.normalizeForMatch()
        val wantArtist = artist.normalizeForMatch()
        if (wantAlbum.isBlank()) return null

        val hit = index.firstOrNull { entry ->
            val listed = entry.album.normalizeForMatch()
            val credited = entry.artist.normalizeForMatch()
            listed == wantAlbum && credited.isNotBlank() &&
                (wantArtist.contains(credited) || credited.contains(wantArtist))
        } ?: return null

        Log.d(TAG, "manifest hit for album '${hit.album}' by '${hit.artist}'")
        return CanvasArtwork(
            url = hit.url,
            title = hit.album,
            artist = hit.artist,
            album = hit.album,
        )
    }

    @Synchronized
    private fun manifest(): List<Entry> {
        val now = System.currentTimeMillis()
        if (entries.isNotEmpty() && now - fetchedAtMs < TTL_MS) return entries

        val body = canvasGet(MANIFEST, mapOf("User-Agent" to CANVAS_UA))
        if (body == null) {
            fetchedAtMs = now
            return entries
        }

        val parsed = runCatching {
            json.parseToJsonElement(body).jsonObject["items"]?.jsonArray
                ?.mapNotNull { item ->
                    val obj = item.jsonObject
                    Entry(
                        song = obj["song"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        album = obj["album"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    )
                }
        }.getOrNull().orEmpty()

        if (parsed.isNotEmpty()) {
            Log.d(TAG, "manifest holds ${parsed.size} entries")
            entries = parsed
        }
        fetchedAtMs = now
        return entries
    }
}
