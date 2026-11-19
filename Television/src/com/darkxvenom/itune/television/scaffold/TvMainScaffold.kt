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

package com.darkxvenom.itune.television.scaffold

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.television.ui.artist.TvArtistScreen
import com.darkxvenom.itune.television.ui.browse.TvBrowseScreen
import com.darkxvenom.itune.television.ui.home.TvHomeScreen
import com.darkxvenom.itune.television.ui.library.TvLibraryScreen
import com.darkxvenom.itune.television.ui.player.TvPlayerScreen
import com.darkxvenom.itune.television.ui.search.TvSearchScreen
import com.darkxvenom.itune.television.ui.settings.TvSettingsScreen
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.television.focus.SFProDisplay

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

enum class TvNavDestination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    SEARCH("Search", Icons.Rounded.Search),
    BROWSE("Browse", Icons.Rounded.Explore),
    LIBRARY("Library", Icons.Rounded.QueueMusic),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

sealed class TvScreenState {
    object Main : TvScreenState()
    data class Artist(val browseId: String?, val name: String, val thumb: String?) : TvScreenState()
    object Player : TvScreenState()
}

@Composable
fun TvMainScaffold(
    modifier: Modifier = Modifier,
) {
    var selectedNav by remember { mutableStateOf(TvNavDestination.HOME) }
    var screenState by remember { mutableStateOf<TvScreenState>(TvScreenState.Main) }

    val track = MusicPlayerState.currentTrack
    val isPlaying = MusicPlayerState.isPlaying

    BackHandler(enabled = screenState != TvScreenState.Main) {
        screenState = TvScreenState.Main
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
    ) {
        when (val state = screenState) {
            is TvScreenState.Player -> {
                TvPlayerScreen(
                    onBack = { screenState = TvScreenState.Main },
                )
            }
            is TvScreenState.Artist -> {
                TvArtistScreen(
                    artistName = state.name,
                    artistThumb = state.thumb,
                    onBack = { screenState = TvScreenState.Main },
                )
            }
            is TvScreenState.Main -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left TV Navigation Rail (80.dp width)
                    TvNavigationRail(
                        selectedDestination = selectedNav,
                        onDestinationSelected = { dest ->
                            selectedNav = dest
                        },
                        onOpenPlayer = { screenState = TvScreenState.Player },
                        modifier = Modifier
                            .width(88.dp)
                            .fillMaxHeight(),
                    )

                    // Right Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        AnimatedContent(
                            targetState = selectedNav,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "tvNavCrossfade",
                        ) { destination ->
                            when (destination) {
                                TvNavDestination.HOME -> {
                                    TvHomeScreen(
                                        onArtistClick = { browseId, name, thumb ->
                                            screenState = TvScreenState.Artist(browseId, name, thumb)
                                        },
                                        onOpenPlayer = {
                                            screenState = TvScreenState.Player
                                        },
                                    )
                                }
                                TvNavDestination.SEARCH -> {
                                    TvSearchScreen(
                                        onArtistClick = { browseId, name, thumb ->
                                            screenState = TvScreenState.Artist(browseId, name, thumb)
                                        },
                                        onBack = { selectedNav = TvNavDestination.HOME },
                                    )
                                }
                                TvNavDestination.BROWSE -> {
                                    TvBrowseScreen(
                                        onCategoryClick = { /* Can open category playlists */ },
                                        onBack = { selectedNav = TvNavDestination.HOME },
                                    )
                                }
                                TvNavDestination.LIBRARY -> {
                                    TvLibraryScreen(
                                        onBack = { selectedNav = TvNavDestination.HOME },
                                    )
                                }
                                TvNavDestination.SETTINGS -> {
                                    TvSettingsScreen(
                                        onBack = { selectedNav = TvNavDestination.HOME },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvNavigationRail(
    selectedDestination: TvNavDestination,
    onDestinationSelected: (TvNavDestination) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = MusicPlayerState.currentTrack

    Column(
        modifier = modifier
            .background(Color(0xFF0F0F12))
            .border(
                width = 0.8.dp,
                color = Color.White.copy(alpha = 0.08f),
            )
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top iTune Logo
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_itune_waves),
                contentDescription = "iTune TV",
                modifier = Modifier.size(36.dp),
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Items
            TvNavDestination.entries.forEach { dest ->
                val isSelected = dest == selectedDestination
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .tvFocusable(
                            shape = CircleShape,
                            focusedBorderColor = Color.White,
                            scaleOnFocus = 1.15f,
                            interactionSource = interactionSource,
                            onClick = { onDestinationSelected(dest) },
                        )
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> AppleMusicRed.copy(alpha = 0.28f)
                                isFocused -> Color(0x35FFFFFF)
                                else -> Color.Transparent
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
                        tint = if (isSelected) AppleMusicRed else Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // Bottom Now Playing Mini Artwork / Player Shortcut
        if (track != null) {
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .tvFocusable(
                        shape = RoundedCornerShape(12.dp),
                        focusedBorderColor = Color.White,
                        scaleOnFocus = 1.15f,
                        interactionSource = interactionSource,
                        onClick = onOpenPlayer,
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.5.dp,
                        color = if (isFocused) Color.White else AppleMusicRed.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = "Now Playing",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(52.dp))
        }
    }
}
