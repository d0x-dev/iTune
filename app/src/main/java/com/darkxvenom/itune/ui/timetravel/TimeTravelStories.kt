package com.darkxvenom.itune.ui.timetravel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.darkxvenom.itune.data.timetravel.TimeTravelStats
import kotlinx.coroutines.launch

private const val STORY_DURATION_MILLIS = 5_000

@Composable
fun TimeTravelStoriesDialog(
    stats: TimeTravelStats,
    displayName: String,
    startPage: ReplayStoryPage = ReplayStoryPage.INTRO,
    onDismiss: () -> Unit,
    onSharePage: (ReplayStoryPage) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val pages = remember(stats) {
            ReplayStoryPage.ordered.filter { page ->
                when (page) {
                    ReplayStoryPage.SONGS -> stats.topSongs.isNotEmpty()
                    ReplayStoryPage.ARTISTS -> stats.topArtists.isNotEmpty()
                    ReplayStoryPage.ALBUMS -> stats.topAlbums.isNotEmpty()
                    else -> true
                }
            }
        }

        val initialIndex = pages.indexOf(startPage).coerceAtLeast(0)
        val pagerState = rememberPagerState(
            initialPage = initialIndex,
            pageCount = { pages.size },
        )
        val scope = rememberCoroutineScope()
        var held by remember { mutableStateOf(false) }
        val progress = remember { Animatable(0f) }
        val current by remember { derivedStateOf { pagerState.settledPage } }

        fun advance() {
            if (current < pages.size - 1) {
                scope.launch { pagerState.animateScrollToPage(current + 1) }
            } else {
                onDismiss()
            }
        }

        fun rewind() {
            if (current > 0) {
                scope.launch { pagerState.animateScrollToPage(current - 1) }
            }
        }

        LaunchedEffect(current, held) {
            if (held) {
                progress.stop()
                return@LaunchedEffect
            }
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = STORY_DURATION_MILLIS,
                    easing = LinearEasing,
                ),
            )
            advance()
        }

        val currentPage = pages[current]
        val heroArtwork = when (currentPage) {
            ReplayStoryPage.SONGS -> stats.topSongs.firstOrNull()?.artworkUrl
            ReplayStoryPage.ARTISTS -> stats.topArtists.firstOrNull()?.artworkUrl
            ReplayStoryPage.ALBUMS -> stats.topAlbums.firstOrNull()?.artworkUrl
            else -> stats.topSongs.firstOrNull()?.artworkUrl
        }
        val palette = rememberArtworkColors(heroArtwork)
        val shiftedPalette = remember(palette, currentPage) {
            val hueShift = storyHue(currentPage)
            MeshPalette(palette.colors.map { it.shifted(hueShift, 0f) })
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            held = true
                            tryAwaitRelease()
                            held = false
                        },
                        onTap = { offset ->
                            val width = size.width
                            if (offset.x < width * 0.35f) {
                                rewind()
                            } else {
                                advance()
                            }
                        },
                    )
                },
        ) {
            // Animated Backdrop
            MeshGradientBackground(
                palette = shiftedPalette,
                trackKey = currentPage,
                blurRadius = 56.dp,
                continuous = false,
                animated = true,
            )

            // Scrim
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.60f),
                                Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                // Progress segments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    pages.indices.forEach { index ->
                        val segmentProgress = when {
                            index < current -> 1f
                            index == current -> progress.value
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.25f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(segmentProgress)
                                    .background(Color.White),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Top bar with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "iTune Time Travel".uppercase(),
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Story Content Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = true,
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    StoryPageContent(page, stats, displayName)
                }

                Spacer(Modifier.height(16.dp))

                // Bottom Share Action Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable { onSharePage(pages[current]) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.IosShare,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Share this story",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryPageContent(
    page: ReplayStoryPage,
    stats: TimeTravelStats,
    displayName: String,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Headline
        val headline = buildStoryHeadline(page, stats)
        Text(
            text = headline,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Hero Card
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (page) {
                ReplayStoryPage.INTRO -> IntroHero(stats, displayName)
                ReplayStoryPage.MINUTES -> MinutesHero(stats)
                ReplayStoryPage.SONGS -> SongHero(stats)
                ReplayStoryPage.ARTISTS -> ArtistHero(stats)
                ReplayStoryPage.ALBUMS -> AlbumHero(stats)
                ReplayStoryPage.HABITS -> HabitsHero(stats)
                ReplayStoryPage.SUMMARY -> SummaryHero(stats, displayName)
            }
        }
    }
}

@Composable
private fun IntroHero(stats: TimeTravelStats, displayName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(RoundedCornerShape(26.dp))
                .shadow(24.dp)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            val art = stats.topSongs.firstOrNull()?.artworkUrl
            if (!art.isNullOrBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = displayName.ifBlank { "iTune Listener" },
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${stats.period.label} listening recap",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun MinutesHero(stats: TimeTravelStats) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${stats.totalListeningSec / 60}",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 68.sp,
                fontWeight = FontWeight.ExtraBold,
                brush = PolishedInk,
                shadow = EmbossShadow,
            ),
        )
        Text(
            text = "MINUTES LISTENED",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Across ${stats.totalPlays} plays in ${stats.period.label.lowercase()}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SongHero(stats: TimeTravelStats) {
    val song = stats.topSongs.firstOrNull()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(22.dp))
                .shadow(20.dp),
        ) {
            AsyncImage(
                model = song?.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = song?.title ?: "Top Song",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${song?.artist} · ${song?.playCount ?: 0} plays",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ArtistHero(stats: TimeTravelStats) {
    val artist = stats.topArtists.firstOrNull()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .shadow(20.dp),
        ) {
            AsyncImage(
                model = artist?.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = artist?.artistName ?: "Top Artist",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${artist?.playCount ?: 0} total plays",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun AlbumHero(stats: TimeTravelStats) {
    val album = stats.topAlbums.firstOrNull()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .shadow(20.dp),
        ) {
            AsyncImage(
                model = album?.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = album?.albumName ?: "Top Album",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album?.artistName} · ${album?.playCount ?: 0} plays",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun HabitsHero(stats: TimeTravelStats) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBubble("SONGS", "${stats.distinctSongsCount}")
            StatBubble("ARTISTS", "${stats.distinctArtistsCount}")
            StatBubble("ALBUMS", "${stats.distinctAlbumsCount}")
        }
        if (stats.busiestDay != null) {
            Text(
                text = "Busiest day: ${stats.busiestDay}",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = "Peak hour: ${stats.peakListeningHour}:00",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatBubble(label: String, value: String) {
    Column(
        modifier = Modifier
            .size(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun SummaryHero(stats: TimeTravelStats, displayName: String) {
    ReplayCreditCard(
        label = "REPLAY SUMMARY",
        value = stats.formattedListeningTime,
        detail = "${stats.totalPlays} plays · ${stats.period.label}",
        artworkUrl = stats.topSongs.firstOrNull()?.artworkUrl,
        holder = displayName,
        memberSince = "11/26",
        onClick = {},
        modifier = Modifier.width(310.dp),
    )
}

private fun buildStoryHeadline(page: ReplayStoryPage, stats: TimeTravelStats) = buildAnnotatedString {
    when (page) {
        ReplayStoryPage.INTRO -> {
            append("Your ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFFFC3C44))) {
                append("Time Travel Replay")
            }
            append(" is ready.")
        }
        ReplayStoryPage.MINUTES -> {
            append("You spent ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF9500))) {
                append("${stats.totalListeningSec / 60} minutes")
            }
            append(" immersed in music.")
        }
        ReplayStoryPage.SONGS -> {
            append("One song defined your ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFF34C759))) {
                append(stats.period.label.lowercase())
            }
            append(".")
        }
        ReplayStoryPage.ARTISTS -> {
            append("You kept coming back to ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFFAF52DE))) {
                append(stats.topArtists.firstOrNull()?.artistName ?: "your favorite artist")
            }
            append(".")
        }
        ReplayStoryPage.ALBUMS -> {
            append("This album held your attention from start to finish.")
        }
        ReplayStoryPage.HABITS -> {
            append("You explored ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFF5856D6))) {
                append("${stats.distinctSongsCount} songs")
            }
            append(" from ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFF5856D6))) {
                append("${stats.distinctArtistsCount} artists")
            }
            append(".")
        }
        ReplayStoryPage.SUMMARY -> {
            append("Here is your ")
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Color(0xFFFC3C44))) {
                append(stats.period.label)
            }
            append(" in music.")
        }
    }
}

fun storyHue(page: ReplayStoryPage): Float = when (page) {
    ReplayStoryPage.INTRO -> 0f
    ReplayStoryPage.MINUTES -> 40f
    ReplayStoryPage.SONGS -> 95f
    ReplayStoryPage.ARTISTS -> 145f
    ReplayStoryPage.ALBUMS -> 195f
    ReplayStoryPage.HABITS -> 285f
    ReplayStoryPage.SUMMARY -> 325f
}
