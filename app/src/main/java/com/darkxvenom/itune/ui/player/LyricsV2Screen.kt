package com.darkxvenom.itune.ui.player

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkxvenom.itune.R
import com.darkxvenom.itune.backend.LyricsLine
import com.darkxvenom.itune.backend.MusicPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_bold)
)

private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private val HEAD_LYRICS_ENTRY = AirBeatsLyricsEntry(time = 0L, text = "")

data class AirBeatsWordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val isBackground: Boolean = false
)

data class AirBeatsLyricsEntry(
    val time: Long,
    val text: String,
    val words: List<AirBeatsWordTimestamp>? = null,
    val agent: String? = null,
) : Comparable<AirBeatsLyricsEntry> {
    override fun compareTo(other: AirBeatsLyricsEntry): Int = (time - other.time).toInt()
}

private fun unescapeLyricsHtml(text: String): String {
    if (!text.contains('&')) return text
    val cleaned = text
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
    return try {
        if (cleaned.contains('&')) {
            android.text.Html.fromHtml(cleaned, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            cleaned
        }
    } catch (_: Throwable) {
        cleaned
    }
}

@Composable
fun shimmerBrush(targetValue: Float = 1200f): Brush {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.22f),
        Color.White.copy(alpha = 0.08f),
    )
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation.value - 300f, y = 0f),
        end = Offset(x = translateAnimation.value, y = 300f)
    )
}

/**
 * In-Player Synced Lyrics Component (Direct 1:1 port from iTube / AirBeats)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AirBeatsLyricsV2Content(
    lyrics: List<LyricsLine>,
    sliderPositionProvider: () -> Long?,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onUserInteraction: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lyricsLoading = MusicPlayerState.lyricsLoading

    val textColor = Color.White
    val inactiveAlpha = 0.38f
    val lyricsTextSize = 27f

    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }

    val entriesWithWords: List<AirBeatsLyricsEntry> = remember(lyrics) {
        if (lyrics.isEmpty()) return@remember emptyList()
        val mapped = lyrics.mapIndexed { index, line ->
            val cleanLineText = unescapeLyricsHtml(line.text)
            val nextTime = if (index < lyrics.lastIndex) lyrics[index + 1].timeMs else line.timeMs + 5000L
            val lineDurationMs = (nextTime - line.timeMs).coerceAtLeast(600L)

            val words = if (line.words.isNotEmpty()) {
                line.words.map { w ->
                    val cleanWord = unescapeLyricsHtml(w.text)
                    AirBeatsWordTimestamp(
                        text = if (cleanWord.endsWith(" ")) cleanWord else "$cleanWord ",
                        startTime = w.startMs / 1000.0,
                        endTime = w.endMs / 1000.0
                    )
                }
            } else {
                synthesizeWordTimestamps(cleanLineText, line.timeMs, lineDurationMs)
            }
            AirBeatsLyricsEntry(time = line.timeMs, text = cleanLineText, words = words)
        }
        listOf(HEAD_LYRICS_ENTRY) + mapped
    }

    val isSynced = remember(entriesWithWords) { entriesWithWords.size > 1 }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var currentLineIndex by remember { mutableIntStateOf(0) }

    // 60fps frame loop
    LaunchedEffect(entriesWithWords, isSynced) {
        if (!isSynced || entriesWithWords.isEmpty()) return@LaunchedEffect
        while (isActive) {
            val sliderPos = sliderPositionProvider()
            val pos = sliderPos ?: MusicPlayerState.positionMs

            currentPositionMs = pos
            currentLineIndex = findCurrentLineIndex(entriesWithWords, currentPositionMs)
            delay(16L)
        }
    }

    val listState = rememberLazyListState()
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    val nestedScrollConnection = remember(onUserInteraction) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                    isManualScrolling = true
                    lastManualScrollTime = System.currentTimeMillis()
                    onUserInteraction()
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    LaunchedEffect(currentLineIndex, isManualScrolling) {
        if (isManualScrolling || !isSynced) return@LaunchedEffect
        if (currentLineIndex < 0 || currentLineIndex >= entriesWithWords.size) return@LaunchedEffect

        val visibleInfo = listState.layoutInfo
        val viewportHeight = visibleInfo.viewportSize.height
        val targetOffset = (viewportHeight * 0.35f).toInt()

        val distance = abs(currentLineIndex - listState.firstVisibleItemIndex)
        if (distance > 15) {
            listState.scrollToItem((currentLineIndex - 2).coerceAtLeast(0), 0)
        }
        listState.animateScrollToItem(
            index = currentLineIndex,
            scrollOffset = -targetOffset
        )
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {
        if (lyricsLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val brush = shimmerBrush()
                val widths = listOf(0.7f, 0.9f, 0.6f, 0.8f, 0.5f, 0.75f, 0.65f)
                widths.forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(brush)
                    )
                }
            }
            return@BoxWithConstraints
        }

        if (entriesWithWords.isEmpty() || entriesWithWords.size <= 1) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lyrics not available for this song",
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.W600,
                        color = textColor.copy(alpha = 0.6f)
                    ),
                    textAlign = TextAlign.Center
                )
            }
            return@BoxWithConstraints
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .smoothFadingEdge(vertical = 80.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            contentPadding = PaddingValues(
                start = 22.dp,
                end = 22.dp,
                top = 24.dp,
                bottom = 280.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            itemsIndexed(
                items = entriesWithWords,
                key = { index, entry -> "${index}_${entry.time}" }
            ) { index, item ->
                if (item == HEAD_LYRICS_ENTRY) {
                    Spacer(modifier = Modifier.height(16.dp))
                    return@itemsIndexed
                }

                val textAlign = TextAlign.Start
                val horizontalAlignment = Alignment.Start

                val isActive = isSynced && index == currentLineIndex
                val isPast = isSynced && index < currentLineIndex

                val distanceFromActive = if (isSynced) abs(index - currentLineIndex) else 0
                val lineAlpha = when {
                    !isSynced -> 0.95f
                    isActive -> 1f
                    else -> (inactiveAlpha - (distanceFromActive - 1) * 0.04f).coerceIn(0.18f, inactiveAlpha)
                }
                val wordLineAlpha = if (item.words != null && isSynced) 1f else lineAlpha

                val isSelected = selectedIndices.contains(index)
                val lineScale = if (isActive && isSynced) 1.04f else 1f

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = lineScale
                            scaleY = lineScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            alpha = wordLineAlpha
                        }
                        .background(
                            color = if (isSelected && isSelectionModeActive)
                                Color(0xFFFA2D48).copy(alpha = 0.35f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 4.dp)
                        .combinedClickable(
                            enabled = true,
                            onClick = {
                                onUserInteraction()
                                if (isSelectionModeActive) {
                                    if (isSelected) {
                                        selectedIndices.remove(index)
                                        if (selectedIndices.isEmpty()) isSelectionModeActive = false
                                    } else {
                                        if (selectedIndices.size < 5) selectedIndices.add(index)
                                    }
                                } else if (isSynced && item.time > 0) {
                                    onSeek(item.time)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionModeActive) {
                                    isSelectionModeActive = true
                                    selectedIndices.add(index)
                                } else if (!isSelected && selectedIndices.size < 5) {
                                    selectedIndices.add(index)
                                }
                            }
                        ),
                    horizontalAlignment = horizontalAlignment
                ) {
                    if (!item.words.isNullOrEmpty() && isSynced) {
                        AirBeatsLyricsLineV2(
                            words = item.words,
                            isActive = isActive,
                            isPast = isPast,
                            currentPositionMs = currentPositionMs,
                            textColor = textColor,
                            inactiveAlpha = inactiveAlpha,
                            baseFontSize = lyricsTextSize,
                            textAlign = textAlign
                        )
                    } else {
                        Text(
                            text = item.text,
                            style = TextStyle(
                                fontFamily = SFProDisplay,
                                fontSize = if (isSynced) 27.sp else 23.sp,
                                fontWeight = if (isActive) FontWeight.W800 else FontWeight.W700,
                                letterSpacing = (-0.6).sp,
                                lineHeight = 33.sp
                            ),
                            color = textColor.copy(alpha = if (isActive) 1f else inactiveAlpha),
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(280.dp))
            }
        }

        if (isManualScrolling && isSynced) {
            FilledTonalButton(
                onClick = {
                    isManualScrolling = false
                    scope.launch {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        listState.animateScrollToItem(
                            index = currentLineIndex,
                            scrollOffset = -(viewportHeight * 0.35f).toInt()
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.20f),
                    contentColor = Color.White
                )
            ) {
                Text(text = "Resume", style = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = Color.White)
            }
        }

        if (isSelectionModeActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), shape = CircleShape)
                            .clickable {
                                isSelectionModeActive = false
                                selectedIndices.clear()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = "Cancel",
                            colorFilter = ColorFilter.tint(Color.White),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .background(
                                color = if (selectedIndices.isNotEmpty()) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable(enabled = selectedIndices.isNotEmpty()) {
                                if (selectedIndices.isNotEmpty()) {
                                    val sortedIndices = selectedIndices.sorted()
                                    val selectedText = sortedIndices
                                        .mapNotNull { entriesWithWords.getOrNull(it)?.text }
                                        .joinToString("\n")

                                    val trackInfo = MusicPlayerState.currentTrack
                                    val shareText = "\"$selectedText\"\n\n${trackInfo?.title} - ${trackInfo?.artists}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Lyrics"))
                                    isSelectionModeActive = false
                                    selectedIndices.clear()
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_nav_radio),
                            contentDescription = "Share",
                            colorFilter = ColorFilter.tint(Color.Black),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Share",
                            color = Color.Black,
                            style = TextStyle(fontFamily = SFProDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AirBeatsLyricsLineV2(
    words: List<AirBeatsWordTimestamp>,
    isActive: Boolean,
    isPast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    baseFontSize: Float,
    textAlign: TextAlign,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        words.forEachIndexed { _, word ->
            if (word.text == " ") {
                Text(
                    text = " ",
                    style = TextStyle(
                        fontFamily = SFProDisplay,
                        fontSize = baseFontSize.sp,
                        fontWeight = if (isActive) FontWeight.W800 else FontWeight.W700,
                        letterSpacing = (-0.6).sp,
                        lineHeight = 33.sp
                    ),
                    color = Color.Transparent
                )
                return@forEachIndexed
            }
            if (word.text == "\n") {
                Spacer(modifier = Modifier.fillMaxWidth())
                return@forEachIndexed
            }

            AirBeatsAnimatedWordV2(
                word = word,
                isLineActive = isActive,
                isLinePast = isPast,
                currentPositionMs = currentPositionMs,
                textColor = textColor,
                inactiveAlpha = inactiveAlpha,
                fontSize = baseFontSize
            )
        }
    }
}

@Composable
private fun AirBeatsAnimatedWordV2(
    word: AirBeatsWordTimestamp,
    isLineActive: Boolean,
    isLinePast: Boolean,
    currentPositionMs: Long,
    textColor: Color,
    inactiveAlpha: Float,
    fontSize: Float,
) {
    val wordStartMs = (word.startTime * 1000).toLong()
    val wordEndMs = (word.endTime * 1000).toLong()
    val wordDuration = (wordEndMs - wordStartMs).coerceAtLeast(1L)

    val isWordComplete = currentPositionMs >= wordEndMs
    val isWordActive = currentPositionMs in wordStartMs until wordEndMs

    val progress = when {
        isWordComplete -> 1f
        currentPositionMs <= wordStartMs -> 0f
        else -> ((currentPositionMs - wordStartMs).toFloat() / wordDuration).coerceIn(0f, 1f)
    }

    val sinProgress = sin(progress * PI).toFloat()
    val wordScale = 1f + (0.015f * sinProgress)

    val targetFloat = if (isWordActive) -4f * sinProgress else 0f
    val floatOffset by animateFloatAsState(
        targetValue = targetFloat,
        animationSpec = tween(
            durationMillis = if (isWordActive) 50 else 350,
            easing = FastOutSlowInEasing
        ),
        label = "wordFloat"
    )

    val glowProgress = (progress * 2f).coerceAtMost(1f)
    val glowAlpha = if (isWordActive) glowProgress * 0.45f else 0f
    val glowRadius = if (isWordActive) glowProgress * 12f else 0f

    val fontWeight = if (isLineActive) FontWeight.W800 else FontWeight.W700
    val density = LocalDensity.current

    val wordStyle = TextStyle(
        fontFamily = SFProDisplay,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        fontStyle = FontStyle.Normal,
        letterSpacing = (-0.6).sp,
        lineHeight = 33.sp
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = floatOffset * density.density
                scaleX = wordScale
                scaleY = wordScale
            }
    ) {
        Text(
            text = word.text,
            style = wordStyle,
            color = textColor.copy(alpha = inactiveAlpha)
        )

        if (isWordComplete || isWordActive || isLinePast) {
            Text(
                text = word.text,
                style = wordStyle.copy(
                    shadow = if (glowAlpha > 0f) {
                        Shadow(
                            color = textColor.copy(alpha = glowAlpha),
                            offset = Offset.Zero,
                            blurRadius = glowRadius.coerceAtLeast(1f)
                        )
                    } else null
                ),
                color = textColor,
                modifier = if (isWordActive && !isWordComplete) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val edgeWidth = 8.dp.toPx()
                            val center = (size.width + edgeWidth * 2) * progress - edgeWidth
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = center - edgeWidth,
                                    endX = center + edgeWidth
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                } else {
                    Modifier
                }
            )
        }
    }
}

private fun synthesizeWordTimestamps(
    text: String,
    startTimeMs: Long,
    durationMs: Long,
): List<AirBeatsWordTimestamp> {
    if (text.isBlank() || startTimeMs < 0) return emptyList()
    val lineStartSec = startTimeMs / 1000.0
    val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()

    val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
    val words = mutableListOf<AirBeatsWordTimestamp>()
    var currentOffsetMs = 0.0

    tokens.forEachIndexed { wordIdx, token ->
        val weight = token.length.toDouble() / totalChars
        val wordDurMs = durationMs * weight
        val wordStartSec = lineStartSec + (currentOffsetMs / 1000.0)
        val wordEndSec = wordStartSec + (wordDurMs / 1000.0)

        val wordText = if (wordIdx < tokens.lastIndex) "$token " else token
        words.add(
            AirBeatsWordTimestamp(
                text = wordText,
                startTime = wordStartSec,
                endTime = wordEndSec
            )
        )
        currentOffsetMs += wordDurMs
    }
    return words
}

private fun findCurrentLineIndex(
    lines: List<AirBeatsLyricsEntry>,
    position: Long,
): Int {
    if (lines.isEmpty()) return -1
    var low = 0
    var high = lines.lastIndex
    while (low <= high) {
        val mid = (low + high).ushr(1)
        if (lines[mid].time <= position) {
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return high.coerceIn(0, lines.lastIndex)
}

private fun Modifier.smoothFadingEdge(
    vertical: Dp = 80.dp,
): Modifier = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        val topPx = vertical.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.3f to Color.Black.copy(alpha = 0.15f),
                    0.5f to Color.Black.copy(alpha = 0.4f),
                    0.7f to Color.Black.copy(alpha = 0.7f),
                    0.85f to Color.Black.copy(alpha = 0.9f),
                    1.0f to Color.Black,
                ),
                startY = 0f,
                endY = topPx,
            ),
            blendMode = BlendMode.DstIn,
        )
        val bottomPx = vertical.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black,
                    0.15f to Color.Black.copy(alpha = 0.9f),
                    0.3f to Color.Black.copy(alpha = 0.7f),
                    0.5f to Color.Black.copy(alpha = 0.4f),
                    0.7f to Color.Black.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                ),
                startY = size.height - bottomPx,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
