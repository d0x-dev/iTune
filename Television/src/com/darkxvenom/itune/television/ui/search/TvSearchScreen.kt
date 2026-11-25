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


import androidx.compose.ui.res.stringResource
import com.darkxvenom.itune.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicArtist
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.UnifiedSearchResults
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.library.SearchHistoryStore
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvAppleSilverBg
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.search.TopResultItem
import com.darkxvenom.itune.ui.search.pickTopResult
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private enum class TvSearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists"),
}

/**
 * 10-Foot Television Search Screen matching Apple TV tvOS layout & App Module functionality:
 * - Silver background matching Home screen (TvAppleSilverBg).
 * - TV Search input bar with clear action.
 * - Does NOT fetch results automatically on keystroke.
 * - Shows "Recent Searches" initially, with Clear All and remove capabilities.
 * - Shows search suggestions as user types.
 * - On tapping any recent item, suggestion, or hitting Enter, starts loading with TV indicator.
 * - Displays Top Result card at the top (matching app module pickTopResult).
 * - Below Top Result, displays sections in order: Songs, Artists, Albums, Playlists.
 */
@Composable
fun TvSearchScreen(
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
    onReleaseClick: (browseId: String?, playlistId: String?, title: String, subtitle: String?, thumb: String?) -> Unit = { _, _, _, _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SearchHistoryStore.ensureInit(context)
    }

    val searchHistory by SearchHistoryStore.history.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var executedQuery by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf(UnifiedSearchResults()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeFilter by remember { mutableStateOf(TvSearchFilter.ALL) }
    var isSearching by remember { mutableStateOf(false) }

    val searchInputFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120L)
        searchInputFocusRequester.requestFocus()
    }

    // Live search suggestions as user types (only when not searching / not yet executed)
    LaunchedEffect(searchQuery, executedQuery) {
        val q = searchQuery.trim()
        if (q.length >= 2 && q != executedQuery) {
            delay(180L)
            suggestions = runCatching { MusicBackend.searchSuggestions(q) }.getOrDefault(emptyList()).take(8)
        } else {
            suggestions = emptyList()
        }
    }

    fun executeSearch(q: String) {
        val clean = q.trim()
        if (clean.isBlank()) return
        searchQuery = clean
        executedQuery = clean
        suggestions = emptyList()
        isSearching = true
        SearchHistoryStore.addQuery(context, clean)
        coroutineScope.launch {
            try {
                val res = MusicBackend.searchAllCategories(clean)
                searchResults = res
            } catch (_: Exception) {
                searchResults = UnifiedSearchResults()
            } finally {
                isSearching = false
            }
        }
    }

    BackHandler {
        if (executedQuery != null || searchQuery.isNotEmpty()) {
            searchQuery = ""
            executedQuery = null
            searchResults = UnifiedSearchResults()
        } else {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvAppleSilverBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            // ── Top: Search Title & TV Input ──────────────────────────────────
            Text(
                text = stringResource(R.string.ui_search),
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
                        contentDescription = stringResource(R.string.ui_search_icon),
                        tint = if (isInputFocused) Color.White else Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.size(22.dp),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isBlank()) {
                                executedQuery = null
                                searchResults = UnifiedSearchResults()
                            }
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 17.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Normal,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executeSearch(searchQuery) }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(searchInputFocusRequester)
                            .onFocusChanged { isInputFocused = it.isFocused },
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.ui_artists_songs_albums_playlists),
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
                            contentDescription = stringResource(R.string.ui_clear),
                            tint = Color.White.copy(alpha = 0.60f),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    searchQuery = ""
                                    executedQuery = null
                                    searchResults = UnifiedSearchResults()
                                },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Main Content Area: Suggestions / History / Loading / Results ───────────────
            if (isSearching) {
                // Loading State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(42.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Searching for \"${executedQuery.orEmpty()}\"…",
                            color = Color.White.copy(alpha = 0.60f),
                            fontSize = 15.sp,
                            fontFamily = SFProDisplay,
                        )
                    }
                }
            } else if (executedQuery != null) {
                // Search Results View
                if (searchResults.isEmpty) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No results found for \"$executedQuery\"",
                            color = Color.White.copy(alpha = 0.50f),
                            fontSize = 16.sp,
                            fontFamily = SFProDisplay,
                        )
                    }
                } else {
                    // Apple TV Filter Switches
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

                    Spacer(Modifier.height(16.dp))

                    val topResult = remember(searchResults, executedQuery) {
                        pickTopResult(executedQuery ?: "", searchResults)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 60.dp),
                    ) {
                        // 1. Top Result Card at top
                        if (activeFilter == TvSearchFilter.ALL && topResult != null) {
                            item(key = "top_result_section") {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                                    Text(
                                        text = stringResource(R.string.ui_top_result),
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontFamily = SFProDisplay,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp),
                                    )

                                    TvTopResultHeroCard(
                                        item = topResult,
                                        onPlaySong = { track ->
                                            MusicPlayerState.play(track, listOf(track) + searchResults.songs.filterNot { it.id == track.id }, expandPlayer = false)
                                        },
                                        onArtistClick = { artist ->
                                            onArtistClick(artist.id, artist.name, artist.thumbnailUrl)
                                        },
                                        onReleaseClick = { release ->
                                            val isAlbum = topResult is TopResultItem.Album
                                            onReleaseClick(
                                                if (isAlbum) release.id else null,
                                                if (!isAlbum) release.id else null,
                                                release.title,
                                                release.author,
                                                release.thumbnailUrl,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        // 2. Songs Section
                        if ((activeFilter == TvSearchFilter.ALL || activeFilter == TvSearchFilter.SONGS) && searchResults.songs.isNotEmpty()) {
                            item(key = "songs_header") {
                                Text(
                                    text = stringResource(R.string.songs),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
                                )
                            }

                            val songsToShow = if (activeFilter == TvSearchFilter.ALL) searchResults.songs.take(6) else searchResults.songs
                            itemsIndexed(songsToShow, key = { _, s -> "song_${s.id}" }) { index, track ->
                                TvSearchSongRow(
                                    track = track,
                                    index = index + 1,
                                    onPlay = {
                                        MusicPlayerState.play(track, searchResults.songs, expandPlayer = false)
                                    },
                                )
                            }
                        }

                        // 3. Artists Section
                        if ((activeFilter == TvSearchFilter.ALL || activeFilter == TvSearchFilter.ARTISTS) && searchResults.artists.isNotEmpty()) {
                            item(key = "artists_header") {
                                Text(
                                    text = stringResource(R.string.ui_artists),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                                )
                            }

                            item(key = "artists_shelf") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items(searchResults.artists, key = { "artist_${it.id}" }) { artist ->
                                        TvSearchArtistCard(
                                            name = artist.name,
                                            thumb = artist.thumbnailUrl ?: "",
                                            onClick = { onArtistClick(artist.id, artist.name, artist.thumbnailUrl) },
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Albums Section
                        if ((activeFilter == TvSearchFilter.ALL || activeFilter == TvSearchFilter.ALBUMS) && searchResults.albums.isNotEmpty()) {
                            item(key = "albums_header") {
                                Text(
                                    text = stringResource(R.string.ui_albums),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                                )
                            }

                            item(key = "albums_shelf") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items(searchResults.albums, key = { "album_${it.id}" }) { album ->
                                        TvSearchShelfItemCard(
                                            title = album.title,
                                            subtitle = album.author.ifBlank { "Album" },
                                            thumb = album.thumbnailUrl ?: "",
                                            onClick = {
                                                onReleaseClick(album.id, null, album.title, album.author, album.thumbnailUrl)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // 5. Playlists Section
                        if ((activeFilter == TvSearchFilter.ALL || activeFilter == TvSearchFilter.PLAYLISTS) && searchResults.playlists.isNotEmpty()) {
                            item(key = "playlists_header") {
                                Text(
                                    text = stringResource(R.string.ui_playlists),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                                )
                            }

                            item(key = "playlists_shelf") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    items(searchResults.playlists, key = { "playlist_${it.id}" }) { playlist ->
                                        TvSearchShelfItemCard(
                                            title = playlist.title,
                                            subtitle = playlist.author.ifBlank { "Playlist" },
                                            thumb = playlist.thumbnailUrl ?: "",
                                            onClick = {
                                                onReleaseClick(null, playlist.id, playlist.title, playlist.author, playlist.thumbnailUrl)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (searchQuery.isNotBlank() && suggestions.isNotEmpty()) {
                // Search Suggestions View
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.ui_suggestions),
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 14.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 48.dp),
                    ) {
                        items(suggestions) { suggestion ->
                            val interaction = remember { MutableInteractionSource() }
                            val isFocused by interaction.collectIsFocusedAsState()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .tvFocusable(
                                        shape = RoundedCornerShape(10.dp),
                                        scaleOnFocus = 1.02f,
                                        elevationOnFocus = 2.dp,
                                        interactionSource = interaction,
                                        onClick = { executeSearch(suggestion) },
                                    )
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isFocused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.50f),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = suggestion,
                                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.90f),
                                    fontSize = 15.sp,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    imageVector = Icons.Rounded.NorthWest,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.35f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                // Recent Searches View (Empty query state)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.ui_recent_searches),
                            color = Color.White,
                            fontSize = 19.sp,
                            fontFamily = SFProDisplay,
                            fontWeight = FontWeight.Bold,
                        )

                        if (searchHistory.isNotEmpty()) {
                            val clearInteraction = remember { MutableInteractionSource() }
                            val isClearFocused by clearInteraction.collectIsFocusedAsState()

                            Text(
                                text = stringResource(R.string.ui_clear_all),
                                color = if (isClearFocused) Color.White else AppleMusicRed,
                                fontSize = 13.5.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .tvFocusable(
                                        shape = RoundedCornerShape(8.dp),
                                        scaleOnFocus = 1.05f,
                                        interactionSource = clearInteraction,
                                        onClick = { SearchHistoryStore.clearAll(context) },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (searchHistory.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.ui_search_for_songs_artists_albums_and_playlists),
                                color = Color.White.copy(alpha = 0.38f),
                                fontSize = 15.5.sp,
                                fontFamily = SFProDisplay,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 48.dp),
                        ) {
                            items(searchHistory) { item ->
                                val interaction = remember { MutableInteractionSource() }
                                val isFocused by interaction.collectIsFocusedAsState()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .tvFocusable(
                                            shape = RoundedCornerShape(10.dp),
                                            scaleOnFocus = 1.02f,
                                            elevationOnFocus = 2.dp,
                                            interactionSource = interaction,
                                            onClick = { executeSearch(item) },
                                        )
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isFocused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.50f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        text = item,
                                        color = if (isFocused) Color.White else Color.White.copy(alpha = 0.90f),
                                        fontSize = 15.sp,
                                        fontFamily = SFProDisplay,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val delInteraction = remember { MutableInteractionSource() }
                                    val isDelFocused by delInteraction.collectIsFocusedAsState()
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .tvFocusable(
                                                shape = CircleShape,
                                                scaleOnFocus = 1.15f,
                                                interactionSource = delInteraction,
                                                onClick = { SearchHistoryStore.removeQuery(context, item) },
                                            )
                                            .clip(CircleShape)
                                            .background(if (isDelFocused) Color.White.copy(alpha = 0.25f) else Color.Transparent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.ui_remove),
                                            tint = if (isDelFocused) Color.White else Color.White.copy(alpha = 0.40f),
                                            modifier = Modifier.size(16.dp),
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

// ── Top Result Hero Card (Matching App Module Top Result) ──────────────────────────────────────

@Composable
private fun TvTopResultHeroCard(
    item: TopResultItem,
    onPlaySong: (MusicTrack) -> Unit,
    onArtistClick: (MusicArtist) -> Unit,
    onReleaseClick: (MusicPlaylistRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val kindLabel = when (item) {
        is TopResultItem.Song -> "SONG"
        is TopResultItem.Artist -> "ARTIST"
        is TopResultItem.Album -> "ALBUM"
        is TopResultItem.Playlist -> "PLAYLIST"
    }

    val subtitle = when (item) {
        is TopResultItem.Song -> "${item.track.artists} • Song"
        is TopResultItem.Artist -> item.artist.subscribers?.takeIf { it.isNotBlank() } ?: "Artist"
        is TopResultItem.Album -> "${item.album.author ?: "Various Artists"} • Album"
        is TopResultItem.Playlist -> "${item.playlist.author ?: "Curator"} • Playlist"
    }

    val isArtist = item is TopResultItem.Artist
    val artShape = if (isArtist) CircleShape else RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .tvFocusable(
                shape = RoundedCornerShape(16.dp),
                scaleOnFocus = 1.03f,
                elevationOnFocus = 12.dp,
                interactionSource = interaction,
                onClick = {
                    when (item) {
                        is TopResultItem.Song -> onPlaySong(item.track)
                        is TopResultItem.Artist -> onArtistClick(item.artist)
                        is TopResultItem.Album -> onReleaseClick(item.album)
                        is TopResultItem.Playlist -> onReleaseClick(item.playlist)
                    }
                },
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Artwork
        Box(
            modifier = Modifier
                .size(82.dp)
                .shadow(if (isFocused) 12.dp else 4.dp, artShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(artShape)
                .background(Color(0xFF1E1E22)),
        ) {
            AsyncImage(
                model = item.thumbnailUrl?.highQualityThumbnail(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.width(18.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
            // Kind Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.ui_top_result_c5e5adab, kindLabel),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }

            Spacer(Modifier.height(5.dp))

            Text(
                text = item.title,
                color = Color.White,
                fontSize = 18.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(16.dp))

        // Play / Open action button
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.play),
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

// ── Search Song Row ────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvSearchSongRow(
    track: MusicTrack,
    index: Int,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val isCurrent = MusicPlayerState.currentTrack?.id == track.id
    val isPlaying = isCurrent && MusicPlayerState.isPlaying

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .tvFocusable(
                shape = RoundedCornerShape(10.dp),
                scaleOnFocus = 1.02f,
                elevationOnFocus = 4.dp,
                interactionSource = interaction,
                onClick = onPlay,
            )
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Track number / Equalizer
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isPlaying) {
                AnimatedEqualizer(isPlaying = true, color = AppleMusicRed, size = 16.dp)
            } else {
                Text(
                    text = stringResource(R.string.ui_text_c79f71243f, index),
                    color = if (isCurrent) AppleMusicRed else Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Track Artwork
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF1E1E22)),
        ) {
            AsyncImage(
                model = track.thumbnailUrl?.highQualityThumbnail(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.width(14.dp))

        // Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    color = if (isCurrent) AppleMusicRed else Color.White,
                    fontSize = 14.5.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.isExplicit) {
                    Spacer(Modifier.width(6.dp))
                    ExplicitBadge(color = Color.White.copy(alpha = 0.70f))
                }
            }
            Text(
                text = track.artists,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                fontFamily = SFProDisplay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Duration
        if (track.duration.isNotBlank()) {
            Text(
                text = track.duration,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                fontFamily = SFProDisplay,
            )
        }
    }
}

// ── Search Artist Card (Circular) ──────────────────────────────────────────────────────────────

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
            .width(136.dp)
            .tvFocusable(
                shape = CircleShape,
                scaleOnFocus = 1.08f,
                elevationOnFocus = 12.dp,
                interactionSource = interaction,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(126.dp)
                .shadow(if (isFocused) 16.dp else 4.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
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
            fontSize = 13.5.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Search Shelf Item Card (Albums & Playlists, 136.dp square) ──────────────────────────────────

@Composable
private fun TvSearchShelfItemCard(
    title: String,
    subtitle: String,
    thumb: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .width(136.dp)
            .tvFocusable(
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.06f,
                elevationOnFocus = 12.dp,
                interactionSource = interaction,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E22))
                .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
        ) {
            AsyncImage(
                model = thumb.highQualityThumbnail(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontFamily = SFProDisplay,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Filter Pill Switch ─────────────────────────────────────────────────────────────────────────

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
            .height(32.dp)
            .tvFocusable(
                shape = RoundedCornerShape(16.dp),
                scaleOnFocus = 1.06f,
                elevationOnFocus = 0.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
