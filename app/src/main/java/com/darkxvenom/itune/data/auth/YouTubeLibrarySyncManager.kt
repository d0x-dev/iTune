package com.darkxvenom.itune.data.auth

import android.content.Context
import android.util.Log
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import com.darkxvenom.itune.data.artist.ArtistInnertubeParser
import com.darkxvenom.itune.data.artist.ArtistRepository
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.PersonalizedHomeRepository
import com.darkxvenom.itune.data.library.SavedCollectionEntry
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.UserPlaylist
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.LikeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bidirectional real-time YouTube Music synchronization manager:
 * - Pulls Artists, Liked Songs (`VLLM` / `FEmusic_liked_videos`), Playlists (`FEmusic_liked_playlists`),
 *   Albums (`FEmusic_liked_albums`), and Watch History (`FEmusic_history`).
 * - Pushes local likes, unlikes, artist subscribes, album/playlist bookmarks, and Google playlist
 *   creation / edits / deletions back to YouTube Music immediately.
 * - Preserves locally liked tracks across logout so the user can always view them offline or signed out.
 */
object YouTubeLibrarySyncManager {
    private const val TAG = "YtmLibrarySync"
    private const val MAX_LIBRARY_PAGES = 10
    private const val MAX_PLAYLIST_TRACKS_PAGES = MAX_LIBRARY_PAGES
    private const val PLAYLIST_HYDRATE_BATCH_SIZE = 8

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()

    @Volatile
    private var syncJob: Job? = null

    fun launchFullSync(context: Context) {
        val app = context.applicationContext
        syncJob?.cancel()
        syncJob = scope.launch { performFullSync(app) }
    }

    private val SYSTEM_AUTO_PLAYLIST_IDS = setOf("LM", "SE", "NE", "SS", "SR", "LL", "WL")
    private val NOT_EDITABLE_PLAYLIST_PREFIXES = listOf("LM", "SE", "NE", "SS", "SR", "LL", "WL", "RD", "OLAK", "MPRE")

    suspend fun performFullSync(context: Context) = withContext(Dispatchers.IO) {
        GoogleAuthStore.ensureInit(context)
        if (!GoogleAuthStore.isSignedIn) return@withContext
        val app = context.applicationContext
        syncMutex.withLock {
            if (!GoogleAuthStore.isSignedIn) return@withLock
            val expectedKey = GoogleAuthStore.activeSessionKey() ?: return@withLock
            GoogleAuthStore.updateSyncStatus(
                syncing = true,
                error = null,
                expectedSessionKey = expectedKey,
            )
            try {
                // Rebuild the user/delegated session IDs from live ytcfg on every full sync.
                // This also repairs sessions saved by older versions with mixed identifiers.
                ArtistInnertubeClient.verifyAuthenticatedLibraryAccess(persistScope = true)
                if (GoogleAuthStore.activeSessionKey() != expectedKey) return@withLock
                refreshAccountMetadata(app, expectedKey)
                val taskResults = coroutineScope {
                    val likesJob = async { labeledSyncTask("Liked songs") { syncLikedSongs(app, expectedKey) } }
                    val artistsJob = async { labeledSyncTask("Artists") { syncSubscribedArtists(app, expectedKey) } }
                    val albumsJob = async { labeledSyncTask("Albums") { syncSavedAlbums(app, expectedKey) } }
                    val playlistsJob = async { labeledSyncTask("Playlists") { syncPlaylists(app, expectedKey) } }
                    val historyJob = async { labeledSyncTask("History") { syncHistory(app, expectedKey) } }
                    val homeJob = async { labeledSyncTask("Home feed") { PersonalizedHomeRepository.refreshIfSignedIn() } }
                    awaitAll(likesJob, artistsJob, albumsJob, playlistsJob, historyJob, homeJob)
                }
                if (GoogleAuthStore.activeSessionKey() != expectedKey) return@withLock
                val failures = taskResults.filter { it.second.isFailure }
                val syncError = failures.takeIf { it.isNotEmpty() }?.joinToString(" • ") { (label, result) ->
                    "$label: ${result.exceptionOrNull()?.message?.take(180) ?: "request failed"}"
                }?.take(900)
                GoogleAuthStore.updateSyncStatus(
                    syncing = false,
                    error = syncError,
                    syncedNow = syncError == null,
                    expectedSessionKey = expectedKey,
                )
            } catch (e: CancellationException) {
                if (GoogleAuthStore.activeSessionKey() == expectedKey) {
                    GoogleAuthStore.updateSyncStatus(syncing = false, expectedSessionKey = expectedKey)
                }
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Full sync failed: ${e.message}", e)
                GoogleAuthStore.updateSyncStatus(
                    syncing = false,
                    error = e.message ?: "Sync failed",
                    expectedSessionKey = expectedKey,
                )
            }
        }
    }

    private suspend fun <T> labeledSyncTask(
        label: String,
        task: suspend () -> T,
    ): Pair<String, Result<T>> {
        return try {
            label to Result.success(task())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "$label sync failed: ${e.message}", e)
            label to Result.failure(e)
        }
    }

    suspend fun refreshAccountMetadata(
        context: Context,
        expectedSessionKey: String? = GoogleAuthStore.activeSessionKey(),
    ) {
        if (!GoogleAuthStore.isSignedIn || expectedSessionKey == null) return
        val info = runCatching {
            YouTubeAccountParsers.parseAccountInfo(ArtistInnertubeClient.accountMenu())
        }.getOrNull()
        val channels = runCatching {
            val switcher = runCatching { ArtistInnertubeClient.accountsList() }.getOrNull()
                ?.let { YouTubeAccountParsers.parseAccountChannels(it) }
                .orEmpty()
            switcher.ifEmpty {
                YouTubeAccountParsers.parseAccountChannels(ArtistInnertubeClient.accountSwitcher())
            }
        }.getOrDefault(emptyList())
        if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
        GoogleAuthStore.updateSyncStatus(
            channels = channels.takeIf { it.isNotEmpty() },
            accountInfo = info,
            expectedSessionKey = expectedSessionKey,
        )
    }

    suspend fun syncHistory(
        context: Context,
        expectedSessionKey: String? = GoogleAuthStore.activeSessionKey(),
    ) {
        if (!GoogleAuthStore.isSignedIn || expectedSessionKey == null) return

        val first = try {
            ArtistInnertubeClient.browse("FEmusic_history")
        } catch (primaryError: Throwable) {
            if (primaryError is CancellationException) throw primaryError
            runCatching { ArtistInnertubeClient.postWebBrowse("FEhistory") }
                .getOrElse { webError ->
                    if (webError is CancellationException) throw webError
                    throw IllegalStateException(
                        "Could not read YouTube Music watch history: ${primaryError.message}; fallback: ${webError.message}",
                        webError,
                    )
                }
        }

        val collected = mutableListOf<SavedSongMetadata>()
        val (firstSongs, firstToken) = YouTubeAccountParsers.parseHistoryTracks(first)
        firstSongs.forEach { song ->
            collected.add(SavedSongMetadata.fromSong(song = song, savedLocally = false, isYtmSynced = true))
        }
        var token = firstToken
        var pageCount = 1
        while (token != null && pageCount < MAX_LIBRARY_PAGES) {
            if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
            val cont = try {
                ArtistInnertubeClient.browseContinuation(token, allowAnonymousFallback = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw IllegalStateException("YouTube Music history page $pageCount failed: ${e.message}", e)
            }
            val (nextSongs, nextToken) = YouTubeAccountParsers.parseHistoryTracks(cont)
            nextSongs.forEach { song ->
                collected.add(SavedSongMetadata.fromSong(song = song, savedLocally = false, isYtmSynced = true))
            }
            token = nextToken
            pageCount++
        }
        if (collected.isEmpty()) {
            // Some accounts still expose the history shelf only through the WEB endpoint.
            try {
                val fallback = ArtistInnertubeClient.postWebBrowse("FEhistory")
                YouTubeAccountParsers.parseHistoryTracks(fallback).first.forEach { song ->
                    collected.add(SavedSongMetadata.fromSong(song = song, savedLocally = false, isYtmSynced = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw IllegalStateException(
                    "Could not read YouTube history from either endpoint: ${e.message}. Existing history was kept.",
                    e,
                )
            }
        }
        if (collected.isEmpty()) {
            throw IllegalStateException("YouTube returned no recognizable history tracks. Existing history was kept.")
        }
        if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
        LibraryStore.replaceYtmHistory(context, collected.distinctBy { it.id })
    }

    suspend fun syncLikedSongs(
        context: Context,
        expectedSessionKey: String? = GoogleAuthStore.activeSessionKey(),
    ) {
        if (!GoogleAuthStore.isSignedIn || expectedSessionKey == null) return
        val songs = mutableListOf<SavedSongMetadata>()
        val firstPageResult = ArtistRepository.browseSongs("VLLM")
        val firstPage = firstPageResult.getOrNull()
        if (firstPage != null) {
            firstPage.songs.forEach { song ->
                songs.add(SavedSongMetadata.fromSong(song, savedLocally = false, isYtmSynced = true))
            }
            var token = firstPage.continuation
            var page = 1
            while (token != null && page < MAX_LIBRARY_PAGES) {
                if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
                val next = ArtistRepository.moreSongs(token).getOrElse { error ->
                    throw IllegalStateException("YouTube Music liked songs page $page failed: ${error.message}", error)
                }
                next.songs.forEach { song ->
                    songs.add(SavedSongMetadata.fromSong(song, savedLocally = false, isYtmSynced = true))
                }
                token = next.continuation
                page++
            }
        } else {
            val primaryError = firstPageResult.exceptionOrNull()
                ?: IllegalStateException("YouTube Music returned no liked-songs page")
            val likedResponse = try {
                ArtistInnertubeClient.browse("FEmusic_liked_videos")
            } catch (musicError: Throwable) {
                if (musicError is CancellationException) throw musicError
                runCatching { ArtistInnertubeClient.postWebBrowse("VLLL") }
                    .getOrElse { webError ->
                        if (webError is CancellationException) throw webError
                        throw IllegalStateException(
                            "Could not read liked songs: ${primaryError.message}; YouTube Music fallback: ${musicError.message}; YouTube fallback: ${webError.message}",
                            webError,
                        )
                    }
            }
            val (parsed, _) = YouTubeAccountParsers.parseHistoryTracks(likedResponse)
            parsed.forEach { song ->
                songs.add(SavedSongMetadata.fromSong(song, savedLocally = false, isYtmSynced = true))
            }
        }

        if (songs.isEmpty() && firstPage != null) {
            // Keep a second authenticated shelf parser for accounts where VLLM is empty
            // or its content renderer differs from the FEmusic private-library renderer.
            try {
                val fallback = ArtistInnertubeClient.browse("FEmusic_liked_videos", anonymous = false)
                YouTubeAccountParsers.parseHistoryTracks(fallback).first.forEach { song ->
                    songs.add(SavedSongMetadata.fromSong(song, savedLocally = false, isYtmSynced = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw IllegalStateException(
                    "Could not read liked songs from the alternate YouTube Music shelf: ${e.message}. Existing likes were kept.",
                    e,
                )
            }
        }
        if (songs.isEmpty()) {
            throw IllegalStateException("YouTube returned no recognizable liked songs. Existing likes were kept.")
        }
        if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
        val mergedIds = LibraryStore.mergeRemoteLikedSongs(context, songs.distinctBy { it.id })
        MusicPlayerState.replaceFavoriteIdsFromSync(context, mergedIds)
    }

    suspend fun syncSubscribedArtists(
        context: Context,
        expectedSessionKey: String? = GoogleAuthStore.activeSessionKey(),
    ) {
        if (!GoogleAuthStore.isSignedIn || expectedSessionKey == null) return
        val corpusCards = fetchAllLibraryCards("FEmusic_library_corpus_artists", BrowseType.ARTIST)
        val trackArtistCards = if (corpusCards.isEmpty()) {
            fetchAllLibraryCards("FEmusic_library_corpus_track_artists", BrowseType.ARTIST)
        } else {
            emptyList()
        }
        val webChannels = if (corpusCards.isEmpty() && trackArtistCards.isEmpty()) {
            runCatching {
                YouTubeAccountParsers.parseLibraryItems(
                    ArtistInnertubeClient.postWebBrowse("FEchannels"),
                    BrowseType.ARTIST,
                ).items
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val cards = (corpusCards + trackArtistCards + webChannels).distinctBy { it.browseId }
        if (cards.isEmpty()) {
            throw IllegalStateException("YouTube returned no recognizable subscribed artists. Existing artists were kept.")
        }
        val entries = cards.map { card ->
            SavedCollectionEntry(
                browseId = card.browseId,
                title = card.title,
                subtitle = card.subtitle.ifBlank { "Artist" },
                thumbnailUrl = card.thumbnailUrl,
                type = BrowseType.ARTIST,
                isExplicit = card.isExplicit,
                isYtmSynced = true,
                savedLocally = false,
            )
        }
        if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
        val mergedIds = LibraryStore.mergeRemoteArtists(context, entries)
        ArtistStateStore.replaceSubscribedFromSync(context, mergedIds)
    }

    suspend fun syncSavedAlbums(
        context: Context,
        expectedSessionKey: String? = GoogleAuthStore.activeSessionKey(),
    ) {
        if (!GoogleAuthStore.isSignedIn || expectedSessionKey == null) return
        val cards = fetchAllLibraryCards("FEmusic_liked_albums", BrowseType.ALBUM)
        if (cards.isEmpty()) {
            throw IllegalStateException("YouTube returned no recognizable saved albums. Existing albums were kept.")
        }
        val entries = cards.map { card ->
            SavedCollectionEntry(
                browseId = card.browseId,
                title = card.title,
                subtitle = card.subtitle.ifBlank { "Album" },
                thumbnailUrl = card.thumbnailUrl,
                type = BrowseType.ALBUM,
                isExplicit = card.isExplicit,
                isYtmSynced = true,
                savedLocally = false,
                playlistId = card.playlistId,
            )
        }
        if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
        val mergedAlbumIds = LibraryStore.mergeRemoteAlbums(context, entries)
        ArtistStateStore.mergeSavedReleaseIdsFromSync(context, mergedAlbumIds)
    }

    suspend fun syncPlaylists(
        context: Context,
        expectedSessionKey: String? = GoogleAuthStore.activeSessionKey(),
    ) {
        if (!GoogleAuthStore.isSignedIn || expectedSessionKey == null) return
        // Fetch strictly from YouTube Music (`FEmusic_liked_playlists` via WEB_REMIX)
        // rather than YouTube's account-wide aggregation feed, which includes Saved Shorts
        // and non-music YouTube video playlists.
        val ytmCards = fetchAllLibraryCards("FEmusic_liked_playlists", BrowseType.PLAYLIST)
        val cards = ytmCards
            .asSequence()
            .filter { it.type == BrowseType.PLAYLIST }
            .filter {
                val clean = it.browseId.removePrefix("VL")
                clean.isNotBlank() &&
                    clean !in SYSTEM_AUTO_PLAYLIST_IDS &&
                    !clean.startsWith("FE") &&
                    !clean.startsWith("MPSP") &&
                    !clean.startsWith("MPED") &&
                    !clean.startsWith("UC")
            }
            .distinctBy { it.browseId.removePrefix("VL") }
            .toList()
        if (cards.isEmpty()) {
            throw IllegalStateException("YouTube returned no recognizable playlists. Existing playlists were kept.")
        }

        val existingByYtId = LibraryStore.userPlaylists.value
            .filter { it.isGoogleAccount && !it.ytPlaylistId.isNullOrBlank() }
            .associateBy { it.ytPlaylistId!! }

        val userOwned = mutableListOf<UserPlaylist>()
        val bookmarked = mutableListOf<SavedCollectionEntry>()

        cards.chunked(PLAYLIST_HYDRATE_BATCH_SIZE).forEach { batch ->
            coroutineScope {
                batch.map { card ->
                    async {
                        val cleanYtId = card.browseId.removePrefix("VL")
                        val browseId = "VL$cleanYtId"
                        val notEditablePrefix = NOT_EDITABLE_PLAYLIST_PREFIXES.any { cleanYtId.startsWith(it) }
                        val existingOwned = existingByYtId[cleanYtId]
                        val page = ArtistRepository.browseSongs(browseId).getOrElse { error ->
                            if (existingOwned != null) {
                                null
                            } else {
                                throw IllegalStateException(
                                    "Could not load YouTube playlist \"${card.title}\": ${error.message}",
                                    error,
                                )
                            }
                        }
                        val isUserOwned = !notEditablePrefix && (
                            page?.owned == true ||
                                (page == null && existingOwned != null)
                            )
                        if (isUserOwned) {
                            val syncedSongs = if (page != null) {
                                val allSongs = page.songs.toMutableList()
                                var token = page.continuation
                                var steps = 1
                                while (token != null && steps < MAX_PLAYLIST_TRACKS_PAGES) {
                                    val cont = ArtistRepository.moreSongs(token).getOrElse { error ->
                                        throw IllegalStateException(
                                            "Could not load tracks for \"${card.title}\": ${error.message}",
                                            error,
                                        )
                                    }
                                    allSongs.addAll(cont.songs)
                                    token = cont.continuation
                                    steps++
                                }
                                allSongs.distinctBy { it.setVideoId ?: it.videoId }.map { s ->
                                    SavedSongMetadata.fromSong(s, savedLocally = false, isYtmSynced = true)
                                }
                            } else {
                                existingOwned?.songs.orEmpty()
                            }
                            card to UserPlaylist(
                                id = "yt_$cleanYtId",
                                name = card.title,
                                songs = syncedSongs,
                                isGoogleAccount = true,
                                ytPlaylistId = cleanYtId,
                                isEditable = true,
                            )
                        } else {
                            card to SavedCollectionEntry(
                                browseId = browseId,
                                title = card.title,
                                subtitle = card.subtitle.ifBlank { "Playlist" },
                                thumbnailUrl = card.thumbnailUrl ?: page?.header?.thumbnailUrl,
                                type = BrowseType.PLAYLIST,
                                isExplicit = card.isExplicit,
                                isYtmSynced = true,
                                savedLocally = false,
                                playlistId = card.playlistId ?: cleanYtId,
                            )
                        }
                    }
                }.awaitAll().forEach { (_, resolved) ->
                    when (resolved) {
                        is UserPlaylist -> userOwned.add(resolved)
                        is SavedCollectionEntry -> bookmarked.add(resolved)
                    }
                }
            }
        }

        if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
        LibraryStore.mergeRemoteUserPlaylists(context, userOwned)
        val mergedPlIds = LibraryStore.mergeRemoteSavedPlaylists(context, bookmarked)
        ArtistStateStore.mergeSavedReleaseIdsFromSync(context, mergedPlIds)
    }

    private suspend fun fetchAllLibraryCards(
        browseId: String,
        expectedType: BrowseType,
    ): List<YouTubeAccountParsers.LibraryCardItem> {
        val all = mutableListOf<YouTubeAccountParsers.LibraryCardItem>()
        val first = ArtistInnertubeClient.browse(browseId)
        val parsedFirst = YouTubeAccountParsers.parseLibraryItems(first, expectedType)
        all.addAll(parsedFirst.items)
        var token = parsedFirst.continuation
        var pages = 1
        while (token != null && pages < MAX_LIBRARY_PAGES) {
            val cont = try {
                ArtistInnertubeClient.browseContinuation(token, allowAnonymousFallback = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                throw IllegalStateException("YouTube Music library continuation failed: ${e.message}", e)
            }
            val parsedNext = YouTubeAccountParsers.parseLibraryItems(cont, expectedType)
            all.addAll(parsedNext.items)
            token = parsedNext.continuation
            pages++
        }
        return all.distinctBy { it.browseId }
    }

    // --- Real-time mutations ---

    private fun reportMutationFailure(label: String, error: Throwable, sessionKey: String?) {
        Log.w(TAG, "$label failed: ${error.message}", error)
        if (sessionKey != null && GoogleAuthStore.activeSessionKey() == sessionKey) {
            GoogleAuthStore.updateSyncStatus(
                syncing = GoogleAuthStore.state.value.isSyncing,
                error = "$label was not saved to YouTube Music: ${error.message ?: "request failed"}",
                expectedSessionKey = sessionKey,
            )
        }
    }

    fun pushSongLikeAsync(trackId: String, liked: Boolean) {
        if (!GoogleAuthStore.isSignedIn || trackId.isBlank()) return
        val sessionKey = GoogleAuthStore.activeSessionKey()
        scope.launch {
            try {
                ArtistInnertubeClient.rateVideo(
                    videoId = trackId,
                    status = if (liked) LikeStatus.LIKE else LikeStatus.INDIFFERENT,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reportMutationFailure("Like update", e, sessionKey)
            }
        }
    }

    fun pushArtistSubscriptionAsync(channelId: String, subscribed: Boolean) {
        if (!GoogleAuthStore.isSignedIn || channelId.isBlank()) return
        val sessionKey = GoogleAuthStore.activeSessionKey()
        scope.launch {
            try {
                ArtistInnertubeClient.setSubscribed(channelId = channelId, subscribed = subscribed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reportMutationFailure("Artist subscription update", e, sessionKey)
            }
        }
    }

    fun pushReleaseSavedAsync(browseId: String, playlistIdHint: String?, saved: Boolean) {
        if (!GoogleAuthStore.isSignedIn || browseId.isBlank()) return
        val sessionKey = GoogleAuthStore.activeSessionKey()
        scope.launch {
            try {
                val resolvedPlaylistId = playlistIdHint?.takeIf { it.isNotBlank() }
                    ?: when {
                        browseId.startsWith("VL") -> browseId.removePrefix("VL")
                        browseId.startsWith("PL") || browseId.startsWith("OLAK") -> browseId
                        else -> ArtistRepository.browseSongs(browseId).getOrThrow().library?.playlistId
                    }
                    ?: error("Could not resolve the YouTube Music playlist ID for $browseId")
                ArtistInnertubeClient.ratePlaylist(resolvedPlaylistId, saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                reportMutationFailure("Album or playlist save update", e, sessionKey)
            }
        }
    }

    suspend fun createPlaylist(
        context: Context,
        name: String,
        target: PlaylistStorageTarget,
        initialSongs: List<SavedSongMetadata> = emptyList(),
    ): UserPlaylist {
        if (target == PlaylistStorageTarget.GOOGLE_ACCOUNT && !GoogleAuthStore.isSignedIn) {
            error("Sign in with Google before creating a YouTube Music playlist")
        }
        if (target == PlaylistStorageTarget.GOOGLE_ACCOUNT) {
            val ytId = withContext(Dispatchers.IO) {
                ArtistInnertubeClient.createPlaylist(
                    title = name.trim().ifBlank { "New Playlist" },
                    privacyStatus = "PRIVATE",
                    videoIds = initialSongs.map { it.id }.distinct(),
                )
            }
            val created = LibraryStore.createUserPlaylist(
                context = context,
                name = name,
                initialSongs = initialSongs,
                isGoogleAccount = true,
                ytPlaylistId = ytId,
            )
            // Fetch initial setVideoIds in background so individual tracks can be removed immediately
            if (initialSongs.isNotEmpty()) {
                scope.launch {
                    runCatching {
                        val page = ArtistRepository.browseSongs("VL$ytId").getOrNull()
                        val map = page?.songs.orEmpty()
                            .mapNotNull { s -> s.setVideoId?.let { s.videoId to it } }
                            .toMap()
                        LibraryStore.setSongSetVideoIds(context, created.id, map)
                    }
                }
            }
            return created
        }
        return LibraryStore.createUserPlaylist(
            context = context,
            name = name,
            initialSongs = initialSongs,
            isGoogleAccount = false,
            ytPlaylistId = null,
        )
    }

    suspend fun addSongsToPlaylist(
        context: Context,
        playlistId: String,
        songs: List<SavedSongMetadata>,
    ): Int = withContext(Dispatchers.IO) {
        val playlist = LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
            ?: error("Playlist no longer exists")
        val existingIds = playlist.songs.mapTo(HashSet()) { it.id }
        val freshSongs = songs.distinctBy { it.id }.filterNot { it.id in existingIds }
        if (freshSongs.isEmpty()) return@withContext 0

        var resultMap: Map<String, String> = emptyMap()
        if (playlist.isGoogleAccount) {
            if (!GoogleAuthStore.isSignedIn) {
                error("Sign in with Google before editing this YouTube Music playlist")
            }
            val ytId = playlist.ytPlaylistId?.takeIf { it.isNotBlank() }
                ?: error("This YouTube playlist is missing its remote playlist ID")
            // Commit remotely first. Local state changes only after YouTube accepts the edit.
            resultMap = ArtistInnertubeClient.addToPlaylist(
                ytId,
                freshSongs.map { it.id },
            )
        }

        val addedCount = LibraryStore.addSongsToUserPlaylist(context, playlistId, freshSongs)
        if (resultMap.isNotEmpty()) {
            LibraryStore.setSongSetVideoIds(context, playlistId, resultMap)
        }
        addedCount
    }

    private suspend fun resolvePlaylistSetVideoId(playlistId: String, songId: String): String? {
        val first = ArtistRepository.browseSongs("VL" + playlistId.removePrefix("VL")).getOrThrow()
        first.songs.firstOrNull { it.videoId == songId }?.setVideoId?.let { return it }
        var token = first.continuation
        var page = 1
        while (token != null && page < MAX_PLAYLIST_TRACKS_PAGES) {
            val next = ArtistRepository.moreSongs(token).getOrThrow()
            next.songs.firstOrNull { it.videoId == songId }?.setVideoId?.let { return it }
            token = next.continuation
            page++
        }
        return null
    }

    fun removeSongFromPlaylist(
        context: Context,
        playlistId: String,
        songId: String,
        onResult: (Result<Unit>) -> Unit = {},
    ) {
        scope.launch {
            val result = try {
                val playlist = LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
                    ?: error("Playlist no longer exists")
                val targetSong = playlist.songs.firstOrNull { it.id == songId }
                    ?: error("Track is no longer in this playlist")
                if (playlist.isGoogleAccount) {
                    if (!GoogleAuthStore.isSignedIn) {
                        error("Sign in with Google before editing this YouTube Music playlist")
                    }
                    val ytId = playlist.ytPlaylistId?.takeIf { it.isNotBlank() }
                        ?: error("This YouTube playlist is missing its remote playlist ID")
                    val setId = targetSong.setVideoId
                        ?: resolvePlaylistSetVideoId(ytId, songId)
                        ?: error("Could not resolve the YouTube playlist entry for this track")
                    // Do not remove the local entry unless the remote operation succeeds.
                    ArtistInnertubeClient.removeFromPlaylist(ytId, listOf(setId to songId))
                }
                LibraryStore.removeSongFromUserPlaylist(context, playlistId, songId)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "removeSongFromPlaylist($playlistId, $songId) failed: ${e.message}", e)
                Result.failure(e)
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    suspend fun renamePlaylist(context: Context, playlistId: String, newName: String) =
        withContext(Dispatchers.IO) {
            val cleanName = newName.trim()
            if (cleanName.isBlank()) error("Enter a playlist name")
            val playlist = LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
                ?: error("Playlist no longer exists")
            if (playlist.isGoogleAccount) {
                if (!GoogleAuthStore.isSignedIn) {
                    error("Sign in with Google before renaming this YouTube Music playlist")
                }
                val ytId = playlist.ytPlaylistId?.takeIf { it.isNotBlank() }
                    ?: error("This YouTube playlist is missing its remote playlist ID")
                ArtistInnertubeClient.renamePlaylist(ytId, cleanName)
            }
            LibraryStore.renameUserPlaylist(context, playlistId, cleanName)
        }

    fun deletePlaylist(
        context: Context,
        playlistId: String,
        onResult: (Result<Unit>) -> Unit = {},
    ) {
        scope.launch {
            val result = try {
                val playlist = LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
                    ?: error("Playlist no longer exists")
                if (playlist.isGoogleAccount) {
                    if (!GoogleAuthStore.isSignedIn) {
                        error("Sign in with Google before deleting this YouTube Music playlist")
                    }
                    val ytId = playlist.ytPlaylistId?.takeIf { it.isNotBlank() }
                        ?: error("This YouTube playlist is missing its remote playlist ID")
                    // Keep the playlist locally visible until YouTube confirms deletion.
                    ArtistInnertubeClient.deletePlaylist(ytId)
                }
                LibraryStore.deleteUserPlaylist(context, playlistId)
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "deletePlaylist($playlistId) failed: ${e.message}", e)
                Result.failure(e)
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun onAccountSessionChanged(
        context: Context,
        previousSessionKey: String?,
        newSessionKey: String?,
        launchSync: Boolean = true,
    ) {
        val app = context.applicationContext
        syncJob?.cancel()
        val (keptLikes, keptArtists, keptReleases) = LibraryStore.switchRemoteSession(
            context = app,
            previousSessionKey = previousSessionKey,
            targetSessionKey = newSessionKey,
        )
        MusicPlayerState.replaceFavoriteIdsFromSync(app, keptLikes)
        ArtistStateStore.replaceAllFromSignOut(app, keptArtists, keptReleases)
        PersonalizedHomeRepository.switchSession(newSessionKey)
        if (launchSync && !newSessionKey.isNullOrBlank()) {
            launchFullSync(app)
        }
    }

    fun switchAccountOrProfile(
        context: Context,
        accountId: String,
        profileId: String? = null,
    ): Boolean {
        val app = context.applicationContext
        GoogleAuthStore.ensureInit(app)
        val previousKey = GoogleAuthStore.activeSessionKey()
        val targetAccount = GoogleAuthStore.state.value.accounts.firstOrNull { it.accountId == accountId } ?: return false
        val resolvedPid = profileId ?: targetAccount.activeProfileId ?: targetAccount.profiles.firstOrNull()?.profileId
        val targetKey = "$accountId:${resolvedPid.orEmpty()}"
        if (previousKey == targetKey) return false
        if (!GoogleAuthStore.switchProfile(app, accountId, resolvedPid)) return false
        val newKey = GoogleAuthStore.activeSessionKey()
        onAccountSessionChanged(app, previousKey, newKey, launchSync = true)
        return true
    }

    fun removeAccountAndSwitch(context: Context, accountId: String): Boolean {
        val app = context.applicationContext
        GoogleAuthStore.ensureInit(app)
        val targetSession = GoogleAuthStore.state.value.accounts.firstOrNull { it.accountId == accountId }
        val wasActive = GoogleAuthStore.state.value.activeAccountId == accountId
        val stillSignedIn = GoogleAuthStore.removeAccount(app, accountId)
        targetSession?.profiles?.forEach { p ->
            LibraryStore.removeRemoteSnapshot(app, "$accountId:${p.profileId}")
        }
        LibraryStore.removeRemoteSnapshot(app, "$accountId:")
        PersonalizedHomeRepository.clearAccountCache(accountId)

        if (stillSignedIn) {
            if (wasActive) {
                val newKey = GoogleAuthStore.activeSessionKey()
                onAccountSessionChanged(app, previousSessionKey = null, newSessionKey = newKey, launchSync = true)
            }
        } else {
            syncJob?.cancel()
            val (keptLikes, keptArtists, keptReleases) = LibraryStore.clearGoogleAccountDataOnSignOut(app)
            MusicPlayerState.replaceFavoriteIdsFromSync(app, keptLikes)
            ArtistStateStore.replaceAllFromSignOut(app, keptArtists, keptReleases)
            PersonalizedHomeRepository.clear()
        }
        return stillSignedIn
    }

    fun signOutAndPreserveLocal(context: Context) {
        val app = context.applicationContext
        syncJob?.cancel()
        val (keptLikes, keptArtists, keptReleases) = LibraryStore.clearGoogleAccountDataOnSignOut(app)
        MusicPlayerState.replaceFavoriteIdsFromSync(app, keptLikes)
        ArtistStateStore.replaceAllFromSignOut(app, keptArtists, keptReleases)
        GoogleAuthStore.signOut(app)
        PersonalizedHomeRepository.clear()
    }
}
