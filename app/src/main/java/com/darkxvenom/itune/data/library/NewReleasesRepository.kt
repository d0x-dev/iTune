package com.darkxvenom.itune.data.library

import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.NewReleaseAlbum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared live repository for regional New Releases (`FEmusic_new_releases_albums`) and
 * localized Explore Playlists, driven by active Content Language (`hl`), Content Country (`gl`),
 * and Hide Explicit Content preference.
 */
object NewReleasesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fetchJob: Job? = null
    @Volatile
    private var syncedLocaleSignature: String? = null

    private val _releases = MutableStateFlow<List<NewReleaseAlbum>>(emptyList())
    val releases: StateFlow<List<NewReleaseAlbum>> = _releases.asStateFlow()

    private val _featuredPlaylists = MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val featuredPlaylists: StateFlow<List<MusicPlaylistRef>> = _featuredPlaylists.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun ensureLoaded() {
        val curSig = SettingsStore.localeSignature()
        if ((syncedLocaleSignature == curSig && _releases.value.isNotEmpty()) || fetchJob?.isActive == true) {
            return
        }
        refresh()
    }

    fun refresh() {
        val targetSig = SettingsStore.localeSignature()
        fetchJob?.cancel()
        fetchJob = scope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val releasesDeferred = async { MusicBackend.fetchNewReleaseAlbums() }
                val playlistsDeferred = async { MusicBackend.fetchLocalizedExplorePlaylists() }
                val fetchedReleases = releasesDeferred.await()
                val fetchedPlaylists = playlistsDeferred.await()
                if (fetchedReleases.isNotEmpty()) {
                    _releases.value = fetchedReleases
                    syncedLocaleSignature = targetSig
                } else if (_releases.value.isEmpty()) {
                    _errorMessage.value = "Couldn't load new releases"
                }
                if (fetchedPlaylists.isNotEmpty()) {
                    _featuredPlaylists.value = fetchedPlaylists
                    syncedLocaleSignature = targetSig
                }
            } catch (e: Exception) {
                if (_releases.value.isEmpty()) {
                    _errorMessage.value = e.message ?: "Couldn't load new releases"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}
