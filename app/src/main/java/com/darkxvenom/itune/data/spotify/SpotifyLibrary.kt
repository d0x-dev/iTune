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

import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.artist.artistsCompatible
import com.darkxvenom.itune.data.artist.normalizeForMatch
import com.darkxvenom.itune.data.artist.titlesCompatible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val owner: String?,
    val imageUrl: String?,
)

data class SpotifyCreatedPlaylist(
    val id: String,
    val name: String,
    val isPublic: Boolean,
    val shareUrl: String,
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Int,
    val imageUrl: String?,
)

object SpotifyLibrary {
    /** The web player's bearer, and its client token when there is one. */
    class Tokens(val bearer: String, val clientToken: String?)

    fun interface Auth {
        suspend fun tokens(): Tokens?
    }

    /** Installed at app start-up; until then every call fails as signed out. */
    @Volatile
    var auth: Auth? = null

    private const val GQL = "https://api-partner.spotify.com/pathfinder/v2/query"
    private const val PATHFINDER_V1 = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val REST_API = "https://api.spotify.com/v1"
    private const val SEARCH_TRACKS_HASH = "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"
    private const val LIBRARY = "973e511ca44261fda7eebac8b653155e7caee3675abb4fb110cc1b8c78b091c3"
    private const val PLAYLIST = "346811f856fb0b7e4f6c59f8ebea78dd081c6e2fb01b77c954b26259d5fc6763"
    const val LIKED_ID = "liked"
    private const val LIKED_COVER = "https://misc.scdn.co/liked-songs/liked-songs-640.png"
    private const val LIKED = "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/122.0.0.0 Safari/537.36"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /** Every playlist in the library, however many pages it takes. */
    suspend fun playlists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        val collected = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        while (true) {
            val (items, total, raw) = playlistPage(headers, offset)
            collected += items
            offset += raw
            if (raw == 0 || offset >= total) break
        }
        listOf(SpotifyPlaylist(id = LIKED_ID, name = "Liked Songs", owner = null, imageUrl = LIKED_COVER)) + collected
    }

    /** Every track of a playlist, reporting progress after each page. */
    suspend fun tracks(
        playlistId: String,
        onPage: (List<SpotifyTrack>) -> Unit = {},
    ): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        val collected = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (true) {
            val (items, total, raw) = if (playlistId == LIKED_ID) {
                likedPage(headers, offset)
            } else {
                trackPage(headers, playlistId, offset)
            }
            collected += items
            offset += raw
            onPage(collected.toList())
            if (raw == 0 || offset >= total) break
        }
        collected
    }

    /**
     * Fetches both the playlist title and every track using the authenticated
     * Spotify session — works for both private and public playlists.
     */
    suspend fun fetchPlaylistDetails(
        playlistId: String,
        onPage: (List<SpotifyTrack>) -> Unit = {},
    ): Pair<String, List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        if (playlistId == LIKED_ID) {
            val collected = mutableListOf<SpotifyTrack>()
            var offset = 0
            while (true) {
                val (items, total, raw) = likedPage(headers, offset)
                collected += items
                offset += raw
                onPage(collected.toList())
                if (raw == 0 || offset >= total) break
            }
            return@withContext "Liked Songs" to collected
        }
        var playlistTitle: String? = null
        val collected = mutableListOf<SpotifyTrack>()
        var offset = 0
        while (true) {
            val variables = buildJsonObject {
                put("uri", "spotify:playlist:$playlistId")
                put("offset", offset)
                put("limit", 100)
                put("enableWatchFeedEntrypoint", false)
            }
            val root = gql("fetchPlaylist", PLAYLIST, variables, headers)
            if (playlistTitle == null) {
                playlistTitle = root.obj("data")?.obj("playlistV2")?.str("name")?.takeIf { it.isNotBlank() }
            }
            val (items, total, raw) = parseTrackPage(root)
            collected += items
            offset += raw
            onPage(collected.toList())
            if (raw == 0 || offset >= total) break
        }
        (playlistTitle ?: "Imported Spotify Playlist") to collected
    }

    suspend fun cover(playlistId: String): String? = withContext(Dispatchers.IO) {
        if (playlistId == LIKED_ID) return@withContext LIKED_COVER
        val variables = buildJsonObject {
            put("uri", "spotify:playlist:$playlistId")
            put("offset", 0)
            put("limit", 1)
            put("enableWatchFeedEntrypoint", false)
        }
        val root = gql("fetchPlaylist", PLAYLIST, variables, authHeaders())
        largestSource(root.obj("data")?.obj("playlistV2")?.obj("images"))
    }

    private fun playlistPage(headers: Map<String, String>, offset: Int): Triple<List<SpotifyPlaylist>, Int, Int> {
        val variables = buildJsonObject {
            putJsonArray("filters") { add("Playlists") }
            put("order", null as String?)
            put("textFilter", "")
            putJsonArray("features") {
                add("LIKED_SONGS")
                add("YOUR_EPISODES_V2")
                add("PRERELEASES")
                add("EVENTS")
            }
            put("limit", 50)
            put("offset", offset)
            put("flatten", true)
            putJsonArray("expandedFolders") {}
            put("folderUri", null as String?)
            put("includeFoldersWhenFlattening", false)
        }
        return parsePlaylistPage(gql("libraryV3", LIBRARY, variables, headers))
    }

    private fun trackPage(
        headers: Map<String, String>,
        playlistId: String,
        offset: Int,
    ): Triple<List<SpotifyTrack>, Int, Int> {
        val variables = buildJsonObject {
            put("uri", "spotify:playlist:$playlistId")
            put("offset", offset)
            put("limit", 100)
            put("enableWatchFeedEntrypoint", false)
        }
        return parseTrackPage(gql("fetchPlaylist", PLAYLIST, variables, headers))
    }

    private fun likedPage(headers: Map<String, String>, offset: Int): Triple<List<SpotifyTrack>, Int, Int> {
        val variables = buildJsonObject {
            put("offset", offset)
            put("limit", 100)
        }
        return parseLikedPage(gql("fetchLibraryTracks", LIKED, variables, headers))
    }

    private suspend fun authHeaders(): Map<String, String> {
        val tokens = auth?.tokens()
            ?: throw IllegalStateException("Spotify sign-in expired")
        return buildMap {
            put("Authorization", "Bearer ${tokens.bearer}")
            put("Accept", "application/json")
            put("app-platform", "WebPlayer")
            put("Origin", "https://open.spotify.com")
            put("Referer", "https://open.spotify.com/")
            put("User-Agent", USER_AGENT)
            tokens.clientToken?.let { put("Client-Token", it) }
        }
    }

    private fun gql(operation: String, hash: String, variables: JsonObject, headers: Map<String, String>): JsonObject {
        val body = buildJsonObject {
            put("variables", variables)
            put("operationName", operation)
            putJsonObject("extensions") {
                putJsonObject("persistedQuery") {
                    put("version", 1)
                    put("sha256Hash", hash)
                }
            }
        }
        val request = Request.Builder()
            .url(GQL)
            .post(body.toString().toRequestBody(mediaType))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        val text = Net.client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify $operation failed (${response.code})")
            }
            payload
        }
        val root = json.parseToJsonElement(text).jsonObject
        val error = root.arr("errors")?.firstOrNull()?.jsonObject?.str("message")
        if (!error.isNullOrBlank()) throw IllegalStateException(error)
        return root
    }

    // ── Write & Track-Conversion APIs (Create / Share / Add / Rename / Remove / Delete) ──

    private val NOISE_REGEX = Regex(
        """\((?:from|official|lyrical|video|audio)[^)]*\)|\[[^]]*]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun String.cleanForSearch(): String = replace(NOISE_REGEX, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { this.trim() }

    private fun restHeaders(tokens: Tokens): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${tokens.bearer}")
        put("Accept", "application/json")
        put("User-Agent", USER_AGENT)
        tokens.clientToken?.let { put("Client-Token", it) }
    }

    // ── Spotify's own Home feed (the web player's `home` GraphQL operation) ──
    private const val HOME_HASH_DEFAULT = "72325e84c876c72564fb9ab012f602be8ef6a1fdd3039be2f8b4f2be4c229a30"
    @Volatile private var homeHash: String? = null
    private val deviceId = java.util.UUID.randomUUID().toString()

    /**
     * The personalised Home feed exactly as open.spotify.com builds it
     * (`data.home.sectionContainer.sections.items`). Spotify rotates persisted-query hashes
     * per web-player release, so when the known hash is rejected the current one is read
     * out of the web player's own JS bundle.
     */
    suspend fun homeFeed(): JsonObject = withContext(Dispatchers.IO) {
        val headers = authHeaders()
        val vars = buildJsonObject {
            put("timeZone", java.util.TimeZone.getDefault().id)
            put("sp_t", deviceId)
            put("facet", "")
            put("sectionItemsLimit", 15)
        }
        fun attempt(hash: String): JsonObject? =
            runCatching { gql("home", hash, vars, headers) }.getOrNull()
                ?.takeIf { it.obj("data")?.obj("home") != null }
        val tried = mutableSetOf<String>()
        for (h in listOfNotNull(homeHash, HOME_HASH_DEFAULT)) {
            if (!tried.add(h)) continue
            attempt(h)?.let { homeHash = h; return@withContext it }
        }
        val found = runCatching { discoverHomeHash() }.getOrNull()
        if (found != null && tried.add(found)) {
            attempt(found)?.let { homeHash = found; return@withContext it }
        }
        throw IllegalStateException("Spotify home feed unavailable")
    }

    private fun httpText(url: String): String {
        val req = Request.Builder().url(url).get()
            .header("User-Agent", USER_AGENT).header("Accept", "*/*").build()
        return Net.client.newCall(req).execute().use { it.body?.string().orEmpty() }
    }

    private fun discoverHomeHash(): String? {
        val page = httpText("https://open.spotify.com/")
        val scripts = Regex("""<script[^>]+src="(https://[^"]+\.js)"""").findAll(page).map { it.groupValues[1] }.toList()
        val patterns = listOf(
            Regex(""""home","query","([0-9a-f]{64})""""),
            Regex("""home['"]\s*,\s*['"]query['"]\s*,\s*['"]([0-9a-f]{64})"""),
        )
        for (src in scripts.take(10)) {
            val js = runCatching { httpText(src) }.getOrNull() ?: continue
            for (p in patterns) p.find(js)?.let { return it.groupValues[1] }
        }
        return null
    }

    private val restGate = kotlinx.coroutines.sync.Semaphore(3)

    /**
     * Executes [req], retrying HTTP 429 / 503 (honouring `Retry-After`, else 2s/4s/8s backoff,
     * capped at 15s). Returns the final status code and body.
     */
    private suspend fun execRetry(req: Request): Pair<Int, String> {
        var attempt = 0
        while (true) {
            val (code, body, retryAfter) = withContext(Dispatchers.IO) {
                Net.client.newCall(req).execute().use { r ->
                    Triple(r.code, r.body?.string().orEmpty(), r.header("Retry-After")?.trim()?.toLongOrNull())
                }
            }
            if ((code != 429 && code != 503) || attempt >= 4) return code to body
            val waitMs = ((retryAfter ?: (2L shl attempt)).coerceIn(1L, 15L)) * 1000L
            kotlinx.coroutines.delay(waitMs)
            attempt++
        }
    }

    /** Web-player GraphQL (pathfinder) persisted query with the signed-in session. */
    suspend fun pathfinder(operation: String, hash: String, variables: JsonObject): JsonObject =
        withContext(Dispatchers.IO) { gql(operation, hash, variables, authHeaders()) }

    /** Authenticated GET against the Spotify Web API (path starts with "/"). */
    suspend fun restGet(pathAndQuery: String): JsonObject = withContext(Dispatchers.IO) {
        val tokens = auth?.tokens() ?: throw IllegalStateException("Spotify sign-in expired")
        val req = Request.Builder()
            .url("$REST_API$pathAndQuery")
            .get()
            .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
            .build()
        val (code, body) = restGate.withPermit { execRetry(req) }
        if (code !in 200..299) throw IllegalStateException("Spotify request failed ($code)")
        json.parseToJsonElement(body).jsonObject
    }

    private suspend fun currentUserId(tokens: Tokens): String {
        val req = Request.Builder()
            .url("$REST_API/me")
            .get()
            .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
            .build()
        Net.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Could not read Spotify profile (${resp.code})")
            }
            val id = json.parseToJsonElement(body).jsonObject.str("id")
            if (id.isNullOrBlank()) throw IllegalStateException("Spotify user ID missing")
            return id
        }
    }

    /**
     * Creates a new playlist on the signed-in Spotify account with [isPublic] visibility
     * and returns its canonical share URL (`https://open.spotify.com/playlist/<id>`).
     */
    suspend fun createPlaylist(name: String, isPublic: Boolean): SpotifyCreatedPlaylist = withContext(Dispatchers.IO) {
        val tokens = auth?.tokens() ?: throw IllegalStateException("Sign in to Spotify first")
        val cleanName = name.trim().ifBlank { "New Playlist" }
        val payload = buildJsonObject {
            put("name", cleanName)
            put("public", isPublic)
            put("collaborative", false)
            put("description", "")
        }.toString()

        val userId = runCatching { currentUserId(tokens) }.getOrNull()
        val endpoints = buildList {
            if (!userId.isNullOrBlank()) add("$REST_API/users/$userId/playlists")
            add("$REST_API/me/playlists")
        }

        var lastCode = -1
        var createdObj: JsonObject? = null
        for (url in endpoints) {
            val req = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(mediaType))
                .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
                .build()
            val (code, text) = execRetry(req)
            lastCode = code
            if (code in 200..299 && text.isNotBlank()) {
                createdObj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            }
            if (createdObj != null || code == 429) break // don't hammer the fallback endpoint while rate limited
        }

        val obj = createdObj
            ?: throw IllegalStateException("Could not create Spotify playlist (HTTP $lastCode)")
        val id = obj.str("id")?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Spotify did not return a playlist ID")
        val resolvedName = obj.str("name")?.takeIf { it.isNotBlank() } ?: cleanName
        val actualPublic = (obj["public"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: isPublic

        // Ensure requested public/private state is applied if Spotify defaulted differently
        if (actualPublic != isPublic) {
            runCatching {
                val updateBody = buildJsonObject { put("public", isPublic) }.toString()
                val putReq = Request.Builder()
                    .url("$REST_API/playlists/$id")
                    .put(updateBody.toRequestBody(mediaType))
                    .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
                    .build()
                Net.client.newCall(putReq).execute().close()
            }
        }

        val shareUrl = obj.obj("external_urls")?.str("spotify")?.takeIf { it.startsWith("http") }
            ?: "https://open.spotify.com/playlist/$id"
        SpotifyCreatedPlaylist(
            id = id,
            name = resolvedName,
            isPublic = isPublic,
            shareUrl = shareUrl,
        )
    }

    /**
     * Converts a YouTube Music song (`title`, `artist`, optional `album`) into its best
     * matching Spotify track URI (`spotify:track:<id>`) using Pathfinder search + REST fallback.
     */
    suspend fun findSpotifyTrackUri(
        title: String,
        artist: String,
        album: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val tokens = auth?.tokens() ?: return@withContext null
        val cleanTitle = title.cleanForSearch()
        val cleanArtist = artist.cleanForSearch()
        val cleanAlbum = album?.cleanForSearch()?.takeIf { it.isNotBlank() }
        if (cleanTitle.isBlank()) return@withContext null

        val queries = buildList {
            add(listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" "))
            if (cleanAlbum != null) {
                add(listOf(cleanTitle, cleanArtist, cleanAlbum).filter { it.isNotBlank() }.joinToString(" "))
            }
        }.distinct()

        for (q in queries) {
            searchTrackUriViaPathfinder(q, cleanTitle, cleanArtist, tokens)?.let { return@withContext it }
        }
        for (q in queries) {
            searchTrackUriViaRest(q, cleanTitle, cleanArtist, tokens)?.let { return@withContext it }
        }
        null
    }

    private fun searchTrackUriViaPathfinder(
        searchTerm: String,
        wantTitle: String,
        wantArtist: String,
        tokens: Tokens,
    ): String? {
        val clientToken = tokens.clientToken ?: return null
        val variables = buildJsonObject {
            put("searchTerm", searchTerm)
            put("offset", 0)
            put("limit", 10)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", false)
            put("includePreReleases", false)
        }.toString()
        val extensions = buildJsonObject {
            putJsonObject("persistedQuery") {
                put("version", 1)
                put("sha256Hash", SEARCH_TRACKS_HASH)
            }
        }.toString()

        val url = PATHFINDER_V1.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "searchTracks")
            .addQueryParameter("variables", variables)
            .addQueryParameter("extensions", extensions)
            .build()
        val req = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer ${tokens.bearer}")
            .header("Client-Token", clientToken)
            .header("App-platform", "WebPlayer")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        val body = runCatching {
            Net.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return null

        val items = runCatching {
            json.parseToJsonElement(body).jsonObject
                .obj("data")?.obj("searchV2")?.obj("tracksV2")?.arr("items")
        }.getOrNull() ?: return null

        var relaxedUri: String? = null
        val wantTitleNorm = wantTitle.normalizeForMatch()
        for (el in items) {
            val data = (el as? JsonObject)?.obj("item")?.obj("data") ?: continue
            val uri = data.str("uri")
                ?: data.str("id")?.let { "spotify:track:$it" }
                ?: continue
            val name = data.str("name").orEmpty()
            val artistsList = data.obj("artists")?.arr("items")?.mapNotNull { a ->
                (a as? JsonObject)?.obj("profile")?.str("name") ?: (a as? JsonObject)?.str("name")
            }.orEmpty()
            val joinedArtists = artistsList.joinToString(", ")

            if (titlesCompatible(name, wantTitle) &&
                (wantArtist.isBlank() || artistsCompatible(joinedArtists, name, wantArtist, wantTitle))
            ) {
                return uri
            }
            if (relaxedUri == null && name.normalizeForMatch().contains(wantTitleNorm.take(12))) {
                relaxedUri = uri
            }
        }
        return relaxedUri
    }

    private fun searchTrackUriViaRest(
        query: String,
        wantTitle: String,
        wantArtist: String,
        tokens: Tokens,
    ): String? {
        val url = "$REST_API/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "10")
            .build()
        val req = Request.Builder()
            .url(url)
            .get()
            .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
            .build()
        val body = runCatching {
            Net.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return null

        val items = runCatching {
            json.parseToJsonElement(body).jsonObject.obj("tracks")?.arr("items")
        }.getOrNull() ?: return null

        var firstValid: String? = null
        for (el in items) {
            val track = el as? JsonObject ?: continue
            val uri = track.str("uri") ?: continue
            val name = track.str("name").orEmpty()
            val artistsList = track.arr("artists")?.mapNotNull { (it as? JsonObject)?.str("name") }.orEmpty()
            val joined = artistsList.joinToString(", ")
            if (titlesCompatible(name, wantTitle) &&
                (wantArtist.isBlank() || artistsCompatible(joined, name, wantArtist, wantTitle))
            ) {
                return uri
            }
            if (firstValid == null) firstValid = uri
        }
        return firstValid
    }

    /** Adds one or more `spotify:track:<id>` URIs to a Spotify playlist. */
    suspend fun addTracksToPlaylist(playlistId: String, trackUris: List<String>) = withContext(Dispatchers.IO) {
        val cleanUris = trackUris.map { it.trim() }.filter { it.startsWith("spotify:track:") }.distinct()
        if (cleanUris.isEmpty()) return@withContext
        val tokens = auth?.tokens() ?: throw IllegalStateException("Sign in to Spotify first")
        for (chunk in cleanUris.chunked(100)) {
            val body = buildJsonObject {
                putJsonArray("uris") { chunk.forEach { add(it) } }
            }.toString()
            val req = Request.Builder()
                .url("$REST_API/playlists/$playlistId/tracks")
                .post(body.toRequestBody(mediaType))
                .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
                .build()
            val (code, _) = execRetry(req)
            if (code !in 200..299) {
                throw IllegalStateException("Could not add track(s) to Spotify playlist (HTTP $code)")
            }
            kotlinx.coroutines.delay(300)
        }
    }

    /** Removes one or more `spotify:track:<id>` URIs from a Spotify playlist. */
    suspend fun removeTracksFromPlaylist(playlistId: String, trackUris: List<String>) = withContext(Dispatchers.IO) {
        val cleanUris = trackUris.map { it.trim() }.filter { it.startsWith("spotify:track:") }.distinct()
        if (cleanUris.isEmpty()) return@withContext
        val tokens = auth?.tokens() ?: throw IllegalStateException("Sign in to Spotify first")
        val body = buildJsonObject {
            putJsonArray("tracks") {
                cleanUris.forEach { uri ->
                    add(buildJsonObject { put("uri", uri) })
                }
            }
        }.toString()
        val req = Request.Builder()
            .url("$REST_API/playlists/$playlistId/tracks")
            .delete(body.toRequestBody(mediaType))
            .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
            .build()
        Net.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Could not remove track from Spotify playlist (HTTP ${resp.code})")
            }
        }
    }

    /** Renames a Spotify playlist on the user's account. */
    suspend fun renamePlaylist(playlistId: String, newName: String) = withContext(Dispatchers.IO) {
        val tokens = auth?.tokens() ?: throw IllegalStateException("Sign in to Spotify first")
        val body = buildJsonObject { put("name", newName.trim()) }.toString()
        val req = Request.Builder()
            .url("$REST_API/playlists/$playlistId")
            .put(body.toRequestBody(mediaType))
            .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
            .build()
        Net.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Could not rename Spotify playlist (HTTP ${resp.code})")
            }
        }
    }

    /** Removes (unfollows) a Spotify playlist from the user's library. */
    suspend fun unfollowPlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        val tokens = auth?.tokens() ?: throw IllegalStateException("Sign in to Spotify first")
        val req = Request.Builder()
            .url("$REST_API/playlists/$playlistId/followers")
            .delete()
            .apply { restHeaders(tokens).forEach { (k, v) -> header(k, v) } }
            .build()
        Net.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Could not delete Spotify playlist (HTTP ${resp.code})")
            }
        }
    }
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

internal fun parsePlaylistPage(root: JsonObject): Triple<List<SpotifyPlaylist>, Int, Int> {
    val library = root.obj("data")?.obj("me")?.obj("libraryV3")
        ?: throw IllegalStateException("Spotify library response was empty")
    val rawItems = library.arr("items").orEmpty()
    val items = rawItems.mapNotNull { element ->
        val wrapper = element.jsonObject.obj("item") ?: return@mapNotNull null
        val typeName = wrapper.str("__typename").orEmpty()
        if (!typeName.contains("Playlist", ignoreCase = true)) return@mapNotNull null
        val data = wrapper.obj("data") ?: return@mapNotNull null
        if (data.str("__typename") != "Playlist") return@mapNotNull null
        val uri = wrapper.str("_uri") ?: return@mapNotNull null
        val name = data.str("name").orEmpty()
        if (name.isBlank()) return@mapNotNull null
        SpotifyPlaylist(
            id = uri.substringAfterLast(":"),
            name = name,
            owner = data.obj("ownerV2")?.obj("data")?.str("name"),
            imageUrl = coverUrl(data.obj("images")),
        )
    }
    return Triple(items, library.int("totalCount") ?: rawItems.size, rawItems.size)
}

internal fun parseTrackPage(root: JsonObject): Triple<List<SpotifyTrack>, Int, Int> {
    val content = root.obj("data")?.obj("playlistV2")?.obj("content")
        ?: throw IllegalStateException("Spotify playlist response was empty")
    val rawItems = content.arr("items").orEmpty()
    val items = rawItems.mapNotNull { element ->
        val data = element.jsonObject.obj("itemV2")?.obj("data") ?: return@mapNotNull null
        val title = data.str("name").orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val uri = data.str("uri") ?: data.str("_uri") ?: return@mapNotNull null
        val artist = data.obj("artists")?.arr("items").orEmpty().mapNotNull { artist ->
            artist.jsonObject.obj("profile")?.str("name")?.takeIf { it.isNotBlank() }
        }.joinToString(", ")
        val album = data.obj("albumOfTrack")
        SpotifyTrack(
            id = uri.substringAfterLast(":"),
            title = title,
            artist = artist,
            album = album?.str("name"),
            durationMs = data.obj("duration")?.int("totalMilliseconds") ?: 0,
            imageUrl = album?.obj("coverArt")?.arr("sources")?.lastUrl(),
        )
    }
    return Triple(items, content.int("totalCount") ?: rawItems.size, rawItems.size)
}

internal fun parseLikedPage(root: JsonObject): Triple<List<SpotifyTrack>, Int, Int> {
    val tracks = root.obj("data")?.obj("me")?.obj("library")?.obj("tracks")
        ?: throw IllegalStateException("Spotify liked songs response was empty")
    val rawItems = tracks.arr("items").orEmpty()
    val items = rawItems.mapNotNull { element ->
        val wrapper = element.jsonObject.obj("track") ?: return@mapNotNull null
        val data = wrapper.obj("data") ?: return@mapNotNull null
        val title = data.str("name").orEmpty()
        if (title.isBlank()) return@mapNotNull null
        val uri = wrapper.str("_uri") ?: data.str("uri") ?: return@mapNotNull null
        val album = data.obj("albumOfTrack")
        SpotifyTrack(
            id = uri.substringAfterLast(":"),
            title = title,
            artist = data.obj("artists")?.arr("items").orEmpty().mapNotNull { artist ->
                artist.jsonObject.obj("profile")?.str("name")?.takeIf { it.isNotBlank() }
            }.joinToString(", "),
            album = album?.str("name"),
            durationMs = (data.obj("trackDuration") ?: data.obj("duration"))?.int("totalMilliseconds") ?: 0,
            imageUrl = album?.obj("coverArt")?.arr("sources")?.lastUrl(),
        )
    }
    return Triple(items, tracks.int("totalCount") ?: rawItems.size, rawItems.size)
}

private fun largestSource(images: JsonObject?): String? =
    images?.arr("items")?.firstOrNull()?.jsonObject?.arr("sources")
        ?.mapNotNull { source ->
            val url = source.jsonObject.str("url") ?: return@mapNotNull null
            url to (source.jsonObject.int("width") ?: 0)
        }
        ?.maxByOrNull { it.second }
        ?.first

private fun coverUrl(images: JsonObject?): String? = largestSource(images)

private fun JsonArray.lastUrl(): String? =
    mapNotNull { it.jsonObject.str("url") }.lastOrNull()

private fun JsonObject.obj(key: String): JsonObject? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonObject }.getOrNull() }

private fun JsonObject.arr(key: String): JsonArray? =
    this[key]?.takeIf { it !is JsonNull }?.let { runCatching { it.jsonArray }.getOrNull() }

private fun JsonObject.str(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull
