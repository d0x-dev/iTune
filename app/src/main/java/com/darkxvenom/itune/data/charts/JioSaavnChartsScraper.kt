package com.darkxvenom.itune.data.charts

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object JioSaavnChartsScraper {
    private const val TAG = "JioSaavnChartsScraper"
    private const val BASE_URL = "https://www.jiosaavn.com/api.php"
    private const val DES_KEY = "38346591"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val headers = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )
        .add("Accept", "application/json, text/plain, */*")
        .build()

    private val streamUrlCache = ConcurrentHashMap<String, String>()

    suspend fun fetchTrendingSongs(): List<ChartTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<ChartTrack>()
        try {
            // Primary: JioSaavn's official Trending Today editorial chart playlist (listid=110858205)
            val request = Request.Builder()
                .url("$BASE_URL?__call=playlist.getDetails&listid=110858205&_format=json&_marker=0&cc=in")
                .headers(headers)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!body.isNullOrBlank()) {
                val json = JSONObject(body)
                val songsArray = json.optJSONArray("songs")
                if (songsArray != null && songsArray.length() > 0) {
                    for (i in 0 until songsArray.length()) {
                        val item = songsArray.optJSONObject(i) ?: continue
                        parseSong(item, rank = i + 1)?.let { tracks.add(it) }
                    }
                }
            }

            // Fallback: If playlist was empty, query content.getTrending
            if (tracks.isEmpty()) {
                val fallbackReq = Request.Builder()
                    .url("$BASE_URL?__call=content.getTrending&_format=json&_marker=0&cc=in")
                    .headers(headers)
                    .build()
                val fallbackResp = client.newCall(fallbackReq).execute()
                val fallbackBody = fallbackResp.body?.string()
                if (!fallbackBody.isNullOrBlank()) {
                    val trendingArray = JSONArray(fallbackBody)
                    var currentRank = 1
                    for (i in 0 until trendingArray.length()) {
                        val entry = trendingArray.optJSONObject(i) ?: continue
                        if (entry.optString("type") == "song") {
                            val details = entry.optJSONObject("details") ?: continue
                            parseSong(details, rank = currentRank)?.let {
                                tracks.add(it)
                                currentRank++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching JioSaavn trending songs: ${e.message}")
        }
        tracks
    }

    suspend fun getStreamUrl(rawId: String): String? = withContext(Dispatchers.IO) {
        val mappedId = if (rawId.startsWith("JS:")) rawId else "JS:$rawId"
        streamUrlCache[mappedId]?.let { return@withContext it }
        val originalId = mappedId.removePrefix("JS:")

        try {
            val request = Request.Builder()
                .url("$BASE_URL?__call=song.getDetails&cc=in&_format=json&pids=$originalId")
                .headers(headers)
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val songObj = json.optJSONObject(originalId)
                ?: json.optJSONArray("songs")?.optJSONObject(0)
            if (songObj != null) {
                val encryptedUrl = songObj.optString("encrypted_media_url")
                if (encryptedUrl.isNotBlank()) {
                    val supports320 = songObj.optString("320kbps").equals("true", ignoreCase = true) ||
                            songObj.optBoolean("320kbps", false)
                    val streamUrl = decryptMediaUrl(encryptedUrl, supports320)
                    if (streamUrl != null) {
                        streamUrlCache[mappedId] = streamUrl
                        return@withContext streamUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving JioSaavn stream for $originalId: ${e.message}")
        }
        null
    }

    private fun decryptMediaUrl(encryptedUrl: String, supports320: Boolean = true): String? {
        if (encryptedUrl.isBlank()) return null
        return try {
            val keySpec = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decoded = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
            val decrypted = cipher.doFinal(decoded)
            val rawUrl = String(decrypted, Charsets.UTF_8).trim()
            val suffix = Regex("_(48|96|160|320)\\.(mp4|aac|mp3)$").find(rawUrl)
            if (suffix != null) {
                val ext = suffix.groupValues[2]
                if (supports320) {
                    rawUrl.replaceRange(suffix.range, "_320.$ext")
                } else {
                    rawUrl
                }
            } else {
                if (supports320) {
                    rawUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")
                } else {
                    rawUrl
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting media url: ${e.message}")
            null
        }
    }

    private fun parseSong(item: JSONObject, rank: Int): ChartTrack? {
        val rawId = item.optString("id").ifEmpty { return null }
        val id = "JS:$rawId"
        val rawName = item.optString("song").ifEmpty { item.optString("title").ifEmpty { item.optString("name") } }
        if (rawName.isEmpty()) return null
        val title = unescapeHtml(rawName)

        val artist = unescapeHtml(
            item.optString("primary_artists")
                .ifEmpty { item.optString("singers").ifEmpty { item.optString("music") } }
        ).ifEmpty { "JioSaavn Artist" }

        val rawImage = item.optString("image")
        val image = rawImage
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")

        val durationStr = item.optString("duration")
        val durationSec = durationStr.toLongOrNull() ?: item.optLong("duration", 0L)
        val isExplicit = item.optString("explicit_content") == "1"

        // Cache streamUrl early if encrypted url is available
        val encryptedUrl = item.optString("encrypted_media_url")
        var directUrl: String? = null
        if (encryptedUrl.isNotBlank()) {
            directUrl = decryptMediaUrl(encryptedUrl, supports320 = true)
            if (directUrl != null) {
                streamUrlCache[id] = directUrl
            }
        }

        return ChartTrack(
            rank = rank,
            title = title,
            artist = artist,
            thumbnailUrl = image,
            id = id,
            durationSec = durationSec,
            isExplicit = isExplicit,
            streamUrl = directUrl
        )
    }

    private fun unescapeHtml(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
    }
}
