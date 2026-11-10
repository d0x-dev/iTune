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
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.formatMusicDuration
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.model.SearchCategory
import com.darkxvenom.itune.data.repository.MusicRepository
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onCategoryClick: (SearchCategory) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(MusicPlayerState.pendingArtistSearch) {
        val artistQuery = MusicPlayerState.pendingArtistSearch
        if (!artistQuery.isNullOrBlank()) {
            searchQuery = artistQuery
            MusicPlayerState.pendingArtistSearch = null
        }
    }

    // Live search query debounced by 350ms to match iTube backend search
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(350)
        try {
            val results = MusicBackend.searchMusic(trimmed)
            searchResults = results
        } catch (_: Exception) {
            searchResults = emptyList()
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
        // Main Grid with spring overscroll physics containing BOTH the Header and the Content
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .springOverscroll(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Large Title "Search" + Profile Avatar + Search Bar
            // Attached inside grid so pulling/scrolling bounces the entire screen together
            item(span = { GridItemSpan(2) }, key = "search_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(vertical = 8.dp)
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

                    // Apple Music Search Input Field
                    AppleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onMicClick = {
                            Toast.makeText(context, "Voice Search listening...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // CONTENT: Categories when query is empty, live YouTube Music results when typed
            if (searchQuery.isBlank()) {
                items(MusicRepository.searchCategories, key = { it.id }) { category ->
                    SearchCategoryCardItem(
                        category = category,
                        onClick = {
                            onCategoryClick(category)
                            searchQuery = category.title
                        }
                    )
                }
            } else {
                if (isSearching && searchResults.isEmpty()) {
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
                } else if (!isSearching && searchResults.isEmpty()) {
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
                    items(searchResults, key = { it.id }, span = { GridItemSpan(2) }) { track ->
                        SearchResultItem(
                            track = track,
                            isCurrent = MusicPlayerState.currentTrack?.id == track.id,
                            isPlaying = MusicPlayerState.isPlaying,
                            onClick = {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                MusicPlayerState.play(track, searchResults)
                            }
                        )
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
    }
}

/**
 * Apple Music styled search result item with high quality thumbnail,
 * red playing title highlight, animated equalizer, and formatted metadata.
 */
@Composable
private fun SearchResultItem(
    track: MusicTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with rounded corners and playing indicator
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2C2C2E))
        ) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl)
                    .memoryCacheKey("${track.id}_search_thumb_54")
                    .crossfade(200)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedEqualizer(
                        isPlaying = isPlaying,
                        color = Color.White,
                        size = 24.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title and artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = if (isCurrent) Color(0xFFFA2D48) else AppleTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Apple Music styled "Music" badge
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF2C2C2E),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        text = "Music",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp
                        ),
                        color = AppleTextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = track.artists + if (track.durationSec > 0) " • ${formatMusicDuration(track.durationSec)}" else "",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = AppleTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AppleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
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
                if (query.isEmpty()) {
                    Text(
                        text = "Artists, Songs, Lyrics & More",
                        color = AppleTextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.2).sp
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        if (category.imageUrl != null) {
            AsyncImage(
                model = category.imageUrl,
                contentDescription = category.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CategoryCardArtwork(
                type = category.type,
                modifier = Modifier.fillMaxSize()
            )

            Text(
                text = category.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 12.dp)
            )
        }
    }
}
