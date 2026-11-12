package com.darkxvenom.itune.data.spotify

import android.util.Log
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class PlaylistImportTrack(val title: String, val artist: String)

data class PlaylistImportResult(
    val title: String,
    val tracks: List<MusicTrack>,
    val unmatched: List<PlaylistImportTrack>,
)

object PlaylistLinkImporter {
    private const val TAG = "PlaylistLinkImporter"
    private const val PAGE_SIZE = 100
    private const val PLAYLIST_QUERY_HASH = "19ff1327c29e99c208c86d7a9d8f1929cfdf3d3202a0ff4253c821f1901aa94d"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val playlistIdPattern = Regex("[A-Za-z0-9]{22}")
    private val urlRegex = Regex("""https?://[^\s<>"]+""")

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("spotify:playlist:")) {
            val id = trimmed.removePrefix("spotify:playlist:")
                .substringBefore("?").substringBefore("/")
            return id.takeIf(playlistIdPattern::matches)
        }

        val candidateUrl = urlRegex.find(trimmed)?.value ?: trimmed
        val uri = runCatching { URI(candidateUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (host != "spotify.com" && !host.endsWith(".spotify.com") && !host.contains("spotify")) return null
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val index = parts.indexOf("playlist")
        if (index >= 0 && index + 1 < parts.size) {
            val segment = parts[index + 1].substringBefore("?").substringBefore("/")
            if (playlistIdPattern.matches(segment)) return segment
        }
        return null
    }

    suspend fun importPlaylist(
        playlistIdOrUrl: String,
        language: String? = null,
        onProgress: suspend (completed: Int, total: Int) -> Unit,
    ): PlaylistImportResult {
        val playlistId = extractPlaylistId(playlistIdOrUrl)
            ?: playlistIdOrUrl.trim().takeIf(playlistIdPattern::matches)
            ?: error("Invalid Spotify playlist link or ID")

        val (title, sourceTracks) = fetchPublicPlaylist(playlistId)
        val matched = mutableListOf<MusicTrack>()
        val unmatched = mutableListOf<PlaylistImportTrack>()
        val semaphore = Semaphore(4)
        val completedCount = AtomicInteger(0)

        val cleanLang = language?.trim()?.takeIf {
            !it.equals("No Preference", ignoreCase = true) &&
                !it.equals("Multilingual", ignoreCase = true) &&
                it.isNotBlank()
        }

        val results = coroutineScope {
            sourceTracks.map { source ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val query = if (cleanLang != null) {
                            listOf(source.title, source.artist, cleanLang)
                                .filter(String::isNotBlank).joinToString(" ")
                        } else {
                            listOf(source.title, source.artist)
                                .filter(String::isNotBlank).joinToString(" ")
                        }
                        val candidates = if (query.isBlank()) emptyList() else {
                            runCatching { MusicBackend.searchMusic(query) }.getOrDefault(emptyList())
                        }
                        val best = candidates
                            .distinctBy { it.id }
                            .map { it to matchScore(source, it) }
                            .maxByOrNull { it.second }
                            ?.takeIf { it.second >= 0.40f }
                            ?.first

                        val done = completedCount.incrementAndGet()
                        onProgress(done, sourceTracks.size)
                        best
                    }
                }
            }.mapIndexed { index, deferred ->
                val track = deferred.await()
                if (track != null) {
                    matched += track
                } else {
                    unmatched += sourceTracks[index]
                }
            }
        }

        return PlaylistImportResult(title, matched.distinctBy { it.id }, unmatched)
    }

    private suspend fun fetchPublicPlaylist(id: String): Pair<String, List<PlaylistImportTrack>> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/playlist/$id")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Playlist request failed (HTTP ${response.code})")
                response.body?.string()?.takeIf(String::isNotBlank)
                    ?: error("The Spotify playlist page was empty")
            }

            val script = Regex(
                """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ).find(html)?.groupValues?.getOrNull(1)
                ?: error("Could not read playlist data. Check that the playlist is public.")

            val root = JSONObject(script)
            val props = root.optJSONObject("props")?.optJSONObject("pageProps")
            val state = props?.optJSONObject("state")
            val entity = state?.optJSONObject("data")?.optJSONObject("entity")
                ?: props?.optJSONObject("entity")
                ?: error("Playlist data is unavailable or private")

            val title = entity.optString("name").takeIf(String::isNotBlank)
                ?: entity.optString("title").takeIf(String::isNotBlank)
                ?: "Imported Spotify Playlist"

            val list = entity.optJSONArray("trackList") ?: entity.optJSONArray("tracks")
                ?: JSONArray()

            val tracks = buildList {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val name = item.optString("title").ifBlank { item.optString("name") }.trim()
                    if (name.isBlank()) continue
                    val artists = item.optJSONArray("artists")
                    val artist = item.optString("subtitle").ifBlank {
                        artists?.joinJsonNames().orEmpty()
                    }.trim()
                    add(PlaylistImportTrack(name, artist))
                }
            }.toMutableList()

            if (tracks.isEmpty()) error("No tracks were found. The playlist may be private or empty.")

            // Spotify's public embed contains up to 100 tracks.
            // Page through the remaining entries if session token is exposed.
            if (tracks.size >= PAGE_SIZE) {
                val token = state?.optJSONObject("settings")
                    ?.optJSONObject("session")
                    ?.optString("accessToken")
                    ?.takeIf(String::isNotBlank)
                if (token != null) {
                    val remaining = runCatching { fetchRemainingTracks(id, token, tracks.size) }
                        .getOrNull()
                    if (remaining != null) {
                        tracks.addAll(remaining)
                    }
                }
            }
            title to tracks
        }

    private fun fetchRemainingTracks(
        playlistId: String,
        token: String,
        startOffset: Int,
    ): List<PlaylistImportTrack> {
        val output = mutableListOf<PlaylistImportTrack>()
        var offset = startOffset
        while (true) {
            val variables = """{"uri":"spotify:playlist:$playlistId","offset":$offset,"limit":$PAGE_SIZE,"enableWatchFeedEntrypoint":false}"""
            val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$PLAYLIST_QUERY_HASH"}}"""
            val url = "https://api-partner.spotify.com/pathfinder/v1/query".toHttpUrl()
                .newBuilder()
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
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val content = JSONObject(body).optJSONObject("data")
                ?.optJSONObject("playlistV2")
                ?.optJSONObject("content") ?: break
            val items = content.optJSONArray("items") ?: break
            for (index in 0 until items.length()) {
                val data = items.optJSONObject(index)
                    ?.optJSONObject("itemV2")
                    ?.optJSONObject("data") ?: continue
                val name = data.optString("name").trim()
                if (name.isBlank()) continue
                val artistItems = data.optJSONObject("artists")?.optJSONArray("items")
                val artists = buildList {
                    for (artistIndex in 0 until (artistItems?.length() ?: 0)) {
                        val artistName = artistItems?.optJSONObject(artistIndex)
                            ?.optJSONObject("profile")?.optString("name").orEmpty()
                        if (artistName.isNotBlank()) add(artistName)
                    }
                }.joinToString(", ")
                output += PlaylistImportTrack(name, artists)
            }
            val total = content.optInt("totalCount", 0)
            offset += items.length()
            if (items.length() == 0 || offset >= total) break
        }
        return output
    }

    private fun JSONArray.joinJsonNames(): String = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index)
            val name = item?.optString("name").orEmpty()
            if (name.isNotBlank()) add(name)
        }
    }.joinToString(", ")

    private fun matchScore(source: PlaylistImportTrack, candidate: MusicTrack): Float {
        val sourceTitle = normalize(source.title)
        val candidateTitle = normalize(candidate.title)
        val sourceArtist = normalize(source.artist)
        val candidateArtist = normalize(candidate.artists)
        if (sourceTitle.isBlank() || candidateTitle.isBlank()) return 0f

        val titleScore = when {
            sourceTitle == candidateTitle -> 1f
            sourceTitle.startsWith(candidateTitle) || candidateTitle.startsWith(sourceTitle) -> 0.86f
            else -> tokenSimilarity(sourceTitle, candidateTitle)
        }
        val artistScore = when {
            sourceArtist.isBlank() -> 0.45f
            candidateArtist.contains(sourceArtist) || sourceArtist.contains(candidateArtist) -> 1f
            else -> tokenSimilarity(sourceArtist, candidateArtist)
        }
        return titleScore * 0.78f + artistScore * 0.22f
    }

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun tokenSimilarity(a: String, b: String): Float {
        val left = a.split(' ').filter(String::isNotBlank).toSet()
        val right = b.split(' ').filter(String::isNotBlank).toSet()
        if (left.isEmpty() || right.isEmpty()) return 0f
        return left.intersect(right).size.toFloat() / left.union(right).size.toFloat()
    }
}
