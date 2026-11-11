package com.darkxvenom.itune.ui.radio

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.R
import com.darkxvenom.itune.radio.OnlineRadioController
import com.darkxvenom.itune.radio.OnlineRadioStation
import com.darkxvenom.itune.radio.RADIO_ALL_CITIES
import com.darkxvenom.itune.ui.artist.components.ShimmerBox
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.components.AppleMemojiAvatar
import com.darkxvenom.itune.ui.components.rememberSpringOverscrollState
import com.darkxvenom.itune.ui.components.springOverscroll
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged

private val RadioGroupSurface = Color(0xFF1C1C1E)
private val RadioModalSurface = Color(0xFF1C1C1E)
private val RadioGroupBorder = Color.White.copy(alpha = 0.08f)
private val RadioDividerColor = Color.White.copy(alpha = 0.08f)

/**
 * Online FM Radio Screen styled cleanly in the exact iOS Apple Settings grouped-card UI:
 * Step 1: Pick Country -> Step 2: Pick City/State -> Step 3: Browse & play live FM stations.
 */
@Composable
fun OnlineRadioScreen(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        OnlineRadioController.ensureInit(context)
        OnlineRadioController.ensureCatalogLoaded()
    }

    val state by OnlineRadioController.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val overscrollState = rememberSpringOverscrollState()

    var showCountryPicker by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }

    val cleanQuery = state.searchQuery.trim()
    val visibleStations = remember(state.stations, state.searchResults, cleanQuery) {
        when {
            cleanQuery.length >= 2 -> state.searchResults
            cleanQuery.length == 1 -> state.stations.filter { st ->
                st.name.contains(cleanQuery, ignoreCase = true) ||
                    st.cityState.contains(cleanQuery, ignoreCase = true) ||
                    st.tags.any { it.contains(cleanQuery, ignoreCase = true) }
            }
            else -> state.stations
        }
    }

    // Automatically paginate ALL remaining stations when user approaches the bottom (handles 10,000+ smoothly)
    LaunchedEffect(listState, state.hasMoreStations, cleanQuery) {
        if (cleanQuery.isNotEmpty() || !state.hasMoreStations) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, totalCount) ->
                if (totalCount > 10 && lastVisible >= totalCount - 12) {
                    OnlineRadioController.loadMoreStations()
                }
            }
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .springOverscroll(overscrollState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp),
        ) {
            item(key = "radio_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, bottom = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Radio",
                            color = AppleTextPrimary,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RadioGroupSurface)
                                    .border(0.6.dp, RadioGroupBorder, CircleShape)
                                    .clickable { OnlineRadioController.refreshCurrentRegion() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh Stations",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            AppleMemojiAvatar(
                                size = 38.dp,
                                onClick = onProfileClick,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // iOS Settings-style search pill (searches loaded & server-side 10,000+ catalog)
                    SettingsStyleSearchField(
                        query = state.searchQuery,
                        placeholder = "Search FM stations, tags, or cities",
                        onQueryChange = { OnlineRadioController.onSearchQueryChanged(it) },
                    )
                }
            }

            // Section 1: Country & City Selector (Settings Grouped Card UI)
            item(key = "country_city_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp),
                ) {
                    SettingsSectionHeaderText("STATION LOCATION")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RadioGroupSurface)
                            .border(0.5.dp, RadioGroupBorder, RoundedCornerShape(16.dp)),
                    ) {
                        SettingsLocationPickerRow(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            title = "Country",
                            subtitle = "Choose country to browse online FM",
                            trailingValue = when {
                                state.loadingCountries -> "Loading..."
                                state.selectedCountry != null -> state.selectedCountry!!.name
                                else -> "Select Country"
                            },
                            onClick = { showCountryPicker = true },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 52.dp),
                            thickness = 0.5.dp,
                            color = RadioDividerColor,
                        )
                        SettingsLocationPickerRow(
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.LocationCity,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            title = "City / Region",
                            subtitle = "Filter FM transmitters by city or state",
                            trailingValue = when {
                                state.loadingCities -> "Loading..."
                                state.selectedCity == RADIO_ALL_CITIES -> "All Cities"
                                else -> state.selectedCity
                            },
                            onClick = { showCityPicker = true },
                        )
                    }
                }
            }

            // Section 2: Favorite Stations (Virtualized inside LazyColumn)
            if (state.favoriteStations.isNotEmpty() && cleanQuery.isEmpty()) {
                item(key = "favorites_header") {
                    SettingsSectionHeaderText("FAVORITE STATIONS (${state.favoriteStations.size})")
                }
                itemsIndexed(
                    items = state.favoriteStations,
                    key = { _, s -> "fav_${s.uuid}" },
                    contentType = { _, _ -> "station_row" },
                ) { idx, station ->
                    val isCurrent = state.currentStation?.uuid == station.uuid
                    GroupedStationCardRowShell(
                        isFirst = idx == 0,
                        isLast = idx == state.favoriteStations.lastIndex,
                        bottomSpacerDp = if (idx == state.favoriteStations.lastIndex) 18.dp else 0.dp,
                    ) {
                        RadioStationGroupedRow(
                            station = station,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && state.isPlaying,
                            isBuffering = isCurrent && state.isBuffering,
                            isFavorite = true,
                            onClick = {
                                OnlineRadioController.playStation(
                                    station = station,
                                    queue = state.favoriteStations,
                                    expandPlayer = true,
                                )
                            },
                            onFavoriteClick = { OnlineRadioController.toggleFavorite(station) },
                        )
                    }
                }
            }

            // Section 3: Virtualized Online FM Stations List (handles 10,000+ rows cleanly at 120fps)
            item(key = "stations_header") {
                val regionTitle = buildString {
                    append("LIVE FM STATIONS")
                    state.selectedCountry?.let { append(" • ${it.name.uppercase()}") }
                    if (state.selectedCity != RADIO_ALL_CITIES) {
                        append(" • ${state.selectedCity.uppercase()}")
                    }
                    if (!state.loadingStations) {
                        if (cleanQuery.isEmpty() && state.hasMoreStations && state.totalRegionStations > visibleStations.size) {
                            append(" (${visibleStations.size} / ${state.totalRegionStations})")
                        } else {
                            append(" (${visibleStations.size})")
                        }
                    }
                }
                SettingsSectionHeaderText(regionTitle)
            }

            when {
                state.loadingCountries || state.loadingStations || (state.searchingStations && visibleStations.isEmpty()) -> {
                    item(key = "stations_loading") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RadioGroupSurface)
                                .border(0.5.dp, RadioGroupBorder, RoundedCornerShape(16.dp)),
                        ) {
                            repeat(6) { idx ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ShimmerBox(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(11.dp)),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        ShimmerBox(
                                            modifier = Modifier
                                                .fillMaxWidth(0.62f)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        ShimmerBox(
                                            modifier = Modifier
                                                .fillMaxWidth(0.42f)
                                                .height(11.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                        )
                                    }
                                }
                                if (idx < 5) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 72.dp),
                                        thickness = 0.5.dp,
                                        color = RadioDividerColor,
                                    )
                                }
                            }
                        }
                    }
                }

                state.errorMessage != null && visibleStations.isEmpty() -> {
                    item(key = "stations_error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RadioGroupSurface)
                                .border(0.5.dp, RadioGroupBorder, RoundedCornerShape(16.dp))
                                .padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Radio,
                                contentDescription = null,
                                tint = AppleMusicRed,
                                modifier = Modifier.size(30.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = state.errorMessage.orEmpty(),
                                color = AppleTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(AppleMusicRed)
                                    .clickable { OnlineRadioController.refreshCurrentRegion() }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "Retry",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }

                visibleStations.isEmpty() -> {
                    item(key = "stations_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(RadioGroupSurface)
                                .border(0.5.dp, RadioGroupBorder, RoundedCornerShape(16.dp))
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (cleanQuery.isNotEmpty()) {
                                    "No stations matched \"$cleanQuery\""
                                } else {
                                    "No live FM stations available for this selection"
                                },
                                color = AppleTextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                else -> {
                    itemsIndexed(
                        items = visibleStations,
                        key = { _, station -> "st_${station.uuid}" },
                        contentType = { _, _ -> "station_row" },
                    ) { idx, station ->
                        val isCurrent = state.currentStation?.uuid == station.uuid
                        GroupedStationCardRowShell(
                            isFirst = idx == 0,
                            isLast = idx == visibleStations.lastIndex,
                        ) {
                            RadioStationGroupedRow(
                                station = station,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && state.isPlaying,
                                isBuffering = isCurrent && state.isBuffering,
                                isFavorite = state.favoriteIds.contains(station.uuid),
                                onClick = {
                                    OnlineRadioController.playStation(
                                        station = station,
                                        queue = visibleStations,
                                        expandPlayer = true,
                                    )
                                },
                                onFavoriteClick = { OnlineRadioController.toggleFavorite(station) },
                            )
                        }
                    }

                    if (cleanQuery.isEmpty() && (state.loadingMoreStations || state.hasMoreStations)) {
                        item(key = "stations_load_more_footer") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    color = AppleMusicRed,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Loading more FM stations (${visibleStations.size} loaded)...",
                                    color = AppleTextSecondary,
                                    fontSize = 12.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isScrolled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(AppleOledBlack, Color.Transparent),
                        ),
                    ),
            )
        }
    }

    if (showCountryPicker) {
        val options = remember(state.countries) {
            state.countries.map { c ->
                RadioPickerItem(
                    key = c.code,
                    title = c.name,
                    detail = "${c.stationCount} FM stations • ${c.code}",
                )
            }
        }
        RadioSearchablePickerDialog(
            title = "Select Country",
            subtitle = "Choose a country to fetch live online cities & FM stations",
            items = options,
            selectedKey = state.selectedCountry?.code,
            isLoading = state.loadingCountries,
            onSelect = { item ->
                state.countries.firstOrNull { it.code == item.key }?.let {
                    OnlineRadioController.selectCountry(it)
                }
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
        )
    }

    if (showCityPicker) {
        val options = remember(state.cities) {
            state.cities.map { city ->
                RadioPickerItem(
                    key = city.name,
                    title = city.name,
                    detail = if (city.stationCount > 0) "${city.stationCount} FM stations" else "Regional filter",
                )
            }
        }
        RadioSearchablePickerDialog(
            title = "Select City / Region",
            subtitle = state.selectedCountry?.name?.let { "Cities & states in $it" } ?: "Filter stations by region",
            items = options,
            selectedKey = state.selectedCity,
            isLoading = state.loadingCities,
            onSelect = { item ->
                OnlineRadioController.selectCity(item.key)
                showCityPicker = false
            },
            onDismiss = { showCityPicker = false },
        )
    }
}

@Composable
private fun SettingsSectionHeaderText(title: String) {
    Text(
        text = title,
        color = AppleTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 12.dp, bottom = 7.dp),
    )
}

@Composable
private fun SettingsStyleSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(RadioGroupSurface)
            .border(0.5.dp, RadioGroupBorder, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = AppleTextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = AppleTextSecondary,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.5.sp),
                cursorBrush = SolidColor(AppleMusicRed),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear",
                    tint = AppleTextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsLocationPickerRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailingValue: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppleTextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = AppleTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = trailingValue,
            color = AppleTextSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = SfIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.36f),
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun GroupedStationCardRowShell(
    isFirst: Boolean,
    isLast: Boolean,
    bottomSpacerDp: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val topR = if (isFirst) 16.dp else 0.dp
    val botR = if (isLast) 16.dp else 0.dp
    val shape = RoundedCornerShape(topStart = topR, topEnd = topR, bottomStart = botR, bottomEnd = botR)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(RadioGroupSurface),
        ) {
            content()
            if (!isLast) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 74.dp),
                    thickness = 0.5.dp,
                    color = RadioDividerColor,
                )
            }
        }
        if (bottomSpacerDp > 0.dp) {
            Spacer(modifier = Modifier.height(bottomSpacerDp))
        }
    }
}

@Composable
private fun RadioStationGroupedRow(
    station: OnlineRadioStation,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xFF28282C))
                .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Radio,
                contentDescription = null,
                tint = AppleMusicRed.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp),
            )
            if (!station.faviconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = remember(station.faviconUrl) {
                        ImageRequest.Builder(context)
                            .data(station.faviconUrl)
                            .size(128)
                            .build()
                    },
                    contentDescription = station.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else if (isPlaying) {
                        AnimatedEqualizer(isPlaying = true, color = AppleMusicRed)
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = station.name,
                color = if (isCurrent) AppleMusicRed else AppleTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = station.subtitleLine,
                color = AppleTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onFavoriteClick, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "Favorite Station",
                tint = if (isFavorite) AppleMusicRed else Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(19.dp),
            )
        }

        Icon(
            imageVector = SfIcons.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.34f),
            modifier = Modifier.size(13.dp),
        )
    }
}

private data class RadioPickerItem(
    val key: String,
    val title: String,
    val detail: String,
)

@Composable
private fun RadioSearchablePickerDialog(
    title: String,
    subtitle: String,
    items: List<RadioPickerItem>,
    selectedKey: String?,
    isLoading: Boolean = false,
    onSelect: (RadioPickerItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val filtered = remember(items, filter) {
        val q = filter.trim()
        if (q.isEmpty()) items else items.filter {
            it.title.contains(q, ignoreCase = true) || it.detail.contains(q, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 390.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(RadioModalSurface)
                    .border(0.75.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = AppleTextSecondary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))

                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsStyleSearchField(
                        query = filter,
                        placeholder = "Search...",
                        onQueryChange = { filter = it },
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (isLoading && filtered.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(
                            color = AppleMusicRed,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Fetching live catalog...",
                            color = AppleTextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                    ) {
                        itemsIndexed(
                            items = filtered,
                            key = { idx, it -> "${it.key}_$idx" },
                            contentType = { _, _ -> "picker_row" },
                        ) { idx, option ->
                            val selected = option.key == selectedKey
                            HorizontalDivider(thickness = 0.5.dp, color = RadioDividerColor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(option) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.title,
                                        color = if (selected) AppleMusicRed else Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                    Spacer(Modifier.height(1.dp))
                                    Text(
                                        text = option.detail,
                                        color = Color.White.copy(alpha = 0.50f),
                                        fontSize = 12.sp,
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = AppleMusicRed,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                            if (idx == filtered.lastIndex) {
                                HorizontalDivider(thickness = 0.5.dp, color = RadioDividerColor)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFF8E8E93),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
