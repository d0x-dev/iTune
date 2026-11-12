package com.darkxvenom.itune.data.timetravel

import androidx.compose.runtime.Immutable
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.toMusicTrack

enum class ListeningPeriod(val label: String, val shortLabel: String, val days: Int?) {
    ALL_TIME("All Time", "All Time", null),
    ONE_WEEK("1 Week", "7 Days", 7),
    ONE_MONTH("1 Month", "30 Days", 30),
    THREE_MONTHS("3 Months", "90 Days", 90),
    SIX_MONTHS("6 Months", "180 Days", 180),
    ONE_YEAR("1 Year", "365 Days", 365);

    fun getStartTimeMs(now: Long = System.currentTimeMillis()): Long {
        val d = days ?: return 0L
        return now - (d.toLong() * 24L * 60L * 60L * 1000L)
    }
}

@Immutable
data class PlaybackRecord(
    val sessionId: String,
    val songId: String,
    val songTitle: String,
    val artistName: String,
    val artistId: String? = null,
    val albumName: String? = null,
    val albumId: String? = null,
    val playlistName: String? = null,
    val playlistId: String? = null,
    val artworkUrl: String? = null,
    val timestamp: Long,
    val durationSec: Long,
    val completed: Boolean = false,
)

@Immutable
data class TopSongStat(
    val rank: Int,
    val songId: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val playCount: Int,
    val durationSec: Long,
) {
    fun toSong(): Song = Song(
        videoId = songId,
        title = title,
        artist = artist,
        artists = if (!artistId.isNullOrBlank()) listOf(com.darkxvenom.itune.data.model.ArtistRef(artist, artistId)) else emptyList(),
        thumbnailUrl = artworkUrl.orEmpty(),
        durationText = if (durationSec > 0) "${durationSec / 60}:${(durationSec % 60).toString().padStart(2, '0')}" else null,
        artistId = artistId,
        albumName = album,
    )
}

@Immutable
data class TopArtistStat(
    val rank: Int,
    val artistName: String,
    val artistId: String? = null,
    val artworkUrl: String? = null,
    val playCount: Int,
    val durationSec: Long,
)

@Immutable
data class TopAlbumStat(
    val rank: Int,
    val albumName: String,
    val artistName: String,
    val artworkUrl: String? = null,
    val playCount: Int,
    val durationSec: Long,
)

@Immutable
data class TopPlaylistStat(
    val rank: Int,
    val playlistName: String,
    val artworkUrl: String? = null,
    val playCount: Int,
    val durationSec: Long,
)

@Immutable
data class ListeningHistoryItem(
    val id: Long,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val timestamp: Long,
    val durationSec: Long,
) {
    fun toSong(): Song = Song(
        videoId = songId,
        title = title,
        artist = artist,
        artists = listOf(com.darkxvenom.itune.data.model.ArtistRef(artist, null)),
        thumbnailUrl = artworkUrl.orEmpty(),
        durationText = if (durationSec > 0) "${durationSec / 60}:${(durationSec % 60).toString().padStart(2, '0')}" else null,
        albumName = album,
    )
}

@Immutable
data class ListeningPersonality(
    val title: String,
    val tagline: String,
    val description: String,
    val primaryColorHex: Long = 0xFFFC3C44,
)

@Immutable
data class TimeTravelStats(
    val period: ListeningPeriod,
    val totalListeningSec: Long = 0L,
    val totalPlays: Int = 0,
    val averageDurationSec: Long = 0L,
    val topSongs: List<TopSongStat> = emptyList(),
    val topArtists: List<TopArtistStat> = emptyList(),
    val topAlbums: List<TopAlbumStat> = emptyList(),
    val topPlaylists: List<TopPlaylistStat> = emptyList(),
    val hourlyActivity: Map<Int, Int> = emptyMap(), // 0..23 -> count
    val dailyActivity: Map<Int, Int> = emptyMap(), // 0..6 (Sun=0..Sat=6) -> count
    val personality: ListeningPersonality = ListeningPersonality(
        title = "The Deep Diver",
        tagline = "Immersive & Focused",
        description = "You explore music deeply, letting tracks take you on seamless emotional journeys.",
    ),
    val timeline: List<ListeningHistoryItem> = emptyList(),
) {
    val isEmpty: Boolean get() = totalPlays == 0 && totalListeningSec == 0L

    val formattedListeningTime: String
        get() {
            val hours = totalListeningSec / 3600L
            val minutes = (totalListeningSec % 3600L) / 60L
            return when {
                hours > 0 -> "${hours}h ${minutes}m"
                minutes > 0 -> "${minutes}m"
                totalListeningSec > 0 -> "${totalListeningSec}s"
                else -> "0m"
            }
        }

    val mostActiveHour: Int?
        get() = hourlyActivity.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key

    val mostActiveDay: Int?
        get() = dailyActivity.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key

    val distinctSongsCount: Int get() = topSongs.size
    val distinctArtistsCount: Int get() = topArtists.size
    val distinctAlbumsCount: Int get() = topAlbums.size
    val peakListeningHour: Int get() = mostActiveHour ?: 16
    val busiestDay: String? get() = mostActiveDay?.let { dayIdx ->
        when (dayIdx) {
            0 -> "Sunday"
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            else -> "Saturday"
        }
    }
}
