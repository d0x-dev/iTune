package com.darkxvenom.itune.data.artist

import com.darkxvenom.itune.data.model.ArtistNameIndex
import com.darkxvenom.itune.data.model.ArtistPage
import com.darkxvenom.itune.data.model.ArtistRef
import com.darkxvenom.itune.data.model.BrowseItem
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.HomeShelf
import com.darkxvenom.itune.data.model.LibraryState
import com.darkxvenom.itune.data.model.SearchFilter
import com.darkxvenom.itune.data.model.SearchResult
import com.darkxvenom.itune.data.model.ShelfItem
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.SubscriptionState
import com.darkxvenom.itune.data.model.WatchTarget
import com.darkxvenom.itune.data.model.completeArtistCredits
import com.darkxvenom.itune.data.model.shelfLayoutFor
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Locale

object ArtistInnertubeParser {

    data class SearchPage(val rows: List<SearchResult>, val continuation: String?)
    data class LibraryItemPage(val items: List<ShelfItem>, val continuation: String?)
    data class PlaylistShelfPage(val songs: List<Song>, val suggested: List<Song>, val continuation: String?)
    data class BrowseHeader(val title: String, val subtitle: String, val thumbnailUrl: String?)

    private data class Credits(
        val artistId: String? = null,
        val artistName: String? = null,
        val artists: List<ArtistRef> = emptyList(),
        val albumId: String? = null,
        val albumName: String? = null,
    )

    fun parseSearchPage(response: JSONObject, includeVideos: Boolean = false): SearchPage {
        val topResults: List<SearchResult> = if (includeVideos) emptyList() else {
            collectRenderers(response, "musicCardShelfRenderer")
                .mapNotNull { card ->
                    parseCardShelfSong(card)?.let(SearchResult::TopTrack)
                        ?: parseCardShelfBrowse(card)?.let(SearchResult::Browse)
                }
        }
        val rows = collectRenderers(response, "musicResponsiveListItemRenderer")
        val cardCredits: List<Pair<JSONObject, Credits>> = if (includeVideos) emptyList() else {
            collectRenderers(response, "musicCardShelfRenderer").flatMap { card ->
                val credit = cardShelfCredit(card) ?: return@flatMap emptyList()
                collectRenderers(card, "musicResponsiveListItemRenderer").map { it to credit }
            }
        }

        val seen = HashSet<String>()
        val parsed = buildList {
            topResults.forEach { result ->
                when (result) {
                    is SearchResult.TopTrack -> {
                        if (!result.song.isVideo && seen.add("v:${result.song.videoId}")) add(result)
                    }
                    is SearchResult.Browse -> {
                        if (seen.add("b:${result.item.browseId}")) add(result)
                    }
                    is SearchResult.Track -> Unit
                }
            }
            rows.forEach { renderer ->
                val browse = parseBrowseItem(renderer)
                if (browse != null) {
                    if (seen.add("b:${browse.browseId}")) add(SearchResult.Browse(browse))
                } else {
                    val fallback = cardCredits.firstOrNull { it.first === renderer }?.second
                    parseResponsiveListItem(renderer, fallback ?: Credits())?.let { song ->
                        if (song.isVideo == includeVideos && seen.add("v:${song.videoId}")) {
                            add(SearchResult.Track(song))
                        }
                    }
                }
            }
        }
        return SearchPage(parsed, continuationToken(response))
    }

    fun parseArtistPage(response: JSONObject): ArtistPage {
        val sections = response.o("contents")
            .o("singleColumnBrowseResultsRenderer").a("tabs").firstObj()
            .o("tabRenderer").o("content").o("sectionListRenderer").a("contents")
            .toObjList()

        val songs = mutableListOf<Song>()
        var moreSongs: String? = null
        val shelves = mutableListOf<HomeShelf>()
        val header = response.opt("header")
        val credit = Credits(artistName = artistName(header))

        sections.forEach { section ->
            section.o("musicShelfRenderer")?.let { shelf ->
                shelf.a("contents").toObjList().forEach { row ->
                    parseResponsiveListItem(row.o("musicResponsiveListItemRenderer"), credit)
                        ?.let(songs::add)
                }
                if (moreSongs == null) {
                    moreSongs = shelf.o("title").a("runs").firstObj()
                        .o("navigationEndpoint").o("browseEndpoint").s("browseId")?.takeIf { it.isNotBlank() }
                }
            }
            section.o("musicCarouselShelfRenderer")?.let { carousel ->
                val carouselHeader = carousel.o("header").o("musicCarouselShelfBasicHeaderRenderer")
                val title = carouselHeader.o("title").runs()
                val items = carousel.a("contents").toObjList().mapNotNull {
                    parseTwoRowItem(it.o("musicTwoRowItemRenderer"))
                }.filter { it.browseId != null || it.isVideo }
                val moreEndpoint = carouselHeader.o("title").a("runs").toObjList().firstNotNullOfOrNull {
                    it.o("navigationEndpoint").o("browseEndpoint")
                }
                    ?: carouselHeader.o("moreContentButton").o("buttonRenderer")
                        .o("navigationEndpoint").o("browseEndpoint")
                    ?: carouselHeader.o("moreContentButton").o("musicMoreContentButtonRenderer")
                        .o("navigationEndpoint").o("browseEndpoint")
                    ?: carouselHeader.a("endIcons").toObjList().firstNotNullOfOrNull {
                        it.o("musicNavigationButtonRenderer").o("clickCommand").o("browseEndpoint")
                            ?: it.o("musicNavigationButtonRenderer").o("navigationEndpoint").o("browseEndpoint")
                    }
                    ?: carouselHeader.o("navigationEndpoint").o("browseEndpoint")
                val moreBrowseId = moreEndpoint.s("browseId")?.takeIf { it.isNotBlank() }
                val moreParams = moreEndpoint.s("params")?.takeIf { it.isNotBlank() }
                if (title.isNotBlank() && items.isNotEmpty()) {
                    shelves += HomeShelf(
                        title = title,
                        items = items,
                        moreBrowseId = moreBrowseId,
                        moreParams = moreParams,
                        layout = shelfLayoutFor(title, items, fromList = false),
                    )
                }
            }
        }
        return ArtistPage(
            songs = songs,
            moreSongsBrowseId = moreSongs,
            sections = shelves,
            thumbnailUrl = artistThumbnail(header),
            name = credit.artistName,
            description = parseDescription(response),
            subscriberCountText = subscriberCount(header),
            monthlyListenerCount = monthlyListeners(header),
            subscription = subscription(header),
            radio = headerWatchTarget(header, "startRadioButton"),
            shuffle = headerWatchTarget(header, "playButton"),
        )
    }

    private fun headerWatchTarget(header: Any?, button: String): WatchTarget? {
        val endpoint = header.o("musicImmersiveHeaderRenderer").o(button)
            .o("buttonRenderer").o("navigationEndpoint").o("watchEndpoint")
            ?: return null
        val playlistId = endpoint.s("playlistId")?.takeIf { it.isNotBlank() } ?: return null
        return WatchTarget(
            playlistId = playlistId,
            videoId = endpoint.s("videoId")?.takeIf { it.isNotBlank() },
            params = endpoint.s("params")?.takeIf { it.isNotBlank() }?.let {
                runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
            },
        )
    }

    private fun subscription(header: Any?): SubscriptionState? {
        val immersive = header.o("musicImmersiveHeaderRenderer") ?: return null
        return listOfNotNull(
            immersive.o("subscriptionButton2").o("subscribeButtonRenderer"),
            immersive.o("subscriptionButton").o("subscribeButtonRenderer"),
        ).firstNotNullOfOrNull { button ->
            val subscribed = if (button.has("subscribed")) button.optBoolean("subscribed") else return@firstNotNullOfOrNull null
            val channelId = button.s("channelId")?.takeIf { it.isNotBlank() }
                ?: button.a("serviceEndpoints").toObjList().firstNotNullOfOrNull { endpoint ->
                    endpoint.o("subscribeEndpoint").a("channelIds")?.optString(0)?.takeIf { it.isNotBlank() }
                }
                ?: return@firstNotNullOfOrNull null
            SubscriptionState(channelId = channelId, subscribed = subscribed)
        }
    }

    private fun subscriberCount(header: Any?): String? {
        val immersive = header.o("musicImmersiveHeaderRenderer") ?: return null
        val button2 = immersive.o("subscriptionButton2").o("subscribeButtonRenderer")
        val button1 = immersive.o("subscriptionButton").o("subscribeButtonRenderer")
        return button2.o("subscriberCountWithSubscribeText").firstRunText()
            ?: button1.o("longSubscriberCountText").firstRunText()
            ?: button1.o("shortSubscriberCountText").firstRunText()
    }

    private fun monthlyListeners(header: Any?): String? =
        header.o("musicImmersiveHeaderRenderer").o("monthlyListenerCount").firstRunText()

    private fun artistName(header: Any?): String? {
        val renderer = header.o("musicImmersiveHeaderRenderer")
            ?: header.o("musicVisualHeaderRenderer")
            ?: return null
        return renderer.o("title").runs().takeIf { it.isNotBlank() }
    }

    private fun artistThumbnail(header: Any?): String? {
        if (header == null) return null
        val immersive = header.o("musicImmersiveHeaderRenderer")
        val visual = header.o("musicVisualHeaderRenderer")
        val renderer = (
            immersive.o("thumbnail")
                ?: visual.o("foregroundThumbnail")
                ?: visual.o("thumbnail")
            ).o("musicThumbnailRenderer")
            ?: collectRenderers(header, "musicThumbnailRenderer").firstOrNull()
        return renderer.o("thumbnail").a("thumbnails").best()
    }

    fun collectSongsDeep(root: JSONObject, show: String? = null): List<Song> {
        val out = LinkedHashMap<String, Song>()
        val pageCredit = pageCredit(root)
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("musicResponsiveListItemRenderer")?.let { renderer ->
                        parseResponsiveListItem(renderer, pageCredit)?.let { out[it.videoId] = it }
                    }
                    node.optJSONObject("musicMultiRowListItemRenderer")?.let { renderer ->
                        parseMultiRowListItem(renderer, show)?.let { out[it.videoId] = it }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return out.values.toList()
    }

    fun podcastShow(root: JSONObject): String? {
        if (collectRenderers(root, "musicMultiRowListItemRenderer").isEmpty()) return null
        return parseBrowseHeader(root)?.title
    }

    fun parsePlaylistShelf(root: JSONObject): PlaylistShelfPage? {
        val scope: Any = root.o("continuationContents")
            ?: root.o("contents").o("twoColumnBrowseResultsRenderer").o("secondaryContents")
            ?: return null
        val playlistScope = scope.o("musicPlaylistShelfContinuation")
            ?: collectRenderers(scope, "musicPlaylistShelfRenderer").firstOrNull()
        val suggestionShelves = collectRenderers(scope, "musicShelfRenderer")
            .filter { it.o("title").runs() == "Suggestions" }
        if (playlistScope == null && suggestionShelves.isEmpty()) return null

        val pageCredit = pageCredit(root)
        val songs = playlistScope?.let { playlist ->
            collectRenderers(playlist, "musicResponsiveListItemRenderer")
                .mapNotNull { parseResponsiveListItem(it, pageCredit) }
                .distinctBy { it.videoId }
        }.orEmpty()
        val knownSongs = songs.mapTo(HashSet()) { it.videoId }
        val suggested = suggestionShelves
            .flatMap { collectRenderers(it, "musicResponsiveListItemRenderer") }
            .mapNotNull { parseResponsiveListItem(it, pageCredit) }
            .filterNot { it.videoId in knownSongs }
            .distinctBy { it.videoId }
        val token = playlistScope?.let { continuationToken(it) }
        return PlaylistShelfPage(songs, suggested, token)
    }

    fun parseLibraryItemPage(root: JSONObject): LibraryItemPage {
        val out = LinkedHashMap<String, ShelfItem>()
        collectRenderers(root, "musicTwoRowItemRenderer").forEach { renderer ->
            val item = parseTwoRowItem(renderer) ?: return@forEach
            val key = item.browseId ?: item.videoId ?: return@forEach
            out.putIfAbsent(key, item)
        }
        collectRenderers(root, "musicResponsiveListItemRenderer").forEach { renderer ->
            val item = parseBrowseItem(renderer) ?: return@forEach
            out.putIfAbsent(
                item.browseId,
                ShelfItem(item.title, item.subtitle, item.thumbnailUrl, null, item.browseId),
            )
        }
        return LibraryItemPage(out.values.toList(), continuationToken(root))
    }

    fun continuationToken(root: Any): String? {
        val endpoint = collectRenderers(root, "continuationItemRenderer").firstOrNull()
            .o("continuationEndpoint")
        val cmd = endpoint.o("continuationCommand")
            ?: endpoint.o("commandExecutorCommand").a("commands").toObjList()
                .firstNotNullOfOrNull { it.o("continuationCommand") }
        cmd.s("token")?.takeIf { it.isNotBlank() }?.let { return it }
        return collectRenderers(root, "nextContinuationData").firstOrNull()
            .s("continuation")?.takeIf { it.isNotBlank() }
    }

    private fun parseBrowseItem(renderer: JSONObject): BrowseItem? {
        val endpoint = renderer.o("navigationEndpoint").o("browseEndpoint") ?: return null
        val browseId = endpoint.s("browseId")?.takeIf { it.isNotBlank() } ?: return null
        val pageType = endpoint.o("browseEndpointContextSupportedConfigs")
            .o("browseEndpointContextMusicConfig").s("pageType").orEmpty()

        val columns = renderer.a("flexColumns").toObjList()
        val title = columns.getOrNull(0)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        if (title.isBlank()) return null

        val subtitle = columns.getOrNull(1)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        if (VIDEO_WORD.containsMatchIn(title) || VIDEO_WORD.containsMatchIn(subtitle)) return null

        return BrowseItem(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = renderer.o("thumbnail").o("musicThumbnailRenderer")
                .o("thumbnail").a("thumbnails").best(),
            type = when {
                "ALBUM" in pageType -> BrowseType.ALBUM
                "ARTIST" in pageType -> BrowseType.ARTIST
                "PLAYLIST" in pageType -> BrowseType.PLAYLIST
                else -> BrowseType.OTHER
            },
        )
    }

    private fun parseResponsiveListItem(
        renderer: JSONObject?,
        fallback: Credits = Credits(),
    ): Song? {
        if (renderer == null) return null
        val videoId = renderer.o("playlistItemData").s("videoId")?.takeIf { it.isNotBlank() }
            ?: renderer.o("overlay")
                .o("musicItemThumbnailOverlayRenderer").o("content")
                .o("musicPlayButtonRenderer").o("playNavigationEndpoint")
                .o("watchEndpoint").s("videoId")?.takeIf { it.isNotBlank() }
            ?: return null

        val columns = renderer.a("flexColumns").toObjList()
        val title = columns.getOrNull(0)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").runs()
        if (title.isBlank()) return null

        val subtitleRuns = columns.getOrNull(1)
            .o("musicResponsiveListItemFlexColumnRenderer").o("text").a("runs").toObjList()
        val subtitle = subtitleRuns.joinToString("") { it.s("text").orEmpty() }
        val parts = subtitle.split(" • ").filter { it.isNotBlank() }
        val duration = parts.lastOrNull()?.takeIf { it.matches(DURATION) }
            ?: renderer.a("fixedColumns").toObjList().firstNotNullOfOrNull { column ->
                column.o("musicResponsiveListItemFixedColumnRenderer")
                    .o("text").runs().takeIf { it.matches(DURATION) }
            }
        val rowType = parts.firstOrNull { it.lowercase(Locale.ROOT) in TYPE_WORDS }?.lowercase(Locale.ROOT)
        val artist = parts.firstOrNull {
            !it.matches(DURATION) && it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY)
        }

        val allColumnRuns = columns.flatMap {
            it.o("musicResponsiveListItemFlexColumnRenderer").o("text").a("runs").toObjList()
        }
        val credits = creditsOf(allColumnRuns)
        val creditedArtists = artistNamesFromRuns(subtitleRuns)
        val episode = rowType == "episode" || renderer.isPodcastEpisode()
        val show = if (episode) showNameFromRuns(subtitleRuns) else null
        val thumbnails = renderer.o("thumbnail").o("musicThumbnailRenderer")
            .o("thumbnail").a("thumbnails")

        return Song(
            videoId = videoId,
            title = title,
            artist = creditedArtists
                ?: credits.artistName?.takeIf { it.isNotBlank() }
                ?: show
                ?: artist
                ?: fallback.artistName
                ?: "Unknown artist",
            thumbnailUrl = thumbnails.best(),
            durationText = duration,
            artistId = credits.artistId ?: fallback.artistId,
            artists = completeArtistCredits(artist.orEmpty(), credits.artists.ifEmpty { fallback.artists }),
            albumId = credits.albumId ?: fallback.albumId,
            albumName = credits.albumName ?: fallback.albumName,
            setVideoId = renderer.o("playlistItemData").s("playlistSetVideoId")?.takeIf { it.isNotBlank() },
            isVideo = !episode && (rowType == "video" || thumbnails.isNotSquare()),
            isExplicit = renderer.hasExplicitBadge(),
        )
    }

    private fun parseMultiRowListItem(renderer: JSONObject?, show: String?): Song? {
        if (renderer == null) return null
        val videoId = renderer.o("onTap").o("watchEndpoint").s("videoId")?.takeIf { it.isNotBlank() }
            ?: renderer.o("overlay")
                .o("musicItemThumbnailOverlayRenderer").o("content")
                .o("musicPlayButtonRenderer").o("playNavigationEndpoint")
                .o("watchEndpoint").s("videoId")?.takeIf { it.isNotBlank() }
            ?: return null
        val title = renderer.o("title").runs()
        if (title.isBlank()) return null
        return Song(
            videoId = videoId,
            title = title,
            artist = renderer.o("secondTitle").runs().takeIf { it.isNotBlank() }
                ?: show
                ?: "Unknown artist",
            thumbnailUrl = renderer.o("thumbnail").o("musicThumbnailRenderer")
                .o("thumbnail").a("thumbnails").best(),
            durationText = episodeRuntime(
                renderer.o("playbackProgress").o("musicPlaybackProgressRenderer")
                    .o("durationText").runs(),
            ),
            isExplicit = renderer.hasExplicitBadge(),
        )
    }

    private fun episodeRuntime(text: String): String? {
        var seconds = 0L
        RUNTIME_PART.findAll(text).forEach { part ->
            val count = part.groupValues[1].toLong()
            seconds += when (part.groupValues[2].lowercase(Locale.ROOT).first()) {
                'h' -> count * 3_600
                'm' -> count * 60
                else -> count
            }
        }
        if (seconds == 0L) return null
        val hours = seconds / 3_600
        val minutes = seconds % 3_600 / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, secs)
        }
    }

    private fun showNameFromRuns(runs: List<JSONObject>): String? =
        runs.firstOrNull { run ->
            run.o("navigationEndpoint").o("browseEndpoint")
                .o("browseEndpointContextSupportedConfigs")
                .o("browseEndpointContextMusicConfig").s("pageType")
                .orEmpty().contains("PODCAST_SHOW")
        }.s("text")?.trim()?.takeIf { it.isNotBlank() }

    private fun parseCardShelfSong(renderer: JSONObject): Song? {
        val videoId = renderer.o("onTap").o("watchEndpoint").s("videoId")?.takeIf { it.isNotBlank() } ?: return null
        val title = renderer.o("title").runs()
        if (title.isBlank()) return null

        val subtitleRuns = renderer.o("subtitle").a("runs").toObjList()
        val subtitle = subtitleRuns.joinToString("") { it.s("text").orEmpty() }
        val parts = subtitle.split(" • ").filter { it.isNotBlank() }
        val duration = parts.lastOrNull()?.takeIf { it.matches(DURATION) }
        val rowType = parts.firstOrNull { it.lowercase(Locale.ROOT) in TYPE_WORDS }?.lowercase(Locale.ROOT)
        val credits = creditsOf(subtitleRuns)
        val creditedArtists = artistNamesFromRuns(subtitleRuns)
        val artist = parts.firstOrNull {
            !it.matches(DURATION) && it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY)
        }
        val thumbnails = renderer.o("thumbnail").o("musicThumbnailRenderer")
            .o("thumbnail").a("thumbnails")

        return Song(
            videoId = videoId,
            title = title,
            artist = creditedArtists
                ?: credits.artistName?.takeIf { it.isNotBlank() }
                ?: artist
                ?: "Unknown artist",
            thumbnailUrl = thumbnails.best(),
            durationText = duration,
            artistId = credits.artistId,
            artists = completeArtistCredits(artist.orEmpty(), credits.artists),
            albumId = credits.albumId,
            albumName = credits.albumName,
            isVideo = rowType == "video" || thumbnails.isNotSquare(),
            isExplicit = renderer.opt("subtitleBadges").hasExplicitBadge(),
        )
    }

    private fun cardShelfCredit(card: JSONObject): Credits? {
        val endpoint = card.o("onTap").o("browseEndpoint") ?: return null
        val pageType = endpoint.o("browseEndpointContextSupportedConfigs")
            .o("browseEndpointContextMusicConfig").s("pageType").orEmpty()
        if ("ARTIST" !in pageType) return null
        val name = card.o("title").runs().takeIf { it.isNotBlank() } ?: return null
        return Credits(artistId = endpoint.s("browseId")?.takeIf { it.isNotBlank() }, artistName = name)
    }

    private fun parseCardShelfBrowse(renderer: JSONObject): BrowseItem? {
        val endpoint = renderer.o("onTap").o("browseEndpoint") ?: return null
        val browseId = endpoint.s("browseId")?.takeIf { it.isNotBlank() } ?: return null
        val pageType = endpoint.o("browseEndpointContextSupportedConfigs")
            .o("browseEndpointContextMusicConfig").s("pageType").orEmpty()
        if ("NON_MUSIC_AUDIO_TRACK" in pageType) return null
        val title = renderer.o("title").runs()
        if (title.isBlank()) return null
        val subtitle = renderer.o("subtitle").runs()
        if (VIDEO_WORD.containsMatchIn(title) || VIDEO_WORD.containsMatchIn(subtitle)) return null

        return BrowseItem(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = renderer.o("thumbnail").o("musicThumbnailRenderer")
                .o("thumbnail").a("thumbnails").best(),
            type = when {
                "ALBUM" in pageType -> BrowseType.ALBUM
                "ARTIST" in pageType -> BrowseType.ARTIST
                "PLAYLIST" in pageType -> BrowseType.PLAYLIST
                else -> BrowseType.OTHER
            },
        )
    }

    private fun creditsOf(runs: List<JSONObject>): Credits {
        var credits = Credits()
        val artists = mutableListOf<ArtistRef>()
        runs.forEach { run ->
            val browse = run.o("navigationEndpoint").o("browseEndpoint")
            val id = browse.s("browseId")?.takeIf { it.isNotBlank() } ?: return@forEach
            val pageType = browse.o("browseEndpointContextSupportedConfigs")
                .o("browseEndpointContextMusicConfig").s("pageType").orEmpty()
            if ("ARTIST" !in pageType && "ALBUM" !in pageType) return@forEach
            val name = run.s("text").orEmpty()
            if ("ARTIST" in pageType) {
                if (name.isNotBlank()) {
                    artists += ArtistRef(name = name, browseId = id)
                }
                if (credits.artistId == null) {
                    credits = credits.copy(artistId = id, artistName = name)
                }
            } else if (credits.albumId == null) {
                credits = credits.copy(albumId = id, albumName = name)
            }
        }
        ArtistNameIndex.record(artists)
        return if (artists.isEmpty()) credits else credits.copy(artists = artists)
    }

    private fun artistNamesFromRuns(runs: List<JSONObject>): String? {
        val groups = mutableListOf<MutableList<JSONObject>>(mutableListOf())
        runs.forEach { run ->
            if (run.s("text")?.trim() == "•") {
                groups.add(mutableListOf())
            } else {
                groups.last() += run
            }
        }
        val artistGroup = groups.firstOrNull { group ->
            group.any { run ->
                val pageType = run.o("navigationEndpoint").o("browseEndpoint")
                    .o("browseEndpointContextSupportedConfigs")
                    .o("browseEndpointContextMusicConfig").s("pageType").orEmpty()
                "ARTIST" in pageType
            }
        } ?: return null
        return artistGroup.mapIndexedNotNull { index, run ->
            if (index % 2 != 0) return@mapIndexedNotNull null
            run.s("text")?.trim()?.takeIf { it.isNotBlank() }
        }.distinct().joinToString(", ").ifBlank { null }
    }

    private fun pageCredit(root: Any): Credits {
        val header = HEADER_RENDERERS.firstNotNullOfOrNull {
            collectRenderers(root, it).firstOrNull()
        } ?: return Credits()
        val lines = HEADER_CREDIT_LINES.map { header.o(it).a("runs").toObjList() }
        val parts = lines.flatMap { line ->
            line.joinToString("") { it.s("text").orEmpty() }.split(" • ", " · ").map(String::trim)
        }
        if (parts.none { it.lowercase(Locale.ROOT) in RELEASE_WORDS }) return Credits()

        val credits = creditsOf(lines.flatten())
        val creditedArtists = lines.firstNotNullOfOrNull(::artistNamesFromRuns)
        if (creditedArtists != null) return credits.copy(artistName = creditedArtists)
        val name = parts.firstOrNull {
            it.isNotBlank() && it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY) &&
                !it.matches(YEAR) && !it.matches(DURATION)
        }
        return credits.copy(artistName = name)
    }

    fun parseBrowseHeader(root: Any): BrowseHeader? {
        val header = HEADER_RENDERERS.firstNotNullOfOrNull {
            collectRenderers(root, it).firstOrNull()
        } ?: return null
        val title = header.o("title").runs()
        if (title.isBlank()) return null
        val subtitle = HEADER_CREDIT_LINES
            .map { header.o(it).runs() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" • ")
        return BrowseHeader(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = collectRenderers(header, "musicThumbnailRenderer").firstOrNull()
                .o("thumbnail").a("thumbnails").best(),
        )
    }

    fun parseAlbumPlaylistId(root: Any): String? {
        val header = HEADER_RENDERERS.firstNotNullOfOrNull {
            collectRenderers(root, it).firstOrNull()
        }
        if (header != null) {
            collectRenderers(header, "musicPlayButtonRenderer")
                .firstNotNullOfOrNull { it.playlistIdFromPlayAction() }
                ?.let { return it }
            collectRenderers(header, "buttonRenderer")
                .firstNotNullOfOrNull { it.playlistIdFromPlayAction() }
                ?.let { return it }
        }
        val canonical = root.o("microformat").o("microformatDataRenderer").s("urlCanonical")
            ?: return null
        return canonical.substringAfter("list=", missingDelimiterValue = "")
            .substringBefore('&')
            .takeIf { it.isNotBlank() }
    }

    private fun Any?.playlistIdFromPlayAction(): String? {
        val endpoint = o("playNavigationEndpoint")
            ?: o("navigationEndpoint")
            ?: this
        return endpoint.o("watchPlaylistEndpoint").s("playlistId")?.takeIf { it.isNotBlank() }
            ?: endpoint.o("watchEndpoint").s("playlistId")?.takeIf { it.isNotBlank() }
    }

    fun parseDescription(root: Any): String? {
        val shelf = collectRenderers(root, "musicDescriptionShelfRenderer")
            .firstOrNull()?.o("description").runs()
        if (shelf.isNotBlank()) return shelf
        return (HEADER_RENDERERS + "musicImmersiveHeaderRenderer")
            .firstNotNullOfOrNull { name ->
                collectRenderers(root, name).firstOrNull()
                    ?.o("description").runs().takeIf { it.isNotBlank() }
            }
    }

    fun parseWatchQueue(root: Any, preferAudio: Boolean = false, preferVideo: Boolean = false): List<Song> {
        val out = LinkedHashMap<String, Song>()
        val replacements = HashMap<String, JSONObject>()
        val dropped = HashSet<String>()
        if (preferAudio || preferVideo) {
            collectRenderers(root, "playlistPanelVideoWrapperRenderer").forEach { wrapper ->
                val halves = wrapperRenderers(wrapper)
                val primary = halves.firstOrNull() ?: return@forEach
                val audio = halves.firstOrNull { if (preferVideo) !it.isAudioTrack() else it.isAudioTrack() }
                    ?: return@forEach
                val primaryId = primary.s("videoId")?.takeIf { it.isNotBlank() } ?: return@forEach
                replacements[primaryId] = audio
                halves.filter { it !== primary }.mapNotNullTo(dropped) { it.s("videoId") }
            }
        }
        collectRenderers(root, "playlistPanelVideoRenderer").forEach { found ->
            val foundId = found.s("videoId")?.takeIf { it.isNotBlank() } ?: return@forEach
            if (foundId in dropped) return@forEach
            val song = panelSong(replacements[foundId] ?: found) ?: return@forEach
            out[song.videoId] = song
        }
        return out.values.toList()
    }

    private fun wrapperRenderers(wrapper: JSONObject): List<JSONObject> = buildList {
        wrapper.o("primaryRenderer").o("playlistPanelVideoRenderer")?.let(::add)
        wrapper.a("counterpart").toObjList().forEach {
            it.o("counterpartRenderer").o("playlistPanelVideoRenderer")?.let(::add)
        }
    }

    private fun JSONObject.isAudioTrack(): Boolean =
        findString("musicVideoType") == "MUSIC_VIDEO_TYPE_ATV"

    private fun panelSong(renderer: JSONObject): Song? {
        val videoId = renderer.s("videoId")?.takeIf { it.isNotBlank() } ?: return null
        val title = renderer.o("title").runs()
        if (title.isBlank()) return null
        val bylineRuns = renderer.o("longBylineText").a("runs").toObjList()
        val byline = bylineRuns.map { it.s("text").orEmpty() }
        val artist = byline.takeWhile { !it.contains("•") }.joinToString("").trim()
        val credits = creditsOf(bylineRuns)
        return Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = renderer.o("thumbnail").a("thumbnails").best(),
            durationText = renderer.o("lengthText").runs().takeIf { it.isNotBlank() },
            artistId = credits.artistId,
            artists = completeArtistCredits(artist, credits.artists),
            albumId = credits.albumId,
            albumName = credits.albumName,
            isVideo = byline.any { it.contains("views", ignoreCase = true) },
            isExplicit = renderer.hasExplicitBadge(),
        )
    }

    fun parseLibraryState(root: Any): LibraryState? {
        val buttons = collectRenderers(root, "musicResponsiveHeaderRenderer")
            .firstOrNull()
            .a("buttons")
            .toObjList()
        val save = buttons.firstNotNullOfOrNull { it.o("toggleButtonRenderer")?.takeIf { b -> b.isSaveToggle } }
            ?: return null
        if (save.optBoolean("isDisabled", false) || save.s("isDisabled") == "true") return null
        val play = buttons.firstNotNullOfOrNull { it.o("musicPlayButtonRenderer").o("playNavigationEndpoint") }
        return LibraryState(
            playlistId = play.o("watchPlaylistEndpoint").s("playlistId")?.takeIf { it.isNotBlank() }
                ?: play.o("watchEndpoint").s("playlistId")?.takeIf { it.isNotBlank() }
                ?: return null,
            saved = save.optBoolean("isToggled", false) || save.s("isToggled") == "true",
        )
    }

    private val Any?.isSaveToggle: Boolean
        get() = o("defaultIcon").s("iconType") == "BOOKMARK_BORDER" ||
            o("toggledIcon").s("iconType") == "BOOKMARK"

    fun parsePlaylistOwned(root: Any): Boolean? {
        if (collectRenderers(root, "musicEditablePlaylistDetailHeaderRenderer").isNotEmpty()) {
            return true
        }
        val header = collectRenderers(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: return null
        if (collectRenderers(header, "menuNavigationItemRenderer")
                .any { it.o("icon").s("iconType") in OWNER_ICONS }
        ) {
            return true
        }
        return header.a("buttons").toObjList().none { it.o("toggleButtonRenderer").isSaveToggle }
    }

    private val OWNER_ICONS = setOf("DELETE", "EDIT")

    private fun parseTwoRowItem(renderer: JSONObject?): ShelfItem? {
        if (renderer == null) return null
        val title = renderer.o("title").runs()
        if (title.isBlank()) return null
        val endpoint = renderer.o("navigationEndpoint")
        val browseId = endpoint.o("browseEndpoint").s("browseId")?.takeIf { it.isNotBlank() }
        val videoId = endpoint.o("watchEndpoint").s("videoId")?.takeIf { it.isNotBlank() }
            ?: browseId?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")?.takeIf { it.isNotBlank() }
        val resolvedBrowseId = browseId?.takeUnless { it.startsWith("MPED") }
        val thumbnails = renderer.o("thumbnailRenderer").o("musicThumbnailRenderer")
            .o("thumbnail").a("thumbnails")
        val subtitle = renderer.o("subtitle").runs()
        val isVideo = resolvedBrowseId == null && videoId != null && (
            thumbnails.isNotSquare() ||
                renderer.s("aspectRatio")?.contains("16_9") == true
            )
        if (resolvedBrowseId != null &&
            !resolvedBrowseId.startsWith("VL") &&
            !resolvedBrowseId.startsWith("PL") &&
            !resolvedBrowseId.startsWith("MPRE") &&
            !resolvedBrowseId.startsWith("UC") &&
            (VIDEO_WORD.containsMatchIn(title) || VIDEO_WORD.containsMatchIn(subtitle))
        ) {
            return null
        }
        return ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnails.best(),
            videoId = videoId,
            browseId = resolvedBrowseId,
            isVideo = isVideo,
        )
    }

    fun artistFromSubtitle(subtitle: String): String {
        val parts = subtitle.split(" • ").map(String::trim).filter { it.isNotBlank() }
        return parts.firstOrNull {
            it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY) && !it.matches(DURATION)
        } ?: subtitle
    }

    private fun collectRenderers(root: Any?, name: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject(name)?.let(out::add)
                    val keys = node.keys()
                    while (keys.hasNext()) walk(node.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return out
    }

    private fun Any?.hasExplicitBadge(): Boolean? =
        true.takeIf {
            when (this) {
                is String -> this == "MUSIC_EXPLICIT_BADGE"
                is JSONArray -> (0 until length()).any { opt(it).hasExplicitBadge() == true }
                is JSONObject -> {
                    val keys = keys()
                    var matched = false
                    while (keys.hasNext()) {
                        if (opt(keys.next()).hasExplicitBadge() == true) {
                            matched = true
                            break
                        }
                    }
                    matched
                }
                else -> false
            }
        }

    private val DURATION = Regex("""\d+:\d{2}""")
    private val RUNTIME_PART = Regex("""(\d+)\s*(hours?|hr|min|sec)""", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("""\d{4}""")
    private val TALLY = Regex(
        """[\d.,]+\s*[KMB]?\s+(plays|views|likes|songs|tracks|subscribers|hours?|minutes?|seconds?)\b.*""",
        RegexOption.IGNORE_CASE,
    )
    private val RELEASE_WORDS = setOf("album", "single", "ep")
    private val HEADER_RENDERERS = listOf(
        "musicResponsiveHeaderRenderer",
        "musicDetailHeaderRenderer",
    )
    private val HEADER_CREDIT_LINES = listOf("straplineTextOne", "subtitle")
    private val TYPE_WORDS = setOf(
        "song", "video", "album", "single", "ep", "artist",
        "playlist", "podcast", "episode",
    )
    private val VIDEO_WORD = Regex("""\bvideos?\b""", RegexOption.IGNORE_CASE)
}

private fun Any?.o(key: String): JSONObject? = (this as? JSONObject)?.optJSONObject(key)

private fun Any?.a(key: String): JSONArray? = (this as? JSONObject)?.optJSONArray(key)

private fun Any?.s(key: String): String? {
    val obj = this as? JSONObject ?: return null
    val v = obj.opt(key) ?: return null
    if (v === JSONObject.NULL) return null
    if (v is JSONObject || v is JSONArray) return null
    return v.toString()
}

private fun JSONArray?.firstObj(): JSONObject? = this?.optJSONObject(0)

private fun JSONArray?.toObjList(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (i in 0 until length()) {
            optJSONObject(i)?.let(::add)
        }
    }
}

private fun Any?.findString(key: String): String? = when (this) {
    is JSONObject -> {
        s(key) ?: run {
            val keys = keys()
            var found: String? = null
            while (keys.hasNext()) {
                found = opt(keys.next()).findString(key)
                if (found != null) break
            }
            found
        }
    }
    is JSONArray -> {
        var found: String? = null
        for (i in 0 until length()) {
            found = opt(i).findString(key)
            if (found != null) break
        }
        found
    }
    else -> null
}

private fun JSONObject.isPodcastEpisode(): Boolean =
    findString("musicVideoType") == "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"

private fun Any?.runs(): String =
    this.a("runs").toObjList().joinToString("") { it.s("text").orEmpty() }

private fun Any?.firstRunText(): String? =
    this.a("runs").firstObj().s("text")?.takeIf { it.isNotBlank() }

private fun JSONArray?.best(): String? =
    this?.let { if (length() > 0) optJSONObject(length() - 1).s("url")?.takeIf { u -> u.isNotBlank() } else null }

private fun JSONArray?.isNotSquare(): Boolean {
    val arr = this ?: return false
    if (arr.length() == 0) return false
    val last = arr.optJSONObject(arr.length() - 1) ?: return false
    val width = last.optDouble("width", -1.0).takeIf { it > 0 } ?: return false
    val height = last.optDouble("height", -1.0).takeIf { it > 0 } ?: return false
    return width / height !in 0.85..1.15
}
