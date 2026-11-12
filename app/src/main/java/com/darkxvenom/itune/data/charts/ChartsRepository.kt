package com.darkxvenom.itune.data.charts

import android.util.Log
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

object ChartsRepository {
    private const val TAG = "ChartsRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val selectedPlatform = MutableStateFlow(ChartPlatform.APPLE)
    val selectedRegion = MutableStateFlow("system")

    private val _appleTracks = MutableStateFlow<List<ChartTrack>>(emptyList())
    val appleTracks = _appleTracks.asStateFlow()

    private val _appleAlbums = MutableStateFlow<List<ChartAlbum>>(emptyList())
    val appleAlbums = _appleAlbums.asStateFlow()

    private val _appleArtists = MutableStateFlow<List<ChartArtist>>(emptyList())
    val appleArtists = _appleArtists.asStateFlow()

    private val _ituneTracks = MutableStateFlow<List<ChartTrack>>(emptyList())
    val ituneTracks = _ituneTracks.asStateFlow()

    private val _ituneArtists = MutableStateFlow<List<ChartArtist>>(emptyList())
    val ituneArtists = _ituneArtists.asStateFlow()

    private val _youtubeTracks = MutableStateFlow<List<ChartTrack>>(emptyList())
    val youtubeTracks = _youtubeTracks.asStateFlow()

    private val _jioSaavnTracks = MutableStateFlow<List<ChartTrack>>(emptyList())
    val jioSaavnTracks = _jioSaavnTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var currentLoadedRegion: String? = null

    fun ensureLoaded(regionCode: String = "system") {
        if (currentLoadedRegion == null || _appleTracks.value.isEmpty()) {
            refresh(regionCode, force = false)
        }
    }

    fun refresh(countryCode: String = "system", force: Boolean = false) {
        val resolvedCode = if (countryCode == "system") {
            val userCountry = SettingsStore.state.value.contentCountry
            if (userCountry.isNotBlank() && userCountry != "system") {
                userCountry.lowercase()
            } else {
                Locale.getDefault().country.lowercase().ifEmpty { "us" }
            }
        } else {
            countryCode.lowercase()
        }

        if (_isLoading.value && !force && currentLoadedRegion == resolvedCode) return

        scope.launch {
            _isLoading.value = true

            if (currentLoadedRegion != resolvedCode || force) {
                _appleTracks.value = emptyList()
                _appleAlbums.value = emptyList()
                _appleArtists.value = emptyList()
                _ituneTracks.value = emptyList()
                _ituneArtists.value = emptyList()
                _youtubeTracks.value = emptyList()
                _jioSaavnTracks.value = emptyList()
            }

            try {
                // Fetch in parallel
                val appleSongsJob = launch {
                    try {
                        val tracks = AppleMusicChartsScraper.fetchTopSongs(resolvedCode)
                        if (tracks.isNotEmpty()) {
                            _appleTracks.value = tracks
                            val artists = AppleMusicChartsScraper.getTrendingArtists(tracks)
                            _appleArtists.value = artists
                            // Populate iTune tracks & artists with top hits
                            _ituneTracks.value = tracks.take(50)
                            _ituneArtists.value = artists
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch Apple Music songs: ${e.message}")
                    }
                }

                val appleAlbumsJob = launch {
                    try {
                        val albums = AppleMusicChartsScraper.fetchTopAlbums(resolvedCode)
                        if (albums.isNotEmpty()) {
                            _appleAlbums.value = albums
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch Apple Music albums: ${e.message}")
                    }
                }

                val jioJob = launch {
                    try {
                        val jioSongs = JioSaavnChartsScraper.fetchTrendingSongs()
                        if (jioSongs.isNotEmpty()) {
                            _jioSaavnTracks.value = jioSongs
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch JioSaavn songs: ${e.message}")
                    }
                }

                val ytJob = launch {
                    try {
                        val ytTracks = MusicBackend.fetchYouTubeCharts()
                        if (ytTracks.isNotEmpty()) {
                            _youtubeTracks.value = ytTracks
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch YouTube charts: ${e.message}")
                    }
                }

                appleSongsJob.join()
                appleAlbumsJob.join()
                jioJob.join()
                ytJob.join()

                currentLoadedRegion = resolvedCode
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing charts: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playTrack(track: ChartTrack, queue: List<ChartTrack> = emptyList()) {
        scope.launch {
            try {
                // 1. Direct YouTube video ID
                if (!track.id.isNullOrBlank() && !track.id.startsWith("JS:")) {
                    val musicTrack = MusicTrack(
                        id = track.id,
                        title = track.title,
                        artists = track.artist,
                        thumbnailUrl = track.thumbnailUrl,
                        durationSec = track.durationSec,
                        isExplicit = track.isExplicit
                    )
                    val musicQueue = queue.mapNotNull { q ->
                        if (!q.id.isNullOrBlank() && !q.id.startsWith("JS:")) {
                            MusicTrack(
                                id = q.id,
                                title = q.title,
                                artists = q.artist,
                                thumbnailUrl = q.thumbnailUrl,
                                durationSec = q.durationSec,
                                isExplicit = q.isExplicit
                            )
                        } else null
                    }.takeIf { it.isNotEmpty() }

                    withContext(Dispatchers.Main) {
                        MusicPlayerState.play(musicTrack, newQueue = musicQueue)
                    }
                    return@launch
                }

                // 2. JioSaavn with direct 320kbps MP4 stream
                if (track.id?.startsWith("JS:") == true) {
                    val directUrl = track.streamUrl ?: JioSaavnChartsScraper.getStreamUrl(track.id)
                    if (!directUrl.isNullOrBlank()) {
                        val musicTrack = MusicTrack(
                            id = track.id,
                            title = track.title,
                            artists = track.artist,
                            thumbnailUrl = track.thumbnailUrl,
                            durationSec = track.durationSec,
                            isExplicit = track.isExplicit
                        )
                        withContext(Dispatchers.Main) {
                            MusicPlayerState.play(musicTrack, preResolvedUrl = directUrl)
                        }
                        return@launch
                    }
                }

                // 3. Search matching track via YouTube InnerTube
                val query = "${track.title} ${track.artist}".trim()
                val searchResults = MusicBackend.searchMusic(query)
                val best = searchResults.firstOrNull { r ->
                    r.title.equals(track.title, ignoreCase = true)
                } ?: searchResults.firstOrNull { r ->
                    r.title.contains(track.title, ignoreCase = true)
                } ?: searchResults.firstOrNull()

                if (best != null) {
                    val resolved = if (!track.thumbnailUrl.isNullOrBlank()) {
                        best.copy(thumbnailUrl = track.thumbnailUrl)
                    } else best

                    withContext(Dispatchers.Main) {
                        MusicPlayerState.play(resolved)
                    }
                } else {
                    Log.w(TAG, "No playback match found for: $query")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing chart track ${track.title}: ${e.message}")
            }
        }
    }
}
