package com.darkxvenom.itune.ui.player

import android.Manifest
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.R
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.audio.OutputDeviceEntry
import com.darkxvenom.itune.ui.settings.AppleGlassSwitch
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

private val OutputCardBlack = Color(0xFF0E0E10)
private val OutputElevatedBlack = Color(0xFF1C1C1E)
private val OutputActivePillBlack = Color(0xFF323236)
private val OutputActiveGreen = Color(0xFF30D158)

/**
 * Apple AirPlay / Output Audio Devices liquid-glass popup anchored above the split capsule's left Buds/Headphone button.
 * Reuses AppleFrostedPopupOverlay water-drop bloom & ripples with obsidian black buttons for hardware routing,
 * volume control, and Simultaneous Dual Output multi-device mirroring.
 */
@Composable
fun AppleAudioOutputPopup(
    visible: Boolean,
    artUrl: String?,
    anchorCenterXPx: Float,
    anchorCenterYPx: Float,
    statusBarTopDp: Dp,
    navBottomDp: Dp,
    maxHeightDp: Dp,
    rootCoordsProvider: () -> LayoutCoordinates?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible) {
        onDismiss()
    }

    val context = LocalContext.current
    val view = LocalView.current

    val outputState by AudioOutputManager.state.collectAsStateWithLifecycle()

    LaunchedEffect(visible) {
        if (visible) {
            AudioOutputManager.refreshDevices()
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        AudioOutputManager.refreshDevices()
    }

    AppleFrostedPopupOverlay(
        visible = visible,
        title = "Audio Output",
        subtitle = outputState.activeSummary,
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
        hasInputFocusPage = false,
        modifier = modifier,
        headerActions = {
            HeaderCircleBlackXmlButton(
                iconRes = R.drawable.ic_output_bluetooth,
                contentDescription = "Bluetooth Settings",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    AudioOutputManager.openSystemMediaOutputOrBluetooth(context, preferBluetoothSettings = true)
                },
            )
            Box(
                modifier = Modifier
                    .size(33.dp)
                    .clip(CircleShape)
                    .background(OutputCardBlack)
                    .border(0.6.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioOutputManager.refreshDevices()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Scan Output Devices",
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Bluetooth Permission Prompt on Android 12+ if not granted yet ──
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !outputState.hasBluetoothPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(OutputCardBlack)
                            .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_output_bluetooth),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bluetooth Device Access",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Grant to show earbuds name & live battery %",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 11.5.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(OutputActivePillBlack)
                                .border(0.6.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Grant",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                PopupMenuDivider()
            }

            // ── 1. Playback Destination Devices (Auto + All Physical / Wireless Outputs) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                OutputSectionHeader(
                    title = "OUTPUT AUDIO DEVICES",
                    trailing = "${outputState.devices.size} available",
                )

                // System Default (Automatic Routing)
                val isAutoSelected = outputState.preferredDeviceId == null
                OutputDeviceBlackButton(
                    iconRes = R.drawable.ic_output_airplay,
                    title = "System Default (Automatic)",
                    subtitle = outputState.activeDevice?.let { "Auto -> ${it.name}" }
                        ?: "Follows connected buds, USB DAC or jack",
                    badgeText = null,
                    selected = isAutoSelected,
                    autoInUse = false,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioOutputManager.selectPreferredDevice(null)
                    },
                )

                // Physical / Connected Devices
                outputState.devices.forEach { dev ->
                    val isExplicitlySelected = outputState.preferredDeviceId == dev.id
                    val isAutoRouted = isAutoSelected && outputState.activeDevice?.id == dev.id
                    val metaBadge = buildList {
                        dev.batteryPercent?.let { add("$it%") }
                        dev.sampleRateLabel?.let { add(it) }
                    }.joinToString(" • ").takeIf { it.isNotBlank() }

                    OutputDeviceBlackButton(
                        iconRes = dev.iconRes,
                        title = dev.name,
                        subtitle = dev.subtitle,
                        badgeText = metaBadge,
                        selected = isExplicitlySelected,
                        autoInUse = isAutoRouted,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            AudioOutputManager.selectPreferredDevice(dev)
                        },
                    )
                }
            }

            PopupMenuDivider()

            // ── 2. Master Volume Control Bar right inside popup ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                OutputSectionHeader(title = "OUTPUT VOLUME", trailing = null)
                Spacer(Modifier.height(6.dp))
                IosVolumeSlider()
            }

            PopupMenuDivider()

            // ── 3. Simultaneous Dual Audio Output (AirBeats Multi-Device Mirroring) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                SimultaneousDualAudioBlackCard(
                    enabled = outputState.dualAudioEnabled,
                    secondaryDeviceId = outputState.dualSecondaryDeviceId,
                    secondaryVolume = outputState.dualSecondaryVolume,
                    availableDevices = outputState.devices,
                    activePrimaryId = outputState.activeDevice?.id,
                    onToggle = { next ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioOutputManager.setDualAudioEnabled(next)
                    },
                    onSelectSecondary = { dev ->
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioOutputManager.setDualAudioSecondaryDevice(dev)
                    },
                    onSecondaryVolumeChange = { vol ->
                        AudioOutputManager.setDualAudioSecondaryVolume(vol)
                    },
                )
            }

            PopupMenuDivider()

            // ── 4. Footer Obsidian Black Action Buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutputFooterBlackButton(
                    iconRes = R.drawable.ic_output_settings,
                    label = "System Output",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioOutputManager.openSystemMediaOutputOrBluetooth(context, preferBluetoothSettings = false)
                    },
                    modifier = Modifier.weight(1f),
                )
                OutputFooterBlackButton(
                    iconRes = R.drawable.ic_output_bluetooth,
                    label = "Bluetooth",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        AudioOutputManager.openSystemMediaOutputOrBluetooth(context, preferBluetoothSettings = true)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun OutputSectionHeader(
    title: String,
    trailing: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = AppleTextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun OutputDeviceBlackButton(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    badgeText: String?,
    selected: Boolean,
    autoInUse: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) OutputElevatedBlack else OutputCardBlack,
        animationSpec = tween(160),
        label = "outputRowBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = 0.6.dp,
                color = if (selected) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) OutputActivePillBlack else Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = title,
                colorFilter = ColorFilter.tint(Color.White),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(11.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!badgeText.isNullOrBlank()) {
                    Text(
                        text = " • $badgeText",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        if (selected) {
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OutputActiveGreen.copy(alpha = 0.20f))
                    .border(0.5.dp, OutputActiveGreen.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Active",
                    tint = OutputActiveGreen,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "Active",
                    color = OutputActiveGreen,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else if (autoInUse) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OutputActivePillBlack)
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "In Use",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SimultaneousDualAudioBlackCard(
    enabled: Boolean,
    secondaryDeviceId: Int?,
    secondaryVolume: Float,
    availableDevices: List<OutputDeviceEntry>,
    activePrimaryId: Int?,
    onToggle: (Boolean) -> Unit,
    onSelectSecondary: (OutputDeviceEntry) -> Unit,
    onSecondaryVolumeChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OutputCardBlack)
            .border(
                width = 0.6.dp,
                color = if (enabled) OutputActiveGreen.copy(alpha = 0.48f) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!enabled) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(OutputElevatedBlack),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_output_dual_audio),
                    contentDescription = "Simultaneous Dual Audio",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Simultaneous Dual Output",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Play on buds & phone speaker/AUX at the same time",
                    color = Color.White.copy(alpha = 0.58f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            AppleGlassSwitch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.6.dp)
                        .background(Color.White.copy(alpha = 0.10f)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "SECONDARY MIRROR SINK",
                    color = AppleTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
                Spacer(Modifier.height(6.dp))

                val secondaryList = availableDevices.filter { it.id != activePrimaryId }
                    .ifEmpty { availableDevices }

                secondaryList.forEach { sec ->
                    val isSecSelected = (secondaryDeviceId == sec.id) ||
                        (secondaryDeviceId == null && sec.category == com.darkxvenom.itune.audio.AudioDeviceCategory.BUILTIN_SPEAKER)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSecSelected) OutputActivePillBlack else Color.Transparent)
                            .clickable { onSelectSecondary(sec) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(sec.iconRes),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = sec.name,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSecSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isSecSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = OutputActiveGreen,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mirror Vol",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(10.dp))
                    DualVolumeCapsuleBar(
                        fraction = secondaryVolume,
                        onFractionChange = onSecondaryVolumeChange,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(secondaryVolume * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DualVolumeCapsuleBar(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    onFractionChange((offset.x / w).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    onFractionChange((change.position.x / w).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun OutputFooterBlackButton(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(OutputCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeaderCircleBlackXmlButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(33.dp)
            .clip(CircleShape)
            .background(OutputCardBlack)
            .border(0.6.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
            modifier = Modifier.size(17.dp),
        )
    }
}
