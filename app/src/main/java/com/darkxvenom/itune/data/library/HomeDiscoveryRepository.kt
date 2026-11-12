package com.darkxvenom.itune.data.library

import com.darkxvenom.itune.backend.MusicArtist
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Anonymous YouTube Music discovery used when there is no Google account connected.
 * Catalog entries are fetched from YouTube Music search/Explore endpoints, never fabricated.
 */
object HomeDiscoveryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var loading = false
    @Volatile private var loadedLocale: String? = null

    private val _trendingTracks = kotlinx.coroutines.flow.MutableStateFlow<List<MusicTrack>>(emptyList())
    val trendingTracks = _trendingTracks.asStateFlow()

    private val _popularPlaylists = kotlinx.coroutines.flow.MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val popularPlaylists = _popularPlaylists.asStateFlow()

    private val _popularAlbums = kotlinx.coroutines.flow.MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val popularAlbums = _popularAlbums.asStateFlow()

    private val _popularArtists = kotlinx.coroutines.flow.MutableStateFlow<List<MusicArtist>>(emptyList())
    val popularArtists = _popularArtists.asStateFlow()

    private val _podcastShows = kotlinx.coroutines.flow.MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val podcastShows = _podcastShows.asStateFlow()

    fun ensureLoaded() {
        val locale = SettingsStore.localeSignature()
        if (loadedLocale == locale || loading) return
        synchronized(this) {
            if (loadedLocale == locale || loading) return
            loading = true
        }
        scope.launch {
            try {
                val country = ContentLocaleCatalog.countryLabel(SettingsStore.state.value.contentCountry)
                    .takeUnless { it == "System Default" }
                    ?: SettingsStore.effectiveGl()
                val musicQuery = "trending songs $country"
                val podcastQuery = "popular podcasts $country"
                val (music, explore, releases, podcasts) = kotlinx.coroutines.coroutineScope {
                    val musicJob = async { MusicBackend.searchAllCategories(musicQuery) }
                    val exploreJob = async { MusicBackend.fetchLocalizedExplorePlaylists() }
                    val releasesJob = async { MusicBackend.fetchNewReleaseAlbums() }
                    val podcastsJob = async { MusicBackend.searchAllCategories(podcastQuery) }
                    listOf(musicJob.await(), exploreJob.await(), releasesJob.await(), podcastsJob.await())
                }.let { values ->
                    @Suppress("UNCHECKED_CAST")
                    val musicResult = values[0] as com.darkxvenom.itune.backend.UnifiedSearchResults
                    @Suppress("UNCHECKED_CAST")
                    val exploreResult = values[1] as List<MusicPlaylistRef>
                    @Suppress("UNCHECKED_CAST")
                    val releaseResult = values[2] as List<com.darkxvenom.itune.backend.NewReleaseAlbum>
                    @Suppress("UNCHECKED_CAST")
                    val podcastResult = values[3] as com.darkxvenom.itune.backend.UnifiedSearchResults
                    listOf(musicResult, exploreResult, releaseResult, podcastResult)
                }
                val musicResult = music as com.darkxvenom.itune.backend.UnifiedSearchResults
                val exploreResult = explore as List<MusicPlaylistRef>
                val releaseResult = releases as List<com.darkxvenom.itune.backend.NewReleaseAlbum>
                val podcastResult = podcasts as com.darkxvenom.itune.backend.UnifiedSearchResults

                _trendingTracks.value = musicResult.songs.distinctBy { it.id }.take(36)
                _popularPlaylists.value = (musicResult.playlists + exploreResult)
                    .filterNot { it.isAlbum }
                    .distinctBy { it.id }
                    .take(24)
                _popularAlbums.value = (musicResult.albums + releaseResult.map { album ->
                    MusicPlaylistRef(
                        id = album.browseId,
                        title = album.title,
                        author = album.artist,
                        authorId = album.artistId,
                        thumbnailUrl = album.thumbnailUrl,
                        isAlbum = true,
                        year = album.year?.toString(),
                    )
                }).distinctBy { it.id }.take(24)
                _popularArtists.value = musicResult.artists.distinctBy { it.id }.take(24)
                _podcastShows.value = (podcastResult.playlists + podcastResult.albums)
                    .filter {
                        it.id.startsWith("MPSP", ignoreCase = true) ||
                            it.title.contains("podcast", ignoreCase = true) ||
                            it.author.contains("podcast", ignoreCase = true)
                    }
                    .distinctBy { it.id }
                    .take(18)
                loadedLocale = locale
            } finally {
                synchronized(this) { loading = false }
            }
        }
    }

    fun refresh() {
        synchronized(this) { loadedLocale = null }
        ensureLoaded()
    }
}
