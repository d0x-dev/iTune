package com.darkxvenom.itune.backend

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles bot check fallback for NewPipeExtractor requests by substituting VISIONOS answers
 * when ANDROID reels are refused.
 */
object PlayerFallback {
    private const val CLIENT_NAME = "VISIONOS"
    private const val CLIENT_ID = "101"
    private const val CLIENT_VERSION = "1.02"
    private const val USER_AGENT = "com.google.ios.youtube/1.02 (RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X)"

    private fun isReel(url: String) = "/youtubei/v1/reel/reel_item_watch" in url
    private fun isVisionOs(url: String) = "youtubei.googleapis.com/youtubei/v1/player" in url

    fun handles(url: String) = isReel(url) || isVisionOs(url)

    fun substitute(url: String, requestBody: ByteArray?, responseBody: String?): String? =
        if (isReel(url)) substituteReel(requestBody, responseBody) else responseBody

    private fun substituteReel(requestBody: ByteArray?, responseBody: String?): String? {
        val reelStatus = runCatching { JSONObject(responseBody.orEmpty()) }.getOrNull()
            ?.optJSONObject("playerResponse")?.optJSONObject("playabilityStatus")
        if (requestBody == null || responseBody == null || !isBotCheck(reelStatus)) return responseBody
        val request = runCatching { JSONObject(String(requestBody)) }.getOrNull() ?: return responseBody
        val player = request.optJSONObject("playerRequest") ?: return responseBody
        val videoId = player.optString("videoId").ifEmpty { return responseBody }
        val answer = runCatching { ask(player, request.optJSONObject("context")?.optJSONObject("client")) }
            .onFailure { Log.w("PlayerFallback", "bot check on ANDROID for $videoId; VISIONOS request failed", it) }
            .getOrNull() ?: return responseBody
        val status = answer.optJSONObject("playabilityStatus")?.optString("status")
        if (status != "OK") {
            Log.w("PlayerFallback", "bot check on ANDROID for $videoId; VISIONOS said $status too")
            return responseBody
        }
        Log.i("PlayerFallback", "bot check on ANDROID for $videoId; details from VISIONOS instead")
        answer.optJSONObject("streamingData")?.let { data ->
            listOf("formats", "adaptiveFormats").forEach { key -> data.optJSONArray(key)?.let(::dropUrls) }
        }
        return JSONObject().put("playerResponse", answer).toString()
    }

    private fun isBotCheck(status: JSONObject?): Boolean =
        status != null && status.optString("status").equals("LOGIN_REQUIRED", ignoreCase = true) &&
            "a bot" in status.optString("reason")

    private fun dropUrls(formats: JSONArray) {
        for (i in 0 until formats.length()) {
            formats.optJSONObject(i)?.run { remove("url"); remove("signatureCipher"); remove("cipher") }
        }
    }

    private fun ask(player: JSONObject, original: JSONObject?): JSONObject {
        val visitorData = original?.optString("visitorData").orEmpty()
        val client = JSONObject()
            .put("clientName", CLIENT_NAME)
            .put("clientVersion", CLIENT_VERSION)
            .put("clientScreen", "WATCH")
            .put("platform", "MOBILE")
            .put("deviceMake", "Apple")
            .put("deviceModel", "RealityDevice14,1")
            .put("osName", "visionOS")
            .put("osVersion", "25.6.0.23O471")
            .put("hl", original?.optString("hl")?.ifEmpty { null } ?: "en")
            .put("gl", original?.optString("gl")?.ifEmpty { null } ?: "US")
            .apply { if (visitorData.isNotEmpty()) put("visitorData", visitorData) }
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("videoId", player.getString("videoId"))
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val request = okhttp3.Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .header("X-Goog-Api-Format-Version", "2")
            .header("X-YouTube-Client-Name", CLIENT_ID)
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .apply { if (visitorData.isNotEmpty()) header("X-Goog-Visitor-Id", visitorData) }
            .build()
        Net.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return JSONObject(response.body!!.string())
        }
    }
}
