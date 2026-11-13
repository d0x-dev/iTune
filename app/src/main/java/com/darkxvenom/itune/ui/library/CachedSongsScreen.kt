package com.darkxvenom.itune.ui.library

import android.widget.Toast
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.CachedMusicTrack
import com.darkxvenom.itune.backend.MusicCacheManager
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.LibraryDestination
import com.darkxvenom.itune.data.model.ArtistRef
import com.darkxvenom.itune.data.model.ROW_ART_PX
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.ui.artist.components.CoverPlaceholder
import com.darkxvenom.itune.ui.artist.components.SongRow
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.settings.AppleConfirmDialog

internal val LibraryIndigoAccent = Color(0xFF007AFF)

private fun CachedMusicTrack.toSong(): Song {
    val durText = if (durationSec > 0L) {
        val m = durationSec / 60L
        val s = durationSec % 60L
        "$m:${s.toString().padStart(2, '0')}"
    } else null
    val refs = if (!artistId.isNullOrBlank()) {
        listOf(ArtistRef(artists.substringBefore(",").trim().ifBlank { artists }, artistId))
    } else emptyList()
    return Song(
        videoId = id,
        title = title,
        artist = artists,
        thumbnailUrl = resolvedArtworkUri() ?: thumbnailUrl,
        durationText = durText,
        artistId = artistId,
        artists = refs,
        albumName = album,
        albumId = albumId,
        localPath = audioFilePath,
    )
}

/**
 * Dedicated Cached & Offline Music Screen.
 * Fully styled to match Apple Music Playlists with:
 * - 4-tile artwork collage
 * - Play All & Shuffle Play actions with complete offline queue generation
 * - Per-track offline indicator and file size badge
 * - Search filter bar and long-press / 3-dot options menu
 */
@Composable
fun CachedSongsScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val cachedTracks = MusicCacheManager.cachedTracks
    val totalBytes = MusicCacheManager.totalStorageBytes.longValue
    val maxLimitMB = MusicCacheManager.maxCacheLimitMB.longValue
    val query = controller.searchQuery

    var showClearDialog by remember { mutableStateOf(false) }

    val allSongs = remember(cachedTracks.toList()) {
        cachedTracks.map { it.toSong() }
    }

    val visibleSongs = remember(allSongs, query) {
        if (query.isBlank()) {
            allSongs
        } else {
            allSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    (song.albumName?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    val artworkUrls = remember(cachedTracks.toList()) {
        cachedTracks.mapNotNull { it.resolvedArtworkUri()?.takeIf { u -> u.isNotBlank() } }.distinct().take(4)
    }

    val listState = controller.listStates.getOrPut(LibraryDestination.CACHED_SONGS) { LazyListState() }
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
        scrolledTitle = "Downloaded & Cached",
        isScrolled = isScrolled,
        hasMiniPlayer = hasMiniPlayer,
        fallbackAccent = LibraryIndigoAccent,
        onBack = onBack,
        topBarActions = {
            if (cachedTracks.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Clear Cache",
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
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
            item(key = "cached_hero") {
                CachedSongsHeroSection(
                    artworkUrls = artworkUrls,
                    songCount = cachedTracks.size,
                    totalStorageSize = MusicCacheManager.formatStorageSize(totalBytes),
                    limitText = if (maxLimitMB > 0) "Limit: ${maxLimitMB}MB" else "Unlimited",
                    hasSongs = visibleSongs.isNotEmpty(),
                    onPlay = { controller.playSongs(visibleSongs, 0) },
                    onShuffle = { controller.playSongs(visibleSongs, 0, shuffle = true) },
                )
            }

            item(key = "cached_filter") {
                LibraryFilterBar(
                    visible = controller.searchOpen,
                    query = query,
                    onQueryChange = { controller.searchQuery = it },
                    onClose = controller::toggleSearch,
                    autoFocus = controller.focusSearchNext,
                    onFocused = { controller.focusSearchNext = false },
                    placeholder = "Filter cached songs, artists...",
                )
            }

            if (cachedTracks.isEmpty()) {
                item(key = "cached_empty") {
                    Spacer(Modifier.height(14.dp))
                    LibraryGlassEmptyCard(
                        icon = SfIcons.Download,
                        title = "No Cached Songs Yet",
                        subtitle = "Songs you stream in iTune are automatically cached here with high-resolution artwork for 100% offline playback.",
                        accent = LibraryIndigoAccent,
                        actionLabel = "Explore Music",
                        onAction = onExploreClick,
                    )
                }
            } else if (visibleSongs.isEmpty()) {
                item(key = "cached_no_matches") {
                    Spacer(Modifier.height(14.dp))
                    LibraryGlassEmptyCard(
                        icon = ITubeIcons.Search,
                        title = "No Matches",
                        subtitle = "No cached songs matched \"$query\".",
                        accent = LibraryIndigoAccent,
                        actionLabel = "Clear Filter",
                        onAction = { controller.searchQuery = "" },
                    )
                }
            } else {
                item(key = "cached_list_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (query.isBlank()) "Cached Tracks" else "Matches (${visibleSongs.size})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${visibleSongs.size} tracks • ${MusicCacheManager.formatStorageSize(totalBytes)}",
                            color = Color.White.copy(alpha = 0.52f),
                            fontSize = 12.sp,
                        )
                    }
                }

                itemsIndexed(
                    items = visibleSongs,
                    key = { _, song -> song.videoId },
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
                    )

                    if (index < visibleSongs.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AppleConfirmDialog(
            title = "Clear Cached Songs?",
            message = "This will permanently remove all ${cachedTracks.size} offline audio songs (${MusicCacheManager.formatStorageSize(totalBytes)}) from your device storage.",
            confirmLabel = "Clear All",
            onDismiss = { showClearDialog = false },
            onConfirm = {
                MusicCacheManager.clearAllCache()
                showClearDialog = false
                Toast.makeText(context, "Cached music cleared", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun CachedSongsHeroSection(
    artworkUrls: List<String>,
    songCount: Int,
    totalStorageSize: String,
    limitText: String,
    hasSongs: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hero Collage Avatar (2x2 artwork mosaic)
        Box(
            modifier = Modifier
                .size(200.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = LibraryIndigoAccent.copy(alpha = 0.35f),
                    spotColor = LibraryIndigoAccent.copy(alpha = 0.45f),
                )
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF1A1A22)),
        ) {
            if (artworkUrls.isEmpty()) {
                CoverPlaceholder(
                    icon = SfIcons.Download,
                    base = Color(0xFF1A1A22),
                    tint = LibraryIndigoAccent,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (artworkUrls.size == 1) {
                AsyncImage(
                    model = artworkUrls[0].artworkAt(ROW_ART_PX * 2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f)) {
                        AsyncImage(
                            model = artworkUrls.getOrNull(0)?.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                        AsyncImage(
                            model = artworkUrls.getOrNull(1)?.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                    Row(Modifier.weight(1f)) {
                        AsyncImage(
                            model = artworkUrls.getOrNull(2)?.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                        AsyncImage(
                            model = artworkUrls.getOrNull(3)?.artworkAt(ROW_ART_PX),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }

            // Offline Pin Badge in bottom right of avatar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC007AFF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SfIcons.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Downloaded & Cached",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "$songCount songs • $totalStorageSize • $limitText",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        // Play & Shuffle Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (hasSongs) {
                        haptics.play(Haptic.Resume)
                        onPlay()
                    }
                },
                enabled = hasSongs,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.25f),
                    disabledContentColor = Color.White.copy(alpha = 0.40f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Play",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            FilledTonalButton(
                onClick = {
                    if (hasSongs) {
                        haptics.play(Haptic.Select)
                        onShuffle()
                    }
                },
                enabled = hasSongs,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFF2C2C2E),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF1C1C1E),
                    disabledContentColor = Color.White.copy(alpha = 0.35f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Shuffle",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
