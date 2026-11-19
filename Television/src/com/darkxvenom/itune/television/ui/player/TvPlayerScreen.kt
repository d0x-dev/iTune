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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.LyricsLine
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.artist.CanvasArtwork
import com.darkxvenom.itune.data.canvas.CanvasRepository
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.television.ui.common.TvAmbientBackground
import com.darkxvenom.itune.ui.artist.canvas.CanvasArtworkPlayer
import com.darkxvenom.itune.ui.artist.canvas.CanvasContentMode
import kotlinx.coroutines.delay
import kotlin.math.ceil

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private enum class TvPlayerMode { NOW_PLAYING, LYRICS, QUEUE }

private const val CONTROLS_HIDE_DELAY_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L

/**
 * Apple Music TV styled player: Now Playing (artwork carousel), Lyrics (auto-hiding controls) and
 * Queue, plus a glass options popup. Canvas (motion artwork) plays inside the artwork tile.
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

    // Lyrics screen hides its controls after a few idle seconds; any interaction shows them again.
    LaunchedEffect(mode, popupOpen, lastInteraction) {
        if (mode == TvPlayerMode.LYRICS && !popupOpen) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        } else {
            controlsVisible = true
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
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                    // First press only reveals hidden lyrics controls (Apple TV behaviour)
                    if (mode == TvPlayerMode.LYRICS && !controlsVisible && !popupOpen) {
                        controlsVisible = true
                        true
                    } else false
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

        Crossfade(targetState = mode, animationSpec = tween(320), label = "tvPlayerMode") { m ->
            when (m) {
                TvPlayerMode.NOW_PLAYING -> NowPlayingPane(
                    track = track,
                    canvas = canvas,
                    popupOpen = popupOpen,
                    onShowLyrics = { mode = TvPlayerMode.LYRICS },
                    onOpenPopup = { popupOpen = true },
                )
                TvPlayerMode.LYRICS -> LyricsPane(
                    track = track,
                    canvas = canvas,
                    controlsVisible = controlsVisible,
                    popupOpen = popupOpen,
                    onShowNowPlaying = { mode = TvPlayerMode.NOW_PLAYING },
                    onShowQueue = { mode = TvPlayerMode.QUEUE },
                    onOpenPopup = { popupOpen = true },
                )
                TvPlayerMode.QUEUE -> QueuePane(
                    track = track,
                    canvas = canvas,
                    popupOpen = popupOpen,
                    onShowLyrics = { mode = TvPlayerMode.LYRICS },
                    onOpenPopup = { popupOpen = true },
                )
            }
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

// ── Canvas ─────────────────────────────────────────────────────────────────────────────────────

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
    val canvasAlpha by animateFloatAsState(if (rendered) 1f else 0f, tween(500), label = "canvasAlpha")
    Box(
        modifier = modifier
            .size(size)
            .shadow(36.dp, RoundedCornerShape(20.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x22FFFFFF))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp)),
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
                modifier = Modifier.fillMaxSize().alpha(canvasAlpha),
            )
        }
    }
}

// ── Now Playing ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun NowPlayingPane(
    track: MusicTrack?,
    canvas: CanvasArtwork?,
    popupOpen: Boolean,
    onShowLyrics: () -> Unit,
    onOpenPopup: () -> Unit,
) {
    val queue = MusicPlayerState.queue
    val index = MusicPlayerState.queueIndex
    val prev = queue.getOrNull(index - 1)
    val next = queue.getOrNull(index + 1)
    val isPlaying = MusicPlayerState.isPlaying
    val centerFocus = remember { FocusRequester() }
    LaunchedEffect(popupOpen) { if (!popupOpen) runCatching { centerFocus.requestFocus() } }

    Box(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
        Text(
            "iTune", color = Color.White, fontSize = 28.sp, fontFamily = SFProDisplay,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.TopStart),
        )
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvRoundButton(Icons.Rounded.Shuffle, "Shuffle", { MusicPlayerState.toggleShuffle() }, active = MusicPlayerState.shuffleEnabled)
            TvRoundButton(
                if (MusicPlayerState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat", { MusicPlayerState.toggleRepeat() }, active = MusicPlayerState.repeatMode != Player.REPEAT_MODE_OFF,
            )
            TvRoundButton(Icons.Rounded.AllInclusive, "Endless queue", { MusicPlayerState.toggleEndlessQueue() }, active = MusicPlayerState.endlessQueueEnabled)
            TvRoundButton(Icons.Rounded.Lyrics, "Lyrics", onShowLyrics)
        }

        Column(
            Modifier.align(Alignment.Center).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                NeighbourCover(prev, "Previous") { MusicPlayerState.skipPrevious() }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .tvFocusable(
                                shape = RoundedCornerShape(22.dp), focusedBorderWidth = 4.dp, scaleOnFocus = 1.04f,
                                focusRequester = centerFocus, interactionSource = interaction,
                                onClick = { MusicPlayerState.togglePlayPause() },
                            ),
                    ) {
                        TvArtworkTile(track, canvas, 300.dp)
                        if (!isPlaying) {
                            Box(Modifier.size(300.dp).background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(72.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(track?.title ?: "Not Playing", color = Color.White, fontSize = 22.sp, fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(380.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(track?.artists ?: "", color = Color.White.copy(alpha = 0.65f), fontSize = 18.sp, fontFamily = SFProDisplay,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(380.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val fav = track?.let { MusicPlayerState.isFavorite(it.id) } == true
                        TvRoundButton(
                            if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorite",
                            { track?.let { MusicPlayerState.toggleFavorite(it) } }, active = fav, size = 46.dp,
                        )
                        TvRoundButton(Icons.Rounded.MoreHoriz, "More", onOpenPopup, size = 46.dp)
                    }
                }
                NeighbourCover(next, "Next") { MusicPlayerState.skipNext() }
            }
        }

        TvProgressBar(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
    }
}

@Composable
private fun NeighbourCover(track: MusicTrack?, label: String, onClick: () -> Unit) {
    if (track == null) {
        Spacer(Modifier.width(230.dp))
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 35.dp)) {
        Box(
            Modifier
                .size(230.dp)
                .tvFocusable(shape = RoundedCornerShape(18.dp), focusedBorderWidth = 3.dp, scaleOnFocus = 1.05f, onClick = onClick)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x22FFFFFF)),
        ) {
            AsyncImage(
                model = track.thumbnailUrl?.highQualityThumbnail(), contentDescription = "$label: ${track.title}",
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.82f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(track.title, color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, fontFamily = SFProDisplay,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(230.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(track.artists, color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp, fontFamily = SFProDisplay,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(230.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ── Lyrics ─────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LyricsPane(
    track: MusicTrack?,
    canvas: CanvasArtwork?,
    controlsVisible: Boolean,
    popupOpen: Boolean,
    onShowNowPlaying: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenPopup: () -> Unit,
) {
    val lines = MusicPlayerState.lyrics
    val position = MusicPlayerState.positionMs
    val activeIndex = remember(position, lines) {
        val pos = position + MusicPlayerState.lyricsOffsetMs
        lines.indexOfLast { it.timeMs <= pos }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex, lines.size) {
        if (activeIndex >= 0) {
            val offset = -(listState.layoutInfo.viewportSize.height * 0.30f).toInt()
            listState.animateScrollToItem(activeIndex, offset)
        }
    }

    // Focus holder while controls are hidden; real controls take focus when shown.
    val catcher = remember { FocusRequester() }
    val firstControl = remember { FocusRequester() }
    LaunchedEffect(controlsVisible, popupOpen) {
        if (popupOpen) return@LaunchedEffect
        runCatching { if (controlsVisible) firstControl.requestFocus() else catcher.requestFocus() }
    }

    Box(Modifier.fillMaxSize().focusRequester(catcher).focusable().padding(horizontal = 56.dp, vertical = 40.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            Column(Modifier.width(340.dp).fillMaxHeight().padding(top = 40.dp)) {
                TvArtworkTile(track, canvas, 340.dp)
                Spacer(Modifier.height(18.dp))
                Text(track?.title ?: "", color = Color.White, fontSize = 22.sp, fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track?.artists ?: "", color = Color.White.copy(alpha = 0.65f), fontSize = 18.sp,
                    fontFamily = SFProDisplay, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (lines.isEmpty()) {
                    Text(
                        if (MusicPlayerState.lyricsLoading) "Loading lyrics…" else "Lyrics not available for this song",
                        color = Color.White.copy(alpha = 0.5f), fontSize = 26.sp, fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 60.dp, bottom = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent, 0.12f to Color.Black, 0.78f to Color.Black, 1f to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        itemsIndexed(lines) { i, line ->
                            TvLyricLine(line, i - activeIndex, position + MusicPlayerState.lyricsOffsetMs)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val fav = track?.let { MusicPlayerState.isFavorite(it.id) } == true
                    TvRoundButton(
                        if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, "Favorite",
                        { track?.let { MusicPlayerState.toggleFavorite(it) } }, active = fav, size = 46.dp,
                        focusRequester = firstControl,
                    )
                    TvRoundButton(Icons.Rounded.MoreHoriz, "More", onOpenPopup, size = 46.dp)
                }
                TvProgressBar(Modifier.fillMaxWidth())
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvRoundButton(Icons.Rounded.Lyrics, "Hide lyrics", onShowNowPlaying, active = true, size = 46.dp)
                    TvRoundButton(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", onShowQueue, size = 46.dp)
                }
            }
        }
    }
}

/** state: negative = already sung, 0 = active, positive = upcoming. */
@Composable
private fun TvLyricLine(line: LyricsLine, state: Int, positionMs: Long) {
    val alpha by animateFloatAsState(
        targetValue = when {
            state == 0 -> 1f
            state < 0 -> 0.45f
            else -> 0.32f
        },
        animationSpec = tween(350), label = "lyricAlpha",
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
    Text(
        text = text,
        color = Color.White,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().alpha(alpha),
    )
}

// ── Queue ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun QueuePane(
    track: MusicTrack?,
    canvas: CanvasArtwork?,
    popupOpen: Boolean,
    onShowLyrics: () -> Unit,
    onOpenPopup: () -> Unit,
) {
    val queue = MusicPlayerState.queue
    val current = MusicPlayerState.queueIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))
    val currentRow = remember { FocusRequester() }
    LaunchedEffect(popupOpen) { if (!popupOpen) runCatching { currentRow.requestFocus() } }

    Box(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            Column(Modifier.width(340.dp).fillMaxHeight().padding(top = 40.dp)) {
                TvArtworkTile(track, canvas, 340.dp)
                Spacer(Modifier.height(18.dp))
                Text(track?.title ?: "", color = Color.White, fontSize = 22.sp, fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track?.artists ?: "", color = Color.White.copy(alpha = 0.65f), fontSize = 18.sp,
                    fontFamily = SFProDisplay, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("Playing Next", color = Color.White, fontSize = 30.sp, fontFamily = SFProDisplay, fontWeight = FontWeight.Bold)
                Text("${queue.size} songs", color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp, fontFamily = SFProDisplay)
                Spacer(Modifier.height(18.dp))
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(queue.toList(), key = { i, t -> "$i-${t.id}" }) { i, t ->
                        QueueRow(
                            track = t,
                            isCurrent = i == current,
                            focusRequester = if (i == current) currentRow else null,
                            onPlay = { MusicPlayerState.play(t, queue.toList()) },
                            onRemove = if (i != current) ({ MusicPlayerState.removeFromQueue(i) }) else null,
                        )
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvRoundButton(Icons.Rounded.Lyrics, "Lyrics", onShowLyrics, size = 46.dp)
            TvRoundButton(Icons.Rounded.MoreHoriz, "More", onOpenPopup, size = 46.dp)
        }
    }
}

@Composable
private fun QueueRow(
    track: MusicTrack,
    isCurrent: Boolean,
    focusRequester: FocusRequester?,
    onPlay: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Row(
            Modifier
                .weight(1f)
                .tvFocusable(
                    shape = RoundedCornerShape(14.dp), focusedBorderWidth = 2.5.dp, scaleOnFocus = 1.02f,
                    focusRequester = focusRequester, interactionSource = interaction, onClick = onPlay,
                )
                .clip(RoundedCornerShape(14.dp))
                .background(if (focused) Color(0x30FFFFFF) else if (isCurrent) Color(0x22FFFFFF) else Color(0x0FFFFFFF))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))) {
                AsyncImage(track.thumbnailUrl, track.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (isCurrent) {
                    Box(Modifier.fillMaxSize().background(Color(0x77000000)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.GraphicEq, "Now playing", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontSize = 19.sp, fontFamily = SFProDisplay,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artists, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontFamily = SFProDisplay,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onRemove != null) TvRoundButton(Icons.Rounded.Close, "Remove from queue", onRemove, size = 40.dp)
    }
}

// ── Options popup ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvOptionsPopup(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowQueue: () -> Unit,
    onOpenArtist: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val fav = MusicPlayerState.isFavorite(track.id)
    val sleepLabel = when {
        MusicPlayerState.sleepTimerEndOfTrack -> "End of track"
        MusicPlayerState.sleepTimerRemainingMs > 0 -> "${ceil(MusicPlayerState.sleepTimerRemainingMs / 60_000.0).toInt()} min left"
        else -> "Off"
    }

    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
        ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xE61C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(30.dp))
                .padding(vertical = 18.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(track.title, color = Color.White, fontSize = 20.sp, fontFamily = SFProDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp))
            Text(track.artists, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontFamily = SFProDisplay,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp))
            Spacer(Modifier.height(10.dp))
            PopupRow(if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                if (fav) "Remove from Favorites" else "Add to Favorites", null, first) { MusicPlayerState.toggleFavorite(track) }
            PopupRow(Icons.Rounded.Lyrics, "Lyrics", null, null, onShowLyrics)
            PopupRow(Icons.AutoMirrored.Rounded.QueueMusic, "Playing Next", "${MusicPlayerState.queue.size}", null, onShowQueue)
            PopupRow(Icons.Rounded.Person, "Go to Artist", null, null, onOpenArtist)
            PopupRow(Icons.Rounded.Bedtime, "Sleep Timer", sleepLabel, null) { cycleSleepTimer() }
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
private fun PopupRow(icon: ImageVector, label: String, trailing: String?, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = RoundedCornerShape(16.dp), focusedBorderWidth = 0.dp, scaleOnFocus = 1.02f,
                focusRequester = focusRequester, interactionSource = interaction, onClick = onClick,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) Color.White else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (focused) Color.Black else Color.White
        Icon(icon, null, tint = c, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = c, fontSize = 19.sp, fontFamily = SFProDisplay, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = c.copy(alpha = 0.6f), fontSize = 16.sp, fontFamily = SFProDisplay)
    }
}

// ── Shared controls ────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvRoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 52.dp,
    focusRequester: FocusRequester? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .tvFocusable(
                shape = CircleShape, focusedBorderWidth = 3.dp, scaleOnFocus = 1.12f,
                focusRequester = focusRequester, onClick = onClick,
            )
            .clip(CircleShape)
            .background(if (active) Color.White else Color(0x33FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun TvProgressBar(modifier: Modifier = Modifier) {
    val position = MusicPlayerState.positionMs
    val duration = MusicPlayerState.durationMs.coerceAtLeast(1L)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val barHeight by animateFloatAsState(if (focused) 10f else 6f, tween(150), label = "barH")
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
            .tvFocusable(shape = RoundedCornerShape(10.dp), focusedBorderWidth = 0.dp, scaleOnFocus = 1.0f, interactionSource = interaction)
            .padding(vertical = 8.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(barHeight.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.28f))) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontFamily = SFProDisplay, fontWeight = FontWeight.Medium)
            Text("-" + formatTime((duration - position).coerceAtLeast(0L)), color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp,
                fontFamily = SFProDisplay, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
