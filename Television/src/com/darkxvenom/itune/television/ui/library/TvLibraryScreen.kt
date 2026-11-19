/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.television.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.ReleaseType
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.auth.GoogleAuthStore
import com.darkxvenom.itune.data.library.ContentLocaleCatalog
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.NewReleasesRepository
import com.darkxvenom.itune.data.library.SavedCollectionEntry
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.library.UserPlaylist
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.television.focus.SFProDisplay
import com.darkxvenom.itune.television.focus.TvAppleSilverBg
import com.darkxvenom.itune.television.focus.tvFocusable
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private enum class LibrarySection(val label: String, val isGenre: Boolean = false) {
    RECENTLY_ADDED("Recently Added"),
    PLAYLISTS("Playlists"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    HISTORY("History"),
    COMPILATIONS("Compilations"),
    GENRE_ALTERNATIVE("Alternative", isGenre = true),
    GENRE_BLUES("Blues", isGenre = true),
    GENRE_CLASSICAL("Classical", isGenre = true),
    GENRE_DANCE("Dance", isGenre = true),
    GENRE_HIPHOP("Hip-Hop", isGenre = true),
    GENRE_JAZZ("Jazz", isGenre = true),
    GENRE_POP("Pop", isGenre = true),
    GENRE_RNB("R&B", isGenre = true),
    GENRE_ROCK("Rock", isGenre = true),
}

data class TvDisplayArtist(
    val id: String?,
    val name: String,
    val thumbnailUrl: String?,
    val trackCount: Int = 0,
)

data class TvDisplayAlbum(
    val id: String?,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val songCount: Int = 0,
)

/**
 * 10-Foot Television Library Screen exactly matching Apple TV tvOS layout:
 * - Left vertical sidebar menu with category pills ("Recently Added", "Playlists", "Artists", "Albums", "Songs", "Compilations", "GENRE" headers).
 * - Focused sidebar item highlights with an Apple TV rounded white pill.
 * - Right content pane displays dynamic grid with circular artist avatars, square playlists/albums, or songs list.
 * - Circular Play, Shuffle and Sort action pills on the top right.
 * - In-place Playlist Creation Dialog and full playback integration.
 */
@Composable
fun TvLibraryScreen(
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
    onReleaseClick: (browseId: String?, playlistId: String?, title: String, subtitle: String?, thumb: String?) -> Unit = { _, _, _, _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedSection by remember { mutableStateOf(LibrarySection.ARTISTS) }
    var viewingPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val likedSongs by LibraryStore.likedSongs.collectAsStateWithLifecycle()
    val recentSongs by LibraryStore.recentSongs.collectAsStateWithLifecycle()
    val ytmHistorySongs by LibraryStore.ytmHistorySongs.collectAsStateWithLifecycle()
    val ytAccount by GoogleAuthStore.state.collectAsStateWithLifecycle()
    val playlists by LibraryStore.userPlaylists.collectAsStateWithLifecycle()
    val savedPlaylists by LibraryStore.savedPlaylists.collectAsStateWithLifecycle()
    val savedAlbums by LibraryStore.savedAlbums.collectAsStateWithLifecycle()
    val likedArtists by LibraryStore.likedArtists.collectAsStateWithLifecycle()

    BackHandler {
        if (viewingPlaylist != null) {
            viewingPlaylist = null
        } else {
            onBack()
        }
    }

    val newReleases by NewReleasesRepository.releases.collectAsStateWithLifecycle()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        NewReleasesRepository.ensureLoaded()
    }

    // Subscribed artists ONLY - no random filler
    val displayArtists = remember(likedArtists) {
        likedArtists.map { entry ->
            TvDisplayArtist(
                id = entry.browseId,
                name = entry.title,
                thumbnailUrl = entry.thumbnailUrl,
            )
        }
    }

    // Saved albums ONLY - no random filler
    val displayAlbums = remember(savedAlbums) {
        savedAlbums.map { a ->
            TvDisplayAlbum(
                id = a.browseId,
                title = a.title,
                artist = a.subtitle ?: "Various Artists",
                thumbnailUrl = a.thumbnailUrl,
            )
        }
    }

    // Real Recently Added Albums from NewReleasesRepository + savedAlbums like app module
    val displayRecentlyAdded = remember(savedAlbums, newReleases, settingsState.hideExplicitContent) {
        val visibleNew = ContentLocaleCatalog.filterVisible(newReleases, settingsState.hideExplicitContent) {
            it.isExplicit
        }
        val albumsOnly = visibleNew.filter { it.releaseType == ReleaseType.ALBUM }
        val newReleaseItems = (albumsOnly + visibleNew).distinctBy { it.browseId }.map { nr ->
            TvDisplayAlbum(
                id = nr.browseId,
                title = nr.title,
                artist = nr.artist,
                thumbnailUrl = nr.thumbnailUrl,
            )
        }
        val userAlbums = savedAlbums.map { a ->
            TvDisplayAlbum(
                id = a.browseId,
                title = a.title,
                artist = a.subtitle ?: "Various Artists",
                thumbnailUrl = a.thumbnailUrl,
            )
        }
        (userAlbums + newReleaseItems).distinctBy { it.id }
    }

    // Created and saved playlists ONLY
    val displayPlaylists = remember(playlists, savedPlaylists) {
        val userList = playlists
        val savedList = savedPlaylists.map { sp ->
            UserPlaylist(
                id = sp.browseId,
                name = sp.title,
                coverImageUrl = sp.thumbnailUrl,
                isEditable = false,
            )
        }
        (userList + savedList).distinctBy { it.id }
    }

    // All songs combined for genres
    val allSongs = remember(likedSongs, recentSongs) {
        (likedSongs + recentSongs).distinctBy { it.id }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvAppleSilverBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 20.dp, start = 40.dp, end = 40.dp),
        ) {
            // ── Left Sidebar: Apple TV Library Navigation ────────────────────────
            LazyColumn(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val primarySections = listOf(
                    LibrarySection.RECENTLY_ADDED,
                    LibrarySection.PLAYLISTS,
                    LibrarySection.ARTISTS,
                    LibrarySection.ALBUMS,
                    LibrarySection.HISTORY,
                    LibrarySection.COMPILATIONS,
                )

                items(primarySections) { section ->
                    TvSidebarPill(
                        label = section.label,
                        isSelected = selectedSection == section && viewingPlaylist == null,
                        onClick = {
                            viewingPlaylist = null
                            selectedSection = section
                        },
                    )
                }

                item {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "GENRE",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 11.5.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                val genreSections = listOf(
                    LibrarySection.GENRE_ALTERNATIVE,
                    LibrarySection.GENRE_BLUES,
                    LibrarySection.GENRE_CLASSICAL,
                    LibrarySection.GENRE_DANCE,
                    LibrarySection.GENRE_HIPHOP,
                    LibrarySection.GENRE_JAZZ,
                    LibrarySection.GENRE_POP,
                    LibrarySection.GENRE_RNB,
                    LibrarySection.GENRE_ROCK,
                )

                items(genreSections) { section ->
                    TvSidebarPill(
                        label = section.label,
                        isSelected = selectedSection == section && viewingPlaylist == null,
                        onClick = {
                            viewingPlaylist = null
                            selectedSection = section
                        },
                    )
                }
            }

            Spacer(Modifier.width(36.dp))

            // ── Right Content Area ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // Top Action Header (Section Title + Play, Shuffle, Sort Action Pills)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val currentTitle = viewingPlaylist?.name ?: selectedSection.label
                    Text(
                        text = currentTitle,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Play All Action
                        TvCircleActionButton(
                            icon = Icons.Rounded.PlayArrow,
                            contentDescription = "Play All",
                            onClick = {
                                val tracks = when {
                                    viewingPlaylist != null -> viewingPlaylist?.songs?.mapNotNull { it.toMusicTrack() }.orEmpty()
                                    selectedSection == LibrarySection.HISTORY -> recentSongs.mapNotNull { it.toMusicTrack() }
                                    selectedSection == LibrarySection.RECENTLY_ADDED -> recentSongs.mapNotNull { it.toMusicTrack() }
                                    else -> likedSongs.mapNotNull { it.toMusicTrack() }
                                }
                                if (tracks.isNotEmpty()) {
                                    MusicPlayerState.play(tracks.first(), tracks, expandPlayer = false)
                                }
                            },
                        )

                        // Shuffle All Action
                        TvCircleActionButton(
                            icon = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            onClick = {
                                val tracks = when {
                                    viewingPlaylist != null -> viewingPlaylist?.songs?.mapNotNull { it.toMusicTrack() }?.shuffled().orEmpty()
                                    selectedSection == LibrarySection.HISTORY -> recentSongs.mapNotNull { it.toMusicTrack() }.shuffled()
                                    selectedSection == LibrarySection.RECENTLY_ADDED -> recentSongs.mapNotNull { it.toMusicTrack() }.shuffled()
                                    else -> likedSongs.mapNotNull { it.toMusicTrack() }.shuffled()
                                }
                                if (tracks.isNotEmpty()) {
                                    MusicPlayerState.play(tracks.first(), tracks, expandPlayer = false)
                                }
                            },
                        )

                        // Sort / Menu Action
                        TvCircleActionButton(
                            icon = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = "Sort",
                            onClick = { /* Sort order cycling */ },
                        )
                    }
                }

                // Section Content
                Box(modifier = Modifier.fillMaxSize()) {
                    if (viewingPlaylist != null) {
                        val p = viewingPlaylist!!
                        TvPlaylistDetailView(
                            playlist = p,
                            onPlayTrack = { track, list ->
                                MusicPlayerState.play(track, list, expandPlayer = false)
                            },
                            onDelete = {
                                LibraryStore.deleteUserPlaylist(context, p.id)
                                viewingPlaylist = null
                            },
                        )
                    } else {
                        when (selectedSection) {
                            LibrarySection.ARTISTS -> {
                                TvArtistsGridView(
                                    artists = displayArtists,
                                    onArtistClick = onArtistClick,
                                )
                            }
                            LibrarySection.PLAYLISTS -> {
                                TvPlaylistsGridView(
                                    playlists = displayPlaylists,
                                    onCreateNew = { showCreatePlaylistDialog = true },
                                    onSelectPlaylist = { p ->
                                        onReleaseClick(null, p.id, p.name, "${p.songs.size} Songs", p.leadArtwork)
                                    },
                                )
                            }
                            LibrarySection.ALBUMS -> {
                                TvAlbumsGridView(
                                    albums = displayAlbums,
                                    onSelectAlbum = { album ->
                                        onReleaseClick(album.id, null, album.title, album.artist, album.thumbnailUrl)
                                    },
                                )
                            }
                            LibrarySection.HISTORY -> {
                                TvHistoryView(
                                    recentSongs = recentSongs,
                                    ytmHistorySongs = ytmHistorySongs,
                                    isYtSignedIn = ytAccount.isSignedIn,
                                    onPlayTrack = { track, list ->
                                        MusicPlayerState.play(track, list, expandPlayer = false)
                                    },
                                )
                            }
                            LibrarySection.RECENTLY_ADDED -> {
                                TvAlbumsGridView(
                                    albums = displayRecentlyAdded.ifEmpty { displayAlbums },
                                    onSelectAlbum = { album ->
                                        onReleaseClick(album.id, null, album.title, album.artist, album.thumbnailUrl)
                                    },
                                )
                            }
                            LibrarySection.COMPILATIONS -> {
                                TvAlbumsGridView(
                                    albums = displayAlbums.take(6),
                                    onSelectAlbum = { album ->
                                        onReleaseClick(album.id, null, album.title, album.artist, album.thumbnailUrl)
                                    },
                                )
                            }
                            else -> {
                                // Genre sections
                                val genreName = selectedSection.label
                                TvGenreView(
                                    genre = genreName,
                                    allSongs = allSongs,
                                    onPlayTrack = { track, list ->
                                        MusicPlayerState.play(track, list, expandPlayer = false)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Create New Playlist Modal Dialog ─────────────────────────────────
        if (showCreatePlaylistDialog) {
            TvCreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name ->
                    if (name.isNotBlank()) {
                        LibraryStore.createUserPlaylist(context, name.trim())
                    }
                    showCreatePlaylistDialog = false
                },
            )
        }
    }
}

// ── Left Sidebar Pill Item ─────────────────────────────────────────────────────────────────────

@Composable
private fun TvSidebarPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val bg = when {
        isFocused -> Color.White
        isSelected -> Color.White.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused -> Color.Black
        isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.65f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .tvFocusable(
                shape = RoundedCornerShape(12.dp),
                scaleOnFocus = 1.02f,
                elevationOnFocus = 0.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 15.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Header Circle Action Button (Play, Shuffle, Sort) ──────────────────────────────────────────

@Composable
private fun TvCircleActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .size(36.dp)
            .tvFocusable(
                shape = CircleShape,
                scaleOnFocus = 1.12f,
                elevationOnFocus = 4.dp,
                interactionSource = interaction,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(if (isFocused) Color.White else Color.White.copy(alpha = 0.12f))
            .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.20f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(19.dp),
        )
    }
}

// ── Artists Grid View (Matching Screenshot Circular Avatars) ────────────────────────────────────

@Composable
private fun TvArtistsGridView(
    artists: List<TvDisplayArtist>,
    onArtistClick: (browseId: String?, name: String, thumb: String?) -> Unit,
) {
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No artists in your library yet",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 16.sp,
                fontFamily = SFProDisplay,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        items(artists, key = { it.name }) { artist ->
            TvArtistGridCard(
                artist = artist,
                onClick = { onArtistClick(artist.id, artist.name, artist.thumbnailUrl) },
            )
        }
    }
}

@Composable
private fun TvArtistGridCard(
    artist: TvDisplayArtist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(
                shape = CircleShape,
                scaleOnFocus = 1.10f,
                elevationOnFocus = 14.dp,
                interactionSource = interaction,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .shadow(if (isFocused) 20.dp else 6.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF))
                .border(if (isFocused) 2.5.dp else 1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.20f), CircleShape),
        ) {
            AsyncImage(
                model = artist.thumbnailUrl?.highQualityThumbnail(),
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = artist.name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontFamily = SFProDisplay,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

// ── Playlists Grid View (with New Playlist Card) ────────────────────────────────────────────────

@Composable
private fun TvPlaylistsGridView(
    playlists: List<UserPlaylist>,
    onCreateNew: () -> Unit,
    onSelectPlaylist: (UserPlaylist) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // "New Playlist" card
        item(key = "new_playlist_card") {
            val interaction = remember { MutableInteractionSource() }
            val isFocused by interaction.collectIsFocusedAsState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(
                        shape = RoundedCornerShape(16.dp),
                        scaleOnFocus = 1.08f,
                        elevationOnFocus = 12.dp,
                        interactionSource = interaction,
                        onClick = onCreateNew,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isFocused) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                        .border(
                            1.5.dp,
                            if (isFocused) Color.White else Color.White.copy(alpha = 0.25f),
                            RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New Playlist",
                        tint = if (isFocused) Color.White else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(48.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "New Playlist",
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            val interaction = remember { MutableInteractionSource() }
            val isFocused by interaction.collectIsFocusedAsState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(
                        shape = RoundedCornerShape(16.dp),
                        scaleOnFocus = 1.08f,
                        elevationOnFocus = 12.dp,
                        interactionSource = interaction,
                        onClick = { onSelectPlaylist(playlist) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                ) {
                    val cover = playlist.leadArtwork
                    if (!cover.isNullOrBlank()) {
                        AsyncImage(
                            model = cover.highQualityThumbnail(),
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.40f),
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
                Text(
                    text = "${playlist.songs.size} songs",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontFamily = SFProDisplay,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Albums Grid View ───────────────────────────────────────────────────────────────────────────

@Composable
private fun TvAlbumsGridView(
    albums: List<TvDisplayAlbum>,
    onSelectAlbum: (TvDisplayAlbum) -> Unit,
) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No saved albums in your library",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 16.sp,
                fontFamily = SFProDisplay,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(albums, key = { it.title + it.artist }) { album ->
            val interaction = remember { MutableInteractionSource() }
            val isFocused by interaction.collectIsFocusedAsState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(
                        shape = RoundedCornerShape(14.dp),
                        scaleOnFocus = 1.08f,
                        elevationOnFocus = 12.dp,
                        interactionSource = interaction,
                        onClick = { onSelectAlbum(album) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, if (isFocused) Color.White else Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                ) {
                    AsyncImage(
                        model = album.thumbnailUrl?.highQualityThumbnail(),
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = album.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
                Text(
                    text = album.artist,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontFamily = SFProDisplay,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Songs List View (Clean Apple TV Track Table) ────────────────────────────────────────────────

@Composable
private fun TvSongsListView(
    songs: List<SavedSongMetadata>,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No songs found in this section",
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 16.sp,
                fontFamily = SFProDisplay,
            )
        }
        return
    }

    val trackList = remember(songs) { songs.mapNotNull { it.toMusicTrack() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            val interaction = remember { MutableInteractionSource() }
            val isFocused by interaction.collectIsFocusedAsState()

            val isCurrentTrack = song.id == MusicPlayerState.currentTrack?.id
            val isPlaying = MusicPlayerState.isPlaying && isCurrentTrack

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .tvFocusable(
                        shape = RoundedCornerShape(12.dp),
                        scaleOnFocus = 1.02f,
                        elevationOnFocus = 0.dp,
                        interactionSource = interaction,
                        onClick = {
                            val track = song.toMusicTrack()
                            if (track != null) onPlayTrack(track, trackList)
                        },
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isFocused) Color.White.copy(alpha = 0.22f) else Color(0x10FFFFFF))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPlaying) {
                    Box(
                        modifier = Modifier.width(28.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        AnimatedEqualizer(
                            isPlaying = true,
                            color = if (isFocused) Color.White else AppleMusicRed,
                            size = 18.dp,
                        )
                    }
                } else {
                    Text(
                        text = "${index + 1}",
                        color = Color.White.copy(alpha = 0.40f),
                        fontSize = 13.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(28.dp),
                    )
                }

                AsyncImage(
                    model = song.thumbnailUrl.highQualityThumbnail(),
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artists,
                        color = Color.White.copy(alpha = 0.60f),
                        fontSize = 12.5.sp,
                        fontFamily = SFProDisplay,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (song.isExplicit) {
                    ExplicitBadge(color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.width(12.dp))
                }

                if (!song.album.isNullOrBlank()) {
                    Text(
                        text = song.album,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontFamily = SFProDisplay,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(160.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                val durationText = remember(song.durationSec) {
                    val m = song.durationSec / 60
                    val s = song.durationSec % 60
                    "%d:%02d".format(m, s)
                }
                Text(
                    text = durationText,
                    color = Color.White.copy(alpha = 0.40f),
                    fontSize = 13.sp,
                    fontFamily = SFProDisplay,
                )
            }
        }
    }
}

// ── Listening History View with Switch (All, Recent Plays, YouTube Music) ─────────────────

private enum class TvHistoryFilter(val label: String) {
    ALL("All"),
    LOCAL("Recent Plays"),
    YOUTUBE("YouTube Music"),
}

@Composable
private fun TvHistoryView(
    recentSongs: List<SavedSongMetadata>,
    ytmHistorySongs: List<SavedSongMetadata>,
    isYtSignedIn: Boolean,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    var activeFilter by remember { mutableStateOf(TvHistoryFilter.ALL) }

    val filteredList = remember(activeFilter, recentSongs, ytmHistorySongs) {
        when (activeFilter) {
            TvHistoryFilter.ALL -> {
                (recentSongs + ytmHistorySongs)
                    .distinctBy { it.id }
                    .sortedByDescending { it.addedAtMs }
            }
            TvHistoryFilter.LOCAL -> recentSongs
            TvHistoryFilter.YOUTUBE -> ytmHistorySongs
        }
    }

    val trackList = remember(filteredList) { filteredList.mapNotNull { it.toMusicTrack() } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab switch header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TvHistoryFilter.values().forEach { filter ->
                val interaction = remember { MutableInteractionSource() }
                val isFocused by interaction.collectIsFocusedAsState()
                val isSelected = activeFilter == filter

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .tvFocusable(
                            shape = RoundedCornerShape(17.dp),
                            scaleOnFocus = 1.05f,
                            elevationOnFocus = 0.dp,
                            interactionSource = interaction,
                            onClick = { activeFilter = filter },
                        )
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            when {
                                isFocused -> Color.White
                                isSelected -> Color.White.copy(alpha = 0.28f)
                                else -> Color.White.copy(alpha = 0.08f)
                            }
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = filter.label,
                        color = if (isFocused) Color.Black else Color.White,
                        fontSize = 13.5.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        if (filteredList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (activeFilter == TvHistoryFilter.YOUTUBE && !isYtSignedIn) {
                        "Sign in to Google / YouTube Music in Settings to view YTM history"
                    } else {
                        "No history in this section"
                    },
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 16.sp,
                    fontFamily = SFProDisplay,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(filteredList, key = { _, s -> s.id }) { index, song ->
                    val interaction = remember { MutableInteractionSource() }
                    val isFocused by interaction.collectIsFocusedAsState()
                    val isCurrentTrack = song.id == MusicPlayerState.currentTrack?.id
                    val isPlaying = MusicPlayerState.isPlaying && isCurrentTrack

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .tvFocusable(
                                shape = RoundedCornerShape(12.dp),
                                scaleOnFocus = 1.02f,
                                elevationOnFocus = 0.dp,
                                interactionSource = interaction,
                                onClick = {
                                    val track = song.toMusicTrack()
                                    if (track != null) {
                                        onPlayTrack(track, trackList)
                                    }
                                },
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFocused) Color.White.copy(alpha = 0.22f) else Color(0x10FFFFFF))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isPlaying) {
                            Box(
                                modifier = Modifier.width(28.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                AnimatedEqualizer(
                                    isPlaying = true,
                                    color = if (isFocused) Color.White else AppleMusicRed,
                                    size = 18.dp,
                                )
                            }
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = Color.White.copy(alpha = 0.40f),
                                fontSize = 13.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(28.dp),
                            )
                        }

                        AsyncImage(
                            model = song.thumbnailUrl.highQualityThumbnail(),
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontFamily = SFProDisplay,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (song.isExplicit) {
                                    Spacer(Modifier.width(6.dp))
                                    ExplicitBadge(color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.85f))
                                }
                            }
                            Text(
                                text = song.artists,
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 12.5.sp,
                                fontFamily = SFProDisplay,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val timeText = formatHistoryTime(song.addedAtMs)
                        if (timeText.isNotBlank()) {
                            Text(
                                text = timeText,
                                color = Color.White.copy(alpha = 0.40f),
                                fontSize = 12.sp,
                                fontFamily = SFProDisplay,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }

                        val durationText = remember(song.durationSec) {
                            val m = song.durationSec / 60
                            val s = song.durationSec % 60
                            "%d:%02d".format(m, s)
                        }
                        Text(
                            text = durationText,
                            color = Color.White.copy(alpha = 0.40f),
                            fontSize = 13.sp,
                            fontFamily = SFProDisplay,
                        )
                    }
                }
            }
        }
    }
}

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
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))
        }
    } else {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
    }
}

// ── Playlist Detail View (Songs inside Playlist with Delete Action) ─────────────────────────────

@Composable
private fun TvPlaylistDetailView(
    playlist: UserPlaylist,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
    onDelete: () -> Unit,
) {
    val trackList = remember(playlist.songs) { playlist.songs.mapNotNull { it.toMusicTrack() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${playlist.songs.size} songs",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 14.sp,
                fontFamily = SFProDisplay,
            )

            TvCircleActionButton(
                icon = Icons.Rounded.Delete,
                contentDescription = "Delete Playlist",
                onClick = onDelete,
            )
        }

        if (playlist.songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "This playlist is empty. Add songs from player or search!",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 16.sp,
                    fontFamily = SFProDisplay,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(playlist.songs, key = { idx, s -> s.id + "_$idx" }) { index, song ->
                    val interaction = remember { MutableInteractionSource() }
                    val isFocused by interaction.collectIsFocusedAsState()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .tvFocusable(
                                shape = RoundedCornerShape(12.dp),
                                scaleOnFocus = 1.02f,
                                elevationOnFocus = 0.dp,
                                interactionSource = interaction,
                                onClick = {
                                    val track = song.toMusicTrack()
                                    if (track != null) onPlayTrack(track, trackList)
                                },
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isFocused) Color.White.copy(alpha = 0.22f) else Color(0x10FFFFFF))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White.copy(alpha = 0.40f),
                            fontSize = 13.sp,
                            modifier = Modifier.width(28.dp),
                        )
                        AsyncImage(
                            model = song.thumbnailUrl.highQualityThumbnail(),
                            contentDescription = song.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontFamily = SFProDisplay,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artists,
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Genre View ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvGenreView(
    genre: String,
    allSongs: List<SavedSongMetadata>,
    onPlayTrack: (MusicTrack, List<MusicTrack>) -> Unit,
) {
    val genreFiltered = remember(genre, allSongs) {
        // Filter songs loosely or return top songs for this genre
        allSongs.filter { s ->
            s.title.contains(genre, ignoreCase = true) ||
            s.artists.contains(genre, ignoreCase = true) ||
            (s.album?.contains(genre, ignoreCase = true) == true)
        }.ifEmpty { allSongs.take(15) }
    }

    TvSongsListView(
        songs = genreFiltered,
        onPlayTrack = onPlayTrack,
    )
}

// ── Create New Playlist Modal Dialog for TV ────────────────────────────────────────────────────

@Composable
private fun TvCreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xE61C1C1E))
                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "New Playlist",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = SFProDisplay,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(16.dp))

            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = SFProDisplay,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCreate(name) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { innerTextField ->
                    if (name.isEmpty()) {
                        Text(
                            text = "Playlist name",
                            color = Color.White.copy(alpha = 0.40f),
                            fontSize = 16.sp,
                            fontFamily = SFProDisplay,
                        )
                    }
                    innerTextField()
                },
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Cancel Button
                val cancelInteraction = remember { MutableInteractionSource() }
                val cancelFocused by cancelInteraction.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .tvFocusable(
                            shape = RoundedCornerShape(12.dp),
                            scaleOnFocus = 1.04f,
                            elevationOnFocus = 0.dp,
                            interactionSource = cancelInteraction,
                            onClick = onDismiss,
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (cancelFocused) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Create Button
                val createInteraction = remember { MutableInteractionSource() }
                val createFocused by createInteraction.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .tvFocusable(
                            shape = RoundedCornerShape(12.dp),
                            scaleOnFocus = 1.04f,
                            elevationOnFocus = 4.dp,
                            interactionSource = createInteraction,
                            onClick = { onCreate(name) },
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (createFocused) Color.White else Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Create",
                        color = if (createFocused) Color.Black else Color.White,
                        fontSize = 15.sp,
                        fontFamily = SFProDisplay,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
