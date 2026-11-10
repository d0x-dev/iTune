package com.darkxvenom.itune.data.library

import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.NewReleaseAlbum
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared live repository for AirBeats New Releases (`FEmusic_new_releases_albums`),
 * shared between Library "Recently Added" and the full-screen Apple New Releases destination.
 */
object NewReleasesRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fetchJob: Job? = null

    private val _releases = MutableStateFlow<List<NewReleaseAlbum>>(emptyList())
    val releases: StateFlow<List<NewReleaseAlbum>> = _releases.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun ensureLoaded() {
