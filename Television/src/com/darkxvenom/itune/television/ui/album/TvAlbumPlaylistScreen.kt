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

package com.darkxvenom.itune.television.ui.album

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.artist.ArtistInnertubeParser
import com.darkxvenom.itune.data.artist.ArtistRepository
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import com.darkxvenom.itune.ui.components.AnimatedEqualizer

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

/**
 * Authentic Apple Music TV styled Album and Playlist Detail Screen.
 * Two-pane split layout: Left large sleeve artwork, Right rich header, action buttons, and full tracklist.
 * Includes dynamic ambient background colors matching the app module's artwork palette.
 */
@Composable
fun TvAlbumPlaylistScreen(
    browseId: String?,
    playlistId: String?,
    initialTitle: String,
    initialSubtitle: String?,
    initialThumb: String?,
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
    onOpenPlayer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var title by remember { mutableStateOf(initialTitle) }
    var subtitle by remember { mutableStateOf(initialSubtitle ?: "") }
    var thumbnailUrl by remember { mutableStateOf(initialThumb) }
    var description by remember { mutableStateOf<String?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var isDescExpanded by remember { mutableStateOf(false) }

    val userPlaylists by LibraryStore.userPlaylists.collectAsStateWithLifecycle()
    val savedReleases by ArtistStateStore.savedReleases.collectAsStateWithLifecycle()
    val isSaved = remember(browseId, savedReleases) {
        !browseId.isNullOrBlank() && browseId in savedReleases
    }

    LaunchedEffect(browseId, playlistId) {
        isLoading = true
        if (!playlistId.isNullOrBlank()) {
            val userPlaylist = userPlaylists.firstOrNull { it.id == playlistId }
                ?: LibraryStore.userPlaylists.value.firstOrNull { it.id == playlistId }
            if (userPlaylist != null) {
                title = userPlaylist.name
                subtitle = "${userPlaylist.songs.size} Songs"
                thumbnailUrl = userPlaylist.leadArtwork ?: initialThumb
                songs = userPlaylist.songs.map { meta ->
                    Song(
                        videoId = meta.id,
                        title = meta.title,
                        artist = meta.artists,
                        artistId = meta.artistId,
                        albumName = meta.album,
                        thumbnailUrl = meta.thumbnailUrl,
                        durationText = if (meta.durationSec > 0) {
                            val m = meta.durationSec / 60
                            val s = meta.durationSec % 60
                            "$m:${s.toString().padStart(2, '0')}"
                        } else null,
                        isExplicit = meta.isExplicit,
                    )
                }
                isLoading = false
            }
        } else if (!browseId.isNullOrBlank()) {
            ArtistRepository.browseSongs(browseId).fold(
                onSuccess = { page ->
                    if (page.header != null) {
                        if (page.header.title.isNotBlank()) title = page.header.title
                        if (page.header.subtitle.isNotBlank()) subtitle = page.header.subtitle
                        if (!page.header.thumbnailUrl.isNullOrBlank()) thumbnailUrl = page.header.thumbnailUrl
                    }
                    description = page.description
                    songs = page.songs
                    isLoading = false
                },
                onFailure = {
                    isLoading = false
                },
            )
        } else {
            isLoading = false
        }
    }

    // Dynamic Artwork Palette (same as app module)
    val palette = rememberArtworkPalette(imageUrl = thumbnailUrl)

    val listState = rememberLazyListState()
    val playButtonFocus = remember { FocusRequester() }
    val firstSongFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { playButtonFocus.requestFocus() }
    }

    val currentPlayingId = MusicPlayerState.currentTrack?.id
    val isPlaying = MusicPlayerState.isPlaying

    // Ambient background with radial glow matching artwork palette
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        palette.wash.copy(alpha = 0.80f),
                        palette.background.copy(alpha = 0.95f),
                        palette.background,
                        Color(0xFF0A0A0C),
                    ),
                    center = Offset(450f, 320f),
                    radius = 1800f,
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 36.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            // LEFT COLUMN: Large Square Album Sleeve Artwork
            Column(
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(370.dp)
                        .shadow(
                            elevation = 28.dp,
                            shape = RoundedCornerShape(16.dp),
                            spotColor = Color.Black.copy(alpha = 0.65f),
                            ambientColor = Color.Black.copy(alpha = 0.50f),
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E)),
                ) {
                    AsyncImage(
                        model = thumbnailUrl?.highQualityThumbnail() ?: thumbnailUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // RIGHT COLUMN: Header Info, Actions & Full Tracklist
            Column(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxHeight(),
            ) {
                // Title
                Text(
                    text = title,
                    fontSize = 28.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 34.sp,
                )

                // Artist / Curator Subtitle
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        fontSize = 18.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Metadata Line: Genre · Year · Lossless Badge
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val countText = if (songs.isNotEmpty()) "${songs.size} Songs" else ""
                    if (countText.isNotBlank()) {
                        Text(
                            text = countText,
                            fontSize = 13.sp,
                            fontFamily = SFProDisplay,
                            color = Color.White.copy(alpha = 0.50f),
                        )
                        Text(
                            text = "·",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.40f),
                        )
                    }

                    // Lossless Audio Wave Badge
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_lossless),
                        contentDescription = "Lossless",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Lossless",
                        fontSize = 12.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }

                // Editor's Notes / Description with MORE link
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = description.orEmpty(),
                        fontSize = 12.sp,
                        fontFamily = SFProDisplay,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = if (isDescExpanded) 10 else 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                        modifier = Modifier.clickable { isDescExpanded = !isDescExpanded },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Action Row: Play, Shuffle, 3-dots More button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Play Button
                    TvPillButton(
                        iconRes = R.drawable.play,
                        label = "Play",
                        focusRequester = playButtonFocus,
                        onClick = {
                            if (songs.isNotEmpty()) {
                                val q = songs.map { it.toMusicTrack() }
                                MusicPlayerState.play(q.first(), q, expandPlayer = false)
                                onOpenPlayer()
                            }
                        },
                        modifier = Modifier.focusProperties {
                            down = firstSongFocus
                        },
                    )

                    // Shuffle Button
                    TvPillButton(
                        iconRes = R.drawable.shuffle,
                        label = "Shuffle",
                        focusRequester = null,
                        onClick = {
                            if (songs.isNotEmpty()) {
                                val q = songs.map { it.toMusicTrack() }.shuffled()
                                MusicPlayerState.play(q.first(), q, expandPlayer = false)
                                onOpenPlayer()
                            }
                        },
                        modifier = Modifier.focusProperties {
                            down = firstSongFocus
                        },
                    )

                    // 3-Dot More Options Button (matching app module)
                    TvCircleMoreButton(
                        iconRes = R.drawable.more_horiz,
                        onClick = { showMoreOptions = true },
                        modifier = Modifier.focusProperties {
                            down = firstSongFocus
                        },
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Tracklist
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                } else if (songs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No songs found in this release",
                            color = Color.White.copy(alpha = 0.50f),
                            fontFamily = SFProDisplay,
                            fontSize = 15.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(songs, key = { index, song -> song.videoId.ifBlank { "$index" } }) { idx, song ->
                            TvAlbumSongRow(
                                index = idx + 1,
                                song = song,
                                isPlaying = currentPlayingId == song.videoId && isPlaying,
                                focusRequester = if (idx == 0) firstSongFocus else null,
                                onClick = {
                                    val q = songs.map { it.toMusicTrack() }
                                    val track = song.toMusicTrack()
                                    MusicPlayerState.play(track, q, expandPlayer = false)
                                    onOpenPlayer()
                                },
                                modifier = if (idx == 0) {
                                    Modifier.focusProperties { up = playButtonFocus }
                                } else Modifier,
                            )
                        }
                    }
                }
            }
        }

        // 3-Dot More Options Popup Modal
        if (showMoreOptions) {
            TvReleaseOptionsPopup(
                title = title,
                subtitle = subtitle,
                browseId = browseId,
                isSaved = isSaved,
                songs = songs,
                onDismiss = { showMoreOptions = false },
                onArtistClick = {
                    val first = songs.firstOrNull()
                    val artistId = first?.artistId ?: first?.artists?.firstOrNull { !it.browseId.isNullOrBlank() }?.browseId
                    val artistName = first?.artist ?: subtitle
                    if (!artistId.isNullOrBlank()) {
                        showMoreOptions = false
                        onArtistClick(artistId, artistName, thumbnailUrl)
                    }
                },
            )
        }
    }
}

/**
 * Solid capsule Play/Shuffle button. Inverts to white fill with black text on focus.
 */
@Composable
private fun TvPillButton(
    iconRes: Int,
    label: String,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1.0f, label = "pillScale")

    Row(
        modifier = modifier
            .scale(scale)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.16f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

/**
 * Circular 3-dot More Options button. Solid white on focus, translucent on idle.
 */
@Composable
private fun TvCircleMoreButton(
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1.0f, label = "circleScale")

    Box(
        modifier = modifier
            .scale(scale)
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.16f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = "More Options",
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Apple TV style Tracklist Song Row.
 * Highlights with a full-width solid white capsule pill, black play icon, bold black title, and dark duration.
 */
@Composable
private fun TvAlbumSongRow(
    index: Int,
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val dur = song.durationText ?: run {
        val sec = song.toMusicTrack().durationSec
        if (sec > 0) "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}" else ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White else Color.Transparent)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Track number, Equalizer wave, or Play icon
        if (isPlaying) {
            Box(modifier = Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                AnimatedEqualizer(
                    isPlaying = true,
                    color = if (isFocused) Color.Black else Color.White,
                    size = 16.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
        } else if (isFocused) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = "Play",
                tint = Color.Black,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Text(
                text = "$index",
                color = Color.White.copy(alpha = 0.40f),
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                modifier = Modifier.width(26.dp),
            )
            Spacer(Modifier.width(12.dp))
        }

        // Title
        Text(
            text = song.title,
            color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.90f),
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Explicit tag
        if (song.isExplicit == true) {
            Spacer(Modifier.width(6.dp))
            ExplicitBadge(color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.85f))
        }

        Spacer(Modifier.width(12.dp))

        // Duration
        if (dur.isNotBlank()) {
            Text(
                text = dur,
                color = if (isFocused) Color.Black.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.40f),
                fontFamily = SFProDisplay,
                fontSize = 13.sp,
            )
        }
    }
}

/**
 * Apple TV style Options Popup Modal for Albums and Playlists.
 */
@Composable
private fun TvReleaseOptionsPopup(
    title: String,
    subtitle: String,
    browseId: String?,
    isSaved: Boolean,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onArtistClick: () -> Unit,
) {
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    var showPlaylistPicker by remember { mutableStateOf(false) }
    val userPlaylists by LibraryStore.userPlaylists.collectAsStateWithLifecycle()

    BackHandler {
        if (showPlaylistPicker) {
            showPlaylistPicker = false
        } else {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF01C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(vertical = 18.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showPlaylistPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPlaylistPicker = false }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.80f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Add to Playlist",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (userPlaylists.isEmpty()) {
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
                        items(userPlaylists) { p ->
                            PopupRow(
                                icon = Icons.Rounded.Add,
                                label = p.name,
                                trailing = "${p.songs.size} songs",
                                focusRequester = null,
                                onClick = {
                                    val metadataList = songs.map { song ->
                                        val tr = song.toMusicTrack()
                                        SavedSongMetadata(
                                            id = song.videoId,
                                            title = song.title,
                                            artists = song.artist,
                                            album = song.albumName,
                                            thumbnailUrl = song.thumbnailUrl ?: "",
                                            durationSec = tr.durationSec,
                                            isExplicit = song.isExplicit == true,
                                            artistId = song.artistId,
                                        )
                                    }
                                    LibraryStore.addSongsToUserPlaylist(context, p.id, metadataList)
                                    Toast.makeText(context, "Added ${metadataList.size} songs to ${p.name}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 13.sp,
                        fontFamily = SFProDisplay,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))

                // Toggle Library Save
                if (!browseId.isNullOrBlank()) {
                    PopupRow(
                        icon = if (isSaved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = if (isSaved) "Remove from Library" else "Add to Library",
                        trailing = null,
                        focusRequester = firstFocus,
                        onClick = {
                            ArtistStateStore.toggleReleaseSaved(context, browseId, title, subtitle)
                            Toast.makeText(context, if (isSaved) "Removed from Library" else "Saved to Library", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    )
                }

                // Add to Playlist
                PopupRow(
                    icon = Icons.Rounded.Add,
                    label = "Add All to Playlist",
                    trailing = "${songs.size}",
                    focusRequester = if (browseId.isNullOrBlank()) firstFocus else null,
                    onClick = { showPlaylistPicker = true },
                )

                // Play Next in Queue
                PopupRow(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Play Next",
                    trailing = null,
                    focusRequester = null,
                    onClick = {
                        val tracks = songs.map { it.toMusicTrack() }
                        tracks.reversed().forEach { MusicPlayerState.playNext(it) }
                        Toast.makeText(context, "Added to queue next", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                )

                // Go to Artist
                PopupRow(
                    icon = Icons.Rounded.Person,
                    label = "Go to Artist",
                    trailing = null,
                    focusRequester = null,
                    onClick = onArtistClick,
                )

                // Share / Copy Link
                PopupRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy Link",
                    trailing = null,
                    focusRequester = null,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val cleanId = browseId?.removePrefix("VL") ?: ""
                        val link = "https://music.youtube.com/playlist?list=$cleanId"
                        val clip = ClipData.newPlainText("iTune Album Link", link)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun PopupRow(
    icon: ImageVector,
    label: String,
    trailing: String?,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.08f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 15.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                color = if (isFocused) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.50f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
            )
        }
    }
}
