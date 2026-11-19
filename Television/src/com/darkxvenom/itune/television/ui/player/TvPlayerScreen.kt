/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.television.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.LyricsLine
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.backend.lyrics.LyricsHelper
import com.darkxvenom.itune.data.artist.CanvasArtwork
import com.darkxvenom.itune.data.canvas.CanvasRepository
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.ishare.IShareSyncManager
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.television.ui.common.TvAmbientBackground
import com.darkxvenom.itune.ui.artist.canvas.CanvasArtworkPlayer
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.artist.canvas.CanvasContentMode
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.ceil

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private enum class TvPlayerMode { NOW_PLAYING, QUEUE, LYRICS }

private const val CONTROLS_HIDE_DELAY_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L

/**
 * 10-Foot Television Player matching Apple Music on Apple TV (tvOS):
 * 1. Default initial mode: Sleek Single Centered Artwork with album title, metadata, and bottom controls.
 * 2. Queue mode: Compact 3-card carousel (Previous, Current, Next) toggled via the Queue button.
 * 3. Lyrics mode: Split view with artwork on left and real-time synchronized lyrics on right, with auto-hiding controls.
 * Uses official app drawable resources directly from app/res/drawable.
 */
@Composable
fun TvPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: (browseId: String?, name: String, thumb: String?) -> Unit = { _, _, _ -> },
) {
    var mode by remember { mutableStateOf(TvPlayerMode.NOW_PLAYING) }
    var popupOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val track = MusicPlayerState.currentTrack
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val canvas = rememberTvCanvas(track, settings.animatedArtwork)

    BackHandler {
        when {
            popupOpen -> popupOpen = false
            mode != TvPlayerMode.NOW_PLAYING -> mode = TvPlayerMode.NOW_PLAYING
            else -> onBack()
        }
    }

    // Controls auto-hide in lyrics mode after 3.5s; reappear on remote interaction
    LaunchedEffect(mode) {
        if (mode == TvPlayerMode.LYRICS) {
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }

    LaunchedEffect(mode, popupOpen, lastInteraction) {
        if (mode == TvPlayerMode.LYRICS && !popupOpen && controlsVisible) {
            delay(3500L)
            controlsVisible = false
        }
    }

    val onKey: (KeyEvent) -> Boolean = { e ->
        if (e.type == KeyEventType.KeyDown) {
            lastInteraction = System.currentTimeMillis()
            when (e.key) {
                Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { MusicPlayerState.togglePlayPause(); true }
                Key.MediaNext -> { MusicPlayerState.skipNext(); true }
                Key.MediaPrevious -> { MusicPlayerState.skipPrevious(); true }
                Key.MediaFastForward -> {
                    MusicPlayerState.seekTo((MusicPlayerState.positionMs + SEEK_STEP_MS).coerceAtMost(MusicPlayerState.durationMs))
                    true
                }
                Key.MediaRewind -> {
                    MusicPlayerState.seekTo((MusicPlayerState.positionMs - SEEK_STEP_MS).coerceAtLeast(0L))
                    true
                }
                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (mode == TvPlayerMode.LYRICS && !controlsVisible && !popupOpen) {
                        controlsVisible = true
                        true
                    } else false
                }
                else -> false
            }
        } else false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent(onKey),
    ) {
        TvAmbientBackground(Modifier.fillMaxSize())

        if (mode == TvPlayerMode.QUEUE) {
            QueuePane(
                track = track,
                canvas = canvas,
                popupOpen = popupOpen,
                onShowNowPlaying = { mode = TvPlayerMode.NOW_PLAYING },
                onShowLyrics = { mode = TvPlayerMode.LYRICS },
                onOpenPopup = { popupOpen = true },
                onOpenArtist = {
                    track?.let {
                        onOpenArtist(it.artistId, it.artists.substringBefore(",").trim(), it.thumbnailUrl)
                    }
                },
            )
        } else {
            NowPlayingWithLyricsPane(
                track = track,
                canvas = canvas,
                isLyrics = mode == TvPlayerMode.LYRICS,
                controlsVisible = controlsVisible,
                popupOpen = popupOpen,
                onToggleLyrics = {
                    mode = if (mode == TvPlayerMode.LYRICS) TvPlayerMode.NOW_PLAYING else TvPlayerMode.LYRICS
                },
                onShowQueue = { mode = TvPlayerMode.QUEUE },
                onOpenPopup = { popupOpen = true },
                onOpenArtist = {
                    track?.let {
                        onOpenArtist(it.artistId, it.artists.substringBefore(",").trim(), it.thumbnailUrl)
                    }
                },
            )
        }

        if (popupOpen && track != null) {
            TvOptionsPopup(
                track = track,
                onDismiss = { popupOpen = false },
                onShowLyrics = { popupOpen = false; mode = TvPlayerMode.LYRICS },
                onShowQueue = { popupOpen = false; mode = TvPlayerMode.QUEUE },
                onOpenArtist = {
                    popupOpen = false
                    onOpenArtist(track.artistId, track.artists.substringBefore(",").trim(), track.thumbnailUrl)
                },
            )
        }
    }
}

// ── Canvas Motion Artwork ──────────────────────────────────────────────────────────────────────

@Composable
private fun rememberTvCanvas(track: MusicTrack?, enabled: Boolean): CanvasArtwork? {
    var canvas by remember(track?.id) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(track?.id, track?.album, enabled) {
        if (!enabled || track == null) {
            canvas = null
            return@LaunchedEffect
        }
        canvas = CanvasRepository.cached(track)
        if (canvas == null && track.album.isNullOrBlank()) delay(800)
        canvas = CanvasRepository.canvasFor(track) ?: canvas
    }
    return remember(canvas) {
        canvas?.let { art -> art.portraitUrl?.let { art.copy(url = it, fallbackUrl = art.url, portraitUrl = null) } ?: art }
    }
}

/** Rounded artwork tile; motion artwork (canvas) fades in over the still cover once it renders. */
@Composable
private fun TvArtworkTile(
    track: MusicTrack?,
    canvas: CanvasArtwork?,
    size: Dp,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    var rendered by remember(canvas?.url) { mutableStateOf(false) }
    val canvasAlpha by animateFloatAsState(if (rendered) 1f else 0f, tween(400), label = "canvasAlpha")
    Box(
        modifier = modifier
            .size(size)
            .shadow(24.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x22FFFFFF))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
    ) {
        AsyncImage(
            model = track?.thumbnailUrl?.highQualityThumbnail(),
            contentDescription = track?.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (canvas != null) {
            CanvasArtworkPlayer(
                canvas = canvas,
                isPlaying = MusicPlayerState.isPlaying && !paused,
                contentMode = CanvasContentMode.CROP,
                onRenderedChanged = { rendered = it },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(canvasAlpha),
            )
        }
    }
}

// ── Unified Now Playing + Lyrics View (Sliding Avatar + Realtime Synced Lyrics) ─────────────────

@Composable
private fun NowPlayingWithLyricsPane(
    track: MusicTrack?,
    canvas: CanvasArtwork?,
    isLyrics: Boolean,
    controlsVisible: Boolean,
    popupOpen: Boolean,
    onToggleLyrics: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenPopup: () -> Unit,
    onOpenArtist: () -> Unit,
) {
    val queue = MusicPlayerState.queue
    val index = MusicPlayerState.queueIndex
    val isPlaying = MusicPlayerState.isPlaying

    val lyricsProgress by animateFloatAsState(
        targetValue = if (isLyrics) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 280f),
        label = "lyricsProgress",
    )

    val horizontalBias = lerp(0f, -0.70f, lyricsProgress)
    val verticalBias = lerp(-0.06f, 0f, lyricsProgress)
    val avatarWidth by animateDpAsState(
        targetValue = if (isLyrics) 230.dp else 420.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 280f),
        label = "avatarWidth",
    )

    val centerFocus = remember { FocusRequester() }
    val topControlsFocus = remember { FocusRequester() }
    val quickActionsFocus = remember { FocusRequester() }
    val progressBarFocus = remember { FocusRequester() }
    val lyricsFocus = remember { FocusRequester() }

    LaunchedEffect(isLyrics, popupOpen) {
        if (!popupOpen && !isLyrics) {
            runCatching { centerFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        // ── Top Left: iTune Brand (ALWAYS visible in both Now Playing and Lyrics!) ─────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_itune_waves),
                contentDescription = "iTune",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "iTune",
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )
        }

        // ── Top Center: Album Title (Only in Now Playing) ─────────────────
        val albumName = track?.album?.takeIf { it.isNotBlank() }
        if (!albumName.isNullOrBlank() && lyricsProgress < 0.99f) {
            Text(
                text = albumName,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(420.dp)
                    .graphicsLayer { alpha = 1f - lyricsProgress },
            )
        }

        // ── Top Right: Playback Options (Shuffle, Repeat, Endless) ────────
        if (lyricsProgress < 0.99f) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer { alpha = 1f - lyricsProgress },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shuffleActive = MusicPlayerState.shuffleEnabled
                TvDrawableButton(
                    drawableRes = if (shuffleActive) R.drawable.shuffle_on else R.drawable.shuffle,
                    contentDescription = "Shuffle",
                    onClick = { MusicPlayerState.toggleShuffle() },
                    active = shuffleActive,
                    size = 32.dp,
                    iconSize = 16.dp,
                    focusRequester = topControlsFocus,
                    modifier = Modifier.focusProperties { down = centerFocus },
                )

                val repeatMode = MusicPlayerState.repeatMode
                val repeatRes = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                    Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                    else -> R.drawable.repeat
                }
                TvDrawableButton(
                    drawableRes = repeatRes,
                    contentDescription = "Repeat",
                    onClick = { MusicPlayerState.toggleRepeat() },
                    active = repeatMode != Player.REPEAT_MODE_OFF,
                    size = 32.dp,
                    iconSize = 16.dp,
                    modifier = Modifier.focusProperties { down = centerFocus },
                )

                val endlessActive = MusicPlayerState.endlessQueueEnabled
                TvDrawableButton(
                    drawableRes = R.drawable.ic_infinite_queue,
                    contentDescription = "Endless queue",
                    onClick = { MusicPlayerState.toggleEndlessQueue() },
                    active = endlessActive,
                    size = 32.dp,
                    iconSize = 16.dp,
                    modifier = Modifier.focusProperties { down = centerFocus },
                )
            }
        }

        // ── Shared Center / Left Avatar + Name Block (Slides smoothly!) ───
        Box(
            modifier = Modifier
                .align(BiasAlignment(horizontalBias, verticalBias))
                .width(avatarWidth),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .tvFocusable(
                            shape = RoundedCornerShape(16.dp),
                            scaleOnFocus = 1.04f,
                            focusRequester = centerFocus,
                            interactionSource = interaction,
                            onClick = { MusicPlayerState.togglePlayPause() },
                        )
                        .focusProperties {
                            up = topControlsFocus
                            down = quickActionsFocus
                        },
                ) {
                    TvArtworkTile(track, canvas, 205.dp)
                    if (!isPlaying) {
                        Box(
                            modifier = Modifier
                                .size(205.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x55000000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.play),
                                contentDescription = "Play",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(46.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Metadata: Waves Icon + Track Title + Explicit Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isPlaying) {
                        AnimatedEqualizer(
                            isPlaying = isPlaying,
                            color = Color.White,
                            size = 18.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = track?.title ?: "Not Playing",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (track?.isExplicit == true) {
                        Spacer(Modifier.width(6.dp))
                        ExplicitBadge(color = Color.White.copy(alpha = 0.85f))
                    }
                }

                Spacer(Modifier.height(3.dp))

                Text(
                    text = track?.artists ?: "",
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 13.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Quick Center Actions (Favorite + More): Only in Now Playing
                if (lyricsProgress < 0.99f) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.graphicsLayer { alpha = 1f - lyricsProgress },
                    ) {
                        val fav = track?.let { MusicPlayerState.isFavorite(it.id) } == true
                        TvDrawableButton(
                            drawableRes = if (fav) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                            contentDescription = "Favorite",
                            onClick = { track?.let { MusicPlayerState.toggleFavorite(it) } },
                            active = fav,
                            size = 32.dp,
                            iconSize = 16.dp,
                            focusRequester = quickActionsFocus,
                            modifier = Modifier.focusProperties {
                                up = centerFocus
                                down = progressBarFocus
                            },
                        )

                        TvDrawableButton(
                            drawableRes = R.drawable.more_horiz,
                            contentDescription = "More",
                            onClick = onOpenPopup,
                            size = 32.dp,
                            iconSize = 16.dp,
                            modifier = Modifier.focusProperties {
                                up = centerFocus
                                down = progressBarFocus
                            },
                        )
                    }
                }
            }
        }

        // ── Right Side: Apple Music TV Lyrics Typography (with Blur Falloff) ──
        if (lyricsProgress > 0.01f) {
            val lines = MusicPlayerState.lyrics
            val position = MusicPlayerState.positionMs
            val activeIndex = remember(position, lines) {
                val pos = position + MusicPlayerState.lyricsOffsetMs
                lines.indexOfLast { it.timeMs <= pos }
            }
            val listState = rememberLazyListState()

            LaunchedEffect(activeIndex, lines.size) {
                if (activeIndex >= 0) {
                    val offset = -(listState.layoutInfo.viewportSize.height * 0.36f).toInt()
                    listState.animateScrollToItem(activeIndex, offset)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.60f)
                    .padding(end = 36.dp, top = 20.dp, bottom = 20.dp)
                    .graphicsLayer {
                        alpha = lyricsProgress
                        translationX = (1f - lyricsProgress) * 90.dp.toPx()
                    },
            ) {
                if (lines.isEmpty()) {
                    Text(
                        text = if (MusicPlayerState.lyricsLoading) "Loading lyrics…" else "Lyrics not available for this song",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 80.dp, bottom = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.12f to Color.Black,
                                        0.80f to Color.Black,
                                        1f to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        itemsIndexed(lines) { i, line ->
                            TvLyricLine(
                                line = line,
                                state = i - activeIndex,
                                positionMs = position + MusicPlayerState.lyricsOffsetMs,
                                onClick = { MusicPlayerState.seekTo(line.timeMs) },
                            )
                        }
                    }
                }
            }
        }

        // ── Auto-Hiding Bottom Controls & Scrubber Bar (Clean, no glass capsule) ──
        val showBottomControls = if (isLyrics) controlsVisible else true
        AnimatedVisibility(
            visible = showBottomControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(350)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TvProgressBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(progressBarFocus)
                        .focusProperties {
                            up = if (isLyrics) lyricsFocus else quickActionsFocus
                            down = lyricsFocus
                        },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (queue.isNotEmpty()) "${index + 1} of ${queue.size}" else "",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Medium,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TvDrawableButton(
                            drawableRes = R.drawable.ic_menu_artist,
                            contentDescription = "Artist Info",
                            onClick = onOpenArtist,
                            size = 34.dp,
                            iconSize = 17.dp,
                            modifier = Modifier.focusProperties { up = progressBarFocus },
                        )

                        TvDrawableButton(
                            drawableRes = R.drawable.lyrics,
                            contentDescription = "Lyrics",
                            onClick = onToggleLyrics,
                            active = isLyrics,
                            size = 34.dp,
                            iconSize = 17.dp,
                            focusRequester = lyricsFocus,
                            modifier = Modifier.focusProperties { up = progressBarFocus },
                        )

                        TvDrawableButton(
                            drawableRes = R.drawable.queue_music,
                            contentDescription = "Queue",
                            onClick = onShowQueue,
                            active = false,
                            size = 34.dp,
                            iconSize = 17.dp,
                            modifier = Modifier.focusProperties { up = progressBarFocus },
                        )
                    }
                }
            }
        }
    }
}



// ── Queue Carousel View (Toggled via Queue Button - Exact Apple TV Design) ─────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueuePane(
    track: MusicTrack?,
    canvas: CanvasArtwork?,
    popupOpen: Boolean,
    onShowNowPlaying: () -> Unit,
    onShowLyrics: () -> Unit,
    onOpenPopup: () -> Unit,
    onOpenArtist: () -> Unit,
) {
    val queue = MusicPlayerState.queue
    val currentTrack = MusicPlayerState.currentTrack
    val isPlaying = MusicPlayerState.isPlaying
    val currentQueueIndex = MusicPlayerState.queueIndex

    val displayQueue = remember(queue, track) {
        if (queue.isNotEmpty()) queue else listOfNotNull(track)
    }

    val lazyListState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)

    // Center the currently playing track on initial open
    LaunchedEffect(currentQueueIndex) {
        if (displayQueue.isNotEmpty() && currentQueueIndex in displayQueue.indices) {
            lazyListState.animateScrollToItem(currentQueueIndex)
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val cardSize = 240.dp
    val cardSpacing = 26.dp
    val horizontalSidePadding = remember(screenWidth, cardSize) {
        ((screenWidth - cardSize) / 2).coerceAtLeast(40.dp)
    }

    val topControlsFocus = remember { FocusRequester() }
    val progressBarFocus = remember { FocusRequester() }
    val currentCardFocus = remember { FocusRequester() }

    LaunchedEffect(popupOpen) {
        if (!popupOpen) {
            runCatching { currentCardFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 20.dp),
    ) {
        // ── Top Left: iTune Brand ──────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_itune_waves),
                contentDescription = "iTune",
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "iTune",
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
            )
        }

        // ── Top Right: Playback Options (Shuffle, Repeat, Endless Queue, Lyrics) ──
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shuffleActive = MusicPlayerState.shuffleEnabled
            TvDrawableButton(
                drawableRes = if (shuffleActive) R.drawable.shuffle_on else R.drawable.shuffle,
                contentDescription = "Shuffle",
                onClick = { MusicPlayerState.toggleShuffle() },
                active = shuffleActive,
                size = 34.dp,
                iconSize = 17.dp,
                focusRequester = topControlsFocus,
                modifier = Modifier.focusProperties { down = currentCardFocus },
            )

            val repeatMode = MusicPlayerState.repeatMode
            val repeatRes = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                else -> R.drawable.repeat
            }
            TvDrawableButton(
                drawableRes = repeatRes,
                contentDescription = "Repeat",
                onClick = { MusicPlayerState.toggleRepeat() },
                active = repeatMode != Player.REPEAT_MODE_OFF,
                size = 34.dp,
                iconSize = 17.dp,
                modifier = Modifier.focusProperties { down = currentCardFocus },
            )

            val endlessActive = MusicPlayerState.endlessQueueEnabled
            TvDrawableButton(
                drawableRes = R.drawable.ic_infinite_queue,
                contentDescription = "Endless queue",
                onClick = { MusicPlayerState.toggleEndlessQueue() },
                active = endlessActive,
                size = 34.dp,
                iconSize = 17.dp,
                modifier = Modifier.focusProperties { down = currentCardFocus },
            )

            TvDrawableButton(
                drawableRes = R.drawable.lyrics,
                contentDescription = "Lyrics",
                onClick = onShowLyrics,
                active = false,
                size = 34.dp,
                iconSize = 17.dp,
                modifier = Modifier.focusProperties { down = currentCardFocus },
            )
        }

        // ── Center Content: Same-Sized Queue Cards Carousel with Left-Right Swipe & Remote Navigation ──
        if (displayQueue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Queue is empty",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 16.sp,
                    fontFamily = SFProDisplay,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                LazyRow(
                    state = lazyListState,
                    flingBehavior = flingBehavior,
                    contentPadding = PaddingValues(horizontal = horizontalSidePadding),
                    horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(displayQueue, key = { i, t -> "${t.id}_$i" }) { itemIndex, qTrack ->
                        val isCurrentPlaying = qTrack.id == currentTrack?.id
                        val interaction = remember { MutableInteractionSource() }
                        val isCardFocused by interaction.collectIsFocusedAsState()

                        // Auto-scroll to center this card when focused via remote
                        LaunchedEffect(isCardFocused) {
                            if (isCardFocused) {
                                lazyListState.animateScrollToItem(itemIndex)
                            }
                        }

                        val cardBoxFocusRequester = remember { FocusRequester() }
                        val addBtnFocusRequester = remember { FocusRequester() }
                        val moreBtnFocusRequester = remember { FocusRequester() }
                        val addBtnInteraction = remember { MutableInteractionSource() }
                        val isAddBtnFocused by addBtnInteraction.collectIsFocusedAsState()
                        val moreBtnInteraction = remember { MutableInteractionSource() }
                        val isMoreBtnFocused by moreBtnInteraction.collectIsFocusedAsState()

                        val isCurrentCard = itemIndex == currentQueueIndex.coerceIn(0, displayQueue.lastIndex)
                        val effectiveCardFocusRequester = if (isCurrentCard) currentCardFocus else cardBoxFocusRequester
                        val showControls = isCurrentCard || isCurrentPlaying || isCardFocused || isAddBtnFocused || isMoreBtnFocused

                        Column(
                            modifier = Modifier.width(cardSize),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Cover Artwork - SAME SIZED across every card (240.dp x 240.dp)
                            Box(
                                modifier = Modifier
                                    .size(cardSize)
                                    .focusRequester(effectiveCardFocusRequester)
                                    .focusProperties {
                                        up = topControlsFocus
                                        down = addBtnFocusRequester
                                    }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                            addBtnFocusRequester.requestFocus()
                                            true
                                        } else false
                                    }
                                    .tvFocusable(
                                        shape = RoundedCornerShape(18.dp),
                                        scaleOnFocus = 1.05f,
                                        elevationOnFocus = 8.dp,
                                        interactionSource = interaction,
                                        onClick = {
                                            if (isCurrentPlaying) {
                                                MusicPlayerState.togglePlayPause()
                                            } else {
                                                MusicPlayerState.play(qTrack, expandPlayer = false)
                                            }
                                        },
                                    )
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0x22FFFFFF)),
                            ) {
                                TvArtworkTile(
                                    track = qTrack,
                                    canvas = if (isCurrentPlaying) canvas else null,
                                    size = cardSize,
                                    paused = !isPlaying,
                                )

                                // Play / Pause overlay indicator
                                if (isCurrentPlaying && !isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x55000000)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.play),
                                            contentDescription = "Play",
                                            colorFilter = ColorFilter.tint(Color.White),
                                            modifier = Modifier.size(48.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Song Title with Live Equalizer if playing
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (isCurrentPlaying && isPlaying) {
                                    AnimatedEqualizer(
                                        isPlaying = true,
                                        color = if (isCardFocused || isAddBtnFocused || isMoreBtnFocused) Color.White else AppleMusicRed,
                                        size = 14.dp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = qTrack.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (qTrack.isExplicit) {
                                    Spacer(Modifier.width(5.dp))
                                    ExplicitBadge(color = Color.White.copy(alpha = 0.85f))
                                }
                            }

                            Spacer(Modifier.height(3.dp))

                            // Artist Subtitle
                            Text(
                                text = qTrack.artists,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(8.dp))

                            // Action buttons: '+' and '•••' under the currently playing or active card (Matching Screenshot 7 & 8)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(38.dp)
                                    .graphicsLayer { alpha = if (showControls) 1f else 0f },
                            ) {
                                val isFav = MusicPlayerState.isFavorite(qTrack.id)
                                TvVectorButton(
                                    imageVector = if (isFav) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = if (isFav) "Added" else "Add",
                                    onClick = { MusicPlayerState.toggleFavorite(qTrack) },
                                    active = isFav,
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                    focusRequester = addBtnFocusRequester,
                                    interactionSource = addBtnInteraction,
                                    modifier = Modifier
                                        .focusProperties {
                                            up = effectiveCardFocusRequester
                                            right = moreBtnFocusRequester
                                            down = progressBarFocus
                                        }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionUp -> { effectiveCardFocusRequester.requestFocus(); true }
                                                    Key.DirectionRight -> { moreBtnFocusRequester.requestFocus(); true }
                                                    Key.DirectionDown -> { progressBarFocus.requestFocus(); true }
                                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                        MusicPlayerState.toggleFavorite(qTrack)
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        },
                                )

                                TvDrawableButton(
                                    drawableRes = R.drawable.more_horiz,
                                    contentDescription = "More",
                                    onClick = onOpenPopup,
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                    focusRequester = moreBtnFocusRequester,
                                    interactionSource = moreBtnInteraction,
                                    modifier = Modifier
                                        .focusProperties {
                                            up = effectiveCardFocusRequester
                                            left = addBtnFocusRequester
                                            down = progressBarFocus
                                        }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionUp -> { effectiveCardFocusRequester.requestFocus(); true }
                                                    Key.DirectionLeft -> { addBtnFocusRequester.requestFocus(); true }
                                                    Key.DirectionDown -> { progressBarFocus.requestFocus(); true }
                                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                                        onOpenPopup()
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom Scrubber / Seekbar (Full width, exact like Apple TV screenshot) ──
        TvProgressBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .focusRequester(progressBarFocus)
                .focusProperties {
                    up = currentCardFocus
                }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                        currentCardFocus.requestFocus()
                        true
                    } else false
                },
        )
    }
}

private val TV_LINE_FALLOFF_BLUR = arrayOf(0.dp, 1.4.dp, 2.5.dp, 3.8.dp, 5.0.dp)
private val TV_LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.65f, 0.44f, 0.30f, 0.20f)
private val TV_LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)

/** state: negative = already sung, 0 = active, positive = upcoming. */
@Composable
private fun TvLyricLine(
    line: LyricsLine,
    state: Int,
    positionMs: Long,
    onClick: () -> Unit,
) {
    val dist = abs(state)
    val blurStep = dist.coerceAtMost(TV_LINE_FALLOFF_BLUR.lastIndex)
    val alphaStep = dist.coerceAtMost(TV_LINE_FALLOFF_ALPHA.lastIndex)

    val targetAlpha = if (state == 0) 1f else TV_LINE_FALLOFF_ALPHA[alphaStep]
    val targetBlur = if (state == 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        0.dp
    } else {
        TV_LINE_FALLOFF_BLUR[blurStep]
    }
    val targetScale = if (state == 0) 1.05f else 0.98f

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 350, easing = TV_LYRIC_EASING),
        label = "tvLyricAlpha",
    )
    val animatedBlur by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = tween(durationMillis = 350, easing = TV_LYRIC_EASING),
        label = "tvLyricBlur",
    )
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "tvLyricScale",
    )

    val text: AnnotatedString = remember(line, state == 0, if (state == 0) positionMs / 90 else 0L) {
        if (state == 0 && line.words.isNotEmpty() &&
            line.words.joinToString("") { it.text }.replace(" ", "") == line.text.replace(" ", "")
        ) {
            buildAnnotatedString {
                line.words.forEachIndexed { i, w ->
                    withStyle(SpanStyle(color = if (positionMs >= w.startMs) Color.White else Color.White.copy(alpha = 0.55f))) {
                        append(w.text)
                        if (!w.text.endsWith(" ") && i < line.words.lastIndex) append(" ")
                    }
                }
            }
        } else AnnotatedString(line.text)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && animatedBlur > 0.05.dp) {
                    Modifier.blur(animatedBlur, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (state == 0) 28.sp else 22.sp,
            lineHeight = if (state == 0) 36.sp else 29.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (state == 0) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── TV Options Popup (Multi-Page Apple TV Modal) ───────────────────────────────────────────────

private enum class TvPopupPage {
    MAIN,
    LYRICS_OPTIONS,
    LYRICS_OFFSET,
    LYRICS_PROVIDERS,
    ISHARE_LAN,
    ADD_TO_PLAYLIST,
}

@Composable
private fun TvOptionsPopup(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenArtist: () -> Unit,
) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(TvPopupPage.MAIN) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(currentPage) { runCatching { firstFocus.requestFocus() } }

    val fav = MusicPlayerState.isFavorite(track.id)
    val settings by SettingsStore.state.collectAsStateWithLifecycle()
    val playlists by LibraryStore.userPlaylists.collectAsStateWithLifecycle()
    val ishareSession by IShareSyncManager.session.collectAsStateWithLifecycle()
    val ishareIsHost by IShareSyncManager.isHost.collectAsStateWithLifecycle()
    val discoveredHosts by IShareSyncManager.discoveredHosts.collectAsStateWithLifecycle()

    val sleepLabel = when {
        MusicPlayerState.sleepTimerEndOfTrack -> "End of track"
        MusicPlayerState.sleepTimerRemainingMs > 0 -> "${ceil(MusicPlayerState.sleepTimerRemainingMs / 60_000.0).toInt()} min left"
        else -> "Off"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF01C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(vertical = 18.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (currentPage) {
                TvPopupPage.MAIN -> {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Text(
                        text = track.artists,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        fontFamily = SFProDisplay,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Spacer(Modifier.height(8.dp))

                    PopupDrawableRow(
                        drawableRes = if (fav) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                        label = if (fav) "Remove from Favorites" else "Add to Favorites",
                        trailing = null,
                        focusRequester = firstFocus,
                    ) { MusicPlayerState.toggleFavorite(track) }

                    PopupDrawableRow(
                        drawableRes = R.drawable.lyrics,
                        label = "Lyrics View",
                        trailing = null,
                        focusRequester = null,
                        onClick = onShowLyrics,
                    )

                    PopupDrawableRow(
                        drawableRes = R.drawable.queue_music,
                        label = "Playing Next (Queue)",
                        trailing = "${MusicPlayerState.queue.size}",
                        focusRequester = null,
                        onClick = onShowQueue,
                    )

                    PopupDrawableRow(
                        drawableRes = R.drawable.ic_menu_artist,
                        label = "Go to Artist",
                        trailing = null,
                        focusRequester = null,
                        onClick = onOpenArtist,
                    )

                    PopupVectorRow(
                        icon = Icons.Rounded.Tune,
                        label = "Lyrics Settings",
                        trailing = "${MusicPlayerState.lyricsOffsetMs}ms",
                        focusRequester = null,
                        onClick = { currentPage = TvPopupPage.LYRICS_OPTIONS },
                    )

                    PopupVectorRow(
                        icon = Icons.Rounded.Add,
                        label = "Add to Playlist",
                        trailing = "${playlists.size}",
                        focusRequester = null,
                        onClick = { currentPage = TvPopupPage.ADD_TO_PLAYLIST },
                    )

                    PopupVectorRow(
                        icon = Icons.Rounded.Wifi,
                        label = "iShare (Listen Together)",
                        trailing = if (ishareSession != null) "Connected" else "Off",
                        focusRequester = null,
                        onClick = { currentPage = TvPopupPage.ISHARE_LAN },
                    )

                    PopupVectorRow(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Copy Song Link",
                        trailing = null,
                        focusRequester = null,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("iTune Link", "https://music.youtube.com/watch?v=${track.id}")
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Song link copied to clipboard", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    )

                    PopupDrawableRow(
                        drawableRes = R.drawable.ic_menu_sleep_timer,
                        label = "Sleep Timer",
                        trailing = sleepLabel,
                        focusRequester = null,
                    ) { cycleSleepTimer() }
                }

                TvPopupPage.LYRICS_OPTIONS -> {
                    PopupBackRow(title = "Lyrics Settings") { currentPage = TvPopupPage.MAIN }
                    Spacer(Modifier.height(6.dp))

                    PopupVectorRow(
                        icon = Icons.Rounded.Speed,
                        label = "Adjust Lyrics Offset",
                        trailing = "${MusicPlayerState.lyricsOffsetMs}ms",
                        focusRequester = firstFocus,
                        onClick = { currentPage = TvPopupPage.LYRICS_OFFSET },
                    )

                    PopupVectorRow(
                        icon = Icons.Rounded.Tune,
                        label = "Select Lyrics Provider",
                        trailing = "${settings.lyricsProviderOrder.size - settings.disabledLyricsProviders.size} Active",
                        focusRequester = null,
                        onClick = { currentPage = TvPopupPage.LYRICS_PROVIDERS },
                    )

                    PopupVectorRow(
                        icon = Icons.Rounded.Language,
                        label = "AI Lyrics Translation",
                        trailing = if (settings.lyricsTranslationEnabled) "On" else "Off",
                        focusRequester = null,
                        onClick = {
                            SettingsStore.setLyricsTranslationEnabled(context, !settings.lyricsTranslationEnabled)
                        },
                    )
                }

                TvPopupPage.LYRICS_OFFSET -> {
                    PopupBackRow(title = "Lyrics Timing Offset") { currentPage = TvPopupPage.LYRICS_OPTIONS }
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Current Offset: ${MusicPlayerState.lyricsOffsetMs} ms",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )

                    val steps = listOf(
                        "-500 ms" to -500L,
                        "-100 ms" to -100L,
                        "-50 ms" to -50L,
                        "Reset (0 ms)" to 0L,
                        "+50 ms" to 50L,
                        "+100 ms" to 100L,
                        "+500 ms" to 500L,
                    )

                    steps.forEachIndexed { idx, (label, delta) ->
                        PopupOffsetRow(
                            label = label,
                            isReset = delta == 0L,
                            focusRequester = if (idx == 3) firstFocus else null,
                            onClick = {
                                val target = if (delta == 0L) 0L else (MusicPlayerState.lyricsOffsetMs + delta)
                                MusicPlayerState.setLyricsOffset(target)
                            },
                        )
                    }
                }

                TvPopupPage.LYRICS_PROVIDERS -> {
                    PopupBackRow(title = "Lyrics Providers") { currentPage = TvPopupPage.LYRICS_OPTIONS }
                    Spacer(Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(LyricsHelper.PROVIDER_METADATA) { provider ->
                            val isEnabled = !settings.disabledLyricsProviders.contains(provider.id)
                            PopupProviderRow(
                                name = provider.displayName,
                                description = provider.description,
                                isEnabled = isEnabled,
                                onClick = {
                                    SettingsStore.setLyricsProviderEnabled(context, provider.id, !isEnabled)
                                },
                            )
                        }
                    }
                }

                TvPopupPage.ISHARE_LAN -> {
                    PopupBackRow(title = "iShare (Listen Together)") { currentPage = TvPopupPage.MAIN }
                    Spacer(Modifier.height(6.dp))

                    val session = ishareSession
                    if (session != null) {
                        Text(
                            text = "Connected Session: ${session.code}",
                            color = Color(0xFF30D158),
                            fontSize = 15.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        Text(
                            text = "Role: ${if (ishareIsHost) "Host" else "Guest"} · ${session.participants} device(s)",
                            color = Color.White.copy(alpha = 0.60f),
                            fontSize = 13.sp,
                            fontFamily = SFProDisplay,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        Spacer(Modifier.height(8.dp))

                        PopupVectorRow(
                            icon = Icons.Rounded.Wifi,
                            label = "Leave Session",
                            trailing = null,
                            focusRequester = firstFocus,
                            onClick = {
                                IShareSyncManager.leaveSession()
                                Toast.makeText(context, "Left iShare Session", Toast.LENGTH_SHORT).show()
                            },
                        )
                    } else {
                        PopupVectorRow(
                            icon = Icons.Rounded.Wifi,
                            label = "Host Local Wi-Fi Session",
                            trailing = "LAN",
                            focusRequester = firstFocus,
                            onClick = {
                                IShareSyncManager.start(context)
                                IShareSyncManager.startLanHost()
                                Toast.makeText(context, "iShare LAN Host Started!", Toast.LENGTH_SHORT).show()
                            },
                        )

                        PopupVectorRow(
                            icon = Icons.Rounded.Speed,
                            label = "Scan Nearby Hosts",
                            trailing = "${discoveredHosts.size} found",
                            focusRequester = null,
                            onClick = {
                                IShareSyncManager.start(context)
                                IShareSyncManager.scanLanHosts()
                                Toast.makeText(context, "Scanning LAN for sessions…", Toast.LENGTH_SHORT).show()
                            },
                        )

                        discoveredHosts.forEach { host ->
                            PopupVectorRow(
                                icon = Icons.Rounded.Wifi,
                                label = "Join ${host.hostName}",
                                trailing = host.address,
                                focusRequester = null,
                                onClick = {
                                    IShareSyncManager.start(context)
                                    IShareSyncManager.joinLanSession(host.address)
                                    Toast.makeText(context, "Joining ${host.hostName}…", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }

                TvPopupPage.ADD_TO_PLAYLIST -> {
                    PopupBackRow(title = "Add to Playlist") { currentPage = TvPopupPage.MAIN }
                    Spacer(Modifier.height(6.dp))

                    if (playlists.isEmpty()) {
                        Text(
                            text = "No user playlists found. Create one in Library!",
                            color = Color.White.copy(alpha = 0.50f),
                            fontSize = 14.sp,
                            fontFamily = SFProDisplay,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(playlists) { p ->
                                PopupVectorRow(
                                    icon = Icons.Rounded.Add,
                                    label = p.name,
                                    trailing = "${p.songs.size} songs",
                                    focusRequester = null,
                                    onClick = {
                                        val metadata = SavedSongMetadata(
                                            id = track.id,
                                            title = track.title,
                                            artists = track.artists,
                                            album = track.album,
                                            thumbnailUrl = track.thumbnailUrl ?: "",
                                            durationSec = track.durationSec,
                                            isExplicit = track.isExplicit,
                                            artistId = track.artistId,
                                        )
                                        LibraryStore.addSongsToUserPlaylist(context, p.id, listOf(metadata))
                                        Toast.makeText(context, "Added to ${p.name}", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupBackRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PopupOffsetRow(
    label: String,
    isReset: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.02f,
                elevationOnFocus = 0.dp,
                focusRequester = focusRequester,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (focused) Color.Black else Color.White
        Text(
            text = label,
            color = if (isReset && !focused) Color(0xFF007AFF) else c,
            fontSize = 14.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isReset || focused) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun PopupProviderRow(
    name: String,
    description: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.02f,
                elevationOnFocus = 0.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (focused) Color.Black else Color.White
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = c,
                fontSize = 14.5.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                color = c.copy(alpha = 0.60f),
                fontSize = 11.5.sp,
                fontFamily = SFProDisplay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isEnabled) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Enabled",
                tint = if (focused) Color.Black else Color(0xFF30D158),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PopupVectorRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    trailing: String?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(14.dp),
                focusedBorderWidth = 0.dp,
                scaleOnFocus = 1.02f,
                focusRequester = focusRequester,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (focused) Color.Black else Color.White
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = c,
            fontSize = 15.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = c.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
            )
        }
    }
}

private fun cycleSleepTimer() {
    val s = MusicPlayerState
    when {
        s.sleepTimerEndOfTrack -> s.cancelSleepTimer()
        s.sleepTimerRemainingMs <= 0 -> s.setSleepTimer(15)
        s.sleepTimerRemainingMs <= 15 * 60_000L -> s.setSleepTimer(30)
        s.sleepTimerRemainingMs <= 30 * 60_000L -> s.setSleepTimer(60)
        else -> s.setSleepTimerEndOfTrack()
    }
}

@Composable
private fun PopupDrawableRow(
    @DrawableRes drawableRes: Int,
    label: String,
    trailing: String?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(14.dp),
                focusedBorderWidth = 0.dp,
                scaleOnFocus = 1.02f,
                focusRequester = focusRequester,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (focused) Color.Black else Color.White
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(c),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = c,
            fontSize = 15.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = c.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
            )
        }
    }
}

// ── Shared Controls & Scrubber ─────────────────────────────────────────────────────────────────

@Composable
private fun TvDrawableButton(
    @DrawableRes drawableRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 34.dp,
    iconSize: Dp = 17.dp,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val defaultInteraction = remember { MutableInteractionSource() }
    val interaction = interactionSource ?: defaultInteraction
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(size)
            .tvFocusable(
                shape = CircleShape,
                focusedBorderWidth = 0.dp,
                scaleOnFocus = 1.15f,
                focusRequester = focusRequester,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color.White
                    active -> Color.White.copy(alpha = 0.40f)
                    else -> Color(0x22FFFFFF)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = drawableRes),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(if (isFocused) Color.Black else if (active) Color.White else Color.White),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun TvVectorButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 34.dp,
    iconSize: Dp = 17.dp,
    focusRequester: FocusRequester? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val defaultInteraction = remember { MutableInteractionSource() }
    val interaction = interactionSource ?: defaultInteraction
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(size)
            .tvFocusable(
                shape = CircleShape,
                focusedBorderWidth = 0.dp,
                scaleOnFocus = 1.15f,
                focusRequester = focusRequester,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color.White
                    active -> Color.White.copy(alpha = 0.40f)
                    else -> Color(0x22FFFFFF)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun TvProgressBar(modifier: Modifier = Modifier) {
    val position = MusicPlayerState.positionMs
    val duration = MusicPlayerState.durationMs.coerceAtLeast(1L)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val barHeight by animateFloatAsState(if (focused) 8f else 4.5f, tween(150), label = "barH")
    val progress = (position.toFloat() / duration).coerceIn(0f, 1f)

    Column(
        modifier
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && focused) {
                    when (e.key) {
                        Key.DirectionLeft -> { MusicPlayerState.seekTo((position - SEEK_STEP_MS).coerceAtLeast(0L)); true }
                        Key.DirectionRight -> { MusicPlayerState.seekTo((position + SEEK_STEP_MS).coerceAtMost(duration)); true }
                        else -> false
                    }
                } else false
            }
            .tvFocusable(
                shape = RoundedCornerShape(8.dp),
                focusedBorderWidth = 0.dp,
                scaleOnFocus = 1.0f,
                elevationOnFocus = 0.dp,
                interactionSource = interaction,
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Color.White),
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(position),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.5.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "-" + formatTime((duration - position).coerceAtLeast(0L)),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.5.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
