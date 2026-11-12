package com.darkxvenom.itune.data.library

import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Anonymous discovery feed. All items originate from YouTube Music's public Innertube catalog;
 * this intentionally does not synthesize track metadata or require a Google session.
 */
object HomeDiscoveryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _trendingTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val trendingTracks: StateFlow<List<MusicTrack>> = _trendingTracks.asStateFlow()
    private val _popularPlaylists = MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val popularPlaylists: StateFlow<List<MusicPlaylistRef>> = _popularPlaylists.asStateFlow()
    private val _newAlbums = MutableStateFlow<List<com.darkxvenom.itune.backend.NewReleaseAlbum>>(emptyList())
    val newAlbums: StateFlow<List<com.darkxvenom.itune.backend.NewReleaseAlbum>> = _newAlbums.asStateFlow()
    private var loading = false

    @Synchronized
    fun ensureLoaded() {
        if (loading || (_trendingTracks.value.isNotEmpty() && _popularPlaylists.value.isNotEmpty())) return
        loading = true
        scope.launch {
            try {
                val tracks = async { MusicBackend.searchMusic("trending songs") }
                val playlists = async { MusicBackend.fetchLocalizedExplorePlaylists() }
                val albums = async { MusicBackend.fetchNewReleaseAlbums() }
                _trendingTracks.value = tracks.await().distinctBy { it.id }.take(32)
                _popularPlaylists.value = playlists.await().distinctBy { it.id }.take(18)
                _newAlbums.value = albums.await().distinctBy { it.browseId }.take(18)
            } finally {
                loading = false
            }
        }
    }

    fun refresh() {
        synchronized(this) { loading = false }
        ensureLoaded()
    }
}
