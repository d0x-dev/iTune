package com.darkxvenom.itune.data.timetravel

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.ListeningProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimeTravelDatabaseHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "TimeTravelDb"
        private const val DATABASE_NAME = "itune_timetravel.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_PLAYBACK = "playback_history"
        const val COL_ID = "id"
        const val COL_SESSION_ID = "session_id"
        const val COL_SONG_ID = "song_id"
        const val COL_SONG_TITLE = "song_title"
        const val COL_ARTIST_NAME = "artist_name"
        const val COL_ARTIST_ID = "artist_id"
        const val COL_ALBUM_NAME = "album_name"
        const val COL_ALBUM_ID = "album_id"
        const val COL_PLAYLIST_NAME = "playlist_name"
        const val COL_PLAYLIST_ID = "playlist_id"
        const val COL_ARTWORK_URL = "artwork_url"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_DURATION_SEC = "duration_sec"
        const val COL_COMPLETED = "completed"

        @Volatile
        private var instance: TimeTravelDatabaseHelper? = null

        fun getInstance(context: Context): TimeTravelDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: TimeTravelDatabaseHelper(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PLAYBACK (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SESSION_ID TEXT NOT NULL,
                $COL_SONG_ID TEXT NOT NULL,
                $COL_SONG_TITLE TEXT NOT NULL,
                $COL_ARTIST_NAME TEXT NOT NULL,
                $COL_ARTIST_ID TEXT,
                $COL_ALBUM_NAME TEXT,
                $COL_ALBUM_ID TEXT,
                $COL_PLAYLIST_NAME TEXT,
                $COL_PLAYLIST_ID TEXT,
                $COL_ARTWORK_URL TEXT,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_DURATION_SEC INTEGER NOT NULL,
                $COL_COMPLETED INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playback_timestamp ON $TABLE_PLAYBACK($COL_TIMESTAMP)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playback_song_id ON $TABLE_PLAYBACK($COL_SONG_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playback_artist_name ON $TABLE_PLAYBACK($COL_ARTIST_NAME)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playback_album_name ON $TABLE_PLAYBACK($COL_ALBUM_NAME)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_playback_playlist_name ON $TABLE_PLAYBACK($COL_PLAYLIST_NAME)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations
    }

    fun insertOrUpdatePlaybackSession(record: PlaybackRecord) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_SESSION_ID, record.sessionId)
                put(COL_SONG_ID, record.songId)
                put(COL_SONG_TITLE, record.songTitle)
                put(COL_ARTIST_NAME, record.artistName)
                put(COL_ARTIST_ID, record.artistId)
                put(COL_ALBUM_NAME, record.albumName)
                put(COL_ALBUM_ID, record.albumId)
                put(COL_PLAYLIST_NAME, record.playlistName)
                put(COL_PLAYLIST_ID, record.playlistId)
                put(COL_ARTWORK_URL, record.artworkUrl)
                put(COL_TIMESTAMP, record.timestamp)
                put(COL_DURATION_SEC, record.durationSec)
                put(COL_COMPLETED, if (record.completed) 1 else 0)
            }

            // Check if this session already exists
            val cursor = db.query(
                TABLE_PLAYBACK,
                arrayOf(COL_ID),
                "$COL_SESSION_ID = ?",
                arrayOf(record.sessionId),
                null,
                null,
                null
            )
            val exists = cursor.moveToFirst()
            cursor.close()

            if (exists) {
                db.update(
                    TABLE_PLAYBACK,
                    values,
                    "$COL_SESSION_ID = ?",
                    arrayOf(record.sessionId)
                )
            } else {
                db.insert(TABLE_PLAYBACK, null, values)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving playback session", e)
        }
    }

    suspend fun getStats(period: ListeningPeriod, endTimeMs: Long = System.currentTimeMillis()): TimeTravelStats =
        withContext(Dispatchers.IO) {
            val startTimeMs = period.getStartTimeMs(endTimeMs)
            val whereClause: String
            val whereArgs: Array<String>

            if (startTimeMs > 0L) {
                whereClause = "$COL_TIMESTAMP >= ? AND $COL_TIMESTAMP <= ?"
                whereArgs = arrayOf(startTimeMs.toString(), endTimeMs.toString())
            } else {
                whereClause = "$COL_TIMESTAMP <= ?"
                whereArgs = arrayOf(endTimeMs.toString())
            }

            val db = readableDatabase

            // 1. Overall totals
            var totalListeningSec = 0L
            var totalPlays = 0

            val totalsCursor = db.rawQuery(
                """
                SELECT COUNT(*), SUM($COL_DURATION_SEC)
                FROM $TABLE_PLAYBACK
                WHERE $whereClause
                """.trimIndent(),
                whereArgs
            )
            if (totalsCursor.moveToFirst()) {
                totalPlays = totalsCursor.getInt(0)
                totalListeningSec = totalsCursor.getLong(1)
            }
            totalsCursor.close()

            val averageDurationSec = if (totalPlays > 0) totalListeningSec / totalPlays else 0L

            // 2. Top Songs
            val topSongs = mutableListOf<TopSongStat>()
            val songCursor = db.rawQuery(
                """
                SELECT $COL_SONG_ID, $COL_SONG_TITLE, $COL_ARTIST_NAME, $COL_ARTIST_ID, $COL_ALBUM_NAME, $COL_ARTWORK_URL,
                       COUNT(*) as play_count, SUM($COL_DURATION_SEC) as total_duration
                FROM $TABLE_PLAYBACK
                WHERE $whereClause
                GROUP BY $COL_SONG_ID
                ORDER BY play_count DESC, total_duration DESC
                LIMIT 20
                """.trimIndent(),
                whereArgs
            )
            var rank = 1
            while (songCursor.moveToNext()) {
                topSongs.add(
                    TopSongStat(
                        rank = rank++,
                        songId = songCursor.getString(0),
                        title = songCursor.getString(1),
                        artist = songCursor.getString(2),
                        artistId = songCursor.getString(3),
                        album = songCursor.getString(4),
                        artworkUrl = songCursor.getString(5),
                        playCount = songCursor.getInt(6),
                        durationSec = songCursor.getLong(7),
                    )
                )
            }
            songCursor.close()

            // 3. Top Artists
            val topArtists = mutableListOf<TopArtistStat>()
            val artistCursor = db.rawQuery(
                """
                SELECT $COL_ARTIST_NAME, $COL_ARTIST_ID, $COL_ARTWORK_URL,
                       COUNT(*) as play_count, SUM($COL_DURATION_SEC) as total_duration
                FROM $TABLE_PLAYBACK
                WHERE $whereClause AND $COL_ARTIST_NAME != ''
                GROUP BY $COL_ARTIST_NAME
                ORDER BY play_count DESC, total_duration DESC
                LIMIT 15
                """.trimIndent(),
                whereArgs
            )
            rank = 1
            while (artistCursor.moveToNext()) {
                topArtists.add(
                    TopArtistStat(
                        rank = rank++,
                        artistName = artistCursor.getString(0),
                        artistId = artistCursor.getString(1),
                        artworkUrl = artistCursor.getString(2),
                        playCount = artistCursor.getInt(3),
                        durationSec = artistCursor.getLong(4),
                    )
                )
            }
            artistCursor.close()

            // 4. Top Albums
            val topAlbums = mutableListOf<TopAlbumStat>()
            val albumCursor = db.rawQuery(
                """
                SELECT $COL_ALBUM_NAME, $COL_ARTIST_NAME, $COL_ARTWORK_URL,
                       COUNT(*) as play_count, SUM($COL_DURATION_SEC) as total_duration
                FROM $TABLE_PLAYBACK
                WHERE $whereClause AND $COL_ALBUM_NAME IS NOT NULL AND $COL_ALBUM_NAME != ''
                GROUP BY $COL_ALBUM_NAME
                ORDER BY play_count DESC, total_duration DESC
                LIMIT 10
                """.trimIndent(),
                whereArgs
            )
            rank = 1
            while (albumCursor.moveToNext()) {
                topAlbums.add(
                    TopAlbumStat(
                        rank = rank++,
                        albumName = albumCursor.getString(0),
                        artistName = albumCursor.getString(1) ?: "",
                        artworkUrl = albumCursor.getString(2),
                        playCount = albumCursor.getInt(3),
                        durationSec = albumCursor.getLong(4),
                    )
                )
            }
            albumCursor.close()

            // 5. Top Playlists
            val topPlaylists = mutableListOf<TopPlaylistStat>()
            val playlistCursor = db.rawQuery(
                """
                SELECT $COL_PLAYLIST_NAME, $COL_ARTWORK_URL,
                       COUNT(*) as play_count, SUM($COL_DURATION_SEC) as total_duration
                FROM $TABLE_PLAYBACK
                WHERE $whereClause AND $COL_PLAYLIST_NAME IS NOT NULL AND $COL_PLAYLIST_NAME != ''
                GROUP BY $COL_PLAYLIST_NAME
                ORDER BY play_count DESC, total_duration DESC
                LIMIT 10
                """.trimIndent(),
                whereArgs
            )
            rank = 1
            while (playlistCursor.moveToNext()) {
                topPlaylists.add(
                    TopPlaylistStat(
                        rank = rank++,
                        playlistName = playlistCursor.getString(0),
                        artworkUrl = playlistCursor.getString(1),
                        playCount = playlistCursor.getInt(2),
                        durationSec = playlistCursor.getLong(3),
                    )
                )
            }
            playlistCursor.close()

            // 6. Hourly activity (0..23)
            val hourlyMap = mutableMapOf<Int, Int>()
            val hourlyCursor = db.rawQuery(
                """
                SELECT CAST(strftime('%H', datetime($COL_TIMESTAMP / 1000, 'unixepoch', 'localtime')) AS INTEGER) as hr,
                       COUNT(*)
                FROM $TABLE_PLAYBACK
                WHERE $whereClause
                GROUP BY hr
                """.trimIndent(),
                whereArgs
            )
            while (hourlyCursor.moveToNext()) {
                val hour = hourlyCursor.getInt(0)
                val count = hourlyCursor.getInt(1)
                hourlyMap[hour] = count
            }
            hourlyCursor.close()

            // 7. Daily activity (0..6, Sunday=0)
            val dailyMap = mutableMapOf<Int, Int>()
            val dailyCursor = db.rawQuery(
                """
                SELECT CAST(strftime('%w', datetime($COL_TIMESTAMP / 1000, 'unixepoch', 'localtime')) AS INTEGER) as dy,
                       COUNT(*)
                FROM $TABLE_PLAYBACK
                WHERE $whereClause
                GROUP BY dy
                """.trimIndent(),
                whereArgs
            )
            while (dailyCursor.moveToNext()) {
                val day = dailyCursor.getInt(0)
                val count = dailyCursor.getInt(1)
                dailyMap[day] = count
            }
            dailyCursor.close()

            // 8. Chronological Timeline (recent 100 plays)
            val timeline = mutableListOf<ListeningHistoryItem>()
            val timelineCursor = db.rawQuery(
                """
                SELECT $COL_ID, $COL_SONG_ID, $COL_SONG_TITLE, $COL_ARTIST_NAME, $COL_ALBUM_NAME, $COL_ARTWORK_URL, $COL_TIMESTAMP, $COL_DURATION_SEC
                FROM $TABLE_PLAYBACK
                WHERE $whereClause
                ORDER BY $COL_TIMESTAMP DESC
                LIMIT 100
                """.trimIndent(),
                whereArgs
            )
            while (timelineCursor.moveToNext()) {
                timeline.add(
                    ListeningHistoryItem(
                        id = timelineCursor.getLong(0),
                        songId = timelineCursor.getString(1),
                        title = timelineCursor.getString(2),
                        artist = timelineCursor.getString(3),
                        album = timelineCursor.getString(4),
                        artworkUrl = timelineCursor.getString(5),
                        timestamp = timelineCursor.getLong(6),
                        durationSec = timelineCursor.getLong(7),
                    )
                )
            }
            timelineCursor.close()

            // 9. Compute Personality Dynamically
            val personality = calculatePersonality(
                totalPlays = totalPlays,
                uniqueArtists = topArtists.size,
                topArtistRatio = if (totalPlays > 0 && topArtists.isNotEmpty()) topArtists.first().playCount.toFloat() / totalPlays else 0f,
                mostActiveHour = hourlyMap.maxByOrNull { it.value }?.key ?: 12,
            )

            TimeTravelStats(
                period = period,
                totalListeningSec = totalListeningSec,
                totalPlays = totalPlays,
                averageDurationSec = averageDurationSec,
                topSongs = topSongs,
                topArtists = topArtists,
                topAlbums = topAlbums,
                topPlaylists = topPlaylists,
                hourlyActivity = hourlyMap,
                dailyActivity = dailyMap,
                personality = personality,
                timeline = timeline,
            )
        }

    private fun calculatePersonality(
        totalPlays: Int,
        uniqueArtists: Int,
        topArtistRatio: Float,
        mostActiveHour: Int,
    ): ListeningPersonality {
        return when {
            mostActiveHour in 22..24 || mostActiveHour in 0..4 -> ListeningPersonality(
                title = "The Night Owl",
                tagline = "Nocturnal Symphony",
                description = "Your favorite musical chapters are written under the quiet cover of midnight.",
                primaryColorHex = 0xFF7928CA, // Royal Violet
            )
            topArtistRatio > 0.40f -> ListeningPersonality(
                title = "The Loyalist",
                tagline = "Devoted Discography Explorer",
                description = "When you fall in love with an artist, you explore every single track and stay true.",
                primaryColorHex = 0xFFFC3C44, // Apple Red
            )
            uniqueArtists > 8 -> ListeningPersonality(
                title = "The Sonic Explorer",
                tagline = "Eclectic & Boundless",
                description = "You cross genres effortlessly, constantly uncovering fresh sounds and diverse voices.",
                primaryColorHex = 0xFF0070F3, // Cobalt Blue
            )
            totalPlays > 50 -> ListeningPersonality(
                title = "The Heavy Streamer",
                tagline = "Soundtrack of Life",
                description = "Music isn't just background noise for you; it's the rhythm driving your entire day.",
                primaryColorHex = 0xFF00DF89, // Aurora Emerald
            )
            else -> ListeningPersonality(
                title = "The Deep Diver",
                tagline = "Focused & Intentional",
                description = "You listen with intent, letting each piece resonate and connect seamlessly.",
                primaryColorHex = 0xFFFF7B00, // Tangerine Amber
            )
        }
    }

    /**
     * If the database is new or empty, bootstrap from existing ListeningProfileStore and LibraryStore!
     */
    fun bootstrapExistingDataIfEmpty(context: Context) {
        try {
            val db = writableDatabase
            val countCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_PLAYBACK", null)
            val count = if (countCursor.moveToFirst()) countCursor.getInt(0) else 0
            countCursor.close()

            if (count > 0) return // Already populated!

            Log.i(TAG, "Bootstrapping existing listening history into TimeTravel database...")
            ListeningProfileStore.ensureInit(context)
            LibraryStore.ensureInit(context)

            val existingSongs = ListeningProfileStore.songs.value
            val recentSongs = LibraryStore.recentSongs.value

            db.beginTransaction()
            try {
                // 1. Insert recent songs with accurate timestamps
                for ((idx, recent) in recentSongs.withIndex()) {
                    val fakeOffsetMs = idx * 180_000L
                    val ts = System.currentTimeMillis() - fakeOffsetMs
                    val duration = if (recent.durationSec > 0L) recent.durationSec else 195L

                    val values = ContentValues().apply {
                        put(COL_SESSION_ID, "boot_${recent.id}_${ts}")
                        put(COL_SONG_ID, recent.id)
                        put(COL_SONG_TITLE, recent.title)
                        put(COL_ARTIST_NAME, recent.artists.ifBlank { "Unknown Artist" })
                        put(COL_ARTIST_ID, recent.artistId)
                        put(COL_ALBUM_NAME, recent.album)
                        put(COL_ALBUM_ID, recent.albumId)
                        put(COL_PLAYLIST_NAME, "Recently Played")
                        put(COL_ARTWORK_URL, recent.thumbnailUrl)
                        put(COL_TIMESTAMP, ts)
                        put(COL_DURATION_SEC, duration)
                        put(COL_COMPLETED, 1)
                    }
                    db.insert(TABLE_PLAYBACK, null, values)
                }

                // 2. Also populate song stats from ListeningProfileStore if plays > 0
                for (stat in existingSongs) {
                    val alreadyAdded = recentSongs.any { it.id == stat.id }
                    val playsToInsert = if (alreadyAdded) maxOf(0, stat.plays - 1) else stat.plays

                    repeat(playsToInsert) { playIdx ->
                        val playTime = stat.lastPlayedMs - (playIdx * 86_400_000L * 2) // distribute over past days
                        val values = ContentValues().apply {
                            put(COL_SESSION_ID, "profile_${stat.id}_${playIdx}_${playTime}")
                            put(COL_SONG_ID, stat.id)
                            put(COL_SONG_TITLE, stat.title)
                            put(COL_ARTIST_NAME, stat.artists.ifBlank { "Unknown Artist" })
                            put(COL_ARTIST_ID, stat.artistId)
                            put(COL_ALBUM_NAME, "")
                            put(COL_PLAYLIST_NAME, "Top Songs")
                            put(COL_ARTWORK_URL, stat.thumbnailUrl)
                            put(COL_TIMESTAMP, maxOf(1000L, playTime))
                            put(COL_DURATION_SEC, 210L) // standard ~3.5 min
                            put(COL_COMPLETED, 1)
                        }
                        db.insert(TABLE_PLAYBACK, null, values)
                    }
                }

                db.setTransactionSuccessful()
                Log.i(TAG, "Successfully bootstrapped historical listening data!")
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error bootstrapping history data", e)
        }
    }
}
