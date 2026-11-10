package com.darkxvenom.itune.ui.library

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.AudioDownloader
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.artist.ArtistRepository
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.library.LibraryDestination
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedCollectionEntry
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.data.library.UserPlaylist
import com.darkxvenom.itune.data.model.BrowseTarget
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.HEADER_ART_PX
import com.darkxvenom.itune.data.model.LikeStatus
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.data.model.PlaybackSourceType
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.ui.artist.components.ActionRow
import com.darkxvenom.itune.ui.artist.components.ActionRowStyle
import com.darkxvenom.itune.ui.artist.components.ArtistSongActionsMenu
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.HeldContextMenu
import com.darkxvenom.itune.ui.artist.components.HeldItem
import com.darkxvenom.itune.ui.artist.components.IosMenuPopover
import com.darkxvenom.itune.ui.artist.components.IosMenuQuickAction
import com.darkxvenom.itune.ui.artist.components.IosMenuQuickActions
import com.darkxvenom.itune.ui.artist.components.IosMenuRow
import com.darkxvenom.itune.ui.artist.components.IosMenuSeparator
import com.darkxvenom.itune.ui.artist.components.LocalActionRowStyle
import com.darkxvenom.itune.ui.artist.components.LocalAppBackdrop
import com.darkxvenom.itune.ui.artist.components.LocalLiquidGlassEnabled
import com.darkxvenom.itune.ui.artist.components.LocalReduceDynamicBlur
import com.darkxvenom.itune.ui.artist.components.LongPressOrigin
import com.darkxvenom.itune.ui.artist.components.MenuAnchor
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.LayerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.SnapshotBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.layerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberLayerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberSnapshotBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.snapshotBackdropSource
import com.darkxvenom.itune.ui.artist.components.isGlassSupported
import com.darkxvenom.itune.ui.artist.components.lightweightLiquidGlass
import com.darkxvenom.itune.ui.artist.components.liquidGlass
import com.darkxvenom.itune.ui.artist.components.liquidGlassRim
import com.darkxvenom.itune.ui.artist.components.menuAnchor
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibrarySongSort(val label: String) {
    RECENT("Recently Added"),
    TITLE_ASC("Title (A–Z)"),
    ARTIST_ASC("Artist (A–Z)"),
}

@Immutable
internal data class LibraryTopMenuRequest(
    val destination: LibraryDestination,
    val songs: List<Song>,
    val stationLabel: String,
    val showSort: Boolean = true,
    val userPlaylist: UserPlaylist? = null,
    val initialStep: PopupPlaylistStep = PopupPlaylistStep.MENU,
)

@Immutable
internal data class LibrarySortMenuRequest(
    val currentSort: LibrarySongSort,
)

val LibraryCrimsonAccent = Color(0xFFFF2D55)
val LibraryGlassBaseBg = Color(0xFF060609)

@Stable
class LibraryHostController internal constructor(
    internal val context: Context,
    private val scope: CoroutineScope,
    internal val pageBackdrop: SnapshotBackdrop,
    internal val appBackdrop: LayerBackdrop,
    internal val hazeState: HazeState,
) {
    internal val stack = mutableStateListOf<LibraryDestination>()
    internal val listStates = mutableStateMapOf<LibraryDestination, LazyListState>()
    internal val gridStates = mutableStateMapOf<LibraryDestination, LazyGridState>()

    internal var searchOpen by mutableStateOf(false)
    internal var searchQuery by mutableStateOf("")
    internal var focusSearchNext by mutableStateOf(false)
    internal var songSort by mutableStateOf(LibrarySongSort.RECENT)

    internal var songActions by mutableStateOf<Song?>(null)
    internal var songMenuOrigin by mutableStateOf<HeldItem?>(null)
    internal var songMenuAnchor by mutableStateOf<Rect?>(null)

    internal var collectionActions by mutableStateOf<SavedCollectionEntry?>(null)
    internal var collectionMenuOrigin by mutableStateOf<HeldItem?>(null)
    internal var collectionMenuAnchor by mutableStateOf<Rect?>(null)

    internal var activeUserPlaylistId by mutableStateOf<String?>(null)
    internal var userPlaylistActions by mutableStateOf<UserPlaylist?>(null)
    internal var userPlaylistMenuOrigin by mutableStateOf<HeldItem?>(null)
    internal var userPlaylistMenuAnchor by mutableStateOf<Rect?>(null)

    internal var topMenuRequest by mutableStateOf<LibraryTopMenuRequest?>(null)
    internal var topMenuAnchor by mutableStateOf<Rect?>(null)

    internal var sortMenuRequest by mutableStateOf<LibrarySortMenuRequest?>(null)
    internal var sortMenuAnchor by mutableStateOf<Rect?>(null)

    internal val downloadingIds = mutableStateMapOf<String, Boolean>()

    val isOpen: Boolean by derivedStateOf { stack.isNotEmpty() }
    val currentDestination: LibraryDestination? by derivedStateOf { stack.lastOrNull() }

    fun openMenu(itemId: String) {
        val dest = when (itemId) {
            "lm1" -> LibraryDestination.PLAYLISTS
            "lm2" -> LibraryDestination.ARTISTS
            "lm3" -> LibraryDestination.ALBUMS
            "lm4" -> LibraryDestination.SONGS
            "lm5" -> LibraryDestination.LIKED_SONGS
            else -> return
        }
        openDestination(dest)
    }

    fun openDestination(destination: LibraryDestination) {
        LibraryStore.ensureInit(context)
        ArtistStateStore.ensureInit(context)
        clearMenus()
        searchOpen = false
        searchQuery = ""
        if (stack.lastOrNull() != destination) {
            stack += destination
        }
    }

    fun openLikedSongs() = openDestination(LibraryDestination.LIKED_SONGS)

    fun openUserPlaylist(playlistId: String) {
        activeUserPlaylistId = playlistId
        openDestination(LibraryDestination.USER_PLAYLIST)
    }

    fun pop(): Boolean {
        if (hasAnyPopupOpen()) {
            clearMenus()
            return true
        }
        if (searchOpen) {
            searchOpen = false
            searchQuery = ""
            return true
        }
        if (stack.isNotEmpty()) {
            val popped = stack.removeAt(stack.lastIndex)
            if (popped == LibraryDestination.USER_PLAYLIST) {
                activeUserPlaylistId = null
            }
            return true
        }
        return false
    }

    fun clearAll() {
        clearMenus()
        searchOpen = false
        searchQuery = ""
        activeUserPlaylistId = null
        stack.clear()
    }

    fun toggleSearch() {
        searchOpen = !searchOpen
        if (searchOpen) {
            focusSearchNext = true
        } else {
            searchQuery = ""
        }
    }

    fun hasAnyPopupOpen(): Boolean =
        songActions != null ||
            collectionActions != null ||
            userPlaylistActions != null ||
            topMenuRequest != null ||
            sortMenuRequest != null

    fun clearMenus() {
        songActions = null
        songMenuOrigin = null
        songMenuAnchor = null
        collectionActions = null
        collectionMenuOrigin = null
        collectionMenuAnchor = null
        userPlaylistActions = null
        userPlaylistMenuOrigin = null
        userPlaylistMenuAnchor = null
        topMenuRequest = null
        topMenuAnchor = null
        sortMenuRequest = null
        sortMenuAnchor = null
    }

    internal fun openUserPlaylistMenu(playlist: UserPlaylist) {
        val origin = LongPressOrigin.consume()
        val anchor = if (origin == null) MenuAnchor.consume() else null
        clearMenus()
        userPlaylistMenuOrigin = origin
        userPlaylistMenuAnchor = anchor
        userPlaylistActions = playlist
    }

    internal fun openSongMenu(song: Song) {
        val origin = LongPressOrigin.consume()
        val anchor = if (origin == null) MenuAnchor.consume() else null
        clearMenus()
        songMenuOrigin = origin
        songMenuAnchor = anchor
        songActions = song
    }

    internal fun openCollectionMenu(entry: SavedCollectionEntry) {
        val origin = LongPressOrigin.consume()
        val anchor = if (origin == null) MenuAnchor.consume() else null
        clearMenus()
        collectionMenuOrigin = origin
        collectionMenuAnchor = anchor
        collectionActions = entry
    }

    internal fun openTopMenu(request: LibraryTopMenuRequest) {
        val anchor = MenuAnchor.consume()
        clearMenus()
        topMenuAnchor = anchor
        topMenuRequest = request
    }

    internal fun openCreatePlaylistPopover() {
        openTopMenu(
            LibraryTopMenuRequest(
                destination = LibraryDestination.PLAYLISTS,
                songs = emptyList(),
                stationLabel = "Playlists",
                showSort = false,
                initialStep = PopupPlaylistStep.CREATE_PLAYLIST,
            ),
        )
    }

    internal fun openSortMenu() {
        val anchor = MenuAnchor.consume()
        clearMenus()
        sortMenuAnchor = anchor
        sortMenuRequest = LibrarySortMenuRequest(songSort)
    }

    fun playSongs(songs: List<Song>, index: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val tracks = songs.map { it.toMusicTrack() }
        val queue = if (shuffle) tracks.shuffled() else tracks
        val targetIdx = if (shuffle) 0 else index.coerceIn(queue.indices)
        MusicPlayerState.play(
            track = queue[targetIdx],
            newQueue = queue,
            expandPlayer = false,
        )
    }

    fun startRadioFromSongs(songs: List<Song>, stationLabel: String) {
        if (songs.isEmpty()) {
            showToast("Add some songs first to start radio")
            return
        }
        val activeId = MusicPlayerState.currentTrack?.id
        val seed = songs.firstOrNull { it.videoId == activeId } ?: songs.random()
        val seedTrack = seed.toMusicTrack()
        val remaining = songs.filterNot { it.videoId == seed.videoId }.shuffled().take(8).map { it.toMusicTrack() }
        MusicPlayerState.play(
            track = seedTrack,
            newQueue = listOf(seedTrack) + remaining,
            expandPlayer = false,
        )
        showToast("Starting $stationLabel Radio...")
        scope.launch {
            val radioSongs = ArtistRepository.radio(seed.videoId, preferVideo = seed.isVideo)
                .getOrNull()
                .orEmpty()
                .map { it.toMusicTrack() }
                .filter { candidate -> candidate.id != seedTrack.id && remaining.none { it.id == candidate.id } }
            if (radioSongs.isNotEmpty()) {
                MusicPlayerState.addToQueue(radioSongs)
            }
        }
    }

    fun startRadioForSong(song: Song) {
        val baseTrack = song.toMusicTrack()
        MusicPlayerState.play(baseTrack, listOf(baseTrack), expandPlayer = false)
        showToast("Starting ${song.title} Radio...")
        scope.launch {
            val radioSongs = ArtistRepository.radio(song.videoId, preferVideo = song.isVideo)
                .getOrNull()
                .orEmpty()
                .map { it.toMusicTrack() }
                .filter { it.id != baseTrack.id }
            if (radioSongs.isNotEmpty()) {
                MusicPlayerState.addToQueue(radioSongs)
            }
        }
    }

    fun startRadioForArtist(entry: SavedCollectionEntry) {
        showToast("Starting ${entry.title} Radio...")
        scope.launch {
            val artistPage = ArtistRepository.artistPage(entry.browseId).getOrNull()
            val radioTarget = artistPage?.radio
            if (radioTarget != null) {
                val queue = ArtistRepository.watchQueue(radioTarget).getOrNull().orEmpty().map { it.toMusicTrack() }
                if (queue.isNotEmpty()) {
                    MusicPlayerState.play(queue.first(), queue, expandPlayer = false)
                    return@launch
                }
            }
            val topSongs = artistPage?.songs.orEmpty()
            if (topSongs.isNotEmpty()) {
                startRadioFromSongs(topSongs, entry.title)
            } else {
                showToast("Couldn't start radio for ${entry.title}")
            }
        }
    }

    fun withCollectionSongs(entry: SavedCollectionEntry, action: (List<Song>) -> Unit) {
        scope.launch {
            val loaded = if (entry.type == BrowseType.ARTIST) {
                ArtistRepository.artistPage(entry.browseId).getOrNull()?.songs.orEmpty()
            } else {
                ArtistRepository.allSongs(entry.browseId).getOrNull().orEmpty()
            }
            val stamped = loaded.map { song ->
                song.copy(
                    albumName = if (entry.type == BrowseType.ALBUM) song.albumName ?: entry.title else song.albumName,
                    playbackSource = entry.title,
                    playbackSourceType = PlaybackSourceType.BROWSE,
                    playbackSourceId = entry.browseId,
                )
            }
            if (stamped.isEmpty()) {
                showToast("Couldn't load tracks for ${entry.title}")
            } else {
                action(stamped)
            }
        }
    }

    fun openDetail(
        browseId: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String? = null,
        type: BrowseType = BrowseType.OTHER,
    ) {
        clearMenus()
        MusicPlayerState.pendingArtistNav = PendingArtistNav(
            browseId = browseId,
            name = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            initialType = type,
        )
    }

