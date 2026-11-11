package com.darkxvenom.itune.ishare

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Direct REST client targeting the AirBeats Listen Together Cloudflare Worker backend.
 * Keeps 100% wire compatibility with AirBeats rooms and pairing codes.
 */
object IShareOnlineClient {
    const val PRIMARY_BACKEND_URL = "https://listentogether.airbeats.org"
    const val FALLBACK_BACKEND_URL = "https://listentogether.fdar606.workers.dev"

    @Volatile
    private var activeBaseUrl: String = PRIMARY_BACKEND_URL

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun createSession(
        displayName: String,
        state: ISharePlaybackState,
    ): IShareSession = postWithFallback(
        path = "/sessions",
        body = JSONObject()
            .put("name", displayName)
            .put("state", state.toJson(forOnlineWorker = true))
            .put("settings", state.settings.toJson()),
    ) { IShareSession.fromJson(it) }

    suspend fun joinSession(
        code: String,
        displayName: String,
    ): IShareSession = postWithFallback(
        path = "/sessions/${code.trim().uppercase()}/join",
        body = JSONObject().put("name", displayName),
    ) { IShareSession.fromJson(it) }

    suspend fun getSession(code: String): IShareSession =
        getWithFallback("/sessions/${code.trim().uppercase()}") { IShareSession.fromJson(it) }

    suspend fun updateState(
        code: String,
        participantId: String,
        state: ISharePlaybackState,
    ): IShareSession = postWithFallback(
        path = "/sessions/${code.trim().uppercase()}/state",
        body = JSONObject()
            .put("participantId", participantId)
            .put("state", state.toJson(forOnlineWorker = true))
            .put("settings", state.settings.toJson()),
    ) { IShareSession.fromJson(it) }

    suspend fun leaveSession(
        code: String,
        participantId: String,
    ) {
        runCatching {
            postWithFallback(
                path = "/sessions/${code.trim().uppercase()}/leave",
                body = JSONObject().put("participantId", participantId),
            ) { Unit }
        }
    }

    private suspend fun <T> getWithFallback(
        path: String,
        parser: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            executeGet("$activeBaseUrl$path", parser)
        } catch (ioe: IOException) {
            val backup = if (activeBaseUrl == PRIMARY_BACKEND_URL) FALLBACK_BACKEND_URL else PRIMARY_BACKEND_URL
            executeGet("$backup$path", parser).also { activeBaseUrl = backup }
        }
    }

    private suspend fun <T> postWithFallback(
        path: String,
        body: JSONObject,
        parser: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            executePost("$activeBaseUrl$path", body, parser)
        } catch (ioe: IOException) {
            val backup = if (activeBaseUrl == PRIMARY_BACKEND_URL) FALLBACK_BACKEND_URL else PRIMARY_BACKEND_URL
            executePost("$backup$path", body, parser).also { activeBaseUrl = backup }
        }
    }

    private fun <T> executeGet(url: String, parser: (JSONObject) -> T): T {
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(text, response.code))
            return parser(JSONObject(text))
        }
    }

    private fun <T> executePost(url: String, body: JSONObject, parser: (JSONObject) -> T): T {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorMessage(text, response.code))
            return parser(JSONObject(text))
        }
    }

    private fun errorMessage(text: String, code: Int): String =
        runCatching { JSONObject(text).optString("error") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "iShare request failed ($code)"
}

/**
 * Direct HTTP + UDP LAN client compatible with AirBeats LAN Listen Together hosts.
 */
object IShareLanClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.SECONDS)
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    fun normalizeHostAddress(raw: String, defaultPort: Int = 8765): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("airbeats://together", ignoreCase = true) ||
            trimmed.startsWith("ishare://together", ignoreCase = true)
        ) {
            val uri = Uri.parse(trimmed)
            val host = uri.getQueryParameter("host")
            val port = uri.getQueryParameter("port") ?: defaultPort.toString()
            if (!host.isNullOrBlank()) return "$host:$port"
        }
        val clean = trimmed.removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")
            .substringBefore("/")
        return if (clean.contains(":")) clean else "$clean:$defaultPort"
    }

    suspend fun joinSession(
        hostAddress: String,
        displayName: String,
    ): IShareSession = withContext(Dispatchers.IO) {
        val normalized = normalizeHostAddress(hostAddress)
        val url = "http://$normalized/together/join"
        val body = JSONObject().put("name", displayName).toString().toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to join LAN host: HTTP ${response.code}")
            }
            IShareSession.fromJson(JSONObject(text))
        }
    }

    suspend fun getSession(
        hostAddress: String,
        participantId: String,
    ): IShareSession = withContext(Dispatchers.IO) {
        val normalized = normalizeHostAddress(hostAddress)
        val url = "http://$normalized/together/state?participantId=$participantId"
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("LAN host error: HTTP ${response.code}")
            }
            IShareSession.fromJson(JSONObject(text))
        }
    }

    suspend fun updateState(
        hostAddress: String,
        participantId: String,
        state: ISharePlaybackState,
    ): IShareSession = withContext(Dispatchers.IO) {
        val normalized = normalizeHostAddress(hostAddress)
        val url = "http://$normalized/together/state"
        val body = JSONObject()
            .put("participantId", participantId)
            .put("state", state.toJson(forOnlineWorker = false))
            .toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder().url(url).post(body).build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to update LAN state: HTTP ${response.code}")
            }
            IShareSession.fromJson(JSONObject(text))
        }
    }

    suspend fun sendAction(
        hostAddress: String,
        participantId: String,
        action: String,
        positionMs: Long? = null,
        extraData: JSONObject? = null,
    ): IShareSession = withContext(Dispatchers.IO) {
        val normalized = normalizeHostAddress(hostAddress)
        val url = "http://$normalized/together/action"
        val body = JSONObject()
            .put("participantId", participantId)
            .put("action", action)
        if (positionMs != null) body.put("positionMs", positionMs)
        if (extraData != null) body.put("extraData", extraData)
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errMsg = runCatching { JSONObject(text).optString("error") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Action failed: HTTP ${response.code}"
                throw IllegalStateException(errMsg)
            }
            IShareSession.fromJson(JSONObject(text))
        }
    }

    suspend fun leaveSession(
        hostAddress: String,
        participantId: String,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeHostAddress(hostAddress)
            val url = "http://$normalized/together/leave"
            val body = JSONObject().put("participantId", participantId).toString().toRequestBody(jsonMediaType)
            httpClient.newCall(Request.Builder().url(url).post(body).build()).execute().close()
        }
    }

    suspend fun scanLocalNetwork(
        localIp: String,
        port: Int = 8765,
        context: Context? = null,
    ): List<DiscoveredLanHost> = withContext(Dispatchers.IO) {
        val discovered = ConcurrentHashMap<String, DiscoveredLanHost>()
        val prefix = localIp.substringBeforeLast(".")
        var multicastLock: WifiManager.MulticastLock? = null

        runCatching {
            val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("ishare_lan_client_discovery")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }

        try {
            // Strategy 1: Ultra-fast UDP broadcast compatible with AirBeats & iShare hosts
            runCatching {
                val udpSocket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 550
                }
                val data = "AIRBEATS_DISCOVER".toByteArray(Charsets.UTF_8)
                val broadcastTargets = listOfNotNull(
                    runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull(),
                    runCatching { InetAddress.getByName("$prefix.255") }.getOrNull(),
                    runCatching { InetAddress.getByName("$prefix.1") }.getOrNull(),
                )
                for (target in broadcastTargets) {
                    runCatching { udpSocket.send(DatagramPacket(data, data.size, target, port)) }
                }
                val buf = ByteArray(512)
                val startedAt = System.currentTimeMillis()
                while (System.currentTimeMillis() - startedAt < 550) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        udpSocket.receive(packet)
                        val response = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (response.startsWith("AIRBEATS_HOST") || response.startsWith("ISHARE_HOST")) {
                            val parts = response.split(":")
                            val hostPort = parts.getOrNull(1)?.toIntOrNull() ?: port
                            val hostName = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "AirBeats / iShare Host"
                            val sessionId = parts.getOrNull(3) ?: ""
                            val participantCount = parts.getOrNull(4)?.toIntOrNull() ?: 1
                            val senderIp = packet.address.hostAddress ?: ""
                            if (senderIp.isNotBlank()) {
                                val addr = "$senderIp:$hostPort"
                                discovered[addr] = DiscoveredLanHost(
                                    address = addr,
                                    hostName = hostName,
                                    participants = participantCount,
                                    sessionId = sessionId,
                                )
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (_: Exception) {}
                }
                udpSocket.close()
            }

            if (discovered.isNotEmpty()) {
                return@withContext discovered.values.toList()
            }

            // Strategy 2: Batched TCP probe + HTTP /together/status verification across /24 subnet
            val probeClient = OkHttpClient.Builder()
                .callTimeout(1400, TimeUnit.MILLISECONDS)
                .connectTimeout(1000, TimeUnit.MILLISECONDS)
                .readTimeout(1000, TimeUnit.MILLISECONDS)
                .build()

            val candidates = (1..254).map { "$prefix.$it" }
            coroutineScope {
                candidates.chunked(64).forEach { batch ->
                    if (discovered.isNotEmpty()) return@forEach
                    batch.map { ip ->
                        async {
                            val reachable = runCatching {
                                Socket().use { s -> s.connect(InetSocketAddress(ip, port), 350) }
                                true
                            }.getOrDefault(false)
                            if (reachable) {
                                runCatching {
                                    val url = "http://$ip:$port/together/status"
                                    probeClient.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                                        if (resp.isSuccessful) {
                                            val text = resp.body?.string().orEmpty()
                                            if (text.contains("AirBeats") || text.contains("iShare") || text.contains("sessionId")) {
                                                val json = JSONObject(text)
                                                val addr = "$ip:$port"
                                                discovered[addr] = DiscoveredLanHost(
                                                    address = addr,
                                                    hostName = json.optString("hostName").ifBlank { "LAN Host" },
                                                    participants = json.optInt("participants", 1),
                                                    sessionId = json.optString("sessionId", ""),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        } finally {
            runCatching {
                if (multicastLock?.isHeld == true) multicastLock?.release()
            }
        }

        discovered.values.toList()
    }
}

data class SavedIShareSession(
    val code: String,
    val participantId: String,
    val displayName: String,
    val isHost: Boolean,
    val isLan: Boolean = false,
    val hostAddress: String? = null,
)

object IShareStore {
    private const val PREFS = "ishare_together_session"
    private const val KEY_CODE = "code"
    private const val KEY_PARTICIPANT_ID = "participant_id"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_IS_HOST = "is_host"
    private const val KEY_IS_LAN = "is_lan"
    private const val KEY_HOST_ADDRESS = "host_address"

    fun defaultName(context: Context? = null): String {
        context?.let { SettingsStore.ensureInit(it) }
        val profileName = SettingsStore.state.value.displayName.trim()
        if (profileName.isNotBlank()) return profileName
        return Build.MODEL?.takeIf { it.isNotBlank() }?.let { "iTune on $it" } ?: "iShare Listener"
    }

    fun save(
        context: Context,
        session: IShareSession,
        displayName: String,
        isHost: Boolean,
        isLan: Boolean = false,
        hostAddress: String? = null,
    ) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, session.code)
            .putString(KEY_PARTICIPANT_ID, session.participantId)
            .putString(KEY_DISPLAY_NAME, displayName.ifBlank { defaultName(context) })
            .putBoolean(KEY_IS_HOST, isHost)
            .putBoolean(KEY_IS_LAN, isLan)
            .putString(KEY_HOST_ADDRESS, hostAddress)
            .apply()
    }

    fun load(context: Context): SavedIShareSession? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_CODE, null)?.takeIf { it.isNotBlank() } ?: return null
        val participantId = prefs.getString(KEY_PARTICIPANT_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val storedName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: defaultName(context)
        return SavedIShareSession(
            code = code,
            participantId = participantId,
            displayName = storedName,
            isHost = prefs.getBoolean(KEY_IS_HOST, false),
            isLan = prefs.getBoolean(KEY_IS_LAN, false),
            hostAddress = prefs.getString(KEY_HOST_ADDRESS, null),
        )
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
