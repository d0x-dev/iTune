package com.darkxvenom.itune.data.charts

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AppleMusicChartsScraper {
    private const val TAG = "AppleMusicChartsScraper"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun executeGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) return body
                } else {
                    Log.w(TAG, "GET failed: HTTP ${response.code} for $url")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing GET request for $url: ${e.message}")
        }

        return try {
            com.darkxvenom.itune.backend.Net.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in Net.client fallback for $url: ${e.message}")
            null
        }
    }

    suspend fun fetchTopSongs(countryCode: String = "us"): List<ChartTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<ChartTrack>()
        try {
            val code = countryCode.lowercase().ifBlank { "us" }
            val url = "https://rss.marketingtools.apple.com/api/v2/$code/music/most-played/100/songs.json"
            val response = executeGet(url) ?: return@withContext tracks

            val json = JSONObject(response)
            val results = json.getJSONObject("feed").getJSONArray("results")

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val rank = i + 1
                val title = item.optString("name")
                val artist = item.optString("artistName")
                val rawArtwork = item.optString("artworkUrl100")
                val artwork = rawArtwork.replace(Regex("(\\d+)x(\\d+)"), "1400x1400")
                val appleUrl = if (item.has("url")) item.optString("url") else null

                if (title.isNotBlank()) {
                    tracks.add(
                        ChartTrack(
                            rank = rank,
                            title = title,
                            artist = artist,
                            thumbnailUrl = artwork,
                            appleMusicUrl = appleUrl,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Apple Music Top Songs for $countryCode: ${e.message}")
        }
        tracks
    }

    suspend fun fetchTopAlbums(countryCode: String = "us"): List<ChartAlbum> = withContext(Dispatchers.IO) {
        val albums = mutableListOf<ChartAlbum>()
        try {
            val code = countryCode.lowercase().ifBlank { "us" }
            val url = "https://rss.marketingtools.apple.com/api/v2/$code/music/most-played/20/albums.json"
            val response = executeGet(url) ?: return@withContext albums

            val json = JSONObject(response)
            val results = json.getJSONObject("feed").getJSONArray("results")

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val rank = i + 1
                val title = item.optString("name")
                val artist = item.optString("artistName")
                val rawArtwork = item.optString("artworkUrl100")
                val artwork = rawArtwork.replace(Regex("(\\d+)x(\\d+)"), "1400x1400")
                val appleUrl = if (item.has("url")) item.optString("url") else null

                if (title.isNotBlank()) {
                    albums.add(
                        ChartAlbum(
                            rank = rank,
                            title = title,
                            artist = artist,
                            thumbnailUrl = artwork,
                            appleMusicUrl = appleUrl,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Apple Music Top Albums for $countryCode: ${e.message}")
        }
        albums
    }

    suspend fun fetchTopVideos(countryCode: String = "us"): List<ChartTrack> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<ChartTrack>()
        try {
            val code = countryCode.lowercase().ifBlank { "us" }
            val url = "https://rss.marketingtools.apple.com/api/v2/$code/music/most-played/20/music-videos.json"
            val response = executeGet(url) ?: return@withContext videos

            val json = JSONObject(response)
            val results = json.getJSONObject("feed").getJSONArray("results")

            val videoMap = mutableMapOf<String, ChartTrack>()

            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val id = item.optString("id", i.toString())
                val rank = i + 1
                val title = item.optString("name")
                val artist = item.optString("artistName")
                val rawArtwork = item.optString("artworkUrl100")
                val artwork = rawArtwork.replace(Regex("(\\d+)x(\\d+)"), "1920x1080")
                val appleUrl = if (item.has("url")) item.optString("url") else null

                if (title.isNotBlank()) {
                    videoMap[id] = ChartTrack(
                        rank = rank,
                        title = title,
                        artist = artist,
                        thumbnailUrl = artwork,
                        appleMusicUrl = appleUrl
                    )
                }
            }

            videos.addAll(videoMap.values.sortedBy { it.rank })
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Apple Music Top Videos for $countryCode: ${e.message}")
        }
        videos
    }

    fun getTrendingArtists(tracks: List<ChartTrack>): List<ChartArtist> {
        val artistCounts = mutableMapOf<String, Int>()
        val artistImages = mutableMapOf<String, String?>()

        tracks.forEach { track ->
            val mainArtist = track.artist.split(",", "&", "feat.", "ft.").firstOrNull()?.trim().orEmpty()
            if (mainArtist.isNotEmpty()) {
                artistCounts[mainArtist] = (artistCounts[mainArtist] ?: 0) + 1
                if (artistImages[mainArtist] == null) {
                    artistImages[mainArtist] = track.thumbnailUrl?.replace("1400x1400", "500x500")
                }
            }
        }

        return artistCounts
            .toList()
            .sortedByDescending { it.second }
            .take(15)
            .mapIndexed { index, pair ->
                ChartArtist(
                    rank = index + 1,
                    name = pair.first,
                    thumbnailUrl = artistImages[pair.first]
                )
            }
    }
}
