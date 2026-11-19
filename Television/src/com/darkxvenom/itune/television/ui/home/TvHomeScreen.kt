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

package com.darkxvenom.itune.television.ui.home

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.library.HomeDiscoveryRepository
import com.darkxvenom.itune.data.library.HomeFeedRepository
import com.darkxvenom.itune.data.library.NewReleasesRepository
import com.darkxvenom.itune.data.library.PersonalizedHomeRepository
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.model.HomeFeedItem
import com.darkxvenom.itune.data.model.HomeFeedShelf
import com.darkxvenom.itune.data.model.HomeHeroCard
import com.darkxvenom.itune.data.model.HomeItemKind
import com.darkxvenom.itune.data.repository.MusicRepository
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvCard
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import kotlinx.coroutines.launch

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
fun TvHomeScreen(
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val ytAccountState by GoogleAuthStore.state.collectAsStateWithLifecycle()

    val remoteShelves by HomeFeedRepository.remoteShelves.collectAsStateWithLifecycle()
    val personalShelves by HomeFeedRepository.personalShelves.collectAsStateWithLifecycle()
    val newReleases by NewReleasesRepository.releases.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        HomeDiscoveryRepository.ensureLoaded()
        NewReleasesRepository.ensureLoaded()
        HomeFeedRepository.ensureLoaded()
        if (ytAccountState.isSignedIn) {
            PersonalizedHomeRepository.refreshIfSignedIn()
        }
    }

    val hideExplicit = settingsState.hideExplicitContent

    val heroCards = remember(remoteShelves, personalShelves, newReleases, ytAccountState.isSignedIn) {
        val live = HomeFeedRepository.buildHeroCards(remoteShelves, personalShelves, newReleases, ytAccountState.isSignedIn)
        if (live.isNotEmpty()) live else {
            MusicRepository.featuredCards.map { fc ->
                HomeHeroCard(
                    id = fc.id,
                    category = fc.category,
                    title = fc.title,
                    subtitle = fc.subtitle,
                    caption = fc.caption,
                    imageUrl = fc.imageUrl,
                    heroType = fc.heroType,
                    accentColor = fc.accentColor,
                    gradientColors = fc.gradientColors,
                    item = HomeFeedItem(
                        kind = HomeItemKind.PLAYLIST,
                        title = fc.title,
                        subtitle = fc.subtitle,
                        thumbnailUrl = fc.imageUrl,
                        browseId = fc.id,
                    ),
                )
            }
        }
    }

    val listState = rememberLazyListState()

    val onItemClick: (HomeFeedItem) -> Unit = { item ->
        when (item.kind) {
            HomeItemKind.SONG, HomeItemKind.VIDEO, HomeItemKind.EPISODE -> {
                item.toMusicTrack()?.let { track ->
                    MusicPlayerState.play(track, listOf(track), expandPlayer = false)
                    onOpenPlayer()
                }
            }
            HomeItemKind.ARTIST -> {
                onArtistClick(item.browseId, item.title, item.thumbnailUrl)
            }
            HomeItemKind.PLAYLIST, HomeItemKind.ALBUM, HomeItemKind.PODCAST -> {
                val plId = item.browseId ?: item.watchPlaylistId.orEmpty()
                if (plId.isNotBlank()) {
                    coroutineScope.launch {
                        Toast.makeText(context, "Loading ${item.title}...", Toast.LENGTH_SHORT).show()
                        val tracks = HomeFeedRepository.loadWatchPlaylist(plId, item.videoId)
                        if (tracks.isNotEmpty()) {
                            MusicPlayerState.play(tracks.first(), tracks, expandPlayer = false)
                            onOpenPlayer()
                        } else {
                            Toast.makeText(context, "Unable to load tracks", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
        contentPadding = PaddingValues(top = 28.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // Hero Carousel Row
        if (heroCards.isNotEmpty()) {
            item(key = "tv_hero_carousel") {
                TvHeroCarousel(
                    cards = heroCards,
                    onCardClick = { hero ->
                        onItemClick(hero.item)
                    },
                )
            }
        }

        // Remote and Personal Shelves
        items(
            items = personalShelves + remoteShelves,
            key = { shelf -> shelf.title.ifBlank { shelf.group.name } + shelf.hashCode() },
        ) { shelf ->
            if (shelf.items.isNotEmpty()) {
                TvShelfRow(
                    shelf = shelf,
                    onItemClick = onItemClick,
                )
            }
        }
    }
}

@Composable
private fun TvHeroCarousel(
    cards: List<HomeHeroCard>,
    onCardClick: (HomeHeroCard) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Featured for You",
            color = Color.White,
            fontSize = 22.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, bottom = 14.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                TvCard(
                    onClick = { onCardClick(card) },
                    shape = RoundedCornerShape(20.dp),
                    scaleOnFocus = 1.03f,
                    modifier = Modifier
                        .width(420.dp)
                        .height(210.dp),
                ) { isFocused ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = card.imageUrl,
                            contentDescription = card.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.5f),
                                            Color.Black.copy(alpha = 0.95f),
                                        ),
                                        startY = 60f,
                                    )
                                ),
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp),
                        ) {
                            if (card.category.isNotBlank()) {
                                Text(
                                    text = card.category.uppercase(),
                                    color = AppleMusicRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = card.title,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (card.subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = card.subtitle,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 13.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Focus play indicator
                        if (isFocused) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(20.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(AppleMusicRed),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
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
private fun TvShelfRow(
    shelf: HomeFeedShelf,
    onItemClick: (HomeFeedItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = shelf.title,
            color = Color.White,
            fontSize = 20.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(shelf.items, key = { it.videoId ?: (it.browseId ?: (it.watchPlaylistId ?: it.title)) }) { item ->
                val isArtist = item.kind == HomeItemKind.ARTIST
                val cardWidth = if (isArtist) 150.dp else 170.dp

                Column(
                    modifier = Modifier.width(cardWidth),
                    horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    TvCard(
                        onClick = { onItemClick(item) },
                        shape = if (isArtist) CircleShape else RoundedCornerShape(16.dp),
                        scaleOnFocus = 1.06f,
                        modifier = Modifier.size(cardWidth),
                    ) { isFocused ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isFocused) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.30f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(AppleMusicRed),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isArtist) Modifier.padding(horizontal = 4.dp) else Modifier,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            color = Color.White.copy(alpha = 0.60f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
