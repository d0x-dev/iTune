package com.darkxvenom.itune.ui.timetravel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.darkxvenom.itune.data.timetravel.TimeTravelStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SLIDE_DURATION_MS = 5000

@Composable
fun TimeTravelInsightsDialog(
    stats: TimeTravelStats,
    displayName: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val slidesCount = 7
    var currentSlideIndex by remember { mutableIntStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    val progressAnim = remember { Animatable(0f) }

    // Auto advance timer
    LaunchedEffect(currentSlideIndex, isPaused) {
        if (isPaused) return@LaunchedEffect

        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = SLIDE_DURATION_MS, easing = LinearEasing),
        )

        if (currentSlideIndex < slidesCount - 1) {
            currentSlideIndex++
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090810)),
        ) {
            // Ambient animated gradient background based on slide
            val slideBgColors = remember(currentSlideIndex) {
                when (currentSlideIndex) {
                    0 -> listOf(Color(0xFF2A103D), Color(0xFF130920), Color(0xFF06040A))
                    1 -> listOf(Color(0xFF380816), Color(0xFF1D0610), Color(0xFF070206))
                    2 -> listOf(Color(0xFF0D2545), Color(0xFF081426), Color(0xFF040A14))
                    3 -> listOf(Color(0xFF2B1F05), Color(0xFF191203), Color(0xFF0A0701))
                    4 -> listOf(Color(0xFF083324), Color(0xFF051E15), Color(0xFF020D09))
                    5 -> listOf(Color(0xFF27133D), Color(0xFF160A24), Color(0xFF08030F))
                    else -> listOf(Color(0xFF3D101E), Color(0xFF1F080F), Color(0xFF080205))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(slideBgColors)),
            )

            // Touch gesture detector for tap left/right and hold to pause
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentSlideIndex) {
                        detectTapGestures(
                            onPress = {
                                isPaused = true
                                val releaseSuccess = tryAwaitRelease()
                                isPaused = false
                            },
                            onTap = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth * 0.35f) {
                                    // Previous slide
                                    if (currentSlideIndex > 0) {
                                        currentSlideIndex--
                                    }
                                } else {
                                    // Next slide
                                    if (currentSlideIndex < slidesCount - 1) {
                                        currentSlideIndex++
                                    } else {
                                        onDismiss()
                                    }
                                }
                            },
                        )
                    },
            )

            // Content container
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                // Top Progress Indicators Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    for (i in 0 until slidesCount) {
                        val segProgress = when {
                            i < currentSlideIndex -> 1f
                            i == currentSlideIndex -> progressAnim.value
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color.White.copy(alpha = 0.25f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(segProgress)
                                    .fillMaxHeight()
                                    .background(Color.White),
                            )
                        }
                    }
                }

                // Top Bar: iTune Replay Badge & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFFFC3C44),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "iTune Replay",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Main Slide Animated Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = currentSlideIndex,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(250))
                        },
                        label = "story_slide_transition",
                    ) { slide ->
                        when (slide) {
                            0 -> IntroSlide(displayName = displayName, periodLabel = stats.period.label)
                            1 -> TopArtistSlide(stats = stats)
                            2 -> TopSongSlide(stats = stats)
                            3 -> ListeningTimeSlide(stats = stats)
                            4 -> ActivityPeakSlide(stats = stats)
                            5 -> PersonalitySlide(stats = stats)
                            else -> ShareSummarySlide(
                                stats = stats,
                                displayName = displayName,
                                onShare = {
                                    scope.launch {
                                        TimeTravelShareManager.shareReplay(context, stats, displayName)
                                    }
                                },
                            )
                        }
                    }
                }

                // Bottom Hint
                Text(
                    text = "Tap left or right to browse  •  Hold to pause",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun IntroSlide(displayName: String, periodLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFC3C44), Color(0xFF7928CA)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Welcome, $displayName",
            color = Color(0xFFFC3C44),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Your $periodLabel\nin Music",
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 48.sp,
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Every rhythm, late-night anthem, and melody you lived with — gathered in one journey.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun TopArtistSlide(stats: TimeTravelStats) {
    val artist = stats.topArtists.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "YOUR MUSICAL ANCHOR",
            color = Color(0xFFB388FF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your #1 Artist",
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
        )

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!artist?.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artist?.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF7928CA), Color(0xFFFC3C44)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = artist?.artistName?.take(1) ?: "A",
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        Text(
            text = artist?.artistName ?: "No artist yet",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (artist != null) "${artist.playCount} total plays  •  ${artist.durationSec / 60} minutes listened" else "Play music to build your stats",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TopSongSlide(stats: TimeTravelStats) {
    val song = stats.topSongs.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "ON HEAVY REPEAT",
            color = Color(0xFFFC3C44),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your Favourite Song",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
        )

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(26.dp))
                .shadow(16.dp, RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!song?.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = song?.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0070F3), Color(0xFF00DF89)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = song?.title ?: "No top track yet",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = song?.artist ?: "",
            color = Color(0xFFFC3C44),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = if (song != null) "${song.playCount} plays  •  You spent ${song.durationSec / 60}m with this track" else "Start playing tracks to see your top song",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ListeningTimeSlide(stats: TimeTravelStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "IMMERSION",
            color = Color(0xFFFFB800),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Total Listening Time",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = stats.formattedListeningTime,
            color = Color.White,
            fontSize = 62.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = "${stats.totalPlays} songs played",
            color = Color(0xFFFFB800),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "That's an average of ${stats.averageDurationSec / 60}m ${stats.averageDurationSec % 60}s per playback session.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ActivityPeakSlide(stats: TimeTravelStats) {
    val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val peakDayIndex = stats.mostActiveDay ?: 5 // default Friday
    val peakDayName = dayNames.getOrElse(peakDayIndex) { "Weekend" }

    val peakHour = stats.mostActiveHour ?: 21
    val hourString = when {
        peakHour == 0 -> "Midnight"
        peakHour == 12 -> "Noon"
        peakHour > 12 -> "${peakHour - 12} PM"
        else -> "$peakHour AM"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "LISTENING PATTERNS",
            color = Color(0xFF00DF89),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your Sonic Peak",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
        )

        Spacer(Modifier.height(34.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = peakDayName,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "around $hourString",
                    color = Color(0xFF00DF89),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "When the world gets busy, you tune in most on ${peakDayName}s around ${hourString}.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PersonalitySlide(stats: TimeTravelStats) {
    val p = stats.personality

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "YOUR LISTENING PERSONA",
            color = Color(p.primaryColorHex),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = p.title,
            color = Color.White,
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = p.tagline,
            color = Color(p.primaryColorHex),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(24.dp),
        ) {
            Text(
                text = p.description,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ShareSummarySlide(
    stats: TimeTravelStats,
    displayName: String,
    onShare: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "TIME TRAVEL WRAP",
            color = Color(0xFFFC3C44),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "That's Your Story",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
        )

        Spacer(Modifier.height(26.dp))

        // Mini preview card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF281432), Color(0xFF140D22)),
                    ),
                )
                .padding(20.dp),
        ) {
            Column {
                Text(
                    text = "iTune Replay  •  ${stats.period.label}",
                    color = Color(0xFFFC3C44),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(text = "Total Time", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(text = stats.formattedListeningTime, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "Top Song", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(text = stats.topSongs.firstOrNull()?.title?.take(16) ?: "—", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(text = "Plays", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(text = "${stats.totalPlays}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onShare,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFC3C44),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(56.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.IosShare,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Share Your Replay",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
