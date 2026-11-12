package com.darkxvenom.itune.ui.timetravel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.SavedCollectionEntry
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.timetravel.ListeningHistoryItem
import com.darkxvenom.itune.data.timetravel.ListeningPeriod
import com.darkxvenom.itune.data.timetravel.TimeTravelRepository
import com.darkxvenom.itune.data.timetravel.TimeTravelStats
import com.darkxvenom.itune.data.timetravel.TopAlbumStat
import com.darkxvenom.itune.data.timetravel.TopArtistStat
import com.darkxvenom.itune.data.timetravel.TopSongStat
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.longPressMenuClickable
import com.darkxvenom.itune.ui.library.LibraryHostController
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimeTravelScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val stats by TimeTravelRepository.stats.collectAsStateWithLifecycle()
    val selectedPeriod by TimeTravelRepository.selectedPeriod.collectAsStateWithLifecycle()
    val isLoading by TimeTravelRepository.isLoading.collectAsStateWithLifecycle()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val displayName = settingsState.displayName.ifBlank { "You" }

    var showInsightsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TimeTravelRepository.ensureInit(context)
        TimeTravelRepository.refresh()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 90.dp,
                bottom = if (hasMiniPlayer) 150.dp else 100.dp,
            ),
        ) {
            // 1. Credit-Card-Style Statistics Cards Carousel
            item(key = "cards_carousel") {
                TimeTravelCardsCarousel(
                    stats = stats,
                    displayName = displayName,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }

            // 2. Listening Period Selector Chips
            item(key = "period_selector") {
                PeriodSelectorRow(
                    selectedPeriod = selectedPeriod,
                    onSelect = { TimeTravelRepository.selectPeriod(it) },
                    modifier = Modifier.padding(bottom = 20.dp),
                )
            }

            // 3. iTune Insights Stories Experience Launcher Banner
            item(key = "insights_banner") {
                InsightsStoryLauncherBanner(
                    onClick = { showInsightsDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // 4. Overview Metric Badges (Listening Time, Total Plays, Avg per Track)
            item(key = "metrics_row") {
                OverviewMetricsRow(
                    stats = stats,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                )
            }

            // 5. Most Played Songs (Ranks 1..10)
            if (stats.topSongs.isNotEmpty()) {
                item(key = "top_songs_header") {
                    SectionHeader(title = "Top Songs", subtitle = "Your most played tracks in this period")
                }

                itemsIndexed(
                    items = stats.topSongs.take(10),
                    key = { _, item -> "top_song_${item.songId}" },
                ) { index, item ->
                    TopSongRow(
                        stat = item,
                        onClick = {
                            val songObj = item.toSong()
                            controller.playSongs(listOf(songObj))
                        },
                        onMenuClick = {
                            controller.openSongMenu(item.toSong())
                        },
                    )
                }

                item(key = "top_songs_spacer") {
                    Spacer(Modifier.height(24.dp))
                }
            }

            // 6. Most Played Artists (Carousel)
            if (stats.topArtists.isNotEmpty()) {
                item(key = "top_artists_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(title = "Top Artists", subtitle = "Artists on your heaviest rotation")
                        Spacer(Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(stats.topArtists, key = { "art_${it.artistName}" }) { artStat ->
                                TopArtistCard(
                                    artist = artStat,
                                    onClick = {
                                        if (!artStat.artistId.isNullOrBlank()) {
                                            controller.openDetail(
                                                browseId = artStat.artistId,
                                                title = artStat.artistName,
                                                subtitle = "Artist",
                                                thumbnailUrl = artStat.artworkUrl,
                                                type = BrowseType.ARTIST,
                                            )
                                        } else {
                                            controller.openCollectionMenu(
                                                SavedCollectionEntry(
                                                    browseId = artStat.artistId.orEmpty(),
                                                    title = artStat.artistName,
                                                    subtitle = "Artist",
                                                    thumbnailUrl = artStat.artworkUrl,
                                                    type = BrowseType.ARTIST,
                                                ),
                                            )
                                        }
                                    },
                                    onMenuClick = {
                                        controller.openCollectionMenu(
                                            SavedCollectionEntry(
                                                browseId = artStat.artistId.orEmpty(),
                                                title = artStat.artistName,
                                                subtitle = "Artist",
                                                thumbnailUrl = artStat.artworkUrl,
                                                type = BrowseType.ARTIST,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // 7. Most Played Albums (Shelf)
            if (stats.topAlbums.isNotEmpty()) {
                item(key = "top_albums_section") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(title = "Top Albums", subtitle = "Complete projects you explored")
                        Spacer(Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(stats.topAlbums, key = { "alb_${it.albumName}" }) { albStat ->
                                TopAlbumCard(
                                    album = albStat,
                                    onClick = {
                                        controller.openCollectionMenu(
                                            SavedCollectionEntry(
                                                browseId = "",
                                                title = albStat.albumName,
                                                subtitle = albStat.artistName,
                                                thumbnailUrl = albStat.artworkUrl,
                                                type = BrowseType.ALBUM,
                                            ),
                                        )
                                    },
                                    onMenuClick = {
                                        controller.openCollectionMenu(
                                            SavedCollectionEntry(
                                                browseId = "",
                                                title = albStat.albumName,
                                                subtitle = albStat.artistName,
                                                thumbnailUrl = albStat.artworkUrl,
                                                type = BrowseType.ALBUM,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // 8. Listening Activity Charts (Day and Hour)
            item(key = "charts_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SectionHeader(title = "Listening Rhythm", subtitle = "When you tune in most")
                    ListeningActivityByDayChart(dailyActivity = stats.dailyActivity)
                    ListeningActivityByHourChart(hourlyActivity = stats.hourlyActivity)
                    Spacer(Modifier.height(8.dp))
                }
            }

            // 9. When You Listened – Chronological Timeline
            if (stats.timeline.isNotEmpty()) {
                item(key = "timeline_header") {
                    SectionHeader(title = "When You Listened", subtitle = "Your chronological playback activity")
                }

                // Group timeline items by formatted date
                val groupedTimeline = stats.timeline.groupBy { item ->
                    formatTimelineDate(item.timestamp)
                }

                groupedTimeline.forEach { (dateHeader, items) ->
                    item(key = "timeline_date_$dateHeader") {
                        Text(
                            text = dateHeader,
                            color = Color(0xFFFC3C44),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                    }

                    items(items, key = { "tl_${it.id}" }) { tlItem ->
                        TimelineSongRow(
                            item = tlItem,
                            onClick = {
                                controller.playSongs(listOf(tlItem.toSong()))
                            },
                            onMenuClick = {
                                controller.openSongMenu(tlItem.toSong())
                            },
                        )
                    }
                }

                item(key = "timeline_bottom_spacer") {
                    Spacer(Modifier.height(28.dp))
                }
            }

            // 10. Share Replay Prominent Action Button
            item(key = "share_replay_action") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                TimeTravelShareManager.shareReplay(context, stats, displayName)
                            }
                        },
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFC3C44),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Share Replay",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "Share a dynamic high-resolution card to Instagram Stories & socials",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Frosted Top Bar
        FrostedTopBar(
            onBack = onBack,
            centerContent = {
                FrostedTopBarTitlePill(hazeState = null) {
                    Text(
                        text = "Time Travel",
                        color = AppleTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        scope.launch {
                            TimeTravelShareManager.shareReplay(context, stats, displayName)
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = "Share Replay",
                        tint = AppleTextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        // Loading indicator
        AnimatedVisibility(
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            CircularProgressIndicator(color = Color(0xFFFC3C44))
        }

        // Fullscreen Instagram Stories-inspired Insights Experience
        if (showInsightsDialog) {
            TimeTravelInsightsDialog(
                stats = stats,
                displayName = displayName,
                onDismiss = { showInsightsDialog = false },
            )
        }
    }
}

@Composable
private fun PeriodSelectorRow(
    selectedPeriod: ListeningPeriod,
    onSelect: (ListeningPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ListeningPeriod.values().forEach { period ->
            val isSelected = period == selectedPeriod
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) Color(0xFFFC3C44) else Color(0xFF161524),
                    )
                    .border(
                        1.dp,
                        if (isSelected) Color(0xFFFF525E) else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(period) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(
                    text = period.label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun InsightsStoryLauncherBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2A103D), Color(0xFF1B0926), Color(0xFF0F0418)),
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0xFFFC3C44), Color(0xFF7928CA))),
                RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.replay),
                        contentDescription = null,
                        tint = Color(0xFFB388FF),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "iTune INSIGHTS",
                        color = Color(0xFFB388FF),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Tap to View Your Story",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Full-screen interactive highlights & personality",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.5.sp,
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFC3C44), Color(0xFF7928CA)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play Story",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun OverviewMetricsRow(
    stats: TimeTravelStats,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard(
            label = "LISTENING TIME",
            value = stats.formattedListeningTime,
            accentColor = Color(0xFFFC3C44),
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "TOTAL PLAYS",
            value = "${stats.totalPlays}",
            accentColor = Color(0xFF00DF89),
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = "AVG PER TRACK",
            value = "${stats.averageDurationSec / 60}m ${stats.averageDurationSec % 60}s",
            accentColor = Color(0xFF7928CA),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF14131E))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = AppleTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
        Text(
            text = subtitle,
            color = AppleTextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TopSongRow(
    stat: TopSongStat,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val rankColor = when (stat.rank) {
        1 -> Color(0xFFFC3C44)
        2 -> Color(0xFFFF525E)
        3 -> Color(0xFFFF8A94)
        else -> Color.White.copy(alpha = 0.45f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onMenuClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rank number
        Text(
            text = "${stat.rank}",
            color = rankColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )

        Spacer(Modifier.width(10.dp))

        // Artwork
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        ) {
            if (!stat.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = stat.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // Song & Artist Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.title,
                color = AppleTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stat.artist,
                color = AppleTextSecondary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Play Count & 3-dot menu button
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${stat.playCount} plays",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            if (stat.durationSec > 0) {
                Text(
                    text = "${stat.durationSec / 60}m",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                )
            }
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TopArtistCard(
    artist: TopArtistStat,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .longPressMenuClickable(onClick = onClick, onLongClick = onMenuClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .border(2.dp, Color(0xFF7928CA).copy(alpha = 0.6f), CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!artist.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artist.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = artist.artistName.take(1),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Rank badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7928CA)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${artist.rank}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = artist.artistName,
            color = AppleTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${artist.playCount} plays",
            color = AppleTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TopAlbumCard(
    album: TopAlbumStat,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .longPressMenuClickable(onClick = onClick, onLongClick = onMenuClick),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!album.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = album.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(42.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = album.albumName,
            color = AppleTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artistName,
            color = AppleTextSecondary,
            fontSize = 11.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album.playCount} plays",
            color = Color(0xFF0072F5),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TimelineSongRow(
    item: ListeningHistoryItem,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeStr = remember(item.timestamp) { timeFormat.format(Date(item.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onMenuClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!item.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Titles
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = AppleTextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                append(item.artist)
                item.album?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
            }
            Text(
                text = sub,
                color = AppleTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Time and duration
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeStr,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.durationSec > 0) {
                Text(
                    text = "${item.durationSec / 60}:${(item.durationSec % 60).toString().padStart(2, '0')}",
                    color = Color.White.copy(alpha = 0.35f),
                    fontSize = 11.sp,
                )
            }
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = "Options",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun formatTimelineDate(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffDays = (now - timestamp) / (1000 * 60 * 60 * 24)
    return when {
        diffDays == 0L -> "Today"
        diffDays == 1L -> "Yesterday"
        diffDays < 7L -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
