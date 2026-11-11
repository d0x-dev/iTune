package com.darkxvenom.itune.data.library

import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.artist.ArtistInnertubeClient
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.YouTubeAccountParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Fetches personalized `FEmusic_home` quick picks & curated playlists when signed in with Google.
 */
object PersonalizedHomeRepository {
    private val _quickPickTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val quickPickTracks: StateFlow<List<MusicTrack>> = _quickPickTracks.asStateFlow()

    private val _recommendedPlaylists = MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val recommendedPlaylists: StateFlow<List<MusicPlaylistRef>> = _recommendedPlaylists.asStateFlow()

    suspend fun refreshIfSignedIn() = withContext(Dispatchers.IO) {
        if (!GoogleAuthStore.isSignedIn) {
            clear()
            return@withContext
        }
        runCatching {
            val root = ArtistInnertubeClient.browse("FEmusic_home")
            val parsed = YouTubeAccountParsers.parseHomeFeed(root)
            if (parsed.quickPickTracks.isNotEmpty()) {
                _quickPickTracks.value = parsed.quickPickTracks
            }
            if (parsed.recommendedPlaylists.isNotEmpty()) {
                _recommendedPlaylists.value = parsed.recommendedPlaylists
            }
        }
    }

    fun clear() {
        _quickPickTracks.value = emptyList()
        _recommendedPlaylists.value = emptyList()
    }
}
