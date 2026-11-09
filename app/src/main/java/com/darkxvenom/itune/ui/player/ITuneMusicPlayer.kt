package com.darkxvenom.itune.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.formatTime
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.ui.components.WavySlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.roundToInt

/**
 * iTune Music Player Screen.
 * Exact visual duplicate of iTube's iOS styled music player:
 * Full screen high-quality thumbnail with lower ambient blur effect,
 * sharp artwork card, expressive wavy seek bar, audio quality pill,
 * 3 playback controls (Previous, Play/Pause, Next), and iOS volume slider.
 */
@Composable
fun ITuneMusicPlayer(
    modifier: Modifier = Modifier,
    onCollapse: () -> Unit = { MusicPlayerState.expanded = false }
) {
    val track = MusicPlayerState.currentTrack ?: return
    val isPlaying = MusicPlayerState.isPlaying
    val isLoading = MusicPlayerState.isLoading
    val position = MusicPlayerState.positionMs
    val duration = MusicPlayerState.durationMs

    val context = LocalContext.current
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DisposableEffect(track.id) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onDispose { }
    }

    var audioAmbientFrame by remember(track.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(track.id, track.thumbnailUrl) {
        val targetUrl = track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl
        if (!targetUrl.isNullOrBlank()) {
            val bmp = withContext(Dispatchers.IO) {
                var loadedBmp = runCatching {
                    val req = ImageRequest.Builder(context)
                        .data(targetUrl)
                        .allowHardware(false)
                        .size(160)
                        .build()
                    val result = context.imageLoader.execute(req) as? SuccessResult
                    result?.drawable?.toBitmap()
                }.getOrNull()

                if (loadedBmp == null && targetUrl != track.thumbnailUrl && !track.thumbnailUrl.isNullOrBlank()) {
                    loadedBmp = runCatching {
                        val req = ImageRequest.Builder(context)
                            .data(track.thumbnailUrl)
                            .allowHardware(false)
                            .size(160)
                            .build()
                        val result = context.imageLoader.execute(req) as? SuccessResult
                        result?.drawable?.toBitmap()
                    }.getOrNull()
                }
                loadedBmp
            }
            if (bmp != null) {
                audioAmbientFrame = bmp.asImageBitmap()
            }
        }
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = MusicPlayerState.expanded) {
        onCollapse()
    }

    val safeDuration = duration.coerceAtLeast(1L)
    val displayPos = if (isSeeking) seekPosition else position
    val remaining = (duration - displayPos).coerceAtLeast(0L)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (MusicPlayerState.startFastHold()) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        try {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            MusicPlayerState.endFastHold()
                        }
                    }
                }
            }
    ) {
        // ── 1. Ambient Blurred Artwork Layer (Full-screen ambient glow across entire display) ──
        val ambientFrame = audioAmbientFrame
        if (ambientFrame != null) {
            Image(
                bitmap = ambientFrame,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                        alpha = 0.95f
                    }
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl)
                    .allowHardware(false)
                    .size(160)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                        alpha = 0.95f
                    }
            )
        }

        // ── 2. Luminous Scrim Gradient ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.30f),
                            0.30f to Color.Transparent,
                            0.65f to Color.Black.copy(alpha = 0.25f),
                            1f to Color.Black.copy(alpha = 0.52f),
                        )
                    )
                )
        )

        // ── 3. Top Navigation Bar (Collapse Chevron, Centered Indicator, More menu) ──
        Box(
            modifier = Modifier
                .zIndex(25f)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(36.dp)
                )
            }

            // Top drag handle indicator
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 38.dp, height = 4.5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            )

            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Image(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = "More",
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.90f)),
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // ── Fast Forward 2× Pill Indicator ──
        AnimatedVisibility(
            visible = MusicPlayerState.holdingFast,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 2 },
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.78f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = "2× Speed",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }

        // ── 4. Main Body: Center Album Artwork Card ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 64.dp, bottom = 320.dp, start = 28.dp, end = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(elevation = 28.dp, shape = RoundedCornerShape(22.dp), spotColor = Color.Black)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            ) {
                val isYtVideoThumb = (track.thumbnailUrl ?: "").contains("i.ytimg.com")
                val artworkScale = if (isYtVideoThumb) 1.34f else 1.0f
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = artworkScale
                            scaleY = artworkScale
                        }
                )
            }
        }

        // ── 5. Bottom Controls Anchored Column ──
        Column(
            modifier = Modifier
                .zIndex(25f)
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Song Title and Artist Metadata (NO like/dislike/comment/share buttons)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = track.artists,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Wavy Seek Bar (AirBeats Expressive Squiggly Slider) ──
            WavySlider(
                value = displayPos.toFloat(),
                onValueChange = {
                    isSeeking = true
                    seekPosition = it.toLong().coerceIn(0L, safeDuration)
                },
                onValueChangeFinished = {
                    MusicPlayerState.seekTo(seekPosition)
                    isSeeking = false
                },
                valueRange = 0f..safeDuration.toFloat(),
                isPlaying = isPlaying,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                thumbColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(4.dp))

            // ── Time & Audio Quality Tag Row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(displayPos),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )

                // Center Audio Quality Badge Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.graphic_eq),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.85f)),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = Color(0xFFFA2D48))) { append("i") }
                                withStyle(SpanStyle(color = Color.White)) { append("Music • 128k") }
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }

                Text(
                    text = "-${formatTime(remaining)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Main Playback Controls: ONLY 3 BUTTONS (Previous, Play/Pause, Next) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Previous Button
                IconButton(
                    onClick = {
                        MusicPlayerState.skipPrevious()
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = "Previous",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(38.dp)
                    )
                }

                // 2. Play / Pause Button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable {
                            MusicPlayerState.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading && !isPlaying) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(
                                if (isPlaying) R.drawable.pause else R.drawable.play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // 3. Next Button
                IconButton(
                    onClick = {
                        MusicPlayerState.skipNext()
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = "Next",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Volume Seek Bar (iOS Capsule Style) ──
            IosVolumeSlider(
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ── Bottom Action Controls: Queue & Lyrics ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Queue Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = "Queue",
                            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.90f)),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Lyrics Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.lyrics),
                            contentDescription = "Lyrics",
                            colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.90f)),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Audio Output Pill
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "iPhone Speaker",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.90f)
                    )
                }
            }
        }
    }
}

@Composable
private fun IosVolumeSlider(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember(audioManager) { (audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15).coerceAtLeast(1) }

    var currentVolume by remember(audioManager) {
        mutableIntStateOf(audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: (maxVolume / 2))
    }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf((currentVolume.toFloat() / maxVolume).coerceIn(0f, 1f)) }

    DisposableEffect(context, audioManager, maxVolume) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (!isDragging && audioManager != null) {
                    val vol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    currentVolume = vol
                    dragFraction = (vol.toFloat() / maxVolume).coerceIn(0f, 1f)
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {}
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val displayFraction = if (isDragging) dragFraction else (currentVolume.toFloat() / maxVolume).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = displayFraction,
        animationSpec = if (isDragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "volumeFraction"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (currentVolume == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeDown,
            contentDescription = "Mute Volume",
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier
                .size(19.dp)
                .clickable {
                    if (audioManager != null) {
                        if (currentVolume > 0) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                            currentVolume = 0
                            dragFraction = 0f
                        } else {
                            val target = (maxVolume * 0.25f).roundToInt().coerceAtLeast(1)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                            currentVolume = target
                            dragFraction = target.toFloat() / maxVolume
                        }
                    }
                }
        )

        Spacer(Modifier.width(12.dp))

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(maxVolume) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        if (w > 0f) {
                            val fraction = (offset.x / w).coerceIn(0f, 1f)
                            val targetVol = (fraction * maxVolume).roundToInt()
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            currentVolume = targetVol
                            dragFraction = fraction
                        }
                    }
                }
                .pointerInput(maxVolume) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val w = size.width.toFloat()
                            if (w > 0f) {
                                val fraction = (offset.x / w).coerceIn(0f, 1f)
                                dragFraction = fraction
                                val targetVol = (fraction * maxVolume).roundToInt()
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                currentVolume = targetVol
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val targetVol = (dragFraction * maxVolume).roundToInt()
                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                            currentVolume = targetVol
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat()
                            if (w > 0f) {
                                val fraction = (change.position.x / w).coerceIn(0f, 1f)
                                dragFraction = fraction
                                val targetVol = (fraction * maxVolume).roundToInt()
                                if (targetVol != currentVolume) {
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    currentVolume = targetVol
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val trackHeight = 7.5.dp
            val trackShape = RoundedCornerShape(percent = 50)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(trackShape)
                    .background(Color.White.copy(alpha = 0.22f))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(trackHeight)
                    .clip(trackShape)
                    .background(Color.White.copy(alpha = 0.82f))
            )
        }

        Spacer(Modifier.width(12.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = "Max Volume",
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(19.dp)
        )
    }
}
