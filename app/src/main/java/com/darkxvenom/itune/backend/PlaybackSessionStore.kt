package com.darkxvenom.itune.backend

import android.content.Context
import android.util.Log
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

internal data class PlaybackSessionSnapshot(
    val track: MusicTrack,
    val positionMs: Long,
    val durationMs: Long,
    val queue: List<MusicTrack>,
    val queueIndex: Int,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
)

/**
 * Persists the active playback session (track, stopped position, duration, queue, and modes)
 * in SharedPreferences so reopening the app restores the Mini Player at the exact paused timestamp.
 */
internal object PlaybackSessionStore {
    private const val TAG = "PlaybackSessionStore"
    private const val PREFS_NAME = "itune_playback_session"
    private const val KEY_SESSION_JSON = "session_json"
    private const val MAX_PERSISTED_QUEUE_SIZE = 50

    fun save(context: Context, snapshot: PlaybackSessionSnapshot, sync: Boolean = false) {
        try {
            val (boundedQueue, boundedIndex) = boundQueueWindow(
                queue = snapshot.queue,
                currentTrack = snapshot.track,
                currentIndex = snapshot.queueIndex
            )
            val root = JSONObject().apply {
                put("track", trackToJson(snapshot.track))
                put("positionMs", snapshot.positionMs.coerceAtLeast(0L))
                put("durationMs", snapshot.durationMs.coerceAtLeast(0L))
                put("queueIndex", boundedIndex)
                put("shuffleEnabled", snapshot.shuffleEnabled)
                put("repeatMode", snapshot.repeatMode)
                val queueArray = JSONArray()
                boundedQueue.forEach { item -> queueArray.put(trackToJson(item)) }
                put("queue", queueArray)
            }
            val editor = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SESSION_JSON, root.toString())
            if (sync) editor.commit() else editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist playback session", e)
        }
    }

    fun load(context: Context): PlaybackSessionSnapshot? {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SESSION_JSON, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return try {
            val root = JSONObject(raw)
            val trackObj = root.optJSONObject("track") ?: return null
            val track = trackFromJson(trackObj) ?: return null

            val parsedQueue = mutableListOf<MusicTrack>()
            val queueArray = root.optJSONArray("queue")
            if (queueArray != null) {
                for (i in 0 until queueArray.length()) {
                    val item = queueArray.optJSONObject(i)?.let(::trackFromJson)
                    if (item != null) parsedQueue.add(item)
                }
            }
            if (parsedQueue.none { it.id == track.id }) {
                parsedQueue.add(0, track)
            }
            val matchedIndex = parsedQueue.indexOfFirst { it.id == track.id }
            val savedIndex = root.optInt("queueIndex", matchedIndex)
            val normalizedIndex = if (savedIndex in parsedQueue.indices && parsedQueue[savedIndex].id == track.id) {
                savedIndex
            } else {
                matchedIndex.coerceIn(0, parsedQueue.lastIndex)
            }

            val repeatMode = when (root.optInt("repeatMode", Player.REPEAT_MODE_OFF)) {
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }

            PlaybackSessionSnapshot(
                track = track,
                positionMs = root.optLong("positionMs", 0L).coerceAtLeast(0L),
                durationMs = root.optLong("durationMs", 0L).coerceAtLeast(0L),
                queue = parsedQueue,
                queueIndex = normalizedIndex,
                shuffleEnabled = root.optBoolean("shuffleEnabled", false),
                repeatMode = repeatMode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore playback session", e)
            null
        }
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SESSION_JSON)
            .commit()
    }

    /** Keeps at most [MAX_PERSISTED_QUEUE_SIZE] tracks centered around [currentIndex] so preferences stay compact. */
    private fun boundQueueWindow(
        queue: List<MusicTrack>,
        currentTrack: MusicTrack,
        currentIndex: Int
    ): Pair<List<MusicTrack>, Int> {
        if (queue.isEmpty()) return listOf(currentTrack) to 0
        val safeIdx = currentIndex.coerceIn(0, queue.lastIndex)
        if (queue.size <= MAX_PERSISTED_QUEUE_SIZE) return queue to safeIdx

        val half = MAX_PERSISTED_QUEUE_SIZE / 2
        val start = (safeIdx - half).coerceIn(0, queue.size - MAX_PERSISTED_QUEUE_SIZE)
        val endExclusive = (start + MAX_PERSISTED_QUEUE_SIZE).coerceAtMost(queue.size)
        return queue.subList(start, endExclusive) to (safeIdx - start)
    }

    private fun trackToJson(track: MusicTrack): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("artists", track.artists)
        put("artistId", track.artistId)
        put("album", track.album)
        put("albumId", track.albumId)
        put("thumbnailUrl", track.thumbnailUrl)
        put("durationSec", track.durationSec)
        put("liked", track.liked)
        put("isVideo", track.isVideo)
    }

    private fun trackFromJson(obj: JSONObject): MusicTrack? {
        val id = obj.optString("id", "").trim()
        if (id.isEmpty()) return null
        return MusicTrack(
            id = id,
            title = obj.optString("title", ""),
            artists = obj.optString("artists", ""),
            artistId = obj.optString("artistId", "").ifBlank { null },
            album = obj.optString("album", "").ifBlank { null },
            albumId = obj.optString("albumId", "").ifBlank { null },
            thumbnailUrl = obj.optString("thumbnailUrl", "").ifBlank { null },
            durationSec = obj.optLong("durationSec", 0L).coerceAtLeast(0L),
            liked = obj.optBoolean("liked", false),
            isVideo = obj.optBoolean("isVideo", false)
        )
    }
}
