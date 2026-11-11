package com.darkxvenom.itune.ui.ishare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.formatMusicDuration
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.ishare.DiscoveredLanHost
import com.darkxvenom.itune.ishare.IShareConnectionMode
import com.darkxvenom.itune.ishare.IShareParticipant
import com.darkxvenom.itune.ishare.IShareQueueItem
import com.darkxvenom.itune.ishare.IShareSession
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.LocalAppBackdrop
import com.darkxvenom.itune.ui.artist.components.LocalLiquidGlassEnabled
import com.darkxvenom.itune.ui.artist.components.LocalReduceDynamicBlur
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberLayerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberSnapshotBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.snapshotBackdropSource
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.layerBackdrop
import com.darkxvenom.itune.ui.artist.components.isGlassSupported
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.library.LibraryGlassCardContainer
import com.darkxvenom.itune.ui.player.IosCapsuleSeekSlider
import com.darkxvenom.itune.ui.player.IosVolumeSlider
import com.darkxvenom.itune.ui.settings.AppleGlassSwitch
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import org.json.JSONObject
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private val IShareSharePlayGreen = Color(0xFF30D158)
private val IShareLanBlue = Color(0xFF0A84FF)

/**
 * Apple iPhone-styled Dark Liquid Glass Listen Together ("iShare") screen.
 * Supports AirBeats Cloud rooms (pairing codes) and local Wi-Fi / Hotspot LAN hosting & discovery.
 */
@Composable
fun IShareScreen(
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        IShareSyncManager.start(context)
        IShareSyncManager.refreshLocalIp()
    }

    val activeSession by IShareSyncManager.session.collectAsStateWithLifecycle()
    val isHost by IShareSyncManager.isHost.collectAsStateWithLifecycle()
    val connMode by IShareSyncManager.connectionMode.collectAsStateWithLifecycle()
    val displayName by IShareSyncManager.displayName.collectAsStateWithLifecycle()
    val sharedQueue by IShareSyncManager.sharedQueue.collectAsStateWithLifecycle()
    val discoveredHosts by IShareSyncManager.discoveredHosts.collectAsStateWithLifecycle()
    val localIp by IShareSyncManager.localIp.collectAsStateWithLifecycle()
    val isBusy by IShareSyncManager.isBusy.collectAsStateWithLifecycle()
    val isScanningLan by IShareSyncManager.isScanningLan.collectAsStateWithLifecycle()
    val statusMessage by IShareSyncManager.message.collectAsStateWithLifecycle()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        IShareSyncManager.clearMessage()
    }

    var hostOnlineMode by rememberSaveable { mutableStateOf(true) }
    var joinOnlineMode by rememberSaveable { mutableStateOf(true) }
    var joinCodeInput by rememberSaveable { mutableStateOf("") }
    var lanHostInput by rememberSaveable { mutableStateOf("") }
    var lanPortInput by rememberSaveable { mutableStateOf("8765") }
    var allowGuestsControl by rememberSaveable { mutableStateOf(false) }
    var allowGuestsQueue by rememberSaveable { mutableStateOf(true) }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditPortDialog by remember { mutableStateOf(false) }
    var kickTarget by remember { mutableStateOf<IShareParticipant?>(null) }

    BackHandler(enabled = kickTarget == null && !showEditNameDialog && !showEditPortDialog) {
        onBack()
    }

    val appBackdrop = rememberLayerBackdrop()
    val pageBackdrop = rememberSnapshotBackdrop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val overscrollState = rememberSpringOverscrollState()

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 90
        }
    }

    val glassActive = isGlassSupported() && settingsState.liquidGlassChrome
    val currentTrack = MusicPlayerState.currentTrack
    val contentPadding = PaddingValues(
        top = topBarContentPadding() + 6.dp,
        bottom = if (hasMiniPlayer) 210.dp else 140.dp,
    )

    fun copyToClipboard(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "Copied: $value", Toast.LENGTH_SHORT).show()
    }

    fun sharePairingInvite(session: IShareSession, isLan: Boolean) {
        val text = if (isLan) {
            "Join my iShare LAN music session!\nOpen http://${session.code} or enter ${session.code} in iTune / AirBeats."
        } else {
            "Listen Together with me on iShare!\nPairing Code: ${session.code}\n(Compatible with iTune & AirBeats)"
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    "Share iShare Invite",
                ),
            )
        }
    }

    ITubeArtistTheme(darkTheme = true) {
        CompositionLocalProvider(
            LocalAppBackdrop provides appBackdrop,
            LocalLiquidGlassEnabled provides glassActive,
            LocalReduceDynamicBlur provides !settingsState.liquidGlassChrome,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(AppleOledBlack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .snapshotBackdropSource(pageBackdrop, floor = AppleOledBlack)
                        .hazeSource(state = hazeState),
                ) {
                    IShareBackdrop(
                        isConnected = activeSession != null,
                        isLan = connMode == IShareConnectionMode.LAN,
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(appBackdrop),
                    )

                    LazyColumn(
                        state = listState,
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .springOverscroll(overscrollState),
                    ) {
                        item(key = "ishare_header") {
                            IShareHeroHeader(
                                displayName = displayName,
                                activeSession = activeSession,
                                isLan = connMode == IShareConnectionMode.LAN,
                                onEditName = {
                                    haptics.play(Haptic.Tap)
                                    showEditNameDialog = true
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }

                        val currentRoom = activeSession
                        if (currentRoom != null) {
                            item(key = "ishare_active_room") {
                                val roomIsLan = connMode == IShareConnectionMode.LAN
                                ActivePairingHeroCard(
                                    session = currentRoom,
                                    isHost = isHost,
                                    isLan = roomIsLan,
                                    useBackdropGlass = glassActive,
                                    onCopyCode = {
                                        haptics.play(Haptic.Select)
                                        copyToClipboard("iShare Pairing Code", currentRoom.code)
                                    },
                                    onShare = {
                                        haptics.play(Haptic.Tap)
                                        sharePairingInvite(currentRoom, roomIsLan)
                                    },
                                    onOpenBrowser = {
                                        haptics.play(Haptic.Tap)
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("http://${currentRoom.code}")),
                                            )
                                        }.onFailure {
                                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onLeave = {
                                        haptics.play(Haptic.Tap)
                                        IShareSyncManager.leaveSession()
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }

                            item(key = "ishare_now_playing") {
                                val canControl = isHost || currentRoom.settings.allowGuestsToControlPlayback
                                SharedNowPlayingCard(
                                    session = currentRoom,
                                    controlsAllowed = canControl,
                                    useBackdropGlass = glassActive,
                                    onPrevious = {
                                        haptics.play(Haptic.SkipPrevious)
                                        IShareSyncManager.sendPlaybackAction("skip_previous")
                                    },
                                    onPlayPause = {
                                        haptics.play(Haptic.Tap)
                                        IShareSyncManager.sendPlaybackAction("toggle_play_pause")
                                    },
                                    onNext = {
                                        haptics.play(Haptic.SkipNext)
                                        IShareSyncManager.sendPlaybackAction("skip_next")
                                    },
                                    onSeek = { targetMs ->
                                        IShareSyncManager.sendPlaybackAction("seek", positionMs = targetMs)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }

                            item(key = "ishare_devices_list") {
                                ConnectedDevicesCard(
                                    participants = currentRoom.participantList,
                                    myParticipantId = if (isHost) currentRoom.controllerId.ifBlank { "host" } else currentRoom.participantId,
                                    hostName = currentRoom.hostName,
                                    isHost = isHost,
                                    useBackdropGlass = glassActive,
                                    onKick = { p ->
                                        haptics.play(Haptic.Tap)
                                        kickTarget = p
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }

                            item(key = "ishare_room_permissions") {
                                RoomPermissionsGlassCard(
                                    settings = currentRoom.settings,
                                    isHost = isHost,
                                    useBackdropGlass = glassActive,
                                    onUpdate = { allowPb, allowQ ->
                                        haptics.play(Haptic.ToggleOn)
                                        IShareSyncManager.updateSettings(allowPb, allowQ)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }

                            if (sharedQueue.isNotEmpty()) {
                                item(key = "ishare_shared_queue") {
                                    val canControl = isHost || currentRoom.settings.allowGuestsToControlPlayback
                                    val canEditQueue = isHost ||
                                        currentRoom.settings.allowGuestsToAddTracks ||
                                        currentRoom.settings.allowGuestsToControlPlayback
                                    SharedQueuePreviewCard(
                                        queue = sharedQueue,
                                        controlsAllowed = canControl,
                                        queueEditable = canEditQueue,
                                        useBackdropGlass = glassActive,
                                        onPlayItem = { idx, songId ->
                                            haptics.play(Haptic.Tap)
                                            IShareSyncManager.sendPlaybackAction(
                                                "play_index",
                                                extraData = JSONObject().put("index", idx).put("songId", songId),
                                            )
                                        },
                                        onRemoveItem = { idx, songId ->
                                            haptics.play(Haptic.Tap)
                                            IShareSyncManager.sendPlaybackAction(
                                                "remove_track",
                                                extraData = JSONObject().put("index", idx).put("songId", songId),
                                            )
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        } else {
                            item(key = "ishare_host_card") {
                                HostSessionCard(
                                    onlineMode = hostOnlineMode,
                                    onModeChange = { hostOnlineMode = it },
                                    currentTrackTitle = currentTrack?.title,
                                    currentTrackArtist = currentTrack?.artists,
                                    localIp = localIp,
                                    portText = lanPortInput,
                                    onPortClick = {
                                        haptics.play(Haptic.Tap)
                                        showEditPortDialog = true
                                    },
                                    allowGuestsControl = allowGuestsControl,
                                    onAllowControlChange = { allowGuestsControl = it },
                                    allowGuestsQueue = allowGuestsQueue,
                                    onAllowQueueChange = { allowGuestsQueue = it },
                                    isBusy = isBusy,
                                    useBackdropGlass = glassActive,
                                    onStartSession = {
                                        haptics.play(Haptic.Select)
                                        focusManager.clearFocus()
                                        if (hostOnlineMode) {
                                            IShareSyncManager.createOnlineSession(
                                                allowGuestsToControlPlayback = allowGuestsControl,
                                                allowGuestsToAddTracks = allowGuestsQueue,
                                            )
                                        } else {
                                            val port = lanPortInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 8765
                                            IShareSyncManager.startLanHost(
                                                port = port,
                                                allowGuestsToControlPlayback = allowGuestsControl,
                                                allowGuestsToAddTracks = allowGuestsQueue,
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }

                            item(key = "ishare_join_card") {
                                JoinSessionCard(
                                    onlineMode = joinOnlineMode,
                                    onModeChange = { joinOnlineMode = it },
                                    inputValue = if (joinOnlineMode) joinCodeInput else lanHostInput,
                                    onInputChange = { next ->
                                        if (joinOnlineMode) joinCodeInput = next.uppercase()
                                        else lanHostInput = next
                                    },
                                    discoveredHosts = discoveredHosts,
                                    isScanning = isScanningLan,
                                    isBusy = isBusy,
                                    useBackdropGlass = glassActive,
                                    onScanLan = {
                                        haptics.play(Haptic.Tap)
                                        focusManager.clearFocus()
                                        val port = lanPortInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 8765
                                        IShareSyncManager.scanLanHosts(port = port)
                                    },
                                    onSelectLanHost = { host ->
                                        haptics.play(Haptic.Select)
                                        focusManager.clearFocus()
                                        lanHostInput = host.address
                                        IShareSyncManager.joinLanSession(host.address)
                                    },
                                    onJoin = {
                                        haptics.play(Haptic.Select)
                                        focusManager.clearFocus()
                                        if (joinOnlineMode) {
                                            IShareSyncManager.joinOnlineSession(joinCodeInput)
                                        } else {
                                            IShareSyncManager.joinLanSession(lanHostInput)
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }

                TopFadeBlur(
                    hazeState = hazeState,
                    pageColor = AppleOledBlack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                FrostedTopBar(
                    transparentBackdrop = true,
                    artworkPageChrome = true,
                    backButtonHazeState = hazeState,
                    onBack = {
                        haptics.play(Haptic.Tap)
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                    centerContent = {
                        AnimatedVisibility(
                            visible = isScrolled,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180)),
                        ) {
                            FrostedTopBarTitlePill(hazeState = hazeState) {
                                Text(
                                    text = "iShare",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                haptics.play(Haptic.Tap)
                                IShareSyncManager.refreshLocalIp()
                                if (activeSession == null && !joinOnlineMode) {
                                    val port = lanPortInput.toIntOrNull()?.coerceIn(1024, 65535) ?: 8765
                                    IShareSyncManager.scanLanHosts(port)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh Network",
                                tint = if (activeSession != null) IShareSharePlayGreen else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )

                BottomFadeScrim(
                    withMiniPlayer = hasMiniPlayer,
                    pageColor = AppleOledBlack,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showEditNameDialog) {
        IShareTextInputDialog(
            title = "Device Display Name",
            subtitle = "This name is shown to friends and AirBeats devices in the room.",
            initialValue = displayName,
            placeholder = "Enter name",
            keyboardType = KeyboardType.Text,
            onSave = { updated ->
                IShareSyncManager.setDisplayName(updated)
                SettingsStore.setString(context, SettingsStore.KEY_DISPLAY_NAME, updated)
                showEditNameDialog = false
            },
            onDismiss = { showEditNameDialog = false },
        )
    }

    if (showEditPortDialog) {
        IShareTextInputDialog(
            title = "LAN Server Port",
            subtitle = "Default is 8765 for automatic AirBeats & iShare Wi-Fi discovery.",
            initialValue = lanPortInput,
            placeholder = "8765",
            keyboardType = KeyboardType.Number,
            onSave = { updated ->
                val p = updated.toIntOrNull()
                if (p != null && p in 1024..65535) {
                    lanPortInput = p.toString()
                    showEditPortDialog = false
                } else {
                    Toast.makeText(context, "Enter a port between 1024 and 65535", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showEditPortDialog = false },
        )
    }

    kickTarget?.let { participant ->
        IShareConfirmDialog(
            title = "Remove Device",
            message = "Disconnect ${participant.name} from this iShare session?",
            confirmLabel = "Remove",
            onConfirm = {
                IShareSyncManager.kickParticipant(participant.id)
                kickTarget = null
            },
            onDismiss = { kickTarget = null },
        )
    }
}

@Composable
private fun IShareBackdrop(
    isConnected: Boolean,
    isLan: Boolean,
    modifier: Modifier = Modifier,
) {
    val primaryGlow = when {
        isConnected && isLan -> Color(0xFF0A84FF)
        isConnected -> Color(0xFF30D158)
        else -> Color(0xFFFA2D48)
    }
    Canvas(modifier = modifier.background(AppleOledBlack)) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryGlow.copy(alpha = 0.20f),
                    primaryGlow.copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.20f, size.height * 0.10f),
                radius = size.maxDimension * 0.52f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF5856D6).copy(alpha = 0.16f),
                    Color(0xFF1A1742).copy(alpha = 0.05f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.85f, size.height * 0.22f),
                radius = size.maxDimension * 0.54f,
            ),
        )
    }
}

@Composable
private fun IShareHeroHeader(
    displayName: String,
    activeSession: IShareSession?,
    isLan: Boolean,
    onEditName: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "isharePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ishareScale",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(if (activeSession != null) pulseScale else 1f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                if (activeSession != null) {
                                    listOf(IShareSharePlayGreen, Color(0xFF0A84FF))
                                } else {
                                    listOf(AppleMusicRed, Color(0xFFFF5E7E))
                                },
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SfIcons.Radio,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "iShare",
                        color = Color.White,
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                    )
                    Text(
                        text = if (activeSession != null) {
                            if (isLan) "Local Wi-Fi Session Active" else "Cloud SharePlay Active"
                        } else {
                            "Listen Together • Cloud & Wi-Fi LAN"
                        },
                        color = if (activeSession != null) IShareSharePlayGreen else AppleTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                .clickable(onClick = onEditName)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMemojiAvatar(size = 22.dp, onClick = onEditName)
            Spacer(Modifier.width(8.dp))
            Text(
                text = displayName,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Edit Display Name",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
private fun ActivePairingHeroCard(
    session: IShareSession,
    isHost: Boolean,
    isLan: Boolean,
    useBackdropGlass: Boolean,
    onCopyCode: () -> Unit,
    onShare: () -> Unit,
    onOpenBrowser: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LibraryGlassCardContainer(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        accentTint = (if (isLan) IShareLanBlue else IShareSharePlayGreen).copy(alpha = 0.25f),
        useBackdropGlass = useBackdropGlass,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(IShareSharePlayGreen),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = buildString {
                        append(if (isHost) "HOSTING" else "PAIRED GUEST")
                        append(" • ")
                        append(if (isLan) "LOCAL WI-FI" else "ONLINE PAIRING CODE")
                    },
                    color = IShareSharePlayGreen,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onCopyCode)
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = session.code,
                        color = Color.White,
                        fontSize = if (isLan) 22.sp else 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = if (isLan) 0.8.sp else 4.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (isLan) "Tap to copy host address" else "Tap to copy pairing code for iTune & AirBeats",
                        color = AppleTextSecondary,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IShareActionPill(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy",
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White,
                    onClick = onCopyCode,
                    modifier = Modifier.weight(1f),
                )
                IShareActionPill(
                    icon = SfIcons.Share,
                    label = "Share",
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White,
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                )
            }

            if (isLan) {
                Spacer(Modifier.height(10.dp))
                IShareActionPill(
                    icon = Icons.Rounded.OpenInBrowser,
                    label = "Open LAN Web Player (${session.code})",
                    containerColor = IShareLanBlue.copy(alpha = 0.20f),
                    contentColor = Color(0xFF70B8FF),
                    onClick = onOpenBrowser,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            IShareActionPill(
                icon = null,
                label = if (isHost) "End iShare Session" else "Disconnect Session",
                containerColor = AppleMusicRed.copy(alpha = 0.22f),
                contentColor = AppleMusicRed,
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SharedNowPlayingCard(
    session: IShareSession,
    controlsAllowed: Boolean,
    useBackdropGlass: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = MusicPlayerState.currentTrack
    val state = session.state
    val title = track?.title ?: state?.title?.takeIf { it.isNotBlank() } ?: "Waiting for track..."
    val artist = track?.artists
        ?: state?.artists?.joinToString(", ")?.takeIf { it.isNotBlank() }
        ?: session.hostName
    val thumb = track?.thumbnailUrl ?: state?.thumbnailUrl
    val isPlaying = if (track != null) MusicPlayerState.isPlaying else (state?.isPlaying == true)
    val durationMs = if (track != null && MusicPlayerState.durationMs > 0L) {
        MusicPlayerState.durationMs
    } else {
        state?.durationMs?.coerceAtLeast(1L) ?: 1L
    }
    val positionMs = if (track != null) MusicPlayerState.positionMs else (state?.positionMs ?: 0L)
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosMs by remember { mutableLongStateOf(0L) }
    val displayPosMs = if (isSeeking) seekPosMs else positionMs.coerceIn(0L, durationMs)
    val remainingMs = (durationMs - displayPosMs).coerceAtLeast(0L)

    LibraryGlassCardContainer(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        useBackdropGlass = useBackdropGlass,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!thumb.isNullOrBlank()) {
                        AsyncImage(
                            model = thumb,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = SfIcons.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = artist,
                        color = AppleTextSecondary,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!controlsAllowed) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Host controls playback",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            IosCapsuleSeekSlider(
                value = displayPosMs.toFloat(),
                valueRange = 0f..durationMs.toFloat(),
                isSeeking = isSeeking,
                enabled = controlsAllowed,
                onSeekStart = { targetVal ->
                    isSeeking = true
                    seekPosMs = targetVal.toLong().coerceIn(0L, durationMs)
                },
                onSeekChange = { targetVal ->
                    seekPosMs = targetVal.toLong().coerceIn(0L, durationMs)
                },
                onSeekEnd = { targetVal ->
                    val targetPos = targetVal.toLong().coerceIn(0L, durationMs)
                    seekPosMs = targetPos
                    onSeek(targetPos)
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatMusicDuration(displayPosMs / 1000L).ifBlank { "0:00" },
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (durationMs > 1L) {
                        "-${formatMusicDuration(remainingMs / 1000L).ifBlank { "0:00" }}"
                    } else {
                        "--:--"
                    },
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevious,
                    enabled = controlsAllowed,
                    modifier = Modifier.size(56.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = "Previous",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = if (controlsAllowed) 0.96f else 0.35f)),
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(
                    onClick = onPlayPause,
                    enabled = controlsAllowed,
                    modifier = Modifier.size(60.dp),
                ) {
                    Image(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = if (controlsAllowed) 1f else 0.38f)),
                        modifier = Modifier.size(42.dp),
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = controlsAllowed,
                    modifier = Modifier.size(56.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = "Next",
                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = if (controlsAllowed) 0.96f else 0.35f)),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            IosVolumeSlider(
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConnectedDevicesCard(
    participants: List<IShareParticipant>,
    myParticipantId: String,
    hostName: String,
    isHost: Boolean,
    useBackdropGlass: Boolean,
    onKick: (IShareParticipant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownList = participants.ifEmpty {
        listOf(IShareParticipant(id = "host", name = hostName, isHost = true))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Devices,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "CONNECTED DEVICES (${shownList.size})",
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.65.sp,
            )
        }

        LibraryGlassCardContainer(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            useBackdropGlass = useBackdropGlass,
        ) {
            shownList.forEachIndexed { idx, device ->
                val isYou = device.id == myParticipantId || (isHost && device.isHost)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (device.isHost) AppleMusicRed.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.10f),
                            )
                            .border(
                                width = 0.6.dp,
                                color = if (device.isHost) AppleMusicRed.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = null,
                            tint = if (device.isHost) AppleMusicRed else Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = device.name,
                                color = Color.White,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isYou) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "You",
                                    color = IShareSharePlayGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Text(
                            text = if (device.isHost) "Session Host" else "Synced Listener",
                            color = AppleTextSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    if (isHost && !device.isHost) {
                        IconButton(
                            onClick = { onKick(device) },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonRemove,
                                contentDescription = "Remove ${device.name}",
                                tint = AppleMusicRed,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (device.isHost) AppleMusicRed.copy(alpha = 0.20f)
                                    else Color.White.copy(alpha = 0.09f),
                                )
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (device.isHost) "HOST" else "SYNCED",
                                color = if (device.isHost) AppleMusicRed else Color.White.copy(alpha = 0.72f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                }
                if (idx < shownList.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 62.dp),
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.11f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomPermissionsGlassCard(
    settings: com.darkxvenom.itune.ishare.IShareSettings,
    isHost: Boolean,
    useBackdropGlass: Boolean,
    onUpdate: (allowPlayback: Boolean, allowTracks: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "ROOM PERMISSIONS",
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.65.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
        )
        LibraryGlassCardContainer(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            useBackdropGlass = useBackdropGlass,
        ) {
            IShareSwitchRow(
                title = "Guest Playback Controls",
                subtitle = if (isHost) "Let paired friends play, pause, seek & skip" else if (settings.allowGuestsToControlPlayback) "Enabled by host" else "Locked to host",
                checked = settings.allowGuestsToControlPlayback,
                enabled = isHost,
                onCheckedChange = { next -> onUpdate(next, settings.allowGuestsToAddTracks) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.11f),
            )
            IShareSwitchRow(
                title = "Guest Queue Edits",
                subtitle = if (isHost) "Let friends add tracks to Up Next" else if (settings.allowGuestsToAddTracks) "Allowed by host" else "Host only",
                checked = settings.allowGuestsToAddTracks,
                enabled = isHost,
                onCheckedChange = { next -> onUpdate(settings.allowGuestsToControlPlayback, next) },
            )
        }
    }
}

@Composable
private fun SharedQueuePreviewCard(
    queue: List<IShareQueueItem>,
    controlsAllowed: Boolean,
    queueEditable: Boolean,
    useBackdropGlass: Boolean,
    onPlayItem: (index: Int, songId: String) -> Unit,
    onRemoveItem: (index: Int, songId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = queue.take(8)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "UP NEXT (${queue.size})",
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.65.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
        )
        LibraryGlassCardContainer(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            useBackdropGlass = useBackdropGlass,
        ) {
            shown.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = controlsAllowed) {
                            onPlayItem(idx, item.songId)
                        }
                        .padding(start = 14.dp, end = if (queueEditable) 6.dp else 14.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.artist.isNotBlank()) {
                            Text(
                                text = item.artist,
                                color = AppleTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (queueEditable) {
                        IconButton(
                            onClick = { onRemoveItem(idx, item.songId) },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove from queue",
                                tint = Color.White.copy(alpha = 0.48f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                if (idx < shown.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.11f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HostSessionCard(
    onlineMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    currentTrackTitle: String?,
    currentTrackArtist: String?,
    localIp: String?,
    portText: String,
    onPortClick: () -> Unit,
    allowGuestsControl: Boolean,
    onAllowControlChange: (Boolean) -> Unit,
    allowGuestsQueue: Boolean,
    onAllowQueueChange: (Boolean) -> Unit,
    isBusy: Boolean,
    useBackdropGlass: Boolean,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "HOST A SESSION",
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.65.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
        )
        LibraryGlassCardContainer(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            accentTint = AppleMusicRed.copy(alpha = 0.18f),
            useBackdropGlass = useBackdropGlass,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IShareSegmentedModeSwitch(
                    leftLabel = "Online Code",
                    rightLabel = "Wi-Fi LAN",
                    leftSelected = onlineMode,
                    onSelectLeft = onModeChange,
                )

                Text(
                    text = if (onlineMode) {
                        "Creates a shareable 6-character pairing code via AirBeats servers for remote listening."
                    } else {
                        "Hosts locally on ${localIp ?: "your Wi-Fi IP"}:$portText for zero-latency AirBeats & browser playback."
                    },
                    color = AppleTextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                )

                if (!onlineMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onPortClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("LAN Port", color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                        Text("$portText  ›", color = IShareLanBlue, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
                ) {
                    IShareSwitchRow(
                        title = "Allow Playback Control",
                        subtitle = "Guests can play, pause & skip tracks",
                        checked = allowGuestsControl,
                        enabled = true,
                        onCheckedChange = onAllowControlChange,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 14.dp),
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.09f),
                    )
                    IShareSwitchRow(
                        title = "Allow Queue Additions",
                        subtitle = "Guests can suggest songs to Up Next",
                        checked = allowGuestsQueue,
                        enabled = true,
                        onCheckedChange = onAllowQueueChange,
                    )
                }

                val hasSong = !currentTrackTitle.isNullOrBlank()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = SfIcons.MusicNote,
                        contentDescription = null,
                        tint = if (hasSong) AppleMusicRed else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (hasSong) {
                            "Ready: $currentTrackTitle • ${currentTrackArtist.orEmpty()}"
                        } else {
                            "Play any song first to start hosting"
                        },
                        color = if (hasSong) Color.White else AppleTextSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(if (hasSong && !isBusy) Color.White else Color.White.copy(alpha = 0.16f))
                        .clickable(enabled = hasSong && !isBusy, onClick = onStartSession),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(
                            text = if (onlineMode) "Get Pairing Code" else "Start Wi-Fi LAN Host",
                            color = if (hasSong) Color.Black else Color.White.copy(alpha = 0.45f),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinSessionCard(
    onlineMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    inputValue: String,
    onInputChange: (String) -> Unit,
    discoveredHosts: List<DiscoveredLanHost>,
    isScanning: Boolean,
    isBusy: Boolean,
    useBackdropGlass: Boolean,
    onScanLan: () -> Unit,
    onSelectLanHost: (DiscoveredLanHost) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "PAIR & JOIN A DEVICE",
            color = Color.White.copy(alpha = 0.56f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.65.sp,
            modifier = Modifier.padding(start = 14.dp, bottom = 7.dp),
        )
        LibraryGlassCardContainer(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
            accentTint = IShareLanBlue.copy(alpha = 0.16f),
            useBackdropGlass = useBackdropGlass,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IShareSegmentedModeSwitch(
                    leftLabel = "Pairing Code",
                    rightLabel = "Nearby Wi-Fi",
                    leftSelected = onlineMode,
                    onSelectLeft = onModeChange,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (onlineMode) Icons.Rounded.Public else Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = AppleTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (inputValue.isEmpty()) {
                            Text(
                                text = if (onlineMode) "Enter Pairing Code (e.g. K8P4QX)" else "Host IP (e.g. 192.168.1.42:8765)",
                                color = Color.White.copy(alpha = 0.40f),
                                fontSize = 14.5.sp,
                            )
                        }
                        BasicTextField(
                            value = inputValue,
                            onValueChange = onInputChange,
                            singleLine = true,
                            cursorBrush = SolidColor(AppleMusicRed),
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = if (onlineMode) FontFamily.Monospace else FontFamily.Default,
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = if (onlineMode) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onJoin() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (!onlineMode) {
                    IShareActionPill(
                        icon = Icons.Rounded.Wifi,
                        label = if (isScanning) "Scanning Local Wi-Fi..." else "Scan Nearby AirBeats & iShare Devices",
                        containerColor = IShareLanBlue.copy(alpha = 0.22f),
                        contentColor = Color(0xFF70B8FF),
                        onClick = { if (!isScanning) onScanLan() },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (discoveredHosts.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                        ) {
                            discoveredHosts.forEachIndexed { idx, host ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectLanHost(host) }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Smartphone,
                                        contentDescription = null,
                                        tint = IShareLanBlue,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = host.hostName,
                                            color = Color.White,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = "${host.address} • ${host.participants} active",
                                            color = AppleTextSecondary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Text(
                                        text = "Connect ›",
                                        color = IShareLanBlue,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (idx < discoveredHosts.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 42.dp),
                                        thickness = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.10f),
                                    )
                                }
                            }
                        }
                    }
                }

                val canJoin = inputValue.trim().isNotEmpty() && !isBusy
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(if (canJoin) AppleMusicRed else Color.White.copy(alpha = 0.14f))
                        .clickable(enabled = canJoin, onClick = onJoin),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (onlineMode) "Join with Pairing Code" else "Connect to LAN Host",
                        color = if (canJoin) Color.White else Color.White.copy(alpha = 0.45f),
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun IShareSegmentedModeSwitch(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onSelectLeft: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(2.5.dp),
    ) {
        listOf(leftLabel to true, rightLabel to false).forEach { (label, selectsLeft) ->
            val active = leftSelected == selectsLeft
            val bg by animateColorAsState(
                targetValue = if (active) Color(0xFF3A3A3C) else Color.Transparent,
                animationSpec = tween(160),
                label = "segBg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectLeft(selectsLeft) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else AppleTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun IShareSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.72f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.48f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        AppleGlassSwitch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
        )
    }
}

@Composable
private fun IShareActionPill(
    icon: ImageVector?,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillScale",
    )
    Row(
        modifier = modifier
            .height(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(containerColor)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun IShareTextInputDialog(
    title: String,
    subtitle: String,
    initialValue: String,
    placeholder: String,
    keyboardType: KeyboardType,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    IShareDialogShell(onDismiss = onDismiss) {
        Spacer(Modifier.height(20.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text(
            text = subtitle,
            color = AppleTextSecondary,
            fontSize = 12.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (text.isEmpty()) {
                Text(placeholder, color = Color.White.copy(alpha = 0.38f), fontSize = 15.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                cursorBrush = SolidColor(AppleMusicRed),
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave(text.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(enabled = text.isNotBlank()) { onSave(text.trim()) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Save",
                color = if (text.isNotBlank()) AppleMusicRed else Color.White.copy(alpha = 0.35f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("Cancel", color = Color(0xFF8E8E93), fontSize = 17.sp)
        }
    }
}

@Composable
private fun IShareConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    IShareDialogShell(onDismiss = onDismiss) {
        Spacer(Modifier.height(20.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = AppleTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) {
            Text(confirmLabel, color = AppleMusicRed, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("Cancel", color = Color(0xFF8E8E93), fontSize = 17.sp)
        }
    }
}

@Composable
private fun IShareDialogShell(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        val win = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            win?.setDimAmount(0.28f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                win?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 350.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .shadow(28.dp, RoundedCornerShape(22.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1E1E22))
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}
