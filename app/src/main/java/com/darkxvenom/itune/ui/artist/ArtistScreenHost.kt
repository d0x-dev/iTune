package com.darkxvenom.itune.ui.artist

import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.AudioDownloader
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.artist.AppleArtistArtRepository
import com.darkxvenom.itune.data.artist.ArtistRepository
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.artist.keyColors
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import com.darkxvenom.itune.ui.library.IosInMenuPlaylistHost
import com.darkxvenom.itune.ui.library.PendingPlaylistPayload
import com.darkxvenom.itune.data.model.BrowseTarget
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.DetailPage
import com.darkxvenom.itune.data.model.HomeShelf
import com.darkxvenom.itune.data.model.LibraryState
import com.darkxvenom.itune.data.model.LikeStatus
import com.darkxvenom.itune.data.model.PendingArtistNav
import com.darkxvenom.itune.data.model.PlaybackSourceType
import com.darkxvenom.itune.data.model.ShelfItem
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.SongSort
import com.darkxvenom.itune.data.model.SubscriptionState
import com.darkxvenom.itune.data.model.UiState
import com.darkxvenom.itune.data.model.toArtistSong
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.ui.artist.components.ArtistBrowseActionsMenu
import com.darkxvenom.itune.ui.artist.components.ArtistSongActionsMenu
import com.darkxvenom.itune.ui.artist.components.ArtistTopBarActionsMenu
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.HeldContextMenu
import com.darkxvenom.itune.ui.artist.components.HeldItem
import com.darkxvenom.itune.ui.artist.components.IosMenuPopover
import com.darkxvenom.itune.ui.artist.components.LocalAppBackdrop
import com.darkxvenom.itune.ui.artist.components.LocalLiquidGlassEnabled
import com.darkxvenom.itune.ui.artist.components.LocalReduceDynamicBlur
import com.darkxvenom.itune.ui.artist.components.LongPressOrigin
import com.darkxvenom.itune.ui.artist.components.MenuAnchor
import com.darkxvenom.itune.ui.artist.components.ReleaseSortMenuContent
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.LayerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.SnapshotBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.layerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberLayerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.rememberSnapshotBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.snapshotBackdropSource
import com.darkxvenom.itune.ui.artist.components.isGlassSupported
import com.darkxvenom.itune.ui.artist.components.menuAnchor
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.components.topBarHeight
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkTopBandLuminance
import com.darkxvenom.itune.ui.artist.theme.topBandScrimAlpha
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TOP_FADE_START = 0.55f
private const val TOP_FADE_RAMP = 0.3f

@Immutable
internal data class SortMenuRequest(
    val browseId: String,
    val currentSort: SongSort,
)

@Immutable
internal data class TopArtistMenuRequest(
    val page: DetailPage,
)

@Stable
class ArtistHostController internal constructor(
    internal val context: Context,
    private val scope: CoroutineScope,
    internal val pageBackdrop: SnapshotBackdrop,
    internal val appBackdrop: LayerBackdrop,
    internal val hazeState: HazeState,
) {
    internal val detailStack = mutableStateListOf<DetailPage>()
    internal val listStates = mutableStateMapOf<String, LazyListState>()
    internal val activeShelfByPage = mutableStateMapOf<String, HomeShelf?>()
    internal val songSortByPage = mutableStateMapOf<String, SongSort>()
    internal var searchingPageId by mutableStateOf<String?>(null)

    internal var songActions by mutableStateOf<Song?>(null)
    internal var songMenuOrigin by mutableStateOf<HeldItem?>(null)
    internal var songMenuAnchor by mutableStateOf<Rect?>(null)

    internal var browseActions by mutableStateOf<BrowseTarget?>(null)
    internal var browseMenuOrigin by mutableStateOf<HeldItem?>(null)
    internal var browseMenuAnchor by mutableStateOf<Rect?>(null)

    internal var artistMenu by mutableStateOf<TopArtistMenuRequest?>(null)
    internal var artistMenuAnchor by mutableStateOf<Rect?>(null)

    internal var sortMenu by mutableStateOf<SortMenuRequest?>(null)
    internal var sortMenuAnchor by mutableStateOf<Rect?>(null)

    internal val downloadingIds = mutableStateMapOf<String, Boolean>()

    val isOpen: Boolean get() = detailStack.isNotEmpty()
    val currentPage: DetailPage? get() = detailStack.lastOrNull()

    fun clearAll() {
        clearMenus()
        searchingPageId = null
        activeShelfByPage.clear()
        detailStack.clear()
        listStates.clear()
    }

    fun clearMenus() {
        songActions = null
        songMenuOrigin = null
        songMenuAnchor = null
        browseActions = null
        browseMenuOrigin = null
        browseMenuAnchor = null
        artistMenu = null
        artistMenuAnchor = null
        sortMenu = null
        sortMenuAnchor = null
    }

    fun hasAnyPopupOpen(): Boolean =
        songActions != null || browseActions != null || artistMenu != null || sortMenu != null

    fun popTop(): Boolean {
        if (hasAnyPopupOpen()) {
            clearMenus()
            return true
        }
        val top = currentPage ?: return false
        if (searchingPageId == top.browseId) {
            searchingPageId = null
            return true
        }
        val shelf = activeShelfByPage[top.browseId]
        if (shelf != null) {
            activeShelfByPage[top.browseId] = null
            return true
        }
        activeShelfByPage.remove(top.browseId)
        songSortByPage.remove(top.browseId)
        listStates.remove(top.browseId)
        detailStack.removeAt(detailStack.lastIndex)
        return true
    }

    fun openFromNav(request: PendingArtistNav) {
        ArtistStateStore.ensureInit(context)
        val rawId = request.browseId?.takeIf { it.isNotBlank() }
        if (rawId != null) {
            openDetail(
                browseId = rawId,
                title = request.name,
                subtitle = request.subtitle.ifBlank {
                    if (request.initialType == BrowseType.ARTIST) "Artist" else ""
                },
                thumbnailUrl = request.thumbnailUrl,
                type = request.initialType,
            )
            return
        }
        val placeholderId = "resolve:${request.name.trim()}"
        if (currentPage?.browseId == placeholderId && currentPage?.songs is UiState.Loading) return
        detailStack += DetailPage(
            browseId = placeholderId,
            title = request.name,
            subtitle = "Artist",
            thumbnailUrl = request.thumbnailUrl,
            songs = UiState.Loading,
            type = BrowseType.ARTIST,
        )
        scope.launch {
            val resolvedId = ArtistRepository.findArtistPageId(request.name).getOrNull()
            if (resolvedId.isNullOrBlank()) {
                replacePage(placeholderId) { page ->
                    page.copy(songs = UiState.Error("Couldn't find artist page for ${request.name}"))
                }
                return@launch
            }
            val idx = detailStack.indexOfLast { it.browseId == placeholderId }
            if (idx >= 0) {
                detailStack.removeAt(idx)
            }
            openDetail(
                browseId = resolvedId,
                title = request.name,
                subtitle = "Artist",
                thumbnailUrl = request.thumbnailUrl,
                type = BrowseType.ARTIST,
            )
        }
    }

    fun openDetail(
        browseId: String,
        title: String,
        subtitle: String = "",
        thumbnailUrl: String? = null,
        type: BrowseType = BrowseType.OTHER,
    ) {
        ArtistStateStore.ensureInit(context)
        if (detailStack.lastOrNull()?.let { it.browseId == browseId && it.songs is UiState.Loading } == true) {
            return
        }
        val resolvedType = browseTypeOf(browseId, type)
        detailStack += DetailPage(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            songs = UiState.Loading,
            type = resolvedType,
        )
        scope.launch {
            if (resolvedType == BrowseType.ARTIST) {
                ArtistRepository.artistPage(browseId).fold(
                    onSuccess = { page ->
                        val stamped = page.songs.map { song ->
                            song.copy(
                                artistId = song.artistId ?: browseId,
                                playbackSource = page.name ?: title,
                                playbackSourceType = PlaybackSourceType.BROWSE,
                                playbackSourceId = browseId,
                            )
                        }
                        val resolvedTitle = page.name ?: title
                        val resolvedThumb = AppleArtistArtRepository.cached(browseId)?.heroUrl
                            ?: page.thumbnailUrl
                            ?: thumbnailUrl
                        val resolvedSub = page.monthlyListenerCount
                            ?: page.subscriberCountText
                            ?: subtitle.ifBlank { "Artist" }
                        LibraryStore.updateArtistMetadataIfSaved(
                            context = context,
                            browseId = browseId,
                            title = resolvedTitle,
                            subtitle = resolvedSub,
                            thumbnailUrl = resolvedThumb,
                        )
                        replacePage(browseId) { existing ->
                            existing.copy(
                                title = page.name ?: existing.title,
                                thumbnailUrl = page.thumbnailUrl ?: existing.thumbnailUrl,
                                songs = UiState.Success(stamped),
                                sections = page.sections,
                                description = page.description ?: existing.description,
                                subscriberCountText = page.subscriberCountText,
                                monthlyListenerCount = page.monthlyListenerCount,
                                subscription = (page.subscription ?: SubscriptionState(
                                    channelId = browseId,
                                    subscribed = false,
                                )).let { sub ->
                                    sub.copy(subscribed = sub.subscribed || ArtistStateStore.isSubscribed(browseId))
                                },
                                radio = page.radio,
                                shuffle = page.shuffle,
                            )
                        }
                    },
                    onFailure = { err ->
                        replacePage(browseId) { existing ->
                            existing.copy(
                                songs = UiState.Error(err.message ?: "Failed to load artist page"),
                            )
                        }
                    },
                )
            } else {
                ArtistRepository.browseSongs(browseId).fold(
                    onSuccess = { songPage ->
                        val headerTitle = songPage.header?.title?.takeIf { it.isNotBlank() } ?: title
                        val stamped = songPage.songs.map { song ->
                            song.copy(
                                albumId = if (resolvedType == BrowseType.ALBUM) song.albumId ?: browseId else song.albumId,
                                albumName = if (resolvedType == BrowseType.ALBUM) song.albumName ?: headerTitle else song.albumName,
                                playbackSource = headerTitle,
                                playbackSourceType = PlaybackSourceType.BROWSE,
                                playbackSourceId = browseId,
                            )
                        }
                        val headerSub = songPage.header?.subtitle?.takeIf { it.isNotBlank() } ?: subtitle
                        val headerThumb = songPage.header?.thumbnailUrl ?: thumbnailUrl
                        LibraryStore.updateReleaseMetadataIfSaved(
                            context = context,
                            browseId = browseId,
                            title = headerTitle,
                            subtitle = headerSub,
                            thumbnailUrl = headerThumb,
                            type = resolvedType,
                        )
                        replacePage(browseId) { existing ->
                            existing.copy(
                                title = headerTitle,
                                subtitle = songPage.header?.subtitle?.takeIf { it.isNotBlank() } ?: existing.subtitle,
                                thumbnailUrl = songPage.header?.thumbnailUrl ?: existing.thumbnailUrl,
                                songs = UiState.Success(stamped),
                                continuation = songPage.continuation,
                                suggestedSongs = songPage.suggested,
                                description = songPage.description ?: existing.description,
                                library = (songPage.library ?: LibraryState(
                                    playlistId = browseId,
                                    saved = false,
                                )).let { lib ->
                                    lib.copy(saved = lib.saved || ArtistStateStore.isReleaseSaved(browseId))
                                },
                            )
                        }
                        var nextToken = songPage.continuation
                        var pages = 0
                        while (nextToken != null && pages++ < 6) {
                            val nextPage = ArtistRepository.moreSongs(nextToken).getOrNull() ?: break
                            if (nextPage.songs.isEmpty()) break
                            replacePage(browseId) { existing ->
                                val prev = (existing.songs as? UiState.Success)?.data.orEmpty()
                                val merged = (prev + nextPage.songs.map { song ->
                                    song.copy(
                                        albumId = if (resolvedType == BrowseType.ALBUM) song.albumId ?: browseId else song.albumId,
                                        albumName = if (resolvedType == BrowseType.ALBUM) song.albumName ?: headerTitle else song.albumName,
                                    )
                                }).distinctBy { it.videoId }
                                existing.copy(
                                    songs = UiState.Success(merged),
                                    continuation = nextPage.continuation,
                                )
                            }
                            nextToken = nextPage.continuation
                        }
                    },
                    onFailure = { err ->
                        replacePage(browseId) { existing ->
                            existing.copy(
                                songs = UiState.Error(err.message ?: "Failed to load release"),
                            )
                        }
                    },
                )
            }
        }
    }

    fun openSongMenu(song: Song) {
        clearMenus()
        songMenuOrigin = LongPressOrigin.consume()
        songMenuAnchor = MenuAnchor.consume()
        songActions = song
    }

    fun openBrowseMenu(target: BrowseTarget) {
        clearMenus()
        browseMenuOrigin = LongPressOrigin.consume()
        browseMenuAnchor = MenuAnchor.consume()
        browseActions = target
    }

    fun openArtistTopMenu(page: DetailPage) {
        clearMenus()
        artistMenuAnchor = MenuAnchor.consume()
        artistMenu = TopArtistMenuRequest(page)
    }

    fun openSortMenu(browseId: String, currentSort: SongSort) {
        clearMenus()
        sortMenuAnchor = MenuAnchor.consume()
        sortMenu = SortMenuRequest(browseId, currentSort)
    }

    fun playSongs(songs: List<Song>, index: Int, page: DetailPage? = null, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        val albumTitle = page?.takeIf { it.type == BrowseType.ALBUM }?.title
        val normalized = songs.map { s ->
            val withAlbum = if (albumTitle != null && s.albumName.isNullOrBlank()) {
                s.copy(albumName = albumTitle)
            } else {
                s
            }
            withAlbum.toMusicTrack()
        }
        val targetIndex = if (shuffle) 0 else index.coerceIn(normalized.indices)
        val selectedTrack = normalized[targetIndex]

        // When playing a specific song from an Artist screen, seed queue using the
        // algorithmic Next/Radio backend ("Because you listened to..."), keeping albums/playlists playing normally.
        if (page?.type == BrowseType.ARTIST && !shuffle) {
            MusicPlayerState.playWithRadioQueue(selectedTrack, expandPlayer = false)
            return
        }

        val playQueue = if (shuffle) normalized.shuffled() else normalized
        MusicPlayerState.play(
            track = if (shuffle) playQueue.first() else selectedTrack,
            newQueue = playQueue,
            expandPlayer = false,
        )
    }

    fun startRadioForSong(song: Song) {
        val baseTrack = song.toMusicTrack()
        MusicPlayerState.play(baseTrack, listOf(baseTrack), expandPlayer = false)
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

    fun startArtistWatchQueue(target: com.darkxvenom.itune.data.model.WatchTarget) {
        scope.launch {
            val result = ArtistRepository.watchQueue(target).getOrNull().orEmpty()
            if (result.isNotEmpty()) {
                val tracks = result.map { it.toMusicTrack() }
                MusicPlayerState.play(tracks.first(), tracks, expandPlayer = false)
            } else {
                showToast("Couldn't start artist queue")
            }
        }
    }

    fun onSectionItemClick(page: DetailPage, item: ShelfItem) {
        val videoSong = shelfSong(item)?.takeIf { item.browseId == null }
        if (videoSong != null) {
            startRadioForSong(videoSong)
            return
        }
        item.browseId?.let { id ->
            openDetail(
                browseId = id,
                title = item.title,
                subtitle = item.subtitle,
                thumbnailUrl = item.thumbnailUrl,
                type = browseTypeOf(id, BrowseType.ALBUM),
            )
        }
    }

    fun onSectionItemLongPress(item: ShelfItem) {
        val song = shelfSong(item)
        if (song != null) {
            openSongMenu(song)
            return
        }
        openBrowseMenu(
            BrowseTarget(
                browseId = item.browseId ?: return,
                title = item.title,
                subtitle = item.subtitle,
                thumbnailUrl = item.thumbnailUrl,
                type = browseTypeOf(item.browseId, BrowseType.ALBUM),
                fromCard = true,
            ),
        )
    }

    fun withBrowseSongs(target: BrowseTarget, action: (List<Song>) -> Unit) {
        val stamp: (List<Song>) -> List<Song> = { list ->
            list.map { song ->
                song.copy(
                    albumName = if (target.type == BrowseType.ALBUM) song.albumName ?: target.title else song.albumName,
                    playbackSource = target.title,
                    playbackSourceType = PlaybackSourceType.BROWSE,
                    playbackSourceId = target.browseId,
                )
            }
        }
        if (target.songs.isNotEmpty()) {
            action(stamp(target.songs))
            return
        }
        scope.launch {
            ArtistRepository.allSongs(target.browseId).fold(
                onSuccess = { loaded -> action(stamp(loaded)) },
                onFailure = { showToast(it.message ?: "Couldn't load tracks") },
            )
        }
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
                showToast(e.message ?: "Download failed")
            } finally {
                downloadingIds.remove(song.videoId)
            }
        }
    }

    fun downloadSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        showToast("Starting download for ${songs.size} tracks...")
        scope.launch {
            for (song in songs) {
                try {
                    val options = withContext(Dispatchers.IO) {
                        MusicBackend.extractAudioQualities(song.videoId)
                    }
                    val best = options.firstOrNull()
                    if (best != null) {
                        AudioDownloader.download(context, song.toMusicTrack(), best)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Continue remaining tracks in the collection.
                }
            }
        }
    }

    fun shareUrl(title: String, url: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(sendIntent, title))
    }

    fun toggleArtistSubscription(browseId: String) {
        val page = detailStack.lastOrNull { it.browseId == browseId }
        val artThumb = AppleArtistArtRepository.cached(browseId)?.heroUrl ?: page?.thumbnailUrl
        val artistSub = page?.monthlyListenerCount
            ?: page?.subscriberCountText
            ?: page?.subtitle?.ifBlank { "Artist" }
            ?: "Artist"
        val subscribed = ArtistStateStore.toggleSubscription(
            context = context,
            browseId = browseId,
            title = page?.title ?: "",
            subtitle = artistSub,
            thumbnailUrl = artThumb,
        )
        replacePage(browseId) { existing ->
            existing.copy(subscription = SubscriptionState(browseId, subscribed))
        }
    }

    fun toggleReleaseSaved(browseId: String) {
        val page = detailStack.lastOrNull { it.browseId == browseId }
        val isExplicit = (page?.songs as? UiState.Success)?.data?.any { it.isExplicit == true } == true
        val saved = ArtistStateStore.toggleReleaseSaved(
            context = context,
            browseId = browseId,
            title = page?.title ?: "",
            subtitle = page?.subtitle ?: "",
            thumbnailUrl = page?.thumbnailUrl,
            type = page?.type ?: BrowseType.OTHER,
            isExplicit = isExplicit,
            playlistId = page?.library?.playlistId,
        )
        replacePage(browseId) { existing ->
            val updatedLib = existing.library?.copy(saved = saved) ?: LibraryState(browseId, saved)
            existing.copy(library = updatedLib)
        }
    }

    private fun replacePage(browseId: String, transform: (DetailPage) -> DetailPage) {
        for (i in detailStack.indices.reversed()) {
            val page = detailStack[i]
            if (page.browseId == browseId) {
                detailStack[i] = transform(page)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberArtistHostController(): ArtistHostController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageBackdrop = rememberSnapshotBackdrop()
    val windowBg = Color.Black
    val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBg) {
        {
            drawRect(windowBg)
            drawContent()
        }
    }
    val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)
    val hazeState = remember { HazeState() }
    return remember(context, scope, pageBackdrop, appBackdrop, hazeState) {
        ArtistHostController(
            context = context,
            scope = scope,
            pageBackdrop = pageBackdrop,
            appBackdrop = appBackdrop,
            hazeState = hazeState,
        )
    }
}

@Composable
fun ArtistScreenOverlayContent(
    controller: ArtistHostController,
    hasMiniPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    val page = controller.currentPage ?: return
    val glassActive = isGlassSupported()

    BackHandler(enabled = controller.isOpen) {
        controller.popTop()
    }

    val subscribedArtists by ArtistStateStore.subscribedArtists.collectAsStateWithLifecycle()
    val savedReleases by ArtistStateStore.savedReleases.collectAsStateWithLifecycle()
    val appleArtVersion by AppleArtistArtRepository.updates.collectAsStateWithLifecycle()

    val effectivePage = remember(page, subscribedArtists, savedReleases) {
        when (page.type) {
            BrowseType.ARTIST -> page.copy(
                subscription = SubscriptionState(
                    channelId = page.browseId,
                    subscribed = page.browseId in subscribedArtists ||
                        page.subscription?.subscribed == true,
                ),
            )
            else -> page.copy(
                library = LibraryState(
                    playlistId = page.browseId,
                    saved = page.browseId in savedReleases || page.library?.saved == true,
                ),
            )
        }
    }

    val topApple = remember(effectivePage.browseId, effectivePage.type, appleArtVersion) {
        effectivePage.takeIf { it.type == BrowseType.ARTIST }
            ?.let { AppleArtistArtRepository.cached(it.browseId) }
    }

    ITubeArtistTheme(darkTheme = true) {
        val detailPalette = rememberArtworkPalette(
            imageUrl = topApple?.heroUrl ?: effectivePage.thumbnailUrl,
            keyColors = topApple?.keyColors(),
        )
        val chromePageColor = detailPalette.background
        val activeShelf = controller.activeShelfByPage[effectivePage.browseId]
        val listState = controller.listStates.getOrPut(effectivePage.browseId) { LazyListState() }
        val isArtist = effectivePage.type == BrowseType.ARTIST
        val isRelease = !isArtist

        val releaseLibrary = remember(savedReleases) {
            savedReleases.associateWith { id -> LibraryState(id, true) }
        }
        val contentPadding = PaddingValues(
            top = topBarContentPadding(),
            bottom = if (hasMiniPlayer) 210.dp else 140.dp,
        )
        val currentArtistSong = MusicPlayerState.currentTrack?.toArtistSong()
        val isPlaying = MusicPlayerState.isPlaying

        CompositionLocalProvider(
            LocalAppBackdrop provides controller.appBackdrop,
            LocalLiquidGlassEnabled provides glassActive,
            LocalReduceDynamicBlur provides false,
        ) {
            Box(modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .snapshotBackdropSource(controller.pageBackdrop, floor = chromePageColor)
                        .layerBackdrop(controller.appBackdrop)
                        .hazeSource(state = controller.hazeState),
                ) {
                    AnimatedContent(
                        targetState = effectivePage.browseId,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 }) togetherWith
                                (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 12 })
                        },
                        label = "artist_detail_stack_transition",
                    ) { targetId ->
                        val shownPage = controller.detailStack.lastOrNull { it.browseId == targetId } ?: effectivePage
                        val shownSubscribed = shownPage.copy(
                            subscription = if (shownPage.type == BrowseType.ARTIST) {
                                SubscriptionState(shownPage.browseId, shownPage.browseId in subscribedArtists)
                            } else {
                                shownPage.subscription
                            },
                            library = if (shownPage.type != BrowseType.ARTIST) {
                                LibraryState(shownPage.browseId, shownPage.browseId in savedReleases)
                            } else {
                                shownPage.library
                            },
                        )
                        val shownListState = controller.listStates.getOrPut(targetId) { LazyListState() }
                        val shownShelf = controller.activeShelfByPage[targetId]
                        val shownSort = controller.songSortByPage[targetId] ?: SongSort.DEFAULT
                        val shownReleaseLibrary = buildMap {
                            savedReleases.forEach { put(it, LibraryState(it, true)) }
                            shownPage.sections.asSequence()
                                .flatMap { it.items.asSequence() }
                                .mapNotNull { it.browseId }
                                .forEach { id -> putIfAbsent(id, LibraryState(id, false)) }
                        }

                        ITubeDetailScreen(
                            page = shownSubscribed,
                            currentSong = currentArtistSong,
                            isPlaying = isPlaying,
                            listState = shownListState,
                            activeShelf = shownShelf,
                            onActiveShelfChange = { shelf ->
                                controller.activeShelfByPage[targetId] = shelf
                            },
                            onSongClick = { songs, idx ->
                                controller.playSongs(songs, idx, shownPage)
                            },
                            onSongLongPress = controller::openSongMenu,
                            onSongSwipe = { song ->
                                MusicPlayerState.playNext(song.toMusicTrack())
                                Toast.makeText(
                                    controller.context,
                                    "Playing \"${song.title}\" next",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onShuffle = { songs ->
                                val target = shownPage.shuffle
                                if (target != null && shownPage.type == BrowseType.ARTIST) {
                                    controller.startArtistWatchQueue(target)
                                } else {
                                    controller.playSongs(songs, 0, shownPage, shuffle = true)
                                }
                            },
                            onSectionItemClick = { item -> controller.onSectionItemClick(shownPage, item) },
                            onSectionItemLongPress = controller::onSectionItemLongPress,
                            onArtistClick = { id, name ->
                                controller.openDetail(id, name, "Artist", null, BrowseType.ARTIST)
                            },
                            searching = controller.searchingPageId == targetId,
                            onSearchingChange = { open ->
                                controller.searchingPageId = if (open) targetId else null
                            },
                            onToggleLibrary = { controller.toggleReleaseSaved(shownPage.browseId) },
                            onToggleSubscription = { controller.toggleArtistSubscription(shownPage.browseId) },
                            releaseLibrary = shownReleaseLibrary,
                            onToggleReleaseLibrary = controller::toggleReleaseSaved,
                            songSort = shownSort,
                            contentPadding = contentPadding,
                        )
                    }
                }

                val topFadeAlpha by remember(listState, isArtist, activeShelf) {
                    derivedStateOf {
                        if (!isArtist || activeShelf != null) return@derivedStateOf 1f
                        if (listState.firstVisibleItemIndex > 0) return@derivedStateOf 1f
                        val header = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == 0 }?.size?.toFloat()
                            ?: return@derivedStateOf 0f
                        ((listState.firstVisibleItemScrollOffset - header * TOP_FADE_START) /
                            (header * TOP_FADE_RAMP)).coerceIn(0f, 1f)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    TopFadeBlur(
                        hazeState = controller.hazeState,
                        pageColor = chromePageColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer { alpha = topFadeAlpha },
                    )
                } else {
                    BottomFadeScrim(
                        pageColor = chromePageColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .rotate(180f)
                            .graphicsLayer { alpha = topFadeAlpha },
                    )
                }

                if (isArtist && activeShelf == null) {
                    val topLum = rememberArtworkTopBandLuminance(topApple?.heroUrl ?: effectivePage.thumbnailUrl)
                    val statusScrimAlpha = topBandScrimAlpha(topLum)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(topBarHeight() * 0.72f)
                            .graphicsLayer { alpha = (1f - topFadeAlpha).coerceIn(0f, 1f) }
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = statusScrimAlpha),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }

                FrostedTopBar(
                    transparentBackdrop = true,
                    artworkPageChrome = true,
                    backButtonHazeState = controller.hazeState,
                    onBack = { controller.popTop() },
                    modifier = Modifier.align(Alignment.TopCenter),
                    actions = {
                        if (isArtist && activeShelf == null) {
                            if (effectivePage.browseId.startsWith("UC")) {
                                IconButton(
                                    onClick = {
                                        controller.shareUrl(
                                            effectivePage.title,
                                            "https://music.youtube.com/channel/${effectivePage.browseId}",
                                        )
                                    },
                                ) {
                                    Icon(
                                        SfIcons.Share,
                                        contentDescription = stringResource(R.string.share),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            IconButton(
                                modifier = Modifier.menuAnchor(),
                                onClick = { controller.openArtistTopMenu(effectivePage) },
                            ) {
                                Icon(
                                    Icons.Rounded.MoreHoriz,
                                    contentDescription = stringResource(R.string.more),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else if (isRelease && activeShelf == null) {
                            val releaseSongs = (effectivePage.songs as? UiState.Success)?.data.orEmpty()
                            val currentSort = controller.songSortByPage[effectivePage.browseId] ?: SongSort.DEFAULT
                            val searching = controller.searchingPageId == effectivePage.browseId
                            if (releaseSongs.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        controller.searchingPageId = if (searching) null else effectivePage.browseId
                                    },
                                ) {
                                    Icon(
                                        if (searching) Icons.Rounded.Close else ITubeIcons.Search,
                                        contentDescription = stringResource(
                                            if (searching) R.string.close_search else R.string.search_this_list,
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            IconButton(
                                modifier = Modifier.menuAnchor(),
                                onClick = { controller.openSortMenu(effectivePage.browseId, currentSort) },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Sort,
                                    contentDescription = stringResource(R.string.sort_songs),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (releaseSongs.isNotEmpty()) {
                                IconButton(
                                    modifier = Modifier.menuAnchor(),
                                    onClick = {
                                        controller.openBrowseMenu(
                                            BrowseTarget(
                                                browseId = effectivePage.browseId,
                                                title = effectivePage.title,
                                                subtitle = effectivePage.subtitle,
                                                thumbnailUrl = effectivePage.thumbnailUrl,
                                                type = effectivePage.type,
                                                songs = releaseSongs.sortedForDetail(currentSort).map { s ->
                                                    if (effectivePage.type == BrowseType.ALBUM) {
                                                        s.copy(albumName = s.albumName ?: effectivePage.title)
                                                    } else {
                                                        s
                                                    }
                                                },
                                                fromCard = false,
                                            ),
                                        )
                                    },
                                ) {
                                    Icon(
                                        Icons.Rounded.MoreHoriz,
                                        contentDescription = stringResource(R.string.more),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    },
                )

                BottomFadeScrim(
                    withMiniPlayer = hasMiniPlayer,
                    pageColor = chromePageColor,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
fun ArtistScreenOverlayPopups(
    controller: ArtistHostController,
) {
    if (!controller.isOpen && !controller.hasAnyPopupOpen()) return
    val glassActive = isGlassSupported()
    val favoriteIdsSnapshot = MusicPlayerState.favoriteIds
    val subscribedArtists by ArtistStateStore.subscribedArtists.collectAsStateWithLifecycle()
    val savedReleases by ArtistStateStore.savedReleases.collectAsStateWithLifecycle()

    val pageMenuUp =
        (controller.songActions != null && controller.songMenuOrigin == null) ||
            (controller.browseActions != null && controller.browseMenuOrigin == null) ||
            controller.artistMenu != null ||
            controller.sortMenu != null
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
                val isLiked = favoriteIdsSnapshot.contains(song.videoId)
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
                            Toast.makeText(
                                controller.context,
                                "Playing \"${song.title}\" next",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onAddToQueue = {
                            controller.clearMenus()
                            MusicPlayerState.addToQueue(listOf(song.toMusicTrack()))
                            Toast.makeText(
                                controller.context,
                                "Added \"${song.title}\" to queue",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onAddToPlaylist = onOpenAddToPlaylist,
                        onDownload = {
                            controller.clearMenus()
                            controller.downloadSong(song)
                        },
                        onOpenAlbum = { albumId, albumTitle ->
                            controller.clearMenus()
                            controller.openDetail(
                                browseId = albumId,
                                title = albumTitle ?: song.title,
                                subtitle = song.artist,
                                thumbnailUrl = song.thumbnailUrl,
                                type = BrowseType.ALBUM,
                            )
                        },
                        onOpenArtist = { artistId, artistName ->
                            controller.clearMenus()
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
                            controller.shareUrl(
                                song.title,
                                "https://music.youtube.com/watch?v=${song.videoId}",
                            )
                        },
                    )
                }
            }

            val browseMenuBody: @Composable (BrowseTarget) -> Unit = { target ->
                ArtistBrowseActionsMenu(
                    target = target,
                    savedInLibrary = if (target.type != BrowseType.ARTIST) {
                        target.browseId in savedReleases
                    } else {
                        null
                    },
                    onToggleLibrary = if (target.type != BrowseType.ARTIST) {
                        {
                            controller.clearMenus()
                            ArtistStateStore.toggleReleaseSaved(
                                context = controller.context,
                                browseId = target.browseId,
                                title = target.title,
                                subtitle = target.subtitle,
                                thumbnailUrl = target.thumbnailUrl,
                                type = target.type,
                            )
                        }
                    } else {
                        null
                    },
                    onPlay = {
                        controller.clearMenus()
                        controller.withBrowseSongs(target) { list ->
                            if (list.isNotEmpty()) controller.playSongs(list, 0)
                        }
                    }.takeIf { target.fromCard },
                    onShuffle = {
                        controller.clearMenus()
                        controller.withBrowseSongs(target) { list ->
                            if (list.isNotEmpty()) controller.playSongs(list, 0, shuffle = true)
                        }
                    }.takeIf { target.fromCard },
                    onPlayNext = {
                        controller.clearMenus()
                        controller.withBrowseSongs(target) { list ->
                            list.asReversed().forEach { MusicPlayerState.playNext(it.toMusicTrack()) }
                            if (list.isNotEmpty()) {
                                Toast.makeText(
                                    controller.context,
                                    "Playing ${target.title} next",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onAddToQueue = {
                        controller.clearMenus()
                        controller.withBrowseSongs(target) { list ->
                            if (list.isNotEmpty()) {
                                MusicPlayerState.addToQueue(list.map { it.toMusicTrack() })
                                Toast.makeText(
                                    controller.context,
                                    "Added ${target.title} to queue",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    onDownloadAll = {
                        controller.clearMenus()
                        controller.withBrowseSongs(target) { list ->
                            controller.downloadSongs(list)
                        }
                    },
                    onOpen = if (target.fromCard) {
                        {
                            controller.clearMenus()
                            controller.openDetail(
                                browseId = target.browseId,
                                title = target.title,
                                subtitle = target.subtitle,
                                thumbnailUrl = target.thumbnailUrl,
                                type = target.type,
                            )
                        }
                    } else {
                        null
                    },
                    onShare = {
                        controller.clearMenus()
                        val url = when (target.type) {
                            BrowseType.ARTIST -> "https://music.youtube.com/channel/${target.browseId}"
                            BrowseType.PLAYLIST -> "https://music.youtube.com/playlist?list=${target.browseId.removePrefix("VL")}"
                            else -> "https://music.youtube.com/browse/${target.browseId}"
                        }
                        controller.shareUrl(target.title, url)
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
                        controller.clearMenus()
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
                item = controller.browseActions,
                held = controller.browseMenuOrigin,
                hazeState = controller.hazeState,
                onDismiss = { controller.browseActions = null },
                onPreviewClick = { target ->
                    controller.clearMenus()
                    controller.openDetail(
                        browseId = target.browseId,
                        title = target.title,
                        subtitle = target.subtitle,
                        thumbnailUrl = target.thumbnailUrl,
                        type = target.type,
                    )
                },
            ) { target ->
                browseMenuBody(target)
            }

            IosMenuPopover(
                item = controller.browseActions?.takeIf { controller.browseMenuOrigin == null },
                anchor = controller.browseMenuAnchor,
                onDismiss = { controller.browseActions = null },
                backdrop = controller.pageBackdrop,
                hazeState = controller.hazeState,
            ) { target ->
                browseMenuBody(target)
            }

            IosMenuPopover(
                item = controller.artistMenu,
                anchor = controller.artistMenuAnchor,
                onDismiss = { controller.artistMenu = null },
                backdrop = controller.pageBackdrop,
                hazeState = controller.hazeState,
            ) { req ->
                val page = req.page
                val topSongs = (page.songs as? UiState.Success)?.data.orEmpty()
                val subscribed = page.browseId in subscribedArtists
                ArtistTopBarActionsMenu(
                    artistName = page.title,
                    subscribed = subscribed,
                    hasRadio = page.radio != null || topSongs.isNotEmpty(),
                    hasShuffle = page.shuffle != null || topSongs.isNotEmpty(),
                    onPlayTopSongs = {
                        controller.clearMenus()
                        controller.playSongs(topSongs, 0, page)
                    },
                    onShuffle = {
                        controller.clearMenus()
                        val shuffleTarget = page.shuffle
                        if (shuffleTarget != null) {
                            controller.startArtistWatchQueue(shuffleTarget)
                        } else {
                            controller.playSongs(topSongs, 0, page, shuffle = true)
                        }
                    },
                    onStartRadio = {
                        controller.clearMenus()
                        val radioTarget = page.radio
                        if (radioTarget != null) {
                            controller.startArtistWatchQueue(radioTarget)
                        } else {
                            topSongs.firstOrNull()?.let(controller::startRadioForSong)
                        }
                    },
                    onToggleSubscribe = {
                        controller.toggleArtistSubscription(page.browseId)
                        controller.clearMenus()
                    },
                    onShare = {
                        controller.clearMenus()
                        controller.shareUrl(
                            page.title,
                            "https://music.youtube.com/channel/${page.browseId}",
                        )
                    },
                )
            }

            IosMenuPopover(
                item = controller.sortMenu,
                anchor = controller.sortMenuAnchor,
                onDismiss = { controller.sortMenu = null },
                backdrop = controller.pageBackdrop,
                hazeState = controller.hazeState,
            ) { req ->
                ReleaseSortMenuContent(
                    current = controller.songSortByPage[req.browseId] ?: req.currentSort,
                    onSelect = { sort ->
                        controller.songSortByPage[req.browseId] = sort
                        controller.sortMenu = null
                    },
                )
            }

            LaunchedEffect(controller.songActions == null) {
                if (controller.songActions == null) {
                    controller.songMenuOrigin = null
                    controller.songMenuAnchor = null
                }
            }
            LaunchedEffect(controller.browseActions == null) {
                if (controller.browseActions == null) {
                    controller.browseMenuOrigin = null
                    controller.browseMenuAnchor = null
                }
            }
            LaunchedEffect(controller.artistMenu == null) {
                if (controller.artistMenu == null) {
                    controller.artistMenuAnchor = null
                }
            }
            LaunchedEffect(controller.sortMenu == null) {
                if (controller.sortMenu == null) {
                    controller.sortMenuAnchor = null
                }
            }
        }
    }
}

private fun browseTypeOf(browseId: String, hint: BrowseType): BrowseType = when {
    browseId.startsWith("UC") -> BrowseType.ARTIST
    browseId.startsWith("MPRE") || browseId.startsWith("OLAK") -> BrowseType.ALBUM
    browseId.startsWith("VL") || browseId.startsWith("PL") -> BrowseType.PLAYLIST
    else -> hint
}

private fun shelfSong(item: ShelfItem): Song? {
    val videoId = item.videoId ?: return null
    return Song(
        videoId = videoId,
        title = item.title,
        artist = item.subtitle,
        thumbnailUrl = item.thumbnailUrl,
        isVideo = item.isVideo,
    )
}
