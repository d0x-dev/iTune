package com.darkxvenom.itune.data.artist

import android.util.Log
import com.darkxvenom.itune.backend.Net
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

object ArtistInnertubeClient {
    private const val TAG = "ArtistInnertube"
    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val WEB_REMIX_VERSION = "1.20250101.01.00"
    private const val WEB_REMIX_CLIENT_ID = "67"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var visitorData: String? = null

    val currentLocaleTag: String
        get() = LocaleTags.normalizeAppTag(Locale.getDefault().toLanguageTag().ifEmpty { "en" })

    val currentLanguage: String
        get() = LocaleTags.ytHl(currentLocaleTag)

    val currentRegion: String
        get() = LocaleTags.ytGl(currentLocaleTag)

    private val searchRegion: String?
        get() = LocaleTags.ytSearchGl(currentLocaleTag)

    private val acceptLanguageHeader: String
        get() = LocaleTags.acceptLanguage(currentLocaleTag)

    suspend fun browse(browseId: String, params: String? = null): JSONObject =
        postMusic("browse") {
            put("browseId", browseId)
            if (params != null) put("params", params)
        }

    suspend fun browseContinuation(token: String): JSONObject =
        postMusic(
            endpoint = "browse",
            query = mapOf("ctoken" to token, "continuation" to token, "type" to "next"),
        ) {
            put("continuation", token)
        }

    suspend fun next(videoId: String): JSONObject =
        postMusic("next") {
            put("videoId", videoId)
            put("playlistId", "RDAMVM$videoId")
            put("isAudioOnly", true)
        }

    suspend fun nextPlaylist(
        playlistId: String,
        videoId: String? = null,
        params: String? = null,
    ): JSONObject = postMusic("next") {
        if (videoId != null) put("videoId", videoId)
        put("playlistId", playlistId)
        if (params != null) put("params", params)
        put("isAudioOnly", true)
    }

    suspend fun search(query: String, params: String? = null): JSONObject =
        postMusic("search", region = searchRegion) {
            put("query", query)
            if (params != null) put("params", params)
        }

    private suspend fun <T> withRetry(attempts: Int = 3, block: suspend () -> T): T {
        var backoff = 500L
        repeat(attempts - 1) {
            try {
                return block()
            } catch (e: IOException) {
                Log.d(TAG, "retrying request: ${e.message}")
            }
            delay(backoff)
            backoff *= 2
        }
        return block()
    }

    private suspend fun postMusic(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
        region: String? = currentRegion,
        bodyExtras: JSONObject.() -> Unit,
    ): JSONObject {
        val urlBuilder = "$MUSIC_BASE/$endpoint".toHttpUrl().newBuilder()
            .addQueryParameter("prettyPrint", "false")
            .addQueryParameter("hl", currentLanguage)
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val url = urlBuilder.build().toString()

        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", currentLanguage)
                    if (region != null) put("gl", region)
                    visitorData?.let { put("visitorData", it) }
                })
                put("user", JSONObject().apply {
                    put("lockedSafetyMode", false)
                })
                put("request", JSONObject().apply {
                    put("useSsl", true)
                })
            })
            bodyExtras()
        }.toString()

        val responseObj = withRetry {
            val builder = Request.Builder()
                .url(url)
                .header("Accept-Language", acceptLanguageHeader)
                .header("X-Origin", MUSIC_ORIGIN)
                .header("Origin", MUSIC_ORIGIN)
                .header("Referer", "$MUSIC_ORIGIN/")
                .header("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
                .header("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            visitorData?.let { builder.header("X-Goog-Visitor-Id", it) }
            val request = builder.post(payload.toRequestBody(JSON_MEDIA_TYPE)).build()
            Net.client.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    throw IOException("Innertube $endpoint HTTP ${res.code}")
                }
                JSONObject(text)
            }
        }

        if (visitorData == null) {
            val v = responseObj.optJSONObject("responseContext")?.optString("visitorData")
            if (!v.isNullOrBlank()) visitorData = v
        }
        return responseObj
    }
}
