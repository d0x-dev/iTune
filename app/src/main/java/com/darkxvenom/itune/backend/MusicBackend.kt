package com.darkxvenom.itune.backend

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

object MusicBackend {
    private const val TAG = "MusicBackend"
    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val SONG_PARAMS = "EgWKAQIIAWoSEAQQAxAFEAkQChAVEA4QERAQ"
    private const val ARTIST_PARAMS = "EgWKAQIgAWoSEAQQAxAFEAkQChAVEA4QERAQ"
    private const val ALBUM_PARAMS = "EgWKAQIYAWoSEAQQAxAFEAkQChAVEA4QERAQ"
    private const val PLAYLIST_PARAMS = "EgeKAQQoAEABahIQBBADEAUQCRAKEBUQDhAREBA="
    private const val FEATURED_PLAYLIST_PARAMS = "EgeKAQQoADgBahIQBBADEAUQCRAKEBUQDhAREBA="
    private const val STREAM_CACHE_TTL_MS = 20L * 60_000L
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private data class CachedStreamInfo(val info: StreamInfo, val fetchedAtMs: Long)
    private val streamCache = ConcurrentHashMap<String, CachedStreamInfo>()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        try {
            NewPipe.init(NewPipeDownloader.instance)
            org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(false)
            initialized = true
            Log.d(TAG, "NewPipeExtractor initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing NewPipe: ${e.message}", e)
        }
    }

    suspend fun searchMusic(query: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = runCatching {
            collectRenderers(postInnerTubeSearch(query, SONG_PARAMS), ::parseTrackItem)
        }.getOrElse { e ->
            Log.e(TAG, "Search failed for '$query': ${e.message}")
            emptyList()
        }.toMutableList()

        if (tracks.isEmpty()) {
            try {
                val service = ServiceList.YouTube
                val searchInfo = SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query))
                for (item in searchInfo.relatedItems) {
                    if (item is StreamInfoItem) {
                        tracks.add(
                            MusicTrack(
                                id = item.url.substringAfter("v=").substringBefore("&"),
                                title = item.name,
                                artists = item.uploaderName.orEmpty(),
                                thumbnailUrl = item.thumbnails.lastOrNull()?.url?.highQualityThumbnail(),
                                durationSec = item.duration
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "NewPipe fallback search failed: ${e.message}")
            }
        }

        tracks.distinctBy { it.id }
    }

    /** Searches Songs, Artists, Albums, and Playlists in parallel for unified search results. */
    suspend fun searchAllCategories(query: String): UnifiedSearchResults = withContext(Dispatchers.IO) {
        coroutineScope {
            val songsJob = async { searchMusic(query) }
            val artistsJob = async {
                runCatching {
                    collectRenderers(postInnerTubeSearch(query, ARTIST_PARAMS), ::parseArtistItem)
                        .distinctBy { it.id }
                }.getOrDefault(emptyList())
            }
            val albumsJob = async {
                runCatching {
                    collectRenderers(postInnerTubeSearch(query, ALBUM_PARAMS)) { obj ->
                        parsePlaylistOrAlbumItem(obj, isAlbum = true)
                    }.distinctBy { it.id }
                }.getOrDefault(emptyList())
            }
            val featuredPlaylistsJob = async {
                runCatching {
                    collectRenderers(postInnerTubeSearch(query, FEATURED_PLAYLIST_PARAMS)) { obj ->
                        parsePlaylistOrAlbumItem(obj, isAlbum = false)
                    }
                }.getOrDefault(emptyList())
            }
            val communityPlaylistsJob = async {
                runCatching {
                    collectRenderers(postInnerTubeSearch(query, PLAYLIST_PARAMS)) { obj ->
                        parsePlaylistOrAlbumItem(obj, isAlbum = false)
                    }
                }.getOrDefault(emptyList())
            }

            val mergedPlaylists = (featuredPlaylistsJob.await() + communityPlaylistsJob.await())
                .distinctBy { it.id }

            UnifiedSearchResults(
                songs = songsJob.await(),
                artists = artistsJob.await(),
                albums = albumsJob.await(),
                playlists = mergedPlaylists
            )
        }
    }

    /** Loads playable tracks for an Artist (`UC...`), Album (`MPREb...`), or Playlist (`VL...`) browseId. */
    suspend fun loadCollectionTracks(
        browseId: String,
        fallbackArtist: String = "",
        fallbackThumbnailUrl: String? = null
    ): List<MusicTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("browseId", browseId)
            val root = postInnerTube(BROWSE_URL, body)
            collectRenderers(root) { obj ->
                parseTrackItem(
                    itemObj = obj,
                    fallbackArtist = fallbackArtist,
                    fallbackThumbnailUrl = fallbackThumbnailUrl
                )
            }.distinctBy { it.id }
        }.getOrElse { e ->
            Log.e(TAG, "Browse failed for '$browseId': ${e.message}")
            emptyList()
        }
    }

    suspend fun getOrLoadStreamInfo(videoId: String): StreamInfo = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        streamCache[videoId]?.let { cached ->
            if (now - cached.fetchedAtMs < STREAM_CACHE_TTL_MS) {
                return@withContext cached.info
            }
        }
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        streamCache[videoId] = CachedStreamInfo(info, now)
        info
    }

    suspend fun extractAudioQualities(videoId: String): List<AudioDownloadOption> = withContext(Dispatchers.IO) {
        val info = getOrLoadStreamInfo(videoId)
        val durationSec = info.duration.takeIf { it > 0 } ?: 0L
        val audios = info.audioStreams
            .orEmpty()
            .filter {
                it.isUrl &&
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.audioTrackType != AudioTrackType.DUBBED &&
                    it.averageBitrate > 0
            }
            .sortedByDescending { it.averageBitrate }

        // Strictly prefer pure M4A audio containers (matching iTube & Android Music compatibility)
        // so tracks never download as .webm video files.
        val m4aAudios = audios.filter { it.format == MediaFormat.M4A }
        val finalAudios = if (m4aAudios.isNotEmpty()) m4aAudios else audios

        val seenKeys = HashSet<String>()
        val result = mutableListOf<AudioDownloadOption>()

        for (audio in finalAudios) {
            val isM4a = audio.format == MediaFormat.M4A
            val needsOggRemux = !isM4a && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            val formatTag = if (isM4a) "M4A" else "OGG"
            val ext = if (isM4a) "m4a" else "ogg"
            val mime = if (isM4a) "audio/mp4" else "audio/ogg"
            val kbps = audio.averageBitrate
            if (!seenKeys.add("${formatTag}_$kbps")) continue

            val sizeStr = if (durationSec > 0) {
                AudioDownloader.formatBytes((kbps.toLong() * 1000L * durationSec) / 8L)
            } else null

            val titleLabel = when {
                kbps >= 220 -> "Master Quality Audio"
                kbps >= 120 -> "High Quality Audio"
                kbps >= 80 -> "Standard Audio"
                else -> "Data Saver Audio"
            }

            result.add(
                AudioDownloadOption(
                    bitrateKbps = kbps,
                    label = titleLabel,
                    formatTag = formatTag,
                    formatLabel = "$formatTag · $kbps kbps",
                    extension = ext,
                    mimeType = mime,
                    approxSize = sizeStr,
                    streamUrl = audio.content,
                    isHighQuality = kbps >= 120,
                    needsOggRemux = needsOggRemux
                )
            )
        }
        result
    }

    suspend fun resolveAudioStream(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val info = getOrLoadStreamInfo(videoId)

            val audioStreams = info.audioStreams
            if (audioStreams.isNullOrEmpty()) {
                Log.w(TAG, "No audio streams found for $videoId")
                return@withContext null
            }

            val bestM4a = audioStreams
                .filter { it.format == MediaFormat.M4A && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.audioTrackType != AudioTrackType.DUBBED }
                .maxByOrNull { it.averageBitrate }

            val bestAny = audioStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.audioTrackType != AudioTrackType.DUBBED }
                .maxByOrNull { it.averageBitrate }

            val chosen = bestM4a ?: bestAny ?: audioStreams.firstOrNull()
            Log.d(TAG, "Selected stream: ${chosen?.format} @ ${chosen?.averageBitrate} kbps")
            return@withContext chosen?.content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream for $videoId: ${e.message}", e)
            return@withContext null
        }
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String? = resolveAudioStream(videoId)

    private fun postInnerTubeSearch(query: String, params: String? = null): JSONObject {
        val body = JSONObject().put("query", query)
        if (params != null) body.put("params", params)
        return postInnerTube(SEARCH_URL, body)
    }

    private fun postInnerTube(url: String, body: JSONObject): JSONObject {
        val clientObj = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20240101.01.00")
            .put("hl", "en")
            .put("gl", "US")

        val payload = JSONObject(body.toString())
            .put("context", JSONObject().put("client", clientObj))

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_TYPE))
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        Net.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            return JSONObject(text)
        }
    }

    private fun <T> collectRenderers(
        root: JSONObject,
        parser: (JSONObject) -> T?
    ): List<T> {
        val out = mutableListOf<T>()
        root.walk { key, itemObj ->
            if (key == "musicResponsiveListItemRenderer" || key == "musicTwoRowItemRenderer") {
                val wrapper = JSONObject().put(key, itemObj)
                parser(wrapper)?.let(out::add)
            }
        }
        return out
    }

    private fun JSONObject.extractBestThumbnail(): String? =
        optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
            ?.takeIf { it.isNotBlank() }
            ?.highQualityThumbnail()

    private fun JSONObject.extractColumnText(colIndex: Int): String {
        val runs = optJSONArray("flexColumns")
            ?.optJSONObject(colIndex)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.trim()
    }

    /** Splits Innertube flex column runs at bullet separators (`•` / `·`) preserving multi-artist credits like `"A, B, C"`. */
    private fun extractSegmentedRuns(subRuns: JSONArray?): List<Pair<String, String?>> {
        if (subRuns == null || subRuns.length() == 0) return emptyList()
        val segments = mutableListOf<Pair<String, String?>>()
        val currentText = StringBuilder()
        var firstBrowseId: String? = null

        fun flushSegment() {
            val cleaned = currentText.toString().trim()
            if (cleaned.isNotEmpty()) {
                segments.add(cleaned to firstBrowseId)
            }
            currentText.setLength(0)
            firstBrowseId = null
        }

        for (r in 0 until subRuns.length()) {
            val runObj = subRuns.optJSONObject(r) ?: continue
            val t = runObj.optString("text")
            val trimmed = t.trim()
            if (trimmed == "•" || trimmed == "·") {
                flushSegment()
                continue
            }
            if (firstBrowseId == null) {
                firstBrowseId = runObj.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("browseId")
                    ?.takeIf { it.isNotBlank() }
            }
            currentText.append(t)
        }
        flushSegment()
        return segments
    }

    private fun parseArtistItem(itemObj: JSONObject): MusicArtist? {
        val responsive = itemObj.optJSONObject("musicResponsiveListItemRenderer") ?: return null
        val browseId = responsive.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            ?.takeIf { it.isNotBlank() } ?: return null

        val name = responsive.extractColumnText(0).ifBlank { return null }
        val subRuns = responsive.optJSONArray("flexColumns")
            ?.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val segments = extractSegmentedRuns(subRuns)
            .map { it.first }
            .filter { !it.equals("Artist", ignoreCase = true) }
        val subtitle = segments.joinToString(" · ").ifBlank { "Artist" }

        return MusicArtist(
            id = browseId,
            name = name,
            thumbnailUrl = responsive.optJSONObject("thumbnail")?.let {
                JSONObject().put("thumbnail", it).extractBestThumbnail()
            },
            subscribers = subtitle
        )
    }

    private fun parsePlaylistOrAlbumItem(itemObj: JSONObject, isAlbum: Boolean): MusicPlaylistRef? {
        val responsive = itemObj.optJSONObject("musicResponsiveListItemRenderer") ?: return null
        val browseId = responsive.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            ?.takeIf { it.isNotBlank() } ?: return null

        val title = responsive.extractColumnText(0).ifBlank { return null }
        val subRuns = responsive.optJSONArray("flexColumns")
            ?.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val subtitle = extractSegmentedRuns(subRuns)
            .map { it.first }
            .filter {
                !it.equals("Playlist", ignoreCase = true) &&
                    !it.equals("Album", ignoreCase = true)
            }
            .joinToString(" · ")

        val watchPlaylistId = responsive.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchPlaylistEndpoint")
            ?.optString("playlistId")
            ?.takeIf { it.isNotBlank() }

        val thumbUrl = JSONObject().put("thumbnail", responsive.optJSONObject("thumbnail"))
            .extractBestThumbnail()

        return MusicPlaylistRef(
            id = browseId,
            title = title,
            author = subtitle.ifBlank { if (isAlbum) "Album" else "Playlist" },
            thumbnailUrl = thumbUrl,
            isAlbum = isAlbum,
            watchPlaylistId = watchPlaylistId
        )
    }

    private fun parseTrackItem(
        itemObj: JSONObject,
        fallbackArtist: String = "",
        fallbackThumbnailUrl: String? = null
    ): MusicTrack? {
        itemObj.optJSONObject("musicResponsiveListItemRenderer")?.let { responsive ->
            val videoId = responsive.optJSONObject("playlistItemData")?.optString("videoId")
                ?: responsive.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
                ?: responsive.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
            if (videoId.isNullOrBlank()) return null

            val flexColumns = responsive.optJSONArray("flexColumns")
            val title = flexColumns?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text").orEmpty()

            val subRuns = flexColumns?.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

            var artists = ""
            var artistId: String? = null
            var album: String? = null
            var albumId: String? = null
            var durationSec = 0L

            if (subRuns != null && subRuns.length() > 0) {
                val nonDelimiterRuns = mutableListOf<Pair<String, String?>>()
                for (r in 0 until subRuns.length()) {
                    val runObj = subRuns.optJSONObject(r)
                    val t = runObj?.optString("text").orEmpty().trim()
                    val bId = runObj?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                    if (t.isNotEmpty() && t != "•" && t != "·") {
                        if (t.contains(":") && t.matches(Regex("\\d+:\\d+(:\\d+)?"))) {
                            durationSec = parseTimeString(t)
                        } else {
                            nonDelimiterRuns.add(t to bId)
                        }
                    }
                }
                if (nonDelimiterRuns.isNotEmpty()) {
                    artists = nonDelimiterRuns[0].first
                    artistId = nonDelimiterRuns[0].second
                    if (nonDelimiterRuns.size > 1) {
                        album = nonDelimiterRuns[1].first
                        albumId = nonDelimiterRuns[1].second
                    }
                }
            }

            val thumbUrl = responsive.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
                ?.let { thumbs ->
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }?.highQualityThumbnail()

            return MusicTrack(
                id = videoId,
                title = title.ifBlank { "Unknown Title" },
                artists = artists.ifBlank { "Various Artists" },
                artistId = artistId,
                album = album,
                albumId = albumId,
                thumbnailUrl = thumbUrl,
                durationSec = durationSec
            )
        }

        itemObj.optJSONObject("musicTwoRowItemRenderer")?.let { twoRow ->
            val watchEndpoint = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
            val videoId = watchEndpoint?.optString("videoId")
            if (videoId.isNullOrBlank()) return null

            val title = twoRow.optJSONObject("title")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text").orEmpty()

            val subtitleRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
            val artists = subtitleRuns?.optJSONObject(0)?.optString("text").orEmpty()
            val artistId = subtitleRuns?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")

            val thumbUrl = twoRow.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
                ?.let { thumbs ->
                    thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
                }?.highQualityThumbnail()

            return MusicTrack(
                id = videoId,
                title = title.ifBlank { "Unknown Title" },
                artists = artists.ifBlank { "Various Artists" },
                artistId = artistId,
                thumbnailUrl = thumbUrl
            )
        }

        return null
    }
}

/** Depth-first visit of every object-valued key in the tree. */
internal fun JSONObject.walk(visit: (key: String, obj: JSONObject) -> Unit) {
    val keys = keys()
    while (keys.hasNext()) {
        val k = keys.next()
        when (val v = opt(k)) {
            is JSONObject -> { visit(k, v); v.walk(visit) }
            is JSONArray -> v.walkArray(visit)
        }
    }
}

private fun JSONArray.walkArray(visit: (key: String, obj: JSONObject) -> Unit) {
    for (i in 0 until length()) {
        when (val v = opt(i)) {
            is JSONObject -> v.walk(visit)
            is JSONArray -> v.walkArray(visit)
        }
    }
}
