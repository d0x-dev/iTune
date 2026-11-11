package com.darkxvenom.itune.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.ishare.DiscoveredLanHost
import com.darkxvenom.itune.ishare.IShareConnectionMode
import com.darkxvenom.itune.ishare.IShareParticipant
import com.darkxvenom.itune.ishare.IShareSession
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.settings.AppleGlassSwitch
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

private val SharePlayGreen = Color(0xFF30D158)
private val SearchSwitcherTrackBlack = Color(0xFF1C1C1E)
private val SearchSwitcherActiveBlack = Color(0xFF38383A)
private val ObsidianBlackButton = Color(0xFF0E0E10)

/**
 * Apple SharePlay / iShare frosted liquid-glass popup anchored above the bottom player iShare capsule
 * with the same water-drop bloom & ripple origin physics as the player's 3-dot More menu.
 */
@Composable
fun AppleISharePopup(
    visible: Boolean,
    track: MusicTrack,
    artUrl: String?,
    anchorCenterXPx: Float,
    anchorCenterYPx: Float,
    statusBarTopDp: Dp,
    navBottomDp: Dp,
    maxHeightDp: Dp,
    rootCoordsProvider: () -> LayoutCoordinates?,
    onDismiss: () -> Unit,
    onOpenFullIShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible) {
        onDismiss()
    }

    val context = LocalContext.current
    val view = LocalView.current

    val activeSession by IShareSyncManager.session.collectAsStateWithLifecycle()
    val isHost by IShareSyncManager.isHost.collectAsStateWithLifecycle()
    val isBusy by IShareSyncManager.isBusy.collectAsStateWithLifecycle()
    val connectionMode by IShareSyncManager.connectionMode.collectAsStateWithLifecycle()
    val localIp by IShareSyncManager.localIp.collectAsStateWithLifecycle()
    val discoveredHosts by IShareSyncManager.discoveredHosts.collectAsStateWithLifecycle()
    val isScanningLan by IShareSyncManager.isScanningLan.collectAsStateWithLifecycle()

    var selectedOnlineTab by rememberSaveable { mutableStateOf(true) }
    var joinInput by rememberSaveable { mutableStateOf("") }
    var hasInputFocus by remember { mutableStateOf(false) }

    LaunchedEffect(visible, connectionMode) {
        if (visible) {
            IShareSyncManager.refreshLocalIp()
            selectedOnlineTab = if (activeSession != null) {
                connectionMode == IShareConnectionMode.ONLINE
            } else {
                selectedOnlineTab
            }
            if (activeSession == null && !selectedOnlineTab) {
                IShareSyncManager.scanLanHosts(8765)
            }
        } else {
            hasInputFocus = false
        }
    }

    val isCurrentlyOnline = connectionMode == IShareConnectionMode.ONLINE
    val subtitleText = when {
        activeSession != null && !isCurrentlyOnline -> "Wi-Fi LAN • ${activeSession!!.code}"
        activeSession != null -> "Online Code • ${activeSession!!.code}"
        else -> "Listen together with ${track.title}"
    }

    AppleFrostedPopupOverlay(
        visible = visible,
        title = "iShare • Listen Together",
        subtitle = subtitleText,
        artUrl = artUrl,
        anchorCenterXPx = anchorCenterXPx,
        anchorCenterYPx = anchorCenterYPx,
        maxHeightDp = maxHeightDp,
        statusBarTopDp = statusBarTopDp,
        rootCoordsProvider = rootCoordsProvider,
        onDismiss = onDismiss,
        onBack = onDismiss,
        isTopAnchored = false,
        bottomClearanceDp = (88.dp + navBottomDp).coerceIn(76.dp, maxHeightDp * 0.45f),
        hasInputFocusPage = hasInputFocus,
        modifier = modifier,
        headerActions = {
            val room = activeSession
            if (room != null) {
                val shareValue = if (isCurrentlyOnline) room.code else "http://${room.code}"
                HeaderCircleActionButton(
                    iconRes = R.drawable.ic_menu_copy_link,
                    contentDescription = "Copy",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        copyPopupText(context, "iShare", shareValue)
                    },
                )
                HeaderCircleActionButton(
                    iconRes = R.drawable.ic_menu_share,
                    contentDescription = "Share",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val msg = if (isCurrentlyOnline) {
                            "Listen together on iShare / AirBeats! Pairing code: ${room.code}\n${room.joinUrl}"
                        } else {
                            "Join my iShare Wi-Fi session: http://${room.code} (or enter ${room.code} in iShare / AirBeats)"
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, msg)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share iShare Session"))
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(33.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            IShareSyncManager.refreshLocalIp()
                            if (!selectedOnlineTab) IShareSyncManager.scanLanHosts(8765)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh Network",
                        tint = Color.White.copy(alpha = 0.90f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Apple-styled Online / LAN segmented switch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                IShareModeSegmentedSwitch(
                    selectedOnline = selectedOnlineTab,
                    enabled = !isBusy && (activeSession == null || isHost),
                    onSelect = { online ->
                        if (selectedOnlineTab == online) return@IShareModeSegmentedSwitch
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedOnlineTab = online
                        val room = activeSession
                        if (room != null && isHost) {
                            IShareSyncManager.switchHostMode(
                                online = online,
                                allowGuestsToControlPlayback = room.settings.allowGuestsToControlPlayback,
                                allowGuestsToAddTracks = room.settings.allowGuestsToAddTracks,
                            )
                        } else if (!online) {
                            IShareSyncManager.refreshLocalIp()
                            IShareSyncManager.scanLanHosts(8765)
                        }
                    },
                )
            }

            PopupMenuDivider()

            val room = activeSession
            if (room != null) {
                ISharePopupActiveBody(
                    session = room,
                    isHost = isHost,
                    isOnline = isCurrentlyOnline,
                    onCopy = { text -> copyPopupText(context, "iShare", text) },
                    onOpenBrowser = { url ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    onToggleControl = { allowControl ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        IShareSyncManager.updateSettings(allowControl, room.settings.allowGuestsToAddTracks)
                    },
                    onToggleQueue = { allowQueue ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        IShareSyncManager.updateSettings(room.settings.allowGuestsToControlPlayback, allowQueue)
                    },
                    onKickParticipant = { participantId ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        IShareSyncManager.kickParticipant(participantId)
                    },
                    onOpenFullIShare = {
                        onDismiss()
                        onOpenFullIShare()
                    },
                    onLeaveSession = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        IShareSyncManager.leaveSession()
                    },
                )
            } else {
                ISharePopupIdleBody(
                    onlineSelected = selectedOnlineTab,
                    localIp = localIp,
                    isBusy = isBusy,
                    isScanningLan = isScanningLan,
                    discoveredHosts = discoveredHosts,
                    joinInput = joinInput,
                    onJoinInputChange = { joinInput = it },
                    onInputFocusChanged = { hasInputFocus = it },
                    onStartHost = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (selectedOnlineTab) {
                            IShareSyncManager.createOnlineSession(
                                allowGuestsToControlPlayback = true,
                                allowGuestsToAddTracks = true,
                            )
                        } else {
                            IShareSyncManager.startLanHost(
                                port = 8765,
                                allowGuestsToControlPlayback = true,
                                allowGuestsToAddTracks = true,
                            )
                        }
                    },
                    onJoin = { value ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (selectedOnlineTab) {
                            IShareSyncManager.joinOnlineSession(value)
                        } else {
                            IShareSyncManager.joinLanSession(value)
                        }
                    },
                    onScanLan = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        IShareSyncManager.scanLanHosts(8765)
                    },
                    onOpenFullIShare = {
                        onDismiss()
                        onOpenFullIShare()
                    },
                )
            }
        }
    }
}

@Composable
private fun IShareModeSegmentedSwitch(
    selectedOnline: Boolean,
    enabled: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    // Matches SearchResultFilterBar (All / Songs / Artists / Albums / Playlists) black Apple segmented control
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(SearchSwitcherTrackBlack)
            .padding(2.5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeSegmentButton(
                label = "Online",
                icon = Icons.Rounded.Public,
                selected = selectedOnline,
                enabled = enabled,
                onClick = { onSelect(true) },
                modifier = Modifier.weight(1f),
            )
            ModeSegmentButton(
                label = "LAN",
                icon = Icons.Rounded.Wifi,
                selected = !selectedOnline,
                enabled = enabled,
                onClick = { onSelect(false) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeSegmentButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) SearchSwitcherActiveBlack else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "ishareSwitchBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.White else AppleTextSecondary,
        animationSpec = tween(durationMillis = 160),
        label = "ishareSwitchText",
    )
    Row(
        modifier = modifier
            .height(31.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = (-0.2).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ISharePopupActiveBody(
    session: IShareSession,
    isHost: Boolean,
    isOnline: Boolean,
    onCopy: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onToggleControl: (Boolean) -> Unit,
    onToggleQueue: (Boolean) -> Unit,
    onKickParticipant: (String) -> Unit,
    onOpenFullIShare: () -> Unit,
    onLeaveSession: () -> Unit,
) {
    val participants = session.participantList.ifEmpty {
        listOf(IShareParticipant(id = "host", name = session.hostName, isHost = true))
    }
    val deviceCount = participants.size.coerceAtLeast(session.participants)
    val accent = if (isOnline) SharePlayGreen else Color.White.copy(alpha = 0.90f)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Pairing code / LAN URL card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = buildString {
                            append(if (isHost) "HOSTING • " else "JOINED • ")
                            append(if (isOnline) "ONLINE PAIR CODE" else "WI-FI LAN")
                        },
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                    )
                }
                Text(
                    text = "$deviceCount connected",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(13.dp))
                    .clickable {
                        onCopy(if (isOnline) session.code else "http://${session.code}")
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.code,
                        color = Color.White,
                        fontSize = if (isOnline) 22.sp else 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = if (isOnline) 3.sp else 0.4.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isOnline) {
                            session.joinUrl.ifBlank { "Tap to copy AirBeats / iShare pairing code" }
                        } else {
                            "Web Player: http://${session.code}"
                        },
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(16.dp),
                )
            }

            if (!isOnline) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(ObsidianBlackButton)
                        .border(0.75.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(11.dp))
                        .clickable { onOpenBrowser("http://${session.code}") }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInBrowser,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Open Web Player (${session.code})",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (isHost) {
            PopupMenuDivider()
            PopupPermissionRow(
                title = "Guest Playback Controls",
                checked = session.settings.allowGuestsToControlPlayback,
                onCheckedChange = onToggleControl,
            )
            PopupPermissionRow(
                title = "Guest Queue Edits",
                checked = session.settings.allowGuestsToAddTracks,
                onCheckedChange = onToggleQueue,
            )
        }

        PopupMenuDivider()

        // Connected devices list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = "CONNECTED DEVICES ($deviceCount)",
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(6.dp))
            participants.take(6).forEachIndexed { idx, p ->
                val isSelf = (isHost && p.isHost) || (!isHost && p.id == session.participantId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (p.isHost) AppleMusicRed.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = p.name.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = p.name + if (isSelf) " (You)" else "",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (p.isHost) "Session Host" else "Synced Listener",
                            color = Color.White.copy(alpha = 0.54f),
                            fontSize = 11.sp,
                        )
                    }
                    if (p.isHost) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppleMusicRed.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "HOST",
                                color = Color(0xFFFF7A8C),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else if (isHost) {
                        IconButton(
                            onClick = { onKickParticipant(p.id) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonRemove,
                                contentDescription = "Disconnect",
                                tint = AppleMusicRed,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                if (idx < minOf(participants.size, 6) - 1) {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }

        PopupMenuDivider()

        // Footer actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onOpenFullIShare),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Full View",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(AppleMusicRed.copy(alpha = 0.22f))
                    .border(0.6.dp, AppleMusicRed.copy(alpha = 0.42f), CircleShape)
                    .clickable(onClick = onLeaveSession),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isHost) "End Session" else "Leave",
                    color = Color(0xFFFF697D),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PopupPermissionRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        AppleGlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ISharePopupIdleBody(
    onlineSelected: Boolean,
    localIp: String?,
    isBusy: Boolean,
    isScanningLan: Boolean,
    discoveredHosts: List<DiscoveredLanHost>,
    joinInput: String,
    onJoinInputChange: (String) -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    onStartHost: () -> Unit,
    onJoin: (String) -> Unit,
    onScanLan: () -> Unit,
    onOpenFullIShare: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val ctaBackground = if (onlineSelected) SharePlayGreen.copy(alpha = 0.90f) else ObsidianBlackButton
    val ctaTextColor = if (onlineSelected) Color.Black else Color.White

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = if (onlineSelected) {
                    "Generate a 6-character pairing code compatible with iTune & AirBeats friends anywhere."
                } else {
                    val hostLabel = localIp?.let { "$it:8765" } ?: "Wi-Fi / Hotspot required"
                    "Zero-latency LAN hosting + Apple Liquid Glass Web Player at $hostLabel"
                },
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(CircleShape)
                    .background(ctaBackground)
                    .then(
                        if (!onlineSelected) {
                            Modifier.border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable(enabled = !isBusy, onClick = onStartHost),
                contentAlignment = Alignment.Center,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = ctaTextColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (onlineSelected) "Get Pairing Code" else "Start Wi-Fi LAN Host",
                        color = ctaTextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = joinInput,
                    onValueChange = { next ->
                        onJoinInputChange(if (onlineSelected) next.uppercase().take(8) else next.take(40))
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = if (onlineSelected) FontFamily.Monospace else FontFamily.Default,
                    ),
                    cursorBrush = SolidColor(AppleMusicRed),
                    keyboardOptions = KeyboardOptions(
                        capitalization = if (onlineSelected) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (joinInput.isNotBlank()) onJoin(joinInput)
                        },
                    ),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(0.6.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (joinInput.isEmpty()) {
                                Text(
                                    text = if (onlineSelected) "Enter 6-char code" else "Host IP (e.g. 192.168.1.5:8765)",
                                    color = Color.White.copy(alpha = 0.40f),
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { onInputFocusChanged(it.isFocused) },
                )

                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(enabled = !isBusy && joinInput.isNotBlank()) {
                            focusManager.clearFocus()
                            onJoin(joinInput)
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Join",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!onlineSelected) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "NEARBY WI-FI HOSTS",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = if (isScanningLan) "Scanning..." else "Scan",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = !isScanningLan, onClick = onScanLan),
                    )
                }
                if (discoveredHosts.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    discoveredHosts.take(3).forEach { host ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianBlackButton)
                                .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .clickable { onJoin(host.address) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${host.hostName} • ${host.address}",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${host.participants}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                }
            }
        }

        PopupMenuDivider()

        MorePopupVectorOptionRow(
            icon = SfIcons.Radio,
            title = "Open Full iShare View",
            onClick = onOpenFullIShare,
        )
    }
}

private fun copyPopupText(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
}
