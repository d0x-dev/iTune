package com.darkxvenom.itune.backend.lyrics

import android.util.Base64
import android.util.Log
import com.darkxvenom.itune.backend.LyricsLine
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.backend.WordTiming
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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

data class LyricsProviderInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val iconRes: Int,
)

interface LyricsProvider {
    val id: String
    val name: String
    suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String>
}

private val lyricsClient: OkHttpClient by lazy {
    Net.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
}

object LrcLibLyricsProvider : LyricsProvider {
    override val id = "lrclib"
    override val name = "LRC Lib"

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

        lyricsClient.newCall(req).execute().use { response ->
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
    override val id = "kugou"
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
            val songObj = lyricsClient.newCall(searchReq).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                json.optJSONObject("data")?.optJSONArray("info")?.optJSONObject(0)
            } ?: return@runCatching emptyList()

            val hash = songObj.optNonNullString("hash").takeIf { it.isNotBlank() } ?: return@runCatching emptyList()
            val songTitle = songObj.optNonNullString("songname").ifBlank { cleanTitle }
            val singerName = songObj.optNonNullString("singername").ifBlank { cleanArtist }
            val dur = songObj.optInt("duration", 0).coerceAtLeast(0)

            val lyricsSearchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=$hash"
            val lyricsReq = Request.Builder().url(lyricsSearchUrl).header("User-Agent", "KuGou/1.0").build()
            val candidates = lyricsClient.newCall(lyricsReq).execute().use { response ->
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
                    lyricsClient.newCall(dlReq).execute().use { resp ->
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

object SimpLyricsProvider : LyricsProvider {
    override val id = "simpmusic"
    override val name = "SimpMusic"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanId = id.trim()
            if (cleanId.isBlank() || cleanId.startsWith("JS:")) throw IllegalArgumentException("Unsupported ID")
            val req = Request.Builder()
                .url("https://api-lyrics.simpmusic.org/v1/$cleanId")
                .header("User-Agent", "iTune/1.0")
                .header("Accept", "application/json")
                .build()
            lyricsClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) throw IllegalStateException("No lyrics found")
                val dataArray = json.optJSONArray("data") ?: throw IllegalStateException("Empty data array")
                var bestLyrics: String? = null
                var minDiff = Int.MAX_VALUE
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i) ?: continue
                    val synced = item.optString("syncedLyrics", "").trim()
                    val plain = item.optString("plainLyrics", "").trim()
                    val lyrics = synced.ifBlank { plain }
                    if (lyrics.isBlank()) continue
                    val dur = item.optInt("duration", 0)
                    if (durationSec > 0 && dur > 0) {
                        val diff = abs(dur - durationSec)
                        if (diff <= 12 && diff < minDiff) {
                            minDiff = diff
                            bestLyrics = lyrics
                        }
                    } else if (bestLyrics == null) {
                        bestLyrics = lyrics
                    }
                }
                bestLyrics ?: throw IllegalStateException("No matching lyrics on SimpMusic")
            }
        }
    }
}

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val id = "youtube_subtitles"
    override val name = "YouTube Subtitles"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        ArtistInnertubeClient.transcript(id)
    }
}

object YouTubeLyricsProvider : LyricsProvider {
    override val id = "youtube_music"
    override val name = "YouTube Music"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        ArtistInnertubeClient.lyrics(id)
    }
}

object PaxsenixLyricsProvider : LyricsProvider {
    override val id = "paxsenix"
    override val name = "Paxsenix"
    private const val BASE_URL = "https://lyrics.paxsenix.org/"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanId = id.trim()
            if (cleanId.isNotBlank() && !cleanId.startsWith("JS:")) {
                try {
                    val req = Request.Builder()
                        .url("${BASE_URL}youtube/lyrics?id=$cleanId")
                        .header("User-Agent", "iTune/1.0")
                        .header("Accept", "application/json")
                        .build()
                    lyricsClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val lrc = extractLyrics(body)
                            if (!lrc.isNullOrBlank()) return@runCatching lrc
                        }
                    }
                } catch (_: Exception) {}
            }

            val cleanTitle = title.trim()
            val cleanArtist = artist.trim()
            if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
                val encTitle = URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name())
                val encArtist = URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8.name())
                val paths = listOf(
                    "spotify/lyrics?title=$encTitle&artist=$encArtist",
                    "musixmatch/lyrics?title=$encTitle&artist=$encArtist",
                    "apple-music/lyrics?title=$encTitle&artist=$encArtist",
                    "netease/lyrics?title=$encTitle&artist=$encArtist"
                )
                for (p in paths) {
                    try {
                        val req = Request.Builder()
                            .url(BASE_URL + p)
                            .header("User-Agent", "iTune/1.0")
                            .header("Accept", "application/json, text/plain, */*")
                            .build()
                        lyricsClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string().orEmpty()
                                val lrc = extractLyrics(body)
                                if (!lrc.isNullOrBlank()) return@runCatching lrc
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            throw IllegalStateException("No lyrics found on Paxsenix")
        }
    }

    private fun extractLyrics(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return trimmed
        return try {
            val json = JSONObject(trimmed)
            for (key in listOf("lyrics", "lrc", "content", "text", "plainLyrics", "syncedLyrics")) {
                val value = json.optString(key, "").trim()
                if (value.isNotEmpty() && value != "null") return value
            }
            null
        } catch (_: Exception) {
            trimmed
        }
    }
}

object UnisonLyricsProvider : LyricsProvider {
    override val id = "unison"
    override val name = "Unison"
    private const val BASE_URL = "https://unison.boidu.dev/"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val query = listOf(title.trim(), artist.trim()).filter { it.isNotBlank() }.joinToString(" ")
            if (query.isNotBlank()) {
                val enc = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                var searchUrl = "${BASE_URL}search?q=$enc"
                if (durationSec > 0) searchUrl += "&d=$durationSec"
                try {
                    val req = Request.Builder().url(searchUrl).header("User-Agent", "iTune/1.0").build()
                    lyricsClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string().orEmpty())
                            if (json.optBoolean("success", false)) {
                                val arr = json.optJSONArray("data")
                                if (arr != null && arr.length() > 0) {
                                    for (i in 0 until arr.length()) {
                                        val lrc = arr.optJSONObject(i)?.optString("lyrics", "")?.trim().orEmpty()
                                        if (lrc.isNotBlank()) return@runCatching lrc
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            val cleanId = id.trim()
            if (cleanId.isNotBlank() && !cleanId.startsWith("JS:")) {
                val vidUrl = "${BASE_URL}lyrics?v=$cleanId"
                try {
                    val req = Request.Builder().url(vidUrl).header("User-Agent", "iTune/1.0").build()
                    lyricsClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string().orEmpty())
                            if (json.optBoolean("success", false)) {
                                val lrc = json.optJSONObject("data")?.optString("lyrics", "")?.trim().orEmpty()
                                if (lrc.isNotBlank()) return@runCatching lrc
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            throw IllegalStateException("Lyrics not found on Unison")
        }
    }
}

object BetterLyricsProvider : LyricsProvider {
    override val id = "betterlyrics"
    override val name = "BetterLyrics"
    private const val BASE_URL = "https://lyrics-api.boidu.dev/"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = title.trim()
            val cleanArtist = artist.trim()
            if (cleanTitle.isBlank() || cleanArtist.isBlank()) throw IllegalArgumentException("Title and artist required")
            val encTitle = URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name())
            val encArtist = URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8.name())

            val endpoints = listOf(
                "${BASE_URL}getLyrics?s=$encTitle&a=$encArtist" + (if (durationSec > 0) "&d=$durationSec" else ""),
                "${BASE_URL}kugou/getLyrics?s=$encTitle&a=$encArtist" + (if (durationSec > 0) "&d=$durationSec" else ""),
            )

            for (url in endpoints) {
                try {
                    val req = Request.Builder().url(url).header("User-Agent", "iTune/1.0").build()
                    lyricsClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val text = resp.body?.string().orEmpty().trim()
                            if (text.isNotBlank()) {
                                val ttml = if (text.startsWith("{")) {
                                    JSONObject(text).optString("ttml", "")
                                } else text
                                if (ttml.isNotBlank()) {
                                    val lrc = if (TTMLParser.isTtml(ttml)) TTMLParser.ttmlToLrc(ttml) else ttml
                                    if (lrc.isNotBlank()) return@runCatching lrc
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            throw IllegalStateException("Lyrics not found on BetterLyrics")
        }
    }
}

object PortatoLyricsProvider : LyricsProvider {
    override val id = "portato"
    override val name = "Portato"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encTitle = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name())
            val encArtist = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
            val url = "https://lyrics.portato.app/api/v1/lyrics?title=$encTitle&artist=$encArtist"
            val req = Request.Builder().url(url).header("User-Agent", "iTune/1.0").build()
            lyricsClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string().orEmpty())
                val lrc = json.optString("lyrics", json.optString("lrc", "")).trim()
                if (lrc.isBlank()) throw IllegalStateException("No lyrics found on Portato")
                lrc
            }
        }
    }
}

object YouLyLyricsProvider : LyricsProvider {
    override val id = "youly"
    override val name = "YouLy"
    private val mirrors = listOf(
        "https://lyricsplus.binimum.org/",
        "https://lyricsplus.prjktla.my.id/",
        "https://lyricsplus.atomix.one/",
        "https://lyricsplus-seven.vercel.app/"
    )

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val encTitle = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name())
            val encArtist = URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8.name())
            val durParam = if (durationSec > 0) "&duration=$durationSec" else ""

            for (mirror in mirrors) {
                try {
                    val ttmlUrl = "${mirror}v1/ttml/get?title=$encTitle&artist=$encArtist$durParam"
                    val ttmlReq = Request.Builder().url(ttmlUrl).header("User-Agent", "iTune/1.0").build()
                    lyricsClient.newCall(ttmlReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string().orEmpty())
                            val ttml = json.optString("ttml", "").trim()
                            if (ttml.isNotBlank()) {
                                val lrc = if (TTMLParser.isTtml(ttml)) TTMLParser.ttmlToLrc(ttml) else ttml
                                if (lrc.isNotBlank()) return@runCatching lrc
                            }
                        }
                    }
                    val lrcUrl = "${mirror}v2/lyrics/get?title=$encTitle&artist=$encArtist$durParam"
                    val lrcReq = Request.Builder().url(lrcUrl).header("User-Agent", "iTune/1.0").build()
                    lyricsClient.newCall(lrcReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val json = JSONObject(resp.body?.string().orEmpty())
                            val lrc = json.optString("lyrics", "").trim()
                            if (lrc.isNotBlank()) return@runCatching lrc
                        }
                    }
                } catch (_: Exception) {}
            }
            throw IllegalStateException("Lyrics not found on YouLy")
        }
    }
}

object MegalobizLyricsProvider : LyricsProvider {
    override val id = "megalobiz"
    override val name = "Megalobiz"

    override suspend fun getLyrics(id: String, title: String, artist: String, durationSec: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val query = listOf(artist.trim(), title.trim()).filter { it.isNotBlank() }.joinToString(" ")
            val enc = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val searchUrl = "https://www.megalobiz.com/searchall?qry=$enc"
            val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").build()
            val searchHtml = lyricsClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            } ?: throw IllegalStateException("Search failed")

            val match = Regex("""href=["'](/lrc/maker/download/[^"']+)["']""").find(searchHtml)
                ?: throw IllegalStateException("No LRC link found on Megalobiz")
            val lrcUrl = "https://www.megalobiz.com" + match.groupValues[1]
            val detailReq = Request.Builder().url(lrcUrl).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").build()
            val detailHtml = lyricsClient.newCall(detailReq).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            } ?: throw IllegalStateException("Detail download failed")

            val rawSpan = Regex("""id=["']lrc_[^"']*_details["'][^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
                .find(detailHtml)?.groupValues?.get(1) ?: detailHtml

            val clean = rawSpan
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace(Regex("""<[^>]+>"""), "")
                .trim()

            if (LyricsHelper.parseLrc(clean).isNotEmpty()) {
                clean
            } else {
                throw IllegalStateException("Lyrics not synced on Megalobiz")
            }
        }
    }
}

object LyricsHelper {
    private const val TAG = "LyricsHelper"
    private val memoryCache = ConcurrentHashMap<String, LoadedLyrics>()

    val ALL_PROVIDERS: Map<String, LyricsProvider> = mapOf(
        "lrclib" to LrcLibLyricsProvider,
        "kugou" to KuGouLyricsProvider,
        "simpmusic" to SimpLyricsProvider,
        "youtube_subtitles" to YouTubeSubtitleLyricsProvider,
        "paxsenix" to PaxsenixLyricsProvider,
        "unison" to UnisonLyricsProvider,
        "betterlyrics" to BetterLyricsProvider,
        "portato" to PortatoLyricsProvider,
        "youly" to YouLyLyricsProvider,
        "megalobiz" to MegalobizLyricsProvider,
        "youtube_music" to YouTubeLyricsProvider,
    )

    val PROVIDER_METADATA: List<LyricsProviderInfo> = listOf(
        LyricsProviderInfo("lrclib", "LRC Lib", "Synchronized & plain community lyrics", com.darkxvenom.itune.R.drawable.lyrics_provider_lrclib),
        LyricsProviderInfo("kugou", "KuGou", "Extensive Chinese & international lyrics", com.darkxvenom.itune.R.drawable.lyrics_provider_kugou),
        LyricsProviderInfo("simpmusic", "SimpMusic", "Direct YouTube ID synced lyrics API", com.darkxvenom.itune.R.drawable.lyrics_provider_simpmusic),
        LyricsProviderInfo("youtube_subtitles", "YouTube Subtitles", "Official YouTube video timed captions & CC", com.darkxvenom.itune.R.drawable.lyrics_provider_youtube_subtitles),
        LyricsProviderInfo("paxsenix", "Paxsenix", "Multi-source (Spotify, Apple, Musixmatch, Netease)", com.darkxvenom.itune.R.drawable.lyrics_provider_paxsenix),
        LyricsProviderInfo("unison", "Unison", "Fast synchronized lyrics search", com.darkxvenom.itune.R.drawable.lyrics_provider_unison),
        LyricsProviderInfo("betterlyrics", "BetterLyrics", "High precision TTML & syllable-synced lyrics", com.darkxvenom.itune.R.drawable.lyrics_provider_betterlyrics),
        LyricsProviderInfo("portato", "Portato", "Lightweight synced lyrics endpoint", com.darkxvenom.itune.R.drawable.lyrics_provider_portato),
        LyricsProviderInfo("youly", "YouLy", "Multi-mirror TTML & LRC lyrics service", com.darkxvenom.itune.R.drawable.lyrics_provider_youly),
        LyricsProviderInfo("megalobiz", "Megalobiz", "Crowdsourced synchronized LRC archive", com.darkxvenom.itune.R.drawable.lyrics_provider_megalobiz),
        LyricsProviderInfo("youtube_music", "YouTube Music", "Official YouTube Music lyrics metadata", com.darkxvenom.itune.R.drawable.lyrics_provider_youtube_music),
    )

    fun getActiveProviders(): List<LyricsProvider> {
        val state = SettingsStore.state.value
        val order = state.lyricsProviderOrder
        val disabled = state.disabledLyricsProviders
        val active = mutableListOf<LyricsProvider>()
        for (id in order) {
            if (id !in disabled) {
                ALL_PROVIDERS[id]?.let { active.add(it) }
            }
        }
        for ((id, p) in ALL_PROVIDERS) {
            if (id !in order && id !in disabled) {
                active.add(p)
            }
        }
        return active
    }

    suspend fun loadLyricsWithRaw(
        songId: String,
        title: String,
        artist: String,
        durationSec: Int = 0
    ): LoadedLyrics = withContext(Dispatchers.IO) {
        memoryCache[songId]?.let { return@withContext it }

        var rawLrc: String? = null
        val active = getActiveProviders()
        for (provider in active) {
            val result = runCatching { provider.getLyrics(songId, title, artist, durationSec) }.getOrNull()
            if (result != null && result.isSuccess) {
                val text = result.getOrNull()
                if (!text.isNullOrBlank()) {
                    rawLrc = text
                    Log.d(TAG, "Found lyrics for $title using ${provider.name}")
                    break
                }
            }
        }

        val parsed = if (!rawLrc.isNullOrBlank()) {
            parseLyricsText(rawLrc)
        } else {
            emptyList()
        }

        val loaded = LoadedLyrics(lines = parsed, rawText = rawLrc.orEmpty())
        if (parsed.isNotEmpty()) {
            memoryCache[songId] = loaded
        }
        loaded
    }

    suspend fun loadLyrics(
        songId: String,
        title: String,
        artist: String,
        durationSec: Int = 0
    ): List<LyricsLine> = loadLyricsWithRaw(songId, title, artist, durationSec).lines

    /** Queries active search providers in parallel and returns candidates sorted with synced & closest duration first. */
    suspend fun searchAllLyrics(
        title: String,
        artist: String,
        durationSec: Int = 0
    ): List<LyricsSearchResult> = withContext(Dispatchers.IO) {
        coroutineScope {
            val lrcLibDeferred = async {
                if (SettingsStore.isLyricsProviderEnabled("lrclib")) LrcLibLyricsProvider.searchMatches(title, artist) else emptyList()
            }
            val kugouDeferred = async {
                if (SettingsStore.isLyricsProviderEnabled("kugou")) KuGouLyricsProvider.searchCandidates(title, artist, limit = 4) else emptyList()
            }
            val paxsenixDeferred = async {
                if (SettingsStore.isLyricsProviderEnabled("paxsenix")) {
                    PaxsenixLyricsProvider.getLyrics("", title, artist, durationSec).getOrNull()?.let { lrc ->
                        listOf(LyricsSearchResult(PaxsenixLyricsProvider.name, title, artist, durationSec, lrc, null))
                    } ?: emptyList()
                } else emptyList()
            }
            val unisonDeferred = async {
                if (SettingsStore.isLyricsProviderEnabled("unison")) {
                    UnisonLyricsProvider.getLyrics("", title, artist, durationSec).getOrNull()?.let { lrc ->
                        listOf(LyricsSearchResult(UnisonLyricsProvider.name, title, artist, durationSec, lrc, null))
                    } ?: emptyList()
                } else emptyList()
            }
            val betterLyricsDeferred = async {
                if (SettingsStore.isLyricsProviderEnabled("betterlyrics")) {
                    BetterLyricsProvider.getLyrics("", title, artist, durationSec).getOrNull()?.let { lrc ->
                        listOf(LyricsSearchResult(BetterLyricsProvider.name, title, artist, durationSec, lrc, null))
                    } ?: emptyList()
                } else emptyList()
            }

            val combined = lrcLibDeferred.await() + kugouDeferred.await() + paxsenixDeferred.await() + unisonDeferred.await() + betterLyricsDeferred.await()
            combined
                .filter { it.rawText.isNotBlank() }
                .distinctBy { "${it.provider}_${it.rawText.take(120)}" }
                .sortedWith(
                    compareByDescending<LyricsSearchResult> { it.isSynced }
                        .thenBy {
                            if (durationSec > 0 && it.durationSec > 0) abs(it.durationSec - durationSec)
                            else Int.MAX_VALUE / 2
                        }
                )
        }
    }

    fun updateMemoryCache(songId: String, rawText: String, lines: List<LyricsLine>) {
        if (lines.isNotEmpty()) {
            memoryCache[songId] = LoadedLyrics(lines = lines, rawText = rawText)
        } else {
            memoryCache.remove(songId)
        }
    }

    fun invalidateMemoryCache(songId: String) {
        memoryCache.remove(songId)
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

    /** Parses TTML, LRC (`[mm:ss.xx]`), or plain text. */
    fun parseLyricsText(rawText: String): List<LyricsLine> {
        val clean = rawText.trim()
        if (clean.isBlank() || clean.equals("null", ignoreCase = true)) return emptyList()

        if (TTMLParser.isTtml(clean)) {
            val ttmlLines = TTMLParser.parseTtml(clean)
            if (ttmlLines.isNotEmpty()) return ttmlLines
        }

        val synced = parseLrc(clean)
        if (synced.isNotEmpty()) return synced

        return clean.lines()
            .map { unescapeHtml(it.trim()) }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .map { LyricsLine(timeMs = 0L, text = it) }
    }

    fun formatToEditableText(lines: List<LyricsLine>): String {
        if (lines.isEmpty()) return ""
        val synced = lines.any { it.timeMs > 0L }
        return lines.joinToString("\n") { line ->
            if (synced && line.timeMs >= 0L) {
                val mins = line.timeMs / 60_000L
                val secs = (line.timeMs % 60_000L) / 1000L
                val centis = (line.timeMs % 1000L) / 10L
                "[%02d:%02d.%02d] %s".format(mins, secs, centis, line.text)
            } else {
                line.text
            }
        }
    }

    fun parseLrc(lrcText: String): List<LyricsLine> {
        val lines = mutableListOf<LyricsLine>()
        val linePattern = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\](.*)")

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
                    msStr.length == 1 -> msStr.toLong() * 100
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

/** Avoids Android `org.json.JSONObject.optString` returning `"null"` for JSON `null` fields. */
private fun JSONObject.optNonNullString(key: String): String =
    if (isNull(key)) "" else optString(key, "").trim()
