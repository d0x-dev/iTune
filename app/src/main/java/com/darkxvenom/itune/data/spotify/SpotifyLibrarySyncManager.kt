/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.data.spotify

import android.content.Context
import android.util.Log
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.library.UserPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Syncs the signed-in user's Spotify playlists into LibraryStore's unified UserPlaylist list
 * and hydrates/resolves Spotify playlist tracks for seamless playback in iTune's standard
 * playlist UI.
 */
object SpotifyLibrarySyncManager {
    private const val TAG = "SpotifyLibSync"
    const val UNRESOLVED_PREFIX = "sp_track:"
    private const val MIN_SYNC_INTERVAL_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val hydrateLocks = ConcurrentHashMap<String, Mutex>()

    @Volatile
    private var lastSyncMs: Long = 0L

    fun isUnresolvedSpotifyTrackId(id: String): Boolean = id.startsWith(UNRESOLVED_PREFIX)

    fun launchSync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        SettingsStore.ensureInit(app)
        if (SettingsStore.state.value.spotifySpdcToken.isBlank()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSyncMs < MIN_SYNC_INTERVAL_MS) return
        lastSyncMs = now
        scope.launch {
            runCatching { syncPlaylists(app) }
                .onFailure { Log.w(TAG, "Spotify playlist sync failed: ${it.message}") }
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    suspend fun syncPlaylists(context: Context) = syncMutex.withLock {
        val app = context.applicationContext
        SettingsStore.ensureInit(app)
        LibraryStore.ensureInit(app)
        if (SettingsStore.state.value.spotifySpdcToken.isBlank()) {
            LibraryStore.clearSpotifyAccountDataOnSignOut(app)
            return@withLock
        }

        val remote = SpotifyLibrary.playlists()
            .filter { it.id != SpotifyLibrary.LIKED_ID && it.id.isNotBlank() }

        val mapped = remote.map { sp ->
            UserPlaylist(
                id = "sp_${sp.id}",
                name = sp.name,
                songs = emptyList(),
                isGoogleAccount = false,
                ytPlaylistId = null,
                isSpotifyAccount = true,
                spotifyPlaylistId = sp.id,
                isPublic = false,
                coverImageUrl = sp.imageUrl,
                isEditable = true,
            )
        }
        LibraryStore.mergeRemoteSpotifyPlaylists(app, mapped)
        Log.d(TAG, "Synced ${mapped.size} Spotify playlist(s) into Library")
    }

    /**
     * Fetches the latest tracks for a Spotify playlist from Spotify and resolves them to
     * playable YouTube Music tracks so the standard UserPlaylistDetailScreen plays them
     * identically to YouTube Music playlists.
     */
    suspend fun hydratePlaylistTracks(context: Context, localPlaylistId: String) {
        val app = context.applicationContext
        val pl = LibraryStore.userPlaylists.value.firstOrNull { it.id == localPlaylistId } ?: return
        val spId = pl.spotifyPlaylistId?.takeIf { it.isNotBlank() } ?: return
        if (SettingsStore.state.value.spotifySpdcToken.isBlank()) return

        val lock = hydrateLocks.computeIfAbsent(localPlaylistId) { Mutex() }
        lock.withLock {
            try {
                val existingBySpUri = pl.songs
                    .filter { !it.spotifyTrackUri.isNullOrBlank() && !isUnresolvedSpotifyTrackId(it.id) }
                    .associateBy { it.spotifyTrackUri!! }
                val existingByTitleArtist = pl.songs
                    .filter { !isUnresolvedSpotifyTrackId(it.id) }
                    .associateBy { "${it.title.lowercase()}|${it.artists.lowercase()}" }

                val spTracks = SpotifyLibrary.tracks(spId)
                val initialList = spTracks.map { st ->
                    val uri = "spotify:track:${st.id}"
                    existingBySpUri[uri]
                        ?: existingByTitleArtist["${st.title.lowercase()}|${st.artist.lowercase()}"]?.copy(spotifyTrackUri = uri)
                        ?: SavedSongMetadata(
                            id = "$UNRESOLVED_PREFIX${st.id}",
                            title = st.title,
                            artists = st.artist,
                            album = st.album,
                            thumbnailUrl = st.imageUrl.orEmpty(),
                            durationSec = (st.durationMs / 1000L).coerceAtLeast(0L),
                            spotifyTrackUri = uri,
                            isExplicit = st.isExplicit,
                        )
                }

                // Publish tracks immediately so the user sees the full playlist right away
                LibraryStore.replaceUserPlaylistSongs(app, localPlaylistId, initialList)

                // Resolve any placeholder Spotify tracks to playable YTM tracks in parallel
                val unresolvedIndices = initialList.indices.filter { isUnresolvedSpotifyTrackId(initialList[it].id) }
                if (unresolvedIndices.isEmpty()) return@withLock

                val resolvedList = initialList.toMutableList()
                val sem = Semaphore(4)
                coroutineScope {
                    unresolvedIndices.map { idx ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                val st = spTracks[idx]
                                val matched: MusicTrack? = runCatching {
                                    SpotifyImporter.matchTrack(st)
                                }.getOrNull()
                                if (matched != null) {
                                    resolvedList[idx] = SavedSongMetadata.fromTrack(matched).copy(
                                        spotifyTrackUri = "spotify:track:${st.id}",
                                        thumbnailUrl = matched.thumbnailUrl?.takeIf { it.isNotBlank() }
                                            ?: st.imageUrl.orEmpty(),
                                        isExplicit = matched.isExplicit || st.isExplicit,
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
                LibraryStore.replaceUserPlaylistSongs(app, localPlaylistId, resolvedList)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "hydratePlaylistTracks($localPlaylistId) failed: ${e.message}")
            }
        }
    }

    /**
     * Converts a single unresolved `sp_track:<id>` placeholder song to a playable YouTube Music
     * track on demand (used if the user taps play before background hydration finishes).
     */
    suspend fun resolveSingleTrackOnDemand(song: SavedSongMetadata): MusicTrack? = withContext(Dispatchers.IO) {
        if (!isUnresolvedSpotifyTrackId(song.id)) return@withContext song.toMusicTrack()
        val spTrack = SpotifyTrack(
            id = song.id.removePrefix(UNRESOLVED_PREFIX),
            title = song.title,
            artist = song.artists,
            album = song.album,
            durationMs = (song.durationSec * 1000L).toInt(),
            imageUrl = song.thumbnailUrl.ifBlank { null },
        )
        runCatching { SpotifyImporter.matchTrack(spTrack) }.getOrNull()
    }
}
