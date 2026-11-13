package com.darkxvenom.itune.data.lastfm

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.darkxvenom.itune.data.auth.EncryptedAuthPrefs
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Last.fm browser auth and playback integration. Credentials match AirBeats' published app config. */
object LastFmIntegration {
    private const val API_KEY = "e4ff17cfd46f9de0433018bc72d8f8e4"
    private const val API_SECRET = "91a200bc71d725949101232c96020733"
    const val CALLBACK = "itune://auth-callback"
    private const val PREFS = "lastfm_integration"
    private const val BASE = "https://ws.audioscrobbler.com/2.0/"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var lastPlaybackDispatchMs = 0L

    var username by mutableStateOf("")
        private set
    var status by mutableStateOf("Not connected")
        private set
    private var sessionKey = ""
    private var token = ""
    private var lastNowPlaying = ""
    private val scrobbled = mutableSetOf<String>()

    fun initialize(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        username = p.getString("username", "").orEmpty()
        sessionKey = EncryptedAuthPrefs.getDecrypted(context.applicationContext, "lastfm_session_key").orEmpty()
        status = if (sessionKey.isNotBlank()) "Connected as $username" else "Not connected"
    }

    suspend fun beginSignIn(context: Context): Result<Unit> = runCatching {
        val body = get(mapOf("method" to "auth.getToken", "api_key" to API_KEY))
        token = JSONObject(body).getString("token")
        val url = Uri.parse("https://www.last.fm/api/auth/").buildUpon()
            .appendQueryParameter("api_key", API_KEY).appendQueryParameter("token", token)
            .appendQueryParameter("cb", CALLBACK).build()
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        status = "Approve iTune in your browser, then return here"
    }

    /** Last.fm's official mobile-session exchange. The password is used for this request only. */
    suspend fun directSignIn(context: Context, usernameInput: String, password: String): Result<String> = runCatching {
        val cleanUsername = usernameInput.trim()
        require(cleanUsername.isNotBlank()) { "Enter your Last.fm username" }
        require(password.isNotBlank()) { "Enter your Last.fm password" }
        val params = mapOf(
            "method" to "auth.getMobileSession",
            "username" to cleanUsername,
            "password" to password,
            "api_key" to API_KEY,
        )
        val obj = JSONObject(post(params + ("api_sig" to sign(params))))
        val session = obj.getJSONObject("session")
        username = session.getString("name")
        sessionKey = session.getString("key")
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("username", username).apply()
        EncryptedAuthPrefs.putEncrypted(context.applicationContext, "lastfm_session_key", sessionKey)
        status = "Connected as $username"
        lastNowPlaying = ""
        username
    }.onFailure { status = it.message ?: "Last.fm sign in failed" }

    suspend fun completeSignIn(context: Context, returnedToken: String): Result<String> = runCatching {
        val authToken = returnedToken.ifBlank { token }
        require(authToken.isNotBlank()) { "Missing Last.fm authorization token" }
        val params = mapOf("method" to "auth.getSession", "token" to authToken, "api_key" to API_KEY)
        val obj = JSONObject(post(params + ("api_sig" to sign(params))))
        obj.optJSONObject("error")?.let { error("Last.fm: ${obj.optString("message")}") }
        val session = obj.getJSONObject("session")
        username = session.getString("name")
        sessionKey = session.getString("key")
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("username", username).apply()
        EncryptedAuthPrefs.putEncrypted(context.applicationContext, "lastfm_session_key", sessionKey)
        status = "Connected as $username"
        token = ""
        username
    }.onFailure { status = it.message ?: "Last.fm sign in failed" }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        EncryptedAuthPrefs.putEncrypted(context.applicationContext, "lastfm_session_key", null)
        username = ""; sessionKey = ""; token = ""; lastNowPlaying = ""
        status = "Not connected"
    }

    fun onPlayback(trackId: String, title: String, artists: String, album: String?, durationMs: Long, positionMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastPlaybackDispatchMs < 5_000L) return
        lastPlaybackDispatchMs = now
        ioScope.launch {
            if (sessionKey.isBlank() || title.isBlank() || artists.isBlank()) return@launch
            val artist = artists.split(',').first().trim().ifBlank { return@launch }
            val identity = "$trackId|$title|$artist"
            if (lastNowPlaying != identity) {
                val sent = runCatching {
                    signedPost(mapOf("method" to "track.updateNowPlaying", "artist" to artist, "track" to title).withAlbum(album))
                }.isSuccess
                if (sent) lastNowPlaying = identity
            }
            val threshold = minOf(240_000L, durationMs / 2).coerceAtLeast(30_000L)
            if (durationMs > 0 && positionMs >= threshold && identity !in scrobbled) {
                val sent = runCatching {
                    signedPost(mapOf("method" to "track.scrobble", "artist" to artist, "track" to title,
                        "timestamp" to (System.currentTimeMillis() / 1000L - positionMs / 1000L).toString()).withAlbum(album))
                }.isSuccess
                if (sent) scrobbled.add(identity)
            }
        }
    }

    private fun Map<String, String>.withAlbum(album: String?) = if (album.isNullOrBlank()) this else this + ("album" to album)
    private suspend fun signedPost(params: Map<String, String>) {
        val signed = params + mapOf("api_key" to API_KEY, "sk" to sessionKey)
        post(signed + ("api_sig" to sign(signed)))
    }
    private fun sign(params: Map<String, String>): String = md5(params.toSortedMap().entries.joinToString("") { it.key + it.value } + API_SECRET)
    private fun md5(input: String) = MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    private suspend fun get(params: Map<String, String>) = withContext(Dispatchers.IO) {
        val url = Uri.parse(BASE).buildUpon().apply { (params + ("format" to "json")).forEach { (k, v) -> appendQueryParameter(k, v) } }.build()
        http.newCall(Request.Builder().url(url.toString()).get().build()).execute().use { response ->
            val body = response.body?.string().orEmpty(); check(response.isSuccessful) { "Last.fm HTTP ${response.code}: $body" }; body
        }
    }
    private suspend fun post(params: Map<String, String>) = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply { (params + ("format" to "json")).forEach { (k, v) -> add(k, v) } }.build()
        http.newCall(Request.Builder().url(BASE).post(form).build()).execute().use { response ->
            val body = response.body?.string().orEmpty(); check(response.isSuccessful) { "Last.fm HTTP ${response.code}: $body" }
            val obj = JSONObject(body); check(!obj.has("error")) { "Last.fm error ${obj.optInt("error")}: ${obj.optString("message")}" }; body
        }
    }
}
