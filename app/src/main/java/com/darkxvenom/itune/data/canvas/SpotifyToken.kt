/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.data.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.darkxvenom.itune.backend.Net
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The bearer token behind Spotify's own web player, minted from the
 * listener's session cookie ([SettingsStore.state]'s `spotifySpdcToken`) rather
 * than an app credential — there is no public API for a track's Canvas, so this
 * walks the same door the web player itself uses to fetch one.
 *
 * Call [init] once at process start — the WebView harvest below needs a [Context]
 * and none of the suspend call chain that reaches [accessToken] has one to hand.
 */
object SpotifyToken {

    private const val TAG = "SpotifyToken"
    private const val BRIDGE_NAME = "ITuneSpotifyTokenBridge"
    private const val HARVEST_TIMEOUT_MS = 20_000L
    private const val DEFAULT_TOKEN_LIFETIME_MS = 3_600_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val harvestMutex = Mutex()

    @Volatile private var appContext: Context? = null

    @Volatile private var cachedAccessToken: String? = null
    @Volatile private var accessTokenExpiresAtMs = 0L
    @Volatile private var cachedClientId: String? = null

    @Volatile private var cachedSession: SessionInfo? = null
    @Volatile private var cachedClientToken: String? = null
    @Volatile private var clientTokenExpiresAtMs = 0L

    private data class SessionInfo(val clientVersion: String, val deviceId: String)
    private data class HarvestedToken(val token: String, val expiresAt: Long, val clientId: String?)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun invalidate() {
        cachedAccessToken = null
        accessTokenExpiresAtMs = 0L
        cachedClientId = null
        cachedClientToken = null
        clientTokenExpiresAtMs = 0L
    }

    /**
     * The current bearer token, or null when there is no cookie to mint one
     * from, [init] was never called, or the harvest failed. Cached until
     * shortly before it expires so a skip through a queue doesn't pay for
     * this per track.
     */
    suspend fun accessToken(): String? {
        val cookie = SettingsStore.state.value.spotifySpdcToken
        if (cookie.isBlank()) return null

        val now = System.currentTimeMillis()
        cachedAccessToken?.let { if (now < accessTokenExpiresAtMs - 30_000) return it }

        return harvestMutex.withLock {
            val stillNow = System.currentTimeMillis()
            cachedAccessToken?.let { if (stillNow < accessTokenExpiresAtMs - 30_000) return@withLock it }

            val context = appContext
            if (context == null) {
                Log.w(TAG, "SpotifyToken.init was never called; no context for the harvest")
                return@withLock null
            }

            val harvested = withContext(Dispatchers.Main) { harvestViaWebView(context, cookie) }
            if (harvested == null) {
                Log.w(TAG, "token harvest failed or timed out")
                return@withLock null
            }

            cachedAccessToken = harvested.token
            accessTokenExpiresAtMs = harvested.expiresAt
            harvested.clientId?.let { cachedClientId = it }
            Log.d(TAG, "harvested access token, good until ${java.util.Date(harvested.expiresAt)}")
            harvested.token
        }
    }

    /**
     * Loads open.spotify.com for real in an offscreen WebView with the cookie
     * applied, and reads the token the page mints for itself by hooking
     * fetch/XHR before its own bundle runs.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun harvestViaWebView(context: Context, cookie: String): HarvestedToken? {
        val deferred = CompletableDeferred<HarvestedToken?>()

        val cookieManager = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setCookie("https://open.spotify.com/", "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure")
            setCookie("https://accounts.spotify.com/", "sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure")
            flush()
        }
        // The player caches its token in web storage and skips a fresh
        // /api/token request if a live one is already sitting there, leaving
        // the hook with nothing to see — wipe storage so every harvest forces
        // a real mint.
        runCatching { WebStorage.getInstance().deleteAllData() }

        var webView: WebView? = null
        return try {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = CANVAS_UA
                cookieManager.setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(TokenBridge(deferred), BRIDGE_NAME)

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view.evaluateJavascript(HOOK_SCRIPT, null)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        // Re-assert in case the player navigated client-side
                        // after the onPageStarted injection ran.
                        view.evaluateJavascript(HOOK_SCRIPT, null)
                    }
                }
                loadUrl("https://open.spotify.com/")
            }

            withTimeoutOrNull(HARVEST_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            Log.w(TAG, "token harvest threw: ${e.message}")
            null
        } finally {
            runCatching {
                webView?.removeJavascriptInterface(BRIDGE_NAME)
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }

    /** Receives raw `/api/token` response bodies from the hooked page. */
    private class TokenBridge(private val deferred: CompletableDeferred<HarvestedToken?>) {
        @JavascriptInterface
        fun onTokenPayload(payload: String?) {
            if (payload.isNullOrBlank() || deferred.isCompleted) return
            runCatching {
                val root = json.parseToJsonElement(payload).jsonObject
                val token = root["accessToken"]?.jsonPrimitive?.contentOrNull
                val anonymous = root["isAnonymous"]?.jsonPrimitive?.contentOrNull
                    ?.toBooleanStrictOrNull() ?: false
                // The player also mints an anonymous token before the cookie
                // takes effect; that one can't read canvases, so keep waiting
                // for the logged-in one.
                if (token.isNullOrBlank() || anonymous) return
                val expiresAt = root["accessTokenExpirationTimestampMs"]?.jsonPrimitive?.contentOrNull
                    ?.toLongOrNull()?.takeIf { it > System.currentTimeMillis() }
                    ?: (System.currentTimeMillis() + DEFAULT_TOKEN_LIFETIME_MS)
                val clientId = root["clientId"]?.jsonPrimitive?.contentOrNull
                deferred.complete(HarvestedToken(token, expiresAt, clientId))
            }
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    private val HOOK_SCRIPT = """
        (function () {
          if (window.__ituneTokenHook) return;
          window.__ituneTokenHook = true;
          var report = function (body) {
            try { $BRIDGE_NAME.onTokenPayload(body); } catch (e) {}
          };
          var isToken = function (u) {
            try { return String(u).indexOf('/api/token') !== -1; } catch (e) { return false; }
          };
          var origFetch = window.fetch;
          if (origFetch) {
            window.fetch = function (input, init) {
              var url = (input && input.url) ? input.url : input;
              var result = origFetch.apply(this, arguments);
              if (isToken(url)) {
                try {
                  result.then(function (res) {
                    res.clone().text().then(report).catch(function () {});
                  }).catch(function () {});
                } catch (e) {}
              }
              return result;
            };
          }
          var origOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function (method, url) {
            this.__ituneUrl = url;
            return origOpen.apply(this, arguments);
          };
          var origSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.send = function () {
            var xhr = this;
            try {
              xhr.addEventListener('load', function () {
                if (isToken(xhr.__ituneUrl)) {
                  try { report(xhr.responseText); } catch (e) {}
                }
              });
            } catch (e) {}
            return origSend.apply(this, arguments);
          };
        })();
    """.trimIndent()

    /**
     * The second header these endpoints demand alongside the bearer token —
     * api.spotify.com and spclient both turn away a request that only carries
     * [accessToken] with a 429.
     */
    @Synchronized
    fun clientToken(): String? {
        val now = System.currentTimeMillis()
        cachedClientToken?.let { if (now < clientTokenExpiresAtMs - 30_000) return it }

        val clientId = cachedClientId ?: "d8a5ed95f0bc4f2e819c6a8dda425420"
        val session = session() ?: return null

        val payload = buildJsonObject {
            putJsonObject("client_data") {
                put("client_version", session.clientVersion)
                put("client_id", clientId)
                putJsonObject("js_sdk_data") {
                    put("device_brand", "unknown")
                    put("device_model", "unknown")
                    put("os", "android")
                    put("os_version", Build.VERSION.RELEASE.orEmpty())
                    put("device_id", session.deviceId)
                    put("device_type", "smartphone")
                }
            }
        }

        // The ByteArray overload, deliberately: the String overload of
        // toRequestBody rewrites a charset-less MediaType to
        // "application/json; charset=utf-8", and clienttoken 400s on that
        // exact header rather than the bare "application/json" the real web
        // player sends.
        val request = Request.Builder()
            .url("https://clienttoken.spotify.com/v1/clienttoken")
            .post(payload.toString().toByteArray(Charsets.UTF_8).toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("User-Agent", CANVAS_UA)
            .build()

        var lastCode = -1
        val body = runCatching {
            Net.client.newCall(request).execute().use { response ->
                lastCode = response.code
                if (response.isSuccessful) response.body?.string() else null
            }
        }.onFailure { Log.w(TAG, "client-token request threw: ${it.message}") }.getOrNull()
        if (body == null) {
            Log.w(TAG, "client-token request failed, http $lastCode")
            return null
        }

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        if (root == null) {
            Log.w(TAG, "client-token response wasn't JSON")
            return null
        }
        val responseType = root["response_type"]?.jsonPrimitive?.contentOrNull
        if (responseType != "RESPONSE_GRANTED_TOKEN_RESPONSE") {
            Log.w(TAG, "client-token request rejected: $responseType")
            return null
        }
        val granted = root["granted_token"]?.jsonObject
        val token = granted?.get("token")?.jsonPrimitive?.contentOrNull
        if (token == null) {
            Log.w(TAG, "client-token response had no granted_token.token")
            return null
        }
        val ttlSeconds = granted["expires_after_seconds"]?.jsonPrimitive?.contentOrNull
            ?.toLongOrNull() ?: 3600L

        cachedClientToken = token
        clientTokenExpiresAtMs = now + ttlSeconds * 1000
        Log.d(TAG, "minted client token, good for ${ttlSeconds}s")
        return token
    }

    /**
     * [SessionInfo.clientVersion] and [SessionInfo.deviceId] both come off the
     * plain, unauthenticated web player page — the client version from a JSON
     * blob it embeds for itself, the device id from the `sp_t` cookie it hands
     * out to every visitor.
     */
    private fun session(): SessionInfo? {
        cachedSession?.let { return it }

        val request = Request.Builder()
            .url("https://open.spotify.com")
            .header("User-Agent", CANVAS_UA)
            .build()
        val (html, deviceId) = runCatching {
            Net.client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val spT = response.headers("Set-Cookie").firstNotNullOfOrNull { header ->
                    Regex("""sp_t=([^;]+)""").find(header)?.groupValues?.get(1)
                }
                body to spT
            }
        }.getOrNull() ?: (null to null)
        if (html == null) {
            Log.w(TAG, "couldn't load the web player page for session info")
            return null
        }

        val configB64 = Regex("""<script id="appServerConfig" type="text/plain">([^<]+)</script>""")
            .find(html)?.groupValues?.get(1)
        if (configB64 == null) {
            Log.w(TAG, "web player page had no appServerConfig block")
            return null
        }
        val clientVersion = runCatching {
            val configJson = String(Base64.decode(configB64, Base64.DEFAULT), Charsets.UTF_8)
            json.parseToJsonElement(configJson).jsonObject["clientVersion"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        if (clientVersion == null) {
            Log.w(TAG, "appServerConfig had no clientVersion")
            return null
        }

        val session = SessionInfo(clientVersion, deviceId ?: java.util.UUID.randomUUID().toString())
        cachedSession = session
        return session
    }
}
