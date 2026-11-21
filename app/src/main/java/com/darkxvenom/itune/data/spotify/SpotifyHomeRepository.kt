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

import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.HomeFeedItem
import com.darkxvenom.itune.data.model.HomeFeedShelf
import com.darkxvenom.itune.data.model.HomeItemKind
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.model.HomeShelfGroup
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.toMusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Spotify-sourced home shelves shown below the YouTube Music shelves once the user is
 * signed in to Spotify. Songs are matched to YouTube up-front so they play for real;
 * albums, artists, playlists and podcasts carry a `spotify:<type>:<id>` browseId and are
 * converted to YouTube pages lazily, on tap, via [resolve].
 */
object SpotifyHomeRepository {
    private const val PREFIX = "spotify:"

    private val _shelves = MutableStateFlow<List<HomeFeedShelf>>(emptyList())
    val shelves: StateFlow<List<HomeFeedShelf>> = _shelves.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val matchCache = ConcurrentHashMap<String, MusicTrack>()
    private val gate = Semaphore(4)
    @Volatile private var loaded = false

    // YouTube track id → Spotify uri it was matched from, and the ids of the queue the user
    // started from Spotify data (these are logged to Spotify history, everything else to YouTube).
    private val matchedFrom = ConcurrentHashMap<String, String>()
    @Volatile private var originIds: Set<String> = emptySet()

    fun markOrigin(ytIds: Collection<String>) {
        originIds = ytIds.filter { matchedFrom.containsKey(it) }.toSet()
    }

    /** Spotify uri if [ytId] is playing because the user picked it from Spotify data, else null. */
    fun originUri(ytId: String): String? = if (ytId in originIds) matchedFrom[ytId] else null

    private val order = listOf(
        "sp_top_tracks", "sp_recent", "sp_trending", "sp_charts", "sp_top_artists", "sp_playlists",
        "sp_new_releases", "sp_liked", "sp_albums", "sp_podcasts", "sp_following",
    )

    /** Spotify's own home sections come first, in the order Spotify returns them. */
    private fun rank(key: String): Int =
        if (key.startsWith("sp_home_") || key.startsWith("sp_hero_")) key.substring(8).split("_").let { (it[0].toIntOrNull() ?: 0) * 10 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        else 1000 + order.indexOf(key).let { if (it < 0) 99 else it }

    // Editorial / chart playlists (public Spotify-owned ids).
    private val charts = listOf(
        "37i9dQZEVXbMDoHDwVN2tF" to "Top 50 - Global",
        "37i9dQZEVXbLZ52XmnySJg" to "Top 50 - India",
        "37i9dQZF1DXcBWIGoYBM5M" to "Today's Top Hits",
        "37i9dQZEVXbLiRSasKsNU9" to "Viral 50 - Global",
    )

    fun clear() {
        loaded = false
        _shelves.value = emptyList()
    }

    suspend fun refresh(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        _loading.value = true
        try {
            coroutineScope {
                val homeJob = async(Dispatchers.IO) { runCatching { loadHomeFeed() }.getOrDefault(0) }
                fun shelf(key: String, block: suspend () -> HomeFeedShelf?) = async(Dispatchers.IO) {
                    val s = runCatching { block() }.getOrNull()
                    if (s != null && s.items.isNotEmpty()) publish(s)
                }
                listOf(
                    shelf("sp_top_tracks") { songShelf("sp_top_tracks", "Your Top Songs", "/me/top/tracks?limit=20&time_range=short_term", "items") { it } },
                    shelf("sp_recent") { songShelf("sp_recent", "Recently Played on Spotify", "/me/player/recently-played?limit=30", "items") { it.obj("track") } },
                    shelf("sp_liked") { songShelf("sp_liked", "Your Liked Songs", "/me/tracks?limit=20", "items") { it.obj("track") } },
                    shelf("sp_trending") {
                        if (homeJob.await() > 0) return@shelf null
                        val (_, tracks) = SpotifyLibrary.fetchPlaylistDetails(charts[0].first)
                        songItems(tracks.take(20))?.let {
                            HomeFeedShelf("sp_trending", "Trending on Spotify", "Top 50 Global", it, HomeShelfGroup.TRENDING)
                        }
                    },
                    shelf("sp_charts") { if (homeJob.await() > 0) null else chartsShelf() },
                    shelf("sp_top_artists") { artistShelf("sp_top_artists", "Your Top Artists", "/me/top/artists?limit=20") { it } },
                    shelf("sp_following") { artistShelf("sp_following", "Artists You Follow", "/me/following?type=artist&limit=20") { it } },
                    shelf("sp_playlists") {
                        val items = SpotifyLibrary.playlists().filter { it.id != SpotifyLibrary.LIKED_ID }.take(20).map {
                            HomeFeedItem(HomeItemKind.PLAYLIST, it.name, it.owner.orEmpty(), it.imageUrl, browseId = "${PREFIX}playlist:${it.id}")
                        }
                        HomeFeedShelf("sp_playlists", "Your Spotify Playlists", "", items)
                    },
                    shelf("sp_new_releases") { if (homeJob.await() > 0) null else albumShelf("sp_new_releases", "New on Spotify", "/browse/new-releases?limit=20", "albums") { it } },
                    shelf("sp_albums") { albumShelf("sp_albums", "Your Saved Albums", "/me/albums?limit=20", "items") { it.obj("album") } },
                    shelf("sp_podcasts") { podcastShelf() },
                ).forEach { it.await() }
                homeJob.await()
            }
        } finally {
            _loading.value = false
        }
    }

    private fun publish(s: HomeFeedShelf) = _shelves.update { cur ->
        (cur.filterNot { it.key == s.key } + s).sortedBy { rank(it.key) }
    }

    // ── Spotify Home feed → shelves ──────────────────────────────────────────

    private suspend fun loadHomeFeed(): Int = coroutineScope {
        val sections = SpotifyLibrary.homeFeed().obj("data")?.obj("home")
            ?.obj("sectionContainer")?.obj("sections")?.arr("items") ?: return@coroutineScope 0
        var count = 0
        sections.forEachIndexed { index, el ->
            val sec = el as? JsonObject ?: return@forEachIndexed
            val title = sec.obj("data")?.obj("title")?.str("transformedLabel")?.takeIf { it.isNotBlank() }
                ?: return@forEachIndexed
            val raw = sec.obj("sectionItems")?.arr("items").orEmpty()
                .mapNotNull { ((it as? JsonObject)?.obj("content")?.obj("data")) }
            val items = raw.mapNotNull { homeItem(it) }
            // Keep each kind of card in its own shelf (playlists / albums / artists / shows / episodes / songs)
            val groups = items.groupBy { it.item.kind }
            groups.entries.forEachIndexed { gi, (kind, entries) ->
                val resolved = entries.map { entry ->
                    async {
                        val track = entry.track
                        if (track == null) entry.item
                        else matched(track)?.let { m ->
                            entry.item.copy(
                                videoId = m.id, artistName = m.artists.ifBlank { track.artist }, artistId = m.artistId,
                                albumName = m.album ?: track.album, albumId = m.albumId, isExplicit = m.isExplicit,
                            )
                        }
                    }
                }.mapNotNull { it.await() }
                if (resolved.isNotEmpty()) {
                    val isEpisodes = entries.firstOrNull()?.item?.browseId?.startsWith("${PREFIX}episode:") == true
                    val label = when {
                        groups.size <= 1 -> ""
                        isEpisodes -> " · Episodes"
                        kind == HomeItemKind.PODCAST -> " · Shows"
                        kind == HomeItemKind.PLAYLIST -> " · Playlists"
                        kind == HomeItemKind.ALBUM -> " · Albums"
                        kind == HomeItemKind.ARTIST -> " · Artists"
                        else -> " · Songs"
                    }
                    val hero = kind == HomeItemKind.PLAYLIST && title.contains("made for you", ignoreCase = true)
                    publish(
                        if (hero) HomeFeedShelf("sp_hero_%03d_%d".format(index, gi), title, "", resolved.take(6), groupFor(title))
                        else HomeFeedShelf("sp_home_%03d_%d".format(index, gi), title + label, "", resolved.take(20), groupFor(title)),
                    )
                    count++
                }
            }
        }
        count
    }

    private fun groupFor(title: String) = when {
        title.contains("trending", true) || title.contains("popular", true) -> HomeShelfGroup.TRENDING
        title.contains("chart", true) || title.contains("top ", true) -> HomeShelfGroup.CHARTS
        title.contains("podcast", true) || title.contains("show", true) -> HomeShelfGroup.PODCASTS
        else -> HomeShelfGroup.FEED
    }

    private class HomeEntry(val item: HomeFeedItem, val track: SpotifyTrack? = null)

    private fun homeItem(d: JsonObject): HomeEntry? {
        val uri = d.str("uri") ?: return null
        val id = uri.substringAfterLast(':')
        val type = d.str("__typename").orEmpty()
        val name = d.str("name") ?: d.obj("profile")?.str("name") ?: return null
        val img = bestImage(d)
        return when {
            type.contains("Playlist") -> HomeEntry(HomeFeedItem(HomeItemKind.PLAYLIST, name,
                d.obj("ownerV2")?.obj("data")?.str("name").orEmpty(), img, browseId = "${PREFIX}playlist:$id"))
            type.contains("Album") -> {
                val artist = d.obj("artists")?.arr("items")?.firstOrNull()?.jsonObject?.obj("profile")?.str("name").orEmpty()
                HomeEntry(HomeFeedItem(HomeItemKind.ALBUM, name, artist, img, browseId = "${PREFIX}album:$id", artistName = artist))
            }
            type.contains("Artist") -> HomeEntry(HomeFeedItem(HomeItemKind.ARTIST, name, "Artist", img, browseId = "${PREFIX}artist:$id"))
            type.contains("Episode") -> HomeEntry(HomeFeedItem(HomeItemKind.PODCAST, name,
                d.obj("podcastV2")?.obj("data")?.str("name").orEmpty(), img, browseId = "${PREFIX}episode:$id"))
            type.contains("Podcast") -> HomeEntry(HomeFeedItem(HomeItemKind.PODCAST, name,
                d.obj("publisher")?.str("name").orEmpty(), img, browseId = "${PREFIX}show:$id"))
            type.contains("Track") -> {
                val artist = d.obj("artists")?.arr("items")?.mapNotNull { (it as? JsonObject)?.obj("profile")?.str("name") }
                    ?.joinToString(", ").orEmpty()
                val t = SpotifyTrack(id, name, artist, d.obj("albumOfTrack")?.str("name"), 0, img)
                HomeEntry(HomeFeedItem(HomeItemKind.SONG, name, artist, img), t)
            }
            else -> null
        }
    }

    /** Largest image among the shapes Spotify uses for covers / avatars / playlist art. */
    private fun bestImage(d: JsonObject): String? {
        val lists = listOfNotNull(
            d.obj("images")?.arr("items")?.firstOrNull()?.jsonObject?.arr("sources"),
            d.obj("coverArt")?.arr("sources"),
            d.obj("visuals")?.obj("avatarImage")?.arr("sources"),
            d.obj("albumOfTrack")?.obj("coverArt")?.arr("sources"),
        )
        val src = lists.firstOrNull { it.isNotEmpty() } ?: return null
        return src.mapNotNull { it as? JsonObject }
            .maxByOrNull { it["width"]?.jsonPrimitive?.intOrNull ?: 0 }?.str("url")
    }

    // ── Shelf builders ───────────────────────────────────────────────────────

    private suspend fun songShelf(
        key: String, title: String, path: String, listKey: String, pick: (JsonObject) -> JsonObject?,
    ): HomeFeedShelf? {
        val root = SpotifyLibrary.restGet(path)
        val tracks = root.arr(listKey)?.mapNotNull { (it as? JsonObject)?.let(pick)?.toTrack() }
            ?.distinctBy { it.id }.orEmpty()
        return songItems(tracks.take(20))?.let { HomeFeedShelf(key, title, "", it) }
    }

    private suspend fun songItems(tracks: List<SpotifyTrack>): List<HomeFeedItem>? = coroutineScope {
        tracks.map { t -> async { matched(t)?.let { m -> t to m } } }.mapNotNull { it.await() }
            .map { (sp, m) ->
                HomeFeedItem(
                    kind = HomeItemKind.SONG, title = m.title.ifBlank { sp.title },
                    subtitle = m.artists.ifBlank { sp.artist }, thumbnailUrl = sp.imageUrl ?: m.thumbnailUrl,
                    videoId = m.id, artistName = m.artists.ifBlank { sp.artist }, artistId = m.artistId,
                    albumName = m.album ?: sp.album, albumId = m.albumId, isExplicit = m.isExplicit,
                )
            }.takeIf { it.isNotEmpty() }
    }

    private suspend fun matched(t: SpotifyTrack): MusicTrack? {
        matchCache[t.id]?.let { return it }
        val m = gate.withPermit { runCatching { SpotifyImporter.matchTrack(t) }.getOrNull() } ?: return null
        matchCache[t.id] = m
        matchedFrom[m.id] = "spotify:track:${t.id}"
        return m
    }

    private suspend fun artistShelf(key: String, title: String, path: String, pick: (JsonObject) -> JsonObject?): HomeFeedShelf? {
        val root = SpotifyLibrary.restGet(path)
        val list = root.arr("items") ?: root.obj("artists")?.arr("items")
        val items = list?.mapNotNull { (it as? JsonObject)?.let(pick) }?.mapNotNull { a ->
            val id = a.str("id") ?: return@mapNotNull null
            HomeFeedItem(HomeItemKind.ARTIST, a.str("name").orEmpty(), "Artist", a.images(), browseId = "${PREFIX}artist:$id")
        }.orEmpty()
        return HomeFeedShelf(key, title, "", items)
    }

    private suspend fun albumShelf(key: String, title: String, path: String, listKey: String, pick: (JsonObject) -> JsonObject?): HomeFeedShelf? {
        val root = SpotifyLibrary.restGet(path)
        val list = root.arr(listKey) ?: root.obj(listKey)?.arr("items")
        val items = list?.mapNotNull { (it as? JsonObject)?.let(pick) }?.mapNotNull { a ->
            val id = a.str("id") ?: return@mapNotNull null
            val artist = a.arr("artists")?.firstOrNull()?.jsonObject?.str("name").orEmpty()
            HomeFeedItem(HomeItemKind.ALBUM, a.str("name").orEmpty(), artist, a.images(),
                browseId = "${PREFIX}album:$id", artistName = artist)
        }.orEmpty()
        return HomeFeedShelf(key, title, "", items, HomeShelfGroup.TRENDING.takeIf { key == "sp_new_releases" } ?: HomeShelfGroup.FEED)
    }

    private suspend fun podcastShelf(): HomeFeedShelf? {
        val root = SpotifyLibrary.restGet("/me/shows?limit=20")
        val items = root.arr("items")?.mapNotNull { (it as? JsonObject)?.obj("show") }?.mapNotNull { s ->
            val id = s.str("id") ?: return@mapNotNull null
            HomeFeedItem(HomeItemKind.PODCAST, s.str("name").orEmpty(), s.str("publisher").orEmpty(), s.images(), browseId = "${PREFIX}show:$id")
        }.orEmpty()
        return HomeFeedShelf("sp_podcasts", "Your Podcasts", "", items, HomeShelfGroup.PODCASTS)
    }

    private suspend fun chartsShelf(): HomeFeedShelf? = coroutineScope {
        val items = charts.map { (id, name) ->
            async { HomeFeedItem(HomeItemKind.PLAYLIST, name, "Spotify", runCatching { SpotifyLibrary.cover(id) }.getOrNull(), browseId = "${PREFIX}playlist:$id") }
        }.map { it.await() }
        HomeFeedShelf("sp_charts", "Spotify Charts", "", items, HomeShelfGroup.CHARTS)
    }

    // ── Tap handling: Spotify entity → YouTube data ──────────────────────────

    sealed interface Action {
        class Play(val tracks: List<MusicTrack>) : Action
        class Open(val browseId: String, val type: BrowseType) : Action
        data object Failed : Action
    }

    fun isSpotifyItem(item: HomeFeedItem) = item.browseId?.startsWith(PREFIX) == true

    suspend fun resolve(item: HomeFeedItem): Action = withContext(Dispatchers.IO) {
        val parts = item.browseId.orEmpty().removePrefix(PREFIX).split(":", limit = 2)
        val type = parts.getOrNull(0)
        val id = parts.getOrNull(1).orEmpty()
        runCatching {
            when (type) {
                "artist" -> {
                    val hits = MusicBackend.searchAllCategories(item.title).artists
                    val hit = hits.firstOrNull { norm(it.name) == norm(item.title) } ?: hits.firstOrNull()
                    hit?.let { Action.Open(it.id, BrowseType.ARTIST) } ?: Action.Failed
                }
                "album" -> {
                    val hits = MusicBackend.searchAllCategories("${item.title} ${item.subtitle}").albums
                    val hit = hits.firstOrNull { norm(it.title) == norm(item.title) } ?: hits.firstOrNull()
                    hit?.let { Action.Open(it.id, BrowseType.ALBUM) } ?: Action.Failed
                }
                "playlist" -> {
                    val (_, tracks) = SpotifyLibrary.fetchPlaylistDetails(id)
                    val ytTracks = coroutineScope { tracks.take(40).map { async { matched(it) } }.mapNotNull { it.await() } }
                    if (ytTracks.isEmpty()) Action.Failed else Action.Play(ytTracks)
                }
                "show" -> {
                    val yt = MusicBackend.searchAllCategories(item.title).playlists
                        .firstOrNull { norm(it.title) == norm(item.title) }
                    if (yt != null) {
                        Action.Open(yt.id, BrowseType.PLAYLIST)
                    } else {
                        val eps = SpotifyLibrary.restGet("/shows/$id/episodes?limit=10&market=from_token").arr("items").orEmpty()
                        val tracks = coroutineScope {
                            eps.mapNotNull { (it as? JsonObject)?.str("name") }.map { n ->
                                async { gate.withPermit { runCatching { MusicBackend.searchMusic("$n ${item.title}").firstOrNull() }.getOrNull() } }
                            }.mapNotNull { it.await() }
                        }
                        if (tracks.isEmpty()) Action.Failed else Action.Play(tracks)
                    }
                }
                "episode" -> {
                    val q = "${item.title} ${item.subtitle}"
                    MusicBackend.searchMusic(q).firstOrNull()?.let {
                        matchedFrom[it.id] = "spotify:episode:$id"
                        Action.Play(listOf(it))
                    } ?: Action.Failed
                }
                else -> Action.Failed
            }
        }.getOrDefault(Action.Failed)
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray
    private fun JsonObject.str(k: String): String? = runCatching { this[k]?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonObject.images(): String? =
        arr("images")?.firstOrNull()?.jsonObject?.str("url")

    private fun JsonObject.toTrack(): SpotifyTrack? {
        val id = str("id") ?: return null
        val album = obj("album")
        return SpotifyTrack(
            id = id,
            title = str("name").orEmpty(),
            artist = arr("artists")?.mapNotNull { (it as? JsonObject)?.str("name") }?.joinToString(", ").orEmpty(),
            album = album?.str("name"),
            durationMs = this["duration_ms"]?.jsonPrimitive?.intOrNull ?: 0,
            imageUrl = album?.images(),
        )
    }

    /** Resolve a Spotify-backed [Song] (`sp:` track / `spe:` episode) to a playable YouTube track. */
    suspend fun matchSong(song: Song): MusicTrack? = when {
        song.videoId.startsWith("sp:") -> matched(
            SpotifyTrack(song.videoId.removePrefix("sp:"), song.title, song.artist, song.albumName,
                parseDur(song.durationText), song.thumbnailUrl),
        )
        song.videoId.startsWith("spe:") -> gate.withPermit {
            runCatching { MusicBackend.searchMusic("${song.title} ${song.artist}").firstOrNull() }.getOrNull()
        }?.also { matchedFrom[it.id] = "spotify:episode:${song.videoId.removePrefix("spe:")}" }
        else -> song.toMusicTrack()
    }

    private fun parseDur(t: String?): Int {
        val p = t?.split(":")?.mapNotNull { it.toIntOrNull() } ?: return 0
        return if (p.size == 2) (p[0] * 60 + p[1]) * 1000 else 0
    }

    /** Spotify "recently played", matched to YouTube so entries are playable from History. */
    suspend fun history(): List<SavedSongMetadata> = withContext(Dispatchers.IO) {
        val items = SpotifyLibrary.restGet("/me/player/recently-played?limit=50").arr("items").orEmpty()
        coroutineScope {
            items.mapNotNull { it as? JsonObject }.map { row ->
                async {
                    val t = row.obj("track")?.toTrack() ?: return@async null
                    val m = matched(t) ?: return@async null
                    SavedSongMetadata(
                        id = m.id, title = t.title, artists = t.artist, album = t.album,
                        thumbnailUrl = t.imageUrl ?: m.thumbnailUrl.orEmpty(), durationSec = (t.durationMs / 1000).toLong(),
                        addedAtMs = runCatching { java.time.Instant.parse(row.str("played_at")).toEpochMilli() }
                            .getOrDefault(System.currentTimeMillis()),
                        spotifyTrackUri = "spotify:track:${t.id}",
                    )
                }
            }.mapNotNull { it.await() }
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.
}
