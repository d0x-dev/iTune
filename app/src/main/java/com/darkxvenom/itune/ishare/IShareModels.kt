package com.darkxvenom.itune.ishare

import androidx.compose.runtime.Immutable
import com.darkxvenom.itune.backend.MusicTrack
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder

enum class IShareConnectionMode {
    ONLINE,
    LAN,
}

@Immutable
data class DiscoveredLanHost(
    val address: String,
    val hostName: String,
    val participants: Int = 1,
    val sessionId: String = "",
)

@Immutable
data class IShareSettings(
    val allowGuestsToControlPlayback: Boolean = false,
    val allowGuestsToAddTracks: Boolean = true,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("allowGuestsToControlPlayback", allowGuestsToControlPlayback)
            .put("allowGuestsToAddTracks", allowGuestsToAddTracks)

    companion object {
        fun fromJson(json: JSONObject?): IShareSettings {
            if (json == null) return IShareSettings()
            return IShareSettings(
                allowGuestsToControlPlayback = json.optBoolean("allowGuestsToControlPlayback", false),
                allowGuestsToAddTracks = json.optBoolean("allowGuestsToAddTracks", true),
            )
        }
    }
}

@Immutable
data class IShareQueueItem(
    val songId: String,
    val title: String,
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        id = songId,
        title = title.ifBlank { "Unknown Song" },
        artists = artist.ifBlank { "iShare" },
        thumbnailUrl = thumbnailUrl ?: songId.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" },
        durationSec = if (durationMs > 0L) durationMs / 1000L else 0L,
    )

    fun toJson(): JSONObject =
        JSONObject()
            .put("id", songId)
            .put("title", title)
            .put("artist", artist)
            .put("thumbnailUrl", thumbnailUrl.orEmpty())
            .put("durationMs", durationMs)

    companion object {
        fun fromMusicTrack(track: MusicTrack): IShareQueueItem = IShareQueueItem(
            songId = track.id,
            title = track.title,
            artist = track.artists,
            thumbnailUrl = track.thumbnailUrl,
            durationMs = (track.durationSec * 1000L).coerceAtLeast(0L),
        )

        fun fromJson(json: JSONObject): IShareQueueItem = IShareQueueItem(
            songId = json.optString("id").ifBlank { json.optString("songId") },
            title = json.optString("title").ifBlank { "Unknown Song" },
            artist = json.optString("artist").ifBlank { json.optString("artists") },
            thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
            durationMs = json.optLong("durationMs", 0L),
        )
    }
}

@Immutable
data class IShareParticipant(
    val id: String,
    val name: String,
    val isHost: Boolean,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("isHost", isHost)

    companion object {
        fun fromJson(json: JSONObject): IShareParticipant = IShareParticipant(
            id = json.optString("id"),
            name = json.optString("name").ifBlank { "iShare Listener" },
            isHost = json.optBoolean("isHost", false),
        )
    }
}

@Immutable
data class ISharePlaybackState(
    val songId: String = "",
    val title: String = "",
    val artists: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val settings: IShareSettings = IShareSettings(),
    val actionCommand: String? = null,
    val actionPayload: String? = null,
    val actionId: String? = null,
    val queue: List<IShareQueueItem> = emptyList(),
    val totalQueueSize: Int = 0,
) {
    fun toMusicTrack(): MusicTrack = MusicTrack(
        id = songId,
        title = title.ifBlank { "Unknown Track" },
        artists = artists.joinToString(", ").ifBlank { "iShare" },
        thumbnailUrl = thumbnailUrl ?: songId.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" },
        durationSec = if (durationMs > 0L) durationMs / 1000L else 0L,
    )

    fun toJson(forOnlineWorker: Boolean = false): JSONObject {
        val cleanArtists = artists.filterNot { it.startsWith(META_PREFIX) || it.startsWith(QUEUE_PREFIX) }
        val artistsList = mutableListOf<String>()

        if (forOnlineWorker) {
            // Match AirBeats Cloudflare Worker limits (max 8 artist strings, <=120 chars each)
            val primaryArtist = cleanArtists.firstOrNull()?.trim()?.take(35)?.ifBlank { null } ?: "iShare"
            artistsList.add(primaryArtist)

            val metaString = buildString {
                append(META_PREFIX)
                append("pb=").append(if (settings.allowGuestsToControlPlayback) "1" else "0")
                append("&tr=").append(if (settings.allowGuestsToAddTracks) "1" else "0")
                if (!actionCommand.isNullOrBlank()) {
                    append("&cmd=").append(actionCommand)
                }
                if (!actionId.isNullOrBlank()) {
                    append("&actId=").append(actionId)
                }
                if (durationMs > 0L) {
                    append("&dur=").append(durationMs)
                }
                if (totalQueueSize > 0) {
                    append("&qs=").append(totalQueueSize)
                }
            }
            artistsList.add(metaString.take(118))

            if (queue.isNotEmpty()) {
                queue.take(18).chunked(3).take(6).forEachIndexed { idx, chunkItems ->
                    val payload = chunkItems.joinToString(";") { qItem ->
                        val safeTitle = qItem.title
                            .replace(";", " ")
                            .replace(":", " ")
                            .replace("&", " ")
                            .replace("#", " ")
                            .trim()
                            .take(20)
                        "${qItem.songId}:$safeTitle"
                    }
                    artistsList.add("${QUEUE_PREFIX}$idx:$payload".take(115))
                }
            }
        } else {
            cleanArtists.forEach { artistsList.add(it) }
        }

        val rawThumb = (thumbnailUrl ?: songId.takeIf { it.isNotBlank() }?.let { "https://i.ytimg.com/vi/$it/mqdefault.jpg" })
            ?.substringBefore("#")
        val effectiveThumbnailUrl = if (forOnlineWorker && !rawThumb.isNullOrBlank() && queue.isNotEmpty()) {
            val qPayload = queue.take(15).joinToString(";") { qItem ->
                val safeTitle = qItem.title.replace(";", " ").replace(":", " ").replace("#", " ").trim().take(15)
                "${qItem.songId}:$safeTitle"
            }
            "$rawThumb#Q=$qPayload".take(580)
        } else {
            rawThumb
        }

        val queueArray = JSONArray()
        queue.forEach { queueArray.put(it.toJson()) }

        return JSONObject()
            .put("songId", songId)
            .put("title", title)
            .put("artists", JSONArray(artistsList))
            .put("thumbnailUrl", effectiveThumbnailUrl)
            .put("positionMs", positionMs)
            .put("isPlaying", isPlaying)
            .put("durationMs", durationMs)
            .put("updatedAt", updatedAt)
            .put("settings", settings.toJson())
            .put("actionCommand", actionCommand)
            .put("actionPayload", actionPayload)
            .put("actionId", actionId)
            .put("queue", queueArray)
            .put("totalQueueSize", totalQueueSize)
    }

    companion object {
        const val META_PREFIX = "__AB_META__:"
        const val QUEUE_PREFIX = "__AB_Q"

        fun fromJson(json: JSONObject): ISharePlaybackState {
            var parsedSettings: IShareSettings? =
                json.optJSONObject("settings")?.let(IShareSettings::fromJson)
            var parsedCommand: String? = json.optString("actionCommand").takeIf { it.isNotBlank() }
            var parsedPayload: String? = json.optString("actionPayload").takeIf { it.isNotBlank() }
            var parsedActionId: String? = json.optString("actionId").takeIf { it.isNotBlank() }
            var parsedDuration: Long = json.optLong("durationMs", 0L)
            var parsedQueueSize: Int = json.optInt("totalQueueSize", 0)

            val cleanArtists = mutableListOf<String>()
            val parsedQueueItems = mutableListOf<IShareQueueItem>()

            json.optJSONArray("queue")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { obj ->
                        parsedQueueItems.add(IShareQueueItem.fromJson(obj))
                    }
                }
            }

            val rawThumb = json.optString("thumbnailUrl").takeIf { it.isNotBlank() }
            val cleanThumb = rawThumb?.substringBefore("#")
            if (rawThumb != null && rawThumb.contains("#Q=")) {
                val qPayload = rawThumb.substringAfter("#Q=").substringBefore("&")
                qPayload.split(";").forEach { entry ->
                    val colonIdx = entry.indexOf(':')
                    if (colonIdx > 0) {
                        val id = entry.substring(0, colonIdx).trim()
                        val title = entry.substring(colonIdx + 1).trim()
                        if (id.isNotBlank()) {
                            parsedQueueItems.add(
                                IShareQueueItem(
                                    songId = id,
                                    title = title.ifBlank { "Queued track" },
                                    thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                                ),
                            )
                        }
                    }
                }
            }

            json.optJSONArray("artists")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optString(i)
                    when {
                        item.startsWith(META_PREFIX) -> {
                            val query = item.removePrefix(META_PREFIX)
                            val params = query.split("&").associate { param ->
                                val parts = param.split("=", limit = 2)
                                if (parts.size == 2) {
                                    parts[0] to runCatching { URLDecoder.decode(parts[1], "UTF-8") }.getOrDefault(parts[1])
                                } else {
                                    parts[0] to ""
                                }
                            }
                            val pb = params["pb"]
                            val tr = params["tr"]
                            if (pb != null || tr != null) {
                                parsedSettings = IShareSettings(
                                    allowGuestsToControlPlayback = pb == "1",
                                    allowGuestsToAddTracks = tr != "0",
                                )
                            }
                            params["cmd"]?.takeIf { it.isNotBlank() }?.let { parsedCommand = it }
                            params["pl"]?.takeIf { it.isNotBlank() }?.let { parsedPayload = it }
                            params["actId"]?.takeIf { it.isNotBlank() }?.let { parsedActionId = it }
                            params["dur"]?.toLongOrNull()?.let { if (it > 0) parsedDuration = it }
                            (params["qs"] ?: params["qSize"])?.toIntOrNull()?.let { if (it > 0) parsedQueueSize = it }
                        }
                        item.startsWith(QUEUE_PREFIX) -> {
                            val payload = item.substringAfter(":")
                            payload.split(";").forEach { entry ->
                                val colonIdx = entry.indexOf(':')
                                if (colonIdx > 0) {
                                    val id = entry.substring(0, colonIdx).trim()
                                    val title = entry.substring(colonIdx + 1).trim()
                                    if (id.isNotBlank()) {
                                        parsedQueueItems.add(
                                            IShareQueueItem(
                                                songId = id,
                                                title = title.ifBlank { "Queued track" },
                                                thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        item.isNotBlank() -> cleanArtists.add(item)
                    }
                }
            }

            val distinctQueue = parsedQueueItems.distinctBy { it.songId }

            return ISharePlaybackState(
                songId = json.optString("songId"),
                title = json.optString("title"),
                artists = cleanArtists,
                thumbnailUrl = cleanThumb,
                positionMs = json.optLong("positionMs", 0L),
                isPlaying = json.optBoolean("isPlaying", false),
                durationMs = parsedDuration,
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                settings = parsedSettings ?: IShareSettings(),
                actionCommand = parsedCommand,
                actionPayload = parsedPayload,
                actionId = parsedActionId,
                queue = distinctQueue,
                totalQueueSize = if (parsedQueueSize > 0) parsedQueueSize else distinctQueue.size,
            )
        }
    }
}

@Immutable
data class IShareSession(
    val code: String,
    val participantId: String,
    val joinUrl: String,
    val participants: Int,
    val participantList: List<IShareParticipant>,
    val hostName: String,
    val controllerId: String,
    val controllerName: String,
    val stateVersion: Long,
    val serverNow: Long,
    val state: ISharePlaybackState?,
    val settings: IShareSettings = IShareSettings(),
    val queue: List<IShareQueueItem> = emptyList(),
) {
    fun toJson(): JSONObject {
        val queueArr = JSONArray()
        queue.forEach { queueArr.put(it.toJson()) }

        return JSONObject()
            .put("code", code)
            .put("participantId", participantId)
            .put("joinUrl", joinUrl)
            .put("participants", participants)
            .put("participantList", JSONArray().apply { participantList.forEach { put(it.toJson()) } })
            .put("hostName", hostName)
            .put("controllerId", controllerId)
            .put("controllerName", controllerName)
            .put("stateVersion", stateVersion)
            .put("serverNow", serverNow)
            .put("state", state?.toJson(forOnlineWorker = false))
            .put("settings", settings.toJson())
            .put("queue", queueArr)
    }

    companion object {
        fun fromJson(json: JSONObject): IShareSession {
            val parsedState = json.optJSONObject("state")?.let(ISharePlaybackState::fromJson)
            val parsedSettings =
                parsedState?.settings
                    ?: json.optJSONObject("settings")?.let(IShareSettings::fromJson)
                    ?: json.optJSONObject("state")?.optJSONObject("settings")?.let(IShareSettings::fromJson)
                    ?: IShareSettings()

            val parsedQueue = parsedState?.queue?.takeIf { it.isNotEmpty() }
                ?: json.optJSONArray("queue")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let(IShareQueueItem::fromJson)
                    }
                }.orEmpty()

            return IShareSession(
                code = json.optString("code"),
                participantId = json.optString("participantId"),
                joinUrl = json.optString("joinUrl"),
                participants = json.optInt("participants", 1),
                participantList = json.optJSONArray("participantList")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let(IShareParticipant::fromJson)
                    }
                }.orEmpty(),
                hostName = json.optString("hostName").ifBlank { "iShare Host" },
                controllerId = json.optString("controllerId"),
                controllerName = json.optString("controllerName").ifBlank { "iShare Host" },
                stateVersion = json.optLong("stateVersion", 0L),
                serverNow = json.optLong("serverNow", System.currentTimeMillis()),
                state = parsedState,
                settings = parsedSettings,
                queue = parsedQueue,
            )
        }
    }
}
