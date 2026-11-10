package com.darkxvenom.itune.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.model.Track
import com.darkxvenom.itune.data.repository.MusicRepository
import com.darkxvenom.itune.ui.browse.BrowseScreen
import com.darkxvenom.itune.ui.components.AvatarSelectionDialog
import com.darkxvenom.itune.ui.components.CuratedPlaylistsSection
import com.darkxvenom.itune.ui.components.FeaturedHeroCarousel
import com.darkxvenom.itune.ui.components.FloatingMiniPlayer
import com.darkxvenom.itune.ui.components.FloatingNavBar
import com.darkxvenom.itune.ui.components.LatestSongsSection
import com.darkxvenom.itune.ui.components.NavTab
import com.darkxvenom.itune.ui.components.RadioStationsSection
import com.darkxvenom.itune.ui.components.TopHeaderBar
import com.darkxvenom.itune.ui.components.TopSpringLoadingBar
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.library.LibraryScreen
import com.darkxvenom.itune.ui.player.ITuneMusicPlayer
import com.darkxvenom.itune.ui.player.SheetMorph
import com.darkxvenom.itune.ui.player.rememberSheetMorph
import com.darkxvenom.itune.ui.player.sheetMorphDrag
import com.darkxvenom.itune.ui.search.SearchScreen
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import kotlinx.coroutines.delay

enum class AppScreen {
    HOME,
    SEARCH,
    LIBRARY,
    BROWSE
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val overscrollState = rememberSpringOverscrollState()

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }

    // Retain visited screens in memory so navigation is 100% instantaneous with zero GC or layout stutter
    var hasVisitedBrowse by remember { mutableStateOf(false) }
    var hasVisitedLibrary by remember { mutableStateOf(false) }
    var hasVisitedSearch by remember { mutableStateOf(false) }

    LaunchedEffect(currentScreen) {
        when (currentScreen) {
            AppScreen.BROWSE -> hasVisitedBrowse = true
            AppScreen.LIBRARY -> hasVisitedLibrary = true
            AppScreen.SEARCH -> hasVisitedSearch = true
            AppScreen.HOME -> Unit
        }
    }

    // ONLY trigger loading when pulling down from the very top of the list (item 0)
    LaunchedEffect(overscrollState.offset.value) {
        if (listState.firstVisibleItemIndex == 0 && overscrollState.offset.value > 25f && !isRefreshing) {
            isRefreshing = true
        }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(3500) // Keep loading beam active for ~3.5 seconds
            isRefreshing = false
        }
    }

    // Only show top status bar shade when user scrolls down from top
    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }

    val topShadeAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "TopShadeAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack)
    ) {
        val density = LocalDensity.current
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val sheetMorph = rememberSheetMorph { expanded ->
            MusicPlayerState.expanded = expanded
        }

        SideEffect {
            sheetMorph.travel = screenHeightPx
        }

        // Sync MusicPlayerState.expanded with sheetMorph
        LaunchedEffect(MusicPlayerState.expanded) {
            sheetMorph.animateTo(MusicPlayerState.expanded)
        }
        // SCREEN 1: HOME SCREEN (Retained in GPU layer, 0ms switch time)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (currentScreen == AppScreen.HOME) 1f else 0f
                    translationY = if (currentScreen == AppScreen.HOME) 0f else 99999f
                }
        ) {
            // Main Scrollable Body with Unlocked Deep Spring Physics
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .springOverscroll(overscrollState)
            ) {
                // Top Bar with ic_itune_waves and Apple Memoji Avatar
                item(key = "top_header") {
                    TopHeaderBar(
                        onProfileClick = {
                            showAvatarDialog = true
                        }
                    )
                }

                // Featured Hero Carousel (Today's Hits / New Album)
                item(key = "featured_hero") {
                    FeaturedHeroCarousel(
                        cards = MusicRepository.featuredCards,
                        modifier = Modifier.padding(top = 4.dp),
                        onCardClick = { card ->
                            Toast.makeText(context, "Opening ${card.title}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item(key = "spacer_hero") {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Latest Songs Section with Horizontal Paging Columns
                item(key = "latest_songs") {
                    LatestSongsSection(
                        songColumns = MusicRepository.latestSongColumns,
                        onSeeAllClick = {
                            Toast.makeText(context, "Browsing all latest songs", Toast.LENGTH_SHORT).show()
                        },
                        onTrackClick = { track ->
                            currentTrack = track
                            isPlaying = true
                            MusicPlayerState.play(
                                MusicTrack(
                                    id = track.id,
                                    title = track.title,
                                    artists = track.artist,
                                    durationSec = 210L
                                )
                            )
                            Toast.makeText(context, "Now playing: ${track.title}", Toast.LENGTH_SHORT).show()
                        },
                        onTrackMenuClick = { track ->
                            Toast.makeText(context, "Options for ${track.title}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item(key = "spacer_songs") {
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Curated Playlists (Spatial Audio & Essentials)
                item(key = "curated_playlists") {
                    CuratedPlaylistsSection(
                        playlists = MusicRepository.essentialPlaylists,
                        onPlaylistClick = { playlist ->
                            Toast.makeText(context, "Playlist: ${playlist.title}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item(key = "spacer_playlists") {
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Live Radio Stations
                item(key = "live_radio") {
                    RadioStationsSection(
                        stations = MusicRepository.radioStations,
                        onStationClick = { station ->
                            Toast.makeText(context, "Tuning into ${station.name}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Generous bottom spacing so bottom content scrolls above floating nav & mini player
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(190.dp))
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

            // Sleek Left-to-Right Animated Loading Line: ONLY visible when at the top
            TopSpringLoadingBar(
                isVisible = isRefreshing && listState.firstVisibleItemIndex == 0,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // SCREEN 2: BROWSE SCREEN (Retained)
        if (hasVisitedBrowse || currentScreen == AppScreen.BROWSE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (currentScreen == AppScreen.BROWSE) 1f else 0f
                        translationY = if (currentScreen == AppScreen.BROWSE) 0f else 99999f
                    }
            ) {
                BrowseScreen(
                    onCategoryClick = { item ->
                        Toast.makeText(context, "Opening ${item.title}", Toast.LENGTH_SHORT).show()
                    },
                    onProfileClick = {
                        showAvatarDialog = true
                    }
                )
            }
        }

        // SCREEN 3: LIBRARY SCREEN (Retained)
        if (hasVisitedLibrary || currentScreen == AppScreen.LIBRARY) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (currentScreen == AppScreen.LIBRARY) 1f else 0f
                        translationY = if (currentScreen == AppScreen.LIBRARY) 0f else 99999f
                    }
            ) {
                LibraryScreen(
                    onPinClick = { pin ->
                        Toast.makeText(context, "Library Pin: ${pin.title}", Toast.LENGTH_SHORT).show()
                    },
                    onMenuItemClick = { item ->
                        Toast.makeText(context, "Navigating to ${item.title}", Toast.LENGTH_SHORT).show()
                    },
                    onRecentAlbumClick = { album ->
                        Toast.makeText(context, "Recent: ${album.title} by ${album.artist}", Toast.LENGTH_SHORT).show()
                    },
                    onProfileClick = {
                        showAvatarDialog = true
                    }
                )
            }
        }

        // SCREEN 4: SEARCH SCREEN (Retained)
        if (hasVisitedSearch || currentScreen == AppScreen.SEARCH) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (currentScreen == AppScreen.SEARCH) 1f else 0f
                        translationY = if (currentScreen == AppScreen.SEARCH) 0f else 99999f
                    }
            ) {
                SearchScreen(
                    onCategoryClick = { category ->
                        Toast.makeText(context, "Category: ${category.title}", Toast.LENGTH_SHORT).show()
                    },
                    onProfileClick = {
                        showAvatarDialog = true
                    }
                )
            }
        }

        // Apple Dark Mode Gradient Scrim for Floating Bottom Controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AppleOledBlack.copy(alpha = 0.70f),
                            AppleOledBlack.copy(alpha = 0.94f),
                            AppleOledBlack
                        )
                    )
                )
        )

        // Floating Bottom Controls: Mini-Player (ONLY shown when a song is playing) + Navigation Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val p = sheetMorph.progress.value
                    translationY = p * 160.dp.toPx()
                    alpha = (1f - p * 2.5f).coerceIn(0f, 1f)
                }
        ) {
            val realTrack = MusicPlayerState.currentTrack
            if (realTrack != null) {
                FloatingMiniPlayer(
                    modifier = Modifier.sheetMorphDrag(sheetMorph),
                    currentTrack = currentTrack,
                    realTrack = realTrack,
                    isPlaying = MusicPlayerState.isPlaying,
                    onPlayPauseClick = {
                        MusicPlayerState.togglePlayPause()
                    },
                    onNextClick = {
                        MusicPlayerState.skipNext()
                    },
                    onClick = {
                        MusicPlayerState.expanded = true
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
            }

            FloatingNavBar(
                selectedTab = when (currentScreen) {
                    AppScreen.HOME -> selectedTab
                    AppScreen.BROWSE -> NavTab.NEW
                    AppScreen.LIBRARY -> NavTab.LIBRARY
                    AppScreen.SEARCH -> null
                },
                isSearchActive = currentScreen == AppScreen.SEARCH,
                onTabSelected = { tab ->
                    selectedTab = tab
                    currentScreen = when (tab) {
                        NavTab.NEW -> AppScreen.BROWSE
                        NavTab.LIBRARY -> AppScreen.LIBRARY
                        else -> AppScreen.HOME
                    }
                },
                onSearchClick = {
                    currentScreen = if (currentScreen == AppScreen.SEARCH) {
                        when (selectedTab) {
                            NavTab.NEW -> AppScreen.BROWSE
                            NavTab.LIBRARY -> AppScreen.LIBRARY
                            else -> AppScreen.HOME
                        }
                    } else {
                        AppScreen.SEARCH
                    }
                }
            )
        }

        // Apple iOS-styled Avatar Selection Modal Dialog
        if (showAvatarDialog) {
            AvatarSelectionDialog(
                onDismiss = { showAvatarDialog = false }
            )
        }

        // ── Full Player Sheet with 1:1 Tip-of-Finger Gesture Tracking and Spring Physics (Copied from iTube) ──
        val realTrack = MusicPlayerState.currentTrack
        if (realTrack != null) {
            BackHandler(enabled = (MusicPlayerState.expanded || sheetMorph.progress.value > 0.05f) && !MusicPlayerState.lyricsOpen && !MusicPlayerState.queueOpen) {
                MusicPlayerState.expanded = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = sheetMorph.progress.value
                        translationY = (1f - p) * screenHeightPx
                        alpha = if (p <= 0.001f) 0f else 1f
                    }
                    .sheetMorphDrag(sheetMorph)
            ) {
                ITuneMusicPlayer(
                    onCollapse = {
                        MusicPlayerState.expanded = false
                    }
                )
            }
        }
    }
}
