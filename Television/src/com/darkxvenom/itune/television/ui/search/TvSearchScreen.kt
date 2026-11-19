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

package com.darkxvenom.itune.television.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.television.ui.common.TvAmbientBackground
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import kotlinx.coroutines.delay

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private enum class TvSearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
}

/**
 * 10-Foot Television Search Screen matching Apple TV tvOS layout:
 * - Top Search input bar with automatic query execution & clean TV keyboard handling.
 * - Apple-style slide filter switches ("All", "Songs", "Artists", "Albums", "Playlists").
 * - Live dynamic search autocomplete suggestions.
 * - Multi-category grouped results with circular artist avatars, song tables, and album shelves.
 */
@Composable
fun TvSearchScreen(
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
    onReleaseClick: (browseId: String?, playlistId: String?, title: String, subtitle: String?, thumb: String?) -> Unit = { _, _, _, _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeFilter by remember { mutableStateOf(TvSearchFilter.ALL) }
    var isSearching by remember { mutableStateOf(false) }

    val searchInputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120L)
        searchInputFocusRequester.requestFocus()
    }

    // Live search suggestions
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            delay(180L)
            suggestions = runCatching { MusicBackend.searchSuggestions(q) }.getOrDefault(emptyList()).take(8)
        } else {
            suggestions = emptyList()
        }
    }

    // Search query results
    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        delay(380L)
        isSearching = true
        try {
            val results = MusicBackend.searchMusic(query)
            searchResults = results
        } catch (_: Exception) {
            searchResults = emptyList()
        } finally {
            isSearching = false
        }
    }

    // Filter results
    val artists = remember(searchResults) {
        searchResults.map { it.artists.substringBefore(",").trim() }
            .distinct()
            .take(12)
            .map { name ->
                val track = searchResults.firstOrNull { it.artists.startsWith(name) }
                name to (track?.thumbnailUrl ?: "")
            }
    }

    val albums = remember(searchResults) {
        searchResults.filter { !it.album.isNullOrBlank() }
            .distinctBy { it.album }
            .take(12)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
    ) {
        TvAmbientBackground(Modifier.fillMaxSize(), dim = 0.35f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            // ── Top: Search Title & TV Input ──────────────────────────────────
            Text(
                text = "Search",
                color = Color.White,
                fontSize = 28.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )

            Spacer(Modifier.height(14.dp))

            // TV Search Input Box
            var isInputFocused by remember { mutableStateOf(false) }
            val inputBorderColor by animateColorAsState(
                targetValue = if (isInputFocused) Color.White else Color.White.copy(alpha = 0.20f),
                animationSpec = tween(180),
                label = "tvSearchInputBorder",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1CFFFFFF))
                    .border(if (isInputFocused) 2.dp else 1.dp, inputBorderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search Icon",
                        tint = if (isInputFocused) Color.White else Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.size(22.dp),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 17.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Normal,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { isSearching = false }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchInputFocusRequester)
                            .onFocusChanged { isInputFocused = it.isFocused },
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Artists, Songs, Albums, Playlists…",
                                    color = Color.White.copy(alpha = 0.38f),
                                    fontSize = 16.sp,
                                    fontFamily = SFProDisplay,
                                )
                            }
                            innerTextField()
                        },
                    )

                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.60f),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { searchQuery = "" },
                        )
                    }
                }
            }

            // ── Live Search Suggestions Pills ─────────────────────────────────
            AnimatedVisibility(visible = suggestions.isNotEmpty() && searchQuery.isNotBlank()) {
                LazyRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(suggestions) { s ->
                        TvSuggestionChip(
                            text = s,
                            onClick = { searchQuery = s },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Apple TV Sliding Pill Filter Switch ───────────────────────────
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvSearchFilter.entries.forEach { filter ->
                    TvFilterPill(
                        label = filter.label,
                        isSelected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Results Area ──────────────────────────────────────────────────
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp),
                    )
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "Search your favorite artists, albums and tracks" else "No results found for \"$searchQuery\"",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 16.sp,
                        fontFamily = SFProDisplay,
                    )
                }
            } else {
                when (activeFilter) {
                    TvSearchFilter.ALL -> {
                        TvSearchAllView(
                            artists = artists,
                            songs = searchResults,
                            albums = albums,
                            onArtistClick = onArtistClick,
                            onReleaseClick = onReleaseClick,
                            onPlayTrack = { tr, list -> MusicPlayerState.play(tr, list, expandPlayer = false) },
                        )
                    }
                    TvSearchFilter.SONGS -> {
                        TvSearchSongsView(
                            songs = searchResults,
                            onPlayTrack = { tr, list -> MusicPlayerState.play(tr, list, expandPlayer = false) },
                        )
                    }
                    TvSearchFilter.ARTISTS -> {
                        TvSearchArtistsGrid(
                            artists = artists,
                            onArtistClick = onArtistClick,
                        )
                    }
                    TvSearchFilter.ALBUMS -> {
                        TvSearchAlbumsGrid(
                            albums = albums,
                            onReleaseClick = onReleaseClick,
                        )
                    }
                    TvSearchFilter.PLAYLISTS -> {
                        TvSearchAlbumsGrid(
                            albums = albums,
                            onReleaseClick = onReleaseClick,
                        )
                    }
                }
            }
        }
    }
}

// ── Search Filter Pill ─────────────────────────────────────────────────────────────────────────

@Composable
private fun TvFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isSelected -> Color.White.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused -> Color.Black
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.65f)
    }

    Box(
        modifier = modifier
            .height(34.dp)
            .tvFocusable(
                shape = CircleShape,
                scaleOnFocus = 1.05f,
                elevationOnFocus = 0.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// ── Suggestion Chip ────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSuggestionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .height(32.dp)
            .tvFocusable(
                shape = RoundedCornerShape(16.dp),
                scaleOnFocus = 1.06f,
                elevationOnFocus = 2.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.12f))
            .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontFamily = SFProDisplay,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Grouped "All" Results View ─────────────────────────────────────────────────────────────────

@Composable
private fun TvSearchAllView(
    artists: List<Pair<String, String>>,
    songs: List<MusicTrack>,
    albums: List<MusicTrack>,
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
    onReleaseClick: (browseId: String?, playlistId: String?, title: String, subtitle: String?, thumb: String?) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Top Artists Row
        if (artists.isNotEmpty()) {
            item(key = "search_artists_header") {
                Text(
                    text = "Artists",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                )
            }
            item(key = "search_artists_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(artists) { (name, thumb) ->
                        TvSearchArtistCard(
                            name = name,
                            thumb = thumb,
                            onClick = { onArtistClick(null, name, thumb) },
                        )
                    }
                }
            }
        }

        // Songs
        if (songs.isNotEmpty()) {
            item(key = "search_songs_header") {
                Text(
                    text = "Songs",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                )
            }

            val displaySongs = songs.take(6)
            itemsIndexed(displaySongs, key = { _, s -> s.id }) { index, track ->
                TvSearchSongRow(
                    index = index + 1,
                    track = track,
                    onClick = { onPlayTrack(track, songs) },
                )
            }
        }

        // Albums
        if (albums.isNotEmpty()) {
            item(key = "search_albums_header") {
                Text(
                    text = "Albums",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                )
            }
            item(key = "search_albums_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(albums) { albumTrack ->
                        TvSearchAlbumCard(
                            track = albumTrack,
                            onClick = {
                                onReleaseClick(
                                    albumTrack.albumId ?: albumTrack.id,
                                    null,
                                    albumTrack.album ?: albumTrack.title,
                                    albumTrack.artists,
                                    albumTrack.thumbnailUrl,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Search Songs View ──────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSearchSongsView(
    songs: List<MusicTrack>,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(songs, key = { _, s -> s.id }) { index, track ->
            TvSearchSongRow(
                index = index + 1,
                track = track,
                onClick = { onPlayTrack(track, songs) },
            )
        }
    }
}

@Composable
private fun TvSearchSongRow(
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
                text = "$index",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 13.sp,
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

// ── Search Artists Grid ────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSearchArtistsGrid(
    artists: List<Pair<String, String>>,
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        items(artists) { (name, thumb) ->
            TvSearchArtistCard(
                name = name,
                thumb = thumb,
                onClick = { onArtistClick(null, name, thumb) },
            )
        }
    }
}

@Composable
private fun TvSearchArtistCard(
    name: String,
    thumb: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .width(150.dp)
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
                .size(140.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(if (isFocused) 2.dp else 1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.20f), CircleShape),
        ) {
            AsyncImage(
                model = thumb.highQualityThumbnail(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = name,
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

// ── Search Albums Grid ─────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSearchAlbumsGrid(
    albums: List<MusicTrack>,
    onReleaseClick: (browseId: String?, playlistId: String?, title: String, subtitle: String?, thumb: String?) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(albums, key = { it.id }) { track ->
            TvSearchAlbumCard(
                track = track,
                onClick = {
                    onReleaseClick(
                        track.albumId ?: track.id,
                        null,
                        track.album ?: track.title,
                        track.artists,
                        track.thumbnailUrl,
                    )
                },
            )
        }
    }
}

@Composable
private fun TvSearchAlbumCard(
    track: MusicTrack,
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
                model = track.thumbnailUrl?.highQualityThumbnail(),
                contentDescription = track.album ?: track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = track.album ?: track.title,
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artists,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            fontFamily = SFProDisplay,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
