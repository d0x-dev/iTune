package com.darkxvenom.itune.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.LibraryDestination
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedCollectionEntry
import com.darkxvenom.itune.data.library.UserPlaylist
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.DetailPage
import com.darkxvenom.itune.data.model.LibraryState
import com.darkxvenom.itune.data.model.ROW_ART_PX
import com.darkxvenom.itune.data.model.UiState
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.data.model.toArtistSong
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.ui.artist.ITubeDetailScreen
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.CoverPlaceholder
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.LocalAppBackdrop
import com.darkxvenom.itune.ui.artist.components.LocalLiquidGlassEnabled
import com.darkxvenom.itune.ui.artist.components.LocalReduceDynamicBlur
import com.darkxvenom.itune.ui.artist.components.SongRow
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.layerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.snapshotBackdropSource
import com.darkxvenom.itune.ui.artist.components.isGlassSupported
import com.darkxvenom.itune.ui.artist.components.lightweightLiquidGlass
import com.darkxvenom.itune.ui.artist.components.longPressMenuClickable
import com.darkxvenom.itune.ui.artist.components.menuAnchor
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import dev.chrisbanes.haze.hazeSource

@Composable
fun LibraryScreenOverlayContent(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destination = controller.currentDestination ?: return

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 14 })
            },
            label = "library_destination_transition",
        ) { target ->
            when (target) {
                LibraryDestination.LIKED_SONGS -> LikedSongsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.PLAYLISTS -> LibraryPlaylistsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.ARTISTS -> LibraryArtistsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.ALBUMS -> LibraryAlbumsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.SONGS -> LibrarySongsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.USER_PLAYLIST -> UserPlaylistDetailScreen(
                    playlistId = controller.activeUserPlaylistId,
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.NEW_RELEASES -> AppleNewReleasesScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                )
            }
        }
    }
}

@Composable
fun LibraryPlaylistsScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
) {
    val userPlaylists by LibraryStore.userPlaylists.collectAsStateWithLifecycle()
    val playlists by LibraryStore.savedPlaylists.collectAsStateWithLifecycle()
    val likedSongs by LibraryStore.likedSongs.collectAsStateWithLifecycle()
    val query = controller.searchQuery

    val filteredUserPlaylists = remember(userPlaylists, query) {
        if (query.isBlank()) userPlaylists else userPlaylists.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }
    val filteredPlaylists = remember(playlists, query) {
        if (query.isBlank()) playlists else playlists.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true)
        }
    }
    val likedSongsList = remember(likedSongs) { likedSongs.map { it.toSong() } }
    val gridState = controller.gridStates.getOrPut(LibraryDestination.PLAYLISTS) { LazyGridState() }
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 120
        }
    }
    val totalCount = userPlaylists.size + playlists.size

    LibraryFrostedScreenScaffold(
        controller = controller,
        artworkUrls = emptyList(),
        scrolledTitle = "Playlists",
        isScrolled = isScrolled,
        hasMiniPlayer = hasMiniPlayer,
        fallbackAccent = LibraryCrimsonAccent,
        amoledDark = true,
        onBack = onBack,
        topBarActions = {
            LibraryTopBarActionButtons(
                controller = controller,
                showSort = false,
                onMoreClick = {
                    controller.openTopMenu(
                        LibraryTopMenuRequest(
                            destination = LibraryDestination.PLAYLISTS,
                            songs = likedSongsList,
                            stationLabel = "Playlists",
                            showSort = false,
                        ),
                    )
                },
            )
        },
    ) { contentPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(2) }, key = "playlists_header") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            LibraryScreenTitleHeader(
                                title = "Playlists",
                                countText = if (totalCount == 1) "1 Playlist" else "$totalCount Playlists",
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .lightweightLiquidGlass(
                                    shape = CircleShape,
                                    fallbackColor = Color(0xCC141418),
                                )
                                .border(0.8.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                                .menuAnchor()
                                .clickable { controller.openCreatePlaylistPopover() }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Create Playlist",
                                tint = LibraryCrimsonAccent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "New",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    LibraryFilterBar(
                        visible = controller.searchOpen,
                        query = query,
                        onQueryChange = { controller.searchQuery = it },
                        onClose = controller::toggleSearch,
                        autoFocus = controller.focusSearchNext,
                        onFocused = { controller.focusSearchNext = false },
                        placeholder = "Filter playlists...",
                    )
                }
            }

            if (filteredUserPlaylists.isNotEmpty()) {
                item(span = { GridItemSpan(2) }, key = "playlists_custom_label") {
                    Text(
                        text = "Your Playlists",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(filteredUserPlaylists, key = { it.id }) { playlist ->
                    UserPlaylistGlassCard(
                        playlist = playlist,
                        onClick = { controller.openUserPlaylist(playlist.id) },
                        onMenuClick = { controller.openUserPlaylistMenu(playlist) },
                    )
                }
            }

            if (filteredPlaylists.isNotEmpty()) {
                item(span = { GridItemSpan(2) }, key = "playlists_section_label") {
                    Text(
                        text = "Added Playlists",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                items(filteredPlaylists, key = { it.browseId }) { entry ->
                    LibraryCollectionGlassCard(
                        entry = entry,
                        placeholderIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                        onClick = {
                            controller.openDetail(
                                browseId = entry.browseId,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                thumbnailUrl = entry.thumbnailUrl,
                                type = entry.type,
                            )
                        },
                        onMenuClick = { controller.openCollectionMenu(entry) },
                    )
                }
            }

            if (userPlaylists.isEmpty() && playlists.isEmpty()) {
                item(span = { GridItemSpan(2) }, key = "playlists_empty") {
                    Spacer(Modifier.height(6.dp))
                    LibraryGlassEmptyCard(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        title = "No Playlists Yet",
                        subtitle = "Tap + New or the top-right ⋯ menu to create a custom playlist, or save playlists from Search.",
                        actionLabel = "Create Playlist",
                        actionModifier = Modifier.menuAnchor(),
                        onAction = { controller.openCreatePlaylistPopover() },
                    )
                }
            } else if (filteredUserPlaylists.isEmpty() && filteredPlaylists.isEmpty()) {
                item(span = { GridItemSpan(2) }, key = "playlists_no_match") {
                    LibraryGlassEmptyCard(
                        icon = ITubeIcons.Search,
                        title = "No Playlists Found",
                        subtitle = "No playlists matched \"$query\".",
                        actionLabel = "Clear Filter",
                        onAction = { controller.searchQuery = "" },
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryArtistsScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
) {
    val artists by LibraryStore.likedArtists.collectAsStateWithLifecycle()
    val query = controller.searchQuery

    val filteredArtists = remember(artists, query) {
        if (query.isBlank()) artists else artists.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true)
        }
    }
    val listState = controller.listStates.getOrPut(LibraryDestination.ARTISTS) { LazyListState() }
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
        }
    }

    LibraryFrostedScreenScaffold(
        controller = controller,
        artworkUrls = emptyList(),
        scrolledTitle = "Artists",
        isScrolled = isScrolled,
        hasMiniPlayer = hasMiniPlayer,
        fallbackAccent = Color(0xFF9C6BFF),
        amoledDark = true,
        onBack = onBack,
        topBarActions = {
            LibraryTopBarActionButtons(
                controller = controller,
                showSort = false,
                onMoreClick = {
                    controller.openTopMenu(
                        LibraryTopMenuRequest(
                            destination = LibraryDestination.ARTISTS,
                            songs = emptyList(),
                            stationLabel = "Artists",
                            showSort = false,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            item(key = "artists_header") {
                Column {
                    LibraryScreenTitleHeader(
                        title = "Artists",
                        countText = if (artists.size == 1) "1 Favorite Artist" else "${artists.size} Favorite Artists",
                    )
                    LibraryFilterBar(
                        visible = controller.searchOpen,
                        query = query,
                        onQueryChange = { controller.searchQuery = it },
                        onClose = controller::toggleSearch,
                        autoFocus = controller.focusSearchNext,
                        onFocused = { controller.focusSearchNext = false },
                        placeholder = "Filter favorite artists...",
                    )
                }
            }

            if (artists.isEmpty()) {
                item(key = "artists_empty") {
                    Spacer(Modifier.height(10.dp))
                    LibraryGlassEmptyCard(
                        icon = Icons.Rounded.Person,
                        title = "No Favorite Artists Yet",
                        subtitle = "Subscribe or star any artist from Search or an Artist page to build your frosted artist roster.",
                        accent = Color(0xFF9C6BFF),
                        actionLabel = "Explore Artists",
                        onAction = onExploreClick,
                    )
                }
            } else if (filteredArtists.isEmpty()) {
                item(key = "artists_no_match") {
                    LibraryGlassEmptyCard(
                        icon = ITubeIcons.Search,
                        title = "No Artists Found",
                        subtitle = "No favorite artists matched \"$query\".",
                        accent = Color(0xFF9C6BFF),
                        actionLabel = "Clear Filter",
                        onAction = { controller.searchQuery = "" },
                    )
                }
            } else {
                items(filteredArtists, key = { it.browseId }) { entry ->
                    LibraryArtistGlassRow(
                        entry = entry,
                        onClick = {
                            controller.openDetail(
                                browseId = entry.browseId,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                thumbnailUrl = entry.thumbnailUrl,
                                type = BrowseType.ARTIST,
                            )
                        },
                        onMenuClick = { controller.openCollectionMenu(entry) },
                        onStartRadio = { controller.startRadioForArtist(entry) },
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryAlbumsScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
) {
    val albums by LibraryStore.savedAlbums.collectAsStateWithLifecycle()
    val query = controller.searchQuery

    val filteredAlbums = remember(albums, query) {
        if (query.isBlank()) albums else albums.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true)
        }
    }
    val gridState = controller.gridStates.getOrPut(LibraryDestination.ALBUMS) { LazyGridState() }
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 120
        }
    }

    LibraryFrostedScreenScaffold(
        controller = controller,
        artworkUrls = emptyList(),
        scrolledTitle = "Albums",
        isScrolled = isScrolled,
        hasMiniPlayer = hasMiniPlayer,
        fallbackAccent = Color(0xFFFF6D42),
        amoledDark = true,
        onBack = onBack,
        topBarActions = {
            LibraryTopBarActionButtons(
                controller = controller,
                showSort = false,
                onMoreClick = {
                    controller.openTopMenu(
                        LibraryTopMenuRequest(
                            destination = LibraryDestination.ALBUMS,
                            songs = emptyList(),
                            stationLabel = "Albums",
                            showSort = false,
                        ),
                    )
                },
            )
        },
    ) { contentPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(2) }, key = "albums_header") {
                Column {
                    LibraryScreenTitleHeader(
                        title = "Albums",
                        countText = if (albums.size == 1) "1 Added Album" else "${albums.size} Added Albums",
                    )
                    LibraryFilterBar(
                        visible = controller.searchOpen,
                        query = query,
                        onQueryChange = { controller.searchQuery = it },
                        onClose = controller::toggleSearch,
                        autoFocus = controller.focusSearchNext,
                        onFocused = { controller.focusSearchNext = false },
                        placeholder = "Filter albums...",
                    )
                    if (filteredAlbums.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LibraryGlassPillButton(
                                icon = ITubeIcons.Play,
                                label = "Play Top Album",
                                onClick = {
                                    val first = filteredAlbums.first()
                                    controller.withCollectionSongs(first) { list ->
                                        controller.playSongs(list, 0)
                                    }
                                },
                                filled = true,
                                height = 44.dp,
                                modifier = Modifier.weight(1f),
                            )
                            LibraryGlassPillButton(
                                icon = ITubeIcons.Shuffle,
                                label = "Shuffle Pick",
                                onClick = {
                                    val pick = filteredAlbums.random()
                                    controller.withCollectionSongs(pick) { list ->
                                        controller.playSongs(list, 0, shuffle = true)
                                    }
                                },
                                filled = false,
                                height = 44.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (albums.isEmpty()) {
                item(span = { GridItemSpan(2) }, key = "albums_empty") {
                    Spacer(Modifier.height(10.dp))
                    LibraryGlassEmptyCard(
                        icon = Icons.Rounded.Album,
                        title = "No Added Albums Yet",
                        subtitle = "Save any album from Search or an Artist page with Add to Library and browse them in blurred Liquid Glass.",
                        accent = Color(0xFFFF6D42),
                        actionLabel = "Browse Albums",
                        onAction = onExploreClick,
                    )
                }
            } else if (filteredAlbums.isEmpty()) {
                item(span = { GridItemSpan(2) }, key = "albums_no_match") {
                    LibraryGlassEmptyCard(
                        icon = ITubeIcons.Search,
                        title = "No Albums Found",
                        subtitle = "No added albums matched \"$query\".",
                        accent = Color(0xFFFF6D42),
                        actionLabel = "Clear Filter",
                        onAction = { controller.searchQuery = "" },
                    )
                }
            } else {
                items(filteredAlbums, key = { it.browseId }) { entry ->
                    LibraryCollectionGlassCard(
                        entry = entry,
                        placeholderIcon = Icons.Rounded.Album,
                        onClick = {
                            controller.openDetail(
                                browseId = entry.browseId,
                                title = entry.title,
                                subtitle = entry.subtitle,
                                thumbnailUrl = entry.thumbnailUrl,
                                type = BrowseType.ALBUM,
                            )
                        },
                        onMenuClick = { controller.openCollectionMenu(entry) },
                    )
                }
            }
        }
    }
}

@Composable
fun LibrarySongsScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
) {
    val likedMeta by LibraryStore.likedSongs.collectAsStateWithLifecycle()
    val recentMeta by LibraryStore.recentSongs.collectAsStateWithLifecycle()
    val favoriteIds = MusicPlayerState.favoriteIds
    val query = controller.searchQuery
    val sort = controller.songSort

    val mergedMeta = remember(likedMeta, recentMeta) {
        val seen = LinkedHashSet<String>()
        buildList {
            likedMeta.forEach { if (seen.add(it.id)) add(it) }
            recentMeta.forEach { if (seen.add(it.id)) add(it) }
        }
    }
    val allSongs = remember(mergedMeta, sort) {
        sortLibrarySongs(mergedMeta, sort).map { it.toSong() }
    }
    val visibleSongs = remember(allSongs, query) {
        if (query.isBlank()) allSongs else allSongs.filter { s ->
            s.title.contains(query, ignoreCase = true) ||
                s.artist.contains(query, ignoreCase = true) ||
                s.albumName?.contains(query, ignoreCase = true) == true
        }
    }
    val backdropArts = remember(mergedMeta) {
        mergedMeta.mapNotNull { it.thumbnailUrl.ifBlank { null } }.distinct().take(4)
    }
    val listState = controller.listStates.getOrPut(LibraryDestination.SONGS) { LazyListState() }
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 140
        }
    }
    val currentSongId = MusicPlayerState.currentTrack?.id
    val isPlaying = MusicPlayerState.isPlaying

    LibraryFrostedScreenScaffold(
        controller = controller,
        artworkUrls = backdropArts,
        scrolledTitle = "Songs",
        isScrolled = isScrolled,
        hasMiniPlayer = hasMiniPlayer,
        fallbackAccent = LibraryCrimsonAccent,
        onBack = onBack,
        topBarActions = {
            LibraryTopBarActionButtons(
                controller = controller,
                showSort = allSongs.isNotEmpty(),
                onMoreClick = {
                    controller.openTopMenu(
                        LibraryTopMenuRequest(
                            destination = LibraryDestination.SONGS,
                            songs = visibleSongs.ifEmpty { allSongs },
                            stationLabel = "Library Songs",
                            showSort = allSongs.isNotEmpty(),
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
            item(key = "songs_header") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    LibraryScreenTitleHeader(
                        title = "Songs",
                        countText = "${allSongs.size} Library Tracks",
                    )
                    LibraryFilterBar(
                        visible = controller.searchOpen,
                        query = query,
                        onQueryChange = { controller.searchQuery = it },
                        onClose = controller::toggleSearch,
                        autoFocus = controller.focusSearchNext,
                        onFocused = { controller.focusSearchNext = false },
                        placeholder = "Filter library songs...",
                    )
                    if (visibleSongs.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LibraryGlassPillButton(
                                icon = ITubeIcons.Play,
                                label = stringResource(R.string.play),
                                onClick = { controller.playSongs(visibleSongs, 0) },
                                filled = true,
                                height = 46.dp,
                                modifier = Modifier.weight(1f),
                            )
                            LibraryGlassPillButton(
                                icon = ITubeIcons.Shuffle,
                                label = stringResource(R.string.shuffle),
                                onClick = { controller.playSongs(visibleSongs, 0, shuffle = true) },
                                filled = false,
                                height = 46.dp,
                                modifier = Modifier.weight(1f),
                            )
                            LibraryGlassPillButton(
                                icon = SfIcons.Radio,
                                label = "Radio",
                                onClick = { controller.startRadioFromSongs(visibleSongs, "Library Songs") },
                                filled = false,
                                accentTint = LibraryCrimsonAccent,
                                height = 46.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (allSongs.isEmpty()) {
                item(key = "songs_empty") {
                    LibraryGlassEmptyCard(
                        icon = SfIcons.MusicNote,
                        title = "Your Library Is Quiet",
                        subtitle = "Like tracks or play songs across iTube to build your frosted Liquid Glass song collection.",
                        actionLabel = "Explore Music",
                        onAction = onExploreClick,
                    )
                }
            } else if (visibleSongs.isEmpty()) {
                item(key = "songs_no_match") {
                    LibraryGlassEmptyCard(
                        icon = ITubeIcons.Search,
                        title = "No Songs Found",
                        subtitle = "No library songs matched \"$query\".",
                        actionLabel = "Clear Filter",
                        onAction = { controller.searchQuery = "" },
                    )
                }
            } else {
                itemsIndexed(
                    items = visibleSongs,
                    key = { _, song -> song.videoId },
                ) { index, song ->
                    val isLiked = favoriteIds.contains(song.videoId)
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
                        isCurrent = song.videoId == currentSongId,
                        isPlaying = isPlaying,
                        activeTint = LibraryCrimsonAccent,
                        searchPlayingStyle = true,
                        trailing = if (isLiked) {
                            {
                                Icon(
                                    imageVector = SfIcons.StarFill,
                                    contentDescription = "Liked",
                                    tint = LibraryCrimsonAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        } else null,
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

