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
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.artist.normalizeForMatch
import com.darkxvenom.itune.data.artist.splitArtists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Single track metadata extracted from a Spotify playlist. */
data class SpotifyImportTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationSec: Long? = null,
)
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

object SpotifyImporter {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val PLAYLIST_ID = Regex("""[A-Za-z0-9]{22}""")

    private fun isSpotifyHost(host: String) =
        host == "spotify.com" || host.endsWith(".spotify.com")

    /** Parses a raw user string or link into a Spotify playlist ID if valid. */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("spotify:playlist:")) {
            val id = trimmed.removePrefix("spotify:playlist:").substringBefore("?").substringBefore("/")
            return id.takeIf { PLAYLIST_ID.matches(it) }
        }

        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (!isSpotifyHost(uri.host?.lowercase().orEmpty())) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val playlistIdx = segments.indexOf("playlist")
        if (playlistIdx == -1 || playlistIdx + 1 >= segments.size) return null
        return segments[playlistIdx + 1].takeIf { PLAYLIST_ID.matches(it) }
    }

    /** Fetches public Spotify playlist details from Spotify's embed endpoint (+ Pathfinder past 100 tracks). */
    suspend fun fetchPlaylistTracks(playlistId: String): Pair<String, List<SpotifyImportTrack>> =
        withContext(Dispatchers.IO) {
            val url = "https://open.spotify.com/embed/playlist/$playlistId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val html = Net.client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    error("Failed to load Spotify playlist (HTTP ${response.code})")
                }
                body
            }

            val scriptRegex = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            val match = scriptRegex.find(html)
                ?: error("Could not parse Spotify playlist metadata. Make sure the playlist is public.")

            val jsonString = match.groupValues[1]
            val root = json.parseToJsonElement(jsonString).jsonObject

            val props = root["props"]?.jsonObject?.get("pageProps")?.jsonObject
            val stateData = props?.get("state")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("entity")?.jsonObject
                ?: props?.get("entity")?.jsonObject
                ?: error("Spotify playlist entity missing or private")

            val playlistTitle = stateData["name"]?.jsonPrimitive?.content
                ?: stateData["title"]?.jsonPrimitive?.content
                ?: "Imported Spotify Playlist"

            val trackListJson = stateData["trackList"]?.jsonArray
                ?: stateData["tracks"]?.jsonArray
                ?: JsonArray(emptyList())

            val tracks = mutableListOf<SpotifyImportTrack>()
            for (element in trackListJson) {
                val obj = element.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content
                    ?: continue
                val subtitle = obj["subtitle"]?.jsonPrimitive?.content
                    ?: obj["artists"]?.jsonArray?.joinToString(", ") {
                        it.jsonObject["name"]?.jsonPrimitive?.content.orEmpty()
                    }
                    ?: ""
                val durationMs = obj["duration"]?.jsonPrimitive?.content?.toLongOrNull()
                if (title.isNotBlank()) {
                    tracks.add(
                        SpotifyImportTrack(
                            title = title.trim(),
                            artist = subtitle.trim(),
                            durationSec = durationMs?.takeIf { it > 0 }?.div(1000L),
                        ),
                    )
                }
            }

            if (tracks.isEmpty()) {
                error("No tracks found in public Spotify playlist.")
            }

            if (tracks.size >= EMBED_TRACK_LIMIT) {
                val token = props?.get("state")?.jsonObject
                    ?.get("settings")?.jsonObject
                    ?.get("session")?.jsonObject
                    ?.get("accessToken")?.jsonPrimitive?.content
                if (token != null) {
                    runCatching { fetchRemainingTracks(playlistId, token, tracks.size) }
                        .getOrNull()
                        ?.let { tracks.addAll(it) }
                }
            }

            Pair(playlistTitle, tracks)
        }

    /** Matches a Spotify track to the best YouTube Music [MusicTrack]. */
    suspend fun matchTrack(track: SpotifyTrack): MusicTrack? = withContext(Dispatchers.IO) {
        val query = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" ")
        val candidates = runCatching { MusicBackend.searchMusic(query) }.getOrDefault(emptyList())
        bestMatch(
            candidates = candidates,
            wantTitle = track.title,
            wantArtist = track.artist,
            wantAlbum = track.album,
            wantDurationSec = track.durationMs.takeIf { it > 0 }?.div(1000L),
        ) ?: candidates.firstOrNull()
    }

    private const val EMBED_TRACK_LIMIT = 100
    private const val PAGE_SIZE = 100
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val FETCH_PLAYLIST_HASH = "19ff1327c29e99c208c86d7a9d8f1929cfdf3d3202a0ff4253c821f1901aa94d"

    private fun fetchRemainingTracks(
        playlistId: String,
        token: String,
        start: Int,
    ): List<SpotifyImportTrack> {
        val out = mutableListOf<SpotifyImportTrack>()
        var offset = start
        while (true) {
            val variables = """{"uri":"spotify:playlist:$playlistId","offset":$offset,"limit":$PAGE_SIZE,"enableWatchFeedEntrypoint":false}"""
            val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$FETCH_PLAYLIST_HASH"}}"""
            val url = PATHFINDER_URL.toHttpUrl().newBuilder()
                .addQueryParameter("operationName", "fetchPlaylist")
                .addQueryParameter("variables", variables)
                .addQueryParameter("extensions", extensions)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("App-platform", "WebPlayer")
                .header("Accept", "application/json")
                .build()
            val body = Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val content = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
                ?.get("playlistV2")?.jsonObject?.get("content")?.jsonObject
                ?: break
            val items = content["items"]?.jsonArray ?: break
            for (item in items) {
                val data = item.jsonObject["itemV2"]?.jsonObject?.get("data")?.jsonObject ?: continue
                val title = data["name"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (title.isEmpty()) continue
                val artist = data["artists"]?.jsonObject?.get("items")?.jsonArray
                    ?.mapNotNull { it.jsonObject["profile"]?.jsonObject?.get("name")?.jsonPrimitive?.content }
                    ?.joinToString(", ")
                    .orEmpty()
                out.add(SpotifyImportTrack(title = title, artist = artist))
            }
            val total = content["totalCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: break
            offset += items.size
            if (items.isEmpty() || offset >= total) break
        }
        return out
    }

    /**
     * Resolves a list of Spotify tracks to full [MusicTrack] objects using iTune search.
     * Reports real-time progress via [onProgress].
     */
    suspend fun resolveToTracks(
        tracks: List<SpotifyImportTrack>,
        language: String? = null,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): Pair<List<MusicTrack>, List<SpotifyImportTrack>> = coroutineScope {
        val total = tracks.size
        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(4)

        val cleanLang = language?.trim()?.takeIf {
            !it.equals("No Preference", ignoreCase = true) &&
                !it.equals("Multilingual", ignoreCase = true) &&
                it.isNotBlank()
        }

        val knownLanguages = listOf(
            "Tamil", "Telugu", "Kannada", "Malayalam", "Hindi",
            "English", "Punjabi", "Marathi", "Bengali", "Bhojpuri", "Gujarati",
        )

        val deferredResults = tracks.map { track ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val query = if (cleanLang != null) {
                        "${track.title} ${track.artist} $cleanLang".trim()
                    } else {
                        "${track.title} ${track.artist}".trim()
                    }

                    val candidates = runCatching { MusicBackend.searchMusic(query) }.getOrDefault(emptyList())
                    val primary = bestMatch(
                        candidates = candidates,
                        wantTitle = track.title,
                        wantArtist = track.artist,
                        wantAlbum = track.album,
                        wantDurationSec = track.durationSec,
                    ) ?: candidates.firstOrNull()

                    val matched = if (cleanLang != null && candidates.isNotEmpty()) {
                        val conflicting = knownLanguages.filter { !it.equals(cleanLang, ignoreCase = true) }
                        val filtered = candidates.filter { c ->
                            val meta = "${c.title} ${c.artists} ${c.album.orEmpty()}".lowercase()
                            !conflicting.any {
                                meta.contains("(${it.lowercase()})") ||
                                    meta.contains("${it.lowercase()} version") ||
                                    meta.contains("${it.lowercase()} song")
                            }
                        }
                        bestMatch(filtered, track.title, track.artist, track.album, track.durationSec)
                            ?: filtered.firstOrNull()
                            ?: primary
                    } else {
                        primary
                    }

                    val done = completedCount.incrementAndGet()
                    onProgress(done, total)

                    if (matched != null) Pair(matched, null) else Pair(null, track)
                }
            }
        }

        val results = deferredResults.awaitAll()
        Pair(results.mapNotNull { it.first }, results.mapNotNull { it.second })
    }

    private fun bestMatch(
        candidates: List<MusicTrack>,
        wantTitle: String,
        wantArtist: String,
        wantAlbum: String?,
        wantDurationSec: Long?,
    ): MusicTrack? {
        if (candidates.isEmpty()) return null
        val normTitle = wantTitle.normalizeForMatch()
        val normArtists = splitArtists(wantArtist)
        val normAlbum = wantAlbum?.normalizeForMatch()

        return candidates.maxByOrNull { c ->
            var score = 0
            val cTitle = c.title.normalizeForMatch()
            score += when {
                cTitle == normTitle -> 40
                cTitle.contains(normTitle) || normTitle.contains(cTitle) -> 22
                else -> 0
            }
            val cArtists = splitArtists(c.artists)
            if (normArtists.isNotEmpty() && cArtists.isNotEmpty()) {
                val matched = normArtists.count { want -> cArtists.any { it == want || it.contains(want) } }
                score += matched * 20
            }
            if (!normAlbum.isNullOrBlank() && !c.album.isNullOrBlank()) {
                val cAlbum = c.album.normalizeForMatch()
                if (cAlbum == normAlbum) score += 12
                else if (cAlbum.contains(normAlbum) || normAlbum.contains(cAlbum)) score += 6
            }
            if (wantDurationSec != null && wantDurationSec > 0 && c.durationSec > 0) {
                val diff = abs(c.durationSec - wantDurationSec)
                score += when {
                    diff <= 3 -> 15
                    diff <= 10 -> 8
                    diff >= 35 -> -12
                    else -> 0
                }
            }
            if (!c.isVideo) score += 6
            score
        }
    }
}
