package com.darkxvenom.itune.data.library

import com.darkxvenom.itune.backend.MusicArtist
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.NewReleaseAlbum
import com.darkxvenom.itune.backend.UnifiedSearchResults
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import com.darkxvenom.itune.data.artist.ArtistInnertubeParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * YouTube Music-backed discovery for signed-out users and taste-based suggestions for signed-in
 * users. Catalog entries always come from live YouTube Music responses; no synthetic tracks.
 */
object HomeDiscoveryRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var loading = false
    @Volatile private var loadedLocale: String? = null
    @Volatile private var tasteSessionKey: String? = null
    @Volatile private var tasteRequestKey: String? = null

    private val _trendingTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val trendingTracks = _trendingTracks.asStateFlow()

    private val _popularPlaylists = MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val popularPlaylists = _popularPlaylists.asStateFlow()

    private val _popularAlbums = MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val popularAlbums = _popularAlbums.asStateFlow()

    private val _popularArtists = MutableStateFlow<List<MusicArtist>>(emptyList())
    val popularArtists = _popularArtists.asStateFlow()

    private val _podcastShows = MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val podcastShows = _podcastShows.asStateFlow()

    private val _tasteTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val tasteTracks = _tasteTracks.asStateFlow()

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
                val fetched = coroutineScope {
                    val music = async { MusicBackend.searchAllCategories("trending songs $country") }
                    val explore = async { MusicBackend.fetchLocalizedExplorePlaylists() }
                    val releases = async { MusicBackend.fetchNewReleaseAlbums() }
                    val podcasts = async { MusicBackend.searchAllCategories("popular podcasts $country") }
                    val podcastBrowse = async {
                        runCatching {
                            ArtistInnertubeParser.parseLibraryItemPage(
                                ArtistInnertubeClient.browse("FEmusic_podcasts")
                            ).items
                                .filter { it.browseId?.startsWith("MPSP", ignoreCase = true) == true }
                                .map { item ->
                                    MusicPlaylistRef(
                                        id = item.browseId.orEmpty(),
                                        title = item.title,
                                        author = item.subtitle,
                                        thumbnailUrl = item.thumbnailUrl,
                                        isAlbum = false,
                                    )
                                }
                        }.getOrDefault(emptyList())
                    }
                    DiscoveryData(
                        music = music.await(),
                        explore = explore.await(),
                        releases = releases.await(),
                        podcasts = podcasts.await(),
                        podcastBrowse = podcastBrowse.await(),
                    )
                }

                _trendingTracks.value = fetched.music.songs.distinctBy { it.id }.take(36)
                _popularPlaylists.value = (fetched.music.playlists + fetched.explore)
                    .filterNot { it.isAlbum }
                    .distinctBy { it.id }
                    .take(24)
                _popularAlbums.value = (fetched.music.albums + fetched.releases.map(::toPlaylistRef))
                    .distinctBy { it.id }
                    .take(24)
                _popularArtists.value = fetched.music.artists.distinctBy { it.id }.take(24)
                _podcastShows.value = (
                    fetched.podcastBrowse +
                        fetched.podcasts.playlists.filter { it.id.startsWith("MPSP", ignoreCase = true) } +
                        fetched.podcasts.albums.filter { it.id.startsWith("MPSP", ignoreCase = true) }
                    ).distinctBy { it.id }.take(18)
                loadedLocale = locale
            } finally {
                synchronized(this) { loading = false }
            }
        }
    }

    /**
     * Uses the actual artists a listener plays or follows as YouTube Music search seeds.
     * Results are scoped to the active account session to avoid showing another account's taste.
     */
    fun refreshFromListeningTaste(sessionKey: String?, artistNames: List<String>) {
        if (sessionKey.isNullOrBlank()) {
            tasteSessionKey = null
            tasteRequestKey = null
            _tasteTracks.value = emptyList()
            return
        }
        if (tasteSessionKey != sessionKey) {
            _tasteTracks.value = emptyList()
        }
        val artists = artistNames.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(4)
        val requestKey = "$sessionKey|${artists.joinToString("|")}"
        if (tasteRequestKey == requestKey) return
        tasteRequestKey = requestKey
        tasteSessionKey = sessionKey
        if (artists.isEmpty()) {
            _tasteTracks.value = emptyList()
            return
        }
        scope.launch {
            val tracks = coroutineScope {
                artists.map { artist ->
                    async { MusicBackend.searchMusic("songs by $artist") }
                }.flatMap { it.await() }
            }
            if (tasteSessionKey != sessionKey || tasteRequestKey != requestKey) return@launch
            _tasteTracks.value = tracks
                .distinctBy { it.id }
                .filter { track ->
                    artists.any { artist -> track.artists.contains(artist, ignoreCase = true) }
                }
                .take(24)
        }
    }

    fun refresh() {
        synchronized(this) { loadedLocale = null }
        ensureLoaded()
    }

    private fun toPlaylistRef(album: NewReleaseAlbum) = MusicPlaylistRef(
        id = album.browseId,
        title = album.title,
        author = album.artist,
        authorId = album.artistId,
        thumbnailUrl = album.thumbnailUrl,
        isAlbum = true,
        year = album.year?.toString(),
    )

    private data class DiscoveryData(
        val music: UnifiedSearchResults,
        val explore: List<MusicPlaylistRef>,
        val releases: List<NewReleaseAlbum>,
        val podcasts: UnifiedSearchResults,
        val podcastBrowse: List<MusicPlaylistRef>,
    )
}
