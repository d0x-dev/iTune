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
import android.content.SharedPreferences
import android.util.Log
import com.darkxvenom.itune.data.artist.normalizeForMatch
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class LinkedSpotifyPlaylist(
    val localPlaylistId: String,
    val playlistName: String,
    val spotifyPlaylistId: String,
    val spotifyUrl: String,
    val languagePreference: String? = null,
    val knownTrackKeys: Set<String> = emptySet(),
    val autoSyncEnabled: Boolean = true,
    val lastSyncedAtMs: Long = System.currentTimeMillis(),
)

/**
 * Persists Spotify playlist URLs imported with "Add Future Songs Automatically"
 * and checks them on app open to fetch & append any newly added songs to the
 * matching local iTune playlist without duplicating already-imported tracks.
 */
object SpotifyPlaylistAutoSyncManager {
    private const val TAG = "SpotifyAutoSync"
    private const val PREFS_NAME = "itune_spotify_playlist_sync"
    private const val KEY_LINKED_PLAYLISTS = "linked_playlists_v1"
    private const val MIN_SYNC_INTERVAL_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var lastSyncTriggerMs: Long = 0L

    private val _linkedPlaylists = MutableStateFlow<List<LinkedSpotifyPlaylist>>(emptyList())
    val linkedPlaylists: StateFlow<List<LinkedSpotifyPlaylist>> = _linkedPlaylists.asStateFlow()

    fun ensureInit(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            _linkedPlaylists.value = loadAll(p)
        }
    }

    fun trackKey(title: String, artist: String): String =
        "${title.normalizeForMatch()}|${artist.normalizeForMatch()}"

    /**
     * Registers (or updates) a Spotify playlist URL for automatic future-song syncing.
     */
    fun registerAutoSync(
        context: Context,
        localPlaylistId: String,
        playlistName: String,
        spotifyPlaylistId: String,
        spotifyUrl: String,
        languagePreference: String?,
        initialSourceTracks: List<PlaylistImportTrack>,
    ) {
        ensureInit(context)
        val keys = initialSourceTracks.map { trackKey(it.title, it.artist) }.toSet()
        val entry = LinkedSpotifyPlaylist(
            localPlaylistId = localPlaylistId,
            playlistName = playlistName,
            spotifyPlaylistId = spotifyPlaylistId,
            spotifyUrl = spotifyUrl.trim().ifBlank { PlaylistLinkImporter.canonicalUrl(spotifyPlaylistId) },
            languagePreference = languagePreference?.takeIf { it.isNotBlank() && !it.equals("No Preference", ignoreCase = true) },
            knownTrackKeys = keys,
            autoSyncEnabled = true,
            lastSyncedAtMs = System.currentTimeMillis(),
        )
        synchronized(this) {
            val next = _linkedPlaylists.value.filterNot { it.localPlaylistId == localPlaylistId } + entry
            _linkedPlaylists.value = next
            persistAll(next)
        }
        Log.d(TAG, "Registered auto-sync for '$playlistName' ($spotifyPlaylistId) with ${keys.size} initial tracks")
    }

    fun removeLink(context: Context, localPlaylistId: String) {
        ensureInit(context)
        synchronized(this) {
            val next = _linkedPlaylists.value.filterNot { it.localPlaylistId == localPlaylistId }
            _linkedPlaylists.value = next
            persistAll(next)
        }
    }

    /**
     * Checks all auto-sync Spotify playlists in the background when the app opens.
     * Any song added to a tracked Spotify playlist since the last sync is matched
     * and appended automatically to the corresponding local iTune playlist.
     */
    fun launchSyncOnAppOpen(context: Context) {
        val appCtx = context.applicationContext
        ensureInit(appCtx)
        if (_linkedPlaylists.value.none { it.autoSyncEnabled }) return
        val now = System.currentTimeMillis()
        if (now - lastSyncTriggerMs < MIN_SYNC_INTERVAL_MS) return
        lastSyncTriggerMs = now
        scope.launch {
            syncAllNow(appCtx)
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    suspend fun syncAllNow(context: Context): Int = syncMutex.withLock {
        val appCtx = context.applicationContext
        ensureInit(appCtx)
        SettingsStore.ensureInit(appCtx)
        LibraryStore.ensureInit(appCtx)

        val entries = _linkedPlaylists.value
        if (entries.isEmpty()) return@withLock 0

        val existingUserPlaylists = LibraryStore.userPlaylists.value.associateBy { it.id }
        val updatedEntries = mutableListOf<LinkedSpotifyPlaylist>()
        var totalAdded = 0

        for (entry in entries) {
            val localPlaylist = existingUserPlaylists[entry.localPlaylistId]
            if (localPlaylist == null) {
                // Local playlist was deleted by the user — stop tracking it.
                Log.d(TAG, "Pruning deleted local playlist '${entry.playlistName}' (${entry.localPlaylistId})")
                continue
            }
            if (!entry.autoSyncEnabled) {
                updatedEntries += entry
                continue
            }

            val fetched = runCatching {
                PlaylistLinkImporter.fetchPlaylistTracks(entry.spotifyPlaylistId)
            }.onFailure { err ->
                Log.w(TAG, "Auto-sync fetch failed for '${entry.playlistName}' (${entry.spotifyUrl}): ${err.message}")
            }.getOrNull()

            if (fetched == null) {
                updatedEntries += entry
                continue
            }

            val (_, latestSourceTracks) = fetched
            val existingLocalKeys = localPlaylist.songs
                .map { trackKey(it.title, it.artists) }
                .toSet()
            val alreadySeenKeys = entry.knownTrackKeys + existingLocalKeys

            val newSourceTracks = latestSourceTracks.filter { src ->
                val key = trackKey(src.title, src.artist)
                key.isNotBlank() && key !in alreadySeenKeys
            }

            var matchSucceeded = true
            if (newSourceTracks.isNotEmpty()) {
                Log.d(TAG, "Found ${newSourceTracks.size} new song(s) in Spotify playlist '${entry.playlistName}'")
                val matchResult = runCatching {
                    PlaylistLinkImporter.matchTracks(
                        sourceTracks = newSourceTracks,
                        language = entry.languagePreference,
                    )
                }
                if (matchResult.isFailure) {
                    matchSucceeded = false
                    Log.w(TAG, "Auto-sync match failed for '${entry.playlistName}': ${matchResult.exceptionOrNull()?.message}")
                } else {
                    val (matchedTracks, _) = matchResult.getOrThrow()
                    if (matchedTracks.isNotEmpty()) {
                        val savedMeta = matchedTracks.map { SavedSongMetadata.fromTrack(it) }
                        val added = LibraryStore.addSongsToUserPlaylist(appCtx, entry.localPlaylistId, savedMeta)
                        totalAdded += added
                        Log.d(TAG, "Auto-added $added new song(s) to '${entry.playlistName}'")
                    }
                }
            }

            val mergedKeys = if (matchSucceeded) {
                entry.knownTrackKeys + latestSourceTracks.map { trackKey(it.title, it.artist) }
            } else {
                entry.knownTrackKeys
            }
            updatedEntries += entry.copy(
                knownTrackKeys = mergedKeys,
                lastSyncedAtMs = System.currentTimeMillis(),
            )
        }

        synchronized(this) {
            _linkedPlaylists.value = updatedEntries
            persistAll(updatedEntries)
        }
        totalAdded
    }

    private fun loadAll(p: SharedPreferences): List<LinkedSpotifyPlaylist> {
        val raw = p.getString(KEY_LINKED_PLAYLISTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val localId = obj.optString("localPlaylistId").takeIf { it.isNotBlank() } ?: continue
                    val spotifyId = obj.optString("spotifyPlaylistId").takeIf { it.isNotBlank() } ?: continue
                    val keysArr = obj.optJSONArray("knownTrackKeys")
                    val keys = buildSet {
                        if (keysArr != null) {
                            for (k in 0 until keysArr.length()) {
                                keysArr.optString(k).takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }
                    add(
                        LinkedSpotifyPlaylist(
                            localPlaylistId = localId,
                            playlistName = obj.optString("playlistName").ifBlank { "Imported Playlist" },
                            spotifyPlaylistId = spotifyId,
                            spotifyUrl = obj.optString("spotifyUrl").ifBlank { PlaylistLinkImporter.canonicalUrl(spotifyId) },
                            languagePreference = obj.optString("languagePreference").takeIf { it.isNotBlank() },
                            knownTrackKeys = keys,
                            autoSyncEnabled = obj.optBoolean("autoSyncEnabled", true),
                            lastSyncedAtMs = obj.optLong("lastSyncedAtMs", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistAll(list: List<LinkedSpotifyPlaylist>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("localPlaylistId", item.localPlaylistId)
                put("playlistName", item.playlistName)
                put("spotifyPlaylistId", item.spotifyPlaylistId)
                put("spotifyUrl", item.spotifyUrl)
                put("languagePreference", item.languagePreference.orEmpty())
                put("autoSyncEnabled", item.autoSyncEnabled)
                put("lastSyncedAtMs", item.lastSyncedAtMs)
                val keysArr = JSONArray()
                item.knownTrackKeys.forEach { keysArr.put(it) }
                put("knownTrackKeys", keysArr)
            }
            arr.put(obj)
        }
        prefs?.edit()?.putString(KEY_LINKED_PLAYLISTS, arr.toString())?.apply()
    }
}
