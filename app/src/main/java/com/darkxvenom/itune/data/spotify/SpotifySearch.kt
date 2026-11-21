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
import com.darkxvenom.itune.backend.MusicArtist
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.UnifiedSearchResults
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

data class SpotifyEntity(val title: String, val subtitle: String, val image: String?, val browseId: String, val isExplicit: Boolean = false)

data class SpotifySearchResults(
    val songs: List<Song> = emptyList(),
    val artists: List<SpotifyEntity> = emptyList(),
    val albums: List<SpotifyEntity> = emptyList(),
    val playlists: List<SpotifyEntity> = emptyList(),
    val shows: List<SpotifyEntity> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty() && shows.isEmpty()

    fun toUnified(): UnifiedSearchResults {
        val unifiedSongs = songs.map { s ->
            val durSec = s.durationText?.let { d ->
                val p = d.split(":")
                if (p.size == 2) (p[0].toLongOrNull() ?: 0) * 60 + (p[1].toLongOrNull() ?: 0)
                else 0L
            } ?: 0L
            MusicTrack(
                id = s.videoId,
                title = s.title,
                artists = s.artist,
                album = s.albumName,
                albumId = s.albumId,
                thumbnailUrl = s.thumbnailUrl,
                durationSec = durSec,
                isExplicit = s.isExplicit ?: false,
            )
        }
        val unifiedArtists = artists.map { a ->
            MusicArtist(
                id = a.browseId,
                name = a.title,
                thumbnailUrl = a.image,
                subscribers = a.subtitle,
            )
        }
        val unifiedAlbums = albums.map { alb ->
            MusicPlaylistRef(
                id = alb.browseId,
                title = alb.title,
                author = alb.subtitle,
                thumbnailUrl = alb.image,
                isAlbum = true,
                isExplicit = alb.isExplicit,
            )
        }
        val unifiedPlaylists = (playlists + shows).map { pl ->
            MusicPlaylistRef(
                id = pl.browseId,
                title = pl.title,
                author = pl.subtitle,
                thumbnailUrl = pl.image,
                isAlbum = false,
                isExplicit = pl.isExplicit,
            )
        }
        return UnifiedSearchResults(
            songs = unifiedSongs,
            artists = unifiedArtists,
            albums = unifiedAlbums,
            playlists = unifiedPlaylists,
        )
    }
}

object SpotifySearch {
    private const val TAG = "SpotifySearch"

    // Primary: searchDesktop (full catalogue search returning tracks, artists, albums, playlists, podcasts)
    private val SEARCH_DESKTOP_HASHES = listOf(
        "eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03", // Web-player xpui-routes-search
        "4801118d4a100f756e833d33984436a3899cff359c532f8fd3aaf174b60b3b49", // Upstream candidate
    )

    // Fallback: searchTracks (focused track query from Spotify web player)
    private val SEARCH_TRACKS_HASHES = listOf(
        "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751", // Web-player chunk candidate
        "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428", // Pathfinder v1 candidate
    )

    suspend fun search(query: String): SpotifySearchResults {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SpotifySearchResults()

        // 1. Primary Pathfinder searchDesktop query
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
                if (!parsed.isEmpty) {
                    Log.d(TAG, "searchDesktop succeeded: ${parsed.songs.size} songs, ${parsed.artists.size} artists, ${parsed.albums.size} albums")
                    return parsed
                }
            }
        }

        // 2. Fallback Pathfinder searchTracks query
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
                if (!parsed.isEmpty) {
                    Log.d(TAG, "searchTracks succeeded: ${parsed.songs.size} songs")
                    return parsed
                }
            }
        }

        // 3. Fallback to Web API REST search (if Pathfinder unavailable)
        return runCatching { searchViaRest(trimmed) }.getOrElse {
            Log.w(TAG, "All Spotify search strategies exhausted: ${it.message}")
            SpotifySearchResults()
        }
    }

    suspend fun searchUnified(query: String): UnifiedSearchResults =
        search(query).toUnified()

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

        // Tracks / Songs
        val tracksList = (searchData["tracksV2"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["tracks"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["tracks"] as? JsonArray)

        val songs = tracksList.orEmpty().mapNotNull { elem ->
            val itemWrapper = (elem as? JsonObject)?.get("item") as? JsonObject ?: elem as? JsonObject
            val data = (itemWrapper?.get("data") as? JsonObject) ?: itemWrapper ?: return@mapNotNull null
            parseTrackData(data, itemWrapper)
        }

        // Artists
        val artistsList = (searchData["artists"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["artists"] as? JsonArray)

        val artists = artistsList.orEmpty().mapNotNull { elem ->
            val wrapper = elem as? JsonObject
            val data = (wrapper?.get("data") as? JsonObject) ?: wrapper ?: return@mapNotNull null
            val uri = str(data, "uri") ?: str(wrapper, "uri") ?: str(data, "id")?.let { "spotify:artist:$it" } ?: return@mapNotNull null
            val name = (data["profile"] as? JsonObject)?.let { str(it, "name") } ?: str(data, "name") ?: return@mapNotNull null
            val avatar = (data["visuals"] as? JsonObject)?.get("avatarImage")?.let { extractImage(it as? JsonObject) }
                ?: extractImage(data)
            SpotifyEntity(title = name, subtitle = "Artist", image = avatar, browseId = uri)
        }

        // Albums
        val albumsList = (searchData["albumsV2"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["albums"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["albums"] as? JsonArray)

        val albums = albumsList.orEmpty().mapNotNull { elem ->
            val wrapper = elem as? JsonObject
            val data = (wrapper?.get("data") as? JsonObject) ?: wrapper ?: return@mapNotNull null
            val uri = str(data, "uri") ?: str(wrapper, "uri") ?: str(data, "id")?.let { "spotify:album:$it" } ?: return@mapNotNull null
            val name = str(data, "name") ?: return@mapNotNull null
            val artistName = extractArtists(data)
            val cover = (data["coverArt"] as? JsonObject)?.let { extractImage(it) } ?: extractImage(data)
            val year = (data["date"] as? JsonObject)?.let { str(it, "year") } ?: str(data, "release_date")?.take(4)
            val sub = listOfNotNull(
                "Album",
                artistName.ifBlank { null },
                year?.let { "($it)" },
            ).joinToString(" • ")
            val isExp = (data["contentRating"] as? JsonObject)?.let { str(it, "label") }?.equals("EXPLICIT", ignoreCase = true) == true
                || data["explicit"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
            SpotifyEntity(title = name, subtitle = sub, image = cover, browseId = uri, isExplicit = isExp)
        }

        // Playlists
        val playlistsList = (searchData["playlists"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["playlists"] as? JsonArray)

        val playlists = playlistsList.orEmpty().mapNotNull { elem ->
            val wrapper = elem as? JsonObject
            val data = (wrapper?.get("data") as? JsonObject) ?: wrapper ?: return@mapNotNull null
            val uri = str(data, "uri") ?: str(wrapper, "uri") ?: str(data, "id")?.let { "spotify:playlist:$it" } ?: return@mapNotNull null
            val name = str(data, "name") ?: return@mapNotNull null
            val owner = (data["ownerV2"] as? JsonObject)?.get("data")?.let { str(it as? JsonObject, "name") }
                ?: (data["owner"] as? JsonObject)?.let { str(it, "display_name") ?: str(it, "name") }
            val img = extractImage(data)
            val isExp = (data["contentRating"] as? JsonObject)?.let { str(it, "label") }?.equals("EXPLICIT", ignoreCase = true) == true
                || data["explicit"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
            SpotifyEntity(title = name, subtitle = if (!owner.isNullOrBlank()) "Playlist • $owner" else "Playlist", image = img, browseId = uri, isExplicit = isExp)
        }

        // Podcasts / Shows
        val podcastsList = (searchData["podcasts"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["shows"] as? JsonObject)?.get("items") as? JsonArray
            ?: (searchData["shows"] as? JsonArray)

        val shows = podcastsList.orEmpty().mapNotNull { elem ->
            val wrapper = elem as? JsonObject
            val data = (wrapper?.get("data") as? JsonObject) ?: wrapper ?: return@mapNotNull null
            val uri = str(data, "uri") ?: str(wrapper, "uri") ?: str(data, "id")?.let { "spotify:show:$it" } ?: return@mapNotNull null
            val name = str(data, "name") ?: return@mapNotNull null
            val publisher = (data["publisher"] as? JsonObject)?.let { str(it, "name") } ?: str(data, "publisher")
            val img = extractImage(data)
            val isExp = (data["contentRating"] as? JsonObject)?.let { str(it, "label") }?.equals("EXPLICIT", ignoreCase = true) == true
                || data["explicit"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
            SpotifyEntity(title = name, subtitle = if (!publisher.isNullOrBlank()) "Podcast • $publisher" else "Podcast", image = img, browseId = uri, isExplicit = isExp)
        }

        return SpotifySearchResults(
            songs = songs,
            artists = artists,
            albums = albums,
            playlists = playlists,
            shows = shows,
        )
    }

    private fun parseTrackData(data: JsonObject, itemWrapper: JsonObject?): Song? {
        val uri = str(data, "uri")
            ?: str(data, "_uri")
            ?: itemWrapper?.let { str(it, "_uri") ?: str(it, "uri") }
            ?: str(data, "id")?.let { "spotify:track:$it" }
            ?: return null
        val id = uri.substringAfterLast(":")
        val title = str(data, "name") ?: return null
        val artist = extractArtists(data)
        val albumObj = data["albumOfTrack"] as? JsonObject ?: data["album"] as? JsonObject
        val albumName = albumObj?.let { str(it, "name") }
        val albumId = albumObj?.let { str(it, "uri") ?: str(it, "id")?.let { aId -> "spotify:album:$aId" } }
        val albumCover = albumObj?.let { extractImage(it) } ?: extractImage(data)
        val durationMs = (data["trackDuration"] as? JsonObject)?.get("totalMilliseconds")?.jsonPrimitive?.intOrNull
            ?: (data["duration"] as? JsonObject)?.get("totalMilliseconds")?.jsonPrimitive?.intOrNull
            ?: data["duration_ms"]?.jsonPrimitive?.intOrNull
            ?: 0
        val isExp = (data["contentRating"] as? JsonObject)?.let { str(it, "label") }?.equals("EXPLICIT", ignoreCase = true) == true
            || data["explicit"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
        return Song(
            videoId = "sp:$id",
            title = title,
            artist = artist,
            thumbnailUrl = albumCover,
            durationText = if (durationMs > 0) "%d:%02d".format(durationMs / 60000, durationMs / 1000 % 60) else null,
            albumName = albumName,
            albumId = albumId,
            isExplicit = isExp,
        )
    }

    private fun extractArtists(data: JsonObject): String {
        val artObj = data["artists"]
        if (artObj is JsonObject) {
            val items = artObj["items"] as? JsonArray
            if (items != null) {
                val names = items.mapNotNull {
                    val o = it as? JsonObject ?: return@mapNotNull null
                    (o["profile"] as? JsonObject)?.let { p -> str(p, "name") } ?: str(o, "name")
                }
                if (names.isNotEmpty()) return names.joinToString(", ")
            }
        } else if (artObj is JsonArray) {
            val names = artObj.mapNotNull { (it as? JsonObject)?.let { o -> str(o, "name") } }
            if (names.isNotEmpty()) return names.joinToString(", ")
        }
        return ""
    }

    private fun extractImage(obj: JsonObject?): String? {
        if (obj == null) return null
        // 1. Check coverArt or avatarImage or images
        val coverArt = obj["coverArt"] as? JsonObject
            ?: obj["avatarImage"] as? JsonObject
            ?: (obj["visuals"] as? JsonObject)?.get("avatarImage") as? JsonObject
            ?: obj
        val sources = coverArt["sources"] as? JsonArray
            ?: (obj["images"] as? JsonObject)?.get("items")?.let { (it as? JsonArray)?.firstOrNull()?.let { f -> (f as? JsonObject)?.get("sources") as? JsonArray } }
            ?: (obj["images"] as? JsonArray)
            ?: (coverArt["images"] as? JsonArray)

        if (sources != null) {
            val first = sources.firstOrNull() as? JsonObject
            if (first != null) {
                val url = str(first, "url")
                if (!url.isNullOrBlank()) return url
            }
        }
        return null
    }

    private fun str(o: JsonObject?, k: String) = runCatching { o?.get(k)?.jsonPrimitive?.contentOrNull }.getOrNull()
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
