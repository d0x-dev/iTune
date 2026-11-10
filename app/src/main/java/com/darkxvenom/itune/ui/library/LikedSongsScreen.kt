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

