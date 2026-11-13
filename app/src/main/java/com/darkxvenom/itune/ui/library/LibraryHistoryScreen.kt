package com.darkxvenom.itune.ui.library

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.auth.YouTubeLibrarySyncManager
import com.darkxvenom.itune.data.library.LibraryDestination
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.ui.artist.components.SongRow
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class HistoryFilterTab(val label: String) {
    ALL("All"),
    LOCAL("Recent Plays"),
    YOUTUBE("YouTube Music"),
}

@Composable
fun LibraryHistoryScreen(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
) {
    val context = LocalContext.current
    val recentMeta by LibraryStore.recentSongs.collectAsStateWithLifecycle()
    val ytmHistoryMeta by LibraryStore.ytmHistorySongs.collectAsStateWithLifecycle()
    val ytAccount by GoogleAuthStore.state.collectAsStateWithLifecycle()
    val query = controller.searchQuery

    var selectedTab by remember { mutableStateOf(HistoryFilterTab.ALL) }
    var isSyncingYtm by remember { mutableStateOf(false) }

    LaunchedEffect(ytAccount.isSignedIn, ytAccount.activeSessionKey) {
        if (ytAccount.isSignedIn) {
            isSyncingYtm = true
            runCatching {
                YouTubeLibrarySyncManager.syncHistory(context)
            }
            isSyncingYtm = false
        }
    }

    // Filter metadata according to selected tab
    val displayedMeta = remember(recentMeta, ytmHistoryMeta, selectedTab, ytAccount.isSignedIn) {
        when {
            !ytAccount.isSignedIn -> recentMeta
            selectedTab == HistoryFilterTab.LOCAL -> recentMeta
            selectedTab == HistoryFilterTab.YOUTUBE -> ytmHistoryMeta
            else -> {
                // Unified merge with local first, then remote history
                val seen = LinkedHashSet<String>()
                buildList {
                    recentMeta.forEach { if (seen.add(it.id)) add(it) }
                    ytmHistoryMeta.forEach { if (seen.add(it.id)) add(it) }
                }
            }
        }
    }

    val visibleMeta = remember(displayedMeta, query) {
        if (query.isBlank()) displayedMeta else displayedMeta.filter { m ->
            m.title.contains(query, ignoreCase = true) ||
                m.artists.contains(query, ignoreCase = true) ||
                m.album?.contains(query, ignoreCase = true) == true
        }
    }

    val visibleSongs = remember(visibleMeta) {
        visibleMeta.map { meta ->
            val timeText = formatHistoryTime(meta.addedAtMs)
            val sub = if (timeText.isNotBlank()) "${meta.artists} • $timeText" else meta.artists
            val durText = if (meta.durationSec > 0L) {
                val m = meta.durationSec / 60L
                val s = meta.durationSec % 60L
                "%d:%02d".format(m, s)
            } else null
            Song(
                videoId = meta.id,
                title = meta.title,
                artist = sub,
                albumName = meta.album,
                thumbnailUrl = meta.thumbnailUrl,
                durationText = durText,
                isExplicit = meta.isExplicit,
            )
        }
    }

    val backdropArts = remember(displayedMeta) {
        displayedMeta.mapNotNull { it.thumbnailUrl.ifBlank { null } }.distinct().take(4)
    }

    val listState = controller.listStates.getOrPut(LibraryDestination.HISTORY) { LazyListState() }
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
        scrolledTitle = "History",
        isScrolled = isScrolled,
        hasMiniPlayer = hasMiniPlayer,
        fallbackAccent = AppleMusicRed,
        onBack = onBack,
        topBarActions = {
            LibraryTopBarActionButtons(
                controller = controller,
                showSort = false,
                onMoreClick = {
                    controller.openTopMenu(
                        LibraryTopMenuRequest(
                            destination = LibraryDestination.HISTORY,
                            songs = visibleSongs,
                            stationLabel = "History",
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
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "history_header") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                        Text(
                            text = "History",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.6).sp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (ytAccount.isSignedIn) {
                                if (isSyncingYtm) "Syncing YouTube Music History…"
                                else "${displayedMeta.size} Tracks • Local & YouTube History"
                            } else {
                                "${displayedMeta.size} Recently Played Tracks"
                            },
                            color = Color.White.copy(alpha = 0.64f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    LibraryFilterBar(
                        visible = controller.searchOpen,
                        query = query,
                        onQueryChange = { controller.searchQuery = it },
                        onClose = controller::toggleSearch,
                        autoFocus = controller.focusSearchNext,
                        onFocused = { controller.focusSearchNext = false },
                        placeholder = "Filter playback history...",
                    )

                    // Tab Selector if signed in to Google/YouTube Music
                    if (ytAccount.isSignedIn) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(2.5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HistoryFilterTab.values().forEach { tab ->
                                val isSelected = selectedTab == tab
                                val bg by animateColorAsState(
                                    targetValue = if (isSelected) AppleMusicRed else Color.Transparent,
                                    label = "HistoryTabBg"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .clickable { selectedTab = tab }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tab.label,
                                        color = if (isSelected) Color.White else AppleTextSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

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
                                onClick = { controller.startRadioFromSongs(visibleSongs, "History Station") },
                                filled = false,
                                accentTint = AppleMusicRed,
                                height = 46.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (displayedMeta.isEmpty()) {
                item(key = "history_empty") {
                    LibraryGlassEmptyCard(
                        icon = SfIcons.MusicNote,
                        title = "No Playback History Yet",
                        subtitle = "Songs you listen to across iTune and YouTube Music will appear here with exact playback timestamps.",
                        actionLabel = "Explore Music",
                        onAction = onExploreClick,
                    )
                }
            } else if (visibleMeta.isEmpty()) {
                item(key = "history_no_match") {
                    LibraryGlassEmptyCard(
                        icon = ITubeIcons.Search,
                        title = "No History Found",
                        subtitle = "No played songs matched \"$query\".",
                        actionLabel = "Clear Filter",
                        onAction = { controller.searchQuery = "" },
                    )
                }
            } else {
                itemsIndexed(
                    items = visibleSongs,
                    key = { idx, song -> "${song.videoId}_$idx" },
                ) { index, song ->
                    val isCurrent = song.videoId == currentSongId
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
                        isCurrent = isCurrent,
                        isPlaying = isPlaying,
                    )

                    if (index < visibleSongs.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 20.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats a playback timestamp into a user-friendly human string.
 */
private fun formatHistoryTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    if (diff < 60_000L) return "Just now"
    if (diff < 3600_000L) return "${diff / 60_000L}m ago"

    val calNow = Calendar.getInstance()
    val calThen = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(timestampMs))

    return if (calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR)) {
        if (calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)) {
            "Today, $formattedTime"
        } else if (calNow.get(Calendar.DAY_OF_YEAR) - calThen.get(Calendar.DAY_OF_YEAR) == 1) {
            "Yesterday, $formattedTime"
        } else {
            val dateFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
            dateFormat.format(Date(timestampMs))
        }
    } else {
        val dateFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        dateFormat.format(Date(timestampMs))
    }
}
