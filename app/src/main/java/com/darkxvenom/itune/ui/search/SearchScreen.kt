package com.darkxvenom.itune.ui.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.UnifiedSearchResults
import com.darkxvenom.itune.data.artist.ArtistRepository
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.library.SearchHistoryStore
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.data.model.SearchCategory
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.data.repository.MusicRepository
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.TopSpringLoadingBar
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SearchUiMode {
    EXPLORE,
    HISTORY,
    SUGGESTIONS,
    RESULTS
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    onCategoryClick: (SearchCategory) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        SearchHistoryStore.ensureInit(context)
        ArtistStateStore.ensureInit(context)
    }

    var searchInput by remember { mutableStateOf(TextFieldValue("")) }
    val searchQuery = searchInput.text
    var submittedQuery by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    var searchResults by remember { mutableStateOf(UnifiedSearchResults()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(SearchResultFilter.ALL) }
    var remoteSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeSearchJob by remember { mutableStateOf<Job?>(null) }
    var activeArtistActionJob by remember { mutableStateOf<Job?>(null) }
    var topResultBusyAction by remember { mutableStateOf<TopResultBusyAction?>(null) }
    var moreRequest by remember { mutableStateOf<SearchMoreRequest?>(null) }

    val searchHistory by SearchHistoryStore.history.collectAsState()
    val savedReleases by ArtistStateStore.savedReleases.collectAsState()

    val uiMode = when {
        isEditing && searchQuery.isBlank() -> SearchUiMode.HISTORY
        isEditing && searchQuery.isNotBlank() -> SearchUiMode.SUGGESTIONS
        submittedQuery.isNotBlank() -> SearchUiMode.RESULTS
        else -> SearchUiMode.EXPLORE
    }

    fun openCollectionDetail(state: SearchDetailState) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        MusicPlayerState.pendingArtistNav = PendingArtistNav(
            browseId = state.browseId,
            name = state.title,
            subtitle = state.subtitle,
            thumbnailUrl = state.thumbnailUrl,
            initialType = state.initialType,
        )
    }

    fun submitSearch(rawQuery: String, recordHistory: Boolean = true) {
        val clean = rawQuery.trim()
        if (clean.isEmpty()) return

        searchInput = TextFieldValue(clean, selection = TextRange(clean.length))
        submittedQuery = clean
        isEditing = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()

        if (recordHistory) {
            SearchHistoryStore.addQuery(context, clean)
        }

        activeSearchJob?.cancel()
        searchResults = UnifiedSearchResults()
        selectedFilter = SearchResultFilter.ALL
        isSearching = true

        lateinit var launched: Job
        launched = coroutineScope.launch {
            gridState.scrollToItem(0)
            try {
                val results = MusicBackend.searchAllCategories(clean)
                if (activeSearchJob == launched) {
                    searchResults = results
                }
            } catch (_: Exception) {
                if (activeSearchJob == launched) {
                    searchResults = UnifiedSearchResults()
                }
            } finally {
                if (activeSearchJob == launched) {
                    isSearching = false
                }
            }
        }
        activeSearchJob = launched
    }

    fun exitSearchToExplore() {
        activeSearchJob?.cancel()
        isSearching = false
        isEditing = false
        submittedQuery = ""
        searchInput = TextFieldValue("")
        searchResults = UnifiedSearchResults()
        selectedFilter = SearchResultFilter.ALL
        remoteSuggestions = emptyList()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun fillQueryForEditing(text: String) {
        searchInput = TextFieldValue(text, selection = TextRange(text.length))
        isEditing = true
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }

    BackHandler(enabled = uiMode != SearchUiMode.EXPLORE) {
        when {
            isEditing && submittedQuery.isNotBlank() -> {
                searchInput = TextFieldValue(
                    submittedQuery,
                    selection = TextRange(submittedQuery.length)
                )
                isEditing = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
            else -> exitSearchToExplore()
        }
    }

    LaunchedEffect(MusicPlayerState.pendingArtistSearch) {
        val artistQuery = MusicPlayerState.pendingArtistSearch
        if (!artistQuery.isNullOrBlank()) {
            MusicPlayerState.pendingArtistSearch = null
            submitSearch(artistQuery)
        }
    }

    // Fast, smooth suggestions fetched only while typing in editing mode
    LaunchedEffect(isEditing, searchQuery) {
        val trimmed = searchQuery.trim()
        if (!isEditing || trimmed.isEmpty()) {
            remoteSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(95)
        remoteSuggestions = runCatching { MusicBackend.searchSuggestions(trimmed) }
            .getOrDefault(emptyList())
    }

    val mergedSuggestions = remember(isEditing, searchQuery, searchHistory, remoteSuggestions) {
        if (!isEditing || searchQuery.isBlank()) {
            emptyList()
        } else {
            val trimmed = searchQuery.trim()
            val seen = HashSet<String>()
            buildList {
                searchHistory
                    .filter { it.contains(trimmed, ignoreCase = true) }
                    .forEach { entry ->
                        if (seen.add(entry.lowercase())) {
                            add(SearchSuggestionRowModel(text = entry, isHistory = true))
                        }
                    }
                remoteSuggestions.forEach { suggestion ->
                    val s = suggestion.trim()
                    if (s.isNotEmpty() && seen.add(s.lowercase())) {
                        add(SearchSuggestionRowModel(text = s, isHistory = false))
                    }
                }
            }.take(12)
        }
    }

    val topResult = remember(submittedQuery, searchResults) {
        if (submittedQuery.isBlank() || searchResults.isEmpty) null
        else pickTopResult(submittedQuery, searchResults)
    }

    val isScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 4
        }
    }

    val topShadeAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "SearchTopShadeAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Stationary Search Header ensures BasicTextField never drops focus across Explore/History/Suggestions/Results
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppleOledBlack)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AnimatedVisibility(
                    visible = uiMode == SearchUiMode.EXPLORE,
                    enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Search",
                                color = AppleTextPrimary,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )

                            AppleMemojiAvatar(
                                size = 38.dp,
                                onClick = onProfileClick
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppleSearchBar(
                        value = searchInput,
                        onValueChange = { next ->
                            searchInput = next
                            if (!isEditing) isEditing = true
                        },
                        focusRequester = searchFocusRequester,
                        onFocused = { focused ->
                            if (focused && !isEditing) {
                                isEditing = true
                            }
                        },
                        onSubmit = { submitSearch(searchQuery) },
                        onClearClick = {
                            searchInput = TextFieldValue("")
                            isEditing = true
                            searchFocusRequester.requestFocus()
                            keyboardController?.show()
                        },
                        onMicClick = {
                            Toast.makeText(context, "Voice Search listening...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )

                    AnimatedVisibility(visible = uiMode != SearchUiMode.EXPLORE) {
                        Text(
                            text = "Cancel",
                            color = Color(0xFFFA2D48),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { exitSearchToExplore() }
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .springOverscroll(),
                contentPadding = PaddingValues(top = 2.dp, bottom = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                when (uiMode) {
                    SearchUiMode.EXPLORE -> {
                        item(span = { GridItemSpan(2) }, key = "explore_categories_header") {
                            Text(
                                text = "Explore Categories",
                                color = AppleTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 6.dp,
                                    bottom = 10.dp
                                )
                            )
                        }
                        itemsIndexed(
                            items = MusicRepository.searchCategories,
                            key = { _, category -> category.id }
                        ) { index, category ->
                            val horizontalPad = if (index % 2 == 0) {
                                PaddingValues(start = 16.dp, bottom = 12.dp)
                            } else {
                                PaddingValues(end = 16.dp, bottom = 12.dp)
                            }
                            SearchCategoryCardItem(
                                category = category,
                                modifier = Modifier.padding(horizontalPad),
                                onClick = {
                                    onCategoryClick(category)
                                    submitSearch(category.title)
                                }
                            )
                        }
                    }

                    SearchUiMode.HISTORY -> {
                        item(span = { GridItemSpan(2) }, key = "history_header") {
                            SearchHistoryHeaderRow(
                                hasHistory = searchHistory.isNotEmpty(),
                                onClearAll = { SearchHistoryStore.clearAll(context) }
                            )
                        }
                        if (searchHistory.isEmpty()) {
                            item(span = { GridItemSpan(2) }, key = "history_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Search songs, artists, albums, or playlists",
                                        color = AppleTextSecondary,
                                        fontSize = 14.5.sp
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(
                                items = searchHistory,
                                key = { _, q -> "history_${q.lowercase()}" },
                                span = { _, _ -> GridItemSpan(2) }
                            ) { idx, queryItem ->
                                SearchQuerySuggestionRow(
                                    text = queryItem,
                                    highlightQuery = "",
                                    isHistory = true,
                                    showBottomDivider = idx < searchHistory.lastIndex,
                                    onSelect = { submitSearch(queryItem) },
                                    onFillQuery = { fillQueryForEditing(queryItem) },
                                    onRemoveHistory = {
                                        SearchHistoryStore.removeQuery(context, queryItem)
                                    }
                                )
                            }
                        }
                    }

                    SearchUiMode.SUGGESTIONS -> {
                        if (mergedSuggestions.isEmpty()) {
                            item(span = { GridItemSpan(2) }, key = "suggestion_direct_search") {
                                SearchQuerySuggestionRow(
                                    text = searchQuery.trim(),
                                    highlightQuery = searchQuery.trim(),
                                    isHistory = false,
                                    showBottomDivider = false,
                                    onSelect = { submitSearch(searchQuery) },
                                    onFillQuery = { fillQueryForEditing(searchQuery.trim()) }
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = mergedSuggestions,
                                key = { idx, item -> "sugg_${item.text.lowercase()}_$idx" },
                                span = { _, _ -> GridItemSpan(2) }
                            ) { idx, item ->
                                SearchQuerySuggestionRow(
                                    text = item.text,
                                    highlightQuery = searchQuery.trim(),
                                    isHistory = item.isHistory,
                                    showBottomDivider = idx < mergedSuggestions.lastIndex,
                                    onSelect = { submitSearch(item.text) },
                                    onFillQuery = { fillQueryForEditing(item.text) }
                                )
                            }
                        }
                    }

                    SearchUiMode.RESULTS -> {
                        if (isSearching && searchResults.isEmpty) {
                            item(span = { GridItemSpan(2) }, key = "search_results_skeleton") {
                                SearchResultsSkeletonBlock()
                            }
                        } else if (!isSearching && searchResults.isEmpty) {
                            item(span = { GridItemSpan(2) }, key = "search_empty") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No results found for \"$submittedQuery\"",
                                        color = AppleTextSecondary,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        } else {
                            val showSongs = selectedFilter == SearchResultFilter.ALL || selectedFilter == SearchResultFilter.SONGS
                            val showArtists = selectedFilter == SearchResultFilter.ALL || selectedFilter == SearchResultFilter.ARTISTS
                            val showAlbums = selectedFilter == SearchResultFilter.ALL || selectedFilter == SearchResultFilter.ALBUMS
                            val showPlaylists = selectedFilter == SearchResultFilter.ALL || selectedFilter == SearchResultFilter.PLAYLISTS


                        } else Unit
                    }
                }
            }
        }
                            AppleOledBlack.copy(alpha = 0.65f),
                            AppleOledBlack.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        AppleSearchMorePopup(
            request = moreRequest,
            onOpenDetail = ::openCollectionDetail,
            onPlayCollection = { browseId, title, _, thumb ->
                coroutineScope.launch {
                    val loaded = MusicBackend.loadCollectionTracks(
                        browseId = browseId,
                        fallbackArtist = title,
                        fallbackThumbnailUrl = thumb
                    )
                    if (loaded.isNotEmpty()) {
                        MusicPlayerState.play(loaded.first(), loaded)
                    } else {
                        Toast.makeText(context, "No tracks found", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onQueueCollection = { browseId, title, _, thumb ->
                coroutineScope.launch {
                    val loaded = MusicBackend.loadCollectionTracks(
                        browseId = browseId,
                        fallbackArtist = title,
                        fallbackThumbnailUrl = thumb
                    )
                    if (loaded.isNotEmpty()) {
                        MusicPlayerState.addToQueue(loaded)
                        Toast.makeText(context, "Added ${loaded.size} tracks to queue", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No tracks found", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { moreRequest = null }
        )
    }
}

@Composable
fun AppleSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()

@Composable
fun AppleSearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester,
    onFocused: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClearClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusRequester.requestFocus()
                keyboardController?.show()
                onFocused(true)
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = AppleTextSecondary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        text = "Artists, Songs, Lyrics & More",
                        color = AppleTextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(Color(0xFFFA2D48)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { state -> onFocused(state.isFocused) }
                )
            }

            if (value.text.isNotEmpty()) {
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = AppleTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice search",
                        tint = AppleTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SearchCategoryCardItem(
    category: SearchCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.765f)
            .clip(RoundedCornerShape(14.dp))
            .background(category.backgroundColor)
            .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        CategoryCardArtwork(
            type = category.type,
            modifier = Modifier.fillMaxSize()
        )

        if (!category.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = category.imageUrl,
                contentDescription = category.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.08f),
                            0.42f to category.backgroundColor.copy(alpha = 0.24f),
                            1.00f to Color.Black.copy(alpha = 0.78f),
                        )
                    )
            )
        }

        Text(
            text = category.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 10.dp, end = 12.dp)
        )
    }
}
