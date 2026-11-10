package com.darkxvenom.itune.ui.search

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.UnifiedSearchResults
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.data.model.SearchCategory
import com.darkxvenom.itune.data.repository.MusicRepository
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onCategoryClick: (SearchCategory) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(UnifiedSearchResults()) }
    var isSearching by remember { mutableStateOf(false) }
    var moreRequest by remember { mutableStateOf<SearchMoreRequest?>(null) }

    fun openCollectionDetail(state: SearchDetailState) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        MusicPlayerState.pendingArtistNav = PendingArtistNav(
            browseId = state.browseId,
            name = state.title,
            subtitle = state.subtitle,
            thumbnailUrl = state.thumbnailUrl,
            initialType = state.initialType,
        )
    }

    LaunchedEffect(MusicPlayerState.pendingArtistSearch) {
        val artistQuery = MusicPlayerState.pendingArtistSearch
        if (!artistQuery.isNullOrBlank()) {
            searchQuery = artistQuery
            MusicPlayerState.pendingArtistSearch = null
        }
    }

    // Live unified search (Songs, Artists, Albums, Playlists) debounced by 350ms
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            searchResults = UnifiedSearchResults()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(350)
        try {
            searchResults = MusicBackend.searchAllCategories(trimmed)
        } catch (_: Exception) {
            searchResults = UnifiedSearchResults()
        } finally {
            isSearching = false
        }
    }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 4
        }
    }

    val topShadeAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "SearchTopShadeAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack)
    ) {
        // Unified Persistent Grid preserving SearchBar keyboard focus across empty -> typed transitions
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .springOverscroll(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(span = { GridItemSpan(2) }, key = "search_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Search",
                            color = AppleTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )

                        AppleMemojiAvatar(
                            size = 38.dp,
                            onClick = onProfileClick
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AppleSearchBar(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                        },
                        onMicClick = {
                            Toast.makeText(context, "Voice Search listening...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            if (searchQuery.isBlank()) {
                itemsIndexed(
                    items = MusicRepository.searchCategories,
                    key = { _, category -> category.id }
                ) { index, category ->
                    val horizontalPad = if (index % 2 == 0) {
                        PaddingValues(start = 16.dp, top = if (index < 2) 6.dp else 0.dp, bottom = 12.dp)
                    } else {
                        PaddingValues(end = 16.dp, top = if (index < 2) 6.dp else 0.dp, bottom = 12.dp)
                    }
                    SearchCategoryCardItem(
                        category = category,
                        modifier = Modifier.padding(horizontalPad),
                        onClick = {
                            onCategoryClick(category)
                            searchQuery = category.title
                        }
                    )
                }
            } else {
                if (isSearching && searchResults.isEmpty) {
                    item(span = { GridItemSpan(2) }, key = "search_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Color(0xFFFA2D48),
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Searching YouTube Music...",
                                    color = AppleTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else if (!isSearching && searchResults.isEmpty) {
                    item(span = { GridItemSpan(2) }, key = "search_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results found for \"$searchQuery\"",
                                color = AppleTextSecondary,
                                fontSize = 15.sp
                            )
                        }
                    }
                } else {
                    val songsList = searchResults.songs.take(8)
                    val artistsList = searchResults.artists.take(6)
                    val albumsList = searchResults.albums.take(6)
                    val playlistsList = searchResults.playlists.take(6)

                    if (songsList.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }, key = "header_songs") {
                            SearchSectionHeader(title = "Songs")
                        }
                        itemsIndexed(
                            items = songsList,
                            key = { idx, t -> "song_${t.id}_$idx" },
                            span = { _, _ -> GridItemSpan(2) }
                        ) { _, track ->
                            SearchUnifiedRow(
                                title = track.title,
                                subtitle = track.artists,
                                thumbnailUrl = track.thumbnailUrl,
                                cacheKey = "${track.id}_search_song_48",
                                isCurrent = MusicPlayerState.currentTrack?.id == track.id,
                                isPlaying = MusicPlayerState.isPlaying,
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                    MusicPlayerState.play(track, songsList)
                                },
                                onMoreClick = { pos -> moreRequest = SearchMoreRequest(SearchMoreTarget.Song(track), pos) }
                            )
                        }
                    }

                    if (artistsList.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }, key = "header_artists") {
                            SearchSectionHeader(title = "Artists")
                        }
                        itemsIndexed(
                            items = artistsList,
                            key = { idx, a -> "artist_${a.id}_$idx" },
                            span = { _, _ -> GridItemSpan(2) }
                        ) { _, artist ->
                            SearchUnifiedRow(
                                title = artist.name,
                                subtitle = artist.subscribers ?: "Artist",
                                thumbnailUrl = artist.thumbnailUrl,
                                cacheKey = "${artist.id}_search_artist_48",
                                onClick = {
                                    openCollectionDetail(
                                        SearchDetailState(
                                            browseId = artist.id,
                                            title = artist.name,
                                            subtitle = artist.subscribers ?: "Artist",
                                            thumbnailUrl = artist.thumbnailUrl,
                                            initialType = BrowseType.ARTIST
                                        )
                                    )
                                },
                                onMoreClick = { pos -> moreRequest = SearchMoreRequest(SearchMoreTarget.Artist(artist), pos) }
                            )
                        }
                    }

                    if (albumsList.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }, key = "header_albums") {
                            SearchSectionHeader(title = "Albums")
                        }
                        itemsIndexed(
                            items = albumsList,
                            key = { idx, a -> "album_${a.id}_$idx" },
                            span = { _, _ -> GridItemSpan(2) }
                        ) { _, album ->
                            SearchUnifiedRow(
                                title = album.title,
                                subtitle = album.author,
                                thumbnailUrl = album.thumbnailUrl,
                                cacheKey = "${album.id}_search_album_48",
                                onClick = {
                                    openCollectionDetail(
                                        SearchDetailState(
                                            browseId = album.id,
                                            title = album.title,
                                            subtitle = album.author,
                                            thumbnailUrl = album.thumbnailUrl,
                                            initialType = BrowseType.ALBUM
                                        )
                                    )
                                },
                                onMoreClick = { pos -> moreRequest = SearchMoreRequest(SearchMoreTarget.Collection(album), pos) }
                            )
                        }
                    }

                    if (playlistsList.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }, key = "header_playlists") {
                            SearchSectionHeader(title = "Playlists")
                        }
                        itemsIndexed(
                            items = playlistsList,
                            key = { idx, p -> "playlist_${p.id}_$idx" },
                            span = { _, _ -> GridItemSpan(2) }
                        ) { idx, playlist ->
                            SearchUnifiedRow(
                                title = playlist.title,
                                subtitle = playlist.author,
                                thumbnailUrl = playlist.thumbnailUrl,
                                cacheKey = "${playlist.id}_search_playlist_48",
                                showBottomDivider = idx < playlistsList.lastIndex,
                                onClick = {
                                    openCollectionDetail(
                                        SearchDetailState(
                                            browseId = playlist.id,
                                            title = playlist.title,
                                            subtitle = playlist.author,
                                            thumbnailUrl = playlist.thumbnailUrl,
                                            initialType = BrowseType.PLAYLIST
                                        )
                                    )
                                },
                                onMoreClick = { pos -> moreRequest = SearchMoreRequest(SearchMoreTarget.Collection(playlist), pos) }
                            )
                        }
                    }
                }
            }
        }

        // Top subtle gradient shade on scroll for status bar transparency
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .graphicsLayer { alpha = topShadeAlpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppleOledBlack,
                            AppleOledBlack.copy(alpha = 0.94f),
                            AppleOledBlack.copy(alpha = 0.65f),
                            AppleOledBlack.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        AppleSearchMorePopup(
            request = moreRequest,
            onOpenDetail = ::openCollectionDetail,
            onPlayCollection = { browseId, title, _, thumb ->
                coroutineScope.launch {
                    val loaded = MusicBackend.loadCollectionTracks(
                        browseId = browseId,
                        fallbackArtist = title,
                        fallbackThumbnailUrl = thumb
                    )
                    if (loaded.isNotEmpty()) {
                        MusicPlayerState.play(loaded.first(), loaded)
                    } else {
                        Toast.makeText(context, "No tracks found", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onQueueCollection = { browseId, title, _, thumb ->
                coroutineScope.launch {
                    val loaded = MusicBackend.loadCollectionTracks(
                        browseId = browseId,
                        fallbackArtist = title,
                        fallbackThumbnailUrl = thumb
                    )
                    if (loaded.isNotEmpty()) {
                        MusicPlayerState.addToQueue(loaded)
                        Toast.makeText(context, "Added ${loaded.size} tracks to queue", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No tracks found", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { moreRequest = null }
        )
    }
}

@Composable
fun AppleSearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onFocused: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClearClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
                onFocused(true)
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = AppleTextSecondary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        text = "Artists, Songs, Lyrics & More",
                        color = AppleTextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(Color(0xFFFA2D48)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state -> onFocused(state.isFocused) }
                )
            }

            if (value.text.isNotEmpty()) {
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = AppleTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice search",
                        tint = AppleTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchCategoryCardItem(
    category: SearchCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.765f)
            .clip(RoundedCornerShape(14.dp))
            .background(category.backgroundColor)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        CategoryCardArtwork(
            type = category.type,
            modifier = Modifier.fillMaxSize()
        )

        if (!category.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = category.imageUrl,
                contentDescription = category.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.08f),
                            0.42f to category.backgroundColor.copy(alpha = 0.24f),
                            1.00f to Color.Black.copy(alpha = 0.78f),
                        )
                    )
            )
        }

        Text(
            text = category.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 10.dp, end = 12.dp)
        )
    }
}
