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
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches and caches personalized `FEmusic_home` quick picks & curated playlists
 * per signed-in Google account / channel so switching accounts swaps feeds cleanly.
 */
object PersonalizedHomeRepository {
    private data class CachedHomeFeed(
        val quickPickTracks: List<MusicTrack>,
        val recommendedPlaylists: List<MusicPlaylistRef>,
    )

    private val feedBySessionKey = ConcurrentHashMap<String, CachedHomeFeed>()

    @Volatile
    private var activeSessionKey: String? = null

    private val _quickPickTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val quickPickTracks: StateFlow<List<MusicTrack>> = _quickPickTracks.asStateFlow()

    private val _recommendedPlaylists = MutableStateFlow<List<MusicPlaylistRef>>(emptyList())
    val recommendedPlaylists: StateFlow<List<MusicPlaylistRef>> = _recommendedPlaylists.asStateFlow()

    fun switchSession(sessionKey: String?) {
        activeSessionKey = sessionKey
        if (sessionKey.isNullOrBlank()) {
            _quickPickTracks.value = emptyList()
            _recommendedPlaylists.value = emptyList()
            return
        }
        val cached = feedBySessionKey[sessionKey]
        _quickPickTracks.value = cached?.quickPickTracks.orEmpty()
        _recommendedPlaylists.value = cached?.recommendedPlaylists.orEmpty()
    }

    suspend fun refreshIfSignedIn() = withContext(Dispatchers.IO) {
        val requestKey = GoogleAuthStore.activeSessionKey()
        if (!GoogleAuthStore.isSignedIn || requestKey.isNullOrBlank()) {
            clear()
            return@withContext
        }
        activeSessionKey = requestKey
        runCatching {
            val root = ArtistInnertubeClient.browse("FEmusic_home")
            val parsed = YouTubeAccountParsers.parseHomeFeed(root)
            if (GoogleAuthStore.activeSessionKey() != requestKey) return@withContext
            val existing = feedBySessionKey[requestKey]
            val mergedTracks = parsed.quickPickTracks.ifEmpty { existing?.quickPickTracks.orEmpty() }
            val mergedPlaylists = parsed.recommendedPlaylists.ifEmpty { existing?.recommendedPlaylists.orEmpty() }
            feedBySessionKey[requestKey] = CachedHomeFeed(mergedTracks, mergedPlaylists)
            if (activeSessionKey == requestKey) {
                _quickPickTracks.value = mergedTracks
                _recommendedPlaylists.value = mergedPlaylists
            }
        }
    }

    fun clearAccountCache(accountId: String) {
        feedBySessionKey.keys.filter { it.startsWith("$accountId:") }.forEach {
            feedBySessionKey.remove(it)
        }
    }

    fun clear() {
        activeSessionKey = null
        _quickPickTracks.value = emptyList()
        _recommendedPlaylists.value = emptyList()
    }
}
