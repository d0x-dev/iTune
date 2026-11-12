package com.darkxvenom.itune.data.model

import androidx.compose.runtime.Immutable
import com.darkxvenom.itune.backend.MusicTrack

enum class HomeItemKind { SONG, VIDEO, ALBUM, ARTIST, PLAYLIST, PODCAST, EPISODE }

enum class HomeShelfGroup { PERSONAL, FEED, TRENDING, CHARTS, PODCASTS }

/** One tappable entry of a home shelf, parsed from YouTube Music (or built from local listening data). */
@Immutable
data class HomeFeedItem(
    val kind: HomeItemKind,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val videoId: String? = null,
    val browseId: String? = null,
    val watchPlaylistId: String? = null,
    val artistName: String = "",
    val artistId: String? = null,
    val albumName: String? = null,
    val albumId: String? = null,
    val isExplicit: Boolean = false,
) {
    val stableKey: String
        get() = "${kind.name}:${videoId ?: browseId ?: watchPlaylistId ?: title}"

    val isPlayableTrack: Boolean
        get() = !videoId.isNullOrBlank() &&
            (kind == HomeItemKind.SONG || kind == HomeItemKind.VIDEO || kind == HomeItemKind.EPISODE)

    fun toMusicTrack(): MusicTrack? {
        val id = videoId?.takeIf { it.isNotBlank() } ?: return null
        return MusicTrack(
            id = id,
            title = title,
            artists = artistName.ifBlank { subtitle },
            artistId = artistId,
            album = albumName,
            albumId = albumId,
            thumbnailUrl = thumbnailUrl,
            isVideo = kind == HomeItemKind.VIDEO,
            isExplicit = isExplicit,
        )
    }
}

@Immutable
data class HomeFeedShelf(
    val key: String,
    val title: String,
    val strapline: String = "",
    val items: List<HomeFeedItem>,
    val group: HomeShelfGroup = HomeShelfGroup.FEED,
    val moreBrowseId: String? = null,
) {
    val isSongShelf: Boolean get() = items.isNotEmpty() && items.all { it.kind == HomeItemKind.SONG }
    val isArtistShelf: Boolean get() = items.isNotEmpty() && items.all { it.kind == HomeItemKind.ARTIST }
    val isVideoShelf: Boolean
        get() = items.isNotEmpty() && items.all { it.kind == HomeItemKind.VIDEO || it.kind == HomeItemKind.EPISODE }
}

/** A top-of-home hero card backed by real data. Tapping it plays / opens [item]. */
@Immutable
data class HomeHeroCard(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val caption: String,
    val imageUrl: String?,
    val item: HomeFeedItem,
    val queue: List<HomeFeedItem> = emptyList(),
)
