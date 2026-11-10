package com.darkxvenom.itune.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.library.LibraryDestination
import com.darkxvenom.itune.data.library.LibraryStore
import com.darkxvenom.itune.data.library.SavedCollectionEntry
import com.darkxvenom.itune.data.library.UserPlaylist
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.CARD_ART_PX
import com.darkxvenom.itune.data.model.DetailPage
import com.darkxvenom.itune.data.model.LibraryState
import com.darkxvenom.itune.data.model.ROW_ART_PX
import com.darkxvenom.itune.data.model.UiState
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.data.model.toArtistSong
import com.darkxvenom.itune.data.model.toMusicTrack
import com.darkxvenom.itune.ui.artist.ITubeDetailScreen
import com.darkxvenom.itune.ui.artist.components.BottomFadeScrim
import com.darkxvenom.itune.ui.artist.components.CoverPlaceholder
import com.darkxvenom.itune.ui.artist.components.FrostedTopBar
import com.darkxvenom.itune.ui.artist.components.FrostedTopBarTitlePill
import com.darkxvenom.itune.ui.artist.components.LocalAppBackdrop
import com.darkxvenom.itune.ui.artist.components.LocalLiquidGlassEnabled
import com.darkxvenom.itune.ui.artist.components.LocalReduceDynamicBlur
import com.darkxvenom.itune.ui.artist.components.SongRow
import com.darkxvenom.itune.ui.artist.components.TopFadeBlur
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.layerBackdrop
import com.darkxvenom.itune.ui.artist.components.backdrop.backdrops.snapshotBackdropSource
import com.darkxvenom.itune.ui.artist.components.isGlassSupported
import com.darkxvenom.itune.ui.artist.components.lightweightLiquidGlass
import com.darkxvenom.itune.ui.artist.components.longPressMenuClickable
import com.darkxvenom.itune.ui.artist.components.menuAnchor
import com.darkxvenom.itune.ui.artist.components.topBarContentPadding
import com.darkxvenom.itune.ui.artist.icons.ITubeIcons
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.artist.theme.ITubeArtistTheme
import com.darkxvenom.itune.ui.artist.theme.rememberArtworkPalette
import dev.chrisbanes.haze.hazeSource

@Composable
fun LibraryScreenOverlayContent(
    controller: LibraryHostController,
    hasMiniPlayer: Boolean,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destination = controller.currentDestination ?: return

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 14 })
            },
            label = "library_destination_transition",
        ) { target ->
            when (target) {
                LibraryDestination.LIKED_SONGS -> LikedSongsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.PLAYLISTS -> LibraryPlaylistsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.ARTISTS -> LibraryArtistsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.ALBUMS -> LibraryAlbumsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.SONGS -> LibrarySongsScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.USER_PLAYLIST -> UserPlaylistDetailScreen(
                    playlistId = controller.activeUserPlaylistId,
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                    onExploreClick = onExploreClick,
                )
                LibraryDestination.NEW_RELEASES -> AppleNewReleasesScreen(
                    controller = controller,
                    hasMiniPlayer = hasMiniPlayer,
                    onBack = { controller.pop() },
                )
            }
        }
    }
}

