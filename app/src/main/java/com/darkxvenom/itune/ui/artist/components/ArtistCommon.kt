package com.darkxvenom.itune.ui.artist.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.model.ROW_ART_PX
import com.darkxvenom.itune.data.model.ShelfItem
import com.darkxvenom.itune.data.model.Song
import com.darkxvenom.itune.data.model.artworkAt
import com.darkxvenom.itune.ui.artist.haptics.Haptic
import com.darkxvenom.itune.ui.artist.haptics.rememberHaptics
import kotlinx.coroutines.delay
import kotlin.math.abs

val PAGE_GUTTER = 16.dp
val BAR_GUTTER = PAGE_GUTTER
val ROW_DIVIDER_INSET = PAGE_GUTTER + 68.dp
val TRACK_NUMBER_WIDTH = 24.dp
val NUMBERED_ROW_DIVIDER_INSET = PAGE_GUTTER + TRACK_NUMBER_WIDTH + PAGE_GUTTER
val SHELF_CARD_WIDTH = 150.dp
val ROW_MORE_OUTSET = 14.dp

val VIDEO_ASPECT: Float = 16f / 9f
val VIDEO_CARD_WIDTH: Dp = 248.dp

private const val TRACK_COLUMN_FRACTION = 0.88f
private val TRACK_COLUMN_MAX_WIDTH = 400.dp

fun trackColumnWidth(available: Dp): Dp =
    minOf(available * TRACK_COLUMN_FRACTION, TRACK_COLUMN_MAX_WIDTH)

data class LibraryGridSpec(val columns: Int, val cardWidth: Dp)

private val LIBRARY_GRID_MIN_CARD_WIDTH = 140.dp
val LIBRARY_GRID_SPACING = 12.dp
private const val LIBRARY_GRID_MIN_COLUMNS = 2
private const val LIBRARY_GRID_MAX_COLUMNS = 5

fun libraryGrid(available: Dp): LibraryGridSpec {
    val raw = ((available + LIBRARY_GRID_SPACING) / (LIBRARY_GRID_MIN_CARD_WIDTH + LIBRARY_GRID_SPACING))
        .toInt()
    val columns = raw.coerceIn(LIBRARY_GRID_MIN_COLUMNS, LIBRARY_GRID_MAX_COLUMNS)
    val cardWidth = (available - LIBRARY_GRID_SPACING * (columns - 1)) / columns
    return LibraryGridSpec(columns, cardWidth)
}

fun Modifier.trackArtSize(height: Dp, isVideo: Boolean): Modifier =
    if (isVideo) size(width = height * (16f / 9f), height = height) else size(height)

val LocalSearchFieldOnSurface = compositionLocalOf { false }
val LocalPlayingBarsMotion = compositionLocalOf { true }

const val MOTION_FRAME_GAP_MS = 25L
private val PLAYING_BAR_MINIMUMS = floatArrayOf(0.38f, 0.78f, 0.52f)

private fun playingBarHeight(index: Int, minimum: Float, ms: Long): Float {
    val duration = 520 + index * 130
    val peak = 260 + index * 50
    var t = (ms % (2L * duration)).toInt()
    if (t > duration) t = 2 * duration - t
    return if (t <= peak) {
        minimum + (1f - minimum) * t / peak
    } else {
        1f - (1f - minimum) * (t - peak) / (duration - peak)
    }
}

@Composable
fun SearchPlayingBars(modifier: Modifier = Modifier) {
    val elapsedNanos = remember { mutableLongStateOf(0L) }
    val running = LocalPlayingBarsMotion.current
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = Long.MIN_VALUE
        while (true) {
            withFrameNanos { now ->
                if (last != Long.MIN_VALUE) elapsedNanos.longValue += now - last
                last = now
            }
            delay(MOTION_FRAME_GAP_MS)
        }
    }
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.52f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 13.dp, height = 14.dp)) {
            val barWidth = 3.dp.toPx()
            val step = barWidth + 2.dp.toPx()
            val corner = CornerRadius(minOf(2.dp.toPx(), barWidth / 2f))
            val ms = elapsedNanos.longValue / 1_000_000L
            PLAYING_BAR_MINIMUMS.forEachIndexed { index, minimum ->
                val height = size.height * playingBarHeight(index, minimum, ms)
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(index * step, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onSwipeToQueue: (() -> Unit)? = null,
    rowBackground: Color = MaterialTheme.colorScheme.background,
    trackNumber: Int? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    downloadedIds: Set<String> = emptySet(),
    downloadedTint: Color? = MaterialTheme.colorScheme.primary,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    activeTint: Color = MaterialTheme.colorScheme.primary,
    searchPlayingStyle: Boolean = false,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    SwipeToQueueBox(
        onSwipeToQueue = onSwipeToQueue,
        modifier = modifier,
    ) {
        SongRowContent(
            song = song,
            onClick = onClick,
            onLongPress = onLongPress,
            onMore = onMore ?: onLongPress,
            modifier = if (onSwipeToQueue != null) Modifier.background(rowBackground) else modifier,
            trackNumber = trackNumber,
            subtitleColor = subtitleColor,
            downloadedIds = downloadedIds,
            downloadedTint = downloadedTint,
            isCurrent = isCurrent,
            isPlaying = isPlaying,
            activeTint = activeTint,
            searchPlayingStyle = searchPlayingStyle,
            selected = selected,
            trailing = trailing,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToQueueBox(
    onSwipeToQueue: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onSwipeToQueue == null) {
        content()
        return
    }
    val haptics = rememberHaptics()
    val swipeStateHolder = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }
    var boxWidth by remember { mutableFloatStateOf(0f) }
    val currentOnSwipeToQueue by rememberUpdatedState(onSwipeToQueue)

    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                val offset = try { swipeStateHolder.value?.requireOffset() ?: 0f } catch (_: Exception) { 0f }
                if (abs(offset) >= boxWidth * 0.45f) {
                    haptics.play(Haptic.Select)
                    currentOnSwipeToQueue()
                }
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.5f },
    )
    swipeStateHolder.value = swipeState

    LaunchedEffect(swipeState, boxWidth) {
        if (boxWidth <= 0f) return@LaunchedEffect
        val armAt = boxWidth * 0.45f
        var armed = false
        snapshotFlow { try { swipeState.requireOffset() } catch (_: Exception) { 0f } }
            .collect { offset ->
                val travelled = abs(offset)
                when {
                    !armed && travelled >= armAt -> {
                        armed = true
                        haptics.play(Haptic.Tick)
                    }
                    armed && travelled < armAt * 0.8f -> armed = false
                }
            }
    }

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier.onSizeChanged { boxWidth = it.width.toFloat() },
        backgroundContent = { QueueSwipeBackground(swipeState) },
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSwipeBackground(swipeState: SwipeToDismissBoxState) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val offset = try { swipeState.requireOffset() } catch (_: Exception) { 0f }
                if (offset > 0f) {
                    clipRect(left = 0f, top = 0f, right = offset, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                } else if (offset < 0f) {
                    clipRect(left = size.width + offset, top = 0f, right = size.width, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
            }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = PAGE_GUTTER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        QueueSwipeLabel()
        QueueSwipeLabel()
    }
}

@Composable
private fun QueueSwipeLabel() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.PlaylistPlay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.play_next),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SongRowContent(
    song: Song,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trackNumber: Int? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    downloadedIds: Set<String> = emptySet(),
    downloadedTint: Color? = MaterialTheme.colorScheme.primary,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    activeTint: Color = MaterialTheme.colorScheme.primary,
    searchPlayingStyle: Boolean = false,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent && !searchPlayingStyle) activeTint else MaterialTheme.colorScheme.onBackground,
        label = "song row title",
    )
    val activeBackground by animateColorAsState(
        targetValue = if ((isCurrent && !searchPlayingStyle) || selected) activeTint.copy(alpha = 0.14f) else Color.Transparent,
        label = "song row background",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(activeBackground)
            .longPressMenuClickable(
                onClick = onClick,
                onLongClick = onLongPress,
                liftPadding = PaddingValues(horizontal = PAGE_GUTTER),
            )
            .padding(horizontal = PAGE_GUTTER, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null) {
            Box(
                Modifier.width(TRACK_NUMBER_WIDTH).height(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (searchPlayingStyle && isCurrent && isPlaying) {
                    SearchPlayingBars()
                } else if (isCurrent) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = activeTint,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = "$trackNumber",
                        style = MaterialTheme.typography.bodyLarge,
                        color = subtitleColor,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Box(Modifier.trackArtSize(52.dp, song.isVideo)) {
                AsyncImage(
                    model = song.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .thumbnailBorder(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                if (searchPlayingStyle && isCurrent && isPlaying) {
                    SearchPlayingBars(Modifier.align(Alignment.Center))
                }
            }
        }
        Spacer(Modifier.width(if (trackNumber == null) 14.dp else PAGE_GUTTER))
        Column(Modifier.weight(1f)) {
            ExplicitSongTitle(
                song = song,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            val subtitle = listOfNotNull(
                song.artist.takeIf { it.isNotBlank() },
                song.downloadFormat,
            ).joinToString(" · ")
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subtitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (downloadedTint != null && song.videoId in downloadedIds) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.downloaded),
                tint = downloadedTint,
                modifier = Modifier.size(16.dp),
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = activeTint,
                modifier = Modifier.size(20.dp),
            )
        }
        if (isCurrent && trackNumber == null && !searchPlayingStyle) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = activeTint,
                modifier = Modifier.size(20.dp),
            )
        }
        song.durationText?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = subtitleColor,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            trailing()
        }
        if (onMore != null) {
            RowMoreButton(onClick = onMore, tint = subtitleColor)
        }
    }
}

@Composable
fun RowMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .leftOutOfHeldPicture()
            .hangIntoGutter(ROW_MORE_OUTSET)
            .size(36.dp)
            .menuAnchor()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more),
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

fun Modifier.hangIntoGutter(outset: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val width = (placeable.width - outset.roundToPx()).coerceAtLeast(0)
    layout(width, placeable.height) { placeable.place(0, 0) }
}

@Composable
fun VideoShelfCard(
    item: ShelfItem,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier.width(VIDEO_CARD_WIDTH),
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    placeholderColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Column(
        modifier = modifier
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT)
                .clip(RoundedCornerShape(12.dp))
                .thumbnailBorder(RoundedCornerShape(12.dp))
                .background(placeholderColor),
        ) {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isCurrent && isPlaying) SearchPlayingBars(Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MessageState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER + 6.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(container, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val SHIMMER_PERIOD_MS = 1400
private val BlockShape = RoundedCornerShape(6.dp)
private val LineShape = RoundedCornerShape(4.dp)
private val TitleWidths = listOf(0.68f, 0.46f, 0.58f, 0.74f, 0.52f)
private val SubtitleWidths = listOf(0.34f, 0.44f, 0.27f, 0.38f, 0.31f)

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = BlockShape) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = 0.16f)
        .compositeOver(base)
    val sweep = rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MS, easing = LinearEasing)),
        label = "sweep",
    )
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val band = size.width * 0.5f
                val startX = -band + sweep.value * (size.width + band * 2)
                val brush = Brush.horizontalGradient(
                    colors = listOf(base, highlight, base),
                    startX = startX,
                    endX = startX + band,
                )
                onDrawBehind { drawRect(brush) }
            },
    )
}

@Composable
private fun SkeletonLine(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier = modifier.fillMaxWidth(fraction).height(height),
        shape = LineShape,
    )
}

@Composable
fun SectionHeaderSkeleton(index: Int = 0) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp)) {
        SkeletonLine(fraction = TitleWidths[index % TitleWidths.size] * 0.7f, height = 18.dp)
    }
}

@Composable
fun SongRowSkeleton(index: Int = 0, circular: Boolean = false, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(52.dp), if (circular) CircleShape else RoundedCornerShape(8.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            SkeletonLine(fraction = TitleWidths[index % TitleWidths.size], height = 14.dp)
            Spacer(Modifier.height(7.dp))
            SkeletonLine(fraction = SubtitleWidths[index % SubtitleWidths.size], height = 11.dp)
        }
    }
}

fun LazyListScope.songListSkeleton(
    count: Int = 8,
    keyPrefix: String = "skeleton:song",
    circular: Boolean = false,
    alpha: Float = 1f,
) {
    items(count, key = { "$keyPrefix:$it" }) { index ->
        Box(Modifier.graphicsLayer { this.alpha = alpha }) {
            SongRowSkeleton(index = index, circular = circular)
        }
    }
}

@Composable
private fun CompactSongRowSkeleton(index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(48.dp), RoundedCornerShape(7.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonLine(fraction = TitleWidths[index % TitleWidths.size], height = 14.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonLine(fraction = SubtitleWidths[index % SubtitleWidths.size], height = 11.dp)
        }
    }
}

@Composable
private fun ShelfSkeleton(index: Int = 0, cardCorner: Dp = 12.dp) {
    Column(Modifier.padding(bottom = 26.dp)) {
        SectionHeaderSkeleton(index)
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(4) { i ->
                Column(Modifier.width(SHELF_CARD_WIDTH)) {
                    ShimmerBox(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = RoundedCornerShape(cardCorner),
                    )
                    Spacer(Modifier.height(10.dp))
                    SkeletonLine(fraction = TitleWidths[i % TitleWidths.size], height = 14.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonLine(fraction = SubtitleWidths[i % SubtitleWidths.size], height = 12.dp)
                }
            }
        }
    }
}

@Composable
private fun DetailActionsSkeleton(isArtist: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER + 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        ShimmerBox(Modifier.size(50.dp), CircleShape)
        ShimmerBox(Modifier.width(130.dp).height(50.dp), CircleShape)
        if (!isArtist) {
            ShimmerBox(Modifier.size(50.dp), CircleShape)
        }
    }
}

fun LazyListScope.detailSkeleton(isArtist: Boolean) {
    if (isArtist) {
        item(key = "skeleton:detail:actions") {
            Column(Modifier.graphicsLayer { alpha = 0.4f }) {
                DetailActionsSkeleton(isArtist = true)
                Spacer(Modifier.height(22.dp))
            }
        }
        item(key = "skeleton:detail:top") {
            Column(Modifier.graphicsLayer { alpha = 0.4f }) {
                SectionHeaderSkeleton()
                BoxWithConstraints {
                    val columnWidth = trackColumnWidth(maxWidth)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false,
                    ) {
                        item {
                            Column(Modifier.width(columnWidth)) {
                                repeat(4) { index -> CompactSongRowSkeleton(index) }
                            }
                        }
                    }
                }
            }
        }
        item(key = "skeleton:detail:sections") {
            Box(Modifier.graphicsLayer { alpha = 0.4f }) {
                ShelfSkeleton(index = 2, cardCorner = 10.dp)
            }
        }
        return
    }
    item(key = "skeleton:detail:actions") {
        Column(Modifier.graphicsLayer { alpha = 0.4f }) {
            DetailActionsSkeleton(isArtist = false)
            Spacer(Modifier.height(20.dp))
        }
    }
    songListSkeleton(count = 8, keyPrefix = "skeleton:detail:song", alpha = 0.4f)
}

@Composable
fun CoverPlaceholder(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    base: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconFraction: Float = 0.36f,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(lerp(base, tint, 0.16f), base)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxSize(iconFraction),
        )
    }
}

@Composable
fun DownloadedBadge(
    videoId: String,
    tint: Color,
    downloadedIds: Set<String> = emptySet(),
) {
    if (videoId !in downloadedIds) return
    Spacer(Modifier.width(6.dp))
    Icon(
        Icons.Rounded.CheckCircle,
        contentDescription = stringResource(R.string.downloaded),
        tint = tint,
        modifier = Modifier.size(16.dp),
    )
}
