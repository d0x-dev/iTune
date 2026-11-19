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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.darkxvenom.itune.television.focus.TvAppleSilverBg
import com.darkxvenom.itune.television.focus.TvAppleSilverGradient
import com.darkxvenom.itune.television.focus.TvCard
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import com.darkxvenom.itune.ui.home.orderHomeShelves
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import kotlinx.coroutines.launch

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
fun TvHomeScreen(
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
    onReleaseClick: (browseId: String?, playlistId: String?, title: String, subtitle: String?, thumb: String?) -> Unit = { _, _, _, _, _ -> },
    onOpenPlayer: () -> Unit,
    topBarFocusRequester: FocusRequester? = null,
    topBar: (@Composable () -> Unit)? = null,
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
                val releaseId = item.browseId ?: item.watchPlaylistId
                if (!releaseId.isNullOrBlank()) {
                    onReleaseClick(releaseId, null, item.title, item.subtitle, item.thumbnailUrl)
                } else {
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
                    }
                }
            }
            else -> {}
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TvAppleSilverBg),
        contentPadding = PaddingValues(top = 0.dp, bottom = 48.dp),
    ) {
        // Top Navigation Bar scrolling with content
        if (topBar != null) {
            item(key = "home_top_bar") {
                topBar()
            }
        }

        // Section 1: "Top Picks" with Apple Music TV Portrait Cards
        item(key = "home_top_picks_section") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Top Picks",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp),
                )

                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(heroCards, key = { it.id }) { card ->
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier
                                .width(210.dp)
                                .then(
                                    if (topBarFocusRequester != null) {
                                        Modifier.focusProperties { up = topBarFocusRequester }
                                    } else Modifier
                                ),
                        ) {
                            Text(
                                text = card.category.ifBlank { "Top Pick" },
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.5.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
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
                Spacer(modifier = Modifier.height(18.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (recentSongs.isNotEmpty()) "Recently Played" else "New Releases",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp),
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                Spacer(modifier = Modifier.height(18.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    val title = if (shelf.strapline.isBlank()) {
                        shelf.title
                    } else {
                        "${shelf.strapline.lowercase().replaceFirstChar { it.uppercase() }} ${shelf.title}"
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp),
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
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
 * 10-Foot Television Tall Portrait Hero Card matching Apple Music on Apple TV (tvOS 17/18).
 * Not too much curved corners (14.dp), 30% below blurred/palette wash with centered title and subtitle.
 */
@Composable
private fun TvPortraitHeroCard(
    card: HomeHeroCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Artwork palette wash for the bottom 30% panel
    val palette = rememberArtworkPalette(imageUrl = card.imageUrl)

    val cardCornerRadius = 8.dp
    val cardShape = RoundedCornerShape(cardCornerRadius)

    Box(
        modifier = modifier
            .width(210.dp)
            .height(285.dp)
            .tvFocusable(
                shape = cardShape,
                scaleOnFocus = 1.05f,
                elevationOnFocus = 16.dp,
                interactionSource = interactionSource,
                onClick = onClick,
            )
            .clip(cardShape)
            .background(Color(0xFF1E1E22)),
    ) {
        // Upper ~70%: Artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.69f)
                .align(Alignment.TopCenter),
        ) {
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

            // Top-right Apple Music / iTune Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_itune_waves),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "iTune",
                    color = Color.White,
                    fontFamily = SFProDisplay,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                )
            }
        }

        // Lower ~30%: Tinted Blurred Palette Wash with Centered Title & Subtitle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.31f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(bottomStart = cardCornerRadius, bottomEnd = cardCornerRadius))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.wash.copy(alpha = 0.90f),
                            palette.background.copy(alpha = 0.98f),
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = card.title,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
                val desc = card.subtitle.ifBlank { card.caption }
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = desc,
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 11.5.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
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
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val captionDrop by animateDpAsState(
        targetValue = if (isFocused) 6.dp else 0.dp,
        animationSpec = spring(dampingRatio = 1f, stiffness = 420f),
        label = "shelfCaptionDrop",
    )

    Column(
        modifier = modifier.width(136.dp),
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .tvFocusable(
                    shape = RoundedCornerShape(12.dp),
                    scaleOnFocus = 1.06f,
                    elevationOnFocus = 16.dp,
                    interactionSource = interactionSource,
                    onClick = onClick,
                )
                .clip(RoundedCornerShape(12.dp))
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
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = captionDrop.toPx() }
                .padding(top = 6.dp),
        ) {
            Text(
                text = item.title,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.90f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val sub = item.subtitle.ifBlank { item.artistName ?: "" }
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TvArtistCircleCard(
    item: HomeFeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val captionDrop by animateDpAsState(
        targetValue = if (isFocused) 6.dp else 0.dp,
        animationSpec = spring(dampingRatio = 1f, stiffness = 420f),
        label = "artistCaptionDrop",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(116.dp),
    ) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .tvFocusable(
                    shape = CircleShape,
                    scaleOnFocus = 1.06f,
                    elevationOnFocus = 16.dp,
                    interactionSource = interactionSource,
                    onClick = onClick,
                )
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

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = captionDrop.toPx() }
                .padding(top = 6.dp),
        ) {
            Text(
                text = item.title,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.90f),
                fontSize = 12.5.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "Artist",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
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
