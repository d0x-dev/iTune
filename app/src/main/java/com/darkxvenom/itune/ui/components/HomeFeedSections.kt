package com.darkxvenom.itune.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.data.model.HomeFeedItem
import com.darkxvenom.itune.data.model.HomeFeedShelf
import com.darkxvenom.itune.data.model.HomeHeroCard
import com.darkxvenom.itune.data.model.HomeItemKind
import com.darkxvenom.itune.ui.theme.AppleSurface
import com.darkxvenom.itune.ui.theme.AppleSurfaceElevated
import com.darkxvenom.itune.ui.theme.AppleTextPrimary
import com.darkxvenom.itune.ui.theme.AppleTextSecondary

/** Apple Music style section title: optional small-caps strapline, bold title, chevron. */
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    strapline: String = "",
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        if (strapline.isNotBlank()) {
            Text(
                text = strapline.uppercase(),
                color = AppleTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = AppleTextPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppleTextSecondary,
                    modifier = Modifier.padding(start = 4.dp).size(20.dp),
                )
            }
        }
    }
}

/** Square artwork cards: albums, playlists, mixes, podcasts. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquareCardShelf(
    shelf: HomeFeedShelf,
    modifier: Modifier = Modifier,
    cardSize: Dp = 150.dp,
    onHeaderClick: (() -> Unit)? = null,
    onItemClick: (HomeFeedItem) -> Unit,
    onItemMenuClick: (HomeFeedItem, Offset) -> Unit = { _, _ -> },
) {
    Column(modifier.fillMaxWidth()) {
        HomeSectionHeader(shelf.title, strapline = shelf.strapline, onClick = onHeaderClick)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.items, key = { it.stableKey }) { item ->
                var cardCenter by remember { mutableStateOf(Offset.Zero) }
                Column(
                    Modifier
                        .width(cardSize)
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val pos = coords.positionInWindow()
                                cardCenter = Offset(
                                    x = pos.x + coords.size.width / 2f,
                                    y = pos.y + coords.size.height / 2f,
                                )
                            }
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemMenuClick(item, cardCenter) },
                        ),
                ) {
                    Box(Modifier.size(cardSize)) {
                        Artwork(item.thumbnailUrl, Modifier.fillMaxSize(), RoundedCornerShape(10.dp))
                        // Subtle 3-dots button overlay at top right of artwork
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x88000000))
                                .clickable { onItemMenuClick(item, cardCenter) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        item.title,
                        color = AppleTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            item.subtitle,
                            color = AppleTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Circular artist portraits with centered names. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistCircleShelf(
    shelf: HomeFeedShelf,
    modifier: Modifier = Modifier,
    diameter: Dp = 124.dp,
    onHeaderClick: (() -> Unit)? = null,
    onItemClick: (HomeFeedItem) -> Unit,
    onItemMenuClick: (HomeFeedItem, Offset) -> Unit = { _, _ -> },
) {
    Column(modifier.fillMaxWidth()) {
        HomeSectionHeader(shelf.title, strapline = shelf.strapline, onClick = onHeaderClick)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(shelf.items, key = { it.stableKey }) { item ->
                var itemCenter by remember { mutableStateOf(Offset.Zero) }
                Column(
                    Modifier
                        .width(diameter)
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val pos = coords.positionInWindow()
                                itemCenter = Offset(
                                    x = pos.x + coords.size.width / 2f,
                                    y = pos.y + coords.size.height / 2f,
                                )
                            }
                        }
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemMenuClick(item, itemCenter) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Artwork(item.thumbnailUrl, Modifier.size(diameter), CircleShape)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.title,
                        color = AppleTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            item.subtitle,
                            color = AppleTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** 16:9 cards: music videos and podcast episodes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WideCardShelf(
    shelf: HomeFeedShelf,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 250.dp,
    onHeaderClick: (() -> Unit)? = null,
    onItemClick: (HomeFeedItem) -> Unit,
    onItemMenuClick: (HomeFeedItem, Offset) -> Unit = { _, _ -> },
) {
    Column(modifier.fillMaxWidth()) {
        HomeSectionHeader(shelf.title, strapline = shelf.strapline, onClick = onHeaderClick)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shelf.items, key = { it.stableKey }) { item ->
                var itemCenter by remember { mutableStateOf(Offset.Zero) }
                Column(
                    Modifier
                        .width(cardWidth)
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val pos = coords.positionInWindow()
                                itemCenter = Offset(
                                    x = pos.x + coords.size.width / 2f,
                                    y = pos.y + coords.size.height / 2f,
                                )
                            }
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemMenuClick(item, itemCenter) },
                        ),
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                        Artwork(item.thumbnailUrl, Modifier.fillMaxSize(), RoundedCornerShape(10.dp))
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        // 3-dots button overlay at top right of video card
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x88000000))
                                .clickable { onItemMenuClick(item, itemCenter) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Options",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        item.title,
                        color = AppleTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            item.subtitle,
                            color = AppleTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Grey placeholder shelf shown while the first feed request is in flight. */
@Composable
fun HomeShelfSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).width(150.dp).height(20.dp)
                .clip(RoundedCornerShape(6.dp)).background(AppleSurfaceElevated),
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(4) {
                Column(Modifier.width(150.dp)) {
                    Box(Modifier.size(150.dp).clip(RoundedCornerShape(10.dp)).background(AppleSurface))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(110.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(AppleSurface))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(70.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(AppleSurface))
                }
            }
        }
    }
}

/**
 * Top carousel — same layout as [FeaturedHeroCarousel] (category / title / subtitle above a
 * 320×210 banner with a bottom caption) but fed with real YouTube Music data and fully tappable,
 * supporting 3-dot options and long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveFeaturedHeroCarousel(
    cards: List<HomeHeroCard>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onCardClick: (HomeHeroCard) -> Unit,
    onCardMenuClick: (HomeHeroCard, Offset) -> Unit = { _, _ -> },
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = cards.isNotEmpty(),
    ) {
        if (cards.isEmpty() && isLoading) {
            items(2) { HeroSkeletonCard() }
        } else {
            items(cards, key = { it.id }) { card ->
                var cardCenter by remember { mutableStateOf(Offset.Zero) }
                Column(
                    Modifier
                        .width(320.dp)
                        .onGloballyPositioned { coords ->
                            if (coords.isAttached) {
                                val pos = coords.positionInWindow()
                                cardCenter = Offset(
                                    x = pos.x + coords.size.width / 2f,
                                    y = pos.y + coords.size.height / 2f,
                                )
                            }
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { onCardClick(card) },
                            onLongClick = { onCardMenuClick(card, cardCenter) },
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            card.category.uppercase(),
                            color = AppleTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                        )
                        IconButton(
                            onClick = { onCardMenuClick(card, cardCenter) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = "Options",
                                tint = AppleTextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        card.title,
                        color = AppleTextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        card.subtitle,
                        color = AppleTextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .width(320.dp)
                            .height(210.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp)),
                    ) {
                        Artwork(card.imageUrl, Modifier.fillMaxSize(), RoundedCornerShape(18.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                card.caption,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSkeletonCard() {
    Column(Modifier.width(320.dp)) {
        Box(Modifier.width(80.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(AppleSurfaceElevated))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(190.dp).height(22.dp).clip(RoundedCornerShape(5.dp)).background(AppleSurfaceElevated))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(120.dp).height(15.dp).clip(RoundedCornerShape(4.dp)).background(AppleSurface))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.width(320.dp).height(210.dp).clip(RoundedCornerShape(18.dp)).background(AppleSurface))
    }
}

@Composable
private fun Artwork(url: String?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    Box(modifier.clip(shape).background(AppleSurface)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Maps a feed item kind to a short label used for accessibility / fallbacks. */
fun HomeItemKind.label(): String = when (this) {
    HomeItemKind.SONG -> "Song"
    HomeItemKind.VIDEO -> "Video"
    HomeItemKind.ALBUM -> "Album"
    HomeItemKind.ARTIST -> "Artist"
    HomeItemKind.PLAYLIST -> "Playlist"
    HomeItemKind.PODCAST -> "Podcast"
    HomeItemKind.EPISODE -> "Episode"
}
