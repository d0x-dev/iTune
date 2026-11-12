package com.darkxvenom.itune.data.spotify

import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PlaylistImportTrack(val title: String, val artist: String)

data class PlaylistImportResult(
    val title: String,
    val tracks: List<MusicTrack>,
    val unmatched: List<PlaylistImportTrack>,
)

object PlaylistLinkImporter {
    private val playlistIdPattern = Regex("[A-Za-z0-9]{22}")
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    fun extractPlaylistId(input: String): String? {
        val value = input.trim()
        if (value.startsWith("spotify:playlist:")) {
            return value.removePrefix("spotify:playlist:")
                .substringBefore("?").substringBefore("/")
                .takeIf(playlistIdPattern::matches)
        }
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        if (host != "spotify.com" && !host.endsWith(".spotify.com")) return null
        val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        val index = parts.indexOf("playlist")
        if (index < 0) return null
        return parts.getOrNull(index + 1)?.takeIf(playlistIdPattern::matches)
    }

    suspend fun importPlaylist(
        playlistId: String,
        onProgress: suspend (completed: Int, total: Int) -> Unit,
    ): PlaylistImportResult {
        val (title, sourceTracks) = fetchPublicPlaylist(playlistId)
        val matched = mutableListOf<MusicTrack>()
        val unmatched = mutableListOf<PlaylistImportTrack>()
        val semaphore = Semaphore(3)
        val results = coroutineScope {
            sourceTracks.map { source ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val query = listOf(source.title, source.artist)
                            .filter(String::isNotBlank).joinToString(" ")
                        val candidates = if (query.isBlank()) emptyList() else MusicBackend.searchMusic(query)
                        val best = candidates
                            .distinctBy { it.id }
                            .map { it to matchScore(source, it) }
                            .maxByOrNull { it.second }
                            ?.takeIf { it.second >= 0.42f }
                            ?.first
                        best
                    }
                }
            }.mapIndexed { index, deferred ->
                val track = deferred.await()
                onProgress(index + 1, sourceTracks.size)
                track
            }
        }
        results.forEachIndexed { index, track ->
            if (track == null) unmatched += sourceTracks[index] else matched += track
        }
        return PlaylistImportResult(title, matched.distinctBy { it.id }, unmatched)
    }

    private suspend fun fetchPublicPlaylist(id: String): Pair<String, List<PlaylistImportTrack>> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/playlist/$id")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            val html = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Playlist request failed (HTTP ${response.code})")
                response.body?.string()?.takeIf(String::isNotBlank)
                    ?: error("The playlist page was empty")
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
                ?: "Imported Playlist"
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
            }
            if (tracks.isEmpty()) error("No tracks were found. The playlist may be private or empty.")
            title to tracks
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
