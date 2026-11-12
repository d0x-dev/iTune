package com.darkxvenom.itune.ui.timetravel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.darkxvenom.itune.data.model.toMusicTrack
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

private val PAGE_GUTTER = 16.dp
private val AccentRed = Color(0xFFFA2D48)

@Composable
fun TimeTravelScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    controller: LibraryHostController? = null,
    hasMiniPlayer: Boolean = false,
) {
    val context = LocalContext.current
    val stats by TimeTravelRepository.stats.collectAsStateWithLifecycle()
    val selectedPeriod by TimeTravelRepository.selectedPeriod.collectAsStateWithLifecycle()
    val isLoading by TimeTravelRepository.isLoading.collectAsStateWithLifecycle()

    val userDisplayName = remember {
        SettingsStore.ensureInit(context)
        SettingsStore.state.value.displayName.ifBlank { "iTune Listener" }
    }

    var storyStartPage by remember { mutableStateOf<ReplayStoryPage?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TimeTravelRepository.ensureInit(context)
        TimeTravelRepository.refresh()
    }

    val leadArtwork = stats.topSongs.firstOrNull()?.artworkUrl
    val palette = rememberArtworkColors(leadArtwork)
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
    ) {
        // 1. BitChord Mesh Backdrop
        MeshGradientBackground(
            palette = palette,
            trackKey = leadArtwork,
            animated = false,
        )

        // 2. Subtle Dark Gradient Wash
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.72f),
                            Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        // 3. Scrollable Replay Content
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 80.dp, bottom = if (hasMiniPlayer) 140.dp else 100.dp),
        ) {
            // Heading + Period Picker
            item("heading") {
                HeadingSection(
                    period = selectedPeriod,
                    onPeriodSelect = { period ->
                        TimeTravelRepository.selectPeriod(period)
                    },
                )
            }

            if (isLoading) {
                item("loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
                    }
                }
            } else if (stats.isEmpty) {
                item("empty") {
                    EmptyReplayView(selectedPeriod)
                }
            } else {
                // Headline Credit Cards
                item("cards") {
                    val heroCards = remember(stats) {
                        buildList {
                            add(
                                ReplayHeroCard(
                                    label = "MINUTES LISTENED",
                                    value = "${stats.totalListeningSec / 60}",
                                    detail = "${stats.totalPlays} plays · ${selectedPeriod.label}",
                                    artworkUrl = stats.topSongs.firstOrNull()?.artworkUrl,
                                    page = ReplayStoryPage.MINUTES,
                                ),
                            )
                            stats.topSongs.firstOrNull()?.let {
                                add(
                                    ReplayHeroCard(
                                        label = "TOP SONG",
                                        value = it.title,
                                        detail = "${it.artist} · ${it.playCount} plays",
                                        artworkUrl = it.artworkUrl,
                                        page = ReplayStoryPage.SONGS,
                                    ),
                                )
                            }
                            stats.topArtists.firstOrNull()?.let {
                                add(
                                    ReplayHeroCard(
                                        label = "TOP ARTIST",
                                        value = it.artistName,
                                        detail = "${it.durationSec / 60}m · ${it.playCount} plays",
                                        artworkUrl = it.artworkUrl,
                                        page = ReplayStoryPage.ARTISTS,
                                    ),
                                )
                            }
                            stats.topAlbums.firstOrNull()?.let {
                                add(
                                    ReplayHeroCard(
                                        label = "TOP ALBUM",
                                        value = it.albumName,
                                        detail = "${it.artistName} · ${it.playCount} plays",
                                        artworkUrl = it.artworkUrl,
                                        page = ReplayStoryPage.ALBUMS,
                                    ),
                                )
                            }
                        }
                    }

                    ReplayCardRow(
                        cards = heroCards,
                        holder = userDisplayName,
                        memberSince = "11/26",
                        onCardClick = { page ->
                            storyStartPage = page
                        },
                    )
                }

                // Play Replay Action Row
                item("play_replay") {
                    ReplayActionRow(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Play your Replay",
                        onClick = { storyStartPage = ReplayStoryPage.INTRO },
                    )
                }

                // Top Songs Chart
                if (stats.topSongs.isNotEmpty()) {
                    chartSection(
                        title = "Top songs",
                        items = stats.topSongs.take(5),
                        keySelector = { it.songId },
                    ) { songStat ->
                        SongChartRow(
                            stat = songStat,
                            onClick = {
                                MusicPlayerState.play(songStat.toSong().toMusicTrack())
                            },
                            onMenuClick = {
                                controller?.openSongMenu(songStat.toSong())
                            },
                        )
                    }
                }

                // Top Artists Chart
                if (stats.topArtists.isNotEmpty()) {
                    chartSection(
                        title = "Top artists",
                        items = stats.topArtists.take(5),
                        keySelector = { it.artistName },
                    ) { artistStat ->
                        ArtistChartRow(
                            stat = artistStat,
                            onClick = {
                                if (!artistStat.artistId.isNullOrBlank()) {
                                    controller?.openDetail(
                                        browseId = artistStat.artistId,
                                        title = artistStat.artistName,
                                        subtitle = "Artist",
                                        thumbnailUrl = artistStat.artworkUrl,
                                        type = BrowseType.ARTIST,
                                    )
                                }
                            },
                            onMenuClick = {
                                controller?.openCollectionMenu(
                                    SavedCollectionEntry(
                                        browseId = artistStat.artistId.orEmpty(),
                                        title = artistStat.artistName,
                                        subtitle = "Artist",
                                        thumbnailUrl = artistStat.artworkUrl,
                                        type = BrowseType.ARTIST,
                                    ),
                                )
                            },
                        )
                    }
                }

                // Top Albums Chart
                if (stats.topAlbums.isNotEmpty()) {
                    chartSection(
                        title = "Top albums",
                        items = stats.topAlbums.take(5),
                        keySelector = { it.albumName },
                    ) { albumStat ->
                        AlbumChartRow(
                            stat = albumStat,
                            onClick = {
                                controller?.openDetail(
                                    browseId = albumStat.albumName,
                                    title = albumStat.albumName,
                                    subtitle = albumStat.artistName,
                                    thumbnailUrl = albumStat.artworkUrl,
                                    type = BrowseType.ALBUM,
                                )
                            },
                            onMenuClick = {
                                controller?.openCollectionMenu(
                                    SavedCollectionEntry(
                                        browseId = albumStat.albumName,
                                        title = albumStat.albumName,
                                        subtitle = albumStat.artistName,
                                        thumbnailUrl = albumStat.artworkUrl,
                                        type = BrowseType.ALBUM,
                                    ),
                                )
                            },
                        )
                    }
                }

                // Habits / Listening Shape
                item("habits") {
                    HabitsSection(stats)
                }

                // Share Replay Action Row
                item("share_replay") {
                    ReplayActionRow(
                        icon = Icons.Rounded.IosShare,
                        label = "Share my Replay",
                        onClick = { showShareSheet = true },
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
                IconButton(onClick = { showShareSheet = true }) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = "Share Replay",
                        tint = AppleTextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        // Stories Dialog
        storyStartPage?.let { page ->
            TimeTravelStoriesDialog(
                stats = stats,
                displayName = userDisplayName,
                startPage = page,
                onDismiss = { storyStartPage = null },
                onSharePage = {
                    showShareSheet = true
                },
            )
        }

        // Share Sheet
        if (showShareSheet) {
            TimeTravelShareSheet(
                stats = stats,
                holder = userDisplayName,
                memberSince = "11/26",
                onDismiss = { showShareSheet = false },
            )
        }
    }
}

// ── Heading ─────────────────────────────────────────────────────────────────

@Composable
private fun HeadingSection(
    period: ListeningPeriod,
    onPeriodSelect: (ListeningPeriod) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER + 4.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Replay",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            text = period.label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(14.dp))

        // BitChord 4-Segment Period Picker
        PeriodPicker(
            selected = period,
            onSelect = onPeriodSelect,
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PeriodPicker(
    selected: ListeningPeriod,
    onSelect: (ListeningPeriod) -> Unit,
) {
    val periods = listOf(
        ListeningPeriod.ONE_WEEK to "Weekly",
        ListeningPeriod.ONE_MONTH to "Monthly",
        ListeningPeriod.ONE_YEAR to "Yearly",
        ListeningPeriod.ALL_TIME to "Lifetime",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        periods.forEach { (period, tabName) ->
            val active = period == selected
            val background by animateColorAsState(
                if (active) Color.White.copy(alpha = 0.92f) else Color.Transparent,
                tween(160),
                label = "periodChip",
            )
            Text(
                text = tabName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.W700,
                color = if (active) Color.Black else Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(background)
                    .clickable { onSelect(period) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

// ── Action Row ──────────────────────────────────────────────────────────────

@Composable
private fun ReplayActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Chart Section ───────────────────────────────────────────────────────────

private fun <T> LazyListScope.chartSection(
    title: String,
    items: List<T>,
    keySelector: (T) -> String,
    content: @Composable (T) -> Unit,
) {
    item("$title-heading") {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
            modifier = Modifier.padding(
                start = PAGE_GUTTER + 4.dp,
                end = PAGE_GUTTER + 4.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        )
    }
    items(items, key = { keySelector(it) }) { item ->
        content(item)
    }
    item("$title-gap") {
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SongChartRow(
    stat: TopSongStat,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onMenuClick)
            .padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(stat.rank)

        AsyncImage(
            model = stat.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = stat.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val durationText = "${stat.durationSec / 60}m ${stat.durationSec % 60}s"
            Text(
                text = "${stat.artist} · $durationText",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (stat.playCount > 0) {
            Text(
                text = stat.playCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun ArtistChartRow(
    stat: TopArtistStat,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onMenuClick)
            .padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(stat.rank)

        AsyncImage(
            model = stat.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = stat.artistName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${stat.durationSec / 60} minutes",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (stat.playCount > 0) {
            Text(
                text = stat.playCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun AlbumChartRow(
    stat: TopAlbumStat,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onMenuClick)
            .padding(horizontal = PAGE_GUTTER + 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(stat.rank)

        AsyncImage(
            model = stat.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = stat.albumName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W600,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${stat.artistName} · ${stat.durationSec / 60}m",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (stat.playCount > 0) {
            Text(
                text = stat.playCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Text(
        text = rank.toString(),
        style = if (rank == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.W800,
        color = if (rank == 1) AccentRed else Color.White.copy(alpha = 0.45f),
        modifier = Modifier.width(28.dp),
    )
}

// ── Habits Section ──────────────────────────────────────────────────────────

@Composable
private fun HabitsSection(stats: TimeTravelStats) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER + 4.dp)) {
        Text(
            text = "Your listening pattern",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("SONGS", stats.distinctSongsCount.toString(), Modifier.weight(1f))
            StatTile("ARTISTS", stats.distinctArtistsCount.toString(), Modifier.weight(1f))
            StatTile("ALBUMS", stats.distinctAlbumsCount.toString(), Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "You listen most around ${formatHour(stats.peakListeningHour)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
        )

        if (stats.busiestDay != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Your biggest day was ${stats.busiestDay}.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun EmptyReplayView(period: ListeningPeriod) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.replay),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Not enough listening yet",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Play a few more songs in ${period.label.lowercase()} and iTune will turn your history into a Replay.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatHour(hour: Int): String = when (hour) {
    0 -> "midnight"
    12 -> "midday"
    in 1..11 -> "$hour AM"
    else -> "${hour - 12} PM"
}
