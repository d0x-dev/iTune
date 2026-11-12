package com.darkxvenom.itune.ui.timetravel

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.data.timetravel.TimeTravelStats

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
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> ReplayCreditCard(
                    categoryTitle = "MOST PLAYED SONG",
                    categoryIcon = Icons.Rounded.MusicNote,
                    gradientColors = listOf(Color(0xFFE52D3D), Color(0xFF380812), Color(0xFF140306)),
                    accentColor = Color(0xFFFF525E),
                    mainTitle = stats.topSongs.firstOrNull()?.title ?: "No plays yet",
                    subTitle = stats.topSongs.firstOrNull()?.artist ?: "Start listening to songs",
                    artworkUrl = stats.topSongs.firstOrNull()?.artworkUrl,
                    displayName = displayName,
                    badgeText1 = "${stats.topSongs.firstOrNull()?.playCount ?: 0} PLAYS",
                    badgeText2 = formatDuration(stats.topSongs.firstOrNull()?.durationSec ?: 0L),
                )
                1 -> ReplayCreditCard(
                    categoryTitle = "MOST PLAYED ARTIST",
                    categoryIcon = Icons.Rounded.Mic,
                    gradientColors = listOf(Color(0xFF8B2CF5), Color(0xFF28074D), Color(0xFF100124)),
                    accentColor = Color(0xFFB388FF),
                    mainTitle = stats.topArtists.firstOrNull()?.artistName ?: "No artist yet",
                    subTitle = "${stats.topArtists.firstOrNull()?.playCount ?: 0} tracks enjoyed",
                    artworkUrl = stats.topArtists.firstOrNull()?.artworkUrl,
                    displayName = displayName,
                    badgeText1 = "${stats.topArtists.firstOrNull()?.playCount ?: 0} PLAYS",
                    badgeText2 = formatDuration(stats.topArtists.firstOrNull()?.durationSec ?: 0L),
                    isCircularArt = true,
                )
                2 -> ReplayCreditCard(
                    categoryTitle = "MOST PLAYED ALBUM",
                    categoryIcon = Icons.Rounded.Album,
                    gradientColors = listOf(Color(0xFF0072F5), Color(0xFF051C47), Color(0xFF02091C)),
                    accentColor = Color(0xFF52A5FF),
                    mainTitle = stats.topAlbums.firstOrNull()?.albumName ?: "No album yet",
                    subTitle = stats.topAlbums.firstOrNull()?.artistName ?: "Discover full albums",
                    artworkUrl = stats.topAlbums.firstOrNull()?.artworkUrl,
                    displayName = displayName,
                    badgeText1 = "${stats.topAlbums.firstOrNull()?.playCount ?: 0} PLAYS",
                    badgeText2 = formatDuration(stats.topAlbums.firstOrNull()?.durationSec ?: 0L),
                )
                else -> ReplayCreditCard(
                    categoryTitle = "MOST PLAYED PLAYLIST",
                    categoryIcon = Icons.Rounded.QueueMusic,
                    gradientColors = listOf(Color(0xFF05C77E), Color(0xFF043322), Color(0xFF01140D)),
                    accentColor = Color(0xFF2BFFAB),
                    mainTitle = stats.topPlaylists.firstOrNull()?.playlistName ?: "Recently Played",
                    subTitle = "Curated Favorites",
                    artworkUrl = stats.topPlaylists.firstOrNull()?.artworkUrl,
                    displayName = displayName,
                    badgeText1 = "${stats.topPlaylists.firstOrNull()?.playCount ?: stats.totalPlays} PLAYS",
                    badgeText2 = stats.formattedListeningTime,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Pager indicator dots with animated expansion
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { i ->
                val isSelected = pagerState.currentPage == i
                val dotWidth by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 6.dp,
                    animationSpec = tween(250),
                    label = "pagerDotWidth",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun ReplayCreditCard(
    categoryTitle: String,
    categoryIcon: ImageVector,
    gradientColors: List<Color>,
    accentColor: Color,
    mainTitle: String,
    subTitle: String,
    artworkUrl: String?,
    displayName: String,
    badgeText1: String,
    badgeText2: String,
    isCircularArt: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(205.dp)
            .shadow(18.dp, RoundedCornerShape(24.dp), spotColor = gradientColors.first().copy(alpha = 0.5f))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Row: Category chip & Metallic Chip Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = categoryTitle,
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }

                // Credit Card Microchip Hologram representation
                MetallicChipGraphic()
            }

            // Middle: Artwork + Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Artwork thumbnail
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(if (isCircularArt) CircleShape else RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mainTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subTitle,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Bottom Row: User Name & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "iTune MEMBER",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = displayName.uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = badgeText1,
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (badgeText2.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = badgeText2,
                                color = accentColor,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetallicChipGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 28.dp, height = 20.dp)) {
        val w = size.width
        val h = size.height

        // Gold chip base
        drawRoundRect(
            color = Color(0xFFD4AF37),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )
        // Chip lines
        val linePaint = Color(0xFF996515)
        drawLine(linePaint, Offset(w * 0.35f, 0f), Offset(w * 0.35f, h), strokeWidth = 1.dp.toPx())
        drawLine(linePaint, Offset(w * 0.65f, 0f), Offset(w * 0.65f, h), strokeWidth = 1.dp.toPx())
        drawLine(linePaint, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1.dp.toPx())
    }
}

private fun formatDuration(sec: Long): String {
    if (sec <= 0) return "0m"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
