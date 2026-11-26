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

package com.darkxvenom.itune.ui.spotify


import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.spotify.PlaylistImportTrack
import com.darkxvenom.itune.data.spotify.PlaylistLinkImporter
import com.darkxvenom.itune.data.spotify.SpotifyImportTrack
import com.darkxvenom.itune.data.spotify.SpotifyImporter
import com.darkxvenom.itune.data.spotify.SpotifyLibrary
import com.darkxvenom.itune.data.spotify.SpotifyPlaylist
import com.darkxvenom.itune.data.spotify.SpotifyPlaylistAutoSyncManager
import com.darkxvenom.itune.data.spotify.SpotifyTrack
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.theme.AppleMusicRed
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SpotifyGreen = Color(0xFF1DB954)
private val CardBg = Color(0xFF1C1C1E)
private val DividerColor = Color(0xFF2C2C2E)
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Composable
fun SpotifyIntegrationScreen(
    hasMiniPlayer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.state.collectAsStateWithLifecycle()

    val isConnected = settings.spotifySpdcToken.isNotBlank()
    var tokenInput by remember(settings.spotifySpdcToken) { mutableStateOf(settings.spotifySpdcToken) }
    var showWebLogin by remember { mutableStateOf(false) }
    var showManualToken by remember { mutableStateOf(false) }

    // Spotify library state
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var playlistsLoading by remember { mutableStateOf(false) }
    var playlistsError by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }

    // Opened Spotify playlist detail state
    var openedPlaylist by remember { mutableStateOf<SpotifyPlaylist?>(null) }
    var openedTracks by remember { mutableStateOf<List<SpotifyTrack>>(emptyList()) }
    var tracksLoading by remember { mutableStateOf(false) }
    var tracksError by remember { mutableStateOf<String?>(null) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }

    // Playlist URL import state (public & private when signed in)
    var importLink by remember { mutableStateOf("") }
    var importLanguage by remember { mutableStateOf("") }
    var autoSyncFutureSongs by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var importDone by remember { mutableIntStateOf(0) }
    var importTotal by remember { mutableIntStateOf(0) }

    LaunchedEffect(settings.spotifySpdcToken, reloadNonce) {
        if (settings.spotifySpdcToken.isBlank()) {
            playlists = emptyList()
            playlistsError = null
            playlistsLoading = false
            return@LaunchedEffect
        }
        playlistsLoading = true
        playlistsError = null
        runCatching { SpotifyLibrary.playlists() }
            .onSuccess { playlists = it }
            .onFailure { playlistsError = it.message ?: "Failed to load Spotify library" }
        playlistsLoading = false
    }

    LaunchedEffect(openedPlaylist?.id) {
        val pl = openedPlaylist ?: return@LaunchedEffect
        openedTracks = emptyList()
        tracksError = null
        tracksLoading = true
        runCatching {
            SpotifyLibrary.tracks(pl.id) { partial ->
                openedTracks = partial
            }
        }.onFailure {
            tracksError = it.message ?: "Failed to load playlist tracks"
        }
        tracksLoading = false
    }

    BackHandler(enabled = openedPlaylist != null && !showWebLogin) {
        openedPlaylist = null
    }

    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 50 }
    }

    fun saveImportedTracksToLibrary(title: String, tracks: List<com.darkxvenom.itune.backend.MusicTrack>): com.darkxvenom.itune.data.library.UserPlaylist? {
        if (tracks.isEmpty()) return null
        val metadata = tracks.map { t ->
            SavedSongMetadata(
                id = t.id,
                title = t.title,
                artists = t.artists,
                artistId = t.artistId,
                album = t.album,
                albumId = t.albumId,
                thumbnailUrl = t.thumbnailUrl.orEmpty(),
                durationSec = t.durationSec,
                isVideo = t.isVideo,
                isExplicit = t.isExplicit,
            )
        }
        return LibraryStore.createUserPlaylist(context, title, metadata)
    }

    fun startImportFromList(
        playlistName: String,
        sourceTracks: List<SpotifyImportTrack>,
        language: String?,
        spotifyPlaylistId: String? = null,
        spotifyUrl: String? = null,
        enableAutoSync: Boolean = false,
    ) {
        if (importing) return
        importing = true
        importDone = 0
        importTotal = sourceTracks.size
        importStatus = "Matching songs 0 of ${sourceTracks.size}…"
        scope.launch {
            val result = runCatching {
                SpotifyImporter.resolveToTracks(sourceTracks, language) { done, total ->
                    importDone = done
                    importTotal = total
                    importStatus = "Matching songs $done of $total…"
                }
            }
            importing = false
            result.onSuccess { (matched, unmatched) ->
                if (matched.isEmpty()) {
                    importStatus = "None of those songs were found on YouTube Music."
                } else {
                    val created = saveImportedTracksToLibrary(playlistName, matched)
                    if (enableAutoSync && created != null && !spotifyPlaylistId.isNullOrBlank()) {
                        val storedUrl = spotifyUrl?.trim()?.ifBlank { PlaylistLinkImporter.canonicalUrl(spotifyPlaylistId) }
                            ?: PlaylistLinkImporter.canonicalUrl(spotifyPlaylistId)
                        SpotifyPlaylistAutoSyncManager.registerAutoSync(
                            context = context,
                            localPlaylistId = created.id,
                            playlistName = playlistName,
                            spotifyPlaylistId = spotifyPlaylistId,
                            spotifyUrl = storedUrl,
                            languagePreference = language,
                            initialSourceTracks = sourceTracks.map {
                                PlaylistImportTrack(
                                    title = it.title,
                                    artist = it.artist,
                                )
                            },
                        )
                    }
                    val extra = if (unmatched.isNotEmpty()) " (${unmatched.size} unmatched)" else ""
                    val syncNote = if (enableAutoSync && !spotifyPlaylistId.isNullOrBlank()) " • Auto-sync on" else ""
                    importStatus = "Imported '$playlistName' • ${matched.size} songs$extra$syncNote"
                    Toast.makeText(context, context.getString(R.string.toast_imported_songs, playlistName, matched.size), Toast.LENGTH_LONG).show()
                }
            }.onFailure { err ->
                importStatus = err.message ?: "Import failed"
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppleOledBlack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
        ) {
            if (openedPlaylist != null) {
                val activePl = openedPlaylist!!
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = topBarContentPadding() + 8.dp,
                        bottom = if (hasMiniPlayer) 200.dp else 110.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "pl_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = activePl.imageUrl,
                                contentDescription = activePl.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardBg),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activePl.name,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        if (!activePl.owner.isNullOrBlank()) append("${activePl.owner} • ")
                                        append("${openedTracks.size} tracks")
                                    },
                                    color = AppleTextSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                            if (openedTracks.isNotEmpty() && !importing) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SpotifyGreen.copy(alpha = 0.18f))
                                        .clickable {
                                            val mapped = openedTracks.map { t ->
                                                SpotifyImportTrack(
                                                    title = t.title,
                                                    artist = t.artist,
                                                    album = t.album,
                                                    durationSec = t.durationMs.takeIf { it > 0 }?.div(1000L),
                                                )
                                            }
                                            startImportFromList(
                                                playlistName = activePl.name,
                                                sourceTracks = mapped,
                                                language = null,
                                                spotifyPlaylistId = activePl.id,
                                                spotifyUrl = PlaylistLinkImporter.canonicalUrl(activePl.id),
                                                enableAutoSync = autoSyncFutureSongs,
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = stringResource(R.string.ui_import),
                                            tint = SpotifyGreen,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = stringResource(R.string.ui_import),
                                            color = SpotifyGreen,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (importing || !importStatus.isNullOrBlank()) {
                        item(key = "pl_import_status") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardBg)
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = importStatus.orEmpty(),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                )
                                if (importing && importTotal > 0) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { (importDone.toFloat() / importTotal.toFloat()).coerceIn(0f, 1f) },
                                        color = SpotifyGreen,
                                        trackColor = Color.White.copy(alpha = 0.14f),
                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                    )
                                }
                            }
                        }
                    }

                    if (tracksLoading && openedTracks.isEmpty()) {
                        item(key = "tracks_loading") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(36.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = SpotifyGreen)
                            }
                        }
                    }

                    tracksError?.let { err ->
                        item(key = "tracks_err") {
                            Text(
                                text = err,
                                color = AppleMusicRed,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            )
                        }
                    }

                    items(openedTracks, key = { "${it.id}_${it.title}" }) { track ->
                        val isResolving = resolvingTrackId == track.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = resolvingTrackId == null) {
                                    resolvingTrackId = track.id
                                    scope.launch {
                                        val matched = SpotifyImporter.matchTrack(track)
                                        resolvingTrackId = null
                                        if (matched != null) {
                                            withContext(Dispatchers.Main) {
                                                MusicPlayerState.play(matched, listOf(matched), expandPlayer = true)
                                            }
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.toast_couldn_t_find_on_youtube_music, track.title),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = track.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardBg),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    color = AppleTextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isResolving) {
                                CircularProgressIndicator(
                                    color = SpotifyGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.play),
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = topBarContentPadding() + 8.dp,
                        bottom = if (hasMiniPlayer) 200.dp else 110.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Header
                    item(key = "spotify_header") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_spotify),
                                    contentDescription = stringResource(R.string.ui_spotify),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.ui_spotify_canvas),
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp,
                                )
                                Text(
                                    text = stringResource(R.string.ui_spotify_canvas_apple_music_tidal_community_motion_cove),
                                    color = AppleTextSecondary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    // ── 1. Spotify Account Connection Card ──
                    item(key = "spotify_account_card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBg)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isConnected) "Spotify Connected" else "Connect Spotify",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = if (isConnected) {
                                            "Spotify Canvas & your playlists are ready"
                                        } else {
                                            "Sign in to enable Spotify Canvas and load your playlists. Playback uses YouTube Music."
                                        },
                                        color = AppleTextSecondary,
                                        fontSize = 13.sp,
                                    )
                                }
                                if (isConnected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(SpotifyGreen.copy(alpha = 0.18f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = SpotifyGreen,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.ui_active),
                                                color = SpotifyGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SpotifyGreen)
                                        .clickable {
                                            clearSpotifyWebSession()
                                            showWebLogin = true
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (isConnected) "Re-sign in" else "Sign in with Spotify",
                                        color = Color.Black,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                if (isConnected) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.10f))
                                            .clickable {
                                                clearSpotifyWebSession()
                                                SettingsStore.setSpotifySpdcToken(context, "")
                                                tokenInput = ""
                                                Toast.makeText(context, context.getString(R.string.toast_spotify_disconnected), Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ui_disconnect),
                                            color = AppleMusicRed,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }

                            // Manual sp_dc token toggle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showManualToken = !showManualToken }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (showManualToken) "Hide manual sp_dc token setup" else "Use sp_dc cookie manually",
                                    color = SpotifyGreen,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            if (showManualToken) {
                                Text(
                                    text = "1. Sign in at open.spotify.com in a browser.\n" +
                                        "2. Open Developer Tools → Application → Cookies.\n" +
                                        "3. Copy the value of the sp_dc cookie.\n" +
                                        "4. Paste it below and tap Save.",
                                    color = AppleTextSecondary,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp,
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                ) {
                                    if (tokenInput.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.ui_paste_sp_dc_token_here),
                                            color = Color.White.copy(alpha = 0.35f),
                                            fontSize = 13.5.sp,
                                        )
                                    }
                                    BasicTextField(
                                        value = tokenInput,
                                        onValueChange = { tokenInput = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                        cursorBrush = SolidColor(SpotifyGreen),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .clickable {
                                            SettingsStore.setSpotifySpdcToken(context, tokenInput)
                                            Toast.makeText(context, context.getString(R.string.toast_spotify_sp_dc_token_saved), Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_save_token),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    // ── 2. Canvas Playback Preferences Card ──
                    item(key = "canvas_settings_card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBg),
                        ) {
                            CanvasToggleRow(
                                title = "Animated Cover Art (All Canvas)",
                                subtitle = "Play looping Canvas videos from Spotify, Apple Music, Tidal & Community",
                                checked = settings.animatedArtwork,
                                onCheckedChange = {
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_ANIMATED_ARTWORK, it)
                                },
                            )
                            HorizontalDivider(color = DividerColor, thickness = 0.6.dp, modifier = Modifier.padding(start = 16.dp))
                            CanvasToggleRow(
                                title = "Auto-Hide Player Controls",
                                subtitle = "Minimize player controls after 5 seconds while Canvas is playing (tap Canvas to toggle)",
                                checked = settings.spotifyCanvasAutoHide,
                                onCheckedChange = {
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_SPOTIFY_CANVAS_AUTO_HIDE, it)
                                },
                            )
                            HorizontalDivider(color = DividerColor, thickness = 0.6.dp, modifier = Modifier.padding(start = 16.dp))
                            CanvasToggleRow(
                                title = "Show Synced Lyrics Over Canvas",
                                subtitle = "Show animated synced lyrics above the song name when Canvas controls are hidden",
                                checked = settings.showSyncedLyricsOverCanvas,
                                onCheckedChange = {
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_SHOW_SYNCED_LYRICS_OVER_CANVAS, it)
                                },
                            )
                            HorizontalDivider(color = DividerColor, thickness = 0.6.dp, modifier = Modifier.padding(start = 16.dp))
                            CanvasToggleRow(
                                title = "Prioritize Spotify Canvas",
                                subtitle = "Try Spotify before Apple Music, Tidal and Community providers",
                                checked = settings.prioritizeSpotifyCanvas,
                                onCheckedChange = {
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_PRIORITIZE_SPOTIFY_CANVAS, it)
                                },
                            )
                            HorizontalDivider(color = DividerColor, thickness = 0.6.dp, modifier = Modifier.padding(start = 16.dp))
                            CanvasToggleRow(
                                title = "Play Animated Cover Over Cellular",
                                subtitle = "Allow Canvas videos to load on metered mobile data connections",
                                checked = settings.canvasOverCellular,
                                onCheckedChange = {
                                    SettingsStore.setBoolean(context, SettingsStore.KEY_CANVAS_OVER_CELLULAR, it)
                                },
                            )
                        }
                    }

                    // ── 2b. Active Canvas Providers Card ──
                    item(key = "canvas_providers_card") {
                        val providers = if (settings.prioritizeSpotifyCanvas) {
                            listOf(
                                Triple("Spotify Canvas", "9:16 vertical full-screen video (requires Spotify sign-in)", isConnected),
                                Triple("Apple Music Motion Artwork", "3:4 portrait & 1:1 square HLS motion covers", true),
                                Triple("Tidal Video Covers", "1280×1280 looping MP4 album covers", true),
                                Triple("Community Canvas", "Community-curated looping video manifest", true),
                            )
                        } else {
                            listOf(
                                Triple("Apple Music Motion Artwork", "3:4 portrait & 1:1 square HLS motion covers", true),
                                Triple("Tidal Video Covers", "1280×1280 looping MP4 album covers", true),
                                Triple("Community Canvas", "Community-curated looping video manifest", true),
                                Triple("Spotify Canvas", "9:16 vertical full-screen video (requires Spotify sign-in)", isConnected),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBg)
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ui_canvas_providers_lookup_order),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            providers.forEachIndexed { index, (name, desc, ready) ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = DividerColor,
                                        thickness = 0.6.dp,
                                        modifier = Modifier.padding(start = 16.dp),
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        color = AppleTextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(22.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                        Text(
                                            text = name,
                                            color = Color.White,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = desc,
                                            color = AppleTextSecondary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(
                                                if (ready && settings.animatedArtwork) SpotifyGreen.copy(alpha = 0.16f)
                                                else Color.White.copy(alpha = 0.08f),
                                            )
                                            .padding(horizontal = 9.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = when {
                                                !settings.animatedArtwork -> "Off"
                                                ready -> "Active"
                                                else -> "Sign in"
                                            },
                                            color = when {
                                                !settings.animatedArtwork -> AppleTextSecondary
                                                ready -> SpotifyGreen
                                                else -> Color(0xFFFF9F0A)
                                            },
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 3. Import Public or Private Spotify Playlist by Link ──
                    item(key = "spotify_import_link_card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBg)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ui_import_spotify_playlist),
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (isConnected) {
                                    "Paste a public or private Spotify playlist link. Songs are matched on YouTube Music and saved in your Library."
                                } else {
                                    "Paste a public Spotify playlist link (or sign in above to import private playlists)."
                                },
                                color = AppleTextSecondary,
                                fontSize = 13.sp,
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            ) {
                                if (importLink.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.ui_https_open_spotify_com_playlist),
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 13.5.sp,
                                    )
                                }
                                BasicTextField(
                                    value = importLink,
                                    onValueChange = { importLink = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                    cursorBrush = SolidColor(SpotifyGreen),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .border(1.dp, DividerColor, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                if (importLanguage.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.ui_language_preference_optional_e_g_hindi_punjabi),
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 13.sp,
                                    )
                                }
                                BasicTextField(
                                    value = importLanguage,
                                    onValueChange = { importLanguage = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                    cursorBrush = SolidColor(SpotifyGreen),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(
                                        1.dp,
                                        if (autoSyncFutureSongs) SpotifyGreen.copy(alpha = 0.45f) else DividerColor,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable(enabled = !importing) {
                                        autoSyncFutureSongs = !autoSyncFutureSongs
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = autoSyncFutureSongs,
                                    onCheckedChange = { if (!importing) autoSyncFutureSongs = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SpotifyGreen,
                                        uncheckedColor = Color.White.copy(alpha = 0.55f),
                                        checkmarkColor = Color.Black,
                                    ),
                                )
                                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                    Text(
                                        text = stringResource(R.string.ui_add_future_songs_automatically),
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_stores_this_link_and_auto_adds_any_new_songs_on_app_op),
                                        color = AppleTextSecondary,
                                        fontSize = 11.5.sp,
                                    )
                                }
                            }

                            if (!importStatus.isNullOrBlank()) {
                                Text(
                                    text = importStatus.orEmpty(),
                                    color = if (importStatus!!.startsWith("Imported")) SpotifyGreen else Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                )
                            }
                            if (importing && importTotal > 0) {
                                LinearProgressIndicator(
                                    progress = { (importDone.toFloat() / importTotal.toFloat()).coerceIn(0f, 1f) },
                                    color = SpotifyGreen,
                                    trackColor = Color.White.copy(alpha = 0.14f),
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (importing) Color.White.copy(alpha = 0.12f) else SpotifyGreen.copy(alpha = 0.22f))
                                    .clickable(enabled = !importing) {
                                        val plId = PlaylistLinkImporter.extractPlaylistId(importLink)
                                        if (plId == null) {
                                            importStatus = "That isn't a valid Spotify playlist link."
                                            return@clickable
                                        }
                                        val normalizedUrl = importLink.trim().ifBlank { PlaylistLinkImporter.canonicalUrl(plId) }
                                        importing = true
                                        importDone = 0
                                        importTotal = 0
                                        importStatus = "Reading Spotify playlist…"
                                        scope.launch {
                                            val fetched = runCatching { PlaylistLinkImporter.fetchPlaylistTracks(plId) }
                                            fetched.onFailure { err ->
                                                importing = false
                                                importStatus = err.message ?: "Failed to read Spotify playlist"
                                            }.onSuccess { (plTitle, tracks) ->
                                                importing = false
                                                val mapped = tracks.map { t ->
                                                    SpotifyImportTrack(
                                                        title = t.title,
                                                        artist = t.artist,
                                                    )
                                                }
                                                startImportFromList(
                                                    playlistName = plTitle,
                                                    sourceTracks = mapped,
                                                    language = importLanguage.ifBlank { null },
                                                    spotifyPlaylistId = plId,
                                                    spotifyUrl = normalizedUrl,
                                                    enableAutoSync = autoSyncFutureSongs,
                                                )
                                            }
                                        }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (importing) "Importing…" else "Import Playlist",
                                    color = if (importing) Color.White.copy(alpha = 0.6f) else SpotifyGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    // ── 4. Connected Spotify Playlists ──
                    if (isConnected) {
                        item(key = "spotify_playlists_header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_your_spotify_playlists),
                                    color = Color.White,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { reloadNonce++ },
                                    enabled = !playlistsLoading,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.ui_refresh_playlists),
                                        tint = Color.White.copy(alpha = 0.72f),
                                    )
                                }
                            }
                        }

                        if (playlistsLoading) {
                            item(key = "pl_loading") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = SpotifyGreen)
                                }
                            }
                        }

                        playlistsError?.let { err ->
                            item(key = "pl_err") {
                                Text(
                                    text = err,
                                    color = AppleMusicRed,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }

                        items(playlists, key = { it.id }) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CardBg)
                                    .clickable { openedPlaylist = pl }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = pl.imageUrl,
                                    contentDescription = pl.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.name,
                                        color = Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!pl.owner.isNullOrBlank()) {
                                        Text(
                                            text = pl.owner,
                                            color = AppleTextSecondary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = SfIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        TopFadeBlur(
            hazeState = hazeState,
            pageColor = AppleOledBlack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        BottomFadeScrim(
            withMiniPlayer = hasMiniPlayer,
            pageColor = AppleOledBlack,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        FrostedTopBar(
            transparentBackdrop = true,
            artworkPageChrome = true,
            backButtonHazeState = hazeState,
            onBack = {
                if (openedPlaylist != null) openedPlaylist = null else onBack()
            },
            modifier = Modifier.align(Alignment.TopCenter),
            centerContent = {
                if (openedPlaylist != null || isScrolled) {
                    FrostedTopBarTitlePill(hazeState = hazeState) {
                        Text(
                            text = openedPlaylist?.name ?: "Spotify & Canvas",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
        )

        // Full-screen Spotify login browser overlay (same pattern as GoogleLoginScreen, outside hazeSource)
        if (showWebLogin) {
            SpotifyLoginScreen(
                onConnected = { token ->
                    SettingsStore.setSpotifySpdcToken(context, token)
                    tokenInput = token
                    showWebLogin = false
                    reloadNonce++
                    Toast.makeText(context, context.getString(R.string.toast_spotify_connected), Toast.LENGTH_SHORT).show()
                },
                onClose = { showWebLogin = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CanvasToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = AppleTextSecondary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SpotifyGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF39393D),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}
