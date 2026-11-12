package com.darkxvenom.itune.ui.timetravel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.darkxvenom.itune.data.timetravel.TimeTravelStats

/** Compact, artwork-led cards matching BitChord's Replay page rhythm. */
@Composable
fun TimeTravelCardsCarousel(
    stats: TimeTravelStats,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val card = when (page) {
                0 -> TimeTravelCardData(
                    eyebrow = "TOP SONG",
                    title = stats.topSongs.firstOrNull()?.title ?: "No plays yet",
                    subtitle = stats.topSongs.firstOrNull()?.artist ?: "Start listening to build your history",
                    artwork = stats.topSongs.firstOrNull()?.artworkUrl,
                    detail = "${stats.topSongs.firstOrNull()?.playCount ?: 0} plays",
                    icon = Icons.Rounded.MusicNote,
                    circular = false,
                )
                1 -> TimeTravelCardData(
                    eyebrow = "TOP ARTIST",
                    title = stats.topArtists.firstOrNull()?.artistName ?: "No artist yet",
                    subtitle = "${stats.topArtists.firstOrNull()?.playCount ?: 0} plays",
                    artwork = stats.topArtists.firstOrNull()?.artworkUrl,
                    detail = formatDuration(stats.topArtists.firstOrNull()?.durationSec ?: 0L),
                    icon = Icons.Rounded.Mic,
                    circular = true,
                )
                2 -> TimeTravelCardData(
                    eyebrow = "TOP ALBUM",
                    title = stats.topAlbums.firstOrNull()?.albumName ?: "No album yet",
                    subtitle = stats.topAlbums.firstOrNull()?.artistName ?: "Discover a full project",
                    artwork = stats.topAlbums.firstOrNull()?.artworkUrl,
                    detail = "${stats.topAlbums.firstOrNull()?.playCount ?: 0} plays",
                    icon = Icons.Rounded.Album,
                    circular = false,
                )
                else -> TimeTravelCardData(
                    eyebrow = "LISTENING TIME",
                    title = stats.formattedListeningTime,
                    subtitle = "Your time in music",
                    artwork = stats.topPlaylists.firstOrNull()?.artworkUrl,
                    detail = "${stats.totalPlays} plays",
                    icon = Icons.Rounded.QueueMusic,
                    circular = false,
                )
            }
            TimeTravelCard(card = card, displayName = displayName)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (pagerState.currentPage == index) 20.dp else 6.dp, 4.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = 0.28f),
                        ),
                )
            }
        }
    }
}

private data class TimeTravelCardData(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val artwork: String?,
    val detail: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val circular: Boolean,
)

@Composable
private fun TimeTravelCard(card: TimeTravelCardData, displayName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A1A1D).copy(alpha = 0.94f))
            .border(0.8.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = card.eyebrow,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                )
                Icon(card.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(if (card.circular) CircleShape else RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!card.artwork.isNullOrBlank()) {
                        AsyncImage(
                            model = card.artwork,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(card.icon, contentDescription = null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(card.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(card.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("TIME TRAVEL", color = Color.White.copy(alpha = 0.38f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(displayName, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(card.detail, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
        }
    }
}

private fun formatDuration(sec: Long): String {
    if (sec <= 0) return "0m"
    val hours = sec / 3600
    val minutes = (sec % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
