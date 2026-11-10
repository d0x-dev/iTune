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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.components.WavySlider
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    start + (stop - start) * fraction

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Exact Apple Music / AirBeats iOS styled full-screen player:
 * - Full-bleed top artwork with gradient alpha fade dissolving into ambient blur
 * - Morphing artwork avatar animation on Queue / Lyrics panel open
 * - In-player Queue view with Shuffle, Repeat, Now Playing, and Upcoming queue list
 * - In-player Synced Lyrics view (AirBeatsLyricsV2Content) with line scrolling & liquid glow
 * - 3 clean playback controls (Previous, Play/Pause, Next) with WavySlider and iOS volume capsule slider
 */
@Composable
fun ITuneMusicPlayer(
    onCollapse: () -> Unit = { MusicPlayerState.expanded = false },
    modifier: Modifier = Modifier
) {
    val track = MusicPlayerState.currentTrack ?: return
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    val isPlaying = MusicPlayerState.isPlaying
    val isLoading = MusicPlayerState.isLoading
    val position = MusicPlayerState.positionMs
    val duration = MusicPlayerState.durationMs

    val isLyricsOpen = MusicPlayerState.lyricsOpen
    val isQueueOpen = MusicPlayerState.queueOpen
    val isPanelOpen = isLyricsOpen || isQueueOpen

    val panelProgress by animateFloatAsState(
        targetValue = if (isPanelOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "panelProgress"
    )

    var areLyricsControlsVisible by remember { mutableStateOf(true) }
    var lastLyricsInteractionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPanelOpen) {
        if (isPanelOpen) {
            areLyricsControlsVisible = true
            lastLyricsInteractionTime = System.currentTimeMillis()
            if (isLyricsOpen && MusicPlayerState.lyrics.isEmpty() && !MusicPlayerState.lyricsLoading) {
                MusicPlayerState.refreshLyrics()
            }
        } else {
            areLyricsControlsVisible = true
        }
    }

    LaunchedEffect(track.id, isLyricsOpen) {
        if (isLyricsOpen && MusicPlayerState.lyrics.isEmpty() && !MusicPlayerState.lyricsLoading) {
            MusicPlayerState.refreshLyrics()
        }
    }

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isLyricsOpen, lastLyricsInteractionTime, areLyricsControlsVisible, isSeeking) {
        if (isLyricsOpen && areLyricsControlsVisible && !isSeeking) {
            delay(2000L)
            areLyricsControlsVisible = false
        }
    }

    val onLyricsInteraction: () -> Unit = {
        lastLyricsInteractionTime = System.currentTimeMillis()
        if (isLyricsOpen) {
            areLyricsControlsVisible = !areLyricsControlsVisible
        } else {
            areLyricsControlsVisible = true
        }
    }

    val controlsTranslationY by animateFloatAsState(
        targetValue = if (!isLyricsOpen || areLyricsControlsVisible) 0f else with(density) { 340.dp.toPx() },
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "lyricsControlsTranslationY"
    )

    val controlsAlpha by animateFloatAsState(
        targetValue = if (!isLyricsOpen || areLyricsControlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "lyricsControlsAlpha"
    )

    val contentBottomPadding by animateDpAsState(
        targetValue = if (!areLyricsControlsVisible && isLyricsOpen) 24.dp else 295.dp,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "contentBottomPadding"
    )

    BackHandler(enabled = MusicPlayerState.expanded) {
        if (isQueueOpen) {
            MusicPlayerState.queueOpen = false
        } else if (isLyricsOpen) {
            MusicPlayerState.lyricsOpen = false
        } else {
            onCollapse()
        }
    }

    val safeDuration = if (duration > 0) duration else (track.durationSec * 1000L).coerceAtLeast(1L)
    val displayPos = if (isSeeking) seekPosition else position.coerceIn(0L, safeDuration)
    val remaining = (safeDuration - displayPos).coerceAtLeast(0L)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fullBleedHeight = maxHeight * 0.56f

        // ── 1. Fullscreen Ambient Blurred Artwork Layer ──
        val blurScale by animateFloatAsState(
            targetValue = if (isPanelOpen) 1.45f else 1.35f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "blurScale"
        )

        key(track.id) {
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
                        scaleX = blurScale
                        scaleY = blurScale
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

        // ── 3. In-Player Synced Lyrics Screen ──
        if (isLyricsOpen && panelProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .zIndex(5f)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 66.dp)
                    .padding(bottom = contentBottomPadding)
                    .graphicsLayer {
                        alpha = panelProgress
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onLyricsInteraction()
                    }
            ) {
                AirBeatsLyricsV2Content(
                    lyrics = MusicPlayerState.lyrics,
                    sliderPositionProvider = { if (isSeeking) seekPosition else null },
                    onSeek = { seekPos ->
                        onLyricsInteraction()
                        MusicPlayerState.seekTo(seekPos)
                    },
                    onUserInteraction = onLyricsInteraction,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── 4. In-Player Queue Screen ──
        if (isQueueOpen && panelProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .zIndex(5f)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 66.dp)
                    .padding(bottom = contentBottomPadding)
                    .graphicsLayer {
                        alpha = panelProgress
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        onLyricsInteraction()
                    }
            ) {
                InPlayerQueueContent(
                    currentTrack = track,
                    queue = MusicPlayerState.queue,
                    currentIndex = MusicPlayerState.queueIndex,
                    isPlaying = isPlaying,
                    onTrackClick = { clickedTrack: MusicTrack ->
                        MusicPlayerState.play(clickedTrack, null)
                    },
                    onRemoveTrack = { idx: Int ->
                        MusicPlayerState.removeFromQueue(idx)
                    },
                    onUserInteraction = onLyricsInteraction,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── 5. Continuous Morphing Artwork Avatar ──
        // Full bleed across top in music mode, smoothly shrinks into 44dp rounded corner thumbnail
        // in top left corner when lyrics or queue opens!
        // Given zIndex(35f) to ensure taps on the avatar directly collapse the panel.
        val artTop = lerpDp(0.dp, statusBarTopDp + 6.dp, panelProgress)
        val artStart = lerpDp(0.dp, 16.dp, panelProgress)
        val artWidth = lerpDp(maxWidth, 44.dp, panelProgress)
        val artHeight = lerpDp(fullBleedHeight, 44.dp, panelProgress)
        val artRadius = lerpDp(0.dp, 10.dp, panelProgress)

        Box(
            modifier = Modifier
                .zIndex(35f)
                .offset { IntOffset(artStart.roundToPx(), artTop.roundToPx()) }
                .size(width = artWidth, height = artHeight)
                .clip(RoundedCornerShape(artRadius))
                .then(
                    if (panelProgress > 0.85f) {
                        Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.22f * panelProgress), RoundedCornerShape(artRadius))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                MusicPlayerState.lyricsOpen = false
                                MusicPlayerState.queueOpen = false
                            }
                    } else Modifier
                )
        ) {
            val playerFadeWeight = (1f - panelProgress * 8f).coerceIn(0f, 1f)
            val fadeStart = lerpFloat(1.0f, 0.72f, playerFadeWeight)
            val fadeEndAlpha = lerpFloat(1.0f, 0.0f, playerFadeWeight)

            key(track.id) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl)
                        .crossfade(200)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            if (fadeStart < 0.999f || fadeEndAlpha < 0.999f) {
                                val mask = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Black,
                                        fadeStart to Color.Black,
                                        1f to Color.Black.copy(alpha = fadeEndAlpha)
                                    )
                                )
                                drawRect(brush = mask, blendMode = BlendMode.DstIn)
                            }
                        }
                )
            }
        }

        // ── 6. Top Navigation Bar ──
        Box(
            modifier = Modifier
                .zIndex(25f)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // iOS Pull Handle Pill (Visible in full player mode)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .size(width = 38.dp, height = 4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.32f))
                    .graphicsLayer {
                        alpha = (1f - panelProgress * 2.5f).coerceIn(0f, 1f)
                    }
            )

            // Normal Collapse Chevron (fades out in lyrics or queue mode)
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        alpha = (1f - panelProgress * 2.5f).coerceIn(0f, 1f)
                    },
                enabled = panelProgress < 0.2f
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(36.dp)
                )
            }

            // Lyrics / Queue Mode Header Title & Artist (Placed right next to morphing avatar thumbnail)
            // Also clickable to collapse back to the main full artwork player!
            if (panelProgress > 0.01f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 70.dp, end = 52.dp)
                        .align(Alignment.CenterStart)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            MusicPlayerState.lyricsOpen = false
                            MusicPlayerState.queueOpen = false
                        }
                        .graphicsLayer {
                            alpha = panelProgress
                            translationX = (1f - panelProgress) * 20.dp.toPx()
                        },
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.W700,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = track.artists,
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.W500,
                            fontSize = 12.5.sp,
                            lineHeight = 15.sp
                        ),
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            // Top More Menu Button
            IconButton(
                onClick = { onLyricsInteraction() },
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.CenterEnd)
            ) {
                Image(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = "More Options",
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Fast 2x Speed Pill indicator
        FastHoldPill(
            shown = MusicPlayerState.holdingFast,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 58.dp)
        )

        // ── 7. Bottom Controls Anchored Column ──
        Column(
            modifier = Modifier
                .zIndex(25f)
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 18.dp)
                .graphicsLayer {
                    translationY = controlsTranslationY
                    alpha = controlsAlpha
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title and Artist Row (slides down smoothly in lyrics mode)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .graphicsLayer {
                        alpha = (1f - panelProgress * 2f).coerceIn(0f, 1f)
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(unbounded = true)
                        .padding(end = 4.dp)
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            translationY = -48.dp.toPx()
                        }
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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = track.artists,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Wavy Seek Bar (AirBeats Expressive Squiggly Slider)
            WavySlider(
                value = displayPos.toFloat(),
                onValueChange = {
                    onLyricsInteraction()
                    isSeeking = true
                    seekPosition = it.toLong().coerceIn(0L, safeDuration)
                },
                onValueChangeFinished = {
                    onLyricsInteraction()
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

            // Time & Audio Quality Tag Row
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
                        .clickable { onLyricsInteraction() }
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
                                withStyle(SpanStyle(color = Color(0xFFFF1744))) { append("i") }
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

            Spacer(Modifier.height(24.dp))

            // ── Main 3 Playback Controls (Previous, Play/Pause, Next) ONLY ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous
                IconButton(
                    onClick = {
                        onLyricsInteraction()
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

                // Play / Pause
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .clickable {
                            onLyricsInteraction()
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

                // Next
                IconButton(
                    onClick = {
                        onLyricsInteraction()
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

            Spacer(Modifier.height(18.dp))

            // ── Volume Slider (iOS Capsule Style) ──
            IosVolumeSlider(
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(18.dp))

            // ── Bottom Action Controls (Lyrics on Left, Shuffle/Repeat Capsule in Center when panel open, Queue on Right) ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Left down corner: Lyrics Button
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLyricsOpen) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable {
                            onLyricsInteraction()
                            if (isLyricsOpen) {
                                MusicPlayerState.lyricsOpen = false
                            } else {
                                MusicPlayerState.queueOpen = false
                                MusicPlayerState.lyricsOpen = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.lyrics),
                        contentDescription = "Lyrics",
                        colorFilter = ColorFilter.tint(
                            if (isLyricsOpen) Color.White else Color.White.copy(alpha = 0.90f)
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 2. Center: Shuffle & Repeat Capsule (Only shown when queue or lyrics is open, else empty space)
                AnimatedVisibility(
                    visible = isPanelOpen,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Shuffle
                            IconButton(
                                onClick = {
                                    onLyricsInteraction()
                                    MusicPlayerState.toggleShuffle()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        if (MusicPlayerState.shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle
                                    ),
                                    contentDescription = "Shuffle",
                                    tint = if (MusicPlayerState.shuffleEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Repeat
                            IconButton(
                                onClick = {
                                    onLyricsInteraction()
                                    MusicPlayerState.toggleRepeat()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        when (MusicPlayerState.repeatMode) {
                                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                                            Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                                            else -> R.drawable.repeat
                                        }
                                    ),
                                    contentDescription = "Repeat",
                                    tint = if (MusicPlayerState.repeatMode != Player.REPEAT_MODE_OFF) Color.White else Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Right down corner: Queue Button (Replaces previous radio icon)
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (isQueueOpen) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f)
                        )
                        .clickable {
                            onLyricsInteraction()
                            if (isQueueOpen) {
                                MusicPlayerState.queueOpen = false
                            } else {
                                MusicPlayerState.lyricsOpen = false
                                MusicPlayerState.queueOpen = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = "Queue",
                        colorFilter = ColorFilter.tint(
                            if (isQueueOpen) Color.White else Color.White.copy(alpha = 0.90f)
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * In-Player Queue View with Shuffle & Repeat pills, Now Playing item with equalizer,
 * and upcoming queue tracks list.
 */
@Composable
private fun InPlayerQueueContent(
    currentTrack: MusicTrack,
    queue: List<MusicTrack>,
    currentIndex: Int,
    isPlaying: Boolean,
    onTrackClick: (MusicTrack) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onUserInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val queueSnapshot = queue.toList()
    val upcomingItems: List<MusicTrack> = remember(queueSnapshot, currentIndex) {
        if (currentIndex in 0 until queueSnapshot.size - 1) {
            queueSnapshot.drop(currentIndex + 1)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            onUserInteraction()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {
            onUserInteraction()
        },
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // "Queue" Title Row
        item(key = "header_queue") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queue",
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.W800,
                        fontSize = 24.sp,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White
                )
            }
        }

        // "Now playing" Subtitle
        item(key = "header_now_playing") {
            Text(
                text = "Now playing",
                style = TextStyle(
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.W600,
                    fontSize = 15.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // Currently Playing Track
        item(key = "now_playing_track") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = currentTrack.thumbnailUrl?.highQualityThumbnail(),
                    contentDescription = currentTrack.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentTrack.title,
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.W700,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = currentTrack.artists,
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.W400,
                            fontSize = 13.sp,
                            lineHeight = 16.sp
                        ),
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(10.dp))

                AnimatedEqualizer(
                    isPlaying = isPlaying,
                    color = Color.White,
                    size = 20.dp
                )

                Spacer(Modifier.width(12.dp))

                IconButton(
                    onClick = {
                        if (currentIndex in queue.indices) {
                            onRemoveTrack(currentIndex)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove",
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // "Next up" Section Header
        if (upcomingItems.isNotEmpty()) {
            item(key = "header_next_up") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "Next up",
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.W700,
                            fontSize = 16.sp,
                            letterSpacing = (-0.2).sp
                        ),
                        color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Queued tracks to play next",
                        style = TextStyle(
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.W400,
                            fontSize = 13.sp
                        ),
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }

            itemsIndexed(
                items = upcomingItems,
                key = { idx, item -> "upcoming_${item.id}_$idx" }
            ) { relativeIdx, item ->
                val actualIdx = currentIndex + 1 + relativeIdx
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onTrackClick(item) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "Reorder",
                        tint = Color.White.copy(alpha = 0.40f),
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    AsyncImage(
                        model = item.thumbnailUrl?.highQualityThumbnail(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.title,
                            style = TextStyle(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.W600,
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                letterSpacing = (-0.3).sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.artists,
                            style = TextStyle(
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.W400,
                                fontSize = 13.sp,
                                lineHeight = 16.sp
                            ),
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = { onRemoveTrack(actualIdx) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove",
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * iOS-style volume slider with min/max speaker icons and capsule track drag.
 */
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
            modifier = Modifier
                .size(19.dp)
                .clickable {
                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                    currentVolume = maxVolume
                    dragFraction = 1f
                }
        )
    }
}

/**
 * 2x Fast Hold pill feedback badge.
 */
@Composable
fun FastHoldPill(
    shown: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -it / 2 },
        exit = fadeOut(tween(250)),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.80f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color(0xFFFA2D48)),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "2X Fast Speed",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
