package com.darkxvenom.itune.data.artist

import android.util.Log
import com.darkxvenom.itune.data.model.ArtistPage
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.LibraryState
import com.darkxvenom.itune.data.model.SearchFilter
import com.darkxvenom.itune.data.model.SearchResult
import com.darkxvenom.itune.data.model.ShelfItem
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.WatchTarget
import com.darkxvenom.itune.data.model.isExactArtistMatch
import com.darkxvenom.itune.data.model.normalizedArtistName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object ArtistRepository {

    private const val TAG = "ArtistRepository"
    private const val MAX_PAGES = 10

    data class SongPage(
        val songs: List<Song>,
        val continuation: String?,
        val suggested: List<Song> = emptyList(),
        val library: LibraryState? = null,
        val owned: Boolean? = null,
        val header: ArtistInnertubeParser.BrowseHeader? = null,
        val description: String? = null,
    )

    private val showByToken = ConcurrentHashMap<String, String>()

    suspend fun artistPage(browseId: String): Result<ArtistPage> = call("artist:$browseId") {
        val page = ArtistInnertubeParser.parseArtistPage(ArtistInnertubeClient.browse(browseId))
        coroutineScope {
            val fullSongsDeferred = async {
                page.moreSongsBrowseId?.let { playlistId ->
                    runCatching { songsPaged(playlistId) }.getOrNull()
                }
            }
            val shelvesDeferred = page.sections.map { shelf ->
                async {
                    val moreBrowseId = shelf.moreBrowseId
                    if (moreBrowseId != null) {
                        val fullItems = runCatching {
                            itemsPaged(moreBrowseId, shelf.moreParams)
                        }.getOrNull()
                        if (!fullItems.isNullOrEmpty()) {
                            shelf.copy(items = fullItems)
                        } else {
                            shelf
                        }
                    } else {
                        shelf
                    }
                }
            }
            val fullSongs = fullSongsDeferred.await()
            val fullShelves = shelvesDeferred.awaitAll()
            val resolvedSongs = if (!fullSongs.isNullOrEmpty()) fullSongs else page.songs
            page.copy(songs = resolvedSongs, sections = fullShelves)
        }
    }

    suspend fun findArtistPageId(name: String): Result<String?> = call("artistByName:$name") {
        if (normalizedArtistName(name).isEmpty()) return@call null
        val response = ArtistInnertubeClient.search(name, SearchFilter.ARTISTS.params)
        ArtistInnertubeParser.parseSearchPage(response).rows
            .filterIsInstance<SearchResult.Browse>()
            .map { it.item }
            .firstOrNull { it.type == BrowseType.ARTIST && isExactArtistMatch(it.title, name) }
            ?.browseId
    }

    suspend fun trackLinks(videoId: String): Result<Song> = call("links:$videoId") {
        ArtistInnertubeParser.parseWatchQueue(ArtistInnertubeClient.next(videoId))
            .firstOrNull { it.videoId == videoId }
            ?: error("no watch entry for $videoId")
    }

    suspend fun browseSongs(browseId: String): Result<SongPage> = call("browse:$browseId") {
        val response = ArtistInnertubeClient.browse(browseId)
        val page = if (browseId.startsWith("MPREb")) {
            albumPageOf(response)
        } else {
            pageOf(response)
        }
        if (!browseId.startsWith("VL")) page
        else page.copy(owned = ArtistInnertubeParser.parsePlaylistOwned(response))
    }

    suspend fun moreSongs(token: String): Result<SongPage> = call("browse:more") {
        pageOf(ArtistInnertubeClient.browseContinuation(token), showByToken[token])
    }

    suspend fun allSongs(browseId: String): Result<List<Song>> = call("all:$browseId") {
        songsPaged(browseId).ifEmpty { error("No tracks here") }
    }

    suspend fun watchQueue(target: WatchTarget): Result<List<Song>> = call("watch:${target.playlistId}") {
        ArtistInnertubeParser.parseWatchQueue(
            ArtistInnertubeClient.nextPlaylist(target.playlistId, target.videoId, target.params),
        ).filterNot { it.isVideo }
    }

    suspend fun radio(videoId: String, preferVideo: Boolean = false): Result<List<Song>> =
        call("radio:$videoId") {
            ArtistInnertubeParser.parseWatchQueue(
                ArtistInnertubeClient.next(videoId),
                preferAudio = !preferVideo,
                preferVideo = preferVideo,
            ).let { songs -> if (preferVideo) songs else songs.filterNot { it.isVideo } }
        }

    private suspend fun albumPageOf(albumResponse: JSONObject): SongPage {
        val metadata = pageOf(albumResponse)
        val playlist = albumPlaylistOf(albumResponse, metadata.songs.size) ?: return metadata
        return pageOf(playlist).copy(
            library = metadata.library,
            header = metadata.header,
            description = metadata.description,
        )
    }

    private suspend fun albumPlaylistOf(albumResponse: JSONObject, albumRows: Int): JSONObject? {
        val playlistId = ArtistInnertubeParser.parseAlbumPlaylistId(albumResponse) ?: return null
        val response = try {
            ArtistInnertubeClient.browse("VL${playlistId.removePrefix("VL")}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "album playlist $playlistId failed, keeping album rows: ${e.message}")
            return null
        }
        val shelf = ArtistInnertubeParser.parsePlaylistShelf(response)
        val rows = (shelf?.songs ?: ArtistInnertubeParser.collectSongsDeep(response)).size
        val more = (shelf?.continuation ?: ArtistInnertubeParser.continuationToken(response)) != null
        if (rows < albumRows && !more) return null
        return response
    }

    private fun pageOf(response: JSONObject, show: String? = null): SongPage {
        val library = ArtistInnertubeParser.parseLibraryState(response)
        val header = ArtistInnertubeParser.parseBrowseHeader(response)
        ArtistInnertubeParser.parsePlaylistShelf(response)?.let { shelf ->
            return SongPage(shelf.songs, shelf.continuation, shelf.suggested, library, header = header)
        }
        val podcast = ArtistInnertubeParser.podcastShow(response) ?: show
        val continuation = ArtistInnertubeParser.continuationToken(response)
        if (podcast != null && continuation != null) showByToken[continuation] = podcast
        return SongPage(
            songs = ArtistInnertubeParser.collectSongsDeep(response, podcast).distinctBy { it.videoId },
            continuation = continuation,
            library = library,
            header = header,
            description = ArtistInnertubeParser.parseDescription(response),
        )
    }

    private suspend fun songsPaged(browseId: String): List<Song> {
        val out = LinkedHashMap<String, Song>()
        var response = ArtistInnertubeClient.browse(browseId)
        if (browseId.startsWith("MPREb")) {
            val albumRows = ArtistInnertubeParser.collectSongsDeep(response).size
            albumPlaylistOf(response, albumRows)?.let { response = it }
        }
        var page = 1
        val show = ArtistInnertubeParser.podcastShow(response)
        while (true) {
            val shelf = ArtistInnertubeParser.parsePlaylistShelf(response)
            (shelf?.songs ?: ArtistInnertubeParser.collectSongsDeep(response, show)).forEach { out[it.videoId] = it }
            val token = shelf?.continuation ?: ArtistInnertubeParser.continuationToken(response)
            if (token == null || page++ >= MAX_PAGES) break
            response = runCatching { ArtistInnertubeClient.browseContinuation(token) }.getOrNull() ?: break
        }
        return out.values.toList()
    }

    private suspend fun itemsPaged(browseId: String, params: String? = null): List<ShelfItem> {
        val out = LinkedHashMap<String, ShelfItem>()
        var response = ArtistInnertubeClient.browse(browseId, params)
        var page = 1
        while (true) {
            val parsed = ArtistInnertubeParser.parseLibraryItemPage(response)
            parsed.items.forEach { item ->
                val key = item.browseId ?: item.videoId ?: "${item.title}\n${item.subtitle}"
                out.putIfAbsent(key, item)
            }
            val token = parsed.continuation ?: break
            if (page++ >= MAX_PAGES) break
            response = runCatching { ArtistInnertubeClient.browseContinuation(token) }.getOrNull() ?: break
        }
        return out.values.toList()
    }

    private suspend fun <T> call(label: String, block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }
                .onFailure { if (it is CancellationException) throw it }
                .onFailure { Log.w(TAG, "$label failed: ${it.message}") }
        }
}
