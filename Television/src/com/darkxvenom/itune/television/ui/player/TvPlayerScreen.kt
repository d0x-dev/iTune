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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicPlayerState
import androidx.media3.common.Player
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.ui.components.TopSpringLoadingBar
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.television.focus.SFProDisplay

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
fun TvPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    val track = MusicPlayerState.currentTrack
    val isPlaying = MusicPlayerState.isPlaying
    val position = MusicPlayerState.positionMs
    val duration = MusicPlayerState.durationMs.coerceAtLeast(1L)
    val isBuffering = MusicPlayerState.isLoading
    val repeatMode = MusicPlayerState.repeatMode
    val isShuffle = MusicPlayerState.shuffleEnabled

    val lyricsLines = MusicPlayerState.lyrics
    val activeIndex = remember(position, lyricsLines) {
        val pos = position + MusicPlayerState.lyricsOffsetMs
        lyricsLines.indexOfLast { it.timeMs <= pos }.coerceAtLeast(0)
    }

    val lyricsListState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex in lyricsLines.indices) {
            lyricsListState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF320814),
                        Color(0xFF140308),
                        AppleOledBlack,
                    ),
                    radius = 1800f,
                )
            )
            .padding(horizontal = 56.dp, vertical = 40.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column: Artwork, Song Info & Controls
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(310.dp)
                        .shadow(
                            elevation = 32.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = AppleMusicRed.copy(alpha = 0.40f),
                            spotColor = AppleMusicRed.copy(alpha = 0.50f),
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.2.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                ) {
                    AsyncImage(
                        model = track?.thumbnailUrl,
                        contentDescription = track?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Song Title & Artist
                Text(
                    text = track?.title ?: "Not Playing",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track?.artists ?: "Select a song to begin",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 18.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Quality Badge Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x28FFFFFF))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "LOSSLESS",
                            color = Color.White.copy(alpha = 0.90f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppleMusicRed.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "DOLBY ATMOS",
                            color = AppleMusicRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Seek Progress Bar (Navigable via D-pad Left / Right)
                TvSeekBar(
                    positionMs = position,
                    durationMs = duration,
                    isBuffering = isBuffering,
                    onSeek = { newPos -> MusicPlayerState.seekTo(newPos) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // TV Transport Controls Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Shuffle Button
                    TvControlButton(
                        icon = Icons.Rounded.Shuffle,
                        isActive = isShuffle,
                        contentDescription = "Shuffle",
                        onClick = { MusicPlayerState.toggleShuffle() },
                    )

                    // Previous Button
                    TvControlButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        onClick = { MusicPlayerState.skipPrevious() },
                    )

                    // Big Play/Pause Button
                    TvPlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = { MusicPlayerState.togglePlayPause() },
                    )

                    // Next Button
                    TvControlButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        onClick = { MusicPlayerState.skipNext() },
                    )

                    // Repeat Button
                    TvControlButton(
                        icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        isActive = repeatMode != Player.REPEAT_MODE_OFF,
                        contentDescription = "Repeat",
                        onClick = { MusicPlayerState.toggleRepeat() },
                    )
                }
            }

            // Right Column: Live Synced Lyrics Pane
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                    .padding(32.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = AppleMusicRed,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Live Lyrics",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (lyricsLines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No synchronized lyrics available for this track",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 16.sp,
                            fontFamily = SFProDisplay,
                        )
                    }
                } else {
                    LazyColumn(
                        state = lyricsListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        itemsIndexed(lyricsLines) { index, line ->
                            val isActive = index == activeIndex
                            val textColor by animateColorAsState(
                                targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                                animationSpec = tween(220),
                                label = "lyricsColor",
                            )
                            val fontSize = if (isActive) 24.sp else 18.sp
                            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        MusicPlayerState.seekTo(line.timeMs)
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(
                                    text = line.text,
                                    color = textColor,
                                    fontSize = fontSize,
                                    fontFamily = SFProDisplay,
                                    fontWeight = fontWeight,
                                    lineHeight = 30.sp,
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
private fun TvSeekBar(
    positionMs: Long,
    durationMs: Long,
    isBuffering: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeek((positionMs - 10_000L).coerceAtLeast(0L))
                            true
                        }
                        Key.DirectionRight -> {
                            onSeek((positionMs + 10_000L).coerceAtMost(durationMs))
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .tvFocusable(
                shape = RoundedCornerShape(8.dp),
                focusedBorderColor = Color.White,
                focusedBorderWidth = 2.dp,
                scaleOnFocus = 1.02f,
                interactionSource = interactionSource,
            )
            .padding(vertical = 6.dp),
    ) {
        // Track Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.20f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(AppleMusicRed),
            )
            if (isBuffering) {
                TopSpringLoadingBar(
                    isVisible = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(positionMs),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatDuration(durationMs),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun TvControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(48.dp)
            .tvFocusable(
                shape = CircleShape,
                focusedBorderColor = Color.White,
                focusedBorderWidth = 2.5.dp,
                scaleOnFocus = 1.12f,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(if (isActive) AppleMusicRed.copy(alpha = 0.35f) else Color(0x22FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) AppleMusicRed else Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun TvPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(62.dp)
            .tvFocusable(
                shape = CircleShape,
                focusedBorderColor = Color.White,
                focusedBorderWidth = 3.dp,
                scaleOnFocus = 1.10f,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFA2446), Color(0xFFC70024)),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
