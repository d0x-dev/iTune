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
    private const val MAX_PLAYLIST_TRACKS_PAGES = 4
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
                refreshAccountMetadata(app, expectedKey)
                val failures = coroutineScope {
                    val likesJob = async { runCatching { syncLikedSongs(app, expectedKey) }.onFailure { Log.w(TAG, "syncLikedSongs failed: ${it.message}", it) } }
                    val artistsJob = async { runCatching { syncSubscribedArtists(app, expectedKey) }.onFailure { Log.w(TAG, "syncSubscribedArtists failed: ${it.message}", it) } }
                    val albumsJob = async { runCatching { syncSavedAlbums(app, expectedKey) }.onFailure { Log.w(TAG, "syncSavedAlbums failed: ${it.message}", it) } }
                    val playlistsJob = async { runCatching { syncPlaylists(app, expectedKey) }.onFailure { Log.w(TAG, "syncPlaylists failed: ${it.message}", it) } }
                    val historyJob = async { runCatching { syncHistory(app, expectedKey) }.onFailure { Log.w(TAG, "syncHistory failed: ${it.message}", it) } }
                    val homeJob = async { runCatching { PersonalizedHomeRepository.refreshIfSignedIn() }.onFailure { Log.w(TAG, "home refresh failed: ${it.message}", it) } }
                    awaitAll(likesJob, artistsJob, albumsJob, playlistsJob, historyJob, homeJob)
                }.mapNotNull { it.exceptionOrNull() }

                if (GoogleAuthStore.activeSessionKey() != expectedKey) return@withLock
                val syncError = if (failures.size >= 5) {
                    failures.firstOrNull()?.message ?: "Could not retrieve library from Google account"
                } else {
                    null
                }
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
        val first = runCatching { ArtistInnertubeClient.browse("FEmusic_history") }.getOrNull()
        val collected = mutableListOf<SavedSongMetadata>()
        val (firstSongs, firstToken) = first?.let { YouTubeAccountParsers.parseHistoryTracks(it) }
            ?: (emptyList<com.darkxvenom.itune.data.model.Song>() to null)
        firstSongs.forEach { song ->
            collected.add(SavedSongMetadata.fromSong(song = song, savedLocally = false, isYtmSynced = true))
        }
        var token = firstToken
        var pageCount = 1
        while (token != null && pageCount < 3) {
            if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
            val cont = runCatching { ArtistInnertubeClient.browseContinuation(token!!, allowAnonymousFallback = false) }.getOrNull() ?: break
            val (nextSongs, nextToken) = YouTubeAccountParsers.parseHistoryTracks(cont)
            nextSongs.forEach { song ->
                collected.add(SavedSongMetadata.fromSong(song = song, savedLocally = false, isYtmSynced = true))
            }
            token = nextToken
            pageCount++
        }
        if (collected.isEmpty()) {
            val webHistory = runCatching { ArtistInnertubeClient.postWebBrowse("FEhistory") }.getOrNull()
            if (webHistory != null) {
                YouTubeAccountParsers.parseHistoryTracks(webHistory).first.forEach { song ->
                    collected.add(SavedSongMetadata.fromSong(song = song, savedLocally = false, isYtmSynced = true))
                }
            }
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
        val firstPage = runCatching { ArtistRepository.browseSongs("VLLM").getOrNull() }.getOrNull()
        if (firstPage != null && firstPage.songs.isNotEmpty()) {
            firstPage.songs.forEach { s ->
                songs.add(SavedSongMetadata.fromSong(s, savedLocally = false, isYtmSynced = true))
            }
            var token = firstPage.continuation
            var count = 1
            while (token != null && count < MAX_LIBRARY_PAGES) {
                if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
                val next = ArtistRepository.moreSongs(token).getOrNull() ?: break
                next.songs.forEach { s ->
                    songs.add(SavedSongMetadata.fromSong(s, savedLocally = false, isYtmSynced = true))
                }
                token = next.continuation
                count++
            }
        }
        if (songs.isEmpty()) {
            val likedResp = runCatching { ArtistInnertubeClient.browse("FEmusic_liked_videos") }.getOrNull()
                ?: runCatching { ArtistInnertubeClient.postWebBrowse("VLLL") }.getOrNull()
            if (likedResp != null) {
                val (parsed, _) = YouTubeAccountParsers.parseHistoryTracks(likedResp)
                parsed.forEach { s ->
                    songs.add(SavedSongMetadata.fromSong(s, savedLocally = false, isYtmSynced = true))
                }
            }
        }
        if (GoogleAuthStore.activeSessionKey() != expectedSessionKey) return
        if (songs.isNotEmpty()) {
            val mergedIds = LibraryStore.mergeRemoteLikedSongs(context, songs.distinctBy { it.id })
            MusicPlayerState.replaceFavoriteIdsFromSync(context, mergedIds)
        }
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
                        val page = runCatching { ArtistRepository.browseSongs(browseId).getOrNull() }.getOrNull()
                        val existingOwned = existingByYtId[cleanYtId]
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
                                    val cont = ArtistRepository.moreSongs(token).getOrNull() ?: break
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
            val cont = runCatching {
                ArtistInnertubeClient.browseContinuation(token!!, allowAnonymousFallback = false)
            }.getOrNull() ?: break
            val parsedNext = YouTubeAccountParsers.parseLibraryItems(cont, expectedType)
            all.addAll(parsedNext.items)
            token = parsedNext.continuation
            pages++
        }
        return all.distinctBy { it.browseId }
    }

    // --- Real-time mutations ---

    fun pushSongLikeAsync(trackId: String, liked: Boolean) {
        if (!GoogleAuthStore.isSignedIn || trackId.isBlank()) return
        scope.launch {
            runCatching {
                ArtistInnertubeClient.rateVideo(
                    videoId = trackId,
                    status = if (liked) LikeStatus.LIKE else LikeStatus.INDIFFERENT,
                )
            }.onFailure {
                Log.w(TAG, "rateVideo($trackId, $liked) failed: ${it.message}")
            }
        }
    }

    fun pushArtistSubscriptionAsync(channelId: String, subscribed: Boolean) {
        if (!GoogleAuthStore.isSignedIn || channelId.isBlank()) return
        scope.launch {
            runCatching {
                ArtistInnertubeClient.setSubscribed(channelId = channelId, subscribed = subscribed)
            }.onFailure {
                Log.w(TAG, "setSubscribed($channelId, $subscribed) failed: ${it.message}")
            }
        }
    }

    fun pushReleaseSavedAsync(browseId: String, playlistIdHint: String?, saved: Boolean) {
        if (!GoogleAuthStore.isSignedIn || browseId.isBlank()) return
        scope.launch {
            runCatching {
                val resolvedPlaylistId = playlistIdHint?.takeIf { it.isNotBlank() }
                    ?: when {
                        browseId.startsWith("VL") -> browseId.removePrefix("VL")
                        browseId.startsWith("PL") || browseId.startsWith("OLAK") -> browseId
                        else -> ArtistRepository.browseSongs(browseId).getOrNull()?.library?.playlistId
                    }
                if (resolvedPlaylistId.isNullOrBlank()) {
                    Log.w(TAG, "No playlistId found to save $browseId on YTM")
                    return@runCatching
                }
                ArtistInnertubeClient.ratePlaylist(resolvedPlaylistId, saved)
            }.onFailure {
                Log.w(TAG, "ratePlaylist($browseId, $saved) failed: ${it.message}")
            }
        }
    }

    suspend fun createPlaylist(
        context: Context,
        name: String,
        target: PlaylistStorageTarget,
        initialSongs: List<SavedSongMetadata> = emptyList(),
    ): UserPlaylist {
        if (target == PlaylistStorageTarget.GOOGLE_ACCOUNT && GoogleAuthStore.isSignedIn) {
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

    fun addSongsToPlaylist(
        context: Context,
        playlistId: String,
        songs: List<SavedSongMetadata>,
    ): Int {
        val addedCount = LibraryStore.addSongsToUserPlaylist(context, playlistId, songs)
        val playlist = LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
        val ytId = playlist?.ytPlaylistId
        if (addedCount > 0 && playlist?.isGoogleAccount == true && !ytId.isNullOrBlank() && GoogleAuthStore.isSignedIn) {
            scope.launch {
                runCatching {
                    val resultMap = ArtistInnertubeClient.addToPlaylist(ytId, songs.map { it.id }.distinct())
                    LibraryStore.setSongSetVideoIds(context, playlistId, resultMap)
                }.onFailure {
                    Log.w(TAG, "addToPlaylist($ytId) failed: ${it.message}")
                }
            }
        }
        return addedCount
    }

    fun removeSongFromPlaylist(
        context: Context,
        playlistId: String,
        songId: String,
    ) {
        val playlist = LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
        val targetSong = playlist?.songs?.firstOrNull { it.id == songId }
        val ytId = playlist?.ytPlaylistId
        val setId = targetSong?.setVideoId
        LibraryStore.removeSongFromUserPlaylist(context, playlistId, songId)
        if (playlist?.isGoogleAccount == true && !ytId.isNullOrBlank() && GoogleAuthStore.isSignedIn) {
            scope.launch {
                runCatching {
                    val resolvedSetId = setId ?: ArtistRepository.browseSongs("VL$ytId").getOrNull()
                        ?.songs?.firstOrNull { it.videoId == songId }?.setVideoId
                    if (resolvedSetId != null) {
                        ArtistInnertubeClient.removeFromPlaylist(ytId, listOf(resolvedSetId to songId))
                    }
                }.onFailure {
                    Log.w(TAG, "removeFromPlaylist($ytId, $songId) failed: ${it.message}")
                }
            }
        }
    }

    fun renamePlaylist(context: Context, playlistId: String, newName: String) {
        val playlist = LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
        val ytId = playlist?.ytPlaylistId
        LibraryStore.renameUserPlaylist(context, playlistId, newName)
        if (playlist?.isGoogleAccount == true && !ytId.isNullOrBlank() && GoogleAuthStore.isSignedIn) {
            scope.launch {
                runCatching { ArtistInnertubeClient.renamePlaylist(ytId, newName.trim()) }
                    .onFailure { Log.w(TAG, "renamePlaylist($ytId) failed: ${it.message}") }
            }
        }
    }

    fun deletePlaylist(context: Context, playlistId: String) {
        val playlist = LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
        val ytId = playlist?.ytPlaylistId
        LibraryStore.deleteUserPlaylist(context, playlistId)
        if (playlist?.isGoogleAccount == true && !ytId.isNullOrBlank() && GoogleAuthStore.isSignedIn) {
            scope.launch {
                runCatching { ArtistInnertubeClient.deletePlaylist(ytId) }
                    .onFailure { Log.w(TAG, "deletePlaylist($ytId) failed: ${it.message}") }
            }
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
