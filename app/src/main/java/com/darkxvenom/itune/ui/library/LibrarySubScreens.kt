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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
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
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
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
                LibraryDestination.CACHED_SONGS -> CachedSongsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.TIME_TRAVEL -> com.darkxvenom.itune.ui.timetravel.TimeTravelScreen(
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
    var showPlaylistImport by remember { mutableStateOf(false) }
    if (showPlaylistImport) {
        PlaylistImportDialog(onDismiss = { showPlaylistImport = false })
    }
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .lightweightLiquidGlass(shape = CircleShape, fallbackColor = Color(0xCC141418))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                                    .clickable { showPlaylistImport = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    contentDescription = "Import playlist",
                                    tint = LibraryCrimsonAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("Import", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .lightweightLiquidGlass(shape = CircleShape, fallbackColor = Color(0xCC141418))
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
                                Text("New", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
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
    val context = LocalContext.current
    val likedMeta by LibraryStore.likedSongs.collectAsStateWithLifecycle()
    val recentMeta by LibraryStore.recentSongs.collectAsStateWithLifecycle()
    val ytmHistoryMeta by LibraryStore.ytmHistorySongs.collectAsStateWithLifecycle()
    val ytAccount by GoogleAuthStore.state.collectAsStateWithLifecycle()
    val favoriteIds = MusicPlayerState.favoriteIds
    val query = controller.searchQuery
    val sort = controller.songSort

    LaunchedEffect(ytAccount.isSignedIn, ytAccount.activeSessionKey) {
        if (ytAccount.isSignedIn) {
            runCatching { YouTubeLibrarySyncManager.syncHistory(context) }
        }
    }

    val mergedMeta = remember(ytmHistoryMeta, recentMeta, likedMeta, ytAccount.isSignedIn) {
        val seen = LinkedHashSet<String>()
        buildList {
            if (ytAccount.isSignedIn) {
                ytmHistoryMeta.forEach { if (seen.add(it.id)) add(it) }
            }
            recentMeta.forEach { if (seen.add(it.id)) add(it) }
            likedMeta.forEach { if (seen.add(it.id)) add(it) }
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
                        countText = if (ytAccount.isSignedIn) {
                            "${allSongs.size} Tracks • YouTube Music History"
                        } else {
                            "${allSongs.size} Library Tracks"
                        },
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

@Composable
private fun LibraryScreenTitleHeader(
    title: String,
    countText: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = countText,
            color = Color.White.copy(alpha = 0.66f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LibraryCollectionGlassCard(
    entry: SavedCollectionEntry,
    placeholderIcon: ImageVector,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    LibraryGlassCardContainer(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(
                onClick = onClick,
                onLongClick = onMenuClick,
            ),
        cornerRadius = 20.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(0.7.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            ) {
                if (entry.thumbnailUrl.isNullOrBlank()) {
                    CoverPlaceholder(
                        icon = placeholderIcon,
                        base = Color(0xFF1E1E24),
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = entry.thumbnailUrl.artworkAt(CARD_ART_PX),
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0x88101015))
                        .menuAnchor(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.isExplicit) {
                    ExplicitBadge(
                        color = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.padding(end = 5.dp),
                    )
                }
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            val fallbackType = when (entry.type) {
                BrowseType.PLAYLIST -> "Playlist"
                BrowseType.ALBUM -> "Album"
                BrowseType.ARTIST -> "Artist"
                BrowseType.OTHER -> "Collection"
            }
            Text(
                text = entry.subtitle.ifBlank { fallbackType },
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryArtistGlassRow(
    entry: SavedCollectionEntry,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    onStartRadio: () -> Unit,
) {
    LibraryGlassCardContainer(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(
                onClick = onClick,
                onLongClick = onMenuClick,
            ),
        cornerRadius = 22.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .shadow(10.dp, CircleShape, spotColor = Color(0xFF9C6BFF).copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            ) {
                if (entry.thumbnailUrl.isNullOrBlank()) {
                    CoverPlaceholder(
                        icon = Icons.Rounded.Person,
                        base = Color(0xFF1F1E28),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = entry.thumbnailUrl.artworkAt(ROW_ART_PX),
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = entry.subtitle.ifBlank { "Favorite Artist" },
                    color = Color.White.copy(alpha = 0.64f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onStartRadio,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .lightweightLiquidGlass(CircleShape, fallbackColor = Color(0x33FFFFFF)),
            ) {
                Icon(
                    imageVector = SfIcons.Radio,
                    contentDescription = stringResource(R.string.start_radio),
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.menuAnchor(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more),
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun UserPlaylistDetailScreen(
    playlistId: String?,
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
) {
    val userPlaylists by LibraryStore.userPlaylists.collectAsStateWithLifecycle()
    val playlist = remember(userPlaylists, playlistId) {
        userPlaylists.firstOrNull { it.id == playlistId }
    }
    val rawMeta = playlist?.songs.orEmpty()
    val sort = controller.songSort
    val sortedSongs = remember(rawMeta, sort) {
        sortLibrarySongs(rawMeta, sort).map { it.toSong() }
    }
    val listState = controller.listStates.getOrPut(LibraryDestination.USER_PLAYLIST) { LazyListState() }
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 380
        }
    }
    val currentSong = MusicPlayerState.currentTrack?.toArtistSong()
    val isPlaying = MusicPlayerState.isPlaying
    val glassActive = isGlassSupported()
    val contentPadding = PaddingValues(
        top = topBarContentPadding(),
        bottom = if (hasMiniPlayer) 210.dp else 140.dp,
    )

    if (playlist == null) {
        LibraryFrostedScreenScaffold(
            controller = controller,
            artworkUrls = emptyList(),
            scrolledTitle = "Playlist",
            isScrolled = false,
            hasMiniPlayer = hasMiniPlayer,
            amoledDark = true,
            onBack = onBack,
        ) { pad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center,
            ) {
                LibraryGlassEmptyCard(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = "Playlist Removed",
                    subtitle = "This playlist is no longer available.",
                    actionLabel = "Back to Playlists",
                    onAction = onBack,
                )
            }
        }
        return
    }

    val playlistPage = remember(playlist, sortedSongs) {
        DetailPage(
            browseId = playlist.id,
            title = playlist.name,
            subtitle = "Playlist",
            thumbnailUrl = playlist.leadArtwork,
            collageArtworkUrls = playlist.artworkUrls,
            songs = UiState.Success(sortedSongs),
            type = BrowseType.PLAYLIST,
            library = LibraryState(playlistId = playlist.id, saved = true),
        )
    }

    ITubeArtistTheme(darkTheme = true) {
        val detailPalette = rememberArtworkPalette(imageUrl = playlist.leadArtwork)
        val pageColor = detailPalette.background

        CompositionLocalProvider(
            LocalAppBackdrop provides controller.appBackdrop,
            LocalLiquidGlassEnabled provides glassActive,
            LocalReduceDynamicBlur provides false,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .snapshotBackdropSource(controller.pageBackdrop, floor = pageColor)
                        .hazeSource(state = controller.hazeState),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(pageColor)
                            .layerBackdrop(controller.appBackdrop),
                    )
                    ITubeDetailScreen(
                        page = playlistPage,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSongClick = { queue, index -> controller.playSongs(queue, index) },
                        onSongLongPress = { song -> controller.openSongMenu(song) },
                        onSongSwipe = { song ->
                            MusicPlayerState.playNext(song.toMusicTrack())
                            controller.showToast("Playing \"${song.title}\" next")
                        },
                        onShuffle = { queue -> controller.playSongs(queue, 0, shuffle = true) },
                        onSectionItemClick = {},
                        onArtistClick = { artistId, artistName ->
                            controller.openDetail(
                                browseId = artistId,
                                title = artistName,
                                subtitle = "Artist",
                                thumbnailUrl = null,
                                type = BrowseType.ARTIST,
                            )
                        },
                        contentPadding = contentPadding,
                        listState = listState,
                        searching = controller.searchOpen,
                        onSearchingChange = { open ->
                            controller.searchOpen = open
                            if (!open) controller.searchQuery = ""
                        },
                        onToggleLibrary = {
                            controller.openTopMenu(
                                LibraryTopMenuRequest(
                                    destination = LibraryDestination.USER_PLAYLIST,
                                    songs = sortedSongs,
                                    stationLabel = playlist.name,
                                    showSort = rawMeta.isNotEmpty(),
                                    userPlaylist = playlist,
                                ),
                            )
                        },
                        emptyContent = {
                            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                                LibraryGlassEmptyCard(
                                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    title = "Empty Playlist",
                                    subtitle = "Open \u22EE on any song in Search, Artist pages, Album pages, or Library and tap Add to Playlist to populate this playlist.",
                                    actionLabel = "Add Songs from Search",
                                    onAction = onExploreClick,
                                )
                            }
                        },
                    )
                }

                TopFadeBlur(
                    hazeState = controller.hazeState,
                    pageColor = pageColor,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                FrostedTopBar(
                    transparentBackdrop = true,
                    artworkPageChrome = true,
                    backButtonHazeState = controller.hazeState,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopCenter),
                    centerContent = {
                        AnimatedVisibility(
                            visible = isScrolled,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180)),
                        ) {
                            FrostedTopBarTitlePill(hazeState = controller.hazeState) {
                                Text(
                                    text = playlist.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        LibraryTopBarActionButtons(
                            controller = controller,
                            showSort = rawMeta.isNotEmpty(),
                            onMoreClick = {
                                controller.openTopMenu(
                                    LibraryTopMenuRequest(
                                        destination = LibraryDestination.USER_PLAYLIST,
                                        songs = sortedSongs,
                                        stationLabel = playlist.name,
                                        showSort = rawMeta.isNotEmpty(),
                                        userPlaylist = playlist,
                                    ),
                                )
                            },
                        )
                    },
                )

                BottomFadeScrim(
                    withMiniPlayer = hasMiniPlayer,
                    pageColor = pageColor,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun UserPlaylistGlassCard(
    playlist: UserPlaylist,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    LibraryGlassCardContainer(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(
                onClick = onClick,
                onLongClick = onMenuClick,
            ),
        cornerRadius = 20.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            ) {
                PlaylistCollageAvatar(
                    artworkUrls = playlist.artworkUrls,
                    size = null,
                    cornerRadius = 14.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                if (playlist.isGoogleAccount) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.ic_google_logo),
                            contentDescription = "Google Account Playlist",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0x88101015))
                        .menuAnchor(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            val hasExplicit = remember(playlist.songs) { playlist.songs.any { it.isExplicit } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasExplicit) {
                    ExplicitBadge(
                        color = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.padding(end = 5.dp),
                    )
                }
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            val subText = buildString {
                append(if (playlist.songs.size == 1) "1 song" else "${playlist.songs.size} songs")
                if (playlist.isGoogleAccount) append(" • Google")
            }
            Text(
                text = subText,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
