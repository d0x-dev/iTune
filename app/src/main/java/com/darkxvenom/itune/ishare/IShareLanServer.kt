package com.darkxvenom.itune.ishare

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Embedded pure-Kotlin HTTP + UDP LAN host server 100% compatible with AirBeats Listen Together
 * and mobile/desktop browser guests on local Wi-Fi or Hotspot networks.
 */
class IShareLanServer(
    val context: Context? = null,
    val hostIp: String,
    val port: Int,
    val sessionId: String = UUID.randomUUID().toString().take(8).uppercase(),
    val hostDisplayName: String,
    var audioUrlResolver: (suspend (songId: String) -> String?)? = null,
    var onPlaybackCommand: ((action: String, positionMs: Long?, extraData: JSONObject?) -> Unit)? = null,
    var queueProvider: (() -> List<JSONObject>)? = null,
) {
    private val participants = ConcurrentHashMap<String, IShareParticipant>()
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    var playbackState: ISharePlaybackState? = null

    @Volatile
    var settings: IShareSettings = IShareSettings()

    @Volatile
    var stateVersion: Long = 1L

    @Volatile
    var controllerId: String = "host"

    @Volatile
    var lastActionTime: Long = 0L

    @Volatile
    private var cachedAudioUrl: Pair<String, String>? = null

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    private var acceptThread: Thread? = null
    private var discoveryThread: Thread? = null
    private var discoverySocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        participants["host"] = IShareParticipant(
            id = "host",
            name = hostDisplayName,
            isHost = true,
        )
    }

    fun start() {
        if (running) return
        running = true
        val server = ServerSocket(port)
        server.reuseAddress = true
        serverSocket = server

        acceptThread = Thread {
            while (running && !server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                executor.execute { handleClientSocket(client) }
            }
        }.apply {
            isDaemon = true
            name = "IShareLanHttp-Accept"
            start()
        }

        startUdpDiscoveryResponder()
    }

    fun stop() {
        running = false
        stopUdpDiscoveryResponder()
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        executor.shutdownNow()
    }

    fun kickParticipant(participantId: String) {
        if (participantId != "host") {
            participants.remove(participantId)
            stateVersion++
        }
    }

    fun buildSessionSnapshot(participantId: String): IShareSession =
        IShareSession(
            code = "$hostIp:$port",
            participantId = participantId,
            joinUrl = "airbeats://together?host=$hostIp&port=$port&sid=$sessionId",
            participants = participants.size,
            participantList = participants.values.sortedByDescending { it.isHost },
            hostName = hostDisplayName,
            controllerId = controllerId,
            controllerName = participants[controllerId]?.name ?: hostDisplayName,
            stateVersion = stateVersion,
            serverNow = System.currentTimeMillis(),
            state = playbackState?.let { s ->
                s.copy(
                    artists = s.artists.filterNot {
                        it.startsWith(ISharePlaybackState.META_PREFIX) || it.startsWith(ISharePlaybackState.QUEUE_PREFIX)
                    },
                    settings = settings,
                )
            },
            settings = settings,
        )

    private fun attachQueueAndSnapshot(participantId: String): JSONObject {
        val json = buildSessionSnapshot(participantId).toJson()
        queueProvider?.invoke()?.let { q ->
            json.put("queue", JSONArray().apply { q.forEach(::put) })
        }
        return json
    }

    private fun startUdpDiscoveryResponder() {
        stopUdpDiscoveryResponder()
        runCatching {
            val wifiManager = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("ishare_lan_host_discovery")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        discoveryThread = Thread {
            try {
                val socket = DatagramSocket(port).apply {
                    reuseAddress = true
                    broadcast = true
                }
                discoverySocket = socket
                val buf = ByteArray(512)
                while (running && !socket.isClosed) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (msg.startsWith("AIRBEATS_DISCOVER") || msg.startsWith("ISHARE_DISCOVER")) {
                        val replyMsg = "AIRBEATS_HOST:$port:$hostDisplayName:$sessionId:${participants.size}"
                        val replyData = replyMsg.toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(replyData, replyData.size, packet.address, packet.port))
                    }
                }
            } catch (_: Exception) {}
        }.apply {
            isDaemon = true
            name = "IShareLan-Discovery"
            start()
        }
    }

    private fun stopUdpDiscoveryResponder() {
        runCatching {
            discoverySocket?.close()
            discoverySocket = null
            discoveryThread?.interrupt()
            discoveryThread = null
            if (multicastLock?.isHeld == true) multicastLock?.release()
            multicastLock = null
        }
    }

    private fun handleClientSocket(socket: Socket) {
        socket.use { client ->
            runCatching {
                client.soTimeout = 10_000
                val input = BufferedInputStream(client.getInputStream())
                val output = client.getOutputStream()

                val req = parseHttpRequest(input) ?: return
                routeHttpRequest(req, output)
            }
        }
    }

    private data class ParsedHttpRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: String,
    )

    private fun parseHttpRequest(input: BufferedInputStream): ParsedHttpRequest? {
        val headerBuf = ByteArrayOutputStream()
        var prev1 = -1
        var prev2 = -1
        var prev3 = -1
        while (true) {
            val b = input.read()
            if (b == -1) return null
            headerBuf.write(b)
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) break
            if (headerBuf.size() > 32 * 1024) return null
            prev3 = prev2
            prev2 = prev1
            prev1 = b
        }
        val rawHeader = headerBuf.toString(Charsets.UTF_8.name())
        val lines = rawHeader.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val requestParts = lines[0].split(" ")
        if (requestParts.size < 2) return null
        val method = requestParts[0].uppercase()
        val fullTarget = requestParts[1]
        val path = fullTarget.substringBefore("?").ifBlank { "/" }
        val query = parseQuery(fullTarget.substringAfter("?", ""))

        val headers = HashMap<String, String>()
        for (i in 1 until lines.size) {
            val idx = lines[i].indexOf(':')
            if (idx > 0) {
                headers[lines[i].substring(0, idx).trim().lowercase()] = lines[i].substring(idx + 1).trim()
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 1024 * 1024) ?: 0
        val body = if (contentLength > 0) {
            val bytes = ByteArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val n = input.read(bytes, read, contentLength - read)
                if (n <= 0) break
                read += n
            }
            String(bytes, 0, read, Charsets.UTF_8)
        } else {
            ""
        }
        return ParsedHttpRequest(method, path, query, headers, body)
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        rawQuery.split("&").forEach { pair ->
            if (pair.isBlank()) return@forEach
            val idx = pair.indexOf('=')
            val k = if (idx >= 0) pair.substring(0, idx) else pair
            val v = if (idx >= 0) pair.substring(idx + 1) else ""
            map[urlDecode(k)] = urlDecode(v)
        }
        return map
    }

    private fun urlDecode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun routeHttpRequest(req: ParsedHttpRequest, output: OutputStream) {
        if (req.method == "OPTIONS") {
            writeTextResponse(output, 200, "OK", "text/plain", "")
            return
        }

        when {
            req.path == "/" || req.path == "/together" || req.path == "/index.html" -> {
                writeTextResponse(output, 200, "OK", "text/html; charset=UTF-8", renderWebPlayerHtml())
            }
            req.path == "/stream" || req.path == "/together/stream" || req.path == "/audio/stream" -> {
                serveAudioStream(req, output)
            }
            req.path == "/favicon.ico" -> {
                writeTextResponse(output, 204, "No Content", "image/x-icon", "")
            }
            req.path == "/together/status" || req.path == "/together/ping" -> {
                val json = JSONObject()
                    .put("status", "ok")
                    .put("app", "AirBeats")
                    .put("client", "iShare")
                    .put("sessionId", sessionId)
                    .put("hostName", hostDisplayName)
                    .put("participants", participants.size)
                writeJsonResponse(output, 200, "OK", json)
            }
            req.path == "/together/join" && req.method == "POST" -> {
                val body = parseBodyJson(req.body)
                val guestName = body.optString("name").ifBlank { "iShare Listener" }
                val guestId = UUID.randomUUID().toString().take(8)
                participants[guestId] = IShareParticipant(id = guestId, name = guestName, isHost = false)
                stateVersion++
                writeJsonResponse(output, 200, "OK", attachQueueAndSnapshot(guestId))
            }
            req.path == "/together/state" && req.method == "GET" -> {
                val pid = req.query["participantId"] ?: "guest"
                writeJsonResponse(output, 200, "OK", attachQueueAndSnapshot(pid))
            }
            req.path == "/together/queue" && req.method == "GET" -> {
                val arr = JSONArray().apply { queueProvider?.invoke()?.forEach(::put) }
                writeJsonResponse(output, 200, "OK", JSONObject().put("queue", arr))
            }
            req.path == "/together/search" && req.method == "GET" -> {
                val q = req.query["q"]?.trim().orEmpty()
                val itemsArr = JSONArray()
                if (q.isNotBlank()) {
                    val results = runCatching {
                        runBlocking(Dispatchers.IO) { MusicBackend.searchMusic(q) }
                    }.getOrDefault(emptyList())
                    results.take(12).forEach { t ->
                        itemsArr.put(
                            JSONObject()
                                .put("id", t.id)
                                .put("title", t.title)
                                .put("artist", t.artists)
                                .put("thumbnailUrl", t.thumbnailUrl ?: "")
                                .put("durationMs", t.durationSec.coerceAtLeast(0L) * 1000L),
                        )
                    }
                }
                writeJsonResponse(output, 200, "OK", JSONObject().put("items", itemsArr))
            }
            req.path == "/together/state" && req.method == "POST" -> {
                val body = parseBodyJson(req.body)
                body.optJSONObject("state")?.let { stateObj ->
                    playbackState = ISharePlaybackState.fromJson(stateObj)
                    stateVersion++
                }
                val pid = body.optString("participantId").ifBlank { "host" }
                writeJsonResponse(output, 200, "OK", attachQueueAndSnapshot(pid))
            }
            req.path == "/together/action" && req.method == "POST" -> {
                val body = parseBodyJson(req.body)
                val action = body.optString("action")
                val posMs = if (body.has("positionMs")) body.optLong("positionMs") else null
                val pid = body.optString("participantId").ifBlank { "guest" }
                val extra = body.optJSONObject("extraData")
                val isHostSender = pid == "host" || pid == controllerId

                if (action in CONTROL_ACTIONS && !isHostSender && !settings.allowGuestsToControlPlayback) {
                    writeJsonResponse(output, 403, "Forbidden", JSONObject().put("error", "Playback controls are disabled by the host"))
                    return
                }
                if (action in QUEUE_ACTIONS && !isHostSender && !settings.allowGuestsToAddTracks && !settings.allowGuestsToControlPlayback) {
                    writeJsonResponse(output, 403, "Forbidden", JSONObject().put("error", "Queue modification is disabled by the host"))
                    return
                }

                if (action.isNotBlank()) {
                    lastActionTime = System.currentTimeMillis()
                    playbackState?.let { cur ->
                        when (action) {
                            "play", "resume" -> playbackState = cur.copy(isPlaying = true, positionMs = posMs ?: cur.positionMs, updatedAt = System.currentTimeMillis(), settings = settings)
                            "pause" -> playbackState = cur.copy(isPlaying = false, positionMs = posMs ?: cur.positionMs, updatedAt = System.currentTimeMillis(), settings = settings)
                            "toggle_play_pause" -> playbackState = cur.copy(isPlaying = !cur.isPlaying, positionMs = posMs ?: cur.positionMs, updatedAt = System.currentTimeMillis(), settings = settings)
                            "seek" -> if (posMs != null) playbackState = cur.copy(positionMs = posMs, updatedAt = System.currentTimeMillis(), settings = settings)
                        }
                        stateVersion++
                    }
                    onPlaybackCommand?.invoke(action, posMs, extra)
                }
                writeJsonResponse(output, 200, "OK", attachQueueAndSnapshot(pid))
            }
            req.path == "/together/settings" && req.method == "POST" -> {
                val body = parseBodyJson(req.body)
                val pid = body.optString("participantId").ifBlank { "guest" }
                if (pid == "host" || pid == controllerId) {
                    settings = IShareSettings(
                        allowGuestsToControlPlayback = body.optBoolean("allowGuestsToControlPlayback", settings.allowGuestsToControlPlayback),
                        allowGuestsToAddTracks = body.optBoolean("allowGuestsToAddTracks", settings.allowGuestsToAddTracks),
                    )
                    playbackState = playbackState?.copy(settings = settings)
                    stateVersion++
                }
                writeJsonResponse(output, 200, "OK", attachQueueAndSnapshot(pid))
            }
            req.path == "/together/leave" && req.method == "POST" -> {
                val pid = parseBodyJson(req.body).optString("participantId")
                if (pid.isNotBlank() && pid != "host") {
                    participants.remove(pid)
                    stateVersion++
                }
                writeJsonResponse(output, 200, "OK", JSONObject().put("status", "ok"))
            }
            else -> {
                writeTextResponse(output, 404, "Not Found", "text/plain", "Not Found")
            }
        }
    }

    private fun serveAudioStream(req: ParsedHttpRequest, output: OutputStream) {
        val targetSongId = req.query["id"]?.takeIf { it.isNotBlank() }
            ?: playbackState?.songId?.takeIf { it.isNotBlank() }
        if (targetSongId == null) {
            writeTextResponse(output, 404, "Not Found", "text/plain", "No active track")
            return
        }
        val streamUrl = getOrResolveUrl(targetSongId)
        if (streamUrl.isNullOrBlank()) {
            writeTextResponse(output, 404, "Not Found", "text/plain", "Stream unavailable")
            return
        }
        val builder = Request.Builder().url(streamUrl)
        req.headers["range"]?.let { builder.header("Range", it) }
        val upstream = runCatching { Net.client.newCall(builder.build()).execute() }.getOrNull()
        if (upstream == null) {
            writeTextResponse(output, 502, "Bad Gateway", "text/plain", "Upstream unavailable")
            return
        }
        upstream.use { resp ->
            val contentType = resp.header("Content-Type") ?: "audio/mp4"
            val contentRange = resp.header("Content-Range")
            val contentLen = resp.body?.contentLength()?.takeIf { it >= 0L }
            val statusMsg = if (resp.code == 206) "Partial Content" else "OK"
            val hdr = buildString {
                append("HTTP/1.1 ").append(resp.code).append(' ').append(statusMsg).append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Accept-Ranges: bytes\r\n")
                appendCorsHeaders(this)
                if (contentLen != null) append("Content-Length: ").append(contentLen).append("\r\n")
                if (contentRange != null) append("Content-Range: ").append(contentRange).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(hdr.toByteArray(Charsets.UTF_8))
            if (req.method != "HEAD") {
                resp.body?.byteStream()?.use { runCatching { copyStream(it, output) } }
            }
            runCatching { output.flush() }
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
    }

    private fun getOrResolveUrl(songId: String): String? {
        val cached = cachedAudioUrl
        if (cached != null && cached.first == songId) return cached.second

        val resolver = audioUrlResolver
        val directUrl = if (resolver != null) {
            runCatching { runBlocking(Dispatchers.IO) { resolver(songId) } }.getOrNull()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val resolvedUrl = directUrl ?: resolveFallbackByTitle(songId) ?: return null
        cachedAudioUrl = songId to resolvedUrl
        return resolvedUrl
    }

    private fun resolveFallbackByTitle(songId: String): String? {
        val activeState = playbackState?.takeIf { it.songId == songId }
        val fallbackItem = queueProvider?.invoke()?.firstOrNull {
            it.optString("id") == songId || it.optString("songId") == songId
        }
        val title = activeState?.title?.takeIf { it.isNotBlank() }
            ?: fallbackItem?.optString("title")?.takeIf { it.isNotBlank() }
            ?: return null
        val artist = activeState?.artists
            ?.filterNot { it.startsWith(ISharePlaybackState.META_PREFIX) || it.startsWith(ISharePlaybackState.QUEUE_PREFIX) }
            ?.firstOrNull()
            ?: fallbackItem?.optString("artist").orEmpty()
        val query = "$title $artist".trim()
        return runCatching {
            runBlocking(Dispatchers.IO) {
                val match = MusicBackend.searchMusic(query).firstOrNull() ?: return@runBlocking null
                MusicBackend.resolveAudioStreamUrl(match.id)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun parseBodyJson(raw: String): JSONObject =
        runCatching { if (raw.isNotBlank()) JSONObject(raw) else JSONObject() }.getOrDefault(JSONObject())

    private fun writeJsonResponse(output: OutputStream, statusCode: Int, statusText: String, json: JSONObject) {
        writeTextResponse(output, statusCode, statusText, "application/json", json.toString())
    }

    private fun writeTextResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        mimeType: String,
        body: String,
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n")
            append("Content-Type: ").append(mimeType).append("\r\n")
            append("Content-Length: ").append(bytes.size).append("\r\n")
            appendCorsHeaders(this)
            append("Connection: close\r\n\r\n")
        }
        output.write(headers.toByteArray(Charsets.UTF_8))
        if (bytes.isNotEmpty()) output.write(bytes)
        output.flush()
    }

    private fun appendCorsHeaders(sb: StringBuilder) {
        sb.append("Access-Control-Allow-Origin: *\r\n")
        sb.append("Access-Control-Allow-Headers: Range, Origin, Accept, Content-Type\r\n")
        sb.append("Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n")
        sb.append("Access-Control-Expose-Headers: Content-Range, Accept-Ranges, Content-Length\r\n")
    }

    private fun renderWebPlayerHtml(): String =
        buildIShareWebPlayerHtml(
            hostDisplayName = hostDisplayName,
            hostIp = hostIp,
            port = port,
            sessionId = sessionId,
        )

    companion object {
        private val CONTROL_ACTIONS = setOf(
            "play", "pause", "resume", "toggle_play_pause", "seek",
            "next", "skip_next", "previous", "skip_previous", "play_index", "play_song", "jump_to",
        )
        private val QUEUE_ACTIONS = setOf(
            "add_track", "play_next", "remove_track", "remove_song", "move_track",
        )

        fun getLocalIpAddress(context: Context?): String? {
            if (context != null) {
                val ipFromCm = runCatching {
                    val cm = context.getSystemService(ConnectivityManager::class.java)
                    cm?.allNetworks?.asSequence()
                        ?.filter { net ->
                            val caps = cm.getNetworkCapabilities(net)
                            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
                        }
                        ?.flatMap { cm.getLinkProperties(it)?.linkAddresses.orEmpty().asSequence() }
                        ?.map { it.address }
                        ?.filterIsInstance<Inet4Address>()
                        ?.firstOrNull { !it.isLoopbackAddress }
                        ?.hostAddress
                }.getOrNull()
                if (!ipFromCm.isNullOrBlank() && ipFromCm != "127.0.0.1") return ipFromCm
            }

            return runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .asSequence()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList().asSequence() }
                    .filterIsInstance<Inet4Address>()
                    .map { it.hostAddress }
                    .firstOrNull { !it.isNullOrBlank() && it != "127.0.0.1" }
            }.getOrNull()
        }
    }
}
