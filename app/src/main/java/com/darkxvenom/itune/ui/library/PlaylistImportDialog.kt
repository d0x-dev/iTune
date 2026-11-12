package com.darkxvenom.itune.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.spotify.PlaylistImportResult
import com.darkxvenom.itune.data.spotify.PlaylistLinkImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistImportDialog(
    onDismiss: () -> Unit,
    onImported: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var result by remember { mutableStateOf<PlaylistImportResult?>(null) }
    var createdPlaylistId by remember { mutableStateOf<String?>(null) }

    fun startImport() {
        val id = PlaylistLinkImporter.extractPlaylistId(link)
        if (id == null) {
            error = "Enter a valid public playlist link."
            return
        }
        loading = true
        error = null
        status = "Loading playlist…"
        progress = 0 to 0
        scope.launch {
            try {
                val imported = PlaylistLinkImporter.importPlaylist(id) { done, total ->
                    progress = done to total
                    status = "Matching songs · $done of $total"
                }
                if (imported.tracks.isEmpty()) {
                    error = "No tracks could be matched. Try another public playlist."
                } else {
                    LibraryStore.ensureInit(context)
                    val playlist = LibraryStore.createUserPlaylist(
                        context = context,
                        name = imported.title,
                        initialSongs = imported.tracks.map { SavedSongMetadata.fromTrack(it) },
                    )
                    result = imported
                    createdPlaylistId = playlist.id
                    status = "Imported ${imported.tracks.size} of ${imported.tracks.size + imported.unmatched.size} tracks."
                    onImported(playlist.id)
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
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(if (completed) "Playlist imported" else "Import playlist") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!completed) {
                    Text(
                        "Paste a public playlist link. Its songs will be matched to the iTune catalogue and saved as a new playlist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = link,
                        onValueChange = {
                            link = it
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        singleLine = true,
                        label = { Text("Playlist link") },
                        placeholder = { Text("https://open.spotify.com/playlist/…") },
                    )
                }
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(status ?: "Importing…", modifier = Modifier.padding(start = 12.dp))
                    }
                } else if (status != null) {
                    Text(status!!, style = MaterialTheme.typography.bodyMedium)
                }
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                val unmatched = result?.unmatched.orEmpty()
                if (unmatched.isNotEmpty()) {
                    Text(
                        "${unmatched.size} tracks could not be matched:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        unmatched.forEach { track ->
                            Text(
                                if (track.artist.isBlank()) track.title else "${track.title} · ${track.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (completed) {
                Button(onClick = onDismiss) { Text("Done") }
            } else {
                Button(
                    onClick = ::startImport,
                    enabled = link.isNotBlank() && !loading,
                ) { Text(if (loading) "Importing…" else "Import") }
            }
        },
        dismissButton = {
            if (!completed) {
                TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancel") }
            }
        },
    )
}
