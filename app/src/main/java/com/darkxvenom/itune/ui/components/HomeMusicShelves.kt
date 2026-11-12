package com.darkxvenom.itune.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.backend.MusicPlaylistRef
import com.darkxvenom.itune.backend.MusicTrack
import com.darkxvenom.itune.backend.NewReleaseAlbum
import com.darkxvenom.itune.backend.highQualityThumbnail
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

@Composable
fun HomeTrackShelf(
    title: String,
    subtitle: String = "",
    tracks: List<MusicTrack>,
    onTrackClick: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tracks.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, color = AppleTextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = AppleTextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(tracks, key = { "home_track_${it.id}" }) { track ->
                Column(
                    Modifier.width(142.dp).clickable { onTrackClick(track) }
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl?.highQualityThumbnail() ?: track.thumbnailUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(142.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF202024)),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(track.title, color = AppleTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artists, color = AppleTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun HomeCollectionShelf(
    title: String,
    subtitle: String = "",
    items: List<MusicPlaylistRef>,
    onItemClick: (MusicPlaylistRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(title, color = AppleTextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = AppleTextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { "home_collection_${it.id}" }) { item ->
                Column(Modifier.width(150.dp).clickable { onItemClick(item) }) {
                    AsyncImage(
                        model = item.thumbnailUrl?.highQualityThumbnail() ?: item.thumbnailUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(150.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF202024)),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(item.title, color = AppleTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.author.ifBlank { if (item.isAlbum) "Album" else "YouTube Music" }, color = AppleTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun HomeAlbumShelf(
    title: String,
    albums: List<NewReleaseAlbum>,
    onAlbumClick: (NewReleaseAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.padding(horizontal = 20.dp), color = AppleTextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(albums, key = { "home_album_${it.browseId}" }) { album ->
                Column(Modifier.width(150.dp).clickable { onAlbumClick(album) }) {
                    AsyncImage(
                        model = album.thumbnailUrl?.highQualityThumbnail() ?: album.thumbnailUrl,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(150.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF202024)),
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(album.title, color = AppleTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(album.subtitleLine, color = AppleTextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
