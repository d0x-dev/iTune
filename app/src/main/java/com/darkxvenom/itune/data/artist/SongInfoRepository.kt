package com.darkxvenom.itune.data.artist

import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormatSymbols
import java.time.LocalDate

data class SongInfo(
    val album: String? = null,
    val releaseYear: String? = null,
    val releaseDate: String? = null,
    val views: String? = null,
    val likes: String? = null,
    val comments: String? = null,
    val failed: Boolean = false,
)

fun compactCount(raw: String): String {
    val n = raw.toLongOrNull()?.takeIf { it >= 0 } ?: return raw
    val (unit, suffix) = when {
        n >= 1_000_000_000 -> 1_000_000_000L to "B"
        n >= 1_000_000 -> 1_000_000L to "M"
        n >= 1_000 -> 1_000L to "K"
        else -> return n.toString()
    }
    val whole = n / unit
    if (whole >= 10) return "$whole$suffix"
    val tenth = n % unit * 10 / unit
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    return if (tenth == 0L) "$whole$suffix" else "$whole$separator$tenth$suffix"
}

object SongInfoRepository {

    suspend fun load(song: Song): SongInfo = withContext(Dispatchers.IO) {
        if (song.localUri != null || !Regex("[A-Za-z0-9_-]{11}").matches(song.videoId)) {
            return@withContext SongInfo(album = song.albumName)
        }
        val statistics = async {
            attempt {
                val request = Request.Builder()
                    .url("https://www.youtube.com/watch?v=${song.videoId}&hl=${ArtistInnertubeClient.currentLanguage}")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
                    )
                    .header("Cookie", "SOCS=CAI")
                    .build()
                val html = Net.client.newCall(request).execute().use { res ->
                    check(res.isSuccessful)
                    res.body?.string().orEmpty()
                }
                SongInfoParser.parse(html, song.videoId)
            }
        }
        var album = song.albumName
        var year: String? = null
        val release = attempt {
            val linked = if (song.albumId == null) {
                ArtistRepository.trackLinks(song.videoId).getOrThrow()
            } else song
            album = album ?: linked.albumName
            linked.albumId?.let { id ->
                val header = ArtistInnertubeParser.parseBrowseHeader(ArtistInnertubeClient.browse(id))
                album = album ?: header?.title
                year = header?.subtitle?.split('•')?.map { it.trim() }
                    ?.firstOrNull { it.matches(Regex("(?:19|20)[0-9]{2}")) }
            }
        }
        (statistics.await().getOrNull() ?: SongInfo(failed = true)).copy(
            album = album,
            releaseYear = year,
        ).let { it.copy(failed = it.failed || release.isFailure) }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal object SongInfoParser {
    private val SCRIPT_REGEX = Regex("<script[^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    fun parse(html: String, videoId: String): SongInfo {
        val scripts = SCRIPT_REGEX.findAll(html).map { it.groupValues[1] }.toList()
        val player = scripts.firstNotNullOfOrNull { extract(it, "ytInitialPlayerResponse") }
            ?: error("Missing player metadata")
        val details = player.optJSONObject("videoDetails") ?: JSONObject()
        check(details.optString("videoId") == videoId) { "Metadata belongs to another track" }
        val data = scripts.firstNotNullOfOrNull { extract(it, "ytInitialData") }
        val primary = data?.find("videoPrimaryInfoRenderer")?.firstOrNull()
        val oldLike = primary?.find("toggleButtonRenderer")?.firstOrNull {
            it.optJSONObject("defaultIcon")?.optString("iconType") == "LIKE"
        }?.opt("defaultText")?.text()
        val newLike = primary?.find("likeButtonViewModel")?.firstOrNull()
            ?.find("buttonViewModel")?.firstOrNull()?.optString("title")?.takeIf { it.isNotBlank() }
        val comments = data?.find("commentsHeaderRenderer")?.firstNotNullOfOrNull {
            it.opt("countText")?.text()?.withCount()
        }
            ?: data?.find("commentsEntryPointHeaderRenderer")?.firstOrNull()
                ?.opt("commentCount")?.text()?.withCount()
            ?: data?.find("engagementPanelSectionListRenderer")?.firstOrNull {
                it.optString("targetId") == "engagement-panel-comments-section" ||
                    it.optString("panelIdentifier") == "engagement-panel-comments-section"
            }?.find("engagementPanelTitleHeaderRenderer")?.firstOrNull()
                ?.opt("contextualInfo")?.text()?.withCount()
        return SongInfo(
            releaseDate = releaseDate(details.optString("shortDescription")),
            views = details.optString("viewCount").takeIf { it.toLongOrNull()?.let { n -> n >= 0 } == true },
            likes = (newLike ?: oldLike)?.withCount(),
            comments = comments,
            failed = data == null,
        )
    }

    private fun releaseDate(description: String?): String? {
        val date = description?.lineSequence()?.firstNotNullOfOrNull { line ->
            Regex("Released on:\\s*(\\d{4}-\\d{2}-\\d{2})", RegexOption.IGNORE_CASE)
                .matchEntire(line.trim())?.groupValues?.get(1)
        } ?: return null
        return runCatching { LocalDate.parse(date).toString() }.getOrNull()
    }

    private fun extract(script: String, name: String): JSONObject? {
        val assignment = Regex("(?:var\\s+)?${name}\\s*=\\s*").find(script) ?: return null
        val start = assignment.range.last + 1
        if (script.getOrNull(start) != '{') return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (i in start until script.length) {
            val c = script[i]
            if (quoted) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) {
                    return runCatching { JSONObject(script.substring(start, i + 1)) }.getOrNull()
                }
            }
        }
        return null
    }

    private fun String.withCount(): String? = takeIf { it.any(Char::isDigit) }

    private fun Any?.text(): String? = (this as? JSONObject)?.let { obj ->
        val simple = obj.optString("simpleText").takeIf { it.isNotBlank() }
        if (simple != null) return@let simple
        val runs = obj.optJSONArray("runs") ?: return@let null
        buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }
    }?.takeIf { it.isNotBlank() }

    private fun Any?.find(key: String): List<JSONObject> = when (this) {
        is JSONObject -> buildList {
            optJSONObject(key)?.let(::add)
            val keys = keys()
            while (keys.hasNext()) {
                addAll(opt(keys.next()).find(key))
            }
        }
        is JSONArray -> buildList {
            for (i in 0 until length()) {
                addAll(opt(i).find(key))
            }
        }
        else -> emptyList()
    }
}
