package com.darkxvenom.itune.data.library

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.artist.AppleArtistArtRepository
import com.darkxvenom.itune.data.artist.ArtistRepository
import com.darkxvenom.itune.data.model.ArtistRef
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.PLAYER_ART_PX
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.data.model.toMusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

enum class LibraryDestination {
    PLAYLISTS,
    ARTISTS,
    ALBUMS,
    SONGS,
    LIKED_SONGS,
    USER_PLAYLIST,
    NEW_RELEASES,
}

@Immutable
data class UserPlaylist(
    val id: String,
    val name: String,
    val songs: List<SavedSongMetadata> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val artworkUrls: List<String>
        get() = songs.mapNotNull { it.thumbnailUrl.ifBlank { null } }.distinct().take(4)

    val leadArtwork: String?
        get() = artworkUrls.firstOrNull()
}

@Immutable
data class SavedSongMetadata(
    val id: String,
    val title: String,
    val artists: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val thumbnailUrl: String = "",
    val durationSec: Long = 0L,
    val isVideo: Boolean = false,
    val addedAtMs: Long = System.currentTimeMillis(),
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        id = id,
        title = title,
        artists = artists,
        artistId = artistId,
        album = album,
        albumId = albumId,
        thumbnailUrl = thumbnailUrl.ifBlank { null },
        durationSec = durationSec,
        liked = true,
        isVideo = isVideo,
    )

    fun toSong(): Song {
        val durText = if (durationSec > 0L) {
            val m = durationSec / 60L
            val s = durationSec % 60L
            "$m:${s.toString().padStart(2, '0')}"
        } else {
            null
        }
        val refs = if (!artistId.isNullOrBlank()) {
            listOf(ArtistRef(artists.substringBefore(",").trim().ifBlank { artists }, artistId))
        } else {
            emptyList()
        }
        return Song(
            videoId = id,
            title = title,
            artist = artists,
            thumbnailUrl = thumbnailUrl.ifBlank { null },
            durationText = durText,
            artistId = artistId,
            artists = refs,
            albumId = albumId,
            albumName = album,
            isVideo = isVideo,
        )
    }

    companion object {
        fun fromTrack(track: MusicTrack, addedAtMs: Long = System.currentTimeMillis()): SavedSongMetadata =
            SavedSongMetadata(
                id = track.id,
                title = track.title,
                artists = track.artists,
                artistId = track.artistId,
                album = track.album,
                albumId = track.albumId,
                thumbnailUrl = track.thumbnailUrl.orEmpty(),
                durationSec = track.durationSec,
                isVideo = track.isVideo,
                addedAtMs = addedAtMs,
            )

        fun fromSong(song: Song, addedAtMs: Long = System.currentTimeMillis()): SavedSongMetadata =
            fromTrack(
                MusicTrack(
                    id = song.videoId,
                    title = song.title,
                    artists = song.artist,
                    artistId = song.artistId ?: song.artists.firstOrNull { !it.browseId.isNullOrBlank() }?.browseId,
                    album = song.albumName,
                    albumId = song.albumId,
                    thumbnailUrl = song.artworkAt(PLAYER_ART_PX) ?: song.thumbnailUrl,
                    durationSec = 0L,
                    liked = true,
                    isVideo = song.isVideo,
                ),
                addedAtMs = addedAtMs,
            )
    }
}

@Immutable
data class SavedCollectionEntry(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val type: BrowseType,
    val addedAtMs: Long = System.currentTimeMillis(),
)

object LibraryStore {
    private const val PREFS_NAME = "itune_library_metadata"
    private const val KEY_LIKED_SONGS = "liked_songs_json"
    private const val KEY_RECENT_SONGS = "recent_songs_json"
    private const val KEY_ARTISTS = "liked_artists_json"
    private const val KEY_ALBUMS = "saved_albums_json"
    private const val KEY_PLAYLISTS = "saved_playlists_json"
    private const val KEY_USER_PLAYLISTS = "user_playlists_json"
    private const val MAX_RECENT_SONGS = 60

    @Volatile
    private var prefs: SharedPreferences? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val hydrating = ConcurrentHashMap.newKeySet<String>()

    private val _likedSongs = MutableStateFlow<List<SavedSongMetadata>>(emptyList())
    val likedSongs: StateFlow<List<SavedSongMetadata>> = _likedSongs.asStateFlow()

    private val _recentSongs = MutableStateFlow<List<SavedSongMetadata>>(emptyList())
    val recentSongs: StateFlow<List<SavedSongMetadata>> = _recentSongs.asStateFlow()

    private val _likedArtists = MutableStateFlow<List<SavedCollectionEntry>>(emptyList())
    val likedArtists: StateFlow<List<SavedCollectionEntry>> = _likedArtists.asStateFlow()

    private val _savedAlbums = MutableStateFlow<List<SavedCollectionEntry>>(emptyList())
    val savedAlbums: StateFlow<List<SavedCollectionEntry>> = _savedAlbums.asStateFlow()

    private val _savedPlaylists = MutableStateFlow<List<SavedCollectionEntry>>(emptyList())
    val savedPlaylists: StateFlow<List<SavedCollectionEntry>> = _savedPlaylists.asStateFlow()

    private val _userPlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val userPlaylists: StateFlow<List<UserPlaylist>> = _userPlaylists.asStateFlow()

    fun ensureInit(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = p
            _likedSongs.value = decodeSongs(p.getString(KEY_LIKED_SONGS, null))
            _recentSongs.value = decodeSongs(p.getString(KEY_RECENT_SONGS, null))
            _likedArtists.value = decodeCollections(p.getString(KEY_ARTISTS, null), BrowseType.ARTIST)
            _savedAlbums.value = decodeCollections(p.getString(KEY_ALBUMS, null), BrowseType.ALBUM)
            _savedPlaylists.value = decodeCollections(p.getString(KEY_PLAYLISTS, null), BrowseType.PLAYLIST)
            _userPlaylists.value = decodeUserPlaylists(p.getString(KEY_USER_PLAYLISTS, null))
        }
    }

    fun createUserPlaylist(
        context: Context,
        name: String,
        initialSongs: List<SavedSongMetadata> = emptyList(),
    ): UserPlaylist {
        ensureInit(context)
        val cleanName = name.trim().ifBlank { "New Playlist" }
        val now = System.currentTimeMillis()
        val deduped = initialSongs.distinctBy { it.id }
        val playlist = UserPlaylist(
            id = "up_${now}_${(1000..9999).random()}",
            name = cleanName,
            songs = deduped,
            createdAtMs = now,
            updatedAtMs = now,
        )
        synchronized(this) {
            val updated = listOf(playlist) + _userPlaylists.value
            _userPlaylists.value = updated
            persistUserPlaylists(updated)
        }
        return playlist
    }

    fun addSongsToUserPlaylist(
        context: Context,
        playlistId: String,
        songs: List<SavedSongMetadata>,
    ): Int {
        ensureInit(context)
        if (songs.isEmpty()) return 0
        var addedCount = 0
        synchronized(this) {
            val current = _userPlaylists.value
            val idx = current.indexOfFirst { it.id == playlistId }
            if (idx < 0) return 0
            val existing = current[idx]
            val existingIds = existing.songs.map { it.id }.toSet()
            val fresh = songs.filter { it.id !in existingIds }.distinctBy { it.id }
            addedCount = fresh.size
            val merged = existing.songs + fresh
            val updatedItem = existing.copy(
                songs = merged,
                updatedAtMs = System.currentTimeMillis(),
            )
            val next = current.toMutableList().apply {
                removeAt(idx)
                add(0, updatedItem)
            }
            _userPlaylists.value = next
            persistUserPlaylists(next)
        }
        return addedCount
    }

    fun removeSongFromUserPlaylist(context: Context, playlistId: String, songId: String) {
        ensureInit(context)
        synchronized(this) {
            val current = _userPlaylists.value
            val idx = current.indexOfFirst { it.id == playlistId }
            if (idx < 0) return
            val existing = current[idx]
            val nextSongs = existing.songs.filterNot { it.id == songId }
            val next = current.toMutableList().apply {
                set(idx, existing.copy(songs = nextSongs, updatedAtMs = System.currentTimeMillis()))
            }
            _userPlaylists.value = next
            persistUserPlaylists(next)
        }
    }

    fun renameUserPlaylist(context: Context, playlistId: String, newName: String) {
        ensureInit(context)
        val clean = newName.trim()
        if (clean.isBlank()) return
        synchronized(this) {
            val next = _userPlaylists.value.map { pl ->
                if (pl.id == playlistId) pl.copy(name = clean, updatedAtMs = System.currentTimeMillis()) else pl
            }
            _userPlaylists.value = next
            persistUserPlaylists(next)
        }
    }

    fun deleteUserPlaylist(context: Context, playlistId: String) {
        ensureInit(context)
        synchronized(this) {
            val next = _userPlaylists.value.filterNot { it.id == playlistId }
            _userPlaylists.value = next
            persistUserPlaylists(next)
        }
    }

