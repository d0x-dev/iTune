package com.darkxvenom.itune.ui.artist

import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import coil.size.Size
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.artist.AppleArtistArt
import com.darkxvenom.itune.data.artist.AppleArtistArtRepository
import com.darkxvenom.itune.data.artist.ArtistSignature
import com.darkxvenom.itune.data.artist.ArtistSignatureRepository
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.artist.CanvasArtwork
import com.darkxvenom.itune.data.artist.SignatureInkTransformation
import com.darkxvenom.itune.data.artist.appleCatalogue
import com.darkxvenom.itune.data.artist.keyColors
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.DetailPage
import com.darkxvenom.itune.data.model.HEADER_ART_PX
import com.darkxvenom.itune.data.model.HomeShelf
import com.darkxvenom.itune.data.model.LibraryState
import com.darkxvenom.itune.data.model.ROW_ART_PX
import com.darkxvenom.itune.data.model.ShelfItem
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.SongSort
import com.darkxvenom.itune.data.model.SubscriptionState
import com.darkxvenom.itune.data.model.UiState
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.data.model.durationMillis
import com.darkxvenom.itune.data.model.playingRowMatcher
import com.darkxvenom.itune.ui.artist.canvas.CanvasArtworkPlayer
import com.darkxvenom.itune.ui.artist.components.CoverPlaceholder
import com.darkxvenom.itune.ui.artist.components.DownloadedBadge
import com.darkxvenom.itune.ui.artist.components.ExplicitSongTitle
import com.darkxvenom.itune.ui.artist.components.FadingText
import com.darkxvenom.itune.ui.artist.components.LIBRARY_GRID_SPACING
import com.darkxvenom.itune.ui.artist.components.LocalReduceDynamicBlur
import com.darkxvenom.itune.ui.artist.components.MessageState
import com.darkxvenom.itune.ui.artist.components.NUMBERED_ROW_DIVIDER_INSET
import com.darkxvenom.itune.ui.artist.components.PAGE_GUTTER
import com.darkxvenom.itune.ui.artist.components.ROW_DIVIDER_INSET
import com.darkxvenom.itune.ui.artist.components.RowMoreButton
import com.darkxvenom.itune.ui.artist.components.SHELF_CARD_WIDTH
import com.darkxvenom.itune.ui.artist.components.SearchPlayingBars
import com.darkxvenom.itune.ui.artist.components.SongRow
import com.darkxvenom.itune.ui.artist.components.VIDEO_CARD_WIDTH
import com.darkxvenom.itune.ui.artist.components.VideoShelfCard
import com.darkxvenom.itune.ui.artist.components.continuous
import com.darkxvenom.itune.ui.artist.components.detailSkeleton
import com.darkxvenom.itune.ui.artist.components.libraryGrid
import com.darkxvenom.itune.ui.artist.components.lightweightLiquidGlass
import com.darkxvenom.itune.ui.artist.components.liquidGlassRim
import com.darkxvenom.itune.ui.artist.components.longPressMenuClickable
import com.darkxvenom.itune.ui.artist.components.thumbnailBorder
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.components.trackArtSize
import com.darkxvenom.itune.ui.artist.components.trackColumnWidth
import com.darkxvenom.itune.ui.artist.graphics.FADE_CURVE_STOPS
import com.darkxvenom.itune.ui.artist.graphics.smootherStep
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.theme.ArtworkPalette
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import com.darkxvenom.itune.ui.library.PlaylistCollageAvatar
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_ARTIST_SONGS = 20
private const val SONGS_PER_COLUMN = 4
private const val ARTIST_ROW_MAX_ITEMS = 5

/** The artist photo, very slightly taller than it is wide. */
private const val ARTIST_PHOTO_RATIO = 0.95f

/** The framed sleeve on a release page, as a fraction of the page width. */
private const val SLEEVE_FRACTION = 0.76f

/** ...and of its height, so a landscape window still shows the title under it. */
private const val SLEEVE_MAX_HEIGHT = 0.5f

private val SLEEVE_SHAPE = RoundedCornerShape(12.dp)
private val SLEEVE_SHADOW = 18.dp

private const val FULL_BLEED_MAX_HEIGHT = 0.62f
private const val FULL_BLEED_FADE_FRACTION = 0.42f
private val FULL_BLEED_TITLE_OVERLAP = 44.dp

/** Shuffle and Add to Library either side of the release page's Play pill. */
private val RELEASE_SIDE_BUTTON = 50.dp
private val RELEASE_ACTION_GAP = 14.dp

/** Past this the Play pill stops growing, so a tablet doesn't get a bar. */
private val RELEASE_ACTIONS_MAX_WIDTH = 420.dp
private val PILL_SHAPE = RoundedCornerShape(12.dp)

private const val SEARCH_ITEM_INDEX = 1

private val HEADER_GUTTER = PAGE_GUTTER + 8.dp
private val ABOUT_GUTTER = PAGE_GUTTER + 6.dp
private val ARTIST_CONTENT_GUTTER = PAGE_GUTTER

private val HEADER_DROP = 44.dp
private val ARTIST_HEADER_LIFT = 64.dp

/** The artist action row: a large Play flanked by two smaller glass circles. */
private val ARTIST_PLAY_BUTTON = 70.dp
private const val ARTIST_PLAY_ICON_SCALE = 0.56f
private val ARTIST_SIDE_BUTTON = 52.dp
private val ARTIST_ACTION_GAP = 24.dp

/** The title logo's widest share of the page, and tallest share of the photo. */
private const val ARTIST_LOGO_WIDTH = 0.82f

/** The signature closing the About section. */
private val ABOUT_LOGO_HEIGHT = 56.dp

/** A signature is drawn at this share of a title logo's size. */
private const val SIGNATURE_SCALE = 0.7f
private const val ARTIST_LOGO_MAX_HEIGHT = 0.30f

private const val LOGO_RETRIES = 2
private const val LOGO_RETRY_DELAY_MS = 800L

/** Vector signatures decode at this width so small files stay crisp laid out large. */
private const val SVG_DECODE_WIDTH = 1600

private val CREDIT_SEPARATOR = Regex(""",\s|\s&\s|\s(?:x|and|feat\.?|ft\.?|with)\s""", RegexOption.IGNORE_CASE)

private fun String.looksLikeCredit() = CREDIT_SEPARATOR.containsMatchIn(this)

/**
 * iTube's Artist & Release Detail Screen with dynamic artwork palette extraction,
 * Apple Music artist hero art + wordmark logos + animated HLS motion canvas video, smooth blurred
 * bottom transition, Top Songs 4-row snap pager, TopReleaseCard, shelves carousels & "Show all" grid,
 * About section with stats chips & signature, and release sub-page support.
 */
@Composable
fun ITubeDetailScreen(
    page: DetailPage,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
    onSongSwipe: (Song) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onSectionItemClick: (ShelfItem) -> Unit,
    onArtistClick: (String, String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    activeShelf: HomeShelf? = null,
    onActiveShelfChange: (HomeShelf?) -> Unit = {},
    onSectionItemLongPress: ((ShelfItem) -> Unit)? = null,
    searching: Boolean = false,
    onSearchingChange: (Boolean) -> Unit = {},
    onToggleLibrary: (() -> Unit)? = null,
    onToggleSubscription: (() -> Unit)? = null,
    releaseLibrary: Map<String, LibraryState> = emptyMap(),
    onLoadReleaseLibrary: ((String) -> Unit)? = null,
    onToggleReleaseLibrary: ((String) -> Unit)? = null,
    songSort: SongSort = SongSort.DEFAULT,
    animatedCanvasEnabled: Boolean = true,
    fullBleedArtwork: Boolean = false,
    downloadedIds: Set<String> = emptySet(),
    onAddSuggested: (Song) -> Unit = {},
    emptyContent: (@Composable () -> Unit)? = null,
) {
    val rawSongs = (page.songs as? UiState.Success)?.data.orEmpty()
    val songs = remember(rawSongs, songSort) { rawSongs.sortedForDetail(songSort) }
    val originalTrackNumbers = remember(rawSongs) {
        val byTag = rawSongs.useTrackTags()
        rawSongs.withIndex().associate { (i, s) -> s.videoId to ((if (byTag) s.trackNumber else null) ?: (i + 1)) }
    }
    val isArtist = page.type == BrowseType.ARTIST
    val topReleaseId = if (isArtist) page.sections.topRelease()?.browseId else null
    LaunchedEffect(topReleaseId) { topReleaseId?.let { onLoadReleaseLibrary?.invoke(it) } }

    val appleArtVersion by AppleArtistArtRepository.updates.collectAsStateWithLifecycle()
    var appleArt by remember(page.browseId, appleArtVersion) {
        mutableStateOf(if (isArtist) AppleArtistArtRepository.cached(page.browseId) else null)
    }
    val songsLoading = page.songs is UiState.Loading
    LaunchedEffect(page.browseId, page.title, songsLoading) {
        if (isArtist && !songsLoading) {
            appleArt = AppleArtistArtRepository.artFor(page.browseId, page.title) { page.appleCatalogue() }
                ?: appleArt
        }
    }

    val signatureEnabled by ArtistStateStore.signatureApiEnabled.collectAsStateWithLifecycle()
    val signatureBaseUrl by ArtistStateStore.signatureApiUrl.collectAsStateWithLifecycle()
    val signatureUpdates by ArtistSignatureRepository.updates.collectAsStateWithLifecycle()
    var signature by remember(page.browseId) { mutableStateOf<ArtistSignature?>(null) }
    val nameSettled = !page.title.looksLikeCredit() || page.songs !is UiState.Loading
    LaunchedEffect(page.browseId, page.title, nameSettled, signatureEnabled, signatureBaseUrl, signatureUpdates) {
        if (!isArtist || !nameSettled) return@LaunchedEffect
        val base = ArtistSignatureRepository.effectiveBaseUrl(signatureEnabled, signatureBaseUrl)
        if (base == null) {
            signature = null
            return@LaunchedEffect
        }
        val backoff = listOf(0L, 5_000L, 15_000L, 30_000L)
        for (wait in backoff) {
            if (wait > 0) delay(wait)
            signature = ArtistSignatureRepository.cached(page.title, base)
                ?: ArtistSignatureRepository.signatureFor(page.title, base)
                ?: signature
            if (signature != null) return@LaunchedEffect
        }
    }

    val palette = rememberArtworkPalette(
        imageUrl = appleArt?.heroUrl ?: page.thumbnailUrl,
        keyColors = appleArt?.keyColors(),
    )

    var query by rememberSaveable(page.browseId) { mutableStateOf("") }
    var focusSearch by remember(page.browseId) { mutableStateOf(false) }
    val closeSearch = { onSearchingChange(false) }
    BackHandler(enabled = searching) { closeSearch() }

    val matches = remember(songs, query) { songs.matching(query) }
    val queue = remember(matches) { matches.map { it.value } }
    val suggested = remember(page.suggestedSongs, query) {
        page.suggestedSongs.matching(query).map { it.value }
    }
    val downloadedTint = palette.accent

    val artistVideo = appleArt?.videoUrl
    var canvas by remember(page.browseId) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(isArtist, artistVideo, animatedCanvasEnabled) {
        if (isArtist) {
            canvas = artistVideo?.takeIf { animatedCanvasEnabled }?.let { CanvasArtwork(url = it) }
        }
    }

    val searchStop = with(LocalDensity.current) { topBarContentPadding().roundToPx() }
    LaunchedEffect(searching) {
        if (searching) {
            focusSearch = true
            listState.animateScrollToItem(SEARCH_ITEM_INDEX, -searchStop)
        } else {
            query = ""
        }
    }

    AnimatedContent(
        targetState = activeShelf,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
        },
        label = "artist_shelf_transition",
        modifier = modifier.fillMaxSize(),
    ) { targetShelf ->
        if (targetShelf == null) {
            val pageColor = if (isArtist) palette.wash else palette.background
            BoxWithConstraints(Modifier.fillMaxSize().background(pageColor)) {
                val artHeight = (maxWidth / ARTIST_PHOTO_RATIO).coerceAtMost(maxHeight * 0.6f)
                val sleeveSize = (maxWidth * SLEEVE_FRACTION).coerceAtMost(maxHeight * SLEEVE_MAX_HEIGHT)
                val fullBleedSleeve = fullBleedArtwork && maxWidth <= maxHeight * FULL_BLEED_MAX_HEIGHT

                if (isArtist) {
                    PageBackground(
                        page = page,
                        palette = palette,
                        canvas = canvas,
                        artHeight = artHeight,
                        listState = listState,
                        heroUrl = appleArt?.heroUrl,
                        modifier = Modifier.matchParentSize(),
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                ) {
                    item(key = "header") {
                        if (isArtist) {
                            ArtistHeader(
                                page = page,
                                palette = palette,
                                artHeight = artHeight,
                                appleArt = appleArt,
                                signature = signature,
                            )
                        } else {
                            ReleaseHeader(
                                page = page,
                                palette = palette,
                                sleeveSize = sleeveSize,
                                fullBleed = fullBleedSleeve,
                                canvas = canvas,
                                songs = songs,
                                onPlay = { onSongClick(songs, 0) },
                                onShuffle = { onShuffle(songs) },
                                onArtistClick = onArtistClick,
                                onToggleLibrary = onToggleLibrary,
                            )
                        }
                    }

                    if (searching) {
                        item(key = "search") {
                            DetailSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                onClose = closeSearch,
                                autoFocus = focusSearch,
                                onFocused = { focusSearch = false },
                                palette = palette,
                            )
                        }
                    }

                    val description = page.description

                    if (songs.isNotEmpty() && isArtist) {
                        item(key = "actions") {
                            ActionRow(
                                onPlay = { onSongClick(songs, 0) },
                                onShuffle = { onShuffle(songs) },
                                subscription = page.subscription?.takeIf { onToggleSubscription != null },
                                onToggleSubscription = onToggleSubscription,
                                bottomSpace = 22.dp,
                                playColor = appleArt?.keyColor?.let { Color(it) },
                            )
                        }
                    }

                    val topRelease = if (isArtist) page.sections.topRelease() else null
                    if (topRelease != null) {
                        item(key = "top-release") {
                            TopReleaseCard(
                                item = topRelease,
                                palette = palette,
                                onClick = { onSectionItemClick(topRelease) },
                                onLongPress = onSectionItemLongPress?.let { { it(topRelease) } },
                                saved = topRelease.browseId?.let { releaseLibrary[it]?.saved },
                                onToggleSaved = onToggleReleaseLibrary?.let { toggle ->
                                    topRelease.browseId?.let { id -> { toggle(id) } }
                                },
                            )
                        }
                    }

                    val hasStats = isArtist &&
                        (page.subscriberCountText != null || page.monthlyListenerCount != null)
                    val aboutSignature = if (isArtist && appleArt?.logoUrl != null) signature else null
                    val showAbout = (!description.isNullOrBlank() &&
                        (page.type == BrowseType.ALBUM || isArtist)) || hasStats || aboutSignature != null

                    fun LazyListScope.aboutItem() {
                        if (!showAbout) return
                        item(key = "about") {
                            Box(Modifier.padding(top = if (isArtist) 28.dp else 0.dp)) {
                                AboutSection(
                                    title = stringResource(
                                        if (isArtist) R.string.about_artist else R.string.about_album,
                                    ),
                                    text = description?.takeIf { it.isNotBlank() },
                                    palette = palette,
                                    horizontalPadding = if (isArtist) ARTIST_CONTENT_GUTTER else ABOUT_GUTTER,
                                    stats = if (hasStats) {
                                        {
                                            ArtistStatsRow(
                                                subscriberCountText = page.subscriberCountText,
                                                monthlyListenerCount = page.monthlyListenerCount,
                                                palette = palette,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    footer = aboutSignature?.let { sig ->
                                        {
                                            AboutSignature(
                                                signature = sig,
                                                name = page.title,
                                                palette = palette,
                                                horizontalPadding = ARTIST_CONTENT_GUTTER,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    if (!isArtist) aboutItem()

                    when (val state = page.songs) {
                        is UiState.Loading -> detailSkeleton(isArtist)
                        is UiState.Error -> item { MessageState(state.message) }
                        is UiState.Success -> if (isArtist) {
                            item {
                                val top = state.data.take(MAX_ARTIST_SONGS)
                                val isCurrentRow = playingRowMatcher(top, currentSong)
                                SectionHeading(
                                    title = stringResource(R.string.top_songs),
                                    palette = palette,
                                    horizontalPadding = ARTIST_CONTENT_GUTTER,
                                )
                                BoxWithConstraints {
                                    val columnWidth = trackColumnWidth(maxWidth)
                                    val topSongsState = rememberLazyListState()
                                    LazyRow(
                                        state = topSongsState,
                                        contentPadding = PaddingValues(horizontal = ARTIST_CONTENT_GUTTER),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        flingBehavior = rememberSnapFlingBehavior(lazyListState = topSongsState),
                                    ) {
                                        items(top.chunked(SONGS_PER_COLUMN)) { column ->
                                            Column(Modifier.width(columnWidth)) {
                                                column.forEach { song ->
                                                    CompactSongRow(
                                                        song = song,
                                                        palette = palette,
                                                        onClick = { onSongClick(top, top.indexOf(song)) },
                                                        onLongPress = { onSongLongPress(song) },
                                                        downloadedTint = downloadedTint,
                                                        downloadedIds = downloadedIds,
                                                        isCurrent = isCurrentRow(song),
                                                        isPlaying = isCurrentRow(song) && isPlaying,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val numbered = page.type == BrowseType.ALBUM
                            val isCurrentRow = playingRowMatcher(matches.map { it.value }, currentSong)
                            if (state.data.isEmpty() && emptyContent != null) {
                                item(key = "empty-playlist-content") {
                                    emptyContent()
                                }
                            } else if (matches.isEmpty() && state.data.isNotEmpty()) {
                                item(key = "no-matches") {
                                    MessageState(stringResource(R.string.nothing_matches, query))
                                }
                            }
                            itemsIndexed(
                                items = matches,
                                key = { _, entry -> "${entry.index}_${entry.value.videoId}" },
                            ) { position, entry ->
                                val song = entry.value
                                val isCurrent = isCurrentRow(song)
                                SongRow(
                                    song = if (numbered) {
                                        song
                                    } else {
                                        song.copy(thumbnailUrl = song.thumbnailUrl ?: page.thumbnailUrl)
                                    },
                                    onClick = { onSongClick(queue, position) },
                                    onLongPress = { onSongLongPress(song) },
                                    onSwipeToQueue = { onSongSwipe(song) },
                                    rowBackground = Color.Transparent,
                                    trackNumber = originalTrackNumbers[song.videoId].takeIf { numbered },
                                    subtitleColor = palette.onBackgroundVariant,
                                    downloadedIds = downloadedIds,
                                    downloadedTint = downloadedTint,
                                    isCurrent = isCurrent,
                                    isPlaying = isCurrent && isPlaying,
                                    activeTint = palette.accent,
                                    searchPlayingStyle = true,
                                )
                                if (position < matches.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(
                                            start = if (numbered) NUMBERED_ROW_DIVIDER_INSET else ROW_DIVIDER_INSET,
                                        ),
                                        thickness = 0.5.dp,
                                        color = palette.divider,
                                    )
                                }
                            }
                        }
                    }

                    if (suggested.isNotEmpty()) {
                        item(key = "suggested-heading") {
                            SectionHeading(stringResource(R.string.suggested), palette)
                        }
                        val isSuggestedCurrent = playingRowMatcher(suggested, currentSong)
                        itemsIndexed(
                            suggested,
                            key = { _, song -> "suggested-${song.videoId}" },
                        ) { index, song ->
                            SuggestedSongRow(
                                song = song,
                                palette = palette,
                                onClick = { onSongClick(suggested, index) },
                                onLongPress = { onSongLongPress(song) },
                                onAdd = { onAddSuggested(song) },
                                downloadedTint = downloadedTint,
                                downloadedIds = downloadedIds,
                                isCurrent = isSuggestedCurrent(song),
                                isPlaying = isSuggestedCurrent(song) && isPlaying,
                            )
                            if (index < suggested.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                    thickness = 0.5.dp,
                                    color = palette.divider,
                                )
                            }
                        }
                    }

                    items(page.sections) { shelf ->
                        val canShowAll = shelf.items.size > ARTIST_ROW_MAX_ITEMS
                        val displayItems = remember(shelf.items) {
                            if (canShowAll) shelf.items.take(ARTIST_ROW_MAX_ITEMS) else shelf.items
                        }
                        Column(Modifier.padding(top = 22.dp)) {
                            SectionHeading(
                                title = shelf.title,
                                palette = palette,
                                onShowAll = if (canShowAll) {
                                    { onActiveShelfChange(shelf) }
                                } else {
                                    null
                                },
                                horizontalPadding = ARTIST_CONTENT_GUTTER,
                            )
                            val carouselState = rememberLazyListState()
                            LazyRow(
                                state = carouselState,
                                contentPadding = PaddingValues(horizontal = ARTIST_CONTENT_GUTTER),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                flingBehavior = rememberSnapFlingBehavior(lazyListState = carouselState),
                            ) {
                                items(displayItems) { item ->
                                    SectionCard(
                                        item = item,
                                        palette = palette,
                                        onClick = { onSectionItemClick(item) },
                                        onLongPress = onSectionItemLongPress?.let { { it(item) } },
                                        modifier = Modifier.width(if (item.isVideo) VIDEO_CARD_WIDTH else SHELF_CARD_WIDTH),
                                    )
                                }
                            }
                        }
                    }
                    if (isArtist) aboutItem()
                }
            }
        } else {
            ArtistShelfGridPage(
                shelf = targetShelf,
                palette = palette,
                onItemClick = onSectionItemClick,
                onItemLongPress = onSectionItemLongPress,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun ReleaseHeader(
    page: DetailPage,
    palette: ArtworkPalette,
    sleeveSize: Dp,
    fullBleed: Boolean,
    canvas: CanvasArtwork?,
    songs: List<Song>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onArtistClick: ((String, String) -> Unit)?,
    onToggleLibrary: (() -> Unit)?,
) {
    val (credit, meta) = page.headerLines(songs.size, songs.playtime())
    val artist = songs.firstOrNull()
    var artFailed by remember(page.thumbnailUrl) { mutableStateOf(false) }
    val bleed = fullBleed && page.thumbnailUrl != null && !artFailed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (bleed) 0.dp else topBarContentPadding(), bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bleed) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val height = (placeable.height - FULL_BLEED_TITLE_OVERLAP.roundToPx()).coerceAtLeast(0)
                        layout(placeable.width, height) { placeable.place(0, 0) }
                    }
                    .aspectRatio(1f),
            ) {
                AsyncImage(
                    model = page.thumbnailUrl.artworkAt(HEADER_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { artFailed = it is AsyncImagePainter.State.Error },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colorStops = Array(FADE_CURVE_STOPS + 1) { i ->
                                        val t = i / FADE_CURVE_STOPS.toFloat()
                                        t to Color.Black.copy(alpha = 1f - smootherStep(t))
                                    },
                                    startY = size.height * (1f - FULL_BLEED_FADE_FRACTION),
                                    endY = size.height,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                        .background(palette.elevated),
                )
                canvas?.let { clip ->
                    CanvasArtworkPlayer(
                        canvas = clip,
                        isPlaying = true,
                        bottomFade = FULL_BLEED_FADE_FRACTION,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .size(sleeveSize)
                    .shadow(SLEEVE_SHADOW, SLEEVE_SHAPE)
                    .background(palette.elevated)
                    .thumbnailBorder(SLEEVE_SHAPE),
            ) {
                if (page.thumbnailUrl == null || artFailed) {
                    CoverPlaceholder(
                        icon = page.placeholderIcon(),
                        base = palette.elevated,
                        tint = palette.onBackgroundVariant,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                AsyncImage(
                    model = page.thumbnailUrl.artworkAt(HEADER_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { artFailed = it is AsyncImagePainter.State.Error },
                    modifier = Modifier.matchParentSize(),
                )
                canvas?.let { clip ->
                    CanvasArtworkPlayer(
                        canvas = clip,
                        isPlaying = true,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = HEADER_GUTTER),
        )
        if (credit.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = credit,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Normal,
                    fontSize = 19.sp,
                ),
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = HEADER_GUTTER)
                    .let { m ->
                        val id = artist?.artistId
                        if (id == null || onArtistClick == null) {
                            m
                        } else {
                            m.clip(RoundedCornerShape(6.dp))
                                .clickable { onArtistClick(id, artist.artist) }
                        }
                    },
            )
        }
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = palette.onBackgroundVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = HEADER_GUTTER),
            )
        }

        if (songs.isNotEmpty()) {
            val library = page.library?.takeIf { onToggleLibrary != null }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .widthIn(max = RELEASE_ACTIONS_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = HEADER_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(RELEASE_ACTION_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = ITubeIcons.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    onClick = onShuffle,
                    haptic = Haptic.Resume,
                    size = RELEASE_SIDE_BUTTON,
                )
                ActionPill(
                    icon = ITubeIcons.Play,
                    label = stringResource(R.string.play),
                    onClick = onPlay,
                    filled = true,
                    size = RELEASE_SIDE_BUTTON,
                    modifier = Modifier.weight(1f),
                )
                if (library != null) {
                    CircleIconButton(
                        icon = if (library.saved) ITubeIcons.Check else ITubeIcons.Plus,
                        contentDescription = if (library.saved) {
                            stringResource(R.string.remove_from_library)
                        } else {
                            stringResource(R.string.add_to_library)
                        },
                        onClick = { onToggleLibrary?.invoke() },
                        haptic = if (library.saved) Haptic.ToggleOff else Haptic.ToggleOn,
                        size = RELEASE_SIDE_BUTTON,
                    )
                } else {
                    CircleIconButton(
                        icon = ITubeIcons.Plus,
                        contentDescription = stringResource(R.string.add_to_library),
                        onClick = {},
                        size = RELEASE_SIDE_BUTTON,
                        enabled = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    autoFocus: Boolean,
    onFocused: () -> Unit,
    palette: ArtworkPalette,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focus.requestFocus()
            onFocused()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER)
            .padding(bottom = 10.dp)
            .height(46.dp)
            .clip(PILL_SHAPE)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), PILL_SHAPE)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ITubeIcons.Search,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_this_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onBackgroundVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { if (query.isEmpty()) onClose() else onQueryChange("") },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(
                    if (query.isEmpty()) R.string.close_search else R.string.clear_search,
                ),
                tint = palette.onBackgroundVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

internal fun List<Song>.sortedForDetail(sort: SongSort): List<Song> = when (sort) {
    SongSort.DEFAULT -> this
    SongSort.TITLE_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.TITLE_DESC -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.DATE_ADDED_ASC -> withIndex()
        .sortedWith(
            compareBy<IndexedValue<Song>> { (_, song) ->
                song.localDateAddedSeconds ?: Long.MAX_VALUE
            }.thenBy { (position, _) -> position },
        )
        .map { it.value }
    SongSort.DATE_ADDED_DESC -> withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<Song>> { (_, song) ->
                song.localDateAddedSeconds ?: Long.MIN_VALUE
            }.thenByDescending { (position, _) -> position },
        )
        .map { it.value }
}

internal fun List<Song>.matching(query: String): List<IndexedValue<Song>> {
    val all = withIndex().toList()
    if (query.isBlank()) return all
    return all.filter { (_, song) -> song.matchesSearch(query) }
}

private fun Song.matchesSearch(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        artist.contains(query, ignoreCase = true) ||
        albumName?.contains(query, ignoreCase = true) == true

private fun List<Song>.useTrackTags(): Boolean {
    val numbered = count { (it.trackNumber ?: 0) > 0 }
    return numbered > 0 && numbered >= size / 2
}

@Composable
private fun ArtistHeader(
    page: DetailPage,
    palette: ArtworkPalette,
    artHeight: Dp,
    appleArt: AppleArtistArt?,
    signature: ArtistSignature?,
) {
    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(artHeight + HEADER_DROP - ARTIST_HEADER_LIFT))
        val logoUrl = appleArt?.logoUrl
        val signatureUrl = signature?.imageUrl.takeIf { logoUrl == null }
        val imageUrl = logoUrl ?: signatureUrl
        val tint = if (logoUrl == null && signature?.format == "svg") {
            ColorFilter.tint(Color.White)
        } else {
            null
        }
        var logoShown by remember(imageUrl) { mutableStateOf(false) }
        if (imageUrl != null) {
            val context = LocalContext.current
            var attempt by remember(imageUrl) { mutableIntStateOf(0) }
            var failed by remember(imageUrl) { mutableStateOf(false) }
            val isSignatureSvg = logoUrl == null && signature?.format == "svg"
            val isSignatureScan = logoUrl == null && signature != null && !isSignatureSvg
            val aspect = (if (logoUrl != null) appleArt?.logoAspect else signature?.aspect)
                ?.coerceIn(0.4f, 6f) ?: 1f
            val request = remember(imageUrl, attempt) {
                ImageRequest.Builder(context).data(imageUrl).apply {
                    if (isSignatureSvg) {
                        decoderFactory(SvgDecoder.Factory())
                        size(SVG_DECODE_WIDTH, (SVG_DECODE_WIDTH / aspect).roundToInt())
                    } else {
                        size(Size.ORIGINAL)
                    }
                    if (isSignatureScan) transformations(SignatureInkTransformation)
                }.build()
            }
            LaunchedEffect(failed) {
                if (failed && attempt < LOGO_RETRIES) {
                    delay(LOGO_RETRY_DELAY_MS)
                    failed = false
                    attempt++
                }
            }
            val isSignature = logoUrl == null && signature != null
            val sizeFactor = if (isSignature) SIGNATURE_SCALE else 1f
            val maxLogoHeight = artHeight * ARTIST_LOGO_MAX_HEIGHT * sizeFactor
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = page.title,
                    contentScale = ContentScale.Fit,
                    colorFilter = tint,
                    onState = { state ->
                        when (state) {
                            is AsyncImagePainter.State.Success -> logoShown = true
                            is AsyncImagePainter.State.Error -> {
                                val cause = state.result.throwable.let { "${it.javaClass.simpleName}: ${it.message}" }
                                Log.d("ArtistHeader", "image load failed: $imageUrl attempt $attempt cause=$cause")
                                failed = true
                            }
                            else -> Unit
                        }
                    },
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = if (isSignature) 6.dp else 20.dp)
                        .layout { measurable, constraints ->
                            val widest = constraints.maxWidth * ARTIST_LOGO_WIDTH * sizeFactor
                            val height = minOf(widest / aspect, maxLogoHeight.toPx()).roundToInt().coerceAtLeast(1)
                            val width = (height * aspect).roundToInt().coerceAtLeast(1)
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            layout(width, height) { placeable.place(0, 0) }
                        },
                )
                if (isSignature && logoShown) {
                    SignedByCaption(
                        name = page.title,
                        palette = palette,
                        modifier = Modifier.padding(horizontal = HEADER_GUTTER).padding(bottom = 20.dp),
                    )
                }
            }
        }
        if (!logoShown) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.displayLarge,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = HEADER_GUTTER, end = HEADER_GUTTER, top = 14.dp, bottom = 7.dp),
            )
        }
    }
}

@Composable
private fun PageBackground(
    page: DetailPage,
    palette: ArtworkPalette,
    canvas: CanvasArtwork?,
    artHeight: Dp,
    listState: LazyListState,
    heroUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur = LocalReduceDynamicBlur.current
    val softenFoot = canvas == null && !reduceDynamicBlur &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val art = heroUrl ?: page.thumbnailUrl.artworkAt(HEADER_ART_PX)

    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(artHeight)
                .offset { IntOffset(0, listState.headerTop(artHeight.toPx()).roundToInt()) },
        ) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(palette.elevated),
            )

            canvas?.let { clip ->
                CanvasArtworkPlayer(
                    canvas = clip,
                    isPlaying = true,
                    modifier = Modifier.matchParentSize(),
                )
            }

            if (softenFoot) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(SOFT_FOOT_MASK, blendMode = BlendMode.DstIn)
                        }
                        .blur(SOFT_FOOT_BLUR, BlurredEdgeTreatment.Rectangle),
                )
            }

            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            0.65f to palette.wash.copy(alpha = 0.30f),
                            0.82f to palette.wash.copy(alpha = 0.72f),
                            0.94f to palette.wash.copy(alpha = 0.95f),
                            1.00f to palette.wash,
                        ),
                    ),
            )
        }
    }
}

private fun LazyListState.headerTop(artHeightPx: Float): Float =
    if (firstVisibleItemIndex == 0) -firstVisibleItemScrollOffset.toFloat() else -artHeightPx * 2f

private val SOFT_FOOT_MASK = Brush.verticalGradient(
    0.55f to Color.Transparent,
    0.85f to Color.Black,
)

private val SOFT_FOOT_BLUR = 48.dp

@Composable
private fun ActionRow(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    bottomSpace: Dp = 22.dp,
    subscription: SubscriptionState? = null,
    onToggleSubscription: (() -> Unit)? = null,
    playColor: Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HEADER_GUTTER),
        horizontalArrangement = Arrangement.spacedBy(ARTIST_ACTION_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = ITubeIcons.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            onClick = onShuffle,
            haptic = Haptic.Resume,
            size = ARTIST_SIDE_BUTTON,
        )

        PlayCircle(
            onClick = onPlay,
            size = ARTIST_PLAY_BUTTON,
            iconScale = ARTIST_PLAY_ICON_SCALE,
            containerColor = playColor ?: Color.White,
        )

        if (subscription != null) {
            CircleIconButton(
                icon = if (subscription.subscribed) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(
                    if (subscription.subscribed) R.string.unsubscribe else R.string.subscribe,
                ),
                onClick = { onToggleSubscription?.invoke() },
                haptic = if (subscription.subscribed) Haptic.ToggleOff else Haptic.ToggleOn,
                size = ARTIST_SIDE_BUTTON,
            )
        } else {
            CircleIconButton(
                icon = Icons.Rounded.StarBorder,
                contentDescription = stringResource(R.string.subscribe),
                onClick = {},
                size = ARTIST_SIDE_BUTTON,
                enabled = false,
            )
        }
    }
    Spacer(Modifier.height(bottomSpace))
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
) {
    val haptics = rememberHaptics()
    val content = if (filled) Color.Black else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .height(size)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(Color.White)
                } else {
                    Modifier
                        .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), CircleShape)
                        .liquidGlassRim(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), CircleShape)
                },
            )
            .clickable {
                haptics.play(Haptic.Resume)
                onClick()
            }
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlayCircle(
    onClick: () -> Unit,
    size: Dp,
    iconScale: Float,
    containerColor: Color,
) {
    val haptics = rememberHaptics()
    val description = stringResource(R.string.play)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable {
                haptics.play(Haptic.Resume)
                onClick()
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawCircle(containerColor)
                val box = this.size.minDimension * iconScale
                val unit = box / 24f
                val origin = Offset((this.size.width - box) / 2f, (this.size.height - box) / 2f)
                val triangle = Path().apply {
                    moveTo(origin.x + 6.8f * unit, origin.y + 4.8f * unit)
                    lineTo(origin.x + 19.2f * unit, origin.y + 12f * unit)
                    lineTo(origin.x + 6.8f * unit, origin.y + 19.2f * unit)
                    close()
                }
                drawPath(triangle, Color.Black, blendMode = BlendMode.Clear)
                drawPath(
                    triangle,
                    Color.Black,
                    style = Stroke(
                        width = 2f * unit,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                    blendMode = BlendMode.Clear,
                )
            }
            .semantics { contentDescription = description },
    )
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    haptic: Haptic = Haptic.Tap,
    size: Dp = 50.dp,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else DISABLED_ACTION_ALPHA)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), CircleShape)
            .liquidGlassRim(CircleShape)
            .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), CircleShape)
            .clickable(enabled = enabled) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.44f),
        )
    }
}

@Composable
private fun ArtistStatsRow(
    subscriberCountText: String?,
    monthlyListenerCount: String?,
    palette: ArtworkPalette,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ARTIST_CONTENT_GUTTER, end = ARTIST_CONTENT_GUTTER, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        subscriberCountText?.let {
            StatChip(
                icon = Icons.Rounded.Person,
                text = stringResource(R.string.subscribers, it.substringBefore(' ')),
                palette = palette,
            )
        }
        monthlyListenerCount?.let {
            StatChip(
                icon = Icons.Rounded.GraphicEq,
                text = stringResource(R.string.monthly_listeners, it.substringBefore(' ')),
                palette = palette,
            )
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, text: String, palette: ArtworkPalette) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .lightweightLiquidGlass(
                shape = CircleShape,
                fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.onBackgroundVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onBackgroundVariant,
        )
    }
}

@Composable
private fun SignedByCaption(
    name: String,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val signed = stringResource(R.string.signed_by, name)
    val nameAt = signed.indexOf(name)
    Text(
        text = buildAnnotatedString {
            append(signed)
            if (nameAt >= 0) {
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), nameAt, nameAt + name.length)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = palette.onBackground.copy(alpha = 0.8f),
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun AboutSignature(
    signature: ArtistSignature,
    name: String,
    palette: ArtworkPalette,
    horizontalPadding: Dp,
) {
    val context = LocalContext.current
    val isSvg = signature.format == "svg"
    val aspect = signature.aspect.coerceIn(0.4f, 6f)
    val request = remember(signature.imageUrl) {
        ImageRequest.Builder(context).data(signature.imageUrl).apply {
            if (isSvg) {
                decoderFactory(SvgDecoder.Factory())
                size(SVG_DECODE_WIDTH, (SVG_DECODE_WIDTH / aspect).roundToInt())
            } else {
                size(Size.ORIGINAL)
                transformations(SignatureInkTransformation)
            }
        }.build()
    }
    var loaded by remember(signature.imageUrl) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        horizontalAlignment = Alignment.Start,
    ) {
        AsyncImage(
            model = request,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            colorFilter = if (isSvg) ColorFilter.tint(Color.White) else null,
            onState = { if (it is AsyncImagePainter.State.Success) loaded = true },
            modifier = Modifier
                .padding(top = 20.dp, bottom = 6.dp)
                .height(ABOUT_LOGO_HEIGHT)
                .aspectRatio(aspect),
        )
        if (loaded) SignedByCaption(name = name, palette = palette, textAlign = TextAlign.Start)
    }
}

@Composable
private fun AboutSection(
    title: String,
    text: String?,
    palette: ArtworkPalette,
    horizontalPadding: Dp = ABOUT_GUTTER,
    stats: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var clipped by remember(text) { mutableStateOf(false) }
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            modifier = Modifier.padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 2.dp,
                bottom = 6.dp,
            ),
        )
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = palette.onBackgroundVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { result -> if (!expanded) clipped = result.hasVisualOverflow },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(horizontal = horizontalPadding)
                    .let { m -> if (clipped || expanded) m.clickable { expanded = !expanded } else m },
            )
        }
        if (clipped || expanded) {
            Text(
                text = stringResource(if (expanded) R.string.less else R.string.more),
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding, vertical = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
        if (stats != null) {
            Spacer(Modifier.height(if (text != null) 12.dp else 4.dp))
            stats()
        }
        footer?.invoke()
    }
}

@Composable
private fun SectionHeading(
    title: String,
    palette: ArtworkPalette,
    onShowAll: (() -> Unit)? = null,
    horizontalPadding: Dp = PAGE_GUTTER,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 10.dp,
                bottom = 8.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onShowAll != null) {
            Text(
                text = stringResource(R.string.show_all),
                style = MaterialTheme.typography.titleSmall,
                color = palette.accent,
                modifier = Modifier
                    .clickable(onClick = onShowAll)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactSongRow(
    song: Song,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    downloadedTint: Color? = null,
    downloadedIds: Set<String> = emptySet(),
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.trackArtSize(48.dp, song.isVideo)) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(7.dp))
                    .thumbnailBorder(RoundedCornerShape(7.dp))
                    .background(palette.elevated),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) palette.accent else palette.onBackground,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint, downloadedIds)
        }
        RowMoreButton(onClick = onLongPress, tint = palette.onBackgroundVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedSongRow(
    song: Song,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onAdd: () -> Unit,
    downloadedTint: Color? = null,
    downloadedIds: Set<String> = emptySet(),
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .longPressMenuClickable(
                onClick = onClick,
                onLongClick = onLongPress,
                liftPadding = PaddingValues(horizontal = PAGE_GUTTER),
            )
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.trackArtSize(52.dp, song.isVideo)) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .thumbnailBorder(RoundedCornerShape(8.dp))
                    .background(palette.elevated),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) palette.accent else palette.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onBackgroundVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null) {
            DownloadedBadge(song.videoId, downloadedTint, downloadedIds)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.16f))
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.add_to_playlist),
                tint = palette.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun List<HomeShelf>.topRelease(): ShelfItem? =
    asSequence()
        .flatMap { it.items.asSequence() }
        .filter { it.browseId?.startsWith("MPRE") == true }
        .withIndex()
        .maxWithOrNull(
            compareBy<IndexedValue<ShelfItem>> { it.value.releaseYear() ?: 0 }
                .thenByDescending { it.index },
        )?.value

private val RELEASE_YEAR = Regex("""\b(19|20)\d{2}\b""")

private fun ShelfItem.releaseYear(): Int? =
    RELEASE_YEAR.find(subtitle)?.value?.toIntOrNull()

@StringRes
private fun ShelfItem.recentLabel(): Int {
    val kind = subtitle.lowercase()
    return when {
        kind.contains("single") -> R.string.recent_single
        Regex("""\bep\b""").containsMatchIn(kind) -> R.string.recent_ep
        else -> R.string.recent_album
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopReleaseCard(
    item: ShelfItem,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    saved: Boolean?,
    onToggleSaved: (() -> Unit)?,
) {
    val shape = remember { RoundedCornerShape(TOP_RELEASE_CORNER).continuous() }
    val coverShape = remember { RoundedCornerShape(TOP_RELEASE_CORNER - TOP_RELEASE_INSET).continuous() }
    val haptics = rememberHaptics()
    val hasButton = onToggleSaved != null && saved != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ARTIST_CONTENT_GUTTER)
            .padding(bottom = 22.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), shape)
            .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), shape)
            .clip(shape)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(
                start = TOP_RELEASE_INSET,
                top = TOP_RELEASE_INSET,
                bottom = TOP_RELEASE_INSET,
                end = if (hasButton) 19.dp else TOP_RELEASE_INSET,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(96.dp)
                .clip(coverShape)
                .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_COVER_EDGE_ALPHA), coverShape)
                .background(palette.elevated),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val secondary = palette.onBackground.copy(alpha = TOP_RELEASE_SECONDARY_ALPHA)
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FadingText(
                text = item.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    lineHeight = TOP_RELEASE_TITLE_LINE_HEIGHT,
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                ),
                color = palette.onBackground,
                maxLines = 2,
            )
            Text(
                text = stringResource(item.recentLabel()),
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
                maxLines = 1,
            )
        }
        if (onToggleSaved != null && saved != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = LIGHT_FILL_ALPHA), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = TOP_RELEASE_EDGE_ALPHA), CircleShape)
                    .clickable {
                        haptics.play(if (saved) Haptic.ToggleOff else Haptic.ToggleOn)
                        onToggleSaved()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
                    contentDescription = stringResource(
                        if (saved) R.string.remove_from_library else R.string.add_to_library,
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

private val TOP_RELEASE_CORNER = 24.dp
private val TOP_RELEASE_INSET = 12.dp
private const val TOP_RELEASE_SECONDARY_ALPHA = 0.55f
private val TOP_RELEASE_TITLE_LINE_HEIGHT = 22.sp
private const val LIGHT_FILL_ALPHA = 0.07f
private const val DISABLED_ACTION_ALPHA = 0.38f
private const val TOP_RELEASE_EDGE_ALPHA = 0.10f
private const val TOP_RELEASE_COVER_EDGE_ALPHA = 0.215f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionCard(
    item: ShelfItem,
    palette: ArtworkPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(SHELF_CARD_WIDTH),
    onLongPress: (() -> Unit)? = null,
) {
    if (item.isVideo) {
        VideoShelfCard(
            item = item,
            onClick = onClick,
            onLongPress = onLongPress,
            modifier = modifier,
            titleColor = palette.onBackground,
            subtitleColor = palette.onBackgroundVariant,
            placeholderColor = palette.elevated,
        )
        return
    }
    Column(
        modifier = modifier
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .thumbnailBorder(RoundedCornerShape(10.dp))
                .background(palette.elevated),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onBackgroundVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistShelfGridPage(
    shelf: HomeShelf,
    palette: ArtworkPalette,
    onItemClick: (ShelfItem) -> Unit,
    onItemLongPress: ((ShelfItem) -> Unit)?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    BoxWithConstraints(modifier.fillMaxSize().background(palette.background)) {
        val grid = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(grid.columns),
            state = gridState,
            contentPadding = PaddingValues(
                top = topBarContentPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PAGE_GUTTER),
        ) {
            items(shelf.items, key = { it.browseId ?: it.videoId ?: it.title }) { item ->
                SectionCard(
                    item = item,
                    palette = palette,
                    onClick = { onItemClick(item) },
                    onLongPress = onItemLongPress?.let { { it(item) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DetailPage.headerLines(trackCount: Int, playtime: String? = null): Pair<String, String> {
    val parts = subtitle.split("•", "·").map { it.trim() }.filter { it.isNotEmpty() }
    val year = parts.lastOrNull { it.length == 4 && it.all(Char::isDigit) }
    val kind = parts.firstOrNull { it.lowercase(Locale.ROOT) in KIND_WORDS }
    val credit = parts.filter { it != year && it != kind }.joinToString(", ")
    val meta = listOfNotNull(
        (kind ?: type.localizedLabel())?.capitalizeWords(),
        year,
        trackCount.takeIf { it > 0 }?.let {
            pluralStringResource(R.plurals.track_count_plural, it, it).capitalizeWords()
        },
        playtime,
    ).joinToString(" · ")
    return credit to meta
}

private fun DetailPage.placeholderIcon(): ImageVector = when (type) {
    BrowseType.ARTIST -> Icons.Rounded.Person
    BrowseType.ALBUM -> Icons.Rounded.Album
    BrowseType.PLAYLIST -> Icons.AutoMirrored.Rounded.QueueMusic
    else -> ITubeIcons.MusicNote
}

private fun String.capitalizeWords(): String =
    split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

private val KIND_WORDS = setOf(
    "album", "single", "ep", "playlist", "artist", "podcast", "episode", "song", "video",
)

@Composable
private fun BrowseType.localizedLabel(): String? = when (this) {
    BrowseType.ALBUM -> stringResource(R.string.album)
    BrowseType.PLAYLIST -> stringResource(R.string.playlist)
    BrowseType.ARTIST -> stringResource(R.string.artist)
    BrowseType.OTHER -> null
}

@Composable
private fun List<Song>.playtime(): String? {
    val minutes = sumOf { it.durationMillis() } / 60_000
    return when {
        minutes <= 0 -> null
        minutes < 60 -> stringResource(R.string.minutes_short, minutes.toInt())
        else -> stringResource(R.string.hours_minutes_short, (minutes / 60).toInt(), (minutes % 60).toInt())
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ITubeArtistPreview() {
    ITubeArtistTheme(darkTheme = true) {
        val sampleSongs = (1..6).map { i ->
            Song(
                videoId = "id_$i",
                title = "Midnight Track $i",
                artist = "Sample Artist",
                thumbnailUrl = null,
                durationText = "3:2$i",
                isExplicit = i % 2 == 0,
            )
        }
        ITubeDetailScreen(
            page = DetailPage(
                browseId = "UC_preview",
                title = "Sample Artist",
                subtitle = "Artist",
                thumbnailUrl = null,
                songs = UiState.Success(sampleSongs),
                type = BrowseType.ARTIST,
                subscription = SubscriptionState("UC_preview", false),
                subscriberCountText = "12.4M subscribers",
                monthlyListenerCount = "45.8M monthly listeners",
                description = "Sample editorial artist biography showing how iTube lays out the About section at the bottom.",
            ),
            currentSong = sampleSongs.first(),
            isPlaying = true,
            onSongClick = { _, _ -> },
            onSongLongPress = {},
            onSongSwipe = {},
            onShuffle = {},
            onSectionItemClick = {},
            onArtistClick = { _, _ -> },
            contentPadding = PaddingValues(bottom = 32.dp),
            onToggleSubscription = {},
        )
    }
}
