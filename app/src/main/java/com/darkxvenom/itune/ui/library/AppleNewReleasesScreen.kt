package com.darkxvenom.itune.ui.library

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.NewReleaseAlbum
import com.darkxvenom.itune.backend.ReleaseType
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.LibraryDestination
import com.darkxvenom.itune.data.library.NewReleasesRepository
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.artist.components.ShimmerBox
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.components.TopSpringLoadingBar
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

private enum class NewReleaseFilter(val label: String) {
    ALL("All"),
    ALBUMS("Albums"),
    SINGLES("Singles"),
    EPS("EPs"),
}

/**
 * Full-screen Apple-styled destination showcasing AirBeats' New Releases catalog
 * (Spotlight carousel + Albums, Singles, and EPs sections) with direct Album/Single navigation.
 */
@Composable
fun AppleNewReleasesScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
) {
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val localeSignature = remember(settingsState.contentLanguage, settingsState.contentCountry) {
        SettingsStore.localeSignature()
    }
    val releases by NewReleasesRepository.releases.collectAsStateWithLifecycle()
    val isLoading by NewReleasesRepository.isLoading.collectAsStateWithLifecycle()
    val errorMessage by NewReleasesRepository.errorMessage.collectAsStateWithLifecycle()

    LaunchedEffect(localeSignature) {
        NewReleasesRepository.ensureLoaded()
    }

    var selectedFilter by remember { mutableStateOf(NewReleaseFilter.ALL) }
    val searchQuery = controller.searchQuery.trim()

    val filteredReleases = remember(releases, searchQuery, settingsState.hideExplicitContent) {
        val visible = ContentLocaleCatalog.filterVisible(releases, settingsState.hideExplicitContent) {
            it.isExplicit
        }
        if (searchQuery.isEmpty()) visible
        else visible.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val albumsList = remember(filteredReleases) {
        filteredReleases.filter { it.releaseType == ReleaseType.ALBUM }
    }
    val singlesList = remember(filteredReleases) {
        filteredReleases.filter { it.releaseType == ReleaseType.SINGLE }
    }
    val epsList = remember(filteredReleases) {
        filteredReleases.filter { it.releaseType == ReleaseType.EP }
    }
    val spotlightList = remember(filteredReleases) {
        filteredReleases.take(8)
    }

    val gridState = controller.gridStates.getOrPut(LibraryDestination.NEW_RELEASES) { LazyGridState() }
    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 120
        }
    }

    fun openReleaseDetail(release: NewReleaseAlbum) {
        controller.openDetail(
            browseId = release.browseId,
            title = release.title,
            subtitle = release.subtitleLine,
            thumbnailUrl = release.thumbnailUrl,
            type = BrowseType.ALBUM,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryFrostedScreenScaffold(
            controller = controller,
            artworkUrls = emptyList(),
            scrolledTitle = "New Releases",
            isScrolled = isScrolled,
            hasMiniPlayer = hasMiniPlayer,
            fallbackAccent = LibraryCrimsonAccent,
            amoledDark = true,
            onBack = onBack,
            topBarActions = {
                IconButton(onClick = controller::toggleSearch) {
                    Icon(
                        imageVector = ITubeIcons.Search,
                        contentDescription = "Search New Releases",
                        tint = if (controller.searchOpen) LibraryCrimsonAccent else Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
                IconButton(onClick = { NewReleasesRepository.refresh() }) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh New Releases",
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                }
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
                item(span = { GridItemSpan(2) }, key = "nr_header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val regionLabel = ContentLocaleCatalog.activeRegionSummary(
                            storedCountry = settingsState.contentCountry,
                            storedLanguage = settingsState.contentLanguage,
                            hideExplicit = settingsState.hideExplicitContent,
                        )
                        val summaryLine = if (filteredReleases.isEmpty()) {
                            regionLabel
                        } else {
                            val counts = buildList {
                                if (albumsList.isNotEmpty()) add("${albumsList.size} Albums")
                                if (singlesList.isNotEmpty()) add("${singlesList.size} Singles")
                                if (epsList.isNotEmpty()) add("${epsList.size} EPs")
                            }.joinToString(" • ").ifBlank { "${filteredReleases.size} Releases" }
                            "$regionLabel • $counts"
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "New Releases",
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.6).sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = summaryLine,
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 13.5.sp,
                            )
                        }

                        LibraryFilterBar(
                            visible = controller.searchOpen,
                            query = controller.searchQuery,
                            onQueryChange = { controller.searchQuery = it },
                            onClose = controller::toggleSearch,
                            autoFocus = controller.focusSearchNext,
                            onFocused = { controller.focusSearchNext = false },
                            placeholder = "Filter albums, singles & EPs...",
                        )
                    }
                }

                if (isLoading && releases.isEmpty()) {
                    item(span = { GridItemSpan(2) }, key = "nr_skeleton") {
                        NewReleasesSkeletonGrid()
                    }
                } else if (releases.isEmpty()) {
                    item(span = { GridItemSpan(2) }, key = "nr_error_empty") {
                        Spacer(Modifier.height(8.dp))
                        LibraryGlassEmptyCard(
                            icon = Icons.Rounded.Album,
                            title = "Couldn't Load New Releases",
                            subtitle = errorMessage ?: "Check your internet connection and tap Retry to fetch the latest albums and singles.",
                            accent = LibraryCrimsonAccent,
                            actionLabel = "Retry",
                            onAction = { NewReleasesRepository.refresh() },
                        )
                    }
                } else {
                    if (selectedFilter == NewReleaseFilter.ALL && searchQuery.isEmpty() && spotlightList.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }, key = "nr_spotlight_header") {
                            NewReleaseSectionHeaderRow(
                                title = "Featured Releases",
                                count = spotlightList.size,
                            )
                        }
                        item(span = { GridItemSpan(2) }, key = "nr_spotlight_row") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(end = 4.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(spotlightList, key = { "spot_${it.browseId}" }) { item ->
                                    AppleSpotlightReleaseCard(
                                        release = item,
                                        onClick = { openReleaseDetail(item) },
                                    )
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(2) }, key = "nr_filter_bar") {
                        AppleNewReleaseSegmentedBar(
                            selected = selectedFilter,
                            onSelect = { selectedFilter = it },
                        )
                    }

                    if (filteredReleases.isEmpty()) {
                        item(span = { GridItemSpan(2) }, key = "nr_search_empty") {
                            LibraryGlassEmptyCard(
                                icon = ITubeIcons.Search,
                                title = "No Matching Releases",
                                subtitle = "No new releases matched \"$searchQuery\".",
                                accent = LibraryCrimsonAccent,
                                actionLabel = "Clear Filter",
                                onAction = { controller.searchQuery = "" },
                            )
                        }
                    } else {
                        val showAlbums = selectedFilter == NewReleaseFilter.ALL || selectedFilter == NewReleaseFilter.ALBUMS
                        val showSingles = selectedFilter == NewReleaseFilter.ALL || selectedFilter == NewReleaseFilter.SINGLES
                        val showEps = selectedFilter == NewReleaseFilter.ALL || selectedFilter == NewReleaseFilter.EPS

                        if (showAlbums && albumsList.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }, key = "nr_hdr_albums") {
                                NewReleaseSectionHeaderRow(title = "Albums", count = albumsList.size)
                            }
                            items(albumsList, key = { "alb_${it.browseId}" }) { item ->
                                AppleReleaseGridCard(
                                    release = item,
                                    onClick = { openReleaseDetail(item) },
                                )
                            }
                        } else if (selectedFilter == NewReleaseFilter.ALBUMS) {
                            item(span = { GridItemSpan(2) }, key = "nr_empty_albums") {
                                FilterCategoryEmptyNotice(label = "albums")
                            }
                        }

                        if (showSingles && singlesList.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }, key = "nr_hdr_singles") {
                                NewReleaseSectionHeaderRow(title = "Singles", count = singlesList.size)
                            }
                            items(singlesList, key = { "sng_${it.browseId}" }) { item ->
                                AppleReleaseGridCard(
                                    release = item,
                                    onClick = { openReleaseDetail(item) },
                                )
                            }
                        } else if (selectedFilter == NewReleaseFilter.SINGLES) {
                            item(span = { GridItemSpan(2) }, key = "nr_empty_singles") {
                                FilterCategoryEmptyNotice(label = "singles")
                            }
                        }

                        if (showEps && epsList.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }, key = "nr_hdr_eps") {
                                NewReleaseSectionHeaderRow(title = "EPs", count = epsList.size)
                            }
                            items(epsList, key = { "ep_${it.browseId}" }) { item ->
                                AppleReleaseGridCard(
                                    release = item,
                                    onClick = { openReleaseDetail(item) },
                                )
                            }
                        } else if (selectedFilter == NewReleaseFilter.EPS) {
                            item(span = { GridItemSpan(2) }, key = "nr_empty_eps") {
                                FilterCategoryEmptyNotice(label = "EPs")
                            }
                        }
                    }
                }
            }
        }

        TopSpringLoadingBar(
            isVisible = isLoading,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun AppleNewReleaseSegmentedBar(
    selected: NewReleaseFilter,
    onSelect: (NewReleaseFilter) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF1C1C1E))
            .padding(2.5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewReleaseFilter.entries.forEach { option ->
                val isSelected = option == selected
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF38383A) else Color.Transparent,
                    animationSpec = tween(durationMillis = 160),
                    label = "NewReleaseSegBg",
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else AppleTextSecondary,
                    animationSpec = tween(durationMillis = 160),
                    label = "NewReleaseSegTxt",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(7.dp))
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewReleaseSectionHeaderRow(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0x18FFFFFF))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = count.toString(),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AppleSpotlightReleaseCard(
    release: NewReleaseAlbum,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(196.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(196.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1C1C1E))
                .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
        ) {
            AsyncImage(
                model = release.thumbnailUrl?.artworkAt(CARD_ART_PX),
                contentDescription = release.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.56f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = release.releaseType.label.uppercase(),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.title,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (release.isExplicit) {
                Spacer(Modifier.width(5.dp))
                ExplicitBadge(color = Color.White.copy(alpha = 0.65f))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = release.subtitleLine,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppleReleaseGridCard(
    release: NewReleaseAlbum,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1C1C1E))
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
        ) {
            AsyncImage(
                model = release.thumbnailUrl?.artworkAt(CARD_ART_PX),
                contentDescription = release.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.title,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (release.isExplicit) {
                Spacer(Modifier.width(5.dp))
                ExplicitBadge(color = Color.White.copy(alpha = 0.65f))
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = release.subtitleLine,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 12.5.sp,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FilterCategoryEmptyNotice(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No new $label available right now",
            color = AppleTextSecondary,
            fontSize = 14.5.sp,
        )
    }
}

@Composable
private fun NewReleasesSkeletonGrid() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(2) {
                Column(Modifier.width(196.dp)) {
                    ShimmerBox(Modifier.size(196.dp), RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.75f).height(14.dp), RoundedCornerShape(5.dp))
                    Spacer(Modifier.height(5.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.55f).height(11.dp), RoundedCornerShape(5.dp))
                }
            }
        }
        ShimmerBox(Modifier.fillMaxWidth().height(36.dp), RoundedCornerShape(9.dp))
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(2) {
                    Column(Modifier.weight(1f)) {
                        ShimmerBox(Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(8.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.72f).height(14.dp), RoundedCornerShape(5.dp))
                        Spacer(Modifier.height(5.dp))
                        ShimmerBox(Modifier.fillMaxWidth(0.52f).height(11.dp), RoundedCornerShape(5.dp))
                    }
                }
            }
        }
    }
}
