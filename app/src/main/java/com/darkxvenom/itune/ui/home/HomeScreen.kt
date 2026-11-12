package com.darkxvenom.itune.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.backend.MusicArtist
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.NewReleaseAlbum
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.WebSessionMode
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.ui.auth.GoogleLoginScreen
import com.darkxvenom.itune.ui.settings.ContinueWithGoogleRedCard
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.HomeDiscoveryRepository
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.NewReleasesRepository
import com.darkxvenom.itune.data.library.PersonalizedHomeRepository
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.model.BrowseCategoryItem
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.data.model.Playlist
import com.darkxvenom.itune.data.model.Track
import com.darkxvenom.itune.data.repository.MusicRepository
import com.darkxvenom.itune.ui.artist.ArtistScreenOverlayContent
import com.darkxvenom.itune.ui.artist.ArtistScreenOverlayPopups
import com.darkxvenom.itune.ui.artist.rememberArtistHostController
import com.darkxvenom.itune.ui.browse.AppleMoodCategoryScreen
import com.darkxvenom.itune.ui.browse.BrowseScreen
import com.darkxvenom.itune.ui.components.AvatarSelectionDialog
import com.darkxvenom.itune.ui.components.CuratedPlaylistsSection
import com.darkxvenom.itune.ui.components.FeaturedHeroCarousel
import com.darkxvenom.itune.ui.components.FloatingMiniPlayer
import com.darkxvenom.itune.ui.components.FloatingNavBar
import com.darkxvenom.itune.ui.components.LatestSongsSection
import com.darkxvenom.itune.ui.components.HomeAlbumShelf
import com.darkxvenom.itune.ui.components.HomeArtistShelf
import com.darkxvenom.itune.ui.components.HomeCollectionShelf
import com.darkxvenom.itune.ui.components.HomeNewReleaseShelf
import com.darkxvenom.itune.ui.components.HomeTrackShelf
import com.darkxvenom.itune.ui.components.NavTab
import com.darkxvenom.itune.ui.components.RadioStationsSection
import com.darkxvenom.itune.ui.components.TopHeaderBar
import com.darkxvenom.itune.ui.components.TopSpringLoadingBar
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.library.LibraryScreen
import com.darkxvenom.itune.ui.library.LibraryScreenOverlayContent
import com.darkxvenom.itune.ui.library.LibraryScreenOverlayPopups
import com.darkxvenom.itune.ui.library.rememberLibraryHostController
import com.darkxvenom.itune.ui.player.ITuneMusicPlayer
import com.darkxvenom.itune.ui.recognition.IRecognitionScreen
import com.darkxvenom.itune.ui.player.SheetMorph
import com.darkxvenom.itune.ui.player.miniPlayerGestures
import com.darkxvenom.itune.ui.player.rememberSheetMorph
import com.darkxvenom.itune.ui.player.sheetMorphDrag
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.radio.buildHomeLiveRadioStations
import com.darkxvenom.itune.ui.ishare.IShareScreen
import com.darkxvenom.itune.ui.player.AppleRadioPlayerOverlay
import com.darkxvenom.itune.ui.player.FloatingRadioMiniPlayer
import com.darkxvenom.itune.ui.radio.OnlineRadioScreen
import com.darkxvenom.itune.ui.search.SearchScreen
import com.darkxvenom.itune.ui.settings.AppleSettingsScreen
import com.darkxvenom.itune.ui.settings.DynamicIslandScreen
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    (start.value + (stop.value - start.value) * fraction.coerceIn(0f, 1f)).coerceAtLeast(0f).dp

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    val f = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * f
}

enum class AppScreen {
    HOME,
    SEARCH,
    LIBRARY,
    BROWSE,
    RADIO,
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    showTopBarBrand: Boolean = true,
    deferStartupPrompts: Boolean = false,
    onTopBarBrandBoundsChanged: ((iconBounds: Rect, titleBounds: Rect) -> Unit)? = null,
) {
    val context = LocalContext.current
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val localeSignature = remember(settingsState.contentLanguage, settingsState.contentCountry) {
        SettingsStore.localeSignature()
    }
    val featuredPlaylists by NewReleasesRepository.featuredPlaylists.collectAsStateWithLifecycle()
    val newReleaseAlbums by NewReleasesRepository.releases.collectAsStateWithLifecycle()
    val personalizedQuickPicks by PersonalizedHomeRepository.quickPickTracks.collectAsStateWithLifecycle()
    val personalizedPlaylists by PersonalizedHomeRepository.recommendedPlaylists.collectAsStateWithLifecycle()
    val ytAccountState by GoogleAuthStore.state.collectAsStateWithLifecycle()

    val trendingTracks by HomeDiscoveryRepository.trendingTracks.collectAsStateWithLifecycle()
    val popularPlaylists by HomeDiscoveryRepository.popularPlaylists.collectAsStateWithLifecycle()
    val popularAlbums by HomeDiscoveryRepository.popularAlbums.collectAsStateWithLifecycle()
    val popularArtists by HomeDiscoveryRepository.popularArtists.collectAsStateWithLifecycle()
    val podcastShows by HomeDiscoveryRepository.podcastShows.collectAsStateWithLifecycle()
    val tasteTracks by HomeDiscoveryRepository.tasteTracks.collectAsStateWithLifecycle()

    val localRecentSongs by LibraryStore.recentSongs.collectAsStateWithLifecycle()
    val accountHistory by LibraryStore.ytmHistorySongs.collectAsStateWithLifecycle()
    val savedArtists by LibraryStore.likedArtists.collectAsStateWithLifecycle()
    val savedAlbums by LibraryStore.savedAlbums.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        LibraryStore.ensureInit(context)
    }
    LaunchedEffect(localeSignature, ytAccountState.isSignedIn, ytAccountState.activeSessionKey) {
        HomeDiscoveryRepository.ensureLoaded()
        NewReleasesRepository.ensureLoaded()
        if (ytAccountState.isSignedIn) {
            PersonalizedHomeRepository.refreshIfSignedIn()
        }
    }

    val visibleQuickPicks = remember(personalizedQuickPicks, settingsState.hideExplicitContent) {
        ContentLocaleCatalog.filterVisible(
            personalizedQuickPicks,
            settingsState.hideExplicitContent,
        ) { it.isExplicit }
    }
    val visibleTrendingTracks = remember(trendingTracks, settingsState.hideExplicitContent) {
        ContentLocaleCatalog.filterVisible(
            trendingTracks,
            settingsState.hideExplicitContent,
        ) { it.isExplicit }
    }
    val primaryHomeTracks = if (ytAccountState.isSignedIn && visibleQuickPicks.isNotEmpty()) {
        visibleQuickPicks
    } else {
        visibleTrendingTracks
    }
    val allHomePlaylistRefs = remember(
        personalizedPlaylists, featuredPlaylists, popularPlaylists, ytAccountState.isSignedIn
    ) {
        val accountFeed = if (ytAccountState.isSignedIn) personalizedPlaylists else emptyList()
        (accountFeed + featuredPlaylists + popularPlaylists).distinctBy { it.id }
    }
    val visibleHomePlaylistRefs = remember(allHomePlaylistRefs, settingsState.hideExplicitContent) {
        ContentLocaleCatalog.filterVisible(
            allHomePlaylistRefs,
            settingsState.hideExplicitContent,
        ) { it.isExplicit }
    }
    val recentHomeTracks = remember(localRecentSongs, accountHistory) {
        (localRecentSongs + accountHistory)
            .distinctBy { it.id }
            .map { it.toMusicTrack() }
            .take(24)
    }
    val preferredArtistNames = remember(savedArtists, recentHomeTracks) {
        (savedArtists.map { it.title } + recentHomeTracks.map { it.artists.substringBefore(",").trim() })
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(4)
    }
    LaunchedEffect(ytAccountState.isSignedIn, ytAccountState.activeSessionKey, preferredArtistNames) {
        HomeDiscoveryRepository.refreshFromListeningTaste(
            sessionKey = ytAccountState.activeSessionKey.takeIf { ytAccountState.isSignedIn },
            artistNames = preferredArtistNames,
        )
    }
    val recommendedAlbums = remember(savedAlbums, popularAlbums) {
        (
            savedAlbums.map { album ->
                MusicPlaylistRef(
                    id = album.browseId,
                    title = album.title,
                    author = album.subtitle,
                    thumbnailUrl = album.thumbnailUrl,
                    isAlbum = true,
                )
            } + popularAlbums
        ).distinctBy { it.id }.take(24)
    }
    val recommendedArtists = remember(savedArtists, popularArtists) {
        (
            savedArtists.map { artist ->
                MusicArtist(
                    id = artist.browseId,
                    name = artist.title,
                    thumbnailUrl = artist.thumbnailUrl,
                    subscribers = artist.subtitle,
                )
            } + popularArtists
        ).distinctBy { it.id }.take(24)
    }
    val visibleNewReleases = remember(newReleaseAlbums, settingsState.hideExplicitContent) {
        ContentLocaleCatalog.filterVisible(
            newReleaseAlbums,
            settingsState.hideExplicitContent,
        ) { it.isExplicit }
    }
    val featuredSourceRefs = remember(visibleNewReleases, visibleHomePlaylistRefs, popularAlbums) {
        val liveAlbums = visibleNewReleases.map { album ->
            MusicPlaylistRef(
                id = album.browseId,
                title = album.title,
                author = album.artist,
                authorId = album.artistId,
                thumbnailUrl = album.thumbnailUrl,
                isAlbum = true,
                year = album.year?.toString(),
            )
        }
        (liveAlbums + visibleHomePlaylistRefs + popularAlbums)
            .distinctBy { it.id }
            .take(3)
    }
    val homeFeaturedCards = remember(featuredSourceRefs) {
        MusicRepository.featuredCards.mapIndexed { index, baseCard ->
            val ref = featuredSourceRefs.getOrNull(index) ?: return@mapIndexed baseCard
            baseCard.copy(
                category = if (ref.isAlbum) "NEW ALBUM" else "FEATURED PLAYLIST",
                title = ref.title,
                subtitle = ref.author.ifBlank { "YouTube Music" },
                caption = if (ref.isAlbum) {
                    listOfNotNull("New release", ref.year).joinToString(" • ")
                } else {
                    if (ref.trackCount > 0) "${ref.trackCount} tracks • YouTube Music" else "Featured on YouTube Music"
                },
                imageUrl = ref.thumbnailUrl,
                destinationBrowseId = ref.id,
                destinationIsAlbum = ref.isAlbum,
            )
        }
    }
    val listState = rememberLazyListState()
    val overscrollState = rememberSpringOverscrollState()
    val coroutineScope = rememberCoroutineScope()

    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }
    var currentTrack by remember { mutableStateOf<Track?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showIShare by remember { mutableStateOf(false) }
    var showDynamicIsland by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showHomeGoogleLogin by remember { mutableStateOf(false) }
    var showIRecognition by remember { mutableStateOf(false) }

    // Retain visited screens in memory so navigation is 100% instantaneous with zero GC or layout stutter
    var hasVisitedBrowse by remember { mutableStateOf(false) }
    var hasVisitedLibrary by remember { mutableStateOf(false) }
    var hasVisitedSearch by remember { mutableStateOf(false) }
    var hasVisitedRadio by remember { mutableStateOf(false) }
    val onlineRadioState by OnlineRadioController.state.collectAsStateWithLifecycle()
    val artistHostController = rememberArtistHostController()
    val libraryHostController = rememberLibraryHostController()
    var activeMoodCategory by remember { mutableStateOf<BrowseCategoryItem?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled silently on startup; Download popup re-checks before notifying */ }

    LaunchedEffect(Unit) {
        OnlineRadioController.ensureInit(context)
        OnlineRadioController.ensureCatalogLoaded()
    }

    LaunchedEffect(deferStartupPrompts) {
        if (!deferStartupPrompts &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(currentScreen) {
        when (currentScreen) {
            AppScreen.BROWSE -> hasVisitedBrowse = true
            AppScreen.LIBRARY -> hasVisitedLibrary = true
            AppScreen.SEARCH -> hasVisitedSearch = true
            AppScreen.RADIO -> hasVisitedRadio = true
            AppScreen.HOME -> Unit
        }
    }

    LaunchedEffect(MusicPlayerState.pendingArtistSearch) {
        if (!MusicPlayerState.pendingArtistSearch.isNullOrBlank()) {
            currentScreen = AppScreen.SEARCH
        }
    }

    LaunchedEffect(MusicPlayerState.pendingArtistNav) {
        val nav = MusicPlayerState.pendingArtistNav ?: return@LaunchedEffect
        MusicPlayerState.pendingArtistNav = null
        artistHostController.openFromNav(nav)
    }

    // ONLY trigger loading when pulling down from the very top of the list (item 0)
    LaunchedEffect(overscrollState.offset.value) {
        if (listState.firstVisibleItemIndex == 0 && overscrollState.offset.value > 25f && !isRefreshing) {
            isRefreshing = true
            HomeDiscoveryRepository.refresh()
            NewReleasesRepository.refresh()
            if (ytAccountState.isSignedIn) {
                YouTubeLibrarySyncManager.launchFullSync(context)
            }
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

    var rootBoxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var miniPlayerSlotTopPx by remember { mutableFloatStateOf(0f) }
    var radioMiniPlayerSlotTopPx by remember { mutableFloatStateOf(0f) }

    val showRadioMiniPlayer = onlineRadioState.hasActiveSession && (
        onlineRadioState.isPlaying ||
            onlineRadioState.isBuffering ||
            (!MusicPlayerState.isPlaying && (currentScreen == AppScreen.RADIO || MusicPlayerState.currentTrack == null))
        )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(com.darkxvenom.itune.ui.theme.AppleOledBlack)
            .onGloballyPositioned { rootBoxCoords = it }
    ) {
        val hasMiniPlayer = showRadioMiniPlayer || MusicPlayerState.currentTrack != null
        val density = LocalDensity.current
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight
        val screenWidthPx = with(density) { screenWidthDp.toPx() }
        val screenHeightPx = with(density) { screenHeightDp.toPx() }

        val sheetMorph = rememberSheetMorph { expanded ->
            MusicPlayerState.expanded = expanded
        }
        val radioSheetMorph = rememberSheetMorph { expanded ->
            OnlineRadioController.setPlayerExpanded(expanded)
        }

        SideEffect {
            sheetMorph.travel = screenHeightPx
            radioSheetMorph.travel = screenHeightPx
        }

        // Sync MusicPlayerState.expanded with sheetMorph
        LaunchedEffect(MusicPlayerState.expanded) {
            sheetMorph.animateTo(MusicPlayerState.expanded)
        }
        LaunchedEffect(onlineRadioState.playerExpanded) {
            radioSheetMorph.animateTo(onlineRadioState.playerExpanded)
        }
        LaunchedEffect(onlineRadioState.hasActiveSession) {
            if (!onlineRadioState.hasActiveSession) {
                radioSheetMorph.progress.snapTo(0f)
            }
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
                        displayName = settingsState.displayName,
                        showBrandLogoAndTitle = showTopBarBrand,
                        onBrandBoundsChanged = onTopBarBrandBoundsChanged,
                        onProfileClick = {
                            showSettings = true
                        }
                    )
                }

                // Red Continue with Google card shown at top below TopHeaderBar when signed out; hides once logged in
                if (!ytAccountState.isSignedIn) {
                    item(key = "home_google_connect_card") {
                        ContinueWithGoogleRedCard(
                            title = "Continue with Google",
                            subtitlePhrases = listOf(
                                "Sync your YouTube Music playlists",
                                "Sync liked songs, artists & history",
                                "Personalize your feed in real time",
                            ),
                            onClick = { showHomeGoogleLogin = true },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }

                // Featured Hero Carousel (Today's Hits / New Album)
                item(key = "featured_hero") {
                    FeaturedHeroCarousel(
                        cards = homeFeaturedCards,
                        modifier = Modifier.padding(top = 4.dp),
                        onCardClick = { card ->
                            val browseId = card.destinationBrowseId
                            if (!browseId.isNullOrBlank()) {
                                MusicPlayerState.pendingArtistNav = PendingArtistNav(
                                    browseId = browseId,
                                    name = card.title,
                                    subtitle = card.subtitle,
                                    thumbnailUrl = card.imageUrl,
                                    initialType = if (card.destinationIsAlbum) BrowseType.ALBUM else BrowseType.PLAYLIST,
                                )
                            } else {
                                MusicPlayerState.pendingArtistSearch = card.title
                                currentScreen = AppScreen.SEARCH
                            }
                        }
                    )
                }

                item(key = "spacer_hero") {
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Latest Songs Section with Horizontal Paging Columns
                item(key = "latest_songs") {
                    LatestSongsSection(
                        songColumns = emptyList(),
                        personalizedTracks = primaryHomeTracks,
                        sectionTitle = if (ytAccountState.isSignedIn && visibleQuickPicks.isNotEmpty()) {
                            "Picked for You"
                        } else {
                            "Trending on YouTube Music"
                        },
                        onSeeAllClick = {
                            if (primaryHomeTracks.isNotEmpty()) {
                                MusicPlayerState.play(primaryHomeTracks.first(), primaryHomeTracks)
                            } else {
                                currentScreen = AppScreen.BROWSE
                                selectedTab = NavTab.BROWSE
                            }
                        },
                        onTrackClick = { track ->
                            currentTrack = track
                            isPlaying = true
                            MusicPlayerState.play(
                                MusicTrack(
                                    id = track.id,
                                    title = track.title,
                                    artists = track.artist,
                                    durationSec = 210L,
                                    isExplicit = track.isExplicit,
                                )
                            )
                            Toast.makeText(context, "Now playing: ${track.title}", Toast.LENGTH_SHORT).show()
                        },
                        onTrackMenuClick = { track ->
                            Toast.makeText(context, "Options for ${track.title}", Toast.LENGTH_SHORT).show()
                        },
                        onPersonalizedTrackClick = { liveTrack ->
                            MusicPlayerState.play(liveTrack, primaryHomeTracks)
                        },
                        onPersonalizedTrackMenuClick = { liveTrack ->
                            MusicPlayerState.playNext(liveTrack)
                            Toast.makeText(context, "Playing \"${liveTrack.title}\" next", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                item(key = "spacer_songs") {
                    Spacer(modifier = Modifier.height(28.dp))
                }

                if (ytAccountState.isSignedIn && recentHomeTracks.isNotEmpty()) {
                    item(key = "home_recently_played") {
                        HomeTrackShelf(
                            title = "Recently Played",
                            subtitle = "From your listening history",
                            tracks = recentHomeTracks,
                            onTrackClick = { track -> MusicPlayerState.play(track, recentHomeTracks) },
                        )
                    }
                    item(key = "spacer_recently_played") { Spacer(Modifier.height(28.dp)) }
                }

                if (ytAccountState.isSignedIn && tasteTracks.isNotEmpty()) {
                    item(key = "home_taste_recommendations") {
                        HomeTrackShelf(
                            title = "Because You Listen To",
                            subtitle = preferredArtistNames.joinToString(" · "),
                            tracks = tasteTracks,
                            onTrackClick = { track -> MusicPlayerState.play(track, tasteTracks) },
                        )
                    }
                    item(key = "spacer_taste_recommendations") { Spacer(Modifier.height(28.dp)) }
                }

                if (ytAccountState.isSignedIn && recommendedArtists.isNotEmpty()) {
                    item(key = "home_recommended_artists") {
                        HomeArtistShelf(
                            title = if (ytAccountState.isSignedIn && savedArtists.isNotEmpty()) "Your Artists" else "Popular Artists",
                            artists = recommendedArtists,
                            onArtistClick = { artist ->
                                MusicPlayerState.pendingArtistNav = PendingArtistNav(
                                    browseId = artist.id,
                                    name = artist.name,
                                    subtitle = artist.subscribers ?: "Artist",
                                    thumbnailUrl = artist.thumbnailUrl,
                                    initialType = BrowseType.ARTIST,
                                )
                            },
                        )
                    }
                    item(key = "spacer_recommended_artists") { Spacer(Modifier.height(28.dp)) }
                }

                if (ytAccountState.isSignedIn && recommendedAlbums.isNotEmpty()) {
                    item(key = "home_recommended_albums") {
                        HomeCollectionShelf(
                            title = if (ytAccountState.isSignedIn && savedAlbums.isNotEmpty()) "Albums You Saved & More" else "Popular Albums",
                            items = recommendedAlbums,
                            onItemClick = { album ->
                                MusicPlayerState.pendingArtistNav = PendingArtistNav(
                                    browseId = album.id,
                                    name = album.title,
                                    subtitle = album.author.ifBlank { "Album" },
                                    thumbnailUrl = album.thumbnailUrl,
                                    initialType = BrowseType.ALBUM,
                                )
                            },
                        )
                    }
                    item(key = "spacer_recommended_albums") { Spacer(Modifier.height(28.dp)) }
                }

                if (ytAccountState.isSignedIn && visibleNewReleases.isNotEmpty()) {
                    item(key = "home_new_releases") {
                        HomeNewReleaseShelf(
                            title = "New Releases",
                            albums = visibleNewReleases,
                            onAlbumClick = { album ->
                                MusicPlayerState.pendingArtistNav = PendingArtistNav(
                                    browseId = album.browseId,
                                    name = album.title,
                                    subtitle = album.subtitleLine,
                                    thumbnailUrl = album.thumbnailUrl,
                                    initialType = BrowseType.ALBUM,
                                )
                            },
                        )
                    }
                    item(key = "spacer_new_releases") { Spacer(Modifier.height(28.dp)) }
                }

                val playlistShelf = if (visibleHomePlaylistRefs.isNotEmpty()) visibleHomePlaylistRefs else emptyList()
                if (playlistShelf.isNotEmpty()) {
                    item(key = "curated_playlists") {
                        HomeCollectionShelf(
                            title = if (ytAccountState.isSignedIn && personalizedPlaylists.isNotEmpty()) "Made for You" else "Popular Playlists",
                            items = playlistShelf,
                            subtitle = "Playlists from YouTube Music",
                            onItemClick = { playlist ->
                                MusicPlayerState.pendingArtistNav = PendingArtistNav(
                                    browseId = playlist.id,
                                    name = playlist.title,
                                    subtitle = playlist.author.ifBlank { "YouTube Music" },
                                    thumbnailUrl = playlist.thumbnailUrl,
                                    initialType = BrowseType.PLAYLIST,
                                )
                            },
                        )
                    }
                    item(key = "spacer_playlists") { Spacer(Modifier.height(28.dp)) }
                }

                if (ytAccountState.isSignedIn && podcastShows.isNotEmpty()) {
                    item(key = "home_podcasts") {
                        HomeCollectionShelf(
                            title = "Podcasts",
                            subtitle = "Shows discovered on YouTube Music",
                            items = podcastShows,
                            onItemClick = { show ->
                                MusicPlayerState.pendingArtistNav = PendingArtistNav(
                                    browseId = show.id,
                                    name = show.title,
                                    subtitle = show.author.ifBlank { "Podcast" },
                                    thumbnailUrl = show.thumbnailUrl,
                                    initialType = BrowseType.PLAYLIST,
                                )
                            },
                        )
                    }
                    item(key = "spacer_podcasts") { Spacer(Modifier.height(28.dp)) }
                }

                item(key = "spacer_before_radio") {
                    Spacer(modifier = Modifier.height(28.dp))
                }

                // Live Radio Stations (Top 10: Favorites prioritized first, then unliked online stations)
                item(key = "live_radio") {
                    val homeRadioStations = remember(
                        onlineRadioState.favoriteStations,
                        onlineRadioState.stations,
                    ) {
                        buildHomeLiveRadioStations(
                            favorites = onlineRadioState.favoriteStations,
                            catalog = onlineRadioState.stations,
                            limit = 10,
                        )
                    }
                    RadioStationsSection(
                        stations = homeRadioStations,
                        favoriteIds = onlineRadioState.favoriteIds,
                        currentStationUuid = onlineRadioState.currentStation?.uuid,
                        liveMetadataTitle = onlineRadioState.liveMetadataTitle,
                        isLoading = onlineRadioState.loadingCountries || onlineRadioState.loadingStations,
                        onHeaderClick = {
                            selectedTab = NavTab.RADIO
                            currentScreen = AppScreen.RADIO
                        },
                        onStationClick = { station ->
                            OnlineRadioController.playStation(
                                station = station,
                                queue = homeRadioStations,
                                expandPlayer = true,
                            )
                        },
                    )
                }

                // Generous bottom spacing so bottom content scrolls above floating nav & mini player
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(if (hasMiniPlayer) 190.dp else 130.dp))
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
                        activeMoodCategory = item
                    },
                    onProfileClick = {
                        showSettings = true
                    },
                    onOpenContentSettings = {
                        showSettings = true
                    },
                )
                activeMoodCategory?.let { category ->
                    BackHandler(
                        enabled = currentScreen == AppScreen.BROWSE &&
                            !artistHostController.isOpen &&
                            !MusicPlayerState.expanded,
                    ) {
                        activeMoodCategory = null
                    }
                    AppleMoodCategoryScreen(
                        category = category,
                        hasMiniPlayer = hasMiniPlayer,
                        onBack = { activeMoodCategory = null },
                    )
                }
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
                    onFavoriteArtistClick = { artist ->
                        libraryHostController.openDetail(
                            browseId = artist.browseId,
                            title = artist.title,
                            subtitle = artist.subtitle,
                            thumbnailUrl = artist.thumbnailUrl,
                            type = BrowseType.ARTIST,
                        )
                    },
                    onMenuItemClick = { item ->
                        libraryHostController.openMenu(item.id)
                    },
                    onRecentlyAddedClick = {
                        libraryHostController.openDestination(
                            com.darkxvenom.itune.data.library.LibraryDestination.NEW_RELEASES,
                        )
                    },
                    onNewReleaseClick = { release ->
                        libraryHostController.openDetail(
                            browseId = release.browseId,
                            title = release.title,
                            subtitle = release.subtitleLine,
                            thumbnailUrl = release.thumbnailUrl,
                            type = BrowseType.ALBUM,
                        )
                    },
                    onProfileClick = {
                        showSettings = true
                    }
                )
                if (libraryHostController.isOpen) {
                    BackHandler(
                        enabled = currentScreen == AppScreen.LIBRARY &&
                            !artistHostController.isOpen &&
                            !MusicPlayerState.expanded,
                    ) {
                        libraryHostController.pop()
                    }
                    LibraryScreenOverlayContent(
                        controller = libraryHostController,
                        hasMiniPlayer = hasMiniPlayer,
                        onExploreClick = {
                            libraryHostController.clearAll()
                            currentScreen = AppScreen.SEARCH
                        },
                    )
                }
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
                    onCategoryClick = {},
                    onProfileClick = {
                        showSettings = true
                    }
                )
            }
        }

        // SCREEN 5: ONLINE FM RADIO SCREEN (Retained)
        if (hasVisitedRadio || currentScreen == AppScreen.RADIO) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (currentScreen == AppScreen.RADIO) 1f else 0f
                        translationY = if (currentScreen == AppScreen.RADIO) 0f else 99999f
                    }
            ) {
                OnlineRadioScreen(
                    onProfileClick = {
                        showSettings = true
                    }
                )
            }
        }

        // iTube Artist / Album / Playlist Detail Screen Overlay (preserves Home/Search/Player beneath)
        if (artistHostController.isOpen) {
            ArtistScreenOverlayContent(
                controller = artistHostController,
                hasMiniPlayer = hasMiniPlayer,
            )
        }

        // Apple iOS-styled Dark Liquid Glass Settings Screen Overlay
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
        ) {
            BackHandler(
                enabled = showSettings && !showIShare && !showDynamicIsland && !showAvatarDialog && !MusicPlayerState.expanded,
            ) {
                showSettings = false
            }
            AppleSettingsScreen(
                hasMiniPlayer = hasMiniPlayer,
                onBack = { showSettings = false },
                onAccountClick = { showAvatarDialog = true },
                onOpenIShare = { showIShare = true },
                onOpenDynamicIsland = { showDynamicIsland = true },
            )
        }

        // Apple iPhone SharePlay-styled Listen Together ("iShare") Screen Overlay
        AnimatedVisibility(
            visible = showIShare,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
        ) {
            BackHandler(enabled = showIShare && !showAvatarDialog && !MusicPlayerState.expanded) {
                showIShare = false
            }
            IShareScreen(
                hasMiniPlayer = hasMiniPlayer,
                onBack = { showIShare = false },
            )
        }

        // Dynamic Island Calibration & Water-Bubble Settings Overlay
        AnimatedVisibility(
            visible = showDynamicIsland,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 8 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 },
        ) {
            BackHandler(enabled = showDynamicIsland && !showAvatarDialog && !MusicPlayerState.expanded) {
                showDynamicIsland = false
            }
            DynamicIslandScreen(
                hasMiniPlayer = hasMiniPlayer,
                onBack = { showDynamicIsland = false },
            )
        }

        // Apple Dark Mode Gradient Scrim for Floating Bottom Controls
        if (
            !artistHostController.isOpen &&
            !showSettings &&
            !showIShare &&
            !showDynamicIsland &&
            !showHomeGoogleLogin &&
            !(currentScreen == AppScreen.LIBRARY && libraryHostController.isOpen) &&
            !(currentScreen == AppScreen.BROWSE && activeMoodCategory != null)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(if (hasMiniPlayer) 180.dp else 120.dp)
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
        }

        // Floating Bottom Controls: Separate FM Radio Mini-Player OR Music Mini-Player + Navigation Dock
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val p = maxOf(sheetMorph.progress.value, radioSheetMorph.progress.value)
                    translationY = p * 160.dp.toPx()
                    alpha = (1f - p * 2.5f).coerceIn(0f, 1f)
                }
        ) {
            val realTrack = MusicPlayerState.currentTrack
            if (showRadioMiniPlayer) {
                val view = LocalView.current
                val radioDismissY = remember { Animatable(0f) }
                val radioSwipeX = remember { Animatable(0f) }
                val radioDensity = LocalDensity.current
                val radioDismissThresholdPx = with(radioDensity) { 45.dp.toPx() }
                val radioDismissTargetPx = with(radioDensity) { 160.dp.toPx() }
                val radioSwipeThresholdPx = with(radioDensity) { 64.dp.toPx() }
                val radioMaxSwipePx = with(radioDensity) { 145.dp.toPx() }
                val radioSwipeFraction = (abs(radioSwipeX.value) / radioMaxSwipePx).coerceIn(0f, 1f)

                LaunchedEffect(onlineRadioState.currentStation?.uuid) {
                    radioDismissY.snapTo(0f)
                    radioSwipeX.snapTo(0f)
                }

                FloatingRadioMiniPlayer(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val root = rootBoxCoords
                                if (root != null && root.isAttached) {
                                    val pos = root.localPositionOf(coords, Offset.Zero)
                                    if (pos.y > 0f) {
                                        radioMiniPlayerSlotTopPx = pos.y
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            translationY = radioDismissY.value
                            translationX = radioSwipeX.value
                            alpha = (1f - (radioDismissY.value / radioDismissTargetPx)).coerceIn(0f, 1f) *
                                (1f - radioSwipeFraction * 0.55f)
                            scaleX = 1f - radioSwipeFraction * 0.03f
                            scaleY = 1f - radioSwipeFraction * 0.03f
                        }
                        .miniPlayerGestures(
                            morph = radioSheetMorph,
                            dismissOffset = radioDismissY,
                            swipeOffsetX = radioSwipeX,
                            scope = coroutineScope,
                            dismissThresholdPx = radioDismissThresholdPx,
                            dismissTargetPx = radioDismissTargetPx,
                            swipeThresholdPx = radioSwipeThresholdPx,
                            maxSwipeDistancePx = radioMaxSwipePx,
                            canSkipNext = {
                                (onlineRadioState.stationQueue.ifEmpty { onlineRadioState.stations }).size > 1
                            },
                            canSkipPrevious = {
                                (onlineRadioState.stationQueue.ifEmpty { onlineRadioState.stations }).size > 1
                            },
                            onDismiss = {
                                OnlineRadioController.closeSession()
                            },
                            onSwipeNext = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                OnlineRadioController.skipNextStation()
                            },
                            onSwipePrevious = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                OnlineRadioController.skipPreviousStation()
                            },
                        ),
                    state = onlineRadioState,
                    showEmptyPlaceholder = radioSheetMorph.progress.value > 0.001f,
                    onExpand = {
                        MusicPlayerState.expanded = false
                        OnlineRadioController.setPlayerExpanded(true)
                    },
                    onPlayPause = { OnlineRadioController.togglePlayPause() },
                    onNextStation = { OnlineRadioController.skipNextStation() },
                )
                Spacer(modifier = Modifier.height(2.dp))
            } else if (realTrack != null) {
                val view = LocalView.current
                val miniPlayerDismissY = remember { Animatable(0f) }
                val miniPlayerSwipeX = remember { Animatable(0f) }
                val density = LocalDensity.current
                val dismissThresholdPx = with(density) { 45.dp.toPx() }
                val dismissTargetPx = with(density) { 160.dp.toPx() }
                val swipeThresholdPx = with(density) { 64.dp.toPx() }
                val maxSwipeDistancePx = with(density) { 145.dp.toPx() }
                val swipeFraction = (abs(miniPlayerSwipeX.value) / maxSwipeDistancePx).coerceIn(0f, 1f)

                LaunchedEffect(realTrack.id) {
                    miniPlayerDismissY.snapTo(0f)
                    miniPlayerSwipeX.snapTo(0f)
                }

                FloatingMiniPlayer(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val root = rootBoxCoords
                                if (root != null && root.isAttached) {
                                    val pos = root.localPositionOf(coords, Offset.Zero)
                                    if (pos.y > 0f) {
                                        miniPlayerSlotTopPx = pos.y
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            translationY = miniPlayerDismissY.value
                            translationX = miniPlayerSwipeX.value
                            alpha = (1f - (miniPlayerDismissY.value / dismissTargetPx)).coerceIn(0f, 1f) *
                                (1f - swipeFraction * 0.55f)
                            scaleX = 1f - swipeFraction * 0.03f
                            scaleY = 1f - swipeFraction * 0.03f
                        }
                        .miniPlayerGestures(
                            morph = sheetMorph,
                            dismissOffset = miniPlayerDismissY,
                            swipeOffsetX = miniPlayerSwipeX,
                            scope = coroutineScope,
                            dismissThresholdPx = dismissThresholdPx,
                            dismissTargetPx = dismissTargetPx,
                            swipeThresholdPx = swipeThresholdPx,
                            maxSwipeDistancePx = maxSwipeDistancePx,
                            canSkipNext = { MusicPlayerState.hasNextTrack() },
                            canSkipPrevious = { MusicPlayerState.hasPreviousTrack() },
                            onDismiss = {
                                MusicPlayerState.close()
                            },
                            onSwipeNext = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                MusicPlayerState.skipNext()
                            },
                            onSwipePrevious = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                MusicPlayerState.skipPrevious(forcePrevious = true)
                            }
                        ),
                    currentTrack = currentTrack,
                    realTrack = realTrack,
                    isPlaying = MusicPlayerState.isPlaying,
                    showEmptyPlaceholder = sheetMorph.progress.value > 0.001f,
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

                Spacer(modifier = Modifier.height(2.dp))
            }

            FloatingNavBar(
                selectedTab = when (currentScreen) {
                    AppScreen.HOME -> selectedTab
                    AppScreen.BROWSE -> NavTab.NEW
                    AppScreen.RADIO -> NavTab.RADIO
                    AppScreen.LIBRARY -> NavTab.LIBRARY
                    AppScreen.SEARCH -> null
                },
                isSearchActive = currentScreen == AppScreen.SEARCH && !artistHostController.isOpen && !showSettings && !showIShare && !showDynamicIsland,
                onTabSelected = { tab ->
                    val wasLibraryRootTap =
                        tab == NavTab.LIBRARY &&
                            currentScreen == AppScreen.LIBRARY &&
                            !artistHostController.isOpen &&
                            !showSettings &&
                            !showIShare &&
                            !showDynamicIsland
                    showDynamicIsland = false
                    showIShare = false
                    showSettings = false
                    artistHostController.clearAll()
                    if (wasLibraryRootTap) {
                        libraryHostController.clearAll()
                    }
                    selectedTab = tab
                    currentScreen = when (tab) {
                        NavTab.NEW -> AppScreen.BROWSE
                        NavTab.RADIO -> AppScreen.RADIO
                        NavTab.LIBRARY -> AppScreen.LIBRARY
                        else -> AppScreen.HOME
                    }
                },
                onSearchClick = {
                    val wasArtistOpen = artistHostController.isOpen
                    val wasSettingsOpen = showSettings || showIShare || showDynamicIsland
                    showDynamicIsland = false
                    showIShare = false
                    showSettings = false
                    artistHostController.clearAll()
                    currentScreen = if (currentScreen == AppScreen.SEARCH && !wasArtistOpen && !wasSettingsOpen) {
                        when (selectedTab) {
                            NavTab.NEW -> AppScreen.BROWSE
                            NavTab.RADIO -> AppScreen.RADIO
                            NavTab.LIBRARY -> AppScreen.LIBRARY
                            else -> AppScreen.HOME
                        }
                    } else {
                        AppScreen.SEARCH
                    }
                }
            )
        }

        // iTube Frosted Popups (HeldContextMenu & IosMenuPopover) over bottom bars
        ArtistScreenOverlayPopups(controller = artistHostController)
        if (!artistHostController.isOpen) {
            LibraryScreenOverlayPopups(controller = libraryHostController)
        }

        // Apple iOS-styled Avatar Selection Modal Dialog
        if (showAvatarDialog) {
            AvatarSelectionDialog(
                onDismiss = { showAvatarDialog = false }
            )
        }

        // ── Full Player Sheet with Continuous Morphing Bounds & 1:1 Tip-of-Finger Physics ──
        val realTrack = MusicPlayerState.currentTrack
        if (realTrack != null) {
            BackHandler(enabled = MusicPlayerState.expanded || sheetMorph.progress.value > 0.05f) {
                if (MusicPlayerState.queueOpen) {
                    MusicPlayerState.queueOpen = false
                } else if (MusicPlayerState.lyricsOpen) {
                    MusicPlayerState.lyricsOpen = false
                } else {
                    MusicPlayerState.expanded = false
                }
            }

            val p = sheetMorph.progress.value.coerceIn(0f, 1f)
            if (p > 0.001f) {
                val fallbackMiniTopPx = screenHeightPx - with(density) { (66.dp + 2.dp + 54.dp).toPx() }
                val targetMiniTopPx = if (miniPlayerSlotTopPx > 0f) miniPlayerSlotTopPx else fallbackMiniTopPx

                val curTopPx = lerpFloat(targetMiniTopPx, 0f, p)
                val curLeftPx = lerpFloat(with(density) { 14.dp.toPx() }, 0f, p)
                val curWidthPx = lerpFloat(with(density) { (screenWidthDp - 28.dp).toPx() }, screenWidthPx, p)
                val curHeightPx = lerpFloat(with(density) { 54.dp.toPx() }, screenHeightPx, p)
                val curRadiusDp = lerpDp(27.dp, 0.dp, p).coerceAtLeast(0.dp)
                val curShadowDp = lerpDp(16.dp, 0.dp, p).coerceAtLeast(0.dp)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(curLeftPx.roundToInt(), curTopPx.roundToInt()) }
                        .size(
                            width = with(density) { curWidthPx.toDp() },
                            height = with(density) { curHeightPx.toDp() }
                        )
                        .shadow(
                            elevation = curShadowDp,
                            shape = RoundedCornerShape(curRadiusDp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black
                        )
                        .clip(RoundedCornerShape(curRadiusDp))
                        .background(Color.Black)
                        .sheetMorphDrag(sheetMorph)
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(width = screenWidthDp, height = screenHeightDp)
                    ) {
                        ITuneMusicPlayer(
                            sheetProgress = p,
                            onCollapse = {
                                MusicPlayerState.expanded = false
                            },
                            onOpenIShare = {
                                showSettings = false
                                showIShare = true
                                MusicPlayerState.expanded = false
                            },
                            onOpenIRecognition = {
                                showIRecognition = true
                            },
                        )
                    }
                }
            }
        }

        // Dedicated Separate Apple-Styled Online FM Radio Player with Continuous 1:1 Morphing
        if (onlineRadioState.hasActiveSession && onlineRadioState.currentStation != null) {
            val rp = radioSheetMorph.progress.value.coerceIn(0f, 1f)
            if (rp > 0.001f) {
                val fallbackMiniTopPx = screenHeightPx - with(density) { (66.dp + 2.dp + 54.dp).toPx() }
                val targetMiniTopPx = if (radioMiniPlayerSlotTopPx > 0f) radioMiniPlayerSlotTopPx else fallbackMiniTopPx

                val curTopPx = lerpFloat(targetMiniTopPx, 0f, rp)
                val curLeftPx = lerpFloat(with(density) { 14.dp.toPx() }, 0f, rp)
                val curWidthPx = lerpFloat(with(density) { (screenWidthDp - 28.dp).toPx() }, screenWidthPx, rp)
                val curHeightPx = lerpFloat(with(density) { 54.dp.toPx() }, screenHeightPx, rp)
                val curRadiusDp = lerpDp(27.dp, 0.dp, rp).coerceAtLeast(0.dp)
                val curShadowDp = lerpDp(16.dp, 0.dp, rp).coerceAtLeast(0.dp)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(curLeftPx.roundToInt(), curTopPx.roundToInt()) }
                        .size(
                            width = with(density) { curWidthPx.toDp() },
                            height = with(density) { curHeightPx.toDp() },
                        )
                        .shadow(
                            elevation = curShadowDp,
                            shape = RoundedCornerShape(curRadiusDp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        )
                        .clip(RoundedCornerShape(curRadiusDp))
                        .background(Color.Black)
                        .sheetMorphDrag(radioSheetMorph),
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(width = screenWidthDp, height = screenHeightDp),
                    ) {
                        AppleRadioPlayerOverlay(
                            state = onlineRadioState,
                            sheetProgress = rp,
                            onCollapse = { OnlineRadioController.setPlayerExpanded(false) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showHomeGoogleLogin,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 6 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 6 },
        ) {
            GoogleLoginScreen(
                mode = WebSessionMode.SIGN_IN,
                initialCookie = null,
                onSignedIn = {
                    showHomeGoogleLogin = false
                    Toast.makeText(context, "Google account connected", Toast.LENGTH_SHORT).show()
                },
                onClose = { showHomeGoogleLogin = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = showIRecognition,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            IRecognitionScreen(
                onClose = { showIRecognition = false },
            )
        }
    }
}
