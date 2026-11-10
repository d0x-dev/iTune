package com.darkxvenom.itune.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import coil.size.Size
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.components.WavySlider
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    (start.value + (stop.value - start.value) * fraction.coerceIn(0f, 1f)).coerceAtLeast(0f).dp

private fun lerpSp(start: TextUnit, stop: TextUnit, fraction: Float): TextUnit =
    (start.value + (stop.value - start.value) * fraction.coerceIn(0f, 1f)).coerceAtLeast(1f).sp

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * f
}

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
    sheetProgress: Float = 1f,
    onCollapse: () -> Unit = { MusicPlayerState.expanded = false },
    modifier: Modifier = Modifier
) {
    val track = MusicPlayerState.currentTrack ?: return
    val sp = sheetProgress.coerceIn(0f, 1f)
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
        animationSpec = spring(
            dampingRatio = 0.90f,
            stiffness = 170f
        ),
        label = "panelProgress"
    )

    var rootBoxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var playerTitleVisualTopPx by remember { mutableFloatStateOf(0f) }
    var playerControlsVisualTopPx by remember { mutableFloatStateOf(0f) }
    var playerNextVisualLeftPx by remember { mutableFloatStateOf(0f) }
    var playerNextVisualTopPx by remember { mutableFloatStateOf(0f) }

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
    var isMoreMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sp) {
        if (sp < 0.85f && isMoreMenuOpen) {
            isMoreMenuOpen = false
        }
    }

    LaunchedEffect(isLyricsOpen, lastLyricsInteractionTime, areLyricsControlsVisible, isSeeking, isMoreMenuOpen) {
        if (isLyricsOpen && areLyricsControlsVisible && !isSeeking && !isMoreMenuOpen) {
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
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 170f),
        label = "lyricsControlsTranslationY"
    )

    val controlsAlpha by animateFloatAsState(
        targetValue = if (!isLyricsOpen || areLyricsControlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "lyricsControlsAlpha"
    )

    val contentBottomPadding by animateDpAsState(
        targetValue = if (!areLyricsControlsVisible && isLyricsOpen) 24.dp else 295.dp,
        animationSpec = spring(dampingRatio = 0.90f, stiffness = 170f),
        label = "contentBottomPadding"
    )


    val safeDuration = if (duration > 0) duration else (track.durationSec * 1000L).coerceAtLeast(1L)
    val displayPos = if (isSeeking) seekPosition else position.coerceIn(0L, safeDuration)
    val remaining = (safeDuration - displayPos).coerceAtLeast(0L)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { rootBoxCoords = it }
    ) {
        val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottomDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val fullBleedHeight = maxHeight * 0.56f

        // ── 1. Fullscreen Ambient Blurred Artwork Layer ──
        val blurScale by animateFloatAsState(
            targetValue = if (isPanelOpen) 1.45f else 1.35f,
            animationSpec = spring(dampingRatio = 0.90f, stiffness = 170f),
            label = "blurScale"
        )

        val currentArtUrl = track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl

        Crossfade(
            targetState = currentArtUrl,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            label = "ambientBlurCrossfade",
            modifier = Modifier.fillMaxSize()
        ) { artUrl ->
            if (!artUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUrl)
                        .size(Size.ORIGINAL)
                        .crossfade(200)
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
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            }
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
                    .padding(top = 96.dp)
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
                    .padding(top = 96.dp)
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
        // Full bleed across top in music mode, smoothly shrinks into 58dp rounded corner thumbnail
        // spaced comfortably below the drag handle pill when lyrics or queue opens.
        val progressClamped = panelProgress.coerceIn(0f, 1f)
        val fullArtStart = lerpDp(0.dp, 24.dp, panelProgress)
        val fullArtTop = lerpDp(0.dp, statusBarTopDp + 34.dp, panelProgress)
        val fullArtWidth = lerpDp(maxWidth, 58.dp, panelProgress).coerceAtLeast(1.dp)
        val fullArtHeight = lerpDp(fullBleedHeight, 58.dp, panelProgress).coerceAtLeast(1.dp)
        val fullArtRadius = lerpDp(0.dp, 11.dp, progressClamped).coerceAtLeast(0.dp)

        val miniArtStart = 10.dp
        val miniArtTop = 10.dp
        val miniArtWidth = 38.dp
        val miniArtHeight = 38.dp
        val miniArtRadius = 6.dp

        val artStart = lerpDp(miniArtStart, fullArtStart, sp)
        val artTop = lerpDp(miniArtTop, fullArtTop, sp)
        val artWidth = lerpDp(miniArtWidth, fullArtWidth, sp)
        val artHeight = lerpDp(miniArtHeight, fullArtHeight, sp)
        val artRadius = lerpDp(miniArtRadius, fullArtRadius, sp).coerceAtLeast(0.dp)

        Box(
            modifier = Modifier
                .zIndex(35f)
                .offset { IntOffset(artStart.roundToPx(), artTop.roundToPx()) }
                .size(width = artWidth, height = artHeight)
                .clip(RoundedCornerShape(artRadius))
                .then(
                    if (panelProgress > 0.85f) {
                        Modifier
                            .border(1.dp, Color.White.copy(alpha = (0.22f * panelProgress).coerceIn(0f, 1f)), RoundedCornerShape(artRadius))
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

            Crossfade(
                targetState = currentArtUrl,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                label = "avatarArtworkCrossfade",
                modifier = Modifier.fillMaxSize()
            ) { artUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUrl)
                        .size(Size.ORIGINAL)
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

        // ── 5b. Continuous Morphing Title & Artist ──
        // Seamlessly morphs between Mini Player position/size and Full Player resting position/size,
        // and glides into the top header when Queue or Lyrics opens.
        val defaultPlayerTitleTopDp = maxHeight - 340.dp - navBottomDp
        val startTitleTopPx = if (playerTitleVisualTopPx > 0f) playerTitleVisualTopPx else with(density) { defaultPlayerTitleTopDp.toPx() }

        val fullTitleStartPx = lerpFloat(with(density) { 24.dp.toPx() }, with(density) { 96.dp.toPx() }, panelProgress)
        // Center Title + Artist vertically against the 58dp header avatar (centerY = statusBarTopDp + 63dp)
        val fullTitleTopPx = lerpFloat(startTitleTopPx, with(density) { (statusBarTopDp + 43.dp).toPx() }, panelProgress)
        val fullTitleEndPx = lerpFloat(with(density) { 114.dp.toPx() }, with(density) { 112.dp.toPx() }, panelProgress)
        val fullTitleFontSize = lerpSp(24.sp, 16.5.sp, progressClamped)
        val fullTitleTargetLineHeight = lerpSp(28.sp, 21.sp, progressClamped)
        val fullArtistFontSize = lerpSp(15.sp, 13.5.sp, progressClamped)
        val fullArtistTargetLineHeight = lerpSp(19.sp, 17.sp, progressClamped)

        val miniTitleStartPx = with(density) { 58.dp.toPx() }
        val miniTitleTopPx = with(density) { 10.dp.toPx() }
        val miniTitleEndPx = with(density) { 86.dp.toPx() }
        val miniTitleFontSize = 14.sp
        val miniArtistFontSize = 12.sp

        val curTitleTopPx = lerpFloat(miniTitleTopPx, fullTitleTopPx, sp)
        val curTitleLeftPx = lerpFloat(miniTitleStartPx, fullTitleStartPx, sp)
        val curTitleEndPx = lerpFloat(miniTitleEndPx, fullTitleEndPx, sp)

        val curTitleFontSize = lerpSp(miniTitleFontSize, fullTitleFontSize, sp)
        val curTitleLineHeight = lerpSp(18.sp, fullTitleTargetLineHeight, sp)
        val curArtistFontSize = lerpSp(miniArtistFontSize, fullArtistFontSize, sp)
        val curArtistLineHeight = lerpSp(15.sp, fullArtistTargetLineHeight, sp)
        val curArtistAlpha = lerpFloat(0.65f, 0.72f, sp).coerceIn(0f, 1f)
        val curTitleWidthDp = with(density) { (maxWidth.toPx() - curTitleLeftPx - curTitleEndPx).coerceAtLeast(0f).toDp() }

        Box(
            modifier = Modifier
                .zIndex(36f)
                .offset { IntOffset(curTitleLeftPx.roundToInt(), curTitleTopPx.roundToInt()) }
                .width(curTitleWidthDp)
                .height(if (sp < 0.5f) 38.dp else 48.dp)
                .then(
                    if (panelProgress > 0.80f) {
                        Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            MusicPlayerState.lyricsOpen = false
                            MusicPlayerState.queueOpen = false
                        }
                    } else Modifier
                )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = curTitleFontSize,
                        lineHeight = curTitleLineHeight,
                        letterSpacing = lerpSp((-0.2).sp, (-0.3).sp, panelProgress)
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(Modifier.height(lerpDp(lerpDp(1.dp, 3.dp, sp), 2.dp, panelProgress)))
                Text(
                    text = track.artists,
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontWeight = if (panelProgress > 0.5f) FontWeight.W500 else FontWeight.SemiBold,
                        fontSize = curArtistFontSize,
                        lineHeight = curArtistLineHeight
                    ),
                    color = Color.White.copy(alpha = curArtistAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        // ── 5c. Continuous Morphing Play/Pause & Next Buttons ──
        // Smoothly glides and scales between top-right mini player position and center full player playback row
        val panelControlsTransY = if (isPanelOpen) controlsTranslationY else 0f
        val panelControlsAlpha = if (isPanelOpen) controlsAlpha else 1f

        val fallbackPlayTopPx = with(density) { (maxHeight - 210.dp - navBottomDp).toPx() }
        val fullPlayTopPx = if (playerControlsVisualTopPx > 0f) playerControlsVisualTopPx else fallbackPlayTopPx

        val miniPlayLeftPx = with(density) { (maxWidth - 84.dp).toPx() }
        val miniPlayTopPx = with(density) { 11.dp.toPx() }
        val fullPlayLeftPx = with(density) { (maxWidth / 2 - 36.dp).toPx() }

        val miniNextLeftPx = with(density) { (maxWidth - 44.dp).toPx() }
        val miniNextTopPx = with(density) { 11.dp.toPx() }
        val fallbackNextLeftPx = with(density) { (maxWidth / 2 + 76.dp).toPx() }
        val fullNextLeftPx = if (playerNextVisualLeftPx > 0f) playerNextVisualLeftPx else fallbackNextLeftPx
        val fullNextTopPx = if (playerNextVisualTopPx > 0f) playerNextVisualTopPx else fullPlayTopPx

        val curPlayLeftPx = lerpFloat(miniPlayLeftPx, fullPlayLeftPx, sp)
        val curPlayTopPx = lerpFloat(miniPlayTopPx, fullPlayTopPx + panelControlsTransY, sp)
        val curPlaySizeDp = lerpDp(36.dp, 72.dp, sp)
        val curPlayIconSizeDp = lerpDp(24.dp, 48.dp, sp)

        val curNextLeftPx = lerpFloat(miniNextLeftPx, fullNextLeftPx, sp)
        val curNextTopPx = lerpFloat(miniNextTopPx, fullNextTopPx + panelControlsTransY, sp)
        val curNextSizeDp = lerpDp(36.dp, 72.dp, sp)
        val curNextIconSizeDp = lerpDp(22.dp, 52.dp, sp)

        // Morphing Play/Pause Button
        Box(
            modifier = Modifier
                .zIndex(40f)
                .offset { IntOffset(curPlayLeftPx.roundToInt(), curPlayTopPx.roundToInt()) }
                .size(curPlaySizeDp)
                .graphicsLayer { alpha = panelControlsAlpha }
                .clip(CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onLyricsInteraction()
                    MusicPlayerState.togglePlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading && !isPlaying && sp > 0.4f) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(curPlayIconSizeDp * 0.9f)
                )
            } else {
                Image(
                    painter = painterResource(
                        if (isPlaying) R.drawable.pause else R.drawable.play
                    ),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(curPlayIconSizeDp)
                )
            }
        }

        // Morphing Next Button
        Box(
            modifier = Modifier
                .zIndex(40f)
                .offset { IntOffset(curNextLeftPx.roundToInt(), curNextTopPx.roundToInt()) }
                .size(curNextSizeDp)
                .graphicsLayer { alpha = panelControlsAlpha }
                .clip(CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onLyricsInteraction()
                    MusicPlayerState.skipNext()
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.skip_next),
                contentDescription = "Next",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(curNextIconSizeDp)
            )
        }

        val fullElementsAlpha = ((sp - 0.22f) / 0.78f).coerceIn(0f, 1f)
        val fullElementsTransY = (1f - sp) * with(density) { 50.dp.toPx() }

        val isFavorite = MusicPlayerState.isFavorite(track.id)

        // ── 5d. Continuous Morphing Star (Favorite / Like) Button ──
        // Initially positioned beside the 3-dot button at the end of the Title & Artist row in player mode (matching Apple Music),
        // glides smoothly upward into the top header next to the 3-dot button when Queue or Lyrics opens.
        // Kept at the exact same size (36dp, 20dp icon) at both resting player and top header positions.
        val startStarTopPx = startTitleTopPx + with(density) { 6.dp.toPx() }
        // Keep Star vertically centered with 58dp avatar (centerY = statusBarTopDp + 63dp -> top = 63 - 18 = 45dp)
        val targetStarTopPx = with(density) { (statusBarTopDp + 45.dp).toPx() }
        val startStarLeftPx = with(density) { (maxWidth - 104.dp).toPx() }
        val targetStarLeftPx = with(density) { (maxWidth - 104.dp).toPx() }

        val curStarTopPx = lerpFloat(startStarTopPx, targetStarTopPx, panelProgress)
        val curStarLeftPx = lerpFloat(startStarLeftPx, targetStarLeftPx, panelProgress)
        val curStarSizeDp = 36.dp
        val curStarIconSizeDp = 20.dp
        val curStarBgAlpha = if (isFavorite) 0.22f else 0.16f

        Box(
            modifier = Modifier
                .zIndex(37f)
                .offset { IntOffset(curStarLeftPx.roundToInt(), (curStarTopPx + fullElementsTransY).roundToInt()) }
                .size(curStarSizeDp)
                .graphicsLayer { alpha = fullElementsAlpha }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = curStarBgAlpha))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    MusicPlayerState.toggleFavorite(track)
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(
                    if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                ),
                contentDescription = if (isFavorite) "Favorited" else "Favorite",
                colorFilter = ColorFilter.tint(
                    if (isFavorite) Color.White else Color.White.copy(alpha = 0.90f)
                ),
                modifier = Modifier.size(curStarIconSizeDp)
            )
        }

        // ── 5e. Continuous Morphing More Button ──
        // Initially positioned at the far right end of the Title & Artist row in player mode (matching Apple Music),
        // glides smoothly upward into the top-right header corner when Queue or Lyrics opens.
        // Kept at the exact same size (36dp, 22dp icon) at both resting player and top header positions.
        val startMoreTopPx = startTitleTopPx + with(density) { 6.dp.toPx() }
        // Keep 3-dot vertically centered with 58dp avatar (centerY = statusBarTopDp + 63dp -> top = 63 - 18 = 45dp)
        val targetMoreTopPx = with(density) { (statusBarTopDp + 45.dp).toPx() }
        val startMoreLeftPx = with(density) { (maxWidth - 60.dp).toPx() }
        val targetMoreLeftPx = with(density) { (maxWidth - 60.dp).toPx() }

        val curMoreTopPx = lerpFloat(startMoreTopPx, targetMoreTopPx, panelProgress)
        val curMoreLeftPx = lerpFloat(startMoreLeftPx, targetMoreLeftPx, panelProgress)
        val curMoreSizeDp = 36.dp
        val curMoreIconSizeDp = 22.dp
        val curMoreBgAlpha = 0.16f

        Box(
            modifier = Modifier
                .zIndex(37f)
                .offset { IntOffset(curMoreLeftPx.roundToInt(), (curMoreTopPx + fullElementsTransY).roundToInt()) }
                .size(curMoreSizeDp)
                .graphicsLayer { alpha = fullElementsAlpha }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = curMoreBgAlpha))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    lastLyricsInteractionTime = System.currentTimeMillis()
                    areLyricsControlsVisible = true
                    isMoreMenuOpen = !isMoreMenuOpen
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = "More Options",
                colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
                modifier = Modifier.size(curMoreIconSizeDp)
            )
        }

        // ── 6. Top Navigation Bar ──
        Box(
            modifier = Modifier
                .zIndex(25f)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // iOS Pull Handle Pill (Centered at top in both full player and queue/lyrics header)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
                    .size(width = 38.dp, height = 4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.34f))
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

            // Full Header Tap Target to collapse back to main player in Queue or Lyrics mode
            if (panelProgress > 0.8f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, start = 10.dp, end = 102.dp)
                        .height(54.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            MusicPlayerState.lyricsOpen = false
                            MusicPlayerState.queueOpen = false
                        }
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
                    alpha = controlsAlpha * fullElementsAlpha
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Anchor placeholder for the morphing Title & Artist
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .onGloballyPositioned { coords ->
                        val root = rootBoxCoords
                        if (root != null && root.isAttached && coords.isAttached) {
                            val pos = root.localPositionOf(coords, Offset.Zero)
                            val visualTop = pos.y
                            if (visualTop > 0f) {
                                playerTitleVisualTopPx = visualTop
                            }
                        }
                    }
            )

            Spacer(Modifier.height(18.dp))

            // iOS Capsule Seek Bar (Exact same UI as Volume Slider, no waves)
            IosCapsuleSeekSlider(
                value = displayPos.toFloat(),
                valueRange = 0f..safeDuration.toFloat(),
                isSeeking = isSeeking,
                onSeekStart = { targetVal ->
                    onLyricsInteraction()
                    isSeeking = true
                    seekPosition = targetVal.toLong().coerceIn(0L, safeDuration)
                },
                onSeekChange = { targetVal ->
                    seekPosition = targetVal.toLong().coerceIn(0L, safeDuration)
                },
                onSeekEnd = { targetVal ->
                    onLyricsInteraction()
                    val targetPos = targetVal.toLong().coerceIn(0L, safeDuration)
                    seekPosition = targetPos
                    MusicPlayerState.seekTo(targetPos)
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(6.dp))

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
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        val root = rootBoxCoords
                        if (root != null && root.isAttached && coords.isAttached) {
                            val pos = root.localPositionOf(coords, Offset.Zero)
                            if (pos.y > 0f) {
                                playerControlsVisualTopPx = pos.y
                            }
                        }
                    },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous (balanced 52dp icon in 72dp touch target)
                IconButton(
                    onClick = {
                        onLyricsInteraction()
                        MusicPlayerState.skipPrevious()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer { alpha = fullElementsAlpha }
                ) {
                    Image(
                        painter = painterResource(R.drawable.skip_previous),
                        contentDescription = "Previous",
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(52.dp)
                    )
                }

                // Layout Placeholder for Morphing Play / Pause
                Spacer(modifier = Modifier.size(72.dp))

                // Layout Placeholder for Morphing Next
                Spacer(
                    modifier = Modifier
                        .size(72.dp)
                        .onGloballyPositioned { coords ->
                            val root = rootBoxCoords
                            if (root != null && root.isAttached && coords.isAttached) {
                                val pos = root.localPositionOf(coords, Offset.Zero)
                                if (pos.x > 0f) {
                                    playerNextVisualLeftPx = pos.x
                                }
                                if (pos.y > 0f) {
                                    playerNextVisualTopPx = pos.y
                                }
                            }
                        }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Volume Slider (iOS Capsule Style) ──
            IosVolumeSlider(
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(Modifier.height(14.dp))

            // ── Bottom Action Controls (Bigger, high-contrast icons with balanced horizontal spacing like iOS Apple Music) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Left: Lyrics Button
                BottomPanelToggleButton(
                    iconRes = R.drawable.lyrics,
                    contentDescription = "Lyrics",
                    iconSize = 25.dp,
                    active = isLyricsOpen,
                    onClick = {
                        onLyricsInteraction()
                        if (isLyricsOpen) {
                            MusicPlayerState.lyricsOpen = false
                        } else {
                            MusicPlayerState.queueOpen = false
                            MusicPlayerState.lyricsOpen = true
                        }
                    }
                )

                // 2. Center: Shuffle & Repeat Split Capsule (When panel open) OR Vector Airbuds (When player in main mode)
                Box(
                    modifier = Modifier.width(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPanelOpen,
                        transitionSpec = {
                            fadeIn(tween(200, easing = LinearOutSlowInEasing)) +
                                scaleIn(initialScale = 0.92f, animationSpec = tween(200, easing = LinearOutSlowInEasing)) togetherWith
                                fadeOut(tween(160, easing = FastOutLinearInEasing)) +
                                scaleOut(targetScale = 0.92f, animationSpec = tween(160, easing = FastOutLinearInEasing))
                        },
                        label = "centerControlsTransition"
                    ) { panelOpen ->
                        if (panelOpen) {
                            ShuffleRepeatSplitCapsule(
                                shuffleEnabled = MusicPlayerState.shuffleEnabled,
                                repeatMode = MusicPlayerState.repeatMode,
                                onToggleShuffle = {
                                    onLyricsInteraction()
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    MusicPlayerState.toggleShuffle()
                                },
                                onToggleRepeat = {
                                    onLyricsInteraction()
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    MusicPlayerState.toggleRepeat()
                                }
                            )
                        } else {
                            // Crisp vector Airbuds centered between Lyrics and Queue
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onLyricsInteraction()
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_airbuds),
                                    contentDescription = "Audio Output",
                                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.92f)),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(35.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Right: Queue Button
                BottomPanelToggleButton(
                    iconRes = R.drawable.queue_music,
                    contentDescription = "Queue",
                    iconSize = 26.dp,
                    active = isQueueOpen,
                    onClick = {
                        onLyricsInteraction()
                        if (isQueueOpen) {
                            MusicPlayerState.queueOpen = false
                        } else {
                            MusicPlayerState.lyricsOpen = false
                            MusicPlayerState.queueOpen = true
                        }
                    }
                )
            }
        }

        // ── 8. Apple-Styled Floating More Options Popup (Water-Drop Flow Origin from 3-Dot Button) ──
        val moreAnchorXPx = curMoreLeftPx + with(density) { (curMoreSizeDp / 2).toPx() }
        val moreAnchorYPx = curMoreTopPx + fullElementsTransY + with(density) { (curMoreSizeDp / 2).toPx() }
        val restingTitleRowTopDp = with(density) { startTitleTopPx.toDp() }

        AppleMoreOptionsPopup(
            visible = isMoreMenuOpen,
            track = track,
            artUrl = currentArtUrl,
            anchorCenterXPx = moreAnchorXPx,
            anchorCenterYPx = moreAnchorYPx,
            isPanelOpen = isPanelOpen,
            statusBarTopDp = statusBarTopDp,
            titleRowTopDp = restingTitleRowTopDp,
            maxHeightDp = maxHeight,
            rootCoordsProvider = { rootBoxCoords },
            onDismiss = {
                lastLyricsInteractionTime = System.currentTimeMillis()
                isMoreMenuOpen = false
            },
            modifier = Modifier.zIndex(80f)
        )
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
                val itemContext = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(itemContext)
                        .data(currentTrack.thumbnailUrl?.highQualityThumbnail() ?: currentTrack.thumbnailUrl)
                        .memoryCacheKey("${currentTrack.id}_queue_thumb_46")
                        .crossfade(200)
                        .build(),
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

                    val itemContext = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(itemContext)
                            .data(item.thumbnailUrl?.highQualityThumbnail() ?: item.thumbnailUrl)
                            .memoryCacheKey("${item.id}_queue_thumb_46")
                            .crossfade(200)
                            .build(),
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
 * iOS-style non-wavy capsule seek bar matching the exact visual style and geometry
 * of the iOS volume slider (capsule track with 50% rounded corners).
 */
@Composable
internal fun IosCapsuleSeekSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    isSeeking: Boolean,
    onSeekStart: (Float) -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    var dragVal by remember { mutableFloatStateOf(value) }

    val displayFraction = if (isSeeking) {
        ((dragVal - valueRange.start) / duration).coerceIn(0f, 1f)
    } else {
        ((value - valueRange.start) / duration).coerceIn(0f, 1f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = displayFraction,
        animationSpec = if (isSeeking) snap() else tween(durationMillis = 250, easing = LinearEasing),
        label = "seekSliderFraction"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    val w = size.width.toFloat()
                    if (w > 0f) {
                        val fraction = (offset.x / w).coerceIn(0f, 1f)
                        val targetVal = valueRange.start + fraction * duration
                        dragVal = targetVal
                        onSeekStart(targetVal)
                        onSeekEnd(targetVal)
                    }
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        if (w > 0f) {
                            val fraction = (offset.x / w).coerceIn(0f, 1f)
                            val targetVal = valueRange.start + fraction * duration
                            dragVal = targetVal
                            onSeekStart(targetVal)
                        }
                    },
                    onDragEnd = {
                        onSeekEnd(dragVal)
                    },
                    onDragCancel = {
                        onSeekEnd(dragVal)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        if (w > 0f) {
                            val fraction = (change.position.x / w).coerceIn(0f, 1f)
                            val targetVal = valueRange.start + fraction * duration
                            dragVal = targetVal
                            onSeekChange(targetVal)
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackHeight = 7.5.dp
        val trackShape = RoundedCornerShape(percent = 50)

        // Inactive background capsule track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(trackShape)
                .background(Color.White.copy(alpha = 0.22f))
        )

        // Active progress capsule track
        if (animatedFraction > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(trackHeight)
                    .clip(trackShape)
                    .background(Color.White.copy(alpha = 0.85f))
            )
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

/**
 * Apple-styled frosted Shuffle / Repeat pill split cleanly down the center by a vertical break.
 */
@Composable
private fun ShuffleRepeatSplitCapsule(
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repeatIcon = when (repeatMode) {
        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
        Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
        else -> R.drawable.repeat
    }

    // Offscreen compositing lets BlendMode.Clear punch an exact transparent break through both fill and glass border.
    Box(
        modifier = modifier
            .width(96.dp)
            .height(34.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val gapPx = 1.5.dp.toPx()
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset((size.width - gapPx) / 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(gapPx, size.height),
                    blendMode = BlendMode.Clear
                )
            }
            .clip(CircleShape)
            .border(
                width = 0.6.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.10f)
                    )
                ),
                shape = CircleShape
            )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            SplitCapsuleSegment(
                iconRes = if (shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                contentDescription = "Shuffle",
                active = shuffleEnabled,
                opticalOffsetStart = 2.dp,
                opticalOffsetEnd = 0.dp,
                onClick = onToggleShuffle,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            SplitCapsuleSegment(
                iconRes = repeatIcon,
                contentDescription = "Repeat",
                active = repeatMode != Player.REPEAT_MODE_OFF,
                opticalOffsetStart = 0.dp,
                opticalOffsetEnd = 2.dp,
                onClick = onToggleRepeat,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun SplitCapsuleSegment(
    iconRes: Int,
    contentDescription: String,
    active: Boolean,
    opticalOffsetStart: Dp,
    opticalOffsetEnd: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = if (active) 0.28f else 0.18f))
            .clickable(onClick = onClick)
            .padding(start = opticalOffsetStart, end = opticalOffsetEnd),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (active) 1f else 0.62f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun BottomPanelToggleButton(
    iconRes: Int,
    contentDescription: String,
    iconSize: Dp,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(
                Color.White.copy(alpha = if (active) 1f else 0.90f)
            ),
            modifier = Modifier.size(iconSize)
        )
    }
}
