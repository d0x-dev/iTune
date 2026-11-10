package com.darkxvenom.itune.backend.lyrics

import android.util.Base64
import android.util.Log
import com.darkxvenom.itune.backend.LyricsLine
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.backend.WordTiming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.math.abs

data class LyricsSearchResult(
    val provider: String,
    val trackName: String,
    val artistName: String,
    val durationSec: Int,
    val syncedLyrics: String?,
    val plainLyrics: String?,
) {
    val isSynced: Boolean get() = !syncedLyrics.isNullOrBlank()
    val rawText: String get() = syncedLyrics?.takeIf { it.isNotBlank() } ?: plainLyrics.orEmpty()
    val preview: String
        get() = rawText.lines()
            .map { it.replace(Regex("^\\[\\d{1,3}:\\d{2}(?:\\.\\d{1,3})?\\]\\s*"), "").trim() }
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(" · ")
}

data class LoadedLyrics(
    val lines: List<LyricsLine>,
    val rawText: String,
)

interface LyricsProvider {
    val name: String
    suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String>
}

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val matches = searchMatches(title = title, artist = artist)
            var bestSynced: String? = null
            var bestPlain: String? = null
            var minDiff = Int.MAX_VALUE

            for (item in matches) {
                val synced = item.syncedLyrics
                if (!synced.isNullOrBlank()) {
                    if (durationSec > 0 && item.durationSec > 0) {
                        val diff = abs(item.durationSec - durationSec)
                        if (diff < minDiff) {
                            minDiff = diff
                            bestSynced = synced
                        }
                    } else if (bestSynced == null) {
                        bestSynced = synced
                    }
                } else if (bestPlain == null && !item.plainLyrics.isNullOrBlank()) {
                    bestPlain = item.plainLyrics
                }
            }

            bestSynced ?: bestPlain ?: throw IllegalStateException("No lyrics found on LrcLib")
        }
    }

    suspend fun searchMatches(title: String, artist: String): List<LyricsSearchResult> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isBlank() && cleanArtist.isBlank()) return@withContext emptyList()

        val primaryUrl = buildString {
            append("https://lrclib.net/api/search?track_name=")
            append(URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name()))
            if (cleanArtist.isNotBlank()) {
                append("&artist_name=")
                append(URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8.name()))
            }
        }
        val firstBatch = runCatching { queryEndpoint(primaryUrl) }.getOrDefault(emptyList())
        if (firstBatch.isNotEmpty()) return@withContext firstBatch

        val fallbackQuery = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")
        val fallbackUrl = "https://lrclib.net/api/search?q=${URLEncoder.encode(fallbackQuery, StandardCharsets.UTF_8.name())}"
        val secondBatch = runCatching { queryEndpoint(fallbackUrl) }.getOrDefault(emptyList())
        if (secondBatch.isNotEmpty()) return@withContext secondBatch

        // Strip common remix/version tags like "(Lofi)" or "[Slowed]" when looking up original lyrics
        val strippedTitle = cleanTitle
            .replace(Regex("\\s*[(\\[].*?[)\\]]"), "")
            .substringBefore("|")
            .trim()
        if (strippedTitle.isNotBlank() && strippedTitle != cleanTitle) {
            val cleanUrl = "https://lrclib.net/api/search?q=${URLEncoder.encode(strippedTitle, StandardCharsets.UTF_8.name())}"
            return@withContext runCatching { queryEndpoint(cleanUrl) }.getOrDefault(emptyList())
        }
        emptyList()
    }

    private fun queryEndpoint(url: String): List<LyricsSearchResult> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "iTune/1.0")
            .header("Accept", "application/json")
            .build()

        Net.client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("LrcLib error: ${response.code}")
            val array = JSONArray(response.body?.string().orEmpty())
            val out = mutableListOf<LyricsSearchResult>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val synced = obj.optNonNullString("syncedLyrics").ifBlank { null }
                val plain = obj.optNonNullString("plainLyrics").ifBlank { null }
                if (synced == null && plain == null) continue
                out.add(
                    LyricsSearchResult(
                        provider = name,
                        trackName = obj.optNonNullString("trackName"),
                        artistName = obj.optNonNullString("artistName"),
                        durationSec = obj.optInt("duration", 0).coerceAtLeast(0),
                        syncedLyrics = synced,
                        plainLyrics = plain
                    )
                )
            }
            return out
        }
    }
}

object KuGouLyricsProvider : LyricsProvider {
    override val name = "KuGou"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            searchCandidates(title = title, artist = artist, limit = 1).firstOrNull()?.syncedLyrics
                ?: throw IllegalStateException("No candidates on KuGou")
        }
    }

    suspend fun searchCandidates(title: String, artist: String, limit: Int = 4): List<LyricsSearchResult> = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        val rawKeyword = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" - ")
        if (rawKeyword.isBlank()) return@withContext emptyList()

        runCatching {
            val query = URLEncoder.encode(rawKeyword, StandardCharsets.UTF_8.name())
            val searchUrl = "https://mobileservice.kugou.com/api/v3/search/song?version=9108&plat=0&pagesize=5&showtype=0&keyword=$query"
            val searchReq = Request.Builder().url(searchUrl).header("User-Agent", "KuGou/1.0").build()
            val songObj = Net.client.newCall(searchReq).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                json.optJSONObject("data")?.optJSONArray("info")?.optJSONObject(0)
            } ?: return@runCatching emptyList()

            val hash = songObj.optNonNullString("hash").takeIf { it.isNotBlank() } ?: return@runCatching emptyList()
            val songTitle = songObj.optNonNullString("songname").ifBlank { cleanTitle }
            val singerName = songObj.optNonNullString("singername").ifBlank { cleanArtist }
            val dur = songObj.optInt("duration", 0).coerceAtLeast(0)

            val lyricsSearchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$hash"
            val lyricsReq = Request.Builder().url(lyricsSearchUrl).header("User-Agent", "KuGou/1.0").build()
            val candidates = Net.client.newCall(lyricsReq).execute().use { response ->
                JSONObject(response.body?.string().orEmpty()).optJSONArray("candidates")
            } ?: return@runCatching emptyList()

            val results = mutableListOf<LyricsSearchResult>()
            val cap = candidates.length().coerceAtMost(limit)
            for (i in 0 until cap) {
                val cand = candidates.optJSONObject(i) ?: continue
                val candId = cand.optNonNullString("id")
                val accessKey = cand.optNonNullString("accesskey")
                if (candId.isBlank() || accessKey.isBlank()) continue

                val dlUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&id=$candId&accesskey=$accessKey"
                val dlReq = Request.Builder().url(dlUrl).header("User-Agent", "KuGou/1.0").build()
                val lrcText = runCatching {
                    Net.client.newCall(dlReq).execute().use { resp ->
                        val base64Content = JSONObject(resp.body?.string().orEmpty()).optNonNullString("content")
                        if (base64Content.isBlank()) "" else String(Base64.decode(base64Content, Base64.DEFAULT), StandardCharsets.UTF_8)
                    }
                }.getOrDefault("")
                if (isReadableLrcText(lrcText)) {
                    results.add(
                        LyricsSearchResult(
                            provider = name,
                            trackName = cand.optNonNullString("song").ifBlank { songTitle },
                            artistName = cand.optNonNullString("singer").ifBlank { singerName },
                            durationSec = (cand.optInt("duration", dur * 1000) / 1000).coerceAtLeast(0),
                            syncedLyrics = lrcText,
                            plainLyrics = null
                        )
                    )
                }
            }
            results
        }.getOrDefault(emptyList())
    }

    private fun isReadableLrcText(raw: String): Boolean {
        if (raw.isBlank() || raw.startsWith("krc1")) return false
        val badChars = raw.count { it == '\uFFFD' || (it.code < 32 && it != '\n' && it != '\r' && it != '\t') }
        return badChars <= 2
    }
}

object LyricsHelper {
    private const val TAG = "LyricsHelper"
    private val memoryCache = ConcurrentHashMap<String, LoadedLyrics>()
    private val providers = listOf(
        LrcLibLyricsProvider,
        KuGouLyricsProvider
    )

    suspend fun loadLyrics(
        songId: String,
        title: String,
        artist: String,
        durationSec: Int = 0
    ): List<LyricsLine> = withContext(Dispatchers.IO) {
        memoryCache[songId]?.let { return@withContext it }

        var rawLrc: String? = null
        for (provider in providers) {
            val result = provider.getLyrics(songId, title, artist, durationSec)
            if (result.isSuccess) {
                val text = result.getOrNull()
                if (!text.isNullOrBlank()) {
                    rawLrc = text
                    Log.d(TAG, "Found lyrics for $title using ${provider.name}")
                    break
                }
            }
        }

        val parsed = if (!rawLrc.isNullOrBlank()) {
            parseLrc(rawLrc)
        } else {
            emptyList()
        }

        if (parsed.isNotEmpty()) {
            memoryCache[songId] = parsed
        }
        parsed
    }

    private fun unescapeHtml(text: String): String {
        if (!text.contains('&')) return text
        val cleaned = text
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        return try {
            if (cleaned.contains('&')) {
                android.text.Html.fromHtml(cleaned, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                cleaned
            }
        } catch (_: Throwable) {
            cleaned
        }
    }

    fun parseLrc(lrcText: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val linePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\](.*)")

        for (line in lrcText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val matcher = linePattern.matcher(trimmed)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3)
                val ms = when {
                    msStr == null -> 0L
                    msStr.length == 2 -> msStr.toLong() * 10
                    else -> msStr.take(3).toLong()
                }
                val timeMs = min * 60000L + sec * 1000L + ms
                val rawText = matcher.group(4)?.trim().orEmpty()
                val text = unescapeHtml(rawText)
                if (text.isNotBlank()) {
                    lines.add(LyricsLine(timeMs = timeMs, text = text))
                }
            }
        }

        val sorted = lines.sortedBy { it.timeMs }
        val enriched = mutableListOf<LyricsLine>()

        for (i in sorted.indices) {
            val cur = sorted[i]
            val nextTime = if (i + 1 < sorted.size) sorted[i + 1].timeMs else cur.timeMs + 4000L
            val lineDuration = (nextTime - cur.timeMs).coerceIn(1200L, 8000L)

            val rawWords = cur.text.split(" ").filter { it.isNotBlank() }
            val words = mutableListOf<WordTiming>()
            if (rawWords.isNotEmpty()) {
                val wordDuration = lineDuration / rawWords.size
                var wordStart = cur.timeMs
                for (w in rawWords) {
                    val wordEnd = wordStart + wordDuration
                    words.add(WordTiming(startMs = wordStart, endMs = wordEnd, text = w))
                    wordStart = wordEnd
                }
            }
            enriched.add(cur.copy(words = words))
        }

        return enriched
    }
}
