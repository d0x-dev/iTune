package com.darkxvenom.itune.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.LibraryDestination
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.model.HEADER_ART_PX
import com.darkxvenom.itune.data.model.ROW_ART_PX
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.data.model.toArtistSong
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.ui.artist.components.SongRow
import com.darkxvenom.itune.ui.artist.components.lightweightLiquidGlass
import com.darkxvenom.itune.ui.artist.components.liquidGlass
import com.darkxvenom.itune.ui.artist.components.menuAnchor
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.icons.SfIcons

@Composable
fun LikedSongsScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
) {
    val likedMeta by LibraryStore.likedSongs.collectAsStateWithLifecycle()
    val recentMeta by LibraryStore.recentSongs.collectAsStateWithLifecycle()
    val query = controller.searchQuery
    val sort = controller.songSort

    val sortedSongs = remember(likedMeta, sort) {
        sortLibrarySongs(likedMeta, sort).map { it.toSong() }
    }
    val visibleSongs = remember(sortedSongs, query) {
        if (query.isBlank()) {
            sortedSongs
        } else {
            sortedSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    song.albumName?.contains(query, ignoreCase = true) == true
            }
        }
    }
    val artworkUrls = remember(likedMeta) {
        likedMeta.mapNotNull { it.thumbnailUrl.ifBlank { null } }.distinct().take(4)
    }
    val totalMinutes = remember(likedMeta) {
        (likedMeta.sumOf { it.durationSec } / 60L).toInt()
    }

    val listState = controller.listStates.getOrPut(LibraryDestination.LIKED_SONGS) { LazyListState() }
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 280
        }
    }

    val currentTrack = MusicPlayerState.currentTrack
    val currentSongId = currentTrack?.id
    val isPlaying = MusicPlayerState.isPlaying

    LibraryFrostedScreenScaffold(
        controller = controller,
        artworkUrls = artworkUrls,
        scrolledTitle = "Liked Songs",
        isScrolled = isScrolled,
        hasMiniPlayer = hasMiniPlayer,
        fallbackAccent = LibraryCrimsonAccent,
        onBack = onBack,
        topBarActions = {
            LibraryTopBarActionButtons(
                controller = controller,
                showSort = likedMeta.isNotEmpty(),
                onMoreClick = {
                    controller.openTopMenu(
                        LibraryTopMenuRequest(
                            destination = LibraryDestination.LIKED_SONGS,
                            songs = visibleSongs.ifEmpty { sortedSongs },
                            stationLabel = "Liked Songs",
                            showSort = likedMeta.isNotEmpty(),
                        ),
                    )
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "liked_hero") {
                LikedSongsHeroSection(
                    artworkUrls = artworkUrls,
                    songCount = likedMeta.size,
                    totalMinutes = totalMinutes,
                    sortLabel = sort.label,
                    hasSongs = visibleSongs.isNotEmpty(),
                    onSortClick = controller::openSortMenu,
                    onPlay = { controller.playSongs(visibleSongs, 0) },
                    onShuffle = { controller.playSongs(visibleSongs, 0, shuffle = true) },
                    onStartRadio = {
                        controller.startRadioFromSongs(
                            songs = visibleSongs.ifEmpty { sortedSongs },
                            stationLabel = "Liked Songs",
                        )
                    },
                )
            }

            item(key = "liked_filter") {
                LibraryFilterBar(
                    visible = controller.searchOpen,
                    query = query,
                    onQueryChange = { controller.searchQuery = it },
                    onClose = controller::toggleSearch,
                    autoFocus = controller.focusSearchNext,
                    onFocused = { controller.focusSearchNext = false },
                    placeholder = "Filter liked songs, artists...",
                )
            }

            if (likedMeta.isEmpty()) {
                item(key = "liked_empty") {
                    Spacer(Modifier.height(14.dp))
                    val fallbackRecent = remember(recentMeta) { recentMeta.map { it.toSong() } }
                    LibraryGlassEmptyCard(
                        icon = SfIcons.StarFill,
                        title = "No Liked Songs Yet",
                        subtitle = "Tap the star on any track in Search, Artist pages, or the Player to collect your favourites in Liquid Glass.",
                        accent = LibraryCrimsonAccent,
                        actionLabel = if (fallbackRecent.isNotEmpty()) "Play Recent Tracks" else "Explore Music",
                        onAction = {
                            if (fallbackRecent.isNotEmpty()) {
                                controller.playSongs(fallbackRecent, 0)
                            } else {
                                onExploreClick()
                            }
                        },
                    )
                }
            } else if (visibleSongs.isEmpty()) {
                item(key = "liked_no_matches") {
                    Spacer(Modifier.height(14.dp))
                    LibraryGlassEmptyCard(
                        icon = ITubeIcons.Search,
                        title = "No Matches",
                        subtitle = "No liked songs matched \"$query\".",
                        accent = LibraryCrimsonAccent,
                        actionLabel = "Clear Filter",
                        onAction = { controller.searchQuery = "" },
                    )
                }
            } else {
                item(key = "liked_list_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (query.isBlank()) "Tracks" else "Matches (${visibleSongs.size})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .lightweightLiquidGlass(
                                    shape = CircleShape,
                                    fallbackColor = Color(0x33FFFFFF),
                                )
                                .menuAnchor()
                                .clickable { controller.openSortMenu() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = sort.label,
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = visibleSongs,
                    key = { _, s -> s.videoId },
                ) { index, song ->
                    val isCurrent = song.videoId == currentSongId
                    SongRow(
                        song = song,
                        onClick = { controller.playSongs(visibleSongs, index) },
                        onLongPress = { controller.openSongMenu(song) },
                        onMore = { controller.openSongMenu(song) },
                        onSwipeToQueue = {
                            MusicPlayerState.playNext(song.toMusicTrack())
                            controller.showToast("Playing \"${song.title}\" next")
                        },
                        rowBackground = Color.Transparent,
                        subtitleColor = Color.White.copy(alpha = 0.64f),
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                        activeTint = LibraryCrimsonAccent,
                        searchPlayingStyle = true,
                    )
                    if (index < visibleSongs.lastIndex) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedSongsHeroSection(
    artworkUrls: List<String>,
    songCount: Int,
    totalMinutes: Int,
    sortLabel: String,
    hasSongs: Boolean,
    onSortClick: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Floating Frosted Liquid Glass Sleeve / Mosaic
        Box(
            modifier = Modifier
                .size(204.dp)
                .shadow(28.dp, RoundedCornerShape(26.dp), spotColor = LibraryCrimsonAccent.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF4C0D1E),
                            Color(0xFF1B101C),
                            Color(0xFF0E0E14),
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp)),
        ) {
            when {
                artworkUrls.size >= 4 -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            MosaicTile(artworkUrls[0], Modifier.weight(1f).fillMaxSize())
                            MosaicTile(artworkUrls[1], Modifier.weight(1f).fillMaxSize())
                        }
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            MosaicTile(artworkUrls[2], Modifier.weight(1f).fillMaxSize())
                            MosaicTile(artworkUrls[3], Modifier.weight(1f).fillMaxSize())
                        }
                    }
                }
                artworkUrls.isNotEmpty() -> {
                    AsyncImage(
                        model = artworkUrls.first().artworkAt(HEADER_ART_PX),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = SfIcons.StarFill,
                            contentDescription = null,
                            tint = LibraryCrimsonAccent,
                            modifier = Modifier.size(74.dp),
                        )
                    }
                }
            }

            // Frosted specular sheen + crimson star badge in corner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.14f),
                            0.35f to Color.Transparent,
                            0.80f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.42f),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .lightweightLiquidGlass(CircleShape, fallbackColor = Color(0xAA16161C))
                    .border(0.8.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = SfIcons.StarFill,
                    contentDescription = null,
                    tint = LibraryCrimsonAccent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "FAVORITES",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Liked Songs",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        val countPart = if (songCount == 1) "1 Song" else "$songCount Songs"
        val durationPart = if (totalMinutes > 0) " • $totalMinutes min" else ""
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "$countPart$durationPart",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            if (songCount > 0) {
                Text(
                    text = " • $sortLabel",
                    color = LibraryCrimsonAccent.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .menuAnchor()
                        .clickable { onSortClick() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Liquid Glass Action Deck: Play, Shuffle & Start Radio
        LibraryGlassCardContainer(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            accentTint = LibraryCrimsonAccent,
            useBackdropGlass = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LibraryGlassPillButton(
                        icon = ITubeIcons.Play,
                        label = stringResource(R.string.play),
                        onClick = onPlay,
                        filled = true,
                        enabled = hasSongs,
                        height = 48.dp,
                        modifier = Modifier.weight(1f),
                    )
                    LibraryGlassPillButton(
                        icon = ITubeIcons.Shuffle,
                        label = stringResource(R.string.shuffle),
                        onClick = onShuffle,
                        filled = false,
                        enabled = hasSongs,
                        height = 48.dp,
                        modifier = Modifier.weight(1f),
                    )
                }

                StartRadioLiquidButton(
                    enabled = hasSongs,
                    onClick = onStartRadio,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

