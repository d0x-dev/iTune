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

package com.darkxvenom.itune.television.ui.artist


import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.ColorFilter
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
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.artist.AppleArtistArt
import com.darkxvenom.itune.data.artist.AppleArtistArtRepository
import com.darkxvenom.itune.data.artist.ArtistRepository
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.model.ArtistPage
import com.darkxvenom.itune.data.model.HomeShelf
import com.darkxvenom.itune.data.model.ShelfItem
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.television.ui.common.TvAmbientBackground
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

/**
 * 10-Foot Television Artist Screen matching Apple TV tvOS layout:
 * - Dynamic color-changing ambient background reacting to the artist's photography.
 * - Hero Header: Large high-res artist portrait, bold typography, listener metrics, and action buttons.
 * - Top Songs: Ranked top tracks with instant playback & queueing.
 * - Albums, Singles & EPs: Horizontal shelves with focused magnification.
 * - Featured In & Playlists: Curated editorial and community playlists.
 * - Fans Also Like / Similar Artists: Interactive circular artist cards with recursive navigation.
 * - Artist Bio / Description card.
 */
@Composable
fun TvArtistScreen(
    artistName: String,
    artistThumb: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    artistBrowseId: String? = null,
    onNavigateArtist: (browseId: String?, name: String, thumb: String?) -> Unit = { _, _, _ -> },
    onReleaseClick: (browseId: String?, playlistId: String?, title: String, subtitle: String?, thumb: String?) -> Unit = { _, _, _, _, _ -> },
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var resolvedId by remember(artistBrowseId) { mutableStateOf(artistBrowseId) }
    val likedArtists by LibraryStore.likedArtists.collectAsStateWithLifecycle()
    val isSubscribed = remember(likedArtists, resolvedId, artistName) {
        likedArtists.any { it.browseId == resolvedId || it.title.equals(artistName, ignoreCase = true) }
    }

    var artistPage by remember(artistName, artistBrowseId) { mutableStateOf<ArtistPage?>(null) }
    var fallbackTracks by remember(artistName) { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isLoading by remember(artistName, artistBrowseId) { mutableStateOf(true) }

    val playButtonFocus = remember { FocusRequester() }

    LaunchedEffect(artistName, artistBrowseId) {
        isLoading = true
        try {
            val resolvedBrowseId = artistBrowseId ?: runCatching {
                ArtistRepository.findArtistPageId(artistName).getOrNull()
            }.getOrNull()

            if (!resolvedBrowseId.isNullOrBlank()) {
                resolvedId = resolvedBrowseId
                val pageResult = ArtistRepository.artistPage(resolvedBrowseId)
                if (pageResult.isSuccess) {
                    artistPage = pageResult.getOrNull()
                }
            }

            if (artistPage == null) {
                // Fallback search
                fallbackTracks = MusicBackend.searchMusic(artistName)
            }
        } catch (_: Exception) {
            fallbackTracks = emptyList()
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            runCatching { playButtonFocus.requestFocus() }
        }
    }

    val page = artistPage
    val effectiveThumb = page?.thumbnailUrl ?: artistThumb
    val effectiveName = page?.name ?: artistName
    val topSongs = remember(page, fallbackTracks) {
        page?.songs?.map { it.toMusicTrack() } ?: fallbackTracks
    }

    var appleArt by remember(resolvedId) {
        mutableStateOf(resolvedId?.let { AppleArtistArtRepository.cached(it) })
    }

    LaunchedEffect(resolvedId, effectiveName, topSongs) {
        val bId = resolvedId ?: return@LaunchedEffect
        if (appleArt == null && topSongs.isNotEmpty()) {
            val art = AppleArtistArtRepository.artFor(bId, effectiveName) {
                topSongs.map { it.title }
            }
            if (art != null) {
                appleArt = art
            }
        }
    }

    val heroUrl = appleArt?.heroUrl ?: effectiveThumb
    val palette = rememberArtworkPalette(imageUrl = heroUrl ?: effectiveThumb)
    val pageColor = palette.wash

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(pageColor),
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.ui_loading, effectiveName),
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 15.sp,
                        fontFamily = SFProDisplay,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp),
            ) {
                // ── Hero Header: Full-bleed cover top page blended like app module PageBackground ──
                item(key = "artist_hero") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(480.dp),
                    ) {
                        // Background hero photography - aligned to TopCenter so avatar is fully visible
                        if (heroUrl != null) {
                            AsyncImage(
                                model = heroUrl.highQualityThumbnail(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(pageColor),
                            )
                        }

                        // Gradient blend matching app module PageBackground: smoothly melts into pageColor
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.00f to Color.Black.copy(alpha = 0.20f),
                                        0.25f to Color.Transparent,
                                        0.50f to pageColor.copy(alpha = 0.35f),
                                        0.72f to pageColor.copy(alpha = 0.75f),
                                        0.88f to pageColor.copy(alpha = 0.94f),
                                        1.00f to pageColor,
                                    ),
                                ),
                        )

                        // Artist profile details & Apple-styled action buttons
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 24.dp),
                        ) {
                            val logoUrl = appleArt?.logoUrl
                            if (logoUrl != null) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = effectiveName,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .height(70.dp)
                                        .padding(bottom = 12.dp),
                                )
                            } else {
                                Text(
                                    text = effectiveName,
                                    color = Color.White,
                                    fontSize = 44.sp,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.8).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                            }

                            val subtitle = page?.monthlyListenerCount
                                ?: page?.subscriberCountText
                                ?: "Artist"
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 15.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Medium,
                            )

                            Spacer(Modifier.height(18.dp))

                            // Action Buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Play All Button
                                TvArtistActionButton(
                                    label = "Play",
                                    icon = Icons.Rounded.PlayArrow,
                                    isPrimary = true,
                                    focusRequester = playButtonFocus,
                                    onClick = {
                                        if (topSongs.isNotEmpty()) {
                                            MusicPlayerState.playWithRadioQueue(topSongs.first(), expandPlayer = false)
                                        }
                                    },
                                )

                                // Shuffle Button
                                TvArtistActionButton(
                                    label = "Shuffle",
                                    icon = Icons.Rounded.Shuffle,
                                    isPrimary = false,
                                    onClick = {
                                        if (topSongs.isNotEmpty()) {
                                            MusicPlayerState.play(topSongs.shuffled().first(), topSongs.shuffled(), expandPlayer = false)
                                        }
                                    },
                                )

                                // Subscribe / Following Button with YouTube Sync
                                TvArtistActionButton(
                                    label = if (isSubscribed) "Subscribed" else "Subscribe",
                                    icon = if (isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add,
                                    isPrimary = isSubscribed,
                                    onClick = {
                                        val id = resolvedId ?: ""
                                        if (isSubscribed) {
                                            if (id.isNotBlank()) {
                                                LibraryStore.removeArtist(context, id)
                                                YouTubeLibrarySyncManager.pushArtistSubscriptionAsync(id, false)
                                            }
                                        } else {
                                            if (id.isNotBlank()) {
                                                LibraryStore.saveArtist(
                                                    context = context,
                                                    browseId = id,
                                                    title = effectiveName,
                                                    subtitle = page?.subscriberCountText ?: "Artist",
                                                    thumbnailUrl = effectiveThumb,
                                                )
                                                YouTubeLibrarySyncManager.pushArtistSubscriptionAsync(id, true)
                                            }
                                        }
                                    },
                                )

                                // Radio Button
                                TvArtistCircleIcon(
                                    icon = Icons.Rounded.Radio,
                                    contentDescription = stringResource(R.string.ui_artist_radio),
                                    onClick = {
                                        if (topSongs.isNotEmpty()) {
                                            MusicPlayerState.playWithRadioQueue(topSongs.first(), expandPlayer = false)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // ── Section 1: Top Songs ─────────────────────────────────────
                if (topSongs.isNotEmpty()) {
                    item(key = "section_top_songs_header") {
                        Text(
                            text = stringResource(R.string.ui_top_songs),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 48.dp, top = 20.dp, bottom = 10.dp),
                        )
                    }

                    val songsToShow = topSongs.take(8)
                    itemsIndexed(songsToShow, key = { _, s -> s.id }) { index, song ->
                        TvArtistTrackRow(
                            index = index + 1,
                            track = song,
                            onClick = { MusicPlayerState.play(song, topSongs, expandPlayer = false) },
                        )
                    }
                }

                // ── Sections from Innertube: Albums, Singles, Featured, Similar Artists ──
                page?.sections?.forEach { shelf ->
                    if (shelf.items.isNotEmpty()) {
                        item(key = "shelf_header_${shelf.title}") {
                            Text(
                                text = shelf.title,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 48.dp, top = 28.dp, bottom = 12.dp),
                            )
                        }

                        item(key = "shelf_row_${shelf.title}") {
                            val isSimilarArtists = shelf.title.contains("Similar", ignoreCase = true) ||
                                shelf.title.contains("Fans Also Like", ignoreCase = true)

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                items(shelf.items, key = { it.title + it.browseId }) { item ->
                                    if (isSimilarArtists) {
                                        TvSimilarArtistCard(
                                            item = item,
                                            onClick = {
                                                onNavigateArtist(item.browseId, item.title, item.thumbnailUrl)
                                            },
                                        )
                                    } else {
                                        TvArtistShelfCard(
                                            item = item,
                                            onClick = {
                                                if (!item.browseId.isNullOrBlank()) {
                                                    onReleaseClick(item.browseId, null, item.title, effectiveName, item.thumbnailUrl)
                                                } else {
                                                    val videoId = item.videoId
                                                    if (videoId != null) {
                                                        val tr = MusicTrack(
                                                            id = videoId,
                                                            title = item.title,
                                                            artists = item.subtitle.ifBlank { effectiveName },
                                                            thumbnailUrl = item.thumbnailUrl.orEmpty(),
                                                        )
                                                        MusicPlayerState.play(tr, listOf(tr), expandPlayer = false)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Section: Artist Bio / Description ─────────────────────────
                val bio = page?.description?.takeIf { it.isNotBlank() }
                if (!bio.isNullOrBlank()) {
                    item(key = "artist_bio") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 28.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ui_about, effectiveName),
                                color = Color.White,
                                fontSize = 22.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                                    .padding(20.dp),
                            ) {
                                Text(
                                    text = bio,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    fontFamily = SFProDisplay,
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Artist Action Buttons ──────────────────────────────────────────────────────────────────────

@Composable
private fun TvArtistActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isPrimary -> Color(0xFF007AFF)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val textColor = if (isFocused) Color.Black else Color.White

    Box(
        modifier = modifier
            .height(44.dp)
            .tvFocusable(
                shape = RoundedCornerShape(22.dp),
                scaleOnFocus = 1.06f,
                elevationOnFocus = 8.dp,
                interactionSource = interaction,
                focusRequester = focusRequester,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 15.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TvArtistCircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(44.dp)
            .tvFocusable(
                shape = CircleShape,
                scaleOnFocus = 1.10f,
                elevationOnFocus = 6.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Artist Top Songs Table Row ─────────────────────────────────────────────────────────────────

@Composable
private fun TvArtistTrackRow(
    index: Int,
    track: MusicTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val isCurrentTrack = MusicPlayerState.currentTrack?.id == track.id
    val isPlaying = MusicPlayerState.isPlaying && isCurrentTrack

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 48.dp, vertical = 2.dp)
            .tvFocusable(
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.02f,
                elevationOnFocus = 0.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.22f) else Color(0x0EFFFFFF))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPlaying) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                AnimatedEqualizer(
                    isPlaying = true,
                    color = if (isFocused) Color.White else AppleMusicRed,
                    size = 18.dp,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.ui_text_c79f71243f, index),
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 14.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp),
            )
        }

        AsyncImage(
            model = track.thumbnailUrl?.highQualityThumbnail(),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artists,
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 12.5.sp,
                fontFamily = SFProDisplay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (track.isExplicit) {
            ExplicitBadge(color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.width(12.dp))
        }

        val durationFormatted = remember(track.durationSec) {
            val m = track.durationSec / 60
            val s = track.durationSec % 60
            "%d:%02d".format(m, s)
        }
        Text(
            text = durationFormatted,
            color = Color.White.copy(alpha = 0.40f),
            fontSize = 13.sp,
            fontFamily = SFProDisplay,
        )
    }
}

// ── Albums / Singles Card ──────────────────────────────────────────────────────────────────────

@Composable
private fun TvArtistShelfCard(
    item: ShelfItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .width(160.dp)
            .tvFocusable(
                shape = RoundedCornerShape(14.dp),
                scaleOnFocus = 1.08f,
                elevationOnFocus = 12.dp,
                interactionSource = interaction,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
        ) {
            AsyncImage(
                model = item.thumbnailUrl?.highQualityThumbnail(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontFamily = SFProDisplay,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Similar Artist Circular Card ───────────────────────────────────────────────────────────────

@Composable
private fun TvSimilarArtistCard(
    item: ShelfItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .width(140.dp)
            .tvFocusable(
                shape = CircleShape,
                scaleOnFocus = 1.10f,
                elevationOnFocus = 12.dp,
                interactionSource = interaction,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(if (isFocused) 2.dp else 1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.20f), CircleShape),
        ) {
            AsyncImage(
                model = item.thumbnailUrl?.highQualityThumbnail(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
