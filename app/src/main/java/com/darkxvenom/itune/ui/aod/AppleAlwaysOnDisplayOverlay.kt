package com.darkxvenom.itune.ui.aod

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.R
import com.darkxvenom.itune.audio.AudioOutputManager
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.ui.player.AppleVolumeManager

/**
 * 100% Identical iOS / StandBy styled Always-On Display (AOD) overlay.
 *
 * Visual spec:
 * - Pure OLED Black canvas (#000000).
 * - Floating rounded capsule card with heavy water bubble frosted glass styling:
 *   - 44.dp rounded corners
 *   - Soft translucent liquid grey/white gradient
 *   - Subtle top-down specular highlight and glowing rim border
 * - Top album artwork banner with 22.dp rounded corners.
 * - Song Title with marquee + animated bouncing 4-bar audio equalizer bars.
 * - Sleek scrub bar with elapsed time & negative remaining time.
 * - Previous, Play/Pause, Next controls.
 * - Interactive Volume bar with low/high speaker icons.
 * - Bottom AirPlay / Audio Output pill button with device icon + device name.
 * - Tapping anywhere outside the card or tapping back dismisses AOD.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleAlwaysOnDisplayOverlay(
    modifier: Modifier = Modifier,
) {
    val isAodActive by AlwaysOnDisplayManager.isActive.collectAsStateWithLifecycle()
    val outputState by AudioOutputManager.state.collectAsStateWithLifecycle()
    val volumeFraction by AppleVolumeManager.volumeFraction.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = isAodActive,
        enter = fadeIn(animationSpec = tween(350, easing = FastOutSlowInEasing)),
        exit = fadeOut(animationSpec = tween(280, easing = FastOutSlowInEasing)),
        modifier = modifier,
    ) {
        val context = LocalContext.current
        val view = LocalView.current
        val track = MusicPlayerState.currentTrack
        val isPlaying = MusicPlayerState.isPlaying
        val positionMs = MusicPlayerState.positionMs
        val durationMs = MusicPlayerState.durationMs.coerceAtLeast(1L)

        BackHandler(enabled = isAodActive) {
            AlwaysOnDisplayManager.dismiss()
        }

        // Full Screen Pure OLED Black Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    AlwaysOnDisplayManager.dismiss()
                },
            contentAlignment = Alignment.Center,
        ) {
            // Floating Apple Water-Bubble Frosted Capsule Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(44.dp))
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(44.dp),
                        spotColor = Color.White.copy(alpha = 0.16f),
                    )
                    // Water-bubble liquid glass backdrop: dark frosted grey with soft specular gradient
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2E2E33),
                                Color(0xFF202023),
                                Color(0xFF161618),
                            )
                        )
                    )
                    // Glowing subtle liquid glass rim border
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.22f),
                            )
                        ),
                        shape = RoundedCornerShape(44.dp),
                    )
                    // Intercept taps inside the card so tapping controls doesn't dismiss AOD
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        AlwaysOnDisplayManager.onUserInteraction()
                    }
                    .padding(horizontal = 22.dp, vertical = 24.dp),
            ) {
                // Specular top-left water bubble sheen reflection
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent,
                                ),
                                center = Offset(140f, 60f),
                                radius = 320f,
                            )
                        )
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Top Album Artwork (16:10 widescreen rounded banner)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF111113))
                            .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val artUrl = track?.thumbnailUrl
                        if (!artUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(artUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_apple_logo),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(56.dp),
                            )
                        }
                    }

                    // 2. Song Title & Equalizer Wave Bars
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, start = 2.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = track?.title?.ifBlank { "Not Playing" } ?: "Not Playing",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(),
                        )

                        Spacer(Modifier.width(12.dp))

                        // Animated 4-bar audio equalizer bars (like |||| in screenshot)
                        AudioWaveBarsIndicator(isPlaying = isPlaying)
                    }

                    // 3. Scrub Bar & Timestamps
                    AodScrubBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onSeek = { targetMs ->
                            MusicPlayerState.seekTo(targetMs)
                            AlwaysOnDisplayManager.onUserInteraction()
                        },
                    )

                    // 4. Playback Controls (Previous, Play/Pause, Next)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // Previous Track (<<)
                        Icon(
                            painter = painterResource(id = R.drawable.skip_previous),
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    MusicPlayerState.skipPrevious()
                                    AlwaysOnDisplayManager.onUserInteraction()
                                },
                        )

                        // Play / Pause (|| / ▶)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    MusicPlayerState.togglePlayPause()
                                    AlwaysOnDisplayManager.onUserInteraction()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPlaying) {
                                // Pause Bars (||)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(6.5.dp)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color.White)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(6.5.dp)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color.White)
                                    )
                                }
                            } else {
                                // Play Triangle (▶)
                                Icon(
                                    painter = painterResource(id = R.drawable.play),
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                        }

                        // Next Track (>>)
                        Icon(
                            painter = painterResource(id = R.drawable.skip_next),
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    MusicPlayerState.skipNext()
                                    AlwaysOnDisplayManager.onUserInteraction()
                                },
                        )
                    }

                    // 5. Volume Slider with Speaker Icons
                    AodVolumeSlider(
                        volumeFraction = volumeFraction,
                        onVolumeChange = { frac ->
                            AppleVolumeManager.setVolumeFraction(context, frac)
                            AlwaysOnDisplayManager.onUserInteraction()
                        },
                    )

                    // 6. AirPlay / Output Device Pill Button
                    val activeDeviceName = outputState.activeDevice?.name
                        ?.takeIf { it.isNotBlank() && !it.equals("bluedevice", ignoreCase = true) }
                        ?: AudioOutputManager.getConnectedBluetoothDeviceName()
                        ?: "AirPlay"
                    val activeDeviceIcon = outputState.activeDevice?.iconRes ?: R.drawable.ic_output_airplay

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                AudioOutputManager.openSystemMediaOutputOrBluetooth(context)
                                AlwaysOnDisplayManager.onUserInteraction()
                            }
                            .padding(horizontal = 22.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = activeDeviceIcon),
                                contentDescription = "Audio Output",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                text = activeDeviceName,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated 4-bar audio equalizer waves matching |||| in screenshot.
 */
@Composable
private fun AudioWaveBarsIndicator(isPlaying: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "AodWaveBars")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = if (isPlaying) 15f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "h1",
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = if (isPlaying) 18f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(310, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "h2",
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = if (isPlaying) 5f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(470, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "h3",
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = if (isPlaying) 16f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "h4",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(22.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.2.dp)
                .height(h1.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .width(3.2.dp)
                .height(h2.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .width(3.2.dp)
                .height(h3.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
        Box(
            modifier = Modifier
                .width(3.2.dp)
                .height(h4.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * Sleek progress scrub bar with time labels.
 */
@Composable
private fun AodScrubBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val displayFraction = if (dragFraction >= 0f) dragFraction else fraction

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        val newFrac = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((newFrac * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (dragFraction >= 0f) {
                                onSeek((dragFraction * durationMs).toLong())
                                dragFraction = -1f
                            }
                        },
                        onDragCancel = {
                            dragFraction = -1f
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val barWidth = maxWidth

            // Thick track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // Thick played progress
            Box(
                modifier = Modifier
                    .width(barWidth * displayFraction)
                    .height(6.5.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // Timestamps below seekbar: 1:19 on left, -3:17 on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp, top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val currentMs = if (dragFraction >= 0f) (dragFraction * durationMs).toLong() else positionMs
            val remainingMs = (durationMs - currentMs).coerceAtLeast(0L)

            Text(
                text = formatAodTime(currentMs),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = "-${formatAodTime(remainingMs)}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Capsule volume seekbar with speaker icons.
 */
@Composable
private fun AodVolumeSlider(
    volumeFraction: Float,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Low speaker icon
        Icon(
            painter = painterResource(id = R.drawable.ic_aod_volume_low),
            contentDescription = "Low Volume",
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp),
        )

        // Volume track
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newFrac = (offset.x / size.width).coerceIn(0f, 1f)
                        onVolumeChange(newFrac)
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val newFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                            onVolumeChange(newFrac)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val barWidth = maxWidth

            // Thick track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )

            // Thick played progress
            Box(
                modifier = Modifier
                    .width(barWidth * volumeFraction.coerceIn(0f, 1f))
                    .height(6.5.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // High speaker icon with sound waves
        Icon(
            painter = painterResource(id = R.drawable.volume_up),
            contentDescription = "High Volume",
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(19.dp),
        )
    }
}

private fun formatAodTime(timeMs: Long): String {
    val totalSec = (timeMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "$mins:${secs.toString().padStart(2, '0')}"
}
