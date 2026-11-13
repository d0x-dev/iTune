package com.darkxvenom.itune.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.charts.ChartTrack
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.SettingsStore
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
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

object MusicBackend {
    private const val TAG = "MusicBackend"
    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
    private const val SUGGESTIONS_URL = "https://music.youtube.com/youtubei/v1/music/get_search_suggestions?prettyPrint=false"
    private const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val NEXT_URL = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
    private const val BROWSE_NEW_RELEASES_ALBUMS = "FEmusic_new_releases_albums"
    private const val BROWSE_EXPLORE = "FEmusic_explore"
    private const val BROWSE_MOODS_AND_GENRES = "FEmusic_moods_and_genres"
    private const val SONG_PARAMS = "EgWKAQIIAWoSEAQQAxAFEAkQChAVEA4QERAQ"
    private const val ARTIST_PARAMS = "EgWKAQIgAWoSEAQQAxAFEAkQChAVEA4QERAQ"
    private const val ALBUM_PARAMS = "EgWKAQIYAWoSEAQQAxAFEAkQChAVEA4QERAQ"
    private const val PLAYLIST_PARAMS = "EgeKAQQoAEABahIQBBADEAUQCRAKEBUQDhAREBA="
    private const val FEATURED_PLAYLIST_PARAMS = "EgeKAQQoADgBahIQBBADEAUQCRAKEBUQDhAREBA="
    private const val STREAM_CACHE_TTL_MS = 20L * 60_000L
    private const val MAX_SUGGESTION_CACHE = 120
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    private data class CachedStreamInfo(val info: StreamInfo, val fetchedAtMs: Long)
    private val streamCache = ConcurrentHashMap<String, CachedStreamInfo>()
    private val suggestionCache = ConcurrentHashMap<String, List<String>>()
    private val moodCategoryCache = ConcurrentHashMap<String, MoodCategoryPage>()
    private val explorePlaylistsCache = ConcurrentHashMap<String, List<MusicPlaylistRef>>()

    private var initialized = false
    private var appContext: Context? = null

    fun clearLocalizedCaches() {
        suggestionCache.clear()
        moodCategoryCache.clear()
        explorePlaylistsCache.clear()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
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

        val hideExplicit = SettingsStore.isHideExplicitEnabled()
        tracks.distinctBy { it.id }.filter { !hideExplicit || !it.isExplicit }
    }

    /** Fast search autocomplete suggestions via YouTube Music InnerTube with NewPipe fallback and memory cache. */
    suspend fun searchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val cacheKey = trimmed.lowercase()
        suggestionCache[cacheKey]?.let { return@withContext it }

        val innerTubeSuggestions = runCatching {
            val root = postInnerTube(SUGGESTIONS_URL, JSONObject().put("input", trimmed), anonymous = true)
            extractMusicSuggestions(root)
        }.getOrDefault(emptyList())

        val resolved = if (innerTubeSuggestions.isNotEmpty()) {
            innerTubeSuggestions
        } else {
            runCatching {
                ServiceList.YouTube.suggestionExtractor.suggestionList(trimmed)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }.getOrDefault(emptyList())
        }.distinctBy { it.lowercase() }.take(10)

        if (resolved.isNotEmpty()) {
            if (suggestionCache.size >= MAX_SUGGESTION_CACHE) {
                suggestionCache.clear()
            }
            suggestionCache[cacheKey] = resolved
        }
        resolved
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

            val hideExplicit = SettingsStore.isHideExplicitEnabled()
            val mergedPlaylists = (featuredPlaylistsJob.await() + communityPlaylistsJob.await())
                .distinctBy { it.id }
                .filter { !hideExplicit || !it.isExplicit }

            UnifiedSearchResults(
                songs = songsJob.await(),
                artists = artistsJob.await(),
                albums = albumsJob.await().filter { !hideExplicit || !it.isExplicit },
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

    /**
     * Fetches AirBeats-equivalent YouTube Music New Releases (`FEmusic_new_releases_albums` with
     * `FEmusic_explore` carousel fallback), preserving Albums, Singles, EPs, year, and explicit metadata.
     */
    suspend fun fetchNewReleaseAlbums(): List<NewReleaseAlbum> = withContext(Dispatchers.IO) {
        val hideExplicit = SettingsStore.isHideExplicitEnabled()
        val direct = runCatching {
            val root = postInnerTube(BROWSE_URL, JSONObject().put("browseId", BROWSE_NEW_RELEASES_ALBUMS))
            val out = mutableListOf<NewReleaseAlbum>()
            root.walk { key, obj ->
                if (key == "musicTwoRowItemRenderer") {
                    parseNewReleaseTwoRowItem(obj)?.let(out::add)
                }
            }
            out.distinctBy { it.browseId }.filter { !hideExplicit || !it.isExplicit }
        }.getOrDefault(emptyList())

        if (direct.isNotEmpty()) return@withContext direct

        runCatching {
            val exploreRoot = postInnerTube(BROWSE_URL, JSONObject().put("browseId", BROWSE_EXPLORE))
            val fallback = mutableListOf<NewReleaseAlbum>()
            exploreRoot.walk { key, obj ->
                if (key == "musicCarouselShelfRenderer") {
                    val moreBrowseId = obj.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("moreContentButton")
                        ?.optJSONObject("buttonRenderer")
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                    if (moreBrowseId == BROWSE_NEW_RELEASES_ALBUMS) {
                        val contents = obj.optJSONArray("contents")
                        if (contents != null) {
                            for (i in 0 until contents.length()) {
                                contents.optJSONObject(i)
                                    ?.optJSONObject("musicTwoRowItemRenderer")
                                    ?.let(::parseNewReleaseTwoRowItem)
                                    ?.let(fallback::add)
                            }
                        }
                    }
                }
            }
            fallback.distinctBy { it.browseId }.filter { !hideExplicit || !it.isExplicit }
        }.getOrDefault(emptyList())
    }

    private fun parseNewReleaseTwoRowItem(twoRow: JSONObject): NewReleaseAlbum? {
        val browseEndpoint = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        val browseId = browseEndpoint?.optString("browseId")?.takeIf { it.isNotBlank() } ?: return null

        val title = buildRunsText(twoRow.optJSONObject("title")?.optJSONArray("runs")).ifBlank { return null }

        val playlistId = twoRow.optJSONObject("thumbnailOverlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchPlaylistEndpoint")
            ?.optString("playlistId")
            ?.takeIf { it.isNotBlank() }

        val subtitleRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
        val segments = extractSegmentedRuns(subtitleRuns)
        val firstSegText = segments.firstOrNull()?.first.orEmpty()
        val lowerFirst = firstSegText.trim().lowercase()
        val startsWithReleaseType = lowerFirst in setOf("album", "albums", "single", "singles", "ep", "eps")

        val releaseType = if (startsWithReleaseType) {
            ReleaseType.fromLabel(firstSegText)
        } else {
            val hasSingle = segments.any { it.first.equals("Single", ignoreCase = true) }
            val hasEp = segments.any { it.first.equals("EP", ignoreCase = true) }
            when {
                hasSingle -> ReleaseType.SINGLE
                hasEp -> ReleaseType.EP
                else -> ReleaseType.ALBUM
            }
        }

        val yearRegex = Regex("\\d{4}")
        val year = segments.asReversed().firstNotNullOfOrNull { seg ->
            if (yearRegex.matches(seg.first.trim())) seg.first.trim().toIntOrNull() else null
        }

        val artistSegment = when {
            startsWithReleaseType -> segments.getOrNull(1)
            else -> segments.firstOrNull { !yearRegex.matches(it.first.trim()) }
        }
        val artist = artistSegment?.first?.takeIf { it.isNotBlank() } ?: releaseType.label
        val artistId = artistSegment?.second

        val thumbUrl = twoRow.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
            ?.takeIf { it.isNotBlank() }
            ?.highQualityThumbnail()

        return NewReleaseAlbum(
            browseId = browseId,
            playlistId = playlistId,
            title = title,
            artist = artist,
            artistId = artistId,
            year = year,
            releaseType = releaseType,
            thumbnailUrl = thumbUrl,
            isExplicit = twoRow.hasExplicitBadge()
        )
    }

    /**
     * Fetches AirBeats "Moods & moments" navigation items from `FEmusic_moods_and_genres`.
     */
    suspend fun fetchMoodsAndMoments(): List<MoodAndMomentRef> =
        fetchMoodsAndGenresCatalog().moods

    /**
     * Fetches localized Moods & Moments AND Genres sections from `FEmusic_moods_and_genres`
     * for the user's configured Content Language (`hl`) and Content Country (`gl`).
     */
    suspend fun fetchMoodsAndGenresCatalog(): MoodsAndGenresCatalog = withContext(Dispatchers.IO) {
        runCatching {
            val root = postInnerTube(BROWSE_URL, JSONObject().put("browseId", BROWSE_MOODS_AND_GENRES))
            val sections = root.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return@runCatching MoodsAndGenresCatalog()

            var moodsList = emptyList<MoodAndMomentRef>()
            var genresList = emptyList<MoodAndMomentRef>()
            for (i in 0 until sections.length()) {
                val grid = sections.optJSONObject(i)?.optJSONObject("gridRenderer") ?: continue
                val sectionTitle = buildRunsText(
                    grid.optJSONObject("header")
                        ?.optJSONObject("gridHeaderRenderer")
                        ?.optJSONObject("title")
                        ?.optJSONArray("runs")
                )
                val items = grid.optJSONArray("items") ?: continue
                val parsed = buildList {
                    for (j in 0 until items.length()) {
                        val btn = items.optJSONObject(j)
                            ?.optJSONObject("musicNavigationButtonRenderer") ?: continue
                        val label = buildRunsText(btn.optJSONObject("buttonText")?.optJSONArray("runs"))
                        val endpoint = btn.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint")
                        val bId = endpoint?.optString("browseId")?.takeIf { it.isNotBlank() } ?: continue
                        val params = endpoint.optString("params").takeIf { it.isNotBlank() }
                        if (label.isNotBlank()) {
                            add(MoodAndMomentRef(title = label, browseId = bId, params = params))
                        }
                    }
                }
                if (parsed.isEmpty()) continue
                if (sectionTitle.contains("mood", ignoreCase = true) || moodsList.isEmpty()) {
                    if (moodsList.isEmpty() || sectionTitle.contains("mood", ignoreCase = true)) {
                        if (moodsList.isNotEmpty() && genresList.isEmpty()) {
                            genresList = moodsList
                        }
                        moodsList = parsed
                        continue
                    }
                }
                if (genresList.isEmpty()) {
                    genresList = parsed
                }
            }
            MoodsAndGenresCatalog(moods = moodsList, genres = genresList)
        }.getOrDefault(MoodsAndGenresCatalog())
    }

    /**
     * Fetches regional & language-tailored Featured / Charts Playlists from `FEmusic_explore`
     * with search fallback tailored to the active Content Country and Content Language.
     */
    suspend fun fetchLocalizedExplorePlaylists(): List<MusicPlaylistRef> = withContext(Dispatchers.IO) {
        val localeSig = SettingsStore.localeSignature()
        val hideExplicit = SettingsStore.isHideExplicitEnabled()
        val cached = explorePlaylistsCache[localeSig]
        if (cached != null) {
            return@withContext cached.filter { !hideExplicit || !it.isExplicit }
        }

        val collected = mutableListOf<MusicPlaylistRef>()
        runCatching {
            val root = postInnerTube(BROWSE_URL, JSONObject().put("browseId", BROWSE_EXPLORE))
            root.walk { key, obj ->
                if (key == "musicTwoRowItemRenderer") {
                    val nav = obj.optJSONObject("navigationEndpoint")
                    val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                    if (browseId.startsWith("VL") || browseId.startsWith("OLAK") || browseId.startsWith("RDCLAK")) {
                        val wrapper = JSONObject().put("musicTwoRowItemRenderer", obj)
                        parsePlaylistOrAlbumItem(wrapper, isAlbum = false)?.let(collected::add)
                    }
                }
            }
        }

        if (collected.size < 6) {
            val countryName = ContentLocaleCatalog.countryLabel(SettingsStore.state.value.contentCountry)
                .takeIf { it != "System Default" } ?: SettingsStore.effectiveGl()
            val langName = ContentLocaleCatalog.languageLabel(SettingsStore.state.value.contentLanguage)
                .takeIf { it != "System Default" }
            val query = listOfNotNull(countryName, langName, "Top Hits").joinToString(" ")
            runCatching {
                val searched = collectRenderers(postInnerTubeSearch(query, FEATURED_PLAYLIST_PARAMS)) { obj ->
                    parsePlaylistOrAlbumItem(obj, isAlbum = false)
                }
                collected.addAll(searched)
            }
        }

        val distinct = collected.distinctBy { it.id }
        if (distinct.isNotEmpty()) {
            explorePlaylistsCache[localeSig] = distinct
        }
        distinct.filter { !hideExplicit || !it.isExplicit }
    }

    /**
     * Fetches ranked YouTube Charts tracks (`FEmusic_charts`) with fallback to regional top chart search.
     */
    suspend fun fetchYouTubeCharts(): List<ChartTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<ChartTrack>()
        val seenIds = HashSet<String>()
        runCatching {
            val root = postInnerTube(BROWSE_URL, JSONObject().put("browseId", "FEmusic_charts"))
            var rank = 1
            root.walk { key, obj ->
                if (key == "musicResponsiveListItemRenderer") {
                    val parsed = parseTrackItem(JSONObject().put("musicResponsiveListItemRenderer", obj))
                    if (parsed != null && seenIds.add(parsed.id)) {
                        tracks.add(
                            ChartTrack(
                                rank = rank++,
                                title = parsed.title,
                                artist = parsed.artists,
                                thumbnailUrl = parsed.thumbnailUrl,
                                id = parsed.id,
                                durationSec = parsed.durationSec,
                                isExplicit = parsed.isExplicit
                            )
                        )
                    }
                }
            }
        }

        // Fallback: If FEmusic_charts returned fewer than 10 tracks, query regional top charts
        if (tracks.size < 10) {
            val country = SettingsStore.state.value.contentCountry.ifBlank { SettingsStore.effectiveGl() }
            val query = "Top 100 Songs $country"
            val searched = searchMusic(query)
            for (st in searched) {
                if (seenIds.add(st.id)) {
                    tracks.add(
                        ChartTrack(
                            rank = tracks.size + 1,
                            title = st.title,
                            artist = st.artists,
                            thumbnailUrl = st.thumbnailUrl,
                            id = st.id,
                            durationSec = st.durationSec,
                            isExplicit = st.isExplicit
                        )
                    )
                }
            }
        }
        tracks.take(100)
    }

    /**
     * Fetches full category browse content (carousels, grids, playlists, albums, songs)
     * for any tapped AirBeats Moods & Moments item (`FEmusic_moods_and_genres_category`).
     */
    suspend fun fetchMoodCategoryPage(
        browseId: String,
        params: String?,
        fallbackTitle: String = "",
        forceRefresh: Boolean = false,
    ): MoodCategoryPage = withContext(Dispatchers.IO) {
        val localeSig = SettingsStore.localeSignature()
        val hideExplicit = SettingsStore.isHideExplicitEnabled()
        val cacheKey = "$localeSig|$browseId|${params.orEmpty()}"
        if (!forceRefresh) {
            moodCategoryCache[cacheKey]?.let { cached ->
                return@withContext if (hideExplicit) cached.filteredCleanOnly() else cached
            }
        }

        val body = JSONObject().put("browseId", browseId)
        if (!params.isNullOrBlank()) body.put("params", params)
        val root = postInnerTube(BROWSE_URL, body)

        val headerTitle = buildRunsText(
            root.optJSONObject("header")
                ?.optJSONObject("musicHeaderRenderer")
                ?.optJSONObject("title")
                ?.optJSONArray("runs")
        ).ifBlank { fallbackTitle }

        val sectionsJson = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: root.optJSONObject("contents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

        val parsedSections = buildList {
            if (sectionsJson != null) {
                for (i in 0 until sectionsJson.length()) {
                    val sectionObj = sectionsJson.optJSONObject(i) ?: continue
                    sectionObj.optJSONObject("musicCarouselShelfRenderer")?.let { carousel ->
                        val title = buildRunsText(
                            carousel.optJSONObject("header")
                                ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                                ?.optJSONObject("title")
                                ?.optJSONArray("runs")
                        )
                        val contents = carousel.optJSONArray("contents")
                        val items = parseMoodContainerItems(contents)
                        if (items.isNotEmpty()) {
                            add(MoodCategorySection(title = title.ifBlank { headerTitle }, items = items, isGrid = false))
                        }
                    }
                    sectionObj.optJSONObject("gridRenderer")?.let { grid ->
                        val title = buildRunsText(
                            grid.optJSONObject("header")
                                ?.optJSONObject("gridHeaderRenderer")
                                ?.optJSONObject("title")
                                ?.optJSONArray("runs")
                        )
                        val items = parseMoodContainerItems(grid.optJSONArray("items"))
                        if (items.isNotEmpty()) {
                            add(MoodCategorySection(title = title.ifBlank { headerTitle }, items = items, isGrid = true))
                        }
                    }
                    sectionObj.optJSONObject("musicShelfRenderer")?.let { shelf ->
                        val title = buildRunsText(shelf.optJSONObject("title")?.optJSONArray("runs"))
                        val items = parseMoodContainerItems(shelf.optJSONArray("contents"))
                        if (items.isNotEmpty()) {
                            add(MoodCategorySection(title = title.ifBlank { "Tracks" }, items = items, isGrid = false))
                        }
                    }
                }
            }
        }

        val page = MoodCategoryPage(
            title = headerTitle.ifBlank { fallbackTitle },
            sections = parsedSections
        )
        if (parsedSections.isNotEmpty()) {
            moodCategoryCache[cacheKey] = page
        }
        if (hideExplicit) page.filteredCleanOnly() else page
    }

    private fun MoodCategoryPage.filteredCleanOnly(): MoodCategoryPage {
        val cleanSections = sections.mapNotNull { sec ->
            val items = sec.items.filter { !it.isExplicit }
            if (items.isEmpty()) null else sec.copy(items = items)
        }
        return copy(sections = cleanSections)
    }

    private fun parseMoodContainerItems(arr: JSONArray?): List<MoodCategoryItem> {
        if (arr == null) return emptyList()
        val out = mutableListOf<MoodCategoryItem>()
        val seenIds = HashSet<String>()
        for (i in 0 until arr.length()) {
            val wrapper = arr.optJSONObject(i) ?: continue
            val item = wrapper.optJSONObject("musicTwoRowItemRenderer")?.let(::parseMoodTwoRowItem)
                ?: wrapper.optJSONObject("musicResponsiveListItemRenderer")?.let { responsive ->
                    parseTrackItem(JSONObject().put("musicResponsiveListItemRenderer", responsive))?.let { tr ->
                        MoodCategoryItem(
                            id = "song_${tr.id}",
                            title = tr.title,
                            subtitle = tr.artists,
                            thumbnailUrl = tr.thumbnailUrl,
                            type = MoodCategoryItemType.SONG,
                            videoId = tr.id,
                            isExplicit = tr.isExplicit,
                        )
                    }
                }
            if (item != null && seenIds.add(item.id)) {
                out.add(item)
            }
        }
        return out
    }

    private fun parseMoodTwoRowItem(twoRow: JSONObject): MoodCategoryItem? {
        val nav = twoRow.optJSONObject("navigationEndpoint")
        val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() }
        val watchEndpoint = nav?.optJSONObject("watchEndpoint")
        val videoId = watchEndpoint?.optString("videoId")?.takeIf { it.isNotBlank() }
        val playlistId = twoRow.optJSONObject("thumbnailOverlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchPlaylistEndpoint")
            ?.optString("playlistId")
            ?.takeIf { it.isNotBlank() }
            ?: watchEndpoint?.optString("playlistId")?.takeIf { it.isNotBlank() }

        val title = buildRunsText(twoRow.optJSONObject("title")?.optJSONArray("runs")).ifBlank { return null }
        val subtitle = buildRunsText(twoRow.optJSONObject("subtitle")?.optJSONArray("runs"))
        val thumbUrl = twoRow.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
            ?.takeIf { it.isNotBlank() }
            ?.highQualityThumbnail()

        val itemType = when {
            videoId != null && browseId == null -> MoodCategoryItemType.SONG
            browseId?.startsWith("MPREb") == true -> MoodCategoryItemType.ALBUM
            browseId?.startsWith("UC") == true -> MoodCategoryItemType.ARTIST
            else -> MoodCategoryItemType.PLAYLIST
        }
        val stableId = browseId ?: videoId ?: return null

        return MoodCategoryItem(
            id = "${itemType.name}_$stableId",
            title = title,
            subtitle = subtitle.ifBlank {
                when (itemType) {
                    MoodCategoryItemType.PLAYLIST -> "Playlist"
                    MoodCategoryItemType.ALBUM -> "Album"
                    MoodCategoryItemType.ARTIST -> "Artist"
                    MoodCategoryItemType.SONG -> "Song"
                }
            },
            thumbnailUrl = thumbUrl,
            type = itemType,
            browseId = browseId,
            videoId = videoId,
            playlistId = playlistId,
            isExplicit = twoRow.hasExplicitBadge()
        )
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

    /**
     * Resolves the best audio stream candidate matching the user's Lossless Audio and Streaming Quality settings.
     * - When Lossless Audio is ON: selects the maximum fidelity, highest-bitrate stream available (favoring M4A if equal).
     * - When Lossless Audio is OFF: selects according to chosen streaming quality:
     *     - Extreme: Maximum fidelity / highest bitrate
     *     - High: ~160 kbps target
     *     - Medium: ~128 kbps target (standard)
     *     - Low: ~48-64 kbps target (data saver)
     */
    fun pickAudioStream(
        candidates: List<AudioStream>,
        isLossless: Boolean,
        quality: String,
    ): AudioStream? {
        if (candidates.isEmpty()) return null
        if (isLossless) {
            return candidates.maxWithOrNull(
                compareBy<AudioStream> { it.averageBitrate }
                    .thenBy { if (it.format == MediaFormat.M4A) 1 else 0 }
            ) ?: candidates.maxByOrNull { it.averageBitrate }
        }
        return when (quality.trim().lowercase()) {
            "extreme", "hi-res lossless", "lossless (24-bit)", "lossless" -> {
                candidates.maxWithOrNull(
                    compareBy<AudioStream> { it.averageBitrate }
                        .thenBy { if (it.format == MediaFormat.M4A) 1 else 0 }
                ) ?: candidates.maxByOrNull { it.averageBitrate }
            }
            "high", "high quality" -> {
                candidates.minWithOrNull(
                    compareBy<AudioStream> { kotlin.math.abs(it.averageBitrate - 160) }
                        .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                )
            }
            "medium" -> {
                candidates.minWithOrNull(
                    compareBy<AudioStream> { kotlin.math.abs(it.averageBitrate - 128) }
                        .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                )
            }
            "low", "high efficiency" -> {
                candidates.minWithOrNull(
                    compareBy<AudioStream> { it.averageBitrate }
                        .thenBy { if (it.format == MediaFormat.M4A) 0 else 1 }
                ) ?: candidates.minByOrNull { it.averageBitrate }
            }
            else -> {
                candidates.maxWithOrNull(
                    compareBy<AudioStream> { it.averageBitrate }
                        .thenBy { if (it.format == MediaFormat.M4A) 1 else 0 }
                ) ?: candidates.maxByOrNull { it.averageBitrate }
            }
        }
    }

    suspend fun resolveAudioStream(
        videoId: String,
        forceLossless: Boolean? = null,
        targetQuality: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val info = getOrLoadStreamInfo(videoId)

            val audioStreams = info.audioStreams
            if (audioStreams.isNullOrEmpty()) {
                Log.w(TAG, "No audio streams found for $videoId")
                return@withContext null
            }

            val progressiveCandidates = audioStreams.filter {
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.audioTrackType != AudioTrackType.DUBBED &&
                    !it.content.isNullOrBlank()
            }
            val candidates = progressiveCandidates.ifEmpty {
                audioStreams.filter { !it.content.isNullOrBlank() }
            }

            if (candidates.isEmpty()) {
                Log.w(TAG, "No usable audio stream candidates found for $videoId")
                return@withContext null
            }

            val settings = SettingsStore.state.value
            val onCellular = appContext?.let(::isCellularConnection) == true
            val cellularMode = settings.cellularStreaming
            if (onCellular && cellularMode.equals("Off", ignoreCase = true)) {
                Log.i(TAG, "Cellular streaming blocked by user setting for $videoId")
                return@withContext null
            }
            val isLossless = forceLossless ?: if (onCellular) {
                cellularMode.equals("Lossless", ignoreCase = true)
            } else settings.losslessAudio
            val userQuality = targetQuality ?: if (onCellular) {
                when (cellularMode) {
                    "High Efficiency" -> "Low"
                    "High Quality" -> "High"
                    else -> settings.audioQuality
                }
            } else settings.audioQuality

            val chosen = pickAudioStream(candidates, isLossless, userQuality) ?: candidates.firstOrNull()
            val effectiveQuality = if (isLossless) "Lossless (Max)" else userQuality
            Log.d(TAG, "Selected stream for $videoId (lossless=$isLossless, quality=$effectiveQuality): format=${chosen?.format}, bitrate=${chosen?.averageBitrate} kbps")
            return@withContext chosen?.content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream for $videoId: ${e.message}", e)
            return@withContext null
        }
    }

    private fun isCellularConnection(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }

    suspend fun resolveAudioStreamUrl(
        videoId: String,
        forceLossless: Boolean? = null,
        targetQuality: String? = null,
    ): String? = resolveAudioStream(videoId, forceLossless, targetQuality)

    data class CacheStreamChoice(val url: String, val bitrateKbps: Int)

    /**
     * Picks the stream to store in the offline cache for the chosen cache quality.
     * - High: the highest-bitrate progressive audio stream available (any container).
     * - Medium / Low: the M4A stream closest to the target bitrate (any container as fallback); the
     *   cache manager then compresses the file if it is still above the target.
     */
    suspend fun resolveAudioStreamForCache(videoId: String, quality: String): CacheStreamChoice? =
        withContext(Dispatchers.IO) {
            try {
                val info = getOrLoadStreamInfo(videoId)
                val candidates = info.audioStreams.orEmpty().filter {
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                        it.audioTrackType != AudioTrackType.DUBBED &&
                        !it.content.isNullOrBlank()
                }
                if (candidates.isEmpty()) return@withContext null
                val target = MusicCacheManager.targetBitrateKbps(quality)
                val chosen = (
                    if (target == null) {
                        candidates.maxByOrNull { it.averageBitrate }
                    } else {
                        val m4a = candidates.filter { it.format == MediaFormat.M4A }
                        m4a.ifEmpty { candidates }.minByOrNull { kotlin.math.abs(it.averageBitrate - target) }
                    }
                    ) ?: return@withContext null
                Log.d(TAG, "Cache stream ($quality): ${chosen.format} @ ${chosen.averageBitrate} kbps")
                CacheStreamChoice(chosen.content, chosen.averageBitrate)
            } catch (e: Exception) {
                Log.w(TAG, "Cache stream resolve failed for $videoId: ${e.message}")
                null
            }
        }

    /**
     * Official YouTube Music Radio / Automix queue fetching using the official /v1/next endpoint.
     * Parses authentic playlistPanelVideoRenderer items and returns algorithmic recommendations.
     */
    suspend fun fetchRadioQueue(videoId: String): List<MusicTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<MusicTrack>()
        try {
            val root = postInnerTubeNext(videoId)
            root.walk { key, itemObj ->
                if (key == "playlistPanelVideoRenderer") {
                    parsePlaylistPanelVideo(itemObj)?.let {
                        if (it.id != videoId) tracks.add(it)
                    }
                } else if (key == "musicResponsiveListItemRenderer") {
                    val wrapper = JSONObject().put(key, itemObj)
                    parseTrackItem(wrapper)?.let {
                        if (it.id != videoId) tracks.add(it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchRadioQueue failed for videoId $videoId: ${e.message}")
        }
        tracks.distinctBy { it.id }
    }

    private fun postInnerTubeNext(videoId: String, playlistId: String? = null): JSONObject {
        val clientObj = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", "1.20240101.01.00")
            .put("hl", SettingsStore.effectiveHl())
            .put("gl", SettingsStore.effectiveGl())

        val body = JSONObject()
            .put("videoId", videoId)
            .put("isAudioOnly", true)
            .put("context", JSONObject().put("client", clientObj))

        if (!playlistId.isNullOrBlank()) {
            body.put("playlistId", playlistId)
        } else {
            body.put("playlistId", "RDAMVM$videoId")
        }

        val request = Request.Builder()
            .url(NEXT_URL)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("X-Youtube-Client-Name", "67")
            .header("X-Youtube-Client-Version", "1.20240101.01.00")
            .build()

        Net.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("InnerTube next request failed: ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            return JSONObject(text)
        }
    }

    private fun parsePlaylistPanelVideo(panel: JSONObject): MusicTrack? {
        val videoId = panel.optString("videoId")
        if (videoId.isNullOrBlank()) return null

        val title = panel.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
        val bylineRuns = panel.optJSONObject("longBylineText")?.optJSONArray("runs")
            ?: panel.optJSONObject("shortBylineText")?.optJSONArray("runs")

        var artists = ""
        var artistId: String? = null
        var album: String? = null
        var albumId: String? = null

        if (bylineRuns != null && bylineRuns.length() > 0) {
            val nonDelimiterRuns = mutableListOf<Pair<String, String?>>()
            for (r in 0 until bylineRuns.length()) {
                val runObj = bylineRuns.optJSONObject(r)
                val t = runObj?.optString("text").orEmpty().trim()
                val bId = runObj?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (t.isNotEmpty() && t != "•" && t != "·" && !t.contains("views") && !t.contains("likes")) {
                    nonDelimiterRuns.add(t to bId)
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

        val lenText = panel.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
        val durationSec = if (lenText.isNotBlank()) parseTimeString(lenText) else 0L

        val thumbUrl = panel.optJSONObject("thumbnail")
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

    private fun postInnerTubeSearch(query: String, params: String? = null): JSONObject {
        val body = JSONObject().put("query", query)
        if (params != null) body.put("params", params)
        // Search runs anonymously so account delegation or expired cookies never truncate results.
        return postInnerTube(SEARCH_URL, body, anonymous = true)
    }

    private fun postInnerTube(
        url: String,
        body: JSONObject,
        anonymous: Boolean = false,
    ): JSONObject {
        val useAuth = !anonymous && ArtistInnertubeClient.hasSession()
        val firstResult = executeInnerTubeCall(url, body, authenticated = useAuth)
        val firstJson = firstResult.second
        val hasErrorPayload = firstJson?.has("error") == true
        if (useAuth && (!firstResult.first || firstJson == null || hasErrorPayload)) {
            Log.w(TAG, "InnerTube authenticated request failed on $url; retrying anonymously")
            val fallback = executeInnerTubeCall(url, body, authenticated = false)
            return fallback.second ?: JSONObject()
        }
        return firstJson ?: JSONObject()
    }

    private fun executeInnerTubeCall(
        url: String,
        body: JSONObject,
        authenticated: Boolean,
    ): Pair<Boolean, JSONObject?> {
        val hl = SettingsStore.effectiveHl()
        val gl = SettingsStore.effectiveGl()
        val clientVersion = ArtistInnertubeClient.currentClientVersion()
        val clientObj = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", clientVersion)
            .put("hl", hl)
            .put("gl", gl)

        val userObj = if (authenticated) {
            ArtistInnertubeClient.buildUserContextObject()
        } else {
            JSONObject().put("lockedSafetyMode", false)
        }

        val contextObj = JSONObject()
            .put("client", clientObj)
            .put("user", userObj)
            .put("request", JSONObject().put("useSsl", true))

        val payload = JSONObject(body.toString())
            .put("context", contextObj)

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_TYPE))
            .header("Accept-Language", "$hl,en-US;q=0.8,en;q=0.7")
            .header("X-Origin", "https://music.youtube.com")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("X-YouTube-Client-Name", "67")
            .header("X-YouTube-Client-Version", clientVersion)
        if (authenticated) {
            ArtistInnertubeClient.applyAuthHeaders(reqBuilder)
        }
        val request = reqBuilder.build()

        return try {
            Net.client.newCall(request).execute().use { response ->
                GoogleAuthStore.absorbSetCookies(response.headers("Set-Cookie"))
                val text = response.body?.string().orEmpty()
                val json = text.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
                response.isSuccessful to json
            }
        } catch (e: Throwable) {
            Log.w(TAG, "InnerTube call error on $url (auth=$authenticated): ${e.message}")
            false to null
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
            watchPlaylistId = watchPlaylistId,
            isExplicit = responsive.hasExplicitBadge()
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
            val title = responsive.extractColumnText(0)
            val subRuns = flexColumns?.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

            var artists = ""
            var artistId: String? = null
            var album: String? = null
            var albumId: String? = null
            var durationSec = 0L

            val metaParts = mutableListOf<Pair<String, String?>>()
            for ((segText, segId) in extractSegmentedRuns(subRuns)) {
                when {
                    segText.matches(Regex("\\d+:\\d+(:\\d+)?")) -> durationSec = parseTimeString(segText)
                    segText.equals("Song", ignoreCase = true) || segText.equals("Video", ignoreCase = true) -> Unit
                    segText.endsWith("plays", ignoreCase = true) || segText.endsWith("views", ignoreCase = true) -> Unit
                    else -> metaParts.add(segText to segId)
                }
            }
            if (metaParts.isNotEmpty()) {
                artists = metaParts[0].first
                artistId = metaParts[0].second
                if (metaParts.size > 1) {
                    album = metaParts[1].first
                    albumId = metaParts[1].second
                }
            }
            if (artists.isBlank() && fallbackArtist.isNotBlank()) {
                artists = fallbackArtist
            }

            val thumbUrl = JSONObject().put("thumbnail", responsive.optJSONObject("thumbnail"))
                .extractBestThumbnail()
                ?: fallbackThumbnailUrl

            return MusicTrack(
                id = videoId,
                title = title.ifBlank { "Unknown Title" },
                artists = artists.ifBlank { "Various Artists" },
                artistId = artistId,
                album = album,
                albumId = albumId,
                thumbnailUrl = thumbUrl,
                durationSec = durationSec,
            isExplicit = responsive.hasExplicitBadge()
            )
        }

        itemObj.optJSONObject("musicTwoRowItemRenderer")?.let { twoRow ->
            val watchEndpoint = twoRow.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
            val videoId = watchEndpoint?.optString("videoId")
            if (videoId.isNullOrBlank()) return null

            val titleRuns = twoRow.optJSONObject("title")?.optJSONArray("runs")
            val title = buildString {
                if (titleRuns != null) {
                    for (i in 0 until titleRuns.length()) {
                        append(titleRuns.optJSONObject(i)?.optString("text").orEmpty())
                    }
                }
            }.trim()

            val subtitleRuns = twoRow.optJSONObject("subtitle")?.optJSONArray("runs")
            val firstSeg = extractSegmentedRuns(subtitleRuns).firstOrNull()
            val artists = firstSeg?.first.orEmpty().ifBlank { fallbackArtist }
            val artistId = firstSeg?.second

            val thumbUrl = twoRow.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
                ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
                ?.highQualityThumbnail()
                ?: fallbackThumbnailUrl

            return MusicTrack(
                id = videoId,
                title = title.ifBlank { "Unknown Title" },
                artists = artists.ifBlank { "Various Artists" },
                artistId = artistId,
                thumbnailUrl = thumbUrl,
                isExplicit = twoRow.hasExplicitBadge()
            )
        }

        return null
    }

    private fun extractMusicSuggestions(root: JSONObject): List<String> {
        val out = mutableListOf<String>()
        root.walk { key, obj ->
            when (key) {
                "searchSuggestionRenderer" -> {
                    val navQuery = obj.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("searchEndpoint")
                        ?.optString("query")
                        ?.trim()
                    val runsText = buildRunsText(obj.optJSONObject("suggestion")?.optJSONArray("runs"))
                    val candidate = navQuery?.takeIf { it.isNotEmpty() } ?: runsText
                    if (candidate.isNotBlank()) out.add(candidate)
                }
                "musicResponsiveListItemRenderer" -> {
                    val title = obj.extractColumnText(0)
                    if (title.isNotBlank()) out.add(title)
                }
            }
        }
        return out
    }

    private fun buildRunsText(runs: JSONArray?): String {
        if (runs == null) return ""
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.trim()
    }

    private fun Any?.hasExplicitBadge(): Boolean = when (this) {
        is String -> this == "MUSIC_EXPLICIT_BADGE"
        is JSONArray -> (0 until length()).any { opt(it).hasExplicitBadge() }
        is JSONObject -> {
            val keys = keys()
            var matched = false
            while (keys.hasNext()) {
                if (opt(keys.next()).hasExplicitBadge()) {
                    matched = true
                    break
                }
            }
            matched
        }
        else -> false
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
