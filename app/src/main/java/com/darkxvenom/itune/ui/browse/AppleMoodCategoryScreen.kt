package com.darkxvenom.itune.ui.browse

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.MoodCategoryItem
import com.darkxvenom.itune.backend.MoodCategoryItemType
import com.darkxvenom.itune.backend.MoodCategoryPage
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.model.BrowseCategoryItem
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.LocalAppBackdrop
import com.darkxvenom.itune.ui.artist.components.LocalLiquidGlassEnabled
import com.darkxvenom.itune.ui.artist.components.LocalReduceDynamicBlur
import com.darkxvenom.itune.ui.artist.components.ShimmerBox
import com.darkxvenom.itune.ui.artist.components.SongRowSkeleton
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.layerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberLayerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberSnapshotBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.snapshotBackdropSource
import com.darkxvenom.itune.ui.artist.components.isGlassSupported
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.components.TopSpringLoadingBar
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.library.LibraryCrimsonAccent
import com.darkxvenom.itune.ui.library.LibraryFilterBar
import com.darkxvenom.itune.ui.library.LibraryGlassEmptyCard
import com.darkxvenom.itune.ui.library.LibraryGlassPillButton
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * Individual Apple-UI-styled screen opened when tapping any of the 12 Moods & Moments cards
 * on Browse/New, fetching curated sections from YouTube Music InnerTube (`FEmusic_moods_and_genres_category`).
 */
@Composable
fun AppleMoodCategoryScreen(
    category: BrowseCategoryItem,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val pageBackdrop = rememberSnapshotBackdrop()
    val appBackdrop = rememberLayerBackdrop()
    val glassActive = isGlassSupported()

    var page by remember(category.id, category.params) { mutableStateOf<MoodCategoryPage?>(null) }
    var isLoading by remember(category.id, category.params) { mutableStateOf(true) }
    var errorMessage by remember(category.id, category.params) { mutableStateOf<String?>(null) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var searchOpen by remember(category.id) { mutableStateOf(false) }
    var searchQuery by remember(category.id) { mutableStateOf("") }
    var focusSearchNext by remember { mutableStateOf(false) }
    var isPlayingMix by remember { mutableStateOf(false) }

    LaunchedEffect(category.browseId, category.params, refreshNonce) {
        isLoading = true
        errorMessage = null
        try {
            val loaded = MusicBackend.fetchMoodCategoryPage(
                browseId = category.browseId,
                params = category.params,
                fallbackTitle = category.title,
                forceRefresh = refreshNonce > 0,
            )
            page = loaded
            if (loaded.sections.isEmpty()) {
                errorMessage = "No curated content found for ${category.title}"
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Couldn't load ${category.title}"
        } finally {
            isLoading = false
        }
    }

    val query = searchQuery.trim()
    val filteredSections = remember(page, query) {
        val sections = page?.sections.orEmpty()
        if (query.isEmpty()) sections
        else sections.mapNotNull { sec ->
            val matching = sec.items.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.subtitle.contains(query, ignoreCase = true)
            }
            if (matching.isNotEmpty() || sec.title.contains(query, ignoreCase = true)) {
                sec.copy(items = if (matching.isNotEmpty()) matching else sec.items)
            } else {
                null
            }
        }
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120
        }
    }

    val displayTitle = page?.title?.takeIf { it.isNotBlank() } ?: category.title

    fun openMoodItem(item: MoodCategoryItem, sectionSongs: List<MoodCategoryItem>) {
        when (item.type) {
            MoodCategoryItemType.SONG -> {
                val track = MusicTrack(
                    id = item.videoId ?: return,
                    title = item.title,
                    artists = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                )
                val queue = sectionSongs.mapNotNull { s ->
                    s.videoId?.let { vid ->
                        MusicTrack(
                            id = vid,
                            title = s.title,
                            artists = s.subtitle,
                            thumbnailUrl = s.thumbnailUrl,
                        )
                    }
                }.ifEmpty { listOf(track) }
                MusicPlayerState.play(track, queue, expandPlayer = true)
            }
            MoodCategoryItemType.ALBUM,
            MoodCategoryItemType.PLAYLIST,
            MoodCategoryItemType.ARTIST -> {
                val navId = item.browseId ?: return
                val navType = when (item.type) {
                    MoodCategoryItemType.ALBUM -> BrowseType.ALBUM
                    MoodCategoryItemType.ARTIST -> BrowseType.ARTIST
                    else -> BrowseType.PLAYLIST
                }
                MusicPlayerState.pendingArtistNav = PendingArtistNav(
                    browseId = navId,
                    name = item.title,
                    subtitle = item.subtitle,
                    thumbnailUrl = item.thumbnailUrl,
                    initialType = navType,
                )
            }
        }
    }

    fun startMoodPlayback(shuffled: Boolean) {
        if (isPlayingMix) return
        val allItems = page?.sections.orEmpty().flatMap { it.items }
        if (allItems.isEmpty()) return
        val collections = allItems.filter { it.browseId != null && it.type != MoodCategoryItemType.ARTIST }
        val targetCollection = if (shuffled && collections.size > 1) collections.random() else collections.firstOrNull()
        val songs = allItems.filter { it.type == MoodCategoryItemType.SONG && it.videoId != null }

        if (targetCollection?.browseId != null) {
            isPlayingMix = true
            scope.launch {
                try {
                    val loaded = MusicBackend.loadCollectionTracks(
                        browseId = targetCollection.browseId,
                        fallbackArtist = targetCollection.title,
                        fallbackThumbnailUrl = targetCollection.thumbnailUrl,
                    )
                    if (loaded.isNotEmpty()) {
                        val queue = if (shuffled) loaded.shuffled() else loaded
                        MusicPlayerState.play(queue.first(), queue, expandPlayer = false)
                    } else {
                        Toast.makeText(context, "Couldn't load tracks", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    isPlayingMix = false
                }
            }
        } else if (songs.isNotEmpty()) {
            val mapped = songs.mapNotNull { s ->
                s.videoId?.let {
                    MusicTrack(id = it, title = s.title, artists = s.subtitle, thumbnailUrl = s.thumbnailUrl)
                }
            }
            val queue = if (shuffled) mapped.shuffled() else mapped
            if (queue.isNotEmpty()) {
                MusicPlayerState.play(queue.first(), queue, expandPlayer = false)
            }
        }
    }

    ITubeArtistTheme(darkTheme = true) {
        val artworkPalette = rememberArtworkPalette(imageUrl = category.imageUrl, dark = true)
        val topGlowColor = artworkPalette.accent.copy(alpha = 0.22f)
        val topPad = topBarContentPadding()
        val bottomPad = if (hasMiniPlayer) 210.dp else 140.dp

        CompositionLocalProvider(
            LocalAppBackdrop provides appBackdrop,
            LocalLiquidGlassEnabled provides glassActive,
            LocalReduceDynamicBlur provides false,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(AppleOledBlack),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .snapshotBackdropSource(pageBackdrop, floor = AppleOledBlack)
                        .hazeSource(state = hazeState),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(AppleOledBlack)
                            .layerBackdrop(appBackdrop),
                    )

                    // Subtle top radial tint extracted from the category's 3D sculpture artwork
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(topGlowColor, Color.Transparent),
                                    center = Offset(240f, 160f),
                                    radius = 860f,
                                ),
                            ),
                    )

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = topPad + 8.dp,
                            bottom = bottomPad,
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .springOverscroll(),
                    ) {
                        item(key = "mood_hero") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = category.imageUrl,
                                        contentDescription = displayTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(86.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF1C1C1E))
                                            .border(
                                                0.8.dp,
                                                Color.White.copy(alpha = 0.16f),
                                                RoundedCornerShape(16.dp),
                                            ),
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "MOODS & MOMENTS",
                                            color = artworkPalette.accent,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = displayTitle,
                                            color = Color.White,
                                            fontSize = 30.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = (-0.6).sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        val sectionCount = page?.sections?.size ?: 0
                                        Text(
                                            text = if (sectionCount > 0) {
                                                "$sectionCount curated collections"
                                            } else {
                                                "Curated playlists & highlights"
                                            },
                                            color = Color.White.copy(alpha = 0.62f),
                                            fontSize = 13.sp,
                                        )
                                    }
                                }

                                LibraryFilterBar(
                                    visible = searchOpen,
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onClose = {
                                        searchOpen = false
                                        searchQuery = ""
                                    },
                                    autoFocus = focusSearchNext,
                                    onFocused = { focusSearchNext = false },
                                    placeholder = "Filter in $displayTitle...",
                                )

                                if (!page?.sections.isNullOrEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        LibraryGlassPillButton(
                                            icon = ITubeIcons.Play,
                                            label = if (isPlayingMix) "Loading..." else "Play Mix",
                                            onClick = { startMoodPlayback(shuffled = false) },
                                            filled = true,
                                            height = 44.dp,
                                            enabled = !isPlayingMix,
                                            modifier = Modifier.weight(1f),
                                        )
                                        LibraryGlassPillButton(
                                            icon = ITubeIcons.Shuffle,
                                            label = "Shuffle",
                                            onClick = { startMoodPlayback(shuffled = true) },
                                            filled = false,
                                            height = 44.dp,
                                            enabled = !isPlayingMix,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }

                        if (isLoading && page == null) {
                            item(key = "mood_loading_skel") {
                                MoodCategoryLoadingSkeleton()
                            }
                        } else if (page?.sections.isNullOrEmpty()) {
                            item(key = "mood_empty_error") {
                                Spacer(Modifier.height(22.dp))
                                LibraryGlassEmptyCard(
                                    icon = Icons.Rounded.MusicNote,
