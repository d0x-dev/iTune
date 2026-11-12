package com.darkxvenom.itune.data.artist

import android.util.Log
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.YouTubeAccountParsers
import com.darkxvenom.itune.data.auth.buildFullSapisidAuthorization
import com.darkxvenom.itune.data.auth.delegatedSessionId
import com.darkxvenom.itune.data.auth.buildSapisidHash
import com.darkxvenom.itune.data.auth.normalizeDataSyncId
import com.darkxvenom.itune.data.auth.sapisidFrom
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.model.LikeStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

object ArtistInnertubeClient {
    private const val TAG = "ArtistInnertube"
    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val WEB_REMIX_VERSION = "1.20261104.00.00"
    private const val WEB_VERSION = "2.20261104.00.00"
    private const val WEB_REMIX_CLIENT_ID = "67"
    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private const val DEFAULT_ATR_SECONDS = 5L
    private const val CPN_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val CONFIG_LOGGED_IN = Regex(""""LOGGED_IN"\s*:\s*(true|false)""")
    private val CONFIG_DATASYNC_ID = Regex(""""DATASYNC_ID"\s*:\s*"([^"]+)"""")
    private val CONFIG_PAGE_ID = Regex(""""DELEGATED_SESSION_ID"\s*:\s*"([^"]+)"""")
    private val CONFIG_SESSION_INDEX = Regex(""""SESSION_INDEX"\s*:\s*"?(\d+)""")
    private val CONFIG_VISITOR_DATA = Regex(""""VISITOR_DATA"\s*:\s*"([^"]+)"""")
    private val CONFIG_CLIENT_VERSION = Regex(""""INNERTUBE_CLIENT_VERSION"\s*:\s*"([^"]+)"""")

    private data class SessionScope(
        val dataSyncId: String?,
        val pageId: String?,
        val authUser: String,
        val clientVersion: String?,
    )

    private class HttpStatusException(
        val code: Int,
        val endpoint: String,
        val responseSnippet: String,
    ) : IOException("Innertube $endpoint HTTP $code${responseSnippet.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")

    private data class ChannelSelection(
        val pageId: String?,
        val dataSyncId: String?,
        val authUser: String?,
    )

    data class PlaybackTracking(
        val playbackUrl: String,
        val watchtimeUrl: String?,
        val atrUrl: String?,
        val atrAfterSeconds: Long,
    )

    @Volatile
    private var visitorData: String? = null

    @Volatile
    var cookie: String? = null
        private set

    @Volatile
    private var scope: SessionScope? = null

    @Volatile
    private var channelOverride: ChannelSelection? = null

    @Volatile
    private var cachedSignatureTimestamp: Int? = null

    private val scopeLock = Mutex()

    val currentLocaleTag: String
        get() {
            val lang = SettingsStore.state.value.contentLanguage
            return if (lang == ContentLocaleCatalog.SYSTEM_DEFAULT) {
                LocaleTags.normalizeAppTag(Locale.getDefault().toLanguageTag().ifEmpty { "en" })
            } else {
                LocaleTags.normalizeAppTag(lang)
            }
        }

    val currentLanguage: String
        get() = SettingsStore.effectiveHl()

    val currentRegion: String
        get() = SettingsStore.effectiveGl()

    private val searchRegion: String?
        get() = currentRegion

    private val acceptLanguageHeader: String
        get() = LocaleTags.acceptLanguage(currentLocaleTag)

    suspend fun browse(
        browseId: String,
        params: String? = null,
        anonymous: Boolean = false,
    ): JSONObject = postMusic(
        endpoint = "browse",
        anonymous = anonymous,
        allowAnonymousFallback = !isPrivateLibraryBrowseId(browseId),
    ) {
        put("browseId", browseId)
        if (params != null) put("params", params)
    }

    suspend fun browseContinuation(
        token: String,
        anonymous: Boolean = false,
        allowAnonymousFallback: Boolean = true,
    ): JSONObject = postMusic(
        endpoint = "browse",
        query = mapOf("ctoken" to token, "continuation" to token, "type" to "next"),
        anonymous = anonymous,
        allowAnonymousFallback = allowAnonymousFallback,
    ) {
        put("continuation", token)
    }

    /** Calls youtube.com WEB client `/browse` for account-wide fallbacks (`FEhistory`, `FEchannels`). */
    suspend fun postWebBrowse(browseId: String): JSONObject = postMusicRawUrl(
        url = "https://www.youtube.com/youtubei/v1/browse",
        clientName = "WEB",
        clientVersion = WEB_VERSION,
        clientId = "1",
        origin = "https://www.youtube.com",
        anonymous = false,
        allowAnonymousFallback = false,
    ) {
        put("browseId", browseId)
    }

    suspend fun next(videoId: String): JSONObject = postMusic(
        endpoint = "next",
        allowAnonymousFallback = true,
    ) {
        put("videoId", videoId)
        put("playlistId", "RDAMVM$videoId")
        put("isAudioOnly", true)
    }

    suspend fun nextPlaylist(
        playlistId: String,
        videoId: String? = null,
        params: String? = null,
    ): JSONObject = postMusic(
        endpoint = "next",
        allowAnonymousFallback = true,
    ) {
        if (videoId != null) put("videoId", videoId)
        put("playlistId", playlistId)
        if (params != null) put("params", params)
        put("isAudioOnly", true)
    }

    suspend fun transcript(videoId: String): Result<String> = runCatching {
        val cleanId = videoId.trim()
        if (cleanId.isBlank()) throw IllegalArgumentException("Blank videoId")
        val protoBytes = byteArrayOf(0x0a, cleanId.length.toByte()) + cleanId.toByteArray(StandardCharsets.UTF_8)
        val paramsBase64 = android.util.Base64.encodeToString(protoBytes, android.util.Base64.NO_WRAP)
        val json = postMusicRawUrl(
            url = "https://www.youtube.com/youtubei/v1/get_transcript",
            clientName = "WEB",
            clientVersion = WEB_VERSION,
            clientId = "1",
            origin = "https://www.youtube.com",
            anonymous = true,
            allowAnonymousFallback = true,
        ) {
            put("params", paramsBase64)
        }
        parseTranscriptJson(json) ?: throw IllegalStateException("No transcript returned for $videoId")
    }

    private fun parseTranscriptJson(json: JSONObject): String? {
        val actions = json.optJSONArray("actions") ?: return null
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i) ?: continue
            val renderer = action.optJSONObject("updateEngagementPanelAction")
                ?.optJSONObject("content")
                ?.optJSONObject("transcriptRenderer")
                ?.optJSONObject("body")
                ?.optJSONObject("transcriptBodyRenderer") ?: continue
            val cueGroups = renderer.optJSONArray("cueGroups") ?: continue
            val lines = mutableListOf<String>()
            for (j in 0 until cueGroups.length()) {
                val group = cueGroups.optJSONObject(j) ?: continue
                val cue = group.optJSONObject("transcriptCueGroupRenderer")
                    ?.optJSONArray("cues")?.optJSONObject(0)
                    ?.optJSONObject("transcriptCueRenderer") ?: continue
                val timeMs = cue.optLong("startOffsetMs", 0L)
                val text = cue.optJSONObject("cue")?.optString("simpleText", "")
                    ?.trim('♪', ' ', '\n', '\r').orEmpty()
                if (text.isNotBlank()) {
                    val mins = timeMs / 60000L
                    val secs = (timeMs / 1000L) % 60L
                    val ms = timeMs % 1000L
                    lines.add("[%02d:%02d.%03d] %s".format(mins, secs, ms, text))
                }
            }
            if (lines.isNotEmpty()) {
                return lines.joinToString("\n")
            }
        }
        return null
    }

    suspend fun lyrics(videoId: String): Result<String> = runCatching {
        val nextJson = next(videoId)
        val tabs = nextJson.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs") ?: throw IllegalStateException("No tabs in next response")

        var browseId: String? = null
        var params: String? = null
        for (i in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
            val ep = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint") ?: continue
            val bId = ep.optString("browseId", "")
            if (bId.startsWith("MPLY") || tab.optString("title", "").contains("Lyrics", ignoreCase = true)) {
                browseId = bId
                params = ep.optString("params", "").takeIf { it.isNotBlank() }
                break
            }
        }

        val targetBrowseId = browseId ?: throw IllegalStateException("No lyrics tab found for $videoId")
        val browseJson = browse(targetBrowseId, params = params, anonymous = true)
        val runs = browseJson.optJSONObject("contents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")?.optJSONObject(0)
            ?.optJSONObject("musicDescriptionShelfRenderer")
            ?.optJSONObject("description")
            ?.optJSONArray("runs") ?: throw IllegalStateException("No lyrics runs found in browse response")

        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val t = run.optString("text", "")
            if (t.isNotEmpty()) sb.append(t)
        }
        val text = sb.toString().trim()
        if (text.isBlank()) throw IllegalStateException("Empty lyrics content")
        text
    }

    suspend fun search(
        query: String,
        params: String? = null,
        anonymous: Boolean = true,
    ): JSONObject = postMusic(
        endpoint = "search",
        region = searchRegion,
        anonymous = anonymous,
        allowAnonymousFallback = true,
    ) {
        put("query", query)
        if (params != null) put("params", params)
    }

    fun selectChannel(pageId: String?, dataSyncId: String?, authUser: String? = null) {
        channelOverride = if (pageId == null && dataSyncId == null) {
            null
        } else {
            ChannelSelection(pageId, dataSyncId, authUser)
        }
    }

    fun hasSession(): Boolean = !cookie.isNullOrBlank()

    fun activePageId(): String? = pageIdFor(scope)

    fun activeDataSyncId(): String? = dataSyncIdFor(scope)

    internal fun updateCookie(newCookie: String) {
        if (newCookie.isNotBlank()) cookie = newCookie
    }

    private fun clearInvalidDelegationScope(clearUserSessionId: Boolean = true) {
        val retainedUserSessionId = if (clearUserSessionId) {
            null
        } else {
            channelOverride?.dataSyncId ?: scope?.dataSyncId
        }
        channelOverride = channelOverride?.copy(
            pageId = null,
            dataSyncId = if (clearUserSessionId) null else channelOverride?.dataSyncId,
        )
        scope = scope?.copy(
            pageId = null,
            dataSyncId = if (clearUserSessionId) null else scope?.dataSyncId,
        )
        GoogleAuthStore.updateActiveScope(pageId = null, dataSyncId = retainedUserSessionId)
    }

    private fun isPrivateLibraryBrowseId(browseId: String): Boolean =
        browseId.startsWith("FEmusic_liked") ||
            browseId.startsWith("FEmusic_library") ||
            browseId == "FEmusic_history" ||
            browseId == "FEhistory" ||
            browseId == "FEchannels" ||
            browseId == "VLLM" ||
            browseId == "LM"

    fun adoptSessionScope(
        cookie: String,
        pageId: String?,
        dataSyncId: String?,
        authUser: String?,
        visitorData: String?,
        clientVersion: String?,
        loggedIn: Boolean,
    ) {
        this.cookie = cookie.takeIf { it.isNotBlank() }
        this.channelOverride = null
        val version = clientVersion?.takeIf { it.isNotBlank() } ?: scope?.clientVersion
        scope = if (!loggedIn) {
            version?.let { SessionScope(null, null, "0", it) }
        } else {
            SessionScope(
                dataSyncId = dataSyncId?.takeIf { it.isNotBlank() },
                pageId = pageId?.takeIf { it.isNotBlank() },
                authUser = authUser?.takeIf { it.isNotBlank() } ?: "0",
                clientVersion = version,
            )
        }
        this.visitorData = visitorData?.takeIf { it.isNotBlank() }
    }

    fun clearAuthSession() {
        cookie = null
        scope = null
        channelOverride = null
        visitorData = null
    }

    suspend fun accountMenu(): JSONObject = postMusic("account/account_menu") {}

    suspend fun accountsList(): JSONObject = postMusic("account/accounts_list") {}

    suspend fun accountSwitcher(): JSONObject = postMusicRawUrl(
        url = "https://www.youtube.com/getAccountSwitcherEndpoint",
        clientName = "WEB",
        clientVersion = WEB_VERSION,
        clientId = "1",
        origin = "https://www.youtube.com",
    ) {}

    /**
     * Confirms that this cookie session can read private YouTube Music library data.
     * Account-menu metadata is optional; this probe is the actual permission check
     * required before the app advertises bidirectional library sync.
     */
    suspend fun verifyAuthenticatedLibraryAccess() {
        requireSession()
        val failures = mutableListOf<String>()
        for (browseId in listOf("FEmusic_liked_playlists", "FEmusic_liked_videos")) {
            try {
                browse(browseId, anonymous = false)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failures += "$browseId: ${e.message?.take(160) ?: e.javaClass.simpleName}"
            }
        }
        throw IOException(
            "Google sign-in succeeded, but YouTube Music did not authorize private library access. " +
                failures.joinToString("; "),
        )
    }

    suspend fun rateVideo(videoId: String, status: LikeStatus) {
        requireSession()
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.INDIFFERENT -> "like/removelike"
        }
        val response = postMusicWithWebFallback(endpoint) {
            put("target", JSONObject().put("videoId", videoId))
        }
        checkInnertubeError(response, "YouTube Music refused rating")
    }

    suspend fun ratePlaylist(playlistId: String, saved: Boolean) {
        requireSession()
        val endpoint = if (saved) "like/like" else "like/removelike"
        val response = postMusicWithWebFallback(endpoint) {
            put("target", JSONObject().put("playlistId", playlistId.removePrefix("VL")))
        }
        checkInnertubeError(response, "YouTube Music refused playlist save")
    }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) {
        requireSession()
        val endpoint = if (subscribed) "subscription/subscribe" else "subscription/unsubscribe"
        val response = postMusicWithWebFallback(endpoint) {
            put("channelIds", JSONArray().put(channelId))
        }
        checkInnertubeError(response, "YouTube Music refused subscription change")
    }

    suspend fun createPlaylist(
        title: String,
        privacyStatus: String = "PRIVATE",
        description: String? = null,
        videoIds: List<String> = emptyList(),
    ): String {
        requireSession()
        val response = postMusicWithWebFallback("playlist/create") {
            put("title", title)
            if (!description.isNullOrBlank()) put("description", description)
            put("privacyStatus", privacyStatus)
            if (videoIds.isNotEmpty()) {
                put("videoIds", JSONArray().apply { videoIds.forEach(::put) })
            }
        }
        checkInnertubeError(response, "YouTube Music refused playlist creation")
        return response.optString("playlistId").takeIf { it.isNotBlank() }
            ?: YouTubeAccountParsers.findStringDeep(response, "playlistId")
            ?: error("Playlist created on YouTube Music but no playlistId returned")
    }

    suspend fun deletePlaylist(playlistId: String) {
        requireSession()
        val response = postMusicWithWebFallback("playlist/delete") {
            put("playlistId", playlistId.removePrefix("VL"))
        }
        checkInnertubeError(response, "YouTube Music refused playlist deletion")
    }

    private suspend fun postMusicWithWebFallback(
        endpoint: String,
        bodyExtras: JSONObject.() -> Unit,
    ): JSONObject {
        val primaryError = try {
            return postMusic(endpoint, bodyExtras = bodyExtras)
        } catch (e: HttpStatusException) {
            Log.w(TAG, "WEB_REMIX $endpoint failed (${e.code}), retrying on WEB: ${e.responseSnippet}")
            e
        }
        return try {
            postMusicRawUrl(
                url = "https://www.youtube.com/youtubei/v1/$endpoint",
                clientName = "WEB",
                clientVersion = WEB_VERSION,
                clientId = "1",
                origin = "https://www.youtube.com",
                anonymous = false,
                allowAnonymousFallback = false,
                bodyExtras = bodyExtras,
            )
        } catch (webErr: Throwable) {
            Log.w(TAG, "WEB fallback for $endpoint also failed: ${webErr.message}")
            throw primaryError
        }
    }

    suspend fun renamePlaylist(playlistId: String, title: String) {
        editPlaylist(playlistId) {
            put(
                JSONObject()
                    .put("action", "ACTION_SET_PLAYLIST_NAME")
                    .put("playlistName", title),
            )
        }
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Map<String, String> {
        if (videoIds.isEmpty()) return emptyMap()
        val response = editPlaylist(playlistId) {
            videoIds.forEach { vid ->
                put(
                    JSONObject()
                        .put("action", "ACTION_ADD_VIDEO")
                        .put("addedVideoId", vid),
                )
            }
        }
        val arr = response.optJSONArray("playlistEditResults")
            ?: error("YouTube Music did not confirm the playlist add operation")
        val out = linkedMapOf<String, String>()
        for (i in 0 until arr.length()) {
            val added = arr.optJSONObject(i)?.optJSONObject("playlistEditVideoAddedResultData") ?: continue
            val vid = added.optString("videoId").takeIf { it.isNotBlank() } ?: continue
            val setId = added.optString("setVideoId").takeIf { it.isNotBlank() } ?: continue
            out[vid] = setId
        }
        return out
    }

    suspend fun removeFromPlaylist(playlistId: String, entries: List<Pair<String, String>>) {
        if (entries.isEmpty()) return
        editPlaylist(playlistId) {
            entries.forEach { (setVideoId, videoId) ->
                put(
                    JSONObject()
                        .put("action", "ACTION_REMOVE_VIDEO")
                        .put("setVideoId", setVideoId)
                        .put("removedVideoId", videoId),
                )
            }
        }
    }

    suspend fun signatureTimestamp(videoId: String): Int? = withContext(Dispatchers.IO) {
        cachedSignatureTimestamp?.let { return@withContext it }
        runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }
            .onFailure { Log.w(TAG, "signatureTimestamp lookup failed: ${it.message}") }
            .getOrNull()
            ?.also { cachedSignatureTimestamp = it }
    }

    suspend fun playbackTracking(videoId: String, signatureTimestamp: Int?): PlaybackTracking? {
        if (cookie == null) return null
        ensureSessionScope()
        val response = postMusic("player") {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put(
                "playbackContext",
                JSONObject().put(
                    "contentPlaybackContext",
                    JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("referer", "$MUSIC_ORIGIN/watch?v=$videoId")
                        if (signatureTimestamp != null) put("signatureTimestamp", signatureTimestamp)
                    },
                ),
            )
        }
        val tracking = response.optJSONObject("playbackTracking") ?: return null
        val playbackUrl = tracking.optJSONObject("videostatsPlaybackUrl")
            ?.optString("baseUrl")?.takeIf { it.isNotBlank() } ?: return null
        val watchtimeUrl = tracking.optJSONObject("videostatsWatchtimeUrl")
            ?.optString("baseUrl")?.takeIf { it.isNotBlank() }
        val atrObj = tracking.optJSONObject("atrUrl")
        val atrUrl = atrObj?.optString("baseUrl")?.takeIf { it.isNotBlank() }
        val atrAfter = atrObj?.optLong("elapsedMediaTimeSeconds", DEFAULT_ATR_SECONDS)
            ?.takeIf { it > 0L } ?: DEFAULT_ATR_SECONDS
        return PlaybackTracking(
            playbackUrl = playbackUrl,
            watchtimeUrl = watchtimeUrl,
            atrUrl = atrUrl,
            atrAfterSeconds = atrAfter,
        )
    }

    suspend fun pingPlayback(baseUrl: String, cpn: String): Int =
        pingStats(baseUrl, cpn) {}

    suspend fun pingWatchtime(baseUrl: String, cpn: String, seconds: Long, final: Boolean = false): Int =
        pingStats(baseUrl, cpn) {
            addQueryParameter("st", "0")
            addQueryParameter("et", seconds.toString())
            addQueryParameter("cmt", seconds.toString())
            addQueryParameter("state", if (final) "paused" else "playing")
            if (final) addQueryParameter("final", "1")
        }

    suspend fun pingAtr(baseUrl: String, cpn: String): Int = withContext(Dispatchers.IO) {
        if (cookie != null) ensureSessionScope()
        val session = scope
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("cpn", cpn)
            .build()
        val builder = Request.Builder().url(url).get()
        applyStatsHeaders(builder, session)
        Net.client.newCall(builder.build()).execute().use { it.code }
    }

    fun newCpn(): String = (1..16).map { CPN_ALPHABET.random() }.joinToString("")

    fun applyAuthHeaders(
        builder: Request.Builder,
        origin: String = MUSIC_ORIGIN,
    ) {
        val session = scope
        builder.header("User-Agent", WEB_USER_AGENT)
        visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }
        cookie?.let { c ->
            builder.header("Cookie", c)
            builder.header("X-Youtube-Bootstrap-Logged-In", "true")
            builder.header("X-Goog-AuthUser", authUserFor(session))
            pageIdFor(session)?.let { builder.header("X-Goog-PageId", it) }
            buildFullSapisidAuthorization(
                cookieHeader = c,
                origin = origin,
                userSessionId = dataSyncIdFor(session),
            )?.let { auth ->
                builder.header("Authorization", auth)
            }
        }
    }

    fun buildUserContextObject(): JSONObject = JSONObject().apply {
        put("lockedSafetyMode", false)
        dataSyncIdFor(scope)?.let { put("onBehalfOfUser", it) }
    }

    fun currentClientVersion(): String = scope?.clientVersion ?: WEB_REMIX_VERSION

    private suspend fun <T> withRetry(attempts: Int = 3, block: suspend () -> T): T {
        var backoff = 500L
        repeat(attempts - 1) {
            try {
                return block()
            } catch (e: HttpStatusException) {
                // Only retry genuine gateway/rate-limit codes; let 400/401/403/500 hit resilient fallback immediately.
                if (e.code !in setOf(429, 502, 503, 504)) throw e
                Log.d(TAG, "retrying transient HTTP ${e.code}: ${e.endpoint}")
            } catch (e: IOException) {
                Log.d(TAG, "retrying request: ${e.message}")
            }
            delay(backoff)
            backoff *= 2
        }
        return block()
    }

    private suspend fun editPlaylist(
        playlistId: String,
        actions: JSONArray.() -> Unit,
    ): JSONObject {
        requireSession()
        val response = postMusicWithWebFallback("browse/edit_playlist") {
            put("playlistId", playlistId.removePrefix("VL"))
            put("actions", JSONArray().apply(actions))
        }
        val status = response.optString("status")
        if (status.isNotBlank() && status != "STATUS_SUCCEEDED") {
            error("YouTube Music refused edit ($status)")
        }
        checkInnertubeError(response, "YouTube Music refused edit")
        return response
    }

    private fun checkInnertubeError(response: JSONObject, fallbackMessage: String) {
        val err = response.optJSONObject("error") ?: return
        val msg = err.optString("message").takeIf { it.isNotBlank() } ?: fallbackMessage
        error(msg)
    }

    private fun requireSession() {
        if (cookie.isNullOrBlank()) error("Sign in with Google to sync with YouTube Music")
    }

    private suspend fun ensureSessionScope() {
        val activeCookie = cookie ?: return
        val existing = scope
        if (existing != null && !existing.clientVersion.isNullOrBlank()) return
        scopeLock.withLock {
            val lockedExisting = scope
            if ((lockedExisting != null && !lockedExisting.clientVersion.isNullOrBlank()) || cookie != activeCookie) return
            runCatching { fetchSessionScope(activeCookie) }
                .onFailure { Log.w(TAG, "could not read session scope: ${it.message}") }
                .getOrNull()
                ?.let { fresh ->
                    if (cookie != activeCookie) return@let
                    // Refresh both session identifiers from the live ytcfg. Older app
                    // versions stored DELEGATED_SESSION_ID as the user session id.
                    val refreshed = if (lockedExisting != null) {
                        lockedExisting.copy(
                            dataSyncId = fresh.dataSyncId,
                            pageId = fresh.pageId,
                            authUser = fresh.authUser,
                            clientVersion = fresh.clientVersion ?: lockedExisting.clientVersion,
                        )
                    } else {
                        fresh
                    }
                    scope = refreshed
                    GoogleAuthStore.updateActiveScope(
                        pageId = refreshed.pageId,
                        dataSyncId = refreshed.dataSyncId,
                    )
                }
        }
    }

    private suspend fun fetchSessionScope(sessionCookie: String): SessionScope? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("$MUSIC_ORIGIN/")
            .get()
            .header("User-Agent", WEB_USER_AGENT)
            .header("Accept-Language", acceptLanguageHeader)
            .header("Cookie", sessionCookie)
            .header("X-Youtube-Bootstrap-Logged-In", "true")
        val activeScope = scope
        builder.header("X-Goog-AuthUser", authUserFor(activeScope))
        pageIdFor(activeScope)?.let { builder.header("X-Goog-PageId", it) }
        buildFullSapisidAuthorization(
            cookieHeader = sessionCookie,
            origin = MUSIC_ORIGIN,
            userSessionId = dataSyncIdFor(activeScope),
        )?.let { builder.header("Authorization", it) }
        val html = Net.client.newCall(builder.build()).execute().use { res ->
            GoogleAuthStore.absorbSetCookies(res.headers("Set-Cookie"))
            res.body?.string().orEmpty()
        }
        val signedIn = CONFIG_LOGGED_IN.find(html)?.groupValues?.get(1) == "true"
        val clientVersion = CONFIG_CLIENT_VERSION.find(html)?.groupValues?.get(1)
        if (!signedIn) {
            return@withContext clientVersion?.let { SessionScope(null, null, "0", it) }
        }
        val rawDataSync = CONFIG_DATASYNC_ID.find(html)?.groupValues?.get(1)?.trim()
        val pageId = CONFIG_PAGE_ID.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: delegatedSessionId(rawDataSync)
        // DATASYNC_ID stores USER_SESSION_ID (or DELEGATED_SESSION_ID||USER_SESSION_ID).
        // DELEGATED_SESSION_ID is a separate header value and must never replace it.
        val dataSyncId = normalizeDataSyncId(rawDataSync)
        val authUser = CONFIG_SESSION_INDEX.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() } ?: "0"
        CONFIG_VISITOR_DATA.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }?.let {
            visitorData = it
        }
        SessionScope(dataSyncId, pageId, authUser, clientVersion)
    }

    private suspend fun pingStats(
        baseUrl: String,
        cpn: String,
        extras: okhttp3.HttpUrl.Builder.() -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        if (cookie != null) ensureSessionScope()
        val session = scope
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("ver", "2")
            .addQueryParameter("c", "WEB_REMIX")
            .addQueryParameter("cver", currentClientVersion())
            .addQueryParameter("cpn", cpn)
            .addQueryParameter("cplayer", "UNIPLAYER")
            .addQueryParameter("cbr", "Chrome")
            .addQueryParameter("cbrver", "141.0.0.0")
            .addQueryParameter("cos", "Windows")
            .addQueryParameter("cosver", "10.0")
            .addQueryParameter("hl", currentLanguage.replace('-', '_'))
            .addQueryParameter("cr", currentRegion)
            .apply(extras)
            .build()
        val builder = Request.Builder().url(url).get()
        applyStatsHeaders(builder, session)
        Net.client.newCall(builder.build()).execute().use { it.code }
    }

    private fun applyStatsHeaders(builder: Request.Builder, session: SessionScope?) {
        builder.header("X-Origin", MUSIC_ORIGIN)
            .header("Origin", MUSIC_ORIGIN)
            .header("Referer", "$MUSIC_ORIGIN/")
            .header("User-Agent", WEB_USER_AGENT)
        visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }
        cookie?.let { c ->
            builder.header("Cookie", c)
            builder.header("X-Goog-AuthUser", authUserFor(session))
            pageIdFor(session)?.let { builder.header("X-Goog-PageId", it) }
            buildFullSapisidAuthorization(
                cookieHeader = c,
                origin = MUSIC_ORIGIN,
                userSessionId = dataSyncIdFor(session),
            )?.let { builder.header("Authorization", it) }
        }
    }

    private fun pageIdFor(session: SessionScope?): String? {
        val override = channelOverride
        return if (override != null) override.pageId else session?.pageId
    }

    private fun dataSyncIdFor(session: SessionScope?): String? {
        val override = channelOverride
        return if (override != null) override.dataSyncId else session?.dataSyncId
    }

    private fun authUserFor(session: SessionScope?): String =
        channelOverride?.authUser ?: session?.authUser ?: "0"

    private suspend fun postMusic(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
        region: String? = currentRegion,
        anonymous: Boolean = false,
        allowAnonymousFallback: Boolean = false,
        bodyExtras: JSONObject.() -> Unit,
    ): JSONObject = postMusicRawUrl(
        url = "$MUSIC_BASE/$endpoint",
        query = query,
        region = region,
        anonymous = anonymous,
        allowAnonymousFallback = allowAnonymousFallback,
        bodyExtras = bodyExtras,
    )

    private suspend fun postMusicRawUrl(
        url: String,
        query: Map<String, String> = emptyMap(),
        region: String? = currentRegion,
        clientName: String = "WEB_REMIX",
        clientVersion: String = currentClientVersion(),
        clientId: String = WEB_REMIX_CLIENT_ID,
        origin: String = MUSIC_ORIGIN,
        anonymous: Boolean = false,
        allowAnonymousFallback: Boolean = false,
        bodyExtras: JSONObject.() -> Unit,
    ): JSONObject {
        if (!anonymous && cookie != null) ensureSessionScope()
        val session = scope
        val resolvedVersion = if (clientName == "WEB_REMIX") currentClientVersion() else clientVersion

        val urlBuilder = url.toHttpUrl().newBuilder()
            .addQueryParameter("prettyPrint", "false")
            .addQueryParameter("hl", currentLanguage)
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val finalUrl = urlBuilder.build().toString()

        fun buildPayload(includeVisitorData: Boolean = true): String = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", clientName)
                    put("clientVersion", resolvedVersion)
                    put("hl", currentLanguage)
                    if (region != null) put("gl", region)
                    if (includeVisitorData) visitorData?.let { put("visitorData", it) }
                })
                // Account/channel identity is carried by X-Goog-PageId and the
                // user-session-bound SAPISID authorization header.
                put("user", JSONObject().put("lockedSafetyMode", false))
                put("request", JSONObject().apply {
                    put("useSsl", true)
                })
            })
            bodyExtras()
        }.toString()

        val activeCookie = if (anonymous) null else cookie
        val activeDataSyncId = if (anonymous) null else dataSyncIdFor(session)
        val activePageId = if (anonymous) null else pageIdFor(session)
        val activeAuthUser = authUserFor(session)

        val responseObj = try {
            executeInnertubePost(
                finalUrl = finalUrl,
                payload = buildPayload(includeVisitorData = true),
                origin = origin,
                clientId = clientId,
                clientVersion = resolvedVersion,
                activeCookie = activeCookie,
                authUser = activeAuthUser,
                pageId = activePageId,
                userSessionId = activeDataSyncId,
            )
        } catch (e: HttpStatusException) {
            if (!anonymous && activeCookie != null && e.code in setOf(400, 401, 403, 500)) {
                Log.w(
                    TAG,
                    "Auth call rejected (${e.code}) on $finalUrl [obou=${activeDataSyncId != null}, page=${activePageId != null}, vd=${visitorData != null}]: ${e.responseSnippet}",
                )
                val withoutDelegation = if (activeDataSyncId != null || activePageId != null) {
                    runCatching {
                        executeInnertubePost(
                            finalUrl = finalUrl,
                            payload = buildPayload(includeVisitorData = true),
                            origin = origin,
                            clientId = clientId,
                            clientVersion = resolvedVersion,
                            activeCookie = activeCookie,
                            authUser = activeAuthUser,
                            pageId = null,
                            userSessionId = if (activePageId != null) activeDataSyncId else null,
                        )
                    }.onSuccess {
                        clearInvalidDelegationScope(clearUserSessionId = activePageId == null)
                    }.getOrNull()
                } else {
                    null
                }

                val minimalAuth = withoutDelegation ?: runCatching {
                    executeInnertubePost(
                        finalUrl = finalUrl,
                        payload = buildPayload(includeVisitorData = false),
                        origin = origin,
                        clientId = clientId,
                        clientVersion = resolvedVersion,
                        activeCookie = activeCookie,
                        authUser = null,
                        pageId = null,
                        userSessionId = null,
                        includeVisitorHeader = false,
                        singleHashOnly = true,
                    )
                }.onSuccess {
                    clearInvalidDelegationScope()
                    visitorData = null
                }.getOrNull()

                minimalAuth ?: if (allowAnonymousFallback) {
                    Log.w(TAG, "Falling back to anonymous request for $finalUrl")
                    executeInnertubePost(
                        finalUrl = finalUrl,
                        payload = buildPayload(includeVisitorData = false),
                        origin = origin,
                        clientId = clientId,
                        clientVersion = resolvedVersion,
                        activeCookie = null,
                        authUser = null,
                        pageId = null,
                        userSessionId = null,
                        includeVisitorHeader = false,
                    )
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }

        if (visitorData == null) {
            val v = responseObj.optJSONObject("responseContext")?.optString("visitorData")
            if (!v.isNullOrBlank()) visitorData = v
        }
        return responseObj
    }

    private suspend fun executeInnertubePost(
        finalUrl: String,
        payload: String,
        origin: String,
        clientId: String,
        clientVersion: String,
        activeCookie: String?,
        authUser: String?,
        pageId: String?,
        userSessionId: String?,
        includeVisitorHeader: Boolean = true,
        singleHashOnly: Boolean = false,
    ): JSONObject = withRetry {
        val builder = Request.Builder()
            .url(finalUrl)
            .header("Accept-Language", acceptLanguageHeader)
            .header("X-Origin", origin)
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .header("X-YouTube-Client-Name", clientId)
            .header("X-YouTube-Client-Version", clientVersion)
            .header("User-Agent", WEB_USER_AGENT)
        if (includeVisitorHeader) {
            visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }
        }
        if (!activeCookie.isNullOrBlank()) {
            builder.header("Cookie", activeCookie)
            builder.header("X-Youtube-Bootstrap-Logged-In", "true")
            authUser?.takeIf { it.isNotBlank() }?.let { builder.header("X-Goog-AuthUser", it) }
            pageId?.let { builder.header("X-Goog-PageId", it) }
            val authHeader = if (singleHashOnly) {
                sapisidFrom(activeCookie)?.let {
                    buildSapisidHash(it, origin, userSessionId = userSessionId)
                }
            } else {
                buildFullSapisidAuthorization(
                    cookieHeader = activeCookie,
                    origin = origin,
                    userSessionId = userSessionId,
                )
            }
            authHeader?.let { builder.header("Authorization", it) }
        }
        val request = builder.post(payload.toRequestBody(JSON_MEDIA_TYPE)).build()
        Net.client.newCall(request).execute().use { res ->
            GoogleAuthStore.absorbSetCookies(res.headers("Set-Cookie"))
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw HttpStatusException(res.code, finalUrl, text.take(240))
            }
            val json = JSONObject(text)
            // Innertube can encode failures in a JSON "error" object while returning
            // HTTP 200. Treat those as failures so auth fallback and sync reporting run.
            val apiError = json.optJSONObject("error")
            if (apiError != null) {
                val apiCode = apiError.optInt("code", 500).takeIf { it in 400..599 } ?: 500
                val message = apiError.optString("message").ifBlank { "YouTube API error" }
                throw HttpStatusException(apiCode, finalUrl, message.take(240))
            }
            json
        }
    }
}
