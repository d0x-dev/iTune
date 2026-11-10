package com.darkxvenom.itune.ui.library

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import coil.compose.AsyncImage
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.NewReleaseAlbum
import com.darkxvenom.itune.backend.ReleaseType
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.NewReleasesRepository
import com.darkxvenom.itune.data.library.SavedCollectionEntry
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.LibraryMenuItem
import com.darkxvenom.itune.data.model.LibraryPinItem
import com.darkxvenom.itune.data.model.LibraryRecentAlbum
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.data.repository.MusicRepository
import com.darkxvenom.itune.ui.artist.components.CoverPlaceholder
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.artist.components.ShimmerBox
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.TopSpringLoadingBar
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.delay

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onPinClick: (LibraryPinItem) -> Unit = {},
    onFavoriteArtistClick: (SavedCollectionEntry) -> Unit = {},
    onMenuItemClick: (LibraryMenuItem) -> Unit = {},
    onRecentAlbumClick: (LibraryRecentAlbum) -> Unit = {},
    onRecentlyAddedClick: () -> Unit = {},
    onNewReleaseClick: (NewReleaseAlbum) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val overscrollState = rememberSpringOverscrollState()
    var isRefreshing by remember { mutableStateOf(false) }
    val likedArtists by LibraryStore.likedArtists.collectAsStateWithLifecycle()
    val topArtists = remember(likedArtists) { likedArtists.take(6) }
    val newReleases by NewReleasesRepository.releases.collectAsStateWithLifecycle()
    val releasesLoading by NewReleasesRepository.isLoading.collectAsStateWithLifecycle()
    val recentlyAddedReleases = remember(newReleases) {
        val albumsOnly = newReleases.filter { it.releaseType == ReleaseType.ALBUM }
        (albumsOnly + newReleases).distinctBy { it.browseId }.take(6)
    }

    LaunchedEffect(Unit) {
        NewReleasesRepository.ensureLoaded()
    }

    // ONLY trigger loading when pulling down from the very top of the list (item 0)
    // Never trigger at the bottom/end of the screen
    LaunchedEffect(overscrollState.offset.value) {
        if (listState.firstVisibleItemIndex == 0 && overscrollState.offset.value > 25f && !isRefreshing) {
            isRefreshing = true
            NewReleasesRepository.refresh()
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(3500) // Keep loading beam active for ~3.5 seconds
            isRefreshing = false
        }
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }

    val topShadeAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "LibraryTopShadeAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .springOverscroll(overscrollState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp)
        ) {
            // Header: Large Title "Library" + Profile Avatar (NO 3-dot button)
            item(key = "library_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Library",
                            color = AppleTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )

                        // Profile Icon Only (No 3-dot button)
                        AppleMemojiAvatar(
                            size = 38.dp,
                            onClick = onProfileClick
                        )
                    }
                }
            }

            // Favorite Artists (max 6): 3 Columns x up to 2 Rows in the same rounded-square tile UI
            if (topArtists.isNotEmpty()) {
                item(key = "library_pins_grid") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        topArtists.chunked(3).forEach { rowArtists ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowArtists.forEach { artist ->
                                    LibraryFavoriteArtistCard(
                                        artist = artist,
                                        onClick = { onFavoriteArtistClick(artist) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowArtists.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item(key = "spacer_menu") {
                    Spacer(modifier = Modifier.height(26.dp))
                }
            }

            // Navigation Menu List: Playlists, Artists, Albums, Songs
            // With pure WHITE icons in dark mode as requested
            item(key = "library_nav_list") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val menuItems = MusicRepository.libraryMenuItems
                    val likedSongs by LibraryStore.likedSongs.collectAsStateWithLifecycle()
                    menuItems.forEachIndexed { index, item ->
                        LibraryMenuRow(
                            item = item,
                            onClick = {
                                onMenuItemClick(item)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 44.dp),
                            color = Color(0x1FFFFFFF),
                            thickness = 0.5.dp
                        )
                    }
                    LibraryLikedSongsMenuRow(
                        count = likedSongs.size,
                        onClick = {
                            onMenuItemClick(
                                LibraryMenuItem(
                                    id = "lm5",
                                    title = "Liked Songs",
                                    iconRes = R.drawable.ic_lib_songs,
                                ),
                            )
                        },
                    )
                }
            }

            item(key = "spacer_recent") {
                Spacer(modifier = Modifier.height(30.dp))
            }

            // "Recently Added" Section Header
            item(key = "recently_added_header") {
                Text(
                    text = "Recently Added",
                    color = AppleTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }

            // Recently Added 2-Column Grid
            item(key = "recently_added_grid") {
                val albums = MusicRepository.libraryRecentAlbums
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    albums.chunked(2).forEach { rowAlbums ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            rowAlbums.forEach { album ->
                                LibraryRecentAlbumCard(
                                    album = album,
                                    onClick = {
                                        onRecentAlbumClick(album)
                                        Toast.makeText(context, "Playing ${album.title}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowAlbums.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Top Status Bar Area Black Blur Shade Scrim: Only appears when scrolling down
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
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

        // Sleek Left-to-Right Animated Loading Line: ONLY visible when at the top (never at the bottom)
        TopSpringLoadingBar(
            isVisible = isRefreshing && listState.firstVisibleItemIndex == 0,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun LibraryFavoriteArtistCard(
    artist: SavedCollectionEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF1C1C1E))
        ) {
            if (artist.thumbnailUrl.isNullOrBlank()) {
                CoverPlaceholder(
                    icon = Icons.Rounded.Person,
                    base = Color(0xFF1C1C1E),
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = artist.thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = artist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.title,
            color = AppleTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LibraryMenuRow(
    item: LibraryMenuItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon color WHITE in dark mode as requested
        Icon(
            painter = painterResource(id = item.iconRes),
            contentDescription = item.title,
            tint = Color.White,
            modifier = Modifier.size(23.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = item.title,
            color = AppleTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.2).sp,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open",
            tint = Color(0x66FFFFFF),
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
private fun LibraryRecentAlbumCard(
    album: LibraryRecentAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        AsyncImage(
            model = album.imageUrl,
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1E))
        )

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = album.title,
            color = AppleTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = album.artist,
            color = AppleTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
