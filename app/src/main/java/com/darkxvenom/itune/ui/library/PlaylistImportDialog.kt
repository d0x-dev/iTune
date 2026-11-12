package com.darkxvenom.itune.ui.library

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.spotify.PlaylistImportResult
import com.darkxvenom.itune.data.spotify.PlaylistLinkImporter
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SPOTIFY_GREEN = Color(0xFF1DB954)

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
 * iOS-styled Apple Music modal pop-up dialog for importing Spotify playlists,
 * matching [AvatarSelectionDialog] with frosted dark card, Apple alert dividers,
 * Spotify icon, language filter chips, and progress feedback.
 */
@Composable
internal fun PlaylistImportDialog(
    initialLink: String = "",
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf(initialLink) }
    var selectedLanguage by remember { mutableStateOf("No Preference") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var result by remember { mutableStateOf<PlaylistImportResult?>(null) }

    fun startImport() {
        val extractedId = PlaylistLinkImporter.extractPlaylistId(link)
        if (extractedId == null) {
            error = "Enter a valid public Spotify playlist link."
            return
        }
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
                    error = "No tracks could be matched. Check that the playlist is public."
                } else {
                    LibraryStore.ensureInit(context)
                    LibraryStore.createUserPlaylist(
                        context = context,
                        name = imported.title,
                        initialSongs = imported.tracks.map { SavedSongMetadata.fromTrack(it) },
                    )
                    result = imported
                    status = "Imported ${imported.tracks.size} of ${imported.tracks.size + imported.unmatched.size} tracks."
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
                    .fillMaxWidth(0.88f)
                    .widthIn(max = 350.dp)
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
                        contentDescription = "Spotify",
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
                    } else {
                        "Paste a public playlist link to match and save songs into your iTune library."
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
                        .padding(horizontal = 18.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (!completed) {
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
                                    "https://open.spotify.com/playlist/…",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 13.sp,
                                )
                            },
                            trailingIcon = {
                                if (link.isNotBlank() && !loading) {
                                    IconButton(onClick = { link = ""; error = null }) {
                                        Icon(
                                            Icons.Rounded.Clear,
                                            contentDescription = "Clear",
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
                                            contentDescription = "Paste",
                                            tint = SPOTIFY_GREEN,
                                        )
                                    }
                                }
                            },
                        )

                        // Language chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Music language preference",
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
                                    text = "Saved to your library!",
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
                            text = "${unmatched.size} track(s) couldn't be matched:",
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
                                    text = "• ${track.title} - ${track.artist}",
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

                // Apple alert-style stacked action buttons matching AvatarSelectionDialog
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
                            text = "Cancel",
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
