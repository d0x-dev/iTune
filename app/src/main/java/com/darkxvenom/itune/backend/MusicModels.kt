package com.darkxvenom.itune.backend

import androidx.compose.runtime.Immutable

@Immutable
data class MusicTrack(
    val id: String,
    val title: String,
    val artists: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val thumbnailUrl: String? = null,
    val durationSec: Long = 0,
    val liked: Boolean = false,
    val isVideo: Boolean = false,
) {
    val duration: String
        get() = if (durationSec > 0) formatMusicDuration(durationSec) else ""
}

@Immutable
data class MusicArtist(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val subscribers: String? = null,
)

@Immutable
data class MusicPlaylistRef(
    val id: String,
    val title: String,
    val author: String = "",
    val authorId: String? = null,
    val thumbnailUrl: String? = null,
    val isAlbum: Boolean = false,
    val trackCount: Int = 0,
    val year: String? = null,
)

@Immutable
data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val words: List<WordTiming> = emptyList(),
)

@Immutable
data class WordTiming(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** Upgrades YouTube and Google CDN album artwork to high resolution (544px / 720p). */
fun String.highQualityThumbnail(): String {
    var url = this
    if (url.contains("googleusercontent.com") || url.contains("ggpht.com")) {
        url = if (url.contains(Regex("=w\\d+-h\\d+"))) {
            url.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
        } else if (url.contains(Regex("=s\\d+"))) {
            url.replace(Regex("=s\\d+"), "=s544")
        } else {
            "$url=w544-h544"
        }
    }
    // YouTube video thumbnail upgrade
    if (url.contains("i.ytimg.com/vi/")) {
        if (url.contains("/default.jpg") || url.contains("/mqdefault.jpg") || url.contains("/hqdefault.jpg")) {
            url = url.replace(Regex("/(default|mqdefault|hqdefault)\\.jpg"), "/hq720.jpg")
        }
    }
    return url
}

/** Formats seconds into MM:SS or HH:MM:SS string. */
fun formatMusicDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val m = seconds / 60
    val s = seconds % 60
    return if (seconds >= 3600) {
        val h = seconds / 3600
        val remM = (seconds % 3600) / 60
        "%d:%02d:%02d".format(h, remM, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

/** Formats milliseconds into MM:SS string. */
fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Parses time string such as "3:45" or "1:15:20" to seconds. */
fun parseTimeString(timeStr: String): Long {
    val parts = timeStr.trim().split(":")
    return when (parts.size) {
        1 -> parts[0].toLongOrNull() ?: 0L
        2 -> (parts[0].toLongOrNull() ?: 0L) * 60 + (parts[1].toLongOrNull() ?: 0L)
        3 -> (parts[0].toLongOrNull() ?: 0L) * 3600 + (parts[1].toLongOrNull() ?: 0L) * 60 + (parts[2].toLongOrNull() ?: 0L)
        else -> 0L
    }
}
