package com.darkxvenom.itune.ui.browse

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.data.charts.ChartArtist
import com.darkxvenom.itune.data.charts.ChartPlatform
import com.darkxvenom.itune.data.charts.ChartRegionSlugToName
import com.darkxvenom.itune.data.charts.ChartTrack
import com.darkxvenom.itune.data.charts.ChartsRepository
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.ui.player.SFProDisplay
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.launch

@Composable
fun BrowseChartsView(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val selectedPlatform by ChartsRepository.selectedPlatform.collectAsStateWithLifecycle()
    val selectedRegion by ChartsRepository.selectedRegion.collectAsStateWithLifecycle()
    val isLoading by ChartsRepository.isLoading.collectAsStateWithLifecycle()

    val appleTracks by ChartsRepository.appleTracks.collectAsStateWithLifecycle()
    val appleArtists by ChartsRepository.appleArtists.collectAsStateWithLifecycle()
    val ituneTracks by ChartsRepository.ituneTracks.collectAsStateWithLifecycle()
    val ituneArtists by ChartsRepository.ituneArtists.collectAsStateWithLifecycle()
    val ytTracks by ChartsRepository.youtubeTracks.collectAsStateWithLifecycle()
    val jioTracks by ChartsRepository.jioSaavnTracks.collectAsStateWithLifecycle()

    var showRegionPicker by remember { mutableStateOf(false) }

    LaunchedEffect(selectedRegion) {
        ChartsRepository.ensureLoaded(selectedRegion)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Platform Selector Pills
        ChartsPlatformSelector(
            selectedPlatform = selectedPlatform,
            onPlatformSelected = { platform ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                ChartsRepository.selectedPlatform.value = platform
            }
        )

        Spacer(Modifier.height(12.dp))

        // Region Pill for Apple Music & iTune
        if (selectedPlatform == ChartPlatform.APPLE || selectedPlatform == ChartPlatform.ITUNE) {
            val effectiveCode = if (selectedRegion == "system") {
                ContentLocaleCatalog.resolveEffectiveGl(SettingsStore.state.value.contentCountry).lowercase()
            } else selectedRegion.lowercase()
            val regionName = ChartRegionSlugToName[effectiveCode] ?: "United States"
            Row(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF1C1C1E))
                    .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .clickable { showRegionPicker = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = AppleMusicRed,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Region: $regionName",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = SFProDisplay
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // Animated Content for selected platform
        AnimatedContent(
            targetState = selectedPlatform,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "ChartsPlatformContent"
        ) { platform ->
            when (platform) {
                ChartPlatform.APPLE -> {
                    if (isLoading && appleTracks.isEmpty()) {
                        ChartsSkeletonList()
                    } else if (appleTracks.isEmpty()) {
                        ChartsEmptyView("No Apple Music charts available for this region.")
                    } else {
                        ChartsTrackList(
                            tracks = appleTracks,
                            artists = appleArtists,
                            onTrackClick = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Playing ${track.title}...", Toast.LENGTH_SHORT).show()
                                ChartsRepository.playTrack(track, appleTracks)
                            }
                        )
                    }
                }

                ChartPlatform.ITUNE -> {
                    if (isLoading && ituneTracks.isEmpty()) {
                        ChartsSkeletonList()
                    } else if (ituneTracks.isEmpty()) {
                        ChartsEmptyView("No iTune charts available.")
                    } else {
                        ChartsTrackList(
                            tracks = ituneTracks,
                            artists = ituneArtists,
                            headerFeatured = true,
                            onTrackClick = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Playing ${track.title}...", Toast.LENGTH_SHORT).show()
                                ChartsRepository.playTrack(track, ituneTracks)
                            }
                        )
                    }
                }

                ChartPlatform.YOUTUBE -> {
                    if (isLoading && ytTracks.isEmpty()) {
                        ChartsSkeletonList()
                    } else if (ytTracks.isEmpty()) {
                        ChartsEmptyView("No YouTube charts available.")
                    } else {
                        ChartsTrackList(
                            tracks = ytTracks,
                            onTrackClick = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Playing ${track.title}...", Toast.LENGTH_SHORT).show()
                                ChartsRepository.playTrack(track, ytTracks)
                            }
                        )
                    }
                }

                ChartPlatform.JIOSAAVN -> {
                    if (isLoading && jioTracks.isEmpty()) {
                        ChartsSkeletonList()
                    } else if (jioTracks.isEmpty()) {
                        ChartsEmptyView("No JioSaavn charts available.")
                    } else {
                        ChartsTrackList(
                            tracks = jioTracks,
                            onTrackClick = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                Toast.makeText(context, "Playing ${track.title}...", Toast.LENGTH_SHORT).show()
                                ChartsRepository.playTrack(track, jioTracks)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRegionPicker) {
        ChartRegionPickerSheet(
            currentRegion = selectedRegion,
            onRegionSelected = { newRegion ->
                ChartsRepository.selectedRegion.value = newRegion
                ChartsRepository.refresh(newRegion, force = true)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }
}

@Composable
private fun ChartsPlatformSelector(
    selectedPlatform: ChartPlatform,
    onPlatformSelected: (ChartPlatform) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF18181A))
            .border(0.6.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartPlatform.entries.forEach { platform ->
                val isSelected = platform == selectedPlatform

                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF2C2C2E) else Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "PlatformBgColor"
                )

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else AppleTextSecondary,
                    animationSpec = tween(durationMillis = 180),
                    label = "PlatformTextColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(9.dp))
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPlatformSelected(platform) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = platform.title,
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = SFProDisplay,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartsTrackList(
    tracks: List<ChartTrack>,
    artists: List<ChartArtist> = emptyList(),
    headerFeatured: Boolean = false,
    onTrackClick: (ChartTrack) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Optional Featured Hero row for iTune
        if (headerFeatured && tracks.isNotEmpty()) {
            Text(
                text = "Featured Hit",
                color = AppleTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SFProDisplay,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            val topTrack = tracks.first()
            FeaturedChartHeroCard(track = topTrack, onClick = { onTrackClick(topTrack) })
            Spacer(Modifier.height(8.dp))
        }

        // Trending Artists shelf if available
        if (artists.isNotEmpty()) {
            Text(
                text = "Trending Artists",
                color = AppleTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SFProDisplay,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(artists, key = { "art_${it.name}_${it.rank}" }) { artist ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(80.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artist.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C2C2E))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = artist.name,
                            color = AppleTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = SFProDisplay,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "Top Songs",
            color = AppleTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = SFProDisplay,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        // All ranked tracks
        tracks.forEachIndexed { index, track ->
            ChartSongRowItem(
                track = track,
                rank = index + 1,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

@Composable
private fun FeaturedChartHeroCard(
    track: ChartTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .background(Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
    ) {
        // High-res background artwork with gradient overlay
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(track.thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.90f),
                            Color.Black.copy(alpha = 0.65f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFD700).copy(alpha = 0.20f))
                    .border(1.dp, Color(0xFFFFD700), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#1",
                    color = Color(0xFFFFD700),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SFProDisplay
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP STREAMED",
                    color = AppleMusicRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    fontFamily = SFProDisplay
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = SFProDisplay,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    fontFamily = SFProDisplay,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AppleMusicRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChartSongRowItem(
    track: ChartTrack,
    rank: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> Color.White.copy(alpha = 0.60f)
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank Number
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                color = rankColor,
                fontSize = 15.sp,
                fontWeight = if (rank <= 3) FontWeight.ExtraBold else FontWeight.Medium,
                fontFamily = SFProDisplay
            )
        }

        Spacer(Modifier.width(8.dp))

        // High-res Thumbnail
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(track.thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF2C2C2E))
        )

        Spacer(Modifier.width(12.dp))

        // Metadata
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = AppleTextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = SFProDisplay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.isExplicit) {
                    Box(
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .size(13.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "E",
                            color = Color.Black,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Text(
                    text = track.artist,
                    color = AppleTextSecondary,
                    fontSize = 12.5.sp,
                    fontFamily = SFProDisplay,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Quick Play Icon Button
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = AppleMusicRed,
                modifier = Modifier.size(20.dp)
            )
        }

        // 3-dots Menu Button
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Options",
                    tint = AppleTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(Color(0xFF2C2C2E))
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = Color.White) },
                    onClick = {
                        menuExpanded = false
                        val mTrack = MusicTrack(
                            id = track.id ?: "chart_${track.rank}",
                            title = track.title,
                            artists = track.artist,
                            thumbnailUrl = track.thumbnailUrl,
                            durationSec = track.durationSec,
                            isExplicit = track.isExplicit
                        )
                        MusicPlayerState.playNext(mTrack)
                        Toast.makeText(context, "Playing next: ${track.title}", Toast.LENGTH_SHORT).show()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Copy Song Name", color = Color.White) },
                    onClick = {
                        menuExpanded = false
                        clipboardManager.setText(AnnotatedString("${track.title} - ${track.artist}"))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun ChartsSkeletonList() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222224))
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF222224))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF222224))
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.40f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF1C1C1E))
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartsEmptyView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = AppleTextSecondary,
            fontSize = 14.sp,
            fontFamily = SFProDisplay,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartRegionPickerSheet(
    currentRegion: String,
    onRegionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filtered = remember(searchQuery) {
        ChartRegionSlugToName.toList().filter { (code, name) ->
            searchQuery.isBlank() || name.contains(searchQuery, ignoreCase = true) || code.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1C1E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Choose Charts Region",
                color = AppleTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = SFProDisplay,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search country...", color = AppleTextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AppleTextSecondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = AppleTextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppleMusicRed,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0xFF2C2C2E),
                    unfocusedContainerColor = Color(0xFF2C2C2E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered, key = { it.first }) { (code, name) ->
                    val isSelected = code.equals(currentRegion, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onRegionSelected(code) }
                            .background(if (isSelected) Color(0xFF2C2C2E) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            color = if (isSelected) AppleMusicRed else Color.White,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = SFProDisplay
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = AppleMusicRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
