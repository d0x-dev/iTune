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
@Composable
fun IosInMenuPlaylistHost(
    menuKey: Any?,
    initialStep: PopupPlaylistStep = PopupPlaylistStep.MENU,
    payload: PendingPlaylistPayload? = null,
    renameTarget: UserPlaylist? = null,
    onCloseMenu: () -> Unit,
    mainMenuContent: @Composable (
        onOpenAddToPlaylist: () -> Unit,
        onOpenCreateEmpty: () -> Unit,
        onOpenRename: () -> Unit,
    ) -> Unit,
) {
    var step by remember(menuKey, initialStep) { mutableStateOf(initialStep) }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val dir = if (forward) 1 else -1
            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { dir * it / 5 }) togetherWith
                (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -dir * it / 5 })
        },
        label = "ios_in_menu_playlist_step",
    ) { activeStep ->
        when (activeStep) {
            PopupPlaylistStep.MENU -> mainMenuContent(
                { step = PopupPlaylistStep.ADD_TO_PLAYLIST },
                { step = PopupPlaylistStep.CREATE_PLAYLIST },
                { step = PopupPlaylistStep.RENAME_PLAYLIST },
            )
            PopupPlaylistStep.ADD_TO_PLAYLIST -> {
                val activePayload = payload
                if (activePayload == null) {
                    step = PopupPlaylistStep.MENU
                } else {
                    IosAddToPlaylistMenuView(
                        payload = activePayload,
                        onBack = { step = PopupPlaylistStep.MENU },
                        onCreateNewClick = { step = PopupPlaylistStep.CREATE_PLAYLIST },
                        onCompleted = onCloseMenu,
                    )
                }
            }
            PopupPlaylistStep.CREATE_PLAYLIST -> IosCreatePlaylistMenuView(
                payload = payload,
                backLabel = if (initialStep == PopupPlaylistStep.CREATE_PLAYLIST) "Cancel" else "Back",
                onBack = {
                    if (initialStep == PopupPlaylistStep.CREATE_PLAYLIST) {
                        onCloseMenu()
                    } else {
                        step = if (payload != null) PopupPlaylistStep.ADD_TO_PLAYLIST else PopupPlaylistStep.MENU
                    }
                },
                onDone = { onCloseMenu() },
            )
            PopupPlaylistStep.RENAME_PLAYLIST -> IosCreatePlaylistMenuView(
                renameTarget = renameTarget,
                backLabel = if (initialStep == PopupPlaylistStep.RENAME_PLAYLIST) "Cancel" else "Back",
                onBack = {
                    if (initialStep == PopupPlaylistStep.RENAME_PLAYLIST) onCloseMenu()
                    else step = PopupPlaylistStep.MENU
                },
                onDone = { onCloseMenu() },
            )
        }
    }
}

/**
 * In-place Apple-styled iOS popover step for selecting a pre-created playlist first
 * or switching to New Playlist inside the exact same IosMenuPopover / HeldContextMenu.
 */
@Composable
fun IosAddToPlaylistMenuView(
    payload: PendingPlaylistPayload,
    onBack: () -> Unit,
    onCreateNewClick: () -> Unit,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = iosMenuColors()
    val accent = MaterialTheme.colorScheme.primary
    LaunchedEffect(Unit) { LibraryStore.ensureInit(context) }
    val userPlaylists by LibraryStore.userPlaylists.collectAsStateWithLifecycle()
    val (resolvedSongs, loading) = rememberResolvedPayloadSongs(payload)
    var busy by remember(payload) { mutableStateOf(false) }
    val singleId = resolvedSongs.singleOrNull()?.id

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        // Compact iOS Menu Header with < Back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = SfIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = "Back",
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Add to Playlist",
                color = colors.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (loading || busy) {
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 1.8.dp,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        IosMenuSeparator(colors)

        // Pre-created Playlists FIRST!
        if (userPlaylists.isNotEmpty()) {
            userPlaylists.forEach { pl ->
                val alreadyIn = singleId != null && pl.songs.any { it.id == singleId }
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (pressed) colors.pressed else Color.Transparent)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = !busy,
                        ) {
                            busy = true
                            scope.launch {
                                try {
                                    resolveAndAddToPlaylist(context, pl, payload, resolvedSongs)
                                    onCompleted()
                                } finally {
                                    busy = false
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistCollageAvatar(
                        artworkUrls = pl.artworkUrls,
                        size = 38.dp,
                        cornerRadius = 9.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = pl.name,
                            color = colors.label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (pl.songs.size == 1) "1 song" else "${pl.songs.size} songs",
                            color = colors.secondaryLabel,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        imageVector = if (alreadyIn) SfIcons.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        tint = if (alreadyIn) accent else colors.secondaryLabel,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IosMenuSeparator(colors)
        }

        // Create New Playlist option inside the exact same popover
        IosMenuRow(
            label = "New Playlist...",
            icon = SfIcons.AddToPlaylist,
            iconTint = LibraryCrimsonAccent,
            onClick = { if (!busy) onCreateNewClick() },
        )
    }
}

/**
 * In-place Apple-styled iOS popover step for naming & creating a new playlist
 * (empty or initialized with pending tracks so its avatar builds from added song thumbnails).
 */
@Composable
fun IosCreatePlaylistMenuView(
    payload: PendingPlaylistPayload? = null,
    renameTarget: UserPlaylist? = null,
    backLabel: String = "Back",
    onBack: () -> Unit,
    onDone: (UserPlaylist?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = iosMenuColors()
    val accent = MaterialTheme.colorScheme.primary
    val (resolvedSongs, loading) = rememberResolvedPayloadSongs(payload)

    var nameInput by remember(renameTarget, payload) {
        mutableStateOf(renameTarget?.name.orEmpty())
    }
    var busy by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler(onBack = onBack)

    val previewArts = remember(renameTarget, payload, resolvedSongs) {
        when {
            renameTarget != null -> renameTarget.artworkUrls
            payload != null -> (resolvedSongs.mapNotNull { it.thumbnailUrl.ifBlank { null } } +
                listOfNotNull(payload.artworkUrl)).distinct().take(4)
            else -> emptyList()
        }
    }

    fun submit() {
        if (busy) return
        val clean = nameInput.trim()
        if (clean.isBlank()) {
            Toast.makeText(context, "Enter a playlist name", Toast.LENGTH_SHORT).show()
            return
        }
        if (renameTarget != null) {
            LibraryStore.renameUserPlaylist(context, renameTarget.id, clean)
            Toast.makeText(context, "Renamed to \"$clean\"", Toast.LENGTH_SHORT).show()
            onDone(renameTarget.copy(name = clean))
            return
        }
        busy = true
        scope.launch {
            try {
                val created = resolveAndCreatePlaylist(context, clean, payload, resolvedSongs)
                onDone(created)
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // iOS-native compact nav bar row inside the popover
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = backLabel,
                color = colors.secondaryLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !busy, onClick = onBack)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Text(
                text = if (renameTarget != null) "Rename" else "New Playlist",
                color = colors.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (busy || loading) {
                CircularProgressIndicator(
                    color = accent,
                    strokeWidth = 1.8.dp,
                    modifier = Modifier.padding(end = 6.dp).size(15.dp),
                )
            } else {
                val canSave = nameInput.isNotBlank()
                Text(
                    text = if (renameTarget != null) "Save" else "Create",
                    color = if (canSave) LibraryCrimsonAccent else colors.secondaryLabel.copy(alpha = 0.45f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = canSave, onClick = ::submit)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        IosMenuSeparator(colors)

        // Compact grouped iOS preview avatar + name input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaylistCollageAvatar(
                artworkUrls = previewArts,
                size = 44.dp,
                cornerRadius = 10.dp,
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White.copy(alpha = 0.09f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (nameInput.isEmpty()) {
                        Text(
                            text = "Playlist Name",
                            color = colors.secondaryLabel,
                            fontSize = 15.sp,
                        )
                    }
                    BasicTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        cursorBrush = SolidColor(LibraryCrimsonAccent),
                        textStyle = TextStyle(
                            color = colors.label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                if (nameInput.isNotEmpty()) {
                    IconButton(
                        onClick = { nameInput = "" },
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = colors.secondaryLabel,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }

        Text(
            text = when {
                renameTarget != null -> "Updates playlist title across Library."
                payload != null -> "Playlist avatar builds from added songs."
                else -> "Creates an empty playlist in Library."
            },
            color = colors.secondaryLabel,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }
}

/**
 * In-place playlist picker view styled directly inside Search's AppleFrostedPopupOverlay.
 */
