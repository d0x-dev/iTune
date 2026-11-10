package com.darkxvenom.itune.ui.library

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.UserPlaylist
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.ROW_ART_PX
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.ui.artist.components.IosMenuRow
import com.darkxvenom.itune.ui.artist.components.IosMenuSeparator
import com.darkxvenom.itune.ui.artist.components.iosMenuColors
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.player.MorePopupVectorOptionRow
import com.darkxvenom.itune.ui.player.PopupMenuDivider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class PopupPlaylistStep {
    MENU,
    ADD_TO_PLAYLIST,
    CREATE_PLAYLIST,
    RENAME_PLAYLIST,
}

@Immutable
data class PendingPlaylistPayload(
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val staticSongs: List<SavedSongMetadata> = emptyList(),
    val lazyLoader: (suspend () -> List<SavedSongMetadata>)? = null,
) {
    companion object {
        fun fromSong(song: Song): PendingPlaylistPayload {
            val meta = SavedSongMetadata.fromSong(song)
            return PendingPlaylistPayload(
                title = song.title,
                subtitle = song.artist,
                artworkUrl = meta.thumbnailUrl.ifBlank { song.artworkAt(ROW_ART_PX) },
                staticSongs = listOf(meta),
            )
        }
    }
}

/**
 * Dynamic playlist cover built from added song thumbnails:
 * 0 = sleek dark-glass gradient music icon
 * 1 = single cover art
 * 2 = split 2-column cover collage
 * 3 = 2 top + 1 wide bottom collage
 * 4+ = 2x2 mosaic collage
 */
@Composable
fun PlaylistCollageAvatar(
    artworkUrls: List<String>,
    modifier: Modifier = Modifier,
    size: Dp? = 56.dp,
    cornerRadius: Dp = 14.dp,
) {
    val cleanUrls = remember(artworkUrls) {
        artworkUrls.filter { it.isNotBlank() }.distinct().take(4)
    }
    val shape = RoundedCornerShape(cornerRadius)
    val artPx = if ((size ?: 160.dp) >= 140.dp) CARD_ART_PX else ROW_ART_PX
    val iconSize = (size ?: 140.dp) * 0.44f

    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF3E1023),
                        Color(0xFF1A1423),
                        Color(0xFF101016),
                    ),
                ),
            )
            .border(0.75.dp, Color.White.copy(alpha = 0.20f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when (cleanUrls.size) {
            0 -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(iconSize),
                )
            }
            1 -> {
                AsyncImage(
                    model = cleanUrls[0].artworkAt(artPx),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            2 -> {
                Row(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = cleanUrls[0].artworkAt(artPx),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    AsyncImage(
                        model = cleanUrls[1].artworkAt(artPx),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            3 -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = cleanUrls[0].artworkAt(artPx),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        AsyncImage(
                            model = cleanUrls[1].artworkAt(artPx),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    AsyncImage(
                        model = cleanUrls[2].artworkAt(artPx),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = cleanUrls[0].artworkAt(artPx),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        AsyncImage(
                            model = cleanUrls[1].artworkAt(artPx),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = cleanUrls[2].artworkAt(artPx),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        AsyncImage(
                            model = cleanUrls[3].artworkAt(artPx),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberResolvedPayloadSongs(payload: PendingPlaylistPayload?): Pair<List<SavedSongMetadata>, Boolean> {
    var resolved by remember(payload) { mutableStateOf(payload?.staticSongs.orEmpty()) }
    var loading by remember(payload) {
        mutableStateOf(payload != null && payload.staticSongs.isEmpty() && payload.lazyLoader != null)
    }

    LaunchedEffect(payload) {
        val loader = payload?.lazyLoader
        if (payload == null || payload.staticSongs.isNotEmpty() || loader == null) {
            loading = false
            resolved = payload?.staticSongs.orEmpty()
            return@LaunchedEffect
        }
        loading = true
        try {
            resolved = loader.invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            resolved = emptyList()
        } finally {
            loading = false
        }
    }

    return resolved to loading
}

private suspend fun resolveAndAddToPlaylist(
    context: Context,
    target: UserPlaylist,
    payload: PendingPlaylistPayload,
    preloadedSongs: List<SavedSongMetadata>,
): Boolean {
    val songs = if (preloadedSongs.isNotEmpty() || payload.lazyLoader == null) {
        preloadedSongs
    } else {
        payload.lazyLoader.invoke()
    }
    if (songs.isEmpty()) {
        Toast.makeText(context, "No tracks available to add", Toast.LENGTH_SHORT).show()
        return false
    }
    val added = LibraryStore.addSongsToUserPlaylist(context, target.id, songs)
    val msg = when {
        added == 0 -> "Already in \"${target.name}\""
        added == 1 -> "Added to \"${target.name}\""
        else -> "Added $added songs to \"${target.name}\""
    }
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    return true
}

private suspend fun resolveAndCreatePlaylist(
    context: Context,
    name: String,
    payload: PendingPlaylistPayload?,
    preloadedSongs: List<SavedSongMetadata>,
): UserPlaylist {
    val songs = if (payload == null) {
        emptyList()
    } else if (preloadedSongs.isNotEmpty() || payload.lazyLoader == null) {
        preloadedSongs
    } else {
        payload.lazyLoader.invoke()
    }
    val created = LibraryStore.createUserPlaylist(context, name, songs)
    val msg = if (songs.isEmpty()) {
        "Created \"${created.name}\""
    } else if (songs.size == 1) {
        "Created \"${created.name}\" & added track"
    } else {
        "Created \"${created.name}\" (${songs.size} songs)"
    }
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    return created
}

/**
 * Coordinates in-place Apple iOS sub-menu transitions inside any IosMenuPopover or HeldContextMenu
 * without closing the current popup or spawning a separate modal dialog.
 */
