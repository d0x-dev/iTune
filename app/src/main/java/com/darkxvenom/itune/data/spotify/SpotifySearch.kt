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

import android.util.Log
import com.darkxvenom.itune.data.model.Song
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
    private const val TAG = "SpotifySearch"

    // Primary: searchDesktop (full catalogue search returning tracks, artists, albums, playlists, podcasts)
    private val SEARCH_DESKTOP_HASHES = listOf(
        "eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03", // Web-player xpui-routes-search
        "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49", // AirBeats candidate
    )

    // Fallback: searchTracks (focused track query from Spotify web player)
    private val SEARCH_TRACKS_HASHES = listOf(
        "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751", // Web-player chunk candidate
        "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428", // Pathfinder v1 candidate
    )

    suspend fun search(query: String): SpotifySearchResults {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SpotifySearchResults()

        val desktopVars = buildJsonObject {
            put("searchTerm", trimmed)
            put("offset", 0)
            put("limit", 20)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includeArtistHasConcertsField", false)
            put("includePreReleases", true)
            put("includeLocalConcertsField", false)
            put("includeAuthors", false)
        }

        for (hash in SEARCH_DESKTOP_HASHES) {
            val root = runCatching {
                SpotifyLibrary.pathfinder("searchDesktop", hash, desktopVars)
            }.onFailure {
                Log.w(TAG, "searchDesktop hash $hash failed: ${it.message}")
            }.getOrNull()

            if (root != null) {
                val parsed = parseSearchDesktop(root)
                if (!parsed.isEmpty) return parsed
            }
        }

        val trackVars = buildJsonObject {
            put("searchTerm", trimmed)
            put("offset", 0)
            put("limit", 20)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includePreReleases", false)
        }

        for (hash in SEARCH_TRACKS_HASHES) {
            val root = runCatching {
                SpotifyLibrary.pathfinder("searchTracks", hash, trackVars)
            }.onFailure {
                Log.w(TAG, "searchTracks hash $hash failed: ${it.message}")
            }.getOrNull()

            if (root != null) {
                val parsed = parseSearchDesktop(root)
                if (!parsed.isEmpty) return parsed
            }
        }

        return runCatching { searchViaRest(trimmed) }.getOrElse {
            Log.w(TAG, "All Spotify search strategies exhausted: ${it.message}")
            SpotifySearchResults()
        }
    }

    private suspend fun searchViaRest(query: String): SpotifySearchResults {
        val q = URLEncoder.encode(query, "UTF-8")
        val root = SpotifyLibrary.restGet("/search?q=$q&type=track,artist,album,playlist,show&limit=10&market=from_token")
        fun items(key: String): List<JsonObject> =
            ((root[key] as? JsonObject)?.get("items") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
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

    private fun parseSearchDesktop(root: JsonObject): SpotifySearchResults {
        val searchData = root["data"]?.jsonObject?.get("searchV2")?.jsonObject
            ?: root["data"]?.jsonObject
            ?: root

        val tracksList = (searchData["tracksV2"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["tracks"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["tracks"] as? JsonArray)

        val songs = tracksList.orEmpty().mapNotNull { elem ->
            val itemWrapper = (elem as? JsonObject)?.get("item") as? JsonObject ?: elem as? JsonObject
            val data = (itemWrapper?.get("data") as? JsonObject) ?: itemWrapper ?: return@mapNotNull null
            parseTrackData(data, itemWrapper)
        }

        return SpotifySearchResults(songs = songs)
    }

    private fun parseTrackData(data: JsonObject, itemWrapper: JsonObject?): Song? {
        val uri = str(data, "uri")
            ?: str(data, "_uri")
            ?: itemWrapper?.let { str(it, "_uri") ?: str(it, "uri") }
            ?: str(data, "id")?.let { "spotify:track:$it" }
            ?: return null
        val id = uri.substringAfterLast(":")
        val title = str(data, "name") ?: return null
        val artist = (data["artists"] as? JsonObject)?.get("items")?.let { arr ->
            (arr as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("profile")?.let { p -> str(p as? JsonObject, "name") } ?: str(it as? JsonObject, "name") }?.joinToString(", ")
        } ?: ""
        val albumObj = data["albumOfTrack"] as? JsonObject ?: data["album"] as? JsonObject
        val albumName = albumObj?.let { str(it, "name") }
        val albumId = albumObj?.let { str(it, "uri") ?: str(it, "id")?.let { aId -> "spotify:album:$aId" } }
        val durationMs = (data["trackDuration"] as? JsonObject)?.get("totalMilliseconds")?.jsonPrimitive?.intOrNull
            ?: (data["duration"] as? JsonObject)?.get("totalMilliseconds")?.jsonPrimitive?.intOrNull
            ?: 0
        return Song(
            videoId = "sp:$id",
            title = title,
            artist = artist,
            thumbnailUrl = null,
            durationText = if (durationMs > 0) "%d:%02d".format(durationMs / 60000, durationMs / 1000 % 60) else null,
            albumName = albumName,
            albumId = albumId,
        )
    }

    private fun str(o: JsonObject?, k: String) = runCatching { o?.get(k)?.jsonPrimitive?.contentOrNull }.getOrNull()
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
