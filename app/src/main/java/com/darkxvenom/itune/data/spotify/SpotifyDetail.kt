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
import kotlinx.serialization.json.JsonElement
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
    val headerImageUrl: String? = null,
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
                albumViaPathfinder(id, fallbackTitle, fallbackThumb)
                    ?: runCatching { albumViaRest(id, fallbackTitle, fallbackThumb) }.getOrNull()
                    ?: SpotifyPage(fallbackTitle, "Album", fallbackThumb, emptyList())
            }
            "artist" -> coroutineScope {
                // Prioritize Pathfinder GraphQL queryArtistOverview to avoid 429 delays and errors
                artistViaPathfinder(id, fallbackTitle, fallbackThumb)?.let { return@coroutineScope it }
                artistViaSearch(id, fallbackTitle, fallbackThumb)?.let { return@coroutineScope it }
                val info = runCatching { SpotifyLibrary.restGet("/artists/$id") }.getOrNull()
                val releases = runCatching { artistAlbums(id) }.getOrDefault(emptyList())
                val sections = buildList {
                    fun shelf(title: String, list: List<JsonObject>) {
                        val items = list.mapNotNull { x ->
                            val releaseId = x.str("id") ?: return@mapNotNull null
                            ShelfItem(
                                x.str("name").orEmpty(),
                                x.str("release_date").orEmpty().take(4),
                                x.img(),
                                null,
                                "spotify:album:$releaseId",
                            )
                        }
                        if (items.isNotEmpty()) add(HomeShelf(title, items))
                    }
                    shelf("Albums", releases.filter { it.str("album_type") == "album" })
                    shelf("Singles & EPs", releases.filter { it.str("album_type") == "single" })
                    shelf("Compilations", releases.filter { it.str("album_type") == "compilation" })
                    shelf("Appears On", releases.filter { it.str("album_group") == "appears_on" })
                }
                val followers = info?.obj("followers")?.get("total")?.jsonPrimitive?.intOrNull
                if (sections.isEmpty()) error("Spotify didn't return any data for this artist")
                SpotifyPage(
                    info?.str("name") ?: fallbackTitle,
                    "Artist",
                    info?.img() ?: fallbackThumb,
                    emptyList(),
                    sections,
                    info?.arr("genres")?.joinToString(", ") { it.jsonPrimitive.content }?.ifBlank { null },
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

    private const val ARTIST_PLAYLISTS_HASH = "54f7e5a5a2af05b7dc98526df376a46c6b15c05440c8dfdc8f6cecb1a807eca7"

    private val ALBUM_HASHES = listOf(
        "0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f", // Web player live getAlbum / queryAlbumTracks
        "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10", // Upstream getAlbum candidate
        "8628ad33de3267d7bef516c76a746979a5f98891a2c9eaff3dfec828abdcd983", // Web player getAlbumNameAndTracks
    )

    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.items(k: String): List<JsonObject> = obj(k)?.arr("items").orEmpty().mapNotNull { it as? JsonObject }
    private fun JsonObject.srcUrl(): String? =
        arr("sources")?.mapNotNull { it as? JsonObject }?.maxByOrNull { it["width"]?.jsonPrimitive?.intOrNull ?: 0 }?.str("url")

    private suspend fun albumViaPathfinder(id: String, fallbackTitle: String, fallbackThumb: String?): SpotifyPage? {
        val vars = buildJsonObject {
            put("uri", "spotify:album:$id")
            put("locale", "")
            put("offset", 0)
            put("limit", 150)
        }
        for (h in ALBUM_HASHES) {
            var albumData: JsonObject? = null
            for (operation in listOf("getAlbum", "getAlbumNameAndTracks")) {
                val root = runCatching { SpotifyLibrary.pathfinder(operation, h, vars) }.getOrNull()
                    ?: continue
                albumData = root.obj("data")?.obj("albumUnion")
                    ?: root.obj("data")?.obj("album")
                if (albumData != null) break
            }
            val album = albumData ?: continue

            val name = album.str("name") ?: fallbackTitle
            val img = album.obj("coverArt")?.srcUrl() ?: fallbackThumb
            val artistList = album.obj("artists")?.items("items").orEmpty()
            val artistName = artistList.mapNotNull { it.obj("profile")?.str("name") ?: it.str("name") }.joinToString(", ").ifBlank { "" }
            val year = album.obj("date")?.let { it.str("year") ?: it.str("isoString")?.take(4) }.orEmpty()
            val subtitle = when {
                artistName.isNotBlank() && year.isNotBlank() -> "$artistName • $year"
                artistName.isNotBlank() -> artistName
                year.isNotBlank() -> "Album • $year"
                else -> "Album"
            }

            val tracksSection = album.obj("tracksV2") ?: album.obj("tracks")
            val trackItems = tracksSection?.items("items").orEmpty()
            val songs = trackItems.mapNotNull { elem ->
                val t = elem.obj("track") ?: elem
                val tid = t.str("uri")?.substringAfterLast(':') ?: t.str("id") ?: return@mapNotNull null
                val tArtists = t.obj("artists")?.items("items")?.mapNotNull {
                    it.obj("profile")?.str("name") ?: it.str("name")
                }?.joinToString(", ")?.ifBlank { artistName } ?: artistName
                val durationMs = t.obj("duration")?.get("totalMilliseconds")?.jsonPrimitive?.intOrNull
                    ?: t.get("duration_ms")?.jsonPrimitive?.intOrNull
                    ?: 0
                Song(
                    videoId = "sp:$tid",
                    title = t.str("name").orEmpty(),
                    artist = tArtists,
                    thumbnailUrl = t.obj("albumOfTrack")?.obj("coverArt")?.srcUrl() ?: img,
                    durationText = dur(durationMs),
                    albumName = name,
                    albumId = "spotify:album:$id",
                )
            }
            if (songs.isNotEmpty()) {
                return SpotifyPage(name, subtitle, img, songs)
            }
        }
        return null
    }

    private suspend fun albumViaRest(id: String, fallbackTitle: String, fallbackThumb: String?): SpotifyPage {
        val a = SpotifyLibrary.restGet("/albums/$id?market=from_token")
        val name = a.str("name") ?: fallbackTitle
        val img = a.img() ?: fallbackThumb
        val artist = a.artistNames()
        val trackItems = mutableListOf<JsonObject>()
        var offset = 0
        val total = a.obj("tracks")?.int("total") ?: 0
        trackItems += a.obj("tracks")?.arr("items").orEmpty().mapNotNull { it as? JsonObject }
        offset += trackItems.size
        while (offset < total) {
            val page = SpotifyLibrary.restGet("/albums/$id/tracks?limit=50&offset=$offset&market=from_token")
            val items = page.arr("items").orEmpty().mapNotNull { it as? JsonObject }
            if (items.isEmpty()) break
            trackItems += items
            offset += items.size
        }
        val songs = trackItems.mapNotNull { it.track(img, name, "spotify:album:$id") }
        val totalTracks = a.obj("tracks")?.int("total") ?: songs.size
        val label = a.str("label")?.takeIf { it.isNotBlank() }
        val genres = a.arr("genres").orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }.joinToString(", ")
        val copyright = a.arr("copyrights").orEmpty().mapNotNull { (it as? JsonObject)?.str("text") }
            .distinct().joinToString("\n")
        val details = listOfNotNull(
            "$totalTracks tracks",
            label?.let { "Label: $it" },
            genres.takeIf { it.isNotBlank() }?.let { "Genres: $it" },
            copyright.takeIf { it.isNotBlank() },
        ).joinToString("\n")
        return SpotifyPage(
            name,
            listOf(artist, a.str("release_date").orEmpty().take(4)).filter { it.isNotBlank() }.joinToString(" • "),
            img,
            songs,
            description = details.ifBlank { null },
        )
    }

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
            val restReleases = runCatching { artistAlbums(id) }.getOrDefault(emptyList())
            val playlistItems = (
                a.obj("profile")?.obj("playlistsV2")?.let(::collectSpotifyPlaylists).orEmpty() +
                    runCatching {
                        val playlistVars = buildJsonObject {
                            put("uri", "spotify:artist:$id")
                            put("offset", 0)
                            put("limit", 50)
                        }
                        collectSpotifyPlaylists(SpotifyLibrary.pathfinder("queryArtistPlaylists", ARTIST_PLAYLISTS_HASH, playlistVars))
                    }.getOrDefault(emptyList())
                ).distinctBy { it.browseId }
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
                if (restReleases.isEmpty()) {
                    releases(disc?.obj("albums")).takeIf { it.isNotEmpty() }?.let { add(HomeShelf("Albums", it)) }
                    releases(disc?.obj("singles")).takeIf { it.isNotEmpty() }?.let { add(HomeShelf("Singles & EPs", it)) }
                    releases(disc?.obj("compilations")).takeIf { it.isNotEmpty() }?.let { add(HomeShelf("Compilations", it)) }
                }
                val relatedContent = a.obj("relatedContent")
                fun relatedShelf(key: String, title: String) {
                    val items = relatedContent?.obj(key)?.items("items").orEmpty().flatMap { row ->
                        row.obj("releases")?.items("items") ?: listOf(row)
                    }.mapNotNull { r ->
                        val rid = r.str("id") ?: r.str("uri")?.substringAfterLast(':') ?: return@mapNotNull null
                        ShelfItem(r.str("name").orEmpty(), r.obj("date")?.str("year").orEmpty(), r.obj("coverArt")?.srcUrl(), null, "spotify:album:$rid")
                    }
                    if (items.isNotEmpty()) add(HomeShelf(title, items))
                }
                relatedShelf("appearsOn", "Appears On")
                relatedShelf("featuringV2", "Featuring")
                relatedShelf("discoveredOnV2", "Discovered On")
                val pinned = a.obj("profile")?.obj("pinnedItem")
                val pinnedUri = pinned?.str("uri")
                val pinnedTitle = pinned?.str("title")
                if (!pinnedUri.isNullOrBlank() && !pinnedTitle.isNullOrBlank()) {
                    val pinnedImage = pinned.obj("thumbnailImage")?.obj("data")?.srcUrl()
                        ?: pinned.obj("coverArt")?.srcUrl()
                    val pinnedSubtitle = listOfNotNull(
                        pinned.str("subtitle")?.takeIf { it.isNotBlank() },
                        pinned.str("comment")?.takeIf { it.isNotBlank() },
                    ).joinToString(" • ")
                    add(HomeShelf("Artist Pick", listOf(
                        ShelfItem(pinnedTitle, pinnedSubtitle, pinnedImage, null, pinnedUri),
                    )))
                }
                if (playlistItems.isNotEmpty()) add(HomeShelf("Artist Playlists", playlistItems))
                val restAlbums = restReleases.filter { it.str("album_type") == "album" }
                val restSingles = restReleases.filter { it.str("album_type") == "single" }
                val restCompilations = restReleases.filter { it.str("album_type") == "compilation" }
                fun restShelf(title: String, list: List<JsonObject>) {
                    val mapped = list.mapNotNull { r ->
                        val rid = r.str("id") ?: return@mapNotNull null
                        ShelfItem(r.str("name").orEmpty(), r.str("release_date").orEmpty().take(4), r.img(), null, "spotify:album:$rid")
                    }
                    if (mapped.isNotEmpty()) add(HomeShelf(title, mapped))
                }
                restShelf("Albums", restAlbums)
                restShelf("Singles & EPs", restSingles)
                restShelf("Compilations", restCompilations)
                restShelf("Appears On", restReleases.filter { it.str("album_group") == "appears_on" })
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
            val headerImage = a.obj("headerImage")?.obj("data")?.srcUrl()
                ?: a.obj("visuals")?.obj("headerImage")?.srcUrl()
            return SpotifyPage(name, "Artist", thumb, songs, sections, bio, listeners, headerImage)
        }
        return null
    }

    /** Extract Spotify playlist cards from the artist-playlists Pathfinder response. */
    private fun collectSpotifyPlaylists(root: JsonElement): List<ShelfItem> {
        val found = linkedMapOf<String, ShelfItem>()
        fun visit(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val uri = element.str("uri")
                    if (!uri.isNullOrBlank() && uri.startsWith("spotify:playlist:")) {
                        val id = uri.substringAfterLast(':')
                        val image = element.obj("coverArt")?.srcUrl()
                            ?: element.obj("images")?.arr("items")?.firstOrNull()?.let { (it as? JsonObject)?.str("url") }
                            ?: element.arr("images")?.firstOrNull()?.let { (it as? JsonObject)?.str("url") }
                        val title = element.str("name") ?: element.obj("profile")?.str("name")
                        if (!title.isNullOrBlank()) found[id] = ShelfItem(title, "Spotify playlist", image, null, uri)
                    }
                    element.values.forEach(::visit)
                }
                is JsonArray -> element.forEach(::visit)
                else -> Unit
            }
        }
        visit(root)
        return found.values.toList()
    }

    /** Fetch every Spotify artist release page; Web API currently caps this endpoint at 10. */
    private suspend fun artistAlbums(id: String): List<JsonObject> {
        val collected = mutableListOf<JsonObject>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total && offset < 1000) {
            val page = SpotifyLibrary.restGet(
                "/artists/$id/albums?include_groups=album,single,appears_on,compilation&limit=10&offset=$offset&market=from_token",
            )
            val items = page.arr("items").orEmpty().mapNotNull { it as? JsonObject }
            if (items.isEmpty()) break
            collected += items
            offset += items.size
            total = page.int("total") ?: offset
        }
        return collected.distinctBy { it.str("id") }
    }

    /** Last resort: Spotify search for the artist's tracks and albums. */
    private suspend fun artistViaSearch(id: String, name: String, thumb: String?): SpotifyPage? {
        val search = runCatching { SpotifySearch.search("artist:\"$name\"") }.getOrNull()
            ?: runCatching { SpotifySearch.search(name) }.getOrNull()
            ?: return null
        val songs = search.songs.filter { it.artist.contains(name, ignoreCase = true) }
        val albums = search.albums.filter { it.subtitle.contains(name, ignoreCase = true) || it.title.contains(name, ignoreCase = true) }
            .map { ShelfItem(it.title, it.subtitle, it.image, null, it.browseId) }
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
    private fun JsonObject.int(k: String) = runCatching { this[k]?.jsonPrimitive?.intOrNull }.getOrNull()
    private fun JsonObject.arr(k: String) = this[k] as? JsonArray
    private fun JsonObject.artistNames() = arr("artists")?.mapNotNull { (it as? JsonObject)?.str("name") }?.joinToString(", ").orEmpty()
    private fun JsonObject.img() = arr("images")?.firstOrNull()?.jsonObject?.str("url")
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
}
