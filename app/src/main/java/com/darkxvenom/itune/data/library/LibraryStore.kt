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

