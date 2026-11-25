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


import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvAppleSilverBg
import com.darkxvenom.itune.television.ui.album.TvAlbumPlaylistScreen
import com.darkxvenom.itune.television.ui.artist.TvArtistScreen
import com.darkxvenom.itune.television.ui.browse.TvBrowseScreen
import com.darkxvenom.itune.television.ui.home.TvHomeScreen
import com.darkxvenom.itune.television.ui.library.TvLibraryScreen
import com.darkxvenom.itune.television.ui.player.TvPlayerScreen
import com.darkxvenom.itune.television.ui.radio.TvRadioScreen
import com.darkxvenom.itune.television.ui.search.TvSearchScreen
import com.darkxvenom.itune.television.ui.settings.TvSettingsScreen
import com.darkxvenom.itune.ui.theme.AppleOledBlack

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

enum class TvNavDestination(val label: String) {
    HOME("Listen Now"),
    BROWSE("Browse"),
    RADIO("Radio"),
    LIBRARY("Library"),
    SEARCH("Search"),
    SETTINGS("Settings"),
}

sealed class TvScreenState {
    object Main : TvScreenState()
    data class Artist(val browseId: String?, val name: String, val thumb: String?) : TvScreenState()
    data class Release(
        val browseId: String?,
        val playlistId: String?,
        val title: String,
        val subtitle: String?,
        val thumb: String?,
    ) : TvScreenState()
    object Player : TvScreenState()
}

@Composable
fun TvMainScaffold(
    showTopBarBrand: Boolean = true,
    onTopBarBrandBoundsChanged: ((iconBounds: Rect, titleBounds: Rect) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var selectedNav by remember { mutableStateOf(TvNavDestination.HOME) }
    var screenState by remember { mutableStateOf<TvScreenState>(TvScreenState.Main) }
    val topBarFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = screenState != TvScreenState.Main) {
        screenState = TvScreenState.Main
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvAppleSilverBg),
    ) {
        when (val state = screenState) {
            is TvScreenState.Player -> {
                TvPlayerScreen(
                    onBack = { screenState = TvScreenState.Main },
                    onOpenArtist = { id, name, thumb -> screenState = TvScreenState.Artist(id, name, thumb) },
                )
            }
            is TvScreenState.Release -> {
                TvAlbumPlaylistScreen(
                    browseId = state.browseId,
                    playlistId = state.playlistId,
                    initialTitle = state.title,
                    initialSubtitle = state.subtitle,
                    initialThumb = state.thumb,
                    onArtistClick = { id, name, thumb ->
                        screenState = TvScreenState.Artist(id, name, thumb)
                    },
                    onOpenPlayer = { screenState = TvScreenState.Player },
                    onBack = { screenState = TvScreenState.Main },
                )
            }
            is TvScreenState.Artist -> {
                TvArtistScreen(
                    artistName = state.name,
                    artistThumb = state.thumb,
                    artistBrowseId = state.browseId,
                    onNavigateArtist = { id, name, thumb ->
                        screenState = TvScreenState.Artist(id, name, thumb)
                    },
                    onReleaseClick = { browseId, playlistId, title, subtitle, thumb ->
                        screenState = TvScreenState.Release(browseId, playlistId, title, subtitle, thumb)
                    },
                    onBack = { screenState = TvScreenState.Main },
                )
            }
            is TvScreenState.Main -> {
                val topBarContent: @Composable () -> Unit = {
                    TvAppleTopBar(
                        selectedDestination = selectedNav,
                        onDestinationSelected = { dest -> selectedNav = dest },
                        showBrand = showTopBarBrand,
                        onBrandBoundsChanged = onTopBarBrandBoundsChanged,
                        onOpenPlayer = { screenState = TvScreenState.Player },
                        topBarFocusRequester = topBarFocusRequester,
                    )
                }

                if (selectedNav == TvNavDestination.HOME) {
                    TvHomeScreen(
                        onArtistClick = { browseId, name, thumb ->
                            screenState = TvScreenState.Artist(browseId, name, thumb)
                        },
                        onReleaseClick = { browseId, playlistId, title, subtitle, thumb ->
                            screenState = TvScreenState.Release(browseId, playlistId, title, subtitle, thumb)
                        },
                        onOpenPlayer = { screenState = TvScreenState.Player },
                        topBarFocusRequester = topBarFocusRequester,
                        topBar = topBarContent,
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        topBarContent()

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AnimatedContent(
                                targetState = selectedNav,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "tvScreenTransition",
                                modifier = Modifier.fillMaxSize(),
                            ) { dest ->
                                when (dest) {
                                    TvNavDestination.HOME -> {} // Handled above
                                    TvNavDestination.BROWSE -> {
                                        TvBrowseScreen(
                                            onCategoryClick = { /* mood explore */ },
                                            onReleaseClick = { browseId, playlistId, title, subtitle, thumb ->
                                                screenState = TvScreenState.Release(browseId, playlistId, title, subtitle, thumb)
                                            },
                                            onBack = { selectedNav = TvNavDestination.HOME },
                                        )
                                    }
                                    TvNavDestination.RADIO -> {
                                        TvRadioScreen(
                                            onStationClick = { station ->
                                                OnlineRadioController.playStation(station)
                                            },
                                        )
                                    }
                                    TvNavDestination.LIBRARY -> {
                                        TvLibraryScreen(
                                            onArtistClick = { id, name, thumb ->
                                                screenState = TvScreenState.Artist(id, name, thumb)
                                            },
                                            onReleaseClick = { browseId, playlistId, title, subtitle, thumb ->
                                                screenState = TvScreenState.Release(browseId, playlistId, title, subtitle, thumb)
                                            },
                                            onBack = { selectedNav = TvNavDestination.HOME },
                                        )
                                    }
                                    TvNavDestination.SEARCH -> {
                                        TvSearchScreen(
                                            onArtistClick = { browseId, name, thumb ->
                                                screenState = TvScreenState.Artist(browseId, name, thumb)
                                            },
                                            onReleaseClick = { browseId, playlistId, title, subtitle, thumb ->
                                                screenState = TvScreenState.Release(browseId, playlistId, title, subtitle, thumb)
                                            },
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
}

/**
 * Apple TV Top Navigation Bar with brand logo, floating pill tabs, and search/settings controls.
 */
@Composable
private fun TvAppleTopBar(
    selectedDestination: TvNavDestination,
    onDestinationSelected: (TvNavDestination) -> Unit,
    showBrand: Boolean,
    onBrandBoundsChanged: ((iconBounds: Rect, titleBounds: Rect) -> Unit)?,
    onOpenPlayer: () -> Unit,
    topBarFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var iconBounds by remember { mutableStateOf<Rect?>(null) }
    var titleBounds by remember { mutableStateOf<Rect?>(null) }

    fun publishBounds() {
        val ib = iconBounds
        val tb = titleBounds
        if (ib != null && tb != null) {
            onBrandBoundsChanged?.invoke(ib, tb)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top Left: iTune Brand Waves + "iTune" title (destination for Welcome outro traveling brand)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                alpha = if (showBrand) 1f else 0f
            },
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_itune_waves),
                contentDescription = stringResource(R.string.ui_itune_brand_waves),
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier
                    .size(26.dp)
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached) {
                            iconBounds = coords.boundsInRoot()
                            publishBounds()
                        }
                    },
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontFamily = SFProDisplay,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.onGloballyPositioned { coords ->
                    if (coords.isAttached) {
                        titleBounds = coords.boundsInRoot()
                        publishBounds()
                    }
                },
            )
        }

        // Center-Right: Apple TV Unified Pill Tab Bar (Listen Now, Browse, Radio, Library, Now Playing, Search, Settings)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 4.dp, vertical = 3.dp),
        ) {
            TvTopTabPill(
                label = "Listen Now",
                isSelected = selectedDestination == TvNavDestination.HOME,
                focusRequester = topBarFocusRequester,
                onClick = { onDestinationSelected(TvNavDestination.HOME) },
            )

            TvTopTabPill(
                label = "Browse",
                isSelected = selectedDestination == TvNavDestination.BROWSE,
                onClick = { onDestinationSelected(TvNavDestination.BROWSE) },
            )

            TvTopTabPill(
                label = "Radio",
                isSelected = selectedDestination == TvNavDestination.RADIO,
                onClick = { onDestinationSelected(TvNavDestination.RADIO) },
            )

            TvTopTabPill(
                label = "Library",
                isSelected = selectedDestination == TvNavDestination.LIBRARY,
                onClick = { onDestinationSelected(TvNavDestination.LIBRARY) },
            )

            TvTopTabPill(
                label = if (MusicPlayerState.isPlaying) "Now Playing ♪" else "Now Playing",
                isSelected = false,
                onClick = onOpenPlayer,
            )

            Spacer(Modifier.width(2.dp))

            // Search Icon Tab
            TvTopTabIconButton(
                icon = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.ui_search),
                isSelected = selectedDestination == TvNavDestination.SEARCH,
                onClick = { onDestinationSelected(TvNavDestination.SEARCH) },
            )

            // Settings Icon Tab
            TvTopTabIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.ui_settings),
                isSelected = selectedDestination == TvNavDestination.SETTINGS,
                onClick = { onDestinationSelected(TvNavDestination.SETTINGS) },
            )
        }
    }
}

/**
 * Apple TV Capsule Pill Tab.
 * When selected: White capsule pill, bold black text, soft shadow.
 * When focused: Subtle scale and border.
 */
@Composable
private fun TvTopTabPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.96f
            isFocused -> 1.05f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "tabPillScale",
    )

    val backgroundColor = when {
        isSelected -> Color.White
        isFocused -> Color.White.copy(alpha = 0.22f)
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> Color.Black
        isFocused -> Color.White
        else -> Color.White.copy(alpha = 0.72f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(
                if (isSelected) {
                    Modifier.shadow(elevation = 4.dp, shape = CircleShape)
                } else Modifier
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isFocused && !isSelected) 2.dp else 0.dp,
                color = if (isFocused && !isSelected) Color.White else Color.Transparent,
                shape = CircleShape,
            )
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = SFProDisplay,
            fontSize = 13.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TvTopTabIconButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.94f
            isFocused -> 1.08f
            else -> 1.0f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "tabIconScale",
    )

    val backgroundColor = when {
        isSelected -> Color.White
        isFocused -> Color.White.copy(alpha = 0.22f)
        else -> Color.Transparent
    }

    val iconTint = when {
        isSelected -> Color.Black
        isFocused -> Color.White
        else -> Color.White.copy(alpha = 0.75f)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (isSelected) {
                    Modifier.shadow(elevation = 4.dp, shape = CircleShape)
                } else Modifier
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .border(
                width = if (isFocused && !isSelected) 2.dp else 0.dp,
                color = if (isFocused && !isSelected) Color.White else Color.Transparent,
                shape = CircleShape,
            )
            .focusable(interactionSource = interactionSource)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun TvTopIconButton(
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = TvTopTabIconButton(icon, contentDescription, isSelected, onClick, modifier)
