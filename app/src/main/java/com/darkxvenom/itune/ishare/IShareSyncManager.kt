package com.darkxvenom.itune.ishare

import android.content.Context
import android.util.Log
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/**
 * Coordinates iShare Listen Together sessions across both:
 * 1) AirBeats Cloud API (`https://listentogether.airbeats.org`) with pairing codes
 * 2) Local Wi-Fi / Hotspot LAN host & client discovery (`IShareLanServer` / `IShareLanClient`)
 */
object IShareSyncManager {
    private const val TAG = "IShareSyncManager"
    private const val ONLINE_SYNC_MS = 900L
    private const val LAN_SYNC_MS = 250L
    private const val CONTROLLER_PUBLISH_MS = 4000L
    private const val APPLY_GRACE_MS = 2500L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncJob: Job? = null
    private var appContext: Context? = null

    private var lastAppliedVersion = 0L
    private var lastControllerPublishAt = 0L
    private var suppressLocalSyncUntil = 0L
    private var pendingGuestSongId: String? = null
    private var lastHandledActionId: String? = null
    private var loadingSongId: String? = null
    private var lastSongLoadAttempt = 0L

    private var originalGuestParticipantId: String? = null
    private var sessionHostId: String? = null

    private var lanServer: IShareLanServer? = null
    var lanHostAddress: String? = null
        private set

    private val _session = MutableStateFlow<IShareSession?>(null)
    val session: StateFlow<IShareSession?> = _session.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _connectionMode = MutableStateFlow(IShareConnectionMode.ONLINE)
    val connectionMode: StateFlow<IShareConnectionMode> = _connectionMode.asStateFlow()

    private val _displayName = MutableStateFlow(IShareStore.defaultName())
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _sharedQueue = MutableStateFlow<List<IShareQueueItem>>(emptyList())
    val sharedQueue: StateFlow<List<IShareQueueItem>> = _sharedQueue.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredLanHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredLanHost>> = _discoveredHosts.asStateFlow()

    private val _localIp = MutableStateFlow<String?>(null)
    val localIp: StateFlow<String?> = _localIp.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        refreshLocalIp()

        val defName = IShareStore.defaultName(app)
        if (_session.value == null) {
            _displayName.value = defName
        }

        val saved = IShareStore.load(app) ?: run {
            if (_session.value != null) restartSync()
            return
        }
        if (_session.value != null) {
            restartSync()
            return
        }

        _displayName.value = saved.displayName.ifBlank { defName }
        _isHost.value = saved.isHost
        _connectionMode.value = if (saved.isLan) IShareConnectionMode.LAN else IShareConnectionMode.ONLINE
        lanHostAddress = saved.hostAddress

        scope.launch {
            runCatching {
                if (saved.isLan) {
                    if (saved.isHost) {
                        val port = saved.code.substringAfterLast(":").toIntOrNull() ?: 8765
                        startLanHost(port)
                        return@launch
                    } else {
                        val host = saved.hostAddress ?: saved.code
                        IShareLanClient.getSession(host, saved.participantId)
                    }
                } else {
                    IShareOnlineClient.getSession(saved.code).copy(participantId = saved.participantId)
                }
            }.onSuccess { restored ->
                adoptRestoredSession(saved, restored)
            }.onFailure {
                IShareStore.clear(app)
            }
        }
    }

    private fun adoptRestoredSession(saved: SavedIShareSession, remote: IShareSession) {
        if (saved.isHost) {
            originalGuestParticipantId = null
            sessionHostId = remote.participantId
            _session.value = remote
        } else {
            originalGuestParticipantId = saved.participantId
            sessionHostId = remote.controllerId.ifBlank { if (saved.isLan) "host" else null }
            val canControl = remote.settings.allowGuestsToControlPlayback
            val effectiveId = if (canControl && !sessionHostId.isNullOrBlank()) sessionHostId!! else saved.participantId
            _session.value = remote.copy(participantId = effectiveId)
        }
        lastAppliedVersion = remote.stateVersion
        restartSync()
    }

    fun refreshLocalIp() {
        _localIp.value = IShareLanServer.getLocalIpAddress(appContext)
    }

    fun setDisplayName(name: String) {
        _displayName.value = name.trim().ifBlank { IShareStore.defaultName(appContext) }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun emitMessage(msg: String?) {
        if (msg.isNullOrBlank()) return
        val lower = msg.lowercase()
        val noisy = lower.contains("cancelled") ||
            lower.contains("standalonecoroutine") ||
            lower.contains("jobcancellationexception")
        if (!noisy) _message.value = msg
    }

    fun createOnlineSession(
        allowGuestsToControlPlayback: Boolean,
        allowGuestsToAddTracks: Boolean,
    ) {
        val context = appContext ?: return
        val track = MusicPlayerState.currentTrack ?: run {
            emitMessage("Play any song first to host an iShare session")
            return
        }
        if (_isBusy.value) return
        _isBusy.value = true

        val initialSettings = IShareSettings(
            allowGuestsToControlPlayback = allowGuestsToControlPlayback,
            allowGuestsToAddTracks = allowGuestsToAddTracks,
        )
        val upcomingQueue = MusicPlayerState.upcomingIShareQueue()
        val initialState = ISharePlaybackState(
            songId = track.id,
            title = track.title,
            artists = track.artists.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            thumbnailUrl = track.thumbnailUrl,
            positionMs = MusicPlayerState.positionMs,
            isPlaying = MusicPlayerState.isPlaying,
            durationMs = MusicPlayerState.durationMs.takeIf { it > 0L } ?: (track.durationSec * 1000L).coerceAtLeast(0L),
            settings = initialSettings,
            queue = upcomingQueue,
            totalQueueSize = MusicPlayerState.queue.size,
        )

        scope.launch {
            runCatching {
                IShareOnlineClient.createSession(_displayName.value, initialState)
            }.onSuccess { created ->
                originalGuestParticipantId = null
                sessionHostId = created.participantId
                _isHost.value = true
                _connectionMode.value = IShareConnectionMode.ONLINE
                lanHostAddress = null
                lastAppliedVersion = created.stateVersion
                _sharedQueue.value = upcomingQueue
                _session.value = created.copy(
                    settings = initialSettings,
                    state = created.state?.copy(settings = initialSettings, queue = upcomingQueue) ?: initialState,
                    queue = upcomingQueue,
                )
                IShareStore.save(context, created, _displayName.value, isHost = true, isLan = false)
                emitMessage("iShare room ready • Code ${created.code}")
                restartSync()
            }.onFailure { err ->
                emitMessage(err.message ?: "Failed to create iShare session")
            }
            _isBusy.value = false
        }
    }

    fun joinOnlineSession(code: String) {
        val context = appContext ?: return
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            emitMessage("Enter an iShare pairing code")
            return
        }
        if (_isBusy.value) return
        _isBusy.value = true

        scope.launch {
            runCatching {
                IShareOnlineClient.joinSession(cleanCode, _displayName.value)
            }.onSuccess { joined ->
                originalGuestParticipantId = joined.participantId
                sessionHostId = joined.controllerId.ifBlank { null }
                _isHost.value = false
                _connectionMode.value = IShareConnectionMode.ONLINE
                lanHostAddress = null
                lastAppliedVersion = 0L
                loadingSongId = null
                lastSongLoadAttempt = 0L

                val hasPerm = joined.settings.allowGuestsToControlPlayback
                val effectiveId = if (hasPerm && !sessionHostId.isNullOrBlank()) sessionHostId!! else joined.participantId
                _session.value = joined.copy(participantId = effectiveId)
                IShareStore.save(context, joined, _displayName.value, isHost = false, isLan = false)
                emitMessage("Paired with ${joined.hostName}")
                restartSync()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not find room $cleanCode")
            }
            _isBusy.value = false
        }
    }

    fun startLanHost(
        port: Int = 8765,
        allowGuestsToControlPlayback: Boolean = false,
        allowGuestsToAddTracks: Boolean = true,
    ) {
        val context = appContext ?: return
        val track = MusicPlayerState.currentTrack ?: run {
            emitMessage("Play any song first to host on local Wi-Fi")
            return
        }
        val ip = IShareLanServer.getLocalIpAddress(context)
        if (ip.isNullOrBlank()) {
            emitMessage("Connect to Wi-Fi or turn on Hotspot first")
            return
        }
        _localIp.value = ip
        if (_isBusy.value) return
        _isBusy.value = true

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    lanServer?.stop()
                    val serverSettings = IShareSettings(
                        allowGuestsToControlPlayback = allowGuestsToControlPlayback,
                        allowGuestsToAddTracks = allowGuestsToAddTracks,
                    )
                    val server = IShareLanServer(
                        context = context,
                        hostIp = ip,
                        port = port,
                        hostDisplayName = _displayName.value,
                        audioUrlResolver = { songId -> MusicBackend.resolveAudioStreamUrl(songId) },
                    ).apply {
                        settings = serverSettings
                        playbackState = buildHostPlaybackState(track, serverSettings)
                        queueProvider = { buildLanQueueJson() }
                        onPlaybackCommand = { action, posMs, extra ->
                            handleHostCommandFromGuest(action, posMs, extra)
                        }
                        start()
                    }
                    server
                }
            }.onSuccess { server ->
                lanServer = server
                lanHostAddress = "$ip:$port"
                _connectionMode.value = IShareConnectionMode.LAN
                originalGuestParticipantId = null
                sessionHostId = "host"
                val snapshot = server.buildSessionSnapshot("host")
                _session.value = snapshot
                _isHost.value = true
                lastAppliedVersion = snapshot.stateVersion
                IShareStore.save(
                    context = context,
                    session = snapshot,
                    displayName = _displayName.value,
                    isHost = true,
                    isLan = true,
                    hostAddress = "$ip:$port",
                )
                emitMessage("LAN host live at $ip:$port")
                restartSync()
            }.onFailure { err ->
                emitMessage("Failed to start LAN host: ${err.message}")
            }
            _isBusy.value = false
        }
    }

    fun joinLanSession(hostAddress: String) {
        val context = appContext ?: return
        if (hostAddress.isBlank()) {
            emitMessage("Enter host IP or pick a discovered Wi-Fi device")
            return
        }
        if (_isBusy.value) return
        _isBusy.value = true

        scope.launch {
            runCatching {
                val normalized = IShareLanClient.normalizeHostAddress(hostAddress)
                normalized to IShareLanClient.joinSession(normalized, _displayName.value)
            }.onSuccess { (cleanAddr, joined) ->
                lanHostAddress = cleanAddr
                _connectionMode.value = IShareConnectionMode.LAN
                originalGuestParticipantId = joined.participantId
                sessionHostId = "host"
                val hasPerm = joined.settings.allowGuestsToControlPlayback
                val effectiveId = if (hasPerm) "host" else joined.participantId
                _session.value = joined.copy(participantId = effectiveId)
                _isHost.value = false
                lastAppliedVersion = 0L
                loadingSongId = null
                lastSongLoadAttempt = 0L

                IShareStore.save(
                    context = context,
                    session = joined,
                    displayName = _displayName.value,
                    isHost = false,
                    isLan = true,
                    hostAddress = cleanAddr,
                )
                emitMessage("Joined LAN session on ${joined.hostName}")
                restartSync()
            }.onFailure { err ->
                emitMessage(err.message ?: "Failed to connect to LAN host")
            }
            _isBusy.value = false
        }
    }

    fun scanLanHosts(port: Int = 8765) {
        val context = appContext ?: return
        if (_isScanningLan.value) return
        val ip = IShareLanServer.getLocalIpAddress(context)
        _localIp.value = ip
        if (ip.isNullOrBlank()) {
            emitMessage("Connect to Wi-Fi to scan local devices")
            return
        }
        _isScanningLan.value = true
        scope.launch {
            runCatching {
                IShareLanClient.scanLocalNetwork(localIp = ip, port = port, context = context)
            }.onSuccess { hosts ->
                _discoveredHosts.value = hosts
                emitMessage(
                    if (hosts.isEmpty()) "No AirBeats or iShare hosts found on Wi-Fi"
                    else "Found ${hosts.size} nearby host(s)",
                )
            }.onFailure { err ->
                emitMessage(err.message ?: "LAN scan failed")
            }
            _isScanningLan.value = false
        }
    }

    fun leaveSession() {
        scope.launch {
            cleanupSessionInternal(notify = true)
        }
    }

    fun switchHostMode(
        online: Boolean,
        allowGuestsToControlPlayback: Boolean,
        allowGuestsToAddTracks: Boolean,
    ) {
        if (_isBusy.value) return
        scope.launch {
            cleanupSessionInternal(notify = false)
            if (online) {
                createOnlineSession(allowGuestsToControlPlayback, allowGuestsToAddTracks)
            } else {
                startLanHost(
                    port = 8765,
                    allowGuestsToControlPlayback = allowGuestsToControlPlayback,
                    allowGuestsToAddTracks = allowGuestsToAddTracks,
                )
            }
        }
    }

    private suspend fun cleanupSessionInternal(notify: Boolean) {
        val context = appContext
        val active = _session.value
        val isLan = _connectionMode.value == IShareConnectionMode.LAN
        val hostAddr = lanHostAddress

        syncJob?.cancel()
        if (active != null) {
            withContext(Dispatchers.IO) {
                if (isLan) {
                    if (_isHost.value) {
                        runCatching { lanServer?.stop() }
                        lanServer = null
                    } else if (!hostAddr.isNullOrBlank()) {
                        IShareLanClient.leaveSession(hostAddr, originalGuestParticipantId ?: active.participantId)
                    }
                } else {
                    IShareOnlineClient.leaveSession(active.code, originalGuestParticipantId ?: active.participantId)
                }
            }
        }
        runCatching { lanServer?.stop() }
        lanServer = null
        lanHostAddress = null
        loadingSongId = null
        lastSongLoadAttempt = 0L
        originalGuestParticipantId = null
        sessionHostId = null
        MusicPlayerState.setRemotePlaybackSpeed(1f)
        if (context != null) IShareStore.clear(context)
        _session.value = null
        _isHost.value = false
        _sharedQueue.value = emptyList()
        if (notify) emitMessage("Disconnected from iShare")
    }

    fun updateSettings(
        allowGuestsToControlPlayback: Boolean,
        allowGuestsToAddTracks: Boolean,
    ) {
        if (!_isHost.value) return
        val newSettings = IShareSettings(
            allowGuestsToControlPlayback = allowGuestsToControlPlayback,
            allowGuestsToAddTracks = allowGuestsToAddTracks,
        )
        val current = _session.value ?: return
        _session.value = current.copy(
            settings = newSettings,
            state = current.state?.copy(settings = newSettings),
        )

        scope.launch {
            if (_connectionMode.value == IShareConnectionMode.LAN) {
                val server = lanServer ?: return@launch
                server.settings = newSettings
                server.playbackState = server.playbackState?.copy(settings = newSettings)
                server.stateVersion++
                _session.value = server.buildSessionSnapshot("host")
            } else {
                val track = MusicPlayerState.currentTrack
                val payload = if (track != null) {
                    buildHostPlaybackState(track, newSettings)
                } else {
                    current.state?.copy(settings = newSettings) ?: ISharePlaybackState(settings = newSettings)
                }
                runCatching {
                    IShareOnlineClient.updateState(current.code, current.participantId, payload)
                }.onSuccess { updated ->
                    _session.value = updated.copy(
                        participantId = current.participantId,
                        settings = newSettings,
                        state = updated.state?.copy(settings = newSettings) ?: payload,
                    )
                }.onFailure {
                    Log.w(TAG, "updateSettings online failed: ${it.message}")
                }
            }
        }
    }

    fun kickParticipant(participantId: String) {
        if (!_isHost.value) return
        lanServer?.kickParticipant(participantId)
        _session.value?.let { cur ->
            val updated = cur.participantList.filterNot { it.id == participantId }
            _session.value = cur.copy(
                participants = updated.size,
                participantList = updated,
            )
        }
    }

    fun onLocalPlaybackEvent() {
        val active = _session.value ?: return
        if (!_isHost.value) return
        if (System.currentTimeMillis() < suppressLocalSyncUntil) return
        scope.launch {
            if (_connectionMode.value == IShareConnectionMode.ONLINE) {
                publishLocalStateOnline(active)
            } else {
                publishLocalStateLan()
            }
        }
    }

    fun sendPlaybackAction(
        action: String,
        positionMs: Long? = null,
        extraData: JSONObject? = null,
    ): Boolean {
        val active = _session.value ?: return false
        val perms = active.settings
        val isControl = action in setOf("play", "pause", "toggle_play_pause", "seek", "next", "skip_next", "previous", "skip_previous", "play_index", "play_song", "jump_to")
        val isQueue = action in setOf("add_track", "play_next", "remove_track", "remove_song", "move_track")

        if (isControl && !_isHost.value && !perms.allowGuestsToControlPlayback) {
            emitMessage("Playback controls are locked by host")
            return false
        }
        if (isQueue && !_isHost.value && !perms.allowGuestsToAddTracks && !perms.allowGuestsToControlPlayback) {
            emitMessage("Queue edits are locked by host")
            return false
        }

        if (_isHost.value) {
            executeLocalOrHostAction(action, positionMs, extraData)
            onLocalPlaybackEvent()
            return true
        }

        val hasControlPermission = perms.allowGuestsToControlPlayback
        val effectivePid = if (hasControlPermission && !sessionHostId.isNullOrBlank()) sessionHostId!! else active.participantId

        // Optimistic instant local feedback for guests
        suppressLocalSyncUntil = System.currentTimeMillis() + 3000L
        when (action) {
            "toggle_play_pause" -> MusicPlayerState.setPlayPauseFromSync(!MusicPlayerState.isPlaying)
            "play", "resume" -> MusicPlayerState.setPlayPauseFromSync(true)
            "pause" -> MusicPlayerState.setPlayPauseFromSync(false)
            "seek" -> positionMs?.let { MusicPlayerState.seekFromSync(it) }
        }

        scope.launch {
            runCatching {
                if (_connectionMode.value == IShareConnectionMode.LAN) {
                    val hostAddr = lanHostAddress ?: return@launch
                    IShareLanClient.sendAction(
                        hostAddress = hostAddr,
                        participantId = if (hasControlPermission) "host" else active.participantId,
                        action = action,
                        positionMs = positionMs,
                        extraData = extraData,
                    )
                } else {
                    val curState = active.state ?: ISharePlaybackState(settings = perms)
                    val targetPlaying = when (action) {
                        "play", "resume" -> true
                        "pause" -> false
                        "toggle_play_pause" -> !curState.isPlaying
                        else -> curState.isPlaying
                    }
                    val targetPos = positionMs ?: curState.positionMs
                    val nextState = curState.copy(
                        positionMs = targetPos,
                        isPlaying = targetPlaying,
                        updatedAt = System.currentTimeMillis(),
                        settings = perms,
                        actionCommand = action,
                        actionPayload = extraData?.toString(),
                        actionId = UUID.randomUUID().toString().take(8),
                    )
                    IShareOnlineClient.updateState(active.code, effectivePid, nextState)
                }
            }.onSuccess { updated ->
                _session.value = updated.copy(
                    participantId = effectivePid,
                    settings = perms,
                )
                lastAppliedVersion = updated.stateVersion
            }.onFailure { err ->
                emitMessage(err.message ?: "Action failed")
            }
        }
        return true
    }

    private fun restartSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                val active = _session.value ?: break
                syncOnce(active)
                delay(if (_connectionMode.value == IShareConnectionMode.LAN) LAN_SYNC_MS else ONLINE_SYNC_MS)
            }
        }
    }

    private suspend fun syncOnce(session: IShareSession) {
        if (_connectionMode.value == IShareConnectionMode.LAN) {
            syncOnceLan(session)
        } else {
            syncOnceOnline(session)
        }
    }

    private suspend fun syncOnceLan(session: IShareSession) {
        val server = lanServer
        if (_isHost.value && server != null) {
            publishLocalStateLan()
            _session.value = server.buildSessionSnapshot("host")
            return
        }

        val hostAddr = lanHostAddress ?: return
        runCatching {
            val hasPerm = _session.value?.settings?.allowGuestsToControlPlayback == true
            val pid = if (hasPerm) "host" else (originalGuestParticipantId ?: session.participantId)
            IShareLanClient.getSession(hostAddr, pid)
        }.onSuccess { remote ->
            val hasPerm = remote.settings.allowGuestsToControlPlayback
            val effectiveId = if (hasPerm) "host" else (originalGuestParticipantId ?: session.participantId)
            val remoteQueue = remote.state?.queue?.takeIf { it.isNotEmpty() } ?: remote.queue
            _session.value = remote.copy(participantId = effectiveId, queue = remoteQueue)
            _sharedQueue.value = remoteQueue

            val remoteState = remote.state ?: return@onSuccess
            val now = System.currentTimeMillis()
            val curId = MusicPlayerState.currentTrack?.id
            if (now < suppressLocalSyncUntil) {
                if (pendingGuestSongId != null && remoteState.songId == pendingGuestSongId || curId == remoteState.songId) {
                    suppressLocalSyncUntil = 0L
                    pendingGuestSongId = null
                    lastAppliedVersion = remote.stateVersion
                } else {
                    return@onSuccess
                }
            }

            if ((remoteState.songId.isNotBlank() && curId != remoteState.songId) || remote.stateVersion > lastAppliedVersion) {
                applyRemoteState(remote)
                if (MusicPlayerState.currentTrack?.id == remoteState.songId) {
                    lastAppliedVersion = remote.stateVersion
                }
            } else {
                softCorrectPosition(remote)
            }
        }.onFailure { err ->
            Log.w(TAG, "LAN sync failed: ${err.message}")
        }
    }

    private suspend fun syncOnceOnline(session: IShareSession) {
        runCatching {
            IShareOnlineClient.getSession(session.code)
        }.onSuccess { remote ->
            if (sessionHostId.isNullOrBlank() && remote.controllerId.isNotBlank()) {
                sessionHostId = remote.controllerId
            }
            val preservedSettings = _session.value?.settings ?: session.settings
            val resolvedSettings = if (_isHost.value) preservedSettings else remote.settings
            val hasPerm = resolvedSettings.allowGuestsToControlPlayback
            val effectivePid = if (!_isHost.value) {
                if (hasPerm && !sessionHostId.isNullOrBlank()) sessionHostId!! else (originalGuestParticipantId ?: session.participantId)
            } else {
                session.participantId
            }
            val remoteQueue = remote.state?.queue?.takeIf { it.isNotEmpty() } ?: remote.queue
            val keptSession = remote.copy(
                participantId = effectivePid,
                settings = resolvedSettings,
                state = remote.state?.copy(settings = resolvedSettings, queue = remoteQueue) ?: remote.state,
                queue = remoteQueue,
            )
            _session.value = keptSession
            if (remoteQueue.isNotEmpty() || _sharedQueue.value.isNotEmpty()) {
                _sharedQueue.value = remoteQueue
            }

            val remoteState = remote.state ?: return@onSuccess
            val now = System.currentTimeMillis()

            if (_isHost.value) {
                val actionId = remoteState.actionId
                val cmd = remoteState.actionCommand
                if (!cmd.isNullOrBlank() && actionId != null && actionId != lastHandledActionId) {
                    lastHandledActionId = actionId
                    val json = remoteState.actionPayload?.let { runCatching { JSONObject(it) }.getOrNull() }
                    handleHostCommandFromGuest(cmd, remoteState.positionMs, json)
                    publishLocalStateOnline(keptSession)
                    return@onSuccess
                }

                val isRemoteNewer = remote.stateVersion > lastAppliedVersion
                val curId = MusicPlayerState.currentTrack?.id
                val diffSong = remoteState.songId.isNotBlank() && remoteState.songId != curId
                val diffPlay = remoteState.isPlaying != MusicPlayerState.isPlaying
                if (isRemoteNewer && (diffSong || diffPlay) && now > suppressLocalSyncUntil) {
                    applyRemoteState(keptSession)
                    lastAppliedVersion = remote.stateVersion
                    lastControllerPublishAt = now
                    return@onSuccess
                }

                if (now - lastControllerPublishAt >= CONTROLLER_PUBLISH_MS) {
                    publishLocalStateOnline(keptSession)
                    lastControllerPublishAt = now
                }
                return@onSuccess
            }

            val curId = MusicPlayerState.currentTrack?.id
            if (now < suppressLocalSyncUntil) {
                if (curId == remoteState.songId) {
                    suppressLocalSyncUntil = 0L
                    lastAppliedVersion = remote.stateVersion
                } else {
                    return@onSuccess
                }
            }

            if ((remoteState.songId.isNotBlank() && curId != remoteState.songId) || remote.stateVersion > lastAppliedVersion) {
                applyRemoteState(keptSession)
                if (MusicPlayerState.currentTrack?.id == remoteState.songId) {
                    lastAppliedVersion = remote.stateVersion
                }
            } else {
                softCorrectPosition(keptSession)
            }
        }.onFailure { err ->
            Log.w(TAG, "Online sync failed: ${err.message}")
        }
    }

    private fun handleHostCommandFromGuest(action: String, positionMs: Long?, extraData: JSONObject?) {
        val perms = lanServer?.settings ?: _session.value?.settings ?: IShareSettings()
        val isControl = action in setOf("play", "pause", "resume", "toggle_play_pause", "seek", "next", "skip_next", "previous", "skip_previous", "play_index", "play_song", "jump_to")
        val isQueue = action in setOf("add_track", "play_next", "remove_track", "remove_song", "move_track")
        if (isControl && !perms.allowGuestsToControlPlayback) return
        if (isQueue && !perms.allowGuestsToAddTracks && !perms.allowGuestsToControlPlayback) return

        runBlocking(Dispatchers.Main.immediate) {
            val overridePlay = when (action) {
                "play", "resume" -> true
                "pause" -> false
                "toggle_play_pause" -> !MusicPlayerState.isPlaying
                else -> null
            }
            executeLocalOrHostAction(action, positionMs, extraData)
            publishLocalStateLan(forceVersionBump = true, overrideIsPlaying = overridePlay)
        }
    }

    private fun executeLocalOrHostAction(action: String, positionMs: Long?, extraData: JSONObject?) {
        when (action) {
            "play", "resume" -> {
                if (positionMs != null && positionMs >= 0L) MusicPlayerState.seekFromSync(positionMs)
                if (!MusicPlayerState.isPlaying) MusicPlayerState.setPlayPauseFromSync(true)
            }
            "pause" -> if (MusicPlayerState.isPlaying) MusicPlayerState.setPlayPauseFromSync(false)
            "toggle_play_pause" -> MusicPlayerState.setPlayPauseFromSync(!MusicPlayerState.isPlaying)
            "seek" -> positionMs?.let { if (it >= 0L) MusicPlayerState.seekFromSync(it) }
            "next", "skip_next" -> MusicPlayerState.skipNext()
            "previous", "skip_previous" -> MusicPlayerState.skipPrevious(forcePrevious = true)
            "play_index", "jump_to" -> {
                val idx = extraData?.optInt("index", -1) ?: -1
                val sid = extraData?.optString("songId")
                MusicPlayerState.playFromIShareIndexOrId(idx, sid)
            }
            "play_song" -> {
                val sid = extraData?.optString("songId")?.ifBlank { extraData.optString("id") }
                if (!sid.isNullOrBlank()) {
                    val meta = ISharePlaybackState.fromJson(extraData).toMusicTrack()
                    MusicPlayerState.play(meta, expandPlayer = false)
                }
            }
            "add_track" -> extraData?.let { MusicPlayerState.addToQueue(listOf(ISharePlaybackState.fromJson(it).toMusicTrack())) }
            "play_next" -> extraData?.let { MusicPlayerState.playNext(ISharePlaybackState.fromJson(it).toMusicTrack()) }
            "remove_track", "remove_song" -> {
                val idx = extraData?.optInt("index", -1) ?: -1
                val sid = extraData?.optString("songId")?.ifBlank { extraData.optString("id") }
                MusicPlayerState.removeUpcomingFromIShare(idx, sid)
            }
            "move_track" -> {
                val from = extraData?.optInt("from", -1) ?: -1
                val to = extraData?.optInt("to", -1) ?: -1
                if (from >= 0 && to >= 0) {
                    MusicPlayerState.moveUpcomingTrack(from, to)
                }
            }
        }
    }

    private fun publishLocalStateLan(
        forceVersionBump: Boolean = false,
        overrideIsPlaying: Boolean? = null,
    ) {
        val server = lanServer ?: return
        val track = MusicPlayerState.currentTrack ?: return
        val upcomingQueue = MusicPlayerState.upcomingIShareQueue()
        _sharedQueue.value = upcomingQueue

        val cur = server.playbackState
        val isPlaying = overrideIsPlaying ?: MusicPlayerState.isPlaying
        val pos = MusicPlayerState.positionMs
        val isTrackChange = cur == null || cur.songId != track.id
        val isPlayChange = cur != null && cur.isPlaying != isPlaying
        val isQueueChange = cur == null || cur.queue != upcomingQueue
        val expectedPos = if (cur != null && cur.isPlaying) cur.positionMs + (System.currentTimeMillis() - cur.updatedAt) else cur?.positionMs ?: 0L
        val isHardSeek = cur != null && abs(pos - expectedPos) > 1500L

        if (forceVersionBump || isTrackChange || isPlayChange || isQueueChange || isHardSeek || abs(pos - (cur?.positionMs ?: 0L)) > 450L) {
            val built = buildHostPlaybackState(track, server.settings)
            server.playbackState = if (overrideIsPlaying != null) built.copy(isPlaying = overrideIsPlaying) else built
            if (forceVersionBump || isTrackChange || isPlayChange || isQueueChange || isHardSeek) {
                server.stateVersion++
            }
            _session.value = server.buildSessionSnapshot("host")
        }
    }

    private suspend fun publishLocalStateOnline(session: IShareSession) {
        val track = MusicPlayerState.currentTrack ?: return
        val upcomingQueue = MusicPlayerState.upcomingIShareQueue()
        _sharedQueue.value = upcomingQueue
        val payload = buildHostPlaybackState(track, session.settings)

        runCatching {
            IShareOnlineClient.updateState(session.code, session.participantId, payload)
        }.onSuccess { updated ->
            val preserved = _session.value?.settings ?: session.settings
            _session.value = updated.copy(
                participantId = session.participantId,
                settings = preserved,
                state = updated.state?.copy(settings = preserved, queue = upcomingQueue) ?: payload,
                queue = upcomingQueue,
            )
            lastAppliedVersion = updated.stateVersion
            lastControllerPublishAt = System.currentTimeMillis()
        }.onFailure { err ->
            Log.w(TAG, "publishLocalStateOnline failed: ${err.message}")
        }
    }

    private fun buildHostPlaybackState(track: MusicTrack, settings: IShareSettings): ISharePlaybackState {
        val upcomingQueue = MusicPlayerState.upcomingIShareQueue()
        return ISharePlaybackState(
            songId = track.id,
            title = track.title,
            artists = track.artists.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            thumbnailUrl = track.thumbnailUrl,
            positionMs = MusicPlayerState.positionMs,
            isPlaying = MusicPlayerState.isPlaying,
            durationMs = MusicPlayerState.durationMs.takeIf { it > 0L } ?: (track.durationSec * 1000L).coerceAtLeast(0L),
            updatedAt = System.currentTimeMillis(),
            settings = settings,
            queue = upcomingQueue,
            totalQueueSize = MusicPlayerState.queue.size,
        )
    }

    private fun buildLanQueueJson(): List<JSONObject> =
        MusicPlayerState.upcomingIShareQueue().mapIndexed { idx, item ->
            JSONObject()
                .put("index", idx)
                .put("id", item.songId)
                .put("title", item.title)
                .put("artist", item.artist)
                .put("thumbnailUrl", item.thumbnailUrl.orEmpty())
                .put("isCurrent", false)
        }

    private fun applyRemoteState(remoteSession: IShareSession) {
        val remoteState = remoteSession.state ?: return
        val remoteQueue = remoteState.queue.takeIf { it.isNotEmpty() } ?: remoteSession.queue
        val queueTracks = remoteQueue.map { it.toMusicTrack() }
        val targetPos = expectedPosition(remoteSession)
        val curId = MusicPlayerState.currentTrack?.id
        val now = System.currentTimeMillis()

        if (remoteState.songId.isNotBlank() && curId != remoteState.songId) {
            val stale = (now - lastSongLoadAttempt) > 4000L
            if (loadingSongId != remoteState.songId || stale) {
                loadingSongId = remoteState.songId
                lastSongLoadAttempt = now
                suppressLocalSyncUntil = now + APPLY_GRACE_MS
                MusicPlayerState.playSyncedFromIShare(
                    track = remoteState.toMusicTrack(),
                    upcomingQueue = queueTracks,
                    positionMs = targetPos,
                    shouldPlay = remoteState.isPlaying,
                )
            }
            return
        }

        loadingSongId = null
        MusicPlayerState.syncUpcomingQueueFromIShare(queueTracks)
        softCorrectPosition(remoteSession)
        MusicPlayerState.setPlayPauseFromSync(remoteState.isPlaying)
    }

    private fun softCorrectPosition(remoteSession: IShareSession) {
        val now = System.currentTimeMillis()
        if (now < suppressLocalSyncUntil) return
        val remoteState = remoteSession.state ?: return
        if (MusicPlayerState.currentTrack?.id != remoteState.songId) return
        if (MusicPlayerState.isPlaying != remoteState.isPlaying) {
            MusicPlayerState.setPlayPauseFromSync(remoteState.isPlaying)
        }
        if (!remoteState.isPlaying) return

        val target = expectedPosition(remoteSession)
        val isLan = _connectionMode.value == IShareConnectionMode.LAN
        val hardThreshold = if (isLan) 3500L else 5000L
        val softThreshold = if (isLan) 800L else 1200L
        val diff = MusicPlayerState.positionMs - target
        val absDiff = abs(diff)

        when {
            absDiff > hardThreshold -> {
                suppressLocalSyncUntil = now + APPLY_GRACE_MS
                MusicPlayerState.seekFromSync(target)
                MusicPlayerState.setRemotePlaybackSpeed(1f)
            }
            absDiff > softThreshold -> {
                val speed = if (diff < 0) 1.04f else 0.96f
                MusicPlayerState.setRemotePlaybackSpeed(speed)
            }
            else -> {
                MusicPlayerState.setRemotePlaybackSpeed(1f)
            }
        }
    }

    private fun expectedPosition(session: IShareSession): Long {
        val state = session.state ?: return 0L
        val serverAge = (session.serverNow - state.updatedAt).coerceAtLeast(0L)
        return if (state.isPlaying) state.positionMs + serverAge else state.positionMs
    }

    private fun MusicPlayerState.upcomingIShareQueue(): List<IShareQueueItem> {
        val cur = queueIndex.coerceAtLeast(0)
        if (queue.isEmpty() || cur + 1 >= queue.size) return emptyList()
        return queue.subList(cur + 1, minOf(queue.size, cur + 31)).map(IShareQueueItem::fromMusicTrack)
    }
}
