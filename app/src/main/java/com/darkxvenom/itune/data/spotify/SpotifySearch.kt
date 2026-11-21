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

import com.darkxvenom.itune.data.model.Song
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class SpotifyEntity(val title: String, val subtitle: String, val image: String?, val browseId: String)

data class SpotifySearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<SpotifyEntity> = emptyList(),
    val albums: List<SpotifyEntity> = emptyList(),
    val playlists: List<SpotifyEntity> = emptyList(),
    val shows: List<SpotifyEntity> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty() && shows.isEmpty()
}

object SpotifySearch {
    suspend fun search(query: String): SpotifySearchResults {
        val q = URLEncoder.encode(query, "UTF-8")
        val root = SpotifyLibrary.restGet("/search?q=$q&type=track,artist,album,playlist,show&limit=10&market=from_token")
        fun items(key: String): List<JsonObject> =
            ((root[key] as? JsonObject)?.get("items") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun str(o: JsonObject, k: String) = runCatching { o[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
        fun img(o: JsonObject) = (o["images"] as? JsonArray)?.firstOrNull()?.jsonObject?.let { str(it, "url") }
        fun entity(o: JsonObject, type: String, sub: String): SpotifyEntity? {
            val id = str(o, "id") ?: return null
            return SpotifyEntity(str(o, "name").orEmpty(), sub, img(o), "spotify:$type:$id")
        }
        return SpotifySearchResults(
            songs = items("tracks").mapNotNull { SpotifyDetail.parseTrack(it) },
            artists = items("artists").mapNotNull { entity(it, "artist", "Artist") },
            albums = items("albums").mapNotNull {
                val artist = (it["artists"] as? JsonArray)?.firstOrNull()?.jsonObject?.let { a -> str(a, "name") }.orEmpty()
                entity(it, "album", "Album • $artist")
            },
            playlists = items("playlists").mapNotNull {
                entity(it, "playlist", "Playlist • ${(it["owner"] as? JsonObject)?.let { o -> str(o, "display_name") }.orEmpty()}")
            },
            shows = items("shows").mapNotNull { entity(it, "show", "Podcast • ${str(it, "publisher").orEmpty()}") },
        )
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
}
