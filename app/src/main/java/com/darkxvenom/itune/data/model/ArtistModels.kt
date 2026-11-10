package com.darkxvenom.itune.data.model

import com.darkxvenom.itune.backend.MusicTrack
import java.util.Locale

enum class QueueTier {
    USER_QUEUE,
    CONTEXT,
    AUTOPLAY,
}

data class ArtistRef(
    val name: String,
    val browseId: String? = null,
)

data class Song(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationText: String? = null,
    val artistId: String? = null,
    val artists: List<ArtistRef> = emptyList(),
    val albumId: String? = null,
    val albumName: String? = null,
    val isVideo: Boolean = false,
    val isVideoOrigin: Boolean = isVideo,
    val setVideoId: String? = null,
    val queueTier: QueueTier = QueueTier.CONTEXT,
    val queueEntryId: String? = null,
    val radioName: String? = null,
    val localUri: String? = null,
    val downloadFormat: String? = null,
    val localPath: String? = null,
    val localDateAddedSeconds: Long? = null,
    val localDateModifiedSeconds: Long? = null,
    val trackNumber: Int? = null,
    val sourceQuality: String? = null,
    val sourceHiRes: Boolean = false,
    val isExplicit: Boolean? = null,
    val playbackSource: String? = null,
    val playbackSourceType: PlaybackSourceType? = null,
    val playbackSourceId: String? = null,
    val feedbackToken: String? = null,
) {
    val fromAutoplay: Boolean get() = queueTier == QueueTier.AUTOPLAY
}

fun Song.artworkAt(px: Int): String? = thumbnailUrl.artworkAt(px)

fun Song.isSameTrackAs(other: Song?): Boolean {
    other ?: return false
    return title == other.title && artist == other.artist
}

fun playingRowMatcher(rows: List<Song>, current: Song?): (Song) -> Boolean {
    current ?: return { false }
    val sameId = rows.filter { it.videoId == current.videoId }
    if (sameId.isNotEmpty()) {
        val slot = current.setVideoId
        if (slot != null && sameId.any { it.setVideoId == slot }) {
            return { it.videoId == current.videoId && it.setVideoId == slot }
        }
        return { it.videoId == current.videoId }
    }
    val byName = rows.firstOrNull { it.isSameTrackAs(current) } ?: return { false }
    return { it == byName }
}

fun Song.durationMillis(): Long = durationText.durationMillis()

fun String?.durationMillis(): Long {
    val parts = this?.trim()?.takeIf { it.isNotEmpty() }?.split(":") ?: return 0L
    val numbers = parts.map { it.trim().toLongOrNull() ?: return 0L }
    val seconds = when (numbers.size) {
        2 -> numbers[0] * 60 + numbers[1]
        3 -> numbers[0] * 3_600 + numbers[1] * 60 + numbers[2]
        else -> return 0L
    }
    return (seconds * 1_000).coerceAtLeast(0L)
}

fun String?.artworkAt(px: Int): String? {
    if (this == null) return null
    val sized = this.replace(SIZE_HINT, "w$px-h$px")
    return if (isGoogleImageHost(this)) {
        sized.replace(PARAM_SIZE_HINT, "=s$px")
    } else {
        sized
    }
}

private fun isGoogleImageHost(url: String): Boolean {
    val host = url.substringAfter("://", "")
        .ifEmpty { url.substringAfter("//", "") }
        .substringBefore('/')
        .substringBefore(':')
        .substringBefore('?')
        .lowercase(Locale.ROOT)
    return host == "ggpht.com" || host.endsWith(".ggpht.com") ||
        host == "googleusercontent.com" || host.endsWith(".googleusercontent.com")
}

private val SIZE_HINT = Regex("""w\d+-h\d+""")
private val PARAM_SIZE_HINT = Regex("""=s\d+""")

const val ROW_ART_PX = 160
const val CARD_ART_PX = 480
const val HEADER_ART_PX = 720
const val NOTIFICATION_ART_PX = 544
const val PLAYER_ART_PX = 1200

enum class BrowseType { ALBUM, ARTIST, PLAYLIST, OTHER }

enum class PlaybackSourceType {
    HOME,
    SEARCH,
    HISTORY,
    REPLAY,
    EXPLORE,
    BROWSE,
    SHARED_LINK,
    QUEUE,
}

data class BrowseItem(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val type: BrowseType,
)

sealed interface SearchResult {
    data class TopTrack(val song: Song) : SearchResult
    data class Track(val song: Song) : SearchResult
    data class Browse(val item: BrowseItem) : SearchResult
}

enum class SearchFilter(val label: String, val params: String?) {
    ALL("All", null),
    SONGS("Songs", "EgWKAQIIAWoKEAkQChAFEAMQBA=="),
    VIDEOS("Videos", "EgWKAQIQAWoKEAkQChAFEAMQBA=="),
    ALBUMS("Albums", "EgWKAQIYAWoKEAkQChAFEAMQBA=="),
    ARTISTS("Artists", "EgWKAQIgAWoKEAkQChAFEAMQBA=="),
    PLAYLISTS("Playlists", "EgWKAQIoAWoKEAkQChAFEAMQBA=="),
}

data class WatchTarget(
    val playlistId: String,
    val videoId: String? = null,
    val params: String? = null,
)

data class ShelfItem(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val videoId: String?,
    val browseId: String?,
    val isVideo: Boolean = false,
)

enum class ShelfLayout {
    CARDS,
    LIST,
    VIDEO,
}

data class HomeShelf(
    val title: String,
    val items: List<ShelfItem>,
    val subtitle: String = "",
    val moreBrowseId: String? = null,
    val moreParams: String? = null,
    val layout: ShelfLayout = ShelfLayout.CARDS,
)

fun shelfLayoutFor(title: String, items: List<ShelfItem>, fromList: Boolean): ShelfLayout {
    if (items.isEmpty()) return ShelfLayout.CARDS
    val first = items.first()
    if (fromList && first.videoId != null) return ShelfLayout.LIST
    if (title.trim().equals("Listen again", ignoreCase = true)) return ShelfLayout.LIST
    if (items.count { it.isVideo } * 2 > items.size) return ShelfLayout.VIDEO
    return ShelfLayout.CARDS
}

data class DetailPage(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val songs: UiState<List<Song>>,
    val type: BrowseType = BrowseType.OTHER,
    val continuation: String? = null,
    val sections: List<HomeShelf> = emptyList(),
    val suggestedSongs: List<Song> = emptyList(),
    val library: LibraryState? = null,
    val description: String? = null,
    val subscriberCountText: String? = null,
    val monthlyListenerCount: String? = null,
    val subscription: SubscriptionState? = null,
    val radio: WatchTarget? = null,
    val shuffle: WatchTarget? = null,
)

data class LibraryState(
    val playlistId: String,
    val saved: Boolean,
)

data class SubscriptionState(
    val channelId: String,
    val subscribed: Boolean,
)

data class ArtistPage(
    val songs: List<Song>,
    val moreSongsBrowseId: String?,
    val sections: List<HomeShelf>,
    val thumbnailUrl: String? = null,
    val name: String? = null,
    val description: String? = null,
    val subscriberCountText: String? = null,
    val monthlyListenerCount: String? = null,
    val subscription: SubscriptionState? = null,
    val radio: WatchTarget? = null,
    val shuffle: WatchTarget? = null,
)

data class BrowseTarget(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val type: BrowseType,
    val songs: List<Song> = emptyList(),
    val fromCard: Boolean = songs.isEmpty(),
    val downloadId: String? = null,
)

enum class SongSort {
    DEFAULT,
    TITLE_ASC,
    TITLE_DESC,
    DATE_ADDED_ASC,
    DATE_ADDED_DESC,
}

enum class LikeStatus { LIKE, DISLIKE, INDIFFERENT }

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

fun Song.toMusicTrack(): MusicTrack = MusicTrack(
    id = videoId,
    title = title,
    artists = artist,
    artistId = artistId ?: artists.firstOrNull { !it.browseId.isNullOrBlank() }?.browseId,
    album = albumName,
    albumId = albumId,
    thumbnailUrl = artworkAt(PLAYER_ART_PX) ?: thumbnailUrl.orEmpty(),
    durationSec = durationMillis() / 1000L,
    liked = false,
    isVideo = isVideo,
)

fun MusicTrack.toArtistSong(): Song {
    val durText = if (durationSec > 0) {
        val m = durationSec / 60
        val s = durationSec % 60
        "$m:${s.toString().padStart(2, '0')}"
    } else null
    val refs = if (!artistId.isNullOrBlank()) {
        listOf(ArtistRef(artists.substringBefore(",").trim().ifBlank { artists }, artistId))
    } else {
        emptyList()
    }
    return Song(
        videoId = id,
        title = title,
        artist = artists,
        thumbnailUrl = thumbnailUrl?.ifBlank { null },
        durationText = durText,
        artistId = artistId,
        artists = refs,
        albumId = albumId,
        albumName = album,
        isVideo = isVideo,
    )
}

data class PendingArtistNav(
    val browseId: String? = null,
    val name: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val initialType: BrowseType = BrowseType.ARTIST,
)
