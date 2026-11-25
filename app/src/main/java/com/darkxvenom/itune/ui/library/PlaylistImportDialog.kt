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

package com.darkxvenom.itune.ui.library


import androidx.compose.ui.res.stringResource
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.SettingsStore
import com.darkxvenom.itune.data.spotify.PlaylistImportResult
import com.darkxvenom.itune.data.spotify.PlaylistLinkImporter
import com.darkxvenom.itune.data.spotify.SpotifyLibrary
import com.darkxvenom.itune.data.spotify.SpotifyPlaylist
import com.darkxvenom.itune.data.spotify.SpotifyPlaylistAutoSyncManager
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SPOTIFY_GREEN = Color(0xFF1DB954)
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

private val IMPORT_LANGUAGES = listOf(
    "No Preference",
    "English",
    "Hindi",
    "Punjabi",
    "Tamil",
    "Telugu",
    "Kannada",
    "Malayalam",
)

/**
 * iOS-styled Apple Music modal pop-up dialog for importing public & private Spotify playlists,
 * with Spotify account playlist picker, language filter chips, and automatic future-song sync.
 */
@Composable
internal fun PlaylistImportDialog(
    initialLink: String = "",
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val settingsState by SettingsStore.state.collectAsStateWithLifecycle()
    val isSpotifySignedIn = settingsState.spotifySpdcToken.isNotBlank()

    var link by remember { mutableStateOf(initialLink) }
    var selectedLanguage by remember { mutableStateOf("No Preference") }
    var autoAddFutureSongs by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var result by remember { mutableStateOf<PlaylistImportResult?>(null) }

    // Connected Spotify account playlists (includes private playlists & Liked Songs)
    var myPlaylists by remember { mutableStateOf<List<SpotifyPlaylist>>(emptyList()) }
    var myPlaylistsLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isSpotifySignedIn) {
        if (!isSpotifySignedIn) {
            myPlaylists = emptyList()
            myPlaylistsLoading = false
            return@LaunchedEffect
        }
        myPlaylistsLoading = true
        runCatching { SpotifyLibrary.playlists() }
            .onSuccess { myPlaylists = it }
            .onFailure { myPlaylists = emptyList() }
        myPlaylistsLoading = false
    }

    fun startImport() {
        val extractedId = PlaylistLinkImporter.extractPlaylistId(link)
        if (extractedId == null) {
            error = if (isSpotifySignedIn) {
                "Enter a valid Spotify playlist link or pick one of your playlists above."
            } else {
                "Enter a valid Spotify playlist link."
            }
            return
        }
        val storedUrl = PlaylistLinkImporter.canonicalUrl(extractedId, link)
        loading = true
        error = null
        status = "Fetching playlist from Spotify…"
        progress = 0 to 0
        scope.launch {
            try {
                val effectiveLanguage = if (selectedLanguage == "No Preference") null else selectedLanguage
                val imported = PlaylistLinkImporter.importPlaylist(
                    playlistIdOrUrl = extractedId,
                    language = effectiveLanguage,
                ) { done, total ->
                    progress = done to total
                    status = "Matching songs · $done of $total"
                }

                if (imported.tracks.isEmpty()) {
                    error = if (isSpotifySignedIn) {
                        "No tracks could be matched from this Spotify playlist."
                    } else {
                        "No tracks could be matched. If it is private, sign in to Spotify in Settings first."
                    }
                } else {
                    LibraryStore.ensureInit(context)
                    val created = LibraryStore.createUserPlaylist(
                        context = context,
                        name = imported.title,
                        initialSongs = imported.tracks.map { SavedSongMetadata.fromTrack(it) },
                    )
                    if (autoAddFutureSongs) {
                        SpotifyPlaylistAutoSyncManager.registerAutoSync(
                            context = context,
                            localPlaylistId = created.id,
                            playlistName = created.name,
                            spotifyPlaylistId = imported.playlistId.ifBlank { extractedId },
                            spotifyUrl = storedUrl,
                            languagePreference = effectiveLanguage,
                            initialSourceTracks = imported.sourceTracks,
                        )
                    }
                    result = imported
                    val syncNote = if (autoAddFutureSongs) " • Auto-sync ON" else ""
                    status = "Imported ${imported.tracks.size} of ${imported.tracks.size + imported.unmatched.size} tracks$syncNote"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = e.message?.takeIf(String::isNotBlank) ?: "Playlist import failed."
            } finally {
                loading = false
            }
        }
    }

    val completed = result != null

    Dialog(
        onDismissRequest = { if (!loading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !loading,
            dismissOnClickOutside = !loading,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setDimAmount(0.20f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (!loading) onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .widthIn(max = 364.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { /* Catch clicks inside card */ }
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black,
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1E1E22))
                    .border(
                        width = 0.75.dp,
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(22.dp),
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Centered circular Spotify Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_spotify),
                        contentDescription = stringResource(R.string.ui_spotify),
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (completed) "Playlist Imported" else "Import Spotify Playlist",
                    color = AppleTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (completed) {
                        result?.title.orEmpty()
                    } else if (isSpotifySignedIn) {
                        "Paste any public or private playlist link, or pick one of your Spotify playlists below."
                    } else {
                        "Paste a public playlist link (sign in to Spotify in Settings to import private playlists too)."
                    },
                    color = AppleTextSecondary,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Content Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .padding(horizontal = 18.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!completed) {
                        // Connected Spotify Playlists Quick Picker (Public & Private)
                        if (isSpotifySignedIn) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_your_spotify_playlists_public_private),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SPOTIFY_GREEN,
                                    )
                                    if (myPlaylistsLoading) {
                                        CircularProgressIndicator(
                                            color = SPOTIFY_GREEN,
                                            strokeWidth = 1.6.dp,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                                if (myPlaylists.isNotEmpty()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        items(myPlaylists, key = { it.id }) { pl ->
                                            val plUrl = PlaylistLinkImporter.canonicalUrl(pl.id)
                                            val isSelected = PlaylistLinkImporter.extractPlaylistId(link) == pl.id
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isSelected) SPOTIFY_GREEN.copy(alpha = 0.22f)
                                                        else Color(0xFF141416),
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) SPOTIFY_GREEN else Color.White.copy(alpha = 0.10f),
                                                        shape = RoundedCornerShape(12.dp),
                                                    )
                                                    .clickable(enabled = !loading) {
                                                        link = plUrl
                                                        error = null
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                            ) {
                                                AsyncImage(
                                                    model = pl.imageUrl,
                                                    contentDescription = pl.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color.White.copy(alpha = 0.08f)),
                                                )
                                                Text(
                                                    text = pl.name,
                                                    color = if (isSelected) SPOTIFY_GREEN else Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 130.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = link,
                            onValueChange = {
                                link = it
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading,
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SPOTIFY_GREEN,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                                focusedContainerColor = Color(0xFF141416),
                                unfocusedContainerColor = Color(0xFF141416),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                            placeholder = {
                                Text(
                                    stringResource(R.string.ui_https_open_spotify_com_playlist),
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 13.sp,
                                )
                            },
                            trailingIcon = {
                                if (link.isNotBlank() && !loading) {
                                    IconButton(onClick = { link = ""; error = null }) {
                                        Icon(
                                            Icons.Rounded.Clear,
                                            contentDescription = stringResource(R.string.ui_clear),
                                            tint = Color.White.copy(alpha = 0.6f),
                                        )
                                    }
                                } else if (!loading) {
                                    IconButton(onClick = {
                                        val clip = clipboardManager.getText()?.text.orEmpty().trim()
                                        if (clip.isNotBlank()) {
                                            link = clip
                                            error = null
                                        }
                                    }) {
                                        Icon(
                                            Icons.Rounded.ContentPaste,
                                            contentDescription = stringResource(R.string.ui_paste),
                                            tint = SPOTIFY_GREEN,
                                        )
                                    }
                                }
                            },
                        )

                        // Checkbox: Add Future Songs Automatically
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141416))
                                .border(
                                    width = 0.8.dp,
                                    color = if (autoAddFutureSongs) SPOTIFY_GREEN.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable(enabled = !loading) {
                                    autoAddFutureSongs = !autoAddFutureSongs
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (autoAddFutureSongs) SPOTIFY_GREEN else Color.Transparent)
                                    .border(
                                        width = 1.6.dp,
                                        color = if (autoAddFutureSongs) SPOTIFY_GREEN else Color.White.copy(alpha = 0.42f),
                                        shape = RoundedCornerShape(6.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (autoAddFutureSongs) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(R.string.ui_checked),
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ui_add_future_songs_automatically),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.ui_stores_this_link_and_auto_adds_any_new_songs_on_app_op),
                                    color = AppleTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.5.sp,
                                )
                            }
                        }

                        // Language chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.ui_music_language_preference),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppleTextSecondary,
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(IMPORT_LANGUAGES) { lang ->
                                    val selected = lang == selectedLanguage
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (selected) SPOTIFY_GREEN
                                                else Color.White.copy(alpha = 0.08f),
                                            )
                                            .clickable(enabled = !loading) {
                                                selectedLanguage = lang
                                            }
                                            .padding(horizontal = 11.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = lang,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) Color.Black else Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (loading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val (done, total) = progress
                            if (total > 0) {
                                val fraction = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = SPOTIFY_GREEN,
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = SPOTIFY_GREEN,
                                    trackColor = Color.White.copy(alpha = 0.12f),
                                )
                            }
                            Text(
                                text = status ?: "Importing…",
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (completed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SPOTIFY_GREEN.copy(alpha = 0.15f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = SPOTIFY_GREEN,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.ui_saved_to_your_library),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                )
                                Text(
                                    text = status.orEmpty(),
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }

                    if (error != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF3B30).copy(alpha = 0.15f))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = error!!,
                                color = Color(0xFFFF453A),
                                fontSize = 12.sp,
                            )
                        }
                    }

                    val unmatched = result?.unmatched.orEmpty()
                    if (unmatched.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.ui_track_s_couldn_t_be_matched, unmatched.size),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 110.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141416))
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            unmatched.forEach { track ->
                                Text(
                                    text = stringResource(R.string.ui_text_a3edb2b714, track.title, track.artist),
                                    color = Color.White.copy(alpha = 0.60f),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Apple alert-style stacked action buttons
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.12f),
                )

                // Primary Button ("Import" / "Done")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(
                            enabled = if (completed) true else (link.isNotBlank() && !loading),
                        ) {
                            if (completed) onDismiss() else startImport()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val enabled = completed || (link.isNotBlank() && !loading)
                    Text(
                        text = if (completed) "Done" else if (loading) "Importing…" else "Import",
                        color = if (enabled) SPOTIFY_GREEN else AppleTextSecondary.copy(alpha = 0.35f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                    )
                }

                if (!completed) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = Color.White.copy(alpha = 0.12f),
                    )

                    // Secondary Button ("Cancel")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(enabled = !loading) { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = AppleTextSecondary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-0.2).sp,
                        )
                    }
                }
            }
        }
    }
}
