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
    val isGoogleAccount: Boolean = false,
    val ytPlaylistId: String? = null,
    val isEditable: Boolean = true,
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
    val isExplicit: Boolean = false,
    val setVideoId: String? = null,
    val savedLocally: Boolean = true,
    val isYtmSynced: Boolean = false,
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
        isExplicit = isExplicit,
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
            setVideoId = setVideoId,
            isExplicit = isExplicit,
        )
    }

    companion object {
        fun fromTrack(
            track: MusicTrack,
            addedAtMs: Long = System.currentTimeMillis(),
            setVideoId: String? = null,
            savedLocally: Boolean = true,
            isYtmSynced: Boolean = false,
        ): SavedSongMetadata =
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
                isExplicit = track.isExplicit,
                setVideoId = setVideoId,
                savedLocally = savedLocally,
                isYtmSynced = isYtmSynced,
            )

        fun fromSong(
            song: Song,
            addedAtMs: Long = System.currentTimeMillis(),
            savedLocally: Boolean = true,
            isYtmSynced: Boolean = false,
        ): SavedSongMetadata =
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
                    isExplicit = song.isExplicit == true,
                ),
                addedAtMs = addedAtMs,
                setVideoId = song.setVideoId,
                savedLocally = savedLocally,
                isYtmSynced = isYtmSynced,
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
    val isExplicit: Boolean = false,
    val isYtmSynced: Boolean = false,
    val savedLocally: Boolean = true,
    val playlistId: String? = null,
)

object LibraryStore {
    private const val PREFS_NAME = "itune_library_metadata"
    private const val KEY_LIKED_SONGS = "liked_songs_json"
    private const val KEY_RECENT_SONGS = "recent_songs_json"
    private const val KEY_YTM_HISTORY = "ytm_history_songs_json"
    private const val KEY_ARTISTS = "liked_artists_json"
    private const val KEY_ALBUMS = "saved_albums_json"
    private const val KEY_PLAYLISTS = "saved_playlists_json"
    private const val KEY_USER_PLAYLISTS = "user_playlists_json"
    private const val MAX_RECENT_SONGS = 120

    @Volatile
    private var prefs: SharedPreferences? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val hydrating = ConcurrentHashMap.newKeySet<String>()

    private val _likedSongs = MutableStateFlow<List<SavedSongMetadata>>(emptyList())
    val likedSongs: StateFlow<List<SavedSongMetadata>> = _likedSongs.asStateFlow()

    private val _recentSongs = MutableStateFlow<List<SavedSongMetadata>>(emptyList())
    val recentSongs: StateFlow<List<SavedSongMetadata>> = _recentSongs.asStateFlow()

    private val _ytmHistorySongs = MutableStateFlow<List<SavedSongMetadata>>(emptyList())
    val ytmHistorySongs: StateFlow<List<SavedSongMetadata>> = _ytmHistorySongs.asStateFlow()

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
            _ytmHistorySongs.value = decodeSongs(p.getString(KEY_YTM_HISTORY, null))
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
        isGoogleAccount: Boolean = false,
        ytPlaylistId: String? = null,
    ): UserPlaylist {
        ensureInit(context)
        val cleanName = name.trim().ifBlank { "New Playlist" }
        val now = System.currentTimeMillis()
        val deduped = initialSongs.distinctBy { it.id }
        val cleanYtId = ytPlaylistId?.removePrefix("VL")?.takeIf { it.isNotBlank() }
        val resolvedId = if (isGoogleAccount && cleanYtId != null) {
            "yt_$cleanYtId"
        } else {
            "up_${now}_${(1000..9999).random()}"
        }
        val playlist = UserPlaylist(
            id = resolvedId,
            name = cleanName,
            songs = deduped,
            createdAtMs = now,
            updatedAtMs = now,
            isGoogleAccount = isGoogleAccount,
            ytPlaylistId = cleanYtId,
            isEditable = true,
        )
        synchronized(this) {
            val updated = listOf(playlist) + _userPlaylists.value.filterNot { it.id == playlist.id }
            _userPlaylists.value = updated
            persistUserPlaylists(updated)
        }
        return playlist
    }

    fun setSongSetVideoIds(
        context: Context,
        playlistId: String,
        setVideoIds: Map<String, String>,
    ) {
        if (setVideoIds.isEmpty()) return
        ensureInit(context)
        synchronized(this) {
            val next = _userPlaylists.value.map { pl ->
                if (pl.id != playlistId && pl.ytPlaylistId != playlistId.removePrefix("VL")) {
                    pl
                } else {
                    pl.copy(
                        songs = pl.songs.map { s ->
                            val newSetId = setVideoIds[s.id]
                            if (newSetId != null) s.copy(setVideoId = newSetId) else s
                        },
                    )
                }
            }
            _userPlaylists.value = next
            persistUserPlaylists(next)
        }
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

    fun saveLikedSong(
        context: Context,
        track: MusicTrack,
        savedLocally: Boolean = true,
        isYtmSynced: Boolean = false,
    ) {
        ensureInit(context)
        val existing = _likedSongs.value.firstOrNull { it.id == track.id }
        val entry = SavedSongMetadata.fromTrack(
            track = track.copy(isExplicit = track.isExplicit || existing?.isExplicit == true),
            savedLocally = savedLocally || existing?.savedLocally == true,
            isYtmSynced = isYtmSynced || existing?.isYtmSynced == true,
        )
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
        savedLocally: Boolean = true,
        isYtmSynced: Boolean = false,
    ) {
        ensureInit(context)
        if (browseId.isBlank()) return
        val existing = _likedArtists.value.firstOrNull { it.browseId == browseId }
        val entry = SavedCollectionEntry(
            browseId = browseId,
            title = title.ifBlank { existing?.title ?: "Artist" },
            subtitle = subtitle.ifBlank { existing?.subtitle ?: "Artist" },
            thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
            type = BrowseType.ARTIST,
            savedLocally = savedLocally || existing?.savedLocally == true,
            isYtmSynced = isYtmSynced || existing?.isYtmSynced == true,
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
        isExplicit: Boolean = false,
        savedLocally: Boolean = true,
        isYtmSynced: Boolean = false,
        playlistId: String? = null,
    ) {
        ensureInit(context)
        if (browseId.isBlank()) return
        val resolvedType = resolveReleaseType(browseId, type)
        val existing = (_savedAlbums.value + _savedPlaylists.value).firstOrNull { it.browseId == browseId }
        val entry = SavedCollectionEntry(
            browseId = browseId,
            title = title.ifBlank {
                existing?.title ?: if (resolvedType == BrowseType.PLAYLIST) "Playlist" else "Album"
            },
            subtitle = subtitle.ifBlank { existing?.subtitle.orEmpty() },
            thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
            type = resolvedType,
            isExplicit = isExplicit || existing?.isExplicit == true,
            savedLocally = savedLocally || existing?.savedLocally == true,
            isYtmSynced = isYtmSynced || existing?.isYtmSynced == true,
            playlistId = playlistId ?: existing?.playlistId,
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

    private fun songToJson(s: SavedSongMetadata): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("title", s.title)
        put("artists", s.artists)
        put("artistId", s.artistId ?: JSONObject.NULL)
        put("album", s.album ?: JSONObject.NULL)
        put("albumId", s.albumId ?: JSONObject.NULL)
        put("thumbnailUrl", s.thumbnailUrl)
        put("durationSec", s.durationSec)
        put("isVideo", s.isVideo)
        put("addedAtMs", s.addedAtMs)
        put("isExplicit", s.isExplicit)
        put("setVideoId", s.setVideoId ?: JSONObject.NULL)
        put("savedLocally", s.savedLocally)
        put("isYtmSynced", s.isYtmSynced)
    }

    private fun jsonToSong(o: JSONObject): SavedSongMetadata? {
        val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
        return SavedSongMetadata(
            id = id,
            title = o.optString("title"),
            artists = o.optString("artists"),
            artistId = o.optNullableString("artistId"),
            album = o.optNullableString("album"),
            albumId = o.optNullableString("albumId"),
            thumbnailUrl = o.optString("thumbnailUrl"),
            durationSec = o.optLong("durationSec", 0L),
            isVideo = o.optBoolean("isVideo", false),
            addedAtMs = o.optLong("addedAtMs", System.currentTimeMillis()),
            isExplicit = o.optBoolean("isExplicit", false),
            setVideoId = o.optNullableString("setVideoId"),
            savedLocally = o.optBoolean("savedLocally", true),
            isYtmSynced = o.optBoolean("isYtmSynced", false),
        )
    }

    private fun persistSongs(key: String, items: List<SavedSongMetadata>) {
        val arr = JSONArray()
        items.forEach { arr.put(songToJson(it)) }
        prefs?.edit()?.putString(key, arr.toString())?.apply()
    }

    private fun decodeSongs(raw: String?): List<SavedSongMetadata> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    jsonToSong(o)?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun mergeRemoteLikedSongs(context: Context, remoteSongs: List<SavedSongMetadata>): Set<String> {
        ensureInit(context)
        val merged = synchronized(this) {
            val localOnly = _likedSongs.value.filter { it.savedLocally }
            val byId = linkedMapOf<String, SavedSongMetadata>()
            remoteSongs.forEach { remote ->
                val localMatch = localOnly.firstOrNull { it.id == remote.id }
                byId[remote.id] = if (localMatch != null) {
                    remote.copy(
                        savedLocally = true,
                        isYtmSynced = true,
                        addedAtMs = localMatch.addedAtMs,
                        isExplicit = remote.isExplicit || localMatch.isExplicit,
                    )
                } else {
                    remote.copy(savedLocally = false, isYtmSynced = true)
                }
            }
            localOnly.forEach { local ->
                if (local.id !in byId) {
                    byId[local.id] = local
                }
            }
            val result = byId.values.toList()
            _likedSongs.value = result
            persistSongs(KEY_LIKED_SONGS, result)
            result.mapTo(LinkedHashSet()) { it.id }
        }
        return merged
    }

    fun replaceYtmHistory(context: Context, historySongs: List<SavedSongMetadata>) {
        ensureInit(context)
        val clean = historySongs.distinctBy { it.id }.take(MAX_RECENT_SONGS)
        synchronized(this) {
            _ytmHistorySongs.value = clean
            persistSongs(KEY_YTM_HISTORY, clean)
        }
    }

    fun prependYtmHistoryTrack(context: Context, track: MusicTrack) {
        ensureInit(context)
        val entry = SavedSongMetadata.fromTrack(track, savedLocally = false, isYtmSynced = true)
        synchronized(this) {
            val updated = (listOf(entry) + _ytmHistorySongs.value.filterNot { it.id == track.id })
                .take(MAX_RECENT_SONGS)
            _ytmHistorySongs.value = updated
            persistSongs(KEY_YTM_HISTORY, updated)
        }
    }

    fun mergeRemoteArtists(context: Context, remoteArtists: List<SavedCollectionEntry>): Set<String> {
        ensureInit(context)
        return synchronized(this) {
            val existingLocal = _likedArtists.value.filter { it.savedLocally }
            val byId = linkedMapOf<String, SavedCollectionEntry>()
            remoteArtists.forEach { r ->
                val loc = existingLocal.firstOrNull { it.browseId == r.browseId }
                byId[r.browseId] = if (loc != null) {
                    r.copy(savedLocally = true, isYtmSynced = true, thumbnailUrl = r.thumbnailUrl ?: loc.thumbnailUrl)
                } else {
                    r.copy(savedLocally = false, isYtmSynced = true)
                }
            }
            existingLocal.forEach { loc -> if (loc.browseId !in byId) byId[loc.browseId] = loc }
            val list = byId.values.toList()
            _likedArtists.value = list
            persistCollections(KEY_ARTISTS, list)
            list.mapTo(LinkedHashSet()) { it.browseId }
        }
    }

    fun mergeRemoteAlbums(context: Context, remoteAlbums: List<SavedCollectionEntry>): Set<String> {
        ensureInit(context)
        return synchronized(this) {
            val existingLocal = _savedAlbums.value.filter { it.savedLocally }
            val byId = linkedMapOf<String, SavedCollectionEntry>()
            remoteAlbums.forEach { r ->
                val loc = existingLocal.firstOrNull { it.browseId == r.browseId }
                byId[r.browseId] = if (loc != null) {
                    r.copy(
                        savedLocally = true,
                        isYtmSynced = true,
                        isExplicit = r.isExplicit || loc.isExplicit,
                        playlistId = r.playlistId ?: loc.playlistId,
                    )
                } else {
                    r.copy(savedLocally = false, isYtmSynced = true)
                }
            }
            existingLocal.forEach { loc -> if (loc.browseId !in byId) byId[loc.browseId] = loc }
            val list = byId.values.toList()
            _savedAlbums.value = list
            persistCollections(KEY_ALBUMS, list)
            list.mapTo(LinkedHashSet()) { it.browseId }
        }
    }

    fun mergeRemoteSavedPlaylists(context: Context, remoteSaved: List<SavedCollectionEntry>): Set<String> {
        ensureInit(context)
        return synchronized(this) {
            val existingLocal = _savedPlaylists.value.filter { it.savedLocally }
            val byId = linkedMapOf<String, SavedCollectionEntry>()
            remoteSaved.forEach { r ->
                val loc = existingLocal.firstOrNull { it.browseId == r.browseId }
                byId[r.browseId] = if (loc != null) {
                    r.copy(
                        savedLocally = true,
                        isYtmSynced = true,
                        isExplicit = r.isExplicit || loc.isExplicit,
                        playlistId = r.playlistId ?: loc.playlistId,
                    )
                } else {
                    r.copy(savedLocally = false, isYtmSynced = true)
                }
            }
            existingLocal.forEach { loc -> if (loc.browseId !in byId) byId[loc.browseId] = loc }
            val list = byId.values.toList()
            _savedPlaylists.value = list
            persistCollections(KEY_PLAYLISTS, list)
            list.mapTo(LinkedHashSet()) { it.browseId }
        }
    }

    fun mergeRemoteUserPlaylists(context: Context, remoteGooglePlaylists: List<UserPlaylist>) {
        ensureInit(context)
        synchronized(this) {
            val localOnly = _userPlaylists.value.filterNot { it.isGoogleAccount }
            val cleanRemote = remoteGooglePlaylists.map { it.copy(isGoogleAccount = true) }
            val combined = cleanRemote + localOnly
            _userPlaylists.value = combined
            persistUserPlaylists(combined)
        }
    }

    fun clearGoogleAccountDataOnSignOut(context: Context): Triple<Set<String>, Set<String>, Set<String>> {
        ensureInit(context)
        return synchronized(this) {
            val keptLikes = _likedSongs.value
                .filter { it.savedLocally }
                .map { it.copy(isYtmSynced = false) }
            _likedSongs.value = keptLikes
            persistSongs(KEY_LIKED_SONGS, keptLikes)

            _ytmHistorySongs.value = emptyList()
            persistSongs(KEY_YTM_HISTORY, emptyList())

            val keptArtists = _likedArtists.value
                .filter { it.savedLocally }
                .map { it.copy(isYtmSynced = false) }
            _likedArtists.value = keptArtists
            persistCollections(KEY_ARTISTS, keptArtists)

            val keptAlbums = _savedAlbums.value
                .filter { it.savedLocally }
                .map { it.copy(isYtmSynced = false) }
            _savedAlbums.value = keptAlbums
            persistCollections(KEY_ALBUMS, keptAlbums)

            val keptSavedPlaylists = _savedPlaylists.value
                .filter { it.savedLocally }
                .map { it.copy(isYtmSynced = false) }
            _savedPlaylists.value = keptSavedPlaylists
            persistCollections(KEY_PLAYLISTS, keptSavedPlaylists)

            val keptUserPlaylists = _userPlaylists.value.filterNot { it.isGoogleAccount }
            _userPlaylists.value = keptUserPlaylists
            persistUserPlaylists(keptUserPlaylists)

            val likeIds = keptLikes.mapTo(LinkedHashSet()) { it.id }
            val artistIds = keptArtists.mapTo(LinkedHashSet()) { it.browseId }
            val releaseIds = LinkedHashSet<String>().apply {
                keptAlbums.forEach { add(it.browseId) }
                keptSavedPlaylists.forEach { add(it.browseId) }
            }
            Triple(likeIds, artistIds, releaseIds)
        }
    }

    private fun persistUserPlaylists(items: List<UserPlaylist>) {
        val arr = JSONArray()
        items.forEach { pl ->
            val songsArr = JSONArray()
            pl.songs.forEach { songsArr.put(songToJson(it)) }
            arr.put(
                JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("songs", songsArr)
                    put("createdAtMs", pl.createdAtMs)
                    put("updatedAtMs", pl.updatedAtMs)
                    put("isGoogleAccount", pl.isGoogleAccount)
                    put("ytPlaylistId", pl.ytPlaylistId ?: JSONObject.NULL)
                    put("isEditable", pl.isEditable)
                },
            )
        }
        prefs?.edit()?.putString(KEY_USER_PLAYLISTS, arr.toString())?.apply()
    }

    private fun decodeUserPlaylists(raw: String?): List<UserPlaylist> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = o.optString("name").ifBlank { "Playlist" }
                    val songsArr = o.optJSONArray("songs")
                    val decodedSongs = buildList {
                        if (songsArr != null) {
                            for (j in 0 until songsArr.length()) {
                                val sObj = songsArr.optJSONObject(j) ?: continue
                                jsonToSong(sObj)?.let(::add)
                            }
                        }
                    }
                    add(
                        UserPlaylist(
                            id = id,
                            name = name,
                            songs = decodedSongs,
                            createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
                            updatedAtMs = o.optLong("updatedAtMs", System.currentTimeMillis()),
                            isGoogleAccount = o.optBoolean("isGoogleAccount", false),
                            ytPlaylistId = o.optNullableString("ytPlaylistId"),
                            isEditable = o.optBoolean("isEditable", true),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistCollections(key: String, items: List<SavedCollectionEntry>) {
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("browseId", c.browseId)
                    put("title", c.title)
                    put("subtitle", c.subtitle)
                    put("thumbnailUrl", c.thumbnailUrl ?: JSONObject.NULL)
                    put("type", c.type.name)
                    put("addedAtMs", c.addedAtMs)
                    put("isExplicit", c.isExplicit)
                    put("isYtmSynced", c.isYtmSynced)
                    put("savedLocally", c.savedLocally)
                    put("playlistId", c.playlistId ?: JSONObject.NULL)
                },
            )
        }
        prefs?.edit()?.putString(key, arr.toString())?.apply()
    }

    private fun decodeCollections(raw: String?, fallbackType: BrowseType): List<SavedCollectionEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("browseId").takeIf { it.isNotBlank() } ?: continue
                    val typeName = o.optString("type")
                    val parsedType = runCatching { BrowseType.valueOf(typeName) }.getOrDefault(fallbackType)
                    add(
                        SavedCollectionEntry(
                            browseId = id,
                            title = o.optString("title"),
                            subtitle = o.optString("subtitle"),
                            thumbnailUrl = o.optNullableString("thumbnailUrl"),
                            type = parsedType,
                            addedAtMs = o.optLong("addedAtMs", System.currentTimeMillis()),
                            isExplicit = o.optBoolean("isExplicit", false),
                            isYtmSynced = o.optBoolean("isYtmSynced", false),
                            savedLocally = o.optBoolean("savedLocally", true),
                            playlistId = o.optNullableString("playlistId"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
