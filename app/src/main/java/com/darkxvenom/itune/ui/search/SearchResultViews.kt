package com.darkxvenom.itune.ui.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicArtist
import com.darkxvenom.itune.backend.AudioDownloader
import com.darkxvenom.itune.backend.MusicBackend
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.data.artist.ArtistStateStore
import com.darkxvenom.itune.data.library.SavedSongMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.ui.artist.components.ExplicitBadge
import com.darkxvenom.itune.ui.components.AnimatedEqualizer
import com.darkxvenom.itune.ui.library.AppleSheetAddToPlaylistContent
import com.darkxvenom.itune.ui.library.AppleSheetCreatePlaylistContent
import com.darkxvenom.itune.ui.library.PendingPlaylistPayload
import com.darkxvenom.itune.ui.library.PopupPlaylistStep
import com.darkxvenom.itune.ui.player.AppleFrostedPopupOverlay
import com.darkxvenom.itune.ui.player.HeaderCircleActionButton
import com.darkxvenom.itune.ui.player.MorePopupVectorOptionRow
import com.darkxvenom.itune.ui.player.PopupMenuDivider
import com.darkxvenom.itune.ui.theme.AppleTextPrimary

internal sealed interface SearchMoreTarget {
    data class Song(val track: MusicTrack) : SearchMoreTarget
    data class Artist(val artist: MusicArtist) : SearchMoreTarget
    data class Collection(val item: MusicPlaylistRef) : SearchMoreTarget
}

internal data class SearchMoreRequest(
    val target: SearchMoreTarget,
    val anchorCenterWindow: Offset = Offset.Zero
)

internal data class SearchDetailState(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val initialType: BrowseType = BrowseType.OTHER,
)

@Composable
internal fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        fontSize = 15.5.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White.copy(alpha = 0.88f),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

/**
 * Unified compact flat row used across Songs, Artists, Albums, and Playlists
 * matching the target dark list design with thumbnail, title, subtitle, divider, and 3-dot icon.
 */
@Composable
internal fun SearchUnifiedRow(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    cacheKey: String,
    onClick: () -> Unit,
    onMoreClick: (anchorCenterWindow: Offset) -> Unit,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isExplicit: Boolean = false,
    showBottomDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var moreBtnCenter by remember { mutableStateOf(Offset.Zero) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF1C1C1E))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbnailUrl?.highQualityThumbnail() ?: thumbnailUrl)
                        .memoryCacheKey(cacheKey)
                        .crossfade(180)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedEqualizer(
                            isPlaying = isPlaying,
                            color = Color.White,
                            size = 20.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExplicit) {
                        ExplicitBadge(
                            color = if (isCurrent) Color(0xFFFA2D48) else Color(0xFF9E9EA6),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = title,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isCurrent) Color(0xFFFA2D48) else AppleTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF8E8E93),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { onMoreClick(moreBtnCenter) },
                modifier = Modifier
                    .size(40.dp)
                    .onGloballyPositioned { coords ->
                        if (coords.isAttached) {
                            val pos = coords.positionInWindow()
                            moreBtnCenter = Offset(
                                x = pos.x + coords.size.width / 2f,
                                y = pos.y + coords.size.height / 2f
                            )
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (showBottomDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 78.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.11f)
            )
        }
    }
}

/**
 * Apple frosted liquid-glass popup for Search result 3-dot actions matching the Player More popup
 * with water-drop spring bloom, concentric ripples, blurred artwork card, top circular action icons,
 * and existing row actions.
 */
@Composable
internal fun AppleSearchMorePopup(
    request: SearchMoreRequest?,
    onOpenDetail: (SearchDetailState) -> Unit,
    onPlayCollection: (browseId: String, title: String, subtitle: String, thumb: String?) -> Unit,
    onQueueCollection: (browseId: String, title: String, subtitle: String, thumb: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var displayedRequest by remember { mutableStateOf<SearchMoreRequest?>(null) }
    var popupVisible by remember { mutableStateOf(false) }
    var dialogRootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(request) {
        if (request != null) {
            displayedRequest = request
            popupVisible = true
        } else if (displayedRequest != null) {
            popupVisible = false
        }
    }

    val activeRequest = displayedRequest ?: return
    val target = activeRequest.target

    fun closeSmoothly() {
        popupVisible = false
    }

    val (title, subtitle, thumbUrl, shareUrl) = when (target) {
        is SearchMoreTarget.Song -> listOf(
            target.track.title,
            target.track.artists,
            target.track.thumbnailUrl,
            "https://music.youtube.com/watch?v=${target.track.id}"
        )
        is SearchMoreTarget.Artist -> listOf(
            target.artist.name,
            target.artist.subscribers ?: "Artist",
            target.artist.thumbnailUrl,
            "https://music.youtube.com/channel/${target.artist.id}"
        )
        is SearchMoreTarget.Collection -> listOf(
            target.item.title,
            target.item.author,
            target.item.thumbnailUrl,
            "https://music.youtube.com/playlist?list=${target.item.watchPlaylistId ?: target.item.id.removePrefix("VL")}"
        )
    }
    val safeTitle = title as String
    val safeSubtitle = subtitle as String
    val artUrl = (thumbUrl as String?)?.highQualityThumbnail() ?: thumbUrl
    val safeShareUrl = shareUrl as String
    var popupStep by remember(activeRequest) { mutableStateOf(PopupPlaylistStep.MENU) }
    var startingDownload by remember(activeRequest) { mutableStateOf(false) }
    val playlistPayload: PendingPlaylistPayload? = remember(target, artUrl) {
        if (target is SearchMoreTarget.Song) {
            PendingPlaylistPayload(
                title = target.track.title,
                subtitle = target.track.artists,
                artworkUrl = artUrl,
                staticSongs = listOf(SavedSongMetadata.fromTrack(target.track)),
            )
        } else {
            null
        }
    }

    Dialog(
        onDismissRequest = { closeSmoothly() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { win ->
                win.setDimAmount(0f)
                win.setWindowAnimations(0)
                win.setBackgroundDrawableResource(android.R.color.transparent)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
                @Suppress("DEPRECATION")
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords -> dialogRootCoords = coords }
        ) {
            val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val rootWindowOrigin = dialogRootCoords?.let { coords ->
                if (coords.isAttached) coords.positionInWindow() else Offset.Zero
            } ?: Offset.Zero
            val anchorLocalX = (activeRequest.anchorCenterWindow.x - rootWindowOrigin.x).takeIf {
                activeRequest.anchorCenterWindow != Offset.Zero
            } ?: 0f
            val anchorLocalY = (activeRequest.anchorCenterWindow.y - rootWindowOrigin.y).takeIf {
                activeRequest.anchorCenterWindow != Offset.Zero
            } ?: 0f

            AppleFrostedPopupOverlay(
                visible = popupVisible,
                title = when (popupStep) {
                    PopupPlaylistStep.ADD_TO_PLAYLIST -> "Add to Playlist"
                    PopupPlaylistStep.CREATE_PLAYLIST -> "New Playlist"
                    else -> safeTitle
                },
                subtitle = when (popupStep) {
                    PopupPlaylistStep.ADD_TO_PLAYLIST, PopupPlaylistStep.CREATE_PLAYLIST -> safeTitle
                    else -> safeSubtitle
                },
                artUrl = artUrl,
                anchorCenterXPx = anchorLocalX,
                anchorCenterYPx = anchorLocalY,
                maxHeightDp = maxHeight,
                statusBarTopDp = statusBarTopDp,
                rootCoordsProvider = { dialogRootCoords },
                hasInputFocusPage = popupStep == PopupPlaylistStep.CREATE_PLAYLIST,
                onDismiss = { closeSmoothly() },
                onBack = {
                    when (popupStep) {
                        PopupPlaylistStep.CREATE_PLAYLIST -> popupStep = PopupPlaylistStep.ADD_TO_PLAYLIST
                        PopupPlaylistStep.ADD_TO_PLAYLIST -> popupStep = PopupPlaylistStep.MENU
                        else -> closeSmoothly()
                    }
                },
                onExited = {
                    displayedRequest = null
                    popupStep = PopupPlaylistStep.MENU
                    onDismiss()
                },
                headerActions = {
                    if (popupStep == PopupPlaylistStep.MENU) {
                        HeaderCircleActionButton(
                            iconRes = R.drawable.ic_menu_share,
                            contentDescription = "Share",
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "$safeTitle\n$safeShareUrl")
                                }
                                context.startActivity(Intent.createChooser(intent, "Share"))
                                closeSmoothly()
                            }
                        )
                        HeaderCircleActionButton(
                            iconRes = R.drawable.ic_menu_copy_link,
                            contentDescription = "Copy link",
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Link", safeShareUrl))
                                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                closeSmoothly()
                            }
                        )
                        if (target is SearchMoreTarget.Song) {
                            val isFav = MusicPlayerState.isFavorite(target.track.id)
                            HeaderCircleActionButton(
                                iconRes = if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                                contentDescription = if (isFav) "Favorited" else "Favorite",
                                highlighted = isFav,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    MusicPlayerState.toggleFavorite(target.track)
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(33.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    popupStep = if (popupStep == PopupPlaylistStep.CREATE_PLAYLIST) {
                                        PopupPlaylistStep.ADD_TO_PLAYLIST
                                    } else {
                                        PopupPlaylistStep.MENU
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (popupStep == PopupPlaylistStep.ADD_TO_PLAYLIST) {
                            Box(
                                modifier = Modifier
                                    .size(33.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFA2D48).copy(alpha = 0.28f))
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        popupStep = PopupPlaylistStep.CREATE_PLAYLIST
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "New Playlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }
            ) {
                AnimatedContent(
                    targetState = popupStep,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val dir = if (forward) 1 else -1
                        (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { dir * it / 5 }) togetherWith
                            (fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -dir * it / 5 })
                    },
                    label = "search_popup_playlist_step",
                ) { activeStep ->
                    when (activeStep) {
                        PopupPlaylistStep.ADD_TO_PLAYLIST -> {
                            playlistPayload?.let { payload ->
                                AppleSheetAddToPlaylistContent(
                                    payload = payload,
                                    onCreateNewClick = { popupStep = PopupPlaylistStep.CREATE_PLAYLIST },
                                    onCompleted = { closeSmoothly() },
                                )
                            }
                        }
                        PopupPlaylistStep.CREATE_PLAYLIST -> {
                            playlistPayload?.let { payload ->
                                AppleSheetCreatePlaylistContent(
                                    payload = payload,
                                    onCompleted = { closeSmoothly() },
                                )
                            }
                        }
                        else -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                when (target) {
                                    is SearchMoreTarget.Song -> {
                                        val isFav = MusicPlayerState.isFavorite(target.track.id)
                                        MorePopupVectorOptionRow(
                                            icon = Icons.Rounded.PlayArrow,
                                            title = "Play Next",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                MusicPlayerState.playNext(target.track)
                                                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                                                closeSmoothly()
                                            }
                                        )
                                        PopupMenuDivider()
                                        MorePopupVectorOptionRow(
                                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                            title = "Add to Queue",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                MusicPlayerState.addToQueue(listOf(target.track))
                                                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                                closeSmoothly()
                                            }
                                        )
                                        PopupMenuDivider()
                                        MorePopupVectorOptionRow(
                                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                            title = "Add to Playlist",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                popupStep = PopupPlaylistStep.ADD_TO_PLAYLIST
                                            }
                                        )
                                        PopupMenuDivider()
                                        MorePopupVectorOptionRow(
                                            icon = Icons.Rounded.Download,
                                            title = if (startingDownload) "Preparing Download…" else "Download",
                                            onClick = {
                                                if (startingDownload) return@MorePopupVectorOptionRow
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                startingDownload = true
                                                coroutineScope.launch {
                                                    val result = runCatching {
                                                        val options = withContext(Dispatchers.IO) {
                                                            MusicBackend.extractAudioQualities(target.track.id)
                                                        }
                                                        val selected = MusicBackend.selectDownloadOption(options)
                                                            ?: error("No downloadable audio stream found")
                                                        AudioDownloader.download(context, target.track, selected)
                                                    }
                                                    if (result.isSuccess) {
                                                        Toast.makeText(context, "Download started · Check notifications", Toast.LENGTH_SHORT).show()
                                                        closeSmoothly()
                                                    } else {
                                                        startingDownload = false
                                                        Toast.makeText(context, result.exceptionOrNull()?.message ?: "Download failed", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        )
                                        PopupMenuDivider()
                                        MorePopupVectorOptionRow(
                                            icon = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            title = if (isFav) "Remove from Favorites" else "Favorite",
                                            iconTint = if (isFav) Color(0xFFFA2D48) else Color.White.copy(alpha = 0.92f),
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                MusicPlayerState.toggleFavorite(target.track)
                                                closeSmoothly()
                                            }
                                        )
                                    }

                                    is SearchMoreTarget.Artist -> {
                                        MorePopupVectorOptionRow(
                                            icon = Icons.Rounded.PlayArrow,
                                            title = "Play Top Songs",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                closeSmoothly()
                                                onPlayCollection(
                                                    target.artist.id,
                                                    target.artist.name,
                                                    target.artist.subscribers ?: "Artist",
                                                    target.artist.thumbnailUrl
                                                )
                                            }
                                        )
                                        PopupMenuDivider()
                                        MorePopupVectorOptionRow(
                                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                            title = "Open Artist",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                closeSmoothly()
                                                onOpenDetail(
                                                    SearchDetailState(
                                                        browseId = target.artist.id,
                                                        title = target.artist.name,
                                                        subtitle = target.artist.subscribers ?: "Artist",
                                                        thumbnailUrl = target.artist.thumbnailUrl,
                                                        initialType = BrowseType.ARTIST
                                                    )
                                                )
                                            }
                                        )
                                        PopupMenuDivider()
                                        val subscribedArtists by ArtistStateStore.subscribedArtists.collectAsState()
                                        val isSub = target.artist.id in subscribedArtists
                                        MorePopupVectorOptionRow(
                                            icon = if (isSub) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                            title = if (isSub) "Remove from Favorite Artists" else "Add to Favorite Artists",
                                            iconTint = if (isSub) Color(0xFFFA2D48) else Color.White.copy(alpha = 0.92f),
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                ArtistStateStore.toggleSubscription(
                                                    context = context,
                                                    browseId = target.artist.id,
                                                    title = target.artist.name,
                                                    subtitle = target.artist.subscribers ?: "Artist",
                                                    thumbnailUrl = target.artist.thumbnailUrl,
                                                )
                                                Toast.makeText(
                                                    context,
                                                    if (isSub) "Removed from Artists" else "Added to Artists",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                closeSmoothly()
                                            }
                                        )
                                    }

                                    is SearchMoreTarget.Collection -> {
                                        MorePopupVectorOptionRow(
                                            icon = Icons.Rounded.PlayArrow,
                                            title = if (target.item.isAlbum) "Play Album" else "Play Playlist",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                closeSmoothly()
                                                onPlayCollection(
                                                    target.item.id,
                                                    target.item.title,
                                                    target.item.author,
                                                    target.item.thumbnailUrl
                                                )
                                            }
                                        )
                                        PopupMenuDivider()
                                        MorePopupVectorOptionRow(
                                            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                            title = "Add to Queue",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                closeSmoothly()
                                                onQueueCollection(
                                                    target.item.id,
                                                    target.item.title,
                                                    target.item.author,
                                                    target.item.thumbnailUrl
                                                )
                                            }
                                        )
                                        PopupMenuDivider()
                                        val savedReleases by ArtistStateStore.savedReleases.collectAsState()
                                        val isSaved = target.item.id in savedReleases
                                        MorePopupVectorOptionRow(
                                            icon = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add,
                                            title = if (isSaved) "Remove from Library" else "Add to Library",
                                            iconTint = if (isSaved) Color(0xFFFA2D48) else Color.White.copy(alpha = 0.92f),
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                ArtistStateStore.toggleReleaseSaved(
                                                    context = context,
                                                    browseId = target.item.id,
                                                    title = target.item.title,
                                                    subtitle = target.item.author,
                                                    thumbnailUrl = target.item.thumbnailUrl,
                                                    type = if (target.item.isAlbum) BrowseType.ALBUM else BrowseType.PLAYLIST,
                                                    isExplicit = target.item.isExplicit,
                                                    playlistId = target.item.watchPlaylistId,
                                                )
                                                Toast.makeText(
                                                    context,
                                                    if (isSaved) "Removed from Library" else "Added to Library",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                closeSmoothly()
                                            }
                                        )
                                        PopupMenuDivider()
                                        MorePopupVectorOptionRow(
                                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                            title = if (target.item.isAlbum) "Open Album" else "Open Playlist",
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                closeSmoothly()
                                                onOpenDetail(
                                                    SearchDetailState(
                                                        browseId = target.item.id,
                                                        title = target.item.title,
                                                        subtitle = target.item.author,
                                                        thumbnailUrl = target.item.thumbnailUrl,
                                                        initialType = if (target.item.isAlbum) BrowseType.ALBUM else BrowseType.PLAYLIST
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
