package com.darkxvenom.itune.data.auth

import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.artist.ArtistInnertubeParser
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.Song
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses authenticated YouTube Music responses (`account_menu`, `accounts_list`,
 * `FEmusic_liked_*`, `FEmusic_library_corpus_artists`, `FEmusic_history`, and `FEmusic_home`)
 * using Innertube parser rules including recursive `MUSIC_EXPLICIT_BADGE` detection.
 */
object YouTubeAccountParsers {

    data class LibraryCardItem(
        val browseId: String,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String?,
        val type: BrowseType,
        val isExplicit: Boolean = false,
        val playlistId: String? = null,
    )

    data class LibraryPageResult(
        val items: List<LibraryCardItem>,
        val continuation: String?,
    )

    data class HomeFeedResult(
        val quickPickTracks: List<MusicTrack>,
        val recommendedPlaylists: List<MusicPlaylistRef>,
    )

    fun parseAccountInfo(response: JSONObject): YouTubeAccountInfo? {
        val header = collectRenderers(response, "activeAccountHeaderRenderer").firstOrNull()
        if (header != null) {
            val name = header.optJSONObject("accountName").runsText()
                .ifBlank { header.optJSONObject("accountName")?.optString("simpleText").orEmpty() }
            if (name.isNotBlank()) {
                val email = header.optJSONObject("email").runsText()
                    .ifBlank { header.optJSONObject("email")?.optString("simpleText").orEmpty() }
                val handle = header.optJSONObject("channelHandle").runsText()
                    .ifBlank { header.optJSONObject("channelHandle")?.optString("simpleText").orEmpty() }
                val avatar = header.optJSONObject("accountPhoto")?.optJSONArray("thumbnails").bestThumbnail()
                return YouTubeAccountInfo(
                    name = name,
                    email = email.ifBlank { handle },
                    handle = handle,
                    avatarUrl = avatar,
                )
            }
        }
        val firstChannel = parseAccountChannels(response).firstOrNull() ?: return null
        return YouTubeAccountInfo(
            name = firstChannel.name,
            email = firstChannel.subtitle,
            handle = firstChannel.subtitle,
            avatarUrl = firstChannel.thumbnailUrl,
        )
    }

    fun parseAccountChannels(root: JSONObject): List<AccountChannel> =
        collectRenderers(root, "accountItem").mapNotNull { item ->
            val name = item.optJSONObject("accountName").runsText()
                .ifBlank { item.optJSONObject("accountName")?.optString("simpleText").orEmpty() }
            if (name.isBlank()) return@mapNotNull null
            val rawDataSyncId = findStringDeep(item, "datasyncIdToken")
            val pageId = findStringDeep(item, "pageId") ?: delegatedSessionId(rawDataSyncId)
            val dataSyncId = normalizeDataSyncId(rawDataSyncId)
            if (pageId == null && dataSyncId == null) return@mapNotNull null
            val subtitle = item.optJSONObject("channelHandle").runsText()
                .ifBlank { item.optJSONObject("channelHandle")?.optString("simpleText").orEmpty() }
                .ifBlank { item.optJSONObject("accountByline").runsText() }
                .ifBlank { item.optJSONObject("accountByline")?.optString("simpleText").orEmpty() }
            val thumb = item.optJSONObject("accountPhoto")?.optJSONArray("thumbnails").bestThumbnail()
            AccountChannel(
                name = name,
                subtitle = subtitle,
                thumbnailUrl = thumb,
                pageId = pageId,
                dataSyncId = dataSyncId,
                activeOnWeb = item.optBoolean("isSelected", false),
            )
        }.distinctBy { it.key }

    fun parseLibraryItems(root: JSONObject, expectedType: BrowseType): LibraryPageResult {
        val out = linkedMapOf<String, LibraryCardItem>()
        collectRenderers(root, "musicTwoRowItemRenderer").forEach { renderer ->
            parseLibraryTwoRow(renderer, expectedType)?.let { item ->
                out.putIfAbsent(item.browseId, item)
            }
        }
        collectRenderers(root, "musicResponsiveListItemRenderer").forEach { renderer ->
            parseLibraryResponsiveRow(renderer, expectedType)?.let { item ->
                out.putIfAbsent(item.browseId, item)
            }
        }
        if (expectedType == BrowseType.PLAYLIST) {
            collectRenderers(root, "lockupViewModel").forEach { vm ->
                parseLockupPlaylist(vm)?.let { item -> out.putIfAbsent(item.browseId, item) }
            }
            collectRenderers(root, "playlistRenderer").forEach { r ->
                parseWebPlaylistRenderer(r)?.let { item -> out.putIfAbsent(item.browseId, item) }
            }
            collectRenderers(root, "gridPlaylistRenderer").forEach { r ->
                parseWebPlaylistRenderer(r)?.let { item -> out.putIfAbsent(item.browseId, item) }
            }
        } else if (expectedType == BrowseType.ARTIST) {
            collectRenderers(root, "channelRenderer").forEach { r ->
                parseWebChannelRenderer(r)?.let { item -> out.putIfAbsent(item.browseId, item) }
            }
            collectRenderers(root, "gridChannelRenderer").forEach { r ->
                parseWebChannelRenderer(r)?.let { item -> out.putIfAbsent(item.browseId, item) }
            }
        }
        return LibraryPageResult(
            items = out.values.toList(),
            continuation = ArtistInnertubeParser.continuationToken(root),
        )
    }

    fun parseHistoryTracks(root: JSONObject): Pair<List<Song>, String?> {
        val seen = linkedSetOf<String>()
        val tracks = mutableListOf<Song>()

        collectRenderers(root, "musicResponsiveListItemRenderer").forEach { r ->
            parseResponsiveSong(r)?.let { song ->
                if (seen.add(song.videoId)) tracks.add(song)
            }
        }
        collectRenderers(root, "musicTwoRowItemRenderer").forEach { r ->
            parseTwoRowSong(r)?.let { song ->
                if (seen.add(song.videoId)) tracks.add(song)
            }
        }
        if (tracks.isEmpty()) {
            collectRenderers(root, "playlistVideoRenderer").forEach { r ->
                parseWebVideoRenderer(r)?.let { song ->
                    if (seen.add(song.videoId)) tracks.add(song)
                }
            }
            collectRenderers(root, "videoRenderer").forEach { r ->
                parseWebVideoRenderer(r)?.let { song ->
                    if (seen.add(song.videoId)) tracks.add(song)
                }
            }
            collectRenderers(root, "compactVideoRenderer").forEach { r ->
                parseWebVideoRenderer(r)?.let { song ->
                    if (seen.add(song.videoId)) tracks.add(song)
                }
            }
        }
        return tracks to ArtistInnertubeParser.continuationToken(root)
    }

    fun parseHomeFeed(root: JSONObject): HomeFeedResult {
        val tracks = mutableListOf<MusicTrack>()
        val seenTrackIds = linkedSetOf<String>()
        val playlists = mutableListOf<MusicPlaylistRef>()
        val seenPlaylistIds = linkedSetOf<String>()

        collectRenderers(root, "musicResponsiveListItemRenderer").forEach { r ->
            val song = parseResponsiveSong(r) ?: return@forEach
            if (seenTrackIds.add(song.videoId)) {
                tracks.add(
                    MusicTrack(
                        id = song.videoId,
                        title = song.title,
                        artists = song.artist,
                        artistId = song.artistId,
                        album = song.albumName,
                        albumId = song.albumId,
                        thumbnailUrl = song.thumbnailUrl,
                        isVideo = song.isVideo,
                        isExplicit = song.isExplicit == true,
                    ),
                )
            }
        }

        collectRenderers(root, "musicTwoRowItemRenderer").forEach { r ->
            val endpoint = r.optJSONObject("navigationEndpoint")
            val browseId = endpoint?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?.takeIf { it.isNotBlank() }
            val watchVid = endpoint?.optJSONObject("watchEndpoint")?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
                ?: browseId?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")?.takeIf { it.isNotBlank() }

            if (watchVid != null && (browseId == null || browseId.startsWith("MPED"))) {
                parseTwoRowSong(r)?.let { song ->
                    if (seenTrackIds.add(song.videoId)) {
                        tracks.add(
                            MusicTrack(
                                id = song.videoId,
                                title = song.title,
                                artists = song.artist,
                                artistId = song.artistId,
                                thumbnailUrl = song.thumbnailUrl,
                                isVideo = song.isVideo,
                                isExplicit = song.isExplicit == true,
                            ),
                        )
                    }
                }
            } else if (browseId != null && (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RDCLAK"))) {
                val title = r.optJSONObject("title").runsText()
                if (title.isNotBlank() && seenPlaylistIds.add(browseId)) {
                    val subtitle = r.optJSONObject("subtitle").runsText()
                    val thumb = r.optJSONObject("thumbnailRenderer")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails").bestThumbnail()
                    val watchPlId = r.optJSONObject("thumbnailOverlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchPlaylistEndpoint")
                        ?.optString("playlistId")
                        ?.takeIf { it.isNotBlank() }
                    playlists.add(
                        MusicPlaylistRef(
                            id = browseId,
                            title = title,
                            author = subtitle.ifBlank { "For You" },
                            thumbnailUrl = thumb,
                            isAlbum = false,
                            watchPlaylistId = watchPlId ?: browseId.removePrefix("VL"),
                            isExplicit = r.hasExplicitBadgeRecursive(),
                        ),
                    )
                }
            }
        }

        return HomeFeedResult(
            quickPickTracks = tracks.take(24),
            recommendedPlaylists = playlists.take(12),
        )
    }

    private fun normalizeLibraryBrowseId(rawId: String, expectedType: BrowseType): String {
        val cleaned = if (rawId.startsWith("MPLA") && rawId.removePrefix("MPLA").startsWith("UC")) {
            rawId.removePrefix("MPLA")
        } else {
            rawId
        }
        return if (expectedType == BrowseType.ARTIST && cleaned.startsWith("MPLA")) {
            cleaned.removePrefix("MPLA")
        } else {
            cleaned
        }
    }

    private val SYSTEM_AUTO_PLAYLIST_IDS = setOf("LM", "SE", "NE", "SS", "SR", "LL", "WL")

    private fun isValidPlaylistBrowseId(browseId: String): Boolean {
        if (!browseId.startsWith("VL") && !browseId.startsWith("PL")) return false
        return browseId.removePrefix("VL") !in SYSTEM_AUTO_PLAYLIST_IDS
    }

    private fun parseLibraryTwoRow(renderer: JSONObject, expectedType: BrowseType): LibraryCardItem? {
        val title = renderer.optJSONObject("title").runsText().trim()
            .ifBlank { renderer.optString("title").trim() }
        if (title.isBlank()) return null
        val nav = renderer.optJSONObject("navigationEndpoint")
        val rawBrowseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() }
            ?: nav?.optJSONObject("watchEndpoint")?.optString("playlistId")?.takeIf { it.isNotBlank() }?.let { "VL$it" }
            ?: nav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")?.takeIf { it.isNotBlank() }?.let { "VL$it" }
            ?: renderer.optJSONObject("thumbnailOverlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchPlaylistEndpoint")
                ?.optString("playlistId")?.takeIf { it.isNotBlank() }?.let { "VL$it" }
            ?: return null
        // Skip synthetic create tiles and podcast/episode containers.
        if (rawBrowseId.startsWith("FE") || rawBrowseId.startsWith("MPSP") || rawBrowseId.startsWith("MPED")) return null
        val browseId = normalizeLibraryBrowseId(rawBrowseId, expectedType)
        val resolvedType = inferType(browseId, expectedType)
        if (expectedType == BrowseType.PLAYLIST && (!isValidPlaylistBrowseId(browseId) || resolvedType != BrowseType.PLAYLIST)) {
            return null
        }

        val subtitle = renderer.optJSONObject("subtitle").runsText().trim()
        val thumb = renderer.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            .bestThumbnail()
        val watchPlId = renderer.optJSONObject("thumbnailOverlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchPlaylistEndpoint")
            ?.optString("playlistId")
            ?.takeIf { it.isNotBlank() }

        return LibraryCardItem(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumb,
            type = resolvedType,
            isExplicit = renderer.hasExplicitBadgeRecursive(),
            playlistId = watchPlId ?: browseId.takeIf { it.startsWith("VL") || it.startsWith("PL") }?.removePrefix("VL"),
        )
    }

    private fun parseLibraryResponsiveRow(renderer: JSONObject, expectedType: BrowseType): LibraryCardItem? {
        val titleRunBrowseId = renderer.optJSONArray("flexColumns")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            ?.takeIf { it.isNotBlank() }
        val subscribeChannelId = if (expectedType == BrowseType.ARTIST) {
            findStringDeep(renderer.optJSONObject("menu"), "channelIds")
                ?: collectRenderers(renderer.optJSONObject("menu"), "subscribeEndpoint")
                    .firstOrNull()?.optJSONArray("channelIds")?.optString(0)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val rawBrowseId = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            ?.takeIf { it.isNotBlank() }
            ?: titleRunBrowseId
            ?: subscribeChannelId
            ?: return null
        if (rawBrowseId.startsWith("FE") || rawBrowseId.startsWith("MPSP") || rawBrowseId.startsWith("MPED")) return null
        val browseId = normalizeLibraryBrowseId(rawBrowseId, expectedType)
        val resolvedType = inferType(browseId, expectedType)
        if (expectedType == BrowseType.PLAYLIST && (!isValidPlaylistBrowseId(browseId) || resolvedType != BrowseType.PLAYLIST)) {
            return null
        }

        val title = renderer.columnText(0)
        if (title.isBlank()) return null
        val subtitle = renderer.columnText(1)
        val thumb = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            .bestThumbnail()
        val watchPlId = renderer.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchPlaylistEndpoint")
            ?.optString("playlistId")
            ?.takeIf { it.isNotBlank() }

        return LibraryCardItem(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumb,
            type = resolvedType,
            isExplicit = renderer.hasExplicitBadgeRecursive(),
            playlistId = watchPlId ?: browseId.takeIf { it.startsWith("VL") || it.startsWith("PL") }?.removePrefix("VL"),
        )
    }

    private fun parseLockupPlaylist(vm: JSONObject): LibraryCardItem? {
        val contentType = vm.optString("contentType")
        if (contentType.isNotBlank() && contentType != "LOCKUP_CONTENT_TYPE_PLAYLIST") return null
        val contentId = vm.optString("contentId").takeIf { it.isNotBlank() }
            ?: findStringDeep(vm, "playlistId")
            ?: return null
        val cleanId = contentId.removePrefix("VL")
        if (cleanId in SYSTEM_AUTO_PLAYLIST_IDS) return null
        val metadata = vm.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = metadata?.optJSONObject("title")?.optString("content")?.trim()
            ?.takeIf { it.isNotBlank() } ?: return null
        val subtitleParts = mutableListOf<String>()
        val rows = metadata.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")
        if (rows != null) {
            for (r in 0 until rows.length()) {
                val parts = rows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                for (p in 0 until parts.length()) {
                    val txt = parts.optJSONObject(p)?.optJSONObject("text")?.optString("content")?.trim()
                    if (!txt.isNullOrBlank()) subtitleParts.add(txt)
                }
            }
        }
        val thumb = findBestSourceUrl(vm.optJSONObject("contentImage"))
        return LibraryCardItem(
            browseId = "VL$cleanId",
            title = title,
            subtitle = subtitleParts.joinToString(" • ").ifBlank { "Playlist" },
            thumbnailUrl = thumb,
            type = BrowseType.PLAYLIST,
            isExplicit = vm.hasExplicitBadgeRecursive(),
            playlistId = cleanId,
        )
    }

    private fun parseWebPlaylistRenderer(renderer: JSONObject): LibraryCardItem? {
        val playlistId = renderer.optString("playlistId").takeIf { it.isNotBlank() } ?: return null
        if (playlistId in SYSTEM_AUTO_PLAYLIST_IDS) return null
        val title = renderer.optJSONObject("title").runsText().trim()
        if (title.isBlank()) return null
        val author = renderer.optJSONObject("shortBylineText").runsText()
            .ifBlank { renderer.optJSONObject("longBylineText").runsText() }
        val thumb = (renderer.optJSONArray("thumbnails")?.optJSONObject(0)
            ?.optJSONArray("thumbnails") ?: renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))
            .bestThumbnail()
        return LibraryCardItem(
            browseId = "VL$playlistId",
            title = title,
            subtitle = author.ifBlank { "Playlist" },
            thumbnailUrl = thumb,
            type = BrowseType.PLAYLIST,
            isExplicit = renderer.hasExplicitBadgeRecursive(),
            playlistId = playlistId,
        )
    }

    private fun parseWebChannelRenderer(renderer: JSONObject): LibraryCardItem? {
        val channelId = renderer.optString("channelId").takeIf { it.startsWith("UC") } ?: return null
        val title = renderer.optJSONObject("title").runsText().trim()
        if (title.isBlank()) return null
        val subs = renderer.optJSONObject("subscriberCountText").runsText()
        val thumb = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails").bestThumbnail()
        return LibraryCardItem(
            browseId = channelId,
            title = title,
            subtitle = subs.ifBlank { "Artist" },
            thumbnailUrl = thumb,
            type = BrowseType.ARTIST,
            isExplicit = false,
            playlistId = null,
        )
    }

    private fun parseWebVideoRenderer(renderer: JSONObject): Song? {
        val videoId = renderer.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val title = renderer.optJSONObject("title").runsText().trim()
        if (title.isBlank()) return null
        val artistRuns = renderer.optJSONObject("shortBylineText")
            ?: renderer.optJSONObject("longBylineText")
            ?: renderer.optJSONObject("ownerText")
        val artist = artistRuns.runsText().ifBlank { "Unknown artist" }
        val artistId = artistRuns?.optJSONArray("runs")?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            ?.optString("browseId")?.takeIf { it.startsWith("UC") }
        val duration = renderer.optJSONObject("lengthText").runsText().takeIf { it.isNotBlank() }
        val thumb = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails").bestThumbnail()
        return Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumb,
            durationText = duration,
            artistId = artistId,
            isExplicit = renderer.hasExplicitBadgeRecursive(),
        )
    }

    private fun findBestSourceUrl(node: Any?): String? {
        val sources = collectRenderers(node, "thumbnailViewModel")
            .firstOrNull()?.optJSONObject("image")?.optJSONArray("sources")
            ?: return null
        return if (sources.length() > 0) {
            sources.optJSONObject(sources.length() - 1)?.optString("url")
                ?.takeIf { it.isNotBlank() }
                ?.highQualityThumbnail()
        } else {
            null
        }
    }

    private fun parseResponsiveSong(renderer: JSONObject): Song? {
        val watchEp = renderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
            ?: renderer.optJSONObject("overlay")?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")?.optJSONObject("watchEndpoint")
        val videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: watchEp?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: renderer.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?.optString("browseId")?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")
            ?: return null

        val flex = renderer.optJSONArray("flexColumns") ?: return null
        val title = renderer.columnText(0)
        if (title.isBlank()) return null

        val subRuns = flex.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
        val segments = mutableListOf<Pair<String, String?>>()
        var duration: String? = null
        if (subRuns != null) {
            val sb = StringBuilder()
            var segId: String? = null
            fun flush() {
                val txt = sb.toString().trim()
                if (txt.isNotEmpty()) {
                    if (txt.matches(Regex("\\d+:\\d+(:\\d+)?"))) {
                        duration = txt
                    } else if (
                        !txt.equals("Song", true) &&
                        !txt.equals("Video", true) &&
                        !txt.endsWith("plays", true) &&
                        !txt.endsWith("views", true)
                    ) {
                        segments.add(txt to segId)
                    }
                }
                sb.setLength(0)
                segId = null
            }
            for (i in 0 until subRuns.length()) {
                val run = subRuns.optJSONObject(i) ?: continue
                val t = run.optString("text")
                if (t.trim() == "•" || t.trim() == "·") {
                    flush()
                    continue
                }
                if (segId == null) {
                    segId = run.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId")
                        ?.takeIf { it.isNotBlank() }
                }
                sb.append(t)
            }
            flush()
        }

        val artistName = segments.firstOrNull()?.first?.ifBlank { null } ?: "Various Artists"
        val artistId = segments.firstOrNull()?.second
        val albumName = segments.getOrNull(1)?.first
        val albumId = segments.getOrNull(1)?.second
        val thumb = renderer.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            .bestThumbnail()

        return Song(
            videoId = videoId,
            title = title,
            artist = artistName,
            thumbnailUrl = thumb,
            durationText = duration,
            artistId = artistId,
            albumId = albumId,
            albumName = albumName,
            setVideoId = renderer.optJSONObject("playlistItemData")?.optString("playlistSetVideoId")
                ?.takeIf { it.isNotBlank() },
            isExplicit = renderer.hasExplicitBadgeRecursive(),
        )
    }

    private fun parseTwoRowSong(renderer: JSONObject): Song? {
        val title = renderer.optJSONObject("title").runsText().trim()
        if (title.isBlank()) return null
        val endpoint = renderer.optJSONObject("navigationEndpoint") ?: return null
        val browseId = endpoint.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotBlank() }
        val videoId = endpoint.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: browseId?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")?.takeIf { it.isNotBlank() }
            ?: return null

        val subtitle = renderer.optJSONObject("subtitle").runsText().trim()
        val artist = subtitle.split(" • ", " · ")
            .map { it.trim() }
            .firstOrNull {
                it.isNotBlank() &&
                    !it.equals("Song", true) &&
                    !it.equals("Video", true) &&
                    !it.equals("Single", true) &&
                    !it.endsWith("plays", true)
            }
            ?: subtitle.ifBlank { "Various Artists" }

        val thumb = renderer.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            .bestThumbnail()

        return Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumb,
            isExplicit = renderer.hasExplicitBadgeRecursive(),
        )
    }

    private fun inferType(browseId: String, fallback: BrowseType): BrowseType = when {
        browseId.startsWith("VL") || browseId.startsWith("PL") -> BrowseType.PLAYLIST
        browseId.startsWith("MPRE") || browseId.startsWith("OLAK") -> BrowseType.ALBUM
        browseId.startsWith("UC") || browseId.startsWith("FEmusic_library_privately_owned_artist") -> BrowseType.ARTIST
        else -> fallback
    }

    private fun collectRenderers(root: Any?, key: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = node.opt(k)
                        if (k == key && v is JSONObject) out.add(v)
                        walk(v)
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length()) {
                        walk(node.opt(i))
                    }
                }
            }
        }
        walk(root)
        return out
    }

    private fun JSONObject.columnText(colIndex: Int): String {
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

    private fun JSONObject?.runsText(): String {
        if (this == null) return ""
        val runs = optJSONArray("runs") ?: return optString("simpleText")
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }.trim()
    }

    private fun JSONArray?.bestThumbnail(): String? {
        if (this == null || length() == 0) return null
        return optJSONObject(length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?.highQualityThumbnail()
    }

    private fun Any?.hasExplicitBadgeRecursive(): Boolean = when (this) {
        is String -> this == "MUSIC_EXPLICIT_BADGE"
        is JSONArray -> (0 until length()).any { opt(it).hasExplicitBadgeRecursive() }
        is JSONObject -> {
            val keys = keys()
            var matched = false
            while (keys.hasNext()) {
                if (opt(keys.next()).hasExplicitBadgeRecursive()) {
                    matched = true
                    break
                }
            }
            matched
        }
        else -> false
    }

    internal fun findStringDeep(element: Any?, targetKey: String): String? {
        return when (element) {
            is JSONObject -> {
                val direct = element.optString(targetKey).takeIf { it.isNotBlank() }
                if (direct != null) return direct
                val keys = element.keys()
                while (keys.hasNext()) {
                    val found = findStringDeep(element.opt(keys.next()), targetKey)
                    if (found != null) return found
                }
                null
            }
            is JSONArray -> {
                for (i in 0 until element.length()) {
                    val found = findStringDeep(element.opt(i), targetKey)
                    if (found != null) return found
                }
                null
            }
            else -> null
        }
    }
}
