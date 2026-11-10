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

    fun downloadSong(song: Song) {
        if (downloadingIds[song.videoId] == true) return
        downloadingIds[song.videoId] = true
        showToast("Preparing download for ${song.title}...")
        scope.launch {
            try {
                val options = withContext(Dispatchers.IO) {
                    MusicBackend.extractAudioQualities(song.videoId)
                }
                val best = options.firstOrNull()
                if (best != null) {
                    AudioDownloader.download(context, song.toMusicTrack(), best)
                    showToast("Downloading ${song.title}")
                } else {
                    showToast("No downloadable stream found")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Download failed: ${e.message ?: "Error"}")
            } finally {
                downloadingIds.remove(song.videoId)
            }
        }
    }

    fun downloadSongs(songs: List<Song>) {
        if (songs.isEmpty()) {
            showToast("Nothing to download")
            return
        }
        showToast("Queuing ${songs.size} tracks for download...")
        scope.launch {
            songs.take(25).forEach { downloadSong(it) }
        }
    }

    fun removeCollection(entry: SavedCollectionEntry) {
        if (entry.type == BrowseType.ARTIST) {
            ArtistStateStore.toggleSubscription(
                context = context,
                browseId = entry.browseId,
                title = entry.title,
                subtitle = entry.subtitle,
                thumbnailUrl = entry.thumbnailUrl,
            )
            showToast("Removed ${entry.title} from Favorite Artists")
        } else {
            ArtistStateStore.toggleReleaseSaved(
                context = context,
                browseId = entry.browseId,
                title = entry.title,
                subtitle = entry.subtitle,
                thumbnailUrl = entry.thumbnailUrl,
                type = entry.type,
            )
            showToast("Removed ${entry.title} from Library")
        }
    }

    fun shareUrl(title: String, url: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(sendIntent, title))
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberLibraryHostController(): LibraryHostController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageBackdrop = rememberSnapshotBackdrop()
    val appBackdrop = rememberLayerBackdrop()
    val hazeState = remember { HazeState() }
    return remember(context, pageBackdrop, appBackdrop, hazeState) {
        LibraryHostController(
            context = context.applicationContext,
            scope = scope,
            pageBackdrop = pageBackdrop,
            appBackdrop = appBackdrop,
            hazeState = hazeState,
        )
    }
}

private fun Color.colorChroma(): Float =
    maxOf(red, green, blue) - minOf(red, green, blue)

/**
 * Full-bleed blurred liquid glass backdrop dynamically tinted from the user's cover art.
 */
@Composable
fun LibraryAtmosphericBackdrop(
    artworkUrls: List<String?>,
    fallbackAccent: Color = LibraryCrimsonAccent,
    modifier: Modifier = Modifier,
) {
    val leadUrl = artworkUrls.firstOrNull { !it.isNullOrBlank() }
    val secondaryUrl = artworkUrls.drop(1).firstOrNull { !it.isNullOrBlank() }
    val palette = rememberArtworkPalette(imageUrl = leadUrl)
    val secondaryPalette = rememberArtworkPalette(imageUrl = secondaryUrl)

    val defaultVioletAura = Color(0xFF5A1678)
    val targetAccent = if (leadUrl != null && palette.accent.colorChroma() >= 0.14f) {
        palette.accent
    } else if (leadUrl != null) {
        lerp(palette.accent, fallbackAccent, 0.58f)
    } else {
        fallbackAccent
    }
    val targetViolet = if (secondaryUrl != null && secondaryPalette.accent.colorChroma() >= 0.22f) {
        lerp(secondaryPalette.accent, defaultVioletAura, 0.48f)
    } else {
        defaultVioletAura
    }
    val targetWash = if (leadUrl != null && palette.wash.colorChroma() >= 0.12f) {
        palette.wash
    } else {
        lerp(fallbackAccent, defaultVioletAura, 0.55f)
    }

    val animatedAccent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(600),
        label = "library_accent_glow",
    )
    val animatedViolet by animateColorAsState(
        targetValue = targetViolet,
        animationSpec = tween(600),
        label = "library_violet_glow",
    )
    val animatedWash by animateColorAsState(
        targetValue = targetWash,
        animationSpec = tween(600),
        label = "library_wash_glow",
    )

    Box(
        modifier = modifier
            .background(Color(0xFF0A040C))
            .clip(RoundedCornerShape(0.dp)),
    ) {
        if (leadUrl != null) {
            AsyncImage(
                model = leadUrl.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.38f
                        scaleY = 1.38f
                    }
                    .alpha(0.44f)
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(90.dp, BlurredEdgeTreatment.Rectangle)
                        } else {
                            Modifier.alpha(0.50f)
                        },
                    ),
            )
        }
        val secondaryArt = secondaryUrl ?: leadUrl
        if (secondaryArt != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AsyncImage(
                model = secondaryArt.artworkAt(CARD_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.56f
                        scaleY = 1.56f
                        translationX = size.width * 0.18f
                        translationY = -size.height * 0.12f
                    }
                    .alpha(0.28f)
                    .blur(104.dp, BlurredEdgeTreatment.Rectangle),
            )
        }

        // Dynamic multi-zone liquid bokeh washes matching deep crimson-orange left & rich violet-plum right
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedAccent.copy(alpha = 0.60f),
                                animatedAccent.copy(alpha = 0.24f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.10f, size.height * 0.12f),
                            radius = size.maxDimension * 0.50f,
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedViolet.copy(alpha = 0.56f),
                                animatedViolet.copy(alpha = 0.24f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.80f, size.height * 0.20f),
                            radius = size.maxDimension * 0.66f,
                        ),
                    )
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                lerp(animatedAccent, Color(0xFF8A1858), 0.52f).copy(alpha = 0.40f),
                                animatedWash.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.44f, size.height * 0.34f),
                            radius = size.maxDimension * 0.56f,
                        ),
                    )
                },
        )

        // Contrast-protecting velvet silk scrim keeping rich midnight framing + luminous mid bokeh
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.34f),
                        0.20f to Color(0xFF0A040C).copy(alpha = 0.18f),
                        0.50f to Color(0xFF070409).copy(alpha = 0.48f),
                        0.78f to Color(0xFF060508).copy(alpha = 0.88f),
                        1.00f to LibraryGlassBaseBg,
                    ),
                ),
        )
    }
}

@Composable
fun LibraryFrostedScreenScaffold(
    controller: LibraryHostController,
    artworkUrls: List<String?>,
    scrolledTitle: String,
    isScrolled: Boolean,
    hasMiniPlayer: Boolean,
    fallbackAccent: Color = LibraryCrimsonAccent,
    amoledDark: Boolean = false,
    onBack: () -> Unit,
    topBarActions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val glassActive = isGlassSupported()
    val pageFloor = if (amoledDark) Color.Black else LibraryGlassBaseBg
    val contentPadding = PaddingValues(
        top = topBarContentPadding(),
        bottom = if (hasMiniPlayer) 210.dp else 140.dp,
    )

    ITubeArtistTheme(darkTheme = true) {
        CompositionLocalProvider(
            LocalAppBackdrop provides controller.appBackdrop,
            LocalLiquidGlassEnabled provides glassActive,
            LocalReduceDynamicBlur provides false,
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .snapshotBackdropSource(controller.pageBackdrop, floor = pageFloor)
                        .hazeSource(state = controller.hazeState),
                ) {
                    // Only the background layer is recorded into appBackdrop so no liquidGlass
                    // child inside content() can ever form a self-referential RenderNode cycle.
                    if (amoledDark) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .layerBackdrop(controller.appBackdrop),
                        )
                    } else {
                        LibraryAtmosphericBackdrop(
                            artworkUrls = artworkUrls,
                            fallbackAccent = fallbackAccent,
                            modifier = Modifier
                                .fillMaxSize()
                                .layerBackdrop(controller.appBackdrop),
                        )
                    }
                    content(contentPadding)
                }

                TopFadeBlur(
                    hazeState = controller.hazeState,
                    pageColor = pageFloor,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                FrostedTopBar(
                    transparentBackdrop = true,
                    artworkPageChrome = true,
                    backButtonHazeState = controller.hazeState,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopCenter),
                    centerContent = {
                        AnimatedVisibility(
                            visible = isScrolled,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(180)),
                        ) {
                            FrostedTopBarTitlePill(hazeState = controller.hazeState) {
                                Text(
                                    text = scrolledTitle,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = topBarActions,
                )

                BottomFadeScrim(
                    withMiniPlayer = hasMiniPlayer,
                    pageColor = pageFloor,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
fun LibraryTopBarActionButtons(
    controller: LibraryHostController,
    showSort: Boolean,
    onMoreClick: () -> Unit,
) {
    IconButton(onClick = controller::toggleSearch) {
        Icon(
            imageVector = ITubeIcons.Search,
            contentDescription = "Search",
            tint = if (controller.searchOpen) LibraryCrimsonAccent else Color.White,
            modifier = Modifier.size(21.dp),
        )
    }
    if (showSort) {
        IconButton(
            onClick = controller::openSortMenu,
            modifier = Modifier.menuAnchor(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = "Sort",
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }
    }
    IconButton(
        onClick = onMoreClick,
        modifier = Modifier.menuAnchor(),
    ) {
        Icon(
            imageVector = Icons.Rounded.MoreHoriz,
            contentDescription = stringResource(R.string.more),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
fun LibraryGlassPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    accentTint: Color? = null,
    height: Dp = 50.dp,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    val glassActive = LocalLiquidGlassEnabled.current && isGlassSupported()
    val contentColor = if (filled) Color.Black else Color.White

    Row(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(Color.White)
                } else if (glassActive) {
                    Modifier
                        .liquidGlass(
                            shape = CircleShape,
                            surfaceTint = accentTint?.copy(alpha = 0.24f),
                        )
                        .background(
                            color = accentTint?.copy(alpha = 0.14f) ?: Color.White.copy(alpha = 0.08f),
                            shape = CircleShape,
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                } else {
                    Modifier
                        .lightweightLiquidGlass(
                            shape = CircleShape,
                            fallbackColor = accentTint?.copy(alpha = 0.24f) ?: Color(0x33FFFFFF),
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                },
            )
            .clickable(enabled = enabled) {
                haptics.play(Haptic.Resume)
                onClick()
            }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (!filled && accentTint != null) accentTint else contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LibraryFilterBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    placeholder: String,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(220)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
    ) {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        LaunchedEffect(autoFocus, visible) {
            if (visible && autoFocus) {
                focusRequester.requestFocus()
                onFocused()
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(46.dp)
                .clip(CircleShape)
                .lightweightLiquidGlass(
                    shape = CircleShape,
                    fallbackColor = Color(0x441E1E24),
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ITubeIcons.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    cursorBrush = SolidColor(LibraryCrimsonAccent),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White,
                        fontSize = 15.sp,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            IconButton(
                onClick = {
                    if (query.isNotEmpty()) {
                        onQueryChange("")
                    } else {
                        focusManager.clearFocus()
                        onClose()
                    }
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
fun LibraryGlassCardContainer(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    accentTint: Color? = null,
    useBackdropGlass: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val glassActive = LocalLiquidGlassEnabled.current && isGlassSupported() && useBackdropGlass
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (glassActive) {
                    Modifier
                        .liquidGlass(shape, surfaceTint = accentTint?.copy(alpha = 0.16f))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0.02f),
                                ),
                            ),
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), shape)
                } else {
                    Modifier
                        .lightweightLiquidGlass(
                            shape = shape,
                            fallbackColor = Color(0x5C15131B),
                        )
                        .liquidGlassRim(shape)
                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), shape)
                },
            ),
        content = content,
    )
}

@Composable
fun LibraryGlassEmptyCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accent: Color = LibraryCrimsonAccent,
    actionLabel: String? = null,
    actionModifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    LibraryGlassCardContainer(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        cornerRadius = 26.dp,
        accentTint = accent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                LibraryGlassPillButton(
                    icon = ITubeIcons.Play,
                    label = actionLabel,
                    onClick = onAction,
                    filled = true,
                    height = 46.dp,
                    modifier = actionModifier,
                )
            }
        }
    }
}

@Composable
fun LibraryScreenOverlayPopups(
    controller: LibraryHostController,
) {
    if (!controller.isOpen && !controller.hasAnyPopupOpen()) return
    val glassActive = isGlassSupported()
    val favoriteIds = MusicPlayerState.favoriteIds

    val pageMenuUp =
        (controller.songActions != null && controller.songMenuOrigin == null) ||
            (controller.collectionActions != null && controller.collectionMenuOrigin == null) ||
            (controller.userPlaylistActions != null && controller.userPlaylistMenuOrigin == null) ||
            controller.topMenuRequest != null ||
            controller.sortMenuRequest != null

    LaunchedEffect(pageMenuUp) {
        if (pageMenuUp && glassActive) {
            controller.pageBackdrop.capture()
        } else {
            delay(260L)
            controller.pageBackdrop.release()
        }
    }

    ITubeArtistTheme(darkTheme = true) {
        CompositionLocalProvider(
            LocalAppBackdrop provides controller.appBackdrop,
            LocalLiquidGlassEnabled provides glassActive,
            LocalReduceDynamicBlur provides false,
        ) {
            val songMenuBody: @Composable (Song) -> Unit = { song ->
                val isLiked = favoriteIds.contains(song.videoId)
                val isDownloading = controller.downloadingIds[song.videoId] == true
                val songPayload = remember(song) { PendingPlaylistPayload.fromSong(song) }
                IosInMenuPlaylistHost(
                    menuKey = song.videoId,
                    payload = songPayload,
                    onCloseMenu = { controller.clearMenus() },
                ) { onOpenAddToPlaylist, _, _ ->
                    ArtistSongActionsMenu(
                        song = song,
                        likeStatus = if (isLiked) LikeStatus.LIKE else LikeStatus.INDIFFERENT,
                        downloading = isDownloading,
                        downloaded = false,
                        onToggleLike = {
                            MusicPlayerState.toggleFavorite(song.toMusicTrack())
                            controller.clearMenus()
                        },
                        onStartRadio = {
                            controller.clearMenus()
                            controller.startRadioForSong(song)
                        },
                        onPlayNext = {
                            controller.clearMenus()
                            MusicPlayerState.playNext(song.toMusicTrack())
                            controller.showToast("Playing \"${song.title}\" next")
                        },
                        onAddToQueue = {
                            controller.clearMenus()
                            MusicPlayerState.addToQueue(listOf(song.toMusicTrack()))
                            controller.showToast("Added \"${song.title}\" to queue")
                        },
                        onAddToPlaylist = onOpenAddToPlaylist,
                        onRemoveFromPlaylist = if (
                            controller.currentDestination == LibraryDestination.USER_PLAYLIST &&
                            controller.activeUserPlaylistId != null
                        ) {
                            {
                                val pid = controller.activeUserPlaylistId
                                controller.clearMenus()
                                if (pid != null) {
                                    LibraryStore.removeSongFromUserPlaylist(controller.context, pid, song.videoId)
                                    controller.showToast("Removed \"${song.title}\" from playlist")
                                }
                            }
                        } else {
                            null
                        },
                        onDownload = {
                            controller.clearMenus()
                            controller.downloadSong(song)
                        },
                        onOpenAlbum = { albumId, albumTitle ->
                            controller.openDetail(
                                browseId = albumId,
                                title = albumTitle ?: song.title,
                                subtitle = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                type = BrowseType.ALBUM,
                            )
                        },
                        onOpenArtist = { artistId, artistName ->
                            controller.openDetail(
                                browseId = artistId,
                                title = artistName ?: song.artist,
                                subtitle = "Artist",
                                thumbnailUrl = null,
                                type = BrowseType.ARTIST,
                            )
                        },
                        onShare = {
                            controller.clearMenus()
                            controller.shareUrl(song.title, "https://music.youtube.com/watch?v=${song.videoId}")
                        },
                    )
                }
            }

            val collectionMenuBody: @Composable (SavedCollectionEntry) -> Unit = { entry ->
                LibraryCollectionActionsMenu(
                    entry = entry,
                    onPlay = {
                        controller.clearMenus()
                        controller.withCollectionSongs(entry) { list ->
                            controller.playSongs(list, 0)
                        }
                    },
                    onShuffle = {
                        controller.clearMenus()
                        controller.withCollectionSongs(entry) { list ->
                            controller.playSongs(list, 0, shuffle = true)
                        }
                    },
                    onStartRadio = if (entry.type == BrowseType.ARTIST) {
                        {
                            controller.clearMenus()
                            controller.startRadioForArtist(entry)
                        }
                    } else null,
                    onPlayNext = {
                        controller.clearMenus()
                        controller.withCollectionSongs(entry) { list ->
                            list.asReversed().forEach { MusicPlayerState.playNext(it.toMusicTrack()) }
                            controller.showToast("Playing ${entry.title} next")
                        }
                    },
                    onAddToQueue = {
                        controller.clearMenus()
                        controller.withCollectionSongs(entry) { list ->
                            MusicPlayerState.addToQueue(list.map { it.toMusicTrack() })
                            controller.showToast("Added ${entry.title} to queue")
                        }
                    },
                    onDownloadAll = {
                        controller.clearMenus()
                        controller.withCollectionSongs(entry) { list ->
                            controller.downloadSongs(list)
                        }
                    },
                    onOpen = {
                        controller.openDetail(
                            browseId = entry.browseId,
                            title = entry.title,
                            subtitle = entry.subtitle,
                            thumbnailUrl = entry.thumbnailUrl,
                            type = entry.type,
                        )
                    },
                    onRemove = {
                        controller.clearMenus()
                        controller.removeCollection(entry)
                    },
                    onShare = {
                        controller.clearMenus()
                        val url = when (entry.type) {
                            BrowseType.ARTIST -> "https://music.youtube.com/channel/${entry.browseId}"
                            BrowseType.PLAYLIST -> "https://music.youtube.com/playlist?list=${entry.browseId.removePrefix("VL")}"
                            else -> "https://music.youtube.com/browse/${entry.browseId}"
                        }
                        controller.shareUrl(entry.title, url)
                    },
                )
            }

            HeldContextMenu(
                item = controller.songActions,
                held = controller.songMenuOrigin,
                hazeState = controller.hazeState,
                onDismiss = { controller.songActions = null },
                onPreviewClick = { song ->
                    song.albumId?.let { id ->
                        controller.openDetail(
                            browseId = id,
                            title = song.albumName ?: song.title,
                            subtitle = song.artist,
                            thumbnailUrl = song.thumbnailUrl,
                            type = BrowseType.ALBUM,
                        )
                    }
                },
            ) { song ->
                songMenuBody(song)
            }

            IosMenuPopover(
                item = controller.songActions?.takeIf { controller.songMenuOrigin == null },
                anchor = controller.songMenuAnchor,
                onDismiss = { controller.songActions = null },
                backdrop = controller.pageBackdrop,
                hazeState = controller.hazeState,
            ) { song ->
                songMenuBody(song)
            }

            HeldContextMenu(
                item = controller.collectionActions,
                held = controller.collectionMenuOrigin,
                hazeState = controller.hazeState,
                onDismiss = { controller.collectionActions = null },
                onPreviewClick = { entry ->
                    controller.openDetail(
                        browseId = entry.browseId,
                        title = entry.title,
                        subtitle = entry.subtitle,
                        thumbnailUrl = entry.thumbnailUrl,
                        type = entry.type,
                    )
                },
            ) { entry ->
                collectionMenuBody(entry)
            }

            IosMenuPopover(
                item = controller.collectionActions?.takeIf { controller.collectionMenuOrigin == null },
                anchor = controller.collectionMenuAnchor,
                onDismiss = { controller.collectionActions = null },
                backdrop = controller.pageBackdrop,
                hazeState = controller.hazeState,
            ) { entry ->
                collectionMenuBody(entry)
            }

            val userPlaylistMenuBody: @Composable (UserPlaylist) -> Unit = { pl ->
                val plSongs = remember(pl.songs) { pl.songs.map { it.toSong() } }
                IosInMenuPlaylistHost(
                    menuKey = pl.id,
                    renameTarget = pl,
                    onCloseMenu = { controller.clearMenus() },
                ) { _, _, onOpenRename ->
                    LibraryUserPlaylistActionsMenu(
                        playlist = pl,
                        hasSongs = plSongs.isNotEmpty(),
                        onPlay = {
                            controller.clearMenus()
                            controller.playSongs(plSongs, 0)
                        },
                        onShuffle = {
                            controller.clearMenus()
                            controller.playSongs(plSongs, 0, shuffle = true)
                        },
                        onStartRadio = {
                            controller.clearMenus()
                            controller.startRadioFromSongs(plSongs, pl.name)
                        },
                        onAddToQueue = {
                            controller.clearMenus()
                            MusicPlayerState.addToQueue(pl.songs.map { it.toMusicTrack() })
                            controller.showToast("Added ${pl.name} to queue")
                        },
                        onOpen = {
                            controller.clearMenus()
                            controller.openUserPlaylist(pl.id)
                        },
                        onRename = onOpenRename,
                        onDelete = {
                            controller.clearMenus()
                            LibraryStore.deleteUserPlaylist(controller.context, pl.id)
                            controller.showToast("Deleted \"${pl.name}\"")
                        },
                    )
                }
            }

            HeldContextMenu(
                item = controller.userPlaylistActions,
                held = controller.userPlaylistMenuOrigin,
                hazeState = controller.hazeState,
                onDismiss = { controller.userPlaylistActions = null },
                onPreviewClick = { pl ->
                    controller.clearMenus()
                    controller.openUserPlaylist(pl.id)
                },
            ) { pl ->
                userPlaylistMenuBody(pl)
            }

            IosMenuPopover(
                item = controller.userPlaylistActions?.takeIf { controller.userPlaylistMenuOrigin == null },
                anchor = controller.userPlaylistMenuAnchor,
                onDismiss = { controller.userPlaylistActions = null },
                backdrop = controller.pageBackdrop,
                hazeState = controller.hazeState,
            ) { pl ->
                userPlaylistMenuBody(pl)
            }

            IosMenuPopover(
                item = controller.topMenuRequest,
                anchor = controller.topMenuAnchor,
                onDismiss = { controller.topMenuRequest = null },
                backdrop = controller.pageBackdrop,
                hazeState = controller.hazeState,
            ) { req ->
                IosInMenuPlaylistHost(
                    menuKey = req,
                    initialStep = req.initialStep,
                    renameTarget = req.userPlaylist,
                    onCloseMenu = { controller.clearMenus() },
                ) { _, onOpenCreateEmpty, onOpenRename ->
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        if (req.destination == LibraryDestination.PLAYLISTS) {
                            IosMenuRow(
                                label = "Create Playlist",
                                description = "Create empty or add songs",
                                icon = SfIcons.AddToPlaylist,
                                iconTint = LibraryCrimsonAccent,
                                onClick = onOpenCreateEmpty,
                            )
                            IosMenuSeparator()
                        }
                        if (req.songs.isNotEmpty()) {
                            IosMenuQuickActions {
                                IosMenuQuickAction(
                                    icon = SfIcons.Play,
                                    label = stringResource(R.string.play),
                                    onClick = {
                                        controller.clearMenus()
                                        controller.playSongs(req.songs, 0)
                                    },
                                )
                                IosMenuQuickAction(
                                    icon = SfIcons.Shuffle,
                                    label = stringResource(R.string.shuffle),
                                    onClick = {
                                        controller.clearMenus()
                                        controller.playSongs(req.songs, 0, shuffle = true)
                                    },
                                )
                                IosMenuQuickAction(
                                    icon = SfIcons.Radio,
                                    label = "Radio",
                                    tint = LibraryCrimsonAccent,
                                    onClick = {
                                        controller.clearMenus()
                                        controller.startRadioFromSongs(req.songs, req.stationLabel)
                                    },
                                )
                            }
                            IosMenuSeparator()
                            IosMenuRow(
                                label = stringResource(R.string.start_radio),
                                description = "${req.stationLabel} station",
                                icon = SfIcons.Radio,
                                iconTint = LibraryCrimsonAccent,
                                onClick = {
                                    controller.clearMenus()
                                    controller.startRadioFromSongs(req.songs, req.stationLabel)
                                },
                            )
                            IosMenuRow(
                                label = stringResource(R.string.download_all),
                                icon = SfIcons.Download,
                                onClick = {
                                    controller.clearMenus()
                                    controller.downloadSongs(req.songs)
                                },
                            )
                            IosMenuSeparator()
                        }
                        IosMenuRow(
                            label = "Search",
                            icon = ITubeIcons.Search,
                            onClick = {
                                controller.clearMenus()
                                if (!controller.searchOpen) controller.toggleSearch()
                            },
                        )
                        if (req.showSort) {
                            IosMenuRow(
                                label = "Sort: ${controller.songSort.label}",
                                icon = Icons.AutoMirrored.Rounded.Sort,
                                onClick = {
                                    val next = when (controller.songSort) {
                                        LibrarySongSort.RECENT -> LibrarySongSort.TITLE_ASC
                                        LibrarySongSort.TITLE_ASC -> LibrarySongSort.ARTIST_ASC
                                        LibrarySongSort.ARTIST_ASC -> LibrarySongSort.RECENT
                                    }
                                    controller.songSort = next
                                    controller.clearMenus()
                                },
                            )
                        }
                        req.userPlaylist?.let { pl ->
                            IosMenuSeparator()
                            IosMenuRow(
                                label = "Rename Playlist",
                                icon = Icons.Rounded.Edit,
                                onClick = onOpenRename,
                            )
                            IosMenuRow(
                                label = "Delete Playlist",
                                icon = SfIcons.Trash,
                                iconTint = LibraryCrimsonAccent,
                                onClick = {
                                    controller.clearMenus()
                                    LibraryStore.deleteUserPlaylist(controller.context, pl.id)
                                    controller.pop()
                                    controller.showToast("Deleted \"${pl.name}\"")
                                },
                            )
                        }
                    }
                }
            }

            IosMenuPopover(
                item = controller.sortMenuRequest,
                anchor = controller.sortMenuAnchor,
                onDismiss = { controller.sortMenuRequest = null },
                backdrop = controller.pageBackdrop,
                hazeState = controller.hazeState,
            ) { req ->
                Column(Modifier.fillMaxWidth()) {
                    LibrarySongSort.entries.forEach { option ->
                        IosMenuRow(
                            label = option.label,
                            icon = if (option == req.currentSort) SfIcons.Check else null,
                            onClick = {
                                controller.songSort = option
                                controller.clearMenus()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryCollectionActionsMenu(
    entry: SavedCollectionEntry,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: (() -> Unit)?,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit,
) {
    CompositionLocalProvider(LocalActionRowStyle provides ActionRowStyle.Menu) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            IosMenuQuickActions {
                IosMenuQuickAction(
                    icon = SfIcons.Play,
                    label = stringResource(R.string.play),
                    onClick = onPlay,
                )
                IosMenuQuickAction(
                    icon = SfIcons.Shuffle,
                    label = stringResource(R.string.shuffle),
                    onClick = onShuffle,
                )
                IosMenuQuickAction(
                    icon = SfIcons.Share,
                    label = stringResource(R.string.share),
                    onClick = onShare,
                )
            }
            IosMenuSeparator()
            if (onStartRadio != null) {
                ActionRow(
                    icon = SfIcons.Radio,
                    label = stringResource(R.string.start_radio),
                    description = entry.title,
                    tint = LibraryCrimsonAccent,
                    onClick = onStartRadio,
                )
            }
            ActionRow(
                icon = SfIcons.PlayNext,
                label = stringResource(R.string.play_next),
                onClick = onPlayNext,
            )
            ActionRow(
                icon = SfIcons.PlayLast,
                label = stringResource(R.string.add_to_queue),
                onClick = onAddToQueue,
            )
            ActionRow(
                icon = SfIcons.Download,
                label = stringResource(R.string.download_all),
                onClick = onDownloadAll,
            )
            IosMenuSeparator()
            ActionRow(
                icon = SfIcons.Open,
                label = when (entry.type) {
                    BrowseType.ARTIST -> stringResource(R.string.open_artist)
                    BrowseType.PLAYLIST -> stringResource(R.string.open_playlist)
                    else -> stringResource(R.string.open_album)
                },
                onClick = onOpen,
            )
            ActionRow(
                icon = SfIcons.Trash,
                label = if (entry.type == BrowseType.ARTIST) {
                    "Remove from Favorite Artists"
                } else {
                    stringResource(R.string.remove_from_library)
                },
                tint = LibraryCrimsonAccent,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun LibraryUserPlaylistActionsMenu(
    playlist: UserPlaylist,
    hasSongs: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onAddToQueue: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    CompositionLocalProvider(LocalActionRowStyle provides ActionRowStyle.Menu) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (hasSongs) {
                IosMenuQuickActions {
                    IosMenuQuickAction(
                        icon = SfIcons.Play,
                        label = stringResource(R.string.play),
                        onClick = onPlay,
                    )
                    IosMenuQuickAction(
                        icon = SfIcons.Shuffle,
                        label = stringResource(R.string.shuffle),
                        onClick = onShuffle,
                    )
                    IosMenuQuickAction(
                        icon = SfIcons.Radio,
                        label = "Radio",
                        tint = LibraryCrimsonAccent,
                        onClick = onStartRadio,
                    )
                }
                IosMenuSeparator()
                ActionRow(
                    icon = SfIcons.Radio,
                    label = stringResource(R.string.start_radio),
                    description = playlist.name,
                    tint = LibraryCrimsonAccent,
                    onClick = onStartRadio,
                )
                ActionRow(
                    icon = SfIcons.PlayLast,
                    label = stringResource(R.string.add_to_queue),
                    onClick = onAddToQueue,
                )
                IosMenuSeparator()
            }
            ActionRow(
                icon = SfIcons.Open,
                label = stringResource(R.string.open_playlist),
                onClick = onOpen,
            )
            ActionRow(
                icon = Icons.Rounded.Edit,
                label = "Rename Playlist",
                onClick = onRename,
            )
            ActionRow(
                icon = SfIcons.Trash,
                label = "Delete Playlist",
                tint = LibraryCrimsonAccent,
                onClick = onDelete,
            )
        }
    }
}
