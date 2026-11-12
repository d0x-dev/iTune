package com.darkxvenom.itune.data.library

import android.content.Context
import android.content.SharedPreferences
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.exp

/**
 * Local, on-device listening profile. Every song the user plays is counted per song and per artist
 * with exponential time decay (half-life ~3 weeks) so recent taste wins. The Home screen uses this
 * for "Your Top Artists" and "Because You Listened To …" recommendations, with or without sign-in.
 */
object ListeningProfileStore {
    private const val PREFS = "itune_listening_profile"
    private const val KEY_SONGS = "songs_v1"
    private const val KEY_ARTISTS = "artists_v1"
    private const val MAX_SONGS = 300
    private const val MAX_ARTISTS = 120
    private const val DECAY_PER_DAY = 0.033 // ln(2) / 21 days
    private const val MIN_REPEAT_GAP_MS = 30_000L

    data class SongStat(
        val id: String,
        val title: String,
        val artists: String,
        val artistId: String?,
        val thumbnailUrl: String?,
        val score: Double,
        val plays: Int,
        val lastPlayedMs: Long,
    )

    data class ArtistStat(
        val key: String,
        val name: String,
        val artistId: String?,
        val photoUrl: String?,
        val score: Double,
        val plays: Int,
        val lastPlayedMs: Long,
        /** Video id of the most played song by this artist, used to seed artist radio. */
        val topSongId: String?,
    )

    @Volatile private var prefs: SharedPreferences? = null
    private var lastRecordedId: String? = null
    private var lastRecordedAt = 0L

    private val _songs = MutableStateFlow<List<SongStat>>(emptyList())
    private val _artists = MutableStateFlow<List<ArtistStat>>(emptyList())
    val songs: StateFlow<List<SongStat>> = _songs.asStateFlow()
    val artists: StateFlow<List<ArtistStat>> = _artists.asStateFlow()

    val hasHistory: Boolean get() = _songs.value.isNotEmpty()

    fun ensureInit(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs = p
            _songs.value = decodeSongs(p.getString(KEY_SONGS, null))
            _artists.value = decodeArtists(p.getString(KEY_ARTISTS, null))
        }
    }

    fun recordPlay(context: Context, track: MusicTrack) {
        ensureInit(context)
        if (track.id.isBlank() || track.isVideo && track.artists.isBlank()) return
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (lastRecordedId == track.id && now - lastRecordedAt < MIN_REPEAT_GAP_MS) return
            lastRecordedId = track.id
            lastRecordedAt = now

            val songs = _songs.value.toMutableList()
            val si = songs.indexOfFirst { it.id == track.id }
            val old = songs.getOrNull(si)
            val song = SongStat(
                id = track.id,
                title = track.title,
                artists = track.artists,
                artistId = track.artistId ?: old?.artistId,
                thumbnailUrl = track.thumbnailUrl ?: old?.thumbnailUrl,
                score = decayed(old?.score, old?.lastPlayedMs, now) + 1.0,
                plays = (old?.plays ?: 0) + 1,
                lastPlayedMs = now,
            )
            if (si >= 0) songs[si] = song else songs.add(song)
            val trimmedSongs = songs.sortedByDescending { it.score }.take(MAX_SONGS)
            _songs.value = trimmedSongs

            val primary = primaryArtist(track.artists)
            if (primary.isNotBlank()) {
                val key = primary.lowercase()
                val artists = _artists.value.toMutableList()
                val ai = artists.indexOfFirst { it.key == key }
                val prev = artists.getOrNull(ai)
                val newScore = decayed(prev?.score, prev?.lastPlayedMs, now) + 1.0
                val topSong = trimmedSongs
                    .filter { primaryArtist(it.artists).equals(primary, ignoreCase = true) }
                    .maxByOrNull { it.score }?.id
                val artist = ArtistStat(
                    key = key,
                    name = primary,
                    artistId = track.artistId ?: prev?.artistId,
                    photoUrl = prev?.photoUrl,
                    score = newScore,
                    plays = (prev?.plays ?: 0) + 1,
                    lastPlayedMs = now,
                    topSongId = topSong ?: track.id,
                )
                if (ai >= 0) artists[ai] = artist else artists.add(artist)
                _artists.value = artists.sortedByDescending { it.score }.take(MAX_ARTISTS)
            }
            persist()
        }
    }

    fun setArtistPhoto(artistKey: String, photoUrl: String) {
        synchronized(this) {
            val list = _artists.value.toMutableList()
            val i = list.indexOfFirst { it.key == artistKey }
            if (i < 0) return
            list[i] = list[i].copy(photoUrl = photoUrl)
            _artists.value = list
            persist()
        }
    }

    fun topArtists(limit: Int): List<ArtistStat> = _artists.value.take(limit)

    fun topSongs(limit: Int): List<SongStat> = _songs.value.take(limit)

    fun clear() {
        synchronized(this) {
            _songs.value = emptyList()
            _artists.value = emptyList()
            prefs?.edit()?.remove(KEY_SONGS)?.remove(KEY_ARTISTS)?.apply()
        }
    }

    private fun primaryArtist(artists: String): String =
        artists.split(",", "&", " x ", " feat. ", " ft. ").firstOrNull()?.trim().orEmpty()

    private fun decayed(score: Double?, lastMs: Long?, now: Long): Double {
        if (score == null || lastMs == null) return 0.0
        val days = ((now - lastMs).coerceAtLeast(0L)) / 86_400_000.0
        return score * exp(-DECAY_PER_DAY * days)
    }

    private fun persist() {
        val p = prefs ?: return
        val songsJson = JSONArray()
        _songs.value.forEach {
            songsJson.put(
                JSONObject().put("id", it.id).put("t", it.title).put("a", it.artists)
                    .put("aid", it.artistId).put("th", it.thumbnailUrl).put("s", it.score)
                    .put("p", it.plays).put("l", it.lastPlayedMs),
            )
        }
        val artistsJson = JSONArray()
        _artists.value.forEach {
            artistsJson.put(
                JSONObject().put("k", it.key).put("n", it.name).put("aid", it.artistId)
                    .put("ph", it.photoUrl).put("s", it.score).put("p", it.plays)
                    .put("l", it.lastPlayedMs).put("ts", it.topSongId),
            )
        }
        p.edit().putString(KEY_SONGS, songsJson.toString()).putString(KEY_ARTISTS, artistsJson.toString()).apply()
    }

    private fun decodeSongs(raw: String?): List<SongStat> = runCatching {
        val arr = JSONArray(raw ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SongStat(
                id, o.optString("t"), o.optString("a"),
                o.optString("aid").takeIf { it.isNotBlank() && it != "null" },
                o.optString("th").takeIf { it.isNotBlank() && it != "null" },
                o.optDouble("s", 1.0), o.optInt("p", 1), o.optLong("l", 0L),
            )
        }
    }.getOrDefault(emptyList())

    private fun decodeArtists(raw: String?): List<ArtistStat> = runCatching {
        val arr = JSONArray(raw ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val key = o.optString("k").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ArtistStat(
                key, o.optString("n"),
                o.optString("aid").takeIf { it.isNotBlank() && it != "null" },
                o.optString("ph").takeIf { it.isNotBlank() && it != "null" },
                o.optDouble("s", 1.0), o.optInt("p", 1), o.optLong("l", 0L),
                o.optString("ts").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }.getOrDefault(emptyList())
}
