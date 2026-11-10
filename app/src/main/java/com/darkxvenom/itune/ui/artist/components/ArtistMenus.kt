package com.darkxvenom.itune.ui.artist.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.artist.ArtistRepository
import com.darkxvenom.itune.data.model.BrowseTarget
import com.darkxvenom.itune.data.model.BrowseType
import com.darkxvenom.itune.data.model.LikeStatus
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.SongSort
import com.darkxvenom.itune.ui.artist.icons.SfIcons
import com.darkxvenom.itune.ui.artist.theme.ArtworkPalette

internal enum class ActionRowStyle { Sheet, Menu }

internal val LocalActionRowStyle = staticCompositionLocalOf { ActionRowStyle.Sheet }

@Composable
private fun neutralMenuPalette(): ArtworkPalette {
    val accent = MaterialTheme.colorScheme.primary
    val colors = iosMenuColors()
    return remember(accent, colors) {
        ArtworkPalette(
            background = colors.fill,
            wash = colors.fill,
            elevated = colors.fill,
            accent = accent,
            onBackground = colors.label,
            onBackgroundVariant = colors.secondaryLabel,
            divider = colors.separator,
        )
    }
}

@Composable
fun ArtistSongActionsMenu(
    song: Song,
    likeStatus: LikeStatus,
    downloading: Boolean,
    downloaded: Boolean,
    onToggleLike: () -> Unit,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDownload: () -> Unit,
    onOpenAlbum: (String, String?) -> Unit,
    onOpenArtist: (String, String?) -> Unit,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showingSongInfo by remember(song.videoId) { mutableStateOf(false) }

    val needsLinks = song.videoId.isNotBlank() && (song.albumId == null || song.artistId == null)
    var resolvedLinks by remember(song.videoId) { mutableStateOf<Song?>(null) }
    var resolvingLinks by remember(song.videoId) { mutableStateOf(needsLinks) }

    LaunchedEffect(song.videoId, needsLinks) {
        if (!needsLinks) return@LaunchedEffect
        resolvingLinks = true
        resolvedLinks = ArtistRepository.trackLinks(song.videoId).getOrNull()
        resolvingLinks = false
    }

    val effectiveSong = resolvedLinks?.takeIf { it.videoId == song.videoId }?.let { extra ->
        song.copy(
            artistId = song.artistId ?: extra.artistId,
            albumId = song.albumId ?: extra.albumId,
            albumName = song.albumName ?: extra.albumName,
            artists = song.artists.ifEmpty { extra.artists },
        )
    } ?: song

    if (showingSongInfo) {
        SongInfoDialog(effectiveSong) { showingSongInfo = false }
    }

    val palette = neutralMenuPalette()
    val liked = likeStatus == LikeStatus.LIKE

    CompositionLocalProvider(LocalActionRowStyle provides ActionRowStyle.Menu) {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            IosMenuQuickActions {
                IosMenuQuickAction(
                    icon = if (liked) SfIcons.StarFill else SfIcons.Star,
                    label = stringResource(R.string.like),
                    tint = if (liked) palette.accent else null,
                    onClick = onToggleLike,
                )
                onShare?.let {
                    IosMenuQuickAction(
                        icon = SfIcons.Share,
                        label = stringResource(R.string.share),
                        onClick = it,
                    )
                }
            }

            IosMenuSeparator()
            ActionRow(
                icon = SfIcons.Radio,
                label = stringResource(R.string.start_radio),
                accent = palette.accent,
                onClick = onStartRadio,
            )
            ActionRow(
                icon = SfIcons.PlayNext,
                label = stringResource(R.string.play_next),
                accent = palette.accent,
                onClick = onPlayNext,
            )
            ActionRow(
                icon = SfIcons.PlayLast,
                label = stringResource(R.string.add_to_queue),
                accent = palette.accent,
                onClick = onAddToQueue,
            )
            onAddToPlaylist?.let {
                ActionRow(
                    icon = SfIcons.AddToPlaylist,
                    label = stringResource(R.string.add_to_playlist),
                    accent = palette.accent,
                    onClick = it,
                )
            }
            onRemoveFromPlaylist?.let {
                ActionRow(
                    icon = SfIcons.Trash,
                    label = "Remove from Playlist",
                    tint = Color(0xFFFF2D55),
                    onClick = it,
                )
            }
            ActionRow(
                icon = if (downloaded) SfIcons.Downloaded else SfIcons.Download,
                label = stringResource(R.string.download),
                value = when {
                    downloading -> stringResource(R.string.downloading)
                    downloaded -> stringResource(R.string.downloaded)
                    else -> null
                },
                accent = palette.accent,
                onClick = onDownload,
            )

            IosMenuSeparator()
            when (val id = effectiveSong.albumId) {
                null -> if (resolvingLinks) {
                    LoadingActionRow(SfIcons.Album, stringResource(R.string.open_album), palette)
                }
                else -> ActionRow(
                    icon = SfIcons.Album,
                    label = stringResource(R.string.open_album),
                    accent = palette.accent,
                    description = effectiveSong.albumName,
                    onClick = { onOpenAlbum(id, effectiveSong.albumName) },
                )
            }
            val artistCredits = effectiveSong.artists.filter { !it.browseId.isNullOrBlank() }
            when {
                artistCredits.size > 1 -> artistCredits.forEach { credit ->
                    ActionRow(
                        icon = SfIcons.Artist,
                        label = stringResource(R.string.open_artist),
                        accent = palette.accent,
                        description = credit.name,
                        onClick = { onOpenArtist(credit.browseId!!, credit.name) },
                    )
                }
                effectiveSong.artistId != null -> ActionRow(
                    icon = SfIcons.Artist,
                    label = stringResource(R.string.open_artist),
                    accent = palette.accent,
                    description = effectiveSong.artist.takeIf { it.isNotBlank() },
                    onClick = { onOpenArtist(effectiveSong.artistId, effectiveSong.artist) },
                )
                resolvingLinks -> LoadingActionRow(SfIcons.Artist, stringResource(R.string.open_artist), palette)
            }
            ActionRow(
                icon = SfIcons.Info,
                label = stringResource(R.string.song_info),
                accent = palette.accent,
                onClick = { showingSongInfo = true },
            )
        }
    }
}

@Composable
fun ArtistBrowseActionsMenu(
    target: BrowseTarget,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalActionRowStyle provides ActionRowStyle.Menu) {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            onPlay?.let { ActionRow(SfIcons.Play, stringResource(R.string.play), onClick = it) }
            onShuffle?.let { ActionRow(SfIcons.Shuffle, stringResource(R.string.shuffle), onClick = it) }
            onPlayNext?.let {
                ActionRow(SfIcons.PlayNext, stringResource(R.string.play_next), onClick = it)
            }
            onAddToQueue?.let {
                ActionRow(SfIcons.PlayLast, stringResource(R.string.add_to_queue), onClick = it)
            }
            onDownloadAll?.let {
                ActionRow(SfIcons.Download, stringResource(R.string.download_all), onClick = it)
            }
            onOpen?.let {
                ActionRow(SfIcons.Open, target.type.localizedOpenLabel(), onClick = it)
            }
            onShare?.let {
                ActionRow(SfIcons.Share, stringResource(R.string.share), onClick = it)
            }
        }
    }
}

@Composable
fun ArtistTopBarActionsMenu(
    artistName: String,
    subscribed: Boolean,
    hasRadio: Boolean,
    hasShuffle: Boolean,
    onPlayTopSongs: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = neutralMenuPalette()
    CompositionLocalProvider(LocalActionRowStyle provides ActionRowStyle.Menu) {
        Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            IosMenuQuickActions {
                IosMenuQuickAction(
                    icon = SfIcons.Play,
                    label = stringResource(R.string.play),
                    onClick = onPlayTopSongs,
                )
                if (hasShuffle) {
                    IosMenuQuickAction(
                        icon = SfIcons.Shuffle,
                        label = stringResource(R.string.shuffle),
                        onClick = onShuffle,
                    )
                }
                IosMenuQuickAction(
                    icon = SfIcons.Share,
                    label = stringResource(R.string.share),
                    onClick = onShare,
                )
            }
            IosMenuSeparator()
            if (hasRadio) {
                ActionRow(
                    icon = SfIcons.Radio,
                    label = stringResource(R.string.start_radio),
                    description = artistName,
                    accent = palette.accent,
                    onClick = onStartRadio,
                )
            }
            ActionRow(
                icon = if (subscribed) SfIcons.StarFill else SfIcons.Star,
                label = stringResource(if (subscribed) R.string.unsubscribe else R.string.subscribe),
                tint = if (subscribed) palette.accent else null,
                accent = palette.accent,
                onClick = onToggleSubscribe,
            )
        }
    }
}

@Composable
fun ReleaseSortMenuContent(
    current: SongSort,
    onSelect: (SongSort) -> Unit,
) {
    val options = listOf(
        SongSort.DEFAULT to "Default",
        SongSort.TITLE_ASC to "Title (A–Z)",
        SongSort.TITLE_DESC to "Title (Z–A)",
    )
    options.forEach { (option, label) ->
        IosMenuRow(
            label = label,
            icon = if (option == current) SfIcons.Check else null,
            onClick = { onSelect(option) },
        )
    }
}

@Composable
private fun BrowseType.localizedOpenLabel(): String = when (this) {
    BrowseType.ALBUM -> stringResource(R.string.open_album)
    BrowseType.ARTIST -> stringResource(R.string.open_artist)
    BrowseType.PLAYLIST -> stringResource(R.string.open_playlist)
    BrowseType.OTHER -> stringResource(R.string.open)
}

@Composable
internal fun ActionRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    tint: Color? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    description: String? = null,
    onClick: () -> Unit,
) {
    if (LocalActionRowStyle.current == ActionRowStyle.Menu) {
        IosMenuRow(
            label = label,
            icon = icon,
            onClick = onClick,
            description = description,
            value = value,
            iconTint = tint,
            valueColor = accent,
            enabled = enabled,
        )
        return
    }
    val foreground = MaterialTheme.colorScheme.onBackground
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = (tint ?: foreground).copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = foreground.copy(alpha = alpha),
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = accent.copy(alpha = alpha),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LoadingActionRow(icon: ImageVector, label: String, palette: ArtworkPalette) {
    IosMenuRow(label = label, icon = icon, onClick = null, enabled = false) {
        CircularProgressIndicator(
            color = palette.onBackgroundVariant,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
    }
}
