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
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.artist.CanvasArtwork
import com.darkxvenom.itune.data.artist.CanvasSource
import com.darkxvenom.itune.data.artist.artistsCompatible
import com.darkxvenom.itune.data.artist.normalizeForMatch
import com.darkxvenom.itune.data.artist.titlesCompatible
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/**
 * Spotify's own Canvas — the looping 9:16 vertical video clip for tracks.
 *
 * Asks via Pathfinder v2 `canvas` GraphQL query ([SpotifyCanvasQuery]),
 * authenticated with a bearer token minted from the listener's own session
 * ([SpotifyToken]). The older protobuf endpoint ([CANVAS_URL]) is kept as a
 * fallback when Pathfinder fails or returns a non-MP4 shape.
 */
object SpotifyCanvas {

    private const val TAG = "SpotifyCanvas"
    private const val SEARCH_URL = "https://api.spotify.com/v1/search"
    private const val ALBUM_TRACKS_URL = "https://api.spotify.com/v1/albums"
    private const val CANVAS_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"

    /** The persisted query id for the web player's own track search. */
    private const val PATHFINDER_SEARCH_HASH =
        "bc1ca2fcd0ba1013a0fc88e6cc4f190af501851e3dafd3e1ef85840297694428"

    /**
     * spclient gates this path to Spotify's own apps by user agent; the web
     * player's own UA is turned away, so this wears a mobile client's instead.
     */
    private const val SPOTIFY_APP_UA = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val CANVAS_URL_REGEX = Regex("""https://[^"'\s\x00-\x1F]+\.cnvs\.mp4""")

    private data class TrackHit(val uri: String, val title: String, val artist: String, val album: String?)

    private const val MAX_CANVAS_CANDIDATES = 5

    /**
     * Canvas for a track whose Spotify id is already known (e.g. from a Spotify playlist): skips the
     * fuzzy search and asks for the clip directly, so it is faster and can't match the wrong song.
     */
    suspend fun forTrackId(trackId: String, title: String, artist: String, album: String?): CanvasArtwork? {
        val token = SpotifyToken.accessToken() ?: return null
        val url = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { fetchCanvasUrl("spotify:track:$trackId", token) }.getOrNull()
        } ?: return null
        return CanvasArtwork(url = url, title = title, artist = artist, album = album, source = CanvasSource.SPOTIFY)
    }

    suspend fun search(title: String, artist: String, album: String?): CanvasArtwork? {
        val token = SpotifyToken.accessToken()
        if (token == null) {
            Log.d(TAG, "no access token (cookie unset or mint failed); skipping")
            return null
        }

        val candidates = linkedMapOf<String, TrackHit>()
        // Try clean "$title $artist" first AND "$title $artist $album" so a single/compilation
        // album tag from YTM doesn't hide the canonical album track URI that carries Canvas.
        val queries = buildList {
            if (!album.isNullOrBlank()) add(listOf(title, artist, album).joinToString(" "))
            add(listOf(title, artist).joinToString(" "))
        }.distinct()

        for (query in queries) {
            searchViaPathfinder(query, title, artist, album, token).forEach { hit ->
                candidates.putIfAbsent(hit.uri, hit)
            }
            if (candidates.size >= MAX_CANVAS_CANDIDATES) break
        }
        if (candidates.isEmpty()) {
            for (query in queries) {
                searchViaRest(query, title, artist, token).forEach { hit ->
                    candidates.putIfAbsent(hit.uri, hit)
                }
                if (candidates.size >= MAX_CANVAS_CANDIDATES) break
            }
        }

        if (candidates.isEmpty()) {
            Log.d(TAG, "no canvas for '$title' by '$artist' (no matching track found)")
            return null
        }

        val ordered = candidates.values.take(MAX_CANVAS_CANDIDATES)
        for (hit in ordered) {
            val canvasUrl = fetchCanvasUrl(hit.uri, token)
            if (canvasUrl != null) {
                Log.d(TAG, "canvas for '${hit.title}' by '${hit.artist}' (${hit.uri})")
                return CanvasArtwork(
                    url = canvasUrl,
                    title = hit.title,
                    artist = hit.artist,
                    album = hit.album,
                    source = CanvasSource.SPOTIFY,
                )
            }
        }
        Log.d(TAG, "no canvas for '$title' by '$artist' (checked ${ordered.size} candidate URI(s), none had a clip)")
        return null
    }

    private fun searchViaPathfinder(
        searchTerm: String,
        title: String,
        artist: String,
        album: String?,
        token: String,
    ): List<TrackHit> {
        val clientToken = SpotifyToken.clientToken()
        if (clientToken == null) {
            Log.d(TAG, "no client token; skipping pathfinder search")
            return emptyList()
        }
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
                put("sha256Hash", PATHFINDER_SEARCH_HASH)
            }
        }.toString()

        val url = PATHFINDER_URL.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "searchTracks")
            .addQueryParameter("variables", variables)
            .addQueryParameter("extensions", extensions)
            .build()
            .toString()

        val headers = mapOf(
            "Authorization" to "Bearer $token",
            "Client-Token" to clientToken,
            "App-platform" to "WebPlayer",
            "Accept" to "application/json",
            "User-Agent" to CANVAS_UA,
        )
        val (code, body) = canvasGetWithStatus(url, headers)
        if (body == null) {
            Log.w(TAG, "pathfinder search failed, http $code")
            return emptyList()
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val items = root?.get("data")?.jsonObject
            ?.get("searchV2")?.jsonObject
            ?.get("tracksV2")?.jsonObject
            ?.get("items")?.jsonArray
        if (items == null || items.isEmpty()) {
            Log.w(TAG, "pathfinder response had no hits (http $code): ${body.take(200)}")
            return emptyList()
        }

        val matched = mutableListOf<TrackHit>()
        var fallbackFirst: TrackHit? = null
        for (entry in items) {
            val data = (entry as? JsonObject)
                ?.get("item")?.jsonObject
                ?.get("data")?.jsonObject
                ?: continue
            val uri = data["uri"]?.jsonPrimitive?.contentOrNull
                ?: data["id"]?.jsonPrimitive?.contentOrNull?.let { "spotify:track:$it" }
                ?: continue
            val hitTitle = data["name"]?.jsonPrimitive?.contentOrNull
            val hitArtists = extractPathfinderArtists(data)
            val hitAlbum = data["albumOfTrack"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull

            if (hitTitle.isNullOrBlank() || hitArtists.isEmpty()) {
                if (fallbackFirst == null) {
                    fallbackFirst = TrackHit(uri, hitTitle ?: title, hitArtists.joinToString(", ").ifBlank { artist }, hitAlbum ?: album)
                }
                continue
            }
            if (!isMatch(hitTitle, hitArtists, title, artist)) continue
            matched += TrackHit(
                uri = uri,
                title = hitTitle,
                artist = hitArtists.joinToString(", ").ifBlank { artist },
                album = hitAlbum,
            )
        }
        return if (matched.isNotEmpty()) matched else listOfNotNull(fallbackFirst)
    }

    private fun extractPathfinderArtists(data: JsonObject): List<String> {
        val artistsEl = data["artists"] ?: return emptyList()
        val itemsArr = (artistsEl as? JsonObject)?.get("items")?.jsonArray
        if (itemsArr != null) {
            return itemsArr.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                obj["profile"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
            }
        }
        return runCatching {
            artistsEl.jsonArray.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
        }.getOrDefault(emptyList())
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    private fun searchViaRest(query: String, title: String, artist: String, token: String): List<TrackHit> {
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "10")
            .build()
            .toString()

        val (code, body) = canvasGetWithStatus(url, authHeaders(token))
        if (body == null) {
            Log.w(TAG, "search request failed, http $code")
            return emptyList()
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (root == null) {
            Log.w(TAG, "search response wasn't JSON (http $code)")
            return emptyList()
        }
        val items = root["tracks"]?.jsonObject?.get("items")?.jsonArray
        if (items == null) {
            Log.w(TAG, "search response had no tracks.items (http $code): ${body.take(200)}")
            return emptyList()
        }

        val hits = mutableListOf<TrackHit>()
        for (item in items) {
            val track = item as? JsonObject ?: continue
            val trackTitle = track["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val artists = track["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()
            if (!isMatch(trackTitle, artists, title, artist)) continue

            val uri = track["uri"]?.jsonPrimitive?.contentOrNull ?: continue
            val albumName = track["album"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            hits += TrackHit(uri, trackTitle, artists.joinToString(", ").ifBlank { artist }, albumName)
        }
        return hits
    }

    /**
     * A release's canvas, read off its first track — Spotify hangs Canvas off
     * individual tracks, not the release itself.
     */
    suspend fun searchAlbum(album: String, artist: String): CanvasArtwork? {
        val token = SpotifyToken.accessToken() ?: return null
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", "$album $artist")
            .addQueryParameter("type", "album")
            .addQueryParameter("limit", "10")
            .build()
            .toString()

        val body = canvasGet(url, authHeaders(token)) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val items = root["albums"]?.jsonObject?.get("items")?.jsonArray ?: return null

        for (item in items) {
            val record = item as? JsonObject ?: continue
            val recordTitle = record["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val artists = record["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()
            if (!isMatch(recordTitle, artists, album, artist)) continue

            val albumId = record["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val trackUri = firstTrackUri(albumId, token) ?: continue
            val canvasUrl = fetchCanvasUrl(trackUri, token) ?: continue

            Log.d(TAG, "canvas for album '$recordTitle' by ${artists.joinToString()}")
            return CanvasArtwork(
                url = canvasUrl,
                title = recordTitle,
                artist = artists.joinToString(", ").ifBlank { null },
                album = recordTitle,
                source = CanvasSource.SPOTIFY,
            )
        }
        return null
    }

    private fun firstTrackUri(albumId: String, token: String): String? {
        val url = "$ALBUM_TRACKS_URL/$albumId/tracks".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "1")
            .build()
            .toString()
        val body = canvasGet(url, authHeaders(token)) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return root["items"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("uri")?.jsonPrimitive?.contentOrNull
    }

    private fun authHeaders(token: String): Map<String, String> {
        val headers = mutableMapOf("Authorization" to "Bearer $token", "User-Agent" to CANVAS_UA)
        SpotifyToken.clientToken()?.let { headers["Client-Token"] = it }
        return headers
    }

    private fun isMatch(gotName: String, gotArtists: List<String>, wantName: String, wantArtist: String): Boolean {
        if (!titlesCompatible(gotName, wantName)) return false
        return artistsCompatible(
            gotArtistsRaw = gotArtists.joinToString(", "),
            gotTitleRaw = gotName,
            wantArtistsRaw = wantArtist,
            wantTitleRaw = wantName,
        )
    }

    // ---- Pathfinder: the `canvas` GraphQL query ------------------------

    private val queryHashes = SpotifyCanvasQuery.QueryHashes(
        fetch = { url -> canvasGet(url, mapOf("User-Agent" to CANVAS_UA)) },
    )

    private fun fetchCanvasUrl(trackUri: String, token: String): String? =
        when (val answer = fetchCanvasViaPathfinder(trackUri, token)) {
            is SpotifyCanvasQuery.Answer.Found -> {
                Log.d(TAG, "pathfinder canvas (${answer.type ?: "no type"}) for $trackUri")
                answer.url
            }
            is SpotifyCanvasQuery.Answer.NoCanvas -> {
                Log.d(TAG, "pathfinder: no playable canvas for $trackUri (${answer.detail}); trying canvaz-cache")
                fetchCanvasViaCanvaz(trackUri, token)
            }
            is SpotifyCanvasQuery.Answer.Failed -> {
                Log.w(TAG, "pathfinder canvas query failed (${answer.reason}); trying canvaz-cache")
                fetchCanvasViaCanvaz(trackUri, token)
            }
        }

    private fun fetchCanvasViaPathfinder(trackUri: String, token: String, isRetry: Boolean = false): SpotifyCanvasQuery.Answer {
        val hash = queryHashes.canvasHash(forceRefresh = isRetry)
        val request = Request.Builder()
            .url(SpotifyCanvasQuery.ENDPOINT)
            .post(SpotifyCanvasQuery.requestBody(trackUri, hash).toRequestBody("application/json".toMediaType()))
            .apply { authHeaders(token).forEach { (name, value) -> header(name, value) } }
            .header("App-platform", "WebPlayer")
            .header("Accept", "application/json")
            .header("Accept-Language", "en")
            .build()
        val (code, body) = runCatching {
            Net.client.newCall(request).execute().use { response ->
                response.code to if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrElse { return SpotifyCanvasQuery.Answer.Failed("request threw: ${it.message}") }
        if (body == null) return SpotifyCanvasQuery.Answer.Failed("http $code")

        val answer = SpotifyCanvasQuery.parse(body)
        if (answer is SpotifyCanvasQuery.Answer.Failed && answer.staleHash && !isRetry) {
            return fetchCanvasViaPathfinder(trackUri, token, isRetry = true)
        }
        return answer
    }

    // ---- canvaz-cache: protobuf request/response (fallback) ------------

    private data class CanvasHit(val id: String?, val url: String, val trackUri: String?)

    private fun fetchCanvasViaCanvaz(trackUri: String, token: String): String? {
        val requestBody = encodeCanvasRequest(trackUri)
            .toRequestBody("application/protobuf".toMediaType())
        val request = Request.Builder()
            .url(CANVAS_URL)
            .post(requestBody)
            .apply { authHeaders(token).forEach { (name, value) -> header(name, value) } }
            .header("Accept", "application/protobuf")
            .header("Accept-Language", "en")
            .header("User-Agent", SPOTIFY_APP_UA)
            .build()

        val bytes = runCatching {
            Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "canvaz-cache http ${response.code} for $trackUri")
                }
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.onFailure { Log.w(TAG, "canvaz-cache request threw: ${it.message}") }.getOrNull() ?: return null

        val hits = decodeCanvasResponse(bytes)
        val structured = hits.firstOrNull { it.trackUri == trackUri }?.url ?: hits.firstOrNull()?.url
        if (structured != null) return structured

        val fallback = CANVAS_URL_REGEX.find(String(bytes, Charsets.ISO_8859_1))?.value
        if (fallback == null) {
            Log.d(TAG, "canvaz-cache had nothing for $trackUri")
        } else {
            Log.d(TAG, "canvaz-cache url recovered by regex fallback for $trackUri")
        }
        return fallback
    }

    /** `CanvasRequest { repeated Track tracks = 1; Track { string track_uri = 1; } }` */
    private fun encodeCanvasRequest(trackUri: String): ByteArray {
        val track = ByteArrayOutputStream().let { buffer ->
            val out = CodedOutputStream.newInstance(buffer)
            out.writeString(1, trackUri)
            out.flush()
            buffer.toByteArray()
        }
        val request = ByteArrayOutputStream()
        val out = CodedOutputStream.newInstance(request)
        out.writeByteArray(1, track)
        out.flush()
        return request.toByteArray()
    }

    /** `CanvasResponse { repeated Canvas canvases = 1; }`, `Canvas { id = 1; canvas_url = 2; track_uri = 5; }` */
    private fun decodeCanvasResponse(bytes: ByteArray): List<CanvasHit> = runCatching {
        val hits = mutableListOf<CanvasHit>()
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) break
            if (tag ushr 3 == 1) {
                decodeCanvas(input.readByteArray())?.let(hits::add)
            } else {
                input.skipField(tag)
            }
        }
        hits
    }.getOrElse { emptyList() }

    private fun decodeCanvas(bytes: ByteArray): CanvasHit? = runCatching {
        var id: String? = null
        var url: String? = null
        var trackUri: String? = null
        val input = CodedInputStream.newInstance(bytes)
        while (!input.isAtEnd) {
            val tag = input.readTag()
            if (tag == 0) break
            when (tag ushr 3) {
                1 -> id = input.readString()
                2 -> url = input.readString()
                5 -> trackUri = input.readString()
                else -> input.skipField(tag)
            }
        }
        url?.let { CanvasHit(id, it, trackUri) }
    }.getOrNull()
}
