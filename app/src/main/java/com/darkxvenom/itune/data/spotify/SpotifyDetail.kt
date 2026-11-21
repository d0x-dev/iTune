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

import com.darkxvenom.itune.data.model.HomeShelf
import com.darkxvenom.itune.data.model.ShelfItem
import com.darkxvenom.itune.data.model.Song
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Spotify-sourced content for the existing artist / album / playlist detail screen. */
data class SpotifyPage(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val songs: List<Song>,
    val sections: List<HomeShelf> = emptyList(),
    val description: String? = null,
    val followers: String? = null,
)

object SpotifyDetail {
    /** `spotify:artist:ID` → `https://open.spotify.com/artist/ID`; `sp:ID` → track link; else null. */
    fun link(id: String?): String? = when {
        id == null -> null
        id.startsWith("spotify:") -> id.split(":").let { if (it.size >= 3) "https://open.spotify.com/${it[1]}/${it[2]}" else null }
        id.startsWith("sp:") -> "https://open.spotify.com/track/${id.removePrefix("sp:")}"
        id.startsWith("spe:") -> "https://open.spotify.com/episode/${id.removePrefix("spe:")}"
        else -> null
    }

    suspend fun load(browseId: String, fallbackTitle: String, fallbackThumb: String?): SpotifyPage {
        val parts = browseId.removePrefix("spotify:").split(":", limit = 2)
        val id = parts.getOrNull(1).orEmpty()
        return when (parts.getOrNull(0)) {
            "playlist" -> {
                val (name, tracks) = SpotifyLibrary.fetchPlaylistDetails(id)
                SpotifyPage(name.ifBlank { fallbackTitle }, "Playlist • ${tracks.size} songs", fallbackThumb,
                    tracks.map { it.toSong(null, null) })
            }
            "album" -> {
                val a = SpotifyLibrary.restGet("/albums/$id?market=from_token")
                val name = a.str("name") ?: fallbackTitle
                val img = a.img() ?: fallbackThumb
                val artist = a.artistNames()
                val songs = a.arr("tracks")?.let { (it as? JsonObject)?.arr("items") }.orEmpty()
                    .mapNotNull { (it as? JsonObject)?.track(img, name, browseId) }
                SpotifyPage(name, "$artist • ${a.str("release_date").orEmpty().take(4)}", img, songs)
            }
            "artist" -> coroutineScope {
                val info = async { runCatching { SpotifyLibrary.restGet("/artists/$id") }.getOrNull() }
                val top = async { runCatching { SpotifyLibrary.restGet("/artists/$id/top-tracks?market=from_token") }.getOrNull() }
                val albums = async { runCatching { SpotifyLibrary.restGet("/artists/$id/albums?include_groups=album,single&limit=30&market=from_token") }.getOrNull() }
                val related = async { runCatching { SpotifyLibrary.restGet("/artists/$id/related-artists") }.getOrNull() }
                val i = info.await()
                val songs = top.await()?.arr("tracks").orEmpty().mapNotNull { (it as? JsonObject)?.track(null, null, null) }
                val sections = buildList {
                    val items = albums.await()?.arr("items").orEmpty().mapNotNull { (it as? JsonObject) }
                    fun shelf(title: String, list: List<JsonObject>) {
                        if (list.isNotEmpty()) add(HomeShelf(title, list.map { x ->
                            ShelfItem(x.str("name").orEmpty(), (x.str("release_date") ?: "").take(4), x.img(), null, "spotify:album:${x.str("id")}")
                        }))
                    }
                    shelf("Albums", items.filter { it.str("album_type") == "album" })
                    shelf("Singles & EPs", items.filter { it.str("album_type") != "album" })
                    val rel = related.await()?.arr("artists").orEmpty().mapNotNull { it as? JsonObject }
                    if (rel.isNotEmpty()) add(HomeShelf("Fans also like", rel.map { x ->
                        ShelfItem(x.str("name").orEmpty(), "Artist", x.img(), null, "spotify:artist:${x.str("id")}")
                    }))
                }
                val followers = i?.get("followers")?.let { (it as? JsonObject)?.get("total")?.jsonPrimitive?.intOrNull }
                if (songs.isEmpty() && sections.isEmpty()) {
                    // The plain Web API blocks artist endpoints for the web-player token → use the web player's own query
                    artistViaPathfinder(id, fallbackTitle, fallbackThumb)?.let { return@coroutineScope it }
                    artistViaSearch(id, i?.str("name") ?: fallbackTitle, i?.img() ?: fallbackThumb)?.let { return@coroutineScope it }
                    error("Spotify didn't return any data for this artist")
                }
                SpotifyPage(
                    i?.str("name") ?: fallbackTitle, "Artist", i?.img() ?: fallbackThumb, songs, sections,
                    i?.arr("genres")?.joinToString(", ") { it.jsonPrimitive.content }?.ifBlank { null },
                    followers?.let { "%,d followers".format(it) },
                )
            }
            "show" -> {
                val s = SpotifyLibrary.restGet("/shows/$id?market=from_token")
                val name = s.str("name") ?: fallbackTitle
                val img = s.img() ?: fallbackThumb
                val pub = s.str("publisher").orEmpty()
                val eps = (s["episodes"] as? JsonObject)?.arr("items").orEmpty().mapNotNull { (it as? JsonObject) }
                    .map { e ->
                        Song(
                            videoId = "spe:${e.str("id")}", title = e.str("name").orEmpty(), artist = pub,
                            thumbnailUrl = e.img() ?: img, durationText = dur(e["duration_ms"]?.jsonPrimitive?.intOrNull ?: 0),
                            albumName = name,
                        )
                    }
                SpotifyPage(name, "Podcast • $pub", img, eps, description = s.str("description"))
            }
            else -> error("Unsupported Spotify item")
        }
    }


    private val ARTIST_HASHES = listOf(
        "1ac33ddab5d39a3a9c27802774e6d78b9405cc188c6f75aed007df2a32737c72",
        "d66221ea13998b2f81883c5187d174c8646e4041d67f5b1e103bc262d447e3a0",
        "433e28d1e949372d3ca3aa6c47975cff428b5dc37b12f5325d9213accadf770a",
    )

    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.items(k: String): List<JsonObject> = obj(k)?.arr("items").orEmpty().mapNotNull { it as? JsonObject }
    private fun JsonObject.srcUrl(): String? =
        arr("sources")?.mapNotNull { it as? JsonObject }?.maxByOrNull { it["width"]?.jsonPrimitive?.intOrNull ?: 0 }?.str("url")

    private suspend fun artistViaPathfinder(id: String, fallbackTitle: String, fallbackThumb: String?): SpotifyPage? {
        val vars = kotlinx.serialization.json.buildJsonObject {
            put("uri", "spotify:artist:$id"); put("locale", ""); put("includePrerelease", true)
        }
        for (h in ARTIST_HASHES) {
            val a = runCatching { SpotifyLibrary.pathfinder("queryArtistOverview", h, vars) }.getOrNull()
                ?.obj("data")?.obj("artistUnion") ?: continue
            val name = a.obj("profile")?.str("name") ?: fallbackTitle
            val thumb = a.obj("visuals")?.obj("avatarImage")?.srcUrl() ?: a.obj("visuals")?.obj("headerImage")?.srcUrl() ?: fallbackThumb
            val disc = a.obj("discography")
            val songs = disc?.items("topTracks").orEmpty().mapNotNull { row ->
                val t = row.obj("track") ?: return@mapNotNull null
                val tid = t.str("uri")?.substringAfterLast(':') ?: return@mapNotNull null
                val album = t.obj("albumOfTrack")
                Song(
                    videoId = "sp:$tid", title = t.str("name").orEmpty(),
                    artist = t.items("artists").mapNotNull { it.obj("profile")?.str("name") }.joinToString(", ").ifBlank { name },
                    thumbnailUrl = album?.obj("coverArt")?.srcUrl() ?: thumb,
                    durationText = dur(t.obj("duration")?.get("totalMilliseconds")?.jsonPrimitive?.intOrNull ?: 0),
                    albumName = album?.str("name"), albumId = album?.str("uri")?.let { "spotify:album:${it.substringAfterLast(':')}" },
                )
            }
            fun releases(path: JsonObject?): List<ShelfItem> = path?.items("items").orEmpty().flatMap { row ->
                row.obj("releases")?.items("items") ?: listOf(row)
            }.mapNotNull { r ->
                val rid = r.str("id") ?: r.str("uri")?.substringAfterLast(':') ?: return@mapNotNull null
                ShelfItem(r.str("name").orEmpty(), r.obj("date")?.str("year").orEmpty(), r.obj("coverArt")?.srcUrl(), null, "spotify:album:$rid")
            }
            val sections = buildList {
                releases(disc?.obj("popularReleasesAlbums")).takeIf { it.isNotEmpty() }?.let { add(HomeShelf("Popular releases", it)) }
                releases(disc?.obj("albums")).takeIf { it.isNotEmpty() }?.let { add(HomeShelf("Albums", it)) }
                releases(disc?.obj("singles")).takeIf { it.isNotEmpty() }?.let { add(HomeShelf("Singles & EPs", it)) }
                val rel = a.obj("relatedContent")?.obj("relatedArtists")?.items("items").orEmpty().mapNotNull { r ->
                    val rid = r.str("id") ?: r.str("uri")?.substringAfterLast(':') ?: return@mapNotNull null
                    ShelfItem(r.obj("profile")?.str("name").orEmpty(), "Artist", r.obj("visuals")?.obj("avatarImage")?.srcUrl(), null, "spotify:artist:$rid")
                }
                if (rel.isNotEmpty()) add(HomeShelf("Fans also like", rel))
            }
            if (songs.isEmpty() && sections.isEmpty()) continue
            val stats = a.obj("stats")
            val listeners = stats?.get("monthlyListeners")?.jsonPrimitive?.intOrNull?.let { "%,d monthly listeners".format(it) }
                ?: stats?.get("followers")?.jsonPrimitive?.intOrNull?.let { "%,d followers".format(it) }
            val bio = a.obj("profile")?.obj("biography")?.str("text")?.replace(Regex("<[^>]*>"), "")?.ifBlank { null }
            return SpotifyPage(name, "Artist", thumb, songs, sections, bio, listeners)
        }
        return null
    }

    /** Last resort: Spotify search for the artist's tracks and albums. */
    private suspend fun artistViaSearch(id: String, name: String, thumb: String?): SpotifyPage? {
        val q = java.net.URLEncoder.encode("artist:\"$name\"", "UTF-8")
        val root = runCatching { SpotifyLibrary.restGet("/search?q=$q&type=track,album&limit=10&market=from_token") }.getOrNull() ?: return null
        val songs = root.obj("tracks")?.arr("items").orEmpty().mapNotNull { (it as? JsonObject) }
            .filter { t -> t.arr("artists").orEmpty().any { (it as? JsonObject)?.str("id") == id } }
            .mapNotNull { it.track(null, null, null) }
        val albums = root.obj("albums")?.arr("items").orEmpty().mapNotNull { (it as? JsonObject) }
            .filter { t -> t.arr("artists").orEmpty().any { (it as? JsonObject)?.str("id") == id } }
            .map { x -> ShelfItem(x.str("name").orEmpty(), (x.str("release_date") ?: "").take(4), x.img(), null, "spotify:album:${x.str("id")}") }
        if (songs.isEmpty() && albums.isEmpty()) return null
        return SpotifyPage(name, "Artist", thumb, songs, if (albums.isEmpty()) emptyList() else listOf(HomeShelf("Albums", albums)))
    }

    fun SpotifyTrack.toSong(albumImg: String?, albumId: String?) = Song(
        videoId = "sp:$id", title = title, artist = artist, thumbnailUrl = imageUrl ?: albumImg,
        durationText = dur(durationMs), albumName = album, albumId = albumId,
    )

    fun parseTrack(o: JsonObject): Song? = o.track(null, null, null)

    private fun dur(ms: Int) = if (ms <= 0) null else "%d:%02d".format(ms / 60000, ms / 1000 % 60)

    private fun JsonObject.track(img: String?, album: String?, albumId: String?): Song? {
        val id = str("id") ?: return null
        val al = this["album"] as? JsonObject
        return Song(
            videoId = "sp:$id", title = str("name").orEmpty(), artist = artistNames(),
            thumbnailUrl = al?.img() ?: img, durationText = dur(this["duration_ms"]?.jsonPrimitive?.intOrNull ?: 0),
            albumName = al?.str("name") ?: album, albumId = albumId ?: al?.str("id")?.let { "spotify:album:$it" },
            trackNumber = this["track_number"]?.jsonPrimitive?.intOrNull,
            isExplicit = (this["explicit"]?.jsonPrimitive?.contentOrNull)?.toBooleanStrictOrNull(),
        )
    }

    private fun JsonObject.str(k: String) = runCatching { this[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray
    private fun JsonObject.artistNames() = arr("artists")?.mapNotNull { (it as? JsonObject)?.str("name") }?.joinToString(", ").orEmpty()
    private fun JsonObject.img() = arr("images")?.firstOrNull()?.jsonObject?.str("url")
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
}
