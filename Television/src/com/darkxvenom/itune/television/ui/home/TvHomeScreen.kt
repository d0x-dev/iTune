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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.NewReleaseAlbum
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.library.HomeDiscoveryRepository
import com.darkxvenom.itune.data.library.HomeFeedRepository
import com.darkxvenom.itune.data.library.LibraryStore
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
import com.darkxvenom.itune.ui.home.orderHomeShelves
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import kotlinx.coroutines.launch

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
fun TvHomeScreen(
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
    onOpenPlayer: () -> Unit,
    topBarFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val ytAccountState by GoogleAuthStore.state.collectAsStateWithLifecycle()

    val remoteShelves by HomeFeedRepository.remoteShelves.collectAsStateWithLifecycle()
    val personalShelves by HomeFeedRepository.personalShelves.collectAsStateWithLifecycle()
    val newReleases by NewReleasesRepository.releases.collectAsStateWithLifecycle()
    val featuredPlaylists by NewReleasesRepository.featuredPlaylists.collectAsStateWithLifecycle()
    val recentSongs by LibraryStore.recentSongs.collectAsStateWithLifecycle()

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
                    )
                )
            }
        }
    }

    val homeShelves = remember(
        remoteShelves, personalShelves, newReleases, featuredPlaylists, ytAccountState.isSignedIn, hideExplicit,
    ) {
        orderHomeShelves(
            remote = remoteShelves,
            personal = personalShelves,
            releases = newReleases.filter { !hideExplicit || !it.isExplicit },
            playlists = featuredPlaylists.filter { !hideExplicit || !it.isExplicit },
            signedIn = ytAccountState.isSignedIn,
        )
    }

    val recentItems: List<HomeFeedItem> = remember(recentSongs, newReleases) {
        if (recentSongs.isNotEmpty()) {
            recentSongs.take(16).map { it.toMusicTrack().toHomeFeedItem() }
        } else {
            newReleases.take(16).map { it.toHomeFeedItem() }
        }
    }

    val onPlayItem: (HomeFeedItem, List<HomeFeedItem>) -> Unit = { item, queue ->
        when (item.kind) {
            HomeItemKind.SONG, HomeItemKind.VIDEO, HomeItemKind.EPISODE -> {
                val track = item.toMusicTrack()
                if (track != null) {
                    val q = queue.filter { it.isPlayableTrack }.mapNotNull { it.toMusicTrack() }
                    MusicPlayerState.play(track, q.ifEmpty { listOf(track) })
                    onOpenPlayer()
                }
            }
            HomeItemKind.ARTIST -> {
                onArtistClick(item.browseId, item.title, item.thumbnailUrl)
            }
            HomeItemKind.PLAYLIST, HomeItemKind.ALBUM -> {
                val watchId = item.watchPlaylistId
                if (!watchId.isNullOrBlank()) {
                    coroutineScope.launch {
                        val tracks = HomeFeedRepository.loadWatchPlaylist(watchId, item.videoId)
                        if (tracks.isNotEmpty()) {
                            MusicPlayerState.play(tracks.first(), tracks)
                            onOpenPlayer()
                        } else {
                            Toast.makeText(context, "Couldn't load ${item.title}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (!item.browseId.isNullOrBlank()) {
                    onArtistClick(item.browseId, item.title, item.thumbnailUrl)
                }
            }
            else -> {}
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
    ) {
        // Section 1: "Top Picks" with Apple Music TV Portrait Cards
        item(key = "home_top_picks_section") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Top Picks",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp),
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(heroCards, key = { it.id }) { card ->
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier
                                .width(240.dp)
                                .then(
                                    if (topBarFocusRequester != null) {
                                        Modifier.focusProperties { up = topBarFocusRequester }
                                    } else Modifier
                                ),
                        ) {
                            Text(
                                text = card.category.ifBlank { "Top Pick" },
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                            )

                            TvPortraitHeroCard(
                                card = card,
                                onClick = { onPlayItem(card.item, card.queue) },
                            )
                        }
                    }
                }
            }
        }

        // Section 2: "Recently Played" Horizontal Shelf
        if (recentItems.isNotEmpty()) {
            item(key = "home_recently_played_section") {
                Spacer(modifier = Modifier.height(28.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (recentSongs.isNotEmpty()) "Recently Played" else "New Releases",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp),
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(recentItems, key = { it.stableKey }) { item ->
                            TvSquareShelfCard(
                                item = item,
                                onClick = { onPlayItem(item, recentItems) },
                            )
                        }
                    }
                }
            }
        }

        // Subsequent Shelves from orderHomeShelves
        items(homeShelves, key = { it.key }) { shelf ->
            if (shelf.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(26.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    val title = if (shelf.strapline.isBlank()) {
                        shelf.title
                    } else {
                        "${shelf.strapline.lowercase().replaceFirstChar { it.uppercase() }} ${shelf.title}"
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp),
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(shelf.items, key = { it.stableKey }) { item ->
                            if (shelf.isArtistShelf) {
                                TvArtistCircleCard(
                                    item = item,
                                    onClick = { onArtistClick(item.browseId, item.title, item.thumbnailUrl) },
                                )
                            } else {
                                TvSquareShelfCard(
                                    item = item,
                                    onClick = { onPlayItem(item, shelf.items) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 10-Foot Television Tall Portrait Hero Card matching Apple Music on Apple TV.
 */
@Composable
private fun TvPortraitHeroCard(
    card: HomeHeroCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .width(240.dp)
            .height(340.dp),
    ) { isFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1A1A1E)),
        ) {
            // Background Artwork Layer
            if (!card.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                TvAmbientCardArtwork(title = card.title, category = card.category)
            }

            // Top subtle gradient scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    ),
            )

            // Top-right iTune Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_itune_waves),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White.copy(alpha = 0.95f)),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "iTune",
                    color = Color.White.copy(alpha = 0.95f),
                    fontFamily = SFProDisplay,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
            }

            // Bottom Liquid Glass Scrim with Metadata
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.96f),
                            )
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = card.title,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 23.sp,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    val desc = card.subtitle.ifBlank { card.caption }
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.5.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // TV Remote Focus Play Button Overlay
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AppleMusicRed)
                        .shadow(14.dp, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
    }
}

/**
 * Concentric circular ambient artwork matching Apple Music Station cards (like Michael Cavanna's Station).
 */
@Composable
private fun TvAmbientCardArtwork(
    title: String,
    category: String,
    modifier: Modifier = Modifier,
) {
    val bgGradient = remember(title, category) {
        when {
            title.contains("Jazz", ignoreCase = true) || category.contains("Jazz", ignoreCase = true) ->
                listOf(Color(0xFFE5B03E), Color(0xFF8A5A12))
            title.contains("Favorite", ignoreCase = true) || category.contains("Favorite", ignoreCase = true) ->
                listOf(Color(0xFF8E24AA), Color(0xFF3949AB))
            title.contains("Chill", ignoreCase = true) || title.contains("Relax", ignoreCase = true) ->
                listOf(Color(0xFF00897B), Color(0xFF004D40))
            title.contains("Party", ignoreCase = true) || title.contains("Energy", ignoreCase = true) ->
                listOf(Color(0xFFFF3D00), Color(0xFFC51162))
            else ->
                listOf(Color(0xFFEA1D3F), Color(0xFF9E0D27))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.radialGradient(bgGradient)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.44f)
            val stroke = Stroke(width = 2.dp.toPx())
            val ringColor = Color.White.copy(alpha = 0.28f)
            val radii = listOf(size.width * 0.12f, size.width * 0.24f, size.width * 0.36f, size.width * 0.48f)
            radii.forEach { r ->
                drawCircle(color = ringColor, radius = r, center = center, style = stroke)
            }
            drawCircle(color = Color.White.copy(alpha = 0.45f), radius = 6.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun TvSquareShelfCard(
    item: HomeFeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.width(180.dp),
    ) { isFocused ->
        Column(modifier = Modifier.width(180.dp)) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E22)),
            ) {
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF2E2E38), Color(0xFF1A1A20))
                                )
                            ),
                    )
                }

                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(AppleMusicRed),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = item.subtitle.ifBlank { item.artistName ?: "" },
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvArtistCircleCard(
    item: HomeFeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier.width(150.dp),
    ) { _ ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(150.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1E22)),
            ) {
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "Artist",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

private fun MusicTrack.toHomeFeedItem(): HomeFeedItem = HomeFeedItem(
    kind = HomeItemKind.SONG,
    title = title,
    subtitle = artists,
    videoId = id,
    thumbnailUrl = thumbnailUrl,
    artistName = artists,
    artistId = artistId,
    albumName = album,
    albumId = albumId,
    isExplicit = isExplicit,
)

private fun NewReleaseAlbum.toHomeFeedItem(): HomeFeedItem = HomeFeedItem(
    kind = HomeItemKind.ALBUM,
    title = title,
    subtitle = artist,
    browseId = browseId,
    thumbnailUrl = thumbnailUrl,
    artistName = artist,
    albumName = title,
    isExplicit = isExplicit,
)
