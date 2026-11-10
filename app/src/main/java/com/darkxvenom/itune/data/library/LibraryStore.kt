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

    fun saveLikedSong(context: Context, track: MusicTrack) {
        ensureInit(context)
        val entry = SavedSongMetadata.fromTrack(track)
        synchronized(this) {
            val current = _likedSongs.value.filterNot { it.id == track.id }
            val updated = listOf(entry) + current
            _likedSongs.value = updated
            persistSongs(KEY_LIKED_SONGS, updated)
        }
    }

    fun removeLikedSong(context: Context, trackId: String) {
        ensureInit(context)
        synchronized(this) {
            val updated = _likedSongs.value.filterNot { it.id == trackId }
            _likedSongs.value = updated
            persistSongs(KEY_LIKED_SONGS, updated)
        }
    }

    fun recordRecentSong(context: Context, track: MusicTrack) {
        ensureInit(context)
        val entry = SavedSongMetadata.fromTrack(track)
        synchronized(this) {
            val current = _recentSongs.value.filterNot { it.id == track.id }
            val updated = (listOf(entry) + current).take(MAX_RECENT_SONGS)
            _recentSongs.value = updated
            persistSongs(KEY_RECENT_SONGS, updated)
        }
    }

    fun saveArtist(
        context: Context,
        browseId: String,
        title: String,
        subtitle: String = "Artist",
        thumbnailUrl: String? = null,
    ) {
        ensureInit(context)
        if (browseId.isBlank()) return
        val entry = SavedCollectionEntry(
            browseId = browseId,
            title = title.ifBlank { "Artist" },
            subtitle = subtitle.ifBlank { "Artist" },
            thumbnailUrl = thumbnailUrl,
            type = BrowseType.ARTIST,
        )
        synchronized(this) {
            val current = _likedArtists.value.filterNot { it.browseId == browseId }
            val updated = listOf(entry) + current
            _likedArtists.value = updated
            persistCollections(KEY_ARTISTS, updated)
        }
        if (thumbnailUrl.isNullOrBlank()) hydrateArtist(context, browseId)
    }

    fun updateArtistMetadataIfSaved(
        context: Context,
        browseId: String,
        title: String,
        subtitle: String,
        thumbnailUrl: String?,
    ) {
        ensureInit(context)
        synchronized(this) {
            val idx = _likedArtists.value.indexOfFirst { it.browseId == browseId }
            if (idx < 0) return
            val existing = _likedArtists.value[idx]
            val updatedItem = existing.copy(
                title = title.ifBlank { existing.title },
                subtitle = subtitle.ifBlank { existing.subtitle },
                thumbnailUrl = thumbnailUrl ?: existing.thumbnailUrl,
            )
            if (updatedItem == existing) return
            val next = _likedArtists.value.toMutableList().apply { set(idx, updatedItem) }
            _likedArtists.value = next
            persistCollections(KEY_ARTISTS, next)
        }
    }

    fun removeArtist(context: Context, browseId: String) {
        ensureInit(context)
        synchronized(this) {
            val updated = _likedArtists.value.filterNot { it.browseId == browseId }
            _likedArtists.value = updated
            persistCollections(KEY_ARTISTS, updated)
        }
    }

    fun saveRelease(
        context: Context,
        browseId: String,
        title: String,
        subtitle: String,
        thumbnailUrl: String?,
        type: BrowseType,
    ) {
        ensureInit(context)
        if (browseId.isBlank()) return
        val resolvedType = resolveReleaseType(browseId, type)
        val entry = SavedCollectionEntry(
            browseId = browseId,
            title = title.ifBlank {
                if (resolvedType == BrowseType.PLAYLIST) "Playlist" else "Album"
            },
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            type = resolvedType,
        )
        synchronized(this) {
            if (resolvedType == BrowseType.PLAYLIST) {
                val updated = listOf(entry) + _savedPlaylists.value.filterNot { it.browseId == browseId }
                _savedPlaylists.value = updated
                persistCollections(KEY_PLAYLISTS, updated)
            } else {
                val updated = listOf(entry) + _savedAlbums.value.filterNot { it.browseId == browseId }
                _savedAlbums.value = updated
                persistCollections(KEY_ALBUMS, updated)
            }
        }
        if (thumbnailUrl.isNullOrBlank() || title.isBlank()) {
            hydrateRelease(context, browseId, resolvedType)
        }
    }

    fun updateReleaseMetadataIfSaved(
        context: Context,
        browseId: String,
        title: String,
        subtitle: String,
        thumbnailUrl: String?,
        type: BrowseType,
    ) {
        ensureInit(context)
        val resolvedType = resolveReleaseType(browseId, type)
        synchronized(this) {
            if (resolvedType == BrowseType.PLAYLIST) {
                val idx = _savedPlaylists.value.indexOfFirst { it.browseId == browseId }
                if (idx < 0) return
                val cur = _savedPlaylists.value[idx]
                val next = _savedPlaylists.value.toMutableList()
                next[idx] = cur.copy(
                    title = title.ifBlank { cur.title },
                    subtitle = subtitle.ifBlank { cur.subtitle },
                    thumbnailUrl = thumbnailUrl ?: cur.thumbnailUrl,
                )
                _savedPlaylists.value = next
                persistCollections(KEY_PLAYLISTS, next)
            } else {
                val idx = _savedAlbums.value.indexOfFirst { it.browseId == browseId }
                if (idx < 0) return
                val cur = _savedAlbums.value[idx]
                val next = _savedAlbums.value.toMutableList()
                next[idx] = cur.copy(
                    title = title.ifBlank { cur.title },
                    subtitle = subtitle.ifBlank { cur.subtitle },
                    thumbnailUrl = thumbnailUrl ?: cur.thumbnailUrl,
                )
                _savedAlbums.value = next
                persistCollections(KEY_ALBUMS, next)
            }
        }
    }

    fun removeRelease(context: Context, browseId: String) {
        ensureInit(context)
        synchronized(this) {
            val albumsUpdated = _savedAlbums.value.filterNot { it.browseId == browseId }
            if (albumsUpdated.size != _savedAlbums.value.size) {
                _savedAlbums.value = albumsUpdated
                persistCollections(KEY_ALBUMS, albumsUpdated)
            }
            val playlistsUpdated = _savedPlaylists.value.filterNot { it.browseId == browseId }
            if (playlistsUpdated.size != _savedPlaylists.value.size) {
                _savedPlaylists.value = playlistsUpdated
                persistCollections(KEY_PLAYLISTS, playlistsUpdated)
            }
        }
    }

    fun syncLegacyIds(
        context: Context,
        favoriteTrackIds: Collection<String>,
        subscribedArtistIds: Collection<String>,
        savedReleaseIds: Collection<String>,
        knownTracks: Map<String, MusicTrack> = emptyMap(),
    ) {
        ensureInit(context)
        val existingSongIds = _likedSongs.value.mapTo(HashSet()) { it.id }
        val recentById = _recentSongs.value.associateBy { it.id }
        favoriteTrackIds.forEach { id ->
            if (id in existingSongIds) return@forEach
            val localKnown = knownTracks[id] ?: recentById[id]?.toMusicTrack()
            if (localKnown != null) {
                saveLikedSong(context, localKnown)
            } else {
                hydrateSong(context, id)
            }
        }

        val existingArtistIds = _likedArtists.value.mapTo(HashSet()) { it.browseId }
        subscribedArtistIds.forEach { id ->
            if (id !in existingArtistIds) hydrateArtist(context, id)
        }

        val existingReleaseIds = HashSet<String>().apply {
            _savedAlbums.value.forEach { add(it.browseId) }
            _savedPlaylists.value.forEach { add(it.browseId) }
        }
        savedReleaseIds.forEach { id ->
            if (id !in existingReleaseIds) {
                hydrateRelease(context, id, resolveReleaseType(id, BrowseType.OTHER))
            }
        }
    }

    private fun hydrateSong(context: Context, videoId: String) {
        if (!hydrating.add("song:$videoId")) return
        scope.launch {
            try {
                val song = ArtistRepository.trackLinks(videoId).getOrNull() ?: return@launch
                saveLikedSong(context, song.toMusicTrack())
            } finally {
                hydrating.remove("song:$videoId")
            }
        }
    }

    private fun hydrateArtist(context: Context, browseId: String) {
        if (!hydrating.add("artist:$browseId")) return
        scope.launch {
            try {
                val page = ArtistRepository.artistPage(browseId).getOrNull() ?: return@launch
                val name = page.name ?: return@launch
                val appleHero = AppleArtistArtRepository.cached(browseId)?.heroUrl
                    ?: AppleArtistArtRepository.artFor(browseId, name) { page.songs.map { it.title } }?.heroUrl
                val subtitle = page.subscriberCountText ?: page.monthlyListenerCount ?: "Artist"
                saveArtist(
                    context = context,
                    browseId = browseId,
                    title = name,
                    subtitle = subtitle,
                    thumbnailUrl = appleHero ?: page.thumbnailUrl,
                )
            } finally {
                hydrating.remove("artist:$browseId")
            }
        }
    }

    private fun hydrateRelease(context: Context, browseId: String, hintType: BrowseType) {
        if (!hydrating.add("release:$browseId")) return
        scope.launch {
            try {
                val page = ArtistRepository.browseSongs(browseId).getOrNull() ?: return@launch
                val title = page.header?.title?.takeIf { it.isNotBlank() } ?: return@launch
                val subtitle = page.header?.subtitle.orEmpty()
                val thumb = page.header?.thumbnailUrl ?: page.songs.firstOrNull()?.thumbnailUrl
                saveRelease(
                    context = context,
                    browseId = browseId,
                    title = title,
                    subtitle = subtitle,
                    thumbnailUrl = thumb,
                    type = hintType,
                )
            } finally {
                hydrating.remove("release:$browseId")
            }
        }
    }

    private fun resolveReleaseType(browseId: String, hint: BrowseType): BrowseType = when {
        browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RDCLAK") -> BrowseType.PLAYLIST
        browseId.startsWith("MPRE") || browseId.startsWith("OLAK") -> BrowseType.ALBUM
        hint == BrowseType.PLAYLIST -> BrowseType.PLAYLIST
        else -> BrowseType.ALBUM
    }
