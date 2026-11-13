package com.darkxvenom.itune.data.discord

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.darkxvenom.airbeats.discordrpc.rpc.DiscordRpcClient
import com.darkxvenom.airbeats.discordrpc.rpc.RpcImage
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.auth.EncryptedAuthPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Optional listening presence, using the user's explicitly connected Discord account. */
object DiscordPresenceManager {
    const val APPLICATION_ID = "1411019391843172514"
    private const val PREFS = "itune_discord_presence"
    private const val TOKEN_KEY = "itune_discord_user_token"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var token: String = ""
    private var rpc: DiscordRpcClient? = null
    private var lastTrackId: String? = null
    private var lastUpdateAtMs = 0L

    var username by mutableStateOf("")
        private set
    var displayName by mutableStateOf("")
        private set
    var enabled by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        token = EncryptedAuthPrefs.getDecrypted(app, TOKEN_KEY).orEmpty()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        username = prefs.getString("username", "").orEmpty()
        displayName = prefs.getString("display_name", "").orEmpty()
        enabled = prefs.getBoolean("enabled", false) && token.isNotBlank()
    }

    suspend fun connect(accessToken: String): Result<String> = runCatching {
        val clean = accessToken.trim().trim('"', '\'')
        require(clean.isNotBlank()) { "Discord did not provide an account token" }
        val info = DiscordRpcClient.getUserInfo(clean).getOrThrow()
        token = clean
        username = info.username
        displayName = info.name.ifBlank { info.username }
        val app = appContext ?: error("iTune is not initialized")
        EncryptedAuthPrefs.putEncrypted(app, TOKEN_KEY, clean)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("username", username)
            .putString("display_name", displayName)
            .putBoolean("enabled", true)
            .apply()
        enabled = true
        lastTrackId = null
        displayName
    }

    fun setPresenceEnabled(enabled: Boolean) {
        val app = appContext ?: return
        if (enabled && token.isBlank()) return
        this.enabled = enabled
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        if (!enabled) clearActivity()
    }

    fun disconnect() {
        val app = appContext
        enabled = false
        token = ""
        username = ""
        displayName = ""
        rpc?.closeRPC()
        rpc = null
        EncryptedAuthPrefs.putEncrypted(app ?: return, TOKEN_KEY, null)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun onTrackProgress(track: MusicTrack, positionMs: Long, durationMs: Long, speed: Float) {
        if (!enabled || token.isBlank()) return
        val now = System.currentTimeMillis()
        val changed = lastTrackId != track.id
        if (!changed && now - lastUpdateAtMs < 15_000L) return
        lastTrackId = track.id
        lastUpdateAtMs = now
        scope.launch {
            runCatching {
                val client = rpc ?: DiscordRpcClient(token).also { rpc = it }
                val safeSpeed = speed.coerceAtLeast(0.25f)
                val elapsed = (positionMs / safeSpeed).toLong()
                val remaining = ((durationMs - positionMs).coerceAtLeast(0L) / safeSpeed).toLong()
                val timestamp = System.currentTimeMillis()
                client.setActivity(
                    name = "iTune",
                    details = track.title,
                    state = track.artists,
                    detailsUrl = null,
                    largeImage = track.thumbnailUrl?.takeIf { it.isNotBlank() }?.let(RpcImage::ExternalImage),
                    smallImage = null,
                    largeText = track.album,
                    smallText = track.artists,
                    type = DiscordRpcClient.Type.LISTENING,
                    statusDisplayType = DiscordRpcClient.StatusDisplayType.STATE,
                    since = timestamp,
                    startTime = timestamp - elapsed,
                    endTime = if (durationMs > 0L) timestamp + remaining else null,
                    applicationId = APPLICATION_ID,
                )
            }
        }
    }

    fun clearActivity() {
        lastTrackId = null
        scope.launch { runCatching { rpc?.stopActivity() } }
    }
}
